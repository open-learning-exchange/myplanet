import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/team_members_screen.dart';

import '../support/widget_harness.dart';

class _MockActions extends Mock implements TeamMembershipActions {}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

UserRow _user(String id, String name) => UserRow(
  id: id,
  name: name,
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  isArchived: false,
  isUpdated: false,
);

TeamRow _member({
  required String id,
  required String userId,
  bool isLeader = false,
}) => TeamRow(
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

/// The Members screen's leave affordance — `MembersAdapter
/// .checkUserAndShowOverflowMenu` and `MembersFragment.handleLeaveTeam`.
void main() {
  late _MockActions actions;

  setUp(() {
    actions = _MockActions();
    when(
      () => actions.leaveFromMembers(any()),
    ).thenAnswer((_) async => MemberActionOutcome.succeeded);
    when(
      () => actions.removeMember(any(), any()),
    ).thenAnswer((_) async => MemberActionOutcome.succeeded);
  });

  Future<void> pump(
    WidgetTester tester, {
    required List<TeamRow> members,
    TeamRow? membership,
  }) async {
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
            (ref) => Stream.value(
              membership == null ? const {} : {'team-1': membership},
            ),
          ),
          teamMembershipActionsProvider.overrideWith((ref) => actions),
          sessionProvider.overrideWith(
            () => _TestSessionNotifier(_user('ada', 'Ada')),
          ),
        ],
        // The screen pops after a successful leave, and `context.canPop` is a
        // go_router extension that asserts without a router in scope. Any
        // route registers one; this is the one the member rows push to.
        pushTargets: {
          '/life/teams/team-1/members/bob': (_) => const SizedBox.shrink(),
        },
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('a sole member is offered no overflow menu at all', (
    tester,
  ) async {
    // `(isLoggedInUserTeamLeader || isOwnCard) && itemCount > 1`. **This is
    // the clause that keeps a team from being left leaderless** — Kotlin's
    // leave path promotes when it can and removes regardless, so nothing
    // downstream would refuse. The sole leader of a team is simply never
    // shown the action.
    final leader = _member(id: 'm-ada', userId: 'ada', isLeader: true);
    await pump(tester, members: [leader], membership: leader);

    expect(find.byIcon(Icons.more_vert), findsNothing);
  });

  testWidgets('a second member restores it', (tester) async {
    // The complement, and the reason the test above is about the *count* and
    // not about leadership: nothing else changes between the two.
    final leader = _member(id: 'm-ada', userId: 'ada', isLeader: true);
    await pump(
      tester,
      members: [
        leader,
        _member(id: 'm-bob', userId: 'bob'),
      ],
      membership: leader,
    );

    expect(find.byIcon(Icons.more_vert), findsNWidgets(2));
  });

  testWidgets('leaving asks for confirmation and declining does nothing', (
    tester,
  ) async {
    // `MembersFragment.handleLeaveTeam:130-137` wraps the call in
    // `confirmDialog(message = confirm_exit)`; the port went straight to the
    // action, so one mis-tap dropped the membership and — now — handed the
    // team to somebody else.
    final leader = _member(id: 'm-ada', userId: 'ada', isLeader: true);
    await pump(
      tester,
      members: [
        leader,
        _member(id: 'm-bob', userId: 'bob'),
      ],
      membership: leader,
    );

    await tester.tap(find.byIcon(Icons.more_vert).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Leave').last);
    await tester.pumpAndSettle();

    expect(find.text('Are you sure you want to leave this team?'), findsOne);
    await tester.tap(find.text('No'));
    await tester.pumpAndSettle();

    verifyNever(() => actions.leaveFromMembers(any()));
  });

  testWidgets('accepting calls the succeeding leave, not the plain one', (
    tester,
  ) async {
    // The distinction this lane turns on. `leave` is the detail screen's
    // Kotlin path (`TeamViewModel.leaveTeam:143-151`) and promotes nobody;
    // `leaveFromMembers` is `RequestsViewModel.leaveTeam:77-90` and does.
    // Point this at `leave` and the team goes leaderless again with every
    // other test in the suite still green.
    final leader = _member(id: 'm-ada', userId: 'ada', isLeader: true);
    await pump(
      tester,
      members: [
        leader,
        _member(id: 'm-bob', userId: 'bob'),
      ],
      membership: leader,
    );

    await tester.tap(find.byIcon(Icons.more_vert).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Leave').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Yes'));
    await tester.pumpAndSettle();

    verify(() => actions.leaveFromMembers('team-1')).called(1);
    verifyNever(() => actions.leave(any()));
  });

  testWidgets('the last-leader refusal gets its own message', (tester) async {
    // `MembersFragment:108-110` toasts `cannot_remove_user`, not the generic
    // error. Collapse the outcome back to a bool and this is what breaks:
    // a refusal would read as "Operation failed", which is what the user sees
    // when the server is unreachable — a different problem with a different
    // remedy.
    when(
      () => actions.removeMember(any(), any()),
    ).thenAnswer((_) async => MemberActionOutcome.cannotRemoveLastLeader);
    final leader = _member(id: 'm-ada', userId: 'ada', isLeader: true);
    await pump(
      tester,
      members: [
        leader,
        _member(id: 'm-bob', userId: 'bob'),
      ],
      membership: leader,
    );

    await tester.tap(find.byIcon(Icons.more_vert).last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Remove').last);
    await tester.pumpAndSettle();

    expect(find.text('User could not be removed'), findsOne);
    expect(find.text('Operation failed'), findsNothing);
  });
}
