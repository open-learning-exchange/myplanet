import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';

import '../../l10n/app_localizations.dart';

/// Data class holding challenge dialog state.
///
/// Port of `DashboardViewModel.ChallengeDialogData`. Populated by
/// `ChallengeEvaluator.evaluate` (see `challenge_provider.dart`).
class ChallengeDialogData {
  final int voiceCount;
  final int allVoiceCount;
  final bool hasUnfinishedSurvey;
  final bool hasValidSync;
  final String courseStatus;

  const ChallengeDialogData({
    required this.voiceCount,
    required this.allVoiceCount,
    required this.hasUnfinishedSurvey,
    required this.hasValidSync,
    required this.courseStatus,
  });
}

/// Challenge Dialog - shows user's daily challenge progress
/// This is the Flutter port of the Kotlin MarkdownDialogFragment
class ChallengeDialog extends StatefulWidget {
  const ChallengeDialog({
    super.key,
    required this.voiceCount,
    required this.allVoiceCount,
    required this.hasUnfinishedSurvey,
    required this.hasValidSync,
    required this.courseStatus,
    required this.onStartCourse,
    required this.onNext,
    required this.onSync,
  });

  final int voiceCount;
  final int allVoiceCount;
  final bool hasUnfinishedSurvey;
  final bool hasValidSync;
  final String courseStatus;
  final VoidCallback onStartCourse;
  final VoidCallback onNext;
  final VoidCallback onSync;

  @override
  State<ChallengeDialog> createState() => _ChallengeDialogState();
}

class _ChallengeDialogState extends State<ChallengeDialog> {
  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final isCompleted = _isChallengeCompleted();

    return AlertDialog(
      title: Row(
        children: [
          const Icon(Icons.emoji_events, color: Colors.amber),
          const SizedBox(width: 8),
          Text(l10n.challengeDialogTitle),
        ],
      ),
      content: SizedBox(
        width: 400,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildProgressSection(context),
            const SizedBox(height: 16),
            _buildTasksSection(context),
            const SizedBox(height: 16),
            _buildMarkdownContent(context, isCompleted),
          ],
        ),
      ),
      actions: _buildActions(context, isCompleted),
    );
  }

  Widget _buildProgressSection(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final userEarnings =
        widget.voiceCount * 2 + (widget.hasUnfinishedSurvey ? 0 : 1);
    final progress = ((userEarnings / 11) * 100).clamp(0, 100);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        LinearProgressIndicator(
          value: progress / 100,
          backgroundColor: Colors.grey[300],
          valueColor: const AlwaysStoppedAnimation<Color>(Colors.green),
        ),
        const SizedBox(height: 8),
        Text(
          l10n.progressLabel(progress.toInt()),
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ],
    );
  }

  Widget _buildTasksSection(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final voiceTaskDone = widget.voiceCount >= 5;
    final courseTaskDone = widget.courseStatus.toLowerCase().contains(
      'terminado',
    );
    final syncTaskDone = _isSyncTaskDone();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _TaskRow(
          done: courseTaskDone,
          text: widget.courseStatus.isNotEmpty
              ? widget.courseStatus
              : l10n.courseNotStarted,
        ),
        _TaskRow(
          done: voiceTaskDone,
          text: l10n.voicesProgress(widget.voiceCount.clamp(0, 5)),
        ),
        _TaskRow(done: syncTaskDone, text: l10n.syncCompletedChallenge),
      ],
    );
  }

  Widget _buildMarkdownContent(BuildContext context, bool isCompleted) {
    final l10n = AppLocalizations.of(context);
    final communityEarnings = widget.allVoiceCount * 2;
    final userEarnings =
        widget.voiceCount * 2 + (widget.hasUnfinishedSurvey ? 0 : 1);

    final content = isCompleted
        ? '''
### ${l10n.congratulations}

${l10n.communityEarnings(communityEarnings)}
${l10n.yourEarnings(userEarnings)}
'''
        : '''
${l10n.communityEarnings(communityEarnings)}
${l10n.yourEarnings(userEarnings)}
''';

    return MarkdownBody(
      data: content,
      styleSheet: MarkdownStyleSheet(
        h3: Theme.of(context).textTheme.titleMedium,
        p: Theme.of(context).textTheme.bodyMedium,
      ),
    );
  }

  List<Widget> _buildActions(BuildContext context, bool isCompleted) {
    final l10n = AppLocalizations.of(context);

    if (isCompleted) {
      return [
        TextButton(
          onPressed: () {
            Navigator.of(context).pop();
          },
          child: Text(l10n.close),
        ),
      ];
    }

    final courseStatus = widget.courseStatus.toLowerCase();
    String buttonText;
    VoidCallback? onPressed;

    if (courseStatus.isEmpty || courseStatus == 'not started') {
      buttonText = l10n.start;
      onPressed = widget.onStartCourse;
    } else if (courseStatus.contains('terminado')) {
      if (widget.voiceCount >= 5) {
        buttonText = l10n.sync;
        onPressed = widget.onSync;
      } else {
        buttonText = l10n.next;
        onPressed = widget.onNext;
      }
    } else {
      buttonText = l10n.continuation;
      onPressed = widget.onStartCourse;
    }

    return [
      TextButton(
        onPressed: () => Navigator.of(context).pop(),
        child: Text(l10n.close),
      ),
      FilledButton(onPressed: onPressed, child: Text(buttonText)),
    ];
  }

  bool _isChallengeCompleted() {
    final voiceDone = widget.voiceCount >= 5;
    final courseDone = widget.courseStatus.toLowerCase().contains('terminado');
    final syncDone = _isSyncTaskDone();
    return voiceDone && courseDone && syncDone;
  }

  bool _isSyncTaskDone() {
    final prereqsMet =
        widget.courseStatus.toLowerCase().contains('terminado') &&
        widget.voiceCount >= 5;
    return prereqsMet && widget.hasValidSync;
  }
}

class _TaskRow extends StatelessWidget {
  const _TaskRow({required this.done, required this.text});

  final bool done;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(
            done ? Icons.check_box : Icons.check_box_outline_blank,
            color: done ? Colors.green : Colors.grey,
            size: 20,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                decoration: done ? TextDecoration.lineThrough : null,
                color: done ? Colors.grey : null,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Shows the challenge dialog if conditions are met
Future<void> showChallengeDialog({
  required BuildContext context,
  required int voiceCount,
  required int allVoiceCount,
  required bool hasUnfinishedSurvey,
  required bool hasValidSync,
  required String courseStatus,
  required VoidCallback onStartCourse,
  required VoidCallback onNext,
  required VoidCallback onSync,
}) async {
  return showDialog<void>(
    context: context,
    builder: (context) => ChallengeDialog(
      voiceCount: voiceCount,
      allVoiceCount: allVoiceCount,
      hasUnfinishedSurvey: hasUnfinishedSurvey,
      hasValidSync: hasValidSync,
      courseStatus: courseStatus,
      onStartCourse: onStartCourse,
      onNext: onNext,
      onSync: onSync,
    ),
  );
}
