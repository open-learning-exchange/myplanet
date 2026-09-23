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
/// not either half. `RequestsViewModel.leaveTeam` (`:77-90`) and
/// `removeMember` (`:92-112`).
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

  group('a leaderless team heals rather than staying stuck', () {
    test('any member leaving a leaderless team promotes a successor', () async {
      // **The second audit pass caught this, and it is the inverse of the
      // lane's purpose.** The first cut gated the succession purely on "is
      // the leaver a leader", reasoning that the port could not reach a
      // leaderless team because `updateTeamLeader` always leaves exactly one
      // leader. Both halves were false: `TeamMapper.fromDoc`
      // (`team_mapper.dart:78`) writes `isLeader` on every pull and the
      // catalog's `leaders` fan-out writes it too, and the port authors no
      // teams at all — so *every* leader it holds came from a server
      // document and a team can simply arrive leaderless.
      //
      // Once it has, `canManage` is false for everyone, so "Make leader" is
      // never offered; with a leaver-only gate the succession would never
      // fire either, and the team would be unrecoverable in-app for ever.
      // Kotlin self-heals here because it calls `getNextLeaderCandidate`
      // unconditionally. The gate is therefore "the leaver leads **or**
      // nobody does".
      await open(session('bob', 'Bob'));
      await seedMember(userId: 'ada', name: 'Ada', visits: 1);
      await seedMember(userId: 'bob', name: 'Bob');
      await seedMember(userId: 'cleo', name: 'Cleo', visits: 7);

      final outcome = await container
          .read(teamMembershipActionsProvider)
          .leaveFromMembers('team-1');

      expect(outcome, MemberActionOutcome.succeeded);
      expect(await isLeaderOf('cleo'), isTrue, reason: 'the team has a leader');
    });

    test(
      'a duplicate membership row cannot hide the leaver leadership',
      () async {
        // `_needsSuccession` reads every membership row rather than a `LIMIT 1`
        // lookup with no `ORDER BY`. A user holding two membership documents —
        // which `TeamDao.watchMemberCount` documents as reachable — would
        // otherwise have leadership decided by whichever row SQLite returned.
        await open(session('ada', 'Ada'));
        await seedMember(userId: 'ada', name: 'Ada', isLeader: true);
        await database.teamDao.upsert(
          TeamsCompanion.insert(
            id: 'm-ada-dup',
            teamId: const Value('team-1'),
            userId: const Value('ada'),
            docType: const Value('membership'),
          ),
        );
        await seedMember(userId: 'bob', name: 'Bob');

        await container
            .read(teamMembershipActionsProvider)
            .leaveFromMembers('team-1');

        expect(await isLeaderOf('bob'), isTrue);
      },
    );

    test('the departing member is never promoted back in', () async {
      // The exclusion predicate matches one spelling of the leaver's id while
      // resolution matches two, so a second membership row under the other
      // spelling survives `eligibleNextLeaderCandidates` and resolves right
      // back to the person leaving. Without the filter in
      // `_nextLeaderCandidate` they are promoted into the team they just
      // left. Kotlin cannot reach this only because its one-keyed map
      // resolves that row to nobody; having deliberately fixed that, the port
      // has to close this too.
      await open(session('uuid-ada', 'Ada'));
      await database.userDao.upsert(
        UsersCompanion.insert(
          id: 'uuid-ada',
          couchId: const Value('org.couchdb.user:ada'),
          name: const Value('Ada'),
        ),
      );
      await database.teamDao.upsert(
        TeamsCompanion.insert(
          id: 'm-ada',
          teamId: const Value('team-1'),
          userId: const Value('uuid-ada'),
          docType: const Value('membership'),
          isLeader: const Value(true),
        ),
      );
      // The same person, under their other id, as a plain member.
      await database.teamDao.upsert(
        TeamsCompanion.insert(
          id: 'm-ada-couch',
          teamId: const Value('team-1'),
          userId: const Value('org.couchdb.user:ada'),
          docType: const Value('membership'),
        ),
      );

      await container
          .read(teamMembershipActionsProvider)
          .leaveFromMembers('team-1');

      expect(
        (await database.teamDao.getById('m-ada-couch'))?.isLeader,
        isFalse,
        reason: 'the leaver must not be promoted into the team they left',
      );
    });

    test('the leaver own demotion is not enqueued', () async {
      // `_promoteLeader(skipUserId:)`. The leaver's row is about to be
      // deleted, and `leave` writes a tombstone only when the server already
      // knows it — so for a locally-authored membership the demotion would be
      // the only thing left in the outbox under that id: a create-shaped POST
      // putting the leaver back into the team on the next pull.
      await open(session('ada', 'Ada'));
      await seedMember(userId: 'ada', name: 'Ada', isLeader: true);
      await seedMember(userId: 'bob', name: 'Bob');

      await container
          .read(teamMembershipActionsProvider)
          .leaveFromMembers('team-1');

      final queued = await database.outboxDao.due(
        DateTime.now().millisecondsSinceEpoch,
      );
      expect(queued.map((row) => row.itemId), ['m-bob']);
    });
  });

  group('removing yourself through the remove action', () {
    test('refuses when there is no successor', () async {
      // `RequestsViewModel.removeMember` — the refusal is `:101`, the only
      // place Kotlin
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
      // (`:96`), so a leader removing a member touches no `isLeader` flag —
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
