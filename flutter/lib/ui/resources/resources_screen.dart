import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/resources_providers.dart';
import '../../providers/sync_state.dart';
import '../dashboard/dashboard_shell.dart';

/// Port of `ui/resources/ResourcesFragment.kt`.
///
/// The Kotlin fragment wires a RecyclerView + adapter + `OnLibraryItemSelected`
/// callback and refreshes itself from `RealtimeSyncManager.dataUpdateFlow`.
/// Here the list is a `ListView.builder` fed by a Drift stream, so a sync
/// writing to `my_library` repaints the list with no callback plumbing.
class ResourcesScreen extends ConsumerWidget {
  const ResourcesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final resources = ref.watch(resourcesStreamProvider);
    final syncState = ref.watch(resourceSyncProvider);

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
          if (items.isEmpty) {
            return Center(child: Text(l10n.noDataAvailable));
          }
          return ListView.separated(
            itemCount: items.length,
            separatorBuilder: (_, _) => const Divider(height: 1),
            itemBuilder: (context, index) => _ResourceTile(items[index]),
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
        context.push('/resources/viewer/${resource.id}');
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
