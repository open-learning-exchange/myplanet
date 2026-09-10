import 'package:drift/drift.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// Phase 150, Job 4 (`PHASE_148_NOTES.md` item 2).
///
/// `HealthExaminationDao.markUploaded(id, rev)` wrote `rev: Value(rev)`
/// unconditionally, so a caller that has cleared the record's dirty flag but
/// has **no** revision to record — the shape Kotlin's health upload reaches
/// when a 2xx body carries `id` and no `rev` — erased the `_rev` the row
/// already held. The next edit then PUTs against a null revision and takes a
/// 409 for the life of the install.
///
/// Kotlin repaired the identical defect in `1004e90` by partitioning the
/// null-rev ids into `UPDATE health_examinations SET isUpdated = 0 WHERE _id
/// IN (:ids)` (`HealthExaminationDao.kt:30-46`), with the test
/// `markUploaded_rowsWithoutRev_clearsIsUpdatedAndPreservesExistingRev`. The
/// port's equivalent is `Value.absent()`, which drift omits from the `SET`
/// clause — the pattern `UserDao.markUploaded` and `AchievementDao
/// .markUploaded` already use.
void main() {
  late AppDatabase db;

  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  Future<void> seed({String? rev}) => db.healthExaminationDao.upsert(
    HealthExaminationsCompanion.insert(
      id: 'exam-1',
      couchId: const Value('exam-1'),
      rev: Value(rev),
      userId: const Value('org.couchdb.user:ada'),
      isUpdated: const Value(true),
    ),
  );

  Future<HealthExaminationRow> reread() async {
    final row = await db.healthExaminationDao.getById('exam-1');
    // `isNotNull` is ambiguous in a file importing drift — it exports its own.
    expect(row, isA<HealthExaminationRow>());
    return row!;
  }

  test('a null rev clears the dirty flag and keeps the stored one', () async {
    await seed(rev: '3-abc');

    await db.healthExaminationDao.markUploaded('exam-1', null);

    final row = await reread();
    expect(
      row.rev,
      '3-abc',
      reason: 'the revision is the only thing that lets the next edit PUT',
    );
    expect(row.isUpdated, isFalse, reason: 'the record is no longer pending');
  });

  test('a supplied rev still replaces the stored one', () async {
    // The half that must not regress: `Value.absent()` may only stand in for a
    // *null* argument. A version that skipped the write whenever the row
    // already had a revision would leave every update conflicting.
    await seed(rev: '3-abc');

    await db.healthExaminationDao.markUploaded('exam-1', '4-def');

    final row = await reread();
    expect(row.rev, '4-def');
    expect(row.isUpdated, isFalse);
  });

  test('a blank rev is written, not treated as absent', () async {
    // The null test is `== null`, not `isNullOrEmpty`, because Kotlin's
    // partition is `it.value == null` (`HealthExaminationDao.kt:37`): an empty
    // string goes to the single-id overload and is stored. Pinned so a
    // reimplementation cannot quietly widen the guard to blanks and start
    // preserving a revision Kotlin overwrites.
    await seed(rev: '3-abc');

    await db.healthExaminationDao.markUploaded('exam-1', '');

    expect((await reread()).rev, '');
  });

  test('a null rev on a row that never had one leaves it null', () async {
    await seed();

    await db.healthExaminationDao.markUploaded('exam-1', null);

    final row = await reread();
    expect(row.rev, equals(null));
    expect(row.isUpdated, isFalse);
  });
}
