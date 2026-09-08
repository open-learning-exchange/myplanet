import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/repository/voices_uploader.dart';
import 'package:myplanet/ui/teams/team_voices_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Phase 147, Job 2 (`PHASE_144_NOTES.md` item 1).
///
/// `TeamsVoicesFragment.kt:80` writes `map["messageType"] =
/// getEffectiveTeamType()`, and `BaseTeamFragment.kt:94-96` resolves that to
/// the nav argument or **the team's own `type`**, falling back to `""`. An
/// enterprise is not a separate feature but a team *type* (Phase 99), so a
/// voice posted from an enterprise's discussion page is `"enterprise"` on
/// Planet. The port hardcoded `'team'` for every team post, mislabelling every
/// enterprise post on the server.
///
/// **Driven through the screen on purpose.** The gap was two halves — a writer
/// that could not express the type and a caller that had it in scope — and
/// each half alone looks correct. `PHASE_144_NOTES.md` declined to add the
/// argument without the caller for exactly that reason.
void main() {
  late AppDatabase db;

  setUpAll(() => registerFallbackValue(<String, dynamic>{}));

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
  });
  tearDown(() => db.close());

  TeamRow team({required String? type}) => TeamRow(
    id: 'team-1',
    name: 'Water',
    type: type,
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
    isUpdated: false,
  );

  final ada = buildUserRow(
    id: 'local-ada',
    name: 'ada',
  ).copyWith(couchId: const Value('org.couchdb.user:ada'));

  /// Composes a post from the team voices screen and returns the document the
  /// outbox is actually holding — what would go on the wire, not a
  /// re-serialization of the row.
  Future<Map<String, dynamic>> composeFrom(
    WidgetTester tester,
    TeamRow row,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamVoicesScreen(teamId: 'team-1'),
        overrides: [
          appDatabaseProvider.overrideWithValue(db),
          planetApiProvider.overrideWithValue(_MockPlanetApi()),
          planetPrefsProvider.overrideWithValue(
            PlanetPrefs(await SharedPreferences.getInstance()),
          ),
          deviceIdentitySourceProvider.overrideWithValue(testDeviceIdentity),
          serverConfigProvider.overrideWith(_TestServerConfigNotifier.new),
          sessionProvider.overrideWith(() => _TestSessionNotifier(ada)),
          teamVoicesProvider.overrideWith(
            (ref, teamId) => Stream.value(const []),
          ),
          teamProvider.overrideWith((ref, teamId) async => row),
          // A membership is what renders the compose FAB at all.
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value({'team-1': row}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byType(FloatingActionButton));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'The pump needs parts');
    await tester.tap(find.text('Post'));
    await tester.pumpAndSettle();

    final posts = await db.newsDao.getAll();
    expect(posts, hasLength(1), reason: 'the post was never written');
    final queued = await db.outboxDao.forItem(
      VoicesUploader.type,
      posts.single.id,
    );
    expect(queued, hasLength(1), reason: 'the post was never queued');
    return jsonDecode(queued.single.payload) as Map<String, dynamic>;
  }

  testWidgets('an enterprise voice post is labelled enterprise', (
    tester,
  ) async {
    final document = await composeFrom(tester, team(type: 'enterprise'));

    expect(
      document['messageType'],
      'enterprise',
      reason:
          'Kotlin sends `getEffectiveTeamType()`, which for an enterprise is '
          '"enterprise" — the port hardcoded "team" for every team post',
    );
  });

  testWidgets('an ordinary team voice post is still labelled team', (
    tester,
  ) async {
    final document = await composeFrom(tester, team(type: 'team'));

    expect(document['messageType'], 'team');
  });

  testWidgets('a team with no type sends the empty string, not "team"', (
    tester,
  ) async {
    // `getEffectiveTeamType()` ends `?: team?.type ?: ""`. Substituting
    // `'team'` for a missing type would invent a value Kotlin never sends —
    // and `MyTeam` reads the column with `JsonUtils.getString`, so a team
    // document that omits `type` yields `""` in Kotlin too. The port's own
    // chat-share writer already sends `target?.teamType ?? ''` here
    // (`chat_repository.dart:359`), so this is also the two writers agreeing.
    final document = await composeFrom(tester, team(type: null));

    expect(document['messageType'], '');
  });
}

class _TestServerConfigNotifier extends ServerConfigNotifier {
  @override
  ServerConfig? build() => const ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this._user);

  final UserRow? _user;

  @override
  Future<UserRow?> build() async => _user;
}
