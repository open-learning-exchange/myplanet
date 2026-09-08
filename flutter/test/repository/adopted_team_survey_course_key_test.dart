import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// **The pair, not the halves.** Phase 136, and the same class as Phase 125.
///
/// `SurveysRepositoryImpl.createMappedSurvey` copies the source survey's
/// `courseId` and `stepId` onto an adopted team clone
/// (`SurveysRepositoryImpl.kt:180-181`). The port's `adoptSurvey` copied
/// neither, and once Phase 132 made Send pass the *clone's* id,
/// `createBulkSurveySubmissions` resolved `survey?.courseId == null` and keyed
/// every member's answer sheet with the bare clone id where Kotlin keys
/// `"<cloneId>@<courseId>"`. Each half had a passing test; only the pair was
/// wrong.
///
/// Nothing here fabricates the join. The survey row comes out of
/// `SurveyMapper.fromCourseDoc` reading a course document shaped like the
/// server's, so `surveys.courseId` is populated for the same reason it is in
/// the field, and the clone is produced by the production `adoptSurvey`.
void main() {
  late AppDatabase database;
  late SubmissionsRepository submissions;
  late SurveysRepository surveys;

  /// The reachable shape: a course step's survey that is **also** shareable
  /// with teams. `SurveyMapper._build` reads `teamShareAllowed` off the
  /// embedded survey JSON and writes `courseId`/`stepId` from the step it was
  /// found on, so one document produces both joins at once.
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

  /// `adoptSurvey`'s default clone id — `'${surveyId}_$teamId'`.
  const cloneId = 'survey-1_team-1';

  setUp(() async {
    database = AppDatabase.memory();
    final api = MockPlanetApi();
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
  });

  tearDown(() => database.close());

  test('the fixture is reachable, not fabricated', () async {
    // If this fails the rest of the file proves nothing: the chain needs a
    // survey that is course-attached *and* offered for adoption, and both
    // columns have to come off the one server-shaped document.
    final attached = await database.surveyDao.getByCourseId('course-1');
    expect(attached.map((row) => row.id), ['survey-1']);
    expect(attached.single.teamShareAllowed, isTrue);
    expect(
      (await surveys.adoptableTeamSurveys('team-1')).map((row) => row.id),
      ['survey-1'],
      reason: 'a course-attached survey really is adoptable',
    );
  });

  Future<void> adopt() => surveys.adoptSurvey(
    surveyId: 'survey-1',
    userId: 'leader-1',
    teamId: 'team-1',
    teamName: 'Water Team',
    isTeam: true,
    planetCode: 'planet-a',
    parentCode: 'parent-a',
    now: DateTime.fromMillisecondsSinceEpoch(5000),
  );

  test('the clone carries the course, as Kotlin\'s does', () async {
    await adopt();
    final clone = await database.surveyDao.adoptedTeamSurvey(
      'team-1',
      'survey-1',
    );
    expect(clone, isNotNull);
    expect(
      clone!.courseId,
      'course-1',
      reason: '`createMappedSurvey` assigns `courseId = exam.courseId`',
    );
    // `stepId` is deliberately *not* copied — see `adoptSurvey`'s doc comment.
    // Kotlin's step ids are content hashes; the port's are positional and the
    // courses walk owns the join, so a copied `stepId` would be revoked (and
    // the `courseId` nulled with it) by `releaseStepJoinsForCourse`.
    expect(clone.stepId, isNull);
  });

  test('the members\' answer sheets carry the compound key', () async {
    await adopt();
    // Phase 132: Send passes the clone's own id, never the source's.
    await submissions.createBulkSurveySubmissions(cloneId, const [
      'member-1',
      'member-2',
    ]);
    final sheets = [
      for (final userId in ['member-1', 'member-2'])
        (await database.submissionDao.getSurveySubmissionsByUser(
          userId,
        )).single,
    ];
    expect(
      sheets.map((row) => row.parentId),
      ['$cloneId@course-1', '$cloneId@course-1'],
      reason: 'Kotlin resolves the clone\'s own courseId for the whole batch',
    );
  });

  test('re-sending does not give a member a second sheet', () async {
    await adopt();
    await submissions.createBulkSurveySubmissions(cloneId, const ['member-1']);
    await submissions.createBulkSurveySubmissions(cloneId, const ['member-1']);
    expect(
      await database.submissionDao.getSurveySubmissionsByUser('member-1'),
      hasLength(1),
      reason: 'the existence check compares the whole parentId',
    );
  });

  test('the repair sweep now reaches a clone\'s stale sheets', () async {
    await adopt();
    // What a pre-Phase-136 build wrote: the bare clone id, because the clone
    // had no `courseId` for `examParentId` to fold in.
    await submissions.getOrCreateSurveySubmission(
      userId: 'member-1',
      parentId: cloneId,
      now: DateTime.fromMillisecondsSinceEpoch(6000),
      createId: () => 'stale-sheet',
    );
    expect(
      await submissions.repairCourseSurveyParentIds('course-1'),
      1,
      reason: 'the clone is in `getByCourseId` now, so the sweep sees it',
    );
    expect(
      (await submissions.getById('stale-sheet'))!.parentId,
      '$cloneId@course-1',
    );
    // And the writer's own repair, which is the path that actually runs when a
    // leader re-sends, leaves the member with one sheet rather than two.
    await submissions.createBulkSurveySubmissions(cloneId, const ['member-1']);
    expect(
      await database.submissionDao.getSurveySubmissionsByUser('member-1'),
      hasLength(1),
    );
  });

  test('the gate does not demand a team\'s private copy', () async {
    // The hazard the copy introduces, and the reason `hasUnfinishedSurveys`
    // filters clones explicitly instead of relying on `adoptSurvey` leaving
    // `courseId` null. Remove that filter and this fails: `getByCourseId`
    // returns the clone, no sheet exists for a learner outside the team, and
    // the course can never be finished.
    await adopt();
    expect(await database.surveyDao.getByCourseId('course-1'), hasLength(2));
    final id = await surveys.submitResponse('survey-1', 'outsider', const {});
    expect(id, isNotNull);
    expect(
      await submissions.hasUnfinishedSurveys('course-1', 'outsider'),
      isFalse,
      reason: 'the course asks for its own survey, not every team\'s clone',
    );
  });

  test('the adoption marker still keys the bare source id', () async {
    await adopt();
    final marker = (await database.submissionDao.getSurveySubmissionsByUser(
      'leader-1',
    )).singleWhere((row) => row.status == '');
    expect(
      marker.parentId,
      'survey-1',
      reason: '`findExistingAdoption` looks it up by the bare source id',
    );
    // Neither sweep may fold a course into it, or every adoption re-adopts.
    expect(await submissions.repairCourseSurveyParentIds('course-1'), 0);
    expect((await submissions.getById(marker.id))!.parentId, 'survey-1');
  });

  test('the marker\'s parent document carries the source course', () async {
    await adopt();
    final marker = (await database.submissionDao.getSurveySubmissionsByUser(
      'leader-1',
    )).singleWhere((row) => row.status == '');
    final parent = jsonDecode(marker.parent!) as Map<String, dynamic>;
    expect(
      parent['courseId'],
      'course-1',
      reason: '`createParentJsonString` writes `exam.courseId ?: ""`',
    );
    expect(parent['_id'], 'survey-1');
    expect(parent['teamShareAllowed'], isTrue);
    expect(parent['noOfQuestions'], 1);
  });

  test('a course-less survey is unaffected', () async {
    await database.surveyDao.upsertAll([
      SurveysCompanion.insert(
        id: 'loose',
        name: const Value('Loose'),
        teamShareAllowed: const Value(true),
      ),
    ], {});
    await surveys.adoptSurvey(
      surveyId: 'loose',
      userId: 'leader-1',
      teamId: 'team-1',
      teamName: 'Water Team',
      isTeam: true,
      now: DateTime.fromMillisecondsSinceEpoch(5000),
    );
    final clone = await database.surveyDao.adoptedTeamSurvey('team-1', 'loose');
    expect(clone!.courseId, isNull);
    await submissions.createBulkSurveySubmissions('loose_team-1', const [
      'member-1',
    ]);
    expect(
      (await database.submissionDao.getSurveySubmissionsByUser(
        'member-1',
      )).single.parentId,
      'loose_team-1',
      reason: 'no course to fold in, so the bare id is already the key',
    );
  });
}
