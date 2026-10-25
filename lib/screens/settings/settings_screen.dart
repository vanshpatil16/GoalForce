import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/theme/app_theme.dart';
import '../../models/app_usage_record.dart';
import '../../services/android_monitor_service.dart';
import '../../services/focus_session_service.dart';
import '../../widgets/section_heading.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    required this.preferredDuration,
    required this.remindersEnabled,
    required this.onPreferencesChanged,
    required this.apiBaseUrl,
    required this.syncStatus,
    required this.onBackendUrlSaved,
    required this.monitoringEnabled,
    required this.distractionPackages,
    required this.onMonitoringPreferencesChanged,
    this.trackedPackages = '',
    this.onTrackedPackagesChanged,
    this.monitorService,
    super.key,
  });

  final int preferredDuration;
  final bool remindersEnabled;
  final Future<void> Function(int duration, bool remindersEnabled)
  onPreferencesChanged;
  final String apiBaseUrl;
  final String syncStatus;
  final Future<void> Function(String value) onBackendUrlSaved;
  final bool monitoringEnabled;
  final String distractionPackages;
  final Future<void> Function({
    required bool enabled,
    required String distractionPackages,
  })
  onMonitoringPreferencesChanged;
  final String trackedPackages;
  final Future<void> Function(String value)? onTrackedPackagesChanged;
  final AndroidMonitorService? monitorService;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen>
    with WidgetsBindingObserver {
  static const _durations = [15, 25, 30, 45, 60];
  late final TextEditingController _apiUrlController;
  late final TextEditingController _packagesController;
  late final AndroidMonitorService _monitorService;
  late int _preferredDuration;
  late bool _remindersEnabled;
  late bool _monitoringEnabled;
  bool _checkingPermission = false;
  bool _savingBackendUrl = false;
  bool _queryingUsage = false;
  bool? _usageAccessGranted;
  String? _monitorError;
  String? _nativeConnectionStatus;
  List<AppUsageRecord> _usageRecords = [];
  Set<String> _trackedPackages = {};
  List<TrackableApp> _launchableApps = [];
  bool _loadingTrackableApps = false;
  bool _pickAppsAfterPermissionReturn = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _apiUrlController = TextEditingController(text: widget.apiBaseUrl);
    _packagesController = TextEditingController(
      text: widget.distractionPackages,
    );
    _monitorService = widget.monitorService ?? AndroidMonitorService();
    _preferredDuration = widget.preferredDuration;
    _remindersEnabled = widget.remindersEnabled;
    _monitoringEnabled = widget.monitoringEnabled;
    _trackedPackages = widget.trackedPackages
        .split(',')
        .map((value) => value.trim())
        .where((value) => value.isNotEmpty)
        .toSet();
    _refreshUsageAccess();
  }

  @override
  void didUpdateWidget(covariant SettingsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.preferredDuration != widget.preferredDuration) {
      _preferredDuration = widget.preferredDuration;
    }
    if (oldWidget.remindersEnabled != widget.remindersEnabled) {
      _remindersEnabled = widget.remindersEnabled;
    }
    if (oldWidget.monitoringEnabled != widget.monitoringEnabled) {
      _monitoringEnabled = widget.monitoringEnabled;
    }
    if (oldWidget.apiBaseUrl != widget.apiBaseUrl) {
      _apiUrlController.text = widget.apiBaseUrl;
    }
    if (oldWidget.distractionPackages != widget.distractionPackages) {
      _packagesController.text = widget.distractionPackages;
    }
    if (oldWidget.trackedPackages != widget.trackedPackages) {
      _trackedPackages = widget.trackedPackages
          .split(',')
          .map((value) => value.trim())
          .where((value) => value.isNotEmpty)
          .toSet();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _handleResume();
  }

  Future<void> _handleResume() async {
    await _refreshUsageAccess();
    if (!mounted || !_pickAppsAfterPermissionReturn) return;
    _pickAppsAfterPermissionReturn = false;
    if (_usageAccessGranted == true && _trackedPackages.isEmpty) {
      await _openTrackedAppPicker();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _apiUrlController.dispose();
    _packagesController.dispose();
    super.dispose();
  }

  Future<void> _savePreferences() async {
    try {
      await widget.onPreferencesChanged(_preferredDuration, _remindersEnabled);
    } on PlatformException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not save preference: $error')),
        );
      }
    }
  }

  Future<void> _saveMonitoringPreferences({required bool enabled}) async {
    setState(() => _monitoringEnabled = enabled);
    try {
      await widget.onMonitoringPreferencesChanged(
        enabled: enabled,
        distractionPackages: _packagesController.text.trim(),
      );
    } on PlatformException catch (error) {
      if (mounted) {
        setState(
          () => _monitorError =
              'Could not save monitoring preference: ${error.message ?? error.code}',
        );
      }
    }
  }

  Future<void> _refreshUsageAccess() async {
    if (!mounted) return;
    setState(() {
      _checkingPermission = true;
      _monitorError = null;
    });
    try {
      final hasAccess = await _monitorService.hasUsageAccess();
      if (!mounted) return;
      setState(() => _usageAccessGranted = hasAccess);
    } on AndroidMonitorException catch (error) {
      if (!mounted) return;
      setState(() {
        _usageAccessGranted = null;
        _monitorError = error.message;
      });
    } finally {
      if (mounted) setState(() => _checkingPermission = false);
    }
  }

  Future<void> _testNativeConnection() async {
    setState(() => _nativeConnectionStatus = 'Checking native connection…');
    try {
      final version = await _monitorService.getAndroidVersion();
      if (mounted) {
        setState(
          () => _nativeConnectionStatus = 'Connected to Android API $version.',
        );
      }
    } on AndroidMonitorException catch (error) {
      if (mounted) {
        setState(() => _nativeConnectionStatus = error.message);
      }
    }
  }

  Future<void> _toggleMonitoring(bool enabled) async {
    if (!enabled) {
      await _saveMonitoringPreferences(enabled: false);
      return;
    }

    await _saveMonitoringPreferences(enabled: true);
    await _refreshUsageAccess();
    if (!mounted) return;
    if (_usageAccessGranted == true) {
      if (_trackedPackages.isEmpty) await _openTrackedAppPicker();
      return;
    }

    final shouldOpenSettings = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Allow app-usage access?'),
        content: const Text(
          'Android usage access lets Attention OS read app package names and '
          'aggregate foreground time for a time range you choose. It does not '
          'read messages or screen contents, and this app does not upload '
          'usage records. You can revoke access in Android Settings at any time.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Not now'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Open Android Settings'),
          ),
        ],
      ),
    );
    if (shouldOpenSettings == true) {
      await _openUsageAccessSettings();
    } else {
      await _saveMonitoringPreferences(enabled: false);
    }
  }

  Future<void> _openUsageAccessSettings() async {
    try {
      final opened = await _monitorService.openUsageAccessSettings();
      if (opened) _pickAppsAfterPermissionReturn = true;
      if (!opened && mounted) {
        setState(
          () => _monitorError =
              'Android could not open Usage Access settings on this device.',
        );
      }
    } on AndroidMonitorException catch (error) {
      if (mounted) setState(() => _monitorError = error.message);
    }
  }

  Future<void> _explainAndOpenUsageSettings() async {
    final shouldOpenSettings = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Why Usage Access?'),
        content: const Text(
          'Android requires you to grant Usage Access manually. Attention OS '
          'uses it only to query app package names and aggregate foreground '
          'time for a requested interval. It does not read screen contents, '
          'and usage records are not uploaded.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Continue'),
          ),
        ],
      ),
    );
    if (shouldOpenSettings == true) await _openUsageAccessSettings();
  }

  Future<void> _queryRecentUsage() async {
    if (_usageAccessGranted != true) return;
    setState(() {
      _queryingUsage = true;
      _monitorError = null;
    });
    final end = DateTime.now().millisecondsSinceEpoch;
    final start = end - const Duration(hours: 24).inMilliseconds;
    try {
      final records = await _monitorService.getRecentAppUsage(
        startTimeMs: start,
        endTimeMs: end,
      );
      if (mounted) setState(() => _usageRecords = records);
    } on AndroidMonitorException catch (error) {
      if (mounted) setState(() => _monitorError = error.message);
    } finally {
      if (mounted) setState(() => _queryingUsage = false);
    }
  }

  void _toggleFlaggedPackage(String packageName) {
    final packages = _packagesController.text
        .split(',')
        .map((value) => value.trim())
        .where((value) => value.isNotEmpty)
        .toSet();
    if (!packages.add(packageName)) packages.remove(packageName);
    setState(() => _packagesController.text = packages.join(', '));
  }

  Future<void> _openTrackedAppPicker() async {
    setState(() => _loadingTrackableApps = true);
    try {
      _launchableApps = await _monitorService.getTrackableApps();
    } on AndroidMonitorException catch (error) {
      if (mounted) setState(() => _monitorError = error.message);
      return;
    } finally {
      if (mounted) setState(() => _loadingTrackableApps = false);
    }
    if (!mounted) return;
    final selected = Set<String>.from(_trackedPackages);
    final result = await showDialog<Set<String>>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Choose apps to track'),
          content: SizedBox(
            width: 420,
            height: 420,
            child: _launchableApps.isEmpty
                ? const Center(
                    child: Text('No launcher apps were returned by Android.'),
                  )
                : ListView.builder(
                    itemCount: _launchableApps.length,
                    itemBuilder: (context, index) {
                      final app = _launchableApps[index];
                      final checked = selected.contains(app.packageName);
                      return CheckboxListTile(
                        value: checked,
                        title: Text(app.displayName),
                        subtitle: Text(app.packageName),
                        onChanged: (value) => setDialogState(() {
                          if (value == true) {
                            selected.add(app.packageName);
                          } else {
                            selected.remove(app.packageName);
                          }
                        }),
                      );
                    },
                  ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext, selected),
              child: const Text('Save selection'),
            ),
          ],
        ),
      ),
    );
    if (result == null || !mounted) return;
    setState(() => _trackedPackages = result);
    final save = widget.onTrackedPackagesChanged;
    if (save != null) await save(result.join(', '));
  }

  Future<void> _saveBackendUrl() async {
    setState(() => _savingBackendUrl = true);
    try {
      await widget.onBackendUrlSaved(_apiUrlController.text);
      if (mounted) {
        FocusScope.of(context).unfocus();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Backend URL setting saved.')),
        );
      }
    } on FocusSessionApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error.message)));
      }
    } on PlatformException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Could not save backend URL: ${error.message ?? error.code}',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _savingBackendUrl = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 22, 20, 30),
        children: [
          Text(
            'Settings',
            style: Theme.of(context).textTheme.headlineSmall
                ?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          const Text(
            'Make Attention OS work the way you want.',
            style: TextStyle(color: AppColors.muted),
          ),
          const SizedBox(height: 26),
          const SectionHeading('Focus preferences'),
          const SizedBox(height: 10),
          Card(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: DropdownButtonFormField<int>(
                initialValue: _preferredDuration,
                decoration: const InputDecoration(
                  labelText: 'Preferred focus duration',
                  border: InputBorder.none,
                  filled: false,
                ),
                items: _durations
                    .map(
                      (duration) => DropdownMenuItem(
                        value: duration,
                        child: Text('$duration minutes'),
                      ),
                    )
                    .toList(),
                onChanged: (value) {
                  if (value == null) return;
                  setState(() => _preferredDuration = value);
                  _savePreferences();
                },
              ),
            ),
          ),
          Card(
            child: SwitchListTile(
              value: _remindersEnabled,
              onChanged: (value) {
                setState(() => _remindersEnabled = value);
                _savePreferences();
              },
              secondary: const Icon(Icons.notifications_none_rounded),
              title: const Text('Session reminder preference'),
              subtitle: const Text(
                'Saved locally as a preference. No notifications are scheduled yet.',
                style: TextStyle(color: AppColors.muted),
              ),
            ),
          ),
          const SizedBox(height: 24),
          const SectionHeading('Android App Usage Monitoring'),
          const SizedBox(height: 10),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    value: _monitoringEnabled,
                    onChanged: _toggleMonitoring,
                    title: const Text('Enable app-usage checks'),
                    subtitle: const Text(
                      'Optional. After Usage Access, choose specific apps in Attention OS. Android may delay usage events.',
                      style: TextStyle(color: AppColors.muted),
                    ),
                  ),
                  const Divider(height: 22),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          _checkingPermission
                              ? 'Checking usage-access permission…'
                              : _usageAccessGranted == true
                              ? 'Usage access is granted'
                              : _usageAccessGranted == false
                              ? 'Usage access is not granted'
                              : 'Usage access status unavailable',
                          style: const TextStyle(fontWeight: FontWeight.w600),
                        ),
                      ),
                      if (_checkingPermission)
                        const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      else
                        Icon(
                          _usageAccessGranted == true
                              ? Icons.check_circle_outline
                              : Icons.info_outline,
                          color: _usageAccessGranted == true
                              ? AppColors.green
                              : AppColors.muted,
                        ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      OutlinedButton.icon(
                        onPressed: _explainAndOpenUsageSettings,
                        icon: const Icon(Icons.open_in_new_rounded),
                        label: const Text('Usage Access settings'),
                      ),
                      OutlinedButton.icon(
                        onPressed: _testNativeConnection,
                        icon: const Icon(Icons.cable_rounded),
                        label: const Text('Test native connection'),
                      ),
                      OutlinedButton.icon(
                        onPressed:
                            _usageAccessGranted == true && !_queryingUsage
                            ? _queryRecentUsage
                            : null,
                        icon: const Icon(Icons.query_stats_rounded),
                        label: const Text('Check last 24 hours'),
                      ),
                    ],
                  ),
                  if (_nativeConnectionStatus != null) ...[
                    const SizedBox(height: 8),
                    Text(
                      _nativeConnectionStatus!,
                      style: const TextStyle(color: AppColors.muted),
                    ),
                  ],
                  if (_queryingUsage) ...[
                    const SizedBox(height: 12),
                    const LinearProgressIndicator(),
                  ],
                  if (_usageAccessGranted == false) ...[
                    const SizedBox(height: 12),
                    const Text(
                      'Android requires you to grant Usage Access manually. Attention OS will not query usage until access is granted.',
                      style: TextStyle(color: AppColors.muted, height: 1.45),
                    ),
                  ],
                  if (_monitorError != null) ...[
                    const SizedBox(height: 10),
                    Text(
                      _monitorError!,
                      style: const TextStyle(color: Color(0xFFFF9B9B)),
                    ),
                  ],
                  const Divider(height: 28),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Apps to track during focus'),
                    subtitle: Text(
                      _trackedPackages.isEmpty
                          ? 'None selected. Choose apps to include in focus-session results.'
                          : '${_trackedPackages.length} app(s) selected. Tracking does not label them as distracting.',
                      style: const TextStyle(color: AppColors.muted),
                    ),
                    trailing: OutlinedButton(
                      onPressed: _loadingTrackableApps
                          ? null
                          : _openTrackedAppPicker,
                      child: _loadingTrackableApps
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Choose apps'),
                    ),
                  ),
                  if (_usageRecords.isNotEmpty) ...[
                    const SizedBox(height: 14),
                    const Text(
                      'Recent aggregate usage',
                      style: TextStyle(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 6),
                    ..._usageRecords
                        .take(8)
                        .map(
                          (record) => ListTile(
                            dense: true,
                            contentPadding: EdgeInsets.zero,
                            title: Text(record.packageName),
                            subtitle: Text(
                              'Foreground ${_durationLabel(record.totalTimeInForeground)} · '
                              'last used ${record.lastTimeUsed.toLocal()}',
                              style: const TextStyle(color: AppColors.muted),
                            ),
                            trailing: Checkbox(
                              value: _packagesController.text
                                  .split(',')
                                  .map((value) => value.trim())
                                  .contains(record.packageName),
                              onChanged: (_) =>
                                  _toggleFlaggedPackage(record.packageName),
                            ),
                            onTap: () =>
                                _toggleFlaggedPackage(record.packageName),
                          ),
                        ),
                  ] else if (_usageAccessGranted == true &&
                      !_queryingUsage &&
                      _nativeConnectionStatus != null) ...[
                    const SizedBox(height: 8),
                    const Text(
                      'No usage records have been queried yet.',
                      style: TextStyle(color: AppColors.muted),
                    ),
                  ],
                ],
              ),
            ),
          ),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(
                    controller: _packagesController,
                    decoration: const InputDecoration(
                      labelText: 'Apps to flag during focus (optional)',
                      hintText: 'com.example.social, com.example.video',
                      helperText: 'Enter exact package names, separated by commas. UsageStats may update late; no content is inspected.',
                      helperMaxLines: 3,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Align(
                    alignment: Alignment.centerRight,
                    child: OutlinedButton(
                      onPressed: () => _saveMonitoringPreferences(
                        enabled: _monitoringEnabled,
                      ),
                      child: const Text('Save app list'),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 22),
          const SectionHeading('Session sync backend'),
          const SizedBox(height: 10),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(
                    controller: _apiUrlController,
                    keyboardType: TextInputType.url,
                    autocorrect: false,
                    decoration: const InputDecoration(
                      labelText: 'FastAPI base URL',
                      hintText: 'http://192.168.1.20:8000',
                      helperText: 'On Wi-Fi, enter your laptop private IP. The LAN debug APK accepts local HTTP; hosted APIs require HTTPS.',
                    ),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      onPressed: _savingBackendUrl ? null : _saveBackendUrl,
                      child: _savingBackendUrl
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Save backend URL and connect'),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    widget.syncStatus,
                    style: const TextStyle(color: AppColors.muted),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          const SectionHeading('Notification monitoring'),
          const SizedBox(height: 10),
          const _StatusCard(
            icon: Icons.notifications_active_outlined,
            title: 'Not connected',
            detail: 'Notification access has not been requested or enabled. Notification monitoring is not part of this milestone.',
          ),
          const SizedBox(height: 24),
          const SectionHeading('Privacy'),
          const SizedBox(height: 10),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.shield_outlined, color: AppColors.green),
                      SizedBox(width: 10),
                      Text(
                        'Local by default. Sync is your choice.',
                        style: TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'When a backend URL is configured, session goals, durations, '
                    'completion status, and an optional distraction self-report '
                    'are sent to that configured service. App-usage results stay on '
                    'this device and are not uploaded. No notification content '
                    'is collected.',
                    style: Theme.of(context).textTheme.bodySmall
                        ?.copyWith(color: AppColors.muted, height: 1.5),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  static String _durationLabel(Duration duration) {
    if (duration.inMinutes < 1) return '${duration.inSeconds}s';
    return '${duration.inMinutes}m';
  }
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({
    required this.icon,
    required this.title,
    required this.detail,
  });

  final IconData icon;
  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 9),
      child: ListTile(
        leading: Icon(icon, color: AppColors.muted),
        title: Text(title),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 5),
          child: Text(
            detail,
            style: const TextStyle(color: AppColors.muted, height: 1.4),
          ),
        ),
        trailing: const Icon(
          Icons.block_rounded,
          color: AppColors.muted,
          size: 20,
        ),
      ),
    );
  }
}
