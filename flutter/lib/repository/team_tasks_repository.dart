import 'dart:convert';

import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../core/sync/table_walk.dart';
import '../core/utils/json_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';

/// Port of the task CRUD portion of `TeamsRepositoryImpl.kt` and
/// `TeamTask.serialize`.
class TeamTasksRepository {
  TeamTasksRepository(
    this._api,
    this._dao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId =
           createId ?? (() => 'task-${DateTime.now().microsecondsSinceEpoch}');

  /// `TransactionSyncManager.syncDb`'s default page size, which `tasks` takes.
  static const int initialBatchSize = 100;

  final PlanetApi _api;
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

  /// Port of the `"tasks"` arm of `TransactionSyncManager.syncDb` (`:254-256`),
  /// run in phase 1 of every Kotlin sync.
  ///
  /// Without it a task created on Planet web, or by a teammate on another
  /// handset, never arrives — the table only ever held tasks this device
  /// authored, so the team tasks screen was a private list.
  ///
  /// **Never prunes.** The Kotlin issues no delete here, and `team_tasks` is a
  /// preserved local-authority table: a task created offline lives only in this
  /// table until the outbox drains, and it carries a locally-minted id that no
  /// `_all_docs` keep set contains.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) => walkAllDocs(
    api: _api,
    config: config,
    table: 'tasks',
    initialBatchSize: initialBatchSize,
    onProgress: onProgress,
    insert: insertTasksFromSync,
  );

  /// Port of `TeamsRepositoryImpl.bulkInsertTasksFromSync` + `TeamTask.fromJson`
  /// (`model/TeamTask.kt:35-54`).
  ///
  /// `status` is stored exactly as the document carries it, **including the
  /// empty string** `fromJson` produces for a document that has none —
  /// `TeamTask.serialize` emits no `status` field, so that is what a task
  /// authored by either app actually looks like on the server. The reader is
  /// `TeamTaskDao.watchForTeam`, which after this phase excludes only
  /// `'archived'`, matching `TeamTaskDao.getByTeamId`.
  ///
  /// `link` and `sync` are dropped: the port has no column for either, and
  /// [serialize] rebuilds both from `teamId` and the session's planet code the
  /// way `upsertTask` does for a locally authored row.
  ///
  /// Two preservation rules, both about columns the server document cannot
  /// carry:
  ///
  /// * `isNotified` is never written, so a re-pull cannot make the deadline
  ///   notifier fire a second time for a task the user has already been told
  ///   about.
  /// * A row still flagged `isUpdated` takes only the identity columns. Its
  ///   title, deadline, assignee and completion state are an edit the user made
  ///   offline that has not reached the server yet; overwriting them with the
  ///   server's older copy — and clearing the flag, as the Kotlin does — would
  ///   discard the edit and stop it ever uploading.
  Future<int> insertTasksFromSync(List<Map<String, dynamic>> docs) async {
    if (docs.isEmpty) return 0;

    final ids = <String>[
      for (final doc in docs)
        if (JsonUtils.getString('_id', doc) case final id when id.isNotEmpty)
          id,
    ];
    final byId = <String, TeamTaskRow>{};
    for (final row in await _dao.getByAnyIds(ids)) {
      byId[row.id] = row;
      final docId = row.docId;
      if (docId != null && docId.isNotEmpty) byId[docId] = row;
    }

    final companions = <TeamTasksCompanion>[];
    final identityPatches = <(String, String, String?)>[];

    for (final doc in docs) {
      final docId = JsonUtils.getString('_id', doc);
      if (docId.isEmpty) continue;
      final existing = byId[docId];
      final rowId = existing?.id ?? docId;

      if (existing != null && existing.isUpdated) {
        identityPatches.add((
          rowId,
          docId,
          JsonUtils.getStringOrNull('_rev', doc),
        ));
        continue;
      }

      final link = JsonUtils.getObject('link', doc) ?? const {};
      final assignee = JsonUtils.getObject('assignee', doc) ?? const {};

      companions.add(
        TeamTasksCompanion(
          id: Value(rowId),
          docId: Value(docId),
          rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
          title: Value(JsonUtils.getStringOrNull('title', doc)),
          description: Value(JsonUtils.getStringOrNull('description', doc)),
          teamId: Value(JsonUtils.getString('teams', link)),
          // `if (user.has("_id"))` — an `assignee` serialized as the empty
          // string for an unassigned task leaves the column alone rather than
          // writing `""`, which the deadline query would match on.
          assignee: assignee.containsKey('_id')
              ? Value(JsonUtils.getStringOrNull('_id', assignee))
              : const Value.absent(),
          deadline: Value(JsonUtils.getLong('deadline', doc)),
          completedTime: Value(JsonUtils.getLong('completedTime', doc)),
          status: Value(JsonUtils.getString('status', doc)),
          completed: Value(JsonUtils.getBool('completed', doc)),
          isUpdated: const Value(false),
        ),
      );
    }

    await _dao.upsertAll(companions);
    for (final (id, docId, rev) in identityPatches) {
      await _dao.recordServerIdentity(id, docId, rev);
    }
    return companions.length + identityPatches.length;
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
