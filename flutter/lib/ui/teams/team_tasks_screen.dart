import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/team_tasks_provider.dart';
import 'inline_comments.dart';

class TeamTasksScreen extends ConsumerWidget {
  const TeamTasksScreen({required this.teamId, super.key});
  final String teamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final tasks = ref.watch(teamTasksProvider(teamId));
    return Scaffold(
      appBar: AppBar(title: Text(l10n.teamTasks)),
      body: tasks.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.tasksUnavailable)),
        data: (rows) => rows.isEmpty
            ? Center(child: Text(l10n.noTeamTasks))
            : ListView.separated(
                padding: const EdgeInsets.all(12),
                itemCount: rows.length,
                separatorBuilder: (_, _) => const SizedBox(height: 6),
                itemBuilder: (context, index) {
                  final task = rows[index];
                  final due = task.deadline == 0
                      ? l10n.noDeadline
                      : MaterialLocalizations.of(context).formatMediumDate(
                          DateTime.fromMillisecondsSinceEpoch(task.deadline),
                        );
                  return Column(
                    children: [
                      Card(
                        child: CheckboxListTile(
                          value: task.completed,
                          onChanged: (value) => ref
                              .read(teamTaskActionsProvider)
                              .complete(task.id, value ?? false),
                          title: Text(
                            task.title ?? l10n.untitledTask,
                            style: task.completed
                                ? const TextStyle(
                                    decoration: TextDecoration.lineThrough,
                                  )
                                : null,
                          ),
                          subtitle: Text(
                            [
                              due,
                              task.assignee,
                            ].where((v) => v?.isNotEmpty == true).join(' · '),
                          ),
                          secondary: PopupMenuButton<String>(
                            onSelected: (value) async {
                              if (value == 'edit') {
                                await _showTaskDialog(
                                  context,
                                  ref,
                                  teamId,
                                  task: task,
                                );
                              }
                              if (value == 'delete') {
                                await ref
                                    .read(teamTaskActionsProvider)
                                    .delete(task.id);
                              }
                            },
                            itemBuilder: (_) => [
                              PopupMenuItem(
                                value: 'edit',
                                child: Text(l10n.editTask),
                              ),
                              PopupMenuItem(
                                value: 'delete',
                                child: Text(l10n.deleteTask),
                              ),
                            ],
                          ),
                        ),
                      ),
                      InlineComments(parentId: task.id, teamId: teamId),
                    ],
                  );
                },
              ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showTaskDialog(context, ref, teamId),
        icon: const Icon(Icons.add_task),
        label: Text(l10n.addTask),
      ),
    );
  }
}

Future<void> _showTaskDialog(
  BuildContext context,
  WidgetRef ref,
  String teamId, {
  TeamTaskRow? task,
}) async {
  final l10n = AppLocalizations.of(context);
  final title = TextEditingController(text: task?.title);
  final description = TextEditingController(text: task?.description);
  final assignee = TextEditingController(text: task?.assignee);
  var deadline = task?.deadline ?? 0;
  await showDialog<void>(
    context: context,
    builder: (dialogContext) => StatefulBuilder(
      builder: (context, setState) => AlertDialog(
        title: Text(task == null ? l10n.addTask : l10n.editTask),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: title,
                autofocus: true,
                decoration: InputDecoration(labelText: l10n.taskTitle),
              ),
              TextField(
                controller: description,
                decoration: InputDecoration(labelText: l10n.description),
                maxLines: 3,
              ),
              TextField(
                controller: assignee,
                decoration: InputDecoration(labelText: l10n.assigneeId),
              ),
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(
                  deadline == 0
                      ? l10n.noDeadline
                      : MaterialLocalizations.of(context).formatMediumDate(
                          DateTime.fromMillisecondsSinceEpoch(deadline),
                        ),
                ),
                trailing: const Icon(Icons.calendar_month),
                onTap: () async {
                  final picked = await showDatePicker(
                    context: context,
                    firstDate: DateTime.now().subtract(const Duration(days: 1)),
                    lastDate: DateTime.now().add(const Duration(days: 3650)),
                    initialDate: deadline == 0
                        ? DateTime.now()
                        : DateTime.fromMillisecondsSinceEpoch(deadline),
                  );
                  if (picked != null) {
                    setState(() => deadline = picked.millisecondsSinceEpoch);
                  }
                },
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () async {
              final ok = await ref
                  .read(teamTaskActionsProvider)
                  .save(
                    id: task?.id,
                    teamId: teamId,
                    title: title.text,
                    description: description.text,
                    deadline: deadline,
                    assignee: assignee.text,
                  );
              if (ok && dialogContext.mounted) Navigator.pop(dialogContext);
            },
            child: Text(l10n.save),
          ),
        ],
      ),
    ),
  );
  title.dispose();
  description.dispose();
  assignee.dispose();
}
