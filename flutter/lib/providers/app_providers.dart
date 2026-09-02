import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/background/background_download_queue.dart';
import '../core/background/background_scheduler.dart';
import '../core/notifications/notification_presenter.dart';
import '../core/notifications/task_deadline_notifier.dart';
import '../core/prefs/planet_prefs.dart';
import '../core/sync/server_url_mapper.dart';
import '../core/system/app_version_info.dart';
import '../core/system/device_stats.dart';
import '../core/system/device_identity.dart';
import '../core/files/resource_files.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../repository/achievements_repository.dart';
import '../repository/achievements_uploader.dart';
import '../repository/activities_repository.dart';
import '../repository/activities_uploader.dart';
import '../repository/chat_repository.dart';
import '../repository/chat_repository_impl.dart';
import '../repository/chat_uploader.dart';
import '../repository/configurations_repository.dart';
import '../repository/course_progress_uploader.dart';
import '../repository/courses_repository.dart';
import '../repository/dictionary_repository.dart';
import '../repository/events_repository.dart';
import '../repository/events_uploader.dart';
import '../repository/feedback_repository.dart';
import '../repository/feedback_repository_impl.dart';
import '../repository/health_repository.dart';
import '../repository/health_uploader.dart';
import '../repository/ratings_uploader.dart';
import '../repository/notifications_repository.dart';
import '../repository/outbox_drainer.dart';
import '../repository/outbox_repository.dart';
import '../repository/personals_uploader.dart';
import '../repository/personals_repository.dart';
import '../repository/progress_repository.dart';
import '../repository/public_survey_uploader.dart';
import '../repository/ratings_repository.dart';
import '../repository/life_repository.dart';
import '../repository/resource_downloader.dart';
import '../repository/resources_repository.dart';
import '../repository/shelf_repository.dart';
import '../repository/myplanet_activities_uploader.dart';
import '../repository/user_repository.dart';
import '../repository/voices_repository.dart';
import '../repository/voices_uploader.dart';
import '../repository/submissions_repository.dart';
import '../repository/submissions_uploader.dart';
import '../repository/submit_photos_uploader.dart';
import '../repository/user_uploader.dart';
import '../repository/submissions_exporter.dart';
import '../repository/surveys_repository.dart';
import '../repository/teams_repository.dart';
import '../repository/team_tasks_repository.dart';
import '../repository/team_log_uploader.dart';
import '../repository/search_activity_repository.dart';
import '../repository/search_activity_uploader.dart';
import '../repository/tags_repository.dart';
import '../repository/team_tasks_uploader.dart';
import '../repository/feedback_uploader.dart';
import '../repository/teams_uploader.dart';
import 'resources_providers.dart' show diskStatsProvider;

/// The dependency graph, replacing the Hilt modules in `di/`.
///
/// Riverpod providers cover what `@Module`/`@Provides`/`@Binds` did, with two
/// practical differences: the graph is resolved at runtime rather than by an
/// annotation processor (no kapt/KSP step), and there is no need for the
/// `@EntryPoint` escape hatches `di/` defines for Workers — any code holding a
/// `Ref` or `ProviderContainer` can read a provider directly.

/// Overridden in `main()` once [SharedPreferences] has loaded, the same way
/// `SharedPreferencesModule` provides a ready instance.
final planetPrefsProvider = Provider<PlanetPrefs>(
  (ref) => throw UnimplementedError('planetPrefsProvider must be overridden'),
);

final backgroundSchedulerProvider = Provider<BackgroundScheduler>(
  (ref) => const WorkmanagerScheduler(),
);

/// Replaces `DatabaseModule` / `DatabaseService`.
final appDatabaseProvider = Provider<AppDatabase>((ref) {
  final database = AppDatabase.open();
  ref.onDispose(database.close);
  return database;
});

final downloadQueueDaoProvider = Provider<DownloadQueueDao>(
  (ref) => ref.watch(appDatabaseProvider).downloadQueueDao,
);

final userDaoProvider = Provider<UserDao>(
  (ref) => ref.watch(appDatabaseProvider).userDao,
);

/// A single cached `users` row by id, for the team members list and member
/// detail screen. `null` when the user document is not in the local cache.
final userByIdProvider = FutureProvider.family<UserRow?, String>(
  (ref, id) => ref.watch(userDaoProvider).getById(id),
);

final myLibraryDaoProvider = Provider<MyLibraryDao>(
  (ref) => ref.watch(appDatabaseProvider).myLibraryDao,
);

final courseDaoProvider = Provider<CourseDao>(
  (ref) => ref.watch(appDatabaseProvider).courseDao,
);

final removedLogDaoProvider = Provider<RemovedLogDao>(
  (ref) => ref.watch(appDatabaseProvider).removedLogDao,
);

final dictionaryDaoProvider = Provider<DictionaryDao>(
  (ref) => ref.watch(appDatabaseProvider).dictionaryDao,
);

final notificationDaoProvider = Provider<NotificationDao>(
  (ref) => ref.watch(appDatabaseProvider).notificationDao,
);

final meetupDaoProvider = Provider<MeetupDao>(
  (ref) => ref.watch(appDatabaseProvider).meetupDao,
);

final teamDaoProvider = Provider<TeamDao>(
  (ref) => ref.watch(appDatabaseProvider).teamDao,
);

final teamsRepositoryProvider = Provider<TeamsRepository>(
  (ref) => TeamsRepository(
    ref.watch(planetApiProvider),
    ref.watch(teamDaoProvider),
    ref.watch(appDatabaseProvider).teamLogDao,
  ),
);
final teamTaskDaoProvider = Provider<TeamTaskDao>(
  (ref) => ref.watch(appDatabaseProvider).teamTaskDao,
);
final teamTasksRepositoryProvider = Provider<TeamTasksRepository>(
  (ref) => TeamTasksRepository(ref.watch(teamTaskDaoProvider)),
);
final teamTasksUploaderProvider = Provider<TeamTasksUploader>(
  (ref) => TeamTasksUploader(
    ref.watch(planetApiProvider),
    ref.watch(teamTasksRepositoryProvider),
    ref.watch(outboxRepositoryProvider),
  ),
);

final teamsUploaderProvider = Provider<TeamsUploader>(
  (ref) => TeamsUploader(
    ref.watch(planetApiProvider),
    ref.watch(teamDaoProvider),
    ref.watch(deviceIdentitySourceProvider),
  ),
);

final teamLogUploaderProvider = Provider<TeamLogUploader>(
  (ref) => TeamLogUploader(
    ref.watch(planetApiProvider),
    ref.watch(teamsRepositoryProvider),
    ref.watch(appDatabaseProvider).teamLogDao,
    ref.watch(outboxRepositoryProvider),
    ref.watch(deviceIdentitySourceProvider),
  ),
);

final searchActivityDaoProvider = Provider<SearchActivityDao>(
  (ref) => ref.watch(appDatabaseProvider).searchActivityDao,
);

final searchActivityRepositoryProvider = Provider<SearchActivityRepository>(
  (ref) => SearchActivityRepository(ref.watch(searchActivityDaoProvider)),
);

final searchActivityUploaderProvider = Provider<SearchActivityUploader>(
  (ref) => SearchActivityUploader(
    ref.watch(planetApiProvider),
    ref.watch(searchActivityRepositoryProvider),
    ref.watch(searchActivityDaoProvider),
    ref.watch(outboxRepositoryProvider),
    ref.watch(deviceIdentitySourceProvider),
  ),
);

final tagsRepositoryProvider = Provider<TagsRepository>(
  (ref) => TagsRepository(
    ref.watch(planetApiProvider),
    ref.watch(appDatabaseProvider).tagDao,
  ),
);

final achievementDaoProvider = Provider<AchievementDao>(
  (ref) => ref.watch(appDatabaseProvider).achievementDao,
);

final userChallengeActionDaoProvider = Provider<UserChallengeActionDao>(
  (ref) => ref.watch(appDatabaseProvider).userChallengeActionDao,
);

final achievementsRepositoryProvider = Provider<AchievementsRepository>(
  (ref) => AchievementsRepository(ref.watch(achievementDaoProvider)),
);

final achievementsUploaderProvider = Provider<AchievementsUploader>(
  (ref) => AchievementsUploader(
    ref.watch(planetApiProvider),
    ref.watch(achievementsRepositoryProvider),
    ref.watch(achievementDaoProvider),
    ref.watch(outboxRepositoryProvider),
  ),
);

final eventsRepositoryProvider = Provider<EventsRepository>(
  (ref) => EventsRepository(
    ref.watch(planetApiProvider),
    ref.watch(meetupDaoProvider),
  ),
);

final eventsUploaderProvider = Provider<EventsUploader>(
  (ref) => EventsUploader(
    ref.watch(planetApiProvider),
    ref.watch(eventsRepositoryProvider),
    ref.watch(outboxRepositoryProvider),
  ),
);

final myLifeDaoProvider = Provider<MyLifeDao>(
  (ref) => ref.watch(appDatabaseProvider).myLifeDao,
);

final personalDaoProvider = Provider<PersonalDao>(
  (ref) => ref.watch(appDatabaseProvider).personalDao,
);

final ratingDaoProvider = Provider<RatingDao>(
  (ref) => ref.watch(appDatabaseProvider).ratingDao,
);

final submissionDaoProvider = Provider<SubmissionDao>(
  (ref) => ref.watch(appDatabaseProvider).submissionDao,
);

final submitPhotosDaoProvider = Provider<SubmitPhotosDao>(
  (ref) => ref.watch(appDatabaseProvider).submitPhotosDao,
);

final surveyDaoProvider = Provider<SurveyDao>(
  (ref) => ref.watch(appDatabaseProvider).surveyDao,
);

final examDaoProvider = Provider<ExamDao>(
  (ref) => ref.watch(appDatabaseProvider).examDao,
);

final courseProgressDaoProvider = Provider<CourseProgressDao>(
  (ref) => ref.watch(appDatabaseProvider).courseProgressDao,
);

final certificationDaoProvider = Provider<CertificationDao>(
  (ref) => ref.watch(appDatabaseProvider).certificationDao,
);

final offlineActivityDaoProvider = Provider<OfflineActivityDao>(
  (ref) => ref.watch(appDatabaseProvider).offlineActivityDao,
);

final resourceActivityDaoProvider = Provider<ResourceActivityDao>(
  (ref) => ref.watch(appDatabaseProvider).resourceActivityDao,
);

final courseActivityDaoProvider = Provider<CourseActivityDao>(
  (ref) => ref.watch(appDatabaseProvider).courseActivityDao,
);

final teamNotificationDaoProvider = Provider<TeamNotificationDao>(
  (ref) => ref.watch(appDatabaseProvider).teamNotificationDao,
);

final activitiesRepositoryProvider = Provider<ActivitiesRepository>(
  (ref) => ActivitiesRepository(
    ref.watch(planetApiProvider),
    ref.watch(offlineActivityDaoProvider),
    ref.watch(resourceActivityDaoProvider),
    ref.watch(courseActivityDaoProvider),
    ref.watch(userChallengeActionDaoProvider),
  ),
);

final activitiesUploaderProvider = Provider<ActivitiesUploader>(
  (ref) => ActivitiesUploader(
    ref.watch(planetApiProvider),
    ref.watch(activitiesRepositoryProvider),
    ref.watch(outboxRepositoryProvider),
    ref.watch(deviceStatsProvider),
    ref.watch(planetPrefsProvider),
  ),
);

/// The device-stats seam. Production wires the method-channel-backed
/// [DeviceStats] instance; widget and repository tests override this with a
/// fake so the platform channel is never invoked.
final deviceStatsProvider = Provider<DeviceStats>(
  (ref) => DeviceStats.instance,
);

final deviceIdentitySourceProvider = Provider<DeviceIdentitySource>(
  (ref) => PlatformDeviceIdentitySource(
    ref.watch(deviceStatsProvider),
    ref.watch(planetPrefsProvider),
  ),
);

/// Port of the `myplanet_activities` telemetry upload path
/// (`ActivitiesRepositoryImpl.uploadMyPlanetActivities`).
final myPlanetActivitiesUploaderProvider = Provider<MyPlanetActivitiesUploader>(
  (ref) => MyPlanetActivitiesUploader(
    ref.watch(planetApiProvider),
    ref.watch(planetPrefsProvider),
    ref.watch(deviceStatsProvider),
  ),
);

final resourceDownloaderProvider = Provider<ResourceDownloader>(
  (ref) => ResourceDownloader(
    ref.watch(planetApiProvider),
    ref.watch(myLibraryDaoProvider),
    queue: BackgroundDownloadQueue(
      ref.watch(downloadQueueDaoProvider),
      ref.watch(backgroundSchedulerProvider),
    ),
  ),
);

/// Reads a text/CSV/markdown attachment's contents as a string.
///
/// The text renderers in `ResourceViewerScreen` call this instead of
/// `File.readAsString` so a widget test can override it (returning a fixed
/// string) and exercise the rendering pipeline without real `dart:io`, which
/// hangs under the test binding's fake clock. Production delegates to
/// `ResourceFiles.readTextContent`.
typedef ResourceContentReader =
    Future<String?> Function({required String docId, required String filename});

final resourceContentReaderProvider = Provider<ResourceContentReader>(
  (_) => ResourceFiles.readTextContent,
);

final chatDaoProvider = Provider<ChatDao>(
  (ref) => ref.watch(appDatabaseProvider).chatDao,
);

final chatRepositoryProvider = Provider<ChatRepository>((ref) {
  final api = ref.watch(planetApiProvider);
  final dao = ref.watch(chatDaoProvider);
  final config = ref.watch(serverConfigProvider);
  final serverUrl = config == null ? '' : UrlUtils.credentialFreeDbUrl(config);
  return ChatRepositoryImpl(planetApi: api, chatDao: dao, serverUrl: serverUrl);
});

final chatUploaderProvider = Provider<ChatUploader>(
  (ref) => ChatUploader(
    ref.watch(planetApiProvider),
    ref.watch(chatDaoProvider),
    ref.watch(outboxRepositoryProvider),
  ),
);

final userUploaderProvider = Provider<UserUploader>(
  (ref) => UserUploader(
    ref.watch(planetApiProvider),
    ref.watch(userDaoProvider),
    ref.watch(outboxRepositoryProvider),
    onCreated:
        ({
          required localId,
          required config,
          required username,
          required password,
        }) async {
          final repo = ref.read(userRepositoryProvider);
          await repo.saveKeyIv(
            localId: localId,
            config: config,
            username: username,
            password: password,
          );
          // Rewrite health examinations' userId from the local id to the
          // server-assigned couch id, as `updateHealthFn` does. The row just
          // gained its couchId from markUploaded; reading it back gets the
          // fresh value.
          final user = await ref.read(userDaoProvider).getById(localId);
          final couchId = user?.couchId;
          if (couchId != null && couchId.isNotEmpty) {
            await ref
                .read(healthExaminationDaoProvider)
                .updateUserId(localId, couchId);
          }
        },
    readConfig: () => ref.read(serverConfigProvider),
    readPassword: () => ref.read(planetPrefsProvider).readPassword(),
  ),
);

final feedbackDaoProvider = Provider<FeedbackDao>(
  (ref) => ref.watch(appDatabaseProvider).feedbackDao,
);

final feedbackUploaderProvider = Provider<FeedbackUploader>(
  (ref) => FeedbackUploader(
    ref.watch(planetApiProvider),
    ref.watch(feedbackRepositoryProvider),
    ref.watch(feedbackDaoProvider),
    ref.watch(outboxRepositoryProvider),
  ),
);

final feedbackRepositoryProvider = Provider<FeedbackRepository>((ref) {
  final dao = ref.watch(feedbackDaoProvider);
  final api = ref.watch(planetApiProvider);
  return FeedbackRepositoryImpl(feedbackDao: dao, planetApi: api);
});

final submissionsRepositoryProvider = Provider<SubmissionsRepository>(
  (ref) => SubmissionsRepository(
    ref.watch(planetApiProvider),
    ref.watch(submissionDaoProvider),
    ref.watch(submitPhotosDaoProvider),
    ref.watch(surveyDaoProvider),
  ),
);

final surveysRepositoryProvider = Provider<SurveysRepository>(
  (ref) => SurveysRepository(
    ref.watch(planetApiProvider),
    ref.watch(surveyDaoProvider),
    ref.watch(examDaoProvider),
    ref.watch(submissionsRepositoryProvider),
    urlMapper: ref.watch(serverUrlMapperProvider),
  ),
);

final submissionsUploaderProvider = Provider<SubmissionsUploader>(
  (ref) => SubmissionsUploader(
    ref.watch(planetApiProvider),
    ref.watch(submissionsRepositoryProvider),
    ref.watch(outboxRepositoryProvider),
    ref.watch(deviceIdentitySourceProvider),
  ),
);

final submissionsExporterProvider = Provider<SubmissionsExporter>(
  (ref) => SubmissionsExporter(ref.watch(submissionsRepositoryProvider)),
);

final submitPhotosUploaderProvider = Provider<SubmitPhotosUploader>(
  (ref) => SubmitPhotosUploader(
    ref.watch(planetApiProvider),
    ref.watch(submissionsRepositoryProvider),
    ref.watch(outboxRepositoryProvider),
    ref.watch(deviceIdentitySourceProvider),
  ),
);

/// Replaces `NetworkModule`.
final planetApiProvider = Provider<PlanetApi>(
  (ref) => PlanetApi.withDefaults(),
);

final serverUrlMapperProvider = Provider<ServerUrlMapper>(
  (ref) => ServerUrlMapper(),
);

/// Replaces `RepositoryModule`'s `@Binds` declarations.
final configurationsRepositoryProvider = Provider<ConfigurationsRepository>(
  (ref) => ConfigurationsRepository(
    ref.watch(planetApiProvider),
    ref.watch(serverUrlMapperProvider),
    // The server's `minapk` is compared against the build's real version, not
    // a constant somebody has to bump by hand — under-reporting it fails the
    // whole configuration check. Read lazily, so this costs nothing until a
    // configuration attempt actually happens.
    appVersionLookup: () async =>
        (await ref.read(appVersionInfoProvider.future)).version,
  ),
);

final userRepositoryProvider = Provider<UserRepository>(
  (ref) =>
      UserRepository(ref.watch(planetApiProvider), ref.watch(userDaoProvider)),
);

final resourcesRepositoryProvider = Provider<ResourcesRepository>(
  (ref) => ResourcesRepository(
    ref.watch(planetApiProvider),
    ref.watch(myLibraryDaoProvider),
    ref.watch(removedLogDaoProvider),
  ),
);

final dictionaryRepositoryProvider = Provider<DictionaryRepository>(
  (ref) => DictionaryRepository(
    ref.watch(planetApiProvider),
    ref.watch(dictionaryDaoProvider),
  ),
);

final notificationsRepositoryProvider = Provider<NotificationsRepository>(
  (ref) => NotificationsRepository(
    ref.watch(notificationDaoProvider),
    teamNotificationDao: ref.watch(teamNotificationDaoProvider),
    newsDao: ref.watch(newsDaoProvider),
    teamTaskDao: ref.watch(teamTaskDaoProvider),
    api: ref.watch(planetApiProvider),
  ),
);

final lifeRepositoryProvider = Provider<LifeRepository>(
  (ref) => LifeRepository(ref.watch(myLifeDaoProvider)),
);

/// The OS notification surface. Overridden with a fake in tests, the same way
/// [diskStatsProvider] is — a real one would need a platform channel.
final notificationPresenterProvider = Provider<NotificationPresenter>(
  (ref) => LocalNotificationsPresenter(),
);

/// Port of `TaskNotificationWorker`'s dependencies. Read from the background
/// isolate's own container, so everything it needs is constructed there.
final taskDeadlineNotifierProvider = Provider<TaskDeadlineNotifier>(
  (ref) => TaskDeadlineNotifier(
    tasks: ref.watch(teamTasksRepositoryProvider),
    notifications: ref.watch(notificationsRepositoryProvider),
    presenter: ref.watch(notificationPresenterProvider),
    diskStats: ref.watch(diskStatsProvider),
    // Supplies the cached available-storage figure for the headless case, where
    // the `disk_stats` channel does not exist.
    prefs: ref.watch(planetPrefsProvider),
  ),
);

final personalsRepositoryProvider = Provider<PersonalsRepository>(
  (ref) => PersonalsRepository(ref.watch(personalDaoProvider)),
);

final ratingsRepositoryProvider = Provider<RatingsRepository>(
  (ref) => RatingsRepository(ref.watch(ratingDaoProvider)),
);

final coursesRepositoryProvider = Provider<CoursesRepository>(
  (ref) => CoursesRepository(
    ref.watch(planetApiProvider),
    ref.watch(courseDaoProvider),
    ref.watch(removedLogDaoProvider),
    ref.watch(examDaoProvider),
    ref.watch(surveyDaoProvider),
  ),
);

final newsDaoProvider = Provider<NewsDao>(
  (ref) => ref.watch(appDatabaseProvider).newsDao,
);

final voicesRepositoryProvider = Provider<VoicesRepository>(
  (ref) => VoicesRepository(
    ref.watch(planetApiProvider),
    ref.watch(newsDaoProvider),
  ),
);

final voicesUploaderProvider = Provider<VoicesUploader>(
  (ref) => VoicesUploader(
    ref.watch(planetApiProvider),
    ref.watch(voicesRepositoryProvider),
    ref.watch(outboxRepositoryProvider),
  ),
);

final outboxDaoProvider = Provider<OutboxDao>(
  (ref) => ref.watch(appDatabaseProvider).outboxDao,
);

final outboxRepositoryProvider = Provider<OutboxRepository>(
  (ref) => OutboxRepository(ref.watch(outboxDaoProvider)),
);

final personalsUploaderProvider = Provider<PersonalsUploader>(
  (ref) => PersonalsUploader(
    ref.watch(planetApiProvider),
    ref.watch(personalsRepositoryProvider),
    ref.watch(outboxRepositoryProvider),
    ref.watch(deviceIdentitySourceProvider),
  ),
);

/// Replaces `RetryQueueWorker`'s WorkManager registration in `MainApplication`.
///
/// Handlers are registered per `uploadType`; anything without one is replayed
/// verbatim from the stored payload. The credential is *not* held here — it is
/// passed to `drain()` per call, so it cannot go stale against the current
/// [serverConfigProvider] or linger after the config is cleared.
final outboxDrainerProvider = Provider<OutboxDrainer>((ref) {
  return OutboxDrainer(
    ref.watch(planetApiProvider),
    ref.watch(outboxRepositoryProvider),
    handlers: {
      PersonalsUploader.type: ref.watch(personalsUploaderProvider).handler,
      SubmissionsUploader.type: ref.watch(submissionsUploaderProvider).handler,
      SubmitPhotosUploader.type: ref
          .watch(submitPhotosUploaderProvider)
          .handler,
      EventsUploader.type: ref.watch(eventsUploaderProvider).handler,
      VoicesUploader.type: ref.watch(voicesUploaderProvider).handler,
      TeamTasksUploader.type: ref.watch(teamTasksUploaderProvider).handler,
      TeamLogUploader.type: ref.watch(teamLogUploaderProvider).handler,
      SearchActivityUploader.type: ref
          .watch(searchActivityUploaderProvider)
          .handler,
      for (final type in TeamsUploader.types)
        type: ref.watch(teamsUploaderProvider).handler,
      FeedbackUploader.type: ref.watch(feedbackUploaderProvider).handler,
      HealthUploader.type: ref.watch(healthUploaderProvider).handler,
      RatingsUploader.type: ref.watch(ratingsUploaderProvider).handler,
      CourseProgressUploader.type: ref
          .watch(courseProgressUploaderProvider)
          .handler,
      ChatUploader.type: ref.watch(chatUploaderProvider).handler,
      UserUploader.type: ref.watch(userUploaderProvider).handler,
      ...ref.watch(activitiesUploaderProvider).handlers,
      PublicSurveyUploader.type: ref
          .watch(publicSurveyUploaderProvider)
          .handler,
      AchievementsUploader.type: ref
          .watch(achievementsUploaderProvider)
          .handler,
    },
  );
});

/// Number of writes waiting to reach the server, for the UI to surface.
final pendingUploadCountProvider = StreamProvider<int>(
  (ref) => ref.watch(outboxRepositoryProvider).watchPendingCount(),
);

final shelfRepositoryProvider = Provider<ShelfRepository>(
  (ref) => ShelfRepository(
    ref.watch(planetApiProvider),
    ref.watch(courseDaoProvider),
    ref.watch(myLibraryDaoProvider),
    ref.watch(removedLogDaoProvider),
    ref.watch(meetupDaoProvider),
  ),
);

/// The configured server, or `null` before the first successful handshake.
/// Drives the router's redirect, so persisting a config navigates the app.
class ServerConfigNotifier extends Notifier<ServerConfig?> {
  @override
  ServerConfig? build() => ref.watch(planetPrefsProvider).serverConfig;

  Future<void> save(ServerConfig config) async {
    await ref.read(planetPrefsProvider).saveServerConfig(config);
    state = config;
  }

  Future<void> clear() async {
    // Must reach storage, not just state: `build()` reads the persisted config
    // back on the next cold start.
    await ref.read(planetPrefsProvider).clearServerConfig();
    state = null;
  }
}

final serverConfigProvider =
    NotifierProvider<ServerConfigNotifier, ServerConfig?>(
      ServerConfigNotifier.new,
    );

/// First-launch gate used by the declarative router instead of launching and
/// finishing `OnboardingActivity` imperatively.
class OnboardingNotifier extends Notifier<bool> {
  @override
  bool build() => ref.watch(planetPrefsProvider).onboardingComplete;

  Future<void> complete() async {
    await ref.read(planetPrefsProvider).setOnboardingComplete();
    state = true;
  }
}

final onboardingProvider = NotifierProvider<OnboardingNotifier, bool>(
  OnboardingNotifier.new,
);

/// Health examination DAO provider.
final healthExaminationDaoProvider = Provider<HealthExaminationDao>(
  (ref) => ref.watch(appDatabaseProvider).healthExaminationDao,
);

/// Health repository provider.
final healthRepositoryProvider = Provider<HealthRepository>((ref) {
  final api = ref.watch(planetApiProvider);
  final dao = ref.watch(healthExaminationDaoProvider);
  final config = ref.watch(serverConfigProvider);
  return HealthRepository(api, dao, ref.watch(userDaoProvider), config: config);
});

final ratingsUploaderProvider = Provider<RatingsUploader>(
  (ref) => RatingsUploader(
    ref.watch(planetApiProvider),
    ref.watch(ratingsRepositoryProvider),
    ref.watch(appDatabaseProvider).ratingDao,
    ref.watch(userDaoProvider),
    ref.watch(outboxRepositoryProvider),
    ref.watch(deviceIdentitySourceProvider),
  ),
);

final progressRepositoryProvider = Provider<ProgressRepository>(
  (ref) => ProgressRepository(
    ref.watch(planetApiProvider),
    ref.watch(courseDaoProvider),
    ref.watch(courseProgressDaoProvider),
    ref.watch(examDaoProvider),
    ref.watch(submissionDaoProvider),
    ref.watch(certificationDaoProvider),
  ),
);

final publicSurveyUploaderProvider = Provider<PublicSurveyUploader>(
  (ref) => PublicSurveyUploader(
    ref.watch(planetApiProvider),
    ref.watch(surveysRepositoryProvider),
    ref.watch(submissionsRepositoryProvider),
    ref.watch(outboxRepositoryProvider),
  ),
);

final courseProgressUploaderProvider = Provider<CourseProgressUploader>(
  (ref) => CourseProgressUploader(
    ref.watch(planetApiProvider),
    ref.watch(courseProgressDaoProvider),
    ref.watch(outboxRepositoryProvider),
  ),
);

final healthUploaderProvider = Provider<HealthUploader>(
  (ref) => HealthUploader(
    ref.watch(planetApiProvider),
    ref.watch(healthRepositoryProvider),
    ref.watch(healthExaminationDaoProvider),
    ref.watch(outboxRepositoryProvider),
  ),
);

/// The running app's version name and build number, read through
/// `package_info_plus` — the runtime equivalent of the Kotlin app's
/// `BuildConfig.VERSION_NAME` / `VERSION_CODE`. The About and Settings
/// screens watch this instead of a hardcoded constant, so the version line
/// tracks pubspec rather than drifting on each release. Tests override it
/// to inject a fixed `AppVersionInfo`.
final appVersionInfoProvider = FutureProvider<AppVersionInfo>(
  (ref) => loadAppVersionInfo(),
);
