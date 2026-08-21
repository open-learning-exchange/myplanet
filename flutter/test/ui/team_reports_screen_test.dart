import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/team_reports_screen.dart';

import '../support/widget_harness.dart';

class _MockTeamReportActions extends Mock implements TeamReportActions {}

TeamRow _leaderMembership(String teamId) => TeamRow(
  id: teamId,
  courses: const [],
  createdDate: 0,
  limit: 0,
  isPublic: false,
  isLeader: true,
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

TeamRow _report({
  String id = 'r1',
  String? description,
  int beginningBalance = 100,
  int sales = 50,
  int otherIncome = 10,
  int wages = 20,
  int otherExpenses = 5,
  required int startDate,
  required int endDate,
}) => TeamRow(
  id: id,
  description: description,
  beginningBalance: beginningBalance,
  sales: sales,
  otherIncome: otherIncome,
  wages: wages,
  otherExpenses: otherExpenses,
  startDate: startDate,
  endDate: endDate,
  courses: const [],
  createdDate: 0,
  limit: 0,
  isPublic: false,
  isLeader: false,
  updatedDate: 0,
  date: 0,
  amount: 0,
  isUpdated: false,
);

void main() {
  testWidgets('shows the empty state when no reports exist', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamReportsScreen(teamId: 'team-1'),
        overrides: [
          teamReportsProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamRow>[])),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No financial reports yet'), findsOneWidget);
    // A non-leader sees no add-report FAB.
    expect(find.byType(FloatingActionButton), findsNothing);
  });

  testWidgets('renders a report with totals and the export button', (
    tester,
  ) async {
    final start = DateTime(2026, 1, 1).millisecondsSinceEpoch;
    final end = DateTime(2026, 1, 31).millisecondsSinceEpoch;
    final reports = [
      _report(
        id: 'r1',
        description: 'January summary',
        beginningBalance: 100,
        sales: 50,
        otherIncome: 10,
        wages: 20,
        otherExpenses: 5,
        startDate: start,
        endDate: end,
      ),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamReportsScreen(teamId: 'team-1'),
        overrides: [
          teamReportsProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(reports)),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('January summary'), findsOneWidget);
    // totalIncome = sales + otherIncome = 50 + 10 = 60
    expect(find.text('60'), findsOneWidget);
    // totalExpenses = wages + otherExpenses = 20 + 5 = 25
    expect(find.text('25'), findsOneWidget);
    // profitLoss = 60 - 25 = 35
    expect(find.text('35'), findsOneWidget);
    // endingBalance = beginningBalance(100) + profitLoss(35) = 135
    expect(find.text('135'), findsOneWidget);
    expect(find.text('Export CSV'), findsOneWidget);
    // Non-leader: no edit/archive actions.
    expect(find.text('Archive'), findsNothing);
  });

  testWidgets('a leader sees the add-report FAB and archive action', (
    tester,
  ) async {
    final start = DateTime(2026, 1, 1).millisecondsSinceEpoch;
    final end = DateTime(2026, 1, 31).millisecondsSinceEpoch;
    final reports = [
      _report(
        id: 'r1',
        description: 'January summary',
        startDate: start,
        endDate: end,
      ),
    ];
    final memberships = {'team-1': _leaderMembership('team-1')};
    final actions = _MockTeamReportActions();
    when(() => actions.archive(any())).thenAnswer((_) async => true);

    await tester.pumpWidget(
      wrapScreen(
        const TeamReportsScreen(teamId: 'team-1'),
        overrides: [
          teamReportsProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(reports)),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(memberships),
          ),
          teamReportActionsProvider.overrideWith((ref) => actions),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Add report'), findsOneWidget);
    expect(find.byIcon(Icons.add_chart), findsOneWidget);
    expect(find.text('Archive'), findsOneWidget);

    await tester.tap(find.text('Archive'));
    await tester.pump();

    verify(() => actions.archive('r1')).called(1);
  });
}
