import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// One crash report recovered from disk, waiting to become an `apk_log` row.
///
/// Port of `CrashLogStore.PendingLog`.
class PendingCrashLog {
  const PendingCrashLog({
    required this.file,
    required this.type,
    required this.time,
    required this.error,
  });

  final File file;
  final String type;

  /// Epoch millis as text, because that is what the column and the uploaded
  /// document carry — see the `ApkLogs.time` column.
  final String time;
  final String error;
}

/// Synchronous, dependency-free persistence for crash reports.
///
/// Port of `utils/CrashLogStore.kt`, whose own header states the reason and it
/// transfers unchanged: *"Reports are written here before any coroutine or
/// Room machinery runs, because app shutdown can happen before launched
/// coroutines finish persisting rows."* A Dart isolate dying takes every
/// pending `await` with it just as surely as a dying Android process takes a
/// launched coroutine, so the first thing the error hook does is a
/// **synchronous** file write; the database row and the outbox entry follow on
/// a future that may never complete.
///
/// ### The three windows, and what closes each
///
/// 1. **Between the file write and the row insert** — the file is on disk and
///    no row exists. [loadPending] sweeps it on the next app start. This is
///    the window the class exists for.
/// 2. **Between the row insert and the file delete** — a row exists and so
///    does the file, so the next start's sweep would insert the report a
///    second time. Kotlin duplicates here: `buildApkLog` mints a fresh
///    `UUID.randomUUID()` for every insert, so nothing relates the swept row
///    to the one already stored. **The port does not**, and this is its one
///    deliberate divergence in this class: [rowIdFor] derives the row's key
///    from the report's own `(time, type)` — the same pair the filename is
///    built from — so the immediate insert and a later sweep of the same file
///    produce the *same* id, so the sweep's `insertOnConflictUpdate` lands back
///    on the row already there instead of adding a second report. (It is not
///    literally a no-op — it rewrites the identity columns with today's
///    context, which is the same re-stamping the sweep does anyway.)
///
///    **This is a trade, not a free win, and an earlier revision of this
///    comment claimed otherwise.** It argued the derivation "cannot collide
///    more often than the filename already does, since two reports sharing a
///    millisecond and a type already overwrite one another's file in Kotlin".
///    The premise is true and the conclusion does not follow: in Kotlin a
///    filename collision loses the *file*, while `buildApkLog` still mints a
///    fresh `UUID.randomUUID()`, so both rows reach `apk_logs`. Deriving the
///    key moves the collision from the file onto the row, where Kotlin never
///    had one. So a frame that reports ten framework errors in the same
///    millisecond under the same type files **one** row here where Kotlin
///    would file ten. De-duplicating a burst of identical reports is the
///    better behaviour for an operator reading this database, and closing
///    window 2 is worth it — but it is a decision, and it is written down as
///    one.
/// 3. **Between the row insert and the outbox enqueue** — nothing is lost.
///    The row is durable in `apk_log` with `_rev = ''`, which is precisely the
///    pending predicate, and the sweep in `background_entrypoint.dart`
///    re-enqueues every pending row on each background invocation.
class CrashLogStore {
  const CrashLogStore._();

  static const String dirName = 'pending_logs';
  static const String fileExtension = '.log';

  /// `CrashLogStore.MAX_PENDING_FILES`. At the cap a new report is **dropped**
  /// rather than evicting an older one — Kotlin returns `null` without
  /// writing, and the oldest reports are the ones nearest the first failure,
  /// which is usually the informative one.
  static const int maxPendingFiles = 20;

  /// Overridable so tests do not need a platform channel, matching
  /// `SubmitPhotosFiles.baseDirectory` and its siblings.
  static Future<Directory> Function() baseDirectory =
      getApplicationDocumentsDirectory;

  static Directory? _directory;

  /// The primed directory, or `null` if [prime] has not run or failed.
  ///
  /// Kotlin gets `context.filesDir` synchronously from the Application
  /// object; Dart's equivalent is a platform channel and therefore a future,
  /// which a dying isolate cannot await. So the path is resolved once at
  /// startup and cached — the same shape as the UI-primed device-identity and
  /// `disk_stats` caches, and for the same reason.
  static Directory? get primedDirectory => _directory;

  /// Resolves and creates the report directory. Call once during bootstrap.
  ///
  /// Returns `null` on any failure; [save] then writes nothing, which is the
  /// same outcome Kotlin reaches when `mkdirs()` fails. Telemetry must never
  /// be the reason an app cannot start.
  static Future<Directory?> prime() async {
    try {
      final base = await baseDirectory();
      final dir = Directory(p.join(base.path, dirName));
      if (!dir.existsSync()) {
        await dir.create(recursive: true);
      }
      return _directory = dir;
    } catch (_) {
      return _directory = null;
    }
  }

  /// Forgets the primed directory. Tests only.
  @visibleForTesting
  static void resetForTest() => _directory = null;

  /// The `apk_log` row key for a report — see window 2 in the class doc.
  ///
  /// Deliberately the same string the filename is built from, so the two
  /// cannot drift: [loadPending] reads `(time, type)` back out of the name and
  /// this rebuilds the identical key.
  static String rowIdFor({required String time, required String type}) =>
      'crash:$time:$type';

  /// Writes a report and returns its file, or `null` if it was not written.
  ///
  /// **Synchronous by contract**: every call site is an error hook running
  /// while the isolate may be about to die. Port of `CrashLogStore.save`,
  /// including its cap check happening *before* the write and its
  /// swallow-everything failure mode.
  static File? save({
    required String type,
    required String error,
    required String time,
  }) {
    final dir = _directory;
    if (dir == null) return null;
    try {
      if (!dir.existsSync()) dir.createSync(recursive: true);
      // Kotlin counts the *valid* entries and refuses at the cap, so a
      // directory full of junk names does not lock the store. `take(MAX)`
      // there is an early exit, not a different count.
      final valid = dir
          .listSync()
          .whereType<File>()
          .where((file) => _parse(file) != null)
          .length;
      if (valid >= maxPendingFiles) return null;
      // [time] is not passed through [_segment] because it must stay exactly
      // what [_parse] reads back — a `int.tryParse`-able prefix. Refusing a
      // non-numeric one closes the same seam sanitising would, and does it
      // without producing a name this class could no longer read: a sanitised
      // `../x` would become `x`, which parses as no number and would make the
      // report permanently invisible rather than merely unwritten.
      if (int.tryParse(time) == null) return null;
      final file = File(
        p.join(dir.path, '${time}_${_segment(type)}$fileExtension'),
      );
      // **No `flush: true`.** Kotlin's `file.writeText(error)` does not
      // `fsync`, and this write runs on the caller's thread — which for
      // Kotlin is a thread already dying and for the port is the live UI
      // thread, because `FlutterError.onError` fires for errors Flutter
      // carries on from. An `fsync` is routinely 10–50 ms on cheap Android
      // storage, so a frame reporting several recoverable errors would spend
      // that many times over: jank, or an ANR, caused by the telemetry.
      // Without it the bytes reach the OS page cache, which survives the
      // process dying — which is the failure this store is for. Only a kernel
      // panic or power loss loses them, and those lose the row too.
      file.writeAsStringSync(error);
      return file;
    } catch (_) {
      // Kotlin logs and returns null. There is nowhere safer to report a
      // failure to report a failure.
      return null;
    }
  }

  /// Every readable report left on disk. Port of
  /// `CrashLogStore.loadPendingLogs`.
  ///
  /// Async where [save] is synchronous, and the asymmetry is the Kotlin's:
  /// `MainApplication.sweepPendingLogs` runs the read on
  /// `dispatcherProvider.io` because nothing is dying at that moment, while
  /// `persistCriticalLog` writes on whatever thread crashed. A file that
  /// cannot be read is skipped rather than failing the sweep, so one bad entry
  /// cannot strand the rest.
  static Future<List<PendingCrashLog>> loadPending() async {
    final dir = _directory;
    if (dir == null) return const [];
    try {
      if (!dir.existsSync()) return const [];
      final result = <PendingCrashLog>[];
      for (final entity in dir.listSync()) {
        if (entity is! File) continue;
        final parsed = _parse(entity);
        if (parsed == null) continue;
        try {
          result.add(
            PendingCrashLog(
              file: entity,
              type: parsed.type,
              time: parsed.time,
              error: await entity.readAsString(),
            ),
          );
        } catch (_) {
          continue;
        }
      }
      return result;
    } catch (_) {
      return const [];
    }
  }

  /// A filename's `(time, type)`, or `null` when the name is not one of ours.
  ///
  /// Kotlin's three rejections, kept exactly: not a `.log` file, no `_`
  /// separator (or one at index 0, so an empty time is refused), and a time
  /// that is not a number. A rejected file is invisible to both the cap count
  /// and the sweep, so it neither blocks new reports nor is ever deleted —
  /// also Kotlin's behaviour.
  static _ParsedName? _parse(File file) {
    final name = p.basename(file.path);
    if (!name.endsWith(fileExtension)) return null;
    final stem = name.substring(0, name.length - fileExtension.length);
    final separator = stem.indexOf('_');
    if (separator <= 0) return null;
    final time = stem.substring(0, separator);
    if (int.tryParse(time) == null) return null;
    return _ParsedName(time, stem.substring(separator + 1));
  }

  /// Reduces a type to a single path segment.
  ///
  /// Kotlin interpolates the type into the filename unguarded. In practice
  /// only `ApkLog.ERROR_TYPE_CRASH` ("crash") reaches the file store —
  /// `createLog`'s types never do — so the hole is unreachable there; the port
  /// closes it anyway, the way `AchievementFiles._segment` does, because the
  /// hook is now a public seam a caller could pass anything to.
  static String _segment(String value) {
    final base = p.basename(value.replaceAll(r'\', '/'));
    final cleaned = base.replaceAll(RegExp(r'[^A-Za-z0-9 ._-]'), '');
    return cleaned.isEmpty || cleaned == '.' || cleaned == '..'
        ? 'log'
        : cleaned;
  }
}

class _ParsedName {
  const _ParsedName(this.time, this.type);
  final String time;
  final String type;
}
