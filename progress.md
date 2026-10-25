# Attention OS — Progress

**Updated:** 2026-10-25 20:20:36 +05:30  
**Milestone:** Session-segment Android UsageEvents monitoring integrated; real-device app checks still pending  
**Workspace:** `C:\IIIT Nagpur\attention_os`

## Decisions and technical approach

- Started from a new Flutter project because the workspace was empty. Kept the
  generated Android scaffold and package ID `com.attentionos.attention_os`.
- Used Material 3 and the existing centralized dark navy/purple/blue theme.
  Kept application state simple and avoided adding a state-management framework.
- Implemented the Home, Insights, Settings, session creation, active countdown,
  and summary screens. The deadline-based timer supports pause/resume, lifecycle
  reconciliation, end confirmation, and explicit completed/ended-early status.
- Kept the backend optional: a FastAPI service stores only user-created focus
  session summaries in SQLite, and the app requires an HTTPS base URL before
  syncing. It uses a random per-install bearer token; the server stores its
  SHA-256 hash and scopes records to that token. No hosted API has been deployed.
- Added a typed Flutter/Kotlin `MethodChannel` for Android version, Usage Access
  status/settings, and aggregate UsageStats queries. Android Usage Access is
  opt-in and granted manually in Android Settings. Selected package warnings
  are queried only while an enabled focus screen is open, and only for the
  current active segment.
- Kept usage records and app/package annotations local; session API payloads
  exclude them. Android UsageStats may be delayed and is not a real-time
  detector. No notification listener, Accessibility service, background
  monitoring, app blocking, or content inspection was added.
- No app-usage, notification, or session data is uploaded unless a user configures
  a backend and syncs a session. The app does not claim AI predictions or
  monitoring are active by default.
- Added a device test checklist to the root README. Automated tests do not
  replace manual permission, emulator, or physical-device checks.

## Main files created or modified

- Flutter app: `lib/main.dart`, `lib/app.dart`, `lib/core/theme/app_theme.dart`,
  `lib/models/focus_session.dart`, `lib/models/app_usage_record.dart`.
- Flutter screens and widgets: `lib/screens/home/home_screen.dart`,
  `lib/screens/focus/create_session_screen.dart`,
  `lib/screens/focus/active_session_screen.dart`,
  `lib/screens/focus/session_summary_screen.dart`,
  `lib/screens/insights/insights_screen.dart`,
  `lib/screens/settings/settings_screen.dart`,
  `lib/widgets/focus_timer.dart`, `lib/widgets/primary_button.dart`,
  `lib/widgets/section_heading.dart`, `lib/widgets/stat_card.dart`.
- Flutter services: `lib/services/focus_session_service.dart`,
  `lib/services/android_monitor_service.dart`.
- Android integration: `android/app/src/main/kotlin/com/attentionos/attention_os/MainActivity.kt`,
  `android/app/src/main/kotlin/com/attentionos/attention_os/UsageMonitorModels.kt`,
  `android/app/src/main/AndroidManifest.xml`,
  `android/app/build.gradle.kts`,
  `android/app/src/test/kotlin/com/attentionos/attention_os/UsageMonitorModelsTest.kt`,
  `android/gradle/wrapper/gradle-wrapper.properties`.
- Backend: `backend/app/main.py`, `backend/tests/test_main.py`,
  `backend/requirements.txt`, `backend/Dockerfile`, `backend/README.md`.
- Flutter tests: `test/widget_test.dart`,
  `test/active_session_monitor_test.dart`,
  `test/android_monitor_service_test.dart`,
  `test/focus_session_service_test.dart`,
  `test/session_summary_test.dart`, `test/settings_monitoring_test.dart`.
- Documentation: `README.md`, `progress.md`.

## Commands and observed results

| Command | Result |
| --- | --- |
| `flutter pub get` | Succeeded. Added the HTTP, shared-preferences, and secure-storage packages for optional session sync and preferences; Flutter reports newer versions outside the current constraints. |
| `dart format lib test` | Succeeded; latest run formatted 23 files with 0 changes. |
| `flutter analyze` | Succeeded: no issues found. |
| `flutter test` | Final run succeeded: 32 tests passed, including focus flow, timer/monitor behavior, API client, local-only usage annotations, and package-selection UI. |
| `python -m pytest -q` (from `backend`) | Succeeded: 14 API tests passed. |
| `.\gradlew.bat test` (from `android`) | Succeeded: Gradle `BUILD SUCCESSFUL` in 16m38s; includes the app Kotlin unit-test task. `UsageMonitorModelsTest.kt` defines 5 test cases. |
| `flutter build apk --debug` | Succeeded in 591.6s after Android SDK installed CMake 3.22.1. APK: `build\app\outputs\flutter-apk\app-debug.apk` (162,031,020 bytes); SHA-256 `3E94CD8E1F990DC075373FAE82E4E0022EF5CC76D47DD7493DF547CB461084C2`. |
| `flutter devices` | Initially detected Windows, Chrome, and Edge; no Android device was attached. |
| `flutter emulators --launch Medium_Phone_API_37.0` | Launch command returned successfully, but the emulator did not become usable during this run. |
| `C:\Users\hp\AppData\Local\Android\Sdk\platform-tools\adb.exe devices -l` | Reports `emulator-5554 offline`; no APK install or emulator smoke test was possible. `adb` is not in PowerShell PATH. |

## Remaining verification and limitations

1. Resolve the emulator's offline state or connect a physical Android phone,
   then install the verified APK and perform the manual checklist in `README.md`.
   No APK install, emulator smoke test, or physical-device test has yet occurred.
2. The FastAPI backend is deployable but not hosted. A hosting account/provider,
   HTTPS URL, persistent storage, and production operational safeguards are
   needed before live cloud sync can be tested.
3. Usage Access must be granted manually on an Android device for UsageStats
   checks. Results are aggregate and can be delayed.

## Next milestone

- Resolve emulator/USB connectivity, install the debug APK on an Android target,
  and complete the manual checklist in `README.md`.
- Choose a hosting provider and deploy the session API with HTTPS and persistent
  storage before configuring a real backend URL in the app.
- Consider local SQLite session persistence only after the first milestone is
  exercised on a device and offline retention requirements are confirmed.

## Review update — 2026-10-25

### What I checked

- Read this progress log, `README.md`, the Flutter Android-monitor service and
  usage-record model, `MainActivity.kt`, `UsageMonitorModels.kt`, the active
  focus screen, settings UI, and the MethodChannel unit tests.
- Confirmed the native query currently uses `UsageStatsManager.queryUsageStats`
  and returns aggregate package name, last-used timestamp, and total foreground
  time for the requested range. It does not parse `UsageEvents`, resolve app
  display names, or calculate foreground intervals bounded to the focus
  session.
- Confirmed the active screen polls once per minute while resumed, but only
  checks packages manually selected in Settings. A matching aggregate record
  causes a one-time informational warning; the screen does not show the
  requested detected-app list, per-app timestamps/durations, last successful
  query time, or classification explanations.
- Confirmed there is no configured distraction-rule engine or Android
  device-test evidence in the inspected implementation. The warning correctly
  says app activity does not prove distraction.

### Verification observed in this update

| Check | Result |
| --- | --- |
| Debug APK artifact | Present at `build\app\outputs\flutter-apk\app-debug.apk`, 162,031,020 bytes, last written 2026-10-25 19:08. This establishes that an artifact exists, not that a new build was run in this update. |
| `flutter test` | Attempted; command produced no output and did not return a usable result in the available execution window. Treat as unverified. |
| `flutter analyze` | Attempted; command produced no output and remained running without a usable result. Treat as unverified. |
| Android emulator | `emulator-5554` reported `offline`; no app launch, permission, or real usage query was verified. |
| Physical Android device | Not connected/tested in this update. |

### Remaining work and reasoning

The current feature is an initial aggregate UsageStats integration, not the
full session-specific monitoring feature described in the project brief.
Aggregate statistics can include use outside the focus interval, so matching a
package in those results must not be presented as proof that it was used during
that session. The current monitor also requires users to enter/select package
names and does not provide app names or an in-session results panel.

To meet the primary objective, the next implementation should query native
usage events for the actual focus-session interval, deduplicate/order relevant
transitions, stop intervals on screen-off/lock events, mark incomplete duration
as uncertain, and return structured records with package name and event times.
Flutter should display those records and monitoring/query status, while keeping
rule-based potential-distraction classification separate from observed usage.
Tests should cover the pure event processor and Dart parsing/classification;
real Instagram/Snapchat/Reddit checks must remain marked **Not tested** until
performed on a working emulator or physical device.

The APK, test, analyze, and emulator checks above were recorded as observed;
none is being reported as a newly successful verification. The previous test
counts above refer to earlier recorded runs and remain historical until the
full suite can be rerun successfully.

## Implementation update — 2026-10-25

### Implemented

- Added a native `getSessionAppUsage` MethodChannel method that queries Android
  `UsageEvents` only within the requested time interval. The existing aggregate
  query remains available in Settings for the 24-hour package-selection view;
  it is no longer used to decide which apps were observed in a focus session.
- Added a pure Kotlin processor that orders and deduplicates events, tracks
  foreground transitions, closes a foreground interval when another app or
  Attention OS resumes, and closes intervals on screen-off/keyguard events.
  Open or interrupted durations are marked incomplete. Records include the
  package identifier, display label when Android package visibility allows it,
  observed first/last timestamps, estimated duration, transition count, and
  data source.
- Added launcher-app package visibility to the manifest. If Android will not
  resolve a package label, the UI falls back to its package name.
- Added typed Dart parsing for session records and an active-session monitoring
  panel. It performs an initial query, polls once per minute while the screen is
  resumed, reconciles when the app resumes, and performs a final query when the
  session ends. Paused timer segments are queried separately and their
  estimates are combined without adding repeated cumulative polls twice.
- Shows observed apps, timestamps, estimated duration and incomplete status,
  event count/source, last successful update, and query/permission errors. The
  package warning list remains a user-selected rule; observed use alone does
  not classify an app as distracting.
- Updated the device checklist and UsageEvents explanation in `README.md`.
- Added Kotlin tests for ordering, duplicate removal, interval filtering,
  screen-off closure, open intervals, and package preservation. Added Dart
  tests for native-record parsing and active-session rendering.

### Verification from this implementation pass

| Command/check | Result |
| --- | --- |
| `dart format lib test` | Succeeded; final formatting run reported no changes. |
| `flutter analyze` | Succeeded: no issues found. |
| `flutter test --no-pub` | Succeeded: 37 tests passed. |
| `.\gradlew.bat test --console=plain` (from `android`) | Succeeded: `BUILD SUCCESSFUL`; Android app Kotlin unit-test task ran. |
| `flutter build apk --debug --no-pub` | Succeeded; generated `build\app\outputs\flutter-apk\app-debug.apk` (184,416,519 bytes, 2026-10-25 19:57). |
| `adb devices -l` | No connected Android devices were listed. |
| Instagram/Snapchat/Reddit/YouTube/Chrome/WhatsApp | Not tested on a device; no real usage result is claimed. |

### Current limits and follow-up

- UsageEvents is a delayed system event history, not an instantaneous
  foreground-app stream. Polling runs only while Attention OS is visible; the
  next query reconciles events after resume. There is no background service.
- Foreground durations are estimates. A running interval at query time is
  incomplete until the next transition is observed; screen-off/keyguard stops
  the interval and marks it incomplete. Apps already foreground before a
  session boundary, clock changes, OEM event omissions, and stale system data
  can still make duration incomplete or unavailable.
- A real device check is still needed to validate OEM/API behavior, permission
  revocation UI, popular app package/name resolution, and observed session
  boundaries. Follow the checklist in `README.md`; record unavailable apps as
  **Not tested**.
- Classification remains deliberately simple: only packages selected by the
  user trigger the optional informational warning. No grace period, allowed
  duration, overrides by category, or goal-content inference is implemented.

## User-requested app picker follow-up — 2026-10-25

Android has no system permission dialog that lets Attention OS grant access to
individual target applications. The system Usage Access page grants the special
access to Attention OS itself. To give the user the requested choice, added an
Attention OS **Choose apps** picker in Settings. It lists installed launcher
apps returned by Android, saves selected package IDs in local preferences, and
uses that independent list to filter session results. The existing optional
focus-warning list stays separate; selecting Instagram to track does not mark
it as distracting.

The app picker obtains labels and package IDs through a new
`getTrackableApps` MethodChannel method. Launcher-app visibility is declared in
the manifest. The app picker can be used before Usage Access is granted, but
monitoring still reports no results until the user enables Usage Access for
Attention OS in Android Settings. Updated `README.md` with this distinction and
the manual test steps.

### Final checks after the app-picker change

| Command/check | Result |
| --- | --- |
| `dart format lib test` | Succeeded; no further changes after formatting. |
| `flutter analyze --no-pub` | Succeeded: no issues found. |
| `flutter test --no-pub` | Succeeded: 39 tests passed, including installed-app selection and active-session rendering. |
| `.\gradlew.bat test --console=plain` (from `android`) | Succeeded: `BUILD SUCCESSFUL`; app Kotlin unit tests compiled and ran. |
| `flutter build apk --debug --no-pub` | Succeeded after the automatic permission-to-picker flow; `build\app\outputs\flutter-apk\app-debug.apk`, 184,423,617 bytes, last written 2026-10-25 20:20. |
| `adb devices -l` | No connected Android devices. |
| Actual Instagram/Snapchat/Reddit usage | Not tested on a device; do not treat the picker/parser fixture as a device detection result. |

When the user enables monitoring and Usage Access is already granted, Settings
now opens the in-app app picker immediately if no apps are selected. If Android
Usage Access was missing, enabling the preference opens Android's special
access settings; on return the app rechecks access and opens the app picker if
permission was granted. A focused widget test covers the granted-access flow.
This is the closest supported flow to a direct permission/app selection prompt:
Android grants Usage Access to Attention OS, and Attention OS then asks which
launcher apps to include.

## Backend status check — 2026-10-25

The backend remains a local/deployable prototype for focus-session summaries.
`python -m pytest -q` could not create pytest's default temp directory in the
restricted user temp path during this check. Rerunning with pytest temp files
inside the workspace (`python -m pytest -q -p no:cacheprovider --basetemp=...`)
succeeded: **14 passed**. It is not deployed; there is no production HTTPS URL
configured. It does not receive app-usage records, which remain on-device.
Before a public production deployment, provide persistent storage and HTTPS,
and add account recovery, rate limiting/abuse controls, backups, and a retention
and deletion policy, as documented in `backend/README.md`.
