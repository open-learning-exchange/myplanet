import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/teams_screen.dart';

import '../../support/widget_harness.dart';

class _MockTeamMembershipActions extends Mock
    implements TeamMembershipActions {}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

TeamRow _team({
  String id = 'team-1',
  String? name = 'Readers',
  String? type = 'team',
  bool isPublic = false,
}) => TeamRow(
  id: id,
  name: name,
  type: type,
  isPublic: isPublic,
  isLeader: false,
  courses: const [],
  createdDate: 0,
  limit: 0,
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

TeamRow _membership({String teamId = 'team-1', bool isLeader = false}) =>
    TeamRow(
      id: 'm-$teamId',
      teamId: teamId,
      userId: 'user-1',
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

UserRow _user({String id = 'user-1', String name = 'ada'}) => UserRow(
  id: id,
  name: name,
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  isArchived: false,
  isUpdated: false,
);

/// Every entry `buildPages` puts behind the `isMyTeam || isPublic` branch.
/// `Members` is deliberately absent: Kotlin shows it on both sides.
///
/// `Leaderboard` has no Kotlin counterpart — it comes from the unmerged `14880`
/// branch — but it belongs here for the port's own reason: `watchMembers` has
/// no viewer predicate, so the board is a private team's roster with each
/// member's counts beside it.
const _gatedEntries = [
  'Tasks',
  'Team Calendar',
  'Team surveys',
  'Discussions',
  'Leaderboard',
];

void main() {
  late _MockTeamMembershipActions actions;

  setUpAll(() {
    registerFallbackValue(_team());
  });

  setUp(() {
    // The link list is a `ListView(children: [...])`: children below the
    // 600px default fold are built but never mounted, so `find.text` reports
    // "Found 0 widgets" for entries that render fine on a device — and a
    // negative assertion would then pass for the wrong reason. A tall
    // viewport puts the whole list in frame instead.
    final view =
        TestWidgetsFlutterBinding.instance.platformDispatcher.views.first;
    view.physicalSize = const Size(1000, 3000);
    view.devicePixelRatio = 1.0;
    addTearDown(view.resetPhysicalSize);
    addTearDown(view.resetDevicePixelRatio);
  });

  setUp(() {
    actions = _MockTeamMembershipActions();
    when(() => actions.leave(any())).thenAnswer((_) async => true);
    when(() => actions.requestToJoin(any())).thenAnswer((_) async => true);
  });

  Widget harness({
    TeamRow? team,
    TeamRow? membership,
    UserRow? user,
    int memberCount = 5,
    List<TeamRow> requests = const [],
  }) => wrapScreen(
    const TeamDetailScreen(teamId: 'team-1'),
    overrides: [
      teamProvider('team-1').overrideWith((ref) async => team ?? _team()),
      teamMembershipsProvider.overrideWith(
        (ref) => Stream.value(
          membership == null ? const {} : {'team-1': membership},
        ),
      ),
      teamRequestsProvider(
        'team-1',
      ).overrideWith((ref) => Stream.value(requests)),
      teamMemberCountProvider(
        'team-1',
      ).overrideWith((ref) => Stream.value(memberCount)),
      sessionProvider.overrideWith(() => _TestSessionNotifier(user ?? _user())),
      teamMembershipActionsProvider.overrideWithValue(actions),
    ],
  );

  Future<void> settle(WidgetTester tester) async {
    await tester.pumpAndSettle();
    await tester.pump();
  }

  group('the non-member access gate', () {
    testWidgets('a non-member of a private team sees only Plan and Members', (
      tester,
    ) async {
      // `TeamDetailFragment.buildPages` (:74-92): without
      // `isMyTeam || team?.isPublic == true`, the page set is exactly
      // Plan/Mission and Members.
      await tester.pumpWidget(harness());
      await settle(tester);

      expect(find.text('Plan'), findsOneWidget);
      expect(find.text('Members'), findsOneWidget);
      for (final entry in _gatedEntries) {
        expect(
          find.text(entry),
          findsNothing,
          reason: '$entry is behind the member/public gate',
        );
      }
      // The labels are `l10n.teamResources` = "Resources" and
      // `l10n.teamCourses` = "Team courses". Asserting the wrong string here
      // gave a negative that could never match, and so would have passed with
      // the gate removed.
      expect(find.text('Resources'), findsNothing);
      expect(find.text('Team courses'), findsNothing);
    });

    testWidgets('a non-member of a public team sees the full page set', (
      tester,
    ) async {
      await tester.pumpWidget(harness(team: _team(isPublic: true)));
      await settle(tester);

      for (final entry in _gatedEntries) {
        expect(find.text(entry), findsOneWidget, reason: entry);
      }
    });

    testWidgets('a member of a private team sees the full page set', (
      tester,
    ) async {
      await tester.pumpWidget(harness(membership: _membership()));
      await settle(tester);

      for (final entry in _gatedEntries) {
        expect(find.text(entry), findsOneWidget, reason: entry);
      }
    });

    testWidgets(
      "a non-member of a private enterprise cannot reach its financial reports",
      (tester) async {
        // `ReportsPage` is inside the `isMyTeam || isPublic` branch (:85).
        await tester.pumpWidget(harness(team: _team(type: 'enterprise')));
        await settle(tester);

        expect(find.text('Mission & Services'), findsOneWidget);
        expect(find.text('Financial reports'), findsNothing);
        expect(find.text('Finances'), findsNothing);
      },
    );

    testWidgets('a member of an enterprise reaches reports and finances', (
      tester,
    ) async {
      await tester.pumpWidget(
        harness(
          team: _team(type: 'enterprise'),
          membership: _membership(),
        ),
      );
      await settle(tester);

      expect(find.text('Financial reports'), findsOneWidget);
      expect(find.text('Finances'), findsOneWidget);
    });
  });

  group('the join button', () {
    testWidgets('a guest is not offered membership', (tester) async {
      // `setupNonMyTeamButtons` (:255-258) hides the button outright for a
      // `guest…` id, before any of the join wiring runs.
      await tester.pumpWidget(
        harness(
          user: _user(id: 'guest_abc', name: 'guest_abc'),
        ),
      );
      await settle(tester);

      expect(find.text('Request to join'), findsNothing);
    });

    testWidgets('a signed-in non-member is offered membership', (tester) async {
      await tester.pumpWidget(harness());
      await settle(tester);

      expect(find.text('Request to join'), findsOneWidget);
    });

    testWidgets('a pending request replaces the button with a chip', (
      tester,
    ) async {
      await tester.pumpWidget(harness(requests: [_membership()]));
      await settle(tester);

      expect(find.text('Request pending'), findsOneWidget);
      expect(find.text('Request to join'), findsNothing);
    });
  });

  group('the leave button', () {
    testWidgets('a leader can leave', (tester) async {
      // `setupMyTeamButtons` (:285-308) attaches the leave handler with no
      // leader test at all, and `markMembershipsForLeave` has none either.
      // Kotlin has `isTeamLeader` and pointedly does not use it here.
      await tester.pumpWidget(harness(membership: _membership(isLeader: true)));
      await settle(tester);

      final button = tester.widget<OutlinedButton>(
        find.widgetWithText(OutlinedButton, 'Leave team'),
      );
      expect(button.onPressed, isNotNull);
    });

    testWidgets('leaving asks for confirmation first', (tester) async {
      // Every Kotlin leave is behind a `confirm_exit` Yes/No dialog.
      await tester.pumpWidget(harness(membership: _membership()));
      await settle(tester);

      await tester.tap(find.text('Leave team'));
      await tester.pumpAndSettle();

      expect(
        find.text('Are you sure you want to leave this team?'),
        findsOneWidget,
      );
      verifyNever(() => actions.leave(any()));
    });

    testWidgets('declining the confirmation leaves the membership alone', (
      tester,
    ) async {
      await tester.pumpWidget(harness(membership: _membership()));
      await settle(tester);

      await tester.tap(find.text('Leave team'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('No'));
      await tester.pumpAndSettle();

      verifyNever(() => actions.leave(any()));
    });

    testWidgets('accepting the confirmation leaves the team', (tester) async {
      await tester.pumpWidget(harness(membership: _membership()));
      await settle(tester);

      await tester.tap(find.text('Leave team'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Yes'));
      await tester.pumpAndSettle();

      verify(() => actions.leave('team-1')).called(1);
    });

    testWidgets('the last member is not offered the leave button', (
      tester,
    ) async {
      // `TeamDetailFragment.kt:179-186` hides it when
      // `getJoinedMemberCount(teamId) <= 1 && isMyTeam`.
      await tester.pumpWidget(
        harness(membership: _membership(), memberCount: 1),
      );
      await settle(tester);

      expect(find.text('Leave team'), findsNothing);
    });
  });
}
