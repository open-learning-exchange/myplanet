import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/feedback_mapper.dart';
import 'package:myplanet/repository/feedback_repository_impl.dart';
import 'package:myplanet/repository/feedback_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';

import 'device_identity_fixture.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late FeedbackUploader uploader;
  late OutboxRepository outbox;
  late FeedbackRepositoryImpl repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    registerFallbackValue(<String, dynamic>{});
    outbox = OutboxRepository(database.outboxDao);
    repository = FeedbackRepositoryImpl(
      feedbackDao: database.feedbackDao,
      planetApi: api,
    );
    uploader = FeedbackUploader(
      api,
      repository,
      database.feedbackDao,
      outbox,
      testDeviceIdentity,
    );
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: FeedbackUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: FeedbackUploader.endpointFor(config),
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  Future<void> seedPending() => database.feedbackDao.upsert(
    FeedbackEntriesCompanion.insert(
      id: 'feedback-1',
      title: const Value('Sync fails offline'),
      owner: const Value('ada'),
      isUploaded: const Value(false),
    ),
  );

  test('the endpoint carries no credentials', () {
    // It is persisted in `outbox`, which survives schema upgrades; the PIN
    // travels as a header at send time instead.
    final endpoint = FeedbackUploader.endpointFor(config);
    expect(endpoint, isNot(contains('satellite')));
    expect(endpoint, isNot(contains('1234')));
    expect(endpoint, endsWith('/feedback'));
  });

  test('queues only feedback that has not reached the server', () async {
    await seedPending();
    await database.feedbackDao.upsert(
      FeedbackEntriesCompanion.insert(
        id: 'feedback-2',
        owner: const Value('ada'),
        isUploaded: const Value(true),
      ),
    );

    expect(await uploader.queuePending(config: config, userId: 'ada'), 1);
    // `enqueue` stamps `nextAttemptAt` with the wall clock, so the due
    // horizon has to be past it.
    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.map((row) => row.itemId), ['feedback-1']);
  });

  test(
    'a conflicting feedback document is re-sent under the server revision',
    () async {
      // `FeedbackMapper.toDoc` sends a device-generated `_id` deliberately, so a
      // reply to an already-uploaded thread is an update rather than a duplicate
      // — and an update against a stale `_rev` is a 409.
      await seedPending();
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
        return body['_rev'] == '3-server'
            ? NetworkSuccess<Map<String, dynamic>>({'rev': '4-d'})
            : const NetworkError<Map<String, dynamic>>(409, 'conflict');
      });
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          '_id': 'feedback-1',
          '_rev': '3-server',
        }),
      );

      final result = await uploader.handler(rowFor('feedback-1'), const {
        '_id': 'feedback-1',
        '_rev': '2-stale',
        'title': 'Sync fails offline',
      }, 'auth');

      expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
      expect(sent, hasLength(2));
      expect(sent[1]['title'], 'Sync fails offline');
      expect(sent[1]['_rev'], '3-server');
      expect((await database.feedbackDao.getById('feedback-1'))?.rev, '4-d');
    },
  );

  test(
    'a re-send drops a reply only the server has — pre-existing, not new',
    () async {
      // The trade the update arm makes, demonstrated rather than argued: the
      // re-send carries the payload's own content, so anything the server has
      // and the payload does not is overwritten.
      //
      // `feedback` is the one armed uploader where that is a *merge* loss rather
      // than a revision bump — `messages` is an append array both Planet's web
      // UI and the handset write. **And the loss is pre-existing.** The second
      // half of this test is the proof: `FeedbackMapper.fromDoc` keeps the local
      // array on a pending reply while taking the server's `rev`
      // (`feedback_mapper.dart:27-31, 50-54`) and leaves `isUploaded = false`,
      // so the next sweep's payload differs from the refused one, Phase 148's
      // memo re-arms it, and the admin reply goes over the side one sync later
      // with no recovery arm involved at all.
      await seedPending();
      await (database.update(
        database.feedbackEntries,
      )..where((f) => f.id.equals('feedback-1'))).write(
        FeedbackEntriesCompanion(
          rev: const Value('1-local'),
          messages: Value(
            jsonEncode([
              {'message': 'mine'},
            ]),
          ),
        ),
      );

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
        return body['_rev'] == '2-admin'
            ? NetworkSuccess<Map<String, dynamic>>({'rev': '3-x'})
            : const NetworkError<Map<String, dynamic>>(409, 'conflict');
      });
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          '_id': 'feedback-1',
          '_rev': '2-admin',
          // The admin's reply, which this device has never seen.
          'messages': [
            {'message': 'mine'},
            {'message': 'from the admin'},
          ],
        }),
      );

      await uploader.handler(rowFor('feedback-1'), {
        '_id': 'feedback-1',
        '_rev': '1-local',
        'messages': [
          {'message': 'mine'},
        ],
      }, 'auth');

      // The re-send carries only this device's array. This is the documented
      // trade, asserted so a future reader sees it rather than reading three
      // paragraphs about it.
      expect(sent, hasLength(2));
      expect(sent[1]['messages'], [
        {'message': 'mine'},
      ]);
    },
  );

  test(
    'a pull already loses that reply, with no recovery arm involved',
    () async {
      // The proof that the loss above is **pre-existing**, and the reason it is
      // reported rather than treated as something this phase introduced.
      //
      // `FeedbackMapper.fromDoc` keeps the local `messages` on a pending reply
      // while taking the server's `rev` (`feedback_mapper.dart:27-31, 50-54`)
      // and leaves `isUploaded = false`. So the admin's reply is gone locally
      // the moment the pull lands, the row is still swept, and its payload now
      // carries a revision the refused one did not — which is exactly what
      // Phase 148's memo treats as a changed request. The next sweep sends the
      // local array over the server's with no 409 and no arm in sight.
      await seedPending();
      await (database.update(
        database.feedbackEntries,
      )..where((f) => f.id.equals('feedback-1'))).write(
        FeedbackEntriesCompanion(
          rev: const Value('1-local'),
          messages: Value(
            jsonEncode([
              {'message': 'mine'},
            ]),
          ),
        ),
      );

      await repository.insertFromJson([
        {
          '_id': 'feedback-1',
          '_rev': '9-admin',
          'messages': [
            {'message': 'mine'},
            {'message': 'from the admin'},
          ],
        },
      ]);

      final pulled = await database.feedbackDao.getById('feedback-1');
      expect(pulled!.rev, '9-admin', reason: "the server's revision is taken");
      expect(pulled.isUploaded, isFalse, reason: 'so the row is still swept');
      expect(
        pulled.messages,
        isNot(contains('from the admin')),
        reason: 'the local array is kept, so the reply is already lost locally',
      );
    },
  );

  test('a successful upload records the revision', () async {
    await seedPending();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rev': '1-a'}),
    );

    await uploader.handler(rowFor('feedback-1'), {}, 'auth');

    final row = await database.feedbackDao.getById('feedback-1');
    expect(row?.isUploaded, isTrue);
    // Without the revision, adding a reply later PUTs against a missing `_rev`
    // and CouchDB rejects it as a conflict.
    expect(row?.rev, '1-a');
  });

  test('a response without a revision is not treated as uploaded', () async {
    await seedPending();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    final result = await uploader.handler(rowFor('feedback-1'), {}, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect(
      (await database.feedbackDao.getById('feedback-1'))?.isUploaded,
      isFalse,
    );
  });

  test('a reply re-queues the feedback as an update, not a copy', () async {
    await seedPending();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rev': '1-a'}),
    );
    await uploader.handler(rowFor('feedback-1'), {}, 'auth');

    final repository = FeedbackRepositoryImpl(
      feedbackDao: database.feedbackDao,
      planetApi: api,
    );
    await repository.addReply('feedback-1', 'Still broken', 'ada');

    expect(await uploader.queuePending(config: config, userId: 'ada'), 1);
    final payload = FeedbackMapper.toDoc(
      (await database.feedbackDao.getById('feedback-1'))!,
    );
    // Both keys matter: without `_id` the upload creates a second thread, and
    // without `_rev` it is rejected as a conflict.
    expect(payload['_id'], 'feedback-1');
    expect(payload['_rev'], '1-a');
  });

  test('the queued feedback is stamped with its origin', () async {
    // `Feedback.serializeFeedback` gained `addDocumentOrigin()` in `27c0470`
    // — a new-stamp site, so both `androidId` and `app` are new on the wire
    // and no device name accompanies them.
    await seedPending();

    await uploader.queuePending(config: config, userId: 'ada');
    final entry = await database.outboxDao.findOpen(
      FeedbackUploader.type,
      'feedback-1',
    );

    final doc = jsonDecode(entry!.payload) as Map<String, dynamic>;
    expect(doc['androidId'], 'android-id_build-id');
    expect(doc['app'], 'myplanet');
    expect(doc.containsKey('deviceName'), isFalse);
    expect(doc.containsKey('customDeviceName'), isFalse);
  });

  test('stale cleanup spares feedback that never uploaded', () async {
    await seedPending();
    await database.feedbackDao.upsert(
      FeedbackEntriesCompanion.insert(
        id: 'server-gone',
        owner: const Value('ada'),
        isUploaded: const Value(true),
      ),
    );

    expect(await database.feedbackDao.deleteNotIn(const []), 1);
    expect(await database.feedbackDao.getById('feedback-1'), isNotNull);
    expect(await database.feedbackDao.getById('server-gone'), equals(null));
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
