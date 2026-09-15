import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/utils/url_utils.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/local_resource_request.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/resources_repository.dart';
import 'package:myplanet/repository/resources_uploader.dart';
import 'package:myplanet/repository/teams_repository.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// **Write, upload, sync — in one test.**
///
/// The defect this file exists for could not be seen by any of the three
/// halves on their own, and all three had passing tests. `saveLocalResource`
/// wrote a correct row under a local uuid; `ResourcesUploader` correctly
/// POSTed it and recorded the revision CouchDB returned; `ResourcesRepository
/// .sync` correctly pruned rows the server no longer lists. Run in sequence
/// they detached the user from their own resource:
///
/// * `markUploaded` writing `_rev` made the row *prunable*
///   (`_rev IS NOT NULL AND _rev != ''`);
/// * the walk's keep set is document `_id`s, and the row's primary key was
///   still the uuid, so it was in no keep set;
/// * so the walk inserted a **second** row under the CouchDB id — empty
///   shelf, `resourceOffline` at its default — and deleted the first, leaving
///   the bytes under `ole/<uuid>/` with nothing that would ever collect them.
///
/// And a second half that needs no sync at all: every file reader in the port
/// resolves `couchId ?? id` (`resource_viewer_screen._getLocalFilePath`,
/// `ResourceDownloader`), so the moment `markUploaded` wrote a `couchId` the
/// viewer began looking in a directory that does not exist.
///
/// A fixture that hand-writes the post-upload row would prove none of it. The
/// walk here is the real `sync`, driven off `_all_docs` pages shaped like the
/// server's — including the document the POST in the previous step created,
/// under the id the server assigned it, which is what makes the keep set
/// realistic rather than convenient.
void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late ResourcesRepository resources;
  late ResourcesUploader uploader;
  late OutboxRepository outbox;
  late Directory sandbox;
  late Directory source;
  var clock = DateTime.fromMillisecondsSinceEpoch(1000);

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: 'p1n-9x7',
    couchDbUrl: 'https://satellite:p1n-9x7@planet.example.org:443',
  );
  final dbUrl = UrlUtils.dbUrl(config);

  const couchId = 'srv-well-survey';
  const shelfUser = 'org.couchdb.user:ada';

  final user = UserRow(
    id: shelfUser,
    name: 'ada',
    rolesList: const [],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
    planetCode: 'guatemala',
  );

  setUp(() async {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    clock = DateTime.fromMillisecondsSinceEpoch(1000);
    resources = ResourcesRepository(
      api,
      database.myLibraryDao,
      database.removedLogDao,
    );
    outbox = OutboxRepository(database.outboxDao, now: () => clock);
    uploader = ResourcesUploader(
      api,
      resources,
      TeamsRepository(
        api,
        database.teamDao,
        database.teamLogDao,
        createId: () => 'link-1',
      ),
      outbox,
      testDeviceIdentity,
    );
    sandbox = await Directory.systemTemp.createTemp('res-identity-docs');
    source = await Directory.systemTemp.createTemp('res-identity-pick');
    ResourceFiles.baseDirectory = () async => sandbox;
  });

  tearDown(() async {
    ResourceFiles.baseDirectory = getApplicationDocumentsDirectoryFallback;
    await database.close();
    await sandbox.delete(recursive: true);
    await source.delete(recursive: true);
  });

  /// Step 1 — the user creates a resource from a picked file.
  Future<String> createLocally({String filename = 'well-survey.pdf'}) async {
    final picked = File('${source.path}/$filename');
    await picked.writeAsString('water table falling');
    final error = await resources.saveLocalResource(
      LocalResourceRequest(
        title: 'Well survey notes',
        userId: shelfUser,
        addedBy: user.name,
        mediaType: 'pdf',
        resourceUrl: picked.path,
      ),
    );
    expect(error, isNull);
    final row = (await database.myLibraryDao.getAll()).single;
    expect(row.resourceOffline, isTrue);
    expect(row.userId, [shelfUser], reason: 'it is on the creator\'s shelf');
    expect(row.couchId, isNull, reason: 'nothing has been uploaded yet');
    return row.id;
  }

  /// Step 2 — it uploads, and CouchDB assigns it an identity.
  Future<void> upload({
    NetworkResult<Map<String, dynamic>> attachment =
        const NetworkSuccess<Map<String, dynamic>>({
          'ok': true,
          'rev': '2-att',
        }),
  }) async {
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({
        'id': couchId,
        'rev': '1-srv',
      }),
    );
    when(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    ).thenAnswer((_) async => attachment);

    await uploader.queuePending(config: config, user: user);
    await OutboxDrainer(api, outbox, handlers: uploader.handlers).drain();
  }

  /// Step 3 — a **full** resources walk, with the keep set the server would
  /// really send: the document the upload created, plus an unrelated one so
  /// the keep set is not a single-element special case.
  ///
  /// [uploadedRev] is `2-att` rather than the POST's `1-srv` on purpose: the
  /// attachment PUT bumped the document's revision, so `2-att` is what the
  /// server would report on the next walk. A fixture that replayed the POST's
  /// revision would look like a passing test of a *different* server.
  Future<void> fullResourcesWalk({
    bool includeUploaded = true,
    bool withAttachment = true,
    String uploadedRev = '2-att',
  }) async {
    Map<String, dynamic> serverRow(String id, String title, String rev) => {
      'id': id,
      'doc': {
        '_id': id,
        '_rev': rev,
        'title': title,
        'filename': 'well-survey.pdf',
        if (withAttachment)
          '_attachments': {
            'well-survey.pdf': {'content_type': 'application/pdf'},
          },
      },
    };

    final rows = <Map<String, dynamic>>[
      if (includeUploaded) serverRow(couchId, 'Well survey notes', uploadedRev),
      serverRow('srv-unrelated', 'Somebody else\'s resource', '1-srv'),
    ];

    when(
      () => api.getJsonObject(
        '$dbUrl/resources/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': rows.length}),
    );
    when(
      () => api.getJsonObject(
        any(that: contains('include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rows': rows}),
    );

    await resources.sync(config: config);
  }

  /// What the viewer would open — `ResourceFiles` under `couchId ?? id`,
  /// exactly as `resource_viewer_screen._getLocalFilePath` resolves it.
  Future<File?> viewerFile(MyLibraryRow row) => ResourceFiles.existingFileFor(
    docId: row.couchId ?? row.id,
    filename: row.filename ?? '',
  );

  test(
    'the resource survives its own upload and the sync that follows it',
    () async {
      final localId = await createLocally();
      await upload();
      await fullResourcesWalk();

      // One row, not two. Before the fix the walk inserted a second row under
      // the CouchDB id and deleted the original.
      final rows = await database.myLibraryDao.getAll();
      expect(
        rows.map((r) => r.id),
        containsAll(<String>[couchId, 'srv-unrelated']),
      );
      expect(
        rows.where((r) => r.id == couchId),
        hasLength(1),
        reason: 'two rows for one document is the defect, not a symptom',
      );
      expect(
        await database.myLibraryDao.getById(localId),
        isNull,
        reason: 'the local uuid is retired, not duplicated',
      );

      final survivor = rows.firstWhere((r) => r.id == couchId);

      // It is still the user's.
      expect(survivor.userId, [
        shelfUser,
      ], reason: 'the shelf is what puts it in My Library');
      // It is still downloaded...
      expect(survivor.resourceOffline, isTrue);
      expect(
        survivor.rev,
        survivor.downloadedRev,
        reason:
            'unequal revisions make the bell ask the user to download the '
            'resource they created, over the copy already on the device',
      );
      // ...and the viewer can still open it.
      final opened = await viewerFile(survivor);
      expect(opened, isNotNull, reason: 'the bytes must not be orphaned');
      expect(await opened!.readAsString(), 'water table falling');

      // And nothing is left behind under the old key.
      expect(
        await (await ResourceFiles.directoryFor(docId: localId)).exists(),
        isFalse,
      );
    },
  );

  test('the viewer can open it before any sync runs', () async {
    // The second half of the defect, which needs no walk: `couchId ?? id`
    // starts resolving to the CouchDB id the moment the upload records one,
    // while the bytes were written under the local uuid.
    await createLocally();
    await upload();

    final row = (await database.myLibraryDao.getAll()).single;
    expect(row.id, couchId);
    expect(await viewerFile(row), isNotNull);
  });

  test('the shelf document names a resource Planet can resolve', () async {
    // `ShelfRepository._localResourceIds` sends `resourceId ?? id`, so this is
    // what the server-side shelf document would carry. A local uuid there is
    // a dangling reference, and `mergeJsonArray` keeps one for ever.
    await createLocally();
    await upload();

    final row = (await database.myLibraryDao.getAll()).single;
    expect(row.resourceId, couchId);
  });

  test('a resource the server really did drop is still pruned', () async {
    // The guard must not become "spare everything the device has touched".
    // Same walk, with the uploaded document withheld from the keep set —
    // which is what a deletion on Planet looks like.
    await createLocally();
    await upload();
    await fullResourcesWalk(includeUploaded: false);

    expect(
      await database.myLibraryDao.getById(couchId),
      isNull,
      reason:
          'an identity that survives a prune it should not is the mirror '
          'defect: a resource deleted on Planet lingering for ever',
    );
  });

  test('a resource that never uploaded is still spared', () async {
    // The pre-existing eligibility guard — `_rev IS NULL` — is what protects a
    // resource created offline, and moving the key must not have leaned on it.
    final localId = await createLocally();
    await fullResourcesWalk(includeUploaded: false);

    final row = await database.myLibraryDao.getById(localId);
    expect(row, isNotNull);
    expect(row!.userId, [shelfUser]);
    expect(await viewerFile(row), isNotNull);
  });

  /// **A handler that is not registered is worse than absent here.**
  ///
  /// `OutboxDrainer` falls back to `sendJsonObject(row.endpoint, body:
  /// payload, method: row.httpMethod)` for an unknown `uploadType`, so an
  /// unregistered `attachmentType` row would POST `{"filename": …}` to
  /// `<db>/resources` and file a junk document in the shared catalog. The
  /// source-text guard is the shape Phase 154 asked for after a lane shipped a
  /// function nothing called: every test in this file drives the uploader
  /// directly and would stay green with the registration missing.
  test('both resource handlers are registered with the drainer', () {
    final wiring = File('lib/providers/app_providers.dart').readAsStringSync();
    expect(
      wiring,
      contains('ref.watch(resourcesUploaderProvider).handlers'),
      reason:
          'registering only ResourcesUploader.type leaves attachment rows to '
          'the drainer generic fallback, which files a junk document',
    );
    expect(
      ResourcesUploader(
        MockPlanetApi(),
        resources,
        TeamsRepository(api, database.teamDao, database.teamLogDao),
        outbox,
        testDeviceIdentity,
      ).handlers.keys,
      containsAll(<String>[
        ResourcesUploader.type,
        ResourcesUploader.attachmentType,
      ]),
    );
  });

  test(
    'a document the walk pulled first does not become a second row',
    () async {
      // The upgrade case: a handset that ran the buggy build already has the
      // walk's stray row sitting at the CouchDB id when the fix lands. The
      // rekey merges onto it rather than colliding, and the shelf is the
      // union — the stray row may have gained a membership from a later sync.
      final localId = await createLocally();
      await fullResourcesWalk(includeUploaded: true);
      await database.myLibraryDao.upsertAll([
        (await database.myLibraryDao.getById(
          couchId,
        ))!.copyWith(userId: const ['org.couchdb.user:bob']).toCompanion(true),
      ]);

      await upload();

      final rows = await database.myLibraryDao.getAll();
      expect(rows.where((r) => r.id == couchId), hasLength(1));
      expect(await database.myLibraryDao.getById(localId), isNull);
      final merged = rows.firstWhere((r) => r.id == couchId);
      expect(
        merged.userId,
        containsAll(<String>[shelfUser, 'org.couchdb.user:bob']),
      );
      expect(merged.resourceOffline, isTrue);
      expect(await viewerFile(merged), isNotNull);
    },
  );
}

/// Restores the seam to something harmless — a leaked sandbox path outlives
/// the temp directory `tearDown` deletes.
Future<Directory> getApplicationDocumentsDirectoryFallback() async =>
    Directory.systemTemp;
