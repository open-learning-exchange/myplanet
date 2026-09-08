# Phase 132 — the team context an answer sheet is written with

Lane B of a three-lane round. Branch `claude/survey-team-context-m2p6`, PR
#16929.

Closes the reachability half of Phase 129: `createSurveyDraft(teamId:)` and
`startExamSession(teamId:)` shipped **with no caller**, so the parameter was
correct, tested, green and dead.

Two `parity-auditor` passes at `effort: max` ran per the standing rule — one on
the Kotlin ground truth before any code, one aimed at this lane's own finished
code. Both found real defects, and the ground-truth pass corrected my brief's
own framing; the corrections are folded in below rather than appended.

## The premise, corrected

The brief (and Phase 129's notes) said **"every answer sheet the port writes is
team-less where Kotlin's carries the team."** The first half is true, the second
overstates the comparison, and the audit was right to say so.

Kotlin has **seven** survey journeys (plus a course step's *exam*), and only
**two** carry a team:

| entry point | `isMySurvey` | `isTeam` | `teamId` |
|---|---|---|---|
| `SurveysAdapter:88` from the team detail's Surveys tab | false | **true** | **`team?._id`** |
| `PublicSurveyActivity:100-108` (deep link) | false | **true** | **the link's team** |
| `SurveysAdapter:88` from the individual list | false | false | `null` |
| `SubmissionsAdapter:98` (my-surveys row) | true | false | `""` |
| `BellDashboardFragment:256` (pending prompt) | true | false | `""` |
| `CourseStepFragment:289` (a course step's survey) | false | false | `""` |
| `DashboardActivity:220` (`surveyNavigationEvent`) | false | false | `""` |
| `CourseStepFragment:275-284` (a course step's **exam**) | false | false | absent |

The flag and the id are only ever set together, and `TeamPagerAdapter:89-94` is
the **only** producer of the pair — the other three `SurveyFragment()`
constructions and `BaseExamFragment.navigateToSurveyList` set neither. So five
of the seven port paths were already at parity by doing nothing, and the phase
is about the two that were not.

**The count was "six" in the first draft, and the implementation audit caught
it**: `DashboardActivity:220`'s `surveyNavigationEvent` is an eighth
`openSurvey` call site the table had omitted. It passes `false, false, ""`, so
no port change follows and the conclusion is unaffected — but a table that
claims to be exhaustive has to be.

**The exam arm is confirmed unreachable with a team**, which Phase 129 claimed
and this pass proved from the call sites rather than by analogy: `type` defaults
to `"exam"` (`BaseExamFragment:52`) and is only overwritten from the bundle
(`:95-99`); every survey site hard-codes `putString("type", "survey")`, and the
one bundle that omits it — `CourseStepFragment:275-284` — carries only `stepId`
and `stepNum`, so `isTeam` is false there and `if (isTeam) teamId else null` is
structurally null.

## What landed, and why each piece is where it is

### The chain (four links, one key)

1. **`team_surveys_screen.dart`** pushes
   `'${Routes.surveys}/${survey.id}?teamId=${Uri.encodeQueryComponent(teamId)}'`.
2. **`router.dart`**'s `/life/surveys/:surveyId` reads
   `state.uri.queryParameters['teamId']`.
3. **`take_survey_screen.dart`** takes a nullable `teamId` and forwards it to
   `submitResponse` — and **not** to the resume path.
4. **`surveys_repository.dart`**'s `submitResponse` forwards it to
   `createSurveyDraft`, which blank-guards it as `createExamSubmission:440`
   does.

Plus **`public_survey_screen.dart`**, Kotlin's second team-carrying site, where
the value had been sitting two statements from the call and going only to
`UserInformationScreen(teamId:)`.

Three choices in there are deliberate:

* **`teamId` as the query key, not `team`.** `addResource` and `userInfo`
  already read exactly that key (`router.dart:267,319`), so the alternative
  would have made this the odd one out. The mutation that renames it is one of
  the fifteen below, because a route reading `team` where the push writes
  `teamId` is the Phase 74/100 shape.
* **The location stays a literal at the call site.** A `surveyLocation(...)`
  helper in `router.dart` reads better and would have been *worse*:
  `route_reachability_test`'s scanner reads `'${Routes.x}/…'` literals out of
  `lib/` and **skips `router.dart`**, so centralising would have made this
  phase's one new navigation the one navigation that guard cannot see. It
  handles the query string already (`_resolve` cuts at `?`).
* **One nullable parameter for Kotlin's two arguments.** Sound because the flag
  and the id are only ever set together (above), and because `isTeam` is the
  sole switch on both Kotlin write sites — a `teamId` without the flag is
  discarded there too.

### The profile step — what `UserInformationScreen.teamId` is for

The brief said the unread parameter is a symptom, not the defect. It is, and
this is the defect: **`take_survey_screen` never opened that screen at all**, so
a team survey collected nothing about its respondent.

`continueExam:127-131` sends the last question of a team survey to
`showUserInfoDialog` rather than to the thank-you dialog, and
`showUserInfoDialog:153-164` opens `UserInformationFragment(sub.id, teamId,
exam?.isFromNation != true)`. Its else arm — mark complete, toast, go to the
survey list — is **dead in Kotlin**: `isMySurvey` is false at both team entry
points, and `isFromNation`'s only writer is
`myExam.isFromNation = !parentId.isNullOrEmpty()` (`StepExam.kt:55`) whose sole
caller passes `""` (`SurveysRepositoryImpl.kt:387`). It is **live in the port**,
because `SurveyMapper` reads the key off the document — so the arm is ported
rather than dropped, and a nation survey skips the profile step here too.

The two Kotlin reads of `teamId`, and what each became:

| Kotlin | port |
|---|---|
| `submitForm:151-174` — a non-empty team routes the profile onto the *submission* rather than onto the signed-in user's own document | inert: this screen requires a `submissionId`, so it always takes Kotlin's `saveSubmission` arm anyway. Documented, not coded. |
| `onDismiss:293-311` — a non-empty team uploads, **on every dismissal**: Save, Cancel, an outside tap, the back button | ported: `_queueUpload` is gated on the team id and hangs off the screen's `PopScope`, so all three exits take it |

The toast and `popBackStack` that Kotlin gates on the same value are
deliberately **not** ported to that gate: each port screen owns its own
messaging, `PublicSurveyScreen` already thanks the respondent after its POST,
and a second identical snackbar would queue behind the first on the one path
that reaches the screen today. The pop is owned by `take_survey_screen`, which
lands the respondent back on the team's surveys tab — Kotlin's
`FragmentNavigator.popBackStack` — instead of on a submission detail.

**The queue hangs off the pop, not off the buttons, and that was the second
thing my own re-read caught.** `onDismiss` is not the Cancel handler — it is
every dismissal, the system back button included. Wiring the two buttons left
the back button as a silent third exit that saved nothing and sent nothing:
exactly the defect the Cancel fix had just removed, in the same method. A
`PopScope` whose `onPopInvokedWithResult` calls `_queueUpload` covers all three,
and it also puts the order where Kotlin has it — the queue runs *after*
`markSubmissionComplete`, so the enqueued payload carries the profile.
`enqueue` dedupes on `(uploadType, itemId)`, so a path that pops twice cannot
double-post.

**A third half of the gate landed after the implementation audit.** Kotlin's
condition is `!isMySurvey && exam?.isFromNation != true`, and the first cut
ported only the second half. No pusher pairs `?submission=` with `?teamId=`, so
a resumed team sheet is latent — but `updateSurveyResponse` deliberately
carries no team, so such a sheet would have been asked for a profile it could
not attribute. `widget.submissionId == null` makes the gate line-for-line, and
a test drives a real resumed sheet through it (asserting the answer was stored,
so the gate cannot pass merely because nothing happened).

**`public_survey_screen` does *not* apply the `isFromNation` half**, and the
asymmetry is now stated at the call site rather than left for the next reader
to find. Kotlin's nation arm inside `PublicSurveyActivity` is
`navigateToSurveyList` with `addToBackStack = false`, after which neither the
back-stack listener nor `onFragmentDetached` fires and the answers are never
POSTed at all. Porting the gate there would mean porting that, and delivering
the answers is the deep link's whole purpose.

**Review `user_information_screen.dart` with `git diff -w`.** Wrapping the
returned `Scaffold` re-indents the whole build method: 292 changed lines, of
which 28 are real. The alternative placements all cost a reindent of something,
and hanging one hook off the pop is what makes the three exits agree — which is
the point, since two of them disagreeing is what this fixed.

**The Cancel path had a hazard of its own, found by re-reading my own diff.**
`_cancel` pops before the queue finishes, so the `State` can be disposed while
the session future is still pending — and a `ref` read after disposal throws,
straight into the `catch` that exists to make a *failed* queue harmless. The
upload would have been skipped silently: the exact failure mode the gate was
added to remove, reintroduced by the fix for it. Every `ref` read in
`_queueUpload` is now taken synchronously, ahead of the first `await`, which
keeps the standing rule (the `await` stays inside the `try`) while owing nothing
to `ref` afterwards. A test holds the session pending until `pumpAndSettle` has
disposed the route, so the window is real rather than incidental; the deferred
version fails it.

**Ordering matters here and it is now pinned.** Kotlin queues from `onDismiss`,
*after* `markSubmissionComplete` writes the profile. The port used to queue in
`_submit` for every path; for the team path that would enqueue a payload
serialized before the profile existed. `OutboxRepository.enqueue` refreshes an
open row's payload, so the end state usually survives — but a drain landing
between the two calls `markUploaded`s the row out of `pendingUploads`
permanently, and the profile would never go out. So `_submit` skips the queue
when it is about to ask, and a test asserts the outbox is **empty while the
profile screen is open**.

### Send targets the survey on the card, not its source

Found by the ground-truth audit, in this lane's file, and the most expensive
thing in the round. `team_surveys_screen.dart` passed
`survey.sourceSurveyId ?? survey.id` to `SendSurveyScreen`. Kotlin hands the
**bound exam** straight through: `listener?.sendSurvey(current.exam)`
(`SurveysAdapter:63-66`) → `b.putString("surveyId", current?.id)`
(`DashboardActivity:1008-1014`) → `sendSurveyToUsers` →
`createBulkSurveySubmissions`.

An adopted team survey always has a `sourceSurveyId`, so every Send from the
team tab created the members' pending sheets against the **un-adopted
original**: `parentId = <source>`, so their answers filed against the source
survey rather than against the team's own copy — mis-attributed at the parent
id, which is what identifies a survey's responses.
`surveys_screen.dart:118` already passed `row.id`; **the two screens
disagreeing was the tell.** The port's `adoptSurvey` copies the questions onto
the clone (`surveys_repository.dart:96-135`), so there was no lookup reason for
the old value either.

**The symptom I first wrote for this was the wrong mechanism, and the
implementation audit was right to reject it.** I claimed the team's own copy
"read zero submissions forever, because that is what `submissionsForTeam` and
the adoptable list read". Neither reader can see a bulk-created sheet under
*either* id: `getOrCreateSurveySubmission` writes no `teamId` column and no
`parent` document (`submissions_repository.dart:683-695`), while
`submissionsForTeam` is `byTeam` on that column and `_teamSubmissionSurveyIds`
reads `parent._id`. Verified in both directions. The change is right because
Kotlin does it and because the parent id is the attribution; it does **not**
repair those readers, and the corrected reasoning now stands in the code
comment and the test rather than only here.

Worth recording so nobody re-litigates the severity: Kotlin's own Send button is
**invisible** — `SurveysAdapter` sets `sendSurvey.visibility = View.GONE` in the
view holder's `init` (`:62`) and never sets it visible. The port's button is a
surplus affordance. Which id its handler passes is still the Kotlin's answer to
the question, and the port's feature is live.

## Defects, each demonstrated failing first

Pre-fix run kept at `prefix_evidence.txt` in the session scratchpad:

```
DEFECT 1: the team surveys tab opens the survey with no team
  reached the survey route: true
  Expected teamId: team-1
    Actual teamId: null
DEFECT 2: the public survey sheet stores no team
  submissions written: 1
  Expected teamId: team-1
    Actual teamId: null
```

and, on the same tree, the four that needed the new parameters to exist before
they could be written:

* `a team survey asks the respondent who they are` — failed (no
  `UserInformationScreen` anywhere in the flow);
* `Send targets the survey on the card, not its source` — failed with
  `'s1'` where `'s1_team-1'` was expected;
* `cancel still queues the team survey for upload` — failed (nothing queued);
* `a team-less sheet is not queued at all` — failed (queued anyway).

## Tests

`test/ui/surveys/survey_team_context_test.dart`, 13 new tests, plus 5 in
`user_information_screen_test.dart`, 1 in `team_surveys_screen_test.dart` and 2
in `public_survey_screen_test.dart`.

They exercise the **chain**, not its halves, because every link here already
passed alone while the whole was broken:

* the location the team tab actually pushes;
* that location fed to the **real route table** — `routerProvider`'s own
  `findMatch`, then the matched `GoRoute`'s builder — so the route's parameter
  reading is under test rather than a hand-written repetition of it. The test
  takes the location *from the screen* rather than writing one out, so there is
  no third copy of the key to agree with neither side;
* the row the real repository writes, over a real in-memory database;
* the payload the real uploader enqueues (`team: {_id: 'team-1'}` and the
  respondent's `age`), which is the document Planet receives;
* and one test that walks the whole journey — team tab → real
  `TakeSurveyScreen` → profile → back on the tab.

**Mutation-tested: fifteen injected, fifteen killed**, each by a test that names
it. Dropping the query parameter; renaming the route's read to `team`; dropping
the forward at each of the two repository hops; dropping the public screen's id;
re-seeding the Phase 106 pop gate; never asking who the respondent is; ignoring
`isFromNation`; going to the submission detail instead of popping back; queueing
before the profile step; not queueing on Cancel; dropping the team gate on the
queue; deferring `_queueUpload`'s `ref` reads past its first `await`; and
narrowing the pop hook to a `true` result, which is the back button's exit; and
dropping the resume half of the profile gate.

The implementation audit ran sixteen of its own on a scratchpad copy, including
one this set had not covered — breaking the *pushed path* to `/surveys/typo/…`,
which `route_reachability_test` kills, confirming the guard still reads the new
literal. None survived.

The twelfth is the one worth the note: **queueing before the profile step
passed at first.** `enqueue` refreshes an open row's payload, so the end state
was identical and only the timing differed. The assertion that the outbox is
empty *while the profile screen is open* is what turned a reasoned invariant
into a checked one — Phase 122's "make the test able to fail", arrived at from
the other direction.

Two fixture problems surfaced while writing these, and both are the same
mistake this project keeps naming:

* **`user_information_screen_test.dart`'s seed cannot produce the row the
  screen actually receives.** It seeds `status: 'pending', isUpdated: false`;
  every real sheet arrives from `createSurveyDraft` as
  `status: 'complete', isUpdated: true` (Kotlin marks it in `saveExamAnswer`
  before the dialog opens, `:546-551`), and `pendingUploads` selects on
  `isUpdated`. My cancel test asserted against the seed at first and failed
  against correct code. The new tests put the row into its real state; the 21
  existing tests are left alone — see *Reported, not fixed*.
* **`take_survey_screen_test.dart` runs every submit into its own failure
  branch.** `_submit` reads `serverConfigProvider` *inside* its `try`, that
  notifier reads `planetPrefs`, and `planetPrefs` is `UnimplementedError` in
  the widget harness — so the screen writes the row and then shows "Could not
  save your answers". The existing tests assert only on the row, so nothing
  saw it. The new tests override the provider; a production handset has real
  prefs, so this is a harness artefact rather than a defect.

## One file outside this lane's set, named here rather than buried

**`flutter/lib/repository/surveys_repository.dart`** — one additive optional
named parameter on `submitResponse`, forwarded verbatim to `createSurveyDraft`.

The brief listed `submissions_repository.dart` as link 3 and told me to verify
for myself. Verified: `createSurveyDraft` does live there (the brief is right
about that, and Phase 129's notes were wrong), **but the method
`TakeSurveyScreen` calls is `SurveysRepository.submitResponse`**, which is the
only thing between the screen and the writer — so Phase 129 named the right file
for the wrong reason, and the brief corrected a claim that happened to be true.

The two ways to stay strictly inside the file set were both worse:

* have the screen call `createSurveyDraft` directly — it does hold the survey
  row and questions from the providers it watches, so no lookup is duplicated,
  but `submitResponse` then has **no production caller at all**, and creating
  dead code to close a dead-code gap is self-defeating;
* stop and report — which leaves `teamId` plumbed as far as
  `TakeSurveyScreen` and then dropped, reproducing precisely the
  declared-and-never-read symptom this phase exists to remove.

So the edit is made. No other lane owns the file this round (Lane A: the
resources/notifications repositories, `app_database.dart`, the dashboard and
notification UI; Lane C: `app/` and `docs/`), the change is additive and
forwarding-only, and every other edit is inside the set. Flagged on the PR too.

## Reported, not fixed

### Nothing sweeps `pendingUploads`, and this phase leans on that

**The most important thing the implementation audit found, and my change made
it reachable.** Kotlin has two ways a completed sheet reaches the server: the
dismissal (`UserInformationFragment:303`) *and* `uploadManager.uploadSubmissions()`
running unconditionally from `AutoSyncWorker:136`, `UserDataWorker:48` and
`ServerReachabilityWorker:183,186`. A missed dismissal costs nothing there.

The port has **no `queuePending` sweep in any sync or background path**. Its
only writers of a submissions outbox row are four write-time call sites
(`take_survey_screen:252`, `user_information_screen:471`,
`take_exam_screen:544`, `submissions_screen:187`);
`background_entrypoint.dart:177-184`'s `'submissions'` step is the *pull*, and
`OutboxDrainScope` only drains rows that already exist.

Before this phase a team survey's row was enqueued at submit time. Now it is
enqueued at the profile screen's pop — the Kotlin order, and the one that
avoids the double POST above — so two windows end with the sheet queued by
nobody: **process death while the profile screen is open**, and the
`if (!mounted) return` before the push. In both, `submissions` holds a
`complete, isUpdated, !uploaded` team sheet with no outbox row, and it stays
there until the same user completes some *other* submission, because
`queuePending` is an unscoped sweep that then rescues it. Reproduce it by
answering a team survey, tapping Submit, and force-stopping the app on "Your
information".

Not data loss — the answers are on the device — but for a field survey an
indefinitely deferred delivery is the same outcome. **The fix is a
`queuePending` step in the sync path**, beside the existing steps in
`lib/providers/dashboard_sync_provider.dart` and
`lib/background_entrypoint.dart`, which is where Kotlin's safety net lives.
Both are outside this lane's set; the gap is pre-existing and port-wide (it
applies equally to an exam attempt whose queue call fails), and it wants the
owner of the sync path rather than a third out-of-set edit from here. Whoever
takes it should treat it as the round's highest-value item.

### `adoptSurvey` does not copy `courseId`/`stepId` onto the clone

`createMappedSurvey` copies both onto an adopted team clone
(`SurveysRepositoryImpl.kt:441-456`); `surveys_repository.dart:99-118` sets 14
fields and neither, though `Surveys` has both columns
(`tables.dart:541-542`). The Send fix above now depends on it: with the clone's
own id being sent, `createBulkSurveySubmissions` resolves
`survey?.courseId == null` and keys the members' sheets `<cloneId>` where
Kotlin keys `"<cloneId>@<courseId>"` — Phase 125's class, in the one place the
Phase 125 sweep cannot reach (`_repairSurveyParentId` returns 0 when
`target == survey.id`). And `hasUnfinishedSurveys` selects by
`getByCourseId`, so the port's clone is invisible to the mandatory-survey gate
where Kotlin's is one more requirement in it. Reachable only for a
`teamShareAllowed` survey that also carries a `courseId`. Two field copies in a
file outside this set.

### The Send id change is a parentId key change with no repair

`createBulkSurveySubmissions`' existence check is
`latestPendingByUserAndParent`, on the whole column. A team that used Send
*before* this change has member sheets under `<sourceId>`; after it, Send
creates a **second** pending sheet under `<cloneId>` — the shape
`submissions_repository.dart:645-652` describes for the Phase 125 key change,
which shipped `repairCourseSurveyParentIds` while this one ships nothing.
Mitigating: Kotlin's Send button is invisible (`SurveysAdapter:62`, never set
`VISIBLE`), so the port's is a surplus affordance and real usage is unknown. A
decision rather than an obvious repair, and it belongs with whoever decides
whether the port keeps that button at all.

### Adopt is gated on team leadership; Kotlin gates on guest

`team_surveys_screen.dart:32,78` — `canAdopt = membership?.isLeader == true`.
Kotlin has **no role gate**: `SurveysAdapter:83-90` routes any tap to
`onAdoptSurvey`, and the only visibility rule is
`if (userId?.startsWith("guest") == true) startSurvey.visibility = View.GONE`
(`:105-107`). So an ordinary member sees a disabled Adopt button where Kotlin
adopts, and a guest sees an enabled Start affordance on **both** survey screens
where Kotlin hides it. Same shape as Phase 99's manage-gate finding.

In this lane's file, and left alone deliberately: it is an **authorization**
change rather than team-context plumbing, and the current gate is pinned by an
existing test (`'a non-leader sees the adopt button disabled'`), so some earlier
round chose it. Changing a pinned gate deserves its own decision and its own
diff. The fix is two edits — `canAdopt` becomes `userId?.startsWith('guest') !=
true`, and the same predicate hides the start/tap affordance on both
`team_surveys_screen.dart` and `surveys_screen.dart` (the latter is outside this
set).

### No incremental answer persistence, and therefore no resume

Kotlin writes each answer as the respondent passes it: `updateAnsDb()` from
`btnBack` (`:189-195`), `btnNext` (`:196-206`), the submit path (`:640-641`)
and `onDestroyView` under `NonCancellable` (`:830-837`).
`take_survey_screen.dart` holds everything in widget state and writes once in
`_submit`. Kill the app on question 8 of 10: Kotlin has 7 answers and a
resumable `pending` row, the port has nothing. Pre-existing, on this trail, and
the reason the port has no resume-or-restart dialog to port. It is a
`take_survey_screen.dart` change (mine) but a structural one — the screen would
have to create the sheet at open time, as Kotlin does — so it wants a phase, not
a corner of this one.

### Opening and abandoning a team survey does not mark it taken

Kotlin's `recreate = isTeam` (`ExamTakingFragment:150-153`) creates a **new**
`pending` row every time a team survey is opened and never resumes. Two live
readers are status-blind — `getSurveyFormState:344-347`
(`getByParentIdsAndTeamId`) and `getTeamSubmissionExamIds:273-276`
(`getByTeamId`) — so in Kotlin, opening a `teamShareAllowed` survey and pressing
Back moves it out of the adoptable list and into the team's own. The port,
creating the sheet at submit time, does neither. The port's readers match Kotlin
(`_teamSubmissionSurveyIds`, no status filter); it is the writer's timing that
differs, so this is the same root as the item above.

### Nothing on screen shows what the team id now buys

Kotlin binds `tvNoSubmissions`/`tvDateCompleted`/`tvDate` from `SurveyInfo`
(`SurveysAdapter:109-112`, computed team-scoped in `getSurveyInfos:288-335` via
`getByTeamId`). No `SurveyInfo` analogue exists in `lib/`, and neither survey
screen renders any of it. Said out loud because this phase's win is in the
uploaded document, not on the display: after it, the team is written, carried
and uploaded correctly, and the screen looks the same.

Related and visible: Kotlin's radio toggle shows one list at a time while
`team_surveys_screen.dart:45-102` stacks both sections, so a team's own
`teamShareAllowed` survey renders **twice** — tappable under *Team surveys* and
again with an Adopt button under *Adoptable*.

### `SurveyMapper` reads `isFromNation`; Kotlin derives it and always gets false

`survey_mapper.dart:85` and `exam_mapper.dart:64` both do
`JsonUtils.getBool('isFromNation', doc)`. Kotlin's only writer is
`!parentId.isNullOrEmpty()` with `parentId` always `""`, so the field is false
for every row in the Android app. If a Planet `exams` document carries the key,
the port's rows diverge — and this phase has just made that field decide
whether a team survey asks its respondent anything, so the divergence now has a
consequence. Both mappers are outside this set. Someone should decide whether
the port derives the field or trusts the document; trusting it is arguably
better, but it should be a decision.

### The adoption marker carries a `teamId` Kotlin never writes

`surveys_repository.dart:177` passes `teamId: isTeam ? teamId : null` into
`createSurveyAdoptionSubmission`. Kotlin's `createMappedSubmission:205-241` sets
only the `@Ignore`d `membershipDoc`, so nothing persists. Combined with the
port's `pendingUploads` gating on `isUpdated` where Kotlin's
`getPendingSubmissions` requires `status = 'complete'`, the port **uploads an
adoption document Kotlin never uploads**. Phase 129 recorded the divergence;
it is still not in `docs/kotlin-to-flutter-migration.md`'s deviations list
(Lane C owns that file this round).

### `user_information_screen_test.dart`'s shared seed

As above: `status: 'pending', isUpdated: false` is a state no caller of that
screen can produce. Twenty-one existing tests assert against it, several
explicitly (`expect(row?.status, 'pending')`), so correcting the seed means
re-reading each of those expectations — worth doing, too large to bundle here.
The two new tests set the row up themselves and say why.

### `Routes.userInfo` still has no pusher

`router.dart:127,315-321` registers `/exam/user-info/:submissionId`, already
parsing `?teamId=`, and nothing navigates to it — it sits in
`route_reachability_test`'s `allowed` map. This phase did **not** change that:
`take_survey_screen` pushes the screen with `Navigator.push`, matching
`public_survey_screen` and matching Kotlin, where it is a dialog over the
survey rather than a destination. What did change is that the route is now
spare with **two** live builders instead of one, which the allowlist's reason
string said out loud only after the implementation audit noticed it was stale.
Either give the route a caller or delete it; leaving a parsed-but-unreachable
route is how the `teamId` this phase wired sat unread in the first place.

### Kotlin's degenerate team survey has no guard, and the port inherits none

`TeamPagerAdapter` is handed `team?._id`, and `TeamDetailFragment` can reach
`setupViewPager` with a null team (`newInstance` puts no `"id"`, so
`shouldQueryRealm("")` is false and the early return at `:134-141` is skipped).
Result in Kotlin: `isTeam = true, teamId = null` — the sheet is team-less, the
profile dialog still opens, and `onDismiss` then returns early, so there is no
upload, no toast and no pop, leaving the respondent stranded. The port cannot
reproduce the *stranding* (its pop is unconditional), and a null `teamId` here
simply means "no team", which is the same sheet Kotlin writes. Noted because
anyone porting the team detail's tab pager should not reproduce the missing
guard.

### Kotlin's dead `isFromNation` arm navigates somewhere the port does not

`navigateToSurveyList` replaces the container with a bare `SurveyFragment()` —
the *individual* list, losing the team context — and inside
`PublicSurveyActivity` it would strand the answers entirely, because
`replaceFragment(..., addToBackStack = false)` leaves `backStackEntryCount` at
1, so neither the back-stack listener nor `onFragmentDetached` fires and nothing
is POSTed. The port's nation-survey arm goes to the submission detail instead.
Since the arm is dead in Kotlin and its Kotlin behaviour is partly broken,
matching it would be porting a bug; recorded rather than copied.

### Smaller confirmed divergences, all currently unreachable

* **A whitespace-only team id.** `onDismiss`'s gate is exact string equality —
  `if (safeTeamId == "") return` — so `" "` *uploads* in Kotlin, where
  `user_information_screen`'s `.trim().isEmpty` does not. No port path can
  produce one (the value comes from a route parameter fed by `team.id`), and
  the repository's own `.trim()` is right there because that writer really is
  `isNotBlank`. Left as is: the two Kotlin sites genuinely differ, so being
  faithful means differing, and churning unreachable semantics costs more than
  it buys.
* **Two silent returns in `_submit`.** `if (id == null) return` leaves the user
  on a reset form with no message when `submitResponse` finds no survey row,
  and `if (!mounted) return` is the second window of the sweep item above.
  Both pre-existing; the first is behaviourally unchanged by this phase.

### Two of the thirteen new tests are negative controls

`'a personal survey stores no team'` and `'a personal survey location builds a
team-less screen'` assert an absence that holds trivially on the pre-fix tree.
They are worth keeping — they are what would catch a future change that starts
attributing personal sheets to a team — but they are controls, not evidence,
and no mutation in the table below is killed by them alone.

Related, and a claim of mine the audit trimmed: the whole-journey test's
`pushTargets` hand-writes `queryParameters['teamId']`, so it *is* a third copy
of the key. "No third copy" is true only of `tapOwnedSurvey` +
`_buildFromRouter`, which is the pair that actually guards the route's read.
