import 'package:diacritic/diacritic.dart';
import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `MyLibrary.insertMyLibrary` (`model/MyLibrary.kt`).
///
/// Converts one CouchDB `resources` document into a row. The Kotlin version
/// mutates a Realm/Room entity in place and merges list fields with what is
/// already stored; here the merge inputs are passed in explicitly so the
/// function stays pure and testable.
class MyLibraryMapper {
  const MyLibraryMapper._();

  /// Mirrors the `titleNormal` computation: NFD-normalise, strip combining
  /// marks, lower-case. Used for accent-insensitive search.
  static String normalizeTitle(String title) =>
      removeDiacritics(title).toLowerCase();

  /// Returns `null` for an empty document or a `_design/*` doc, matching the
  /// filter in `ResourcesRepositoryImpl.batchInsertResources`.
  static MyLibraryTableCompanion? fromDoc(
    Map<String, dynamic> doc, {
    required String couchDbUrl,
    List<String> existingUserIds = const [],
    List<String> existingResourceFor = const [],
    List<String> existingSubject = const [],
    List<String> existingLevel = const [],
    List<String> existingTag = const [],
    List<String> existingLanguages = const [],
  }) {
    if (doc.isEmpty) return null;

    final resourceId = JsonUtils.getString('_id', doc);
    if (resourceId.isEmpty || resourceId.startsWith('_design')) return null;

    final title = JsonUtils.getString('title', doc);
    final attachment = _primaryAttachment(doc, resourceId, couchDbUrl);

    return MyLibraryTableCompanion(
      id: Value(resourceId),
      couchId: Value(resourceId),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      resourceId: Value(resourceId),
      title: Value(title),
      titleNormal: Value(normalizeTitle(title)),
      description: Value(JsonUtils.getStringOrNull('description', doc)),
      filename: Value(JsonUtils.getStringOrNull('filename', doc)),
      averageRating: Value(JsonUtils.getStringOrNull('averageRating', doc)),
      uploadDate: Value(JsonUtils.getStringOrNull('uploadDate', doc)),
      year: Value(JsonUtils.getStringOrNull('year', doc)),
      addedBy: Value(JsonUtils.getStringOrNull('addedBy', doc)),
      publisher: Value(JsonUtils.getStringOrNull('publisher', doc)),
      linkToLicense: Value(JsonUtils.getStringOrNull('linkToLicense', doc)),
      openWith: Value(JsonUtils.getStringOrNull('openWith', doc)),
      articleDate: Value(JsonUtils.getStringOrNull('articleDate', doc)),
      kind: Value(JsonUtils.getStringOrNull('kind', doc)),
      createdDate: Value(JsonUtils.getLong('createdDate', doc)),
      language: Value(JsonUtils.getStringOrNull('language', doc)),
      author: Value(JsonUtils.getStringOrNull('author', doc)),
      mediaType: Value(JsonUtils.getStringOrNull('mediaType', doc)),
      resourceType: Value(JsonUtils.getStringOrNull('resourceType', doc)),
      medium: Value(JsonUtils.getStringOrNull('medium', doc)),
      timesRated: Value(JsonUtils.getInt('timesRated', doc)),
      resourceRemoteAddress: Value(attachment?.remoteAddress),
      resourceLocalAddress: Value(attachment?.localAddress),
      userId: Value(existingUserIds),
      resourceFor: Value(
        _mergedList(
          existingResourceFor,
          JsonUtils.getStringList('resourceFor', doc),
        ),
      ),
      subject: Value(
        _mergedList(existingSubject, JsonUtils.getStringList('subject', doc)),
      ),
      level: Value(
        _mergedList(existingLevel, JsonUtils.getStringList('level', doc)),
      ),
      tag: Value(_mergedList(existingTag, JsonUtils.getStringList('tag', doc))),
      languages: Value(
        _mergedList(
          existingLanguages,
          JsonUtils.getStringList('languages', doc),
        ),
      ),
      isPrivate: Value(JsonUtils.getBool('private', doc)),
      privateFor: Value(JsonUtils.getStringOrNull('privateFor', doc)),
    );
  }

  /// Port of `MyLibrary.mergedList` — union of what is stored and what the
  /// server sent, preserving order and dropping duplicates.
  static List<String> _mergedList(
    List<String> existing,
    List<String> incoming,
  ) {
    final merged = <String>{...existing, ...incoming};
    return merged.toList(growable: false);
  }

  /// The Kotlin walks `_attachments` and treats the first key without a `/` as
  /// the resource's own file, deriving the download URL from it.
  static _Attachment? _primaryAttachment(
    Map<String, dynamic> doc,
    String resourceId,
    String couchDbUrl,
  ) {
    final attachments = JsonUtils.getObject('_attachments', doc);
    if (attachments == null) return null;

    for (final key in attachments.keys) {
      if (key.contains('/')) continue;
      final base = couchDbUrl.isEmpty ? 'http://' : couchDbUrl;
      return _Attachment(
        remoteAddress: '$base/resources/$resourceId/$key',
        localAddress: key,
      );
    }
    return null;
  }
}

class _Attachment {
  const _Attachment({required this.remoteAddress, required this.localAddress});

  final String remoteAddress;
  final String localAddress;
}
