import 'package:flutter/material.dart';

import '../../core/theme/app_theme.dart';
import '../../models/focus_session.dart';
import '../../widgets/primary_button.dart';

class CreateSessionScreen extends StatefulWidget {
  const CreateSessionScreen({
    required this.preferredDuration,
    required this.remindersEnabled,
    super.key,
  });

  final int preferredDuration;
  final bool remindersEnabled;

  @override
  State<CreateSessionScreen> createState() => _CreateSessionScreenState();
}

class _CreateSessionScreenState extends State<CreateSessionScreen> {
  static const _durations = [15, 25, 30, 45, 60];
  static const _examples = [
    'Study DSA',
    'Build a Flutter project',
    'Complete an assignment',
    'Practice coding',
  ];

  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _goalController;
  late int _duration;
  late bool _remindersEnabled;
  bool _starting = false;

  @override
  void initState() {
    super.initState();
    _goalController = TextEditingController();
    _duration = _durations.contains(widget.preferredDuration)
        ? widget.preferredDuration
        : 25;
    _remindersEnabled = widget.remindersEnabled;
  }

  @override
  void dispose() {
    _goalController.dispose();
    super.dispose();
  }

  void _start() {
    if (_starting || !_formKey.currentState!.validate()) return;
    setState(() => _starting = true);
    final session = FocusSession(
      id: createFocusSessionId(),
      goal: _goalController.text.trim(),
      plannedMinutes: _duration,
      startedAt: DateTime.now(),
      remindersEnabled: _remindersEnabled,
    );
    Navigator.of(context).pop(session);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('New focus session')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 10, 20, 28),
            children: [
              Text(
                'What matters right now?',
                style: Theme.of(context).textTheme.headlineSmall
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 8),
              const Text(
                'Choose one clear intention for this session.',
                style: TextStyle(color: AppColors.muted),
              ),
              const SizedBox(height: 24),
              TextFormField(
                controller: _goalController,
                autofocus: false,
                textCapitalization: TextCapitalization.sentences,
                maxLength: 80,
                decoration: const InputDecoration(
                  labelText: 'Your focus goal',
                  hintText: 'e.g. Finish the graph traversal practice',
                  prefixIcon: Icon(Icons.flag_outlined),
                  counterStyle: TextStyle(color: AppColors.muted),
                ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return 'Enter a goal to start your session.';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 18),
              Text(
                'Need an idea?',
                style: Theme.of(context).textTheme.titleSmall
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: _examples.map((example) {
                  return ActionChip(
                    label: Text(example),
                    onPressed: () => _goalController.text = example,
                  );
                }).toList(),
              ),
              const SizedBox(height: 28),
              Text(
                'Session length',
                style: Theme.of(context).textTheme.titleSmall
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: _durations.map((minutes) {
                  final selected = _duration == minutes;
                  return ChoiceChip(
                    label: Text('$minutes min'),
                    selected: selected,
                    onSelected: (_) => setState(() => _duration = minutes),
                    selectedColor: AppColors.purple.withValues(alpha: 0.2),
                    labelStyle: TextStyle(
                      color: selected ? AppColors.purple : Colors.white,
                      fontWeight: selected ? FontWeight.w700 : null,
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 18),
              Card(
                child: SwitchListTile(
                  value: _remindersEnabled,
                  onChanged: (value) =>
                      setState(() => _remindersEnabled = value),
                  title: const Text('Session reminders'),
                  subtitle: const Text(
                    'A local reminder preference for a future notification integration.',
                    style: TextStyle(color: AppColors.muted),
                  ),
                  secondary: const Icon(Icons.notifications_none_rounded),
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 4,
                  ),
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                'The timer tracks this session only. It does not monitor other apps.',
                textAlign: TextAlign.center,
                style: TextStyle(color: AppColors.muted, fontSize: 12),
              ),
              const SizedBox(height: 16),
              PrimaryButton(
                label: 'Start session',
                icon: Icons.play_arrow_rounded,
                onPressed: _starting ? null : _start,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
