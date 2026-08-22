import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/courses_providers.dart';
import '../../providers/sync_state.dart';
import '../../providers/view_mode_providers.dart';
import '../components/grid_span_calculator.dart';
import '../components/list_view_mode.dart';
import '../components/view_mode_toggle.dart';
import '../dashboard/dashboard_shell.dart';
import '../router.dart';
import 'course_subject.dart';

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
    final courses = ref.watch(filteredSortedCoursesProvider);
    final syncState = ref.watch(courseSyncProvider);
    final viewMode = ref.watch(courseViewModeProvider);

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
          ViewModeToggle(
            mode: viewMode,
            onChanged: ref.read(courseViewModeProvider.notifier).set,
          ),
          IconButton(
            tooltip: l10n.myProgress,
            onPressed: () => context.push('${Routes.courses}/progress'),
            icon: const Icon(Icons.analytics_outlined),
          ),
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
          if (viewMode == ListViewMode.grid) {
            return LayoutBuilder(
              builder: (context, constraints) {
                final spanCount = GridSpanCalculator.columnCount(
                  constraints.maxWidth / MediaQuery.devicePixelRatioOf(context),
                );
                return GridView.builder(
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: spanCount,
                    childAspectRatio: 0.85,
                    crossAxisSpacing: 8,
                    mainAxisSpacing: 8,
                  ),
                  padding: const EdgeInsets.all(8),
                  itemCount: items.length,
                  itemBuilder: (context, index) =>
                      _CourseGridTile(items[index]),
                );
              },
            );
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
    final sort = ref.watch(courseSortProvider);
    final progressFilter = ref.watch(courseProgressFilterProvider);

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
          const SizedBox(width: 8),
          _ProgressFilterDropdown(
            value: progressFilter,
            onChanged: ref.read(courseProgressFilterProvider.notifier).set,
          ),
          const SizedBox(width: 8),
          _SortButton(
            label: l10n.orderByDate,
            active: sort.field == CourseSortField.date,
            ascending: sort.field == CourseSortField.date
                ? sort.dateAscending
                : null,
            onTap: ref.read(courseSortProvider.notifier).toggleDate,
          ),
          const SizedBox(width: 8),
          _SortButton(
            label: l10n.orderByTitle,
            active: sort.field == CourseSortField.title,
            ascending: sort.field == CourseSortField.title
                ? sort.titleAscending
                : null,
            onTap: ref.read(courseSortProvider.notifier).toggleTitle,
          ),
          if (filter.gradeLevel != null ||
              filter.subjectLevel != null ||
              filter.myCoursesOnly ||
              filter.query.isNotEmpty ||
              progressFilter != CourseProgressFilter.all ||
              sort.field != null) ...[
            const SizedBox(width: 8),
            ActionChip(
              avatar: const Icon(Icons.clear, size: 18),
              label: Text(l10n.clearFilters),
              onPressed: () {
                notifier.clear();
                ref.read(courseProgressFilterProvider.notifier).clear();
                ref.read(courseSortProvider.notifier).clear();
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

/// The progress filter spinner — `CourseFilterController`'s `spnProgress`,
/// whose items are the `progress_filter` array (All / Not Started / In
/// Progress / Completed).
class _ProgressFilterDropdown extends StatelessWidget {
  const _ProgressFilterDropdown({required this.value, required this.onChanged});

  final CourseProgressFilter value;
  final ValueChanged<CourseProgressFilter> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Center(
      child: DropdownButton<CourseProgressFilter>(
        value: value,
        hint: Text(l10n.progress),
        borderRadius: BorderRadius.circular(8),
        items: [
          DropdownMenuItem(
            value: CourseProgressFilter.all,
            child: Text(l10n.progressFilterAll),
          ),
          DropdownMenuItem(
            value: CourseProgressFilter.notStarted,
            child: Text(l10n.progressFilterNotStarted),
          ),
          DropdownMenuItem(
            value: CourseProgressFilter.inProgress,
            child: Text(l10n.progressFilterInProgress),
          ),
          DropdownMenuItem(
            value: CourseProgressFilter.completed,
            child: Text(l10n.progressFilterCompleted),
          ),
        ],
        onChanged: (v) {
          if (v != null) onChanged(v);
        },
      ),
    );
  }
}

/// A sort toggle — `CoursesFragment`'s `order_by_date_button` /
/// `order_by_title_button`. An active sort shows its direction arrow; tapping
/// a non-active sort makes it active without flipping (the Kotlin sets
/// `activeSort` first, flipping only on the next tap).
class _SortButton extends StatelessWidget {
  const _SortButton({
    required this.label,
    required this.active,
    required this.ascending,
    required this.onTap,
  });

  final String label;
  final bool active;
  final bool? ascending;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ActionChip(
      label: Text(label),
      avatar: active
          ? Icon(
              ascending == true ? Icons.arrow_upward : Icons.arrow_downward,
              size: 18,
            )
          : const Icon(Icons.sort, size: 18),
      onPressed: onTap,
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

/// Grid variant of [_CourseTile]. Port of the `GridViewHolder` /
/// `item_library_grid.xml` layout the Kotlin `CoursesAdapter` inflates in
/// grid mode — a card with a subject icon placeholder, the title, and the
/// grade/subject subtitle.
class _CourseGridTile extends ConsumerWidget {
  const _CourseGridTile(this.course);

  final CourseRow course;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final subject = classifyCourseSubject(course.subjectLevel);
    final coverFileName = course.coverFileName?.trim() ?? '';
    final subtitleParts = [
      if (course.gradeLevel != null && course.gradeLevel!.isNotEmpty)
        course.gradeLevel!,
      if (course.subjectLevel != null && course.subjectLevel!.isNotEmpty)
        course.subjectLevel!,
    ];

    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () =>
            context.go('${Routes.courses}/${Uri.encodeComponent(course.id)}'),
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                // Port of `CoursesAdapter.bindCover` (818732139): when the
                // course has a cover attachment it is fetched via the
                // authenticated `courseCoverImageProvider` and shown with
                // `Image.memory`; otherwise the subject-tinted background
                // with the subject icon is shown, matching the Kotlin's
                // `setCoverColor` + `subjectIconRes` fallback path.
                child: _CourseCover(
                  courseId: course.id,
                  coverFileName: coverFileName,
                  subject: subject,
                  subjectLabel: courseSubjectLabel(subject, l10n),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                course.courseTitle ?? '',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall?.copyWith(
                  fontWeight: FontWeight.w500,
                ),
              ),
              if (subtitleParts.isNotEmpty)
                Text(
                  subtitleParts.join(' · '),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// The cover area of a course grid tile. Shows the fetched cover image when
/// available, or the subject-tinted icon fallback otherwise.
class _CourseCover extends ConsumerWidget {
  const _CourseCover({
    required this.courseId,
    required this.coverFileName,
    required this.subject,
    required this.subjectLabel,
  });

  final String courseId;
  final String coverFileName;
  final CourseSubject subject;
  final String subjectLabel;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (coverFileName.isEmpty) {
      return _SubjectFallback(subject: subject, label: subjectLabel);
    }
    final image = ref.watch(
      courseCoverImageProvider(
        CourseCoverImageRequest(
          courseId: courseId,
          coverFileName: coverFileName,
        ),
      ),
    );
    return image.when(
      data: (bytes) => bytes == null || bytes.isEmpty
          ? _SubjectFallback(subject: subject, label: subjectLabel)
          : ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.memory(bytes, fit: BoxFit.cover),
            ),
      loading: () => _SubjectFallback(subject: subject, label: subjectLabel),
      error: (_, _) => _SubjectFallback(subject: subject, label: subjectLabel),
    );
  }
}

/// The subject-tinted icon shown when there is no cover image — the Flutter
/// counterpart of `setCoverColor` + `subjectIconRes`.
class _SubjectFallback extends StatelessWidget {
  const _SubjectFallback({required this.subject, required this.label});

  final CourseSubject subject;
  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      decoration: BoxDecoration(
        color: courseSubjectColor(subject).withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            courseSubjectIcon(subject),
            size: 40,
            color: courseSubjectColor(subject),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.labelSmall?.copyWith(
              color: courseSubjectColor(subject),
            ),
          ),
        ],
      ),
    );
  }
}
