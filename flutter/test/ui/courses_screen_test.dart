import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/ratings_provider.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/ui/courses/course_detail_screen.dart';
import 'package:myplanet/ui/courses/courses_screen.dart';

import '../support/widget_harness.dart';

void main() {
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
