# Kotlin → Flutter/Dart migration

Tracking document for migrating myPlanet from the **Kotlin/Android** app in `app/` to a
**Flutter/Dart** app in `flutter/`.

## Status

**Phase 99 complete.** The Flutter app is *not* yet a replacement for the Kotlin app:
**all 28 UI packages** have a screen, and a screen existing is not the same as the feature
working. Counted honestly:

- `enterprises` has no screens *of its own* — and it never needed any. Enterprises are a
  team **type**, not a feature: `TeamDetailFragment.buildPages` branches on
  `team?.type == "enterprise"`, while `EnterprisesFinancesFragment` and
  `EnterprisesReportsFragment` carry no type branching at all. They are ported as
  `team_finances_screen.dart` and `team_reports_screen.dart`. Phase 99 audited that mapping
  field by field and retired the "27 of 28" count this line used to support, which was
  counting a Kotlin directory name rather than a missing screen.
- `components`: `CheckboxList` is used by four screens, and `ChallengeDialog` is wired to the
  home screen as of Phase 81. `CustomDropdown` is still built and called from nowhere — library
  code waiting for a caller, not a feature.
- `user`: `BecomeMemberScreen` now POSTs the `_users` document when the server is reachable and
  adopts the server's PBKDF2 material, falling back to the local-only account when it is not —
  the shape Kotlin has.

Known gaps:
- All three WorkManager responsibilities are ported. Timed background sync landed in Phase 38,
  task deadline notifications in Phase 42, and Phase 43 added the durable one-shot resource
  download queue.
- Team attachments are ported as of Phase 39: a receipt image on a finance transaction or a
  report is written under `team_attachments/<docId>/<imageName>`, the document POSTs, then the
  bytes PUT as a CouchDB attachment — best-effort and in that order, the same shape the
  personal-note attachments use, and the sync-in direction downloads them back.
- Universal links are Android-only. The `myplanet://` scheme is registered on iOS, but the
  https Planet hosts need an associated-domains entitlement and an
  `apple-app-site-association` file served by each planet — neither exists, and the shipping
  app is Android-only.
- The dashboard's About and Disclaimer destinations are ported (see Phase 33);
  their translated HTML bodies are rendered as markdown.
- Device/tablet usage telemetry and device identity on activity documents landed in Phase 41.
  Phase 44 carries the same `androidId`/`deviceName`/`customDeviceName` identity onto personal,
  rating, submission and team uploads. Challenge actions (`user_challenge_actions`) remain
  unported because the challenge feature has no screen here.
- Notification destination routing landed in Phase 49. Resource and storage rows open their
  feature screens; task and join-request rows resolve cached team data before navigating.

- **Phase 1** -- skeleton plus the server configuration → login → resources slice.
- **Phase 2** -- dashboard shell (bottom-tab navigation) plus the courses list and detail.
- **Phase 3** -- the first write-back path: shelf upload, so joining or leaving a course reaches
  the server.
- **Phase 4** -- the calendar package and its dashboard destination.
- **Phase 5** -- localized, persisted first-launch onboarding and router gating.
- **Phase 6** -- offline user profile, editable account metadata, and dashboard destination.
- **Phase 7** -- persisted system/light/dark appearance settings and safe server details.
- **Phase 8** -- downloadable, SQLite-backed offline dictionary and search experience.
- **Phase 9** -- reactive notifications, filters, unread badges, read actions, and deletion.
- **Phase 10** -- personalized My life ordering/visibility and the references launcher.
- **Phase 11** -- offline personal-item creation, editing, deletion, and pending-upload tracking.
- **Phase 12** -- offline course ratings, comments, aggregates, edits, and upload tracking.
- **Phase 13** -- durable write-back: an outbox replacing `RetryQueue`/`RetryQueueWorker`, drained
  on app resume, with personal-note upload as its first append-style path.
- **Phase 14** -- offline submission creation, durable upload, list, question-aware answer review, PDF export, and detail metadata, backed by a paginated,
  reactive Drift cache with pull-to-refresh, status filters, progress reporting, and safe stale
  cleanup.
- **Phase 15** -- offline meetups list/detail/create/edit, date/time and recurrence editing,
  search/sort, paginated pull-to-refresh, join/leave shelf write-back, CouchDB mapping, reactive
  Drift persistence, and durable outbox upload.
- **Phase 16** -- offline individual-survey catalog, search/sort and paginated sync, question
  forms for text and single/multiple choice, required-answer validation, and durable submission.
- **Phase 17** -- the voices/discussion feed: community visibility filtering, threaded replies,
  compose/edit/delete, labels, paginated sync, and durable outbox upload.
- **Phase 18** -- teams: the paginated offline team/enterprise catalog, search, membership and
  leader badges, member counts, embedded courses, detail metadata, and safe CouchDB refresh are
  ported. Offline task create/edit/complete/delete and durable CouchDB write-back are also available.
  Join/leave requests, leader accept/decline, the member directory, and durable membership write-back
  are now included. Leaders can also browse, add, and remove team resource links with durable
  CouchDB writes. Leaders can now mutate embedded team courses through the offline course cache with
  durable full-team updates. Enterprise financial reports now support offline create/edit, computed
  totals, archive, and durable writes; attachments and the remaining team pages remain. Team documents
  authored offline now survive both the stale-row cleanup and a schema upgrade, and clear their
  local-edit flag once uploaded.
- **Phase 19** -- chat: the AI-powered chat interface with chat history list, conversation detail,
  search/filter, message input, AI provider selection, and durable local storage via Drift.
- **Phase 20** -- feedback: the user feedback/review system with list view, detail view with replies,
  create feedback dialog, priority/type selection, close feedback action for managers, and
  reactive Drift persistence.
- **Phase 21** -- community: the community/nation tab with voices, leaders, calendar, services tabs.
  Leaders are parsed from the JSON stored in preferences. Services display team links/routes
  from the community. Community/nation info (name, type) is persisted in preferences.
- **Phase 22** -- exam: graded course exams with question navigation, answer types (select,
  selectMultiple, ratingScale, input, textarea), automatic grading, and score display. User
  information collection for team surveys. Exams and ExamQuestions tables with Drift persistence.
  The exam documents are pulled by the existing `exams`-database sync (which already downloaded
  and discarded them -- surveys and exams share that CouchDB database and are told apart by
  `type`), reached from a course step's *Take exam* action, and each attempt is written as a
  graded `exam` submission that the outbox uploads. `UserInformationScreen` attaches its profile
  to that submission via `markSubmissionComplete`.
- **Phase 23** -- viewer: the shared resource viewer for video, audio, PDF, image, markdown, text
  and CSV, reached by tapping a resource. Backed by a new foreground download path
  (`ResourceDownloader` + `ResourceFiles`) -- the port synced resource *metadata* only, so before
  this every resource reported itself as not downloaded and there was no way to get the file.
  `DownloadWorker`'s background queue was still unported at this point; it landed in Phase 43.
- **Phase 24** -- health: My health, the health profile form and the examination form, reached
  from My life. Three things had to be added around the screens for them to be safe. The
  examination `data` blob is AES-256-CBC encrypted with the user's key exactly as
  `AndroidDecrypter` does it (`HealthCipher`) -- the port was about to write diagnoses and
  medications to SQLite and to CouchDB in plaintext, and could not have read anything Kotlin
  wrote. `users.key`/`users.iv` carry that key; both `users` and `health_examinations` joined
  the preserved set, since the key is generated on-device and a sync cannot give it back.
  `HealthUploader` gives the records a way off the handset, and `HealthRepository.sync` got its
  first caller.

- **Phase 25** — offline maps, storage management, become-a-member, shared components, and the
  ratings upload path. Notes on what each of those does and does not do:
  - **maps**: `OfflineMapsScreen` on `flutter_map` with the same OpenStreetMap/Mapnik tile
    source `OfflineMapsActivity` uses — so, like the Kotlin, "offline" means cached tiles, not a
    bundled archive. Reached from the references tile that until now said "not ported yet".
  - **storage**: `StorageBreakdownScreen` and `StorageCategoryDetailScreen`, reached from
    settings; per-category file sizes and deletion.
  - **user**: `BecomeMemberScreen`, reached from login. Only the offline half is ported — see
    Status; the account never reaches the server.
  - **ratings**: `RatingsUploader` arrived with no caller on either side. Registered on the
    outbox drain and queued from `RatingActions.submit`; its payload gained the `user` object
    Kotlin sends, without which Planet cannot attribute a rating to its author; and a response
    carrying no `_rev` is now an error rather than a success, which had been retiring the outbox
    entry while the row stayed pending — the next queue posted a duplicate document.
  - **components**: `CheckboxList` is used by four screens; `ChallengeDialog` gained its caller
    in Phase 81. `CustomDropdown` is still called from nowhere.

- **Phase 26** — chat and feedback sync-in, team plan/finances/calendar/voices, take-course and
  course progress, and survey sending. The two syncs arrived with no caller on either side, as
  the uploaders before them had; both are now reachable from their screens. The chat sync needed
  two guards before it was safe to run at all: it called `deleteNotIn` on a table with no
  uploader — so every conversation the server had not confirmed would have been destroyed, and
  an empty keep list emptied the table outright — and its upsert overwrote `conversations`
  wholesale, discarding a question whose answer had not yet arrived. Feedback's `deleteNotIn`
  already spared un-uploaded rows and needed no change.


- **Phase 28** — team and public survey sharing, personal-note attachments, and an immediate
  chat retry. Sending a survey uses the same get-or-create semantics as Kotlin's
  `createBulkSurveySubmissions` instead of inserting duplicate rows from the dialog; shareable
  surveys can be adopted into a team with their questions copied and an adoption submission
  queued; team-owned/adoptable/individual filters follow `SurveysRepositoryImpl`; and
  `PublicSurveyScreen` answers a `publicAccess` survey with no session at all. A chat POST now
  retries three times at two-second intervals before falling back to the outbox, which is the
  gap Phase 27 left open. Three defects were fixed on the way in:
  - The public-survey route read `state.uri.origin` for its base URL. `Uri.origin` **throws**
    `StateError` on a URI without a scheme and host rather than returning an empty string, and
    a go_router location is relative whenever the platform did not hand over an absolute one —
    so the route crashed the moment it was reached. `publicSurveyBaseUrl` now mirrors Kotlin's
    `"${uri.scheme}://${uri.encodedAuthority}"` when there is an origin and falls back to the
    configured server when there is not.
  - The deep-link intent filter matched `<data android:host="*"/>` over both `http` and
    `https`, registering the app as a candidate handler for every `/survey…` URL on the
    internet and letting any of them choose the base URL the screen fetches from. It is now
    scoped to the same eight `autoVerify` Planet hosts the Kotlin manifest declares.
  - Submissions uploaded `parent` and `user` as JSON *strings*. Kotlin sends both as nested
    objects, so Planet could resolve neither `parent._id` nor `user.name` on anything this app
    posted. This predates the phase but survey adoption is built entirely around the user
    document it carries, which is what surfaced it.

- **Phase 29** — the home ("bell") dashboard: a `/home` shell tab, first in the bottom nav and
  the post-login landing route, porting `BellDashboardFragment`'s content — the profile card
  (name, role, planet code), the four myLibrary/myCourses/myTeams/myLife cards with their count
  badges (hidden at zero), alternating fixed-size tiles in a horizontally scrolling grid,
  card-header navigation (myCourses sets the existing my-courses filter), the Kotlin's exact
  guest gates (library/courses headers and five of the seven myLife tiles gated; teams and life
  headers, References and Calendar open), the localized empty-state placeholders, and the
  pending-survey dialog with its one-hour throttle, per-survey dedupe, and dropped-survey
  filtering. Empty-status adoption records are deliberately excluded: survey assignments are
  created with Kotlin's explicit `pending` status, while an adoption records the act of copying
  a survey and is not an answer sheet for the adopter. The myLife feature registry moved to
  `ui/life/life_features.dart`, shared with
  `LifeScreen`; `DialogUtils.guestDialog` became the shared `ui/components/guest_dialog.dart`.
  Still unported after Phase 30 (each needs infrastructure the port lacks): the completed-course
  star row (per-step progress records), the avatar's network-status ring (connectivity plugin), team
  chat/task alert badges (team notifications), the offline-logins count and activity-chart FAB
  (login activity tracking), the navigation drawer and overflow menu, and
  the "remind later" reminder scheduler. **Corrected (Phase 66 audit):** the star row, ring,
  badges, offline-logins count, activity chart, and remind-later scheduler all landed in
  Phase 33; the navigation drawer and overflow menu remain a deliberate divergence
  (bottom-tab navigation).

- **Phase 30** — successful foreground syncs now persist Kotlin's `LastSync` timestamp and
  publish it through reactive Riverpod state. The home dashboard displays the missing last-sync
  strip, including localized never/just-now/minutes/hours/days labels, updates immediately after
  a successful sync, and preserves it across launches. Failed syncs deliberately do not advance
  the timestamp.

- **Phase 31** — dashboard navigation parity: the home app bar now exposes AI chat directly and
  restores the Kotlin overflow actions for feedback, settings, theme cycling, and logout. A
  profile-aware navigation drawer makes resources, courses, teams, calendar, My life, community,
  chat, feedback, references, and settings directly reachable. Chat and feedback keep their guest
  gates (the Kotlin toolbar gates both); the Library and Courses entries are deliberately *open*
  to guests, as the Kotlin drawer's `menu_library`/`menu_courses` are — browsing is what a guest
  account is for, and only the *my*-filtered variants are members-only. The theme action persists
  through the shared `ThemeModeNotifier`, rather than applying a one-screen-only color change.

- **Phase 32** — foreground sync orchestration: a dashboard Sync center runs resources, courses,
  teams, events, surveys, voices, feedback, chat, and health sequentially, surfaces per-area
  waiting/running/success/failure state, reports aggregate progress and saved counts, and permits
  an isolated retry after failure. It is reachable from both the drawer and Kotlin-style overflow
  "Sync now" action. Sequential execution deliberately avoids nine simultaneous CouchDB page
  walks on resource-constrained community devices; durable outbox writes remain separate and are
  still drained by `OutboxDrainScope`. This is foreground orchestration, not a claim that the
  missing WorkManager-equivalent scheduling gap is solved.

- **Phase 27** — chat upload, member registration against `_users`, and the resource detail
  screen with its filter sheet. Three fixes were needed around it:
  - `chat_history` gained an `is_uploaded` column with no `schemaVersion` bump and no
    hand-written migration step. It is a *preserved* table, so `onUpgrade` steps over it and
    `createAll` emits `CREATE TABLE IF NOT EXISTS` — the column would never have appeared on an
    existing install, and every chat query would have failed on it. Preserved tables need an
    explicit `addColumn`; there is now a helper and a test that pins it.
  - Nothing set `is_uploaded`, so every conversation — including ones the sync had just pulled —
    read as pending and would have been posted again as a duplicate. Server-origin rows are
    marked uploaded at the mapper, and the migration backfills by `_rev`.
  - `ChatUploader` was registered on the drain with nothing queuing for it, because a failed
    send dropped the message rather than storing it. `savePendingChat` keeps it and queues it.
  Also: the chat provider called `sendNewChatRequest` on both branches, so every follow-up
  message opened a fresh conversation instead of continuing the one on screen.

## Phase 33 — finishing the home dashboard

Phase 32 left the bell dashboard with its profile card, four count cards and the pending-survey
dialog. The rest of `BellDashboardFragment` and `BaseDashboardFragment` is now ported, along with
`ActivitiesFragment` and the language action from the overflow menu.

**Completed-course stars.** `progressRepository.completedCourseIds` and `isCourseCertified`
already existed from the Phase 32 harvest with only the courses screen reading them; the star row
gives them their dashboard caller. A star per completed course, tinted `colorPrimary` when a
certification covers the course and `md_blue_grey_300` when not, tapping through to the course.
The Kotlin hides its spinner after a two-second timer so an empty result does not spin forever;
Riverpod's `loading` state says the same thing without the timer.

**Team alert badges.** Needed two things the port did not have: a `team_notification` table (the
per-team "seen" chat watermark) and a count of team-visible posts. Two Kotlin quirks are
reproduced rather than fixed, and both are worth knowing:

- `hasTask` is computed **once** for the whole team list — the user's tasks due between now and
  this time tomorrow — and written onto every team. A task in one team lights the dot on all of
  them. The window also starts at *now*, so an already-overdue task lights nothing.
- `hasChat` requires a watermark row to exist (`notification != null && lastCount < chatCount`),
  so a team whose voices the user has never opened shows no dot however many posts it has. The
  dot means "new since you last looked", and never-looked reads as nothing new.

`updateTeamNotification` moves the watermark, keyed `<teamId>:chat` rather than a fresh UUID —
the Kotlin mints a UUID but always looks the row up by `(parentId, type)` first, so a derived key
is the same row with one fewer way to duplicate it.

**Offline logins and the activity chart.** A new `offline_activity` table, written by
`SessionNotifier` on sign-in (`UserSessionManager.onLoginAsync`) and stamped with a logout time on
sign-out. It feeds the `(n)` in the name line — `R.string.user_name` is `"%1$s (%2$s)"`, which the
port had been rendering without its count — and `ActivitiesScreen`'s bar chart.

The table is **preserved** across a schema bump: Kotlin carries these rows to the server through
`UploadManager`'s `activities` upload, the port has no activities uploader yet, so they exist
nowhere else and no sync can give them back. `logLogout` reproduces a real Kotlin quirk — it
stamps the newest `login` row *globally*, ignoring the user name it is handed, so on a shared
handset the logout lands on whichever login is newest.

The chart is drawn with plain widgets rather than a charting dependency: the data is at most
twelve integers and the Kotlin's chart is a bar chart with month labels. `monthlyLoginCounts`
keeps `computeMonthlyCounts`' bucketing exactly, including grouping on `Calendar.MONTH` with no
year component.

**The network-status ring.** The colour around the avatar, probed against the configured server
(primary, then the mapped alternative) as `MainApplication.isServerReachable` does — success being
any 2xx. Two deliberate differences, both from having no connectivity plugin, which did not earn
its keep for one ring:

- No connectivity trigger. The Kotlin re-probes on OS connectivity changes; this probes when the
  dashboard builds and on demand, and deliberately does **not** poll on a timer.
- The three states are inferred from the failure kind rather than the radio: a transport failure
  (socket error, timeout) reads red, a response that arrived but was not 2xx reads yellow. That
  lands on the Kotlin's colour in the ordinary cases — no network fails at the transport, and a
  reachable-but-sick server answers with a status.

**Remind-later.** The survey dialog's neutral button, and the reminders it schedules. Stored as
`reminder_time_<ids>` preference keys exactly as `SurveysRepositoryImpl` stores them, with the ids
being the comma-joined submission list so a reminder re-opens the same set. `checkPendingSurveys`
now also consults `isReminderScheduled`, so the hourly dialog cannot undo a snooze — that guard
existed in the Kotlin and was missing from the port. The Kotlin's polling flow
(`while (true) { … delay(60_000) }`) became a `Timer.periodic` cancelled in `onDispose`: an
un-cancellable `Future.delayed` outlives the provider, keeps polling after the dashboard is gone,
and leaves a pending timer that fails every widget test that mounted the screen.

The Kotlin's `NumberPicker` + `RadioGroup` became a slider and a segmented button, keeping the
per-unit caps (60 minutes / 24 hours / 30 days) and the clamp when a unit change lowers the cap.

**The language action.** `R.id.change_language` → `SettingsActivity.languageChanger`: the six
supported languages, stored under the Kotlin's own `language` preference key and applied through
`MaterialApp.locale`. Unset means "follow the device", which is what the app did before. The
labels were already translated upstream and are seeded from `values-es` as usual.

**About and Disclaimer.** `R.id.action_about` / `R.id.action_disclaimer` →
`AboutFragment` / `DisclaimerFragment`. Static HTML bodies in `strings.xml`,
rendered as markdown through the port's `flutter_markdown_plus` (the same
renderer the challenge dialog and resource viewer use) rather than porting an
HTML widget. The Kotlin's HTML maps cleanly to markdown (`<h1>`→`#`,
`<ul><li>`→`-`, `<strong>`→`**`, `<a href>`→`[text](url)`), so the content is
authored once as markdown in `app_en.arb` / `app_es.arb` — no new rendering
dependency, just `url_launcher` to stand in for `LinkMovementMethod` on the
disclaimer's contact link.

`AboutFragment` injects the app version after the `<h3>MyPlanet</h3>` heading
(`about` → `<h3>MyPlanet</h3>\n<h4>Version …</h4>`); the port does the same
by appending `\n\n#### $versionLine` to `aboutContent`. The Kotlin reads
`BuildConfig.VERSION_NAME`; the port has no `package_info_plus` dependency, so
`ConfigurationsRepository.defaultAppVersion` — the same constant the
configuration flow uses — stands in. Both bodies are seeded into Spanish from
`values-es`; the other four locales fall back to English until somebody fills
them, the same as every other key.

OS-scheduled background sync remains out of scope for the same reason it
always has.

## Phase 34 — the activity log, and something that leaves it

`offline_activity` arrived in Phase 33 with a writer (`SessionNotifier`) and two readers (the
dashboard count, the activity chart) and no way off the device. It was the last table in the port
in that shape, and the reason it was on the preserved list. The rest of
`ActivitiesRepositoryImpl` is now ported with it.

**What is logged.** Three kinds, all of which the Kotlin records and none of which the port did:

- **Resource opens and downloads** (`resource_activity`), from the viewer —
  `ResourcesRepositoryImpl.trackResourceOpen` for `visit`, `BaseContainerFragment` for
  `download`. The Kotlin logs a download when the fetch *starts*; the port logs it on success, so
  a failed download is not reported to the server as one that happened.
- **Course visits** (`course_activity`), once per take-course open. `TakeCourseFragment` logs from
  `setData`, which a rebuild can run again; a mount-scoped flag is what "a visit" means.
- **Completed syncs** (`resource_activity`, type `sync`), from the dashboard Sync center. Kotlin
  records one row per `SyncManager` run, and the port's equivalent of a run is the whole
  sequential pass rather than one table pull. Recorded only when at least one area succeeded:
  the Kotlin records unconditionally at the end of a sync that aborts on failure, so an
  all-failed pass has no counterpart to be faithful to.

**What leaves.** `ActivitiesUploader` registers four outbox handlers, one per destination
database — `login_activities`, `resource_activities`, `admin_activities` (the `sync` rows) and
`course_activities`. They are separate `uploadType`s rather than one type with a variable
endpoint because the handler has to know which table to mark, and a `sync` row and a `visit` row
share a table while going to different databases. `pendingUploads`/`pendingSyncUploads` partition
that table on the same predicate the Kotlin's two configs differ by, so no row is posted twice.

**What the profile now shows.** `UserProfileViewModel`'s stats had no port: last login (global,
with no user predicate — that is the Kotlin's), total visits, most-opened resource, and the
resource-open count. `getMostOpenedResource`'s title filter is reproduced, so an untitled resource
cannot win the row. The Kotlin renders every row with `Utilities.checkNA`; here an absent value
drops its row and an empty log drops the whole card, rather than showing a column of zeroes on a
fresh install. `TimeUtils.getRelativeTime` became the shared `relativeTimeLabel`, which the
dashboard's last-sync strip already had a private copy of.

Three things worth knowing about the shape of the Kotlin here, two of them deliberate
divergences:

- **`changeRev` reads the wrong keys.** It pulls `_id`/`_rev` out of a CouchDB insert response,
  which carries `id`/`rev`; through `JsonUtils.getString` those misses become `""`. The row does
  drop out of `getPendingLoginUploads` (`""` is not null, so it is not re-posted) but the document
  can never be updated again. The port records the real values and treats a response without them
  as an error, as every other uploader here does.
- **`serializeLoginActivities` writes the logout timestamp as `_id`.** `ob.addProperty("_id",
  activity.logoutTime)`. It is unreachable — the rows it serializes are exactly those with a null
  `_rev`, and nothing sets `_id` without also setting `_rev` — so the branch is omitted rather
  than reproduced. Reproducing a nonsense document id on the chance it is reached would corrupt
  the document.
- **The guest exclusion is real and load-bearing.** `getUnuploadedLoginActivities` drops rows with
  a null or `guest`-prefixed `userId`, because a guest has no CouchDB user document for the server
  to attribute the session to. Expressed in SQL here, as `CourseProgressDao.getPendingUploads`
  already does, and repeated at the write site so no caller can log an unattributable row.

`resource_activity` and `course_activity` join the preserved set. The test is the one this doc
already states — *can a sync restore this?* — and the answer is no in both directions: a pending
row exists nowhere else, and `resource_activities`, `admin_activities` and `course_activities` are
write-only from this app's side.

One defect was fixed on the way in rather than shipped: the row-id minter derived its key from
`DateTime.now().microsecondsSinceEpoch` alone, and two opens minted in the same microsecond
collide on a primary key — so the second `insertOnConflictUpdate` silently overwrote the first
open instead of recording a second one. That is the same defect the chat and feedback
`_generateId` helpers shipped with; a monotonic counter is now part of the key, and the
most-opened-resource test pins it by logging the same resource twice in a row.

## Phase 35 — deep links, and the last write path that could lose data

Two gaps that were listed separately turned out to be the same slice: a public-survey link
could not deliver the one thing the screen needs, and the answers it collected had nowhere to go
if the post failed.

**Deep links now arrive whole.** `app_links` replaces reading `Intent.getData()`, with
`core/deeplinks/deep_link.dart` (pure Dart, so the URI rules are testable without a platform
channel) porting `handleDeepLinkIntent` and `maybeLaunchPublicSurvey`, and `DeepLinkScope`
delivering the launch link and anything that arrives afterwards — the Kotlin's
`onCreate`/`onNewIntent` pair, which here is a future and a stream from one source.

The origin travels to the route as an `origin` query parameter, because it is the only part of
the link the route cannot recover for itself: `publicSurveyBaseUrl` now reads that first, the
location's own origin second, and the configured server last. That is what fixes the broken
case — a respondent who followed a link and has never configured a server could not load the
survey at all.

Three behaviours came with it, all the Kotlin's:

- **A section link survives the login it triggers.** `myplanet://courses/<id>` and
  `https://host/app/courses/<id>` are stored under the Kotlin's own
  `pending_deep_link_section`/`pending_deep_link_id` keys when there is no session, and applied
  once one appears — `DashboardActivity` reads them in `openFragmentFromIntent`, clearing them
  on read so the link does not reopen on every later launch. The section set is exactly the
  Kotlin's five (`feedbackList`, `courses`, `resources`, `teams`, `surveys`).
- **A survey link opened by a member is not the public screen.** `maybeLaunchPublicSurvey` bails
  when logged in, and the section branch then maps the *third* path segment — the survey id, not
  the team's — onto the surveys section.
- **A path with no `app` segment still resolves.** `segments.indexOf("app")` is -1, so
  `appIndex + 1` is 0 and the Kotlin reads the *first* segment as the section. Reproduced: the
  section names are a closed set, so an unrelated path resolves to a section nothing maps and is
  dropped a layer up. Narrowing it would change which links work.

The manifest gains what the Kotlin's has and the port's lacked — the `myplanet` scheme, the
`/app/` path prefix, and the cleartext filter for local community servers — and declares
`flutter_deeplinking_enabled=false` so the engine's own routing does not race the plugin with a
host-less location.

**The public-survey answer sheet is durable.** It was the last write in the port that could be
lost: `PublicSurveyActivity` POSTs to the public API and a failed post takes the answers with
it. `PublicSurveyUploader` follows `ChatUploader`'s shape — a live attempt first, so the common
case still ends on "thank you for taking this survey", and the outbox when that attempt fails,
with the screen reporting "saved offline" instead of "could not save your answers".

Two properties make this upload type unlike every other one, and both needed work elsewhere:

- **It carries no credentials**, being the public API, so the handler ignores the drain's
  `authHeader` rather than attaching a Planet Basic credential to an anonymous request.
- **It has to drain with no server configured.** `OutboxDrainScope`'s "nothing to send to before
  the handshake" guard would otherwise hold this row forever, since a respondent who followed a
  link never configures a server. `OutboxDrainer.drain` takes an `onlyTypes` filter and the
  scope drains this type alone in that case — draining *everything* unauthenticated would earn
  401s, which the retry rule classifies as permanent and would abandon writes that are
  perfectly deliverable later.

`markPublicSubmitted` records the delivery without a revision, because the public endpoint is
not a CouchDB insert and its response carries no document handle. Clearing `isUpdated` is the
part that matters: without it the same answer sheet could be queued twice, and a respondent who
tapped submit twice would file two.

The mirror fallback (`ServerUrlMapper`'s alternative URL) stays on the live attempt, which tries
both before anything is queued. A queued row keeps the primary endpoint — the respondent reached
this app through a link to *that* host, and quietly posting their answers to a different one is
not a retry.

Two defects were fixed on the way in, both in the new code and both invisible to `flutter
analyze`:

- **`GoRouter.of(context)` cannot be used from `MaterialApp.router`'s `builder`.** That is where
  `DeepLinkScope` is mounted — deliberately, so it follows the app rather than a screen — and the
  builder's context sits *above* the `InheritedGoRouter` the router delegate inserts. Every
  incoming link would have thrown "No GoRouter found in context" on arrival. Navigation goes
  through `routerProvider` instead, and `deep_link_scope_test.dart` mounts the scope in exactly
  that position so the next person cannot reintroduce it.
- **An empty `queryParameters` map still renders a trailing `?`**, so a link with no origin
  produced `/survey/t/s?` where the same route reached in-app is `/survey/t/s`. Harmless to
  go_router's matching, and exactly the kind of difference that makes two locations look
  unrelated in a log.

## Phase 36 — harvesting `flutter-openhands4`

The `flutter-openhands4` branch forked from `381618dc3` (in this branch's history) and carried six
commits. Two of them reimplemented work this branch had already done independently — the
completed-course star row and the login-activity chart, both Phase 33 here — so this was a
selective harvest rather than a merge. Two things were genuinely new.

**Profile photos.** Taken almost verbatim, because the shape is right and it closes the `user`
package's largest remaining gap. A `_users` document stores the photo as a CouchDB *attachment*
under `_attachments`, not as a top-level field, so `UserEntity.addImageUrl` reads the first
attachment key as the image name; `UserMapper.fromDoc` now derives `users.userImage` from that key
and ignores any stray top-level `userImage`. Only the attachment *name* is persisted, so the row
carries no credentials and survives a server URL change. `profileImageProvider` rebuilds the URL
against the current config and fetches the bytes through the authenticated `PlanetApi.getBytes`
path — `Image.network` cannot reach a blob behind Basic auth — and `ProfileAvatar` falls back to
initials while loading, when there is no attachment, and on error, matching Kotlin's
`R.drawable.profile`. A guest renders synchronously with no network attempt.

One resolution was not mechanical: the openhands branch replaced the home card's avatar outright,
which would have **dropped the server-reachability ring** ported in Phase 33. The photo belongs
*inside* the ring — the Kotlin draws both — so `_NetworkRingAvatar` now takes the user and renders
`ProfileAvatar` within its border.

**The `login_activities` sync-in.** The direction this port lacked: Phase 33 wrote login rows and
Phase 34 uploaded them, but nothing pulled back the ones other devices had already sent, so a
member's history was whatever *this* handset happened to observe. `OfflineActivityMapper` and
`insertLoginActivitiesFromSync` port the Kotlin's two-stage match — by `_id`, falling back to the
`(loginTime, userName)` pair — so a row this device authored offline and then uploaded is *adopted*
rather than twinned when its own document comes back. Deliberately no `deleteNotIn`: the table is
preserved and holds rows the server has never seen, so pruning against a synced id set would delete
exactly those. The Kotlin does not prune either.

Two details the merge depends on, both easy to get wrong:

- **The companion must carry the local `description` and `userId`.** Neither is on the
  `login_activities` document, and `insertOnConflictUpdate` writes every column the companion
  carries — so passing them as absent would blank the `userId` that `offlineVisitCount` (the
  profile's "Total visits") keys on. Pinned by a test.
- **The pull needed a caller.** A sync with no caller is the failure this port has shipped three
  times, so it is registered as a tenth Sync center area rather than left as library code. The
  sync-center test's hard-coded "All 9 areas" assertion became
  `DashboardSyncArea.values.length` — a hard-coded count fails on the next area added and says
  nothing about the summary being correct.

Not harvested: their star row and chart (ours predate them and are wired to the ring and the
`(n)` login count), and their version bump. Their `analysis_options.yaml` exclude for `build/` and
the platform directories is taken — analysing generated output is noise.

Recorded from their notes rather than harvested: **`package:intl` exports its own `TextDirection`**
(`.LTR`/`.RTL`) which shadows `dart:ui`'s (`.ltr`/`.rtl`), so a file combining `DateFormat` with a
`TextPainter` needs `import 'package:intl/intl.dart' hide TextDirection;`. Their chart is a
`CustomPainter`; ours is plain widgets, so nothing here trips it today.

## Phase 38 — background sync, harvested from a branch that could not be merged

`codex/increase-line-count-by-threefold` is **not** a descendant of this branch. It forks from an
older lineage that never received Phases 34–36: no `deep_link.dart`, no
`activities_uploader.dart`, no `public_survey_uploader.dart`, no `user_uploader.dart`, no
`profile_avatar.dart`. Merging it would have deleted roughly 18,000 lines of work. One commit on
it, `8f6e031ba`, is worth having on its own and was cherry-picked; the rest was left alone.

**What it adds.** The item this document has called the highest-risk gap since Phase 1: timed
execution with no user present. `workmanager` sits behind `BackgroundScheduler`, an abstract
interface, so scheduling *policy* — should this run, is the interval due, which steps failed — is
ordinary unit-tested Dart in `BackgroundTaskRunner`, while the plugin call is a four-line adapter.
`background_entrypoint.dart` is the headless isolate, settings gains an auto-sync toggle and an
interval control, and `main.dart` applies the schedule at startup.

This does **not** make the port Kotlin-free, and the claim that the gap could close that way should
be retired: `workmanager`'s Android side is itself Kotlin, wrapping the same `WorkManager` the
Kotlin app registers directly. What changed is that the Kotlin is now a maintained third-party
plugin rather than app code.

**Two changes the two-isolate world made necessary**, both in the outbox and both correct:

- `OutboxDao.claim` replaces the unconditional `setStatus`. The UI isolate and the WorkManager
  isolate have separate in-memory single-flight guards, so the guard that matters is a conditional
  SQL update: both may select the same due row, only one `status = pending` update wins, and the
  loser skips the row instead of double-posting it.
- `recoverStuck` takes a cutoff. Recovering *every* `in_progress` row at startup was right with one
  isolate; with two it would steal a row the background isolate has in flight.

**Three resolutions where the old lineage disagreed with this branch:**

- **`lastSync` stays epoch millis.** The commit redefines it as `DateTime?`. Ours matches
  `SharedPrefManager.LAST_SYNC` and is read by `LastSyncNotifier` and the dashboard's last-sync
  strip, so the runner's `DateTime` API is bridged in `background_entrypoint.dart` instead —
  neither side changes, and the adapter is four lines with a comment saying why.
- **`OutboxDrainer.drain` keeps the `onlyTypes` filter** from Phase 35 (the public-survey
  respondent who has no credential) *and* takes the commit's nullable-outcome claim logic. The
  conflict presented them as alternatives; they are orthogonal.
- The prefs test keeps this branch's `lastSync` coverage and adopts the commit's background
  preference and diagnostics tests, with the `DateTime` assertions rewritten for the int API.

Also fixed here: 16 raw CP1252 `0x97` bytes in this document, left by the Phase 37 docs commits,
which made it invalid UTF-8 and broke any tool that reads it as such.

Still unported from `WorkManager` at the time of this phase: `TaskNotificationWorker`'s deadline
notifications (since ported — Phase 42) and `DownloadWorker`'s background queue.

## Phase 39 — harvesting `flutter-openhands7`

A clean fast-forward: ten commits, a strict descendant of the previous head, nothing of this
branch's history missing and no file deleted. Unlike the branch behind Phase 38 this one needed
no cherry-picking. It closed four items this document had been carrying as open:

- **About and Disclaimer** (`426ca043f`) — described in the Phase 33 section above, since that is
  where the rest of the dashboard's overflow actions live. The one new dependency is
  `url_launcher`, standing in for `LinkMovementMethod` on the disclaimer's contact link.
- **Team finance/report receipt attachments** (`7479086a3`) — `TeamAttachments` writes the image
  under `team_attachments/<docId>/<imageName>`; `TeamsUploader` POSTs the document and then PUTs
  the bytes, and the sync-in direction downloads them back into the same slot the preview reads.
  Both directions exist, so this is a feature rather than a screen.
- **Free-up-space storage management** (`fe4c1c0ea`) — `ResourcesRepository.freeUpSpace` ports
  `FreeSpaceWorker.doWork`, and a `DiskStats` seam over a new `disk_stats` method channel supplies
  the available/total figures. The method channel is answered by the Flutter module's own
  `MainActivity.kt`: there is no pure-Dart device-free-space API, and this is the platform layer,
  the same category as the `workmanager` plugin's Kotlin side.
- **Debounced username validation** (`349ab84e0`) — detailed in the spec-debt list above.

The schema went 30 → 31 for `teams.imageName`. `teams` is a preserved table, so the column is
added by `_addColumnIfMissing` under a `from < 31` guard rather than by `createAll` — the trap
this document records under "The preserved-table test", handled correctly by the branch.

Two defects were fixed on top of the merge rather than left for a later reader:

- Two more raw CP1252 `0x97` bytes in this document, from the new commits, in the same place the
  Phase 38 round repaired 16 of them. The file was invalid UTF-8 again. Whatever authoring path
  produces these has now done it twice, so it is worth expecting a third time.
- Two claims the branch's own work falsified but did not update: "Team attachments are unported"
  in Status, and "debounced username validation is still validate-on-submit" in the remaining-
  packages list. Both now contradicted the same document's spec-debt entries.

The CI action bumps in the same range (`3ce7e16b2`) looked wrong on sight — `actions/checkout@v7`
and `upload-artifact@v7` are well ahead of the versions these notes were written against. They are
correct: `build.yml`, `release.yml`, and `automerge.yml` were already on v7 before this branch, so
the commit brought `flutter.yml` and `test.yml` in line with the repo's own convention.

## Phase 40 — voices share-to-community, and upstream voices parity

The 2026-08-19 harvest audit (below) found one behavioural commit in the 43 upstream since
`f53e466`: `f4adebf` ("community: smoother voices showing"). It changed four things in
`VoicesRepositoryImpl`, and the port took all of them, since its voices slice was ported against
the older semantics:

- **Community visibility needs a community entry.** `isVisibleToUser` used to match *any* `viewIn`
  entry whose `_id` named the viewer, so a team entry leaked the post into the community feed for
  exactly that user. It now requires `section == "community"` and accepts empty/`"@"` ids and
  viewers as planet-wide wildcards. The port's `isVisibleToUser` did the old matching and its test
  pinned it — the test has been rewritten, with the new wildcard and "teams stays invisible" rules
  pinned alongside.
- **The feed orders by share date.** `getCommunityNews` gained
  `.sortedByDescending { it.sortDate }`. The port's provider was already re-sorting locally; the
  sort now lives in `watchCommunityFeed` itself, so the repository's contract matches
  `getCommunityNews` and the provider just filters the search query.
- **Sharing to the community is hardened.** `shareNewsToCommunity` no longer throws on a malformed
  existing `viewIn` (it shares against an empty array instead of failing), fills the first entry's
  `name` only when missing and non-empty, and never emits a bare `"@"` community id when both codes
  are empty. The port did not have share-to-community at all — it is here now as
  `shareToCommunity`, with the `VoiceCard`'s share button on both the feed and the team-voices
  screens, gated like `VoicesAdapter.canShare` (logged-in, non-guest, not already community).
- **Deleting is actually un-sharing, when possible.** `deletePost(newsId, teamName)` from a team
  screen still removes the thread; from the community feed it strips the community (and any other
  shared-in) entries from `viewIn`, clears `sharedBy`, and keeps the row — unless the original
  `viewIn` had one entry or the filter empties it, in which case the thread dies. The port had
  delete-whole-thread on every screen; `teamName = ''` on the feed and the thread, the team name on
  team-voices, decided at the `VoiceCard`. Kotlin's own tests for this are mirrored in the port's.

Two deliberate divergences, both downstream of an earlier one. The send and un-share paths mark
the row `isEdited`, which Kotlin never needs to do — its `getNewsForUpload` re-PUTs every post on
every sync, where this port deliberately queues only new-or-edited rows (see `pendingUploads`'s
comment). Without the flag the share would sit locally forever. And the Kotlin adapter's
moderator/shared-by widenings on edit/delete stay unported — the port gates on authorship, the
subset it can support without its label-manager/admin/leader plumbing.

The thread screen's author-gated app-bar edit/delete moved onto the card itself, matching
`row_news.xml`'s per-row buttons, so team voices and the feed both get them through `VoiceCard`.

Still unported on voices: the "Shared from X" date suffix the Kotlin adapter derives from its
team-name lookups, and anything the moderator gates protect. **Corrected (Phase 66 audit):** the
suffix landed in Phase 53 (`JsonUtils.extractSharedTeamName`, appended on the community feed);
only the moderator-gate widenings remain.

## Harvesting upstream: the 2026-08-12 rebase

Rebased onto master again (8 new Kotlin commits, clean replay). Five changed behavior; four
were harvested, one (notification group expand/collapse defaults) lands on a grouping model
the port has not built:

- **The home-card placeholders became invitations** (`48fbf109d`): "You can add resources" /
  "You can join courses" / "You can join a team", in every locale — the ARB texts follow.
- **A reply keys on its parent's local id** (`5f3198970`): `postReply` was `news._id ?: news.id`
  and is now `news.id`. The port follows; the delete walk still probes both keys for rows
  written under the old rule.
- **No attendance write without a signed-in user** (`2a49db978`): the old guard let a joined
  meetup be *left* with no current user, writing `userId = ''`. Toggling is now a no-op in both
  directions without a session.
- **The finances "to" date picker falls back to the "from" date** (`437a3d28a`) before today.

The same pass adopted the `flutter-openhands2` branch (course progress + certification data
layer, the progress filter/sort on the courses screen) with one correction: its completion rule
counted passed *rows*; the Kotlin counts unique passed `stepNum`s (`toSet()`, "matches web"),
and sync can deliver several rows per step — one per device — so row-counting would let a
twice-passed step 1 complete a two-step course.

## Harvesting upstream: the 2026-08-11 rebase

The branch was rebased onto master (`6977707ef`), 130 Kotlin commits ahead of the previous
base. Since the Kotlin app is the port's specification, every one of those commits was
reviewed for *behavioral* change — refactors, test additions, DI moves and formatter caching
need nothing from the port. Four changes were harvested:

- **Feedback sync no longer destroys a pending local reply** (`af4605362`). The port
  replicated the old `mapToFeedback`: a sync overwrote `messages` from the server document and
  flipped `isUploaded` back to true, so a reply composed offline was silently lost the moment
  the next sync ran. `FeedbackMapper.fromDoc` now takes the stored row and, when it has an
  unconfirmed reply, keeps the local thread and stays pending — exactly the Kotlin fix.
- **Removing a resource from the shelf now writes a `removed_log` row** (`4345d871a`).
  `ShelfRepository` already *read* `removed_log` rows of type `resources`, but nothing ever
  wrote one — `ResourceDetailScreen` cleared the userId directly on the row, so the next shelf
  push re-merged the resource from the server copy and the removal did not stick. The same bug
  existed in Kotlin and was fixed there; `ResourcesRepository.setShelfMembership` now pairs the
  membership write with the removal record (and clears it on re-add), mirroring the courses path.
- **`CourseMapper.mergeUserIds` drops blank entries** (`ff7bb5c7c`). The Kotlin's shelf-merge
  now filters blank/duplicate user ids; the port deduped through a `Set` already but let a
  blank string persist forever.
- **Storage management is guest-gated** (`82140152b`). A guest tapping the settings tile is
  offered membership (`DialogUtils.guestDialog` → an `AlertDialog` pushing `Routes.becomeMember`)
  instead of the storage tools.

Recorded as spec debt rather than harvested (the Kotlin change lands on something the port has
not built, or needs a primitive the port lacks):

- The rest of `82140152b` is **now ported** (was spec debt): the free-up-space button and
  available-space text inside the breakdown sheet. `ResourcesRepository.freeUpSpace` walks the
  `ole/` tree, deletes every resource-id directory, and clears the offline flag on the matching
  library rows (the `FreeSpaceWorker.doWork` delete-and-mark-not-offline pass, with a
  `spareDirectoryNames` seam for a future `cv` store). The available/total disk figures come
  through a `DiskStats` seam backed by a `disk_stats` method channel on `MainActivity` (Android
  `StorageStatsManager` — there is no pure-Dart device-free-space API, so the seam keeps the UI
  testable with a fake). `StorageBreakdownScreen` now sizes its categories through
  `ResourcesRepository.getOfflineResourceItems` rather than an inline `ole/` walk, which both
  removes a duplicate of the repository's grouping logic and makes the screen testable under the
  fake clock (an inline walk hangs the way `storage_category_detail_screen_test` documents).
- `b8e98c550` / `2b39eb329`: the courses progress filter and sort toggle **are ported** (this
  entry was stale): `courseProgressFilterProvider` filters over the whole library with the
  `max`-as-step-count fallback, and `CourseSortState` lives in the provider so it survives
  stream emissions — the new semantics this entry asked for.
- `4fdc7fcb1` is **now ported** (was spec debt): `BecomeMemberActivity`'s username validation is
  debounced 300 ms with a stale-result guard. The Flutter `BecomeMemberScreen` owns a
  `_usernameValidationTimer` that supersedes the previous one on every keystroke, and a stale-result
  guard drops a result whose input no longer matches the field. The check itself moved into
  `UserRepository.validateUsername` (port of `UserRepositoryImpl.validateUsername`): empty/whitespace/
  first-char/charset rules plus the taken-check that skips guest rows. The Dart charset rule is the
  stricter "ASCII letter, digit, `_`, `.`, `-` only" — Kotlin relies on `Character.isLetter` plus an
  ICU `Normalizer.NFD` pass to reject accented Latin, and Dart has no pure-Dart NFD normaliser, so
  the ASCII-only rule encodes the same intent the user-facing message advertises. The submit-time
  validator now also surfaces the live error so a fast tap cannot slip past it.
- `9f3fac1d9`: the dashboard key/IV sync **is now ported** (Phase 61), guard included —
  the `syncJob?.isActive` check is the `SyncRunning` state check in
  `HealthKeyIvSyncNotifier.sync`.
- `dc5659243`: if memberships ever gain a delete-pending state, the roster query must filter it.

## Strategy

- **Coexistence, green at every commit.** The Flutter app lives in `flutter/` alongside the
  untouched Kotlin app. `app/` keeps building and shipping; its `build.yml` / `test.yml` workflows
  are unmodified. A separate `.github/workflows/flutter.yml` guards the port, path-filtered to
  `flutter/**` so neither pipeline slows the other down.
- **Port by vertical slice, not by layer.** Each slice carries a feature from UI to network to
  disk, so every increment is a runnable, testable app rather than an unusable pile of models.
- **The Kotlin stays the specification.** Every ported file names its Kotlin counterpart in a
  doc comment, and behaviour is replicated including quirks (see *Faithful quirks* below).
  Deviations are called out explicitly rather than silently improved.
- **Drop-and-resync, no data copy.** Same policy as the Realm → Room migration: local rows are a
  cache of CouchDB, so the Flutter app starts with an empty database and re-pulls from the
  server. There is no Room → Drift data migration path, and none is planned.

  **Except where the row is not a cache.** `AppDatabase._localAuthorityTables` exempts `outbox`,
  `my_personal`, `removed_log` and `my_life` from the drop: they hold un-pushed writes, private
  notes that may never have been uploaded, the "leave" records that keep the shelf merge from
  re-adding something, and the user's own ordering. No sync can give any of that back, so
  dropping it would silently discard work done offline. The exemption has a cost worth knowing:
  `createAll` will not *alter* a preserved table, so changing one of their shapes needs a
  hand-written migration step.

## What is ported

| Slice | Kotlin source | Flutter destination |
|---|---|---|
| Server configuration | `ConfigurationsRepositoryImpl.getMinApk`, `SyncActivity`, `ServerDialogExtensions` | `repository/configurations_repository.dart`, `ui/sync/server_config_screen.dart` |
| Login (online + offline) | `LoginSyncManager.login`, `UserRepositoryImpl.authenticateUser`, `LoginActivity` | `repository/user_repository.dart`, `ui/sync/login_screen.dart` |
| Resources list | `SyncManager` phase 2, `ResourcesRepositoryImpl`, `ResourcesFragment` | `repository/resources_repository.dart`, `ui/resources/resources_screen.dart` |
| PBKDF2 credential check | `utils/AndroidDecrypter.kt` (via `jpbkdf2`) | `core/crypto/pbkdf2.dart`, `core/crypto/android_decrypter.dart` |
| URL construction | `utils/UrlUtils.kt` | `core/utils/url_utils.dart` |
| Server mirror mapping | `services/sync/ServerUrlMapper.kt` | `core/sync/server_url_mapper.dart` |
| Adaptive batch sizing | `services/sync/AdaptiveBatchProcessor.kt` | `core/sync/adaptive_batch_processor.dart` |
| JSON coercion | `utils/JsonUtils.kt` | `core/utils/json_utils.dart` |
| Version comparison | `utils/VersionUtils.kt` (partial) | `core/utils/version_utils.dart` |
| Dashboard navigation host | `ui/dashboard/DashboardActivity.kt` | `ui/dashboard/dashboard_shell.dart` |
| Courses list, search, filters | `CoursesRepositoryImpl`, `CoursesFragment` | `repository/courses_repository.dart`, `ui/courses/courses_screen.dart` |
| Course detail and steps | `CourseDetailFragment`, `MyCourse`/`CourseStep` | `ui/courses/course_detail_screen.dart`, `data/local/course_mapper.dart` |
| Shelf write-back | `UserRepositoryImpl.uploadShelfData`, `UploadToShelfService`, `RemovedLog` | `repository/shelf_repository.dart`, `removed_log` table |
| Calendar | `CalendarFragment` | `ui/calendar/calendar_screen.dart` |
| Onboarding | `OnboardingActivity`, `OnboardingAdapter` | `ui/onboarding/onboarding_screen.dart` |
| User profile (offline view/edit) | `UserProfileFragment`, `UserProfileViewModel` | `ui/user/profile_screen.dart`, `providers/session_provider.dart` |
| Settings (appearance/server details) | `SettingsActivity`, `ThemeManager` | `ui/settings/settings_screen.dart`, `providers/settings_provider.dart` |
| Dictionary | `DictionaryActivity`, `DictionaryDao` | `ui/dictionary/dictionary_screen.dart`, `repository/dictionary_repository.dart` |
| Notifications | `NotificationsFragment`, `NotificationsRepositoryImpl` | `ui/notifications/notifications_screen.dart`, `repository/notifications_repository.dart` |
| My life | `LifeFragment`, `LifeRepositoryImpl` | `ui/life/life_screen.dart`, `repository/life_repository.dart` |
| References | `ReferencesFragment`, `ReferencesAdapter` | `ui/references/references_screen.dart` |
| Personals (offline CRUD) | `PersonalsFragment`, `PersonalsRepositoryImpl` | `ui/personals/personals_screen.dart`, `repository/personals_repository.dart` |
| Ratings (course UI/offline data) | `RatingsFragment`, `RatingsRepositoryImpl` | `ui/ratings/rating_dialog.dart`, `repository/ratings_repository.dart` |
| Durable write-back queue | `services/retry/RetryQueue.kt`, `RetryQueueWorker`, `model/RetryOperation.kt` | `repository/outbox_repository.dart`, `repository/outbox_drainer.dart`, `ui/outbox_drain_scope.dart` |
| Personal-note upload | `PersonalsRepositoryImpl.uploadPersonalDocument`, `Personal.serialize` | `repository/personals_uploader.dart` |
| Submissions create/upload/list/detail/answers | `Submission`, `Answer`, `SubmissionDao`, `UploadConfigs.Submissions`, `SubmissionsFragment`, `SubmissionDetailFragment` | `repository/submissions_repository.dart`, `repository/submissions_uploader.dart`, `ui/submissions/submissions_screen.dart`, `ui/submissions/submission_detail_screen.dart` |
| Events/meetups | `Meetup`, `MeetupDao`, `EventsRepositoryImpl`, `EventsDetailFragment`, meetup upload config | `data/local/meetup_mapper.dart`, `repository/events_repository.dart`, `repository/events_uploader.dart`, `ui/events/` |
| Individual surveys | `StepExam`, `ExamQuestion`, `SurveysRepositoryImpl`, `SurveyFragment`, survey mode of `ExamTakingFragment` | `data/local/survey_mapper.dart`, `repository/surveys_repository.dart`, `ui/surveys/` |
| Voices / discussions | `News`, `NewsDao`, `VoicesRepositoryImpl`, `VoicesFragment`, `VoicesAdapter`, `ReplyActivity` | `data/local/news_mapper.dart`, `repository/voices_repository.dart`, `repository/voices_uploader.dart`, `ui/voices/` |
| Teams catalog and tasks (partial) | `MyTeam`, `TeamsRepositoryImpl`, `TeamFragment`, `TeamDetailFragment` | `data/local/team_mapper.dart`, `repository/teams_repository.dart`, `ui/teams/` |
| Chat history and conversations | `ChatHistory`, `ChatDao`, `ChatRepositoryImpl`, `ChatHistoryFragment`, `ChatDetailFragment`, `ChatViewModel` | `data/local/chat_mapper.dart`, `repository/chat_repository.dart`, `repository/chat_repository_impl.dart`, `ui/chat/` |
| Feedback | `Feedback`, `FeedbackReply`, `FeedbackDao`, `FeedbackRepositoryImpl`, `FeedbackListFragment`, `FeedbackFragment`, `FeedbackDetailActivity` | `data/local/feedback_mapper.dart`, `repository/feedback_repository.dart`, `repository/feedback_repository_impl.dart`, `ui/feedback/` |
| Community (leaders, services, bottom sheet) | `CommunityTabFragment`, `LeadersFragment`, `CommunityServicesFragment`, `HomeCommunityDialogFragment`, `CommunityLeadersAdapter`, `CommunityPagerAdapter` | `ui/community/`, `core/prefs/planet_prefs.dart` (communityLeaders, communityName, planetType) |
| Exam (graded course exams) | `StepExam`, `ExamQuestion`, `ExamTakingFragment`, `UserInformationFragment`, `BaseExamFragment` | `data/local/exam_mapper.dart`, `data/local/tables.dart` (Exams, ExamQuestions), `ui/exam/` |
| Activity log (logins, resource opens, course visits, syncs) | `ActivitiesRepositoryImpl`, `OfflineActivity`/`ResourceActivity`/`CourseActivity`, `UserSessionManager.setResourceOpenCount`, `UploadConfigs.ResourceActivities`/`ResourceActivitiesSync`/`CourseActivities` | `repository/activities_repository.dart`, `repository/activities_uploader.dart`, `providers/activities_provider.dart` |
| Profile activity stats | `UserProfileViewModel` (lastVisit, offlineVisits, maxOpenedResource), `UserProfileFragment.createStatsMap`, `TimeUtils.getRelativeTime` | `ui/user/profile_screen.dart`, `ui/components/relative_time.dart` |
| Deep links | `OnboardingActivity.handleDeepLinkIntent`/`maybeLaunchPublicSurvey`, `DashboardActivity.openFragmentFromIntent`, the manifest's `myplanet`/`/app/`/`/survey` filters | `core/deeplinks/deep_link.dart`, `providers/deep_link_provider.dart`, `ui/deep_link_scope.dart` |
| Public-survey delivery | `PublicSurveyActivity`'s POST to `/api/public/surveys/…/submissions` | `repository/public_survey_uploader.dart` (live attempt, then the outbox) |

`SharedPrefManager.getFirstLaunch()` is misleadingly named: it defaults to `false` and is set to
`true` once onboarding finishes, so it actually means "onboarding already done". The port stores
the same polarity under the clearer name `onboardingComplete`.

## Technology mapping

| Concern | Kotlin/Android | Flutter/Dart | Notes |
|---|---|---|---|
| DI | Dagger Hilt 2.60 + `@EntryPoint` | Riverpod 2 providers | Runtime graph, no kapt/KSP. `@EntryPoint` escape hatches for Workers become unnecessary. |
| Local DB | Room 2.8.4 (was Realm) | Drift 2.28 | Both are SQLite + DAOs + compile-checked queries. `Flow<List<T>>` → `Stream<List<T>>`. |
| Networking | Retrofit 3 + OkHttp 5 | Dio 5 | Retrofit's annotated interface becomes thin methods; `NetworkResult` sealed class ports directly. |
| Async | Coroutines + `StateFlow` | `Future`/`Stream` + Riverpod `Notifier` | `suspend fun` → `Future`, `StateFlow` → `Notifier`, `SharedFlow` → `Stream`. |
| Navigation | Activities/Fragments + `FragmentNavigator` | go_router | Per-activity prefs checks collapse into one declarative `redirect`. |
| UI | XML layouts + View Binding + RecyclerView | Widgets | 181 layout files have no direct equivalent; they are rewritten, not converted. |
| Prefs | `SharedPrefManager` | `shared_preferences` | |
| Secure storage | `SecurePrefs` (Tink `EncryptedSharedPreferences`) | `flutter_secure_storage` | Keystore-backed on Android either way. |
| Localization | `res/values-{lang}/strings.xml` | `.arb` + `gen-l10n` | Mechanical key-by-key transform; see below. |
| Background work | AndroidX `WorkManager` | Outbox + lifecycle drain | Durable write-back ported; timed/no-user work still open -- see *Hard subsystems*. |
| Deep links | `Intent.getData()` in an Activity | `app_links` + go_router | The engine's own routing may hand over a path-only location; a public-survey link's origin is the server to fetch from, so the full URI has to come from the plugin. |

## Faithful quirks (deliberate non-improvements)

These look like bugs and are reproduced anyway, because changing them changes behaviour for
existing users and servers:

1. **PBKDF2 iteration count is hard-coded to 10** in `AndroidDecrypter`, ignoring the `iterations`
   field on the CouchDB `_users` document. Raising it would lock out every existing user.
2. **`buildCouchdbUrl` drops the URL path.** `https://planet.example.org/ml` becomes
   `https://satellite:PIN@planet.example.org:443` -- CouchDB is reached at the host root under `/db`.
3. **`getUserImageUrl` form-encodes both segments** but rewrites `+` back to `%20` only in the
   image name, not the user id.
4. **Partial syncs are not rolled back.** A page failure mid-walk leaves earlier pages persisted,
   matching `SyncManager`.

Deliberate *deviations*, all flagged in code:

- **`ServerUrlMapper` no longer reads `BuildConfig.PLANET_*`.** Those come from the tracked
  `gradle.properties` -- the committed-secrets problem `CLAUDE.md` documents. The Dart version takes
  its mapping table from `--dart-define=PLANET_SERVER_MAPPINGS=primary=alternative,...`, so nothing
  sensitive is committed. **The exposed PINs still need rotating server-side** regardless of this port.
- **Failure reasons are enums, not pre-localised strings.** `LoginSyncManager` returns English
  literals and `ConfigurationsRepositoryImpl` calls `context.getString(...)` inside the repository.
  The Dart repositories return `LoginFailureReason` / `ConfigurationFailureReason` and the UI
  localises, which keeps `BuildContext` out of the data layer.
- **The PIN and the credentialed CouchDB URL live in secure storage.** `SharedPrefManager` keeps
  `serverPin` and `couchdbURL` in plain `SharedPreferences`, which is world-readable by root and
  is swept up by Android auto-backup. `PlanetPrefs` puts both in `flutter_secure_storage` and
  caches them in memory at startup so the config getter stays synchronous.
- **`resourceRemoteAddress` is credential-free.** `MyLibrary.insertMyLibrary` writes
  `scheme://satellite:<pin>@host/resources/...` into every resource row, putting the PIN in the
  database thousands of times over. The port strips the userinfo and attaches credentials at
  request time. An empty CouchDB URL yields `null` rather than the Kotlin's unusable
  `http:///resources/...`.
- **Course step ids are position-derived, not content-derived.** Embedded steps carry no `_id`, and
  the Kotlin derives one as `Base64(stepJson.toString())`. That is unsafe as a primary key: two
  steps with identical content collide, so the upsert silently drops one (within a course) or moves
  it to the wrong course (across courses), and the key length grows with the step's text. The port
  uses `<courseId>:<position>` -- bounded, unique, and stable across syncs. Safe to diverge on
  because the id is local in both apps, never a server value. `CourseDao.upsertAll` also deletes
  steps past the new length, which upserting alone cannot do when a course shrinks.
- **URL components are percent-encoded.** `buildCouchDbUrl` encodes the PIN, `userDocUrl` encodes
  the login name (keeping the `org.couchdb.user:` colon literal, which CouchDB requires), and
  `resourceUrl` returns `null` instead of interpolating the literal text `null`. All are no-ops
  for well-formed input; the Kotlin is simply wrong when a value contains `@`, `/` or a space.

## Write-back

Phase 3 opened the write path with `ShelfRepository`, which pushes the user's shelf document
(`courseIds` / `resourceIds` / `meetupIds`) back to CouchDB. Two things are worth knowing before
extending it:

- **The payload is derived state, not a queue.** It is recomputed from SQLite on every upload, so a
  failed push needs no retry bookkeeping -- the next one simply sends current truth. Prefer this
  shape over an outbox for anything else that is a whole-document overwrite.
- **Deletions need an explicit record.** The merge unions local ids with the server's, so a "leave"
  would be silently re-added. The `removed_log` table (port of `RemovedLog`) is what makes a
  removal stick. Note the asymmetry, which is the Kotlin's: the removal list filters only the
  *server* side, so re-adding something beats a stale removal record.

Not yet solved: uploads only run while the user is in the app and acting. Submissions, news and
team writes need retry and background delivery -- see item 1 below.

## Platform policy

Both platforms must permit cleartext, because the primary myPlanet deployment is a local community
server on plain HTTP (`http://<ip>:5000`):

- **Android** -- `INTERNET` is declared in the *main* manifest (the Flutter template only declares
  it for debug/profile, so release builds would have had no network at all), plus
  `usesCleartextTraffic` and a `network_security_config.xml` mirroring the Kotlin app's.
- **iOS** -- `NSAppTransportSecurity` / `NSAllowsArbitraryLoads` in `Info.plist`. Narrow this to
  specific domains if the deployment ever standardises on HTTPS.

## The hard part: what does not port mechanically

Ordered by risk, highest first.

1. **`WorkManager` → no equivalent. Resolved for write-back; still open for the rest.**
   `AutoSyncWorker`, `TaskNotificationWorker`, `NetworkMonitorWorker`, `RetryQueueWorker`,
   `DownloadWorker`, `FreeSpaceWorker`, `ServerReachabilityWorker` and `HeavyTableSyncWorker` rely
   on guaranteed, constraint-aware, OS-scheduled execution that survives process death. Flutter
   has no first-party answer; `workmanager` / `flutter_background_service` are thin
   platform-channel wrappers whose Android side would remain Kotlin.

   The unblocking observation is that `RetryQueue` and `RetryQueueWorker` do two separable jobs.
   The queue is a SQLite table -- it is what actually survives process death -- and the worker only
   decides *when* to drain it. So durability ports directly and only the trigger needs replacing.
   `OutboxRepository` is that table (a faithful port of `RetryOperation`, including the
   `min(30s · 2^attempt, 30min)` backoff and the `code >= 500` retryable rule from
   `UploadCoordinator`), `OutboxDrainer` replaces the worker, and `OutboxDrainScope` triggers it
   at startup and on every app resume.

   **The residual gap is scheduling, not durability**: a write made offline is sent the next time
   the app is opened with connectivity, not while it is closed. For an app users open regularly
   that is a latency cost rather than a correctness one, and it is bounded -- the operation sits in
   SQLite until it succeeds or exhausts its attempts.

   This unblocks the *append* write-backs (`submissions`, `voices`, personal notes) that the
   shelf's derived-payload trick could not cover, because an append lost to a dead network is not
   recoverable by recomputation. `PersonalsUploader` is the first one built on it. What is still
   unresolved is periodic background work with no user present -- `AutoSyncWorker`'s timed sync and
   `TaskNotificationWorker`'s deadline notifications genuinely need OS scheduling, and those are
   the cases that may still argue for a permanent Kotlin platform layer.
2. **`TeamsRepositoryImpl` (~1785 lines).** The largest file in the codebase, spanning team
   creation, tasks, membership roles and reactive queries. Should be split by responsibility
   *during* the port, not carried over whole.
3. **The generic upload framework.** `UploadConfig` is generic over `KClass<T : RealmObject>` with
   `queryBuilder: (RealmQuery<T>) -> RealmQuery<T>`. Dart has no reified generics and Drift's DAOs
   are concrete per-table, so `UploadCoordinator` needs re-architecting around explicit
   `fetchPending` / `markSynced` callbacks per upload type -- the same problem the Realm → Room
   migration hit, and the same shape of answer.
4. **181 XML layouts.** No conversion tool produces idiomatic widgets. These are rewrites, and
   they dominate the remaining effort.
5. **Media playback and viewers.** Media3/ExoPlayer, OSMDroid offline maps, Markwon markdown and
   the shared `ResourceViewerActivity` each need a package choice and a fidelity review
   (`video_player`/`media_kit`, `flutter_map`, `flutter_markdown`).
6. **The 5 existing locales.** `values-{ar,es,fr,ne,so}/strings.xml` → `.arb` is mechanical and
   scriptable. Phase 1 ships `app_en.arb` in full and `app_es.arb` populated **only** from strings
   that already exist in `values-es/strings.xml` -- nothing was machine-translated. Where a screen
   needs a string the Kotlin never had, the English is authored and the other locales are left
   absent so `gen-l10n` falls back and it can be translated properly later. Arabic also needs an
   RTL pass.

   **`crowdin.yml` no longer exists**: `abc0dfd01` deleted it from master, so the "repoint it at
   `flutter/lib/l10n/*.arb`" step earlier revisions of this document called for is void, and
   nothing in the repo now describes how translations reach either app. That removes a task from
   the port but does not fill a single string — whoever owns translation now owns these keys, and
   until they are filled a Spanish user reads English.

   Current counts, which move every phase: `app_en.arb` holds **727** messages and `app_es.arb`
   **205**, leaving **522 English-only** — the number `flutter gen-l10n` prints on every build. A
   key is carried into Spanish only when a `values-es` counterpart is unambiguous: the Kotlin
   string name normalises to the ARB key *and* its English text matches, or the English text
   matches exactly and every candidate shares one translation. So the 522 are genuinely new
   phrasings rather than an unfinished mechanical pass — but they are also the largest single gap
   between this port and a shippable replacement, and they grow with every phase that authors a
   new screen.

   **Phase 47 ran that conversion.** `tool/arb_from_strings_xml.dart` now derives `app_ar.arb`,
   `app_fr.arb`, `app_ne.arb` and `app_so.arb` from the Kotlin `values-{ar,fr,ne,so}` files, giving
   each 195-196 strings by the same two matching rules `app_es.arb` was built with — see Phase 47
   below for what that turned up.

   Two quirks are reproduced rather than corrected, because they are what Spanish users see in
   the shipping app today: `myCourses`/`myLife`/`myHealth`/`myPersonals`/`achievements` resolve to
   `misCursos`/`miVida`/`miSalud`/`misPersonales`/`misLogros` -- untranslated camelCase in
   `values-es` -- and `search` is lower-case in English but `Buscar` in Spanish. Both are upstream
   defects in `strings.xml`; fixing them at the translation source corrects the Kotlin and Flutter
   apps together, whereas diverging here would make the two apps disagree.

## Composite row ids, and the answer that exported blank

`SubmissionQuestions` has no `questionId` column: a row's id is
`submissionId:rawQuestionId`, and the exporter recovers the raw id by stripping that prefix to
look up the matching answer. A *survey* question's own row id is already composite
(`surveyId:questionId`), so nesting it produced a three-part key whose stripped form no longer
matched the answer's `questionId` -- and every answer on an exported survey rendered blank.

Two details made it easy to miss. It only bites when the server supplies question ids; when it
does not, the synthetic `surveyId:index` fallback happens to line both sides up, so the
degenerate case passes. And the failure is silent -- a missing key is a null answer, which the
PDF renders as empty rather than as an error. Both cases are pinned in
`test/repository/survey_export_test.dart`.

The prefix is now stripped by known length rather than by splitting at the first colon, so a
question id containing a colon no longer truncates.

## A durable queue outlives its subject

`VoicesRepositoryImpl.getNewsForUpload` reads the live table at send time, so a post deleted
before the upload runs is simply absent from the list. An outbox does not work that way: the
queued operation is a durable row that survives the thing it was queued for. Deleting a post
whose upload was still pending would POST it anyway on the next drain and resurrect it on the
server, with no local row left to record the result against.

`OutboxRepository.cancel` closes this, and `VoicesActions.deletePost` withdraws the whole thread's
operations before deleting. An operation already `in_progress` is deliberately left alone -- the
request may be on the wire, and the drainer still needs the row to record the outcome. This is a
general hazard of the outbox rather than a voices one: any future slice that lets a user delete
something uploadable needs the same withdrawal.

Related, `pendingUploads` diverges from `getNewsForUpload` on purpose. The Kotlin returns *every*
non-guest post and re-PUTs it on each sync, which is harmless for a one-shot batch but would refill
the outbox with unchanged documents on every drain and churn a `_rev` per post per sync. The port
queues only posts that were never delivered or have been edited since. The guest exclusion is
kept: a guest account has no CouchDB user document, so the server rejects anything authored under
it.

## Five defects the outbox and sync paths shared

Found while reconciling the voices and meetups slices; all four are the kind that pass every
test until real data arrives.

- **A refresh un-joined the user from every meetup.** `MeetupMapper.fromDoc` wrote `userId` from
  its (never-supplied) parameter, so each sync nulled the attendance marker -- and `meetupsOnShelf`
  reads exactly that column, so the shelf push then dropped the meetup. Attendance is local: the
  `meetups` document says nothing about whether *this* user joined. `''` is preserved distinctly
  from null, because it means "left" rather than "never joined".
- **The reply walk could recurse forever.** `_collectWithReplies` rebuilt its visited list in each
  frame, so the guard only saw the current one. `replyTo` is server data with no acyclicity
  guarantee, and two rows pointing at each other overflow the stack from an ordinary "delete post"
  tap. One set now threads through the whole walk.
- **`deleteNotIn` bound one variable per synced id.** SQLite rejects statements past
  `SQLITE_MAX_VARIABLE_NUMBER` (999 on older builds), so sync cleanup aborted on a large data set --
  in `news`, `meetups` and `surveys` alike. A `NOT IN` cannot simply be chunked, since each chunk
  matches rows the others keep, so the set difference is taken in Dart and the deletes go through
  the chunking this file already had.
- **An edit racing an in-flight drain was lost.** `findOpen` matches `in_progress` as well as
  `pending`, so `enqueue` rewrote the payload of a row the drainer had already read; the send then
  succeeded with the *old* body and `markCompleted` deleted the row. For voices that is a silent
  loss rather than a delay, because `markUploaded` clears `isEdited` and the post drops out of
  `pendingUploads` too. Patching an `in_progress` row now also returns it to `pending`, and
  `markCompleted` deletes only rows still `in_progress`. `nextAttemptAt` is made due *only* on that
  transition -- resetting it for a merely backing-off row would defeat the backoff, since
  `queuePending` re-enqueues everything pending after each user write.
- **`OutboxRepository.cancel` had a check-then-delete window.** It read the status, then deleted in
  a second statement; the drainer interleaves at every `await` and could claim the row in between,
  so the delete would remove a request already on the wire and leave `markCompleted` with nothing
  to write to. The in-flight exclusion is now part of the delete.

## Voices details worth knowing

- **Visibility fails closed.** `isVisibleToUser` returns false for malformed `viewIn`, and a post
  with no `viewIn` and a `viewableBy` other than "community" is visible to *nobody* -- that
  fall-through is what keeps team-only posts out of the community feed, so it is reproduced
  exactly and pinned by tests.
- **Replies thread by server id.** `postReply` keys the reply to `news._id ?: news.id`, so a reply
  written offline against a synced post still threads correctly. The port's reply lookups follow
  the same rule, and the recursive delete walk checks both keys -- the Kotlin's walk passes only
  `reply.id` and works solely because its rows are keyed by `_id` after a sync.
- **`sharedBy` is read from the nested `news` object** by `buildNewsFromJson` but written at the
  top level by `News.createNews`. The asymmetry is upstream's and is reproduced.
- **`addLabel` deduplicates here.** The Kotlin appends unconditionally, so the same label twice
  yields two identical filter chips that cannot be told apart. That one is corrected.
- **Voices is parked on the My Life app bar.** Its real home is the Community tab
  (`CommunityPagerAdapter`), which is not ported; it was not added to the My Life *list*, because
  that list is seeded, user-reorderable data and inserting a non-Kotlin entry would diverge from
  what the shipping app shows.

## Meetup quirks reproduced deliberately

`Meetup.serialize` sends `sync` through `addProperty`, so the JSON the column holds reaches
CouchDB as a *string* rather than an object -- double-encoded. It also omits `link` entirely when
empty instead of writing null. Neither app reads `sync` back, so the shape is invisible locally,
but both apps write the same database and a document only one of them can read is worse than an
odd one both can. Reproduced, and pinned in `events_repository_test.dart`.

Two divergences went the other way, where faithfulness would corrupt data:

- `getShelfData` appends `meetupId` unconditionally, writing a JSON `null` into `meetupIds` for a
  meetup that has not been uploaded. The port omits it instead -- and specifically does *not* fall
  back to the local row id, which would write a plausible-looking id resolving to no document.
- `getShelfData` filters `meetupIds` against `removedResources`, the *resources* removed log.
  That is a copy-paste slip; there is no removed log for meetups. Unobservable either way, since
  the two id spaces come from different databases and cannot collide. The consequence both apps
  share is that a meetup can be added to a shelf but never removed from one.

`MeetupDao.getByUserId`'s `AND userId != ''` guard is load-bearing and is reproduced:
`toggleAttendance` writes an empty string when a user *leaves* a meetup, so without it a blank
user id would select precisely the meetups the user has left and push them back onto the shelf.

## A cache table that is also a system of record

The `teams` database is the first ported table where CouchDB is authoritative for
*some rows* and the device is authoritative for others. A team, an enterprise, a
membership, a join request, a resource link and a financial report all live in
`teams`, separated only by `docType` -- and the ones the user authors offline are
written with device-generated ids.

That combination broke the two policies this port applies to every cache:

- **Stale-row cleanup.** `sync()` ends by deleting everything absent from the
  synced id set. A locally-authored document is absent *by construction*, so the
  first sync after writing a financial report deleted it. Nothing could bring it
  back: the report existed only on that device.
- **Schema-upgrade drop.** Same shape one layer down. `teams` was not in
  `localAuthorityTables`, so an upgrade dropped the offline documents with the
  catalog.

Both now key off a `Teams.isUpdated` flag set by every local write, mirroring
`Meetups.updated` and `TeamTasks.isUpdated`. Three consequences worth stating,
because the flag is load-bearing in more places than it looks:

1. `TeamMapper.fromDoc` takes the stored row and, when it is flagged, keeps the
   local values and adopts only the server `_rev`. Without this a refresh
   overwrote the edit it was supposed to preserve.
2. `TeamDao.deleteNotIn` skips flagged rows -- and computes the difference in
   Dart rather than issuing `NOT IN`, which `teams` would overflow on any real
   planet since it holds a row per membership and per report.
3. **The flag has to be cleared, or the fix becomes the next bug.** The enqueue
   side lived in `teams_provider.dart` with no handler registered, so the
   drainer's generic fallback posted the payload and recorded nothing. A row
   that stayed flagged after a successful upload would outrank the server
   forever and never be evicted. `TeamsUploader` closes the loop for all four
   team upload types.

The related `getById` bug came from the same conflation: it matched `_id | teamId`,
and every membership, report and resource link carries the team's id in `teamId`.
`getById(teamId)` could return one of those instead of the team, chosen by scan
order, and `addCourses` bails on any row with a `docType` -- so adding a course to
a team silently did nothing, intermittently.

## The preserved-table test is "can a sync restore this?", not "is it local?"

Three packages in a row shipped a table the schema upgrade would drop and
nothing would refill. The nominal rule -- *local intent the server cannot give
back* -- kept passing them, because CouchDB genuinely does hold the data:

- **`teams`** carried offline reports, join requests and resource links mixed
  in with the server catalog.
- **`chat_history`** is a pure mirror of CouchDB, but `insertChatHistoryFromSync`
  has no callers. There is no chat sync, so a drop is permanent.
- **`feedback`** is filed on the device and, before this slice, uploaded by
  nothing at all -- `getPendingFeedback` existed and was never called.

The operative question is not who authored the row, it is whether the next sync
can put it back. A cache with no sync path is local-only in practice, whatever
its provenance. Both later cases were caught by the guard test added after the
`my_life` omission: adding a name to the preserved set fails the suite until a
preservation test exists, which is how the coverage held while the set grew from
nine tables to twelve.

Three related defects clustered around the same code, all from copy-paste:

- `_generateId` appeared twice (chat, feedback) deriving its "random" suffix
  from the timestamp it was already prefixing -- so it was a second copy of the
  same value, and two rows created in the same millisecond collided.
- Chat stored rows under a locally-minted id while CouchDB assigned its own, so
  every follow-up message addressed a document that did not exist.
- `FeedbackMapper.toDoc` omitted `_id`, turning a reply on an uploaded feedback
  into a duplicate thread rather than an update, and `markUploaded` recorded no
  revision, so the update would have conflicted anyway.

## A screen is not ported until something fills it and something leaves it

The three preceding rounds each shipped a table the user wrote to that nothing
uploaded. The exam round inverted it: a screen nothing could reach, reading
tables nothing populated, discarding the only result it produced.

Every piece looked done in isolation. `ExamMapper` was complete and correct in
outline; `ExamDao` had `getById`, `getByStepId`, `questionsFor`; the routes were
registered; the widget rendered five question types. But `ExamMapper.fromDoc`
had **no caller**, so `exams` was always empty; nothing pushed `Routes.exam`, so
the screen was unreachable; and `_submitExam` computed a score into an
`AlertDialog` and dropped it when the dialog closed.

The cheap check that finds all three is a grep for callers, in both directions:

```bash
# Does anything write this table? (mapper with no caller ⇒ table never fills)
grep -rn "ExamMapper\|examDao" lib/ --include=*.dart | grep -v '\.g\.dart'
# Does anything navigate here? (route with no pusher ⇒ screen unreachable)
grep -rn "Routes.exam" lib/ --include=*.dart | grep -v router.dart
```

Neither is caught by `flutter analyze` -- an uncalled public function is not a
warning -- and neither is caught by a test that constructs the mapper directly.
The test that catches it drives the real path: sync a document, then read the
row back through the DAO the screen uses.

Two shape bugs hid behind the same emptiness, and both would have surfaced the
first time a real document arrived:

- **Choices were flattened to `List<String>` by `JsonUtils.getStringList`**,
  which calls `toString()` on each element. CouchDB stores `{"id","text"}`
  objects, so every radio button would have been labelled `{id: c1, text: Paris}`
  while the id -- the thing an answer records and grading compares -- was thrown
  away. Fixed with an `ExamChoice` type and its own converter.
- **The question label was read from `header`**; the Kotlin reads `title`
  (`ExamQuestion.insertExamQuestions`). No document has a `header` key, so every
  question would have rendered unlabelled.

One deliberate divergence: Kotlin's single-`correctChoice` branch stores the
choice's `"res"` field, but choice objects carry the label under `"text"` (see
`ExamTakingFragment.addCompoundButton`), so that lookup yields `""` and the
question cannot be graded. The port records the choice **id** in both branches,
which is what an answer stores and what the multi-select branch already used.

## New dependencies get the same caller check as new code

The viewer round added six packages to `pubspec.yaml`. Three of them --
`photo_view`, `record`, `flutter_tts` -- were never imported by anything, and
`flutter_markdown` is discontinued upstream in favour of
`flutter_markdown_plus`. An unused dependency is not free: `record` alone pulls
native audio-capture code and its permission into the build for a feature that
does not exist yet.

Both checks are one command each, and neither is part of `flutter analyze`:

```bash
# Declared but never imported?
for p in $(sed -n 's/^  \([a-z_]*\):.*/\1/p' pubspec.yaml); do
  grep -rq "package:$p/" lib/ || echo "unused: $p"
done
# Discontinued or abandoned?
flutter pub get 2>&1 | grep -i discontinued
```

## Remaining UI packages (2 of 28)

`components` and `enterprises` are the two packages with no screen of their own. What remains
*within* ported packages (updated through Phase 50):

- `user` -- nothing open. Profile photo *upload* landed in the Phase 36
  harvest (`54790d605`): `UserUploader` carries a `_users` document with the
  photo embedded as a base64 `_attachments` blob, reading the picker's local
  file at queue time; `UserMapper.toDoc` serializes the row, `readImageBytes`
  harvests the bytes, and `UserDao.pendingSyncUsers`/`markUploaded` drive the
  dirty-flag cycle (the `isUpdated` column landed with it as schema v30).
  Displaying the photo also landed in Phase 36. Membership registration landed
  in Phase 27, and `BecomeMemberActivity`'s debounced username validation
  landed in Phase 39.
  - Still unported, but exam-domain rather than `user`: the **submission
    photo** path — `SubmitPhotos` rows captured during a certified exam
    (`ExamTakingFragment.capturePhoto` via `CameraUtils`) and uploaded by
    `PhotoUploader` as `/submissions` documents with the image bytes PUT as a
    CouchDB attachment afterwards. The port's `take_exam_screen.dart` has no
    camera capture, and no `submit_photos` drift table exists (see the
    schema-harvest note). The earlier "Remaining UI packages" list conflated
    this `PhotoUploader` with `updateUserImage`; the profile half is done, the
    exam-proctoring half is the open item.
- `settings` -- nothing open. The free-up-space button and available-space text inside the
  storage sheet landed in this slice: `ResourcesRepository.freeUpSpace` (the
  `FreeSpaceWorker.doWork` delete-and-mark-not-offline pass) plus a `DiskStats` seam over a
  `disk_stats` method channel (`StorageStatsManager`) for the available/total figures.
- `dashboard` -- nothing open on the OS-scheduled side: `AutoSyncWorker` landed in Phase 38
  through the `workmanager` plugin, `TaskNotificationWorker`'s deadline notifications in Phase 42
  on the same plugin's `maintenance` cadence, and `DownloadWorker`'s queue in Phase 43 as a
  durable one-shot.
- `sync` -- nothing open; see `dashboard` above. All three WorkManager jobs now have a home.

Earlier revisions of this list also named team voices, team/public survey sharing, personal
attachments, and team attachments; team attachments (receipt images on finance transactions and
reports, with their upload write-back and sync-in download) landed via `TeamAttachments`, and the
others landed in Phases 26 and 28, and deep links and the durable public-survey response in Phase 35.

**Notes on remaining packages:**
- `components` -- reusable utility widgets. `CheckboxList` and (since Phase 81)
  `ChallengeDialog` are in use; `CustomDropdown` still exists with no callers. Most of what the
  Kotlin package did is handled by Flutter's built-in widgets.
- `enterprises` -- financial reports for teams. Already covered by `team_reports_screen.dart` in the
  teams slice (Phase 18). The Kotlin package is a separate UI layer over the same team data.

**Completed infrastructure:** ratings upload (`RatingsUploader`), offline maps, and storage
management all landed in Phase 25.

Course progress and certification are deliberately deferred with their own packages rather than
bundled into the courses slice. `events` and `surveys` are ported for the individual case, and team
meetups and team/public survey sharing arrived with `teams`.

## Working on the Flutter app

```bash
cd flutter

flutter pub get

# Generated sources are gitignored and must be built before analyze/test.
dart run build_runner build
flutter gen-l10n

flutter analyze
flutter test
flutter build apk --debug

# CI gate. `flutter analyze` passes on unformatted code, so this is a separate
# check and the one most easily missed before pushing.
dart format --output=none --set-exit-if-changed lib test

# Point at mirrored community servers without committing their addresses:
flutter run --dart-define=PLANET_SERVER_MAPPINGS=http://a.example=https://a-clone.example
```

### Conventions

- `lib/core/` is **pure Dart** -- it must not import `package:flutter`. That keeps URL building,
  crypto, JSON coercion and version comparison testable without a widget binding.
- Every ported file names its Kotlin counterpart in its doc comment.
- Repositories return plain rows/values, never live database objects -- the same rule
  `CLAUDE.md` states for Realm/Room.
- Security-critical code is tested against **published or independently generated vectors**
  (RFC 6070 for PBKDF2; Python `hashlib` digests for the credential check), never against the
  implementation itself.

### Known gap: PDF export is Latin-only

`SubmissionsExporter` renders through the `pdf` package's built-in Helvetica, which is
WinAnsi-encoded. Arabic and Nepali are supported app locales, and their glyphs are simply absent
from that font -- a submission with a Devanagari or Arabic title exports without crashing (verified)
but with those characters missing. Fixing it means embedding a Unicode TTF, a ~1 MB binary asset
that was deliberately removed from the port. Decide that trade before the export is offered to
users in an RTL or Devanagari locale.

### Two drift traps that silently lose writes

Both cost a debugging round in the ratings/personals batch, and both fail *quietly* -- the write
succeeds, it just doesn't do what the Kotlin did.

- **`insertOnConflictUpdate` only writes the columns the companion carries.** Room's `@Update`
  writes the whole row. So a `Companion.insert(...)` that omits a field leaves the existing value
  in place instead of resetting it, and `row.toCompanion(true)` (`nullToAbsent`) drops every
  field you just set to `null`, so a cleared description or phone number can never be cleared.
  Port an `@Update` with `toCompanion(false)`, and name the columns a partial upsert must reset --
  `NotificationsRepository` has to pass `isRead: const Value(false)` explicitly to match
  `NotificationsRepositoryImpl`.
- **Widget tests fall through to the real database.** `wrapScreen` redirects
  `appDatabaseProvider` to `AppDatabase.memory()` ahead of the caller's overrides. Without it a
  screen that reads an un-overridden DAO -- an unread badge, a rating summary, a filter list --
  reaches `AppDatabase.open()`, whose `path_provider` lookup has no platform channel under
  `flutter test`; screens read those through `.valueOrNull ?? <default>`, so the error is
  swallowed and the test passes while asserting against nothing. The backstop turns that into a
  "Timer is still pending" failure at teardown: when you see it, override the provider the screen
  actually reads.

## Phase 37 — notifications grouping

The 2026-08-12 rebase recorded commit `8f4d06d5d` ("smoother notifications group view modelling")
as landing on "a grouping model the port has not built" and deferred it. This phase builds it,
harvesting the grouping/expansion model `NotificationsViewModel` gained. The port's notifications
screen had been a flat filtered list; it is now grouped by type with expandable headers.

**The model.** `ui/notifications/notification_grouping.dart` is a pure-Dart port of
`buildGroupedList`/`isGroupDefaultExpanded`/`typeLabelFor`, plus a sealed `NotificationListItem`
(`Header`/`Entry`) mirroring the Kotlin's. Three rules carry over exactly, and the Kotlin test
suite (`NotificationsViewModelTest`) is mirrored as a unit test so the behaviour is pinned:

- **A group is expanded by default only when it has unread items.** `unreadCount > 0` is the
  default; a read-only group arrives collapsed.
- **A tap on a header overrides the default in either direction.** Two sets (`collapsed`/
  `expanded`, in `NotificationExpansionState` via `notificationExpansionProvider`) make "user
  said so" outrank "unread says so", exactly as `_collapsedGroups`/`_expandedGroups` do.
  `toggleExpansion` evaluates the *effective* state (explicit override, else the unread default)
  and flips it. Toggling twice restores the default — not by emptying the sets (the Kotlin keeps
  the type in `expanded` after the second toggle) but by leaving the effective state equal to
  the default; the test asserts the effective state, not the set contents.
- **`markAllAsRead` clears both override sets.** Once everything is read every group's default is
  collapsed, so the screen collapses to a list of headers. `NotificationActions.markAllAsRead`
  calls `resetOverrides` after the write, the way the Kotlin resets both flows.

Unrecognized types normalize to a single `notification` ("Other") group via the `KNOWN_TYPES`
guard, and groups appear in the Kotlin's fixed `typeOrder` (`join_request`, `team_join`, `task`,
`chat`, `voice_reply`, `resource`, `storage`) with any remainder appended.

**The screen.** `notifications_screen.dart` renders the grouped list: headers show the type
icon, the collective label (`groupLabelFor` — "Join Requests", "Tasks", …, distinct from the
per-notification `_titleFor`), an unread-count badge, and an expand/collapse arrow. A header tap
toggles through `notificationExpansionProvider`. The existing filter bar, empty states, swipe-to-
delete, and mark-as-read are unchanged.

One detail the Kotlin deferred and so does this: `resolveType`'s message-content inference (it
re-classifies a `notification`-typed payload as `join_request`/`task`/… from the message text) is
not ported. The port persists `type` at write time (resource/storage/chat rows are typed by their
writers), so grouping on the stored column is correct for what this app produces; the inference
matters for server-originated payloads whose `type` field is unreliable, and the grouping this
commit added is orthogonal to that classification. The six group label strings and a `tasks`
string were added to `app_en.arb`.

### Schema harvest — `all: smoother model database indexing` (8f993472e)

The same rebase brought a schema-only commit adding `@Entity` indices to seven Kotlin tables.
Only two of those tables exist in the port yet (`chat_history`, `feedback`); the rest
(`achievements`, `apk_log`, `community`, `submit_photos`, `user_challenge_actions`) have not been
ported as drift tables and so have nothing to index. Of the two that do exist:

- `chat_history` already carried a `chat_user` index (added in Phase 19), matching the Kotlin's
  `Index("user")`.
- `feedback` carried `feedback_owner` (matching `Index("owner")`) but was missing the Kotlin's
  `Index("openTime")` and `Index("isUploaded")`. Both are added as `@TableIndex` annotations.

Both are preserved tables (`localAuthorityTables`), so the indices land via the migration
strategy's existing index-drop-then-`createAll` loop — no hand-written `ALTER` step is needed,
because `createAll` emits bare `CREATE INDEX` and the pre-drop clears any collision. The
`schemaVersion` bumps 28 → 29; a migration test asserts both indices exist after an upgrade and
that the preserved feedback row survives.

### Harvest audit — the 2026-08-13 commit batch

After the indexing harvest, the remaining commits in the `6977707ef..33cedc3c3` range
were audited. Each was classified as already-harvested, an architectural no-op, or a
substantial new feature beyond simple harvesting:

**Already harvested (prior sessions or this one).** Four commits had their behavioural
fix already in the port, with the code itself citing the commit hash:

- `5f3198970` (voices replying) — `VoicesRepository.postReply` keys `replyTo` to
  `parent.id`, not `_id`, matching the Kotlin's `news.id` switch.
- `2a49db978` (events detail) — `EventsRepository.toggleAttendance` bails out when
  `userId` is null/empty, closing the "leave with no user" hole the Kotlin fixed.
- `48fbf109d` (placeholder wording) — `app_en.arb` carries the "You can add resources" /
  "You can join courses" / "You can join a team" wording.
- `0b8f76770` (responsive layout) — `_LastSyncStrip` shows "Last synced: [relative
  time]" via `lastSyncProvider`, the `updateRailSyncStatus` equivalent.

**Architectural no-ops.** Seven commits fix patterns that the declarative Riverpod
architecture or the port's scope make moot:

- `425602051` (sync message spacing) — a strings.xml trailing-space fix and a Gradle
  version bump; `.arb` preserves whitespace natively.
- `b2733a4e9` (refresh job cancelling) — the Kotlin cancels stale imperative coroutine
  launches (`refreshJobs`, `selectPatientJob`, `updateTasksJob`). Riverpod
  `StreamProvider`/`FutureProvider`/`AsyncNotifier` auto-invalidate and cancel the
  previous computation when a dependency changes, so the race the Kotlin guards against
  does not arise.
- `d7fd6d56c` (download dialog handling) — suppresses a download-suggestion dialog in
  CoursesFragment; the port has no such dialog.
- `f316a8c69` (resources pending downloading) — optimizes a `SELECT *` to `SELECT id`
  for pending-download tracking; the port has no `getPendingDownloads` query.
- `33cedc3c3` (importing) — Kotlin import cleanup; Dart has its own import management
  (`dart format` / `flutter analyze`).
- `db96330a2` (download service testing) — Kotlin `DownloadServiceTest` additions only.
- `5496e1dc1` (merge prepping submodule pinning) — tooling, not the app.

**Substantial new features (not simple harvests).** Four commits are feature additions
whose Kotlin UI layer (RecyclerView adapters, FlexboxLayout, custom Views) has no direct
Flutter counterpart and would require design work, not a line-by-line port. Three of the
four have since been harvested; one remains deferred:

- `c2cf2a788` (grid/list view mode) — **harvested** (`ee0bd3063`). Adds a grid/list
  view-mode toggle persisted in prefs, surfaced on the courses and resources screens.
- `818732139` (grid cover imaging) — **harvested** (`f0d941999`). Adds cover-image
  loading (local file, remote URL with auth, subject-color fallback) to the courses
  grid. Depends on the grid view above.
- `437a3d28a` (enterprises finances date picking) — **deferred.** The enterprises
  feature is not ported.
- `962e1e736` (health user repositories) — **harvested** (`6bb51427a`). Refactors a
  clinician patient-selection flow: patient queries move from `UserRepository` into
  `HealthRepository` (`getPatientById`, `getPatientsSortedBy`, `searchPatients`,
  `getPatientHealthRecords`), a `HealthRecord` model bundles the pojo/profile/
  examinations/user map, and `MyHealthScreen` gains a patient picker dialog (debounced
  search, sort by join-date/name, avatar initials) visible to health providers.


---

## Harvest audit — the 2026-08-14/15/16 commit batch

The 31 commits after `33cedc3c3` (up to `c18d15808`) were audited. One was
harvested; three were already-correct (the port's design sidesteps the bug),
fourteen were architectural/perf/UI no-ops, and three landed on unported
features (deferred):

**Harvested.**

- `c5141b658` (courses: smoother completion rating) — **harvested.** On course
  finish the Kotlin now checks `ratingsRepository.getRatingSummary("course",
  cId, userId)` and, if `userRating` is null, shows the rating dialog before
  popping; an already-rated course pops immediately. The port had only the
  reactive `watchSummary` (`Stream`), so a one-shot `RatingsRepository.summary`
  Future was added (reusing `watchSummary`'s aggregation, backed by a new
  `RatingDao.forItem` Future), and `take_course_screen.dart`'s finish handler
  now mirrors the Kotlin: unrated → `RatingDialog` then pop, rated → pop. Three
  widget tests cover the unrated/rated/dismissed paths; a repository test
  locks in the `summary()` one-shot. The mandatory-survey-toast half of the
  Kotlin commit is **also harvested** — see Phase 52.

**Already correct (the port's design sidesteps the bug).**

- `c6c4030dc` (survey submission syncing) fixed two `SubmissionDao` gaps: (1)
  `countPendingOfflineSubmissions`/`getPendingSubmissions` now match `_id IS
  NULL` as well as `_id = ''`; (2) `updateStatus`/`updateStatusAndLastUpdate`
  now set `isUpdated = 1` so a status change re-queues the row for upload. The
  port's `pendingUploads(userId)` keys pending-ness on `isUpdated` alone (no
  `_id`/`couchId` predicate), so null-`_id` rows are already included, and
  every submission status write in the port (`markComplete`, exam/survey
  creation) already sets `isUpdated: true` — there is no
  `updateStatus`-without-`isUpdated` path. Both halves are no-ops.
- `e8b66b82d` (progress repository data fetching) replaced a
  `submission.parentId.contains(courseId)` substring match with an exact
  `parentId.split("@")[1]` set lookup, fixing mis-attribution when one courseId
  is a substring of another. The port's `ProgressRepository` already routes
  `parentId` through `_examIdFromParent` (`split("@")[0]` → examId → step →
  courseId), an exact path that never substring-matched. No-op.
- `602c178ad` (regex normalizing) extracted `Utilities.normalizeText` (NFD +
  strip diacritics + lowercase). The port's `core/utils/text_utils.dart`
  `normalizeText` (`removeDiacritics(value).toLowerCase()`, explicitly a port
  of `Utilities.normalizeText`) already feeds `titleNormal`/`courseTitleNormal`
  in both mappers. No-op.

**Architectural no-ops.**

- `02368af6b` (view pager listening) — leaks-fix: stores the `ViewPager2`
  page-change callback and unregisters it in `onDestroyView`. Flutter's
  `PageView`/`onPageChanged` has no manual register/unregister and no leak.
- `b35ba5555` (transaction checkpoint applying) — `SharedPreferences.commit()`
  → `apply()` (sync → async) for the sync-checkpoint `putInt`. The port keeps
  no persisted sync checkpoint — each `syncCourses`/`sync*` walk starts `skip`
  at 0 in memory — so there is nothing to make async. (Cross-run
  checkpoint/resume is itself not yet ported.)
- `9bd6a8ed0` (status dashboard collecting) — dedupes consecutive equal
  `SyncStatus` emissions before notifying. Riverpod providers already emit
  only on distinct state by default; the port's dashboard sync state is a
  `StateController`/notifier whose value comparison absorbs the duplicate.
- `1b2eb4346` (network utils flowing) — `SharingStarted.WhileSubscribed()` →
  `WhileSubscribed(5_000)`. The port reads connectivity from
  `connectivity_plus`'s stream directly (no `stateIn` replay subject), so the
  keepalive gap does not arise.
- `84e6c147b` (base container dispatcher providing) — makes `startDownload`
  `suspend` and dispatches `DownloadUtils.openPriorityDownloadService` to IO.
  Dart's download start is already an async event-loop call (no blocking
  main-thread disk/IO); the dispatcher concept does not map.
- `a08fc5662` (notification repository destination view modelling) — two
  parts: (1) `getEffectiveTeamName`/`getEffectiveTeamType` now treat a
  blank-string nav arg as absent (`.takeIf { isNotBlank() }`). The port reads
  `teamName` from the watched `TeamRow`, not a blank-string nav arg, so the
  edge case does not arise. (2) Adds `subType` to `AppNotification` for
  finer-grained destination-view routing — **harvested**: the `Notifications`
  cache table gained a `subType` column at schema v37, and `NotificationParser`
  ports the server-notification parsing (see the deferred-section update
  below).
- `d5f9998e1` (database service module removal) — deletes the vestigial
  `DatabaseService`/`DatabaseModule` and updates docs. The port has no
  `DatabaseService` equivalent (drift `AppDatabase` is used directly).
  Doc-only for the port.
- `a3b76af54` (voices payload diffing), `c18d15808` (enterprises reports
  payload diffing) — RecyclerView `onBindViewHolder` payload-bundle
  diffing; Flutter's widget `diff`/rebuild has no payload-bundle concept.
- `9699e019c` (content item callback diffing) — DiffUtil `contentSelector`;
  same as above.
- `32cab5381`, `c03eb31e2` (workflow test sharding / automerge retrying) —
  CI tooling, not the app.
- `db96330a2`, `33cedc3c3`-style import/version bumps — already covered in
  the prior batch.

**Performance/no-op refactors (same behaviour, faster or cleaner).**

- `70f98be88` (upload shelf api interface) — drops an unused `apiInterface`
  ctor param in favour of a member. DI tidy; the port's shelf uploader takes
  its api via the repository. No-op.
- `178ff70d6` (courses repository flowing) — adds `.distinctUntilChanged` +
  `.flowOn(default)` to a courses `Flow`. Riverpod `StreamProvider` already
  re-emits on distinct stream values; the port's `courseProvider` is a
  `StreamProvider`. Perf, not behaviour.
- `8e0a813fa` (submissions dao bulk inserting) — replaces a per-user
  `getOrCreateSubmission` loop with a batched `getPendingByUsersAndParent`
  (`chunked(500)`) lookup. Same result, fewer round-trips. The port's
  equivalent survey-distribution path is already batched.
- `c390e1343` (courses repository batch querying) — batches existing-course
  lookups (`chunked(300)`) during sync instead of per-row. The port's
  courses sync already preserves shelf membership with one per-page read.
- `d4ae46fad` (voices reply bulk querying) — replaces a recursive
  reply-id walk with a single recursive-CTE query. Same thread set; the
  port collects replies via its own query.
- `4bdbe5867` (converters type token caching) — caches Gson `TypeToken`s
  to avoid per-call allocation. The port uses drift columns / `jsonDecode`
  directly, so there is no `TypeToken` reuse to do.
- `a8c444f4c` (time logger date formatting) — `SimpleDateFormat` →
  `DateTimeFormatter` (thread-safe) on a perf-log line. Internal refactor.
- `1c631e749` (teams repository dao querying) — adds `getByIds`/
  `getResourceIdsByTeamId` (with a lenient `docType IS NULL OR '' OR
  'resourceLink'/'link'` match) and `.distinctUntilChanged()`. The port's
  `watchResourceLinks` already filters `docType = 'resourceLink'` on a
  fresh schema (no legacy null/`link` rows to be lenient about); the
  `distinctUntilChanged` is Riverpod's default.
- `03ebe3aa1` (resource viewer view modelling) — moves
  `getExternalFilesDir` into a ViewModel and drops a redundant
  `isExtractingText` flag. Internal refactor of the viewer's text
  extraction; the port's viewer reads the file inline.
- `d25620b0d` (thumbnail preview loading) — evicts a `PdfThumbnailLoader`
  cache on `onTrimMemory`. The port has no PDF-thumbnail image cache to
  evict (PDFs render through the shared viewer).
- `c9ee679eb` (guest all selecting) — `lifecycleScope` →
  `viewLifecycleOwner.lifecycleScope` and null-guards `checkList` against a
  destroyed view. Dart has no fragment-vs-view lifecycle scope split; the
  port's guest-selection guard is its widget-tree nullability.

**Minor UI (cosmetic or styling-only; the port carries its own layout).**

- `73d04bc51` (courses empty state controling) — hides the search/chip/
  view-toggle controls when the courses list is empty. The port's empty
  state renders its own affordances; tracked as a minor UI nuance, not
  harvested this pass.
- `658f49b00` (survey title ordering) — adds explicit title-asc/desc sort
  options. The port's `SurveysScreen` already exposes both
  `SurveySort.titleAscending` and `titleDescending`. Already present.
- `0b4f3c14a` (user profile landscaping) — dimension rename +
  layout sizing. Cosmetic.
- `295d07f5c` (activities chart landscaping) — makes `computeMonthlyCounts`
  test-visible and calls `setFitBars` (an MPAndroidChart styling API). The
  port's activities chart uses a different chart library.
- `e8b69b591` (list filter icon spacing) — pure layout padding/dimensions.

**Deferred (lands on unported features).**

- `a08fc5662`'s `subType` on `AppNotification` — **harvested.** The
  `Notifications` cache table gained a `subType` column at schema v37, and
  `NotificationParser` ports the server-notification parsing: a raw `"team"`
  type is split (via `linkParams.activeTab == "applicantTab"` or message
  sniffing) into `team_join`, `chat`, and `voice_reply`, each carrying its
  `relatedId` straight to a destination kind. The four Phase-49 routes
  (`resource`, `storage`, `task`, `join_request`) are unchanged; the new
  `teamJoin`, `teamChat`, and `voiceReply` kinds open the team detail or the
  voices thread.
- `437a3d28a` (enterprises finances date picking) — carried over from the
  prior batch; enterprises is not ported.

---

## Harvest audit — the 2026-08-16/17 commit batch

The five commits after `c18d15808` (up to `ffa3fe862`) were audited. One was
harvested; the rest are refactors or perf no-ops:

**Harvested.**

- `756cf75ce` (sync: smoother sync repository json tree mapping) — **harvested.**
  `MyTeam.serialize` builds the team document sent to CouchDB by
  `addProperty`-ing every field (nulls included) and then stripping null-valued
  keys at the end (`object.keySet().filter { isJsonNull }.forEach { remove }`),
  so the uploaded doc never carries `"field": null`. The commit replaced the
  old serialize-then-reparse round-trip (`JsonParser.parseString(gson.toJson)`)
  with this explicit in-place strip — same contract, faster — and added
  `MyTeamTest.testSerializeStripsNulls` to lock it in. The port's
  `TeamsRepository.serializeTeamDocument` already strips nulls for most fields
  via Dart `if (row.X != null)` collection-if entries, but four nullable
  columns — `teamId`, `userId`, `docType`, `teamType` — were added
  unconditionally and would have sent nulls upstream. All four are now guarded
  with `if (row.X != null)`, and a port of `testSerializeStripsNulls` (a row
  with only `_id`/`_rev`/`name` asserts the four are absent) was added to
  `teams_repository_test.dart`. (A broader field-set divergence — the Kotlin's
  `resourceLink` minimal payload and its `report`/`request` field omission —
  predates this commit and is tracked separately as porting work, not part of
  this harvest.)

**Refactor / perf no-ops.**

- `ffa3fe862` (sync: repository api interface injecting) — moves `apiInterface`
  from a `processShelfParallel` method param to constructor injection in
  `SyncRepository`. DI tidy; the port's shelf processing holds its api via the
  repository. No-op.
- `97afa19a2` (teams: voices repository view modelling) — removes
  `getUserById`/`getLibraryResource` from `TeamsRepository` (callers inject
  `UserRepository`/`ResourcesRepository` directly) and drops
  `userRepositoryLazy`. Dependency-structure refactor. No-op.
- `5133ea3ef` (life: health examination dispatcher providing) — refactors
  `HealthExaminationAdapter` to pre-resolve `formattedDate`/
  `isSelfExamination`/`resolvedName` into a `HealthExaminationItem` data class
  (off the `onBindViewHolder` thread) via `DispatcherProvider`. Same displayed
  result; the port's health list builds inline. No-op.
- `25e6a0a97` (life: list adapter caching) — caches `lifeAdapter` so it is not
  recreated on every view creation and guards observer re-attachment
  (`isObserverAttached`). A Kotlin `RecyclerView` perf/leak fix; Flutter's
  `ListView` rebuild is cheap and has no manual observer to double-attach.
  No-op.

---

## Harvest audit — the 2026-08-18 commit batch

The 23 commits after `ffa3fe862` (up to `f53e466b6`, the current tip of
`master`) were audited. None was harvested: every commit is a Kotlin-only DI,
perf, or idiom refactor with no behavioural change the port lacks, or it lands
on an unported feature. The port's own structure (Riverpod providers rather
than ViewModels, Drift rather than Room, no RecyclerView payload/diff model)
already carries the equivalent behaviour where one exists.

**Dependency-structure / DI refactors (same behaviour, cleaner wiring).**

- `41fd50fbc` (dashboard bell view modelling) — moves `checkPendingSurveys`,
  `handleDueReminders`, `scheduleSurveyReminder`, and `getUserModel` out of
  `BellDashboardFragment` and into `BellDashboardViewModel` (a `surveyPrompt`
  `SharedFlow` replaces the fragment's direct dialog calls). Pure extraction;
  the port's dashboard providers already drive the survey-remind-later flow
  landed in Phase 33. No-op.
- `1837101d3` (user repository dao save searching) — `HealthRepositoryImpl`
  and `SubmissionsRepositoryImpl` stop injecting `UserDao` and go through
  `UserRepository` (a `Lazy` wrapper breaks the Dagger cycle), and
  `getUsersByIds` is rewritten as a batched `getUsersByAnyIds`(`chunked(400)`)
  DB query instead of loading every user and filtering in memory. Same result,
  fewer round-trips — the same shape as `8e0a813fa`'s submissions bulk lookup,
  which was already audited as a no-op. The port's user lookups go through its
  own DAO and have no in-memory load-all filter. No-op.
- `d1747995f` (feedback repository saving) — adds `createAndSaveFeedback`, a
  convenience that calls `createFeedback` then `saveFeedback`, and points the
  fragment at it. The port's `FeedbackRepositoryImpl.createFeedback` already
  creates and persists in one step. No-op.
- `b0b6b5bc7` (configurations repository server url updating) — lifts
  `ensureServerUrlUpdated` out of `ResourceViewerViewModel` (where it inlined
  `ServerUrlMapper` + `SharedPrefManager`) into
  `ConfigurationsRepository.ensureServerUrlUpdated`, and swaps
  `UserSessionManager` → `UserRepository` in `CourseProgressViewModel`. DI
  tidy; the port's viewer holds its server-URL update path through its own
  providers. No-op.
- `230991ae7` (diagnostics repository configs uploading) — extracts a
  `DiagnosticsRepository` (crash / `ApkLog` save + pending-lookup + mark-uploaded)
  out of `MainApplication` and `UploadConfigs`, and threads `getDiagnosticsCount`
  hooks through `Activities`/`Progress`/`Submissions`/`Voices` repositories.
  The port has **no** apk-log / crash-diagnostics feature (no `apk_log` table,
  no `CrashLogStore`), so this lands on an unported feature, not a harvest.
  Deferred.

**Perf / concurrency refactors (same displayed result, faster).**

- `212769b7a` (courses progress repository state mapping) — replaces
  `getCourseProgress`'s `HashMap<String?, JsonObject>` return with a typed
  `Map<String, CourseProgressState>` (`current`/`max`), serialising back to JSON
  only at the export boundary. The port's `courseProgressSummary` already
  returns a typed `CourseProgressSummary({max, current})` model — the same
  shape, ported ahead of this commit. No-op.
- `891e5f057` (repositories json parsing) — three Gson-parse tidy-ups:
  `FeedbackRepositoryImpl.addMessage` guards `messages.isNullOrEmpty()` → an
  empty `JsonArray` (the port's `FeedbackMapper.addReply` already returns `[]`
  on null/empty before appending); `SubmissionsRepositoryImpl` hoists
  `JsonParser.parseString(question.choices)` out of the per-choice loop so a
  malformed choices blob fails once rather than per choice (the port decodes
  choices once via `jsonDecode`); and `News.calculateSortDate` /
  `VoicesRepositoryImpl` cache the parsed `viewIn` `JsonArray` in a
  `parsedViewIn` field (the port has no `parsedViewIn` cache to add and parses
  inline). No-op.
- `69719d475` (courses surveys sort views modelling) — moves
  `CoursesViewModel.sortCourses` and `SurveysViewModel.applyFilterAndSort` off
  the main thread (`withContext(default)`) and cancels stale sort `Job`s. Same
  sorted output; the port's courses/surveys screens already expose
  `titleAscending`/`titleDescending`/date sort. No-op.
- `d266d7362` (view models loading) — parallelises independent loads with
  `async`/`coroutineScope` in `EventsDetailViewModel`, `HealthViewModel`, and
  `UserProfileViewModel`. Same data, faster; the port's screens load through
  their own providers. No-op.
- `dd16b4d6b` (resources search view modelling) — wraps
  `saveSearchActivity`, `removeResourcesFromShelf`, and `getFilterFacets` in
  `ResourcesViewModel` methods and makes `removeResourcesFromShelf` surface
  its `Result.onFailure` as an error toast. Phase 50 now carries batch resource
  selection/removal and its failure snackbar. `saveSearchActivity` remains
  unported; filter facets are computed locally from the cached catalog rather
  than through a repository method. **Corrected (Phase 66 audit):**
  `saveSearchActivity` landed in Phase 65.

**RecyclerView / adapter-internal refactors (no Flutter equivalent).**

- `2459a4ae2` (courses surveys refreshing) — drops the `OnDiffRefreshListener`
  interface from `CoursesAdapter`/`SurveysAdapter` in favour of a direct
  `notifyItemChangedById`. Flutter's `ListView` rebuild has no payload/diff
  concept. No-op.
- `87ee0cc59` (resources payload notifying) — replaces
  `notifyDataSetChanged` with `notifyItemRangeChanged` and inlines a
  per-item-lookup in `markItemAsOffline`. RecyclerView payload-notify perf;
  Flutter has no payload-bundle. No-op.

**Kotlin-idiom / dead-code removal (no behaviour).**

- `f53e466b6` (courses repository parts matching) — rewrites
  `matchesAllParts`'s `for` loop as `parts.all { title.contains(it) }`. Pure
  idiom. No-op.
- `9975795ee` (dialog utils indeterminate) — deletes an unused
  `DialogUtils` method. No-op.
- `30b2ace49`, `18edd6a34`, `e2a498d6e`, `dc92815f8`, `d2ca98fda`,
  `6a1b25707`, `da72b96f8`, `61efc728e`, `dcd47dbb8` (the "less … is more"
  series) — each drops an unused repository dependency from a manager
  (`SyncManager`, `UploadManager`, `RetryQueue`, `LoginSyncManager`,
  `TransactionSyncManager`) and the matching test field. Pure DI removal; the
  port's sync/upload/outbox wiring has a different structure with no unused
  injection to trim. No-op.

---

## Harvest audit — the 2026-08-19 commit batch

The 43 commits after `f53e466b6` (up to `9c54a03`, the current tip of
`master`) were audited. One was harvested: `f4adebfc2` ("community: smoother
voices showing"), which rewrote voices community-visibility, share-to-
community, and delete-post semantics — ported in Phase 40 above. The rest were
Refactor / CI / dependency work with no behavioural change the port lacks, or
they land on unported features.

**Behavioural — harvested (one).**

- `f4adebfc2` (community voices showing) — see Phase 40. Harvested.

**Deferred — lands on an unported feature.**

- `beb4696d6`, `55e3d833e` (enterprises finances date filters/reset) — the
  port's team finances screen already carries the date picker and reset; the
  `maxDate` cap (`beb4696d6`) and the sort-order reset on date clear
  (`55e3d833e`) are now ported in `team_finances_screen.dart`.
- `08e18ffdc` (dashboard library card my/call split) — **harvested.** The
  dashboard library card now sets a `resourceShelfOnlyProvider` flag before
  navigating: when the user has shelf items it opens the "My Library"
  (shelf-only) view, and when the shelf is empty it opens the full catalog.
  The resources screen gained a shelf/catalog toggle in its AppBar, backed by
  the same provider, which scopes `watchResources` to `shelfUserId` (the
  `isMyCourseLib` view).
- `758a06f80` (teams submissions streamlining) — `ApkLog.serialize` drops its
  `Context` and `serializeSubmission` drops its `context` parameter; both sit
  on the apk-log/crash-diagnostics feature the port has never had. Deferred.

**View-modelling / DI structure (same behaviour, cleaner wiring).**

- `c5dd782c4` (chat detail ui state view modelling) — moves chat-detail state
  into the ViewModel with dedupe caches. The port's chat providers already
  drive that screen. No-op.
- `a388e44bf` (teams tasks view modelling) — moves task-creator flows into
  `TeamsTasksViewModel` and drops `getAssignee` from the repository interface.
  No-op.
- `27b638c5b` (courses take view modelling) — moves `getCurrentProgress`,
  `logCourseVisit`, `getCourseStepData`, `isStepCompleted` into
  `TakeCourseViewModel`. No-op.
- `f106ee8c7` (base voices tasks dispatcher providing) — `BaseTeamFragment`
  cancels a stale team-load `Job` rather than launching over it. No-op.
- `602eb5027` (courses steps filter coroutine scoping) — `CourseFilterController`
  takes its scope instead of owning one, and the inline-resources preview moves
  behind a `ResourcesPreviewLoader`. Same screen output. No-op.
- `8f1895402` (all: gson injecting) — `@PlainGson` qualifier and a plain
  `Gson()` provider. DI. No-op.
- `487425f56` (configurations repository io wrapping) — wraps repository ops
  in `withContext(io)`. No-op.
- `970f03408` (upload repository api routing) — upload URLs move to the
  repository. No-op.
- `ebb7ab01d` (upload repository attachment dispatcher providing) — wraps
  attachment upload in a dispatcher. No-op.
- `0615f5a8e` (user repository name unifying) — duplicate-user cleanup moves
  from load-all-group-by to a SQL query, relaxed with `IFNULL`. Same set.
  No-op.
- `cc7ae7fe2` (user repository dao querying) — replaces in-memory filters
  (`getSyncedUsers`, `getUsersForHealthSync`, `getPendingSyncUsers`, guest
  lookups, duplicates) with SQL queries. Same selection. No-op.
- `2ea2cd6e3` (user repository parsing) — moves `parseLeadersJson` to
  `UserEntity`'s companion and `getHealthProfile`/`updateUserHealthProfile`
  into `HealthRepository`. Same parsed output. No-op.
- `9d6ece3f9` (teams voices replying) — parses the leaders list once instead
  of per-adapter. No-op.
- `b12e4dc69` (courses download dialog handling) — replaces empty
  `showDownloadDialog` overrides with a `shouldShowDownloadDialog` flag. The
  port has no download dialog on courses to suppress. No-op.
- `2cbf75368` (sync repositories interfaces writing) — adds `*SyncWriter`
  interfaces bound to the same impls. No-op.
- `e1abb0c79` (configurations repository provisioning) — moves first-run
  storage clearing and queued-downloads reads into the repository. No-op.
- `f5bd9cfc7` (all: flow collecting) — fragment-side collect idiom swap.
  No-op.
- `76616dd29`, `eec59939e`, `c84441c69`, `fdd0b2db2` ("less … is more") — DI
  removals from `SyncManager`/`TransactionSyncManager`/`UploadToShelfService`/
  `UploadManager`. No-op.
- `646cd92e8` (teams task json testing) — test-only addition. No-op.

**Perf (same result, faster).**

- `c9fb2eb5b` (courses removed log dao deleting) — chunked deletion moves into
  the DAO at chunk 900 instead of 1000. No-op.
- `5b54eb269` (resources repository inserting) — batches per-item upserts into
  one `upsertAll`. The port upserts in bulk already. No-op.
- `6f10e5970` (sync file uploading streaming) — `asRequestBody` replaces
  read-bytes-then-wrap. The port streams via Dio. No-op.
- `473a9c032` (teams repository csv reports exporting) — rebuilds the reports
  CSV with `append` calls instead of interpolation; same output, and the port
  has no CSV export to chase. No-op.

**Worker/adapter-internal (no Flutter equivalent).**

- `c7e3ad702` (free space worker recursive deleting) — `walkBottomUp` replaces
  hand recursion; children-before-parent semantics, same as the port's
  `freeUpSpace`. No-op.
- `263e6ecf9` (enterprises glide request managing) — clears Glide targets in
  `onViewRecycled`. No-op.
- `a63e2551d` (courses resources caching) — adapter caching and a list/grid
  view-mode setter. RecyclerView-internal. No-op.
- `06c7d5398` (resources list grid toggling) — hides the whole search shell
  and the grid toggle when the list is empty. Tiniest of UI fixes, no analogue
  in the port's resources screen. No-op.

**CI / build / dependency bumps (no app impact).**

- `9c54a0341`, `52d6dc03b`, `f71a68b43` — automerge/release workflow edits.
  No-op.
- `5f80d2453` (`actions/cache` 4→6), `dd8bcb88d` (webkit), `321f5ecaa`
  (appcompat) — dependency bumps. No-op.

---

## Harvest audit — the 2026-08-20 commit batch

The six commits after `9c54a03` (up to `96a04b138`, the new tip of `master`)
were audited. None changes behaviour the Flutter port lacks: two land on
unported features, two are already safe by construction, and two are
CI/importing with no app impact. Each also bumps `versionCode`/`versionName`
(6546→6551) in lockstep, the usual "smoother" cadence.

**Deferred — lands on an unported feature.**

- `815e5bcee` (resources web view nested entry pathing) — adds an
  `openWhichFile` field to `MyLibrary` and a `FileUtils.resolveHtmlEntryFile`
  resolver so an HTML resource whose entry point nests in a subfolder (e.g.
  `sudoku/index.html`) opens from there instead of a bare `index.html`, with a
  path-traversal guard refusing to escape the resource directory. The whole
  change sits on the HTML-resource-as-extracted-directory feature: the
  downloader unzips into `ole/<resourceId>/`, `BaseContainerFragment` checks
  the entry file's existence to decide "downloaded", and `WebViewActivity`
  loads it. The port has none of that — `ResourceFiles` stores a flat
  `ole/<docId>/<filename>`, `ResourceType` has no `html` case, and no
  `webview_flutter` dependency or extraction path exists. Adding
  `openWhichFile` + the resolver with no HTML download or viewer would be
  library code waiting for a caller (the anti-pattern Phase 39's
  `ChallengeDialog`/`CustomDropdown` warn against), so the whole commit
  defers with the viewers hard problem (see "The hard part"). The
  download-path naming change (`getResourceRelativePathFromUrl`, which keys
  the stored file on the nested attachment path) is the same: it only matters
  once a nested HTML resource is downloaded as a directory.
- `96a04b138` (server url alternative credentials mapping) — fixes
  `ServerUrlMapper.updateUrlPreferences` to extract `url_user`/`url_pwd` from
  the *alternative* URL's embedded `user:pwd@host` userinfo rather than the
  primary `uri`'s. The port's `ServerUrlMapper` only ports `processUrl`/
  `extractBaseUrl`; it does not port `updateUrlPreferences` at all, and the
  `ConfigurationsRepository` config handshake uses a single `pin` uniformly
  for both primary and mirror rather than per-URL `url_user`/`url_pwd`
  SharedPreferences. The embedded-credentials-in-alternative-URL case is an
  edge the port's deliberately different config model does not open. No-op
  for the port as it stands; flagged here so a future port of the credentials
  path does not re-introduce the bug.

**Already safe by construction.**

- `2ec7e3187` (sync manager resources cleaning) — sets a `hadBatchFailure`
  flag during the resources `_all_docs` walk and, when any batch failed,
  skips `removeDeletedResources` so an incomplete id list does not evict
  server rows the device still has. The port's `ResourcesRepository.sync`
  previously returned `SyncFailed` on the *first* failed page, more
  conservative than the Kotlin fix but also less resilient — it abandoned
  the whole walk. **Phase 52 now ports the Kotlin's resilient approach**:
  a failed batch advances `skip`, sets the `hadBatchFailure` flag, and the
  cleanup is skipped, so the sync saves what it can and returns
  `SyncComplete` (see Phase 52).
- `a372000df` (courses progress scrolling) — re-layouts
  `activity_course_progress.xml` (NestedScrollView/RecyclerView nesting) to
  fix scroll-jank. Pure Android-layout XML; the port's course-progress
  screen is its own widget tree. No-op.

**CI / build / importing (no app impact).**

- `41d89f46c` (automerge base judging) and `f6bf012bb` (Kotlin importing) —
  `automerge.sh`/`automerge.yml` workflow edits and a Kotlin import cleanup
  across `MainApplication`/`build.gradle`. No-op.

---

## Phase 41 — device/tablet usage telemetry (`myplanet_activities` upload)

The one telemetry path explicitly unported in `ActivitiesRepositoryImpl` — the
`myplanet_activities` document that records device identity and per-app foreground
usage — now ships. This is the per-sync aggregate doc, distinct from the per-row
activity queue (login/resource/course rows) that landed in earlier phases.

### What landed

- **`DeviceStats` platform-channel seam** (`core/system/device_stats.dart`): a Dart
  interface backed by a `MethodChannel` into `MainActivity.kt`, mirroring the
  `DiskStats` pattern. The Kotlin side reads `Settings.Secure.ANDROID_ID` (bare),
  `Build.MANUFACTURER + " " + Build.MODEL` (uppercased), `PackageInfo` version
  code/name, and `UsageStatsManager.queryAndAggregateUsageStats` for the foreground
  time-slice since the last upload. `PACKAGE_USAGE_STATS` (with
  `tools:ignore="ProtectedPermissions"`) is declared in the manifest.
- **`PlanetPrefs` additions**: `customDeviceName`, `lastUsageUploaded` (the cutoff
  the usage query starts from), and `versionDetail` (the raw `/versions` JSON the
  config handshake caches, which `planetVersion` is parsed out of at upload time).
  The `ConfigurationSuccess` now carries the `versionDetail` body through the
  handshake, and `server_config_screen` persists it.
- **`MyPlanetActivitiesUploader`** (`repository/myplanet_activities_uploader.dart`): the
  two-POST + one-GET merge. Posts `getNormalMyPlanetActivities` (the "sync" doc), GETs
  the existing per-device usages doc by `androidId@uniqueIdentifier`, then POSTs the
  merge with `UsageStatsManager` rows appended (or a fresh "usages" doc). After a
  successful merge, `lastUsageUploaded` advances. Skipped for managers. Auth is the
  `satellite:PIN` Basic header.
- **Wired into both sync paths**: the dashboard `syncAll` completion fires it after at
  least one area succeeds (swallowed on error); the `BackgroundTaskRunner` gained an
  `onSyncComplete` hook the background entrypoint fills (also swallowed). This mirrors
  `AutoSyncWorker`/`UserDataWorker` calling `UploadManager.uploadActivities`.
- **Device fields on the per-row serializers**: `loginDoc` now carries
  `androidId`/`deviceName`/`customDeviceName`, and `resourceDoc`/`courseDoc` carry
  `androidId`/`deviceName` — the fields the Kotlin serializers add that the port
  omitted. `ActivitiesUploader` reads them once per `queuePending` through the
  `DeviceStats` seam.

### Closed in Phase 44: other uploaders

The `androidId`/`deviceName`/`customDeviceName` fields Kotlin adds to submissions,
personals, ratings and teams now use the same platform seam; see Phase 44 below.

### Fixed on merge: the identity fields on a usage row

The `usages` rows this phase posts carried two wrong values, fixed when the branch was merged.
Both came from the platform layer answering questions only Dart can answer:

- **`androidId` was the bare `ANDROID_ID`.** `addStats` writes
  `NetworkUtils.getUniqueIdentifier()` — the `androidId_buildId` composite — into a field it
  merely *names* `androidId`. The doc-level serializer here got that right; the per-row one
  passed through what the method channel supplied, which was the bare id. Since the server
  aggregates per device on this value, the "sync" doc and the usage rows from the same handset
  would have described two different devices.
- **`customDeviceName` was always `""`.** It is a stored preference, so the Kotlin `MainActivity`
  cannot read it — but `PlanetPrefs.customDeviceName` exists and the doc-level serializer already
  used it. A user who had named their device uploaded rows claiming they had not.

`device_stats.dart`'s own doc comment states the `androidId` distinction correctly, so this was a
slip rather than a misunderstanding — and one the branch's test pinned (`expect(…['androidId'],
'android-id')`), which is why it passed CI.

The fix removes all three identity fields from `TabletUsageStats` and from the method channel's
payload, leaving the row to carry only what the usage query measures. `MyPlanetActivitiesUploader`
fills `androidId`, `customDeviceName`, and `deviceName` when it serializes, from the values it
already holds. That is a structural fix rather than a value correction: the platform layer can no
longer supply a plausible-looking wrong identity, because it no longer supplies one at all — which
matters because, as `AGENTS.md` notes, the Kotlin side is not covered by the Flutter test gate.

Two tests were added or corrected: the pinned assertion now expects the composite, and a new test
covers the `customDeviceName` half at both the doc level and the row level so the two cannot drift
apart again.

## Phase 42 — task deadline notifications (`TaskNotificationWorker`)

The second of the three `WorkManager` jobs, and the one whose *point* is running with nobody
present: a deadline reminder that only arrives while you have the app open is not a reminder. The
scheduling seam from Phase 38 is what made it tractable — there was nowhere to put this before.

**Where it runs.** Kotlin schedules `TaskNotificationWorker` as its own 900-second periodic worker,
independent of whether a sync was due. This port already has a job with exactly that cadence and
independence: the `maintenance` task. `BackgroundTaskRunner` gained an `onMaintenance` hook next to
Phase 41's `onSyncComplete`, fired on the maintenance run only — firing on both would run the pass
twice a quarter hour — and swallowed like its neighbour, because the Kotlin worker wraps every step
in `runCatching` and always returns `Result.success()`. A missed reminder is not worth an OS retry.

**The once-only guarantee is a column, not the OS.** `team_tasks.isNotified` is what lets a
15-minute worker run forever without re-notifying, so the schema went 31 → 32. `team_tasks` is a
preserved table, so the column needs the hand-written `_addColumnIfMissing` step. Existing rows
default to false, which can re-notify a task the *Kotlin* app already notified about on the same
handset; the alternative — defaulting to true — would swallow the first reminder for every task
already on the device, and a duplicate reminder is the cheaper mistake. The flag is never uploaded
(it is on neither the CouchDB document nor `TeamTask.serialize`), so `markNotified` writes only
`isNotified` and deliberately leaves `isUpdated` alone: marking a row notified must not make it
look like it has an edit to push.

**The split.** `TaskDeadlineNotifier` is the policy as ordinary Dart — order of steps, the window,
the once-only guarantee — with the platform pieces injected: a `NotificationPresenter`, the
existing `DiskStats`, and a clock. `LocalNotificationsPresenter` is the only part that touches
`flutter_local_notifications`. This is the same split `BackgroundTaskRunner` uses and for the same
reason: the contract is invisible from a widget test and impossible from inside an isolate.

**Two quirks reproduced, one of them load-bearing.**

- *The window starts at `now`*, so an **already-overdue** task is outside it and never notified.
  That reads like a bug, but the dashboard's team task badge queries the same window, and the badge
  and the notification disagreeing would be worse than both being narrow.
- *`isTaskUrgent` compares against the deadline day's **midnight***. Kotlin formats the deadline to
  `"EEE dd, MMMM yyyy"` and parses it straight back with `TimeUtils.parseDate` — same pattern, then
  `atStartOfDay` — so the time of day is discarded. The port truncates directly instead of
  round-tripping a display string: same arithmetic, no locale dependency. Note this *widens* the
  urgent band rather than narrowing it, which the test pins with a worked example. It is also
  unreachable-by-one: the window is a day wide and the threshold is two days, so every task this
  path selects is `PRIORITY_HIGH` and the default branch cannot be reached from `run`. The branch is
  kept because it is the Kotlin's, with a test that will start failing if a caller widens the
  window.

**What was deliberately not ported.** `NotificationUtils` also builds survey, join-request,
storage, resource and summary configs, and gates every type behind a `notification_preferences`
`SharedPreferences` file. None of that is here: no ported path calls those factories, and the port
has no UI to toggle the gates, so both would be library code with no caller — the thing this
document keeps recording as a failure mode. The notification titles stay the Kotlin's hardcoded
English (`NotificationUtils` reads no `strings.xml` for them, and a background isolate has no
`BuildContext` to resolve `.arb` against), which is parity rather than a regression but is worth
listing as an open localisation item.

**Two build facts the APK gate caught, both invisible to `analyze` and the 1044 tests:**

- `flutter_local_notifications` fails at `checkDebugAarMetadata` unless **core library desugaring**
  is enabled, because it compiles against `java.time`. `android/app/build.gradle.kts` now sets
  `isCoreLibraryDesugaringEnabled` and adds `desugar_jdk_libs`. Worth remembering as the general
  shape: a plugin can be perfectly happy in Dart and still refuse to link.
- The KGP deprecation warning now names its plugins: `file_picker`, `pdfx`,
  `shared_preferences_android`, `workmanager_android`. It builds today and will not forever.

---

## Phase 43 — durable background resource downloads (`DownloadWorker`)

The last unported WorkManager responsibility now has an end-to-end caller. A viewer download first
stores the resource id in `PlanetPrefs`, deduplicating the queue in the same spirit as Kotlin's
persisted URL set, and registers unique network-constrained one-shot work. The foreground request
still starts immediately, so the viewer's existing progress and success experience does not become
worker-latency-bound. Successful writes remove the id; failures and process death leave it durable.

The background entrypoint recognizes the stable `myplanet.download` task separately from periodic
sync and maintenance. It resolves each current library row, reuses `ResourceDownloader` so file
placement, authentication, empty-body rejection and Drift write-back cannot diverge, removes stale
ids whose metadata vanished, and requests WorkManager retry if any attachment fails. The background
call disables re-enqueueing, preventing a worker from recursively scheduling itself.

`BackgroundScheduler` now exposes one-shot work in addition to periodic work, keeping the plugin
behind the same test seam. Queue policy has a focused test covering persistence, deduplication,
scheduling constraints and completion.

## Phase 44 — device identity parity across locally-authored uploads

Phase 41 introduced the platform seam but intentionally limited its serializer changes to
activities. That left four known document families distinguishable from Kotlin at the server:
personal resources, ratings, submissions and team documents. All four now carry Kotlin's exact
field trio: `androidId` is the `ANDROID_ID_buildId` composite from `uniqueIdentifier()`,
`deviceName` is the normalized manufacturer/model string, and `customDeviceName` is read from
preferences at upload time so a renamed tablet does not retain a stale cached label.

`DeviceIdentitySource` centralizes that contract rather than teaching four repositories about
platform channels. Queue-based uploaders read it once per batch and persist the fields in each
outbox payload. Teams have several enqueue sites, so their shared handler adds the fields at drain
time before POSTing. A fixed source keeps repository tests platform-independent, and each uploader
now asserts all three values on the actual queued or posted document.

## Phase 45 — hardening process-death work and headless identity

The first download-queue pass used a `SharedPreferences` string list. That was durable but not
transactional: the UI and WorkManager isolates could each read an old list and overwrite the
other's update. It also used `ExistingWorkPolicy.keep`, which can strand a request added after the
running worker snapshots the list—the new registration is ignored and the running worker never
saw the late id.

The queue is now a preserved Drift table at schema v33, one primary-keyed row per resource. SQLite
serializes cross-isolate inserts/deletes, deduplicates by construction and participates in the
database's local-authority migration contract. Its migration test proves a requested download
survives an upgrade. One-shot work uses `append`, so every enqueue while another worker is active
has a successor rather than depending on snapshot timing.

Phase 44 also exposed a headless-engine edge: the device-stats channel is registered by
`MainActivity`, which may not exist when WorkManager starts Flutter. App bootstrap now caches the
stable composite id and model name from the UI engine. `PlatformDeviceIdentitySource` refreshes
that cache whenever the channel is available and falls back to it when headless, while continuing
to read the user-editable custom name live. Tests cover platform refresh, headless fallback and the
fail-closed no-platform/no-cache case.

### Phase 46 — the same headless-engine edge, on the storage warning

Phase 45's headless-channel finding applies to `disk_stats` too, and there it was not an edge case
but the whole feature. `TaskDeadlineNotifier`'s storage-warning step (Phase 42) read
`DiskStats.instance` — the `MainActivity`-registered channel — from inside the WorkManager engine,
so every call raised `MissingPluginException`, the step's `runCatching`-shaped `catch` swallowed it,
and nothing was written. Since that step is the *only* caller of
`NotificationsRepository.updateStorageNotification`, the storage notification row was never written
at all. A dead write path behind a caught exception, which is the quietest version of the failure
this document keeps recording.

The fix follows Phase 45's mechanism exactly: app bootstrap primes
`PlanetPrefs.storageAvailablePercent` from the UI engine, and the notifier tries the live read
first, refreshes the cache when it succeeds, and falls back to the primed figure when it cannot.
Where nothing has ever been measured it still writes nothing — a guessed percentage would be worse
than silence.

The cost is staleness: the figure is as old as the last app launch, where Kotlin's `FileUtils`
reads it live because it needs no Activity. A warning a few hours stale still tells the user their
device is filling up; no warning at all does not. Closing that gap properly means moving the two
channels out of `MainActivity` into a `FlutterPlugin` registered for every engine, which is the
real fix for both channels and is not done.

Also corrected here: a comment in `task_deadline_notifier.dart` claiming the dashboard already
called `updateStorageNotification` (nothing did), and two places still describing
`DownloadWorker`'s queue as open after Phase 43 ported it.

## Phase 47 — the other four locales

The mechanical conversion this document had been listing as undone. `tool/arb_from_strings_xml.dart`
derives `app_ar.arb`, `app_fr.arb`, `app_ne.arb` and `app_so.arb` from the Kotlin app's
`values-{ar,fr,ne,so}/strings.xml`, by the same two rules `app_es.arb` was built with: a Kotlin
string name that normalises to the ARB key *with* matching English, or an exact English-text match
where every candidate sharing that English agrees on one translation. Each locale lands 195-196 of
727 keys, alongside Spanish's 205.

Nothing is machine-translated; these are the translations the Kotlin app already ships. Keys with
ICU placeholders or plurals are skipped outright — Kotlin writes `%1$s`, and where a namesake
exists its wording is usually a different phrasing, so deriving from it would attach a translation
to text that says something else. The script is committed rather than run once, because the template
grows with every phase and the same pass will want re-running.

**The picker had four dead entries.** `LocaleNotifier.supportedLanguageCodes` has offered all six
languages since the language action landed in Phase 33, but `supportedLocales` comes from the `.arb`
files, which were `en` and `es`. So choosing Arabic, French, Nepali or Somali set the locale, failed
resolution, and silently rendered English. No test could have caught it: every string still
appeared, just in the wrong language. There is now a test asserting every code the picker offers has
a locale to resolve to.

**Somali would have crashed rather than fallen back.** `flutter_localizations` does not translate
Somali — all three global delegates answer `isSupported(Locale('so'))` with false, where `ar`, `fr`,
`ne` and `es` are all covered. Shipping `app_so.arb` therefore made the locale *resolve* with no
delegate to supply `MaterialLocalizations`, and the first widget to ask for one throws. Adding the
translations would have turned a quietly-English menu entry into a crashing one. This is a genuine
platform difference rather than a porting gap: Android resources need no framework support, Flutter
widgets do.

`lib/l10n/framework_fallback_delegates.dart` serves English framework labels for any locale the
global delegates decline. It must be listed **last**, because `Localizations` uses the first
delegate that claims a type — a fallback placed first would silently revert every locale's framework
strings to English. Both halves are pinned by tests: Somali renders English framework labels with
Somali app strings and no exception, and Spanish still gets Spanish `MaterialLocalizations`.

**The RTL pass, partially.** Arabic actually resolving makes `Directionality` flip for the first
time, which turns 11 hard-coded sites into real bugs. `EdgeInsets.only(left:/right:)` became
`EdgeInsetsDirectional.only(start:/end:)` and `Alignment.centerLeft/Right` became
`AlignmentDirectional.centerStart/End`, including the chat bubbles — where the sender-side asymmetry
*should* mirror in RTL. What this does **not** cover is a visual review: icons that imply direction,
`Row` orders that read as sequences, and anything positioned with a `Stack`. Those need eyes on a
screen in Arabic, which is still open.

Also fixed: `l10n.yaml`'s header still pointed at `../crowdin.yml`, deleted from master.

---

### Phase 48 -- make the finance summary reflect its transactions

The team-finances screen already had the Kotlin enterprises date filters, sort control, receipt
attachments, and debit/credit list, but its summary was calculated too late. The summary widgets
were built with zeroes before the `AsyncValue.data` branch iterated the loaded transactions. The
correct totals only changed local variables after those widgets had been created, so every team
always displayed debit `0`, credit `0`, and balance `0` above an otherwise-correct ledger.

The summary now lives in the same data branch as the transaction list. Debit and credit are reduced
from the filtered rows, balance is derived from those totals, and all three values are built together
for each stream emission. This also means a date-filter or sort-driven provider refresh cannot show
a stale summary from an earlier result. A widget test pins a 125-credit/40-debit ledger to the
expected 85 balance. No image, font, or other binary asset was added.

---

### Phase 49 -- restore notification destination routing

The Flutter notification list had copied the grouped presentation and read/delete actions, but not
`NotificationsFragment.handleNotificationClick`. Tapping an unread row only marked it read, after
which Flutter set `onTap` to null. Resource, storage, task, and join-request notifications were all
dead ends, and even that one mark-read action could not be repeated to revisit a destination.

Notification rows now remain actionable whether read or unread. The first tap still marks an unread
row, then a Dart resolver applies Kotlin's destination policy:

- `resource` opens the resources catalog;
- `storage` opens Flutter's storage-management screen;
- `task` resolves `relatedId` through the cached task and opens that team's task screen; and
- `join_request` resolves the cached request document and opens the join-requests tab on that
  team's members screen.

Resolution deliberately fails closed when a related id is blank, its cached document is missing, or
the type is unknown; the row is still marked read, but the app does not invent a team or malformed
route. The resolver is separate from the widget and covered for all four destinations plus missing,
blank, and unknown inputs. This is entirely Dart/Drift/go_router work with no platform code or binary
assets.

---

### Phase 50 -- batch resource shelf actions

The resource catalog now ports the Kotlin adapter's multi-selection path instead of forcing a
learner through one detail screen per resource. Long-pressing a list row or grid card enters a
contextual selection mode; subsequent taps toggle more resources, selected tiles are visibly
highlighted, and the app bar offers add-to-library and remove-from-library actions. Closing the
contextual bar clears the selection without changing data.

The write is more than a visual shortcut. `ResourcesRepository.setShelfMemberships` loads all
selected rows and updates their `userId` membership arrays in one Drift transaction. In that same
transaction it clears stale removal records for additions or records every removal, so the shelf
merge cannot resurrect only part of a batch. Once the local write commits,
`ResourceShelfActions` attempts the derived CouchDB shelf upload when server configuration and a
CouchDB user id are available; offline and local-only accounts keep the durable local truth for a
later push.

Selection works in list and grid layouts and preserves the existing ordinary-tap navigation when
selection mode is inactive. Repository coverage pins the two-row atomic removal/removal-log result,
and a widget test exercises long-press, multi-select, contextual add, selection clearing, and the
success message. No binary assets or platform code were added.

### Phase 51 — submission photo capture and write-back

A certified course exam now captures a verification photo on submit, the port of
`ExamTakingFragment.capturePhoto` and the `SubmitPhotos` / `PhotoUploader` pair. The gate is
`ProgressRepository.isCourseCertified(courseId)` — the Kotlin source also excludes `isMySurvey`, but
this screen carries only graded course exams, so the certification flag alone is the gate. Capture
runs through a `PhotoCapture` seam (`lib/core/system/photo_capture.dart`) backed by `image_picker`
in production and faked in tests, the same interface-not-static-helper shape `DiskStats` and
`DeviceStats` use. A null capture (no camera, permission denied, or the user backed out) is
swallowed, matching the Kotlin's own try/catch; the submission still uploads, just without a photo.

The write-back is the durable two-step shape every other locally-authored upload uses. A new
`submit_photos` Drift table (schema v34, preserved in `localAuthorityTables` because a captured
photo exists nowhere else) holds the row; `SubmissionsRepository.addSubmissionPhoto` authors it,
`unuploadedPhotos` pairs each row with its serialized document, and `SubmitPhotosUploader.queuePending`
enqueues to the outbox with device identity layered on at queue time (not persisted on the row,
matching every other Flutter uploader). The `OutboxDrainer` handler POSTs the document to
`submissions`, records the CouchDB id/rev via `markPhotoUploaded`, then best-effort PUTs the JPEG
bytes as an attachment to `submissions/<id>/<name>` — a missing file (cleared by storage management)
succeeds as a document, the bytes being the only part that can be re-sent. `SubmitPhotosFiles`
routes the bytes through `<docs>/submit_photos/<photoId>/<filename>`, the same keyed-on-row-id
layout `TeamAttachments` and `ResourceFiles` use so the write-back and the upload read-back cannot
drift. Coverage mirrors `teams_uploader_test.dart` (two-step, no-rev, failure, attachment-skip) and
the migration preservation test.

---

### Phase 52 — mandatory survey toast and resilient resource cleanup

Two upstream fixes that were deferred or noted as buggy in the prior audit are
now closed.

The mandatory-survey toast, the second half of `c5141b658`, is the last piece
of the Kotlin's course-finish gate. The Kotlin, on finish, also checks whether
the course has an attached survey the user has not yet submitted and, if so,
shows a toast and blocks the pop. The blocker was schema: the port's `Surveys`
table had no `courseId`, so course-attached surveys were not modelled. Schema v35
adds `courseId` (and `stepId`) to `Surveys` with an index on `courseId`;
`SurveyMapper.fromDoc` now reads both from the CouchDB document. The check is a
new `SubmissionsRepository.hasUnfinishedSurveys(courseId, userId)` — a port of
the Kotlin `hasUnfinishedSurveys` / `hasSubmission` pair: it pulls every survey
attached to the course (`SurveyDao.getByCourseId`), builds the Kotlin
`parentId` (`"$surveyId@$courseId"`), and counts submissions matching that
parent and user via the new `SubmissionDao.countByUserParentAndType`. The finish
handler in `take_course_screen.dart` now calls it before the rating dialog for
the one course the Kotlin gates (`MANDATORY_SURVEY_COURSE_ID`), showing the
`pleaseCompleteSurvey` toast and returning early when the user has an
outstanding survey. Four repository tests cover the no-surveys / unfinished /
complete / blank-input paths; a widget test locks in the toast; a mapper test
locks in the new columns.

The resource-sync `deleteNotIn` bug (`2ec7e3187`, fixes #15831) is the second
fix. The prior port returned `SyncFailed` on the first batch failure, which
abandoned the whole walk. The Kotlin fix is more resilient: continue past the
failed batch, track a `hadBatchFailure` flag, and skip the cleanup at the end
— the id list is then incomplete and `deleteNotIn` would delete valid
resources. The port now matches that: a failed batch advances `skip`, sets the
flag, and the cleanup is skipped, so the sync saves what it can and returns
`SyncComplete` (not `SyncFailed`). A test seeds a full library, re-syncs with a
failed second batch, and verifies the 50 resources in that batch survive; the
old "fails when a page request fails mid-walk" test is rewritten to expect the
new resilient completion.

### Phase 53 — dashboard shelf/library split, notification sub-destinations, nested HTML entry files

Five upstream fixes from the deferred/audit backlog are now closed.

The dashboard library-card my/call split (`08e18ffdc`, fixes #15728) is the
first. The Kotlin `BellDashboardFragment` passes an `isMyCourseLib` flag so the
library card opens the user's shelf when it has items and the full catalog
otherwise. The port gained a `resourceShelfOnlyProvider` state flag:
`watchResources` now takes a `shelfUserId` (the `isMyCourseLib` view), the
resources screen carries a shelf/catalog toggle in its AppBar backed by that
provider, and the dashboard library card sets the flag from the shelf's size
before navigating — shelf items open "My Library", an empty shelf opens the
catalog. A widget test locks in the toggle.

The notification sub-destination work (`a08fc5662`) is the second. The
`Notifications` cache table gained a `subType` column at schema v37, and a new
`NotificationParser` ports the server-notification parsing the Kotlin
`NotificationsViewModel` does: a raw `"team"` type covers join requests,
membership changes, and chat posts alike, and the server renders `message` in
the recipient's locale so it cannot be classified by phrase-sniffing alone.
The parser splits `"team"` via the locale-independent `linkParams.activeTab ==
"applicantTab"` signal (falling back to message sniffing) into `team_join`,
`chat`, and `voice_reply`, each carrying its `relatedId` straight to a new
destination kind (`teamJoin`, `teamChat`, `voiceReply`). The four Phase-49
routes are unchanged; the new kinds open the team detail or the voices thread.
Four destination tests and the parser tests cover the new paths.

The nested HTML entry-file resolution is the third. An HTML resource's
`openWhichFile` may nest the entry point in a subfolder
(`sudoku/index.html`), and the port's viewer flattened that to
`ole/<id>/index.html`, breaking multi-file bundles. Schema v36 adds
`openWhichFile` to `MyLibraryTable`; `MyLibraryMapper` reads it (nulling out a
whitespace-only value, the Kotlin `.takeIf { isNotBlank() }`); and
`ResourceFiles` gains `resolveHtmlEntryFile` (path-traversal-safe resolution
against the download directory) and `resourceRelativePathFromUrl` (preserves
the subfolder structure when storing the attachment). A mapper test and a
`resource_files_test.dart` cover both the resolution and the traversal guard.

The voices shared-team suffix is the fourth. The Kotlin `VoicesAdapter` appends
"| Shared from {name}" to a community-feed card's date when the post was shared
from a team, derived from `viewIn[0].name`. `JsonUtils.extractSharedTeamName`
ports the read, and `voices_screen.dart`'s `VoiceCard` appends the suffix on
the community feed (no team context).

The team-finances date-filter hardening (`beb4696d6`/`55e3d833e`, #15766) is
the fifth. The date picker now caps `lastDate` at `now` (no future picks), and
the reset button also restores the default descending sort.

---

### Phase 54 — server-url alt-credential fix, HTML resource viewer, resources empty-state controls

Five upstream commits since `9c54a0341` were audited. Four are Kotlin-only
refactors with no behavioural change the port lacks (`2ec7e3187`'s
`hadBatchFailure` cleanup-skip was already ported in Phase 52; `27b638c5b`,
`76616dd29`, `9d6ece3f9` move method calls between Activity/ViewModel/Adapter
without changing behaviour; `a372000df` is a layout-only XML change). The one
real bug is `96a04b138` ("sync: server url alt credentials mapping",
#15781): `ServerUrlMapper.mapUrl` extracted the credentials from the *primary*
URL's userinfo rather than the *alternative* URL it was building, so a server
with different credentials on its clone sent an empty password. The port's
credential extraction lives in `UrlUtils.authHeader` (see below) rather than
in `ServerUrlMapper`, so the mapper was already correct — but `authHeader`
was not reading the alternative URL's userinfo either. Fixed to extract user
and password from the alternative URL itself; a `url_utils_test.dart` case
covers the cross-credential case.

The `authHeader(ServerConfig)` helper deduplicates the
`basicAuthHeader('satellite', config.pin)` pattern that appeared in 20 call
sites across the repositories, uploaders, and providers. The helper lives in
`UrlUtils` alongside `basicAuthHeader`; all 20 sites were replaced, and the
helper is also the seam where the alternative-URL credential extraction
happens (the port's `ServerUrlMapper` does not extract credentials — it only
maps the URL, so the extraction belongs at the header boundary). Three new
tests cover the encoding edge cases (primary server, credential-bearing
alternative URL, alternative without userinfo).

The HTML resource viewer is the long-standing gap: the data layer
(`resolveHtmlEntryFile`, `resourceRelativePathFromUrl`, `openWhichFile`
column) was prepped in Phase 53 but the viewer had no `ResourceType.html`
case, so an HTML resource downloaded fine and then showed "unknown type."
Phase 54 adds the `html` enum value, detects it from `mediaType`/
`resourceType`/filename, and routes `_getLocalFilePath` through
`ResourceFiles.resolveHtmlEntryFile` + `directoryFor` (new) so the entry
file is found in its subfolder rather than as a flat filename. The viewer
itself is a `WebViewWidget` from `webview_flutter`, loading the local entry
file via `loadFile`. Two `directoryFor` tests cover path resolution and
traversal sanitisation.

**Fixed on merge: JavaScript was off.** `WebViewActivity` sets
`javaScriptEnabled = isLocalResource` — on for exactly the local-file case this
viewer implements — while `webview_flutter` defaults to
`JavaScriptMode.disabled`. So an interactive HTML resource (a lesson with a
quiz, anything scripted) rendered inert here and worked in the Kotlin app. The
controller now sets `JavaScriptMode.unrestricted`. The permission is as narrow
as the Kotlin's: this viewer only ever calls `loadFile` on a path under the
app's own resource directory and never `loadRequest`, so scripts run against
downloaded Planet content, never a remote page. Note this is verified by
compilation and by reading the Kotlin, **not** by a test — `resource_viewer_screen`
has no widget test at all, because a `WebViewController` (like the video and PDF
controllers beside it) cannot be constructed without platform bindings. That
absence is worth closing with platform mocks at some point; it is the largest
screen in the port with no coverage.

While reading it: the viewer carried eight hardcoded English strings ("Video
file not found locally", "Unable to load PDF", and so on) that predated that
phase, alongside an unused `fileNotFound` ARB key. That is now closed — see
Phase 57, which wires every viewer sub-screen to `.arb` and removes the last
screen that opted out of localisation wholesale.

The resources empty-state control hiding (#15572, `06c7d5398`) is the UI
fix: when the shelf has zero rows the Kotlin hides the search bar, the
list/grid toggle, and the filter button, leaving only the sync button. The
port now does the same via a `hasData` guard on the AppBar `bottom` (search)
and the `ViewModeToggle`/filter `IconButton`s. A test verifies the three
controls are absent and the sync button remains.

### Phase 55 — team financial report CSV export

The team financial-report screen (`TeamReportsScreen`) lacked the Kotlin
`EnterprisesReportsFragment`'s "Export CSV" button (`473a9c032`, #15785).
The Kotlin flow: the button opens a Storage Access Framework
`ACTION_CREATE_DOCUMENT` picker for a `.csv` filename, then writes the output
of `TeamsRepositoryImpl.exportReportsAsCsv()` — a per-report row of start/end/
created/updated dates (via `TimeUtils.formatDateForCsv`), the five raw
financial fields, and the derived profit/loss and ending balance.

The port adds:

- `TeamsRepository.exportReportsAsCsv(List<TeamRow>, String teamName)` — a
  pure function that builds the CSV string. The derived totals
  (`totalIncome`, `totalExpenses`, `profitLoss`, `endingBalance`) are
  computed inline to match the Kotlin column order exactly.
- `formatDateForCsv(int millis)` — a top-level function porting
  `TimeUtils.formatDateForCsv`'s `"EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)"`
  format (US-locale weekday/month abbreviations, timezone offset and name).
- An "Export CSV" `OutlinedButton` in the reports list header, visible only
  when reports exist (matching the Kotlin `View.GONE` when empty). It calls
  `FilePicker.saveFile()` (the `file_picker` 11.x equivalent of SAF
  `ACTION_CREATE_DOCUMENT`), passing the CSV as UTF-8 bytes. The default
  filename follows the Kotlin pattern:
  `Report_of_<Team>_Financial_Report_Summary_on_<EEE_MMM_dd_yyyy>.csv`.
- Four l10n keys (`exportCsv`, `csvFileSavedSuccessfully`,
  `failedToSaveCsvFile`, `exportCancelled`) across all six arb files, with
  translations taken from the Kotlin `values-*/strings.xml`.

Tests: `exportReportsAsCsv` verifies the header, two report rows with correct
derived totals, and row count; `formatDateForCsv` verifies the format
pattern.

---

## Phase 56 — security-data preservation; harvest audit of the 2026-08-20 batch

The three commits after `96a04b138` (up to `373420b6f`, the new tip of
`master`) were audited. One is a real bug the port shared and now fixes; the
other two are no-ops. Each also bumps `versionCode`/`versionName`
(6551→6555) in lockstep, the usual "smoother" cadence.

**Ported — the bug the port shared.**

- `aa24dfa6c` (sync: user repository security data preserving, #15836) —
  `UserRepositoryImpl.updateSecurityData` wrote `derived_key`/`salt`/
  `password_scheme`/`iterations` unconditionally, so a server response that
  omitted them (a failed/incomplete follow-up GET after a successful PUT)
  overwrote the existing credentials with `null`, locking the user out of
  offline PBKDF2 verification. The Kotlin fix guards each assignment with
  `?.let {}`. **The port had the same bug.** `UserDao.updateUserSecurityData`
  built a `UsersCompanion` with `Value(passwordScheme)` etc., and Drift's
  `Value(null)` is an explicit "set this column to NULL" — distinct from
  `Value.absent()`, which leaves the row untouched. So the moment the
  security-data fetch in `UserRepository.uploadNewUser` returned
  `NetworkError`, the subsequent `updateUserSecurityData` call wiped any
  previously stored `derived_key`/`salt` the row already carried. The fix
  mirrors the Kotlin guard exactly: each nullable credential is written only
  when non-null, otherwise `Value.absent()` is emitted so the column is left
  alone. (`couchId`/`rev` are still written unconditionally — the PUT
  succeeded and the server *did* assign them, so they are never null here,
  matching the Kotlin which assigns `_id`/`_rev` outside the `?.let` guards.)
  The previous DAO test "preserves null fields as null" codified the buggy
  behaviour (it asserted that a null argument *writes* null over the row);
  it is replaced by "preserves existing credentials when the server omits
  them", which seeds a row with real credentials and asserts they survive a
  null-argument call. The `uploadNewUser` repository test that exercises a
  failed security-data GET on a freshly seeded (credential-less) user still
  passes — `Value.absent()` correctly leaves the absent fields null.

**No-op — DI/UI refactoring the port does not mirror.**

- `373420b6f` (teams: finances members repositories splitting, #15840) —
  extracts `TeamsFinancesRepository` and `TeamsMembersRepository` interfaces
  out of `TeamsRepository`, makes `TeamsRepositoryImpl` `@Singleton`, and
  moves `JoinedMemberData` to its own file. Pure Kotlin-DI restructuring:
  the method set and behaviour are unchanged, only the Hilt graph is
  reorganised. The port's `TeamsRepository` is a single plain Dart class
  with no DI container, so there is nothing to split and nothing the split
  changes. The `@Singleton` annotation has no Flutter equivalent either
  (Riverpod providers are already singletons). No-op.
- `563fcf73b` (enterprises: finances landscaping, #15577) — restructures
  `EnterprisesFinancesFragment`'s UI: a `ConcatAdapter` with a sticky
  `FinanceHeaderAdapter` holding the date filter + summary + sort toggle, a
  `HeaderState` replacing scattered fields, and the add-transaction form
  moves from `add_transaction.xml` to `dialog_add_transaction.xml`. It is a
  layout/visual reorganisation, not a behaviour change — the date-filter,
  reset, sort-ascending toggle, debit/credit/balance summary, and
  add-transaction flow are all unchanged. The port's `TeamFinancesScreen`
  already has every one of those (date filter with the `#15766` future-date
  cap, reset, sort toggle, summary, add-transaction FAB); its widget tree is
  its own and the Kotlin's XML/header-adapter split has no analogue to
  mirror. No-op.

---

## Phase 57 — resource viewer localisation

Phase 54 landed the resource viewer with every sub-screen (video, audio, PDF,
image, text/CSV, markdown, HTML) carrying hardcoded English strings — "Video
file not found locally", "Unable to load PDF", "No content", "Empty file",
"Untitled", "HTML entry file not found" — making it the only screen in the port
that had opted out of `.arb` wholesale. (Phase 54 noted this as an open debt;
an unused `fileNotFound` key even sat in `app_en.arb` waiting to be wired.)

This phase closes that. Every sub-screen now resolves its strings through
`AppLocalizations.of(context)`:

- **New keys** in `app_en.arb`: `videoFileNotFound`, `unableToLoadVideo`,
  `audioFileNotFound`, `unableToLoadAudio`, `pdfFileNotFound`,
  `unableToLoadPdf`, `imageFileNotFound`, `noContent`, `emptyFile`,
  `htmlEntryNotFound`. The two pre-existing keys are reused where they fit:
  `fileNotFound` ("File not found locally") for the text and markdown
  "file not found" paths, and `untitledResource` for the `title ?? 'Untitled'`
  fallbacks (the viewer shows resource titles, so the resource-specific key is
  the right one).

- **Translations** across all five locales (ar, es, fr, ne, so) are composed
  from the vocabulary the Kotlin human translators already chose for
  `unable_to_load`, `unable_to_play_video`, and `file_not_found` in each
  `values-<lang>/strings.xml` — the same words, applied to the type-specific
  English the viewer authors. This is not machine translation; it reuses
  paid-for terms. (Spanish's existing `fileNotFound` = "Archivo no encontrado
  localmente" already followed this composition.)

- **Mechanism**: the sub-viewers run their file lookups in `initState`'s
  async callback, before `build` runs — so `AppLocalizations.of(context)`
  cannot be resolved at the lookup site (the `l10n/initState` trap). Each
  sub-viewer now sets a `_fileMissing` bool on a null path (rather than
  overloading `_error` with a literal), and `build` resolves the localized
  message through `AppLocalizations.of(context)`. The "unable to load" path
  (controller null/uninitialized) likewise moves to `build`, where l10n is
  available.

The viewer still has no widget test: `WebViewController`, `VideoPlayerController`,
and `PdfControllerPinch` cannot be constructed under the test binding without
platform bindings, so the largest screen in the port remains untested. That
gap is unchanged by this phase and flagged in Phase 54.

Tests: the existing 1139-test suite passes unchanged; `flutter analyze` clean;
`dart format` clean.

---

## Phase 58 — the last hardcoded UI strings, and screen-test backfill

Phase 57 localised the resource viewer, but a sweep for `Text('…')` literals
with a leading capital turned up four more hardcoded English strings outside
it — the tail of the per-screen localisation pass. Three of the four are
genuine user-facing strings; the fourth is version metadata.

- **`services_screen.dart`** — two `ScaffoldMessenger` snackbars that read
  `Text('Opening: $title')` when a community service's route is an external
  URL or a malformed internal one. `_handleServiceTap` is a method (not a
  `build`), so it now resolves `AppLocalizations.of(context)` at the call
  site and calls `l10n.openingResource(title)`. The new key takes a `{title}`
  placeholder of type `String`.

- **`become_member_screen.dart`** — the `catch (e)` block's
  `Text('Error: $e')` snackbar. `AppLocalizations` was already imported for
  the success path; the catch now calls `l10n.errorOccurred(e)`. The new key
  takes an `{error}` placeholder of type `Object`, matching the existing
  `failedToDelete` pattern.

- **`settings_screen.dart`** — `subtitle: const Text('Build 6297')`. This is
  not a localisation gap: it is version metadata (the same number in every
  locale) and, worse, it is **stale** — `pubspec.yaml` is `0.62.98+6298` and
  `ConfigurationsRepository.defaultAppVersion` still pins `0.62.97`. The
  honest fix is `package_info_plus` reading the real build number at runtime,
  which is a separate task; this phase leaves it.

Two new ARB keys (`errorOccurred`, `openingResource`) with placeholders,
translated across all five locales from the same Kotlin translators'
vocabulary (`error_occurred` / `opening` patterns). ARB files verified
valid JSON + UTF-8.

### Screen-test backfill

Three previously-untested `ConsumerWidget` screens gained widget tests this
phase, each mirroring an existing test's override pattern:

- **`services_screen_test.dart`** (5 tests) — empty state, listing with
  titles, the title-less "Untitled team" fallback, an internal route pushing
  the team screen, and an external route showing the localised "Opening:"
  snackbar. Overrides `teamLinksStreamProvider` with a `Stream.value`, the
  pattern from `team_finances_screen_test.dart`.
- **`leaders_screen_test.dart`** (5 tests) — empty state, per-leader cards
  with the composed display name, the no-email case, the tap-to-open details
  bottom sheet, and the malformed-JSON fallback. Seeds `PlanetPrefs` via
  `SharedPreferences.setMockInitialValues`, the pattern from
  `home_screen_test.dart`'s `_prefs()` helper.
- **`chat_history_screen_test.dart`** (5 tests) — empty state, listing with
  title/last-message/avatar and the "Untitled chat" fallback, the
  malformed-conversation-JSON no-subtitle case, title-based search filtering,
  and the no-matches message. Overrides `chatHistoryProvider` with a
  `Future.value`.

The resource viewer remains untested (platform controllers), as in Phase 57.

Tests: 1139 → 1154 (15 new); `flutter analyze` clean; `dart format` clean.

---

## Phase 59 — runtime app version through `package_info_plus`

Phase 58 noted that `settings_screen.dart`'s `subtitle: const Text('Build
6297')` was not a localisation gap but **stale version metadata** — and that
the honest fix was `package_info_plus` reading the real build number at
runtime. This phase lands that fix, the last correctness gap Phase 58
flagged.

### The problem

Two screens rendered version metadata from hardcoded constants:

- **`settings_screen.dart`** — `l10n.appVersion('0.62.97')` (a literal passed
  to the localised `Version {version}` template) and `const Text('Build
  6297')`. The pubspec version was already `0.62.98+6298`, so both lines were
  wrong.
- **`about_disclaimer_screens.dart`** — `l10n.appVersion(
  ConfigurationsRepository.defaultAppVersion)`, where `defaultAppVersion` was
  itself the stale `0.62.97` constant.

Every release would have silently drifted these further.

### The fix — a testable seam

`package_info_plus` reads the values the build bakes into the Android
manifest (`versionName` / `versionCode`), which is exactly what the Kotlin
app's `BuildConfig.VERSION_NAME` / `VERSION_CODE` exposed. It is wrapped
behind the same seam pattern as `DiskStats` and `DeviceIdentity`, so widget
tests never touch the platform:

- **`lib/core/system/app_version_info.dart`** — an `AppVersionInfo` typedef
  (`({String version, String buildNumber})`) and `loadAppVersionInfo()`, a
  top-level function that calls `PackageInfo.fromPlatform()` and normalises
  the empty-string fallbacks (`package_info_plus` returns `''` under `flutter
  test`) to `0.0.0` / `0`.
- **`appVersionInfoProvider`** — a `FutureProvider<AppVersionInfo>` in
  `app_providers.dart`, the runtime analogue of `diskStatsProvider`. Tests
  override it with a `Future.value` to inject a known version; production
  reads through the platform.

### Wiring

- **`settings_screen.dart`** watches `appVersionInfoProvider`; the version
  tile renders `l10n.appVersion(versionInfo?.version ?? '…')` and a new
  localised `buildNumber({number})` key (replacing the English-only
  `'Build 6297'` literal). The `…` placeholder shows while the async value
  resolves, which is a single frame in production.
- **`about_disclaimer_screens.dart`** — `AboutScreen` is now a
  `ConsumerWidget` reading the same provider, dropping its dependency on
  `ConfigurationsRepository.defaultAppVersion` entirely.
- **`configurations_repository.dart`** — `defaultAppVersion` bumped to
  `0.62.98` to match pubspec, and left as the default for `currentAppVersion`
  (the update-check comparator). Phase 60 revisits that second half: the
  comparator is the one caller where a stale version is not cosmetic, so it
  now reads the runtime value too.

### New ARB key

`buildNumber` (`"Build {number}"` in `app_en.arb`, `"Compilación {number}"`
in `app_es.arb`). The other four locales fall back to the English form, the
same as `appVersion` already does for them. `gen-l10n` clean.

### Tests

- `settings_screen_test.dart` gains a fifth test: scrolls to the version tile
  and asserts `Version 0.62.98` / `Build 6298`, both injected through the
  provider override.
- `about_disclaimer_screens_test.dart` updated: the About test injects
  `0.62.98` through the provider (was asserting the constant).

The 1155-test suite passes, `flutter analyze` clean, `dart format` clean.

### Team-screen test backfill

Phase 58 left the team detail screens (plan, resources, courses, members,
surveys, tasks, calendar, reports) as the largest untested surface —
only `team_finances` and `team_voices` had any coverage. This phase
backfills the rest, each test overriding the screen's family providers
(`teamResourcesProvider(teamId)`, `teamMembersProvider(teamId)`, etc.)
with `Stream.value`/`Future.value` and `teamMembershipsProvider` with
the `isLeader` flag the leader-gated UI depends on. 33 new tests land
across seven files:

- **`team_plan_screen_test.dart`** (6) — not-found, plan sections,
  enterprise mission/services/rules, no-plan/no-mission empty states,
  the leader edit FAB.
- **`team_resources_screen_test.dart`** (4) — empty state, rendering
  linked resources (title/description, the sentence-case "Untitled
  resource" fallback), the leader-gated add-resource FAB and per-row
  remove button, the add-resource dialog open.
- **`team_courses_screen_test.dart`** (4) — empty state, rendering
  linked courses (title/description, the "Untitled course" fallback),
  the leader-gated add-course FAB and remove button, the add-course
  dialog open.
- **`team_members_screen_test.dart`** (4) — members empty state for a
  non-leader (only the Members tab shows), rendering members with
  first-letter avatars and the leader star, the leader-gated Join
  requests tab with accept/decline actions, the requests-tab empty
  state.
- **`team_surveys_screen_test.dart`** (4) — both empty sections,
  rendering owned surveys with the per-row send button (and the
  "Untitled survey" fallback), the disabled adopt button for a
  non-leader, a leader adopting through a mocked
  `SurveysRepository` (verifying the call args and the snackbar).
- **`team_tasks_screen_test.dart`** (5) — empty state, rendering tasks
  (the joined deadline+assignee subtitle, "Untitled task" fallback),
  toggling a checkbox to complete a task, the add-task dialog invoking
  save with the entered title, the per-row popup menu offering edit and
  delete.
- **`team_calendar_screen_test.dart`** (3) — no-meetups message,
  rendering meetups for the selected team on the focused day (filtering
  other teams' meetups, the "Untitled meetup" fallback, location
  subtitle), the formatted HH:mm-HH:mm time range.
- **`team_reports_screen_test.dart`** (3) — empty state, rendering a
  report with the computed totals (`totalIncome`/`totalExpenses`/
  `profitLoss`/`endingBalance` from the `TeamRow` extension getters)
  and the Export CSV button, the leader-gated add-report FAB plus the
  archive action.

The `buildLibraryRow()` test helper gains optional `description` and
`resourceId` params so the resources tests can exercise the description
subtitle and the resourceId-based filtering.

#### Duplicate ARB key

Writing the resources empty-title assertion surfaced a latent bug:
`app_en.arb` had **two** `"untitledResource"` entries — line 639
`"Untitled resource"` (the list fallback, sentence case, present since
the port) and line 1052 `"Untitled Resource"` (added in Phase 57 for
the resource viewer app-bar, title case). A duplicate JSON key means
`gen-l10n` emits one getter and the *last* value shadows the first, so
every caller — including the list screens that wanted sentence case —
rendered "Untitled Resource". The fix splits the key: the viewer and
resource-detail app-bar use the new `untitledResourceTitle` ("Untitled
Resource"), restoring the sentence-case `untitledResource` for the list
fallbacks. The other four locales fall back to English for the new
title key, unchanged.

Two test-determinism hazards were worked around, both noted in the
commit that introduced them: the team-tasks add-dialog test has the
mocked `save` return `false` so the dialog stays mounted (the production
dialog disposes its `TextEditingController`s after `showDialog` resolves,
which races the test binding's teardown); and the team-tasks per-row
popup-menu test asserts only that the menu *offers* edit/delete without
tapping delete, to avoid a `_FocusInheritedScope` deactivation assertion
that the popup route's focus teardown trips under the test binding.

The 1188-test suite passes, `flutter analyze` clean, `dart format`
clean. Every team detail screen now has at least three widget tests.

---

## Phase 60 — the rest of the duplicate-key class, and the version gate

Two follow-ons from the Phase 59 harvest, both extending a fix that was
applied to one instance of a problem rather than to the problem.

### Three more duplicate ARB keys, and a test that will notice next time

Phase 59 found `untitledResource` declared twice and split it. Sweeping
`app_en.arb` for the same shape found **three more**: `justNow` (lines 121
and 675), `description` (527 and 1064) and `apply` (716 and 1087). All three
pairs happen to carry identical values, so unlike `untitledResource` nothing
renders wrongly today — but that is luck, not safety. `untitledResource` was
also harmless on the day its second entry was added; it became a bug when
somebody edited one of the two copies. Each surviving pair is a silent
override waiting for its first divergent edit.

The keys are deduped, and the reason this went unnoticed is now closed off:

- **`locale_coverage_test.dart`** gains `no locale file declares the same key
  twice`, checked across all six locales. It deliberately does **not** use the
  file's own `readArb` helper — `jsonDecode` collapses a duplicate pair before
  any assertion can see it, which is precisely why every existing ARB test was
  blind to this. The check counts keys in the source text instead, tracking
  brace depth so that `@key` metadata blocks (which nest their own
  `description`/`placeholders`/`type`) and repeated placeholder names like
  `count` are not mistaken for top-level duplicates.

The guard was verified in both directions: it fails on the pre-fix file,
naming `justNow, description, apply`, and passes after the dedup. A guard test
that has only ever been observed passing is not evidence of anything.

### The `minapk` comparator reads the runtime version

Phase 59 introduced `appVersionInfoProvider` and pointed the two screens that
*display* a version at it, leaving `ConfigurationsRepository.currentAppVersion`
— the value compared against the server's `minapk` — on the hand-bumped
constant, described there as "the right constant for that path". It is the
opposite: it is the one caller where a stale version is not cosmetic.

`_checkConfigurationUrl` returns `_UrlCheckFailure` when
`isVersionAllowed(currentAppVersion, minApk)` fails, and that failure is
indistinguishable from an unreachable server — so a constant somebody forgot
to bump presents as "cannot reach the server" on the app's *first* screen,
with nothing pointing at the version. The display path drifting is a cosmetic
bug; the comparator drifting locks users out of configuration.

- **`configurations_repository.dart`** — the constructor takes an optional
  `Future<String> Function()? appVersionLookup`, and `_resolveAppVersion()`
  awaits it at the comparison site. `currentAppVersion` stays as the fallback,
  so all existing tests and call sites are unaffected.
- **`app_providers.dart`** — the production provider supplies the lookup off
  `appVersionInfoProvider`, read lazily so it costs nothing until a
  configuration attempt happens.
- The fallback is deliberately generous: a throwing lookup, an empty string,
  or the `0.0.0` placeholder `package_info_plus` reports when no manifest
  values are available all fall back to the constant. Under-reporting the
  version would fail the check outright, which is worse than comparing a
  slightly stale constant — so the runtime value is only allowed to *replace*
  the constant when it is real.

Six tests cover the resolution: the runtime version passing a gate the
constant would fail, the inverse (so the first is not passing for the wrong
reason), and the placeholder/empty/throwing/absent fallbacks.

The 1195-test suite passes, `flutter analyze` clean, `dart format` clean,
`flutter build apk --debug` green.

---

## Phase 61 — the dashboard key/IV sync-in

The last `9f3fac1d9` spec-debt item: the Kotlin dashboard fetches the health
AES key/IV a user published to their per-user CouchDB database when their
local row has none — the path that lets health records written on another
device decrypt here. Without it the port generated a fresh key via
`ensureUserSecurityKeys` and could never read a sibling device's records.

The chain, as `TransactionSyncManager` runs it:

- `BellDashboardFragment.onViewCreated` calls `syncKeyId()` for a non-guest
  user with `TextUtils.isEmpty(user.key)`. The home screen's session listener
  is the same trigger (`!guest && key empty`), firing once per user load as
  the Kotlin's `wasUserNull` gate does.
- `DashboardViewModel.syncKeyId(role)` guards re-entrancy with
  `syncJob?.isActive` and emits Loading/Success/Error. The port's
  `HealthKeyIvSyncNotifier` mirrors both: the guard is the `SyncRunning`
  state check, the events are the shared `SyncUiState`. It does **not** stamp
  the last-sync preference — the Kotlin records that only for full syncs —
  so it is a small dedicated notifier rather than another `SyncNotifier`
  subclass.
- A role containing "health" syncs **every** locally-known synced account
  (`syncAllHealthData` over `getUsersForHealthSync` — couch id non-blank);
  anything else syncs only the signed-in user (`syncKeyIv`). The request
  authenticates as the user (`basicAuthHeader(name, password)` from the
  session + secure storage), not the satellite PIN — the per-user database
  accepts only its owner.
- Per user the fetch is `_all_docs` → first row id → that document, and a
  document with a non-empty key or iv lands via `markUserKeyIvSaved`.
  Per-user failures are swallowed exactly as the Kotlin's
  `catch (e: Exception)` — one unreachable account must not fail the batch,
  and the dashboard never surfaces this sync.

Two details that would have been wrong without reading the source:

- The table name is `userdb-<hex(planetCode)>-<hex(name)>` where `toHex` is
  `String.format("%x", BigInteger(1, bytes))` — one big-endian number, not
  per-byte hex: leading zero bytes collapse and the empty string is `"0"`.
  `toHexString` in `text_utils.dart` pins that (the empty and leading-zero
  cases are the ones a per-byte port gets wrong), and the repository tests
  hardcode the expected table names so a drift shows up as a red test.
- Master's `di?.show()`/`di?.dismiss()` progress dialog is **not** ported:
  `di` is never assigned on master, so those calls are no-ops and nothing
  renders. Porting the dialog would have been porting a phantom.

Still not ported (recorded here so it stops being implicit): the **upload**
direction — `UserRepositoryImpl.saveKeyIv` posts the freshly generated key/IV
to the user's `userdb-*` during member creation, which is what makes a key
recoverable on a second device in the first place. Until that lands, a key
generated by the Flutter app itself is device-local; the sync-in path only
helps users whose key was published by the Kotlin app.

Nineteen tests cover it: `toHexString` parity (including empty-string and
leading-zero collapse), the DAO filter/write methods, the repository fan-out
(non-health → self only with user credentials, health → all synced accounts,
empty doc → no write, per-account failure isolation, no config → no-op), the
notifier (credentials wiring, the re-entrancy guard, error state, no-op
states), and the home-screen gate (triggers for a non-guest without a key,
skips a stored key and guests).

---

## Phase 62 — the key/IV upload direction (`saveKeyIv`)

Phase 61's sync-in reads the key back; this phase writes it. A member
created on the Flutter app now publishes the health AES key/IV to their
per-user CouchDB database during the online creation flow, so records
encrypted on this device decrypt on another — the direction that makes the
key recoverable in the first place.

`UserRepository.saveKeyIv` ports `UserRepositoryImpl.saveKeyIv`:

1. Generate a key/IV (or reuse the row's stored ones — a re-upload must not
   rotate the key, or the records encrypted with the old one become
   unreadable).
2. PUT an empty document to `${dbUrl}/$table` to create the database —
   best-effort, failure swallowed (it may already exist).
3. POST `{key, iv, createdOn}` to that database, retried up to 3 times with
   a 2 s backoff (`RetryUtils.retry`). Throws on the 3rd failure, as the
   Kotlin's `IOException` does — but `uploadNewUser` swallows that so the
   user-facing creation still reports success and the key stays
   device-local.
4. `changeUserSecurity` (port of the same-named Kotlin method) GETs the
   database's `_security` document, appends `health` to the members' roles
   array, and PUTs it back — best-effort, swallowed, so a `_security`
   failure does not undo the key recording.

`uploadNewUser` calls `saveKeyIv` after the security-data fetch, matching
the `saveUserToDb` to `saveKeyIv` chain. Two things worth noting:

- The POST and `_security` calls authenticate as the new member
  (`basicAuthHeader(username, password)`), not the satellite PIN — the
  per-user database accepts only its owner, exactly as the sync-in does.
- The table name reuses `toHexString` from Phase 61, so the upload and
  sync-in target the same `userdb-`+hex database.

Eight tests cover it: generate-and-POST-and-grant-and-record, reuse-stored
key/IV (no rotation), user-credential authentication on both POST and
`_security`, 3-attempt retry-then-succeed, throw-after-3-failures,
`_security` GET failure swallowed but key still recorded, `uploadNewUser`
calls `saveKeyIv`, and `uploadNewUser` still succeeds when `saveKeyIv`
throws.

Still open: the **background** upload path. The Kotlin's
`UploadToShelfService.uploadUserData` to `checkAndUploadUser` to
`uploadNewUser(model)` to `processUserAfterCreation` to `saveKeyIv` chain
runs for pending-sync users drained from the outbox. The Flutter
`UserUploader` handles the `_users` PUT but does not call `saveKeyIv` after
a first-time creation, so an account created offline and later uploaded via
the outbox does not publish its key. That is the durable-path counterpart
to this phase's synchronous path. **Ported in Phase 63.**

---

## Phase 63 — the durable key/IV path (`processUserAfterCreation`)

Phase 62's `saveKeyIv` ran only on the synchronous online-creation path
(`BecomeMemberScreen` → `uploadNewUser`). An account created offline — no
server reachable at signup time — gets a local row with no couchId and no
published key, and later uploads via the outbox. That durable path now
fires the same post-creation steps the Kotlin's
`processUserAfterCreation` runs.

`UserUploader._send` gained an `onCreated` callback, fired after a
first-time `_users` PUT succeeds (no prior couchId → creation, not
update). The provider wires it to call `UserRepository.saveKeyIv` (the
Phase 62 flow) and `HealthExaminationDao.updateUserId` — rewriting
examinations' userId from the local id to the server-assigned couch id,
exactly as the Kotlin's `updateHealthFn` lambda does. Two seams resolve
what the outbox handler signature lacks:

- `readConfig` returns the live `ServerConfig` — the handler gets only
  `authHeader` (the satellite PIN), so the per-user database URL needs the
  config the provider holds.
- `readPassword` reads the signed-in user's password from secure storage —
  `saveKeyIv`'s basic-auth header needs it, not the satellite PIN.

The whole step is best-effort and swallowed, matching the Kotlin's
`try/catch { e.printStackTrace() }` around `processUserAfterCreation`: the
`_users` PUT already succeeded and the row is marked uploaded, so a
`saveKeyIv` or `updateUserId` failure just means the key stays
device-local and the exams keep their local id until the next attempt.

Four tests cover it: `onCreated` fires after a new-user PUT with the right
localId/username/password, does not fire for an existing-user update, a
failure is swallowed and the PUT still reports success, and no config skips
the step entirely.

---

## Phase 64 — team visit logging (`team_log` / `team_activities`)

The Kotlin logs a `teamVisit` action every time a user opens a team's detail
screen (`TeamDetailFragment.onViewCreated` → `TeamsRepositoryImpl.logTeamVisit`
→ `team_log` table → `UploadManager.uploadTeamActivities` on the next sync).
The Flutter port had no `team_log` table and no upload path, so visits were
never recorded.

The port now:

- **`team_log` Drift table** (schema v38, preserved in `localAuthorityTables`):
  one row per visit, carrying `teamId`/`user`/`type`/`teamType`/`createdOn`/
  `parentCode`/`time` and the `uploaded` flag that marks delivery to
  `team_activities`. The preservation test in `migration_test.dart` covers it.
- **`TeamLogDao`**: `insert`, `pendingUploads` (`uploaded = false`), and
  `markUploaded` (records couchId/rev and clears the flag) — the same shape
  as every other local-authority uploader table.
- **`TeamsRepository.logTeamVisit`** — port of the same-named Kotlin method:
  blanks guard (the Kotlin's `if (teamId.isBlank() || userName.isNullOrBlank())
  return`), then `TeamLogTableCompanion.insert`. Returns the new row's id or
  `null`.
- **`TeamLogUploader`** (`type: 'teamLog'`) — the outbox half: `queuePending`
  serializes each pending row with the device identity layered on at queue
  time (the same `DeviceIdentitySource` seam every other uploader uses) and
  POSTs it to `team_activities`; the handler records the returned id/rev via
  `markUploaded`.
- **`TeamDetailScreen` converted to `ConsumerStatefulWidget`**: `_logVisitOnce`
  fires once per mount (a rebuild is not a revisit) via
  `addPostFrameCallback`, matching `TakeCourseScreen`'s visit-log pattern.
  After the write it queues the pending uploads through `TeamLogUploader` so
  the row reaches `team_activities` on the next drain.

Eleven tests cover it: `logTeamVisit` writes the row with the right fields,
blank guards return null, `pendingTeamLogUploads` excludes uploaded rows, the
uploader endpoint is credential-free, `queuePending` enqueues pending rows
with device identity, the handler POSTs and marks uploaded, a missing id/rev
leaves the row pending, the preserved-table migration test survives a schema
bump, and the widget test asserts one visit row fires on open and none for a
guest.

## Phase 65 — search activity logging (`search_activity` / `search_activities`)

The Kotlin records one `search_activity` row every time the user leaves the
courses or resources screen with a filter applied
(`CoursesFragment.onPause`/`ResourcesFragment.onPause` →
`CoursesRepositoryImpl.saveSearchActivity`/`ResourcesRepositoryImpl.saveSearchActivity`
→ `search_activity` table → `UploadManager.uploadSearchActivity`, fired from
`AutoSyncWorker`/`UserDataWorker` at sync completion). The row carries the
search text, the active filter (grade/subject for courses; subjects/languages/
levels/mediums for resources), the user, and the device identity fields the
`SearchActivity.serialize` adds. The Flutter port had no `search_activity`
table, so filtered searches were never logged.

The port now:

- **`search_activities` Drift table** (SQL `search_activity`, schema v39,
  preserved in `localAuthorityTables`): one row per applied search, carrying
  `text` (the CouchDB `searchText` column, renamed to avoid shadowing drift's
  `Table.text`), `type`, `time`, `user`, `filter` (the CouchDB `filterJson`
  column, renamed for the same reason), `createdOn`, `parentCode`, and the
  `uploaded` flag. The preservation test in `migration_test.dart` covers it.
- **`SearchActivityDao`**: `insert`, `pendingUploads` (`uploaded = false`),
  and `markUploaded` (records couchId/rev and clears the flag) — the same
  shape as every other local-authority uploader table.
- **`SearchActivityRepository`** — `saveCourseSearch`/`saveResourceSearch`
  ports of the same-named Kotlin methods: mint a microsecond id, build the
  filter JSON in the Kotlin's key shape (`doc.gradeLevel`/`doc.subjectLevel`
  for courses; `subjects`/`language`/`level`/`mediaType`/`tags` for
  resources — tags serialize as an empty array since the port has no tags
  filter UI), then `insertOnConflictUpdate`. `serialize` is the static port
  of `SearchActivity.serialize`.
- **`SearchActivityUploader`** (`type: 'searchActivity'`) — the outbox half:
  `queuePending` serializes each pending row with the device identity layered
  on at queue time and POSTs it to `search_activities`; the handler records
  the returned id/rev via `markUploaded`. Registered in the
  `outboxDrainerProvider` handler map.
- **`search_activity_providers.dart`** — `saveCourseSearchActivity`/
  `saveResourceSearchActivity` helpers that read the session user and call
  the repository, then queue pending rows into the outbox. They take a
  `ProviderContainer` rather than a `WidgetRef` so they can be called from
  `dispose()`, where the widget's `ref` is already torn down.
- **`CoursesScreen`/`ResourcesScreen` `dispose()`** — port of `onPause` →
  `saveSearchActivity`. The filter state is captured on each `build` into
  fields (since `ref.read` throws after the element is disposed) and the
  `ProviderContainer` is captured via `ProviderScope.containerOf`; on
  `dispose`, if a filter is applied, the helper writes one row and queues it.
- **`dashboard_sync_provider.syncAll`** — `_queueSearchActivities` runs after
  the sync completes, porting `AutoSyncWorker`/`UserDataWorker`'s
  `uploadSearchActivity` call. Swallowed on error, like
  `_uploadMyPlanetActivities`, so losing telemetry never fails the sync.

Eight tests cover it: the uploader endpoint is credential-free and points at
`search_activities`, `queuePending` enqueues pending rows with device identity
and the right filter shape (and skips already-uploaded rows), the handler
POSTs and marks uploaded, a missing id/rev leaves the row pending,
`serialize` reproduces the Kotlin's field shape (and handles an empty
filter), `saveResourceSearch` serializes the resource filter shape, and the
preserved-table migration test survives a schema bump.

## Phase 66 — harvest audit, the 2026-08-20→23 commit batch (85 commits)

The 85 commits after `9c54a03` (the tip of the 2026-08-19 batch) were
audited. Four had already been harvested by earlier phases; the rest are
refactors, performance rewrites, Kotlin-idiom cleanups, CI/dependency work,
or land on unported features — no new behavioural port came out of the batch,
and three stale "unported" claims in older sections were corrected.

**Already harvested (no action).**

- `2ec7e3187` (mid-walk resource cleanup failure) — harvested in Phase 52
  (`hadBatchFailure` skips `deleteNotIn`; the issue number even matches,
  #15831).
- `aa24dfa6c` (security-data preservation) — harvested in Phase 56
  (`Value.absent()` vs `Value(null)` in `updateUserSecurityData`).
- `96a04b138` (alternative-URL credentials, #15834) — harvested in Phase 54;
  the port reads the userinfo straight out of `ServerConfig.alternativeUrl`
  at the `authHeader` boundary instead of re-running the Kotlin's
  prefs-rewrite step.
- `815e5bcee` (nested HTML entry pathing, #15634) — harvested in Phase 53:
  `openWhichFile` column, `resolveHtmlEntryFile` (path-traversal-safe), and
  `resourceRelativePathFromUrl` (subfolder-preserving storage) all exist on
  the Flutter side.

**Deferred — lands on unported features.**

- `2ee164f88`, `825413a9b` (Collections screen view modelling / tag data
  querying) — the resources `CollectionsFragment` (tag editing against
  `tags`) has no counterpart screen in the port.
- `563fcf73b` (enterprises finances landscaping) — deferred for the fourth
  time; enterprises remains covered by the teams slice with no UI layer of
  its own.
- `9d5acb6e2` (bundled `dhulikhel`/`somalia`.mbtiles copy) — the port's
  maps are cached-tiles-only (Phase 25); there are no bundled archives to
  skip copying.

**No Flutter equivalent — same behaviour, different mechanism.**

- `77087b09b` (releases the old ExoPlayer before recreating it) — the port's
  viewer builds one `VideoPlayerController` per widget and disposes it at
  `dispose`; there is no re-create path to leak through.
- `792ca9b5f` (`Locale.US` on `%02d:%02d`) — Dart's own zero-padded minute
  formatting is locale-independent; the Kotlin fix guards against
  locale-sensitive digit substitution the port never performs.
- `dd16b4d6b`-class ViewModel extractions (`4252652c1`, `5939bb25a`,
  `9038ffe96`, `3ec57002c`, `b937ab668`, `d67de28e2`, `e2b1515c2`,
  `2ee164f88`, `9e6256591`, `6c94b58a1`,
  `4c91a5c96`, `ebb8abc1f`, `b45b306dc`'s DI addition, `373420b6f`'s
  repository splitting, `a92fbe3ec`'s and `39d02d4cf`'s repository-method
  moves) — RecyclerView/ViewBinding/Hilt reshuffling. Riverpod providers
  already sit where the Kotlin is moving logic to.
- Layout/dimension handling (`a372000df`, `5386738ce`, `5418c2bf6`,
  `563fcf73b`, `b68cc3ca3` RecyclerView decorations) — no Flutter
  counterpart.
- Kotlin idiom cleanups (`f6bf012bb` import sorting, `d68b6dc85`
  `TextUtils.isEmpty` → `isNullOrEmpty`, `19d81672f` launch→suspend,
  `33367300b` `safeGet` lambda defaults, `9bd82e7ae` override removal,
  `d2753bd7d`/`9bdca5c9f` small API additions) — semantics identical.
- Performance rewrites that keep semantics (`b10ef665b`, `165dfd987`,
  `337cd477c`, `e986d3583`, `6abec3e35`, `868ac3ef0`, `475ca7c68`,
  `c03c3064c`, `14d98ee6b`, `5b5795333`, `13c2b981d`, `7eeb55e55`,
  `83d281311`, `a56ef0943`, `0a80a8203`, `4252652c1`'s query moves,
  `9bdca5c9f`, `5c1902cc0`, `2cc3a836a`/`71c310044` DiffUtil diffs) — the
  port's providers, drift queries, and Riverpod rebuilds make different
  trade-offs; nothing the port lacks is added.
- `240ffbe47` OkHttp 5.5.0 (port runs Dio), `f9881e165`/`86dddb147`/
  `41d89f46c` CI workflow callbacks, and `app/build.gradle` version bumps —
  Kotlin-side machinery.

**Doctor's note (stale claims corrected in place).**

- Phase 30's "still unported" list (completed-course stars, reachability
  ring, team alert badges, offline-logins count, activity chart, remind-later
  scheduler) — all landed in Phase 33; only the navigation drawer/overflow
  menu remains, as a deliberate divergence.
- Phase 40's "still unported on voices" line — the "Shared from X" suffix
  landed in Phase 53; only moderator-gate widenings remain.
- The 2026-08-16/17 audit's "`saveSearchActivity` remains unported" — landed
  in Phase 65 (this doc's own immediately preceding entry).
- Phase 65's "tags serialize as an empty array since the port has no tags
  filter UI" — the tags filter UI and search-activity tag capture landed in
  Phase 67.

---

## Phase 79 — harvest audit, the 2026-08-24/25 commit batch (33 commits)

The 33 commits dated 2026-08-24 and 2026-08-25 were audited. Every one is a
refactor, a performance rewrite, or an Android-lifecycle concern with no
behavioural port coming out of the batch.

**ViewModel extractions and dependency-direction refactors.**

The largest cluster moves logic from Fragments/Activities into ViewModels, or
breaks Hilt cyclic dependencies by having the caller resolve the `UserEntity`
and pass it in rather than the repository looking it up internally. Riverpod
providers already sit where the Kotlin is moving logic to, so none of these
applies to the port:

- `62908f134` (chat repository search) — moves `searchChats`/`sortChats` from
  `ChatViewModel` into `ChatRepositoryImpl`. The port has had this logic as a
  top-level pure function (`searchChatsForMode`/`sortChatsByRecency` in
  `chat_repository.dart`) since Phase 75; the provider calls it directly, and
  `getChatHistoryForUser` already applies the recency sort. The matching,
  ranking (prefix before contains, first turn before later), and normalization
  are identical.
- `efec5e7c6` (submissions repository exams starting) — centralizes
  `ExamTakingFragment`'s exam-start flow into `startExamSession` with a 3-retry
  loop for transient SQLite constraints and a "Failed to start exam session"
  toast. The port's `TakeExamScreen` holds answers in memory and creates the
  submission in a single `createExamDraft` transaction at submit time, so the
  concurrent-write problem that motivated the retry does not arise; the catch
  + `examSubmitFailed` snackbar is the user-facing equivalent of the toast.
  Survey resume (the `recreate = false` path) is already handled by
  `getOrCreateSurveySubmission`, which checks `latestPendingByUserAndParent`
  first. The exam-resume path (a pending exam submission) remains a deliberate
  divergence: the port chose in-memory answers over per-question persistence.
- `d64e98a30` (submissions repository detail view modelling) — removes the
  `Lazy<UserRepository>` from `SubmissionsRepositoryImpl` (breaking a Dagger
  cycle) by having `SubmissionDetailViewModel` and `UploadConfigs` resolve the
  user and pass it into `getSubmissionDetail`/`getExamUploadPayload`/
  `serializeSubmission`. The port's `SubmissionsRepository` resolves user data
  through its own providers and has no Hilt cycle to break.
- `5587845e2` (ratings repository user querying) — removes `userRepository`
  from `RatingsRepositoryImpl`; `submitRating` now takes a `UserEntity` rather
  than a `userId` it looks up. `RatingSummaryProvider` takes `userId` directly.
  Same dependency-direction refactor; the port's ratings provider already
  resolves the user upstream.
- `11cbb723c` (courses less ui state rating map) — drops the ratings map from
  `CoursesUiState` and the `ratingsRepository` from `CoursesViewModel`;
  ratings are now loaded separately through `RatingSummaryProvider`. The port's
  courses screen loads ratings through its own provider and never carried them
  in the courses-list state.
- `fe2bac4b5` (community tab view modelling) — extracts `CommunityTabFragment`'s
  direct prefs/repository reads into a `CommunityTabViewModel`. The port's
  community tab already reads through Riverpod providers.
- `51e602c7c` (notifications view modelling) — rewrites the mark-as-read state
  update as a single `mapNotNull` pass instead of find + conditional filter.
  Same behaviour; the port's notifications provider uses a different state
  shape.
- `9a291306f` (teams events repository detail view modelling) — changes
  `getMembersByIds` from `getAllUsers().filter{...}` to a chunked
  `userDao.getUsersByAnyIds` query, and renames
  `toggleCurrentUserAttendance` to `toggleAttendance(meetupId, userId)`. The
  port already has `toggleAttendance(meetupId, userId)` with the userId passed
  in, and does not load all users to filter for event members.
- `c86eb43d7`, `91ab5f8d8`, `6136f85f8` — cache colors/strings/drawables in
  RecyclerView adapters via `lazy` delegates. No Flutter equivalent; widgets
  resolve theme colors through `Theme.of(context)` on each build.

**Regex and string-handling simplifications (no semantic change).**

- `5131e6c0f` (blood pressure) — `.split("/".toRegex())` becomes `.split("/")`.
  The port already uses `.split('/')`.
- `606752551` (base exams regex) — `parentId?.split("@".toRegex())...get(0)`
  becomes `parentId?.substringBefore("@")`. The port's `_examIdFromParent`
  already uses `indexOf('@')` + `substring`.
- `480de4a3c` (personals resources opening) — `split("\.".toRegex())` becomes
  `substringAfterLast('.', "")` + `lowercase()`. The port's
  `pathResourceType` already lowercases the extension.
- `a467b034c` (concatenated links) — `toMutableSet()` becomes `toHashSet()`.
  The port has no `concatenatedLinks` SharedPreferences path.

**Performance rewrites that keep semantics.**

- `8bf3206cb` (android decrypter sha utils) — replaces `String.format("%02x",
  b)` with a manual hex char array in `bytesToHex`/`Sha256Utils`. The port's
  `bytesToHex` uses `byte.toRadixString(16).padLeft(2, '0')`, which is already
  the fast path; output is identical.
- `99af9569c` (utilities toast) — caches the main-thread `Handler`. Flutter's
  `ScaffoldMessenger` is already on the UI thread.
- `4b27c5808` (selection utils) — drops the redundant `contains` check before
  `remove` (`remove` is a no-op if absent). The port handles selection inline
  in widgets.
- `47342473d` (realtime sync mixin) — caches `getWatchedTables().toSet()`
  instead of calling it per emission. Same filter result.
- `862fb1c5e` (realtime table flowing) — adds an `updatesFor(table)` helper
  that filters the SharedFlow; callers replace inline `.filter { it.table ==
  ... }`. Convenience method, same behaviour.
- `2cf84c298` (progress activities) — wraps `getMostOpenedResource` in
  `withContext(dispatcherProvider.default)`. Dart is single-threaded; the port
  runs the equivalent synchronously.
- `c1721d7b4` (notifications repository) — drops intermediate `toSet()`
  conversions in `markNotificationsAsRead`/`deleteNotifications`. Same result.

**Android-lifecycle concerns (no Flutter equivalent).**

- `2ded20c8e` (fragment manager back stack) — stores the
  `OnBackStackChangedListener` in a field so `onDestroy` can remove it, in
  both `DashboardActivity` and `PublicSurveyActivity`. Flutter uses `go_router`
  for navigation; there are no Fragment back-stack listeners to leak.

**No Flutter equivalent / Kotlin-side machinery.**

- `7f9f80cd4` (code style guide indexing), `d0a1dc01f` (retry interceptor
  testing), `95432de7a` (upload repository querying), `f212742a3` (pager list
  submitting), `aea3c6bfb` (submissions exams view modelling), `c86b26671`
  (surveys view modelling), `14a9f144a` (resources title view modelling),
  `fe3d98cb5` (resources tagging ViewBinding), `4598e8427` (server dialog
  pinning refactor) — RecyclerView/ViewBinding/Hilt reshuffling, CI, or
  refactors of UI paths the port handles differently. The server dialog
  pinning logic has no counterpart: the port uses direct URL entry, not a
  preset-server list with pinning.

The 1360-test suite passes, `flutter analyze` clean, `dart format` clean.

---

## Phase 80 — resource detail toast-on-change (`ef80dda52`)

The `ef80dda52` commit refactored `ResourceDetailFragment`'s add/remove-from-
library flow into a centralized `setUserLibrary(resourceId, add)` method on
`ResourcesRepositoryImpl`. The behavioral change that matters for the port:
the success toast now fires **only when the shelf membership actually
changed**, not on every button press. The Kotlin compares
`library.userId?.size` before and after the write; a no-op toggle (e.g.
another device already synced the same state) stays silent.

The Flutter port's `_toggleLibraryMembership` always showed the snackbar.
The port's `setShelfMembership` is already idempotent at the data level
(rebuilding the `userId` list to the same value when the user is already
present), but it returned `void`, so the screen had no way to know whether
the write changed anything. The fix captures `_resource!.userId.length`
before the async operation, reloads, and only shows the snackbar when the
length differs — the Dart equivalent of the Kotlin's size comparison. The
error snackbar is unchanged.

Three widget tests were added (`test/ui/resource_detail_screen_test.dart`):
add fires the snackbar, remove fires the snackbar, and a no-op (simulated by
writing the user back into the row before the toggle runs, so
`setShelfMembership` rebuilds to the same list) stays silent. The test
overrides `sessionProvider` with a stub `SessionNotifier` and
`ratingSummaryProvider` with an empty `RatingSummary` stream so the
`LinearProgressIndicator` in the rating section doesn't spin forever.

The other four new commits from the batch need no port:
- `1404b04cd` (resources: less apply filter button is more) removes a
  redundant "Apply" button from the Kotlin filter dialog that just called
  `dismiss()`. The Flutter port's filter sheet uses a deliberately different
  two-phase design (local state committed on Apply), so the button serves a
  real purpose and stays.
- `ce6f701bc` (gradle-wrapper bump) and `f71ab5633` (playstore quota CI)
  are Kotlin/CI machinery with no Flutter equivalent.
- `f66ee1454` (enterprises finances transaction view modelling) extracts a
  `transactionCreated` SharedFlow into `EnterprisesFinancesViewModel`. The
  port's finances screen uses Riverpod providers, so the refactor pattern
  doesn't apply.

The 1363-test suite passes, `flutter analyze` clean, `dart format` clean.

---

## Phase 81 — the challenge dialog (`ChallengePrompter` / `DashboardViewModel.evaluateChallengeDialog`)

The December 2024 / January 2025 challenge campaign. A non-guest user on a
participating server, between Nov 30 2024 and Jan 16 2025, sees a dialog on
dashboard load tracking three tasks: complete the challenge course
("terminado"), post five community voices, and sync. The Kotlin's
`ChallengePrompter.showChallengeDialog` is driven by
`DashboardViewModel.evaluateChallengeDialog`, which gathers the voice counts,
course status, and sync state and then builds a `ChallengeDialogData`.

The port:

- **`user_challenge_actions` Drift table** (schema v43, a preserved
  local-authority table) — one row per challenge action, with `id`, `userId`,
  `actionType`, `resourceId`, and `time`. `UserChallengeActionDao` provides
  `insert` and `countByUserAndType`. The table is preserved because a sync
  action the user recorded but has not yet uploaded must survive a schema
  bump, or the challenge dialog's "sync completed" check would silently flip
  back to false. A preservation test pins it in `migration_test.dart`, and
  the table is listed in its `covered` set.
- **`ActivitiesRepository`** gains `recordSyncUserChallengeAction(userId)`
  (port of the Kotlin method the dashboard calls right before the manual-
  sync flow begins, not on auto-sync) and `hasUserCompletedSync(userId)`
  (counts `"sync"` actions in the table). The constructor takes the new
  `UserChallengeActionDao`.
- **`ChallengeEvaluator`** (`lib/providers/challenge_provider.dart`) — the
  port of `evaluateChallengeDialog`. It holds the challenge course id, the
  campaign window, the prompt window, and the participating-server URL list
  (mirrors `ServerConfigUtils.getChallengeServerUrls`). `evaluate()` checks
  the gating (guest, window, server) and returns `ChallengeDialogData` or
  null. `courseStatusString` (public, port of `getCourseStatusString`)
  returns the course name with a "terminado" marker when `current == max`,
  the substring the dialog's completion check keys on.
- **`ChallengeDialog`** (`lib/ui/components/challenge_dialog.dart`) — a
  `StatefulWidget` (no Riverpod; the data arrives via constructor params)
  that renders the progress bar, the three task rows, the markdown
  earnings content, and the action button. The action routes to the course
  (`/courses/<challengeCourseId>/take`), voices (`/life/voices`), or sync
  center (`/sync-center`) depending on which task is next. The congratulations
  variant fires once: `hasShownChallengeCongratsProvider` (a
  `NotifierProvider` backed by `PlanetPrefs`) suppresses every subsequent
  appearance, matching `ChallengePrompter`'s `hasShownCongrats` guard.
- **`home_screen.dart`** — `_evaluateChallenge` is wired to the session
  listener via `addPostFrameCallback`, so it fires once on dashboard load
  when a non-guest user is present.
- **`dashboard_sync_provider.dart`** — `syncAll()` calls
  `recordSyncUserChallengeAction` at the start of a manual sync, so the
  "sync completed" task lights up after the user syncs.

The hardcoded English strings in the dialog (`Progress: N%`,
`Course: Not started`, `Sync completed`, `N of 5 Daily Voices`) are
localized: four new keys (`progressLabel`, `courseNotStarted`,
`syncCompletedChallenge`, `voicesProgress`) added to `app_en.arb` and
generated into `AppLocalizations`. The derived locales fall back to English
for these, matching the existing pattern for resource-viewer strings.

The 1379-test suite passes (16 new tests: 5 challenge-action repository
tests, 10 challenge-provider tests, 1 migration preservation test),
`flutter analyze` clean, `dart format` clean.

---

## Phase 67 — tags and collections (`tags`)

The Kotlin's collections feature is a single CouchDB `tags` database holding
three doc shapes: parent definitions (`isAttached = false`, scoped by `db` =
`resources`/`courses`), child definitions (`attachedTo` lists parent ids), and
link rows (`name` empty, `tagId` + `linkId` + `db`) that attach a tag to a
resource or course. `CollectionsFragment` (resources) and
`CollectionsDialog` (courses) let the user pick tags — singly, or many when
`MainApplication.isCollectionSwitchOn` is on — and the selected set filters
the list (`filterLocalLibraryByTag`/`filterCourses` resolve the tag ids into
link ids and keep matching rows) and is captured into `search_activity`'s
filter JSON (`TagEntity.getTagsArray`).

The port now:

- **`tags` Drift table** (schema v40, a pure CouchDB cache — link rows make
  the table a merge of definitions and attachments, so it is not a
  local-authority table): one row per doc, with `StringListConverter` for
  `attachedTo`. Insertion is an upsert keyed on the doc `_id`; the Kotlin's
  `insertTags` likewise rewrites a doc's row in place.
- **`TagDao`**: `parentTags(db)` / `childTags(parentIds)` for the dialog's
  parent list and the expand-to-children lookup, `tagsByIds` for joining
  link rows to their definitions, `linkTagsForLinkIds` for the resource/
  course id → tags map, and `linkIdsForTagNames` — the port of the courses
  controller's name-to-link-id resolution.
- **`TagsRepository`** — `getTagsWithChildren` (the pair the dialog shows),
  `getTagsForResources`/`getTagsForCourses` (each row's named tags, deduped),
  `getLinkIdsForTagNames`, and `sync` — a paginated `_all_docs` walk through
  `AdaptiveBatchProcessor` that skips `_design/` docs and treats a bare
  string `attachedTo` like the array form, then runs `deleteNotIn`. The
  Phase 52/66 `hadBatchFailure` rule applies here too: a failed batch skips
  the cleanup so valid tags survive an incomplete walk.
- **`CollectionsDialog`** (`ui/resources/collections_dialog.dart`) — the
  shared dialog for both screens, parameterized by `dbType`: parents listed
  with an expand chevron for children, a debounced (300 ms) search box, a
  "Select Many Collections" switch flipping single-select into
  checkbox multi-select, and the OK button enabling once something is
  checked. An empty tag cache toasts "No data available" and dismisses,
  matching the Kotlin's `showDialog` path. The multi-select flag is the
  `collectionMultiSelectProvider` StateProvider, standing in for the Kotlin's
  static `MainApplication.isCollectionSwitchOn`.
- **Screen wiring** — the resources screen gains the collections action
  button (badged when tags are selected), a dismissible chip per selected tag
  (`refreshTagChips`), and the any-of selected-tags filter applied after the
  regular filter; its `dispose`-time `saveSearchActivity` now records the
  selected tag couch ids, so Phase 65's `tags: []` placeholder is gone. The
  courses screen gains the same button, a "Selected: …" label under the
  filter bar (the Kotlin's `tvSelected`), the same filter over
  `filteredSortedCoursesProvider`'s rows, and tag capture in its own
  `saveCourseSearchActivity`. `resourceTagsProvider`/`courseTagsProvider` map
  each listed id to its tags; the family key is the ids joined (identity
  keys would refetch on every rebuild).
- **Sync wiring** — `ResourceSyncNotifier` and `CourseSyncNotifier` each
  fire `tagsRepository.sync` alongside their table syncs, so a library or
  course sync refreshes the tag cache too. Dashboard sync areas pick it up
  for free through those notifiers.

Fifteen tests cover it: insert mapping (design-doc skip, string vs array
`attachedTo`, link rows), `getTagsWithChildren` grouping and db scoping,
resource-tag join with dedup, name-to-link-id resolution (including the
unknown-tag and empty cases), the sync walk with prune, empty-server prune,
a failed count lookup returning `SyncFailed`, the `hadBatchFailure` cleanup
skip, and the dialog's single-select return, child expansion, debounced
filter, multi-select OK, and empty-cache toast-and-dismiss paths.

---

## Phase 68 — achievements (`achievements`)

The Kotlin's "My Achievements" ledger — one `achievement` CouchDB document per
user keyed `"userId@planetCode"`, carrying goals/purpose/header text, the
achievements and references lists as JSON columns, and a resume/CV file whose
bytes ride a CouchDB attachment. `UserRepositoryImpl.initializeAchievement`
creates the row on first edit; `AchievementUploader` (and the partial user
profile update from `updateProfileFields`) carries them out; the sync-in
bulk-inserts `Achievement.serialize` rows, then a chained
`updateAchievementList` downloads the `resume.pdf` attachment per row.

The Flutter side had no `achievements` table at all — the Life tile toasted
"coming soon".

The port now:

- **`Achievements` Drift table** (schema v41, preserved in
  `localAuthorityTables` on a Kotlin-faithful column set — `achievementsJson`,
  `referencesJson`, `linksJson`, `otherInfoJson`, `dateSortOrder`, `createdOn`,
  `username`, `parentCode`, `resumeFileName`, `uploaded`). Its preservation
  test in `migration_test.dart` round-trips an un-uploaded ledger through a
  schema bump.
- **`AchievementDao`**: `getById`, upsert-by-id, `pendingUploads` (the Kotlin
  `_id NOT LIKE 'guest%'` guard plus `isUpdated = true`, minus
  `username LIKE 'guest@%'` — the Kotlin lets it through, so no rows can
  match both), `markUploaded` (records couchId/rev and clears the flag), and
  a `upsertAll` for the sync bulk path.
- **`AchievementsRepository`** — ports `initializeAchievement` (`getById` or
  create), `updateAchievement` (marks the row unsynced, like the Kotlin
  `isUpdated = true`), `serialize` (rebuilds the document with
  `dateSortOrder` defaulted to `none`, `sendToNation` as a real bool, the
  guest guard skipped by the backlog query), the sync-in bulk path
  (`designDocumentPattern` skip, bool-or-string `sendToNation` tolerated, and
  an upload-safe round trip — a synced row serializes back to the same
  document), plus the `achievementsArray`/`referencesArray`/`resourcesOf`
  helpers the screens parse JSON columns through.
- **`AchievementFiles`** (`lib/core/files/`) — the `<ole>/cv/<name>` slot the
  Kotlin uses for both the edit-time copy and the sync-time download, with the
  same zero-length guard `ResourceFiles.existingFileFor` has.
- **`AchievementsUploader`** (`type: 'achievements'`) — the outbox half of
  `AchievementUploader`: `queuePending` puts the serialized document into the
  `outbox`; the handler PUTs it, records the returned rev via `markUploaded`,
  then best-effort PUTs the resume bytes to the `resume.pdf` attachment key
  only when the file is on the device — both halves matching the Kotlin, whose
  attachment upload is fire-and-forget.
- **`AchievementsScreen`** — the Kotlin's three-tier body (goals, purpose,
  achievements-header card) with the list cards below, and the CV card only
  when the named file is on disk (`AchievementFragment.setupCv`), routed at
  `/life/achievements` under the `life` branch; the tile under `LifeScreen`
  now opens it.
- **`EditAchievementScreen`** — the port of `EditAchievementFragment`: name/
  birth-date/place validation (first name, last name, birth date required,
  the toast listing the missing labels), the achievements/add and
  references/add dialogs (title or name required), the resource multi-select
  built from the whole resources table (the Kotlin `readResources`), the
  send-to-nation switch, and the CV pick/delete/preview chain with the
  pdf-only gate, plus `cv_viewer_dialog.dart` for the preview.
- **Provider wiring** — `achievementsRepositoryProvider`,
  `achievementEntryProvider` (the auto-initializing ledger row the screen
  reads and the form saves through), `achievementActionsProvider` (`save` =
  ledger update + `UserRepository.updateProfileFields` + `queuePending`), and
  the `outboxDrainerProvider` handler registration.

Thirty achievement arb keys were added to `app_en.arb` and translations to
`ar`/`fr`/`ne`/`so`, matching the Kotlin `values-*` strings (`myAchievements`,
`editAchievement`, `addAnAchievement`, `addAReference`, `myGoals`, `myPurpose`,
`noGoalAdded`, `noPurposeAdded`, `noAchievementAdded`, `noReferencesAdded`,
`sendToNation`, `saving`, `achievementSaved`, `titleIsRequired`,
`selectPdfOnly`, `currentCv`, `viewCv`, `deleteCv`, `uploadCvLabel`,
`addMaterials`, `labelAddReferences`, …). The knowledge that
`AchievementUploader` always PUTs to the literal `resume.pdf` attachment key
(and that Kotlin's `Achievement.serialize` falls back `dateSortOrder` to
`none`) came from target-text review, and the four failing guesses along the
way — column schema fidelity, the `notLike` drift API, and the locale key
casing — were caught by analyzer/locale-coverage tests before the suite ran
clean.

Tests cover the round trip (repository upsert semantics, the unsynced flag
reset, serialize against gleaned Kotlin doc shape, the guest guard), the DAO
query portfolio, the uploader's queue/handler and resume attachment, the
`initState` l10n trap, and widget tests for the empty state, the populated
body, and the route push. The sync-in path is exercised end-to-end: a row the
server delivers deserializes, uploads, and serializes back to the same
document.

---

## Phase 69 — the ARB derivation tool stops deleting translations

`tool/arb_from_strings_xml.dart` (Phase 47) derived the four locale files from
the Kotlin `strings.xml` by regenerating each one from scratch. That was fine
on the day it was written, when the files contained nothing but its own output.
It is not fine now: Phases 54–58 translated **17 keys per locale by hand** —
the resource viewer's per-media-type error states (`videoFileNotFound`,
`unableToLoadPdf`, `emptyFile`, `htmlEntryNotFound`, …) plus `errorOccurred`
and `openingResource`. Those have no counterpart in `strings.xml` at all, so
the tool cannot derive them, and regenerating deleted every one.

Measured before fixing, by running the tool as documented:

```
ar: committed=246 regenerated=230 LOST=17
fr: committed=247 regenerated=231 LOST=17
ne: committed=246 regenerated=230 LOST=17
so: committed=246 regenerated=230 LOST=17
```

68 translations, removed with no error and no warning. The advice the tool's own
header gave — "add such strings to the Kotlin `strings.xml` instead" — does not
apply to them: there is no Kotlin string to add. And the header called the
script "safe and idempotent" in the same breath as admitting hand-translations
would be lost, which is a contradiction that made the trap easy to miss.

Two changes:

- **It merges.** Existing keys keep their value *and their position*, so
  re-running produces no diff for them; only keys the file lacks are appended,
  in template order. Position matters because these files are not in template
  order — reordering them would bury a one-key change under a whole-file diff.
  `@key` metadata blocks (placeholder declarations `gen-l10n` needs) are carried
  over verbatim; an earlier cut of this fix dropped `@currentCv` by filtering
  non-String values, which would have silently un-declared a placeholder.
- **It writes literal UTF-8.** The escaping existed to match `app_es.arb`'s
  original style, but the locale files were since rewritten as literal UTF-8, so
  escaping meant every run rewrote all four files — 548 insertions / 636
  deletions of pure churn, and unreviewable Arabic.

Because existing values now win, the tool can no longer *correct* a translation
already in a file; delete the key first if the XML has a better one. That is
recorded in the header.

Verified by running the tool twice: the first run adds one genuinely derived key
(`update`, present in all four `values-*/strings.xml` and previously missing)
and changes nothing else; the second run produces an empty diff.

### Test

`locale_coverage_test.dart` gains `hand-authored translations survive in every
derived locale`, pinning the 12 Flutter-only keys across `ar`/`fr`/`ne`/`so`.
This tests the artifact rather than the tool, which is the right place for it:
whatever future change makes regeneration destructive again, the suite fails
instead of four locales quietly reverting to English. Verified in both
directions — removing one key fails the test with the key named.

The 1279-test suite passes, `flutter analyze` clean, `dart format` clean,
`flutter build apk --debug` green.

---

## Phase 70 — resource list sort toggles, and the 2026-08-24 upstream batch

The five upstream commits after the Phase 66 audit window (2026-08-24) are
four refactors and one behavioural fix:

- `d64e98a` (submissions repository detail view modelling), `9a29130`
  (events repository detail view modelling), `862fb1c` (realtime table
  flowing), `2ded20c` (fragment manager back stack listening) —
  ViewModel/repository reshuffling with no behaviour change; the port's
  Riverpod providers already sit where the Kotlin is moving logic to.
- `14a9f14` (#15941) flips `ResourcesViewModel.isTitleAscending`'s initial
  value from `true` to `false`, so the first title-sort tap orders A→Z
  instead of Z→A. Behavioural — and it lands on a feature the port had
  never built: the `orderByDateButton`/`orderByTitleButton` pair in
  `ResourcesFragment`'s bottom sheet, which drives
  `ResourcesViewModel.toggleSortOrder`/`toggleTitleSortOrder`. The port's
  resources screen had no sort affordance at all.

This phase ports the pair rather than just the fix:

- `ResourceSortState` + `resourceSortProvider`
  (`providers/resources_providers.dart`) mirror the ViewModel's
  `sortMode`/`isAscending`/`isTitleAscending` fields. Each toggle switches
  the mode *and* flips that mode's own direction flag, so switching between
  modes never disturbs the other mode's direction — the Kotlin holds one
  flag per mode, and so does this.
- `applyResourceSort` is the `applyCurrentSort` port, run on the filtered
  list at build time so a sync pushing fresh rows into the Drift stream
  keeps the chosen order — the same thing the Kotlin gets by re-sorting on
  every `getLibraryListModels` refresh. The title key is the lower-cased
  title with a null title sorting as ""; the date key is `createdDate`.
  Kotlin's `sortedBy` is stable and Dart's `List.sort` is not, so equal
  keys are tie-broken on the original index to keep the stream order
  between them.
- A sort `IconButton` in the resources app bar — badged once a mode is
  active, the same treatment the collections and filter buttons get — opens
  a bottom sheet with the two options; the active one carries an up/down
  arrow for its direction. Tapping one toggles, pops the sheet, and
  `jumpTo(0)`s the scroll controller both list layouts now share — the port
  of `recyclerView.scrollToPosition(0)`.
- The `sortResources` tooltip string is new in `app_en.arb`; the derived
  locales fall back to English for it until translated. The option labels
  (`orderByTitle`/`orderByDate`) already existed in all five locales,
  derived from `strings.xml` in Phase 47.

Tests: `test/providers/resource_sort_test.dart` pins the toggle semantics —
first title toggle A→Z (the `14a9f14` fix), first date toggle newest-first,
per-mode direction independence, case-insensitive title keys with nulls
first, stable ordering on equal keys, and `none` leaving the stream order
alone. Two widget tests on the resources screen drive the sheet through
both toggles in each mode and assert the rendered row order. The 1287-test
suite passes, `flutter analyze` clean, `dart format` clean.

---

## Phase 71 — member detail screen, and real member names in the list

`MembersDetailFragment` — the profile card opened by tapping a member in the
team members list (also the destination for voice authors and community
leaders in the Kotlin) — had no counterpart. Worse, the port's
`team_members_screen.dart` rendered each member as a plain `ListTile` titled
by the raw `userId` string, with no tap handler at all. A member's full name,
email, date of birth, language, phone, level, visit count, and last login were
all unreachable.

This phase ports the screen and fixes the list:

- `TeamLogDao.teamVisitsForUsers` / `lastTeamVisit` port the two Kotlin
  team-log queries the member card reads (`getTeamVisitsForUsers` for the
  per-team visit count, `getLastVisit` for the most recent visit). The
  `offline_activities` half (`lastVisit`/`offlineVisitCount`) was already
  there from the activity-log slice.
- `memberDetailProvider` joins the three sources the way the Kotlin's
  `getJoinedMembersWithVisitInfo` joins them for one member: the `users` row
  by id (`UserDao.getById`), the per-team visit count, and the last login
  timestamp. It returns `null` for a member whose user document is not in
  the local cache, the same state the Kotlin shows as a blank card.
- `MemberDetailScreen` (`/life/teams/:teamId/members/:userId`) renders the
  profile photo, full name, leader badge, and the labelled rows for
  username, email, date of birth (cut at `T`), language, phone, level,
  number of visits, and last login. Empty/blank fields hide — the port of
  `setFieldOrHide`, which also hides the wrapping parent, so a member with
  no email set shows no email row.
- `_MembersList` is now a `ConsumerWidget` that resolves each member's
  display name from the cached `users` row (first + last name, falling
  back to the username, then the raw id — the Kotlin's `MembersAdapter`
  fallback chain) and navigates to the detail screen on tap. A new
  `userByIdProvider` backs the per-row lookup.
- `memberDetail`, `numberOfVisits`, and `noLogoutRecord` are new in
  `app_en.arb`; the derived locales fall back to English until translated.

Tests: `test/ui/member_detail_screen_test.dart` covers the populated-fields
render (scrolling the below-fold visit/login rows into view first), the
leader badge, the hide-empty-fields behaviour, the visit count + formatted
last login, and the unknown-member state. `team_members_screen_test.dart`
gains a name-resolution test and a tap-opens-detail-route test. The
1294-test suite passes, `flutter analyze` clean, `dart format` clean.

---

## Phase 72 — add-resource screen, team leader actions, and member-detail wiring

Three features land together: the resource creation/editing form, the team
member overflow menu (remove / make leader / leave), and the member-detail
screen wired to two more call sites (voice authors and community leaders).

**AddResource** (`AddResourceActivity` + the file-pick half of
`AddResourceFragment`):

- `ResourcesRepository.saveLocalResource` / `updateLocalResource` /
  `resourceTitleExists` port the three Kotlin repository methods. The
  create path builds a `MyLibraryTableCompanion` from the form fields and a
  picked file path, marks it `resourceOffline`, and shelves it for the
  user unless it is a private team resource. The edit path loads the row,
  applies the field changes via `toCompanion(false).copyWith(...)`, and
  upserts. `LocalResourceRequest` is the port of the Kotlin data class.
- `MyLibraryDao.countByTitle` ports the duplicate-title guard query.
- `AddResourceScreen` (`/resources/add`) renders the full metadata form:
  title, author, year, description, publisher, license, levels (chips),
  subjects (chips), resource-for (chips), language / open-with / media /
  resource-type (dropdowns), and a private-resource toggle for team
  resources. Create mode picks a file first (`FilePicker.pickFiles`); edit
  mode (reached from the resource detail screen's new edit button)
  prefills from the existing row. The string arrays from `strings.xml` are
  carried as const lists. The audio/video/image capture paths need platform
  channels the port has not built, so only the file-pick path is ported —
  the most common one.
- A FAB on the resources screen opens the form; the resource detail
  screen gains an edit action.

**Team member leader actions** (the `MembersAdapter` overflow menu):

- `TeamsRepository.removeMember` (same tombstone path as `leave`) and
  `updateTeamLeader` (flip `isLeader` on every membership, mark dirty, return
  the changed rows) port the two Kotlin methods.
- `TeamMembershipActions.removeMember` / `makeLeader` enqueue the tombstone
  or the updated documents through the outbox, exactly as `leave` and
  `respond` do.
- The members list gains a `PopupMenuButton` per member card: Leave for the
  current user, Remove / Make leader for a leader acting on another member.
  The `canManage` flag (already computed by the parent) is threaded into
  `_MembersList`.

**Member-detail wiring** — two more call sites for the Phase 71 screen:

- Tapping a voice author's name navigates to the member detail route.
- Tapping a community leader navigates to the member detail route (the
  previous bottom-sheet detail is replaced, matching the Kotlin's
  `CommunityLeadersAdapter.showLeaderDetails` → `MembersDetailFragment`).

`memberDetail`, `numberOfVisits`, `noLogoutRecord`, `levels`, `leave`,
`remove`, `makeLeader`, `leftTeam`, `memberRemoved`, `leaderUpdated`,
`editResource`, `resourceUpdated`, `resourceAddedToTeam`,
`descriptionIsRequired`, `levelIsRequired`, `subjectIsRequired`,
`selectFile`, `fileSelected`, `privateResource`, `saveChanges`,
`selectOpenWith`, `selectMedia`, `selectResourceType`, and `linkToLicense`
are new in `app_en.arb`; the derived locales fall back to English.

Tests: `resources_repository_test.dart` gains 5 tests (create, duplicate
rejection, private team resource, edit, title-exists). The leaders screen
test is updated from the bottom-sheet assertion to the navigation assertion.
1299 tests pass, `flutter analyze` clean, `dart format` clean.

---

## Phase 73 — standalone WebView, course step exam/survey buttons, team leaderboard

Three features land together: a standalone web browser, the exam/survey
buttons on course steps, and the team leaderboard from the upstream
`14880` feature branch.

**Standalone WebView** (`WebViewActivity`'s external-URL half):

- `WebViewScreen` (`/web-view`) loads an external URL in a
  `webview_flutter` WebView with JavaScript disabled (matching the
  Kotlin's `isLocalResource = false` default), a progress bar, a reload
  action, and an error/retry view. The local-resource HTML viewer was
  already ported by the resource viewer (Phase 54); this is the
  standalone browser that community services and other external links
  open.
- Community services wiring: the stub snackbar ("would open in WebView")
  is replaced with `context.push` to the new screen.

**Course step exam/survey buttons** (`CourseStepFragment`'s
`btnTakeTest`/`btnTakeSurvey`):

- `stepExamProvider` / `stepSurveysProvider` load the exam or surveys
  attached to a step (via `ExamDao.getByStepId` / `SurveyDao.getByStepId`,
  the latter newly added).
- `_StepContent` is now a `ConsumerWidget` that watches the providers
  and renders "Take test" and "Record survey" cards when the step has an
  exam or surveys, navigating to the exam or public-survey route on tap.

**Team leaderboard** (from `14880-add-team-and-enterprise-leaderboards`):

- `TeamLeaderboardCalculator` is a port of the Kotlin's pure-logic
  calculator: given members, course progress, and survey completion
  timestamps, it ranks by courses completed then surveys completed (both
  descending), with an optional period-start filter for "this month".
- `TeamLeaderboardScreen` resolves team members' display names and visit
  counts (via Phase 71's `teamVisitsForUsers`), pre-computes course
  progress per user (`courseProgressSummary`), and survey completion
  timestamps (`getSurveySubmissionsByUser`, newly added to
  `SubmissionDao`). An all-time / this-month `SegmentedButton` re-runs
  the calculation. The current user's card is highlighted; the top three
  carry medal emojis.
- `SurveyDao.getByStepId` and `SubmissionDao.getSurveySubmissionsByUser`
  are newly added DAO methods the leaderboard and step buttons read.

`leaderboard`, `allTime`, `thisMonth`, `coursesCompleted` (placeholder),
`surveysCompleted` (placeholder), `takeTest`, and `recordSurvey` are new
in `app_en.arb`; the derived locales fall back to English.

Tests: `team_leaderboard_calculator_test.dart` (4 tests). The services
screen test is updated from the snackbar stub to the web-view navigation
assertion. 1303 tests pass, `flutter analyze` clean, `dart format` clean.

---

## Phase 74 — voice reactions and task comment threads (not ports), and a broken round trip

Two features landed here that are **not** Kotlin ports, which makes them the
first work in this document that does not advance Kotlin→Flutter parity:

- **Emoji reactions on voices** — issue #13357, *open*, with PR #13415 *open and
  unmerged*. Nothing in `app/` mentions reactions; `grep -rni "reaction\|emoji"`
  over the Kotlin sources finds no match.
- **Team task comment threads** — issue #15112, *open*, with PR #15820 *open and
  unmerged*.

Both are feature requests implemented directly in Flutter. That is legitimate
work, but it changes what "parity" means for these two items and removes the
safety net every earlier phase relied on: there is no Kotlin implementation to
check the port against, so behaviour and wire format are unverifiable except
against the issue text.

Worth knowing about the reference implementations, since they will land in the
shipping app eventually:

- Kotlin PR #13415 stores `reactions` as a `String?` on `RealmNews` holding
  JSON (`{emoji: [userId, …]}`), and **never serializes it** — no change to the
  news serializer, so reactions stay on the device that made them. The port
  syncs them, so it is ahead of that PR rather than matching it.
- Issue #15112 asks for comment threads on team tasks **and meetups**. Only the
  task half is wired (`team_tasks_screen`); the meetup card has no thread. Half
  the issue.

### The round trip was broken

`serializeNews` writes reactions into the nested `news` sub-object:

```dart
'news': { …, if (row.reactions != null …) 'reactions': row.reactions },
```

while `NewsMapper.fromDoc` read them from the **top level**:

```dart
reactions: Value(JsonUtils.getStringOrNull('reactions', doc)),   // wrong level
```

So a reaction never reached another device — and the more serious half, the
mapper writes its companion on *every* pull, so a document whose reactions it
could not find wrote `Value(null)` straight over the local column. A user's own
reaction disappeared on their next sync. That is the same failure the Phase 56
security-data fix addressed (`Value(null)` where the value should have been left
alone), in a different table.

Each side had its own passing test, which is exactly why this survived: the
serializer test checked the document, the mapper test checked the column, and
nothing ran the two together. The fix reads from `nested`, matching `sharedBy`
three lines above it, and the new coverage is a genuine round trip —
`toggleReaction` → `serialize` → `fromDoc` → compare. Verified in both
directions: three tests fail on the pre-fix mapper.

### A trade-off left unresolved, and pinned

`serializeNews` omits `reactions` when the column is empty, so an absent key
cannot be distinguished from "no reactions". That leaves two imperfect options:

- Clear on absence (what it does): a removal propagates correctly, but a pull
  landing between a local reaction and its upload discards it.
- Preserve on absence (`Value.absent()`, the Phase 56 trick): a fresh local
  reaction is safe, but a *removal* never reaches another device.

Clearing is kept, because losing a removal is the worse of the two and the race
is narrow. Resolving it properly means always writing the key so absence is
unambiguous — a wire-format change, and there is no shipping Kotlin behaviour to
agree with yet. `news_mapper_test.dart` pins the current behaviour with that
reasoning attached, so the next person meets a decision rather than a surprise.

The 1310-test suite passes, `flutter analyze` clean, `dart format` clean,
`flutter build apk --debug` green.

---

## Phase 75 — chat full-conversation search (upstream audit + one port)

This phase is an upstream audit plus one behavioural port rather than a new
screen. Twenty-eight commits between the Phase 70 boundary (`14a9f14`,
2026-08-24 04:31) and the master tip (`7f9f80cd4`, tag v0.66.63,
2026-08-24 10:29) were reviewed against the Flutter port. Twenty-seven of
them need no port — they are refactors, Kotlin-idiom cleanups, performance
rewrites, CI/version bumps, or land on mechanisms the Dart port already has
(gradient drawables, `@`-split parsers, regex consolidations, BP-number
parsing, hex formatting where Dart's `String.toRadixString` is already
locale-independent). The one genuine gap:

- **Chat repository search** — `ChatViewModel.searchChats(query, isFullSearch,
  isQuestion)`. (This was first written up as commit `62908f134`, which does not
  resolve in this repository while neighbouring citations like `aa24dfa6c` and
  `2ec7e3187` do; the port is real either way, so the reference is the Kotlin
  symbol, which outlives a rebase.) The Kotlin
  `ChatRepository` gained a `ChatSearchMode` enum (`title`/`question`/
  `response`), a `searchChats()` with **ranked** matching (a prefix hit
  outranks a substring hit; a hit in the first conversation — which is the
  title — outranks one in a later turn), a `sortChats()` ordering by
  `max(createdDate, updatedDate)` descending rather than by id, and
  `Utilities.normalizeText` (NFD-decompose, strip combining marks, lowercase)
  so an accented search finds its plain match. The port had only a flat
  `title.contains` filter and ordered the DAO's id-desc output as-is.

### The port

The search logic is **pure** — it lives in a top-level `searchChatsForMode`
(and `sortChatsByRecency`) in `lib/repository/chat_repository.dart`, and the
provider calls that directly rather than `ref.watch(chatRepositoryProvider)`.
That matters because the repo provider transitively watches
`planetPrefsProvider`, which is `UnimplementedError` in the widget-test
harness — a `filteredChatHistoryProvider` that reached for the repo would
throw on every non-empty search and render "Chats could not be loaded"
instead of the no-results message. The repo's `searchChats` impl delegates
to the same function so the interface stays satisfied and the repository
tests still exercise it through the repo.

### Dart has no NFD normalizer

The Kotlin normalizer is `Normalizer.normalize(str, NFD)` then a
`\p{InCombiningDiacriticalMarks}` strip. Dart's core library has neither:
it has no NFD decomposition, and its `RegExp` rejects the
`InCombiningDiacriticalMarks` block name (`FormatException: Invalid property
name`, even with `unicode: true`). A precomposed Dart string ("café", NFC)
would survive the strip untouched, so an accented search would miss its
plain match — the original port's `_normalizeText` had this bug, it just was
never tested.

`lib/core/utils/text_normalize.dart` bridges both gaps by hand: a
decomposition table for the Latin-1 Supplement block (U+00C0–U+00FF, covering
French and Spanish accented vowels, the cedilla, and ñ) rewrites a
precomposed letter to base + combining mark, then any code point in the
U+0300–U+036F combining-marks range is dropped in the same pass. Both a
precomposed "café" and an already-decomposed "cafe\u0301" normalize to
"cafe". The coverage does not extend past Latin-1 Supplement — the app's
other locales (Arabic, Nepali, Somali) use scripts with no combining-mark
diacritic stripping need, and Latin Extended-A/B is not present in the
languages this app ships.

### UI

`chat_history_screen.dart` gained a "Full conversation response" checkbox
(the Kotlin `fullSearch`) and, when it is on, a `SegmentedButton` switching
between `question` and `response`. Off means title-only search (the
default), matching the Kotlin. Two new ARB keys (`fullConversationResponse`,
`response`) were added to `app_en.arb`; `question` already existed (the
feedback "Question" label) and is reused, matching the Kotlin's shared
string. The four other locales fall back to English until
`tool/arb_from_strings_xml.dart` is run, which is how every prior phase
added English strings.

The 1324-test suite passes (9 new — search modes, ranked matching,
diacritics, recency sort, the toggle UI, the response-mode search),
`flutter analyze` clean, `dart format --set-exit-if-changed` clean.

## Phases 76–77 and two unnumbered ports

The harvest shipped these and left them out of this document; recorded here so
the phase numbering stays contiguous.

- **Phase 76 — courses multi-select shelf actions.** Ports the batch add/leave
  from Kotlin's `CourseSelectionController`: long-press enters selection mode,
  select-all toggles every visible tile, and one action writes shelf membership
  for the whole batch with a count snackbar. It also fixes a real race it found
  on the way: `CoursesScreen` resolved the user with `ref.read`, which returned
  null on first access and silently no-oped the entire batch. The screen now
  watches `sessionProvider` in `build`, matching its sibling screens.
- **Phase 77 — course cover image and markdown description.** Course detail and
  take-course render descriptions through `MarkdownBody` instead of `Text`, and
  the detail screen gains the cover banner (`CourseDetailFragment.setCourseCover`,
  `CourseStepFragment`'s `prependBaseUrlToImages` + `setMarkdownText`). The
  interesting part is `CourseMarkdownBody`'s `imageBuilder`: CouchDB attachments
  sit behind Basic auth and `Image.network` cannot send the header, so relative
  `resources/<id>/<file>` paths resolve against the server's `/db` root and are
  fetched as authenticated bytes through `PlanetApi.getBytes` — the same path
  `profileImageProvider` already uses. A miss shrinks to nothing rather than
  showing a broken-image icon, matching the Kotlin's silent `<img>` fallback.
- **Blood-pressure validation** (no phase number). Kotlin's
  `HealthExaminationActivity.validateFields` checks BP in three tiers: contains
  `/`, splits into two parts, and `sys` 60–300 / `dis` 40–200. The Flutter
  validator had only the format check, so `400/80` or `abc/def` passed. Verified
  against the Kotlin source: the range expression is
  `sys < 60 || dis < 40 || sys > 300 || dis > 200`, which the port now matches
  exactly.
- **Personal-note attachments** (no phase number, #16070).
  `PathResourceViewerScreen` routes a personal note's stored path to the pdf /
  image / audio / video renderer by extension, mirroring
  `PersonalsAdapter.openResource`. Personal attachments are not `MyLibrary`
  rows, so they cannot go through the id-based `ResourceViewerScreen`; this is
  the `TOUCHED_FILE` / `isFullPath=true` entry point. No WebView is involved,
  so the local-files-only rule that governs the HTML viewer does not apply here.

## Phase 78 — one `normalizeText`, not two

Phase 75 added `lib/core/utils/text_normalize.dart` with a hand-written Unicode
decomposition table, described in its own header as bridging a gap in Dart:
"Dart's core library has no NFD normalizer and its `RegExp` does not accept
`\p{InCombiningDiacriticalMarks}`, so this bridges both gaps by hand."

Both halves of that are true, and the project had already solved it.
`lib/core/utils/text_utils.dart` has carried `normalizeText` since the resource
search landed — `removeDiacritics(value).toLowerCase()`, on the `diacritic`
package that `pubspec.yaml` lists for exactly this purpose ("stands in for
`java.text.Normalizer` NFD"). Both files documented themselves as the port of
the same Kotlin function, `Utilities.normalizeText`.

Two implementations of one function would be untidy but harmless if they agreed.
They do not — measured on 15 accented samples, they disagree on 7:

| input | hand-written table | `diacritic` package |
|---|---|---|
| `Māori` | `māori` | `maori` |
| `Łódź` | `łodź` | `lodz` |
| `Škoda` | `škoda` | `skoda` |
| `Çağrı` | `cağrı` | `cagri` |
| `Ærø` | `ærø` | `aero` |

The table covers precomposed Latin vowels — the common French and Spanish
accents its tests exercise — and misses macrons, Eastern European letters,
Turkish dotless/breve forms and Nordic ligatures. Because chat search used the
new function while resource and course search use the old one, the same query
folded two different ways in one app: searching `skoda` found a resource titled
`Škoda` but not a chat conversation about it.

Nothing pinned the narrower behaviour — the new file's tests only asserted cases
where the two agree — and the function was used for in-memory filtering rather
than a persisted `*Normalized` column, so there was no stored data to migrate:

- `text_normalize.dart` and its test file are deleted.
- `chat_repository.dart` imports `text_utils.dart`.
- The deleted tests' cases move into `text_utils_test.dart`, which had no
  `normalizeText` coverage at all despite being the older implementation, plus a
  `folds beyond the common French and Spanish accents` case pinning the five
  divergences above. That is the guard against a narrower reimplementation
  arriving again.

The 1360-test suite passes, `flutter analyze` clean, `dart format` clean,
`flutter build apk --debug` green.

## Phases 82–89 — settings, health, and dashboard gaps

The harvest shipped these without write-ups; recorded here so the numbering
stays contiguous. All eight are Kotlin ports, checked against the named
symbols.

- **Phase 82 — text size and reset app** (`SettingsActivity`). Three text
  scales matching the Kotlin's `floatArrayOf(0.85f, 1.0f, 1.15f)`, applied as a
  `MediaQuery` `textScaler` override. Reset app is the destructive one and is
  built correctly: a Yes/No confirmation ported from `clearDataButtonInit`,
  then `AppDatabase.clearAllData()` (a batched `DELETE FROM` over `allTables`,
  the drift equivalent of `RoomDatabase.clearAllTables()`) plus
  `PlanetPrefs.clearAllData()`, which calls `_secureStorage.deleteAll()` so the
  stored password and PIN go with it rather than outliving the server they
  belong to. `onboardingComplete` is deliberately kept. Undelivered outbox rows
  are wiped along with everything else, matching the Kotlin — that is what the
  user asked for, unlike a schema bump, where `localAuthorityTables` exists to
  protect exactly those rows.
- **Phase 83 — examination detail dialog** (`my_health_screen`).
- **Phase 84 — full diagnosis list and custom diagnosis chip cloud**
  (`HealthExaminationActivity.showOtherDiagnosis` / `preloadCustomDiagnosis`).
- **Phase 85 — health profile editor.** Decrypts the stored blob, mutates it,
  re-encrypts, and saves — the encryption path is intact and goes through
  `HealthCipher` with `ensureSecurityKeys`. Its field rules are a faithful port
  of `UserRepositoryImpl.updateUserHealthProfile`, including an asymmetry worth
  not "fixing": the six name/email/phone fields are assigned unconditionally
  (so an absent key clears the stored value) while `dob` is preserved when
  absent, because the Kotlin writes the first group as
  `(userData[k] as? String)?.trim()` and the second as `userData["dob"]?.let`.
  Likewise `emergencyContact`/`emergencyContactType` keep their existing value
  when the incoming one is blank, while name/specialNeeds/notes are overwritten.
- **Phase 86 — examination exit confirmation** (`HealthExaminationActivity.finish`),
  as a `PopScope(canPop: false)` intercepting the system back gesture.
- **Phase 87 — inactive-user dashboard** (`InactiveDashboardFragment`), gated on
  `rolesList.isEmpty() && userAdmin != true` per `handleGuestAccess`; guests
  still fall through to the bell dashboard because their access is gated
  per-action.
- **Phase 88 — survey resume from a pending submission**
  (`ExamTakingFragment` reusing `BaseExamFragment.checkId`'s `sub`). The shape
  is right where it matters: `updateSurveyAnswers` updates the existing
  submission row rather than inserting, and answer rows are keyed
  `<submissionId>:<questionId>`, so resuming a survey cannot produce a second
  submission or duplicate answers.
- **Phase 89 — resource detail download button state**
  (`ResourceDetailFragment.setupDownloadButton` / `updateDownloadButtonState`).

Schema went 42 → 43 for `user_challenge_actions`, Phase 81's table. It is a new
*table* rather than a new column, so `createAll` covers it and no hand-written
step was needed; it is preserved, and the existing "every preserved table has a
preservation test" meta-test did its job by forcing the migration test that
comes with it.

## Phase 90 — trim parity in the health profile, and a stale Status claim

Two small corrections to the batch above.

**Three untrimmed fields.** `saveHealthProfile` trimmed
`emergencyContact` and `emergencyContactType` but not `emergencyContactName`,
`specialNeeds` or `notes`, where the Kotlin trims all five
(`(userData[k] as? String)?.trim() ?: ""`). Two consequences: the same typed
input stored differently in the two apps, and a whitespace-only entry survived
as whitespace where the Kotlin stores `""` — so `"   "` read as a note that
exists. Fixed, with a test that fails on the pre-fix code.

**`ChallengeDialog` is no longer dead code.** Three passages still described it
as "built and called from nowhere ... library code waiting for a caller",
including the document's headline Status section, which is the first thing a
reader sees. Phase 81 gave it its caller (`home_screen.dart`, via
`_evaluateChallenge`). `CustomDropdown` genuinely is still uncalled, so only the
`ChallengeDialog` half changed. The Status header and phase footer were also
stale and disagreed with each other — "Phase 81 complete" against
"**Phase**: 80" — while the branch had shipped through 89.

The 1424-test suite passes, `flutter analyze` clean, `dart format` clean,
`flutter build apk --debug` green.

## Phase 91 — first tests for the resource viewer

`ResourceViewerScreen` is 1052 lines and had no tests at all — flagged as the
largest untested surface in the port for five consecutive rounds. Eight now
cover it:

- the loading state and the missing-resource state;
- the app-bar title and its `untitledResourceTitle` fallback;
- the download prompt in each of its branches — a download offered when the
  attachment is absent, nothing offered when no server is configured or the row
  names no attachment, and the stale-offline-flag repair, where a row still
  flagged downloaded whose file has been deleted has the flag cleared so the
  screen can offer the download again. That last one was verified to fail when
  the clearing is disabled, so it is testing the behaviour rather than
  restating it.

### What is not covered, and what was tried

The screen resolves attachments through `ResourceFiles`, which is genuine
`dart:io`, and a widget test's zone is fake-async: real file futures never
progress there, so the screen sits on its `CircularProgressIndicator` and
`pumpAndSettle` spins on that indefinite animation until its **ten-minute**
default expires — a failure that presents as a hang. `runAsync` is the way
through, but the recipe matters: yielding wall-clock time and then pumping
works, while pumping *inside* `runAsync` does not.

That gets the states above under test. What it did not get, at the time, was
rendering an attachment that **exists**. `_TextViewer`, `_CsvContent` and
`_MarkdownViewer` each read the file in their own `initState`, and a test that
writes a real file and pumps one of them stalls indefinitely rather than failing
— the stall is reported against the file rather than the test, and it survives a
best-effort teardown, so an open handle on the temp directory is not the
explanation. Four such tests were written and withdrawn: a suite that stalls CI
is worse than a gap that is written down.

Phase 92 closed the gap by adding the seam that paragraph called for. The three
text renderers now take a `getContent` closure (built from
`resourceContentReaderProvider`, a `ResourceFiles.readTextContent` delegate)
instead of a path, so a widget test overrides the provider with a fixed string
and pumps without a real `File.readAsString`. A real file still has to exist on
disk for the screen's `_getLocalFilePath` to route into the viewer rather than the
download prompt, and that file write runs inside `runAsync` — but the bytes the
renderer displays come from the override. The four withdrawn tests (plain text,
the title row, the CSV table, markdown) now pass; `ResourceFiles.readTextContent`
gets its own three unit tests (absent, present, zero-length).

The three media renderers — `video_player`, `pdfx`, `webview_flutter` — are out
of reach for a more permanent reason: each needs a texture or a platform view
that no widget test can serve.

So the screen is no longer untested, and the media-renderer gap is the only one
left, now for a permanent reason rather than a seam that is missing.

The 1444-test suite passes, `flutter analyze` clean, `dart format` clean,
`flutter build apk --debug` green.

## Phase 92 — harvest audit, the 2026-08-25 commit batch (38 commits), and the courses shelf split

> **Addendum (same round, post-harvest review):** `readTextContent` shipped
> without the try/catch the renderers it replaced had. `readAsString` throws on
> undecodable bytes — a binary file behind a `.txt` name, or a corrupted
> download — and with the renderers' own `_error` state removed in the same
> commit, that exception escaped `initState` unhandled and left the screen on
> its spinner forever. The seam now catches and returns `null`, so unusable
> content renders as the not-found message (which the old `e.toString()` body
> text was not an improvement on anyway); a unit test with an invalid UTF-8
> sequence pins it, verified to fail without the catch.

The 38 commits after `efec5e7c6` (Phase 79's last covered commit, up to
`cfa883770`, the current tip of `master`) were audited. One had a behavioural
port; the rest are refactors, performance rewrites, Kotlin-idiom cleanups,
Android-lifecycle concerns, or CI/build work with no behavioural change the
port lacks.

**Behavioural — harvested (three).**

- `034626415` (dashboard courses shelf handling, #15727) — the courses-card
  analogue of the library-card my/call split Phase 53 ported (`08e18ffdc`).
  `BellDashboardFragment` now tracks `userCourses` and, on the courses image
  button tap, opens "My Courses" (`openMyFragment`, the shelf view) when the
  user has joined courses and the full catalog (`openCallFragment`) when they
  do not. **Ported:** `home_screen.dart` gains an `_openCoursesCard` mirroring
  `_openLibraryCard` — it reads `myCoursesStreamProvider`, sets
  `courseFilterProvider.setMyCoursesOnly(courses.isNotEmpty)`, and navigates.
  Before this the courses header always set `myCoursesOnly = true`, so a user
  with no joined courses landed on an empty shelf rather than the catalog. The
  `_CourseTiles` empty-state placeholder already opened the catalog (it did not
  set the filter), so only the header tap diverged. 3 widget tests cover the
  shelf branch, the catalog branch, and the guest gate.
- `ef80dda52` (resource shelf add/remove idempotency) — the resource-detail
  screen's add/remove-from-shelf toast now fires only when shelf membership
  actually changed, not on every button press. **Ported:**
  `ResourcesRepository.setShelfMembership`/`setShelfMemberships` now fetch the
  current row before writing and skip the DAO upsert (and so the derived
  shelf upload the caller enqueues) when the `userId` list already reflects the
  requested state. Idempotency tests added (no-op remove, no-op add); the
  re-join test fixed to add the user first so the removal is real. The
  resource-detail screen test "stays silent when membership did not change"
  was already in place from the Phase 80 first pass; this makes the repository
  side match it.
- (testability, not a Kotlin port) the resource-viewer text/CSV/markdown
  renderers now read their bytes through a `resourceContentReaderProvider`
  seam (`ResourceFiles.readTextContent`) instead of calling `File.readAsString`
  in their own `initState`. The four Phase 91 tests that were withdrawn for
  stalling CI now pass (plain text, the title row, the CSV table, markdown),
  and `ResourceFiles.readTextContent` gets three unit tests. See the "What is
  not covered" section above for the trap this closes.

**Deferred — lands on an unported feature.**

- `6136f85f8`, `f66ee1454`, `1ef162120` (enterprises finances view binding /
  transaction view modelling / teams repositories view modelling) — enterprises
  remains covered by the teams slice with no UI layer of its own; deferred for
  the fifth time.
- `90dba9916` (crash log store handling) — the port has no `CrashLogStore`;
  lands on the crash-diagnostics feature the port has never had.

**No Flutter equivalent — same behaviour, different mechanism.**

- `12e0fbd14` (sync url utils managing) — hoists `UrlUtils.getUrl()`/`header`
  into locals before the resources `_all_docs` walk and the shelf fetch. The
  port passes the `ServerConfig` (and derives the header from it) per call; the
  config is read once before the loop and does not change. Micro-optimization,
  no behavioural gap.
- `bafc2da16` (download auth header) — caches `UrlUtils.header` once before the
  `DownloadWorker` per-file loop. The port's background entrypoint reads
  `prefs.serverConfig` once and `UrlUtils.authHeader(config)` is a pure function
  of it, so the header is already constant across the batch. No-op.
- `59e70b501` (photo url utils uploading) — hoists `UrlUtils.getUrl()` to a
  local in `PhotoUploader`. The port's `SubmitPhotosUploader` builds the URL
  from the passed `ServerConfig`. No-op.
- `95d68b3d7` (download service buffering) — bumps the read buffer 4 KB → 16 KB
  and moves a `current` KB calculation inside the notification-update branch.
  The port streams via Dio, not a manual byte-buffer loop, so neither change has
  an analogue.
- `1404b04cd` (resources: less apply filter button is more, #16091) — removes a
  "Close" button (labelled `@string/close`, id `btn_apply_filter`) from the
  filter dialog whose only action was `dismiss()`; in the Kotlin, filters apply
  live as checkboxes toggle. The port's `resources_filter_sheet` applies the
  filter on an "Apply" button press (a deliberately different interaction
  model), so its Apply button is functional, not redundant, and the header's
  `Icons.close` IconButton already serves the dismiss role. No redundant button
  to remove.
- `3c6a76aed` (guest user role handling) — `UserEntity.isGuest()` swaps
  `it?.lowercase() == "guest"` for `it.equals("guest", ignoreCase = true)`
  (semantically identical) and `android.util.Base64` for `java.util.Base64`
  (same NO_WRAP output). The port detects guests by the `guest_` id prefix
  alone and does not consult `rolesList`, a pre-existing deliberate
  simplification this refactor does not change.
- `d9e400bf7` (voices sorting) — moves `filterNotNull()` into `sortNews` and
  drops the redundant call-site filters. The port's `watchCommunityFeed` already
  returns `List<NewsRow>` (non-nullable) sorted by `sortDateOf` descending. No
  nulls to filter.
- `50a2aa1b0` (voices label managing) — collapses a `when` into
  `Constants.LABELS[labelText] ?: labels.firstOrNull{...}`. Semantically
  identical; the port's labels are a `List<String>` on the row with no
  chip-cloud lookup counterpart.
- `19373d0a1` (myplanet context handling) — replaces `MainApplication.context`
  (static singleton) with the passed `context` parameter and drops a
  `LOLLLIPOP_MR1` SDK guard (minSdk 26 is well above it). The port's
  `MyPlanetActivitiesUploader` uses the `DeviceStats` platform channel, which
  already runs against the engine's context. No-op.

**ViewModel extractions / DI refactors (Riverpod already sits there).**

- `3235d3705` (activities view modelling), `9aacbb9a8` (submissions list
  flowing), `f6bbf981a` (life repository view modelling), `79778bdc6` (community
  voices view modelling), `62bfe6d7e` (members finances ratings querying),
  `7fbfd40ea` (resources repositories linking), `14281a30b` (sync time logger
  providing), `f42381c47` (surveys repository querying), `988994f88` (retry
  repository dao querying), `135914643` (personals repository querying),
  `ce9f0dad7` (submissions repository exporting), `90e250e8c` (layout change
  listening), `8928ea2f8` (view lifecycle owning) — RecyclerView/ViewBinding/
  Hilt reshuffling, dependency-direction refactors, or Android-lifecycle
  concerns. Riverpod providers already sit where the Kotlin is moving logic to.

**CI / build / dependency (no app impact).**

- `cfa883770` (automerge conflict handling), `d652653ae` (dependabot config),
  `ce6f701bc` (gradle-wrapper 9.7.1), `f71ab5633` (playstore quota), `8eb59e047`
  (test workflow), `073d2e9fa` (download service testing) — workflow/build/test-
  only edits plus the lockstep `versionCode`/`versionName` bumps.

The 1444-test suite passes, `flutter analyze` clean, `dart format` clean.

## Phase 93 — master merged in, the post-92 batch audited, and the version caught up

The branch merged `origin/master` (merge-base `c2cf2a788`; 279 commits brought
in; `app/` and `gradle/` verified byte-identical to master afterwards, so the
Kotlin side is exactly what master's CI tested). The repository was shallow —
the graft made master look 6699 commits away with no merge base — so it was
unshallowed first; a merge under the graft would have been treated as unrelated
histories. Two conflicts, both CI workflow files where our `flutter/**`
`paths-ignore` met master's independently-added ignores and
`permissions`/`concurrency` blocks; resolved as the union.

**The 12 commits after `cfa883770`** (Phase 92's audit boundary, up to
`ba794f4bb`) were audited. Eleven need no port: Android-lifecycle fixes
(`viewLifecycleOwner`, Glide recycling, an adapter text-cache micro-opt), DI
interface splits, import cleanups, an `HttpURLConnection` resource-leak fix in
code the port replaces with Dio, a `Context` → `String` signature refactor with
no wire change, and CI work. One is behavioural:

- **`770d6608c` (#16154) — `LoginSyncManager.isManager` substring bug.** The
  Kotlin stringified the roles array and substring-matched `"manager"`, so a
  role like `"comanager"` — or any role with the word in it — opened the
  manager door. The fix element-matches. The port's `UserMapper.isManager`
  **always** element-matched, which means the two apps *disagreed* about who a
  manager was until this fix; they agree now. Nothing to port — instead the
  lookalike cases (`comanager`, `managers`, `team manager`) are pinned in
  `user_mapper_test.dart` so a refactor toward `contains` fails loudly.

**The version caught up.** Kotlin moved 0.64.35 → **0.67.14** while the port's
pubspec still pinned `0.62.98+6298` — and that pubspec version is what
`package_info_plus` reports at runtime, which since Phase 60 is what the
`minapk` gate compares. A server whose `minapk` moved past 0.62.98 would have
refused the Flutter app configuration with the unreachable-server message.
`pubspec.yaml` is now `0.67.14+6714` and
`ConfigurationsRepository.defaultAppVersion` (the fallback for a failed
runtime lookup) matches. The settings/About screens need no change — they read
the runtime value.

Test count and gates re-verified after the merge and the changes above.

## Phase 94 — the channels become a plugin, and version drift becomes a test failure

Two items, both from the standing backlog rather than a harvest.

**The `MainActivity` channel gap is closed.** `disk_stats` and `device_stats`
were registered in `MainActivity.configureFlutterEngine`, which only runs for
the Activity's engine — a headless engine started by the `workmanager` plugin
never passed through it, so every background call threw
`MissingPluginException`. Phases 45–46 papered over that with UI-primed
preference caches; this phase lands the fix those phases named: the channels
moved into `flutter/packages/planet_platform_channels`, an in-tree plugin on a
path dependency, generated from `flutter create --template=plugin` so the
Gradle shape matches the pinned toolchain. Being a real plugin puts
`PlanetPlatformChannelsPlugin` into `GeneratedPluginRegistrant` (verified in
the built registrant), which every engine runs — foreground and headless
alike. The Kotlin moved verbatim except for `Activity` → application `Context`
(nothing in it ever needed an Activity, which is why the Activity was the
wrong home) and `PackageInfoCompat` → a two-line SDK fork, so the plugin
carries no androidx dependency. `MainActivity` is a bare `FlutterActivity`.
The caches stay as belt-and-braces for values cached before the plugin
existed.

Landing it surfaced a second alignment bug: the port's `minSdk` was Flutter's
default **24** while the Kotlin app ships **26** — the port had been silently
claiming to support two Android versions its sibling does not. The plugin's
API-26 `StorageStatsManager` made the manifest merger fail loudly, and the
app's `minSdk` is now pinned to 26 with a comment naming the Kotlin source.
(Side effect: the debug APK dropped 214 MB → 181 MB.)

**Version drift now fails the suite.** Phase 93 caught pubspec five releases
behind `app/build.gradle` by hand. `test/version_parity_test.dart` reads the
Kotlin gradle file — the release train both apps ride — and checks the port
against it.

> **Amended immediately after CI.** The first cut asserted pubspec == the Kotlin
> `versionName` exactly, and it was wrong about this repo's cadence.
> `automerge.yml` bumps the version on **every PR it merges**, so master went
> 0.67.14 → 0.67.25 within the hour. The branch's own push run stayed green;
> the *pull-request* run went red, because a PR run tests the merge with master
> and therefore sees master's newer gradle file. Exact equality against a target
> that moves several times an hour is a permanently-red build, and a
> permanently-red build teaches everyone to ignore it — strictly worse than no
> test.
>
> The rule now splits in two. The strict half is internal and always valid:
> pubspec's version and `ConfigurationsRepository.defaultAppVersion` must agree
> with **each other**. The external half tolerates patch lag and fails on a
> **minor** version behind, which is where the risk becomes real — servers set
> `minapk` to force upgrades of genuinely old clients, not to the newest patch
> (a server demanding the current patch would lock out everyone who had not
> updated in the last hour). Verified in both directions: the 0.67.25-vs-0.67.14
> patch lag that broke CI now passes, and the actual Phase 93 drift
> (0.62.98 against 0.67.25) fails with the two lines to change. The test carries
> a note asking the next reader not to tighten it back to equality.

The 1448-test suite passes, `flutter analyze` clean, `dart format` clean,
`flutter build apk --debug` green with the plugin in the registrant.

## Phase 95 — master merged, and the health screen's hand-rolled avatar

**The master audit is a no-op for the port.** 26 commits landed since Phase 93
(`3ee2ac7b4`…`ee81cb3b2`), and the merge was clean. They are the "smoother X"
refactor series: `ResourceSearchUtils` renamed to `ResourcesSearchUtils` with
its split moved to a sequence, `String.format` collapsed into interpolation,
`findViewById` replaced by view binding, `ContextCompat.getDrawable` hoisted
into `lazy` fields, `showZoomableImage` extracted from `MarkdownUtils` into
`ImageViewerUtils`, `MyLife.defaultItems` taking a label resolver instead of a
`Context`. Two carry real behaviour, and neither applies here:

- `SubmissionsRepositoryExporter`'s PDF word-wrap now measures incrementally
  (`lineWidth + measureText(" $word")`) instead of measuring the whole
  candidate line. Text measurement is not additive, so this can drift by a
  word at a boundary — an accepted trade for dropping the O(n²) measuring. The
  port has no counterpart: its export uses `package:pdf`'s own layout.
- `ImageViewerUtils.showZoomableImage` gained an http/https branch; a URL used
  to be wrapped in `File(imagePath)` with the raw string only as an error
  fallback. The port's `course_markdown.dart` already branches on scheme
  (`file`, `http`/`https` on the server host → authenticated bytes, elsewhere →
  plain network, else relative) and never builds a `File` from a URL.

The new `VersionUtilsTest` cases pin four `compareVersions` edges — throws on a
malformed part *anywhere* (both strings are parsed eagerly), throws on empty,
strips `-lite` only from the first argument, and sorts a shorter matching
prefix lower. The port matches three and diverges on the first **by design**:
`int.tryParse(p) ?? 0` instead of `toInt()`, because a throw here returns
`_UrlCheckFailure`, which the configuration screen reports as an unreachable
server. That divergence was already documented at the call site and pinned by a
test; the Kotlin's new cases contradict nothing.

**`MyHealthScreen` gets its first tests, and they found five defects.** 955
lines, zero coverage — the largest untested hand-written surface after the
resource viewer. The tests drive the real provider graph
(`patientDetailProvider` → `loggedInUserProvider` → `sessionProvider` → an
in-memory database) and seed genuinely encrypted rows through
`ensureSecurityKeys` + `encryptData`, so the decryption round trip the screen
depends on is exercised rather than mocked. Four of the five failed on the
pre-fix code; the fifth surfaced as a thrown layout error.

1. **The profile photo could never load.** The screen built its own
   `CircleAvatar(backgroundImage: NetworkImage(user.userImage!))`.
   `users.userImage` holds a CouchDB *attachment name* — `UserMapper` says so
   in a comment where it writes the column — not a URL, and the attachment is
   behind Basic auth, which `Image.network` cannot send. Both halves are wrong
   independently. The shared `ProfileAvatar` already resolves the name through
   the authenticated bytes path, handles a locally-picked file path, and falls
   back to initials; the screen now uses it, in the profile card and in the
   patient picker's rows.
2. **A whitespace-padded username crashed the screen.** `_getInitials` did
   `parts[0][0]` on `name.split(' ')`, guarded by `if (parts.isEmpty)` — dead
   code, because `''.split(' ')` is `['']` and the list is never empty. The
   display-name fallback returns `user.name` **untrimmed**, so a synced user
   with `"name": " jane "` and no first/last name produced an empty first part
   and threw `RangeError` out of `build`. Confirmed as a real `RangeError`
   before the fix. The shared helper filters blanks before indexing and uses
   `characters.first`, so it is grapheme-safe too — the old `[0]` split a
   surrogate pair on a non-BMP name.
3. **A hardcoded `'Unknown'`** in the display-name fallback, in a file that
   already reads `l10n.unknown` two widgets down. Phases 57–58 retired the
   port's hardcoded strings; this one survived by sitting in a helper.
4. **Pull-to-refresh did nothing.** `RefreshIndicator.onRefresh` was an empty
   async body with a `// Refresh health data` comment. Kotlin's
   `MyHealthFragment` has no `SwipeRefreshLayout`, so the gesture is the port's
   own invention and had never been wired. It now re-reads the selected
   patient.
5. **A fully-populated examination card overflowed.** The history strip was
   140px tall, 8px short of a card carrying date, examiner, temperature, pulse,
   blood pressure *and* the has-info icon — a `RenderFlex` overflow, the
   yellow-and-black stripe on a device. Only the fullest card trips it, which
   is why nothing caught it. Sized for that card with headroom, and the card
   body scrolls so a large text scale (the app has a text-size setting)
   degrades into a scrollable card rather than a striped one.

Two consolidations came with them. `_resolveCreatorName` existed as identical
copies in the card and its detail dialog and is now one top-level
`resolveCreatorName`; the `couchId`-else-`id` derivation that keys a health
record appeared three times and is now `patientIdOf` in the provider file.

The refresh fix needed a new `PatientDetailNotifier.refresh()`, and writing it
exposed a sixth issue worth naming: the sync button did
`ref.invalidate(patientDetailProvider)`, which rebuilds the notifier and reruns
`_loadInitial` — resolving the **logged-in** user. A health provider who had
selected another patient was silently bounced back to their own record on every
sync. `refresh()` re-selects the current patient instead, and the sync button
now calls it.

Not fixed, and worth knowing: `'myPlanet learner'` (the shared display-name
fallback) is itself hardcoded English, in both `profile_avatar.dart` and
`profile_screen.dart`, and pinned by two existing tests. That is a separate
change from this one and was left alone.

---

## Phase 96 — harvest audit of the 26 post-94 commits, and the ranked resource search

**The batch was clean.** Master moved 0.67.14 → 0.67.40 across the 26 commits
after Phase 94's boundary (`ba794f4bb` → `ee81cb3b2`). Auditing them turned up
no new behavioural port: the batch is ViewModel extractions, ViewBinding
cleanup, lazy-init and string-template idioms, lambda `forEach` rewrites, and
CI/build config. The one thing worth a port was a **pre-existing** gap the
audit surfaced, not something this batch introduced.

**The ranked resource search.** The Kotlin resources screen does not filter
with a SQL `LIKE`. It loads the full list and runs `ResourcesSearchUtils`
(`49617105e`, refined `1e41d3353`) — a `searchList` that ranks titles whose
normalized form **starts with** the whole query ahead of titles that merely
**contain every whitespace-separated word**. The Flutter port, since the
first resources slice, had been filtering in SQL with `titleNormal LIKE
'%query%'`, which can neither rank a prefix match above a substring match nor
split the query on words — so "math basic" matched only the literal
contiguous substring, and "Math Basics" ranked no higher than "Advanced
Math". The `ac5adbddb` commit in this batch merely refactored how
`ResourcesFragment` calls the util (it did not touch the algorithm), which
is what drew attention to the divergence.

The port now matches the Kotlin. `MyLibraryDao.watchResources` drops the
text-search `LIKE` (the shelf `userId` scope stays in SQL — it is a
structural filter, not a text search) and orders offline-first then
alphabetically, exactly as `getResourceListModels` does.
`ResourcesRepository.watchResources` maps that stream through
`searchResources`, a top-level pure function (the same shape as
`searchChatsForMode` in Phase 75) ported from `searchList`: trim the query
(normalize the whole string for the prefix check, split on spaces and
normalize each part for the contains-all check), bucket starts-with before
contains-all, and preserve the input order within each bucket. Normalization
reuses `text_utils.normalizeText` (the single `normalizeText` settled in
Phase 78), so accents fold the same way as resource, course, and chat search.

The courses analogue needs no port: `CoursesRepositoryImpl.search(query)` —
the ranked `matchesAllParts` version — is in the interface but has no callers;
the courses screen filters with `filterCourses`, a plain `contains`, which the
port's `watchCourses` already mirrors.

11 new tests: 9 for the pure `searchResources` (prefix-before-contains,
word-split regardless of order, diacritic- and case-folding, blank-query
passthrough, the all-parts-AND rule, in-bucket order, and the `titleNormal`-
null fallback) and 2 through the repository's `watchResources` to pin the
ranking end-to-end. The 1460-test suite passes, `flutter analyze` clean,
`dart format` clean.

---

## Phase 97 — three search/sort/visibility gaps the deeper audit found

Phase 95's audit found one gap. Asked to go deeper ("more much much more
triple as much"), the same systematic Kotlin-vs-Flutter comparison of the
search/filter/sort surface turned up three more behavioural gaps the port
had been getting wrong, each in a different screen.

**Resource catalog visibility.** The Kotlin `ResourcesRepositoryImpl`
catalog never shows every row. `getEnrichedLibraries` splits the list three
ways: the user's shelf (`getMyLibrary` — `userId LIKE %"userId"%`,
private team resources the user owns included), the public catalog minus
the signed-in user's shelf (`getPublicNotUserPattern` — `isPrivate = 0 AND
(userId IS NULL OR userId NOT LIKE %"userId"%)`, so a resource already on
My Library is not duplicated between the two tabs), and a guest sees
`getPublic` (every public resource). The Flutter port's
`MyLibraryDao.watchResources(shelfUserId)` did the shelf half but missed
the catalog half: with a user signed in it returned *every* public
resource — the user's own shelf items *and* every private team resource
— because it only applied the `userId LIKE` scope in shelf mode and no
scope at all in catalog mode. So the catalog showed private resources that
should never appear, and duplicated shelf items between the catalog and My
Library. `watchResources` now takes a `myLibrary` flag and mirrors the
Kotlin split: shelf mode is `userId LIKE` (private team resources
included); catalog mode is `isPrivate = 0 AND (userId IS NULL OR userId
NOT LIKE)` when a user is signed in, and `isPrivate = 0` for a guest.
`resourcesStreamProvider` passes the signed-in user's id and the
`shelfOnly` toggle through; `myLibraryStreamProvider` (the dashboard
card) passes `myLibrary: true`. The offline-first then alphabetical
ordering is unchanged.

**Survey search.** `SurveysViewModel.filter` is the same ranked algorithm
as `ResourcesSearchUtils.searchList`: titles whose normalized form starts
with the whole query rank ahead of titles that contain every
whitespace-separated word, and accents fold. The Flutter port used a flat
`toLowerCase().contains(query)` on both `name` and `description`, so it
could neither rank a prefix match nor split the query on words, folded no
accents ("cafe" did not find "Café"), and searched the `description` column
the Kotlin never reads. The port now calls a top-level `searchSurveys`
pure function (the same shape as `searchResources`/`searchChatsForMode`)
that buckets starts-with before contains-all on `name` only, splits the
query on spaces, and reuses `text_utils.normalizeText` for the accent
folding — so a survey search now behaves the same way a resource search
does.

**Survey sort date.** `SurveysViewModel.getSortDate` does not sort an
adopted survey (one with a `sourceSurveyId`) by its `createdDate`; it
prefers `adoptionDate`, falling back to `createdDate` only when
`adoptionDate` is 0. The Flutter port's `newest`/`oldest` comparators used
`createdDate` directly, so an adopted survey sorted by its template's
creation date rather than the date the team adopted it — the wrong order.
A `surveySortDate` pure function ports `getSortDate`, and the
`surveysProvider` comparators call it; a native survey (no
`sourceSurveyId`) still sorts by `createdDate`.

14 new tests: 8 for `searchSurveys` (prefix-before-contains, word-split,
accent- and case-folding, blank-query passthrough, the all-parts-AND rule,
the never-search-description pin, the no-name skip, in-bucket order), 3 for
`surveySortDate` (native uses createdDate, adopted uses adoptionDate,
adopted with adoptionDate 0 falls back), and 3 through the repository's
`watchResources` to pin the visibility split end-to-end (catalog excludes
private, catalog excludes the user's own shelf items, My Library includes
the user's private team resources). The 1474-test suite passes (1460
baseline + 14), `flutter analyze` clean, `dart format` clean.

---

## Phase 98 — the notification sync-in direction, and read-state round-trip

Phase 96's deeper audit fixed the search/sort/visibility surface but left
the notifications domain half-ported: the bell list rendered rows the
**upload** direction had created (`userId:resource:count`,
`userId:storage`, team watermarks) and the Phase 49 sub-destination parser,
but the **sync-in** (pull) direction — `TransactionSyncManager`'s
`"notifications"` walk — had never landed. A server-side notification
(join request, new task, reply) never reached the local cache, so the bell
never showed anything the user had not generated locally, and the
read-state upload had no `rev` to PUT back, so a "mark read" could never
persist to CouchDB. This phase closes both halves and the round-trip gap
between them.

**Schema.** The `Notifications` table gains `rev` (TextColumn, nullable)
and `needsSync` (BoolColumn, default false) at schema v44. Neither column
is preserved by `localAuthorityTables` — `notifications` is a pure
CouchDB cache, dropped and refilled by the next sync, so no hand-written
migration step is needed and the `covered` set in `migration_test.dart` is
unchanged. `rev` carries the CouchDB `_rev` so the read-state upload can
PUT without a 409; `needsSync` flags a row whose read state changed locally
but has not been uploaded yet, so a re-pull does not clobber the local
"read" with the server's stale "unread" (the same shape as Phase 56's
security-data fix and Phase 74's reactions round-trip).

**DAO.** `NotificationDao` gains `markSummaryAsRead`,
`getPendingSyncNotifications` (via `needsSync`), `upsertAll`,
`getByIds`, `deleteByIds`, and `markSynced`, porting the Kotlin
`NotificationDao` surface. The earlier `watchForUser`/`watchUnreadCount`
had type errors from the Phase 53 port — `isAdmin` was passed where an
`Expression<bool>` was expected and the unread count used a raw `bool` —
both fixed.

**Repository sync-in.** `NotificationsRepository.sync` ports the
`TransactionSyncManager` notifications walk: a paginated `_all_docs` pull
over the `notifications` database via `AdaptiveBatchProcessor`, parsing
each document through `_parseNotification` (a port of
`NotificationsRepositoryImpl.parseNotification`) which stamps
`isFromServer`, carries `_rev`, maps `status != "unread"` to `isRead`,
and falls back to now when `time` is missing. `_bulkInsertFromSync` ports
the Kotlin merge: a row whose `needsSync` is true keeps its local `isRead`
and the flag, so a re-sync cannot undo a read. `_design` documents are
skipped (Kotlin's `!id.startsWith("_design")`, no trailing slash).
`extractTeamSubtype`/`extractRelatedId`/`extractIdFromLink` are straight
ports; the last mirrors Kotlin's `link.trim('/').split('/')` (slashes
only, not whitespace, empty mid-segments kept) so the `view` index lines
up exactly. Unlike every other sync repository this one runs **no**
`deleteNotIn` — the Kotlin walk never does either, and a prune would
evict the locally-authored `userId:resource:count` and `userId:storage`
rows that have no server document; stale server rows linger until
individually deleted.

**Read-state round-trip.** `markNotificationAsRead` (ported in the
Phase 53 batch) now flags server-originated rows `needsSync = true`, and
`syncNotificationReads` (the upload direction) PUTs each pending row back
to `notifications/<id>?rev=<rev>` using the carried `rev`, then calls
`markSynced(id, newRev)` so a second upload in the same pass is a no-op.
The dashboard's `syncAll` already called `_syncNotificationReads` after
the table pulls; the pull now seeds the rows it uploads.

**Dashboard wiring.** A new `DashboardSyncArea.notifications` joins the
sync center, backed by `NotificationsSyncNotifier` +
`notificationsSyncProvider` (the same `SyncNotifier` shape as the other
ten areas), so a user can refresh notifications independently and the
bell list reflects it. One new l10n key (`syncNotificationsDescription`)
in `app_en.arb`; the `notifications` label key already existed.

4 new tests: the sync-in parse (subType/relatedId/rev/isFromServer,
`_design` skipped, `status` → isRead, `newTask` pulls the id from the
link), the read-state preservation across a re-pull (the round-trip gap —
a row marked read locally stays read and flagged after a second sync),
the no-`deleteNotIn` guarantee (a local `userId:resource:count` row
survives a sync that returns only server rows), and the `summary_` prefix
mark-all-read path. The 1478-test suite passes (1474 baseline + 4),
`flutter analyze` clean, `dart format` clean. (1495 after the harvest merge
and the correction below.)

> **Corrected on harvest.** `markAllAsRead` and `markSummaryAsRead` were each
> written as *two* statements — one to set `is_read`, a second to set
> `needs_sync` on the server-originated rows. The Kotlin does it in **one**,
> with `needsSync = CASE WHEN isFromServer = 1 THEN 1 ELSE needsSync END` under
> a single `WHERE ... AND isRead = 0`, and that single `WHERE` is the whole
> point: once the first update has flipped the rows, nothing distinguishes the
> ones it just changed, so the second update could only re-select by
> `is_read = 1` — which matches every notification the user has *ever* read.
> One "mark all as read" tap therefore re-flagged the entire read history, and
> the next `syncNotificationReads` PUT every one of those documents back to
> CouchDB, each a full write bumping its `_rev`. `markSummaryAsRead` had also
> dropped `is_read = 0` from its *first* update, so it reported every row of
> the type as newly marked rather than only the unread ones. Both are now the
> Kotlin's single `customUpdate`; four DAO tests pin the flagging and the count,
> three of which fail on the pre-fix code (`read-long-ago` came back in the
> pending-upload set, and the count read 2 where it should read 1).

---

## Phase 99 — the enterprises audit, and four gaps in the team finance screens

**The 28th package was never missing.** `ui/enterprises/` had been listed for
dozens of phases as the one UI package with no Flutter screen. It is not a
screen the port lacks; it is two screens the port files under `ui/teams/`.
Enterprises are a team **type**, not a separate feature:
`TeamDetailFragment.buildPages` computes `isEnterprise = team?.type ==
"enterprise"` and swaps tabs on it, while `EnterprisesFinancesFragment` and
`EnterprisesReportsFragment` carry no type branching whatsoever — and neither
does `BaseTeamFragment`, which only resolves the team and the user's
membership. So the mapping to `team_finances_screen.dart` /
`team_reports_screen.dart` is complete and always was. The count was counting a
directory name. (This document's own Status section had said as much since
Phase 18 — "the teams slice covers what it did" — so the two docs had
disagreed with each other for a long time; the audit's real product is that
they now agree.)

**Auditing the mapping field by field surfaced four real gaps.**

- **The manage gate was wrong in both screens.** Kotlin's
  `canManage = if (fromCommunity) user?.isManager() == true else isMember`
  takes `isMember` from `isMemberFlow`, which `BaseTeamFragment` fills from
  `TeamsRepositoryImpl.isMember` — *plain membership*. Both port screens
  required `?.isLeader`, so an ordinary member of an enterprise saw no
  add-transaction and no add-report button where the Kotlin gives them both.
  Kotlin has a separate `isTeamLeader` and deliberately does not use it here.
  The `fromCommunity` branch ports alongside it — `CommunityPagerAdapter` puts
  `fromCommunity = true` in the arguments of both fragments, so on the
  community tabs the gate is the `manager` role instead — and it is read only
  on that path, so the team path never builds `sessionProvider`.
  Worth recording why `!= null` is the right membership test here even though
  Kotlin's `isMember` also filters `!it.isDeletePending`: the port has no
  `isDeletePending` column at all. `TeamsRepository.leave`/`removeMember`
  hard-delete the row and enqueue the tombstone through the outbox, so a
  pending leave is modelled as *row absent*, and the row's presence is
  exactly Kotlin's predicate under that model.
- **The report card rendered 4 of the 9 value rows** `EnterprisesReportsAdapter`
  binds: the derived totals only, none of the five authored figures
  (`beginningBalance`, `sales`, `otherIncome`, `wages`, `otherExpenses`) and no
  created/updated footer. Every field was already on `TeamRow`, so this was
  display-only — no schema change.
- **The finances summary had no negative-balance caution** — `balance_caution`
  in `header_finance.xml`, shown when `FinanceHeaderState.isCautionVisible`,
  i.e. `total < 0`.
- **The CSV export filename carried a stray brace.**
  `'${weekday}_$month}_${day}_${dt.year}'` interpolates the bare `$month` and
  then emits `}` as a literal, so the save dialog offered `Thu_Aug}_20_2026`
  against Kotlin's `EEE_MMM_dd_yyyy`. The formatter moved to a top-level
  `reportExportDateSuffix` so a test can pin it, instead of it being reachable
  only through the platform save-file dialog — which is why a whole-string bug
  survived in a formatter that had a correct doc comment above it.

Six tests, each run against the unfixed code first. Two differences were found
and deliberately left: the port's team detail lists a Finances entry gated on
membership where Kotlin gates it on the enterprise type (a surplus, not a gap,
and the port's detail screen is a link list rather than Kotlin's tab pager, so
its gating is not a line-for-line port anyway), and the report card's
`%s Financial Report` title, which needs a `teamProvider` watch and so trips
the documented widget-test harness trap.

## Phase 100 — first coverage for `TakeExamScreen`, and two defects it found

`flutter/lib/ui/exam/take_exam_screen.dart` (the port of `ExamTakingFragment` /
`BaseExamFragment`) had no tests. It now has 16, in
`test/ui/exam/take_exam_screen_test.dart`: the four question renderers, the
`Question n / m` counter and the app-bar title fallback, Next/Previous with the
per-question answer map, an attempt graded and persisted to `submissions` +
`submission_answers`, the result dialog's score and its pop, the failed-save
snackbar, both exit-prompt branches, and all three verification-photo branches
(uncertified, captured, cancelled). They drive the real provider graph against
an in-memory `AppDatabase` — the exam and its questions are seeded as rows, so
grading and persistence run through the real `SubmissionsRepository` rather than
a fake that would assert on nothing.

**Defect: the certified-course verification photo never reached CouchDB.**
`_captureVerificationPhoto` saved the JPEG with
`SubmitPhotosFiles.write(photoId: submissionId, …)`. But
`SubmitPhotosUploader._uploadAttachment` reads it back with
`SubmitPhotosFiles.existingFileFor(photoId: row.itemId, …)`, and `row.itemId` is
the **`submit_photos` row's** id — a different sha1
(`photo:<submissionId>:<examId>:<courseId>:<ts>`) from the submission's
(`<userId>:<ts>:<examId>`). The two directories could never coincide, so the
lookup missed on every capture and `_uploadAttachment` took its "a missing file
is a no-op, not an error" branch. The submission document uploaded fine; the
photo — the entire point of a certified course's exam — stayed on the device,
silently. `SubmitPhotosFiles`' own header documents the invariant that was
broken: *"when the write-back and the upload read-back build their paths from
the same source they cannot drift."*

The fix promotes the derivation `addSubmissionPhoto` already performed inline to
a public `SubmissionsRepository.photoIdFor({submissionId, capturedAt, examId,
courseId})`. The screen mints the row id first, writes the bytes under it, and
passes the same `capturedAt` back into `addSubmissionPhoto`, so the row lands on
that key. The test asserts the uploader's *own* lookup rather than a path
string, and on the pre-fix code it fails with `Expected: not null / Actual:
<null>`.

**Defect: submitting could silently discard the whole attempt.** `_submitExam`
took the user from `ref.read(sessionProvider).valueOrNull`. That reports `null`
until something else has resolved the provider, and this screen never watches
it — so `if (user == null || exam == null) return` threw the graded answers away
with no dialog, no snackbar and nothing written. It is latent in the shipping
app, where the router's `ref.listen(sessionProvider, …)` keeps the session
resolved, but it is real for any entry point that does not and it made every
submit path untestable in isolation. `_submitExam` now awaits
`sessionProvider.future`, which is also what the Kotlin does —
`initializeExamData` resolves its own `userSessionManager.getUserModel()` before
the exam is usable. Eight of the new tests fail on the pre-fix code, all with
the same shape: "Found 0 widgets with text 'Exam Complete'".

**Not a defect, recorded because it is easy to assume otherwise.** The exam
screen does not carry the mandatory-survey block, and should not: that check is
in `take_course_screen.dart`'s `_onFinish`, gated on
`MANDATORY_SURVEY_COURSE_ID`, exactly where the Kotlin puts it
(`TakeCourseFragment.onFinishStep`). Neither `ExamTakingFragment` nor
`BaseExamFragment` reads surveys at all.

**Testing notes for this screen.** Never `pumpAndSettle` after tapping Submit:
while `_isSubmitting` is true the button holds a `CircularProgressIndicator`,
whose indefinite animation spins `pumpAndSettle` to its ten-minute default — the
resource viewer's failure-that-looks-like-a-hang, from a different cause. Use
`runAsync` + `pump` rounds instead. The photo test needs many more of them than
the rest of the file: `SubmitPhotosFiles` is genuine `dart:io`, and
`Directory.create` + `writeAsBytes` + the row that follows them want noticeably
more wall-clock time than drift's in-memory reads.

---

---

**Last updated**: 2026-09-02 (Phase 100 complete — first coverage for `TakeExamScreen` (16 tests) and the two defects they found: the certified-course verification photo was filed under the *submission* id while `SubmitPhotosUploader` reads it back under the *photo row* id (a sha1), so no photo ever uploaded and the missing-file branch swallowed it silently — `SubmissionsRepository.photoIdFor` now exposes the derivation; and `_submitExam` read `sessionProvider` with `ref.read(...).valueOrNull` on a screen that never watches it, so a graded attempt could be dropped with no dialog, no snackbar and no row (latent in the shipping app, which holds a `ref.listen` in the router). Phase 99 complete — the enterprises audit: `ui/enterprises/` was never a missing screen (enterprises are a team *type*; the two fragments are already ported as `team_finances_screen`/`team_reports_screen`), so the "27 of 28 UI packages" count is retired in both docs. The field-by-field audit closed four real gaps: the manage gate required `isLeader` where Kotlin takes plain membership (so an ordinary enterprise member saw no add buttons), the report card showed 4 of 9 value rows, the finances summary had no negative-balance caution, and the CSV filename carried a stray brace (`Thu_Aug}_20_2026`). 6 tests. Phase 98 amended on harvest — markAllAsRead/markSummaryAsRead ran as two statements, so the needs_sync half could only re-select by is_read = 1 and one "mark all read" tap re-queued the user's entire read history for upload; both are now the Kotlin's single CASE WHEN statement, with four DAO tests. Phase 98 complete — the notification sync-in direction and the read-state round-trip. The `Notifications` table gains `rev` + `needsSync` (schema v44, pure cache so no preservation test); `NotificationDao` gains `markSummaryAsRead`/`getPendingSyncNotifications`/`upsertAll`/`getByIds`/`deleteByIds`/`markSynced` plus the Phase 53 `watchForUser`/`watchUnreadCount` type-error fixes; `NotificationsRepository.sync` ports the `TransactionSyncManager` notifications `_all_docs` walk and `parseNotification` (with the `needsSync` merge that stops a re-pull clobbering a local read — the Phase 56/74 round-trip shape); `syncNotificationReads` PUTs back with the carried `rev`; `DashboardSyncArea.notifications` + `NotificationsSyncNotifier` wire the bell-list refresh into the sync center. 4 new tests, 1478 pass. Phase 97 complete — three search/sort/visibility gaps the deeper audit found. The resource catalog visibility split: `getEnrichedLibraries`'s `getMyLibrary`/`getPublicNotUserPattern`/`getPublic` three-way split now mirrored by `MyLibraryDao.watchResources(myLibrary:)`, so the catalog excludes private resources and the signed-in user's own shelf items (no duplication between catalog and My Library), and My Library includes the user's private team resources. The survey search: `SurveysViewModel.filter` is the same ranked algorithm as `ResourcesSearchUtils.searchList` (startsWith-before-contains-all-words, word-split, accent-folded, name-only); the port's flat `toLowerCase().contains` on name+description is replaced by a `searchSurveys` pure function. The survey sort date: `SurveysViewModel.getSortDate` prefers `adoptionDate` over `createdDate` for adopted surveys (those with a `sourceSurveyId`); a `surveySortDate` pure function ports it. 14 new tests, 1474 pass. Phase 96 complete — harvest audit of the 26 commits after `ba794f4bb` (master 0.67.14 → 0.67.40) found no new behavioural port; the batch is refactors and CI/build work. The one port is a pre-existing gap the audit surfaced: the ranked resource search from `ResourcesSearchUtils.searchList` (`49617105e`/`1e41d3353`), which ranks starts-with matches ahead of contains-all-words matches and splits the query on spaces — neither expressible in the SQL `LIKE` the port had been using since the first resources slice. `MyLibraryDao.watchResources` now drops the text-search `LIKE` (the shelf `userId` scope stays in SQL), `ResourcesRepository.watchResources` maps the stream through a top-level `searchResources` pure function ported from `searchList`, and `text_utils.normalizeText` (the single one, Phase 78) does the accent folding. The courses analogue needs no port — `CoursesRepositoryImpl.search(query)` is in the interface but uncalled; the screen uses `filterCourses` (a plain `contains`) which the port already mirrors. 11 new tests, 1460 pass. Phase 95 complete — master merged (26 commits, all refactors; the two behavioural ones have no port counterpart) and MyHealthScreen got its first 13 tests, which found five defects: an avatar that handed a CouchDB attachment name to NetworkImage so the photo could never load, a RangeError crash on a whitespace-padded username, a hardcoded "Unknown", a pull-to-refresh whose handler was empty, and a RenderFlex overflow on a fully-populated examination card; plus a sync button that invalidated the provider and so reset a health provider's selected patient. Phase 94 complete — the disk_stats/device_stats channels moved into the in-tree planet_platform_channels plugin so headless WorkManager engines get them (the Phase 45–46 caches stay as fallback), minSdk aligned to the Kotlin app's 26, and version_parity_test.dart pins pubspec and the minapk fallback to app/build.gradle. Phase 93 complete — master merged in (unshallowed first; app/ verified byte-identical to master), the 12 post-92 Kotlin commits audited with one agreement-pin (isManager lookalikes), and the app version caught up to Kotlin's 0.67.14 in pubspec and the minapk fallback constant. Phase 92 complete — harvest audit of the 38 commits after `efec5e7c6` (up to `cfa883770`, master tip): three behavioural ports. The dashboard courses-card my/call split (`034626415`, #15727) — the analogue of the library-card split Phase 53 ported; `home_screen`'s courses header now opens "My Courses" when the user has joined courses and the full catalog when they do not. The resource shelf add/remove idempotency (`ef80dda52`) — `setShelfMembership` now fetches the row before writing and skips the no-op upsert, so the add/remove toast fires only on a real change. The resource-viewer text/CSV/markdown renderers now read through a `resourceContentReaderProvider` seam (`ResourceFiles.readTextContent`) instead of `File.readAsString` in `initState`, so the four Phase 91 tests that stalled CI now pass (plain text, the title row, the CSV table, markdown) and `readTextContent` gets three unit tests. The rest of the batch is refactors, perf rewrites, Android-lifecycle concerns, and CI/build work with no behavioural port needed. Phase 91 complete — first tests for `ResourceViewerScreen`, the port's largest untested screen: load and missing states, title and fallback, and the whole download prompt; the file-backed text/CSV/markdown renderers' stall is now closed by the Phase 92 content-reader seam. Phase 90 complete — trim parity for three health-profile fields the Kotlin trims and the port did not, and the Status section corrected where it still called `ChallengeDialog` dead code after Phase 81 wired it; Phases 82–89 written up. Phase 89 complete — ported the resource detail download button state logic (`ResourceDetailFragment.setupDownloadButton`/`updateDownloadButtonState`) to Flutter: the `resource_detail_screen`'s primary action button now reflects download state instead of always showing "View Resource". When the resource's `resourceOffline` flag is set (the port of `MyLibrary.isResourceOffline()`), the button shows a "View" label with a visibility icon (or a play icon for video). When not downloaded, it shows "Download" — except for video, which shows "View" when the server is reachable (streaming) and "Download" when offline. The button is hidden entirely for non-HTML resources with no `resourceLocalAddress`, matching `setupDownloadButton`'s visibility rule. Tapping a non-HTML resource with no local address shows a "Link not available" snackbar (Kotlin's `link_not_available` toast). The state check is a pure data read (`resourceOffline`) rather than a filesystem probe, so the screen stays testable under the fake clock — the actual file-exists check belongs to the viewer, as in Kotlin. Two new l10n keys (`view`, `linkNotAvailable`) added to all six locale files. 4 widget tests cover the four button states. Phase 88 complete — ported survey resume from pending submission: the `take_survey_screen` now accepts a `submissionId` parameter and loads the existing submission's answers into the form controllers, so a user who started a survey but didn't finish can resume from where they left off rather than starting fresh. The survey list's "Continue" action passes the pending submission id. Phase 87 complete — ported the inactive-user dashboard (`InactiveDashboardFragment`) to Flutter: when a logged-in, non-guest user has no roles and is not an admin, `home_screen` now renders `InactiveDashboardScreen` instead of the bell dashboard — a centered "User not activated, please contact administrator" message with a "Submit Feedback" button that opens the feedback create screen. The check mirrors `DashboardActivity.handleGuestAccess`'s `rolesList.isEmpty() && userAdmin != true` gate; guests fall through to the bell dashboard since their access is gated per-action via `showGuestDialog`. Two new l10n keys (`inactiveMessage`, `submitFeedback`) added to all six locale files. 5 widget tests cover all four branches (inactive user, user with roles, admin with no roles, guest) plus the feedback navigation. Phase 86 complete — ported the examination exit-confirmation dialog (`HealthExaminationActivity.finish()`) to Flutter: the `add_examination_screen` now wraps its Scaffold in a `PopScope(canPop: false)` that intercepts the system back gesture and shows a dialog asking the user to confirm leaving, since in-progress examination data would be lost. "Cancel" dismisses the dialog and stays; "Yes, I want to exit" pops the screen. Two new l10n keys (`cancelAddingExamination`, `yesIWantToExit`) added to all six locale files. 3 widget tests cover the back-gesture interception and both dialog buttons. Phase 85 complete — ported the health profile editor (`AddHealthActivity`) to Flutter: the `add_health_screen` now has the full form (first/middle/last name, email, dob via date picker, birth place, phone, emergency contact name/contact/type with a Phone/Email spinner, special needs, other needs), loads the existing user + health profile into the controllers on mount, and saves through `HealthRepository.saveHealthProfile` which preserves `emergencyContact`/`emergencyContactType` when a new value is empty (matching Kotlin's logic). `my_health_screen` now shows `birthPlace` and `language` in the user profile card (Kotlin's `txtBirthPlace`/`txtLanguage`). `time_utils.formatDateToDDMMYYYY` validates numeric date parts so a non-date like `not-a-date` no longer crashes the form. 6 widget tests for the editor (form load, birth place field, contact-type dropdown, save-button state, required-field validation, end-to-end save round-trip), 43 repository tests (including health profile save/load), and a `time_utils_test` suite. Phase 84 complete — full diagnosis list + custom diagnosis chip cloud. Phase 83 complete — health examination detail dialog. Phase 82 complete — text size changer + reset/clear data. Phase 81 complete — ported the challenge dialog: the `user_challenge_actions` Drift table (schema v43, preserved), `recordSyncUserChallengeAction`/`hasUserCompletedSync` on `ActivitiesRepository`, the `ChallengeEvaluator` provider (port of `DashboardViewModel.evaluateChallengeDialog`), the `ChallengeDialog` widget with localized strings, the home-screen wiring, and the sync-start call that lights up the "sync completed" task. 16 new tests. Phase 80 complete — ported `ef80dda52` toast-on-change behavior to the resource detail screen: the add/remove snackbar now fires only when shelf membership actually changed, not on every button press. Three widget tests added. Phase 79 complete — harvest audit of the 2026-08-24/25 upstream batch (33 commits): all refactors, performance rewrites, and Android-lifecycle concerns with no behavioural port needed. Phase 78 complete — the duplicate `normalizeText` removed, so chat search folds accents the same way resource and course search do. Phases 76–77 and two unnumbered ports (blood-pressure validation, personal-note attachments) written up. Phase 75 complete — chat full-conversation
search ported from `ChatViewModel.searchChats`: a `ChatSearchMode` enum with ranked matching
(prefix before contains, first conversation before later), recency sort by
`max(createdDate, updatedDate)`, and a hand-rolled NFD decomposition because
Dart has neither an NFD normalizer nor a `RegExp` that accepts the
`InCombiningDiacriticalMarks` block name. Phase 74 — voice emoji reactions
and team task comment threads, neither a Kotlin port; the reactions sync
round trip fixed, where the serializer wrote to the nested `news` object and
the mapper read the top level, erasing local reactions on the next pull.
Phase 73 — standalone WebView
screen, course step exam/survey buttons, and the team leaderboard from
the `14880` upstream branch. Phase 72 — the add-resource screen, team
leader actions, and member-detail wiring. Phase 71 — the member detail
screen, reached by tapping a member. Phase 70 — resource list sort
toggles. Phase 69 — the ARB derivation tool merges instead of
regenerating. Phase 68 — achievements. Phase 67 — tags and collections.)
**Phase**: 100 of N (all 28 UI packages have a screen — see Status for what that does and
does not mean)
