package app.drift.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import app.drift.DriftApp
import app.drift.domain.DriftCommand
import app.drift.domain.content.ContentBlockCatalog
import app.drift.domain.content.ContentBlockMode
import app.drift.domain.model.ForegroundEventKind
import app.drift.domain.model.AttentionPathNode
import app.drift.domain.model.UsageSnapshot
import app.drift.intervention.InterventionActivity
import app.drift.notification.DriftNotifications
import app.drift.util.AppLabels
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow

/**
 * Detects drift from intent by observing foreground-app transitions and running
 * a periodic overrun check while a session is active.
 *
 * Signals:
 *  - COMPULSIVE_REENTRY: the user left the target app and returned within a
 *    short window, repeatedly.
 *  - OVER_RUN: elapsed time exceeds the user's declared duration.
 *
 * It never blocks; it only launches [InterventionActivity] with a contextual
 * prompt.
 */
class DriftAccessibilityService : AccessibilityService() {

    private val container get() = (application as DriftApp).container

    private val handler = Handler(Looper.getMainLooper())
    private val contentScanner = ContentScanner()
    private val attentionStateClassifier = AttentionStateClassifier(contentScanner)
    private val contentBlockOverlay = ContentBlockOverlay()
    private var lastForeground: String? = null
    private var lastInterventionAt = 0L
    private var lastOpenInterventionAt = 0L
    private var lastContentBlockAt = 0L
    private var ticking = false
    private var lastAttentionNode: AttentionPathNode? = null
    private var lastAttentionPackage: String? = null
    private var lastAttentionSessionId: Long? = null
    private var lastAttentionContentAt = 0L
    private var lastAttentionScrollAt = 0L
    private var lastAttentionScrollY = Int.MIN_VALUE
    @Volatile private var motivationNewsInFlight = false

    @Volatile private var contentBlockingEnabled = false
    @Volatile private var protectedContentApps: Set<String> = ContentBlockCatalog.rules.map { it.appId }.toSet()
    @Volatile private var allowDmVideos = false
    @Volatile private var contentBlockMode = ContentBlockMode.COMPLETE_BLOCK

    private val periodicCheck = object : Runnable {
        override fun run() {
            if (container.repository.activeSession() != null) {
                val snapshot = buildSnapshot()
                maybeEvaluate(snapshot)
                handler.postDelayed(this, TICK_MS)
            } else {
                ticking = false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected")
        container.appScope.launch { container.repository.restoreActiveSession() }
        container.appScope.launch {
            for (observation in attentionPathEvents) {
                if (observation.from == null) {
                    container.attentionPathStore.recordNodeObservation(observation.to, observation.sessionId, observation.atMs)
                } else {
                    container.attentionPathStore.recordTransition(observation.from, observation.to, observation.sessionId, observation.atMs)
                }
            }
        }
        container.appScope.launch {
            container.settings.settings.collect { s ->
                contentBlockingEnabled = s.contentBlockingEnabled
                protectedContentApps = s.protectedContentApps
                allowDmVideos = s.allowDmVideos
                contentBlockMode = runCatching { ContentBlockMode.valueOf(s.contentBlockMode) }
                    .getOrDefault(ContentBlockMode.COMPLETE_BLOCK)
            }
        }
        ensureTicking()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        observeAttentionPath(event)
        // Block short-form viewers (Reels/Shorts) regardless of window vs content event.
        maybeBlockContent(event)

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Log a fine-grained open event for the assistant ("Instagram opened 4x in 1 min").
        if (pkg != lastForeground && AppLabels.isUserFacingApp(this, pkg)) {
            val sessionId = container.repository.activeSession()?.session?.id
            val elapsed = SystemClock.elapsedRealtime()
            container.appScope.launch {
                container.repository.recordForegroundEvent(
                    packageName = pkg,
                    label = AppLabels.name(this@DriftAccessibilityService, pkg),
                    kind = ForegroundEventKind.APP_OPEN,
                    sessionId = sessionId,
                    elapsedRealtimeMs = elapsed,
                )
            }
        }

        val target = container.repository.activeSession()?.intent?.targetPackage
        val prev = lastForeground
        lastForeground = pkg
        Log.d(TAG, "window: pkg=$pkg target=$target active=${container.repository.activeSession() != null}")

        val session = container.repository.activeSession()
        if (session != null && target != null) {
            when {
                // Opened (or returned to) the target app — intervene immediately.
                pkg == target -> {
                    if (prev != null && prev != target && prev != packageName) {
                        container.appScope.launch { container.repository.recordReentry() }
                    }
                    maybeOpenIntervention()
                }

                // Left the target app.
                prev == target && pkg != target -> {
                    container.appScope.launch { container.repository.recordExit() }
                }

                // Roving across other apps while a session is nominally open.
                session.session.reentries > 0 -> {
                    container.appScope.launch { container.repository.recordAppSwitch() }
                }
            }
        }

        maybeEvaluate(buildSnapshot())
    }

    private fun maybeEvaluate(snapshot: UsageSnapshot) {
        val now = System.currentTimeMillis()
        if (now - lastInterventionAt < INTERVENTION_MIN_INTERVAL_MS) return
        if (snapshot.foregroundPackage == null) return

        container.appScope.launch {
            val command = container.coordinator.evaluate(snapshot) ?: return@launch
            if (command !is DriftCommand.Show) return@launch
            lastInterventionAt = now
            launchIntervention(command)
        }
    }

    /** One-sec style: show the breathing intervention the moment the app opens. */
    private fun maybeOpenIntervention() {
        val now = System.currentTimeMillis()
        if (now - lastOpenInterventionAt < OPEN_INTERVENTION_COOLDOWN_MS) {
            Log.d(TAG, "maybeOpenIntervention: cooldown")
            return
        }
        Log.d(TAG, "maybeOpenIntervention: triggering")
        container.appScope.launch {
            val command = container.coordinator.onTargetOpened()
            Log.d(TAG, "onTargetOpened -> $command")
            if (command !is DriftCommand.Show) return@launch
            lastOpenInterventionAt = now
            launchIntervention(command)
        }
    }

    /** Blocks Reels/Shorts by pressing Back or covering the video, leaving DMs/feed usable. */
    private fun maybeBlockContent(event: AccessibilityEvent) {
        if (!contentBlockingEnabled) {
            contentBlockOverlay.hide()
            return
        }
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val rule = ContentBlockCatalog.ruleFor(pkg)
        if (rule == null) {
            contentBlockOverlay.hide()
            return
        }
        if (rule.appId !in protectedContentApps) {
            contentBlockOverlay.hide()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastContentBlockAt < CONTENT_BLOCK_COOLDOWN_MS) return
        val root = rootInActiveWindow ?: event.source ?: return
        val blocked = contentScanner.isBlockedContent(root, pkg, rule, allowDmVideos)
        if (blocked) {
            lastContentBlockAt = now
            val active = container.repository.activeSession()
            val sessionId = active?.session?.id
            val elapsed = SystemClock.elapsedRealtime()
            container.appScope.launch {
                container.repository.recordForegroundEvent(
                    packageName = pkg,
                    label = rule.displayName,
                    kind = ForegroundEventKind.CONTENT_BLOCKED,
                    sessionId = sessionId,
                    elapsedRealtimeMs = elapsed,
                )
            }
            when (contentBlockMode) {
                ContentBlockMode.COVER_OVERLAY -> {
                    if (active != null) maybeShowMotivationNews(active.session.id, active.intent.purpose, active.intent.targetLabel, rule.displayName)
                    Log.d(TAG, "Covering ${rule.displayName} in $pkg")
                    val metrics = resources.displayMetrics
                    val target = contentScanner.viewerBounds(root, pkg, rule)?.let {
                        ContentBlockOverlay.videoRegion(it, metrics.widthPixels, metrics.heightPixels, metrics.density)
                    }
                    contentBlockOverlay.show(this, rule.displayName, target) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        contentBlockOverlay.hide()
                    }
                }
                ContentBlockMode.COMPLETE_BLOCK -> {
                    Log.d(TAG, "Blocking ${rule.displayName} in $pkg")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        } else {
            contentBlockOverlay.hide()
        }
    }

    /** Records a coarse navigation path; raw accessibility strings are never logged or persisted. */
    private fun observeAttentionPath(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val eventType = event.eventType
        val scrollEvent = eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val recognizedEvent = scrollEvent || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!recognizedEvent) return

        val now = System.currentTimeMillis()
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastAttentionContentAt < CONTENT_GRAPH_DEBOUNCE_MS) return
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) lastAttentionContentAt = now

        val active = container.repository.activeSession()
        val sessionId = active?.session?.id
        val root = rootInActiveWindow ?: event.source
        val node = attentionStateClassifier.classify(
            packageName = packageName,
            root = root,
            event = event,
            activeGoal = active?.intent?.purpose,
            allowDmVideos = allowDmVideos,
        )
        if (node == null) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && packageName != lastAttentionPackage) {
                lastAttentionNode = null
                lastAttentionPackage = packageName
                lastAttentionSessionId = sessionId
                lastAttentionScrollY = Int.MIN_VALUE
            }
            return
        }

        val samePackage = packageName == lastAttentionPackage
        val previous = lastAttentionNode.takeIf { samePackage }
        val nowInSession = sessionId != null && sessionId != lastAttentionSessionId
        when {
            previous == null || nowInSession -> attentionPathEvents.trySend(PathObservation(null, node, sessionId, now))
            previous.id != node.id || previous.relationBucket != node.relationBucket ->
                attentionPathEvents.trySend(PathObservation(previous, node, sessionId, now))
            scrollEvent && hasScrollMovement(event) -> {
                val elapsed = android.os.SystemClock.elapsedRealtime()
                if (elapsed - lastAttentionScrollAt >= SCROLL_GRAPH_COOLDOWN_MS) {
                    attentionPathEvents.trySend(PathObservation(previous, node, sessionId, now))
                    lastAttentionScrollAt = elapsed
                }
            }
        }
        lastAttentionNode = node
        lastAttentionPackage = packageName
        lastAttentionSessionId = sessionId
    }

    private fun hasScrollMovement(event: AccessibilityEvent): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            event.scrollDeltaX != 0 || event.scrollDeltaY != 0
        } else {
            val moved = if (lastAttentionScrollY == Int.MIN_VALUE) {
                event.scrollY != 0 || event.maxScrollY > 0
            } else {
                event.scrollY != lastAttentionScrollY
            }
            lastAttentionScrollY = event.scrollY
            moved
        }
    }

    /** Fetches a real positive-news headline off the accessibility callback thread. */
    private fun maybeShowMotivationNews(sessionId: Long, purpose: String, targetLabel: String, blockedContentLabel: String) {
        if (motivationNewsInFlight || !DriftNotifications.canNotify(this)) return
        motivationNewsInFlight = true
        container.appScope.launch {
            try {
                val now = System.currentTimeMillis()
                if (!container.motivationNewsLimiter.canShow(sessionId, now)) return@launch
                val memory = container.assistantService.memoryMarkdown()
                val story = container.motivationNewsRepository.storyFor(
                    goal = "$purpose $targetLabel $blockedContentLabel",
                    savedContext = memory,
                ) ?: return@launch

                val current = container.repository.activeSession()
                if (current?.session?.id != sessionId || !contentBlockingEnabled ||
                    contentBlockMode != ContentBlockMode.COVER_OVERLAY || !DriftNotifications.canNotify(this@DriftAccessibilityService)
                ) return@launch

                val nudgeNumber = container.motivationNewsLimiter.markShown(sessionId, System.currentTimeMillis())
                    ?: return@launch
                DriftNotifications.ensureChannels(this@DriftAccessibilityService)
                val notificationId = (sessionId.hashCode() * 31 + nudgeNumber) and 0x7fffffff
                if (DriftNotifications.showMotivationNews(
                        context = this@DriftAccessibilityService,
                        title = "A little inspiration for $targetLabel",
                        headline = story.title,
                        summary = story.summary,
                        id = notificationId,
                    )
                ) {
                    container.repository.recordNotificationPosted("motivation_news")
                }
            } catch (error: Exception) {
                Log.w(TAG, "Could not show motivation news", error)
            } finally {
                motivationNewsInFlight = false
            }
        }
    }

    private fun buildSnapshot(): UsageSnapshot {
        val session = container.repository.activeSession()
        val intent = session?.intent
        return UsageSnapshot(
            foregroundPackage = lastForeground,
            elapsedMinutes = session?.let { ((System.currentTimeMillis() - it.session.startedAt) / 60_000L).toInt() } ?: 0,
            reentries = session?.session?.reentries ?: 0,
            secondsSinceLastExit = session?.session?.lastExitAt
                ?.let { (System.currentTimeMillis() - it) }
                ?: Long.MAX_VALUE,
            singleAppFocus = (session?.session?.appSwitches ?: 0) <= 2 && intent?.kind != null,
        )
    }

    private fun launchIntervention(command: DriftCommand.Show) {
        Log.d(TAG, "launchIntervention for ${command.active.intent.targetPackage}")
        val i = Intent(this, InterventionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(InterventionActivity.EXTRA_TEMPLATE, command.template.name)
            .putExtra(InterventionActivity.EXTRA_DRIFT, command.driftKind.name)
            .putExtra(InterventionActivity.EXTRA_PACKAGE, command.active.intent.targetPackage)
            .putExtra(InterventionActivity.EXTRA_LABEL, command.active.intent.targetLabel)
            .putExtra(InterventionActivity.EXTRA_PURPOSE, command.active.intent.purpose)
            .putExtra(InterventionActivity.EXTRA_INTENDED_MINUTES, command.active.intent.intendedMinutes)
            .putExtra(
                InterventionActivity.EXTRA_ELAPSED_MINUTES,
                ((System.currentTimeMillis() - command.active.session.startedAt) / 60_000L).toInt(),
            )
        startActivity(i)
    }

    private fun ensureTicking() {
        if (!ticking) {
            ticking = true
            handler.post(periodicCheck)
        }
    }

    override fun onInterrupt() = Unit

    private data class PathObservation(
        val from: AttentionPathNode?,
        val to: AttentionPathNode,
        val sessionId: Long?,
        val atMs: Long,
    )

    override fun onDestroy() {
        attentionPathEvents.close()
        contentBlockOverlay.hide()
        handler.removeCallbacks(periodicCheck)
        ticking = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DriftAccessibility"
        private const val TICK_MS = 20_000L
        private const val INTERVENTION_MIN_INTERVAL_MS = 60_000L
        private const val OPEN_INTERVENTION_COOLDOWN_MS = 4_000L
        private const val CONTENT_BLOCK_COOLDOWN_MS = 1_000L
        private const val CONTENT_GRAPH_DEBOUNCE_MS = 250L
        private const val SCROLL_GRAPH_COOLDOWN_MS = 2_000L
    }

    private val attentionPathEvents = kotlinx.coroutines.channels.Channel<PathObservation>(
        capacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
}
