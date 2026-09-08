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
import '../exam/user_information_screen.dart';
import '../router.dart';

/// Offline survey-taking form, replacing the survey mode of
/// `ExamTakingFragment.kt` for text and single/multiple-choice questions.
class TakeSurveyScreen extends ConsumerStatefulWidget {
  const TakeSurveyScreen({
    required this.surveyId,
    this.submissionId,
    this.teamId,
    super.key,
  });
  final String surveyId;
  final String? submissionId;

  /// The team this sheet is being answered for, or null for a personal one.
  ///
  /// Kotlin's pair of fragment arguments (`isTeam` plus the id, read at
  /// `BaseExamFragment:77-78`) collapses to one nullable value here, and
  /// `ExamTakingFragment` hands it to `CreateExamSubmissionRequest` as
  /// `if (isTeam) teamId else null` (`:151-152`) — so a null or blank value
  /// means "no team", and the repository's own blank guard is what enforces
  /// that rather than a check here.
  ///
  /// Only `${Routes.surveys}/:surveyId?teamId=` fills it, from
  /// `TeamSurveysScreen`. It is deliberately **not** carried into the resume
  /// path (`updateSurveyResponse`), and Kotlin agrees twice over: with a team
  /// it never resumes at all — `if (sub == null || isTeam)` recreates the
  /// sheet (`ExamTakingFragment:150-153`, `recreate = isTeam`) and the
  /// resume-or-restart branch below it is unreachable while `isTeam` is true —
  /// and every site that opens a *pending* sheet passes
  /// `isMySurvey = true, isTeam = false, teamId = ""`
  /// (`BellDashboardFragment:256`, `SubmissionsAdapter:98`). A resumed row
  /// already carries whatever team it was created with.
  final String? teamId;

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
      // Only a question that offers no choices has a text field to seed. A
      // choice answer's `value` is now the picked choice's display text (the
      // one `saveExamAnswer` derives), and seeding it would put that label in
      // a controller the card never renders for such a question. No symptom
      // is reachable today — a radio group cannot be cleared, so `_submit`'s
      // answered-everything guard cannot be fooled by the leftover text, and
      // a `selectMultiple` answer's `value` is empty — but seeding a field
      // that is not on screen is how that guard would come apart later.
      if (question.choices.isEmpty && answer.value?.isNotEmpty == true) {
        textAnswers[question.id]?.text = answer.value!;
      }
      if (answer.valueChoices.isNotEmpty) {
        choiceAnswers[question.id]?.addAll(
          answer.valueChoices
              .map(ExamChoice.decode)
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
              .map((choice) => choice.encode())
              .toList(growable: false),
        ),
    };
    // `submitting` disables the button, so anything that escapes here leaves
    // the form permanently unusable with no message — the user's answers are
    // still on screen but there is no way to send them.
    String? id;
    var askWhoTheyAre = false;
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
      // `BaseExamFragment.continueExam:127-131` sends the last question of a
      // *team* survey to `showUserInfoDialog` instead of the thank-you
      // dialog, and `showUserInfoDialog:153-164` opens the respondent form
      // unless the survey came from the nation — in which case it marks the
      // sheet complete and leaves. `exam?.isFromNation != true` is reproduced
      // literally, null included: a survey row that has not loaded takes the
      // ask-them branch there too.
      //
      // Awaited rather than read off the `AsyncValue` this screen watches:
      // the title falls back to a label while the survey loads, so the form
      // can be submitted before it resolves, and `.valueOrNull` would read
      // null and decide the gate on a value it does not have yet. The await
      // is inside the `try` because the future can reject.
      final survey = await ref.read(surveyProvider(widget.surveyId).future);
      // `!isMySurvey && exam?.isFromNation != true` (`:154-155`) — both
      // halves. A resumed sheet is Kotlin's `isMySurvey`, and it takes the
      // else arm: mark complete and leave, without asking. No pusher produces
      // a `?submission=` *and* a `?teamId=` today, so this half is latent —
      // but a gate that is a line-for-line port cannot drift into the case
      // later, and `updateSurveyResponse` deliberately carries no team, so
      // without it a resumed sheet would be asked for a profile it could not
      // attribute.
      askWhoTheyAre =
          widget.submissionId == null &&
          (widget.teamId ?? '').trim().isNotEmpty &&
          survey?.isFromNation != true;
      final repo = ref.read(surveysRepositoryProvider);
      id = widget.submissionId != null
          ? await repo.updateSurveyResponse(
              widget.submissionId!,
              answers: answers,
            )
          : await repo.submitResponse(
              widget.surveyId,
              user.id,
              answers,
              teamId: widget.teamId,
            );
      final config = ref.read(serverConfigProvider);
      // Not before the profile step, which is the order Kotlin uses: the
      // upload is `UserInformationFragment.onDismiss`'s job, *after* the
      // dialog.
      //
      // The reason that always holds is duplication, not loss, and an
      // earlier draft of this comment had it the other way round. Queueing
      // here enqueues a payload serialized before `markSubmissionComplete`
      // writes the profile; if the outbox drains before the respondent
      // saves — an app background/foreground cycle mid-form is enough — the
      // sheet is POSTed once without the profile, `markComplete` then puts
      // the row back in `pendingUploads` (`app_database.dart`'s
      // `markComplete` sets `uploaded: false, isUpdated: true`, and says so),
      // and the pop's queue POSTs it **again**. Two documents on the server
      // for one answer sheet, where Kotlin posts one. That is worse for a
      // survey's results than a late delivery, which is what the old comment
      // wrongly claimed was at stake here.
      //
      // The cost of this order is recorded in `PHASE_132_NOTES.md` under
      // *Reported, not fixed*: nothing in the port sweeps `pendingUploads` on
      // sync, so a sheet whose profile step is interrupted by process death
      // waits for the user's next submission. Kotlin's safety net is
      // `uploadSubmissions()` running unconditionally from `AutoSyncWorker`.
      if (id != null && config != null && !askWhoTheyAre) {
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
    if (id == null) return;
    if (askWhoTheyAre) {
      // Kotlin shows this over the exhausted question screen
      // (`childFragmentManager`) and, when it dismisses — Save or Cancel
      // alike — `onDismiss` uploads, thanks the respondent and pops the
      // survey off the back stack. The screen owns the first two; this owns
      // the pop, so the respondent lands back on the team's surveys tab
      // rather than on a submission detail they did not ask for.
      //
      // `Navigator.push` rather than the `Routes.userInfo` route, matching
      // `public_survey_screen`: Kotlin's is a dialog over this screen, not a
      // destination, and pushing a location would put it in the history.
      await Navigator.of(context).push(
        MaterialPageRoute<bool>(
          builder: (_) => UserInformationScreen(
            submissionId: id!,
            teamId: widget.teamId,
            // Kotlin's `shouldHideElements` is `exam?.isFromNation != true`,
            // which this branch has already established is true, and this
            // parameter is its negation.
            showAdditionalFields: false,
          ),
        ),
      );
      if (!mounted) return;
      // `FragmentNavigator.popBackStack`. A team survey is always reached by
      // a push from the team's surveys tab, so there is something to pop.
      //
      // The fallback is **not** a mirror of anything, and an earlier version
      // of this comment claimed it was: it cited Kotlin's `isFromNation` arm
      // landing on the survey list via `navigateToSurveyList`, which is dead
      // code (`isFromNation` has no writer that can make it true in Kotlin —
      // `insertCourseStepsExams` sets it from a `parentId` its only caller
      // passes as `""`), and `FragmentNavigator.popBackStack` is simply a
      // no-op on an empty back stack (`FragmentNavigator.kt:55`). Kept as a
      // belt-and-braces destination for a `go`-entered survey rather than for
      // fidelity.
      if (context.canPop()) {
        context.pop();
      } else {
        context.go(Routes.surveys);
      }
      return;
    }
    await _thankAndLeave();
  }

  /// Kotlin's terminal state for every non-team survey: the thank-you dialog,
  /// and its Finish button pops the survey off the back stack
  /// (`BaseExamFragment.continueExam:132-148`).
  ///
  /// **This replaced a `context.go('${Routes.submissions}/$id')`, which was a
  /// port invention with no Kotlin counterpart on any entry point.** No Kotlin
  /// path shows the learner their own submission after finishing one — the
  /// only openers of `SubmissionDetailFragment` are list-row taps. All five
  /// `openSurvey` call sites reach `continueExam`, four of them hardcoding
  /// `isTeam = false` (`SubmissionsAdapter.kt:98`, `CourseStepFragment.kt:289`,
  /// `BellDashboardFragment.kt:256`, `DashboardActivity.kt:220`) and the fifth
  /// (`SurveysAdapter.kt:88`) passing a variable, so the individual list, the
  /// my-surveys row, the dashboard's pending prompt, a deep link and a course
  /// step all end here.
  ///
  /// A pop rather than a destination is what makes one exit right for all of
  /// them: each was reached by a `context.push`, so each lands back where the
  /// learner started. **The course step is the entry that was actually
  /// broken**: `go` unmounted `TakeCourseScreen` outright, so the step tile's
  /// `await context.push(…)` never resumed and Phase 128's *redo survey*
  /// relabel — whose only trigger is a submission made on this screen — was
  /// unreachable on the one path that produces it.
  ///
  /// One deviation, deliberate. Kotlin composes its dialog title from two
  /// strings and the survey/exam word — *"Thank you for taking this survey!
  /// We wish you all the best."* (`:135`) — and no single Kotlin string
  /// matches that, so a new ARB key for it could not derive a translation from
  /// `values-*/strings.xml` and would show English in all five locales.
  /// [AppLocalizations.thankYouForTakingSurvey] is one sentence shorter and
  /// has a reviewed human translation in every locale. Five translations beat
  /// four extra words.
  ///
  /// `barrierDismissible: false` stands in for `setCancelable(false)`
  /// (`:147`) but is **not** its equal: Kotlin's also swallows the back
  /// button, and this dialog can still be dismissed with back. The outcome is
  /// identical today because either exit reaches the pop below, and it stops
  /// being identical the moment the pop is made conditional on how the dialog
  /// closed — `PopScope` is the literal port if that ever happens.
  ///
  /// **One arm reaches here that Kotlin sends elsewhere, and the divergence is
  /// a choice.** `showUserInfoDialog`'s else branch — a team survey that is
  /// `isFromNation` — marks the sheet complete, toasts and
  /// `navigateToSurveyList`s (`:156-163`). That branch is dead in Kotlin
  /// (`isFromNation`'s only writer derives it from a `parentId` both callers
  /// pass as `""`), but it is **live here**, because `SurveyMapper` reads the
  /// field straight off the server document. So its Kotlin behaviour is
  /// unexercised, unreviewed code, and this port gives the path the same exit
  /// as every other non-team survey rather than reproducing it. Recorded in
  /// `PHASE_139_NOTES.md` so the next lane can take the other view.
  ///
  /// `_submit` awaits `queuePending` before reaching here, so the pop cannot
  /// race the outbox row.
  Future<void> _thankAndLeave() async {
    final l10n = AppLocalizations.of(context);
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        title: Text(l10n.thankYouForTakingSurvey),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(l10n.finish),
          ),
        ],
      ),
    );
    if (!mounted) return;
    // `popBackStack` is a no-op on an empty back stack, so a survey that was
    // somehow reached without a push stays put rather than being sent
    // somewhere Kotlin would not send it.
    if (context.canPop()) context.pop();
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
