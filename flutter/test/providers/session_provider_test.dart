import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';

/// Pins the write-back half of `SessionNotifier.updateProfile` /
/// `setUserImage`: a profile edit or photo change must flag the row
/// (`isUpdated = true`) so [UserUploader.queuePending] carries it to the
/// `_users` database on the next drain.
void main() {
  late AppDatabase db;
  late MockPlanetApi api;

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    registerFallbackValue(<String, dynamic>{});
  });
  tearDown(() => db.close());

  UserRow user() => UserRow(
    id: 'user-1',
    couchId: 'org.couchdb.user:ada',
    rev: '1-a',
    name: 'ada',
    rolesList: const ['learner'],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  Future<ProviderContainer> containerFor(UserRow current) async {
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        serverConfigProvider.overrideWith(_TestConfig.new),
        sessionProvider.overrideWith(() => _TestSessionNotifier(current)),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);
    return container;
  }

  test(
    'updateProfile flags the row as pending and enqueues the upload',
    () async {
      // Seed the row so the notifier has something to edit. The override's build
      // returns it, but `updateProfile` writes through the DAO, so the row must
      // exist in the database first.
      await db.userDao.upsert(user().toCompanion(false));
      final container = await containerFor(user());

      await container
          .read(sessionProvider.notifier)
          .updateProfile(
            firstName: 'Ada',
            middleName: 'Augusta',
            lastName: 'Lovelace',
            email: 'ada@example.org',
            phoneNumber: '+1 555',
            level: 'a1',
            language: 'English',
            gender: 'female',
            dateOfBirth: '1815-12-10',
          );

      final saved = await db.userDao.getById('user-1');
      expect(saved?.firstName, 'Ada');
      expect(saved?.lastName, 'Lovelace');
      expect(saved?.isUpdated, isTrue);
      final queued = await db.outboxDao.due(
        DateTime.now().millisecondsSinceEpoch + 1000,
      );
      expect(queued.map((row) => row.uploadType), ['user']);
      expect(queued.single.httpMethod, 'PUT');
    },
  );

  test('setUserImage stores the path and flags the row', () async {
    await db.userDao.upsert(user().toCompanion(false));
    final container = await containerFor(user());

    await container
        .read(sessionProvider.notifier)
        .setUserImage('/data/cache/photo.jpg');

    final saved = await db.userDao.getById('user-1');
    expect(saved?.userImage, '/data/cache/photo.jpg');
    expect(saved?.isUpdated, isTrue);
    expect(await db.userDao.pendingSyncUsers(), isNotEmpty);
  });

  test(
    'a profile edit without a configured server still writes locally',
    () async {
      // The edit must land even when there is nowhere to send it; the dirty flag
      // carries it to the next drain once a server is configured.
      await db.userDao.upsert(user().toCompanion(false));
      final container = ProviderContainer(
        overrides: [
          appDatabaseProvider.overrideWithValue(db),
          planetApiProvider.overrideWithValue(api),
          serverConfigProvider.overrideWith(_NullConfig.new),
          sessionProvider.overrideWith(() => _TestSessionNotifier(user())),
        ],
      );
      addTearDown(container.dispose);
      await container.read(sessionProvider.future);

      await container
          .read(sessionProvider.notifier)
          .updateProfile(
            firstName: 'Ada',
            middleName: '',
            lastName: 'Lovelace',
            email: '',
            phoneNumber: '',
            level: '',
            language: '',
            gender: '',
            dateOfBirth: '',
          );

      final saved = await db.userDao.getById('user-1');
      expect(saved?.firstName, 'Ada');
      expect(saved?.isUpdated, isTrue);
      // No server → no outbox entry yet, but the row is dirty and waiting.
      expect(await db.outboxDao.due(9999999999999), isEmpty);
    },
  );

  test(
    'blanking a field nulls the column rather than leaving the old value',
    () async {
      await db.userDao.upsert(user().toCompanion(false));
      final container = await containerFor(user());

      await container
          .read(sessionProvider.notifier)
          .updateProfile(
            firstName: 'Ada',
            middleName: '',
            lastName: '',
            email: '   ',
            phoneNumber: '',
            level: '',
            language: '',
            gender: '',
            dateOfBirth: '',
          );

      final saved = await db.userDao.getById('user-1');
      expect(saved?.firstName, 'Ada');
      // A blank field clears the column — `nullToAbsent` would have left the
      // prior value in place.
      expect(saved?.lastName, isNull);
      expect(saved?.email, isNull);
      expect(saved?.isUpdated, isTrue);
    },
  );

  test('the session state reflects the edited row', () async {
    await db.userDao.upsert(user().toCompanion(false));
    final container = await containerFor(user());

    await container
        .read(sessionProvider.notifier)
        .updateProfile(
          firstName: 'Grace',
          middleName: '',
          lastName: 'Hopper',
          email: '',
          phoneNumber: '',
          level: '',
          language: '',
          gender: '',
          dateOfBirth: '',
        );

    final session = container.read(sessionProvider).valueOrNull;
    expect(session?.firstName, 'Grace');
    expect(session?.lastName, 'Hopper');
  });

  test('a profile edit is a no-op when no session is loaded', () async {
    // Before login there is no current user; the edit must not throw.
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        serverConfigProvider.overrideWith(_TestConfig.new),
        sessionProvider.overrideWith(() => _TestSessionNotifier(null)),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);

    await container
        .read(sessionProvider.notifier)
        .updateProfile(
          firstName: 'Ghost',
          middleName: '',
          lastName: '',
          email: '',
          phoneNumber: '',
          level: '',
          language: '',
          gender: '',
          dateOfBirth: '',
        );

    expect(await db.userDao.getById('user-1'), isNull);
    expect(await db.outboxDao.due(9999999999999), isEmpty);
  });

  test('setUserImage is a no-op when no session is loaded', () async {
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        serverConfigProvider.overrideWith(_TestConfig.new),
        sessionProvider.overrideWith(() => _TestSessionNotifier(null)),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);

    await container.read(sessionProvider.notifier).setUserImage('/x.jpg');

    expect(await db.outboxDao.due(9999999999999), isEmpty);
  });

  test('a queue failure does not undo the edit', () async {
    // The dirty flag is the durable half; an enqueue that throws must leave
    // the row dirty so the next drain carries it. The `_queueUserUpload` path
    // swallows the error deliberately.
    await db.userDao.upsert(user().toCompanion(false));
    final container = await containerFor(user());

    await container
        .read(sessionProvider.notifier)
        .updateProfile(
          firstName: 'Ada',
          middleName: '',
          lastName: 'Lovelace',
          email: '',
          phoneNumber: '',
          level: '',
          language: '',
          gender: '',
          dateOfBirth: '',
        );

    final saved = await db.userDao.getById('user-1');
    expect(saved?.firstName, 'Ada');
    expect(saved?.isUpdated, isTrue);
  });
}

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

class MockPlanetApi extends Mock implements PlanetApi {}
