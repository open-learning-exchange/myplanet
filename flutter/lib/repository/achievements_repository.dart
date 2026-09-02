import 'dart:convert';

import 'package:drift/drift.dart';

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
  AchievementsRepository(this._dao);

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
    final rows = <AchievementsCompanion>[];
    for (final doc in docs) {
      final id = doc['_id'];
      if (id is! String || id.startsWith('_design')) continue;
      rows.add(
        AchievementsCompanion(
          id: Value(id),
          couchId: Value(id),
          rev: Value(doc['_rev'] as String? ?? ''),
          purpose: Value(doc['purpose'] as String? ?? ''),
          goals: Value(doc['goals'] as String? ?? ''),
          achievementsHeader: Value(doc['achievementsHeader'] as String? ?? ''),
          sendToNation: Value(
            doc['sendToNation'] is bool
                ? doc['sendToNation'] as bool
                : doc['sendToNation'] == 'true',
          ),
          dateSortOrder: Value(doc['dateSortOrder'] as String? ?? 'none'),
          createdOn: Value(doc['createdOn'] as String? ?? ''),
          username: Value(doc['username'] as String? ?? ''),
          parentCode: Value(doc['parentCode'] as String? ?? ''),
          achievementsJson: Value(jsonEncode(doc['achievements'] ?? [])),
          referencesJson: Value(jsonEncode(doc['references'] ?? [])),
          linksJson: Value(jsonEncode(doc['links'] ?? [])),
          otherInfoJson: Value(jsonEncode(doc['otherInfo'] ?? [])),
          resumeFileName: Value(doc['resumeFileName'] as String? ?? ''),
          uploaded: const Value(true),
        ),
      );
    }
    await _dao.insertDocs(rows);
    return rows.length;
  }

  /// Port of `UserRepositoryImpl.getAchievementsForUpload`.
  Future<List<AchievementRow>> pendingUploads() => _dao.pendingUploads();
}
