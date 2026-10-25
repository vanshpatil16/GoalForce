package app.drift.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import app.drift.DriftApp
import app.drift.domain.DriftCommand
import app.drift.domain.model.UsageSnapshot
import app.drift.intervention.InterventionActivity
import kotlinx.coroutines.launch

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
    private var lastForeground: String? = null
    private var lastInterventionAt = 0L
    private var ticking = false

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
        container.appScope.launch { container.repository.restoreActiveSession() }
        ensureTicking()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val target = container.repository.activeSession()?.intent?.targetPackage
        val prev = lastForeground
        lastForeground = pkg

        val session = container.repository.activeSession()
        if (session != null && target != null) {
            when {
                // Returned to the target app.
                pkg == target -> if (prev != null && prev != target && prev != packageName) {
                    container.appScope.launch { container.repository.recordReentry() }
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

    override fun onDestroy() {
        handler.removeCallbacks(periodicCheck)
        ticking = false
        super.onDestroy()
    }

    companion object {
        private const val TICK_MS = 20_000L
        private const val INTERVENTION_MIN_INTERVAL_MS = 60_000L
    }
}
