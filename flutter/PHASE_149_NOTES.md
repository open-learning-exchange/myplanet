# Phase 149 — making the documentation true

Lane D of a four-lane round. The brief was six statements other lanes reported
as stale but could not edit, plus four small defects reported alongside them.
Nearly nothing here was taken on trust: almost every claim was read against the
Kotlin or the Dart before it was acted on, and **three of the reports did not
survive that reading**. The exception is recorded below rather than smoothed
over, because it is the one that matters: I pasted one supplied sentence
*including its citation* without opening the citation, and the citation says the
opposite. Those are in *Findings* below, ahead of the fixes, because a wrong
correction shipped into a load-bearing document is worse than the stale sentence
it replaces — and this project writes its briefs from these documents.

## Method

1. A `parity-auditor` pass at `effort: max` on the **Kotlin ground truth**,
   before any Dart, asking five numbered questions with the reported claims
   quoted verbatim and an explicit instruction to say which were false. It
   returned fourteen corrections.
2. An independent reading of the same Kotlin by hand for the two claims the
   fixes turned on (the step row, the guest predicate). **The two readings
   agreed on the finding that changed the phase**, which is the argument for
   doing both.
3. Implement, each behavioural change demonstrated failing first.
4. Mutation-test every new pin.
5. A second `parity-auditor` pass at `effort: max` on the finished code. **It
   found seven defects, all of them documentation-truth defects in a
   documentation-truth lane**, and one of them is the lesson of the phase —
   see *The correction that was itself uncorrected* below. Run both passes; the
   ground-truth pass cannot see what you then write.

## Findings — three reports that were wrong

### 1. The step row's second line is not "hidden unless expanded". It can never be shown.

`PHASE_145_NOTES.md` item 3 says `CoursesStepsAdapter.bind` sets the step row's
second line from `R.string.test_size` and "hides it unless the row is expanded".
The first half is exactly right (`CoursesStepsAdapter.kt:51-55`). The second is
true as a description of the code and false as a description of the app:

* `row_steps.xml` declares `tv_description` `visibility="gone"`.
* `updateDescriptionVisibility` (`:57-63`) keys on `StepItem.isDescriptionVisible`,
  default `false` (`StepItem.kt:7`).
* Its only writer is `CourseDetailViewModel.toggleStepDescription` (`:84-92`).
* Whose only caller is `CourseDetailFragment:140` — **the `else` arm** of
  `val parent = parentFragment as? TakeCourseFragment; if (parent != null) parent.navigateToStep(...) else ...`.
* And `CourseDetailFragment` is constructed in exactly one place:
  `CoursesPagerAdapter.kt:44`, page 0 of `CoursesPagerAdapter(this@TakeCourseFragment, courseId)`
  (`TakeCourseFragment.kt:122`). `FragmentStateAdapter(Fragment)` hosts its pages
  in that fragment's child manager, so `parentFragment` is **always**
  `TakeCourseFragment`. No manifest entry, no nav graph, no second construction.

So the `else` is dead, `isDescriptionVisible` is always `false`, and
**"This test has %d questions" is never drawn in the shipping Kotlin app.**

That changed what this phase did. The brief said to show the right datum; the
ground truth says the slot is empty. Porting the line would have been porting a
no-op — the thing Phase 61 declined to do with the master's progress dialog,
whose `di` is never assigned. So the fix is the removal, and the ARB key
(`testSize`) that had already been added for the line was **reverted before
commit**: an orphan template key is precisely the class of defect item 8 is
about.

Two things worth keeping from the same reading. `CourseStep.noOfResources` is
written at `CoursesRepositoryImpl.kt:695` and **read nowhere** in
`app/src/main` — a dead column in Kotlin too, so the port's subtitle had no
Kotlin origin in either the datum or the slot. And `step.questionCount` is
`examDao.getFirstByStepId(stepId)?.noOfQuestions` (via
`SubmissionsRepositoryImpl.getExamQuestionCount:162-164`), a query with neither
a `type` filter nor an `ORDER BY`, while `collectRoomExam` writes a step's exam
*and* its survey under the same `stepId` (`CoursesRepositoryImpl.kt:698-699`) —
so on a survey-only step that line would *always* have reported a survey's
question count under "This test has …". Porting it would have carried the
mislabel into a slot where, unlike Kotlin's, it renders.

### 2. `_isStepCompleted` is not in `take_course_screen.dart`

`PHASE_143_NOTES.md` item 2 routes this work to Lane D on the grounds that the
caller "is in `flutter/lib/ui/courses/take_course_screen.dart`, **Lane D's
file**". It is not: `take_course_screen.dart` has no such function, only a
doc-comment mentioning it. `_isStepCompleted` is a private top-level function in
`flutter/lib/providers/courses_providers.dart`, which is **on no lane's declared
set this round**.

Done anyway — the change is one function body plus its dartdoc, and the item was
assigned to this lane — and flagged to the integrator on the PR the same hour,
naming the exact hunks, so a collision with Lane A's course work is checkable
rather than latent.

### 3. The `courseDetails` deletion cannot be split across lanes, so it was not done

`PHASE_145_NOTES.md`'s integrator note proposes deleting the orphan key "from the
template" and reporting the locale half. **That split is not available.**
`test/l10n/locale_coverage_test.dart:60-72` asserts that no locale carries a key
the template does not, so deleting `courseDetails` from `app_en.arb` alone reds
the suite. Demonstrated rather than assumed: with the key removed from the
template only, the test fails with
`Actual: WhereIterable<String>:['courseDetails'] … es has keys absent from app_en.arb`.

The deletion is one atomic edit across four ARBs plus one number: `app_en.arb`,
`app_ar.arb`, `app_es.arb`, `app_fr.arb` (ne and so do not carry the key), and
`humanReviewed['es']` 477 → 476 in `test/l10n/placeholder_integrity_test.dart`
— ar and fr are `"x-mt"`, so only the es entry is counted there. Verified, and
handed to the integrator as one unit. Phase 128 left `takeTest` in the same
state; this makes two.

## The correction that was itself uncorrected

The `AnswerShape` fix (below) came with a replacement sentence supplied verbatim
by `PHASE_143_NOTES.md`, ending: *"`PHASE_123_NOTES.md:108-110` records the same
three columns as still owed, so this is a live target, not a hypothetical one."*
I checked the half about `migration_test.dart` against the test file, extended
it, and **pasted the citation without opening it.**

`PHASE_123_NOTES.md:106-112` says the opposite about two of the three:

> `SurveyQuestions` has no `marks`, `correctChoice` or `hasOtherOption` column,
> so a survey's questions omit those three keys **rather than inventing
> defaults**; a survey question has no correct answer to lose. (Kotlin would
> send `""`, `[]` and `false` there — `JsonUtils` defaults, not data.)

That is a design decision in the port's favour, not a debt. Only
`hasOtherOption` is a gap. The sentence I shipped pointed a future lane at
adding two columns to a **preserved** table — a schema bump, two hand-written
`_addColumnIfMissing` steps and the migration-test churn that follows — to close
a hole Phase 123 argued should stay open.

Two things make this worth a section rather than a line. It is exactly the
failure mode this lane exists to prevent, committed by the lane, on its second
target. And the mechanism is the one `CLAUDE.md` already names: *a brief
propagates the previous round's mistakes as efficiently as its facts.* The
wording was inherited, the error was inherited with it, and the reason the chain
broke here is that the sentence looked like a correction — the very shape that
gets read as already checked.

**A supplied replacement sentence is a claim, not a patch.** Its citations need
opening like any other. Caught by the implementation audit, which is the
argument for running one even on a phase whose entire diff is prose.

## What landed

### Preserved tables: four → the real set, in both passages

`docs/kotlin-to-flutter-migration.md` carried the four-table list in **two**
places, not one: the *Drop-and-resync* principle, and the *preserved-table test*
section, whose closing line said the guard held "while the set grew from nine
tables to twelve". The set counted out of `app_database.dart:141-272` is **27**.

Both passages now point at `app_database.dart` as the authority and say why
reading it beats reading a copy: every entry past the first four carries the
argument for its own membership as a comment, and the list in
`migration_test.dart` is a second copy, not a source. The cost sentence is
sharpened too — `createAll` cannot ALTER a preserved table, so **every** new
column on one needs a hand-written `_addColumnIfMissing` step, not "changing one
of their shapes".

One precision fix while there: the guard test does not "fail until a
preservation test exists" — it asserts set equality against a list written by
hand beside those tests, so what it enforces is that you go to the file where
they live. Said that way instead.

### `AnswerShape.forQuestion`'s column cost

`submissions_repository.dart` said a `hasOtherOption` column on
`survey_questions` "costs only a schema bump, no hand-written step". True until
Phase 143 preserved that table. The replacement is the one Phase 143 supplied,
checked against `migration_test.dart` first and then extended, because the
mechanics are more specific than "the guard fails": adding a column reds
`installSurveyShape`'s frozen `survey_questions` DDL comparison (`:1341-1349`)
**immediately**, and the post-upgrade `containsAll` over
`database.surveyQuestions.$columns` (`:1381-1387`) stays red until an
`_addColumnIfMissing` step actually adds it. The literals are deliberately never
updated to match the table (`:1197-1204`) — that is what makes them able to
fail. `PHASE_123_NOTES.md:108-110`'s three owed columns are named at the site so
the next person does not have to find them.

### `ChatShareActions`' delivery claim

The header ended "…and the reason a shared chat cannot sit undelivered on the
device". Phase 140 item 4 withdrew exactly that. **Phase 144 then re-earned the
conclusion but not the attribution**, which is why the suggested replacement in
`PHASE_142_NOTES.md` ("It is not a delivery guarantee") would itself have been
stale by the time it was applied.

What is true now: `share` still skips the enqueue when `serverConfigProvider` is
null and still returns `ChatShareOutcome.shared`
(`chat_provider.dart`, the `if (config != null)` at the end of `share`). But
`createFromShareMap` leaves the row with no `_id`, so
`VoicesRepository.pendingUploads` finds it (`voices_repository.dart:814-820`),
and Phase 144's sweep collects it on the first pass that has a config —
`DashboardSyncNotifier.queuePendingVoices` (`dashboard_sync_provider.dart:421`)
in the foreground, `sweepPendingVoices` from `drainOutbox`
(`background_entrypoint.dart:284`) headless. So the chat does not sit
undelivered; **the reason is the sweep, and the header now says so**.

### The `uploadNews()` shapes, recorded so a harvest does not port them

A new section in `docs/kotlin-to-flutter-migration.md` covering the three
duplicate/loss vectors `PHASE_144_NOTES.md` item 5 reported. Substance
confirmed on all three, by the audit and then by hand. Two things the report had
wrong or thin, both now corrected in the doc:

* **The locators had rotted.** `UploadManager.kt:485-487` is past the end of a
  462-line file; the loop is at `:365-367`. And "`uploadTeams` ten lines up" was
  203 lines away in a different function when written, and is now in a different
  *file* (`TeamsUploader.kt:65-67`). The section cites functions and mechanisms
  for that reason, and says so.
* **The teams/news comparison omits the two differences that matter.** Teams
  also guard the *longer*-response direction (`batch.getOrNull(i) ?: continue`,
  `TeamsUploader.kt:71`) where news index bare and throw; and teams deliberately
  do **not** retry per-document errors, passing `response.code()` with no
  exception and a comment saying so (`:73-75`), where news construct an
  `Exception` that makes `queueNewsRetry`'s gate (`UploadManager.kt:412`)
  retryable. That second one is the *root* of vector (c), not an aside.

Also added: the 409-as-success path recurs without bound, because
`RetryDao.findExisting` (`:37-41`) excludes `completed`, so each sweep enqueues a
fresh operation at `attemptCount = 1` rather than counting toward the
five-attempt ceiling.

### `UserMapper.isGuestAccount` — the role clause, beside `isGuest`, not folded in

`UserEntity.isGuest()` (`UserEntity.kt:178-182`) is

```kotlin
val hasGuestId = _id?.startsWith("guest_") == true
val hasGuestRole = rolesList?.any { it.equals("guest", ignoreCase = true) } == true
return hasGuestId || (hasGuestRole && rolesList?.any { it.equals("learner", ignoreCase = true) } != true)
```

and the port implemented the first disjunct only. Phase 145 landed the port's
first counterpart of a Kotlin gate that reads this predicate
(`TakeCourseFragment:213` — **not** `:212`, which is the `setCourseData`
signature), on *both* course screens, so both were narrower than their
originals: a user with `roles: ["guest"]`, no `learner`, and an ordinary
`org.couchdb.user:` id got a join button Kotlin withholds — and the
`course_detail_screen` copy's tap pushes the shelf to the server.

The doc said where the fix goes and **it was right**: a second predicate beside
`isGuest`, never folded into it. The ground-truth pass measured the reason
rather than restating it — 41 `startsWith("guest")` occurrences against 12
`isGuest()` calls, and **every** settings gate (`SettingsActivity:221`, `:249`,
`:286`, including reset app) and every voices gate (`VoicesFragment:94`,
`VoicesAdapter:526`, `:664`, `TeamsVoicesFragment:105`) reads the narrow one.
Folding would have widened all seven.

Three details the tests pin, each of which a naive port gets wrong: the role
comparisons are case-insensitive where the id prefix is not; a null or empty
role list is **not** a guest (the conjunction short-circuits, so the learner
clause never decides alone); and the learner clause is `!= true`, so a user
carrying **both** roles is not a guest.

Two things kept deliberately. The id half delegates to `isGuest`, which tests
both id columns where Kotlin reads `_id` alone — the same documented widening,
which errs towards withholding a privilege. And the other eleven `isGuest()` sites
(`TeamFragment:235`, `:253`, `CoursesFragment:135`, `:141`, `:250`,
`ResourcesFragment:208`, `:217`, `:524`, `:535`, `ResourceDetailFragment:215`,
`BaseContainerFragment:154`) still have no port counterpart; when one lands it
wants `isGuestAccount`, which the dartdoc now says.

Still unreachable today — no port writer creates a guest row — but the *role*
half is different in kind from the id half: `UserMapper.fromDoc` writes
`rolesList` straight from the account document's `roles`, so Planet can produce
a role-only guest on the next login. The id half needs a `createGuestUser` the
port does not have.

### The `_StepTile` subtitle, removed

Covered in *Findings* 1. `resourcesInStep(step.noOfResources)` is gone from
`course_detail_screen`'s step tile; the count survives on `take_course_screen`,
where it is a deliberate, documented stand-in for the inline resource list the
port cannot build until the course-resource walk lands (Lane A's target this
round). One is a stand-in; two was a duplicate of a thing Kotlin does not do.

### `_isStepCompleted` reads Kotlin's own count

`SubmissionDao.countCompletedByUserAndExamId` had zero callers by design. The
swap replaces read-all-then-filter-in-Dart with the query, closing five
divergences at once, all toward the Kotlin:

* **The `type` predicate**, which is the port's own — copied from
  `SubmissionDao.kt:15` (`getExamSubmissionsByUser`), a *different* Kotlin
  method that does carry one. Kotlin's count has none.
* **`LIKE` is ASCII-case-insensitive** where `contains` is not.
* **`%` and `_` in the exam id are wildcards**, unescaped in Kotlin;
  escaping would have made the port stricter than the app it ports.
* **A null `userId`** matches rows whose `userId` IS NULL (`userId IS :userId`)
  where the old code matched `userId = ''`, a value the port never writes —
  `normalizeSubmissionUserId('')` returns null.
* **A null `parentId` with an empty assessment id**: `(null ?? '').contains('')`
  is `true` in Dart; `parentId LIKE '%%'` is NULL and excludes the row.

The NULL-`status` handling is unchanged and was already right.

The `type` case is the one with a demonstrated failure, and it is reachable
through a production writer rather than a fixture:
`SubmissionsRepository.upsertDocuments` stores
`type: Value(JsonUtils.getStringOrNull('type', json))`, so a Planet document
that omits `type` lands with a null one. *a synced submission with no type
releases the lock* drives that path with a server-shaped document and asserts
the step advances; on the pre-swap code it fails on the page assertion, with the
row's null `type` asserted first so the fixture cannot silently stop being
evidence.

## Mutations run

Every new pin was mutated on green code and reverted.

| Mutation | Result |
|---|---|
| `countCompletedByUserAndExamId(userId, …)` → `(null, …)` (narrowing) | red — the 4 release tests |
| drop the `userMatch` conjunct in the DAO (widening) | red — *another learner's finished attempt does not release it*, and `dao_query_semantics_test`'s *a null userId matches the rows with no user* |
| revert `_isStepCompleted` to the Dart filter | red — the new typeless-submission test |
| `isGuestAccount`: drop `&& !roles.contains('learner')` | red — 3 tests across two files |
| `isGuestAccount`: drop the `toLowerCase()` fold | red — *the role comparison ignores case* |
| `isGuestAccount`: drop the `isGuest(user)` disjunct | red — *the id rule still stands on its own* |
| both join gates: `isGuestAccount` → `isGuest` | red — one test per screen (this is how they were demonstrated failing first) |
| remove `courseDetails` from `app_en.arb` only | red — `locale_coverage_test.dart`, which is *Findings* 3 |

## Reported, not fixed

1. **A status-less Planet submission unlocks a step in Kotlin and locks it in
   the port.** The biggest live item on this list, found by the ground-truth
   pass and **surviving this phase's own fix**. Kotlin's sync-in writes
   `status = JsonUtils.getString("status", submission)`
   (`SubmissionsRepositoryImpl.kt:659`, `:679`) and Kotlin's `JsonUtils.getString`
   returns `""` for a missing key (`JsonUtils.kt:65-68`), so a document with no
   `status` stores `""`, `'' != 'pending'` is true, and the count is 1. The
   port's `upsertDocuments` stores `getStringOrNull('status', json)`
   (`submissions_repository.dart:1762`), which is **null** for a missing key
   *and* for `""` (`json_utils.dart:19-22`); `NOT (NULL = 'pending')` is NULL and
   the row is excluded. Same divergence on `type`, which is why the typeless
   test above had to set `'status': 'complete'` — it exercises one axis and
   cannot see this one. The fix is a `""`-vs-null decision in the sync-in that
   every submission reader shares, which is wider than this lane's brief and
   should be its own slice.
2. **`app_database.dart` carries a claim its own phase corrected.** The
   `surveys` entry in `_localAuthorityTables` says an orphaned step-joined
   survey row is "unreachable from the UI (the step tile is gone with the
   course)". `PHASE_143_NOTES.md` item 4 explicitly retracts that:
   `SurveysRepository.individualSurveys` filters on
   `!teamShareAllowed && (teamId ?? '').isEmpty` (`surveys_repository.dart:41-46`)
   — **no `stepId` or `courseId` test** — so such a row is listed at
   `/life/surveys` and tappable forever. Verified here. The retraction reached
   the notes and not the code; `app_database.dart` is Lane A's file. *A
   correction has to reach every copy of the claim.*
3. **The `courseDetails` deletion**, as one atomic edit — see *Findings* 3 for
   the exact file list and the number to change.
4. **Eleven `isGuest()` gates still have no port counterpart, and five of them
   are gates on screens the port already has** — so these are *ungated
   affordances*, not merely unported ones. Verified at the Dart:
   * `ResourceDetailFragment:215` (the add/remove-from-My-Library button) →
     `resource_detail_screen.dart:356`, no gate.
   * `BaseContainerFragment:154` (attaches the rating listener only for a
     non-guest) → `resource_detail_screen.dart:219` **and**
     `course_detail_screen.dart:171`, neither gated.
   * `CoursesFragment:250` (`CourseSelectionController(isGuest = …)`, which
     suppresses multi-select) → `courses_screen.dart:51-103`, no gate.
   * `ResourcesFragment:524`/`:535` (the batch add/remove buttons, select-all,
     search, collections) and `TeamFragment:235`/`:253` (add team, per-row
     join/leave) — the two this list originally named.

   All unreachable while no port writer mints a guest row, and all wanting
   `isGuestAccount` rather than `isGuest` when they land. My first draft of this
   item named two of the five; the implementation audit found the other three,
   which is the second time in this phase that a list I wrote from memory was
   short.
5. **Kotlin's two null-user fallbacks contradict each other.**
   `CoursesFragment:135` is `user?.isGuest() ?: true` and
   `ResourcesFragment:208` is `user?.isGuest() == true` — the same null user is
   a guest on one screen and not on the other. Recorded before someone ports
   both and reproduces the inconsistency without noticing it is one.
6. **`getNewsForUpload` has no unsynced predicate at all**, so Kotlin re-sends
   every non-guest news row on every sweep. The port's `pendingUploads` already
   diverges from this deliberately and the migration doc records why; noted
   again here only because it is what turns vectors (a) and (b) from "a row is
   missed" into "a row is duplicated".
7. **`RetryOperation.modelClassName` is stored and read by nothing**
   (`RetryOperation.kt:42`), which is why no retry writes an id back for any
   upload type. If a future slice ports the retry queue rather than the outbox,
   that field is the hook the write-back would need.
