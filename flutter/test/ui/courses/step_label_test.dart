import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/courses/take_course_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/widget_harness.dart';

/// `_ProgressSection`'s heading, against `TakeCourseFragment.updateStepDisplay`
/// (`TakeCourseFragment.kt:184-193`).
///
/// **Kotlin's pager carries a cover page and the port's does not, and that one
/// structural difference is the whole of this file.**
/// `CoursesPagerAdapter.getItemCount()` is `steps.size + 1`, `createFragment(0)`
/// builds a `CourseDetailFragment` and `createFragment(p > 0)` builds the step
/// at `steps[p - 1]` with `stepNumber = p` (`CoursesPagerAdapter.kt:40-60`). So
/// Kotlin position `p` shows step index `p - 1`, and `updateStepDisplay` reads
/// `"Course Details"` at 0 and `"Step p/N"` at every `p >= 1`.
///
/// The port's `PageView.builder` has `itemCount: steps.length` and no cover
/// page, so **port index `i` is Kotlin position `i + 1`** and the heading at
/// index `i` must be `Step ${i + 1}`. `_ProgressSection` special-cased index 0
/// to `l10n.courseDetails` — Kotlin's cover-page branch, on a pager that has no
/// cover page — so the first step of every course announced itself as the
/// course description while rendering step 1.
///
/// **The `else` branch was already right, and the brief that sent me here said
/// it was off by one.** It is not: `l10n.stepNumber(currentStep + 1)` is
/// exactly `i + 1` under the mapping above, and it agrees with `_StepContent`'s
/// own `stepNumber: index + 1`, with the `n / N` counter beside it, and with
/// the `stepNum` both tiles push at the exam screen. Only the `== 0` branch
/// ever disagreed with the page under it. The pins below are written so that
/// changing the `else` branch to `currentStep` reds them too, because a "fix"
/// that made the fossil's premise true everywhere would have renumbered every
/// step in the app.
///
/// Every assertion pairs the heading with the **title of the step actually
/// rendered**. That is not thoroughness, it is the lesson of Phase 139: the
/// heading and the counter are both driven by `currentStep`, so they agreed
/// with each other while disagreeing with the `PageView` for four phases. A
/// heading assertion alone cannot see that.
void main() {
  Future<void> pumpCourse(
    WidgetTester tester, {
    required List<String> stepTitles,
  }) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    final steps = [
      for (var i = 0; i < stepTitles.length; i++)
        buildStepRow(id: 's$i', stepTitle: stepTitles[i], stepIndex: i),
    ];

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
        },
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
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

  Future<void> tapNext(WidgetTester tester) async {
    await tester.tap(find.text('Next'));
    await tester.pumpAndSettle();
  }

  testWidgets('the first step is labelled Step 1, not Course Details', (
    tester,
  ) async {
    // Pre-fix: `Course Details` was on screen above a page titled `First`,
    // and `Step 1` appeared nowhere.
    await pumpCourse(tester, stepTitles: const ['First', 'Second', 'Third']);

    expect(find.text('First'), findsOneWidget, reason: 'the rendered page');
    expect(find.text('Step 1'), findsOneWidget);
    expect(find.text('1 / 3'), findsOneWidget);
    expect(find.text('Course Details'), findsNothing);
  });

  testWidgets('every later step names the step under it', (tester) async {
    await pumpCourse(tester, stepTitles: const ['First', 'Second', 'Third']);

    await tapNext(tester);
    expect(find.text('Second'), findsOneWidget, reason: 'the rendered page');
    expect(find.text('First'), findsNothing);
    expect(find.text('Step 2'), findsOneWidget);
    expect(find.text('2 / 3'), findsOneWidget);

    await tapNext(tester);
    expect(find.text('Third'), findsOneWidget, reason: 'the rendered page');
    expect(find.text('Step 3'), findsOneWidget);
    expect(find.text('3 / 3'), findsOneWidget);
  });

  testWidgets('a one-step course reads Step 1 of 1', (tester) async {
    // The degenerate case the fossil hid completely: with a single step,
    // index 0 is the only page there is, so `Course Details` was the only
    // heading the course ever showed.
    await pumpCourse(tester, stepTitles: const ['Only']);

    expect(find.text('Only'), findsOneWidget);
    expect(find.text('Step 1'), findsOneWidget);
    expect(find.text('1 / 1'), findsOneWidget);
    expect(find.text('Course Details'), findsNothing);
  });
}

class _Session extends SessionNotifier {
  _Session(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}
