import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/theme/app_theme.dart';
import 'models/focus_session.dart';
import 'services/android_monitor_service.dart';
import 'services/focus_session_service.dart';
import 'screens/focus/active_session_screen.dart';
import 'screens/focus/create_session_screen.dart';
import 'screens/home/home_screen.dart';
import 'screens/insights/insights_screen.dart';
import 'screens/settings/settings_screen.dart';

class AttentionOsApp extends StatefulWidget {
  const AttentionOsApp({super.key});

  @override
  State<AttentionOsApp> createState() => _AttentionOsAppState();
}

class _AttentionOsAppState extends State<AttentionOsApp> {
  final GlobalKey<NavigatorState> _navigatorKey = GlobalKey<NavigatorState>();
  final GlobalKey<ScaffoldMessengerState> _messengerKey =
      GlobalKey<ScaffoldMessengerState>();
  final List<FocusSession> _sessions = [];
  late final FocusSessionService _sessionService;
  late final AndroidMonitorService _androidMonitorService;
  int _selectedTab = 0;
  int _preferredDuration = 25;
  bool _remindersEnabled = false;
  bool _monitoringEnabled = false;
  String _distractionPackages = '';
  String _trackedPackages = '';
  String _apiBaseUrl = const String.fromEnvironment('ATTENTION_OS_API_URL');
  String _syncStatus =
      'Backend not configured. Session sync is currently disabled.';

  @override
  void initState() {
    super.initState();
    _sessionService = FocusSessionService();
    _androidMonitorService = AndroidMonitorService();
    _restoreBackendSettings();
  }

  Future<void> _restoreBackendSettings() async {
    try {
      final preferences = await SharedPreferences.getInstance();
      final storedUrl = preferences.getString('session_api_base_url');
      final apiUrl = storedUrl ?? _apiBaseUrl;
      if (!mounted) return;
      setState(() {
        _apiBaseUrl = apiUrl;
        _monitoringEnabled =
            preferences.getBool('usage_monitoring_enabled') ?? false;
        _distractionPackages =
            preferences.getString('usage_distraction_packages') ?? '';
        _trackedPackages =
            preferences.getString('usage_tracked_packages') ?? '';
        final savedDuration = preferences.getInt('preferred_focus_duration');
        if (savedDuration != null &&
            const [15, 25, 30, 45, 60].contains(savedDuration)) {
          _preferredDuration = savedDuration;
        }
        _remindersEnabled =
            preferences.getBool('session_reminders_enabled') ?? false;
      });
      if (apiUrl.isNotEmpty) {
        await _loadRemoteSessions(apiUrl);
        await _loadBackendPreferences(apiUrl);
      }
    } on PlatformException catch (error) {
      if (!mounted) return;
      setState(
        () => _syncStatus =
            'Could not restore local settings: ${error.message ?? error.code}',
      );
    }
  }

  Future<void> _loadRemoteSessions(String apiUrl) async {
    setState(() => _syncStatus = 'Connecting to the session backend…');
    try {
      final token = await _sessionService.deviceToken();
      final remote = await _sessionService.loadSessions(
        baseUrl: apiUrl,
        token: token,
      );
      if (!mounted) return;
      setState(() {
        for (final session in remote.reversed) {
          _sessions.removeWhere((item) => item.id == session.id);
          _sessions.insert(0, session);
        }
        _syncStatus = 'Connected. Sessions are synced to the configured API.';
      });
    } on FocusSessionApiException catch (error) {
      if (!mounted) return;
      setState(() => _syncStatus = error.message);
    }
  }

  Future<void> _startSession() async {
    final navigator = _navigatorKey.currentState;
    if (navigator == null) {
      throw StateError('The app navigator is not ready.');
    }
    final draft = await navigator.push<FocusSession>(
      MaterialPageRoute(
        builder: (_) => CreateSessionScreen(
          preferredDuration: _preferredDuration,
          remindersEnabled: _remindersEnabled,
        ),
      ),
    );
    if (!mounted || draft == null) return;

    final result = await navigator.push<FocusSession>(
      MaterialPageRoute(
        builder: (_) => ActiveSessionScreen(
          session: draft,
          monitoringEnabled: _monitoringEnabled,
          distractionPackages: _distractionPackages,
          trackedPackages: _trackedPackages,
          monitorService: _androidMonitorService,
        ),
      ),
    );
    if (!mounted || result == null) return;

    setState(() {
      _sessions.removeWhere((item) => item.id == result.id);
      _sessions.insert(0, result);
    });
    await _syncSession(result);
  }

  Future<void> _syncSession(FocusSession session) async {
    if (_apiBaseUrl.isEmpty) {
      setState(() {
        _syncStatus =
            'Session saved for this app run. Add a backend URL to sync.';
      });
      return;
    }
    setState(() => _syncStatus = 'Syncing the completed session…');
    try {
      final token = await _sessionService.deviceToken();
      await _sessionService.saveSession(
        baseUrl: _apiBaseUrl,
        token: token,
        session: session,
      );
      if (!mounted) return;
      setState(() => _syncStatus = 'Session synced to the configured API.');
      _messengerKey.currentState?.showSnackBar(
        const SnackBar(content: Text('Session synced successfully.')),
      );
    } on FocusSessionApiException catch (error) {
      if (!mounted) return;
      setState(() => _syncStatus = error.message);
      _messengerKey.currentState?.showSnackBar(
        SnackBar(content: Text('Session was not synced: ${error.message}')),
      );
    }
  }

  Future<void> _saveBackendUrl(String value) async {
    final apiUrl = value.trim();
    if (apiUrl.isNotEmpty &&
        !isSupportedFocusApiUrl(
          apiUrl,
          allowLanHttp: attentionOsLanHttpEnabled,
        )) {
      throw const FocusSessionApiException(
        'Enter an HTTPS API URL, or a private IPv4 LAN URL in the LAN-test debug build.',
      );
    }
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('session_api_base_url', apiUrl);
    if (!mounted) return;
    setState(() {
      _apiBaseUrl = apiUrl;
      if (apiUrl.isEmpty) {
        _syncStatus = 'Backend disconnected. Session sync is disabled.';
      }
    });
    if (apiUrl.isNotEmpty) {
      await _loadRemoteSessions(apiUrl);
      await _loadBackendPreferences(apiUrl);
    }
  }

  Future<void> _updatePreferences(int duration, bool remindersEnabled) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setInt('preferred_focus_duration', duration);
    await preferences.setBool('session_reminders_enabled', remindersEnabled);
    if (!mounted) return;
    setState(() {
      _preferredDuration = duration;
      _remindersEnabled = remindersEnabled;
    });
    if (_apiBaseUrl.isNotEmpty) await _saveBackendPreferences();
  }

  Future<void> _loadBackendPreferences(String apiUrl) async {
    try {
      final token = await _sessionService.deviceToken();
      final remote = await _sessionService.loadPreferences(
        baseUrl: apiUrl,
        token: token,
      );
      final duration = remote.preferredDuration ?? _preferredDuration;
      final reminders = remote.remindersEnabled ?? _remindersEnabled;
      if (remote.preferredDuration == null || remote.remindersEnabled == null) {
        await _sessionService.savePreferences(
          baseUrl: apiUrl,
          token: token,
          preferredDuration: duration,
          remindersEnabled: reminders,
        );
      }
      final preferences = await SharedPreferences.getInstance();
      await preferences.setInt('preferred_focus_duration', duration);
      await preferences.setBool('session_reminders_enabled', reminders);
      if (!mounted) return;
      setState(() {
        _preferredDuration = duration;
        _remindersEnabled = reminders;
        _syncStatus = 'Connected. Sessions and focus preferences are synced.';
      });
    } on FocusSessionApiException catch (error) {
      if (!mounted) return;
      setState(() => _syncStatus = error.message);
    }
  }

  Future<void> _saveBackendPreferences() async {
    try {
      final token = await _sessionService.deviceToken();
      await _sessionService.savePreferences(
        baseUrl: _apiBaseUrl,
        token: token,
        preferredDuration: _preferredDuration,
        remindersEnabled: _remindersEnabled,
      );
      if (!mounted) return;
      setState(() => _syncStatus = 'Focus preferences synced to the backend.');
    } on FocusSessionApiException catch (error) {
      if (!mounted) return;
      setState(() => _syncStatus = error.message);
    }
  }

  Future<void> _updateMonitoringPreferences({
    required bool enabled,
    required String distractionPackages,
  }) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('usage_monitoring_enabled', enabled);
    await preferences.setString(
      'usage_distraction_packages',
      distractionPackages,
    );
    if (!mounted) return;
    setState(() {
      _monitoringEnabled = enabled;
      _distractionPackages = distractionPackages;
    });
  }

  Future<void> _updateTrackedPackages(String value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('usage_tracked_packages', value);
    if (!mounted) return;
    setState(() => _trackedPackages = value);
  }

  @override
  void dispose() {
    _sessionService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      HomeScreen(sessions: _sessions, onStartSession: _startSession),
      InsightsScreen(sessions: _sessions),
      SettingsScreen(
        preferredDuration: _preferredDuration,
        remindersEnabled: _remindersEnabled,
        onPreferencesChanged: _updatePreferences,
        apiBaseUrl: _apiBaseUrl,
        syncStatus: _syncStatus,
        onBackendUrlSaved: _saveBackendUrl,
        monitoringEnabled: _monitoringEnabled,
        distractionPackages: _distractionPackages,
        trackedPackages: _trackedPackages,
        onMonitoringPreferencesChanged: _updateMonitoringPreferences,
        onTrackedPackagesChanged: _updateTrackedPackages,
        monitorService: _androidMonitorService,
      ),
    ];

    return MaterialApp(
      title: 'Attention OS',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark,
      navigatorKey: _navigatorKey,
      scaffoldMessengerKey: _messengerKey,
      home: Scaffold(
        body: IndexedStack(index: _selectedTab, children: pages),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _selectedTab,
          onDestinationSelected: (index) {
            setState(() => _selectedTab = index);
          },
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.grid_view_rounded),
              label: 'Home',
            ),
            NavigationDestination(
              icon: Icon(Icons.insights_rounded),
              label: 'Insights',
            ),
            NavigationDestination(
              icon: Icon(Icons.tune_rounded),
              label: 'Settings',
            ),
          ],
        ),
      ),
    );
  }
}
