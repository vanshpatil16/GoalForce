import 'package:flutter/material.dart';

import '../../core/theme/app_theme.dart';
import '../../models/focus_session.dart';
import '../../widgets/primary_button.dart';

class SessionSummaryScreen extends StatefulWidget {
  const SessionSummaryScreen({required this.session, super.key});

  final FocusSession session;

  @override
  State<SessionSummaryScreen> createState() => _SessionSummaryScreenState();
}

class _SessionSummaryScreenState extends State<SessionSummaryScreen> {
  int? _distractions;
  bool _skippedDistractionQuestion = false;
  bool _returned = false;

  void _returnToDashboard() {
    if (_returned) return;
    _returned = true;
    Navigator.of(context).pop(
      widget.session.copyWith(
        reportedDistractions: _skippedDistractionQuestion
            ? null
            : _distractions,
        clearReportedDistractions: true,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final completed = widget.session.status == FocusSessionStatus.completed;
    return PopScope(
      canPop: false,
      child: Scaffold(
        body: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 480),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Container(
                      width: 72,
                      height: 72,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: (completed ? AppColors.green : AppColors.blue)
                            .withValues(alpha: 0.13),
                      ),
                      child: Icon(
                        completed
                            ? Icons.check_rounded
                            : Icons.self_improvement,
                        color: completed ? AppColors.green : AppColors.blue,
                        size: 36,
                      ),
                    ),
                    const SizedBox(height: 22),
                    Text(
                      completed ? 'Session complete' : 'Session saved',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.headlineSmall
                          ?.copyWith(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      completed
                          ? 'You showed up for what matters.'
                          : 'Every focused minute counts.',
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: AppColors.muted),
                    ),
                    const SizedBox(height: 28),
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(18),
                        child: Column(
                          children: [
                            _SummaryRow('Goal', widget.session.goal),
                            const Divider(height: 24),
                            _SummaryRow(
                              'Planned',
                              '${widget.session.plannedMinutes} minutes',
                            ),
                            const SizedBox(height: 14),
                            _SummaryRow(
                              'Actual focus time',
                              formatDuration(widget.session.actualDuration),
                            ),
                            const SizedBox(height: 14),
                            _SummaryRow(
                              'Status',
                              completed ? 'Completed' : 'Ended early',
                            ),
                          ],
                        ),
                      ),
                    ),
                    if (widget.session.locallyObservedPackages.isNotEmpty) ...[
                      const SizedBox(height: 20),
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Selected-app activity (local only)',
                                style: TextStyle(fontWeight: FontWeight.w700),
                              ),
                              const SizedBox(height: 6),
                              const Text(
                                'UsageStats may be delayed or aggregated; activity does not prove distraction.',
                                style: TextStyle(
                                  color: AppColors.muted,
                                  fontSize: 12,
                                ),
                              ),
                              const SizedBox(height: 8),
                              ...widget.session.locallyObservedPackages.map(
                                (packageName) => Padding(
                                  padding: const EdgeInsets.only(top: 6),
                                  child: Text(
                                    packageName,
                                    style: const TextStyle(
                                      color: AppColors.muted,
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                    const SizedBox(height: 26),
                    Text(
                      'How many times did you feel distracted?',
                      style: Theme.of(context).textTheme.titleSmall
                          ?.copyWith(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 5),
                    const Text(
                      'Self-reported only. This is not measured by monitoring.',
                      style: TextStyle(color: AppColors.muted, fontSize: 12),
                    ),
                    const SizedBox(height: 14),
                    Wrap(
                      spacing: 8,
                      children: [
                        _DistractionChoice(
                          label: 'None',
                          value: 0,
                          selected: _distractions == 0,
                          onSelected: _selectDistractions,
                        ),
                        _DistractionChoice(
                          label: '1–2',
                          value: 2,
                          selected: _distractions == 2,
                          onSelected: _selectDistractions,
                        ),
                        _DistractionChoice(
                          label: '3+',
                          value: 3,
                          selected: _distractions == 3,
                          onSelected: _selectDistractions,
                        ),
                        _DistractionChoice(
                          label: 'Skip',
                          value: -1,
                          selected: _skippedDistractionQuestion,
                          onSelected: _selectDistractions,
                        ),
                      ],
                    ),
                    const SizedBox(height: 28),
                    PrimaryButton(
                      label: 'Return to dashboard',
                      icon: Icons.home_rounded,
                      onPressed: _returnToDashboard,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _selectDistractions(int value) {
    setState(() {
      _skippedDistractionQuestion = value == -1;
      _distractions = value == -1 ? null : value;
    });
  }
}

class _SummaryRow extends StatelessWidget {
  const _SummaryRow(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(label, style: const TextStyle(color: AppColors.muted)),
        ),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
        ),
      ],
    );
  }
}

class _DistractionChoice extends StatelessWidget {
  const _DistractionChoice({
    required this.label,
    required this.value,
    required this.selected,
    required this.onSelected,
  });

  final String label;
  final int value;
  final bool selected;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      onSelected: (_) => onSelected(value),
    );
  }
}
