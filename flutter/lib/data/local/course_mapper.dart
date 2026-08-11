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
  /// union, so syncing a second user's shelf does not evict the first. Blank
  /// entries already persisted in the row are dropped on the way through,
  /// matching the Kotlin's blank-filtering merge.
  static List<String> mergeUserIds(List<String> existing, String? shelfId) {
    final kept = existing.where((id) => id.isNotEmpty);
    if (shelfId == null || shelfId.isEmpty) {
      return {...kept}.toList(growable: false);
    }
    return {...kept, shelfId}.toList(growable: false);
  }

  /// Port of the `steps` loop in `parseCourseDocument`.
  ///
  /// **Deviation from the Kotlin.** Embedded steps carry no `_id`, and the
  /// Kotlin derives one as `Base64(stepJson.toString())` — the step's content.
  /// That is unsafe as a primary key, in two ways the Kotlin also suffers:
  ///
  /// * Two steps with identical content collide. Within one course the upsert
  ///   silently drops the second; across courses the surviving row keeps
  ///   whichever `courseId` was written last, so the step vanishes from the
  ///   other course.
  /// * Base64 is an encoding, not a digest, so a step with a long description
  ///   stores a multi-kilobyte primary key in the row and in the index.
  ///
  /// The id is derived from the course and the step's position instead:
  /// bounded, unique, and stable across syncs. This is safe to diverge on
  /// because the id is local — the Kotlin's is locally derived too, never a
  /// server value.
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
          id: Value(stepIdFor(courseId, i)),
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

  /// Local step id: `<courseId>:<position>`.
  static String stepIdFor(String courseId, int stepIndex) =>
      '$courseId:$stepIndex';
}
