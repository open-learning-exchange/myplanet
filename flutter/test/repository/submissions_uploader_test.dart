import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/submissions_uploader.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late SubmissionsRepository repository;
  late OutboxRepository outbox;
  late SubmissionsUploader uploader;
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = SubmissionsRepository(
      api,
      db.submissionDao,
      db.submitPhotosDao,
      db.surveyDao,
      db.examDao,
      teamDao: db.teamDao,
    );
    outbox = OutboxRepository(db.outboxDao);
    uploader = SubmissionsUploader(api, repository, outbox, testDeviceIdentity);
  });
  tearDown(() => db.close());

  test(
    'an edited answer sheet conflicting on its revision is re-sent',
    () async {
      // `pendingUploads` is `isUpdated = true`, so a sheet edited after its
      // first upload is re-offered carrying `_id` and `_rev`
      // (`submissions_repository.dart:1263-1264`). Adopting the server's
      // revision would set `uploaded: true, isUpdated: false` with the answers
      // the learner added still on the handset.
      final id = await repository.createDraft(
        userId: 'user-1',
        type: 'exam',
        title: 'Draft',
        answers: const [],
      );
      await repository.markUploaded(id, 'sub-couch', '1-stale');
      // The edit that re-offers it.
      await (db.update(db.submissions)..where((r) => r.id.equals(id))).write(
        const SubmissionsCompanion(isUpdated: Value(true)),
      );

      expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
      final operation = (await outbox.due()).single;
      final payload = jsonDecode(operation.payload) as Map<String, dynamic>;
      expect(payload['_id'], 'sub-couch');
      expect(payload['_rev'], '1-stale');

      final sent = <Map<String, dynamic>>[];
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((invocation) async {
        final body = Map<String, dynamic>.from(
          invocation.positionalArguments[1] as Map<String, dynamic>,
        );
        sent.add(body);
        return body['_rev'] == '4-server'
            ? NetworkSuccess<Map<String, dynamic>>({
                'id': 'sub-couch',
                'rev': '5-j',
              })
            : const NetworkError<Map<String, dynamic>>(409, 'conflict');
      });
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          '_id': 'sub-couch',
          '_rev': '4-server',
        }),
      );

      final result = await uploader.handler(operation, payload, 'auth');

      expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
      expect(sent, hasLength(2));
      expect(sent[1]['_rev'], '4-server');
      final stored = await db.submissionDao.getById(id);
      expect(stored?.rev, '5-j');
      expect(stored?.isUpdated, isFalse);
    },
  );

  test('queues a draft once and adopts CouchDB ids after upload', () async {
    final id = await repository.createDraft(
      userId: 'user-1',
      type: 'exam',
      title: 'Draft',
      answers: const [],
    );
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    final operation = (await outbox.due()).single;
    final queuedDoc = jsonDecode(operation.payload) as Map<String, dynamic>;
    for (final field in testDeviceFields.entries) {
      expect(queuedDoc, containsPair(field.key, field.value));
    }
    when(
      () => api.postJsonObject(
        operation.endpoint,
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': 'server-id',
        'rev': '1-rev',
      }),
    );

    final result = await uploader.handler(operation, const {}, 'Basic test');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    expect(await repository.pendingUploads(), isEmpty);
    expect((await repository.getById(id))?.couchId, 'server-id');
  });

  /// **The shared handset, end to end.** `SubmissionDao.pendingUploads` is
  /// pinned by its own tests, but until this one nothing drove `queuePending`
  /// with more than one owner — so a Dart-side `.where((row) => row.userId ==
  /// userId)` here would have re-scoped the uploader with the whole suite
  /// green, reinstating exactly the defect the DAO change fixed. The
  /// `userId` parameter is the outbox row's session tag, not a filter.
  test('queues every owner on the handset, not just the session', () async {
    await repository.createDraft(
      userId: 'org.couchdb.user:ada',
      type: 'survey',
      title: "Ada's sheet",
      answers: const [],
    );
    await repository.createDraft(
      userId: 'org.couchdb.user:bob',
      type: 'survey',
      title: "Bob's sheet",
      answers: const [],
    );

    // Bob is the one signed in and syncing; Ada answered and signed out.
    final queued = await uploader.queuePending(
      config: config,
      userId: 'org.couchdb.user:bob',
    );

    expect(queued, 2);
    final owners = [
      for (final row in await outbox.due())
        ((jsonDecode(row.payload) as Map<String, dynamic>)['user']
                as Map<String, dynamic>)['_id']
            as String,
    ];
    expect(
      owners,
      containsAll(<String>['org.couchdb.user:ada', 'org.couchdb.user:bob']),
      reason:
          "each document is attributed to its own owner; only the outbox row's "
          'session tag is the signed-in user',
    );
  });

  test('an already-uploaded submission is updated, not duplicated', () async {
    // A submission the server already holds, marked dirty again — which is
    // exactly what `upsertDocuments` produces when a synced document carries
    // `isUpdated: true`.
    await db.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 'local-1',
        couchId: const Value('srv-1'),
        rev: const Value('3-abc'),
        userId: const Value('user-1'),
        type: const Value('survey'),
        uploaded: const Value(true),
        isUpdated: const Value(true),
      ),
    ]);

    final payload = await repository.serialize(
      (await db.submissionDao.pendingUploads()).single,
    );

    // Kotlin's serializeSubmission adds both when present; CouchDB needs them
    // to treat the POST as an update rather than minting a second document.
    expect(payload['_id'], 'srv-1');
    expect(payload['_rev'], '3-abc');
  });

  test('a never-uploaded draft carries no _id or _rev', () async {
    final id = await repository.createDraft(
      userId: 'user-1',
      type: 'exam',
      title: 'Fresh',
      answers: const [],
    );

    final row = await db.submissionDao.getById(id);
    final payload = await repository.serialize(row!);

    expect(payload.containsKey('_id'), isFalse);
    expect(
      payload.containsKey('_rev'),
      isFalse,
      reason: 'sending a null _rev would make CouchDB reject the insert',
    );
  });
}
