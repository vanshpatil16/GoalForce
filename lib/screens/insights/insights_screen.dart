import 'package:flutter/material.dart';

import '../../core/theme/app_theme.dart';
import '../../models/focus_session.dart';
import '../../widgets/section_heading.dart';
import '../../widgets/stat_card.dart';

class InsightsScreen extends StatelessWidget {
  const InsightsScreen({required this.sessions, super.key});

  final List<FocusSession> sessions;

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final weekStart = DateTime(
      now.year,
      now.month,
      now.day,
    ).subtract(Duration(days: now.weekday - 1));
    final weeklySessions = sessions.where(
      (session) => !session.startedAt.isBefore(weekStart),
    );
    final weekSeconds = weeklySessions.fold<int>(
      0,
      (sum, session) => sum + session.actualDuration.inSeconds,
    );
    final completed = weeklySessions
        .where((session) => session.status == FocusSessionStatus.completed)
        .length;
    final distractions = weeklySessions.fold<int>(
      0,
      (sum, session) => sum + (session.reportedDistractions ?? 0),
    );
    final dailyMinutes = List<int>.generate(7, (index) {
      final day = weekStart.add(Duration(days: index));
      return sessions
          .where(
            (session) =>
                session.startedAt.year == day.year &&
                session.startedAt.month == day.month &&
                session.startedAt.day == day.day,
          )
          .fold<int>(
            0,
            (sum, session) => sum + session.actualDuration.inMinutes,
          );
    });

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 22, 20, 30),
        children: [
          Text(
            'Insights',
            style: Theme.of(context).textTheme.headlineSmall
                ?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          const Text(
            'A clear view of the focus time you choose to record.',
            style: TextStyle(color: AppColors.muted),
          ),
          const SizedBox(height: 22),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
            decoration: BoxDecoration(
              color: AppColors.blue.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Row(
              children: [
                Icon(
                  Icons.info_outline_rounded,
                  color: AppColors.blue,
                  size: 17,
                ),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'All values are from sessions completed in this app; no sample data.',
                    style: TextStyle(color: AppColors.blue, fontSize: 11),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: StatCard(
                  label: 'Focus this week',
                  value: _formatFocus(weekSeconds),
                  icon: Icons.timelapse_rounded,
                  accent: AppColors.blue,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: StatCard(
                  label: 'Completed',
                  value: '$completed',
                  icon: Icons.check_circle_outline_rounded,
                  accent: AppColors.green,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          StatCard(
            label: 'User-reported distractions this week',
            value: '$distractions',
            icon: Icons.bolt_rounded,
            accent: AppColors.purple,
          ),
          const SizedBox(height: 26),
          const SectionHeading('This week'),
          const SizedBox(height: 4),
          const Text(
            'Minutes in recorded focus sessions',
            style: TextStyle(color: AppColors.muted, fontSize: 12),
          ),
          const SizedBox(height: 14),
          _WeeklyChart(minutes: dailyMinutes, weekStart: weekStart),
          const SizedBox(height: 20),
          const Text(
            'Distraction counts are optional self-reports from session summaries. '
            'App usage and notifications are not monitored.',
            style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.5),
          ),
        ],
      ),
    );
  }

  static String _formatFocus(int seconds) {
    final minutes = seconds ~/ 60;
    if (minutes == 0) return '0 min';
    if (minutes < 60) return '$minutes min';
    return '${minutes ~/ 60}h ${minutes % 60}m';
  }
}

class _WeeklyChart extends StatelessWidget {
  const _WeeklyChart({required this.minutes, required this.weekStart});

  final List<int> minutes;
  final DateTime weekStart;

  @override
  Widget build(BuildContext context) {
    final maximum = minutes.fold<int>(0, (a, b) => a > b ? a : b);
    final today = DateTime.now();
    const labels = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 18, 14, 12),
        child: Column(
          children: [
            SizedBox(
              height: 150,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: List.generate(7, (index) {
                  final day = weekStart.add(Duration(days: index));
                  final isToday =
                      day.year == today.year &&
                      day.month == today.month &&
                      day.day == today.day;
                  final value = minutes[index];
                  final height = maximum == 0
                      ? 5.0
                      : (value / maximum * 112).clamp(5.0, 112.0);
                  return Expanded(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        if (value > 0)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 6),
                            child: Text(
                              '$value',
                              style: const TextStyle(
                                fontSize: 10,
                                color: AppColors.muted,
                              ),
                            ),
                          ),
                        Container(
                          height: height,
                          width: 16,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(10),
                            gradient: value > 0
                                ? const LinearGradient(
                                    colors: [AppColors.purple, AppColors.blue],
                                    begin: Alignment.topCenter,
                                    end: Alignment.bottomCenter,
                                  )
                                : null,
                            color: value == 0 ? AppColors.elevated : null,
                          ),
                        ),
                        const SizedBox(height: 10),
                        Text(
                          labels[index],
                          style: TextStyle(
                            fontSize: 11,
                            color: isToday ? AppColors.purple : AppColors.muted,
                            fontWeight: isToday
                                ? FontWeight.w700
                                : FontWeight.normal,
                          ),
                        ),
                      ],
                    ),
                  );
                }),
              ),
            ),
            if (maximum == 0) ...[
              const SizedBox(height: 12),
              const Text(
                'Your recorded weekly focus will appear here.',
                style: TextStyle(color: AppColors.muted, fontSize: 12),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
