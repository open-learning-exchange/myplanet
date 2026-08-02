import 'dart:math';

import 'package:drift/drift.dart';

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
  }) async {
    final current = await _dao.getById(id);
    if (current == null) return;
    final trimmedTitle = title.trim();
    final normalized = trimmedTitle.toLowerCase();
    if (await _dao.titleExists(
      current.userId,
      normalized,
      excludingId: id,
    )) {
      throw const DuplicatePersonalTitle();
    }
    await _dao.upsert(
      current
          .copyWith(
            title: trimmedTitle,
            titleNormalized: normalized,
            description: Value(_nullable(description)),
            isUploaded: false,
          )
          .toCompanion(true),
    );
  }

  Future<int> delete(String id) => _dao.deleteById(id);
  Future<List<PersonalRow>> pendingUploads(String userId) =>
      _dao.pendingUploads(userId);
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
