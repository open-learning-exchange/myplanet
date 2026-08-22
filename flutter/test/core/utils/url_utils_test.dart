import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/utils/url_utils.dart';

void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  group('basicAuthHeader', () {
    test('base64-encodes user:password', () {
      final header = UrlUtils.basicAuthHeader('satellite', '1234');
      expect(header, 'Basic ${base64.encode(utf8.encode('satellite:1234'))}');
      expect(header, 'Basic c2F0ZWxsaXRlOjEyMzQ=');
    });

    test('handles non-ASCII credentials as UTF-8', () {
      expect(
        UrlUtils.basicAuthHeader('usuario', 'contraseña'),
        'Basic ${base64.encode(utf8.encode('usuario:contraseña'))}',
      );
    });
  });

  group('authHeader', () {
    test('uses satellite:pin for the primary server', () {
      expect(
        UrlUtils.authHeader(config),
        UrlUtils.basicAuthHeader('satellite', '1234'),
      );
    });

    test('uses satellite:pin when the alternative URL has no userinfo', () {
      final alt = config.copyWith(
        alternativeUrl: 'https://clone.example.org:443',
        isAlternativeUrl: true,
      );
      expect(
        UrlUtils.authHeader(alt),
        UrlUtils.basicAuthHeader('satellite', '1234'),
      );
    });

    test('extracts credentials from a credential-bearing alternative URL', () {
      // Upstream #15834: the auth header must carry the alternative URL's
      // embedded credentials, not the primary's satellite:pin.
      final alt = config.copyWith(
        alternativeUrl: 'http://clone_user:clone_pass@alternative.com:5984',
        isAlternativeUrl: true,
      );
      expect(
        UrlUtils.authHeader(alt),
        UrlUtils.basicAuthHeader('clone_user', 'clone_pass'),
      );
    });

    test('ignores the alternative URL when isAlternativeUrl is false', () {
      final alt = config.copyWith(
        alternativeUrl: 'http://clone_user:clone_pass@alternative.com:5984',
        isAlternativeUrl: false,
      );
      expect(
        UrlUtils.authHeader(alt),
        UrlUtils.basicAuthHeader('satellite', '1234'),
      );
    });
  });

  group('baseUrl / dbUrl', () {
    test('baseUrl returns the CouchDB URL without a /db suffix', () {
      expect(
        UrlUtils.baseUrl(config),
        'https://satellite:1234@planet.example.org:443',
      );
    });

    test('baseUrl strips an existing /db suffix', () {
      expect(
        UrlUtils.baseUrl(config.copyWith(couchDbUrl: 'https://host:443/db')),
        'https://host:443',
      );
    });

    test('baseUrl prefers the alternative URL when it is active', () {
      final withAlternative = config.copyWith(
        alternativeUrl: 'https://clone.example.org',
        isAlternativeUrl: true,
      );
      expect(UrlUtils.baseUrl(withAlternative), 'https://clone.example.org');
    });

    test('baseUrl ignores the alternative URL when it is not active', () {
      final withAlternative = config.copyWith(
        alternativeUrl: 'https://clone.example.org',
      );
      expect(
        UrlUtils.baseUrl(withAlternative),
        'https://satellite:1234@planet.example.org:443',
      );
    });

    test('dbUrl appends /db exactly once', () {
      expect(
        UrlUtils.dbUrl(config),
        'https://satellite:1234@planet.example.org:443/db',
      );
      expect(UrlUtils.dbUrlOf('https://host/db'), 'https://host/db');
      expect(UrlUtils.dbUrlOf('https://host/'), 'https://host/db');
      expect(UrlUtils.dbUrlOf('https://host'), 'https://host/db');
    });
  });

  group('derived endpoints', () {
    test('updateUrl, apkVersionUrl and checksumUrl hang off the base URL', () {
      final base = UrlUtils.baseUrl(config);
      expect(UrlUtils.updateUrl(config), '$base/versions');
      expect(UrlUtils.apkVersionUrl(config), '$base/apkversion');
      expect(UrlUtils.checksumUrl(config), '$base/fs/myPlanet.apk.sha256');
    });

    test('healthAccessUrl carries the PIN', () {
      expect(
        UrlUtils.healthAccessUrl(config),
        '${UrlUtils.baseUrl(config)}/healthaccess?p=1234',
      );
    });

    test('healthAccessUrl falls back to 0000 for a blank PIN', () {
      expect(
        UrlUtils.healthAccessUrl(config.copyWith(pin: '')),
        '${UrlUtils.baseUrl(config)}/healthaccess?p=0000',
      );
    });

    test('userDocUrl uses the CouchDB user-document naming convention', () {
      expect(
        UrlUtils.userDocUrl(config, 'ada'),
        '${UrlUtils.dbUrl(config)}/_users/org.couchdb.user:ada',
      );
    });

    test('resourceUrl points at the attachment', () {
      expect(
        UrlUtils.resourceUrl(config, 'res-1', 'chapter.pdf'),
        '${UrlUtils.dbUrl(config)}/resources/res-1/chapter.pdf',
      );
    });

    test('resourceUrl encodes both segments', () {
      expect(
        UrlUtils.resourceUrl(config, 'res 1', 'a/b.pdf'),
        '${UrlUtils.dbUrl(config)}/resources/res%201/a%2Fb.pdf',
      );
    });

    /// The Kotlin interpolates nulls and builds a URL containing the literal
    /// text "null", which can only 404.
    test('resourceUrl returns null instead of embedding "null"', () {
      expect(UrlUtils.resourceUrl(config, null, 'a.pdf'), isNull);
      expect(UrlUtils.resourceUrl(config, 'res-1', null), isNull);
      expect(UrlUtils.resourceUrl(config, '', 'a.pdf'), isNull);
    });

    test('userDocUrl encodes the name but keeps the CouchDB colon literal', () {
      final url = UrlUtils.userDocUrl(config, 'ada lovelace');
      expect(url, endsWith('/_users/org.couchdb.user:ada%20lovelace'));
      // CouchDB requires the colon unencoded in the document id.
      expect(url, contains('org.couchdb.user:'));
      expect(url, isNot(contains('org.couchdb.user%3A')));
    });
  });

  group('image URLs', () {
    test('encodes spaces in the image name as %20', () {
      expect(
        UrlUtils.userImageUrl(config, 'user-1', 'my photo.png'),
        '${UrlUtils.dbUrl(config)}/_users/user-1/my%20photo.png',
      );
    });

    test('returns null for blank inputs', () {
      expect(UrlUtils.userImageUrl(config, null, 'a.png'), isNull);
      expect(UrlUtils.userImageUrl(config, '  ', 'a.png'), isNull);
      expect(UrlUtils.userImageUrl(config, 'user-1', '  '), isNull);
      expect(UrlUtils.courseImageUrl(config, 'course-1', null), isNull);
    });

    test('courseImageUrl mirrors userImageUrl', () {
      expect(
        UrlUtils.courseImageUrl(config, 'course-1', 'cover art.jpg'),
        '${UrlUtils.dbUrl(config)}/courses/course-1/cover%20art.jpg',
      );
    });
  });

  group('credentialFreeDbUrl', () {
    test('strips the satellite:PIN userinfo before the host', () {
      // This is the security-critical contract: anything persisted to the
      // outbox (which survives schema upgrades on disk) must not carry the
      // server PIN. The credential-free URL is what the uploader persists.
      expect(
        UrlUtils.credentialFreeDbUrl(config),
        'https://planet.example.org/db',
      );
    });

    test('leaves a URL with no userinfo unchanged', () {
      const safe = ServerConfig(
        serverUrl: 'https://planet.example.org',
        pin: '1234',
        couchDbUrl: 'https://planet.example.org:443',
      );
      expect(
        UrlUtils.credentialFreeDbUrl(safe),
        'https://planet.example.org/db',
      );
    });

    test('preserves a non-default port and path', () {
      const portConfig = ServerConfig(
        serverUrl: 'https://planet.example.org:6984',
        pin: '4321',
        couchDbUrl: 'https://admin:4321@planet.example.org:6984',
      );
      expect(
        UrlUtils.credentialFreeDbUrl(portConfig),
        'https://planet.example.org:6984/db',
      );
    });

    test('falls back to the raw dbUrl when the URL cannot be parsed', () {
      const broken = ServerConfig(
        serverUrl: 'not a url',
        pin: '1234',
        couchDbUrl: 'not a url',
      );
      // No host to strip userinfo from — returns the input verbatim rather
      // than throwing, so a malformed config never blocks the upload path.
      expect(UrlUtils.credentialFreeDbUrl(broken), UrlUtils.dbUrl(broken));
    });

    test('differs from dbUrl whenever credentials are present', () {
      // The invariant the uploader relies on: the persisted endpoint is never
      // the credential-bearing one.
      expect(
        UrlUtils.credentialFreeDbUrl(config),
        isNot(UrlUtils.dbUrl(config)),
      );
    });

    test('matches dbUrl when there are no credentials to strip', () {
      const bare = ServerConfig(
        serverUrl: 'https://planet.example.org',
        pin: '1234',
        couchDbUrl: 'https://planet.example.org',
      );
      // No userinfo to strip, so the two are identical (same input → same
      // output; no Uri normalisation discrepancy when there is no port).
      expect(UrlUtils.credentialFreeDbUrl(bare), UrlUtils.dbUrl(bare));
    });
  });
}
