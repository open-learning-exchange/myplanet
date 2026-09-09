import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/my_library_mapper.dart';
import 'package:myplanet/repository/local_resource_request.dart';
import 'package:myplanet/repository/resources_repository.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Phase 150, Job 1 (`PHASE_146_NOTES.md` *Reported, not fixed* item 1).
///
/// `my_library` was treated as a pure CouchDB cache, and it is not one twice
/// over:
///
///  * `ResourcesRepository.saveLocalResource` writes a row that exists nowhere
///    else — no `_rev`, no document, and (unlike Kotlin, whose
///    `MyLibraryDao.getPendingUploads` is `WHERE _rev IS NULL` and feeds
///    `UploadManager.uploadResource`) no outbox row either. A sync cannot give
///    it back, which is the operative membership test for
///    [AppDatabase.localAuthorityTables].
///  * `resourceOffline` / `resourceLocalAddress` / `downloadedRev` are written
///    only when a download completes. Kotlin re-derives the flag from the disk
///    on **every** pull (`MyLibrary.insertMyLibrary`, `MyLibrary.kt:255-268`,
///    `FileUtils.checkFileExist`); [MyLibraryMapper.fromDoc] writes none of the
///    three, so a rebuilt table cannot recover them.
///
/// So before this phase a schema bump destroyed the user's own resource
/// outright and left every downloaded resource's bytes on disk with the table
/// claiming they were not there.
///
/// Nothing here fabricates a row: the local resource comes out of the
/// production `saveLocalResource`, and the synced one out of
/// [MyLibraryMapper.fromDoc] reading a document shaped like the server's.
void main() {
  late AppDatabase database;
  late ResourcesRepository resources;
  late Directory sandbox;
  late Directory picked;

  setUp(() async {
    database = AppDatabase.memory();
    resources = ResourcesRepository(
      _MockPlanetApi(),
      database.myLibraryDao,
      database.removedLogDao,
    );
    sandbox = await Directory.systemTemp.createTemp('phase150-bump-docs');
    picked = await Directory.systemTemp.createTemp('phase150-bump-pick');
    ResourceFiles.baseDirectory = () async => sandbox;
  });
  tearDown(() async {
    await database.close();
    await sandbox.delete(recursive: true);
    await picked.delete(recursive: true);
  });

  Future<void> runUpgrade({int? from}) async {
    await database.customStatement('SELECT 1');
    final migrator = database.createMigrator();
    await database.migration.onUpgrade(
      migrator,
      from ?? database.schemaVersion - 1,
      database.schemaVersion,
    );
  }

  const ada = 'org.couchdb.user:ada';

  /// A `resources` document the way the server sends one, with the single
  /// unnested `_attachments` key `insertMyLibrary` treats as the resource's own
  /// file.
  const serverDoc = {
    '_id': 'resource-1',
    '_rev': '3-abc',
    'title': 'Rainfall patterns',
    'private': false,
    '_attachments': {
      'rainfall.pdf': {'content_type': 'application/pdf', 'length': 12},
    },
  };

  Future<void> pullServerResource() async {
    final row = MyLibraryMapper.fromDoc(
      serverDoc,
      couchDbUrl: 'https://satellite:1234@planet.example',
      shelfId: ada,
    );
    expect(row, isA<MyLibraryTableCompanion>());
    await database.myLibraryDao.upsertAll([row!]);
  }

  Future<String> saveLocalResource() async {
    final source = File('${picked.path}/well-survey.pdf');
    await source.writeAsString('water table falling');
    final error = await resources.saveLocalResource(
      LocalResourceRequest(
        title: 'Well survey notes',
        userId: ada,
        addedBy: 'ada',
        resourceUrl: source.path,
        mediaType: 'pdf',
      ),
    );
    expect(error, equals(null), reason: 'the save must succeed');
    final rows = await database.myLibraryDao.getAll();
    final local = rows.where((row) => row.rev == null).toList();
    expect(local, hasLength(1));
    return local.single.id;
  }

  test('a resource the user created offline survives a schema bump', () async {
    final id = await saveLocalResource();

    await runUpgrade();

    final survivor = await database.myLibraryDao.getById(id);
    expect(
      survivor,
      isA<MyLibraryRow>(),
      reason:
          'nothing can give this row back — no document, no rev, no outbox row',
    );
    expect(survivor!.title, 'Well survey notes');
    expect(
      survivor.resourceLocalAddress,
      'well-survey.pdf',
      reason: 'the pointer to the copied file goes with it',
    );
    expect(
      await ResourceFiles.existingFileFor(
        docId: survivor.couchId ?? survivor.id,
        filename: survivor.filename ?? '',
      ),
      isA<File>(),
      reason: 'and the bytes are still where the viewer resolves them',
    );
    expect(
      await database.myLibraryDao.resourcesOnShelf(ada),
      hasLength(1),
      reason: 'and it is still on the shelf of the user who made it',
    );
  });

  test('a downloaded resource still says so after a schema bump', () async {
    await pullServerResource();
    await database.myLibraryDao.markDownloaded(
      'resource-1',
      'rainfall.pdf',
      '3-abc',
    );

    await runUpgrade();

    final row = await database.myLibraryDao.getById('resource-1');
    expect(row, isA<MyLibraryRow>());
    expect(
      row!.resourceOffline,
      isTrue,
      reason:
          'the bytes are still on disk, and no sync-in re-derives this flag the '
          'way insertMyLibrary does',
    );
    expect(row.resourceLocalAddress, 'rainfall.pdf');
    expect(
      row.downloadedRev,
      '3-abc',
      reason: 'losing this makes every already-current resource look stale',
    );
    expect(
      await database.myLibraryDao.watchResourcesNeedingUpdateCount(ada).first,
      0,
      reason: 'and the bell must not ask the user to download it again',
    );
  });

  test('the stale cache half is still evicted by the next walk', () async {
    // The other side of the trade preservation makes, and the reason it is
    // safe: the rows kept are exactly the rows the prune spares anyway.
    // A public synced row the server no longer lists is pruned after the bump
    // just as it was before it — preservation defers the eviction to the walk,
    // it does not cancel it.
    await pullServerResource();
    final localId = await saveLocalResource();

    await runUpgrade();
    expect(await database.myLibraryDao.getAll(), hasLength(2));

    await database.myLibraryDao.deleteNotIn(const ['resource-2']);

    expect(
      (await database.myLibraryDao.getAll()).map((row) => row.id),
      [localId],
      reason:
          'the cache row goes, the row with no server document behind it stays',
    );
  });

  test('a private team resource created offline also survives', () async {
    // `saveLocalResource` writes `userId: []` for a private team resource, so
    // the shelf assertions above cannot cover it — and `isPrivate = 1` is the
    // other half of what the prune spares.
    final source = File('${picked.path}/budget.pdf');
    await source.writeAsString('receipts');
    final error = await resources.saveLocalResource(
      LocalResourceRequest(
        title: 'Team budget scan',
        userId: ada,
        resourceUrl: source.path,
        isPrivateTeamResource: true,
        teamId: 'team-1',
      ),
    );
    expect(error, equals(null));

    await runUpgrade();

    final rows = await database.myLibraryDao.getAll();
    expect(rows, hasLength(1));
    expect(rows.single.title, 'Team budget scan');
    expect(rows.single.privateFor, 'team-1');
  });
}
