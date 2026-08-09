# Title corpus — the last 500 merged PRs

Regenerated 2026-08-09 from the 500 most recent squash commits on `master`
(`abc0dfd`, PR #15448 / issue #15449, back to `2bb4024`, PR #14167 / issue #14200).
Each line pairs the landed title with the changed files that produced it,
because **the changed files are the primary input to the title** — skim for the
nearest precedent by scope, then by the layer of the files you changed.

Path shorthand: bare paths are under `app/src/main/java/org/ole/planet/myplanet/`;
`test/` is `app/src/test/java/org/ole/planet/myplanet/`; `res/` is
`app/src/main/res/`. `app/build.gradle` (the per-PR version bump, present in
nearly every diff) is omitted.

## What 500 PRs add to the 200-commit rules

The grammar in SKILL.md was distilled from 200 commits; this corpus is 2.5× that
window, and the rules hold. What the wider window sharpens or adds:

- **Shape shares are stable.** `smoother` 445/500 (89%), `less … is more` 37
  (7.4%), `bump` 12 (2.4%), broken 6. Nothing new to learn here — `smoother`
  stays the default.
- **Scope league table:** `all` 133, `teams` 76, `courses` 68, `sync` 55,
  `resources` 51, `login` 28, `life` 26, `dashboard` 23, `chat` 21, `actions` 6,
  `enterprises` 5, `community` 3, `feedback` 1. A `feedback:` scope exists
  (missing from the SKILL.md table) — one use, for `FeedbackDao`/repository work.
- **Gerund league table (top of 445 `smoother` titles):** modelling 40 ·
  handling 27 · testing 27 · inserting 17 · providing 17 · caching 15 ·
  managing 12 · diffing 11 · collecting 11 · adapting 10 · importing 10 ·
  flowing 10 · querying 10. `modelling` is always the pair **view modelling**
  (40 of 40; the log's single `modeling` is a typo — double-l is canonical).
- **The Flow pair the 200-window missed:** `flowing` = a repository, DAO, or
  controller *starts exposing* a `Flow` (`SubmissionsRepositoryImpl` →
  `courses: smoother submissions repository flowing`); `collecting` = a
  Fragment/Activity *collects* one (`BellDashboardFragment` → `dashboard:
  smoother bell flow collecting`). Producer side gets `flowing`, consumer side
  gets `collecting` — 21 titles split cleanly on this line.
- **`handling` is the licensed fallback.** When the principal file is a
  Fragment or Activity and no sharper operation word applies, the log says
  `handling` (27 uses) — not `fragmenting`, not a stretched metaphor.
- **The suffix-conversion rule is ironclad.** As nouns, `fragment`, `activity`
  and `worker` appear **zero** times in 500 titles; `adapter` and `module` 3
  each, `manager` 5. Suffixes convert to gerunds or vanish. Even one-off
  classes obey it: `BaseAdapterFactory.kt` → `factoring`.
- **`repository` stays the champion noun** — 113 uses (+11 `repositories`,
  +28 `utils`), because those layers have no natural gerund and keep their
  noun while the operation supplies the gerund.
- **Coverage of the mechanical rule:** 216/500 diffs touch a single file
  beyond the version bump, and 372/500 touch ≤3. Three quarters of all titles
  are a pure function of one to three filenames.
- **`less` arrives in sweeps.** Eleven of the 37 `less` titles landed in one
  dead-code campaign (#14555–#14569), most shaped `<scope>: less repository
  methods is more`. Outside sweeps it is rare — roughly one PR in twenty.
- **The `(fixes #N)` link fails ~2% of the time, always by typo:**
  `{fixes #14889)` (brace), `(fixes 14801)` and `(fixes 14266)` (missing `#`),
  `(#14755)` (missing the word), and one title with no link at all. 489/500 got
  it exactly right; the six broken ones are preserved under Counterexamples.
- **Era vocabulary is fine.** `realm` appears in 21 titles from the
  Realm-to-Room migration and then disappears. Titles name what the diff
  touches *today*; don't sand off project-phase words.

## all (132)

- `all: smoother coderabbit configuring (fixes #15449)`
  ← .coderabbit.yaml, CLAUDE.md, crowdin.yml, docs/AGENT_SPELLBOOK.md, docs/CODE_STYLE_GUIDE.md
- `all: smoother claude assisting (fixes #15447)`
  ← CLAUDE.md, test/model/UserEntityTest.kt, docs/AGENT_SPELLBOOK.md, docs/CODE_STYLE_GUIDE.md, docs/DOMAIN_MODEL.md, +1 more
- `all: less service module injection is more (fixes #15434)`
  ← di/CoreDependenciesEntryPoint.kt, di/ServiceModule.kt, services/sync/RealtimeSyncManager.kt, ui/sync/RealtimeSyncMixin.kt, test/services/sync/RealtimeSyncManagerTest.kt
- `all: smoother user repository json array merging (fixes #15431)`
  ← repository/UserRepositoryImpl.kt
- `all: smoother dialog utils showing (fixes #15430)`
  ← utils/DialogUtils.kt
- `all: smoother theme managing (fixes #15424)`
  ← MainApplication.kt, services/ThemeManager.kt, ui/dashboard/DashboardActivity.kt, ui/settings/SettingsActivity.kt, ui/sync/LoginActivity.kt, +1 more
- `all: smoother download server checking (fixes #15410)`
  ← MainApplication.kt
- `all: smoother settings text capitalizing (fixes #5488)`
  ← res/values/strings.xml
- `all: smoother network utils lazy entering (fixes #15402)`
  ← utils/NetworkUtils.kt
- `all: smoother logo landscape handling (fixes #15262)`
  ← res/layout/app_bar_bell.xml
- `all: smoother claude assisting (fixes #15250)`
  ← CLAUDE.md, ui/settings/StorageCategoryDetailFragment.kt, ui/surveys/PublicSurveyActivity.kt, docs/realm-to-room-migration.md
- `all: smoother importing (fixes #15248)`
  ← base/BaseContainerFragment.kt, base/BaseResourceFragment.kt, repository/ChatRepositoryImpl.kt, repository/CoursesRepository.kt, repository/RatingsRepository.kt, +46 more
- `all: smoother settings storage space organizing (fixes #14958)`
  ← ui/settings/SettingsActivity.kt, ui/settings/StorageBreakdownFragment.kt, res/layout/fragment_storage_breakdown.xml, res/values-ar/strings.xml, res/values-es/strings.xml, +5 more
- `all: smoother configurations repository check version testing (fixes #15218)`
  ← test/repository/ConfigurationsRepositoryImplTest.kt
- `all: smoother settings free space handling (fixes #15217)`
  ← ui/settings/SettingsActivity.kt
- `all: smoother storage category detail callback diffing (fixes #15210)`
  ← ui/settings/StorageCategoryDetailFragment.kt
- `all: smoother user repository parse leaders testing (fixes #15207)`
  ← test/repository/UserRepositoryImplParseLeadersTest.kt
- `all: smoother base adapter factoring (fixes #15201)`
  ← base/BaseAdapterFactory.kt, base/BaseRecyclerFragment.kt, ui/courses/CoursesAdapter.kt, ui/courses/CoursesFragment.kt, ui/resources/ResourcesAdapter.kt, +2 more
- `all: smoother payload constants handling (fixes #15199)`
  ← ui/courses/CoursesAdapter.kt, ui/courses/InlineResourceAdapter.kt, ui/resources/ResourcesAdapter.kt, ui/teams/members/MembersAdapter.kt, ui/user/AchievementsAdapter.kt, +1 more
- `all: bump `androidx.constraintlayout:constraintlayout` to 2.2.2 (fixes #15192)`
  ← gradle/libs.versions.toml
- `all: smoother image utils loading (fixes #15159)`
  ← base/BaseDashboardFragment.kt, ui/health/MyHealthFragment.kt, ui/sync/LoginActivity.kt, ui/teams/members/MembersDetailFragment.kt, ui/user/AchievementFragment.kt, +2 more
- `all: less notifications repository survey id is more (fixes #15180)`
  ← repository/NotificationsRepository.kt, repository/NotificationsRepositoryImpl.kt, ui/notifications/NotificationsAdapter.kt, ui/notifications/NotificationsFragment.kt, ui/notifications/NotificationsViewModel.kt
- `all: smoother settings accessibility text size configuring (fixes #14894)`
  ← base/BaseActivity.kt, ui/settings/SettingsActivity.kt, utils/LocaleUtils.kt, res/values-ar/strings.xml, res/values-es/strings.xml, +6 more
- `all: smoother notifications read view modelling (fixes #15153)`
  ← ui/notifications/NotificationsViewModel.kt
- `all: less network dependencies entry point is more (fixes #15143)`
  ← MainApplication.kt, di/NetworkDependenciesEntryPoint.kt, di/ServiceDependenciesEntryPoint.kt
- `all: less user session main dispatcher is more (fixes #15141)`
  ← services/UserSessionManager.kt
- `all: smoother server url reachability mapping (fixes #15134)`
  ← services/sync/ServerUrlMapper.kt, ui/viewer/ResourceViewerViewModel.kt, test/services/sync/ServerUrlMapperTest.kt
- `all: smoother feedback caching (fixes #15124)`
  ← ui/feedback/FeedbackAdapter.kt, res/drawable/bg_grey.xml, test/ui/feedback/TintTest.kt
- `all: smoother text watchers annotations suppressing (fixes #15119)`
  ← base/BaseExamFragment.kt, ui/chat/ChatDetailFragment.kt, ui/courses/CourseFilterController.kt, ui/exam/ExamTakingFragment.kt
- `all: smoother user repository json applying (fixes #15117)`
  ← repository/UserRepositoryImpl.kt
- `all: smoother crash log store time providing (fixes #15116)`
  ← MainApplication.kt, utils/CrashLogStore.kt, test/utils/CrashLogStoreTest.kt
- `all: smoother notification utils time providing (fixes #15106)`
  ← services/TaskNotificationWorker.kt, utils/NotificationUtils.kt, test/utils/NotificationUtilsTest.kt
- `all: less download resources repository entry point is more (fixes #15104)`
  ← di/CoreDependenciesEntryPoint.kt, di/ResourcesRepositoryEntryPoint.kt, utils/DownloadUtils.kt
- `all: smoother file utils time providing (fixes #15103)`
  ← base/BaseResourceFragment.kt, ui/dashboard/BellDashboardFragment.kt, ui/enterprises/EnterprisesFinancesFragment.kt, ui/enterprises/EnterprisesReportsFragment.kt, utils/FileUtils.kt
- `all: less pager adapter diff utils is more (fixes #15099)`
  ← ui/courses/CoursesPagerAdapter.kt, ui/teams/TeamPagerAdapter.kt
- `all: smoother time utils providing (fixes #15096)`
  ← ui/dashboard/DashboardActivity.kt, ui/settings/SettingsActivity.kt, ui/sync/SyncActivity.kt, ui/user/UserProfileFragment.kt, utils/TimeUtils.kt
- `all: smoother notifications text caching (fixes #15093)`
  ← model/NotificationListItem.kt, ui/notifications/NotificationsAdapter.kt
- `all: smoother settings guest users handling (fixes #14959)`
  ← ui/settings/SettingsActivity.kt
- `all: bump `com.android.tools.build:gradle` to 9.3.1 (fixes #15078)`
  ← gradle/libs.versions.toml
- `all: smoother user repository batch deleting (fixes #14942)`
  ← data/room/dao/LegacyEntityDaos.kt, repository/UserRepositoryImpl.kt
- `all: smoother markdown image loading (fixes #14939)`
  ← utils/MarkdownUtils.kt, gradle/libs.versions.toml
- `all: smoother application time providing (fixes #14936)`
  ← MainApplication.kt, di/CoreDependenciesEntryPoint.kt
- `all: smoother default dispatcher provider utils testing (fixes #14935)`
  ← test/utils/DefaultDispatcherProviderTest.kt
- `all: smoother item reorder helper utils testing (fixes #14934)`
  ← test/utils/ItemReorderHelperTest.kt
- `all: smoother top right menu items capitalizing (fixes #14873)`
  ← res/values-es/strings.xml, res/values-fr/strings.xml, res/values/strings.xml
- `all: smoother room database configuring (fixes #14888)`
  ← app/src/androidTest/java/org/ole/planet/myplanet/data/DatabaseServiceTest.kt, app/src/androidTest/java/org/ole/planet/myplanet/model/RealmUserTest.kt, MainApplication.kt, base/BaseContainerFragment.kt, base/BaseDashboardFragment.kt, +371 more
- `all: smoother importing (fixes #14887)`
  ← .claude/settings.json, ui/dashboard/DashboardActivity.kt, ui/health/MyHealthFragment.kt
- `all: less notification bell icon list item is more (fixes #14859)`
  ← ui/notifications/NotificationsAdapter.kt, res/layout/row_notifications.xml
- `all: smoother notification item sorting (fixes #14858)`
  ← ui/notifications/NotificationsFragment.kt, res/layout/fragment_notifications.xml, res/layout/spinner_item_right.xml
- `all: smoother text view extensions utils coloring (fixes #14831)`
  ← utils/TextViewExtensions.kt
- `all: smoother deep linking (fixes #14870)`
  ← app/src/main/AndroidManifest.xml, repository/SubmissionsRepository.kt, repository/SubmissionsRepositoryImpl.kt, repository/SurveysRepository.kt, repository/SurveysRepositoryImpl.kt, +11 more
- `all: smoother importing (fixes #14823)`
  ← base/BaseTeamFragment.kt, repository/RatingsRepository.kt, repository/RatingsRepositoryImpl.kt, repository/UserRepository.kt, services/ServerReachabilityWorker.kt, +16 more
- `all: smoother view model scoping (fixes #14820)`
  ← ui/courses/ProgressViewModel.kt, ui/feedback/FeedbackDetailViewModel.kt, ui/feedback/FeedbackListViewModel.kt, ui/notifications/NotificationsViewModel.kt, ui/ratings/RatingsViewModel.kt, +12 more
- `all: smoother onboarding server hint handling (fixes #14810)`
  ← ui/onboarding/OnboardingActivity.kt, res/layout/activity_onboarding.xml, res/values-ar/strings.xml, res/values-es/strings.xml, res/values-fr/strings.xml, +3 more
- `all: smoother repositories api typed parameters writing (fixes #14819)`
  ← model/FinanceReportParams.kt, model/MeetupCreationParams.kt, model/MemberInfo.kt, repository/EventsRepository.kt, repository/EventsRepositoryImpl.kt, +14 more
- `all: smoother feedback detail stats replying (fixes #14817)`
  ← ui/feedback/FeedbackDetailActivity.kt, ui/feedback/FeedbackReplyAdapter.kt, ui/user/StatsAdapter.kt, ui/user/UserProfileFragment.kt
- `all: smoother settings user data processing (fixes #14815)`
  ← ui/settings/SettingsActivity.kt, ui/sync/ProcessUserDataActivity.kt
- `all: smoother importing (fixes #14814)`
  ← base/BaseDashboardFragment.kt, repository/ResourcesRepositoryImpl.kt, repository/VoicesRepositoryImpl.kt, services/VoicesLabelManager.kt, ui/chat/ChatHistoryFragment.kt, +5 more
- `all: smoother diff callback naming (fixes #14784)`
  ← ui/feedback/FeedbackDetailActivity.kt, ui/personals/PersonalsAdapter.kt, ui/settings/StorageCategoryDetailFragment.kt, ui/submissions/QuestionAnswerAdapter.kt, ui/teams/TeamsAdapter.kt, +5 more
- `all: smoother application anr watchdog scoping (fixes #14780)`
  ← MainApplication.kt, utils/ANRWatchdog.kt
- `all: smoother settings retry queue details view modelling (fixes #14776)`
  ← ui/settings/SettingsViewModel.kt
- `all: smoother user repository guest creating (fixes #14777)`
  ← repository/UserRepositoryImpl.kt
- `all: smoother base recycler view creating (fixes #14769)`
  ← base/BaseRecyclerFragment.kt
- `all: smoother upload repository realm utils handling (fixes #14768)`
  ← repository/UploadRepositoryImpl.kt, services/upload/UploadCoordinator.kt, utils/RealmUtils.kt
- `all: bump `org.jetbrains.kotlin:kotlin-*` to 2.4.10 (fixes #14767)`
  ← gradle/libs.versions.toml
- `all: bump `com.android.tools.build:gradle` to 9.3.0 (fixes #14766)`
  ← gradle/libs.versions.toml
- `all: smoother view extensions utils flow testing (fixes #14763)`
  ← test/utils/ViewExtensionsTest.kt
- `all: smoother tags repository children processing (fixes #14761)`
  ← repository/TagsRepositoryImpl.kt
- `all: smoother tags repository bulk linking (fixes #14760)`
  ← repository/TagsRepositoryImpl.kt
- `all: smoother realm database indexing (fixes #14657)`
  ← data/DatabaseService.kt, data/RealmMigrations.kt, model/RealmMyPersonal.kt, model/RealmNews.kt, model/RealmSubmission.kt
- `all: smoother server url mapper testing (fixes #14757)`
  ← test/services/sync/ServerUrlMapperTest.kt
- `all: smoother dimen utils testing (fixes #14756)`
  ← test/utils/DimenUtilsTest.kt
- `all: smoother text view extensions testing (fixes #14754)`
  ← test/utils/TextViewExtensionsTest.kt
- `all: smoother tags repository database testing (fixes #14753)`
  ← test/repository/TagsRepositoryImplTest.kt
- `all: bump `glide` to 5.0.9 (fixes #14744)`
  ← gradle/libs.versions.toml
- `all: smoother importing (fixes #14672)`
  ← repository/CoursesRepositoryImpl.kt, repository/ResourcesRepositoryImpl.kt, repository/TeamsRepositoryImpl.kt, ui/chat/ChatViewModel.kt, ui/courses/CourseDetailFragment.kt, +12 more
- `all: bump `com.google.crypto.tink` to 1.23.0 (fixes #14666)`
  ← gradle/libs.versions.toml
- `all: bump `com.google.devtools.ksp` to 2.3.10 (fixes #14665)`
  ← gradle/libs.versions.toml
- `all: smoother readme formatting (fixes #14647)`
  ← README.md
- `all: smoother importing (fixes #14645)`
  ← app/src/androidTest/java/org/ole/planet/myplanet/model/RealmUserTest.kt, MainApplication.kt, base/BaseDashboardFragment.kt, base/BaseResourceFragment.kt, model/RealmMyCourse.kt, +32 more
- `all: smoother feedback repository upload pending (fixes #14634)`
  ← repository/FeedbackRepository.kt, repository/FeedbackRepositoryImpl.kt, repository/UploadRepositoryImpl.kt, services/upload/UploadConfig.kt, services/upload/UploadConfigs.kt, +1 more
- `all: smoother utilities entry points caching (fixes #14624)`
  ← services/ThemeManager.kt, utils/NetworkUtils.kt, utils/SyncTimeLogger.kt, test/services/ThemeManagerTest.kt
- `all: smoother database migrations schema resetting (fixes #14431)`
  ← data/DatabaseService.kt, data/RealmMigrations.kt, test/data/RealmMigrationsTest.kt
- `all: smoother guest dialog utils handling (fixes #14592)`
  ← ui/courses/CoursesFragment.kt, ui/dashboard/BellDashboardFragment.kt, ui/dashboard/DashboardActivity.kt, ui/dashboard/DashboardPluginFragment.kt, ui/resources/ResourcesFragment.kt, +2 more
- `all: smoother network traffic tagging (fixes #14591)`
  ← MainApplication.kt, di/NetworkModule.kt, utils/Constants.kt
- `all: smoother server reach entry point caching (fixes #14588)`
  ← MainApplication.kt
- `all: smoother user array adapting (fixes #14585)`
  ← ui/user/UserArrayAdapter.kt
- `all: less gradle kotlin compiler options is more (fixes #14561)`
  ← app/build.gradle
- `all: less realm apk log model methods is more (fixes #14566)`
  ← model/RealmApkLog.kt
- `all: less base recycler show no filter is more (fixes #14565)`
  ← base/BaseRecyclerFragment.kt, test/base/BaseRecyclerFragmentCompanionTest.kt
- `all: less base recycler methods is more (fixes #14568)`
  ← base/BaseRecyclerFragment.kt, test/base/BaseRecyclerFragmentTest.kt
- `all: less repository injections is more (fixes #14560)`
  ← ui/health/HealthExaminationActivity.kt, ui/resources/AddResourceFragment.kt, ui/settings/StorageBreakdownFragment.kt, ui/sync/LoginActivity.kt
- `all: smoother notifications repository batch querying (fixes #14558)`
  ← repository/NotificationsRepositoryImpl.kt
- `all: less activities submissions repositories methods is more (fixes #14556)`
  ← repository/ActivitiesRepository.kt, repository/ActivitiesRepositoryImpl.kt, repository/SubmissionsRepository.kt, repository/SubmissionsRepositoryImpl.kt
- `all: smoother importing (fixes #14464)`
  ← repository/HealthRepositoryImpl.kt, ui/courses/CoursesViewModel.kt, ui/events/EventsAdapter.kt, ui/events/EventsDetailFragment.kt, utils/NotificationUtils.kt, +6 more
- `all: smoother claude assisting (fixes #14450)`
  ← CLAUDE.md
- `all: smoother user repository realm testing (fixes #14453)`
  ← app/src/androidTest/java/org/ole/planet/myplanet/model/RealmUserTest.kt, repository/UserRepositoryImpl.kt
- `all: smoother database service realm testing (fixes #14452)`
  ← app/src/androidTest/java/org/ole/planet/myplanet/data/DatabaseServiceTest.kt
- `all: smoother docs domain modelling (fixes #14461)`
  ← docs/DOMAIN_MODEL.md
- `all: smoother docs testing (fixes #14460)`
  ← docs/TESTING.md
- `all: bump `com.google.dagger:hilt` to 2.60.1 (fixes #14459)`
  ← gradle/libs.versions.toml
- `all: smoother notifications repository realm compacting (fixes #14418)`
  ← data/DatabaseService.kt, repository/NotificationsRepository.kt, repository/NotificationsRepositoryImpl.kt, services/DownloadService.kt, services/TaskNotificationWorker.kt
- `all: smoother free space working (fixes #14403)`
  ← services/FreeSpaceWorker.kt, test/services/FreeSpaceWorkerTest.kt
- `all: less jvm inter op annotations is more (fixes #14422)`
  ← CLAUDE.md, base/BaseDialogFragment.kt, base/BasePermissionActivity.kt, base/BaseVoicesFragment.kt, model/MyPlanet.kt, +55 more
- `all: less main dispatcher rule is more (fixes #14440)`
  ← test/MainDispatcherRule.kt, test/ui/enterprises/EnterprisesFinancesViewModelTest.kt, test/ui/ratings/RatingsViewModelTest.kt, test/ui/voices/NewsViewModelTest.kt
- `all: smoother crash logs storing (fixes #14397)`
  ← MainApplication.kt, utils/CrashLogStore.kt, test/utils/CrashLogStoreTest.kt
- `all: smoother inline fully qualified naming (fixes #14446)`
  ← app/src/androidTest/java/org/ole/planet/myplanet/data/DatabaseServiceTest.kt, app/src/androidTest/java/org/ole/planet/myplanet/model/RealmUserTest.kt, MainApplication.kt, base/BasePermissionActivity.kt, base/BaseRecyclerFragment.kt, +155 more
- `all: smoother importing (fixes #14416)`
  ← di/ServiceModule.kt, services/DownloadService.kt, services/DownloadWorker.kt, services/ServerReachabilityWorker.kt, services/sync/RealtimeSyncManager.kt, +32 more
- `all: bump `androidx.hilt:hilt-*` to 1.4.0 (fixes #14398)`
  ← gradle/libs.versions.toml
- `all: smoother utilities toasting (fixes #14387)`
  ← utils/Utilities.kt, test/model/RealmUserTest.kt, test/utils/UtilitiesTest.kt
- `all: less realm dispatcher provider shutdown is more (fixes #14378)`
  ← MainApplication.kt
- `all: smoother notification utils channel creating (fixes #14368)`
  ← utils/NotificationUtils.kt
- `all: smoother user repository first copy finding (fixes #14362)`
  ← repository/UserRepositoryImpl.kt
- `all: smoother ratings notifications repositories copy finding (fixes #14361)`
  ← repository/NotificationsRepositoryImpl.kt, repository/RatingsRepositoryImpl.kt, test/repository/RatingsRepositoryImplTest.kt
- `all: smoother storage category detail diffing (fixes #14357)`
  ← ui/settings/StorageCategoryDetailFragment.kt
- `all: smoother camera utils dispatcher providing (fixes #14356)`
  ← utils/CameraUtils.kt
- `all: smoother settings free space working (fixes #14355)`
  ← ui/settings/SettingsActivity.kt
- `all: smoother docs code style guiding (fixes #14351)`
  ← docs/CODE_STYLE_GUIDE.md
- `all: less android manifest permission is more (fixes #14277)`
  ← app/src/main/AndroidManifest.xml
- `all: smoother service module importing (fixes #14263)`
  ← di/ServiceModule.kt
- `all: bump `gradle-wrapper` to 9.6.1 (fixes #14269)`
  ← gradle/wrapper/gradle-wrapper.properties
- `all: smoother time utils providing (fixes #14247)`
  ← base/BasePermissionActivity.kt, di/ServiceModule.kt, di/TimeModule.kt, repository/ActivitiesRepositoryImpl.kt, repository/ConfigurationsRepositoryImpl.kt, +33 more
- `all: bump `com.google.dagger:hilt-android` to 2.60 (fixes #14258)`
  ← gradle/libs.versions.toml
- `all: smoother importing (fixes #14251)`
  ← model/RealmHealthExamination.kt, repository/FeedbackRepositoryImpl.kt, repository/RealmRepository.kt, repository/ResourcesRepositoryImpl.kt, repository/UploadRepositoryImpl.kt, +14 more
- `all: smoother glide memory building (fixes #14232)`
  ← utils/TaggedGlideModule.kt
- `all: smoother settings view modelling (fixes #14245)`
  ← ui/settings/SettingsActivity.kt, ui/settings/SettingsViewModel.kt
- `all: smoother realm repository query list flowing (fixes #14236)`
  ← repository/RealmRepository.kt, test/repository/RealmRepositoryTest.kt
- `all: smoother view modelling (fixes #14235)`
  ← ui/chat/ChatViewModel.kt, ui/teams/TeamViewModel.kt, ui/teams/voices/TeamsVoicesViewModel.kt, ui/voices/LabelManipulator.kt, ui/voices/NewsViewModel.kt, +1 more
- `all: smoother network monitor working (fixes #14209)`
  ← services/NetworkMonitorWorker.kt
- `all: smoother user data processing (fixes #14208)`
  ← ui/sync/ProcessUserDataActivity.kt
- `all: smoother user data processing (fixes #14204)`
  ← ui/sync/ProcessUserDataActivity.kt
- `all: smoother settings category detail storing (fixes #14203)`
  ← ui/settings/StorageCategoryDetailFragment.kt

## teams (74)

- `teams: smoother voices removing (fixes #15407)`
  ← ui/voices/VoicesAdapter.kt
- `teams: smoother repository members leader switching (fixes #15408)`
  ← repository/TeamsRepositoryImpl.kt, ui/teams/members/MembersFragment.kt
- `teams: smoother members leader refreshing (fixes #15246)`
  ← ui/teams/members/MembersAdapter.kt
- `teams: smoother voices post removing (fixes #15215)`
  ← ui/voices/VoicesAdapter.kt
- `teams: smoother voices list preparing  (fixes #15214)`
  ← ui/voices/VoicesAdapter.kt
- `teams: smoother submissions repository negative flow testing (fixes #15212)`
  ← test/repository/SubmissionsRepositoryImplTest.kt
- `teams: smoother submissions repository submitter name testing (fixes #15209)`
  ← test/repository/SubmissionsRepositoryImplTest.kt
- `teams: smoother repository details flow testing (fixes #15195)`
  ← test/repository/TeamsRepositoryImplTest.kt
- `teams: smoother repository serialize activities testing (fixes #15194)`
  ← test/repository/TeamsRepositoryImplTest.kt
- `teams: smoother repository user injecting (fixes #15193)`
  ← repository/TeamsRepositoryImpl.kt, test/repository/TeamsRepositoryBenchmarkTest.kt, test/repository/TeamsRepositoryBulkInsertTransactionTest.kt, test/repository/TeamsRepositoryImplTest.kt
- `teams: smoother members placeholder handling (fixes #15164)`
  ← ui/teams/members/MembersFragment.kt
- `teams: smoother voices repository dao querying (fixes #15154)`
  ← data/room/dao/NewsDao.kt, repository/VoicesRepositoryImpl.kt, test/data/room/dao/NewsDaoTest.kt, test/repository/VoicesRepositoryImplTest.kt
- `teams: smoother voices posting policy testing (fixes #15150)`
  ← test/repository/VoicePostingPolicyTest.kt
- `teams: smoother surveys repository reminders flow testing (fixes #15148)`
  ← test/repository/SurveysRepositoryImplTest.kt
- `teams: smoother voices reply helper scoping (fixes #15136)`
  ← ui/teams/voices/TeamsVoicesFragment.kt, ui/voices/ReplyActivity.kt, ui/voices/VoicesAdapterHelper.kt, ui/voices/VoicesFragment.kt
- `teams: smoother events repository user fetching (fixes #15132)`
  ← repository/EventsRepositoryImpl.kt, test/repository/EventsRepositoryImplTest.kt
- `teams: smoother voices notifications repositories chat counting (fixes #15131)`
  ← repository/NotificationsRepositoryImpl.kt, repository/VoicesRepository.kt, repository/VoicesRepositoryImpl.kt, test/repository/NotificationsRepositoryImplTest.kt
- `teams: smoother voices item spacing (fixes #15092)`
  ← ui/voices/VoicesAdapter.kt, res/layout/row_news.xml, res/values/dimens.xml
- `teams: smoother repository admin querying (fixes #15089)`
  ← data/room/dao/LegacyEntityDaos.kt, repository/TeamsRepositoryImpl.kt
- `teams: smoother voices coloring (fixes #15087)`
  ← ui/teams/TeamsAdapter.kt, ui/voices/VoicesAdapter.kt, res/values/colors.xml
- `teams: smoother task notifying (fixes #14932)`
  ← services/TaskNotificationWorker.kt
- `teams: smoother repository task list handling (fixes #14882)`
  ← data/room/dao/TeamTaskDao.kt, repository/TeamsRepositoryImpl.kt, ui/teams/tasks/TeamsTasksFragment.kt
- `teams: smoother repository leader marking (fixes #14827)`
  ← repository/TeamsRepositoryImpl.kt
- `teams: smoother repository records deleting (fixes #14943)`
  ← data/room/dao/LegacyEntityDaos.kt, repository/TeamsRepositoryImpl.kt
- `teams: smoother voices repository community distinct changing (fixes #14900)`
  ← repository/VoicesRepositoryImpl.kt
- `teams: smoother calendar dates selection binding (fixes #14798)`
  ← ui/teams/TeamCalendarFragment.kt
- `teams: smoother user repository model fetching (fixes #14809)`
  ← base/BaseTeamFragment.kt, repository/UserRepository.kt, repository/UserRepositoryImpl.kt, services/UploadManager.kt, services/UserSessionManager.kt, +1 more
- `teams: smoother voices steps resources dispatcher providing (fixes #14807)`
  ← ui/courses/CourseStepFragment.kt, ui/courses/InlineResourceAdapter.kt, ui/teams/voices/TeamsVoicesFragment.kt, ui/voices/ReplyActivity.kt, ui/voices/VoicesAdapterHelper.kt, +1 more
- `teams: smoother courses pagers calculate diffing (fixes #14805)`
  ← ui/courses/CoursesPagerAdapter.kt, ui/teams/TeamPagerAdapter.kt
- `teams: smoother view model dispatcher providing (fixes #14804)`
  ← ui/teams/TeamViewModel.kt
- `teams: smoother life progress repositories data filtering (fixes #14791)`
  ← repository/LifeRepository.kt, repository/LifeRepositoryImpl.kt, repository/ProgressRepositoryImpl.kt, repository/TeamsRepositoryImpl.kt
- `teams: smoother submissions questions detail item listing  (fixes #14651)`
  ← res/layout/fragment_submission_detail.xml, res/layout/fragment_submission_list.xml, res/layout/item_question_answer.xml, res/layout/item_submission.xml
- `teams: smoother voices filter view modelling (fixes #14789)`
  ← repository/VoicesRepositoryImpl.kt, ui/voices/VoicesViewModel.kt, test/ui/voices/VoicesViewModelTest.kt
- `teams: smoother voices label managing (fixes #14787)`
  ← services/VoicesLabelManager.kt, ui/voices/VoicesAdapter.kt
- `teams: smoother tasks list updating (fixes #14772)`
  ← ui/teams/tasks/TeamsTasksFragment.kt
- `teams: smoother submission list export view modelling (fixes #14770)`
  ← ui/submissions/SubmissionListViewModel.kt
- `teams: smoother leave view modelling (fixes #14600)`
  ← ui/teams/TeamDetailFragment.kt, ui/teams/TeamFragment.kt, ui/teams/TeamViewModel.kt
- `teams: smoother leader selection request diffing (fixes #14762)`
  ← ui/community/CommunityLeadersAdapter.kt, ui/teams/TeamsSelectionAdapter.kt, ui/teams/members/RequestsAdapter.kt, test/ui/community/CommunityLeadersAdapterTest.kt, test/ui/teams/TeamsSelectionAdapterTest.kt, +1 more
- `teams: smoother sync repository uploading (fixes #14759)`
  ← repository/TeamsRepositoryImpl.kt, repository/TeamsSyncRepository.kt, services/UploadManager.kt
- `teams: smoother repository member dating (fixes #14758)`
  ← repository/TeamsRepositoryImpl.kt
- `teams: smoother voices posting (fixes #14599)`
  ← ui/teams/voices/TeamsVoicesFragment.kt, ui/voices/VoicesFragment.kt
- `teams: smoother voices repository querying (fixes #14638)`
  ← repository/VoicesRepository.kt, repository/VoicesRepositoryImpl.kt, ui/teams/voices/TeamsVoicesViewModel.kt, ui/voices/VoicesViewModel.kt, test/repository/VoicesRepositoryImplTest.kt, +2 more
- `teams: smoother resources repositories querying (fixes #14629)`
  ← model/RealmMyCourse.kt, model/RealmMyTeam.kt, repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, repository/TeamsRepositoryImpl.kt
- `teams: smoother voices repository uploading (fixes #14627)`
  ← data/DatabaseService.kt, data/RealmMigrations.kt, repository/VoicesRepositoryImpl.kt, test/repository/VoicesRepositoryImplTest.kt
- `teams: smoother tasks view binding (fixes #14625)`
  ← ui/teams/tasks/TeamsTasksAdapter.kt, ui/teams/tasks/TeamsTasksFragment.kt
- `teams: smoother events item callback diffing (fixes #14620)`
  ← ui/events/EventsAdapter.kt, test/ui/events/EventsAdapterTest.kt
- `teams: smoother events detail time handling (fixes #14618)`
  ← ui/events/EventsDetailFragment.kt
- `teams: smoother tasks view modelling (fixes #14608)`
  ← ui/teams/tasks/TeamsTasksFragment.kt, ui/teams/tasks/TeamsTasksViewModel.kt, test/ui/teams/tasks/TeamsTasksViewModelTest.kt
- `teams: smoother detail pager adapting (fixes #14607)`
  ← ui/teams/TeamDetailFragment.kt, ui/teams/TeamPagerAdapter.kt
- `teams: smoother voices filters view modelling (fixes #14605)`
  ← ui/voices/VoicesViewModel.kt, test/ui/voices/VoicesViewModelTest.kt
- `teams: smoother detail view modelling (fixes #14593)`
  ← di/ServiceModule.kt, ui/teams/TeamDetailFragment.kt, ui/teams/TeamViewModel.kt, test/ui/teams/TeamViewModelTest.kt
- `teams: smoother voices helper dispatcher providing (fixes #14590)`
  ← ui/teams/voices/TeamsVoicesFragment.kt, ui/voices/ReplyActivity.kt, ui/voices/VoicesAdapterHelper.kt, ui/voices/VoicesFragment.kt
- `teams: smoother voices view modelling (fixes #14587)`
  ← ui/teams/voices/TeamsVoicesFragment.kt, ui/teams/voices/TeamsVoicesViewModel.kt, test/ui/teams/voices/TeamsVoicesViewModelTest.kt
- `teams: smoother repository querying (fixes #14581)`
  ← repository/TeamsRepositoryImpl.kt
- `teams: smoother events date caching (fixes #14579)`
  ← ui/events/EventsAdapter.kt, ui/teams/TeamsAdapter.kt
- `teams: less repository methods is more (fixes #14557)`
  ← repository/TeamsRepository.kt, repository/TeamsRepositoryImpl.kt
- `teams: smoother submissions ui view modelling (fixes #14408)`
  ← ui/submissions/SubmissionUiModel.kt, ui/submissions/SubmissionViewModel.kt, ui/submissions/SubmissionsAdapter.kt, ui/submissions/SubmissionsFragment.kt, test/ui/submissions/SubmissionViewModelTest.kt
- `teams: smoother base data fallback routing (fixes #14400)`
  ← base/BaseTeamFragment.kt, ui/teams/voices/TeamsVoicesFragment.kt, test/base/BaseTeamFragmentTest.kt
- `teams: smoother surveys repository adopting (fixes #14395)`
  ← repository/SurveysRepositoryImpl.kt
- `teams: smoother repository voices posting (fixes #14393)`
  ← repository/TeamsRepository.kt, ui/teams/voices/TeamsVoicesFragment.kt, test/repository/VoicePostingPolicyTest.kt
- `teams: smoother tasks assignee preloading (fixes #14383)`
  ← ui/teams/tasks/TeamsTasksAdapter.kt, ui/teams/tasks/TeamsTasksFragment.kt
- `teams: smoother calendar date formatting (fixes #14376)`
  ← ui/teams/TeamCalendarFragment.kt
- `teams: smoother survey debounced flowing (fixes #14365)`
  ← ui/surveys/SurveyFragment.kt
- `teams: smoother realm submissions repositories first copy finding (fixes #14358)`
  ← repository/RealmRepository.kt, repository/SubmissionsRepositoryImpl.kt
- `teams: less callback interfaces is more (fixes #14264)`
  ← callback/OnTeamActionsListener.kt, callback/OnTeamEditListener.kt, callback/OnUpdateCompleteListener.kt
- `teams: smoother voices view modelling (fixes #14261)`
  ← ui/voices/VoicesFragment.kt, ui/voices/VoicesViewModel.kt, res/layout/fragment_voices.xml
- `teams: smoother voices label filtering (fixes #14217)`
  ← ui/voices/VoicesFragment.kt, res/layout/fragment_voices.xml
- `teams: smoother voices view holding (fixes #14246)`
  ← ui/voices/VoicesAdapter.kt
- `teams: smoother events detail collecting (fixes #14243)`
  ← ui/events/EventsDetailFragment.kt
- `teams: smoother voices label item adapting (fixes #14229)`
  ← ui/voices/VoicesFragment.kt, ui/voices/VoicesLabelAdapter.kt, ui/voices/VoicesLabelItem.kt
- `teams: smoother meetup realm model bulk upserting (fixes #14225)`
  ← model/RealmMeetup.kt, test/model/RealmMeetupTest.kt
- `teams: smoother voices refreshing (fixes #14222)`
  ← ui/voices/VoicesAdapter.kt
- `teams: smoother notifications repositories looping (fixes #14207)`
  ← repository/NotificationsRepositoryImpl.kt, repository/TeamsRepository.kt, repository/TeamsRepositoryImpl.kt
- `teams: smoother repository member login caching (fixes #14201)`
  ← repository/TeamsRepository.kt, repository/TeamsRepositoryImpl.kt, ui/sync/LoginActivity.kt, test/repository/TeamsRepositoryImplTest.kt

## courses (67)

- `courses: smoother repository list. filtering (fixes #15411)`
  ← repository/CoursesRepositoryImpl.kt
- `courses: smoother filter tags controlling (fixes #15404)`
  ← ui/courses/CourseFilterController.kt
- `courses: smoother list progress filter view modelling (fixes #15242)`
  ← ui/courses/CoursesViewModel.kt, test/ui/courses/CoursesViewModelTest.kt
- `courses: smoother repository payloads binding (fixes #15155)`
  ← repository/CoursesRepositoryImpl.kt, ui/courses/CoursesAdapter.kt
- `courses: smoother repository list filter testing (fixes #15211)`
  ← test/repository/CoursesRepositoryImplTest.kt
- `courses: smoother repository id flow testing (fixes #15208)`
  ← test/repository/CoursesRepositoryImplTest.kt
- `courses: smoother repository detail model providing (fixes #15189)`
  ← model/CourseDetailModel.kt, repository/CoursesRepository.kt, repository/CoursesRepositoryImpl.kt, ui/courses/CourseDetailProvider.kt, test/repository/CoursesRepositoryImplTest.kt, +1 more
- `courses: smoother ratings repository user injecting (fixes #15184)`
  ← repository/RatingsRepositoryImpl.kt, test/repository/RatingsRepositoryImplTest.kt
- `courses: smoother progress row callback diffing (fixes #15152)`
  ← model/CoursesProgressRow.kt, ui/courses/CoursesProgressAdapter.kt, ui/courses/CoursesProgressFragment.kt, test/ui/courses/CoursesProgressAdapterTest.kt
- `courses: smoother submissions repository pending flow testing (fixes #15149)`
  ← test/repository/SubmissionsRepositoryImplTest.kt
- `courses: smoother progress repository find testing (fixes #15147)`
  ← test/repository/ProgressRepositoryImplTest.kt
- `courses: smoother tags repository linking (fixes #15130)`
  ← repository/CoursesRepositoryImpl.kt, repository/TagsRepository.kt, repository/TagsRepositoryImpl.kt, test/repository/CoursesRepositoryImplTest.kt
- `courses: smoother steps inline resources coroutine scoping (fixes #15126)`
  ← ui/courses/CourseStepFragment.kt, ui/courses/InlineResourceAdapter.kt
- `courses: smoother submissions repositories exam utils answering (fixes #14878)`
  ← repository/CoursesRepositoryImpl.kt, repository/SubmissionsRepositoryImpl.kt, ui/exam/ExamTakingFragment.kt, utils/ExamAnswerUtils.kt, test/repository/SubmissionsRepositoryImplTest.kt
- `courses: smoother repository realtime sync managing (fixes #15105)`
  ← repository/CoursesRepositoryImpl.kt, ui/health/MyHealthFragment.kt, test/repository/CoursesRepositoryImplTest.kt
- `courses: less next button binding is more (fixes #15080)`
  ← ui/courses/TakeCourseFragment.kt
- `courses: smoother repository payload building (fixes #15095)`
  ← repository/CoursesRepositoryImpl.kt
- `courses: smoother progress grid lazy coloring (fixes #15088)`
  ← ui/courses/ProgressGridAdapter.kt
- `courses: smoother filter controller debouncing (fixes #15084)`
  ← ui/courses/CourseFilterController.kt
- `courses: smoother inline resources caching (fixes #15083)`
  ← ui/courses/InlineResourceAdapter.kt
- `courses: smoother exams taking (fixes #14829)`
  ← repository/SubmissionsRepository.kt, repository/SubmissionsRepositoryImpl.kt, ui/exam/ExamTakingFragment.kt
- `courses: smoother joining (fixes #14879)`
  ← base/BaseRecyclerFragment.kt, ui/courses/CoursesFragment.kt, ui/resources/ResourcesFragment.kt
- `courses: smoother take view modelling (fixes #14864)`
  ← ui/courses/TakeCourseFragment.kt, ui/courses/TakeCourseViewModel.kt, test/ui/courses/TakeCourseViewModelTest.kt
- `courses: smoother survey item listing (fixes #14892)`
  ← res/layout/row_mysurvey.xml
- `courses: smoother progress status view modelling (fixes #14797)`
  ← ui/courses/CoursesAdapter.kt, ui/courses/CoursesViewModel.kt, test/ui/courses/CoursesViewModelTest.kt
- `courses: smoother steps exams border spacing (fixes #14826)`
  ← res/layout/fragment_course_detail.xml, res/layout/fragment_exam_taking.xml, res/layout/fragment_take_course.xml
- `courses: smoother inline resources dark mode handling (fixes #14792)`
  ← res/layout/item_inline_resource.xml
- `courses: smoother achievements payload handling (fixes #14785)`
  ← ui/courses/CoursesAdapter.kt, ui/user/AchievementsAdapter.kt, utils/DiffUtils.kt
- `courses: smoother repository searching (fixes #14783)`
  ← repository/CoursesRepositoryImpl.kt, test/repository/CoursesRepositoryImplTest.kt
- `courses: smoother repository certifications bulk inserting (fixes #14782)`
  ← repository/CoursesRepositoryImpl.kt
- `courses: smoother filter controller spinner listening (fixes #14775)`
  ← ui/courses/CourseFilterController.kt
- `courses: smoother submissions repository realm querying (fixes #14774)`
  ← repository/SubmissionsRepositoryImpl.kt
- `courses: smoother base progress batch deleting (fixes #14752)`
  ← base/BaseRecyclerFragment.kt
- `courses: smoother inline resources caching (fixes #14749)`
  ← ui/courses/InlineResourceAdapter.kt
- `courses: smoother repository exams querying (fixes #14746)`
  ← repository/CoursesRepositoryImpl.kt
- `courses: smoother steps linking  (fixes #14653)`
  ← ui/courses/CourseStepFragment.kt
- `courses: smoother list filtering (fixes #14602)`
  ← ui/courses/CoursesFragment.kt
- `courses: smoother detail joining (fixes #14553)`
  ← ui/courses/CourseDetailFragment.kt, ui/courses/TakeCourseFragment.kt
- `courses: smoother pager creation diffing (fixes #14635)`
  ← ui/courses/CoursesPagerAdapter.kt, ui/courses/TakeCourseFragment.kt
- `courses: smoother repository querying (fixes #14628)`
  ← repository/CoursesRepositoryImpl.kt
- `courses: smoother resources repositories pre-filtered searching (fixes #14626)`
  ← app/src/androidTest/java/org/ole/planet/myplanet/data/DatabaseServiceTest.kt, app/src/androidTest/java/org/ole/planet/myplanet/model/RealmUserTest.kt, data/DatabaseService.kt, data/RealmMigrations.kt, model/RealmMyLibrary.kt, +4 more
- `courses: smoother filter controller flowing (fixes #14623)`
  ← ui/courses/CourseFilterController.kt, ui/courses/CoursesFragment.kt
- `courses: smoother steps resources view holding (fixes #14621)`
  ← ui/courses/InlineResourceAdapter.kt
- `courses: smoother submissions repository flowing (fixes #14611)`
  ← repository/SubmissionsRepositoryImpl.kt, test/repository/SubmissionsRepositoryImplTest.kt, test/ui/submissions/SubmissionViewModelTest.kt
- `courses: smoother detail rating view modelling (fixes #14610)`
  ← base/BaseContainerFragment.kt, ui/courses/CourseDetailProvider.kt, ui/courses/CourseDetailViewModel.kt, ui/courses/RatingSummaryProvider.kt, utils/CourseRatingUtils.kt, +3 more
- `courses: smoother progress repository data fetching (fixes #14580)`
  ← repository/ProgressRepositoryImpl.kt, test/repository/ProgressRepositoryImplTest.kt
- `courses: smoother submissions search flowing (fixes #14578)`
  ← ui/submissions/SubmissionsFragment.kt
- `courses: smoother status updates view modelling (fixes #14575)`
  ← ui/courses/CoursesViewModel.kt
- `courses: smoother ratings view modelling (fixes #14572)`
  ← ui/ratings/RatingsFragment.kt, ui/ratings/RatingsViewModel.kt, test/ui/ratings/RatingsViewModelTest.kt
- `courses: less realm step exam model creation time is more (fixes #14564)`
  ← model/RealmStepExam.kt, test/model/RealmStepExamTest.kt
- `courses: less realm step exam model ids is more (fixes #14562)`
  ← model/RealmStepExam.kt, test/model/RealmStepExamTest.kt
- `courses: less repository methods is more (fixes #14555)`
  ← repository/CoursesRepository.kt, repository/CoursesRepositoryImpl.kt
- `courses: smoother cover image handling (fixes #14392)`
  ← data/DatabaseService.kt, data/RealmMigrations.kt, model/RealmMyCourse.kt, repository/CoursesRepositoryImpl.kt, services/sync/TransactionSyncManager.kt, +9 more
- `courses: smoother progress repository bulk inserting (fixes #14409)`
  ← repository/ProgressRepository.kt, repository/ProgressRepositoryImpl.kt, services/sync/TransactionSyncManager.kt, test/repository/ProgressRepositoryImplTest.kt
- `courses: smoother resources inline metadata caching (fixes #14406)`
  ← ui/courses/InlineResourceAdapter.kt
- `courses: smoother exam base progressing (fixes #14401)`
  ← base/BaseExamFragment.kt, ui/exam/ExamTakingFragment.kt, test/ui/exam/ExamTakingFragmentTest.kt
- `courses: smoother repository log batch deleting (fixes #14371)`
  ← repository/CoursesRepositoryImpl.kt
- `courses: smoother submissions repository create exam submission modelling (fixes #14370)`
  ← model/CreateExamSubmissionRequest.kt, repository/SubmissionsRepository.kt, repository/SubmissionsRepositoryImpl.kt, ui/exam/ExamTakingFragment.kt, test/repository/SubmissionsRepositoryImplTest.kt
- `courses: smoother repository exam survey inserting (fixes #14369)`
  ← repository/CoursesRepositoryImpl.kt
- `courses: smoother repository bulk inserting (fixes #14354)`
  ← repository/CoursesRepositoryImpl.kt, services/sync/TransactionSyncManager.kt
- `courses: smoother submissions repository async serializing (fixes #14352)`
  ← repository/SubmissionsRepositoryImpl.kt
- `courses: smoother take button navigating (fixes #13957)`
  ← ui/courses/CoursesAdapter.kt, ui/courses/TakeCourseFragment.kt, ui/dashboard/DashboardViewModel.kt
- `courses: smoother submissions latest collecting (fixes #14242)`
  ← ui/submissions/SubmissionsAdapter.kt, ui/submissions/SubmissionsFragment.kt
- `courses: smoother list latest collecting (fixes #14237)`
  ← ui/courses/CoursesFragment.kt
- `courses: smoother exams question realm model bulk inserting (fixes #14226)`
  ← model/RealmExamQuestion.kt, test/model/RealmExamQuestionTest.kt
- `courses: smoother submissions repository flowing (fixes #14223)`
  ← repository/SubmissionsRepository.kt, repository/SubmissionsRepositoryImpl.kt
- `courses: smoother ratings refresh view modelling (fixes #14206)`
  ← callback/OnDiffRefreshListener.kt, callback/OnRatingChangeListener.kt, ui/courses/CoursesAdapter.kt, ui/courses/CoursesFragment.kt, ui/courses/CoursesViewModel.kt, +1 more

## sync (55)

- `sync: smoother user repository inserting (fixes #15432)`
  ← repository/UserRepositoryImpl.kt, test/repository/UserRepositoryBulkInsertTest.kt
- `sync: smoother download service starting (fixes #15428)`
  ← services/DownloadService.kt
- `sync: smoother download file servicing (fixes #15427)`
  ← services/DownloadService.kt
- `sync: smoother upload voices managing (fixes #15423)`
  ← services/UploadManager.kt
- `sync: smoother repository user data flowing (fixes #15216)`
  ← repository/SyncRepository.kt, ui/sync/ProcessUserDataActivity.kt
- `sync: smoother url utils auth header handling (fixes #15160)`
  ← repository/UserRepositoryImpl.kt, services/sync/LoginSyncManager.kt, services/sync/TransactionSyncManager.kt, utils/UrlUtils.kt
- `sync: smoother upload bulk voices managing (fixes #15206)`
  ← base/BaseDashboardFragment.kt, data/api/ApiInterface.kt, repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, repository/SurveysRepository.kt, +8 more
- `sync: smoother upload repository bulk team managing (fixes #15203)`
  ← data/api/ApiInterface.kt, repository/UploadRepository.kt, repository/UploadRepositoryImpl.kt, services/UploadManager.kt, test/services/UploadManagerTest.kt
- `sync: smoother user repository uploading (fixes #15202)`
  ← repository/UserRepositoryImpl.kt, repository/UserSyncRepository.kt, services/UploadToShelfService.kt
- `sync: smoother process user data normalizing (fixes #15163)`
  ← ui/sync/ProcessUserDataActivity.kt
- `sync: smoother login auth utils managing (fixes #15151)`
  ← services/sync/LoginSyncManager.kt, utils/AuthUtils.kt
- `sync: smoother photo uploading (fixes #15145)`
  ← services/upload/PhotoUploader.kt
- `sync: smoother upload immediate dispatcher providing (fixes #15142)`
  ← services/UploadManager.kt, utils/DispatcherProvider.kt, test/data/DatabaseServiceTest.kt, test/repository/ConfigurationsRepositoryImplTest.kt, test/ui/courses/CourseDetailViewModelTest.kt, +8 more
- `sync: smoother user repository shelf batch uploading (fixes #15137)`
  ← repository/UserRepositoryImpl.kt, repository/UserSyncRepository.kt, services/UploadToShelfService.kt
- `sync: smoother upload repository attachments managing (fixes #15135)`
  ← repository/UploadRepository.kt, repository/UploadRepositoryImpl.kt, services/FileUploader.kt, services/UploadManager.kt, services/upload/PhotoUploader.kt, +1 more
- `sync: smoother time logger providing (fixes #15128)`
  ← utils/SyncTimeLogger.kt
- `sync: smoother retry interceptor time providing (fixes #15118)`
  ← data/api/RetryInterceptor.kt, di/NetworkModule.kt, test/data/api/RetryInterceptorTest.kt
- `sync: smoother realtime flow filtering (fixes #15098)`
  ← ui/chat/ChatViewModel.kt, ui/teams/TeamDetailFragment.kt
- `sync: smoother guest login validating (fixes #14951)`
  ← ui/sync/GuestLoginExtensions.kt
- `sync: smoother transaction manager attachment downloading (fixes #14937)`
  ← services/sync/TransactionSyncManager.kt
- `sync: smoother download queue starting (fixes #14841)`
  ← services/DownloadService.kt
- `sync: smoother network module requesting (fixes #14802)`
  ← di/NetworkModule.kt
- `sync: smoother server reachability caching (fixes #14799)`
  ← MainApplication.kt, services/ServerReachabilityWorker.kt
- `sync: smoother upload managing (fixes #14794)`
  ← di/ServiceModule.kt, services/UploadManager.kt, test/services/UploadManagerTest.kt
- `sync: smoother repository submissions uploading (fixes #14816)`
  ← repository/SyncRepository.kt, services/SubmissionsUploader.kt, ui/exam/UserInformationFragment.kt, ui/sync/ProcessUserDataActivity.kt
- `sync: smoother user ratings repositories transaction managing (fixes #14812)`
  ← di/ServiceModule.kt, repository/RatingsRepository.kt, repository/RatingsRepositoryImpl.kt, repository/UserRepositoryImpl.kt, repository/UserSyncRepository.kt, +4 more
- `sync: smoother file attachment photo uploading (fixes #14779)`
  ← services/FileUploader.kt, services/upload/PhotoUploader.kt
- `sync: smoother download repository responding (fixes #14771)`
  ← repository/DownloadRepositoryImpl.kt
- `sync: smoother configuration coordinator dispatcher providing (fixes #14596)`
  ← ui/sync/SyncActivity.kt, ui/sync/SyncConfigurationCoordinator.kt, test/ui/sync/SyncConfigurationCoordinatorTest.kt
- `sync: smoother upload repository coordinating (fixes #14636)`
  ← repository/UploadRepository.kt, repository/UploadRepositoryImpl.kt, services/upload/UploadCoordinator.kt, test/repository/UploadRepositoryImplTest.kt
- `sync: smoother time logger utils handling (fixes #14604)`
  ← utils/SyncTimeLogger.kt
- `sync: smoother min apk checking (fixes #14594)`
  ← ui/sync/SyncActivity.kt, ui/sync/SyncConfigurationCoordinator.kt, test/ui/sync/SyncConfigurationCoordinatorTest.kt
- `sync: smoother retry repository pending (fixes #14577)`
  ← repository/RetryRepositoryImpl.kt, test/repository/RetryRepositoryImplTest.kt
- `sync: smoother download repository testing (fixes #14570)`
  ← test/repository/DownloadRepositoryImplTest.kt
- `sync: smoother policy force testing (fixes #14432)`
  ← ui/sync/ForceSyncPolicy.kt, ui/sync/SyncActivity.kt, test/ui/sync/ForceSyncPolicyTest.kt, test/ui/sync/LoginViewModelTest.kt, test/ui/sync/SyncConfigurationCoordinatorTest.kt
- `sync: smoother transaction manager batch inserting (fixes #14438)`
  ← services/sync/TransactionSyncManager.kt
- `sync: smoother realtime manger flowing (fixes #14442)`
  ← callback/OnBaseRealtimeSyncListener.kt, callback/OnRealtimeSyncListener.kt, services/sync/RealtimeSyncManager.kt, ui/chat/ChatHistoryFragment.kt, ui/health/MyHealthFragment.kt, +3 more
- `sync: smoother adaptive batch processing (fixes #14424)`
  ← services/sync/AdaptiveBatchProcessor.kt, services/sync/SyncManager.kt, test/services/sync/AdaptiveBatchProcessorTest.kt
- `sync: smoother post requests retrying (fixes #14415)`
  ← data/api/RetryInterceptor.kt, test/data/api/RetryInterceptorTest.kt
- `sync: less gradle realm configuration is more (fixes #14433)`
  ← app/build.gradle
- `sync: smoother user repository bulk inserting (fixes #14404)`
  ← repository/UserRepositoryImpl.kt, repository/UserSyncRepository.kt, services/sync/TransactionSyncManager.kt, test/repository/UserRepositoryBulkInsertTest.kt
- `sync: smoother feedback list real time mixing (fixes #14366)`
  ← ui/feedback/FeedbackListFragment.kt
- `sync: smoother realtime manager notification listening (fixes #14364)`
  ← services/sync/RealtimeSyncManager.kt
- `sync: smoother realm repository transacting (fixes #14359)`
  ← di/ServiceModule.kt, services/sync/TransactionSyncManager.kt, test/services/sync/TransactionSyncManagerTest.kt
- `sync: smoother retry interceptor cancelling (fixes #14282)`
  ← data/api/RetryInterceptor.kt, test/data/api/RetryInterceptorTest.kt
- `sync: less experimental manager is more (fixes #14275)`
  ← di/ServiceModule.kt, services/SharedPrefManager.kt, services/sync/AdaptiveBatchProcessor.kt, services/sync/ImprovedSyncManager.kt, services/sync/StandardSyncStrategy.kt, +13 more
- `sync: smoother retry intercepting (fixes #14265)`
  ← data/api/RetryInterceptor.kt, test/data/api/RetryInterceptorTest.kt
- `sync: less beta fast option is more (fixes #14270)`
  ← model/SyncState.kt, services/SharedPrefManager.kt, services/sync/ImprovedSyncManager.kt, services/sync/SyncManager.kt, services/sync/SyncStrategy.kt, +29 more
- `sync: smoother url utils initializing (fixes #14259)`
  ← MainApplication.kt, utils/UrlUtils.kt, test/utils/UrlUtilsTest.kt
- `sync: less api client singleton is more (fixes #14238)`
  ← MainApplication.kt, data/api/ApiClient.kt, di/NetworkDependenciesEntryPoint.kt, di/NetworkModule.kt
- `sync: smoother network timeout handling (fixes #14228)`
  ← di/NetworkModule.kt
- `sync: smoother upload repository bulk querying (fixes #14212)`
  ← repository/UploadRepositoryImpl.kt, test/repository/UploadRepositoryImplTest.kt
- `sync: smoother upload managing (fixes #14211)`
  ← services/UploadManager.kt
- `sync: smoother personals repository documents uploading (fixes #14210)`
  ← repository/PersonalsRepository.kt, repository/PersonalsRepositoryImpl.kt, services/UploadManager.kt, test/repository/PersonalsRepositoryImplTest.kt, test/services/UploadManagerTest.kt
- `sync: smoother user repository achievements bulk inserting (fixes #14202)`
  ← repository/UserRepositoryImpl.kt

## resources (51)

- `resources: smoother download utils handling (fixes #15429)`
  ← services/DownloadService.kt, services/DownloadWorker.kt, utils/DownloadUtils.kt
- `resources: smoother viewer audio path resolving (fixes #15413)`
  ← ui/viewer/ResourceViewerFragment.kt
- `resources: smoother repository shelf leaving (fixes #15255)`
  ← data/room/AppDatabase.kt, data/room/dao/RemovedLogDao.kt, model/MyLibrary.kt, repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, +2 more
- `resources: smoother bottom navigation adding (fixes #15221)`
  ← res/layout/fragment_add_resource.xml
- `resources: smoother base user injecting (fixes #15197)`
  ← base/BaseContainerFragment.kt, base/BaseDashboardFragment.kt, base/BaseRecyclerFragment.kt, base/BaseResourceFragment.kt, ui/courses/CourseStepFragment.kt, +8 more
- `resources: smoother repository recent downloads testing (fixes #15190)`
  ← test/repository/ResourcesRepositoryImplTest.kt
- `resources: smoother repository list view modelling (fixes #15188)`
  ← repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, ui/resources/ResourcesViewModel.kt, test/ui/resources/ResourcesViewModelTest.kt
- `resources: smoother repository search dao title filtering (fixes #15186)`
  ← data/room/dao/MyLibraryDao.kt, repository/ResourcesRepositoryImpl.kt, test/repository/ResourcesRepositoryImplTest.kt
- `resources: smoother repository open tracking (fixes #15185)`
  ← base/BaseContainerFragment.kt, repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, utils/ResourceOpener.kt, test/repository/ResourcesRepositoryBenchmarkTest.kt, +2 more
- `resources: smoother repository user injecting (fixes #15183)`
  ← repository/ResourcesRepositoryImpl.kt, test/repository/ResourcesRepositoryBenchmarkTest.kt, test/repository/ResourcesRepositoryImplTest.kt, test/repository/ResourcesRepositoryLibrarySyncTest.kt
- `resources: smoother video picture in picture viewing (fixes #14902)`
  ← app/src/main/AndroidManifest.xml, ui/viewer/ResourceViewerActivity.kt, ui/viewer/ResourceViewerFragment.kt
- `resources: smoother view modelling (fixes #15100)`
  ← ui/resources/ResourcesViewModel.kt
- `resources: smoother chip cloud configuring (fixes #15079)`
  ← ui/resources/ResourcesAdapter.kt
- `resources: less web view orientation lock is more (fixes #14945)`
  ← ui/viewer/WebViewActivity.kt
- `resources: smoother web view pathing (fixes #14944)`
  ← ui/viewer/WebViewActivity.kt
- `resources: smoother view extensions utils hint spinning (fixes #14940)`
  ← ui/resources/AddResourceActivity.kt, utils/ViewExtensions.kt
- `resources: smoother repository batch inserting (fixes #14938)`
  ← repository/ResourcesRepositoryImpl.kt, test/repository/ResourcesRepositoryBenchmarkTest.kt
- `resources: smoother creation handing (fixes #14603)`
  ← ui/resources/AddResourceActivity.kt, ui/resources/AddResourceFragment.kt, ui/resources/ResourcesFragment.kt
- `resources: smoother video viewing (fixes #14840)`
  ← ui/viewer/ResourceViewerFragment.kt
- `resources: smoother searching (fixes #14818)`
  ← utils/ViewExtensions.kt
- `resources: less webview progress bar increment is more (fixes #14830)`
  ← ui/viewer/WebViewActivity.kt
- `resources: smoother repository creation editing (fixes #14648)`
  ← repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, ui/resources/AddResourceActivity.kt, ui/resources/ResourcesAdapter.kt, ui/resources/ResourcesFragment.kt, +7 more
- `resources: smoother repository local requesting (fixes #14788)`
  ← repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, ui/resources/AddResourceActivity.kt
- `resources: smoother voices payload adapting (fixes #14786)`
  ← ui/resources/ResourcesAdapter.kt, ui/voices/VoicesAdapter.kt
- `resources: smoother repository search normalizing (fixes #14745)`
  ← repository/ResourcesRepositoryImpl.kt, test/repository/ResourcesRepositoryImplTest.kt
- `resources: smoother webview navigating (fixes #14656)`
  ← ui/viewer/WebViewActivity.kt
- `resources: smoother courses repositories search utils handing (fixes #14631)`
  ← repository/CoursesRepositoryImpl.kt, repository/ResourcesRepositoryImpl.kt, ui/chat/ChatViewModel.kt, ui/resources/ResourcesFragment.kt, ui/surveys/SurveysViewModel.kt, +4 more
- `resources: smoother view modelling (fixes #14667)`
  ← ui/resources/ResourcesViewModel.kt, test/ui/resources/ResourcesViewModelTest.kt
- `resources: smoother multi line text spinning (fixes #14646)`
  ← ui/resources/AddResourceActivity.kt, res/layout/activity_add_resource.xml
- `resources: smoother viewer initializing (fixes #14616)`
  ← app/src/main/AndroidManifest.xml, ui/viewer/ResourceViewerActivity.kt
- `resources: smoother repository realm results filtering (fixes #14606)`
  ← repository/ResourcesRepositoryImpl.kt, test/repository/ResourcesRepositoryImplTest.kt
- `resources: smoother coroutines job refreshing (fixes #14589)`
  ← ui/resources/ResourcesFragment.kt
- `resources: smoother regex text normalizing (fixes #14586)`
  ← ui/resources/ResourcesFragment.kt
- `resources: less base survey methods is more (fixes #14569)`
  ← base/BaseResourceFragment.kt
- `resources: less repository methods is more (fixes #14559)`
  ← repository/ResourcesRepository.kt, repository/ResourcesRepositoryImpl.kt, test/repository/ResourcesRepositoryImplTest.kt
- `resources: less viewers ratings layouts is more (fixes #14444)`
  ← res/drawable/bg_rating_button_1.xml, res/drawable/bg_rating_button_2.xml, res/drawable/bg_rating_button_3.xml, res/drawable/bg_rating_button_4.xml, res/drawable/bg_rating_button_5.xml, +12 more
- `resources: smoother tags repository relation collecting (fixes #14402)`
  ← repository/TagsRepository.kt, repository/TagsRepositoryImpl.kt, ui/resources/CollectionsFragment.kt, test/repository/TagsRepositoryImplTest.kt
- `resources: smoother realm model inserting (fixes #14390)`
  ← model/RealmMyLibrary.kt, repository/CoursesRepositoryImpl.kt, repository/ResourcesRepositoryImpl.kt
- `resources: smoother tags repository bulk inserting (fixes #14389)`
  ← repository/TagsRepositoryImpl.kt
- `resources: smoother download utils injecting (fixes #14367)`
  ← di/RepositoryDependenciesEntryPoint.kt, di/ResourcesRepositoryEntryPoint.kt, utils/DownloadUtils.kt
- `resources: smoother collections debounce text flowing (fixes #14382)`
  ← ui/resources/CollectionsFragment.kt, ui/resources/ResourcesFragment.kt
- `resources: smoother repository realm handling (fixes #14380)`
  ← repository/ResourcesRepositoryImpl.kt, test/repository/ResourcesRepositoryImplTest.kt
- `resources: smoother selecting (fixes #14377)`
  ← ui/resources/ResourcesAdapter.kt
- `resources: smoother list filtering (fixes #14375)`
  ← ui/resources/ResourcesFragment.kt
- `resources: smoother viewer video playing (fixes #14374)`
  ← ui/viewer/ResourceViewerFragment.kt
- `resources: smoother rating (fixes #14256)`
  ← ui/resources/ResourceDetailFragment.kt, ui/resources/ResourcesFragment.kt
- `resources: smoother selection adapting (fixes #14231)`
  ← ui/resources/ResourcesAdapter.kt
- `resources: smoother realm model bulk inserting (fixes #14224)`
  ← model/RealmMyLibrary.kt
- `resources: smoother repository log removing (fixes #14219)`
  ← repository/ResourcesRepositoryImpl.kt
- `resources: smoother tag repository list inserting (fixes #14218)`
  ← repository/TagsRepository.kt, repository/TagsRepositoryImpl.kt
- `resources: smoother inline coroutine scoping (fixes #14205)`
  ← ui/courses/CourseStepFragment.kt, ui/courses/InlineResourceAdapter.kt

## login (26)

- `login: less auth utils username validation is more (fixes #15414)`
  ← ui/sync/GuestLoginExtensions.kt, utils/AuthUtils.kt
- `login: smoother creation username validating (fixes #15406)`
  ← ui/user/BecomeMemberActivity.kt
- `login: smoother landscape scrolling (fixes #15235)`
  ← res/layout-large-land/activity_login.xml, res/layout-night/activity_login.xml, res/layout-normal-land/activity_login.xml, res/layout-xlarge-land/activity_login.xml, res/layout/activity_login.xml
- `login: smoother settings language dialog cancelling (fixes #14948)`
  ← ui/settings/SettingsActivity.kt, ui/sync/LoginActivity.kt, res/layout/checked_list_item.xml
- `login: smoother activities repository courses uploading (fixes #15138)`
  ← repository/ActivitiesRepository.kt, repository/ActivitiesRepositoryImpl.kt, services/upload/UploadConfigs.kt, test/services/upload/UploadConfigsTest.kt
- `login: smoother settings resetting (fixes #14957)`
  ← ui/settings/SettingsActivity.kt
- `login: smoother user repository case insensitive finding (fixes #15082)`
  ← data/room/dao/LegacyEntityDaos.kt, repository/UserRepositoryImpl.kt
- `login: smoother registering (fixes #14952)`
  ← res/values/strings.xml
- `login: smoother storage category selection coloring (fixes #14838)`
  ← res/layout/fragment_storage_category_detail.xml
- `login: smoother download dialog all selecting (fixes #14860)`
  ← res/layout/fragment_storage_category_detail.xml, res/layout/item_downloaded_resource.xml
- `login: smoother view model dispatcher providing (fixes #14751)`
  ← ui/sync/LoginViewModel.kt
- `login: smoother user avatar dimension caching (fixes #14750)`
  ← ui/health/HealthUsersAdapter.kt, ui/teams/members/MembersAdapter.kt, ui/user/UserArrayAdapter.kt, ui/user/UsersAdapter.kt
- `login: smoother onboarding (fixes #14615)`
  ← ui/onboarding/OnboardingActivity.kt
- `login: smoother guest extensions validating (fixes #14614)`
  ← ui/sync/GuestLoginExtensions.kt, ui/sync/LoginActivity.kt
- `login: smoother shared preferences credentials managing (fixes #14612)`
  ← services/SharedPrefManager.kt, ui/sync/LoginActivity.kt, ui/user/BecomeMemberActivity.kt, test/services/SharedPrefManagerTest.kt
- `login: smoother user data processing (fixes #14584)`
  ← ui/sync/ProcessUserDataActivity.kt
- `login: smoother network utils caching (fixes #14582)`
  ← utils/NetworkUtils.kt
- `login: smoother user repository saving (fixes #14574)`
  ← repository/UserRepository.kt, repository/UserRepositoryImpl.kt, ui/sync/LoginViewModel.kt, test/repository/UserRepositoryImplTest.kt, test/ui/sync/LoginViewModelTest.kt
- `login: smoother user profile view modelling (fixes #14573)`
  ← ui/user/UserProfileFragment.kt, ui/user/UserProfileViewModel.kt, test/ui/user/UserProfileViewModelTest.kt
- `login: less shared preferences url port is more (fixes #14563)`
  ← services/SharedPrefManager.kt, ui/sync/ProcessUserDataActivity.kt, test/services/SharedPrefManagerTest.kt
- `login: smoother user repository bulk insert testing (fixes #14435)`
  ← test/repository/UserRepositoryBulkInsertTest.kt
- `login: smoother screen layout spacing (fixes #14281)`
  ← res/layout-night/activity_login.xml, res/layout/activity_login.xml
- `login: smoother activities repository bulk inserting (fixes #14385)`
  ← repository/ActivitiesRepository.kt, repository/ActivitiesRepositoryImpl.kt, services/sync/TransactionSyncManager.kt
- `login: smoother settings flow collecting (fixes #14394)`
  ← ui/settings/SettingsActivity.kt
- `login: smoother teams loading (fixes #14255)`
  ← ui/sync/LoginActivity.kt
- `login: smoother activities flow collecting (fixes #14386)`
  ← ui/dashboard/ActivitiesFragment.kt

## life (25)

- `life: smoother user repository achievement view modelling (fixes #15433)`
  ← repository/UserRepository.kt, repository/UserRepositoryImpl.kt, ui/user/AchievementFragment.kt, ui/user/AchievementViewModel.kt, test/repository/UserRepositoryBulkInsertTest.kt, +2 more
- `life: smoother achievement linking (fixes #15425)`
  ← model/Achievement.kt
- `life: smoother repository shelf visibility updating (fixes #15220)`
  ← base/BaseDashboardFragment.kt, data/room/dao/MyLifeDao.kt, repository/LifeRepositoryImpl.kt, ui/life/LifeAdapter.kt, ui/life/LifeFragment.kt
- `life: smoother achievement edit button aligning (fixes #15219)`
  ← res/layout/fragment_achievement.xml
- `life: smoother health repository user injecting (fixes #15200)`
  ← repository/HealthRepository.kt, repository/HealthRepositoryImpl.kt, repository/UserRepositoryImpl.kt
- `life: smoother health users item callback diffing (fixes #15140)`
  ← ui/health/HealthUsersAdapter.kt
- `life: smoother health examination item callback diffing (fixes #15139)`
  ← ui/health/HealthExaminationAdapter.kt
- `life: smoother list sorting (fixes #14953)`
  ← callback/OnItemMoveListener.kt, model/MyLife.kt, ui/dashboard/DashboardPluginFragment.kt, ui/life/LifeAdapter.kt, ui/life/LifeFragment.kt, +1 more
- `life: smoother health repository examination marking (fixes #15085)`
  ← data/room/dao/HealthExaminationDao.kt, repository/HealthRepositoryImpl.kt, test/repository/HealthRepositoryImplTest.kt
- `life: smoother health profile image loading (fixes #14617)`
  ← ui/health/MyHealthFragment.kt
- `life: smoother health birthdate handling (fixes #14832)`
  ← ui/health/MyHealthFragment.kt, res/layout/fragment_vital_sign.xml
- `life: smoother health repository examining (fixes #14806)`
  ← repository/HealthRepository.kt, repository/HealthRepositoryImpl.kt, ui/health/HealthExaminationActivity.kt, test/repository/HealthRepositoryImplTest.kt
- `life: smoother repository list order caching (fixes #14790)`
  ← repository/LifeRepositoryImpl.kt
- `life: smoother health users adapting (fixes #14622)`
  ← ui/health/MyHealthFragment.kt
- `life: smoother repository items caching (fixes #14613)`
  ← base/BaseDashboardFragment.kt, repository/LifeRepository.kt, repository/LifeRepositoryImpl.kt, services/SharedPrefManager.kt, test/repository/LifeRepositoryImplTest.kt, +2 more
- `life: smoother achievement view modelling (fixes #14595)`
  ← ui/user/AchievementFragment.kt, ui/user/AchievementViewModel.kt
- `life: smoother dictionary handling (fixes #14462)`
  ← di/RealmDispatcherProvider.kt, repository/SubmissionsRepositoryExporter.kt, ui/dictionary/DictionaryActivity.kt, ui/exam/ExamTakingFragment.kt, ui/sync/SyncActivity.kt
- `life: smoother personals state handling (fixes #13757)`
  ← ui/personals/PersonalsFragment.kt
- `life: smoother health examination view modelling (fixes #14412)`
  ← app/src/main/AndroidManifest.xml, ui/health/AddExaminationViewModel.kt, ui/health/HealthExaminationActivity.kt, ui/health/HealthExaminationAdapter.kt, ui/health/HealthExaminationViewModel.kt, +3 more
- `life: smoother achievements references adapting (fixes #14384)`
  ← ui/references/ReferencesAdapter.kt, ui/references/ReferencesFragment.kt, ui/user/AchievementFragment.kt, ui/user/AchievementsAdapter.kt, test/ui/user/AchievementsAdapterTest.kt
- `life: smoother health creation flow collecting (fixes #14388)`
  ← ui/health/AddHealthActivity.kt
- `life: smoother health users list adapting (fixes #14381)`
  ← ui/health/HealthUsersAdapter.kt
- `life: smoother health examination creation collecting (fixes #14372)`
  ← ui/health/AddExaminationActivity.kt
- `life: smoother health view modelling (fixes #14249)`
  ← ui/health/AddHealthActivity.kt, ui/health/HealthViewModel.kt
- `life: smoother adapter view holding (fixes #14221)`
  ← ui/life/LifeAdapter.kt

## dashboard (23)

- `dashboard: smoother health user handling (fixes #15426)`
  ← base/BaseDashboardFragment.kt
- `dashboard: smoother coroutine view modelling (fixes #15405)`
  ← ui/dashboard/DashboardViewModel.kt
- `dashboard: smoother base cards placeholding (fixes #14950)`
  ← base/BaseDashboardFragment.kt, res/values-ar/strings.xml, res/values-es/strings.xml, res/values-fr/strings.xml, res/values-ne/strings.xml, +2 more
- `dashboard: smoother profile banner handling (fixes #15401)`
  ← res/layout-large-land/card_profile_bell.xml, res/layout-normal-land/card_profile_bell.xml, res/layout-xlarge-land/card_profile_bell.xml, res/layout/card_profile_bell.xml
- `dashboard: smoother life items spacing (fixes #15109)`
  ← res/layout/fragment_feedback_list.xml, res/layout/fragment_finance.xml, res/layout/fragment_home_bell.xml, res/layout/fragment_my_course.xml, res/layout/fragment_my_library.xml, +6 more
- `dashboard: smoother landscape navigating (fixes #14949)`
  ← ui/dashboard/DashboardActivity.kt
- `dashboard: smoother notification refresh view modelling (fixes #15162)`
  ← ui/dashboard/DashboardViewModel.kt, ui/sync/SyncActivity.kt
- `dashboard: smoother bell reminding (fixes #15146)`
  ← ui/dashboard/BellDashboardFragment.kt
- `dashboard: smoother base transaction manager syncing (fixes #15133)`
  ← base/BaseDashboardFragment.kt, di/ServiceModule.kt, services/sync/TransactionSyncManager.kt, test/services/sync/TransactionSyncManagerCheckpointTest.kt, test/services/sync/TransactionSyncManagerTest.kt
- `dashboard: smoother dispatcher usage view modelling (fixes #15097)`
  ← ui/dashboard/DashboardViewModel.kt
- `dashboard: smoother guest dark mode visiting (fixes #14954)`
  ← res/layout/activity_dashboard.xml
- `dashboard: smoother guest handling (fixes #14956)`
  ← ui/dashboard/DashboardElementActivity.kt
- `dashboard: smoother base loading (fixes #15091)`
  ← base/BaseDashboardFragment.kt
- `dashboard: smoother guest login warning (fixes #14955)`
  ← ui/dashboard/DashboardActivity.kt
- `dashboard: less inactive layout comment is more (fixes #14928)`
  ← res/layout/fragment_in_active_dashboard.xml
- `dashboard: smoother window edge handling (fixes #14825)`
  ← ui/dashboard/DashboardActivity.kt
- `dashboard: smoother user repositroy profile view modelling (fixes #14808)`
  ← model/DashboardProfile.kt, repository/UserRepository.kt, repository/UserRepositoryImpl.kt, ui/dashboard/DashboardViewModel.kt, test/repository/UserRepositoryImplTest.kt, +1 more
- `dashboard: smoother base plugin item handling (fixes #14781)`
  ← base/BaseDashboardFragment.kt, ui/dashboard/DashboardItem.kt, ui/dashboard/DashboardPluginFragment.kt
- `dashboard: smoother challenge view modelling (fixes #14576)`
  ← ui/dashboard/DashboardViewModel.kt
- `dashboard: less base download dictionary is more (fixes #14567)`
  ← base/BaseDashboardFragment.kt
- `dashboard: less action listener callback is more (fixes #14430)`
  ← base/BaseDashboardFragment.kt, callback/OnDashboardActionListener.kt
- `dashboard: smoother ui state flow collecting (fixes #14396)`
  ← ui/dashboard/DashboardActivity.kt
- `dashboard: smoother bell flow collecting (fixes #14363)`
  ← ui/dashboard/BellDashboardFragment.kt, utils/FlowExtensions.kt

## chat (20)

- `chat: smoother history voices view modelling (fixes #15422)`
  ← ui/chat/ChatHistoryFragment.kt, ui/chat/ChatViewModel.kt, test/ui/chat/ChatViewModelTest.kt
- `chat: smoother search filter view modeling (fixes #15409)`
  ← ui/chat/ChatViewModel.kt
- `chat: smoother history view binding (fixes #15403)`
  ← ui/chat/ChatHistoryAdapter.kt
- `chat: smoother repository history data view modelling (fixes #15129)`
  ← repository/ChatRepository.kt, repository/ChatRepositoryImpl.kt, ui/chat/ChatHistoryAdapter.kt, ui/chat/ChatHistoryFragment.kt, ui/chat/ChatHistoryScreenData.kt, +5 more
- `chat: smoother detail ai providing (fixes #15144)`
  ← ui/chat/ChatDetailFragment.kt
- `chat: smoother view modelling (fixes #15127)`
  ← ui/chat/ChatViewModel.kt
- `chat: smoother message model callback diffing (fixes #15102)`
  ← model/ChatMessage.kt, ui/chat/ChatAdapter.kt
- `chat: smoother share target coloring (fixes #15101)`
  ← ui/chat/ChatShareTargetAdapter.kt
- `chat: smoother history teams sharing (fixes #14854)`
  ← ui/chat/ChatHistoryAdapter.kt, res/values-ar/strings.xml, res/values-es/strings.xml, res/values-fr/strings.xml, res/values-ne/strings.xml, +2 more
- `chat: smoother primary url checking (fixes #14866)`
  ← MainApplication.kt, ui/chat/ChatDetailFragment.kt, test/MainApplicationTest.kt
- `chat: smoother history realtime sync view modelling (fixes #14778)`
  ← ui/chat/ChatHistoryFragment.kt, ui/chat/ChatViewModel.kt, test/ui/chat/ChatViewModelTest.kt
- `chat: smoother share target item callback diffing (fixes #14748)`
  ← ui/chat/ChatShareTargetAdapter.kt
- `chat: smoother history color caching (fixes #14747)`
  ← ui/chat/ChatHistoryAdapter.kt, ui/health/HealthExaminationAdapter.kt
- `chat: smoother history share target item adapting (fixes #14609)`
  ← ui/chat/ChatHistoryAdapter.kt, ui/chat/ChatShareTargetAdapter.kt, ui/chat/ChatShareTargetItem.kt, res/layout/chat_share_dialog.xml, res/layout/expandable_list_group.xml
- `chat: smoother repository experimental coroutines api testing (fixes #14571)`
  ← app/src/androidTest/java/org/ole/planet/myplanet/model/RealmUserTest.kt, test/model/RealmUserTest.kt, test/repository/ChatRepositoryImplTest.kt, test/repository/ChatRepositoryTest.kt
- `chat: smoother repository bulk inserting (fixes #14407)`
  ← repository/ChatRepository.kt, repository/ChatRepositoryImpl.kt, services/sync/TransactionSyncManager.kt, test/repository/ChatRepositoryImplTest.kt, test/repository/ChatRepositoryTest.kt
- `chat: smoother history search input shared flowing (fixes #14379)`
  ← ui/chat/ChatHistoryFragment.kt
- `chat: smoother history latest collecting (fixes #14241)`
  ← ui/chat/ChatHistoryFragment.kt
- `chat: smoother history view holding (fixes #14234)`
  ← ui/chat/ChatHistoryAdapter.kt
- `chat: smoother target id sharing (fixes #14220)`
  ← ui/chat/ChatShareTargetAdapter.kt

## actions (6)

- `actions: smoother workflow automerge scripting (fixes #15400)`
  ← .github/scripts/automerge.sh
- `actions: smoother workflow test handling (fixes #15398)`
  ← .github/scripts/test_timing_summary.py, .github/workflows/test.yml, res/layout/fragment_edit_achievement.xml, gradle.properties, gradle/libs.versions.toml
- `actions: smoother workflow automerge looping (fixes #15397)`
  ← .github/scripts/automerge.sh, .github/scripts/coauthors.sh, .github/scripts/version.sh, .github/workflows/automerge.yml
- `actions: bump `gradle/actions` to 6.2.0 (fixes #15394)`
  ← .github/scripts/automerge.sh, .github/workflows/automerge.yml, .github/workflows/build.yml, .github/workflows/release.yml, .github/workflows/test.yml
- `actions: smoother workflow automerging (fixes #15393)`
  ← .github/scripts/automerge.sh, .github/scripts/coauthors.sh, .github/scripts/version.sh, .github/workflows/automerge.yml
- `actions: smoother gradle tooling parallel enabling (fixes #14884)`
  ← gradle.properties

## enterprises (5)

- `enterprises: smoother reports state change notifying (fixes #15249)`
  ← ui/enterprises/EnterprisesReportsAdapter.kt
- `enterprises: smoother finances initializing (fixes #15090)`
  ← ui/enterprises/EnterprisesFinancesFragment.kt
- `enterprises: smoother finances date formatting (fixes #14360)`
  ← ui/enterprises/EnterprisesFinancesFragment.kt
- `enterprises: smoother finances images view modelling (fixes #14215)`
  ← data/DatabaseService.kt, data/RealmMigrations.kt, model/RealmMyTeam.kt, model/Transaction.kt, repository/TeamsRepository.kt, +20 more
- `enterprises: smoother reporting (fixes #14200)`
  ← ui/enterprises/EnterprisesReportsFragment.kt

## community (3)

- `community: smoother leaders adapting (fixes #14583)`
  ← ui/community/CommunityLeadersAdapter.kt
- `community: smoother voices label filtering (fixes #14420)`
  ← ui/voices/VoicesFragment.kt, ui/voices/VoicesLabelAdapter.kt, ui/voices/VoicesLabelItem.kt, res/layout/fragment_voices.xml
- `community: smoother repository meetups bulk inserting (fixes #14353)`
  ← repository/CommunityRepository.kt, repository/CommunityRepositoryImpl.kt, services/sync/TransactionSyncManager.kt

## feedback (1)

- `feedback: smoother repository dao replying (fixes #15243)`
  ← data/room/dao/FeedbackDao.kt, repository/FeedbackRepositoryImpl.kt, test/repository/FeedbackRepositoryImplTest.kt

## Counterexamples — titles that broke the pattern

Kept as warnings, not precedents:

- `filter window fills screen in landscape mode (fixes #15261)`
  ← ui/courses/CoursesFragment.kt, ui/resources/ResourcesFilterFragment.kt, ui/resources/ResourcesFragment.kt, res/layout-land/fragment_my_course.xml, res/layout-land/fragment_my_library.xml, +3 more
- `🧪 [Testing Improvement] Add test for getTasksFlow testing (fixes #15196)`
  ← test/repository/TeamsRepositoryImplTest.kt
- `courses: ratings repository summary providing (fixes #15187)`
  ← repository/RatingsRepository.kt, repository/RatingsRepositoryImpl.kt, ui/courses/CourseDetailViewModel.kt, ui/courses/RatingSummaryProvider.kt, test/repository/RatingsRepositoryImplTest.kt, +1 more
- `teams: smoother survey submissions guest handling {fixes #14889)`
  ← ui/submissions/SubmissionsAdapter.kt
- `all: smoother server config utils handling (fixes 14801)`
  ← ui/dashboard/DashboardActivity.kt, ui/sync/ServerDialogExtensions.kt, ui/viewer/WebViewActivity.kt, utils/ServerConfigUtils.kt
- `teams: smoother base member list requesting`
  ← base/BaseMemberFragment.kt, ui/teams/members/RequestsFragment.kt
- `chat: smoother history utils shared view testing (#14755)`
  ← test/utils/ChatHistoryUtilsTest.kt
- `Refactor upload data contracts to repository layer`
  ← repository/UploadRepository.kt, repository/UploadRepositoryImpl.kt, services/upload/UploadCoordinator.kt, test/repository/UploadRepositoryImplTest.kt
- `login: smother syncing (fixes 14266)`
  ← ui/sync/LoginActivity.kt
- `login: smoother view modelling (fixes  #14244)`
  ← ui/sync/LoginActivity.kt, ui/sync/LoginViewModel.kt
- ` sync: smoother process continuing (fixes #14213)`
  ← ui/sync/SyncActivity.kt
- `life: smoother health examination realm model bulk inserting (fixes 14227)`
  ← model/RealmHealthExamination.kt, repository/HealthRepositoryImpl.kt, test/repository/HealthRepositoryImplTest.kt

