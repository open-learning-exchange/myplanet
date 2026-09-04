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

  group('copyWith — each field', () {
    const base = ServerConfig(
      serverUrl: 'https://a.example.org',
      pin: '1111',
      couchDbUrl: 'https://satellite:1111@a.example.org:443',
      alternativeUrl: 'https://b.example.org',
      isAlternativeUrl: false,
      code: 'gt',
      id: 'config-1',
      parentCode: 'nation',
    );

    test('serverUrl', () {
      final updated = base.copyWith(serverUrl: 'https://c.example.org');
      expect(updated.serverUrl, 'https://c.example.org');
      expect(updated.pin, base.pin);
    });

    test('pin', () {
      final updated = base.copyWith(pin: '2222');
      expect(updated.pin, '2222');
      expect(updated.serverUrl, base.serverUrl);
    });

    test('couchDbUrl', () {
      final updated = base.copyWith(
        couchDbUrl: 'https://satellite:9999@x.org:443',
      );
      expect(updated.couchDbUrl, 'https://satellite:9999@x.org:443');
    });

    test('alternativeUrl', () {
      final updated = base.copyWith(
        alternativeUrl: 'https://mirror.example.org',
      );
      expect(updated.alternativeUrl, 'https://mirror.example.org');
    });

    test('isAlternativeUrl', () {
      final updated = base.copyWith(isAlternativeUrl: true);
      expect(updated.isAlternativeUrl, isTrue);
      expect(base.isAlternativeUrl, isFalse);
    });

    test('code', () {
      final updated = base.copyWith(code: 'us');
      expect(updated.code, 'us');
    });

    test('id', () {
      final updated = base.copyWith(id: 'config-2');
      expect(updated.id, 'config-2');
    });

    test('parentCode', () {
      final updated = base.copyWith(parentCode: 'earth');
      expect(updated.parentCode, 'earth');
    });
  });

  group('equality', () {
    const a = ServerConfig(
      serverUrl: 'https://a.example.org',
      pin: '1111',
      couchDbUrl: 'https://satellite:1111@a.example.org:443',
      alternativeUrl: 'https://b.example.org',
      isAlternativeUrl: true,
      code: 'gt',
      id: 'config-1',
      parentCode: 'nation',
    );

    test('is equal when all fields match', () {
      expect(a, equals(a.copyWith()));
    });

    test('differs when serverUrl differs', () {
      expect(a, isNot(equals(a.copyWith(serverUrl: 'https://other'))));
    });

    test('differs when pin differs', () {
      expect(a, isNot(equals(a.copyWith(pin: '9999'))));
    });

    test('differs when couchDbUrl differs', () {
      expect(a, isNot(equals(a.copyWith(couchDbUrl: 'other'))));
    });

    test('differs when alternativeUrl differs', () {
      expect(a, isNot(equals(a.copyWith(alternativeUrl: 'https://other'))));
    });

    test('differs when isAlternativeUrl differs', () {
      expect(a, isNot(equals(a.copyWith(isAlternativeUrl: false))));
    });

    test('differs when code differs', () {
      expect(a, isNot(equals(a.copyWith(code: 'other'))));
    });

    test('differs when id differs', () {
      expect(a, isNot(equals(a.copyWith(id: 'other'))));
    });

    test('differs when parentCode differs', () {
      expect(a, isNot(equals(a.copyWith(parentCode: 'other'))));
    });

    test('is not equal to a non-ServerConfig object', () {
      expect(a, isNot(equals('not a config')));
    });
  });

  group('defaults', () {
    test('alternativeUrl defaults to null', () {
      const config = ServerConfig(
        serverUrl: 'https://a.example.org',
        pin: '1111',
        couchDbUrl: 'https://satellite:1111@a.example.org:443',
      );
      expect(config.alternativeUrl, isNull);
    });

    test('isAlternativeUrl defaults to false', () {
      const config = ServerConfig(
        serverUrl: 'https://a.example.org',
        pin: '1111',
        couchDbUrl: 'https://satellite:1111@a.example.org:443',
      );
      expect(config.isAlternativeUrl, isFalse);
    });

    test('code defaults to an empty string', () {
      const config = ServerConfig(
        serverUrl: 'https://a.example.org',
        pin: '1111',
        couchDbUrl: 'https://satellite:1111@a.example.org:443',
      );
      expect(config.code, isEmpty);
    });

    test('id defaults to an empty string', () {
      const config = ServerConfig(
        serverUrl: 'https://a.example.org',
        pin: '1111',
        couchDbUrl: 'https://satellite:1111@a.example.org:443',
      );
      expect(config.id, isEmpty);
    });

    test('parentCode defaults to an empty string', () {
      const config = ServerConfig(
        serverUrl: 'https://a.example.org',
        pin: '1111',
        couchDbUrl: 'https://satellite:1111@a.example.org:443',
      );
      expect(config.parentCode, isEmpty);
    });
  });
}
