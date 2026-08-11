import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/course_mapper.dart';

void main() {
  Map<String, dynamic> courseDoc({
    String id = 'course-1',
    String title = 'Álgebra Básica',
    List<Map<String, dynamic>>? steps,
  }) {
    return {
      '_id': id,
      '_rev': '2-abc',
      'courseTitle': title,
      'description': 'An intro course',
      'languageOfInstruction': 'Spanish',
      'gradeLevel': 'Primary',
      'subjectLevel': 'Mathematics',
      'method': 'self-paced',
      'memberLimit': 30,
      'createdDate': 1700000000000,
      'steps': ?steps,
    };
  }

  group('fromDoc — course fields', () {
    test('maps the scalar fields', () {
      final parsed = CourseMapper.fromDoc(courseDoc());

      expect(parsed, isNotNull);
      final course = parsed!.course;
      expect(course.id.value, 'course-1');
      expect(course.couchId.value, 'course-1');
      expect(course.courseId.value, 'course-1');
      expect(course.rev.value, '2-abc');
      expect(course.courseTitle.value, 'Álgebra Básica');
      expect(course.courseTitleNormal.value, 'algebra basica');
      expect(course.description.value, 'An intro course');
      expect(course.languageOfInstruction.value, 'Spanish');
      expect(course.gradeLevel.value, 'Primary');
      expect(course.subjectLevel.value, 'Mathematics');
      expect(course.memberLimit.value, 30);
      expect(course.createdDate.value, 1700000000000);
    });

    test('defaults missing fields rather than throwing', () {
      final parsed = CourseMapper.fromDoc({'_id': 'course-1'});

      expect(parsed, isNotNull);
      expect(parsed!.course.courseTitle.value, '');
      expect(parsed.course.description.value, isNull);
      expect(parsed.course.createdDate.value, 0);
      expect(parsed.steps, isEmpty);
    });

    test('returns null for empty, design and id-less documents', () {
      expect(CourseMapper.fromDoc(const {}), isNull);
      expect(CourseMapper.fromDoc(const {'_id': '_design/courses'}), isNull);
      expect(CourseMapper.fromDoc(const {'courseTitle': 'no id'}), isNull);
    });
  });

  group('fromDoc — steps', () {
    final steps = [
      {
        'stepTitle': 'Getting started',
        'description': 'First step',
        'resources': [
          {'_id': 'r1'},
          {'_id': 'r2'},
        ],
      },
      {'stepTitle': 'Practice', 'description': 'Second step'},
    ];

    test('parses steps in order and records their position', () {
      final parsed = CourseMapper.fromDoc(courseDoc(steps: steps))!;

      expect(parsed.steps.length, 2);
      expect(parsed.steps[0].stepTitle.value, 'Getting started');
      expect(parsed.steps[0].stepIndex.value, 0);
      expect(parsed.steps[1].stepTitle.value, 'Practice');
      expect(parsed.steps[1].stepIndex.value, 1);
    });

    test('counts each step resources', () {
      final parsed = CourseMapper.fromDoc(courseDoc(steps: steps))!;

      expect(parsed.steps[0].noOfResources.value, 2);
      expect(parsed.steps[1].noOfResources.value, 0);
    });

    test('links every step back to its course', () {
      final parsed = CourseMapper.fromDoc(courseDoc(steps: steps))!;

      expect(
        parsed.steps.map((s) => s.courseId.value),
        everyElement('course-1'),
      );
    });

    /// Embedded steps have no `_id`, so the id is derived from the course and
    /// the step's position: bounded in length, and unique within a course.
    test('derives a stable id from the course and position', () {
      final first = CourseMapper.fromDoc(courseDoc(steps: steps))!;
      final second = CourseMapper.fromDoc(courseDoc(steps: steps))!;

      expect(first.steps[0].id.value, 'course-1:0');
      expect(first.steps[1].id.value, 'course-1:1');
      // Re-syncing unchanged content yields the same ids.
      expect(
        second.steps.map((s) => s.id.value),
        first.steps.map((s) => s.id.value),
      );
    });

    /// The Kotlin keys steps on `Base64(stepJson)`, so two steps with identical
    /// content collide and the upsert silently drops one.
    test('keeps two identical steps distinct', () {
      final parsed = CourseMapper.fromDoc(
        courseDoc(
          steps: [
            {'stepTitle': 'Practice', 'description': 'Repeat'},
            {'stepTitle': 'Practice', 'description': 'Repeat'},
          ],
        ),
      )!;

      expect(parsed.steps.length, 2);
      expect(parsed.steps[0].id.value, isNot(parsed.steps[1].id.value));
    });

    test('keeps identical steps in different courses distinct', () {
      final a = CourseMapper.fromDoc(
        courseDoc(
          id: 'course-a',
          steps: [
            {'stepTitle': 'Intro'},
          ],
        ),
      )!;
      final b = CourseMapper.fromDoc(
        courseDoc(
          id: 'course-b',
          steps: [
            {'stepTitle': 'Intro'},
          ],
        ),
      )!;

      expect(a.steps.single.id.value, isNot(b.steps.single.id.value));
    });

    /// Base64 of the step JSON grows with the content; a position-derived id
    /// stays short no matter how long the description is.
    test('keeps the id bounded regardless of step size', () {
      final parsed = CourseMapper.fromDoc(
        courseDoc(
          steps: [
            {'stepTitle': 'Long', 'description': 'x' * 5000},
          ],
        ),
      )!;

      expect(parsed.steps.single.id.value.length, lessThan(64));
    });

    test('skips malformed step entries', () {
      final parsed = CourseMapper.fromDoc({
        '_id': 'course-1',
        'steps': [
          'not an object',
          42,
          {'stepTitle': 'Real'},
        ],
      })!;

      expect(parsed.steps.length, 1);
      expect(parsed.steps.single.stepTitle.value, 'Real');
    });

    test('tolerates a non-list steps field', () {
      final parsed = CourseMapper.fromDoc({
        '_id': 'course-1',
        'steps': 'not a list',
      })!;

      expect(parsed.steps, isEmpty);
    });
  });

  group('mergeUserIds', () {
    test('adds the shelf id without dropping existing members', () {
      expect(
        CourseMapper.mergeUserIds(const ['user-1'], 'user-2'),
        containsAll(['user-1', 'user-2']),
      );
    });

    test('does not duplicate an existing member', () {
      expect(CourseMapper.mergeUserIds(const ['user-1'], 'user-1'), ['user-1']);
    });

    test('leaves membership untouched for a null or empty shelf id', () {
      expect(CourseMapper.mergeUserIds(const ['user-1'], null), ['user-1']);
      expect(CourseMapper.mergeUserIds(const ['user-1'], ''), ['user-1']);
    });

    test('drops blank entries already persisted in the row', () {
      expect(CourseMapper.mergeUserIds(const ['', 'user-1', ''], 'user-2'), [
        'user-1',
        'user-2',
      ]);
      expect(CourseMapper.mergeUserIds(const ['', 'user-1'], null), ['user-1']);
    });
  });
}
