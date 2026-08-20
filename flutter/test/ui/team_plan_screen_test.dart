import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/team_plan_screen.dart';

import '../support/widget_harness.dart';

TeamRow _team({
  String id = 'team-1',
  String? type,
  String? description,
  String? services,
  String? rules,
  int createdDate = 0,
  bool isLeader = false,
}) => TeamRow(
  id: id,
  type: type,
  description: description,
  services: services,
  rules: rules,
  createdDate: createdDate,
  courses: const [],
  limit: 0,
  isPublic: false,
  isLeader: isLeader,
  beginningBalance: 0,
  sales: 0,
  otherIncome: 0,
  wages: 0,
  otherExpenses: 0,
  startDate: 0,
  endDate: 0,
  updatedDate: 0,
  date: 0,
  amount: 0,
  isUpdated: false,
);

const _emptyMemberships = <String, TeamRow>{};

void main() {
  testWidgets('shows "Team not found" when the team is null', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamPlanScreen(teamId: 'missing'),
        overrides: [
          teamProvider('missing').overrideWith((ref) async => null),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(_emptyMemberships),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Team not found'), findsOneWidget);
  });

  testWidgets('renders the team plan sections for a team', (tester) async {
    final team = _team(
      description: 'We learn together every week.',
      createdDate: DateTime(2024, 1, 15).millisecondsSinceEpoch,
    );

    await tester.pumpWidget(
      wrapScreen(
        const TeamPlanScreen(teamId: 'team-1'),
        overrides: [
          teamProvider('team-1').overrideWith((ref) async => team),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(_emptyMemberships),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // A team (type != enterprise) labels its description "What is your team's plan?".
    expect(find.text("What is your team's plan?"), findsOneWidget);
    expect(find.text('We learn together every week.'), findsOneWidget);
    // The created date is rendered as d/m/yyyy.
    expect(find.textContaining('Created on: 15/1/2024'), findsOneWidget);
    // A non-leader sees no edit button.
    expect(find.byType(FloatingActionButton), findsNothing);
  });

  testWidgets('renders mission, services, and rules for an enterprise', (
    tester,
  ) async {
    final team = _team(
      type: 'enterprise',
      description: 'Our mission statement.',
      services: 'We offer tutoring.',
      rules: 'Be kind.',
    );

    await tester.pumpWidget(
      wrapScreen(
        const TeamPlanScreen(teamId: 'team-1'),
        overrides: [
          teamProvider('team-1').overrideWith((ref) async => team),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(_emptyMemberships),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Mission & Services'), findsOneWidget);
    expect(find.text('Our mission statement.'), findsOneWidget);
    expect(find.text('Services'), findsOneWidget);
    expect(find.text('We offer tutoring.'), findsOneWidget);
    expect(find.text('Rules'), findsOneWidget);
    expect(find.text('Be kind.'), findsOneWidget);
  });

  testWidgets('shows the no-plan empty state when nothing is defined', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamPlanScreen(teamId: 'team-1'),
        overrides: [
          teamProvider('team-1').overrideWith((ref) async => _team()),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(_emptyMemberships),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('This team has no plan defined.'), findsOneWidget);
  });

  testWidgets('shows the no-mission empty state for an empty enterprise', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamPlanScreen(teamId: 'team-1'),
        overrides: [
          teamProvider(
            'team-1',
          ).overrideWith((ref) async => _team(type: 'enterprise')),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(_emptyMemberships),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('The enterprise has no mission & services.'),
      findsOneWidget,
    );
  });

  testWidgets('a leader sees the edit-plan button', (tester) async {
    final team = _team(description: 'A plan.');
    final memberships = {'team-1': _team(id: 'team-1', isLeader: true)};

    await tester.pumpWidget(
      wrapScreen(
        const TeamPlanScreen(teamId: 'team-1'),
        overrides: [
          teamProvider('team-1').overrideWith((ref) async => team),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(memberships),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    final fab = find.byType(FloatingActionButton);
    expect(fab, findsOneWidget);
    expect(find.text('Edit Plan'), findsOneWidget);
  });
}
