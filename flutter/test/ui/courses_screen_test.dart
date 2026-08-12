import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/ratings_provider.dart';
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
