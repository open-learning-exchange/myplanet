# Phase 151 — `""` and null are not the same thing

Lane B of a four-lane round. One defect, traced end to end, fixed on one line,
with the rest of its reach measured and handed on rather than half-closed.

Brief: `PHASE_149_NOTES.md` § *Reported, not fixed* item 1, described by its
author as *"the biggest live item on this list."* It was right, and it was right
for the reason it gave.

## The defect

Kotlin's submissions sync-in stores `JsonUtils.getString("status", submission)`
(`SubmissionsRepositoryImpl.kt:659`, `:679`). Kotlin's `getString` returns `""`
for a key the document does not carry — and for an explicit JSON `null`, and for
a non-string primitive, since the `isString` test's false branch also returns
`""` (`JsonUtils.kt:65-68`). So a Planet submission with no `status` is stored
as `""`.

`SubmissionDao.countCompletedByUserAndExamId` (`SubmissionDao.kt:24`) is

```sql
SELECT COUNT(*) FROM submissions
 WHERE userId IS :userId AND parentId LIKE '%' || :examId || '%'
   AND status != 'pending'
```

`'' != 'pending'` is true, the count is 1, `isStepCompleted` returns true and the
course step's **Next** button unlocks. The whole chain is
`SubmissionsRepositoryImpl.isStepCompleted:359-365` →
`CoursesRepositoryImpl.kt:577` → `TakeCourseViewModel.kt:67` →
`TakeCourseFragment.changeNextButtonState:317-336`, whose body is gated on the
MyPlanet Onboarding course id; count 0 means `isNextStepLocked = true` and a
`please_complete_test` snackbar instead of advancing.

The port stored `getStringOrNull('status', json)`, which is null for a missing
key *and* for `""` (`json_utils.dart`). `NOT (NULL = 'pending')` is SQL **NULL**,
not true, and `WHERE NULL` drops the row. The count was 0 and **the step stayed
locked for a learner Kotlin lets straight through.**

Not hypothetical, and not a fixture artefact: the only thing needed to reach it
is a submission document that omits `status`, which is the shape Planet sends
whenever nothing has set one.

## Which side moved, and why

Two candidates. The brief said to argue it rather than default to the
reader-side fix, so:

**The reader was never wrong.** `countCompletedByUserAndExamId` is a faithful
port of Kotlin's query, its three-valued NULL behaviour included — Kotlin's own
reader also excludes a NULL status, and `app_database.dart` already says so in
as many words at the query. Coalescing there would have made the port's *reader*
diverge from Kotlin's in order to compensate for the port's *writer* diverging
from Kotlin's. Two wrongs, and the second one permanent: every future reader of
the column would inherit the obligation to remember.

**The writer had drifted, alone, on one line.** So one line moved:
`getStringOrNull` → `getString` for `status` in `upsertDocuments`. **No DAO
predicate changed**, which is also why this lane needed nothing from Lane A.

Three further arguments, all checked rather than asserted:

1. **The port has already paid for the reader-side approach, and the count is
   smaller than my first draft claimed.** That draft said three times —
   `pendingUploads`' coalesced operands, `_repairSurveyParentId`'s
   `coalesce([row.status, Constant('')]).equals('').not()`, and
   `SurveysRepository`'s `(row.status ?? '').isEmpty`. The implementation audit
   took two of the three apart and it was right:

   * `pendingUploads`' coalesces are on **`type` and `userId`**, not `status`;
     its `status` clause is deliberately *not* coalesced.
   * `_repairSurveyParentId`'s `coalesce` is **behaviourally redundant** —
     `status.equals('').not()` excludes NULL and `''` identically, because
     `NOT (NULL = '')` is NULL and a `WHERE` drops it. Verified by mutation:
     dropping the coalesce passes the suite. Phase 136's actual fix was the
     *operator* (`isNotValue` → `equals().not()`), not the coalesce. The
     comment at the site now says so, and says not to cite it as evidence.

   So the unambiguous instance is one: `SurveysRepository`'s Dart-side guard.
   **The conclusion survives and the evidence was a third of what I claimed**,
   which is worth the space because the argument is the phase's main one:
   a rule enforced at one writer is enforced, a rule enforced at N readers
   gets broken again — Phase 148's *one rule at the right layer beat twenty
   special cases*, Phase 78's `normalizeText`, Phase 95's `ProfileAvatar`. And
   the step count really was a reader nobody remembered.
2. **`''` and NULL select identically under every positive equality**, so the
   change can only ever *include* rows Kotlin includes. It cannot strand
   anything, which is the failure mode this project keeps paying for.
3. **It needs no migration to converge.** `sync()` walks
   `_all_docs?include_docs=true` in full every run and re-upserts every page
   (there is deliberately no `deleteNotIn`), so a submissions pull rewrites a
   row a shipped build stored as NULL. Neither option repairs an existing row
   retroactively — the brief was right to ask — but the reader fix would have
   to stay correct for both states for ever, where this one converges as soon
   as the table is pulled.

   **The count of callers went wrong twice before landing here, and it is the
   most instructive thing in the phase.** My first draft said "self-healing by
   the next ordinary sync". The ground-truth audit called that false, on the
   grounds that `sync()` has exactly one caller — the Submissions screen's
   refresh icon — and I took the correction, wrote it into the code and the
   notes, and made *"a mechanism verified is not a mechanism that runs"* the
   phase's headline lesson. The implementation audit then found
   `background_entrypoint.dart:195-202`: a `BackgroundSyncStep('submissions',
   …)` inside `syncSteps`, on the periodic auto-sync task, with
   `PlanetPrefs.autoSyncEnabled` defaulting to **true**
   (`planet_prefs.dart:441`). Verified by hand. **The table is pulled
   headlessly, the original claim was substantially right, and the correction
   over-corrected.**

   What is actually true of the sync center is only what
   `dashboard_sync_provider.dart:276` says — nothing in the **foreground**
   pass pulls `submissions` — which is a real gap against Kotlin's
   `HeavyTableSyncWorker` (`SyncManager.kt:209`) and is a different statement
   from the one I built on it. **An audit finding is a claim like any other.**
   The first pass was right that I had not checked who calls `sync`; it was
   wrong about the answer, and I propagated its answer without doing the check
   its own finding said I had skipped. Fifth instance of the class this
   project has now recorded, and the first where the error arrived *through* a
   correction.

### The one place the argument could have failed, verified rather than reasoned

`pendingUploads`' `isATeamAdoptionMarker` is deliberately
`status IS NOT NULL AND status = ''`, and its comment argues for that strictness
on the grounds that a **round-tripped marker reads back as NULL**. After this
change it reads back as `''`, so the marker test now *matches* a pulled row.

It still does not select one, because `upsertDocuments` hard-codes
`isUpdated: false` and the sweep requires `isUpdated = true`, so a pulled row
never reaches the marker test at all. That is the load-bearing step in the whole
argument, so it is **pinned by three tests** rather than left as a paragraph: a
round-tripped marker is not re-queued, a local marker is still excluded, and a
genuinely NULL-status local row is still swept (the case that predicate's
strictness exists for).

Mutating that predicate the other way is the useful part. Rewriting
`isATeamAdoptionMarker` as `coalesce([status, ''])  == ''` — which is precisely
the reader-side fix (R) would have applied — makes *a status-less local row is
still swept* fail: the row is stranded on the handset with no outbox entry.
So (R) is not merely more call sites than (W); on this column, applied
uniformly, it would have created a data-loss defect of the class this project
keeps paying for. That is an experiment, not an argument.

### What the fix does *not* change

`serialize` is untouched and became *more* faithful for free. Kotlin's
`serializeSubmission` writes `submission.status ?: "pending"`
(`SubmissionsRepositoryImpl.kt:840`) — an Elvis, so it fires on null and **not**
on `""`. The port's `row.status ?? 'pending'` is the same expression, so a
status-less document that Kotlin pulls and re-sends carries `""` back, and now
the port does too. Before this phase it substituted `pending` and told Planet
something the document never said.

`submissions_exporter.dart` needed one line to stay where it was. `row.status ??
'Pending'` stopped covering the status-less case the moment the column held `''`
instead of null, and both states mean *the server told us nothing*, so both now
take the fallback. That is the idiom `submissions_screen.dart`'s tile subtitle
already uses, so a submission does not read "Pending" in the list and blank in
its own PDF. Kotlin prints the bare column here and therefore shows blank
(`SubmissionsRepositoryExporter.kt:77`); keeping the port's label is a
deliberate nicety and this phase's job was the step lock, not a change of copy.

## The test that can see this axis

`test/repository/submission_empty_vs_null_status_test.dart`, 11 tests.
Reverting the writer (M1) fails **five** of them, the headline one with
`Expected: <1> Actual: <0>`. The other six are controls, and each is pinned by
its own mutation — the figure is five rather than the six an earlier draft of
this said, because two of the original failures were `type` assertions since
rewritten to pin the deferral instead.

The existing coverage could not see it **by construction**, which is the point
worth keeping. `step_next_lock_test`'s type-less document sets
`'status': 'complete'` — it exercises the `type` axis and holds the status axis
at a value that passes either way. Every other sync-in test hands
`upsertDocuments` a document that carries a status. A test for this axis has to
*omit the key*, which is what the server does, so the fixture here is a whole
Planet-shaped document (owner in the nested `user` object, no top-level
`userId`, a `_rev` because it came back from CouchDB) with one key absent.

Both directions are pinned, so the fix cannot be mistaken for "count
everything": a `pending` status still locks the step.

## The defect this phase caused, and the guard it disarmed

**The most valuable thing the implementation audit found is a defect the fix
created in the test suite, on a line the same commit annotated to say it
had not.**

`_repairSurveyParentId`'s status test exists because Phase 136 shipped
`row.status.isNotValue('')`, which emits SQL `IS NOT`; `NULL IS NOT ''` is
**true**, so the guard rewrote an adoption marker into the gate key and, in
that test's own words, *"the learner's Finish sailed past a survey they had
never answered."* The only fixture reaching that branch was one document whose
explicit `'status': ''` the old sync-in folded to NULL.

After the writer fix that document stores `''`, and `'' IS NOT ''` is **false**
— so the bug became invisible. Reverting `_repairSurveyParentId` to the
Phase-136 form left all 40 tests in the three relevant files green.
Demonstrated, not reasoned. And my edit to that test's expectation carried the
comment *"The assertion below is unaffected either way"*, which was true of
that assertion and false of the test's purpose; the same commit wrote the
justification *"which is why the `coalesce` stays: rows a pre-Phase-151 build
stored as NULL are still on devices"* and removed the coverage for exactly
that population.

Fixed by building the legacy row as a companion — **the sync-in can no longer
produce it** — and asserting the repair leaves it alone. Reverting the guard
now fails. `submissions` is a preserved table, so those rows are real, not
hypothetical.

**The general lesson, which is new here:** when a writer fix changes what a
column holds, every test whose *fixture* went through that writer may have
moved off the branch it was written to cover — silently, while staying green.
Changing an expectation from `isNull` to `''` is the visible half; the
invisible half is that no fixture reaches the old branch any more. **After a
writer change, re-run the mutation that the affected guards were written
against, not just the phase's own tests.**

## `type`, and why it is reported rather than fixed

The inherited report says *"the same divergence exists on `type`"*. In the
column, it does. **At every reader, it is invisible**, and the reason matters
for whoever picks it up:

* Every port predicate on `submissions.type` is a **positive equality** —
  `getExamSubmissionsByUser`'s `type = 'exam'`, `getSurveySubmissionsByUser`'s
  `type = 'survey'`, `countByUserParentAndType` — and `'' = 'exam'` and
  `NULL = 'exam'` both fail to select. Moving the writer buys no behavioural
  parity at all.
* What it *would* buy is two blank labels.
  `submission_detail_screen.dart:80` falls back
  `submissionDisplayTitle(row) ?? row.type ?? l10n.submission` and `:91` renders
  `row.type ?? '—'`; neither fallback catches `''`, so a typeless submission
  would lose its title and its type row. That file is on **no lane's set** this
  round, and half a pair across two lanes is worse than a clean hand-over.

So `type` stays null this round, and the *decision* is pinned rather than the
divergence: one test asserts it is still null with the reason, another asserts
no positive predicate distinguishes — which is the guard that trips if a later
phase adds a negated type predicate without moving the writer with it. A third
pins the narrow consequence that remains: `serialize` still sends
`type: 'survey'` where Kotlin echoes the `""` it received, reachable only once a
local edit sets `isUpdated` on a synced typeless row.

## Reported, not fixed

1. **`type` in the submissions sync-in**, as above. The complete change is
   `submissions_repository.dart`'s `type:` line to `JsonUtils.getString`, plus
   `submission_detail_screen.dart:80` and `:91` made `''`-aware with the
   `row.type?.trim().isNotEmpty == true ? … : …` idiom the list screen and now
   the exporter both use. One slice, one owner, three lines.
2. **Three surfaces now disagree about a status-less submission's label, and
   `submission_detail_screen.dart:84` is the one left behind.**
   `row.status ?? l10n.pending` does not catch `''`, so a status-less synced
   submission shows a blank status label there while the list and the PDF say
   "Pending". It matches Kotlin, which shows blank too
   (`SubmissionsRepositoryImpl.kt:310`'s `?: "Unknown"` fires on null only), so
   this is a port-internal inconsistency rather than a parity gap — but it is
   one *this phase introduced* and it is one line to close, in a file this lane
   does not own.
3. **`sender`, `source`, `parentCode` and `teamId` carry the same writer-side
   divergence with no reader that can see it.** Swept: `sender`, `source` and
   `parentCode` on `submissions` are read by **no predicate anywhere** in the
   port (and Kotlin's uploader supplies `source`/`parentCode` from arguments,
   not from the row). Moving them is parity tidying with no behavioural
   payload — worth doing in one pass with item 1, not worth a phase.

   **`teamId` needs a sharper statement than "no payload", though.** The
   *writer* change really is inert, because `byUserWithoutTeam`
   (`app_database.dart:2688-2694`) and `pendingSurveySubmissions` are written
   `teamId.isNull() | teamId.equals('')` and accept both. But that disjunct is
   itself a divergence in the other direction: Kotlin's
   `getByUserIdWithoutTeam`, `getUniquePendingSurveyCandidates` and
   `deletePendingSurveyOrphans` are plain `teamId IS NULL`
   (`SubmissionDao.kt:14`, `:20`, `:39`) while Kotlin's sync-in stores `''`, so
   **Kotlin's own readers match no synced team-less submission at all** and the
   port's match every one. A Kotlin bug the port does not reproduce, and
   `SurveysRepository.findExistingAdoption`'s non-team path sits downstream of
   it. **Anyone who "tidies up" the `equals('')` disjunct after item 1 lands
   will break `byUserWithoutTeam` for every pulled row.**

   And **`parentId` belongs on this list**, which presented itself as
   complete and was not: `submissions_repository.dart`'s `parentId` is
   `getStringOrNull` where Kotlin's `:661` is `getString`, and Kotlin *does*
   have a distinguishing reader for it (`SubmissionDao.kt:40`,
   `parentId IS NOT NULL`). Latent in the port — no port predicate
   distinguishes it — but it is the same divergence.
4. **`course_progress.userId` is a second live instance, and my first sweep
   missed it.** Kotlin's `ProgressRepositoryImpl.kt:260` writes
   `JsonUtils.getString("userId", act)` → `''`; the port's
   `course_progress_mapper.dart:54` writes `getStringOrNull` → NULL. The reader
   `CourseProgressDao.getByUserAndCourseIds` (`app_database.dart:4005-4015`)
   uses `equalsNullable`, drift's spelling of `IS`, matching Kotlin's `IS` — so
   on a `courses_progress` document that omits `userId`, **Kotlin's
   `userId IS NULL` finds nothing and the port's finds the row**, attributing
   its `passed`/`stepNum` to every null-user progress read. Already documented
   in the port at `app_database.dart:3993-4002`, which names
   `PHASE_135_NOTES.md` and says the mapper is the side that should move.
   Verified here against both sources.

   **Why my sweep missed it, because the method matters more than the miss.**
   I swept from the readers rather than the 198 writers, which was right, but
   with an incomplete list of distinguishing predicates: negated equality,
   `LIKE`, `IS NOT`, and a Dart `??`. **`IS NULL` / `equalsNullable` also
   distinguishes** — it matches NULL and not `''` — and it is a *positive*
   predicate, so it did not look like a candidate. Anything with a null-safe
   comparison belongs on that list. `teamId`'s
   `isNull() | equals('')` disjuncts in item 3 are the same family, already
   written to accept both.
5. **Everything else in the reader sweep is clear, and that is a measurement
   rather than an impression.** With the corrected predicate list, every
   remaining site that can distinguish `''` from NULL is one of: already
   explicitly null-safe (`teams.status`' four `isNull() | …not()` readers,
   `health.getUpdated`'s `userId.isNotNull()`, the `rev.isNotNull() &
   rev.equals('').not()` pairs, the generic distinct helper); on a
   **non-nullable** column (`teamTasks.status`, default `'active'`, whose
   comment already records this exact reasoning; `achievements.id`, a primary
   key); written by `getString` already (`tags.name`, so `parentTags`'
   `name IS NOT ''` — **true** for NULL — is safe; `newsEntries.docId`, which
   matters because it feeds a destructive `deleteNotIn`); deliberately matching
   Kotlin (`offlineActivities.pendingLoginUploads`, where Kotlin's
   `getUnuploadedLoginActivities` drops null-`userId` rows after the query
   too); or **latent but unreachable**: `ratings.pendingUploads` and
   `courseProgress.getPendingUploads` both negate `userId LIKE 'guest%'` on a
   nullable column, where a NULL would be silently stranded rather than
   uploaded — but the rating writer takes a `required String userId`, only
   local writes set `isUpdated`, and the progress query additionally requires
   `couchId IS NULL`, which excludes every synced row. Both are one careless
   writer away from real; neither is real today.

   One general rule fell out of it, and one claim that looked like a rule and
   was false. **`_id` and `_rev` cannot diverge on this axis *for a top-level
   document*** — CouchDB always returns a non-empty `_rev` from
   `_all_docs?include_docs=true`. My first draft stated that unqualified, as
   something that "cuts the 198 call sites down sharply", and the
   implementation audit refuted it with the phase's own material: not every
   mapper input is a top-level document. `survey_mapper.dart:145` and
   `exam_mapper.dart:247` read `_rev` from a survey or exam **embedded in a
   course document**, which has none — and item 6 below describes exactly that
   as the Phase-138 defect, two paragraphs from where I wrote the rule. And **sometimes the port's null is
   better and must not be "fixed" toward Kotlin**: `my_library_mapper.dart:73`
   reads `year`, and Kotlin's `getString` blanks a JSON *number* (its
   `isString` test's false branch returns `""`), so a resource with
   `"year": 2019` shows no year in the Kotlin app and shows `2019` here. The
   port's `getString` differs from Kotlin's in exactly that case, which the
   helper's own doc comment described incompletely; same class for any
   numeric-valued string column.
6. **A counter-precedent to blanket-(W), worth knowing before the next one.**
   `surveys.rev`/`exams.rev` carried this same fold and were fixed on the
   **reader/schema** side instead: Kotlin's `ExamDao.kt:21`
   (`sourceSurveyId IS NOT NULL AND _rev IS NULL`) names one writer only
   because its sync writers store `''`, while the port's `getStringOrNull`
   made `rev` NULL for every course-embedded survey — and Phase 138 found the
   resulting sweep POSTing another team's private copy under the signed-in
   user's credentials. The remedy chosen there was a new local-authority
   column, `Surveys.needsSync`, not a writer change. So (W) is the right call
   for `status` on the argument above, and it is not automatically the right
   call for the next column: **ask whether the port needs to distinguish local
   authorship, and if it does, say so with a column rather than with `''`.**
7. **`app_database.dart:2946-2954` explains `isATeamAdoptionMarker`'s
   strictness with a clause this fix makes false**: *"the sync-in stores null
   for a document that omits the key"*. It no longer does, for `status`. **The
   predicate should stay** — rows a pre-Phase-151 build stored are still NULL
   on device, and two of that table's own tests build a status-less row to
   probe the guest test — but its argument now rests on those two things
   rather than on the sync-in. Handed over rather than edited: *making an
   existing statement true* is a stop-and-report carve-out, but
   `app_database.dart` is **Lane A's named file this round**, and a carve-out
   is not a licence to edit into a live collision. (The same correction in
   `submissions_repository.dart` and `surveys_repository.dart` is done — the
   first is this lane's file, the second is unowned and comment-only.)
8. **The port does not pull `submissions` in the sync center at all**, which
   is how item 3's caveat arises and is a bigger gap than the caveat.
   `DashboardSyncArea` has sixteen members and no `submissions`
   (`dashboard_sync_provider.dart:30-47`, and `:276` records it), while Kotlin
   pulls the table on every full sync (`SyncManager.kt:209` →
   `HeavyTableSyncWorker.ALL_HEAVY_TABLES`). So a submission made on Planet
   web or another handset reaches this device only when the learner opens the
   Submissions screen and taps refresh. Pre-existing and already noted in that
   file; repeated here because this phase's repair depends on it.
9. **Kotlin's one negated `type` predicate belongs to a feature the port does
   not have.** `SubmissionDao.kt:32`'s `type != 'survey'` — where `'' !=
   'survey'` is true, so Kotlin *deletes* a typeless submission and the port
   would keep it — is reached only from
   `CoursesRepositoryImpl.deleteCoursesProgress`, when a learner removes a
   course from their shelf. The port has no counterpart to that method at all.
   A separate pre-existing gap, and the reason item 1's `type` change stays
   safe in the meantime.
10. **The port's `JsonUtils.getString` is not quite Kotlin's.** Kotlin's
   returns `""` for a non-string primitive (the `isString` test's false
   branch); the port's does `value.toString()`. The port's behaviour is the
   better one — see item 5's `year` example — but the helper's doc comment
   said only "missing/null becomes `''`", which is half the Kotlin. Left as
   is, deliberately, and now described accurately.
11. **`app_database.dart:2907-2911` carries the same falsified claim** as item
   7's range — *"`json_utils.dart:19-22` turns the empty string back into null
   on a re-pull"* — and is additionally stale against its own code, since it
   justifies a `coalesce` that `isATeamAdoptionMarker` does not use. Two
   ranges in Lane A's file, one hand-over. Its `json_utils.dart:19-22`
   citation is also now off by ~37 lines, because this phase's own doc
   comments moved `getStringOrNull`'s body; the same stale citation is fixed
   in the two test files and in this lane's own new one.
12. **`submissionStatusLabel` trims and `submissions_screen.dart:248` does
   not**, so a status of `'  graded  '` renders trimmed in the PDF and padded
   in the list. The trim is deliberate (a PDF table cell has no layout that
   absorbs leading whitespace) and now pinned by a test; the screen's line is
   outside this lane's set. One line if anyone wants them identical.
13. **The trap is now documented at the helper**, since that is the layer with
   198 callers: `getStringOrNull`'s dartdoc states the three-valued-logic
   mechanism, names this defect as the instance that reached a learner, lists
   the three earlier reader-side patches, and says to prefer `getString` on a
   sync-in path unless the column's readers are all positive equalities and
   nothing reads it with a `??` fallback.

## Two process notes, both mine

**An audit finding is a claim like any other.** The ground-truth pass told me
my "self-healing by the next ordinary sync" was false because `sync()` has one
caller. I took it, wrote it into the code and these notes, and made it the
phase's headline lesson — without running the check its own finding said I had
skipped. It has two callers, one of them headless and on by default, so the
original claim was substantially right and the correction was the error that
shipped. Phase 149 recorded that *a supplied replacement sentence is a claim,
not a patch*; this is the same failure with an audit as the supplier, which is
worse, because an audit finding arrives pre-labelled as verified. **Open the
citation even when the correction comes from something whose job was checking.**

**Do not `git checkout <file>` to revert a mutation while the fix is
uncommitted.** Twice it restored HEAD and silently deleted the change under
test — once the whole writer fix and its comment, once the exporter
extraction — and each time the next test run passed for the wrong reason,
because the mutation *and* the fix were gone together. `git status` caught
both, which is why `CLAUDE.md` says to read the tree after an audit rather
than stage it. The fix is to **commit before mutation-testing**, so a revert
restores the fix instead of erasing it; after that, every mutation behaved.
Seven mutations (M1–M7) then each failed exactly one test, and the two
predicate mutations were run against a committed tree deliberately.

## Method

Ground-truth `parity-auditor` pass at `effort: max` on the Kotlin **before**
implementing, plus an independent hand reading of the same Kotlin (JsonUtils,
the sync-in, `SubmissionDao`, `Submission`'s nullability, `serializeSubmission`,
the exporter and the three UI status readers) — the two agreed on the finding
the phase turned on, which is the argument for doing both. Then: failing test
first, implement, mutation-test every pin, second `parity-auditor` pass at
`effort: max` on the finished green code, `git status` read after each audit
pass.
