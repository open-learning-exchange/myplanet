import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/ui/settings/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/widget_harness.dart';

class _MockSecureStorage extends Mock implements FlutterSecureStorage {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('shows server identity without exposing credentials', (
    tester,
  ) async {
    final prefs = await _prefs();
    const server = ServerConfig(
      serverUrl: 'https://planet.example.org',
      pin: 'secret-pin',
      couchDbUrl: 'https://satellite:secret-pin@planet.example.org',
      id: 'config-1',
      code: 'community-a',
      parentCode: 'nation',
    );

    await tester.pumpWidget(
      wrapScreen(
        const SettingsScreen(),
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          serverConfigProvider.overrideWith(() => _TestServerConfig(server)),
        ],
      ),
    );

    expect(find.text('Appearance'), findsOneWidget);
    expect(find.text('https://planet.example.org'), findsOneWidget);
    expect(find.text('community-a'), findsOneWidget);
    expect(find.textContaining('secret-pin'), findsNothing);
  });

  testWidgets('persists theme selection', (tester) async {
    final prefs = await _prefs();
    await tester.pumpWidget(
      wrapScreen(
        const SettingsScreen(),
        overrides: [planetPrefsProvider.overrideWithValue(prefs)],
      ),
    );

    expect(find.text('Use device theme'), findsOneWidget);
    expect(find.text('Light'), findsOneWidget);
    expect(find.text('Dark'), findsOneWidget);

    await tester.tap(find.text('Dark'));
    await tester.pump();

    expect(prefs.themeModeName, ThemeMode.dark.name);
  });
}

Future<PlanetPrefs> _prefs() async {
  SharedPreferences.setMockInitialValues({});
  return PlanetPrefs(
    await SharedPreferences.getInstance(),
    secureStorage: _MockSecureStorage(),
  );
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);

  final ServerConfig config;

  @override
  ServerConfig? build() => config;
}
