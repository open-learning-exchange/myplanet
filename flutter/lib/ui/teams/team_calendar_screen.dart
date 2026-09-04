import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:table_calendar/table_calendar.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/events_provider.dart';
import '../router.dart';

/// Port of `ui/teams/TeamCalendarFragment.kt`.
///
/// Displays a calendar view with team meetups/events.
class TeamCalendarScreen extends ConsumerStatefulWidget {
  const TeamCalendarScreen({required this.teamId, super.key});
  final String teamId;

  @override
  ConsumerState<TeamCalendarScreen> createState() => _TeamCalendarScreenState();
}

class _TeamCalendarScreenState extends ConsumerState<TeamCalendarScreen> {
  CalendarFormat _calendarFormat = CalendarFormat.month;
  DateTime _focusedDay = DateTime.now();
  DateTime? _selectedDay;

  @override
  void initState() {
    super.initState();
    _selectedDay = _focusedDay;
  }

  List<MeetupRow> _getMeetupsForDay(DateTime day, List<MeetupRow> allMeetups) {
    return allMeetups.where((meetup) {
      final meetupDate = DateTime.fromMillisecondsSinceEpoch(meetup.startDate);
      return meetupDate.year == day.year &&
          meetupDate.month == day.month &&
          meetupDate.day == day.day;
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final allMeetups = ref.watch(eventsProvider).valueOrNull ?? [];
    final teamMeetups = allMeetups
        .where((m) => m.teamId == widget.teamId)
        .toList();

    final selectedMeetups = _selectedDay != null
        ? _getMeetupsForDay(_selectedDay!, teamMeetups)
        : <MeetupRow>[];

    return Scaffold(
      appBar: AppBar(title: Text(l10n.teamCalendar)),
      body: Column(
        children: [
          TableCalendar<MeetupRow>(
            firstDay: DateTime.utc(2020, 1, 1),
            lastDay: DateTime.utc(2030, 12, 31),
            focusedDay: _focusedDay,
            calendarFormat: _calendarFormat,
            selectedDayPredicate: (day) => isSameDay(_selectedDay, day),
            eventLoader: (day) => _getMeetupsForDay(day, teamMeetups),
            startingDayOfWeek: StartingDayOfWeek.monday,
            calendarStyle: CalendarStyle(
              markerDecoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primary,
                shape: BoxShape.circle,
              ),
              selectedDecoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primary,
                shape: BoxShape.circle,
              ),
              todayDecoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primaryContainer,
                shape: BoxShape.circle,
              ),
            ),
            headerStyle: HeaderStyle(
              formatButtonVisible: true,
              titleCentered: true,
              formatButtonShowsNext: false,
              formatButtonDecoration: BoxDecoration(
                border: Border.all(
                  color: Theme.of(context).colorScheme.outline,
                ),
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            onDaySelected: (selectedDay, focusedDay) {
              setState(() {
                _selectedDay = selectedDay;
                _focusedDay = focusedDay;
              });
            },
            onFormatChanged: (format) {
              setState(() {
                _calendarFormat = format;
              });
            },
            onPageChanged: (focusedDay) {
              _focusedDay = focusedDay;
            },
          ),
          const Divider(height: 1),
          Expanded(
            child: selectedMeetups.isEmpty
                ? Center(child: Text(l10n.noMeetupsOnDate))
                : ListView.separated(
                    padding: const EdgeInsets.all(12),
                    itemCount: selectedMeetups.length,
                    separatorBuilder: (context, index) =>
                        const SizedBox(height: 8),
                    itemBuilder: (context, index) {
                      final meetup = selectedMeetups[index];
                      return _MeetupCard(meetup: meetup);
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

class _MeetupCard extends StatelessWidget {
  const _MeetupCard({required this.meetup});
  final MeetupRow meetup;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final startDate = DateTime.fromMillisecondsSinceEpoch(meetup.startDate);
    final endDate = DateTime.fromMillisecondsSinceEpoch(meetup.endDate);

    return Card(
      child: ListTile(
        leading: const CircleAvatar(child: Icon(Icons.event)),
        title: Text(meetup.title ?? l10n.untitledMeetup),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (meetup.meetupLocation?.isNotEmpty == true)
              Text(meetup.meetupLocation!),
            Text(
              '${_formatTime(startDate)} - ${_formatTime(endDate)}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.push('${Routes.events}/${meetup.id}'),
      ),
    );
  }

  String _formatTime(DateTime date) {
    return '${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
  }
}
