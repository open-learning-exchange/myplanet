import 'package:drift/drift.dart' hide isNotNull, isNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/user_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// Credentials generated independently with Python's `hashlib.pbkdf2_hmac`
/// using the parameters `AndroidDecrypter` pins (SHA1, 10 iterations, 20 bytes).
const _password = 'correct-horse';
const _salt = 'a3f1c9d2e5b74806a1c2d3e4f5061728';
const _derivedKey = 'ddb696f6f34c21547a45f8034c6e41daf63b3ce3';

void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late UserRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  final userDocUrl = '${config.couchDbUrl}/db/_users/org.couchdb.user:ada';

  Map<String, dynamic> userDoc({
    String name = 'ada',
    List<String> roles = const ['learner'],
    bool isUserAdmin = false,
  }) {
    return {
      '_id': 'org.couchdb.user:ada',
      '_rev': '1-abc',
      'name': name,
      'roles': roles,
      'isUserAdmin': isUserAdmin,
      'derived_key': _derivedKey,
      'salt': _salt,
      'password_scheme': 'pbkdf2',
      'iterations': '10',
      'firstName': 'Ada',
      'lastName': 'Lovelace',
      'planetCode': 'gt',
    };
  }

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = UserRepository(api, db.userDao);
  });

  tearDown(() => db.close());

  group('loginOnline', () {
    test('authenticates and caches the user for offline use', () async {
      when(
        () =>
            api.getJsonObject(userDocUrl, authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>(userDoc()),
      );

      final result = await repository.loginOnline(
        config: config,
        username: 'ada',
        password: _password,
      );

      expect(result, isA<LoginSuccess>());
      expect((result as LoginSuccess).user.name, 'ada');
      expect(result.user.firstName, 'Ada');
      expect(result.user.rolesList, ['learner']);

      // The row is now local, so the offline path works with no network.
      final cached = await db.userDao.getByName('ada');
      expect(cached, isNotNull);
      expect(cached!.derivedKey, _derivedKey);
    });

    test('sends a Basic auth header derived from the credentials', () async {
      when(
        () =>
            api.getJsonObject(userDocUrl, authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>(userDoc()),
      );

      await repository.loginOnline(
        config: config,
        username: 'ada',
        password: _password,
      );

      final captured =
          verify(
                () => api.getJsonObject(
                  userDocUrl,
                  authHeader: captureAny(named: 'authHeader'),
                ),
              ).captured.single
              as String;
      expect(captured, startsWith('Basic '));
    });

    test(
      'rejects a wrong password even when the server returns the doc',
      () async {
        when(
          () => api.getJsonObject(
            userDocUrl,
            authHeader: any(named: 'authHeader'),
          ),
        ).thenAnswer(
          (_) async => NetworkSuccess<Map<String, dynamic>>(userDoc()),
        );

        final result = await repository.loginOnline(
          config: config,
          username: 'ada',
          password: 'wrong',
        );

        expect(result, isA<LoginFailure>());
        expect(
          (result as LoginFailure).reason,
          LoginFailureReason.invalidCredentials,
        );
        // Nothing should be cached for a failed attempt.
        expect(await db.userDao.getByName('ada'), isNull);
      },
    );

    test('reports missing auth data when the doc has no derived_key', () async {
      when(
        () =>
            api.getJsonObject(userDocUrl, authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async =>
            const NetworkSuccess<Map<String, dynamic>>({'name': 'ada'}),
      );

      final result = await repository.loginOnline(
        config: config,
        username: 'ada',
        password: _password,
      );

      expect(
        (result as LoginFailure).reason,
        LoginFailureReason.missingAuthData,
      );
    });

    test('maps HTTP status codes onto failure reasons', () async {
      Future<LoginFailureReason> reasonFor(int status) async {
        when(
          () => api.getJsonObject(
            userDocUrl,
            authHeader: any(named: 'authHeader'),
          ),
        ).thenAnswer(
          (_) async => NetworkError<Map<String, dynamic>>(status, null),
        );
        final result = await repository.loginOnline(
          config: config,
          username: 'ada',
          password: _password,
        );
        return (result as LoginFailure).reason;
      }

      expect(await reasonFor(401), LoginFailureReason.invalidCredentials);
      expect(await reasonFor(404), LoginFailureReason.userNotFound);
      expect(await reasonFor(500), LoginFailureReason.serverError);
    });

    test('reports a network failure when the request never lands', () async {
      when(
        () =>
            api.getJsonObject(userDocUrl, authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async =>
            NetworkException<Map<String, dynamic>>(Exception('offline')),
      );

      final result = await repository.loginOnline(
        config: config,
        username: 'ada',
        password: _password,
      );

      expect((result as LoginFailure).reason, LoginFailureReason.network);
    });

    test('rejects empty credentials without calling the API', () async {
      final result = await repository.loginOnline(
        config: config,
        username: '  ',
        password: '',
      );

      expect(
        (result as LoginFailure).reason,
        LoginFailureReason.emptyCredentials,
      );
      verifyNever(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      );
    });

    test('refuses a non-manager in manager mode', () async {
      when(
        () =>
            api.getJsonObject(userDocUrl, authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>(userDoc()),
      );

      final result = await repository.loginOnline(
        config: config,
        username: 'ada',
        password: _password,
        isManagerMode: true,
      );

      expect((result as LoginFailure).reason, LoginFailureReason.notAManager);
    });

    test('admits a manager in manager mode', () async {
      when(
        () =>
            api.getJsonObject(userDocUrl, authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>(
          userDoc(roles: const ['manager']),
        ),
      );

      final result = await repository.loginOnline(
        config: config,
        username: 'ada',
        password: _password,
        isManagerMode: true,
      );

      expect(result, isA<LoginSuccess>());
    });
  });

  group('loginOffline', () {
    Future<void> seedSyncedUser() async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'org.couchdb.user:ada',
          couchId: const Value('org.couchdb.user:ada'),
          name: const Value('ada'),
          derivedKey: const Value(_derivedKey),
          salt: const Value(_salt),
        ),
      );
    }

    test('authenticates a synced user with no network at all', () async {
      await seedSyncedUser();

      final result = await repository.loginOffline(
        username: 'ada',
        password: _password,
      );

      expect(result, isA<LoginSuccess>());
      verifyZeroInteractions(api);
    });

    test('rejects a wrong password offline', () async {
      await seedSyncedUser();

      final result = await repository.loginOffline(
        username: 'ada',
        password: 'wrong',
      );

      expect(
        (result as LoginFailure).reason,
        LoginFailureReason.invalidCredentials,
      );
    });

    test('reports an unknown user', () async {
      final result = await repository.loginOffline(
        username: 'nobody',
        password: _password,
      );

      expect((result as LoginFailure).reason, LoginFailureReason.userNotFound);
    });

    /// Guest accounts have an empty `_id` and a plaintext password.
    test('authenticates a guest against the plaintext password', () async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'guest-1',
          name: const Value('guest'),
          password: const Value('letmein'),
        ),
      );

      expect(
        await repository.loginOffline(username: 'guest', password: 'letmein'),
        isA<LoginSuccess>(),
      );
      expect(
        await repository.loginOffline(username: 'guest', password: 'nope'),
        isA<LoginFailure>(),
      );
    });
  });

  group('saved users', () {
    test('lists non-archived users and counts them', () async {
      expect(await repository.hasAnyUser(), isFalse);

      await db.userDao.upsert(
        UsersCompanion.insert(id: 'u1', name: const Value('ada')),
      );
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'u2',
          name: const Value('archived'),
          isArchived: const Value(true),
        ),
      );

      expect(await repository.hasAnyUser(), isTrue);
      final saved = await repository.getSavedUsers();
      expect(saved.map((u) => u.name), ['ada']);
    });
  });
}
