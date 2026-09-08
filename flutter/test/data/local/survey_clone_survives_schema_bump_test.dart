import 'package:drift/native.dart';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/repository/adopted_surveys_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';

import '../../repository/device_identity_fixture.dart';

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
  late OutboxRepository outbox;
  late AdoptedSurveysUploader uploader;
  late MockPlanetApi api;
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

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
    outbox = OutboxRepository(database.outboxDao);
    uploader = AdoptedSurveysUploader(
      api,
      database.surveyDao,
      outbox,
      testDeviceIdentity,
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

  /// The one row the v47 backfill flags that this device did **not** author,
  /// and why it is accepted rather than predicated away.
  ///
  /// The backfill identifies its own work by the port's deterministic clone id
  /// (`'<sourceSurveyId>_<teamId>'`, which Kotlin's `UUID.randomUUID()` cannot
  /// collide with). A row can satisfy that *and* `_rev IS NULL` *and*
  /// `step_id IS NULL` without being local work, through one route:
  ///
  ///  1. another handset at v47+ publishes the clone, so the document exists;
  ///  2. it is attached to a course step on Planet, and this handset's courses
  ///     walk writes the row back — `SurveyMapper._build` assigns `rev`
  ///     unconditionally and a course sub-object carries no `_rev`, so the
  ///     authoritative revision is clobbered to NULL (the two-writer conflict
  ///     Phase 138 recorded and did not fix);
  ///  3. the course document stops naming it, and
  ///     `releaseStepJoinsForCourse` nulls `stepId` **and** `courseId`;
  ///  4. *then* this handset upgrades off its pre-v47 build.
  ///
  /// **`rev` is the only column that records "the server has this", and it is
  /// exactly the column those two writers disagree about** — so there is no
  /// local discriminator left to add. Narrowing on `courseId` would miss a
  /// genuine clone of a course-attached survey (`adoptSurvey` copies
  /// `courseId`), and requiring the adoption-marker submission would miss a
  /// clone adopted with no `userId`.
  ///
  /// So it is left, because the cost is bounded and self-clearing: the POST
  /// takes a 409, and Phase 138's `_adoptExistingDocument` GETs the winning
  /// document, records its rev and clears the flag. One POST and one GET,
  /// once — after which the row rejoins the prune. Pinned as a **pair** rather
  /// than asserted, because each half passing alone is the shape this project
  /// keeps getting wrong.
  test(
    'a flagged row the server already has is resolved by the 409 arm',
    () async {
      // Step 3's outcome, written straight into the table: the shape a pre-v47
      // database actually holds when it reaches step 4.
      await database.customStatement('DELETE FROM surveys');
      await database.customStatement(
        'INSERT INTO surveys (id, name, _rev, source_survey_id, team_id, '
        'step_id, course_id, needs_sync) VALUES '
        "('survey-3_team-9', 'Other handset clone', NULL, 'survey-3', "
        "'team-9', NULL, NULL, 0)",
      );

      await runUpgrade(from: 46);

      expect(
        (await database.surveyDao.pendingAdoptedSurveys()).map((row) => row.id),
        ['survey-3_team-9'],
        reason: 'the misfire is real; what follows is why it is accepted',
      );

      // Drain it. The document exists, so the POST conflicts.
      expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
      final operation = (await outbox.due()).single;
      when(
        () => api.postJsonObject(
          operation.endpoint,
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async =>
            const NetworkError<Map<String, dynamic>>(409, 'Document conflict'),
      );
      when(
        () => api.getJsonObject(
          '${operation.endpoint}/survey-3_team-9',
          authHeader: 'Basic x',
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          '_id': 'survey-3_team-9',
          '_rev': '4-winner',
        }),
      );

      expect(
        await uploader.handler(
          operation,
          jsonDecode(operation.payload) as Map<String, dynamic>,
          'Basic x',
        ),
        isA<NetworkSuccess<Map<String, dynamic>>>(),
      );

      final row = (await database.surveyDao.getById('survey-3_team-9'))!;
      expect(row.rev, '4-winner');
      expect(row.needsSync, isFalse, reason: 'the flag must not persist');
      expect(
        await database.surveyDao.pendingAdoptedSurveys(),
        isEmpty,
        reason: 'one POST and one GET, once — not one per sync forever',
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
