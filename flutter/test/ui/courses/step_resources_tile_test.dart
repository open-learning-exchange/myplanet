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
/// `CoursesRepositoryImpl.kt:136`). The per-step counterpart is
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
/// `my_library` table has neither a `stepId` nor a `courseId` column, and no
/// port mapper writes step-resource provenance at all — so `noOfResources` is
/// a number about rows the port does not have. Until that walk exists there is nowhere for a tap to
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
    // The count survives the fix, but **not** because Kotlin shows it.
    //
    // An earlier draft of this comment said `CourseStepFragment` "shows the
    // same number". It does not, and the mistake has a history worth naming:
    // that is precisely the claim **Phase 128 made and retracted in the same
    // paragraph** (`PHASE_128_NOTES.md:378-380`, *"there is no live Kotlin
    // count"*), and Phase 145 reinstated it from memory. The number is written
    // to `btnResources` at `CourseStepFragment.kt:121-122` and the button is
    // GONE at `:292`, inside a container that is `visibility="gone"`. The only
    // per-step resources UI Kotlin renders is `tv_resources_header`, whose
    // text is the bare `@string/resources` with **no count**, above the list.
    //
    // So the count is a port presentation of data the port does hold (the
    // step document's `resources` array length), standing in for a list it
    // cannot build until the walk in `PHASE_145_NOTES.md` item 1 lands. It is
    // kept because it is true and it is the only thing the screen can say
    // about a step's resources; when the list arrives it should replace this,
    // not sit beside it.
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
    // `ExpansionTile` whose trailing arrow does what it says, expanding the
    // description. Pinned rather than assumed, because the claim survived two
    // rounds of notes unchecked.
    //
    // **The resource count is gone from this tile as of Phase 149**, and only
    // from this one. Kotlin's own step row (`CoursesStepsAdapter.bind`) shows
    // the title and a second line that is never visible; it shows no resource
    // count anywhere, and `CourseStep.noOfResources` is read nowhere in
    // `app/src/main`. The count survives on `take_course_screen`, where it
    // stands in for the inline resource list the port cannot build yet — see
    // the test above — and that is a deliberate, documented port addition
    // rather than a second copy of one.
    await pumpCourseDetail(tester);

    expect(find.text('First'), findsOneWidget);
    expect(find.text('3 resources'), findsNothing);
    expect(find.byIcon(Icons.chevron_right), findsNothing);
  });
}

class _Session extends SessionNotifier {
  _Session(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}
