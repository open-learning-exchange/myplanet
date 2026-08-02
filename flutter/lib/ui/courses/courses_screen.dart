import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/courses_providers.dart';
import '../../providers/sync_state.dart';
import '../dashboard/dashboard_shell.dart';
import '../router.dart';

/// Port of `ui/courses/CoursesFragment.kt`.
///
/// Keeps the Kotlin's three filters — free-text search, grade level, subject
/// level — plus the my-courses/all-courses toggle, all applied in SQL rather
/// than by filtering an in-memory list as `BaseRecyclerFragment` does.
class CoursesScreen extends ConsumerStatefulWidget {
  const CoursesScreen({super.key});

  @override
  ConsumerState<CoursesScreen> createState() => _CoursesScreenState();
}

class _CoursesScreenState extends ConsumerState<CoursesScreen> {
  final _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final courses = ref.watch(coursesStreamProvider);
    final syncState = ref.watch(courseSyncProvider);

    ref.listen<SyncUiState>(courseSyncProvider, (previous, next) {
      final messenger = ScaffoldMessenger.of(context);
      switch (next) {
        case SyncSucceeded(:final savedCount):
          messenger.showSnackBar(
            SnackBar(content: Text(l10n.syncedCourses(savedCount))),
          );
        case SyncErrored(:final message):
          messenger.showSnackBar(
            SnackBar(content: Text(l10n.syncFailed(message))),
          );
        case SyncIdle():
        case SyncRunning():
          break;
      }
    });

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.courses),
        actions: [
          IconButton(
            tooltip: l10n.sync,
            onPressed: syncState is SyncRunning
                ? null
                : () => ref.read(courseSyncProvider.notifier).sync(),
            icon: const Icon(Icons.sync),
          ),
          const LogoutAction(),
        ],
        bottom: PreferredSize(
          preferredSize: Size.fromHeight(syncState is SyncRunning ? 116 : 112),
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: SearchBar(
                  controller: _searchController,
                  hintText: l10n.search,
                  leading: const Icon(Icons.search),
                  onChanged: (value) =>
                      ref.read(courseFilterProvider.notifier).setQuery(value),
                ),
              ),
              _CourseFilterBar(onCleared: _searchController.clear),
              if (syncState is SyncRunning)
                LinearProgressIndicator(
                  value: syncState.progress.total == 0
                      ? null
                      : syncState.progress.fraction,
                ),
            ],
          ),
        ),
      ),
      body: courses.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(l10n.syncFailed('$error'))),
        data: (items) {
          if (items.isEmpty) {
            return Center(child: Text(l10n.noDataAvailable));
          }
          return ListView.separated(
            itemCount: items.length,
            separatorBuilder: (_, _) => const Divider(height: 1),
            itemBuilder: (context, index) => _CourseTile(items[index]),
          );
        },
      ),
    );
  }
}

class _CourseFilterBar extends ConsumerWidget {
  const _CourseFilterBar({required this.onCleared});

  /// Clears the search field, which lives in the parent's state.
  final VoidCallback onCleared;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final filter = ref.watch(courseFilterProvider);
    final notifier = ref.read(courseFilterProvider.notifier);
    final grades = ref.watch(gradeLevelsProvider).valueOrNull ?? const [];
    final subjects = ref.watch(subjectLevelsProvider).valueOrNull ?? const [];

    return SizedBox(
      height: 48,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        children: [
          FilterChip(
            label: Text(l10n.myCourses),
            selected: filter.myCoursesOnly,
            onSelected: notifier.setMyCoursesOnly,
          ),
          const SizedBox(width: 8),
          _LevelDropdown(
            label: l10n.gradeLevel,
            value: filter.gradeLevel,
            options: grades,
            allLabel: l10n.allLevels,
            onChanged: notifier.setGradeLevel,
          ),
          const SizedBox(width: 8),
          _LevelDropdown(
            label: l10n.subjectLevel,
            value: filter.subjectLevel,
            options: subjects,
            allLabel: l10n.allLevels,
            onChanged: notifier.setSubjectLevel,
          ),
          if (filter.gradeLevel != null ||
              filter.subjectLevel != null ||
              filter.myCoursesOnly ||
              filter.query.isNotEmpty) ...[
            const SizedBox(width: 8),
            ActionChip(
              avatar: const Icon(Icons.clear, size: 18),
              label: Text(l10n.clearFilters),
              onPressed: () {
                notifier.clear();
                onCleared();
              },
            ),
          ],
        ],
      ),
    );
  }
}

/// Replaces the `CustomSpinner` the Kotlin filter row uses.
class _LevelDropdown extends StatelessWidget {
  const _LevelDropdown({
    required this.label,
    required this.value,
    required this.options,
    required this.allLabel,
    required this.onChanged,
  });

  final String label;
  final String? value;
  final List<String> options;
  final String allLabel;
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: DropdownButton<String?>(
        // Belt and braces: a selection that is no longer among the options
        // would otherwise assert. Can happen transiently while options reload.
        value: options.contains(value) ? value : null,
        hint: Text(label),
        borderRadius: BorderRadius.circular(8),
        items: [
          DropdownMenuItem<String?>(child: Text(allLabel)),
          ...options.map(
            (option) =>
                DropdownMenuItem<String?>(value: option, child: Text(option)),
          ),
        ],
        onChanged: onChanged,
      ),
    );
  }
}

class _CourseTile extends StatelessWidget {
  const _CourseTile(this.course);

  final CourseRow course;

  @override
  Widget build(BuildContext context) {
    final subtitleParts = [
      if (course.gradeLevel != null && course.gradeLevel!.isNotEmpty)
        course.gradeLevel!,
      if (course.subjectLevel != null && course.subjectLevel!.isNotEmpty)
        course.subjectLevel!,
    ];

    return ListTile(
      leading: const Icon(Icons.school_outlined),
      title: Text(
        course.courseTitle ?? '',
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: subtitleParts.isEmpty
          ? null
          : Text(
              subtitleParts.join(' · '),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
      trailing: const Icon(Icons.chevron_right),
      onTap: () =>
          context.go('${Routes.courses}/${Uri.encodeComponent(course.id)}'),
    );
  }
}
