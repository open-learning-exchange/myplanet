import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/ui/community/services_screen.dart';
import 'package:myplanet/ui/router.dart';

import '../../support/widget_harness.dart';

TeamRow _serviceRow({
  required String id,
  String? title,
  String? name,
  String? route,
}) => TeamRow(
  id: id,
  name: name,
  title: title,
  route: route,
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
  amount: 0,
  isUpdated: false,
);

void main() {
  testWidgets('shows the empty state when no services are linked', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ServicesScreen(),
        overrides: [
          teamLinksStreamProvider.overrideWith(
            (ref) => Stream.value(const <TeamRow>[]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No services available'), findsOneWidget);
    expect(find.byIcon(Icons.link), findsNothing);
  });

  testWidgets('lists each service with its title', (tester) async {
    final rows = [
      _serviceRow(
        id: 's1',
        title: 'Health clinic',
        route: 'https://clinic.example.org',
      ),
      _serviceRow(id: 's2', name: 'Library', route: '/teams/t/library'),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const ServicesScreen(),
        overrides: [
          teamLinksStreamProvider.overrideWith((ref) => Stream.value(rows)),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Health clinic'), findsOneWidget);
    // A row with no title falls back to its name.
    expect(find.text('Library'), findsOneWidget);
    expect(find.byIcon(Icons.link), findsNWidgets(2));
  });

  testWidgets('a title-less service falls back to Untitled team', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ServicesScreen(),
        overrides: [
          teamLinksStreamProvider.overrideWith(
            (ref) => Stream.value([
              _serviceRow(id: 's1', route: 'https://x.example.org'),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Untitled team'), findsOneWidget);
  });

  testWidgets('an internal route pushes the team screen', (tester) async {
    final rows = [
      _serviceRow(id: 's1', name: 'Library', route: '/teams/community/library'),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const ServicesScreen(),
        pushTargets: {
          '${Routes.teams}/library': (_) =>
              const Scaffold(body: Text('the team')),
        },
        overrides: [
          teamLinksStreamProvider.overrideWith((ref) => Stream.value(rows)),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Library'));
    await tester.pumpAndSettle();

    expect(find.text('the team'), findsOneWidget);
    // The external-URL snackbar does not fire for internal routes.
    expect(find.textContaining('Opening:'), findsNothing);
  });

  testWidgets('an external route navigates to the web view screen', (
    tester,
  ) async {
    final rows = [
      _serviceRow(
        id: 's1',
        title: 'Clinic',
        route: 'https://clinic.example.org',
      ),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const ServicesScreen(),
        overrides: [
          teamLinksStreamProvider.overrideWith((ref) => Stream.value(rows)),
        ],
        pushTargets: {
          '/web-view': (_) => const Scaffold(body: Text('web view target')),
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Clinic'));
    await tester.pumpAndSettle();

    // The web view screen is pushed.
    expect(find.text('web view target'), findsOneWidget);
  });
}
