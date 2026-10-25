import 'dart:math';

enum FocusSessionStatus { completed, endedEarly }

class FocusSession {
  const FocusSession({
    required this.id,
    required this.goal,
    required this.plannedMinutes,
    required this.startedAt,
    required this.remindersEnabled,
    this.actualDuration = Duration.zero,
    this.status,
    this.reportedDistractions,
    this.locallyObservedPackages = const [],
  });

  final String id;
  final String goal;
  final int plannedMinutes;
  final DateTime startedAt;
  final bool remindersEnabled;
  final Duration actualDuration;
  final FocusSessionStatus? status;
  final int? reportedDistractions;
  final List<String> locallyObservedPackages;

  FocusSession copyWith({
    Duration? actualDuration,
    FocusSessionStatus? status,
    int? reportedDistractions,
    bool clearReportedDistractions = false,
    List<String>? locallyObservedPackages,
  }) {
    return FocusSession(
      id: id,
      goal: goal,
      plannedMinutes: plannedMinutes,
      startedAt: startedAt,
      remindersEnabled: remindersEnabled,
      actualDuration: actualDuration ?? this.actualDuration,
      status: status ?? this.status,
      reportedDistractions:
          reportedDistractions ??
          (clearReportedDistractions ? null : this.reportedDistractions),
      locallyObservedPackages:
          locallyObservedPackages ?? this.locallyObservedPackages,
    );
  }
}

String createFocusSessionId() {
  final bytes = List<int>.generate(16, (_) => Random.secure().nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes.map((byte) => byte.toRadixString(16).padLeft(2, '0'));
  final value = hex.join();
  return '${value.substring(0, 8)}-${value.substring(8, 12)}-'
      '${value.substring(12, 16)}-${value.substring(16, 20)}-'
      '${value.substring(20)}';
}

String formatDuration(Duration duration) {
  final minutes = duration.inMinutes;
  final seconds = duration.inSeconds.remainder(60);
  if (minutes == 0) return '${seconds}s';
  return seconds == 0 ? '${minutes}m' : '${minutes}m ${seconds}s';
}
