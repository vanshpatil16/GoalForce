import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:attention_os/models/app_usage_record.dart';
import 'package:attention_os/models/focus_session.dart';
import 'package:attention_os/screens/focus/active_session_screen.dart';
import 'package:attention_os/services/android_monitor_service.dart';

class _NoCallMonitorService extends AndroidMonitorService {
  int methodCalls = 0;

  @override
  Future<bool> hasUsageAccess() async {
    methodCalls++;
    return true;
  }

  @override
  Future<List<AppUsageRecord>> getRecentAppUsage({
    required int startTimeMs,
    required int endTimeMs,
  }) async {
    methodCalls++;
    return const [];
  }
}

class _SessionUsageMonitorService extends AndroidMonitorService {
  @override
  Future<bool> hasUsageAccess() async => true;

  @override
  Future<List<SessionAppUsageRecord>> getSessionAppUsage({
    required int startTimeMs,
    required int endTimeMs,
  }) async => [
    SessionAppUsageRecord(
      packageName: 'com.example.reader',
      displayName: 'Reader',
      firstObservedAt: DateTime.fromMillisecondsSinceEpoch(startTimeMs + 100),
      lastObservedAt: DateTime.fromMillisecondsSinceEpoch(startTimeMs + 500),
      estimatedForegroundDuration: const Duration(milliseconds: 400),
      transitionCount: 2,
      durationIncomplete: false,
      dataSource: 'Android UsageEvents',
    ),
  ];
}

void main() {
  testWidgets('disabled usage monitoring makes no native calls', (
    tester,
  ) async {
    final monitor = _NoCallMonitorService();
    await tester.pumpWidget(
      MaterialApp(
        home: ActiveSessionScreen(
          session: FocusSession(
            id: 'd3a0c9f4-7b4f-4e01-8a85-58bd3e6228af',
            goal: 'Study DSA',
            plannedMinutes: 25,
            startedAt: DateTime.now(),
            remindersEnabled: false,
          ),
          monitoringEnabled: false,
          distractionPackages: 'com.example.video',
          monitorService: monitor,
        ),
      ),
    );

    expect(find.text('Study DSA'), findsOneWidget);
    expect(monitor.methodCalls, 0);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('shows returned session app usage and package details', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: ActiveSessionScreen(
          session: FocusSession(
            id: 'd3a0c9f4-7b4f-4e01-8a85-58bd3e6228af',
            goal: 'Study DSA',
            plannedMinutes: 25,
            startedAt: DateTime.now(),
            remindersEnabled: false,
          ),
          monitoringEnabled: true,
          trackedPackages: 'com.example.reader',
          monitorService: _SessionUsageMonitorService(),
        ),
      ),
    );
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Observed applications'), findsOneWidget);
    expect(find.text('Reader'), findsOneWidget);
    expect(find.text('com.example.reader'), findsNothing);
    await tester.tap(find.text('Reader'));
    await tester.pump();
    expect(find.text('com.example.reader'), findsOneWidget);

    await tester.pumpWidget(const SizedBox());
  });
}
