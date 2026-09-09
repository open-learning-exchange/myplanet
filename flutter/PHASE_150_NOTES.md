# Phase 150 — `my_library` preservation, and four corrections in `app_database.dart`

Lane A of a four-lane round. Five items, all landing in
`flutter/lib/data/local/app_database.dart` and its neighbours:

1. **The schema bump destroys resources the user created offline** and leaves
   `resourceOffline` false on every downloaded resource
   (`PHASE_146_NOTES.md` *Reported, not fixed* item 1). Outranks the rest.
2. The challenge tally excludes replies where Kotlin's counts them
   (`PHASE_147_NOTES.md` item 1).
3. Three false dartdoc claims on those same queries (`PHASE_142_NOTES.md`
   item 5, `PHASE_147_NOTES.md` item 2).
4. `HealthExaminationDao.markUploaded(id, null)` erases a stored `_rev`
   (`PHASE_148_NOTES.md` item 2) — a blocker for Lane C.
5. The `surveys` entry in `_localAuthorityTables` carries a claim its own phase
   retracted (`PHASE_143_NOTES.md` item 4).

Notes are written as the work lands; the *Reported, not fixed* section is at the
bottom.

## Method

1. Read the Kotlin by hand for each of the five claims before touching Dart —
   `NewsDao.kt`'s two challenge counters, `VoicesRepositoryImpl.postReply`,
   `HealthExaminationDao.kt` and `HealthRepositoryImpl`,
   `MyLibrary.insertMyLibrary`, `MyLibraryDao.getPendingUploads` and
   `UploadConfigs.getResourcesConfig`, and the port's
   `SurveysRepository.individualSurveys`.
2. A `parity-auditor` pass at `effort: max` on nine lettered claims, aimed at
   the **ground truth**, before implementing.
3. Implement, each defect demonstrated failing first.
4. Mutation-test every fix.
5. A second `parity-auditor` pass at `effort: max` aimed at the finished,
   already-green code.

## Jobs 2 and 3 — the challenge tally, and two comments that misnamed it

Both confirmed against the Kotlin and both fixed in one edit, since they sit on
the same two queries.

`NewsDao.countDistinctCommunityVoiceDates` / `…ForUser` (`NewsDao.kt:60-73`)
have exactly three predicates: the time window, the optional `userId`, and
`viewIn LIKE '%"section":"community"%'`. The port's two queries each carried an
`_isTopLevel(r)` conjunct on top of that. `postReply` copies the parent's
`viewIn` verbatim (`VoicesRepositoryImpl.kt:319`) **and so does the port's**
(`voices_repository.dart:710-716`), so a reply to a community voice is a
community-section row in both apps: Kotlin credits the day it was posted and
the port dropped it. The port **under**-counted, which is the direction that
matters — the dialog told a learner they had not done something they had.

The two dartdocs went with the predicate. Each claimed to be a port of a
`NewsDao.getInTimeRange`, which Kotlin does not have, and each described its
result as "all top-level *community* voices" — a filter neither query
implements, because the community half is the caller's. They now name the real
counterpart, quote its SQL, and say which half lives in the caller.

**The third false claim reported alongside them was not in this file.**
`PHASE_142_NOTES.md` item 5's "The Kotlin filters `isCommunitySection` in
memory after the DAO query" is on `getCommunityVoiceDates` in
`voices_repository.dart`, and Phase 147 already corrected it there. The brief
listed all three as mine; two were. The one comment in `app_database.dart` that
*does* claim in-memory filtering — `watchTopLevelMessages`, three lines below —
is **true**: `VoicesRepositoryImpl.getTopLevelMessagesFlow:136-160` really does
map the whole list and read inside `viewIn` in Kotlin. It was left alone.

Three tests, driven through the production `createPost`/`postReply` rather than
hand-built rows, because the whole defect is about a reply's stored shape and a
fixture that set `viewIn` itself would assert about a document instead. Two red
before the change. The third is the guard that a team thread still never counts
— it is `isCommunityNews` in the caller that excludes it, not `_isTopLevel`, so
removing the top-level filter must not widen the tally.

## Job 4 — a null rev erased a health examination's stored one

Confirmed, fixed, and landed first: `PHASE_148_NOTES.md` records it as a
blocker for the lane doing 409-recovery work across the uploaders, so it went
out in its own commit with a PR comment routing the alignment.

`HealthExaminationDao.markUploaded(id, rev)` wrote `rev: Value(rev)`, so the
`HealthUploader` branch that has an id and no rev erased the revision the row
already held. `_rev` is the only thing that lets the next edit PUT rather than
conflict, and `HealthRepository.cacheDocuments` skips a locally dirty row
(`PHASE_148_NOTES.md` item 3), so nothing supplies it again — the record 409s
for the life of the install.

**The line numbers in the report were stale and its own correction said so, so
they were read rather than trusted.** The method is at `:3961`, not `:3812`;
the correct pattern is at `:1198`/`:2066` as the report's amendment says, not
`:1035`.

**One sub-claim needed settling before the comment could be written, and it
changes what the fix *is*.** Kotlin's single-id overload `markUploaded(id,
rev: String?)` *does* still write `_rev = :rev`, so on its own it has the same
defect. What `1004e90` added is the `@Transaction` map overload (`:36-46`),
which partitions the null-rev ids into `SET isUpdated = 0 WHERE _id IN (:ids)`
— and that is the one the app calls, from
`HealthRepositoryImpl.markHealthExaminationsUploaded:66-69`, the only caller
anywhere. The single-id overload's only caller is the non-null half of that
partition, so a null never reaches it. **So the port's `Value.absent()` is
parity, not an improvement on the Kotlin** — which is the opposite of what it
would have been had the live path used the other overload, and is why it was
worth the two greps.

Blank is deliberately not folded in with null: Kotlin partitions on
`it.value == null`, so an empty-string rev is written. Pinned, because folding
it in is the obvious "tidier" reimplementation.

`HealthRepository.markUploadedBatch` delegates to this DAO method, so its copy
of the bug is fixed by the same line. It still has zero callers.

Three mutations, each reding a different test: restoring `Value(rev)`, folding
blank in with null, and leaving the dirty flag set.

**A method-body mutation needs an anchor, not a text substitution.** Three
scripted `str.replace` mutations of `rev: Value(rev)` landed on
`TeamTaskDao.markUploaded` and `NotificationDao.markSynced` instead of the
health method — a two-line pattern that reads as unique is not, in a
4800-line file of DAOs that all write a rev and clear a flag. The tell was
that all three mutations red the *same* test, which is not what three
different mutations do; the file was reset and every mutation re-applied
through an exact-match edit. Phase 135's rule was *read the tree after an
audit pass*; this is the same rule for one's own mutation runs, and `git diff`
with context is what answers it.

## Job 5 — a retraction that reached the notes and not the code

Confirmed. The `surveys` entry in `_localAuthorityTables` still said an
orphaned step-joined survey row is "unreachable from the UI (the step tile is
gone with the course)", which `PHASE_143_NOTES.md` item 4 retracts.
`SurveysRepository.individualSurveys` (`surveys_repository.dart:41-46`) filters
on `!row.teamShareAllowed && (row.teamId ?? '').isEmpty` and tests neither
`stepId` nor `courseId`; a course-embedded survey arrives with neither team key
set (`SurveyMapper.fromCourseDoc` reads a missing `teamShareAllowed` as false
and a missing `teamId` as null), so the orphan satisfies that predicate exactly
and is listed at `/life/surveys`, tappable, for good.

The sentence replaced is the one that decided the accretion was not worth
fixing, which is why it is worth correcting rather than deleting.
