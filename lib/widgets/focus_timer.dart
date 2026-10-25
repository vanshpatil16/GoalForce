import 'package:flutter/material.dart';

import '../core/theme/app_theme.dart';

class FocusTimer extends StatelessWidget {
  const FocusTimer({
    required this.progress,
    required this.timeLabel,
    required this.isPaused,
    super.key,
  });

  final double progress;
  final String timeLabel;
  final bool isPaused;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 264,
      height: 264,
      child: Stack(
        alignment: Alignment.center,
        children: [
          SizedBox.expand(
            child: CircularProgressIndicator(
              value: 1,
              strokeWidth: 9,
              color: AppColors.border.withValues(alpha: 0.75),
            ),
          ),
          SizedBox.expand(
            child: CircularProgressIndicator(
              value: progress.clamp(0, 1),
              strokeWidth: 9,
              strokeCap: StrokeCap.round,
              backgroundColor: Colors.transparent,
              valueColor: const AlwaysStoppedAnimation(AppColors.purple),
            ),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                isPaused ? 'PAUSED' : 'FOCUSING',
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: AppColors.muted,
                  letterSpacing: 2,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                timeLabel,
                style: Theme.of(context).textTheme.displaySmall?.copyWith(
                  fontWeight: FontWeight.w700,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
              const SizedBox(height: 4),
              const Text('remaining', style: TextStyle(color: AppColors.muted)),
            ],
          ),
        ],
      ),
    );
  }
}
