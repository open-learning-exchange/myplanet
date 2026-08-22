import 'dart:convert';

import 'package:drift/drift.dart';

import '../data/local/app_database.dart';

/// Port of the task CRUD portion of `TeamsRepositoryImpl.kt` and
/// `TeamTask.serialize`.
class TeamTasksRepository {
  TeamTasksRepository(
    this._dao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId =
           createId ?? (() => 'task-${DateTime.now().microsecondsSinceEpoch}');

  final TeamTaskDao _dao;
  final DateTime Function() _now;
  final String Function() _createId;

  Stream<List<TeamTaskRow>> watchForTeam(String teamId) =>
      _dao.watchForTeam(teamId);
  Future<TeamTaskRow?> getById(String id) => _dao.getById(id);
  Future<List<TeamTaskRow>> pending() => _dao.pending();

  /// Port of `TeamsRepositoryImpl.getPendingTasksForUser`, including its guards:
  /// a blank user id or an inverted window returns nothing rather than issuing a
  /// query that would match every task or none by accident.
  Future<List<TeamTaskRow>> pendingDeadlineTasks({
    required String userId,
    required int start,
    required int end,
  }) async {
    if (userId.trim().isEmpty || start > end) return const [];
    return _dao.pendingDeadlineTasks(userId, start, end);
  }

  /// Port of `TeamsRepositoryImpl.markTasksNotified` — blank ids dropped and the
  /// list de-duplicated before it reaches the `IN` clause.
  Future<void> markNotified(Iterable<String> taskIds) async {
    final valid = taskIds.where((id) => id.trim().isNotEmpty).toSet().toList();
    if (valid.isEmpty) return;
    await _dao.markNotified(valid);
  }

  Future<String?> create({
    required String teamId,
    required String title,
    required String description,
    required int deadline,
    String? assignee,
  }) async {
    if (teamId.trim().isEmpty || title.trim().isEmpty) return null;
    final id = _createId();
    await _dao.upsert(
      TeamTasksCompanion.insert(
        id: id,
        teamId: teamId,
        title: Value(title.trim()),
        description: Value(description.trim()),
        deadline: Value(deadline),
        assignee: Value(
          assignee?.trim().isEmpty == true ? null : assignee?.trim(),
        ),
        isUpdated: const Value(true),
      ),
    );
    return id;
  }

  Future<bool> update(
    String id, {
    required String title,
    required String description,
    required int deadline,
    String? assignee,
  }) async {
    final row = await _dao.getById(id);
    if (row == null || title.trim().isEmpty) return false;
    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            title: Value(title.trim()),
            description: Value(description.trim()),
            deadline: Value(deadline),
            assignee: Value(
              assignee?.trim().isEmpty == true ? null : assignee?.trim(),
            ),
            isUpdated: const Value(true),
          ),
    );
    return true;
  }

  Future<bool> setCompleted(String id, bool completed) async {
    final row = await _dao.getById(id);
    if (row == null) return false;
    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            completed: Value(completed),
            completedTime: Value(completed ? _now().millisecondsSinceEpoch : 0),
            isUpdated: const Value(true),
          ),
    );
    return true;
  }

  Future<bool> delete(String id) async {
    final row = await _dao.getById(id);
    if (row == null) return false;
    await _dao.deleteById(id);
    return true;
  }

  Future<void> markUploaded(String id, String docId, String rev) =>
      _dao.markUploaded(id, docId, rev);

  static Map<String, dynamic> serialize(
    TeamTaskRow row, {
    String? planetCode,
  }) => {
    if (row.docId?.isNotEmpty == true) '_id': row.docId,
    if (row.rev?.isNotEmpty == true) '_rev': row.rev,
    'title': row.title,
    'deadline': row.deadline,
    'description': row.description,
    'completed': row.completed,
    'completedTime': row.completedTime,
    'assignee': row.assignee?.isNotEmpty == true ? {'_id': row.assignee} : '',
    'sync': {'type': 'local', 'planetCode': ?planetCode},
    'link': {'teams': row.teamId},
  };

  static String encodeForDebug(TeamTaskRow row) => jsonEncode(serialize(row));
}
