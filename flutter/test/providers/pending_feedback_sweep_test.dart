import 'dart:convert';
import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/background_entrypoint.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/repository/feedback_repository_impl.dart';
import 'package:myplanet/repository/feedback_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';

import '../repository/device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

class _ThrowingUploader extends Mock implements FeedbackUploader {}

/// The headless half of the feedback re-queue.
///
/// Phase 156 made a pull *merge* the server's thread with the replies this
/// device has not sent (`FeedbackMapper._mergePendingReplies`). But
/// `outbox.payload` is a snapshot taken at queue time, so the merge only
/// survives if something re-queues afterwards.
/// `FeedbackSyncNotifier.runSync` does — `feedback_sync_requeue_test.dart`
/// drives that one. The headless path did not, and these drive the fix.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  /// The thread as the server had it when this device last uploaded.
  const openingMessage = {
    'message': 'the projector will not start',
    'time': '100',
    'user': 'ada',
  };

  /// Written through Planet's web UI while the handset was offline. This is
  /// the message the stale snapshot destroys.
  const adminReply = {
    'message': 'a replacement lamp is on its way',
    'time': '200',
    'user': 'admin',
  };

  late AppDatabase db;
  late MockPlanetApi api;
  late FeedbackRepositoryImpl repository;
  late FeedbackUploader uploader;
  late OutboxRepository outbox;

  /// Written on the handset while it was offline, still in the outbox.
  /// `addReply` stamps its own `DateTime.now()`, so the fixture reads the
  /// reply back rather than asserting a time it does not control.
  late Map<String, dynamic> handsetReply;

  setUpAll(() {
    registerFallbackValue(config);
    registerFallbackValue(<String, dynamic>{});
  });

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

  /// The reported state: a thread the server already has, a reply written on
  /// this handset that has not been sent, and an outbox row holding the
  /// payload **as it was when that reply was written**.
  Future<void> seedPendingReply() async {
    await db.feedbackDao.upsert(
      FeedbackEntriesCompanion.insert(
        id: 'fb1',
        rev: const Value('1-a'),
        title: const Value('Question regarding /'),
        status: const Value('Open'),
        owner: const Value('ada'),
        openTime: const Value(100),
        isUploaded: const Value(true),
        messages: Value(jsonEncode([openingMessage])),
      ),
    );
    await repository.addReply('fb1', 'it is the lamp', 'ada');
    final written = await db.feedbackDao.getById('fb1');
    handsetReply =
        (jsonDecode(written!.messages!) as List).last as Map<String, dynamic>;
    await uploader.queuePending(config: config, userId: 'ada');
  }

  /// The server's copy once the admin has answered: a new revision, and the
  /// admin's reply where the handset's has not arrived.
  void stubServerWithAdminReply() {
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments[0] as String;
      if (url.contains('limit=0')) {
        return const NetworkSuccess<Map<String, dynamic>>({'total_rows': 1});
      }
      return const NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'doc': {
              '_id': 'fb1',
              '_rev': '2-b',
              'title': 'Question regarding /',
              'status': 'Open',
              'owner': 'ada',
              'openTime': 100,
              'messages': [openingMessage, adminReply],
            },
          },
        ],
      });
    });
  }

  ProviderContainer containerFor() {
    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        feedbackRepositoryProvider.overrideWithValue(repository),
        feedbackUploaderProvider.overrideWithValue(uploader),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  Future<List<Object?>> queuedMessages() async {
    final rows = await db.outboxDao.forItem(FeedbackUploader.type, 'fb1');
    expect(rows, hasLength(1), reason: 'one outbox row per item, for ever');
    final payload = jsonDecode(rows.single.payload) as Map<String, dynamic>;
    return payload['messages'] as List<Object?>;
  }

  test('the queued payload carries the admin reply the pull merged', () async {
    await seedPendingReply();
    // Precondition, and it is what tells this test's two outcomes apart: the
    // snapshot the outbox is holding does **not** know about the admin's
    // reply, because it was taken before the pull that learns of it.
    expect(await queuedMessages(), [openingMessage, handsetReply]);

    stubServerWithAdminReply();
    final result = await repository.sync(config: config);
    expect(result, isA<SyncComplete>());

    // The merge ran. Asserted separately so a failure below localises to the
    // re-queue rather than to `_mergePendingReplies`.
    final stored = await db.feedbackDao.getById('fb1');
    expect(
      jsonDecode(stored!.messages!),
      [openingMessage, adminReply, handsetReply],
      reason: 'the pull should have merged, not replaced',
    );
    expect(stored.isUploaded, isFalse, reason: 'the reply is still unsent');

    await sweepPendingFeedback(
      containerFor(),
      config: config,
      userId: 'ada',
      result: result,
    );

    // Without the sweep this is still `[opening, handset]` — the array that
    // drains over the server's document under a revision `ConflictRecovery`
    // has just re-read, destroying the admin's reply for every device.
    expect(await queuedMessages(), [openingMessage, adminReply, handsetReply]);
  });

  test('the refreshed payload carries the revision the pull brought', () async {
    await seedPendingReply();
    final before = jsonDecode(
      (await db.outboxDao.forItem(FeedbackUploader.type, 'fb1')).single.payload,
    );
    expect((before as Map<String, dynamic>)['_rev'], '1-a');

    stubServerWithAdminReply();
    final result = await repository.sync(config: config);
    await sweepPendingFeedback(
      containerFor(),
      config: config,
      userId: 'ada',
      result: result,
    );

    final after = jsonDecode(
      (await db.outboxDao.forItem(FeedbackUploader.type, 'fb1')).single.payload,
    );
    // Secondary, and free: the refreshed snapshot no longer conflicts, so the
    // send skips `ConflictRecovery`'s fetch-and-retry round trip entirely.
    expect((after as Map<String, dynamic>)['_rev'], '2-b');
  });

  test('a partial walk leaves the queued payload alone', () async {
    await seedPendingReply();

    // A walk that merged one page and then lost the server. The merge has
    // already landed in the row, so the row and the snapshot now *differ* —
    // which is what makes this a test rather than a fixture that cannot tell
    // the gate's two outcomes apart. Stubbing a plain `SyncFailed` without
    // running the walk leaves the row identical to the snapshot, and a
    // re-queue from it writes the same bytes: green with the gate and green
    // without it.
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments[0] as String;
      if (url.contains('limit=0')) {
        // Two pages' worth, so the walk comes back for a second one.
        return const NetworkSuccess<Map<String, dynamic>>({'total_rows': 200});
      }
      if (url.contains('skip=0')) {
        return const NetworkSuccess<Map<String, dynamic>>({
          'rows': [
            {
              'doc': {
                '_id': 'fb1',
                '_rev': '2-b',
                'title': 'Question regarding /',
                'status': 'Open',
                'owner': 'ada',
                'openTime': 100,
                'messages': [openingMessage, adminReply],
              },
            },
          ],
        });
      }
      return const NetworkError<Map<String, dynamic>>(503, 'server went away');
    });

    final result = await repository.sync(config: config);
    expect(result, isA<SyncFailed>());
    // The merge really did land, so the two outcomes are distinguishable.
    expect(jsonDecode((await db.feedbackDao.getById('fb1'))!.messages!), [
      openingMessage,
      adminReply,
      handsetReply,
    ]);

    // Half a walk has merged only some threads; a payload assembled from a
    // state the server never finished describing is worse than a stale one,
    // and the next completed pull re-queues it anyway.
    await sweepPendingFeedback(
      containerFor(),
      config: config,
      userId: 'ada',
      result: result,
    );

    expect(await queuedMessages(), [openingMessage, handsetReply]);
  });

  test('a throwing queue call does not fail the step', () async {
    // `queuePending` reads device identity, which rethrows on a headless
    // engine with no channel and no primed cache. A throw here would add
    // `'feedback'` to the runner's `failedSteps` and ask the OS to retry the
    // whole task — and a retry begins with `drainOutbox`, which would send the
    // very snapshot this call failed to refresh. Requesting a retry would
    // bring the data loss forward.
    final throwing = _ThrowingUploader();
    when(
      () => throwing.queuePending(
        config: any(named: 'config'),
        userId: any(named: 'userId'),
      ),
    ).thenThrow(StateError('no platform channel'));

    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [feedbackUploaderProvider.overrideWithValue(throwing)],
    );
    addTearDown(container.dispose);

    await expectLater(
      sweepPendingFeedback(
        container,
        config: config,
        userId: 'ada',
        result: const SyncComplete(1),
      ),
      completes,
    );
  });

  /// **Reachability, not behaviour.** The tests above call the sweep directly,
  /// which is exactly the shape Phase 113 warned about and Phase 154 shipped:
  /// a function ported, tested, green and called by nothing.
  /// `executeBackgroundTask` needs a Flutter binding, real preferences and a
  /// WorkManager engine, so its wiring cannot be built in a unit test — the
  /// source is read instead, as `pending_submissions_sweep_test.dart` does.
  ///
  /// It asserts the call *site*: the window bounded below excludes this
  /// function's own declaration, so "nothing calls it" can actually fail.
  test('the headless path calls the sweep from the feedback step', () {
    final source = File('lib/background_entrypoint.dart').readAsStringSync();
    final wiring = source.substring(
      source.indexOf('BackgroundTaskRunner('),
      source.indexOf('@visibleForTesting'),
    );

    expect(
      wiring,
      contains('sweepPendingFeedback('),
      reason: 'nothing in the headless path calls sweepPendingFeedback',
    );

    final swept = wiring.indexOf('sweepPendingFeedback(');
    final steps = wiring.indexOf('syncSteps:');
    // **The pull itself, not the step's name literal.** An earlier cut
    // anchored on `"'feedback',"`, which is the `BackgroundSyncStep` label a
    // line above the pull — so moving the sweep call *above* the `sync(...)`
    // inside the same closure, the precise mistake this assertion's reason
    // describes, left it green. `feedbackRepositoryProvider` occurs exactly
    // once in the window and is the read whose result the sweep depends on.
    final pull = wiring.indexOf('feedbackRepositoryProvider');

    expect(steps, greaterThan(-1), reason: 'syncSteps moved');
    expect(pull, greaterThan(-1), reason: 'the feedback pull moved');
    expect(
      'feedbackRepositoryProvider'.allMatches(wiring).length,
      1,
      reason: 'the anchor above is only unambiguous while it occurs once',
    );

    expect(
      swept,
      greaterThan(pull),
      reason:
          'the re-queue must follow the pull: it exists to refresh a snapshot '
          'the pull has just made stale, and a call before it refreshes from a '
          'row the pull has not touched',
    );
    expect(
      swept,
      greaterThan(steps),
      reason:
          'the re-queue must NOT move into drainOutbox beside the three '
          'sweeps: that leg runs before syncSteps, so it would run before the '
          'pull it has to follow — a no-op shaped like a fix',
    );
    // The runtime ordering this placement rests on — drainOutbox before
    // syncSteps — is pinned by `background_task_runner_test.dart`'s
    // `expect(calls, ['recover', 'drain', 'sync'])`. An earlier cut asserted
    // it here as the textual order of two *named arguments*, which Dart does
    // not tie to execution order at all: reordering the argument list turned
    // it red with nothing changed, and reordering `run` left it green.
  });
}
