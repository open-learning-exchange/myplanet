import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/background_entrypoint.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/repository/apk_log_uploader.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'device_identity_fixture.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// **The `apk_log` slice is a write path with no screen**, so nothing a user
/// can do would notice if its wiring were lost. That is the Phase 154 shape —
/// a slice merging green, tested and dead — arriving through a feature with
/// no UI rather than through a lane boundary, and these are the guards that
/// make losing any of it loud.
///
/// Four call sites hold the whole slice up, and every one of them lives in a
/// file some future lane will edit for an unrelated reason:
///
///  1. `main.dart` installs the error hooks — without it nothing is ever
///     recorded.
///  2. `main.dart` sweeps the crash files — without it a report written while
///     the app was dying never becomes a row.
///  3. `background_entrypoint.dart` queues the pending rows — without it
///     nothing ever leaves the handset.
///  4. `app_providers.dart` registers the uploader's handler — without it the
///     drainer's *generic fallback* still POSTs the payload but never writes
///     `_rev` back, so the row stays pending and the next sweep files a
///     **second** crash document. That is precisely the Kotlin retry-path
///     defect (`RetryRepositoryImpl:67-125`) reproduced by omission, and it is
///     the one failure here that is worse than doing nothing.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  late AppDatabase db;
  late _MockPlanetApi api;

  setUpAll(() => registerFallbackValue(<String, dynamic>{}));

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
    api = _MockPlanetApi();
  });
  tearDown(() => db.close());

  Future<ProviderContainer> containerFor() async {
    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        planetPrefsProvider.overrideWithValue(
          PlanetPrefs(await SharedPreferences.getInstance()),
        ),
        deviceIdentitySourceProvider.overrideWithValue(testDeviceIdentity),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  Future<void> seed(String id) => db.apkLogDao.insert(
    ApkLogsCompanion.insert(
      id: id,
      type: const Value('crash'),
      error: const Value('boom'),
      time: const Value('1700'),
    ),
  );

  group('the drainer knows this upload type', () {
    test('a real drain writes the revision back', () async {
      // **Behavioural, not a source-text scan.** If the handler entry in
      // `app_providers.dart` were deleted, the generic fallback would still
      // POST and still complete the outbox row — so a test that only checked
      // "the send happened" would be green on the broken tree. The revision
      // landing on the row is what only the registered handler can do.
      final container = await containerFor();
      await seed('a');
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => const NetworkSuccess({'id': 'doc-1', 'rev': '1-abc'}),
      );

      await container
          .read(apkLogUploaderProvider)
          .queuePending(config: config, userId: null);
      await container.read(outboxDrainerProvider).drain(authHeader: 'Basic x');

      expect(
        (await db.apkLogDao.getById('a'))!.rev,
        '1-abc',
        reason:
            'without ApkLogUploader.type in outboxDrainerProvider\'s handler '
            'map the generic fallback sends the payload and never marks the '
            'row, so the next sweep files a second crash document',
      );
      expect(await db.apkLogDao.pendingUploads(), isEmpty);
    });
  });

  group('the headless path queues what the recorder wrote', () {
    test('sweepPendingApkLogs queues a stranded report', () async {
      final container = await containerFor();
      await seed('a');

      await sweepPendingApkLogs(container, config: config, userId: null);

      expect(
        await db.outboxDao.forItem(ApkLogUploader.type, 'a'),
        hasLength(1),
      );
    });

    test('a throwing sweep is swallowed', () async {
      // `queuePending` reads device identity, which rethrows on a headless
      // engine with no channel and no primed cache. Telemetry failing must not
      // add `outboxDrain` to the runner's `failedSteps` and ask the OS to
      // retry the whole task — no Kotlin caller of `uploadCrashLog` does that.
      final container = ProviderContainer(
        retry: noProviderRetry,
        overrides: [
          appDatabaseProvider.overrideWithValue(db),
          planetApiProvider.overrideWithValue(api),
          planetPrefsProvider.overrideWithValue(
            PlanetPrefs(await SharedPreferences.getInstance()),
          ),
          deviceIdentitySourceProvider.overrideWithValue(
            const _ThrowingIdentitySource(),
          ),
        ],
      );
      addTearDown(container.dispose);
      await seed('a');

      await expectLater(
        sweepPendingApkLogs(container, config: config, userId: null),
        completes,
      );
    });
  });

  group('call sites', () {
    test('the headless drain leg queues apk logs before it drains', () {
      final source = File('lib/background_entrypoint.dart').readAsStringSync();
      // Bounded to the runner's argument list, ending at the *first*
      // `@visibleForTesting` — where the top-level declarations begin — so
      // `sweepPendingApkLogs`'s own declaration cannot satisfy the `contains`.
      // That is the Phase 134 failure class, found in a search window wide
      // enough to include the thing it searched for.
      final wiring = source.substring(
        source.indexOf('BackgroundTaskRunner('),
        source.indexOf('@visibleForTesting'),
      );

      final swept = wiring.indexOf('sweepPendingApkLogs(');
      final drained = wiring.indexOf('drainer.drain(');
      expect(
        swept,
        greaterThan(-1),
        reason: 'nothing in the headless path calls sweepPendingApkLogs',
      );
      expect(drained, greaterThan(-1), reason: 'drainer.drain moved');
      expect(
        swept,
        lessThan(drained),
        reason:
            'the sweep must queue before the drain that carries it, or the '
            'reports wait for the next invocation',
      );
      expect(
        swept,
        lessThan(wiring.indexOf('syncSteps:')),
        reason:
            'it must stay in drainOutbox rather than move into syncSteps: '
            'those run only for a due autoSync task with auto-sync enabled, '
            'and Kotlin reaches uploadCrashLog() from AutoSyncWorker:135 and '
            'UserDataWorker:49 — a manual full sync does not upload logs',
      );
    });

    test('bootstrap installs the hooks and sweeps the crash files', () {
      final source = File('lib/main.dart').readAsStringSync();
      // Bounded to `main()`'s body. The end bound is the first top-level
      // declaration after it, so neither helper's own declaration can satisfy
      // a `contains` here.
      final body = source.substring(
        source.indexOf('Future<void> main() async {'),
        source.indexOf('void _observeForeground('),
      );

      expect(
        body,
        contains('installErrorHandlers()'),
        reason:
            'without this the port has no uncaught-error handler at all and a '
            'crash in the field reaches neither the database nor the server',
      );
      expect(
        body,
        contains('sweepPendingFiles()'),
        reason:
            'a report written while the isolate was dying only becomes a row '
            'through the next start\'s sweep',
      );
      expect(
        body,
        contains('CrashLogStore.prime()'),
        reason:
            'the error hook writes synchronously, so the directory has to be '
            'resolved before anything can fail',
      );
      expect(
        body.indexOf('CrashLogStore.prime()'),
        lessThan(body.indexOf('installErrorHandlers()')),
        reason: 'an unprimed store silently writes nothing',
      );
    });
  });
}

class _ThrowingIdentitySource implements DeviceIdentitySource {
  const _ThrowingIdentitySource();

  @override
  Future<DeviceIdentity> read() async =>
      throw StateError('no platform channel and no primed cache');
}
