import 'dart:async';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/system/crash_log_store.dart';
import '../data/local/app_database.dart';
import '../providers/app_providers.dart';
import '../providers/session_provider.dart';
import 'diagnostics_repository.dart';

/// The writer half of the `apk_log` slice — the port of the four things
/// `MainApplication` does with diagnostics.
///
/// | Kotlin | Here |
/// |---|---|
/// | `registerExceptionHandler` (`:350-354`) | [installErrorHandlers] |
/// | `persistCriticalLog` (`:194-203`) | [recordCrash] |
/// | `createLog` (`:153-159`) | [log] |
/// | `sweepPendingLogs` (`:253-268`) | [sweepPendingFiles] |
///
/// ### Where the error hook differs from Kotlin, and why
///
/// Kotlin installs a **process-wide** `Thread.setDefaultUncaughtExceptionHandler`
/// that does **not** chain — Android's `KillApplicationHandler` is replaced
/// outright, never captured — and does **not** kill the process; it logs,
/// persists, and launches a `CATEGORY_HOME` intent, leaving the app alive in a
/// degraded state with the user back at the launcher.
///
/// The port chains and does not bounce, and both halves are deliberate:
///
///  * **Chaining is not optional here.** Flutter's default `FlutterError.onError`
///    is what prints an error to the console and paints the red screen in
///    debug. Replacing it would make every framework error invisible to
///    whoever is developing the app — a strictly worse outcome than the one
///    this class exists to fix.
///  * **There is nothing to bounce away from.** Kotlin's home intent is damage
///    control for a process whose killing handler it removed. Flutter does not
///    terminate on an uncaught error, so the port has no such state to escape,
///    and sending a user to the launcher over a recoverable layout error would
///    be a regression invented by the port rather than a behaviour ported into
///    it.
///
/// Two Kotlin writers have **no port counterpart at all** and are not invented
/// here: the ANR watchdog's `"anr"` reports (`MainApplication:312` —
/// `ANRWatchdog` has no Dart equivalent) and `AudioRecorder:102`, which files a
/// `"crash"` row *and* sends the user home when stopping a recording throws.
class ApkLogRecorder {
  ApkLogRecorder(this._container, {DateTime Function()? now})
    : _now = now ?? DateTime.now;

  /// `ApkLog.ERROR_TYPE_CRASH`.
  static const String crashType = 'crash';

  /// `MainApplication.onAppStarted` (`:470`). **Not a login** — it fires once
  /// per process start, whoever is or is not signed in.
  static const String newLoginType = 'new login';

  /// `MainApplication.onAppForegrounded` (`:463`), which the `isFirstLaunch`
  /// gate (`:459-465`) skips on the first foreground of a process.
  static const String foregroundType = 'foreground';

  /// `SyncTimeLogger.saveSummaryToRoom` (`:102`).
  static const String syncSummaryType = 'sync summary';

  /// `DownloadRepositoryImpl` (`:58`, `:60`).
  static const String fileNotFoundType = 'File Not Found';

  final ProviderContainer _container;
  final DateTime Function() _now;

  FlutterExceptionHandler? _previousFlutterOnError;
  ErrorCallback? _previousPlatformOnError;
  bool _installed = false;

  /// Chains crash recording onto the framework's two uncaught-error surfaces.
  ///
  /// `FlutterError.onError` catches build/layout/paint failures;
  /// `PlatformDispatcher.instance.onError` catches everything that escapes to
  /// the root zone, which is where an unawaited future's rejection lands. Both
  /// call whatever was installed before, so the console dump and the debug red
  /// screen still happen.
  ///
  /// Idempotent: a second call does nothing, so a hot restart cannot stack two
  /// handlers and file every crash twice.
  void installErrorHandlers() {
    if (_installed) return;
    _installed = true;
    _previousFlutterOnError = FlutterError.onError;
    FlutterError.onError = (details) {
      recordCrash(
        crashType,
        _describe(details.exception, details.stack, details.library),
      );
      _previousFlutterOnError?.call(details);
    };
    _previousPlatformOnError = PlatformDispatcher.instance.onError;
    PlatformDispatcher.instance.onError = (error, stack) {
      recordCrash(crashType, _describe(error, stack, null));
      return _previousPlatformOnError?.call(error, stack) ?? false;
    };
  }

  /// Restores the handlers this instance installed. Tests only.
  @visibleForTesting
  void removeErrorHandlers() {
    if (!_installed) return;
    _installed = false;
    FlutterError.onError = _previousFlutterOnError;
    PlatformDispatcher.instance.onError = _previousPlatformOnError;
  }

  /// Port of `persistCriticalLog`: the **synchronous** file write first, then
  /// the row, then the file delete — and only on a successful insert.
  ///
  /// The ordering is the whole design and it carries over unchanged: a dying
  /// isolate takes every pending `await` with it exactly as a dying Android
  /// process takes a launched coroutine, so the durable half has to happen
  /// before any future is created. See [CrashLogStore] for the three windows
  /// this leaves and what closes each.
  ///
  /// A `null` from [CrashLogStore.save] — the store is at its twenty-file cap,
  /// or was never primed — does **not** abort the log. Kotlin continues to the
  /// insert and its `pendingFile?.delete()` simply no-ops; at the cap you lose
  /// the crash-survival guarantee, not the report.
  ///
  /// **One clock read where Kotlin has two.** Kotlin times the filename
  /// (`CrashLogStore:45`) and the row (`MainApplication:198`) from independent
  /// `timeProvider.now()` calls, so the same report carries a different `time`
  /// depending on whether it reached the database directly or via the sweep.
  /// The port reads once and uses it for both, which is what makes
  /// [CrashLogStore.rowIdFor] able to recognise the swept file as the row
  /// already stored.
  void recordCrash(String type, String error) {
    final time = _now().millisecondsSinceEpoch.toString();
    final file = CrashLogStore.save(type: type, error: error, time: time);
    unawaited(() async {
      try {
        final stored = await _insert(
          type: type,
          error: error,
          time: time,
          id: CrashLogStore.rowIdFor(time: time, type: type),
        );
        if (stored) await file?.delete();
      } catch (_) {
        // `runBestEffort`. There is nowhere safer to report a failure to
        // report a failure, and the file is deliberately left behind for the
        // next start's sweep.
      }
    }());
  }

  /// Port of `MainApplication.createLog(type, error)` — a row, no file.
  ///
  /// Fire-and-forget and never throws, matching `runBestEffort`. The empty
  /// default matters: `buildApkLog` assigns `error` only when non-empty, so a
  /// zero-error type uploads `"error": null`.
  Future<bool> log({required String type, String error = ''}) async {
    try {
      return await _insert(
        type: type,
        error: error,
        time: _now().millisecondsSinceEpoch.toString(),
      );
    } catch (_) {
      return false;
    }
  }

  /// Port of `MainApplication.sweepPendingLogs`.
  ///
  /// Runs once per process start and **after** the database is open, as the
  /// Kotlin's sequential `performDeferredInitialization` body has it
  /// (`initializeDatabaseConnection()` then `sweepPendingLogs()`).
  ///
  /// All-or-nothing in both directions, which is Kotlin's behaviour and worth
  /// stating because it is the conservative half that matters: one failing row
  /// keeps **every** file, so nothing is deleted that was not stored.
  ///
  /// The sweep re-stamps identity — a crash recorded yesterday under one user
  /// is filed against whoever is signed in now, with today's planet code and
  /// app version. `CrashLogStore.PendingLog` carries only `(file, type, time,
  /// error)` in Kotlin too, so this is parity rather than an omission.
  Future<void> sweepPendingFiles() async {
    try {
      final pending = await CrashLogStore.loadPending();
      if (pending.isEmpty) return;
      final stored = await _container
          .read(diagnosticsRepositoryProvider)
          .saveLogs(context: await resolveContext(), logs: pending);
      if (!stored) return;
      for (final log in pending) {
        try {
          await log.file.delete();
        } catch (_) {
          // Kotlin ignores each `delete()`'s return value; a file that will
          // not go is swept again next start and, unlike Kotlin, lands on the
          // same row id rather than a duplicate.
        }
      }
    } catch (_) {
      // `Log.e(TAG, "Failed to sweep pending logs", e)`.
    }
  }

  /// The identity fields every row carries.
  ///
  /// Port of `resolveParentCode`/`resolvePlanetCode` and
  /// `appVersionProvider.versionName`. The two code fallbacks test
  /// **`isNotBlank`**, not `isNotEmpty` — a whitespace-only value on the user
  /// row falls back to the configuration — and the fallback itself is `""`
  /// when unset, never null, so a signed-out install on an unconfigured server
  /// uploads empty strings rather than nulls for both.
  ///
  /// `userId` is the user row's id, or `''` when nobody is signed in;
  /// [DiagnosticsRepository.serialize] turns that back into the JSON `null`
  /// Kotlin's `modelId?.let { userId = it }` produces.
  ///
  /// Every lookup degrades rather than throwing. `appVersionInfoProvider`
  /// reads `package_info_plus`, which is a platform channel and therefore
  /// unavailable on a headless engine — and a crash report with no version is
  /// far better than no crash report.
  Future<ApkLogContext> resolveContext() async {
    final user = await _session();
    final config = _container.read(serverConfigProvider);
    return (
      userId: user?.id ?? '',
      parentCode: _resolveCode(user?.parentCode, config?.parentCode),
      planetCode: _resolveCode(user?.planetCode, config?.code),
      version: await _version(),
    );
  }

  /// `model?.parentCode?.takeIf { it.isNotBlank() } ?: sharedPrefManager…`.
  static String _resolveCode(String? fromUser, String? fallback) {
    if (fromUser != null && fromUser.trim().isNotEmpty) return fromUser;
    return fallback ?? '';
  }

  Future<UserRow?> _session() async {
    try {
      return await resolveSessionIn(_container);
    } catch (_) {
      return null;
    }
  }

  Future<String> _version() async {
    try {
      return (await _container.read(appVersionInfoProvider.future)).version;
    } catch (_) {
      return '';
    }
  }

  Future<bool> _insert({
    required String type,
    required String error,
    required String time,
    String? id,
  }) async {
    return _container
        .read(diagnosticsRepositoryProvider)
        .saveLog(
          context: await resolveContext(),
          type: type,
          error: error,
          time: time,
          id: id,
        );
  }

  /// The report body. Kotlin sends `e.stackTraceToString()`, uncapped and
  /// whole; this adds the library label `FlutterErrorDetails` carries so a
  /// render failure is distinguishable from a root-zone one, and is otherwise
  /// the same shape.
  static String _describe(Object error, StackTrace? stack, String? library) {
    final buffer = StringBuffer();
    if (library != null && library.isNotEmpty) buffer.writeln('[$library]');
    buffer.writeln(error.toString());
    if (stack != null) buffer.write(stack.toString());
    return buffer.toString();
  }
}
