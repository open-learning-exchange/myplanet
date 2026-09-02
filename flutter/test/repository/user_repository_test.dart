import 'dart:convert';

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
    registerFallbackValue(<String, dynamic>{});
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

    /// `name` has no unique constraint, and two planets can hold accounts with
    /// the same name. This used to throw StateError and take down login.
    test('does not crash when two users share a name', () async {
      await seedSyncedUser();
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'org.couchdb.user:ada-other',
          couchId: const Value('org.couchdb.user:ada-other'),
          name: const Value('ada'),
          derivedKey: const Value(_derivedKey),
          salt: const Value(_salt),
        ),
      );

      final result = await repository.loginOffline(
        username: 'ada',
        password: _password,
      );

      expect(result, isA<LoginSuccess>());
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

    test('hasAnyUser returns false on an empty database', () async {
      expect(await repository.hasAnyUser(), isFalse);
    });

    test('getSavedUsers returns an empty list on an empty database', () async {
      expect(await repository.getSavedUsers(), isEmpty);
    });

    test('getSavedUsers excludes all archived users', () async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'a1',
          name: const Value('archived1'),
          isArchived: const Value(true),
        ),
      );
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'a2',
          name: const Value('archived2'),
          isArchived: const Value(true),
        ),
      );

      expect(await repository.getSavedUsers(), isEmpty);
    });

    test('getSavedUsers includes multiple non-archived users', () async {
      await db.userDao.upsert(
        UsersCompanion.insert(id: 'u1', name: const Value('ada')),
      );
      await db.userDao.upsert(
        UsersCompanion.insert(id: 'u2', name: const Value('bob')),
      );

      final saved = await repository.getSavedUsers();
      expect(saved.length, 2);
      expect(saved.map((u) => u.name).toSet(), {'ada', 'bob'});
    });
  });

  group('uploadNewUser', () {
    Future<void> seedLocalUser({String id = '12345'}) => db.userDao.upsert(
      UsersCompanion.insert(
        id: id,
        name: const Value('newmember'),
        password: const Value('secret'),
      ),
    );

    test(
      'PUTs the user doc and stores the server-assigned id and rev',
      () async {
        await seedLocalUser();

        when(() => api.putJsonObject(any(), any())).thenAnswer(
          (_) async => NetworkSuccess({
            'id': 'org.couchdb.user:newmember',
            'rev': '1-abc',
            'ok': true,
          }),
        );
        when(
          () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
        ).thenAnswer(
          (_) async => NetworkSuccess({
            '_id': 'org.couchdb.user:newmember',
            '_rev': '1-abc',
            'name': 'newmember',
            'derived_key': 'abc',
            'salt': 'def',
            'password_scheme': 'pbkdf2',
            'iterations': '10',
          }),
        );

        final result = await repository.uploadNewUser(
          localId: '12345',
          config: config,
          username: 'newmember',
          password: 'secret',
        );

        expect(result, isTrue);
        final saved = await db.userDao.getById('12345');
        expect(saved?.couchId, 'org.couchdb.user:newmember');
        expect(saved?.rev, '1-abc');
        expect(saved?.derivedKey, 'abc');
        expect(saved?.salt, 'def');
        expect(saved?.passwordScheme, 'pbkdf2');
        expect(saved?.iterations, '10');
      },
    );

    test('returns false when the PUT fails', () async {
      await seedLocalUser();

      when(
        () => api.putJsonObject(any(), any()),
      ).thenAnswer((_) async => NetworkError(409, 'conflict'));

      final result = await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newmember',
        password: 'secret',
      );

      expect(result, isFalse);
      // The local row is untouched — no couchId assigned.
      final saved = await db.userDao.getById('12345');
      expect(saved?.couchId, isNull);
    });

    test('returns false when the PUT succeeds but yields no id', () async {
      await seedLocalUser();

      when(
        () => api.putJsonObject(any(), any()),
      ).thenAnswer((_) async => NetworkSuccess({'ok': true}));

      final result = await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newmember',
        password: 'secret',
      );

      expect(result, isFalse);
    });

    test('returns true even when the security-data fetch fails', () async {
      // The user was created on the server; the security-data GET is a
      // best-effort follow-up. A failure there must not undo the success.
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newmember',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkError(401, 'unauthorized'));

      final result = await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newmember',
        password: 'secret',
      );

      expect(result, isTrue);
      final saved = await db.userDao.getById('12345');
      expect(saved?.couchId, 'org.couchdb.user:newmember');
      expect(saved?.rev, '1-abc');
      // Security fields stay null — login will fall back to a server fetch.
      expect(saved?.derivedKey, isNull);
      expect(saved?.salt, isNull);
    });

    test('returns false on a transport failure during the PUT', () async {
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkException(Exception('connection refused')),
      );

      final result = await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newmember',
        password: 'secret',
      );

      expect(result, isFalse);
    });

    test('sends the user doc to the CouchDB _users endpoint', () async {
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newmember',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkSuccess({}));

      await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newmember',
        password: 'secret',
      );

      final captured = verify(
        () => api.putJsonObject(captureAny(), captureAny()),
      ).captured;
      final url = captured[0] as String;
      expect(url, contains('/_users/org.couchdb.user:newmember'));
    });

    test('fetches the created doc with the user own credentials', () async {
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newmember',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkSuccess({}));

      await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newmember',
        password: 'secret',
      );

      final authHeader =
          verify(
                () => api.getJsonObject(
                  any(),
                  authHeader: captureAny(named: 'authHeader'),
                ),
              ).captured.single
              as String;
      // The follow-up GET uses the new member's own credentials, not the
      // satellite account, so they can read back their own security data.
      expect(authHeader, contains('Basic'));
    });

    test('encodes a username containing a space in the PUT URL', () async {
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:new%20member',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkSuccess({}));

      await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'new member',
        password: 'secret',
      );

      final url =
          verify(
                () => api.putJsonObject(
                  captureAny(that: contains('_users')),
                  any(),
                ),
              ).captured.single
              as String;
      expect(url, contains('new%20member'));
    });

    test('stores the security fields when the fetch returns them', () async {
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newuser',
          'rev': '2-def',
          'ok': true,
        }),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess({
          'derived_key': 'abc123',
          'salt': 's4lt',
          'password_scheme': 'pbkdf2',
          'iterations': '10000',
        }),
      );

      await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newuser',
        password: 'secret',
      );

      final user = await db.userDao.getById('12345');
      expect(user?.couchId, 'org.couchdb.user:newuser');
      expect(user?.rev, '2-def');
      expect(user?.derivedKey, 'abc123');
      expect(user?.salt, 's4lt');
      expect(user?.passwordScheme, 'pbkdf2');
      expect(user?.iterations, '10000');
    });

    test(
      'does not store security fields when the fetch returns an empty doc',
      () async {
        await seedLocalUser();

        when(() => api.putJsonObject(any(), any())).thenAnswer(
          (_) async => NetworkSuccess({
            'id': 'org.couchdb.user:newuser',
            'rev': '1-abc',
            'ok': true,
          }),
        );
        when(
          () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
        ).thenAnswer((_) async => NetworkSuccess({}));

        await repository.uploadNewUser(
          localId: '12345',
          config: config,
          username: 'newuser',
          password: 'secret',
        );

        final user = await db.userDao.getById('12345');
        expect(user?.couchId, 'org.couchdb.user:newuser');
        expect(user?.derivedKey, isNull);
        expect(user?.salt, isNull);
      },
    );

    test('uses the username and password for the fetch auth header', () async {
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newuser',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkSuccess({}));

      await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newuser',
        password: 'secret',
      );

      final header =
          verify(
                () => api.getJsonObject(
                  any(),
                  authHeader: captureAny(named: 'authHeader'),
                ),
              ).captured.single
              as String;
      // Basic auth header is base64('newuser:secret').
      expect(header, startsWith('Basic '));
      final decoded = String.fromCharCodes(base64Decode(header.substring(6)));
      expect(decoded, 'newuser:secret');
    });

    test('fetches the doc from the _users database by server id', () async {
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newuser',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkSuccess({}));

      await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newuser',
        password: 'secret',
      );

      final fetchUrl =
          verify(
                () => api.getJsonObject(
                  captureAny(),
                  authHeader: any(named: 'authHeader'),
                ),
              ).captured.single
              as String;
      expect(fetchUrl, contains('/_users/org.couchdb.user:newuser'));
    });

    test('returns true when both PUT and fetch succeed', () async {
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newuser',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkSuccess({}));

      final result = await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newuser',
        password: 'secret',
      );

      expect(result, isTrue);
    });

    test('returns true even when the fetch throws an exception', () async {
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newuser',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkException(Exception('network')));

      final result = await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newuser',
        password: 'secret',
      );

      expect(result, isTrue);
      // The local row still has the server id/rev from the PUT.
      final user = await db.userDao.getById('12345');
      expect(user?.couchId, 'org.couchdb.user:newuser');
    });

    test(
      'stores the server-assigned id and rev after a successful PUT',
      () async {
        await seedLocalUser();

        when(() => api.putJsonObject(any(), any())).thenAnswer(
          (_) async => NetworkSuccess({
            'id': 'org.couchdb.user:newuser',
            'rev': '1-abc',
            'ok': true,
          }),
        );
        when(
          () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
        ).thenAnswer((_) async => NetworkSuccess({}));

        await repository.uploadNewUser(
          localId: '12345',
          config: config,
          username: 'newuser',
          password: 'secret',
        );

        final user = await db.userDao.getById('12345');
        expect(user?.couchId, 'org.couchdb.user:newuser');
        expect(user?.rev, '1-abc');
      },
    );

    test('returns false when the server returns an empty id', () async {
      await seedLocalUser();

      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({'id': '', 'rev': '1', 'ok': true}),
      );

      final result = await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newuser',
        password: 'secret',
      );

      expect(result, isFalse);
    });
  });

  group('validateUsername', () {
    const messages = UsernameValidationMessages(
      cannotBeEmpty: 'empty',
      invalid: 'invalid',
      mustStartWithLetterOrNumber: 'must-start',
      onlyLettersNumbers: 'only-letters',
      taken: 'taken',
    );

    test('rejects an empty username', () async {
      expect(await repository.validateUsername('', messages), 'empty');
    });

    test('rejects a username containing a space', () async {
      expect(await repository.validateUsername('a b', messages), 'invalid');
    });

    test('rejects a first character that is not a letter or digit', () async {
      expect(await repository.validateUsername('_abc', messages), 'must-start');
      expect(await repository.validateUsername('-abc', messages), 'must-start');
      expect(await repository.validateUsername('.abc', messages), 'must-start');
    });

    test('rejects characters outside a-z, 0-9, _, ., -', () async {
      expect(
        await repository.validateUsername('café', messages),
        'only-letters',
      );
      expect(
        await repository.validateUsername('a@b', messages),
        'only-letters',
      );
      expect(
        await repository.validateUsername('a*b', messages),
        'only-letters',
      );
    });

    test('accepts a free username with allowed characters', () async {
      expect(await repository.validateUsername('ada', messages), isNull);
      expect(await repository.validateUsername('a.b-c_2', messages), isNull);
    });

    test('flags a username already taken by a non-guest user', () async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'org.couchdb.user:ada',
          couchId: const Value('org.couchdb.user:ada'),
          name: const Value('ada'),
        ),
      );
      expect(await repository.validateUsername('ada', messages), 'taken');
    });

    test('lets a guest user re-take their own name', () async {
      // A guest row's `_id` starts with `guest`, so the taken-check skips it —
      // the same exclusion `UserRepositoryImpl.validateUsername` applies.
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'guest_ada',
          couchId: const Value('guest_ada'),
          name: const Value('ada'),
        ),
      );
      expect(await repository.validateUsername('ada', messages), isNull);
    });
  });

  // ── saveKeyIv — port of UserRepositoryImpl.saveKeyIv ────────────────────
  //
  // The upload direction Phase 61's sync-in reads back: publish the health
  // AES key/IV to the user's per-user database so records written on this
  // device decrypt on another. The expected table names below are pinned to
  // the Kotlin `Utilities.toHex` output (see text_utils_test).

  group('saveKeyIv', () {
    Future<void> seedUser({String? key, String? iv}) => db.userDao.upsert(
      UsersCompanion.insert(
        id: '12345',
        name: const Value('newmember'),
        planetCode: const Value('earth'),
        key: key == null ? const Value.absent() : Value(key),
        iv: iv == null ? const Value.absent() : Value(iv),
      ),
    );

    // `earth` → 6561727468, `newmember` → 6e65776d656d626572.
    const table = 'userdb-6561727468-6e65776d656d626572';
    // `UrlUtils.dbUrl(config)` appends `/db` to the credentialed root, as
    // Kotlin's `UrlUtils.getUrl()` does.
    final dbUrl = '${config.couchDbUrl}/db';
    final tableUrl = '$dbUrl/$table';
    final secUrl = '$tableUrl/_security';

    void stubSuccessPost() {
      when(
        () => api.putJsonObject(any(), any()),
      ).thenAnswer((_) async => const NetworkError(412, 'db exists'));
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess({'ok': true, 'id': 'k1', 'rev': '1'}),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess({
          'members': {'roles': <String>[]},
        }),
      );
      when(
        () => api.putJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async => NetworkSuccess({'ok': true}));
    }

    test(
      'generates a key/IV, POSTs them, grants the health role, and records them',
      () async {
        await seedUser();
        stubSuccessPost();

        await repository.saveKeyIv(
          localId: '12345',
          config: config,
          username: 'newmember',
          password: 'secret',
        );

        // The POST carries key/iv/createdOn to the per-user database.
        final postCapture = verify(
          () => api.postJsonObject(
            captureAny(),
            captureAny(),
            authHeader: any(named: 'authHeader'),
          ),
        ).captured;
        expect(postCapture[0], tableUrl);
        final body = postCapture[1] as Map<String, dynamic>;
        expect(body.containsKey('key'), isTrue);
        expect(body.containsKey('iv'), isTrue);
        expect(body['createdOn'], isA<int>());

        // The health role was PUT onto _security.
        final secCapture = verify(
          () => api.putJsonObject(
            secUrl,
            captureAny(),
            authHeader: any(named: 'authHeader'),
          ),
        ).captured;
        final security = secCapture.single as Map<String, dynamic>;
        final roles = (security['members'] as Map)['roles'] as List;
        expect(roles, contains('health'));

        // The generated key/IV landed on the row.
        final user = await db.userDao.getById('12345');
        expect(user?.key, isNotNull);
        expect(user?.iv, isNotNull);
        expect(body['key'], user?.key);
        expect(body['iv'], user?.iv);
      },
    );

    test('reuses a stored key/IV instead of rotating them', () async {
      await seedUser(key: 'EXISTING-KEY', iv: 'EXISTING-IV');
      stubSuccessPost();

      await repository.saveKeyIv(
        localId: '12345',
        config: config,
        username: 'newmember',
        password: 'secret',
      );

      final postCapture =
          verify(
                () => api.postJsonObject(
                  any(),
                  captureAny(),
                  authHeader: any(named: 'authHeader'),
                ),
              ).captured.single
              as Map<String, dynamic>;
      expect(postCapture['key'], 'EXISTING-KEY');
      expect(postCapture['iv'], 'EXISTING-IV');
      final user = await db.userDao.getById('12345');
      expect(user?.key, 'EXISTING-KEY');
      expect(user?.iv, 'EXISTING-IV');
    });

    test(
      'authenticates the POST and _security calls as the user, not the satellite',
      () async {
        await seedUser();
        stubSuccessPost();

        await repository.saveKeyIv(
          localId: '12345',
          config: config,
          username: 'newmember',
          password: 'secret',
        );

        final postHeader =
            verify(
                  () => api.postJsonObject(
                    any(),
                    any(),
                    authHeader: captureAny(named: 'authHeader'),
                  ),
                ).captured.single
                as String;
        final secHeader =
            verify(
                  () => api.getJsonObject(
                    any(),
                    authHeader: captureAny(named: 'authHeader'),
                  ),
                ).captured.single
                as String;
        // Both are Basic auth for newmember:secret, not satellite:1234.
        for (final header in [postHeader, secHeader]) {
          expect(header, startsWith('Basic '));
          final decoded = String.fromCharCodes(
            base64Decode(header.substring(6)),
          );
          expect(decoded, 'newmember:secret');
        }
      },
    );

    test('retries the POST up to 3 times, then succeeds', () async {
      await seedUser();
      var calls = 0;
      when(
        () => api.putJsonObject(any(), any()),
      ).thenAnswer((_) async => const NetworkError(412, 'db exists'));
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async {
        calls++;
        if (calls < 3) return const NetworkError(500, 'down');
        return NetworkSuccess({'ok': true, 'id': 'k1', 'rev': '1'});
      });
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess({
          'members': {'roles': <String>[]},
        }),
      );
      when(
        () => api.putJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async => NetworkSuccess({'ok': true}));

      await repository.saveKeyIv(
        localId: '12345',
        config: config,
        username: 'newmember',
        password: 'secret',
      );

      expect(calls, 3);
      final user = await db.userDao.getById('12345');
      expect(user?.key, isNotNull);
    });

    test('throws after 3 failed POSTs', () async {
      await seedUser();
      when(
        () => api.putJsonObject(any(), any()),
      ).thenAnswer((_) async => const NetworkError(412, 'db exists'));
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async => const NetworkError(500, 'down'));

      await expectLater(
        repository.saveKeyIv(
          localId: '12345',
          config: config,
          username: 'newmember',
          password: 'secret',
        ),
        throwsA(isA<Exception>()),
      );
      // Nothing was recorded.
      final user = await db.userDao.getById('12345');
      expect(user?.key, isNull);
    });

    test(
      'a _security GET failure is swallowed but the key is still recorded',
      () async {
        await seedUser();
        when(
          () => api.putJsonObject(any(), any()),
        ).thenAnswer((_) async => const NetworkError(412, 'db exists'));
        when(
          () => api.postJsonObject(
            any(),
            any(),
            authHeader: any(named: 'authHeader'),
          ),
        ).thenAnswer(
          (_) async => NetworkSuccess({'ok': true, 'id': 'k1', 'rev': '1'}),
        );
        // _security GET fails.
        when(
          () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
        ).thenAnswer((_) async => const NetworkError(403, 'forbidden'));

        await repository.saveKeyIv(
          localId: '12345',
          config: config,
          username: 'newmember',
          password: 'secret',
        );

        // The key/IV were still recorded locally.
        final user = await db.userDao.getById('12345');
        expect(user?.key, isNotNull);
        expect(user?.iv, isNotNull);
        // And the _security PUT was never attempted.
        verifyNever(
          () => api.putJsonObject(
            secUrl,
            any(),
            authHeader: any(named: 'authHeader'),
          ),
        );
      },
    );

    test('uploadNewUser calls saveKeyIv after a successful creation', () async {
      await seedUser();
      // The _users PUT (no authHeader) — stubbed with a URL guard so the
      // db-creation PUT does not collide with it.
      when(
        () => api.putJsonObject(any(that: contains('_users')), any()),
      ).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newmember',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      // The db-creation PUT (no authHeader) — best-effort, ignored.
      when(
        () => api.putJsonObject(any(that: contains('userdb-')), any()),
      ).thenAnswer((_) async => const NetworkError(412, 'db exists'));
      // The _security PUT (authHeader).
      when(
        () => api.putJsonObject(
          any(that: contains('_security')),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async => NetworkSuccess({'ok': true}));
      // The _users fetch and the _security GET both go through getJsonObject.
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess({
          'members': {'roles': <String>[]},
        }),
      );
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess({'ok': true, 'id': 'k1', 'rev': '1'}),
      );

      final result = await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newmember',
        password: 'secret',
      );

      expect(result, isTrue);
      // saveKeyIv ran: the row carries a health key.
      final user = await db.userDao.getById('12345');
      expect(user?.key, isNotNull);
    });

    test('uploadNewUser still succeeds when saveKeyIv throws', () async {
      await seedUser();
      when(
        () => api.putJsonObject(any(that: contains('_users')), any()),
      ).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newmember',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      when(
        () => api.putJsonObject(any(that: contains('userdb-')), any()),
      ).thenAnswer((_) async => const NetworkError(412, 'db exists'));
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkSuccess({}));
      // Every POST fails — saveKeyIv throws after 3 attempts.
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async => const NetworkError(500, 'down'));

      final result = await repository.uploadNewUser(
        localId: '12345',
        config: config,
        username: 'newmember',
        password: 'secret',
      );

      // The account was still created; the key just stays device-local.
      expect(result, isTrue);
      final user = await db.userDao.getById('12345');
      expect(user?.key, isNull);
    });
  });
  group('one member, one row', () {
    // `become_member_screen` writes the account with a locally-minted
    // `'<millis>'` id and no `couchId`; the health screens mint `key`/`iv` on
    // that row; the user uploader then records the server-assigned `couchId`
    // on the same row (`UserDao.updateUserSecurityData`). Every test here
    // starts from that state and then signs the member in online, which is
    // the transition `buildUserFromJson`'s identity rule exists to survive.
    const localId = '1700000000000';

    Future<void> seedLocallyRegisteredMember({
      String? firstName = 'Ada',
      String? userImage,
      bool isUpdated = false,
    }) async {
      await db.userDao.upsert(
        UsersCompanion(
          id: const Value(localId),
          name: const Value('ada'),
          firstName: Value(firstName),
          lastName: const Value('Lovelace'),
          phoneNumber: const Value('555-0100'),
          level: const Value('intermediate'),
          language: const Value('en'),
          birthPlace: const Value('London'),
          joinDate: const Value(1600000000000),
          userImage: Value(userImage),
          isUpdated: Value(isUpdated),
          key: const Value('the-key'),
          iv: const Value('the-iv'),
        ),
      );
      await db.userDao.updateUserSecurityData(
        localId: localId,
        couchId: 'org.couchdb.user:ada',
        rev: '1-abc',
        passwordScheme: 'pbkdf2',
        derivedKey: _derivedKey,
        salt: _salt,
        iterations: '10',
      );
    }

    void serverReturns(Map<String, dynamic> doc) {
      when(
        () =>
            api.getJsonObject(userDocUrl, authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkSuccess<Map<String, dynamic>>(doc));
    }

    Future<LoginResult> signIn() => repository.loginOnline(
      config: config,
      username: 'ada',
      password: _password,
    );

    test(
      'the online login reuses the local row instead of adding one',
      () async {
        await seedLocallyRegisteredMember();
        serverReturns(userDoc());

        expect(await signIn(), isA<LoginSuccess>());

        // `buildUserFromJson` resolves the row through `getUserByAnyId(_id)` —
        // `WHERE id = :id OR _id = :id` — and `applyJsonToUser` reassigns `id`
        // only when it is blank, so the member keeps the one row they had.
        final rows = await db.userDao.getAllUsers();
        expect(
          rows,
          hasLength(1),
          reason:
              'the CouchDB _id already resolves to this member through '
              'their couchId; keying a second row on it duplicates the member',
        );
        expect(rows.single.id, localId);
        expect(rows.single.couchId, 'org.couchdb.user:ada');
        // The deterministic, user-visible half: every list built from the users
        // table — the login account picker, the health patient picker, the
        // survey recipient list — showed the member twice.
        expect(await db.userDao.getSavedUsers(), hasLength(1));
      },
    );

    test('the health key and iv survive the transition', () async {
      await seedLocallyRegisteredMember();
      serverReturns(userDoc());

      await signIn();

      // The consequence that makes this more than a duplicate name: health
      // records are encrypted with the row's `key`/`iv`, which live only on
      // this device. A row keyed on the CouchDB `_id` carries neither, and
      // `getById(couchId)` matches it as readily as the real one.
      for (final row in await db.userDao.getAllUsers()) {
        expect(
          row.key,
          'the-key',
          reason:
              'a row that answers to this member carries no health key, '
              'so the records it encrypted cannot be decrypted',
        );
        expect(row.iv, 'the-iv');
      }
      final resolved = await db.userDao.getById('org.couchdb.user:ada');
      expect(resolved?.id, localId);
      expect(resolved?.key, 'the-key');
    });

    test('the session is the row the member already had', () async {
      await seedLocallyRegisteredMember();
      serverReturns(userDoc());

      final result = await signIn();

      // `upsertUser` returns `userDao.getById(entity.id)`, so the signed-in
      // user is the row the document was applied to. Re-reading by name is
      // scan-ordered and picks by luck once two rows carry the name.
      expect((result as LoginSuccess).user.id, localId);
      expect(result.user.key, 'the-key');
    });

    test('a field the document omits keeps its stored value', () async {
      await seedLocallyRegisteredMember();
      // `applyJsonToUser` guards these with
      // `if (new.isNotEmpty() || old.isNullOrEmpty()) field = new`, so a
      // document that omits one leaves the stored value alone. Writing every
      // field unconditionally nulls a profile the member filled in offline.
      final doc = userDoc()
        ..remove('firstName')
        ..remove('lastName')
        ..remove('phoneNumber')
        ..remove('level')
        ..remove('language')
        ..remove('birthPlace')
        ..remove('joinDate');
      serverReturns(doc);

      await signIn();

      final row = (await db.userDao.getAllUsers()).single;
      expect(row.firstName, 'Ada');
      expect(row.lastName, 'Lovelace');
      expect(row.phoneNumber, '555-0100');
      expect(row.level, 'intermediate');
      expect(row.language, 'en');
      expect(row.birthPlace, 'London');
      expect(row.joinDate, 1600000000000);
    });

    test('a non-empty document field still wins', () async {
      await seedLocallyRegisteredMember();
      serverReturns(
        userDoc()
          ..['firstName'] = 'Augusta'
          ..['joinDate'] = 1700000000001,
      );

      await signIn();

      final row = (await db.userDao.getAllUsers()).single;
      expect(row.firstName, 'Augusta');
      expect(row.joinDate, 1700000000001);
    });

    test(
      'an unsynced profile photo survives a document without attachments',
      () async {
        await seedLocallyRegisteredMember(
          userImage: '/tmp/queued-photo.jpg',
          isUpdated: true,
        );
        serverReturns(userDoc());

        await signIn();

        // `addImageUrl` only touches `userImage` when the document carries a
        // non-empty `_attachments`. The photo here is a local path the user
        // uploader has not sent yet; nulling it loses the photo outright.
        final row = (await db.userDao.getAllUsers()).single;
        expect(row.userImage, '/tmp/queued-photo.jpg');
      },
    );

    test(
      'an attachment in the document does replace the stored name',
      () async {
        await seedLocallyRegisteredMember(userImage: 'old.jpg');
        serverReturns(
          userDoc()
            ..['_attachments'] = {
              'img': {'content_type': 'image/jpeg'},
            },
        );

        await signIn();

        expect((await db.userDao.getAllUsers()).single.userImage, 'img');
      },
    );

    test('a rejected manager login caches nothing', () async {
      // `checkManagerAndInsert` (`LoginSyncManager.kt:167-175`) tests
      // `isManager(jsonDoc)` and returns **before** `saveUser`, so a
      // manager-mode sign-in by a non-manager leaves the table untouched.
      // Writing the row first and rejecting afterwards cached an account the
      // Kotlin declines to cache.
      serverReturns(userDoc());

      final result = await repository.loginOnline(
        config: config,
        username: 'ada',
        password: _password,
        isManagerMode: true,
      );

      expect((result as LoginFailure).reason, LoginFailureReason.notAManager);
      expect(await db.userDao.getAllUsers(), isEmpty);
    });

    test('a first sign-in still keys the row on the document id', () async {
      // No local row: `buildUserFromJson` falls through to
      // `UserEntity().apply { this.id = id }`, so the CouchDB `_id` is the
      // row's id. This is the case the pre-fix mapper handled correctly and
      // it has to keep working.
      serverReturns(userDoc());

      final result = await signIn();

      final rows = await db.userDao.getAllUsers();
      expect(rows, hasLength(1));
      expect(rows.single.id, 'org.couchdb.user:ada');
      expect((result as LoginSuccess).user.id, 'org.couchdb.user:ada');
    });
  });
}
