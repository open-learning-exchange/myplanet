import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/courses_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/teams_provider.dart';

class TeamCoursesScreen extends ConsumerWidget {
  const TeamCoursesScreen({required this.teamId, super.key});
  final String teamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final courses = ref.watch(teamCoursesProvider(teamId));
    final membership = ref.watch(teamMembershipsProvider).valueOrNull?[teamId];
    // Kotlin's add has no gate in `TeamCoursesFragment` at all — it is driven
    // by `btnAddDoc`, which `setupMyTeamButtons` shows to any **member**
    // (`TeamDetailFragment.kt:286-289`).
    final canAdd = membership != null;
    // `canRemove = currentUserId.equals(teamCreator, ignoreCase = true)`
    // (`TeamCoursesFragment.kt:44-46`), where `getTeamCreator` is the team
    // row's `userId` (`TeamsRepositoryImpl.kt:1120-1123`) — the **creator**,
    // not the leader. The port gated it on `isLeader`, which both offered the
    // unlink to a leader who did not create the team and withheld it from a
    // creator who is not the leader.
    final creatorId = ref.watch(teamProvider(teamId)).valueOrNull?.userId;
    final currentUserId = ref.watch(sessionProvider).valueOrNull?.id;
    // Both sides must be non-empty as well as non-null. Kotlin's
    // `sharedPrefManager.getUserId().ifEmpty { "--" }` sentinel exists to
    // stop an empty id matching an empty creator; without it a team document
    // with no `userId` would hand the unlink to a session with no id.
    final canRemove =
        creatorId != null &&
        creatorId.isNotEmpty &&
        currentUserId != null &&
        currentUserId.isNotEmpty &&
        creatorId.toLowerCase() == currentUserId.toLowerCase();
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
                    trailing: canRemove
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
      floatingActionButton: canAdd
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
