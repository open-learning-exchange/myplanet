import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/planet_servers.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/settings_provider.dart';
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

  Widget build({
    List<PlanetServer> servers = const [learning, local],
    ServerConfig? existing,
    // `deviceHoldsServerDataProvider` reads `planetPrefsProvider`, which
    // throws unless overridden, so every test declares this. `false` is the
    // fresh-install case the older tests here were written against.
    bool holdsServerData = false,
    _StubClearDataNotifier? clearData,
  }) {
    return wrapScreen(
      const ServerConfigScreen(),
      overrides: [
        planetServersProvider.overrideWithValue(servers),
        serverConfigProvider.overrideWith(
          () => _StubServerConfigNotifier(existing),
        ),
        deviceHoldsServerDataProvider.overrideWithValue(holdsServerData),
        clearDataProvider.overrideWith(
          () => clearData ?? _StubClearDataNotifier(),
        ),
      ],
    );
  }

  const configuredForLearning = ServerConfig(
    serverUrl: 'https://planet.learning.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.learning.example.org:443',
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
    testWidgets(
      'a different server on a device holding data does not fill the fields',
      (tester) async {
        // The defect this group exists for. Kotlin routes this tap to
        // `onClearDataDialog` and never calls `onItemClick`, so the fields
        // keep showing the server whose documents are on the device
        // (`ServerAddressAdapter.kt:88-92`). Filling them and connecting
        // leaves one Planet's database under another Planet's configuration.
        await tester.pumpWidget(
          build(existing: configuredForLearning, holdsServerData: true),
        );
        await tester.pumpAndSettle();

        await tester.tap(find.text('🇬🇹 planet san pablo'));
        await tester.pumpAndSettle();

        expect(
          find.text(
            'Are you sure you want to change the configuration? This action '
            'will clear the app data.',
          ),
          findsOneWidget,
        );
        expect(fieldTexts(tester), <String>[
          'https://planet.learning.example.org',
          '1234',
        ]);
      },
    );

    testWidgets('cancelling leaves the fields and the data alone', (
      tester,
    ) async {
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          existing: configuredForLearning,
          holdsServerData: true,
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();

      expect(clearData.calls, 0);
      expect(fieldTexts(tester), <String>[
        'https://planet.learning.example.org',
        '1234',
      ]);
      // `revertSelection`: the highlight is back on the configured row, not
      // on the one that was tapped and declined.
      final selected = tester
          .widgetList<ListTile>(find.byType(ListTile))
          .where((t) => t.selected)
          .map((t) => (t.title! as Text).data)
          .toList();
      expect(selected, <String>['🌎 planet learning']);
    });

    testWidgets('confirming wipes first, then fills the new server', (
      tester,
    ) async {
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          existing: configuredForLearning,
          holdsServerData: true,
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Clear data'));
      // Never `pumpAndSettle` here: while the wipe runs the dialog holds an
      // indefinite `CircularProgressIndicator`.
      await tester.pump();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));

      expect(clearData.calls, 1);
      expect(find.text('Clear data'), findsNothing);
      expect(fieldTexts(tester), <String>['http://192.168.48.253', '5678']);
    });

    testWidgets('a second tap after the wipe does not ask again', (
      tester,
    ) async {
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          existing: configuredForLearning,
          holdsServerData: true,
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Clear data'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));

      await tester.tap(find.text('🌎 planet learning'));
      await tester.pumpAndSettle();

      // The data is already gone, so there is nothing left to warn about.
      expect(clearData.calls, 1);
      expect(fieldTexts(tester), <String>[
        'https://planet.learning.example.org',
        '1234',
      ]);
    });

    testWidgets('a failed wipe keeps the dialog open with its buttons live', (
      tester,
    ) async {
      // Kotlin's `catch`: progress dismissed, back-press guard released, both
      // buttons re-enabled, dialog still up (`SyncActivity.kt:289-295`).
      final clearData = _StubClearDataNotifier(fails: true);
      await tester.pumpWidget(
        build(
          existing: configuredForLearning,
          holdsServerData: true,
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Clear data'));
      await tester.pumpAndSettle();

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
      // And the fields still describe the server the data belongs to.
      expect(fieldTexts(tester), <String>[
        'https://planet.learning.example.org',
        '1234',
      ]);
    });

    testWidgets('tapping the server already selected asks nothing', (
      tester,
    ) async {
      // `position != selectedPosition` is the gate, so the configured row
      // takes the `else` arm and simply re-fills.
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(
        build(
          existing: configuredForLearning,
          holdsServerData: true,
          clearData: clearData,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🌎 planet learning'));
      await tester.pumpAndSettle();

      expect(clearData.calls, 0);
      expect(find.text('Clear data'), findsNothing);
      // Re-filled from the row: Kotlin uses `getPinForUrl`, not the stored PIN.
      expect(fieldTexts(tester), <String>[
        'https://planet.learning.example.org',
        '1234',
      ]);
    });

    testWidgets('a fresh device is never asked to clear anything', (
      tester,
    ) async {
      final clearData = _StubClearDataNotifier();
      await tester.pumpWidget(build(clearData: clearData));
      await tester.pumpAndSettle();

      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pumpAndSettle();

      expect(clearData.calls, 0);
      expect(fieldTexts(tester), <String>['http://192.168.48.253', '5678']);
    });

    testWidgets(
      'a synced device whose configuration is gone still warns on any tap',
      (tester) async {
        // The path that actually exists: the login screen's "change server"
        // clears the persisted config to make the router redirect here, so the
        // signal Kotlin reads is destroyed while the database is still full.
        // With no selection every row is "different", which is Kotlin's own
        // behaviour when `selectedPosition == -1`.
        final clearData = _StubClearDataNotifier();
        await tester.pumpWidget(
          build(holdsServerData: true, clearData: clearData),
        );
        await tester.pumpAndSettle();

        await tester.tap(find.text('🌎 planet learning'));
        await tester.pumpAndSettle();

        expect(find.text('Clear data'), findsOneWidget);
        expect(fieldTexts(tester), <String>['', '']);
      },
    );

    testWidgets('the wipe dialog cannot be dismissed by tapping outside', (
      tester,
    ) async {
      await tester.pumpWidget(
        build(existing: configuredForLearning, holdsServerData: true),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('🇬🇹 planet san pablo'));
      await tester.pumpAndSettle();

      await tester.tapAt(const Offset(10, 10));
      await tester.pumpAndSettle();

      expect(find.text('Clear data'), findsOneWidget);
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
