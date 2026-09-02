import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'sync_state.dart';
import 'session_provider.dart';
import '../core/utils/url_utils.dart';
import '../repository/teams_repository.dart';
import '../repository/teams_uploader.dart';

final teamsSearchProvider = StateProvider<String>((ref) => '');
final teamsTypeProvider = StateProvider<String>((ref) => 'team');

final teamMembershipsProvider = StreamProvider<Map<String, TeamRow>>((ref) {
  final userId = ref.watch(sessionProvider).valueOrNull?.id;
  if (userId == null || userId.isEmpty) return Stream.value(const {});
  return ref
      .watch(teamsRepositoryProvider)
      .watchMemberships(userId)
      .map(
        (rows) => {
          for (final row in rows)
            if (row.teamId?.isNotEmpty == true) row.teamId!: row,
        },
      );
});

final teamMemberCountProvider = StreamProvider.family<int, String>(
  (ref, teamId) => ref.watch(teamsRepositoryProvider).watchMemberCount(teamId),
);

final teamMembersProvider = StreamProvider.family<List<TeamRow>, String>(
  (ref, teamId) => ref.watch(teamsRepositoryProvider).watchMembers(teamId),
);
final teamRequestsProvider = StreamProvider.family<List<TeamRow>, String>(
  (ref, teamId) => ref.watch(teamsRepositoryProvider).watchRequests(teamId),
);
final teamResourceLinksProvider = StreamProvider.family<List<TeamRow>, String>(
  (ref, teamId) =>
      ref.watch(teamsRepositoryProvider).watchResourceLinks(teamId),
);

/// The profile + visit data `MemberDetailScreen` shows, joined the way the
/// Kotlin's `getJoinedMembersWithVisitInfo` joins it for one member: the
/// `users` row, the per-team visit count (from `team_log`), and the last
/// login timestamp (from `offline_activity`). `null` when the member's user
/// document is not in the local cache (a guest, or a member whose profile
/// has not synced yet).
class MemberDetail {
  const MemberDetail({
    required this.user,
    required this.visitCount,
    required this.lastVisit,
    required this.lastLogin,
    required this.isLeader,
  });

  final UserRow user;
  final int visitCount;
  final int? lastVisit;
  final int? lastLogin;
  final bool isLeader;
}

final memberDetailProvider =
    FutureProvider.family<MemberDetail?, ({String teamId, String userId})>((
      ref,
      key,
    ) async {
      final user = await ref.watch(userDaoProvider).getById(key.userId);
      if (user == null) return null;
      final visits = await ref
          .watch(teamsRepositoryProvider)
          .teamVisitsForUsers(key.teamId, [user.name ?? '']);
      final lastVisit = await ref
          .watch(teamsRepositoryProvider)
          .lastTeamVisit(user.name, key.teamId);
      final lastLogin = user.name == null || user.name!.isEmpty
          ? null
          : await ref.watch(activitiesRepositoryProvider).lastVisit(user.name!);
      // `teamMembersProvider` rows carry `isLeader`; look it up so the
      // detail screen's header matches the list's leader star.
      final membership = await ref
          .watch(teamsRepositoryProvider)
          .membership(key.teamId, key.userId);
      return MemberDetail(
        user: user,
        visitCount: visits.length,
        lastVisit: lastVisit,
        lastLogin: lastLogin,
        isLeader: membership?.isLeader ?? false,
      );
    });
final teamResourcesProvider = StreamProvider.family<List<MyLibraryRow>, String>(
  (ref, teamId) async* {
    await for (final links
        in ref.watch(teamsRepositoryProvider).watchResourceLinks(teamId)) {
      final ids = links
          .map((row) => row.resourceId)
          .whereType<String>()
          .toList();
      yield ids.isEmpty
          ? const []
          : await ref.watch(myLibraryDaoProvider).getByIds(ids);
    }
  },
);
final teamCoursesProvider = FutureProvider.family<List<CourseRow>, String>((
  ref,
  teamId,
) async {
  final team = await ref.watch(teamsRepositoryProvider).getById(teamId);
  return team == null || team.courses.isEmpty
      ? const []
      : ref.watch(courseDaoProvider).getByIds(team.courses);
});
final teamReportsProvider = StreamProvider.family<List<TeamRow>, String>(
  (ref, teamId) => ref.watch(teamsRepositoryProvider).watchReports(teamId),
);

/// A financial transaction with computed balance.
class TransactionRow {
  final TeamRow row;
  final int balance;
  TransactionRow({required this.row, required this.balance});
}

final teamTransactionsProvider =
    StreamProvider.family<
      List<TransactionRow>,
      ({String teamId, int? startDate, int? endDate, bool ascending})
    >((ref, params) {
      final repo = ref.watch(teamsRepositoryProvider);
      return repo
          .watchTransactions(
            params.teamId,
            startDate: params.startDate,
            endDate: params.endDate,
            ascending: params.ascending,
          )
          .map((rows) {
            var balance = 0;
            final result = <TransactionRow>[];
            for (final row in params.ascending ? rows : rows) {
              if (row.type?.toLowerCase() == 'debit') {
                balance -= row.amount;
              } else {
                balance += row.amount;
              }
              result.add(TransactionRow(row: row, balance: balance));
            }
            return params.ascending ? result : result.reversed.toList();
          });
    });

class TeamFinancesActions {
  TeamFinancesActions(this.ref);
  final Ref ref;

  Future<bool> createTransaction({
    required String teamId,
    required String type,
    required String note,
    required int amount,
    required int date,
    String? imageName,
    List<int>? imageBytes,
  }) async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return false;
    final row = await ref
        .read(teamsRepositoryProvider)
        .createTransaction(
          teamId: teamId,
          type: type,
          note: note,
          amount: amount,
          date: date,
          imageName: imageName,
          imageBytes: imageBytes,
        );
    if (row == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: TeamsUploader.financesType,
          itemId: row.id,
          endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
          payload: TeamsRepository.serializeTeamDocument(row),
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
    return true;
  }
}

final teamFinancesActionsProvider = Provider<TeamFinancesActions>(
  TeamFinancesActions.new,
);

/// Whether a membership row the server has never seen — no `_rev` — should
/// have a tombstone uploaded for it.
///
/// `markMembershipsForLeave` (`TeamsRepositoryImpl.kt:1323-1337`) branches on
/// exactly this: `if (membership._rev.isNullOrBlank())` the row is deleted
/// locally and **nothing** is uploaded; only a revision-bearing row becomes a
/// `{_id, _rev, _deleted: true}` document. Enqueueing regardless sent
/// `"_rev": null`, which CouchDB rejects 4xx — and the outbox's retryable
/// rule is `code >= 500`, so the row failed out permanently for a document
/// the server never had.
bool _serverKnowsRow(String? rev) => rev != null && rev.trim().isNotEmpty;

/// The signed-in user's id, resolved rather than read — see
/// [TeamMembershipActions._session] for why the rejection is swallowed.
Future<String?> _sessionUserId(Ref ref) async {
  try {
    return (await ref.read(sessionProvider.future))?.id;
  } catch (_) {
    return null;
  }
}

class TeamMembershipActions {
  TeamMembershipActions(this.ref);
  final Ref ref;

  /// `TeamMembershipActions` is a plain `Provider`, so its `ref` never
  /// *watches* `sessionProvider` — `ref.read(sessionProvider).valueOrNull` is
  /// null until something else resolves it, and every action below then took
  /// its `return false` branch silently. Latent in the app only because the
  /// router holds a `ref.listen`; not a guarantee this class can rely on.
  ///
  /// **The rejection is swallowed here, deliberately.** Awaiting `.future`
  /// resolves the session properly, but a future rejects where `valueOrNull`
  /// could only be null — and not one of the five call sites wraps the await:
  /// `team_members_screen`'s `_handleMemberAction` shows its "operation
  /// failed" snackbar off the returned `false`, and the join button and the
  /// leave dialog are both fire-and-forget `onPressed`s. Throwing would lose
  /// that message and escape as an uncaught async error, which is strictly
  /// worse than the `false` it replaced. Returning null keeps every action's
  /// existing contract: it reports failure, and the caller says so.
  Future<UserRow?> _session() async {
    try {
      return await ref.read(sessionProvider.future);
    } catch (_) {
      return null;
    }
  }

  String? get _endpoint {
    final config = ref.read(serverConfigProvider);
    return config == null
        ? null
        : '${UrlUtils.credentialFreeDbUrl(config)}/teams';
  }

  Future<bool> requestToJoin(TeamRow team) async {
    final user = await _session();
    final endpoint = _endpoint;
    if (user == null || endpoint == null) return false;
    final row = await ref
        .read(teamsRepositoryProvider)
        .createJoinRequest(
          teamId: team.id,
          userId: user.id,
          teamType: team.teamType,
          planetCode: user.planetCode,
        );
    if (row == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamMembership',
          itemId: row.id,
          endpoint: endpoint,
          payload: TeamsRepository.serializeTeamDocument(row),
          userId: user.id,
        );
    return true;
  }

  Future<bool> leave(String teamId) async {
    final user = await _session();
    final endpoint = _endpoint;
    if (user == null || endpoint == null) return false;
    final row = await ref.read(teamsRepositoryProvider).leave(teamId, user.id);
    if (row == null) return false;
    if (_serverKnowsRow(row.rev)) {
      await ref
          .read(outboxRepositoryProvider)
          .enqueue(
            uploadType: 'teamMembership',
            itemId: row.id,
            endpoint: endpoint,
            payload: {'_id': row.id, '_rev': row.rev, '_deleted': true},
            userId: user.id,
          );
    }
    return true;
  }

  /// Port of `MembersAdapter`'s remove-member overflow action. A leader
  /// removes another member: the membership row is hard-deleted locally and
  /// a tombstone is enqueued, exactly as [leave] does for the current user.
  Future<bool> removeMember(String teamId, String userId) async {
    final endpoint = _endpoint;
    final currentUser = await _session();
    if (endpoint == null || currentUser == null) return false;
    final row = await ref
        .read(teamsRepositoryProvider)
        .removeMember(teamId, userId);
    if (row == null) return false;
    if (_serverKnowsRow(row.rev)) {
      await ref
          .read(outboxRepositoryProvider)
          .enqueue(
            uploadType: 'teamMembership',
            itemId: row.id,
            endpoint: endpoint,
            payload: {'_id': row.id, '_rev': row.rev, '_deleted': true},
            userId: currentUser.id,
          );
    }
    return true;
  }

  /// Port of `MembersAdapter`'s make-leader overflow action. Flips
  /// `isLeader` on every membership (true for the new leader, false for the
  /// rest) and enqueues each changed row for upload.
  Future<bool> makeLeader(String teamId, String newLeaderId) async {
    final endpoint = _endpoint;
    final currentUser = await _session();
    if (endpoint == null || currentUser == null) return false;
    final changed = await ref
        .read(teamsRepositoryProvider)
        .updateTeamLeader(teamId, newLeaderId);
    if (changed.isEmpty) return false;
    for (final row in changed) {
      await ref
          .read(outboxRepositoryProvider)
          .enqueue(
            uploadType: 'teamMembership',
            itemId: row.id,
            endpoint: endpoint,
            payload: TeamsRepository.serializeTeamDocument(row),
            userId: currentUser.id,
          );
    }
    return true;
  }

  Future<bool> respond(String requestId, {required bool accept}) async {
    final endpoint = _endpoint;
    if (endpoint == null) return false;
    // Resolved *before* `respondToRequest`, as the four actions above do.
    // This was the one session read placed after its own local write, and
    // `respondToRequest` has already converted the request row into a
    // `membership` with `isUpdated = true` by then. The outbox is the only
    // upload route — `TeamDao` has no pending sweep and `TeamsUploader` has
    // no rescan — so failing between the two left the accepted member on
    // this device and nowhere else, permanently.
    final user = await _session();
    if (user == null) return false;
    final original = await ref.read(teamsRepositoryProvider).getById(requestId);
    if (original == null) return false;
    final updated = await ref
        .read(teamsRepositoryProvider)
        .respondToRequest(requestId, accept: accept);
    if (updated == null) return false;
    // A declined request the server never saw needs no tombstone either.
    if (accept || _serverKnowsRow(original.rev)) {
      await ref
          .read(outboxRepositoryProvider)
          .enqueue(
            uploadType: 'teamMembership',
            itemId: original.id,
            endpoint: endpoint,
            payload: accept
                ? TeamsRepository.serializeTeamDocument(updated)
                : {'_id': original.id, '_rev': original.rev, '_deleted': true},
            userId: user.id,
          );
    }
    return true;
  }
}

class TeamResourceActions {
  TeamResourceActions(this.ref);
  final Ref ref;

  Future<bool> add(String teamId, MyLibraryRow resource) async {
    final config = ref.read(serverConfigProvider);
    if (config == null || resource.resourceId?.isNotEmpty != true) return false;
    final row = await ref
        .read(teamsRepositoryProvider)
        .addResourceLink(
          teamId: teamId,
          resourceId: resource.resourceId!,
          title: resource.title ?? '',
          planetCode: ref.read(sessionProvider).valueOrNull?.planetCode,
        );
    if (row == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamResource',
          itemId: row.id,
          endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
          payload: TeamsRepository.serializeTeamDocument(row),
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
    return true;
  }

  Future<bool> remove(String teamId, MyLibraryRow resource) async {
    final config = ref.read(serverConfigProvider);
    if (config == null || resource.resourceId == null) return false;
    final row = await ref
        .read(teamsRepositoryProvider)
        .removeResourceLink(teamId, resource.resourceId!);
    if (row == null) return false;
    // The fourth tombstone in this file, and it needs the same guard as the
    // three in `TeamMembershipActions`: `removeResourceLink` hard-deletes a
    // row that has no `rev` until it uploads. Now reachable by far more
    // users, because the add gate this phase corrected to plain membership
    // lets any member create such a link in the first place.
    if (_serverKnowsRow(row.rev)) {
      await ref
          .read(outboxRepositoryProvider)
          .enqueue(
            uploadType: 'teamResource',
            itemId: row.id,
            endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
            payload: {'_id': row.id, '_rev': row.rev, '_deleted': true},
            userId: (await _sessionUserId(ref)),
          );
    }
    return true;
  }
}

final teamResourceActionsProvider = Provider<TeamResourceActions>(
  TeamResourceActions.new,
);

class TeamCourseActions {
  TeamCourseActions(this.ref);
  final Ref ref;

  Future<bool> _queue(TeamRow? team) async {
    final config = ref.read(serverConfigProvider);
    if (team == null || config == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamCourses',
          itemId: team.id,
          endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
          payload: TeamsRepository.serializeTeamDocument(team),
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
    ref.invalidate(teamProvider(team.id));
    ref.invalidate(teamCoursesProvider(team.id));
    return true;
  }

  Future<bool> add(String teamId, Iterable<String> courseIds) async => _queue(
    await ref.read(teamsRepositoryProvider).addCourses(teamId, courseIds),
  );

  Future<bool> remove(String teamId, String courseId) async => _queue(
    await ref.read(teamsRepositoryProvider).removeCourse(teamId, courseId),
  );
}

final teamCourseActionsProvider = Provider<TeamCourseActions>(
  TeamCourseActions.new,
);

class TeamReportActions {
  TeamReportActions(this.ref);
  final Ref ref;

  Future<bool> _queue(TeamRow? report) async {
    final config = ref.read(serverConfigProvider);
    if (report == null || config == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamReports',
          itemId: report.id,
          endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
          payload: TeamsRepository.serializeTeamDocument(report),
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
    return true;
  }

  Future<bool> save({
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
  }) async => _queue(
    await ref
        .read(teamsRepositoryProvider)
        .saveReport(
          id: id,
          teamId: teamId,
          description: description,
          startDate: startDate,
          endDate: endDate,
          beginningBalance: beginningBalance,
          sales: sales,
          otherIncome: otherIncome,
          wages: wages,
          otherExpenses: otherExpenses,
          imageName: imageName,
          imageBytes: imageBytes,
        ),
  );

  Future<bool> archive(String id) async =>
      _queue(await ref.read(teamsRepositoryProvider).archiveReport(id));
}

final teamReportActionsProvider = Provider<TeamReportActions>(
  TeamReportActions.new,
);

final teamMembershipActionsProvider = Provider<TeamMembershipActions>(
  TeamMembershipActions.new,
);

final teamsProvider = StreamProvider<List<TeamRow>>((ref) async* {
  // Trimmed, deliberately: `TeamViewModel.applyFilters` does not trim, so a
  // trailing space in Kotlin hides the whole catalog. Every other rule below
  // follows the Kotlin exactly.
  final search = ref.watch(teamsSearchProvider).trim().toLowerCase();
  final type = ref.watch(teamsTypeProvider);
  final userId = ref.watch(sessionProvider).valueOrNull?.id;
  final repo = ref.watch(teamsRepositoryProvider);
  await for (final rows in repo.watchCatalog(type: type)) {
    final filtered = rows
        .where(
          // `TeamViewModel.applyFilters` (TeamViewModel.kt:112-118) is a flat,
          // case-insensitive `contains` on **`name` only** — not the ranked
          // `ResourcesSearchUtils` algorithm Phases 96/97 ported for resources
          // and surveys, and not the description. Matching the description
          // surfaced teams whose name the query does not appear in at all.
          (row) =>
              search.isEmpty || (row.name ?? '').toLowerCase().contains(search),
        )
        .toList();
    // Port of `TeamsRepositoryImpl.mapToTeamDetails`'s sort: leader > member
    // > non-member, then 30-day visit count DESC. Without it the catalog was
    // alphabetical (`name ASC`) and a user's own teams floated nowhere near
    // the top, diverging from the Kotlin list.
    final ids = filtered.map((r) => r.id).toList();
    final statuses = await repo.memberStatuses(userId, ids);
    final visits = await repo.recentVisitCounts(ids);
    yield sortTeamsCatalog(filtered, statuses, visits);
  }
});

final teamProvider = FutureProvider.family<TeamRow?, String>(
  (ref, id) => ref.watch(teamsRepositoryProvider).getById(id),
);

class TeamsSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(config, void Function(SyncProgress) onProgress) =>
      ref
          .read(teamsRepositoryProvider)
          .sync(config: config, onProgress: onProgress);
}

final teamsSyncProvider = NotifierProvider<TeamsSyncNotifier, SyncUiState>(
  TeamsSyncNotifier.new,
);
