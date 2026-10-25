import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../core/theme/app_theme.dart';
import '../../models/app_usage_record.dart';
import '../../models/focus_session.dart';
import '../../services/android_monitor_service.dart';
import '../../widgets/focus_timer.dart';
import 'session_summary_screen.dart';

class ActiveSessionScreen extends StatefulWidget {
  const ActiveSessionScreen({
    required this.session,
    this.monitoringEnabled = false,
    this.distractionPackages = '',
    this.trackedPackages = '',
    this.monitorService,
    super.key,
  });

  final FocusSession session;
  final bool monitoringEnabled;
  final String distractionPackages;
  final String trackedPackages;
  final AndroidMonitorService? monitorService;

  @override
  State<ActiveSessionScreen> createState() => _ActiveSessionScreenState();
}

class _ActiveSessionScreenState extends State<ActiveSessionScreen>
    with WidgetsBindingObserver {
  late final int _totalSeconds;
  late int _remainingSeconds;
  late final AndroidMonitorService _monitorService;
  Timer? _ticker;
  Timer? _usageTicker;
  DateTime? _deadline;
  DateTime? _runStartedAt;
  int _focusedSecondsBeforeCurrentRun = 0;
  bool _isPaused = false;
  bool _finishing = false;
  late bool _usageMonitoringActive;
  bool _usageQueryInProgress = false;
  bool _permissionWarningShown = false;
  final Set<String> _warnedPackages = {};
  final Set<String> _pendingPackages = {};
  final Map<String, SessionAppUsageRecord> _committedUsage = {};
  final Map<String, SessionAppUsageRecord> _currentSegmentUsage = {};
  DateTime? _lastUsageUpdate;
  String? _monitoringError;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _monitorService = widget.monitorService ?? AndroidMonitorService();
    _usageMonitoringActive = widget.monitoringEnabled;
    _totalSeconds = widget.session.plannedMinutes * 60;
    _remainingSeconds = _totalSeconds;
    _resumeTimer();
    _startUsageChecks();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !_isPaused && !_finishing) {
      _tick();
      if (_usageTicker == null) {
        _startUsageChecks();
      } else {
        _checkUsage();
      }
      _showPendingUsageWarning();
    }
  }

  Future<void> _startUsageChecks() async {
    if (!_usageMonitoringActive || _trackedPackageSet.isEmpty) return;
    try {
      if (!await _monitorService.hasUsageAccess()) {
        if (mounted) {
          setState(() => _usageMonitoringActive = false);
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'App-usage checks are off because Android Usage Access is not granted.',
              ),
            ),
          );
        }
        return;
      }
      if (!mounted || _finishing) return;
      _usageTicker = Timer.periodic(
        const Duration(minutes: 1),
        (_) => _checkUsage(),
      );
      await _checkUsage();
    } on AndroidMonitorException catch (error) {
      _showMonitoringError(error);
    }
  }

  Set<String> get _configuredPackages => widget.distractionPackages
      .split(',')
      .map((packageName) => packageName.trim())
      .where((packageName) => packageName.isNotEmpty)
      .toSet();

  Set<String> get _trackedPackageSet => widget.trackedPackages
      .split(',')
      .map((packageName) => packageName.trim())
      .where((packageName) => packageName.isNotEmpty)
      .toSet();

  Future<void> _checkUsage({bool finalQuery = false}) async {
    if (!_usageMonitoringActive ||
        _trackedPackageSet.isEmpty ||
        _isPaused ||
        _usageQueryInProgress ||
        (_finishing && !finalQuery) ||
        WidgetsBinding.instance.lifecycleState != AppLifecycleState.resumed) {
      return;
    }
    _usageQueryInProgress = true;
    try {
      final startTimeMs =
          (_runStartedAt ?? widget.session.startedAt).millisecondsSinceEpoch;
      final endTimeMs = DateTime.now().millisecondsSinceEpoch;
      if (endTimeMs <= startTimeMs) return;
      final records = await _monitorService.getSessionAppUsage(
        startTimeMs: startTimeMs,
        endTimeMs: endTimeMs,
      );
      if (!mounted) return;
      _currentSegmentUsage
        ..clear()
        ..addEntries(
          records
              .where(
                (record) => _trackedPackageSet.contains(record.packageName),
              )
              .map((record) => MapEntry(record.packageName, record)),
        );
      _lastUsageUpdate = DateTime.now();
      _monitoringError = null;
      for (final record in records.where(
        (record) => _trackedPackageSet.contains(record.packageName),
      )) {
        if (_configuredPackages.contains(record.packageName) &&
            !record.lastObservedAt.isBefore(widget.session.startedAt) &&
            !_warnedPackages.contains(record.packageName)) {
          _pendingPackages.add(record.packageName);
          _warnedPackages.add(record.packageName);
        }
      }
      setState(() {});
      if (!finalQuery) _showPendingUsageWarning();
    } on AndroidMonitorException catch (error) {
      if (mounted) {
        setState(() {
          _monitoringError = error.message;
          if (error.code == 'USAGE_ACCESS_DENIED') {
            _usageMonitoringActive = false;
          }
        });
      }
      _showMonitoringError(error);
      if (error.code == 'USAGE_ACCESS_DENIED') {
        _usageTicker?.cancel();
      }
    } finally {
      _usageQueryInProgress = false;
    }
  }

  Future<void> _toggleUsageMonitoring(bool enabled) async {
    if (!enabled) {
      setState(() => _usageMonitoringActive = false);
      _usageTicker?.cancel();
      _pendingPackages.clear();
      return;
    }
    setState(() => _usageMonitoringActive = true);
    await _startUsageChecks();
  }

  void _showPendingUsageWarning() {
    if (!mounted ||
        _pendingPackages.isEmpty ||
        WidgetsBinding.instance.lifecycleState != AppLifecycleState.resumed) {
      return;
    }
    final packageName = _pendingPackages.first;
    _pendingPackages.remove(packageName);
    _warnedPackages.add(packageName);
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Activity on a selected app'),
        content: Text(
          'Android UsageStats reported recent activity for $packageName. '
          'These statistics may be delayed or aggregated and do not prove '
          'that the activity was distracting.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Dismiss'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              _pause();
            },
            child: const Text('Pause focus'),
          ),
        ],
      ),
    );
  }

  void _showMonitoringError(AndroidMonitorException error) {
    if (!mounted || _permissionWarningShown) return;
    _permissionWarningShown = true;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('App-usage check unavailable: ${error.message}')),
    );
  }

  void _resumeTimer() {
    _isPaused = false;
    _runStartedAt = DateTime.now();
    _deadline = _runStartedAt!.add(Duration(seconds: _remainingSeconds));
    _ticker?.cancel();
    _usageTicker?.cancel();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
  }

  void _tick() {
    if (_finishing || _deadline == null) return;
    final left = math.max(
      0,
      (_deadline!.difference(DateTime.now()).inMilliseconds / 1000).ceil(),
    );
    if (left == 0) {
      _finish(FocusSessionStatus.completed);
      return;
    }
    if (left != _remainingSeconds && mounted) {
      setState(() => _remainingSeconds = left);
    }
  }

  void _pause() {
    if (_isPaused || _finishing) return;
    final now = DateTime.now();
    _remainingSeconds = math.max(
      0,
      (_deadline!.difference(now).inMilliseconds / 1000).ceil(),
    );
    _focusedSecondsBeforeCurrentRun += now.difference(_runStartedAt!).inSeconds;
    _commitCurrentSegmentUsage();
    _ticker?.cancel();
    _usageTicker?.cancel();
    setState(() {
      _isPaused = true;
      _deadline = null;
      _runStartedAt = null;
    });
  }

  void _commitCurrentSegmentUsage() {
    for (final entry in _currentSegmentUsage.entries) {
      final current = entry.value;
      final previous = _committedUsage[entry.key];
      if (previous == null) {
        _committedUsage[entry.key] = current;
      } else {
        _committedUsage[entry.key] = SessionAppUsageRecord(
          packageName: current.packageName,
          displayName: current.displayName ?? previous.displayName,
          firstObservedAt:
              previous.firstObservedAt.isBefore(current.firstObservedAt)
              ? previous.firstObservedAt
              : current.firstObservedAt,
          lastObservedAt:
              previous.lastObservedAt.isAfter(current.lastObservedAt)
              ? previous.lastObservedAt
              : current.lastObservedAt,
          estimatedForegroundDuration:
              previous.estimatedForegroundDuration +
              current.estimatedForegroundDuration,
          transitionCount: previous.transitionCount + current.transitionCount,
          durationIncomplete:
              previous.durationIncomplete || current.durationIncomplete,
          dataSource: current.dataSource,
        );
      }
    }
    _currentSegmentUsage.clear();
  }

  List<SessionAppUsageRecord> get _visibleUsageRecords {
    final combined = Map<String, SessionAppUsageRecord>.from(_committedUsage);
    for (final entry in _currentSegmentUsage.entries) {
      final current = entry.value;
      final previous = combined[entry.key];
      if (previous == null) {
        combined[entry.key] = current;
      } else {
        combined[entry.key] = SessionAppUsageRecord(
          packageName: current.packageName,
          displayName: current.displayName ?? previous.displayName,
          firstObservedAt:
              previous.firstObservedAt.isBefore(current.firstObservedAt)
              ? previous.firstObservedAt
              : current.firstObservedAt,
          lastObservedAt:
              previous.lastObservedAt.isAfter(current.lastObservedAt)
              ? previous.lastObservedAt
              : current.lastObservedAt,
          estimatedForegroundDuration:
              previous.estimatedForegroundDuration +
              current.estimatedForegroundDuration,
          transitionCount: previous.transitionCount + current.transitionCount,
          durationIncomplete:
              previous.durationIncomplete || current.durationIncomplete,
          dataSource: current.dataSource,
        );
      }
    }
    return combined.values.toList()..sort(
      (a, b) => b.estimatedForegroundDuration.compareTo(
        a.estimatedForegroundDuration,
      ),
    );
  }

  Future<void> _togglePause() async {
    if (_isPaused) {
      setState(_resumeTimer);
      await _startUsageChecks();
    } else {
      _pause();
    }
  }

  Future<void> _requestEnd() async {
    if (_finishing) return;
    if (_remainingSeconds == 0) {
      await _finish(FocusSessionStatus.completed);
      return;
    }
    final shouldEnd = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('End this session?'),
        content: const Text(
          'Your focus time so far will be saved as an early-ended session.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Keep focusing'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('End session'),
          ),
        ],
      ),
    );
    if (shouldEnd == true && mounted) {
      await _finish(FocusSessionStatus.endedEarly);
    }
  }

  Future<void> _finish(FocusSessionStatus status) async {
    if (_finishing) return;
    _finishing = true;
    _ticker?.cancel();
    if (!_isPaused && _usageMonitoringActive) {
      await _checkUsage(finalQuery: true);
    }
    var actualSeconds = _focusedSecondsBeforeCurrentRun;
    if (!_isPaused && _runStartedAt != null) {
      actualSeconds += DateTime.now().difference(_runStartedAt!).inSeconds;
    }
    actualSeconds = math.min(_totalSeconds, actualSeconds);
    if (status == FocusSessionStatus.completed) {
      actualSeconds = _totalSeconds;
    }
    final result = widget.session.copyWith(
      actualDuration: Duration(seconds: actualSeconds),
      status: status,
      locallyObservedPackages: _warnedPackages.toList(),
    );
    if (!mounted) return;

    final summarized = await Navigator.of(context).push<FocusSession>(
      MaterialPageRoute(builder: (_) => SessionSummaryScreen(session: result)),
    );
    if (mounted) Navigator.of(context).pop(summarized ?? result);
  }

  String get _timeLabel {
    final minutes = _remainingSeconds ~/ 60;
    final seconds = _remainingSeconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:'
        '${seconds.toString().padLeft(2, '0')}';
  }

  static String _formatObservedTime(DateTime value) {
    final local = value.toLocal();
    return '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }

  static String _formatUsageDuration(Duration duration) =>
      duration.inSeconds < 60
      ? '${duration.inSeconds}s'
      : '${duration.inMinutes}m ${duration.inSeconds.remainder(60)}s';

  @override
  void dispose() {
    _ticker?.cancel();
    _usageTicker?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final progress = 1 - (_remainingSeconds / _totalSeconds);
    return PopScope(
      canPop: false,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Focus session'),
          leading: IconButton(
            tooltip: 'End session',
            onPressed: _requestEnd,
            icon: const Icon(Icons.close_rounded),
          ),
        ),
        body: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(24, 12, 24, 28),
              child: Column(
                children: [
                  const Text(
                    'YOUR INTENTION',
                    style: TextStyle(
                      color: AppColors.muted,
                      letterSpacing: 1.6,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    widget.session.goal,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleLarge
                        ?.copyWith(fontWeight: FontWeight.w700),
                  ),
                  if (widget.monitoringEnabled || _usageMonitoringActive) ...[
                    const SizedBox(height: 14),
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(14),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            SwitchListTile(
                              contentPadding: EdgeInsets.zero,
                              value: _usageMonitoringActive,
                              onChanged: _toggleUsageMonitoring,
                              title: const Text('App-usage monitoring'),
                              subtitle: Text(
                                _monitoringError ??
                                    (_usageMonitoringActive
                                        ? _trackedPackageSet.isEmpty
                                              ? 'Choose apps to track in Settings before monitoring starts.'
                                              : 'Usage Access checked on Android; updates may be delayed.'
                                        : 'Monitoring is off.'),
                                style: const TextStyle(color: AppColors.muted),
                              ),
                              secondary: const Icon(Icons.query_stats_rounded),
                            ),
                            if (_usageMonitoringActive) ...[
                              const Divider(),
                              Text(
                                _trackedPackageSet.isEmpty
                                    ? 'No apps selected. Choose apps in Settings → Android App Usage Monitoring.'
                                    : _lastUsageUpdate == null
                                    ? 'Waiting for the first usage-event query'
                                    : 'Last successful update: ${_lastUsageUpdate!.toLocal()}',
                                style: Theme.of(context).textTheme.bodySmall
                                    ?.copyWith(color: AppColors.muted),
                              ),
                              const SizedBox(height: 8),
                              if (_visibleUsageRecords.isEmpty)
                                const Text(
                                  'No app transitions observed in this active session segment yet. Android may delay usage events.',
                                  style: TextStyle(color: AppColors.muted),
                                )
                              else ...[
                                const Text(
                                  'Observed applications',
                                  style: TextStyle(fontWeight: FontWeight.w700),
                                ),
                                ..._visibleUsageRecords.map(
                                  (record) => ExpansionTile(
                                    tilePadding: EdgeInsets.zero,
                                    title: Text(
                                      record.displayName ?? record.packageName,
                                    ),
                                    subtitle: Text(
                                      '${_formatObservedTime(record.firstObservedAt)} – ${_formatObservedTime(record.lastObservedAt)} · ${_formatUsageDuration(record.estimatedForegroundDuration)} estimated${record.durationIncomplete ? ' · incomplete' : ''}',
                                      style: const TextStyle(
                                        color: AppColors.muted,
                                      ),
                                    ),
                                    children: [
                                      ListTile(
                                        dense: true,
                                        title: Text(record.packageName),
                                        subtitle: Text(
                                          '${record.transitionCount} observed transitions · ${record.dataSource}\n'
                                          '${_configuredPackages.contains(record.packageName) ? 'You selected this app for a focus warning.' : 'No focus warning rule is configured for this app.'} Usage alone does not establish distraction.',
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ],
                          ],
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: 38),
                  FocusTimer(
                    progress: progress,
                    timeLabel: _timeLabel,
                    isPaused: _isPaused,
                  ),
                  const SizedBox(height: 24),
                  Text(
                    '${(progress * 100).round()}% complete · '
                    '${widget.session.plannedMinutes} min planned',
                    style: const TextStyle(color: AppColors.muted),
                  ),
                  const SizedBox(height: 32),
                  SizedBox(
                    width: 190,
                    height: 54,
                    child: OutlinedButton.icon(
                      onPressed: _finishing ? null : _togglePause,
                      icon: Icon(
                        _isPaused
                            ? Icons.play_arrow_rounded
                            : Icons.pause_rounded,
                      ),
                      label: Text(_isPaused ? 'Resume' : 'Pause'),
                      style: OutlinedButton.styleFrom(
                        side: const BorderSide(color: AppColors.border),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextButton(
                    onPressed: _finishing ? null : _requestEnd,
                    child: const Text(
                      'End session',
                      style: TextStyle(color: AppColors.muted),
                    ),
                  ),
                  const SizedBox(height: 20),
                  const Text(
                    'Your focus timer is private and does not inspect other apps.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.muted, fontSize: 12),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
