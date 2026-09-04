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
