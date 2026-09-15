import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `MyTeam.populateTeamFields` in `model/MyTeam.kt`.
class TeamMapper {
  const TeamMapper._();

  static TeamsCompanion? fromDoc(
    Map<String, dynamic> doc, {
    TeamRow? existing,
  }) {
    final id = JsonUtils.getString('_id', doc);
    if (id.isEmpty || id.startsWith('_design/')) return null;
    if (existing != null && existing.isUpdated) {
      // A row the user edited offline outranks the server copy until something
      // uploads it — overwriting here would silently discard the edit, and
      // there is no second copy of it anywhere. The revision is still adopted
      // so the eventual upload does not conflict on a stale `_rev`.
      return existing
          .toCompanion(false)
          .copyWith(rev: Value(JsonUtils.getStringOrNull('_rev', doc)));
    }
    return TeamsCompanion(
      id: Value(id),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      teamId: Value(JsonUtils.getStringOrNull('teamId', doc)),
      userId: Value(JsonUtils.getStringOrNull('userId', doc)),
      name: Value(JsonUtils.getStringOrNull('name', doc)),
      description: Value(JsonUtils.getStringOrNull('description', doc)),
      resourceId: Value(JsonUtils.getStringOrNull('resourceId', doc)),
      title: Value(JsonUtils.getStringOrNull('title', doc)),
      type: Value(JsonUtils.getStringOrNull('type', doc)),
      docType: Value(JsonUtils.getStringOrNull('docType', doc)),
      teamType: Value(JsonUtils.getStringOrNull('teamType', doc)),
      status: Value(JsonUtils.getStringOrNull('status', doc)),
      services: Value(JsonUtils.getStringOrNull('services', doc)),
      rules: Value(JsonUtils.getStringOrNull('rules', doc)),
      createdBy: Value(JsonUtils.getStringOrNull('createdBy', doc)),
      route: Value(JsonUtils.getStringOrNull('route', doc)),
      courses: Value(_courseIds(doc['courses'])),
      createdDate: Value(JsonUtils.getLong('createdDate', doc)),
      limit: Value(JsonUtils.getInt('limit', doc)),
      isPublic: Value(JsonUtils.getBool('public', doc)),
      isLeader: Value(JsonUtils.getBool('isLeader', doc)),
      beginningBalance: Value(JsonUtils.getInt('beginningBalance', doc)),
      sales: Value(JsonUtils.getInt('sales', doc)),
      otherIncome: Value(JsonUtils.getInt('otherIncome', doc)),
      wages: Value(JsonUtils.getInt('wages', doc)),
      otherExpenses: Value(JsonUtils.getInt('otherExpenses', doc)),
      startDate: Value(JsonUtils.getLong('startDate', doc)),
      endDate: Value(JsonUtils.getLong('endDate', doc)),
      updatedDate: Value(JsonUtils.getLong('updatedDate', doc)),
      date: Value(JsonUtils.getLong('date', doc)),
      amount: Value(JsonUtils.getInt('amount', doc)),
      imageName: Value(firstAttachmentName(doc['_attachments'])),
    );
  }

  /// Port of `MyTeam.getFirstAttachmentName`: a team document's binary
  /// attachment (a receipt image) is stored under CouchDB `_attachments`, whose
  /// keys are the attachment names. There is at most one per finance document,
  /// so the first key is the name the preview and the upload read-back use.
  static String? firstAttachmentName(Object? attachments) {
    if (attachments is! Map) return null;
    final keys = attachments.keys;
    for (final key in keys) {
      if (key is String && key.isNotEmpty) return key;
    }
    return keys.isEmpty ? null : keys.first.toString();
  }

  static List<String> _courseIds(Object? value) {
    if (value is! List) return const [];
    return value
        .map(
          (course) => switch (course) {
            String id => id,
            Map<String, dynamic> map => JsonUtils.getString('_id', map),
            _ => '',
          },
        )
        .where((id) => id.isNotEmpty)
        .toSet()
        .toList(growable: false);
  }
}

/// Port of `TeamsRepositoryImpl.teamLogFromJson` (`:1150`) — one row of the
/// `team_activities` CouchDB database, which the port pulls for the first time
/// in this phase.
///
/// **This is not a literal port of the Kotlin function, and the divergence is
/// the point.** `teamLogFromJson` builds a bare `TeamLog` keyed by the server
/// `_id`, so a visit this device authored — keyed by its own generated id, with
/// `_id`/`_rev` stamped on by `markUploaded` — comes back as a *second* row and
/// `getTeamVisitsForUsers` counts the same visit twice. The merge here is the
/// shape Kotlin uses for the sibling table one file over
/// (`ActivitiesRepositoryImpl.activityFromJson:241`, reached from
/// `bulkInsertOfflineActivitiesFromSync`): resolve the local row first, keep
/// its primary key, and overwrite only the server-owned fields. Kotlin's
/// `TeamLogDao.getByRemoteIds` (`TeamLogDao.kt:25`) is the lookup that merge
/// needs and it has **no caller anywhere in `app/src/main`** — the dedup was
/// laid in and never wired, the same shape this port keeps finding.
///
/// [existing] is the row already carrying this `_id`; [fallback] is the row
/// matched on `(time, user, teamId)`, which catches the window where a POST
/// landed but `markUploaded` never ran — the device then holds an unstamped
/// row for a visit the server already has.
class TeamLogMapper {
  const TeamLogMapper._();

  /// The natural key a visit is identified by when no `_id` links the rows.
  ///
  /// Kotlin's analogue is `"${loginTime}_${userName}"`; `teamId` joins it here
  /// because one user legitimately visits several teams, and two visits by the
  /// same user at the same millisecond to *different* teams are distinct rows.
  static String naturalKey({int? time, String? user, String? teamId}) =>
      '${time ?? 0}_${user ?? ''}_${teamId ?? ''}';

  static String naturalKeyForDoc(Map<String, dynamic> doc) => naturalKey(
    time: JsonUtils.getLong('time', doc),
    user: JsonUtils.getStringOrNull('user', doc),
    teamId: JsonUtils.getStringOrNull('teamId', doc),
  );

  static String naturalKeyForRow(TeamLogRow row) =>
      naturalKey(time: row.time, user: row.user, teamId: row.teamId);

  /// Builds the companion for one synced document, or null when the document
  /// carries no usable `_id`.
  static TeamLogTableCompanion? fromDoc(
    Map<String, dynamic> doc, {
    TeamLogRow? existing,
    TeamLogRow? fallback,
  }) {
    final docId = JsonUtils.getString('_id', doc);
    if (docId.isEmpty || docId.startsWith('_design')) return null;
    final base = existing ?? fallback;

    return TeamLogTableCompanion(
      // Keeping the local row's primary key is what makes this a merge rather
      // than an insert; only a document with no local counterpart is keyed by
      // its `_id`.
      id: Value(base?.id ?? docId),
      couchId: Value(docId),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      teamId: Value(JsonUtils.getStringOrNull('teamId', doc)),
      user: Value(JsonUtils.getStringOrNull('user', doc)),
      type: Value(JsonUtils.getStringOrNull('type', doc)),
      teamType: Value(JsonUtils.getStringOrNull('teamType', doc)),
      createdOn: Value(JsonUtils.getStringOrNull('createdOn', doc)),
      parentCode: Value(JsonUtils.getStringOrNull('parentCode', doc)),
      time: Value(JsonUtils.getLong('time', doc)),
      // **The one column that is not copied from the document, and the reason
      // a literal port of `teamLogFromJson` would have been a data-multiplying
      // bug rather than a missing feature.**
      //
      // Kotlin's pending-upload predicate is `TeamLogDao.getPendingUploads` =
      // `WHERE _rev IS NULL`, so writing the document's `_rev` onto the row is
      // itself what takes it out of the upload queue; `TeamLog.uploaded` is
      // never read by a query and never set to true anywhere in
      // `app/src/main`. This port expresses the same predicate as
      // `uploaded = false` (`TeamLogDao.pendingUploads`), so `uploaded = true`
      // is the *faithful* translation of Kotlin writing `_rev` — not a
      // divergence from it.
      //
      // Left at the column default instead, every one of the 13,659
      // `team_activities` documents on planet.learning would have arrived
      // flagged as needing upload and been POSTed straight back, duplicating
      // the whole database on the server on every sync. A document that came
      // *from* `team_activities` has by definition reached `team_activities`.
      uploaded: const Value(true),
    );
  }
}
