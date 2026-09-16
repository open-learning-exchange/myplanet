import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/local_resource_request.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/resources_repository.dart';
import 'package:myplanet/repository/resources_uploader.dart';
import 'package:myplanet/repository/teams_repository.dart';

import 'device_identity_fixture.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Schema v50's attachment-delivery column, and the sweep that reads it.
///
/// **The defect these are about is an absence, which is why the fixtures are
/// shaped the way they are.** Before this column, a resource whose attachment
/// PUT never landed and one whose attachment landed were the same `my_library`
/// row — `_rev == downloaded_rev`, `resource_offline = 1`, bytes under
/// `ole/<_id>/`. So *no* assertion over the row could have told them apart,
/// and every test below that claims a difference is really a test of whether
/// the column is written at the one moment that can distinguish them.
///
/// Two routes reach the lost state and both are exercised here rather than
/// argued about:
///
///  * the process dies between `MyLibraryDao.markUploaded` and the attachment
///    PUT, so **no retry row is ever filed** — *the sweep delivers an
///    attachment nothing queued*;
///  * the retry row spends its ladder while the handset is offline — *the
///    sweep re-arms an abandoned ladder*.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: 'p1n-9x7',
    couchDbUrl: 'https://satellite:p1n-9x7@planet.example.org:443',
  );

  late AppDatabase database;
  late _MockPlanetApi api;
  late ResourcesRepository resources;
  late OutboxRepository outbox;
  late ResourcesUploader uploader;
  late Directory sandbox;
  late Directory source;

  setUp(() async {
    database = AppDatabase.memory();
    api = _MockPlanetApi();
    resources = ResourcesRepository(
      api,
      database.myLibraryDao,
      database.removedLogDao,
    );
    outbox = OutboxRepository(database.outboxDao);
    uploader = ResourcesUploader(
      api,
      resources,
      TeamsRepository(api, database.teamDao, database.teamLogDao),
      outbox,
      testDeviceIdentity,
    );
    sandbox = await Directory.systemTemp.createTemp('attach-docs');
    source = await Directory.systemTemp.createTemp('attach-pick');
    ResourceFiles.baseDirectory = () async => sandbox;
  });

  tearDown(() async {
    ResourceFiles.baseDirectory = () async => Directory.systemTemp;
    await database.close();
    await sandbox.delete(recursive: true);
    await source.delete(recursive: true);
  });

  OutboxDrainer drainer() =>
      OutboxDrainer(api, outbox, handlers: uploader.handlers);

  void stubAttachment([
    NetworkResult<Map<String, dynamic>> result =
        const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
  ]) {
    when(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    ).thenAnswer((_) async => result);
  }

  /// Saves a local resource, with a real file on disk unless [withFile] is
  /// false, and returns its local id.
  Future<String> saveLocal({
    String title = 'Well survey',
    bool withFile = true,
  }) async {
    String? path;
    if (withFile) {
      final picked = File('${source.path}/well.pdf');
      await picked.writeAsString('water table falling');
      path = picked.path;
    }
    final error = await resources.saveLocalResource(
      LocalResourceRequest(
        title: title,
        userId: 'org.couchdb.user:ada',
        resourceUrl: path,
        mediaType: 'pdf',
      ),
    );
    expect(error, isNull, reason: 'the fixture should save cleanly');
    final rows = await database.myLibraryDao.getAll();
    return rows.firstWhere((r) => r.title == title).id;
  }

  /// Reproduces **exactly** the state a process killed after
  /// `markUploaded` leaves behind: the document is filed, the row has adopted
  /// the CouchDB identity, the bytes have moved with it — and no outbox row
  /// of any kind exists for the attachment.
  ///
  /// It calls the two steps the handler calls, in the handler's order, rather
  /// than hand-writing the resulting row. A hand-written row would be a
  /// fixture asserting the state this test is supposed to *discover*, which is
  /// the shape Phase 113 warns about: every exam fixture faked the join.
  Future<String> uploadedButUndelivered({String couchId = 'server-1'}) async {
    final localId = await saveLocal();
    await database.myLibraryDao.markUploaded(localId, couchId, '1-rev');
    await ResourceFiles.moveResourceDirectory(
      fromDocId: localId,
      toDocId: couchId,
    );
    expect(
      await database.outboxDao.forItem(
        ResourcesUploader.attachmentType,
        couchId,
      ),
      isEmpty,
      reason: 'the fixture must reproduce the undelivered state, not assume it',
    );
    return couchId;
  }

  // ------------------------------------------------------------- the column

  group('markUploaded writes the discriminator', () {
    test('a resource with bytes owes an attachment', () async {
      final id = await saveLocal();
      await database.myLibraryDao.markUploaded(id, 'server-1', '1-rev');

      final row = await database.myLibraryDao.getById('server-1');
      expect(
        row?.attachmentPending,
        isTrue,
        reason:
            'the statement that files the document is the only place a killed '
            'process cannot slip past; recording it later reopens the window '
            'the column exists to close',
      );
    });

    test('a metadata-only resource owes nothing', () async {
      // The port's form allows a resource with no file where Kotlin's does
      // not. Flagging one would make the sweep hunt for bytes that were never
      // written, on every pass, for ever.
      final id = await saveLocal(withFile: false);
      await database.myLibraryDao.markUploaded(id, 'server-2', '1-rev');

      final row = await database.myLibraryDao.getById('server-2');
      expect(row?.attachmentPending, isFalse);
    });

    test('a re-mark under the same id flags it too', () async {
      // `markUploaded` has two branches — rekey (`id != couchId`, the
      // production POST path, since CouchDB always mints a fresh id) and
      // update-in-place. **Mutation testing is what put this test here**: the
      // metadata-only assertion above stayed green when the in-place branch
      // was mutated to flag unconditionally, because every other test in this
      // file takes the rekey branch. A branch no test reaches is a branch the
      // next edit can silently break.
      final id = await saveLocal();
      await database.myLibraryDao.markUploaded(id, id, '1-rev');

      expect(
        (await database.myLibraryDao.getById(id))?.attachmentPending,
        isTrue,
      );

      // And the same branch must respect `hasBytes`, which is the half the
      // mutation actually escaped through.
      final bare = await saveLocal(title: 'No file', withFile: false);
      await database.myLibraryDao.markUploaded(bare, bare, '1-rev');
      expect(
        (await database.myLibraryDao.getById(bare))?.attachmentPending,
        isFalse,
      );
    });

    test('a delivered attachment clears it', () async {
      final id = await saveLocal();
      stubAttachment();
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => const NetworkSuccess<Map<String, dynamic>>({
          'id': 'server-1',
          'rev': '1-rev',
        }),
      );

      await uploader.queuePending(config: config);
      await drainer().drain();

      final row = await database.myLibraryDao.getById('server-1');
      expect(row?.attachmentPending, isFalse);
      expect(
        await resources.pendingAttachments(),
        isEmpty,
        reason: 'a delivered attachment must leave the sweep nothing to do',
      );
      // The pair, not the halves: the flag being false is only meaningful if
      // the PUT actually happened. Phase 100 lost the exam photo with both
      // halves passing their own tests.
      verify(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: any(named: 'contentType'),
          ifMatch: any(named: 'ifMatch'),
        ),
      ).called(1);
      expect(id, isNotEmpty);
    });

    test('a refused attachment leaves it set', () async {
      await saveLocal();
      stubAttachment(const NetworkError<Map<String, dynamic>>(500, 'boom'));
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => const NetworkSuccess<Map<String, dynamic>>({
          'id': 'server-1',
          'rev': '1-rev',
        }),
      );

      await uploader.queuePending(config: config);
      await drainer().drain();

      expect(
        (await database.myLibraryDao.getById('server-1'))?.attachmentPending,
        isTrue,
        reason:
            'reporting delivery on a refusal is the silence this column was '
            'added to end',
      );
    });
  });

  // -------------------------------------------------------------- the sweep

  group('queuePendingAttachments', () {
    test('delivers an attachment nothing queued', () async {
      // Route one: the process died between `markUploaded` and the PUT, so
      // there is no retry row and never was. No outbox row could have covered
      // this — the process died before there was one to file.
      final couchId = await uploadedButUndelivered();
      stubAttachment();

      final queued = await uploader.queuePendingAttachments(config: config);
      expect(queued, 1);
      await drainer().drain();

      verify(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: any(named: 'contentType'),
          ifMatch: '1-rev',
        ),
      ).called(1);
      expect(
        (await database.myLibraryDao.getById(couchId))?.attachmentPending,
        isFalse,
      );
    });

    test('re-arms an abandoned ladder', () async {
      // Route two: the retry row exists and has spent every attempt while the
      // handset was offline. A transport failure classifies `transient`, which
      // is what lets `enqueue` re-arm it — the memo applies to a verdict on
      // the bytes, and a dead socket is not one.
      final couchId = await uploadedButUndelivered();
      stubAttachment(const NetworkException<Map<String, dynamic>>('offline'));

      await uploader.queuePendingAttachments(config: config);
      final row = (await database.outboxDao.forItem(
        ResourcesUploader.attachmentType,
        couchId,
      )).single;
      for (var i = 0; i < row.maxAttempts; i++) {
        await outbox.markInProgress(row.id);
        await outbox.markFailed(row.id, errorMessage: 'offline');
      }
      expect(
        (await database.outboxDao.forItem(
          ResourcesUploader.attachmentType,
          couchId,
        )).single.status,
        isNot(OutboxDao.statusPending),
        reason: 'the fixture must actually exhaust the ladder',
      );

      stubAttachment();
      await uploader.queuePendingAttachments(config: config);
      await drainer().drain();

      expect(
        (await database.myLibraryDao.getById(couchId))?.attachmentPending,
        isFalse,
        reason:
            'a handset offline for longer than the ladder lost the file '
            'silently, and nothing could re-arm it',
      );
    });

    test('files one row however often it runs', () async {
      // The Phase 148 bound: an outbox item owns exactly one row, for ever.
      // The accretion that policy removed was unbounded *rows*, and a sweep
      // running on every sync pass is the shape that produced it.
      final couchId = await uploadedButUndelivered();
      stubAttachment(const NetworkException<Map<String, dynamic>>('offline'));

      await uploader.queuePendingAttachments(config: config);
      await uploader.queuePendingAttachments(config: config);
      await uploader.queuePendingAttachments(config: config);

      expect(
        await database.outboxDao.forItem(
          ResourcesUploader.attachmentType,
          couchId,
        ),
        hasLength(1),
      );
    });

    test('skips a row whose bytes are not on disk', () async {
      // Without the disk check this files an outbox row per pass that the
      // drain then deletes again, for ever — for a resource whose file the
      // user removed, or whose directory move failed. The flag stays set
      // regardless: "the bytes are not where the row says" is not evidence
      // the attachment was delivered.
      final couchId = await uploadedButUndelivered();
      await Directory('${sandbox.path}/ole/$couchId').delete(recursive: true);

      expect(await uploader.queuePendingAttachments(config: config), 0);
      expect(
        await database.outboxDao.forItem(
          ResourcesUploader.attachmentType,
          couchId,
        ),
        isEmpty,
      );
      expect(
        (await database.myLibraryDao.getById(couchId))?.attachmentPending,
        isTrue,
      );
    });

    test('leaves a downloaded catalog row alone', () async {
      // The whole planet's catalog sits in this table with a `_rev`, and a
      // sweep reading `_rev IS NOT NULL` alone would PUT an attachment for
      // every one of them — over the top of the server's own.
      //
      // **The row is downloaded on purpose, and mutation testing is why.**
      // The first cut of this fixture was a bare catalog row with no local
      // address, which the sweep skips on the `localAddress == null` clause
      // whether or not it reads the flag — so dropping the flag from
      // `pendingAttachments` left this test green and the claim was pinned by
      // nothing. A row the user has *downloaded* carries an address,
      // `resourceOffline` and real bytes, so the flag is the only thing that
      // excludes it. That is also the dangerous case rather than the tidy one.
      const catalogId = 'catalog-1';
      final file = await ResourceFiles.fileFor(
        docId: catalogId,
        filename: 'atlas.pdf',
      );
      await file.parent.create(recursive: true);
      await file.writeAsString('someone else’s bytes');
      await database.myLibraryDao.upsertAll([
        MyLibraryTableCompanion.insert(
          id: catalogId,
          couchId: const Value(catalogId),
          rev: const Value('9-rev'),
          downloadedRev: const Value('9-rev'),
          title: const Value('Someone else’s book'),
          resourceLocalAddress: const Value('atlas.pdf'),
          filename: const Value('atlas.pdf'),
          resourceOffline: const Value(true),
        ),
      ]);

      expect(
        await uploader.queuePendingAttachments(config: config),
        0,
        reason:
            'a downloaded catalog row is indistinguishable from an uploaded '
            'local one except by this column',
      );
    });
  });

  // ------------------------------------------------------------- the 409 arm

  group('a conflicting attachment PUT', () {
    /// One 409, then whatever [second] says.
    void stubConflictThen(NetworkResult<Map<String, dynamic>> second) {
      var calls = 0;
      when(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: any(named: 'contentType'),
          ifMatch: any(named: 'ifMatch'),
        ),
      ).thenAnswer((_) async {
        calls++;
        return calls == 1
            ? const NetworkError<Map<String, dynamic>>(409, 'conflict')
            : second;
      });
    }

    test('re-sends under the revision the server reports', () async {
      final couchId = await uploadedButUndelivered();
      stubConflictThen(
        const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => const NetworkSuccess<Map<String, dynamic>>({
          '_id': 'server-1',
          '_rev': '4-live',
        }),
      );

      await uploader.queuePendingAttachments(config: config);
      await drainer().drain();

      // The point of the arm: the second PUT carries the revision the *server*
      // reported, not the one the local row held. Re-reading the local row
      // would return the same stale value and make the arm inert.
      verify(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: any(named: 'contentType'),
          ifMatch: '4-live',
        ),
      ).called(1);
      expect(
        (await database.myLibraryDao.getById(couchId))?.attachmentPending,
        isFalse,
      );
    });

    test('does not re-send when the revision has not moved', () async {
      // Re-sending bytes the server has just refused under the identical
      // revision is precisely what Phase 148's memo exists to stop.
      await uploadedButUndelivered();
      stubConflictThen(
        const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => const NetworkSuccess<Map<String, dynamic>>({
          '_id': 'server-1',
          '_rev': '1-rev',
        }),
      );

      await uploader.queuePendingAttachments(config: config);
      await drainer().drain();

      verifyNever(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: any(named: 'contentType'),
          ifMatch: '4-live',
        ),
      );
      verify(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: any(named: 'contentType'),
          ifMatch: '1-rev',
        ),
      ).called(1);
    });

    test('an unreadable document leaves the conflict standing', () async {
      await uploadedButUndelivered();
      stubConflictThen(
        const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => const NetworkError<Map<String, dynamic>>(401, 'nope'),
      );

      await uploader.queuePendingAttachments(config: config);
      await drainer().drain();

      expect(
        (await database.myLibraryDao.getById('server-1'))?.attachmentPending,
        isTrue,
        reason: 'a failed recovery must not be reported as a delivery',
      );
    });
  });

  // ------------------------------------------------------------ reachability

  /// **Reachability, not behaviour** — the shape Phase 113 warned about and
  /// Phase 154 shipped anyway: a function ported, tested and green while
  /// nothing in the app calls it.
  ///
  /// The sweep is called from [ResourcesUploader.queuePending] rather than
  /// from a new site, which is what keeps it inside this lane's file set:
  /// `queuePending` already has three callers — `sweepPendingResources`
  /// (headless), `DashboardSyncNotifier.queuePendingResources` (foreground)
  /// and `add_resource_screen._save`. One sweep, three call sites, no other
  /// lane's file touched. `pending_resources_sweep_test` is what pins those
  /// three.
  ///
  /// The window is bounded to `queuePending`'s own body and both bounds are
  /// asserted found, so a rename fails loudly instead of silently widening the
  /// search until it matches the declaration — the Phase 134 trap.
  test('queuePending calls the attachment sweep', () {
    final source = File(
      'lib/repository/resources_uploader.dart',
    ).readAsStringSync();

    final start = source.indexOf('Future<int> queuePending({');
    expect(start, greaterThan(-1), reason: 'queuePending was renamed');
    final end = source.indexOf('Future<int> queuePendingAttachments({', start);
    expect(
      end,
      greaterThan(start),
      reason: 'queuePendingAttachments no longer follows queuePending',
    );
    final body = source.substring(start, end);

    expect(
      body,
      contains('await queuePendingAttachments('),
      reason:
          'nothing calls queuePendingAttachments, so an attachment whose PUT '
          'never landed stays on the handset for ever — green, tested and '
          'dead, which is exactly how Phase 154 shipped sweepPendingResources',
    );

    final swept = body.indexOf('await queuePendingAttachments(');
    final earlyReturn = body.indexOf('if (pending.isEmpty) return 0;');
    expect(earlyReturn, greaterThan(-1), reason: 'the early return moved');
    expect(
      swept,
      lessThan(earlyReturn),
      reason:
          'the attachment sweep must precede the document early return: a '
          'handset can have every document filed and still owe an attachment, '
          'which is the state this round exists to close',
    );
  });
}
