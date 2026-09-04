import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/resources_providers.dart';
import '../../providers/teams_provider.dart';

class TeamResourcesScreen extends ConsumerWidget {
  const TeamResourcesScreen({required this.teamId, super.key});
  final String teamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final resources = ref.watch(teamResourcesProvider(teamId));
    final membership = ref.watch(teamMembershipsProvider).valueOrNull?[teamId];
    // Two different gates, as `TeamResourcesFragment` has: the add FAB is
    // `isVisible = isMember` (`:51-52`, `isMemberFlow` → plain membership),
    // while remove is `isTeamLeader` (`:73`). One `isLeader` flag drove both
    // here, so an ordinary member could not link a resource Kotlin lets them
    // link — the Phase 99 shape.
    final canAdd = membership != null;
    final canRemove = membership?.isLeader ?? false;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.teamResources)),
      body: resources.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.resourcesUnavailable)),
        data: (rows) => rows.isEmpty
            ? Center(child: Text(l10n.noTeamResources))
            : ListView.separated(
                padding: const EdgeInsets.all(12),
                itemCount: rows.length,
                separatorBuilder: (_, _) => const SizedBox(height: 6),
                itemBuilder: (context, index) {
                  final row = rows[index];
                  return Card(
                    child: ListTile(
                      leading: const Icon(Icons.description_outlined),
                      title: Text(row.title ?? l10n.untitledResource),
                      subtitle: Text(row.description ?? ''),
                      trailing: canRemove
                          ? IconButton(
                              tooltip: l10n.removeFromTeam,
                              icon: const Icon(Icons.link_off),
                              onPressed: () => ref
                                  .read(teamResourceActionsProvider)
                                  .remove(teamId, row),
                            )
                          : null,
                    ),
                  );
                },
              ),
      ),
      floatingActionButton: canAdd
          ? FloatingActionButton.extended(
              onPressed: () => _chooseResource(context, ref),
              icon: const Icon(Icons.add_link),
              label: Text(l10n.addResource),
            )
          : null,
    );
  }

  Future<void> _chooseResource(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final linked =
        ref.read(teamResourcesProvider(teamId)).valueOrNull ?? const [];
    final linkedIds = linked.map((row) => row.resourceId).toSet();
    final all = ref.read(resourcesStreamProvider).valueOrNull ?? const [];
    final available = all
        .where((row) => !linkedIds.contains(row.resourceId))
        .toList();
    final selected = await showDialog<MyLibraryRow>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.addResource),
        content: SizedBox(
          width: 480,
          height: 420,
          child: available.isEmpty
              ? Center(child: Text(l10n.noResourcesToAdd))
              : ListView.builder(
                  itemCount: available.length,
                  itemBuilder: (context, index) => ListTile(
                    leading: const Icon(Icons.description_outlined),
                    title: Text(
                      available[index].title ?? l10n.untitledResource,
                    ),
                    onTap: () => Navigator.pop(context, available[index]),
                  ),
                ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l10n.cancel),
          ),
        ],
      ),
    );
    if (selected != null) {
      await ref.read(teamResourceActionsProvider).add(teamId, selected);
    }
  }
}
