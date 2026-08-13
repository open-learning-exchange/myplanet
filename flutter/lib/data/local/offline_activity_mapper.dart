import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `ActivitiesRepositoryImpl.activityFromJson` /
/// `insertLoginActivitiesFromSync`.
///
/// A synced `login_activities` document corresponds to a row that may already
/// exist locally — either an earlier-synced cache row (matched by `_id`) or an
/// offline-authored login record with no `_id` yet (matched by the
/// `(loginTime, userName)` pair the document also carries). The merge mirrors
/// the Kotlin so a re-sync adopts the local row rather than creating a twin,
/// which is what keeps the chart's counts from doubling after each pull.
class OfflineActivityMapper {
  const OfflineActivityMapper._();

  /// Builds the companion for one synced document.
  ///
  /// [existing] is the row already keyed by the server `_id`, if any.
  /// [fallback] is the row keyed by `(loginTime, userName)` whose own `_id`
  /// is null or matches [docId], used when [existing] is null.
  static OfflineActivitiesCompanion fromDoc(
    Map<String, dynamic> doc, {
    OfflineActivityRow? existing,
    OfflineActivityRow? fallback,
  }) {
    final docId = JsonUtils.getString('_id', doc);
    final base = existing ?? fallback;

    return OfflineActivitiesCompanion(
      id: Value(base?.id ?? docId),
      couchId: Value(docId),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      type: Value(JsonUtils.getStringOrNull('type', doc)),
      userName: Value(JsonUtils.getStringOrNull('user', doc)),
      parentCode: Value(JsonUtils.getStringOrNull('parentCode', doc)),
      createdOn: Value(JsonUtils.getStringOrNull('createdOn', doc)),
      loginTime: Value(JsonUtils.getLong('loginTime', doc)),
      logoutTime: Value(JsonUtils.getLong('logoutTime', doc)),
      androidId: Value(JsonUtils.getStringOrNull('androidId', doc)),
      // `description` and `userId` are not on the `login_activities` document;
      // preserve any local value the row already carries.
      description: Value(base?.description),
      userId: Value(base?.userId),
    );
  }
}
