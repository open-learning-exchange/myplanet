import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/events_provider.dart';
import '../router.dart';

/// Flutter port of the meetup list hosted by the Kotlin teams UI.
class EventsScreen extends ConsumerWidget {
  const EventsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final events = ref.watch(eventsProvider);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.meetups),
        actions: [
          IconButton(
            tooltip: l10n.syncPendingMeetups,
            onPressed: () => ref.read(eventsActionsProvider).queuePending(),
            icon: const Icon(Icons.sync),
          ),
        ],
      ),
      body: events.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.meetupsUnavailable)),
        data: (rows) => rows.isEmpty
            ? Center(child: Text(l10n.noMeetups))
            : ListView.separated(
                padding: const EdgeInsets.all(12),
                itemCount: rows.length,
                separatorBuilder: (_, _) => const SizedBox(height: 6),
                itemBuilder: (context, index) {
                  final row = rows[index];
                  final date = row.startDate == 0
                      ? l10n.dateNotSet
                      : MaterialLocalizations.of(context).formatMediumDate(
                          DateTime.fromMillisecondsSinceEpoch(row.startDate),
                        );
                  return Card(
                    child: ListTile(
                      leading: Icon(
                        row.updated ? Icons.cloud_upload_outlined : Icons.event,
                      ),
                      title: Text(row.title ?? l10n.untitledMeetup),
                      subtitle: Text(
                        [date, row.meetupLocation]
                            .where((value) => value?.isNotEmpty == true)
                            .join(' · '),
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => context.push('${Routes.events}/${row.id}'),
                    ),
                  );
                },
              ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('${Routes.events}/new'),
        icon: const Icon(Icons.add),
        label: Text(l10n.addMeetup),
      ),
    );
  }
}
