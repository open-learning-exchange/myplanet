import 'dart:io';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/resources_repository.dart';
import 'package:myplanet/repository/local_resource_request.dart';
import 'package:myplanet/ui/settings/storage_breakdown_screen.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late ResourcesRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = ResourcesRepository(api, db.myLibraryDao, db.removedLogDao);
  });

  tearDown(() => db.close());

  Map<String, dynamic> row(String id, String title) => {
    'id': id,
    'doc': {'_id': id, 'title': title},
  };

  void stubCount(int totalRows) {
    when(
      () => api.getJsonObject(
        '$dbUrl/resources/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': totalRows}),
    );
  }

  void stubPage(int skip, int limit, List<Map<String, dynamic>> rows) {
    when(
      () => api.getJsonObject(
        '$dbUrl/resources/_all_docs?include_docs=true&limit=$limit&skip=$skip',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rows': rows}),
    );
  }

  group('sync', () {
    test('pulls a single page and stores the resources', () async {
      stubCount(2);
      stubPage(0, 100, [row('res-1', 'First'), row('res-2', 'Second')]);

      final result = await repository.sync(config: config);

      expect(result, isA<SyncComplete>());
      expect((result as SyncComplete).savedCount, 2);
      expect(await repository.localCount(), 2);
    });

    test('walks multiple pages until the total is reached', () async {
      stubCount(150);
      stubPage(0, 100, List.generate(100, (i) => row('res-$i', 'Title $i')));
      stubPage(
        100,
        100,
        List.generate(50, (i) => row('res-${100 + i}', 'Title')),
      );

      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 150);
      expect(await repository.localCount(), 150);
    });

    test('reports progress as pages land', () async {
      stubCount(150);
      stubPage(0, 100, List.generate(100, (i) => row('res-$i', 'Title')));
      stubPage(
        100,
        100,
        List.generate(50, (i) => row('res-${100 + i}', 'Title')),
      );

      final progress = <int>[];
      await repository.sync(
        config: config,
        onProgress: (p) => progress.add(p.completed),
      );

      expect(progress, [100, 150]);
    });

    test('drops local rows the server no longer lists', () async {
      stubCount(2);
      stubPage(0, 100, [row('res-1', 'First'), row('res-2', 'Second')]);
      await repository.sync(config: config);
      expect(await repository.localCount(), 2);

      // Second sync: the server now only knows about res-1.
      stubCount(1);
      stubPage(0, 100, [row('res-1', 'First')]);
      await repository.sync(config: config);

      expect(await repository.localCount(), 1);
      final remaining = await db.myLibraryDao.getByIds(['res-1', 'res-2']);
      expect(remaining.map((r) => r.id), ['res-1']);
    });

    test('clears the table when the server reports no resources', () async {
      stubCount(1);
      stubPage(0, 100, [row('res-1', 'First')]);
      await repository.sync(config: config);

      stubCount(0);
      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 0);
      expect(await repository.localCount(), 0);
    });

    test('skips design documents and malformed rows', () async {
      stubCount(3);
      stubPage(0, 100, [
        row('res-1', 'Real'),
        {
          'id': '_design/resources',
          'doc': {'_id': '_design/resources'},
        },
        {'id': 'no-doc'},
      ]);

      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 1);
      expect(await repository.localCount(), 1);
    });

    test(
      'fails without touching the database when the count call fails',
      () async {
        when(
          () => api.getJsonObject(
            '$dbUrl/resources/_all_docs?limit=0',
            authHeader: any(named: 'authHeader'),
          ),
        ).thenAnswer(
          (_) async =>
              const NetworkError<Map<String, dynamic>>(401, 'Unauthorized'),
        );

        final result = await repository.sync(config: config);

        expect(result, isA<SyncFailed>());
        expect((result as SyncFailed).message, contains('401'));
        expect(await repository.localCount(), 0);
      },
    );

    test('continues past a failed batch and skips cleanup', () async {
      stubCount(150);
      stubPage(0, 100, List.generate(100, (i) => row('res-$i', 'Title')));
      when(
        () => api.getJsonObject(
          '$dbUrl/resources/_all_docs?include_docs=true&limit=100&skip=100',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async =>
            NetworkException<Map<String, dynamic>>(Exception('dropped')),
      );

      final result = await repository.sync(config: config);

      // The sync completes (not fails) — the first batch is saved and the
      // failed batch is skipped rather than aborting the whole walk.
      expect(result, isA<SyncComplete>());
      expect((result as SyncComplete).savedCount, 100);
      expect(await repository.localCount(), 100);
    });

    test('authenticates as the satellite account', () async {
      stubCount(0);
      await repository.sync(config: config);

      final header =
          verify(
                () => api.getJsonObject(
                  any(),
                  authHeader: captureAny(named: 'authHeader'),
                ),
              ).captured.first
              as String;
      expect(header, startsWith('Basic '));
    });
  });

  group('sync — partial page walk', () {
    test('keeps local rows when the walk ends early', () async {
      // Seed a full library.
      stubCount(150);
      stubPage(0, 100, List.generate(100, (i) => row('res-$i', 'T')));
      stubPage(100, 100, List.generate(50, (i) => row('res-${100 + i}', 'T')));
      await repository.sync(config: config);
      expect(await repository.localCount(), 150);

      // Now the server truncates mid-walk: page 2 comes back empty even though
      // the count promised more. savedIds is only a prefix of what exists.
      stubCount(150);
      stubPage(0, 100, List.generate(50, (i) => row('res-$i', 'T')));
      stubPage(50, 100, const []);
      await repository.sync(config: config);

      // Cleanup must be skipped — deleting everything outside the prefix would
      // throw away rows the server still has.
      expect(await repository.localCount(), 150);
    });
  });

  group('sync — batch failure', () {
    test(
      'skips cleanup so a failed batch does not delete valid rows',
      () async {
        // Seed a full library of 150 resources.
        stubCount(150);
        stubPage(0, 100, List.generate(100, (i) => row('res-$i', 'T')));
        stubPage(
          100,
          100,
          List.generate(50, (i) => row('res-${100 + i}', 'T')),
        );
        await repository.sync(config: config);
        expect(await repository.localCount(), 150);

        // Re-sync, but the second batch fails. The id list is then incomplete.
        stubCount(150);
        stubPage(0, 100, List.generate(100, (i) => row('res-$i', 'T')));
        when(
          () => api.getJsonObject(
            '$dbUrl/resources/_all_docs?include_docs=true&limit=100&skip=100',
            authHeader: any(named: 'authHeader'),
          ),
        ).thenAnswer(
          (_) async =>
              NetworkException<Map<String, dynamic>>(Exception('dropped')),
        );
        await repository.sync(config: config);

        // Cleanup must be skipped — the 50 resources in the failed batch are
        // still on the server and must not be deleted locally.
        expect(await repository.localCount(), 150);
      },
    );
  });

  group('sync — large libraries', () {
    test(
      'deletes stale rows when the kept set exceeds the SQLite bind limit',
      () async {
        // 1200 ids is past the 999-variable floor a single NOT IN would bind.
        stubCount(1200);
        stubPage(0, 100, List.generate(100, (i) => row('res-$i', 'T')));
        for (var skip = 100; skip < 1200; skip += 100) {
          stubPage(
            skip,
            100,
            List.generate(100, (i) => row('res-${skip + i}', 'T')),
          );
        }
        final result = await repository.sync(config: config);

        expect((result as SyncComplete).savedCount, 1200);
        expect(await repository.localCount(), 1200);

        // Shrink to a single resource; 1199 rows must be removed in chunks.
        stubCount(1);
        stubPage(0, 100, [row('res-0', 'T')]);
        await repository.sync(config: config);

        expect(await repository.localCount(), 1);
      },
    );
  });

  group('watchResources', () {
    test('emits an updated list when a sync writes new rows', () async {
      stubCount(1);
      stubPage(0, 100, [row('res-1', 'Álgebra')]);

      final emissions = <int>[];
      final subscription = repository.watchResources().listen(
        (rows) => emissions.add(rows.length),
      );

      await pumpEventQueue();
      await repository.sync(config: config);
      await pumpEventQueue();

      expect(emissions.first, 0);
      expect(emissions.last, 1);
      await subscription.cancel();
    });

    test('filters on the diacritic-folded title', () async {
      stubCount(2);
      stubPage(0, 100, [row('res-1', 'Álgebra'), row('res-2', 'Biology')]);
      await repository.sync(config: config);

      // Search without the accent still matches.
      final matches = await repository.watchResources(query: 'algebra').first;
      expect(matches.map((r) => r.title), ['Álgebra']);

      final none = await repository.watchResources(query: 'chemistry').first;
      expect(none, isEmpty);
    });

    test('returns everything for a blank query', () async {
      stubCount(2);
      stubPage(0, 100, [row('res-1', 'A'), row('res-2', 'B')]);
      await repository.sync(config: config);

      expect((await repository.watchResources(query: '  ').first).length, 2);
    });

    test('ranks a prefix match ahead of a substring match', () async {
      stubCount(2);
      stubPage(0, 100, [
        row('res-1', 'Advanced Math'),
        row('res-2', 'Math Basics'),
      ]);
      await repository.sync(config: config);

      final matches = await repository.watchResources(query: 'math').first;
      expect(matches.map((r) => r.title), ['Math Basics', 'Advanced Math']);
    });

    test('splits the query into words so order does not matter', () async {
      stubCount(2);
      stubPage(0, 100, [
        row('res-1', 'Basic Mathematics'),
        row('res-2', 'Chemistry'),
      ]);
      await repository.sync(config: config);

      final matches = await repository
          .watchResources(query: 'math basic')
          .first;
      expect(matches.map((r) => r.title), ['Basic Mathematics']);
    });

    test('catalog excludes private resources', () async {
      stubCount(1);
      stubPage(0, 100, [row('res-1', 'Public')]);
      await repository.sync(config: config);
      // A private resource owned by another team — it must never reach the
      // public catalog (the Kotlin `getPublic` filters `isPrivate = 0`).
      await db.myLibraryDao.upsertAll([
        MyLibraryTableCompanion.insert(
          id: 'res-private',
          title: const Value('Secret'),
          isPrivate: const Value(true),
        ),
      ]);

      final catalog = await repository.watchResources().first;
      expect(catalog.map((r) => r.title), ['Public']);
    });

    test('catalog excludes resources already on the user\'s shelf', () async {
      stubCount(1);
      stubPage(0, 100, [row('res-1', 'On Shelf')]);
      await repository.sync(config: config);
      await repository.setShelfMembership('res-1', 'user-1', joined: true);
      // Add a second public resource not on the shelf.
      await db.myLibraryDao.upsertAll([
        MyLibraryTableCompanion.insert(
          id: 'res-2',
          title: const Value('Catalog Only'),
        ),
      ]);

      final catalog = await repository
          .watchResources(shelfUserId: 'user-1', myLibrary: false)
          .first;
      expect(catalog.map((r) => r.title), ['Catalog Only']);
    });

    test('My Library includes the user\'s private team resources', () async {
      stubCount(0);
      stubPage(0, 100, const []);
      await repository.sync(config: config);
      await db.myLibraryDao.upsertAll([
        MyLibraryTableCompanion.insert(
          id: 'res-1',
          title: const Value('Team Private'),
          isPrivate: const Value(true),
          userId: const Value(['user-1']),
        ),
        MyLibraryTableCompanion.insert(
          id: 'res-2',
          title: const Value('Someone Else\'s Private'),
          isPrivate: const Value(true),
          userId: const Value(['user-2']),
        ),
      ]);

      final shelf = await repository
          .watchResources(shelfUserId: 'user-1', myLibrary: true)
          .first;
      expect(shelf.map((r) => r.title), ['Team Private']);
    });
  });

  group('setShelfMembership', () {
    test('batch removal updates every row and removal log', () async {
      stubCount(2);
      stubPage(0, 100, [row('res-1', 'Algebra'), row('res-2', 'Biology')]);
      await repository.sync(config: config);
      await repository.setShelfMemberships(
        ['res-1', 'res-2'],
        'user-1',
        joined: true,
      );

      await repository.setShelfMemberships(
        ['res-1', 'res-2'],
        'user-1',
        joined: false,
      );

      expect((await db.myLibraryDao.getById('res-1'))!.userId, isEmpty);
      expect((await db.myLibraryDao.getById('res-2'))!.userId, isEmpty);
      expect(
        await db.removedLogDao.removedDocIds('resources', 'user-1'),
        containsAll(['res-1', 'res-2']),
      );
    });

    test(
      'leaving records the removal so the shelf push cannot re-add',
      () async {
        stubCount(1);
        stubPage(0, 100, [row('res-1', 'Algebra')]);
        await repository.sync(config: config);
        await repository.setShelfMembership('res-1', 'user-1', joined: true);

        await repository.setShelfMembership('res-1', 'user-1', joined: false);

        final stored = await db.myLibraryDao.getById('res-1');
        expect(stored!.userId, isNot(contains('user-1')));
        expect(await db.removedLogDao.removedDocIds('resources', 'user-1'), [
          'res-1',
        ]);
      },
    );

    test('re-joining clears the stale removal record', () async {
      stubCount(1);
      stubPage(0, 100, [row('res-1', 'Algebra')]);
      await repository.sync(config: config);
      // Join first so the subsequent removal is a real change that records a
      // `removed_log` row — the stale record the re-join must clear.
      await repository.setShelfMembership('res-1', 'user-1', joined: true);
      await repository.setShelfMembership('res-1', 'user-1', joined: false);

      await repository.setShelfMembership('res-1', 'user-1', joined: true);

      final stored = await db.myLibraryDao.getById('res-1');
      expect(stored!.userId, contains('user-1'));
      expect(
        await db.removedLogDao.removedDocIds('resources', 'user-1'),
        isEmpty,
      );
    });

    test(
      'a no-op add when already a member skips the write (ef80dda52)',
      () async {
        stubCount(1);
        stubPage(0, 100, [row('res-1', 'Algebra')]);
        await repository.sync(config: config);
        await repository.setShelfMembership('res-1', 'user-1', joined: true);
        final firstCount = (await db.myLibraryDao.getById(
          'res-1',
        ))!.userId.length;

        // A second add with the same state is a no-op: no write, no
        // removed_log clear (there is nothing to clear), and the userId list
        // is untouched.
        await repository.setShelfMembership('res-1', 'user-1', joined: true);

        expect(
          (await db.myLibraryDao.getById('res-1'))!.userId.length,
          firstCount,
        );
      },
    );

    test(
      'a no-op remove when not a member skips the write (ef80dda52)',
      () async {
        stubCount(1);
        stubPage(0, 100, [row('res-1', 'Algebra')]);
        await repository.sync(config: config);
        // The user was never on the shelf, so a remove is a no-op: it must
        // NOT record a spurious `removed_log` entry that the next shelf push
        // would carry to the server.
        await repository.setShelfMembership('res-1', 'user-1', joined: false);

        expect(
          (await db.myLibraryDao.getById('res-1'))!.userId,
          isNot(contains('user-1')),
        );
        expect(
          await db.removedLogDao.removedDocIds('resources', 'user-1'),
          isEmpty,
        );
      },
    );
  });

  group('offline storage management', () {
    late Directory tempDir;

    setUp(() async {
      tempDir = await Directory.systemTemp.createTemp('resources_storage_test');
      ResourceFiles.baseDirectory = () async => tempDir;
    });

    tearDown(() async {
      ResourceFiles.baseDirectory = getApplicationDocumentsDirectoryFallback;
      if (tempDir.existsSync()) await tempDir.delete(recursive: true);
    });

    Future<void> seedOffline({
      required String id,
      required String title,
      required String filename,
      List<int> bytes = const [1, 2, 3],
    }) async {
      await db.myLibraryDao.upsertAll([
        MyLibraryTableCompanion.insert(
          id: id,
          couchId: Value(id),
          resourceId: Value(id),
          title: Value(title),
          filename: Value(filename),
          resourceOffline: const Value(true),
          resourceLocalAddress: Value('${tempDir.path}/ole/$id/$filename'),
        ),
      ]);
      final dir = Directory('${tempDir.path}/ole/$id');
      await dir.create(recursive: true);
      await File('${dir.path}/$filename').writeAsBytes(bytes);
    }

    test('getResourceTitlesMap maps resourceId to title', () async {
      await seedOffline(id: 'res-1', title: 'Algebra', filename: 'a.pdf');
      await seedOffline(id: 'res-2', title: 'Biology', filename: 'b.mp4');

      final titles = await repository.getResourceTitlesMap();

      expect(titles, {'res-1': 'Algebra', 'res-2': 'Biology'});
    });

    test(
      'getOfflineResourceItems groups files by docId and resolves titles',
      () async {
        await seedOffline(
          id: 'res-1',
          title: 'Algebra',
          filename: 'lecture.mp4',
        );
        await seedOffline(id: 'res-2', title: 'Biology', filename: 'notes.pdf');
        await seedOffline(
          id: 'res-3',
          title: 'Unknown on disk',
          filename: 'gone.pdf',
        );
        // A file with no matching library row falls back to the unknown label.
        await Directory('${tempDir.path}/ole/orphan').create(recursive: true);
        await File('${tempDir.path}/ole/orphan/lost.mp4').writeAsBytes([9]);

        final items = await repository.getOfflineResourceItems(
          extensions: videoExtensions,
          allKnownExtensions: allKnownExtensions,
          unknownTitle: 'Unknown resource',
        );

        expect(items, hasLength(2));
        final titles = items.map((i) => i.title).toList();
        expect(titles, containsAll(['Algebra', 'Unknown resource']));
        final algebra = items.firstWhere((i) => i.resourceId == 'res-1');
        expect(algebra.filePaths, hasLength(1));
        expect(algebra.totalSizeBytes, 3);
      },
    );

    test(
      'getOfflineResourceItems routes non-matching extensions to the other bucket',
      () async {
        await seedOffline(
          id: 'res-1',
          title: 'Algebra',
          filename: 'lecture.mp4',
        );

        final items = await repository.getOfflineResourceItems(
          extensions: pdfExtensions,
          allKnownExtensions: allKnownExtensions,
          unknownTitle: 'Unknown resource',
        );

        expect(items, isEmpty);
      },
    );

    test(
      'deleteOfflineResources removes files and clears the offline flag',
      () async {
        await seedOffline(
          id: 'res-1',
          title: 'Algebra',
          filename: 'lecture.mp4',
        );
        final items = await repository.getOfflineResourceItems(
          extensions: videoExtensions,
          allKnownExtensions: allKnownExtensions,
          unknownTitle: 'Unknown resource',
        );

        await repository.deleteOfflineResources(items);

        expect(
          File('${tempDir.path}/ole/res-1/lecture.mp4').existsSync(),
          isFalse,
        );
        expect(Directory('${tempDir.path}/ole/res-1').existsSync(), isFalse);
        final row = await db.myLibraryDao.getById('res-1');
        expect(row!.resourceOffline, isFalse);
      },
    );

    test(
      'markResourcesAsNotOffline clears the flag without touching files',
      () async {
        await seedOffline(id: 'res-1', title: 'Algebra', filename: 'a.pdf');

        await repository.markResourcesAsNotOffline(['res-1']);

        final row = await db.myLibraryDao.getById('res-1');
        expect(row!.resourceOffline, isFalse);
        expect(File('${tempDir.path}/ole/res-1/a.pdf').existsSync(), isTrue);
      },
    );

    test(
      'freeUpSpace clears every resource dir and marks the rows not offline',
      () async {
        // Port of `FreeSpaceWorker.doWork`: a top-level sweep that deletes each
        // resource-id directory under `ole/` and clears the offline flag on
        // the rows whose files just vanished, so the list stops offering them
        // as available offline before the next sync.
        await seedOffline(
          id: 'res-1',
          title: 'Algebra',
          filename: 'lecture.mp4',
          bytes: [1, 2, 3, 4],
        );
        await seedOffline(
          id: 'res-2',
          title: 'Biology',
          filename: 'notes.pdf',
          bytes: [5, 6],
        );

        final result = await repository.freeUpSpace();

        expect(result.deletedFiles, 2);
        expect(result.freedBytes, 6);
        expect(Directory('${tempDir.path}/ole/res-1').existsSync(), isFalse);
        expect(Directory('${tempDir.path}/ole/res-2').existsSync(), isFalse);
        expect(
          (await db.myLibraryDao.getById('res-1'))!.resourceOffline,
          isFalse,
        );
        expect(
          (await db.myLibraryDao.getById('res-2'))!.resourceOffline,
          isFalse,
        );
      },
    );

    test(
      'freeUpSpace spares a named directory and leaves its flag alone',
      () async {
        // The Kotlin skips the `cv` resume directory; the port has no resume
        // feature yet, but the [spareDirectoryNames] seam keeps the contract —
        // a future slice can exclude its store without touching the call site.
        await seedOffline(
          id: 'res-1',
          title: 'Algebra',
          filename: 'lecture.mp4',
        );
        await Directory('${tempDir.path}/ole/cv').create(recursive: true);
        await File('${tempDir.path}/ole/cv/resume.pdf').writeAsBytes([7, 8, 9]);

        final result = await repository.freeUpSpace(
          spareDirectoryNames: const {'cv'},
        );

        expect(result.deletedFiles, 1);
        expect(result.freedBytes, 3);
        expect(File('${tempDir.path}/ole/cv/resume.pdf').existsSync(), isTrue);
        expect(
          (await db.myLibraryDao.getById('res-1'))!.resourceOffline,
          isFalse,
        );
      },
    );

    test(
      'freeUpSpace is a no-op when the ole directory does not exist',
      () async {
        final result = await repository.freeUpSpace();

        expect(result.deletedFiles, 0);
        expect(result.freedBytes, 0);
      },
    );
  });

  group('local resource creation', () {
    test('saveLocalResource creates a row and marks it offline', () async {
      final error = await repository.saveLocalResource(
        const LocalResourceRequest(
          title: 'My New Resource',
          addedBy: 'ada',
          author: 'Author',
          year: '2026',
          description: 'A test resource',
          subjects: ['Agriculture'],
          levels: ['Lower Primary'],
          userId: 'user-1',
        ),
      );
      expect(error, isNull);

      final rows = await db.myLibraryDao.getAll();
      expect(rows, hasLength(1));
      final row = rows.first;
      expect(row.title, 'My New Resource');
      expect(row.resourceOffline, isTrue);
      expect(row.subject, contains('Agriculture'));
      expect(row.level, contains('Lower Primary'));
      expect(row.userId, contains('user-1'));
    });

    test('saveLocalResource rejects a duplicate title', () async {
      await repository.saveLocalResource(
        const LocalResourceRequest(
          title: 'Duplicate',
          description: 'first',
          subjects: ['Arts'],
          levels: ['Upper Primary'],
          userId: 'user-1',
        ),
      );
      final error = await repository.saveLocalResource(
        const LocalResourceRequest(
          title: 'Duplicate',
          description: 'second',
          subjects: ['Arts'],
          levels: ['Upper Primary'],
          userId: 'user-1',
        ),
      );
      expect(error, LocalResourceError.titleAlreadyExists);
    });

    test(
      'saveLocalResource as private team resource does not add to shelf',
      () async {
        await repository.saveLocalResource(
          const LocalResourceRequest(
            title: 'Team Resource',
            description: 'private',
            subjects: ['Arts'],
            levels: ['Upper Primary'],
            isPrivateTeamResource: true,
            teamId: 'team-1',
          ),
        );
        final row = await db.myLibraryDao.getAll();
        expect(row.first.isPrivate, isTrue);
        expect(row.first.privateFor, 'team-1');
        expect(row.first.userId, isEmpty);
      },
    );

    test('updateLocalResource edits an existing row', () async {
      await repository.saveLocalResource(
        const LocalResourceRequest(
          title: 'Original',
          description: 'original desc',
          subjects: ['Arts'],
          levels: ['Upper Primary'],
          userId: 'user-1',
        ),
      );
      final row = (await db.myLibraryDao.getAll()).first;
      final error = await repository.updateLocalResource(
        resourceId: row.id,
        title: 'Updated Title',
        author: 'New Author',
        year: '2027',
        description: 'updated desc',
        publisher: 'Pub',
        linkToLicense: 'MIT',
        subjects: ['History'],
        levels: ['Graduate'],
      );
      expect(error, isNull);
      final updated = await db.myLibraryDao.getById(row.id);
      expect(updated!.title, 'Updated Title');
      expect(updated.author, 'New Author');
      expect(updated.subject, contains('History'));
      expect(updated.level, contains('Graduate'));
    });

    test('resourceTitleExists returns true for an existing title', () async {
      await repository.saveLocalResource(
        const LocalResourceRequest(
          title: 'Unique Title',
          description: 'desc',
          subjects: ['Arts'],
          levels: ['Upper Primary'],
          userId: 'user-1',
        ),
      );
      expect(await repository.resourceTitleExists('Unique Title'), isTrue);
      expect(await repository.resourceTitleExists('Nonexistent'), isFalse);
    });
  });
}

Future<Directory> getApplicationDocumentsDirectoryFallback() async =>
    Directory.systemTemp;
