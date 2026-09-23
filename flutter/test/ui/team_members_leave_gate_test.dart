import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
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
    int? memberCount,
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
          // Kotlin's `itemCount` is `getJoinedMembersWithVisitInfo().size` —
          // *resolvable* members, not membership rows — and
          // `teamMemberCountProvider` is the port's spelling of it. Defaults
          // to the row count so the ordinary cases read naturally; the tests
          // that care pass the two apart.
          teamMemberCountProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(memberCount ?? members.length)),
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

  testWidgets('a member this handset cannot resolve does not unlock Leave', (
    tester,
  ) async {
    // **The gate counts resolvable members, not membership rows, and this is
    // the test that tells the two apart.** A team holding a membership row
    // for somebody whose `users` row has never synced here has two rows and
    // one resolvable member. Counting rows offered Leave, the successor
    // lookup then resolved nobody, and the leader's row went anyway — a team
    // with a member and no leader, produced by the fix meant to prevent
    // exactly that. Kotlin's `itemCount` drops the unresolvable member
    // (`mapUsersByAnyId`, `TeamsRepositoryImpl:952-961`) and hides the menu.
    final leader = _member(id: 'm-ada', userId: 'ada', isLeader: true);
    await pump(
      tester,
      members: [
        leader,
        _member(id: 'm-ghost', userId: 'ghost'),
      ],
      membership: leader,
      memberCount: 1,
    );

    expect(find.byIcon(Icons.more_vert), findsNothing);
  });

  testWidgets('an unresolved count hides the menu rather than showing it', (
    tester,
  ) async {
    // The opposite of `teams_screen.dart`'s leave button, which shows on a
    // null count. This affordance is destructive and a not-yet-known count
    // cannot justify it.
    final leader = _member(id: 'm-ada', userId: 'ada', isLeader: true);
    await tester.pumpWidget(
      wrapScreen(
        const TeamMembersScreen(teamId: 'team-1'),
        overrides: [
          teamMembersProvider('team-1').overrideWith(
            (ref) =>
                Stream.value([leader, _member(id: 'm-bob', userId: 'bob')]),
          ),
          teamRequestsProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamRow>[])),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value({'team-1': leader}),
          ),
          // Never emits: the count has not resolved.
          teamMemberCountProvider(
            'team-1',
          ).overrideWith((ref) => const Stream<int>.empty()),
          teamMembershipActionsProvider.overrideWith((ref) => actions),
          sessionProvider.overrideWith(
            () => _TestSessionNotifier(_user('ada', 'Ada')),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.more_vert), findsNothing);
  });

  testWidgets('leaving asks for confirmation and declining does nothing', (
    tester,
  ) async {
    // `MembersFragment.handleLeaveTeam:131-138` wraps the call in
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

  testWidgets('a successful leave reports it and leaves the screen', (
    tester,
  ) async {
    // **Both halves of this were pinned by nothing until the second audit
    // pass said so.** `wrapScreen` puts the screen at `/`, where
    // `context.canPop()` is false, so the `context.pop()` never ran in any
    // test and no test looked for the snackbar either — delete both lines and
    // the suite stayed green, on one of the round's advertised fixes. Pushing
    // the screen onto a route first is what makes the pop observable.
    // Kotlin: `MembersFragment:100-103`, toast then `popBackStack()`.
    final leader = _member(id: 'm-ada', userId: 'ada', isLeader: true);
    await tester.pumpWidget(
      wrapScreen(
        const _Launcher(),
        overrides: [
          teamMembersProvider('team-1').overrideWith(
            (ref) =>
                Stream.value([leader, _member(id: 'm-bob', userId: 'bob')]),
          ),
          teamRequestsProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamRow>[])),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value({'team-1': leader}),
          ),
          teamMemberCountProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(2)),
          teamMembershipActionsProvider.overrideWith((ref) => actions),
          sessionProvider.overrideWith(
            () => _TestSessionNotifier(_user('ada', 'Ada')),
          ),
        ],
        pushTargets: {
          '/members': (_) => const TeamMembersScreen(teamId: 'team-1'),
        },
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.byType(TeamMembersScreen), findsOne);

    await tester.tap(find.byIcon(Icons.more_vert).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Leave').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Yes'));
    await tester.pumpAndSettle();

    expect(find.text('Left team'), findsOne);
    expect(
      find.byType(TeamMembersScreen),
      findsNothing,
      reason: 'the screen popped, as MembersFragment does',
    );
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

class _Launcher extends StatelessWidget {
  const _Launcher();
  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(
      child: TextButton(
        onPressed: () => GoRouter.of(context).push('/members'),
        child: const Text('open'),
      ),
    ),
  );
}
