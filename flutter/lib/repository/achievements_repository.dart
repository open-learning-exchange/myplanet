import 'dart:convert';

import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/files/achievement_files.dart';
import '../core/network/network_result.dart';
import '../core/sync/sync_result.dart';
import '../core/sync/table_walk.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';

/// The editable state carried by one update of the achievements ledger.
///
/// Port of the free-form fields `EditAchievementFragment` assembles and
/// `UserRepositoryImpl.updateAchievement` persists.
class AchievementInput {
  const AchievementInput({
    this.purpose = '',
    this.goals = '',
    this.achievementsHeader = '',
    this.sendToNation = false,
    this.achievementsJson = '[]',
    this.referencesJson = '[]',
    this.createdOn = '',
    this.username = '',
    this.parentCode = '',
    this.resumeFileName = '',
  });

  final String purpose;
  final String goals;
  final String achievementsHeader;
  final bool sendToNation;
  final String achievementsJson;
  final String referencesJson;
  final String createdOn;
  final String username;
  final String parentCode;
  final String resumeFileName;
}

/// Port of the achievement half of `repository/UserRepositoryImpl.kt` —
/// the lookup and the upsert `EditAchievementFragment` performs, and the
/// sync-in `bulkInsertAchievementsFromSync`.
///
/// Reads and writes go through [AchievementDao]; the converter between the
/// JSON columns and the parsed lists is a plain helper here (the Kotlin puts
/// it on `Achievement` and on Room's `Converters`).
class AchievementsRepository {
  AchievementsRepository(this._api, this._dao);

  /// `TransactionSyncManager.syncDb`'s default page size, which `achievements`
  /// takes.
  static const int initialBatchSize = 100;

  final PlanetApi _api;
  final AchievementDao _dao;

  /// Port of the `"${user.id}@${user.planetCode}"` derivation both screens
  /// build by hand.
  static String idFor(String userId, String planetCode) =>
      '$userId@$planetCode';

  /// Port of `Achievement.achievementsArray` — the `achievements` JSON decoded.
  static List<Map<String, dynamic>> achievementsArray(String json) =>
      _decodeList(json);

  /// Port of `Achievement.getReferencesArray` — the `references` JSON decoded.
  static List<Map<String, dynamic>> referencesArray(String json) =>
      _decodeList(json);

  static List<Map<String, dynamic>> _decodeList(String json) {
    try {
      final parsed = jsonDecode(json);
      if (parsed is List) {
        return [
          for (final item in parsed)
            if (item is Map<String, dynamic>) item,
        ];
      }
      return const [];
    } on FormatException {
      return const [];
    }
  }

  /// Port of the `resources` read on an achievement entry.
  static List<Map<String, dynamic>> resourcesOf(Map<String, dynamic> entry) {
    final raw = entry['resources'];
    if (raw is List) {
      return [
        for (final item in raw)
          if (item is Map<String, dynamic>) item,
      ];
    }
    return const [];
  }

  /// Port of `Achievement.serialize(achievement)`. The Kotlin stores the
  /// lists as JSON-in-a-string through Room's `Converters`; the document it
  /// emits re-parses those into real arrays, so the preserved JSON columns
  /// serialize straight through.
  ///
  /// `_id` falls back to the row's own id, which is the derived
  /// `"$userId@$planetCode"` [idFor] builds. Kotlin has one field: an
  /// `Achievement`'s `_id` *is* its primary key, set by
  /// `initializeAchievement`, so the first PUT creates
  /// `achievements/<that id>`. The port splits the local id from the couch
  /// id, and nothing fills the couch id in until an upload succeeds — so
  /// reading `couchId` alone emitted `'_id': ''` for every ledger the user
  /// had just authored, the outbox handler rejected it as naming no
  /// document, and a first-time achievement could never reach the server.
  static Map<String, dynamic> serialize(AchievementRow row) => {
    '_id': row.couchId.isEmpty ? row.id : row.couchId,
    if (row.rev.isNotEmpty) '_rev': row.rev,
    'goals': row.goals,
    'purpose': row.purpose,
    'achievementsHeader': row.achievementsHeader,
    'sendToNation': row.sendToNation,
    'dateSortOrder': row.dateSortOrder.isEmpty ? 'none' : row.dateSortOrder,
    'createdOn': row.createdOn,
    'username': row.username,
    'parentCode': row.parentCode,
    'achievements': achievementsArray(row.achievementsJson),
    'references': referencesArray(row.referencesJson),
    'links': _decodeList(row.linksJson),
    'otherInfo': _decodeList(row.otherInfoJson),
    'resumeFileName': row.resumeFileName,
  };

  /// Port of `UserRepositoryImpl.initializeAchievement`: look the row up by
  /// its derived id (`"$userId@$planetCode"` — the caller builds it) and
  /// create an empty one when the user has never edited it.
  Future<AchievementRow?> getOrInitialize(String id) async {
    final existing = await _dao.getById(id);
    if (existing != null) return existing;
    await _dao.upsert(
      AchievementsCompanion(
        id: Value(id),
        couchId: const Value(''),
        rev: const Value(''),
        resumeFileName: const Value(''),
        // `Achievement()` starts `isUpdated = false`, and `uploaded` is the
        // port's inverted name for it — so the placeholder must be `true`.
        // The column's default is `false`, the wrong way round, and leaving it
        // there made a blank row indistinguishable from an edit the user has
        // not uploaded: the sync-in skipped the server's real ledger to
        // "preserve" it, permanently (nothing ever sets `uploaded`), and then
        // handed the blank row a `_rev` so the next save PUT it over the real
        // one. Opening the achievements screen once was enough to trigger it,
        // because `achievementEntryProvider` calls this on watch.
        uploaded: const Value(true),
      ),
    );
    return _dao.getById(id);
  }

  /// Port of `UserRepositoryImpl.updateAchievement`. The caller carries the
  /// identity triple (`createdOn`, `username`, `parentCode`) through, the
  /// way the Kotlin does from `EditAchievementFragment`; the write flags the
  /// row unsynced (the Kotlin `isUpdated = true`), so an edit always lands
  /// on the upload backlog.
  Future<void> update(String id, AchievementInput input) async {
    final existing = await _dao.getById(id);
    await _dao.upsert(
      AchievementsCompanion(
        id: Value(id),
        couchId: Value(existing?.couchId ?? ''),
        rev: Value(existing?.rev ?? ''),
        purpose: Value(input.purpose),
        goals: Value(input.goals),
        achievementsHeader: Value(input.achievementsHeader),
        sendToNation: Value(input.sendToNation),
        achievementsJson: Value(input.achievementsJson),
        referencesJson: Value(input.referencesJson),
        createdOn: Value(input.createdOn),
        username: Value(input.username),
        parentCode: Value(input.parentCode),
        resumeFileName: Value(input.resumeFileName),
        uploaded: const Value(false),
      ),
    );
  }

  /// Port of `Achievement.fromJson` on a `_all_docs` page —
  /// `UserRepositoryImpl.bulkInsertAchievementsFromSync` skips `_design` rows
  /// and upserts the rest; the `isUpdated = false` reset matches the port's
  /// `uploaded = true` for sync-in rows.
  Future<int> syncAchievements(List<Map<String, dynamic>> docs) async {
    if (docs.isEmpty) return 0;
    final ids = <String>[
      for (final doc in docs)
        if (JsonUtils.getString('_id', doc) case final id when id.isNotEmpty)
          id,
    ];
    final pending = {
      for (final row in await _dao.getByIds(ids))
        if (!row.uploaded) row.id,
    };

    final rows = <AchievementsCompanion>[];
    final identityPatches = <(String, String)>[];
    for (final doc in docs) {
      final id = JsonUtils.getString('_id', doc);
      if (id.isEmpty || id.startsWith('_design')) continue;
      // A ledger the user has edited but not yet uploaded takes only the
      // `_rev`. The Kotlin overwrites the whole row and clears `isUpdated`,
      // which discards the edit; and since the port's rows carry no `_rev`
      // until a walk supplies one, the `_rev` is what a later save needs to be
      // a PUT rather than a 409-ing POST. Same shape as the read state Phase 98
      // had to preserve.
      if (pending.contains(id)) {
        identityPatches.add((id, JsonUtils.getString('_rev', doc)));
        continue;
      }
      rows.add(
        AchievementsCompanion(
          id: Value(id),
          couchId: Value(id),
          rev: Value(JsonUtils.getString('_rev', doc)),
          purpose: Value(JsonUtils.getString('purpose', doc)),
          goals: Value(JsonUtils.getString('goals', doc)),
          achievementsHeader: Value(
            JsonUtils.getString('achievementsHeader', doc),
          ),
          sendToNation: Value(JsonUtils.getBool('sendToNation', doc)),
          dateSortOrder: Value(_orDefault(doc, 'dateSortOrder', 'none')),
          createdOn: Value(JsonUtils.getString('createdOn', doc)),
          username: Value(JsonUtils.getString('username', doc)),
          parentCode: Value(JsonUtils.getString('parentCode', doc)),
          achievementsJson: Value(jsonEncode(doc['achievements'] ?? [])),
          referencesJson: Value(jsonEncode(doc['references'] ?? [])),
          linksJson: Value(jsonEncode(doc['links'] ?? [])),
          otherInfoJson: Value(jsonEncode(doc['otherInfo'] ?? [])),
          resumeFileName: Value(JsonUtils.getString('resumeFileName', doc)),
          uploaded: const Value(true),
        ),
      );
    }

    await _dao.insertDocs(rows);
    for (final (id, rev) in identityPatches) {
      await _dao.recordServerRev(id, rev);
    }
    return rows.length + identityPatches.length;
  }

  /// `JsonUtils.getString` with a fallback for a key the document omits, where
  /// the empty string is not the value the column should hold.
  static String _orDefault(
    Map<String, dynamic> doc,
    String key,
    String fallback,
  ) {
    final value = JsonUtils.getString(key, doc);
    return value.isEmpty ? fallback : value;
  }

  /// Port of the `"achievements"` arm of `TransactionSyncManager.syncDb`
  /// (`:260-262`) together with `downloadCvAttachmentsFromBatch` (`:366-383`),
  /// which the Kotlin runs immediately after each page.
  ///
  /// Phase 116 found [syncAchievements] written and never called: a second
  /// device showed a blank ledger, and saving there POSTed a document with no
  /// `_rev`, which CouchDB answers 409 — a status `OutboxDrainer` classifies as
  /// permanent, so the row was abandoned with no snackbar and no log. Both ends
  /// close together; adding only the upload would have made it worse.
  ///
  /// **Never prunes.** The Kotlin issues no delete, and `achievements` is a
  /// preserved local-authority table whose rows are keyed by a derived
  /// `"<userId>@<planetCode>"` that exists locally before it exists on the
  /// server.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) => walkAllDocs(
    api: _api,
    config: config,
    table: 'achievements',
    initialBatchSize: initialBatchSize,
    onProgress: onProgress,
    insert: (docs) async {
      final saved = await syncAchievements(docs);
      await downloadCvAttachments(docs, config: config);
      return saved;
    },
  );

  /// Port of `TransactionSyncManager.downloadCvAttachmentsFromBatch`.
  ///
  /// Downloads `achievements/<docId>/resume.pdf` for every document that both
  /// names a `resumeFileName` and actually carries the attachment, skipping
  /// anything already on disk (`!destFile.exists()`) and de-duplicating within
  /// the page (`inProgress.add(resumeFileName)`) — two documents can name the
  /// same file.
  ///
  /// Best-effort throughout, like the Kotlin's `catch (_: Exception) { }`: a CV
  /// that fails to download must not fail the walk that carried the ledger.
  Future<void> downloadCvAttachments(
    List<Map<String, dynamic>> docs, {
    required ServerConfig config,
  }) async {
    final started = <String>{};
    for (final doc in docs) {
      final docId = JsonUtils.getString('_id', doc);
      if (docId.isEmpty || docId.startsWith('_design')) continue;
      final resumeFileName = JsonUtils.getString('resumeFileName', doc);
      if (resumeFileName.isEmpty) continue;
      final attachments = doc['_attachments'];
      if (attachments is! Map || !attachments.containsKey('resume.pdf')) {
        continue;
      }
      if (!started.add(resumeFileName)) continue;
      if (await AchievementFiles.hasResume(resumeFileName)) continue;

      final result = await _api.getBytes(
        '${UrlUtils.dbUrl(config)}/achievements/$docId/resume.pdf',
        authHeader: UrlUtils.authHeader(config),
      );
      if (result is NetworkSuccess<List<int>> && result.data.isNotEmpty) {
        await AchievementFiles.write(
          resumeFileName: resumeFileName,
          bytes: result.data,
        );
      }
    }
  }

  /// Port of `UserRepositoryImpl.getAchievementsForUpload`.
  Future<List<AchievementRow>> pendingUploads() => _dao.pendingUploads();
}
