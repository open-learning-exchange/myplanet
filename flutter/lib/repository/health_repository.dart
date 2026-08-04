import 'dart:convert';

import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/health_models.dart';

/// Port of `repository/HealthRepositoryImpl.kt`.
///
/// HealthRepository manages health examination records, including local storage
/// in Drift and sync with the CouchDB health database.
class HealthRepository {
  HealthRepository(
    this._api,
    this._dao, {
    ServerConfig? config,
    String Function()? createId,
  })  : _config = config,
        _createId = createId ?? _defaultId;

  final PlanetApi _api;
  final HealthExaminationDao _dao;
  final ServerConfig? _config;
  final String Function() _createId;

  static String _defaultId() =>
      'health-${DateTime.now().microsecondsSinceEpoch}';

  /// Get health examination by user id or examination id.
  Future<HealthExaminationRow?> getByIdOrUserId(String id) =>
      _dao.getByIdOrUserId(id);

  /// Get health examination by id.
  Future<HealthExaminationRow?> getById(String id) => _dao.getById(id);

  /// Get all examinations for a user.
  Future<List<HealthExaminationRow>> getForUser(String userId) =>
      _dao.getForUser(userId);

  /// Get examinations by profileId.
  Future<List<HealthExaminationRow>> getByProfileId(String profileId) =>
      _dao.getByProfileId(profileId);

  /// Get updated examinations that need syncing.
  Future<List<HealthExaminationRow>> getUpdated() => _dao.getUpdated();

  /// Get updated examinations for a specific user.
  Future<List<HealthExaminationRow>> getUpdatedForUser(String userId) =>
      _dao.getUpdatedForUser(userId);

  /// Create a new health examination.
  Future<String> createExamination({
    required String userId,
    String? profileId,
    required double temperature,
    required int pulse,
    String? bp,
    required double height,
    required double weight,
    String? vision,
    String? hearing,
    String? conditions,
    String? gender,
    int? age,
    bool selfExamination = false,
    String? planetCode,
    String? data,
    bool hasInfo = false,
  }) async {
    final id = _createId();
    final now = DateTime.now().millisecondsSinceEpoch;
    await _dao.upsert(
      HealthExaminationsCompanion.insert(
        id: id,
        userId: Value(userId),
        temperature: Value(temperature),
        pulse: Value(pulse),
        bp: Value(bp),
        height: Value(height),
        weight: Value(weight),
        vision: Value(vision),
        hearing: Value(hearing),
        conditions: Value(conditions),
        selfExamination: Value(selfExamination),
        planetCode: Value(planetCode),
        hasInfo: Value(hasInfo),
        profileId: Value(profileId),
        gender: Value(gender),
        age: Value(age ?? 0),
        date: Value(now),
        data: Value(data),
        isUpdated: const Value(true),
      ),
    );
    return id;
  }

  /// Update an existing examination.
  Future<void> updateExamination(
    String id, {
    double? temperature,
    int? pulse,
    String? bp,
    double? height,
    double? weight,
    String? vision,
    String? hearing,
    String? conditions,
    String? data,
    bool? hasInfo,
  }) async {
    final existing = await _dao.getById(id);
    if (existing == null) return;

    await _dao.upsert(
      existing.toCompanion(false).copyWith(
            temperature: Value(temperature ?? existing.temperature),
            pulse: Value(pulse ?? existing.pulse),
            bp: Value(bp ?? existing.bp),
            height: Value(height ?? existing.height),
            weight: Value(weight ?? existing.weight),
            vision: Value(vision ?? existing.vision),
            hearing: Value(hearing ?? existing.hearing),
            conditions: Value(conditions ?? existing.conditions),
            data: Value(data ?? existing.data),
            hasInfo: Value(hasInfo ?? existing.hasInfo),
            isUpdated: const Value(true),
          ),
    );
  }

  /// Insert or update a health examination row.
  Future<void> upsert(HealthExaminationsCompanion row) => _dao.upsert(row);

  /// Insert or update multiple health examinations.
  Future<void> upsertAll(List<HealthExaminationsCompanion> rows) =>
      _dao.upsertAll(rows);

  /// Mark examination as uploaded with the server revision.
  Future<void> markUploaded(String id, String? rev) =>
      _dao.markUploaded(id, rev);

  /// Mark multiple examinations as uploaded.
  Future<void> markUploadedBatch(Map<String, String?> idToRev) async {
    for (final entry in idToRev.entries) {
      await _dao.markUploaded(entry.key, entry.value);
    }
  }

  /// Update the userId for an examination.
  Future<void> updateUserId(String id, String userId) =>
      _dao.updateUserId(id, userId);

  /// Parse examination conditions from JSON string.
  Map<String, bool> parseConditions(String? conditionsJson) {
    if (conditionsJson == null || conditionsJson.isEmpty) return {};
    try {
      final decoded = jsonDecode(conditionsJson);
      if (decoded is! Map) return {};
      return decoded.map((k, v) => MapEntry(k.toString(), v == true));
    } catch (e) {
      return {};
    }
  }

  /// Convert database row to domain model.
  static HealthExamination rowToModel(HealthExaminationRow row) {
    return HealthExamination(
      id: row.id,
      couchId: row.couchId,
      rev: row.rev,
      userId: row.userId,
      temperature: row.temperature,
      pulse: row.pulse,
      bp: row.bp,
      height: row.height,
      weight: row.weight,
      vision: row.vision,
      hearing: row.hearing,
      conditions: row.conditions,
      selfExamination: row.selfExamination,
      planetCode: row.planetCode,
      hasInfo: row.hasInfo,
      profileId: row.profileId,
      creatorId: row.creatorId,
      gender: row.gender,
      age: row.age,
      date: row.date,
      data: row.data,
      isUpdated: row.isUpdated,
    );
  }

  /// Convert domain model to database companion for insertion.
  static HealthExaminationsCompanion modelToCompanion(
    HealthExamination model, {
    bool update = true,
  }) {
    return HealthExaminationsCompanion(
      id: Value(model.id),
      couchId: Value(model.couchId),
      rev: Value(model.rev),
      userId: Value(model.userId),
      temperature: Value(model.temperature),
      pulse: Value(model.pulse),
      bp: Value(model.bp),
      height: Value(model.height),
      weight: Value(model.weight),
      vision: Value(model.vision),
      hearing: Value(model.hearing),
      conditions: Value(model.conditions),
      selfExamination: Value(model.selfExamination),
      planetCode: Value(model.planetCode),
      hasInfo: Value(model.hasInfo),
      profileId: Value(model.profileId),
      creatorId: Value(model.creatorId),
      gender: Value(model.gender),
      age: Value(model.age),
      date: Value(model.date),
      data: Value(model.data),
      isUpdated: Value(model.isUpdated),
    );
  }

  /// Sync health examinations from CouchDB.
  Future<SyncResult> sync({void Function(SyncProgress)? onProgress}) async {
    if (_config == null) return const SyncFailed('No server config');

    final config = _config;
    final dbUrl = UrlUtils.dbUrl(config);
    final auth = UrlUtils.basicAuthHeader('satellite', config.pin);

    final countResult = await _api.getJsonObject(
      '$dbUrl/health/_all_docs?limit=0',
      authHeader: auth,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }
    final total = JsonUtils.getInt('total_rows', countResult.data);
    if (total == 0) {
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    const batchSize = 100;
    var skip = 0;
    var synced = 0;
    while (skip < total) {
      final result = await _api.getJsonObject(
        '$dbUrl/health/_all_docs?include_docs=true&limit=$batchSize&skip=$skip',
        authHeader: auth,
      );
      if (result is! NetworkSuccess<Map<String, dynamic>>) {
        return SyncFailed(describeNetworkFailure(result));
      }
      final rows = result.data['rows'];
      if (rows is! List || rows.isEmpty) break;

      final documents = rows
          .whereType<Map<String, dynamic>>()
          .map((row) => JsonUtils.getObject('doc', row))
          .whereType<Map<String, dynamic>>()
          .where((doc) => !doc['_id'].toString().startsWith('_design/'))
          .toList(growable: false);

      await cacheDocuments(documents);
      synced += documents.length;
      skip += rows.length;
      onProgress?.call(
        SyncProgress(completed: skip.clamp(0, total), total: total),
      );
    }
    return SyncComplete(synced);
  }

  /// Cache documents from the server, preserving local edits.
  Future<int> cacheDocuments(List<Map<String, dynamic>> documents) async {
    if (documents.isEmpty) return 0;
    final ids = documents
        .map((doc) => doc['_id']?.toString() ?? '')
        .where((id) => id.isNotEmpty)
        .toList(growable: false);

    final existing = <String, HealthExaminationRow>{};
    for (final row in await _dao.getForUser(ids.first)) {
      existing[row.id] = row;
    }
    // Also fetch by id
    for (final id in ids) {
      final row = await _dao.getById(id);
      if (row != null) existing[id] = row;
    }

    final rows = <HealthExaminationsCompanion>[];
    for (final doc in documents) {
      final id = doc['_id']?.toString() ?? '';
      final current = existing[id];
      // Don't overwrite locally edited rows
      if (current?.isUpdated == true) continue;
      rows.add(_docToCompanion(doc, existing: current));
    }
    await _dao.upsertAll(rows);
    return documents.length;
  }

  static HealthExaminationsCompanion _docToCompanion(
    Map<String, dynamic> doc, {
    HealthExaminationRow? existing,
  }) {
    return HealthExaminationsCompanion(
      id: Value(doc['_id']?.toString() ?? ''),
      couchId: Value(doc['_id']?.toString()),
      rev: Value(doc['_rev']?.toString()),
      userId: Value(doc['_id']?.toString()),
      temperature: Value(_parseDouble(doc['temperature'])),
      pulse: Value(_parseInt(doc['pulse'])),
      bp: Value(doc['bp']?.toString()),
      height: Value(_parseDouble(doc['height'])),
      weight: Value(_parseDouble(doc['weight'])),
      vision: Value(doc['vision']?.toString()),
      hearing: Value(doc['hearing']?.toString()),
      conditions: Value(doc['conditions']?.toString()),
      selfExamination: Value(doc['selfExamination'] == true),
      planetCode: Value(doc['planetCode']?.toString()),
      hasInfo: Value(doc['hasInfo'] == true),
      profileId: Value(doc['profileId']?.toString()),
      creatorId: Value(doc['creatorId']?.toString()),
      gender: Value(doc['gender']?.toString()),
      age: Value(_parseInt(doc['age'])),
      date: Value(_parseInt(doc['date'])),
      data: Value(doc['data']?.toString()),
      isUpdated: Value(existing?.isUpdated ?? false),
    );
  }

  static double _parseDouble(dynamic value) {
    if (value == null) return 0;
    if (value is double) return value;
    if (value is int) return value.toDouble();
    if (value is String && value.isNotEmpty) {
      return double.tryParse(value) ?? 0;
    }
    return 0;
  }

  static int _parseInt(dynamic value) {
    if (value == null) return 0;
    if (value is int) return value;
    if (value is double) return value.toInt();
    if (value is String && value.isNotEmpty) {
      return int.tryParse(value) ?? 0;
    }
    return 0;
  }

  /// Serialize a row to JSON for upload.
  static Map<String, dynamic> serialize(HealthExaminationRow row) {
    return {
      if (row.id.isNotEmpty) '_id': row.id,
      if (row.rev != null && row.rev!.isNotEmpty) '_rev': row.rev,
      if (row.data != null) 'data': row.data,
      'temperature': row.temperature,
      'pulse': row.pulse,
      if (row.bp != null) 'bp': row.bp,
      'height': row.height,
      'weight': row.weight,
      if (row.vision != null) 'vision': row.vision,
      if (row.hearing != null) 'hearing': row.hearing,
      'date': row.date,
      'selfExamination': row.selfExamination,
      if (row.planetCode != null) 'planetCode': row.planetCode,
      'hasInfo': row.hasInfo,
      if (row.profileId != null) 'profileId': row.profileId,
      if (row.creatorId != null) 'creatorId': row.creatorId,
      if (row.gender != null) 'gender': row.gender,
      'age': row.age,
      if (row.conditions != null) 'conditions': row.conditions,
    };
  }
}
