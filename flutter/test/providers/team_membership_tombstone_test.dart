import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/repository/outbox_repository.dart';

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);
  final ServerConfig? config;
  @override
  ServerConfig? build() => config;
}

/// A session whose `build` rejects — an unavailable `planetPrefs`, a failed
/// `userDao` read. `.valueOrNull` was null here and every action returned
/// `false`; a `.future` rejects instead, which is a different contract.
class _FailingSessionNotifier extends SessionNotifier {
  @override
  Future<UserRow?> build() async => throw StateError('prefs unavailable');
}

/// A session that resolves only after a delay. `TeamMembershipActions` is a
/// plain `Provider`, so nothing it holds ever *watches* `sessionProvider`; a
/// bare `ref.read(...).valueOrNull` is null for this whole window.
class _DelayedSessionNotifier extends SessionNotifier {
  _DelayedSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() =>
      Future.delayed(const Duration(milliseconds: 50), () => user);
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

void main() {
  late AppDatabase database;
  late ProviderContainer container;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: 'secret-pin',
    couchDbUrl: 'https://satellite:secret-pin@planet.example.org',
    id: 'config-1',
    code: 'community-a',
    parentCode: 'nation',
  );

  setUp(() {
    database = AppDatabase.memory();
    container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(database),
        outboxRepositoryProvider.overrideWithValue(
          OutboxRepository(database.outboxDao),
        ),
        serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(
            UserRow(
              id: 'user-1',
              name: 'ada',
              rolesList: const ['learner'],
              userAdmin: false,
              joinDate: 0,
              isArchived: false,
              isUpdated: false,
            ),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);
    addTearDown(database.close);
  });

  Future<void> seedMembership({required String id, String? rev}) =>
      database.teamDao.upsert(
        TeamsCompanion.insert(
          id: id,
          rev: Value(rev),
          teamId: const Value('team-1'),
          userId: const Value('user-1'),
          docType: const Value('membership'),
        ),
      );

  test(
    'leaving a team the server knows about enqueues its tombstone',
    () async {
      await seedMembership(id: 'm-synced', rev: '2-abc');
      // Resolve the session first: `TeamMembershipActions` is a plain
      // `Provider`, so its `ref` never watches `sessionProvider`.
      await container.read(sessionProvider.future);

      final ok = await container
          .read(teamMembershipActionsProvider)
          .leave('team-1');

      expect(ok, isTrue);
      final queued = await database.outboxDao.due(
        DateTime.now().millisecondsSinceEpoch,
      );
      expect(queued.length, 1);
      expect(queued.single.itemId, 'm-synced');
    },
  );

  test('leaving before the first sync enqueues nothing', () async {
    // `markMembershipsForLeave` (`TeamsRepositoryImpl.kt:1323-1337`) branches
    // on the revision: `if (membership._rev.isNullOrBlank())` it is a pure
    // local delete with nothing uploaded. The port enqueued unconditionally,
    // so the body carried `"_rev": null`, CouchDB rejected it 4xx, and the
    // outbox's retryable rule is `code >= 500` — the row failed out
    // permanently for a document the server never had.
    await seedMembership(id: 'm-local', rev: null);
    await container.read(sessionProvider.future);

    final ok = await container
        .read(teamMembershipActionsProvider)
        .leave('team-1');

    expect(ok, isTrue, reason: 'the local delete still succeeds');
    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch,
    );
    expect(queued, isEmpty);
    expect(await database.teamDao.getById('m-local'), isNull);
  });

  test('an empty revision counts as unsynced too', () async {
    // Kotlin's test is `isNullOrBlank`, not `== null`.
    await seedMembership(id: 'm-blank', rev: '');
    await container.read(sessionProvider.future);

    await container.read(teamMembershipActionsProvider).leave('team-1');

    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch,
    );
    expect(queued, isEmpty);
  });

  test('leaving works before anything else has resolved the session', () async {
    // The tests above `await container.read(sessionProvider.future)` first,
    // which makes `.valueOrNull` non-null and so cannot catch a regression of
    // the read-but-never-watched shape. This one deliberately does not
    // pre-resolve it: `TeamMembershipActions` must resolve the session itself.
    await seedMembership(id: 'm-synced', rev: '2-abc');
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(database),
        outboxRepositoryProvider.overrideWithValue(
          OutboxRepository(database.outboxDao),
        ),
        serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
        sessionProvider.overrideWith(
          () => _DelayedSessionNotifier(
            UserRow(
              id: 'user-1',
              name: 'ada',
              rolesList: const ['learner'],
              userAdmin: false,
              joinDate: 0,
              isArchived: false,
              isUpdated: false,
            ),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);

    final ok = await container
        .read(teamMembershipActionsProvider)
        .leave('team-1');

    expect(ok, isTrue, reason: 'the leave must not take a silent failure path');
    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch,
    );
    expect(queued.length, 1);
    expect(await database.teamDao.getById('m-synced'), isNull);
  });

  ProviderContainer failingSessionContainer() {
    final c = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(database),
        outboxRepositoryProvider.overrideWithValue(
          OutboxRepository(database.outboxDao),
        ),
        serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
        sessionProvider.overrideWith(_FailingSessionNotifier.new),
      ],
    );
    addTearDown(c.dispose);
    return c;
  }

  test('a rejecting session reports failure rather than throwing', () async {
    // Before the session fix these returned `false` on a null read, and
    // `team_members_screen`'s `_handleMemberAction` showed an "Operation
    // failed" snackbar off that. A future *rejects* where `valueOrNull` could
    // not, and no caller wraps the await — the join button and the leave
    // dialog are both fire-and-forget — so a throw loses the message and
    // escapes as an uncaught async error.
    await seedMembership(id: 'm-synced', rev: '2-abc');
    final c = failingSessionContainer();

    await expectLater(
      c.read(teamMembershipActionsProvider).leave('team-1'),
      completion(isFalse),
    );
  });

  test('a rejecting session cannot strand an accepted join request', () async {
    // `respond` resolved the session *after* `respondToRequest` had already
    // converted the request row to a `membership` with `isUpdated = true`.
    // A rejection there threw past the enqueue, and the outbox is the only
    // upload route — `TeamDao` has no pending sweep and `TeamsUploader` has
    // no rescan — so the accepted member existed on the leader's device and
    // nowhere else, permanently. Resolve the user before the local write.
    await database.teamDao.upsert(
      TeamsCompanion.insert(
        id: 'req-1',
        rev: const Value('3-def'),
        teamId: const Value('team-1'),
        userId: const Value('user-2'),
        docType: const Value('request'),
      ),
    );
    final c = failingSessionContainer();

    final ok = await c
        .read(teamMembershipActionsProvider)
        .respond('req-1', accept: true);

    expect(ok, isFalse, reason: 'the caller must learn it failed');
    // Nothing was queued, so nothing may have been written either.
    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch,
    );
    expect(queued, isEmpty);
    final row = await database.teamDao.getById('req-1');
    expect(
      row?.docType,
      'request',
      reason: 'the request must not be converted with no route to upload it',
    );
  });

  test('removing a member who never synced enqueues nothing', () async {
    await seedMembership(id: 'm-local', rev: null);
    await container.read(sessionProvider.future);

    await container
        .read(teamMembershipActionsProvider)
        .removeMember('team-1', 'user-1');

    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch,
    );
    expect(queued, isEmpty);
  });
}
