import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/resources_providers.dart';
import '../../providers/sync_state.dart';
import '../../providers/view_mode_providers.dart';
import '../components/grid_span_calculator.dart';
import '../components/list_view_mode.dart';
import '../components/view_mode_toggle.dart';
import '../dashboard/dashboard_shell.dart';
import 'resources_filter_sheet.dart';

/// Port of `ui/resources/ResourcesFragment.kt`.
///
/// The Kotlin fragment wires a RecyclerView + adapter + `OnLibraryItemSelected`
/// callback and refreshes itself from `RealtimeSyncManager.dataUpdateFlow`.
/// Here the list is a `ListView.builder` / `GridView.builder` fed by a Drift
/// stream, so a sync writing to `my_library` repaints the list with no callback
/// plumbing.
class ResourcesScreen extends ConsumerWidget {
  const ResourcesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final resources = ref.watch(resourcesStreamProvider);
    final syncState = ref.watch(resourceSyncProvider);
    final filter = ref.watch(resourceFilterProvider);
    final viewMode = ref.watch(libraryViewModeProvider);

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
      appBar: AppBar(
        title: Text(l10n.resources),
        actions: [
          ViewModeToggle(
            mode: viewMode,
            onChanged: ref.read(libraryViewModeProvider.notifier).set,
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
          IconButton(
            tooltip: l10n.sync,
            onPressed: syncState is SyncRunning
                ? null
                : () => ref.read(resourceSyncProvider.notifier).sync(),
            icon: const Icon(Icons.sync),
          ),
          const LogoutAction(),
        ],
        bottom: PreferredSize(
          preferredSize: Size.fromHeight(syncState is SyncRunning ? 68 : 64),
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: SearchBar(
                  hintText: l10n.search,
                  leading: const Icon(Icons.search),
                  onChanged: (value) =>
                      ref.read(resourceSearchQueryProvider.notifier).state =
                          value,
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
      body: resources.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(l10n.syncFailed('$error'))),
        data: (items) {
          // Apply filter if active
          final filteredItems = items.applyFilter(filter);
          if (filteredItems.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(l10n.noDataAvailable),
                  if (!filter.isEmpty) ...[
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: () {
                        ref.read(resourceFilterProvider.notifier).state =
                            const ResourceFilter();
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
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: spanCount,
                    childAspectRatio: 0.85,
                    crossAxisSpacing: 8,
                    mainAxisSpacing: 8,
                  ),
                  padding: const EdgeInsets.all(8),
                  itemCount: filteredItems.length,
                  itemBuilder: (context, index) =>
                      _ResourceGridTile(filteredItems[index]),
                );
              },
            );
          }
          return ListView.separated(
            itemCount: filteredItems.length,
            separatorBuilder: (_, _) => const Divider(height: 1),
            itemBuilder: (context, index) =>
                _ResourceTile(filteredItems[index]),
          );
        },
      ),
    );
  }
}

class _ResourceTile extends StatelessWidget {
  const _ResourceTile(this.resource);

  final MyLibraryRow resource;

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
      trailing: resource.resourceOffline
          ? Tooltip(
              message: l10n.availableOffline,
              child: const Icon(Icons.offline_pin_outlined),
            )
          : null,
      onTap: () {
        context.push('/resources/detail/${resource.id}');
      },
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
  const _ResourceGridTile(this.resource);

  final MyLibraryRow resource;

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
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.push('/resources/detail/${resource.id}'),
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
                      if (resource.resourceOffline)
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
