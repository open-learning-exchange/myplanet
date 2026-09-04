import 'dart:convert';

import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `Meetup.fromJson` in `model/Meetup.kt`.
class MeetupMapper {
  const MeetupMapper._();

  static MeetupsCompanion? fromDoc(
    Map<String, dynamic> doc, {
    MeetupRow? existing,
    String? userId,
  }) {
    final id = JsonUtils.getString('_id', doc);
    if (id.isEmpty || id.startsWith('_design/')) return null;
    final link = JsonUtils.getObject('link', doc);
    return MeetupsCompanion(
      id: Value(id),
      // Attendance is local: the `meetups` document says nothing about whether
      // *this* user joined. Overwriting it from a server refresh un-joins the
      // user from every meetup, and `meetupsOnShelf` then drops them from the
      // shelf push. An existing `''` is preserved as-is — it means "left",
      // which is distinct from "never joined".
      userId: Value(userId ?? existing?.userId),
      meetupId: Value(id),
      meetupIdRev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      title: Value(JsonUtils.getStringOrNull('title', doc)),
      description: Value(JsonUtils.getStringOrNull('description', doc)),
      startDate: Value(JsonUtils.getLong('startDate', doc)),
      endDate: Value(JsonUtils.getLong('endDate', doc)),
      recurring: Value(JsonUtils.getStringOrNull('recurring', doc)),
      day: Value(_encoded(doc['day'])),
      startTime: Value(JsonUtils.getStringOrNull('startTime', doc)),
      endTime: Value(JsonUtils.getStringOrNull('endTime', doc)),
      category: Value(JsonUtils.getStringOrNull('category', doc)),
      meetupLocation: Value(JsonUtils.getStringOrNull('meetupLocation', doc)),
      meetupLink: Value(JsonUtils.getStringOrNull('meetupLink', doc)),
      creator: Value(JsonUtils.getStringOrNull('createdBy', doc)),
      link: Value(link == null ? null : jsonEncode(link)),
      teamId: Value(
        link == null ? null : JsonUtils.getStringOrNull('teams', link),
      ),
      createdDate: Value(existing?.createdDate ?? 0),
      recurringNumber: Value(existing?.recurringNumber ?? 10),
      sync: Value(existing?.sync),
      sourcePlanet: Value(existing?.sourcePlanet),
      updated: Value(existing?.updated ?? false),
    );
  }

  static String? _encoded(Object? value) =>
      value == null ? null : jsonEncode(value);
}
