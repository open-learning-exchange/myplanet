# Phase 145 — the step label, the dead chevron, and the non-member navigation question

Lane D of a four-lane round. Three jobs, all from previous rounds'
*Reported, not fixed* lists — the seventh consecutive round where that list was
the highest-yield source of work:

1. `_ProgressSection` labels step 1 "Course Details" (Phase 139 item 3).
2. The step's resource count has a chevron with no `onTap` (Phase 128 item 1,
   restated as Phase 139 item 4).
3. `_NavigationBar` shows Next and Previous to a non-member where
   `updateNavigationVisibility:256-270` hides both (Phase 139 item 2).

## Method

1. Read the Kotlin by hand: `TakeCourseFragment`, `CoursesPagerAdapter`,
   `CourseStepFragment`, `CourseDetailFragment`, `CoursesStepsAdapter`,
   `BaseContainerFragment.setResourceButton`, `CoursesRepositoryImpl`'s course
   parse and its `pendingCourseResources` drain, plus the three layouts.
2. A `parity-auditor` pass at `effort: max` on **14 lettered claims** about that
   reading, before touching Dart. It confirmed eleven, corrected two and
   **refuted one outright** — the refuted one had already been written into a
   test file as a rationale, which is exactly why the pass runs first.
3. Implement, each defect demonstrated failing first.
4. Mutation-test every claim.
5. A second `parity-auditor` pass at `effort: max` aimed at the finished,
   already-green code.

## What the ground-truth audit changed

### The brief was wrong about Job 1, and the shape of the error is familiar

The brief said the label "is off by one against `stepNumber` for every other
step". **It is not**, and this is the *"a brief propagates the previous round's
mistakes as efficiently as its facts"* pattern again.

Kotlin's pager **does** have a cover page. `CoursesPagerAdapter.getItemCount()`
is `steps.size + 1`, `createFragment(0)` builds a `CourseDetailFragment` and
`createFragment(p > 0)` builds the step at `steps[p - 1]` with
`stepNumber = p` (`:40-60`); `getItemId(0)` is even a distinct sentinel,
`COURSE_DETAIL_ID = 0L`. So Kotlin position `p ≥ 1` shows step index `p - 1`
and `updateStepDisplay` reads `step p/N`.

The port has no cover page — it reaches the course description through its own
`CourseDetailScreen` route — so **port index `i` is Kotlin position `i + 1`**,
and `l10n.stepNumber(currentStep + 1)` is right at every index. Checked in both
directions: at `i = N-1` Kotlin is at position `N` and reads `step N/N`, the
port reads `Step N`. The counter and the progress bar already agreed too.

**Only the `== 0` branch was ever wrong**, and it was wrong under *either*
premise: with a cover page, index 1 would be step 1 and `stepNumber(2)` would be
the off-by-one the brief describes; without one, index 0 is a real step wearing
the cover page's heading. The fix is to delete the branch, not to renumber, and
the tests are written so that "fixing" the `else` to `currentStep` reds them —
because that would have renumbered every step in the app.

Worth knowing while you are there: `"Course Details"` is a bare literal in
`TakeCourseFragment.kt:194` with **no `strings.xml` key**, untranslated in all
six Kotlin locales. The port's `l10n.courseDetails` was a port-only string and
is now an orphan key (see *Integrator*).

### Job 2's live Kotlin counterpart is not the one two rounds of notes named

Phase 128 named `BaseContainerFragment.setResourceButton`, reached from
`CourseDetailFragment`, and Phase 139 repeated it. That button is live, but it
is **course-level**: `state.resources` ← `CourseDetailModel.resources` ←
`getCourseOnlineResources(courseId)` → `MyLibraryDao`'s
`WHERE courseId = :courseId`. It is not a step's resources, and its click opens
the multi-select download sheet rather than a viewer.

The per-step Kotlin UI **is not a button at all**. `CourseStepFragment
.setupInlineResources()` (`:176-194`) shows `tvResourcesHeader` and binds
`rvInlineResources` to an `InlineResourceAdapter` whose rows call
`openResource(library)`, with `autoDownloadResources()` (`:196-213`) and
`prefetchNextStepResources()` (`:215-230`) alongside. `btnResources` is dead
twice over exactly as Phase 128 said — no listener is ever attached, and
`setListeners` sets it GONE at `:292` — and the audit found the container too:
`legacy_buttons_container` in `fragment_course_step.xml:14`, `visibility="gone"`
at `:20`, referenced from no Kotlin. `btn_open` in the same container is dead
with it.

**So Phase 128's conclusion survives its own citation being wrong**, which is
worth saying plainly: there is no live per-step *count* to diverge from, and
also no live per-step *navigation*. What there is instead is a list the port
cannot render.

### Why the port cannot render it: a missing sync walk, not a missing widget

Kotlin knows a step's resources because the courses walk writes them.
`parseCourse` calls `queueCourseResources(courseId, stepId, …)` per step
(`CoursesRepositoryImpl.kt:687`), buffering each embedded resource **document**
(`:792-798`); `flushPendingCourseResources` (`:819-851`) upserts them into
`my_library` through `MyLibrary.insertMyLibrary`, and `getAllStepResources`
reads them back with `myLibraryDao.getByStepId`.

`CourseMapper._parseSteps` keeps `resources.length` as `noOfResources` and
**discards the documents**. `MyLibraryTable` has no `stepId` column — and, the
audit corrected me here, **no `courseId` column either**, which my first reading
of `tables.dart` got wrong by matching a `courseId` line belonging to the next
table. So the number on the tile describes rows the port does not hold, on
either axis.

That is a Phase 119-class finding: a walk Kotlin runs that the port never had,
found by asking Job 2's third reachability question — *does anything navigate
here?* — and getting "there is nowhere to navigate to". Removing the chevron is
the whole of the fix available in this lane's files; the walk is reported below.

### Job 3: one claim refuted, and it was already written into a test

I claimed a resumed non-member gets Next back for **exactly one tap**, because
the resulting `onPageSelected` would call `updateNavigationVisibility` and hide
it again. **Refuted.** `viewPager2.currentItem += 1` dispatches `onPageSelected`
*synchronously* — `setCurrentItemInternal` → `ScrollEventAdapter
.notifyProgrammaticScroll` → `dispatchSelected(target)` on a changed target — so
`updateNavigationVisibility` runs **inside** line `:372`, and `:373`
(`previousStep` VISIBLE) and `:375` → `onClickNext():347` (`nextStep` VISIBLE)
then undo it. The non-member ends the gesture with *both* buttons and keeps
them.

The androidx source is not in this repository, so that half is the auditor's
citation rather than something I verified here. What **is** checkable locally
corroborates the ordering it depends on: `onClickNext()` is called *after* the
increment and reads `viewPager2.currentItem` (`:342`), while `onClickPrevious()`
is called *before* the decrement and reads `currentItem - 1` (`:353`) — both
correct only if the item index updates synchronously inside the setter, which is
the same statement that precedes `notifyProgrammaticScroll`.

Two further corrections from the same pass:

* **The restore is not exam-specific.** `onPause:164-169` stamps
  `lastPositionBeforeExam` on *every* pause, so backgrounding the app is enough.
  And when the saved position is `0` the `> 0` test fails and it falls through
  to `currentItem`, still reaching the `else` at `:157-160` — so a non-member
  sitting on the cover page gets Next after a home-button round trip.
* **`steps` can be initialised before a first `onResume` after all.** My claim
  that it cannot was right only for a cold open. `collectLatestWhenStarted` is
  `repeatOnLifecycle(STARTED)` on `Dispatchers.Main.immediate` and the ViewModel
  survives configuration change, so after a rotation or a back-stack pop the
  `Success` state is delivered during `onStart` and `bindCourse` runs before
  that instance's `onResume`.

Net: **in the shipping Android app the non-member gate holds on a cold open and
not afterwards.**

## Job 1 — the heading

`_ProgressSection` renders `l10n.stepNumber(currentStep + 1)` unconditionally.
Three tests, each pairing the heading with the **title of the page actually
rendered** — the Phase 139 lesson, since the heading and the counter are both
driven by `currentStep` and agreed with each other while disagreeing with the
`PageView` for four phases.

The one-step case is the one to keep in mind: with a single step, index 0 is the
only page there is, so `Course Details` was the only heading that course ever
showed.

## Job 2 — the chevron

`trailing: const Icon(Icons.chevron_right)` removed from the resource-count
tile. The count stays: it is true (it is the length of the step document's
`resources` array) and `CourseStepFragment` shows the same number. The two tiles
below keep their chevrons, because those two navigate.

**Phase 128's item said the chevron was in `course_detail_screen.dart` too and
Phase 139 repeated it. It is not, and was not** — `git log --all -S chevron --
flutter/lib/ui/courses/course_detail_screen.dart` returns nothing, so no commit
on any branch ever added or removed one there. `_StepTile` is an `ExpansionTile`
whose count is a subtitle and whose trailing arrow expands the description — an
honest affordance. Pinned by a test rather than left as a second unchecked
claim.

## Job 3 — the non-member gate, ported

`_NavigationBar` gains `isMyCourse` gates on Next and Previous, and the copy
Phase 139 had put on `_nextStepLock` is **removed**, so that method now reads
like `changeNextButtonState`, which has no membership test of its own. One
documented deviation collapses into fidelity.

**The decision, stated as a decision.** Both options deviate from something: the
gate is stricter than the shipping app in every session after the first resume,
and no gate contradicts the only place Kotlin states a rule. Ported, because

* `updateNavigationVisibility` is the only site expressing an intent, while all
  three writes that undo it are doing something else — restoring after a
  lifecycle event, or swapping Next for Finish at the end of the list — and each
  is a **partial copy of the member branch written without its `else`**;
* the same method that hides the buttons puts a *do you want to join this
  course?* dialog in front of a non-member (`setCourseData:219-229`), which is a
  design statement about whose screen this is;
* nothing is actually lost in the port: `CourseDetailScreen`, the screen a
  learner arrives from, lists every step with its description and its exam
  button, and the join button sits in this very bar;
* reproducing the escape would mean modelling Android's fragment lifecycle, so a
  Flutter learner's navigation would depend on whether they had been away.

**What a non-member sees on the last step is Finish, and that is Kotlin's answer
rather than a choice.** The `else` branch never touches `finishStep`, and
`bindCourse` has already run `setNavigationButtons()` (`:118`, `:464-473`),
which shows Finish exactly when `position >= steps.size`. In Kotlin that state
is reached by an **ex-member**: progress rows survive leaving a course
(`leaveCourses` does not touch `course_progress`) and `position` is restored
from them (`:104`, `:116`). The port has no resume-at-saved-step (Phase 139
item 9), so it reaches the same state on a **one-step course**, where index 0 is
also the last step — which is what the test drives, and what the pre-existing
finish/rating tests in `take_course_screen_test.dart` were already relying on
without anyone noticing they used a non-member course row.

**The Previous gate is pinned by a real path, not a contrived one.** A
non-member can never advance, so Previous is unreachable for them by
navigation — which would have made its gate dead code. It is observable the
other way round: a member on step 2 taps *Remove from my courses*, and both
buttons go. Kotlin does the same, `addRemoveCourse`'s success path calling
`setCourseData()` → `updateNavigationVisibility()` (`:445`, `:237`).

### The guest button, found by the audit and fixed here

`setCourseData:216-232` shows `btnRemove` for `!isGuest && !containsUserId`
only. The port offered the join/leave button unconditionally, and for a guest
that was worse than inert: `_toggleMembership` bails only on a null `userId`,
and the guest row has one, so the tap **wrote a shelf membership Kotlin does not
allow a guest to write**. `canChangeMembership` now gates it on
`UserMapper.isGuest`. This is Job 2's principle applied to a second affordance
in the same widget — with the gate on Next, that button is a non-member's only
control, so its own gating stopped being incidental.

`UserMapper.isGuest(user)` rather than `isGuestId(widget.userId)`: the former
tests both id columns, which its own doc calls a deliberate widening.

**The `!containsUserId` half is deliberately not ported.** Kotlin hides this
button from *members* too — `TakeCourseFragment` has no leave affordance at
all — where the port keeps one, as `CourseDetailScreen` does. That is a port
addition rather than a defect, and removing a working capability is a decision
beyond this brief. Named rather than folded in.

## Files touched

| file | change |
|---|---|
| `lib/ui/courses/take_course_screen.dart` | the heading, the chevron, the nav gate, `canChangeMembership`, `_nextStepLock`'s gate removed |
| `lib/ui/courses/course_detail_screen.dart` | **none** — see Job 2 |
| `test/ui/courses/step_label_test.dart` | new, 3 tests |
| `test/ui/courses/step_resources_tile_test.dart` | new, 3 tests |
| `test/ui/courses/non_member_navigation_test.dart` | new, 5 tests |
| `test/ui/courses/step_next_lock_test.dart` | one existing test reshaped — see below |
| `lib/l10n/app_en.arb` | **none** — no key added |

`course_detail_screen.dart` is in the file set and is deliberately unchanged.
Both things Job 2 sent me to look at there turned out to be absent (the chevron)
or unportable (the resource list). The one real gap the audit surfaced in it is
reported below rather than fixed, because it is a display decision beyond the
three jobs.

**One existing test changed shape**, `step_next_lock_test.dart`'s *a learner who
has not joined is not locked*. It tapped Next and asserted the page moved,
pinning the membership test Phase 139 had put on the lock. With the gate moved
to the button there is no Next to tap, so it now asserts the **absence of
Next** — which is what keeps it able to fail: re-adding a gate to `_nextStepLock`
leaves it green (the lock is unreachable on that path either way), while
dropping the gate in `_NavigationBar` reds it immediately.

## Mutation testing

Every fix reverted, every test replayed.

| # | mutation | reds |
|---|---|---|
| 1 | restore `currentStep == 0 ? l10n.courseDetails : …` | 2 tests |
| 2 | `stepNumber(currentStep + 1)` → `stepNumber(currentStep)` | 3 tests |
| 3 | restore the resource tile's `trailing:` chevron | *the resource count offers no chevron to tap* |
| 4 | drop the `isMyCourse` gate on Next | 4 tests, across two files |
| 5 | drop the `isMyCourse` gate on Previous | *leaving the course part-way…* |
| 6 | Finish gated on `isMyCourse` too | *a non-member on the last step still sees Finish*, plus 3 pre-existing finish/rating tests in `take_course_screen_test.dart` |
| 7 | drop `canChangeMembership` on the join/leave button | *a guest gets no navigation and no join button* |
| 8 | re-add the membership gate to `_nextStepLock` | **nothing** — deliberately; see above |

Mutation 8 is recorded as a **non-red on purpose**. The lock's gate is
unreachable once the button is gated, so nothing can pin it; that is the reason
it was removed rather than kept as belt-and-braces, and stating it here is
cheaper than the next lane rediscovering that an untestable line exists.

## Integrator

**Nothing to run.** No ARB key is added and no locale file is touched, so unlike
Phases 128 and 139 there is no derivation hand-off.

One thing to know: **`courseDetails` is now an orphan template key.** Nothing in
`lib/` reads it after this diff. It carries real ar/es/fr translations, and
deleting it means editing all five locale files plus `humanReviewed` in
`test/l10n/placeholder_integrity_test.dart` — outside this lane's file set.
Phase 128 left `takeTest` in the same state; this makes two.

## Reported, not fixed

1. **The port never ingests a course step's resources**, so `noOfResources` is a
   count of rows it does not hold. Kotlin's courses walk buffers each embedded
   resource document (`queueCourseResources`, `CoursesRepositoryImpl.kt:687`,
   `:792-798`) and drains it into `my_library` stamped with `courseId` and
   `stepId` (`flushPendingCourseResources`, `:819-851`, run from
   `TransactionSyncManager.kt:302`); `CourseMapper._parseSteps` keeps only the
   length. **This is the item behind Job 2** and it blocks three readers at
   once: the inline per-step resource list (`setupInlineResources`), the
   course-level download button (`setResourceButton`, `getCourseResources`'s
   `WHERE courseId = ?`), and the step's auto-download and next-step prefetch.
   Three constraints for whoever takes it, from the audit:
   * write `stepId`/`courseId` **only when non-blank** (`MyLibrary.kt:229-234`),
     so the plain resources walk cannot clear the course link on a re-pull —
     the Phase 56 / 74 / 98 shape;
   * key on the port's own `CourseMapper.stepIdFor` (`'$courseId:$index'`), not
     Kotlin's `Base64(stepElement.toString())`;
   * `my_library` gains two columns, so it needs a **schema bump**, and it is
     not in `localAuthorityTables` — confirm that before writing the migration.
2. **`course_detail_screen.dart:204` shows the wrong datum.**
   `CoursesStepsAdapter.bind` (`:51-55`) sets the step row's second line from
   `R.string.test_size` — *"This test has %d questions"* — with
   `step.questionCount`, and hides it unless the row is expanded (`:57-63`). The
   port's subtitle is `resourcesInStep(step.noOfResources)`: a different number,
   always visible, and per item 1 one the port cannot substantiate. This
   sharpens Phase 139 item 5, which concluded there was "nothing to fix today"
   because the port "has no analogue" — it does, it is just showing something
   else in the same slot. Whoever ports it meets the row-pick question Phase 139
   answered for the lock, since `questionCount` is
   `examDao.getFirstByStepId(stepId)?.noOfQuestions` and can name a *survey's*
   count under a "test" label.
3. **The port has no join prompt.** `setCourseData:219-229` puts a *do you want
   to join this course?* dialog in front of a non-member on open, once per
   ViewModel (`hasOfferedJoinDialog`), whose positive action joins and
   immediately restores navigation. With Job 3's gate landed, the port shows a
   non-member a step they cannot leave and a button they must work out for
   themselves. Not built here because it is a new affordance rather than one of
   the three jobs, and because the ViewModel-scoped "offered once" state has no
   port equivalent. Note two things inside that Kotlin **not** to port:
   `maybeShowJoinDialog`/`onCourseDetailContentReady`/`JOIN_DIALOG_FALLBACK_MS`
   are dead (`pendingJoinDialog` is never set true anywhere), and the dialog
   shows unconditionally at `:228`.
4. **Kotlin's non-member gate fails open and the port's does not**, so this is a
   deviation for `docs/kotlin-to-flutter-migration.md`'s *Faithful quirks /
   deliberate deviations* list rather than parity. Text: *"`TakeCourseFragment`
   hides Next and Previous from a non-member in `updateNavigationVisibility`,
   but `onResume:158`, `onClick:373` and `onClickNext:347` all restore them
   without a membership test, so in the shipping app the gate holds only until
   the first resume. The port applies the stated rule permanently."* That file
   is Lane A's this round. Phase 128 item 5 left the same kind of hand-off and
   it is still outstanding, so this is the second entry queued for that list.
5. **A 0-step course diverges.** Kotlin's `bindCourse:111-114` hides both
   buttons and then `:118` evaluates `0 >= 0` as true and shows **Finish** — to
   members, non-members and guests alike. The port renders `l10n.noDataAvailable`
   and never reaches `_NavigationBar`. Benign, and the port's is the better
   behaviour; recorded because it is the one place the Finish rule above does
   not transfer.
6. **`TakeCourseFragment` has no leave affordance and the port has one.**
   `setCourseData`'s `else` (`:230-232`) hides `btnRemove` for a member as well
   as a guest. The port keeps *Remove from my courses* here, as
   `CourseDetailScreen` does. A port addition, left alone — removing a working
   capability is not a parity fix — but it is the other half of the
   `canChangeMembership` gate and belongs with it.
7. **The port does not resume a course at the learner's saved step** — Phase 139
   item 9, still open, and it is what makes the non-member Finish state above
   reachable in Kotlin but not in the port. Related: `TakeCourseScreen.build`'s
   clamp still moves the index during `build` without driving the controller
   (Phase 139 item 10).
8. **`SubmissionDao.countCompletedByUserAndExamId` was routed to Lane B this
   round** and `_isStepCompleted` still filters in Dart. Not touched here, as
   briefed; `_nextStepLock` was edited for the membership gate only and
   `_isStepCompleted` is untouched.
9. **Cosmetic, and named so nobody "fixes" it twice.** Kotlin's heading renders
   `STEP 3/5` — `R.string.step` is the lowercase `"step"` and `tv_step` carries
   `android:textAllCaps="true"` (`fragment_take_course.xml:66`) — and its Next
   button reads lowercase `next`. The port renders `Step 3` beside a separate
   `3 / 5` counter. Same numbers, different typography; not worth a change.
10. **Phase 139 items 1 and 5–14 are otherwise unchanged**, as are Phase 135
    items 5, 7, 9, 10 and 11.
