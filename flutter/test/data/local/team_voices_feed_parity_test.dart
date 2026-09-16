import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// `NewsDao.countTopLevelByTeam` / `watchTopLevelByTeam` against the statement
/// they port (`NewsDao.kt:27-31`, `:73-74`):
///
/// ```sql
/// SELECT * FROM news
///  WHERE (replyTo IS NULL OR replyTo = '')
///    AND ((viewableBy = 'teams' COLLATE NOCASE AND viewableId = :teamId COLLATE NOCASE)
///          OR viewIn LIKE :teamPattern ESCAPE '\')
///  ORDER BY time DESC
/// ```
///
/// with `:teamPattern` from `VoicesRepositoryImpl.teamIdPattern` (`:99-105`).
/// Every case runs the raw statement on the same rows, so what is pinned is
/// SQLite's answer rather than a reading of the Kotlin.
void main() {
  late AppDatabase db;
  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  Future<int> kotlinCount(String teamId) async {
    final row = await db
        .customSelect(
          'SELECT COUNT(*) AS c FROM news '
          "WHERE (reply_to IS NULL OR reply_to = '') "
          "AND ((viewable_by = 'teams' COLLATE NOCASE "
          'AND viewable_id = ?1 COLLATE NOCASE) '
          "OR view_in LIKE ?2 ESCAPE '\\')",
          variables: [
            Variable<String>(teamId),
            Variable<String>(NewsDao.teamIdPattern(teamId)),
          ],
        )
        .getSingle();
    return row.read<int>('c');
  }

  Future<void> post(
    String id, {
    String? viewIn,
    String? viewableBy,
    String? viewableId,
    String? replyTo,
    String docType = 'message',
    int time = 0,
  }) => db.newsDao.upsert(
    NewsEntriesCompanion.insert(
      id: id,
      docType: Value(docType),
      time: Value(time),
      replyTo: replyTo == null ? const Value.absent() : Value(replyTo),
      viewIn: viewIn == null ? const Value.absent() : Value(viewIn),
      viewableBy: viewableBy == null ? const Value.absent() : Value(viewableBy),
      viewableId: viewableId == null ? const Value.absent() : Value(viewableId),
    ),
  );

  String viewInFor(String teamId) =>
      '[{"_id":"$teamId","section":"teams","name":"T"}]';

  test(
    'a post composed in-app reaches the team through viewIn alone',
    () async {
      // What `createTeamPost` writes: `viewIn` names the team and `viewableBy`
      // is never set — in **either** app. Kotlin's composer builds the same map
      // (`TeamsVoicesFragment.kt:76-85`).
      await post('n-1', viewIn: viewInFor('team-1'));
      expect(await db.newsDao.countTopLevelByTeam('team-1'), 1);
      expect(await kotlinCount('team-1'), 1);
    },
  );

  test(
    'a server-authored post reaches the team through viewableBy, case-insensitively',
    () async {
      // The arm the port had dropped. `viewableBy`/`viewableId` are filled only
      // by the sync-in, so this is a post Planet wrote. The mixed case is the
      // `COLLATE NOCASE` half: written `equals('teams')` against the raw column,
      // this row is invisible and every other case in this file stays green.
      await post('n-1', viewableBy: 'Teams', viewableId: 'TEAM-1');
      expect(await db.newsDao.countTopLevelByTeam('team-1'), 1);
      expect(await kotlinCount('team-1'), 1);
    },
  );

  test(
    'viewableId alone is not the audience — viewableBy must say teams',
    () async {
      // **Load-bearing decoy.** Every other `viewableBy` fixture in this file
      // says `teams`, so deleting the `viewableBy` conjunct from
      // `_viewableByTeam` leaves all eight of them green — the Phase 156
      // "fixture that cannot distinguish" shape, in the file written to avoid
      // it. A row whose `viewableId` matches under a *different* audience is the
      // only thing that separates "checks the column" from "ignores it".
      await post('n-1', viewableBy: 'community', viewableId: 'team-1');
      expect(await db.newsDao.countTopLevelByTeam('team-1'), 0);
      expect(await kotlinCount('team-1'), 0);
    },
  );

  test(
    'COLLATE NOCASE folds ASCII only, on the column and the parameter alike',
    () async {
      // The case the first cut got wrong in both directions. It folded the
      // column with SQL `lower()` and the parameter with Dart's Unicode-aware
      // `toLowerCase()`, so `TEAM-Ä` missed its own exact match while `team-ä`
      // matched a query for `TEAM-Ä`. `COLLATE NOCASE` does neither.
      await post('exact', viewableBy: 'teams', viewableId: 'TEAM-Ä');
      await post('folded', viewableBy: 'teams', viewableId: 'team-ä');

      // **Which row comes back is the assertion, not how many.** Both
      // spellings fold to the same ASCII stem, so every query here matches
      // exactly one row under either reading and a count cannot tell them
      // apart — only the identity of the row differs. The first draft of this
      // test asserted a 0 that neither reading produces, which is the
      // "fixture that cannot distinguish" trap arriving from the other side:
      // an expectation no implementation satisfies, rather than one every
      // implementation satisfies.
      //
      // Under `COLLATE NOCASE` the `Ä`/`ä` distinction survives, so `TEAM-Ä`
      // finds its own row. Under the `lower()` / Dart-`toLowerCase()` pair it
      // did not: the parameter folded to `team-ä` while the column folded
      // only to `team-Ä`, so this query returned the *other* row.
      expect(
        (await db.newsDao.watchTopLevelByTeam('TEAM-Ä').first).map((r) => r.id),
        ['exact'],
      );
      expect(await kotlinCount('TEAM-Ä'), 1);
      // The ASCII half still folds, which is what the collation is for.
      expect(
        (await db.newsDao.watchTopLevelByTeam('team-Ä').first).map((r) => r.id),
        ['exact'],
      );
      expect(await kotlinCount('team-Ä'), 1);
      expect(
        (await db.newsDao.watchTopLevelByTeam('team-ä').first).map((r) => r.id),
        ['folded'],
      );
      expect(await kotlinCount('team-ä'), 1);
    },
  );

  test('a reply is not a top-level post', () async {
    await post('n-1', viewIn: viewInFor('team-1'));
    await post('n-2', viewIn: viewInFor('team-1'), replyTo: 'n-1');
    // `''` is what a reply written by this app stores; a synced one that
    // omitted the field stores null, and both must read as top-level/not.
    await post('n-3', viewIn: viewInFor('team-1'), replyTo: '');
    expect(await db.newsDao.countTopLevelByTeam('team-1'), 2);
    expect(await kotlinCount('team-1'), 2);
  });

  test('docType is not a predicate here', () async {
    // **Load-bearing decoy.** `watchTopLevelMessages` filters
    // `docType = 'message'`; the Kotlin's *team* statement has no such clause.
    // A post with any other docType is the only fixture that distinguishes
    // this method from one built on top of that one — which is exactly how
    // `teamVoicesProvider` is still built.
    await post('n-1', viewIn: viewInFor('team-1'));
    await post('n-2', viewIn: viewInFor('team-1'), docType: 'chat');
    expect(await db.newsDao.countTopLevelByTeam('team-1'), 2);
    expect(await kotlinCount('team-1'), 2);
  });

  test(
    'a team id containing LIKE metacharacters matches only its own team',
    () async {
      // Phase 156's rule about fixtures, applied: `a_l` under an **unescaped**
      // `_` matches `axl`, and under an escaped one does not. A decoy sharing no
      // character in that slot (`ada`) would match under neither reading and
      // pin nothing.
      await post('n-1', viewIn: viewInFor('a_l'));
      await post('n-2', viewIn: viewInFor('axl'));
      expect(await db.newsDao.countTopLevelByTeam('a_l'), 1);
      expect(await kotlinCount('a_l'), 1);
      expect(
        (await db.newsDao.watchTopLevelByTeam('a_l').first).single.id,
        'n-1',
      );
    },
  );

  test('a percent in a team id is not a wildcard', () async {
    await post('n-1', viewIn: viewInFor('%'));
    await post('n-2', viewIn: viewInFor('team-1'));
    expect(await db.newsDao.countTopLevelByTeam('%'), 1);
    expect(await kotlinCount('%'), 1);
  });

  test('another team is out of scope through either arm', () async {
    await post('n-1', viewIn: viewInFor('team-2'));
    await post('n-2', viewableBy: 'teams', viewableId: 'team-2');
    expect(await db.newsDao.countTopLevelByTeam('team-1'), 0);
    expect(await kotlinCount('team-1'), 0);
  });

  test('the feed is newest first and counts what the badge counts', () async {
    await post('old', viewIn: viewInFor('team-1'), time: 100);
    await post('new', viewIn: viewInFor('team-1'), time: 300);
    await post('server', viewableBy: 'teams', viewableId: 'team-1', time: 200);

    final feed = await db.newsDao.watchTopLevelByTeam('team-1').first;
    expect(feed.map((r) => r.id), ['new', 'server', 'old']);
    // The invariant the badge depends on: the watermark is written from the
    // feed's own length, so a count taken from a different population can
    // never agree with it. Kotlin keeps these two on one predicate; so does
    // this.
    expect(await db.newsDao.countTopLevelByTeam('team-1'), feed.length);
  });

  // The `teamVoicesProvider has not yet moved onto watchTopLevelByTeam`
  // group that stood here has been **retired** — by expiring, as intended.
  //
  // `lib/providers/voices_provider.dart` was not Lane 1's file, so the DAO
  // statement landed without its caller and the group held the gap open with
  // a failure message naming the replacement. `teamVoicesProvider` is now
  // `dao.watchTopLevelByTeam(teamId)` and the group failed in both
  // directions, exactly as written.
  //
  // The predicate tests above are the durable half: they are what stop the
  // feed and the badge drifting apart again, which is the defect that made
  // this file necessary.
}
