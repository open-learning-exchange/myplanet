import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/resources_providers.dart';
import '../../providers/search_activity_providers.dart';
import '../../providers/sync_state.dart';
import '../../providers/tags_providers.dart';
import '../../providers/view_mode_providers.dart';
import '../components/grid_span_calculator.dart';
import '../components/list_view_mode.dart';
import '../components/view_mode_toggle.dart';
import '../dashboard/dashboard_shell.dart';
import '../router.dart';
import 'collections_dialog.dart';
import 'resources_filter_sheet.dart';

/// Port of `ui/resources/ResourcesFragment.kt`.
///
/// The Kotlin fragment wires a RecyclerView + adapter + `OnLibraryItemSelected`
/// callback and refreshes itself from `RealtimeSyncManager.dataUpdateFlow`.
/// Here the list is a `ListView.builder` / `GridView.builder` fed by a Drift
/// stream, so a sync writing to `my_library` repaints the list with no callback
/// plumbing.
class ResourcesScreen extends ConsumerStatefulWidget {
  const ResourcesScreen({super.key});

  @override
  ConsumerState<ResourcesScreen> createState() => _ResourcesScreenState();
}

class _ResourcesScreenState extends ConsumerState<ResourcesScreen> {
  final Set<String> _selectedIds = {};
  final ScrollController _scrollController = ScrollController();

  bool get _selecting => _selectedIds.isNotEmpty;

  /// The filter state captured on the last build, so `dispose` can read it
  /// after the widget is torn down — `ref.read` throws once the element is
  /// disposed. Port of `ResourcesFragment.onPause` → `saveSearchActivity`.
  ResourceFilter _lastFilter = const ResourceFilter();
  String _lastSearchText = '';
  List<Tag> _lastSelectedTags = const [];
  ProviderContainer? _container;

  @override
  void dispose() {
    // Port of `ResourcesFragment.onPause` → `saveSearchActivity`: records one
    // search-activity row when the user leaves the screen with a filter or
    // search text applied. `dispose` is the Flutter lifecycle point that maps
    // to `onPause`. Fire-and-forget; the row is durable once written.
    _scrollController.dispose();
    _saveSearchActivity();
    super.dispose();
  }

  void _saveSearchActivity() {
    final container = _container;
    if (container == null) return;
    final filter = _lastFilter;
    final searchText = _lastSearchText;
    final tags = _lastSelectedTags;
    final applied = searchText.isNotEmpty || !filter.isEmpty || tags.isNotEmpty;
    if (!applied) return;
    saveResourceSearchActivity(
      container,
      searchText: searchText,
      tags: [for (final t in tags) t.couchId],
      subjects: filter.subjects,
      languages: filter.languages,
      levels: filter.levels,
      mediums: filter.mediaTypes,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final resources = ref.watch(resourcesStreamProvider);
    final syncState = ref.watch(resourceSyncProvider);
    final filter = ref.watch(resourceFilterProvider);
    final viewMode = ref.watch(libraryViewModeProvider);
    final shelfOnly = ref.watch(resourceShelfOnlyProvider);
    final selectedTags = ref.watch(resourceSelectedTagsProvider);
    final sort = ref.watch(resourceSortProvider);

    // Capture the current filter state, search text, and container so
    // `dispose` can fire `saveSearchActivity` without touching `ref` (which
    // throws after the element is torn down).
    _lastFilter = filter;
    _lastSearchText = ref.watch(resourceSearchQueryProvider);
    _lastSelectedTags = selectedTags;
    _container = ProviderScope.containerOf(context, listen: false);

    // Resource id → its named tags, for the collections filter. Only needed
    // when a tag is selected, but the watch must run unconditionally (hooks
    // rule). The family key is the ids joined so an unchanged list reuses the
    // cached map.
    final tagMap =
        ref
            .watch(
              resourceTagsProvider(
                [
                  for (final r in resources.valueOrNull ?? const []) r.id,
                ].join('\n'),
              ),
            )
            .valueOrNull ??
        const {};

    // #15572: when the shelf has no rows at all, the search bar, the list/grid
    // toggle, and the filter button offer nothing to act on. Hiding them
    // leaves only the sync button, which is the one thing the user can do.
    final hasData = resources.valueOrNull?.isNotEmpty ?? false;

    ref.listen<SyncUiState>(resourceSyncProvider, (previous, next) {
      final messenger = ScaffoldMessenger.of(context);
      switch (next) {
        case SyncSucceeded(:final savedCount):
          messenger.showSnackBar(
            SnackBar(content: Text(l10n.syncedResources(savedCount))),
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
      floatingActionButton: !_selecting && shelfOnly
          ? FloatingActionButton(
              tooltip: l10n.addResource,
              onPressed: () => context.push(Routes.addResource),
              child: const Icon(Icons.add),
            )
          : null,
      appBar: AppBar(
        leading: _selecting
            ? IconButton(
                icon: const Icon(Icons.close),
                tooltip: l10n.cancel,
                onPressed: () => setState(_selectedIds.clear),
              )
            : null,
        title: Text(
          _selecting
              ? l10n.storageSelectedCount(_selectedIds.length)
              : l10n.resources,
        ),
        actions: [
          if (_selecting) ...[
            IconButton(
              tooltip: l10n.addToMyLibrary,
              icon: const Icon(Icons.bookmark_add_outlined),
              onPressed: () => _setSelectedMembership(joined: true),
            ),
            IconButton(
              tooltip: l10n.removeFromMyLibrary,
              icon: const Icon(Icons.bookmark_remove_outlined),
              onPressed: () => _setSelectedMembership(joined: false),
            ),
          ] else ...[
            IconButton(
              tooltip: shelfOnly ? l10n.allResources : l10n.myLibrary,
              icon: Icon(
                shelfOnly ? Icons.folder_outlined : Icons.bookmark_border,
              ),
              onPressed: () =>
                  ref.read(resourceShelfOnlyProvider.notifier).state =
                      !shelfOnly,
            ),
            if (hasData) ...[
              IconButton(
                tooltip: l10n.collections,
                icon: Badge(
                  isLabelVisible: selectedTags.isNotEmpty,
                  child: const Icon(Icons.collections_bookmark_outlined),
                ),
                onPressed: () async {
                  final picked = await showCollectionsDialog(
                    context,
                    dbType: 'resources',
                  );
                  if (picked != null) {
                    ref.read(resourceSelectedTagsProvider.notifier).state =
                        picked;
                  }
                },
              ),
              ViewModeToggle(
                mode: viewMode,
                onChanged: ref.read(libraryViewModeProvider.notifier).set,
              ),
              IconButton(
                tooltip: l10n.sortResources,
                icon: Badge(
                  isLabelVisible: sort.mode != ResourceSortMode.none,
                  child: const Icon(Icons.sort),
                ),
                onPressed: _showSortSheet,
              ),
              IconButton(
                tooltip: l10n.filterResources,
                onPressed: () {
                  showModalBottomSheet(
                    context: context,
                    isScrollControlled: true,
                    builder: (context) => const ResourcesFilterSheet(),
                  );
                },
                icon: Badge(
                  isLabelVisible: !filter.isEmpty,
                  child: const Icon(Icons.filter_list),
                ),
              ),
            ],
            IconButton(
              tooltip: l10n.sync,
              onPressed: syncState is SyncRunning
                  ? null
                  : () => ref.read(resourceSyncProvider.notifier).sync(),
              icon: const Icon(Icons.sync),
            ),
            const LogoutAction(),
          ],
        ],
        bottom: _selecting || !hasData
            ? null
            : PreferredSize(
                preferredSize: Size.fromHeight(
                  (syncState is SyncRunning ? 68 : 64) +
                      (selectedTags.isNotEmpty ? 40 : 0),
                ),
                child: Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                      child: SearchBar(
                        hintText: l10n.search,
                        leading: const Icon(Icons.search),
                        onChanged: (value) =>
                            ref
                                    .read(resourceSearchQueryProvider.notifier)
                                    .state =
                                value,
                      ),
                    ),
                    if (syncState is SyncRunning)
                      LinearProgressIndicator(
                        value: syncState.progress.total == 0
                            ? null
                            : syncState.progress.fraction,
                      ),
                    // Port of `ResourcesFragment.refreshTagChips` — the
                    // selected collections as dismissible chips.
                    if (selectedTags.isNotEmpty)
                      SizedBox(
                        height: 36,
                        child: ListView(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          children: [
                            for (final tag in selectedTags)
                              Padding(
                                padding: const EdgeInsets.only(right: 8),
                                child: InputChip(
                                  label: Text(tag.name),
                                  onDeleted: () =>
                                      ref
                                          .read(
                                            resourceSelectedTagsProvider
                                                .notifier,
                                          )
                                          .state = selectedTags
                                          .where((t) => t.id != tag.id)
                                          .toList(),
                                ),
                              ),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
      ),
      body: resources.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(l10n.syncFailed('$error'))),
        data: (items) {
          // Apply filter if active
          var filteredItems = items.applyFilter(filter);

          // Port of `filterLocalLibraryByTag`: keep only resources carrying
          // at least one of the selected collections. A resource's tag set
          // comes from the `tag` link rows, joined to the tag definitions.
          if (selectedTags.isNotEmpty) {
            final selectedTagIds = {for (final t in selectedTags) t.id};
            filteredItems = filteredItems.where((item) {
              final tags = tagMap[item.id] ?? const [];
              return tags.any((t) => selectedTagIds.contains(t.id));
            }).toList();
          }

          filteredItems = applyResourceSort(filteredItems, sort);

          if (filteredItems.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(l10n.noDataAvailable),
                  if (!filter.isEmpty || selectedTags.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: () {
                        ref.read(resourceFilterProvider.notifier).state =
                            const ResourceFilter();
                        ref.read(resourceSelectedTagsProvider.notifier).state =
                            [];
                      },
                      child: Text(l10n.clearFilters),
                    ),
                  ],
                ],
              ),
            );
          }
          if (viewMode == ListViewMode.grid) {
            return LayoutBuilder(
              builder: (context, constraints) {
                final spanCount = GridSpanCalculator.columnCount(
                  constraints.maxWidth / MediaQuery.devicePixelRatioOf(context),
                );
                return GridView.builder(
                  controller: _scrollController,
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: spanCount,
                    childAspectRatio: 0.85,
                    crossAxisSpacing: 8,
                    mainAxisSpacing: 8,
                  ),
                  padding: const EdgeInsets.all(8),
                  itemCount: filteredItems.length,
                  itemBuilder: (context, index) => _ResourceGridTile(
                    filteredItems[index],
                    selected: _selectedIds.contains(filteredItems[index].id),
                    onTap: () => _tapResource(filteredItems[index]),
                    onLongPress: () =>
                        _toggleSelection(filteredItems[index].id),
                  ),
                );
              },
            );
          }
          return ListView.separated(
            controller: _scrollController,
            itemCount: filteredItems.length,
            separatorBuilder: (_, _) => const Divider(height: 1),
            itemBuilder: (context, index) => _ResourceTile(
              filteredItems[index],
              selected: _selectedIds.contains(filteredItems[index].id),
              onTap: () => _tapResource(filteredItems[index]),
              onLongPress: () => _toggleSelection(filteredItems[index].id),
            ),
          );
        },
      ),
    );
  }

  /// Port of the `orderByDateButton`/`orderByTitleButton` pair in
  /// `ResourcesFragment`'s bottom sheet. Tapping an option switches to its
  /// sort mode and flips that mode's direction
  /// (`ResourcesViewModel.toggleSortOrder`/`toggleTitleSortOrder`), then the
  /// list scrolls back to the top exactly as the Kotlin's
  /// `recyclerView.scrollToPosition(0)` does.
  void _showSortSheet() {
    final l10n = AppLocalizations.of(context);
    showModalBottomSheet<void>(
      context: context,
      builder: (context) {
        final sort = ref.read(resourceSortProvider);
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.sort_by_alpha),
                title: Text(l10n.orderByTitle),
                trailing: sort.mode == ResourceSortMode.title
                    ? Icon(
                        sort.titleAscending
                            ? Icons.arrow_upward
                            : Icons.arrow_downward,
                      )
                    : null,
                onTap: () => _toggleSort((s) => s.toggleTitle()),
              ),
              ListTile(
                leading: const Icon(Icons.event),
                title: Text(l10n.orderByDate),
                trailing: sort.mode == ResourceSortMode.date
                    ? Icon(
                        sort.dateAscending
                            ? Icons.arrow_upward
                            : Icons.arrow_downward,
                      )
                    : null,
                onTap: () => _toggleSort((s) => s.toggleDate()),
              ),
            ],
          ),
        );
      },
    );
  }

  void _toggleSort(ResourceSortState Function(ResourceSortState) toggle) {
    ref.read(resourceSortProvider.notifier).update(toggle);
    Navigator.pop(context);
    if (_scrollController.hasClients) {
      _scrollController.jumpTo(0);
    }
  }

  void _tapResource(MyLibraryRow resource) {
    if (_selecting) {
      _toggleSelection(resource.id);
    } else {
      context.push('/resources/detail/${resource.id}');
    }
  }

  void _toggleSelection(String id) {
    setState(() {
      if (!_selectedIds.add(id)) _selectedIds.remove(id);
    });
  }

  Future<void> _setSelectedMembership({required bool joined}) async {
    final l10n = AppLocalizations.of(context);
    final selected = Set<String>.from(_selectedIds);
    try {
      await ref
          .read(resourceShelfActionsProvider)
          .setMemberships(selected, joined: joined);
      if (!mounted) return;
      setState(_selectedIds.clear);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            joined ? l10n.addedToMyLibrary : l10n.removedFromMyLibrary,
          ),
        ),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.operationFailed)));
    }
  }
}

class _ResourceTile extends StatelessWidget {
  const _ResourceTile(
    this.resource, {
    required this.selected,
    required this.onTap,
    required this.onLongPress,
  });

  final MyLibraryRow resource;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final subtitleParts = [
      if (resource.author != null && resource.author!.isNotEmpty)
        resource.author!,
      if (resource.year != null && resource.year!.isNotEmpty) resource.year!,
      ...resource.subject,
    ];

    return ListTile(
      selected: selected,
      selectedTileColor: Theme.of(context).colorScheme.secondaryContainer,
      leading: Icon(_iconFor(resource.mediaType)),
      title: Text(
        resource.title ?? '',
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
      trailing: selected
          ? const Icon(Icons.check_circle)
          : resource.resourceOffline
          ? Tooltip(
              message: l10n.availableOffline,
              child: const Icon(Icons.offline_pin_outlined),
            )
          : null,
      onTap: onTap,
      onLongPress: onLongPress,
    );
  }

  /// Mirrors the media-type icon mapping in the Kotlin resource adapter.
  static IconData _iconFor(String? mediaType) {
    return switch (mediaType?.toLowerCase()) {
      'video' => Icons.play_circle_outline,
      'audio' => Icons.audiotrack_outlined,
      'image' => Icons.image_outlined,
      'pdf' => Icons.picture_as_pdf_outlined,
      _ => Icons.article_outlined,
    };
  }
}

/// Grid variant of [_ResourceTile]. Port of the `GridViewHolder` /
/// `item_library_grid.xml` layout the Kotlin `ResourcesAdapter` inflates in
/// grid mode — a card with the media-type icon, title, and a subtitle.
class _ResourceGridTile extends StatelessWidget {
  const _ResourceGridTile(
    this.resource, {
    required this.selected,
    required this.onTap,
    required this.onLongPress,
  });

  final MyLibraryRow resource;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final subtitleParts = [
      if (resource.author != null && resource.author!.isNotEmpty)
        resource.author!,
      if (resource.year != null && resource.year!.isNotEmpty) resource.year!,
      ...resource.subject,
    ];

    return Card(
      color: selected ? theme.colorScheme.secondaryContainer : null,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Center(
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      Icon(
                        _iconFor(resource.mediaType),
                        size: 40,
                        color: theme.colorScheme.primary.withValues(alpha: 0.6),
                      ),
                      if (selected)
                        Icon(
                          Icons.check_circle,
                          size: 28,
                          color: theme.colorScheme.primary,
                        )
                      else if (resource.resourceOffline)
                        Positioned(
                          bottom: 0,
                          right: 0,
                          child: Icon(
                            Icons.offline_pin_outlined,
                            size: 16,
                            color: theme.colorScheme.tertiary,
                          ),
                        ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                resource.title ?? '',
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

  static IconData _iconFor(String? mediaType) =>
      _ResourceTile._iconFor(mediaType);
}
