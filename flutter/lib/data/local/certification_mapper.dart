import 'dart:convert';

import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `CoursesRepositoryImpl.insertCertificationsFromSync`.
///
/// `courseIds` is stored verbatim as a JSON array string so the `LIKE` membership
/// query in [CertificationDao.countByCourseId] mirrors the Kotlin exactly.
/// Re-encoding rather than storing the raw fragment keeps it canonical, which
/// the substring match still satisfies.
class CertificationMapper {
  const CertificationMapper._();

  /// Parses one `_all_docs` row's `doc` into a certification companion.
  ///
  /// Returns `null` for `_design/*` docs, matching the Kotlin's skip.
  static CertificationsCompanion? fromDoc(Map<String, dynamic> doc) {
    final id = JsonUtils.getString('_id', doc);
    if (id.isEmpty || id.startsWith('_design/')) return null;

    final rawCourseIds = doc['courseIds'];
    final courseIds = rawCourseIds is List
        ? jsonEncode(rawCourseIds)
        : (rawCourseIds is String ? rawCourseIds : null);

    return CertificationsCompanion(
      id: Value(id),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      name: Value(JsonUtils.getStringOrNull('name', doc)),
      courseIds: Value(courseIds),
    );
  }
}
