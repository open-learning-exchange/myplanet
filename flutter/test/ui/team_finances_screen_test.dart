import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/team_finances_screen.dart';

import '../support/widget_harness.dart';

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
  });
}
