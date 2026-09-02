import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../data/local/converters.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../repository/submissions_repository.dart';
import '../exam/user_information_screen.dart';
import '../router.dart';

/// Port of `ui/surveys/PublicSurveyActivity.kt`.
///
/// Lets anyone respond to a `publicAccess` survey without logging in. Opened
/// from `/survey/<teamId>/<surveyId>` deep links. Fetches the survey from the
/// server's public API, stores it locally, hosts the standard survey form, then
/// collects respondent information and posts back through the public API.
class PublicSurveyScreen extends ConsumerStatefulWidget {
  const PublicSurveyScreen({
    required this.baseUrl,
    required this.teamId,
    required this.surveyId,
    super.key,
  });

  final String baseUrl;
  final String teamId;
  final String surveyId;

  @override
  ConsumerState<PublicSurveyScreen> createState() => _PublicSurveyScreenState();
}

class _PublicSurveyScreenState extends ConsumerState<PublicSurveyScreen> {
  bool _loading = true;
  bool _loadFailed = false;
  bool _submitting = false;
  SurveyRow? _survey;
  final List<SurveyQuestionRow> _questions = [];
  final Map<String, TextEditingController> _textControllers = {};
  final Map<String, Set<ExamChoice>> _choiceAnswers = {};

  @override
  void initState() {
    super.initState();
    _loadSurvey();
  }

  @override
  void dispose() {
    for (final controller in _textControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _loadSurvey() async {
    final repo = ref.read(surveysRepositoryProvider);
    final doc = await repo.fetchPublicSurvey(
      widget.baseUrl,
      widget.teamId,
      widget.surveyId,
    );
    if (doc == null) {
      if (mounted) setState(() => _loadFailed = true);
      return;
    }
    final survey = await repo.saveSurveyFromPublicApi(doc);
    if (survey == null || !mounted) {
      if (mounted) setState(() => _loadFailed = true);
      return;
    }
    _survey = survey;
    // `saveSurveyFromPublicApi` has just written the questions through
    // `SurveyMapper`, so they are read back rather than parsed a second time
    // here. The screen used to re-parse the document itself because the mapper
    // flattened `choices` to `toString()`d maps; with that fixed the local copy
    // was a second, divergent parser — and the divergence was load-bearing, as
    // its question ids were keyed on the route's `surveyId` while the mapper's
    // are keyed on the document's `_id`, so the answer map `_submit` builds
    // could miss every row `createSurveyDraft` writes.
    _questions.addAll(await repo.questionsFor(widget.surveyId));
    for (final question in _questions) {
      if (question.choices.isEmpty) {
        _textControllers[question.id] = TextEditingController();
      } else {
        _choiceAnswers[question.id] = <ExamChoice>{};
      }
    }
    if (mounted) setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (_loadFailed) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.takeSurvey)),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(l10n.surveyLoadFailed),
                const SizedBox(height: 16),
                FilledButton(onPressed: _leave, child: Text(l10n.close)),
              ],
            ),
          ),
        ),
      );
    }
    if (_loading) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.takeSurvey)),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    // `ExamTakingFragment` hides the form and the submit button outright when
    // the document carries no questions and labels the counter
    // `no_questions` ("No questions available"). Without this the respondent
    // was offered a Submit button on an empty page, and `_submit`'s
    // answered-everything guard is vacuously true for zero questions — so it
    // created and POSTed a submission with no answers in it.
    if (_questions.isEmpty) {
      return Scaffold(
        appBar: AppBar(title: Text(_survey?.name ?? l10n.takeSurvey)),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(l10n.surveyHasNoQuestions),
          ),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(title: Text(_survey?.name ?? l10n.takeSurvey)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (_survey?.description?.isNotEmpty == true)
            Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: Text(_survey!.description!),
            ),
          for (var index = 0; index < _questions.length; index++)
            _QuestionCard(
              number: index + 1,
              question: _questions[index],
              controller: _textControllers[_questions[index].id],
              selected: _choiceAnswers.putIfAbsent(
                _questions[index].id,
                () => <ExamChoice>{},
              ),
              onChanged: () => setState(() {}),
            ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: _submitting ? null : _submit,
            icon: _submitting
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.send),
            label: Text(l10n.submitSurvey),
          ),
        ],
      ),
    );
  }

  /// `PublicSurveyActivity` calls `finish()` when the survey will not load. A
  /// deep link opens this screen as the first route on the stack, so there is
  /// nothing to pop and `maybePop` left the respondent staring at the failure
  /// card. Fall through to the same destination the submit path uses.
  void _leave() {
    final navigator = Navigator.of(context);
    if (navigator.canPop()) {
      navigator.pop();
      return;
    }
    final session = ref.read(sessionProvider).valueOrNull;
    context.go(session != null ? Routes.resources : Routes.login);
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context);
    // `ExamTakingFragment` has no notion of an optional question: `btnNext` is
    // hidden until the current one is answered, and submitting with a blank
    // answer toasts `please_select_write_your_answer_to_continue`
    // (`isQuestionAnswered`). The guard used to key on a `required` flag read
    // off the document, but Planet never writes one — so it was false for every
    // question of every real survey and an untouched answer sheet went to the
    // server as a row of empty strings.
    final missing = _questions.any(
      (question) => question.choices.isEmpty
          ? _textControllers[question.id]!.text.trim().isEmpty
          : _choiceAnswers[question.id]!.isEmpty,
    );
    if (missing) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.answerRequiredQuestions)));
      return;
    }

    setState(() => _submitting = true);

    final answers = <String, SubmissionDraftAnswer>{};
    for (final question in _questions) {
      if (question.choices.isEmpty) {
        answers[question.id] = SubmissionDraftAnswer(
          questionId: question.questionId,
          value: _textControllers[question.id]!.text.trim(),
        );
      } else if (_isSelectMultiple(question.type)) {
        final selected = _choiceAnswers[question.id]!;
        answers[question.id] = SubmissionDraftAnswer(
          questionId: question.questionId,
          choices: selected.map((c) => jsonEncode(c.toJson())).toList(),
        );
      } else {
        final selected = _choiceAnswers[question.id]!.firstOrNull;
        answers[question.id] = SubmissionDraftAnswer(
          questionId: question.questionId,
          choices: selected != null
              ? [jsonEncode(selected.toJson())]
              : const [],
        );
      }
    }

    try {
      final repo = ref.read(surveysRepositoryProvider);
      final survey = await repo.getById(widget.surveyId);
      final questions = await repo.questionsFor(widget.surveyId);
      if (survey == null) throw StateError('survey missing');

      final userId = 'public_${DateTime.now().millisecondsSinceEpoch}';
      final submissionId = await ref
          .read(submissionsRepositoryProvider)
          .createSurveyDraft(
            survey: survey,
            questions: questions,
            userId: userId,
            answers: answers,
          );

      if (!mounted) return;
      final saved = await Navigator.of(context).push<bool>(
        MaterialPageRoute<bool>(
          builder: (_) => UserInformationScreen(
            submissionId: submissionId,
            teamId: widget.teamId,
            showAdditionalFields: false,
          ),
        ),
      );

      // A cancelled profile step sends nothing, the way
      // `PublicSurveyActivity.uploadCompletedSubmission` finds no `complete`
      // submission to POST when the dialog is dismissed without saving. The
      // answers stay on the device. (The port marks the draft `complete` on
      // creation, so the status alone cannot carry this.)
      if (!mounted || saved != true) return;
      final success = await ref
          .read(surveysRepositoryProvider)
          .submitPublicSurvey(
            baseUrl: widget.baseUrl,
            teamId: widget.teamId,
            surveyId: widget.surveyId,
            submissionId: submissionId,
          );
      if (success) {
        // Nothing else records the delivery: the public API is not a CouchDB
        // insert, so there is no revision to store. Without this the outbox
        // would accept the same answer sheet again.
        await ref
            .read(submissionsRepositoryProvider)
            .markPublicSubmitted(submissionId);
      }

      // A failed post used to end here with "could not save your answers",
      // which was true — the answers were gone. They are kept now, and go out
      // on the next drain.
      final queued =
          !success &&
          await ref
              .read(publicSurveyUploaderProvider)
              .queue(
                baseUrl: widget.baseUrl,
                teamId: widget.teamId,
                surveyId: widget.surveyId,
                submissionId: submissionId,
              );

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            success
                ? l10n.thankYouForTakingSurvey
                : queued
                ? l10n.savedOffline
                : l10n.surveySubmitFailed,
          ),
        ),
      );

      // Leaving the form is right in both delivered cases: the answer sheet is
      // either on the server or durably queued, and re-submitting it would post
      // a second copy.
      if (success || queued) {
        final session = ref.read(sessionProvider).valueOrNull;
        if (mounted) {
          context.go(session != null ? Routes.resources : Routes.login);
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(l10n.surveySubmitFailed)));
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}

/// `ExamTakingFragment.startExam` compares the question type with
/// `equals("selectMultiple", ignoreCase = true)`, and so does this port's own
/// `SurveysRepository._buildPublicAnswers`. Matching case-sensitively here put
/// the two halves out of step: a document spelling it `selectmultiple` drew
/// radio buttons, so the respondent could pick exactly one, and everything else
/// they meant to say was gone before the payload was built.
bool _isSelectMultiple(String? type) => type?.toLowerCase() == 'selectmultiple';

class _QuestionCard extends StatelessWidget {
  const _QuestionCard({
    required this.number,
    required this.question,
    this.controller,
    required this.selected,
    required this.onChanged,
  });

  final int number;
  final SurveyQuestionRow question;
  final TextEditingController? controller;
  final Set<ExamChoice> selected;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final multiple = _isSelectMultiple(question.type);
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '$number. ${question.body ?? question.header ?? ''}',
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
                    if (checked == true) {
                      selected.add(choice);
                    } else {
                      selected.remove(choice);
                    }
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
