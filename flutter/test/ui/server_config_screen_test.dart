import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/planet_servers.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/providers/app_providers.dart';
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

void main() {
  const learning = PlanetServer(
    name: '🌎 planet learning',
    host: 'planet.learning.example.org',
    pin: '1234',
    scheme: 'https',
  );
  const local = PlanetServer(
    name: '🇬🇹 planet san pablo',
    host: 'sanpablo.example.org',
    pin: '5678',
    scheme: 'http',
  );
  const fourth = PlanetServer(
    name: '🇸🇴 planet somalia',
    host: 'somalia.example.org',
    pin: '9012',
    scheme: 'https',
  );

  Widget build({
    List<PlanetServer> servers = const [learning, local],
    ServerConfig? existing,
  }) {
    return wrapScreen(
      const ServerConfigScreen(),
      overrides: [
        planetServersProvider.overrideWithValue(servers),
        serverConfigProvider.overrideWith(
          () => _StubServerConfigNotifier(existing),
        ),
      ],
    );
  }

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
    expect(url, 'http://sanpablo.example.org');
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
}
