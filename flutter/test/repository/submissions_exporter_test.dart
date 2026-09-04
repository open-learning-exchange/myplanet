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
    );
    exporter = SubmissionsExporter(repository);
  });
  tearDown(() => db.close());

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
