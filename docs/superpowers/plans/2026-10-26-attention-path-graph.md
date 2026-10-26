# Attention Path Graph Implementation Plan

> **For agentic workers:** This plan is intended for native, task-by-task implementation in this session. User instruction: do not add or run tests, and do not build the app.

**Goal:** Build an on-device graph of observed app navigation paths, relate paths to focus-session outcomes, and let users protect supported short-form viewer nodes.

**Architecture:** Extend accessibility event collection to classify coarse app states without retaining raw text. Persist a bounded graph in private local preferences, correlate observed nodes with completed session outcomes, and expose a graph view with protection controls backed by the current content-block mode.

**Tech Stack:** Kotlin, Android AccessibilityService, AndroidX DataStore Preferences, SharedPreferences/JSON for bounded graph persistence, Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-10-26-attention-path-graph-design.md`

## Global Constraints

- All state classification and graph scoring run on-device.
- Never persist raw accessible text, usernames, message text, or screenshots.
- Do not send accessibility content to Groq or the news feed.
- Accessibility trees vary across app versions and may omit content. Mark state detection as observed only when a known marker is present; otherwise use unknown.
- The graph reports correlation, not proof that content caused an overrun.
- Enforce limits on graph size and age so long-term storage remains bounded.
- Avoid Room schema changes while destructive fallback migration remains configured.
- Do not add or run tests or build the app, per the user's instruction.

## Review Focus

- Missing or changed third-party view IDs must map to `unknown` and must not cause blocking. Review `AttentionStateClassifier` fallback paths.
- Repeated `TYPE_WINDOW_CONTENT_CHANGED` events must not inflate navigation edges. Review event deduplication and state-transition checks.
- DM-sent videos must remain exempt when the current DM exemption is enabled. Review the service path before selected-node protection.
- Corrupt or oversized graph preferences must recover to an empty bounded graph without a crash. Review JSON decode and pruning paths.
- Sessions ended by app/service interruption must not be counted as completed overruns. Review outcome correlation at session finalization.

---

## File Map

- Create `app/src/main/java/app/drift/domain/model/AttentionPathGraph.kt` for nodes, transitions, node statistics, and graph snapshots.
- Create `app/src/main/java/app/drift/domain/AttentionPathAnalyzer.kt` for bounded state scoring and evidence labels.
- Create `app/src/main/java/app/drift/data/AttentionPathStore.kt` for private local JSON persistence and observable snapshots. It must cap data and discard malformed storage safely.
- Create `app/src/main/java/app/drift/service/AttentionStateClassifier.kt` for mapping an accessibility window/event to a coarse node ID using known view IDs and an allowlist of accessible labels. It must not return raw labels.
- Modify `app/src/main/res/xml/accessibility_service_config.xml` to include click and scroll event types and view IDs needed for classification.
- Modify `app/src/main/java/app/drift/service/DriftAccessibilityService.kt` to record deduplicated transitions, link them to the active session, and continue using existing block behavior.
- Modify `app/src/main/java/app/drift/domain/content/ContentBlockRules.kt` only if a stable short-form state ID/app ID is needed by the classifier and UI.
- Modify `app/src/main/java/app/drift/data/SettingsRepository.kt` to persist supported content-node selections. When the preference is absent, default to all currently supported short-form nodes to preserve existing behavior.
- Modify `app/src/main/java/app/drift/di/AppContainer.kt` to construct the store/analyzer and expose them to the service and UI.
- Modify `app/src/main/java/app/drift/domain/DriftCoordinator.kt` to provide completed outcome evidence to the path store. Do not mark abandoned sessions as overruns.
- Modify `app/src/main/java/app/drift/ui/DriftViewModel.kt` to expose graph state and a selection method.
- Create `app/src/main/java/app/drift/ui/screens/AttentionPathsScreen.kt` to render nodes/edges, evidence/sample counts, and supported-node toggles.
- Modify `app/src/main/java/app/drift/ui/screens/ProfileHubScreen.kt` to add the Attention Paths tab.

## Interfaces

- `AttentionPathNode(id: String, label: String, appId: String?, relationBucket: String, supportedForBlocking: Boolean)` identifies a coarse state; `id` is derived only from app package, a known semantic state, and a fixed relevance bucket.
- `AttentionPathEdge(fromNodeId: String, toNodeId: String, relationBucket: String, transitions: Int, sessions: Int)` summarizes observed navigation and the destination's fixed goal-relation bucket.
- `AttentionPathNodeStats(node: AttentionPathNode, observedSessions: Int, completedSessions: Int, overrunSessions: Int, overrunRate: Float, evidenceLabel: String)` reports session association without implying causality.
- `AttentionPathGraph(nodes: List<AttentionPathNodeStats>, edges: List<AttentionPathEdge>, protectedAppIds: Set<String>)` is the UI snapshot composed by the view model from graph data and settings.
- `AttentionPathStore.recordTransition(from: AttentionPathNode, to: AttentionPathNode, sessionId: Long?, atMs: Long)` records one deduplicated transition; the destination node carries the fixed relation bucket and safe label.
- `AttentionPathStore.recordNodeObservation(node: AttentionPathNode, sessionId: Long?, atMs: Long)` records a first/current state without inventing a self-transition.
- `AttentionPathStore.recordSessionOutcome(sessionId: Long, outcome: Outcome)` records only completed outcomes and updates the bounded snapshot.
- `AttentionPathStore.graph: Flow<AttentionPathGraph>` exposes persisted aggregate state.
- `AttentionStateClassifier.classify(packageName: String, root: AccessibilityNodeInfo?, event: AccessibilityEvent, activeGoal: String?, allowDmVideos: Boolean): AttentionPathNode?` returns a known coarse node or null; it may compare accessible text to goal keywords in memory and returns only `goal_related`, `no_goal_match`, or `unavailable` as the relation bucket. Raw accessible text is never returned.
- `AttentionPathAnalyzer` labels a node “associated with distraction” only with at least three completed sessions and when its overrun rate is at least 0.5 and at least 0.2 above the overall overrun baseline. Otherwise the evidence label is “not enough sessions yet” or “no elevated association.”

## Tasks

### Task 1: Define graph data and evidence scoring

**Files:**
- Create `app/src/main/java/app/drift/domain/model/AttentionPathGraph.kt`
- Create `app/src/main/java/app/drift/domain/AttentionPathAnalyzer.kt`

**Interfaces:**
- Produces the graph types and analyzer contract listed above.
- The analyzer consumes bounded observation aggregates and completed outcomes; it has no Android or storage dependency.

- [x] Define node, edge, per-node stats, and graph snapshot data classes with stable string IDs.
- [x] Implement the evidence rule: fewer than three completed sessions means “not enough sessions yet”; otherwise mark “associated with distraction” only when overrun rate is at least 0.5 and at least 0.2 above the overall baseline.
- [x] Clamp rates to `[0, 1]`, handle zero-session baselines as zero, and keep all displayed counts explicit.
- [x] Review the analyzer against zero samples, equal-to-baseline, and exactly-three-session boundaries without adding/running tests.

### Task 2: Add bounded private graph persistence

**Files:**
- Create `app/src/main/java/app/drift/data/AttentionPathStore.kt`
- Modify `app/src/main/java/app/drift/di/AppContainer.kt`

**Interfaces:**
- Consumes the domain graph types from Task 1.
- Produces `graph: Flow<AttentionPathGraph>`, `recordTransition(...)`, and `recordSessionOutcome(...)`.

- [x] Store only node IDs, fixed relevance buckets, edge IDs, counts, session outcome flags, and timestamps in app-private preferences; do not serialize labels from accessibility text.
- [x] Add `recordNodeObservation(...)` so first visits and newly started sessions record a node without fabricating a self-edge.
- [x] Cap unique nodes at 32, edges at 64, and session observations at 500 records/90 days; when over cap, remove oldest/least-used records first.
- [x] Decode malformed JSON as an empty graph and rewrite only valid bounded data.
- [x] Keep updates serialized and emit snapshots after each persisted change.
- [x] Construct one store and analyzer in `AppContainer`; do not add or modify Room entities or schema version.
- [x] Review stored JSON fields to confirm there is no raw text, screenshot data, or content payload.

### Task 3: Classify and record accessibility navigation

**Files:**
- Create `app/src/main/java/app/drift/service/AttentionStateClassifier.kt`
- Modify `app/src/main/res/xml/accessibility_service_config.xml`
- Modify `app/src/main/java/app/drift/service/DriftAccessibilityService.kt`
- Modify `app/src/main/java/app/drift/domain/content/ContentBlockRules.kt` if needed for stable viewer IDs

**Interfaces:**
- Consumes the store API from Task 2.
- Produces coarse nodes only for known states; unknown/inaccessible content yields a non-blocking `unknown` state or no edge.

- [x] Add `typeViewClicked` and `typeViewScrolled` to the service event configuration while retaining existing window/content event types and `canRetrieveWindowContent`.
- [x] Classify supported Reels/Shorts/TikTok viewer states using the existing known viewer view IDs.
- [x] Classify feed, inbox, search, and profile only when an allowlisted accessible ID or label is present; normalize labels in memory and return fixed category IDs only.
- [x] Read visible text/content descriptions only transiently. Compare any exposed text with active-goal keywords and return only `goal_related`, `no_goal_match`, or `unavailable`; never write raw values to storage or logs.
- [x] Do not traverse or inspect accessible text in inbox/DM surfaces; even when a DM video is configured to block, keep its relation bucket `unavailable`.
- [x] Record edges when the coarse state or fixed relevance bucket changes. Record same-state scroll self-edges only for real scroll events with nonzero movement, at most once every two seconds; suppress repeated content-change events for the same state.
- [x] Tag transitions with the active focus session ID, and update the previous node on package/window changes.
- [x] Preserve `allowDmVideos`, the content-block master switch, the configured cover/full mode, and existing overlay behavior.
- [x] Review the event path to ensure classification failures and unknown screens never invoke a block.

### Task 4: Correlate outcomes and persist node protection choices

**Files:**
- Modify `app/src/main/java/app/drift/domain/DriftCoordinator.kt`
- Modify `app/src/main/java/app/drift/data/SettingsRepository.kt`
- Modify `app/src/main/java/app/drift/di/AppContainer.kt`
- Modify `app/src/main/java/app/drift/service/DriftAccessibilityService.kt`

**Interfaces:**
- Consumes completed outcomes from `DriftCoordinator` and selected supported app IDs from settings.
- Produces graph node rates and applies the selected short-form app IDs inside existing block mode.

- [x] After session finalization, call `recordSessionOutcome(sessionId, outcome)` for completed sessions only; do not record abandoned or still-active sessions as failed.
- [x] Add a DataStore string-set preference for supported content app IDs. If unset, return all IDs in `ContentBlockCatalog` to preserve the current default behavior.
- [x] Add a settings method to toggle one content app ID without overwriting unrelated preferences.
- [x] Have the accessibility service collect the selected IDs and skip content scanning/blocking for unsupported or unselected app IDs.
- [x] Ensure DM exemptions are checked before presenting the overlay or recording the viewer as a blocked node.
- [x] Review master-off, empty-set, all-selected, one-selected, DM-exempt, and unsupported-app behavior manually.

### Task 5: Render the graph and node protection controls

**Files:**
- Create `app/src/main/java/app/drift/ui/screens/AttentionPathsScreen.kt`
- Modify `app/src/main/java/app/drift/ui/DriftViewModel.kt`
- Modify `app/src/main/java/app/drift/ui/screens/ProfileHubScreen.kt`

**Interfaces:**
- Consumes `AttentionPathStore.graph` and current selected content app IDs.
- Exposes a view-model toggle that delegates to `SettingsRepository`.

- [x] Expose graph state combined with selected content app IDs as a `StateFlow` in `DriftViewModel` and add `toggleProtectedPath(appId, selected)`.
- [x] Render each known node with label, session sample count, overrun association label, and protection switch when supported.
- [x] Render transitions as ordered source → destination rows with observed transition count and session count; explain that association is not causation.
- [x] Show an empty state explaining that navigation observations appear after Accessibility is enabled and supported screens are visited.
- [x] Add an “Attention paths” tab to `ProfileHubScreen` and connect it to the new screen.
- [x] Review scrolling, empty data, unsupported/unknown nodes, and selection persistence through code inspection only.

## Final source review

- [x] Search graph serialization and logs for caption, username, message, or screenshot fields; remove any raw-content persistence.
- [x] Confirm all selected node IDs come from the supported catalog before the service can block them.
- [x] Run `git diff --check` only on files changed for this feature; do not run Gradle, build, unit tests, or instrumentation tests per user instruction.
- [x] Record any remaining device-only behavior that requires a later manual check; do not claim runtime verification.
