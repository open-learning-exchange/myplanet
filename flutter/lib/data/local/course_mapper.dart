import 'dart:convert';

import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import '../../core/utils/text_utils.dart';
import 'app_database.dart';

/// A course document parsed into the rows it produces.
///
/// Port of `CoursesRepositoryImpl.ParsedCourseSyncPayload`, minus the exam and
/// question halves — those arrive with the `ui/exam` package.
class ParsedCourse {
  const ParsedCourse({required this.course, required this.steps});

  final CoursesCompanion course;
  final List<CourseStepsCompanion> steps;
}

/// Port of the course-parsing half of `repository/CoursesRepositoryImpl.kt`
/// (`parseCourseDocument`).
class CourseMapper {
  const CourseMapper._();

  /// Returns `null` for an empty document or a `_design/*` doc.
  static ParsedCourse? fromDoc(
    Map<String, dynamic> doc, {
    List<String> existingUserIds = const [],
    String? shelfId,
  }) {
    if (doc.isEmpty) return null;

    final courseId = JsonUtils.getString('_id', doc);
    if (courseId.isEmpty || courseId.startsWith('_design')) return null;

    final title = JsonUtils.getString('courseTitle', doc);
    final steps = _parseSteps(doc, courseId);

    return ParsedCourse(
      course: CoursesCompanion(
        id: Value(courseId),
        couchId: Value(courseId),
        courseId: Value(courseId),
        rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
        courseTitle: Value(title),
        courseTitleNormal: Value(normalizeText(title)),
        description: Value(JsonUtils.getStringOrNull('description', doc)),
        languageOfInstruction: Value(
          JsonUtils.getStringOrNull('languageOfInstruction', doc),
        ),
        method: Value(JsonUtils.getStringOrNull('method', doc)),
        gradeLevel: Value(JsonUtils.getStringOrNull('gradeLevel', doc)),
        subjectLevel: Value(JsonUtils.getStringOrNull('subjectLevel', doc)),
        memberLimit: Value(JsonUtils.getInt('memberLimit', doc)),
        createdDate: Value(JsonUtils.getLong('createdDate', doc)),
        coverFileName: Value(JsonUtils.getStringOrNull('coverFileName', doc)),
        userId: Value(mergeUserIds(existingUserIds, shelfId)),
      ),
      steps: steps,
    );
  }

  /// Port of `MyCourse.setUserId` / `mergeUserIds` — shelf membership is a
  /// union, so syncing a second user's shelf does not evict the first.
  static List<String> mergeUserIds(List<String> existing, String? shelfId) {
    if (shelfId == null || shelfId.isEmpty) return existing;
    return {...existing, shelfId}.toList(growable: false);
  }

  /// Port of the `steps` loop in `parseCourseDocument`.
  ///
  /// The Kotlin derives each step id as `Base64(stepJson.toString())` — a
  /// content hash, since embedded steps carry no `_id` of their own. That is
  /// replicated here with `base64(jsonEncode(step))`. The bytes need not match
  /// the Kotlin's: under the drop-and-resync policy the id only has to be
  /// stable across syncs *within* this app, which content-derivation gives.
  static List<CourseStepsCompanion> _parseSteps(
    Map<String, dynamic> doc,
    String courseId,
  ) {
    final rawSteps = doc['steps'];
    if (rawSteps is! List) return const [];

    final steps = <CourseStepsCompanion>[];
    for (var i = 0; i < rawSteps.length; i++) {
      final step = rawSteps[i];
      if (step is! Map<String, dynamic>) continue;

      final resources = step['resources'];
      steps.add(
        CourseStepsCompanion(
          id: Value(stepIdFor(step)),
          courseId: Value(courseId),
          stepTitle: Value(JsonUtils.getStringOrNull('stepTitle', step)),
          description: Value(JsonUtils.getStringOrNull('description', step)),
          noOfResources: Value(resources is List ? resources.length : 0),
          stepIndex: Value(i),
        ),
      );
    }
    return steps;
  }

  static String stepIdFor(Map<String, dynamic> step) =>
      base64.encode(utf8.encode(jsonEncode(step)));
}
