import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/background/background_scheduler.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
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
      find.text('Every hour'),
      300,
      scrollable: _settingsScrollable(),
    );
    expect(find.text('Every hour'), findsOneWidget);

    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    expect(prefs.autoSyncEnabled, isFalse);
    expect(scheduler.cancelled, ['myplanet.autoSync']);

    // The ListView recycles the SwitchListTile off-tree once it scrolls far
    // enough. Scroll back up, then down to it again before the second tap.
    await tester.drag(_settingsScrollable(), const Offset(0, 1000));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byType(SwitchListTile),
      300,
      scrollable: _settingsScrollable(),
    );
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
      find.text('Every hour'),
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

  testWidgets('renders the app version and build number from package_info', (
    tester,
  ) async {
    final prefs = await _prefs();
    const version = (version: '0.62.98', buildNumber: '6298');
    await tester.pumpWidget(
      wrapScreen(
        const SettingsScreen(),
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          appVersionInfoProvider.overrideWith((ref) async => version),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.byIcon(Icons.new_releases_outlined),
      300,
      scrollable: _settingsScrollable(),
    );
    // The version line tracks the running app, not a hardcoded constant.
    expect(find.text('Version 0.62.98'), findsOneWidget);
    expect(find.text('Build 6298'), findsOneWidget);
  });

  /// `SettingsActivity.SettingFragment` gates the destructive and the storage
  /// actions on `user?.id?.startsWith("guest") == true` and offers
  /// `DialogUtils.guestDialog` instead (`SettingsActivity.kt:221`, `:249`,
  /// `:286`). Neither gate had a test, and both read
  /// `ref.read(sessionProvider).valueOrNull` on a screen that never watches
  /// `sessionProvider` — so the session resolved to `null`, the gate fell
  /// through, and a guest reached the only destructive action in the app.
  group('guest gates', () {
    testWidgets('a guest tapping Reset app is offered membership', (
      tester,
    ) async {
      final prefs = await _prefs();
      await tester.pumpWidget(
        wrapScreen(
          const SettingsScreen(),
          overrides: [
            planetPrefsProvider.overrideWithValue(prefs),
            sessionProvider.overrideWith(
              () => _TestSessionNotifier(_guestRow()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.scrollUntilVisible(
        find.text('Reset app'),
        300,
        scrollable: _settingsScrollable(),
      );
      await tester.tap(find.text('Reset app'));
      await tester.pumpAndSettle();

      expect(
        find.text(
          'You are currently a guest user. To access this feature, become a member.',
        ),
        findsOneWidget,
      );
      expect(find.text('Are you sure?'), findsNothing);
    });

    testWidgets('a guest tapping Storage Management is offered membership', (
      tester,
    ) async {
      final prefs = await _prefs();
      await tester.pumpWidget(
        wrapScreen(
          const SettingsScreen(),
          overrides: [
            planetPrefsProvider.overrideWithValue(prefs),
            sessionProvider.overrideWith(
              () => _TestSessionNotifier(_guestRow()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.scrollUntilVisible(
        find.text('Storage Management'),
        300,
        scrollable: _settingsScrollable(),
      );
      await tester.tap(find.text('Storage Management'));
      await tester.pumpAndSettle();

      expect(
        find.text(
          'You are currently a guest user. To access this feature, become a member.',
        ),
        findsOneWidget,
      );
    });

    /// The other half of the gate: a member still reaches the confirmation.
    testWidgets('a member tapping Reset app gets the confirmation', (
      tester,
    ) async {
      final prefs = await _prefs();
      await tester.pumpWidget(
        wrapScreen(
          const SettingsScreen(),
          overrides: [
            planetPrefsProvider.overrideWithValue(prefs),
            sessionProvider.overrideWith(
              () => _TestSessionNotifier(_memberRow()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.scrollUntilVisible(
        find.text('Reset app'),
        300,
        scrollable: _settingsScrollable(),
      );
      await tester.tap(find.text('Reset app'));
      await tester.pumpAndSettle();

      expect(find.text('Are you sure?'), findsOneWidget);
    });
  });
}

/// The row `createGuestUser('ada')` produces: `buildGuestUserJson`
/// (`UserRepositoryImpl.kt:146-153`) keys the document `guest_ada`, and
/// `applyJsonToUser` writes that to both the row key and the `_id` column.
UserRow _guestRow() => UserRow(
  id: 'guest_ada',
  couchId: 'guest_ada',
  name: 'ada',
  rolesList: const ['guest'],
  userAdmin: false,
  joinDate: 0,
  isArchived: false,
  isUpdated: false,
);

/// A member registered on this device whose upload has landed.
UserRow _memberRow() => UserRow(
  id: '1699999999999',
  couchId: 'org.couchdb.user:ada',
  name: 'ada',
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  isArchived: false,
  isUpdated: false,
);

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
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
