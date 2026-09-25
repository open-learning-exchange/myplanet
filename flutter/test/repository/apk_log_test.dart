import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/system/crash_log_store.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/apk_log_recorder.dart';
import 'package:myplanet/repository/diagnostics_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/widget_harness.dart';

/// The `apk_log` writer half: [DiagnosticsRepository] (port of
/// `DiagnosticsRepositoryImpl`), [CrashLogStore] (port of `CrashLogStore.kt`)
/// and [ApkLogRecorder] (port of `MainApplication`'s four diagnostics
/// entry points).
///
/// **The error-hook tests below drive the real hook**, not the repository
/// method under it. Phase 155 is the standing reminder for why: fifteen green
/// tests for `add_examination_screen` and not one passed an `examinationId`,
/// so a blank edit form that overwrote the record was green. A write path with
/// no screen is exactly where that mistake is cheapest to make.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
    code: 'planet-from-config',
    parentCode: 'parent-from-config',
  );

  late AppDatabase db;
  late Directory tempDir;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
    tempDir = await Directory.systemTemp.createTemp('apk_log_test');
    CrashLogStore.baseDirectory = () async => tempDir;
    CrashLogStore.resetForTest();
  });

  tearDown(() async {
    await db.close();
    CrashLogStore.resetForTest();
    CrashLogStore.baseDirectory = () async => tempDir;
    if (tempDir.existsSync()) await tempDir.delete(recursive: true);
  });

  Future<ProviderContainer> containerFor({
    UserRow? user,
    ServerConfig? serverConfig = config,
    String version = '0.72.69',
    bool versionThrows = false,
    DiagnosticsRepository? repository,
  }) async {
    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetPrefsProvider.overrideWithValue(
          PlanetPrefs(await SharedPreferences.getInstance()),
        ),
        serverConfigProvider.overrideWith(
          () => _TestServerConfigNotifier(serverConfig),
        ),
        sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
        appVersionInfoProvider.overrideWith((ref) async {
          if (versionThrows) throw StateError('no package_info channel');
          return (version: version, buildNumber: '7269');
        }),
        if (repository != null)
          diagnosticsRepositoryProvider.overrideWithValue(repository),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  UserRow userWith({
    String? planetCode,
    String? parentCode,
    String id = 'u1',
  }) => buildUserRow(
    id: id,
    name: 'ada',
  ).copyWith(planetCode: Value(planetCode), parentCode: Value(parentCode));

  group('DiagnosticsRepository.serialize', () {
    ApkLog rowWith({String error = 'boom', String userId = 'u1'}) => ApkLog(
      id: 'row-1',
      rev: '',
      userId: userId,
      type: 'crash',
      error: error,
      page: '',
      parentCode: 'parent',
      version: '0.72.69',
      createdOn: 'planet',
      time: '1700000000000',
    );

    test('is the eight row keys, and no id of any kind', () {
      // Eight, not the ten `ApkLog.serialize` puts: `deviceName` and
      // `customDeviceName` come from the device identity at queue time, which
      // is `ApkLogUploader.serialize`'s job and is asserted there.
      final doc = DiagnosticsRepository.serialize(rowWith());

      expect(doc, {
        'type': 'crash',
        'error': 'boom',
        'page': '',
        'time': '1700000000000',
        'userId': 'u1',
        'version': '0.72.69',
        'createdOn': 'planet',
        'parentCode': 'parent',
      });
      // Kotlin puts no `_id`, no `_rev` and not even the local UUID, and
      // `UploadConfigs.CrashLog:223` discards the `remoteId` the POST returns
      // because the entity has no `_id` column to keep it in.
      expect(doc.keys, isNot(contains('id')));
      expect(doc.keys, isNot(contains('_id')));
      expect(doc.keys, isNot(contains('_rev')));
    });

    test('sends JSON null where Kotlin leaves the field null', () {
      // `buildApkLog` assigns `error` only when non-empty and `userId` only
      // when the user model has an id, and the Hilt Gson is built with
      // `.serializeNulls()` (`NetworkModule:65-72`) — so the wire body for a
      // zero-error type literally contains `"error": null`. A port that sent
      // `""` would diverge on every `"new login"` and `"foreground"` row.
      final doc = DiagnosticsRepository.serialize(
        rowWith(error: '', userId: ''),
      );

      expect(doc.containsKey('error'), isTrue);
      expect(doc['error'], isNull);
      expect(doc.containsKey('userId'), isTrue);
      expect(doc['userId'], isNull);
    });

    test('sends page as an empty string, not null', () {
      // The opposite case in the same builder: `page = ""` is assigned
      // unconditionally, so it goes out as a string.
      expect(DiagnosticsRepository.serialize(rowWith())['page'], '');
    });
  });

  group('resolveContext', () {
    test('prefers the user row over the configuration', () async {
      final container = await containerFor(
        user: userWith(planetCode: 'planet-a', parentCode: 'parent-a'),
      );

      final context = await ApkLogRecorder(container).resolveContext();

      expect(context.planetCode, 'planet-a');
      expect(context.parentCode, 'parent-a');
      expect(context.userId, 'u1');
      expect(context.version, '0.72.69');
    });

    test('falls back on a blank code, not merely an empty one', () async {
      // `takeIf { it.isNotBlank() }`, not `isNotEmpty`
      // (`DiagnosticsRepositoryImpl:53-57`). A whitespace-only value on the
      // user row falls back to the configuration, and a test seeded with ''
      // alone could not tell the two readings apart.
      final container = await containerFor(
        user: userWith(planetCode: '   ', parentCode: '  '),
      );

      final context = await ApkLogRecorder(container).resolveContext();

      expect(context.planetCode, 'planet-from-config');
      expect(context.parentCode, 'parent-from-config');
    });

    test(
      'is empty strings when signed out on an unconfigured server',
      () async {
        // `SharedPrefManager.getParentCode()/getPlanetCode()` return "" rather
        // than null when unset, so the document carries empty strings here —
        // unlike `userId`, which is genuinely null.
        final container = await containerFor(user: null, serverConfig: null);

        final context = await ApkLogRecorder(container).resolveContext();

        expect(context.planetCode, '');
        expect(context.parentCode, '');
        expect(context.userId, '');
      },
    );

    test('degrades to an empty version rather than throwing', () async {
      // `package_info_plus` is a platform channel, so it is unavailable on the
      // headless engine a background sweep runs in. A crash report with no
      // version beats no crash report.
      final container = await containerFor(versionThrows: true);

      expect((await ApkLogRecorder(container).resolveContext()).version, '');
    });
  });

  group('log — the createLog path', () {
    test('writes a row and no file', () async {
      final container = await containerFor(
        user: userWith(planetCode: 'planet-a', parentCode: 'parent-a'),
      );
      await CrashLogStore.prime();

      final stored = await ApkLogRecorder(
        container,
        now: () => DateTime.fromMillisecondsSinceEpoch(1700),
      ).log(type: ApkLogRecorder.newLoginType);

      expect(stored, isTrue);
      final rows = await db.apkLogDao.pendingUploads();
      expect(rows, hasLength(1));
      expect(rows.single.type, 'new login');
      expect(rows.single.time, '1700');
      expect(rows.single.createdOn, 'planet-a');
      expect(rows.single.page, '');
      expect(
        rows.single.error,
        '',
        reason: 'createLog defaults the error to "", which serializes as null',
      );
      expect(
        Directory('${tempDir.path}/${CrashLogStore.dirName}').listSync(),
        isEmpty,
        reason: 'createLog never touches the file store',
      );
    });
  });

  group('CrashLogStore', () {
    test('round-trips a report through the filename', () async {
      await CrashLogStore.prime();

      final file = CrashLogStore.save(
        type: 'crash',
        error: 'stack trace',
        time: '1700',
      );

      expect(file, isNotNull);
      final pending = await CrashLogStore.loadPending();
      expect(pending, hasLength(1));
      expect(pending.single.type, 'crash');
      expect(pending.single.time, '1700');
      expect(pending.single.error, 'stack trace');
    });

    test('a type containing an underscore survives the round trip', () async {
      // `substring(separator + 1)` takes everything after the **first**
      // underscore, so the type may contain them and the time never can.
      await CrashLogStore.prime();
      CrashLogStore.save(type: 'my_custom_type', error: 'e', time: '42');

      expect((await CrashLogStore.loadPending()).single.type, 'my_custom_type');
    });

    test('refuses the newest report at the cap rather than evicting', () async {
      // Counted **before** the write, so 20 is the true ceiling, and the
      // report dropped is the new one — the oldest are nearest the first
      // failure and usually the informative ones.
      await CrashLogStore.prime();
      for (var i = 0; i < CrashLogStore.maxPendingFiles; i++) {
        expect(
          CrashLogStore.save(type: 'crash', error: 'e', time: '$i'),
          isNotNull,
        );
      }

      expect(
        CrashLogStore.save(type: 'crash', error: 'late', time: '999'),
        isNull,
      );
      expect(
        (await CrashLogStore.loadPending()).map((log) => log.time),
        isNot(contains('999')),
      );
    });

    test('a malformed name counts toward nothing and is never read', () async {
      // Kotlin's three rejections: not `.log`, no `_` (or one at index 0), and
      // a non-numeric time. Such a file is invisible to the cap and to the
      // sweep — and therefore immortal, in both apps.
      await CrashLogStore.prime();
      for (final name in ['nope.log', '_crash.log', 'abc_crash.log', 'x.txt']) {
        File('${tempDir.path}/${CrashLogStore.dirName}/$name')
          ..createSync(recursive: true)
          ..writeAsStringSync('junk');
      }

      expect(await CrashLogStore.loadPending(), isEmpty);
      expect(
        CrashLogStore.save(type: 'crash', error: 'e', time: '1'),
        isNotNull,
        reason: 'junk files must not count toward the twenty-file cap',
      );
    });

    test(
      'refuses a non-numeric time rather than writing an unreadable name',
      () async {
        // [_parse] requires an `int.tryParse`-able prefix, so a name this class
        // cannot read back is a report written and then permanently invisible —
        // worse than one not written. Sanitising would produce exactly that, so
        // the seam is closed by refusing instead.
        await CrashLogStore.prime();

        expect(
          CrashLogStore.save(type: 'crash', error: 'e', time: '../escape'),
          isNull,
        );
        expect(await CrashLogStore.loadPending(), isEmpty);
      },
    );

    test('writes nothing when it was never primed', () async {
      expect(CrashLogStore.save(type: 'crash', error: 'e', time: '1'), isNull);
      expect(await CrashLogStore.loadPending(), isEmpty);
    });
  });

  group('recordCrash — the persistCriticalLog path', () {
    test(
      'writes the file first, then the row, then deletes the file',
      () async {
        final container = await containerFor(user: userWith(planetCode: 'p'));
        await CrashLogStore.prime();
        final recorder = ApkLogRecorder(
          container,
          now: () => DateTime.fromMillisecondsSinceEpoch(1700),
        );

        recorder.recordCrash(ApkLogRecorder.crashType, 'boom');
        // The file is on disk before any future has had a chance to run: that
        // ordering is the whole design, because a dying isolate takes every
        // pending await with it.
        final logDir = Directory('${tempDir.path}/${CrashLogStore.dirName}');
        expect(logDir.listSync(), hasLength(1));

        await pumpEventQueue();

        final rows = await db.apkLogDao.pendingUploads();
        expect(rows, hasLength(1));
        expect(rows.single.type, 'crash');
        expect(rows.single.error, 'boom');
        expect(
          logDir.listSync(),
          isEmpty,
          reason: 'the file is deleted only once the row is stored',
        );
      },
    );

    test('keeps the file when the row could not be stored', () async {
      // The guarantee the whole class exists for: a crash arriving while the
      // database is locked or the disk is full must leave its file behind for
      // the next start's sweep. Nothing pinned this — the refusing fixture
      // overrode `saveLogs` only, so an unconditional delete was green.
      final container = await containerFor(
        repository: _RefusingDiagnosticsRepository(db.apkLogDao),
      );
      await CrashLogStore.prime();

      ApkLogRecorder(
        container,
        now: () => DateTime.fromMillisecondsSinceEpoch(1700),
      ).recordCrash(ApkLogRecorder.crashType, 'boom');
      await pumpEventQueue();

      expect(
        Directory('${tempDir.path}/${CrashLogStore.dirName}').listSync(),
        hasLength(1),
        reason: 'a report that never reached the database must not be deleted',
      );
    });

    test('a sweep of an already-stored report is not a second row', () async {
      // Window 2 in [CrashLogStore]: the process dies between the row insert
      // and the file delete. Kotlin duplicates the report here, because
      // `buildApkLog` mints a fresh UUID for every insert. The port derives the
      // key from `(time, type)` on both paths so the sweep is a no-op.
      final container = await containerFor();
      await CrashLogStore.prime();
      // **An advancing clock, and that is the point.** A fixture pinned at a
      // constant millisecond is green whether `recordCrash` reads the clock
      // once or twice, so it could not pin the "one clock read where Kotlin
      // has two" claim at all — Kotlin times the filename and the row from
      // independent `timeProvider.now()` calls, and in production the two
      // differ. With this clock a second read derives a different row id and
      // the sweep files a duplicate.
      var tick = 1700;
      final recorder = ApkLogRecorder(
        container,
        now: () => DateTime.fromMillisecondsSinceEpoch(tick++),
      );

      recorder.recordCrash(ApkLogRecorder.crashType, 'boom');
      await pumpEventQueue();
      final written = await db.apkLogDao.pendingUploads();
      expect(written, hasLength(1));
      // Put the file back under the name the write used, as a death before the
      // delete would have left it.
      CrashLogStore.save(
        type: 'crash',
        error: 'boom',
        time: written.single.time,
      );
      await recorder.sweepPendingFiles();

      expect(await db.apkLogDao.countAll(), 1);
    });

    test('still records the row when the file store is at its cap', () async {
      // A null from `save` does not abort the log: Kotlin continues to the
      // insert and `pendingFile?.delete()` no-ops. At the cap you lose the
      // crash-survival guarantee, not the report.
      final container = await containerFor();
      await CrashLogStore.prime();
      for (var i = 0; i < CrashLogStore.maxPendingFiles; i++) {
        CrashLogStore.save(type: 'crash', error: 'e', time: '$i');
      }

      ApkLogRecorder(
        container,
        now: () => DateTime.fromMillisecondsSinceEpoch(9999),
      ).recordCrash(ApkLogRecorder.crashType, 'late');
      await pumpEventQueue();

      expect(
        (await db.apkLogDao.pendingUploads()).map((row) => row.error),
        contains('late'),
      );
    });
  });

  group('observeForeground', () {
    /// A cold start as the engine really delivers one: the binding reads the
    /// initial lifecycle state from the native window during
    /// `ensureInitialized`, so by the time `main()` builds a listener the state
    /// is already `resumed` and no transition is generated.
    ///
    /// **This is the fixture half the first cut got wrong.** Driving
    /// `inactive → resumed` alone models a transient interruption under every
    /// reading and cannot tell `onShow` from `onResume`, nor establish whether
    /// a cold start delivers anything at all.
    void coldStart() {
      TestWidgetsFlutterBinding
              .instance
              .platformDispatcher
              .initialLifecycleStateTestValue =
          'AppLifecycleState.resumed';
      TestWidgetsFlutterBinding.instance
          .readTestInitialLifecycleStateFromNativeWindow();
    }

    Future<void> drive(List<AppLifecycleState> states) async {
      for (final state in states) {
        TestWidgetsFlutterBinding.instance.handleAppLifecycleStateChanged(
          state,
        );
      }
      await pumpEventQueue();
    }

    /// Backgrounding and returning: the only transition Kotlin's
    /// `ProcessLifecycleOwner.onStart` fires for.
    Future<void> backgroundAndReturn() => drive(const [
      AppLifecycleState.inactive,
      AppLifecycleState.hidden,
      AppLifecycleState.paused,
      AppLifecycleState.hidden,
      AppLifecycleState.inactive,
      AppLifecycleState.resumed,
    ]);

    test('files nothing for a cold start', () async {
      final container = await containerFor();
      coldStart();
      final listener = ApkLogRecorder(container).observeForeground();
      addTearDown(listener.dispose);

      await pumpEventQueue();

      expect(
        await db.apkLogDao.countAll(),
        0,
        reason:
            'the first foreground is already counted by the "new login" row; '
            'filing both would double every session in Planet\'s aggregation',
      );
    });

    test('files one row per return from background', () async {
      final container = await containerFor();
      coldStart();
      final listener = ApkLogRecorder(container).observeForeground();
      addTearDown(listener.dispose);

      await backgroundAndReturn();

      final rows = await db.apkLogDao.pendingUploads();
      expect(rows, hasLength(1));
      expect(rows.single.type, 'foreground');
      expect(
        rows.single.error,
        '',
        reason: 'createLog defaults the error to "", which serializes as null',
      );

      await backgroundAndReturn();
      expect(await db.apkLogDao.countAll(), 2);
    });

    test('files nothing for a transient loss of focus', () async {
      // The whole reason this is `onShow` and not `onResume`: a notification
      // shade, an incoming-call banner or a permission dialog takes and
      // returns input focus without the app ever leaving the foreground.
      // Kotlin's `ProcessLifecycleOwner.onStart` fires for none of them, and an
      // `onResume` port would have filed a "session" for each — dozens a day.
      final container = await containerFor();
      coldStart();
      final listener = ApkLogRecorder(container).observeForeground();
      addTearDown(listener.dispose);

      await drive(const [
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]);

      expect(await db.apkLogDao.countAll(), 0);
    });
  });

  group('sweepPendingFiles', () {
    test('turns every readable file into a row and deletes them', () async {
      final container = await containerFor(
        user: userWith(planetCode: 'planet-a', parentCode: 'parent-a'),
      );
      await CrashLogStore.prime();
      CrashLogStore.save(type: 'crash', error: 'one', time: '100');
      CrashLogStore.save(type: 'anr', error: 'two', time: '200');

      await ApkLogRecorder(container).sweepPendingFiles();

      final rows = await db.apkLogDao.pendingUploads();
      expect(rows.map((row) => row.error).toSet(), {'one', 'two'});
      expect(
        rows.map((row) => row.createdOn).toSet(),
        {'planet-a'},
        reason:
            'the sweep re-stamps identity with whoever is signed in now — '
            'PendingLog carries only (file, type, time, error) in Kotlin too',
      );
      expect(
        Directory('${tempDir.path}/${CrashLogStore.dirName}').listSync(),
        isEmpty,
      );
    });

    test('keeps every file when the insert fails', () async {
      // All-or-nothing in both directions, which is Kotlin's behaviour, and
      // the conservative half is the one that matters: nothing is deleted that
      // was not stored.
      //
      // **The obvious injection — closing the database — proves nothing.** A
      // closed drift `NativeDatabase.memory()` silently accepts the write
      // (probed directly: the insert succeeds), so a test built on it is green
      // whether the guard exists or not. That is the Phase 156 shape — suspect
      // the fixture before the assertion — so the refusal is injected at the
      // repository instead, where it is unambiguous.
      final container = await containerFor(
        repository: _RefusingDiagnosticsRepository(db.apkLogDao),
      );
      await CrashLogStore.prime();
      CrashLogStore.save(type: 'crash', error: 'one', time: '100');

      await ApkLogRecorder(container).sweepPendingFiles();

      expect(
        Directory('${tempDir.path}/${CrashLogStore.dirName}').listSync(),
        hasLength(1),
      );
      expect(await db.apkLogDao.countAll(), 0);
    });
  });

  group('the real error hooks', () {
    test('a framework error becomes a crash report', () async {
      // Driving `FlutterError.onError` itself rather than calling
      // `recordCrash`, because the hook install is the half that has never
      // existed in this port and a test of the method under it would be green
      // with no handler registered at all.
      final container = await containerFor();
      await CrashLogStore.prime();
      final recorder = ApkLogRecorder(container);
      final before = FlutterError.onError;
      var chained = 0;
      FlutterError.onError = (_) => chained++;
      recorder.installErrorHandlers();
      addTearDown(() {
        recorder.removeErrorHandlers();
        FlutterError.onError = before;
      });

      FlutterError.onError!(
        FlutterErrorDetails(
          exception: StateError('render blew up'),
          stack: StackTrace.fromString('#0 somewhere'),
          library: 'widgets library',
        ),
      );
      await pumpEventQueue();

      final rows = await db.apkLogDao.pendingUploads();
      expect(rows, hasLength(1));
      expect(rows.single.type, 'crash');
      expect(rows.single.error, contains('render blew up'));
      expect(rows.single.error, contains('#0 somewhere'));
      expect(rows.single.error, contains('widgets library'));
      expect(
        chained,
        1,
        reason:
            'Flutter\'s own handler prints the error and paints the red '
            'screen; replacing it would hide every framework error from '
            'whoever is developing the app',
      );
    });

    test('a root-zone error becomes a crash report', () async {
      final container = await containerFor();
      await CrashLogStore.prime();
      final recorder = ApkLogRecorder(container);
      final before = PlatformDispatcher.instance.onError;
      var chained = 0;
      PlatformDispatcher.instance.onError = (_, _) {
        chained++;
        return true;
      };
      recorder.installErrorHandlers();
      addTearDown(() {
        recorder.removeErrorHandlers();
        PlatformDispatcher.instance.onError = before;
      });

      final handled = PlatformDispatcher.instance.onError!(
        StateError('unawaited future rejected'),
        StackTrace.fromString('#0 elsewhere'),
      );
      await pumpEventQueue();

      expect(handled, isTrue, reason: "the previous handler's verdict wins");
      expect(chained, 1);
      final rows = await db.apkLogDao.pendingUploads();
      expect(rows.single.error, contains('unawaited future rejected'));
    });

    test('installing twice does not file every crash twice', () async {
      final container = await containerFor();
      await CrashLogStore.prime();
      final recorder = ApkLogRecorder(container);
      final before = FlutterError.onError;
      recorder.installErrorHandlers();
      recorder.installErrorHandlers();
      addTearDown(() {
        recorder.removeErrorHandlers();
        FlutterError.onError = before;
      });

      FlutterError.onError!(FlutterErrorDetails(exception: StateError('once')));
      await pumpEventQueue();

      expect(await db.apkLogDao.countAll(), 1);
    });
  });
}

class _TestServerConfigNotifier extends ServerConfigNotifier {
  _TestServerConfigNotifier(this._config);

  final ServerConfig? _config;

  @override
  ServerConfig? build() => _config;
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this._user);

  final UserRow? _user;

  @override
  Future<UserRow?> build() async => _user;
}

/// A repository whose inserts refuse, the way `saveLogsToRoom`'s
/// `catch { printStackTrace(); false }` does when Room throws.
class _RefusingDiagnosticsRepository extends DiagnosticsRepository {
  _RefusingDiagnosticsRepository(super.dao);

  @override
  Future<bool> saveLogs({
    required ApkLogContext context,
    required List<PendingCrashLog> logs,
  }) async => false;

  /// The single-row half refuses too. Overriding only [saveLogs] left
  /// `recordCrash`'s `if (stored)` guard — the one line standing between a
  /// failed insert and a deleted crash report — pinned by nothing.
  @override
  Future<bool> saveLog({
    required ApkLogContext context,
    required String type,
    required String error,
    required String time,
    String? id,
  }) async => false;
}
