import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/courses_providers.dart';
import '../../providers/search_activity_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/sync_state.dart';
import '../../providers/tags_providers.dart';
import '../../providers/view_mode_providers.dart';
import '../components/grid_span_calculator.dart';
import '../components/list_view_mode.dart';
import '../components/view_mode_toggle.dart';
import '../dashboard/dashboard_shell.dart';
import '../resources/collections_dialog.dart';
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

  /// The filter state captured on the last build, so `dispose` can read it
  /// after the widget is torn down — `ref.read` throws once the element is
  /// disposed. Port of `CoursesFragment.onPause` → `saveSearchActivity`.
  CourseFilter _lastFilter = const CourseFilter();
  CourseProgressFilter _lastProgress = CourseProgressFilter.all;
  List<Tag> _lastSelectedTags = const [];
  ProviderContainer? _container;

  /// Multi-selection state — port of `CourseSelectionController`. Long-press
  /// a course tile to enter selection mode; tapping a tile then toggles
  /// membership instead of navigating. The selection bar mirrors the
  /// Kotlin's select-all + add-to-my-courses (or leave, in the My Courses
  /// view) row.
  final Set<String> _selectedCourseIds = {};
  bool _selectionMode = false;

  @override
  void dispose() {
    _saveSearchActivity();
    _searchController.dispose();
    super.dispose();
  }

  void _saveSearchActivity() {
    final container = _container;
    if (container == null) return;
    final filter = _lastFilter;
    final progress = _lastProgress != CourseProgressFilter.all;
    final tags = _lastSelectedTags;
    final applied =
        filter.query.isNotEmpty ||
        filter.gradeLevel != null ||
        filter.subjectLevel != null ||
        progress ||
        tags.isNotEmpty;
    if (!applied) return;
    // Fire-and-forget; the row is durable once written. `ref` is gone by the
    // time `dispose` runs, so the helper reads through the container instead.
    saveCourseSearchActivity(
      container,
      searchText: filter.query,
      tags: [for (final t in tags) t.couchId],
      grade: filter.gradeLevel,
      subject: filter.subjectLevel,
    );
  }

  void _enterSelection(String courseId) {
    setState(() {
      _selectionMode = true;
      _selectedCourseIds.add(courseId);
    });
  }

  void _toggleSelection(String courseId) {
    setState(() {
      if (!_selectedCourseIds.add(courseId)) {
        _selectedCourseIds.remove(courseId);
      }
      if (_selectedCourseIds.isEmpty) _selectionMode = false;
    });
  }

  void _clearSelection() {
    setState(() {
      _selectedCourseIds.clear();
      _selectionMode = false;
    });
  }

  void _toggleSelectAll(List<CourseRow> visibleItems) {
    setState(() {
      final allSelected = visibleItems.every(
        (c) => _selectedCourseIds.contains(c.id),
      );
      if (allSelected) {
        _selectedCourseIds.clear();
      } else {
        _selectedCourseIds.addAll(visibleItems.map((c) => c.id));
      }
    });
  }

  Future<void> _batchSetMembership(
    List<CourseRow> selectedCourses, {
    required String? userId,
    required bool joined,
  }) async {
    final l10n = AppLocalizations.of(context);
    if (userId == null) return;
    final repo = ref.read(coursesRepositoryProvider);
    for (final course in selectedCourses) {
      await repo.setShelfMembership(course.id, userId, joined: joined);
    }
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          joined
              ? l10n.addedToMyCourses(selectedCourses.length)
              : l10n.leftCourses(selectedCourses.length),
        ),
      ),
    );
    _clearSelection();
  }

  Future<void> _confirmLeave(
    List<CourseRow> selectedCourses, {
    required String? userId,
  }) async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        content: Text(l10n.leaveCoursesConfirm(selectedCourses.length)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.leaveCourse),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await _batchSetMembership(selectedCourses, userId: userId, joined: false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final courses = ref.watch(filteredSortedCoursesProvider);
    final syncState = ref.watch(courseSyncProvider);
    final viewMode = ref.watch(courseViewModeProvider);
    final userId = ref.watch(sessionProvider).valueOrNull?.id;

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

    // Capture the current filter state and container so `dispose` can fire
    // `saveSearchActivity` without touching `ref` (which throws after the
    // element is torn down).
    _lastFilter = ref.watch(courseFilterProvider);
    _lastProgress = ref.watch(courseProgressFilterProvider);
    _lastSelectedTags = ref.watch(courseSelectedTagsProvider);
    _container = ProviderScope.containerOf(context, listen: false);
    final selectedTags = _lastSelectedTags;

    // Course id → its named tags, for the collections filter. The family key
    // is the ids joined so an unchanged list reuses the cached map.
    final tagMap =
        ref
            .watch(
              courseTagsProvider(
                [
                  for (final c in courses.valueOrNull ?? const []) c.id,
                ].join('\n'),
              ),
            )
            .valueOrNull ??
        const {};

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.courses),
        actions: [
          IconButton(
            tooltip: l10n.collections,
            icon: Badge(
              isLabelVisible: selectedTags.isNotEmpty,
              child: const Icon(Icons.collections_bookmark_outlined),
            ),
            onPressed: () async {
              final picked = await showCollectionsDialog(
                context,
                dbType: 'courses',
              );
              if (picked != null) {
                ref.read(courseSelectedTagsProvider.notifier).state = picked;
              }
            },
          ),
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
          preferredSize: Size.fromHeight(
            (syncState is SyncRunning ? 116 : 112) +
                (selectedTags.isNotEmpty ? 28 : 0),
          ),
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
              // Port of the Kotlin `tvSelected` label under the tag tabs.
              if (selectedTags.isNotEmpty)
                SizedBox(
                  height: 28,
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      child: Text(
                        '${l10n.selected}'
                        '${selectedTags.map((t) => t.name).join(", ")}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                ),
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
          // Port of `filterCourses` in the courses controller: keep only
          // courses carrying at least one of the selected collections.
          final selectedTagIds = {for (final t in selectedTags) t.id};
          final visibleItems = selectedTags.isEmpty
              ? items
              : items.where((course) {
                  final tags = tagMap[course.id] ?? const [];
                  return tags.any((t) => selectedTagIds.contains(t.id));
                }).toList();
          if (visibleItems.isEmpty) {
            return Center(child: Text(l10n.noDataAvailable));
          }
          final selectedCourses = visibleItems
              .where((c) => _selectedCourseIds.contains(c.id))
              .toList();
          final courseContent = viewMode == ListViewMode.grid
              ? LayoutBuilder(
                  builder: (context, constraints) {
                    final spanCount = GridSpanCalculator.columnCount(
                      constraints.maxWidth /
                          MediaQuery.devicePixelRatioOf(context),
                    );
                    return GridView.builder(
                      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: spanCount,
                        childAspectRatio: 0.85,
                        crossAxisSpacing: 8,
                        mainAxisSpacing: 8,
                      ),
                      padding: const EdgeInsets.all(8),
                      itemCount: visibleItems.length,
                      itemBuilder: (context, index) => _CourseGridTile(
                        visibleItems[index],
                        selectionMode: _selectionMode,
                        selected: _selectedCourseIds.contains(
                          visibleItems[index].id,
                        ),
                        onTap: _selectionMode
                            ? () => _toggleSelection(visibleItems[index].id)
                            : null,
                        onLongPress: () =>
                            _enterSelection(visibleItems[index].id),
                      ),
                    );
                  },
                )
              : ListView.separated(
                  itemCount: visibleItems.length,
                  separatorBuilder: (_, _) => const Divider(height: 1),
                  itemBuilder: (context, index) => _CourseTile(
                    visibleItems[index],
                    selectionMode: _selectionMode,
                    selected: _selectedCourseIds.contains(
                      visibleItems[index].id,
                    ),
                    onTap: _selectionMode
                        ? () => _toggleSelection(visibleItems[index].id)
                        : null,
                    onLongPress: () => _enterSelection(visibleItems[index].id),
                  ),
                );
          if (!_selectionMode) return courseContent;
          return Column(
            children: [
              _CourseSelectionBar(
                selectedCount: _selectedCourseIds.length,
                allSelected: visibleItems.every(
                  (c) => _selectedCourseIds.contains(c.id),
                ),
                isMyCoursesView: _lastFilter.myCoursesOnly,
                onToggleAll: () => _toggleSelectAll(visibleItems),
                onAdd: () => _batchSetMembership(
                  selectedCourses,
                  userId: userId,
                  joined: true,
                ),
                onLeave: () => _confirmLeave(selectedCourses, userId: userId),
                onClose: _clearSelection,
              ),
              Expanded(child: courseContent),
            ],
          );
        },
      ),
    );
  }
}

/// Port of `CourseSelectionController`'s action row: a select-all toggle and
/// either the add-to-my-courses action (All Courses view) or the leave action
/// (My Courses view). Shown only while the list is in selection mode.
class _CourseSelectionBar extends StatelessWidget {
  const _CourseSelectionBar({
    required this.selectedCount,
    required this.allSelected,
    required this.isMyCoursesView,
    required this.onToggleAll,
    required this.onAdd,
    required this.onLeave,
    required this.onClose,
  });

  final int selectedCount;
  final bool allSelected;
  final bool isMyCoursesView;
  final VoidCallback onToggleAll;
  final VoidCallback onAdd;
  final VoidCallback onLeave;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final hasSelection = selectedCount > 0;
    return Material(
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Row(
          children: [
            IconButton(
              icon: const Icon(Icons.close),
              tooltip: l10n.cancel,
              onPressed: onClose,
            ),
            TextButton(
              onPressed: onToggleAll,
              child: Text(allSelected ? l10n.unselectAll : l10n.selectAll),
            ),
            const Spacer(),
            Text(l10n.coursesSelected(selectedCount)),
            const SizedBox(width: 8),
            if (isMyCoursesView)
              FilledButton.tonal(
                onPressed: hasSelection ? onLeave : null,
                child: Text(l10n.leaveCourse),
              )
            else
              FilledButton.tonal(
                onPressed: hasSelection ? onAdd : null,
                child: Text(l10n.joinCourse),
              ),
          ],
        ),
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
  const _CourseTile(
    this.course, {
    this.selectionMode = false,
    this.selected = false,
    this.onTap,
    this.onLongPress,
  });

  final CourseRow course;
  final bool selectionMode;
  final bool selected;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  @override
  Widget build(BuildContext context) {
    final subtitleParts = [
      if (course.gradeLevel != null && course.gradeLevel!.isNotEmpty)
        course.gradeLevel!,
      if (course.subjectLevel != null && course.subjectLevel!.isNotEmpty)
        course.subjectLevel!,
    ];
    final leading = selectionMode
        ? Checkbox(value: selected, onChanged: (_) => onTap?.call())
        : const Icon(Icons.school_outlined);
    return ListTile(
      leading: leading,
      selected: selected,
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
      trailing: selectionMode
          ? (selected ? const Icon(Icons.check_circle) : null)
          : const Icon(Icons.chevron_right),
      onTap: selectionMode
          ? onTap
          : () => context.go(
              '${Routes.courses}/${Uri.encodeComponent(course.id)}',
            ),
      onLongPress: onLongPress,
    );
  }
}

/// Grid variant of [_CourseTile]. Port of the `GridViewHolder` /
/// `item_library_grid.xml` layout the Kotlin `CoursesAdapter` inflates in
/// grid mode — a card with a subject icon placeholder, the title, and the
/// grade/subject subtitle.
class _CourseGridTile extends ConsumerWidget {
  const _CourseGridTile(
    this.course, {
    this.selectionMode = false,
    this.selected = false,
    this.onTap,
    this.onLongPress,
  });

  final CourseRow course;
  final bool selectionMode;
  final bool selected;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

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
      color: selected
          ? theme.colorScheme.primaryContainer
          : theme.cardTheme.color,
      child: InkWell(
        onTap: selectionMode
            ? onTap
            : () => context.go(
                '${Routes.courses}/${Uri.encodeComponent(course.id)}',
              ),
        onLongPress: onLongPress,
        child: Stack(
          children: [
            Padding(
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
            if (selectionMode && selected)
              Positioned(
                top: 4,
                right: 4,
                child: Icon(
                  Icons.check_circle,
                  color: theme.colorScheme.primary,
                  size: 20,
                ),
              ),
          ],
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
