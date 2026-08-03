import 'package:drift/drift.dart' hide isNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/team_mapper.dart';
import 'package:myplanet/repository/teams_uploader.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late TeamsUploader uploader;

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    uploader = TeamsUploader(api, database.teamDao);
    registerFallbackValue(<String, dynamic>{});
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: TeamsUploader.membershipType,
    itemId: itemId,
    payload: '{}',
    endpoint: 'https://planet.example/db/teams',
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  Future<void> seedLocalReport() => database.teamDao.upsert(
    TeamsCompanion.insert(
      id: 'report-1',
      teamId: const Value('team-1'),
      docType: const Value('report'),
      description: const Value('Q1'),
      isUpdated: const Value(true),
    ),
  );

  test('a successful upload hands the row back to the server', () async {
    await seedLocalReport();
    when(
      () => api.postJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': 'report-1',
        'rev': '2-b',
      }),
    );

    final result = await uploader.handler(rowFor('report-1'), {}, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    final row = await database.teamDao.getById('report-1');
    expect(row?.rev, '2-b');
    // Still flagged, the row would outrank every future server refresh and
    // stay exempt from stale-row cleanup for good.
    expect(row?.isUpdated, isFalse);
  });

  test('a response without a revision is not treated as uploaded', () async {
    await seedLocalReport();
    when(
      () => api.postJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}));

    final result = await uploader.handler(rowFor('report-1'), {}, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect((await database.teamDao.getById('report-1'))?.isUpdated, isTrue);
  });

  test('a failed upload leaves the row local and retryable', () async {
    await seedLocalReport();
    when(
      () => api.postJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((_) async => const NetworkError<Map<String, dynamic>>(500, 'nope'));

    await uploader.handler(rowFor('report-1'), {}, 'auth');

    expect((await database.teamDao.getById('report-1'))?.isUpdated, isTrue);
  });

  test('a delete tombstone succeeds with no local row to stamp', () async {
    // `leave` removes the membership before the drain runs, so demanding a
    // revision here would fail a delete that CouchDB already accepted.
    when(
      () => api.postJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}));

    final result = await uploader.handler(
      rowFor('membership-1'),
      {'_id': 'membership-1', '_rev': '1-a', '_deleted': true},
      'auth',
    );

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
  });

  test('an uploaded row becomes evictable and takes server updates', () async {
    await seedLocalReport();
    when(
      () => api.postJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rev': '2-b'}),
    );
    await uploader.handler(rowFor('report-1'), {}, 'auth');

    // Both behaviours are gated on `isUpdated`, so this is the round trip that
    // proves the flag is not one-way.
    final refreshed = TeamMapper.fromDoc(
      {'_id': 'report-1', 'docType': 'report', 'description': 'Server copy'},
      existing: await database.teamDao.getById('report-1'),
    );
    await database.teamDao.upsertAll([refreshed!]);
    expect(
      (await database.teamDao.getById('report-1'))?.description,
      'Server copy',
    );
    expect(await database.teamDao.deleteNotIn(const []), 1);
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
