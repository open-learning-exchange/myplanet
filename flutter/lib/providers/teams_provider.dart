import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';

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
  final userId = ref.watch(sessionProvider).value?.id;
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

/// The team Resources tab: port of `TeamsRepositoryImpl.getTeamResources`
/// (`:318-323`), which is a **union of two arms** de-duplicated by id —
/// the library items behind the team's `resourceLink` documents, *plus* the
/// team's private resources (`MyLibraryDao.getTeamPrivate`).
///
/// The port had only the first arm, and nothing in `lib/` read `privateFor`
/// at all. `add_resource_screen` defaults `isPrivate` to true when it is
/// opened from a team, so a resource added there was in **no view of the
/// app** — not the catalog, not My Library, not the team tab — until an
/// upload created a link document for it, and permanently invisible if
/// pulled from a server holding no `resourceLink` document.
///
/// Links first, then private, so the ordering the links arm already carried
/// is preserved and the private arm appends; `getTeamPrivate` has no
/// `ORDER BY` in either app. De-duplication is by `id`, matching the Kotlin's
/// `distinctBy { it.id }`, and it is load-bearing rather than defensive: a
/// resource can be both privately held by the team and linked from it.
final teamResourcesProvider = StreamProvider.family<List<MyLibraryRow>, String>(
  (ref, teamId) async* {
    await for (final links
        in ref.watch(teamsRepositoryProvider).watchResourceLinks(teamId)) {
      final ids = links
          .map((row) => row.resourceId)
          .whereType<String>()
          .toList();
      final dao = ref.watch(myLibraryDaoProvider);
      final linked = ids.isEmpty
          ? const <MyLibraryRow>[]
          : await dao.getByIds(ids);
      final private = await dao.getTeamPrivate(teamId);
      final seen = <String>{};
      yield [
        for (final row in [...linked, ...private])
          if (seen.add(row.id)) row,
      ];
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
          userId: await resolveSessionUserId(ref),
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

/// Outcome of a Members-screen membership action, porting
/// `MemberActionResult` (`RequestsViewModel.kt:40-45`). A plain `bool` cannot
/// carry the middle case: the last-leader refusal is not a failure, and
/// Kotlin shows it its own string (`cannot_remove_user`) rather than the
/// generic error toast.
enum MemberActionOutcome { succeeded, failed, cannotRemoveLastLeader }

class TeamMembershipActions {
  TeamMembershipActions(this.ref);
  final Ref ref;

  String? get _endpoint {
    final config = ref.read(serverConfigProvider);
    return config == null
        ? null
        : '${UrlUtils.credentialFreeDbUrl(config)}/teams';
  }

  Future<bool> requestToJoin(TeamRow team) async {
    final user = await resolveSession(ref);
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
    final user = await resolveSession(ref);
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

  /// Port of `RequestsViewModel.leaveTeam` (`:77-90`) — the Members screen's
  /// own leave, which is **not** the same action as [leave].
  ///
  /// Kotlin has two leave paths and only this one transfers leadership.
  /// `TeamViewModel.leaveTeam` (`:143-151`), behind the team detail screen's
  /// button, calls `TeamsRepositoryImpl.leaveTeam` directly and promotes
  /// nobody; `RequestsViewModel.leaveTeam` (`:77-90`), behind the Members screen's
  /// overflow menu, resolves a successor first. [leave] is the port of the
  /// former and `teams_screen.dart` still calls it; this is the port of the
  /// latter. Keeping them separate is the whole reason this method exists
  /// rather than the succession going inside [leave] — that would have
  /// silently changed the detail screen's behaviour too, away from its
  /// Kotlin counterpart.
  ///
  /// **There is deliberately no last-leader refusal here, because Kotlin has
  /// none.** `leaveTeam` promotes when it can and then removes the member
  /// unconditionally (`:82-83`). What keeps a Kotlin team from going
  /// leaderless is the *menu* gate — `MembersAdapter
  /// .checkUserAndShowOverflowMenu` only offers Leave while
  /// `itemCount > 1` — which `team_members_screen.dart` now carries too.
  Future<MemberActionOutcome> leaveFromMembers(String teamId) async {
    // **Kotlin wraps all three of these actions in `try`/`catch` and emits
    // `MemberActionResult.Failed`, which toasts the error
    // (`RequestsViewModel:86-88`, `MembersFragment:115-122`). The port had no
    // `try` anywhere on this path**, and the screen fires these unawaited from
    // `onSelected`, so a throw became an unhandled zone error: no snackbar, no
    // record, nothing the user could see. `failed` was returned only for a
    // null session, endpoint or row — never for a throw. The realistic
    // triggers are the drift writes and the outbox insert, and the state after
    // a throw part-way through is the one worth telling someone about:
    // successor promoted, old leader demoted, leaver still a member.
    try {
      return await _leaveFromMembers(teamId);
    } catch (_) {
      return MemberActionOutcome.failed;
    }
  }

  Future<MemberActionOutcome> _leaveFromMembers(String teamId) async {
    final user = await resolveSession(ref);
    if (user == null) return MemberActionOutcome.failed;
    // Order matters and is Kotlin's: resolve and promote the successor
    // *before* the membership row goes. `eligibleNextLeaderCandidates`
    // excludes the leaving user by predicate, so the promotion cannot land on
    // them either way — but running it after the delete would mean a failure
    // between the two steps leaves the team with neither the old leader nor a
    // new one.
    await _succeedLeadership(teamId, user.id);
    final left = await leave(teamId);
    return left ? MemberActionOutcome.succeeded : MemberActionOutcome.failed;
  }

  /// Port of `MembersAdapter`'s remove-member overflow action
  /// (`RequestsViewModel.removeMember`, `:92-112`). A leader removes another
  /// member: the membership row is hard-deleted locally and a tombstone is
  /// enqueued, exactly as [leave] does for the current user.
  ///
  /// **The `currentUser.id == userId` branch is unreachable from this port's
  /// UI today, and the first cut of this comment claimed otherwise.** It
  /// argued that `isOwnCard` is false while the session loads, so a leader's
  /// own card would briefly render the other branch's menu — which is Kotlin's
  /// race (`MembersFragment:56` builds the adapter with `user?.id` before
  /// `ensureUserResolved` completes) but not this port's. Here `canManage`
  /// comes from `teamMembershipsProvider`, which watches the *same*
  /// `sessionProvider`, so while it is unresolved `canManage` and `isOwnCard`
  /// are both false and `(canManage || isOwnCard)` renders no menu at all.
  ///
  /// The guard is kept anyway, because it is Kotlin's and because this is a
  /// public method whose only caller's gating could change — but it is
  /// defensive, not live, and `MemberActionOutcome.cannotRemoveLastLeader`
  /// is currently produced only in tests. The *reachable* last-leader case is
  /// a leader using Leave, and neither app refuses that; what prevents it
  /// there is the member-count gate on the menu.
  Future<MemberActionOutcome> removeMember(String teamId, String userId) async {
    // See [leaveFromMembers] for why these three carry a `try`.
    try {
      return await _removeMember(teamId, userId);
    } catch (_) {
      return MemberActionOutcome.failed;
    }
  }

  Future<MemberActionOutcome> _removeMember(
    String teamId,
    String userId,
  ) async {
    final endpoint = _endpoint;
    final currentUser = await resolveSession(ref);
    if (endpoint == null || currentUser == null) {
      return MemberActionOutcome.failed;
    }
    // Kotlin's condition exactly (`RequestsViewModel:96`): self-removal, with
    // no leadership test. An earlier cut added one here for symmetry with
    // `_needsSuccession`, which was a real narrowing of Kotlin bought for
    // nothing — the branch is unreachable either way, so the version with
    // less to justify is the right one.
    if (currentUser.id == userId) {
      final successor = await _nextLeaderCandidate(teamId, userId);
      final successorUserId = successor?.userId;
      if (successorUserId == null) {
        return MemberActionOutcome.cannotRemoveLastLeader;
      }
      await _promoteLeader(teamId, successorUserId);
    }
    final row = await ref
        .read(teamsRepositoryProvider)
        .removeMember(teamId, userId);
    if (row == null) return MemberActionOutcome.failed;
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
    return MemberActionOutcome.succeeded;
  }

  /// Hands leadership of [teamId] to the most active remaining member when
  /// [leavingUserId] is one of its leaders.
  ///
  /// **The `_isLeaderOf` gate is this lane's one deliberate divergence from
  /// Kotlin, and it is here rather than buried because it changes behaviour.**
  /// `RequestsViewModel.leaveTeam` calls `getNextLeaderCandidate`
  /// *unconditionally* (`:81`), and `updateTeamLeader` sets `isLeader = false`
  /// on every membership but the new leader
  /// (`TeamsRepositoryImpl.kt:1065-1074`). Put those together and a
  /// rank-and-file member leaving an Android team **demotes the sitting
  /// leader** and hands the team to whoever has visited it most — on a
  /// departure that changed nothing about who leads it.
  ///
  /// That is a defect rather than a design: the function is a *successor*
  /// lookup, and there is no successor to find when the leaver was not a
  /// leader. Inheriting it would ship a fresh data loss — an elected leader
  /// silently replaced — in the course of closing an older one, which is the
  /// shape Phase 143 named and Phase 156 refused for an inherited
  /// `deleteNotIn`. So the port asks the question Kotlin forgot to.
  ///
  /// The cost, stated so it can be argued with: a team whose leader has
  /// already been demoted by some other route stays leaderless here where
  /// Kotlin would opportunistically re-elect someone on the next departure.
  /// The port cannot reach that state — `updateTeamLeader` is the only writer
  /// of `isLeader` and it always leaves exactly one leader — so the divergence
  /// costs nothing reachable, while the alternative costs a leader every time
  /// anyone leaves.
  /// **A second divergence, inherited rather than chosen, and it runs the
  /// port's way.** `markMembershipsForLeave` (`TeamsRepositoryImpl:1286-1300`)
  /// *soft*-deletes a membership the server already knows about —
  /// `isDeletePending = true` — while `getEligibleNextLeaderCandidates` has no
  /// `isDeletePending` clause. So in Kotlin a member who left while offline is
  /// still an eligible successor, and promoting one is a leaderless team:
  /// `MyTeam.serialize` short-circuits on that flag to `{_id, _rev, _deleted}`
  /// (`MyTeam.kt:161-167`), so the `isLeader` never leaves the device and the
  /// row is then deleted outright — after the real leader has already been
  /// demoted. The port has no such column and no such state, because [leave]
  /// hard-deletes and enqueues the tombstone, so a departed member is simply
  /// not in the pool. Nothing had written that down.
  Future<void> _succeedLeadership(String teamId, String leavingUserId) async {
    final memberships = await ref
        .read(teamsRepositoryProvider)
        .memberships(teamId);
    if (!_needsSuccession(memberships, leavingUserId)) return;
    final successor = await _nextLeaderCandidate(teamId, leavingUserId);
    final successorUserId = successor?.userId;
    if (successorUserId == null) return;
    // Kotlin discards `updateTeamLeader`'s boolean here
    // (`nextLeader?.id?.let { ... }`, `:82`), so a no-op promotion is not a
    // failed leave. Note the port's `false` covers one case Kotlin's does not
    // — a null endpoint or session, i.e. "could not even try" — but both of
    // those also fail [leave] a moment later, so the user is still told.
    await _promoteLeader(teamId, successorUserId, skipUserId: leavingUserId);
  }

  /// Whether [leavingUserId]'s departure should hand leadership to somebody.
  ///
  /// **This gate is the lane's one deliberate divergence from Kotlin, and the
  /// second disjunct is what keeps the divergence honest.**
  ///
  /// `RequestsViewModel.leaveTeam` calls `getNextLeaderCandidate`
  /// *unconditionally* (`:81`), and `updateTeamLeader` sets `isLeader = false`
  /// on every membership but the new leader (`TeamsRepositoryImpl:1065-1074`).
  /// Put those together and a rank-and-file member leaving an Android team
  /// **demotes the sitting leader** and hands the team to whoever has visited
  /// it most — on a departure that changed nothing about who leads it. That is
  /// a defect rather than a design: the function is a *successor* lookup, and
  /// there is no successor to find when the leaver was not a leader.
  ///
  /// **But "only when the leaver is a leader" alone is worse than Kotlin, and
  /// that was this method's first cut.** Kotlin's unconditional call means a
  /// leaderless team *self-heals* on the next departure. Gating purely on the
  /// leaver's own flag removes that, and the port cannot afford it: leadership
  /// here is **not** locally authored. The port creates no teams at all, so
  /// every `isLeader` it holds arrived from a server document —
  /// `TeamMapper.fromDoc` writes it on every pull (`team_mapper.dart:78`), as
  /// does the catalog's `leaders` fan-out (`teams_repository.dart:124`). A
  /// team can therefore *arrive* leaderless, and once it is, `canManage` is
  /// false for everyone, so "Make leader" is never offered — leaving a team
  /// that no leave can ever repair. That is the exact outcome this lane
  /// exists to prevent, reached by the lane's own fix.
  ///
  /// So the question is asked of the team as well as of the leaver: promote a
  /// successor when the leaver holds leadership, **or** when nobody does.
  ///
  /// Reading every membership row rather than one also settles the duplicate
  /// case. `TeamDao.getTeamDocument` is `LIMIT 1` with no `ORDER BY`, and a
  /// re-join can leave a user with two membership documents (see
  /// `TeamDao.watchMemberCount`), so a `LIMIT 1` lookup would answer from
  /// whichever row SQLite handed back first.
  static bool _needsSuccession(
    List<TeamRow> memberships,
    String leavingUserId,
  ) {
    final leaverIsLeader = memberships.any(
      (row) => row.userId == leavingUserId && row.isLeader,
    );
    if (leaverIsLeader) return true;
    return !memberships.any((row) => row.isLeader);
  }

  /// Port of `TeamsRepositoryImpl.getNextLeaderCandidate` (`:1079-1104`) —
  /// the most-active remaining member of [teamId], or null when there is
  /// nobody to promote.
  ///
  /// The three reads are done here and the ranking in the pure
  /// [selectNextLeaderCandidate], so the part with the tie rules can be
  /// pinned without a database.
  ///
  /// **Step three is why this is portable at all now and was not before.**
  /// The ranking counts `team_log` rows, and until the `team_activities`
  /// sync-in landed that table held only what *this handset* had observed —
  /// so the "most active member" would have meant "most active on the
  /// leaving user's phone". The pull makes the counts cross-device and the
  /// ranking meaningful.
  Future<TeamRow?> _nextLeaderCandidate(
    String teamId,
    String? excludeUserId,
  ) async {
    final repository = ref.read(teamsRepositoryProvider);
    final candidates = await repository.eligibleNextLeaderCandidates(
      teamId,
      excludeUserId,
    );
    if (candidates.isEmpty) return null;
    final userIds = candidates
        .map((row) => row.userId)
        .whereType<String>()
        .toList();
    if (userIds.isEmpty) return null;
    final users = await ref.read(userDaoProvider).getByAnyIds(userIds);
    if (users.isEmpty) return null;
    // **The exclusion predicate matches one spelling; resolution matches two.**
    // `eligibleNextLeaderCandidates` drops rows whose `userId` string equals
    // [excludeUserId], but `getByAnyIds` resolves `id` *or* `couchId`. A user
    // holding two membership documents under the two spellings — the state
    // `TeamDao.watchMemberCount` documents as reachable — therefore leaves one
    // of their own rows in the pool, and it resolves to them. Without this,
    // the departing member could be promoted into the team they are leaving.
    // Kotlin cannot reach it only because its one-keyed map resolves that row
    // to nobody; having deliberately fixed that, the port has to close this
    // too.
    final leaving = excludeUserId == null
        ? null
        : await ref.read(userDaoProvider).getById(excludeUserId);
    final eligible = leaving == null
        ? candidates
        : [
            for (final row in candidates)
              if (!users.any(
                (user) =>
                    user.id == leaving.id &&
                    (user.id == row.userId || user.couchId == row.userId),
              ))
                row,
          ];
    if (eligible.isEmpty) return null;
    final names = leaderCandidateVisitNames(users);
    final visits = names.isEmpty
        ? const <TeamLogRow>[]
        : await repository.teamVisitsForUsers(teamId, names);
    return selectNextLeaderCandidate(
      candidates: eligible,
      users: users,
      visits: visits,
    );
  }

  /// `updateTeamLeader` plus the outbox enqueue for every row it changed —
  /// the body [makeLeader] already had, shared with the two succession call
  /// sites so a promoted successor reaches the server by the same route a
  /// hand-picked one does.
  ///
  /// **The enqueue is a divergence in *when*, not in *what*.** Kotlin queues
  /// nothing from this screen: it leaves the changed rows on `updated = true`
  /// for the next `getUpdatedTeams()` upload sweep. The port has no such
  /// sweep — the outbox is the only route off the device — so enqueueing here
  /// is what makes the promotion reach the server at all, and the documents
  /// are identical either way.
  Future<bool> _promoteLeader(
    String teamId,
    String newLeaderId, {
    String? skipUserId,
  }) async {
    final endpoint = _endpoint;
    final currentUser = await resolveSession(ref);
    if (endpoint == null || currentUser == null) return false;
    final changed = await ref
        .read(teamsRepositoryProvider)
        .updateTeamLeader(teamId, newLeaderId);
    if (changed.isEmpty) return false;
    for (final row in changed) {
      // [skipUserId] is the departing member, whose row [leave] is about to
      // delete. Their demotion is a change to a document that is on its way
      // out, and enqueueing it is worse than useless: [leave] only writes a
      // tombstone when the server already knows the row, so for a
      // locally-authored membership the demotion would be the *only* thing
      // left in the outbox under that id — a create-shaped POST that puts the
      // leaver back into the team on the next pull.
      if (skipUserId != null && row.userId == skipUserId) continue;
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

  /// Port of `MembersAdapter`'s make-leader overflow action. Flips
  /// `isLeader` on every membership (true for the new leader, false for the
  /// rest) and enqueues each changed row for upload.
  Future<bool> makeLeader(String teamId, String newLeaderId) async {
    // See [leaveFromMembers] for why these three carry a `try`.
    try {
      return await _promoteLeader(teamId, newLeaderId);
    } catch (_) {
      return false;
    }
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
    final user = await resolveSession(ref);
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

  /// **The user is resolved, and its absence refuses the whole operation.**
  /// Both halves are parity, not caution. `addResourceLinks`
  /// (`TeamsRepositoryImpl.kt:664-690`) opens with
  /// `val user = userRepository.getUserById(userId) ?: return` — with no user
  /// it creates **no row at all**, rather than one with null planet codes.
  ///
  /// The port did the opposite twice over. `ref.read(sessionProvider)
  /// .value?.planetCode` is the standing *a provider a screen reads but never
  /// watches is null* trap — `TeamResourceActions` is a plain `Provider`, so
  /// its `ref` watches nothing, and neither `team_resources_screen` nor
  /// `team_detail_screen` watches `sessionProvider` either, so it was latent
  /// only because the router holds a `ref.listen`. On any pass where it was
  /// not yet resolved the link was created anyway with `teamPlanetCode` and
  /// `userPlanetCode` null, serialized, and uploaded: a server document
  /// Kotlin would never have written.
  ///
  /// [TeamsRepository.addResourceLink]'s own comment records the prefs
  /// fallback question and answers it for the *other* producer:
  /// `createLocalResourceLink` (`:709-710`) falls back to
  /// `sharedPrefManager.getPlanetCode()`, and `addResourceLinks` — which is
  /// what this method calls — does not. So resolving the session is the whole
  /// fix here; there is no fallback to port.
  Future<bool> add(String teamId, MyLibraryRow resource) async {
    final config = ref.read(serverConfigProvider);
    if (config == null || resource.resourceId?.isNotEmpty != true) return false;
    final user = await resolveSession(ref);
    if (user == null) return false;
    final row = await ref
        .read(teamsRepositoryProvider)
        .addResourceLink(
          teamId: teamId,
          resourceId: resource.resourceId!,
          title: resource.title ?? '',
          planetCode: user.planetCode,
        );
    if (row == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamResource',
          itemId: row.id,
          endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
          payload: TeamsRepository.serializeTeamDocument(row),
          userId: user.id,
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
            userId: (await resolveSessionUserId(ref)),
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
          userId: await resolveSessionUserId(ref),
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
          userId: await resolveSessionUserId(ref),
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
  final userId = ref.watch(sessionProvider).value?.id;
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
