import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:attention_os/services/android_monitor_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('attention_os_test/native');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late AndroidMonitorService service;

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
  });

  setUp(() {
    service = AndroidMonitorService(channel: channel);
  });

  test('parses Android SDK version', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (call) async => call.method == 'getAndroidVersion' ? 35 : null,
    );

    expect(await service.getAndroidVersion(), 35);
  });

  test('reads granted usage-access status', () async {
    messenger.setMockMethodCallHandler(channel, (_) async => true);

    expect(await service.hasUsageAccess(), isTrue);
  });

  test('reads denied usage-access status', () async {
    messenger.setMockMethodCallHandler(channel, (_) async => false);

    expect(await service.hasUsageAccess(), isFalse);
  });

  test('reports whether Android opened Usage Access settings', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (call) async => call.method == 'openUsageAccessSettings',
    );

    expect(await service.openUsageAccessSettings(), isTrue);
  });

  test('parses multiple app-usage records and milliseconds', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => [
        {
          'packageName': 'com.example.reader',
          'lastTimeUsedMs': 1700000000123,
          'totalTimeInForegroundMs': 4567,
        },
        {
          'packageName': 'com.example.music',
          'lastTimeUsedMs': 1700000010000,
          'totalTimeInForegroundMs': 8900,
        },
      ],
    );

    final records = await service.getRecentAppUsage(
      startTimeMs: 1700000000000,
      endTimeMs: 1700000100000,
    );

    expect(records, hasLength(2));
    expect(records.first.packageName, 'com.example.reader');
    expect(records.first.lastTimeUsed.millisecondsSinceEpoch, 1700000000123);
    expect(records.first.totalTimeInForeground.inMilliseconds, 4567);
    expect(records.last.packageName, 'com.example.music');
  });

  test('accepts an empty usage result', () async {
    messenger.setMockMethodCallHandler(channel, (_) async => <Object?>[]);

    expect(
      await service.getRecentAppUsage(startTimeMs: 100, endTimeMs: 200),
      isEmpty,
    );
  });

  test(
    'parses session-bounded event records and uncertainty metadata',
    () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'getSessionAppUsage');
        return [
          {
            'packageName': 'com.example.reader',
            'displayName': 'Reader',
            'firstObservedAtMs': 1700000000100,
            'lastObservedAtMs': 1700000000400,
            'estimatedForegroundMs': 300,
            'transitionCount': 2,
            'durationIncomplete': false,
            'dataSource': 'Android UsageEvents',
          },
        ];
      });

      final records = await service.getSessionAppUsage(
        startTimeMs: 1700000000000,
        endTimeMs: 1700000000500,
      );

      expect(records.single.packageName, 'com.example.reader');
      expect(records.single.displayName, 'Reader');
      expect(
        records.single.firstObservedAt.millisecondsSinceEpoch,
        1700000000100,
      );
      expect(records.single.estimatedForegroundDuration.inMilliseconds, 300);
      expect(records.single.transitionCount, 2);
      expect(records.single.durationIncomplete, isFalse);
    },
  );

  test('parses Android launcher apps for the tracking picker', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => [
        {'packageName': 'com.instagram.android', 'displayName': 'Instagram'},
      ],
    );

    final apps = await service.getTrackableApps();
    expect(apps.single.packageName, 'com.instagram.android');
    expect(apps.single.displayName, 'Instagram');
  });

  test('rejects malformed session event records', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => [
        {'packageName': 'com.example.app', 'firstObservedAtMs': 'not-a-time'},
      ],
    );

    await expectLater(
      service.getSessionAppUsage(startTimeMs: 100, endTimeMs: 200),
      throwsA(
        isA<AndroidMonitorException>().having(
          (error) => error.code,
          'code',
          'INVALID_RESPONSE',
        ),
      ),
    );
  });

  test('passes the requested interval to the native method', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getRecentAppUsage');
      expect(call.arguments, {'startTimeMs': 101, 'endTimeMs': 205});
      return <Object?>[];
    });

    await service.getRecentAppUsage(startTimeMs: 101, endTimeMs: 205);
  });

  test('rejects usage intervals with negative start', () async {
    await expectLater(
      service.getRecentAppUsage(startTimeMs: -1, endTimeMs: 10),
      throwsArgumentError,
    );
  });

  test('rejects an end timestamp that is not after the start', () async {
    await expectLater(
      service.getRecentAppUsage(startTimeMs: 10, endTimeMs: 10),
      throwsArgumentError,
    );
    await expectLater(
      service.getRecentAppUsage(startTimeMs: 11, endTimeMs: 10),
      throwsArgumentError,
    );
  });

  test('rejects malformed usage record fields', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => [
        {'packageName': 'com.example.reader'},
      ],
    );

    await expectLater(
      service.getRecentAppUsage(startTimeMs: 100, endTimeMs: 200),
      throwsA(
        isA<AndroidMonitorException>().having(
          (error) => error.code,
          'code',
          'INVALID_RESPONSE',
        ),
      ),
    );
  });

  test('maps native platform errors into typed errors', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => throw PlatformException(
        code: 'USAGE_ACCESS_DENIED',
        message: 'Permission required',
      ),
    );

    await expectLater(
      service.getRecentAppUsage(startTimeMs: 100, endTimeMs: 200),
      throwsA(
        isA<AndroidMonitorException>()
            .having((error) => error.code, 'code', 'USAGE_ACCESS_DENIED')
            .having((error) => error.message, 'message', 'Permission required'),
      ),
    );
  });

  test('maps an unknown native error code without hiding it', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => throw PlatformException(
        code: 'FUTURE_NATIVE_ERROR',
        message: 'Unexpected native failure',
      ),
    );

    await expectLater(
      service.getAndroidVersion(),
      throwsA(
        isA<AndroidMonitorException>().having(
          (error) => error.code,
          'code',
          'FUTURE_NATIVE_ERROR',
        ),
      ),
    );
  });

  test('reports missing native plugin as unsupported platform', () async {
    messenger.setMockMethodCallHandler(channel, null);

    await expectLater(
      service.hasUsageAccess(),
      throwsA(
        isA<AndroidMonitorException>().having(
          (error) => error.code,
          'code',
          'ANDROID_UNSUPPORTED',
        ),
      ),
    );
  });

  test('rejects an invalid native permission response', () async {
    messenger.setMockMethodCallHandler(channel, (_) async => 'allowed');

    await expectLater(
      service.hasUsageAccess(),
      throwsA(
        isA<AndroidMonitorException>().having(
          (error) => error.code,
          'code',
          'INVALID_RESPONSE',
        ),
      ),
    );
  });
}
