import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/teams_provider.dart';

/// Port of `ui/teams/PlanFragment.kt`.
///
/// Displays and allows editing of team/enterprise mission, services, and rules.
class TeamPlanScreen extends ConsumerWidget {
  const TeamPlanScreen({required this.teamId, super.key});
  final String teamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final team = ref.watch(teamProvider(teamId));
    final memberships =
        ref.watch(teamMembershipsProvider).valueOrNull ?? const {};

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plan)),
      body: team.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.teamsUnavailable)),
        data: (row) {
          if (row == null) return Center(child: Text(l10n.teamNotFound));

          final isEnterprise = row.type == 'enterprise';

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              if (row.description?.isNotEmpty == true ||
                  row.services?.isNotEmpty == true ||
                  row.rules?.isNotEmpty == true) ...[
                if (row.description?.isNotEmpty == true)
                  _Section(
                    title: isEnterprise ? l10n.mission : l10n.teamPlan,
                    content: row.description!,
                  ),
                if (isEnterprise && row.services?.isNotEmpty == true)
                  _Section(title: l10n.services, content: row.services!),
                if (isEnterprise && row.rules?.isNotEmpty == true)
                  _Section(title: l10n.rules, content: row.rules!),
              ] else
                Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Text(
                      isEnterprise ? l10n.noMissionDefined : l10n.noPlanDefined,
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                  ),
                ),
              const SizedBox(height: 16),
              Text(
                '${l10n.createdOn}: ${_formatDate(row.createdDate)}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          );
        },
      ),
      floatingActionButton: team.maybeWhen(
        data: (row) {
          if (row == null) return null;
          final membership = memberships[teamId];
          if (membership?.isLeader != true) return null;
          return FloatingActionButton.extended(
            onPressed: () => _showEditDialog(context, ref, row),
            icon: const Icon(Icons.edit),
            label: Text(l10n.editPlan),
          );
        },
        orElse: () => null,
      ),
    );
  }

  String _formatDate(int millis) {
    if (millis == 0) return '';
    final date = DateTime.fromMillisecondsSinceEpoch(millis);
    return '${date.day}/${date.month}/${date.year}';
  }

  Future<void> _showEditDialog(
    BuildContext context,
    WidgetRef ref,
    dynamic row,
  ) async {
    final l10n = AppLocalizations.of(context);
    final isEnterprise = row.type == 'enterprise';

    final nameController = TextEditingController(text: row.name ?? '');
    final descriptionController = TextEditingController(
      text: row.description ?? '',
    );
    final servicesController = TextEditingController(text: row.services ?? '');
    final rulesController = TextEditingController(text: row.rules ?? '');
    String teamType = row.teamType ?? 'local';
    bool isPublic = row.isPublic;

    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: Text(l10n.editPlan),
          content: SizedBox(
            width: 500,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: nameController,
                    decoration: InputDecoration(
                      labelText: isEnterprise
                          ? l10n.enterpriseName
                          : l10n.teamName,
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: descriptionController,
                    decoration: InputDecoration(
                      labelText: isEnterprise ? l10n.mission : l10n.teamPlan,
                    ),
                    maxLines: 3,
                  ),
                  if (isEnterprise) ...[
                    const SizedBox(height: 16),
                    TextField(
                      controller: servicesController,
                      decoration: InputDecoration(labelText: l10n.services),
                      maxLines: 2,
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: rulesController,
                      decoration: InputDecoration(labelText: l10n.rules),
                      maxLines: 3,
                    ),
                    const SizedBox(height: 16),
                    DropdownButtonFormField<String>(
                      initialValue: teamType,
                      decoration: InputDecoration(labelText: l10n.teamType),
                      items: [
                        DropdownMenuItem(
                          value: 'local',
                          child: Text(l10n.local),
                        ),
                        DropdownMenuItem(value: 'sync', child: Text(l10n.sync)),
                      ],
                      onChanged: (value) =>
                          setState(() => teamType = value ?? 'local'),
                    ),
                  ],
                  const SizedBox(height: 16),
                  SwitchListTile(
                    title: Text(l10n.isPublic),
                    value: isPublic,
                    onChanged: (value) => setState(() => isPublic = value),
                  ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: Text(l10n.cancel),
            ),
            FilledButton(
              onPressed: () async {
                if (nameController.text.trim().isEmpty) {
                  ScaffoldMessenger.of(
                    context,
                  ).showSnackBar(SnackBar(content: Text(l10n.nameRequired)));
                  return;
                }
                await ref
                    .read(teamsRepositoryProvider)
                    .updateTeam(
                      teamId: teamId,
                      name: nameController.text.trim(),
                      description: descriptionController.text.trim(),
                      services: isEnterprise
                          ? servicesController.text.trim()
                          : null,
                      rules: isEnterprise ? rulesController.text.trim() : null,
                      teamType: isEnterprise ? teamType : null,
                      isPublic: isPublic,
                    );
                if (dialogContext.mounted) Navigator.pop(dialogContext);
              },
              child: Text(l10n.save),
            ),
          ],
        ),
      ),
    );

    nameController.dispose();
    descriptionController.dispose();
    servicesController.dispose();
    rulesController.dispose();
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.content});
  final String title;
  final String content;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(content),
        ],
      ),
    );
  }
}
