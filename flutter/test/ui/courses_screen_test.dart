import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/ratings_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/progress_repository.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/ui/courses/course_detail_screen.dart';
import 'package:myplanet/ui/courses/courses_screen.dart';

import '../support/widget_harness.dart';

/// A stand-in [ProgressRepository] for the progress-filter tests. Only
/// [courseProgressSummary] is exercised; the rest throw so a stray call is
/// loud rather than silently returning empty.
class _FakeProgressRepository implements ProgressRepository {
  _FakeProgressRepository(this._summary);

  final Map<String, CourseProgressSummary> _summary;

  @override
  Future<Map<String, CourseProgressSummary>> courseProgressSummary(
    List<String> courseIds,
    String? userId,
  ) async {
    return {
      for (final id in courseIds)
        id: _summary[id] ?? const CourseProgressSummary(max: 0, current: 0),
    };
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('ProgressRepository.${invocation.memberName}');
}

/// A [SessionNotifier] that returns a fixed user so the multi-select
/// actions have a userId to write shelf membership against.
class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}

void main() {
  /// Reveals a filter-bar control that sits off-screen to the right, by
  /// dragging the bar's horizontal Scrollable leftward until [finder] is hit.
  /// The filter bar is the last horizontal Scrollable on the screen (the
  /// SearchBar's overlay, when open, is the other).
  Future<void> revealInFilterBar(
    WidgetTester tester,
    Finder finder, {
    double step = -400,
  }) async {
    // The filter bar is the last horizontal Scrollable on the screen.
    final scroll = find
        .byWidgetPredicate(
          (w) => w is Scrollable && w.axisDirection == AxisDirection.right,
        )
        .last;
    for (var i = 0; i < 8; i++) {
      if (finder.evaluate().isNotEmpty) {
        final center = tester.getCenter(finder);
        if (center.dx > 0 && center.dx < 800) break;
      }
      await tester.drag(scroll, Offset(step, 0));
      await tester.pumpAndSettle();
    }
  }

  /// The filter bar reads the level providers, which otherwise reach for the
  /// real on-device database. Stubbing them keeps these tests hermetic.
  List<Override> courseOverrides(
    List<CourseRow> rows, {
    List<String> grades = const [],
    List<String> subjects = const [],
  }) {
    return [
      coursesStreamProvider.overrideWith((ref) => Stream.value(rows)),
      gradeLevelsProvider.overrideWith((ref) => Stream.value(grades)),
      subjectLevelsProvider.overrideWith((ref) => Stream.value(subjects)),
    ];
  }

  group('CoursesScreen', () {
    testWidgets('renders the courses returned by the stream', (tester) async {
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: courseOverrides([
            buildCourseRow(
              id: 'c1',
              courseTitle: 'Álgebra Básica',
              gradeLevel: 'Primary',
              subjectLevel: 'Mathematics',
            ),
            buildCourseRow(id: 'c2', courseTitle: 'Biology'),
          ]),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Álgebra Básica'), findsOneWidget);
      expect(find.text('Biology'), findsOneWidget);
      // Grade and subject are joined into the subtitle.
      expect(find.text('Primary · Mathematics'), findsOneWidget);
    });

    testWidgets('toggles between list and grid view', (tester) async {
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: courseOverrides([
            buildCourseRow(id: 'c1', courseTitle: 'Algebra'),
            buildCourseRow(id: 'c2', courseTitle: 'Biology'),
          ]),
        ),
      );
      await tester.pumpAndSettle();

      // Default is list mode in tests — tiles are ListTile widgets.
      expect(find.byType(ListTile), findsNWidgets(2));
      expect(find.byType(GridTile), findsNothing);

      // Tap the grid-view toggle.
      await tester.tap(find.byTooltip('Grid view'));
      await tester.pumpAndSettle();

      // Now the list renders as a GridView with Card-based tiles.
      expect(find.byType(GridView), findsOneWidget);
      expect(find.byType(ListTile), findsNothing);

      // Tap the list-view toggle to go back.
      await tester.tap(find.byTooltip('List view'));
      await tester.pumpAndSettle();

      expect(find.byType(ListTile), findsNWidgets(2));
      expect(find.byType(GridView), findsNothing);
    });

    testWidgets('shows the empty state when nothing is synced', (tester) async {
      await tester.pumpWidget(
        wrapScreen(const CoursesScreen(), overrides: courseOverrides(const [])),
      );
      await tester.pumpAndSettle();

      expect(find.text('No data available'), findsOneWidget);
    });

    testWidgets('typing in the search box updates the filter', (tester) async {
      await tester.pumpWidget(
        wrapScreen(const CoursesScreen(), overrides: courseOverrides(const [])),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(CoursesScreen)),
      );

      await tester.enterText(find.byType(SearchBar), 'algebra');
      await tester.pump();

      expect(container.read(courseFilterProvider).query, 'algebra');
    });

    testWidgets('the my-courses chip toggles the filter', (tester) async {
      await tester.pumpWidget(
        wrapScreen(const CoursesScreen(), overrides: courseOverrides(const [])),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(CoursesScreen)),
      );

      expect(container.read(courseFilterProvider).myCoursesOnly, isFalse);

      await tester.tap(find.widgetWithText(FilterChip, 'My courses'));
      await tester.pumpAndSettle();

      expect(container.read(courseFilterProvider).myCoursesOnly, isTrue);
    });

    testWidgets('clear filters resets the filter state', (tester) async {
      await tester.pumpWidget(
        wrapScreen(const CoursesScreen(), overrides: courseOverrides(const [])),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(CoursesScreen)),
      );

      await tester.tap(find.widgetWithText(FilterChip, 'My courses'));
      await tester.pumpAndSettle();

      // The filter bar is a horizontal scroller; with the sort and progress
      // controls added the Clear-filters chip lives off-screen. Drag the bar's
      // Scrollable leftward twice to bring it into view.
      final filterScroll = find
          .byWidgetPredicate(
            (w) => w is Scrollable && w.axisDirection == AxisDirection.right,
          )
          .last;
      await tester.drag(filterScroll, const Offset(-400, 0));
      await tester.pumpAndSettle();
      await tester.drag(filterScroll, const Offset(-400, 0));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ActionChip, 'Clear filters'));
      await tester.pumpAndSettle();

      expect(container.read(courseFilterProvider).myCoursesOnly, isFalse);
    });

    testWidgets('renders Spanish strings under the es locale', (tester) async {
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: courseOverrides(const []),
          locale: const Locale('es'),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Cursos'), findsOneWidget);
      expect(find.text('No hay datos disponibles.'), findsOneWidget);
    });

    testWidgets('sorting by title orders the list and flips on re-tap', (
      tester,
    ) async {
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: courseOverrides([
            buildCourseRow(id: 'b', courseTitle: 'Beta'),
            buildCourseRow(id: 'a', courseTitle: 'Alpha'),
          ]),
        ),
      );
      await tester.pumpAndSettle();

      List<String> visibleTitles() => tester
          .widgetList<ListTile>(find.byType(ListTile))
          .map((t) => ((t.title as Text).data) ?? '')
          .where((s) => s == 'Alpha' || s == 'Beta')
          .toList();

      // Tap "Order by Title" once — active, ascending (A→Z).
      await revealInFilterBar(tester, find.text('Order by Title'));
      await tester.tap(find.text('Order by Title'));
      await tester.pumpAndSettle();
      expect(visibleTitles(), ['Alpha', 'Beta']);
      // Tap again — flips to descending (Z→A).
      await tester.tap(find.text('Order by Title'));
      await tester.pumpAndSettle();
      expect(visibleTitles(), ['Beta', 'Alpha']);
    });

    testWidgets('sorting by date orders oldest-first then newest-first', (
      tester,
    ) async {
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: courseOverrides([
            buildCourseRow(id: 'new', courseTitle: 'Newer', createdDate: 200),
            buildCourseRow(id: 'old', courseTitle: 'Older', createdDate: 100),
          ]),
        ),
      );
      await tester.pumpAndSettle();

      List<String> visibleIds() {
        // The course id is not shown on the tile, so identify tiles by title.
        return tester
            .widgetList<ListTile>(find.byType(ListTile))
            .map((t) => ((t.title as Text).data) ?? '')
            .where((s) => s == 'Newer' || s == 'Older')
            .map((title) => title == 'Newer' ? 'new' : 'old')
            .toList();
      }

      // Kotlin's `isDateAscending` starts true; the first toggle flips it to
      // false (descending — newest first), mirroring `CoursesViewModel`.
      await revealInFilterBar(tester, find.text('Order by Date'));
      await tester.tap(find.text('Order by Date'));
      await tester.pumpAndSettle();
      expect(visibleIds(), ['new', 'old']);
      await tester.tap(find.text('Order by Date'));
      await tester.pumpAndSettle();
      expect(visibleIds(), ['old', 'new']);
    });

    testWidgets('the progress filter narrows the list by completion state', (
      tester,
    ) async {
      final summary = {
        'notStarted': const CourseProgressSummary(max: 3, current: 0),
        'inProgress': const CourseProgressSummary(max: 3, current: 1),
        'completed': const CourseProgressSummary(max: 3, current: 3),
      };
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: [
            ...courseOverrides([
              buildCourseRow(id: 'notStarted', courseTitle: 'Not Started'),
              buildCourseRow(id: 'inProgress', courseTitle: 'In Progress'),
              buildCourseRow(id: 'completed', courseTitle: 'Completed'),
            ]),
            progressRepositoryProvider.overrideWithValue(
              _FakeProgressRepository(summary),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      // All three are visible by default.
      expect(find.text('Not Started'), findsOneWidget);
      expect(find.text('In Progress'), findsOneWidget);
      expect(find.text('Completed'), findsOneWidget);

      // The progress dropdown sits after the grade and subject dropdowns in
      // the filter bar's horizontal scroll. Reveal it, open it, and pick
      // "Completed".
      final progressDropdown = find.byType(
        DropdownButton<CourseProgressFilter>,
      );
      await revealInFilterBar(tester, progressDropdown);
      await tester.tap(progressDropdown);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Completed').last);
      await tester.pumpAndSettle();

      // After picking "Completed", the dropdown itself displays "Completed"
      // (its selected value) and the one remaining course tile is "Completed",
      // so the text appears twice. Assert via the course tiles only.
      final tiles = tester
          .widgetList<ListTile>(find.byType(ListTile))
          .map((t) => ((t.title as Text).data) ?? '')
          .where(
            (s) => s == 'Not Started' || s == 'In Progress' || s == 'Completed',
          )
          .toList();
      expect(tiles, ['Completed']);
    });

    testWidgets('grid tile shows the subject fallback when no cover image', (
      tester,
    ) async {
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: courseOverrides([
            buildCourseRow(
              id: 'c1',
              courseTitle: 'Algebra',
              subjectLevel: 'Mathematics',
            ),
          ]),
        ),
      );
      await tester.pumpAndSettle();

      // Switch to grid view to render _CourseGridTile.
      await tester.tap(find.byTooltip('Grid view'));
      await tester.pumpAndSettle();

      // No cover image is fetched — the fallback container with the math icon
      // is shown.
      expect(find.byIcon(Icons.calculate_outlined), findsOneWidget);
      expect(find.text('Mathematics'), findsWidgets);
      expect(find.byType(Image), findsNothing);
    });

    testWidgets('grid tile shows the cover image when one is available', (
      tester,
    ) async {
      // A 1x1 transparent PNG — the smallest valid image Image.memory can
      // decode without throwing.
      final pngBytes = [
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4,
        0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41, // IDAT chunk
        0x54, 0x78, 0x9C, 0x62, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4, 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, // IEND chunk
        0x42, 0x60, 0x82,
      ];

      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: [
            ...courseOverrides([
              buildCourseRow(
                id: 'c1',
                courseTitle: 'Algebra',
                subjectLevel: 'Mathematics',
                coverFileName: 'cover.png',
              ),
            ]),
            courseCoverImageProvider(
              const CourseCoverImageRequest(
                courseId: 'c1',
                coverFileName: 'cover.png',
              ),
            ).overrideWith((ref) async => Uint8List.fromList(pngBytes)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      // Switch to grid view.
      await tester.tap(find.byTooltip('Grid view'));
      await tester.pumpAndSettle();

      // The cover image is rendered — Image.memory is present and the
      // subject fallback icon is not.
      expect(find.byType(Image), findsOneWidget);
      expect(find.byIcon(Icons.calculate_outlined), findsNothing);
    });

    testWidgets('long-press enters selection mode with a select-all bar', (
      tester,
    ) async {
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: courseOverrides([
            buildCourseRow(id: 'c1', courseTitle: 'Algebra'),
            buildCourseRow(id: 'c2', courseTitle: 'Biology'),
          ]),
        ),
      );
      await tester.pumpAndSettle();

      // No selection bar before long-press.
      expect(find.text('Select All'), findsNothing);

      await tester.longPress(find.text('Algebra'));
      await tester.pumpAndSettle();

      // The bar appears, and both tiles are in selection mode (one checkbox
      // each); the long-pressed course is checked, the other is not.
      expect(find.text('Select All'), findsOneWidget);
      expect(find.byType(Checkbox), findsNWidgets(2));
      expect(
        tester.widgetList<Checkbox>(find.byType(Checkbox)).first.value,
        isTrue,
      );
      expect(
        tester.widgetList<Checkbox>(find.byType(Checkbox)).last.value,
        isFalse,
      );
    });

    testWidgets('select-all toggles every tile then unselects', (tester) async {
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: courseOverrides([
            buildCourseRow(id: 'c1', courseTitle: 'Algebra'),
            buildCourseRow(id: 'c2', courseTitle: 'Biology'),
          ]),
        ),
      );
      await tester.pumpAndSettle();

      await tester.longPress(find.text('Algebra'));
      await tester.pumpAndSettle();

      // Tap "Select All" — both tiles gain a checkbox.
      await tester.tap(find.text('Select All'));
      await tester.pumpAndSettle();
      expect(find.byType(Checkbox), findsNWidgets(2));
      // The toggle text flips to Unselect All.
      expect(find.text('Unselect All'), findsOneWidget);

      // Tap again to clear.
      await tester.tap(find.text('Unselect All'));
      await tester.pumpAndSettle();
      // Tiles still show checkboxes (unchecked) — selection mode persists
      // so the user can re-select without long-pressing again.
      expect(find.byType(Checkbox), findsNWidgets(2));
      expect(
        tester.widgetList<Checkbox>(find.byType(Checkbox)).first.value,
        isFalse,
      );
      // The toggle text flips back to Select All (none selected).
      expect(find.text('Select All'), findsOneWidget);
    });

    testWidgets('add-to-my-courses writes shelf membership', (tester) async {
      final user = UserRow(
        id: 'user-1',
        name: 'ada',
        rolesList: const ['learner'],
        userAdmin: false,
        joinDate: 0,
        isArchived: false,
        isUpdated: false,
      );
      final db = AppDatabase.memory();
      addTearDown(() async => db.close());
      await db.courseDao.upsertAll([
        buildCourseCompanion(id: 'c1', courseTitle: 'Algebra'),
        buildCourseCompanion(id: 'c2', courseTitle: 'Biology'),
      ], const []);
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: [
            coursesStreamProvider.overrideWith(
              (ref) => Stream.value([
                buildCourseRow(id: 'c1', courseTitle: 'Algebra'),
                buildCourseRow(id: 'c2', courseTitle: 'Biology'),
              ]),
            ),
            gradeLevelsProvider.overrideWith((ref) => Stream.value(const [])),
            subjectLevelsProvider.overrideWith((ref) => Stream.value(const [])),
            sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
            courseDaoProvider.overrideWith((ref) => db.courseDao),
            removedLogDaoProvider.overrideWith((ref) => db.removedLogDao),
          ],
        ),
      );
      await tester.pumpAndSettle();

      // The two synced courses appear through the overridden stream.
      expect(find.text('Algebra'), findsOneWidget);
      expect(find.text('Biology'), findsOneWidget);

      await tester.longPress(find.text('Algebra'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Select All'));
      await tester.pumpAndSettle();

      // Add both to the user's shelf.
      await tester.tap(find.text('Add to my courses'));
      await tester.pumpAndSettle();

      // The snackbar reports the batch, and selection mode exits.
      expect(find.text('2 courses added'), findsOneWidget);
      expect(find.text('Select All'), findsNothing);

      // The membership rows were actually written to the db.
      expect(await db.courseDao.isMyCourse('c1', 'user-1'), isTrue);
      expect(await db.courseDao.isMyCourse('c2', 'user-1'), isTrue);
    });

    testWidgets('leave shows a confirm dialog before writing', (tester) async {
      final user = UserRow(
        id: 'user-1',
        name: 'ada',
        rolesList: const ['learner'],
        userAdmin: false,
        joinDate: 0,
        isArchived: false,
        isUpdated: false,
      );
      final db = AppDatabase.memory();
      addTearDown(() async => db.close());
      await db.courseDao.upsertAll([
        buildCourseCompanion(id: 'c1', courseTitle: 'Algebra'),
      ], const []);
      await tester.pumpWidget(
        wrapScreen(
          const CoursesScreen(),
          overrides: [
            ...courseOverrides([
              buildCourseRow(
                id: 'c1',
                courseTitle: 'Algebra',
                userId: const ['user-1'],
              ),
            ]),
            sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
            courseDaoProvider.overrideWith((ref) => db.courseDao),
            removedLogDaoProvider.overrideWith((ref) => db.removedLogDao),
          ],
        ),
      );
      await tester.pumpAndSettle();

      // Switch to the My Courses view so the leave action is offered.
      await tester.tap(find.text('My courses'));
      await tester.pumpAndSettle();

      await tester.longPress(find.text('Algebra'));
      await tester.pumpAndSettle();

      // My-courses view shows the leave action, not add.
      expect(find.text('Add to my courses'), findsNothing);
      expect(find.text('Remove from my courses'), findsOneWidget);

      await tester.tap(find.text('Remove from my courses'));
      await tester.pumpAndSettle();

      // The confirmation dialog appears first; no snackbar yet.
      expect(
        find.text('Are you sure you want to remove this course?'),
        findsOneWidget,
      );
      expect(find.text('1 course removed'), findsNothing);

      // Confirm — the membership write fires and selection exits.
      await tester.tap(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.widgetWithText(FilledButton, 'Remove from my courses'),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('1 course removed'), findsOneWidget);
      expect(find.text('Select All'), findsNothing);
    });
  });

  group('CourseDetailScreen', () {
    testWidgets('renders the course header and its ordered steps', (
      tester,
    ) async {
      await tester.pumpWidget(
        wrapScreen(
          const CourseDetailScreen(courseId: 'course-1'),
          overrides: [
            courseProvider('course-1').overrideWith(
              (ref) => Stream.value(
                buildCourseRow(
                  id: 'course-1',
                  courseTitle: 'Algebra',
                  description: 'An intro course',
                  gradeLevel: 'Primary',
                ),
              ),
            ),
            courseStepsProvider('course-1').overrideWith(
              (ref) => Stream.value([
                buildStepRow(id: 's1', stepTitle: 'First', noOfResources: 2),
                buildStepRow(id: 's2', stepTitle: 'Second', stepIndex: 1),
              ]),
            ),
            // The rate button watches the ratings DAO; without this it opens a
            // drift stream against the harness fallback database.
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

      expect(find.text('An intro course'), findsOneWidget);
      expect(find.text('First'), findsOneWidget);
      expect(find.text('Second'), findsOneWidget);
      expect(find.text('2 steps'), findsOneWidget);
      // Resource counts are pluralised per step.
      expect(find.text('2 resources'), findsOneWidget);
      expect(find.text('No resources'), findsOneWidget);
    });

    testWidgets('shows a not-found message for an unknown course', (
      tester,
    ) async {
      await tester.pumpWidget(
        wrapScreen(
          const CourseDetailScreen(courseId: 'missing'),
          overrides: [
            courseProvider('missing').overrideWith((ref) => Stream.value(null)),
            courseStepsProvider(
              'missing',
            ).overrideWith((ref) => Stream.value(const [])),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Course not found'), findsOneWidget);
    });
  });
}
