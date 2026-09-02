import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/team_courses_screen.dart';
import 'package:myplanet/ui/teams/team_resources_screen.dart';

import '../../support/widget_harness.dart';

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

TeamRow _row({
  String id = 'team-1',
  String? teamId,
  String? userId,
  bool isLeader = false,
}) => TeamRow(
  id: id,
  teamId: teamId,
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

TeamRow _membership({bool isLeader = false}) =>
    _row(id: 'm1', teamId: 'team-1', userId: 'user-1', isLeader: isLeader);

void main() {
  group('team resources', () {
    Widget harness({TeamRow? membership}) => wrapScreen(
      const TeamResourcesScreen(teamId: 'team-1'),
      overrides: [
        teamResourcesProvider('team-1').overrideWith(
          (ref) => Stream.value([
            buildLibraryRow(id: 'r1', title: 'Algebra', resourceId: 'res-1'),
          ]),
        ),
        teamMembershipsProvider.overrideWith(
          (ref) => Stream.value(
            membership == null ? const {} : {'team-1': membership},
          ),
        ),
      ],
    );

    testWidgets('an ordinary member is offered the add button', (tester) async {
      // `TeamResourcesFragment.kt:51-52` is
      // `binding.fabAddResource.isVisible = isMember` — `isMemberFlow`, i.e.
      // `TeamsRepositoryImpl.isMember`, plain membership. The port required
      // leadership, so a rank-and-file member could not link a resource the
      // Kotlin lets them link. The Phase 99 shape.
      await tester.pumpWidget(harness(membership: _membership()));
      await tester.pumpAndSettle();

      expect(find.byType(FloatingActionButton), findsOneWidget);
    });

    testWidgets('a leader is offered the add button too', (tester) async {
      await tester.pumpWidget(harness(membership: _membership(isLeader: true)));
      await tester.pumpAndSettle();

      expect(find.byType(FloatingActionButton), findsOneWidget);
    });

    testWidgets('a non-member is not', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      expect(find.byType(FloatingActionButton), findsNothing);
    });

    testWidgets('an ordinary member may not unlink a resource', (tester) async {
      // `TeamResourcesFragment.kt:73` really is `isTeamLeader` for remove —
      // the two gates on this screen are genuinely different.
      await tester.pumpWidget(harness(membership: _membership()));
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.link_off), findsNothing);
    });

    testWidgets('a leader may unlink a resource', (tester) async {
      await tester.pumpWidget(harness(membership: _membership(isLeader: true)));
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.link_off), findsOneWidget);
    });
  });

  group('team courses', () {
    Widget harness({
      TeamRow? membership,
      String? creatorId,
      String userId = 'user-1',
    }) => wrapScreen(
      const TeamCoursesScreen(teamId: 'team-1'),
      overrides: [
        teamCoursesProvider('team-1').overrideWith(
          (ref) async => [buildCourseRow(id: 'c1', courseTitle: 'Algebra')],
        ),
        teamMembershipsProvider.overrideWith(
          (ref) => Stream.value(
            membership == null ? const {} : {'team-1': membership},
          ),
        ),
        teamProvider(
          'team-1',
        ).overrideWith((ref) async => _row(userId: creatorId)),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(buildUserRow(id: userId, name: 'ada')),
        ),
      ],
    );

    testWidgets('an ordinary member is offered the add button', (tester) async {
      // Kotlin's add has no gate in `TeamCoursesFragment` at all — it is
      // driven by `btnAddDoc`, which `setupMyTeamButtons` shows to any
      // member (`TeamDetailFragment.kt:286-289`).
      await tester.pumpWidget(harness(membership: _membership()));
      await tester.pumpAndSettle();

      expect(find.byType(FloatingActionButton), findsOneWidget);
    });

    testWidgets('a non-member is not', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      expect(find.byType(FloatingActionButton), findsNothing);
    });

    testWidgets('a leader who did not create the team may not unlink', (
      tester,
    ) async {
      // `TeamCoursesFragment.kt:44-46`:
      // `canRemove = currentUserId.equals(teamCreator, ignoreCase = true)`,
      // where `getTeamCreator` is the team row's `userId`
      // (`TeamsRepositoryImpl.kt:1120-1123`) — the creator, not the leader.
      await tester.pumpWidget(
        harness(
          membership: _membership(isLeader: true),
          creatorId: 'someone-else',
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.link_off), findsNothing);
    });

    testWidgets('the creator may unlink a course', (tester) async {
      await tester.pumpWidget(
        harness(membership: _membership(), creatorId: 'user-1'),
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.link_off), findsOneWidget);
    });

    testWidgets('the creator match ignores case', (tester) async {
      await tester.pumpWidget(
        harness(membership: _membership(), creatorId: 'USER-1'),
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.link_off), findsOneWidget);
    });
  });
}
