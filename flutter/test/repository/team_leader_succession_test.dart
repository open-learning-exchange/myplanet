import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/teams_repository.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Leadership succession when a member leaves via the Members screen —
/// `TeamsRepositoryImpl.getNextLeaderCandidate` (`:1079-1104`) and the query
/// behind it, `TeamDao.getEligibleNextLeaderCandidates` (`TeamDao.kt:22`).
void main() {
  late AppDatabase database;

  setUp(() => database = AppDatabase.memory());
  tearDown(() => database.close());

  TeamsCompanion membership({
    required String id,
    String teamId = 'team-1',
    String? userId,
    String docType = 'membership',
    bool isLeader = false,
    String? status,
  }) => TeamsCompanion.insert(
    id: id,
    teamId: Value(teamId),
    userId: Value(userId),
    docType: Value(docType),
    isLeader: Value(isLeader),
    status: Value(status),
  );

  group('the eligible-candidates predicate', () {
    // `WHERE teamId = :teamId AND docType = 'membership' AND isLeader = 0
    //    AND (status IS NULL OR status != 'archived')
    //    AND (:excludeUserId IS NULL OR userId != :excludeUserId)`
    //
    // **One decoy per conjunct, and that is the point of this fixture.** Phase
    // 156's two integration tests were green on the fix *and* green with the
    // fix reverted, because the rows they seeded could not tell the two
    // behaviours apart. A fixture holding only the wanted row would pin
    // nothing here: every clause could be deleted and the test would still
    // pass. Each decoy below is chosen so that dropping *its own* clause makes
    // this test fail and no other.
    test('admits only the rows all five clauses agree on', () async {
      await database.teamDao.upsertAll([
        membership(id: 'wanted-a', userId: 'ada'),
        membership(id: 'wanted-b', userId: 'bob'),
        // teamId — a perfectly eligible member of a *different* team.
        membership(id: 'other-team', teamId: 'team-2', userId: 'cleo'),
        // docType — a join request, not yet a member.
        membership(id: 'request', userId: 'dana', docType: 'request'),
        // isLeader — the sitting leader is not their own successor.
        membership(id: 'leader', userId: 'eve', isLeader: true),
        // status — an archived membership.
        membership(id: 'archived', userId: 'finn', status: 'archived'),
        // excludeUserId — the member who is leaving.
        membership(id: 'leaving', userId: 'gus'),
      ]);

      final rows = await database.teamDao.eligibleNextLeaderCandidates(
        'team-1',
        'gus',
      );

      expect(
        rows.map((row) => row.id).toSet(),
        {'wanted-a', 'wanted-b'},
        reason: 'each excluded row is excluded by exactly one clause',
      );
    });

    test('a non-archived status is still eligible', () async {
      // The `status IS NULL OR …` clause is written the long way because SQL
      // would otherwise drop null-status rows; this pins the *other* half, so
      // narrowing the clause to `status IS NULL` fails here.
      await database.teamDao.upsertAll([
        membership(id: 'active', userId: 'ada', status: 'active'),
      ]);

      final rows = await database.teamDao.eligibleNextLeaderCandidates(
        'team-1',
        null,
      );

      expect(rows.map((row) => row.id), ['active']);
    });

    test('a null status is eligible', () async {
      // The complement of the test above: every locally-authored membership
      // carries a null `status`, and a bare `status != 'archived'` evaluates
      // to NULL — not true — for those rows, so it would exclude exactly the
      // members this device knows best.
      await database.teamDao.upsertAll([
        membership(id: 'local', userId: 'ada'),
      ]);

      final rows = await database.teamDao.eligibleNextLeaderCandidates(
        'team-1',
        null,
      );

      expect(rows.map((row) => row.id), ['local']);
    });

    test('a null excludeUserId excludes nobody', () async {
      await database.teamDao.upsertAll([
        membership(id: 'a', userId: 'ada'),
        membership(id: 'b', userId: 'bob'),
      ]);

      final rows = await database.teamDao.eligibleNextLeaderCandidates(
        'team-1',
        null,
      );

      expect(rows.map((row) => row.id).toSet(), {'a', 'b'});
    });

    test(
      'a membership with no userId survives only while nobody is excluded',
      () async {
        // Kotlin's `userId != :excludeUserId` is NULL — so falsy — for a null
        // `userId`, while the `:excludeUserId IS NULL` arm short-circuits the
        // whole clause. Reproducing that asymmetry is what `NOT (user_id = ?)`
        // buys over `user_id IS NOT ?`; swap the two and this test fails.
        await database.teamDao.upsertAll([membership(id: 'nameless')]);

        expect(
          (await database.teamDao.eligibleNextLeaderCandidates(
            'team-1',
            null,
          )).map((row) => row.id),
          ['nameless'],
        );
        expect(
          await database.teamDao.eligibleNextLeaderCandidates('team-1', 'gus'),
          isEmpty,
        );
      },
    );
  });

  group('updateTeamLeader', () {
    // Added because the mutation run found this guard pinned by nothing: the
    // audit that proposed it also said it is unreachable from the three
    // callers, all of which pass a `userId` read off a membership row of the
    // team. An unpinned guard reads as coverage, so it gets a direct test.
    test('a newLeaderId matching no membership changes nothing', () async {
      // `TeamsRepositoryImpl.kt:1063` —
      // `memberships.firstOrNull { it.userId == newLeaderId } ?: return false`.
      // Without it the loop's `shouldBeLeader` is false for every row, so it
      // **demotes the whole team** and reports success.
      final repository = TeamsRepository(
        _MockPlanetApi(),
        database.teamDao,
        database.teamLogDao,
      );
      await database.teamDao.upsertAll([
        membership(id: 'm-ada', userId: 'ada', isLeader: true),
        membership(id: 'm-bob', userId: 'bob'),
      ]);

      final changed = await repository.updateTeamLeader('team-1', 'nobody');

      expect(changed, isEmpty);
      expect((await database.teamDao.getById('m-ada'))?.isLeader, isTrue);
    });
  });

  group('the successor ranking', () {
    // Rows are seeded through the real database and read back rather than
    // constructed by hand: a hand-built `TeamRow` has to name every
    // non-nullable column, which makes the fixture drift silently the moment
    // a column is added, and these rows are exactly what the DAO above hands
    // the ranker in production.
    Future<UserRow> user(String id, String? name) async {
      await database.userDao.upsert(
        UsersCompanion.insert(id: id, name: Value(name)),
      );
      return (await database.userDao.getById(id))!;
    }

    var logSeq = 0;
    Future<TeamLogRow> visit(String userName) async {
      final id = 'log-${logSeq++}';
      await database.teamLogDao.insert(
        TeamLogTableCompanion.insert(
          id: id,
          teamId: const Value('team-1'),
          user: Value(userName),
          type: const Value('teamVisit'),
          time: const Value(0),
        ),
      );
      return (await database.teamLogDao.teamVisitsForUsers('team-1', [
        userName,
      ])).firstWhere((row) => row.id == id);
    }

    Future<TeamRow> candidate(String userId) async {
      await database.teamDao.upsertAll([
        membership(id: 'm-$userId', userId: userId),
      ]);
      return (await database.teamDao.getById('m-$userId'))!;
    }

    test('promotes the member with the most team visits', () async {
      final winner = selectNextLeaderCandidate(
        candidates: [await candidate('ada'), await candidate('bob')],
        users: [await user('ada', 'Ada'), await user('bob', 'Bob')],
        visits: [await visit('Ada'), await visit('Bob'), await visit('Bob')],
      );

      expect(winner?.userId, 'bob');
    });

    test('a member with no visits still ranks, at zero', () async {
      // `visitCounts[name] ?: 0L` — a candidate nobody has a log row for is
      // not dropped from the pool, it simply scores nothing. With a single
      // candidate that makes them the successor.
      final winner = selectNextLeaderCandidate(
        candidates: [await candidate('ada')],
        users: [await user('ada', 'Ada')],
        visits: const [],
      );

      expect(winner?.userId, 'ada');
    });

    test('ties go to the first candidate', () async {
      // Kotlin's `maxByOrNull` replaces its running maximum only on a strict
      // `>`, so the earliest element wins. Relax the comparison in
      // `selectNextLeaderCandidate` to `>=` and this is the test that fails —
      // it is the only one that can, because every other case here has a
      // unique maximum.
      final winner = selectNextLeaderCandidate(
        candidates: [await candidate('ada'), await candidate('bob')],
        users: [await user('ada', 'Ada'), await user('bob', 'Bob')],
        visits: [await visit('Ada'), await visit('Bob')],
      );

      expect(winner?.userId, 'ada');
    });

    test('visits are counted by user name, not by user id', () async {
      // `team_log.user` holds the user's *name*
      // (`TeamLogDao.getTeamVisitsForUsers`), and the join back to the
      // membership row is `userMap[member.userId]?.name`. Seeding the log
      // rows under the **ids** here is the decoy: a ranking that keyed on id
      // would read three visits for `bob` and promote them.
      final winner = selectNextLeaderCandidate(
        candidates: [await candidate('ada'), await candidate('bob')],
        users: [await user('ada', 'Ada'), await user('bob', 'Bob')],
        visits: [
          await visit('bob'),
          await visit('bob'),
          await visit('bob'),
          await visit('Ada'),
        ],
      );

      expect(winner?.userId, 'ada');
    });

    test(
      'a candidate in no users row scores zero rather than winning',
      () async {
        // A membership pointing at an account this device has never synced
        // resolves to nobody. It stays in the pool — Kotlin does not filter it
        // — but it can never out-rank a member who does resolve.
        final winner = selectNextLeaderCandidate(
          candidates: [await candidate('ghost'), await candidate('ada')],
          users: [await user('ada', 'Ada')],
          visits: [await visit('Ada')],
        );

        expect(winner?.userId, 'ada');
      },
    );

    test('an unresolvable winner promotes nobody', () async {
      // Kotlin's closing `userMap[successorMember.userId]`: the winner has to
      // be somebody this device knows. Here everyone scores zero and `ghost`
      // is first, so it wins the tie — and is then refused.
      final winner = selectNextLeaderCandidate(
        candidates: [await candidate('ghost'), await candidate('ada')],
        users: [await user('ada', 'Ada')],
        visits: const [],
      );

      expect(winner, isNull);
    });

    test('a membership keyed on the server id still resolves', () async {
      // **The lane's second deliberate divergence, and the test that pins
      // it.** Kotlin keys its `userMap` on `it.id` alone while having fetched
      // on `id OR _id`, so a membership carrying the CouchDB `_id` for an
      // account whose primary key is a locally-minted UUID resolves to
      // nobody: it scores zero and, if it wins, promotes nobody at all.
      //
      // Both spellings reach CouchDB from the Android app
      // (`TeamsRepositoryImpl:620` writes `user.id`, `:174` writes
      // `user._id`) and both sync down here, so inheriting the asymmetry
      // would mean the succession silently no-ops for those teams.
      //
      // The decoy is that `ada` is seeded with *more* visits: a lookup that
      // failed to resolve `bob` would score them zero and promote `ada`, and
      // the test would pass for the wrong reason without it.
      await database.userDao.upsert(
        UsersCompanion.insert(
          id: 'uuid-local',
          couchId: const Value('org.couchdb.user:bob'),
          name: const Value('Bob'),
        ),
      );
      final bob = (await database.userDao.getById('uuid-local'))!;

      final winner = selectNextLeaderCandidate(
        candidates: [
          await candidate('org.couchdb.user:bob'),
          await candidate('ada'),
        ],
        users: [bob, await user('ada', 'Ada')],
        visits: [await visit('Bob'), await visit('Bob'), await visit('Ada')],
      );

      expect(winner?.userId, 'org.couchdb.user:bob');
    });

    test('no candidates and no users both yield nobody', () async {
      expect(
        selectNextLeaderCandidate(
          candidates: const [],
          users: [await user('ada', 'Ada')],
          visits: const [],
        ),
        isNull,
      );
      expect(
        selectNextLeaderCandidate(
          candidates: [await candidate('ada')],
          users: const [],
          visits: const [],
        ),
        isNull,
      );
    });

    test('a nameless user contributes no name to fetch visits for', () async {
      // `users.mapNotNull { it.name }.distinct()` — nulls dropped, duplicates
      // collapsed.
      expect(
        leaderCandidateVisitNames([
          await user('a', 'Ada'),
          await user('b', null),
          await user('c', 'Ada'),
        ]),
        ['Ada'],
      );
    });
  });
}
