import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/submissions_repository.dart';

/// Upload a submission, then pull the same document back.
///
/// The two halves live in one file but had never been run against each other.
/// `serialize` emitted a top-level `userId` that no Planet document carries and
/// `upsertDocuments` read exactly that key, so the pair agreed with itself and
/// disagreed with the server: a submission the learner made on Planet web, or on
/// another handset, arrived with `userId == null` and could not appear in
/// `watchForUser`. Kotlin derives it from the nested user object
/// (`normalizeSubmissionUserId(JsonUtils.getString("_id", userJson))`,
/// `SubmissionsRepositoryImpl.kt:680`) and its uploader emits no top-level
/// `userId` at all (`serializeSubmission`, `:813-862`).
///
/// Same shape as Phase 74's reactions and Phase 116's community share: each
/// half had a passing test and only the pair was wrong.
class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late SubmissionsRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = SubmissionsRepository(
      MockPlanetApi(),
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
    );
  });
  tearDown(() => database.close());

  /// The `_id`/`_rev` CouchDB stamps onto a document the app POSTed, plus the
  /// keys Planet adds. Everything else is the app's own payload.
  Map<String, dynamic> asStoredByCouch(
    Map<String, dynamic> payload, {
    String id = 'couch-1',
    String rev = '1-abc',
  }) => {...payload, '_id': id, '_rev': rev};

  group('a submission this app uploaded survives being pulled back', () {
    test('the learner still owns it after the round trip', () async {
      final localId = await repository.createDraft(
        userId: 'org.couchdb.user:ada',
        type: 'survey',
        title: 'Water survey',
        answers: const [SubmissionDraftAnswer(questionId: 'q1', value: 'Yes')],
      );
      final row = await repository.getById(localId);
      final payload = await repository.serialize(row!);

      await repository.upsertDocuments([asStoredByCouch(payload)]);

      final pulled = await repository.getById('couch-1');
      expect(
        pulled?.userId,
        'org.couchdb.user:ada',
        reason:
            'the pull could not recover the owner from the document the '
            'uploader produced, so the submission vanished from the list',
      );
    });

    test(
      'the uploaded document carries the owner where Planet looks',
      () async {
        final localId = await repository.createDraft(
          userId: 'org.couchdb.user:ada',
          type: 'survey',
          title: 'Water survey',
          answers: const [],
        );
        final payload = await repository.serialize(
          (await repository.getById(localId))!,
        );

        final user = payload['user'];
        expect(
          user,
          isA<Map<String, dynamic>>(),
          reason: 'Kotlin uploads `user` as an object, never a bare string',
        );
        expect((user as Map)['_id'], 'org.couchdb.user:ada');
      },
    );
  });

  group('a document Planet wrote', () {
    // Shaped like a real `submissions` document: the owner lives in the nested
    // user object and there is no top-level `userId`.
    Map<String, dynamic> planetDocument({
      String userId = 'org.couchdb.user:ada@lea',
    }) => {
      '_id': 'planet-1',
      '_rev': '3-ffe',
      'parentId': 'exam-1@course-1',
      'type': 'exam',
      'status': 'complete',
      'startTime': 1000,
      'lastUpdateTime': 2000,
      'grade': 4,
      'user': {
        '_id': userId,
        'name': 'ada',
        'planetCode': 'lea',
        'membershipDoc': {'teamId': 'team-9'},
      },
      'parent': {'_id': 'exam-1', 'name': 'Week 1 quiz'},
      'answers': [
        {'questionId': 'q1', 'value': 'Yes', 'mistakes': 0, 'passed': true},
      ],
    };

    test('reaches the list under the signed-in learner', () async {
      await repository.upsertDocuments([planetDocument()]);

      final rows = await repository.watchForUser('org.couchdb.user:ada').first;
      expect(
        rows.map((row) => row.id),
        ['planet-1'],
        reason:
            'the reader looked for a top-level `userId` key that no Planet '
            'document has',
      );
    });

    test('stores parent and user as JSON, not a Dart map literal', () async {
      await repository.upsertDocuments([planetDocument()]);
      final row = await repository.getById('planet-1');

      expect(
        () => jsonDecode(row!.parent!),
        returnsNormally,
        reason:
            'the column held `{_id: exam-1, name: Week 1 quiz}` — Map.toString, '
            'not JSON — so every jsonDecode of it threw',
      );
      expect(jsonDecode(row!.parent!), {
        '_id': 'exam-1',
        'name': 'Week 1 quiz',
      });
      expect((jsonDecode(row.user!) as Map)['name'], 'ada');
    });

    test('re-uploads Planet objects rather than their string form', () async {
      await repository.upsertDocuments([planetDocument()]);
      final payload = await repository.serialize(
        (await repository.getById('planet-1'))!,
      );

      expect(payload['parent'], isA<Map<String, dynamic>>());
      expect((payload['parent'] as Map)['name'], 'Week 1 quiz');
      expect((payload['user'] as Map)['name'], 'ada');
    });

    test('counts as turned in, and as nothing left to upload', () async {
      await repository.upsertDocuments([planetDocument()]);
      final row = await repository.getById('planet-1');

      expect(
        row!.uploaded,
        isTrue,
        reason:
            'Kotlin derives `uploaded` from a non-empty `_rev`; the port read a '
            'top-level `uploaded` key, so every synced submission rendered '
            '"not turned in"',
      );
      expect(row.isUpdated, isFalse);
    });

    // `isUpdated = false` is hard-coded (`:700`). Reading it from the document
    // would let the server decide what this device still owes it — and a row
    // that arrives claiming `isUpdated` re-uploads itself on every sync.
    test(
      'the server cannot mark a pulled row as still owing an upload',
      () async {
        await repository.upsertDocuments([
          {...planetDocument(), 'isUpdated': true},
        ]);
        expect((await repository.getById('planet-1'))!.isUpdated, isFalse);
        expect(
          await repository.pendingUploads('org.couchdb.user:ada'),
          isEmpty,
        );
      },
    );

    // `userJson.remove("_attachments")` (`:678`) — a base64 profile photo
    // inside the blob can push one row past SQLite's cursor window, which is a
    // crash on a later `SELECT *`, not a display problem.
    test('strips the user attachments before storing the blob', () async {
      await repository.upsertDocuments([
        {
          '_id': 'planet-1',
          '_rev': '1-a',
          'user': {
            '_id': 'org.couchdb.user:ada',
            'name': 'ada',
            '_attachments': {'img': {}},
          },
        },
      ]);
      final stored =
          jsonDecode((await repository.getById('planet-1'))!.user!) as Map;

      expect(stored.containsKey('_attachments'), isFalse);
      expect(stored['name'], 'ada');
    });

    // Without `couchId` a re-upload has no `_id` to PUT against, so
    // `serialize` would POST a duplicate document.
    test('records the CouchDB id so a re-upload updates in place', () async {
      await repository.upsertDocuments([planetDocument()]);
      final row = (await repository.getById('planet-1'))!;

      expect(row.couchId, 'planet-1');
      expect(row.rev, '3-ffe');
      expect((await repository.serialize(row))['_id'], 'planet-1');
    });

    test('takes its team from the embedded membership document', () async {
      await repository.upsertDocuments([planetDocument()]);
      expect((await repository.getById('planet-1'))!.teamId, 'team-9');
    });

    test('normalizes a planet-suffixed owner id', () async {
      await repository.upsertDocuments([planetDocument(userId: 'ada@lea')]);
      expect(
        (await repository.getById('planet-1'))!.userId,
        'org.couchdb.user:ada',
      );
    });
  });

  group('the walk leaves local work alone', () {
    const config = ServerConfig(
      serverUrl: 'https://planet.example.org',
      pin: '1234',
      couchDbUrl: 'https://satellite:1234@planet.example.org:443',
    );
    late MockPlanetApi api;

    setUp(() {
      api = MockPlanetApi();
      repository = SubmissionsRepository(
        api,
        database.submissionDao,
        database.submitPhotosDao,
        database.surveyDao,
      );
    });

    void serve(int total, List<Map<String, dynamic>> docs) {
      when(
        () => api.getJsonObject(
          any(that: contains('_all_docs?limit=0')),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async => NetworkSuccess({'total_rows': total}));
      when(
        () => api.getJsonObject(
          any(that: contains('include_docs=true')),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess({
          'rows': [
            for (final doc in docs) {'doc': doc},
          ],
        }),
      );
    }

    test('an uploaded submission survives the next full walk', () async {
      final localId = await repository.createDraft(
        userId: 'org.couchdb.user:ada',
        type: 'survey',
        title: 'Water survey',
        answers: const [SubmissionDraftAnswer(questionId: 'q1', value: 'Yes')],
      );
      await repository.markUploaded(localId, 'couch-1', '1-abc');

      // The walk sees the document under its CouchDB id; the local row keeps
      // its sha1. A prune over that keep set deletes the learner's own row.
      serve(1, [
        {
          '_id': 'couch-1',
          '_rev': '1-abc',
          'type': 'survey',
          'user': {'_id': 'org.couchdb.user:ada'},
        },
      ]);
      expect(await repository.sync(config: config), isA<SyncComplete>());

      expect(
        await repository.getById(localId),
        isNotNull,
        reason:
            'the prune keyed on CouchDB ids while a locally authored row keeps '
            'its sha1 id, and only spared isUpdated rows — so clearing that '
            'flag on upload made the learner own attempt deletable',
      );
      expect((await repository.answersFor(localId)).length, 1);
    });

    test('an empty server database deletes nothing', () async {
      final localId = await repository.createDraft(
        userId: 'org.couchdb.user:ada',
        type: 'survey',
        title: 'Water survey',
        answers: const [],
      );
      await repository.markUploaded(localId, 'couch-1', '1-abc');

      serve(0, const []);
      expect(await repository.sync(config: config), isA<SyncComplete>());

      expect(await repository.getById(localId), isNotNull);
    });

    // The severest case, and the one `createDraft` cannot demonstrate:
    // `getOrCreateSurveySubmission` writes `isUpdated: false` from the start
    // (so does `_openExamSession`), which is exactly what the old prune did
    // *not* spare. A pending survey sheet the learner has started, and an exam
    // attempt in progress, were deleted by the next sync before any upload.
    test('a pending sheet that was never uploaded survives too', () async {
      final row = await repository.getOrCreateSurveySubmission(
        userId: 'org.couchdb.user:ada',
        parentId: 'survey-1',
      );
      expect(
        row.isUpdated,
        isFalse,
        reason: 'the case the prune did not spare',
      );

      serve(0, const []);
      await repository.sync(config: config);

      expect(await repository.getById(row.id), isNotNull);
    });
  });

  group('the list collapses what the walk duplicates', () {
    SubmissionRow row(String id, {String? parentId, int lastUpdateTime = 0}) =>
        SubmissionRow(
          id: id,
          parentId: parentId,
          startTime: 0,
          lastUpdateTime: lastUpdateTime,
          grade: 0,
          uploaded: false,
          isUpdated: false,
        );

    // The port keeps a locally authored row's sha1 primary key after upload,
    // so the walk pulls the same document back under its CouchDB `_id`. Kotlin
    // has the identical duplicate (its local key is a UUID) and hides it with
    // `groupBy(parentId)` + newest wins (`SubmissionViewModel.kt:67-73`) — not
    // by deleting anything.
    test('two rows for one attempt render once, newest first', () {
      final entries = collapseSubmissionsByParent([
        row('sha1-local', parentId: 'exam-1@course-1', lastUpdateTime: 10),
        row('couch-1', parentId: 'exam-1@course-1', lastUpdateTime: 20),
      ]);

      expect(entries.length, 1);
      expect(entries.single.row.id, 'couch-1');
      expect(entries.single.count, 2);
    });

    test('different parents stay separate, newest first', () {
      final entries = collapseSubmissionsByParent([
        row('a', parentId: 'exam-1', lastUpdateTime: 10),
        row('b', parentId: 'exam-2', lastUpdateTime: 30),
      ]);
      expect(entries.map((e) => e.row.id), ['b', 'a']);
      expect(entries.every((e) => e.count == 1), isTrue);
    });

    // `createDraft` — the list's own New submission button — leaves `parentId`
    // null, and Kotlin's `groupBy` would fold every such draft into one.
    test('drafts with no parent are never folded together', () {
      final entries = collapseSubmissionsByParent([
        row('draft-1', lastUpdateTime: 10),
        row('draft-2', lastUpdateTime: 20),
      ]);
      expect(entries.map((e) => e.row.id), ['draft-2', 'draft-1']);
    });
  });

  group('what the list and detail screens read', () {
    test('a JSON parent renders its name, not the serialized object', () async {
      await repository.upsertDocuments([
        {
          '_id': 'planet-1',
          '_rev': '1-a',
          'user': {'_id': 'org.couchdb.user:ada', 'name': 'Ada Lovelace'},
          'parent': {'_id': 'exam-1', 'name': 'Week 1 quiz'},
        },
      ]);
      final stored = (await repository.getById('planet-1'))!;

      expect(submissionDisplayTitle(stored), 'Week 1 quiz');
      expect(submissionSubmitterName(stored), 'Ada Lovelace');
    });

    test('a plain-title draft keeps its title', () async {
      final id = await repository.createDraft(
        userId: 'org.couchdb.user:ada',
        type: 'survey',
        title: 'Water survey',
        answers: const [],
      );
      final stored = (await repository.getById(id))!;

      expect(submissionDisplayTitle(stored), 'Water survey');
      expect(submissionSubmitterName(stored), isNull);
    });
  });
}
