import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// `TeamDao.watchMemberCount` against the statement it ports,
/// `TeamDao.countByTeamIdAndDocType` (`TeamDao.kt:29`), reached in the Kotlin
/// only as `TeamsRepositoryImpl.getJoinedMemberCount` (`:1045-1047`):
///
/// ```sql
/// SELECT COUNT(DISTINCT userId) FROM teams
///  WHERE teamId = :teamId AND docType = :docType AND isDeletePending = 0
///    AND userId IS NOT NULL
///    AND EXISTS (SELECT 1 FROM users u
///                 WHERE u.id = teams.userId OR u._id = teams.userId)
/// ```
///
/// Each case is run against the **raw statement** as well as the DAO, on the
/// same rows, so the assertion is pinned to SQLite's answer rather than to a
/// reading of it. The raw form drops `isDeletePending` because the port has no
/// such column and no such state — `leave`/`removeMember` hard-delete the row
/// — which the DAO's doc comment argues at length.
void main() {
  late AppDatabase db;
  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  /// The Kotlin statement, minus the `isDeletePending` clause, on this schema.
  Future<int> kotlinCount(String teamId) async {
    final row = await db
        .customSelect(
          'SELECT COUNT(DISTINCT user_id) AS c FROM teams '
          "WHERE team_id = ?1 AND doc_type = 'membership' "
          'AND user_id IS NOT NULL '
          'AND EXISTS (SELECT 1 FROM users u '
          'WHERE u.id = teams.user_id OR u._id = teams.user_id)',
          variables: [Variable<String>(teamId)],
        )
        .getSingle();
    return row.read<int>('c');
  }

  Future<void> seedUser(String id, {String? couchId}) => db.userDao.upsert(
    UsersCompanion.insert(
      id: id,
      couchId: couchId == null ? const Value.absent() : Value(couchId),
    ),
  );

  Future<void> seedMembership(String docId, String teamId, String? userId) =>
      db.teamDao.upsertAll([
        TeamsCompanion.insert(
          id: docId,
          teamId: Value(teamId),
          userId: userId == null ? const Value.absent() : Value(userId),
          docType: const Value('membership'),
        ),
      ]);

  test('two membership documents for one person count as one member', () async {
    await seedUser('ada');
    // A re-join leaves both documents behind: same person, two CouchDB ids,
    // and nothing in the sync-in collapses them. `COUNT(id)` said two.
    await seedMembership('m-1', 'team-1', 'ada');
    await seedMembership('m-2', 'team-1', 'ada');

    expect(await db.teamDao.watchMemberCount('team-1').first, 1);
    expect(await kotlinCount('team-1'), 1);
  });

  test(
    'a membership whose person this device has never synced is not a member',
    () async {
      await seedUser('ada');
      await seedMembership('m-1', 'team-1', 'ada');
      await seedMembership('m-2', 'team-1', 'grace'); // no `users` row

      expect(await db.teamDao.watchMemberCount('team-1').first, 1);
      expect(await kotlinCount('team-1'), 1);
    },
  );

  test('a member matched only by the CouchDB id counts', () async {
    // **The load-bearing fixture in this file.** `bianca`'s membership names
    // her server id while her `users` row is keyed on the local id it was
    // registered under, so she is reachable through `u._id` and *not* through
    // `u.id`. An `EXISTS` written with only the `u.id` half — the obvious way
    // to write it, and wrong — returns 1 here instead of 2, and every other
    // case in this file stays green. See `UserDao.getById` for why an account
    // has two identities at all.
    await seedUser('ada');
    await seedUser('local-99', couchId: 'org.couchdb.user:bianca');
    await seedMembership('m-1', 'team-1', 'ada');
    await seedMembership('m-2', 'team-1', 'org.couchdb.user:bianca');

    expect(await db.teamDao.watchMemberCount('team-1').first, 2);
    expect(await kotlinCount('team-1'), 2);
  });

  test('a membership document with no userId is not a member', () async {
    await seedUser('ada');
    await seedMembership('m-1', 'team-1', 'ada');
    await seedMembership('m-2', 'team-1', null);

    expect(await db.teamDao.watchMemberCount('team-1').first, 1);
    expect(await kotlinCount('team-1'), 1);
  });

  test('another team and another docType are out of scope', () async {
    await seedUser('ada');
    await seedUser('grace');
    await seedMembership('m-1', 'team-1', 'ada');
    await seedMembership('m-2', 'team-2', 'grace');
    await db.teamDao.upsertAll([
      TeamsCompanion.insert(
        id: 'r-1',
        teamId: const Value('team-1'),
        userId: const Value('grace'),
        docType: const Value('request'),
      ),
    ]);

    expect(await db.teamDao.watchMemberCount('team-1').first, 1);
    expect(await kotlinCount('team-1'), 1);
  });

  test(
    'the count re-emits when the missing person arrives on a later sync',
    () async {
      // Why `readsFrom` names `users` as well as `teams`: this member becomes
      // countable without `teams` changing at all, and a stream that declared
      // only `teams` would sit on the stale 0.
      await seedMembership('m-1', 'team-1', 'ada');
      final counts = db.teamDao.watchMemberCount('team-1');
      expect(await counts.first, 0);

      await seedUser('ada');
      expect(await counts.first, 1);
    },
  );
}
