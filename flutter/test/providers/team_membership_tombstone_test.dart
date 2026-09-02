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
