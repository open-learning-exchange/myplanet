import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `ProgressRepositoryImpl.courseProgressFromJson` /
/// `insertCourseProgressFromSync`.
///
/// A synced `courses_progress` document corresponds to a row that may already
/// exist locally — either because the user authored it offline (a step viewed
/// or an exam passed with no connectivity) or because an earlier sync pulled
/// it. The merge mirrors the Kotlin:
///
/// - match by the server `_id` first (`existingProgress`),
/// - else by the local `(courseId, userId, stepNum)` triple
///   (`localRecord`), preferring a row whose own `_id` is null or already
///   matches the incoming one,
/// - else create a new row keyed by the server id.
///
/// The local `passed` flag is preserved: a sync that arrives *after* the user
/// passed a step offline must not overwrite that with a stale `false`. The
/// Kotlin keeps `passed` only when it is already `true`; the port does the same
/// — a server `true` is still adopted when the local value is `false`, since
/// the row may have been created locally with `passed = false` by a step view
/// that the exam later graded `true` on the server.
class CourseProgressMapper {
  const CourseProgressMapper._();

  /// Builds the companion for one synced document.
  ///
  /// [existing] is the row already keyed by the server `_id`, if any.
  /// [localRecord] is the row keyed by the `(courseId, userId, stepNum)`
  /// triple whose own `_id` is null or matches [docId], used when [existing]
  /// is null so a re-sync adopts the local row rather than creating a twin.
  static CourseProgressCompanion fromDoc(
    Map<String, dynamic> doc, {
    CourseProgressRow? existing,
    CourseProgressRow? localRecord,
  }) {
    final docId = JsonUtils.getString('_id', doc);
    final base = existing ?? localRecord;

    final alreadyPassed = base?.passed == true;
    final serverPassed = JsonUtils.getBool('passed', doc);

    return CourseProgressCompanion(
      id: Value(base?.id ?? docId),
      couchId: Value(docId),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      // Keep a local `true`; only adopt a server `true` when the local is not
      // already `true` — exactly the Kotlin's `if (passed != true)` guard.
      passed: Value(alreadyPassed || (!alreadyPassed && serverPassed)),
      stepNum: Value(JsonUtils.getInt('stepNum', doc)),
      userId: Value(JsonUtils.getStringOrNull('userId', doc)),
      courseId: Value(JsonUtils.getStringOrNull('courseId', doc)),
      parentCode: Value(JsonUtils.getStringOrNull('parentCode', doc)),
      createdOn: Value(JsonUtils.getStringOrNull('createdOn', doc)),
      createdDate: Value(JsonUtils.getLong('createdDate', doc)),
      updatedDate: Value(JsonUtils.getLong('updatedDate', doc)),
    );
  }
}
