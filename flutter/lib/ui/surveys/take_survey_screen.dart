import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../data/local/converters.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/surveys_provider.dart';
import '../../repository/submissions_repository.dart';
import '../router.dart';

/// Offline survey-taking form, replacing the survey mode of
/// `ExamTakingFragment.kt` for text and single/multiple-choice questions.
class TakeSurveyScreen extends ConsumerStatefulWidget {
  const TakeSurveyScreen({
    required this.surveyId,
    this.submissionId,
    super.key,
  });
  final String surveyId;
  final String? submissionId;

  @override
  ConsumerState<TakeSurveyScreen> createState() => _TakeSurveyScreenState();
}

class _TakeSurveyScreenState extends ConsumerState<TakeSurveyScreen> {
  final textAnswers = <String, TextEditingController>{};

  /// Selections are held as [ExamChoice] objects, not labels: an answer
  /// records the whole `{id, text}` object (`Answer.valueChoicesArray`), so
  /// the id has to survive from the tap to the payload.
  final choiceAnswers = <String, Set<ExamChoice>>{};
  bool submitting = false;
  bool _loaded = false;

  @override
  void dispose() {
    for (final controller in textAnswers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _loadExistingAnswers(List<SurveyQuestionRow> rows) async {
    if (_loaded || widget.submissionId == null) return;
    _loaded = true;
    final answers = await ref
        .read(submissionsRepositoryProvider)
        .answersFor(widget.submissionId!);
    for (final answer in answers) {
      final questionId = answer.questionId;
      if (questionId == null) continue;
      SurveyQuestionRow? question;
      for (final q in rows) {
        if (_rawId(q) == questionId) {
          question = q;
          break;
        }
      }
      if (question == null) continue;
      if (answer.value?.isNotEmpty == true) {
        textAnswers[question.id]?.text = answer.value!;
      }
      if (answer.valueChoices.isNotEmpty) {
        choiceAnswers[question.id]?.addAll(
          answer.valueChoices
              .map(_decodeChoice)
              .whereType<ExamChoice>()
              // Only a choice the question still offers can be re-selected;
              // `ExamChoice` is a value type, so this is set membership.
              .where(question.choices.contains),
        );
      }
    }
    if (mounted) setState(() {});
  }

  static String _rawId(SurveyQuestionRow q) => q.questionId ?? q.id;

  /// A stored answer choice is the choice object as a JSON string; a row
  /// written before that was fixed carries a bare label instead, which
  /// [ExamChoice.fromJson] keeps as its own id.
  static ExamChoice? _decodeChoice(String raw) {
    try {
      return ExamChoice.fromJson(jsonDecode(raw));
    } on FormatException {
      return ExamChoice.fromJson(raw);
    }
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
        data: (rows) {
          if (rows.isNotEmpty && widget.submissionId != null && !_loaded) {
            WidgetsBinding.instance.addPostFrameCallback(
              (_) => _loadExistingAnswers(rows),
            );
          }
          return rows.isEmpty
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
                          () => <ExamChoice>{},
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
                );
        },
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
    setState(() => submitting = true);
    final answers = {
      for (final question in questions)
        question.id: SubmissionDraftAnswer(
          questionId: question.id,
          value: textAnswers[question.id]!.text.trim(),
          choices: choiceAnswers[question.id]!
              .map((choice) => jsonEncode(choice.toJson()))
              .toList(growable: false),
        ),
    };
    // `submitting` disables the button, so anything that escapes here leaves
    // the form permanently unusable with no message — the user's answers are
    // still on screen but there is no way to send them.
    String? id;
    try {
      // `ref.read(sessionProvider).valueOrNull` is null until something else
      // resolves that provider, and this screen never watches it: the early
      // `if (user == null) return` then dropped the answered sheet with no
      // dialog, no snackbar and no row — the Phase 100 shape, latent in the
      // app only because the router holds a `ref.listen` on the session.
      // Awaiting the future is what `ExamTakingFragment` does, which resolves
      // its own `userSessionManager.getUserModel()` before the survey is
      // usable. The await sits inside the `try` so a rejecting session takes
      // the failure path rather than reintroducing the silence.
      final user = await ref.read(sessionProvider.future);
      if (user == null) throw StateError('no signed-in user');
      final repo = ref.read(surveysRepositoryProvider);
      id = widget.submissionId != null
          ? await repo.updateSurveyResponse(
              widget.submissionId!,
              answers: answers,
            )
          : await repo.submitResponse(widget.surveyId, user.id, answers);
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
  final Set<ExamChoice> selected;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final prompt = question.body?.isNotEmpty == true
        ? question.body!
        : question.header ?? '';
    // `ExamTakingFragment.startExam` compares the type with
    // `equals("selectMultiple", ignoreCase = true)`. Matching case-sensitively
    // drew radio buttons for a document spelling it `selectmultiple`, so the
    // respondent could pick exactly one of several intended answers — the same
    // defect Phase 102 fixed in the public-survey screen.
    final multiple = question.type?.toLowerCase() == 'selectmultiple';
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
                  title: Text(choice.text),
                  onChanged: (checked) {
                    checked == true
                        ? selected.add(choice)
                        : selected.remove(choice);
                    onChanged();
                  },
                )
            else
              RadioGroup<ExamChoice?>(
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
                      RadioListTile<ExamChoice?>(
                        contentPadding: EdgeInsets.zero,
                        value: choice,
                        title: Text(choice.text),
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
