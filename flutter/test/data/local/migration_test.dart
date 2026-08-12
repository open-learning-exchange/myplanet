import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// The upgrade path drops and recreates the CouchDB caches so the next sync
/// refills them. These tests pin the exception to that rule: a table holding
/// work the user did offline must survive, because no sync can give it back.
void main() {
  late NativeDatabase executor;
  late AppDatabase database;

  setUp(() {
    // One executor shared across both "runs" so the file-level state persists,
    // the way an on-device upgrade would see it.
    executor = NativeDatabase.memory();
    database = AppDatabase(executor);
  });
  tearDown(() => database.close());

  Future<void> runUpgrade({int? from}) =>
      database.customStatement('SELECT 1').then((_) async {
        final migrator = database.createMigrator();
        await database.migration.onUpgrade(
          migrator,
          from ?? database.schemaVersion - 1,
          database.schemaVersion,
        );
      });

  test('an un-pushed outbox operation survives a schema upgrade', () async {
    await database.outboxDao.upsert(
      OutboxEntriesCompanion.insert(
        id: 'op-1',
        uploadType: 'personals',
        itemId: 'note-1',
        payload: '{"title":"offline note"}',
        endpoint: 'https://planet.example.org/db/resources',
        createdAt: 1000,
      ),
    );

    await runUpgrade();

    final survivors = await database.outboxDao.due(9999);
    expect(
      survivors.map((row) => row.id),
      ['op-1'],
      reason: 'dropping the queue would discard a write the user already made',
    );
  });

  test('an un-uploaded personal note survives a schema upgrade', () async {
    await database.personalDao.upsert(
      PersonalEntriesCompanion.insert(
        id: 'note-1',
        title: 'Private',
        titleNormalized: 'private',
        date: 1000,
        userId: 'user-1',
      ),
    );

    await runUpgrade();

    expect((await database.personalDao.getById('note-1'))?.title, 'Private');
  });

  test('a removed-log entry survives, so a leave is not re-added', () async {
    await database.removedLogDao.record(
      type: 'courses',
      docId: 'course-1',
      userId: 'user-1',
    );

    await runUpgrade();

    expect(
      await database.removedLogDao.removedDocIds('courses', 'user-1'),
      contains('course-1'),
    );
  });

  test('a meetup that has not reached the server survives', () async {
    // `EventsRepository.create` writes these before any upload, so a drop
    // would discard a meetup that exists nowhere else.
    await database.meetupDao.upsert(
      MeetupsCompanion.insert(
        id: 'local-1',
        title: const Value('Offline meetup'),
        updated: const Value(true),
      ),
    );

    await runUpgrade();

    final survivors = await database.meetupDao.pendingUploads();
    expect(survivors.map((row) => row.id), contains('local-1'));
  });

  test('a voices post that has not reached the server survives', () async {
    await database.newsDao.upsert(
      NewsEntriesCompanion.insert(
        id: 'local-1',
        message: const Value('Written offline'),
        userId: const Value('user-1'),
      ),
    );

    await runUpgrade();

    final survivor = await database.newsDao.getById('local-1');
    expect(survivor?.message, 'Written offline');
  });

  test('an offline team task survives a schema upgrade', () async {
    await database.teamTaskDao.upsert(
      TeamTasksCompanion.insert(
        id: 'task-1',
        teamId: 'team-1',
        title: const Value('Offline task'),
        isUpdated: const Value(true),
      ),
    );
    await runUpgrade();
    expect(
      (await database.teamTaskDao.getById('task-1'))?.title,
      'Offline task',
    );
  });

  test('a team document authored offline survives a schema upgrade', () async {
    // The `teams` table is a hybrid: dropping it takes the CouchDB catalog,
    // which the next sync refills, but also the report/request/membership rows
    // that only exist here. The stale cache rows left behind are pruned by
    // `deleteNotIn` on that same sync.
    await database.teamDao.upsert(
      TeamsCompanion.insert(
        id: 'report-1',
        teamId: const Value('team-1'),
        docType: const Value('report'),
        description: const Value('Q1 offline'),
        isUpdated: const Value(true),
      ),
    );

    await runUpgrade();

    expect(
      (await database.teamDao.getById('report-1'))?.description,
      'Q1 offline',
    );
  });

  test('a My life ordering choice survives', () async {
    await database.myLifeDao.seedIfEmpty('user-1', [
      MyLifeEntriesCompanion.insert(
        id: 'user-1:health',
        feature: 'health',
        userId: 'user-1',
        weight: 3,
        isVisible: const Value(false),
      ),
    ]);

    await runUpgrade();

    final rows = await database.myLifeDao.watchForUser('user-1').first;
    expect(rows.single.weight, 3);
    expect(rows.single.isVisible, isFalse);
  });

  test('an un-uploaded submission survives with its answers', () async {
    await database.submissionDao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: 'sub-1',
          userId: const Value('user-1'),
          type: const Value('survey'),
          status: const Value('complete'),
          uploaded: const Value(false),
        ),
      ],
      questions: {
        'sub-1': [
          SubmissionQuestionsCompanion.insert(
            id: 'sub-1:q1',
            submissionId: 'sub-1',
            header: const Value('H'),
            position: 0,
          ),
        ],
      },
      answers: {
        'sub-1': [
          SubmissionAnswersCompanion.insert(
            id: 'sub-1:q1',
            submissionId: 'sub-1',
            questionId: const Value('q1'),
            value: const Value('Yes'),
          ),
        ],
      },
    );

    await runUpgrade();

    // All three tables are preserved together: a submission whose answers were
    // dropped would upload as an empty response, which is worse than losing it.
    expect(await database.submissionDao.getById('sub-1'), isA<SubmissionRow>());
    expect(await database.submissionDao.answersFor('sub-1'), hasLength(1));
    expect(
      (await database.submissionDao.watchQuestions('sub-1').first),
      hasLength(1),
    );
  });

  test('a chat conversation survives a schema upgrade', () async {
    // There is no chat sync, so a drop here is permanent even though CouchDB
    // still holds the document.
    await database.chatDao.upsertAll([
      ChatEntriesCompanion.insert(
        id: 'chat-1',
        docId: const Value('chat-1'),
        user: const Value('ada'),
        title: const Value('Capital of Iceland?'),
      ),
    ]);

    await runUpgrade();

    expect(
      (await database.chatDao.getByUser('ada')).single.title,
      'Capital of Iceland?',
    );
  });

  test('feedback filed offline survives a schema upgrade', () async {
    await database.feedbackDao.upsert(
      FeedbackEntriesCompanion.insert(
        id: 'feedback-1',
        title: const Value('App crashes on sync'),
        owner: const Value('ada'),
        isUploaded: const Value(false),
      ),
    );

    await runUpgrade();

    expect(
      (await database.feedbackDao.getById('feedback-1'))?.title,
      'App crashes on sync',
    );
  });

  test('a health examination survives a schema upgrade', () async {
    // A clinician's reading, recorded offline. No health sync had a caller
    // until this slice, and even with one the record is authored here — the
    // server has nothing to give back.
    await database.healthExaminationDao.upsert(
      HealthExaminationsCompanion.insert(
        id: 'exam-1',
        userId: const Value('user-1'),
        pulse: const Value(72),
      ),
    );

    await runUpgrade();

    expect((await database.healthExaminationDao.getById('exam-1'))?.pulse, 72);
  });

  test('the health encryption key survives a schema upgrade', () async {
    // `key`/`iv` are generated on this device and never uploaded. Dropping
    // them would leave every health record already encrypted with them
    // unreadable — and would sign the user out, since the session restores by
    // looking their id up in this table.
    await database.userDao.upsert(
      UsersCompanion.insert(id: 'user-1', name: const Value('ada')),
    );
    final before = await database.userDao.ensureSecurityKeys('user-1');

    await runUpgrade();

    final after = await database.userDao.getById('user-1');
    expect(after?.key, before?.key);
    expect(after?.iv, before?.iv);
  });

  test(
    'a column added to a preserved table reaches an existing install',
    () async {
      // `createAll` emits `CREATE TABLE IF NOT EXISTS`, and a preserved table is
      // skipped by the drop loop — so a new column on one only ever exists on
      // installs created after the change unless `onUpgrade` adds it by hand.
      // `chat_history.is_uploaded` arrived without that step; every chat query
      // then failed on the missing column.
      await database.customStatement(
        'ALTER TABLE chat_history DROP COLUMN is_uploaded',
      );

      await runUpgrade(from: 24);

      final columns = await database
          .customSelect('PRAGMA table_info(chat_history)')
          .get();
      expect(
        columns.map((row) => row.read<String>('name')),
        contains('is_uploaded'),
      );
    },
  );

  test('an un-uploaded course-progress row survives a schema upgrade', () async {
    // The step-view path writes a `passed=false` row the moment a step is
    // opened, and the exam flips it to `true`. Dropping the table between
    // the two would discard the pass — the server has nothing to give back
    // for a row the exam just authored.
    await database.courseProgressDao.upsert(
      CourseProgressCompanion.insert(
        id: 'progress-1',
        courseId: const Value('course-1'),
        userId: const Value('user-1'),
        stepNum: const Value(1),
        passed: const Value(true),
      ),
    );

    await runUpgrade();

    final survivor = await database.courseProgressDao
        .findByCourseUserAndStep('course-1', 'user-1', 1);
    expect(survivor?.passed, isTrue);
  });

  test('an already-uploaded chat is not re-queued after the upgrade', () async {
    // A chat carries a `_rev` only once the server acknowledged it. Leaving
    // those at the column default would mark every synced conversation pending
    // and post a duplicate of each on the next drain.
    await database.chatDao.upsertAll([
      ChatEntriesCompanion.insert(
        id: 'chat-1',
        docId: const Value('chat-1'),
        rev: const Value('1-a'),
        user: const Value('ada'),
      ),
    ]);
    await database.customStatement(
      'ALTER TABLE chat_history DROP COLUMN is_uploaded',
    );

    await runUpgrade(from: 24);

    expect(await database.chatDao.getPending(), isEmpty);
  });

  test('every preserved table has a preservation test', () {
    // `my_life` and the submissions tables were added to the preserved set
    // without one. This fails the moment another name is added, so the next
    // slice cannot repeat that quietly.
    const covered = {
      'outbox',
      'my_personal',
      'removed_log',
      'my_life',
      'submissions',
      'submission_answers',
      'submission_questions',
      'meetups',
      'news',
      'team_tasks',
      'teams',
      'chat_history',
      'feedback',
      'health_examinations',
      'users',
      'course_progress',
    };
    expect(
      AppDatabase.localAuthorityTables,
      covered,
      reason: 'add a preservation test above, then list the table here',
    );
  });

  test('a survey cache is dropped, carrying no local writes', () async {
    await database.surveyDao.upsertAll([
      SurveysCompanion.insert(id: 'survey-1', name: const Value('Survey')),
    ], const {});
    // `isNull`/`isNotNull` are ambiguous here — drift exports its own.
    expect(await database.surveyDao.getById('survey-1'), isA<SurveyRow>());

    await runUpgrade();

    expect(await database.surveyDao.getById('survey-1'), equals(null));
  });

  test(
    'a CouchDB cache is still dropped and refilled by the next sync',
    () async {
      // `users` used to be the example here. It is preserved now — it carries
      // the locally-generated health key — so the rule needs a table that is
      // genuinely nothing but a cache.
      await database.myLibraryDao.upsertAll([
        MyLibraryTableCompanion.insert(
          id: 'resource-1',
          title: const Value('Algebra'),
        ),
      ]);
      expect(
        (await database.myLibraryDao.getById('resource-1'))?.id,
        'resource-1',
      );

      await runUpgrade();

      expect(
        await database.myLibraryDao.getById('resource-1'),
        equals(null),
        reason:
            'the resource cache is refilled by the next sync, so the '
            'drop-and-resync policy still applies',
      );
    },
  );
}
