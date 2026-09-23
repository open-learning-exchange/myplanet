import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// `ApkLogDao` — the port of `data/room/dao/ApkLogDao.kt`.
///
/// The Kotlin file has **two** `@Query` statements, not the four the Phase 160
/// brief claimed: `getPending` and `getExistingIds`, plus two `@Insert`, one
/// partial `@Update` and the `@Transaction` that wraps the last two.
void main() {
  late AppDatabase db;
  late ApkLogDao dao;

  setUp(() {
    db = AppDatabase.memory();
    dao = db.apkLogDao;
  });
  tearDown(() => db.close());

  Future<void> insert(String id, {String rev = '', String type = 'crash'}) =>
      dao.insert(
        ApkLogsCompanion.insert(
          id: id,
          rev: Value(rev),
          type: Value(type),
          error: const Value('boom'),
          time: const Value('1000'),
        ),
      );

  group('pendingUploads', () {
    test('is every row the server has not acknowledged', () async {
      await insert('a');
      await insert('b', rev: '1-abc');
      await insert('c');

      final pending = await dao.pendingUploads();

      expect(
        pending.map((row) => row.id).toSet(),
        {'a', 'c'},
        reason:
            'Kotlin is `SELECT * FROM apk_log WHERE _rev IS NULL`; the port '
            'stores "" for that and tests `_rev = ""`',
      );
    });

    test('a freshly inserted row is pending at the column default', () async {
      // The column default is what decides this, which is why it is stated at
      // `ApkLogs.rev`. Phase 156's `team_activities` is the cautionary tale:
      // a default that says "pending" is only safe while nothing but the
      // uploader writes the column, and this table has no pull direction.
      await dao.insert(ApkLogsCompanion.insert(id: 'fresh'));

      expect((await dao.pendingUploads()).map((row) => row.id), ['fresh']);
    });
  });

  group('markUploadedBatch', () {
    test('writes the revision and dequeues the row', () async {
      await insert('a');

      final unapplied = await dao.markUploadedBatch({'a': '1-abc'});

      expect(unapplied, isEmpty);
      expect((await dao.getById('a'))!.rev, '1-abc');
      expect(await dao.pendingUploads(), isEmpty);
    });

    test('reports the ids that matched no row', () async {
      // The contract the Kotlin `@Transaction` exists for:
      // `markUploadedBatchInternal` returns `Unit`, so Room discards the
      // affected-row count and the pre-read is the only way to know what
      // matched. `UploadConfigs.CrashLog` turns the returned ids back into
      // upload failures rather than recording a delivery that did not happen.
      await insert('a');

      final unapplied = await dao.markUploadedBatch({
        'a': '1-abc',
        'gone': '1-def',
      });

      expect(unapplied, {'gone'});
      expect(
        (await dao.getById('a'))!.rev,
        '1-abc',
        reason: 'the ids that did match are still applied',
      );
    });

    test(
      'writes only the revision, leaving every other column alone',
      () async {
        // Room generates `UPDATE apk_log SET _rev = ? WHERE id = ?` from the
        // partial `UploadUpdate(id, _rev)` POJO. Here the partial write is the
        // faithful port, not the trap this codebase usually warns about: a
        // whole-row update would be the defect.
        await dao.insert(
          ApkLogsCompanion.insert(
            id: 'a',
            userId: const Value('user-1'),
            type: const Value('crash'),
            error: const Value('stack trace'),
            parentCode: const Value('parent'),
            version: const Value('0.72.69'),
            createdOn: const Value('planet'),
            time: const Value('1700'),
          ),
        );

        await dao.markUploadedBatch({'a': '2-xyz'});

        final row = (await dao.getById('a'))!;
        expect(row.userId, 'user-1');
        expect(row.type, 'crash');
        expect(row.error, 'stack trace');
        expect(row.parentCode, 'parent');
        expect(row.version, '0.72.69');
        expect(row.createdOn, 'planet');
        expect(row.time, '1700');
      },
    );

    test('an empty batch touches nothing', () async {
      await insert('a');
      expect(await dao.markUploadedBatch(const {}), isEmpty);
      expect((await dao.getById('a'))!.rev, '');
    });

    test('chunks the existence read past the SQLite variable limit', () async {
      // `ids.chunked(900)` in the Kotlin, for SQLite's historical 999-variable
      // ceiling on `IN (:ids)`. Drift binds the same way and the port keeps the
      // number so the two chunk alike.
      //
      // **The first cut of this test seeded 2000 ids and was green with the
      // chunking removed** — the Phase 156 shape, a fixture that cannot
      // distinguish, found by mutating rather than by re-reading. This build's
      // real ceiling is 32766 (probed: 32766 prepares, 32767 throws
      // `SqliteException(1): too many SQL variables`), so a test below it
      // exercises no chunking at all. Hence the size: it is the smallest that
      // makes the claim testable, not an arbitrary large number.
      const total = 33000;
      await insert('present');
      final updates = {
        for (var i = 0; i < total; i++) 'row-$i': '1-rev',
        'present': '1-rev',
      };

      final unapplied = await dao.markUploadedBatch(updates);

      expect(unapplied.length, total);
      expect(unapplied.contains('present'), isFalse);
      expect((await dao.getById('present'))!.rev, '1-rev');
    });
  });

  test('insertAll writes the whole batch', () async {
    await dao.insertAll([
      ApkLogsCompanion.insert(id: 'a', type: const Value('crash')),
      ApkLogsCompanion.insert(id: 'b', type: const Value('anr')),
    ]);

    expect((await dao.pendingUploads()).length, 2);
  });

  test('inserting the same id twice replaces rather than duplicates', () async {
    // `@Insert(onConflict = REPLACE)`, and the port's crash path depends on it:
    // a report stored directly and then swept from its file lands on the same
    // `CrashLogStore.rowIdFor` key, and the sweep must be a no-op rather than a
    // second report.
    await insert('a', type: 'crash');
    await dao.insertAll([
      ApkLogsCompanion.insert(id: 'a', type: const Value('crash')),
    ]);

    expect(await dao.countAll(), 1);
  });
}
