# Phase 129 — a submission's `team` object (the harvest's routed remainder)

Lane B of a three-lane round. Ports the three behavioural changes Phase 126
verified in upstream `aca425a` ("teams: smoother submissions repository
stamping", fixes #16664) and routed rather than applied, because
`lib/repository/submissions_repository.dart` was another lane's file that
round.

PR #16919. Branch `claude/submission-team-object-w6p3`.

## What a submission carries, before and after

The cache-miss case is the one that matters, because it is not an edge case:
an incomplete local `teams` table is the *normal* state of a handset that has
not finished syncing, and it is exactly the state in which the old code threw
the association away.

| | before | after |
|---|---|---|
| adopted team survey, team document **present** | no `team` key at all | `{"_id": …, "name": …, "type": …}` |
| adopted team survey, team document **absent** | no `team` key at all | `{"_id": …}` |
| team exam attempt, team present | no `team` key, and no `teamId` on the row | row carries `teamId`; `{"_id", "name", "type"}` |
| team exam attempt, team absent | no `team` key, no `teamId` | row carries `teamId`; `{"_id"}` |
| no team | no `team` key | no `team` key |
| blank/whitespace team id | no `team` key | no `team` key |

`_id` alone is enough: Planet attributes a submission by `team._id`, so a
document with the id and no name is attributable and one with neither is not.
`name` and `type` are **omitted, never sent as null** — Kotlin guards each with
`?.let { addProperty(…) }` (`resolveTeamJson:806-808`), and a null would
overwrite a name the server already holds.

`type` is also no longer defaulted to `"team"`. The pre-change Kotlin wrote
`team.type ?: "team"`, which mislabelled an enterprise whose own document said
otherwise; it now travels only when the local document has one.

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
emitted no `team` key at all, and its reader is already reachable: two writers
put a value in `submissions.teamId` today — `createSurveyAdoptionSubmission`
(a team survey's adoption marker, which is `isUpdated: true` and so a pending
upload the moment it is written) and `upsertDocuments` (the sync-in, from
`team._id` or `user.membershipDoc.teamId`). Both went up with the team dropped
from the document body.

**Item 1** is the writer half. The port's exam-session writer took no team at
all, so the column was never written from this device; `startExamSession` now
takes `teamId` and persists it **unconditionally when non-blank**, which is the
upstream fix. See *Reported, not fixed* — nothing passes it yet.

**Item 2** is the lookup, and it is what makes item 1's "unconditionally"
survive contact with a real device.

## Cancellation versus exception

Kotlin's `getTeamByIdOrNull` rethrows `CancellationException` and swallows
every other `Exception`. It **has** to name that type, because in Kotlin a
cancellation *is* an `Exception`: a bare `catch (_: Exception)` would swallow a
cancelled coroutine and turn it into a silent null.

Dart has no cancellation exception — a `Future` is not cancellable — so there
is no type to rethrow. The analogous hazard is `Error`: a `TypeError` or
`StateError` out of this lookup means a defect in this code, not a missing row,
and reporting it as a cache miss would hide it behind a document that merely
looks under-populated.

So the port catches `on Exception`, not a bare `catch`:

* an `Exception` (a drift/IO failure — the thing Kotlin is absorbing) → `null`,
  the cache-miss path, and the upload keeps its `team._id`;
* an `Error` → propagates.

Both halves are pinned by tests, with a `TeamDao` whose one read fails: one
throwing an `Exception` (the document still uploads with `{"_id": …}`) and one
throwing a `StateError` (`serialize` rethrows it).

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

This is also why item 1 matters as much as it does: the column is the *only*
durable carrier, so the pre-change Kotlin's gate on a resolved team did not
merely omit a name — it lost the attribution permanently.

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
| `serialize` | `final team = await _resolveTeamJson(row)` and `'team': ?team` after `type` |
| `_userDocument` doc block | the stale `team`-object claim corrected |
| new, placed after `_userDocument` | `_resolveTeamJson`, `_teamByIdOrNull` |

`lib/providers/app_providers.dart` — one line, `teamDao:` on
`submissionsRepositoryProvider`.

The dependency is a **required** named parameter rather than an optional one on
purpose. An optional team lookup that silently degrades a document's contents
is the exact shape this project keeps getting burned by; the compiler now makes
every construction site say what it wants. That cost 18 one-line additions
across 14 test files (all in `setUp`-style constructor calls, plus two
`Fake` overrides of `startExamSession` in `test/ui/exam/take_exam_screen_test.dart`
that the signature change forced).

## Tests

`test/repository/submission_team_object_test.dart`, 14 new tests: the resolved
and unresolved team, blank name/type, the absent `type` (no `"team"` default),
no team, a blank team id, `membershipDoc` surviving alongside the new key, the
two lookup-failure halves, and four on the exam writer.

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
team.** Closing it is three edits, all outside this lane's file set:

1. `lib/ui/router.dart` — a `teamId` query parameter on the survey route;
2. `lib/ui/surveys/take_survey_screen.dart` — accept it and pass it to the
   submission writer (its writer is `createSurveyDraft` /
   `getOrCreateSurveySubmission`, which need the same `teamId` treatment
   `_openExamSession` just got — Kotlin reaches all of them through the one
   `createExamSubmission`);
3. `lib/ui/teams/team_surveys_screen.dart` — send it.

Whoever takes it should note that `take_survey_screen` is the port's survey
analogue of `ExamTakingFragment`'s survey branch, and Kotlin's survey branch
passes the team too (`:151-152`) — this is not exam-only.

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

### Item 4 of Phase 126's routed list was not assigned to this lane

Phase 126's "To Lane A — `aca425a`" list has a fourth item, the `27c0470`
site: a submission's nested `parent` should carry `androidId` and `app`,
because `StepExam.serializeExam` is shared with the `exams`-database upload,
and the port's `examParentDocument` is `static` with no `DeviceIdentity` seam.
This lane's brief enumerates items 1–3 only, and `examParentDocument` sits
between the two owners' regions, so it was left alone. It is still open.
