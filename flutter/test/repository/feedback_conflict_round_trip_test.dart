import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/feedback_repository_impl.dart';
import 'package:myplanet/repository/feedback_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// **Send, conflict, pull — driven together.**
///
/// The send-time reconcile and the pull's merge each had passing tests and
/// neither knew about the other, which is the shape `CLAUDE.md` calls "test
/// the pair, not the halves". Driven together they lost data twice, and both
/// losses were *created* by the reconcile: it made the server hold a thread
/// the local row does not, which is a state the port could not previously
/// reach.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  const opening = {'message': 'the projector', 'time': '1', 'user': 'ada'};
  const adminReply = {'message': 'a lamp is coming', 'time': '2', 'user': 'ad'};

  late AppDatabase db;
  late MockPlanetApi api;
  late FeedbackRepositoryImpl repository;
  late FeedbackUploader uploader;
  late OutboxRepository outbox;

  setUpAll(() => registerFallbackValue(<String, dynamic>{}));

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = FeedbackRepositoryImpl(
      feedbackDao: db.feedbackDao,
      planetApi: api,
    );
    outbox = OutboxRepository(db.outboxDao);
    uploader = FeedbackUploader(
      api,
      repository,
      db.feedbackDao,
      outbox,
      testDeviceIdentity,
    );
  });
  tearDown(() => db.close());

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

  /// A thread the server has, with a reply written on this handset that has
  /// not been sent, queued as it stood at that moment.
  Future<void> seedPendingReply() async {
    await db.feedbackDao.upsert(
      FeedbackEntriesCompanion.insert(
        id: 'fb1',
        rev: const Value('1-a'),
        owner: const Value('ada'),
        status: const Value('Open'),
        isUploaded: const Value(true),
        messages: Value(jsonEncode([opening])),
      ),
    );
    await repository.addReply('fb1', 'it is the lamp', 'ada');
    await uploader.queuePending(config: config, userId: 'ada');
  }

  List<Object?> messagesOf(String? json) => jsonDecode(json!) as List<Object?>;

  test('the reconciled thread is written back to the row', () async {
    // **Otherwise the next reply destroys the admin's reply on a clean 201.**
    // The reconcile puts `[opening, admin, ours]` on the server, but
    // `markUploaded` writes only `isUploaded` and `rev`, so the row still
    // reads `[opening, ours]` — and the screen reads the row. ada, who still
    // cannot see the answer, replies again; that reply is built on the local
    // array, goes out under a revision that now *matches*, and CouchDB takes
    // it with no 409 and nothing to reconcile against.
    await seedPendingReply();
    final queued =
        jsonDecode(
              (await db.outboxDao.forItem(
                FeedbackUploader.type,
                'fb1',
              )).single.payload,
            )
            as Map<String, dynamic>;

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
      return body['_rev'] == '2-b'
          ? NetworkSuccess<Map<String, dynamic>>({'rev': '3-c'})
          : const NetworkError<Map<String, dynamic>>(409, 'conflict');
    });
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        '_id': 'fb1',
        '_rev': '2-b',
        'messages': [opening, adminReply],
      }),
    );

    await uploader.handler(rowFor('fb1'), queued, 'auth');

    final row = await db.feedbackDao.getById('fb1');
    expect(row!.rev, '3-c');
    expect(
      messagesOf(row.messages).length,
      3,
      reason:
          'the row must hold what the reconcile actually sent, or the next '
          "reply is built on a thread missing the admin's answer",
    );
    expect((messagesOf(row.messages)[1] as Map)['message'], 'a lamp is coming');

    // And the next reply carries all four, so nothing is dropped when it goes
    // out under the matching revision.
    await repository.addReply('fb1', 'any news?', 'ada');
    await uploader.queuePending(config: config, userId: 'ada');
    final next =
        jsonDecode(
              (await db.outboxDao.forItem(
                FeedbackUploader.type,
                'fb1',
              )).single.payload,
            )
            as Map<String, dynamic>;
    expect((next['messages'] as List).length, 4);
  });

  test('a reply written during the send survives the write-back', () async {
    // Why the write-back merges instead of overwriting. The drain is
    // asynchronous and the reply box is not disabled while it runs, so a
    // reply can land between the POST and the row write. Writing the sent
    // array over the row would drop it — silently, and with the outbox row
    // already completed.
    await seedPendingReply();
    final queued =
        jsonDecode(
              (await db.outboxDao.forItem(
                FeedbackUploader.type,
                'fb1',
              )).single.payload,
            )
            as Map<String, dynamic>;

    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        '_id': 'fb1',
        '_rev': '2-b',
        'messages': [opening, adminReply],
      }),
    );
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((invocation) async {
      final body = invocation.positionalArguments[1] as Map<String, dynamic>;
      if (body['_rev'] != '2-b') {
        return const NetworkError<Map<String, dynamic>>(409, 'conflict');
      }
      // The user taps send while the drain is in flight.
      await repository.addReply('fb1', 'any news?', 'ada');
      return NetworkSuccess<Map<String, dynamic>>({'rev': '3-c'});
    });

    await uploader.handler(rowFor('fb1'), queued, 'auth');

    final texts = messagesOf(
      (await db.feedbackDao.getById('fb1'))!.messages,
    ).map((m) => (m as Map)['message']).toList();
    expect(
      texts,
      ['the projector', 'a lamp is coming', 'it is the lamp', 'any news?'],
      reason:
          'the admin reply the send delivered and the reply written during it '
          'must both be present',
    );
  });

  test(
    'a pull after a landed-but-unacknowledged send does not duplicate',
    () async {
      // The reconcile's own failure mode, and the one place this lane could be
      // **worse** than not shipping. The reconciled POST reaches CouchDB but its
      // response is lost, so nothing marks the row uploaded. The next pull then
      // merges the local `[opening, ours]` against the server's
      // `[opening, admin, ours]` — and a shared-prefix scan diverges at index 1,
      // so it appends our reply a second time. The re-queue after the pull then
      // publishes that duplicate under a revision that matches.
      await seedPendingReply();

      await repository.insertFromJson([
        {
          '_id': 'fb1',
          '_rev': '3-c',
          'owner': 'ada',
          'status': 'Open',
          'messages': [
            opening,
            adminReply,
            {...?_ours(await db.feedbackDao.getById('fb1'))},
          ],
        },
      ]);

      final row = await db.feedbackDao.getById('fb1');
      expect(
        messagesOf(row!.messages).length,
        3,
        reason:
            'our reply is already on the server; the merge must not add it '
            'a second time just because the admin answered before it',
      );
    },
  );
}

/// The reply `addReply` minted, read back so the fixture does not assert a
/// timestamp it does not control.
Map<String, dynamic>? _ours(FeedbackRow? row) => row == null
    ? null
    : (jsonDecode(row.messages!) as List).last as Map<String, dynamic>;
