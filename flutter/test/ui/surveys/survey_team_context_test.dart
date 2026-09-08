import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/team_surveys_provider.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/router.dart';
import 'package:myplanet/ui/surveys/take_survey_screen.dart';
import 'package:myplanet/ui/teams/team_surveys_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/widget_harness.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

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

  Future<void> seedSurvey({String id = 's1'}) async {
    final mapping = SurveyMapper.fromDoc({
      '_id': id,
      'type': 'surveys',
      'name': 'Water access',
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

  group('the answer sheet the port writes carries the team', () {
    Future<void> pumpTakeSurvey(WidgetTester tester, {String? teamId}) async {
      tester.view.physicalSize = const Size(1000, 3000);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        wrapScreen(
          TakeSurveyScreen(surveyId: 's1', teamId: teamId),
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
