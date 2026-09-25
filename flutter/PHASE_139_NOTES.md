# Phase 139 — the per-step assessment lock, the survey's return, and an explicit `stepNum`

Lane B of a four-lane round. Three jobs, all from previous rounds'
*Reported, not fixed* lists — the sixth consecutive round where that list was
the highest-yield source of work:

1. `TakeCourseFragment.changeNextButtonState` was unported — the per-step Next
   lock for `MANDATORY_SURVEY_COURSE_ID` (Phase 128 item 4).
2. A course-step survey `go`es to the submissions screen instead of returning
   to the step, making half of Phase 128's tile refresh unreachable (Phase 128
   item 6).
3. A course step's number was derived from `stepIndex` where `?stepNum=` is the
   closer port (Phase 135 item 4).

## Method

1. Read the Kotlin by hand: `TakeCourseFragment`, `CourseStepFragment`,
   `CoursesPagerAdapter`, `TakeCourseViewModel`, `CoursesRepositoryImpl
   .getCourseStepData`, `SubmissionsRepositoryImpl.isStepCompleted`,
   `BaseExamFragment.continueExam`/`showUserInfoDialog`, `SubmissionsAdapter
   .openSurvey` and all five of its call sites.
2. A `parity-auditor` pass at `effort: max` on **24 numbered claims** about that
   reading, before any Dart. It confirmed most, corrected four, and added six
   material items I had not asked about. All of them are recorded below rather
   than quietly folded in.
3. Implement, with each defect demonstrated failing first.
4. **Eighteen mutations**, each replayed against the suite to confirm the
   intended test reds.
5. A second `parity-auditor` pass at `effort: max` aimed at the finished,
   already-green code. It found one test that could not fail, three recorded
   rationales that were wrong, and four divergences worth naming — every one of
   them is folded in below rather than left in the transcript. **That is five
   consecutive rounds in which the second pass found something on green code.**

## What the ground-truth audit corrected

### One claim was simply wrong: a non-member *can* meet Kotlin's lock

I claimed `changeNextButtonState` has no membership test but
`updateNavigationVisibility` (`:256-270`) hides Next for a non-member, so a
non-member can never reach the lock. **The second half is false**, and the
audit found the line: `onResume:154-160` makes Next visible again with **no**
membership test, `setNavigationButtons`/`onClickNext`/`onClickPrevious` are
equally ungated, and `next_step` carries no `android:visibility` in the layout
(visible by default — only `finish_step` is `gone`). Since `position` is
restored from the learner's saved progress (`:116`), somebody who joined, made
progress, left the course and came back opens at `position > 0` with a visible
Next and meets the lock.

So gating the port's lock on membership is a **deviation, not fidelity**, and it
is now documented as one at `_nextStepLock`. It is still the right call: the
port's `_NavigationBar` has no membership gate at all, so gating the lock
reproduces Kotlin's *intended* rule (`updateNavigationVisibility`) and diverges
only from its `onResume` slip — and it keeps the lock consistent with the step
tile, which `_StepContent` hides for a non-member. Being blocked by an
assessment the screen is not offering you is worse than not being blocked.

### `stepExams` is `type`-filtered where `getFirstByStepId` is not, and the port cannot reproduce the split

The most important omission. Kotlin's `type` column holds **the server
document's own `type`**, with the singular `examKey` only as a fallback:

```kotlin
// CoursesRepositoryImpl.kt:746
type = if (examJson.has("type")) JsonUtils.getString("type", examJson) else examKey,
```

`examKey` is `"exam"`/`"survey"` — values no Kotlin query ever selects. So a
`steps[i].exam` object carrying **no `type` field** is stored as `"exam"` and
`getByStepIdAndType(stepId, "courses")` returns nothing: no Take Test button,
and **no lock**. Kotlin's lock only ever fires for documents that declare
`"type": "courses"` or `"type": "surveys"`.

The port has no `type` column on `Exams`: `ExamMapper` routes on
`type == 'surveys'` and files everything else as an exam, so its tables are a
**strict superset** of Kotlin's. A step carrying a type-less exam and a
type-less survey is unlocked in Kotlin and locked in the port. That is Phase
113's documented deviation, extended from the buttons to the lock — the
rationale now lives at `_isStepCompleted` rather than only at the mappers.

Two more consequences, both in the port's favour:

* **Kotlin can wedge a step permanently and the port cannot.** `stepExams` is
  `type`-filtered while `getFirstByStepId` is not, so a step whose exam object
  is type-less and whose survey says `"type": "surveys"` locks with the *survey*
  message while the unlock condition interrogates the *exam* row. Answering the
  survey never opens it.
* **Kotlin's row pick is not deterministic even in Kotlin.** `exams` has an
  index on `stepId`, so the order is rowid-within-stepId, and a course-document
  edit mints a new Base64 step id whose row lands later — flipping the order.
  The port's positional step ids make the pick deterministic.

### `isStepCompleted` differs from `hasSubmission` in four ways, not one

I had named the `status != 'pending'` test. The audit's table:

| | `isStepCompleted` (`:359-365`) | `hasSubmission` (`:176-192`) |
|---|---|---|
| parentId | `LIKE '%' \|\| examId \|\| '%'` | exact `"$examId@$courseId"` |
| status | `!= 'pending'` | none |
| type | none | `type = :type` |
| questions | none | `countByExamId == 0 → false` |
| missing input | `stepId == null → true`; no row → **true** | blank id/course/user → **false** |

**The row that mattered most was the first, not the one I had spotted.** Given
Phase 125, a Dart `isStepCompleted` written as `parentId == examId` finds
nothing (every writer stores the compound key), and one written as
`parentId == '$examId@$courseId'` diverges the other way — Kotlin's `LIKE`
matches a bare-id row, a compound row, and anything else carrying the id as a
substring. `_isStepCompleted` uses a `contains` and says at the code that it is
deliberately loose. Mutating it to `==` reds three tests.

Also from the audit, and reproduced: a NULL `status` does **not** count
(`status != 'pending'` is NULL in SQL, hence false), while the team-adoption
marker `status: ''` **does**.

### `CourseDao.getSteps` does not exist in Kotlin, and "the same ordering" was wrong

`take_exam_screen`'s doc comment claimed the derivation used "the *same*
ordering" as Kotlin. There is no such ordering: the query is
`CourseStepDao.getByCourseId`, `SELECT * FROM course_steps WHERE courseId = ?`
with **no `ORDER BY`**, and `CourseStep`'s PK is `Base64(stepJson)` with no
position column — so Kotlin's step order is first-sync document order and is
*not* stable under an edit. The port's `stepIndex` ordering is deterministic
where Kotlin's is not. The comment is corrected.

## Job 1 — the per-step Next lock

`stepNextLockProvider` (`courses_providers.dart`) plus `_nextStepLock` on
`_CourseContentState`. The lock reads **presence**, not submission-existence,
and that distinction is the feature: `changeNextButtonState:320-321` locally
shadows the names `hasExam`/`hasSurvey` with `stepExams.isNotEmpty()` /
`stepSurvey.isNotEmpty()`, where the identically-named `CourseStepData` fields
are `hasSubmission(...)`. Building the lock on those inverts it — only a learner
who had already answered would be blocked. **Confirmed material by the audit**,
and mutating presence → `assessment.hasExam` reds five tests.

The lists come off `stepAssessmentProvider` rather than a fresh query, so the
lock and the tile cannot disagree about what the step carries **and** the tile's
post-return `invalidate` refreshes both. That is not an optimisation. The audit
flagged it independently: Kotlin re-evaluates the lock only on a page-selection
event (`onPageSelected:309`; `onResume` does *not* call
`changeNextButtonState`) and stays correct anyway because `openCallFragment`'s
`replace()` destroys the fragment's view, so returning from the exam
re-dispatches `onPageSelected`. A `context.push` leaves the screen mounted, so
an unshared read would hold a step the learner had just finished. Giving the
lock its own step read reds exactly the return-trip test.

Faithful quirks kept deliberately:

* **The button stays enabled** and shows a `Snackbar`; `isNextStepLocked` is
  read in `onClick` (`:367-370`), which returns before advancing. Kotlin never
  touches `isEnabled`.
* **A loading or rejected read is unlocked.** `getCourseStepData` throws on a
  missing step row and `changeNextButtonState`'s `lifecycleScope.launch` has no
  `try`, so Kotlin's flag keeps the `false` that `onPageSelected:306` had just
  assigned. Reading `.valueOrNull` gives Kotlin's outcome without Kotlin's
  crash, and the same mapping reproduces Kotlin's own race (`:306` clears the
  flag synchronously, `:318` sets it from a coroutine, so a fast tap escapes).
* **`please_complete_survey`'s wording is written for the finish gate**
  ("…to finish the course") because Kotlin shares one string between the two
  sites. Pinned so nobody mints a second key.

`viewPager2.isUserInputEnabled = false` (`:137`) is ported alongside, and it is
load-bearing rather than cosmetic: a swipe reaches `onPageChanged` directly, so
while the `PageView` scrolled the lock could be dragged past. The audit
confirmed there is no *other* bypass — the SeekBar that looks like one sits
inside `ll_progress`, which is `gone` in the layout and never made visible by
any code, so its `onProgressChanged` jump is dead UI.

## The defect this lane did not go looking for

**Previous and Next moved the step counter and left the page where it was.**

The `PageController` was built inline in `build` as
`PageController(initialPage: currentStep)`. `Scrollable` hands a replacement
controller the existing `ScrollPosition`'s pixels
(`createScrollPosition(..., oldPosition: _position)`), so `initialPage` is read
once at first attach and ignored on every rebuild after it. Tapping Next
advanced `_currentStep` — so `_ProgressSection` read `2 / 2` and the button set
switched to Finish — while the pager still showed step 1.

**Why nothing caught it:** every navigation assertion in the suite read the
counter, which is driven by the same `currentStep` the controller was being
handed. The two agreed about the *number* while disagreeing about what was on
screen. It surfaced only because a `stepNum` test tapped the *second* step's
assessment tile and could not find it; a probe of the rendered `Text` widgets
showed `[Step 2, 2 / 2, 1, First, …]` — the counter on step 2, the content on
step 1.

It had to be fixed here rather than reported, because Job 1 makes it worse:
with `isUserInputEnabled = false` ported, the buttons are the *only* way through
a course, so a lock guarding a button that does not navigate would have been the
only working half of the pair — and a learner would have had no way to advance
at all. The controller now lives in the state, `goToStep` drives it with
`animateToPage` (matching `viewPager2.currentItem += 1`, whose ViewPager2
default is a smooth scroll), and `onPageChanged` only reports the landing rather
than re-driving the controller from inside its own completion.

Two tests assert on the *page* rather than the counter; reverting the fix reds
both, plus the second-step `stepNum` test.

## Job 2 — where a finished survey leaves the learner

`TakeSurveyScreen._submit` ended with `context.go('${Routes.submissions}/$id')`.
**That is a port invention with no Kotlin counterpart on any entry point** — no
Kotlin path shows the learner their own submission after finishing one; the only
openers of `SubmissionDetailFragment` are list-row taps.

`BaseExamFragment.continueExam:132-148` ends a non-team survey with a thank-you
dialog whose Finish calls `FragmentNavigator.popBackStack`. All five
`openSurvey` call sites reach it, four hardcoding `isTeam = false`
(`SubmissionsAdapter:98`, `CourseStepFragment:289`, `BellDashboardFragment:256`,
`DashboardActivity:220`) and the fifth (`SurveysAdapter:88`) passing a variable.
So the individual list, the my-surveys row, the dashboard prompt, a deep link
and a course step all pop.

**A pop is what makes one exit right for all of them**: each was reached by a
`context.push`, so each lands back on its own caller. The brief expected these
entry points to want different destinations; they do not — they want the same
*rule*, which resolves to a different screen per caller by construction.

The course step is the entry that was actually broken: `go` unmounted
`TakeCourseScreen` outright, so the tile's `await context.push(…)` never
resumed and Phase 128's *redo survey* relabel — whose only trigger is a
submission made on this screen — was unreachable on the one path that produces
it. The round trip is now tested with the **real** `TakeSurveyScreen` as the
push target: the existing test that looks like it covers this path stubs the
route with a `Text` widget, so the real screen's exit had never been exercised
by anything.

Three corrections the audit made to my plan, all applied:

* **The thank-you string is composed**, not a single key:
  `"${thank_you_for_taking_this}$type! ${we_wish_you_all_the_best}"` (`:135`) →
  *"Thank you for taking this survey! We wish you all the best."* No single
  Kotlin string matches that, so a new ARB key for it could derive nothing from
  `values-*/strings.xml` and would render English in all five locales.
  `thankYouForTakingSurvey` is one sentence shorter and has a **reviewed human
  translation in every locale**. Five translations beat four extra words;
  recorded as a deviation.
* **`popBackStack` is a no-op on an empty back stack** (`FragmentNavigator
  .kt:55`), so with nothing to pop the new exit does nothing rather than
  inventing a destination.
* **The team branch's existing `go(Routes.surveys)` fallback cites dead code.**
  It claimed to mirror `navigateToSurveyList`, which is unreachable:
  `isFromNation` has no Kotlin writer that can make it true
  (`insertCourseStepsExams` sets it from a `parentId` its only caller passes as
  `""`). Behaviour left alone; the comment now says what it is.

One existing test changed: `survey_team_context_test.dart`'s *"a personal survey
goes straight to the submission"*. Its point was that a personal sheet must not
acquire the profile step, and the `SUBMISSION_PAGE` assertion was incidental to
that; it now asserts the thank-you dialog instead.

**And a finding about the existing tests in `take_survey_screen_test.dart`:**
every submit in that file was landing in `_submit`'s **failure** branch. The
real `serverConfigProvider` reaches `planetPrefs`, which is `UnimplementedError`
in the harness, and `_submit` reads it *inside* its own `try` — so the screen
wrote the row and then showed "Could not save your answers". The tests assert
only on the stored row, so they never saw it, which is how the screen's whole
exit path stayed untested through four phases. `survey_team_context_test.dart`
had already found and documented this for its own harness; the note had not
travelled. Both harnesses in the file now override it.

## Job 3 — the explicit step number

Kotlin never derives the number: `CoursesPagerAdapter.kt:49` stamps the pager
position on the step fragment as `"stepNumber"`, `CourseStepFragment.kt:280`
forwards it to the exam as `"stepNum"` (**the key is renamed across the
boundary**, which is why no other entry point carries one), and
`BaseExamFragment.kt:75` reads it back with a default of 0.

Both port pushers now send it — `take_course_screen`'s step tile from the
`stepNumber` it already drew in the step header, and `course_detail_screen`'s
tile from its own `number` — the router parses it with `int.tryParse`, and
`TakeExamScreen.stepNum` prefers it over the derivation. So the number written
to `course_progress` is the one the learner saw, and it cannot disagree with
`take_course_screen._recordProgress`'s `stepNum: index + 1`.

The derivation stays as the fallback, because the route's query parameters are
optional and a location without a `stepNum` must still work — though with both
pushers sending it, that branch now has no production caller. A non-positive
value is treated as absent: `0` is Kotlin's missing-argument default and
`WHERE stepNum = 0` is precisely the value its own update matches nothing with.

**The argument for passing it is one materialisation versus two queries, and
my first version of that argument was unsound.** I wrote that the derivation
rests on `stepIndex`, whose column default of `0` makes ties legal. It does
not: `CourseMapper._parseSteps` is the only writer of `course_steps` and always
sets `stepIndex: Value(i)`, so no tie is reachable — and if one were,
`watchSteps` and `getSteps` are byte-identical queries, so it would corrupt the
*passed* number just as badly, because that ordering is where the tile's number
comes from. The sound argument is the other one: the tile's rendered number,
`_recordProgress`'s `index + 1` and the pushed `stepNum` all index the same
`widget.steps` list, while the derivation issues a fresh query and re-finds the
step by id. Corrected at the code too. Relatedly, my claim that the old comment
"used the *same* ordering as Kotlin" misread it — it was a port-to-port claim
about `getSteps` versus `_recordProgress`, and a true one.

**What passing it explicitly buys, concretely:** the derivation had to resolve
the *step id* in a re-query first, so a step id the course does not list — a
step row that has not synced, or one re-bound by a reorder — skipped the write
entirely. Kotlin never consults the step table here at all. Tested.

**What it does not buy, stated because my `stepNum` work sits on top of it.**
Phase 135 item 5: a course-step reorder re-binds the port's step ids to
different content, because the port mints `<courseId>:<index>` while Kotlin's id
is content-derived (`Base64(stepElement.toString())`, `CoursesRepositoryImpl
.kt:681`). Passing `stepNum` explicitly does **not** fix that and is not offered
as a fix — the number is still the position the learner navigated from, so after
a reorder it names the new content at that position while older progress rows
name the old. What it removes is only the extra hop: a reorder can no longer
make the write miss the row entirely rather than merely describe the wrong step.
Fixing the underlying divergence means changing how `CourseMapper` mints step
ids, which is a mapper and migration question.

## Files touched

| file | change |
|---|---|
| `lib/providers/courses_providers.dart` | `StepNextLock`, `stepNextLockProvider`, `_isStepCompleted` |
| `lib/ui/courses/take_course_screen.dart` | the lock wiring, the owned `PageController`, `NeverScrollableScrollPhysics`, `&stepNum=` |
| `lib/ui/courses/course_detail_screen.dart` | `&stepNum=` |
| `lib/ui/surveys/take_survey_screen.dart` | `_thankAndLeave` replaces the `go` |
| `lib/ui/router.dart` | parses `stepNum` |
| `lib/ui/exam/take_exam_screen.dart` | **outside the brief's file set — see below** |
| `lib/l10n/app_en.arb` | `pleaseCompleteTest`, additions only |
| `test/ui/courses/step_next_lock_test.dart` | new, 15 tests |
| `test/ui/courses/step_num_argument_test.dart` | new, 6 tests |
| `test/ui/surveys/take_survey_screen_test.dart` | +4 tests, and the harness fix above |
| `test/ui/courses/take_course_screen_test.dart` | +1 round-trip test |
| `test/ui/exam/take_exam_screen_test.dart` | +3 tests, `pumpExam` gains `stepNum` |
| `test/ui/surveys/survey_team_context_test.dart` | one existing test's destination assertion |

**`lib/ui/exam/take_exam_screen.dart` is outside the file set the brief listed**,
and it is named here rather than left to be noticed in the diff. Job 3 cannot
land without it: the router has nowhere to put the parsed `stepNum` unless
`TakeExamScreen` accepts one, so passing the number explicitly would have been a
push nothing read — the writer/reader shape this project has paid for four
times. No other lane owns the file (Lane A has the repositories and
`app_providers`, Lane C the chat and voices, Lane D the locale files). The
change is three things: the constructor parameter, `_resolveStepNum` preferring
it, and two stale doc claims corrected.

## Integrator: one command, and a count

`lib/l10n/app_en.arb` gains **one** key, `pleaseCompleteTest`, worded as the
exact literal from `values/strings.xml` — *"please complete the test to
proceed"* — so `tool/arb_from_strings_xml.dart` can derive it. **All five
`values-*/strings.xml` carry `please_complete_test` as a human translation
already shipping in the Android app**, so:

**Run `dart run tool/arb_from_strings_xml.dart` (Lane D's file) and bump
`humanReviewed` in `test/l10n/placeholder_integrity_test.dart` by +1 per
locale: ar 418→419, es 470→471, fr 417→418, ne 419→420, so 419→420.**

Until that runs, an Arabic, Spanish, French, Nepali or Somali learner sees
`please complete the test to proceed` in English on a screen where the Android
app has always shown their own language. I did not run it myself because Lane D
owns those five files and the tool this round. Phase 128 left the identical
hand-off; this is one key rather than five.

## Mutation testing

Eighteen reverts, each replayed against the suite. Every one reds its intended
test, and nine red exactly one. The last two close the gap the second audit
pass found.

| # | mutation | reds |
|---|---|---|
| 1 | the lock never fires | 6 tests |
| 2 | presence → `hasSubmission` (the inversion) | 5 tests |
| 3 | drop `status != 'pending'` | *"a half-answered attempt still blocks"* |
| 4 | drop the user scoping | *"another learner's finished attempt…"* |
| 5 | drop the course-id gate | *"any other course is never locked"* |
| 6 | drop `NeverScrollableScrollPhysics` | *"the lock cannot be swiped past"* |
| 7 | `blockedByTest` always false | 5 tests |
| 8 | drop the membership gate | *"a learner who has not joined is not locked"* |
| 9 | give the lock its own step read | *"the lock releases on returning from the exam"* |
| 10 | `contains` → `==` on `parentId` | 3 tests |
| 11 | restore `go('${Routes.submissions}/$id')` | 6 tests, across three files |
| 12 | restore the inline `PageController` | 3 tests |
| 13 | drop `&stepNum=` from the step tile | 2 tests |
| 14 | drop `&stepNum=` from the detail tile | *"the second tile sends 2"* |
| 15 | router stops parsing `stepNum` | the router pair test |
| 16 | `_resolveStepNum` ignores the passed value | 2 tests |
| 17 | the lock reads `steps[0]` rather than the displayed step | *"the lock reads the displayed step"* |
| 18 | the lock reads `steps[currentStep + 1]` | 4 tests |

**Three tests of mine could not fail for the reason their names gave.** Two I
found; the third the implementation audit found, and it was the most important
of the three.

*The audit's:* nothing pinned which step the lock reads.
`_nextStepLock` takes `steps[currentStep]`, Kotlin takes
`steps.getOrNull(position - 1)` — and replacing it with `steps[0]` left the
**entire suite green**. Every fixture here put the assessment on step 1 of a
two-step course, and the lock is only ever *consulted* at index 0: at index 1
`onNext` is null and `_NavigationBar` renders Finish, so the value computed for
the last step is discarded. Nothing distinguished `currentStep` from `0`,
`currentStep + 1` or `currentStep - 1`. Closed by a three-step course with the
exam on the **middle** step, where the two readings disagree in both directions
— reading `steps[0]` lets the learner off the locked step *and* blocks them on
the unlocked one. `steps[0]` now reds exactly that test; `steps[currentStep+1]`
reds four.

*Mine, and the reason I recorded for them was wrong.* The first cut of the
second-step `stepNum` test tapped a tile belonging to step 1 and reported
`stepNum=1` for step 2, and I attributed it to `find.text` reaching an
off-screen `PageView` page. **It does not reach one:** `PageView`'s
`cacheExtent` is `allowImplicitScrolling ? 1.0 : 0.0` and that flag defaults
false, so a settled `PageView` has only the visible page in the element tree —
which is what the `findsNothing` assertion in *Next moves the page* depends on.
The real cause was the `PageController` defect this phase then fixed: the page
never moved, so step 1's tile was the only one built. The wrong reason was
recorded in three places and is corrected in all three, because it would have
told the next lane that page assertions are unreliable when they are the
strongest assertions in the file — and a title assertion is exactly what would
have caught the controller defect a round earlier.

One harness note worth carrying forward: **`pumpAndSettle` does not clear a
`SnackBar`.** It returns as soon as no frame is scheduled, and the four-second
dismiss timer needs wall-clock time pumped — so a second tap on a bottom
navigation button lands on the SnackBar, is silently swallowed (only a
`warnIfMissed` line in the log), and the test reads as "the lock never
released". `letSnackBarExpire` exists for that and says so.

## Reported, not fixed

1. **`SubmissionDao` wants `countCompletedByUserAndExamId`.** `_isStepCompleted`
   reads one user's submissions of one type and filters in Dart, where Kotlin
   issues `SELECT COUNT(*) FROM submissions WHERE userId IS :userId AND parentId
   LIKE '%' || :examId || '%' AND status != 'pending'` (`SubmissionDao.kt:24`).
   The set is bounded, so this is not a performance defect — it is a layering
   one: every other query in the port lives in a DAO, and adding this one means
   editing `app_database.dart`, which Lane A owns this round. **The method to
   add**, for whoever takes it:
   ```dart
   Future<int> countCompletedByUserAndExamId(String? userId, String examId)
   ```
   with `userId` matched null-safely (Kotlin's `IS`),
   `parentId.like('%$examId%')`, `status.equals('pending').not()`, and **no
   `type` predicate** — Kotlin's count has none, where `_isStepCompleted` picks
   its table by type, so adopting this closes a divergence rather than
   preserving one. A NULL status must not count, which SQL gives for free and
   Dart does not.
   **Do not escape the `LIKE` pattern**, which an earlier draft of this item
   advised: Kotlin interpolates the raw exam id and escaping would make the
   port *stricter* than Kotlin, the opposite of the looseness the `contains`
   was chosen to preserve. Two axes the `contains` gets wrong in the other
   direction, and which the DAO version would fix for free: SQLite's `LIKE` is
   case-insensitive for ASCII, and it treats `%`/`_` in the pattern as
   wildcards. All three are unreachable while `parentId` is minted from the
   same `exam.id` through `examParentId`.
2. **`_NavigationBar` shows Next and Previous to a non-member**, where
   `updateNavigationVisibility:267-270` hides both. Porting that would remove
   this phase's need for a membership gate on the lock, and it is a one-line
   change to `_NavigationBar`. Left alone because it takes a capability away
   from a browsing learner and is a behavioural call beyond this brief.
   **Do not port it as "a non-member cannot page through a course at all"** —
   an earlier draft of this item said exactly that, contradicting this file's
   own §*One claim was simply wrong* two screens up, which is the half that is
   right: `onResume:146-162` puts Next back with no membership test, and
   `steps` is a fragment field that survives view destruction, so a non-member
   who leaves and returns *does* get Next. Kotlin's rule is stricter on the
   first open than on any later one. Note also that the else branch does not
   touch `finishStep`, so a faithful port has to decide what a non-member sees
   on the last step.
3. **`_ProgressSection` labels step 1 "Course Details".** It renders
   `currentStep == 0 ? l10n.courseDetails : l10n.stepNumber(currentStep + 1)`,
   a fossil of Kotlin's pager where position 0 *is* the course cover. The port's
   `PageView` has no cover page, so index 0 is a real step and is mislabelled —
   and the label is off by one against `stepNumber` for every other step. Not
   touched because it is display-only and outside all three jobs, but it is in
   the file this phase edited most.
4. **The resources tile still has a chevron that does nothing** — Phase 128's
   item 1, unchanged, in both `take_course_screen` and `course_detail_screen`.
5. **`getExamQuestionCount` shares `isStepCompleted`'s untyped first-row pick.**
   `CoursesStepsAdapter.kt:52-56` renders `R.string.test_size`
   (`:54`) with `step.questionCount`, which is `examDao.getFirstByStepId(stepId)?.noOfQuestions`
   (`SubmissionsRepositoryImpl.kt:162-164`) — so Kotlin's step tile can show a
   *survey's* question count under a "Test:" label. The port's course-detail
   tile shows `resourcesInStep` instead and has no analogue, so there is nothing
   to fix today; recorded because anyone porting that count will meet the same
   row-pick question this phase answered for the lock.
6. **`computeParentId()` is wrong on the step-exam path in Kotlin and inert.**
   `ExamTakingFragment.kt:90-96` yields `"@<courseId>"` there, because
   `checkId()` leaves `id = ""` whenever `stepId` is non-empty. Never used on
   that path — the pending lookup is gated on `type != "exam"` and
   `createExamSubmission` recomputes `parentId` from the exam itself. It becomes
   live for any port that derives its parent id at the call site instead of in
   the writer; the port derives it in the writer, so this is a "do not change
   that" note.
7. **`isValidClickRight` is off by one** (`TakeCourseFragment.kt:487`):
   `currentItem < itemCount` should be `< itemCount - 1`. Unreachable today
   because Next is hidden on the last page. Named because this phase ported the
   surrounding click handler and deliberately did not carry the bug.
8. **Phase 135 items 5, 7, 9, 10 and 11 are all still open**, and item 9 —
   `_recordProgress` writing neither `planetCode` nor `parentCode`, so every
   locally-authored progress row reaches CouchDB with both null — is in the file
   this phase edited. It stayed out because the values come from the session
   user and threading them through is a change to the screen's provider reads
   rather than to any of these three jobs.
9. **The port does not resume a course at the learner's saved step.**
   `_TakeCourseScreenState._currentStep` starts at 0 and nothing reads the
   saved progress, where Kotlin opens at
   `position = if (lastPositionBeforeExam > 0) … else if (currentStep > 0)
   currentStep else 0` from `progressMap[courseId]?.current`
   (`TakeCourseFragment.kt:104`, `:116`). Surfaced by the audit while checking
   this phase's `PageController`, whose `initialPage` is therefore always 0.
   Worth knowing that it also narrows the membership-gate rationale above: the
   Kotlin path on which a returning non-member meets the lock depends on that
   restored position, which the port has no equivalent of.
10. **`TakeCourseScreen.build`'s clamp moves the index without driving the
    controller.** When the step list shrinks it assigns `_currentStep` during
    `build` and never calls `animateToPage` — the same shape this phase just
    fixed 250 lines below. The scroll position self-corrects at layout, so the
    page lands right; it is one line and it is pre-existing.
11. **`_resolveStepNum`'s derivation branch is now test-only.** Both builders
    of `/courses/exam/` send `stepNum`, so nothing in `lib/` reaches the
    fallback. Kept deliberately as a defensive path for a location typed
    without the parameter; named so nobody reads its coverage as proof it is
    live.
12. **The `isFromNation` team arm now ends in the thank-you dialog and a pop,
    where Kotlin toasts and replaces with the survey list**
    (`BaseExamFragment.kt:156-163`). That branch is dead in Kotlin — its only
    writer derives `isFromNation` from a `parentId` both callers pass as `""` —
    but it is **live in the port**, because `SurveyMapper` reads the field
    straight off the server document, so "dead code" is not a reason to ignore
    it here. I gave the path the same exit as every other non-team survey
    rather than reproducing an unexercised Kotlin branch; the other view is
    defensible and this is where to argue it.
13. **`barrierDismissible: false` is not `setCancelable(false)`.** Kotlin's
    thank-you dialog also swallows the back button. No behavioural difference
    today, since either exit reaches the same pop, and `PopScope` is the
    literal port if the pop ever becomes conditional on how the dialog closed.
14. **The port's `Exams` table has no `type` column**, which is what makes the
   superset described above unavoidable. Adding one would let the port
   distinguish Kotlin's `"courses"` from its `"exam"` fallback and reproduce
   both the missing button and the wedged step — neither of which is desirable,
   so this is recorded as a reason the deviation is permanent rather than as
   work.
