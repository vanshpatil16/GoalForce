class AppUsageRecord {
  const AppUsageRecord({
    required this.packageName,
    required this.lastTimeUsed,
    required this.totalTimeInForeground,
  });

  final String packageName;
  final DateTime lastTimeUsed;
  final Duration totalTimeInForeground;

  factory AppUsageRecord.fromPlatformMap(Map<Object?, Object?> value) {
    final packageName = value['packageName'];
    final lastTimeUsedMs = value['lastTimeUsedMs'];
    final foregroundTimeMs = value['totalTimeInForegroundMs'];
    if (packageName is! String ||
        packageName.trim().isEmpty ||
        lastTimeUsedMs is! int ||
        lastTimeUsedMs < 0 ||
        foregroundTimeMs is! int ||
        foregroundTimeMs < 0) {
      throw const FormatException('Invalid Android app-usage record.');
    }
    return AppUsageRecord(
      packageName: packageName,
      lastTimeUsed: DateTime.fromMillisecondsSinceEpoch(
        lastTimeUsedMs,
        isUtc: true,
      ),
      totalTimeInForeground: Duration(milliseconds: foregroundTimeMs),
    );
  }
}

class SessionAppUsageRecord {
  const SessionAppUsageRecord({
    required this.packageName,
    required this.firstObservedAt,
    required this.lastObservedAt,
    required this.estimatedForegroundDuration,
    required this.transitionCount,
    required this.durationIncomplete,
    required this.dataSource,
    this.displayName,
  });

  final String packageName;
  final String? displayName;
  final DateTime firstObservedAt;
  final DateTime lastObservedAt;
  final Duration estimatedForegroundDuration;
  final int transitionCount;
  final bool durationIncomplete;
  final String dataSource;

  factory SessionAppUsageRecord.fromPlatformMap(Map<Object?, Object?> value) {
    final packageName = value['packageName'];
    final displayName = value['displayName'];
    final first = value['firstObservedAtMs'];
    final last = value['lastObservedAtMs'];
    final duration = value['estimatedForegroundMs'];
    final transitions = value['transitionCount'];
    final incomplete = value['durationIncomplete'];
    final dataSource = value['dataSource'];
    if (packageName is! String ||
        packageName.trim().isEmpty ||
        (displayName != null && displayName is! String) ||
        first is! int ||
        last is! int ||
        first < 0 ||
        last < first ||
        duration is! int ||
        duration < 0 ||
        transitions is! int ||
        transitions < 0 ||
        incomplete is! bool ||
        dataSource is! String) {
      throw const FormatException('Invalid session app-usage record.');
    }
    return SessionAppUsageRecord(
      packageName: packageName,
      displayName: displayName as String?,
      firstObservedAt: DateTime.fromMillisecondsSinceEpoch(first, isUtc: true),
      lastObservedAt: DateTime.fromMillisecondsSinceEpoch(last, isUtc: true),
      estimatedForegroundDuration: Duration(milliseconds: duration),
      transitionCount: transitions,
      durationIncomplete: incomplete,
      dataSource: dataSource,
    );
  }
}

class TrackableApp {
  const TrackableApp({required this.packageName, required this.displayName});

  final String packageName;
  final String displayName;

  factory TrackableApp.fromPlatformMap(Map<Object?, Object?> value) {
    final packageName = value['packageName'];
    final displayName = value['displayName'];
    if (packageName is! String ||
        packageName.trim().isEmpty ||
        displayName is! String ||
        displayName.trim().isEmpty) {
      throw const FormatException('Invalid installed-app record.');
    }
    return TrackableApp(packageName: packageName, displayName: displayName);
  }
}
