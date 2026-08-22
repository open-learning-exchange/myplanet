import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/ui/components/profile_avatar.dart';
import 'package:mocktail/mocktail.dart';

/// Pins the three render paths of [ProfileAvatar] — the synchronous initials
/// fallback (no photo name), the local-file path (a just-picked photo), and
/// the authenticated network fetch (a synced attachment name) — plus the
/// `displayName`/`_initials` helpers the profile screen shares.
void main() {
  late MockPlanetApi api;

  setUp(() {
    api = MockPlanetApi();
    registerFallbackValue(Uri.parse('https://x.example'));
  });

  UserRow user({
    String id = 'org.couchdb.user:ada',
    String? name = 'ada',
    String? firstName,
    String? lastName,
    String? middleName,
    String? userImage,
  }) => UserRow(
    id: id,
    name: name,
    rolesList: const ['learner'],
    userAdmin: false,
    joinDate: 0,
    firstName: firstName,
    lastName: lastName,
    middleName: middleName,
    userImage: userImage,
    isArchived: false,
    isUpdated: false,
  );

  Widget wrap(UserRow row, {List<Override> overrides = const []}) =>
      ProviderScope(
        overrides: [planetApiProvider.overrideWithValue(api), ...overrides],
        child: MaterialApp(
          home: Scaffold(body: ProfileAvatar(user: row, radius: 24)),
        ),
      );

  group('ProfileAvatar initials fallback', () {
    testWidgets('renders initials when there is no image name', (tester) async {
      await tester.pumpWidget(
        wrap(user(firstName: 'Ada', lastName: 'Lovelace')),
      );
      await tester.pumpAndSettle();

      expect(find.text('AL'), findsOneWidget);
      // No network fetch is attempted for a photoless user.
      verifyNever(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      );
    });

    testWidgets('renders the username initial when no name is set', (
      tester,
    ) async {
      await tester.pumpWidget(wrap(user(firstName: null, lastName: null)));
      await tester.pumpAndSettle();

      expect(find.text('A'), findsOneWidget);
    });

    testWidgets('renders MP for a guest with no name at all', (tester) async {
      await tester.pumpWidget(
        wrap(user(name: null, firstName: null, lastName: null)),
      );
      await tester.pumpAndSettle();

      expect(find.text('MP'), findsOneWidget);
    });
  });

  group('ProfileAvatar local file path', () {
    testWidgets('a local filesystem path bypasses the network fetch', (
      tester,
    ) async {
      // A path starting with / is what the image picker returns; it must be
      // shown from disk, never fetched through the authenticated attachment
      // endpoint.
      await tester.pumpWidget(wrap(user(userImage: '/data/cache/photo.jpg')));
      await tester.pump();

      verifyNever(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      );
    });

    testWidgets('a file:// uri bypasses the network fetch', (tester) async {
      await tester.pumpWidget(
        wrap(user(userImage: 'file:///data/cache/photo.jpg')),
      );
      await tester.pump();

      verifyNever(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      );
    });

    testWidgets('falls back to initials when the local file no longer exists', (
      tester,
    ) async {
      await tester.pumpWidget(
        wrap(
          user(userImage: '/no/such/file.jpg', firstName: 'Ada', lastName: 'L'),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('AL'), findsOneWidget);
      verifyNever(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      );
    });

    testWidgets('a bare attachment name is not treated as a local path', (
      tester,
    ) async {
      // A CouchDB attachment name (no leading / or file://) must go through the
      // network path, not be read from disk.
      when(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkError<List<int>>(404, 'not found'));

      await tester.pumpWidget(
        wrap(
          user(userImage: 'ada.png', firstName: 'Ada', lastName: 'L'),
          overrides: [serverConfigProvider.overrideWith(_TestConfig.new)],
        ),
      );
      await tester.pumpAndSettle();

      verify(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      ).called(1);
    });
  });

  group('ProfileAvatar network fetch', () {
    testWidgets('renders the fetched attachment bytes', (tester) async {
      final bytes = [
        0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
        0x49,
        0x48,
        0x44,
        0x52,
        0x00,
        0x00,
        0x00,
        0x01,
        0x00,
        0x00,
        0x00,
        0x01,
        0x08,
        0x06,
        0x00,
        0x00,
        0x00,
        0x1F,
        0x15,
        0xC4,
        0x89,
        0x00,
        0x00,
        0x00,
        0x0D,
        0x49,
        0x44,
        0x41,
        0x54,
        0x78,
        0x9C,
        0x63,
        0x00,
        0x01,
        0x00,
        0x00,
        0x05,
        0x00,
        0x01,
        0x0D,
        0x0A,
        0x2D,
        0xB4,
        0x00,
        0x00,
        0x00,
        0x00,
        0x49,
        0x45,
        0x4E,
        0x44,
        0xAE,
        0x42,
        0x60,
        0x82,
      ];
      when(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkSuccess<List<int>>(bytes));

      await tester.pumpWidget(
        wrap(
          user(userImage: 'ada.png'),
          overrides: [serverConfigProvider.overrideWith(_TestConfig.new)],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(Image), findsOneWidget);
      verify(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      ).called(1);
    });

    testWidgets('falls back to initials when the fetch errors', (tester) async {
      when(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkError<List<int>>(500, 'boom'));

      await tester.pumpWidget(
        wrap(
          user(userImage: 'ada.png', firstName: 'Ada', lastName: 'Lovelace'),
          overrides: [serverConfigProvider.overrideWith(_TestConfig.new)],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('AL'), findsOneWidget);
    });

    testWidgets('falls back to initials when there is no server config', (
      tester,
    ) async {
      await tester.pumpWidget(
        wrap(
          user(userImage: 'ada.png', firstName: 'Ada', lastName: 'Lovelace'),
          overrides: [serverConfigProvider.overrideWith(_NullConfig.new)],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('AL'), findsOneWidget);
      verifyNever(
        () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
      );
    });
  });

  group('displayName', () {
    test('joins first, middle, and last name', () {
      final row = user(
        firstName: 'Ada',
        middleName: 'Augusta',
        lastName: 'Lovelace',
      );
      expect(displayName(row), 'Ada Augusta Lovelace');
    });

    test('skips blank name parts', () {
      final row = user(
        firstName: 'Ada',
        middleName: '  ',
        lastName: 'Lovelace',
      );
      expect(displayName(row), 'Ada Lovelace');
    });

    test('falls back to the username when no name is set', () {
      final row = user(firstName: null, lastName: null, name: 'ada');
      expect(displayName(row), 'ada');
    });

    test('falls back to the generic label for a guest', () {
      final row = user(firstName: null, lastName: null, name: null);
      expect(displayName(row), 'myPlanet learner');
    });
  });
}

class _TestConfig extends ServerConfigNotifier {
  @override
  ServerConfig? build() => const ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );
}

class _NullConfig extends ServerConfigNotifier {
  @override
  ServerConfig? build() => null;
}

class MockPlanetApi extends Mock implements PlanetApi {}
