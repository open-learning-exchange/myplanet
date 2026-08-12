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
    'last sync defaults to never and persists successful sync time',
    () async {
      SharedPreferences.setMockInitialValues({});
      final sharedPreferences = await SharedPreferences.getInstance();
      final prefs = PlanetPrefs(
        sharedPreferences,
        secureStorage: _MockSecureStorage(),
      );

      expect(prefs.lastSync, 0);

      await prefs.setLastSync(123456789);

      expect(prefs.lastSync, 123456789);
      expect(sharedPreferences.getInt('LastSync'), 123456789);
    },
  );

  test('language code defaults to empty and persists a selection', () async {
    SharedPreferences.setMockInitialValues({});
    final sharedPreferences = await SharedPreferences.getInstance();
    final prefs = PlanetPrefs(
      sharedPreferences,
      secureStorage: _MockSecureStorage(),
    );

    expect(prefs.languageCode, isEmpty);

    await prefs.setLanguageCode('es');

    expect(prefs.languageCode, 'es');
    expect(sharedPreferences.getString('languageCode'), 'es');
  });
}
