import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/planet_servers.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/settings_provider.dart';
import 'package:myplanet/repository/configurations_repository.dart';
import 'package:myplanet/ui/router.dart';
import 'package:myplanet/ui/sync/login_screen.dart';
import 'package:myplanet/ui/sync/server_config_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The reachability guard for the state this port could not previously enter:
/// **a configured device on the server-configuration screen**.
///
/// Every other test in this area builds its screen directly, which is exactly
/// how three correct, green implementations came to be dead. `ServerConfigScreen`
/// prefills from the persisted configuration, `planetServersToShow` prepends
/// and hoists the configured row, and the row tap can warn before a switch —
/// and not one of them could run, because the only way to reach the screen was
/// the login screen's "change server", which called
/// `serverConfigProvider.clear()` so the router's redirect would fire. The
/// fact each of those three needs was destroyed by the navigation that reached
/// them.
///
/// So these tests drive the **real router**, from a tap on the real login
/// screen, and assert on what the configured device then sees.
class _MockSecureStorage extends Mock implements FlutterSecureStorage {}

/// Holds a configuration and records what is adopted over it.
class _RecordingServerConfig extends ServerConfigNotifier {
  _RecordingServerConfig(this._initial, this.saved);

  final ServerConfig? _initial;
  final List<ServerConfig> saved;
  int clears = 0;

  @override
  ServerConfig? build() => _initial;

  @override
  Future<void> save(ServerConfig config) async {
    saved.add(config);
    state = config;
  }

  @override
  Future<void> clear() async {
    clears++;
    state = null;
  }
}

class _StubConfigurations implements ConfigurationsRepository {
  _StubConfigurations(this.result, {this.hang});

  final ConfigurationResult result;

  /// When supplied, the handshake never answers until this completes — which
  /// is the only way to hold `_isChecking` true long enough to press anything
  /// else.
  final Completer<void>? hang;
  int calls = 0;

  @override
  Future<ConfigurationResult> getMinApk(String url, String pin) async {
    calls++;
    await hang?.future;
    return result;
  }

  @override
  noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// A preferences object whose `/versions` cache write fails.
///
/// It is a different storage backend from the one `saveServerConfig` has just
/// succeeded against, so it can fail on its own — which is the point.
class _FailingVersionPrefs extends PlanetPrefs {
  _FailingVersionPrefs(super.prefs, {required super.secureStorage});

  @override
  Future<void> setVersionDetail(String json) async =>
      throw StateError('preferences full');
}

class _StubClearData extends ClearDataNotifier {
  int calls = 0;

  @override
  Future<void> build() async {}

  @override
  Future<void> clearForServerSwitch() async {
    calls++;
    await ref.read(serverConfigProvider.notifier).clear();
  }
}

const _delegates = <LocalizationsDelegate<Object?>>[
  AppLocalizations.delegate,
  GlobalMaterialLocalizations.delegate,
  GlobalWidgetsLocalizations.delegate,
  GlobalCupertinoLocalizations.delegate,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const learning = PlanetServer(
    name: '🌎 planet learning',
    host: 'learning.example.org',
    pin: '1234',
  );
  const second = PlanetServer(
    name: '🇬🇹 planet guatemala',
    host: 'guatemala.example.org',
    pin: '5678',
  );
  const third = PlanetServer(
    name: '🌎 planet earth',
    host: 'earth.example.org',
    pin: '9012',
  );
  // Declared eleventh, like cambridge: the row a collapsed list cannot show
  // and an expanded one used to show last.
  const eleventh = PlanetServer(
    name: '🇺🇸 planet cambridge',
    host: 'cambridge.example.org',
    pin: '3456',
  );

  /// The configuration on the device, pointing at the row declared last.
  const configured = ServerConfig(
    serverUrl: 'https://cambridge.example.org',
    pin: '3456',
    couchDbUrl: 'https://satellite:3456@cambridge.example.org:443',
    code: 'cambridge',
  );

  late AppDatabase db;
  late PlanetPrefs prefs;
  late List<ServerConfig> saved;
  late _StubClearData clearData;
  late _MockSecureStorage secureStorage;
  late _StubConfigurations configurations;

  setUp(() async {
    SharedPreferences.setMockInitialValues({'onboardingComplete': true});
    db = AppDatabase.memory();
    saved = <ServerConfig>[];
    clearData = _StubClearData();
    secureStorage = _MockSecureStorage();
    when(
      () => secureStorage.read(key: any(named: 'key')),
    ).thenAnswer((_) async => null);
    when(
      () => secureStorage.write(
        key: any(named: 'key'),
        value: any(named: 'value'),
      ),
    ).thenAnswer((_) async {});
    when(
      () => secureStorage.delete(key: any(named: 'key')),
    ).thenAnswer((_) async {});
    prefs = PlanetPrefs(
      await SharedPreferences.getInstance(),
      secureStorage: secureStorage,
    );
  });
  tearDown(() => db.close());

  ProviderContainer container({
    ServerConfig? existing = configured,
    List<PlanetServer> servers = const [learning, second, third, eleventh],
    bool holdsServerData = true,
    Set<String> localPlanetCodes = const {'cambridge'},
    ConfigurationResult? configuration,
    PlanetPrefs? planetPrefs,
    Completer<void>? hang,
  }) {
    final c = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        planetPrefsProvider.overrideWithValue(planetPrefs ?? prefs),
        appDatabaseProvider.overrideWithValue(db),
        planetServersProvider.overrideWithValue(servers),
        serverConfigProvider.overrideWith(
          () => _RecordingServerConfig(existing, saved),
        ),
        deviceHoldsServerDataProvider.overrideWithValue(holdsServerData),
        localPlanetCodesProvider.overrideWith((ref) async => localPlanetCodes),
        if (configuration != null)
          configurationsRepositoryProvider.overrideWithValue(
            configurations = _StubConfigurations(configuration, hang: hang),
          ),
        clearDataProvider.overrideWith(() => clearData),
      ],
    );
    addTearDown(c.dispose);
    return c;
  }

  /// Pumps the real router at [at].
  ///
  /// `initialLocation` is `/home`, and a signed-out device only leaves it once
  /// the session resolves — so going to `/login` first keeps the dashboard
  /// shell and its providers out of a test about two screens.
  Future<GoRouter> pumpAt(
    WidgetTester tester,
    ProviderContainer c, {
    String at = Routes.login,
  }) async {
    final router = c.read(routerProvider);
    router.go(at);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: c,
        child: MaterialApp.router(
          routerConfig: router,
          localizationsDelegates: _delegates,
          supportedLocales: AppLocalizations.supportedLocales,
        ),
      ),
    );
    await tester.pumpAndSettle();
    return router;
  }

  testWidgets('change server reaches the screen with the config intact', (
    tester,
  ) async {
    final c = container();
    final router = await pumpAt(tester, c);
    expect(find.byType(LoginScreen), findsOneWidget);

    await tester.tap(find.text('Change server'));
    await tester.pumpAndSettle();

    expect(find.byType(ServerConfigScreen), findsOneWidget);
    expect(router.state.uri.toString(), Routes.changeServer);
    // The whole point: the configuration is still there.
    expect(c.read(serverConfigProvider), configured);
    expect(
      (c.read(serverConfigProvider.notifier) as _RecordingServerConfig).clears,
      0,
    );
  });

  testWidgets('the reached screen is prefilled from the configuration', (
    tester,
  ) async {
    // Dead path 2. `initState`'s prefill branch requires a persisted config on
    // a screen the router would previously never show to a configured device.
    final c = container();
    await pumpAt(tester, c);
    await tester.tap(find.text('Change server'));
    await tester.pumpAndSettle();

    expect(
      tester
          .widgetList<TextField>(find.byType(TextField))
          .map((f) => f.controller?.text)
          .toList(),
      <String>['https://cambridge.example.org', '3456'],
    );
  });

  testWidgets('the configured row is hoisted into the collapsed list', (
    tester,
  ) async {
    // Dead path 1. `planetServersToShow`'s prepend had exactly one caller and
    // it passed a host that was null on every reachable path, so the four-row
    // collapsed list could never appear.
    final c = container();
    await pumpAt(tester, c);
    await tester.tap(find.text('Change server'));
    await tester.pumpAndSettle();

    expect(find.text('🇺🇸 planet cambridge'), findsOneWidget);
    expect(find.text('🌎 planet earth'), findsOneWidget);
    expect(
      tester
          .widgetList<ListTile>(find.byType(ListTile))
          .map((tile) => (tile.title! as Text).data)
          .toList(),
      <String>[
        '🇺🇸 planet cambridge',
        '🌎 planet learning',
        '🇬🇹 planet guatemala',
        '🌎 planet earth',
      ],
    );
  });

  testWidgets('closing returns to login without touching anything', (
    tester,
  ) async {
    // The exemption grants nothing that outlives the location, and the way out
    // is not "adopt a server": the close action drops the marker and the
    // redirect puts a configured, signed-out device back on `/login`.
    final c = container();
    final router = await pumpAt(tester, c);
    await tester.tap(find.text('Change server'));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.close));
    await tester.pumpAndSettle();

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(router.state.uri.toString(), Routes.login);
    expect(c.read(serverConfigProvider), configured);
  });

  testWidgets('the system back gesture closes rather than leaving the app', (
    tester,
  ) async {
    // `go` leaves no route underneath, so an un-intercepted pop drops the user
    // out of the app instead of back to the login screen.
    final c = container();
    final router = await pumpAt(tester, c);
    await tester.tap(find.text('Change server'));
    await tester.pumpAndSettle();

    await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
      'flutter/navigation',
      const JSONMethodCodec().encodeMethodCall(const MethodCall('popRoute')),
      (_) {},
    );
    await tester.pumpAndSettle();

    expect(router.state.uri.toString(), Routes.login);
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  testWidgets('a first configuration offers no way out of the screen', (
    tester,
  ) async {
    // There is nothing behind it: the redirect put the device here because it
    // has no server, and it would put it straight back.
    final c = container(existing: null, holdsServerData: false);
    await pumpAt(tester, c);

    expect(find.byIcon(Icons.close), findsNothing);
  });

  /// Types a URL and PIN rather than tapping a row.
  ///
  /// A row tap on a configured device raises the **early** warning (the port of
  /// `ServerAddressAdapter`'s warn arm), which is right and is covered below —
  /// but it is a different gate from the one at Connect, and a test that wants
  /// the Connect gate has to reach it the way Kotlin's manual mode does.
  Future<void> typeServer(
    WidgetTester tester, {
    required String url,
    required String pin,
  }) async {
    await tester.enterText(find.byType(TextFormField).first, url);
    await tester.enterText(find.byType(TextFormField).at(1), pin);
    await tester.pump();
  }

  Future<void> pumpHandshake(WidgetTester tester, {int frames = 12}) async {
    // Never `pumpAndSettle` here: while `_isChecking` is true the Connect
    // button holds an indefinite `CircularProgressIndicator`.
    for (var i = 0; i < frames; i++) {
      await tester.pump(const Duration(milliseconds: 120));
    }
  }

  testWidgets('adopting a configuration spends the marker and returns', (
    tester,
  ) async {
    // The redirect holds `/server?change=1` on purpose, so nothing would ever
    // navigate away from a screen that had done its job. The screen drops the
    // marker; the redirect then places the device, which is `/login` for a
    // configured, signed-out one.
    const adopted = ServerConfig(
      serverUrl: 'https://learning.example.org',
      pin: '1234',
      couchDbUrl: 'https://satellite:1234@learning.example.org:443',
      // The same community — a clone URL — so no wipe is offered and this test
      // is about the navigation alone.
      code: 'cambridge',
    );
    final c = container(configuration: const ConfigurationSuccess(adopted));
    final router = await pumpAt(tester, c);
    await tester.tap(find.text('Change server'));
    await tester.pumpAndSettle();

    await typeServer(tester, url: 'https://learning.example.org', pin: '1234');
    await tester.tap(find.widgetWithText(FilledButton, 'Connect'));
    await pumpHandshake(tester);

    expect(saved, <ServerConfig>[adopted]);
    expect(router.state.uri.toString(), Routes.login);
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  testWidgets('a wipe at Connect finishes the switch it interrupted', (
    tester,
  ) async {
    // The Connect gate's own wipe, end to end: stop, clear, then adopt.
    //
    // Measured, and worth knowing: reverting the navigation to `push` does
    // *not* fail this test — it fails the row-tap one below. The two differ by
    // one line. Here `save(config)` runs immediately after the wipe and puts
    // `hasServer` back before the router processes the refresh, so the
    // `!hasServer` branch never sees a pushed base to redirect; on the row tap
    // there is no save to restore it. This path survived on ordering rather
    // than on anything guaranteed, which is not a property to build on.
    const adopted = ServerConfig(
      serverUrl: 'https://guatemala.example.org',
      pin: '5678',
      couchDbUrl: 'https://satellite:5678@guatemala.example.org:443',
      code: 'guatemala',
    );
    final c = container(configuration: const ConfigurationSuccess(adopted));
    final router = await pumpAt(tester, c);
    await tester.tap(find.text('Change server'));
    await tester.pumpAndSettle();

    await typeServer(tester, url: 'https://guatemala.example.org', pin: '5678');
    await tester.tap(find.widgetWithText(FilledButton, 'Connect'));
    await pumpHandshake(tester);

    // Another community, so the switch stops for the wipe.
    expect(find.text('Clear data'), findsOneWidget);
    expect(find.byType(ServerConfigScreen), findsOneWidget);

    await tester.tap(find.text('Clear data'));
    await pumpHandshake(tester, frames: 20);

    expect(clearData.calls, 1);
    expect(saved, <ServerConfig>[adopted]);
    expect(router.state.uri.toString(), Routes.login);
  });

  testWidgets('a wipe at the row tap keeps the screen and the tap', (
    tester,
  ) async {
    // **The defect this change's own second audit pass found**, and the reason
    // the screen is reached with `go` rather than `push`.
    //
    // The wipe clears the configuration, which refreshes the router — and a
    // pushed route's own location is not what `redirect` sees on a refresh.
    // go_router re-parses the push's **base**, `/login`, which carries no
    // marker, so the `!hasServer` branch sent it to `/server`: the pushed
    // route collapsed, the screen was rebuilt with a fresh `State`, and the
    // fields the tap was about to fill were cleared instead. Confirm a wipe,
    // get a blank form, with nothing said. Every test was green, because a
    // pushed route only refreshes when some provider it does not own moves.
    final c = container();
    await pumpAt(tester, c);
    await tester.tap(find.text('Change server'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('🌎 planet learning'));
    await tester.pumpAndSettle();
    expect(find.text('Clear data'), findsOneWidget);

    await tester.tap(find.text('Clear data'));
    await pumpHandshake(tester, frames: 20);

    expect(clearData.calls, 1);
    expect(find.byType(ServerConfigScreen), findsOneWidget);
    expect(
      tester
          .widgetList<TextField>(find.byType(TextField))
          .map((f) => f.controller?.text)
          .toList(),
      <String>['https://learning.example.org', '1234'],
    );
  });

  testWidgets('closing after a wipe stays here rather than bouncing', (
    tester,
  ) async {
    // Closing is not "go to the login screen": after a wipe there is no
    // configuration and no data, so a login screen would have nothing to sign
    // into. [_spendMarker] drops the marker and the redirect decides, which
    // here means staying put.
    //
    // What this does *not* pin is `_spendMarker`'s argument: the redirect
    // normalises `/login` and `/home` to the same outcomes, so swapping them
    // in keeps the suite green. It pins that closing navigates at all and that
    // the redirect places the result.
    final c = container();
    await pumpAt(tester, c);
    await tester.tap(find.text('Change server'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('🌎 planet learning'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Clear data'));
    await pumpHandshake(tester, frames: 20);

    await tester.tap(find.byIcon(Icons.close));
    await tester.pumpAndSettle();

    expect(find.byType(ServerConfigScreen), findsOneWidget);
    expect(find.byType(LoginScreen), findsNothing);
  });

  testWidgets('a failed version cache does not report the switch as failed', (
    tester,
  ) async {
    // Both writes used to share one `try`. `saveServerConfig` goes to secure
    // storage and the `/versions` cache to SharedPreferences, so the second can
    // fail on its own — and when it did, the user was told the operation had
    // failed over a configuration that had in fact been adopted, and was left
    // on a screen whose error contradicted its own state.
    const adopted = ServerConfig(
      serverUrl: 'https://learning.example.org',
      pin: '1234',
      couchDbUrl: 'https://satellite:1234@learning.example.org:443',
      code: 'cambridge',
    );
    final c = container(
      configuration: const ConfigurationSuccess(adopted, versionDetail: '{}'),
      planetPrefs: _FailingVersionPrefs(
        await SharedPreferences.getInstance(),
        secureStorage: secureStorage,
      ),
    );
    final router = await pumpAt(tester, c);
    await tester.tap(find.text('Change server'));
    await tester.pumpAndSettle();

    await typeServer(tester, url: 'https://learning.example.org', pin: '1234');
    await tester.tap(find.widgetWithText(FilledButton, 'Connect'));
    await pumpHandshake(tester);

    expect(saved, <ServerConfig>[adopted]);
    expect(router.state.uri.toString(), Routes.login);
    expect(find.text('Operation failed'), findsNothing);
  });

  testWidgets(
    'the keyboard cannot start a second handshake over a running one',
    (tester) async {
      // The PIN field's `onFieldSubmitted` had no `_isChecking` guard where the
      // Connect button's `onPressed` does, so the keyboard's "done" launched a
      // second handshake over the first.
      const adopted = ServerConfig(
        serverUrl: 'https://learning.example.org',
        pin: '1234',
        couchDbUrl: 'https://satellite:1234@learning.example.org:443',
        code: 'cambridge',
      );
      final hang = Completer<void>();
      final c = container(
        configuration: const ConfigurationSuccess(adopted),
        hang: hang,
      );
      await pumpAt(tester, c);
      await tester.tap(find.text('Change server'));
      await tester.pumpAndSettle();

      await typeServer(
        tester,
        url: 'https://learning.example.org',
        pin: '1234',
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Connect'));
      // Never `pumpAndSettle`: the button is holding an indefinite spinner.
      await tester.pump();
      expect(configurations.calls, 1);

      await tester.showKeyboard(find.byType(TextFormField).at(1));
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pump();

      expect(configurations.calls, 1);

      hang.complete();
      await pumpHandshake(tester);
    },
  );

  testWidgets('a bare /server is still refused to a configured device', (
    tester,
  ) async {
    // The exemption is the marked location and nothing wider. Without this the
    // redirect would have a hole a stale bookmark or a mistyped `context.go`
    // could walk through.
    final c = container();
    final router = await pumpAt(tester, c, at: Routes.server);

    expect(router.state.uri.toString(), Routes.login);
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  testWidgets('a first configuration never sees the marker', (tester) async {
    // The unconfigured path is unchanged: the redirect puts the device on
    // `/server`, `changingServer` is false, and the screen leaves the
    // navigation to the redirect as it always did.
    final c = container(existing: null, holdsServerData: false);
    final router = await pumpAt(tester, c);

    expect(router.state.uri.toString(), Routes.server);
    expect(
      tester
          .widget<ServerConfigScreen>(find.byType(ServerConfigScreen))
          .changingServer,
      isFalse,
    );
  });

  test('no screen reaches a route by clearing the server configuration', () {
    // The defect this file exists for, pinned on the source text — because it
    // is the one navigation that leaves *no* navigation behind. The scanner in
    // `route_reachability_test.dart` reads `context.go`/`push` call sites and
    // `Routes` constants; a screen that instead mutates the state `redirect`
    // reads is invisible to every rule there, and to every screen test, and
    // the route still reports as reachable.
    //
    // `ServerConfigNotifier.clear()` has one honest caller — the wipe in
    // `ClearDataNotifier`, which clears it because the configuration should
    // genuinely be gone. A call from a screen is a redirect being fired by
    // destroying the fact the redirect is about.
    final offenders = <String>[];
    for (final file
        in Directory('lib/ui')
            .listSync(recursive: true)
            .whereType<File>()
            .where((f) => f.path.endsWith('.dart'))) {
      final source = file.readAsStringSync();
      if (source.contains('serverConfigProvider.notifier).clear()')) {
        offenders.add(file.path);
      }
    }

    expect(
      offenders,
      isEmpty,
      reason:
          'These screens clear the persisted server configuration. If that is '
          'to make the router navigate, it is the Phase 156 defect: it throws '
          'away which Planet the on-device database belongs to and leaves the '
          'database full. Navigate to Routes.changeServer instead:\n'
          '${offenders.map((f) => '  $f').join('\n')}',
    );
  });

  test('the change-server location is the server route plus its marker', () {
    // Two literals have to agree: the constant the login screen navigates to,
    // and the flag the redirect and the route builder read off the URI.
    expect(Routes.changeServer.startsWith('${Routes.server}?'), isTrue);
    expect(serverChangeRequested(Uri.parse(Routes.changeServer)), isTrue);
    expect(serverChangeRequested(Uri.parse(Routes.server)), isFalse);
    expect(
      serverChangeRequested(Uri.parse('${Routes.server}?change=0')),
      isFalse,
    );
  });
}
