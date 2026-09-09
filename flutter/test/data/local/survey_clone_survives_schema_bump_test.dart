import 'package:drift/native.dart';
import 'dart:convert';

import 'package:drift/drift.dart' show Value;
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

  /// A survey another planet shared publicly must never be flagged, and this
  /// is the row that made the first cut of the backfill unsafe.
  ///
  /// A public survey link is `/survey/<teamId>/<surveyId>` and the `surveyId`
  /// in it *is* a team's adopted clone — so the document
  /// `saveSurveyFromPublicApi` stores carries the clone's own id,
  /// `sourceSurveyId` and `teamId` (`AdoptedSurveysUploader.documentFor` ->
  /// `SubmissionsRepository.surveyParentDocument`, which emits `_rev` only
  /// `if (survey.rev != null)`). Whether the row ends up with a NULL `rev`
  /// therefore depends on what a *remote* Planet chooses to send back, which
  /// this repository cannot check — and that is the argument on its own: a
  /// predicate whose safety rests on a server including a field is not safe.
  ///
  /// The harm was not the bounded, self-clearing kind either. The document
  /// lives on the **link's origin** planet, while
  /// `AdoptedSurveysUploader.endpointFor` POSTs to the **configured** server,
  /// which has no document under that id — so the POST *succeeds*, and a
  /// survey this device never authored is created in the user's own `exams`
  /// database under their credentials, stamped with this handset's origin
  /// fields. That is the leak `Surveys.needsSync` was introduced to close,
  /// reopened for one upgrade by the migration that installs the column.
  ///
  /// Closed by requiring the **adoption marker** `adoptSurvey` writes
  /// alongside the clone (`createSurveyAdoptionSubmission`: `parentId` is the
  /// *source* id, `teamId` the team). It is positive evidence of an action
  /// *this device took*, in a preserved table, which is what the v45 health
  /// repair's `EXISTS` conjunct is too — and unlike `rev` it has one writer.
  ///
  /// The document is not hand-written: it goes through the production
  /// `saveSurveyFromPublicApi`, because the first cut's negative fixtures were
  /// hand-written `INSERT`s and that is precisely why they missed this.
  test('a publicly shared clone from another planet is never flagged', () async {
    final saved = await surveys.saveSurveyFromPublicApi({
      '_id': 'survey-7_team-7',
      'type': 'surveys',
      'name': 'Borehole survey - Team Seven',
      // Absent, as `surveyParentDocument` emits it only for a non-null rev and
      // as `Surveys.rev`'s own doc-comment says every public-API survey is.
      'sourceSurveyId': 'survey-7',
      'teamId': 'team-7',
      'questions': [
        {'id': 'q1', 'title': 'Is the pump working?', 'type': 'input'},
      ],
    });
    expect(saved, isNotNull);
    expect(saved!.rev, equals(null), reason: 'the premise of the leak');
    expect(saved.needsSync, isFalse);
    expect(saved.stepId, equals(null));

    // And the harder one: another team's public clone of the **same source
    // survey this device did adopt**. A marker for `survey-1` exists (from
    // `setUp`), so matching the marker on `parentId` alone would flag this —
    // two teams adopting one shared survey is the ordinary case, not a corner.
    // It is the `teamId` half of the marker match that excludes it.
    final otherTeam = await surveys.saveSurveyFromPublicApi({
      '_id': 'survey-1_team-5',
      'type': 'surveys',
      'name': 'Water needs - Team Five',
      'sourceSurveyId': 'survey-1',
      'teamId': 'team-5',
      'questions': [
        {'id': 's1', 'title': 'How was it?', 'type': 'input'},
      ],
    });
    expect(otherTeam, isNotNull);

    await runUpgrade(from: 46);

    expect(
      (await database.surveyDao.pendingAdoptedSurveys()).map((row) => row.id),
      isNot(contains('survey-7_team-7')),
      reason:
          "this handset never adopted for team-7, so there is no marker "
          'and no claim to publish the document',
    );
    expect(
      (await database.surveyDao.pendingAdoptedSurveys()).map((row) => row.id),
      isNot(contains('survey-1_team-5')),
      reason: 'the marker is for team-1; team-5 adopted on its own handset',
    );
    // The genuine clone from `setUp`, which does have a marker, is untouched.
    expect(
      (await database.surveyDao.pendingAdoptedSurveys()).map((row) => row.id),
      contains(cloneId),
    );
  });

  /// `Surveys.needsSync` is preserved across a re-pull by **omission**, and
  /// nothing pinned that until now.
  ///
  /// Neither `SurveyMapper` writer mentions the column, so
  /// `insertOnConflictUpdate` writes only the columns the companion carries and
  /// leaves the stored flag alone. That is the whole mechanism — there is no
  /// `existing…` parameter here, so the static guard in
  /// `mapper_preserves_local_columns_test.dart` cannot see it, and the 409
  /// tests do not re-pull.
  ///
  /// It matters more now that `surveys` is preserved: if a later round
  /// "completes" the mapper with `needsSync: Value(false)` — the natural thing
  /// to do when filling in a companion — an unpublished clone would drop out of
  /// `pendingAdoptedSurveys` on the next walk that names its id and never
  /// publish. Same shape as Phase 56's credentials, Phase 74's reactions and
  /// Phase 98's read flag: a sync-in writing over a column the server knows
  /// nothing about.
  ///
  /// The document here is the one that reaches this handset in the two-leader
  /// case the 409 recovery arm exists for: another leader adopted the same
  /// source for the same team and published first, so the walk delivers a
  /// document under the port's deterministic clone id while this device's own
  /// row is still pending.
  test('a re-pull under the clone id leaves needsSync alone', () async {
    final before = (await database.surveyDao.getById(cloneId))!;
    expect(before.needsSync, isTrue, reason: 'adoptSurvey sets the flag');

    final mapped = SurveyMapper.fromDoc({
      '_id': cloneId,
      '_rev': '1-otherleader',
      'type': 'surveys',
      'name': 'Water needs - Team One',
      'teamId': 'team-1',
      'sourceSurveyId': 'survey-1',
      'questions': [
        {'id': 's1', 'title': 'How was it?', 'type': 'input'},
      ],
    })!;
    await database.surveyDao.upsertAll(
      [mapped.survey],
      {mapped.survey.id.value: mapped.questions},
    );

    final after = (await database.surveyDao.getById(cloneId))!;
    expect(
      after.rev,
      '1-otherleader',
      reason: 'the walk is authoritative for the columns it does carry',
    );
    expect(
      after.needsSync,
      isTrue,
      reason:
          'and must not touch the one it does not — the row stays this '
          "device's to reconcile, which the 409 arm then does",
    );
    expect(
      (await database.surveyDao.pendingAdoptedSurveys()).map((row) => row.id),
      contains(cloneId),
    );
  });

  /// The other route to a row that looks like local work, and it is closed by
  /// the same conjunct as the public-API one.
  ///
  /// A clone another handset published, which Planet then attached to a course
  /// step, arrives with the clone's own id and `sourceSurveyId` and with `rev`
  /// **NULL** — `SurveyMapper._build` assigns `rev` unconditionally and a
  /// course document's embedded survey is a sub-object carrying no `_rev` of
  /// its own (the two-writer conflict Phase 138 recorded and did not fix).
  /// Once the course stops naming it, `releaseStepJoinsForCourse` nulls
  /// `stepId` **and** `courseId`. Every conjunct but the marker then holds.
  ///
  /// Which is the point worth keeping: `rev` cannot carry this weight, because
  /// it is the one column two sync walks disagree about the ownership of. The
  /// adoption marker can, because it has exactly one writer.
  ///
  /// And where the marker *is* present, flagging is right rather than wrong:
  /// both leaders genuinely adopted, the port's deterministic id makes them
  /// converge on one document by design, and the 409 arm reconciles them —
  /// which the second half of this test drives, so the mitigation is pinned
  /// alongside the exclusion rather than assumed.
  test(
    'a step-released clone is flagged only if this device adopted it',
    () async {
      await database.customStatement('DELETE FROM surveys');
      await database.customStatement(
        'INSERT INTO surveys (id, name, _rev, source_survey_id, team_id, '
        'step_id, course_id, needs_sync) VALUES '
        "('survey-3_team-9', 'Other handset clone', NULL, 'survey-3', "
        "'team-9', NULL, NULL, 0)",
      );

      await runUpgrade(from: 46);

      expect(
        await database.surveyDao.pendingAdoptedSurveys(),
        isEmpty,
        reason: 'no marker for team-9: this handset never adopted survey-3',
      );

      // Now the marker a real local adoption leaves — written by the
      // production `adoptSurvey`, not by hand. This is the convergence case
      // exactly: the source survey is on the device and the other handset's
      // clone already sits under the deterministic id, so `adoptedTeamSurvey`
      // finds it and mints no second clone — but the marker is still written,
      // because that half is gated only on the user.
      await database.surveyDao.upsertAll([
        SurveysCompanion.insert(
          id: 'survey-3',
          name: const Value('Borehole survey'),
          teamShareAllowed: const Value(true),
        ),
      ], const {});
      await surveys.adoptSurvey(
        surveyId: 'survey-3',
        userId: 'user-1',
        userName: 'Ada',
        teamId: 'team-9',
        isTeam: true,
        teamName: 'Team Nine',
      );
      expect(
        (await database.surveyDao.getById('survey-3_team-9'))!.needsSync,
        isFalse,
        reason:
            'no second clone was minted, so the flag is still the pulled '
            "row's — which is what makes the backfill the thing under test",
      );

      await runUpgrade(from: 46);

      expect(
        (await database.surveyDao.pendingAdoptedSurveys()).map((row) => row.id),
        contains('survey-3_team-9'),
        reason: 'with the marker it is this device\'s to reconcile',
      );

      // And reconciling it is a 409, because the other handset published first.
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
      expect(row.needsSync, isFalse);
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
