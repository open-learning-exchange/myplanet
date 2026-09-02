import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/exam_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/ratings_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/ui/courses/course_detail_screen.dart';

import '../../support/widget_harness.dart';

/// Mirrors the test-session notifier in `session_provider_test.dart`: returns a
/// fixed user without touching the database or prefs, so the detail screen has
/// a `userId` for the join/leave and rating buttons.
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

// A 1x1 transparent PNG — the smallest valid image Image.memory can decode.
const _pngBytes = [
  0x89,
  0x50,
  0x4E,
  0x47,
  0x0D,
  0x0A,
  0x1A,
  0x0A,
  0x00,
  0x00,
  0x00,
  0x0D,
  0x49,
  0x48,
  0x44,
  0x52,
  0x00,
  0x00,
  0x00,
  0x01,
  0x00,
  0x00,
  0x00,
  0x01,
  0x08,
  0x06,
  0x00,
  0x00,
  0x00,
  0x1F,
  0x15,
  0xC4,
  0x89,
  0x00,
  0x00,
  0x00,
  0x0D,
  0x49,
  0x44,
  0x41,
  0x54,
  0x78,
  0x9C,
  0x62,
  0x00,
  0x01,
  0x00,
  0x00,
  0x05,
  0x00,
  0x01,
  0x0D,
  0x0A,
  0x2D,
  0xB4,
  0x00,
  0x00,
  0x00,
  0x00,
  0x49,
  0x45,
  0x4E,
  0x44,
  0xAE,
  0x42,
  0x60,
  0x82,
];

void main() {
  Future<void> pumpScreen(
    WidgetTester tester, {
    required CourseRow course,
    List<CourseStepRow> steps = const [],
    List<Override> overrides = const [],
  }) async {
    await tester.pumpWidget(
      wrapScreen(
        CourseDetailScreen(courseId: course.id),
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
          courseProvider(course.id).overrideWith((ref) => Stream.value(course)),
          courseStepsProvider(
            course.id,
          ).overrideWith((ref) => Stream.value(steps)),
          // The rate button watches the ratings DAO; without this it opens a
          // drift stream against the harness fallback database and leaves a
          // pending timer.
          ratingSummaryProvider((
            type: 'course',
            itemId: course.id,
          )).overrideWith(
            (ref) => Stream.value(
              const RatingSummary(average: 0, total: 0, userRating: null),
            ),
          ),
          ...overrides,
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('renders the course header and chips', (tester) async {
    await pumpScreen(
      tester,
      course: buildCourseRow(
        id: 'c1',
        courseTitle: 'Algebra',
        gradeLevel: 'G1',
        subjectLevel: 'Mathematics',
      ),
    );
    expect(find.text('Algebra'), findsWidgets);
    expect(find.byType(Chip), findsNWidgets(2));
  });

  testWidgets('renders the description as markdown, not plain text', (
    tester,
  ) async {
    // A heading renders as larger styled text through MarkdownBody, and a
    // bold span renders as RichText — neither happens with plain Text.
    await pumpScreen(
      tester,
      course: buildCourseRow(
        id: 'c1',
        courseTitle: 'Algebra',
        description: '# Heading\n\n**bold** text',
      ),
    );
    // The heading text is present.
    expect(find.text('Heading'), findsOneWidget);
    expect(find.text('bold text'), findsOneWidget);
  });

  testWidgets('renders the cover image when a coverFileName is set', (
    tester,
  ) async {
    await pumpScreen(
      tester,
      course: buildCourseRow(
        id: 'c1',
        courseTitle: 'Algebra',
        coverFileName: 'cover.png',
      ),
      overrides: [
        courseCoverImageProvider(
          const CourseCoverImageRequest(
            courseId: 'c1',
            coverFileName: 'cover.png',
          ),
        ).overrideWith((ref) async => Uint8List.fromList(_pngBytes)),
      ],
    );
    // The cover banner widget is present and an Image.memory decoded the
    // overridden bytes.
    expect(find.byType(CourseDetailCoverImage), findsOneWidget);
    expect(find.byType(Image), findsOneWidget);
  });

  testWidgets('omits the cover banner when coverFileName is blank', (
    tester,
  ) async {
    await pumpScreen(
      tester,
      course: buildCourseRow(
        id: 'c1',
        courseTitle: 'Algebra',
        coverFileName: null,
      ),
    );
    expect(find.byType(CourseDetailCoverImage), findsNothing);
  });

  testWidgets('renders a step description as markdown', (tester) async {
    await pumpScreen(
      tester,
      course: buildCourseRow(id: 'c1', courseTitle: 'Algebra'),
      steps: [
        buildStepRow(id: 's1', stepTitle: 'First', description: '## Step A'),
      ],
    );
    // Expand the step tile to reveal its description.
    await tester.tap(find.text('First'));
    await tester.pumpAndSettle();
    expect(find.text('Step A'), findsOneWidget);
  });

  testWidgets(
    'a step carrying an embedded exam offers Take exam, from a real sync',
    (tester) async {
      // Phase 113. The reachability proof, and the reason it is written this
      // way: the join is `exams.stepId == course_steps.id`, and until this
      // phase nothing in the port ever wrote one. Overriding `stepExamProvider`
      // here would prove only that the button renders when handed an exam —
      // which it always did. So the database is filled by the real courses
      // walk from a real-shaped course document, and the provider does its own
      // lookup.
      final db = AppDatabase.memory();
      addTearDown(db.close);

      const doc = {
        '_id': 'c1',
        'courseTitle': 'Water',
        'steps': [
          {
            'stepTitle': 'Assessment',
            'exam': {
              '_id': 'exam-1',
              'type': 'courses',
              'name': 'Step test',
              'questions': [
                {'id': 'q1', 'title': 'Which is wet?', 'type': 'input'},
              ],
            },
          },
        ],
      };
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

      final steps = await db.courseDao.getSteps('c1');
      await pumpScreen(
        tester,
        course: buildCourseRow(id: 'c1', courseTitle: 'Water'),
        steps: steps,
        overrides: [appDatabaseProvider.overrideWithValue(db)],
      );

      await tester.tap(find.text('Assessment'));
      await tester.pumpAndSettle();
      expect(find.widgetWithText(FilledButton, 'Take exam'), findsOneWidget);
    },
  );
}
