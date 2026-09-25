import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// Divergences Phase 159 Lane 3's query differential found in
/// `lib/data/local/app_database.dart`, which **Lane 1 owns this round**.
///
/// Each test below asserts the port's *current, diverging* behaviour and says
/// in its failure message what to change and what should hold afterwards. That
/// is deliberate and it is the Phase 157 mechanism: an entry that only
/// suppresses is a silent debt, while one that **fails in both directions** is
/// a scheduled reminder — make the fix and the tripwire goes red, naming
/// itself as the thing to delete.
///
/// And the Phase 158 correction to that mechanism, which cost two behaviours
/// their only pin: *an exemption is not retired when its entry is deleted, it
/// is retired when something else holds its subject.* So each message names
/// the durable test that must exist before the tripwire is removed. Deleting a
/// group here without writing that test leaves the fix pinned by nothing.
void main() {
  late AppDatabase database;

  setUp(() => database = AppDatabase.memory());
  tearDown(() => database.close());

  group('SubmissionDao.markComplete writes a timestamp Kotlin does not', () {
    /// Kotlin `SubmissionDao.kt:35`:
    ///
    /// ```sql
    /// UPDATE submissions SET user = :userJson, status = 'complete',
    ///   isUpdated = 1 WHERE id = :id
    /// ```
    ///
    /// The port's `markComplete` (`app_database.dart`) also writes
    /// `lastUpdateTime: DateTime.now()` and `uploaded: false`.
    ///
    /// `lastUpdateTime` is the real one. Both apps sort the submissions list
    /// by it and both upload it, so an answer sheet finished at 10:00 and
    /// given its respondent profile at 10:05 is stamped 10:00 in Planet by the
    /// Android app and 10:05 by the port, and jumps to the top of a list where
    /// Kotlin's does not. In Kotlin that column additionally drives
    /// `PublicSurveyActivity.kt:123`'s `lastUpdateTime < launchTime`
    /// staleness filter.
    ///
    /// `uploaded: false` is cosmetic and arguably the more truthful label —
    /// the sheet really does owe the server another send — but it is
    /// undocumented, and the doc comment at `markComplete` attributes the
    /// re-arming to it, which is wrong: **neither** app's pending-upload
    /// predicate reads that column (`getPendingSubmissions` is
    /// `status = 'complete' AND (isUpdated = 1 OR …)`, `pendingUploads` is
    /// `isUpdated = true`), so `isUpdated` alone re-arms the row.
    test('`lastUpdateTime` is restamped', () async {
      await database.submissionDao.upsertAll([
        SubmissionsCompanion.insert(
          id: 'sheet',
          userId: const Value('member-1'),
          status: const Value('requires grading'),
          lastUpdateTime: const Value(1000),
          uploaded: const Value(true),
        ),
      ]);

      await database.submissionDao.markComplete('sheet', '{"name":"Ada"}');

      final row = (await database.submissionDao.getById('sheet'))!;
      expect(row.status, 'complete');
      expect(row.isUpdated, isTrue);
      expect(
        row.lastUpdateTime,
        isNot(1000),
        reason:
            'TRIPWIRE — delete this group when the divergence is closed.\n'
            'Kotlin\'s `markComplete` (`SubmissionDao.kt:35`) writes only '
            '`user`, `status` and `isUpdated`; the port also restamps '
            '`lastUpdateTime`, so the same answer sheet reaches Planet with a '
            'different timestamp on the two apps and sorts differently in '
            'both submission lists.\n'
            'To close it: drop `lastUpdateTime` from `SubmissionDao'
            '.markComplete` in `lib/data/local/app_database.dart` (Lane 1 owns '
            'that file this round), and correct the doc comment there, which '
            'says `uploaded: false` is what puts the row back in '
            '`pendingUploads` — it is not, `isUpdated` is.\n'
            'BEFORE DELETING THIS GROUP, write the durable test that replaces '
            'it: `markComplete` leaves a seeded `lastUpdateTime` untouched. '
            'Without it the fix is pinned by nothing and can be reverted with '
            'the suite still green (Phase 158).',
      );
    });
  });

  group('SubmissionDao.getExamSubmissionsByUser coerces a null userId', () {
    /// Kotlin `SubmissionDao.kt:15` is `WHERE userId IS :userId AND
    /// type = 'exam'` with a `String?` parameter, so a null argument selects
    /// the rows whose `userId` **is NULL**. The port writes
    /// `row.userId.equals(userId ?? '')`, which selects rows whose `userId` is
    /// the empty string — a set that is always empty, because
    /// `upsertDocuments` stores null (via `normalizeSubmissionUserId`) for a
    /// document whose `user._id` is blank.
    ///
    /// **Unreachable today**, which is why this is a tripwire and not a fix:
    /// the three port callers resolve a session id first
    /// (`courses_providers.dart` short-circuits on an empty course list before
    /// the query, `progress_repository.dart` and `_deleteExamSubmissions` both
    /// receive a resolved id), and the router does not serve My Progress
    /// signed out. But it is a silent lossy coercion of Kotlin's `IS`, and the
    /// sibling port in the same class — `countCompletedByUserAndExamId` — does
    /// it correctly with `isNull()`, so the two disagree about what a null
    /// means one method apart.
    test('a null user matches the empty-string owner, not the NULL one', () async {
      await database.submissionDao.upsertAll([
        SubmissionsCompanion.insert(
          id: 'ownerless',
          // What `upsertDocuments` stores for a document with no `user._id`,
          // and what Kotlin's `userId IS :userId` returns for a null argument.
          userId: const Value(null),
          type: const Value('exam'),
        ),
        // **`type: 'exam'`, and that is the whole point.** The first cut of
        // this decoy carried `type: 'survey'`, so the query's own
        // `type = 'exam'` conjunct excluded it under *both* readings and it
        // distinguished nothing — while its comment claimed it was what the
        // port's `?? ''` matches. Phase 156's `ada`-vs-`axl`, inside the file
        // that quotes Phase 156; caught by mutation, not by re-reading.
        //
        // With the type right, the assertion below names which row each
        // reading returns rather than asserting an empty list, so "found the
        // wrong row" and "found nothing" cannot be confused.
        SubmissionsCompanion.insert(
          id: 'empty-string-owner',
          userId: const Value(''),
          type: const Value('exam'),
        ),
      ]);

      expect(
        (await database.submissionDao.getExamSubmissionsByUser(
          null,
        )).map((row) => row.id),
        ['empty-string-owner'],
        reason:
            'TRIPWIRE — delete this group when the divergence is closed.\n'
            'Kotlin\'s `userId IS :userId` matches the NULL-owner rows for a '
            'null argument; the port\'s `equals(userId ?? \'\')` matches rows '
            'whose owner is the empty string instead. Unreachable from '
            'today\'s callers, but it is the `IS`-vs-`=` class this round was '
            'sent to settle and it is inconsistent with '
            '`countCompletedByUserAndExamId` one method away.\n'
            'To close it: make `getExamSubmissionsByUser` use '
            '`userId.equalsNullable(userId)` in '
            '`lib/data/local/app_database.dart` (Lane 1 owns it this round) — '
            'drift\'s `equalsNullable` is the spelling of SQL `IS`, as '
            '`CourseProgressDao` already uses it.\n'
            'BEFORE DELETING THIS GROUP, write the durable test that replaces '
            'it: the same two rows, and the result is `[\'ownerless\']`. '
            'Without it the fix is pinned by nothing (Phase 158).',
      );
    });
  });

  group('PersonalDao.titleExists is always user-scoped; Kotlin\'s is not', () {
    /// Kotlin `PersonalDao.countByTitle`:
    ///
    /// ```sql
    /// SELECT COUNT(*) FROM my_personal WHERE title = :title COLLATE NOCASE
    ///   AND (:userId IS NULL OR :userId = '' OR userId = :userId)
    /// ```
    ///
    /// The user predicate is **disabled** when the argument is null or blank,
    /// so the duplicate-title guard goes global: any user's note with that
    /// title blocks. `AddResourceViewModel.checkTitleExists(title, userId)`
    /// takes a `String?`, so that is reachable when the session has not
    /// resolved. The port's `PersonalDao.titleExists(userId, …)` takes a
    /// non-nullable `userId` and always scopes.
    ///
    /// The `COLLATE NOCASE` half is **not** a divergence worth closing: the
    /// port compares a stored `titleNormalized` against a Dart
    /// `toLowerCase()`, which is Unicode-aware where that collation folds
    /// ASCII only — so the port catches `ÉCOLE`/`école` and Kotlin does not.
    /// Matching Kotlin there would be a regression. Recorded in the coverage
    /// ledger, not here.
    /// **The argument has to be the blank one.** Mutation-testing this group
    /// caught it asserting `titleExists('member-1', …)`, which is `false`
    /// under *both* readings — Kotlin scopes to `member-1` too when the id is
    /// there, so applying the fix left the tripwire green and it pinned
    /// nothing. The divergence lives entirely in what a **null or empty**
    /// `userId` means: Kotlin drops the predicate, the port matches rows whose
    /// owner is literally `''`. Phase 156's `ada`-vs-`axl`, one round on.
    test('a session-less check does not see another member\'s note', () async {
      await database.personalDao.upsert(
        PersonalEntriesCompanion.insert(
          id: 'theirs',
          title: 'Water notes',
          titleNormalized: 'water notes',
          date: 1000,
          userId: 'member-2',
        ),
      );

      // The scoped call agrees under both readings, so it is here as the
      // control rather than as the claim.
      expect(
        await database.personalDao.titleExists('member-1', 'water notes'),
        isFalse,
        reason:
            'a resolved member does not collide with another member\'s note',
      );

      expect(
        await database.personalDao.titleExists('', 'water notes'),
        isFalse,
        reason:
            'TRIPWIRE — delete this group when the divergence is closed.\n'
            'Kotlin disables its user predicate for a null or blank `userId` '
            '(`PersonalDao.countByTitle`), so a member with no resolved '
            'session gets a cross-user duplicate check where the port gets a '
            'per-user one.\n'
            'To close it: give `PersonalDao.titleExists` a nullable `userId` '
            'and skip the `userId` conjunct when it is null or empty '
            '(`lib/data/local/app_database.dart`, Lane 1), and thread the '
            'nullable value through `personals_repository.dart` (no lane owns '
            'that file this round — the integrator\'s to place).\n'
            'Note while you are there: the port adds an `excludingId` Kotlin '
            'has no counterpart for, so renaming a note to its own current '
            'title is refused on Android and allowed here. That one is an '
            'improvement; leave it and record it.\n'
            'BEFORE DELETING THIS GROUP, write the durable test that replaces '
            'it: a blank `userId` finds another member\'s note of the same '
            'title, and a resolved one does not. Without it the fix is pinned '
            'by nothing (Phase 158).',
      );
    });
  });
}
