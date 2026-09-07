# Phase 129 — a submission's `team` object (the harvest's routed remainder)

Lane B of a three-lane round. Ports the three behavioural changes Phase 126
verified in upstream `aca425a` ("teams: smoother submissions repository
stamping", fixes #16664) and routed rather than applied, because
`lib/repository/submissions_repository.dart` was another lane's file that
round.

PR #16919. Branch `claude/submission-team-object-w6p3`.

Two `parity-auditor` passes at `effort: max` ran per the standing rule — one on
the Kotlin ground truth, one on this lane's own finished, green code. **Both
found real defects**, including two claims of mine that were simply false, and
the corrections are folded in below rather than appended, so nothing here reads
as the first draft did.

## What a submission carries, before and after

The cache-miss case is the one that matters, because it is not an edge case:
an incomplete local `teams` table is the *normal* state of a handset that has
not finished syncing, and it is exactly the state in which the old code threw
the association away.

| | before | after |
|---|---|---|
| adopted team survey, team document **present** | no `team` key at all | `{"_id": …, "name": …, "type": …}` |
| adopted team survey, team document **absent** | no `team` key at all | `{"_id": …}` |
| team **survey** sheet, team present | no `team` key, and no `teamId` on the row | row carries `teamId`; `{"_id", "name", "type"}` — *once a caller passes one, see below* |
| team **survey** sheet, team absent | no `team` key, no `teamId` | row carries `teamId`; `{"_id"}` — *same* |
| no team | no `team` key | no `team` key |
| blank/whitespace team id | no `team` key | no `team` key |

`_id` alone is enough: Planet attributes a submission by `team._id`, so a
document with the id and no name is attributable and one with neither is not.
`name` and `type` are **omitted, never sent as null** — Kotlin guards each with
`?.let { addProperty(…) }` (`resolveTeamJson:806-808`), and a null would
overwrite a name the server already holds.

`type` is also no longer defaulted to `"team"`. The pre-change Kotlin wrote
`team.type ?: "team"`, which mislabelled an enterprise whose own document said
otherwise; it now travels only when the local document has one. The audit
sharpened this: `?: "team"` was already near-dead, because `MyTeam.type` comes
from `JsonUtils.getString`, which returns `""` rather than null, and local
creation always sets `"team"`/`"enterprise"`. **The live delta is `""` →
omitted, where the old code sent `"type": ""`.**

## The three items, each demonstrated failing first

Run against the pre-fix tree (`git stash push lib/`, temporary test file,
output kept at `prefix_evidence.txt` in the session scratchpad):

```
DEFECT 3: an adopted team survey uploads no team object
  Expected: {'_id': 'team-1', 'name': 'Water quality group', 'type': 'enterprise'}
    Actual: <null>
DEFECT 3b: a team whose document is absent loses its id too
  Expected: {'_id': 'team-missing'}
    Actual: <null>
DEFECT 1: an exam attempt cannot record the team it was taken for
  Expected: 'team-1'
    Actual: <null>
  the attempt was started from team-1
```

**Item 3 is the one with effect today, so it leads.** The port's `serialize`
emitted no `team` key at all, and its reader is reachable through two writers
of `submissions.teamId`. They are not equally good evidence, and the first
draft of these notes ran them together, which was wrong:

* **the sync-in** (`upsertDocuments`, from `team._id` falling back to
  `user.membershipDoc.teamId`). A synced team submission the user re-answers
  goes `isUpdated = 1` and is re-uploaded — through `getPendingSubmissions` in
  Kotlin, `pendingUploads` here — and now carries the team back. **This one is
  parity: it is live in both apps, and pre-change both lost the team.**
* **the team-survey adoption marker** (`createSurveyAdoptionSubmission`, which
  writes `teamId` and `isUpdated: true`). This one is reachable *in the port
  only*, and rests on a pre-existing divergence rather than on Kotlin
  behaviour: Kotlin's `createMappedSubmission` writes `status = ""` and sets
  the `@Ignore`d `membershipDoc` instead of the column, and
  `getPendingSubmissions` requires `status = 'complete'` — **so Kotlin never
  uploads that document at all**, while the port's `pendingUploads` gates on
  `isUpdated` and does. Verified in both trees. The divergence predates this
  phase and is not its to fix, but a claim resting on it is not a parity
  claim.

**Item 2** is the lookup, and it is what makes item 1's "unconditionally"
survive contact with a real device.

**Item 1 is the writer half, and the first cut put it on the wrong arm.** The
port splits Kotlin's single `createExamSubmission` into an exam writer
(`startExamSession`) and a survey writer (`createSurveyDraft`), and I gave the
parameter to the exam one — which is the arm **Kotlin itself cannot reach with
a team.** Every Kotlin site that sets `isTeam = true` also sets
`type = "survey"` (`SubmissionsAdapter.openSurvey:114-121` for the team's
surveys tab, `PublicSurveyActivity:99-107` for a deep link), while
`CourseStepFragment`'s exam launch passes no team argument at all, so a graded
course exam can never carry one. Both writers now take `teamId` and persist it
unconditionally when non-blank; `createSurveyDraft` is the one a team reaches,
and `startExamSession` keeps it only because it is `createExamSubmission`'s
exam arm and dropping it would leave the port's split unable to express its own
specification. Neither has a caller yet — see *Reported, not fixed*.

## Cancellation versus exception

**The first cut of this section was wrong, and the audit caught it.** It said
"Dart has no cancellation exception — a `Future` is not cancellable". Drift
ships one, `package:drift/drift.dart` exports it, and it
`implements Exception`:

```
drift-2.34.3/lib/src/runtime/cancellation_zone.dart:82
  final class CancellationException implements Exception {
```

and the port's production executor is `NativeDatabase.createInBackground`,
whose isolate server wraps every `ExecuteQuery` in `runCancellable`
(`drift/src/remote/server_impl.dart:121-131`). So the precise hazard Kotlin
names a type to avoid — a lone `catch (_: Exception)` swallowing a cancellation
— exists here with the same shape, and `on Exception` alone swallowed it.

No path reaches this one-shot `getSingleOrNull` from inside a cancellation zone
today (only `QueryStreamFetcher.fetchData` enters one), so nothing was broken.
That is not a reason to leave it: the reasoning is what the next reader
inherits, and a stream fetch or `computeWithDatabase` would have made the
comment's own mistake real.

Kotlin's rethrow therefore **ports literally**, not by analogy:

* `CancellationException` → rethrow;
* any other `Exception` — a drift or IO failure, the thing Kotlin absorbs →
  `null`, the cache-miss path, and the upload keeps its `team._id`;
* an `Error` → propagates.

The last of those is **deliberately stricter than Kotlin**, and worth stating
because it is a divergence rather than a port. Reading a closed database throws
Dart `StateError`, where Room throws `IllegalStateException` — an `Exception`
Kotlin's catch absorbs. Nothing in `lib/` closes the database, so the case does
not arise in production; when it does arise it is a defect in the caller, and
`serialize` rethrowing aborts `SubmissionsUploader.queuePending` for the whole
batch rather than queueing rows with a silently under-populated team. Loud
beats silent here.

All three arms are pinned by tests, with a `TeamDao` whose one read fails.

## The corrected doc comment — and a correction to Phase 126's note

Phase 126 recorded that "the port's own doc comment says so
(`serializeSubmission` emits none)". **That quotation is of the wrong comment.**
The text it quotes is in `serialize`'s doc block at
`submissions_repository.dart:981` and is about the **top-level `userId`** key,
not the team — and it is still true, so it is not stale and has not been
touched.

The genuinely stale claim is in `_userDocument`'s doc block, which said
`membershipDoc` is

> where the sync-in recovers `teamId` from when the document has no `team`
> object, so omitting it dropped a team submission's team on every round trip

— i.e. it assumed the port's uploaded document never has a `team` object,
which stops being true here. Corrected in place: `membershipDoc` is now the
*fallback* the sync-in reads when `team` is absent, both are sent as Kotlin
sends both, and the sync-in prefers `team._id`. The comment also now records
why Kotlin rebuilds `membershipDoc` on every read rather than persisting it
(`Submission.membershipDoc` is `@Ignore`d, `hydrateSubmissions:73`), which is
the same reason the field covers rows whose `teamId` came from the server.

## Kotlin's `teamObject` branch has no port counterpart, and needs none

`resolveTeamJson` prefers `submission.teamObject?._id` over
`submission.teamId`. `Submission.teamObject` is `@Ignore`d
(`Submission.kt:20`) — never persisted — and both Kotlin serializers are
reached from `UploadConfigs` with rows fetched through
`getPendingExamResults`/`getPendingSubmissionsForUpload`
(`UploadConfigs.kt:242,257`), which rehydrate `membershipDoc` and not
`teamObject`. So the field is null on the upload path and the `teamId`
fallback is the branch that runs. The port's `serialize` likewise only ever
receives a persisted `SubmissionRow`.

This is also why item 1 matters as much as it does — though **"the only
durable carrier" was too strong, and the audit corrected it.** `teamObject`
and `membershipDoc` are both `@Ignore`d, but the same gated block also wrote
the team id into the **persisted `user` TEXT column**
(`{"membershipDoc":{"teamId":…}}`), which is a real column. So the pre-change
loss was partial, not total. It was still a loss: that blob is overwritten by
the respondent profile when the user-information dialog is submitted
(`UserInformationFragment` -> `SubmissionDao.markComplete`) and dropped on
upload whenever the live `users` row resolves, since both serializers prefer
`UserEntity.serialize()`. The column is the only carrier that survives both.

## What was touched, region by region

`lib/repository/submissions_repository.dart` is shared at section level this
round; Lane A owns the read path (`hasSubmission` and the submission queries).
**Every edit here is on the write side.** No read-path method was read, called
or changed; nothing in the file was renamed or moved.

| region | change |
|---|---|
| constructor + fields (`:18-50`) | `required TeamDao teamDao` named parameter, `_teamDao` field |
| `startExamSession` doc + signature | `String? teamId`, doc for it and for its missing caller |
| `_openExamSession` | `teamId` parameter, `persistedTeamId` blank-guard, `teamId: Value(...)` on the companion |
| `createSurveyDraft` | the same three, plus doc for why this is the arm a team reaches |
| `serialize` | `final team = await _resolveTeamJson(row)` and `'team': ?team` after `type` |
| `_userDocument` doc block | the stale `team`-object claim corrected |
| new, placed after `_userDocument` | `_resolveTeamJson`, `_teamByIdOrNull` |

Two citation ranges in pre-existing comments were also corrected in place:
`serializeSubmission (:813-862)` now reads `:820-865`, because `aca425a`
deleted comment lines above that function. The behaviour those comments
describe is unchanged.

`lib/providers/app_providers.dart` — one line, `teamDao:` on
`submissionsRepositoryProvider`.

The dependency is a **required** named parameter rather than an optional one on
purpose. An optional team lookup that silently degrades a document's contents
is the exact shape this project keeps getting burned by; the compiler now makes
every construction site say what it wants. That cost 20 one-line additions
across 15 test files — most in a `setUp`, six inside test bodies
(`surveys_repository_test.dart`, `dashboard_providers_test.dart`,
`course_progress_screen_test.dart`, `take_course_screen_test.dart`,
`take_exam_screen_test.dart`, `submissions_sync_round_trip_test.dart`) — plus
two `Fake` overrides of `startExamSession` in
`test/ui/exam/take_exam_screen_test.dart` that the signature change forced.
The provider takes `teamDaoProvider` rather than `teamsRepositoryProvider`
deliberately: the repository transitively reaches `planetPrefsProvider`, which
is `UnimplementedError` in the widget-test harness (the Phase 75/99 trap).

## Tests

`test/repository/submission_team_object_test.dart`, 18 new tests: the resolved
and unresolved team, blank name/type, one key resolving where the other does
not, the absent `type` (no `"team"` default), no team, a blank team id,
`membershipDoc` surviving alongside the new key, the three lookup arms
(cancellation, `Exception`, `Error`), three on the survey writer and four on
the exam writer.

**Mutation-tested.** The implementation audit injected 11 mutations and all 11
were killed; I then mutation-tested the three behaviours added after it —
removing the cancellation rethrow, dropping `createSurveyDraft`'s `teamId`
write, and weakening the blank-name guard to `isEmpty` — and each fails a test
that names it. One test the audit found *redundant* (it could not fail unless
the exact-equality test above it failed first) was retargeted to the mixed case
Kotlin's two independent guards allow: name resolvable, type blank.

## Reported, not fixed

### The port's answer sheets carry no team, because nothing plumbs one

This is the reachability half of item 1 and it is a **named gap, not a finished
feature**. `startExamSession(teamId:)` exists and is tested, and no caller
passes it.

In Kotlin the team context is fragment arguments: `TeamPagerAdapter` opens the
team's surveys tab with `putBoolean("isTeam", true)` plus the team id,
`BaseExamFragment` reads both (`:77-78`), and `ExamTakingFragment` passes
`if (isTeam) teamId else null` into `CreateExamSubmissionRequest` (`:124-125`,
`:151-152`).

In the port, `TeamSurveysScreen` (`lib/ui/teams/team_surveys_screen.dart:67`)
pushes `'${Routes.surveys}/${survey.id}'` and drops the team on the floor, so
**every answer sheet the port writes is team-less where Kotlin's carries the
team.** Closing it is four edits, all outside this lane's file set:

1. `lib/ui/router.dart` — a `teamId` query parameter on the survey route;
2. `lib/ui/surveys/take_survey_screen.dart` — accept it and pass it on;
3. `lib/repository/surveys_repository.dart` — `submitResponse` is the link
   between the screen and `createSurveyDraft`, and it has no `teamId`
   parameter;
4. `lib/ui/teams/team_surveys_screen.dart` — send it.

**Not exam-only — in fact not exam at all.** The port's exam route is
`'/courses/exam/:examId'`, reached from a course step, where no team context
exists or can; Kotlin's exam branch is dead with respect to teams for the same
reason. `createSurveyDraft` is the writer to wire.

**A second site, with the value already in hand.**
`lib/ui/surveys/public_survey_screen.dart:245-253` calls `createSurveyDraft`
while `widget.teamId` sits two statements away (it is passed to
`UserInformationScreen(teamId: …)` at `:261`). Kotlin's
`PublicSurveyActivity:99-107` launches the sheet with `isTeam = true` and the
team id, so `createExamSubmission` stamps the column. The effect is small — the
public POST carries the team in its URL, and the port excludes `public_%`
owners from `pendingUploads` — but there are two Kotlin sites, not one, and the
first cut of these notes named only the first.

Found on the same trail and pre-existing: `UserInformationScreen.teamId`
(`lib/ui/exam/user_information_screen.dart:39,45`) is declared and never read,
while `UserInformationFragment` uses it at `:155-157` and `:296-299`.

### The stored `user` blob's `age`/`gender`

Kotlin's `createExamSubmission` writes `user` as
`{"age": dob, "gender": gender, "membershipDoc": {"teamId": …}}` when a team is
present. The port writes no `user` column there, and deliberately: the
`membershipDoc` half is already synthesized at serialize time from the row's
`teamId` (`_userDocument`), which is a superset — it also covers rows whose
`teamId` arrived from the server, exactly as Kotlin's `hydrateSubmissions`
does. The `age`/`gender` half would need the profile fields the port's
exam-session caller does not hold, and on the document that actually reaches
Planet the stored blob is only Kotlin's *fallback* — both serializers prefer
the live `UserEntity.serialize()`. The port having no live user lookup is a
pre-existing recorded gap (`_userDocument`'s doc block), not something this
phase changed.

### The sync-in stores `NULL` where Kotlin stores `''`, and a live query splits on it

Surfaced by the ground-truth audit, verified in both trees, **pre-existing and
not this phase's** — but it has a live caller and it is a read-path query, so
it is reported rather than touched (Lane A's region this round).

Kotlin's `upsertRoomSubmissionsFromSync` writes `teamId` from
`JsonUtils.getString`, which returns `""` and not null, so a synced *team-less*
submission carries `''`. `SubmissionDao.getByUserIdWithoutTeam` then tests
`teamId IS NULL`, which **does not match `''`** — so Kotlin excludes such rows
from a user's personal survey list. The port's `upsertDocuments` writes `NULL`
and `SubmissionDao.byUserWithoutTeam` matches `isNull() | equals('')`, so it
includes them. Same server document, different list. The live caller is
`surveys_repository.dart:142-143`, the team-versus-personal survey split.

The port's behaviour is arguably the better one; Kotlin is the specification,
quirks included, so somebody should decide deliberately rather than by
accident. Two smaller consequences of the same `''`: Kotlin uploads
`user.membershipDoc = {"teamId": ""}` for such a row where the port omits the
key, and Kotlin's `deletePendingSurveyOrphans`/`getUniquePendingSurveyCandidates`
skip it too.

### Item 4 of Phase 126's routed list was not assigned to this lane

Phase 126's "To Lane A — `aca425a`" list has a fourth item, the `27c0470`
site: a submission's nested `parent` should carry `androidId` and `app`,
because `StepExam.serializeExam` is shared with the `exams`-database upload,
and the port's `examParentDocument` is `static` with no `DeviceIdentity` seam.
This lane's brief enumerates items 1–3 only, and `examParentDocument` sits
between the two owners' regions, so it was left alone. It is still open.
