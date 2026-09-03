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
- **A note for testing `courseProgressStreamProvider`**: it is a
  `StreamProvider` over a drift `watch()` query, and `widget_harness.dart` is
  explicit that a screen test should override the provider rather than open a
  live drift stream inside the fake-async zone. Doing the latter hangs
  `pumpAndSettle` on the loading spinner for its ten-minute default — the
  failure that looks like a hang, third recorded instance. Its data path is
  covered without a widget tree in `test/providers/`.
