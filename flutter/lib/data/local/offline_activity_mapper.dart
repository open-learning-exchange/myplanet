import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `ActivitiesRepositoryImpl.activityFromJson` /
/// `insertLoginActivitiesFromSync`.
///
/// Harvested from the `flutter-openhands4` branch, which built the sync-in
/// direction this port lacked: Phase 33 wrote login rows and Phase 34 uploaded
/// them, but nothing pulled the ones other devices had already sent. Without
/// this a member's history is whatever *this* handset happened to observe.
///
/// A synced `login_activities` document corresponds to a row that may already
/// exist locally — either an earlier-synced cache row (matched by `_id`) or an
/// offline-authored login record with no `_id` yet (matched by the
/// `(loginTime, userName)` pair the document also carries). The merge mirrors
/// the Kotlin so a re-sync adopts the local row rather than creating a twin,
/// which is what keeps the dashboard's login count and the activity chart from
/// doubling after each pull.
class OfflineActivityMapper {
  const OfflineActivityMapper._();

  /// Builds the companion for one synced document.
  ///
  /// [existing] is the row already keyed by the server `_id`, if any.
  /// [fallback] is the row keyed by `(loginTime, userName)`, used when
  /// [existing] is null — that is the case where this device authored the login
  /// offline, uploaded it, and is now seeing its own document come back.
  static OfflineActivitiesCompanion fromDoc(
    Map<String, dynamic> doc, {
    OfflineActivityRow? existing,
    OfflineActivityRow? fallback,
  }) {
    final docId = JsonUtils.getString('_id', doc);
    final base = existing ?? fallback;

    return OfflineActivitiesCompanion(
      // Keeping the local row's primary key is what makes this a merge rather
      // than an insert; only a document with no local counterpart is keyed by
      // its `_id`.
      id: Value(base?.id ?? docId),
      couchId: Value(docId),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      type: Value(JsonUtils.getStringOrNull('type', doc)),
      // The document's key is `user` and it holds the user *name* —
      // `serializeLoginActivities` writes `activity.userName` there.
      userName: Value(JsonUtils.getStringOrNull('user', doc)),
      parentCode: Value(JsonUtils.getStringOrNull('parentCode', doc)),
      createdOn: Value(JsonUtils.getStringOrNull('createdOn', doc)),
      loginTime: Value(JsonUtils.getLong('loginTime', doc)),
      logoutTime: Value(JsonUtils.getLong('logoutTime', doc)),
      androidId: Value(JsonUtils.getStringOrNull('androidId', doc)),
      // `description` and `userId` are not on the `login_activities` document,
      // so a partial companion would blank them on an existing row —
      // `insertOnConflictUpdate` writes only the columns it carries, but these
      // are carried, so they must be carried with the *local* values. `userId`
      // in particular is what `offlineVisitCount` keys on for the profile.
      description: Value(base?.description),
      userId: Value(base?.userId),
    );
  }
}
