import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
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
      SubmissionsRepository(api, database.submissionDao),
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
    expect(questions.last.choices, ['Water', 'Health']);
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
}
