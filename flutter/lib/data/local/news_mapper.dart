import 'dart:convert';

import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `VoicesRepositoryImpl.buildNewsFromJson`.
///
/// The local row id is the CouchDB `_id` for anything the server sent, so a
/// re-sync updates a post in place rather than duplicating it. Locally composed
/// posts get a generated id instead and adopt the server's on upload.
class NewsMapper {
  const NewsMapper._();

  static NewsEntriesCompanion? fromDoc(
    Map<String, dynamic> doc, {
    NewsRow? existing,
  }) {
    final docId = JsonUtils.getString('_id', doc);
    if (docId.isEmpty || docId.startsWith('_design/')) return null;

    final user = JsonUtils.getObject('user', doc);
    final nested = JsonUtils.getObject('news', doc);

    return NewsEntriesCompanion.insert(
      // `buildNewsFromJson` seeds `id = underscoreId` for a post it has not
      // seen before, and otherwise keeps the existing row's id — which for a
      // locally composed post is the generated one, so an upload followed by a
      // sync does not fork the row.
      id: existing?.id ?? docId,
      docId: Value(docId),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      viewableBy: Value(JsonUtils.getStringOrNull('viewableBy', doc)),
      viewableId: Value(JsonUtils.getStringOrNull('viewableId', doc)),
      docType: Value(JsonUtils.getStringOrNull('docType', doc)),
      avatar: Value(JsonUtils.getStringOrNull('avatar', doc)),
      updatedDate: Value(JsonUtils.getLong('updatedDate', doc)),
      createdOn: Value(JsonUtils.getStringOrNull('createdOn', doc)),
      messageType: Value(JsonUtils.getStringOrNull('messageType', doc)),
      messagePlanetCode: Value(
        JsonUtils.getStringOrNull('messagePlanetCode', doc),
      ),
      replyTo: Value(JsonUtils.getStringOrNull('replyTo', doc)),
      parentCode: Value(JsonUtils.getStringOrNull('parentCode', doc)),
      user: Value(user == null ? null : jsonEncode(user)),
      userId: Value(
        user == null ? null : JsonUtils.getStringOrNull('_id', user),
      ),
      userName: Value(
        user == null ? null : JsonUtils.getStringOrNull('name', user),
      ),
      time: Value(JsonUtils.getLong('time', doc)),
      message: Value(JsonUtils.getStringOrNull('message', doc)),
      images: Value(jsonEncode(_listOf('images', doc))),
      labels: Value(
        _listOf('labels', doc).map((e) => e.toString()).toList(growable: false),
      ),
      viewIn: Value(jsonEncode(_listOf('viewIn', doc))),
      chat: Value(JsonUtils.getBool('chat', doc)),
      newsId: Value(
        nested == null ? null : JsonUtils.getStringOrNull('_id', nested),
      ),
      newsRev: Value(
        nested == null ? null : JsonUtils.getStringOrNull('_rev', nested),
      ),
      newsUser: Value(
        nested == null ? null : JsonUtils.getStringOrNull('user', nested),
      ),
      aiProvider: Value(
        nested == null ? null : JsonUtils.getStringOrNull('aiProvider', nested),
      ),
      newsTitle: Value(
        nested == null ? null : JsonUtils.getStringOrNull('title', nested),
      ),
      conversations: Value(
        jsonEncode(
          nested == null ? const [] : _listOf('conversations', nested),
        ),
      ),
      newsCreatedDate: Value(
        nested == null ? 0 : JsonUtils.getLong('createdDate', nested),
      ),
      newsUpdatedDate: Value(
        nested == null ? 0 : JsonUtils.getLong('updatedDate', nested),
      ),
      // Read from the *nested* object, matching `buildNewsFromJson`, even
      // though `News.createNews` writes it at the top level. The asymmetry is
      // upstream's; reproducing it keeps a synced post identical to Kotlin's.
      sharedBy: Value(
        nested == null ? null : JsonUtils.getStringOrNull('sharedBy', nested),
      ),
      // Read from the *nested* object, because that is where `serializeNews`
      // writes it. Reading the top level here — as this did — meant a reaction
      // uploaded from one device was never seen by another, and worse, the next
      // sync-in of that same document wrote `Value(null)` straight over the
      // local column, so a user's own reaction vanished on their next sync.
      reactions: Value(
        nested == null ? null : JsonUtils.getStringOrNull('reactions', nested),
      ),
      // Local-only fields: an in-flight attachment list and the edit marker
      // belong to this device and are never sent back by the server.
      imageUrls: Value(existing?.imageUrls ?? const []),
      isEdited: Value(existing?.isEdited ?? false),
      editedTime: Value(existing?.editedTime ?? 0),
    );
  }

  static List<dynamic> _listOf(String key, Map<String, dynamic> doc) {
    final value = doc[key];
    return value is List ? value : const [];
  }
}
