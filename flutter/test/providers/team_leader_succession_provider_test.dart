import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
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

/// The Members screen's leave and remove, end to end — the pair of writes,
/// not either half. `RequestsViewModel.leaveTeam` (`:75-86`) and
/// `removeMember` (`:89-106`).
void main() {
  late AppDatabase database;
  late ProviderContainer container;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: 'secret-pin',
    couchDbUrl: 'https://satellite:secret-pin@planet.example.org',
    id: 'config-1',
  );

  UserRow session(String id, String name) => UserRow(
    id: id,
    name: name,
    rolesList: const ['learner'],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  Future<void> open(UserRow user) async {
    database = AppDatabase.memory();
    container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWithValue(database),
        outboxRepositoryProvider.overrideWithValue(
          OutboxRepository(database.outboxDao),
        ),
        serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
        sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
      ],
    );
    addTearDown(container.dispose);
    addTearDown(database.close);
    // `TeamMembershipActions` is a plain `Provider` and never *watches* the
    // session, so resolve it first — the standing rule for this harness.
    await container.read(sessionProvider.future);
  }

  Future<void> seedMember({
    required String userId,
    required String name,
    bool isLeader = false,
    String? rev,
    int visits = 0,
  }) async {
    await database.teamDao.upsert(
      TeamsCompanion.insert(
        id: 'm-$userId',
        rev: Value(rev),
        teamId: const Value('team-1'),
        userId: Value(userId),
        docType: const Value('membership'),
        isLeader: Value(isLeader),
      ),
    );
    await database.userDao.upsert(
      UsersCompanion.insert(id: userId, name: Value(name)),
    );
    for (var i = 0; i < visits; i++) {
      await database.teamLogDao.insert(
        TeamLogTableCompanion.insert(
          id: 'log-$userId-$i',
          teamId: const Value('team-1'),
          user: Value(name),
          type: const Value('teamVisit'),
          time: Value(i),
        ),
      );
    }
  }

  Future<bool?> isLeaderOf(String userId) async =>
      (await database.teamDao.getById('m-$userId'))?.isLeader;

  group('leaving via the Members screen', () {
    test('the sole leader leaving promotes the most active member', () async {
      // The defect this lane exists for: before the succession, `leave`
      // removed the leader's membership and stopped, so `bob` and `cleo`
      // were left in a team with `isLeader` false on every row and no
      // in-app route to setting it — the Make leader action is behind
      // `canManage`, which *is* leadership.
      await open(session('ada', 'Ada'));
      await seedMember(userId: 'ada', name: 'Ada', isLeader: true);
      await seedMember(userId: 'bob', name: 'Bob', visits: 1);
      await seedMember(userId: 'cleo', name: 'Cleo', visits: 5);

      final outcome = await container
          .read(teamMembershipActionsProvider)
          .leaveFromMembers('team-1');

      expect(outcome, MemberActionOutcome.succeeded);
      expect(await database.teamDao.getById('m-ada'), isNull);
      expect(await isLeaderOf('cleo'), isTrue);
      expect(await isLeaderOf('bob'), isFalse);
    });

    test('the promotion is enqueued for upload', () async {
      // A successor promoted only on this handset is the same defect one
      // layer down: every other device still shows a leaderless team. The
      // promoted row must travel by the same route a hand-picked leader does.
      await open(session('ada', 'Ada'));
      await seedMember(
        userId: 'ada',
        name: 'Ada',
        isLeader: true,
        rev: '2-abc',
      );
      await seedMember(userId: 'bob', name: 'Bob', rev: '3-def');

      await container
          .read(teamMembershipActionsProvider)
          .leaveFromMembers('team-1');

      final queued = await database.outboxDao.due(
        DateTime.now().millisecondsSinceEpoch,
      );
      expect(
        queued.map((row) => row.itemId).toSet(),
        {'m-ada', 'm-bob'},
        reason: 'the promoted successor and the departing tombstone both go',
      );
    });

    test(
      'leaving still removes the member when nobody can be promoted',
      () async {
        // **Kotlin has no last-leader refusal on this path** — `leaveTeam`
        // promotes when it can and then removes unconditionally (`:82-83`).
        // What stops a team going leaderless is the menu gate
        // (`items.length > 1`), pinned in the screen test. Add a refusal here
        // and the port diverges from Kotlin in a way nothing asked for.
        await open(session('ada', 'Ada'));
        await seedMember(userId: 'ada', name: 'Ada', isLeader: true);
        await seedMember(userId: 'bob', name: 'Bob', isLeader: true);

        final outcome = await container
            .read(teamMembershipActionsProvider)
            .leaveFromMembers('team-1');

        expect(outcome, MemberActionOutcome.succeeded);
        expect(await database.teamDao.getById('m-ada'), isNull);
        expect(
          await isLeaderOf('bob'),
          isTrue,
          reason: 'untouched, not demoted',
        );
      },
    );

    test(
      'a rank-and-file member leaving does not disturb the leader',
      () async {
        // **A deliberate divergence from Kotlin, recorded here because it is
        // the one behavioural difference in this lane.** Kotlin calls
        // `getNextLeaderCandidate` unconditionally, and `updateTeamLeader` sets
        // `isLeader = false` on *every* row but the new leader — so in Kotlin
        // `bob` leaving this team promotes `cleo` and **demotes `ada`**, who is
        // sitting in the room. That is a defect, not a design: it loses the
        // elected leader on an unrelated member's departure, and inheriting it
        // would ship a new data loss to close an old one. Phase 156 declined to
        // ship an inherited `deleteNotIn` loss on exactly this argument.
        //
        // The succession therefore runs only when the departing member is a
        // leader. Everything else about the path is Kotlin's.
        await open(session('bob', 'Bob'));
        await seedMember(userId: 'ada', name: 'Ada', isLeader: true);
        await seedMember(userId: 'bob', name: 'Bob');
        await seedMember(userId: 'cleo', name: 'Cleo', visits: 9);

        final outcome = await container
            .read(teamMembershipActionsProvider)
            .leaveFromMembers('team-1');

        expect(outcome, MemberActionOutcome.succeeded);
        expect(await isLeaderOf('ada'), isTrue, reason: 'still the leader');
        expect(await isLeaderOf('cleo'), isFalse);
      },
    );

    test(
      'a successor whose membership carries the server id is promoted',
      () async {
        // End to end for the identity divergence the ranker pins in isolation.
        // `bob`'s membership names the CouchDB `_id` while the `users` row's
        // primary key is a locally-minted UUID — the state Kotlin's
        // `createTeamAndAddMember` (`TeamsRepositoryImpl:174`) writes and syncs
        // out, so it arrives here through `TeamMapper` like any other document.
        //
        // Kotlin resolves nobody, promotes nobody, and removes the leader
        // anyway. Two things have to be right for the port not to: the
        // `userMap` must be keyed on both columns, **and** `updateTeamLeader`
        // must be handed the membership's `userId` rather than the user's `id`
        // — it matches on `row.userId == newLeaderId`. Revert either half and
        // this test fails while the unit tests stay green.
        await open(session('ada', 'Ada'));
        await seedMember(userId: 'ada', name: 'Ada', isLeader: true);
        await database.teamDao.upsert(
          TeamsCompanion.insert(
            id: 'm-bob',
            teamId: const Value('team-1'),
            userId: const Value('org.couchdb.user:bob'),
            docType: const Value('membership'),
          ),
        );
        await database.userDao.upsert(
          UsersCompanion.insert(
            id: 'uuid-local',
            couchId: const Value('org.couchdb.user:bob'),
            name: const Value('Bob'),
          ),
        );

        final outcome = await container
            .read(teamMembershipActionsProvider)
            .leaveFromMembers('team-1');

        expect(outcome, MemberActionOutcome.succeeded);
        expect(
          (await database.teamDao.getById('m-bob'))?.isLeader,
          isTrue,
          reason: 'the team keeps a leader',
        );
      },
    );
  });

  group('a residual hole this lane did not close', () {
    test(
      'a leader can still leave a team whose other members are unknown here',
      () async {
        // **This test pins a gap, not a fix. It is a tripwire: when somebody
        // closes the gap, this goes red and this comment is the hand-off.**
        //
        // `selectNextLeaderCandidate` returns null when *no* candidate
        // resolves to a `users` row — Kotlin's `if (users.isEmpty()) return
        // null` (`TeamsRepositoryImpl:1089`), reproduced. The leave then
        // proceeds unconditionally, as Kotlin's does, so a leader whose
        // fellow members have never been synced onto this handset leaves the
        // team with a member and no leader. The screen's `items.length > 1`
        // gate does not catch it: that counts raw `membership` rows, and the
        // rows are there — it is the `users` rows that are missing.
        //
        // It is left open deliberately, because closing it is a design
        // choice this lane had no warrant to make on its own, and both
        // options cost something:
        //
        //   * **Refuse the leave** when the departing leader is a leader and
        //     no successor resolves. Diverges from Kotlin a third time, and
        //     traps a leader on a handset that simply has not finished
        //     syncing — the team may be perfectly healthy elsewhere.
        //   * **Promote the first candidate anyway**, skipping the
        //     resolve-to-a-user requirement. Keeps the team led, but the
        //     members list renders the new leader as a raw id until their
        //     `users` row arrives.
        //
        // Whoever decides: delete this test, and make sure something else
        // holds whichever behaviour you chose — an exemption is not retired
        // when its entry is deleted, only when something else holds its
        // subject.
        await open(session('ada', 'Ada'));
        await seedMember(userId: 'ada', name: 'Ada', isLeader: true);
        // A membership row with no matching `users` row — the shape a team
        // synced before its members' accounts were.
        await database.teamDao.upsert(
          TeamsCompanion.insert(
            id: 'm-unknown',
            teamId: const Value('team-1'),
            userId: const Value('never-synced'),
            docType: const Value('membership'),
          ),
        );

        final outcome = await container
            .read(teamMembershipActionsProvider)
            .leaveFromMembers('team-1');

        expect(outcome, MemberActionOutcome.succeeded);
        expect(await database.teamDao.getById('m-ada'), isNull);
        expect(
          (await database.teamDao.getById('m-unknown'))?.isLeader,
          isFalse,
          reason:
              'the gap: nobody was promoted, so this team now has a member '
              'and no leader. If this assertion fails, the gap has been '
              'closed — read the comment above and delete this test.',
        );
      },
    );
  });

  group('removing yourself through the remove action', () {
    test('refuses when there is no successor', () async {
      // `RequestsViewModel.removeMember:93-99` — the only place Kotlin
      // refuses. Reachable through the session-still-loading race described
      // at the method.
      await open(session('ada', 'Ada'));
      await seedMember(userId: 'ada', name: 'Ada', isLeader: true);

      final outcome = await container
          .read(teamMembershipActionsProvider)
          .removeMember('team-1', 'ada');

      expect(outcome, MemberActionOutcome.cannotRemoveLastLeader);
      expect(
        await database.teamDao.getById('m-ada'),
        isNotNull,
        reason: 'the refusal must not remove the row anyway',
      );
    });

    test('promotes a successor and then removes when one exists', () async {
      await open(session('ada', 'Ada'));
      await seedMember(userId: 'ada', name: 'Ada', isLeader: true);
      await seedMember(userId: 'bob', name: 'Bob');

      final outcome = await container
          .read(teamMembershipActionsProvider)
          .removeMember('team-1', 'ada');

      expect(outcome, MemberActionOutcome.succeeded);
      expect(await database.teamDao.getById('m-ada'), isNull);
      expect(await isLeaderOf('bob'), isTrue);
    });

    test('removing somebody else never refuses and never promotes', () async {
      // Kotlin's succession branch is gated on `currentUserId == memberId`
      // (`:92`), so a leader removing a member touches no `isLeader` flag —
      // even when they are removing the team's *other* leader.
      await open(session('ada', 'Ada'));
      await seedMember(userId: 'ada', name: 'Ada', isLeader: true);
      await seedMember(userId: 'bob', name: 'Bob', isLeader: true);
      await seedMember(userId: 'cleo', name: 'Cleo', visits: 3);

      final outcome = await container
          .read(teamMembershipActionsProvider)
          .removeMember('team-1', 'bob');

      expect(outcome, MemberActionOutcome.succeeded);
      expect(await database.teamDao.getById('m-bob'), isNull);
      expect(await isLeaderOf('ada'), isTrue);
      expect(await isLeaderOf('cleo'), isFalse, reason: 'nobody promoted');
    });
  });

  test('the detail screen leave is untouched by the succession', () async {
    // `teams_screen.dart` calls `leave`, not `leaveFromMembers`, and Kotlin's
    // matching path (`TeamViewModel.leaveTeam:143-146` →
    // `TeamsRepositoryImpl.leaveTeam:654`) promotes nobody either. This is
    // the guard against the succession being moved *into* `leave`, where it
    // would silently change a second screen away from its own counterpart.
    await open(session('ada', 'Ada'));
    await seedMember(userId: 'ada', name: 'Ada', isLeader: true);
    await seedMember(userId: 'bob', name: 'Bob', visits: 4);

    final ok = await container
        .read(teamMembershipActionsProvider)
        .leave('team-1');

    expect(ok, isTrue);
    expect(await isLeaderOf('bob'), isFalse);
  });
}
