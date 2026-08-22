# Kotlin → Flutter/Dart migration

Tracking document for migrating myPlanet from the **Kotlin/Android** app in `app/` to a
**Flutter/Dart** app in `flutter/`.

## Status

**Phase 57 complete.** The Flutter app is *not* yet a replacement for the Kotlin app:
**27 of 28 UI packages** have a screen, and a screen existing is not the same as the feature
working. Counted honestly:

- `enterprises` has no screens of its own; the teams slice (Phase 18) covers what it did.
- `components`: `CheckboxList` is used by four screens. `ChallengeDialog` and `CustomDropdown`
  are built and called from nowhere — they are library code waiting for a caller, not features.
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
  than through a repository method.

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
to this phase's synchronous path.

---

**Last updated**: 2026-08-21 (Phase 61 complete — dashboard health key/IV
sync-in ported with the 9f3fac1d9 re-entrancy guard; the stale
courses-progress-filter spec-debt entry corrected. Phase 60 — three further
duplicate ARB keys removed and guarded by a test that reads the source text
rather than the parsed map; the `minapk` comparator reads the runtime app
version with a conservative fallback. Phase 59 — app version/build read at
runtime through package_info_plus behind a testable seam; the last
correctness gap Phase 58 flagged is closed; the team detail screens
backfilled with 33 widget tests; duplicate `untitledResource` ARB key
split into `untitledResource`/`untitledResourceTitle`)
**Phase**: 61 of N (27 of 28 UI packages have a screen — see Status for what that does and
does not mean)
