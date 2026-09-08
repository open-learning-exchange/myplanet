import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/team_surveys_provider.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/repository/submissions_uploader.dart';
import 'package:myplanet/ui/exam/user_information_screen.dart';
import 'package:myplanet/ui/router.dart';
import 'package:myplanet/ui/surveys/take_survey_screen.dart';
import 'package:myplanet/ui/teams/team_surveys_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/widget_harness.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

class _StubServerConfig extends ServerConfigNotifier {
  _StubServerConfig(this.config);
  final ServerConfig? config;
  @override
  ServerConfig? build() => config;
}

class _StubSession extends SessionNotifier {
  _StubSession(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

/// Phase 132 — the team context an answer sheet is written with.
///
/// `SubmissionsRepository.createSurveyDraft(teamId:)` landed in Phase 129 with
/// **no caller**, which is the reachability failure class this project keeps
/// re-finding: the writer is correct, its tests are green, and no live journey
/// hands it a value. Kotlin carries the team as fragment arguments
/// (`TeamPagerAdapter` `putBoolean("isTeam", true)` + the id ->
/// `BaseExamFragment:77-78` -> `ExamTakingFragment:124-125,151-152`), so a team
/// survey's submission is attributable to the team; the port dropped it at the
/// navigation and every answer sheet it wrote was team-less.
///
/// These tests exercise the chain rather than its halves — the team screen's
/// push, the real route table's read, and the row the repository actually
/// writes — because each half was already green while the whole was broken.
const _server = ServerConfig(
  serverUrl: 'https://planet.example',
  pin: '1234',
  couchDbUrl: 'https://planet.example/db',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AppDatabase db;

  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  SurveyRow survey({String id = 's1', String? name = 'Water access'}) =>
      SurveyRow(
        id: id,
        name: name,
        createdDate: 0,
        updatedDate: 0,
        adoptionDate: 0,
        totalMarks: 0,
        isFromNation: false,
        teamShareAllowed: false,
      );

  Future<void> seedSurvey({String id = 's1', bool fromNation = false}) async {
    final mapping = SurveyMapper.fromDoc({
      '_id': id,
      'type': 'surveys',
      'name': 'Water access',
      'isFromNation': fromNation,
      'questions': [
        {'id': 'q1', 'body': 'How far is the nearest tap?', 'type': 'input'},
      ],
    })!;
    await db.surveyDao.upsertAll([mapping.survey], {id: mapping.questions});
  }

  Future<List<SubmissionRow>> submissions() => db.select(db.submissions).get();

  /// Taps the first owned survey in the team's surveys tab and returns the
  /// location the screen pushed.
  Future<Uri> tapOwnedSurvey(WidgetTester tester) async {
    Uri? pushed;

    await tester.pumpWidget(
      wrapScreen(
        const TeamSurveysScreen(teamId: 'team-1'),
        pushTargets: {
          '/life/surveys/:surveyId': (context) {
            pushed = GoRouterState.of(context).uri;
            return const Scaffold(body: Text('TAKE_SURVEY'));
          },
        },
        overrides: [
          teamOwnedSurveysProvider(
            'team-1',
          ).overrideWith((ref) async => [survey()]),
          teamAdoptableSurveysProvider(
            'team-1',
          ).overrideWith((ref) async => const []),
          teamProvider('team-1').overrideWith((ref) async => null),
          sessionProvider.overrideWith(() => _StubSession(null)),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Water access'));
    await tester.pumpAndSettle();

    expect(pushed, isNotNull, reason: 'the survey route was not reached');
    return pushed!;
  }

  group('the team surveys screen sends the team it is showing', () {
    testWidgets('tapping an owned survey pushes a location carrying the team', (
      tester,
    ) async {
      // `TeamSurveysScreen` pushed `'${Routes.surveys}/${survey.id}'` and
      // dropped the team on the floor, which is where the whole chain died.
      final pushed = await tapOwnedSurvey(tester);

      expect(pushed.path, '/life/surveys/s1');
      expect(pushed.queryParameters['teamId'], 'team-1');
    });
  });

  group('the survey route carries the team to the screen', () {
    late ProviderContainer container;
    late GoRouter router;

    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      final prefs = PlanetPrefs(await SharedPreferences.getInstance());
      container = ProviderContainer(
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          appDatabaseProvider.overrideWithValue(db),
        ],
      );
      addTearDown(container.dispose);
      router = container.read(routerProvider);
    });

    testWidgets('the location the team tab pushes builds a screen holding '
        'the team', (tester) async {
      // The pair test, and the reason it takes the location from the *screen*
      // rather than writing one out: a route reading `team` where the push
      // writes `teamId` is the Phase 74/100 shape — each half passes its own
      // test, only the pair is wrong — and a hand-written location in this
      // test would be a third copy of the key that agrees with neither.
      final pushed = await tapOwnedSurvey(tester);
      final built = await _buildFromRouter(tester, router, pushed.toString());

      expect(built, isA<TakeSurveyScreen>());
      expect((built as TakeSurveyScreen).surveyId, 's1');
      expect(built.teamId, 'team-1');
    });

    testWidgets('a personal survey location builds a team-less screen', (
      tester,
    ) async {
      final built = await _buildFromRouter(tester, router, '/life/surveys/s1');

      expect((built as TakeSurveyScreen).teamId, isNull);
    });

    testWidgets('the resume query parameter still reaches the screen', (
      tester,
    ) async {
      // `?submission=` and `?teamId=` share the query string now; reading one
      // must not have cost the other.
      final built =
          await _buildFromRouter(
                tester,
                router,
                '/life/surveys/s1?submission=sub-9',
              )
              as TakeSurveyScreen;

      expect(built.submissionId, 'sub-9');
      expect(built.teamId, isNull);
    });
  });

  testWidgets('the whole journey: team tab -> survey -> profile -> back', (
    tester,
  ) async {
    // One test that walks it end to end, because every earlier round's
    // reachability defect was a chain whose links each passed alone. The team
    // tab's tap opens the real `TakeSurveyScreen` here — not a stub — so the
    // team id crosses the navigation for real, and Kotlin's landing is
    // asserted too: `UserInformationFragment.onDismiss` pops the survey off
    // the back stack, so the respondent ends on the team's surveys tab rather
    // than on a submission detail.
    tester.view.physicalSize = const Size(1000, 3000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await seedSurvey();

    final overrides = [
      appDatabaseProvider.overrideWith((ref) {
        ref.onDispose(db.close);
        return db;
      }),
      planetApiProvider.overrideWithValue(_MockPlanetApi()),
      teamOwnedSurveysProvider(
        'team-1',
      ).overrideWith((ref) async => [survey()]),
      teamAdoptableSurveysProvider(
        'team-1',
      ).overrideWith((ref) async => const []),
      teamProvider('team-1').overrideWith((ref) async => null),
      sessionProvider.overrideWith(
        () => _StubSession(buildUserRow(id: 'user-a', name: 'jane')),
      ),
      teamMembershipsProvider.overrideWith(
        (ref) => Stream.value(const <String, TeamRow>{}),
      ),
      serverConfigProvider.overrideWith(() => _StubServerConfig(_server)),
      deviceIdentitySourceProvider.overrideWithValue(
        const FixedDeviceIdentitySource(
          DeviceIdentity(
            androidId: 'android-1',
            deviceName: 'Pixel',
            customDeviceName: 'ada-phone',
          ),
        ),
      ),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamSurveysScreen(teamId: 'team-1'),
        pushTargets: {
          '/life/surveys/:surveyId': (context) => TakeSurveyScreen(
            surveyId: GoRouterState.of(context).pathParameters['surveyId']!,
            teamId: GoRouterState.of(context).uri.queryParameters['teamId'],
          ),
        },
        overrides: overrides,
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Water access'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), '2 km');
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Submit survey'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();

    expect(find.text('Your information'), findsOneWidget);
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Year of birth'),
      '1990',
    );
    await tester.pump();
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    for (var i = 0; i < 12; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    await tester.pumpAndSettle();

    // Back on the team's surveys tab: both pushed pages are gone. (The tab
    // itself was never unmounted — it sits under the pushed routes — so its
    // presence alone would prove nothing.)
    expect(find.byType(TakeSurveyScreen), findsNothing);
    expect(find.byType(UserInformationScreen), findsNothing);
    expect(find.byType(TeamSurveysScreen), findsOneWidget);

    final row = (await db.select(db.submissions).get()).single;
    expect(row.teamId, 'team-1');
    expect(row.user, isNotNull);
  });

  group('the answer sheet the port writes carries the team', () {
    /// A pending sheet the resume path can reopen.
    Future<String> seedPendingSheet() async {
      const id = 'sub-resume';
      await db.submissionDao.upsertAll([
        SubmissionsCompanion.insert(
          id: id,
          parentId: const Value('s1'),
          parent: Value(jsonEncode({'_id': 's1', 'name': 'Water access'})),
          userId: const Value('user-a'),
          type: const Value('survey'),
          status: const Value('pending'),
          uploaded: const Value(false),
        ),
      ]);
      return id;
    }

    Future<void> pumpTakeSurvey(
      WidgetTester tester, {
      String? teamId,
      String? submissionId,
      ServerConfig? config,
    }) async {
      tester.view.physicalSize = const Size(1000, 3000);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        wrapScreen(
          TakeSurveyScreen(
            surveyId: 's1',
            teamId: teamId,
            submissionId: submissionId,
          ),
          pushTargets: {
            '/life/submissions/:id': (_) =>
                const Scaffold(body: Text('SUBMISSION_PAGE')),
          },
          overrides: [
            appDatabaseProvider.overrideWith((ref) {
              ref.onDispose(db.close);
              return db;
            }),
            planetApiProvider.overrideWithValue(_MockPlanetApi()),
            sessionProvider.overrideWith(
              () => _StubSession(buildUserRow(id: 'user-a', name: 'jane')),
            ),
            // Overridden because the real notifier reads `planetPrefs`, which
            // is `UnimplementedError` in this harness — and `_submit` reads it
            // *inside* its `try`, so without this the screen writes the row
            // and then shows "Could not save your answers". The existing
            // tests in `take_survey_screen_test.dart` assert only on the row,
            // so they never saw the failure branch they were leaving the
            // screen in. A null config skips the queue step, which is what
            // an offline handset does anyway.
            serverConfigProvider.overrideWith(() => _StubServerConfig(config)),
            // The real source reads `planetPrefs`; the uploader reads this at
            // queue time.
            deviceIdentitySourceProvider.overrideWithValue(
              const FixedDeviceIdentitySource(
                DeviceIdentity(
                  androidId: 'android-1',
                  deviceName: 'Pixel',
                  customDeviceName: 'ada-phone',
                ),
              ),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byType(TextField), '2 km');
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Submit survey'));
      // Never pumpAndSettle after Submit: the button holds an indefinite
      // CircularProgressIndicator while `submitting` is true.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
    }

    testWidgets('a team survey stores the team on the submission', (
      tester,
    ) async {
      await seedSurvey();
      await pumpTakeSurvey(tester, teamId: 'team-1');

      final rows = await submissions();
      expect(rows, hasLength(1));
      expect(rows.single.teamId, 'team-1');
    });

    testWidgets('a personal survey stores no team', (tester) async {
      await seedSurvey();
      await pumpTakeSurvey(tester);

      final rows = await submissions();
      expect(rows, hasLength(1));
      expect(rows.single.teamId, isNull);
    });

    testWidgets('a team survey asks the respondent who they are', (
      tester,
    ) async {
      // `BaseExamFragment.continueExam:127-131` — the last question of a team
      // survey goes to `showUserInfoDialog`, not to the thank-you dialog — and
      // `showUserInfoDialog:153-164` opens `UserInformationFragment` for every
      // live case (`isMySurvey` is false at both team entry points, and
      // `isFromNation` has no writer that can make it true in Kotlin). The
      // port completed a team survey in one tap and collected nothing, so the
      // demographic a team survey exists to gather was never asked for and
      // `UserInformationScreen.teamId` had no caller to be read by.
      await seedSurvey();
      await pumpTakeSurvey(tester, teamId: 'team-1');
      await tester.pumpAndSettle();

      expect(find.byType(UserInformationScreen), findsOneWidget);
      final profile = tester.widget<UserInformationScreen>(
        find.byType(UserInformationScreen),
      );
      expect(profile.teamId, 'team-1');
      // `shouldHideElements = exam?.isFromNation != true`, which the arm's own
      // predicate makes `true` — so the compact year-of-birth form, whose
      // negation is `showAdditionalFields: false`.
      expect(profile.showAdditionalFields, isFalse);
      expect(profile.submissionId, (await submissions()).single.id);
    });

    testWidgets('a personal survey goes straight to the submission', (
      tester,
    ) async {
      // The other side of the same gate: Kotlin reaches `showUserInfoDialog`
      // only under `isTeam`, so a personal sheet must not acquire a profile
      // step it never had.
      await seedSurvey();
      await pumpTakeSurvey(tester);
      await tester.pumpAndSettle();

      expect(find.byType(UserInformationScreen), findsNothing);
      expect(find.text('SUBMISSION_PAGE'), findsOneWidget);
    });

    testWidgets('a nation survey skips the profile step', (tester) async {
      // `showUserInfoDialog`'s else arm: `exam?.isFromNation == true` marks
      // the sheet complete and leaves without asking. Dead in Kotlin — its
      // only writer is `!parentId.isNullOrEmpty()` with `parentId` always `""`
      // — but *live here*, because `SurveyMapper` reads `isFromNation` off the
      // document, so a server that sends the key reaches this arm.
      await seedSurvey(fromNation: true);
      await pumpTakeSurvey(tester, teamId: 'team-1');
      await tester.pumpAndSettle();

      expect(find.byType(UserInformationScreen), findsNothing);
      // The team is still on the row: the arm changes who is asked what, not
      // what the sheet is attributed to.
      expect((await submissions()).single.teamId, 'team-1');
    });

    testWidgets('the uploaded document carries both the team and the '
        'respondent', (tester) async {
      // The end of the chain, across both screens and the real uploader: what
      // Planet actually receives. `resolveTeamJson` sends `{_id}` alone when
      // the team document is not on this handset — which is the normal state
      // of a handset that has not finished syncing — and the respondent's
      // answers about themselves ride in the same document.
      //
      // Kotlin's order is what makes this hold: the sheet is queued by
      // `onDismiss`, *after* `markSubmissionComplete` writes the profile. A
      // port that queued at submit time would serialize the row before that
      // write, and `pendingUploads` would have nothing left to re-queue once
      // the outbox drained.
      await seedSurvey();
      await pumpTakeSurvey(tester, teamId: 'team-1', config: _server);
      await tester.pumpAndSettle();

      // Nothing is queued yet, and that is the ordering under test rather
      // than an incidental observation: an enqueue here would carry a payload
      // serialized before the profile exists, and a drain landing between the
      // two would `markUploaded` the row out of `pendingUploads` for good.
      expect(
        await db.outboxDao.findOpen(
          SubmissionsUploader.type,
          (await submissions()).single.id,
        ),
        isNull,
        reason: 'the sheet was queued before the respondent was asked',
      );

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Year of birth'),
        '1990',
      );
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Save'));
      for (var i = 0; i < 12; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }

      final submissionId = (await submissions()).single.id;
      final queued = await db.outboxDao.findOpen(
        SubmissionsUploader.type,
        submissionId,
      );
      expect(queued, isNotNull, reason: 'the team sheet was never queued');
      final payload = jsonDecode(queued!.payload) as Map<String, dynamic>;
      expect(payload['team'], {'_id': 'team-1'});
      expect((payload['user'] as Map<String, dynamic>)['age'], isNotNull);
    });

    testWidgets('a resumed sheet is not asked for a profile', (tester) async {
      // `showUserInfoDialog`'s gate is `!isMySurvey && exam?.isFromNation !=
      // true` (`BaseExamFragment:154-155`) — a resumed sheet takes the else
      // arm and is never asked. Latent, since no pusher pairs `?submission=`
      // with `?teamId=`; pinned so the gate stays a line-for-line port,
      // because `updateSurveyResponse` carries no team and a profile asked
      // for here could not be attributed to one.
      await seedSurvey();
      final existing = await seedPendingSheet();
      await pumpTakeSurvey(tester, teamId: 'team-1', submissionId: existing);
      await tester.pumpAndSettle();

      expect(find.byType(UserInformationScreen), findsNothing);
      // And the submit really ran, rather than the gate passing because
      // nothing happened: the resumed row now holds the typed answer.
      final answers = await db.select(db.submissionAnswers).get();
      expect(answers.single.value, '2 km');
      expect((await submissions()).single.id, existing);
    });

    testWidgets('a blank team id is stored as no team at all', (tester) async {
      // `teamId?.takeIf { it.isNotBlank() }` (`createExamSubmission:440`). A
      // query parameter is a string, so `?teamId=` arrives as `''` rather than
      // as absent, and the blank guard is what keeps that out of the column.
      await seedSurvey();
      await pumpTakeSurvey(tester, teamId: '   ');

      final rows = await submissions();
      expect(rows.single.teamId, isNull);
    });
  });
}

/// Builds the widget the **real** route table produces for [location].
///
/// The screen tests elsewhere construct their screen directly, which is
/// precisely why an unreachable screen stayed green for three phases. This
/// walks the router's own match so the route's parameter reading is what is
/// under test, not a hand-written repetition of it.
Future<Widget> _buildFromRouter(
  WidgetTester tester,
  GoRouter router,
  String location,
) async {
  final uri = Uri.parse(location);
  final matchList = router.configuration.findMatch(uri);
  expect(matchList.isError, isFalse, reason: 'no route matches $location');
  final leaf = _leafMatch(matchList.matches.last);
  final route = leaf.route;

  late Widget built;
  await tester.pumpWidget(
    MaterialApp(
      home: Builder(
        builder: (context) {
          built = route.builder!(
            context,
            GoRouterState(
              router.configuration,
              uri: uri,
              matchedLocation: leaf.matchedLocation,
              fullPath: matchList.fullPath,
              pathParameters: matchList.pathParameters,
              pageKey: const ValueKey('phase-132'),
            ),
          );
          return const SizedBox.shrink();
        },
      ),
    ),
  );
  return built;
}

/// The leaf of a match: the survey route sits under the dashboard's shell, so
/// `matches.last` is the shell rather than the `GoRoute` that builds a screen.
RouteMatch _leafMatch(RouteMatchBase match) => match is ShellRouteMatch
    ? _leafMatch(match.matches.last)
    : match as RouteMatch;
