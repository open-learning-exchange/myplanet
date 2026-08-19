import 'dart:math';

import 'package:drift/drift.dart';
import 'package:path/path.dart' as p;

import '../data/local/app_database.dart';

class DuplicatePersonalTitle implements Exception {
  const DuplicatePersonalTitle();
}

/// Offline CRUD portion of `repository/PersonalsRepositoryImpl.kt`.
class PersonalsRepository {
  PersonalsRepository(
    this._dao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId = createId ?? _randomId;

  final PersonalDao _dao;
  final DateTime Function() _now;
  final String Function() _createId;

  Stream<List<PersonalRow>> watch(String userId) => _dao.watchForUser(userId);

  Future<void> create({
    required String userId,
    required String? userName,
    required String title,
    String? description,
    String? path,
  }) async {
    final trimmedTitle = title.trim();
    final normalized = trimmedTitle.toLowerCase();
    if (await _dao.titleExists(userId, normalized)) {
      throw const DuplicatePersonalTitle();
    }
    final id = _createId();
    await _dao.upsert(
      PersonalEntriesCompanion.insert(
        id: id,
        couchId: Value(id),
        title: trimmedTitle,
        titleNormalized: normalized,
        description: Value(_nullable(description)),
        date: _now().millisecondsSinceEpoch,
        userId: userId,
        userName: Value(_nullable(userName)),
        path: Value(_nullable(path)),
      ),
    );
  }

  Future<void> update({
    required String id,
    required String title,
    String? description,
    String? path,
  }) async {
    final current = await _dao.getById(id);
    if (current == null) return;
    final trimmedTitle = title.trim();
    final normalized = trimmedTitle.toLowerCase();
    if (await _dao.titleExists(current.userId, normalized, excludingId: id)) {
      throw const DuplicatePersonalTitle();
    }
    // `toCompanion(false)` writes every column, matching Room's `@Update` in
    // `PersonalDao`. With `nullToAbsent: true` a cleared description would be
    // dropped from the statement instead of nulling the column, so editing a
    // note could never remove its description.
    await _dao.upsert(
      current
          .copyWith(
            title: trimmedTitle,
            titleNormalized: normalized,
            description: Value(_nullable(description)),
            path: Value(_nullable(path)),
            isUploaded: false,
          )
          .toCompanion(false),
    );
  }

  Future<int> delete(String id) => _dao.deleteById(id);
  Future<PersonalRow?> getById(String id) => _dao.getById(id);
  Future<List<PersonalRow>> pendingUploads(String userId) =>
      _dao.pendingUploads(userId);

  /// Port of `Personal.serialize`.
  ///
  /// Device telemetry is deliberately added by [PersonalsUploader], where the
  /// platform seam is available; this pure row serializer remains deterministic.
  static Map<String, dynamic> serialize(
    PersonalRow row, {
    DateTime? uploadedAt,
  }) {
    final filename = row.path != null && row.path!.isNotEmpty
        ? p.basename(row.path!)
        : null;
    return <String, dynamic>{
      'title': row.title,
      'uploadDate': (uploadedAt ?? DateTime.now()).millisecondsSinceEpoch,
      'createdDate': row.date,
      if (filename != null && filename.isNotEmpty) 'filename': filename,
      'author': row.userName,
      'addedBy': row.userName,
      'description': row.description,
      'resourceType': 'Activities',
      'private': true,
      'privateFor': {'users': row.userId},
    };
  }

  /// Port of `updatePersonalAfterSync` — adopts the ids CouchDB assigned.
  Future<void> markUploaded(String id, String couchId, String rev) async {
    final current = await _dao.getById(id);
    if (current == null) return;
    await _dao.upsert(
      current
          .copyWith(isUploaded: true, couchId: Value(couchId), rev: Value(rev))
          .toCompanion(false),
    );
  }
}

String? _nullable(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

String _randomId() {
  final timestamp = DateTime.now().microsecondsSinceEpoch;
  final random = Random.secure().nextInt(1 << 32);
  return '$timestamp-$random';
}
