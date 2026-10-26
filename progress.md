# Progress — Gamification & Rewards System (Drift / "Attention OS")

This document records the actual implementation status of the gamification spec
as applied to the **Drift** Kotlin/Compose Android app (the user's "Attention OS").

## Implemented (local, functional)

### XP & levels
- Append-only **XP transaction ledger** (`xp_transactions`) with a unique
  `idempotencyKey` index — the same completion event cannot award XP twice.
- **Currency separation**: `user_progress` tracks `lifetimeXp` (levels),
  `spendableBalance` (redemption) and `spentXp`. Redemption never reduces lifetime XP.
- **Configurable rules** (`XpRules`) with per-category base values.
- **Level progression** (`LevelSystem`) derived from persisted lifetime XP.
- Award categories: session completion, task completion, attention recovery,
  daily goal, achievement, and bounded variable bonus. Difficulty/study/group
  categories are defined but not yet awarded (no supporting data).

### Streaks & heatmap
- **Daily streak** (existing `StreakManager`, XP removed) + **weekly streak**
  (`StreakService`) computed from a persisted `daily_progress` table.
- **Contribution heatmap** (`ContributionHeatmap`) rendered from real daily
  progress, colour-mapped to qualifying minutes.

### Achievements
- `AchievementCatalog` (11 initial badges) + `AchievementService` evaluation.
- Idempotent unlock (`achievement_unlocks`, PK on achievementId) with one-time
  XP award via the ledger.

### Variable rewards
- `VariableRewardService`: injectable RNG, per-action probabilities, min/max,
  daily/session caps, cooldown. Deterministic unit tests cover award, no-award,
  caps, cooldown and disabled states.

### Rewards marketplace & redemption
- `RewardCatalog` + `RewardsScreen` (spendable balance, catalogue, owned state).
- **Atomic redemption** in the DAO (guard against negative balance + duplicate
  redemption) with a redemption history.

### Notification-cost estimation
- `NotificationCostService`: honest `P(drift) × E(T_away)` estimate from valid
  observations; returns `InsufficientData` (no fabricated number) when history is thin.
  Not yet wired to live Android notification interception (see below).

### Settings & privacy controls
- Toggles for gamification, variable rewards, notification-cost, social sharing,
  and leaderboard participation. Disabling gamification skips all XP awards.

## Abstracted / stubbed (honest states, no fake data)

- **Social / friends / leaderboards / group challenges**: data models and
  repository interfaces exist conceptually but there is **no backend**, so these
  show empty/`setup required` states. No fake friends or leaderboard values.
- **Notification interception (Android)**: no `NotificationListenerService` is
  implemented (requires user-enabled notification access + platform testing).
  The cost model is ready but fed by historical data only.
- **AI attention classification**: not implemented; recovery XP uses the
  accessibility-service re-entry signal, not a simulated classifier.
- **Fatigue data**: no fatigue model is collected; the XP system works without it.

## Database (Room) — schema v5 (destructive migration)

Added tables: `user_progress`, `xp_transactions` (unique `idempotencyKey`),
`achievement_unlocks`, `reward_redemptions` (unique `rewardId`),
`daily_progress`, `foreground_events` (fine-grained `APP_OPEN` /
`CONTENT_BLOCKED` events, now tagged with `sessionId`, monotonic
`elapsedRealtimeMs`, and `confidence`), and `session_summaries`
(per-session derived analytics). Existing tables unchanged.

## Tests

`app/src/test/java/app/drift/domain/` — `EngineTest.kt`, `AnalyticsTest.kt`,
`GamificationTest.kt`, `AssistantTest.kt`, `SessionAnalyticsTest.kt`.
61 unit tests, all passing (`./gradlew testDebugUnitTest`).

## Commands run

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

## Remaining work (prioritized)

1. Android instrumentation tests for the Room DAO idempotency/atomicity.
2. Wire `NotificationCostService` to a real `NotificationListenerService`
   (user-consented) and feed real interruption observations.
3. Train + bundle a real `drift_risk_model.tflite`.
4. Backend for friends/leaderboards/challenges.
5. Personal-best tracking and per-session XP breakdown UI.

## AI Partner (assistant) — implemented

The Assistant tab now answers the user with **live on-device behavioural data**
and a growing **Hermes-style memory**, powered by the Mistral API.

- **Fine-grained logging** — a new `foreground_events` table records every
  foreground `APP_OPEN` and every `CONTENT_BLOCKED` (Reels/Shorts) event, so the
  assistant can surface signals aggregate sessions hide (e.g. "Instagram opened
  4 times in 1 minute" via `AttentionAnalytics.bursts`).
- **Attention report** (`AttentionReport`) — opens-today, top apps, heaviest
  burst, blocked short-form count, sessions/overruns/re-entries today; injected
  into every prompt.
- **Memory** (`AssistantMemoryStore` + `MemoryExtractor`) — durable facts
  (goals/preferences/opinions/context) are extracted from the user's messages and
  appended to a `memory.md` file, then injected into subsequent prompts so the
  assistant gets more personal over time.
- **Mistral client** (`MistralClient`) — JDK `HttpURLConnection` + `org.json`
  (no new dependency). Key is injected via `BuildConfig.MISTRAL_API_KEY`, read
  from a gitignored `.env` / `local.properties` / `MISTRAL_API_KEY` env var.
- **Offline fallback** (`OfflineAssistant`) — deterministic, data-aware answers
  when the network or key is unavailable, so the feature never silently breaks.
- **Wiring** — `AssistantService` (domain orchestrator), `AppContainer`, and a
  single `vm.askAssistant(question)` hook in `DriftViewModel`.

The frontend (`AssistantScreen.kt`) was left visually unchanged; only its
send-button handler was wired to `vm.askAssistant(...)`.

### Frontend build fixes (pre-existing, required to compile)
A prior pass left several files non-compiling. Fixed mechanically (no design
changes): a missing brace in `StatsScreen.kt`, missing Compose imports in
`SettingsScreen.kt` / `RewardsScreen.kt` / `InterventionActivity.kt`, and an
`it.achieved` → outcome check in `HomeScreen.kt`.

### Secret handling
The Mistral key is **not** committed: it lives in `.env` and `local.properties`
(both gitignored) and is read at build time. Note it is still compiled into the
debug APK via `BuildConfig`, so this is suitable for personal/debug use only —
a real backend proxy would be needed before a public release.

## Session tracking, summaries & coaching — implemented

- **Session-aware events** — `foreground_events` rows are tagged with the active
  `sessionId`, a monotonic `elapsedRealtimeMs`, and a `confidence` (OBSERVED for
  accessibility-derived events). `DriftAccessibilityService` stamps every
  `APP_OPEN` / `CONTENT_BLOCKED` with the current session.
- **Session summaries** — `SessionAnalytics` (pure, deterministic) derives a
  `SessionSummary` from the persisted event timeline at session end (planned vs
  actual duration, completion status, estimated restricted-app foreground time,
  distraction/restriction counts, focus score, tracking coverage).
  `DriftCoordinator.finishSession` computes + persists it to `session_summaries`.
- **XP rules** — added `DISTRACTION_FREE_BONUS` (+15, awarded when a session has
  zero distractions) and `VERIFIED_DISMISSAL` (+3, configurable; not yet wired —
  requires a `NotificationListenerService`). All awards remain idempotent via the
  unique `idempotencyKey`.
- **AI coaching** — `SessionCoach` builds a privacy-preserving `CoachingContext`
  from a session summary + recent baseline, formats a structured LLM prompt, and
  provides a deterministic offline fallback. `AssistantService.coachSession` /
  `coachLastSession` (exposed via `DriftViewModel`) return evidence-backed
  coaching without inventing metrics.
- **Tests** — `SessionAnalyticsTest.kt` covers duration, distraction/restriction
  counting, tracking-coverage, late-event exclusion, determinism, and offline
  coaching.

### Honest limitations (not implemented / approximate)
- **Notification listening**: no `NotificationListenerService`; third-party
  notification open/dismiss cannot be reliably observed, so `VERIFIED_DISMISSAL`
  is defined but not awarded. The existing `notification_events` table tracks
  Drift's own reminders only.
- **Content-level detection**: usage APIs cannot reveal *which* Reel/Short is
  viewed; Reels/Shorts are detected only via the accessibility viewer node.
- **Foreground durations**: estimated by pairing consecutive `APP_OPEN` events
  (no explicit foreground-exit event), and are approximate when accessibility is
  off (usage stats are periodic).
- **Destructive migration**: the Room DB still uses `fallbackToDestructiveMigration()`,
  so the v5 schema reset local data (pre-existing behaviour).
