import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/events_provider.dart';
import 'package:myplanet/ui/teams/team_calendar_screen.dart';

import '../support/widget_harness.dart';

MeetupRow _meetup({
  String id = 'm1',
  String? title,
  String? meetupLocation,
  required int startDate,
  required int endDate,
  String? teamId,
}) => MeetupRow(
  id: id,
  title: title,
  meetupLocation: meetupLocation,
  startDate: startDate,
  endDate: endDate,
  teamId: teamId,
  createdDate: 0,
  recurringNumber: 0,
  updated: false,
);

void main() {
  testWidgets('shows the no-meetups message for a day with none', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamCalendarScreen(teamId: 'team-1'),
        overrides: [
          eventsProvider.overrideWith(
            (ref) => Stream.value(const <MeetupRow>[]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No meetups on this date'), findsOneWidget);
  });

  testWidgets('shows meetups for the selected team on the focused day', (
    tester,
  ) async {
    final today = DateTime.now();
    final start = DateTime(today.year, today.month, today.day, 9, 0);
    final end = DateTime(today.year, today.month, today.day, 10, 0);
    final meetups = [
      MeetupRow(
        id: 'e1',
        title: 'Standup',
        meetupLocation: 'Library',
        startDate: start.millisecondsSinceEpoch,
        endDate: end.millisecondsSinceEpoch,
        teamId: 'team-1',
        createdDate: 0,
        recurringNumber: 0,
        updated: false,
      ),
      // A meetup for a different team should not appear.
      MeetupRow(
        id: 'e2',
        title: 'Other Team',
        startDate: start.millisecondsSinceEpoch,
        endDate: end.millisecondsSinceEpoch,
        teamId: 'team-2',
        createdDate: 0,
        recurringNumber: 0,
        updated: false,
      ),
      // A meetup with a null title falls back to the placeholder.
      MeetupRow(
        id: 'e3',
        title: null,
        startDate: start.millisecondsSinceEpoch,
        endDate: end.millisecondsSinceEpoch,
        teamId: 'team-1',
        createdDate: 0,
        recurringNumber: 0,
        updated: false,
      ),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamCalendarScreen(teamId: 'team-1'),
        overrides: [
          eventsProvider.overrideWith((ref) => Stream.value(meetups)),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Standup'), findsOneWidget);
    expect(find.text('Library'), findsOneWidget);
    // A null title uses the placeholder.
    expect(find.text('Untitled meetup'), findsOneWidget);
    // The other team's meetup is filtered out.
    expect(find.text('Other Team'), findsNothing);
  });

  testWidgets('renders the formatted time range', (tester) async {
    final today = DateTime.now();
    final start = DateTime(today.year, today.month, today.day, 9, 5);
    final end = DateTime(today.year, today.month, today.day, 10, 30);
    final meetups = [
      _meetup(
        id: 'e1',
        title: 'Standup',
        startDate: start.millisecondsSinceEpoch,
        endDate: end.millisecondsSinceEpoch,
        teamId: 'team-1',
      ),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamCalendarScreen(teamId: 'team-1'),
        overrides: [
          eventsProvider.overrideWith((ref) => Stream.value(meetups)),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('09:05 - 10:30'), findsOneWidget);
  });
}
