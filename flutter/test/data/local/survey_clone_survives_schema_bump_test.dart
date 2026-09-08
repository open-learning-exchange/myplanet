import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// The upgrade-path half of `adopted_survey_survives_sync_test.dart`.
///
/// Phase 138 stopped `SurveyDao.deleteNotIn` destroying an adopted team survey
/// clone on the next sync. It left the same loss reachable by an *upgrade*:
/// `surveys` and `survey_questions` were not in
/// [AppDatabase.localAuthorityTables] while `submissions`,
/// `submission_answers` and `submission_questions` all are — so a schema bump
/// on a handset that had adopted but not yet published dropped the clone and
/// its questions and **kept** the members' answer sheets.
///
/// Nothing here fabricates a join, for the reason the sync-path test gives: the
/// source survey comes out of `SurveyMapper.fromCourseDoc` reading a course
/// document shaped like the server's, and the clone is produced by the
/// production `adoptSurvey`.
void main() {
  late AppDatabase database;
  late SubmissionsRepository submissions;
  late SurveysRepository surveys;
  late MockPlanetApi api;

  const courseDoc = {
    '_id': 'course-1',
    'courseTitle': 'Clean water',
    'steps': [
      {
        'stepTitle': 'First',
        'survey': {
          '_id': 'survey-1',
          'type': 'surveys',
          'name': 'Water needs',
          'teamShareAllowed': true,
          'sourcePlanet': 'nation',
          'questions': [
            {'id': 's1', 'title': 'How was it?', 'type': 'input'},
          ],
        },
      },
    ],
  };

  const cloneId = 'survey-1_team-1';

  setUp(() async {
    database = AppDatabase(NativeDatabase.memory());
    api = MockPlanetApi();
    submissions = SubmissionsRepository(
      api,
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
      database.examDao,
      teamDao: database.teamDao,
    );
    surveys = SurveysRepository(
      api,
      database.surveyDao,
      database.examDao,
      submissions,
    );

    final parsed = CourseMapper.fromDoc(courseDoc)!;
    await database.courseDao.upsertAll([parsed.course], parsed.steps);
    for (final mapping in SurveyMapper.fromCourseDoc(
      courseDoc,
      stepIdFor: CourseMapper.stepIdFor,
    )) {
      await database.surveyDao.upsertAll(
        [mapping.survey],
        {mapping.survey.id.value: mapping.questions},
      );
    }
    await surveys.adoptSurvey(
      surveyId: 'survey-1',
      userId: 'user-1',
      userName: 'Ada',
      teamId: 'team-1',
      isTeam: true,
      teamName: 'Team One',
    );
  });
  tearDown(() => database.close());

  /// The real bump a v47 handset is one release away from.
  Future<void> runUpgrade({int from = 47}) async {
    final migrator = database.createMigrator();
    await database.migration.onUpgrade(migrator, from, database.schemaVersion);
  }

  test('an adopted, unpublished clone survives a schema bump', () async {
    expect(await database.surveyDao.getById(cloneId), isNotNull);
    expect(await database.surveyDao.questionsFor(cloneId), isNotEmpty);

    await runUpgrade();

    expect(
      await database.surveyDao.getById(cloneId),
      isNotNull,
      reason: 'the clone exists nowhere but this handset until it publishes',
    );
    expect(
      await database.surveyDao.questionsFor(cloneId),
      isNotEmpty,
      reason: 'a survey with no questions cannot be answered or reviewed',
    );
    expect(
      (await database.surveyDao.pendingAdoptedSurveys()).map((row) => row.id),
      contains(cloneId),
      reason: 'needsSync must survive too, or the clone never publishes',
    );
  });

  test(
    "a member's answer sheet does not outlive the survey it answers",
    () async {
      await submissions.createBulkSurveySubmissions(cloneId, const [
        'member-1',
      ]);
      final sheet = await database.submissionDao.latestPendingByUserAndParent(
        'member-1',
        '$cloneId@course-1',
      );
      expect(sheet, isNotNull, reason: 'Send writes the sheet under the clone');

      await runUpgrade();

      // `submissions` is preserved, so the sheet is still here. The defect was
      // the asymmetry: it outlived its own survey.
      expect(
        await database.submissionDao.latestPendingByUserAndParent(
          'member-1',
          '$cloneId@course-1',
        ),
        isNotNull,
      );
      expect(
        await database.surveyDao.getById(sheet!.parentId!.split('@').first),
        isNotNull,
        reason: 'an answer sheet whose survey is gone cannot be reviewed',
      );
    },
  );

  test(
    'a published survey is kept by the bump and evicted by the walk',
    () async {
      // The `teams`/`meetups` precedent: preserve the whole table and let the
      // next `deleteNotIn` evict the stale cache half. A published clone is a
      // cache row again — `markUploaded` clears `needsSync` — so the walk that
      // stops naming it is reporting a deletion this prune should honour.
      await database.surveyDao.markUploaded(cloneId, '1-abc');

      await runUpgrade();

      expect(
        await database.surveyDao.getById(cloneId),
        isNotNull,
        reason: 'the bump no longer decides which rows are stale',
      );

      await database.surveyDao.deleteNotIn(['survey-1']);

      expect(
        await database.surveyDao.getById(cloneId),
        equals(null),
        reason: 'the walk, not the migration, evicts a row the server dropped',
      );
      expect(await database.surveyDao.questionsFor(cloneId), isEmpty);
    },
  );
}
