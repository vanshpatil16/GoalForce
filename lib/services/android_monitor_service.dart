import 'package:flutter/services.dart';

import '../models/app_usage_record.dart';

class AndroidMonitorException implements Exception {
  const AndroidMonitorException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => '$code: $message';
}

class AndroidMonitorService {
  AndroidMonitorService({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel(channelName);

  static const channelName = 'com.attentionos/native';

  final MethodChannel _channel;

  Future<int> getAndroidVersion() async {
    final value = await _invoke<Object?>('getAndroidVersion');
    if (value is! int || value < 1) {
      throw const AndroidMonitorException(
        'INVALID_RESPONSE',
        'Android returned an invalid SDK version.',
      );
    }
    return value;
  }

  Future<bool> hasUsageAccess() async {
    final value = await _invoke<Object?>('hasUsageAccess');
    if (value is! bool) {
      throw const AndroidMonitorException(
        'INVALID_RESPONSE',
        'Android returned an invalid usage-access status.',
      );
    }
    return value;
  }

  Future<bool> openUsageAccessSettings() async {
    final value = await _invoke<Object?>('openUsageAccessSettings');
    if (value is! bool) {
      throw const AndroidMonitorException(
        'INVALID_RESPONSE',
        'Android returned an invalid settings-launch result.',
      );
    }
    return value;
  }

  Future<List<AppUsageRecord>> getRecentAppUsage({
    required int startTimeMs,
    required int endTimeMs,
  }) async {
    if (startTimeMs < 0 || endTimeMs <= startTimeMs) {
      throw ArgumentError('Usage interval must have start < end >= 0.');
    }
    final value = await _invoke<Object?>('getRecentAppUsage', {
      'startTimeMs': startTimeMs,
      'endTimeMs': endTimeMs,
    });
    if (value is! List<Object?>) {
      throw const AndroidMonitorException(
        'INVALID_RESPONSE',
        'Android returned an invalid app-usage list.',
      );
    }
    return value.map((record) {
      if (record is! Map<Object?, Object?>) {
        throw const AndroidMonitorException(
          'INVALID_RESPONSE',
          'Android returned a malformed app-usage record.',
        );
      }
      try {
        return AppUsageRecord.fromPlatformMap(record);
      } on FormatException {
        throw const AndroidMonitorException(
          'INVALID_RESPONSE',
          'Android returned a malformed app-usage record.',
        );
      }
    }).toList();
  }

  Future<List<SessionAppUsageRecord>> getSessionAppUsage({
    required int startTimeMs,
    required int endTimeMs,
  }) async {
    if (startTimeMs < 0 || endTimeMs <= startTimeMs) {
      throw ArgumentError('Usage interval must have start < end >= 0.');
    }
    final value = await _invoke<Object?>('getSessionAppUsage', {
      'startTimeMs': startTimeMs,
      'endTimeMs': endTimeMs,
    });
    if (value is! List<Object?>) {
      throw const AndroidMonitorException(
        'INVALID_RESPONSE',
        'Android returned an invalid session app-usage list.',
      );
    }
    return value.map((record) {
      if (record is! Map<Object?, Object?>) {
        throw const AndroidMonitorException(
          'INVALID_RESPONSE',
          'Android returned a malformed session app-usage record.',
        );
      }
      try {
        return SessionAppUsageRecord.fromPlatformMap(record);
      } on FormatException {
        throw const AndroidMonitorException(
          'INVALID_RESPONSE',
          'Android returned a malformed session app-usage record.',
        );
      }
    }).toList();
  }

  Future<List<TrackableApp>> getTrackableApps() async {
    final value = await _invoke<Object?>('getTrackableApps');
    if (value is! List<Object?>) {
      throw const AndroidMonitorException(
        'INVALID_RESPONSE',
        'Android returned an invalid installed-app list.',
      );
    }
    return value.map((record) {
      if (record is! Map<Object?, Object?>) {
        throw const AndroidMonitorException(
          'INVALID_RESPONSE',
          'Android returned a malformed installed-app record.',
        );
      }
      try {
        return TrackableApp.fromPlatformMap(record);
      } on FormatException {
        throw const AndroidMonitorException(
          'INVALID_RESPONSE',
          'Android returned a malformed installed-app record.',
        );
      }
    }).toList();
  }

  Future<T> _invoke<T>(String method, [Object? arguments]) async {
    try {
      final value = await _channel.invokeMethod<T>(method, arguments);
      if (value == null) {
        throw const AndroidMonitorException(
          'INVALID_RESPONSE',
          'Android returned no result.',
        );
      }
      return value;
    } on PlatformException catch (error) {
      throw AndroidMonitorException(
        error.code,
        error.message ?? 'Android could not complete the requested operation.',
      );
    } on MissingPluginException {
      throw const AndroidMonitorException(
        'ANDROID_UNSUPPORTED',
        'Native Android monitoring is unavailable on this platform.',
      );
    }
  }
}
