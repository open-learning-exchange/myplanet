import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/team_courses_screen.dart';

import '../support/widget_harness.dart';

class _MockTeamCourseActions extends Mock implements TeamCourseActions {}

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
  testWidgets('shows the empty state when no courses are linked', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamCoursesScreen(teamId: 'team-1'),
        overrides: [
          teamCoursesProvider('team-1').overrideWith((ref) async => const []),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No courses linked to this team'), findsOneWidget);
    expect(find.byType(FloatingActionButton), findsNothing);
  });

  testWidgets('renders the linked courses with title and description', (
    tester,
  ) async {
    final rows = [
      buildCourseRow(id: 'c1', courseTitle: 'Algebra 1', description: 'Basics'),
      buildCourseRow(id: 'c2', courseTitle: null, description: null),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamCoursesScreen(teamId: 'team-1'),
        overrides: [
          teamCoursesProvider('team-1').overrideWith((ref) async => rows),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Algebra 1'), findsOneWidget);
    expect(find.text('Basics'), findsOneWidget);
    // A null courseTitle falls back to the sentence-case placeholder.
    expect(find.text('Untitled course'), findsOneWidget);
    expect(find.byIcon(Icons.school_outlined), findsNWidgets(2));
    expect(find.byIcon(Icons.link_off), findsNothing);
  });

  testWidgets('a leader sees the add-course and remove buttons', (
    tester,
  ) async {
    final rows = [buildCourseRow(id: 'c1', courseTitle: 'Algebra 1')];
    final memberships = {'team-1': _leaderMembership('team-1')};

    await tester.pumpWidget(
      wrapScreen(
        const TeamCoursesScreen(teamId: 'team-1'),
        overrides: [
          teamCoursesProvider('team-1').overrideWith((ref) async => rows),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(memberships),
          ),
          teamCourseActionsProvider.overrideWith(
            (ref) => _MockTeamCourseActions(),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.link_off), findsOneWidget);
    expect(find.text('Add course'), findsOneWidget);
    expect(find.byIcon(Icons.playlist_add), findsOneWidget);
  });

  testWidgets('the add-course dialog opens with a cancel action', (
    tester,
  ) async {
    final linked = [buildCourseRow(id: 'c1', courseTitle: 'Already Linked')];
    final memberships = {'team-1': _leaderMembership('team-1')};
    final actions = _MockTeamCourseActions();

    await tester.pumpWidget(
      wrapScreen(
        const TeamCoursesScreen(teamId: 'team-1'),
        overrides: [
          teamCoursesProvider('team-1').overrideWith((ref) async => linked),
          coursesStreamProvider.overrideWith(
            (ref) => Stream.value(const <CourseRow>[]),
          ),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(memberships),
          ),
          teamCourseActionsProvider.overrideWith((ref) => actions),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.playlist_add));
    await tester.pumpAndSettle();

    expect(find.text('Add course'), findsWidgets);
    expect(
      find.text('All available courses are already linked'),
      findsOneWidget,
    );
    expect(find.text('Cancel'), findsOneWidget);
  });
}
