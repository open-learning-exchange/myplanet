import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:drift/drift.dart' show Value;
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/converters.dart';
import 'package:myplanet/repository/submissions_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late SubmissionsRepository repository;
  late MockPlanetApi api;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    repository = SubmissionsRepository(
      api,
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
    );
  });

  tearDown(() => database.close());

  // Server-shaped: the owner is `user._id` and `uploaded` is derived from
  // `_rev`, because that is what `upsertRoomSubmissionsFromSync` reads
  // (`SubmissionsRepositoryImpl.kt:676-700`). Written against a top-level
  // `userId`/`uploaded` pair, this test passed while no real document could
  // reach the list.
  test('maps documents and watches newest submissions first', () async {
    await repository.upsertDocuments([
      {
        '_id': 'older',
        'user': {'_id': 'user-1'},
        'type': 'exam',
        'lastUpdateTime': 10,
        'status': 'complete',
      },
      {
        '_id': 'newer',
        '_rev': '2-aaa',
        'user': {'_id': 'user-1'},
        'lastUpdateTime': '20',
      },
      {
        '_id': 'other-user',
        'user': {'_id': 'user-2'},
        'lastUpdateTime': 30,
      },
    ]);

    final rows = await repository.watchForUser('user-1').first;
    expect(rows.map((row) => row.id), ['newer', 'older']);
    expect(rows.first.uploaded, isTrue);
    expect(rows.last.uploaded, isFalse);
    expect(rows.last.type, 'exam');
  });

  test(
    'bulk survey send creates one reusable pending survey per user',
    () async {
      var nextId = 0;
      await repository.createBulkSurveySubmissions(
        'survey-1',
        ['user-1', 'user-2'],
        now: DateTime.fromMillisecondsSinceEpoch(1234),
        createId: () => 'local-${nextId++}',
      );
      await repository.createBulkSurveySubmissions(
        'survey-1',
        ['user-1'],
        now: DateTime.fromMillisecondsSinceEpoch(9999),
        createId: () => 'local-${nextId++}',
      );

      final userOne = await repository.watchForUser('user-1').first;
      final userTwo = await repository.watchForUser('user-2').first;

      expect(userOne, hasLength(1));
      expect(userOne.single.id, 'local-0');
      expect(userOne.single.parentId, 'survey-1');
      expect(userOne.single.status, 'pending');
      expect(userOne.single.type, 'survey');
      expect(userOne.single.isUpdated, isFalse);
      expect(userOne.single.startTime, 1234);
      expect(userTwo.single.id, 'local-1');
    },
  );

  test('hydrates scalar and choice answers into the related cache', () async {
    await repository.upsertDocuments([
      {
        '_id': 'answered',
        'userId': 'user-1',
        'parentId': 'exam-1@course-1',
        'answers': [
          {'questionId': 'q-1', 'value': 'written response', 'passed': true},
          {
            'questionId': 'q-2',
            'value': ['choice-a', 'choice-b'],
            'mistakes': 1,
          },
        ],
      },
    ]);

    final answers = await repository.watchAnswers('answered').first;
    expect(answers, hasLength(2));
    expect(answers.first.value, 'written response');
    expect(answers.first.isPassed, isTrue);
    expect(answers.last.valueChoices, ['choice-a', 'choice-b']);
    expect(answers.last.examId, 'exam-1');
  });

  test(
    'hydrates embedded question text, choices, and correct answers',
    () async {
      await repository.upsertDocuments([
        {
          '_id': 'questions',
          'parent': {
            'questions': [
              {
                'id': 'q-1',
                'title': 'Capital city',
                'body': 'What is the capital?',
                'type': 'selectOne',
                'choices': [
                  {'id': 'a', 'res': 'Paris'},
                  {'id': 'b', 'res': 'Rome'},
                ],
                'correctChoice': ['paris'],
                'marks': '10',
              },
            ],
          },
        },
      ]);

      final questions = await repository.watchQuestions('questions').first;
      expect(questions.single.header, 'Capital city');
      expect(questions.single.choices, ['Paris', 'Rome']);
      expect(questions.single.correctChoices, ['paris']);
      expect(questions.single.marks, '10');
    },
  );

  test(
    're-sync replaces removed answers instead of leaving stale rows',
    () async {
      await repository.upsertDocuments([
        {
          '_id': 'edited',
          'answers': [
            {'questionId': 'q-1', 'value': 'old'},
          ],
        },
      ]);
      await repository.upsertDocuments([
        {'_id': 'edited', 'answers': <dynamic>[]},
      ]);

      expect(await repository.watchAnswers('edited').first, isEmpty);
    },
  );

  // `if (id.isBlank()) return@forEach` (`:668`) — one unusable document must
  // not abort the page around it. The port used to throw, which would have
  // dropped every later document in the batch.
  test('skips a document with no id and keeps the rest of the page', () async {
    await repository.upsertDocuments([
      {
        'user': {'_id': 'user-1'},
      },
      {
        '_id': 'good',
        'user': {'_id': 'user-1'},
      },
    ]);

    expect(
      (await repository.watchForUser('user-1').first).map((row) => row.id),
      ['good'],
    );
  });

  // The same rule for a document CouchDB is holding an attachment for: the
  // photo documents `UploadConfigs.SubmitPhotos` posts into this database come
  // back through this walk, and `filterNot { it.has("_attachments") }` (`:666`)
  // keeps them out of the submissions list.
  test('skips a document carrying attachments', () async {
    await repository.upsertDocuments([
      {
        '_id': 'photo-doc',
        'user': {'_id': 'user-1'},
        '_attachments': {'img': {}},
      },
    ]);

    expect(await repository.watchForUser('user-1').first, isEmpty);
  });

  test(
    'creates a durable local draft with answers and serializes it',
    () async {
      final id = await repository.createDraft(
        userId: 'user-1',
        type: 'exam',
        title: 'Offline exam',
        answers: const [SubmissionDraftAnswer(questionId: 'q-1', value: '42')],
        now: DateTime.fromMillisecondsSinceEpoch(1000),
      );

      final pending = await repository.pendingUploads('user-1');
      expect(pending.single.id, id);
      expect(pending.single.isUpdated, isTrue);
      final payload = await repository.serialize(pending.single);
      expect(payload['parent'], 'Offline exam');
      expect((payload['answers'] as List).single, containsPair('value', '42'));
    },
  );

  test('caches a synced question choice by its display label', () async {
    // `ExamAnswerUtils.choiceDisplayValue` is `text` first and `res` only as
    // a fallback. Reading only `res` left the label empty for an ordinary
    // choice object, so the detail screen's "Choices:" row was commas.
    await repository.upsertDocuments([
      {
        '_id': 'report-1',
        'userId': 'user-1',
        'status': 'complete',
        'parent': {
          'name': 'Needs survey',
          'questions': [
            {
              'id': 'q-1',
              'title': 'Which service?',
              'choices': [
                {'id': 'water', 'text': 'Water'},
                {'id': 'power', 'res': 'Power'},
                'Other',
              ],
            },
          ],
        },
      },
    ]);

    final questions = await database.select(database.submissionQuestions).get();
    expect(questions.single.choices, ['Water', 'Power', 'Other']);
  });

  test('a survey select answer carries the choice text as its value', () async {
    // `saveExamAnswer`'s `select` branch sets `value = ansForCheck`, the
    // choice's display text — and `Answer.createObject` sends `value`
    // whenever it is non-empty. The survey screen has no text field on a
    // choice question, so it hands the repository an empty string; deriving
    // the value from the question is where Kotlin does it.
    await database.surveyDao.upsertAll(
      [SurveysCompanion.insert(id: 'survey-1', name: const Value('Services'))],
      {
        'survey-1': [
          SurveyQuestionsCompanion.insert(
            id: 'survey-1:q-1',
            surveyId: 'survey-1',
            questionId: const Value('q-1'),
            type: const Value('select'),
            choices: const Value([
              ExamChoice(id: 'water', text: 'Water'),
              ExamChoice(id: 'power', text: 'Power'),
            ]),
            position: 0,
          ),
        ],
      },
    );
    final survey = (await database.surveyDao.getById('survey-1'))!;
    final questions = await database.surveyDao.questionsFor('survey-1');

    final id = await repository.createSurveyDraft(
      survey: survey,
      questions: questions,
      userId: 'user-1',
      answers: const {
        'survey-1:q-1': SubmissionDraftAnswer(
          questionId: 'q-1',
          value: '',
          choices: ['{"id":"water","text":"Water"}'],
        ),
      },
      now: DateTime.fromMillisecondsSinceEpoch(1000),
    );

    final answers = await database.submissionDao.answersFor(id);
    expect(answers.single.value, 'Water');
    expect(answers.single.valueChoices, ['{"id":"water","text":"Water"}']);

    final payload = await repository.serialize((await repository.getById(id))!);
    expect((payload['answers'] as List).single, containsPair('value', 'Water'));
  });

  test('a survey selectMultiple answer uploads objects', () async {
    await database.surveyDao.upsertAll(
      [SurveysCompanion.insert(id: 'survey-2', name: const Value('Services'))],
      {
        'survey-2': [
          SurveyQuestionsCompanion.insert(
            id: 'survey-2:q-1',
            surveyId: 'survey-2',
            questionId: const Value('q-1'),
            type: const Value('selectMultiple'),
            choices: const Value([
              ExamChoice(id: 'water', text: 'Water'),
              ExamChoice(id: 'power', text: 'Power'),
            ]),
            position: 0,
          ),
        ],
      },
    );
    final survey = (await database.surveyDao.getById('survey-2'))!;
    final questions = await database.surveyDao.questionsFor('survey-2');

    final id = await repository.createSurveyDraft(
      survey: survey,
      questions: questions,
      userId: 'user-1',
      answers: const {
        'survey-2:q-1': SubmissionDraftAnswer(
          questionId: 'q-1',
          value: '',
          choices: [
            '{"id":"water","text":"Water"}',
            '{"id":"power","text":"Power"}',
          ],
        ),
      },
      now: DateTime.fromMillisecondsSinceEpoch(1000),
    );

    // Kotlin's `selectMultiple` branch sets `value = ""`, which is what sends
    // `valueChoicesArray` instead of a string.
    final answers = await database.submissionDao.answersFor(id);
    expect(answers.single.value, '');

    final payload = await repository.serialize((await repository.getById(id))!);
    expect((payload['answers'] as List).single['value'], [
      {'id': 'water', 'text': 'Water'},
      {'id': 'power', 'text': 'Power'},
    ]);
  });

  test('a choice pick survives a question with no type', () async {
    // `take_survey_screen` renders choices whenever the question offers any,
    // reading only `selectmultiple` off the type to pick checkboxes over
    // radios — so a document that names no type still gets a radio group and
    // a real answer. Shaping that answer through the plain-text branch (which
    // is what `saveExamAnswer` does for an unrecognised type) would discard
    // the pick silently. Kotlin never faces the case: `startExam` renders
    // *nothing* for a type-less question, so it is unanswerable there.
    await database.surveyDao.upsertAll(
      [SurveysCompanion.insert(id: 'survey-3', name: const Value('Services'))],
      {
        'survey-3': [
          SurveyQuestionsCompanion.insert(
            id: 'survey-3:q-1',
            surveyId: 'survey-3',
            questionId: const Value('q-1'),
            choices: const Value([ExamChoice(id: 'water', text: 'Water')]),
            position: 0,
          ),
        ],
      },
    );
    final survey = (await database.surveyDao.getById('survey-3'))!;

    final id = await repository.createSurveyDraft(
      survey: survey,
      questions: await database.surveyDao.questionsFor('survey-3'),
      userId: 'user-1',
      answers: const {
        'survey-3:q-1': SubmissionDraftAnswer(
          questionId: 'q-1',
          value: '',
          choices: ['{"id":"water","text":"Water"}'],
        ),
      },
      now: DateTime.fromMillisecondsSinceEpoch(1000),
    );

    final answers = await database.submissionDao.answersFor(id);
    expect(answers.single.valueChoices, ['{"id":"water","text":"Water"}']);
    expect(answers.single.value, 'Water');
  });

  test('a synced choice answer is cached as JSON, not as a Dart map', () async {
    // Kotlin stores `valueElement.asJsonArray.map { it.toString() }`, and
    // Gson's `JsonElement.toString()` emits JSON. Dart's `Map.toString()`
    // emits `{id: paris, text: Paris}` — not JSON, so nothing downstream can
    // read it back: the re-upload sent that literal as a *string* where
    // Planet expects the object, and the detail screen and the PDF export
    // printed it verbatim.
    await repository.upsertDocuments([
      {
        '_id': 'synced-1',
        'userId': 'user-1',
        'status': 'complete',
        'answers': [
          {
            'questionId': 'q-1',
            'value': [
              {'id': 'paris', 'text': 'Paris'},
            ],
          },
        ],
      },
    ]);

    final answers = await repository.answersFor('synced-1');
    expect(answers.single.valueChoices, ['{"id":"paris","text":"Paris"}']);
    // And it round-trips: the entry the sync stored is one the uploader can
    // send back as an object.
    final payload = await repository.serialize(
      (await repository.getById('synced-1'))!,
    );
    expect((payload['answers'] as List).single['value'], [
      {'id': 'paris', 'text': 'Paris'},
    ]);
  });

  test('serializes an answer choice as the object it was stored as', () async {
    // Port of `Answer.valueChoicesArray`, which sends each entry back through
    // `gson.fromJson(choice, JsonObject::class.java)`: Planet expects
    // `answers[].value` to be `{id, text}` objects for a choice question. The
    // port sent the stored JSON *strings*, so a choice answer reached the
    // server as a quoted blob rather than an object.
    final id = await repository.createDraft(
      userId: 'user-1',
      type: 'survey',
      title: 'Offline survey',
      answers: const [
        SubmissionDraftAnswer(
          questionId: 'q-1',
          choices: ['{"id":"water","text":"Water"}'],
        ),
      ],
      now: DateTime.fromMillisecondsSinceEpoch(1000),
    );

    final payload = await repository.serialize((await repository.getById(id))!);
    final answer = (payload['answers'] as List).single as Map;
    expect(answer['value'], [
      {'id': 'water', 'text': 'Water'},
    ]);
  });

  test('serializes a bare entry as an object, not as a bare id', () async {
    // The exam path used to store plain choice ids and `_answerChoices`
    // carried a "send a non-JSON entry untouched" branch to let them past.
    // Both write paths go through `AnswerShape` now, so that tolerance is
    // gone: an entry an earlier build left behind resolves to
    // `{id: raw, text: raw}`, which is what Kotlin's own unresolvable-id
    // fallback produces (`getChoiceTextById` returns `map[id] ?: id`).
    // It is not left to throw the way `gson.fromJson` would, because
    // `SubmissionsUploader.queuePending` serializes every pending row in one
    // unguarded loop and one such row would block the whole queue.
    final id = await repository.createDraft(
      userId: 'user-1',
      type: 'exam',
      title: 'Offline exam',
      answers: const [
        SubmissionDraftAnswer(questionId: 'q-1', choices: ['choice-a']),
      ],
      now: DateTime.fromMillisecondsSinceEpoch(1000),
    );

    final payload = await repository.serialize((await repository.getById(id))!);
    final answer = (payload['answers'] as List).single as Map;
    expect(answer['value'], [
      {'id': 'choice-a', 'text': 'choice-a'},
    ]);
  });

  test(
    'empty server sync does not delete an un-uploaded local draft',
    () async {
      await repository.createDraft(
        userId: 'user-1',
        type: 'exam',
        title: 'Keep me',
        answers: const [],
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({'total_rows': 0}),
      );

      await repository.sync(config: config);

      expect(await repository.pendingUploads('user-1'), hasLength(1));
    },
  );

  test('sync pulls pages, reports progress, and removes stale rows', () async {
    when(
      () => api.getJsonObject(
        '$dbUrl/submissions/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'total_rows': 2}),
    );
    when(
      () => api.getJsonObject(
        '$dbUrl/submissions/_all_docs?include_docs=true&limit=100&skip=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'doc': {'_id': 'one', 'userId': 'user-1'},
          },
          {
            'doc': {'_id': 'two', 'userId': 'user-1'},
          },
        ],
      }),
    );
    final progress = <int>[];

    final result = await repository.sync(
      config: config,
      onProgress: (value) => progress.add(value.completed),
    );

    expect((result as SyncComplete).savedCount, 2);
    expect(progress, [2]);
    expect(await repository.localCount(), 2);
  });

  test('sync retains completed pages after a later network failure', () async {
    when(
      () => api.getJsonObject(
        '$dbUrl/submissions/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'total_rows': 101}),
    );
    when(
      () => api.getJsonObject(
        '$dbUrl/submissions/_all_docs?include_docs=true&limit=100&skip=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': List.generate(
          100,
          (index) => {
            'doc': {'_id': 'submission-$index', 'userId': 'user-1'},
          },
        ),
      }),
    );
    when(
      () => api.getJsonObject(
        '$dbUrl/submissions/_all_docs?include_docs=true&limit=100&skip=100',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkException<Map<String, dynamic>>(Exception('connection lost')),
    );

    expect(await repository.sync(config: config), isA<SyncFailed>());
    expect(await repository.localCount(), 100);
  });

  test('parent and user upload as objects, not as JSON text', () async {
    // Kotlin sends both as nested documents (`object.add("user",
    // JsonParser.parseString(submission.user))`). Sending the stored string
    // instead gives Planet a `user` it cannot read `name` or `_id` off, so the
    // submission is attributable to neither its respondent nor its survey.
    await repository.createSurveyAdoptionSubmission(
      id: 'adoption-1',
      surveyId: 'survey-1',
      userId: 'user-1',
      parentJson: '{"_id":"survey-1","name":"Water quality"}',
      userJson: '{"doc":{"_id":"user-1","userId":"user-1"}}',
      source: 'planet-1',
      parentCode: 'parent-1',
    );

    final payload = await repository.serialize(
      (await repository.getById('adoption-1'))!,
    );

    expect(payload['parent'], isA<Map<String, dynamic>>());
    expect((payload['parent'] as Map)['_id'], 'survey-1');
    expect(payload['user'], isA<Map<String, dynamic>>());
    expect(((payload['user'] as Map)['doc'] as Map)['userId'], 'user-1');
  });

  test('a parent that is a plain title survives serialization', () async {
    // `createDraft` stores the title there rather than a JSON document, and
    // decoding must not turn that into a dropped or mangled field.
    final id = await repository.createDraft(
      userId: 'user-1',
      type: 'feedback',
      title: 'Broken projector',
      answers: const [],
    );

    final payload = await repository.serialize((await repository.getById(id))!);

    expect(payload['parent'], 'Broken projector');
  });

  test(
    'addSubmissionPhoto persists a row that the uploader can find',
    () async {
      final id = await repository.addSubmissionPhoto(
        submissionId: 'sub-1',
        examId: 'exam-1',
        courseId: 'course-1',
        memberId: 'user-1',
        photoLocation: '/tmp/capture.jpg',
        now: DateTime.fromMillisecondsSinceEpoch(1700000000000),
      );

      final pending = await repository.unuploadedPhotos();
      expect(pending, hasLength(1));
      expect(pending.first.id, id);
      final doc = pending.first.document;
      expect(doc['submissionId'], 'sub-1');
      expect(doc['type'], 'photo');
      expect(doc['courseId'], 'course-1');
      expect(doc['examId'], 'exam-1');
      expect(doc['memberId'], 'user-1');
      expect(doc['photoLocation'], '/tmp/capture.jpg');
    },
  );

  test(
    'markPhotoUploaded clears the pending set and records the rev',
    () async {
      final id = await repository.addSubmissionPhoto(
        submissionId: 'sub-1',
        examId: 'exam-1',
        courseId: 'course-1',
        memberId: 'user-1',
        now: DateTime.fromMillisecondsSinceEpoch(1700000000000),
      );

      await repository.markPhotoUploaded(id, 'server-id', '2-b');

      expect(await repository.unuploadedPhotos(), isEmpty);
      final survivor = await repository.photoById(id);
      expect(survivor?.uploaded, isTrue);
      expect(survivor?.couchId, 'server-id');
      expect(survivor?.rev, '2-b');
    },
  );

  group('hasUnfinishedSurveys', () {
    test('returns false when the course has no attached surveys', () async {
      final result = await repository.hasUnfinishedSurveys(
        'course-1',
        'user-1',
      );
      expect(result, isFalse);
    });

    test('returns true when a course survey has no submission', () async {
      await database.surveyDao.upsertAll([
        SurveysCompanion.insert(
          id: 'survey-a',
          courseId: const Value('course-1'),
          name: const Value('Onboarding survey'),
        ),
      ], {});
      final result = await repository.hasUnfinishedSurveys(
        'course-1',
        'user-1',
      );
      expect(result, isTrue);
    });

    test('returns false when every course survey has a submission', () async {
      await database.surveyDao.upsertAll([
        SurveysCompanion.insert(
          id: 'survey-a',
          courseId: const Value('course-1'),
          name: const Value('Onboarding survey'),
        ),
      ], {});
      await repository.upsertDocuments([
        {
          '_id': 'sub-1',
          'user': {'_id': 'user-1'},
          'type': 'survey',
          'parentId': 'survey-a@course-1',
          'lastUpdateTime': 100,
          'status': 'complete',
        },
      ]);
      final result = await repository.hasUnfinishedSurveys(
        'course-1',
        'user-1',
      );
      expect(result, isFalse);
    });

    test('returns false for a blank course id or null user', () async {
      expect(await repository.hasUnfinishedSurveys('', 'user-1'), isFalse);
      expect(await repository.hasUnfinishedSurveys('course-1', null), isFalse);
    });
  });
}
