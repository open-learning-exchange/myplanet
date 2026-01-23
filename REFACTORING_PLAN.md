# myPlanet Refactoring Plan - 15 Granular Tasks

This document outlines 15 focused refactoring tasks for improving code organization, naming consistency, and structural clarity in the myPlanet Android codebase. All tasks are cosmetic/structural improvements that can be executed in parallel without merge conflicts.

---

### Repository Naming: Standardize TeamsRepository to Plural Form

The TeamsRepository uses singular "Teams" while other repositories use more appropriate plural forms like EventsRepository, VoicesRepository, and ResourcesRepository. Renaming to TeamRepository (singular) would align with the domain entity naming pattern used elsewhere (UserRepository, ChatRepository).

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L1-L20"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1-L20"}

:::task-stub{title="Rename TeamsRepository to TeamRepository"}
1. Rename TeamsRepository.kt to TeamRepository.kt
2. Rename TeamsRepositoryImpl.kt to TeamRepositoryImpl.kt
3. Update class names inside both files
4. Update RepositoryModule.kt DI bindings
5. Update TeamsRepositoryEntryPoint.kt references
6. Update all injection points in UI components (TeamFragment, TeamDetailFragment, etc.)
7. Run build to verify no compilation errors
:::

---

### Repository Naming: Standardize ActivitiesRepository to Singular Form

ActivitiesRepository uses plural naming while most repositories follow singular patterns (UserRepository, FeedbackRepository, ProgressRepository). This creates inconsistency in the repository layer naming convention.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt#L1-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L1-L20"}

:::task-stub{title="Rename ActivitiesRepository to ActivityRepository"}
1. Rename ActivitiesRepository.kt to ActivityRepository.kt
2. Rename ActivitiesRepositoryImpl.kt to ActivityRepositoryImpl.kt
3. Update class names and interface implementations
4. Update RepositoryModule.kt bindings
5. Update injection points in ActivitiesFragment
6. Update any imports in dashboard components
7. Verify compilation success
:::

---

### Repository Naming: Standardize EventsRepository to Singular Form

EventsRepository follows plural naming while the codebase favors singular repository names. Converting to EventRepository aligns with CourseRepository, FeedbackRepository, and other singular forms.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=12 path=app/src/main/java/org/ole/planet/myplanet/repository/EventsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/EventsRepository.kt#L1-L12"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt#L1-L18"}

:::task-stub{title="Rename EventsRepository to EventRepository"}
1. Rename EventsRepository.kt to EventRepository.kt
2. Rename EventsRepositoryImpl.kt to EventRepositoryImpl.kt
3. Update class and interface names
4. Update RepositoryModule.kt DI configuration
5. Update EventsAdapter and EventsFragment injection
6. Update calendar integration references
7. Run compilation check
:::

---

### Repository Naming: Standardize VoicesRepository to Singular Form

VoicesRepository uses plural naming inconsistently with other repositories. Renaming to VoiceRepository matches the pattern used by UserRepository, LifeRepository, and ChatRepository.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt#L1-L10"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt#L1-L20"}

:::task-stub{title="Rename VoicesRepository to VoiceRepository"}
1. Rename VoicesRepository.kt to VoiceRepository.kt
2. Rename VoicesRepositoryImpl.kt to VoiceRepositoryImpl.kt
3. Update interface and class declarations
4. Update RepositoryModule.kt dependency injection
5. Update VoicesAdapter references in ui/voices
6. Update VoicesLabelManager integration
7. Verify successful build
:::

---

### Repository Naming: Standardize PersonalsRepository to Singular Form

PersonalsRepository deviates from the singular naming pattern. Renaming to PersonalRepository creates consistency with other repositories like UserRepository and FeedbackRepository.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=12 path=app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt#L1-L12"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt#L1-L18"}

:::task-stub{title="Rename PersonalsRepository to PersonalRepository"}
1. Rename PersonalsRepository.kt to PersonalRepository.kt
2. Rename PersonalsRepositoryImpl.kt to PersonalRepositoryImpl.kt
3. Update class names and implementations
4. Update RepositoryModule.kt bindings
5. Update PersonalsAdapter and PersonalsFragment
6. Update OnPersonalSelectedListener references
7. Compile and verify
:::

---

### Repository Naming: Standardize NotificationsRepository to Singular Form

NotificationsRepository should follow the singular pattern like other repositories. Renaming to NotificationRepository improves consistency across the repository layer.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt#L1-L10"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L1-L18"}

:::task-stub{title="Rename NotificationsRepository to NotificationRepository"}
1. Rename NotificationsRepository.kt to NotificationRepository.kt
2. Rename NotificationsRepositoryImpl.kt to NotificationRepositoryImpl.kt
3. Update class and interface names
4. Update RepositoryModule.kt DI setup
5. Update NotificationsAdapter in dashboard/notifications
6. Update BellDashboardViewModel injection
7. Run build verification
:::

---

### Repository Naming: Standardize RatingsRepository to Singular Form

RatingsRepository breaks the singular naming convention. Changing to RatingRepository maintains consistency with UserRepository, FeedbackRepository, and ProgressRepository patterns.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepository.kt#L1-L10"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt#L1-L15"}

:::task-stub{title="Rename RatingsRepository to RatingRepository"}
1. Rename RatingsRepository.kt to RatingRepository.kt
2. Rename RatingsRepositoryImpl.kt to RatingRepositoryImpl.kt
3. Update interface and implementation declarations
4. Update RepositoryModule.kt bindings
5. Update ratings UI components injections
6. Update OnRatingChangeListener callbacks
7. Verify clean build
:::

---

### Repository Naming: Standardize TagsRepository to Singular Form

TagsRepository should be renamed to TagRepository for consistency. This aligns with the dominant singular pattern (UserRepository, ChatRepository, LifeRepository).

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository/TagsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TagsRepository.kt#L1-L10"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt#L1-L18"}

:::task-stub{title="Rename TagsRepository to TagRepository"}
1. Rename TagsRepository.kt to TagRepository.kt
2. Rename TagsRepositoryImpl.kt to TagRepositoryImpl.kt
3. Update class names throughout both files
4. Update RepositoryModule.kt dependency injection
5. Update ResourcesTagsAdapter references
6. Update OnTagClickListener integration
7. Compile to ensure correctness
:::

---

### Repository Naming: Standardize ResourcesRepository to Singular Form

ResourcesRepository follows plural naming while singular is preferred. Renaming to ResourceRepository aligns with CourseRepository, UserRepository, and other singular repository names.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L1-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L1-L20"}

:::task-stub{title="Rename ResourcesRepository to ResourceRepository"}
1. Rename ResourcesRepository.kt to ResourceRepository.kt
2. Rename ResourcesRepositoryImpl.kt to ResourceRepositoryImpl.kt
3. Update interface and class declarations
4. Update RepositoryModule.kt DI mappings
5. Update ResourcesAdapter injection
6. Update TeamResourcesAdapter references
7. Update OnResourcesUpdateListener callbacks
8. Run build verification
:::

---

### Repository Naming: Standardize CoursesRepository to Singular Form

CoursesRepository should be CourseRepository to match the singular pattern used by most repositories (UserRepository, FeedbackRepository, ChatRepository). This improves naming consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt#L1-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=25 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L1-L25"}

:::task-stub{title="Rename CoursesRepository to CourseRepository"}
1. Rename CoursesRepository.kt to CourseRepository.kt
2. Rename CoursesRepositoryImpl.kt to CourseRepositoryImpl.kt
3. Update all class and interface names
4. Update RepositoryModule.kt bindings
5. Update CoursesAdapter injections
6. Update TeamCoursesAdapter references
7. Update OnCourseItemSelectedListener callbacks
8. Verify successful compilation
:::

---

### Repository Naming: Standardize SubmissionsRepository to Singular Form

SubmissionsRepository uses plural naming inconsistently. Renaming to SubmissionRepository maintains the singular pattern established by UserRepository, FeedbackRepository, and ProgressRepository.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt#L1-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L1-L20"}

:::task-stub{title="Rename SubmissionsRepository to SubmissionRepository"}
1. Rename SubmissionsRepository.kt to SubmissionRepository.kt
2. Rename SubmissionsRepositoryImpl.kt to SubmissionRepositoryImpl.kt
3. Update class and interface declarations
4. Update RepositoryModule.kt DI bindings
5. Update SubmissionsAdapter references
6. Update SubmissionsListAdapter injection
7. Update SubmissionsRepositoryExporter references
8. Run build to confirm
:::

---

### Repository Naming: Standardize SurveysRepository to Singular Form

SurveysRepository should follow singular naming like other repositories. Changing to SurveyRepository creates consistency with UserRepository, FeedbackRepository, and ChatRepository.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=12 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepository.kt#L1-L12"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L1-L18"}

:::task-stub{title="Rename SurveysRepository to SurveyRepository"}
1. Rename SurveysRepository.kt to SurveyRepository.kt
2. Rename SurveysRepositoryImpl.kt to SurveyRepositoryImpl.kt
3. Update interface and implementation names
4. Update RepositoryModule.kt bindings
5. Update SurveyAdapter injection
6. Update DashboardSurveysAdapter references
7. Update OnSurveyAdoptListener callbacks
8. Verify clean compilation
:::

---

### Package Organization: Reorganize Callback Interfaces into Feature Subdirectories

The callback package contains 33 listener interfaces in a flat structure, making navigation difficult. Organizing into feature-based subdirectories (chat/, teams/, sync/, dashboard/, library/) improves discoverability and maintainability.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/callback/OnChatHistoryItemClickListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/OnChatHistoryItemClickListener.kt#L1-L10"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/callback/OnTeamUpdateListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/OnTeamUpdateListener.kt#L1-L10"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/callback/OnSyncListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/OnSyncListener.kt#L1-L10"}

:::task-stub{title="Organize Callback Interfaces by Feature"}
1. Create subdirectories: callback/chat/, callback/teams/, callback/sync/, callback/library/, callback/dashboard/
2. Move OnChatHistoryItemClickListener, OnChatItemClickListener to callback/chat/
3. Move OnTeamActionsListener, OnTeamEditListener, OnTeamPageListener, OnTeamUpdateListener, OnMemberActionListener, OnMemberChangeListener to callback/teams/
4. Move OnSyncListener, OnBaseRealtimeSyncListener, OnRealtimeSyncListener to callback/sync/
5. Move OnLibraryItemSelectedListener, OnCourseItemSelectedListener to callback/library/
6. Move OnDashboardActionListener, OnHomeItemClickListener, OnNewsItemClickListener to callback/dashboard/
7. Update all import statements in UI components
8. Verify successful compilation
:::

---

### Service Organization: Consolidate Manager Classes into services/managers/ Subdirectory

Manager classes (ConfigurationManager, UserSessionManager, UploadManager) are scattered across services/ and UI packages. Creating a dedicated services/managers/ subdirectory improves organization and clarifies architectural layers.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=25 path=app/src/main/java/org/ole/planet/myplanet/services/ConfigurationManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/ConfigurationManager.kt#L1-L25"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt#L1-L20"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=25 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L1-L25"}

:::task-stub{title="Consolidate Manager Classes into services/managers/"}
1. Create services/managers/ subdirectory
2. Move ConfigurationManager.kt to services/managers/
3. Move UserSessionManager.kt to services/managers/
4. Move UploadManager.kt to services/managers/
5. Move ChallengeManager.kt from ui/dashboard/ to services/managers/
6. Move VoicesLabelManager.kt from ui/voices/ to services/managers/
7. Update package declarations in all moved files
8. Update imports in SyncManager, AutoSyncWorker, and MainApplication
9. Update DI modules if necessary
10. Verify clean build
:::

---

### Service Organization: Group Worker Classes into services/workers/ Subdirectory

Worker classes (AutoSyncWorker, DownloadWorker, NetworkMonitorWorker, etc.) are mixed with other service files. Creating a dedicated services/workers/ subdirectory clarifies their role as background task executors.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt#L1-L20"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt#L1-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt#L1-L15"}

:::task-stub{title="Organize Worker Classes in services/workers/"}
1. Create services/workers/ subdirectory
2. Move AutoSyncWorker.kt to services/workers/
3. Move DownloadWorker.kt to services/workers/
4. Move NetworkMonitorWorker.kt to services/workers/
5. Move ServerReachabilityWorker.kt to services/workers/
6. Move TaskNotificationWorker.kt to services/workers/
7. Move StayOnlineWorker.kt to services/workers/
8. Update package declarations in all worker files
9. Update MainApplication.kt WorkManager references
10. Update any DI entry points
11. Verify successful build
:::

---

## Summary

These 15 tasks focus exclusively on naming consistency and structural organization improvements:

- **Tasks 1-12**: Standardize repository naming from plural to singular (TeamsRepository → TeamRepository, ActivitiesRepository → ActivityRepository, EventsRepository → EventRepository, VoicesRepository → VoiceRepository, PersonalsRepository → PersonalRepository, NotificationsRepository → NotificationRepository, RatingsRepository → RatingRepository, TagsRepository → TagRepository, ResourcesRepository → ResourceRepository, CoursesRepository → CourseRepository, SubmissionsRepository → SubmissionRepository, SurveysRepository → SurveyRepository)
- **Task 13**: Organize 33 callback interfaces into feature-based subdirectories (chat/, teams/, sync/, library/, dashboard/)
- **Task 14**: Consolidate manager classes into services/managers/ subdirectory (ConfigurationManager, UserSessionManager, UploadManager, ChallengeManager, VoicesLabelManager)
- **Task 15**: Group worker classes into services/workers/ subdirectory (AutoSyncWorker, DownloadWorker, NetworkMonitorWorker, ServerReachabilityWorker, TaskNotificationWorker, StayOnlineWorker)

All tasks are:
- ✅ Non-functional changes (no logic modifications)
- ✅ Independently executable (parallel-safe)
- ✅ Low merge conflict risk (different files)
- ✅ Focused on naming/organization only
- ❌ No new classes or ViewModels
- ❌ No architectural changes
- ❌ No business logic modifications
