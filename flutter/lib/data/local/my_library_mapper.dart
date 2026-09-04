import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import '../../core/utils/text_utils.dart' as text;
import 'app_database.dart';

/// Port of `MyLibrary.insertMyLibrary` (`model/MyLibrary.kt`).
///
/// Converts one CouchDB `resources` document into a row. The Kotlin version
/// mutates a Realm/Room entity in place and merges list fields with what is
/// already stored; here the merge inputs are passed in explicitly so the
/// function stays pure and testable.
class MyLibraryMapper {
  const MyLibraryMapper._();

  /// Mirrors the `titleNormal` computation. Used for accent-insensitive search.
  static String normalizeTitle(String title) => text.normalizeText(title);

  /// Port of `MyLibrary.setUserId` — shelf membership is a union, so pulling a
  /// second user's shelf does not evict the first, and a walk that knows
  /// nothing about membership (`shelfId == null`) leaves the stored list
  /// alone. Blank entries already persisted in the row are dropped on the way
  /// through: `setUserId` returns early on a blank id, so `[""]` is a value the
  /// Kotlin can never write, and it is the one value that fails the My Library
  /// predicate *and* passes the catalog one.
  ///
  /// The same shape as `CourseMapper.mergeUserIds`; kept separate rather than
  /// shared because the two mappers already carry their own copies of the
  /// Kotlin's per-model merge and neither imports the other.
  static List<String> mergeUserIds(List<String> existing, String? shelfId) {
    final kept = existing.where((id) => id.isNotEmpty);
    if (shelfId == null || shelfId.isEmpty) {
      return {...kept}.toList(growable: false);
    }
    return {...kept, shelfId}.toList(growable: false);
  }

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
    String? shelfId,
  }) {
    if (doc.isEmpty) return null;

    final resourceId = JsonUtils.getString('_id', doc);
    if (resourceId.isEmpty || resourceId.startsWith('_design')) return null;

    final title = JsonUtils.getString('title', doc);
    final attachment = _primaryAttachment(doc, resourceId, couchDbUrl);
    final isPrivate = JsonUtils.getBool('private', doc);

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
      // Kotlin uses `.takeIf { it.isNotBlank() }`: a whitespace-only value
      // reads as "no entry file set", so it must null out rather than fall
      // through to the `index.html` default as a non-blank-looking override.
      openWhichFile: Value(
        _takeIfNonBlank(JsonUtils.getStringOrNull('openWhichFile', doc)),
      ),
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
      userId: Value(mergeUserIds(existingUserIds, shelfId)),
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
      // The document key is `tags`, plural (`MyLibrary.kt:291`), even though
      // `serializeResource` writes the key back out as `tag`
      // (`MyLibrary.kt:100`) and the column keeps the singular name. Reading
      // the singular key matched the writer and never the server, so the
      // column was empty on every synced row — and `serializeResource` reads
      // it, so an achievement's attached resource document shipped an empty
      // tag list where the Kotlin ships the resource's tags.
      tag: Value(
        _mergedList(existingTag, JsonUtils.getStringList('tags', doc)),
      ),
      languages: Value(
        _mergedList(
          existingLanguages,
          JsonUtils.getStringList('languages', doc),
        ),
      ),
      isPrivate: Value(isPrivate),
      privateFor: _privateFor(doc, isPrivate),
    );
  }

  /// Port of `MyLibrary.kt:292-299`.
  ///
  /// The stored value is a **bare team id**, pulled out of the nested
  /// `privateFor.teams`; `MyLibrary.serialize` re-wraps it as
  /// `{"teams": <id>}` when it uploads a private resource
  /// (`MyLibrary.kt:174-178`), so the nesting lives in the document and never
  /// in the column. Reading the key as a string stored the Dart literal
  /// `{teams: team-1}` — the Phase 104 `SurveyMapper.choices` shape.
  ///
  /// Nothing in the port reads the column yet: Kotlin's reader is
  /// `ResourcesRepositoryImpl.markResourceUploaded`, which creates the
  /// team-resource link on upload, and the port has no resources uploader. So
  /// this is prophylactic — but a stringified map is not a value a later
  /// reader could recover a team id from, and it would be indistinguishable
  /// from a real one.
  ///
  /// Three of the Kotlin's four outcomes leave the stored value alone rather
  /// than clearing it, which is [Value.absent] here: a public document, a
  /// document with no `privateFor` at all, and one whose `privateFor` is not
  /// an object. The fourth — an object with no `teams` key — assigns the null
  /// that `get("teams")?.asString` produces, so it does write.
  static Value<String?> _privateFor(Map<String, dynamic> doc, bool isPrivate) {
    if (!isPrivate || !doc.containsKey('privateFor')) {
      return const Value<String?>.absent();
    }
    final privateFor = JsonUtils.getObject('privateFor', doc);
    if (privateFor == null) return const Value<String?>.absent();
    // Only a string. `JsonUtils.getString` falls through to `toString()`, so
    // reading `teams` with it would reproduce the very defect this function
    // exists to remove, one level down: an array `teams` would store the Dart
    // literal `[t1, t2]`. The Kotlin instead throws out of `asString`, and
    // `batchInsertResources`' per-document `try`/`catch` silently drops the
    // whole resource — neither app should do either, so an unusable `teams`
    // stores the null the Kotlin already writes for an object that has no
    // `teams` key at all.
    final teams = privateFor['teams'];
    return Value(teams is String && teams.isNotEmpty ? teams : null);
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
  ///
  /// **Deviation from the Kotlin**, twice over:
  /// * The userinfo is stripped before the URL is stored. `couchDbUrl` carries
  ///   `satellite:<pin>@`, and the Kotlin writes that straight into
  ///   `resourceRemoteAddress` — putting the PIN in a database row for every
  ///   resource, from where it reaches anything that reads, exports or logs the
  ///   row. Credentials are attached at request time instead.
  /// * An empty `couchDbUrl` yields `null` rather than the Kotlin's
  ///   `http:///resources/...`, which is not a usable URL.
  static _Attachment? _primaryAttachment(
    Map<String, dynamic> doc,
    String resourceId,
    String couchDbUrl,
  ) {
    final attachments = JsonUtils.getObject('_attachments', doc);
    if (attachments == null) return null;

    final base = credentialFreeBase(couchDbUrl);
    if (base == null) return null;

    for (final key in attachments.keys) {
      if (key.contains('/')) continue;
      return _Attachment(
        // Encoded to match UrlUtils.resourceUrl — both build the same download
        // path, and server-supplied filenames can contain spaces, '#' or '?'.
        remoteAddress:
            '$base/resources'
            '/${Uri.encodeComponent(resourceId)}/${Uri.encodeComponent(key)}',
        localAddress: key,
      );
    }
    return null;
  }

  /// Strips userinfo and any trailing slash from a CouchDB URL. Returns `null`
  /// when the input is empty or unparseable.
  static String? credentialFreeBase(String couchDbUrl) {
    if (couchDbUrl.isEmpty) return null;

    final parsed = Uri.tryParse(couchDbUrl);
    if (parsed == null || parsed.host.isEmpty) return null;

    return parsed
        .replace(userInfo: '')
        .toString()
        .replaceAll(RegExp(r'/+$'), '');
  }
}

class _Attachment {
  const _Attachment({required this.remoteAddress, required this.localAddress});

  final String remoteAddress;
  final String localAddress;
}

/// Kotlin's `String?.takeIf { it.isNotBlank() }` — nulls out a whitespace-only
/// string so it does not masquerade as a set value.
String? _takeIfNonBlank(String? value) {
  if (value == null || value.trim().isEmpty) return null;
  return value;
}
