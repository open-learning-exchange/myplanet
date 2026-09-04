import 'dart:convert';

import 'package:drift/drift.dart' show Value, driftRuntimeOptions;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/converters.dart';
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
  // The cross-device group below opens a second in-memory database on purpose:
  // two handsets, two separate executors, which is the case drift's warning is
  // not about.
  driftRuntimeOptions.dontWarnAboutMultipleDatabases = true;

  late AppDatabase database;
  late SubmissionsRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = SubmissionsRepository(
      MockPlanetApi(),
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
      database.examDao,
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
        expect(await repository.pendingUploads(), isEmpty);
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

  /// **The cross-device half of the round trip.** Everything above uploads and
  /// pulls through one database; this group builds a *second* one that has
  /// never seen the exam or the survey, which is what a real second handset
  /// looks like before it has synced the `exams` database — and what Planet
  /// web looks like always.
  ///
  /// Kotlin's `serializeSubmission` prefers the **live** exam over the stored
  /// blob: `getPayloadData` (`SubmissionsRepositoryImpl.kt:756-761`) resolves
  /// `examDao.getById(parentId.substringBefore("@"))` plus its questions and
  /// emits `StepExam.serializeExam(exam, questions)`, whose `StepExam.kt:92`
  /// adds the `questions` array. The stored blob is only the fallback for an
  /// exam this device no longer has (`:842`).
  ///
  /// The port always sent the blob, which `_openExamSession` writes as
  /// `{_id,_rev,name,courseId,totalMarks}` and `createSurveyDraft` as
  /// `{_id,name}` — no questions at all. The port's own pull then fills
  /// `submission_questions` from `parent['questions']`, so the answer sheet
  /// arrived on the second device with answers and nothing to put them
  /// against. Writer and reader disagreeing about a key, each half passing its
  /// own test: Phase 74's reactions, Phase 100's photo id, Phase 116's feed.
  group('a submission pulled on a device that has never seen the exam', () {
    late AppDatabase secondDevice;
    late SubmissionsRepository secondRepository;

    setUp(() {
      secondDevice = AppDatabase.memory();
      secondRepository = SubmissionsRepository(
        MockPlanetApi(),
        secondDevice.submissionDao,
        secondDevice.submitPhotosDao,
        secondDevice.surveyDao,
        secondDevice.examDao,
      );
    });
    tearDown(() => secondDevice.close());

    Future<ExamRow> seedExam() async {
      await database.examDao.upsertAll(
        [
          ExamsCompanion.insert(
            id: 'exam-1',
            rev: const Value('4-eee'),
            name: const Value('Week 1 quiz'),
            description: const Value('The first week'),
            courseId: const Value('course-1'),
            totalMarks: const Value(2),
            passingPercentage: const Value('50'),
            createdBy: const Value('org.couchdb.user:tutor'),
            sourcePlanet: const Value('lea'),
            createdDate: const Value(100),
            updatedDate: const Value(200),
          ),
        ],
        {
          'exam-1': [
            ExamQuestionsCompanion.insert(
              id: 'exam-1-q1',
              examId: 'exam-1',
              header: const Value('Capital of France?'),
              body: const Value('Pick one'),
              type: const Value('select'),
              marks: const Value('1'),
              choices: const Value([
                ExamChoice(id: 'c1', text: 'Paris'),
                ExamChoice(id: 'c2', text: 'Lyon'),
              ]),
              correctChoices: const Value(['c1']),
              position: 0,
            ),
          ],
        },
      );
      return (await database.examDao.getById('exam-1'))!;
    }

    test('an exam attempt keeps its questions', () async {
      final exam = await seedExam();
      final localId = await repository.startExamSession(
        exam: exam,
        questions: await database.examDao.questionsFor('exam-1'),
        userId: 'org.couchdb.user:ada',
      );
      final payload = await repository.serialize(
        (await repository.getById(localId))!,
      );

      await secondRepository.upsertDocuments([
        asStoredByCouch(payload, id: 'couch-exam-1'),
      ]);

      final questions = await secondRepository
          .watchQuestions('couch-exam-1')
          .first;
      expect(
        questions.map((question) => question.header),
        ['Capital of France?'],
        reason:
            'the upload carried the stored blob, which has no `questions` — so '
            'the second device drew an answer sheet with no questions',
      );
      expect(questions.single.choices, ['Paris', 'Lyon']);
    });

    test('the answers still line up with the questions', () async {
      final exam = await seedExam();
      final questionRows = await database.examDao.questionsFor('exam-1');
      final localId = await repository.startExamSession(
        exam: exam,
        questions: questionRows,
        userId: 'org.couchdb.user:ada',
      );
      await repository.saveExamAnswer(
        submissionId: localId,
        question: questionRows.single,
        answer: const ExamDraftAnswer(choiceIds: ['c1']),
        isFinal: true,
        isExplicitSubmission: true,
      );
      final payload = await repository.serialize(
        (await repository.getById(localId))!,
      );

      await secondRepository.upsertDocuments([
        asStoredByCouch(payload, id: 'couch-exam-1'),
      ]);

      final questions = await secondRepository
          .watchQuestions('couch-exam-1')
          .first;
      final answers = await secondRepository.answersFor('couch-exam-1');
      expect(
        answers.single.questionId,
        'exam-1-q1',
        reason: 'the answer records the id of the question it was given',
      );
      expect(
        questions.single.id,
        'couch-exam-1:exam-1-q1',
        reason:
            'Kotlin `serializeQuestions` emits no question id, so a faithful '
            'upload leaves the pull falling back to a positional '
            '`<submission>-q<index>` key the answer cannot be joined to',
      );
    });

    test('a survey submission keeps its questions', () async {
      await database.surveyDao.upsertAll(
        [SurveysCompanion.insert(id: 'survey-1', name: const Value('Water'))],
        {
          'survey-1': [
            SurveyQuestionsCompanion.insert(
              id: 'survey-1:q1',
              surveyId: 'survey-1',
              questionId: const Value('q1'),
              header: const Value('How is the water?'),
              type: const Value('select'),
              choices: const Value([
                ExamChoice(id: 'c1', text: 'Clean'),
                ExamChoice(id: 'c2', text: 'Dirty'),
              ]),
              position: 0,
            ),
          ],
        },
      );
      final localId = await repository.createSurveyDraft(
        survey: (await database.surveyDao.getById('survey-1'))!,
        questions: await database.surveyDao.questionsFor('survey-1'),
        userId: 'org.couchdb.user:ada',
      );
      final payload = await repository.serialize(
        (await repository.getById(localId))!,
      );

      await secondRepository.upsertDocuments([
        asStoredByCouch(payload, id: 'couch-survey-1'),
      ]);

      final questions = await secondRepository
          .watchQuestions('couch-survey-1')
          .first;
      expect(questions.map((question) => question.header), [
        'How is the water?',
      ]);
      expect(questions.single.id, 'couch-survey-1:q1');
    });
  });

  /// The `parent` object itself, field for field against
  /// `StepExam.serializeExam` (`StepExam.kt:70-94`). The round-trip tests
  /// above only prove the questions arrive; this one is what stops the rest of
  /// the document being quietly dropped again.
  group('the parent object the upload carries', () {
    test('is `serializeExam`, minus the type the port cannot know', () {
      final document = SubmissionsRepository.examParentDocument(
        ExamRow(
          id: 'exam-1',
          rev: '4-eee',
          name: 'Week 1 quiz',
          description: 'The first week',
          courseId: 'course-1',
          stepId: 'step-1',
          createdDate: 100,
          updatedDate: 200,
          adoptionDate: 300,
          createdBy: 'org.couchdb.user:tutor',
          totalMarks: 2,
          passingPercentage: '50',
          sourcePlanet: 'lea',
          isFromNation: false,
          teamId: 'team-9',
          teamShareAllowed: false,
          sourceSurveyId: 'survey-0',
          noOfQuestions: 1,
        ),
        const [],
      );

      expect(document, {
        '_id': 'exam-1',
        '_rev': '4-eee',
        'name': 'Week 1 quiz',
        'description': 'The first week',
        'passingPercentage': '50',
        'updatedDate': 200,
        'createdDate': 100,
        'adoptionDate': 300,
        'sourcePlanet': 'lea',
        'totalMarks': 2,
        'createdBy': 'org.couchdb.user:tutor',
        'sourceSurveyId': 'survey-0',
        'teamId': 'team-9',
        'questions': <Object?>[],
      });
    });

    test('omits the two fields Kotlin guards with an `if`', () {
      final document = SubmissionsRepository.examParentDocument(
        ExamRow(
          id: 'exam-1',
          createdDate: 0,
          updatedDate: 0,
          adoptionDate: 0,
          totalMarks: 0,
          isFromNation: false,
          teamShareAllowed: false,
          noOfQuestions: 0,
        ),
        const [],
      );

      expect(document.containsKey('_rev'), isFalse);
      expect(document.containsKey('sourceSurveyId'), isFalse);
      expect(document.containsKey('teamId'), isFalse);
    });

    test('carries every field `serializeQuestions` writes', () {
      final document = SubmissionsRepository.examParentDocument(
        ExamRow(
          id: 'exam-1',
          createdDate: 0,
          updatedDate: 0,
          adoptionDate: 0,
          totalMarks: 0,
          isFromNation: false,
          teamShareAllowed: false,
          noOfQuestions: 1,
        ),
        const [
          ExamQuestionRow(
            id: 'exam-1-q1',
            examId: 'exam-1',
            header: 'Capital of France?',
            body: 'Pick one',
            type: 'select',
            marks: '1',
            correctChoices: ['c1'],
            choices: [
              ExamChoice(id: 'c1', text: 'Paris'),
              ExamChoice(id: 'c2', text: 'Lyon'),
            ],
            hasOtherOption: false,
            scaleMax: 9,
            position: 0,
          ),
        ],
      );

      expect((document['questions'] as List).single, {
        'id': 'exam-1-q1',
        // Neither is Kotlin's: `serializeQuestions` emits no id, and it puts
        // the label under `header`, a key no real document has. See
        // `examParentDocument`.
        'title': 'Capital of France?',
        'header': 'Capital of France?',
        'body': 'Pick one',
        'type': 'select',
        'marks': '1',
        'choices': [
          {'id': 'c1', 'text': 'Paris'},
          {'id': 'c2', 'text': 'Lyon'},
        ],
        'correctChoice': ['c1'],
        'hasOtherOption': false,
      });
    });

    /// The exams-database walk routes on `type == 'surveys'`, but
    /// `SurveyMapper.fromCourseDoc`'s second pass files a course step's survey
    /// here with no type filter — so the value is a guess for exactly the rows
    /// the exam branch refuses to guess for, and neither side emits it.
    test('a survey does not guess back the type the split discarded', () {
      final document = SubmissionsRepository.surveyParentDocument(
        SurveyRow(
          id: 'survey-1',
          name: 'Water',
          createdDate: 0,
          updatedDate: 0,
          adoptionDate: 0,
          totalMarks: 0,
          isFromNation: false,
          teamShareAllowed: false,
        ),
        const [],
      );

      expect(document.containsKey('type'), isFalse);
      expect(document['_id'], 'survey-1');
    });

    /// The blob is the `else if` branch (`SubmissionsRepositoryImpl.kt:842`),
    /// not the default: a device that no longer has the exam still uploads
    /// what it stored.
    test('falls back to the stored blob when the exam is gone', () async {
      final id = await repository.createDraft(
        userId: 'org.couchdb.user:ada',
        type: 'survey',
        title: 'Water survey',
        answers: const [],
      );

      final payload = await repository.serialize(
        (await repository.getById(id))!,
      );

      expect(payload['parent'], 'Water survey');
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
        database.examDao,
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
