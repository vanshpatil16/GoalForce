import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:attention_os/models/focus_session.dart';
import 'package:attention_os/services/focus_session_service.dart';

void main() {
  final token = List.filled(64, 'a').join();
  final startedAt = DateTime.utc(2026, 10, 25, 12);

  FocusSession completedSession() => FocusSession(
    id: 'd3a0c9f4-7b4f-4e01-8a85-58bd3e6228af',
    goal: 'Study DSA',
    plannedMinutes: 25,
    startedAt: startedAt,
    remindersEnabled: false,
    actualDuration: const Duration(minutes: 25),
    status: FocusSessionStatus.completed,
    reportedDistractions: 2,
    locallyObservedPackages: const ['com.example.video'],
  );

  test(
    'posts a completed session but never uploads usage package names',
    () async {
      var didSend = false;
      final client = MockClient((request) async {
        didSend = true;
        expect(request.method, 'POST');
        expect(request.url.path, '/v1/sessions');
        expect(request.headers['Authorization'], 'Bearer $token');
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body['goal'], 'Study DSA');
        expect(body['actual_seconds'], 1500);
        expect(body['status'], 'completed');
        expect(body['reported_distractions'], 2);
        expect(body.containsKey('locallyObservedPackages'), isFalse);
        expect(body.toString().contains('com.example.video'), isFalse);
        return http.Response('{}', 201);
      });
      final service = FocusSessionService(client: client);
      addTearDown(service.dispose);

      await service.saveSession(
        baseUrl: 'https://attention-api.example.com/',
        token: token,
        session: completedSession(),
      );

      expect(didSend, isTrue);
    },
  );

  test('loads typed sessions from the backend', () async {
    final session = completedSession();
    final client = MockClient((request) async {
      expect(request.method, 'GET');
      expect(request.headers['Authorization'], 'Bearer $token');
      return http.Response(
        jsonEncode([
          {
            'id': session.id,
            'goal': session.goal,
            'planned_minutes': session.plannedMinutes,
            'started_at': session.startedAt.toIso8601String(),
            'actual_seconds': session.actualDuration.inSeconds,
            'status': 'completed',
            'reported_distractions': null,
          },
        ]),
        200,
      );
    });
    final service = FocusSessionService(client: client);
    addTearDown(service.dispose);

    final loaded = await service.loadSessions(
      baseUrl: 'https://attention-api.example.com',
      token: token,
    );

    expect(loaded, hasLength(1));
    expect(loaded.single.id, session.id);
    expect(loaded.single.status, FocusSessionStatus.completed);
    expect(loaded.single.reportedDistractions, isNull);
    expect(loaded.single.locallyObservedPackages, isEmpty);
  });

  test('loads and saves focus preferences without usage settings', () async {
    var savedPayload = <String, dynamic>{};
    final client = MockClient((request) async {
      if (request.method == 'GET') {
        expect(request.url.path, '/v1/preferences');
        return http.Response(
          '{"preferred_focus_duration":null,"reminders_enabled":null}',
          200,
        );
      }
      expect(request.method, 'PUT');
      expect(request.url.path, '/v1/preferences');
      savedPayload = jsonDecode(request.body) as Map<String, dynamic>;
      return http.Response(jsonEncode(savedPayload), 200);
    });
    final service = FocusSessionService(client: client);
    addTearDown(service.dispose);

    final preferences = await service.loadPreferences(
      baseUrl: 'https://attention-api.example.com',
      token: token,
    );
    await service.savePreferences(
      baseUrl: 'https://attention-api.example.com',
      token: token,
      preferredDuration: 45,
      remindersEnabled: true,
    );

    expect(preferences.preferredDuration, isNull);
    expect(preferences.remindersEnabled, isNull);
    expect(savedPayload, {
      'preferred_focus_duration': 45,
      'reminders_enabled': true,
    });
    expect(savedPayload.keys, isNot(contains('usage_monitoring_enabled')));
    expect(savedPayload.keys, isNot(contains('tracked_packages')));
  });

  test('rejects non-HTTPS backend URLs before network access', () async {
    final client = MockClient((_) async => http.Response('{}', 200));
    final service = FocusSessionService(client: client);
    addTearDown(service.dispose);

    await expectLater(
      service.loadSessions(baseUrl: 'http://example.com', token: token),
      throwsA(isA<FocusSessionApiException>()),
    );
  });

  test(
    'permits a private Wi-Fi IP over HTTP when LAN testing is enabled',
    () async {
      expect(
        isSupportedFocusApiUrl('http://192.168.1.20:8000', allowLanHttp: true),
        isTrue,
      );
      expect(
        isSupportedFocusApiUrl('http://10.0.0.5:8000', allowLanHttp: true),
        isTrue,
      );
      expect(
        isSupportedFocusApiUrl('http://172.20.0.2:8000', allowLanHttp: true),
        isTrue,
      );
    },
  );

  test('rejects public and credential-bearing HTTP LAN URLs', () {
    expect(
      isSupportedFocusApiUrl('http://203.0.113.7:8000', allowLanHttp: true),
      isFalse,
    );
    expect(
      isSupportedFocusApiUrl(
        'http://user:pass@192.168.1.20:8000',
        allowLanHttp: true,
      ),
      isFalse,
    );
    expect(
      isSupportedFocusApiUrl('http://192.168.1.20:8000', allowLanHttp: false),
      isFalse,
    );
  });

  test('surfaces backend errors to the caller', () async {
    final client = MockClient(
      (_) async => http.Response('{"detail":"token rejected"}', 401),
    );
    final service = FocusSessionService(client: client);
    addTearDown(service.dispose);

    await expectLater(
      service.loadSessions(
        baseUrl: 'https://attention-api.example.com',
        token: token,
      ),
      throwsA(
        isA<FocusSessionApiException>().having(
          (error) => error.message,
          'message',
          'token rejected',
        ),
      ),
    );
  });
}
