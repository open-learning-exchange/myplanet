import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/planet_servers.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/settings_provider.dart';
import 'package:myplanet/repository/configurations_repository.dart';
import 'package:myplanet/ui/sync/server_config_screen.dart';

import '../support/widget_harness.dart';

/// The screen had no tests at all, and the gap they would have caught is the
/// reason this file exists: the port shipped two empty text boxes where the
/// Kotlin offers a tappable server list that fills the PIN, so the only way to
/// connect the port was to already know a four-character PIN out of
/// `gradle.properties`. Nothing failed — there was simply no way in.
/// Returns a fixed handshake result. The real one needs a server; what the
/// screen has to get right is what it does with a *successful* one on a device
/// that already holds another community's data.
class _StubConfigurationsRepository implements ConfigurationsRepository {
  _StubConfigurationsRepository(this.result);

  final ConfigurationResult result;

  @override
  Future<ConfigurationResult> getMinApk(String url, String pin) async => result;

  @override
  noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Records what was adopted, so a test can assert the switch did *not* happen.
///
/// [initial] is the configuration the device is already on. It is the same
/// class for both cases on purpose: the inherited `save` writes through
/// `planetPrefsProvider`, which throws in the screen harness, so a stub that
/// only overrode `build` turned any Connect on a configured device into
/// "Operation failed" — and a configured device reaching this screen is now
/// the ordinary case rather than the impossible one.
class _RecordingServerConfigNotifier extends ServerConfigNotifier {
  _RecordingServerConfigNotifier(
    this._saved, {
    this.initial,
    this.fails = false,
  });

  final List<ServerConfig> _saved;
  final ServerConfig? initial;
  final bool fails;

  @override
  ServerConfig? build() => initial;

  @override
  Future<void> save(ServerConfig config) async {
    if (fails) throw StateError('keystore down');
    _saved.add(config);
    state = config;
  }
}

/// Stands in for the real wipe. `_wipe` would need a database, preferences and
/// a session; what the screen has to get right is *whether* it runs the wipe
/// before filling the fields, and that is what this records.
class _StubClearDataNotifier extends ClearDataNotifier {
  _StubClearDataNotifier({this.fails = false});

  final bool fails;
  int calls = 0;

  @override
  Future<void> build() async {}

  @override
  Future<void> clearForServerSwitch() async {
    calls++;
    if (fails) throw StateError('wipe failed');
  }
}

void main() {
  const learning = PlanetServer(
    name: '🌎 planet learning',
    host: 'planet.learning.example.org',
    pin: '1234',
  );
  // A real LAN address, because the scheme is now computed from the host:
  // six of the eleven shipping servers are private addresses and two of them
  // reach http only through `isLocalNetwork`. A synthetic public hostname
  // would assert http while resolving https.
  const local = PlanetServer(
    name: '🇬🇹 planet san pablo',
    host: '192.168.48.253',
    pin: '5678',
  );
  const belowFold = PlanetServer(
    name: '🌎 planet below the fold',
    host: 'below.example.org',
    pin: '3456',
  );
  const fourth = PlanetServer(
    name: '🇸🇴 planet somalia',
    host: 'somalia.example.org',
    pin: '9012',
  );
  const fifth = PlanetServer(
    name: '🌎 planet vi',
    host: 'vi.example.org',
    pin: '0660',
  );

  /// Never `pumpAndSettle` after Connect: while `_isChecking` is true the
  /// button holds an indefinite `CircularProgressIndicator`, and
  /// `pumpAndSettle` spins on it to its ten-minute default — a failure that
  /// looks exactly like a hang. Fixed pumps instead, enough for the handshake,
  /// the `localPlanetCodesProvider` await and the dialog's transition.
  Future<void> pumpFrames(WidgetTester tester, {int frames = 8}) async {
    for (var i = 0; i < frames; i++) {
      await tester.pump(const Duration(milliseconds: 120));
    }
  }

  late List<ServerConfig> savedConfigs;
  setUp(() => savedConfigs = <ServerConfig>[]);

  Widget build({
    List<PlanetServer> servers = const [learning, local],
    ServerConfig? existing,
    // `deviceHoldsServerDataProvider` reads `planetPrefsProvider`, which
    // throws unless overridden, so every test declares this. `false` is the
    // fresh-install case.
    bool holdsServerData = false,

    /// Replaces the `holdsServerData` override wholesale, for the two tests
    /// that need the predicate to be slow or to throw. A plain
    /// `(ref) async => value` completes in one microtask, which is why no
    /// existing test can see the window the real database walk opens.
    Future<bool> Function()? holdsServerDataBody,
    Set<String> localPlanetCodes = const {},
    ConfigurationResult? configuration,
    _StubClearDataNotifier? clearData,
    bool saveFails = false,
  }) {
    return wrapScreen(
      const ServerConfigScreen(),
      overrides: [
        planetServersProvider.overrideWithValue(servers),
        serverConfigProvider.overrideWith(
          () => _RecordingServerConfigNotifier(
            savedConfigs,
            initial: existing,
            fails: saveFails,
          ),
        ),
        deviceHoldsServerDataProvider.overrideWith(
          (ref) async => holdsServerDataBody == null
              ? holdsServerData
              : await holdsServerDataBody(),
        ),
        localPlanetCodesProvider.overrideWith((ref) async => localPlanetCodes),
        if (configuration != null)
          configurationsRepositoryProvider.overrideWithValue(
            _StubConfigurationsRepository(configuration),
          ),
        clearDataProvider.overrideWith(
          () => clearData ?? _StubClearDataNotifier(),
        ),
      ],
    );
  }

  /// What a successful handshake against a *different* Planet returns. `code`
  /// is the community code the gate compares, the same value
  /// `UserRepository` writes into `users.planetCode` for a member it creates.
  const guatemalaConfig = ServerConfig(
    serverUrl: 'https://planet.gt',
    pin: '5562',
    couchDbUrl: 'https://satellite:5562@planet.gt:443',
    code: 'guatemala',
  );

  List<String?> fieldTexts(WidgetTester tester) => tester
      .widgetList<TextField>(find.byType(TextField))
      .map((f) => f.controller?.text)
      .toList();

  testWidgets('offers the configured servers by name', (tester) async {
    await tester.pumpWidget(build());
    await tester.pump();

    expect(find.text('🌎 planet learning'), findsOneWidget);
    expect(find.text('🇬🇹 planet san pablo'), findsOneWidget);
  });

  testWidgets('tapping a server fills the URL and the PIN', (tester) async {
    await tester.pumpWidget(build());
    await tester.pump();

    await tester.tap(find.text('🌎 planet learning'));
    await tester.pump();

    final fields = tester.widgetList<TextField>(find.byType(TextField));
    expect(fields.map((f) => f.controller?.text).toList(), <String>[
      'https://planet.learning.example.org',
      '1234',
    ]);
  });

  testWidgets('an http server is offered over http, not https', (tester) async {
    // Four of the eleven are plain http in `getDefaultProtocol`; offering one
    // of those as https fails the handshake against a server that is up.
    await tester.pumpWidget(build());
    await tester.pump();

    await tester.tap(find.text('🇬🇹 planet san pablo'));
    await tester.pump();

    final url = tester
        .widgetList<TextField>(find.byType(TextField))
        .first
        .controller
        ?.text;
    expect(url, 'http://192.168.48.253');
  });

  testWidgets('a build with no servers shows no picker at all', (tester) async {
    await tester.pumpWidget(build(servers: const []));
    await tester.pump();

    expect(find.byType(ListTile), findsNothing);
    // Manual entry still works, which is how the port behaved before the list.
    expect(find.byType(TextFormField), findsNWidgets(2));
  });

  testWidgets('collapses to three, and show more reveals the rest', (
    tester,
  ) async {
    await tester.pumpWidget(
      build(servers: const [learning, local, fourth, learning]),
    );
    await tester.pump();

    expect(find.byType(ListTile), findsNWidgets(3));

    await tester.tap(find.text('show more'));
    await tester.pump();

    expect(find.byType(ListTile), findsNWidgets(4));
    expect(find.text('show less'), findsOneWidget);
  });

  testWidgets('prefills from an existing configuration', (tester) async {
    await tester.pumpWidget(
      build(
        existing: const ServerConfig(
          serverUrl: 'https://planet.learning.example.org',
          pin: '1234',
          couchDbUrl: 'https://satellite:1234@planet.learning.example.org:443',
        ),
      ),
    );
    await tester.pump();

    final fields = tester.widgetList<TextField>(find.byType(TextField));
    expect(fields.map((f) => f.controller?.text).toList(), <String>[
      'https://planet.learning.example.org',
      '1234',
    ]);
  });

  testWidgets('keeps the configured server visible when it is below the fold', (
    tester,
  ) async {
    // Pinned by nothing until this existed: the pure function's prepend branch
    // was covered, but the screen's `configuredHost:` argument could have been
    // deleted without a single test noticing. Every other case here either has
    // three servers or a configured host already in the top three.
    await tester.pumpWidget(
      build(
        servers: const [learning, local, fourth, belowFold],
        existing: const ServerConfig(
          serverUrl: 'https://below.example.org',
          pin: '3456',
          couchDbUrl: 'https://satellite:3456@below.example.org:443',
        ),
      ),
    );
    await tester.pump();

    final titles = tester
        .widgetList<ListTile>(find.byType(ListTile))
        .map((t) => (t.title! as Text).data)
        .toList();
    expect(titles.first, '🌎 planet below the fold');
    expect(titles, hasLength(4));
  });

  group('the server-switch data wipe', () {
    // An earlier cut of this comment said `existing: <a config>` together
    // with `holdsServerData: true` was "a combination `router.dart` forbids,
    // since a device with a config is redirected away from `/server`", and
    // that the only way a configured device reached this screen was the login
    // screen's "change server", which cleared the config to make the redirect
    // fire. Both halves stopped being true when `Routes.changeServer` replaced
    // that trick: the marker holds the screen *with* the configuration intact,
    // which is the whole reason the row-tap gate below could be ported at all.
    // A configured device here is now the ordinary case, and the tests that
    // need one say so.

    testWidgets('a different community is not adopted without a wipe', (
      tester,
    ) async {
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          configuration: const ConfigurationSuccess(guatemalaConfig),
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(
        find.text(
          'Are you sure you want to change the configuration? This action '
          'will clear the app data.',
        ),
        findsOneWidget,
      );
      expect(clearData.calls, 0);
      expect(savedConfigs, isEmpty);
    });

    testWidgets('a typed URL is gated exactly like a tapped row', (
      tester,
    ) async {
      // Kotlin cannot be driven this way at all: in list mode both fields are
      // disabled and the submit button is `GONE`, and reaching a typed URL
      // means switching manual configuration on, which raises this same
      // dialog first. The port's form is always editable, so gating the row
      // tap alone would have left the whole defect reachable by typing.
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          configuration: const ConfigurationSuccess(guatemalaConfig),
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.enterText(find.byType(TextFormField).last, '5562');
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsOneWidget);
      expect(savedConfigs, isEmpty);
    });

    testWidgets('the same community is adopted with no dialog at all', (
      tester,
    ) async {
      // The case that makes the gate usable. Kotlin compares the tapped row
      // against the selected one; the port compares the community the local
      // data belongs to, so re-configuring the server you were already on —
      // the ordinary outcome of tapping "change server" by mistake, or of
      // re-entering a PIN — costs nothing. Comparing hosts instead would have
      // forced a wipe here, and on a clone URL of the same Planet.
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          holdsServerData: true,
          localPlanetCodes: const {'guatemala'},
          configuration: const ConfigurationSuccess(guatemalaConfig),
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsNothing);
      expect(clearData.calls, 0);
      expect(savedConfigs.map((c) => c.code), <String>['guatemala']);
    });

    testWidgets('a fresh device is never asked to clear anything', (
      tester,
    ) async {
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          configuration: const ConfigurationSuccess(guatemalaConfig),
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsNothing);
      expect(clearData.calls, 0);
      expect(savedConfigs, hasLength(1));
    });

    testWidgets('confirming wipes first, then adopts the new server', (
      tester,
    ) async {
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          configuration: const ConfigurationSuccess(guatemalaConfig),
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);
      await tester.tap(find.text('Clear data'));
      await pumpFrames(tester);

      expect(clearData.calls, 1);
      expect(savedConfigs.map((c) => c.code), <String>['guatemala']);
    });

    testWidgets('declining leaves the device on its old server', (
      tester,
    ) async {
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          configuration: const ConfigurationSuccess(guatemalaConfig),
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);
      await tester.tap(find.text('Cancel'));
      await pumpFrames(tester);

      expect(clearData.calls, 0);
      expect(savedConfigs, isEmpty);
      // And the button is usable again rather than spinning: `_isChecking` is
      // cleared on the decline, where the success path deliberately leaves it
      // set for the router's redirect.
      expect(find.text('Connect'), findsOneWidget);
    });

    testWidgets('a failed wipe keeps the dialog open with its buttons live', (
      tester,
    ) async {
      // Kotlin's `catch`: progress dismissed, back-press guard released, both
      // buttons re-enabled, dialog still up (`SyncActivity.kt:289-295`). And
      // the new server must not be adopted, which is what the rethrow in
      // `clearForServerSwitch` is for.
      final clearData = _StubClearDataNotifier(fails: true);
      await tester.pumpWidget(
        build(
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          configuration: const ConfigurationSuccess(guatemalaConfig),
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);
      await tester.tap(find.text('Clear data'));
      await pumpFrames(tester);

      expect(clearData.calls, 1);
      expect(find.text('Clear data'), findsOneWidget);
      expect(find.text('Operation failed'), findsOneWidget);
      final confirm = tester.widget<TextButton>(
        find.ancestor(
          of: find.text('Clear data'),
          matching: find.byType(TextButton),
        ),
      );
      expect(confirm.onPressed, isNotNull);
      expect(savedConfigs, isEmpty);
    });

    testWidgets('unattributable local data is treated as another community', (
      tester,
    ) async {
      // A device that has synced but whose users carry no `planetCode` cannot
      // be attributed, and on a synced device that is unexpected rather than
      // reassuring. Declining costs the user the switch; assuming a match
      // would risk the mixing.
      await tester.pumpWidget(
        build(
          holdsServerData: true,
          localPlanetCodes: const {},
          configuration: const ConfigurationSuccess(guatemalaConfig),
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsOneWidget);
    });

    testWidgets('the wipe dialog cannot be dismissed by tapping outside', (
      tester,
    ) async {
      await tester.pumpWidget(
        build(
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          configuration: const ConfigurationSuccess(guatemalaConfig),
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      await tester.tapAt(const Offset(10, 10));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsOneWidget);
    });

    testWidgets('a throwing save says so instead of spinning for ever', (
      tester,
    ) async {
      // `_isChecking` is deliberately left set on success, because the
      // router's redirect navigates away — so anything that throws after the
      // handshake succeeds used to leave the button spinning silently.
      // `saveServerConfig` writes to secure storage, which raises
      // `PlatformException` on a keystore fault: the same failure the
      // repository's try/catch was added for, one layer up.
      await tester.pumpWidget(
        build(
          configuration: const ConfigurationSuccess(guatemalaConfig),
          saveFails: true,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Operation failed'), findsOneWidget);
      expect(find.text('Connect'), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsNothing);
    });

    testWidgets('a configured device with empty tables is not asked either', (
      tester,
    ) async {
      // The case that made `deviceHoldsServerDataProvider` wrong: handshake a
      // server, never sync, then change to another. The old predicate was true
      // the moment a `ServerConfig` existed, so the switch stopped to ask
      // permission to empty a database with nothing in it. Note the community
      // codes deliberately do NOT match — the point is that the gate never
      // gets as far as comparing them.
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          existing: const ServerConfig(
            serverUrl: 'https://planet.example.org',
            pin: '1234',
            couchDbUrl: 'https://satellite:1234@planet.example.org:443',
            code: 'learning',
            id: 'cfg-learning',
          ),
          holdsServerData: false,
          localPlanetCodes: const {},
          configuration: const ConfigurationSuccess(guatemalaConfig),
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsNothing);
      expect(clearData.calls, 0);
      expect(savedConfigs, hasLength(1));
    });

    testWidgets('the same community on a different Planet still warns', (
      tester,
    ) async {
      // Two unrelated Planets can use the same community `code`; only the
      // `configurations` document id tells them apart, which is what Kotlin
      // compares. Without the veto the code match alone waves this through
      // onto the other Planet's rows.
      //
      // The fixture's load-bearing decoy: `code` is 'learning' on BOTH sides
      // and 'learning' is in `localPlanetCodes`, so the community half of the
      // gate passes. Only the ids differ. Make them equal and this test stops
      // testing anything.
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          existing: const ServerConfig(
            serverUrl: 'https://planet.example.org',
            pin: '1234',
            couchDbUrl: 'https://satellite:1234@planet.example.org:443',
            code: 'learning',
            id: 'cfg-one',
          ),
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          configuration: const ConfigurationSuccess(
            ServerConfig(
              serverUrl: 'https://other.example.org',
              pin: '5562',
              couchDbUrl: 'https://satellite:5562@other.example.org:443',
              code: 'learning',
              id: 'cfg-two',
            ),
          ),
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://other.example.org',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsOneWidget);
      expect(savedConfigs, isEmpty);
    });

    testWidgets('the same configuration document is adopted with no dialog', (
      tester,
    ) async {
      // A clone URL is a different host serving the same community, and its
      // `configurations` document replicates with the same `_id` — so the veto
      // must agree with the community answer here rather than override it.
      await tester.pumpWidget(
        build(
          existing: const ServerConfig(
            serverUrl: 'https://planet.example.org',
            pin: '1234',
            couchDbUrl: 'https://satellite:1234@planet.example.org:443',
            code: 'learning',
            id: 'cfg-one',
          ),
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          configuration: const ConfigurationSuccess(
            ServerConfig(
              serverUrl: 'https://clone.example.org',
              pin: '1234',
              couchDbUrl: 'https://satellite:1234@clone.example.org:443',
              code: 'learning',
              id: 'cfg-one',
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://clone.example.org',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsNothing);
      expect(savedConfigs, hasLength(1));
    });

    testWidgets('an id nobody recorded is unknown, not different', (
      tester,
    ) async {
      // A configuration persisted before `ServerConfig.id` existed carries an
      // empty id. Reading that as "different" would ask every such device to
      // wipe itself on a switch back to the server it is already on. The
      // community answer decides alone.
      await tester.pumpWidget(
        build(
          existing: const ServerConfig(
            serverUrl: 'https://planet.example.org',
            pin: '1234',
            couchDbUrl: 'https://satellite:1234@planet.example.org:443',
            code: 'learning',
          ),
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          configuration: const ConfigurationSuccess(
            ServerConfig(
              serverUrl: 'https://planet.example.org',
              pin: '1234',
              couchDbUrl: 'https://satellite:1234@planet.example.org:443',
              code: 'learning',
              id: 'cfg-one',
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsNothing);
      expect(savedConfigs, hasLength(1));
    });

    testWidgets('a server that reports no id is unknown, not different', (
      tester,
    ) async {
      // The mirror of the case above, and it needs its own test: an unknown is
      // an unknown whichever side is missing it. `_all_docs` can hand back a
      // row without a usable `id`, and treating that as a difference would
      // make the gate fire on the server the device is already on.
      await tester.pumpWidget(
        build(
          existing: const ServerConfig(
            serverUrl: 'https://planet.gt',
            pin: '5562',
            couchDbUrl: 'https://satellite:5562@planet.gt:443',
            code: 'guatemala',
            id: 'cfg-gt',
          ),
          holdsServerData: true,
          localPlanetCodes: const {'guatemala'},
          // `guatemalaConfig` carries no id — that is the decoy here.
          configuration: const ConfigurationSuccess(guatemalaConfig),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsNothing);
      expect(savedConfigs, hasLength(1));
    });

    testWidgets('a transient database failure does not lock Connect out', (
      tester,
    ) async {
      // `FutureProvider` caches a thrown error exactly as it caches a value.
      // Without the invalidate in `_deviceHoldsData` the first failed read was
      // replayed for the rest of the process, so one locked file or full disk
      // meant the app could never be configured again until it was restarted —
      // on a fresh install, where the gate had nothing to protect. Kotlin
      // never asks the database at all, so it configures fine.
      var asked = 0;
      await tester.pumpWidget(
        build(
          holdsServerDataBody: () async {
            asked++;
            throw StateError('database locked');
          },
          configuration: const ConfigurationSuccess(guatemalaConfig),
          existing: const ServerConfig(
            serverUrl: 'https://planet.example.org',
            pin: '1234',
            couchDbUrl: 'https://satellite:1234@planet.example.org:443',
            code: 'learning',
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byType(TextFormField).first,
        'https://planet.gt',
      );
      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);
      expect(find.text('Operation failed'), findsOneWidget);
      expect(asked, 1);

      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);
      expect(
        asked,
        2,
        reason: 'the second Connect must re-ask, not replay a cached throw',
      );
    });

    testWidgets('a tap on a row commits nothing on its own', (tester) async {
      // The binding gate is on the commit, so the tap stays free of it: on a
      // device with no configuration it fills the fields and highlights the
      // row, and that is all. If a tap ever adopts a configuration, the gate
      // is in the wrong place. (A tap on a *configured* device now warns
      // first, which still commits nothing — see the row-tap group below.)
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pumpAndSettle();

      expect(clearData.calls, 0);
      expect(savedConfigs, isEmpty);
      expect(find.text('Clear data'), findsNothing);
      expect(fieldTexts(tester), <String>['http://192.168.48.253', '5678']);
      final selected = tester
          .widgetList<ListTile>(find.byType(ListTile))
          .where((t) => t.selected)
          .map((t) => (t.title! as Text).data)
          .toList();
      expect(selected, <String>['🇬🇹 planet san pablo']);
    });
  });

  testWidgets('the list does not rearrange itself around the tapped row', (
    tester,
  ) async {
    // The ordering argument is the **persisted** configuration, not the text
    // field. Kotlin's is `getPinnedServerUrl()` / `getServerUrl()`, both
    // stored; reading the field instead made the list reorder under the
    // user's finger — and kept reordering as a hand-typed host was entered
    // character by character.
    await tester.pumpWidget(
      build(servers: const [learning, local, fourth, belowFold, fifth]),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('show more'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('🌎 planet vi'));
    await tester.pumpAndSettle();

    var titles = tester
        .widgetList<ListTile>(find.byType(ListTile))
        .map((t) => (t.title! as Text).data)
        .toList();
    expect(titles.first, '🌎 planet learning');

    // Same for a host typed by hand.
    await tester.enterText(
      find.byType(TextFormField).first,
      'https://below.example.org',
    );
    await tester.pumpAndSettle();

    titles = tester
        .widgetList<ListTile>(find.byType(ListTile))
        .map((t) => (t.title! as Text).data)
        .toList();
    expect(titles.first, '🌎 planet learning');
  });

  testWidgets('show more hoists the configured server to the top', (
    tester,
  ) async {
    await tester.pumpWidget(
      build(
        servers: const [learning, local, fourth, belowFold, fifth],
        existing: const ServerConfig(
          serverUrl: 'https://below.example.org',
          pin: '3456',
          couchDbUrl: 'https://satellite:3456@below.example.org:443',
        ),
        holdsServerData: true,
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('show more'));
    await tester.pumpAndSettle();

    final titles = tester
        .widgetList<ListTile>(find.byType(ListTile))
        .map((t) => (t.title! as Text).data)
        .toList();
    expect(titles.first, '🌎 planet below the fold');
    expect(titles, hasLength(5));
    expect(titles.where((t) => t == '🌎 planet below the fold'), hasLength(1));
  });

  group('the row-tap early warning', () {
    /// Port of the warn arm of `ServerAddressAdapter`'s click listener
    /// (`:86-95`), which had no counterpart while "change server" cleared the
    /// configuration: the arm needs to know which server the device is on, and
    /// that was the fact the navigation destroyed.
    ///
    /// It is host-based, as Kotlin's is (`isServerAlreadyConfigured` is "the
    /// `serverURL` preference is non-empty"), while the binding gate at Connect
    /// stays community-based. The two ask what each moment can answer.
    const configured = ServerConfig(
      serverUrl: 'https://below.example.org',
      pin: '3456',
      couchDbUrl: 'https://satellite:3456@below.example.org:443',
      code: 'learning',
    );

    Widget configuredDevice({_StubClearDataNotifier? clearData}) => build(
      servers: const [learning, local, belowFold],
      existing: configured,
      holdsServerData: true,
      localPlanetCodes: const {'learning'},
      clearData: clearData,
    );

    testWidgets('tapping another server warns before filling anything', (
      tester,
    ) async {
      await tester.pumpWidget(configuredDevice());
      await tester.pumpAndSettle();
      expect(fieldTexts(tester), <String>['https://below.example.org', '3456']);

      await tester.tap(find.text('🌎 planet learning'));
      await tester.pumpAndSettle();

      expect(find.text('Clear data'), findsOneWidget);
      // Nothing has moved yet: not the fields, not the highlight.
      expect(fieldTexts(tester), <String>['https://below.example.org', '3456']);
    });

    testWidgets('tapping the server it is already on does not warn', (
      tester,
    ) async {
      // Kotlin's `position != selectedPosition`. Re-tapping the current row is
      // the `else` arm there, and has nothing to warn about here either.
      await tester.pumpWidget(configuredDevice());
      await tester.pumpAndSettle();

      await tester.tap(find.text('🌎 planet below the fold'));
      await tester.pumpAndSettle();

      expect(find.text('Clear data'), findsNothing);
    });

    testWidgets('declining leaves the selection and the fields alone', (
      tester,
    ) async {
      // Kotlin runs `revertSelection()` here, which undoes a selection the
      // warn arm never made — a no-op in its working case. Leaving everything
      // untouched is what that amounts to.
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(configuredDevice(clearData: clearData));
      await tester.pumpAndSettle();

      await tester.tap(find.text('🌎 planet learning'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();

      expect(clearData.calls, 0);
      expect(fieldTexts(tester), <String>['https://below.example.org', '3456']);
      final selected = tester
          .widgetList<ListTile>(find.byType(ListTile))
          .where((t) => t.selected)
          .map((t) => (t.title! as Text).data)
          .toList();
      expect(selected, <String>['🌎 planet below the fold']);
    });

    testWidgets('accepting wipes, then honours the tap', (tester) async {
      // Kotlin discards the tapped server — `onClearDataDialog = { _, _ -> }`
      // ignores it and the wipe ends in `exit(0)`, so the user re-picks after
      // the relaunch. That is the process kill talking, not a decision; there
      // is no restart here to lose the tap across.
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(configuredDevice(clearData: clearData));
      await tester.pumpAndSettle();

      await tester.tap(find.text('🌎 planet learning'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Clear data'));
      await tester.pumpAndSettle();

      expect(clearData.calls, 1);
      expect(fieldTexts(tester), <String>[
        'https://planet.learning.example.org',
        '1234',
      ]);
    });

    testWidgets('a second tap after the wipe does not warn again', (
      tester,
    ) async {
      // The wipe clears what the warning is about. Asking twice for data that
      // is already gone would be the dialog crying wolf, and the user would
      // learn to dismiss it.
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(configuredDevice(clearData: clearData));
      await tester.pumpAndSettle();

      await tester.tap(find.text('🌎 planet learning'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Clear data'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pumpAndSettle();

      expect(find.text('Clear data'), findsNothing);
      expect(clearData.calls, 1);
      expect(fieldTexts(tester), <String>['http://192.168.48.253', '5678']);
    });

    testWidgets('a configured device with empty tables is not warned', (
      tester,
    ) async {
      // This is what pins the `_deviceHoldsData()` conjunct in
      // `_warnsBeforeLeaving`. The test below it cannot: with no `existing`
      // configuration, `_configuredHost` is null and the gate is already
      // closed, so dropping the conjunct leaves that one green.
      //
      // Here the configuration is present and the tapped host differs, so both
      // of Kotlin's own conditions hold and only "is there anything to clear?"
      // stands between the tap and the dialog.
      await tester.pumpWidget(
        build(
          servers: const [learning, local, belowFold],
          existing: configured,
          holdsServerData: false,
          localPlanetCodes: const {},
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🌎 planet learning'));
      await tester.pumpAndSettle();

      expect(find.text('Clear data'), findsNothing);
      // The tap was honoured rather than merely un-warned.
      expect(fieldTexts(tester), <String>[
        'https://planet.learning.example.org',
        '1234',
      ]);
    });

    testWidgets('a double tap raises one dialog, not two', (tester) async {
      // The decision became asynchronous, which left the list live while it
      // resolved — Kotlin's dialog goes up in the same frame as the tap and its
      // modal barrier swallows the rest. Two stacked dialogs each run the wipe
      // and each fill the fields with their own server, so accepting both left
      // the user on the server they tapped *first*.
      //
      // The fixture is the whole test: every other test overrides the
      // predicate with an already-completed future, so the window is one
      // microtask wide and nothing can see it. This one holds it open.
      final gate = Completer<bool>();
      await tester.pumpWidget(
        build(
          servers: const [learning, local, belowFold],
          existing: configured,
          holdsServerDataBody: () => gate.future,
          localPlanetCodes: const {'learning'},
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🌎 planet learning'));
      await tester.pump();
      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pump();

      gate.complete(true);
      await tester.pumpAndSettle();

      expect(find.text('Clear data'), findsOneWidget);
    });

    testWidgets('Connect after a tap wipe does not ask a second time', (
      tester,
    ) async {
      // What pins `_holdsServerData = false` in the tap's accept path. The
      // "second tap does not warn again" test below cannot: the same `setState`
      // nulls `_configuredHost`, which `_warnsBeforeLeaving` evaluates first,
      // so deleting the assignment leaves it green. `_wipeRefusedFor` never
      // reads `_configuredHost`, so Connect is where the assignment shows.
      //
      // The community codes deliberately do not match — without the
      // assignment the gate would get as far as comparing them and warn.
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          servers: const [learning, local, belowFold],
          existing: configured,
          holdsServerData: true,
          localPlanetCodes: const {'learning'},
          configuration: const ConfigurationSuccess(guatemalaConfig),
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Clear data'));
      await tester.pumpAndSettle();
      expect(clearData.calls, 1);

      await tester.tap(find.text('Connect'));
      await pumpFrames(tester);

      expect(find.text('Clear data'), findsNothing);
      expect(clearData.calls, 1);
      expect(savedConfigs, hasLength(1));
    });

    testWidgets('a device that holds nothing is never warned', (tester) async {
      // `isServerAlreadyConfigured` is false on a fresh device, so every tap
      // takes the `else` arm — the first configuration is not a switch.
      await tester.pumpWidget(
        build(servers: const [learning, local], holdsServerData: false),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🌎 planet learning'));
      await tester.pumpAndSettle();

      expect(find.text('Clear data'), findsNothing);
    });
  });
}
