import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import '../../core/utils/text_utils.dart';
import 'app_database.dart';

/// A course document parsed into the rows it produces.
///
/// Port of `CoursesRepositoryImpl.ParsedCourseSyncPayload`, minus the exam and
/// question halves — those arrive with the `ui/exam` package.
class ParsedCourse {
  const ParsedCourse({
    required this.course,
    required this.steps,
    this.resources = const [],
  });

  final CoursesCompanion course;
  final List<CourseStepsCompanion> steps;

  /// The resource documents embedded in the steps, in document order.
  final List<CourseResourceDoc> resources;
}

/// One resource document embedded in a course step, with the join it belongs
/// to. Port of `CoursesRepositoryImpl.PendingCourseResource`.
///
/// Kotlin buffers these on the repository and drains the buffer separately
/// (`queueCourseResources` → `flushPendingCourseResources`), because its parse
/// and its write are in different methods. The port parses and writes in one
/// page loop, so the buffer collapses into a return value — which also removes
/// the two ways Kotlin's buffer misbehaves: a throw mid-page leaves items
/// queued under a `courseId` whose row was discarded, and the shelf path runs
/// up to six coroutines against the one shared list.
class CourseResourceDoc {
  const CourseResourceDoc({
    required this.doc,
    required this.courseId,
    required this.stepId,
  });

  final Map<String, dynamic> doc;
  final String courseId;
  final String stepId;

  /// The resource's CouchDB id — the `my_library` primary key it lands under.
  String get resourceId => JsonUtils.getString('_id', doc);
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
    final resources = <CourseResourceDoc>[];
    final steps = _parseSteps(doc, courseId, resources);

    return ParsedCourse(
      resources: resources,
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
    List<CourseResourceDoc> collectedResources,
  ) {
    final rawSteps = doc['steps'];
    if (rawSteps is! List) return const [];

    final steps = <CourseStepsCompanion>[];
    for (var i = 0; i < rawSteps.length; i++) {
      final step = rawSteps[i];
      if (step is! Map<String, dynamic>) continue;

      final resources = step['resources'];
      collectedResources.addAll(
        _parseStepResources(resources, courseId, stepIdFor(courseId, i)),
      );
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

  /// Port of `queueCourseResources` (`CoursesRepositoryImpl.kt:792-798`).
  ///
  /// **Three deviations, all of them refusals to reproduce a latent
  /// corruption**, because Kotlin filters nothing here — unlike
  /// `batchInsertResources`, which filters both blank ids and `_design` docs
  /// (`ResourcesRepositoryImpl.kt:668-671`) before writing the same table:
  ///
  /// * A non-object element is skipped. Kotlin calls `.asJsonObject` on it,
  ///   which throws `IllegalStateException`, and the enclosing catch
  ///   (`CoursesRepositoryImpl.kt:637-646`) only swallows it when
  ///   `continueOnError` is true — which the shelf path passes and **the sync
  ///   walk does not** (`:619` defaults to false). So one malformed element
  ///   aborts the whole `courses` table walk at that page, writing nothing and
  ///   skipping the drain.
  /// * A blank or whitespace-only `_id` is skipped. `insertMyLibrary` bails out
  ///   only on an *empty* document (`MyLibrary.kt:220`), so one with fields but
  ///   no `_id` still yields a row and Kotlin writes `id = ""` — and
  ///   because the primary key collides, every `_id`-less resource in a batch
  ///   collapses into one row under `REPLACE`, leaving a phantom carrying the
  ///   last one's title and the last one's step.
  /// * A `_design` id is skipped, as the `resources` walk already does.
  static List<CourseResourceDoc> _parseStepResources(
    Object? resources,
    String courseId,
    String stepId,
  ) {
    if (resources is! List) return const [];

    final parsed = <CourseResourceDoc>[];
    for (final resource in resources) {
      if (resource is! Map<String, dynamic>) continue;
      final resourceId = JsonUtils.getString('_id', resource);
      if (resourceId.trim().isEmpty || resourceId.startsWith('_design')) {
        continue;
      }
      parsed.add(
        CourseResourceDoc(doc: resource, courseId: courseId, stepId: stepId),
      );
    }
    return parsed;
  }

  /// Local step id: `<courseId>:<position>`.
  static String stepIdFor(String courseId, int stepIndex) =>
      '$courseId:$stepIndex';
}
