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

  group('survey reminders', () {
    Future<PlanetPrefs> prefs() async {
      SharedPreferences.setMockInitialValues({});
      return PlanetPrefs(
        await SharedPreferences.getInstance(),
        secureStorage: _MockSecureStorage(),
      );
    }

    test('a scheduled reminder is recorded against its id set', () async {
      final planetPrefs = await prefs();

      expect(planetPrefs.isReminderScheduled('a,b'), isFalse);

      await planetPrefs.scheduleSurveyReminder(
        'a,b',
        const Duration(minutes: 30),
      );

      expect(planetPrefs.isReminderScheduled('a,b'), isTrue);
      // A different set is a different reminder, which is why the ids are the
      // key suffix rather than a single "snoozed" flag.
      expect(planetPrefs.isReminderScheduled('a'), isFalse);
    });

    test('a reminder comes due only once its time passes', () async {
      final planetPrefs = await prefs();
      await planetPrefs.scheduleSurveyReminder(
        'a,b',
        const Duration(minutes: 30),
      );
      final now = DateTime.now().millisecondsSinceEpoch;

      expect(await planetPrefs.takeDueSurveyReminders(now), isEmpty);

      final afterwards = now + const Duration(minutes: 31).inMilliseconds;
      expect(await planetPrefs.takeDueSurveyReminders(afterwards), ['a,b']);
    });

    test('taking a due reminder clears it, so it fires once', () async {
      final planetPrefs = await prefs();
      await planetPrefs.scheduleSurveyReminder('a,b', Duration.zero);
      final now = DateTime.now().millisecondsSinceEpoch + 1;

      expect(await planetPrefs.takeDueSurveyReminders(now), ['a,b']);
      expect(await planetPrefs.takeDueSurveyReminders(now), isEmpty);
      expect(planetPrefs.isReminderScheduled('a,b'), isFalse);
    });

    test('unrelated preferences are not mistaken for reminders', () async {
      SharedPreferences.setMockInitialValues({
        'themeMode': 'dark',
        'LastSync': 42,
      });
      final planetPrefs = PlanetPrefs(
        await SharedPreferences.getInstance(),
        secureStorage: _MockSecureStorage(),
      );

      expect(
        await planetPrefs.takeDueSurveyReminders(
          DateTime.now().millisecondsSinceEpoch,
        ),
        isEmpty,
      );
      expect(planetPrefs.themeModeName, 'dark');
    });
  });

  test('the language override defaults to unset and can be cleared', () async {
    SharedPreferences.setMockInitialValues({});
    final sharedPreferences = await SharedPreferences.getInstance();
    final prefs = PlanetPrefs(
      sharedPreferences,
      secureStorage: _MockSecureStorage(),
    );

    expect(prefs.languageCode, null);

    await prefs.setLanguageCode('es');
    expect(prefs.languageCode, 'es');
    // The key matches the Kotlin's, which `LocaleUtils` reads.
    expect(sharedPreferences.getString('language'), 'es');

    await prefs.setLanguageCode(null);
    expect(prefs.languageCode, null);
    expect(sharedPreferences.containsKey('language'), isFalse);
  });

  test(
    'background work preferences default to Kotlin policy and persist',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = PlanetPrefs(
        await SharedPreferences.getInstance(),
        secureStorage: _MockSecureStorage(),
      );

      expect(prefs.autoSyncEnabled, isTrue);
      expect(prefs.autoSyncInterval, const Duration(hours: 1));

      await prefs.setAutoSyncEnabled(false);
      await prefs.setAutoSyncInterval(const Duration(minutes: 30));

      expect(prefs.autoSyncEnabled, isFalse);
      expect(prefs.autoSyncInterval, const Duration(minutes: 30));
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
