import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/background/background_scheduler.dart';
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

  testWidgets('shows storage management link', (tester) async {
    final prefs = await _prefs();
    await tester.pumpWidget(
      wrapScreen(
        const SettingsScreen(),
        overrides: [planetPrefsProvider.overrideWithValue(prefs)],
      ),
    );

    await tester.scrollUntilVisible(
      find.text('Storage Management'),
      300,
      scrollable: _settingsScrollable(),
    );
    expect(find.text('Storage Management'), findsOneWidget);
  });

  testWidgets('updates auto-sync policy and reschedules Android work', (
    tester,
  ) async {
    final prefs = await _prefs();
    final scheduler = _RecordingScheduler();
    await tester.pumpWidget(
      wrapScreen(
        const SettingsScreen(),
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          backgroundSchedulerProvider.overrideWithValue(scheduler),
        ],
      ),
    );

    await tester.scrollUntilVisible(
      find.byType(SwitchListTile),
      300,
      scrollable: _settingsScrollable(),
    );
    expect(find.text('Every hour'), findsOneWidget);

    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    expect(prefs.autoSyncEnabled, isFalse);
    expect(scheduler.cancelled, ['myplanet.autoSync']);

    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    expect(prefs.autoSyncEnabled, isTrue);
    expect(scheduler.scheduled.last.frequency, const Duration(hours: 1));
  });

  testWidgets('shows sanitized background retry diagnostics', (tester) async {
    final prefs = await _prefs();
    await prefs.recordBackgroundRun(
      taskName: 'myplanet.autoSync',
      attemptedAt: DateTime.utc(2026, 8, 16, 13, 45),
      status: 'retryRequested',
      failedSteps: const ['resources', 'outboxDrain'],
    );
    await tester.pumpWidget(
      wrapScreen(
        const SettingsScreen(),
        overrides: [planetPrefsProvider.overrideWithValue(prefs)],
      ),
    );

    await tester.scrollUntilVisible(
      find.text('Last background run'),
      300,
      scrollable: _settingsScrollable(),
    );
    expect(find.textContaining('Android will retry'), findsOneWidget);
    expect(find.textContaining('resources, outboxDrain'), findsOneWidget);
  });

  testWidgets('warns when Android rejects an immediate schedule update', (
    tester,
  ) async {
    final prefs = await _prefs();
    final scheduler = _RecordingScheduler()..throwOnCancel = true;
    await tester.pumpWidget(
      wrapScreen(
        const SettingsScreen(),
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          backgroundSchedulerProvider.overrideWithValue(scheduler),
        ],
      ),
    );
    await tester.scrollUntilVisible(
      find.byType(SwitchListTile),
      300,
      scrollable: _settingsScrollable(),
    );

    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();

    expect(
      prefs.autoSyncEnabled,
      isFalse,
      reason: 'the user choice is durable',
    );
    expect(find.textContaining('preference was saved'), findsOneWidget);
  });
}

Finder _settingsScrollable() => find
    .descendant(of: find.byType(ListView), matching: find.byType(Scrollable))
    .first;

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

class _RecordingScheduler implements BackgroundScheduler {
  bool throwOnCancel = false;
  final cancelled = <String>[];
  final scheduled = <({String name, Duration frequency})>[];

  @override
  Future<void> cancel(String uniqueName) async {
    if (throwOnCancel) throw StateError('WorkManager unavailable');
    cancelled.add(uniqueName);
  }

  @override
  Future<void> initialize() async {}

  @override
  Future<void> scheduleOneOff({
    required String uniqueName,
    required String taskName,
    required bool requiresNetwork,
  }) async {}

  @override
  Future<void> schedulePeriodic({
    required String uniqueName,
    required String taskName,
    required Duration frequency,
    required bool requiresNetwork,
  }) async {
    scheduled.add((name: uniqueName, frequency: frequency));
  }
}
