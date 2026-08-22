import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/surveys_provider.dart';
import '../../repository/submissions_repository.dart';
import '../router.dart';

/// Offline survey-taking form, replacing the survey mode of
/// `ExamTakingFragment.kt` for text and single/multiple-choice questions.
class TakeSurveyScreen extends ConsumerStatefulWidget {
  const TakeSurveyScreen({required this.surveyId, super.key});
  final String surveyId;

  @override
  ConsumerState<TakeSurveyScreen> createState() => _TakeSurveyScreenState();
}

class _TakeSurveyScreenState extends ConsumerState<TakeSurveyScreen> {
  final textAnswers = <String, TextEditingController>{};
  final choiceAnswers = <String, Set<String>>{};
  bool submitting = false;

  @override
  void dispose() {
    for (final controller in textAnswers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final survey = ref.watch(surveyProvider(widget.surveyId));
    final questions = ref.watch(surveyQuestionsProvider(widget.surveyId));
    return Scaffold(
      appBar: AppBar(title: Text(survey.valueOrNull?.name ?? l10n.takeSurvey)),
      body: questions.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.surveyLoadFailed)),
        data: (rows) => rows.isEmpty
            ? Center(child: Text(l10n.surveyHasNoQuestions))
            : ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (survey.valueOrNull?.description?.isNotEmpty == true)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 16),
                      child: Text(survey.valueOrNull!.description!),
                    ),
                  for (var index = 0; index < rows.length; index++)
                    _QuestionCard(
                      number: index + 1,
                      question: rows[index],
                      controller: textAnswers.putIfAbsent(
                        rows[index].id,
                        TextEditingController.new,
                      ),
                      selected: choiceAnswers.putIfAbsent(
                        rows[index].id,
                        () => <String>{},
                      ),
                      onChanged: () => setState(() {}),
                    ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: submitting ? null : () => _submit(rows),
                    icon: submitting
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.send),
                    label: Text(l10n.submitSurvey),
                  ),
                ],
              ),
      ),
    );
  }

  Future<void> _submit(List<SurveyQuestionRow> questions) async {
    final l10n = AppLocalizations.of(context);
    final missing = questions.any(
      (question) =>
          question.required &&
          textAnswers[question.id]!.text.trim().isEmpty &&
          choiceAnswers[question.id]!.isEmpty,
    );
    if (missing) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.answerRequiredQuestions)));
      return;
    }
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    setState(() => submitting = true);
    final answers = {
      for (final question in questions)
        question.id: SubmissionDraftAnswer(
          questionId: question.id,
          value: textAnswers[question.id]!.text.trim(),
          choices: choiceAnswers[question.id]!.toList(growable: false),
        ),
    };
    // `submitting` disables the button, so anything that escapes here leaves
    // the form permanently unusable with no message — the user's answers are
    // still on screen but there is no way to send them.
    String? id;
    try {
      id = await ref
          .read(surveysRepositoryProvider)
          .submitResponse(widget.surveyId, user.id, answers);
      final config = ref.read(serverConfigProvider);
      if (id != null && config != null) {
        await ref
            .read(submissionsUploaderProvider)
            .queuePending(config: config, userId: user.id);
      }
    } catch (_) {
      if (!mounted) return;
      setState(() => submitting = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.surveySubmitFailed)));
      return;
    }
    if (!mounted) return;
    setState(() => submitting = false);
    if (id != null) context.go('${Routes.submissions}/$id');
  }
}

class _QuestionCard extends StatelessWidget {
  const _QuestionCard({
    required this.number,
    required this.question,
    required this.controller,
    required this.selected,
    required this.onChanged,
  });
  final int number;
  final SurveyQuestionRow question;
  final TextEditingController controller;
  final Set<String> selected;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final prompt = question.body?.isNotEmpty == true
        ? question.body!
        : question.header ?? '';
    final multiple = question.type == 'selectMultiple';
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '$number. $prompt${question.required ? ' *' : ''}',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            if (question.choices.isEmpty)
              TextField(controller: controller, maxLines: 3)
            else if (multiple)
              for (final choice in question.choices)
                CheckboxListTile(
                  contentPadding: EdgeInsets.zero,
                  value: selected.contains(choice),
                  title: Text(choice),
                  onChanged: (checked) {
                    checked == true
                        ? selected.add(choice)
                        : selected.remove(choice);
                    onChanged();
                  },
                )
            else
              RadioGroup<String>(
                groupValue: selected.firstOrNull,
                onChanged: (value) {
                  selected
                    ..clear()
                    ..add(value!);
                  onChanged();
                },
                child: Column(
                  children: [
                    for (final choice in question.choices)
                      RadioListTile<String>(
                        contentPadding: EdgeInsets.zero,
                        value: choice,
                        title: Text(choice),
                      ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}
