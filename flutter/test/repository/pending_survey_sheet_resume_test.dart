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
  Future<SurveyRow> seedSurvey({
    String? courseId = 'course-1',
    int questionCount = 1,
  }) async {
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
          for (var i = 1; i <= questionCount; i++)
            SurveyQuestionsCompanion.insert(
              id: 'survey-1:q$i',
              surveyId: 'survey-1',
              questionId: Value('q$i'),
              header: Value('Question $i'),
              type: const Value('input'),
              position: i - 1,
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
      expect(
        all.map((row) => row.id),
        [answeredId],
        reason:
            'but the sweep still runs — `saveExamAnswer`\'s arm is '
            '`newStatus == "complete" && type == "survey"` with no team '
            'condition, and the statement\'s own `teamId IS NULL` is what '
            'spares the team row. Gating the sweep on the team as well left '
            'the team surveys tab, the port\'s only team survey entry point, '
            'with the stale prompt this file exists to close.',
      );
      expect(all.single.teamId, 'team-1');
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
    /// The two timestamps **disagree**, and that is the whole point of the
    /// fixture. `sheet-a` is the older sheet that was touched most recently;
    /// `sheet-b` is the newer sheet touched longest ago. Kotlin's live resume
    /// is `getPendingByUserAndParent`, `ORDER BY startTime DESC`, so it takes
    /// `sheet-b`; the dead `getLatestPendingByUserAndParent` — which the port's
    /// DAO method is named after, and which this lane's first cut called —
    /// orders by `lastUpdateTime DESC` and takes `sheet-a`. Give both rows the
    /// same `startTime` and the two readings become indistinguishable and this
    /// group stops being a test of anything.
    Future<void> seedTwoPending() => database.submissionDao.upsertAll([
      // `sheet-a` is inserted first *and* has the earlier `startTime`, so an
      // unordered `LIMIT 1` and the correct ordering disagree on which row
      // comes back — without that the `ORDER BY` could be deleted outright
      // with this group still green.
      SubmissionsCompanion.insert(
        id: 'sheet-a',
        userId: const Value('member-1'),
        parentId: const Value('survey-1@course-1'),
        type: const Value('survey'),
        status: const Value('pending'),
        startTime: const Value(100),
        lastUpdateTime: const Value(500),
      ),
      SubmissionsCompanion.insert(
        id: 'sheet-b',
        userId: const Value('member-1'),
        parentId: const Value('survey-1@course-1'),
        type: const Value('survey'),
        status: const Value('pending'),
        startTime: const Value(200),
        lastUpdateTime: const Value(300),
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
        // `ORDER BY startTime DESC LIMIT 1` picks `sheet-b`; the sweep takes
        // the one left behind. Resuming `sheet-a` here would mean the port and
        // the Android app update **different** CouchDB documents.
        ['sheet-b'],
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

  test(
    'the resume looks only at this learner\'s pending survey sheets',
    () async {
      final survey = await seedSurvey();
      // **One mutation this file does not pin, stated rather than hidden:**
      // deleting the resume's `ORDER BY` outright leaves the suite green.
      // Without it SQLite is free to return any matching row for `LIMIT 1` and
      // here it happens to return the right one; seeding around that would pin
      // a query plan rather than a behaviour. The mutation that matters —
      // ordering by `lastUpdateTime` instead, which is the dead Kotlin
      // statement's key and what this lane's first cut used — *is* red, below.
      //
      // Every decoy carries a **later** `startTime` than the real sheet, so
      // dropping any one conjunct from the resume predicate makes that decoy win
      // the `ORDER BY startTime DESC LIMIT 1` and this test go red. Give them
      // earlier timestamps and the predicate could be emptied out with the suite
      // still green — the sort would hide it.
      await database.submissionDao.upsertAll([
        SubmissionsCompanion.insert(
          id: 'the-real-one',
          userId: const Value('member-1'),
          parentId: const Value('survey-1@course-1'),
          type: const Value('survey'),
          status: const Value('pending'),
          startTime: const Value(100),
        ),
        SubmissionsCompanion.insert(
          id: 'already-complete',
          userId: const Value('member-1'),
          parentId: const Value('survey-1@course-1'),
          type: const Value('survey'),
          status: const Value('complete'),
          startTime: const Value(900),
        ),
        SubmissionsCompanion.insert(
          id: 'an-exam-attempt',
          userId: const Value('member-1'),
          parentId: const Value('survey-1@course-1'),
          type: const Value('exam'),
          status: const Value('pending'),
          startTime: const Value(900),
        ),
        SubmissionsCompanion.insert(
          id: 'another-member',
          userId: const Value('member-2'),
          parentId: const Value('survey-1@course-1'),
          type: const Value('survey'),
          status: const Value('pending'),
          startTime: const Value(900),
        ),
        SubmissionsCompanion.insert(
          id: 'the-bare-parent-id',
          userId: const Value('member-1'),
          // `parentId` is compared whole, so a sheet stored under the bare
          // survey id by a pre-Phase-125 build is a different key.
          parentId: const Value('survey-1'),
          type: const Value('survey'),
          status: const Value('pending'),
          startTime: const Value(900),
        ),
      ]);

      final id = await submissions.createSurveyDraft(
        survey: survey,
        questions: await questions(),
        userId: 'member-1',
        answers: {
          'survey-1:q1': const SubmissionDraftAnswer(value: 'Two hours'),
        },
      );

      expect(id, 'the-real-one');
    },
  );

  test('a resumed sheet keeps an answer the form did not carry', () async {
    final survey = await seedSurvey(questionCount: 2);
    // A `pending` sheet Planet itself authored, with question 1 already
    // answered. Kotlin's resume prefills the form from exactly these rows
    // (`populateCacheFromSavedAnswers`, `ExamTakingFragment.kt:157`), so the
    // submit that follows carries the stored answer forward.
    // `take_survey_screen` prefills only when the route named the submission,
    // and the surveys-list route does not — **and its Submit button has no
    // every-question-answered gate**, so a resume that simply overwrote would
    // move the data loss rather than remove it (Phase 143).
    await database.submissionDao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: 'from-planet',
          userId: const Value('member-1'),
          parentId: const Value('survey-1@course-1'),
          type: const Value('survey'),
          status: const Value('pending'),
        ),
      ],
      answers: {
        'from-planet': [
          SubmissionAnswersCompanion.insert(
            id: 'from-planet:q1',
            submissionId: 'from-planet',
            questionId: const Value('q1'),
            value: const Value('Answered last week'),
          ),
          // A stored answer for the question the form **does** fill. Without
          // it, "carried wins whenever there is one" and "carried wins only
          // when the draft is blank" are indistinguishable, because q2 would
          // have nothing to be carried from — the decoy that makes the
          // precedence half of this test a test.
          SubmissionAnswersCompanion.insert(
            id: 'from-planet:q2',
            submissionId: 'from-planet',
            questionId: const Value('q2'),
            value: const Value('A stale figure'),
          ),
        ],
      },
    );

    final id = await submissions.createSurveyDraft(
      survey: survey,
      questions: await questions(),
      userId: 'member-1',
      // Only q2 this time.
      answers: {
        'survey-1:q2': const SubmissionDraftAnswer(value: 'Ten litres'),
      },
    );

    expect(id, 'from-planet');
    final byQuestion = {
      for (final row in await database.submissionDao.answersFor(id))
        row.questionId: row.value,
    };
    expect(
      byQuestion['q1'],
      'Answered last week',
      reason: 'the stored answer survives a form that did not carry it',
    );
    expect(
      byQuestion['q2'],
      'Ten litres',
      reason: 'and an answer the learner did give still wins',
    );
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
