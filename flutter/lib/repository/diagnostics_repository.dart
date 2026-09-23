import 'package:drift/drift.dart' show Value;

import '../core/system/crash_log_store.dart';
import '../data/local/app_database.dart';

/// The four fields every `apk_log` row carries that do not come from the
/// report itself.
///
/// Kotlin resolves these inside `DiagnosticsRepositoryImpl` from three
/// injected sources (`UserRepository`, `SharedPrefManager`,
/// `AppVersionProvider`). The port resolves them at the call site and passes
/// them in, the way `SearchActivityRepository` takes `planetCode`/`parentCode`
/// rather than reaching for a session — which keeps the repository a pure
/// function of its inputs and testable without a provider container.
typedef ApkLogContext = ({
  String userId,
  String parentCode,
  String planetCode,
  String version,
});

/// Port of `repository/DiagnosticsRepositoryImpl.kt` — the `apk_log` half of
/// the operator telemetry Kotlin POSTs to `apk_logs`.
///
/// This had never been ported. When a myPlanet install crashed in the field,
/// Kotlin told the server and the port said nothing at all.
class DiagnosticsRepository {
  DiagnosticsRepository(this._dao);

  final ApkLogDao _dao;

  /// Port of `getPendingApkLogs` → `ApkLogDao.getPending`.
  Future<List<ApkLog>> pendingUploads() => _dao.pendingUploads();

  /// Port of `markApkLogsUploaded`. Returns the ids that matched no row, which
  /// `UploadConfigs.CrashLog`'s `markUploaded` reports back as failures.
  Future<Set<String>> markUploaded(Map<String, String> revsById) =>
      _dao.markUploadedBatch(revsById);

  /// Port of `saveLogToRoom(type, error, time)`.
  ///
  /// Returns false rather than throwing, exactly as the Kotlin's
  /// `try/catch(e) { printStackTrace(); false }` does: a failure to record a
  /// failure must not become a second failure. Every caller is an error hook
  /// or a best-effort sweep.
  ///
  /// [id] is the row key. It is supplied by the crash path, which derives it
  /// from the report's `(time, type)` so that an immediate insert and a later
  /// sweep of the same file are the same row — see [CrashLogStore] window 2.
  /// Everything else leaves it null and gets a fresh local id, which is what
  /// Kotlin's unconditional `UUID.randomUUID()` gives every row.
  Future<bool> saveLog({
    required ApkLogContext context,
    required String type,
    required String error,
    required String time,
    String? id,
  }) async {
    try {
      await _dao.insert(_companion(context, type, error, time, id));
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Port of `saveLogsToRoom(pendingLogs)` — the next-start sweep's insert.
  ///
  /// Kotlin resolves the user, version and codes **once** for the whole batch
  /// and reuses them for every row; the port takes the resolved [context] for
  /// the same reason. An empty list is a success, as Kotlin's early
  /// `return true` has it.
  Future<bool> saveLogs({
    required ApkLogContext context,
    required List<PendingCrashLog> logs,
  }) async {
    if (logs.isEmpty) return true;
    try {
      await _dao.insertAll([
        for (final log in logs)
          _companion(
            context,
            log.type,
            log.error,
            log.time,
            CrashLogStore.rowIdFor(time: log.time, type: log.type),
          ),
      ]);
      return true;
    } catch (_) {
      return false;
    }
  }

  ApkLogsCompanion _companion(
    ApkLogContext context,
    String type,
    String error,
    String time,
    String? id,
  ) {
    return ApkLogsCompanion.insert(
      id: id ?? _localId(),
      userId: Value(context.userId),
      type: Value(type),
      // `buildApkLog` assigns the error only when it is non-empty, leaving the
      // field null otherwise. The column stores '' for that, and [serialize]
      // turns it back into the JSON null Kotlin sends.
      error: Value(error),
      // Always '', unconditionally, in `buildApkLog`. Vestigial in both apps.
      page: const Value(''),
      parentCode: Value(context.parentCode),
      version: Value(context.version),
      // The **planet code**, not a date — `buildApkLog` does
      // `createdOn = planetCode`.
      createdOn: Value(context.planetCode),
      time: Value(time),
    );
  }

  /// Port of `ApkLog.serialize(log, customDeviceName)`, minus the device
  /// fields.
  ///
  /// The four Kotlin adds at serialize time rather than from the row —
  /// `androidId`, `app`, `deviceName`, `customDeviceName` — are layered on by
  /// [ApkLogUploader] at queue time, which is where every other uploader here
  /// adds them. `DeviceIdentity.documentFields` is exactly that set:
  /// `serialize` calls `addDocumentOrigin()` with no argument (so the id is
  /// the `androidId + "_" + Build.ID` composite, not the bare ANDROID_ID) and
  /// then puts both device names.
  ///
  /// **Empty is sent as JSON null for the two fields Kotlin leaves null.**
  /// `buildApkLog` only assigns `error` when non-empty and only assigns
  /// `userId` when the user model has an id, so the document Planet receives
  /// carries `null` there rather than `""`. `page` is the opposite case — it
  /// is assigned `""` explicitly — so it goes out as an empty string.
  static Map<String, dynamic> serialize(ApkLog row) {
    return {
      'type': row.type,
      'error': row.error.isEmpty ? null : row.error,
      'page': row.page,
      'time': row.time,
      'userId': row.userId.isEmpty ? null : row.userId,
      'version': row.version,
      'createdOn': row.createdOn,
      'parentCode': row.parentCode,
    };
  }
}

/// Mints a local key for a row with no natural one, following the
/// `microsecondsSinceEpoch` convention the other locally-authored
/// repositories use (`SearchActivityRepository._localId`,
/// `RatingsRepository._defaultId`).
String _localId() => DateTime.now().microsecondsSinceEpoch.toString();
