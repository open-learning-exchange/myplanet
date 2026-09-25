import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/personals_repository.dart';

void main() {
  late AppDatabase database;
  late PersonalsRepository repository;
  var nextId = 0;

  setUp(() {
    database = AppDatabase.memory();
    nextId = 0;
    repository = PersonalsRepository(
      database.personalDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(1000 + nextId),
      createId: () => 'personal-${nextId++}',
    );
  });
  tearDown(() => database.close());

  /// Harvested from master `7a9bb12`, which gave `PersonalDao.kt:24` an
  /// `ORDER BY date DESC, title COLLATE NOCASE ASC` where it had none at all.
  ///
  /// The tie-break is the point, and it is the common case rather than the
  /// rare one: a note's date is the day it was written, so every note written
  /// on one day ties. Without the second term SQLite picks, and one device's
  /// list is not another's.
  ///
  /// Two decoys, both found by mutating the fix rather than by writing the
  /// test:
  ///
  /// * `zebra` is seeded **first** so insertion order and title order
  ///   disagree — otherwise the assertion holds whether or not the sort
  ///   exists at all.
  /// * The titles straddle the ASCII case boundary (`Banana` is `B`=66,
  ///   `apple` is `a`=97). A first cut used `Apple`/`mango`/`zebra`, which
  ///   sort **identically** with and without `COLLATE NOCASE`, so making the
  ///   tie-break case-sensitive left the suite green and the collation half
  ///   of the claim was pinned by nothing.
  test('orders same-date notes by title, case-insensitively', () async {
    for (final title in ['zebra', 'apple', 'Banana']) {
      await database.personalDao.upsert(
        PersonalEntriesCompanion.insert(
          id: 'p-$title',
          userId: 'user-1',
          title: title,
          titleNormalized: title.toLowerCase(),
          date: 5000,
        ),
      );
    }

    final rows = await repository.watch('user-1').first;
    expect(rows.map((row) => row.title), ['apple', 'Banana', 'zebra']);
  });

  test('a newer date still outranks the title tie-break', () async {
    await database.personalDao.upsert(
      PersonalEntriesCompanion.insert(
        id: 'p-old',
        userId: 'user-1',
        title: 'Apple',
        titleNormalized: 'apple',
        date: 1000,
      ),
    );
    await database.personalDao.upsert(
      PersonalEntriesCompanion.insert(
        id: 'p-new',
        userId: 'user-1',
        title: 'zebra',
        titleNormalized: 'zebra',
        date: 9000,
      ),
    );

    final rows = await repository.watch('user-1').first;
    expect(rows.map((row) => row.title), ['zebra', 'Apple']);
  });

  test('creates, edits, orders, and deletes offline personal items', () async {
    await repository.create(
      userId: 'user-1',
      userName: 'learner',
      title: ' First note ',
      description: ' private ',
    );
    await repository.create(
      userId: 'user-1',
      userName: 'learner',
      title: 'Second note',
    );

    var rows = await repository.watch('user-1').first;
    expect(rows.map((row) => row.id), ['personal-1', 'personal-0']);
    expect(rows.last.title, 'First note');
    expect(rows.last.description, 'private');
    expect(rows.every((row) => !row.isUploaded), isTrue);

    await repository.update(
      id: 'personal-0',
      title: 'Updated note',
      description: '',
    );
    rows = await repository.watch('user-1').first;
    final updated = rows.singleWhere((row) => row.id == 'personal-0');
    expect(updated.title, 'Updated note');
    expect(updated.description, isNull);

    expect(await repository.pendingUploads('user-1'), hasLength(2));
    await repository.delete('personal-1');
    expect(await repository.watch('user-1').first, hasLength(1));
  });

  test('enforces case-insensitive title uniqueness per user', () async {
    await repository.create(userId: 'user-1', userName: null, title: 'Journal');

    await expectLater(
      repository.create(userId: 'user-1', userName: null, title: ' journal '),
      throwsA(isA<DuplicatePersonalTitle>()),
    );
    await repository.create(userId: 'user-2', userName: null, title: 'Journal');
    expect(await repository.watch('user-2').first, hasLength(1));
  });

  test('recordRemoteDocRef adopts the ids without claiming the upload is '
      'finished', () async {
    // Port of `PersonalDao.updateRemoteDocRef`, which is
    // `updateUploadedStatus` with `isUploaded = 1` removed — and that one
    // missing assignment is the entire Phase 160 fix. It gives `my_personal` a
    // state it could not previously express: *the document landed, the
    // attachment did not*.
    await repository.create(userId: 'user-1', userName: 'ada', title: 'One');

    await repository.recordRemoteDocRef('personal-0', 'srv-1', '1-a');

    final row = await database.personalDao.getById('personal-0');
    expect(row?.couchId, 'srv-1');
    expect(row?.rev, '1-a');
    expect(
      row?.isUploaded,
      isFalse,
      reason:
          'the flag is what `pendingUploads` reads, so writing it here would '
          'take the note out of the set that retries the attachment — which '
          'is exactly how the file used to be lost',
    );
    expect(
      await repository.pendingUploads('user-1'),
      hasLength(1),
      reason: 'and the note is therefore still in that set',
    );

    // `markUploaded` is the second statement, and only it closes the note.
    await repository.markUploaded('personal-0', 'srv-1', '2-b');
    expect(
      (await database.personalDao.getById('personal-0'))?.isUploaded,
      isTrue,
    );
    expect(await repository.pendingUploads('user-1'), isEmpty);
  });

  group('editing an uploaded note', () {
    // Before Phase 160 `update` reset `isUploaded` unconditionally, so every
    // edit re-entered the upload queue — and since `serialize` carries no
    // `_id`, the handler POSTed a *second* CouchDB document and orphaned the
    // first. The skip-the-POST guard closes the duplicate; this narrowing is
    // what stops a title edit re-PUTting the whole attachment for nothing.
    setUp(() async {
      await repository.create(
        userId: 'user-1',
        userName: 'ada',
        title: 'Field notes',
        path: '/storage/notes/photo.jpg',
      );
      await repository.markUploaded('personal-0', 'srv-1', '1-a');
    });

    test('a title-only edit leaves the note delivered', () async {
      await repository.update(
        id: 'personal-0',
        title: 'Field notes v2',
        path: '/storage/notes/photo.jpg',
      );

      final row = await database.personalDao.getById('personal-0');
      expect(row?.title, 'Field notes v2');
      expect(
        row?.isUploaded,
        isTrue,
        reason:
            'the file on the server is still the file on the device, and '
            'neither app can publish the edited title — Kotlin never resets '
            'this flag at all — so re-opening the note would cost a full '
            'attachment re-PUT and achieve nothing',
      );
      expect(await repository.pendingUploads('user-1'), isEmpty);
    });

    test('attaching a different file re-opens the note for upload', () async {
      await repository.update(
        id: 'personal-0',
        title: 'Field notes',
        path: '/storage/notes/other.jpg',
      );

      expect(
        (await database.personalDao.getById('personal-0'))?.isUploaded,
        isFalse,
        reason:
            'Kotlin cannot edit a path at all, so it has no answer here; '
            'leaving a newly attached file undelivered would strand it',
      );
      expect(await repository.pendingUploads('user-1'), hasLength(1));
    });

    test('clearing the file re-opens the note too', () async {
      await repository.update(id: 'personal-0', title: 'Field notes');

      expect(
        (await database.personalDao.getById('personal-0'))?.isUploaded,
        isFalse,
      );
    });

    test('an un-uploaded note stays pending however it is edited', () async {
      // The guard against reading the narrowing as `isUploaded = !changed`:
      // an edit must never *grant* delivery.
      await repository.create(
        userId: 'user-1',
        userName: 'ada',
        title: 'Never sent',
        path: '/storage/notes/draft.jpg',
      );

      await repository.update(
        id: 'personal-1',
        title: 'Never sent, edited',
        path: '/storage/notes/draft.jpg',
      );

      expect(
        (await database.personalDao.getById('personal-1'))?.isUploaded,
        isFalse,
      );
    });
  });
}
