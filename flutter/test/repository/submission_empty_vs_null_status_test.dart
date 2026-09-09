import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/submissions_repository.dart';

/// `""` and null are not the same thing, and the difference reached a learner.
///
/// Kotlin's sync-in stores `JsonUtils.getString("status", submission)`
/// (`SubmissionsRepositoryImpl.kt:659`, `:679`) and Kotlin's `getString`
/// returns `""` for a key the document does not carry (`JsonUtils.kt:65-68`).
/// So a Planet submission with no `status` is stored as `""`,
/// `SubmissionDao.countCompletedByUserAndExamId`'s `status != 'pending'`
/// (`SubmissionDao.kt:24`) is satisfied, the count is 1, and
/// `isStepCompleted` releases the course step.
///
/// The port stored `getStringOrNull`, which is null for a missing key **and**
/// for `""` (`json_utils.dart`, `getStringOrNull`). `NOT (status = 'pending')`
/// is SQL NULL
/// for a NULL status and `WHERE NULL` drops the row, so the count was 0 and
/// **the step stayed locked for a learner Kotlin lets through.**
///
/// The reader was never the defect: it is a faithful port of Kotlin's query,
/// NULL behaviour included. Only the writer diverged, which is why the fix is
/// on the writer and no DAO predicate changes.
///
/// The `type` column carries the same writer-side divergence and is
/// deliberately left alone; the group below pins why that is currently
/// invisible and what a follow-up needs.
///
/// **Why the existing coverage could not see this.** `step_next_lock_test`'s
/// type-less document sets `'status': 'complete'`, so it exercises the `type`
/// axis and holds the status axis fixed at a value that passes either way; and
/// every other sync-in test hands `upsertDocuments` a document that carries a
/// status. A test for this axis has to omit the key, the way the server does.
class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late SubmissionsRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = SubmissionsRepository(
      MockPlanetApi(),
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
      database.examDao,
      teamDao: database.teamDao,
    );
  });
  tearDown(() => database.close());

  /// A submission document shaped the way Planet sends one: the owner in the
  /// nested `user` object with no top-level `userId`, a `_rev` because it came
  /// back from CouchDB, and a `parentId` minted the way the app mints it.
  ///
  /// Whatever [extra] carries is merged last, so a test can add a key or —
  /// which is the whole point here — leave one out.
  Map<String, dynamic> planetDocument({
    String id = 'sub-1',
    Map<String, dynamic> extra = const {},
  }) => {
    '_id': id,
    '_rev': '1-abc',
    'parentId': SubmissionsRepository.examParentId(
      examId: 'exam-1',
      courseId: 'course-1',
    ),
    'user': {'_id': 'user-1', 'name': 'ada'},
    'startTime': 1700000000000,
    'lastUpdateTime': 1700000001000,
    'grade': 0,
    'answers': const <Map<String, dynamic>>[],
    'parent': const {'_id': 'exam-1', 'name': 'Week 1 quiz'},
    ...extra,
  };

  group('a Planet submission that omits `status`', () {
    test('releases the course step, as Kotlin does', () async {
      await repository.upsertDocuments([planetDocument()]);

      // The reader is Kotlin's own query, unchanged by this phase. What
      // changed is what the writer put in the column for it to read.
      expect(
        await database.submissionDao.countCompletedByUserAndExamId(
          'user-1',
          'exam-1',
        ),
        1,
        reason:
            'Kotlin stores "" and counts the row; a NULL status is dropped by '
            'NOT (status = \'pending\') and locks the step',
      );
    });

    test('is stored as the empty string, not null', () async {
      await repository.upsertDocuments([planetDocument()]);

      expect((await database.submissionDao.getById('sub-1'))?.status, '');
    });

    test('an explicit empty `status` is stored the same way', () async {
      // Kotlin's `getString` cannot tell these two documents apart, so the
      // port must not either: `""` and a missing key both arrive as `""`.
      await repository.upsertDocuments([
        planetDocument(extra: const {'status': ''}),
      ]);

      expect((await database.submissionDao.getById('sub-1'))?.status, '');
      expect(
        await database.submissionDao.countCompletedByUserAndExamId(
          'user-1',
          'exam-1',
        ),
        1,
      );
    });

    test('a `pending` status still locks the step', () async {
      // The other side of the predicate, so the fix cannot be mistaken for
      // "count everything".
      await repository.upsertDocuments([
        planetDocument(extra: const {'status': 'pending'}),
      ]);

      expect(
        await database.submissionDao.countCompletedByUserAndExamId(
          'user-1',
          'exam-1',
        ),
        0,
      );
    });
  });

  group('a Planet submission that omits `type`', () {
    // **The `type` half of the inherited report is real in the column and,
    // today, invisible at every reader — so it is deliberately not changed
    // here, and these tests pin that decision rather than the divergence.**
    //
    // Every port predicate on `submissions.type` is a *positive* equality
    // (`type.equals('exam')`, `.equals('survey')`, `countByUserParentAndType`),
    // and `'' = 'exam'` and `NULL = 'exam'` both fail to select, so switching
    // the writer would buy no behavioural parity at all. What it would buy is
    // two blank labels: `submission_detail_screen.dart:80` falls back
    // `submissionDisplayTitle(row) ?? row.type ?? l10n.submission` and `:91`
    // renders `row.type ?? '—'`, and neither fallback catches `''`. That file
    // is on no lane's set this round, and half a pair across two lanes is
    // worse than a clean hand-over — see `PHASE_151_NOTES.md`
    // § *Reported, not fixed*.
    test('is still stored as null', () async {
      await repository.upsertDocuments([planetDocument()]);

      expect(
        (await database.submissionDao.getById('sub-1'))?.type,
        isNull,
        reason:
            'deferred deliberately; Kotlin stores "" here '
            '(SubmissionsRepositoryImpl.kt:673)',
      );
    });

    test('is matched by no positive `type` predicate either way', () async {
      // The invariant that makes the deferral safe.
      //
      // **A pin, not a tripwire, and an earlier revision of this comment
      // claimed the latter.** It holds under either writer and under a new
      // negated predicate elsewhere, so no plausible mutation fails it — of
      // this file's six controls it is the one nothing reaches. What it buys
      // is the deferral's reason checked against the code rather than
      // asserted in prose; the positive control below is what stops it being
      // a claim about a query that returns nothing.
      await repository.upsertDocuments([planetDocument()]);

      expect(
        await database.submissionDao.getExamSubmissionsByUser('user-1'),
        isEmpty,
      );
      expect(
        await database.submissionDao.getSurveySubmissionsByUser('user-1'),
        isEmpty,
      );
      expect(
        await database.submissionDao.countByUserParentAndType(
          'user-1',
          SubmissionsRepository.examParentId(
            examId: 'exam-1',
            courseId: 'course-1',
          ),
          'survey',
        ),
        0,
      );

      // The positive control, so the three `isEmpty`/`0` assertions above are
      // evidence about the type-less row rather than about a query that never
      // returns anything.
      await repository.upsertDocuments([
        planetDocument(id: 'sub-typed', extra: const {'type': 'exam'}),
      ]);
      expect(
        (await database.submissionDao.getExamSubmissionsByUser(
          'user-1',
        )).map((row) => row.id),
        ['sub-typed'],
      );
    });
  });

  group('re-serializing a pulled row', () {
    test('sends the empty status and type Kotlin sends', () async {
      // `serializeSubmission`'s `submission.type ?: "survey"` (`:835`) and
      // `submission.status ?: "pending"`
      // (`SubmissionsRepositoryImpl.kt:840`) do **not** catch `""` — Kotlin's
      // Elvis fires on null only. So
      // a status-less document Kotlin pulled and re-sent carries `""` back,
      // where the port used to substitute `pending`/`survey` and tell Planet
      // something the document never said.
      await repository.upsertDocuments([planetDocument()]);
      final row = (await database.submissionDao.getById('sub-1'))!;

      final payload = await repository.serialize(row);

      expect(payload['status'], '');
      // The deferred half, pinned as it stands: `type` is still null in the
      // column, so the port's own `?? 'survey'` fires and tells Planet
      // `survey` where Kotlin echoes the `""` the document arrived with. A
      // narrow divergence — a synced row only re-uploads once a local edit
      // sets `isUpdated` — and it moves the day the writer does.
      expect(payload['type'], 'survey');
    });

    test('still defaults a genuinely null status, as Kotlin does', () async {
      // A row with no status at all is not something the sync-in can now
      // produce, and **no local writer can either** — every one of them
      // (`createDraft`, `createSurveyDraft`, `updateSurveyAnswers`,
      // `saveExamAnswer`, `createSurveyAdoptionSubmission`, `markComplete`)
      // writes an explicit status. Only a row a pre-Phase-151 build stored is
      // in this state, on a preserved table, and Kotlin's `?:` covers exactly
      // it. Built as a companion here for that reason.
      await database.submissionDao.upsertAll([
        SubmissionsCompanion.insert(
          id: 'local-1',
          userId: const Value('user-1'),
          isUpdated: const Value(true),
        ),
      ]);
      final row = (await database.submissionDao.getById('local-1'))!;

      final payload = await repository.serialize(row);

      expect(payload['status'], 'pending');
      expect(payload['type'], 'survey');
    });
  });

  group('the team-survey adoption marker survives the writer change', () {
    test(
      'a round-tripped marker reads back as `""` and is not re-queued',
      () async {
        // `pendingUploads`' `isATeamAdoptionMarker` is deliberately
        // `status IS NOT NULL AND status = ''` (`app_database.dart`), written
        // strict on the argument that a *round-tripped* marker reads back as
        // NULL. After this phase it reads back as `''` instead, so the marker
        // test now matches a pulled row — and must still not select it, because
        // `upsertDocuments` hard-codes `isUpdated: false` and the sweep wants
        // `isUpdated = true`. That is the load-bearing step in the argument for
        // fixing the writer rather than the readers, so it is pinned rather
        // than reasoned about.
        await repository.upsertDocuments([
          planetDocument(id: 'marker-1', extra: const {'status': ''}),
        ]);
        final marker = (await database.submissionDao.getById('marker-1'))!;

        expect(marker.status, '');
        expect(marker.isUpdated, isFalse);
        expect(
          (await database.submissionDao.pendingUploads()).map((row) => row.id),
          isNot(contains('marker-1')),
        );
      },
    );

    test('a local marker awaiting upload is still excluded', () async {
      // The row the strict marker test exists for: authored locally by
      // `createSurveyAdoptionSubmission` with the literal empty string and
      // `isUpdated: true`.
      await database.submissionDao.upsertAll([
        SubmissionsCompanion.insert(
          id: 'marker-local',
          userId: const Value('user-1'),
          status: const Value(''),
          isUpdated: const Value(true),
        ),
      ]);

      expect(
        (await database.submissionDao.pendingUploads()).map((row) => row.id),
        isNot(contains('marker-local')),
      );
    });

    test('a status-less local row is still swept for upload', () async {
      // The other half of that predicate's strictness, and the reason it was
      // not written as a `coalesce`: a row with a genuinely NULL status is not
      // a marker and must not be stranded.
      await database.submissionDao.upsertAll([
        SubmissionsCompanion.insert(
          id: 'nullish',
          userId: const Value('user-1'),
          isUpdated: const Value(true),
        ),
      ]);

      expect(
        (await database.submissionDao.pendingUploads()).map((row) => row.id),
        contains('nullish'),
      );
    });
  });
}
