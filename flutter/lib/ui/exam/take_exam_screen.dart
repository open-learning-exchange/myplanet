import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/files/submit_photos_files.dart';
import '../../core/system/photo_capture.dart';
import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../repository/exam_grading.dart';
import '../../repository/submissions_repository.dart';

/// Port of `ExamTakingFragment.kt` for course exams with grading.
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
  List<ExamQuestionRow> _questions = const [];

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

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          if (_currentIndex > 0)
            OutlinedButton.icon(
              onPressed: () => setState(() => _currentIndex--),
              icon: const Icon(Icons.arrow_back),
              label: Text(l10n.previous),
            )
          else
            const SizedBox.shrink(),
          if (isLast)
            FilledButton.icon(
              onPressed: _isSubmitting ? null : () => _submitExam(context),
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
              onPressed: () => setState(() => _currentIndex++),
              icon: const Icon(Icons.arrow_forward),
              label: Text(l10n.next),
            ),
        ],
      ),
    );
  }

  void _recordChoices(ExamQuestionRow question, List<String> choiceIds) {
    _answers[question.id] = ExamDraftAnswer(
      choiceIds: List.unmodifiable(choiceIds),
      isCorrect: ExamGrading.isSelectionCorrect(question, choiceIds),
    );
  }

  void _recordText(ExamQuestionRow question, String value) {
    _answers[question.id] = ExamDraftAnswer(
      value: value,
      isCorrect: ExamGrading.isTextCorrect(question, value),
    );
  }

  Future<void> _submitExam(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    // Awaited rather than read off the current `AsyncValue`. Kotlin's
    // `initializeExamData` resolves its own user (`userSessionManager
    // .getUserModel()`) before the exam is usable; a bare
    // `ref.read(sessionProvider).valueOrNull` instead reports `null` whenever
    // nothing else has resolved the provider yet — and this screen never
    // watches it. That silently dropped the whole attempt: no dialog, no
    // snackbar, the graded answers discarded. It only stays hidden in the
    // shipping app because the router holds a `ref.listen` on the session.
    final UserRow? user;
    try {
      user = await ref.read(sessionProvider.future);
    } catch (_) {
      // Awaiting a future means it can also reject, which `ref.read(...)
      // .valueOrNull` could not. Leaving that uncaught would reproduce the
      // very silence this await was introduced to remove — the attempt lost
      // with nothing on screen — so it reports the same failure the save path
      // does.
      if (!mounted) return;
      messenger.showSnackBar(SnackBar(content: Text(l10n.examSubmitFailed)));
      return;
    }
    if (!mounted) return;
    final exam = ref.read(examProvider(widget.examId)).valueOrNull;
    if (user == null || exam == null) return;

    setState(() => _isSubmitting = true);

    // Persist before showing anything. The attempt is the deliverable — a
    // dialog the user dismisses is not a record of it.
    try {
      final submissionId = await ref
          .read(submissionsRepositoryProvider)
          .createExamDraft(
            exam: exam,
            questions: _questions,
            userId: user.id,
            answers: _answers,
            courseId: widget.courseId,
          );
      await _captureVerificationPhoto(submissionId, exam, user.id);
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
      await _showResult(exam);
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

  Future<void> _showResult(ExamRow exam) async {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final correctCount = _questions
        .where((question) => _answers[question.id]?.isCorrect ?? false)
        .length;
    final total = _questions.length;
    final score = total > 0 ? (correctCount * 100) ~/ total : 0;
    // The exam carries its own bar; only fall back to 70 when it names none.
    final passMark = int.tryParse(exam.passingPercentage ?? '') ?? 70;
    final passed = score >= passMark;

    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        title: Text(l10n.examComplete),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              passed ? Icons.celebration : Icons.emoji_events,
              size: 64,
              color: passed ? Colors.green : Colors.orange,
            ),
            const SizedBox(height: 16),
            Text('$score%', style: theme.textTheme.headlineLarge),
            Text('${l10n.correctAnswers}: $correctCount / $total'),
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
