import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/team_members_screen.dart';

import '../support/widget_harness.dart';

class _MockTeamMembershipActions extends Mock
    implements TeamMembershipActions {}

TeamRow _member({String id = 'm1', String? userId, bool isLeader = false}) =>
    TeamRow(
      id: id,
      userId: userId,
      isLeader: isLeader,
      courses: const [],
      createdDate: 0,
      limit: 0,
      isPublic: false,
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

void main() {
  testWidgets('shows the members empty state for a non-leader', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamMembersScreen(teamId: 'team-1'),
        overrides: [
          teamMembersProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamRow>[])),
          teamRequestsProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamRow>[])),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No members yet'), findsOneWidget);
    // A non-leader sees only the Members tab.
    expect(find.text('Members'), findsWidgets);
    expect(find.text('Join requests'), findsNothing);
  });

  testWidgets('renders members with initials and the leader star', (
    tester,
  ) async {
    final members = [
      _member(id: 'm1', userId: 'alice'),
      _member(id: 'm2', userId: 'bob', isLeader: true),
      _member(id: 'm3', userId: null),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamMembersScreen(teamId: 'team-1'),
        overrides: [
          teamMembersProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(members)),
          teamRequestsProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamRow>[])),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('alice'), findsOneWidget);
    expect(find.text('bob'), findsOneWidget);
    expect(find.text('Leader'), findsOneWidget);
    expect(find.byIcon(Icons.star), findsOneWidget);
    // A null userId falls back to "Unknown member".
    expect(find.text('Unknown member'), findsOneWidget);
  });

  testWidgets('a leader sees the Join requests tab and request actions', (
    tester,
  ) async {
    final requests = [_member(id: 'req-1', userId: 'carol')];
    final memberships = {'team-1': _leaderMembership('team-1')};

    await tester.pumpWidget(
      wrapScreen(
        const TeamMembersScreen(teamId: 'team-1'),
        overrides: [
          teamMembersProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamRow>[])),
          teamRequestsProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(requests)),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(memberships),
          ),
          teamMembershipActionsProvider.overrideWith(
            (ref) => _MockTeamMembershipActions(),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The leader-only tab is present.
    expect(find.text('Join requests'), findsOneWidget);

    // Switch to the requests tab and verify the actions render.
    await tester.tap(find.text('Join requests'));
    await tester.pumpAndSettle();

    expect(find.text('carol'), findsOneWidget);
    expect(find.byIcon(Icons.close), findsOneWidget);
    expect(find.byIcon(Icons.check), findsOneWidget);
  });

  testWidgets('the requests tab shows its empty state for a leader', (
    tester,
  ) async {
    final memberships = {'team-1': _leaderMembership('team-1')};

    await tester.pumpWidget(
      wrapScreen(
        const TeamMembersScreen(teamId: 'team-1'),
        overrides: [
          teamMembersProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamRow>[])),
          teamRequestsProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamRow>[])),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(memberships),
          ),
          teamMembershipActionsProvider.overrideWith(
            (ref) => _MockTeamMembershipActions(),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Join requests'));
    await tester.pumpAndSettle();

    expect(find.text('No pending requests'), findsOneWidget);
  });
}
