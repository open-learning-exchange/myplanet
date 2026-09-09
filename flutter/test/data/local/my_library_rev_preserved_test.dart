import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/my_library_mapper.dart';

/// Phase 150, Job 1 — found by the ground-truth audit while checking what
/// preserving `my_library` would make immortal.
///
/// `my_library` has two writers, and only one of them sees a `_rev`. The
/// `resources` walk pulls whole documents; the courses walk pulls the
/// *thinner* copy embedded in a course step, and a sub-object carries no
/// `_rev`. `MyLibraryMapper.fromDoc` wrote `rev: Value(getStringOrNull(...))`
/// unconditionally, so ingesting a step's resource **nulled the revision the
/// resources walk had recorded** — the same two-writers-one-column shape
/// Phase 146 fixed for `resourceRemoteAddress`/`resourceLocalAddress` and
/// left on `rev`.
///
/// What that costs, in order of severity:
///
///  * `MyLibraryDao.deleteNotIn` prunes only `_rev IS NOT NULL AND _rev != ''`
///    (Kotlin's own eligibility), so a blanked row can never be evicted. With
///    `my_library` preserved from this phase on, the schema bump no longer
///    sweeps it either, and a resource deleted server-side would sit in the
///    public catalog for good.
///  * `watchResourcesNeedingUpdateCount` compares `_rev IS NOT
///    downloaded_rev`, so blanking `_rev` on a downloaded resource makes the
///    bell claim it needs downloading again.
///
/// **A deliberate deviation from the Kotlin**, and the direction matters:
/// `MyLibrary.insertMyLibrary` assigns `_rev = JsonUtils.getString("_rev",
/// doc)` unconditionally too (`MyLibrary.kt:237`), and `getString` returns
/// `""` for a missing key, so Kotlin writes an *empty* revision onto the
/// stored row and its prune spares `_rev != ''` — the same defect, reached the
/// same way. Kotlin's Room bump sweeps the wreckage; the port's no longer
/// will, which is what turns a self-healing quirk into an immortal row. Fixed
/// rather than copied, and recorded here because a later parity audit would
/// otherwise read it as drift.
void main() {
  late AppDatabase db;

  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  const couchDbUrl = 'https://satellite:1234@planet.example';

  /// The full document, as the `resources` database serves it.
  const serverDoc = {
    '_id': 'resource-1',
    '_rev': '3-abc',
    'title': 'Rainfall patterns',
    'private': false,
    '_attachments': {
      'rainfall.pdf': {'content_type': 'application/pdf', 'length': 12},
    },
  };

  /// The same resource as a course step embeds it: no `_rev`, no
  /// `_attachments`.
  const stepSubObject = {
    '_id': 'resource-1',
    'title': 'Rainfall patterns',
    'private': false,
  };

  Future<void> pull(Map<String, dynamic> doc, {String? stepId}) async {
    final row = MyLibraryMapper.fromDoc(
      doc,
      couchDbUrl: couchDbUrl,
      stepId: stepId,
      courseId: stepId == null ? null : 'course-1',
    );
    expect(row, isA<MyLibraryTableCompanion>());
    await db.myLibraryDao.upsertAll([row!]);
  }

  test(
    'ingesting a step resource keeps the revision the walk recorded',
    () async {
      await pull(serverDoc);
      expect((await db.myLibraryDao.getById('resource-1'))!.rev, '3-abc');

      await pull(stepSubObject, stepId: 'course-1:0');

      expect(
        (await db.myLibraryDao.getById('resource-1'))!.rev,
        '3-abc',
        reason: 'a step sub-object carries no _rev and must not clear one',
      );
    },
  );

  test('a blanked revision would make the row unprunable', () async {
    // The consequence, asserted in the language the prune uses rather than
    // trusting the column read above — this is the assertion that says *why*
    // the revision matters, and it is what preservation makes permanent.
    await pull(serverDoc);
    await pull(stepSubObject, stepId: 'course-1:0');

    await db.myLibraryDao.deleteNotIn(const ['resource-2']);

    expect(
      await db.myLibraryDao.getAll(),
      isEmpty,
      reason:
          'the server no longer lists it, so the resources walk must be able '
          'to evict it',
    );
  });

  test('a step-only resource still arrives with no revision', () async {
    // The other half: absent must mean "leave it alone", not "write null" —
    // but on an *insert* there is nothing to leave alone, and the column
    // default is what a row with no server document should carry.
    await pull(stepSubObject, stepId: 'course-1:0');

    final row = await db.myLibraryDao.getById('resource-1');
    expect(row!.rev, equals(null));
    expect(row.stepId, 'course-1:0');
  });

  test('a document that carries an explicit null revision clears it', () async {
    // `_presentOrAbsent` keys on the *key*, not the value, so a server that
    // sends `"_rev": null` is still honoured — otherwise the guard would be a
    // blanket "never write a null rev", which is a different rule.
    await pull(serverDoc);

    await pull({'_id': 'resource-1', '_rev': null, 'title': 'Rainfall'});

    expect((await db.myLibraryDao.getById('resource-1'))!.rev, equals(null));
  });
}
