import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/life_provider.dart';
import '../dashboard/dashboard_shell.dart';
import '../router.dart';

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
            tooltip: l10n.teams,
            onPressed: () => context.push(Routes.teams),
            icon: const Icon(Icons.groups_outlined),
          ),
          // Voices belongs to the Community tab (`CommunityPagerAdapter`),
          // which is not ported yet. Parked here so the feed is reachable,
          // rather than added to the My Life list — that list is seeded,
          // user-reorderable data and inserting a non-Kotlin entry into it
          // would diverge from what the shipping app shows.
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
    final title = _featureTitle(l10n, row.feature, row.title);
    return Opacity(
      opacity: row.isVisible ? 1 : 0.5,
      child: ListTile(
        leading: CircleAvatar(child: Icon(_featureIcon(row.feature))),
        title: Text(title),
        subtitle: Text(
          row.isVisible ? l10n.shownOnDashboard : l10n.hiddenFromDashboard,
        ),
        onTap: () => _openFeature(context, row.feature),
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

void _openFeature(BuildContext context, String feature) {
  switch (feature) {
    case 'calendar':
      context.go(Routes.calendar);
      return;
    case 'references':
      context.push(Routes.references);
      return;
    case 'personals':
      context.push(Routes.personals);
      return;
    case 'submissions':
      context.push(Routes.submissions);
      return;
    case 'surveys':
      context.push(Routes.surveys);
      return;
    default:
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context).featureComingSoon)),
      );
      return;
  }
}

IconData _featureIcon(String feature) => switch (feature) {
  'health' => Icons.favorite_outline,
  'achievements' => Icons.emoji_events_outlined,
  'submissions' => Icons.assignment_outlined,
  'surveys' => Icons.poll_outlined,
  'references' => Icons.library_books_outlined,
  'calendar' => Icons.calendar_month_outlined,
  'personals' => Icons.lock_person_outlined,
  _ => Icons.apps,
};

String _featureTitle(AppLocalizations l10n, String feature, String? fallback) =>
    switch (feature) {
      'health' => l10n.myHealth,
      'achievements' => l10n.achievements,
      'submissions' => l10n.submissions,
      'surveys' => l10n.mySurveys,
      'references' => l10n.references,
      'calendar' => l10n.calendar,
      'personals' => l10n.myPersonals,
      _ => fallback ?? feature,
    };
