# Phase 135 — DAO predicates: user-scoped exam grading, and five unescaped `LIKE`s

Lane B of a four-lane round. Branch `claude/dao-predicates-q5w9`, off
`claude/kotlin-flutter-dart-migration-d3gmrd`. PR #16939.

Sole owner of `lib/data/local/app_database.dart` this round, which is why the
two jobs are one lane: **one file, one owner.** Two jobs, two separable
commits — `88b2fee` (the `LIKE` escaping) and `5bff98b` (`ed5609f` plus its
missing caller). `88b2fee` is committed first and stands alone, so it can be
reverted without touching Job 1.

Both briefs came from a previous round's *Reported, not fixed* list — Phase
133's item 1 and Phase 131's items 1–3 — which is the fifth consecutive round
where that list was the highest-yield source of work.

## Result in one paragraph

The `LIKE` escaping was exactly as reported and is fixed at all five sites,
with one test per site. Job 1 is the interesting half: the port of `ed5609f`
is small and the missing caller is real, but **the consequence Phase 133
attached to it is overturned.** "An exam-bearing course can never complete in
the port" is true, and it is equally true of the Kotlin app, because neither
app grades an exam step locally — so it was never a port defect. What the port
was actually missing is the *clearing* direction, and that is what `ed5609f`
scopes. Wiring the caller then turned three latent defects in the uncalled code
into live ones, which is the part worth remembering.

## Method

1. Read `ed5609f`'s diff, then the Kotlin call chain by hand:
   `BaseExamFragment.continueExam` → `saveCourseProgress` →
   `CoursesRepositoryImpl.updateCourseProgress` → the DAO, plus
   `CourseStepFragment.launchSaveCourseProgress`, `CoursesPagerAdapter`,
   `SubmissionsRepositoryImpl.saveExamAnswer`/`startExamSession` and
   `ProgressRepositoryImpl.getCompletedCourses`.
2. A `parity-auditor` pass at `effort: max` on that reading, **before** any
   Dart — eight numbered claims and three implementation questions.
3. Implement, with every defect demonstrated failing first.
4. A second `parity-auditor` pass at `effort: max` aimed at the finished,
   already-green code.

The first pass corrected two things and added three defects I had not seen;
they are recorded below rather than quietly folded in.

## What the ground-truth audit corrected

### `sub?.status == "graded"` is not unreachable — it is inert

My claim was that the string `"graded"` appears exactly once in the whole
Kotlin tree (the comparison itself), that no local writer produces it
(`saveExamAnswer` writes `pending` / `complete` / `requires grading`), and that
the exam branch of `startExamSession` always recreates the attempt — so the
comparison is *never* true.

The first two hold. The absolute does not. `startExamSession` is not the only
writer of `sub`: `BaseExamFragment.kt:88` does
`sub = submissionsRepository.getSubmissionById(it)` with **no status filter**,
and `SubmissionViewModel`'s survey list filters on `userId` and `type` with no
status predicate either — so a survey submission the server sent back as
`graded` is listed, tappable, and reaches the comparison on the my-surveys
resume path. It is inert there for a different reason: that path carries no
`"stepNum"` argument, `BaseExamFragment.kt:75` reads it out of the Bundle with
a default of 0, and `WHERE stepNum = 0` matches no row a course step ever
wrote.

The implementation is unaffected — the exam screen's attempt is always
recreated, so reading the row back is correct — but the *reason* in the doc
comment was wrong, and a wrong rationale is a defect here. Both comments now
say which path can produce `graded` and why it does not matter.

### `ed5609f` does not close the self case, and that quirk is ported

One user, one device: Planet grades step 3, the pull writes `passed = true`,
the learner retakes that step's exam, and the now-correctly-scoped `UPDATE`
writes `false` back over their own pass — the completed-course star goes dark
until Planet grades the new attempt. Upstream leaves this alone, so the port
does too, with a test named for it. Worth recording because it looks exactly
like a bug to fix.

## Job 1 — `ed5609f`, and the caller that never existed

The commit itself is four lines of behaviour: `AND userId IS :userId` on
`CourseProgressDao.updatePassedByCourseAndStep`, threaded through
`CoursesRepository.updateCourseProgress` and the abstract
`BaseExamFragment.saveCourseProgress`. Before it, passing an exam for step *N*
rewrote `passed` for **every** row at `(courseId, stepNum)` whatever its owner.
No DDL, so no schema bump — the port needed none either.

The port reproduced the old behaviour *and documented it as intentional*
("an exam result is not per-user", in two places). Upstream has repudiated that
rationale, so it is deleted along with the behaviour.

### What the missing caller actually cost

`updateCourseProgress` had **zero callers** in `lib/` or `test/`. Phase 133
read the consequence chain correctly and drew the wrong conclusion from it:

- `take_course_screen.dart` writes `passed: exams.isEmpty ? true : null`, so a
  step *with* an exam is deliberately left unpassed
  (`CourseStepFragment.kt:96`, verbatim).
- Nothing then graded it.
- `completedCourseIds` requires **every** step passed.

⇒ "an exam-bearing course can never complete in the port". True. But follow the
Kotlin the same distance: its exam finish passes `sub?.status == "graded"`,
which on the exam path is always false, so **Kotlin writes `passed = false`
too.** In both apps a step exam becomes passed only when Planet's own grading
arrives in the `courses_progress` document — `ProgressRepositoryImpl`'s
`if (passed != true) passed = getBoolean("passed", act) || localPassed`, which
the port mirrors in `CourseProgressMapper.fromDoc` and pulls in
`syncCourseProgress` from `CourseSyncNotifier`. That route is live and
reachable, and two tests now pin the pair:

| | expectation |
|---|---|
| finishing the exam | `completedCourseIds` is empty — in either app |
| the graded `courses_progress` document arrives | `completedCourseIds` is `{course-1}` |

The second one **passed before any of this phase's production changes**, which
is the evidence that the "most expensive reachability row yet" framing was
wrong. The row the port was genuinely missing is the narrow one: the reset.

**The general lesson, since this is the second time a reachability finding has
been over-read:** a chain that ends "so the port cannot do X" is only a defect
once you have walked the *same* chain in the Kotlin and found that it can. Both
apps are allowed to depend on the server.

### The caller, and the step number

Wired where Kotlin has it — `BaseExamFragment.continueExam:133`, after the last
answer and before the thank-you dialog — as `TakeExamScreen._recordExamProgress`.

Two decisions worth their reasons:

* **`courseId` comes from the exam document, not the route.** Kotlin passes
  `exam?.courseId` and never consults the fragment arguments here. It also
  avoids a trap the audit found: both port entry points build their query
  string as `courseId=${step.courseId ?? ''}` against a nullable column, so
  `widget.courseId` can be the empty string — and `'' ?? exam.courseId` does
  not fall back.
* **`stepNum` is derived, not passed.** Kotlin threads it through fragment
  arguments (`CoursesPagerAdapter` sets `stepNumber = position` where page 0 is
  the course cover, so step *i* carries *i+1*; `CourseStepFragment` forwards it
  as `"stepNum"`). This route carries `stepId` and not `stepNum`, and the same
  number is the step's index in `CourseDao.getSteps` plus one — *the same
  ordering* `take_course_screen._recordProgress` counts to write
  `stepNum: index + 1`. So the writer and the reader of that column agree by
  construction rather than by two independent conventions, which is the
  property the writer/reader-key-disagreement class keeps violating. The
  alternative — adding a `?stepNum=` parameter — is the closer port but only
  one of the two pushers is in this lane's file set, and a parameter half the
  callers set is worse than a derivation both share.

An unresolvable step (no `stepId`, empty `courseId`, or a `stepId` the course
does not list) skips the write rather than writing `stepNum: 0`. Kotlin reaches
the same outcome by a different route — its default *is* 0, and no real row
carries it.

### Three defects wiring it turned live

All three sat in code with no callers, which is why nothing had caught them.

1. **The insert branch was an invention, and its key omitted the user.**
   `updateCourseProgress` used to insert a row when the `UPDATE` matched
   nothing; Kotlin runs the guard plus one statement and nothing else. The row
   id was `'${courseId}_$stepNum'` — no user in it — and `upsert` is
   `insertOnConflictUpdate`, so a second learner taking the same step's exam
   would have overwritten the first learner's whole row *and rewritten its
   `userId`*. That is strictly worse than the pre-`ed5609f` bug it imitated:
   that one reset a flag, this reassigns ownership. Removed.
2. **The same insert wrote `createdOn` from `parentCode`** where Kotlin writes
   `planetCode`. The row has `couchId: null`, so `getPendingUploads` picks it
   up and the uploader sends `createdOn` verbatim — the wrong planet code
   reaching CouchDB. Removed with the branch; `parentCode` was that branch's
   only reader, so the parameter went too, which also matches Kotlin's
   four-argument signature.
3. **No empty-`courseId` guard**, where Kotlin has
   `if (courseId.isNullOrEmpty()) return`. Added, and tested.

A fourth, found by reading the DAO around the method I was changing: **four
user-scoped reads were `equals(userId ?? '')` where the Kotlin `@Query` is
`userId IS :userId`** (`getByUserAndCourseIds`, `getByUserAndCourse`,
`getByUser`, `findByCourseUserAndStep`). Coercing a null argument to the empty
string matches rows whose `userId` is literally `''` and **no** row whose
`userId` is NULL, where Kotlin matches exactly the NULL rows. Fixed with
`equalsNullable`. Not in the brief; it is the same defect class as the job
(null-safe user predicates in the very DAO being changed) and needs no schema
bump.

**The example I first justified it with was wrong, and the second audit caught
it.** I wrote that a synced document omitting `userId` produces one of those
NULL rows. It does *in the port* — `CourseProgressMapper.fromDoc` passes
`getStringOrNull` — but **Kotlin stores `""` there**, because
`ProgressRepositoryImpl.kt:260` uses `JsonUtils.getString`, which returns the
empty string for a missing field. So on my own cited input the fix made the port
**diverge**: a null-argument query returns nothing in Kotlin, nothing in the
pre-fix port, and the row in the post-fix port. The predicate change is still
the right port of the `@Query`; the defect my example describes belongs to the
mapper (item 9), and the case that genuinely justifies `IS` is Kotlin's *local*
writer at `ProgressRepositoryImpl.kt:234`, which does store NULL for a null
argument. Two lessons, both already rules here: **cite the writer, not a
plausible writer**, and a rationale that names the wrong mechanism is a defect
even when the code is right.

## Job 2 — five unescaped `LIKE` interpolations

Exactly as Phase 131 described. `my_library.user_id` and `courses.user_id`
hold a JSON list and shelf membership is the substring `"<userId>"` inside it;
Kotlin builds the pattern with `ResourcesRepositoryImpl.userIdPattern` and
`CoursesRepositoryImpl.userIdPattern` — verified character-for-character
identical, which is why one Dart helper serves both tables — and pairs every
DAO query with `ESCAPE '\'`.

Five sites interpolated the raw id, leaving LIKE's metacharacters live:

| site | |
|---|---|
| `MyLibraryDao.watchResources`, shelf arm | fixed |
| `MyLibraryDao.watchResources`, catalog arm (`.not()`) | fixed |
| `MyLibraryDao.resourcesOnShelf` | fixed |
| `CourseDao.watchCourses` | fixed |
| `CourseDao.coursesOnShelf` | fixed |

The `_` case is the sharp one and it is **two** failures from one cause: a
shelf query for `u_1` returned `ux1`'s rows, *and* the catalog arm — the same
predicate negated — dropped them from the catalog at the same moment, so the
resource was missing from both views at once. `%` is coarser: one id claims
almost every shelf.

All five now route through a `_shelfMembership` helper over the existing
`likeEscapedUserPattern`, with drift's `escapeChar` writing the `ESCAPE` clause
and the pattern bound as a variable. Seven tests, one per site plus the `%`
case, each with ids that differ **only** at the escaped character (`u_1` vs
`ux1`) so a pass cannot come from anything but the wildcard.

### The rationale that sent Phase 131 to raw SQL is false

`watchResourcesNeedingUpdateCount` says it uses `customSelect` because
"drift's `like()` takes no `ESCAPE`". It does:
`like(String regex, {String? escapeChar})` has been there all along
(drift 2.34.3, `expressions/text.dart:10`), and it binds the pattern as a
variable rather than splicing it in. The raw SQL stays — `IS NOT` has no
query-builder spelling and reading the statement beside its `@Query` is worth
something — but the comment is corrected, because the next person to need an
escaped `LIKE` would have believed it and reached for raw SQL too.

## Failing-first and mutation evidence

Every production change was demonstrated failing first:

* the seven `LIKE` tests: all 7 red before the fix;
* `updateCourseProgress` scoping: 2 red before, including the peer-clearing one;
* the `IS` semantics on the update: **mutation-checked** — swapping
  `equalsNullable(userId)` back to `equals(userId ?? '')` turns **exactly one**
  test red, the one written for it;
* the new caller: **mutation-checked twice** — deleting the
  `_recordExamProgress` call, and an off-by-one in the step-number derivation
  (`index` for `index + 1`), each turn a screen test red.

Following Phase 122's practice: a migration or predicate test that cannot fail
reads as coverage without being any.

## Gate

`dart format --set-exit-if-changed lib test` clean, `flutter analyze` clean,
`flutter test` **2430 passing** on the final tree, against 2405 on the base —
25 new tests across the two jobs and the second audit's fixes. Codegen
(`dart run build_runner build`) run before analyzing, per the
stale-generated-sources rule, since the Drift DAO signature changed.

An intermediate count of 2418/2419 appears in the audit narrative below and in
the earlier commit messages; those were taken before the second audit's fixes
added their tests, and before the `take_course_screen_test.dart` repair. The
number to trust is the one measured last.

## What the second audit found in this lane's own finished code

Six defects in code that was formatted, analyzer-clean and green at the time
(2419 tests).
Phases 110, 113, 116 and 119 each found more in their second pass; this is the
fifth. Two of the six are in the work this phase added, and the worst of them
made the phase's own headline change a no-op.

### The exam write was a no-op on the ordinary path (fixed)

`_recordExamProgress` derives the right `stepNum` and issues the right
`UPDATE` — against a row that does not exist. **Nothing in the port wrote a
`course_progress` row for the step the learner lands on.**

`_recordProgress` had one caller, `goToStep`, reached only from
`PageView.onPageChanged` and the Previous/Next buttons — and `onPageChanged`
never fires for the initial page. Kotlin's pager has a course-cover page at
position 0, so its first *step* is always arrived at; the port's `PageView`
opens directly on step 1. Kotlin also records the row twice over without any
page change at all: `CourseStepFragment.onViewCreated`'s
`withResumed { launchSaveCourseProgress() }` (`:168-172`) and
`setMenuVisibility` (`:262-268`), both gated on `userHasCourse`.

The blast radius is much wider than this phase's write:

* `getCurrentProgress` and `courseProgressSummary` count rows ignoring
  `passed`, so both under-reported by a step;
* `completedCourseIds` requires `passed`, and **a one-step course could
  therefore never complete** — the write that would pass its only step never
  ran;
* and the exam-finish `UPDATE` this phase added silently matched nothing.

Fixed by making `_CourseContent` stateful and recording the step in view on
mount and on change, gated on `isMyCourse` (Kotlin's `userHasCourse`), with
`_recordedStep` making a rebuild not a re-visit.

**The fix broke seven existing tests, and that is itself the finding.** They
all failed with `planetPrefsProvider must be overridden` — the Phase 75 harness
trap — because recording progress queues an outbox row, which reaches
`serverConfigProvider` and through it `planetPrefs`. **No test in
`take_course_screen_test.dart` had ever reached that code**, in a file of
thirteen tests for this screen, because nothing in it changed pages. The
harness trap was the proof of the defect: a screen whose progress write is
unreachable also has an untouched provider graph behind it.

**Why it survived a phase that was looking for exactly this.** Every one of my
tests seeded the row it needed — the four widget tests with
`courseProgressDao.upsert`, the repository tests with `saveCourseProgress`.
Nothing drove `take_course_screen` → `take_exam_screen` end to end, so the
fixture supplied what production did not. That is the "every fixture
hand-faked the join" symptom verbatim, and I wrote it in this file's own
method section while doing it. **Asking the three reachability questions is
not the same as answering them; the answer has to come from a test that uses
no fixture for the join.**

The first cut of the fix *also* failed, and instructively: it gated
`didUpdateWidget` on `currentStep` having changed, but `userId` comes from the
parent's `ref.watch(sessionProvider).valueOrNull?.id` and so is null on the
first frame — the membership gate bailed, and the rebuild carrying the
resolved session did not change `currentStep`. The `.valueOrNull` shape again,
in its rarest form: correctly *watched*, and still null when it mattered.

### A failed progress write reported the exam as failed to submit (fixed)

Kotlin's caller is `lifecycleScope.launch { … }` (`ExamTakingFragment.kt:846-850`)
and `continueExam` builds its thank-you dialog on the next statement, so a
failure there can neither suppress the dialog nor reach the learner. Awaited
inside `_submitExam`'s own `try`, a throw took the `examSubmitFailed` branch:
no dialog, no pop, and a learner told their exam failed when the attempt was
already `requires grading` on disk and already queued — so they would retake a
recorded exam. The realistic trigger is not a database fault but `ref` after
dispose, which throws if the learner backs out during the awaited photo
capture. Now contained in its own `try`, and documented as a deliberate
exception to this project's rule against silent failure.

### The call sat behind work Kotlin either does not await or does not have (fixed)

Kotlin's `capturePhoto()` only *launches* the camera, and Kotlin has no
upload-queue step at this point, so its progress write goes out before the
shutter opens and nothing downstream can skip it. Mine sat after a camera
round trip and two `queuePending` calls, making it skippable by any of them.
Moved ahead of both. Textual placement was already Kotlin's; **temporal
placement is the one that decides whether a write happens.**

### A sixth unescaped `LIKE`, and this one Kotlin does not have at all (fixed)

`CourseDao.watchCourses`' `courseTitleNormal.like('%$trimmed%')`. Kotlin's
course search is not a `LIKE`: `CourseDao` has no title query and
`CoursesRepositoryImpl.filterCourses` (`:304`) filters in memory with
`courseTitle?.contains(searchText, ignoreCase = true)`, which is literal. So
the port *invented* the `LIKE` — searching `intro_a` matched `IntroXA`, and a
lone `%` matched every course. Escaped through a new `_literalContains`, kept
separate from `_shelfMembership` because the two mean different things.

### Two of my rationales named the wrong mechanism (fixed in place)

The step-reorder claim was **inverted** — see *Reported, not fixed* item 5 —
and the NULL-`userId` justification cited a writer that behaves differently in
the two apps, see the Job 1 section above. Both are corrected where they were
written rather than only here, since a wrong rationale is what re-seeds a
mistake.

### Also worth keeping

* The `%`/`_` ids I tested with (`u_1`, `ux1`) are synthetic where the shipping
  shape is not: Planet usernames explicitly permit `_`, `.` and `-`
  (`UserRepositoryImpl.kt:827`) and a synced id is `org.couchdb.user:<name>`,
  so **`mary_jane` and `mary-jane` on one handset is the real reproduction** —
  as is any `guest_<name>`.
* The escaping fix is **write-side too**, which I had not realised:
  `shelf_repository.dart`'s `_localCourseIds`/`_localResourceIds` feed the
  uploaded shelf document, so before it `mary_jane`'s shelf POST carried
  `mary-jane`'s course and resource ids to CouchDB.
* The test named for the step-number derivation passes under the off-by-one
  mutation; the sibling test is what catches it. The claim "mutation-checked"
  is true of the pair, but **the test that names a property should be the one
  that fails for it**.

## A parallel-lane hazard nobody had written down

**An audit subagent's mutation check lands in the lane's own working tree.**
The second `parity-auditor` pass reverted this phase's four `equalsNullable`
reads to `equals(x ?? '')` to measure whether anything pinned them — the same
technique this lane used deliberately — and left the file that way. It was
caught only by the session's dirty-tree check on the way out; a reflexive
"commit what's outstanding" would have silently un-shipped the fix its own
commit message describes.

Two rules follow, and they cost nothing:

* **After an audit pass, `git status` is not a formality.** Diff the tree
  against the commit you expect before touching it, and read any change you
  did not make rather than staging it.
* **An audit agent that mutates should restore.** Until an agent definition
  enforces that, a lane must treat the tree as shared with whatever it
  spawned — the same way it treats a file shared with another lane.

The audit also reported it had run whole-suite passes with its own probe files
in place, which is why its test counts and this file's differ by a test or two
depending on when each was taken.

## Reported, not fixed

1. **The JSON/LIKE escaping mismatch — Phase 131's item 2 — is confirmed, and
   its reasoning stands.** The column holds a JSON list, so a user id
   containing a backslash is *stored* with that backslash doubled
   (`jsonEncode([r'a\b'])` is `["a\\b"]`), while `userIdPattern` escapes it for
   LIKE and `ESCAPE '\'` unescapes it again — so the pattern looks for a single
   backslash and never matches what was written. Kotlin's Gson converter and
   `ResourcesRepositoryImpl.userIdPattern` produce the identical mismatch, so
   reproducing it is parity, and **fixing it in the port alone would make the
   two apps disagree about shelf membership** for the affected ids. It needs
   escaping against the stored JSON form in both apps at once. Unreachable
   today — a CouchDB user id is `org.couchdb.user:<name>`. Phase 131's
   existing test pins both halves; nothing here changes it.
2. **`MyLibraryTable.userId` is non-nullable with a `'[]'` default — Phase
   131's item 3 — so `watchResources`' catalog arm `r.userId.isNull() | …`
   still has a branch that can never be true.** Kept deliberately: it is the
   Kotlin condition, escaping it changed nothing about it, and removing it is a
   schema question this round allocated no bump for. `'[]'` matches no
   `%"x"%` pattern, so the surviving arm is correct on its own.
3. **`completedCourseIds` omits Kotlin's non-blank id and title requirement,
   and that is fine — the filter lives one layer up.** Recorded because I
   nearly filed it as a defect and it is not one:
   `ProgressRepositoryImpl.getCompletedCourses` drops a completion with a
   blank id or title, and `progress_repository.dart`'s `completedCourseIds`
   does not — but its **only** consumer,
   `completedCoursesProvider` (`dashboard_providers.dart:172-182`), applies
   exactly that filter after resolving the rows, and says so. The port splits
   one Kotlin method across a repository and a provider, so a term missing
   from one half is not missing from the pair. **Check the consumer before
   filing a missing predicate**, in a port that folds Fragment + ViewModel
   into different seams than the original.
4. **The `?stepNum=` parameter is the closer port of the step number**, and
   only one of the two pushers is in this lane's file set.
   `take_course_screen.dart`'s `onTap` sits inside `_StepContent`, which
   already holds the number, and `course_detail_screen.dart` has `number` in
   scope. If a future round owns both files, passing it explicitly assumes
   nothing about ordering; the derivation here is equivalent for every
   reachable case but rests on `stepIndex`, whose column default of `0` makes
   ties legal even though no current writer produces one.
5. **A course-step reorder re-binds the port's step ids to different
   content, and my first write-up of this had it exactly backwards.** Kotlin's
   step id is content-derived — `Base64(stepElement.toString())`
   (`CoursesRepositoryImpl.kt:681`) — so a reorder changes no id, `@Upsert`
   updates in place, and every `course_progress.stepNum` keeps naming the same
   step content; only Kotlin's *rendered* order lags, because its position
   comes from an unordered read. The port's ids are `<courseId>:<index>` and
   `stepIndex` is rewritten on every pull, so after a reorder step 1's id
   points at what used to be step 2 while the learner's progress rows keep
   their old `stepNum`s — finishing the new step 1's exam writes `passed` onto
   the row that described the old one. **A divergence against the port, not in
   its favour**, and it is now load-bearing for this phase's `stepNum`
   derivation. Not fixed: changing how step ids are minted is a mapper and
   migration question. Note `docs/kotlin-to-flutter-migration.md` calls the
   port's position-derived ids "stable across syncs", which holds only while
   the step order does not change — the two documents should be reconciled by
   whoever takes this.
6. **The survey path's `saveCourseProgress` call is not a port gap.** Kotlin's
   `continueExam` fires it for the non-team survey path too, but that path
   never sets the `"stepNum"` argument — `SubmissionsAdapter.openSurvey` does
   not put the key and `CourseStepFragment.kt:280` is the only site in the tree
   that does — so `stepNumber` is the Bundle default 0 and the `UPDATE` matches
   nothing. `take_survey_screen.dart` (not this lane's file) therefore needs no
   equivalent call. Recorded so nobody adds one, and because the same argument
   is what makes the `graded`-status path above inert.
7. **The port's `CourseProgressMapper` stores NULL where Kotlin stores `''`.**
   A synced `courses_progress` document omitting `userId` gives the port NULL
   (`getStringOrNull`) and Kotlin the empty string
   (`ProgressRepositoryImpl.kt:260` via `JsonUtils.getString`, which returns
   `''` for a missing field). Aligning it is a one-word change in
   `course_progress_mapper.dart`, which is not in this lane's file set. It
   matters because this phase made the four user-scoped reads null-safe, so on
   that input the apps now differ where before they agreed by accident. Planet
   writes `userId` on every progress document, so it is not reachable today.
8. **A course-step reorder re-binds the port's step ids to different content.**
   Item 5 above carries the detail; it is listed twice deliberately, once as a
   correction of my own claim and once as work for somebody. Fixing it means
   changing how `CourseMapper` mints step ids, which is a migration question.
9. **`take_course_screen._recordProgress` writes neither `planetCode` nor
   `parentCode`.** Kotlin's `saveCourseProgress` sets
   `createdOn = planetCode` and `parentCode = parentCode`
   (`ProgressRepositoryImpl.kt:231-233`), and
   `course_progress_uploader.dart:85,89` uploads both columns — so every
   locally-authored progress row reaches CouchDB with both null. The insert
   this phase deleted got `createdOn` wrong in a different way (it passed
   `parentCode`); deleting it leaves `_recordProgress` as the only writer, and
   it fills neither. Not fixed because the values come from the session user
   and threading them through is a change to the screen's provider reads
   rather than to this phase's subject.
10. **The port's course search matches a folded column against an unfolded
    query.** `watchCourses` compares `courseTitleNormal` (diacritic-folded by
    `CourseMapper`) with `query.trim().toLowerCase()`, so searching "Café"
    misses a course the Kotlin's `contains(ignoreCase = true)` finds. Escaping
    the pattern (this phase) does not touch it. The fix is to fold the query
    with `text_utils.normalizeText`, the single one — but the courses screen
    also filters in Dart via `filterCourses`, so which layer should own the
    match wants deciding rather than patching.
11. **`NewsDao`'s team-voices predicate diverges two ways**, surfaced while
    sweeping the `LIKE` class. Kotlin's `getTopLevelByTeamFlow` is
    `viewIn LIKE '%"_id":"<teamId>"%' ESCAPE '\\' OR (viewableBy = 'teams' AND
    viewableId = :teamId)`; the port filters `viewIn` with `jsonDecode` (more
    precise than a `LIKE`, so not an escaping bug) but **drops the
    `viewableBy`/`viewableId` arm** and **adds a `docType = 'message'` filter
    Kotlin does not have**. A server-authored team post carrying
    `viewableBy`/`viewableId` and no `viewIn` array is invisible in the port's
    team voices. Not this lane's files.
12. **`CourseProgressDao.getByIds` matches `couchId` as well as `id`,** where
   Kotlin's is `WHERE id IN (:ids)` only. It is not a defect — the port stores
   `id: base?.id ?? docId`, so a locally-authored row keeps its local id and
   carries the server id in `couchId`, and the sync-in look-up needs the second
   arm. Recorded because it reads like an unexplained extension.
