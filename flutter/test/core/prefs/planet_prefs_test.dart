import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MockSecureStorage extends Mock implements FlutterSecureStorage {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('onboarding is incomplete by default and persists completion', () async {
    SharedPreferences.setMockInitialValues({});
    final sharedPreferences = await SharedPreferences.getInstance();
    final prefs = PlanetPrefs(
      sharedPreferences,
      secureStorage: _MockSecureStorage(),
    );

    expect(prefs.onboardingComplete, isFalse);

    await prefs.setOnboardingComplete();

    expect(prefs.onboardingComplete, isTrue);
    expect(sharedPreferences.getBool('onboardingComplete'), isTrue);
  });

  test('theme defaults to system and persists a user selection', () async {
    SharedPreferences.setMockInitialValues({});
    final sharedPreferences = await SharedPreferences.getInstance();
    final prefs = PlanetPrefs(
      sharedPreferences,
      secureStorage: _MockSecureStorage(),
    );

    expect(prefs.themeModeName, 'system');

    await prefs.setThemeModeName('dark');

    expect(prefs.themeModeName, 'dark');
    expect(sharedPreferences.getString('themeMode'), 'dark');
  });

  test(
    'background work preferences default to Kotlin policy and persist',
    () async {
      SharedPreferences.setMockInitialValues({});
      final sharedPreferences = await SharedPreferences.getInstance();
      final prefs = PlanetPrefs(
        sharedPreferences,
        secureStorage: _MockSecureStorage(),
      );

      expect(prefs.autoSyncEnabled, isTrue);
      expect(prefs.autoSyncInterval, const Duration(hours: 1));
      expect(prefs.lastSync, isNull);

      final syncedAt = DateTime.utc(2026, 8, 16, 12, 30);
      await prefs.setAutoSyncEnabled(false);
      await prefs.setAutoSyncInterval(const Duration(minutes: 30));
      await prefs.setLastSync(syncedAt);

      expect(prefs.autoSyncEnabled, isFalse);
      expect(prefs.autoSyncInterval, const Duration(minutes: 30));
      expect(prefs.lastSync, syncedAt);
    },
  );

  test('background diagnostics persist only stable fields', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = PlanetPrefs(
      await SharedPreferences.getInstance(),
      secureStorage: _MockSecureStorage(),
    );

    await prefs.recordBackgroundRun(
      taskName: 'myplanet.autoSync',
      attemptedAt: DateTime.utc(2026, 8, 16, 13, 45),
      status: 'retryRequested',
      failedSteps: const ['resources', 'outboxDrain'],
    );

    expect(prefs.lastBackgroundRun, {
      'taskName': 'myplanet.autoSync',
      'attemptedAt': '2026-08-16T13:45:00.000Z',
      'status': 'retryRequested',
      'failedSteps': ['resources', 'outboxDrain'],
    });
  });

  test('malformed background diagnostics fail closed', () async {
    SharedPreferences.setMockInitialValues({'backgroundRun': 'not-json'});
    final prefs = PlanetPrefs(
      await SharedPreferences.getInstance(),
      secureStorage: _MockSecureStorage(),
    );

    expect(prefs.lastBackgroundRun, isNull);
  });
}
