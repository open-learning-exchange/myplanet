import 'dart:async';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/repository/progress_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';
import 'package:myplanet/repository/teams_repository.dart';
import 'package:myplanet/ui/teams/leaderboard/team_leaderboard_screen.dart';

import '../../../support/widget_harness.dart';

class _MockTeamsRepository extends Mock implements TeamsRepository {}

class _MockProgressRepository extends Mock implements ProgressRepository {}

class _MockSurveysRepository extends Mock implements SurveysRepository {}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

TeamRow _membership({String id = 'm1', String? userId}) => TeamRow(
  id: id,
  userId: userId,
  isLeader: false,
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

TeamLogRow _visit(String id) =>
    TeamLogRow(id: id, teamId: 'team-1', time: 0, uploaded: false);

void main() {
  late AppDatabase database;
  late _MockTeamsRepository teams;
  late _MockProgressRepository progress;
  late _MockSurveysRepository surveys;

  setUpAll(() {
    registerFallbackValue(<String>[]);
  });

  setUp(() {
    database = AppDatabase.memory();
    teams = _MockTeamsRepository();
    progress = _MockProgressRepository();
    surveys = _MockSurveysRepository();

    when(
      () => surveys.teamOwnedSurveys(any()),
    ).thenAnswer((_) async => const []);
    when(
      () => progress.courseProgressSummary(any(), any()),
    ).thenAnswer((_) async => const {});
    when(
      () => teams.teamVisitsForUsers(any(), any()),
    ).thenAnswer((_) async => const []);
  });

  tearDown(() => database.close());

  /// Seeds one `users` row and returns its id.
  Future<String> seedUser({
    required String id,
    String? name,
    String? firstName,
    String? middleName,
    String? lastName,
  }) async {
    await database.userDao.upsert(
      UsersCompanion.insert(
        id: id,
        name: Value(name),
        firstName: Value(firstName),
        middleName: Value(middleName),
        lastName: Value(lastName),
      ),
    );
    return id;
  }

  Widget harness({
    UserRow? session,
    List<CourseRow> courses = const [],
    Future<List<CourseRow>>? coursesFuture,
  }) => wrapScreen(
    const TeamLeaderboardScreen(teamId: 'team-1'),
    overrides: [
      appDatabaseProvider.overrideWithValue(database),
      teamsRepositoryProvider.overrideWithValue(teams),
      progressRepositoryProvider.overrideWithValue(progress),
      surveysRepositoryProvider.overrideWithValue(surveys),
      sessionProvider.overrideWith(() => _TestSessionNotifier(session)),
      teamCoursesProvider.overrideWith(
        (ref, teamId) => coursesFuture ?? Future.value(courses),
      ),
    ],
  );

  testWidgets('shows the empty state when the team has no members', (
    tester,
  ) async {
    when(
      () => teams.watchMembers(any()),
    ).thenAnswer((_) => Stream.value(const <TeamRow>[]));

    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();

    expect(find.text('No data available'), findsOneWidget);
    expect(find.text('All time'), findsOneWidget);
    expect(find.text('This month'), findsOneWidget);
  });

  testWidgets('ranks members and medals the top three', (tester) async {
    when(() => teams.watchMembers(any())).thenAnswer(
      (_) => Stream.value([
        _membership(id: 'm1', userId: 'u1'),
        _membership(id: 'm2', userId: 'u2'),
        _membership(id: 'm3', userId: 'u3'),
        _membership(id: 'm4', userId: 'u4'),
      ]),
    );
    await seedUser(id: 'u1', firstName: 'Ann', lastName: 'One');
    await seedUser(id: 'u2', firstName: 'Bob', lastName: 'Two');
    await seedUser(id: 'u3', firstName: 'Cal', lastName: 'Three');
    await seedUser(id: 'u4', firstName: 'Dee', lastName: 'Four');

    // u1 finishes both courses, u2 one, u3 and u4 none.
    when(() => progress.courseProgressSummary(any(), 'u1')).thenAnswer(
      (_) async => const {
        'c1': CourseProgressSummary(max: 2, current: 2),
        'c2': CourseProgressSummary(max: 2, current: 2),
      },
    );
    when(() => progress.courseProgressSummary(any(), 'u2')).thenAnswer(
      (_) async => const {'c1': CourseProgressSummary(max: 2, current: 2)},
    );

    await tester.pumpWidget(
      harness(
        courses: [
          buildCourseRow(id: 'c1'),
          buildCourseRow(id: 'c2'),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('🥇'), findsOneWidget);
    expect(find.text('🥈'), findsOneWidget);
    expect(find.text('🥉'), findsOneWidget);
    // The fourth place carries a plain numeral, not a medal.
    expect(find.text('4'), findsOneWidget);
    expect(find.text('2/2 courses'), findsOneWidget);
    expect(find.text('1/2 courses'), findsOneWidget);
  });

  testWidgets('members tied on courses and surveys rank in a stable order', (
    tester,
  ) async {
    // Member ids arrive through a `Set`, and `List.sort` is not stable, so
    // without a final tiebreak two members with identical scores can swap
    // rank between two loads of the same data. A leaderboard whose ranking
    // shuffles on reload is worse than one that is merely arbitrary.
    when(() => teams.watchMembers(any())).thenAnswer(
      (_) => Stream.value([
        _membership(id: 'm1', userId: 'u-zoe'),
        _membership(id: 'm2', userId: 'u-amy'),
        _membership(id: 'm3', userId: 'u-bob'),
      ]),
    );
    await seedUser(id: 'u-zoe', firstName: 'Zoe', lastName: 'Zed');
    await seedUser(id: 'u-amy', firstName: 'Amy', lastName: 'Ash');
    await seedUser(id: 'u-bob', firstName: 'Bob', lastName: 'Bee');

    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();

    final names = tester
        .widgetList<Text>(find.byType(Text))
        .map((t) => t.data)
        .whereType<String>()
        .where(
          (s) =>
              s.startsWith('Amy') || s.startsWith('Bob') || s.startsWith('Zoe'),
        )
        .toList();
    expect(names, ['Amy Ash', 'Bob Bee', 'Zoe Zed']);
  });

  testWidgets('a middle name is part of the display name', (tester) async {
    // `profile_avatar.dart`'s `displayName` is the port's single source for a
    // user's name, and it includes the middle name.
    when(
      () => teams.watchMembers(any()),
    ).thenAnswer((_) => Stream.value([_membership(id: 'm1', userId: 'u1')]));
    await seedUser(
      id: 'u1',
      firstName: 'Ada',
      middleName: 'Byron',
      lastName: 'Lovelace',
    );

    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();

    expect(find.text('Ada Byron Lovelace'), findsOneWidget);
  });

  testWidgets('a whitespace-only name falls back rather than rendering blank', (
    tester,
  ) async {
    // A synced `"name": " jane "` with no first or last name took the health
    // screen down in Phase 95. Here it would render a blank row.
    when(
      () => teams.watchMembers(any()),
    ).thenAnswer((_) => Stream.value([_membership(id: 'm1', userId: 'u1')]));
    await seedUser(id: 'u1', name: ' jane ');

    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('jane'), findsOneWidget);
  });

  testWidgets('the visit stat carries the visit count', (tester) async {
    when(
      () => teams.watchMembers(any()),
    ).thenAnswer((_) => Stream.value([_membership(id: 'm1', userId: 'u1')]));
    await seedUser(id: 'u1', firstName: 'Ann', lastName: 'One');
    when(
      () => teams.teamVisitsForUsers(any(), any()),
    ).thenAnswer((_) async => [_visit('v1'), _visit('v2'), _visit('v3')]);

    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();

    // A stat that reads only "Number of visits" tells the reader nothing —
    // the sibling stats both carry their numbers.
    expect(find.text('3 visits'), findsOneWidget);
  });

  testWidgets('the current user is highlighted', (tester) async {
    when(
      () => teams.watchMembers(any()),
    ).thenAnswer((_) => Stream.value([_membership(id: 'm1', userId: 'u1')]));
    await seedUser(id: 'u1', firstName: 'Ann', lastName: 'One');

    await tester.pumpWidget(
      harness(
        session: buildUserRow(id: 'u1', name: 'ann'),
      ),
    );
    await tester.pumpAndSettle();

    final name = tester.widget<Text>(find.text('Ann One'));
    expect(name.style?.fontWeight, FontWeight.bold);
  });

  testWidgets('switching to This month reloads with the month filter', (
    tester,
  ) async {
    when(
      () => teams.watchMembers(any()),
    ).thenAnswer((_) => Stream.value([_membership(id: 'm1', userId: 'u1')]));
    await seedUser(id: 'u1', firstName: 'Ann', lastName: 'One');
    await database.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 's-old',
        userId: const Value('u1'),
        type: const Value('survey'),
        status: const Value('complete'),
        lastUpdateTime: const Value(1),
      ),
    ]);
    await database.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 's-now',
        userId: const Value('u1'),
        type: const Value('survey'),
        status: const Value('complete'),
        lastUpdateTime: Value(DateTime.now().millisecondsSinceEpoch),
      ),
    ]);
    when(
      () => surveys.teamOwnedSurveys(any()),
    ).thenAnswer((_) async => const []);

    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();

    // All time counts both submissions.
    expect(find.text('2/0 surveys'), findsOneWidget);

    await tester.tap(find.text('This month'));
    await tester.pumpAndSettle();

    // This month counts only the recent one.
    expect(find.text('1/0 surveys'), findsOneWidget);
  });

  testWidgets('a failed load surfaces an error instead of spinning forever', (
    tester,
  ) async {
    // `_load` has no error handling: a throwing dependency leaves `_loading`
    // true and the screen shows an indefinite spinner with no way out.
    when(
      () => teams.watchMembers(any()),
    ).thenAnswer((_) => Stream.error(StateError('members unavailable')));

    await tester.pumpWidget(harness());
    // Bounded pumps only — an indefinite CircularProgressIndicator spins
    // `pumpAndSettle` to its ten-minute default.
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.byType(CircularProgressIndicator), findsNothing);
    // And it must read as a failure, not as "this team has no members".
    expect(find.text('No data available'), findsNothing);
    expect(find.text('The leaderboard is unavailable'), findsOneWidget);
  });

  testWidgets('a slow load does not leave a stale period selected', (
    tester,
  ) async {
    // Toggling the period restarts `_load` without cancelling the one in
    // flight. If the first completes last, its all-time numbers overwrite the
    // this-month ones the user asked for.
    when(
      () => teams.watchMembers(any()),
    ).thenAnswer((_) => Stream.value([_membership(id: 'm1', userId: 'u1')]));
    await seedUser(id: 'u1', firstName: 'Ann', lastName: 'One');
    await database.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 's-old',
        userId: const Value('u1'),
        type: const Value('survey'),
        status: const Value('complete'),
        lastUpdateTime: const Value(1),
      ),
    ]);

    final gate = Completer<List<SurveyRow>>();
    var call = 0;
    when(() => surveys.teamOwnedSurveys(any())).thenAnswer((_) async {
      call++;
      // The first load hangs until released; the second returns at once.
      return call == 1 ? gate.future : const <SurveyRow>[];
    });

    await tester.pumpWidget(harness());
    await tester.pump(const Duration(milliseconds: 50));

    await tester.tap(find.text('This month'));
    await tester.pump(const Duration(milliseconds: 50));
    // Now let the stale all-time load finish last.
    gate.complete(const []);
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump(const Duration(milliseconds: 100));

    // The screen must show what This month asked for, not the late all-time
    // result: the old submission is outside the current month. The period is
    // captured when each load starts, so the stale load really does carry
    // all-time numbers and only the `_loadToken` guard keeps them off screen.
    expect(find.text('0/0 surveys'), findsOneWidget);
    expect(find.text('1/0 surveys'), findsNothing);
  });
}
