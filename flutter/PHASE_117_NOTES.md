# Phase 117 — the course-progress grid

Three rounds running, the same shape. Phase 110 ported per-step mistake totals
nothing could observe; Phase 113 found the screen they belonged to unreachable
and fixed it; its parting note said `ProgressRepository.courseProgress` still
had no UI caller anywhere in `lib/`. This phase renders it.

## First: the target was described wrongly, and that outranked the UI work

Phase 113 wrote:

> the per-step question count and mistake total it computes are exactly what
> Kotlin's `ProgressGridAdapter` renders.

**It renders neither.** `row_my_progress_grid.xml` holds exactly one view,
`tv_progress`, and `ProgressGridAdapter.onBindViewHolder:37-50` is the whole of
the cell:

```kotlin
if (item.has("percentage")) {
    holder.tvProgress.text = context.getString(R.string.percentage, item["percentage"].asString)
    if (item["completed"].asBoolean) holder.itemView.setBackgroundColor(colorCompleted)
    else holder.itemView.setBackgroundColor(colorInProgress)
} else {
    holder.itemView.setBackgroundColor(colorMain)
}
```

The question count is only the *denominator* (`CoursesRepositoryImpl.kt:490,
495-496`) and is never shown. `mistakes` is not computed on this path at all —
`getCourseProgress` never reads `Answer.mistakes`. Mistakes are rendered on the
**My Progress list row**, the screen the grid is opened *from*
(`CoursesProgressAdapter.kt:37-38` for the oval total, `:42-97` for the
mini-table), fed by `ProgressRepositoryImpl.submissionMap`.

So `courseProgress` was not "computed and correct but rendered nowhere". It
computed two numbers the grid does not have and none of the three it does.
The lesson is narrower than the pattern the round was set up to find: **a
predecessor's citation is not a predecessor's reading** — the same trap Phase
106 recorded, one layer up.

## What Kotlin draws, and where

`CourseProgressActivity` is its own Activity (`AndroidManifest.xml:105-107`,
no `android:label`, so the action bar reads "myPlanet"), opened *only* by
`CoursesProgressAdapter.onBindViewHolder`'s `startActivity(...)` — one entry,
gated on `progressCurrent != null && progressMax != null`. It shows:

- a 164dp ring whose centre label is `(current / max * 100).toInt()` — a bare
  truncated integer, no `%` sign, because `app:progressTextType="progress"`
  with `app:totalValue="100"`; `max == 0` takes an explicit zero branch;
- the course title;
- `course_progress` = `Progress %1$s of %2$s`, fed pre-stringified ints;
- a static `steps` = "Steps";
- `GridLayoutManager(this, 4)` over 60dp square cards, one per step, in the
  order `CourseStepDao.getByCourseId` happens to return (no `ORDER BY`, and
  `CourseStep` has no index column — the port has `stepIndex` and orders by it,
  a pre-existing improvement);
- no empty state, no per-cell label, and **no interaction at all**: the adapter
  installs no click listener.

`current` is `getCurrentProgress` — the contiguous run of steps from step 1
that have a progress row, **ignoring `passed`**. That inconsistency with
`getCompletedCourses` (which does filter on `passed`) is deliberate and pinned
by `ProgressRepositoryImplTest.kt:130-146`.

## What the port now has

`ProgressRepository.courseProgress` returns a `CourseProgressData`
(`title` / `current` / `max` / one `CourseStepProgress` per step) instead of
per-step `questionCount`/`totalMistakes`. `_stepCell` is `getExamObject`, whose
write semantics are the entire specification and are easy to get wrong:

- the whole body sits inside `submissionsForExam.forEach`, so **an exam with no
  submission writes nothing** — a step whose only exam is unattempted is
  indistinguishable from a step with no exam, and is *not* 0%;
- the zero-question branch is guarded by `!ob.has(...)` (first wins); the
  with-questions branch is unguarded (last wins). So a zero-question exam
  cannot overwrite a real percentage, and two submissions on one exam leave
  whichever `groupBy` yielded last;
- `status` is written unconditionally and read by nothing, so it is not ported.
  Neither are mistakes. Carrying either would rebuild the pathology.

`CourseProgressScreen` is the Activity, at its own route
`/courses/progress/:courseId` under the My Progress screen, and the list row
now opens it.

### `percentage` is a `num`, and "100.0%" is deliberate

`addProperty(String, Number)` boxes the `Number` as-is, and `getAsString()`
returns `getAsNumber().toString()`. The zero-question branch stores an
**`Integer` 0** and the other a **`Double`**, so the shipping app renders "0%"
for the first and "100.0%", "50.0%", "33.33333333333333%" for the second.
Dart's `double.toString()` is the same shortest-round-trip algorithm as Java's
(checked against JDK output for the 1-, 2-, 3-, 6-, 7-, 9- and 20-question
cases, `55.00000000000001` included), and `0.toString()` is `"0"`, so keeping
the value a `num` and rendering `toString()` is byte-identical.
`toStringAsFixed` would silently change every cell.

A long label clips inside its 60dp cell rather than bleeding over its
neighbours, which is what the Kotlin TextView does; pinned by a test that
asserts the text lays out to exactly 60x60 and raises nothing.

## The end-to-end link, demonstrated

`course_progress_screen_test.dart` drives the real `SubmissionsRepository`:
`startExamSession` → `saveExamAnswer('london')` (returns false, the answer is
refused and `mistakes` becomes 1) → `saveExamAnswer('paris')` (true) → the
screen renders **50.0%** for one of the exam's two questions. The step exam
comes from the real `ExamMapper.fromCourseDoc`, so the Phase 113 join is under
test rather than stubbed. Nothing between the answer and the pixel is faked
except the network.

**Verdict on "are the numbers real": yes, and the answer count is the number
the grid wants.** The mistake accumulator is also intact — the same test
asserts it — but it surfaces on the list row, which is where Kotlin puts it.

## Defects, each demonstrated failing first

| # | defect | pre-fix failure |
|---|---|---|
| 1 | **the My Progress row opened the course detail screen**, which shows none of this. Kotlin's row opens `CourseProgressActivity` | `the My Progress row opens the grid for its course` — `Found 0 widgets with text "GRID_ROUTE course-1"` |
| 2 | **the row was clickable with no progress figures.** Kotlin installs the listener inside `if (progressCurrent != null && progressMax != null)` | `a My Progress row with no progress figures is inert` — `means one was found but none were expected` |
| 3 | **`courseProgressStreamProvider` read the session as `.valueOrNull`.** A null `shelfUserId` makes `CourseDao.watchCourses` drop the shelf predicate entirely — which is not "no courses" but *every* course — so the first thing My Progress emitted was the whole catalogue, each entry captioned with a progress bar | `the list is the user's shelf, never the whole catalogue` — `TimeoutException after 0:00:10: Future not completed` (the pending first future is discarded when the session lands and the provider rebuilds, which is how the wrong pass shows up in a test) |
| 4 | **per-step mistakes were keyed by raw exam id**, and the screen recovered a step number by running `(\d+)` over it — on a hex CouchDB id that is whichever digit appears first, and with no digit at all it is 0 for every row. The same line also *overwrote* per exam instead of summing. Kotlin keys by the exam's ordinal in the course and renders `key + 1` | `per-step mistakes are keyed by the exam's ordinal in the course` — `Expected: {1: 5} Actual: {0: 3}` (both bugs at once) |
| 5 | **a submission was attributed to a course by substring.** Kotlin matches an `@`-delimited *segment* (`parts.lastOrNull { courseIdsSet.contains(it) }`), specifically to survive the `course1`/`course10` collision its own test pins | `a submission whose parent names another course is not counted` — `Expected: <0> Actual: <4>` |
| 6 | `_examIdFromParent` returned `"@abc"` where `getParentBaseId` returns `""`. Aligned, but recorded rather than claimed: it needs an empty exam id, which `examParentId` cannot produce | — |

## Deliberate divergences, each with its reason

1. **The user id.** Kotlin's `CourseProgressViewModel` passes `user?._id` while
   the list's `ProgressViewModel` passes `user?.id`, and **every writer keys on
   `id`**. Equal for a synced account; a member created offline gets a
   generated `id` and an empty `_id`, so the Kotlin grid reads nothing back and
   shows a blank grid beneath a list row that displayed real numbers — and
   permanently, since a later sync fills `_id` and never rewrites `id`. The
   port passes `id` on both paths: reproducing this would mean querying a key
   nothing writes.
2. **Rounded cells.** `row_my_progress_grid.xml` asks for
   `app:cardCornerRadius="8dp"`, but the adapter paints with
   `itemView.setBackgroundColor`, which AndroidX `CardView` does not override —
   the `RoundRectDrawable` supplying the outline is replaced by a
   `ColorDrawable`, so the radius and the shadow are dead and the shipping
   cells are square. The port draws the radius the layout asks for.
3. **An AppBar title.** The Kotlin action bar shows the application label
   ("myPlanet") because the manifest entry has none of its own. A Flutter
   `AppBar` cannot be title-less without looking broken, so it carries
   "My Progress" — the name of the list this screen is entered from.
4. **A loading spinner.** Kotlin shows the ring at its XML default
   `progressValue="10"` (centre text literally "10"), empty title and progress
   lines, and a RecyclerView with no adapter attached, until the load lands.
5. **The exam is reached from the answer's own `examId`**, not through the
   question row. Kotlin drops a mistake whose `questionId` no longer resolves
   to an `exam_questions` row, because that lookup is how it finds the exam;
   the port keeps it, which is the number the learner earned.
6. **`mistakes` is 0 rather than absent** for a course with no submissions.
   Kotlin leaves the key out and the adapter falls back to
   `message_placeholder("0")` — same pixels, different model.

Faithfully reproduced rather than tidied, and worth knowing before someone
files them as port bugs: the `stepMistake` map is the **last** submission's
breakdown while `mistakes` totals all of them (Kotlin builds the map inside the
per-submission loop and writes it there); a step's cell falls from green
"100.0%" back to yellow "0.0%" merely by *opening* its exam again, because
`startExamSession(recreate = true)` deletes the previous attempt; and the
"Step" column of the mini-table is an exam ordinal, not a step number.

## The second audit, on the finished implementation

Phase 113's lesson, holding for the third round: **an audit of the ground truth
does not audit the implementation.** The first pass established the Kotlin and
the fix was green — format clean, analyze clean, 1917 tests, CI green on
`67a9b01`. A second pass at `effort: max`, pointed at my own diff, found nine
more things. `_stepCell` itself survived: the reviewer could not construct data
where it and `getExamObject` disagree, and both mutations it tried (dropping
the `??=` guard, and `??= 0.0` for `??= 0`) were already caught by tests. It
also verified `percentageLabel` exhaustively rather than by argument — every
`((double) n / q) * 100` for `q` in 1..200 and `n` in 0..q+2, 20,700 values,
through JDK 21's `Double.toString` and Dart's, with zero differences.

**And it broke the branch, which is the process finding.** The reviewer
mutation-tested by editing the shared working tree, and the commit that
followed swept the mutation into `7144bdc` along with three of its probe files
— the `??=` in `_stepCell` replaced by plain assignment, i.e. exactly the
guard the phase is about, deleted. `95e0f17` restores it. Two lessons, both
cheap: **a review pass that mutates code must do it on a copy, not the tree the
author is committing from**, and a `git add -A` after handing the tree to
anything else is a commit of unknown content. The suite caught it within
minutes — the earlier "flaky" full-run failure I could not reproduce in
isolation was this, not flakiness.

| # | second-pass defect | pre-fix failure |
|---|---|---|
| 7 | **the My Progress list was frozen for the process lifetime.** `CoursesProgressFragment.onViewCreated:31` calls `loadCourseData()` unconditionally — it has none of `CourseProgressViewModel`'s `if (value != null) return` — and the fragment is pushed with `addToBackStack`, so leaving to take an exam and coming back re-reads. A plain `StreamProvider` that yields once and completes, with nothing invalidating it, does not. Worse for being half a pair: the grid *is* `autoDispose`, so after an exam the row said "1 mistake" while the grid it opened showed the new percentage — two halves of one screen disagreeing, and my own rationale twenty lines below was the argument against it | `re-entering My Progress re-reads rather than serving a frozen list` — `Expected: <3> Actual: <1>` |
| 8 | **the mistakes table listed its exams in the wrong order.** Kotlin's `mistakesMap` is a `HashMap<String, Int>` keyed by the ordinal as a string, round-tripped through Gson into a `LinkedTreeMap`, so the adapter walks it in bucket order — ascending for the keys `"0".."9"`. A Dart map preserves the order the answers came off `answersFor`, so a submission whose first answer belonged to a later exam listed its rows backwards | `the mistakes table lists its exams in ascending order` — `did not find a value matching '3' following expected prior values` |
| 9 | **a partial last grid row was centred.** `GridLayoutManager(this, 4)` puts item n in span `n % 4`, so a ninth step sits in the *first* column of its row; rows centred inside the enclosing Column put it under the gap between columns 1 and 2 | `a partial last grid row is left-aligned, not centred` — `Expected: <24.0> Actual: <126.0>` |
| 10 | **the first frame of the My Courses tab was the whole catalogue.** `coursesStreamProvider` read the session as `.valueOrNull` and passed it as `shelfUserId`, which `watchCourses` drops when null. The same defect as #3, one provider above it in the same file — so found by this phase and fixed with it, awaiting the session **only** when the shelf filter is on so the catalogue view keeps its latency | reasoning plus #3's measured shape; the flash self-corrects on rebuild, so it has no stable assertion |

### Two quirks that were right and unpinned

Both of `submissionMap`'s oddities — `totalMistakes` declared outside the
per-submission loop while `mistakesMap` is rebuilt inside it — were correctly
reproduced and **neither was tested**. Hoisting the map (making the breakdown
accumulate) and moving the accumulator in (making the total reset) each passed
the entire suite. That is the shape a later reader "tidies", and my own comment
telling them not to was the only thing standing in the way. `two attempts total
together but only the last one breaks down` now pins both: mutation 1 gives
`Expected: {1: 3} Actual: {0: 2, 1: 3}`, mutation 2 `Expected: <5> Actual: <3>`.

### Three tests that passed without asserting what they claimed

- `expect(find.byType(Container).evaluate().length, greaterThanOrEqualTo(3))`
  for a three-step course cannot fail on the four cells an off-by-one would
  draw, which is the bug it is named for. Now `findsNWidgets(3)`.
- *"a zero-question exam cannot overwrite a real percentage, **in either
  order**"* exercised one order. It now builds the rows in the other insertion
  order too, rather than trusting an `ORDER BY`-less select.
- *"a row with no progress figures is inert"* covers a state the real provider
  cannot produce — `courseProgressSummary` writes an entry per requested id, so
  the pair is never null, and Kotlin's branch is dead too (its `else` writes
  `JsonNull`, which `getAsJsonObject("progress")` would throw on before the
  adapter saw it). Kept, because the gate is one line and its absence is
  invisible, with a comment saying so.
- The mistake-keying test seeded answers with **no `questionId`** — data on
  which Kotlin counts nothing and draws no table at all, so the fixture could
  not demonstrate the keying it was named for. Its answers now name seeded
  `exam_questions` rows.

### One fix considered and declined

`filteredSortedCoursesProvider` reads the session `.valueOrNull` too, and
awaiting it there is the *worse* option: it costs one frame of every course
reading "Not Started", whereas awaiting makes a session that rejects — or that
no test harness resolves — fail the provider and render an **empty** course
list, where Kotlin's fallback for unavailable progress is `baseCourses`, i.e.
show the list unfiltered. Awaiting it also broke
`the progress filter narrows the list by completion state`, which is the
harness telling the same story. Left as it was, with the reasoning at the code.

### Dead code removed

`Routes.courseProgress` was declared and read by nothing — the pusher builds
its path from the non-pattern `Routes.myProgress`, and the codebase's
convention for a parameterised route (see `take_course_screen.dart:397`) is a
literal with a comment warning against appending to the pattern. Removing it
shrinks the cross-lane crossing in `router.dart` to the single nested
`GoRoute`. `CourseStepProgress.stepId` was write-only; it is now the cell's
`ValueKey`, which is what `ProgressGridAdapter`'s `areItemsTheSame` keys on.
`_seedAttempt`'s `mistakes` parameter had no caller.

### Left for Lane C

`courseProgressCount` = "Progress {current} of {max}" has a **human**
translation already shipping in all five Kotlin locales (`values-es:1010`
"Progreso %1$s de %2$s"; `values-ne:1010` even reorders the placeholders, which
ICU handles). `tool/arb_from_strings_xml.dart` skips placeholder keys outright,
so it will never derive them and Spanish will read English forever. One key,
five files, all in Lane C's set. `stepsHeading` the tool will pick up by exact
English match on `steps`, and `percentageValue` is `%s%%` in every locale.

> **Corrected by Phase 121, which did the work.** `values-ne`'s `course_progress`
> does **not** reorder its placeholders — `%1$s को %2$s प्रगति` keeps ascending
> argument order, and only moves them relative to the words, which any
> substitution handles. The conclusion drawn from it is right and the instance
> is not: four other Kotlin strings really do reorder
> (`ne/download_progress`, `ne/download_progress_with_errors`,
> `ne/steps_done_of_total`, `ar/member_description`), and those are what pin the
> conversion. `percentageValue` was *not* derived: `%s%%` carries no word, so
> every locale's value would be the English template, and a skeleton with no
> letter in it also matches any unrelated key of the same shape. See
> `PHASE_121_NOTES.md`.

## Not a schema change

No table, no converter, no index, no `schemaVersion` bump (still 45), so no
`build_runner` step. Three new `app_en.arb` keys — `stepsHeading`,
`courseProgressCount`, `percentageValue` — each with its placeholders declared.

## For the integrator

- **One boundary crossing, deliberate: `lib/ui/router.dart`** (Lane A's file).
  One route constant `courseProgress` and one nested `GoRoute` under
  `/courses/progress`. Without it the new screen is unreachable, which is the
  exact pathology this phase exists to end, and Lane A is auditing
  reachability — an unreachable new screen would be its finding. Nothing else
  in that file is touched.
- **`CourseProgressRow` in `courses_providers.dart` is renamed
  `CoursesProgressRow`**, which is the Kotlin class's own name
  (`model/CoursesProgressRow.kt`). The short name is also the drift row class
  for the `courses_progress` table: it shadowed the drift one inside that file
  and clashed on import anywhere both were needed. Only
  `courses_progress_screen.dart` referenced it.
- **`courseProgress`'s signature changed** — it returns `CourseProgressData`,
  not `List<CourseStepProgress>`, and `CourseStepProgress` lost `step`,
  `progress`, `passed`, `questionCount` and `totalMistakes`. Its only callers
  were its own tests plus the new provider; the three tests that used
  `grid[0].passed` as a proxy for "did `saveCourseProgress` store it" now ask
  `CourseProgressDao.findByCourseUserAndStep` directly, which is what they
  meant.
- **One behaviour question raised and deliberately not answered here.**
  With the shelf filter on and no signed-in user, `watchCourses(shelfUserId:
  null)` shows the whole catalogue where Kotlin's `getMyCourses(userId ?: "")`
  matches nothing. It is a real divergence, on the courses list rather than
  this slice, and changing what a guest sees on the My Courses tab is a
  decision for whoever owns that screen's behaviour, not a side effect of a
  progress-grid phase.
- **A note for testing `courseProgressStreamProvider`**: it is a
  `StreamProvider` over a drift `watch()` query, and `widget_harness.dart` is
  explicit that a screen test should override the provider rather than open a
  live drift stream inside the fake-async zone. Doing the latter hangs
  `pumpAndSettle` on the loading spinner for its ten-minute default — the
  failure that looks like a hang, third recorded instance. Its data path is
  covered without a widget tree in `test/providers/`.
