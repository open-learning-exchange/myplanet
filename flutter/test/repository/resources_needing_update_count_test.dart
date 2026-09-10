import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/resources_repository.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Port tests for `ResourcesRepositoryImpl.countLibrariesNeedingUpdate`
/// (`:213-216`) and the DAO query behind it,
/// `MyLibraryDao.countPublicNeedingUpdateForUserPattern`
/// (`MyLibraryDao.kt:119-124`):
///
/// ```sql
/// SELECT COUNT(*) FROM my_library WHERE isPrivate = 0
///   AND userId LIKE :userPattern ESCAPE '\'
///   AND (resourceOffline = 0
///        OR (resourceLocalAddress IS NOT NULL AND _rev IS NOT downloadedRev))
/// ```
void main() {
  late AppDatabase db;
  late ResourcesRepository repository;

  const userId = 'org.couchdb.user:ada';

  setUp(() {
    db = AppDatabase.memory();
    repository = ResourcesRepository(
      _MockPlanetApi(),
      db.myLibraryDao,
      db.removedLogDao,
    );
  });

  tearDown(() => db.close());

  /// The production path — there is no `Future` form to test against, by
  /// design: the dashboard watches, so the tests watch too.
  Future<int> countNeedingUpdate(String? userId) =>
      repository.watchResourcesNeedingUpdateCount(userId).first;

  Future<void> insert(
    String id, {
    List<String> userId = const [],
    bool isPrivate = false,
    bool offline = false,
    String? localAddress,
    String? rev,
    String? downloadedRev,
  }) => db.myLibraryDao.upsertAll([
    MyLibraryTableCompanion.insert(
      id: id,
      userId: Value(userId),
      isPrivate: Value(isPrivate),
      resourceOffline: Value(offline),
      resourceLocalAddress: Value(localAddress),
      rev: Value(rev),
      downloadedRev: Value(downloadedRev),
    ),
  ]);

  group('the shelf scope', () {
    test('counts only the signed-in user\'s shelf', () async {
      await insert('mine', userId: const [userId]);
      await insert('theirs', userId: const ['org.couchdb.user:bob']);
      await insert('nobodys');

      expect(await countNeedingUpdate(userId), 1);
    });

    test(
      'is the shelf predicate, not the catalog one — a resource on nobody\'s '
      'shelf is not counted',
      () async {
        // The distinction this pins: `countPublicNeedingUpdateForUserPattern`
        // uses `userId LIKE`, while `getPublicNotUserPattern` immediately below
        // it in the same Kotlin DAO uses `userId IS NULL OR userId NOT LIKE`.
        // `PHASE_130_NOTES.md` cited the second for the first. Under the
        // catalog predicate this fixture would count 2 (`theirs` + `nobodys`)
        // and exclude the user's own.
        await insert('mine', userId: const [userId]);
        await insert('theirs', userId: const ['org.couchdb.user:bob']);
        await insert('nobodys');

        expect(await countNeedingUpdate(userId), 1);
      },
    );

    test('counts a shelf shared with other users', () async {
      await insert('shared', userId: const ['org.couchdb.user:bob', userId]);
      expect(await countNeedingUpdate(userId), 1);
    });

    test('excludes private resources', () async {
      await insert('pub', userId: const [userId]);
      await insert('priv', userId: const [userId], isPrivate: true);
      expect(await countNeedingUpdate(userId), 1);
    });
  });

  group('the download predicate', () {
    test('counts a resource that was never downloaded', () async {
      await insert('a', userId: const [userId]);
      expect(await countNeedingUpdate(userId), 1);
    });

    test('counts a downloaded resource the server has moved past', () async {
      await insert(
        'a',
        userId: const [userId],
        offline: true,
        localAddress: '/tmp/a',
        rev: '4-def',
        downloadedRev: '3-abc',
      );
      expect(await countNeedingUpdate(userId), 1);
    });

    test('does not count a downloaded resource that is current', () async {
      await insert(
        'a',
        userId: const [userId],
        offline: true,
        localAddress: '/tmp/a',
        rev: '3-abc',
        downloadedRev: '3-abc',
      );
      expect(await countNeedingUpdate(userId), 0);
    });

    test(
      'counts a downloaded resource whose downloadedRev was never stamped',
      () async {
        // The `IS NOT` trap, and the reason the Kotlin does not write `!=`.
        // SQLite's `IS NOT` is the **null-safe** inequality: `'3-abc' IS NOT
        // NULL` is true, so this row counts. Written `_rev != downloadedRev`
        // the comparison would be SQL NULL — falsy — and every row an older
        // build marked offline without stamping `downloadedRev` would silently
        // drop out of the count.
        //
        // This is the one assertion in the file that a plausible mis-port
        // passes everything else while failing.
        await insert(
          'a',
          userId: const [userId],
          offline: true,
          localAddress: '/tmp/a',
          rev: '3-abc',
        );
        expect(await countNeedingUpdate(userId), 1);
      },
    );

    test('does not count a local resource with neither rev', () async {
      // A locally-created resource that has never been uploaded carries a null
      // `_rev`; `NULL IS NOT NULL` is false, so it is not "needing update".
      await insert(
        'a',
        userId: const [userId],
        offline: true,
        localAddress: '/tmp/a',
      );
      expect(await countNeedingUpdate(userId), 0);
    });

    test(
      'does not count a resource flagged offline with no local address',
      () async {
        // `resource_local_address IS NOT NULL` is an `AND` *inside* the `OR`,
        // not a third top-level case. Note the column holds the CouchDB
        // attachment *name*, not a path — `MyLibraryMapper._attachmentOf`
        // writes it on every sync — so a synced resource normally has one and
        // this fixture (offline with no attachment at all) is a shape only a
        // hand-built row reaches. It is here to pin the `AND`, not to describe
        // a state the sync produces.
        await insert(
          'a',
          userId: const [userId],
          offline: true,
          rev: '9-zzz',
          downloadedRev: '1-aaa',
        );
        expect(await countNeedingUpdate(userId), 0);
      },
    );
  });

  group('the null guard', () {
    test('a null user id counts nothing', () async {
      await insert('a', userId: const [userId]);
      expect(await countNeedingUpdate(null), 0);
    });

    test('a blank user id reaches the query, as the Kotlin lets it', () async {
      // `countLibrariesNeedingUpdate` tests `userId == null`, **not**
      // `isNullOrBlank`, unlike `getMyLibrary`/`getMyLibraryFlow` above it. The
      // pattern becomes `%""%`, which matches nothing real — a serialized
      // `["a","b"]` renders its separator as `","`, never `""` — so the count is
      // 0 either way. Pinned because the guard as written is the port's, and a
      // future reader should see the difference is deliberate.
      await insert('a', userId: const [userId]);
      expect(await countNeedingUpdate(''), 0);
    });
  });

  group('the LIKE pattern', () {
    test('escapes a user id containing an underscore', () async {
      // `_` is LIKE's single-character wildcard. Unescaped, `%"a_b"%` matches
      // `a1b`, so one user's shelf would count another's resources. Kotlin
      // escapes `\`, `%` and `_` in `userIdPattern` (`:64-70`).
      await insert('mine', userId: const ['a_b']);
      await insert('theirs', userId: const ['a1b']);

      expect(await countNeedingUpdate('a_b'), 1);
      expect(await countNeedingUpdate('a1b'), 1);
    });

    test('escapes a user id containing a percent sign', () async {
      await insert('mine', userId: const ['a%b']);
      await insert('theirs', userId: const ['axxb']);

      expect(await countNeedingUpdate('a%b'), 1);
    });

    test('cannot match a user id containing a backslash — in either app', () {
      // Not a port bug: the two escaping layers do not compose, and they do not
      // compose in the Kotlin either.
      //
      // The column holds a JSON list, so `[r'a\b']` is *stored* as the eight
      // characters `["a\\b"]` — JSON doubles the backslash. The pattern goes
      // the other way: `userIdPattern` escapes the backslash for LIKE, and
      // `ESCAPE '\'` then unescapes it, so the query looks for the single
      // backslash `"a\b"`. It never matches what was written.
      //
      // Kotlin's Gson converter and `ResourcesRepositoryImpl.userIdPattern`
      // (`:64-70`) produce exactly the same mismatch, so reproducing it is
      // parity. It is unreachable in practice — a CouchDB user id is
      // `org.couchdb.user:<name>` — and the fix, if it were ever wanted, is to
      // escape against the *stored* JSON form rather than the raw id, in both
      // apps at once. Recorded in `PHASE_131_NOTES.md`.
      expect(jsonEncode([r'a\b']), r'["a\\b"]');
      expect(likeEscapedUserPattern(r'a\b'), r'%"a\\b"%');
    });
  });

  group('the reactive form', () {
    test('re-emits when the shelf changes', () async {
      await insert('a', userId: const [userId]);

      final counts = <int>[];
      final subscription = repository
          .watchResourcesNeedingUpdateCount(userId)
          .listen(counts.add);
      await pumpEventQueue();

      await insert('b', userId: const [userId]);
      await pumpEventQueue();

      // Downloading `a` takes it back out of the count.
      await db.myLibraryDao.markDownloaded('a', '/tmp/a', null);
      await pumpEventQueue();

      await subscription.cancel();
      expect(counts, [1, 2, 1]);
    });

    test('a null user id yields a single zero', () async {
      expect(await repository.watchResourcesNeedingUpdateCount(null).first, 0);
    });
  });
}
