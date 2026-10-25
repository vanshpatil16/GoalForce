import 'package:flutter/material.dart';

import '../../core/theme/app_theme.dart';
import '../../models/focus_session.dart';
import '../../widgets/section_heading.dart';
import '../../widgets/stat_card.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({
    required this.sessions,
    required this.onStartSession,
    super.key,
  });

  final List<FocusSession> sessions;
  final VoidCallback onStartSession;

  @override
  Widget build(BuildContext context) {
    final today = DateTime.now();
    final todaySessions = sessions.where((session) {
      return session.startedAt.year == today.year &&
          session.startedAt.month == today.month &&
          session.startedAt.day == today.day;
    }).toList();
    final focusSeconds = todaySessions.fold<int>(
      0,
      (sum, session) => sum + session.actualDuration.inSeconds,
    );
    final completed = todaySessions
        .where((session) => session.status == FocusSessionStatus.completed)
        .length;
    final distractions = todaySessions.fold<int>(
      0,
      (sum, session) => sum + (session.reportedDistractions ?? 0),
    );
    final greeting = _greeting();

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 30),
        children: [
          Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(14),
                  gradient: const LinearGradient(
                    colors: [AppColors.purple, AppColors.blue],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                ),
                child: const Icon(
                  Icons.all_inclusive_rounded,
                  color: AppColors.background,
                ),
              ),
              const SizedBox(width: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'ATTENTION OS',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      letterSpacing: 1.4,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const Text(
                    'Your Attention. Your Rules.',
                    style: TextStyle(color: AppColors.muted, fontSize: 12),
                  ),
                ],
              ),
              const Spacer(),
              const Icon(
                Icons.account_circle_outlined,
                color: AppColors.muted,
                size: 27,
              ),
            ],
          ),
          const SizedBox(height: 30),
          Text(
            greeting,
            style: Theme.of(context).textTheme.headlineSmall
                ?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          Text(
            "Make room for what matters today.",
            style: Theme.of(context).textTheme.bodyMedium
                ?.copyWith(color: AppColors.muted),
          ),
          const SizedBox(height: 22),
          _FocusCallout(onStartSession: onStartSession),
          const SizedBox(height: 26),
          const SectionHeading('Today at a glance'),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: StatCard(
                  label: 'Focus time',
                  value: _focusLabel(focusSeconds),
                  icon: Icons.schedule_rounded,
                  accent: AppColors.blue,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: StatCard(
                  label: 'Sessions',
                  value: '$completed',
                  icon: Icons.check_circle_outline_rounded,
                  accent: AppColors.green,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: StatCard(
                  label: 'Distractions',
                  value: '$distractions',
                  icon: Icons.bolt_rounded,
                  accent: AppColors.purple,
                ),
              ),
            ],
          ),
          const SizedBox(height: 26),
          const SectionHeading('Recent focus sessions'),
          const SizedBox(height: 12),
          if (sessions.isEmpty)
            const _EmptySessions()
          else
            ...sessions
                .take(5)
                .map((session) => _SessionTile(session: session)),
          const SizedBox(height: 18),
          const _MonitoringNote(),
        ],
      ),
    );
  }

  static String _greeting() {
    final hour = DateTime.now().hour;
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  }

  static String _focusLabel(int seconds) {
    if (seconds == 0) return '0 min';
    final minutes = seconds ~/ 60;
    if (minutes < 60) return '$minutes min';
    return '${minutes ~/ 60}h ${minutes % 60}m';
  }
}

class _FocusCallout extends StatelessWidget {
  const _FocusCallout({required this.onStartSession});

  final VoidCallback onStartSession;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: const LinearGradient(
          colors: [Color(0xFF222343), Color(0xFF162A40)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        border: Border.all(color: AppColors.purple.withValues(alpha: 0.25)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: AppColors.purple.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(20),
            ),
            child: const Text(
              'DAILY FOCUS',
              style: TextStyle(
                color: AppColors.purple,
                letterSpacing: 1.1,
                fontSize: 10,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            'A little focus goes a long way.',
            style: Theme.of(context).textTheme.titleLarge
                ?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 7),
          const Text(
            'Set an intention. We’ll keep time while you do the work.',
            style: TextStyle(color: AppColors.muted, height: 1.4),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            height: 52,
            child: FilledButton.icon(
              onPressed: onStartSession,
              icon: const Icon(Icons.play_arrow_rounded),
              label: const Text('Start Focus Session'),
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.purple,
                foregroundColor: AppColors.background,
                textStyle: const TextStyle(fontWeight: FontWeight.w700),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptySessions extends StatelessWidget {
  const _EmptySessions();

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Row(
          children: [
            const Icon(Icons.hourglass_empty_rounded, color: AppColors.muted),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'Your focus sessions will appear here. No sample activity is shown.',
                style: Theme.of(context).textTheme.bodySmall
                    ?.copyWith(color: AppColors.muted, height: 1.4),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SessionTile extends StatelessWidget {
  const _SessionTile({required this.session});

  final FocusSession session;

  @override
  Widget build(BuildContext context) {
    final isComplete = session.status == FocusSessionStatus.completed;
    final statusLabel = isComplete
        ? 'Completed'
        : session.status == FocusSessionStatus.endedEarly
        ? 'Ended early'
        : 'In progress';

    return Card(
      margin: const EdgeInsets.only(bottom: 9),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        leading: CircleAvatar(
          backgroundColor: (isComplete ? AppColors.green : AppColors.blue)
              .withValues(alpha: 0.12),
          child: Icon(
            isComplete ? Icons.check_rounded : Icons.timelapse_rounded,
            color: isComplete ? AppColors.green : AppColors.blue,
          ),
        ),
        title: Text(session.goal, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(
          '$statusLabel · ${formatDuration(session.actualDuration)}',
          style: const TextStyle(color: AppColors.muted),
        ),
        trailing: Text(
          '${session.plannedMinutes}m',
          style: const TextStyle(color: AppColors.muted),
        ),
      ),
    );
  }
}

class _MonitoringNote extends StatelessWidget {
  const _MonitoringNote();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: const Row(
        children: [
          Icon(Icons.shield_outlined, color: AppColors.green, size: 19),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              'Private by default · App and notification monitoring is off.',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }
}
