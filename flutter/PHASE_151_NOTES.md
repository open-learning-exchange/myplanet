# Phase 151 — `""` and null are not the same thing, and a learner paid for it

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
course step's **Next** button unlocks.

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

1. **The port has already paid for the reader-side approach three times**, once
   per phase, each time as a separate defect report: `pendingUploads`' coalesced
   guest operands, `SubmissionsRepository._repairSurveyParentId`'s
   `coalesce([row.status, Constant('')]).equals('').not()`, and
   `SurveysRepository`'s adoption guard `(row.status ?? '').isEmpty`. The step
   count is the fourth reader of the same column and nobody remembered it. A
   rule enforced at one writer is enforced; a rule enforced at N readers is a
   rule that gets broken again — which is Phase 148's *one rule at the right
   layer beat twenty special cases*, and Phase 78's `normalizeText`, and Phase
   95's `ProfileAvatar`.
2. **`''` and NULL select identically under every positive equality**, so the
   change can only ever *include* rows Kotlin includes. It cannot strand
   anything, which is the failure mode this project keeps paying for.
3. **It is self-healing on handsets that have already synced.** `sync()` walks
   `_all_docs?include_docs=true` in full every run and re-upserts every page
   (and there is deliberately no `deleteNotIn`), so a row a shipped build stored
   as NULL is rewritten to `''` by the next ordinary sync. Neither option
   repairs an existing row *retroactively* — the brief was right to ask — but
   the writer fix needs no migration to converge, where the reader fix would
   have to be correct for both states for ever.

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

`test/repository/submission_empty_vs_null_status_test.dart`, 11 tests. Six
failed on the pre-fix code, the headline one with `Expected: <1> Actual: <0>`.

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
2. **`submission_detail_screen.dart:84` is now the odd one out on `status`.**
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
   not from the row); `teamId`'s null-distinguishing readers,
   `pendingSurveySubmissions` and the team-scoped pair, are already written
   `teamId.isNull() | teamId.equals('')` and accept both. Moving them is parity
   tidying with no behavioural payload — worth doing in one pass with item 1,
   not worth a phase.
4. **The port-wide sweep found no second live instance, and that is a
   measurement rather than an impression.** `getStringOrNull` has **198 call
   sites**, which is not auditable one by one, so the sweep ran from the
   *readers* instead — the only predicates that can distinguish `''` from NULL
   are a negated equality, `LIKE`, `IS NOT`, and a Dart `??` fallback. Every
   such site in the port is one of: already explicitly null-safe
   (`teams.status`' four `isNull() | …not()` readers, `health.getUpdated`'s
   `userId.isNotNull()`, the two `rev.isNotNull() & rev.equals('').not()`
   pairs); on a **non-nullable** column (`teamTasks.status`, default `'active'`,
   whose comment already records this exact reasoning; `achievements.id`, a
   primary key); written by `getString` already (`tags.name`, so `parentTags`'
   `name IS NOT ''` — which is **true** for NULL — is safe); deliberately
   matching Kotlin (`offlineActivities.pendingLoginUploads`, where Kotlin's
   `getUnuploadedLoginActivities` drops null-`userId` rows after the query too);
   or **latent but unreachable**: `ratings.pendingUploads` and
   `courseProgress.getPendingUploads` both negate `userId LIKE 'guest%'` on a
   nullable column, and a NULL there would be silently stranded rather than
   uploaded — but the rating writer takes a `required String userId` and only
   local writes set `isUpdated`, and the progress query additionally requires
   `couchId IS NULL`, which excludes every synced row. Both are one careless
   writer away from being real; neither is real today. All of these live in
   `app_database.dart`, Lane A's file, so all are report-only either way.
5. **The trap is now documented at the helper**, since that is the layer with
   198 callers: `getStringOrNull`'s dartdoc states the three-valued-logic
   mechanism, names this defect as the instance that reached a learner, lists
   the three earlier reader-side patches, and says to prefer `getString` on a
   sync-in path unless the column's readers are all positive equalities and
   nothing reads it with a `??` fallback.

## Method

Ground-truth `parity-auditor` pass at `effort: max` on the Kotlin **before**
implementing, plus an independent hand reading of the same Kotlin (JsonUtils,
the sync-in, `SubmissionDao`, `Submission`'s nullability, `serializeSubmission`,
the exporter and the three UI status readers) — the two agreed on the finding
the phase turned on, which is the argument for doing both. Then: failing test
first, implement, mutation-test every pin, second `parity-auditor` pass at
`effort: max` on the finished green code, `git status` read after each audit
pass.
