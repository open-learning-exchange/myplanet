import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/team_reports_screen.dart';

import '../support/widget_harness.dart';

class _MockTeamReportActions extends Mock implements TeamReportActions {}

TeamRow _membership(String teamId, {bool isLeader = false}) => TeamRow(
  id: teamId,
  courses: const [],
  createdDate: 0,
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
    final memberships = {'team-1': _membership('team-1', isLeader: true)};
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

  // `EnterprisesReportsAdapter.onBindViewHolder` binds nine value rows, not
  // four: the five figures a report is authored from, each followed by the
  // total it feeds, plus the created/updated footer.
  testWidgets('a report card shows the authored figures, not just the totals', (
    tester,
  ) async {
    final reports = [
      _report(
        id: 'r1',
        beginningBalance: 100,
        sales: 50,
        otherIncome: 10,
        wages: 20,
        otherExpenses: 5,
        startDate: DateTime(2026, 1, 1).millisecondsSinceEpoch,
        endDate: DateTime(2026, 1, 31).millisecondsSinceEpoch,
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

    for (final label in const [
      'Beginning balance',
      'Sales',
      'Other income',
      'Total income',
      'Wages',
      'Other expenses',
      'Total expenses',
      'Profit / loss',
      'Ending balance',
    ]) {
      expect(find.text(label), findsOneWidget, reason: 'missing row: $label');
    }
    // The five authored figures, alongside the four totals the card already had.
    expect(find.text('100'), findsOneWidget);
    expect(find.text('50'), findsOneWidget);
    expect(find.text('10'), findsOneWidget);
    expect(find.text('20'), findsOneWidget);
    expect(find.text('5'), findsOneWidget);
    expect(
      find.textContaining('Report created on:'),
      findsOneWidget,
      reason: 'the createUpdate footer is missing',
    );
  });

  // `canManage` is `isMemberFlow` on the team path — `isMember`, not
  // `isTeamLeader`.
  testWidgets('a plain member may add and edit reports', (tester) async {
    final reports = [
      _report(
        id: 'r1',
        description: 'January summary',
        startDate: DateTime(2026, 1, 1).millisecondsSinceEpoch,
        endDate: DateTime(2026, 1, 31).millisecondsSinceEpoch,
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
            (ref) => Stream.value({'team-1': _membership('team-1')}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Add report'), findsOneWidget);
    expect(find.text('Archive'), findsOneWidget);
  });

  // The Kotlin filename pattern is `EEE_MMM_dd_yyyy`.
  test('reportExportDateSuffix matches the Kotlin filename pattern', () {
    expect(reportExportDateSuffix(DateTime(2026, 8, 20)), 'Thu_Aug_20_2026');
    // Single-digit days are zero-padded by `dd`.
    expect(reportExportDateSuffix(DateTime(2026, 1, 4)), 'Sun_Jan_04_2026');
  });
}
