import 'dart:convert';
import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/my_library_mapper.dart';
import 'package:myplanet/repository/local_resource_request.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/resources_repository.dart';
import 'package:myplanet/repository/resources_uploader.dart';
import 'package:myplanet/repository/teams_repository.dart';
import 'package:myplanet/repository/teams_uploader.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// The resource **upload** direction, which the port did not have at all.
///
/// `ResourcesRepository.saveLocalResource` has been a careful port for several
/// phases and nothing ever sent what it wrote, so a resource the user created
/// lived on that handset alone. Kotlin runs this direction from three places
/// (`AutoSyncWorker:129`, `UserDataWorker:84`, `TeamsRepositoryImpl:928`).
///
/// The load-bearing test in this file is *the round trip* — it drives
/// `saveLocalResource` and the uploader's handler together and asserts the
/// attachment PUT carries the bytes the writer actually put on disk. Every
/// other test here could pass with the two halves disagreeing about where the
/// file is, which is exactly how Phase 100 lost the exam verification photo.
void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late ResourcesRepository resources;
  late TeamsRepository teams;
  late OutboxRepository outbox;
  late ResourcesUploader uploader;
  late Directory sandbox;
  late Directory source;
  var clock = DateTime.fromMillisecondsSinceEpoch(1000);

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    // Deliberately not a digit run: the payload carries 13-digit epoch
    // timestamps, so `isNot(contains(pin))` against '1234' has a real chance
    // of matching one by accident and reading as a pass.
    pin: 'p1n-9x7',
    couchDbUrl: 'https://satellite:p1n-9x7@planet.example.org:443',
  );

  final user = UserRow(
    id: 'org.couchdb.user:ada',
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
    teams = TeamsRepository(
      api,
      database.teamDao,
      database.teamLogDao,
      createId: () => 'link-1',
    );
    uploader = ResourcesUploader(
      api,
      resources,
      teams,
      outbox,
      testDeviceIdentity,
    );
    sandbox = await Directory.systemTemp.createTemp('res-upload-docs');
    source = await Directory.systemTemp.createTemp('res-upload-pick');
    ResourceFiles.baseDirectory = () async => sandbox;
  });

  tearDown(() async {
    ResourceFiles.baseDirectory = getApplicationDocumentsDirectoryFallback;
    await database.close();
    await sandbox.delete(recursive: true);
    await source.delete(recursive: true);
  });

  void stubPost(NetworkResult<Map<String, dynamic>> result) {
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((_) async => result);
  }

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

  OutboxDrainer drainer() => OutboxDrainer(
    api,
    outbox,
    handlers: {ResourcesUploader.type: uploader.handler},
  );

  /// Writes a file the picker would have handed the screen.
  Future<File> pickedFile({
    String name = 'well-survey.pdf',
    String contents = 'water table falling',
  }) async {
    final file = File('${source.path}/$name');
    await file.writeAsString(contents);
    return file;
  }

  Future<void> saveLocal({
    String title = 'Well survey notes',
    String? path,
    bool private = false,
    String? teamId,
  }) async {
    final error = await resources.saveLocalResource(
      LocalResourceRequest(
        title: title,
        userId: user.id,
        addedBy: user.name,
        author: 'Ada Lovelace',
        year: '2026',
        description: 'Notes from the district well',
        publisher: 'OLE',
        linkToLicense: 'https://cc.example/by',
        openWith: 'HTML',
        language: 'English',
        mediaType: 'pdf',
        resourceType: 'Reference',
        subjects: const ['Science'],
        levels: const ['Primary'],
        resourceFor: const ['Learners'],
        resourceUrl: path,
        isPrivateTeamResource: private,
        teamId: teamId,
      ),
    );
    expect(error, isNull, reason: 'the fixture should save cleanly');
  }

  Future<MyLibraryRow> soleRow() async {
    final rows = await database.myLibraryDao.getAll();
    return rows.single;
  }

  // ---------------------------------------------------------------- serializer

  group('serialize (MyLibrary.serialize:154-189)', () {
    test('sends every field the Kotlin create payload sends', () async {
      await saveLocal(path: (await pickedFile()).path);
      final row = await soleRow();

      expect(
        ResourcesUploader.serialize(
          row,
          uploadedAt: 4242,
          addedById: user.id,
          planetCode: user.planetCode,
        ),
        {
          'title': 'Well survey notes',
          'uploadDate': 4242,
          'createdDate': row.createdDate,
          'filename': 'well-survey.pdf',
          'author': 'Ada Lovelace',
          // The user's **id**, not the row's `addedBy` column, which holds the
          // display name. Kotlin: `addProperty("addedBy", user?.id)`.
          'addedBy': 'org.couchdb.user:ada',
          'medium': null,
          'description': 'Notes from the district well',
          'year': '2026',
          'language': 'English',
          'publisher': 'OLE',
          'linkToLicense': 'https://cc.example/by',
          'subject': ['Science'],
          'level': ['Primary'],
          'resourceType': 'Reference',
          'openWith': 'HTML',
          'mediaType': 'pdf',
          'resourceFor': ['Learners'],
          'private': false,
          'isDownloadable': true,
          'sourcePlanet': 'guatemala',
          'resideOn': 'guatemala',
          'updatedDate': 4242,
        },
        reason:
            'Phase 103: a payload whose keys Planet does not read is a '
            'document the server cannot parse, not a cosmetic defect',
      );
    });

    test('reproduces the null-vs-empty-string asymmetry', () async {
      // Kotlin coalesces exactly three fields to '' and leaves the rest as
      // JSON null. Tidying that up either way would change what Planet sees.
      await saveLocal();
      final bare = (await soleRow()).copyWith(
        author: const Value(null),
        publisher: const Value(null),
        linkToLicense: const Value(null),
        description: const Value(null),
        year: const Value(null),
        language: const Value(null),
        resourceType: const Value(null),
        openWith: const Value(null),
        mediaType: const Value(null),
      );

      final json = ResourcesUploader.serialize(bare, uploadedAt: 1);

      expect(json['author'], '');
      expect(json['publisher'], '');
      expect(json['linkToLicense'], '');
      // `mediaType` has its own default, and it is not ''.
      expect(json['mediaType'], 'other');
      for (final key in [
        'medium',
        'description',
        'year',
        'language',
        'resourceType',
        'openWith',
      ]) {
        expect(json.containsKey(key), isTrue, reason: '$key must be present');
        expect(json[key], isNull, reason: '$key must be null, not ""');
      }
    });

    test(
      'privateFor is a nested object, and only for a private team resource',
      () async {
        await saveLocal(
          path: (await pickedFile()).path,
          private: true,
          teamId: 'team-7',
        );
        final private = await soleRow();

        expect(
          ResourcesUploader.serialize(private, uploadedAt: 1)['privateFor'],
          {'teams': 'team-7'},
        );
        expect(
          ResourcesUploader.serialize(private, uploadedAt: 1)['private'],
          isTrue,
        );

        // Kotlin's condition is `isPrivate && privateFor != null`, so both a
        // public resource and a private one with no team omit the key entirely.
        expect(
          ResourcesUploader.serialize(
            private.copyWith(privateFor: const Value(null)),
            uploadedAt: 1,
          ).containsKey('privateFor'),
          isFalse,
        );
        expect(
          ResourcesUploader.serialize(
            private.copyWith(isPrivate: false),
            uploadedAt: 1,
          ).containsKey('privateFor'),
          isFalse,
        );
      },
    );

    test(
      'filename uses the attachment spelling, not the URI-decoded one',
      () async {
        // The phase's most-argued decision, and it was pinned by nothing:
        // `well-survey.pdf` and `deep/dir/book.epub` are names on which
        // `p.basename` and Kotlin's `getFileNameFromUrl` agree, so a future
        // "make it faithful to `getFileNameFromUrl`" change passed every test.
        //
        // Kotlin derives this field twice, differently: the document gets
        // `getFileNameFromUrl` (`MyLibrary.kt:159`), which URI-parses and
        // `URLDecoder.decode`s the last segment, while the attachment PUT names
        // the file with `getFileNameFromLocalAddress` (`FileUploader.kt:38`), a
        // plain `substringAfterLast('/')`. `a+b.pdf` is where they part —
        // `URLDecoder` turns `+` into a space — and a document whose `filename`
        // does not match its attachment's name is a resource Planet cannot
        // resolve. The attachment spelling wins, because it names bytes that
        // really exist.
        await saveLocal(path: (await pickedFile(name: 'a+b.pdf')).path);
        final plus = await soleRow();
        expect(plus.resourceLocalAddress, 'a+b.pdf');
        expect(
          ResourcesUploader.serialize(plus, uploadedAt: 1)['filename'],
          'a+b.pdf',
          reason:
              "getFileNameFromUrl would give 'a b.pdf' and contradict the "
              'attachment PUT below',
        );
      },
    );

    test('filename is the basename, or empty when there is no file', () async {
      await saveLocal();
      final row = await soleRow();

      expect(ResourcesUploader.serialize(row, uploadedAt: 1)['filename'], '');
      expect(
        ResourcesUploader.serialize(
          row.copyWith(resourceLocalAddress: const Value('deep/dir/book.epub')),
          uploadedAt: 1,
        )['filename'],
        'book.epub',
      );
    });

    test('carries no _id or _rev — this is a create, not an update', () async {
      await saveLocal(path: (await pickedFile()).path);
      final json = ResourcesUploader.serialize(await soleRow(), uploadedAt: 1);
      expect(json.containsKey('_id'), isFalse);
      expect(json.containsKey('_rev'), isFalse);
    });
  });

  // ------------------------------------------------------- pending predicate

  group('pendingUploads', () {
    test('finds a resource this device authored', () async {
      await saveLocal(path: (await pickedFile()).path);
      expect(await resources.pendingUploads(), hasLength(1));
    });

    test('excludes a course-embedded resource with no _rev', () async {
      // **The reason this port does not copy Kotlin's predicate.**
      //
      // Kotlin's is `WHERE _rev IS NULL` (`MyLibraryDao.kt:94`). `my_library`
      // has two writers and only one sees a `_rev`: the resources walk pulls
      // whole documents, the courses walk pulls the thinner copy embedded in a
      // course step, and a sub-object carries none. Phase 150 made the port
      // leave `rev` absent in that case — correctly, for reasons at
      // `MyLibraryMapper._revOrAbsent` — which removed the accidental guard
      // Kotlin leans on: Kotlin writes `""` there, via `JsonUtils.getString`
      // on a missing key, and `_rev IS NULL` does not match an empty string.
      //
      // Under the literal predicate this row would be POSTed to `resources`,
      // filing a **second copy of a document that already exists** — one per
      // course resource, on the first drain after the first courses sync.
      final embedded = MyLibraryMapper.fromDoc(
        // A step's `resources` sub-object: an `_id`, no `_rev`.
        const {'_id': 'srv-course-res', 'title': 'Embedded reader'},
        couchDbUrl: config.couchDbUrl,
        stepId: 'course-1:0',
        courseId: 'course-1',
      );
      await database.myLibraryDao.upsertAll([embedded!]);

      final stored = await database.myLibraryDao.getById('srv-course-res');
      expect(
        stored?.rev,
        isNull,
        reason: 'the fixture must reproduce the null rev, or it proves nothing',
      );
      expect(stored?.couchId, 'srv-course-res');

      expect(
        await resources.pendingUploads(),
        isEmpty,
        reason:
            'a row carrying a server _id cannot have been authored here, '
            'whatever its _rev says',
      );
    });

    test('excludes a resource that has already been uploaded', () async {
      await saveLocal(path: (await pickedFile()).path);
      final row = await soleRow();
      expect(
        await resources.markResourceUploaded(row.id, 'srv-1', '1-a'),
        isTrue,
      );
      expect(await resources.pendingUploads(), isEmpty);
    });

    test('excludes a row carrying a _rev but no _id', () async {
      // Mutation testing found the `_rev IS NULL` half of the predicate pinned
      // by **nothing**: `markResourceUploaded` writes `_id` and `_rev`
      // together, so the already-uploaded test above passes on the `_id`
      // clause alone, and no port writer produces this shape today. That makes
      // the clause defence in depth rather than dead — it is the half Kotlin
      // actually relies on — so it gets a row built by hand rather than a
      // comment claiming it matters. A clause pinned by nothing reads as
      // coverage.
      await database.myLibraryDao.upsertAll([
        MyLibraryTableCompanion.insert(
          id: 'odd-row',
          title: const Value('Half-identified'),
          rev: const Value('1-a'),
        ),
      ]);
      expect(await resources.pendingUploads(), isEmpty);
    });

    test('markResourceUploaded reports a row that vanished', () async {
      expect(
        await resources.markResourceUploaded('gone', 'srv-1', '1-a'),
        isFalse,
        reason:
            'Kotlin returns false from `getById(localId) ?: return false`, '
            'and `markUploaded` filters on it to report a failed item',
      );
    });
  });

  // ------------------------------------------------------------------- outbox

  group('outbox behaviour', () {
    test('the endpoint and the queued row carry no credentials', () async {
      await saveLocal(path: (await pickedFile()).path);
      await uploader.queuePending(config: config, user: user);

      expect(
        ResourcesUploader.endpointFor(config),
        'https://planet.example.org/db/resources',
      );
      final row = (await outbox.due()).single;
      expect(row.endpoint, isNot(contains(config.pin)));
      expect(row.payload, isNot(contains(config.pin)));
      expect(row.endpoint, isNot(contains('@')));

      final payload = jsonDecode(row.payload) as Map<String, dynamic>;
      for (final field in testDeviceFields.entries) {
        expect(payload, containsPair(field.key, field.value));
      }
    });

    test('queuePending does not double-queue', () async {
      await saveLocal(path: (await pickedFile()).path);
      expect(await uploader.queuePending(config: config, user: user), 1);
      await uploader.queuePending(config: config, user: user);
      expect(await outbox.due(), hasLength(1));
    });

    test('the payload is stable across sweeps', () async {
      // Kotlin serializes at send time and writes `System.currentTimeMillis()`
      // into `uploadDate` and `updatedDate` freely. The port stores the payload
      // at enqueue time and re-serializes on every sweep, so a moving field
      // makes `enqueue`'s memo never match — and a refused document is POSTed
      // again on every sweep. This resource is an append with a server-minted
      // id, so each of those is an undetectable duplicate.
      await saveLocal(path: (await pickedFile()).path);
      await uploader.queuePending(config: config, user: user);
      final first = (await outbox.due()).single.payload;

      clock = clock.add(const Duration(hours: 3));
      await uploader.queuePending(config: config, user: user);

      expect((await outbox.due()).single.payload, first);
    });

    test(
      'a refused resource is POSTed once, however many sweeps run',
      () async {
        await saveLocal(path: (await pickedFile()).path);
        stubPost(const NetworkError<Map<String, dynamic>>(400, 'bad request'));

        for (var sweep = 0; sweep < 5; sweep++) {
          await uploader.queuePending(config: config, user: user);
          await drainer().drain();
          clock = clock.add(const Duration(hours: 1));
        }

        verify(
          () => api.postJsonObject(
            any(),
            any(),
            authHeader: any(named: 'authHeader'),
          ),
        ).called(1);
        final row = await soleRow();
        expect(
          await database.outboxDao.forItem(ResourcesUploader.type, row.id),
          hasLength(1),
          reason: 'an outbox item owns exactly one row, for ever (Phase 148)',
        );
      },
    );

    test('a 2xx with no id/rev fails rather than dropping the row', () async {
      await saveLocal(path: (await pickedFile()).path);
      await uploader.queuePending(config: config, user: user);
      stubPost(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));

      await drainer().drain();

      final row = await soleRow();
      expect(row.couchId, isNull);
      expect(
        await database.outboxDao.forItem(ResourcesUploader.type, row.id),
        isNotEmpty,
        reason:
            'reporting success would delete the row while the resource stays '
            'pending, so the next sweep would POST a duplicate — for ever',
      );
      // Indeterminate: the transport said the write landed, and a duplicate
      // append with a server-assigned id cannot be detected afterwards.
      //
      // `expect(await outbox.due(), isEmpty)` alone **cannot fail** here, and
      // that is worth saying out loud: a `transient` classification would set
      // `nextAttemptAt = now + 60s` and, with the clock unmoved, `due()` is
      // empty either way. So the clock is advanced well past any backoff and
      // the drain re-run — a regression that made `noUsableResponse` transient
      // would re-POST this append, which is the whole harm.
      expect(await outbox.due(), isEmpty);
      clock = clock.add(const Duration(hours: 2));
      expect(await outbox.due(), isEmpty);
      await drainer().drain();
      verify(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).called(1);
    });

    test('an in-flight resource is not re-enqueued over', () async {
      // **A resource POST is an append, so a duplicate is undetectable.**
      // `enqueue` puts an `in_progress` row back to `pending` to preserve a
      // mid-flight payload edit, and `markCompleted` is `deleteIfInProgress`
      // — so without the `isInFlight` guard the succeeding send deletes
      // nothing, the row survives `pending` with the same body, and the next
      // drain files a **second CouchDB document**. The handler carries no
      // `_id`, so the server mints a fresh one and `markResourceUploaded`
      // repoints the local row at the duplicate, leaving the first document an
      // unidentifiable orphan.
      //
      // Reached by a drain claiming the row on a slow link while the user
      // saves another resource — `_save` sweeps every pending row, this one
      // included. Driven here by re-entering `queuePending` from inside the
      // POST, which is that interleaving exactly.
      await saveLocal(path: (await pickedFile()).path);
      await uploader.queuePending(config: config, user: user);
      final id = (await soleRow()).id;

      var posts = 0;
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async {
        posts++;
        // The user's second save, mid-flight.
        await uploader.queuePending(config: config, user: user);
        return NetworkSuccess<Map<String, dynamic>>({
          'id': 'srv-$posts',
          'rev': '$posts-a',
        });
      });
      stubAttachment();

      await drainer().drain();
      clock = clock.add(const Duration(hours: 1));
      await drainer().drain();

      expect(posts, 1, reason: 'a second POST is a second document in Planet');
      expect((await soleRow()).couchId, 'srv-1');
      expect(
        await database.outboxDao.forItem(ResourcesUploader.type, id),
        isEmpty,
        reason: 'the completed row must actually be deleted, not resurrected',
      );
    });

    test(
      'a transient failure leaves the resource pending, and the retry settles it',
      () async {
        await saveLocal(path: (await pickedFile()).path);
        await uploader.queuePending(config: config, user: user);
        stubPost(const NetworkError<Map<String, dynamic>>(503, 'unavailable'));

        expect(await drainer().drain(), [OutboxOutcome.retryScheduled]);
        expect(await resources.pendingUploads(), hasLength(1));

        clock = clock.add(const Duration(minutes: 1));
        stubPost(
          const NetworkSuccess<Map<String, dynamic>>({
            'id': 'srv-1',
            'rev': '1-a',
          }),
        );
        stubAttachment();
        expect(await drainer().drain(), [OutboxOutcome.completed]);
        expect(await resources.pendingUploads(), isEmpty);
      },
    );

    test('a successful upload adopts the ids CouchDB assigned', () async {
      await saveLocal(path: (await pickedFile()).path);
      await uploader.queuePending(config: config, user: user);
      stubPost(
        const NetworkSuccess<Map<String, dynamic>>({
          'id': 'srv-9',
          'rev': '1-z',
        }),
      );
      stubAttachment();

      expect(await drainer().drain(), [OutboxOutcome.completed]);

      final saved = await soleRow();
      expect(saved.couchId, 'srv-9');
      expect(saved.rev, '1-z');
      expect(await resources.pendingUploads(), isEmpty);
    });

    test(
      'an edit re-queues the edited payload, not the pre-edit one',
      () async {
        // Kotlin re-serializes at upload time and so picks an edit up for free.
        // The port's payload is captured at enqueue time, so without a re-queue
        // the user's edit would be applied locally and the *pre-edit* document
        // would be the one that reached the server. `add_resource_screen` calls
        // `queuePending` after an edit for exactly this reason.
        await saveLocal(path: (await pickedFile()).path);
        await uploader.queuePending(config: config, user: user);
        final row = await soleRow();
        expect(
          jsonDecode((await outbox.due()).single.payload)['title'],
          'Well survey notes',
        );

        await resources.updateLocalResource(
          resourceId: row.id,
          title: 'Well survey notes (revised)',
          author: 'Ada Lovelace',
        );
        await uploader.queuePending(config: config, user: user);

        expect(
          jsonDecode((await outbox.due()).single.payload)['title'],
          'Well survey notes (revised)',
        );
        expect(await outbox.due(), hasLength(1));
      },
    );
  });

  // -------------------------------------------------------------- round trip

  group('the round trip', () {
    test(
      'the attachment PUT carries the bytes saveLocalResource wrote',
      () async {
        // **Test the pair, not the halves.**
        //
        // Nothing above this line would notice if the writer and the uploader
        // disagreed about where the file is — and that disagreement is the most
        // expensive defect this project has had. Phase 100: the exam
        // verification photo was written under the submission id and read back
        // under the `submit_photos` row id, so the lookup missed on every
        // capture, the attachment step took its "a missing file is a no-op"
        // branch, and the document uploaded without the photo the feature exists
        // to collect. Nothing failed loudly. Each half had a passing test.
        //
        // The Kotlin has this defect *right here*:
        // `FileUploader.uploadAttachment` does `File(personal
        // .resourceLocalAddress)` on a column holding a **basename**, so its
        // attachment upload can never find the bytes either.
        //
        // So this test writes a real file through the real writer and asserts
        // the uploader PUTs those exact bytes. No fixture fabricates the join.
        const contents = 'the well ran dry in March';
        final picked = await pickedFile(
          name: 'district-well.pdf',
          contents: contents,
        );
        await saveLocal(path: picked.path);
        final row = await soleRow();

        // What the writer actually did, established rather than assumed.
        expect(row.resourceLocalAddress, 'district-well.pdf');
        expect(row.resourceOffline, isTrue);
        final onDisk = File('${sandbox.path}/ole/${row.id}/district-well.pdf');
        expect(await onDisk.exists(), isTrue);

        await uploader.queuePending(config: config, user: user);
        stubPost(
          const NetworkSuccess<Map<String, dynamic>>({
            'id': 'srv-7',
            'rev': '1-q',
          }),
        );
        stubAttachment();

        expect(await drainer().drain(), [OutboxOutcome.completed]);

        final captured = verify(
          () => api.uploadAttachment(
            captureAny(),
            bytes: captureAny(named: 'bytes'),
            authHeader: any(named: 'authHeader'),
            contentType: captureAny(named: 'contentType'),
            ifMatch: captureAny(named: 'ifMatch'),
          ),
        ).captured;

        expect(
          captured[0],
          'https://planet.example.org/db/resources/srv-7/district-well.pdf',
          reason: 'Kotlin: String.format("%s/resources/%s/%s", url, id, name)',
        );
        expect(
          utf8.decode((captured[1] as List<int>)),
          contents,
          reason:
              'the bytes must be the ones the writer copied, resolved through '
              'the same ResourceFiles helper under the same docId',
        );
        expect(captured[2], 'application/pdf');
        expect(
          captured[3],
          '1-q',
          reason:
              'CouchDB needs the rev from the POST response as If-Match, or it '
              'refuses the attachment (FileUploader.getHeaderMap:85)',
        );
      },
    );

    test(
      'a metadata-only resource uploads its document and no attachment',
      () async {
        // Kotlin *requires* a file (`ResourcesRepositoryImpl.kt:235-238`) while
        // the port's form does not, so this row can exist. Its document is still
        // worth filing; there are simply no bytes to attach.
        await saveLocal();
        final row = await soleRow();
        expect(row.resourceLocalAddress, isNull);
        expect(row.resourceOffline, isFalse);

        await uploader.queuePending(config: config, user: user);
        stubPost(
          const NetworkSuccess<Map<String, dynamic>>({
            'id': 'srv-3',
            'rev': '1-c',
          }),
        );
        stubAttachment();

        expect(await drainer().drain(), [OutboxOutcome.completed]);

        expect((await soleRow()).couchId, 'srv-3');
        verifyNever(
          () => api.uploadAttachment(
            any(),
            bytes: any(named: 'bytes'),
            authHeader: any(named: 'authHeader'),
            contentType: any(named: 'contentType'),
            ifMatch: any(named: 'ifMatch'),
          ),
        );
      },
    );

    test(
      'the bell does not ask to re-download the resource just created',
      () async {
        // A defect the upload direction *introduces* if `downloadedRev` is left
        // behind. `watchResourcesNeedingUpdateCount` counts a shelf row where
        // `_rev IS NOT downloaded_rev`. Both are null before the upload and
        // SQLite's `IS NOT` between two nulls is false, so nothing is counted;
        // writing `_rev` alone flips it true and the user is told to download
        // the file already sitting under `ole/<id>/`.
        //
        // Kotlin writes only the two columns (`ResourcesRepositoryImpl
        // .kt:804-806`), so this is a deliberate divergence rather than a port.
        await saveLocal(path: (await pickedFile()).path);
        final counts = database.myLibraryDao.watchResourcesNeedingUpdateCount(
          user.id,
        );
        expect(await counts.first, 0);

        await uploader.queuePending(config: config, user: user);
        stubPost(
          const NetworkSuccess<Map<String, dynamic>>({
            'id': 'srv-2',
            'rev': '1-b',
          }),
        );
        stubAttachment();
        expect(await drainer().drain(), [OutboxOutcome.completed]);

        final saved = await soleRow();
        expect(saved.rev, isNotNull);
        expect(saved.downloadedRev, saved.rev);
        expect(
          await counts.first,
          0,
          reason:
              'the file on disk is the attachment of the revision just '
              'recorded — this device authored both',
        );
      },
    );

    test('the revision the attachment PUT returns is adopted', () async {
      // Kotlin reads only `ok` from that response (`FileUploader.kt:70-78`), so
      // its row stays a revision behind and the next resources sync pulls the
      // newer `_rev` over it — leaving `_rev != downloaded_rev` and the bell
      // asking for a re-download again, one sync later.
      await saveLocal(path: (await pickedFile()).path);
      await uploader.queuePending(config: config, user: user);
      stubPost(
        const NetworkSuccess<Map<String, dynamic>>({
          'id': 'srv-4',
          'rev': '1-d',
        }),
      );
      stubAttachment(
        const NetworkSuccess<Map<String, dynamic>>({
          'ok': true,
          'id': 'srv-4',
          'rev': '2-e',
        }),
      );

      expect(await drainer().drain(), [OutboxOutcome.completed]);

      final saved = await soleRow();
      expect(saved.rev, '2-e');
      expect(saved.downloadedRev, '2-e');
    });

    test('a metadata-only row claims nothing was downloaded', () async {
      // The other half of the `downloadedRev` rule: there are no bytes, so
      // saying they are current is the `resourceOffline` lie Phase 150 removed.
      await saveLocal();
      await uploader.queuePending(config: config, user: user);
      stubPost(
        const NetworkSuccess<Map<String, dynamic>>({
          'id': 'srv-6',
          'rev': '1-f',
        }),
      );
      stubAttachment();
      await drainer().drain();

      final saved = await soleRow();
      expect(saved.rev, '1-f');
      expect(saved.downloadedRev, isNull);
    });

    test(
      'a blank resourceLocalAddress attempts no attachment either',
      () async {
        // The metadata-only test covers a *null* address, so the blank case
        // was unpinned. No port writer produces a blank one —
        // `saveLocalResource` stores null when the copy is empty — which is
        // exactly why it needs a hand-built row rather than trust.
        //
        // Chasing this down changed the production code rather than confirming
        // it: mutation testing showed **neither** in-method guard could fail,
        // because `ResourceFiles.existingFileFor` refuses an empty filename on
        // its own. The redundant guard is gone and the remaining early return
        // is labelled an optimisation. This test stays because it pins the
        // *behaviour*, which is what matters, and would catch a `ResourceFiles`
        // change that stopped providing it.
        await saveLocal(path: (await pickedFile()).path);
        final row = await soleRow();
        await database.myLibraryDao.upsertAll([
          row.copyWith(resourceLocalAddress: const Value('')).toCompanion(true),
        ]);

        await uploader.queuePending(config: config, user: user);
        stubPost(
          const NetworkSuccess<Map<String, dynamic>>({
            'id': 'srv-8',
            'rev': '1-g',
          }),
        );
        stubAttachment();
        expect(await drainer().drain(), [OutboxOutcome.completed]);

        expect((await soleRow()).couchId, 'srv-8');
        verifyNever(
          () => api.uploadAttachment(
            any(),
            bytes: any(named: 'bytes'),
            authHeader: any(named: 'authHeader'),
            contentType: any(named: 'contentType'),
            ifMatch: any(named: 'ifMatch'),
          ),
        );
      },
    );

    test(
      'a private team resource gains its resourceLink, keyed on the CouchDB id',
      () async {
        // Port of `markResourceUploaded`'s second half
        // (`ResourcesRepositoryImpl.kt:808-816`), which this phase first omitted
        // on the false premise that the port had no `createLocalResourceLink`.
        // It has `TeamsRepository.addResourceLink`; the search was for the
        // Kotlin name rather than the behaviour.
        //
        // Without it a team leader's private resource uploads its own document
        // and **no team links to it**: the team's Resources tab is empty on
        // Planet and on every other member's handset, for bytes already on the
        // server.
        await saveLocal(
          path: (await pickedFile()).path,
          private: true,
          teamId: 'team-7',
        );
        await uploader.queuePending(config: config, user: user);
        stubPost(
          const NetworkSuccess<Map<String, dynamic>>({
            'id': 'srv-private',
            'rev': '1-p',
          }),
        );
        stubAttachment();

        expect(await drainer().drain(), [OutboxOutcome.completed]);

        final link = await database.teamDao.getById('link-1');
        expect(link, isNotNull, reason: 'the resourceLink row must be written');
        expect(link!.teamId, 'team-7');
        expect(link.docType, 'resourceLink');
        expect(link.teamType, 'local');
        expect(
          link.resourceId,
          'srv-private',
          reason:
              'the link must name the CouchDB document. `markUploaded` leaves '
              '`resourceId` at the local uuid, so linking before the POST — as '
              "`TeamResourceActions.add` does — would name a document that "
              'does not exist. Kotlin puts this inside markResourceUploaded '
              'for exactly this reason',
        );

        // Writing the row alone would leave the link local, so it is enqueued
        // the way `TeamResourceActions.add` enqueues one. Kotlin needs no
        // equivalent: its `updated = true` is swept by the team upload pass.
        final queued = await database.outboxDao.forItem(
          TeamsUploader.resourceType,
          'link-1',
        );
        expect(queued, hasLength(1));
        expect(queued.single.endpoint, 'https://planet.example.org/db/teams');
        expect(queued.single.payload, contains('"resourceId":"srv-private"'));
      },
    );

    test('a public resource gets no team link', () async {
      // Kotlin's condition is `isPrivate && !privateFor.isNullOrBlank()`, so
      // both halves matter — a public resource created from a team context
      // must not be linked.
      await saveLocal(path: (await pickedFile()).path);
      await uploader.queuePending(config: config, user: user);
      stubPost(
        const NetworkSuccess<Map<String, dynamic>>({
          'id': 'srv-a',
          'rev': '1-a',
        }),
      );
      stubAttachment();
      await drainer().drain();

      expect(await database.teamDao.getById('link-1'), isNull);
      expect(
        await database.outboxDao.forItem(TeamsUploader.resourceType, 'link-1'),
        isEmpty,
      );
    });

    test(
      'a privateFor with isPrivate false gets no team link either',
      () async {
        // Mutation testing found the `isPrivate` half of the gate pinned by
        // nothing: the public-resource test above has `privateFor` null, so the
        // team-id clause alone excludes it, and `saveLocalResource` moves the
        // two columns together so no port writer produces this shape.
        //
        // The clause is defence in depth — it is Kotlin's explicit two-part
        // condition, `isPrivate && !privateFor.isNullOrBlank()`
        // (`ResourcesRepositoryImpl.kt:809`) — so it gets a hand-built row
        // rather than a comment claiming it matters. A clause pinned by nothing
        // reads as coverage.
        await saveLocal(path: (await pickedFile()).path);
        final row = await soleRow();
        await database.myLibraryDao.upsertAll([
          row
              .copyWith(isPrivate: false, privateFor: const Value('team-9'))
              .toCompanion(true),
        ]);

        await uploader.queuePending(config: config, user: user);
        stubPost(
          const NetworkSuccess<Map<String, dynamic>>({
            'id': 'srv-b',
            'rev': '1-b',
          }),
        );
        stubAttachment();
        await drainer().drain();

        expect((await soleRow()).couchId, 'srv-b');
        expect(await database.teamDao.getById('link-1'), isNull);
      },
    );

    test('an attachment failure does not undo the uploaded document', () async {
      // Kotlin's ordering: the document is already filed before the bytes are
      // attempted, and `uploadDoc` swallows the failure. Re-POSTing would file
      // a second document to fix a missing attachment.
      await saveLocal(path: (await pickedFile()).path);
      await uploader.queuePending(config: config, user: user);
      stubPost(
        const NetworkSuccess<Map<String, dynamic>>({
          'id': 'srv-5',
          'rev': '1-e',
        }),
      );
      stubAttachment(
        const NetworkError<Map<String, dynamic>>(413, 'too large'),
      );

      expect(await drainer().drain(), [OutboxOutcome.completed]);
      expect((await soleRow()).couchId, 'srv-5');
      expect(await resources.pendingUploads(), isEmpty);
    });
  });
}

/// Restores the seam `setUp` overrode; see the note in
/// `local_resource_file_copy_test.dart` for why a real fallback beats a
/// thrower here.
Future<Directory> getApplicationDocumentsDirectoryFallback() async =>
    Directory.systemTemp;
