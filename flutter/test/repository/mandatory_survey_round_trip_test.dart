import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:drift/drift.dart' show Value;
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// **The pair, not the halves.** Phase 125.
///
/// Every writer of a course-attached survey's submission stored the bare survey
/// id; `hasUnfinishedSurveys` — the gate on finishing
/// `MANDATORY_SURVEY_COURSE_ID` — queried `"$surveyId@$courseId"`. Each half had
/// a passing test: the writers were pinned on the fields they set, and the
/// reader's own test seeded its row through `upsertDocuments` with a
/// **hand-written** `'parentId': 'survey-a@course-1'` — a key the port's own
/// writers could not produce. So nothing failed, and a learner who had answered
/// the survey was told to answer it again, forever.
///
/// Every submission here is authored by the production writer and every survey
/// row comes out of `SurveyMapper.fromCourseDoc` reading a course document
/// shaped like the server's, so the `surveys.courseId` the writer reads and the
/// `courseId` the reader is asked about are the same value for the same reason
/// they are in the field.
void main() {
  late AppDatabase database;
  late SubmissionsRepository submissions;
  late SurveysRepository surveys;

  /// A course document with one step carrying one survey, put through the real
  /// mappers — the shape `CoursesRepository.sync` writes.
  const courseDoc = {
    '_id': 'course-1',
    'courseTitle': 'MyPlanet Onboarding',
    'steps': [
      {
        'stepTitle': 'First',
        'survey': {
          '_id': 'survey-1',
          'type': 'surveys',
          'name': 'Onboarding survey',
          'questions': [
            {'id': 's1', 'title': 'How was it?', 'type': 'input'},
          ],
        },
      },
    ],
  };

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

  test('the mapped survey really is attached to the course', () async {
    // If this fails the rest of the file proves nothing: the reader looks the
    // survey up by `courseId`, so a mapper that dropped the column would make
    // every assertion below vacuous.
    final attached = await database.surveyDao.getByCourseId('course-1');
    expect(attached.map((row) => row.id), ['survey-1']);
    expect(attached.single.courseId, 'course-1');
  });

  test(
    'a learner who answered the attached survey can finish the course',
    () async {
      // Pre-fix: `createSurveyDraft` stored `parentId: 'survey-1'` and
      // `hasUnfinishedSurveys` counted `'survey-1@course-1'`, so this was
      // `Expected: false / Actual: true` — the toast on every Finish tap.
      expect(
        await submissions.hasUnfinishedSurveys('course-1', 'user-1'),
        isTrue,
        reason: 'nothing answered yet',
      );

      final id = await surveys.submitResponse('survey-1', 'user-1', const {});
      expect(id, isNotNull);

      expect(
        await submissions.hasUnfinishedSurveys('course-1', 'user-1'),
        isFalse,
        reason: 'the learner answered the attached survey',
      );
    },
  );

  test('the answer sheet carries Kotlin\'s composite key', () async {
    final id = await surveys.submitResponse('survey-1', 'user-1', const {});
    final row = await submissions.getById(id!);
    // `createExamSubmission` (`SubmissionsRepositoryImpl.kt:449-456`).
    expect(row!.parentId, 'survey-1@course-1');
  });

  test('another learner is still blocked by their own survey', () async {
    await surveys.submitResponse('survey-1', 'user-1', const {});
    // `countByUserParentAndType` is scoped to the user, so one learner's sheet
    // must not unblock the course for the next one on a shared handset.
    expect(
      await submissions.hasUnfinishedSurveys('course-1', 'user-2'),
      isTrue,
    );
  });

  test('a bare-id sheet an earlier build wrote unblocks the course', () async {
    // The field-data case: this device answered the survey before the writers
    // were corrected, so the row on disk carries the bare id. It does not heal
    // on sync — the port uploads the row's own `parentId` and `upsertDocuments`
    // writes the document's value straight back — so without the repair the
    // learner is told to answer again.
    final id = await surveys.submitResponse('survey-1', 'user-1', const {});
    await (database.update(database.submissions)
          ..where((row) => row.id.equals(id!)))
        .write(const SubmissionsCompanion(parentId: Value('survey-1')));

    expect(
      await submissions.hasUnfinishedSurveys('course-1', 'user-1'),
      isFalse,
    );
    // Repaired in place rather than answered a second time.
    final row = await submissions.getById(id!);
    expect(row!.parentId, 'survey-1@course-1');
    expect(
      await database.submissionDao.getSurveySubmissionsByUser('user-1'),
      hasLength(1),
    );
  });

  test('a pending sheet sent to the learner blocks and then resumes', () async {
    // `SendSurveyFragment` -> `createBulkSurveySubmissions`, then the learner
    // opens the sheet from the dashboard prompt and `updateSurveyResponse`
    // resumes it. Three keys have to agree across that path.
    await submissions.createBulkSurveySubmissions('survey-1', const ['user-1']);
    final pending = await database.submissionDao.getSurveySubmissionsByUser(
      'user-1',
    );
    expect(pending.single.parentId, 'survey-1@course-1');
    // A sent-but-unanswered sheet is `pending`, and `countByUserParentAndType`
    // has no status filter, so Kotlin counts it: the course is not blocked by a
    // survey the learner has been *sent*.
    expect(
      await submissions.hasUnfinishedSurveys('course-1', 'user-1'),
      isFalse,
    );

    // With the writers corrected and this reader left raw,
    // `updateSurveyResponse` looked `'survey-1@course-1'` up as a survey id,
    // found no questions, and wrote **no answers at all** — the learner's
    // resumed sheet went up empty.
    final questions = await database.surveyDao.questionsFor('survey-1');
    final resumed = await surveys.updateSurveyResponse(
      pending.single.id,
      answers: {
        questions.single.id: const SubmissionDraftAnswer(
          questionId: 's1',
          value: 'It was fine',
        ),
      },
    );
    expect(resumed, pending.single.id);
    final answers = await database.submissionDao.answersFor(pending.single.id);
    expect(answers.map((row) => row.value), ['It was fine']);
  });

  test('an adopted team clone is not counted by the course gate', () async {
    // Phase 125 read this as "the clone must not join the course"; Phase 136
    // corrected it to "the clone joins the course and the gate must skip it".
    // Kotlin's `createMappedSurvey` copies the source survey's `courseId`
    // (`SurveysRepositoryImpl.kt:182`) and Send folds it into every member's
    // key, so the port has to copy it. `SurveyDao.getByCourseId` has no
    // adoption filter, so `hasUnfinishedSurveys` now skips
    // `sourceSurveyId != null` itself — otherwise a learner outside the
    // adopting team could never satisfy that team's copy. `stepId` stays null;
    // `adoptSurvey` documents why.
    await surveys.adoptSurvey(
      surveyId: 'survey-1',
      userId: 'user-1',
      teamId: 'team-1',
      isTeam: true,
      teamName: 'Team One',
    );
    final clone = await database.surveyDao.adoptedTeamSurvey(
      'team-1',
      'survey-1',
    );
    expect(clone, isNotNull);
    expect(clone!.courseId, 'course-1', reason: 'as Kotlin\'s clone does');
    expect(clone.stepId, isNull);
    expect(
      await database.surveyDao.getByCourseId('course-1'),
      hasLength(2),
      reason: 'the clone is in the query the gate reads; the gate skips it',
    );

    // And the adoption marker it wrote — bare id, `status = ''` — must not be
    // mistaken for an answered sheet, nor repaired into the composite key.
    expect(
      await submissions.hasUnfinishedSurveys('course-1', 'user-1'),
      isTrue,
    );
    final marker = (await database.submissionDao.getSurveySubmissionsByUser(
      'user-1',
    )).singleWhere((row) => row.status == '');
    expect(marker.parentId, 'survey-1');
    expect(await submissions.repairCourseSurveyParentIds('course-1'), 0);
    expect(
      (await submissions.getById(marker.id))!.parentId,
      'survey-1',
      reason: '`findExistingAdoption` looks this up by the bare id',
    );
  });

  test('the repair is idempotent and reports what it changed', () async {
    final id = await surveys.submitResponse('survey-1', 'user-1', const {});
    await (database.update(database.submissions)
          ..where((row) => row.id.equals(id!)))
        .write(const SubmissionsCompanion(parentId: Value('survey-1')));

    expect(await submissions.repairCourseSurveyParentIds('course-1'), 1);
    expect(await submissions.repairCourseSurveyParentIds('course-1'), 0);
    expect(await submissions.repairCourseSurveyParentIds(''), 0);
  });

  test('the repair leaves a survey with no course alone', () async {
    // Its bare id is already what `examParentId` produces, so there is nothing
    // to rewrite and rewriting it would invent a course join.
    await database.surveyDao.upsertAll([
      SurveysCompanion.insert(id: 'standalone', name: const Value('Loose')),
    ], {});
    final standalone = (await database.surveyDao.getById('standalone'))!;
    final id = await submissions.createSurveyDraft(
      survey: standalone,
      questions: const [],
      userId: 'user-1',
    );
    expect((await submissions.getById(id))!.parentId, 'standalone');
    await submissions.repairCourseSurveyParentIds('course-1');
    expect((await submissions.getById(id))!.parentId, 'standalone');
  });

  test('createBulkSurveySubmissions keys a course-less survey bare', () async {
    await database.surveyDao.upsertAll([
      SurveysCompanion.insert(id: 'standalone', name: const Value('Loose')),
    ], {});
    await submissions.createBulkSurveySubmissions('standalone', const [
      'user-1',
    ]);
    final rows = await database.submissionDao.getSurveySubmissionsByUser(
      'user-1',
    );
    // `createBulkSurveySubmissions` (`:201-206`) falls back to the bare exam id
    // when the lookup finds no course.
    expect(rows.single.parentId, 'standalone');
  });

  group('parentBaseId', () {
    test('strips the course half and passes a bare id through', () {
      expect(
        SubmissionsRepository.parentBaseId('survey-1@course-1'),
        'survey-1',
      );
      expect(SubmissionsRepository.parentBaseId('survey-1'), 'survey-1');
      expect(SubmissionsRepository.parentBaseId(null), isNull);
      // `substringBefore` on a leading delimiter yields the empty string, and
      // callers treat that as "no id" rather than matching every row.
      expect(SubmissionsRepository.parentBaseId('@course-1'), '');
    });
  });

  test('a synced adoption marker is not repaired into the gate key', () async {
    // **The live defect the second audit found in the repair itself**, and it
    // arrives by a route the locally-authored case cannot show.
    //
    // An adoption marker is `status = ''` on the device that wrote it, and
    // `createSurveyAdoptionSubmission` also sets `isUpdated: true` — so the
    // generic `pendingUploads` sweep uploads it. `serialize` sends
    // `'status': ''`, and the pull reads it back through
    // `JsonUtils.getStringOrNull`, which maps the empty string to **null**
    // (`json_utils.dart:19-22`). So every marker that has round-tripped, and
    // every marker a Kotlin handset in the same deployment published, carries
    // `status = NULL` rather than `''`.
    //
    // The guard was `row.status.isNotValue('')`, and drift's `isNotValue`
    // emits SQL `IS NOT`, not `!=` (`expression.dart:118-120` -> `:148-154`).
    // `NULL IS NOT ''` is **true**, so the guard admitted exactly the rows it
    // existed to skip: the marker was rewritten to `survey-1@course-1`, the
    // status-blind count accepted it, and the learner's Finish sailed past a
    // survey they had never answered. Kotlin's own predicate is
    // `it.status.orEmpty().isEmpty()` (`SurveysRepositoryImpl.kt:203-207`) —
    // negating that needs `coalesce(status, '') != ''`, which is the idiom
    // `pendingUploads` already uses for the same reason.
    //
    // In a real deployment this is the repair's *most likely* match, not a
    // corner case: a bare-id answer sheet can only come from a pre-Phase-125
    // port build, while a bare-id adoption marker is written on purpose by
    // every Kotlin handset there is.
    await submissions.upsertDocuments([
      {
        '_id': 'adopt-doc-1',
        '_rev': '1-a',
        'parentId': 'survey-1',
        'type': 'survey',
        'status': '',
        'user': {'_id': 'org.couchdb.user:ada'},
      },
    ]);
    final synced = await submissions.getById('adopt-doc-1');
    // Phase 151 moved this: the pull used to fold `''` to null and now stores
    // Kotlin's `''` verbatim. The assertion below is unaffected either way,
    // which is the point of `_repairSurveyParentId`'s
    // `coalesce(status, '') != ''` — it skipped the NULL and skips the `''`.
    expect(
      synced!.status,
      '',
      reason: 'the pull stores the empty status Kotlin stores',
    );

    expect(
      await submissions.hasUnfinishedSurveys(
        'course-1',
        'org.couchdb.user:ada',
      ),
      isTrue,
      reason: 'nothing has been answered; the marker is not an answer sheet',
    );
    expect(
      (await submissions.getById('adopt-doc-1'))!.parentId,
      'survey-1',
      reason: '`findExistingAdoption` looks this up by the bare id',
    );
  });

  test(
    're-sending a survey does not duplicate a legacy pending sheet',
    () async {
      // Finding 2 of the implementation audit, and a gap the writer fix itself
      // opened. `getOrCreateSurveySubmission`'s existence check is
      // `latestPendingByUserAndParent`, which compares the **whole** `parentId`.
      // Pre-fix the writer and that lookup agreed on the bare key; with the
      // writer corrected and no repair, a legacy bare-id pending sheet is
      // invisible to it and the member gets a second sheet for one survey.
      await submissions.createBulkSurveySubmissions('survey-1', const [
        'user-1',
      ]);
      final first = (await database.submissionDao.getSurveySubmissionsByUser(
        'user-1',
      )).single;
      await (database.update(database.submissions)
            ..where((row) => row.id.equals(first.id)))
          .write(const SubmissionsCompanion(parentId: Value('survey-1')));

      await submissions.createBulkSurveySubmissions('survey-1', const [
        'user-1',
      ]);

      final rows = await database.submissionDao.getSurveySubmissionsByUser(
        'user-1',
      );
      expect(rows, hasLength(1), reason: 'one survey, one pending sheet');
      expect(rows.single.id, first.id, reason: 'the existing sheet was reused');
      expect(rows.single.parentId, 'survey-1@course-1');
    },
  );

  test('the repair only touches survey-typed rows for this course', () async {
    // Findings 4 and 5: three clauses that were each revertible with the whole
    // suite green. The exam row matters because the two id spaces are not
    // disjoint — `_liveParentDocument`'s own comment says so — and an exam
    // attempt's key is the exam path's to write.
    await database.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 'exam-attempt',
        userId: const Value('user-1'),
        parentId: const Value('survey-1'),
        type: const Value('exam'),
        status: const Value('requires grading'),
      ),
      SubmissionsCompanion.insert(
        id: 'other-course-sheet',
        userId: const Value('user-1'),
        parentId: const Value('survey-elsewhere'),
        type: const Value('survey'),
        status: const Value('complete'),
      ),
    ]);

    expect(await submissions.repairCourseSurveyParentIds('course-1'), 0);
    expect((await submissions.getById('exam-attempt'))!.parentId, 'survey-1');
    expect(
      (await submissions.getById('other-course-sheet'))!.parentId,
      'survey-elsewhere',
    );
  });

  test('the repair keys a course-less survey bare when handed one', () async {
    // The `target == survey.id` branch. It is unreachable through
    // `repairCourseSurveyParentIds`, whose rows are selected by an exact
    // `courseId` match, and reachable through `createBulkSurveySubmissions`,
    // which repairs whatever survey it was handed.
    await database.surveyDao.upsertAll([
      SurveysCompanion.insert(id: 'standalone', name: const Value('Loose')),
    ], {});
    await submissions.createBulkSurveySubmissions('standalone', const [
      'user-1',
    ]);
    final rows = await database.submissionDao.getSurveySubmissionsByUser(
      'user-1',
    );
    expect(rows.single.parentId, 'standalone');
  });

  test('a null user does not block a course that has surveys', () async {
    // A deliberate divergence, and it was unpinned: the existing coverage
    // asserts a null user against a database with **no** surveys, where Kotlin
    // agrees. Kotlin's guard is inside `hasSubmission` (`:181-183`) and inverts
    // to *unfinished*, so Kotlin blocks here. Blocking a learner whose id we do
    // not have cannot be satisfied by answering.
    expect(await submissions.hasUnfinishedSurveys('course-1', null), isFalse);
    expect(await submissions.hasUnfinishedSurveys('course-1', ''), isFalse);
  });
}
