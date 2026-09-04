import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/settings_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MockSecureStorage extends Mock implements FlutterSecureStorage {}

/// Pins `ClearDataNotifier.clearAllData`: it must wipe every Drift table and
/// every preference except `onboardingComplete`, then null the session and
/// server config states so the router redirects off the home screen.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AppDatabase db;
  late PlanetPrefs prefs;
  late _MockSecureStorage secureStorage;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
    secureStorage = _MockSecureStorage();
    registerFallbackValue('');
    when(
      () => secureStorage.write(
        key: any(named: 'key'),
        value: any(named: 'value'),
      ),
    ).thenAnswer((_) async {});
    when(
      () => secureStorage.read(key: any(named: 'key')),
    ).thenAnswer((_) async => null);
    when(
      () => secureStorage.delete(key: any(named: 'key')),
    ).thenAnswer((_) async {});
    when(secureStorage.deleteAll).thenAnswer((_) async {});
    prefs = PlanetPrefs(
      await SharedPreferences.getInstance(),
      secureStorage: secureStorage,
    );
  });
  tearDown(() => db.close());

  ProviderContainer container() {
    final c = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetPrefsProvider.overrideWithValue(prefs),
      ],
    );
    addTearDown(c.dispose);
    return c;
  }

  test('clears all database tables', () async {
    await db.userDao.upsert(
      UserRow(
        id: 'u1',
        name: 'ada',
        rolesList: const [],
        userAdmin: false,
        joinDate: 0,
        isArchived: false,
        isUpdated: false,
      ).toCompanion(false),
    );
    expect(await db.userDao.getById('u1'), isNotNull);

    final c = container();
    await c.read(clearDataProvider.notifier).clearAllData();

    expect(await db.userDao.getById('u1'), isNull);
  });

  test(
    'preserves onboarding complete but clears server config and session',
    () async {
      await prefs.setOnboardingComplete();
      await prefs.saveServerConfig(
        const ServerConfig(
          serverUrl: 'https://planet.example',
          couchDbUrl: 'https://satellite:1234@planet.example:443',
          pin: '1234',
        ),
      );
      await prefs.setLoggedInUserId('u1');

      expect(prefs.onboardingComplete, isTrue);
      expect(prefs.serverConfig, isNotNull);
      expect(prefs.loggedInUserId, 'u1');

      final c = container();
      await c.read(clearDataProvider.notifier).clearAllData();

      expect(prefs.onboardingComplete, isTrue);
      expect(prefs.serverConfig, isNull);
      expect(prefs.loggedInUserId, isNull);
      expect(c.read(serverConfigProvider), isNull);
      expect(c.read(sessionProvider).valueOrNull, isNull);
    },
  );

  test('clears text scale preference', () async {
    await prefs.setTextScale(1.15);
    expect(prefs.textScale, 1.15);

    final c = container();
    await c.read(clearDataProvider.notifier).clearAllData();

    expect(prefs.textScale, 1.0);
  });
}
