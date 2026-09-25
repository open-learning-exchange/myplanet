import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/resources_providers.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/team_resources_screen.dart';

import '../support/widget_harness.dart';

class _MockTeamResourceActions extends Mock implements TeamResourceActions {}

void main() {
  testWidgets('shows the empty state when no resources are linked', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamResourcesScreen(teamId: 'team-1'),
        overrides: [
          teamResourcesProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <MyLibraryRow>[])),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No resources linked to this team'), findsOneWidget);
    // A non-leader sees no add button.
    expect(find.byType(FloatingActionButton), findsNothing);
  });

  testWidgets('renders the linked resources with title and description', (
    tester,
  ) async {
    final rows = [
      buildLibraryRow(
        id: 'r1',
        title: 'Algebra Basics',
        description: 'Intro to algebra',
        resourceId: 'res-1',
      ),
      buildLibraryRow(
        id: 'r2',
        title: null,
        description: null,
        resourceId: 'res-2',
      ),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamResourcesScreen(teamId: 'team-1'),
        overrides: [
          teamResourcesProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(rows)),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Algebra Basics'), findsOneWidget);
    expect(find.text('Intro to algebra'), findsOneWidget);
    // A null title falls back to the sentence-case placeholder.
    expect(find.text('Untitled resource'), findsOneWidget);
    expect(find.byIcon(Icons.description_outlined), findsNWidgets(2));
    // Non-leader: no remove buttons.
    expect(find.byIcon(Icons.link_off), findsNothing);
  });

  testWidgets('a leader sees the add-resource and remove buttons', (
    tester,
  ) async {
    final rows = [
      buildLibraryRow(id: 'r1', title: 'Algebra Basics', resourceId: 'res-1'),
    ];
    final memberships = {
      'team-1': TeamRow(
        id: 'team-1',
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
      ),
    };

    await tester.pumpWidget(
      wrapScreen(
        const TeamResourcesScreen(teamId: 'team-1'),
        overrides: [
          teamResourcesProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(rows)),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(memberships),
          ),
          teamResourceActionsProvider.overrideWith(
            (ref) => _MockTeamResourceActions(),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.link_off), findsOneWidget);
    expect(find.text('Add resource'), findsOneWidget);
    expect(find.byIcon(Icons.add_link), findsOneWidget);
  });

  testWidgets('the add-resource dialog opens with a cancel action', (
    tester,
  ) async {
    final linked = [
      buildLibraryRow(id: 'r1', title: 'Already Linked', resourceId: 'res-1'),
    ];
    final memberships = {
      'team-1': TeamRow(
        id: 'team-1',
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
      ),
    };
    final actions = _MockTeamResourceActions();

    await tester.pumpWidget(
      wrapScreen(
        const TeamResourcesScreen(teamId: 'team-1'),
        overrides: [
          teamResourcesProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(linked)),
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value(const <MyLibraryRow>[]),
          ),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(memberships),
          ),
          teamResourceActionsProvider.overrideWith((ref) => actions),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.add_link));
    await tester.pumpAndSettle();

    // The dialog opens with its title; with no available (unlinked) resources
    // it shows the "all already linked" message and a cancel affordance.
    expect(find.text('Add resource'), findsWidgets);
    expect(
      find.text('All available resources are already linked'),
      findsOneWidget,
    );
    expect(find.text('Cancel'), findsOneWidget);
  });
}
