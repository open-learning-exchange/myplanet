import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/personals_repository.dart';
import 'package:myplanet/repository/personals_uploader.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late PersonalsRepository personals;
  late OutboxRepository outbox;
  late PersonalsUploader uploader;
  var clock = DateTime.fromMillisecondsSinceEpoch(1000);

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    clock = DateTime.fromMillisecondsSinceEpoch(1000);
    var nextId = 0;
    personals = PersonalsRepository(
      database.personalDao,
      now: () => clock,
      createId: () => 'note-${nextId++}',
    );
    outbox = OutboxRepository(database.outboxDao, now: () => clock);
    uploader = PersonalsUploader(api, personals, outbox, testDeviceIdentity);
  });
  tearDown(() => database.close());

  void stubPost(NetworkResult<Map<String, dynamic>> result) {
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((_) async => result);
  }

  OutboxDrainer drainer() => OutboxDrainer(
    api,
    outbox,
    handlers: {PersonalsUploader.type: uploader.handler},
  );

  test('serialize carries the fields Personal.serialize sends', () {
    final row = PersonalRow(
      id: 'note-1',
      isUploaded: false,
      title: 'Field notes',
      titleNormalized: 'field notes',
      description: 'What I saw',
      date: 555,
      userId: 'user-1',
      userName: 'ada',
    );

    expect(
      PersonalsRepository.serialize(
        row,
        uploadedAt: DateTime.fromMillisecondsSinceEpoch(999),
      ),
      {
        'title': 'Field notes',
        'uploadDate': 999,
        'createdDate': 555,
        'author': 'ada',
        'addedBy': 'ada',
        'description': 'What I saw',
        'resourceType': 'Activities',
        'private': true,
        'privateFor': {'users': 'user-1'},
      },
    );
  });

  test('serialize includes filename when a local path is present', () {
    final row = PersonalRow(
      id: 'note-2',
      isUploaded: false,
      title: 'Field notes',
      titleNormalized: 'field notes',
      date: 555,
      userId: 'user-1',
      userName: 'ada',
      path: '/storage/notes/photo.jpg',
    );

    final serialized = PersonalsRepository.serialize(row);
    expect(serialized['filename'], 'photo.jpg');
  });

  test('queuePending queues each un-uploaded note exactly once', () async {
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    await personals.create(userId: 'user-1', userName: 'ada', title: 'Two');

    expect(await uploader.queuePending(config: config, userId: 'user-1'), 2);
    expect(await outbox.due(), hasLength(2));

    // Calling again must not double-queue: `enqueue` keys on (type, itemId).
    await uploader.queuePending(config: config, userId: 'user-1');
    expect(await outbox.due(), hasLength(2));
  });

  test('the payload a sweep builds is stable across sweeps', () async {
    // `OutboxRepository.enqueue`'s memo compares the request it is being
    // handed against the one already recorded for this item, so a payload that
    // is not a pure function of the local row defeats it silently: the memo
    // never matches, and a note the server refused is POSTed again on every
    // sweep. `PersonalsRepository.serialize` defaults `uploadDate` to
    // `DateTime.now()`, so this was live until Phase 148 — and personals is
    // the worst place for it, because a personal note is an append with a
    // server-minted id and a duplicate cannot be detected afterwards.
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');

    await uploader.queuePending(config: config, userId: 'user-1');
    final first = (await outbox.due()).single.payload;

    // Wall-clock time moves between sweeps; nothing about the note does.
    clock = clock.add(const Duration(hours: 3));
    await uploader.queuePending(config: config, userId: 'user-1');

    expect((await outbox.due()).single.payload, first);
  });

  test('a refused note is POSTed once, however many sweeps run', () async {
    // The consequence of the test above, end to end. Five sweeps against a
    // server that refuses the request must produce one POST — before Phase 148
    // this produced five, each one a candidate duplicate document.
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    stubPost(const NetworkError<Map<String, dynamic>>(400, 'bad request'));

    for (var sweep = 0; sweep < 5; sweep++) {
      await uploader.queuePending(config: config, userId: 'user-1');
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
    expect(
      await database.outboxDao.forItem(PersonalsUploader.type, 'note-0'),
      hasLength(1),
    );
  });

  test('the endpoint carries no credentials', () {
    expect(
      PersonalsUploader.endpointFor(config),
      // `Uri.replace(userInfo: '')` also normalizes away :443, the default
      // port for https — same normalization as `credentialFreeBase`.
      'https://planet.example.org/db/resources',
      reason:
          'outbox.endpoint is persisted and survives schema upgrades, so '
          'a satellite:PIN@ userinfo would leave the server PIN in plaintext '
          'SQLite until the upload succeeds',
    );
    expect(PersonalsUploader.endpointFor(config), isNot(contains(config.pin)));
    expect(PersonalsUploader.endpointFor(config), isNot(contains('@')));
  });

  test('the queued row stores no credentials either', () async {
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    await uploader.queuePending(config: config, userId: 'user-1');

    final row = (await outbox.due()).single;
    expect(row.endpoint, isNot(contains(config.pin)));
    expect(row.payload, isNot(contains(config.pin)));
    final payload = jsonDecode(row.payload) as Map<String, dynamic>;
    for (final field in testDeviceFields.entries) {
      expect(payload, containsPair(field.key, field.value));
    }
  });

  test('a successful upload adopts the ids CouchDB assigned', () async {
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    await uploader.queuePending(config: config, userId: 'user-1');
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({
        'id': 'srv-1',
        'rev': '1-abc',
      }),
    );

    expect(await drainer().drain(), [OutboxOutcome.completed]);

    final saved = await database.personalDao.getById('note-0');
    expect(saved?.isUploaded, isTrue);
    expect(saved?.couchId, 'srv-1');
    expect(saved?.rev, '1-abc');
    expect(
      await personals.pendingUploads('user-1'),
      isEmpty,
      reason:
          'without adopting the ids this note would be posted again '
          'on every drain, one duplicate per drain',
    );
  });

  test('uploads the file attachment after the document succeeds', () async {
    final tempDir = await Directory.systemTemp.createTemp('personals_test');
    addTearDown(() => tempDir.delete(recursive: true));
    final file = File('${tempDir.path}/attach.txt')
      ..writeAsBytesSync(utf8.encode('hello'));

    await personals.create(
      userId: 'user-1',
      userName: 'ada',
      title: 'Note with file',
      path: file.path,
    );
    await uploader.queuePending(config: config, userId: 'user-1');

    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': '1-a'}),
    );
    when(
      () => api.uploadAttachment(
        'https://planet.example.org/db/resources/srv-1/attach.txt',
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: 'text/plain',
        ifMatch: '1-a',
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({
        'ok': true,
        'id': 'srv-1',
        'rev': '2-b',
      }),
    );

    expect(await drainer().drain(), [OutboxOutcome.completed]);
    verify(
      () => api.uploadAttachment(
        'https://planet.example.org/db/resources/srv-1/attach.txt',
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: 'text/plain',
        ifMatch: '1-a',
      ),
    ).called(1);

    final saved = await database.personalDao.getById('note-0');
    expect(saved?.isUploaded, isTrue);
    expect(saved?.couchId, 'srv-1');
    expect(
      saved?.rev,
      '2-b',
      reason:
          "Kotlin's `finalRev`: `getString(\"rev\", response.body())"
          '.ifBlank { rev }` (`PersonalsRepositoryImpl:152`). A CouchDB '
          'attachment PUT bumps the revision, so keeping the POST rev here '
          'would leave the note holding a stale `_rev` and earn the next '
          'write against this document a 409 for no reason. This expected '
          "'1-a' until Phase 160.",
    );
  });

  test('a failed upload leaves the note pending for the next drain', () async {
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    await uploader.queuePending(config: config, userId: 'user-1');
    stubPost(const NetworkError<Map<String, dynamic>>(503, 'unavailable'));

    expect(await drainer().drain(), [OutboxOutcome.retryScheduled]);

    final saved = await database.personalDao.getById('note-0');
    expect(saved?.isUploaded, isFalse);
    expect(await personals.pendingUploads('user-1'), hasLength(1));

    // The retry succeeds and the note settles.
    clock = clock.add(const Duration(minutes: 1));
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': '1-a'}),
    );
    expect(await drainer().drain(), [OutboxOutcome.completed]);
    expect(await personals.pendingUploads('user-1'), isEmpty);
  });

  test('a response without ids does not mark the note uploaded', () async {
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    await uploader.queuePending(config: config, userId: 'user-1');
    stubPost(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));

    await drainer().drain();

    expect(
      (await database.personalDao.getById('note-0'))?.isUploaded,
      isFalse,
      reason:
          'nothing to adopt, so the note stays pending rather than '
          'claiming a sync that cannot be proven',
    );
  });

  test('the queued payload survives a restart of the queue object', () async {
    await personals.create(
      userId: 'user-1',
      userName: 'ada',
      title: 'Durable',
      description: 'still here',
    );
    await uploader.queuePending(config: config, userId: 'user-1');

    // A new repository over the same database is what a fresh process sees.
    final reopened = OutboxRepository(database.outboxDao, now: () => clock);
    final due = await reopened.due();
    expect(due, hasLength(1));
    expect(due.single.endpoint, PersonalsUploader.endpointFor(config));
  });

  // ---------------------------------------------------------------------
  // Phase 160: an attachment that never reaches CouchDB.
  //
  // Port of the three properties master gave `PersonalsRepositoryImpl`
  // (`updateRemoteDocRef`; the early returns at `:145`/`:150`; the
  // skip-the-POST guard at `:119-122`). The defect these close is *two states
  // that look identical*: before them, `markUploaded` ran before the
  // attachment and only `log`ged its failure, so a note whose bytes never
  // arrived was permanently `isUploaded == true` — byte for byte the row of a
  // note whose bytes did arrive.
  //
  // Every test below was mutation-checked by reverting the property it names
  // and confirming it goes red; where a fixture could pass under both
  // readings, the decoy is documented at the value.
  // ---------------------------------------------------------------------

  /// Writes a note with a real file on disk and queues it. Returns the path.
  Future<String> noteWithFile({String contents = 'hello'}) async {
    final tempDir = await Directory.systemTemp.createTemp('personals_test');
    addTearDown(() => tempDir.delete(recursive: true));
    final file = File('${tempDir.path}/attach.txt')
      ..writeAsBytesSync(utf8.encode(contents));
    await personals.create(
      userId: 'user-1',
      userName: 'ada',
      title: 'Note with file',
      path: file.path,
    );
    await uploader.queuePending(config: config, userId: 'user-1');
    return file.path;
  }

  void stubAttachment(NetworkResult<Map<String, dynamic>> result) {
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

  int postCount() => verify(
    () =>
        api.postJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
  ).callCount;

  test(
    'a failed attachment leaves the note pending and does not re-POST it',
    () async {
      // **The decisive test, and both halves belong in one.** Either alone
      // passes under a broken implementation:
      //
      //  * `isUploaded == false` alone is satisfied by a handler that reports
      //    the attachment failure but re-POSTs the document on every retry —
      //    one duplicate per drain, undetectable afterwards because a personal
      //    note is an append with a server-minted id.
      //  * `postCount() == 1` alone is satisfied by the *pre-fix* code, which
      //    completed the outbox row on the POST's success and so was never
      //    re-drained at all. It reached one POST by losing the file.
      //
      // Together they say: the document landed once, the note knows the
      // attachment did not, and the retry re-sends only what is outstanding.
      await noteWithFile();
      stubPost(
        const NetworkSuccess<Map<String, dynamic>>({
          'id': 'srv-1',
          'rev': '1-a',
        }),
      );
      // 503 rather than 400: a transient refusal keeps the row on the ladder,
      // which is what gives this test a second drain to make its second
      // assertion about. The terminal case has its own test below.
      stubAttachment(
        const NetworkError<Map<String, dynamic>>(503, 'unavailable'),
      );

      expect(await drainer().drain(), [OutboxOutcome.retryScheduled]);

      final afterFirst = await database.personalDao.getById('note-0');
      expect(
        afterFirst?.isUploaded,
        isFalse,
        reason:
            'the file never reached CouchDB, so nothing may report the note '
            'as uploaded — this is the state that did not exist before '
            'Phase 160',
      );
      expect(
        afterFirst?.couchId,
        'srv-1',
        reason:
            'and the ids the POST assigned are recorded anyway: without them '
            'the retry could not tell "the document landed" from "nothing '
            'landed", which is the whole discriminator',
      );
      expect(afterFirst?.rev, '1-a');
      expect(await personals.pendingUploads('user-1'), hasLength(1));

      // The retry: the attachment succeeds this time.
      clock = clock.add(const Duration(minutes: 5));
      stubAttachment(
        const NetworkSuccess<Map<String, dynamic>>({'ok': true, 'rev': '2-b'}),
      );
      expect(await drainer().drain(), [OutboxOutcome.completed]);

      expect(
        postCount(),
        1,
        reason:
            'the second drain must skip the POST — the document is already '
            'on the server and a second one could never be detected',
      );
      final settled = await database.personalDao.getById('note-0');
      expect(settled?.isUploaded, isTrue);
      expect(settled?.couchId, 'srv-1');
      expect(settled?.rev, '2-b');
      expect(await personals.pendingUploads('user-1'), isEmpty);
    },
  );

  test(
    'an attachment the server refuses is terminal, and never re-POSTs',
    () async {
      // 400 is a verdict on the bytes, so the ladder is not the answer: the row
      // is abandoned carrying the reason and the note stays pending. What must
      // not happen is the sweep reading that pending note as a fresh upload.
      await noteWithFile();
      stubPost(
        const NetworkSuccess<Map<String, dynamic>>({
          'id': 'srv-1',
          'rev': '1-a',
        }),
      );
      stubAttachment(
        const NetworkError<Map<String, dynamic>>(400, 'bad bytes'),
      );

      expect(await drainer().drain(), [OutboxOutcome.abandoned]);

      // Five sweeps, each the shape `PersonalActions._queuePending` produces
      // after any user write, against a note whose document is already filed.
      for (var sweep = 0; sweep < 5; sweep++) {
        await uploader.queuePending(config: config, userId: 'user-1');
        await drainer().drain();
        clock = clock.add(const Duration(hours: 1));
      }

      expect(
        postCount(),
        1,
        reason:
            'the memo holds the row and the skip-the-POST guard holds the '
            'handler. Note what this pins and what it does not: with a 400 '
            'the row is abandoned on attempt 1, so `due()` is empty and the '
            'count stays 1 whether or not the guard exists — mutation-proven. '
            'The conjunction is what is pinned here; the guard alone is '
            'pinned by the two tests above',
      );
      final row = (await database.outboxDao.forItem(
        PersonalsUploader.type,
        'note-0',
      )).single;
      expect(row.status, OutboxDao.statusAbandoned);
      expect(
        row.errorMessage,
        'bad bytes',
        reason:
            'the refusal is inspectable rather than swallowed by a log line',
      );
      expect(
        (await database.personalDao.getById('note-0'))?.isUploaded,
        isFalse,
      );
    },
  );

  test('a missing attachment file is recorded, not swallowed', () async {
    // Pre-Phase-160 this was a silent `return` that still marked the note
    // uploaded. `my_library` settled the same question the other way and for
    // the reason that applies here: "the bytes are not where the row says they
    // are" is not evidence the attachment was delivered.
    final path = await noteWithFile();
    await File(path).delete();
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': '1-a'}),
    );

    expect(await drainer().drain(), [OutboxOutcome.retryScheduled]);

    verifyNever(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    );
    expect((await database.personalDao.getById('note-0'))?.isUploaded, isFalse);
    final row = (await database.outboxDao.forItem(
      PersonalsUploader.type,
      'note-0',
    )).single;
    expect(
      row.httpCode,
      isNull,
      reason:
          'transient, not terminal: a missing file is about the environment — '
          'an unmounted card — not about the bytes, and Kotlin stays pending '
          'and retryable here too. A null status is what `classifyStatus` '
          'reads as transient, which is what lets a later sweep re-arm it',
    );
    expect(row.status, OutboxDao.statusPending);
    expect(row.errorMessage, contains('missing'));

    // And the file coming back is all it takes.
    clock = clock.add(const Duration(minutes: 5));
    File(path).writeAsBytesSync(utf8.encode('hello'));
    stubAttachment(
      const NetworkSuccess<Map<String, dynamic>>({'ok': true, 'rev': '2-b'}),
    );
    expect(await drainer().drain(), [OutboxOutcome.completed]);
    expect(postCount(), 1);
    expect((await database.personalDao.getById('note-0'))?.isUploaded, isTrue);
  });

  test('a note with no attachment still settles on the POST alone', () async {
    // The guard against over-correcting: most notes carry no file, and for
    // those the POST *is* the whole delivery. A handler that treated "no
    // attachment" as "attachment not delivered" would strand every plain note
    // in the pending set for ever.
    await personals.create(userId: 'user-1', userName: 'ada', title: 'Plain');
    await uploader.queuePending(config: config, userId: 'user-1');
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': '1-a'}),
    );

    expect(await drainer().drain(), [OutboxOutcome.completed]);

    final saved = await database.personalDao.getById('note-0');
    expect(saved?.isUploaded, isTrue);
    expect(saved?.rev, '1-a');
  });

  test('the skip-the-POST guard reads _rev, not _id', () async {
    // `create` seeds `couchId` with the note's own local id, exactly as
    // `savePersonalResource` does (`_id = id`), so `_id` is non-null from the
    // first insert and says nothing about whether the server has seen the
    // note. A guard reading it would skip the POST of every note ever written
    // and PUT the attachment at a document that does not exist.
    await personals.create(userId: 'user-1', userName: 'ada', title: 'Plain');
    expect(
      (await database.personalDao.getById('note-0'))?.couchId,
      'note-0',
      reason: 'the decoy this test exists for: `_id` is already set',
    );
    expect((await database.personalDao.getById('note-0'))?.rev, isNull);

    await uploader.queuePending(config: config, userId: 'user-1');
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': '1-a'}),
    );
    await drainer().drain();

    expect(postCount(), 1, reason: 'the note had to be POSTed at all');
  });

  test('a blank id or rev in the response is not adopted', () async {
    // The empty string is not "no id": `couchId is! String` passes it, and
    // writing it would defeat the skip-the-POST guard on the retry and file
    // the duplicate the guard exists to prevent.
    // **Both halves, because the fixture for one cannot distinguish the
    // other.** A first cut stubbed only a blank `rev`, and deleting the
    // `postedId.isEmpty` disjunct left the suite green — the test's name
    // asserted a property its fixture never exercised.
    await personals.create(
      userId: 'user-1',
      userName: 'ada',
      title: 'Blank rev',
    );
    await uploader.queuePending(config: config, userId: 'user-1');
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': ''}),
    );
    await drainer().drain();

    var saved = await database.personalDao.getById('note-0');
    expect(saved?.isUploaded, isFalse);
    expect(saved?.rev, isNull);
    expect(saved?.couchId, 'note-0', reason: 'still the local id');

    clock = clock.add(const Duration(hours: 1));
    await personals.create(
      userId: 'user-1',
      userName: 'ada',
      title: 'Blank id',
    );
    await uploader.queuePending(config: config, userId: 'user-1');
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': '', 'rev': '1-a'}),
    );
    await drainer().drain();

    saved = await database.personalDao.getById('note-1');
    expect(saved?.isUploaded, isFalse);
    expect(saved?.rev, isNull);
    expect(
      saved?.couchId,
      'note-1',
      reason:
          'a blank id must not be written over the local one: the attachment '
          'URL would address `.../resources//name`',
    );
  });

  test('an already-uploaded note is not sent a second time', () async {
    // Kotlin's `if (personal.isUploaded) return "Resource already uploaded"`
    // (`PersonalsRepositoryImpl:113-116`). Reachable when a drain claims a row
    // another pass has already settled.
    //
    // **The note must carry a file, and the assertion must be about the
    // attachment.** A first cut used a plain note and asserted no POST — and
    // stayed green with this early return deleted, because the skip-the-POST
    // guard below it fires on the same row and suppresses the POST anyway.
    // The PUT is the only request the early return uniquely prevents, so it
    // is the only thing that distinguishes the two readings.
    final path = await noteWithFile();
    await personals.markUploaded('note-0', 'srv-1', '1-a', deliveredPath: path);

    expect(await drainer().drain(), [OutboxOutcome.completed]);

    verifyNever(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    );
    verifyNever(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    );
  });

  // -------------------------------------------------------------------
  // What the second `parity-auditor` pass found in this lane's own
  // finished, green code. All three are states the retry loop introduced:
  // the commit added a loop over a durable queue whose subject can change
  // or vanish underneath it, and nothing reconciled the two.
  // -------------------------------------------------------------------

  test('a note deleted mid-retry is not POSTed a second time', () async {
    // 1. POST lands, attachment 503s, the row goes back on the ladder.
    // 2. The user deletes the note. `PersonalActions.delete` does not cancel
    //    the outbox row, so the operation outlives its subject.
    // 3. The retry reads no row, so the skip-the-POST guard has no ids —
    //    and without this check it POSTs again, filing a *second* private
    //    resource document for a note the user deleted, referenced by
    //    nothing and undetectable afterwards.
    await noteWithFile();
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': '1-a'}),
    );
    stubAttachment(
      const NetworkError<Map<String, dynamic>>(503, 'unavailable'),
    );
    expect(await drainer().drain(), [OutboxOutcome.retryScheduled]);

    await personals.delete('note-0');
    clock = clock.add(const Duration(minutes: 5));

    expect(await drainer().drain(), [OutboxOutcome.abandoned]);
    expect(
      postCount(),
      1,
      reason:
          'Kotlin cannot reach this — `getPendingPersonalUploads` reads the '
          'live table, so a deleted note is not in the list it iterates. The '
          "port's queue is durable, so the handler makes the same check",
    );
    final row = (await database.outboxDao.forItem(
      PersonalsUploader.type,
      'note-0',
    )).single;
    expect(row.httpCode, OutboxRepository.notSent);
    expect(row.status, OutboxDao.statusAbandoned);
  });

  test(
    'an edit landing mid-drain does not lose the newly attached file',
    () async {
      // The PUT for the *old* file is on the wire when the user attaches a new
      // one. `update` clears `isUploaded` precisely so the new file is sent;
      // `markUploaded` writing it back unconditionally would go straight over
      // that, and — since `markCompleted` only deletes an `in_progress` row and
      // the re-enqueue has put this one back to `pending` — the next drain
      // would see a note flagged delivered and retire the row. The new file
      // would never be uploaded and nothing would say so.
      final path = await noteWithFile();
      stubPost(
        const NetworkSuccess<Map<String, dynamic>>({
          'id': 'srv-1',
          'rev': '1-a',
        }),
      );
      // **The edit has to land *during* the PUT**, which is the whole window:
      // after the handler has read the row it will pass to `markUploaded`,
      // and before that write. Editing before the drain instead just hands
      // the handler the new path, which is a different and harmless story —
      // a first cut did that and failed for the unrelated reason that the
      // new file does not exist on disk.
      when(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: any(named: 'contentType'),
          ifMatch: any(named: 'ifMatch'),
        ),
      ).thenAnswer((_) async {
        await personals.update(
          id: 'note-0',
          title: 'Note with file',
          path: '$path.v2',
        );
        return const NetworkSuccess<Map<String, dynamic>>({
          'ok': true,
          'rev': '2-b',
        });
      });

      await drainer().drain();

      final saved = await database.personalDao.getById('note-0');
      expect(
        saved?.isUploaded,
        isFalse,
        reason:
            'the bytes that landed came from the old path, so the note is not '
            'delivered — the file the user just attached has not been sent',
      );
      expect(
        saved?.couchId,
        'srv-1',
        reason: 'the document is recorded either way',
      );
      expect(saved?.rev, '2-b');
      expect(await personals.pendingUploads('user-1'), hasLength(1));
    },
  );

  test('a 409 on the attachment is answered with one rev refresh', () async {
    // Reachable on the ordinary bad link this app is built for: a PUT that
    // *lands* whose response is lost retries with the `_rev` from before the
    // PUT, which the PUT itself bumped. Without the refresh, `classifyStatus`
    // reads 409 as `rejected`, the row is abandoned on attempt 1, the memo
    // refuses it for ever, and the note sits pending with its file already on
    // the server and no in-app route back.
    await noteWithFile();
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': '1-a'}),
    );
    var puts = 0;
    when(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    ).thenAnswer((invocation) async {
      puts++;
      return invocation.namedArguments[#ifMatch] == '3-server'
          ? const NetworkSuccess<Map<String, dynamic>>({
              'ok': true,
              'rev': '4-d',
            })
          : const NetworkError<Map<String, dynamic>>(409, 'conflict');
    });
    when(
      () => api.getJsonObject(
        'https://planet.example.org/db/resources/srv-1',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({
        '_id': 'srv-1',
        '_rev': '3-server',
      }),
    );

    expect(await drainer().drain(), [OutboxOutcome.completed]);

    expect(puts, 2, reason: 'one refresh, never a loop');
    final saved = await database.personalDao.getById('note-0');
    expect(saved?.isUploaded, isTrue);
    expect(saved?.rev, '4-d');
  });

  test('a second 409 after the refresh stands as the refusal it is', () async {
    // The bound. A refresh that does not help is returned, not retried again.
    await noteWithFile();
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': '1-a'}),
    );
    stubAttachment(const NetworkError<Map<String, dynamic>>(409, 'conflict'));
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({
        '_id': 'srv-1',
        '_rev': '3-server',
      }),
    );

    expect(await drainer().drain(), [OutboxOutcome.abandoned]);
    verify(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    ).called(2);
    expect((await database.personalDao.getById('note-0'))?.isUploaded, isFalse);
  });

  test(
    'a blank rev on the attachment response falls back to the POST rev',
    () async {
      // Kotlin's `.ifBlank { rev }` (`PersonalsRepositoryImpl:152`), and it is
      // load-bearing rather than defensive: writing a blank `_rev` here would
      // defeat the skip-the-POST guard on the next drain and file the duplicate
      // that guard exists to prevent. Asserted nowhere until the second audit
      // pass mutated `.isNotEmpty` away and the suite stayed green.
      await noteWithFile();
      stubPost(
        const NetworkSuccess<Map<String, dynamic>>({
          'id': 'srv-1',
          'rev': '1-a',
        }),
      );
      stubAttachment(
        const NetworkSuccess<Map<String, dynamic>>({'ok': true, 'rev': ''}),
      );

      expect(await drainer().drain(), [OutboxOutcome.completed]);

      final saved = await database.personalDao.getById('note-0');
      expect(saved?.isUploaded, isTrue);
      expect(saved?.rev, '1-a', reason: 'the POST rev, not the blank one');
    },
  );
}
