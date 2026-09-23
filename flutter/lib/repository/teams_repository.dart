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

  /// The pool of members who could take over leadership of [teamId] when
  /// [excludeUserId] leaves — `TeamDao.getEligibleNextLeaderCandidates`
  /// (`TeamDao.kt:22`), ranked by [selectNextLeaderCandidate].
  Future<List<TeamRow>> eligibleNextLeaderCandidates(
    String teamId,
    String? excludeUserId,
  ) => _dao.eligibleNextLeaderCandidates(teamId, excludeUserId);
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
    // `trim()` on both: Kotlin's guard is `teamId.isBlank() ||
    // userName.isNullOrBlank()` (`TeamsRepositoryImpl.kt:842`), and the port
    // tested `teamId.isEmpty`, so a whitespace-only team id was rejected there
    // and accepted here — filing a visit against a team no screen can reach.
    if (teamId.trim().isEmpty || userName == null || userName.trim().isEmpty) {
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

  /// Port of `TeamsRepositoryImpl.bulkInsertTeamActivitiesFromSync`
  /// (`:1277`) → `insertTeamLogs` (`:1142`) — the `team_activities` pull the
  /// port has never had.
  ///
  /// **What was missing and what it cost.** Kotlin walks `team_activities` on
  /// the way in (`TransactionSyncManager.kt:251-253`); the port only ever
  /// *uploaded* visit rows, so `TeamLogDao.teamVisitsForUsers` and
  /// `lastTeamVisit` returned whatever this one handset happened to observe.
  /// Those two feed the member-detail screen's visit count and last-visit row
  /// **and the team leaderboard's ranking** — and a leaderboard is a
  /// comparison between members by construction, so a member who does all
  /// their work on another device ranked last. Same shape as the `ratings`
  /// average this port already fixed.
  ///
  /// The rows land in the existing `team_log` table rather than a new one:
  /// it is a field-for-field port of `model/TeamLog.kt`, which is the single
  /// table Kotlin uses for both directions, and the readers are already
  /// `TeamLogDao` methods over it. No schema change, therefore no bump —
  /// which matters, because a bump discards unsynced local writes and
  /// `team_log` is a preserved table precisely because its `uploaded` flag is
  /// the only record that a visit has not left the device.
  ///
  /// The merge — resolve the local row, keep its primary key, and treat a
  /// pulled document as already uploaded — is explained at [TeamLogMapper].
  /// Two page-wide lookups instead of two per document, matching how
  /// `ActivitiesRepositoryImpl.bulkInsertOfflineActivitiesFromSync` batches
  /// the identical merge.
  ///
  /// Deliberately **no prune**. Kotlin issues no `deleteNotIn` for
  /// `team_log`, and one here would delete exactly the visits the server has
  /// not seen yet — the rows this table is preserved for. Returns the number
  /// of documents written, so the caller can report a page count.
  Future<int> insertTeamActivitiesFromSync(
    List<Map<String, dynamic>> docs,
  ) async {
    // **Both halves of this predicate are load-bearing, and the empty-id half
    // was missing.** `TeamLogMapper.fromDoc` also refuses these, so dropping
    // `_design` rows here looks redundant — but a document that reaches the
    // loop and yields no row still contributes its `time`/`user` to the two
    // lookup lists below *and still consumes a `claimedRowIds` claim*. An
    // id-less document ahead of a real one in the same page would therefore
    // starve the real one of its local row: two rows for one visit, and the
    // starved local row left `uploaded = false` so it uploads a second server
    // document. Both of the failures this merge exists to prevent, from one
    // malformed row.
    //
    // `extractDocs` already drops empty ids before the heavy writer calls
    // this, so that page cannot arrive today — but this method is public and
    // its guard should defend itself rather than rely on its only caller.
    final documents = docs.where((doc) {
      final id = JsonUtils.getString('_id', doc);
      return id.isNotEmpty && !id.startsWith('_design');
    }).toList();
    if (documents.isEmpty) return 0;

    final ids = documents
        .map((doc) => JsonUtils.getString('_id', doc))
        .where((id) => id.isNotEmpty)
        .toSet()
        .toList(growable: false);
    final existingById = {
      for (final row in await _teamLogDao.getByCouchIds(ids))
        row.couchId ?? '': row,
    };

    final times = documents
        .map((doc) => JsonUtils.getLong('time', doc))
        .where((time) => time > 0)
        .toSet()
        .toList(growable: false);
    final userNames = documents
        .map((doc) => JsonUtils.getString('user', doc))
        .where((name) => name.isNotEmpty)
        .toSet()
        .toList(growable: false);
    final fallbackByKey = <String, TeamLogRow>{};
    for (final row in await _teamLogDao.getByTimesAndUsers(times, userNames)) {
      // A row that already carries a `_id` is reachable through
      // [existingById]; letting it also occupy a natural-key slot would let it
      // shadow the *unstamped* local row that this fallback exists to find.
      if (row.couchId?.isNotEmpty == true) continue;
      // `putIfAbsent` — and **not** "as the Kotlin does", which an earlier
      // revision of this comment claimed. Kotlin builds its initial fallback
      // map with `.associateBy` (`ActivitiesRepositoryImpl.kt:363-365`), where
      // a duplicate key keeps the **last** row; its `putIfAbsent` (`:267`)
      // applies only to entries added during the document loop. First-wins is
      // this port's choice, reachable only with two local rows sharing an
      // exact `(time, user, teamId)`, and it is stated as a choice because
      // this is the file the next lane will cite.
      fallbackByKey.putIfAbsent(TeamLogMapper.naturalKeyForRow(row), () => row);
    }

    final companions = <TeamLogTableCompanion>[];
    // Tracks local rows consumed within this page, so two documents that
    // resolve to the same one do not both adopt its primary key and collapse
    // into a single row — the second must be keyed by its own `_id`.
    //
    // **This is a deliberate improvement on the Kotlin it is modelled on, not
    // a copy of it.** `activityFromJson` mutates both lookup maps inside the
    // loop (`ActivitiesRepositoryImpl.kt:265-267`), so two documents sharing a
    // natural key resolve to the same entity object and Kotlin *does* collapse
    // them, losing one row. Here two server documents are two visits.
    final claimedRowIds = <String>{};
    for (final doc in documents) {
      final docId = JsonUtils.getString('_id', doc);
      final existing = existingById[docId];
      var fallback = fallbackByKey[TeamLogMapper.naturalKeyForDoc(doc)];
      if (existing == null &&
          fallback != null &&
          !claimedRowIds.add(fallback.id)) {
        fallback = null;
      }
      final companion = TeamLogMapper.fromDoc(
        doc,
        existing: existing,
        fallback: fallback,
      );
      if (companion != null) companions.add(companion);
    }
    await _teamLogDao.upsertAllFromSync(companions);
    return companions.length;
  }

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

  /// Port of `TeamsRepositoryImpl.addResourceLinks` (`:664-690`) — the team's
  /// *Add resource* action, reached from `TeamResourcesFragment.kt:129`.
  ///
  /// **Kotlin has two resource-link producers that stamp different fields, and
  /// one port method could not honestly serve both.** This is the split; the
  /// other half is [createLocalResourceLink]. Measured rather than assumed:
  ///
  /// | field | `addResourceLinks` | `createLocalResourceLink` |
  /// |---|---|---|
  /// | `sourcePlanet` | *(unset)* | resolved planet code (`:716`) |
  /// | `teamPlanetCode` | `user.planetCode` (`:682`) | resolved (`:718`) |
  /// | `userPlanetCode` | `user.planetCode` (`:683`) | *(unset)* |
  /// | `status` | `user.parentCode` (`:677`) | *(unset)* |
  ///
  /// Two resource links on the same team, created through the two paths,
  /// produce **differently shaped server documents** in Kotlin. That is the
  /// ground truth, not a Kotlin bug to improve on here, and stamping all of it
  /// from one method would have put keys on the wire that neither path sends.
  ///
  /// [planetCode] keeps its name and position, so the one caller
  /// (`TeamResourceActions.add`) is unchanged — it passes the signed-in
  /// user's `planetCode`, which is exactly what Kotlin reads at `:682-683`.
  ///
  /// **That caller's half of this is closed, and the hand-off this paragraph
  /// used to carry is retired.** It read
  /// `ref.read(sessionProvider).value?.planetCode` — the port's standing *a
  /// provider a screen reads but never watches is null* trap — so on any pass
  /// where the session had not resolved, this method was handed null and
  /// stamped nothing. Phase 158 resolved it, and added the guard the reading
  /// above implies: Kotlin's `addResourceLinks` opens
  /// `val user = userRepository.getUserById(userId) ?: return` (`:671`), so
  /// with no user it creates **no row at all** rather than one with null
  /// planet codes, and `TeamResourceActions.add` now returns `false` the same
  /// way. Note this is why the prefs fallback is *not* the answer here: that
  /// belongs to [createLocalResourceLink] (`:709-710`), a different producer.
  ///
  /// **`status` is deliberately not stamped**, although Kotlin writes
  /// `user.parentCode` there. `MyTeam.serialize`'s `resourceLink` branch emits
  /// no `status` key (`MyTeam.kt:167-179`), so in Kotlin the value never
  /// leaves the device — but [serializeTeamDocument] has no `resourceLink`
  /// branch and *would* send it. Copying the assignment would therefore add a
  /// key to the wire in the name of parity. Nothing reads `teams.status` for a
  /// resource link in either app.
  ///
  /// **No fallback**, matching `:682-683`: `addResourceLinks` uses
  /// `user.planetCode` raw and a null one yields null columns. Only
  /// [createLocalResourceLink] has one, and see there for why the port's is
  /// still missing.
  ///
  /// The duplicate check is the port's own; Kotlin has none.
  ///
  /// Related and still out of scope: [serializeTeamDocument] has no
  /// `resourceLink` branch where `MyTeam.serialize` returns early for that
  /// docType with nine keys, so the port sends `createdDate`, `isLeader` and
  /// `public` where Kotlin sends none of the three. (An earlier revision of
  /// this sentence said fourteen keys. That is the number of key names in
  /// [serializeTeamDocument] absent from Kotlin's branch, but every one is
  /// `if (x != null)`-guarded and a row this method creates leaves them null,
  /// so they are never sent — a count of *potential* keys stated as what the
  /// port sends, in a comment whose whole job was to size a future phase.)
  /// **That gap is closed too, and it was this method's `userPlanetCode` that
  /// forced the issue.** An earlier revision of this paragraph said the four
  /// columns "do not widen the gap" — and then argued only the
  /// [createLocalResourceLink] case, which leaves `userPlanetCode` null. This
  /// method sets it, Kotlin's `resourceLink` serialize branch omits it, and
  /// the port had no such branch, so the gap widened by exactly one key on the
  /// path this comment is attached to — by the same argument used four
  /// paragraphs up to decline `status`. [serializeTeamDocument] now has the
  /// branch, so the row matches Kotlin's row and the document matches Kotlin's
  /// document; `createdDate`, `isLeader` and `public` stop being sent as
  /// well.
  Future<TeamRow?> addResourceLink({
    required String teamId,
    required String resourceId,
    required String title,
    String? planetCode,
  }) => _insertResourceLink(
    teamId: teamId,
    resourceId: resourceId,
    title: title,
    teamPlanetCode: planetCode,
    userPlanetCode: planetCode,
  );

  /// Port of `TeamsRepositoryImpl.createLocalResourceLink` (`:702-723`) — the
  /// link a **private team resource** gains when its document reaches CouchDB,
  /// called from `ResourcesRepositoryImpl.markResourceUploaded:829-836` and,
  /// in the port, from `ResourcesUploader._linkPrivateResourceToTeam`.
  ///
  /// Stamps `sourcePlanet` **and** `teamPlanetCode` from [planetCode]
  /// (`:716`, `:718`) and neither `userPlanetCode` nor `parentCode`. See
  /// [addResourceLink] for the table comparing the two producers, and for why
  /// they are two methods.
  ///
  /// **Kotlin's fallback has no port counterpart, and this is the honest
  /// version of that gap rather than a guess at it.** Kotlin resolves
  /// `planetCode?.takeIf { it.isNotBlank() } ?: sharedPrefManager
  /// .getPlanetCode()` (`:709-710`), reading the `planetCode` preference that
  /// `UserRepositoryImpl.applyJsonToUser:283-285` writes from the user
  /// document — so the fallback returns `""` on a device that has never
  /// completed a user sync, and the user's own row otherwise. The port has no
  /// such preference. Adding `PlanetPrefs.planetCode` would be an accessor
  /// with no writer, since the writer belongs to the user-sync path, which is
  /// not this lane's file — and a second source of truth for a value the
  /// `users` row already holds. The port's caller passes
  /// `payload['sourcePlanet']`, which is the signed-in user's `planetCode`
  /// captured at enqueue time: the same origin as Kotlin's preference, read
  /// one hop earlier. What is genuinely lost is the null-user case, reported
  /// rather than papered over.
  ///
  /// The blank guard *is* ported: a blank [planetCode] is normalised to null
  /// so the column is absent rather than empty, which is what
  /// `takeIf { it.isNotBlank() }` means before the `?:` arrives.
  Future<TeamRow?> createLocalResourceLink({
    required String teamId,
    required String resourceId,
    required String title,
    String? planetCode,
  }) {
    final resolved = (planetCode?.trim().isNotEmpty ?? false)
        ? planetCode
        : null;
    return _insertResourceLink(
      teamId: teamId,
      resourceId: resourceId,
      title: title,
      sourcePlanet: resolved,
      teamPlanetCode: resolved,
    );
  }

  /// The row both producers write, differing only in which planet-code columns
  /// the caller supplies.
  ///
  /// Everything else is identical in the Kotlin: the blank guard on the two
  /// ids (`:669`/`:708`), the generated `_id`, `docType` `'resourceLink'`,
  /// `teamType` `'local'` and `updated = true`. The duplicate check is the
  /// port's addition and applies to both, as it did before the split.
  Future<TeamRow?> _insertResourceLink({
    required String teamId,
    required String resourceId,
    required String title,
    String? sourcePlanet,
    String? teamPlanetCode,
    String? userPlanetCode,
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
        sourcePlanet: Value(sourcePlanet),
        teamPlanetCode: Value(teamPlanetCode),
        userPlanetCode: Value(userPlanetCode),
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

  /// Port of `TeamsRepositoryImpl.requestToJoin` (`:603-627`) — Kotlin has no
  /// `createJoinRequest`; the name here is the port's.
  ///
  /// **[planetCode] used to be accepted and dropped**, the same defect
  /// [addResourceLink] carried, and it is closed by the same schema bump.
  /// Kotlin writes the argument to `teamPlanetCode` *and* `userPlanetCode`
  /// (`:623-624`) — the two fields `MyTeam.serialize`'s general branch then
  /// uploads (`MyTeam.kt:204`, `:207`), so a join request reaching Planet
  /// without them is a request the server cannot attribute to a planet.
  ///
  /// **No fallback here, deliberately, and that is Kotlin's shape rather than
  /// an omission.** Only `createLocalResourceLink` carries
  /// `?: sharedPrefManager.getPlanetCode()`; both of Kotlin's callers of
  /// `requestToJoin` pass `user?.planetCode` raw (`TeamFragment.kt:289`,
  /// `TeamDetailFragment.kt:274-276`), so a user whose `planetCode` is null
  /// produces a request row with both columns null in Kotlin too.
  ///
  /// `sourcePlanet` and `parentCode` are **not** written: Kotlin sets neither
  /// on this path, and the general serialize branch strips a null rather than
  /// sending it, so stamping either would put a key on the wire Kotlin does
  /// not send.
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
        teamPlanetCode: Value(planetCode),
        userPlanetCode: Value(planetCode),
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

  /// Port of `MyTeam.serialize` (`MyTeam.kt:156-225`).
  ///
  /// **The `resourceLink` early return, added when the v50 audit found this
  /// method putting a key on the wire that Kotlin's does not.** `MyTeam
  /// .serialize` returns at `:167-179` for that docType with seven fields plus
  /// `_id`/`_rev`, and the port had no such branch — so a resource link
  /// uploaded `createdDate`, `isLeader` and `public`, which Kotlin never
  /// sends, and (once [addResourceLink] began stamping it) `userPlanetCode`,
  /// which Kotlin sets on the row (`:683`) and deliberately omits from the
  /// document.
  ///
  /// That last one is the point. The same round declined to stamp `status`
  /// from `addResourceLinks` (`:677`) on exactly this argument — Kotlin keeps
  /// it device-local and this serializer would have sent it — and then added
  /// `userPlanetCode`, which is the identical shape, while a comment two
  /// methods up claimed the gap had not widened. Branching here honours both:
  /// the **row** matches Kotlin's row and the **document** matches Kotlin's
  /// document, instead of trading one against the other.
  ///
  /// `teamId` is guarded on non-empty rather than non-null because Kotlin
  /// writes it with `JsonUtils.addString` (`:170`), which skips null *and*
  /// empty — the only field in this branch that does.
  static Map<String, dynamic> serializeTeamDocument(TeamRow row) {
    if (row.docType == 'resourceLink') {
      return {
        '_id': row.id,
        if (row.rev?.isNotEmpty == true) '_rev': row.rev,
        if (row.resourceId != null) 'resourceId': row.resourceId,
        if (row.title != null) 'title': row.title,
        if (row.teamId?.isNotEmpty == true) 'teamId': row.teamId,
        if (row.teamPlanetCode != null) 'teamPlanetCode': row.teamPlanetCode,
        if (row.teamType != null) 'teamType': row.teamType,
        if (row.sourcePlanet != null) 'sourcePlanet': row.sourcePlanet,
        'docType': row.docType,
      };
    }
    return _serializeGeneralTeamDocument(row);
  }

  static Map<String, dynamic> _serializeGeneralTeamDocument(TeamRow row) => {
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
    // The four planet-code fields, ported from `MyTeam.serialize`'s general
    // branch (`MyTeam.kt:204`, `:207`, `:208`, `:211`), where all four are
    // written unconditionally and the `JsonNull` sweep at `:222-223` then
    // removes whichever are null.
    //
    // **Guarded here rather than swept, and the difference is real.** Kotlin's
    // sweep drops `JsonNull` and keeps `""` — a `JsonPrimitive` — so a column
    // the mapper filled from an absent key (`JsonUtils.getString` defaults to
    // `""`) is re-uploaded as an explicit empty string. [TeamMapper.fromDoc]
    // writes null for an absent key instead, so the port sends no key where
    // Kotlin sends `""`. That restores the document to the shape it had before
    // either app touched it; manufacturing `""` would assert a planet code the
    // server never sent.
    //
    // **What this fixes is not the stamping, it is the round trip.** Before
    // these columns existed the port could not emit them at all, so any team
    // document the user edited offline — `updateTeam`, `respondToRequest`, a
    // finance report — uploaded *without* the planet codes the server had
    // sent, silently overwriting them. Same class as Phases 56, 74 and 98,
    // reached through the upload direction rather than the pull.
    //
    // This is the **general** branch only. `MyTeam.serialize`'s `resourceLink`
    // early return (`:167-179`) carries just `teamPlanetCode` and
    // `sourcePlanet` of the four, and [serializeTeamDocument] now mirrors that
    // above rather than relying on a null to keep the key off the wire — which
    // is what the sentence this replaces claimed, and which stopped being true
    // the moment [addResourceLink] began stamping `userPlanetCode`.
    if (row.sourcePlanet != null) 'sourcePlanet': row.sourcePlanet,
    if (row.teamPlanetCode != null) 'teamPlanetCode': row.teamPlanetCode,
    if (row.userPlanetCode != null) 'userPlanetCode': row.userPlanetCode,
    if (row.parentCode != null) 'parentCode': row.parentCode,
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

/// Ranks the eligible successors to a departing team member and returns the
/// **membership row** that should be promoted, or null when nobody can be.
///
/// Port of the tail of `TeamsRepositoryImpl.getNextLeaderCandidate`
/// (`:1079-1104`), from the `getUsersByIds` call onwards. Pure — the three
/// reads it needs are done by the caller — so the ranking can be pinned
/// without a database, the way [sortTeamsCatalog] already is.
///
/// The algorithm, and every step of it is load-bearing:
///
/// 1. [users] keyed by identity — `users.associateBy { it.id }` at `:1091`,
///    but see the divergence below.
/// 2. `userNames = users.mapNotNull { it.name }.distinct()` (`:1092`) — the
///    argument the caller passed to `teamVisitsForUsers`, which keys
///    `team_log` on the user's **name**, not their id.
/// 3. `visitCounts = logs.groupingBy { it.user }.eachCount()` (`:1098`).
/// 4. `members.maxByOrNull { visitCounts[userMap[it.userId]?.name] ?: 0 }`
///    (`:1100-1102`) — the successor is the remaining member with the most
///    team visits, defaulting to zero.
///
/// **Ties go to the first candidate in [candidates].** Kotlin's `maxByOrNull`
/// replaces its running maximum only on a strict `>` (verified against the
/// compiled stdlib, not its documentation), so the earliest element wins — and
/// `getEligibleNextLeaderCandidates` has no `ORDER BY`, so on a tie Kotlin
/// promotes whoever SQLite happened to return first. The port cannot be more
/// deterministic than that without inventing an ordering Kotlin does not have,
/// so it reproduces the rule and leaves the order to the caller's query.
///
/// **The one place this deliberately does not reproduce Kotlin: the identity
/// lookup.** Kotlin fetches users with `id IN (:ids) OR _id IN (:ids)`
/// (`UserDao.kt:12-13`) and then keys the map on `it.id` alone, while looking
/// up by `member.userId`. A membership whose `userId` holds the server's `_id`
/// for an account still carrying a locally-minted `id` therefore resolves to
/// no user: it scores zero, and if it wins, Kotlin's closing
/// `userMap[successorMember.userId]` returns null and **nobody is promoted at
/// all** — the team is left leaderless by the very code meant to prevent it.
///
/// That state is reachable, and not only on Android. Kotlin writes a
/// membership's `userId` from `user.id` in one place
/// (`TeamsRepositoryImpl:620`) and from `user._id` in another (`:174`,
/// `createTeamAndAddMember`), and `markUserUploaded` (`UserRepositoryImpl
/// :946-951`) never rewrites a local account's primary key — so both spellings
/// reach CouchDB and both sync down here, where `TeamMapper` stores whatever
/// the document says. Reproducing the asymmetry would mean this lane's fix
/// silently does nothing for exactly those teams, which is the defect it
/// exists to close, so the map is keyed on **both** columns — the same pair
/// `UserDao.getByAnyIds` matched on to fetch them, and the pair Kotlin's own
/// `mapUsersByAnyId` (`:952-961`) uses 130 lines earlier in the same file for
/// the members list. The effect is strictly to resolve candidates Kotlin
/// loses; a candidate that resolves under `id` resolves identically here.
///
/// Returning the **membership row** rather than the user closes the second
/// half of the same hole: the caller promotes with `updateTeamLeader`, which
/// matches `row.userId == newLeaderId`, so handing it the *user's* `id` — as
/// Kotlin does at `RequestsViewModel:82` — misses for the same rows. The
/// requirement that the winner resolve to a real user is kept: an
/// unresolvable candidate can never be promoted.
UserRow? _resolve(Map<String, UserRow> userMap, String? userId) =>
    userId == null ? null : userMap[userId];

TeamRow? selectNextLeaderCandidate({
  required List<TeamRow> candidates,
  required List<UserRow> users,
  required List<TeamLogRow> visits,
}) {
  if (candidates.isEmpty) return null;
  if (users.isEmpty) return null;
  final userMap = <String, UserRow>{};
  for (final user in users) {
    userMap.putIfAbsent(user.id, () => user);
    final couchId = user.couchId;
    if (couchId != null && couchId.isNotEmpty) {
      userMap.putIfAbsent(couchId, () => user);
    }
  }
  final visitCounts = <String, int>{};
  for (final log in visits) {
    final name = log.user;
    if (name == null) continue;
    visitCounts[name] = (visitCounts[name] ?? 0) + 1;
  }
  int scoreOf(TeamRow member) {
    final name = _resolve(userMap, member.userId)?.name;
    if (name == null) return 0;
    return visitCounts[name] ?? 0;
  }

  var best = candidates.first;
  var bestScore = scoreOf(best);
  for (final candidate in candidates.skip(1)) {
    final score = scoreOf(candidate);
    // Strictly greater, so the first candidate holding the maximum keeps it —
    // `maxByOrNull`'s tie rule. `>=` here would silently promote the *last*
    // most-active member instead.
    if (score > bestScore) {
      best = candidate;
      bestScore = score;
    }
  }
  // Kotlin's closing `successorMember?.userId?.let { userMap[it] }` — the
  // winner has to be somebody this device actually knows, or nobody is
  // promoted.
  return _resolve(userMap, best.userId) == null ? null : best;
}

/// The distinct names [selectNextLeaderCandidate]'s `visits` argument must be
/// fetched for — `users.mapNotNull { it.name }.distinct()` at
/// `TeamsRepositoryImpl.kt:1092`.
List<String> leaderCandidateVisitNames(List<UserRow> users) {
  final seen = <String>{};
  for (final user in users) {
    final name = user.name;
    if (name != null) seen.add(name);
  }
  return seen.toList();
}
