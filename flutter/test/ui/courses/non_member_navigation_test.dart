import 'dart:async';

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

/// `updateNavigationVisibility` (`TakeCourseFragment.kt:256-270`) — a learner
/// who has not joined the course gets no Next and no Previous.
///
/// ```kotlin
/// if (containsUserId) { … } else {
///     binding.nextStep.visibility = View.GONE
///     binding.previousStep.visibility = View.GONE
/// }
/// ```
///
/// The port had no membership test on either button, which is why Phase 139
/// had to put one on the *lock* instead and record it as a deviation. The gate
/// now sits where Kotlin puts it and the lock's copy is gone, so
/// `_nextStepLock` reads like `changeNextButtonState`, which has no membership
/// test of its own.
///
/// **What a non-member sees on the last step is Finish, and that is Kotlin's
/// answer rather than a choice.** The `else` branch above never touches
/// `finishStep`, and `bindCourse` has already run `setNavigationButtons()`
/// (`:118`, `:464-473`), which shows Finish exactly when `position >=
/// steps.size`. So the button survives the hiding. In the port that state is
/// reachable on a one-step course, where index 0 is the last step.
///
/// **What is deliberately not ported is the way Kotlin's own gate leaks.**
/// Three later writes put the buttons back without consulting membership:
/// `onResume:158` sets `nextStep` VISIBLE, `onClick`'s next arm sets
/// `previousStep` VISIBLE at `:373`, and `onClickNext():347` sets `nextStep`
/// VISIBLE at `:375`. The first is not exam-specific — `onPause:164-169`
/// stamps `lastPositionBeforeExam` on **every** pause, so backgrounding the
/// app is enough — and it fires on every resume once a successful bind has
/// assigned `steps`. So in the shipping Android app the gate holds on a cold
/// open and not afterwards.
///
/// An earlier draft of this file said the restored Next survived exactly one
/// tap, because `onPageSelected` would re-hide the buttons. **That was wrong
/// in a way worth recording**: `viewPager2.currentItem += 1` dispatches
/// `onPageSelected` — and with it `updateNavigationVisibility` —
/// synchronously, *before* `:373` and `:375` run, so those two lines undo the
/// hiding rather than being undone by it. (ViewPager2's
/// `setCurrentItemInternal` calls `ScrollEventAdapter.notifyProgrammaticScroll`,
/// which dispatches the selection inline on a changed target. That source is
/// not in this repository; what *is* checkable here is that `onClickNext`
/// reads the post-increment `currentItem` at `:342` while `onClickPrevious`
/// reads `currentItem - 1` at `:353`, which only works if the item index
/// updates synchronously inside the setter.) The correction does not change
/// the decision, because under either reading the gate is gone after a
/// resume — but it does change how the port compares: **the port is stricter
/// than the shipping app**, not merely tidier.
///
/// It is ported anyway, as the intent rather than the effect, because
/// `updateNavigationVisibility` is the only place Kotlin states a rule while
/// each of the three writes that undo it is doing something else — restoring
/// after a lifecycle event, or swapping Next for Finish at the end of the
/// list — and each is a partial copy of the *member* branch written without
/// its `else`. The same method that hides these also puts a *do you want to
/// join this course?* dialog in front of a non-member
/// (`setCourseData:219-229`). Recorded in `PHASE_145_NOTES.md`.
void main() {
  Future<void> pumpCourse(
    WidgetTester tester, {
    required Stream<CourseRow> courses,
    int stepCount = 3,
    String userId = 'user-1',
  }) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    final steps = [
      for (var i = 0; i < stepCount; i++)
        buildStepRow(id: 's$i', stepTitle: 'Step title $i', stepIndex: i),
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
            () => _Session(buildUserRow(id: userId, name: 'ada')),
          ),
          courseProvider('course-1').overrideWith((ref) => courses),
          courseStepsProvider(
            'course-1',
          ).overrideWith((ref) => Stream.value(steps)),
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  CourseRow courseRow({required bool joined}) => buildCourseRow(
    id: 'course-1',
    courseTitle: 'Algebra',
    userId: joined ? const ['user-1'] : const [],
  );

  testWidgets('a learner who has not joined gets no Next', (tester) async {
    // Pre-fix: Next was on screen and moved the page, so somebody who had
    // never joined could walk a course Kotlin will not let them walk.
    await pumpCourse(tester, courses: Stream.value(courseRow(joined: false)));

    expect(find.text('Step title 0'), findsOneWidget, reason: 'step 1 renders');
    expect(find.text('Next'), findsNothing);
    // The way forward is still on screen. Kotlin shows a button here too
    // (`btnRemove`, text `R.string.join` — "Join") and additionally puts a
    // join dialog in front of them; the port has the button and not the
    // dialog.
    expect(find.text('Add to my courses'), findsOneWidget);
  });

  testWidgets('a guest gets no navigation and no join button', (tester) async {
    // Kotlin gives a guest neither: `updateNavigationVisibility:257` keys on
    // `containsUserId` with no guest exception, and `setCourseData:230-232`
    // hides `btnRemove` for a guest. The port used to offer the button to
    // everyone, and for a guest it was worse than inert — `_toggleMembership`
    // bails only on a null `userId` and the guest row has one, so the tap
    // wrote a shelf membership Kotlin does not allow a guest to write.
    await pumpCourse(
      tester,
      courses: Stream.value(courseRow(joined: false)),
      userId: 'guest_ada',
    );

    expect(find.text('Step title 0'), findsOneWidget);
    expect(find.text('Next'), findsNothing);
    expect(find.text('Add to my courses'), findsNothing);
    expect(find.text('Remove from my courses'), findsNothing);
  });

  testWidgets('a member keeps Next', (tester) async {
    await pumpCourse(tester, courses: Stream.value(courseRow(joined: true)));

    expect(find.text('Next'), findsOneWidget);
    expect(find.text('Remove from my courses'), findsOneWidget);
  });

  testWidgets('leaving the course part-way takes both buttons away', (
    tester,
  ) async {
    // The one path on which the **Previous** gate is observable, and it is a
    // real one rather than a contrivance: Leave course sits in this very
    // navigation bar, and Kotlin's `addRemoveCourse` success path calls
    // `setCourseData()` → `updateNavigationVisibility()` for exactly this
    // reason. Without it the port would carry a Previous gate nothing could
    // ever exercise.
    final courses = StreamController<CourseRow>();
    addTearDown(courses.close);
    courses.add(courseRow(joined: true));

    await pumpCourse(tester, courses: courses.stream);

    await tester.tap(find.text('Next'));
    await tester.pumpAndSettle();
    expect(find.text('Step title 1'), findsOneWidget);
    expect(find.text('Previous'), findsOneWidget);

    courses.add(courseRow(joined: false));
    await tester.pumpAndSettle();

    expect(find.text('Previous'), findsNothing);
    expect(find.text('Next'), findsNothing);
    // Still the middle step, so no Finish either: Kotlin's `else` branch
    // leaves `finishStep` as `setNavigationButtons` left it, and at
    // `position < steps.size` that is GONE.
    expect(find.text('Finish'), findsNothing);
    expect(find.text('Step title 1'), findsOneWidget);
  });

  testWidgets('a non-member on the last step still sees Finish', (
    tester,
  ) async {
    // `updateNavigationVisibility`'s non-member branch never touches
    // `finishStep`, and `setNavigationButtons` (`:464-473`) has already made
    // it visible for `position >= steps.size`. A one-step course is where the
    // port can reach that state, since index 0 is the last step.
    await pumpCourse(
      tester,
      courses: Stream.value(courseRow(joined: false)),
      stepCount: 1,
    );

    expect(find.text('Finish'), findsOneWidget);
    expect(find.text('Next'), findsNothing);
    expect(find.text('Previous'), findsNothing);
  });
}

class _Session extends SessionNotifier {
  _Session(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}
