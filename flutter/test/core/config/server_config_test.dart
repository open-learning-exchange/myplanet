import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';

void main() {
  group('buildCouchDbUrl', () {
    test('injects the satellite account and the default https port', () {
      expect(
        ServerConfig.buildCouchDbUrl('https://planet.example.org', '1234'),
        'https://satellite:1234@planet.example.org:443',
      );
    });

    test('uses port 80 for http', () {
      expect(
        ServerConfig.buildCouchDbUrl('http://192.168.1.10', '0000'),
        'http://satellite:0000@192.168.1.10:80',
      );
    });

    test('keeps an explicit port', () {
      expect(
        ServerConfig.buildCouchDbUrl('http://192.168.1.10:5000', '0000'),
        'http://satellite:0000@192.168.1.10:5000',
      );
    });

    /// Matches the Kotlin: only scheme/host/port survive, so a path is dropped.
    test('drops the path', () {
      expect(
        ServerConfig.buildCouchDbUrl('https://planet.example.org/ml', '1234'),
        'https://satellite:1234@planet.example.org:443',
      );
    });

    /// An unencoded '@' or '/' in the PIN would otherwise re-point the URL at a
    /// different host.
    test('percent-encodes the PIN in the userinfo component', () {
      expect(
        ServerConfig.buildCouchDbUrl('https://planet.example.org', 'p@ss/word'),
        'https://satellite:p%40ss%2Fword@planet.example.org:443',
      );
    });

    test('is a no-op for the numeric PINs actually in use', () {
      expect(
        ServerConfig.buildCouchDbUrl('https://planet.example.org', '1234'),
        contains('satellite:1234@'),
      );
    });

    test('rejects a URL with no scheme or host', () {
      expect(
        () => ServerConfig.buildCouchDbUrl('planet.example.org', '1234'),
        throwsFormatException,
      );
      expect(
        () => ServerConfig.buildCouchDbUrl('', '1234'),
        throwsFormatException,
      );
    });

    test('passes through a URL that already carries credentials', () {
      const url = 'https://someone:secret@planet.example.org:443';
      expect(ServerConfig.buildCouchDbUrl(url, '1234'), url);
    });
  });

  group('value semantics', () {
    const config = ServerConfig(
      serverUrl: 'https://planet.example.org',
      pin: '1234',
      couchDbUrl: 'https://satellite:1234@planet.example.org:443',
    );

    test('equal configs compare equal and hash alike', () {
      expect(config, equals(config.copyWith()));
      expect(config.hashCode, config.copyWith().hashCode);
    });

    test('copyWith replaces only the named field', () {
      final updated = config.copyWith(code: 'guatemala');
      expect(updated.code, 'guatemala');
      expect(updated.serverUrl, config.serverUrl);
      expect(updated, isNot(equals(config)));
    });
  });
}
