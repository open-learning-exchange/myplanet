import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/settings_provider.dart';
import 'package:path/path.dart' as p;
import 'package:shared_preferences/shared_preferences.dart';

class _MockSecureStorage extends Mock implements FlutterSecureStorage {}

/// Pins the two halves of the server-switch gate and the wipe's file deletion.
///
/// These exist because a second `parity-auditor` pass pointed out that the
/// screen tests override `deviceHoldsServerDataProvider` and
/// `localPlanetCodesProvider` in *every* case, and override
/// `clearForServerSwitch` wholesale — so the predicate the whole change hangs
/// on, and the `rethrow` that stops a failed wipe from being reported as a
/// successful one, were both readable as covered while nothing held them.
/// Replacing `deviceHoldsServerDataProvider`'s body with `return false` left
/// the entire suite green and the feature disabled in production.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AppDatabase db;
  late PlanetPrefs prefs;
  late _MockSecureStorage secureStorage;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
    secureStorage = _MockSecureStorage();
    registerFallbackValue('');
    when(
      () => secureStorage.write(
        key: any(named: 'key'),
        value: any(named: 'value'),
      ),
    ).thenAnswer((_) async {});
    when(
      () => secureStorage.read(key: any(named: 'key')),
    ).thenAnswer((_) async => null);
    when(
      () => secureStorage.delete(key: any(named: 'key')),
    ).thenAnswer((_) async {});
    when(secureStorage.deleteAll).thenAnswer((_) async {});
    prefs = PlanetPrefs(
      await SharedPreferences.getInstance(),
      secureStorage: secureStorage,
    );
  });
  tearDown(() => db.close());

  ProviderContainer container() {
    final c = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetPrefsProvider.overrideWithValue(prefs),
      ],
    );
    addTearDown(c.dispose);
    return c;
  }

  UsersCompanion user(String id, {String? planetCode}) => UserRow(
    id: id,
    name: id,
    rolesList: const [],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
    planetCode: planetCode,
  ).toCompanion(false);

  group('deviceHoldsServerDataProvider', () {
    test('a fresh install holds nothing', () {
      expect(container().read(deviceHoldsServerDataProvider), isFalse);
    });

    test(
      'a completed sync counts, even with the configuration cleared',
      () async {
        // This is the case the gate exists for: "change server" removes the
        // persisted config and leaves the database, so `lastSync` is the only
        // surviving evidence that this device belongs to someone.
        await prefs.setLastSync(1234);
        final c = container();
        expect(c.read(serverConfigProvider), isNull);
        expect(c.read(deviceHoldsServerDataProvider), isTrue);
      },
    );

    test('a configured server counts on its own', () async {
      await prefs.saveServerConfig(
        const ServerConfig(
          serverUrl: 'https://planet.example.org',
          pin: '1234',
          couchDbUrl: 'https://satellite:1234@planet.example.org:443',
        ),
      );
      expect(container().read(deviceHoldsServerDataProvider), isTrue);
    });
  });

  group('localPlanetCodesProvider', () {
    test('reads the communities the local users belong to', () async {
      await db.userDao.upsert(user('u1', planetCode: 'guatemala'));
      await db.userDao.upsert(user('u2', planetCode: 'guatemala'));
      await db.userDao.upsert(user('u3', planetCode: 'learning'));

      expect(await container().read(localPlanetCodesProvider.future), <String>{
        'guatemala',
        'learning',
      });
    });

    test('users with no community are not a community', () async {
      await db.userDao.upsert(user('u1'));
      await db.userDao.upsert(user('u2', planetCode: ''));

      expect(await container().read(localPlanetCodesProvider.future), isEmpty);
    });
  });

  group('clearForServerSwitch', () {
    test('wipes the database and the preferences', () async {
      await db.userDao.upsert(user('u1', planetCode: 'learning'));
      await prefs.setLastSync(1234);
      await prefs.setOnboardingComplete();

      final c = container();
      await c.read(clearDataProvider.notifier).clearForServerSwitch();

      expect(await db.userDao.getById('u1'), isNull);
      expect(prefs.lastSync, 0);
      expect(prefs.serverConfig, isNull);
      // Kept, so the user does not sit through the slideshow again.
      expect(prefs.onboardingComplete, isTrue);
    });

    test('rethrows, so a failed wipe is not reported as a switch', () async {
      // Without the rethrow the dialog pops `true` on a failure and the new
      // server is adopted over the old server's database — the exact defect
      // the dialog exists to prevent, arrived at through its error path.
      when(secureStorage.deleteAll).thenThrow(StateError('keystore down'));

      final c = container();
      await expectLater(
        c.read(clearDataProvider.notifier).clearForServerSwitch(),
        throwsA(isA<StateError>()),
      );
      expect(c.read(clearDataProvider), isA<AsyncError<void>>());
    });

    test('deletes the downloaded-file tree', () async {
      // Kotlin deletes `<olePath>/**` on the first sync after a wipe, via the
      // `FIRST_RUN` flag `clearPreferences` resets; the port had no
      // counterpart, so one institution's downloaded resources, exam photos
      // and receipts stayed on a device now serving another.
      final temp = await Directory.systemTemp.createTemp('ole-wipe');
      addTearDown(() async {
        if (temp.existsSync()) await temp.delete(recursive: true);
      });
      final previous = ResourceFiles.baseDirectory;
      ResourceFiles.baseDirectory = () async => temp;
      addTearDown(() => ResourceFiles.baseDirectory = previous);

      final resource = File(p.join(temp.path, 'ole', 'doc-1', 'video.mp4'));
      await resource.parent.create(recursive: true);
      await resource.writeAsString('bytes');
      final outside = File(p.join(temp.path, 'keep.txt'));
      await outside.writeAsString('not ours');

      await container().read(clearDataProvider.notifier).clearForServerSwitch();

      expect(resource.existsSync(), isFalse);
      expect(Directory(p.join(temp.path, 'ole')).existsSync(), isFalse);
      // Only the `ole` tree: nothing else under the documents directory.
      expect(outside.existsSync(), isTrue);
    });
  });
}
