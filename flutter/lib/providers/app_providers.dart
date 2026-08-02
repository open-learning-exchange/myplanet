import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/prefs/planet_prefs.dart';
import '../core/sync/server_url_mapper.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../repository/configurations_repository.dart';
import '../repository/courses_repository.dart';
import '../repository/resources_repository.dart';
import '../repository/shelf_repository.dart';
import '../repository/user_repository.dart';

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

/// Replaces `DatabaseModule` / `DatabaseService`.
final appDatabaseProvider = Provider<AppDatabase>((ref) {
  final database = AppDatabase.open();
  ref.onDispose(database.close);
  return database;
});

final userDaoProvider = Provider<UserDao>(
  (ref) => ref.watch(appDatabaseProvider).userDao,
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
  ),
);

final coursesRepositoryProvider = Provider<CoursesRepository>(
  (ref) => CoursesRepository(
    ref.watch(planetApiProvider),
    ref.watch(courseDaoProvider),
    ref.watch(removedLogDaoProvider),
  ),
);

final shelfRepositoryProvider = Provider<ShelfRepository>(
  (ref) => ShelfRepository(
    ref.watch(planetApiProvider),
    ref.watch(courseDaoProvider),
    ref.watch(myLibraryDaoProvider),
    ref.watch(removedLogDaoProvider),
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
