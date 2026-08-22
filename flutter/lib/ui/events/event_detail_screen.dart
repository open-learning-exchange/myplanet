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
        _Value(
          label: l10n.startDate,
          value: _dateTime(context, row.startDate, row.startTime),
        ),
        _Value(
          label: l10n.endDate,
          value: _dateTime(context, row.endDate, row.endTime),
        ),
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
            builder: (_) => AlertDialog(
              title: Text(l10n.editMeetup),
              content: SizedBox(
                width: 480,
                child: _MeetupEditor(existing: row),
              ),
            ),
          ),
          icon: const Icon(Icons.edit),
          label: Text(l10n.editMeetup),
        ),
      ],
    );
  }

  String? _dateTime(BuildContext context, int millis, String? time) {
    if (millis == 0 && (time == null || time.isEmpty)) return null;
    final date = millis == 0
        ? ''
        : MaterialLocalizations.of(
            context,
          ).formatMediumDate(DateTime.fromMillisecondsSinceEpoch(millis));
    return [date, time].where((value) => value?.isNotEmpty == true).join(' · ');
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
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.startDate),
            subtitle: Text(_dateLabel(context, startDate)),
            trailing: const Icon(Icons.calendar_month),
            onTap: () => _pickDate(start: true),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.endDate),
            subtitle: Text(_dateLabel(context, endDate)),
            trailing: const Icon(Icons.event),
            onTap: () => _pickDate(start: false),
          ),
          Row(
            children: [
              Expanded(
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.startTime),
                  subtitle: Text(
                    startTime.text.isEmpty ? l10n.timeNotSet : startTime.text,
                  ),
                  onTap: () => _pickTime(startTime),
                ),
              ),
              Expanded(
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.endTime),
                  subtitle: Text(
                    endTime.text.isEmpty ? l10n.timeNotSet : endTime.text,
                  ),
                  onTap: () => _pickTime(endTime),
                ),
              ),
            ],
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

  String _dateLabel(BuildContext context, int millis) => millis == 0
      ? AppLocalizations.of(context).dateNotSet
      : MaterialLocalizations.of(
          context,
        ).formatMediumDate(DateTime.fromMillisecondsSinceEpoch(millis));

  Future<void> _pickDate({required bool start}) async {
    final current = start ? startDate : endDate;
    final picked = await showDatePicker(
      context: context,
      initialDate: current == 0
          ? DateTime.now()
          : DateTime.fromMillisecondsSinceEpoch(current),
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
    );
    if (picked == null || !mounted) return;
    setState(() {
      final value = DateUtils.dateOnly(picked).millisecondsSinceEpoch;
      if (start) {
        startDate = value;
        if (endDate != 0 && endDate < value) endDate = value;
      } else {
        endDate = value;
      }
    });
  }

  Future<void> _pickTime(TextEditingController controller) async {
    final parts = controller.text.split(':');
    final initial = parts.length == 2
        ? TimeOfDay(
            hour: int.tryParse(parts[0]) ?? 0,
            minute: int.tryParse(parts[1]) ?? 0,
          )
        : TimeOfDay.now();
    final picked = await showTimePicker(context: context, initialTime: initial);
    if (picked == null || !mounted) return;
    setState(() {
      controller.text =
          '${picked.hour.toString().padLeft(2, '0')}:'
          '${picked.minute.toString().padLeft(2, '0')}';
    });
  }
}
