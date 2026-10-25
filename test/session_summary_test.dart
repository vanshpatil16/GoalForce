import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:attention_os/models/focus_session.dart';
import 'package:attention_os/screens/focus/session_summary_screen.dart';

void main() {
  final session = FocusSession(
    id: 'd3a0c9f4-7b4f-4e01-8a85-58bd3e6228af',
    goal: 'Study DSA',
    plannedMinutes: 25,
    startedAt: DateTime.utc(2026, 10, 25),
    remindersEnabled: false,
    actualDuration: const Duration(minutes: 25),
    status: FocusSessionStatus.completed,
    locallyObservedPackages: const ['com.example.video'],
  );

  testWidgets('summary returns self-report and keeps usage annotations local', (
    tester,
  ) async {
    FocusSession? returnedSession;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () async {
                returnedSession = await Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => SessionSummaryScreen(session: session),
                  ),
                );
              },
              child: const Text('Open summary'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open summary'));
    await tester.pumpAndSettle();

    expect(find.text('Session complete'), findsOneWidget);
    expect(find.text('com.example.video'), findsOneWidget);
    await tester.ensureVisible(find.text('1–2'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ChoiceChip, '1–2'));
    await tester.pump();
    expect(
      tester
          .widget<ChoiceChip>(find.widgetWithText(ChoiceChip, '1–2'))
          .selected,
      isTrue,
    );
    await tester.ensureVisible(find.text('Return to dashboard'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Return to dashboard'));
    await tester.pumpAndSettle();

    expect(find.text('Session complete'), findsNothing);
    expect(returnedSession?.reportedDistractions, 2);
    expect(returnedSession?.locallyObservedPackages, ['com.example.video']);
  });

  testWidgets('skipping the distraction question remains optional', (
    tester,
  ) async {
    FocusSession? returnedSession;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () async {
                returnedSession = await Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => SessionSummaryScreen(session: session),
                  ),
                );
              },
              child: const Text('Open summary'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open summary'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Skip'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Skip'));
    await tester.ensureVisible(find.text('Return to dashboard'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Return to dashboard'));
    await tester.pumpAndSettle();

    expect(returnedSession?.reportedDistractions, isNull);
  });
}
