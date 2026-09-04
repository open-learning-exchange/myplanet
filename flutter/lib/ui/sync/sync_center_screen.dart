import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/dashboard_sync_provider.dart';
import '../../providers/sync_state.dart';

class SyncCenterScreen extends ConsumerWidget {
  const SyncCenterScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final state = ref.watch(dashboardSyncProvider);
    final lastSync = ref.watch(lastSyncProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.syncCenter)),
      body: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: _SyncSummary(state: state, lastSync: lastSync),
          ),
          SliverList.builder(
            itemCount: state.items.length,
            itemBuilder: (context, index) => _SyncAreaTile(
              item: state.items[index],
              globallyRunning: state.running,
              onRetry: () => ref
                  .read(dashboardSyncProvider.notifier)
                  .retry(state.items[index].area),
            ),
          ),
          const SliverPadding(padding: EdgeInsets.only(bottom: 96)),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: state.running
            ? null
            : () => ref.read(dashboardSyncProvider.notifier).syncAll(),
        icon: state.running
            ? const SizedBox.square(
                dimension: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Icon(Icons.sync),
        label: Text(state.running ? l10n.syncing : l10n.syncAll),
      ),
    );
  }
}

class _SyncSummary extends StatelessWidget {
  const _SyncSummary({required this.state, required this.lastSync});

  final DashboardSyncState state;
  final int lastSync;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final hasRun = state.startedAt != null;
    final headline = state.running
        ? l10n.syncProgress(state.completedCount, state.items.length)
        : !hasRun
        ? l10n.syncReady
        : state.failureCount == 0
        ? l10n.syncAllSuccessful(state.successCount)
        : l10n.syncCompletedWithFailures(
            state.successCount,
            state.failureCount,
          );

    return Card(
      margin: const EdgeInsets.all(16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  state.failureCount > 0
                      ? Icons.sync_problem_outlined
                      : Icons.cloud_sync_outlined,
                  size: 32,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    headline,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            LinearProgressIndicator(
              value: state.running || hasRun ? state.progress : 0,
            ),
            const SizedBox(height: 12),
            Text(
              lastSync <= 0
                  ? l10n.lastSynced(l10n.neverSynced)
                  : l10n.lastSyncTimestamp(
                      MaterialLocalizations.of(context).formatFullDate(
                        DateTime.fromMillisecondsSinceEpoch(lastSync),
                      ),
                    ),
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 8),
            Text(
              l10n.syncForegroundNotice,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }
}

class _SyncAreaTile extends StatelessWidget {
  const _SyncAreaTile({
    required this.item,
    required this.globallyRunning,
    required this.onRetry,
  });

  final DashboardSyncItem item;
  final bool globallyRunning;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final metadata = _areaMetadata(l10n, item.area);
    return ListTile(
      leading: CircleAvatar(child: Icon(metadata.icon)),
      title: Text(metadata.label),
      subtitle: Text('${metadata.description}\n${_statusText(l10n)}'),
      trailing: switch (item.status) {
        DashboardSyncStatus.waiting => const Icon(Icons.schedule_outlined),
        DashboardSyncStatus.running => const SizedBox.square(
          dimension: 22,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        DashboardSyncStatus.succeeded => const Icon(
          Icons.check_circle,
          color: Colors.green,
        ),
        DashboardSyncStatus.failed => IconButton(
          tooltip: l10n.retry,
          onPressed: globallyRunning ? null : onRetry,
          icon: const Icon(Icons.refresh),
        ),
      },
    );
  }

  String _statusText(AppLocalizations l10n) => switch (item.status) {
    DashboardSyncStatus.waiting => l10n.waiting,
    DashboardSyncStatus.running => l10n.syncing,
    DashboardSyncStatus.succeeded => l10n.itemsSynced(item.savedCount),
    DashboardSyncStatus.failed => l10n.syncFailed(item.message ?? ''),
  };
}

({String label, String description, IconData icon}) _areaMetadata(
  AppLocalizations l10n,
  DashboardSyncArea area,
) => switch (area) {
  DashboardSyncArea.resources => (
    label: l10n.resources,
    description: l10n.syncResourcesDescription,
    icon: Icons.local_library_outlined,
  ),
  DashboardSyncArea.courses => (
    label: l10n.courses,
    description: l10n.syncCoursesDescription,
    icon: Icons.school_outlined,
  ),
  DashboardSyncArea.teams => (
    label: l10n.teams,
    description: l10n.syncTeamsDescription,
    icon: Icons.groups_outlined,
  ),
  DashboardSyncArea.events => (
    label: l10n.events,
    description: l10n.syncEventsDescription,
    icon: Icons.event_outlined,
  ),
  DashboardSyncArea.surveys => (
    label: l10n.surveys,
    description: l10n.syncSurveysDescription,
    icon: Icons.assignment_outlined,
  ),
  DashboardSyncArea.voices => (
    label: l10n.voices,
    description: l10n.syncVoicesDescription,
    icon: Icons.record_voice_over_outlined,
  ),
  DashboardSyncArea.feedback => (
    label: l10n.feedback,
    description: l10n.syncFeedbackDescription,
    icon: Icons.feedback_outlined,
  ),
  DashboardSyncArea.chat => (
    label: l10n.aiChat,
    description: l10n.syncChatDescription,
    icon: Icons.forum_outlined,
  ),
  DashboardSyncArea.health => (
    label: l10n.myHealth,
    description: l10n.syncHealthDescription,
    icon: Icons.health_and_safety_outlined,
  ),
  DashboardSyncArea.activities => (
    label: l10n.myActivities,
    description: l10n.syncActivitiesDescription,
    icon: Icons.history_outlined,
  ),
  DashboardSyncArea.notifications => (
    label: l10n.notifications,
    description: l10n.syncNotificationsDescription,
    icon: Icons.notifications_outlined,
  ),
  DashboardSyncArea.tabletUsers => (
    label: l10n.members,
    description: l10n.syncMembersDescription,
    icon: Icons.people_outline,
  ),
  DashboardSyncArea.ratings => (
    label: l10n.ratings,
    description: l10n.syncRatingsDescription,
    icon: Icons.star_outline,
  ),
  DashboardSyncArea.tasks => (
    label: l10n.teamTasks,
    description: l10n.syncTasksDescription,
    icon: Icons.checklist_outlined,
  ),
  DashboardSyncArea.achievements => (
    label: l10n.achievements,
    description: l10n.syncAchievementsDescription,
    icon: Icons.emoji_events_outlined,
  ),
  DashboardSyncArea.shelf => (
    label: l10n.myLibrary,
    description: l10n.syncShelfDescription,
    icon: Icons.bookmark_outline,
  ),
};
