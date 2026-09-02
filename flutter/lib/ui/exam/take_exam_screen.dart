import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/files/submit_photos_files.dart';
import '../../core/system/photo_capture.dart';
import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../repository/submissions_repository.dart';

/// Port of `ExamTakingFragment.kt` / `BaseExamFragment.kt` for course exams.
///
/// Kotlin's exam model is a **retry** model, not a score model. The attempt is
/// persisted before the first question is shown (`startExamSession`), each
/// answer is written as the learner passes it (`updateAnsDb` ->
/// `saveExamAnswer`), and the verdict that call returns is a gate: `btnNext`
/// and `btnSubmit` both bail with the `incorrect_ans` snackbar when it is
/// false (`ExamTakingFragment.kt:196-204`, `:641-645`), so the learner stays
/// on the question and retries until it is right. That is why `mistakes`
/// accumulates and why every answer in a finished attempt is `isPassed` — a
/// consequence of the gate, not an assertion. There is no score at the end:
/// `continueExam` shows a thank-you dialog (`BaseExamFragment.kt:127-148`) and
/// the submission goes up as `requires grading` for Planet to mark.
///
/// Supports multiple question types:
/// - `select`: Single choice (radio buttons)
/// - `selectMultiple`: Multiple choice (checkboxes)
/// - `ratingScale`: Rating scale (1-9 buttons)
/// - `input`: Text input
/// - `textarea`: Multi-line text input
class TakeExamScreen extends ConsumerStatefulWidget {
  const TakeExamScreen({
    required this.examId,
    this.stepId,
    this.courseId,
    super.key,
  });

  final String examId;
  final String? stepId;
  final String? courseId;

  @override
  ConsumerState<TakeExamScreen> createState() => _TakeExamScreenState();
}

class _TakeExamScreenState extends ConsumerState<TakeExamScreen> {
  int _currentIndex = 0;

  /// Answers keyed by question id.
  ///
  /// This was previously a single `_singleAnswer`/`_selectedRating`/
  /// `_multipleAnswers` shared by every question, cleared on each navigation.
  /// That lost an answer as soon as you moved off its question, and graded all
  /// questions against whatever the last one happened to hold.
  final Map<String, ExamDraftAnswer> _answers = {};
  final Map<String, TextEditingController> _controllers = {};

  bool _isSubmitting = false;
  bool _isSaving = false;
  List<ExamQuestionRow> _questions = const [];

  /// The attempt this exam is being answered into, created once per mount.
  String? _submissionId;
  Future<String>? _session;

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final exam = ref.watch(examProvider(widget.examId));
    final questions = ref.watch(examQuestionsProvider(widget.examId));

    return Scaffold(
      appBar: AppBar(
        title: Text(exam.valueOrNull?.name ?? l10n.takeExam),
        actions: [
          IconButton(
            icon: const Icon(Icons.close),
            onPressed: () => _confirmExit(context),
          ),
        ],
      ),
      body: questions.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.examLoadFailed)),
        data: (rows) {
          if (rows.isEmpty) {
            return Center(child: Text(l10n.examHasNoQuestions));
          }
          _questions = rows;
          if (_currentIndex >= rows.length) {
            _currentIndex = rows.length - 1;
          }
          return _buildQuestionView(context, rows[_currentIndex], rows.length);
        },
      ),
    );
  }

  Widget _buildQuestionView(
    BuildContext context,
    ExamQuestionRow question,
    int totalQuestions,
  ) {
    final l10n = AppLocalizations.of(context);
    final progress = (_currentIndex + 1) / totalQuestions;

    return Column(
      children: [
        LinearProgressIndicator(value: progress),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Text(
            '${l10n.question} ${_currentIndex + 1} / $totalQuestions',
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (question.header?.isNotEmpty == true)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(
                      question.header!,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                if (question.body?.isNotEmpty == true)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 24),
                    child: Text(
                      question.body!,
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                  ),
                _buildAnswerInput(context, question),
              ],
            ),
          ),
        ),
        _buildNavigationButtons(context, totalQuestions),
      ],
    );
  }

  Widget _buildAnswerInput(BuildContext context, ExamQuestionRow question) {
    switch (question.type ?? 'input') {
      case 'select':
        return _buildRadioGroup(question);
      case 'selectMultiple':
        return _buildCheckboxGroup(question);
      case 'ratingScale':
        return _buildRatingScale(question);
      case 'textarea':
        return _buildTextField(context, question, maxLines: 5);
      default:
        return _buildTextField(context, question, maxLines: 1);
    }
  }

  Widget _buildTextField(
    BuildContext context,
    ExamQuestionRow question, {
    required int maxLines,
  }) {
    // Seeded from this question's own answer. Seeding from a shared field
    // pre-filled each new question with the previous question's text.
    final controller = _controllers.putIfAbsent(
      question.id,
      () => TextEditingController(text: _answers[question.id]?.value ?? ''),
    );
    return TextField(
      controller: controller,
      maxLines: maxLines,
      decoration: InputDecoration(
        labelText: AppLocalizations.of(context).yourAnswer,
        border: const OutlineInputBorder(),
      ),
      onChanged: (value) => _recordText(question, value),
    );
  }

  Widget _buildRadioGroup(ExamQuestionRow question) {
    final selected = _answers[question.id]?.choiceIds;
    return RadioGroup<String>(
      groupValue: (selected == null || selected.isEmpty)
          ? null
          : selected.first,
      onChanged: (value) {
        if (value == null) return;
        setState(() => _recordChoices(question, [value]));
      },
      child: Column(
        children: [
          for (final choice in question.choices)
            RadioListTile<String>(title: Text(choice.text), value: choice.id),
        ],
      ),
    );
  }

  Widget _buildCheckboxGroup(ExamQuestionRow question) {
    final selected = _answers[question.id]?.choiceIds ?? const <String>[];
    return Column(
      children: [
        for (final choice in question.choices)
          CheckboxListTile(
            title: Text(choice.text),
            value: selected.contains(choice.id),
            onChanged: (checked) {
              final next = selected.toList();
              if (checked == true) {
                if (!next.contains(choice.id)) next.add(choice.id);
              } else {
                next.remove(choice.id);
              }
              setState(() => _recordChoices(question, next));
            },
          ),
      ],
    );
  }

  Widget _buildRatingScale(ExamQuestionRow question) {
    final max = question.scaleMax > 0 ? question.scaleMax : 9;
    final current = _answers[question.id]?.value;
    return Wrap(
      spacing: 8,
      children: List.generate(max, (index) {
        final value = '${index + 1}';
        return ChoiceChip(
          label: Text(value),
          selected: current == value,
          onSelected: (isSelected) {
            setState(() => _recordText(question, isSelected ? value : ''));
          },
        );
      }),
    );
  }

  Widget _buildNavigationButtons(BuildContext context, int totalQuestions) {
    final l10n = AppLocalizations.of(context);
    final isLast = _currentIndex >= totalQuestions - 1;
    final busy = _isSaving || _isSubmitting;

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          if (_currentIndex > 0)
            OutlinedButton.icon(
              onPressed: busy ? null : _goBack,
              icon: const Icon(Icons.arrow_back),
              label: Text(l10n.previous),
            )
          else
            const SizedBox.shrink(),
          if (isLast)
            FilledButton.icon(
              onPressed: busy ? null : () => _submitExam(context),
              icon: _isSubmitting
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.check),
              label: Text(l10n.submitExam),
            )
          else
            FilledButton.icon(
              onPressed: busy ? null : () => _advance(context),
              icon: const Icon(Icons.arrow_forward),
              label: Text(l10n.next),
            ),
        ],
      ),
    );
  }

  /// Whether the current question has an answer to submit at all.
  ///
  /// Port of `isQuestionAnswered` (`ExamTakingFragment.kt:290-322`), minus its
  /// `hasOtherOption` clause, which the port has no "Other" choice to reach.
  /// Kotlin returns `false` for an unrecognised type because `startExam`
  /// renders no input for one; the port renders a text field, so the rule
  /// follows what the widget can actually hold rather than the type name.
  bool _isAnswered(ExamQuestionRow question) {
    final answer = _answers[question.id];
    return answer != null && !answer.isEmpty;
  }

  /// `btnBack`. Kotlin saves the current answer before moving
  /// (`ExamTakingFragment.kt:187-194`) and then moves regardless of the
  /// verdict — going back is the one way to leave a question wrong, and it
  /// still records the mistake.
  Future<void> _goBack() async {
    if (_currentIndex == 0) return;
    final question = _questions[_currentIndex];
    if (_isAnswered(question)) {
      await _saveCurrentAnswer(question, isFinal: false, isExplicit: false);
    }
    if (mounted) setState(() => _currentIndex--);
  }

  /// `btnNext`: save, and advance only if the answer was right.
  Future<void> _advance(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final question = _questions[_currentIndex];
    if (!_isAnswered(question)) {
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.pleaseAnswerToContinue)),
      );
      return;
    }
    final correct = await _saveCurrentAnswer(
      question,
      isFinal: false,
      isExplicit: false,
    );
    if (!mounted) return;
    if (correct != true) {
      if (correct == false) {
        messenger.showSnackBar(SnackBar(content: Text(l10n.incorrectAnswer)));
      }
      return;
    }
    setState(() => _currentIndex++);
  }

  /// Writes the current answer through `saveExamAnswer` and returns its
  /// verdict, or `null` when the save itself failed — the caller must not read
  /// a failed save as a wrong answer, or the learner would be told their
  /// answer was incorrect when the truth is that nothing was recorded.
  Future<bool?> _saveCurrentAnswer(
    ExamQuestionRow question, {
    required bool isFinal,
    required bool isExplicit,
  }) async {
    final messenger = ScaffoldMessenger.of(context);
    final l10n = AppLocalizations.of(context);
    setState(() => _isSaving = true);
    try {
      final submissionId = await _ensureSession();
      return await ref
          .read(submissionsRepositoryProvider)
          .saveExamAnswer(
            submissionId: submissionId,
            question: question,
            answer: _answers[question.id] ?? const ExamDraftAnswer(),
            isFinal: isFinal,
            isExplicitSubmission: isExplicit,
          );
    } catch (_) {
      if (mounted) {
        messenger.showSnackBar(SnackBar(content: Text(l10n.examSubmitFailed)));
      }
      return null;
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  /// The attempt every answer is written into, created on first use.
  ///
  /// Kotlin opens the session in `initializeExamData`, before the first
  /// question is drawn. Creating it here instead keeps `build` free of side
  /// effects, and the only difference it makes is that abandoning an exam
  /// without answering anything leaves no row behind — where Kotlin leaves an
  /// empty `pending` attempt that its next `deleteStale` clears.
  Future<String> _ensureSession() => _session ??= _createSession();

  Future<String> _createSession() async {
    // Awaited rather than read off the current `AsyncValue`. Kotlin's
    // `initializeExamData` resolves its own user (`userSessionManager
    // .getUserModel()`) before the exam is usable; a bare
    // `ref.read(sessionProvider).valueOrNull` instead reports `null` whenever
    // nothing else has resolved the provider yet — and this screen never
    // watches it. That silently dropped the whole attempt: no dialog, no
    // snackbar, the graded answers discarded. It only stays hidden in the
    // shipping app because the router holds a `ref.listen` on the session.
    final user = await ref.read(sessionProvider.future);
    final exam = await ref.read(examProvider(widget.examId).future);
    if (user == null || exam == null) {
      // Cleared so a retry can try again rather than replaying this failure
      // for the rest of the mount.
      _session = null;
      throw StateError('An exam attempt needs a signed-in user and an exam');
    }
    final id = await ref
        .read(submissionsRepositoryProvider)
        .startExamSession(
          exam: exam,
          questions: _questions,
          userId: user.id,
          courseId: widget.courseId,
        );
    _submissionId = id;
    return id;
  }

  void _recordChoices(ExamQuestionRow question, List<String> choiceIds) {
    _answers[question.id] = ExamDraftAnswer(
      choiceIds: List.unmodifiable(choiceIds),
    );
  }

  void _recordText(ExamQuestionRow question, String value) {
    _answers[question.id] = ExamDraftAnswer(value: value);
  }

  /// `btn_submit` on the last question — `onClick`
  /// (`ExamTakingFragment.kt:626-653`).
  ///
  /// The order is Kotlin's and it matters: save, gate on the verdict, and only
  /// then capture the verification photo and finish. A wrong final answer
  /// takes the `incorrect_ans` branch before `capturePhoto()` is reached, so
  /// the camera never opens for an attempt that is not finished.
  Future<void> _submitExam(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final question = _questions[_currentIndex];
    if (!_isAnswered(question)) {
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.pleaseAnswerToContinue)),
      );
      return;
    }

    // Persist before showing anything. The attempt is the deliverable — a
    // dialog the user dismisses is not a record of it.
    final correct = await _saveCurrentAnswer(
      question,
      isFinal: true,
      isExplicit: true,
    );
    if (!mounted) return;
    if (correct != true) {
      if (correct == false) {
        messenger.showSnackBar(SnackBar(content: Text(l10n.incorrectAnswer)));
      }
      return;
    }

    final exam = ref.read(examProvider(widget.examId)).valueOrNull;
    final submissionId = _submissionId;
    if (exam == null || submissionId == null) return;

    setState(() => _isSubmitting = true);
    try {
      final user = await ref.read(sessionProvider.future);
      await _captureVerificationPhoto(submissionId, exam, user!.id);
      final config = ref.read(serverConfigProvider);
      if (config != null) {
        await ref
            .read(submissionsUploaderProvider)
            .queuePending(config: config, userId: user.id);
        await ref
            .read(submitPhotosUploaderProvider)
            .queuePending(config: config);
      }
      if (!mounted) return;
      await _showResult();
    } catch (_) {
      if (!mounted) return;
      setState(() => _isSubmitting = false);
      messenger.showSnackBar(SnackBar(content: Text(l10n.examSubmitFailed)));
      return;
    }
    if (mounted) setState(() => _isSubmitting = false);
  }

  /// Captures a verification photo for a certified course exam, the port of
  /// `ExamTakingFragment.capturePhoto`.
  ///
  /// The Kotlin source captures on every `btn_submit` press when the course is
  /// certified and the exam is not the user's own survey; this screen carries
  /// only graded course exams (no `isMySurvey` analogue), so the gate is the
  /// certification flag alone. A null capture — no camera, permission denied,
  /// or the user backed out — is swallowed, matching `capturePhoto`'s own
  /// try/catch; the submission still uploads, just without a photo.
  Future<void> _captureVerificationPhoto(
    String submissionId,
    ExamRow exam,
    String memberId,
  ) async {
    final courseId = widget.courseId ?? exam.courseId;
    if (courseId == null || courseId.isEmpty) return;
    final isCertified = await ref
        .read(progressRepositoryProvider)
        .isCourseCertified(courseId);
    if (!isCertified) return;
    try {
      final photo = await PhotoCapture.instance.capture();
      if (photo == null) return;
      // The bytes go under the *photo row's* id, not the submission's. The
      // uploader resolves them with
      // `SubmitPhotosFiles.existingFileFor(photoId: row.id)`, so writing them
      // under the submission id — two different sha1s — made that lookup miss
      // every time: the document uploaded, the attachment step returned early,
      // and the verification photo a certified course exists to collect never
      // left the device. Deriving the id up front keeps the write and the
      // read-back on one key, which is what `SubmitPhotosFiles` documents.
      final capturedAt = DateTime.now();
      final photoId = SubmissionsRepository.photoIdFor(
        submissionId: submissionId,
        capturedAt: capturedAt,
        examId: exam.id,
        courseId: courseId,
      );
      final file = await SubmitPhotosFiles.write(
        photoId: photoId,
        filename: photo.filename,
        bytes: photo.bytes,
      );
      await ref
          .read(submissionsRepositoryProvider)
          .addSubmissionPhoto(
            submissionId: submissionId,
            examId: exam.id,
            courseId: courseId,
            memberId: memberId,
            photoLocation: file?.path,
            now: capturedAt,
          );
    } on Exception {
      // The capture or the write can fail on a device with no camera or no
      // free space. The submission is the deliverable; a missing photo is not
      // grounds to throw the attempt away.
    }
  }

  /// `continueExam`'s end-of-exam dialog (`BaseExamFragment.kt:132-147`):
  /// "Thank you for taking this exam! We wish you all the best." and a
  /// `Finish` button that pops the screen.
  ///
  /// There is no score here, and that is the point. The port used to compute a
  /// percentage and compare it with `passingPercentage`, which the retry gate
  /// makes meaningless — every answer in a finished attempt is correct, so the
  /// dialog would congratulate every learner with 100% however many times they
  /// got a question wrong. The record of that is `mistakes`, which the
  /// courses-progress screen shows per step, and the mark is Planet's to
  /// write: the attempt uploads as `requires grading`.
  Future<void> _showResult() async {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        title: Text(l10n.examComplete),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.celebration, size: 64, color: Colors.green),
            const SizedBox(height: 16),
            Text(
              l10n.thankYouForTakingExam,
              textAlign: TextAlign.center,
              style: theme.textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Text(l10n.savedOffline, style: theme.textTheme.bodySmall),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(l10n.finish),
          ),
        ],
      ),
    );
    if (mounted) context.pop();
  }

  Future<void> _confirmExit(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final shouldExit = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(l10n.exitExam),
        content: Text(l10n.exitExamMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(l10n.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(l10n.exit),
          ),
        ],
      ),
    );

    if (shouldExit == true && context.mounted) {
      context.pop();
    }
  }
}

/// Provider for a single exam by ID.
final examProvider = FutureProvider.family<ExamRow?, String>((
  ref,
  examId,
) async {
  return ref.watch(examDaoProvider).getById(examId);
});

/// Provider for exam questions by exam ID.
final examQuestionsProvider =
    FutureProvider.family<List<ExamQuestionRow>, String>((ref, examId) async {
      return ref.watch(examDaoProvider).questionsFor(examId);
    });
