import 'dart:io';

import 'package:drift/drift.dart' hide isNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/files/team_attachments.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/team_mapper.dart';
import 'package:myplanet/repository/teams_uploader.dart';
import 'package:path_provider/path_provider.dart';

import 'device_identity_fixture.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late TeamsUploader uploader;
  late Directory tmpRoot;

  setUp(() async {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    uploader = TeamsUploader(api, database.teamDao, testDeviceIdentity);
    registerFallbackValue(<String, dynamic>{});

    // Route `TeamAttachments` at a temp dir so the write-back can read the
    // bytes back without a platform channel. The class's own
    // `baseDirectory` override is the seam tests are expected to use.
    tmpRoot = await Directory.systemTemp.createTemp('team_attachments_test');
    TeamAttachments.baseDirectory = () async => tmpRoot;
  });
  tearDown(() async {
    TeamAttachments.baseDirectory = getApplicationDocumentsDirectory;
    await database.close();
    if (await tmpRoot.exists()) await tmpRoot.delete(recursive: true);
  });

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
    late Map<String, dynamic> posted;
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((invocation) async {
      posted = invocation.positionalArguments[1] as Map<String, dynamic>;
      return NetworkSuccess<Map<String, dynamic>>({
        'id': 'report-1',
        'rev': '2-b',
      });
    });

    final result = await uploader.handler(rowFor('report-1'), {}, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    for (final field in testDeviceFields.entries) {
      expect(posted, containsPair(field.key, field.value));
    }
    final row = await database.teamDao.getById('report-1');
    expect(row?.rev, '2-b');
    // Still flagged, the row would outrank every future server refresh and
    // stay exempt from stale-row cleanup for good.
    expect(row?.isUpdated, isFalse);
  });

  test('a response without a revision is not treated as uploaded', () async {
    await seedLocalReport();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    final result = await uploader.handler(rowFor('report-1'), {}, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect((await database.teamDao.getById('report-1'))?.isUpdated, isTrue);
  });

  test('a failed upload leaves the row local and retryable', () async {
    await seedLocalReport();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(500, 'nope'),
    );

    await uploader.handler(rowFor('report-1'), {}, 'auth');

    expect((await database.teamDao.getById('report-1'))?.isUpdated, isTrue);
  });

  test('a delete tombstone succeeds with no local row to stamp', () async {
    // `leave` removes the membership before the drain runs, so demanding a
    // revision here would fail a delete that CouchDB already accepted.
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    final result = await uploader.handler(rowFor('membership-1'), {
      '_id': 'membership-1',
      '_rev': '1-a',
      '_deleted': true,
    }, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
  });

  test('an uploaded row becomes evictable and takes server updates', () async {
    await seedLocalReport();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rev': '2-b'}),
    );
    await uploader.handler(rowFor('report-1'), {}, 'auth');

    // Both behaviours are gated on `isUpdated`, so this is the round trip that
    // proves the flag is not one-way.
    final refreshed = TeamMapper.fromDoc({
      '_id': 'report-1',
      'docType': 'report',
      'description': 'Server copy',
    }, existing: await database.teamDao.getById('report-1'));
    await database.teamDao.upsertAll([refreshed!]);
    expect(
      (await database.teamDao.getById('report-1'))?.description,
      'Server copy',
    );
    expect(await database.teamDao.deleteNotIn(const []), 1);
  });

  test('a document with an attachment PUTs its bytes after upload', () async {
    // Port of `UploadManager.uploadTeamImageAttachment`: once the document is
    // acknowledged, the receipt image is written to the named attachment slot.
    // The bytes are read back from `team_attachments/<id>/<name>`, so the
    // write-back is what proves the repository persisted them.
    await database.teamDao.upsert(
      TeamsCompanion.insert(
        id: 'tx-1',
        teamId: const Value('team-1'),
        docType: const Value('transaction'),
        description: const Value('sale'),
        amount: const Value(100),
        imageName: const Value('receipt.jpg'),
        isUpdated: const Value(true),
      ),
    );
    await TeamAttachments.write(
      docId: 'tx-1',
      filename: 'receipt.jpg',
      bytes: [1, 2, 3, 4],
    );

    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rev': '2-b'}),
    );
    when(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    final result = await uploader.handler(rowFor('tx-1'), {}, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    final captured = verify(
      () => api.uploadAttachment(
        captureAny(),
        bytes: captureAny(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: captureAny(named: 'contentType'),
        ifMatch: captureAny(named: 'ifMatch'),
      ),
    ).captured;
    expect(captured[0], contains('/teams/tx-1/receipt.jpg'));
    expect(captured[1], [1, 2, 3, 4]);
    expect(captured[2], 'image/jpeg');
    expect(captured[3], '2-b');
  });

  test('a document without an attachment skips the attachment PUT', () async {
    await seedLocalReport();

    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rev': '2-b'}),
    );
    when(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    await uploader.handler(rowFor('report-1'), {}, 'auth');

    verifyNever(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    );
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
