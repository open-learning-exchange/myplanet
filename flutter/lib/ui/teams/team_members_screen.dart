import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/teams_provider.dart';
import '../components/profile_avatar.dart';
import '../router.dart';

class TeamMembersScreen extends ConsumerWidget {
  const TeamMembersScreen({
    required this.teamId,
    this.openJoinRequests = false,
    super.key,
  });
  final String teamId;
  final bool openJoinRequests;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final members = ref.watch(teamMembersProvider(teamId));
    final requests = ref.watch(teamRequestsProvider(teamId));
    final memberships = ref.watch(teamMembershipsProvider).value ?? const {};
    final canManage = (memberships[teamId]?.isLeader ?? false);
    return DefaultTabController(
      length: canManage ? 2 : 1,
      initialIndex: canManage && openJoinRequests ? 1 : 0,
      child: Scaffold(
        appBar: AppBar(
          title: Text(l10n.teamMembers),
          bottom: TabBar(
            tabs: [
              Tab(text: l10n.members),
              if (canManage) Tab(text: l10n.joinRequests),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            _MembersList(
              teamId: teamId,
              rows: members,
              emptyText: l10n.noMembers,
              canManage: canManage,
            ),
            if (canManage) _RequestsList(teamId: teamId, rows: requests),
          ],
        ),
      ),
    );
  }
}

class _MembersList extends ConsumerWidget {
  const _MembersList({
    required this.teamId,
    required this.rows,
    required this.emptyText,
    required this.canManage,
  });
  final String teamId;
  final AsyncValue<List<TeamRow>> rows;
  final String emptyText;
  final bool canManage;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return rows.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (_, _) => Center(child: Text(l10n.membersUnavailable)),
      data: (items) => items.isEmpty
          ? Center(child: Text(emptyText))
          : ListView.builder(
              itemCount: items.length,
              itemBuilder: (context, index) {
                final row = items[index];
                final userId = row.userId ?? '';
                // Resolve the member's display name from the cached `users`
                // row. Falls back to the raw id exactly as the Kotlin's
                // `MembersAdapter` falls back to the username when no full
                // name is set.
                final user = userId.isEmpty
                    ? null
                    : ref.watch(userByIdProvider(userId)).value;
                // `profile_avatar.dart` is the port's single source for a
                // user's name and initial. The local copies this replaces
                // were the third in this directory and broken the same two
                // ways the leaderboard's were: no middle name, and no trim —
                // so a synced `"name": " jane "` rendered untrimmed behind a
                // blank avatar, because `' '` is not empty and the
                // `isEmpty ? '?'` guard never fired.
                final name = user == null ? userId : displayName(user);
                final initial = initialFor(name);
                final currentUserId = ref.watch(sessionProvider).value?.id;
                final isOwnCard = userId == currentUserId;
                return ListTile(
                  leading: CircleAvatar(child: Text(initial)),
                  title: Text(name.isEmpty ? l10n.unknownMember : name),
                  subtitle: row.isLeader ? Text(l10n.leader) : null,
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (row.isLeader) const Icon(Icons.star),
                      // `MembersAdapter.checkUserAndShowOverflowMenu`:
                      // `(isLoggedInUserTeamLeader || isOwnCard)
                      //   && itemCount > 1`.
                      //
                      // **The `items.length > 1` half is what actually keeps a
                      // team from being left leaderless**, and the port had
                      // neither it nor the succession behind it. Kotlin's
                      // leave path promotes a successor when one exists and
                      // then removes the member regardless
                      // (`RequestsViewModel.kt:81-83`), so nothing downstream
                      // refuses — the sole member is simply never offered the
                      // action. Dropping this clause would put the hole back
                      // whatever `leaveFromMembers` does.
                      //
                      // **`items.length` is not quite Kotlin's `itemCount`,
                      // and the difference runs in the port's favour.**
                      // Kotlin counts `getJoinedMembersWithVisitInfo`, which
                      // drops memberships whose user row is missing locally
                      // *and* appends community-leader admins who merely
                      // authored some document for this team
                      // (`TeamsRepositoryImpl:967-985`) — so a Kotlin team can
                      // pass `> 1` on the strength of somebody who is not a
                      // member and can never be promoted. `items` here is the
                      // raw `membership` rows, which is the population the
                      // succession actually draws from.
                      if ((canManage || isOwnCard) && items.length > 1)
                        PopupMenuButton<String>(
                          icon: const Icon(Icons.more_vert),
                          onSelected: (value) => _handleMemberAction(
                            context,
                            ref,
                            value,
                            teamId,
                            userId,
                          ),
                          itemBuilder: (context) => [
                            if (isOwnCard)
                              PopupMenuItem(
                                value: 'leave',
                                child: Text(l10n.leave),
                              )
                            else ...[
                              PopupMenuItem(
                                value: 'remove',
                                child: Text(l10n.remove),
                              ),
                              PopupMenuItem(
                                value: 'make_leader',
                                child: Text(l10n.makeLeader),
                              ),
                            ],
                          ],
                        ),
                    ],
                  ),
                  onTap: userId.isEmpty
                      ? null
                      : () => context.push(
                          '${Routes.teams}/$teamId/members/$userId',
                        ),
                );
              },
            ),
    );
  }

  Future<void> _handleMemberAction(
    BuildContext context,
    WidgetRef ref,
    String action,
    String teamId,
    String userId,
  ) async {
    final l10n = AppLocalizations.of(context);
    final actions = ref.read(teamMembershipActionsProvider);
    final messenger = ScaffoldMessenger.of(context);
    String message;
    switch (action) {
      case 'leave':
        // `MembersFragment.handleLeaveTeam` puts this behind a `confirm_exit`
        // Yes/No dialog before it reaches the ViewModel, as the detail
        // screen's leave already does in this port.
        if (!await _confirmLeave(context)) return;
        if (!context.mounted) return;
        // `leaveFromMembers`, not `leave`: this is the path that promotes a
        // successor. See the two-leave-paths note on it.
        final outcome = await actions.leaveFromMembers(teamId);
        message = _messageFor(l10n, outcome, l10n.leftTeam);
        if (outcome == MemberActionOutcome.succeeded) {
          // `MembersFragment:100-103` toasts and then
          // `requireActivity().supportFragmentManager.popBackStack()`. The
          // port stayed put, leaving the user looking at the member list of a
          // team they had just left — a list their own row had vanished from,
          // with the manage tab gone if they had been the leader.
          if (!context.mounted) return;
          messenger.showSnackBar(SnackBar(content: Text(message)));
          if (context.canPop()) context.pop();
          return;
        }
      case 'remove':
        final outcome = await actions.removeMember(teamId, userId);
        message = _messageFor(l10n, outcome, l10n.memberRemoved);
      case 'make_leader':
        final success = await actions.makeLeader(teamId, userId);
        message = success ? l10n.leaderUpdated : l10n.operationFailed;
      default:
        return;
    }
    if (!context.mounted) return;
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }

  /// `MemberActionResult` → the toast `MembersFragment` shows for it
  /// (`:100-113`). The refusal gets `cannot_remove_user`, not the generic
  /// error string, which is the whole reason the outcome is an enum.
  String _messageFor(
    AppLocalizations l10n,
    MemberActionOutcome outcome,
    String successMessage,
  ) => switch (outcome) {
    MemberActionOutcome.succeeded => successMessage,
    MemberActionOutcome.cannotRemoveLastLeader => l10n.cannotRemoveUser,
    MemberActionOutcome.failed => l10n.operationFailed,
  };

  Future<bool> _confirmLeave(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        content: Text(l10n.confirmLeaveTeam),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(l10n.no),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.yes),
          ),
        ],
      ),
    );
    return confirmed == true;
  }
}

class _RequestsList extends ConsumerWidget {
  const _RequestsList({required this.teamId, required this.rows});
  final String teamId;
  final AsyncValue<List<TeamRow>> rows;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return rows.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (_, _) => Center(child: Text(l10n.membersUnavailable)),
      data: (items) => items.isEmpty
          ? Center(child: Text(l10n.noJoinRequests))
          : ListView.builder(
              itemCount: items.length,
              itemBuilder: (context, index) {
                final row = items[index];
                return ListTile(
                  leading: const CircleAvatar(
                    child: Icon(Icons.person_add_alt),
                  ),
                  title: Text(row.userId ?? l10n.unknownMember),
                  trailing: Wrap(
                    children: [
                      IconButton(
                        tooltip: l10n.decline,
                        icon: const Icon(Icons.close),
                        onPressed: () => ref
                            .read(teamMembershipActionsProvider)
                            .respond(row.id, accept: false),
                      ),
                      IconButton(
                        tooltip: l10n.accept,
                        icon: const Icon(Icons.check),
                        onPressed: () => ref
                            .read(teamMembershipActionsProvider)
                            .respond(row.id, accept: true),
                      ),
                    ],
                  ),
                );
              },
            ),
    );
  }
}
