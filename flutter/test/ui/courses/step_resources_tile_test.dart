import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/ratings_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/ui/courses/course_detail_screen.dart';
import 'package:myplanet/ui/courses/take_course_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/widget_harness.dart';

/// The step's resource count, and the chevron that used to sit beside it.
///
/// `_StepContent` rendered `ListTile(title: resourcesInStep(n), trailing:
/// Icon(Icons.chevron_right))` with **no `onTap`** — a disclosure arrow
/// promising a screen nothing implements, reported by Phase 128 as its item 1
/// and still there in Phase 139. The other two tiles on that page keep their
/// chevrons, because those two navigate.
///
/// **Kotlin has nothing for the arrow to open, and the reason is worth having
/// in a test file rather than only in a notes file.** Phase 128 named
/// `BaseContainerFragment.setResourceButton` as the live analogue; that button
/// is real but it is **course-level**, bound once from `CourseDetailFragment`
/// out of `CourseDetailModel.resources` (`CourseDetailFragment.kt:91`,
/// `CoursesRepositoryImpl.kt:137`). The per-step counterpart is
/// `CourseStepFragment.btnResources`, which is dead twice over — no listener is
/// ever attached to it and `setListeners()` sets it `View.GONE`. What
/// `CourseStepFragment` actually shows for a step's resources is not a button
/// at all but an inline list (`setupInlineResources()`, `tvResourcesHeader` +
/// `rvInlineResources`, each row opening `openResource(library)`).
///
/// The port cannot render that list today and this is the reachability
/// question, not a styling one: Kotlin knows a step's resources because
/// `queueCourseResources` writes every embedded resource document into
/// `my_library` stamped with its `courseId` and `stepId`
/// (`CoursesRepositoryImpl.kt:687`, `:794`), and `getAllStepResources` reads
/// them back with `myLibraryDao.getByStepId`. The port's `CourseMapper`
/// keeps `resources.length` and **discards the documents**, its
/// `my_library` table has no `stepId` column at all, and `MyLibraryMapper`
/// writes no `courseId` either — so `noOfResources` is a number about rows the
/// port does not have. Until that walk exists there is nowhere for a tap to
/// go, which is why the fix here is to stop advertising one.
void main() {
  Future<Override> prefsOverride() async {
    SharedPreferences.setMockInitialValues({});
    return planetPrefsProvider.overrideWithValue(
      PlanetPrefs(await SharedPreferences.getInstance()),
    );
  }

  Future<void> pumpTakeCourse(WidgetTester tester) async {
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
          await prefsOverride(),
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
          courseStepsProvider('course-1').overrideWith(
            (ref) => Stream.value([
              buildStepRow(id: 's0', stepTitle: 'First', noOfResources: 3),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> pumpCourseDetail(WidgetTester tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const CourseDetailScreen(courseId: 'course-1'),
        overrides: [
          await prefsOverride(),
          sessionProvider.overrideWith(
            () => _Session(buildUserRow(id: 'user-1', name: 'ada')),
          ),
          courseProvider('course-1').overrideWith(
            (ref) => Stream.value(
              buildCourseRow(id: 'course-1', courseTitle: 'Algebra'),
            ),
          ),
          courseStepsProvider('course-1').overrideWith(
            (ref) => Stream.value([
              buildStepRow(id: 's0', stepTitle: 'First', noOfResources: 3),
            ]),
          ),
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

  testWidgets('the step still says how many resources it has', (tester) async {
    // The count itself is not the defect and must survive the fix — it is the
    // one thing the port does know, and `CourseStepFragment` shows the same
    // number (`R.string.resources_size`, from `data.resources.size`).
    await pumpTakeCourse(tester);

    expect(find.text('3 resources'), findsOneWidget);
  });

  testWidgets('the resource count offers no chevron to tap', (tester) async {
    // Pre-fix: one `chevron_right` on a step whose only tile is the resource
    // count. This step carries no exam and no survey, so the two tiles that
    // legitimately own a chevron are absent and the finder is unambiguous.
    await pumpTakeCourse(tester);

    expect(find.byIcon(Icons.chevron_right), findsNothing);
  });

  testWidgets('the course-detail step tile offers none either', (tester) async {
    // Phase 128's item said the dead chevron was in "both" screens and
    // Phase 139 repeated it. **It is not in this one** — `_StepTile` is an
    // `ExpansionTile` whose count is a subtitle and whose trailing arrow does
    // what it says, expanding the description. Pinned rather than assumed,
    // because the claim survived two rounds of notes unchecked.
    await pumpCourseDetail(tester);

    expect(find.text('3 resources'), findsOneWidget);
    expect(find.byIcon(Icons.chevron_right), findsNothing);
  });
}

class _Session extends SessionNotifier {
  _Session(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}
