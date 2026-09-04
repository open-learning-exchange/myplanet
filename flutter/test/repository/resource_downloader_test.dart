import 'dart:io';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/resource_downloader.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late ResourceDownloader downloader;
  late Directory tempDir;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() async {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    downloader = ResourceDownloader(api, database.myLibraryDao);
    tempDir = await Directory.systemTemp.createTemp('resource_files_test');
    ResourceFiles.baseDirectory = () async => tempDir;
  });

  tearDown(() async {
    ResourceFiles.baseDirectory = getApplicationDocumentsDirectoryFallback;
    await database.close();
    if (tempDir.existsSync()) await tempDir.delete(recursive: true);
  });

  Future<MyLibraryRow> seed({
    String id = 'res-1',
    String? couchId = 'doc-1',
    String? filename = 'lesson.pdf',
  }) async {
    await database.myLibraryDao.upsertAll([
      MyLibraryTableCompanion.insert(
        id: id,
        couchId: Value(couchId),
        rev: const Value('3-abc'),
        filename: Value(filename),
        title: const Value('Lesson'),
      ),
    ]);
    return (await database.myLibraryDao.getById(id))!;
  }

  void stubBody(List<int> bytes, {int status = 200}) {
    when(
      () => api.getBytes(
        any(),
        authHeader: any(named: 'authHeader'),
        onProgress: any(named: 'onProgress'),
      ),
    ).thenAnswer(
      (_) async => status == 200
          ? NetworkSuccess<List<int>>(bytes)
          : NetworkError<List<int>>(status, 'nope'),
    );
  }

  test('writes the attachment where the viewer looks for it', () async {
    final resource = await seed();
    stubBody(const [1, 2, 3, 4]);

    final result = await downloader.download(resource, config: config);

    expect(result, isA<NetworkSuccess<String>>());
    // The path the viewer resolves independently must be the same file.
    final found = await ResourceFiles.existingFileFor(
      docId: 'doc-1',
      filename: 'lesson.pdf',
    );
    expect(found, isNotNull);
    expect(await found!.readAsBytes(), [1, 2, 3, 4]);
  });

  test(
    'records the resource as offline with the revision it fetched',
    () async {
      final resource = await seed();
      stubBody(const [1, 2, 3]);

      await downloader.download(resource, config: config);

      final row = await database.myLibraryDao.getById('res-1');
      expect(row!.resourceOffline, isTrue);
      expect(row.resourceLocalAddress, isNotNull);
      // Lets a later sync notice the server replaced the attachment.
      expect(row.downloadedRev, '3-abc');
    },
  );

  test(
    'two resources sharing a filename do not overwrite each other',
    () async {
      // Attachments are routinely named index.html or video.mp4; a flat
      // `ole/<filename>` layout makes one resource clobber the other.
      final first = await seed(id: 'res-1', couchId: 'doc-1');
      final second = await seed(id: 'res-2', couchId: 'doc-2');

      stubBody(const [1, 1, 1]);
      await downloader.download(first, config: config);
      stubBody(const [2, 2, 2]);
      await downloader.download(second, config: config);

      final a = await ResourceFiles.existingFileFor(
        docId: 'doc-1',
        filename: 'lesson.pdf',
      );
      final b = await ResourceFiles.existingFileFor(
        docId: 'doc-2',
        filename: 'lesson.pdf',
      );
      expect(await a!.readAsBytes(), [1, 1, 1]);
      expect(await b!.readAsBytes(), [2, 2, 2]);
    },
  );

  test('an empty body is a failure, not an empty file', () async {
    final resource = await seed();
    stubBody(const []);

    final result = await downloader.download(resource, config: config);

    expect(result, isA<NetworkError<String>>());
    // Writing it would have created a file that `exists()` accepts and every
    // viewer then renders as blank.
    expect(
      await ResourceFiles.existingFileFor(
        docId: 'doc-1',
        filename: 'lesson.pdf',
      ),
      isNull,
    );
    expect(
      (await database.myLibraryDao.getById('res-1'))!.resourceOffline,
      isFalse,
    );
  });

  test('a failed request leaves the resource un-downloaded', () async {
    final resource = await seed();
    stubBody(const [], status: 404);

    expect(
      await downloader.download(resource, config: config),
      isA<NetworkError<String>>(),
    );
    expect(
      (await database.myLibraryDao.getById('res-1'))!.resourceOffline,
      isFalse,
    );
  });

  test('a resource with no filename is not downloadable', () async {
    final resource = await seed(filename: null);
    expect(ResourceDownloader.urlFor(config, resource), isNull);
    expect(
      await downloader.download(resource, config: config),
      isA<NetworkError<String>>(),
    );
  });

  test('a zero-length file on disk does not count as downloaded', () async {
    // What an interrupted download leaves behind.
    final file = await ResourceFiles.fileFor(
      docId: 'doc-1',
      filename: 'lesson.pdf',
    );
    await file.parent.create(recursive: true);
    await file.writeAsBytes(const []);

    expect(
      await ResourceFiles.existingFileFor(
        docId: 'doc-1',
        filename: 'lesson.pdf',
      ),
      isNull,
    );
  });

  test('a traversing path stays inside the base directory', () async {
    final file = await ResourceFiles.fileFor(
      docId: '../../etc',
      filename: '../../passwd',
    );
    expect(file.path.startsWith(tempDir.path), isTrue);
    expect(file.path, isNot(contains('..')));
  });

  test('the offline flag is cleared when the file is gone', () async {
    final resource = await seed();
    stubBody(const [9, 9]);
    await downloader.download(resource, config: config);

    final file = await ResourceFiles.existingFileFor(
      docId: 'doc-1',
      filename: 'lesson.pdf',
    );
    await file!.delete();
    await database.myLibraryDao.markNotDownloaded('res-1');

    final row = await database.myLibraryDao.getById('res-1');
    expect(row!.resourceOffline, isFalse);
    expect(row.resourceLocalAddress, isNull);
  });
}

Future<Directory> getApplicationDocumentsDirectoryFallback() async =>
    Directory.systemTemp;

class MockPlanetApi extends Mock implements PlanetApi {}
