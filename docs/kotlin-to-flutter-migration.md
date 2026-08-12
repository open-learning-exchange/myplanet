# Kotlin → Flutter/Dart migration

Tracking document for migrating myPlanet from the **Kotlin/Android** app in `app/` to a
**Flutter/Dart** app in `flutter/`.

## Status

**Phase 34 complete.** The Flutter app is *not* yet a replacement for the Kotlin app:
**27 of 28 UI packages** have a screen, and a screen existing is not the same as the feature
working. All **six** locales the Kotlin offers now ship an `.arb` (partial coverage — see
*The 5 existing locales* below), which removes the outright-absent case that made a non-English
release a regression. Counted honestly:

- `enterprises` has no screens of its own; the teams slice (Phase 18) covers what it did.
- `components`: `CheckboxList` is used by four screens. `ChallengeDialog` and `CustomDropdown`
  are built and called from nowhere — they are library code waiting for a caller, not features.
- `user`: `BecomeMemberScreen` now POSTs the `_users` document when the server is reachable and
  adopts the server's PBKDF2 material, falling back to the local-only account when it is not —
  the shape Kotlin has.

Known gaps:
- Background work with no user present (`AutoSyncWorker`'s timed sync,
  `TaskNotificationWorker`'s deadline notifications, `DownloadWorker`'s background queue) needs
  OS scheduling and is not ported.
- Team attachments are unported. Personal-note attachments are: the note POSTs, then the file
  PUTs as a CouchDB attachment, best-effort and in that order, as Kotlin does.
- Public surveys reach `PublicSurveyScreen` only through a deep link whose URI carries an
  origin. Kotlin reads the host off the raw intent; Flutter sees a go_router location, so a
  link that arrives path-only falls back to the configured server and, for a respondent who
  has none, fails to load. Wiring a real deep-link plugin (`app_links` or equivalent) is the
  fix and is not done.
- The public-survey response is POSTed straight to the public API rather than through the
  outbox, so a submission composed offline is lost when the post fails. Kotlin has the same
  behaviour, so this is parity rather than regression, but it is the weakest write path in the
  port.

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

- **Phase 33** — the home profile card's completed-course star row. Porting
  `BellDashboardFragment.showBadges`/`setColor`/`openCourse`: one star per
  completed course, horizontally scrollable under the name line, tinted
  `colorPrimary` (`md_blue_700`) when the course is certified and
  `md_blue_grey_300` when it is not, tapping a star opening the course. The
  data path it depended on was already there — `syncCourseProgress` and
  `syncCertifications` landed in the course-progress slice — but `completedCourseIds`
  returned ids only and dropped the Kotlin's `hasValidId`/`hasValidTitle` guards,
  so a star would have rendered with an empty label. A new `completedCourses(userId)`
  returns `(courseId, courseTitle)` pairs and keeps those guards, since the star's
  content description is `"${completed_course} ${courseTitle}"`; the id-only set stays
  guard-free because the progress filter that reads it needs none. The
  `completedCoursesProvider` and `isCourseCertifiedProvider` mirror the Kotlin
  ViewModel's one-load-per-user and the fragment's per-star certification coroutine.

- **Phase 34** — the last three dashboard overflow items: About, Disclaimer and the
  language changer. `AboutScreen` renders the `about` HTML with the running version spliced
  in after the `<h3>MyPlanet</h3>` heading (`package_info_plus` for `versionName`, matching
  `AboutFragment`), `DisclaimerScreen` renders `disclaimer` with tappable links through
  `flutter_widget_from_html`, and the overflow menu now follows the Kotlin's order: sync,
  feedback, language, theme, about, disclaimer, settings, logout. Two corrections went in with
  it:
  - **`about` was defined twice in `app_en.arb`.** The page body was appended under the name
    the *menu label* already had. A duplicate key is legal JSON and the last one wins, so
    `gen-l10n` and `flutter analyze` both stayed silent while `l10n.about` — still read by
    `settings_screen.dart` as a section heading — became a two-thousand-character HTML
    document. The label is `actionAbout` (Kotlin's `action_about`), the body is `about`
    (Kotlin's `about`), and the settings heading now reads the former.
  - **Four more keys were already shadowed the same way** before this phase: `justNow`,
    `language`, `description`, `apply` (identical values, harmless) and `untitledResource`
    (*different* values — "Untitled resource" was dead, "Untitled Resource" shipped). All six
    are deduped keeping the value JSON already resolved to, so nothing changed at runtime.
    `test/l10n/arb_integrity_test.dart` now fails on a duplicate key, on a translated key
    English does not have, and on a body/label pair that has collapsed into one key.

    Worth stating plainly, because it is the same lesson as the preserved-table guard: no
    existing test asserted the settings About heading, so the suite was green with the bug in
    it. A screen having a test file is not the same as its strings being pinned.

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

- The rest of `82140152b`: the free-up-space button and available-space text inside the
  breakdown sheet. Free-up-space needs `FreeSpaceWorker`'s delete-and-mark-not-offline pass;
  available space needs a disk-stats plugin. The category-detail stubs that blocked this are
  now ported — `getResourceTitlesMap`, `markResourcesAsNotOffline`,
  `getOfflineResourceItems`, and `deleteOfflineResources` live on `ResourcesRepository`, and
  `StorageCategoryDetailScreen` reads from it (no more `_getResourceTitlesMap` returning
  `{}` or no-op `_markResourcesAsNotOffline`).
- `b8e98c550` / `2b39eb329`: the courses progress filter and sort toggle are not ported; when
  they are, implement the *new* semantics (progress filter over the whole library, `max`
  falling back to the step count; sort state living in the provider so it survives stream
  emissions).
- `4fdc7fcb1`: `BecomeMemberActivity`'s username validation is now debounced 300 ms with a
  stale-result guard; the port validates on submit only.
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

### "Sync now" has to push as well as pull

The Phase 32 Sync centre ran nine pulls and drained nothing. `OutboxDrainer.drain` had exactly
two callers — `OutboxDrainScope` (startup and resume) and the submissions screen's refresh — so
a user who worked offline, reconnected and pressed the button built for that moment got their
data pulled and sent nothing. Kotlin pairs the two: `SyncActivity` calls `startUpload` on a
forced sync. `DashboardSyncNotifier.drainOutbox` now runs at the end of `syncAll` and `retry`.

Two details are load-bearing:

- **It is gated on at least one area having succeeded.** Not tidiness: a drain attempted with no
  route to the server consumes one of the operation's five attempts and pushes its backoff out,
  so firing it after a wholly failed run spends the retry budget proving the network is still
  down.
- **A drain failure must not mark the sync failed.** Per-operation outcomes are already recorded
  in the outbox; the pulls genuinely succeeded.

`OutboxDrainer`'s own docstring had claimed drain ran "after a successful sync" for several
phases while nothing called it — a reminder that a doc comment is not a caller. The greps in *A
screen is not ported until something fills it and something leaves it* are the check that finds
this class of gap; run them on triggers, not just on mappers and routes.

`OutboxDrainScope` also had no test whatsoever — it never appeared in the coverage report,
because nothing ever loaded the file. Writing one surfaced a second asymmetry: the startup drain
routed a thrown error to `FlutterError.reportError`, but the resume path called `_drain()`
fire-and-forget with no guard, so a drain that fell over while foregrounding the app raised an
unhandled async error. Both paths now share the guard.

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
   scriptable, but `crowdin.yml` must be repointed at `flutter/lib/l10n/*.arb`. Phase 1 ships
   `app_en.arb` in full and `app_es.arb` populated **only** from strings that already exist in
   `values-es/strings.xml` -- nothing was machine-translated. Where a screen needs a string the
   Kotlin never had, the English is authored and the other locales are left absent so `gen-l10n`
   falls back and Crowdin can translate it properly. Arabic also needs an RTL pass.

   **As of Phase 34 all six locales ship an `.arb`.** For most of the port only `en` and `es`
   existed, so `ar`, `fr`, `ne` and `so` fell back to English in full — a language *regression*
   against a Kotlin app that has all five translated at ~100% (1,041 strings each). The same
   rule already used for Spanish was run for the other four:

   > A key is carried into a locale only when its `values-<loc>` counterpart is unambiguous —
   > the Kotlin string name normalises to the ARB key *and* its English text matches, or the
   > English text matches exactly and every candidate name shares one translation.

   with one widening, which is what took coverage from 25% to 37%: the English texts are
   compared with case and trailing punctuation folded. `sync` is `"Sync"` in the ARB and
   `"sync"` in `strings.xml`; `appTheme` is `"App theme"` against `"App Theme"`. Same key, same
   string — the capitalisation is not what got translated. A name match differing in
   *substance* is still skipped, because the translation answers a different string
   (`subjectLevel`: `"Subject"` vs `"Subject Level"`).

   Nothing is machine-translated, and nothing existing was overwritten: a reviewed entry always
   wins over the mechanical result. Where the two disagreed, the *committed* Spanish was
   sometimes the less faithful of the two — `amount` reads "Monto" where `values-es` says
   "Cantidad", and `myProgress` reads "Mi Progreso" where `values-es` ships the untranslated
   `miProgreso` this document elsewhere commits to reproducing. Those 13 disagreements are left
   as they are rather than silently rewritten; they want a human call.

   Current state, out of 641 translatable English keys:

   | | keys | note |
   |---|---|---|
   | `app_en.arb` | 641 | the template |
   | `app_es.arb` | 296 | 168 pre-existing + mechanical pass |
   | `app_fr.arb` | 240 | |
   | `app_ar.arb` / `app_ne.arb` / `app_so.arb` | 239 | |

   The ceiling is not 641. **311 keys have no Kotlin counterpart at all** — they are genuinely
   new phrasings the shipping app never had — and 50 more carry ICU placeholders that cannot be
   filled from Kotlin's printf strings (`%1$s` → `{count}`) without emitting a message
   `gen-l10n` refuses to compile. Of the ~280 keys a mechanical pass could reach, ~239 are
   carried. The rest, and every new phrasing, remain Crowdin's to fill; `gen-l10n` lists them on
   every build.

   The generator lives outside the repo deliberately — it reads `app/src/main/res`, which is the
   Kotlin app's tree, and re-running it is a rebase-time chore, not a build step. Two things are
   still open: `crowdin.yml` does not exist anywhere in this repository, so there is no
   configuration routing translators at `flutter/lib/l10n/*.arb`, and Arabic still needs an RTL
   layout pass now that it is reachable from the language changer.

   Two quirks are reproduced rather than corrected, because they are what Spanish users see in
   the shipping app today: `myCourses`/`myLife`/`myHealth`/`myPersonals`/`achievements` resolve to
   `misCursos`/`miVida`/`miSalud`/`misPersonales`/`misLogros` -- untranslated camelCase in
   `values-es` -- and `search` is lower-case in English but `Buscar` in Spanish. Both are upstream
   defects in `strings.xml`; fixing them in Crowdin corrects the Kotlin and Flutter apps together,
   whereas diverging here would make the two apps disagree.

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

`components`, `enterprises` -- plus team voices, team/public survey sharing, personal attachments/upload,
storage/retry, and the rest of `settings`, plus profile photo/upload, membership, and the rest of `user`,
and the rest of `sync` and `dashboard` (the home cards and pending-survey dialog are ported as of
Phase 33; still missing are the network ring, team alert badges, activity
chart, language/about/disclaimer overflow actions, OS-scheduled sync, and survey reminders).

**Notes on remaining packages:**
- `components` -- reusable utility widgets. `CheckboxList` is in use; `ChallengeDialog` and
  `CustomDropdown` exist with no callers. Most of what the Kotlin package did is handled by
  Flutter's built-in widgets.
- `enterprises` -- financial reports for teams. Already covered by `team_reports_screen.dart` in the
  teams slice (Phase 18). The Kotlin package is a separate UI layer over the same team data.

**Completed infrastructure:** ratings upload (`RatingsUploader`), offline maps, and storage
management all landed in Phase 25.

Course progress and certification are deliberately deferred with their own packages rather than bundled
into the courses slice. `events` and `surveys` are now ported for the individual case; team meetups
and team/public survey sharing arrive with `teams`.

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

---

**Last updated**: 2026-08-12 (Phase 34 complete)
**Phase**: 34 of N (27 of 28 UI packages have a screen — see Status for what that does and does
not mean)
