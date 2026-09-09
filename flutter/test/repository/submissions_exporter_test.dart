import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/submissions_exporter.dart';
import 'package:myplanet/repository/submissions_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late AppDatabase db;
  late SubmissionsRepository repository;
  late SubmissionsExporter exporter;

  setUp(() {
    db = AppDatabase.memory();
    repository = SubmissionsRepository(
      MockPlanetApi(),
      db.submissionDao,
      db.submitPhotosDao,
      db.surveyDao,
      db.examDao,
      teamDao: db.teamDao,
    );
    exporter = SubmissionsExporter(repository);
  });
  tearDown(() => db.close());

  // The Status cell cannot be asserted through the PDF: content streams are
  // compressed, so `String.fromCharCodes(bytes).contains('Pending')` is false
  // however the cell is drawn — verified, not assumed. (The document's
  // *metadata* does survive as plain text, which is what the title test below
  // relies on and why that one can genuinely fail.) So the label is a
  // top-level function and this is what pins it, the same reasoning as Phase
  // 99's `reportExportDateSuffix`.
  group('submissionStatusLabel', () {
    SubmissionRow rowWith(String? status) => SubmissionRow(
      id: 'r',
      startTime: 0,
      lastUpdateTime: 0,
      grade: 0,
      uploaded: false,
      isUpdated: false,
      status: status,
    );

    test('shows the status the server sent', () {
      expect(submissionStatusLabel(rowWith('graded')), 'graded');
    });

    test('falls back for a null status, as it always did', () {
      expect(submissionStatusLabel(rowWith(null)), 'Pending');
    });

    test('falls back for the empty status the sync-in now stores', () {
      // Phase 151: `upsertDocuments` stores Kotlin's `''` for a document that
      // omits `status`, so `row.status ?? 'Pending'` stopped covering the case
      // it was written for. Both states mean the server told us nothing.
      expect(submissionStatusLabel(rowWith('')), 'Pending');
      expect(submissionStatusLabel(rowWith('   ')), 'Pending');
    });
  });

  test('generates a valid PDF containing submission questions', () async {
    await repository.upsertDocuments([
      {
        '_id': 'report-1',
        'user': {'_id': 'ada', 'name': 'Ada Lovelace'},
        'status': 'graded',
        'grade': 90,
        'answers': [
          {'questionId': 'q-1', 'value': 'Paris'},
        ],
        'parent': {
          'name': 'Geography exam',
          'questions': [
            {'id': 'q-1', 'title': 'Capital city', 'marks': '10'},
          ],
        },
      },
    ]);

    final bytes = await exporter.generateBytes('report-1');

    expect(bytes, isNotNull);
    expect(String.fromCharCodes(bytes!.take(5)), '%PDF-');
    expect(bytes.length, greaterThan(500));
  });

  // `parent` and `user` are JSON text, so drawing the columns raw put a
  // serialized object in the report. Kotlin draws `exam?.name` and
  // `getNormalizedSubmitterName`. The PDF is checked through the document's
  // metadata title, which is set from the same value as the header.
  test(
    'the report is titled with the exam name, not the parent JSON',
    () async {
      await repository.upsertDocuments([
        {
          '_id': 'report-2',
          'user': {'_id': 'ada', 'name': 'Ada Lovelace'},
          'parent': {'_id': 'exam-1', 'name': 'Geography exam'},
        },
      ]);
      final row = (await repository.getById('report-2'))!;

      expect(submissionDisplayTitle(row), 'Geography exam');
      expect(submissionSubmitterName(row), 'Ada Lovelace');

      final bytes = await exporter.generateBytes('report-2');
      final text = String.fromCharCodes(bytes!);
      expect(text.contains(r'{"_id":"exam-1"'), isFalse);
    },
  );

  test('writes reports under the supplied directory', () async {
    await repository.upsertDocuments([
      {'_id': 'report:unsafe', 'status': 'complete'},
    ]);
    final directory = await Directory.systemTemp.createTemp('submission-pdf');
    addTearDown(() => directory.delete(recursive: true));

    final file = await exporter.generateFile(
      'report:unsafe',
      directory: directory,
    );

    expect(await file!.exists(), isTrue);
    expect(file.path, contains('submission_report_unsafe_'));
  });

  test('resolves answers when the submission id contains a colon', () async {
    // A question row id is `submissionId:rawQuestionId`, so recovering the raw
    // id by splitting at the first colon truncates it here and the answer
    // lookup misses — every answer would export as "No answer".
    await repository.upsertDocuments([
      {
        '_id': 'exam:2026:q1',
        'userId': 'ada',
        'status': 'graded',
        'answers': [
          {'questionId': 'q-1', 'value': 'Reykjavik'},
        ],
        'parent': {
          'name': 'Geography exam',
          'questions': [
            {'id': 'q-1', 'title': 'Capital city', 'marks': '10'},
          ],
        },
      },
    ]);

    final lines = await exporter.renderedAnswerLines('exam:2026:q1');

    expect(lines, ['A: Reykjavik']);
  });

  test('returns null for an unknown submission', () async {
    expect(await exporter.generateBytes('missing'), isNull);
  });
}
