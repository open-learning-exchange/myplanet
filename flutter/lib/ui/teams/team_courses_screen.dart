import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/courses_providers.dart';
import '../../providers/teams_provider.dart';

class TeamCoursesScreen extends ConsumerWidget {
  const TeamCoursesScreen({required this.teamId, super.key});
  final String teamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final courses = ref.watch(teamCoursesProvider(teamId));
    final canManage =
        ref.watch(teamMembershipsProvider).valueOrNull?[teamId]?.isLeader ??
        false;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.teamCourses)),
      body: courses.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.coursesUnavailable)),
        data: (rows) => rows.isEmpty
            ? Center(child: Text(l10n.noTeamCourses))
            : ListView.separated(
                padding: const EdgeInsets.all(12),
                itemCount: rows.length,
                separatorBuilder: (_, _) => const SizedBox(height: 6),
                itemBuilder: (context, index) => Card(
                  child: ListTile(
                    leading: const Icon(Icons.school_outlined),
                    title: Text(rows[index].courseTitle ?? l10n.untitledCourse),
                    subtitle: Text(rows[index].description ?? ''),
                    trailing: canManage
                        ? IconButton(
                            tooltip: l10n.removeFromTeam,
                            icon: const Icon(Icons.link_off),
                            onPressed: () => ref
                                .read(teamCourseActionsProvider)
                                .remove(teamId, rows[index].id),
                          )
                        : null,
                  ),
                ),
              ),
      ),
      floatingActionButton: canManage
          ? FloatingActionButton.extended(
              onPressed: () => _chooseCourse(context, ref),
              icon: const Icon(Icons.playlist_add),
              label: Text(l10n.addCourse),
            )
          : null,
    );
  }

  Future<void> _chooseCourse(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final linked =
        ref.read(teamCoursesProvider(teamId)).valueOrNull ??
        const <CourseRow>[];
    final linkedIds = linked.map((row) => row.id).toSet();
    final all =
        ref.read(coursesStreamProvider).valueOrNull ?? const <CourseRow>[];
    final available = all.where((row) => !linkedIds.contains(row.id)).toList();
    final selected = await showDialog<CourseRow>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.addCourse),
        content: SizedBox(
          width: 480,
          height: 420,
          child: available.isEmpty
              ? Center(child: Text(l10n.noCoursesToAdd))
              : ListView.builder(
                  itemCount: available.length,
                  itemBuilder: (context, index) => ListTile(
                    leading: const Icon(Icons.school_outlined),
                    title: Text(
                      available[index].courseTitle ?? l10n.untitledCourse,
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
      await ref.read(teamCourseActionsProvider).add(teamId, [selected.id]);
    }
  }
}
