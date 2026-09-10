import 'dart:async';

import 'package:drift/drift.dart' show Value;
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
/// **Finish is not gated, and the reason is an invariant rather than a
/// preference.** The `else` branch above never touches `finishStep`, and
/// `bindCourse` has already run `setNavigationButtons()` (`:118`, `:464-473`),
/// which shows Finish exactly when `position >= steps.size` — as do the only
/// other writers, `onResume:154-160` and `onClickNext:343-349`. Membership
/// never enters it. Gating Finish would break the case Kotlin genuinely has: an
/// **ex-member** is restored to `position = currentStep` (`:104`, `:116`) from
/// progress rows that survive leaving a course (`leaveCourses` touches
/// `courses` and `removed_log` only), so somebody who completed a course and
/// then left re-opens on the last page and sees Finish with no Next and no
/// Previous.
///
/// **Where the port and Kotlin part company is the cover page, not the gate,
/// and an earlier draft of this file called that parity.** Kotlin's
/// *never*-member is placed at `position = 0` — the `CourseDetailFragment`
/// cover page — where `0 >= steps.size` is false for any course with a step,
/// so they see no Finish either. The port has no cover page, so its index 0 is
/// Kotlin's position 1; on a **one-step** course that is already the last step,
/// and applying Kotlin's own rule at the position the learner is actually on
/// yields Finish. So a learner who never joined a one-step course gets a
/// Finish button here that Kotlin's cold open would not show them.
///
/// That is the same structural difference `step_label_test.dart` is about,
/// surfacing a second time, and it is left as it is: the alternative is to gate
/// Finish on membership, which would contradict the invariant above and take
/// the button away from the ex-member Kotlin gives it to. Recorded in
/// `PHASE_145_NOTES.md` as a divergence rather than dressed up as parity.
///
/// **What is deliberately not ported is the way Kotlin's own gate leaks.**
/// **Five** later writes put the buttons back without consulting membership:
/// `onResume:158`, `onClick`'s next arm at `:373` (`previousStep`),
/// `onClickNext:347` (`nextStep`, called from `:375`), and `onClickPrevious`
/// at `:356` and `:359`, which set `nextStep` VISIBLE on both arms of its own
/// `if`. The first is not exam-specific — `onPause:164-169`
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
    UserRow? sessionUser,
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
            () =>
                _Session(sessionUser ?? buildUserRow(id: userId, name: 'ada')),
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

  testWidgets('a guest who has joined keeps navigation but gets no join '
      'button', (tester) async {
    // **The discriminating case, and the first cut of this test did not have
    // it.** That version put a guest on an *unjoined* course, where three of
    // its four assertions followed from non-membership and only one touched
    // the guest rule at all — it would have passed with `canChangeMembership`
    // deleted in every respect but one.
    //
    // Kotlin's two gates read different things and this is where they
    // disagree: `updateNavigationVisibility:257` keys on `containsUserId`
    // alone, with **no** guest exception, while `setCourseData:216-232` shows
    // `btnRemove` for `!isGuest && !containsUserId`. So a guest whose id is in
    // `course.userId` gets the navigation and not the button.
    await pumpCourse(
      tester,
      courses: Stream.value(
        buildCourseRow(
          id: 'course-1',
          courseTitle: 'Algebra',
          userId: const ['guest_ada'],
        ),
      ),
      userId: 'guest_ada',
    );

    expect(find.text('Next'), findsOneWidget, reason: 'membership, not guest');
    expect(find.text('Add to my courses'), findsNothing);
    expect(find.text('Remove from my courses'), findsNothing);
  });

  testWidgets('a guest who has not joined gets nothing at all', (tester) async {
    // The port used to offer the membership button to everyone. For a guest
    // that was not merely inert: `_toggleMembership` bails only on a null
    // `userId` and a guest row has one, so the tap would have written shelf
    // membership Kotlin does not allow a guest to write — and the copy on
    // `CourseDetailScreen`, gated in the same phase, would have pushed it to
    // the server. Hardening rather than a live bug closed, since no port
    // writer currently creates a guest row at all (`UserMapper` says so).
    await pumpCourse(
      tester,
      courses: Stream.value(courseRow(joined: false)),
      userId: 'guest_ada',
    );

    expect(find.text('Step title 0'), findsOneWidget);
    expect(find.text('Next'), findsNothing);
    expect(find.text('Add to my courses'), findsNothing);
  });

  /// The gate's Kotlin is `setCourseData:213`'s `isGuest()`, which is the id
  /// prefix **or** a `guest` role without a `learner` role
  /// (`UserEntity.kt:178-182`). The port read the id prefix alone, so a user
  /// Planet marks a guest by role — with an ordinary `org.couchdb.user:` id —
  /// was offered a join button Kotlin withholds.
  testWidgets('a guest by role, with an ordinary id, gets no join button', (
    tester,
  ) async {
    await pumpCourse(
      tester,
      courses: Stream.value(courseRow(joined: false)),
      sessionUser: UserRow(
        id: 'org.couchdb.user:jane',
        couchId: 'org.couchdb.user:jane',
        name: 'jane',
        rolesList: const ['guest'],
        userAdmin: false,
        joinDate: 0,
        isArchived: false,
        isUpdated: false,
      ),
    );

    // Positive control first, as this file's three sibling guest tests have:
    // without it the assertion below also passes on a screen that rendered
    // nothing at all.
    expect(find.text('Step title 0'), findsOneWidget);
    expect(find.text('Add to my courses'), findsNothing);
    // The learner clause is `!= true`, so `['guest', 'learner']` is not a
    // guest — covered as a unit in `user_mapper_test.dart`; here the point is
    // only that this screen asks the wide question.
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

  testWidgets('joining brings the navigation back', (tester) async {
    // **The audit's gap, and it was a real one.** Every other test in this
    // file overrides `courseProvider` with a stream, which
    // `_toggleMembership`'s `ref.invalidate(courseProvider)` cannot move — so
    // the *leave* direction was covered by pushing a row at the provider and
    // the *join* direction was covered by a doc comment asserting "joining
    // restores the buttons on both sides" and nothing else.
    //
    // This one taps the button and lets the real Drift stream re-emit, which
    // is the port's equivalent of `addRemoveCourse`'s success path calling
    // `setCourseData()` → `updateNavigationVisibility()` (`:445`, `:237`).
    final db = AppDatabase.memory();
    addTearDown(db.close);
    await db.courseDao.upsertAll(
      [
        buildCourseCompanion(
          id: 'course-1',
          courseTitle: 'Algebra',
          userId: const [],
        ),
      ],
      [
        for (var i = 0; i < 3; i++)
          CourseStepsCompanion.insert(
            id: 's$i',
            courseId: const Value('course-1'),
            stepTitle: Value('Step title $i'),
            stepIndex: Value(i),
          ),
      ],
    );

    SharedPreferences.setMockInitialValues({});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());

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
          appDatabaseProvider.overrideWithValue(db),
          sessionProvider.overrideWith(
            () => _Session(buildUserRow(id: 'user-1', name: 'ada')),
          ),
        ],
        fallbackDatabase: false,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Next'), findsNothing, reason: 'not joined yet');

    await tester.tap(find.text('Add to my courses'));
    await tester.pumpAndSettle();

    expect(find.text('Next'), findsOneWidget);
    expect(find.text('Remove from my courses'), findsOneWidget);

    // Two timers outlive the assertions here, and only one of them is the
    // obvious one.
    //
    // `_toggleMembership` ends in a SnackBar whose four-second dismiss timer
    // `pumpAndSettle` does **not** clear — the trap
    // `step_next_lock_test.dart`'s `letSnackBarExpire` exists for. Pumping it
    // out is necessary and was not sufficient: the survivor is a
    // *zero*-duration timer from Drift itself,
    // `StreamQueryStore.markAsClosed` via `QueryStream._onCancelOrPause`,
    // scheduled when `ref.invalidate(courseProvider)` cancels the old query
    // stream. It is created during teardown, after the last pump a test body
    // can reach, so no amount of pumping inside the body clears it and the
    // test dies on `'!timersPending'` with its expectations already green —
    // another hang-shaped failure that says nothing about the code.
    //
    // Unmounting first moves the disposal inside the test, which is why
    // `course_progress_on_open_test.dart` — the other file here that lets a
    // real Drift stream reach a screen — carries the same helper.
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 1));
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
