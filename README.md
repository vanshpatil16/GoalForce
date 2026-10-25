# Attention OS

Flutter productivity app with an optional Android UsageStats integration and
an optional FastAPI focus-session sync service.

## Run Flutter tests and app

From PowerShell in the project directory:

```powershell
flutter pub get
dart format lib test
flutter analyze
flutter test
flutter run
```

To create a debug APK after Android SDK and Gradle are installed:

```powershell
flutter build apk --debug
```

The APK is written to
`build\app\outputs\flutter-apk\app-debug.apk`. A debug APK can be installed on
a test phone, but it is not a Play Store release.

Install it on a USB-connected Android device with USB debugging enabled:

```powershell
adb install -r build\app\outputs\flutter-apk\app-debug.apk
adb shell monkey -p com.attentionos.attention_os 1
```

## Android device test checklist

These are manual checks to perform on an emulator or phone; automated Flutter,
Kotlin, and backend tests do not replace real Android permission/device checks.

1. Create a focus session with a goal and each supported duration; verify an
   empty goal is rejected.
2. Start, pause, resume, and end a session; confirm unfinished-session
   confirmation, elapsed time, status, summary, and dashboard history.
3. Leave a session running, background the app, then return; confirm the
   countdown reconciles safely.
4. In Settings, inspect the Usage Access explanation and native connection
   result. Grant Usage Access manually only if you choose to test it.
5. In Settings, tap **Choose apps** under “Apps to track during focus” and
   select installed launcher apps. This is an Attention OS picker; Android does
   not provide a separate system permission dialog for choosing other apps.
   Optionally mark packages for focus warnings separately. Tracking by itself
   does not classify an app as distracting.
6. Start a focus session, use a selected app briefly, and return to Attention OS.
   The active-session panel should list applications reported by Android
   `UsageEvents` within the current active timer segment. Expand an entry to
   inspect its package ID, transition count, and event source. Disable checks
   and confirm polling stops.
7. Verify notification access remains unrequested, and app-usage data is not
   present in backend session payloads.
8. Leave the backend URL blank for offline use. Session sync is optional; use
   HTTPS for a deployed backend or the LAN-enabled debug APK for a private
   Wi-Fi test. No public service is configured.

## Android UsageStats

Usage access is an Android special access, not a normal runtime permission.
Open the Android App Usage Monitoring section in Settings, read the explanation,
then open Android Usage Access settings to enable it. The app rechecks access
when it resumes. Android's screen grants access to Attention OS; it cannot
select target apps. After access is granted, Attention OS opens its own app
picker so the user can choose installed launcher apps to track. During an
opted-in focus session, Android `UsageEvents` are
queried once on start, then every minute while Attention OS is visible, and
once more when the session ends. The query uses the current active timer
segment, so paused time is excluded. Choose which launcher apps to include in
Settings; the selected list is separate from the optional focus-warning list.
Observed application transitions, event timestamps, and estimated foreground
intervals stay on-device and are never synced to the backend. An interval still
open at query time or cut short by screen-off/keyguard is marked incomplete.
Android may delay or omit events; this is periodic reconciliation, not an
instantaneous foreground-app stream. Display names are resolved when package
visibility permits and otherwise fall back to package IDs. There is no
background service, notification listener, app blocking, or content
inspection. Observed use does not itself imply distraction; optional focus
warnings apply only to packages the user selected in Settings.

## FastAPI backend

The deployable prototype service and its test/deployment instructions are in
[backend/README.md](./backend/README.md). No cloud instance is deployed yet.
Configure its HTTPS base URL in the app's Settings for a deployed instance.
On the private Wi-Fi debug setup, focus sessions and preferred focus/reminder
settings sync to SQLite. App-usage records, package names, and monitoring
preferences stay on-device.

For a phone-to-laptop test on the same Wi-Fi, start FastAPI on
`0.0.0.0:8000`, find the laptop Wi-Fi IPv4 address with `ipconfig`, and enter
`http://<laptop-private-ip>:8000` in Settings → Session sync backend. Build
the LAN-testing debug APK with
`flutter build apk --debug --dart-define=ATTENTION_OS_ALLOW_LAN_HTTP=true`.
No router port forwarding is needed; allow TCP 8000 only from the local subnet
on the Windows Private firewall profile if required. Detailed steps and the
firewall command are in [backend/README.md](./backend/README.md). Do not expose
the HTTP development server to the public internet.
