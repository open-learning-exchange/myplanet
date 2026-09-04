import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/life_provider.dart';
import '../dashboard/dashboard_shell.dart';
import '../router.dart';
import 'life_features.dart';

/// Port of `ui/life/LifeFragment.kt` and its reorder/visibility adapter.
class LifeScreen extends ConsumerWidget {
  const LifeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final items = ref.watch(lifeItemsProvider);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.myLife),
        actions: [
          IconButton(
            tooltip: l10n.community,
            onPressed: () => context.push(Routes.community),
            icon: const Icon(Icons.groups_outlined),
          ),
          IconButton(
            tooltip: l10n.teams,
            onPressed: () => context.push(Routes.teams),
            icon: const Icon(Icons.groups),
          ),
          // Voices belongs to the Community tab (`CommunityPagerAdapter`).
          IconButton(
            tooltip: l10n.voices,
            onPressed: () => context.push(Routes.voices),
            icon: const Icon(Icons.campaign_outlined),
          ),
          const LogoutAction(),
        ],
      ),
      body: items.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.myLifeUnavailable)),
        data: (rows) => rows.isEmpty
            ? Center(child: Text(l10n.noLifeItems))
            : ReorderableListView.builder(
                padding: const EdgeInsets.symmetric(vertical: 8),
                buildDefaultDragHandles: false,
                itemCount: rows.length,
                // ignore: deprecated_member_use
                onReorder: (oldIndex, newIndex) {
                  // ReorderableListView adjustment for newIndex being greater
                  var adjustedNewIndex = newIndex;
                  if (adjustedNewIndex > oldIndex) {
                    adjustedNewIndex -= 1;
                  }
                  final reordered = rows.toList();
                  final moved = reordered.removeAt(oldIndex);
                  reordered.insert(adjustedNewIndex, moved);
                  ref.read(lifeActionsProvider).reorder(reordered);
                },
                itemBuilder: (context, index) {
                  final row = rows[index];
                  return _LifeTile(
                    key: ValueKey(row.id),
                    row: row,
                    index: index,
                  );
                },
              ),
      ),
    );
  }
}

class _LifeTile extends ConsumerWidget {
  const _LifeTile({required this.row, required this.index, super.key});
  final MyLifeRow row;
  final int index;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final title = lifeFeatureTitle(l10n, row.feature, row.title);
    return Opacity(
      opacity: row.isVisible ? 1 : 0.5,
      child: ListTile(
        leading: CircleAvatar(child: Icon(lifeFeatureIcon(row.feature))),
        title: Text(title),
        subtitle: Text(
          row.isVisible ? l10n.shownOnDashboard : l10n.hiddenFromDashboard,
        ),
        onTap: () => openLifeFeature(context, row.feature),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              tooltip: row.isVisible
                  ? l10n.hideItem(title)
                  : l10n.showItem(title),
              onPressed: () => ref
                  .read(lifeActionsProvider)
                  .setVisibility(row, visible: !row.isVisible),
              icon: Icon(
                row.isVisible ? Icons.visibility : Icons.visibility_off,
              ),
            ),
            ReorderableDragStartListener(
              index: index,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Icon(
                  Icons.drag_handle,
                  semanticLabel: l10n.reorderItem(title),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
