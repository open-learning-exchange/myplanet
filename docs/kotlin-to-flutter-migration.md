# Kotlin → Flutter/Dart migration

Tracking document for migrating myPlanet from the **Kotlin/Android** app in `app/` to a
**Flutter/Dart** app in `flutter/`.

## Status

**Phase 41 complete.** The Flutter app is *not* yet a replacement for the Kotlin app:
**27 of 28 UI packages** have a screen, and a screen existing is not the same as the feature
working. Counted honestly:

- `enterprises` has no screens of its own; the teams slice (Phase 18) covers what it did.
- `components`: `CheckboxList` is used by four screens. `ChallengeDialog` and `CustomDropdown`
  are built and called from nowhere — they are library code waiting for a caller, not features.
- `user`: `BecomeMemberScreen` now POSTs the `_users` document when the server is reachable and
  adopts the server's PBKDF2 material, falling back to the local-only account when it is not —
  the shape Kotlin has.

Known gaps:
- `DownloadWorker`'s background queue is the last unported `WorkManager` job. Timed background
  *sync* landed in Phase 38 and `TaskNotificationWorker`'s deadline notifications in Phase 42,
  both on the `workmanager` plugin, so what remains is one job rather than the whole category.
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
- Device and tablet usage telemetry (`myplanet_activities`, `MyPlanet.getTabletUsages`) is
  unported: it needs a device-info plugin, and the same absence is why the activity documents
  the port posts omit the `androidId`/`deviceName`/`customDeviceName` trio. Challenge actions
  (`user_challenge_actions`) are unported because the challenge feature has no screen here.

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
  `DownloadWorker`'s background queue still needs OS scheduling and is not ported.
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
  - **components**: `CheckboxList` is used by four screens. `ChallengeDialog` and
    `CustomDropdown` are built and called from nowhere.

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
  the "remind later" reminder scheduler.

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
team-name lookups, and anything the moderator gates protect.

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
- `b8e98c550` / `2b39eb329`: the courses progress filter and sort toggle are not ported; when
  they are, implement the *new* semantics (progress filter over the whole library, `max`
  falling back to the step count; sort state living in the provider so it survives stream
  emissions).
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
- `9f3fac1d9`: the dashboard key/IV sync (not ported) gained an in-flight re-entrancy guard —
  carry it when that flow is ported.
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

   The other four locales are worse off than that number suggests: there is no `app_ar.arb`,
   `app_fr.arb`, `app_ne.arb` or `app_so.arb` at all, so Arabic, French, Nepali and Somali users
   get English for **everything**, where the Kotlin app has real `values-{ar,fr,ne,so}` files to
   carry over. That conversion is the mechanical, scriptable part described above and remains
   undone.

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
*within* ported packages, as of Phase 39:

- `user` -- profile photo *upload* (`PhotoUploader`, `updateUserImage`); displaying the photo
  landed in Phase 36. Membership registration landed in Phase 27, and `BecomeMemberActivity`'s
  debounced username validation landed in Phase 39.
- `settings` -- nothing open. The free-up-space button and available-space text inside the
  storage sheet landed in this slice: `ResourcesRepository.freeUpSpace` (the
  `FreeSpaceWorker.doWork` delete-and-mark-not-offline pass) plus a `DiskStats` seam over a
  `disk_stats` method channel (`StorageStatsManager`) for the available/total figures.
- `dashboard` -- nothing open on the OS-scheduled side except downloads: the `AutoSyncWorker` half
  landed in Phase 38 through the `workmanager` plugin and `TaskNotificationWorker`'s deadline
  notifications in Phase 42 on the same plugin's `maintenance` cadence. `DownloadWorker`'s queue
  remains open.
- `sync` -- OS-scheduled background work, a platform gap rather than a screen.

Earlier revisions of this list also named team voices, team/public survey sharing, personal
attachments, and team attachments; team attachments (receipt images on finance transactions and
reports, with their upload write-back and sync-in download) landed via `TeamAttachments`, and the
others landed in Phases 26 and 28, and deep links and the durable public-survey response in Phase 35.

**Notes on remaining packages:**
- `components` -- reusable utility widgets. `CheckboxList` is in use; `ChallengeDialog` and
  `CustomDropdown` exist with no callers. Most of what the Kotlin package did is handled by
  Flutter's built-in widgets.
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
  Kotlin commit is **deferred** (see below).

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
  destination-view routing — see **deferred** below.
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

- `c5141b658`'s mandatory-survey toast — the Kotlin, on finish, also shows a
  toast if the course has an attached mandatory survey the user has not yet
  submitted. The port's `Surveys` table has no `courseId`, so
  course-attached surveys are not modeled; the toast is not portable to the
  current schema.
- `a08fc5662`'s `subType` on `AppNotification` — enables notification
  destination-view routing the port does not yet have (the port's
  notification `onTap` only marks-as-read; there is no navigation to a team/
  course/resource destination). The field would land with that routing.
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
  its `Result.onFailure` as an error toast. The port has no batch resource
  selection / removal screen and no `saveSearchActivity` / `getFilterFacets`
  (search-activity logging is unported), and its single-resource shelf removal
  in `resource_detail_screen.dart` already shows a failure snackbar. Deferred
  (lands on the unported batch-resources path); the error-surfacing half is
  already present.

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
  port has no enterprises screens of its own; the teams slice covers
  creation/edit/archive. Deferred, as the Status note says.
- `08e18ffdc` (dashboard library card my/call split) — distinguishes the "my
  shelf" resources filter from the full library in the dashboard card target.
  The port's library card opens resources outright, like the rest of the
  unported batch-resources/filter gap. Deferred.
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

### Known gap: other uploaders

The `androidId`/`deviceName`/`customDeviceName` fields the Kotlin adds to
**submissions, personals, ratings, and teams** uploads are still absent. Adding them
to those serializers is a cross-cutting sweep through every uploader; this phase
added them to the *activity* serializers only, matching the scoped task. The
`DeviceStats` seam now exists, so closing the gap is a matter of threading it
through each remaining uploader's constructor and doc builder.

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

**Last updated**: 2026-08-19 (Phase 42 complete — `TaskNotificationWorker`'s deadline notifications
ported on the `maintenance` cadence: `TaskDeadlineNotifier` policy + `NotificationPresenter` seam +
`team_tasks.isNotified` at schema v32. `DownloadWorker`'s queue is the last unported WorkManager
job)
**Phase**: 42 of N (27 of 28 UI packages have a screen — see Status for what that does and
does not mean)
