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

    /// Embedded steps have no `_id`, so the Kotlin derives one from the step's
    /// content. The id must therefore be stable across syncs of unchanged
    /// content, and distinct for different content.
    test('derives a stable, content-distinct step id', () {
      final first = CourseMapper.fromDoc(courseDoc(steps: steps))!;
      final second = CourseMapper.fromDoc(courseDoc(steps: steps))!;

      expect(first.steps[0].id.value, second.steps[0].id.value);
      expect(first.steps[0].id.value, isNot(first.steps[1].id.value));
    });

    test('changes the step id when the step content changes', () {
      final before = CourseMapper.fromDoc(
        courseDoc(
          steps: [
            {'stepTitle': 'Original'},
          ],
        ),
      )!;
      final after = CourseMapper.fromDoc(
        courseDoc(
          steps: [
            {'stepTitle': 'Renamed'},
          ],
        ),
      )!;

      expect(before.steps.single.id.value, isNot(after.steps.single.id.value));
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
  });
}
