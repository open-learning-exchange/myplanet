import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_providers.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

class _ConfiguredServerNotifier extends ServerConfigNotifier {
  _ConfiguredServerNotifier(this.config);
  final ServerConfig? config;
  @override
  ServerConfig? build() => config;
}

const _config = ServerConfig(
  serverUrl: 'https://planet.example.org',
  pin: '1234',
  couchDbUrl: 'https://satellite:1234@planet.example.org/db',
);

void main() {
  late _MockPlanetApi api;

  setUp(() {
    api = _MockPlanetApi();
    registerFallbackValue(Uri());
  });

  ProviderContainer container({ServerConfig? config}) => ProviderContainer(
    overrides: [
      planetApiProvider.overrideWithValue(api),
      serverConfigProvider.overrideWith(
        () => _ConfiguredServerNotifier(config),
      ),
    ],
  );

  group('profileImageProvider', () {
    test('fetches the attachment bytes over the satellite-auth url', () async {
      final c = container(config: _config);
      addTearDown(c.dispose);
      final expectedUrl =
          '${_config.couchDbUrl}/_users/'
          'org.couchdb.user%3Aada/ada.png';
      when(
        () => api.getBytes(expectedUrl, authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => const NetworkSuccess<List<int>>([1, 2, 3, 4]));

      final bytes = await c.read(
        profileImageProvider(
          const ProfileImageRequest(
            userId: 'org.couchdb.user:ada',
            imageName: 'ada.png',
          ),
        ).future,
      );

      expect(bytes, [1, 2, 3, 4]);
      final header =
          verify(
                () => api.getBytes(
                  expectedUrl,
                  authHeader: captureAny(named: 'authHeader'),
                ),
              ).captured.single
              as String;
      expect(header, startsWith('Basic '));
    });

    test('returns null when the server config is absent', () async {
      final c = container(config: null);
      addTearDown(c.dispose);

      final bytes = await c.read(
        profileImageProvider(
          const ProfileImageRequest(
            userId: 'org.couchdb.user:ada',
            imageName: 'ada.png',
          ),
        ).future,
      );

      expect(bytes, isNull);
      verifyNever(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      );
    });

    test(
      'returns null without a network call for a blank image name',
      () async {
        final c = container(config: _config);
        addTearDown(c.dispose);

        final bytes = await c.read(
          profileImageProvider(
            const ProfileImageRequest(
              userId: 'org.couchdb.user:ada',
              imageName: '',
            ),
          ).future,
        );

        expect(bytes, isNull);
        verifyNever(
          () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
        );
      },
    );

    test('falls back to null when the download errors', () async {
      final c = container(config: _config);
      addTearDown(c.dispose);
      when(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => const NetworkError<List<int>>(404, 'no doc'));

      final bytes = await c.read(
        profileImageProvider(
          const ProfileImageRequest(
            userId: 'org.couchdb.user:ada',
            imageName: 'ada.png',
          ),
        ).future,
      );

      expect(bytes, isNull);
    });
  });
}
