# CLAUDE.md - AI Assistant Guide for myPlanet

## Project Overview

**myPlanet** is an Android mobile application serving as an offline extension of the Open Learning Exchange's Planet Learning Management System. It enables learners to access educational resources (books, videos, courses) without continuous internet connectivity.

### Key Characteristics
- **Primary Language**: Kotlin (100% — no Java sources remain)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 16); **Compile SDK**: 37
- **Current Version**: 0.67.14 (versionCode: 6714)
- **Build System**: Gradle 9.6.1 with Android Gradle Plugin 9.3.1
- **Local Database**: Room (AndroidX) 2.8.4 — the only local persistence store
- **License**: AGPL v3

### Build Flavors
- **default**: Full-featured version
- **lite**: Lightweight version with reduced features (removes `REQUEST_INSTALL_PACKAGES`; `-lite` version-name suffix)

### Flutter port (in progress)
A Flutter/Dart port lives in **`flutter/`**, alongside — not replacing — the Kotlin app. `app/`
is unchanged and remains the shipping app. **All 28 UI packages** have a screen, plus a durable
write-back path. The first vertical slice ran server configuration → login → resources list;
since then the dashboard shell, courses, calendar, first-launch onboarding, the offline user
profile, appearance settings, the dictionary, notifications, My life, references, personals, and
ratings, offline submissions with question-aware answer review, events/meetups, individual
surveys, teams, chat, feedback, community, graded course exams, and the resource viewer with
its download path, encrypted health records, the chat and feedback sync-in directions, chat
upload, member registration, team and public survey sharing, personal-note attachments, and the
completed home dashboard — completed-course stars, the server-reachability ring, team alert
badges, offline-login counting with its activity chart, survey remind-later, and the language
action — and the activity log (resource opens/downloads, course visits, completed syncs) with the
four-database upload path that carries it and the profile stats that read it, and deep links
(`app_links`, so a public-survey link's origin survives) with durable delivery for the anonymous
answer sheet they collect, and profile photos with the `login_activities` sync-in (harvested from
`flutter-openhands4`), and — harvested from `flutter-openhands7` — the About and Disclaimer
screens, team finance/report receipt attachments in both directions, free-up-space storage
management over a `disk_stats` method channel, and debounced username validation, have landed —
plus voices share-to-community with the upstream `f4adebf` visibility/un-share parity, device and
tablet-usage telemetry (`myplanet_activities`), and task deadline notifications.
Everything below in this document describes the Kotlin app and still applies to it.

See **`docs/kotlin-to-flutter-migration.md`** for scope, the technology mapping (Hilt→Riverpod,
Room→Drift, Retrofit→Dio, strings.xml→.arb), and the open problems. The `WorkManager` gap is
resolved for write-back: `RetryQueue`'s durability was always the SQLite table rather than the
worker, so the queue ported directly and only the drain trigger needed replacing (`outbox` table
+ `OutboxDrainer`, drained on app resume). What remains open is background work with no user
present — `AutoSyncWorker`'s timed sync landed in Phase 38 through the `workmanager` plugin behind
a testable Dart seam, though that plugin's own Android side is Kotlin, and
`TaskNotificationWorker`'s deadline notifications landed in Phase 42 on the same plugin's
`maintenance` cadence (`TaskDeadlineNotifier` policy behind a `NotificationPresenter` seam, with
`team_tasks.isNotified` making the reminder once-only). Phase 43 closes the final WorkManager gap:
resource requests are persisted before the foreground attempt and handed to a network-constrained
one-shot worker for retry and process-death recovery. Phase 44 then closed the device-identity
serializer gap for personal, rating, submission and team uploads using the Phase 41 platform seam.
Phase 45 hardened both: the download queue moved from a preference list to a preserved Drift table
at schema v33, and device identity gained a UI-primed cache for headless WorkManager engines.
Phase 46 applied that same cache to `disk_stats`, where the missing headless channel had been
silently disabling the deadline notifier's storage-warning step — the only caller of
`updateStorageNotification`, so the row was never written at all. **Phase 94 lands the real
fix**: both channels moved into the in-tree `planet_platform_channels` plugin
(`flutter/packages/`), which `GeneratedPluginRegistrant` attaches to every engine — headless
WorkManager ones included — and `MainActivity` is a bare `FlutterActivity` again. The UI-primed
caches stay as fallback. The same phase aligned the port's `minSdk` to the Kotlin app's 26 (it
had silently claimed 24) and added `version_parity_test.dart`, which pins
pubspec and the `minapk` fallback constant to `app/build.gradle` — the drift Phase 93 caught by
hand after five missed releases. **It deliberately tolerates patch lag and fails only on a minor
version behind**: `automerge.yml` bumps the Kotlin version on every merge (0.67.14 → 0.67.25
within an hour), and the first cut's exact-equality rule turned every pull-request run red,
because a PR run tests the merge with master. Do not tighten it back.

Phase 96 audited the 26 upstream commits after `ba794f4bb` (master
0.67.14 → 0.67.40) — all refactors and CI/build work, no new behavioural
port — and closed a pre-existing gap the audit surfaced: the ranked resource
search. The Kotlin resources screen filters with `ResourcesSearchUtils`
(`49617105e`/`1e41d3353`), ranking titles that **start with** the whole
query ahead of those that **contain every whitespace-separated word**; the
port had been using a flat SQL `LIKE '%query%'` since the first resources
slice, which can neither rank nor word-split. `MyLibraryDao.watchResources`
drops the text-search `LIKE` (the shelf `userId` scope stays in SQL);
`ResourcesRepository.watchResources` maps the stream through a top-level
`searchResources` pure function ported from `searchList`, reusing
`text_utils.normalizeText` (the single one, Phase 78). The courses analogue
needs no port — `CoursesRepositoryImpl.search(query)` is in the interface but
uncalled; the screen uses `filterCourses` (a plain `contains`) which the
port already mirrors.

Phase 97 deepened the same systematic Kotlin-vs-Flutter search/filter/sort
audit and closed three more gaps. **Resource catalog visibility**:
`getEnrichedLibraries`'s `getMyLibrary`/`getPublicNotUserPattern`/`getPublic`
three-way split is now mirrored by `MyLibraryDao.watchResources(myLibrary:)`
— the catalog excludes private resources (`isPrivate = 0`) and the signed-in
user's own shelf items (`userId IS NULL OR userId NOT LIKE`, so no
duplication between catalog and My Library), while My Library includes the
user's private team resources (`userId LIKE`). **Survey search**:
`SurveysViewModel.filter` is the same ranked algorithm as
`ResourcesSearchUtils.searchList` (startsWith-before-contains-all-words,
word-split, accent-folded, `name` only); the port's flat
`toLowerCase().contains` on name+description is replaced by a `searchSurveys`
pure function. **Survey sort date**: `SurveysViewModel.getSortDate` prefers
`adoptionDate` over `createdDate` for adopted surveys (those with a
`sourceSurveyId`); a `surveySortDate` pure function ports it. 14 new tests,
1474 pass.

Phase 98 closed the notifications domain's missing half: the **sync-in
(pull) direction**. `TransactionSyncManager`'s `"notifications"` walk had
never ported, so a server-side notification (join request, new task, reply)
never reached the local cache — the bell only ever showed rows the
**upload** direction had authored (`userId:resource:count`,
`userId:storage`, team watermarks). The `Notifications` table gains `rev`
+ `needsSync` (schema v44, pure cache so no preservation test);
`NotificationDao` gains `markSummaryAsRead`/`getPendingSyncNotifications`/
`upsertAll`/`getByIds`/`deleteByIds`/`markSynced` plus the Phase 53
`watchForUser`/`watchUnreadCount` type-error fixes. `NotificationsRepository.sync`
ports the `_all_docs` walk and `parseNotification`; `_bulkInsertFromSync`
preserves a locally-read row's `isRead` + `needsSync` across a re-pull (the
same round-trip shape as Phase 56's security-data fix and Phase 74's
reactions) so a re-sync cannot undo a read. `_design` docs are skipped
(`!id.startsWith("_design")`, no trailing slash). Unlike every other sync
repository this one runs **no** `deleteNotIn` — the Kotlin walk never does
either, and a prune would evict the locally-authored count/storage rows
that have no server document. The **read-state round-trip** closes too:
`markNotificationAsRead` flags server-originated rows `needsSync = true`,
and `syncNotificationReads` PUTs each pending row back with the carried
`rev` then calls `markSynced`. A new `DashboardSyncArea.notifications` +
`NotificationsSyncNotifier` wire the bell-list refresh into the sync
center. **Amended on harvest**: `markAllAsRead` and `markSummaryAsRead`
were written as two statements — set `is_read`, then set `needs_sync` on
the server rows — but the Kotlin does both in **one** statement,
`needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END` under a
single `WHERE ... AND isRead = 0`. That shared `WHERE` is the point: after
the first update nothing marks the rows it changed, so the second could
only re-select by `is_read = 1`, which matches everything the user has
*ever* read — one "mark all read" tap re-queued the whole history and the
next sync PUT every document back. **A two-part read-then-flag over the
same rows needs one statement, or the ids captured first.**

Phase 99 retires the long-standing **"27 of 28 UI packages have a
screen"** claim, whose one gap was named as `ui/enterprises/`. The claim was
wrong, and had been for a while: **`ui/enterprises/` is not a screen the port
lacks, it is two screens the port files under `ui/teams/`.** Enterprises are
not a separate feature — they are a team *type*. `TeamDetailFragment.buildPages`
computes `isEnterprise = team?.type == "enterprise"` and swaps tabs on it
(`MissionPage`/`PlanPage`, `FinancesPage`/`CoursesPage`, `DocumentsPage`/
`ResourcesPage`, plus `ReportsPage` only for enterprises); the two fragments
themselves carry **no** type branching at all, and neither does
`BaseTeamFragment`, which only resolves the team and the user's membership.
So `EnterprisesFinancesFragment` → `team_finances_screen.dart` and
`EnterprisesReportsFragment` → `team_reports_screen.dart` is the whole
mapping, and it already existed. Counting the package as missing was counting
a Kotlin directory name rather than a screen.

Auditing it field by field did surface four real gaps, all now closed.
**The manage gate was wrong in both screens**: Kotlin's
`canManage = if (fromCommunity) user?.isManager() == true else isMember`
reads `isMemberFlow`, which is `TeamsRepositoryImpl.isMember` — plain
membership. The port required `?.isLeader`, so a rank-and-file member of an
enterprise saw no add-transaction or add-report button where the Kotlin gives
them one; Kotlin has a separate `isTeamLeader` it deliberately does not use
here. The `fromCommunity` branch ported alongside it (the community tabs pass
it, and there the gate is the manager role), read only on that path so the
team path never builds `sessionProvider`. **The report card showed 4 of the
9 value rows** `EnterprisesReportsAdapter` binds — only the derived totals,
none of the five figures the report was authored from
(beginningBalance/sales/otherIncome/wages/otherExpenses), and not the
created/updated footer; every field was already on `TeamRow`, so this was
display-only, no schema change. **The finances summary had no
negative-balance caution** (`balance_caution`, shown when
`FinanceHeaderState.isCautionVisible`, i.e. `total < 0`). And the CSV
export's default filename carried a **stray brace** —
`'${weekday}_$month}_...'` interpolates the bare `$month` and leaves the `}`
as literal text, so the picker offered `Thu_Aug}_20_2026` against Kotlin's
`EEE_MMM_dd_yyyy`; the formatter is now the top-level
`reportExportDateSuffix`, pinned by a test rather than reachable only through
the platform save-file dialog. Two differences were found and deliberately
**not** changed: the port's team detail lists a Finances entry gated on
membership where Kotlin gates it on `type == "enterprise"` (a surplus, not a
gap, and the port's detail screen is a link list rather than Kotlin's tab
pager, so its gating is not a line-for-line port anyway); and the report
card's `%s Financial Report` title, which needs a `teamProvider` watch — the
Phase 75 harness trap, since `teamsRepositoryProvider` transitively reaches
`planetPrefsProvider`.

Phase 47 localised the other four languages: `tool/arb_from_strings_xml.dart` derives `app_ar.arb`,
`app_fr.arb`, `app_ne.arb` and `app_so.arb` from the Kotlin `values-*/strings.xml` (195–196 of 727
keys each, nothing machine-translated), which also made the language picker's four dead entries
real — they had been setting a locale with no `.arb` to resolve to. Somali needed
`framework_fallback_delegates.dart`, because `flutter_localizations` does not translate it and the
locale would otherwise resolve with no `MaterialLocalizations` and crash. Directional padding and
alignment are done; a visual RTL review in Arabic is not.

Phase 48 fixed the team-finance summary so it is derived from the filtered transaction stream.
Phase 49 made notification rows actionable rather than read-only: resources and storage warnings
open their matching screens, while task and join-request notifications resolve their cached team
documents before opening that team's tasks or join-requests tab. Already-read notifications remain
actionable, matching Kotlin's click behaviour.

Phase 50 closed the resource-catalog batch-selection gap. Long-press enters selection mode in both
list and grid layouts, further taps build a multi-selection, and one add/remove action atomically
updates every resource plus its `removed_log` entry before attempting the derived shelf upload.

Phase 51 ported the certified-course-exam verification photo. A new `submit_photos` Drift table
(schema v34, preserved in `localAuthorityTables`) holds the row; `SubmissionsRepository` authors
and serializes it with device identity layered on at queue time; `SubmitPhotosUploader` delivers
the durable two-step write-back through the outbox (POST doc, record id/rev, then best-effort PUT
the JPEG bytes as a CouchDB attachment). Capture runs through a `PhotoCapture` seam
(`image_picker` in production, faked in tests), wired into `take_exam_screen` behind
`ProgressRepository.isCourseCertified(courseId)`; a null capture is swallowed, matching Kotlin.

Phase 52 ported the mandatory-survey toast (the deferred second half of `c5141b658`): on
finishing the MyPlanet Onboarding course, the screen checks
`SubmissionsRepository.hasUnfinishedSurveys(courseId, userId)` — a port of the Kotlin
`hasUnfinishedSurveys`/`hasSubmission` pair — and blocks the pop with a toast when an attached
survey is outstanding. The `courseId`/`stepId` columns on `Surveys` (schema v35) make
course-attached surveys queryable. The resource-sync `deleteNotIn` bug (`2ec7e3187`, #15831) is
the second fix: a mid-walk batch failure now sets a `hadBatchFailure` flag and skips the cleanup
(returns `SyncComplete`, not `SyncFailed`), so valid resources survive an incomplete walk.

Phase 53 closed five deferred/audit items. The dashboard library-card my/call split
(`08e18ffdc`, #15728) ships a `resourceShelfOnlyProvider` shelf toggle and a `shelfUserId`-scoped
`watchResources` (the `isMyCourseLib` view): the card opens the user's shelf when it has items
and the full catalog otherwise. The notification sub-destination work (`a08fc5662`) adds a
`subType` column to `Notifications` (schema v37) and a `NotificationParser` that splits a raw
`"team"` type (via `linkParams.activeTab` or message sniffing) into `team_join`, `chat`, and
`voice_reply` destination kinds. Nested HTML entry files land via the `openWhichFile` column on
`MyLibraryTable` (schema v36) and `ResourceFiles.resolveHtmlEntryFile` (path-traversal-safe).
The voices shared-team suffix (`"| Shared from {name}"`) and the team-finances future-date cap
(#15766) round out the batch.

Phases 54–60 shift from adding screens to hardening what exists. Phase 54 landed the HTML
resource viewer (`webview_flutter`, local files only — `loadFile`, never `loadRequest`) and
fixed a server-url alt-credential bug; Phase 55 added team financial report CSV export.
Phase 56 ports `aa24dfa6c` (#15836): `updateUserSecurityData` now writes `Value.absent()`
rather than `Value(null)` when the server omits a credential, so a null-returning fetch can no
longer wipe a stored `derived_key`/`salt` and lock the user out of offline PBKDF2 verification.
Phases 57–58 finished localising the UI, retiring the last hardcoded strings. Phase 59 replaced
the hardcoded version/build line with `appVersionInfoProvider` (`package_info_plus`) and
backfilled the team detail screens with 33 widget tests — the largest untested surface. Phase 60
extends two Phase 59 fixes from one instance to the class: three further duplicate `app_en.arb`
keys (`justNow`, `description`, `apply`) are removed and guarded by a test that reads the ARB
source text, because `jsonDecode` collapses a duplicate pair before any assertion can see it;
and the `minapk` comparator in `ConfigurationsRepository` now reads the runtime version too.
That last one matters because a failed version check returns `_UrlCheckFailure`, which is
indistinguishable from an unreachable server — a constant nobody remembered to bump presents as
"cannot reach the server" on the first screen. The lookup falls back to the constant on anything
unusable (throw, empty, or the `0.0.0` placeholder), since under-reporting the version would
fail configuration outright. Phase 61 ports the dashboard key/IV sync-in (`9f3fac1d9`): the home
screen's session listener fires `HealthKeyIvSyncNotifier` for a non-guest user with no local
health key, which pulls the key/IV from the user's `userdb-<hex(planetCode)>-<hex(name)>`
database with the user's own basic-auth credentials (health-role users sweep every synced
account), guarded against re-entry by its `SyncRunning` state. The `toHex` encoding is one
big-endian number, not per-byte hex — `toHexString` pins the empty-string/leading-zero cases a
naive port gets wrong. The master's progress dialog is deliberately not ported: `di` is never
assigned there, so its show/dismiss calls are no-ops. Phase 62 ports the upload direction
(`saveKeyIv`): `UserRepository.saveKeyIv` publishes the freshly generated key/IV to the
user's `userdb-`+hex database during online member creation, retried 3× with the
`changeUserSecurity` health-role grant, and `uploadNewUser` swallows its failure so the
account still reports success. Still open: the background/outbox path for accounts created
offline. **Ported in Phase 63.** Phase 64 ports team visit logging (`team_log` /
`team_activities`): `TeamDetailScreen` fires `logTeamVisit` once per mount (a rebuild is
not a revisit) via `addPostFrameCallback`, and `TeamLogUploader` carries the row to
`team_activities` through the outbox with device identity layered on at queue time.
Phase 65 ports search-activity logging (`search_activity` / `search_activities`), with
one durable row per applied filter written from the courses/resources screens' `dispose`
and carried through the outbox; Phase 66 was a pure harvest audit of the 2026-08-20→23
upstream batch (no new port). Phase 67 ports tags and collections: the `tags` Drift table
(schema v40, a pure CouchDB cache), `TagsRepository` with the `hadBatchFailure` cleanup
rule, the shared `CollectionsDialog` (single/multi-select, debounced search, expand-to-
children), and per-screen wiring — a badged collections button, selected-tag chips on
resources, a Selected: … label on courses, any-of tag filtering on both lists, and the
selected tag ids now captured into the search-activity filter JSON instead of Phase 65's
empty-array placeholder.

Phase 68 ports achievements: the `achievements` table (schema v41, preserved), an
`AchievementsRepository`/`AchievementsUploader` pair on the outbox, the achievements and
edit screens, and a CV/resume attachment written under `<base>/ole/cv/` through
`AchievementFiles`, whose `_segment` reduces a server-supplied filename to its basename so
a `..` cannot escape that directory. Phase 69 hardens the localisation tooling rather than
adding a screen: `tool/arb_from_strings_xml.dart` **merges** into the existing `.arb`
instead of regenerating it. As written in Phase 47 it rebuilt each locale file from the
template and `strings.xml`, so the 17 keys per locale that later phases translated by hand
— the resource viewer's per-media-type error states, which have no Kotlin counterpart to
derive from — were deleted on any re-run, silently and with no error. The tool also emits
literal UTF-8 now, matching how the locale files are actually committed, so a run no longer
rewrites all four; it is idempotent, verified by running it twice. A test pins the
hand-authored keys in all four locales so a destructive regeneration fails the suite.
Phase 70 ports the resources list sort toggles (`14a9f14`, #15941): the Kotlin
bottom sheet's date/title pair had no counterpart, so the port gains
`ResourceSortState` (one direction flag per mode, like the ViewModel's), a pure
`applyResourceSort` applied to the filtered stream at build time, a badged sort
button opening a two-option sheet, and a scroll-to-top shared by both layouts.
Phase 71 ports the member detail screen (`MembersDetailFragment`) and fixes the
team members list, which had rendered each member as the raw `userId` with no
tap target. `MemberDetailScreen` shows the profile photo, full name, leader
badge, and labelled rows (email, DOB, language, phone, level, visits, last
login); a `memberDetailProvider` joins the `users` row with per-team visit
counts (`TeamLogDao.teamVisitsForUsers`/`lastTeamVisit`) and the last login
(`activitiesRepository.lastVisit`); the list resolves real names via a new
`userByIdProvider` and navigates to the detail route on tap.
Phase 72 ports the add-resource screen (create + edit + file pick via
`FilePicker`), team member leader actions (remove / make leader / leave with
tombstone enqueue), and wires the member detail screen to voice authors and
community leaders. The repository gains `saveLocalResource`/
`updateLocalResource`/`resourceTitleExists`; the members list gains a
`PopupMenuButton` overflow menu per member.
Phase 73 ports a standalone WebView screen for external links, the
exam/survey buttons on course steps (`CourseStepFragment`'s `btnTakeTest`/
`btnTakeSurvey`), and the team leaderboard from the `14880` upstream branch
(`TeamLeaderboardCalculator` + `TeamLeaderboardScreen` with all-time/this-month
period toggle).

Phase 74 adds voice emoji reactions (`reactions` on `NewsEntries`, schema v42 —
a preserved table, so it needs the hand-written `_addColumnIfMissing` step) and
inline comment threads on team tasks. **Neither is a Kotlin port**: both are
open issues (#13357, #15112) whose Kotlin PRs are unmerged, so they are the
first features here that do not advance Kotlin→Flutter parity and have no
reference implementation to check against. Kotlin PR #13415 also keeps
`reactions` device-local (it never serializes the field), so the port syncing
them is ahead of that PR, not matched to it; and #15112 asks for threads on
tasks *and* meetups, of which only tasks are wired. The phase also fixes the
reactions round trip: `serializeNews` wrote them into the nested `news`
sub-object while `NewsMapper.fromDoc` read the top level, so a reaction never
reached another device and — because the mapper writes its companion on every
pull — `Value(null)` went over the local column, erasing the user's own
reaction on their next sync. Same shape as the Phase 56 security-data fix.
Each side had a passing test; nothing ran the two together, which is now what
the new coverage does.

Phase 75 ports the chat full-conversation search from `ChatViewModel.searchChats`: a
`ChatSearchMode` enum (`title`/`question`/`response`), ranked matching
(prefix before contains, first conversation before later), recency sort by
`max(createdDate, updatedDate)`, and accent-insensitive normalization. The
search lives as a pure top-level `searchChatsForMode` (and
`sortChatsByRecency`) in `lib/repository/chat_repository.dart` so the
provider can call it without transitively watching
`chatRepositoryProvider`/`planetPrefsProvider` (the latter is
`UnimplementedError` in the widget-test harness). Accent folding reuses the
existing `normalizeText` in `lib/core/utils/text_utils.dart` — see Phase 78.

Phases 76–77 port the courses multi-select shelf actions (Kotlin's
`CourseSelectionController`, plus a fix for a `ref.read` race that silently
no-oped the whole batch) and the course cover banner with markdown descriptions
(`CourseDetailFragment.setCourseCover`; relative image paths are fetched as
authenticated bytes through `PlanetApi.getBytes`, because CouchDB attachments
sit behind Basic auth and `Image.network` cannot send the header). Two
unnumbered ports land alongside: blood-pressure validation now matches Kotlin's
three tiers exactly (`sys` 60–300, `dis` 40–200, verified against
`HealthExaminationActivity.validateFields`), and `PathResourceViewerScreen`
opens a personal note's attachment by extension (`PersonalsAdapter.openResource`
— these are not `MyLibrary` rows, so the id-based viewer cannot reach them).

Phase 78 removes a duplicate `normalizeText`. Phase 75 added
`core/utils/text_normalize.dart` with a hand-written decomposition table, whose
header explained that Dart has no NFD normalizer — true, and already solved:
`core/utils/text_utils.dart` had carried `normalizeText` on the `diacritic`
package since the resource search landed, and both files documented themselves
as the port of the same Kotlin `Utilities.normalizeText`. The two disagreed on 7
of 15 accented samples (`Škoda` → `škoda` vs `skoda`, `Māori` → `māori` vs
`maori`, also `Łódź`/`Çağrı`/`Ærø`), and because chat search used the new one
while resource and course search use the old one, `skoda` found the resource
`Škoda` but not a chat about it. The hand-written file is deleted, chat imports
`text_utils`, and its tests move to `text_utils_test.dart` — which had no
`normalizeText` coverage at all — with the five divergences pinned so a
narrower reimplementation fails the suite. **When you need accent folding, use
`text_utils.normalizeText`; do not hand-roll a table.**

Phase 81 ports the challenge dialog (the December 2024 / January 2025
campaign): a non-guest user on a participating server, between Nov 30 2024
and Jan 16 2025, sees a dialog on dashboard load tracking three tasks
(complete the challenge course, post five community voices, sync). The
`user_challenge_actions` Drift table (schema v43, a preserved
local-authority table) holds the sync-action rows;
`ActivitiesRepository.recordSyncUserChallengeAction` writes the row when
a manual sync starts, and `hasUserCompletedSync` counts it. The
`ChallengeEvaluator` provider (port of `DashboardViewModel.evaluateChallengeDialog`)
gates on guest/window/server and gathers the voice counts, course status,
and sync state; the `ChallengeDialog` widget renders the progress bar and
task rows and routes the action button to the course, voices, or sync
center. The congratulations variant fires once via
`hasShownChallengeCongratsProvider` (backed by `PlanetPrefs`). Four new
l10n keys replace the dialog's hardcoded English strings.

Phases 82–89 fill in settings, health and dashboard gaps, all Kotlin ports:
text size and **reset app** (`SettingsActivity`), the examination detail dialog,
the full diagnosis list with its custom-diagnosis chip cloud, the health profile
editor, the examination exit-confirmation `PopScope`, the inactive-user
dashboard (`rolesList.isEmpty() && userAdmin != true`), survey resume from a
pending submission, and the resource-detail download button state. Two are worth
knowing in detail. **Reset app** is the only destructive action in the app:
behind a Yes/No confirmation it runs `AppDatabase.clearAllData()` (a batched
`DELETE FROM` over `allTables`) plus `PlanetPrefs.clearAllData()`, which calls
`_secureStorage.deleteAll()` so the password and PIN do not outlive the server
they belong to; `onboardingComplete` is kept, and undelivered outbox rows are
wiped along with everything else — correct here, unlike a schema bump, which is
exactly what `localAuthorityTables` protects those rows from. **Survey resume**
updates the existing submission row rather than inserting and keys answers
`<submissionId>:<questionId>`, so resuming cannot produce a second submission.

Phase 90 corrects two things in that batch. `saveHealthProfile` trimmed
`emergencyContact`/`emergencyContactType` but not `emergencyContactName`,
`specialNeeds` or `notes`, where Kotlin trims all five — so the same input
stored differently in the two apps and a whitespace-only entry read as a real
note. And three passages, the headline Status section among them, still called
`ChallengeDialog` "built and called from nowhere" after Phase 81 gave it a
caller; `CustomDropdown` genuinely is still uncalled, so only that half changed.

Phase 91 gives `ResourceViewerScreen` its first tests — 1052 lines that had none,
the port's largest untested surface for five rounds. Eight cover the load and
missing-resource states, the title and its fallback, and every branch of the
download prompt including the stale-offline-flag repair. **If you write tests for
this screen, know the trap:** it resolves attachments through `ResourceFiles`
(real `dart:io`) while a widget test's zone is fake-async, so the file futures
never complete, the screen sits on its `CircularProgressIndicator`, and
`pumpAndSettle` spins on that animation for its ten-minute default — a failure
that looks exactly like a hang. Yield wall-clock time with `runAsync` and *then*
`pump`; pumping inside `runAsync` does not work. Phase 92 closes the gap the
four withdrawn tests were written for: the text/CSV/markdown renderers now take
their bytes from `resourceContentReaderProvider` (a `ResourceFiles.readTextContent`
seam) instead of calling `File.readAsString` in their own `initState`, so four
rendering tests (plain text, the title row, the CSV table, markdown) run without a
real read — a real file still has to exist on disk for `_getLocalFilePath` to route
the screen into the viewer, and that file write runs inside `runAsync`. The
video/PDF/WebView renderers need platform views no widget test can serve.

Phase 95 gives `MyHealthScreen` its first 13 tests — 955 lines, the largest
untested hand-written surface after the viewer — and they found five defects,
four of which failed on the pre-fix code. The screen hand-rolled three helpers
that `ui/components/profile_avatar.dart` already provides, and its copies were
broken: the avatar passed `users.userImage` to `NetworkImage`, but that column
holds a CouchDB **attachment name** (`UserMapper` says so where it writes it),
not a URL, and the attachment is behind Basic auth that `Image.network` cannot
send — so the photo could never load; and `_getInitials` did `parts[0][0]` on
`name.split(' ')` behind an `if (parts.isEmpty)` guard that is **dead code**,
since `''.split(' ')` is `['']`. Because the display-name fallback returns
`user.name` untrimmed, a synced `"name": " jane "` with no first/last name threw
`RangeError` out of `build` and took the screen down. **When you need a user's
avatar, name, or initials, use `ProfileAvatar` / `displayName`; do not hand-roll
them** — same shape as the Phase 78 `normalizeText` duplicate. The other three:
a hardcoded `'Unknown'` in a file that already reads `l10n.unknown`; a
`RefreshIndicator.onRefresh` that was an empty async body (Kotlin has no
`SwipeRefreshLayout` here, so the gesture is the port's own and had never been
wired); and a 140px examination-history strip that overflowed by 8px on a card
carrying date, examiner, temperature, pulse, blood pressure *and* the has-info
icon. Wiring the refresh exposed a sixth: the sync button called
`ref.invalidate(patientDetailProvider)`, which reruns `_loadInitial` and
resolves the **logged-in** user, so a health provider silently lost their
selected patient on every sync — hence `PatientDetailNotifier.refresh()`.
**A note for writing tests here:** the body is a `ListView(children: [...])`,
which builds child widgets eagerly but only *mounts* those in the viewport, and
`find.text` searches the element tree — so a card below the 600px test fold
reports "Found 0 widgets" for content that renders fine on a device. Scroll
first. The same phase merged 26 master commits; they are all "smoother X"
refactors, and the two that do carry behaviour have no port counterpart (the
PDF word-wrap the port delegates to `package:pdf`, and an image-viewer
http/https branch `course_markdown.dart` already has). **Phase 96 audited that
same batch independently and went further**: reading `ResourcesSearchUtils`
as *the* resource search rather than a renamed helper, it found the ranked
search gap this pass missed. Two audits of one batch disagreeing on what it
implies is the useful lesson — a diff that only renames a file can still point
at a behaviour the port never had.

### Documentation Map

| Document | Read it when… |
|----------|---------------|
| `CLAUDE.md` (this file) | You need the codebase layout, architecture, build/CI facts, or task recipes |
| `docs/DOMAIN_MODEL.md` | You need to understand the learning domain — roles, courses, teams, surveys, sync concepts |
| `docs/CODE_STYLE_GUIDE.md` | You're writing code — naming, imports, coroutines, Room, Hilt, UI conventions |
| `docs/TESTING.md` | You're writing or fixing tests — patterns to copy per layer |
| `docs/kotlin-to-flutter-migration.md` | You're working on the Flutter port in `flutter/` — scope, technology mapping, ported slices, open problems |
| `agents-summoning` skill — `.agents/skills/agents-summoning/SKILL.md` (or the `agents-summoning@summoning` plugin in a Claude Code session) | You're summoning another AI agent (`@coderabbitai` `@codex` `@copilot` `@devin` `@jules` `@openhands` `@dependabot`) on a PR or issue — who answers, how fast, with what side effects, and why a summon went silent. Dated receipts in the same skill's `NOTES.md`; connection checklists in its `references/connecting.md` |

Reviewers speak; doers act — an unleashed doer mention (`@openhands`, `@devin`,
`@copilot`) defaults to commits on your branch, so add "comment only" when that
isn't wanted.

---

## Codebase Structure

### Directory Layout

```
myplanet/
├── .github/                    # CI/CD workflows and Dependabot config
│   └── workflows/
│       ├── automerge.yml      # Manually-dispatched queue drainer for `automerge`-labelled PRs
│       ├── build.yml          # Build workflow for all branches
│       ├── labels.yml         # Size-labels each PR on open and on every push
│       ├── playstore.yml      # Hand-started publish of a release the Play Store quota refused
│       ├── release.yml        # Release and Play Store publishing
│       └── test.yml           # Unit test workflow
├── app/                       # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/org/ole/planet/myplanet/
│   │   │   │   ├── MainApplication.kt       # App entry point with Hilt
│   │   │   │   ├── base/                    # Base classes for activities/fragments
│   │   │   │   ├── callback/                # Event listeners and interfaces
│   │   │   │   ├── data/                    # Data services, API, and Room (room/, api/, auth/)
│   │   │   │   ├── di/                      # Dependency injection modules
│   │   │   │   ├── model/                   # Room @Entity models + DTOs (92 files)
│   │   │   │   ├── repository/              # Repository pattern implementations
│   │   │   │   ├── services/                # Background services and workers
│   │   │   │   ├── ui/                      # UI components (28 packages)
│   │   │   │   └── utils/                   # Helper utilities
│   │   │   ├── res/                         # Android resources
│   │   │   │   ├── layout/                  # 181 layout files
│   │   │   │   ├── values/                  # Strings, colors, styles
│   │   │   │   ├── values-{lang}/           # Translations (ar, es, fr, ne, so)
│   │   │   │   └── drawable*/               # Images and icons
│   │   │   └── AndroidManifest.xml
│   │   └── lite/
│   │       └── AndroidManifest.xml          # Lite variant manifest
│   ├── build.gradle                         # Module build config
│   └── proguard-rules.pro
├── gradle/
│   └── libs.versions.toml                   # Centralized dependency versions
├── build.gradle.kts                         # Root build config
├── settings.gradle                          # Project settings
├── gradle.properties                        # Gradle configuration
└── README.md
```

### Package Organization (`org.ole.planet.myplanet`)

| Package | Purpose | Files | Key Items |
|---------|---------|-------|-----------|
| `base/` | Base classes for common functionality | 13 | BaseActivity, BaseRecyclerFragment, BasePermissionActivity, BaseContainerFragment, BaseDashboardFragment, BaseResourceFragment, BaseTeamFragment, BaseExamFragment, BaseMemberFragment, BaseDialogFragment, BaseVoicesFragment, BaseRecyclerParentFragment |
| `callback/` | Event listeners and interfaces | 28 | OnLibraryItemSelectedListener, OnSyncListener, OnTeamUpdateListener, OnChatItemClickListener, OnNewsItemClickListener, and more |
| `data/` | Data access, Room persistence, and API | 40 | NetworkResult.kt; `room/` (AppDatabase, Converters, 37 DAO interfaces in 30 files — several share `LegacyEntityDaos.kt`), `api/` (ApiInterface, ApiClient, ChatApiService, RetryInterceptor), `auth/` (AuthSessionUpdater) |
| `di/` | Hilt dependency injection | 10 | Modules (NetworkModule, RoomModule, RepositoryModule, ServiceModule, SharedPreferencesModule, DispatcherModule, TimeModule) + entry points (CoreDependenciesEntryPoint, ServiceDependenciesEntryPoint) |
| `model/` | Room `@Entity` models and DTOs | 92 | 37 `@Entity` classes (MyCourse, MyLibrary, News, Submission, TeamTask, UserEntity, …) + DTOs (ChatMessage, ChatRequest, ChatResponse, CourseProgressData, Download, ServerAddress, User) |
| `repository/` | Repository pattern implementations | 50 | 23 domain Interface + Impl pairs + sync-facing interfaces (SyncRepository, TeamsSyncRepository, UserSyncRepository) + SubmissionsRepositoryExporter |
| `services/` | Background services and workers | 39 | 22 root-level + `sync/` (7), `upload/` (8), `retry/` (2) |
| `ui/` | User interface components | 183 | 28 feature packages with 16+ ViewModels (courses, resources, teams, chat, etc.) |
| `utils/` | Helper functions | 46 | NetworkUtils, ImageUtils, DialogUtils, FileUploader, AuthUtils, SecurePrefs, ANRWatchdog, and more |

### UI Sub-packages (28 feature packages, 183 files)

| Package | Files | Key Components |
|---------|-------|----------------|
| `ui/calendar/` | 1 | CalendarFragment |
| `ui/chat/` | 8 | ChatDetailFragment, ChatHistoryFragment, ChatViewModel |
| `ui/community/` | 6 | CommunityTabFragment, LeadersFragment |
| `ui/components/` | 5 | CustomSpinner, MarkdownDialogFragment, FragmentNavigator |
| `ui/courses/` | 22 | CourseDetailFragment, TakeCourseFragment, ProgressViewModel |
| `ui/dashboard/` | 12 | DashboardActivity, DashboardViewModel, BellDashboardViewModel |
| `ui/dictionary/` | 1 | DictionaryActivity |
| `ui/enterprises/` | 6 | EnterprisesViewModel, FinancesFragment, ReportsFragment |
| `ui/events/` | 4 | EventsDetailFragment, EventsAdapter |
| `ui/exam/` | 2 | ExamTakingFragment, UserInformationFragment |
| `ui/feedback/` | 7 | FeedbackFragment, FeedbackDetailActivity, FeedbackListViewModel |
| `ui/health/` | 7 | MyHealthFragment, AddExaminationActivity |
| `ui/life/` | 2 | LifeFragment, LifeAdapter |
| `ui/maps/` | 1 | OfflineMapsActivity |
| `ui/notifications/` | 3 | NotificationsFragment, NotificationsViewModel |
| `ui/onboarding/` | 2 | OnboardingActivity, OnboardingAdapter |
| `ui/personals/` | 3 | PersonalsFragment, PersonalsAdapter |
| `ui/ratings/` | 2 | RatingsFragment, RatingsViewModel |
| `ui/references/` | 2 | ReferencesFragment, ReferencesAdapter |
| `ui/resources/` | 10 | ResourcesFragment, AddResourceFragment, CollectionsFragment |
| `ui/settings/` | 4 | SettingsActivity, SettingsViewModel, StorageBreakdownFragment, StorageCategoryDetailFragment |
| `ui/submissions/` | 10 | SubmissionsFragment, SubmissionViewModel |
| `ui/surveys/` | 5 | SurveyFragment, SendSurveyFragment |
| `ui/sync/` | 10 | LoginActivity, LoginViewModel, SyncActivity, SyncConfigurationCoordinator, ProcessUserDataActivity |
| `ui/teams/` | 25 | TeamFragment, TeamDetailFragment, TeamViewModel (largest UI package) |
| `ui/user/` | 10 | UserProfileFragment, UserProfileViewModel, BecomeMemberActivity |
| `ui/viewer/` | 4 | ResourceViewerActivity, ResourceViewerFragment, ResourceViewerViewModel, WebViewActivity (all media types render through the shared resource viewer) |
| `ui/voices/` | 9 | VoicesFragment, NewsViewModel, ReplyActivity |

### Critical Files to Understand

1. **`MainApplication.kt`** (~537 lines)
   - Application initialization with Hilt DI
   - WorkManager scheduling (AutoSyncWorker, TaskNotificationWorker, NetworkMonitorWorker, RetryQueueWorker)
   - Server reachability checking with alternative URL mapping
   - Theme/locale management, ANR watchdog, uncaught exception handling
   - Location: `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`

2. **`AppDatabase.kt`** (~170 lines) — the Room database
   - `@Database` with 37 entities, `version = 6`, `@TypeConverters(Converters::class)`
   - Declares all 30+ DAO accessors; provisioned by `RoomModule` with a **drop-and-resync** (`fallbackToDestructiveMigration`) strategy — no hand-written migrations; data is re-pulled from CouchDB on first launch after a schema bump
   - Location: `app/src/main/java/org/ole/planet/myplanet/data/room/AppDatabase.kt`

3. **`SyncManager.kt`** (~691 lines)
   - Orchestrates data synchronization with server via StateFlow-based state management (`SyncStatus` Idle/Syncing/Success/Error)
   - Delegates per-table pulls to TransactionSyncManager; notifies UI via RealtimeSyncManager's SharedFlow; batch sizing via AdaptiveBatchProcessor
   - Location: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`

4. **`UploadManager.kt`** (~615 lines)
   - File and data uploads with batch processing (BATCH_SIZE = 50)
   - Integrates with UploadCoordinator for orchestrated uploads
   - Handles activities, submissions, photos, news uploads
   - Location: `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt`

5. **`TeamsRepositoryImpl.kt`** (~1437 lines — largest file; candidate for splitting by responsibility)
   - Team management with reactive Flow-based queries
   - Team creation, task management, membership roles
   - Location: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`

6. **`ApiInterface.kt`** (~65 lines)
   - All REST API endpoint definitions (file downloads/uploads, document CRUD, version checking, health access, AI/chat endpoints)
   - Location: `app/src/main/java/org/ole/planet/myplanet/data/api/ApiInterface.kt`

---

## Technology Stack

### Core Technologies

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| **Language** | Kotlin | 2.4.10 | Primary development language |
| **Build System** | Gradle | 9.6.1 | Build automation |
| **Build Plugin** | Android Gradle Plugin | 9.3.1 | Android build tooling |
| **DI Framework** | Dagger Hilt | 2.60.1 | Dependency injection |
| **Database** | Room (AndroidX) | 2.8.4 | Local SQLite object database |
| **Networking** | Retrofit | 3.0.0 | REST API client |
| **HTTP Client** | OkHttp | 5.4.0 | HTTP communication |
| **JSON** | Gson | 2.14.0 | JSON serialization |
| **Async** | Kotlin Coroutines | 1.11.0 | Asynchronous programming |
| **Background Tasks** | AndroidX Work | 2.11.2 | Background job scheduling |
| **UI Framework** | Material Design 3 | 1.14.0 | UI components |
| **Image Loading** | Glide | 5.0.9 | Image loading and caching |
| **Media Playback** | Media3 (ExoPlayer) | 1.10.1 | Audio/video playback |
| **Markdown** | Markwon | 4.6.2 | Markdown rendering |
| **Maps** | OSMDroid | 6.1.20 | OpenStreetMap integration |
| **Encryption** | Tink | 1.23.0 | Cryptographic operations |
| **Serialization** | Kotlin Serialization | 1.11.0 | Kotlin-native serialization |
| **CSV** | OpenCSV | 5.12.0 | CSV file parsing |

### Build Configuration

**Gradle Plugins** (declared in `app/build.gradle` — only these three):
- `com.android.application`
- `com.google.devtools.ksp` — the **only** annotation-processing path (`kapt` was removed entirely); Room, Glide, and all Hilt compilers run through KSP (`ksp`/`kspTest`/`kspAndroidTest`)
- `com.google.dagger.hilt.android`

Kotlin itself is applied via AGP's built-in Kotlin support (no `kotlin-android` plugin alias); `kotlin-gradle-plugin` and `kotlin-serialization` sit on the root buildscript classpath.

**Compiler Settings:**
- Java Compatibility: 17
- Kotlin JVM Target: 17
- View Binding: Enabled
- Data Binding: Not enabled
- BuildConfig: Enabled

---

## Architecture Patterns

### 1. Layered Architecture

```
UI Layer (Activities/Fragments + 16+ ViewModels)
    ↓
Repository Layer (23 domains, Interface + Impl pairs, Flow-based queries)
    ↓
Service Layer (ApiInterface, SyncManager, UploadCoordinator)
    ↓
Data Sources (Room local DB via DAOs, REST API, SharedPreferences)
```

### 2. Repository Pattern

**Convention**: Each data domain has an interface and implementation. Implementations inject the **Room DAOs** they need (plus `ApiInterface`, `DispatcherProvider`, other repositories as needed) and return plain `@Entity`/data-class instances. Multi-DAO atomic work uses Room's `withTransaction` on the `AppDatabase`.

```kotlin
// Real example — repository/CommunityRepositoryImpl.kt
class CommunityRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val communityDao: CommunityDao,
    private val meetupDao: MeetupDao
) : CommunityRepository {
    override suspend fun getAllSorted(): List<Community> = communityDao.getAllSorted()
}

// Dispatcher discipline — repository/DownloadRepositoryImpl.kt
class DownloadRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider
) : DownloadRepository {
    override suspend fun downloadFileResponse(url: String, authHeader: String): DownloadResult =
        withContext(dispatcherProvider.io) { ... }
}
```

**All 23 Domain Repositories:**
Activities, Chat, Community, Configurations, Courses, Download, Events, Feedback, Health, Life, Notifications, Personals, Progress, Ratings, Resources, Retry, Submissions, Surveys, Tags, Teams, Upload, User, Voices

**Sync-facing interfaces & utilities:**
- `SyncRepository`, `TeamsSyncRepository`, `UserSyncRepository` - narrow interfaces the sync managers depend on
- `SubmissionsRepositoryExporter` - Export utilities

There is no generic base repository; each implementation talks to its Room DAO(s) directly.

**Location**: `app/src/main/java/org/ole/planet/myplanet/repository/`

### 3. Dependency Injection (Hilt)

**Module Structure (8 modules):**
- `NetworkModule.kt` - Provides Retrofit, OkHttp
- `RoomModule.kt` - Builds the `AppDatabase` (with `fallbackToDestructiveMigration`) and provides every DAO
- `RepositoryModule.kt` - Binds repository interfaces to implementations
- `ServiceModule.kt` - Provides service dependencies
- `SharedPreferencesModule.kt` - Provides SharedPreferences
- `DispatcherModule.kt` - Provides coroutine dispatchers and `@ApplicationScope`
- `TimeModule.kt` - Provides time/clock abstractions

**Entry Points for Workers (2 entry point files):** `CoreDependenciesEntryPoint`, `ServiceDependenciesEntryPoint`. Workers can't use constructor injection, so they fetch dependencies via `EntryPointAccessors.fromApplication(applicationContext, CoreDependenciesEntryPoint::class.java)`.

**Location**: `app/src/main/java/org/ole/planet/myplanet/di/`

### 4. Base Classes for Code Reuse (13 classes)

| Base Class | Purpose |
|------------|---------|
| `BaseActivity` | Common activity functionality (permission handling, dialogs) |
| `BasePermissionActivity` | Runtime permission request handling |
| `BaseRecyclerFragment` | List-based fragments (pagination, filtering, search) |
| `BaseRecyclerParentFragment` | Parent fragment for recycler views |
| `BaseContainerFragment` | Navigation containers (fragment transactions) |
| `BaseDashboardFragment` | Dashboard-specific base functionality |
| `BaseResourceFragment` | Resource handling (download, view, share) |
| `BaseTeamFragment` | Team-specific base functionality |
| `BaseExamFragment` | Exam-specific base functionality |
| `BaseMemberFragment` | Member management base functionality |
| `BaseDialogFragment` | Dialog base class |
| `BaseVoicesFragment` | Voices/news-specific base functionality |

**Location**: `app/src/main/java/org/ole/planet/myplanet/base/`

### 5. Background Processing

**AndroidX Work for Scheduled Tasks:**
- `AutoSyncWorker` - Periodic data synchronization
- `NetworkMonitorWorker` - Network state monitoring
- `ServerReachabilityWorker` - Server availability checking
- `TaskNotificationWorker` - Task deadline notifications
- `DownloadWorker` - Background file downloads
- `FreeSpaceWorker` - Disk space monitoring
- `UserDataWorker` - Background processing of pulled user data
- `HeavyTableSyncWorker` - Large-table background sync (`services/sync/`)
- `RetryQueueWorker` - Retries failed operations (`services/retry/`)

**Services and Managers (22 root-level files):**
- `SyncManager` - Manual synchronization (`services/sync/`)
- `UploadManager` - File upload coordination (extends FileUploader)
- `UploadToShelfService` - Shelf upload operations
- `UploadCoordinator` - Upload orchestration (`services/upload/`)
- `AudioRecorder` - Audio recording
- `BroadcastService` - Service broadcasting
- `SharedPrefManager` - SharedPreferences management
- `UserSessionManager` - User session handling
- `ThemeManager` - App theming
- `FileUploader` - File upload utilities
- `DownloadService` - Background file download service (foreground service)
- `ResourceDownloadCoordinator` - Orchestrates resource downloads
- `SubmissionUploadExecutor` - Executes submission uploads
- `VoicesLabelManager` - Voice/discussion forum label management
- `ChallengePrompter` - Challenge prompt generation
- `NotificationActionReceiver` - Broadcast receiver for notification actions

**Sync Sub-package (`services/sync/` - 7 files):**
- `SyncManager` (~691) - Orchestrates sync via StateFlow; the entry point for full syncs
- `TransactionSyncManager` (~519) - Per-table paginated pulls from CouchDB with checkpoint/resume
- `LoginSyncManager` (~195) - Sync triggered around the login flow
- `ServerUrlMapper` (~116) - Maps primary server URLs to alternative/clone URLs
- `HeavyTableSyncWorker` (~66) - WorkManager worker for large-table background sync
- `AdaptiveBatchProcessor` (~37) - Batch-size tuning used by SyncManager
- `RealtimeSyncManager` (~27) - SharedFlow of `TableDataUpdate` events; UI collects `dataUpdateFlow` (via `RealtimeSyncHelper`/`collectWhenStarted`)

**Upload Sub-package (`services/upload/` - 8 files):**
- `UploadCoordinator` - Central orchestration for all upload operations with batch processing and retry
- `UploadConfigs` - Configuration objects for different upload types (NewsActivities, Submissions, Photos, etc.)
- `UploadConfig` - Generic configuration template with batch size and model binding
- `RoomUploadConfig` - Room-DAO-backed upload configuration
- `UploadResult` - Result wrapper with success/failure/empty states
- `UploadConstants` - Shared upload constants
- `PhotoUploader`, `AchievementUploader` - Type-specific uploaders

**Retry Sub-package (`services/retry/` - 2 files):**
- `RetryQueue` - Queue-based retry mechanism for failed operations
- `RetryQueueWorker` - Background worker for processing retries

**Location**: `app/src/main/java/org/ole/planet/myplanet/services/`

---

## Development Workflows

### Building and Running

```bash
# Build debug APK (default / lite flavor)
./gradlew assembleDefaultDebug
./gradlew assembleLiteDebug

# Build release
./gradlew assembleDefaultRelease bundleDefaultRelease

# Install and run on a device/emulator
./gradlew installDefaultDebug && adb shell am start -n org.ole.planet.myplanet/.ui.onboarding.OnboardingActivity
```

### Branch Strategy

**Important**: Always work on branches starting with `claude/` and matching the session ID format. Always use `-u` on the first push:

```bash
git checkout -b claude/feature-name-sessionid
# ...develop, commit with descriptive messages (fix:/feat:/refactor: prefixes)...
git push -u origin claude/feature-name-sessionid
```

See `docs/CODE_STYLE_GUIDE.md` → "Branch & PR Standards" for commit-message and PR conventions.

**Session opened on a branch that isn't `master`?** That's a takeover of someone else's PR branch: the web UI binds its PR panel to this session's auto-minted `claude/…` outcome branch instead, and the PR's CI failures and review comments never arrive. Call `get_session` first, then follow the `overtaking` skill — wired as a plugin in `.claude/settings.json` — to bind a session to the branch and subscribe to the PR. If the plugin didn't load (an empty `~/.claude/plugins/installed_plugins.json` is the tell), clone the marketplace repo listed there and read its `SKILL.md`.

### CI/CD Pipeline

**Build Workflow** (`.github/workflows/build.yml`)
- Triggers: All branches except `master` (includes `claude/**`, `codex/**`, `dependabot/**`, `jules/**`)
- Runs on Ubuntu 24.04
- Matrix builds both `default` and `lite` flavors with fail-fast disabled
- Uses `gradle/actions/setup-gradle@v6` with a remote Gradle build cache
- Build command: `./gradlew assemble${FLAVOR^}Debug --configuration-cache-problems=warn --warning-mode all --stacktrace --parallel --max-workers=4`

**Test Workflow** (`.github/workflows/test.yml`)
- Triggers: every push (all branches) + manual dispatch; `permissions: contents: read`
- Runs `./gradlew testDefaultDebugUnitTest` — **fails the build on any unit-test failure**
- **Two shards, prioritizing wall clock.** `app/build.gradle` `testOptions` implements `-PtestShardTotal=N -PtestShardIndex=I` (each top-level test class is hashed by class-file path into a shard; inner classes follow their outer class; an out-of-range index aborts at configuration time; shards verified disjoint and exactly covering — 174 classes = 86 + 88). CI runs `shard: [1, 2]`: measured on this branch, shards were equal-or-faster in every cache regime (warm source change ~3:03–3:37 vs ~3:57–4:14 unsharded; cold ~4:53 vs ~6:25; no-change ties at ~0:45) at the cost of a second runner per push. Drop the matrix entry to fall back to one job if runner budget outranks wall time
- `default` flavor only (the `lite` flavor's unit tests are not run in CI)
- Passes `-ProbolectricOffline=true`, which makes Gradle stage Robolectric's `android-all-instrumented` jars into `build/robolectric-sdks` (see `robolectricSdkJars` in `app/build.gradle`) instead of letting each test fork download them at runtime — concurrent forks fetching the same jar were poisoning one fork's Robolectric sandbox (`AndroidVersions.CURRENT` null, then `NoSuchFieldError` on framework fields) and costing a rerun. Adding a `@Config(sdk = [N])` for a new API level means adding its jar to that map
- Both `test.yml` and `build.yml` cache `app/build` + `.gradle` per job (`actions/cache`, keyed on the SHA and falling back to the newest earlier run) and pass `cache-read-only: false` to `setup-gradle` — without the latter, `setup-gradle` keeps the Gradle home (and its local build cache) read-only off master, so no branch run could seed it and every push started cold. Measured on one branch: 6m25s cold → ~4m for a push that touches one source file → ~45s for a push that touches no Gradle inputs (workflow/doc-only), where every task, including the test task, is `FROM-CACHE`
- `GRADLE_BUILD_CACHE_URL/USER/PASS` are currently **empty secrets**, so `settings.gradle` disables the remote cache and `GRADLE_BUILD_CACHE_PUSH` is inert; all cache hits today come from the Actions-cached Gradle home
- No instrumented (`androidTest`) execution in CI

**Release Workflow** (`.github/workflows/release.yml`)
- Triggers: `master` branch push or manual dispatch
- Builds signed APK and AAB for both flavors
- Signs with keystore credentials via GitHub Secrets
- Generates SHA256 checksums for integrity verification
- Publishes to Google Play Store (internal track) with fallback retry; a refused upload (usually `Daily save quota exceeded.`) only warns, and the warning links `playstore.yml`, which publishes that bundle later without a rebuild
- Creates GitHub release with artifacts (tag: `v${VERSION}`)
- Sends Discord notifications via Treehouses CLI

**Playstore Workflow** (`.github/workflows/playstore.yml`)
- Menu: `resume_automerge` (default **on** — unsticking the drain is why this gets pressed), `wait_minutes`, `dry_run`. `PLAYSTORE_TRACK`, `PLAYSTORE_DAILY_LIMIT` and `RETRY_MINUTES` are constants in the job's `env:`
- **Never scheduled** — the *Run workflow* button (linked from the release warning and the automerge stop), `gh workflow run playstore.yml`, or a `repository_dispatch` with `event_type: playstore`, which carries no inputs and so never resumes the drain
- If the track is behind the newest GitHub release, re-uploads that release's signed `myPlanet-lite.aab`: no rebuild, no new version code. It reads the track rather than trusting the newest `release.yml` run's warning — a track read opens an edit and deletes it unsaved, so it spends no save quota, and a release run that died before uploading leaves the same silence as one that published. The run decides two things only: wait while one is in flight, and, when the track cannot be read at all, upload blind if it warned. Logic in `.github/scripts/playstore.sh`
- The quota is ~50 slots, each freeing 24h after its own use rather than at midnight (6514 refused 02:57 Pacific on 2026-08-18 after 10 saves that day, run 32123984765; 6714 refused on 2026-08-26 as the 51st release in its window, run 32930850241) — at ~6 min per release a drain eats it in an afternoon. `playstore-quota.sh` estimates the next slot as the oldest still held + 24h + a measured ~300s lag (6714 refused 08:11:16Z, accepted 08:12:17Z, where crisp-24h predicted 08:07:27Z); `forecast` prints the next 10 in eastern time

**Automerge Workflow** (`.github/workflows/automerge.yml`)
- Manually dispatched queue drainer for PRs labelled `automerge`, ordered by priority tier then PR number: PRs also labelled `priority` (`PRIORITY_LABEL`, blank = no tier) drain first
- Per PR: merge the base in, bump the version, wait for build + test on that prepared commit, squash-merge
- Two ways a PR leaves the queue without stopping the drain — it loses `automerge`, gains a mark, and the queue moves on. **conflict** (`CONFLICT_LABEL`): `mergeable: CONFLICTING`, or the real `git merge` failing. **failing** (`FAILING_LABEL`, dark red): build or test red on the prepared commit, which is a verdict on that PR alone. Fix it and re-add `automerge`
- What stops the drain instead: no verdict at all on the prepared commit (no run appeared, or the wait timed out — that says nothing about the PR), a red base, or a release that never reached the Play Store, whose stop names the next save slot and links `playstore.yml`
- A red workflow gets one re-run before it counts, on the base and on a prepared commit alike (`retries`, default 1): each passed build + test on its own minutes earlier, so the first failure of the two together is treated as flaky until it repeats
- Menu: `dry_run` (default `false`, so a dry run is the deliberate tick), `max_merges`, `labels` (`queue,priority,conflict,failing` in one comma-separated field — empty slot = that label goes unused, missing slot = the script's default) and `retries` (0–3). Every other setting is in the workflow's `env:`, so changing one is a reviewable diff and `playstore.yml`'s handover inherits it
- Logic in `.github/scripts/automerge.sh`; needs `AUTOMERGE_TOKEN` (the default `GITHUB_TOKEN` cannot push to the protected base)

**Labels Workflow** (`.github/workflows/labels.yml`)
- Runs on `pull_request_target` (`opened`, `synchronize`, `reopened`, `ready_for_review`), so it re-labels on every push and works on fork and Dependabot PRs, where a `pull_request` token would be read-only. It never checks out PR code — it only reads diff numbers through the API
- Two independent rules, both from `.github/scripts/labels.sh`:
  - **size** from additions + deletions — `small` ≤ 60, `medium` ≤ 100, `large` ≤ 200, `enormous` above that (`SMALL_MAX`/`MEDIUM_MAX`/`LARGE_MAX`)
  - **`less`** when the PR only removes code (0 additions, some deletions). It sits *alongside* the size label (`small` + `less`), matching how the label has been used by hand
- Two exclusions, and both are load-bearing. `EXCLUDE_PATHS` drops `values-*/strings.xml`, because one translated string lands in all five and would count 6×. The version-only lines `automerge.sh` writes into `app/build.gradle` are discounted, because that bump takes a pure deletion from 0 additions to 2 — without the discount, draining the queue would strip `less` from exactly the PRs that earned it

**Dependabot** (`.github/dependabot.yml`)
- Daily checks for GitHub Actions updates (max 10 open PRs)
- Daily checks for Gradle dependency updates (max 15 open PRs)

### Adding New Features

1. **Identify the Layer**
   - UI change? → `ui/` package
   - Data model? → `model/` package
   - Business logic? → `repository/` or `services/`
   - Network API? → `data/api/ApiInterface.kt`

2. **Create Necessary Components**
   - Model class (Room `@Entity` + a DAO if persistent)
   - Repository interface + implementation
   - UI components (Activity/Fragment)
   - Layout XML files

3. **Wire everything up**
   - New dependencies go in `gradle/libs.versions.toml`, referenced from `app/build.gradle`
   - Register new activities (and permissions) in `AndroidManifest.xml`
   - Provide/bind new dependencies in the appropriate `di/` module (`RepositoryModule` for repositories)

---

## Key Conventions

> Full coding conventions live in **`docs/CODE_STYLE_GUIDE.md`** (naming, imports, null safety, coroutines, Hilt, UI, resources, error handling). The rules below are the project-specific ones that most often trip up newcomers.

**File Naming:**
- Activities/Fragments/ViewModels/Adapters/Workers: `*Activity.kt`, `*Fragment.kt`, `*ViewModel.kt`, `*Adapter.kt`, `*Worker.kt`
- Repositories: `*Repository.kt` (interface) and `*RepositoryImpl.kt`
- Room DAOs: `*Dao.kt` in `data/room/dao/` (e.g., `RatingDao.kt`; the legacy-entity DAOs like `CourseDao` share `LegacyEntityDaos.kt`)
- Models/Entities: plain names in `model/` (e.g., `MyCourse.kt`, `Submission.kt` — the old `Realm*` prefix is gone)
- Layouts: `activity_*.xml`, `fragment_*.xml`, `row_*.xml` / `item_*.xml`, `dialog_*.xml`

### Room Database Conventions

> All **structured domain data** goes through Room — the `AppDatabase` (`data/room/AppDatabase.kt`), its DAOs (`data/room/dao/`), and `Converters` (`data/room/Converters.kt`). There is no other database, so reach for DAOs (multi-DAO atomic work via `AppDatabase.withTransaction`), never a raw SQLite or third-party-DB API. (Key-value settings live in `SharedPreferences`, and sensitive preferences in the Tink-backed `SecurePrefs` — see Security Considerations.)

**Entity (model) Classes:**
```kotlin
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses", indices = [Index("courseId"), Index("_id")])
open class MyCourse(
    @PrimaryKey @JvmField var id: String = "",
    @ColumnInfo(name = "_id") var _id: String? = null,
    var courseTitle: String? = null,
    var description: String? = null,
    var createdDate: Long = 0,
    // ... other fields
) {
    @Ignore var courseSteps: MutableList<CourseStep>? = null   // non-persisted helper
}
```

**Key Points:**
- Entities live in `model/` and are annotated with `@Entity(tableName = "...")` (e.g. `MyCourse`, `MyLibrary`, `News`, `Submission`, `UserEntity`, `TeamTask`).
- Use `@PrimaryKey` for the key; add `@Index`/`indices` for frequently queried columns.
- `@ColumnInfo(name = "_id")` maps the CouchDB `_id`/`_rev` fields to Kotlin-friendly property names.
- Non-persisted, computed, or in-memory-only fields use `@Ignore` (and often `@Transient`).
- Multi-valued fields (`List<String>`, nested objects, `Date`) are persisted via `@TypeConverters(Converters::class)` — the converters serialize them to JSON strings with Gson.

**Database Operations — use DAOs (preferred pattern):**

Repositories inject the **DAO(s)** they need directly (provided by `RoomModule`) and call `suspend` DAO functions. Reactive queries return `Flow<…>` (non-suspend, per Room's requirement) — 8 of the 30 DAO files expose them, named either `observe*` (e.g. `CourseDao.observeAll()`) or `*Flow` (e.g. `NewsDao.getTopLevelFlow()`).

```kotlin
// Real DAO examples
// data/room/dao/LegacyEntityDaos.kt — several DAOs share this file, @Upsert style
@Dao
interface CourseDao {
    @Query("SELECT * FROM courses") suspend fun getAll(): List<MyCourse>
    @Query("SELECT * FROM courses") fun observeAll(): Flow<List<MyCourse>>
    @Upsert suspend fun upsertAll(items: List<MyCourse>)
}

// data/room/dao/RatingDao.kt — IS for nullable params, = for non-null
@Query("SELECT * FROM rating WHERE type IS :type AND item IS :item")
suspend fun getByTypeAndItem(type: String?, item: String?): List<Rating>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertAll(items: List<Rating>)
```

**Rules:**
- Inject the specific DAO into a repository. For an atomic multi-DAO transaction, use Room's `withTransaction` on the `AppDatabase`.
- DAO methods are `suspend` (Room runs the query off the main thread via its own executors, so they're safe to call from any dispatcher) or `Flow`-returning — never block the main thread with DB work. `DictionaryActivity` also uses a DAO (`DictionaryDao`) now; there is no raw-DB escape hatch.
- Use `IS` (not `=`) in DAO `@Query` predicates when a `null` argument should match `NULL` rows (`=` never matches `NULL` in SQL).
- **Migration strategy is drop-and-resync**: `RoomModule` builds the DB with `fallbackToDestructiveMigration(true)`. On any schema change bump `version` in `AppDatabase`; there are **no** hand-written `Migration` objects — data is re-pulled from the Planet/CouchDB server on the **next sync** (login-triggered or scheduled auto-sync; the rebuild itself does not start one). ⚠️ The rebuild also discards **unsynced local writes** (pending uploads, drafts) — a schema bump ships that loss to any device that hasn't synced, so treat version bumps as releases that need pending data uploaded first.
- Inject `DispatcherProvider` (don't hard-code `Dispatchers.IO`) so tests can substitute deterministic dispatchers.

### Localization

Supported languages: English (default) + Arabic (ar), Spanish (es), French (fr), Nepali (ne), Somali (so). Add new strings to `app/src/main/res/values/strings.xml` and add the translation to each `values-{lang}/strings.xml`.

---

## Common Tasks

### Adding a New Data Model

1. **Create the Entity**
   ```kotlin
   // app/src/main/java/org/ole/planet/myplanet/model/MyNewModel.kt
   @Entity(tableName = "my_new_models")
   open class MyNewModel(
       @PrimaryKey var _id: String = "",
       var title: String? = null,
       var createdDate: Long = 0,
   )
   ```

2. **Create a DAO**
   ```kotlin
   // app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyNewModelDao.kt
   @Dao
   interface MyNewModelDao {
       @Query("SELECT * FROM my_new_models")
       suspend fun getAll(): List<MyNewModel>

       @Insert(onConflict = OnConflictStrategy.REPLACE)
       suspend fun insertAll(items: List<MyNewModel>)
   }
   ```

3. **Register the entity + DAO in `AppDatabase`** and provide the DAO in `RoomModule`
   ```kotlin
   // AppDatabase.kt: add MyNewModel::class to @Database(entities = [...]) and BUMP `version`
   abstract fun myNewModelDao(): MyNewModelDao
   // RoomModule.kt
   @Provides fun provideMyNewModelDao(db: AppDatabase) = db.myNewModelDao()
   ```

4. **Add the endpoint** in `data/api/ApiInterface.kt` if the model syncs with the server.

5. **Create Repository** (interface + Impl injecting the DAO) and **bind it** in `di/RepositoryModule.kt`:
   ```kotlin
   @Binds
   abstract fun bindMyNewModelRepository(impl: MyNewModelRepositoryImpl): MyNewModelRepository
   ```

### Adding a New Screen

1. Create layout XML in `res/layout/activity_my_feature.xml`
2. Create `@AndroidEntryPoint` Activity/Fragment extending appropriate base class with view binding
3. Register in `AndroidManifest.xml`
4. Navigate with `Intent(context, MyFeatureActivity::class.java)`

### Implementing Offline Sync

1. **Download**: Fetch from API, upsert into Room via a DAO `@Insert(onConflict = REPLACE)` (see `TransactionSyncManager` for the paginated per-table pulls)
2. **Upload**: Query unsynced items via a DAO (`... WHERE synced = 0`), POST each to server through `UploadCoordinator`, mark synced on success

### Adding Background Work

1. Create `CoroutineWorker` subclass with `doWork()` returning `Result.success()` or `Result.retry()`; fetch dependencies via `CoreDependenciesEntryPoint`/`ServiceDependenciesEntryPoint`
2. Schedule with `PeriodicWorkRequestBuilder<MyWorker>(interval, unit)` + network constraints + `WorkManager.enqueueUniquePeriodicWork`

---

## Testing Guidelines

> Full testing patterns (what to copy per layer, shared infra, naming) live in **`docs/TESTING.md`**.

### Current State
- **A real unit-test suite exists**: 166 unit-test files in `app/src/test/`. There is currently **no** `app/src/androidTest/` (instrumented) source set.
- **Stack**: JUnit4, **MockK** (`mockk` / `mockk-android`), **Robolectric**, `kotlinx-coroutines-test`, AndroidX Test (`core`/`ext`/`runner`/`arch-core-testing`), **Room testing** (`room-testing`), and **Hilt testing** (`hilt-android-testing` with `kspTest`). Dependencies are declared in `app/build.gradle` (test block) and `gradle/libs.versions.toml`.
- **Coverage**: nearly all 23 repositories, the sync managers (`services/sync/`), upload/retry services, most ViewModels, many `utils/`, several Room entities/DAOs, DI modules, and the API/auth layer.
- **Shared test infra**: `MainDispatcherRule`, `TestDispatcherProvider` (inject deterministic dispatchers — production code uses an injectable `DispatcherProvider`, so avoid hard-coding `Dispatchers.*` in new code).
- **CI enforcement**: `.github/workflows/test.yml` runs `./gradlew testDefaultDebugUnitTest` on every push and fails the build on any test failure. (Instrumented tests are **not** run in CI.)

### Running Tests

```bash
# Unit tests (default flavor) — what CI runs
./gradlew testDefaultDebugUnitTest

# Unit tests (lite flavor) — NOT covered by CI; run locally when touching flavor-specific code
./gradlew testLiteDebugUnitTest

# A single test class
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.CoursesRepositoryImplTest"
```

**Core conventions**: MockK (not Mockito), `runTest { }` + `MainDispatcherRule` for coroutine code, `TestDispatcherProvider` instead of real dispatchers, Robolectric for Android framework classes, and mirror the `main` package path (an organizational convention — Gradle discovers tests from the compiled test source set regardless of package).

### Manual Testing Checklist

When making changes, verify:
- [ ] App builds successfully
- [ ] Feature works in offline mode
- [ ] Synchronization works correctly
- [ ] UI renders on different screen sizes
- [ ] Dark theme works correctly (if applicable)
- [ ] All supported languages display correctly
- [ ] Permissions are requested appropriately
- [ ] Background sync continues to work

---

## Security Considerations

### Sensitive Data Handling

**Never hardcode:** API keys, passwords, server URLs / server PINs, user credentials.

> ⚠️ **KNOWN ISSUE — secrets currently committed.** `gradle.properties` is **tracked in git** (it is *not* gitignored) and holds real `PLANET_*_URL` / `PLANET_*_PIN` values. `app/build.gradle` bakes each into `BuildConfig`, and since `minifyEnabled=false` they are trivially recoverable from any shipped APK. These PINs are real CouchDB `satellite` credentials (used in `UrlUtils.header`, `ConfigurationsRepositoryImpl.buildCouchdbUrl`, and the `/healthaccess` PIN). **Do not add new secrets here.** Remediation: rotate the exposed PINs server-side, move values to an untracked file / CI secrets, gitignore `gradle.properties`, and purge it from git history.

**Preferred pattern** — inject config via untracked properties (`local.properties` / `secrets.properties`); in CI use the provider's secret store (`-P` flags are for non-secret config only — command-line values can surface in logs and build scans). Expose only **non-secret** configuration (public URLs like `BuildConfig.PLANET_LEARNING_URL`) as `BuildConfig` fields — anything in `BuildConfig` is recoverable from the shipped APK regardless of where it came from, so PINs/passwords need a runtime authentication mechanism, not a build-time constant.

### Other Security Facts

- **Network security config**: `app/src/main/res/xml/network_security_config.xml` (trusted certs, cleartext policy)
- **Encrypted storage**: `SecurePrefs` (Tink-based) for sensitive preferences
- **Password verification**: PBKDF2 in `AndroidDecrypter.androidDecrypter()` (HmacSHA1, per-user salt) — not where the names suggest: `Sha256Utils` only computes SHA-512 **file checksums**, and `AuthUtils` contains no hashing
- **ProGuard/R8**: `minifyEnabled` is currently `false` for both debug and release builds

---

## Troubleshooting

**Issue: Room schema mismatch / "Room cannot verify the data integrity"**
- The app uses drop-and-resync: bump `version` in `AppDatabase` after any entity change. `RoomModule` already builds with `fallbackToDestructiveMigration(true)`, so the local DB is rebuilt and re-pulled from the server — no hand-written `Migration` needed.

**Issue: KSP annotation processing errors**
- `./gradlew clean`, remove `.gradle/`, rebuild.

**Issue: Hilt dependency not found**
- Ensure `@AndroidEntryPoint` annotation is present; verify a module provides the dependency; check injection point (constructor vs field).

**Issue: Blocking the main thread on DB access**
- Room DAO methods in this project are `suspend` (apart from the `Flow`-returning ones); Room executes the query off the main thread through its own executors. Call them from a coroutine (`viewLifecycleOwner.lifecycleScope.launch`) — never run DB work synchronously on the main thread.

**Issue: Push fails with 403**
- Ensure branch name starts with `claude/` and ends with the matching session ID; use `git push -u origin <branch-name>` (see **Branch Strategy** above for details).

---

## Additional Resources

### External Documentation
- [myPlanet Manual](https://open-learning-exchange.github.io/#!pages/manual/myplanet/overview.md)
- [Room Documentation](https://developer.android.com/training/data-storage/room)
- [Hilt Documentation](https://developer.android.com/training/dependency-injection/hilt-android)

### Community
- [Discord Server](https://discord.gg/BVrFEeNtQZ)
- [GitHub Issues](https://github.com/open-learning-exchange/myplanet/issues)

### Key File References

| Purpose | File Path | Line Count |
|---------|-----------|------------|
| Main entry point | `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt` | ~537 |
| REST API endpoints | `app/src/main/java/org/ole/planet/myplanet/data/api/ApiInterface.kt` | ~65 |
| Room database | `app/src/main/java/org/ole/planet/myplanet/data/room/AppDatabase.kt` | ~170 |
| Sync orchestration | `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` | ~691 |
| Upload handling | `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt` | ~615 |
| Upload orchestration | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` | ~478 |
| Team management | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` | ~1437 |
| Build configuration | `app/build.gradle` | ~231 |
| Dependency versions | `gradle/libs.versions.toml` | ~132 |

---

## Codebase Inventory Summary

### Source Files (502 total Kotlin files in `app/src/main/java`) + 166 unit-test files in `app/src/test` (no `app/src/androidTest` source set)

| Component | Files | Purpose |
|-----------|-------|---------|
| `model/` | 92 | Room `@Entity` models + DTOs |
| `repository/` | 50 | Data access abstraction (23 domain Interface+Impl pairs + sync interfaces + utilities) |
| `ui/` | 183 | User interface across 28 feature packages |
| `services/` | 39 | Background tasks & managers (22 root-level + sync/upload/retry sub-packages) |
| `di/` | 10 | Dependency injection (8 modules + 2 entry points) |
| `base/` | 13 | Reusable base classes |
| `callback/` | 28 | Event listeners and interfaces |
| `data/` | 40 | Data services, Room (AppDatabase, Converters, 37 DAO interfaces in 30 files), API, auth |
| `utils/` | 46 | Helper utilities |
| Root | 1 | MainApplication.kt |

### Resource Files

| Category | Count |
|----------|-------|
| Layout files (main) | 181 |
| Translation languages | 5 (ar, es, fr, ne, so) |
| Menu files | 2 |
| XML config files | 3 |

### AndroidManifest Permissions (16 total)

**Network**: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, CHANGE_NETWORK_STATE
**Device**: CAMERA, RECORD_AUDIO, WAKE_LOCK, BLUETOOTH
**System**: PACKAGE_USAGE_STATS, REQUEST_INSTALL_PACKAGES (default flavor only; removed in lite)
**Notifications**: POST_NOTIFICATIONS, C2DM RECEIVE
**Foreground services**: FOREGROUND_SERVICE_DATA_SYNC (FOREGROUND_SERVICE appears only as the `android:permission` attribute on the DownloadService `<service>` element, not as a `<uses-permission>`)
**Other**: SEND_DOWNLOAD_COMPLETED_INTENTS; REQUEST_WRITE_PERMISSION (not a real Android permission — candidate for removal)

Note: SYSTEM_ALERT_WINDOW is **not** declared (removed at some point; older docs claimed it).

---

**Last Updated**: 2026-08-26
**Version**: 0.67.14
**Maintainer**: Open Learning Exchange
