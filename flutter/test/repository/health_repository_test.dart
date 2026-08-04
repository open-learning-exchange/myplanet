import 'package:drift/drift.dart' hide isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/health_repository.dart';

void main() {
  late AppDatabase database;
  late HealthRepository repository;
  late MockPlanetApi api;

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    repository = HealthRepository(
      api,
      database.healthExaminationDao,
      createId: () => 'health-local-1',
    );
  });

  tearDown(() => database.close());

  test('creates and retrieves a health examination', () async {
    final id = await repository.createExamination(
      userId: 'user-1',
      temperature: 36.5,
      pulse: 72,
      bp: '120/80',
      height: 175,
      weight: 70,
      vision: '20/20',
      hearing: 'Normal',
    );

    expect(id, 'health-local-1');

    final row = await repository.getById(id);
    expect(row, isNotNull);
    expect(row!.temperature, 36.5);
    expect(row.pulse, 72);
    expect(row.bp, '120/80');
    expect(row.height, 175);
    expect(row.weight, 70);
    expect(row.isUpdated, isTrue);
  });

  test('updates an existing examination', () async {
    final id = await repository.createExamination(
      userId: 'user-1',
      temperature: 36.5,
      pulse: 72,
      height: 175,
      weight: 70,
    );

    await repository.updateExamination(id, temperature: 37.0, pulse: 80);

    final row = await repository.getById(id);
    expect(row!.temperature, 37.0);
    expect(row.pulse, 80);
    expect(row.isUpdated, isTrue);
  });

  test('parses conditions from JSON', () {
    expect(repository.parseConditions('{"Fever": true, "Cough": false}'), {
      'Fever': true,
      'Cough': false,
    });

    expect(repository.parseConditions(null), isEmpty);
    expect(repository.parseConditions(''), isEmpty);
    expect(repository.parseConditions('invalid'), isEmpty);
  });

  test('converts row to model', () async {
    final companion = HealthExaminationsCompanion.insert(
      id: 'health-1',
      userId: const Value('user-1'),
      temperature: const Value(36.5),
      pulse: const Value(72),
    );

    // Insert into database and get back
    await database.healthExaminationDao.upsert(companion);
    final row = await database.healthExaminationDao.getById('health-1');

    expect(row, isNotNull);
    expect(row!.temperature, 36.5);

    // Test rowToModel conversion
    final model = HealthRepository.rowToModel(row);
    expect(model.temperature, 36.5);
    expect(model.pulse, 72);
  });

  test('maps and caches server health documents', () async {
    expect(
      await repository.cacheDocuments([
        {
          '_id': 'health-1',
          '_rev': '1-a',
          'temperature': 36.6,
          'pulse': 75,
          'bp': '118/78',
          'height': 180.0,
          'weight': 80.0,
        },
      ]),
      1,
    );

    final row = await repository.getById('health-1');
    expect(row?.temperature, 36.6);
    expect(row?.pulse, 75);
    expect(row?.bp, '118/78');
    expect(row?.rev, '1-a');
    expect(row?.isUpdated, isFalse);
  });

  test('preserves local edits during cache', () async {
    await repository.cacheDocuments([
      {'_id': 'health-1', 'temperature': 36.5, 'pulse': 70},
    ]);

    // Create a local edit
    await repository.updateExamination(
      'health-1',
      temperature: 37.0,
      pulse: 80,
    );

    // Server sends an update
    await repository.cacheDocuments([
      {'_id': 'health-1', 'temperature': 36.0, 'pulse': 60},
    ]);

    // Local edit should be preserved
    final row = await repository.getById('health-1');
    expect(row?.temperature, 37.0);
    expect(row?.pulse, 80);
    expect(row?.isUpdated, isTrue);
  });

  test('serializes examination for upload', () async {
    await repository.cacheDocuments([
      {
        '_id': 'health-1',
        '_rev': '1-a',
        'temperature': 36.5,
        'pulse': 72,
        'bp': '120/80',
        'height': 175.0,
        'weight': 70.0,
      },
    ]);

    final row = await repository.getById('health-1');
    final payload = HealthRepository.serialize(row!);

    expect(payload['_id'], 'health-1');
    expect(payload['_rev'], '1-a');
    expect(payload['temperature'], 36.5);
    expect(payload['pulse'], 72);
    expect(payload['bp'], '120/80');
    expect(payload['height'], 175.0);
    expect(payload['weight'], 70.0);
  });

  test('gets examinations by user id', () async {
    // Create examinations directly via repository
    await repository.createExamination(
      userId: 'user-1',
      temperature: 36.5,
      pulse: 72,
      height: 175,
      weight: 70,
    );
    await repository.createExamination(
      userId: 'user-1',
      temperature: 36.6,
      pulse: 72,
      height: 175,
      weight: 70,
    );
    await repository.createExamination(
      userId: 'user-2',
      temperature: 36.7,
      pulse: 75,
      height: 180,
      weight: 80,
    );

    final rows = await repository.getForUser('user-1');
    expect(rows.length, 2);
    expect(rows.every((r) => r.userId == 'user-1'), isTrue);
  });

  test('gets updated examinations for sync', () async {
    await repository.cacheDocuments([
      {'_id': 'health-1', 'temperature': 36.5},
    ]);

    // Create a local edit
    await repository.updateExamination('health-1', temperature: 37.0);

    final updated = await repository.getUpdated();
    expect(updated.length, 1);
    expect(updated.first.temperature, 37.0);
  });

  test('marks examination as uploaded', () async {
    await repository.cacheDocuments([
      {'_id': 'health-1', 'temperature': 36.5},
    ]);

    await repository.updateExamination('health-1', temperature: 37.0);

    await repository.markUploaded('health-1', '2-b');

    final row = await repository.getById('health-1');
    expect(row!.isUpdated, isFalse);
    expect(row.rev, '2-b');
  });

  test('sync walks CouchDB pages', () async {
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments.single as String;
      if (url.endsWith('limit=0')) {
        return NetworkSuccess<Map<String, dynamic>>({'total_rows': 2});
      }
      return NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'doc': {'_id': 'health-1', 'temperature': 36.5},
          },
          {
            'doc': {'_id': 'health-2', 'temperature': 36.6},
          },
        ],
      });
    });

    final result = await repository.sync();

    expect(result, isA<SyncComplete>());
    expect((result as SyncComplete).savedCount, 2);
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
