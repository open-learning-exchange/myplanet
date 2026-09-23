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
    // `toCompanion(false)` writes every column. With `nullToAbsent: true` a
    // cleared description would be dropped from the statement instead of
    // nulling the column, so editing a note could never remove its
    // description.
    //
    // **This is deliberately wider than the Kotlin, which has no `@Update` at
    // all.** `PersonalDao` carries one `@Insert` and no `@Update`
    // (`PersonalDao.kt:20`); the edit path is `updateFields` (`:41`), a
    // targeted `SET title = COALESCE(:title, title), description =
    // COALESCE(:description, description)`. Two consequences, both checked:
    // clearing the description still works on Android, because
    // `PersonalsFragment.kt:120-122` passes the dialog's text rather than
    // null and `COALESCE('', …)` writes the empty string; but `updateFields`
    // never touches `isUploaded`, so editing an already-uploaded note leaves
    // it flagged as uploaded and the edit is never sent. `isUploaded: false`
    // below is what closes that, and it is a divergence in the port's favour
    // rather than a port of the line.
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
  /// platform seam is available.
  ///
  /// **This is only deterministic if [uploadedAt] is passed.** The
  /// `DateTime.now()` default made the same row serialize differently on every
  /// call, which defeats `OutboxRepository.enqueue`'s memo — see the call site
  /// in [PersonalsUploader.queuePending] for why that mattered enough to fix.
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
