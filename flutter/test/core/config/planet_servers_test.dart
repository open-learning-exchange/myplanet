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

    test('resolves both arms of getDefaultProtocol, not just the named one', () {
      // The bug this replaced: an earlier cut hardcoded four http servers,
      // from `getDefaultProtocol`'s explicit-name arm alone, and asserted that
      // `isLocalNetwork` could not fire "because every host is a public
      // domain". Six of the eleven hosts in `gradle.properties` are private
      // addresses. Ruiru (192.168.1.66) and Cambridge (192.168.68.126) are
      // private but *not* named explicitly, so only the local-network arm
      // makes them http — and offering a LAN box over TLS fails the handshake
      // and reports "couldn't reach the server", the exact confusion this
      // slice exists to end.
      expect(defaultProtocolFor('192.168.1.66'), 'http');
      expect(defaultProtocolFor('192.168.68.126'), 'http');
      expect(defaultProtocolFor('10.82.1.31'), 'http');
      expect(defaultProtocolFor('192.168.48.253'), 'http');
      expect(defaultProtocolFor('planet.learning.ole.org'), 'https');
      expect(defaultProtocolFor('planet.gt'), 'https');
    });

    test('isLocalNetwork ports every arm Kotlin tests', () {
      for (final host in const <String>[
        '192.168.1.1',
        '10.0.0.1',
        '172.16.0.1',
        '172.19.5.5',
        '172.31.255.255',
        'localhost',
        '127.0.0.1',
        'box.local',
      ]) {
        expect(isLocalNetwork(host), isTrue, reason: host);
      }
      for (final host in const <String>[
        'planet.learning.ole.org',
        '172.15.0.1', // just below the private block
        '172.32.0.1', // just above it
        '110.0.0.1', // starts with "1", not "10."
        'notlocalhost',
      ]) {
        expect(isLocalNetwork(host), isFalse, reason: host);
      }
    });

    test('isLocalNetwork strips a port and a path first, as Kotlin does', () {
      expect(isLocalNetwork('192.168.1.50:5000'), isTrue);
      expect(isLocalNetwork('192.168.1.50/db'), isTrue);
      expect(isLocalNetwork('planet.gt:443'), isFalse);
    });

    test('url resolves the scheme from the host rather than storing one', () {
      const lan = PlanetServer(name: 'x', host: '192.168.1.66', pin: '1234');
      const public = PlanetServer(
        name: 'y',
        host: 'planet.example.org',
        pin: '1234',
      );
      expect(lan.url, 'http://192.168.1.66');
      expect(public.url, 'https://planet.example.org');
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
