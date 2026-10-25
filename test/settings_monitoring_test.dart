import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:attention_os/models/app_usage_record.dart';
import 'package:attention_os/screens/settings/settings_screen.dart';
import 'package:attention_os/services/android_monitor_service.dart';

class _FakeMonitorService extends AndroidMonitorService {
  bool accessGranted = true;
  int androidVersion = 35;
  int accessChecks = 0;
  int usageQueries = 0;
  List<AppUsageRecord> usageRecords = const [];
  List<TrackableApp> trackableApps = const [];
  Completer<bool>? pendingAccess;
  AndroidMonitorException? failure;

  @override
  Future<bool> hasUsageAccess() async {
    accessChecks++;
    if (pendingAccess != null) return pendingAccess!.future;
    if (failure != null) throw failure!;
    return accessGranted;
  }

  @override
  Future<int> getAndroidVersion() async {
    if (failure != null) throw failure!;
    return androidVersion;
  }

  @override
  Future<bool> openUsageAccessSettings() async => true;

  @override
  Future<List<AppUsageRecord>> getRecentAppUsage({
    required int startTimeMs,
    required int endTimeMs,
  }) async {
    usageQueries++;
    return usageRecords;
  }

  @override
  Future<List<TrackableApp>> getTrackableApps() async => trackableApps;
}

void main() {
  Future<void> pumpSettings(
    WidgetTester tester, {
    required _FakeMonitorService service,
    bool monitoringEnabled = false,
    void Function(bool enabled)? onMonitoringChanged,
    void Function(String packages)? onPackagesChanged,
    void Function(String packages)? onTrackedAppsChanged,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SettingsScreen(
            preferredDuration: 25,
            remindersEnabled: false,
            onPreferencesChanged: (_, _) async {},
            apiBaseUrl: '',
            syncStatus: 'Backend not configured.',
            onBackendUrlSaved: (_) async {},
            monitoringEnabled: monitoringEnabled,
            distractionPackages: '',
            onTrackedPackagesChanged: (packages) async {
              onTrackedAppsChanged?.call(packages);
            },
            onMonitoringPreferencesChanged:
                ({required enabled, required distractionPackages}) async {
                  onMonitoringChanged?.call(enabled);
                  onPackagesChanged?.call(distractionPackages);
                },
            monitorService: service,
          ),
        ),
      ),
    );
  }

  testWidgets('shows monitoring controls and the current permission status', (
    tester,
  ) async {
    final service = _FakeMonitorService();
    await pumpSettings(tester, service: service);
    await tester.pumpAndSettle();

    expect(find.text('Android App Usage Monitoring'), findsOneWidget);
    expect(find.text('Enable app-usage checks'), findsOneWidget);
    expect(find.text('Usage access is granted'), findsOneWidget);
    expect(find.text('Test native connection'), findsOneWidget);
  });

  testWidgets('lets the user choose installed apps to track', (tester) async {
    final service = _FakeMonitorService()
      ..trackableApps = const [
        TrackableApp(
          packageName: 'com.instagram.android',
          displayName: 'Instagram',
        ),
      ];
    String? savedPackages;
    await pumpSettings(
      tester,
      service: service,
      onTrackedAppsChanged: (packages) => savedPackages = packages,
    );
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView).first, const Offset(0, -250));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Choose apps'));
    await tester.pumpAndSettle();
    expect(find.text('Instagram'), findsOneWidget);
    expect(find.text('com.instagram.android'), findsOneWidget);
    await tester.tap(find.text('Instagram'));
    await tester.tap(find.text('Save selection'));
    await tester.pumpAndSettle();

    expect(savedPackages, 'com.instagram.android');
    expect(
      find.text(
        '1 app(s) selected. Tracking does not label them as distracting.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('shows loading state while checking access', (tester) async {
    final service = _FakeMonitorService()..pendingAccess = Completer<bool>();
    await pumpSettings(tester, service: service);
    await tester.pump();

    expect(find.text('Checking usage-access permission…'), findsOneWidget);
    service.pendingAccess!.complete(false);
    await tester.pumpAndSettle();
    expect(find.text('Usage access is not granted'), findsOneWidget);
  });

  testWidgets('shows permission denial clearly', (tester) async {
    final service = _FakeMonitorService()..accessGranted = false;
    await pumpSettings(tester, service: service);
    await tester.pumpAndSettle();

    expect(find.text('Usage access is not granted'), findsOneWidget);
    expect(find.textContaining('grant Usage Access manually'), findsOneWidget);
  });

  testWidgets('shows native connection test result', (tester) async {
    final service = _FakeMonitorService();
    await pumpSettings(tester, service: service);
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView), const Offset(0, -300));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Test native connection'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Test native connection'));
    await tester.pumpAndSettle();

    expect(find.text('Connected to Android API 35.'), findsOneWidget);
  });

  testWidgets('shows typed app-usage results from the native service', (
    tester,
  ) async {
    final service = _FakeMonitorService()
      ..usageRecords = [
        AppUsageRecord(
          packageName: 'com.example.reader',
          lastTimeUsed: DateTime.utc(2026, 10, 25, 12),
          totalTimeInForeground: const Duration(minutes: 4),
        ),
      ];
    await pumpSettings(tester, service: service);
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Check last 24 hours'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Check last 24 hours'));
    await tester.pumpAndSettle();

    expect(service.usageQueries, 1);
    expect(find.text('com.example.reader'), findsOneWidget);
  });

  testWidgets('selects usage results as focus alert targets', (tester) async {
    final service = _FakeMonitorService()
      ..usageRecords = [
        AppUsageRecord(
          packageName: 'com.example.reader',
          lastTimeUsed: DateTime.utc(2026, 10, 25, 12),
          totalTimeInForeground: const Duration(minutes: 4),
        ),
      ];
    String? savedPackages;
    await pumpSettings(
      tester,
      service: service,
      onPackagesChanged: (value) => savedPackages = value,
    );
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView), const Offset(0, -300));
    await tester.pumpAndSettle();
    await tester.dragUntilVisible(
      find.text('Check last 24 hours'),
      find.byType(ListView),
      const Offset(0, -180),
    );
    await tester.tap(find.text('Check last 24 hours'));
    await tester.pumpAndSettle();
    await tester.dragUntilVisible(
      find.text('com.example.reader'),
      find.byType(ListView),
      const Offset(0, -180),
    );
    await tester.tap(find.text('com.example.reader'));
    await tester.pumpAndSettle();
    await tester.dragUntilVisible(
      find.text('Save app list'),
      find.byType(ListView),
      const Offset(0, -180),
    );
    await tester.tap(find.text('Save app list'));
    await tester.pumpAndSettle();

    expect(savedPackages, 'com.example.reader');
  });

  testWidgets('rechecks usage access when the app resumes', (tester) async {
    final service = _FakeMonitorService();
    await pumpSettings(tester, service: service);
    await tester.pumpAndSettle();
    final checksBeforeResume = service.accessChecks;

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();

    expect(service.accessChecks, greaterThan(checksBeforeResume));
  });

  testWidgets('shows monitoring errors without crashing', (tester) async {
    final service = _FakeMonitorService()
      ..failure = const AndroidMonitorException(
        'ANDROID_UNSUPPORTED',
        'Native Android monitoring is unavailable on this platform.',
      );
    await pumpSettings(tester, service: service);
    await tester.pumpAndSettle();

    expect(
      find.text('Native Android monitoring is unavailable on this platform.'),
      findsOneWidget,
    );
  });

  testWidgets('enabling monitoring reports the updated state', (tester) async {
    final service = _FakeMonitorService()
      ..trackableApps = const [
        TrackableApp(
          packageName: 'com.instagram.android',
          displayName: 'Instagram',
        ),
      ];
    bool? enabled;
    String? trackedApps;
    await pumpSettings(
      tester,
      service: service,
      onMonitoringChanged: (value) => enabled = value,
      onTrackedAppsChanged: (value) => trackedApps = value,
    );
    await tester.pumpAndSettle();
    final monitoringSwitch = find.descendant(
      of: find.widgetWithText(SwitchListTile, 'Enable app-usage checks'),
      matching: find.byType(Switch),
    );
    await tester.ensureVisible(monitoringSwitch);
    await tester.pumpAndSettle();
    await tester.tap(monitoringSwitch);
    await tester.pumpAndSettle();

    expect(enabled, isTrue);
    expect(service.accessChecks, greaterThanOrEqualTo(2));
    expect(find.text('Choose apps to track'), findsOneWidget);
    await tester.tap(find.text('Instagram'));
    await tester.tap(find.text('Save selection'));
    await tester.pumpAndSettle();
    expect(trackedApps, 'com.instagram.android');
  });
}
