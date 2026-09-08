import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:myplanet/repository/progress_repository.dart';
import 'package:myplanet/ui/courses/take_course_screen.dart';

import '../../support/widget_harness.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

UserRow _user() => UserRow(
  id: 'user-1',
  couchId: 'org.couchdb.user:ada',
  rev: '1-a',
  name: 'ada',
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  isArchived: false,
  isUpdated: false,
);

/// Does opening a course record progress for the step the learner **lands
/// on**?
///
/// Kotlin records it twice over, neither requiring a page change:
/// `CourseStepFragment.onViewCreated` runs `launchSaveCourseProgress()` inside
/// `withResumed` (`:168-172`), and `setMenuVisibility` runs it again when the
/// pager brings the step into view (`:262-268`). Both are gated on
/// `userHasCourse` = `isMyCourse(userId, step.courseId)`.
///
/// The port drove `_recordProgress` from `goToStep` alone, which
/// `PageView.onPageChanged` never fires for the initial page — and unlike
/// Kotlin's pager there is no cover page at position 0, so the port opens
/// *on* step 1. Nothing recorded step 1 until the learner navigated away and
/// back.
///
/// It is a reachability defect of the shape this port keeps finding: a reader
/// whose writer cannot produce the values it matches. Three readers are
/// affected — `getCurrentProgress` and `courseProgressSummary` (which count
/// rows ignoring `passed`) and `completedCourseIds` (which requires `passed`).
/// A **one-step course therefore could never complete**, because the only
/// write that would pass its single step never ran.
void main() {
  late AppDatabase db;

  late PlanetPrefs prefs;

  setUp(() async {
    db = AppDatabase.memory();
    // `_recordProgress` and `_logVisitOnce` both queue an outbox row, which
    // reaches `serverConfigProvider` -> `planetPrefsProvider` — the harness
    // trap that is `UnimplementedError` unless overridden. With no server
    // configured the queueing is skipped, which is all these tests need.
    SharedPreferences.setMockInitialValues({});
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
  });
  tearDown(() => db.close());

  /// [shelfUserIds] decides `isMyCourse`, which is Kotlin's `userHasCourse`
  /// gate on recording at all.
  Future<void> seedCourse({
    int stepCount = 2,
    List<String> shelfUserIds = const ['user-1'],
  }) => db.courseDao.upsertAll(
    [
      CoursesCompanion.insert(
        id: 'course-1',
        courseId: const Value('course-1'),
        courseTitle: const Value('Algebra'),
        userId: Value(shelfUserIds),
      ),
    ],
    [
      for (var i = 0; i < stepCount; i++)
        CourseStepsCompanion.insert(
          id: 'course-1:$i',
          courseId: const Value('course-1'),
          stepTitle: Value('Step ${i + 1}'),
          stepIndex: Value(i),
        ),
    ],
  );

  /// `runAsync` yields wall-clock time so the real drift futures behind
  /// `courseProvider` / `courseStepsProvider` complete — a widget test's zone
  /// is fake-async, so they never progress otherwise. The trailing
  /// `pumpAndSettle` is what clears the scroll-physics timer `PageView` leaves
  /// behind, which the binding asserts on at teardown; it terminates here
  /// because the loading spinners are gone by the time it runs.
  Future<void> settle(WidgetTester tester) async {
    for (var round = 0; round < 6; round++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 20)),
      );
      await tester.pump(const Duration(milliseconds: 100));
    }
    await tester.pumpAndSettle();
  }

  /// Tears the tree down *inside* the test body.
  ///
  /// `TakeCourseScreen` builds its `PageController` in `build` and never
  /// disposes it, so `PageView`'s scroll simulation leaves a timer pending and
  /// the binding asserts `!timersPending` at the end of the test body —
  /// before any `addTearDown` callback could clear it. Replacing the tree and
  /// pumping past the simulation disposes the controller while the test can
  /// still pump. (The undisposed controller is a pre-existing leak in the
  /// screen, not something these tests introduce.)
  Future<void> unmount(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 1));
  }

  Future<void> pumpCourse(WidgetTester tester) async {
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
          '/take': (_) => const TakeCourseScreen(courseId: 'course-1'),
        },
        overrides: [
          appDatabaseProvider.overrideWith((ref) => db),
          planetPrefsProvider.overrideWithValue(prefs),
          sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
        ],
      ),
    );
    await settle(tester);
  }

  Future<List<int>> recordedSteps() async {
    final rows = await db.courseProgressDao.getByUser('user-1');
    final steps = [for (final row in rows) row.stepNum]..sort();
    return steps;
  }

  testWidgets('opening a course records progress for the step it opens on', (
    tester,
  ) async {
    await seedCourse();
    await pumpCourse(tester);

    expect(await recordedSteps(), [1]);
    await unmount(tester);
  });

  testWidgets('a one-step course with no exam is completable', (tester) async {
    // The consequence that makes this more than bookkeeping. The step has no
    // exam, so `passed: exams.isEmpty ? true : null` is `true` — but only if
    // anything writes the row at all.
    await seedCourse(stepCount: 1);
    await pumpCourse(tester);

    final progress = ProgressRepository(
      _MockPlanetApi(),
      db.courseDao,
      db.courseProgressDao,
      db.examDao,
      db.submissionDao,
      db.certificationDao,
    );
    expect(await progress.completedCourseIds('user-1'), {'course-1'});
    await unmount(tester);
  });

  testWidgets('a course not on the learner\'s shelf records nothing', (
    tester,
  ) async {
    // `userHasCourse` gates both Kotlin triggers, so browsing a course you
    // have not joined leaves no progress rows behind.
    await seedCourse(shelfUserIds: const ['someone-else']);
    await pumpCourse(tester);

    expect(await recordedSteps(), isEmpty);
    await unmount(tester);
  });

  testWidgets('paging forward records the step paged to as well', (
    tester,
  ) async {
    await seedCourse();
    await pumpCourse(tester);

    await tester.tap(find.text('Next'));
    await settle(tester);

    expect(await recordedSteps(), [1, 2]);
    await unmount(tester);
  });
}
