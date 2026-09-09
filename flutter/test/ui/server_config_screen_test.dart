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
class _StubServerConfigNotifier extends ServerConfigNotifier {
  _StubServerConfigNotifier(this._initial);

  final ServerConfig? _initial;

  @override
  ServerConfig? build() => _initial;
}

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
class _RecordingServerConfigNotifier extends ServerConfigNotifier {
  _RecordingServerConfigNotifier(this._saved, {this.fails = false});

  final List<ServerConfig> _saved;
  final bool fails;

  @override
  ServerConfig? build() => null;

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
    Set<String> localPlanetCodes = const {},
    ConfigurationResult? configuration,
    _StubClearDataNotifier? clearData,
    bool saveFails = false,
  }) {
    return wrapScreen(
      const ServerConfigScreen(),
      overrides: [
        planetServersProvider.overrideWithValue(servers),
        if (existing == null)
          serverConfigProvider.overrideWith(
            () =>
                _RecordingServerConfigNotifier(savedConfigs, fails: saveFails),
          )
        else
          serverConfigProvider.overrideWith(
            () => _StubServerConfigNotifier(existing),
          ),
        deviceHoldsServerDataProvider.overrideWithValue(holdsServerData),
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
    // Every test here drives the state the port can actually be in: no
    // persisted `ServerConfig`, because the only way a configured device
    // reaches this screen is the login screen's "change server", which clears
    // it to make the router's redirect fire. An earlier cut of these tests
    // passed `existing: <a config>` together with `holdsServerData: true` — a
    // combination `router.dart` forbids, since a device with a config is
    // redirected away from `/server` — and one of them then certified
    // behaviour production does not have. Phase 113's shape exactly: the
    // fixture fabricated the join.

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

    testWidgets('a tap on a row commits nothing on its own', (tester) async {
      // The gate is on the commit, so the tap has to stay free: it fills the
      // fields and highlights the row, and that is all. If a tap ever adopts a
      // configuration again, the gate is in the wrong place.
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
}
