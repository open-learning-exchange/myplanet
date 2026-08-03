import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/events_provider.dart';

/// Port of `ui/events/EventsDetailFragment.kt`, including local editing and
/// join/leave behavior.
class EventDetailScreen extends ConsumerWidget {
  const EventDetailScreen({required this.meetupId, super.key});
  final String meetupId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final meetup = ref.watch(meetupProvider(meetupId));
    return Scaffold(
      appBar: AppBar(title: Text(l10n.meetupDetails)),
      body: meetup.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.meetupsUnavailable)),
        data: (row) => row == null
            ? Center(child: Text(l10n.meetupNotFound))
            : _Details(row: row),
      ),
    );
  }
}

class _Details extends ConsumerWidget {
  const _Details({required this.row});
  final MeetupRow row;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final joined = row.userId?.isNotEmpty == true;
    final eventEnded =
        row.endDate > 0 &&
        DateTime.now().millisecondsSinceEpoch >
            row.endDate + const Duration(days: 1).inMilliseconds - 1;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          row.title ?? l10n.untitledMeetup,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 16),
        _Value(label: l10n.createdBy, value: row.creator),
        _Value(label: l10n.category, value: row.category),
        _Value(label: l10n.location, value: row.meetupLocation),
        _Value(label: l10n.link, value: row.meetupLink),
        _Value(label: l10n.recurring, value: row.recurring),
        _Value(label: l10n.description, value: row.description),
        const SizedBox(height: 20),
        FilledButton.icon(
          onPressed: eventEnded
              ? null
              : () => ref.read(eventsActionsProvider).toggleAttendance(row),
          icon: Icon(joined ? Icons.event_busy : Icons.event_available),
          label: Text(joined ? l10n.leaveMeetup : l10n.joinMeetup),
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: () => showDialog<void>(
            context: context,
            builder: (_) => _MeetupEditor(existing: row),
          ),
          icon: const Icon(Icons.edit),
          label: Text(l10n.editMeetup),
        ),
      ],
    );
  }
}

class _Value extends StatelessWidget {
  const _Value({required this.label, this.value});
  final String label;
  final String? value;

  @override
  Widget build(BuildContext context) {
    if (value == null || value!.isEmpty) return const SizedBox.shrink();
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(label),
      subtitle: Text(value!),
    );
  }
}

class NewMeetupScreen extends StatelessWidget {
  const NewMeetupScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(AppLocalizations.of(context).addMeetup)),
    body: const Padding(padding: EdgeInsets.all(16), child: _MeetupEditor()),
  );
}

class _MeetupEditor extends ConsumerStatefulWidget {
  const _MeetupEditor({this.existing});
  final MeetupRow? existing;

  @override
  ConsumerState<_MeetupEditor> createState() => _MeetupEditorState();
}

class _MeetupEditorState extends ConsumerState<_MeetupEditor> {
  late final title = TextEditingController(text: widget.existing?.title);
  late final description = TextEditingController(
    text: widget.existing?.description,
  );
  late final location = TextEditingController(
    text: widget.existing?.meetupLocation,
  );
  late final link = TextEditingController(text: widget.existing?.meetupLink);
  late final startTime = TextEditingController(
    text: widget.existing?.startTime,
  );
  late final endTime = TextEditingController(text: widget.existing?.endTime);
  late int startDate = widget.existing?.startDate ?? 0;
  late int endDate = widget.existing?.endDate ?? 0;
  late String recurring = widget.existing?.recurring ?? 'none';

  @override
  void dispose() {
    title.dispose();
    description.dispose();
    location.dispose();
    link.dispose();
    startTime.dispose();
    endTime.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: title,
            decoration: InputDecoration(labelText: l10n.title),
          ),
          TextField(
            controller: description,
            decoration: InputDecoration(labelText: l10n.description),
            maxLines: 3,
          ),
          TextField(
            controller: location,
            decoration: InputDecoration(labelText: l10n.location),
          ),
          TextField(
            controller: link,
            decoration: InputDecoration(labelText: l10n.link),
          ),
          TextField(
            controller: startTime,
            decoration: InputDecoration(labelText: l10n.startTime),
          ),
          TextField(
            controller: endTime,
            decoration: InputDecoration(labelText: l10n.endTime),
          ),
          DropdownButtonFormField<String>(
            initialValue: recurring,
            decoration: InputDecoration(labelText: l10n.recurring),
            items: [
              DropdownMenuItem(value: 'none', child: Text(l10n.none)),
              DropdownMenuItem(value: 'daily', child: Text(l10n.daily)),
              DropdownMenuItem(value: 'weekly', child: Text(l10n.weekly)),
            ],
            onChanged: (value) => recurring = value ?? 'none',
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () async {
              final id = await ref
                  .read(eventsActionsProvider)
                  .save(
                    id: widget.existing?.id,
                    title: title.text,
                    description: description.text,
                    startDate: startDate,
                    endDate: endDate,
                    startTime: startTime.text,
                    endTime: endTime.text,
                    location: location.text,
                    link: link.text,
                    recurring: recurring,
                  );
              if (!context.mounted) return;
              if (id == null) {
                ScaffoldMessenger.of(
                  context,
                ).showSnackBar(SnackBar(content: Text(l10n.titleRequired)));
              } else {
                Navigator.of(context).pop();
              }
            },
            child: Text(l10n.save),
          ),
        ],
      ),
    );
  }
}
