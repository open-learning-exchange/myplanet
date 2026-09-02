import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/team_finances_screen.dart';

import '../support/widget_harness.dart';

/// A plain (non-leader) `membership` row, the shape
/// `TeamsRepositoryImpl.isMember` matches on.
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

TeamRow _transaction(String id, String type, int amount) => TeamRow(
  id: id,
  courses: const [],
  createdDate: 0,
  limit: 0,
  isPublic: false,
  isLeader: false,
  beginningBalance: 0,
  sales: 0,
  otherIncome: 0,
  wages: 0,
  otherExpenses: 0,
  startDate: 0,
  endDate: 0,
  updatedDate: 0,
  date: 0,
  amount: amount,
  type: type,
  isUpdated: false,
);

void main() {
  testWidgets('summary displays totals from the loaded transactions', (
    tester,
  ) async {
    final rows = [
      TransactionRow(row: _transaction('credit', 'credit', 125), balance: 125),
      TransactionRow(row: _transaction('debit', 'debit', 40), balance: 85),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamFinancesScreen(teamId: 'team-1'),
        overrides: [
          teamTransactionsProvider.overrideWith((ref, params) {
            return Stream.value(rows);
          }),
          teamMembershipsProvider.overrideWith((ref) => Stream.value(const {})),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('125'), findsOneWidget);
    expect(find.text('40'), findsOneWidget);
    expect(find.text('85'), findsOneWidget);
    // A positive balance carries no caution.
    expect(find.text('The current balance is negative!'), findsNothing);
  });

  // `EnterprisesFinancesFragment.bindHeader` shows `balance_caution` when
  // `FinanceHeaderState.isCautionVisible`, which the ViewModel sets from
  // `total < 0`.
  testWidgets('a negative balance shows the caution line', (tester) async {
    final rows = [
      TransactionRow(row: _transaction('credit', 'credit', 10), balance: 10),
      TransactionRow(row: _transaction('debit', 'debit', 40), balance: -30),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamFinancesScreen(teamId: 'team-1'),
        overrides: [
          teamTransactionsProvider.overrideWith(
            (ref, params) => Stream.value(rows),
          ),
          teamMembershipsProvider.overrideWith((ref) => Stream.value(const {})),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('-30'), findsOneWidget);
    expect(find.text('The current balance is negative!'), findsOneWidget);
  });

  // `canManage` is `isMemberFlow` on the team path — `isMember`, not
  // `isTeamLeader`. A plain member may add a transaction.
  testWidgets('a plain member sees the add-transaction button', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamFinancesScreen(teamId: 'team-1'),
        overrides: [
          teamTransactionsProvider.overrideWith(
            (ref, params) => Stream.value(const <TransactionRow>[]),
          ),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value({'team-1': _membership('team-1')}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(FloatingActionButton), findsOneWidget);
    expect(find.text('Add Transaction'), findsOneWidget);
  });

  testWidgets('a non-member sees no add-transaction button', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamFinancesScreen(teamId: 'team-1'),
        overrides: [
          teamTransactionsProvider.overrideWith(
            (ref, params) => Stream.value(const <TransactionRow>[]),
          ),
          teamMembershipsProvider.overrideWith((ref) => Stream.value(const {})),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(FloatingActionButton), findsNothing);
  });
}
