import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/events_provider.dart';
import '../../providers/sync_state.dart';
import '../router.dart';

/// Flutter port of the meetup list hosted by the Kotlin teams UI.
class EventsScreen extends ConsumerWidget {
  const EventsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final events = ref.watch(eventsProvider);
    final sync = ref.watch(eventsSyncProvider);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.meetups),
        actions: [
          IconButton(
            tooltip: l10n.syncMeetups,
            onPressed: sync is SyncRunning
                ? null
                : () async {
                    await ref.read(eventsSyncProvider.notifier).sync();
                    await ref.read(eventsActionsProvider).queuePending();
                  },
            icon: sync is SyncRunning
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.sync),
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
            child: Row(
              children: [
                Expanded(
                  child: SearchBar(
                    hintText: l10n.searchMeetups,
                    leading: const Icon(Icons.search),
                    onChanged: (value) =>
                        ref.read(eventsSearchProvider.notifier).state = value,
                  ),
                ),
                const SizedBox(width: 8),
                PopupMenuButton<EventsSort>(
                  tooltip: l10n.sortMeetups,
                  initialValue: ref.watch(eventsSortProvider),
                  onSelected: (value) =>
                      ref.read(eventsSortProvider.notifier).state = value,
                  itemBuilder: (_) => [
                    PopupMenuItem(
                      value: EventsSort.dateDescending,
                      child: Text(l10n.newestFirst),
                    ),
                    PopupMenuItem(
                      value: EventsSort.dateAscending,
                      child: Text(l10n.oldestFirst),
                    ),
                    PopupMenuItem(
                      value: EventsSort.titleAscending,
                      child: Text(l10n.titleAscending),
                    ),
                    PopupMenuItem(
                      value: EventsSort.titleDescending,
                      child: Text(l10n.titleDescending),
                    ),
                  ],
                  icon: const Icon(Icons.sort),
                ),
              ],
            ),
          ),
          if (sync is SyncRunning)
            LinearProgressIndicator(
              value: sync.progress.total == 0 ? null : sync.progress.fraction,
            ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () => ref.read(eventsSyncProvider.notifier).sync(),
              child: events.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (_, _) => Center(child: Text(l10n.meetupsUnavailable)),
                data: (rows) => rows.isEmpty
                    ? ListView(
                        physics: const AlwaysScrollableScrollPhysics(),
                        children: [
                          SizedBox(
                            height: 300,
                            child: Center(child: Text(l10n.noMeetups)),
                          ),
                        ],
                      )
                    : ListView.separated(
                        physics: const AlwaysScrollableScrollPhysics(),
                        padding: const EdgeInsets.all(12),
                        itemCount: rows.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 6),
                        itemBuilder: (context, index) {
                          final row = rows[index];
                          final date = row.startDate == 0
                              ? l10n.dateNotSet
                              : MaterialLocalizations.of(
                                  context,
                                ).formatMediumDate(
                                  DateTime.fromMillisecondsSinceEpoch(
                                    row.startDate,
                                  ),
                                );
                          return Card(
                            child: ListTile(
                              leading: Icon(
                                row.updated
                                    ? Icons.cloud_upload_outlined
                                    : Icons.event,
                              ),
                              title: Text(row.title ?? l10n.untitledMeetup),
                              subtitle: Text(
                                [date, row.meetupLocation]
                                    .where((value) => value?.isNotEmpty == true)
                                    .join(' · '),
                              ),
                              trailing: const Icon(Icons.chevron_right),
                              onTap: () =>
                                  context.push('${Routes.events}/${row.id}'),
                            ),
                          );
                        },
                      ),
              ),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('${Routes.events}/new'),
        icon: const Icon(Icons.add),
        label: Text(l10n.addMeetup),
      ),
    );
  }
}
