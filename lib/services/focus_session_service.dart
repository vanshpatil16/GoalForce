import 'dart:convert';
import 'dart:math';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:flutter/services.dart';

import '../models/focus_session.dart';

const bool attentionOsLanHttpEnabled =
    !bool.fromEnvironment('dart.vm.product') &&
    bool.fromEnvironment('ATTENTION_OS_ALLOW_LAN_HTTP');

class FocusSessionApiException implements Exception {
  const FocusSessionApiException(this.message);

  final String message;

  @override
  String toString() => message;
}

class FocusPreferences {
  const FocusPreferences({
    required this.preferredDuration,
    required this.remindersEnabled,
  });

  final int? preferredDuration;
  final bool? remindersEnabled;
}

class FocusSessionService {
  FocusSessionService({
    http.Client? client,
    FlutterSecureStorage? secureStorage,
    bool? allowLanHttp,
  }) : _client = client ?? http.Client(),
       _allowLanHttp = allowLanHttp ?? attentionOsLanHttpEnabled,
       _secureStorage = secureStorage ?? const FlutterSecureStorage();

  static const _deviceTokenKey = 'attention_os_device_token';

  final http.Client _client;
  final FlutterSecureStorage _secureStorage;
  final bool _allowLanHttp;

  Future<String> deviceToken() async {
    String? existing;
    try {
      existing = await _secureStorage.read(key: _deviceTokenKey);
    } on PlatformException catch (error) {
      throw FocusSessionApiException(
        'Could not read the secure device token: ${error.message ?? error.code}',
      );
    } on MissingPluginException {
      throw const FocusSessionApiException(
        'Secure device storage is unavailable on this platform.',
      );
    }
    if (existing != null && RegExp(r'^[0-9a-f]{64}$').hasMatch(existing)) {
      return existing;
    }

    final random = Random.secure();
    final token = List<int>.generate(
      32,
      (_) => random.nextInt(256),
    ).map((byte) => byte.toRadixString(16).padLeft(2, '0')).join();
    try {
      await _secureStorage.write(key: _deviceTokenKey, value: token);
    } on PlatformException catch (error) {
      throw FocusSessionApiException(
        'Could not store the secure device token: ${error.message ?? error.code}',
      );
    } on MissingPluginException {
      throw const FocusSessionApiException(
        'Secure device storage is unavailable on this platform.',
      );
    }
    return token;
  }

  Future<List<FocusSession>> loadSessions({
    required String baseUrl,
    required String token,
  }) async {
    final response = await _request(
      () => _client.get(
        _endpoint(baseUrl, '/v1/sessions'),
        headers: _headers(token),
      ),
    );
    _requireSuccess(response);

    final decoded = _decodeResponse(response);
    if (decoded is! List<dynamic>) {
      throw const FocusSessionApiException(
        'The session service returned an invalid session list.',
      );
    }
    return decoded.map(_sessionFromJson).toList();
  }

  Future<void> saveSession({
    required String baseUrl,
    required String token,
    required FocusSession session,
  }) async {
    final payload = {
      'id': session.id,
      'goal': session.goal,
      'planned_minutes': session.plannedMinutes,
      'started_at': session.startedAt.toIso8601String(),
      'actual_seconds': session.actualDuration.inSeconds,
      'status': switch (session.status) {
        FocusSessionStatus.completed => 'completed',
        FocusSessionStatus.endedEarly => 'ended_early',
        null => throw const FocusSessionApiException(
          'A session must have a completion status before it can sync.',
        ),
      },
      'reported_distractions': session.reportedDistractions,
    };
    final response = await _request(
      () => _client.post(
        _endpoint(baseUrl, '/v1/sessions'),
        headers: _headers(token),
        body: jsonEncode(payload),
      ),
    );
    _requireSuccess(response);
  }

  Future<FocusPreferences> loadPreferences({
    required String baseUrl,
    required String token,
  }) async {
    final response = await _request(
      () => _client.get(
        _endpoint(baseUrl, '/v1/preferences'),
        headers: _headers(token),
      ),
    );
    _requireSuccess(response);
    final decoded = _decodeResponse(response);
    if (decoded is! Map<String, dynamic>) {
      throw const FocusSessionApiException(
        'The session service returned invalid focus preferences.',
      );
    }
    final duration = decoded['preferred_focus_duration'];
    final reminders = decoded['reminders_enabled'];
    if ((duration != null &&
            (duration is! int ||
                !const [15, 25, 30, 45, 60].contains(duration))) ||
        (reminders != null && reminders is! bool)) {
      throw const FocusSessionApiException(
        'The session service returned invalid focus preferences.',
      );
    }
    return FocusPreferences(
      preferredDuration: duration as int?,
      remindersEnabled: reminders as bool?,
    );
  }

  Future<void> savePreferences({
    required String baseUrl,
    required String token,
    required int preferredDuration,
    required bool remindersEnabled,
  }) async {
    if (![15, 25, 30, 45, 60].contains(preferredDuration)) {
      throw const FocusSessionApiException(
        'Choose a supported focus duration before syncing preferences.',
      );
    }
    final response = await _request(
      () => _client.put(
        _endpoint(baseUrl, '/v1/preferences'),
        headers: _headers(token),
        body: jsonEncode({
          'preferred_focus_duration': preferredDuration,
          'reminders_enabled': remindersEnabled,
        }),
      ),
    );
    _requireSuccess(response);
  }

  Future<http.Response> _request(
    Future<http.Response> Function() request,
  ) async {
    try {
      return await request();
    } on http.ClientException catch (error) {
      throw FocusSessionApiException('Session sync failed: ${error.message}');
    }
  }

  Uri _endpoint(String baseUrl, String path) {
    final uri = Uri.tryParse(baseUrl.trim());
    if (uri == null ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        !isSupportedFocusApiUrl(baseUrl, allowLanHttp: _allowLanHttp)) {
      throw const FocusSessionApiException(
        'Use HTTPS, or a private IPv4 LAN address over HTTP in the debug build configured for LAN testing.',
      );
    }
    final basePath = uri.path.replaceAll(RegExp(r'/+$'), '');
    return uri.replace(path: '$basePath$path');
  }

  Map<String, String> _headers(String token) => {
    'Authorization': 'Bearer $token',
    'Accept': 'application/json',
    'Content-Type': 'application/json',
  };

  void _requireSuccess(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) return;
    var message = 'Session sync failed (HTTP ${response.statusCode}).';
    try {
      final body = jsonDecode(response.body);
      if (body is Map<String, dynamic> && body['detail'] is String) {
        message = body['detail'] as String;
      }
    } on FormatException {
      // Keep the HTTP status as the actionable error when the body is not JSON.
    }
    throw FocusSessionApiException(message);
  }

  dynamic _decodeResponse(http.Response response) {
    try {
      return jsonDecode(response.body);
    } on FormatException {
      throw const FocusSessionApiException(
        'The session service returned invalid JSON.',
      );
    }
  }

  FocusSession _sessionFromJson(dynamic value) {
    if (value is! Map<String, dynamic>) {
      throw const FocusSessionApiException(
        'The session service returned an invalid session.',
      );
    }
    try {
      final status = switch (value['status']) {
        'completed' => FocusSessionStatus.completed,
        'ended_early' => FocusSessionStatus.endedEarly,
        _ => throw const FormatException('Unknown session status'),
      };
      final distractions = value['reported_distractions'];
      return FocusSession(
        id: value['id'] as String,
        goal: value['goal'] as String,
        plannedMinutes: value['planned_minutes'] as int,
        startedAt: DateTime.parse(value['started_at'] as String),
        remindersEnabled: false,
        actualDuration: Duration(seconds: value['actual_seconds'] as int),
        status: status,
        reportedDistractions: distractions as int?,
      );
    } on FormatException catch (error) {
      throw FocusSessionApiException(
        'The session service returned an invalid session: $error',
      );
    } on TypeError catch (error) {
      throw FocusSessionApiException(
        'The session service returned an invalid session: $error',
      );
    }
  }

  void dispose() {
    _client.close();
  }
}

bool isSupportedFocusApiUrl(String value, {required bool allowLanHttp}) {
  final uri = Uri.tryParse(value.trim());
  if (uri == null ||
      uri.host.isEmpty ||
      uri.userInfo.isNotEmpty ||
      uri.hasQuery ||
      uri.hasFragment) {
    return false;
  }
  if (uri.scheme == 'https') return true;
  if (!allowLanHttp || uri.scheme != 'http') return false;

  final octets = uri.host.split('.').map(int.tryParse).toList();
  if (octets.length != 4 || octets.any((octet) => octet == null)) {
    return false;
  }
  final address = octets.cast<int>();
  if (address.any((octet) => octet < 0 || octet > 255)) return false;
  return address[0] == 10 ||
      (address[0] == 172 && address[1] >= 16 && address[1] <= 31) ||
      (address[0] == 192 && address[1] == 168);
}
