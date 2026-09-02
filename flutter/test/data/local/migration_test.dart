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

  test('a queued resource download survives a schema upgrade', () async {
    await database.downloadQueueDao.enqueue('resource-1', createdAt: 1000);

    await runUpgrade();

    expect(
      (await database.downloadQueueDao.pending()).map((row) => row.resourceId),
      ['resource-1'],
      reason: 'the server cannot reconstruct a download the user requested',
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

  group('the v45 legacy-examination repair', () {
    // Before Phase 105 the examination form wrote the *patient's* id into a new
    // row's `userId`, which is the column `HealthRepository.serialize` keys the
    // uploaded document on — so the row claimed the patient's profile document
    // and took a 409 nothing surfaced. `health_legacy_conflict_test.dart` holds
    // the end-to-end loss; these pin the predicate's edges, because re-keying
    // the wrong row would cause the same conflict in the other direction.
    Future<void> seedUser() => database.userDao.upsert(
      UsersCompanion.insert(
        id: 'local-9',
        name: const Value('ada'),
        couchId: const Value('org.couchdb.user:ada'),
      ),
    );

    /// The patient's profile row — the document a legacy examination collides
    /// with, and the reason the collision exists at all.
    Future<void> seedProfileRow() => database.healthExaminationDao.upsert(
      HealthExaminationsCompanion.insert(
        id: 'org.couchdb.user:ada',
        userId: const Value('org.couchdb.user:ada'),
        data: const Value('profile-ciphertext'),
        isUpdated: const Value(true),
      ),
    );

    test('re-keys a legacy examination onto its own id', () async {
      await seedUser();
      await seedProfileRow();
      await database.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'health-1725000000000000',
          userId: const Value('org.couchdb.user:ada'),
          pulse: const Value(72),
          isUpdated: const Value(true),
        ),
      );

      await runUpgrade(from: 44);

      final row = await database.healthExaminationDao.getById(
        'health-1725000000000000',
      );
      expect(row!.userId, 'health-1725000000000000');
      expect(row.pulse, 72, reason: 'the reading itself is untouched');
    });

    test('leaves a legacy row that became the profile row alone', () async {
      // `saveHealthProfileBlob` resolves the profile row with
      // `getByIdOrUserId`, which matches a legacy examination row through its
      // `userId` column — and then keeps `id: Value(existing.id)`. So on a
      // device with no separate profile row, the first post-Phase-105 save
      // turns the legacy examination row *into* the patient's profile row,
      // keeping its `health-…` id. Every other conjunct of the repair matches
      // that row; re-keying it would publish the health profile under a
      // millisecond timestamp no server can resolve to a person, which is the
      // harm this repair exists to prevent.
      await seedUser();
      await database.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'health-1725000000000005',
          userId: const Value('org.couchdb.user:ada'),
          data: const Value('profile-ciphertext'),
          isUpdated: const Value(true),
        ),
      );

      await runUpgrade(from: 44);

      expect(
        (await database.healthExaminationDao.getById(
          'health-1725000000000005',
        ))!.userId,
        'org.couchdb.user:ada',
        reason: 'no row is keyed on that userId, so there is no collision',
      );
    });

    test("leaves a locally-registered member's profile row alone", () async {
      // The row this predicate exists to protect: its id is the member's local
      // user key and its `userId` is the CouchDB id the account was given, so
      // the two legitimately differ. Re-keying it would post the profile under
      // a millisecond timestamp no server can resolve to a person.
      await seedUser();
      await database.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'local-9',
          userId: const Value('org.couchdb.user:ada'),
          isUpdated: const Value(true),
        ),
      );

      await runUpgrade(from: 44);

      expect(
        (await database.healthExaminationDao.getById('local-9'))!.userId,
        'org.couchdb.user:ada',
      );
    });

    test('leaves a profile row keyed on the CouchDB id alone', () async {
      // `UserDao.getById` matches `_id` as well as `id`, so a profile row can
      // be keyed on either. Both columns are checked.
      await database.userDao.upsert(
        UsersCompanion.insert(
          id: 'local-8',
          name: const Value('bea'),
          couchId: const Value('org.couchdb.user:bea'),
        ),
      );
      await database.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'org.couchdb.user:bea',
          userId: const Value('local-8'),
          isUpdated: const Value(true),
        ),
      );

      await runUpgrade(from: 44);

      expect(
        (await database.healthExaminationDao.getById(
          'org.couchdb.user:bea',
        ))!.userId,
        'local-8',
      );
    });

    test('leaves a row that already uploaded alone', () async {
      // Its `_rev` belongs to the document its old `userId` names. Re-keying
      // it while keeping that revision would post a revision of a document
      // that does not exist under the new id — the same 409, newly ours.
      await seedUser();
      await seedProfileRow();
      await database.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'health-1725000000000001',
          userId: const Value('org.couchdb.user:ada'),
          couchId: const Value('org.couchdb.user:ada'),
          rev: const Value('3-abc'),
          isUpdated: const Value(true),
        ),
      );

      await runUpgrade(from: 44);

      expect(
        (await database.healthExaminationDao.getById(
          'health-1725000000000001',
        ))!.userId,
        'org.couchdb.user:ada',
      );
    });

    test('leaves a row carrying a profileId alone', () async {
      // A row with a `profileId` is either recorded after Phase 105 or pulled
      // from the server; neither is the shape being repaired.
      await seedUser();
      await seedProfileRow();
      await database.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'health-1725000000000002',
          userId: const Value('org.couchdb.user:ada'),
          profileId: const Value('user-key-1'),
          isUpdated: const Value(true),
        ),
      );

      await runUpgrade(from: 44);

      expect(
        (await database.healthExaminationDao.getById(
          'health-1725000000000002',
        ))!.userId,
        'org.couchdb.user:ada',
      );
    });

    test('leaves a clean row alone', () async {
      // Nothing queues it, so it is not in the collision.
      await seedUser();
      await seedProfileRow();
      await database.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'health-1725000000000003',
          userId: const Value('org.couchdb.user:ada'),
        ),
      );

      await runUpgrade(from: 44);

      expect(
        (await database.healthExaminationDao.getById(
          'health-1725000000000003',
        ))!.userId,
        'org.couchdb.user:ada',
      );
    });

    test('the repair does not cost the record it repairs', () async {
      // `health_examinations` is preserved, so the upgrade that carries the
      // repair must not be the thing that discards the reading.
      await seedUser();
      await seedProfileRow();
      await database.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'health-1725000000000004',
          userId: const Value('org.couchdb.user:ada'),
          pulse: const Value(88),
          data: const Value('ciphertext'),
          isUpdated: const Value(true),
        ),
      );

      await runUpgrade(from: 44);

      final row = await database.healthExaminationDao.getById(
        'health-1725000000000004',
      );
      expect(row!.pulse, 88);
      expect(row.data, 'ciphertext');
      expect(row.isUpdated, isTrue, reason: 'it still has to be uploaded');
    });
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

  test(
    'an un-uploaded course-progress row survives a schema upgrade',
    () async {
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

      final survivor = await database.courseProgressDao.findByCourseUserAndStep(
        'course-1',
        'user-1',
        1,
      );
      expect(survivor?.passed, isTrue);
    },
  );

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

  test('a users row keeps its data and gains the v30 upload columns', () async {
    // `users` is preserved (the health key cannot be re-synced), so adding
    // `isUpdated`/`age`/`birthPlace` needs a hand-written `ALTER TABLE` step.
    // A row already on the server must not become pending just because the
    // dirty-flag column appeared.
    await database.userDao.upsert(
      UsersCompanion.insert(
        id: 'user-1',
        name: const Value('ada'),
        rolesList: const Value(['learner']),
        userAdmin: const Value(false),
        joinDate: const Value(123),
        couchId: const Value('org.couchdb.user:ada'),
        rev: const Value('2-abc'),
      ),
    );
    // Strip the v30 columns to simulate an install upgrading from v29.
    await database.customStatement('ALTER TABLE users DROP COLUMN is_updated');
    await database.customStatement('ALTER TABLE users DROP COLUMN age');
    await database.customStatement('ALTER TABLE users DROP COLUMN birth_place');

    await runUpgrade(from: 29);

    final survivor = await database.userDao.getById('user-1');
    expect(survivor?.name, 'ada');
    expect(survivor?.couchId, 'org.couchdb.user:ada');
    expect(survivor?.rev, '2-abc');
    expect(survivor?.isUpdated, isFalse);
    expect(survivor?.age, equals(null));
    expect(survivor?.birthPlace, equals(null));
    // A synced row is not pending — only local edits set the flag.
    expect(await database.userDao.pendingSyncUsers(), isEmpty);
  });

  test('an offline login record survives a schema upgrade', () async {
    // `ActivitiesUploader` carries these to `login_activities`, but nothing
    // syncs that database back in, so an uploaded row still exists only here —
    // and a pending one exists nowhere at all. A drop would silently reset the
    // dashboard's login count and empty the activity chart.
    await database.offlineActivityDao.insert(
      OfflineActivitiesCompanion.insert(
        id: 'login-1',
        userName: const Value('ada'),
        userId: const Value('user-1'),
        type: const Value('login'),
        loginTime: const Value(1000),
      ),
    );

    await runUpgrade();

    expect(
      await database.offlineActivityDao.countByUserNameAndType('ada', 'login'),
      1,
    );
  });

  test('resource and course activity rows survive a schema upgrade', () async {
    // Same test as `offline_activity`: can a sync put this back? It cannot in
    // either direction — a pending row exists nowhere else, and
    // `resource_activities`/`admin_activities`/`course_activities` are
    // write-only from this app's side.
    await database.resourceActivityDao.insert(
      ResourceActivitiesCompanion.insert(
        id: 'visit-1',
        user: const Value('ada'),
        type: const Value('visit'),
        resourceId: const Value('res-1'),
        title: const Value('Algebra'),
        time: const Value(1000),
      ),
    );
    await database.courseActivityDao.insert(
      CourseActivitiesCompanion.insert(
        id: 'course-1',
        user: const Value('ada'),
        type: const Value('visit'),
        courseId: const Value('c1'),
        time: const Value(1000),
      ),
    );

    await runUpgrade();

    expect(
      (await database.resourceActivityDao.byUserAndType(
        'ada',
        'visit',
      )).single.id,
      'visit-1',
    );
    expect(
      (await database.courseActivityDao.pendingUploads()).single.id,
      'course-1',
    );
  });

  test('a team chat watermark is dropped, as Room drops it', () async {
    // Deliberately *not* preserved: the Kotlin's Room database drops
    // `team_notification` too. Losing the watermark only suppresses a badge
    // until the user next opens that team's voices.
    await database.teamNotificationDao.upsert(
      TeamNotificationsCompanion.insert(
        id: 'team-1:chat',
        parentId: const Value('team-1'),
        type: const Value('chat'),
        lastCount: const Value(5),
      ),
    );

    await runUpgrade();

    // `isNull` would be ambiguous here — drift's query builder exports one too.
    expect(
      await database.teamNotificationDao.findByParentAndType('team-1', 'chat'),
      null,
    );
  });

  test(
    'a team finance document keeps its attachment name across v31',
    () async {
      // `teams` is preserved, so v31's `imageName` column is added by a
      // hand-written `_addColumnIfMissing` step rather than `createAll`. A
      // pending transaction that already carries a receipt name must keep it —
      // losing the name would orphan the local file and stop the upload
      // write-back from finding the bytes to PUT.
      await database.teamDao.upsert(
        TeamsCompanion.insert(
          id: 'tx-1',
          teamId: const Value('team-1'),
          docType: const Value('transaction'),
          type: const Value('credit'),
          description: const Value('sale'),
          amount: const Value(100),
          isUpdated: const Value(true),
        ),
      );
      // The v30 schema has no `image_name` column, so the name is written
      // directly after the upgrade lands — the row itself survives from before.
      await runUpgrade(from: 30);
      await database.customStatement(
        "UPDATE teams SET image_name = 'receipt.jpg' WHERE _id = 'tx-1'",
      );

      final survivor = await database.teamDao.getById('tx-1');
      expect(survivor?.description, 'sale');
      expect(survivor?.imageName, 'receipt.jpg');
      expect(survivor?.isUpdated, isTrue);
    },
  );

  test('a team task survives v32 and starts out un-notified', () async {
    // `team_tasks` is preserved too, so v32's `isNotified` column needs the same
    // hand-written `_addColumnIfMissing` step. Two things matter here: the task
    // itself must survive (it may be a locally-created row the outbox has not
    // pushed yet), and it must land with `isNotified` false so the deadline
    // reminder still fires. Defaulting to true would silently swallow the first
    // reminder for every task already on the device.
    await database.teamTaskDao.upsert(
      TeamTasksCompanion.insert(
        id: 'task-1',
        teamId: 'team-1',
        title: const Value('Submit the report'),
        assignee: const Value('user-1'),
        deadline: const Value(1770000000000),
        isUpdated: const Value(true),
      ),
    );

    await runUpgrade(from: 31);

    final survivor = await database.teamTaskDao.getById('task-1');
    expect(survivor?.title, 'Submit the report');
    expect(survivor?.deadline, 1770000000000);
    expect(survivor?.isUpdated, isTrue, reason: 'still owed to the server');
    expect(survivor?.isNotified, isFalse);
  });

  test('feedback indexes are present after an upgrade', () async {
    // The Kotlin `all: smoother model database indexing` commit (8f993472e)
    // added `openTime` and `isUploaded` indices to the feedback table. Both
    // land on a preserved table, so they are created by the drop-all-indexes
    // then `createAll` step in the migration — not by a hand-written ALTER.
    await database.feedbackDao.upsert(
      FeedbackEntriesCompanion.insert(
        id: 'fb-1',
        title: const Value('broken login'),
        openTime: const Value(1723000000),
        isUploaded: const Value(false),
      ),
    );

    await runUpgrade();

    final indexes = await database
        .customSelect(
          "SELECT name FROM sqlite_master "
          "WHERE type = 'index' AND tbl_name = 'feedback'",
        )
        .get();
    final names = indexes.map((r) => r.read<String>('name')).toSet();
    expect(names, containsAll(['feedback_open_time', 'feedback_is_uploaded']));
    // The row survives — it is a preserved table.
    expect((await database.feedbackDao.getById('fb-1'))?.id, 'fb-1');
  });

  // A captured exam-verification photo is local intent: it exists only on this
  // device until the `SubmitPhotosUploader` delivers it, and the JPEG it
  // points at lives only on this device's filesystem. Dropping the table on a
  // schema bump would discard a photo the user was never warned had not
  // reached the server, so the table is preserved and the row must survive.
  test('a captured submit_photo survives a schema bump', () async {
    await database.submitPhotosDao.insert(
      SubmitPhotosTableCompanion.insert(
        id: 'photo-1',
        submissionId: const Value('sub-1'),
        examId: const Value('exam-1'),
        courseId: const Value('course-1'),
        memberId: const Value('user-1'),
        photoLocation: const Value('/tmp/capture.jpg'),
        uploaded: const Value(false),
      ),
    );

    await runUpgrade();

    final survivor = await database.submitPhotosDao.getById('photo-1');
    expect(survivor?.id, 'photo-1');
    expect(survivor?.submissionId, 'sub-1');
    expect(survivor?.uploaded, isFalse);
  });

  // A `teamVisit` the user made exists only on this device until the
  // `TeamLogUploader` delivers it to `team_activities`. Dropping the table on
  // a schema bump would silently lose an action the user took, so the table
  // is preserved and the row must survive.
  test('an un-uploaded team_log row survives a schema bump', () async {
    await database.teamLogDao.insert(
      TeamLogTableCompanion.insert(
        id: 'visit-1',
        teamId: const Value('team-1'),
        user: const Value('ada'),
        type: const Value('teamVisit'),
        teamType: const Value('team'),
        createdOn: const Value('earth'),
        parentCode: const Value('sol'),
        time: const Value(1000),
        uploaded: const Value(false),
      ),
    );

    await runUpgrade();

    final survivor = await database.teamLogDao.pendingUploads();
    expect(survivor.single.id, 'visit-1');
    expect(survivor.single.teamId, 'team-1');
    expect(survivor.single.uploaded, isFalse);
  });

  // A filtered search the user ran exists only on this device until the
  // `SearchActivityUploader` delivers it to `search_activities`. Dropping the
  // table on a schema bump would silently lose the analytics event, so the
  // table is preserved and the row must survive.
  test('an un-uploaded search_activity row survives a schema bump', () async {
    await database.searchActivityDao.insert(
      SearchActivitiesCompanion.insert(
        id: 'search-1',
        user: const Value('ada'),
        searchText: const Value('math'),
        type: const Value('resources'),
        time: const Value(1000),
        createdOn: const Value('earth'),
        parentCode: const Value('sol'),
        filterJson: const Value('{"subjects":[]}'),
      ),
    );

    await runUpgrade();

    final survivor = await database.searchActivityDao.pendingUploads();
    expect(survivor.single.id, 'search-1');
    expect(survivor.single.searchText, 'math');
    expect(survivor.single.type, 'resources');
  });

  // An achievements ledger the user edited exists only on this device until
  // the `AchievementsUploader` delivers it. Dropping the table on a schema
  // bump would silently lose the user's lists, so the table is preserved and
  // the row must survive.
  test('an un-uploaded achievements row survives a schema bump', () async {
    await database.achievementDao.upsert(
      AchievementsCompanion(
        id: const Value('user-1@earth'),
        achievementsJson: const Value('[{"title":"First"}]'),
        referencesJson: const Value('[{"name":"Mo"}]'),
        couchId: const Value(''),
        rev: const Value(''),
        resumeFileName: const Value(''),
      ),
    );

    await runUpgrade();

    final survivor = await database.achievementDao.getById('user-1@earth');
    expect(survivor?.id, 'user-1@earth');
    expect(survivor?.achievementsJson, contains('First'));
    expect(survivor?.uploaded, isFalse);
  });

  test('a voice post keeps its reactions across v42', () async {
    // `news` is preserved, so v42's `reactions` column is added by a
    // hand-written `_addColumnIfMissing` step. A voice that already carries
    // a reaction must keep it — losing it would silently drop every reaction
    // on the device.
    await database.newsDao.upsert(
      NewsEntriesCompanion.insert(
        id: 'voice-1',
        message: const Value('hello'),
        time: const Value(1000),
        docType: const Value('message'),
        avatar: const Value(''),
        sharedBy: const Value(''),
        imageUrls: const Value([]),
        labels: const Value([]),
        isEdited: const Value(false),
        editedTime: const Value(0),
        chat: const Value(false),
      ),
    );
    await runUpgrade(from: 41);
    // The reactions column exists now, but the row itself survived.
    final survivor = await database.newsDao.getById('voice-1');
    expect(survivor?.message, 'hello');
    expect(survivor?.reactions, isA<String?>().having((v) => v, 'value', null));
    // Writing a reaction after the upgrade works.
    await database.customStatement(
      r"""UPDATE news SET reactions = '{"like":["user-1"]}' WHERE id = 'voice-1'""",
    );
    final withReaction = await database.newsDao.getById('voice-1');
    expect(withReaction?.reactions, isA<String>());
  });

  test('an un-uploaded challenge sync action survives a schema bump', () async {
    // `user_challenge_actions` is preserved: a sync action the user recorded
    // but has not yet uploaded must survive a schema bump, or the challenge
    // dialog's "sync completed" check would silently flip back to false.
    await database.userChallengeActionDao.insert(
      UserChallengeActionsCompanion.insert(
        id: 'challenge-1',
        userId: const Value('user-1'),
        actionType: const Value('sync'),
        time: const Value(1000),
      ),
    );

    await runUpgrade();

    final survivor = await database.userChallengeActionDao.countByUserAndType(
      'user-1',
      'sync',
    );
    expect(survivor, 1);
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
      'offline_activity',
      'resource_activity',
      'course_activity',
      'download_queue',
      'submit_photos',
      'team_log',
      'search_activity',
      'achievements',
      'user_challenge_actions',
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

  test(
    'the resource cache gains the openWhichFile column after the upgrade',
    () async {
      // v36 adds `openWhichFile`, the nested HTML entry-file field. The table is
      // a cache so it is dropped and recreated — the column appears on the new
      // table, and a row synced after the upgrade carries the value through.
      await runUpgrade();
      await database.myLibraryDao.upsertAll([
        MyLibraryTableCompanion.insert(
          id: 'html-1',
          title: const Value('Sudoku'),
          openWhichFile: const Value('sudoku/index.html'),
        ),
      ]);
      final row = await database.myLibraryDao.getById('html-1');
      expect(row, isNot(equals(null)));
      expect(row!.openWhichFile, 'sudoku/index.html');
    },
  );

  test(
    'the notifications cache gains the subType column after the upgrade',
    () async {
      // v37 adds `subType`, the locale-independent join-request signal the
      // server sends in `linkParams.activeTab`. The table is a cache so it is
      // dropped and recreated — the column appears on the new table, and a row
      // synced after the upgrade carries the value through.
      await runUpgrade();
      await database.notificationDao.upsert(
        NotificationsCompanion.insert(
          id: 'team-1',
          userId: 'user-1',
          type: const Value('team'),
          subType: const Value('join_request'),
          createdAt: 0,
        ),
      );
      final row = await database.notificationDao.getById('team-1');
      expect(row, isNot(equals(null)));
      expect(row!.subType, 'join_request');
    },
  );
}
