import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:drift/drift.dart' show Value;
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
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

  test('maps documents and watches newest submissions first', () async {
    await repository.upsertDocuments([
      {
        '_id': 'older',
        'userId': 'user-1',
        'type': 'exam',
        'lastUpdateTime': 10,
        'status': 'complete',
      },
      {
        '_id': 'newer',
        'userId': 'user-1',
        'lastUpdateTime': '20',
        'uploaded': true,
      },
      {'_id': 'other-user', 'userId': 'user-2', 'lastUpdateTime': 30},
    ]);

    final rows = await repository.watchForUser('user-1').first;
    expect(rows.map((row) => row.id), ['newer', 'older']);
    expect(rows.first.uploaded, isTrue);
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

  test('rejects documents without an id', () {
    expect(
      () => repository.upsertDocuments([
        {'userId': 'user-1'},
      ]),
      throwsFormatException,
    );
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

  test('serializes a bare choice id untouched', () async {
    // The exam path stores plain choice ids in `valueChoices`; Kotlin has no
    // such entries and `gson.fromJson` would throw on one, so they are passed
    // through rather than dropped.
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
    expect(answer['value'], ['choice-a']);
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
          'userId': 'user-1',
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
