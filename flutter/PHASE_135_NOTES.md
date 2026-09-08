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
`userId` is NULL, where Kotlin matches exactly the NULL rows — and a synced
document that omits `userId` writes one of those, since
`CourseProgressMapper.fromDoc` passes `getStringOrNull`. Fixed with
`equalsNullable`, drift's spelling of `IS`. Not in the brief; it is the same
defect class as the job (null-safe user predicates in the very DAO being
changed) and needs no schema bump.

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
`flutter test` **2418 passing** (2405 before this lane's 13 new repository and
screen tests; the 7 `LIKE` tests are inside the 2405 because they were written
and run first). Codegen (`dart run build_runner build`) run before analyzing,
per the stale-generated-sources rule — the Drift DAO signature changed.

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
5. **`CourseStepDao`/`CourseStep` in Kotlin carry no order column**, so
   Kotlin's `stepNum` is a position in an unordered read — the document's step
   order *as first inserted*. An author who reorders a course's steps leaves
   every Kotlin `stepNum` stale, because `@Upsert` keeps the rowids; the port
   rewrites `stepIndex` from the new position on every pull and tracks the
   reorder. A divergence in the port's favour, pre-existing, and now
   load-bearing for a derivation — so it is named in the code rather than left
   implied.
6. **The survey path's `saveCourseProgress` call is not a port gap.** Kotlin's
   `continueExam` fires it for the non-team survey path too, but that path
   never sets the `"stepNum"` argument — `SubmissionsAdapter.openSurvey` does
   not put the key and `CourseStepFragment.kt:280` is the only site in the tree
   that does — so `stepNumber` is the Bundle default 0 and the `UPDATE` matches
   nothing. `take_survey_screen.dart` (not this lane's file) therefore needs no
   equivalent call. Recorded so nobody adds one, and because the same argument
   is what makes the `graded`-status path above inert.
7. **`CourseProgressDao.getByIds` matches `couchId` as well as `id`,** where
   Kotlin's is `WHERE id IN (:ids)` only. It is not a defect — the port stores
   `id: base?.id ?? docId`, so a locally-authored row keeps its local id and
   carries the server id in `couchId`, and the sync-in look-up needs the second
   arm. Recorded because it reads like an unexplained extension.
