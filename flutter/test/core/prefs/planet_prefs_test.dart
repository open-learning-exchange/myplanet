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
}
