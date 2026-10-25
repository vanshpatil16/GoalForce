# Drift — the Infinite Scroll Intervention Engine

> **Detect the drift. Protect the intent.**

Drift is an Android app that detects when your *actual* usage diverges from your
*stated* intention, and introduces **personalized, non-blocking interventions** —
not a screen-time jail. It never assumes long screen time is bad; it only steps
in when what you're doing stops matching what you said you wanted to do.

Built as a clean-room reimplementation of the mechanics analyzed from the "one
sec" APK (accessibility-service detection → decision engine → full-screen
intervention → foreground timer), reimagined around **intentionality** and
enriched with **Duolingo's KDD-2020 multi-armed-bandit** approach to
personalization and rewards.

---

## How it maps to the problem statement

| Requirement | Implementation |
|---|---|
| *Ask users to define purpose + intended duration before a session* | `IntentionScreen` → `UserIntent(purpose, intendedMinutes, kind, targetPackage)` |
| *Detect session overruns* | `IntentEngine`: `elapsed > intended` ⇒ `OVER_RUN` |
| *Detect repeated attempts to exit* | `IntentEngine`: repeated exit/re-enter within 3 min ⇒ `COMPULSIVE_REENTRY` |
| *Context-aware stopping points & exit prompts* | `InterventionActivity` with 5 templates chosen by context |
| *Distinguish intentional long-form from compulsive browsing* | `LONG_FORM` + single-app focus ⇒ `ON_TRACK` (never interrupted); re-entry + app-churn ⇒ compulsive |
| *Measure whether interventions improve outcomes* | `OutcomeEvaluator`: overrun-rate **with vs. without** intervention |
| *Detect loss of intentionality without assuming all long time is harmful* | Time is **never** the sole signal — the anchor is the user's own declared intent |
| *Usage pattern analysis* | `UsagePatternWorker` (UsageStatsManager, WorkManager) |
| *Android usage-access APIs* | `PACKAGE_USAGE_STATS` + accessibility service |
| *Intervention experiments* | `InterventionBandit` (explore/exploit over templates) |
| *Behavioral modeling* | Reward estimation, novelty discounting, eligibility constraints |

## The core loop

```
[You open Instagram after declaring "reply to DMs, 10 min"]
        │
        ▼
DriftAccessibilityService  (TYPE_WINDOW_STATE_CHANGED + periodic overrun tick)
        │
        ▼
IntentEngine.evaluate(intent, snapshot)
        │  → ON_TRACK | OVER_RUN | COMPULSIVE_REENTRY
        ▼
InterventionBandit.select(history, driftKind, hasStreak)   ← picks a template
        │
        ▼
InterventionActivity   (full-screen, "Continue intentionally" / "Give me 5 more" / "I'm done")
        │
        ▼
OutcomeEvaluator  → bandit reward  →  StreakManager (streak + XP + wager)
```

## The Duolingo-inspired pieces

The paper (`yancey.kdd20`) is Duolingo's **multi-armed bandit for notification
content**. The ideas it contributes, and where they live here:

1. **Bandit over intervention templates** (`InterventionBandit`) — arms are
   `STOPPING_POINT`, `EXIT_PROMPT`, `BREATH_PAUSE`, `REFLECTION`, `REWARD_NUDGE`.
2. **Intent-relative reward** — reward = 1 only if the session ended *on-time or
   on-purpose* after the intervention (mirrors "completed a lesson within two
   hours of the notification"). Engagement that isn't *your* goal doesn't count.
3. **Novelty-effect discounting** — a fresh template gets a decaying bonus so the
   bandit doesn't over-exploit something merely because it's new.
4. **Recovering/rested arms** — a just-shown template is temporarily
   down-weighted.
5. **Sleeping arms** — `REWARD_NUDGE` is only eligible when you have an active
   streak (Duolingo's "streak wager" eligibility).
6. **Streak + XP + wager** (`StreakManager`) — meet today's intent to keep (or
   double, via wager) your streak; a freeze absorbs a missed day.

## Architecture

```
app.drift
├── DriftApp.kt / di/AppContainer.kt      (manual DI, application scope)
├── domain/                                (pure, unit-tested — the "engine")
│   ├── IntentEngine.kt                    (intentionality detection)
│   ├── InterventionBandit.kt              (multi-armed bandit)
│   ├── OutcomeEvaluator.kt                (measurement)
│   ├── StreakManager.kt                   (streak/XP/wager)
│   └── DriftCoordinator.kt                (orchestrates the loop)
├── data/
│   ├── db/DriftDatabase.kt                (Room: intents, sessions, interventions)
│   ├── DriftRepository.kt                 (data access + active-session state)
│   └── SettingsRepository.kt              (DataStore: streak + preferences)
├── service/
│   ├── DriftAccessibilityService.kt       (foreground-switch + overrun detection)
│   └── UsagePatternWorker.kt              (usage-access pattern analysis)
├── notification/                          (reminders + streak nudges)
├── intervention/InterventionActivity.kt   (full-screen contextual prompt + breath)
└── ui/                                    (Compose: Intent / Today / Progress / Settings)
```

Key design decisions:

- **Pure domain layer.** `IntentEngine`, `InterventionBandit`,
  `OutcomeEvaluator`, and `StreakManager` have no Android dependencies and are
  covered by unit tests (`app/src/test/.../EngineTest.kt` — 13 tests).
- **Intent, not time, is the anchor.** The `ON_TRACK` path is the default; drift
  is the exception.
- **Never blocks.** Every intervention offers "continue", "extend", and "done".
- **Manual DI.** No Hilt, so the scaffold is small and easy to reason about.

## Build & run

Prerequisites: **JDK 17**, **Android SDK 35**. Then:

```bash
./gradlew assembleDebug       # or: gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest   # run the engine's unit tests
```

On this machine the build was verified with Gradle 8.10.2 / AGP 8.7.3 /
Kotlin 2.0.21 / Compose BOM 2024.10.01.

To try it on a device:

1. Install the APK, open **Drift**, and create an intention in the **Intent** tab.
2. Grant **Usage access** and enable the **Drift accessibility service** (Settings tab).
3. Open your target app. Drift will offer a contextual nudge if you drift.

## Roadmap (natural next steps)

- **Personalized notification *timing*** — a second bandit over "when you're most
  receptive", using the usage patterns already collected.
- **Natural stopping points** — detect scroll-bottom / idle via accessibility
  node inspection (the `NATURAL_STOPPING` drift kind is already modeled).
- **A/B experiment harness** — persist arm assignment + reward for offline
  analysis, and surface per-template success rates in the Progress screen.
- **Cross-platform** — the domain layer is portable; only the detector/overlay
  layers are Android-specific.

## Note on inspiration & IP

Drift is an independent implementation. It reproduces the *mechanics* and
*behavior* of "one sec" and the *methodology* of the Duolingo paper (ideas,
not code or assets). If you ship it commercially, be mindful that "one sec"
discloses a US patent on its breathing-interruption method — the mindful-usage
prompt space has many non-infringing formulations, but consult a lawyer before
a US release.
