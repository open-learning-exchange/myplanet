import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/server_url_mapper.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/repository/configurations_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late MockPlanetApi api;

  setUp(() => api = MockPlanetApi());

  ConfigurationsRepository buildRepository({
    Map<String, String> mappings = const {},
    String appVersion = '0.62.97',
    Future<String> Function()? appVersionLookup,
  }) {
    return ConfigurationsRepository(
      api,
      ServerUrlMapper(mappings: mappings),
      currentAppVersion: appVersion,
      appVersionLookup: appVersionLookup,
    );
  }

  void stubVersions(String url, {required String minApk}) {
    when(() => api.getConfiguration('$url/versions')).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'minapk': minApk}),
    );
  }

  void stubConfigurations(
    String couchDbUrl, {
    String id = 'config-1',
    String code = 'guatemala',
    String parentCode = 'earth',
    String preferredLang = 'Spanish',
  }) {
    when(
      () => api.getConfiguration(
        '$couchDbUrl/db/configurations/_all_docs?include_docs=true',
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'id': id,
            'doc': {
              'code': code,
              'parentCode': parentCode,
              'preferredLang': preferredLang,
            },
          },
        ],
      }),
    );
  }

  group('getMinApk — success', () {
    test(
      'returns the community configuration for a reachable server',
      () async {
        const url = 'https://planet.example.org';
        const couchDbUrl = 'https://satellite:1234@planet.example.org:443';

        stubVersions(url, minApk: '0.60.0');
        stubConfigurations(couchDbUrl);

        final result = await buildRepository().getMinApk(url, '1234');

        expect(result, isA<ConfigurationSuccess>());
        final config = (result as ConfigurationSuccess).config;
        expect(config.serverUrl, url);
        expect(config.pin, '1234');
        expect(config.couchDbUrl, couchDbUrl);
        expect(config.id, 'config-1');
        expect(config.code, 'guatemala');
        expect(config.parentCode, 'earth');
        expect(config.isAlternativeUrl, isFalse);
      },
    );

    test('falls back to the alternative URL when the primary fails', () async {
      const primary = 'http://local.example';
      const alternative = 'https://clone.example';
      const alternativeCouchDb = 'https://satellite:1234@clone.example:443';

      // The primary answers /versions but has no configurations document.
      stubVersions(primary, minApk: '0.60.0');
      when(
        () => api.getConfiguration(
          'http://satellite:1234@local.example:80/db/configurations/_all_docs?include_docs=true',
        ),
      ).thenAnswer(
        (_) async => const NetworkError<Map<String, dynamic>>(500, null),
      );

      stubVersions(alternative, minApk: '0.60.0');
      stubConfigurations(alternativeCouchDb, code: 'clone-code');

      final result = await buildRepository(
        mappings: const {'http://local.example': alternative},
      ).getMinApk(primary, '1234');

      expect(result, isA<ConfigurationSuccess>());
      final config = (result as ConfigurationSuccess).config;
      expect(config.code, 'clone-code');
      expect(config.isAlternativeUrl, isTrue);
      expect(config.alternativeUrl, alternative);
      // serverUrl stays the URL the user typed.
      expect(config.serverUrl, primary);
    });
  });

  group('getMinApk — failure', () {
    test('fails when the app is older than the server minimum', () async {
      const url = 'https://planet.example.org';
      stubVersions(url, minApk: '9.99.99');

      final result = await buildRepository().getMinApk(url, '1234');

      expect(result, isA<ConfigurationFailure>());
      expect(
        (result as ConfigurationFailure).reason,
        ConfigurationFailureReason.nationServerUnreachable,
      );
      // The configurations document must not be requested at all.
      verifyNever(
        () => api.getConfiguration(any(that: contains('configurations'))),
      );
    });

    test('fails when /versions is unreachable', () async {
      const url = 'http://local.example';
      when(() => api.getConfiguration('$url/versions')).thenAnswer(
        (_) async =>
            NetworkException<Map<String, dynamic>>(Exception('no route')),
      );

      final result = await buildRepository().getMinApk(url, '1234');

      expect(result, isA<ConfigurationFailure>());
      expect(
        (result as ConfigurationFailure).reason,
        ConfigurationFailureReason.localServerUnreachable,
      );
    });

    test('fails when /versions has no minapk field', () async {
      const url = 'https://planet.example.org';
      when(
        () => api.getConfiguration('$url/versions'),
      ).thenAnswer((_) async => const NetworkSuccess<Map<String, dynamic>>({}));

      expect(
        await buildRepository().getMinApk(url, '1234'),
        isA<ConfigurationFailure>(),
      );
    });

    test('fails when the configurations document list is empty', () async {
      const url = 'https://planet.example.org';
      const couchDbUrl = 'https://satellite:1234@planet.example.org:443';

      stubVersions(url, minApk: '0.60.0');
      when(
        () => api.getConfiguration(
          '$couchDbUrl/db/configurations/_all_docs?include_docs=true',
        ),
      ).thenAnswer(
        (_) async => const NetworkSuccess<Map<String, dynamic>>({'rows': []}),
      );

      expect(
        await buildRepository().getMinApk(url, '1234'),
        isA<ConfigurationFailure>(),
      );
    });
  });

  group('languageCodeFromName', () {
    test('maps the languages the Kotlin recognises', () {
      expect(ConfigurationsRepository.languageCodeFromName('English'), 'en');
      expect(ConfigurationsRepository.languageCodeFromName('Spanish'), 'es');
      expect(ConfigurationsRepository.languageCodeFromName('español'), 'es');
      expect(ConfigurationsRepository.languageCodeFromName('Somali'), 'so');
      expect(ConfigurationsRepository.languageCodeFromName('Nepali'), 'ne');
      expect(ConfigurationsRepository.languageCodeFromName('Arabic'), 'ar');
      expect(ConfigurationsRepository.languageCodeFromName('français'), 'fr');
    });

    test('returns null for an unknown language', () {
      expect(ConfigurationsRepository.languageCodeFromName('Klingon'), isNull);
    });
  });

  group('app version resolution for the minapk gate', () {
    // The comparison the whole configuration check hinges on: too low a version
    // and `getMinApk` fails with a generic unreachable-server error that says
    // nothing about the version being the cause.
    const url = 'https://planet.example.org';
    const couchDbUrl = 'https://satellite:1234@planet.example.org:443';

    test('the runtime version is preferred over the constant', () async {
      // The build is newer than the server minimum, the constant is older. Only
      // reading the runtime value lets this configure at all.
      stubVersions(url, minApk: '0.63.0');
      stubConfigurations(couchDbUrl);

      final result = await buildRepository(
        appVersion: '0.62.98',
        appVersionLookup: () async => '0.64.35',
      ).getMinApk(url, '1234');

      expect(result, isA<ConfigurationSuccess>());
    });

    test('the runtime version can also fail the gate', () async {
      // The inverse, so the test above is not passing for the wrong reason:
      // a genuinely too-old build is still rejected.
      stubVersions(url, minApk: '0.63.0');
      stubConfigurations(couchDbUrl);

      final result = await buildRepository(
        appVersion: '0.64.35',
        appVersionLookup: () async => '0.62.98',
      ).getMinApk(url, '1234');

      expect(result, isA<ConfigurationFailure>());
    });

    test('the 0.0.0 placeholder falls back to the constant', () async {
      // `package_info_plus` reports `0.0.0` when no manifest values are
      // available. Comparing that against any real `minapk` would fail the
      // check, so it must not displace the constant.
      stubVersions(url, minApk: '0.62.0');
      stubConfigurations(couchDbUrl);

      final result = await buildRepository(
        appVersion: '0.62.98',
        appVersionLookup: () async => '0.0.0',
      ).getMinApk(url, '1234');

      expect(result, isA<ConfigurationSuccess>());
    });

    test('an empty runtime version falls back to the constant', () async {
      stubVersions(url, minApk: '0.62.0');
      stubConfigurations(couchDbUrl);

      final result = await buildRepository(
        appVersion: '0.62.98',
        appVersionLookup: () async => '',
      ).getMinApk(url, '1234');

      expect(result, isA<ConfigurationSuccess>());
    });

    test('a throwing lookup falls back to the constant', () async {
      // A plugin failure must not cost the user their server configuration.
      stubVersions(url, minApk: '0.62.0');
      stubConfigurations(couchDbUrl);

      final result = await buildRepository(
        appVersion: '0.62.98',
        appVersionLookup: () async => throw StateError('no platform channel'),
      ).getMinApk(url, '1234');

      expect(result, isA<ConfigurationSuccess>());
    });

    test('no lookup at all uses the constant', () async {
      stubVersions(url, minApk: '0.62.0');
      stubConfigurations(couchDbUrl);

      expect(
        await buildRepository(appVersion: '0.62.98').getMinApk(url, '1234'),
        isA<ConfigurationSuccess>(),
      );
    });
  });
}
