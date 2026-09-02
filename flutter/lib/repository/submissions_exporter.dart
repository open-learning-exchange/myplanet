import 'dart:io';
import 'dart:typed_data';

import 'package:intl/intl.dart';
import 'package:meta/meta.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import '../data/local/app_database.dart';
import '../data/local/converters.dart';
import 'submissions_repository.dart';

/// Dart port of `SubmissionsRepositoryExporter` using the cross-platform PDF
/// package rather than Android's `PdfDocument` and `Canvas` APIs.
class SubmissionsExporter {
  const SubmissionsExporter(this._repository);

  final SubmissionsRepository _repository;

  Future<Uint8List?> generateBytes(String submissionId) async {
    final submission = await _repository.getById(submissionId);
    if (submission == null) return null;
    final answers = await _repository.watchAnswers(submission.id).first;
    final questions = await _repository.watchQuestions(submission.id).first;
    final answersByQuestion = {
      for (final answer in answers) answer.questionId: answer,
    };
    final document = pw.Document(
      title: submission.parent ?? 'Submission report',
      author: 'myPlanet',
    );
    document.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(48),
        build: (_) => [
          pw.Header(
            level: 0,
            child: pw.Text(submission.parent ?? 'Submission report'),
          ),
          _metadata(submission),
          pw.SizedBox(height: 20),
          if (questions.isEmpty && answers.isEmpty)
            pw.Text('No answers were saved with this submission.'),
          for (var index = 0; index < questions.length; index++)
            _question(
              index,
              questions[index],
              answersByQuestion[_rawQuestionId(questions[index])],
            ),
          if (questions.isEmpty)
            for (var index = 0; index < answers.length; index++)
              _answerOnly(index, answers[index]),
        ],
        footer: (context) => pw.Align(
          alignment: pw.Alignment.centerRight,
          child: pw.Text('Page ${context.pageNumber} of ${context.pagesCount}'),
        ),
      ),
    );
    return document.save();
  }

  /// The question/answer pairs the PDF renders, as plain text.
  ///
  /// A generated PDF compresses its content streams, so a test cannot search
  /// the bytes for an answer. This exposes the pairing itself — which is where
  /// the composite-row-id bug lived — without asserting on layout.
  @visibleForTesting
  Future<List<String>> renderedAnswerLines(String submissionId) async {
    final submission = await _repository.getById(submissionId);
    if (submission == null) return const [];
    final answers = await _repository.watchAnswers(submission.id).first;
    final questions = await _repository.watchQuestions(submission.id).first;
    final answersByQuestion = {
      for (final answer in answers) answer.questionId: answer,
    };
    return [
      for (final question in questions)
        () {
          final answer = answersByQuestion[_rawQuestionId(question)];
          return 'A: ${answer == null ? 'No answer' : _answerValue(answer)}';
        }(),
    ];
  }

  Future<File?> generateFile(
    String submissionId, {
    Directory? directory,
  }) async {
    final bytes = await generateBytes(submissionId);
    if (bytes == null) return null;
    final target = directory ?? await getApplicationDocumentsDirectory();
    final folder = Directory(p.join(target.path, 'Submissions'));
    await folder.create(recursive: true);
    final safeId = submissionId.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_');
    final file = File(
      p.join(
        folder.path,
        'submission_${safeId}_${DateTime.now().millisecondsSinceEpoch}.pdf',
      ),
    );
    await file.writeAsBytes(bytes, flush: true);
    return file;
  }

  pw.Widget _metadata(SubmissionRow row) => pw.Table(
    columnWidths: const {0: pw.FixedColumnWidth(90)},
    children: [
      _metadataRow('Status', row.status ?? 'Pending'),
      _metadataRow('Submitted by', row.user ?? row.userId ?? 'Unknown'),
      _metadataRow('Grade', '${row.grade}'),
      _metadataRow(
        'Updated',
        row.lastUpdateTime == 0
            ? 'Unknown'
            : DateFormat.yMMMd().add_jm().format(
                DateTime.fromMillisecondsSinceEpoch(row.lastUpdateTime),
              ),
      ),
    ],
  );

  pw.TableRow _metadataRow(String label, String value) => pw.TableRow(
    children: [
      pw.Text(label, style: pw.TextStyle(fontWeight: pw.FontWeight.bold)),
      pw.Text(value),
    ],
  );

  pw.Widget _question(
    int index,
    SubmissionQuestionRow question,
    SubmissionAnswerRow? answer,
  ) => pw.Container(
    margin: const pw.EdgeInsets.only(bottom: 16),
    child: pw.Column(
      crossAxisAlignment: pw.CrossAxisAlignment.start,
      children: [
        pw.Text(
          'Q${index + 1}: ${question.header ?? question.body ?? ''}',
          style: pw.TextStyle(fontWeight: pw.FontWeight.bold),
        ),
        if (question.body != null && question.header != null)
          pw.Text(question.body!),
        pw.SizedBox(height: 4),
        pw.Text('A: ${answer == null ? 'No answer' : _answerValue(answer)}'),
        if (question.marks != null) pw.Text('Marks: ${question.marks}'),
      ],
    ),
  );

  pw.Widget _answerOnly(int index, SubmissionAnswerRow answer) => pw.Paragraph(
    text:
        'Q${index + 1}: ${answer.questionId ?? ''}\nA: ${_answerValue(answer)}',
  );

  /// Port of `SubmissionsRepositoryExporter.formatAnswer`: each stored choice
  /// is the `{id, text}` object as a JSON string, so the label has to be
  /// decoded out of it — joining the entries verbatim printed raw JSON.
  ///
  /// `value` is read first and the choices only when it is empty, which is
  /// `formatAnswer`'s own order and the same precedence `Answer.createObject`
  /// uses for the upload. The two orders are not actually distinguishable
  /// here — a `select` row carries the same label in both halves, a
  /// `selectMultiple` row has an empty `value`, and a synced row has only one
  /// of the two — so this is one rule everywhere rather than a fix. The one
  /// place the precedence *is* observable is `SubmissionsRepository.serialize`,
  /// where the two branches produce different JSON types.
  String _answerValue(SubmissionAnswerRow answer) =>
      (answer.value?.isNotEmpty ?? false)
      ? answer.value!
      : answer.valueChoices.isNotEmpty
      ? ExamChoice.labelsFor(answer.valueChoices)
      : 'No answer';

  /// Recovers the raw question id from the `submissionId:rawQuestionId` row id.
  ///
  /// Strips the submission-id prefix rather than splitting at the first colon:
  /// the prefix has a known length, whereas a question id containing a colon
  /// would silently truncate under the split and stop matching its answer.
  String _rawQuestionId(SubmissionQuestionRow question) {
    final prefix = '${question.submissionId}:';
    return question.id.startsWith(prefix)
        ? question.id.substring(prefix.length)
        : question.id;
  }
}
