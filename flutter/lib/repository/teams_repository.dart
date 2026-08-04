import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/team_mapper.dart';
import 'dart:math';
import 'package:drift/drift.dart';

/// First vertical slice of `repository/TeamsRepositoryImpl.kt`: the offline
/// team/enterprise catalog and its CouchDB refresh.
class TeamsRepository {
  TeamsRepository(this._api, this._dao, {String Function()? createId})
    : _createId = createId ?? _randomId;

  final PlanetApi _api;
  final TeamDao _dao;
  final String Function() _createId;

  Stream<List<TeamRow>> watchCatalog({String type = 'team'}) =>
      _dao.watchCatalog(type: type);
  Future<TeamRow?> getById(String id) => _dao.getById(id);
  Stream<List<TeamRow>> watchMemberships(String userId) =>
      _dao.watchMemberships(userId);
  Stream<int> watchMemberCount(String teamId) => _dao.watchMemberCount(teamId);
  Stream<List<TeamRow>> watchMembers(String teamId) =>
      _dao.watchTeamDocuments(teamId, 'membership');
  Stream<List<TeamRow>> watchRequests(String teamId) =>
      _dao.watchTeamDocuments(teamId, 'request');
  Stream<List<TeamRow>> watchResourceLinks(String teamId) =>
      _dao.watchResourceLinks(teamId);
  Stream<List<TeamRow>> watchReports(String teamId) =>
      _dao.watchReports(teamId);

  Future<TeamRow?> saveReport({
    String? id,
    required String teamId,
    required String description,
    required int startDate,
    required int endDate,
    required int beginningBalance,
    required int sales,
    required int otherIncome,
    required int wages,
    required int otherExpenses,
  }) async {
    if (teamId.isEmpty || startDate > endDate) return null;
    final now = DateTime.now().millisecondsSinceEpoch;
    final existing = id == null ? null : await _dao.getById(id);
    final base =
        existing?.toCompanion(false) ?? TeamsCompanion.insert(id: _createId());
    await _dao.upsert(
      base.copyWith(
        teamId: Value(teamId),
        docType: const Value('report'),
        description: Value(description.trim()),
        startDate: Value(startDate),
        endDate: Value(endDate),
        beginningBalance: Value(beginningBalance),
        sales: Value(sales),
        otherIncome: Value(otherIncome),
        wages: Value(wages),
        otherExpenses: Value(otherExpenses),
        createdDate: Value(existing?.createdDate ?? now),
        updatedDate: Value(now),
        status: const Value('active'),
        isUpdated: const Value(true),
      ),
    );
    return _dao.getById(existing?.id ?? base.id.value);
  }

  Future<TeamRow?> archiveReport(String id) async {
    final report = await _dao.getById(id);
    if (report == null || report.docType != 'report') return null;
    final updated = report
        .toCompanion(false)
        .copyWith(
          status: const Value('archived'),
          updatedDate: Value(DateTime.now().millisecondsSinceEpoch),
          isUpdated: const Value(true),
        );
    await _dao.upsert(updated);
    return _dao.getById(id);
  }

  Future<TeamRow?> addResourceLink({
    required String teamId,
    required String resourceId,
    required String title,
    String? planetCode,
  }) async {
    if (teamId.isEmpty || resourceId.isEmpty) return null;
    final existing = await _dao.watchResourceLinks(teamId).first;
    final duplicate = existing
        .where((row) => row.resourceId == resourceId)
        .firstOrNull;
    if (duplicate != null) return duplicate;
    final id = _createId();
    await _dao.upsert(
      TeamsCompanion.insert(
        id: id,
        teamId: Value(teamId),
        resourceId: Value(resourceId),
        title: Value(title),
        docType: const Value('resourceLink'),
        teamType: const Value('local'),
        isUpdated: const Value(true),
      ),
    );
    return _dao.getById(id);
  }

  Future<TeamRow?> removeResourceLink(String teamId, String resourceId) async {
    final links = await _dao.watchResourceLinks(teamId).first;
    final row = links
        .where((item) => item.resourceId == resourceId)
        .firstOrNull;
    if (row != null) await _dao.deleteById(row.id);
    return row;
  }

  Future<TeamRow?> addCourses(String teamId, Iterable<String> courseIds) async {
    final team = await _dao.getById(teamId);
    if (team == null || team.docType != null) return null;
    final merged = {
      ...team.courses,
      ...courseIds.where((id) => id.isNotEmpty),
    }.toList();
    await _dao.upsert(
      team
          .toCompanion(false)
          .copyWith(courses: Value(merged), isUpdated: const Value(true)),
    );
    return _dao.getById(team.id);
  }

  Future<TeamRow?> removeCourse(String teamId, String courseId) async {
    final team = await _dao.getById(teamId);
    if (team == null || team.docType != null) return null;
    await _dao.upsert(
      team
          .toCompanion(false)
          .copyWith(
            courses: Value(team.courses.where((id) => id != courseId).toList()),
            isUpdated: const Value(true),
          ),
    );
    return _dao.getById(team.id);
  }

  Future<TeamRow?> membership(String teamId, String userId) =>
      _dao.getTeamDocument(teamId, userId, 'membership');

  Future<TeamRow?> request(String teamId, String userId) =>
      _dao.getTeamDocument(teamId, userId, 'request');

  /// Check if user is a member of the given team.
  Future<bool> isMember(String? userId, String teamId) async {
    if (userId == null || userId.isEmpty) return false;
    final mem = await membership(teamId, userId);
    return mem != null;
  }

  /// Get team links/services from the community.
  /// These are teams with docType='service' that have a route field.
  Stream<List<TeamRow>> watchTeamLinks() => _dao.watchTeamDocumentsByType('service');

  Future<TeamRow?> createJoinRequest({
    required String teamId,
    required String userId,
    String? teamType,
    String? planetCode,
  }) async {
    if (teamId.isEmpty || userId.isEmpty) return null;
    final existing = await request(teamId, userId);
    if (existing != null) return existing;
    final id = _createId();
    await _dao.upsert(
      TeamsCompanion.insert(
        id: id,
        teamId: Value(teamId),
        userId: Value(userId),
        docType: const Value('request'),
        teamType: Value(teamType),
        createdDate: Value(DateTime.now().millisecondsSinceEpoch),
        isUpdated: const Value(true),
      ),
    );
    return _dao.getById(id);
  }

  Future<TeamRow?> respondToRequest(
    String requestId, {
    required bool accept,
  }) async {
    final row = await _dao.getById(requestId);
    if (row == null || row.docType != 'request') return null;
    if (!accept) {
      await _dao.deleteById(row.id);
      return row;
    }
    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            docType: const Value('membership'),
            isUpdated: const Value(true),
          ),
    );
    return _dao.getById(row.id);
  }

  Future<TeamRow?> leave(String teamId, String userId) async {
    final row = await membership(teamId, userId);
    if (row != null) await _dao.deleteById(row.id);
    return row;
  }

  static Map<String, dynamic> serializeTeamDocument(TeamRow row) => {
    '_id': row.id,
    if (row.rev?.isNotEmpty == true) '_rev': row.rev,
    'teamId': row.teamId,
    'userId': row.userId,
    'docType': row.docType,
    'teamType': row.teamType,
    'createdDate': row.createdDate,
    'isLeader': row.isLeader,
    if (row.name != null) 'name': row.name,
    if (row.description != null) 'description': row.description,
    if (row.type != null) 'type': row.type,
    if (row.status != null) 'status': row.status,
    if (row.services != null) 'services': row.services,
    if (row.rules != null) 'rules': row.rules,
    if (row.createdBy != null) 'createdBy': row.createdBy,
    if (row.route != null) 'route': row.route,
    'public': row.isPublic,
    if (row.courses.isNotEmpty) 'courses': row.courses,
    if (row.resourceId != null) 'resourceId': row.resourceId,
    if (row.title != null) 'title': row.title,
    if (row.docType == 'report') ...{
      'beginningBalance': row.beginningBalance,
      'sales': row.sales,
      'otherIncome': row.otherIncome,
      'wages': row.wages,
      'otherExpenses': row.otherExpenses,
      'startDate': row.startDate,
      'endDate': row.endDate,
      'updatedDate': row.updatedDate,
    },
  };

  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final url = '${UrlUtils.dbUrl(config)}/teams/_all_docs';
    final auth = UrlUtils.basicAuthHeader('satellite', config.pin);
    final countResult = await _api.getJsonObject(
      '$url?limit=0',
      authHeader: auth,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }
    final total = JsonUtils.getInt('total_rows', countResult.data);
    if (total == 0) {
      await _dao.deleteNotIn(const []);
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final batchSizer = AdaptiveBatchProcessor(initialSize: 100);
    final syncedIds = <String>[];
    var skip = 0;
    while (skip < total) {
      final size = batchSizer.currentSize;
      final timer = Stopwatch()..start();
      final pageResult = await _api.getJsonObject(
        '$url?include_docs=true&limit=$size&skip=$skip',
        authHeader: auth,
      );
      timer.stop();
      if (pageResult is! NetworkSuccess<Map<String, dynamic>>) {
        batchSizer.recordFailure();
        // Pages already cached remain usable. Most importantly, stale cleanup
        // is not run against an incomplete id set.
        return SyncFailed(describeNetworkFailure(pageResult));
      }
      batchSizer.recordSuccess(timer.elapsedMilliseconds);
      final rawRows = pageResult.data['rows'];
      if (rawRows is! List || rawRows.isEmpty) {
        return const SyncFailed('Teams sync ended before all rows arrived');
      }
      final docs = rawRows
          .whereType<Map<String, dynamic>>()
          .map((row) => JsonUtils.getObject('doc', row))
          .whereType<Map<String, dynamic>>()
          .toList(growable: false);
      // Locally-edited rows survive the refresh, so the mapper needs to see
      // what is already stored rather than overwriting it from the document.
      final existing = await _dao.byIds(
        docs.map((doc) => JsonUtils.getString('_id', doc)).toList(),
      );
      final mapped = docs
          .map(
            (doc) => TeamMapper.fromDoc(
              doc,
              existing: existing[JsonUtils.getString('_id', doc)],
            ),
          )
          .whereType<TeamsCompanion>()
          .toList();
      await _dao.upsertAll(mapped);
      syncedIds.addAll(mapped.map((row) => row.id.value));
      skip += rawRows.length;
      onProgress?.call(
        SyncProgress(completed: skip.clamp(0, total), total: total),
      );
    }
    await _dao.deleteNotIn(syncedIds);
    return SyncComplete(syncedIds.length);
  }
}

extension TeamReportTotals on TeamRow {
  int get totalIncome => sales + otherIncome;
  int get totalExpenses => wages + otherExpenses;
  int get profitLoss => totalIncome - totalExpenses;
  int get endingBalance => beginningBalance + profitLoss;
}

String _randomId() =>
    '${DateTime.now().microsecondsSinceEpoch}-${Random.secure().nextInt(1 << 32)}';
