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
    repository = SubmissionsRepository(MockPlanetApi(), db.submissionDao);
    exporter = SubmissionsExporter(repository);
  });
  tearDown(() => db.close());

  test('generates a valid PDF containing submission questions', () async {
    await repository.upsertDocuments([
      {
        '_id': 'report-1',
        'userId': 'ada',
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

  test('returns null for an unknown submission', () async {
    expect(await exporter.generateBytes('missing'), isNull);
  });
}
