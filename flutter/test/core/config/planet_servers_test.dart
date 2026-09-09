import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/planet_servers.dart';

/// Guards `lib/core/config/planet_servers.dart` against the two ways it can be
/// wrong without failing: a server offered over the wrong scheme, and a filter
/// that hides the row the user is already on.
void main() {
  group('the list mirrors ServerConfigUtils', () {
    test(
      'declares the eleven servers getServerAddresses does, in its order',
      () {
        expect(allPlanetServers.map((s) => s.name).toList(), <String>[
          '🌎 planet learning',
          '🇬🇹 planet guatemala',
          '🇬🇹 planet san pablo',
          '🌎 planet earth',
          '🇸🇴 planet somalia',
          '🌎 planet vi',
          '🇬🇹 planet xela',
          '🇰🇪 planet uriur',
          '🇰🇪 planet ruiru',
          '🇰🇪 planet embakasi',
          '🇺🇸 planet cambridge',
        ]);
      },
    );

    test('gives exactly the four http servers getDefaultProtocol names', () {
      // `getDefaultProtocol` returns HTTP for xela, san pablo, uriur and
      // embakasi and HTTPS for the rest. Offering any of those four over https
      // would fail the handshake against a server that is up, which is the
      // failure this whole slice exists to stop being mysterious.
      final http = allPlanetServers
          .where((s) => s.scheme == 'http')
          .map((s) => s.name)
          .toList();
      expect(http, <String>[
        '🇬🇹 planet san pablo',
        '🇬🇹 planet xela',
        '🇰🇪 planet uriur',
        '🇰🇪 planet embakasi',
      ]);
      expect(allPlanetServers.where((s) => s.scheme == 'https'), hasLength(7));
    });

    test('url joins the scheme to the bare host', () {
      const server = PlanetServer(
        name: 'x',
        host: 'planet.example.org',
        pin: '1234',
        scheme: 'http',
      );
      expect(server.url, 'http://planet.example.org');
    });

    test('a build with no dart-defines offers nothing rather than blanks', () {
      // `String.fromEnvironment` is empty under `flutter test`, so this is the
      // real state here — and it is the documented fallback: the screen drops
      // the picker and manual entry still works.
      expect(configuredPlanetServers, isEmpty);
      expect(allPlanetServers.every((s) => s.host.isEmpty), isTrue);
    });
  });

  group('planetServersToShow ports getFilteredList', () {
    List<PlanetServer> servers(int count) => List.generate(
      count,
      (i) => PlanetServer(
        name: 'server $i',
        host: 'host$i.example.org',
        pin: '$i$i$i$i',
        scheme: 'https',
      ),
    );

    test('shows the first three when collapsed', () {
      final shown = planetServersToShow(
        servers: servers(11),
        showAdditional: false,
      );
      expect(shown.map((s) => s.name), <String>[
        'server 0',
        'server 1',
        'server 2',
      ]);
    });

    test('shows all of them when expanded', () {
      expect(
        planetServersToShow(servers: servers(11), showAdditional: true),
        hasLength(11),
      );
    });

    test('prepends the configured server when it is below the fold', () {
      final shown = planetServersToShow(
        servers: servers(11),
        showAdditional: false,
        configuredHost: 'host7.example.org',
      );
      expect(shown.map((s) => s.name), <String>[
        'server 7',
        'server 0',
        'server 1',
        'server 2',
      ]);
    });

    test('does not duplicate a configured server already in the top three', () {
      final shown = planetServersToShow(
        servers: servers(11),
        showAdditional: false,
        configuredHost: 'host1.example.org',
      );
      expect(shown, hasLength(3));
      expect(shown.map((s) => s.name), <String>[
        'server 0',
        'server 1',
        'server 2',
      ]);
    });

    test('tolerates a configured host that is not in the list at all', () {
      // A hand-typed local server is the ordinary case here.
      final shown = planetServersToShow(
        servers: servers(11),
        showAdditional: false,
        configuredHost: '192.168.1.50:5000',
      );
      expect(shown, hasLength(3));
    });

    test('an empty or null configured host changes nothing', () {
      for (final host in <String?>[null, '']) {
        expect(
          planetServersToShow(
            servers: servers(5),
            showAdditional: false,
            configuredHost: host,
          ),
          hasLength(3),
        );
      }
    });

    test('fewer servers than the fold is not padded or thrown over', () {
      expect(
        planetServersToShow(servers: servers(2), showAdditional: false),
        hasLength(2),
      );
      expect(
        planetServersToShow(servers: servers(0), showAdditional: false),
        isEmpty,
      );
    });
  });

  group('hostWithoutScheme ports removeProtocol', () {
    test('strips either scheme and leaves anything else alone', () {
      expect(
        hostWithoutScheme('https://planet.example.org'),
        'planet.example.org',
      );
      expect(
        hostWithoutScheme('http://192.168.1.50:5000'),
        '192.168.1.50:5000',
      );
      expect(hostWithoutScheme('planet.example.org'), 'planet.example.org');
      expect(hostWithoutScheme(''), '');
    });
  });
}
