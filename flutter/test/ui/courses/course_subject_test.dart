import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/ui/courses/course_subject.dart';

void main() {
  group('classifyCourseSubject', () {
    test('returns mathematics for subject containing "math"', () {
      expect(classifyCourseSubject('Mathematics'), CourseSubject.mathematics);
      expect(classifyCourseSubject('Applied Math'), CourseSubject.mathematics);
      expect(classifyCourseSubject('MATH 101'), CourseSubject.mathematics);
    });

    test('returns health for subject containing "health"', () {
      expect(classifyCourseSubject('Health'), CourseSubject.health);
      expect(classifyCourseSubject('Public Health'), CourseSubject.health);
    });

    test(
      'returns socialStudies for subject containing "social" or "civic"',
      () {
        expect(
          classifyCourseSubject('Social Studies'),
          CourseSubject.socialStudies,
        );
        expect(
          classifyCourseSubject('Civic Education'),
          CourseSubject.socialStudies,
        );
      },
    );

    test('returns technology for computer/technology/ict subjects', () {
      expect(
        classifyCourseSubject('Computer Science'),
        CourseSubject.technology,
      );
      expect(classifyCourseSubject('Technology'), CourseSubject.technology);
      expect(classifyCourseSubject('ICT Basics'), CourseSubject.technology);
    });

    test('returns literacy as the default', () {
      expect(classifyCourseSubject('Reading'), CourseSubject.literacy);
      expect(classifyCourseSubject('English'), CourseSubject.literacy);
    });

    test('returns literacy for null or empty subject', () {
      expect(classifyCourseSubject(null), CourseSubject.literacy);
      expect(classifyCourseSubject(''), CourseSubject.literacy);
    });
  });

  group('courseSubjectColor', () {
    test('returns the Kotlin palette colour for each subject', () {
      // The values are the colors.xml subject_* entries.
      expect(
        courseSubjectColor(CourseSubject.mathematics),
        const Color(0xFFF57C00),
      );
      expect(
        courseSubjectColor(CourseSubject.literacy),
        const Color(0xFFD32F2F),
      );
      expect(courseSubjectColor(CourseSubject.health), const Color(0xFF388E3C));
      expect(
        courseSubjectColor(CourseSubject.socialStudies),
        const Color(0xFF7B1FA2),
      );
      expect(
        courseSubjectColor(CourseSubject.technology),
        const Color(0xFF1976D2),
      );
    });
  });

  group('courseSubjectIcon', () {
    test('returns a distinct icon for each subject', () {
      final icons = CourseSubject.values.map(courseSubjectIcon).toSet();
      expect(icons.length, CourseSubject.values.length);
    });
  });
}
