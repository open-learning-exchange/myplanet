import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// `MyLibraryDao.deleteNotIn` is the port of Kotlin's `removeDeletedResources`,
/// and for a long time it was not: Kotlin prunes only rows the server has
/// actually seen — `WHERE _rev IS NOT NULL AND _rev != '' AND isPrivate = 0`
/// (`MyLibraryDao.kt:170-178`) — while the port deleted anything the walk had
/// not listed.
///
/// Three rows are destroyed by the unguarded version, and the first is the one
/// that matters: `ResourcesRepository.saveLocalResource` writes a row with no
/// `rev`, no CouchDB document and no outbox entry, so nothing can give it back.
/// The third is why this sits beside the course-resource work rather than in
/// its own phase — it decides whether a course step's stamp is durable or a
/// one-sync artefact.
void main() {
  late AppDatabase db;

  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  Future<void> seed() => db.myLibraryDao.upsertAll([
    // Created offline through the add-resource screen: no rev, ever.
    MyLibraryTableCompanion.insert(
      id: 'local-1',
      title: const Value('My own resource'),
    ),
    // A row that reached the server but whose rev came back empty.
    MyLibraryTableCompanion.insert(id: 'blank-rev-1', rev: const Value('')),
    // A private team resource the public walk never lists.
    MyLibraryTableCompanion.insert(
      id: 'private-1',
      rev: const Value('1-a'),
      isPrivate: const Value(true),
      privateFor: const Value('team-1'),
    ),
    // An ordinary synced resource the walk no longer lists.
    MyLibraryTableCompanion.insert(id: 'stale-1', rev: const Value('1-a')),
    MyLibraryTableCompanion.insert(id: 'server-1', rev: const Value('1-a')),
  ]);

  Future<Set<String>> remaining() async =>
      (await db.myLibraryDao.getAll()).map((r) => r.id).toSet();

  test('the prune removes only public rows the server has seen', () async {
    await seed();

    final deleted = await db.myLibraryDao.deleteNotIn(['server-1']);

    expect(deleted, 1, reason: 'only stale-1 is eligible');
    expect(
      remaining(),
      completion({'local-1', 'blank-rev-1', 'private-1', 'server-1'}),
    );
  });

  test('the empty-walk branch carries the same guards', () async {
    // `total_rows == 0` calls `deleteNotIn(const [])`. Kotlin's counterpart
    // `deleteAllStalePublic` is guarded identically — but it is also
    // **unreachable** (`removeDeletedResources` runs only when the id list is
    // non-empty, `SyncManager.kt:416`), so on an empty walk Kotlin deletes
    // nothing at all and the port still deletes the synced public rows. The
    // guards narrow the blast radius; they do not make this branch parity.
    await seed();

    await db.myLibraryDao.deleteNotIn(const []);

    expect(remaining(), completion({'local-1', 'blank-rev-1', 'private-1'}));
  });

  test('a course resource with no rev of its own is not pruned away', () async {
    // A step's embedded sub-object is not obliged to carry `_rev`. Kotlin's
    // `_rev != ''` clause spares it; the port used to delete it on the next
    // resources sync, taking the freshly written step join with it.
    await db.myLibraryDao.upsertAll([
      MyLibraryTableCompanion.insert(
        id: 'course-res-1',
        stepId: const Value('course-1:0'),
        courseId: const Value('course-1'),
      ),
    ]);

    await db.myLibraryDao.deleteNotIn(['something-else']);

    final row = await db.myLibraryDao.getById('course-res-1');
    expect(row, isNotNull);
    expect(row!.stepId, 'course-1:0');
  });
}
