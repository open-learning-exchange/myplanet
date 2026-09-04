import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/server_url_mapper.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/converters.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late SurveysRepository repository;
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    repository = SurveysRepository(
      api,
      database.surveyDao,
      database.examDao,
      SubmissionsRepository(
        api,
        database.submissionDao,
        database.submitPhotosDao,
        database.surveyDao,
        database.examDao,
      ),
    );
  });
  tearDown(() => database.close());

  test('sync routes surveys to the survey tables, in question order', () async {
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments.single as String;
      if (url.endsWith('limit=0')) {
        return NetworkSuccess<Map<String, dynamic>>({'total_rows': 2});
      }
      return NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'doc': {
              '_id': 'survey-1',
              'type': 'surveys',
              'name': 'Community needs',
              'questions': [
                {
                  '_id': 'q1',
                  'body': 'What do you need?',
                  'type': 'input',
                  'required': true,
                },
                {
                  '_id': 'q2',
                  'body': 'Choose topics',
                  'type': 'selectMultiple',
                  'choices': ['Water', 'Health'],
                },
              ],
            },
          },
          {
            'doc': {'_id': 'exam-1', 'type': 'exam', 'name': 'Not a survey'},
          },
        ],
      });
    });

    final result = await repository.sync(config: config);

    expect(result, isA<SyncComplete>());
    // One survey plus one exam: the `exams` database holds both, and the exam
    // now lands in its own tables instead of being discarded.
    expect((result as SyncComplete).savedCount, 2);
    expect((await repository.getById('survey-1'))?.name, 'Community needs');
    // The exam document must not be mistaken for a survey.
    expect(await repository.getById('exam-1'), isNull);
    expect((await database.examDao.getById('exam-1'))?.name, 'Not a survey');

    final questions = await repository.questionsFor('survey-1');
    expect(questions.map((row) => row.body), [
      'What do you need?',
      'Choose topics',
    ]);
    // Bare-string choices keep the text as their own id, which is how
    // `ExamTakingFragment.addRadioButton` treats them; a `{id, text}` object
    // keeps both halves. Either way the column is no longer a flattened
    // `toString()` of the document's choice.
    expect(questions.last.choices, const [
      ExamChoice(id: 'Water', text: 'Water'),
      ExamChoice(id: 'Health', text: 'Health'),
    ]);
  });

  test('completed offline response becomes an uploadable submission', () async {
    await database.surveyDao.upsertAll(
      [SurveysCompanion.insert(id: 'survey-1', name: const Value('Needs'))],
      {
        'survey-1': [
          SurveyQuestionsCompanion.insert(
            id: 'survey-1:q1',
            surveyId: 'survey-1',
            body: const Value('Your answer'),
            position: 0,
          ),
        ],
      },
    );

    final id = await repository.submitResponse('survey-1', 'user-1', {
      'survey-1:q1': const SubmissionDraftAnswer(
        questionId: 'survey-1:q1',
        value: 'Clean water',
      ),
    });

    final submission = await database.submissionDao.getById(id!);
    expect(submission?.parentId, 'survey-1');
    expect(submission?.status, 'complete');
    expect(submission?.isUpdated, isTrue);
    expect(
      (await database.submissionDao.answersFor(id)).single.value,
      'Clean water',
    );
  });

  test(
    'resuming a pending survey updates answers on the existing submission',
    () async {
      await database.surveyDao.upsertAll(
        [SurveysCompanion.insert(id: 'survey-1', name: const Value('Needs'))],
        {
          'survey-1': [
            SurveyQuestionsCompanion.insert(
              id: 'survey-1:q1',
              surveyId: 'survey-1',
              questionId: const Value('q1'),
              body: const Value('Your answer'),
              position: 0,
            ),
          ],
        },
      );

      // Start a pending submission (as the dashboard's bulk-send would).
      final originalId = await repository.submitResponse('survey-1', 'user-1', {
        'survey-1:q1': const SubmissionDraftAnswer(
          questionId: 'survey-1:q1',
          value: 'first attempt',
        ),
      });

      // Resume it with a new answer — the id must stay the same.
      final resumedId = await repository.updateSurveyResponse(
        originalId!,
        answers: {
          'survey-1:q1': const SubmissionDraftAnswer(
            questionId: 'survey-1:q1',
            value: 'second attempt',
          ),
        },
      );
      expect(resumedId, originalId);

      final submission = await database.submissionDao.getById(originalId);
      expect(submission?.status, 'complete');
      expect(submission?.isUpdated, isTrue);
      expect(submission?.uploaded, isFalse);
      expect(
        (await database.submissionDao.answersFor(originalId)).single.value,
        'second attempt',
      );
    },
  );

  test(
    'adopts a shareable survey into a team and creates upload row',
    () async {
      await database.surveyDao.upsertAll(
        [
          SurveysCompanion.insert(
            id: 'survey-1',
            name: const Value('Needs'),
            teamShareAllowed: const Value(true),
            sourcePlanet: const Value('nation'),
          ),
        ],
        {
          'survey-1': [
            SurveyQuestionsCompanion.insert(
              id: 'survey-1:q1',
              surveyId: 'survey-1',
              questionId: const Value('q1'),
              body: const Value('Your answer'),
              position: 0,
            ),
          ],
        },
      );

      var nextId = 0;
      await repository.adoptSurvey(
        surveyId: 'survey-1',
        userId: 'user-1',
        teamId: 'team-1',
        teamName: 'Water Team',
        isTeam: true,
        planetCode: 'planet-a',
        parentCode: 'parent-a',
        now: DateTime.fromMillisecondsSinceEpoch(5000),
        createId: () => 'created-${nextId++}',
      );
      await repository.adoptSurvey(
        surveyId: 'survey-1',
        userId: 'user-1',
        teamId: 'team-1',
        teamName: 'Water Team',
        isTeam: true,
        now: DateTime.fromMillisecondsSinceEpoch(9000),
        createId: () => 'created-${nextId++}',
      );

      final adopted = await database.surveyDao.getById('created-0');
      expect(adopted?.name, 'Needs - Water Team');
      expect(adopted?.teamId, 'team-1');
      expect(adopted?.sourceSurveyId, 'survey-1');
      expect(adopted?.teamShareAllowed, isFalse);
      expect(adopted?.adoptionDate, 5000);
      expect(await database.surveyDao.questionsFor('created-0'), hasLength(1));

      final submissions = await database.submissionDao.byTeam('team-1');
      expect(submissions, hasLength(1));
      expect(submissions.single.id, 'created-1');
      expect(submissions.single.parentId, 'survey-1');
      expect(submissions.single.status, '');
      expect(submissions.single.isUpdated, isTrue);
      expect(submissions.single.source, 'planet-a');
      expect(submissions.single.parentCode, 'parent-a');

      final adoptable = await repository.adoptableTeamSurveys('team-1');
      expect(adoptable, isEmpty);
      final owned = await repository.teamOwnedSurveys('team-1');
      expect(owned.map((row) => row.id), contains('created-0'));
    },
  );

  group('public survey deep link', () {
    const publicDoc = {
      '_id': 'survey-1',
      'type': 'surveys',
      'name': 'Public survey',
      'questions': [
        {
          'id': 'q1',
          'body': 'Choose topics',
          'type': 'selectMultiple',
          'choices': [
            {'id': 'water', 'text': 'Water'},
            {'id': 'health', 'text': 'Health'},
          ],
        },
        {
          'id': 'q2',
          'body': 'Gender',
          'type': 'select',
          'choices': [
            {'id': 'male', 'text': 'Male'},
            {'id': 'female', 'text': 'Female'},
          ],
        },
        {'id': 'q3', 'body': 'Notes', 'type': 'textarea'},
      ],
    };

    test('fetches and unwraps a survey wrapped under the survey key', () async {
      when(() => api.getJsonObject(any())).thenAnswer(
        (_) async =>
            NetworkSuccess<Map<String, dynamic>>({'survey': publicDoc}),
      );

      final result = await repository.fetchPublicSurvey(
        'http://local.example',
        'team-1',
        'survey-1',
      );

      expect(result?['_id'], 'survey-1');
      expect(result?['name'], 'Public survey');
      verify(
        () => api.getJsonObject(
          'http://local.example/api/public/surveys/team-1/survey-1',
        ),
      ).called(1);
    });

    test('fetches a bare survey document when there is no wrapper', () async {
      when(() => api.getJsonObject(any())).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>(publicDoc),
      );

      final result = await repository.fetchPublicSurvey(
        'http://local.example',
        'team-1',
        'survey-1',
      );

      expect(result?['_id'], 'survey-1');
    });

    test('falls back to the configured alternative server', () async {
      repository = SurveysRepository(
        api,
        database.surveyDao,
        database.examDao,
        SubmissionsRepository(
          api,
          database.submissionDao,
          database.submitPhotosDao,
          database.surveyDao,
          database.examDao,
        ),
        urlMapper: ServerUrlMapper(
          mappings: {'http://local.example': 'https://alt.example'},
        ),
      );
      when(() => api.getJsonObject(any())).thenAnswer((invocation) async {
        final url = invocation.positionalArguments.single as String;
        if (url.startsWith('http://local.example')) {
          return NetworkError<Map<String, dynamic>>(503, 'unreachable');
        }
        return NetworkSuccess<Map<String, dynamic>>(publicDoc);
      });

      final result = await repository.fetchPublicSurvey(
        'http://local.example',
        'team-1',
        'survey-1',
      );

      expect(result?['_id'], 'survey-1');
      verify(
        () => api.getJsonObject(
          'http://local.example/api/public/surveys/team-1/survey-1',
        ),
      ).called(1);
      verify(
        () => api.getJsonObject(
          'https://alt.example/api/public/surveys/team-1/survey-1',
        ),
      ).called(1);
    });

    test('submits public answers and coerces birth year to age', () async {
      await database.surveyDao.upsertAll(
        [SurveysCompanion.insert(id: 'survey-1', name: const Value('Needs'))],
        {
          'survey-1': [
            SurveyQuestionsCompanion.insert(
              id: 'survey-1:q1',
              surveyId: 'survey-1',
              questionId: const Value('q1'),
              body: const Value('Topics'),
              type: const Value('selectMultiple'),
              position: 0,
            ),
            SurveyQuestionsCompanion.insert(
              id: 'survey-1:q2',
              surveyId: 'survey-1',
              questionId: const Value('q2'),
              body: const Value('Gender'),
              type: const Value('select'),
              position: 1,
            ),
            SurveyQuestionsCompanion.insert(
              id: 'survey-1:q3',
              surveyId: 'survey-1',
              questionId: const Value('q3'),
              body: const Value('Notes'),
              type: const Value('textarea'),
              position: 2,
            ),
          ],
        },
      );

      final id = await repository.submitResponse('survey-1', 'anon', {
        'survey-1:q1': SubmissionDraftAnswer(
          questionId: 'q1',
          choices: [
            jsonEncode(const ExamChoice(id: 'water', text: 'Water').toJson()),
            jsonEncode(const ExamChoice(id: 'health', text: 'Health').toJson()),
          ],
        ),
        'survey-1:q2': SubmissionDraftAnswer(
          questionId: 'q2',
          choices: [
            jsonEncode(const ExamChoice(id: 'male', text: 'Male').toJson()),
          ],
        ),
        'survey-1:q3': const SubmissionDraftAnswer(
          questionId: 'q3',
          value: 'more water',
        ),
      });
      await SubmissionsRepository(
        api,
        database.submissionDao,
        database.submitPhotosDao,
        database.surveyDao,
        database.examDao,
      ).markSubmissionComplete(id!, {'birthYear': '2000', 'gender': 'male'});

      Map<String, dynamic>? capturedBody;
      when(
        () => api.sendJsonDynamic(
          any(),
          body: any(named: 'body'),
          method: any(named: 'method'),
        ),
      ).thenAnswer((invocation) async {
        capturedBody = invocation.namedArguments[#body] as Map<String, dynamic>;
        return const NetworkSuccess<dynamic>({});
      });

      final success = await repository.submitPublicSurvey(
        baseUrl: 'http://local.example',
        teamId: 'team-1',
        surveyId: 'survey-1',
        submissionId: id,
      );

      expect(success, isTrue);
      expect(capturedBody?['answers'], [
        [
          {'id': 'water', 'text': 'Water'},
          {'id': 'health', 'text': 'Health'},
        ],
        {'id': 'male', 'text': 'Male'},
        'more water',
      ]);
      final user = capturedBody?['user'] as Map<String, dynamic>?;
      expect(user?['gender'], 'male');
      expect(user?['age'], greaterThan(0));
      expect(user?.containsKey('birthYear'), isFalse);
      verify(
        () => api.sendJsonDynamic(
          'http://local.example/api/public/surveys/team-1/survey-1/submissions',
          body: any(named: 'body'),
          method: 'POST',
        ),
      ).called(1);
    });

    test(
      'submits public answers to the alternative server when primary fails',
      () async {
        await database.surveyDao.upsertAll(
          [SurveysCompanion.insert(id: 'survey-1', name: const Value('Needs'))],
          {
            'survey-1': [
              SurveyQuestionsCompanion.insert(
                id: 'survey-1:q1',
                surveyId: 'survey-1',
                questionId: const Value('q1'),
                body: const Value('Answer'),
                position: 0,
              ),
            ],
          },
        );

        final id = await repository.submitResponse('survey-1', 'anon', {
          'survey-1:q1': const SubmissionDraftAnswer(
            questionId: 'q1',
            value: 'yes',
          ),
        });

        repository = SurveysRepository(
          api,
          database.surveyDao,
          database.examDao,
          SubmissionsRepository(
            api,
            database.submissionDao,
            database.submitPhotosDao,
            database.surveyDao,
            database.examDao,
          ),
          urlMapper: ServerUrlMapper(
            mappings: {'http://local.example': 'https://alt.example'},
          ),
        );

        when(
          () => api.sendJsonDynamic(
            any(),
            body: any(named: 'body'),
            method: any(named: 'method'),
          ),
        ).thenAnswer((invocation) async {
          final url = invocation.positionalArguments.single as String;
          if (url.startsWith('http://local.example')) {
            return NetworkError<dynamic>(503, 'unreachable');
          }
          return const NetworkSuccess<dynamic>({});
        });

        final success = await repository.submitPublicSurvey(
          baseUrl: 'http://local.example',
          teamId: 'team-1',
          surveyId: 'survey-1',
          submissionId: id!,
        );

        expect(success, isTrue);
        verify(
          () => api.sendJsonDynamic(
            'http://local.example/api/public/surveys/team-1/survey-1/submissions',
            body: any(named: 'body'),
            method: 'POST',
          ),
        ).called(1);
        verify(
          () => api.sendJsonDynamic(
            'https://alt.example/api/public/surveys/team-1/survey-1/submissions',
            body: any(named: 'body'),
            method: 'POST',
          ),
        ).called(1);
      },
    );
  });
}
