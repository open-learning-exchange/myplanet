import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/teams_provider.dart';
import '../../data/local/app_database.dart';

class TeamMembersScreen extends ConsumerWidget {
  const TeamMembersScreen({required this.teamId, super.key});
  final String teamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final members = ref.watch(teamMembersProvider(teamId));
    final requests = ref.watch(teamRequestsProvider(teamId));
    final memberships =
        ref.watch(teamMembershipsProvider).valueOrNull ?? const {};
    final canManage = (memberships[teamId]?.isLeader ?? false);
    return DefaultTabController(
      length: canManage ? 2 : 1,
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
            _MembersList(rows: members, emptyText: l10n.noMembers),
            if (canManage) _RequestsList(teamId: teamId, rows: requests),
          ],
        ),
      ),
    );
  }
}

class _MembersList extends StatelessWidget {
  const _MembersList({required this.rows, required this.emptyText});
  final AsyncValue<List<TeamRow>> rows;
  final String emptyText;
  @override
  Widget build(BuildContext context) => rows.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(
            child: Text(AppLocalizations.of(context).membersUnavailable)),
        data: (items) => items.isEmpty
            ? Center(child: Text(emptyText))
            : ListView.builder(
                itemCount: items.length,
                itemBuilder: (context, index) {
                  final row = items[index];
                  return ListTile(
                    leading: CircleAvatar(
                      child: Text(
                        (row.userId ?? '?').characters.first.toUpperCase(),
                      ),
                    ),
                    title: Text(
                      row.userId ?? AppLocalizations.of(context).unknownMember,
                    ),
                    subtitle: row.isLeader
                        ? Text(AppLocalizations.of(context).leader)
                        : null,
                    trailing: row.isLeader ? const Icon(Icons.star) : null,
                  );
                },
              ),
      );
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
