import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/events_provider.dart';
import 'package:myplanet/ui/events/events_screen.dart';

import '../support/widget_harness.dart';

void main() {
  testWidgets('shows cached meetups and pending-upload state', (tester) async {
    final row = MeetupRow(
      id: 'meetup-1',
      title: 'Community lesson',
      meetupLocation: 'Library',
      startDate: DateTime(2026, 8, 3).millisecondsSinceEpoch,
      endDate: 0,
      createdDate: 0,
      recurringNumber: 10,
      updated: true,
    );
    await tester.pumpWidget(
      wrapScreen(
        const EventsScreen(),
        overrides: [
          eventsProvider.overrideWith((ref) => Stream.value([row])),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Meetups'), findsOneWidget);
    expect(find.text('Community lesson'), findsOneWidget);
    expect(find.textContaining('Library'), findsOneWidget);
    expect(find.byIcon(Icons.cloud_upload_outlined), findsOneWidget);
    expect(find.text('Add meetup'), findsOneWidget);
  });

  testWidgets('shows the offline empty state', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const EventsScreen(),
        overrides: [
          eventsProvider.overrideWith((ref) => Stream.value(const [])),
        ],
      ),
    );
    await tester.pump();

    expect(find.text('No meetups yet'), findsOneWidget);
  });
}
