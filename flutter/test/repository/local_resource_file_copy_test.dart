import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/local_resource_request.dart';
import 'package:myplanet/repository/resources_repository.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Phase 150, Job 1 — found by the ground-truth audit, and it is the reason
/// preserving the row is not on its own enough.
///
/// Kotlin copies the picked file into app storage *before* writing the row,
/// and stores only its basename:
///
/// ```kotlin
/// val destinationFile = FileUtils.getLibraryFile(externalFilesDir, id, filename)
/// sourceFile.copyTo(destinationFile, overwrite = true)
/// …
/// this.resourceLocalAddress = filename
/// ```
/// `ResourcesRepositoryImpl.kt:235-256`, `:278-280`; `FileUtils.kt:47-49` is
/// `<ext>/ole/<id>/<basename>` — the same convention [ResourceFiles] uses.
///
/// The port wrote **no file at all**: it stored the raw path `FilePicker`
/// handed the screen and set `resourceOffline = true` on top of it. So the
/// resource the user created was unopenable, and opening it made things
/// worse — `_getLocalFilePath()` returns null, the viewer's stale-flag repair
/// fires and destroys `resourceOffline` *and* `resourceLocalAddress`, and the
/// Download button it then offers cannot work either because `urlFor` needs a
/// `couchId` a local row has never had.
///
/// Preserving such a row across a schema bump preserves the claim rather than
/// the resource, which is why this lands in the same phase.
void main() {
  late AppDatabase db;
  late ResourcesRepository repository;
  late Directory sandbox;
  late Directory source;

  setUp(() async {
    db = AppDatabase.memory();
    repository = ResourcesRepository(
      _MockPlanetApi(),
      db.myLibraryDao,
      db.removedLogDao,
    );
    sandbox = await Directory.systemTemp.createTemp('phase150-docs');
    source = await Directory.systemTemp.createTemp('phase150-pick');
    ResourceFiles.baseDirectory = () async => sandbox;
  });
  tearDown(() async {
    ResourceFiles.baseDirectory = getApplicationDocumentsDirectoryStub;
    await db.close();
    await sandbox.delete(recursive: true);
    await source.delete(recursive: true);
  });

  Future<File> pickedFile({
    String name = 'well-survey.pdf',
    String contents = 'water table falling',
  }) async {
    final file = File('${source.path}/$name');
    await file.writeAsString(contents);
    return file;
  }

  Future<LocalResourceError?> save(String? path) =>
      repository.saveLocalResource(
        LocalResourceRequest(
          title: 'Well survey notes',
          userId: 'org.couchdb.user:ada',
          resourceUrl: path,
          mediaType: 'pdf',
        ),
      );

  test('the picked file is copied where the viewer looks for it', () async {
    final picked = await pickedFile();

    expect(await save(picked.path), equals(null));

    final row = (await db.myLibraryDao.getAll()).single;
    final stored = await ResourceFiles.existingFileFor(
      docId: row.couchId ?? row.id,
      filename: row.filename ?? '',
    );
    expect(
      stored,
      isA<File>(),
      reason:
          'the viewer resolves <base>/ole/<couchId ?? id>/<filename>; a row '
          'claiming resourceOffline with nothing there is unopenable',
    );
    expect(await stored!.readAsString(), 'water table falling');
  });

  test('the stored address is the basename, as Kotlin stores it', () async {
    final picked = await pickedFile();

    await save(picked.path);

    final row = (await db.myLibraryDao.getAll()).single;
    expect(row.resourceLocalAddress, 'well-survey.pdf');
    expect(row.filename, 'well-survey.pdf');
    expect(
      row.resourceLocalAddress,
      isNot(contains(source.path)),
      reason: 'a picker path is a cache location that need not outlive the app',
    );
  });

  test('the copy survives the picked file being cleared away', () async {
    // The point of copying rather than pointing: `FilePicker` hands back a
    // path in a cache directory the OS may reclaim.
    final picked = await pickedFile();
    await save(picked.path);
    await picked.delete();

    final row = (await db.myLibraryDao.getAll()).single;
    final stored = await ResourceFiles.existingFileFor(
      docId: row.couchId ?? row.id,
      filename: row.filename ?? '',
    );
    expect(stored, isA<File>());
  });

  test('a picked file that is gone fails the save and writes no row', () async {
    final error = await save('${source.path}/never-existed.pdf');

    expect(error, LocalResourceError.fileNotFound);
    expect(
      await db.myLibraryDao.getAll(),
      isEmpty,
      reason: 'Kotlin returns before building the entity, so no row is left',
    );
  });

  test('no picked file at all still saves a metadata-only row', () async {
    // A divergence, kept deliberately. Kotlin requires a file
    // (`ResourcesRepositoryImpl.kt:235-238` fails on a null `resourceUrl`),
    // and the port's form does not, so rejecting one here would change what
    // the screen accepts rather than fixing what it stores. Pinned so the
    // divergence is a decision rather than an accident.
    expect(await save(null), equals(null));

    final row = (await db.myLibraryDao.getAll()).single;
    expect(row.resourceLocalAddress, equals(null));
    expect(
      row.resourceOffline,
      isFalse,
      reason: 'nothing is on disk, so the row must not claim otherwise',
    );
  });

  test('two resources with the same filename do not collide', () async {
    // `ResourceFiles.fileFor` keys the directory on the doc id for exactly
    // this reason, and a locally created row has no `couchId`, so the id it
    // keys on is the generated one.
    final first = await pickedFile(name: 'scan.pdf', contents: 'first');
    expect(await save(first.path), equals(null));

    final second = await pickedFile(name: 'scan.pdf', contents: 'second');
    expect(
      await repository.saveLocalResource(
        LocalResourceRequest(
          title: 'Another survey',
          userId: 'org.couchdb.user:ada',
          resourceUrl: second.path,
        ),
      ),
      equals(null),
    );

    final rows = await db.myLibraryDao.getAll();
    expect(rows, hasLength(2));
    final contents = <String>[];
    for (final row in rows) {
      final file = await ResourceFiles.existingFileFor(
        docId: row.couchId ?? row.id,
        filename: row.filename ?? '',
      );
      contents.add(await file!.readAsString());
    }
    expect(contents..sort(), ['first', 'second']);
  });
}

/// The production default, restored in `tearDown`. Named rather than inlined
/// because `ResourceFiles.baseDirectory` is a static seam: leaving a test's
/// sandbox installed leaks into every later test in the same shard.
Future<Directory> getApplicationDocumentsDirectoryStub() async =>
    throw UnsupportedError('resource base directory not overridden');
