import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../data/local/app_database.dart';
import '../../data/local/converters.dart';
import '../../l10n/app_localizations.dart';
import '../../repository/submissions_repository.dart';
import '../../providers/submissions_provider.dart';
import '../../providers/app_providers.dart';

/// Metadata portion of `ui/submissions/SubmissionDetailFragment.kt`.
class SubmissionDetailScreen extends ConsumerWidget {
  const SubmissionDetailScreen({required this.submissionId, super.key});

  final String submissionId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final row = ref.watch(submissionProvider(submissionId));
    final answers = ref.watch(submissionAnswersProvider(submissionId));
    final questions = ref.watch(submissionQuestionsProvider(submissionId));
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.submissionDetails),
        actions: [
          IconButton(
            tooltip: l10n.exportPdf,
            icon: const Icon(Icons.picture_as_pdf_outlined),
            onPressed: () => _export(context, ref),
          ),
        ],
      ),
      body: row.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.submissionsUnavailable)),
        data: (submission) => submission == null
            ? Center(child: Text(l10n.submissionNotFound))
            : _Details(submission, answers: answers, questions: questions),
      ),
    );
  }

  Future<void> _export(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final file = await ref
        .read(submissionsExporterProvider)
        .generateFile(submissionId);
    if (!context.mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text(
          file == null ? l10n.pdfExportFailed : l10n.pdfSaved(file.path),
        ),
      ),
    );
  }
}

class _Details extends StatelessWidget {
  const _Details(this.row, {required this.answers, required this.questions});
  final SubmissionRow row;
  final AsyncValue<List<SubmissionAnswerRow>> answers;
  final AsyncValue<List<SubmissionQuestionRow>> questions;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final updated = row.lastUpdateTime == 0
        ? l10n.unknown
        : DateFormat.yMMMMd().add_jm().format(
            DateTime.fromMillisecondsSinceEpoch(row.lastUpdateTime),
          );
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          submissionDisplayTitle(row) ?? row.type ?? l10n.submission,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 16),
        _Detail(label: l10n.status, value: row.status ?? l10n.pending),
        _Detail(label: l10n.lastUpdated, value: updated),
        _Detail(label: l10n.grade, value: '${row.grade}'),
        _Detail(
          label: l10n.submittedBy,
          value: submissionSubmitterName(row) ?? row.userId ?? '—',
        ),
        _Detail(label: l10n.submissionType, value: row.type ?? '—'),
        _Detail(
          label: l10n.uploadStatus,
          value: row.uploaded ? l10n.uploaded : l10n.savedOffline,
        ),
        const Divider(height: 32),
        Text(l10n.answers, style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        answers.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, _) => Text(l10n.answersUnavailable),
          data: (rows) => rows.isEmpty
              ? Text(l10n.noAnswers)
              : Column(
                  children: [
                    for (var index = 0; index < rows.length; index++)
                      _AnswerTile(
                        answer: rows[index],
                        question: questions.valueOrNull
                            ?.where(
                              (question) => question.id.endsWith(
                                ':${rows[index].questionId}',
                              ),
                            )
                            .firstOrNull,
                        index: index,
                      ),
                  ],
                ),
        ),
      ],
    );
  }
}

class _AnswerTile extends StatelessWidget {
  const _AnswerTile({required this.answer, required this.index, this.question});
  final SubmissionAnswerRow answer;
  final SubmissionQuestionRow? question;
  final int index;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final value = answer.value?.trim().isNotEmpty == true
        ? answer.value!
        : answer.valueChoices.isNotEmpty
        // Each entry is a `{id, text}` object stored as a JSON string, the way
        // `Answer.valueChoicesArray` reads them back; Kotlin's own display
        // paths decode the label out rather than printing the entry.
        ? ExamChoice.labelsFor(answer.valueChoices)
        : l10n.noAnswer;
    // `correctChoices` holds choice **ids**, lowercased — both from
    // `ExamMapper._parseCorrectChoices` and from the `correctChoice` array a
    // synced question carries. A stored answer entry is the whole
    // `{id, text}` object, so it has to be reduced to its id before the
    // comparison; comparing the entries verbatim only ever matched the exam
    // path while it stored bare ids, and matched nothing once both paths
    // stored objects the way `saveExamAnswer` does.
    //
    // This indicator is the port's own: Kotlin's `getSubmissionDetail`
    // computes `isCorrect = question.getCorrectChoice()?.contains(answer
    // .value) == true`, but `QuestionAnswerAdapter.bind` never reads the
    // field and `item_question_answer.xml` has no view for it, so the Kotlin
    // detail screen shows no correctness at all. There is therefore no Kotlin
    // rendering to be faithful to, and its comparison is not available to
    // copy either: it pits the display *text* against a list of ids for
    // `select`, and for `selectMultiple` `answer.value` is the empty string.
    // Comparing ids is what `ExamGrading` does and follows the precedent
    // `ExamMapper._parseCorrectChoices` already set and documented.
    //
    // `isPassed` stays the first signal: it is the verdict Kotlin actually
    // computes and uploads.
    final normalized = {
      if (answer.value?.isNotEmpty ?? false) answer.value!.toLowerCase(),
      for (final entry in answer.valueChoices)
        if (ExamChoice.decode(entry) case final choice?)
          choice.id.toLowerCase(),
    };
    final correct =
        question != null &&
        question!.correctChoices.isNotEmpty &&
        question!.correctChoices.every(normalized.contains);
    return Card(
      child: ListTile(
        leading: Icon(
          answer.isPassed || correct ? Icons.check_circle : Icons.help_outline,
          color: answer.isPassed || correct ? Colors.green : null,
        ),
        title: Text(
          question?.header ??
              question?.body ??
              answer.questionId ??
              l10n.questionNumber(index + 1),
        ),
        subtitle: Text(
          question?.choices.isNotEmpty == true
              ? '$value\n${l10n.availableChoices}: ${question!.choices.join(', ')}'
              : value,
        ),
        // No trailing mark. `saveExamAnswer` writes `grade = 1` for **every**
        // exam answer, right or wrong — a "worth one mark" marker rather than
        // a score — so a badge reading it would say "1" on every row and mean
        // nothing. `QuestionAnswerAdapter.bind` renders it nowhere either.
        // The leading icon carries the verdict; `mistakes` carries how many
        // tries it took, and the courses-progress screen is where Kotlin
        // shows that.
      ),
    );
  }
}

class _Detail extends StatelessWidget {
  const _Detail({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => ListTile(
    contentPadding: EdgeInsets.zero,
    title: Text(label),
    subtitle: Text(value),
  );
}
