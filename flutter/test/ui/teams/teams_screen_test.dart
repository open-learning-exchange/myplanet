import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/repository/teams_repository.dart';
import 'package:myplanet/ui/teams/teams_screen.dart';

import '../../support/widget_harness.dart';

class _MockTeamsRepository extends Mock implements TeamsRepository {}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

TeamRow _team({
  required String id,
  String? name,
  String? description,
  String? type = 'team',
  bool isPublic = false,
}) => TeamRow(
  id: id,
  name: name,
  description: description,
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

TeamRow _membership({
  required String teamId,
  bool isLeader = false,
  String? userId = 'u1',
}) => TeamRow(
  id: 'm-$teamId',
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

void main() {
  late _MockTeamsRepository teams;

  setUpAll(() {
    registerFallbackValue(<String>[]);
  });

  setUp(() {
    teams = _MockTeamsRepository();
    when(
      () => teams.memberStatuses(any(), any()),
    ).thenAnswer((_) async => const <String, TeamMemberStatus>{});
    when(
      () => teams.recentVisitCounts(any()),
    ).thenAnswer((_) async => const <String, int>{});
    when(
      () => teams.watchMemberships(any()),
    ).thenAnswer((_) => Stream.value(const <TeamRow>[]));
  });

  Widget harness({
    List<TeamRow> catalog = const [],
    List<TeamRow> memberships = const [],
    UserRow? session,
    Stream<List<TeamRow>>? catalogStream,
  }) {
    when(
      () => teams.watchCatalog(type: any(named: 'type')),
    ).thenAnswer((_) => catalogStream ?? Stream.value(catalog));
    when(
      () => teams.watchMemberships(any()),
    ).thenAnswer((_) => Stream.value(memberships));
    return wrapScreen(
      const TeamsScreen(),
      overrides: [
        teamsRepositoryProvider.overrideWithValue(teams),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(
            session ?? buildUserRow(id: 'u1', name: 'ann'),
          ),
        ),
      ],
      pushTargets: {
        '/life/teams/:id': (context) => const Scaffold(body: Text('detail')),
      },
    );
  }

  testWidgets('the empty catalog shows the empty state', (tester) async {
    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();

    expect(find.text('No teams available'), findsOneWidget);
  });

  testWidgets('a team row shows its name, description and chevron', (
    tester,
  ) async {
    await tester.pumpWidget(
      harness(
        catalog: [
          _team(id: 't1', name: 'Readers', description: 'A reading circle'),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Readers'), findsOneWidget);
    expect(find.text('A reading circle'), findsOneWidget);
    expect(find.byIcon(Icons.chevron_right), findsOneWidget);
  });

  testWidgets('a nameless team falls back to the untitled label', (
    tester,
  ) async {
    await tester.pumpWidget(harness(catalog: [_team(id: 't1')]));
    await tester.pumpAndSettle();

    expect(find.text('Untitled team'), findsOneWidget);
  });

  testWidgets('a public team gets the public icon, a private one the group', (
    tester,
  ) async {
    await tester.pumpWidget(
      harness(
        catalog: [
          _team(id: 't1', name: 'Open', isPublic: true),
          _team(id: 't2', name: 'Closed'),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.public), findsOneWidget);
    // One list icon. The segmented button's own `groups` icon is replaced by
    // a checkmark on the selected segment, so it does not count here.
    expect(find.byIcon(Icons.groups), findsOneWidget);
  });

  testWidgets('membership shows a Member chip and leadership a Leader chip', (
    tester,
  ) async {
    await tester.pumpWidget(
      harness(
        catalog: [
          _team(id: 't1', name: 'Led'),
          _team(id: 't2', name: 'Joined'),
          _team(id: 't3', name: 'Neither'),
        ],
        memberships: [
          _membership(teamId: 't1', isLeader: true),
          _membership(teamId: 't2'),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.widgetWithText(Chip, 'Leader'), findsOneWidget);
    expect(find.widgetWithText(Chip, 'Member'), findsOneWidget);
    expect(find.byType(Chip), findsNWidgets(2));
  });

  testWidgets('tapping a team row opens its detail route', (tester) async {
    await tester.pumpWidget(
      harness(
        catalog: [_team(id: 't1', name: 'Readers')],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Readers'));
    await tester.pumpAndSettle();

    expect(find.text('detail'), findsOneWidget);
  });

  testWidgets('the search box filters on the team name', (tester) async {
    await tester.pumpWidget(
      harness(
        catalog: [
          _team(id: 't1', name: 'Readers'),
          _team(id: 't2', name: 'Writers'),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(SearchBar), 'read');
    await tester.pumpAndSettle();

    expect(find.text('Readers'), findsOneWidget);
    expect(find.text('Writers'), findsNothing);
  });

  testWidgets('the search box does not match on the description', (
    tester,
  ) async {
    // `TeamViewModel.applyFilters` filters on `it.name` alone
    // (TeamViewModel.kt:115-117) — a word that appears only in a description
    // must not surface the team.
    await tester.pumpWidget(
      harness(
        catalog: [
          _team(id: 't1', name: 'Readers', description: 'poetry and prose'),
          _team(id: 't2', name: 'Poetry club', description: 'verse'),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(SearchBar), 'poetry');
    await tester.pumpAndSettle();

    expect(find.text('Poetry club'), findsOneWidget);
    expect(find.text('Readers'), findsNothing);
  });

  testWidgets('typing in the search box does not blank the list', (
    tester,
  ) async {
    // `teamsProvider` watches `teamsSearchProvider`, so every keystroke tears
    // the provider down and rebuilds it. `AsyncValue.when`'s
    // `skipLoadingOnReload` defaults to false and "does not skip loading
    // states if triggered by `Ref.watch`", so the whole list was replaced by
    // a centered spinner once per character. Kotlin re-filters an in-memory
    // list (`TeamViewModel.applyFilters`) and never shows a loading state.
    await tester.pumpWidget(
      harness(
        catalog: [
          _team(id: 't1', name: 'Readers'),
          _team(id: 't2', name: 'Writers'),
        ],
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Readers'), findsOneWidget);

    await tester.enterText(find.byType(SearchBar), 'r');
    // One frame only — this is the frame the spinner used to occupy.
    await tester.pump();

    expect(
      find.byType(CircularProgressIndicator),
      findsNothing,
      reason: 'the list must survive a keystroke',
    );
    expect(find.text('Readers'), findsOneWidget);
  });

  testWidgets('switching the type segment does not blank the list', (
    tester,
  ) async {
    // Same mechanism: `teamsTypeProvider` is watched too.
    await tester.pumpWidget(
      harness(
        catalog: [_team(id: 't1', name: 'Readers')],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Enterprises'));
    await tester.pump();

    expect(find.byType(CircularProgressIndicator), findsNothing);
  });

  testWidgets('the search is case-insensitive', (tester) async {
    // Kotlin passes `ignoreCase = true`.
    await tester.pumpWidget(
      harness(
        catalog: [_team(id: 't1', name: 'Readers')],
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(SearchBar), 'READ');
    await tester.pumpAndSettle();

    expect(find.text('Readers'), findsOneWidget);
  });

  testWidgets('the enterprise segment retitles the screen and the search box', (
    tester,
  ) async {
    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();

    expect(find.widgetWithText(AppBar, 'Teams'), findsOneWidget);
    expect(find.text('Search teams'), findsOneWidget);

    await tester.tap(find.text('Enterprises'));
    await tester.pumpAndSettle();

    expect(find.widgetWithText(AppBar, 'Enterprises'), findsOneWidget);
    expect(find.text('Search enterprises'), findsOneWidget);
  });

  testWidgets('switching to enterprises re-queries the catalog by type', (
    tester,
  ) async {
    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();

    await tester.tap(find.text('Enterprises'));
    await tester.pumpAndSettle();

    verify(() => teams.watchCatalog(type: 'enterprise')).called(1);
  });

  testWidgets('a failed catalog shows the error state', (tester) async {
    // Overriding `teamsProvider` itself rather than erroring `watchCatalog`:
    // the provider's `await for` rethrows into the test zone as well as into
    // the provider, and the zone report lands after the test body.
    await tester.pumpWidget(
      wrapScreen(
        const TeamsScreen(),
        overrides: [
          teamsRepositoryProvider.overrideWithValue(teams),
          sessionProvider.overrideWith(
            () => _TestSessionNotifier(buildUserRow(id: 'u1', name: 'ann')),
          ),
          teamsProvider.overrideWith(
            (ref) => Stream<List<TeamRow>>.error(StateError('offline')),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Teams are unavailable'), findsOneWidget);
  });
}
