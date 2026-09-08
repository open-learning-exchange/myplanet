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
   already-green code. **It found seven things**, and they are folded in below
   rather than left in a transcript: one behavioural divergence documented as
   parity, one guest hole the lane had declared closed while leaving the larger
   half open **in its own file set**, a claim Phase 128 had already retracted
   and this phase reinstated, a false justification for a `ref.watch`, three
   miscounts, and four weak tests. That is six consecutive rounds in which the
   second pass found something on green code.

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

**Phase 128's conclusion was right and this phase briefly un-learned it.** The
first draft of these notes said Phase 128 had cited the wrong function; it had
not — it cited `setResourceButton` as the live analogue of *the chevron's
promised navigation*, and it had already found `legacy_buttons_container` and
already stated in terms that "there is no live Kotlin count". What is genuinely
new here is only `setupInlineResources` / `autoDownloadResources` /
`prefetchNextStepResources` as the real per-step UI.

Worse, an earlier version of the test comment and of this file asserted that
`CourseStepFragment` "shows the same number" the port's tile shows. **It does
not** — that number goes to `btnResources`, which is GONE — and it is precisely
the claim Phase 128 made and retracted *in the same paragraph*
(`PHASE_128_NOTES.md:378-380`). Reinstating a retraction from memory is the
same failure mode as the brief's off-by-one, one file further along. The count
is kept because it is true and it is the only thing the screen can say about a
step's resources, **not** because Kotlin shows it; when the list arrives it
should replace the count rather than sit beside it. Phase 128's other still-live
observation, unrecorded until now: `resourcesInStep` is an ICU plural, so
`tool/arb_from_strings_xml.dart` can never derive a translation for it.

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

**Finish is not gated, and that is an invariant rather than a preference.** The
`else` branch never touches `finishStep`; `setNavigationButtons()` (`:118`,
`:464-473`) and the only other writers, `onResume:154-160` and
`onClickNext:343-349`, all key on `position >= steps.size` alone. Gating it
would break the case Kotlin genuinely has: an **ex-member** is restored to
`position = currentStep` (`:104`, `:116`) from progress rows that survive
leaving a course (`leaveCourses` touches `courses` and `removed_log` only), so
somebody who finished a course and then left re-opens on the last page with
Finish and neither Next nor Previous.

**Where the port and Kotlin actually part company is the cover page, and the
first draft of this file called that parity — the second audit was right to
push.** Kotlin's *never*-member sits at `position = 0`, the cover page, where
`0 >= steps.size` is false for any course with a step: they see no Finish
either. The port has no cover page, so its index 0 is Kotlin's position 1; on a
**one-step** course that is already the last step, and applying Kotlin's own
rule at the position the learner is on yields Finish. So a learner who never
joined a one-step course gets a Finish button Kotlin's cold open would not show
them.

That is the same structural difference Job 1 is about, surfacing a second time,
and it is left standing: the alternative is to gate Finish on membership, which
contradicts the invariant and takes the button from the ex-member Kotlin gives
it to. Recorded as a divergence, not dressed up as parity.

The **five** pre-existing finish/rating tests in `take_course_screen_test.dart`
were non-member fixtures *by accident* — `buildCourseRow`'s default `userId` is
empty — so from the moment the gate landed they were silently propping up this
decision, and mutating it reddened all five instead of the one test that is
about it. They are member rows now, and that mutation reds exactly one test.

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

**Two corrections from the second audit, both mine.**

*The claim was in the past tense and should not have been.* `UserMapper` says
**nothing in the port creates a guest row at all**, so no user could take that
tap today. This is hardening consistent with the port's other guest gates, not a
live bug closed — and the notes and the code comment said otherwise.

*And the lane closed the smaller half.* `CourseDetailScreen` has the **same**
ungated toggle, in this lane's own file set, and its `_toggleMembership` calls
`shelfRepository.upload` after the local write — so that copy reached the
server where the one Phase 145 gated first only wrote locally. Now gated, and
pinned: a mutation deleting the gate left the entire suite green until a test
was written for it.

**The gate is narrower than Kotlin's, and the narrowing is now the top reported
item.** `UserEntity.isGuest()` is the id-prefix rule **or** a `guest` role
without a `learner` role; `UserMapper.isGuest` implements only the first half.
Its own doc names `TakeCourseFragment:212` — the exact site this phase ported —
as one of three role-clause gates that "have no port counterpart yet", and says
the role clause "belongs here as a second predicate beside this one — not folded
into it". Landing that counterpart made the doc false. `user_mapper.dart` is
outside this lane's file set, so the predicate and the doc line are reported
rather than changed.

**The `!containsUserId` half is deliberately not ported.** Kotlin hides this
button from *members* too — `TakeCourseFragment` has no leave affordance at
all — where the port keeps one, as `CourseDetailScreen` does. That is a port
addition rather than a defect, and removing a working capability is a decision
beyond this brief. Named rather than folded in.

## Files touched

| file | change |
|---|---|
| `lib/ui/courses/take_course_screen.dart` | the heading, the chevron, the nav gate, `canChangeMembership`, `_nextStepLock`'s gate removed |
| `lib/ui/courses/course_detail_screen.dart` | the `!isGuest` gate on the membership toggle |
| `test/ui/courses/step_label_test.dart` | new, 3 tests |
| `test/ui/courses/step_resources_tile_test.dart` | new, 3 tests |
| `test/ui/courses/non_member_navigation_test.dart` | new, 7 tests |
| `test/ui/courses/step_next_lock_test.dart` | one existing test reshaped — see below |
| `test/ui/courses/course_detail_screen_test.dart` | +2 tests pinning the guest gate |
| `test/ui/courses/take_course_screen_test.dart` | 5 finish/rating fixtures made member rows |
| `lib/l10n/app_en.arb` | **none** — no key added |

**`course_detail_screen.dart` was going to be "deliberately unchanged", and the
second audit was right that this was the wrong call.** Both things *Job 2* sent
me to look at there were absent (the chevron) or unportable (the resource
list) — but the lane had just decided, in Job 3, that an ungated membership
button is a defect, and this file has the same one, in a copy that **uploads**.
Checking a file against the job you arrived with is not the same as checking it
against what you learned while you were there. The remaining gap in it
(item 3, the step tile's subtitle) is genuinely a display decision beyond the
three jobs and stays reported.

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
| 6 | Finish gated on `isMyCourse` too | *a non-member on the last step still sees Finish* — **1 test, after the five accidental non-member fixtures were made members**; it reddened 5 before |
| 7 | drop `canChangeMembership` on the join/leave button | 2 guest tests |
| 8 | re-add the membership gate to `_nextStepLock` | **nothing** — deliberately; see above |
| 9 | drop the `!isGuest` gate on `CourseDetailScreen`'s toggle | **nothing, until a test was written** — see below |

Mutation 8 is recorded as a **non-red on purpose**. The lock's gate is
unreachable once the button is gated, so nothing can pin it; that is the reason
it was removed rather than kept as belt-and-braces, and stating it here is
cheaper than the next lane rediscovering that an untestable line exists.

**Mutation 9 was a non-red by accident, which is the different thing.** The
`CourseDetailScreen` guest gate shipped with nothing pinning it — a fix that
reads as coverage and is not. Two tests in `course_detail_screen_test.dart` now
red it. Rows 6 and 7 also moved after the audit: row 6 from five reds to one
(the fixtures were the problem, not the gate), row 7 from one to two (the guest
test was split, because the original put a guest on an *unjoined* course where
three of its four assertions followed from non-membership and would have held
with `canChangeMembership` deleted). The discriminating case is a guest who
**is** in `course.userId`: Kotlin's `updateNavigationVisibility:257` keys on
`containsUserId` with no guest exception while `setCourseData` keys on both, so
that learner gets the navigation and not the button.

## Integrator

**Nothing to run.** No ARB key is added and no locale file is touched, so unlike
Phases 128 and 139 there is no derivation hand-off.

One thing to know: **`courseDetails` is now an orphan template key.** Nothing in
`lib/` reads it after this diff.

The first draft of this paragraph said it "carries real ar/es/fr translations"
and that deleting it touches five locale files. **Both were wrong**, and this
project's vocabulary makes the first one matter: ar and fr are `"x-mt": true`,
unreviewed machine translation, and only **es** is a human translation; ne and
so do not have the key at all. So deleting it touches **three** locale files and
moves only the `es` entry in `humanReviewed`
(`test/l10n/placeholder_integrity_test.dart`). Outside this lane's file set
either way. Phase 128 left `takeTest` in the same state; this makes two.

**Also for Lane A**, whoever holds `user_mapper.dart`: its `isGuest` doc names
`TakeCourseFragment:212` as a role-clause gate with "no port counterpart yet".
This phase landed that counterpart, so the line is now false — see *Reported*
item 1.

## Reported, not fixed

1. **`UserMapper.isGuest` is missing Kotlin's role clause, and this phase made
   its doc stale.** `UserEntity.isGuest()` is
   `_id.startsWith("guest_") || (hasGuestRole && !hasLearnerRole)`;
   `UserMapper.isGuest` implements only the first disjunct. Its doc explains
   why — every port gate so far was a counterpart of Kotlin's narrower
   `id.startsWith` family — and names three role-clause gates that "have no port
   counterpart yet": `TeamFragment:235`, `CoursesFragment:135` and
   **`TakeCourseFragment:212`**. Phase 145 ported the third. So the doc asserts
   something false, and both new guest gates are narrower than their Kotlin
   originals: a user with `roles: ["guest"]`, no `learner`, and an ordinary
   `org.couchdb.user:` id gets a join button Kotlin withholds. Unreachable today
   (no port writer creates a guest row), certain as documentation rot. The doc
   also says where the fix goes: **a second predicate beside `isGuest`, not
   folded into it**, or the settings and voices gates silently widen past their
   Kotlin counterparts. `user_mapper.dart` is outside this lane's file set.
2. **The port never ingests a course step's resources**, so `noOfResources` is a
   count of rows it does not hold. Kotlin's courses walk buffers each embedded
   resource document (`queueCourseResources`, `CoursesRepositoryImpl.kt:687`,
   `:792-798`) and drains it into `my_library` stamped with `courseId` and
   `stepId` (`flushPendingCourseResources`, `:819-851`); `CourseMapper
   ._parseSteps` keeps only the length. **The drain has two call sites**, not
   one: `TransactionSyncManager.kt:302`, per batch inside the `"courses"` walk,
   and `CoursesRepositoryImpl.kt:508`. A port needs both or an equivalent, since
   the walk's own call is what makes a step's resources available before the
   sync finishes. **This is the item behind Job 2** and it blocks three readers at
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
3. **`course_detail_screen.dart:204` shows the wrong datum.**
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
4. **The port has no join prompt.** `setCourseData:219-229` puts a *do you want
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
5. **Kotlin's non-member gate fails open and the port's does not**, so this is a
   deviation for `docs/kotlin-to-flutter-migration.md`'s *Faithful quirks /
   deliberate deviations* list rather than parity. Text: *"`TakeCourseFragment`
   hides Next and Previous from a non-member in `updateNavigationVisibility`,
   but `onResume:158`, `onClick:373` and `onClickNext:347` all restore them
   without a membership test, so in the shipping app the gate holds only until
   the first resume. The port applies the stated rule permanently."* That file
   is Lane A's this round. Phase 128 item 5 left the same kind of hand-off and
   it is still outstanding, so this is the second entry queued for that list.
6. **A 0-step course diverges.** Kotlin's `bindCourse:111-114` hides both
   buttons and then `:118` evaluates `0 >= 0` as true and shows **Finish** — to
   members, non-members and guests alike. The port renders `l10n.noDataAvailable`
   and never reaches `_NavigationBar`. Benign, and the port's is the better
   behaviour; recorded because it is the one place the Finish rule above does
   not transfer.
7. **`TakeCourseFragment` has no leave affordance and the port has one.**
   `setCourseData`'s `else` (`:230-232`) hides `btnRemove` for a member as well
   as a guest. The port keeps *Remove from my courses* here, as
   `CourseDetailScreen` does. A port addition, left alone — removing a working
   capability is not a parity fix — but it is the other half of the
   `canChangeMembership` gate and belongs with it.
8. **The port does not resume a course at the learner's saved step** — Phase 139
   item 9, still open, and it is what makes the non-member Finish state above
   reachable in Kotlin but not in the port. Related: `TakeCourseScreen.build`'s
   clamp still moves the index during `build` without driving the controller
   (Phase 139 item 10).
9. **`SubmissionDao.countCompletedByUserAndExamId` was routed to Lane B this
   round** and `_isStepCompleted` still filters in Dart. Not touched here, as
   briefed; `_nextStepLock` was edited for the membership gate only and
   `_isStepCompleted` is untouched.
10. **Cosmetic, and named so nobody "fixes" it twice.** Kotlin's heading renders
   `STEP 3/5` — `R.string.step` is the lowercase `"step"` and `tv_step` carries
   `android:textAllCaps="true"` (`fragment_take_course.xml:66`) — and its Next
   button reads lowercase `next`. The port renders `Step 3` beside a separate
   `3 / 5` counter. Same numbers, different typography; not worth a change.
11. **Phase 139 items 1 and 5–14 are otherwise unchanged**, as are Phase 135
    items 5, 7, 9, 10 and 11.
