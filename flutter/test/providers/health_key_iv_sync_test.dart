import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/health_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/sync_state.dart';
import 'package:myplanet/repository/health_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Pins `DashboardViewModel.syncKeyId`'s shape: the re-entrancy guard
/// (9f3fac1d9's `syncJob?.isActive`), the session password reaching the
/// repository as the sync credentials, and the SyncUiState lifecycle.
void main() {
  late MockHealthRepository repository;
  late _MockSecureStorage secureStorage;

  setUp(() {
    repository = MockHealthRepository();
    secureStorage = _MockSecureStorage();
  });

  UserRow user({String name = 'ada'}) => UserRow(
    id: 'user-1',
    name: name,
    rolesList: const ['learner'],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  Future<ProviderContainer> containerFor({
    UserRow? sessionUser,
    bool configured = true,
    String? storedPassword,
  }) async {
    SharedPreferences.setMockInitialValues(const {});
    final prefs = PlanetPrefs(
      await SharedPreferences.getInstance(),
      secureStorage: secureStorage,
    );
    when(
      () => secureStorage.read(key: any(named: 'key')),
    ).thenAnswer((_) async => storedPassword);

    final container = ProviderContainer(
      overrides: [
        healthRepositoryProvider.overrideWithValue(repository),
        planetPrefsProvider.overrideWithValue(prefs),
        serverConfigProvider.overrideWith(
          configured ? _TestConfig.new : _NullConfig.new,
        ),
        sessionProvider.overrideWith(() => _TestSessionNotifier(sessionUser)),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);
    return container;
  }

  test(
    'sync passes the stored password and session user to the repository',
    () async {
      when(
        () => repository.syncDashboardKeyIv(
          userName: any(named: 'userName'),
          password: any(named: 'password'),
          currentUserId: any(named: 'currentUserId'),
          role: any(named: 'role'),
        ),
      ).thenAnswer((_) async {});
      final container = await containerFor(
        sessionUser: user(),
        storedPassword: 'pw',
      );

      await container.read(healthKeyIvSyncProvider.notifier).sync('learner');

      final captured = verify(
        () => repository.syncDashboardKeyIv(
          userName: captureAny(named: 'userName'),
          password: captureAny(named: 'password'),
          currentUserId: captureAny(named: 'currentUserId'),
          role: captureAny(named: 'role'),
        ),
      ).captured;
      expect(captured, ['ada', 'pw', 'user-1', 'learner']);
      expect(container.read(healthKeyIvSyncProvider), isA<SyncSucceeded>());
    },
  );

  test('the re-entrancy guard refuses a second concurrent sync', () async {
    final gate = Completer<void>();
    when(
      () => repository.syncDashboardKeyIv(
        userName: any(named: 'userName'),
        password: any(named: 'password'),
        currentUserId: any(named: 'currentUserId'),
        role: any(named: 'role'),
      ),
    ).thenAnswer((_) => gate.future);
    final container = await containerFor(sessionUser: user());

    final notifier = container.read(healthKeyIvSyncProvider.notifier);
    final first = notifier.sync('learner');
    expect(container.read(healthKeyIvSyncProvider), isA<SyncRunning>());
    // The 9f3fac1d9 guard: a second trigger while one runs is dropped.
    await notifier.sync('learner');
    verify(
      () => repository.syncDashboardKeyIv(
        userName: any(named: 'userName'),
        password: any(named: 'password'),
        currentUserId: any(named: 'currentUserId'),
        role: any(named: 'role'),
      ),
    ).called(1);

    gate.complete();
    await first;
    expect(container.read(healthKeyIvSyncProvider), isA<SyncSucceeded>());
  });

  test('a repository throw lands on SyncErrored', () async {
    when(
      () => repository.syncDashboardKeyIv(
        userName: any(named: 'userName'),
        password: any(named: 'password'),
        currentUserId: any(named: 'currentUserId'),
        role: any(named: 'role'),
      ),
    ).thenThrow(StateError('boom'));
    final container = await containerFor(sessionUser: user());

    await container.read(healthKeyIvSyncProvider.notifier).sync('learner');

    expect(container.read(healthKeyIvSyncProvider), isA<SyncErrored>());
  });

  test('no server config or no session is a no-op', () async {
    final unconfigured = await containerFor(
      sessionUser: user(),
      configured: false,
    );
    await unconfigured.read(healthKeyIvSyncProvider.notifier).sync('learner');
    expect(unconfigured.read(healthKeyIvSyncProvider), isA<SyncIdle>());

    final signedOut = await containerFor(sessionUser: null);
    await signedOut.read(healthKeyIvSyncProvider.notifier).sync('learner');
    expect(signedOut.read(healthKeyIvSyncProvider), isA<SyncIdle>());

    verifyNever(
      () => repository.syncDashboardKeyIv(
        userName: any(named: 'userName'),
        password: any(named: 'password'),
        currentUserId: any(named: 'currentUserId'),
        role: any(named: 'role'),
      ),
    );
  });
}

class MockHealthRepository extends Mock implements HealthRepository {}

class _MockSecureStorage extends Mock implements FlutterSecureStorage {}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
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
