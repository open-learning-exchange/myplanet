import '../core/config/server_config.dart';
import '../core/files/team_attachments.dart';
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

/// Per-team membership rank for the catalog sort, porting
/// `TeamsRepositoryImpl.TeamMemberStatus` (without `hasPendingRequest`, which
/// the catalog sort does not read). Used by [TeamsRepository.memberStatuses].
class TeamMemberStatus {
  const TeamMemberStatus({required this.isMember, required this.isLeader});

  final bool isMember;
  final bool isLeader;
}

/// Port of `TeamsRepositoryImpl.mapToTeamDetails`'s sort: membership rank
/// (leader > member > non-member) DESC, then visit count DESC. Pure so the
/// provider can call it without transitively watching another provider, and
/// so a test can pin the order without a database.
List<TeamRow> sortTeamsCatalog(
  List<TeamRow> teams,
  Map<String, TeamMemberStatus> statuses,
  Map<String, int> visitCounts,
) {
  final sorted = [...teams];
  sorted.sort((a, b) {
    final aId = a.id;
    final bId = b.id;
    final aRank = _rank(statuses[aId]);
    final bRank = _rank(statuses[bId]);
    if (aRank != bRank) return bRank.compareTo(aRank);
    final aVisits = visitCounts[aId] ?? 0;
    final bVisits = visitCounts[bId] ?? 0;
    return bVisits.compareTo(aVisits);
  });
  return sorted;
}

int _rank(TeamMemberStatus? status) {
  if (status == null) return 1;
  if (status.isLeader) return 3;
  if (status.isMember) return 2;
  return 1;
}

/// First vertical slice of `repository/TeamsRepositoryImpl.kt`: the offline
/// team/enterprise catalog and its CouchDB refresh.
class TeamsRepository {
  TeamsRepository(
    this._api,
    this._dao,
    this._teamLogDao, {
    String Function()? createId,
  }) : _createId = createId ?? _randomId;

  final PlanetApi _api;
  final TeamDao _dao;
  final TeamLogDao _teamLogDao;
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

  /// Port of `TeamLogDao.getTeamVisitsForUsers` — the per-team visit rows for
  /// the members of `teamId`, used to compute the visit count
  /// `MembersDetailFragment` shows.
  Future<List<TeamLogRow>> teamVisitsForUsers(
    String teamId,
    List<String> userNames,
  ) => _teamLogDao.teamVisitsForUsers(teamId, userNames);

  /// Port of `TeamLogDao.getLastVisit` — the most recent `teamVisit` time for
  /// a user in a team, or null if they have never visited.
  Future<int?> lastTeamVisit(String? userName, String? teamId) =>
      _teamLogDao.lastTeamVisit(userName, teamId);

  /// Port of `TeamsRepositoryImpl.getTeamMemberStatuses` — the catalog's
  /// per-team membership rank for [userId]. `isMember` and `isLeader` come
  /// from the `membership` rows; `hasPendingRequest` from `request` rows.
  Future<Map<String, TeamMemberStatus>> memberStatuses(
    String? userId,
    Iterable<String> teamIds,
  ) async {
    if (userId == null || userId.isEmpty) return const {};
    final valid = teamIds.where((id) => id.isNotEmpty).toSet();
    if (valid.isEmpty) return const {};
    final rows = await _dao.membershipsForUser(userId);
    final memberships = <String>{};
    final leaders = <String>{};
    for (final row in rows) {
      final teamId = row.teamId;
      if (teamId == null || !valid.contains(teamId)) continue;
      memberships.add(teamId);
      if (row.isLeader) leaders.add(teamId);
    }
    return {
      for (final id in valid)
        id: TeamMemberStatus(
          isMember: memberships.contains(id),
          isLeader: leaders.contains(id),
        ),
    };
  }

  /// Port of `TeamsRepositoryImpl.getRecentVisitCounts` — the per-team count
  /// of `teamVisit` logs within the last [window] (defaults to 30 days, the
  /// Kotlin window). Drives the catalog's visit-count tiebreak sort.
  Future<Map<String, int>> recentVisitCounts(
    Iterable<String> teamIds, {
    Duration window = const Duration(days: 30),
  }) {
    final cutoff = DateTime.now().subtract(window).millisecondsSinceEpoch;
    return _teamLogDao.recentVisitCounts(
      teamIds.where((id) => id.isNotEmpty).toList(),
      cutoff,
    );
  }

  Stream<List<TeamRow>> watchResourceLinks(String teamId) =>
      _dao.watchResourceLinks(teamId);
  Stream<List<TeamRow>> watchReports(String teamId) =>
      _dao.watchReports(teamId);

  /// Port of `TeamsRepositoryImpl.logTeamVisit` — record a `teamVisit` action
  /// when a user opens a team's detail screen. The row is queued for upload
  /// to `team_activities` on the next sync; the `uploaded` flag is the only
  /// durable record it has not yet left the device.
  ///
  /// Returns the new row's id, or `null` when the arguments are blank (the
  /// Kotlin's `if (teamId.isBlank() || userName.isNullOrBlank()) return`).
  Future<String?> logTeamVisit({
    required String teamId,
    String? userName,
    String? userPlanetCode,
    String? userParentCode,
    String? teamType,
  }) async {
    if (teamId.isEmpty || userName == null || userName.trim().isEmpty) {
      return null;
    }
    final id = _createId();
    await _teamLogDao.insert(
      TeamLogTableCompanion.insert(
        id: id,
        teamId: Value(teamId),
        user: Value(userName),
        type: const Value('teamVisit'),
        teamType: Value(teamType),
        createdOn: Value(userPlanetCode),
        parentCode: Value(userParentCode),
        time: Value(DateTime.now().millisecondsSinceEpoch),
      ),
    );
    return id;
  }

  /// Rows whose `teamVisit` has not yet reached `team_activities`.
  ///
  /// Port of `TeamsRepositoryImpl.getPendingTeamLogUploads` — the uploader
  /// selects these, serializes each, and POSTs it to `team_activities`.
  Future<List<TeamLogRow>> pendingTeamLogUploads() =>
      _teamLogDao.pendingUploads();

  /// Watch all transactions for a team.
  Stream<List<TeamRow>> watchTransactions(
    String teamId, {
    int? startDate,
    int? endDate,
    bool ascending = false,
  }) => _dao.watchTransactions(
    teamId,
    startDate: startDate,
    endDate: endDate,
    ascending: ascending,
  );

  /// Create a new transaction (debit or credit entry).
  ///
  /// When [imageName] and [imageBytes] are both supplied the receipt image is
  /// written to `team_attachments/<id>/<imageName>` and its name is stored on
  /// the row, porting `TeamsRepositoryImpl.createTransaction` +
  /// `attachTeamImage`. The bytes are persisted before the row is flagged for
  /// upload so the write-back can PUT them once the document is acknowledged;
  /// the attachment is best-effort, the way the Kotlin source does not roll the
  /// document back if the file write fails.
  Future<TeamRow?> createTransaction({
    required String teamId,
    required String type, // 'debit' or 'credit'
    required String note,
    required int amount,
    required int date,
    String? imageName,
    List<int>? imageBytes,
  }) async {
    if (teamId.isEmpty) return null;
    final id = _createId();
    await _dao.upsert(
      TeamsCompanion.insert(
        id: id,
        teamId: Value(teamId),
        docType: const Value('transaction'),
        type: Value(type),
        description: Value(note),
        amount: Value(amount),
        date: Value(date),
        status: const Value('active'),
        isUpdated: const Value(true),
      ),
    );
    if (imageName != null && imageName.isNotEmpty && imageBytes != null) {
      await TeamAttachments.write(
        docId: id,
        filename: imageName,
        bytes: imageBytes,
      );
      final row = await _dao.getById(id);
      if (row != null) {
        await _dao.upsert(
          row
              .toCompanion(false)
              .copyWith(
                imageName: Value(imageName),
                isUpdated: const Value(true),
              ),
        );
      }
    }
    return _dao.getById(id);
  }

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
    String? imageName,
    List<int>? imageBytes,
  }) async {
    if (teamId.isEmpty || startDate > endDate) return null;
    final now = DateTime.now().millisecondsSinceEpoch;
    final existing = id == null ? null : await _dao.getById(id);
    final base =
        existing?.toCompanion(false) ?? TeamsCompanion.insert(id: _createId());
    final docId = existing?.id ?? base.id.value;
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
    // Port of `attachTeamImage` in `addReport`/`updateReport`: the image is
    // attached only when both name and bytes are present, and an absent image
    // leaves an existing report's attachment untouched (a no-image edit does
    // not clear the prior receipt).
    if (imageName != null && imageName.isNotEmpty && imageBytes != null) {
      await TeamAttachments.write(
        docId: docId,
        filename: imageName,
        bytes: imageBytes,
      );
      final row = await _dao.getById(docId);
      if (row != null) {
        await _dao.upsert(
          row
              .toCompanion(false)
              .copyWith(
                imageName: Value(imageName),
                isUpdated: const Value(true),
              ),
        );
      }
    }
    return _dao.getById(docId);
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

  /// Port of `TeamsRepositoryImpl.exportReportsAsCsv`: builds a CSV string of
  /// the financial report summary. [teamName] heads the report. Date columns
  /// use [formatDateForCsv]; the derived totals are computed inline to match
  /// the Kotlin column order exactly.
  String exportReportsAsCsv(List<TeamRow> reports, String teamName) {
    final b = StringBuffer()
      ..write(teamName)
      ..write(' Financial Report Summary\n\n')
      ..writeln(
        'Start Date, End Date, Created Date, Updated Date, Beginning Balance,'
        ' Sales, Other Income, Wages, Other Expenses, Profit/Loss,'
        ' Ending Balance',
      );
    for (final r in reports) {
      final totalIncome = r.sales + r.otherIncome;
      final totalExpenses = r.wages + r.otherExpenses;
      final profitLoss = totalIncome - totalExpenses;
      final endingBalance = profitLoss + r.beginningBalance;
      b
        ..write(formatDateForCsv(r.startDate))
        ..write(', ')
        ..write(formatDateForCsv(r.endDate))
        ..write(', ')
        ..write(formatDateForCsv(r.createdDate))
        ..write(', ')
        ..write(formatDateForCsv(r.updatedDate))
        ..write(', ')
        ..write(r.beginningBalance)
        ..write(', ')
        ..write(r.sales)
        ..write(', ')
        ..write(r.otherIncome)
        ..write(', ')
        ..write(r.wages)
        ..write(', ')
        ..write(r.otherExpenses)
        ..write(', ')
        ..write(profitLoss)
        ..write(', ')
        ..writeln(endingBalance);
    }
    return b.toString();
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

  /// Update team/enterprise details (name, description, services, rules, etc.)
  Future<TeamRow?> updateTeam({
    required String teamId,
    String? name,
    String? description,
    String? services,
    String? rules,
    String? teamType,
    bool? isPublic,
    String? createdBy,
  }) async {
    final team = await _dao.getById(teamId);
    if (team == null) return null;
    await _dao.upsert(
      team
          .toCompanion(false)
          .copyWith(
            name: Value(name ?? team.name),
            description: Value(description ?? team.description),
            services: Value(services ?? team.services),
            rules: Value(rules ?? team.rules),
            teamType: Value(teamType ?? team.teamType),
            isPublic: Value(isPublic ?? team.isPublic),
            createdBy: Value(createdBy ?? team.createdBy),
            isUpdated: const Value(true),
            updatedDate: Value(DateTime.now().millisecondsSinceEpoch),
          ),
    );
    return _dao.getById(teamId);
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
  Stream<List<TeamRow>> watchTeamLinks() =>
      _dao.watchTeamDocumentsByType('service');

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

  /// Port of `TeamsRepositoryImpl.removeMember` — same as [leave] but for a
  /// leader removing another member. The row is hard-deleted locally and the
  /// tombstone is enqueued by the caller.
  Future<TeamRow?> removeMember(String teamId, String userId) async {
    final row = await membership(teamId, userId);
    if (row != null) await _dao.deleteById(row.id);
    return row;
  }

  /// Port of `TeamsRepositoryImpl.updateTeamLeader` — sets `isLeader` to true
  /// only for the new leader and false for every other member, marking each
  /// changed row dirty. Returns the changed rows so the caller can enqueue
  /// them for upload.
  Future<List<TeamRow>> updateTeamLeader(
    String teamId,
    String newLeaderId,
  ) async {
    final memberships = await _dao
        .watchTeamDocuments(teamId, 'membership')
        .first;
    final changed = <TeamRow>[];
    for (final row in memberships) {
      final shouldBeLeader = row.userId == newLeaderId;
      if (row.isLeader != shouldBeLeader) {
        final updated = row.copyWith(isLeader: shouldBeLeader, isUpdated: true);
        await _dao.upsert(updated.toCompanion(false));
        changed.add(updated);
      }
    }
    return changed;
  }

  static Map<String, dynamic> serializeTeamDocument(TeamRow row) => {
    '_id': row.id,
    if (row.rev?.isNotEmpty == true) '_rev': row.rev,
    if (row.teamId != null) 'teamId': row.teamId,
    if (row.userId != null) 'userId': row.userId,
    if (row.docType != null) 'docType': row.docType,
    if (row.teamType != null) 'teamType': row.teamType,
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
    // The attachment's bytes are PUT separately to `teams/<id>/<imageName>`
    // by the uploader, so the document body carries only the name — never the
    // base64 blob. Omitting it for a row without an attachment keeps the
    // document null-free, the way the Kotlin `serialize` guards each field.
    if (row.imageName?.isNotEmpty == true) 'imageName': row.imageName,
  };

  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final url = '${UrlUtils.dbUrl(config)}/teams/_all_docs';
    final auth = UrlUtils.authHeader(config);
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
      // Port of `TransactionSyncManager.downloadTeamAttachmentsFromBatch`:
      // a finance document's receipt image is stored as a CouchDB attachment,
      // not in the document body, so the `_all_docs` pull above only records
      // its name. The bytes are fetched per-document to the
      // `team_attachments/<docId>/<name>` slot the preview and upload read-back
      // share. Best-effort, like the Kotlin source — a single failed download
      // does not fail the sync, the row still shows with its name and a missing
      // thumbnail.
      await _downloadAttachments(config, docs, auth);
      syncedIds.addAll(mapped.map((row) => row.id.value));
      skip += rawRows.length;
      onProgress?.call(
        SyncProgress(completed: skip.clamp(0, total), total: total),
      );
    }
    await _dao.deleteNotIn(syncedIds);
    return SyncComplete(syncedIds.length);
  }

  /// Downloads each finance document's named attachment, porting
  /// `TransactionSyncManager.downloadTeamAttachmentsFromBatch` +
  /// `downloadTeamAttachment`. A document whose `_attachments` is missing or
  /// whose attachment already exists locally is skipped, matching the Kotlin
  /// `!destFile.exists()` guard — re-downloading on every sync would burn the
  /// bandwidth myPlanet is built to conserve.
  Future<void> _downloadAttachments(
    ServerConfig config,
    List<Map<String, dynamic>> docs,
    String auth,
  ) async {
    final base = '${UrlUtils.dbUrl(config)}/teams';
    for (final doc in docs) {
      final docId = JsonUtils.getString('_id', doc);
      if (docId.isEmpty || docId.startsWith('_design/')) continue;
      final name = TeamMapper.firstAttachmentName(doc['_attachments']);
      if (name == null || name.isEmpty) continue;
      final existing = await TeamAttachments.existingFileFor(
        docId: docId,
        filename: name,
      );
      if (existing != null) continue;
      final result = await _api.getBytes(
        '$base/${Uri.encodeComponent(docId)}/${Uri.encodeComponent(name)}',
        authHeader: auth,
      );
      final bytes = result is NetworkSuccess<List<int>> ? result.data : null;
      if (bytes != null && bytes.isNotEmpty) {
        await TeamAttachments.write(docId: docId, filename: name, bytes: bytes);
      }
    }
  }
}

extension TeamReportTotals on TeamRow {
  int get totalIncome => sales + otherIncome;
  int get totalExpenses => wages + otherExpenses;
  int get profitLoss => totalIncome - totalExpenses;
  int get endingBalance => beginningBalance + profitLoss;
}

/// Port of `TimeUtils.formatDateForCsv` — a US-locale, timezone-aware
/// timestamp matching the Kotlin CSV export's date column exactly.
String formatDateForCsv(int millis) {
  final dt = DateTime.fromMillisecondsSinceEpoch(millis, isUtc: false);
  final weekday = const [
    'Mon',
    'Tue',
    'Wed',
    'Thu',
    'Fri',
    'Sat',
    'Sun',
  ][dt.weekday - 1];
  final month = const [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ][dt.month - 1];
  final off = dt.timeZoneOffset;
  final sign = off.isNegative ? '-' : '+';
  final abs = off.abs();
  final tzHours = abs.inHours.toString().padLeft(2, '0');
  final tzMins = (abs.inMinutes % 60).toString().padLeft(2, '0');
  final day = dt.day.toString().padLeft(2, '0');
  final hour = dt.hour.toString().padLeft(2, '0');
  final minute = dt.minute.toString().padLeft(2, '0');
  final second = dt.second.toString().padLeft(2, '0');
  return '$weekday $month $day ${dt.year} '
      '$hour:$minute:$second GMT$sign$tzHours$tzMins (${dt.timeZoneName})';
}

String _randomId() =>
    '${DateTime.now().microsecondsSinceEpoch}-${Random.secure().nextInt(1 << 32)}';
