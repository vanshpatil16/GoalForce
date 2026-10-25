import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:attention_os/app.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('dashboard loads with focus action and navigation', (
    tester,
  ) async {
    await tester.pumpWidget(const AttentionOsApp());

    expect(find.text('ATTENTION OS'), findsOneWidget);
    expect(find.text('Your Attention. Your Rules.'), findsOneWidget);
    expect(find.text('Start Focus Session'), findsOneWidget);
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Insights'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
  });

  testWidgets('focus session form validates an empty goal', (tester) async {
    await tester.pumpWidget(const AttentionOsApp());
    await tester.tap(find.text('Start Focus Session'));
    await tester.pumpAndSettle();

    await tester.drag(find.byType(ListView), const Offset(0, -800));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Start session'));
    await tester.pumpAndSettle();

    expect(find.text('Enter a goal to start your session.'), findsOneWidget);
  });
}
