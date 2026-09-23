import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/submissions_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// **A member answered the survey their leader sent them, and the dashboard
/// went on telling them to answer it.**
///
/// Kotlin reaches its survey answer sheet writer through `startExamSession`,
/// whose first statement is the resume:
///
/// ```kotlin
/// if (!recreate) {
///     val submission = hydrateSubmission(fetchPendingByUserAndParent(parentId, userId))
///     if (submission != null) return submission
/// }
/// ```
/// (`SubmissionsRepositoryImpl.kt:456-462`, over
/// `SubmissionDao.getPendingByUserAndParent`), and the survey launch passes
/// **`recreate = isTeam`** (`ExamTakingFragment.kt:154`). So an *individual*
/// survey resumes the pending sheet and a team or public one always creates.
/// When that sheet reaches `complete`, `saveExamAnswer` follows with
/// `deletePendingSurveyOrphans(submissionRow.parentId, submissionRow.userId)`
/// (`:608-610`).
///
/// The port had **neither**. `surveys_screen.dart:125` pushes `/surveys/<id>`
/// with no `?submission=`, so `SurveysRepository.submitResponse` went straight
/// to `createSurveyDraft`, which inserted a second row keyed on a fresh sha1.
/// The leader's `pending` sheet — written by `createBulkSurveySubmissions` ->
/// `getOrCreateSurveySubmission` — survived untouched, and
/// `pendingSurveysProvider` reads exactly those rows.
///
/// Note what makes this invisible to the tests that already existed: the
/// submit path passed (a `complete` row with the answers *is* written), the
/// dashboard-prompt path passed (it carries `?submission=` and resumes
/// correctly), and the DAO passed. Only the **pair** — answer from the list,
/// then read the prompt — is wrong. Phase 156's *"test the pair, not the
/// halves"*.
void main() {
  late AppDatabase database;
  late SubmissionsRepository submissions;

  /// `survey-1` is attached to `course-1`, so its `parentId` is the composite
  /// `survey-1@course-1` (Phase 125) rather than the bare id. That is
  /// load-bearing as a fixture: a resume keyed on the bare id would find
  /// nothing and this file would report a pass for an unfixed port.
  Future<SurveyRow> seedSurvey({String? courseId = 'course-1'}) async {
    await database.surveyDao.upsertAll(
      [
        SurveysCompanion.insert(
          id: 'survey-1',
          name: const Value('Water needs'),
          courseId: Value(courseId),
        ),
      ],
      {
        'survey-1': [
          SurveyQuestionsCompanion.insert(
            id: 'survey-1:q1',
            surveyId: 'survey-1',
            questionId: const Value('q1'),
            header: const Value('How far is the well?'),
            type: const Value('input'),
            position: 0,
          ),
        ],
      },
    );
    return (await database.surveyDao.getById('survey-1'))!;
  }

  Future<List<SurveyQuestionRow>> questions() =>
      database.surveyDao.questionsFor('survey-1');

  setUp(() {
    database = AppDatabase.memory();
    submissions = SubmissionsRepository(
      MockPlanetApi(),
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
      database.examDao,
      teamDao: database.teamDao,
    );
  });

  tearDown(() => database.close());

  test(
    'answering from the surveys list resumes the sheet the leader sent',
    () async {
      final survey = await seedSurvey();

      // The leader's send, through the production writer.
      await submissions.createBulkSurveySubmissions('survey-1', ['member-1']);
      final sent = await database.submissionDao.pendingSurveySubmissions(
        'member-1',
      );
      expect(sent, hasLength(1));
      final pendingId = sent.single.id;

      // The surveys list: no `?submission=`, so this is the route
      // `SurveysRepository.submitResponse` takes.
      final answeredId = await submissions.createSurveyDraft(
        survey: survey,
        questions: await questions(),
        userId: 'member-1',
        answers: {
          'survey-1:q1': const SubmissionDraftAnswer(value: 'Two hours'),
        },
      );

      expect(
        answeredId,
        pendingId,
        reason: 'the pending sheet is resumed, not duplicated',
      );

      final all = await database.submissionDao.watchForUser('member-1').first;
      expect(all, hasLength(1), reason: 'one answer sheet, not two');
      expect(all.single.status, 'complete');

      expect(
        await database.submissionDao.pendingSurveySubmissions('member-1'),
        isEmpty,
        reason: 'the dashboard prompt has nothing left to offer',
      );

      final answers = await database.submissionDao.answersFor(answeredId);
      expect(answers.single.value, 'Two hours');
    },
  );

  test(
    'a team survey creates a new sheet, as `recreate = isTeam` does',
    () async {
      final survey = await seedSurvey();
      await submissions.createBulkSurveySubmissions('survey-1', ['member-1']);
      final pendingId = (await database.submissionDao.pendingSurveySubmissions(
        'member-1',
      )).single.id;

      // The team surveys tab, which is where Kotlin sets `isTeam = true` and so
      // passes `recreate = true` — the resume is skipped and a team's answer
      // sheet is its own row.
      final answeredId = await submissions.createSurveyDraft(
        survey: survey,
        questions: await questions(),
        userId: 'member-1',
        answers: {
          'survey-1:q1': const SubmissionDraftAnswer(value: 'Two hours'),
        },
        teamId: 'team-1',
      );

      expect(
        answeredId,
        isNot(pendingId),
        reason: 'a team sheet does not adopt the individual pending one',
      );
      final all = await database.submissionDao.watchForUser('member-1').first;
      expect(all, hasLength(2));
      expect(
        (await database.submissionDao.getById(pendingId))!.status,
        'pending',
        reason:
            'and the orphan sweep is likewise skipped, so the individual sheet '
            'the leader sent still stands',
      );
    },
  );

  group('deletePendingSurveyOrphans', () {
    /// Seeds one pending individual sheet per caller-chosen identity. Rows are
    /// written through the DAO rather than through `getOrCreateSurveySubmission`
    /// because that method is find-or-create by (user, parent) and so cannot
    /// produce the duplicate this sweep exists to collect — a pre-Phase-125
    /// build could, and so can a pull.
    Future<void> seedPending({
      required String id,
      required String userId,
      required String parentId,
      String? teamId,
      String status = 'pending',
      String type = 'survey',
    }) => database.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: id,
        userId: Value(userId),
        parentId: Value(parentId),
        teamId: Value(teamId),
        type: Value(type),
        status: Value(status),
      ),
    ]);

    Future<Set<String>> remaining() async {
      final rows = await database.submissionDao.watchForUser('member-1').first;
      final others = await database.submissionDao
          .watchForUser('member-2')
          .first;
      return {...rows, ...others}.map((row) => row.id).toSet();
    }

    test('takes the siblings and spares everything else', () async {
      await seedPending(
        id: 'orphan',
        userId: 'member-1',
        parentId: 'survey-1@course-1',
      );
      await seedPending(
        id: 'other-user',
        userId: 'member-2',
        parentId: 'survey-1@course-1',
      );
      await seedPending(
        id: 'other-parent',
        userId: 'member-1',
        // The **bare** id, not the composite. `parentId` is compared whole in
        // Kotlin (`parentId IS :parentId`), so this is the row that proves the
        // sweep is not matching on a prefix or a `LIKE`.
        parentId: 'survey-1',
      );
      await seedPending(
        id: 'team-sheet',
        userId: 'member-1',
        parentId: 'survey-1@course-1',
        // `teamId IS NULL` in the Kotlin. Without this row, deleting that
        // clause leaves the suite green.
        teamId: 'team-1',
      );
      await seedPending(
        id: 'already-complete',
        userId: 'member-1',
        parentId: 'survey-1@course-1',
        status: 'complete',
      );
      await seedPending(
        id: 'an-exam',
        userId: 'member-1',
        parentId: 'survey-1@course-1',
        type: 'exam',
      );

      final deleted = await submissions.deletePendingSurveyOrphans(
        parentId: 'survey-1@course-1',
        userId: 'member-1',
      );

      expect(deleted, 1);
      expect(await remaining(), {
        'other-user',
        'other-parent',
        'team-sheet',
        'already-complete',
        'an-exam',
      });
    });

    test('carries the orphan\'s answers and questions out with it', () async {
      await seedPending(
        id: 'orphan',
        userId: 'member-1',
        parentId: 'survey-1@course-1',
      );
      await database.submissionDao.upsertAll(
        const [],
        answers: {
          'orphan': [
            SubmissionAnswersCompanion.insert(
              id: 'orphan:q1',
              submissionId: 'orphan',
              value: const Value('stale'),
            ),
          ],
        },
        questions: {
          'orphan': [
            SubmissionQuestionsCompanion.insert(
              id: 'orphan:q1',
              submissionId: 'orphan',
              position: 0,
            ),
          ],
        },
      );
      expect(await database.submissionDao.answersFor('orphan'), hasLength(1));

      await submissions.deletePendingSurveyOrphans(
        parentId: 'survey-1@course-1',
        userId: 'member-1',
      );

      expect(await database.submissionDao.answersFor('orphan'), isEmpty);
      expect(
        await database.submissionDao.watchQuestions('orphan').first,
        isEmpty,
      );
    });
  });

  /// The two tests above prove [SubmissionsRepository.deletePendingSurveyOrphans]
  /// *works*; these two prove something **calls** it. Phase 154 shipped a
  /// correct, fully tested `sweepPendingResources` that nothing invoked, and
  /// every test drove the function directly — so a sweep with only the group
  /// above behind it could be deleted from both call sites with this file
  /// still green.
  group('the sweep is reached from both completion paths', () {
    /// Two pending sheets for one person and one survey. Only a pre-Phase-125
    /// build or a pull can produce this — `getOrCreateSurveySubmission` is
    /// find-or-create — which is exactly why Kotlin carries a sweep rather
    /// than relying on its writers.
    Future<void> seedTwoPending() => database.submissionDao.upsertAll([
      for (final id in ['sheet-a', 'sheet-b'])
        SubmissionsCompanion.insert(
          id: id,
          userId: const Value('member-1'),
          parentId: const Value('survey-1@course-1'),
          type: const Value('survey'),
          status: const Value('pending'),
          lastUpdateTime: Value(id == 'sheet-a' ? 2000 : 1000),
        ),
    ]);

    test('createSurveyDraft', () async {
      final survey = await seedSurvey();
      await seedTwoPending();

      await submissions.createSurveyDraft(
        survey: survey,
        questions: await questions(),
        userId: 'member-1',
        answers: {
          'survey-1:q1': const SubmissionDraftAnswer(value: 'Two hours'),
        },
      );

      final rows = await database.submissionDao.watchForUser('member-1').first;
      expect(
        rows.map((row) => row.id),
        // `lastUpdateTime DESC LIMIT 1` picks `sheet-a`; the sweep takes the
        // one left behind.
        ['sheet-a'],
        reason: 'one resumed, the other swept — not two rows, not three',
      );
      expect(rows.single.status, 'complete');
    });

    test('updateSurveyAnswers', () async {
      await seedSurvey();
      await seedTwoPending();

      // The dashboard-prompt route: `?submission=` names the sheet, so
      // `SurveysRepository.updateSurveyResponse` resumes this one by id.
      await submissions.updateSurveyAnswers(
        submissionId: 'sheet-b',
        questions: await questions(),
        answers: {
          'survey-1:q1': const SubmissionDraftAnswer(value: 'Two hours'),
        },
      );

      final rows = await database.submissionDao.watchForUser('member-1').first;
      expect(rows.map((row) => row.id), ['sheet-b']);
      expect(rows.single.status, 'complete');
    });
  });

  test('resuming a pulled sheet keeps its server identity', () async {
    final survey = await seedSurvey();
    // A pending sheet that came *down* from Planet: it has a `_id`/`_rev`, so
    // the next upload has to be a PUT. Re-inserting the row instead of
    // updating it would null both and the port would POST a second document
    // for one answer sheet.
    await database.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 'sheet-from-server',
        couchId: const Value('sheet-from-server'),
        rev: const Value('3-abc'),
        userId: const Value('member-1'),
        parentId: const Value('survey-1@course-1'),
        type: const Value('survey'),
        status: const Value('pending'),
        startTime: const Value(1000),
      ),
    ]);

    final id = await submissions.createSurveyDraft(
      survey: survey,
      questions: await questions(),
      userId: 'member-1',
      answers: {'survey-1:q1': const SubmissionDraftAnswer(value: 'Two hours')},
    );

    final row = (await database.submissionDao.getById(id))!;
    expect(row.id, 'sheet-from-server');
    expect(row.rev, '3-abc', reason: 'the revision the PUT needs');
    expect(row.couchId, 'sheet-from-server');
    expect(
      row.startTime,
      1000,
      reason: '`getByParentUserAndStatus` sorts on it; a resume is not a start',
    );
    expect(row.status, 'complete');
    expect(row.isUpdated, isTrue);
  });
}
