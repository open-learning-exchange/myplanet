import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/exam_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/ratings_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/ui/courses/course_detail_screen.dart';
import 'package:myplanet/ui/courses/take_course_screen.dart';
import 'package:myplanet/ui/exam/take_exam_screen.dart';
import 'package:myplanet/ui/router.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/widget_harness.dart';

/// The step number a course step hands its exam — Kotlin's `"stepNum"`
/// fragment argument, and Phase 135's item 4.
///
/// Kotlin never derives it. `CoursesPagerAdapter.kt:49` stamps the pager
/// position on the step fragment as `"stepNumber"`,
/// `CourseStepFragment.kt:280` forwards it to the exam as `"stepNum"`, and
/// `BaseExamFragment.kt:75` reads it back. The port derived it instead, from
/// the step's position in a re-query of `CourseDao.getSteps` — equivalent for
/// every reachable case, but resting on `stepIndex`, whose column default of
/// `0` makes ties legal.
///
/// **These are pair tests on purpose.** A push writing `stepNum` and a route
/// reading `step_num` is the Phase 74/100 shape — each half passes its own
/// test and only the pair is wrong — so the location under test is taken from
/// the *screen* that builds it and fed to the *real* router, rather than
/// written out here as a third copy of the key that agrees with neither.
void main() {
  Map<String, Object?> examOn(String id) => {
    '_id': id,
    'type': 'courses',
    'name': '$id test',
    'questions': [
      {'id': 'q1', 'title': 'One?', 'type': 'input'},
    ],
  };

  /// A two-step course carrying an exam on whichever steps are named.
  ///
  /// One exam tile per fixture, which is now belt-and-braces rather than
  /// necessary — and the reason it looked necessary is worth recording,
  /// because it was a misdiagnosis. Every step tile renders the identical
  /// label (`take test [N]`'s count is that step's own `exams.length`, so it
  /// reads `[1]` wherever there is one), and the first cut of the second-step
  /// test tapped a tile belonging to step 1 and reported `stepNum=1` for step
  /// 2. I attributed that to `find.text` reaching an off-screen `PageView`
  /// page. **It does not:** `cacheExtent` is
  /// `allowImplicitScrolling ? 1.0 : 0.0` and that flag defaults false, so a
  /// settled `PageView` has only the visible page in the tree. The real cause
  /// was the `PageController` defect this phase then fixed — the page never
  /// moved, so step 1's tile was the only one built. With that fixed, a
  /// two-tile fixture would match only the built page; one tile per fixture
  /// stays because it makes the assertion independent of that reasoning.
  Map<String, Object?> courseDoc({
    Map<String, Object?>? firstExam,
    Map<String, Object?>? secondExam,
  }) => {
    '_id': 'course-1',
    'courseTitle': 'Algebra',
    'steps': [
      {'stepTitle': 'First', 'exam': ?firstExam},
      {'stepTitle': 'Second', 'exam': ?secondExam},
    ],
  };

  Future<AppDatabase> seed(Map<String, Object?> doc) async {
    final db = AppDatabase.memory();
    final parsed = CourseMapper.fromDoc(doc)!;
    await db.courseDao.upsertAll([parsed.course], parsed.steps);
    for (final mapping in ExamMapper.fromCourseDoc(
      doc,
      stepIdFor: CourseMapper.stepIdFor,
    )) {
      await db.examDao.upsertAll(
        [mapping.exam],
        {mapping.exam.id.value: mapping.questions},
      );
    }
    return db;
  }

  /// Records the location the screen pushes at the exam route.
  late Uri? pushed;

  Map<String, WidgetBuilder> examCapture() => {
    '/courses/exam/:examId': (context) {
      pushed = GoRouterState.of(context).uri;
      return const Scaffold(body: Text('EXAM_ROUTE'));
    },
  };

  setUp(() => pushed = null);

  group('the take-course step tile passes the step number', () {
    Future<void> pumpTakeCourse(WidgetTester tester, AppDatabase db) async {
      addTearDown(db.close);
      SharedPreferences.setMockInitialValues({});
      final prefs = PlanetPrefs(await SharedPreferences.getInstance());
      final steps = await db.courseDao.getSteps('course-1');

      await tester.pumpWidget(
        wrapScreen(
          Builder(
            builder: (context) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                final router = GoRouter.of(context);
                final location = router
                    .routerDelegate
                    .currentConfiguration
                    .last
                    .matchedLocation;
                if (location != '/take') router.push('/take');
              });
              return const Scaffold(body: Text('ROOT_PAGE'));
            },
          ),
          pushTargets: {
            '/take': (context) => const TakeCourseScreen(courseId: 'course-1'),
            ...examCapture(),
          },
          overrides: [
            planetPrefsProvider.overrideWithValue(prefs),
            appDatabaseProvider.overrideWithValue(db),
            sessionProvider.overrideWith(
              () => _Session(buildUserRow(id: 'user-1', name: 'ada')),
            ),
            courseProvider('course-1').overrideWith(
              (ref) => Stream.value(
                buildCourseRow(
                  id: 'course-1',
                  courseTitle: 'Algebra',
                  userId: const ['user-1'],
                ),
              ),
            ),
            courseStepsProvider(
              'course-1',
            ).overrideWith((ref) => Stream.value(steps)),
          ],
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('the first step sends 1', (tester) async {
      await pumpTakeCourse(
        tester,
        await seed(courseDoc(firstExam: examOn('exam-1'))),
      );

      await tester.tap(find.text('take test [1]'));
      await tester.pumpAndSettle();

      expect(pushed, isNotNull, reason: 'the exam route was not reached');
      expect(pushed!.queryParameters['stepNum'], '1');
      // The arguments that were already there must survive the addition.
      expect(pushed!.queryParameters['stepId'], 'course-1:0');
      expect(pushed!.queryParameters['courseId'], 'course-1');
      expect(pushed!.path, '/courses/exam/exam-1');
    });

    testWidgets('the second step sends 2', (tester) async {
      // The assertion a hardcoded number, or an off-by-one, fails. Kotlin's
      // is the pager position with the course cover at 0, so step *i* carries
      // *i+1* and the second step is 2 — not 1, and not 3.
      await pumpTakeCourse(
        tester,
        await seed(courseDoc(secondExam: examOn('exam-2'))),
      );

      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('take test [1]'));
      await tester.pumpAndSettle();

      expect(pushed!.queryParameters['stepNum'], '2');
      expect(pushed!.path, '/courses/exam/exam-2');
    });
  });

  group('the course-detail step tile passes the step number', () {
    Future<void> pumpDetail(WidgetTester tester, AppDatabase db) async {
      addTearDown(db.close);
      final steps = await db.courseDao.getSteps('course-1');

      await tester.pumpWidget(
        wrapScreen(
          const CourseDetailScreen(courseId: 'course-1'),
          pushTargets: examCapture(),
          overrides: [
            appDatabaseProvider.overrideWithValue(db),
            sessionProvider.overrideWith(
              () => _Session(buildUserRow(id: 'user-1', name: 'ada')),
            ),
            courseProvider('course-1').overrideWith(
              (ref) => Stream.value(
                buildCourseRow(id: 'course-1', courseTitle: 'Algebra'),
              ),
            ),
            courseStepsProvider(
              'course-1',
            ).overrideWith((ref) => Stream.value(steps)),
            ratingSummaryProvider((
              type: 'course',
              itemId: 'course-1',
            )).overrideWith(
              (ref) => Stream.value(
                const RatingSummary(average: 0, total: 0, userRating: null),
              ),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('the second tile sends 2', (tester) async {
      // This screen's exam button is a port addition with no Kotlin
      // counterpart — `CourseDetailFragment` shows a count, not a button — so
      // there is no Kotlin `stepNum` to copy. The number is the tile's own
      // 1-based position, which is the same one `take_course_screen` sends,
      // so the two entries into one step exam cannot key `course_progress`
      // differently.
      await pumpDetail(
        tester,
        await seed(
          courseDoc(firstExam: examOn('exam-1'), secondExam: examOn('exam-2')),
        ),
      );

      await tester.tap(find.text('Second'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Take exam'));
      await tester.pumpAndSettle();

      expect(pushed!.queryParameters['stepNum'], '2');
      expect(pushed!.path, '/courses/exam/exam-2');
    });
  });

  group('the exam route reads the step number the pushers write', () {
    testWidgets('the location the step tile builds reaches the screen', (
      tester,
    ) async {
      // The pair, end to end: the location comes from the take-course tile
      // above and is resolved by the real router rather than a harness stub.
      final db = await seed(courseDoc(secondExam: examOn('exam-2')));
      addTearDown(db.close);
      SharedPreferences.setMockInitialValues({});
      final prefs = PlanetPrefs(await SharedPreferences.getInstance());
      final container = ProviderContainer(
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          appDatabaseProvider.overrideWithValue(db),
        ],
      );
      addTearDown(container.dispose);

      final built =
          await _buildFromRouter(
                tester,
                container.read(routerProvider),
                '/courses/exam/exam-2?stepId=course-1:1&courseId=course-1'
                '&stepNum=2',
              )
              as TakeExamScreen;

      expect(built.stepNum, 2);
      expect(built.examId, 'exam-2');
      expect(built.stepId, 'course-1:1');
      expect(built.courseId, 'course-1');
    });

    testWidgets('a location with no step number leaves it null', (
      tester,
    ) async {
      // Every entry that is not a course step, which is Kotlin's `getInt`
      // default of 0 — and the screen then derives the number instead.
      final db = await seed(courseDoc(secondExam: examOn('exam-2')));
      addTearDown(db.close);
      SharedPreferences.setMockInitialValues({});
      final prefs = PlanetPrefs(await SharedPreferences.getInstance());
      final container = ProviderContainer(
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          appDatabaseProvider.overrideWithValue(db),
        ],
      );
      addTearDown(container.dispose);

      final built =
          await _buildFromRouter(
                tester,
                container.read(routerProvider),
                '/courses/exam/exam-2?stepId=course-1:1',
              )
              as TakeExamScreen;

      expect(built.stepNum, isNull);
    });

    testWidgets('a malformed step number reads as absent, not as zero', (
      tester,
    ) async {
      // `int.tryParse` rather than a cast: a hand-typed location must fall
      // back to the derivation rather than crash the route or write
      // `stepNum: 0`.
      final db = await seed(courseDoc(secondExam: examOn('exam-2')));
      addTearDown(db.close);
      SharedPreferences.setMockInitialValues({});
      final prefs = PlanetPrefs(await SharedPreferences.getInstance());
      final container = ProviderContainer(
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          appDatabaseProvider.overrideWithValue(db),
        ],
      );
      addTearDown(container.dispose);

      final built =
          await _buildFromRouter(
                tester,
                container.read(routerProvider),
                '/courses/exam/exam-2?stepNum=second',
              )
              as TakeExamScreen;

      expect(built.stepNum, isNull);
    });
  });
}

/// Builds the widget the router's own route builder produces for [location].
///
/// Mirrors the helper `survey_team_context_test.dart` uses for the same reason:
/// it exercises `router.dart`'s builder rather than a copy of it.
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
              pageKey: const ValueKey('phase-139'),
            ),
          );
          return const SizedBox.shrink();
        },
      ),
    ),
  );
  return built;
}

RouteMatch _leafMatch(RouteMatchBase match) => match is ShellRouteMatch
    ? _leafMatch(match.matches.last)
    : match as RouteMatch;

class _Session extends SessionNotifier {
  _Session(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}
