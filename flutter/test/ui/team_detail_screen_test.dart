import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/teams/teams_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/widget_harness.dart';

class _MockTeamMembershipActions extends Mock
    implements TeamMembershipActions {}

TeamRow _team({String id = 'team-1', String? type}) => TeamRow(
  id: id,
  courses: const [],
  createdDate: 0,
  limit: 0,
  isPublic: false,
  isLeader: false,
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
  type: type,
  isUpdated: false,
);

UserRow _user({String name = 'ada'}) => UserRow(
  id: 'user-1',
  name: name,
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  isArchived: false,
  isUpdated: false,
);

void main() {
  Future<List<Override>> overrides({
    UserRow? user,
    List<TeamRow> memberships = const [],
  }) async {
    SharedPreferences.setMockInitialValues(const {});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    return [
      teamProvider('team-1').overrideWith((ref) async => _team(type: 'team')),
      teamMembershipsProvider.overrideWith((ref) => Stream.value({})),
      teamRequestsProvider(
        'team-1',
      ).overrideWith((ref) => Stream.value(const <TeamRow>[])),
      teamMemberCountProvider('team-1').overrideWith((ref) => Stream.value(0)),
      sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
      teamMembershipActionsProvider.overrideWith(
        (ref) => _MockTeamMembershipActions(),
      ),
      planetPrefsProvider.overrideWithValue(prefs),
    ];
  }

  testWidgets('logs one teamVisit row when the screen opens', (tester) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    await tester.pumpWidget(
      wrapScreen(
        const TeamDetailScreen(teamId: 'team-1'),
        overrides: [
          // The screen's `teamsRepositoryProvider` watches
          // `appDatabaseProvider`, so override the same provider the screen
          // resolves to rather than creating a second instance the test would
          // read from while the providers write to another.
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(database.close);
            return database;
          }),
          ...await overrides(user: _user()),
        ],
      ),
    );
    await tester.pumpAndSettle();
    // `_logVisitOnce` fires via `addPostFrameCallback` after `teamProvider`
    // (a FutureProvider) resolves; `sessionProvider` (an AsyncNotifierProvider)
    // resolves on the same tick. Pump again so the callback can read both.
    await tester.pump();

    final rows = await database.teamLogDao.pendingUploads();
    expect(rows.length, 1);
    final row = rows.single;
    expect(row.teamId, 'team-1');
    expect(row.user, 'ada');
    expect(row.type, 'teamVisit');
    expect(row.uploaded, isFalse);
  });

  testWidgets('logs nothing for a guest session', (tester) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    await tester.pumpWidget(
      wrapScreen(
        const TeamDetailScreen(teamId: 'team-1'),
        overrides: [
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(database.close);
            return database;
          }),
          ...await overrides(user: null),
        ],
      ),
    );
    await tester.pumpAndSettle();
    await tester.pump();

    expect(await database.teamLogDao.pendingUploads(), isEmpty);
  });
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}
