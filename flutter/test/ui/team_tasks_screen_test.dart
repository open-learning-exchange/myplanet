import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/team_tasks_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/ui/teams/team_tasks_screen.dart';

import '../support/widget_harness.dart';

class _MockTeamTaskActions extends Mock implements TeamTaskActions {}

TeamTaskRow _task({
  String id = 't1',
  String? title,
  String? assignee,
  int deadline = 0,
  bool completed = false,
}) => TeamTaskRow(
  id: id,
  teamId: 'team-1',
  title: title,
  assignee: assignee,
  deadline: deadline,
  completed: completed,
  completedTime: 0,
  status: '',
  isUpdated: false,
  isNotified: false,
);

void main() {
  testWidgets('shows the empty state when no tasks exist', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamTasksScreen(teamId: 'team-1'),
        overrides: [
          teamTasksProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamTaskRow>[])),
          commentsForParentProvider.overrideWith(
            (ref, parentId) => Stream.value(const <NewsRow>[]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No tasks yet'), findsOneWidget);
  });

  testWidgets('renders tasks with title, deadline, and assignee', (
    tester,
  ) async {
    final tasks = [
      _task(id: 't1', title: 'Submit report', assignee: 'alice', deadline: 0),
      _task(
        id: 't2',
        title: null,
        assignee: null,
        deadline: DateTime(2026, 3, 15).millisecondsSinceEpoch,
      ),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamTasksScreen(teamId: 'team-1'),
        overrides: [
          teamTasksProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(tasks)),
          commentsForParentProvider.overrideWith(
            (ref, parentId) => Stream.value(const <NewsRow>[]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Submit report'), findsOneWidget);
    // The subtitle joins the deadline label and assignee with " · ".
    expect(find.text('No deadline · alice'), findsOneWidget);
    // A null title falls back to the sentence-case placeholder.
    expect(find.text('Untitled task'), findsOneWidget);
  });

  testWidgets('toggling a checkbox completes the task', (tester) async {
    final tasks = [_task(id: 't1', title: 'Submit report', completed: false)];
    final actions = _MockTeamTaskActions();
    when(() => actions.complete(any(), any())).thenAnswer((_) async {});

    await tester.pumpWidget(
      wrapScreen(
        const TeamTasksScreen(teamId: 'team-1'),
        overrides: [
          teamTasksProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(tasks)),
          commentsForParentProvider.overrideWith(
            (ref, parentId) => Stream.value(const <NewsRow>[]),
          ),
          teamTaskActionsProvider.overrideWith((ref) => actions),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byType(CheckboxListTile));
    await tester.pump();

    verify(() => actions.complete('t1', true)).called(1);
  });

  testWidgets('the add-task dialog invokes save with the entered title', (
    tester,
  ) async {
    final actions = _MockTeamTaskActions();
    // Return false so the dialog stays mounted (no pop/dispose race in the
    // test binding); we only assert the save call, not the dismissal.
    when(
      () => actions.save(
        id: any(named: 'id'),
        teamId: any(named: 'teamId'),
        title: any(named: 'title'),
        description: any(named: 'description'),
        deadline: any(named: 'deadline'),
        assignee: any(named: 'assignee'),
      ),
    ).thenAnswer((_) async => false);

    await tester.pumpWidget(
      wrapScreen(
        const TeamTasksScreen(teamId: 'team-1'),
        overrides: [
          teamTasksProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(const <TeamTaskRow>[])),
          commentsForParentProvider.overrideWith(
            (ref, parentId) => Stream.value(const <NewsRow>[]),
          ),
          teamTaskActionsProvider.overrideWith((ref) => actions),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.add_task));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField).first, 'New task');
    await tester.pump();

    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    verify(
      () => actions.save(
        id: null,
        teamId: 'team-1',
        title: 'New task',
        description: '',
        deadline: 0,
        assignee: '',
      ),
    ).called(1);
  });

  testWidgets('the per-row menu offers edit and delete', (tester) async {
    final tasks = [_task(id: 't1', title: 'Submit report')];
    final actions = _MockTeamTaskActions();

    await tester.pumpWidget(
      wrapScreen(
        const TeamTasksScreen(teamId: 'team-1'),
        overrides: [
          teamTasksProvider(
            'team-1',
          ).overrideWith((ref) => Stream.value(tasks)),
          commentsForParentProvider.overrideWith(
            (ref, parentId) => Stream.value(const <NewsRow>[]),
          ),
          teamTaskActionsProvider.overrideWith((ref) => actions),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();

    expect(find.text('Edit task'), findsOneWidget);
    expect(find.text('Delete task'), findsOneWidget);
  });
}
