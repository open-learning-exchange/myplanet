import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';

import '../core/config/server_config.dart';
import '../core/files/voice_images.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/news_mapper.dart';
import '../data/local/user_mapper.dart';

/// An image the user picked for a post, before it has been written to disk.
class VoiceImageAttachment {
  const VoiceImageAttachment({required this.bytes, required this.filename});

  final List<int> bytes;
  final String filename;
}

/// One entry of the `imageUrls` column — a post's image that has not reached
/// CouchDB yet.
///
/// The JSON shape is Kotlin's, authored at pick time in
/// `BaseVoicesFragment.kt:153-156`: exactly `imageUrl` and `fileName`, where
/// `imageUrl` is an absolute local path and `fileName` its basename.
///
/// The port keeps the shape but **keys on [fileName], not [imageUrl]**.
/// Kotlin reopens the recorded absolute path at upload time
/// (`UploadManager.kt:322`), which a durable outbox cannot rely on — the
/// drain may happen after a process death or an app update, and on iOS the
/// documents directory path changes between launches. [VoiceImages] owns the
/// bytes instead, under `<newsId>/<fileName>`, and [imageUrl] is recorded for
/// shape faithfulness and for `editPost`'s removal predicate, which matches on
/// it (`VoicesRepositoryImpl.editPost`).
class PendingVoiceImage {
  const PendingVoiceImage({required this.imageUrl, required this.fileName});

  final String imageUrl;
  final String fileName;

  /// Null for an entry that is not an object or carries no usable name — the
  /// uploader skips those rather than PUTting to a URL with an empty last
  /// segment, which is what Kotlin does when `getFileNameFromUrl` throws
  /// (`FileUtils.kt:109-112` returns `""`).
  static PendingVoiceImage? decode(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) return null;
      final fileName = JsonUtils.getString('fileName', decoded);
      if (fileName.isEmpty) return null;
      return PendingVoiceImage(
        imageUrl: JsonUtils.getString('imageUrl', decoded),
        fileName: fileName,
      );
    } catch (_) {
      return null;
    }
  }

  String encode() => jsonEncode({'imageUrl': imageUrl, 'fileName': fileName});
}

/// Port of `repository/VoicesRepositoryImpl.kt` — the voices/discussion feed.
class VoicesRepository {
  VoicesRepository(
    this._api,
    this._dao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId = createId ?? _defaultId;

  final PlanetApi _api;
  final NewsDao _dao;
  final DateTime Function() _now;
  final String Function() _createId;

  static String _defaultId() => 'news-${DateTime.now().microsecondsSinceEpoch}';

  Future<NewsRow?> getById(String id) => _dao.getById(id);

  /// Port of `VoicesRepositoryImpl.getCommunityVoiceDates`. Returns the
  /// distinct `yyyy-MM-dd` dates of top-level community-section posts in a
  /// time window — one per day the user (or, when `userId` is null, the whole
  /// community) posted, used by the challenge dialog's voice-count check.
  ///
  /// [isCommunityNews] stands in for the Kotlin's community predicate, which
  /// is **in SQL**, not in memory: `countDistinctCommunityVoiceDates` filters
  /// `viewIn LIKE '%"section":"community"%'` (`NewsDao.kt:61-72`). An
  /// earlier version of this comment said the Kotlin filtered in memory after
  /// the DAO query; it does not, and the same wrong claim is still on
  /// `NewsDao.getCommunityVoiceDates` in `app_database.dart` (another lane's
  /// file this round — reported, not fixed).
  Future<List<String>> getCommunityVoiceDates(
    int startTime,
    int endTime,
    String? userId,
  ) async {
    final rows = await (userId != null
        ? _dao.getInTimeRangeForUser(startTime, endTime, userId)
        : _dao.getInTimeRange(startTime, endTime));
    final dates = <String>{};
    for (final row in rows) {
      if (isCommunityNews(row)) {
        dates.add(_formatDate(row.time));
      }
    }
    return dates.toList();
  }

  /// The device's UTC offset at [instant].
  ///
  /// Overridable because CI runs in UTC, where a local-day and a UTC-day
  /// bucketing are indistinguishable and no test could fail on the defect
  /// this seam exists to pin. The default reads the offset *at that instant*
  /// rather than a fixed one, so it follows DST the way SQLite's `'localtime'`
  /// modifier does.
  @visibleForTesting
  static Duration Function(DateTime instant) deviceUtcOffset =
      _realDeviceUtcOffset;

  @visibleForTesting
  static void resetDeviceUtcOffset() => deviceUtcOffset = _realDeviceUtcOffset;

  static Duration _realDeviceUtcOffset(DateTime instant) =>
      instant.toLocal().timeZoneOffset;

  /// The `yyyy-MM-dd` bucket a post at [millis] falls in, on the **device's**
  /// day.
  ///
  /// Kotlin buckets in SQL —
  /// `strftime('%Y-%m-%d', time / 1000, 'unixepoch', 'localtime')`
  /// (`NewsDao.kt:61,68`) — and `'localtime'` is the device's zone, so a post
  /// made after local midnight belongs to the new day even when the UTC
  /// instant is still the old one. Formatting the UTC instant instead put two
  /// posts either side of local midnight in one bucket, under-counting the
  /// challenge's distinct-days tally by a day.
  ///
  /// The offset is added to a UTC `DateTime` rather than taken from
  /// `DateTime.fromMillisecondsSinceEpoch(millis)` directly so [deviceUtcOffset]
  /// can stand in for a device CI does not run on; the two are the same
  /// calendar date for every real offset.
  static String _formatDate(int millis) {
    final instant = DateTime.fromMillisecondsSinceEpoch(millis, isUtc: true);
    final dt = instant.add(deviceUtcOffset(instant));
    return '${dt.year.toString().padLeft(4, '0')}'
        '-${dt.month.toString().padLeft(2, '0')}'
        '-${dt.day.toString().padLeft(2, '0')}';
  }

  Stream<List<NewsRow>> watchReplies(String newsId) =>
      _dao.watchReplies(newsId);

  Future<int> replyCount(String? newsId) =>
      newsId == null ? Future.value(0) : _dao.replyCount(newsId);

  /// Port of `getCommunityNews`: the top-level `message` posts this user is
  /// allowed to see, sorted by [sortDateOf] descending — a post shared into
  /// the community surfaces by when it was shared, not when it was written.
  Stream<List<NewsRow>> watchCommunityFeed(String userIdentifier) {
    return _dao.watchTopLevelMessages().map((rows) {
      final visible = rows
          .where((row) => isVisibleToUser(row, userIdentifier))
          .toList();
      visible.sort((a, b) => sortDateOf(b).compareTo(sortDateOf(a)));
      return visible;
    });
  }

  /// Port of `isVisibleToUser`.
  ///
  /// A post is visible when it is addressed to the community at large, or when
  /// a *community* entry in its `viewIn` array names the viewer. Note the
  /// fall-through: a post with no `viewIn` and a `viewableBy` other than
  /// "community" is visible to *nobody*, which is how team-only posts stay out
  /// of the community feed — and since upstream f4adebf, only entries with
  /// `section == "community"` count at all, so a team entry naming the viewer
  /// no longer leaks the post into the community feed. An empty or `"@"` id on
  /// a community entry (or an empty/`"@"` viewer) is a wildcard for "everyone",
  /// the Planet convention for a planet-wide share.
  ///
  /// Malformed `viewIn` JSON is treated as "not visible" rather than allowed to
  /// throw — matching the Kotlin's `catch (throwable: Throwable) { false }`,
  /// which fails closed.
  static bool isVisibleToUser(NewsRow row, String userIdentifier) {
    if (row.viewableBy?.toLowerCase() == 'community') return true;

    final viewIn = row.viewIn;
    if (viewIn == null || viewIn.isEmpty) return false;

    try {
      final decoded = jsonDecode(viewIn);
      if (decoded is! List) return false;
      return decoded.any((element) {
        if (element is! Map<String, dynamic>) return false;
        if (JsonUtils.getString('section', element).toLowerCase() !=
            'community') {
          return false;
        }
        final id = JsonUtils.getString('_id', element);
        return id.isEmpty ||
            id == '@' ||
            userIdentifier.isEmpty ||
            userIdentifier == '@' ||
            id.toLowerCase() == userIdentifier.toLowerCase();
      });
    } catch (_) {
      return false;
    }
  }

  /// Port of `News.isCommunityNews`: the post has been shared to the community
  /// (has a community-section entry in `viewIn`). Drives whether the share
  /// action shows at all, matching `VoicesAdapter.canShare`.
  static bool isCommunityNews(NewsRow row) {
    final viewIn = row.viewIn;
    if (viewIn == null || viewIn.isEmpty) return false;
    try {
      final decoded = jsonDecode(viewIn);
      if (decoded is! List) return false;
      return decoded.any(
        (element) =>
            element is Map<String, dynamic> &&
            JsonUtils.getString('section', element).toLowerCase() ==
                'community',
      );
    } catch (_) {
      return false;
    }
  }

  /// Port of `News.calculateSortDate`: a post shared into the community sorts
  /// by when it was *shared*, not when it was written.
  static int sortDateOf(NewsRow row) {
    final viewIn = row.viewIn;
    if (viewIn == null || viewIn.isEmpty) return row.time;
    try {
      final decoded = jsonDecode(viewIn);
      if (decoded is! List) return row.time;
      for (final element in decoded) {
        if (element is! Map<String, dynamic>) continue;
        if (JsonUtils.getString('section', element).toLowerCase() ==
                'community' &&
            element.containsKey('sharedDate')) {
          return JsonUtils.getLong('sharedDate', element);
        }
      }
    } catch (_) {
      // `calculateSortDate` swallows this too and falls back to `time`.
    }
    return row.time;
  }

  /// Port of `News.createNews`. Returns the new row's local id.
  Future<String> createPost({
    required String message,
    required String userId,
    required String userName,
    String? userJson,
    String? planetCode,
    String? parentCode,
    String? messageType,
    String? messagePlanetCode,
    String? viewInId,
    String? viewInSection,
    String? viewInName,
    List<String> imageUrls = const [],
    List<VoiceImageAttachment> attachments = const [],
    bool chat = false,
  }) async {
    final id = _createId();
    // The id is minted here and the bytes are written under it here, in one
    // place, so the row and the files cannot disagree about the key. Handing
    // the id to a caller to do the write is the shape Phase 100's
    // verification-photo bug had, and its symptom was silence.
    final pending = [...imageUrls, ...await _writeAttachments(id, attachments)];
    await _dao.upsert(
      NewsEntriesCompanion.insert(
        id: id,
        message: Value(message),
        time: Value(_now().millisecondsSinceEpoch),
        createdOn: Value(planetCode),
        avatar: const Value(''),
        docType: const Value('message'),
        userName: Value(userName),
        parentCode: Value(parentCode),
        messagePlanetCode: Value(messagePlanetCode),
        messageType: Value(messageType),
        sharedBy: const Value(''),
        viewIn: Value(
          _viewInJson(id: viewInId, section: viewInSection, name: viewInName),
        ),
        chat: Value(chat),
        userId: Value(userId),
        replyTo: const Value(''),
        user: Value(userJson),
        imageUrls: Value(pending),
      ),
    );
    return id;
  }

  /// Writes each picked image into the slot [VoiceImages] owns for [newsId]
  /// and returns the `imageUrls` entries pointing at them.
  ///
  /// An image whose bytes cannot be written is **dropped from the list**
  /// rather than recorded: an entry with no file behind it would make the
  /// uploader take its "a missing file is a no-op" branch on every drain, and
  /// the post would upload with a reference to nothing. Losing the image
  /// visibly beats a document that claims an attachment it does not have.
  Future<List<String>> _writeAttachments(
    String newsId,
    List<VoiceImageAttachment> attachments,
  ) async {
    final entries = <String>[];
    for (final attachment in attachments) {
      final file = await VoiceImages.write(
        newsId: newsId,
        filename: attachment.filename,
        bytes: attachment.bytes,
      );
      if (file == null) continue;
      entries.add(
        PendingVoiceImage(
          imageUrl: file.path,
          fileName: attachment.filename,
        ).encode(),
      );
    }
    return entries;
  }

  /// The images on [newsId] that have not reached the server yet.
  Future<List<PendingVoiceImage>> pendingImagesFor(String newsId) async {
    final row = await _dao.getById(newsId);
    if (row == null) return const [];
    return [for (final raw in row.imageUrls) ?PendingVoiceImage.decode(raw)];
  }

  /// Port of `getViewInJson`: an empty array unless a target was named.
  ///
  /// A null [name] omits the key rather than writing `"name": null`, which is
  /// what the Kotlin produces: `addProperty("name", map["name"])` stores a
  /// `JsonNull`, and `JsonUtils.gson` is a bare `Gson()` with `serializeNulls`
  /// **off**, so the key is dropped at write time. The difference is not
  /// cosmetic — [shareToCommunity] back-fills the first entry's name only when
  /// `!first.containsKey('name')` (the Kotlin's `!obj.has("name")`, which is
  /// true for an *absent* key and false for an explicit null), so an explicit
  /// null would silently disable that back-fill for every post sharing this
  /// helper.
  static String _viewInJson({String? id, String? section, String? name}) {
    if (id == null || id.isEmpty) return jsonEncode(const []);
    return jsonEncode([
      {'_id': id, 'section': section, 'name': ?name},
    ]);
  }

  /// The author object a locally authored `news` document carries.
  ///
  /// `News.createNews` sets `news.user = gson.toJson(user.serialize())`, and
  /// `serializeNews` writes that object into the document. It is the **only**
  /// author identity on a news document — there is no top-level `userId` or
  /// `userName` key — and `NewsMapper.fromDoc` reads both back out of it, so a
  /// post uploaded without it has no author on Planet *and* loses its author
  /// locally on the next sync-in.
  ///
  /// This is `UserEntity.serialize()` minus two groups, deliberately:
  ///
  /// - the credential branch (`password` / `derived_key` / `salt` /
  ///   `password_scheme`) and the device trio. `serialize()` is the body of
  ///   the `_users` PUT, where those belong; a voices post is a public
  ///   document and must never carry them. `UserMapper.toDoc` is that PUT
  ///   body — do not reach for it here.
  /// - the `_attachments` photo, which would embed the user's profile image
  ///   in every post they author.
  ///
  /// Everything else matches `serialize()` field for field **except**
  /// `iterations` (`UserEntity.kt:83-88`), which is left out deliberately: it
  /// is a PBKDF2 parameter and a voices post is a public document, so adding
  /// it wants the same explicit judgement as the credential branch rather
  /// than a silent completion of the field list. The `_id`/`_rev` pair is
  /// written only for an account the server knows, as `serialize()` has it.
  ///
  /// **A null value drops its key rather than writing `null`.** `createNews`
  /// serializes with `JsonUtils.gson`, a bare `Gson()` whose `serializeNulls`
  /// is off, so `addProperty("middleName", null)` writes nothing at all — a
  /// user with no middle name produces an object with no `middleName` key.
  /// This is the same rule [_viewInJson] twelve lines up already applies, and
  /// applying it in one place and not the other was an inconsistency inside
  /// one file. Nothing on either side reads these with a `has`/`containsKey`
  /// test today, unlike `viewIn`'s `name`, so this is faithfulness rather than
  /// a fix — but the next reader of `_viewInJson` should not find a
  /// counter-example sitting beside it.
  static String authorJson(UserRow user) {
    final couchId = user.couchId;
    return jsonEncode(<String, dynamic>{
      if (couchId != null && couchId.isNotEmpty) ...{
        '_id': couchId,
        if (user.rev != null) '_rev': user.rev,
      },
      'name': ?user.name,
      'roles': user.rolesList,
      'isUserAdmin': user.userAdmin,
      'joinDate': user.joinDate,
      'firstName': ?user.firstName,
      'lastName': ?user.lastName,
      'middleName': ?user.middleName,
      'email': ?user.email,
      'language': ?user.language,
      'level': ?user.level,
      'type': 'user',
      'gender': ?user.gender,
      'phoneNumber': ?user.phoneNumber,
      'birthDate': ?user.dob,
      'age': ?user.age,
      'parentCode': ?user.parentCode,
      'planetCode': ?user.planetCode,
      'birthPlace': ?user.birthPlace,
      'isArchived': user.isArchived,
    });
  }

  /// Port of the `map["news"]` branch of `News.createNews` — writes the row a
  /// shared chat conversation becomes in the voices feed.
  ///
  /// [payload] is the map [buildChatShareMap] produced; the fields that come
  /// from the signed-in user are passed alongside it, exactly as
  /// `createNews(map, user, imageList)` takes them separately.
  ///
  /// Three pairings have to hold, and each has a test:
  ///
  /// - `docType = 'message'` and `createdOn = planetCode` are what
  ///   [planetNewsMessages] selects on (`NewsDao.getPlanetMessages`), and that
  ///   query is the only thing that feeds [extractSharedViewInIds]. Get either
  ///   wrong and the share succeeds but the dialog never marks the
  ///   destination as already shared.
  /// - `newsId` is the shared chat's CouchDB `_id`, which is what
  ///   [isAlreadyShared] looks a row up by.
  /// - the row is left with no `_id`, so `pendingUploads` finds it and
  ///   `VoicesUploader` delivers it. Nothing else would ever send it.
  ///
  /// One line of the Kotlin is deliberately **not** ported:
  /// `newsObj?.replace("=", ":")` (`News.kt:187`), a fossil from when the
  /// value was a `HashMap.toString()` (`{_id=abc}`) rather than JSON. In
  /// Kotlin it is a provable no-op, and not because of anything in the source:
  /// `JsonUtils.gson` is a bare `Gson()`, whose `htmlSafe` default escapes
  /// `=` to `\u003d` inside every string, so the encoded payload contains no
  /// `=` for the replace to find. Dart's `jsonEncode` emits `=` raw, so
  /// carrying the line across would rewrite every `=` in the user's own
  /// title and transcript — `2+2=4`, a URL query string, base64 padding —
  /// and CouchDB would keep the damage. Behaviour is preserved by dropping
  /// the line; `an "=" in conversation text survives the share` pins it.
  Future<String> createFromShareMap({
    required Map<String, String> payload,
    required String userId,
    required String userName,
    String? userJson,
    String? planetCode,
    String? parentCode,
  }) async {
    final id = _createId();
    final news = _decodeObject(payload['news']) ?? const <String, dynamic>{};
    final conversations = _shareConversations(news['conversations']);

    await _dao.upsert(
      NewsEntriesCompanion.insert(
        id: id,
        message: Value(payload['message']),
        time: Value(_now().millisecondsSinceEpoch),
        createdOn: Value(planetCode),
        avatar: const Value(''),
        docType: const Value('message'),
        userName: Value(userName),
        parentCode: Value(parentCode),
        messagePlanetCode: Value(payload['messagePlanetCode']),
        messageType: Value(payload['messageType']),
        sharedBy: const Value(''),
        viewIn: Value(
          _viewInJson(
            id: payload['viewInId'],
            section: payload['viewInSection'],
            // `getViewInJson` reads `map["name"]`, which `buildShareMap` never
            // writes, so the entry's name is null in the Kotlin too.
            name: payload['name'],
          ),
        ),
        // `String.toBoolean()`, which is case-insensitive and false for
        // anything that is not "true".
        chat: Value(payload['chat']?.toLowerCase() == 'true'),
        updatedDate: Value(int.tryParse(payload['updatedDate'] ?? '') ?? 0),
        userId: Value(userId),
        replyTo: Value(payload['replyTo'] ?? ''),
        user: Value(userJson),
        imageUrls: const Value([]),
        newsId: Value(JsonUtils.getString('_id', news)),
        newsRev: Value(JsonUtils.getString('_rev', news)),
        newsUser: Value(JsonUtils.getString('user', news)),
        aiProvider: Value(JsonUtils.getString('aiProvider', news)),
        newsTitle: Value(JsonUtils.getString('title', news)),
        // Left null for an empty conversation list, matching the Kotlin's
        // `if (!conversationsArray.isEmpty())` guard.
        conversations: Value(
          conversations.isEmpty ? null : jsonEncode(conversations),
        ),
        newsCreatedDate: Value(_shareMillis(news['createdDate'])),
        newsUpdatedDate: Value(_shareMillis(news['updatedDate'])),
      ),
    );
    return id;
  }

  /// The nested `conversations` value, normalized to `{query, response}` maps.
  ///
  /// It arrives as a JSON array *encoded into a string*, because every value
  /// of the Kotlin payload is a `String`. The Kotlin re-parses it under
  /// `conversationsElement.isJsonPrimitive && isString` and rebuilds each turn
  /// from just those two keys, which is what this mirrors — an unparseable
  /// value yields no conversations rather than throwing.
  static List<Map<String, String>> _shareConversations(dynamic value) {
    if (value is! String || value.isEmpty) return const [];
    try {
      final decoded = jsonDecode(value);
      if (decoded is! List) return const [];
      return decoded
          .whereType<Map<String, dynamic>>()
          .map(
            (turn) => {
              'query': JsonUtils.getString('query', turn),
              'response': JsonUtils.getString('response', turn),
            },
          )
          .toList(growable: false);
    } catch (_) {
      return const [];
    }
  }

  /// `JsonUtils.getLong` over a value the payload stringified: a number is
  /// taken as-is, a numeric string is parsed, anything else is 0.
  static int _shareMillis(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is String) return int.tryParse(value) ?? 0;
    return 0;
  }

  /// Port of `VoicesRepositoryImpl.isAlreadyShared`.
  ///
  /// True when some voices row carrying this chat (`newsId == chatId`) already
  /// names [viewInId] in its `viewIn`. The Kotlin tests the *raw JSON text*
  /// for the substring `"_id":"<viewInId>"`, case-insensitively, rather than
  /// parsing — kept, because Gson writes `viewIn` without spaces and the same
  /// substring test is what decides whether the dialog offers the destination.
  ///
  /// The row lookup walks every voices row instead of a `WHERE newsId = ?`
  /// query: `NewsDao` has no such method and adding one means editing a file
  /// another lane owns this round. Same result, more rows read — see
  /// `PHASE_140_NOTES.md` § "Reported, not fixed".
  Future<bool> isAlreadyShared(String chatId, String viewInId) async {
    final rows = await _dao.getAll();
    final needle = '"_id":"$viewInId"'.toLowerCase();
    return rows
        .where((row) => row.newsId == chatId)
        .any((row) => (row.viewIn ?? '').toLowerCase().contains(needle));
  }

  /// Port of `NewsDao.getPlanetMessages` — the planet's `message` rows, which
  /// is the set [extractSharedViewInIds] is computed over.
  ///
  /// Both predicates are case-insensitive (`COLLATE NOCASE`), and an absent
  /// planet code yields nothing rather than everything.
  Future<List<NewsRow>> planetNewsMessages(String? planetCode) async {
    if (planetCode == null || planetCode.isEmpty) return const [];
    final wanted = planetCode.toLowerCase();
    final rows = await _dao.getAll();
    return rows
        .where(
          (row) =>
              (row.docType ?? '').toLowerCase() == 'message' &&
              (row.createdOn ?? '').toLowerCase() == wanted,
        )
        .toList(growable: false);
  }

  /// Port of `ChatRepositoryImpl.extractSharedViewInIds`: for each shared
  /// chat, the set of destinations it has already been shared to.
  ///
  /// Keyed by the *chat's* CouchDB id (`newsId`), not the voices row's own id.
  /// Rows with no `newsId` — every ordinary voice post — are dropped, and a
  /// row whose `viewIn` will not parse contributes nothing rather than
  /// failing the whole map.
  static Map<String, Set<String>> extractSharedViewInIds(List<NewsRow> rows) {
    final result = <String, Set<String>>{};
    for (final row in rows) {
      final newsId = row.newsId;
      if (newsId == null) continue;
      final ids = result.putIfAbsent(newsId, () => <String>{});
      try {
        final decoded = jsonDecode(row.viewIn ?? '');
        if (decoded is! List) continue;
        for (final element in decoded) {
          if (element is! Map<String, dynamic>) continue;
          final id = element['_id'];
          if (id is String) ids.add(id);
        }
      } catch (_) {
        // `groupBy` still yields the key with whatever the other rows
        // contributed; only this row's ids are lost.
      }
    }
    return result;
  }

  /// Port of `postReply`.
  ///
  /// The reply inherits its parent's audience verbatim — `viewableBy`,
  /// `viewableId` and the whole `viewIn` array — so it cannot become visible to
  /// an audience the post it answers was not.
  Future<String?> postReply({
    required String parentId,
    required String message,
    required String userId,
    required String userName,
    String? userJson,
    String? planetCode,
    String? parentCode,
    List<String> imageUrls = const [],
    List<VoiceImageAttachment> attachments = const [],
  }) async {
    final parent = await _dao.getById(parentId);
    if (parent == null) return null;
    final id = _createId();
    final pending = [...imageUrls, ...await _writeAttachments(id, attachments)];
    await _dao.upsert(
      NewsEntriesCompanion.insert(
        id: id,
        message: Value(message),
        time: Value(_now().millisecondsSinceEpoch),
        createdOn: Value(planetCode),
        avatar: const Value(''),
        docType: const Value('message'),
        userName: Value(userName),
        parentCode: Value(parentCode),
        userId: Value(userId),
        user: Value(userJson),
        // `postReply` keys the reply to the parent's *local* id — the Kotlin
        // switched from `news._id ?: news.id` to `news.id` (5f3198970), and
        // its test pins "replyTo is the parent's local id, not its server
        // _id". Rows are keyed by `_id` after a sync, so the two coincide for
        // synced posts; this matters for replies on posts not yet uploaded.
        replyTo: Value(parent.id),
        viewableBy: Value(parent.viewableBy ?? ''),
        viewableId: Value(parent.viewableId ?? ''),
        messageType: Value(parent.messageType ?? ''),
        messagePlanetCode: Value(parent.messagePlanetCode ?? ''),
        viewIn: Value(parent.viewIn ?? ''),
        imageUrls: Value(pending),
      ),
    );
    return id;
  }

  /// Port of `editPost`. A blank message is ignored, as upstream does.
  Future<bool> editPost({
    required String newsId,
    required String message,
    Set<String> imagesToRemove = const {},
    List<String> newImages = const [],
  }) async {
    if (message.isEmpty) return false;
    final row = await _dao.getById(newsId);
    if (row == null) return false;

    var urls = row.imageUrls;
    if (imagesToRemove.isNotEmpty) {
      urls = urls
          .where((entry) {
            try {
              final decoded = jsonDecode(entry);
              if (decoded is! Map<String, dynamic>) return true;
              return !imagesToRemove.contains(
                JsonUtils.getString('imageUrl', decoded),
              );
            } catch (_) {
              // An entry that is not JSON is kept, matching the Kotlin's
              // `catch { true }` — removal only applies to what it can parse.
              return true;
            }
          })
          .toList(growable: false);
    }

    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            imageUrls: Value([...urls, ...newImages]),
            message: Value(message),
            isEdited: const Value(true),
            editedTime: Value(_now().millisecondsSinceEpoch),
          ),
    );
    return true;
  }

  /// Port of `shareNewsToCommunity`: append a community entry to the post's
  /// `viewIn`, so every member of the planet can see it in the community feed.
  ///
  /// Upstream f4adebf hardened three things this mirrors:
  /// - a malformed existing `viewIn` is treated as an empty array and the share
  ///   proceeds, rather than failing the whole action;
  /// - the first entry's `name` is filled from [teamName] only when it is
  ///   missing *and* a non-empty name was passed;
  /// - the community `_id` is `planetCode@parentCode`, or empty when both are —
  ///   never a bare `"@"`.
  ///
  /// One deliberate divergence: the row is marked [NewsRow.isEdited] so it
  /// qualifies for [pendingUploads]. The Kotlin re-PUTs every post on every
  /// sync, so its share is picked up unconditionally; this port only uploads
  /// rows that are new or edited, so without the flag the share would never
  /// reach the server.
  Future<bool> shareToCommunity({
    required String newsId,
    required String userId,
    String planetCode = '',
    String parentCode = '',
    String teamName = '',
  }) async {
    final row = await _dao.getById(newsId);
    if (row == null) return false;

    List<dynamic> entries;
    try {
      entries = (row.viewIn == null || row.viewIn!.isEmpty)
          ? <dynamic>[]
          : jsonDecode(row.viewIn!) as List? ?? <dynamic>[];
    } catch (_) {
      entries = <dynamic>[];
    }

    if (entries.isNotEmpty) {
      final first = entries.first;
      if (first is Map<String, dynamic> &&
          !first.containsKey('name') &&
          teamName.isNotEmpty) {
        first['name'] = teamName;
      }
    }

    entries.add({
      'section': 'community',
      '_id': (planetCode.isNotEmpty || parentCode.isNotEmpty)
          ? '$planetCode@$parentCode'
          : '',
      'sharedDate': _now().millisecondsSinceEpoch,
    });

    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            sharedBy: Value(userId),
            viewIn: Value(jsonEncode(entries)),
            isEdited: const Value(true),
          ),
    );
    return true;
  }

  /// Port of `deleteNews` + `collectNewsAndReplies`: a post takes its whole
  /// reply subtree with it, or the replies would be orphaned and invisible.
  /// The post and every reply beneath it — the exact set [deletePost] removes
  /// when it actually deletes. Exposed so a caller can withdraw their queued
  /// uploads first.
  Future<List<String>> collectThreadIds(String newsId) =>
      _collectWithReplies(newsId);

  /// Port of `deletePost(newsId, teamName)`.
  ///
  /// Deleting from a *team* screen (non-empty [teamName]) removes the post and
  /// its whole reply subtree. Deleting from the *community* feed
  /// ([teamName] empty) only unshares when the post still has another audience
  /// left: the community entry — and any other shared-in entries — are stripped
  /// from `viewIn`, `sharedBy` is cleared, and the row stays. A post whose
  /// `viewIn` has fewer than two entries (a direct community post), is
  /// unparseable, or has every entry stripped by the filter is deleted
  /// outright, replies included.
  ///
  /// Returns the number of rows deleted, or 0 when the post was unshared
  /// instead.
  Future<int> deletePost(String newsId, {String teamName = ''}) async {
    final row = await _dao.getById(newsId);
    if (row == null) return 0;

    List<dynamic>? entries;
    final viewIn = row.viewIn;
    if (viewIn != null && viewIn.isNotEmpty) {
      try {
        entries = jsonDecode(viewIn) as List?;
      } catch (_) {
        entries = null;
      }
    }

    if (teamName.isNotEmpty || entries == null || entries.length < 2) {
      final ids = await _collectWithReplies(newsId);
      await _dao.deleteByIds(ids);
      return ids.length;
    }

    final kept = entries.where((element) {
      if (element is! Map<String, dynamic>) return false;
      final isCommunity =
          JsonUtils.getString('section', element).toLowerCase() == 'community';
      return !isCommunity && !element.containsKey('sharedDate');
    }).toList();

    if (kept.isEmpty) {
      final ids = await _collectWithReplies(newsId);
      await _dao.deleteByIds(ids);
      return ids.length;
    }

    // Marked edited for the same reason as [shareToCommunity]: the unshare
    // has to qualify for the upload path, which this port keys on `isEdited`.
    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            viewIn: Value(jsonEncode(kept)),
            sharedBy: const Value(''),
            isEdited: const Value(true),
          ),
    );
    return 0;
  }

  Future<List<String>> _collectWithReplies(String newsId) async {
    // One set across the whole walk, not one per frame. `replyTo` is server
    // data and nothing guarantees it is acyclic: two rows pointing at each
    // other would otherwise recurse until the stack gives out, from an
    // ordinary "delete post" tap.
    final seen = <String>{};
    final ids = <String>[];
    await _walkReplies(newsId, seen, ids);
    return ids;
  }

  Future<void> _walkReplies(
    String newsId,
    Set<String> seen,
    List<String> ids,
  ) async {
    if (!seen.add(newsId)) return;
    ids.add(newsId);
    // New replies key on the parent's local id, but rows written before the
    // Kotlin's 5f3198970 switch may carry the server id, so the walk still
    // probes both keys — belt-and-braces for existing data rather than
    // load-bearing for new rows.
    final row = await _dao.getById(newsId);
    final keys = <String>{newsId, if (row?.docId != null) row!.docId!};
    for (final key in keys) {
      for (final reply in await _dao.directReplies(key)) {
        await _walkReplies(reply.id, seen, ids);
      }
    }
  }

  /// Port of `addLabel`.
  Future<void> addLabel(String newsId, String label) async {
    final row = await _dao.getById(newsId);
    if (row == null) return;
    // `addLabel` appends unconditionally, so the same label twice yields a
    // duplicate. Deduplicated here: the label set drives filter chips, and a
    // repeat renders a second identical chip that cannot be told apart.
    if (row.labels.contains(label)) return;
    // Flagged like every other local mutation, and for the same reason: the
    // `labels` column goes on the wire ([serialize]), Kotlin's sweep re-sends
    // the whole table so a Kotlin label change reaches the server, and this
    // port only uploads rows that are new or edited. Nothing calls this yet —
    // which is exactly why the flag belongs here now rather than being
    // discovered missing by whoever wires the label chips up.
    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            labels: Value([...row.labels, label]),
            isEdited: const Value(true),
          ),
    );
  }

  /// Port of `removeLabel`.
  Future<void> removeLabel(String newsId, String label) async {
    final row = await _dao.getById(newsId);
    if (row == null) return;
    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            labels: Value(
              row.labels
                  .where((entry) => entry != label)
                  .toList(growable: false),
            ),
            // See [addLabel].
            isEdited: const Value(true),
          ),
    );
  }

  /// Port of `insertNewsList`.
  Future<int> cacheDocuments(List<Map<String, dynamic>> documents) async {
    if (documents.isEmpty) return 0;
    final docIds = documents
        .map((doc) => JsonUtils.getString('_id', doc))
        .where((id) => id.isNotEmpty)
        .toList(growable: false);
    final existing = {
      for (final row in await _dao.getByDocIds(docIds)) row.docId: row,
    };
    final rows = <NewsEntriesCompanion>[];
    for (final doc in documents) {
      final mapped = NewsMapper.fromDoc(
        doc,
        existing: existing[JsonUtils.getString('_id', doc)],
      );
      if (mapped != null) rows.add(mapped);
    }
    await _dao.upsertAll(rows);
    return rows.length;
  }

  /// Posts that still need to reach the server.
  ///
  /// A deliberate divergence from `getNewsForUpload`, which returns *every*
  /// post that is not a guest's and re-PUTs it on each sync — harmless when the
  /// upload is a one-shot batch, but it would refill the outbox with unchanged
  /// documents on every drain and churn a `_rev` per post per sync. Only posts
  /// that were never delivered or have been edited since are queued.
  ///
  /// The guest exclusion is kept: a guest account has no CouchDB user document,
  /// so the server rejects anything authored under it.
  Future<List<NewsRow>> pendingUploads() async {
    final rows = await _dao.getAll();
    return rows
        .where((row) => !UserMapper.isGuestId(row.userId))
        .where((row) => row.docId == null || row.docId!.isEmpty || row.isEdited)
        .toList(growable: false);
  }

  /// Port of `markNewsUploaded`.
  ///
  /// Clearing `imageUrls` is what marks the attachments as delivered; the
  /// server's `images` array replaces them.
  ///
  /// [delivered] is the document that actually went on the wire. When the row
  /// has changed since that payload was captured, the send carried a
  /// **superseded body** and the row is not in sync with the server, so
  /// `isEdited` is left set and the next sweep re-queues it — this time with
  /// the `_id`/`_rev` just recorded, so it lands as an update rather than a
  /// second document.
  ///
  /// Without this, clearing the flag unconditionally is a silent loss.
  /// [VoicesUploader.queuePending] leaves an in-flight row alone so an append
  /// is not replayed, so an edit made while the POST is on the wire is never
  /// queued; clearing `isEdited` on arrival then drops the row out of
  /// [pendingUploads] for good, and `NewsMapper.fromDoc` writes `message`
  /// unconditionally, so the next pull overwrites the user's text with the
  /// server's older copy — destroyed on the device as well as never sent.
  /// **Kotlin does not lose it**: `getNewsForUpload` has no predicate beyond
  /// the guest prefix and `markNewsUploaded` touches no edit marker, so its
  /// next sweep re-serializes the edited message with the fresh `_rev`. This
  /// is the port's narrow predicate paying for itself, not a Kotlin quirk.
  Future<void> markUploaded(
    String id,
    String docId,
    String rev, {
    List<dynamic> images = const [],
    Map<String, dynamic>? delivered,
  }) async {
    final row = await _dao.getById(id);
    if (row == null) return;
    final superseded = delivered != null && !payloadMatchesRow(delivered, row);
    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            docId: Value(docId),
            rev: Value(rev),
            images: Value(jsonEncode(images)),
            imageUrls: const Value([]),
            isEdited: Value(superseded),
          ),
    );
    // The bytes have reached CouchDB as attachments on their resource
    // documents, and `imageUrls` — the only thing that points at them — has
    // just been cleared, so the slot is unreachable from here on. Kotlin has
    // no equivalent because it never copied the file in the first place; it
    // only forgets the picker's path.
    if (row.imageUrls.isNotEmpty) await VoiceImages.deleteFor(id);
  }

  /// Whether [payload] still describes [row] — i.e. nothing local changed
  /// between the payload being captured and now.
  ///
  /// Compares whole documents rather than a hand-listed set of mutable
  /// columns, so a future writer touching a column nobody thought of here is
  /// covered by construction. Three keys are excluded: `_id` and `_rev`,
  /// which the send is in the middle of establishing, and the origin fields
  /// [VoicesUploader.queuePending] stamps on after serializing. Key order is
  /// comparable because both maps come from [serialize], and the spread that
  /// adds the origin fields appends rather than reorders.
  static bool payloadMatchesRow(Map<String, dynamic> payload, NewsRow row) {
    bool established(String key) =>
        key == '_id' || key == '_rev' || key == 'androidId' || key == 'app';
    Map<String, dynamic> comparable(Map<String, dynamic> doc) => {
      for (final entry in doc.entries)
        if (!established(entry.key)) entry.key: entry.value,
    };
    return jsonEncode(comparable(payload)) ==
        jsonEncode(comparable(serialize(row)));
  }

  /// Port of `VoicesRepositoryImpl.updateReaction` — toggles a user's emoji
  /// reaction on a voice. The `reactions` column is a JSON map of emoji to
  /// a list of user ids; toggling adds the user's id if absent, or removes
  /// it (and the emoji key if the list becomes empty) if present. A user
  /// can only have one reaction at a time — switching emojis removes the
  /// old one first.
  Future<void> toggleReaction(
    String newsId,
    String emoji,
    String userId,
  ) async {
    final row = await _dao.getById(newsId);
    if (row == null) return;
    final current = _parseReactions(row.reactions);
    final updated = _toggleReaction(current, emoji, userId);
    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            reactions: Value(updated.isEmpty ? null : jsonEncode(updated)),
            isEdited: const Value(true),
          ),
    );
  }

  /// Creates a `News` row as an inline comment on a team task or meetup.
  /// `messageType = 'comment'` and `replyTo = parentId` distinguish it from a
  /// voice post.
  ///
  /// **Not a port.** An earlier version of this comment said "Port of
  /// `TeamsRepositoryImpl.addComment`"; there is no such Kotlin method —
  /// `grep -rn "fun addComment" app/src` is empty and `messageType =
  /// "comment"` appears nowhere in the Kotlin. Inline comments are a
  /// port-original from unmerged issue #15112 (Phase 74), so there is no
  /// ground truth to check this against and a naming a Kotlin method that does
  /// not exist is how a future audit reaches a wrong verdict.
  ///
  /// [userJson] is the author, and it is not optional in practice even though
  /// the signature allows null: the nested `user` object is the **only**
  /// author identity a news document carries, and `NewsMapper.fromDoc` reads
  /// `user`, `userId` *and* `userName` back out of it unconditionally — so a
  /// comment uploaded without it is anonymous on Planet **and** loses its
  /// author here at the next sync-in. The three voice writers all pass
  /// [authorJson]; this one did not.
  Future<NewsRow> addComment({
    required String parentId,
    String? teamId,
    required String message,
    required String userId,
    String? userName,
    String? userJson,
    String? planetCode,
    String? parentCode,
  }) async {
    final now = _now().millisecondsSinceEpoch;
    final id = '$now-${userId.hashCode.abs()}';
    final companion = NewsEntriesCompanion.insert(
      id: id,
      message: Value(message),
      time: Value(now),
      docType: const Value('message'),
      messageType: const Value('comment'),
      replyTo: Value(parentId),
      userName: Value(userName),
      userId: Value(userId),
      user: Value(userJson),
      createdOn: Value(planetCode),
      parentCode: Value(parentCode),
      messagePlanetCode: Value(planetCode),
      viewableBy: Value(teamId == null ? '' : 'teams'),
      viewableId: Value(teamId ?? ''),
      avatar: const Value(''),
      sharedBy: const Value(''),
      imageUrls: const Value([]),
      labels: const Value([]),
      isEdited: const Value(false),
      editedTime: const Value(0),
      chat: const Value(false),
    );
    await _dao.upsert(companion);
    return (await _dao.getById(id))!;
  }

  /// Port of `TeamsRepositoryImpl.deleteComment`.
  Future<void> deleteComment(String commentId) async {
    await _dao.deleteById(commentId);
  }

  Map<String, List<String>> _parseReactions(String? json) {
    if (json == null || json.isEmpty) return {};
    try {
      final decoded = jsonDecode(json) as Map<String, dynamic>;
      return {
        for (final entry in decoded.entries)
          entry.key: (entry.value as List).cast<String>(),
      };
    } catch (_) {
      return {};
    }
  }

  Map<String, List<String>> _toggleReaction(
    Map<String, List<String>> current,
    String emoji,
    String userId,
  ) {
    final result = {
      for (final entry in current.entries)
        entry.key: List<String>.from(entry.value),
    };
    // Remove the user from any existing emoji first (one reaction at a time).
    String? existingEmoji;
    for (final entry in result.entries) {
      if (entry.value.contains(userId)) {
        existingEmoji = entry.key;
        entry.value.remove(userId);
        if (entry.value.isEmpty) result.remove(entry.key);
        break;
      }
    }
    // If toggling the same emoji off, we're done. Otherwise add to the new one.
    if (existingEmoji != emoji) {
      result.putIfAbsent(emoji, () => []).add(userId);
    }
    return result;
  }

  /// Port of `serializeNews`.
  static Map<String, dynamic> serialize(NewsRow row) {
    final object = <String, dynamic>{
      'chat': row.chat,
      'message': row.message,
      if (row.docId != null) '_id': row.docId,
      if (row.rev != null) '_rev': row.rev,
      'time': row.time,
      'createdOn': row.createdOn,
      'docType': row.docType,
    };

    // Port of `addViewIn`: the audience keys are written only when populated,
    // so an unaddressed post does not carry empty ones.
    if ((row.viewableId ?? '').isNotEmpty) {
      object['viewableId'] = row.viewableId;
      object['viewableBy'] = row.viewableBy;
    }
    final viewIn = _decodeList(row.viewIn);
    if (viewIn.isNotEmpty) object['viewIn'] = viewIn;

    object.addAll({
      'avatar': row.avatar,
      'messageType': row.messageType,
      'messagePlanetCode': row.messagePlanetCode,
      'createdOn': row.createdOn,
      'replyTo': row.replyTo,
      'parentCode': row.parentCode,
      'images': _decodeList(row.images),
      'labels': row.labels,
      'user': _decodeObject(row.user),
      'news': {
        '_id': row.newsId,
        '_rev': row.newsRev,
        'user': row.newsUser,
        'aiProvider': row.aiProvider,
        'title': row.newsTitle,
        'conversations': _decodeList(row.conversations),
        'createdDate': row.newsCreatedDate,
        'updatedDate': row.newsUpdatedDate,
        'sharedBy': row.sharedBy,
        if (row.reactions != null && row.reactions!.isNotEmpty)
          'reactions': row.reactions,
      },
    });
    return object;
  }

  static List<dynamic> _decodeList(String? value) {
    if (value == null || value.isEmpty) return const [];
    try {
      final decoded = jsonDecode(value);
      return decoded is List ? decoded : const [];
    } catch (_) {
      return const [];
    }
  }

  static Map<String, dynamic>? _decodeObject(String? value) {
    if (value == null || value.isEmpty) return null;
    try {
      final decoded = jsonDecode(value);
      return decoded is Map<String, dynamic> ? decoded : null;
    } catch (_) {
      return null;
    }
  }

  /// Pulls the CouchDB `news` database page by page.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final auth = UrlUtils.authHeader(config);
    final countResult = await _api.getJsonObject(
      '$dbUrl/news/_all_docs?limit=0',
      authHeader: auth,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }
    final total = JsonUtils.getInt('total_rows', countResult.data);
    if (total == 0) {
      await _dao.deleteNotIn(const []);
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final sizer = AdaptiveBatchProcessor(initialSize: 100);
    final docIds = <String>[];
    var skip = 0;
    var completeWalk = true;
    while (skip < total) {
      final size = sizer.currentSize;
      final timer = Stopwatch()..start();
      final result = await _api.getJsonObject(
        '$dbUrl/news/_all_docs?include_docs=true&limit=$size&skip=$skip',
        authHeader: auth,
      );
      timer.stop();
      if (result is! NetworkSuccess<Map<String, dynamic>>) {
        sizer.recordFailure();
        return SyncFailed(describeNetworkFailure(result));
      }
      sizer.recordSuccess(timer.elapsedMilliseconds);
      final rows = result.data['rows'];
      if (rows is! List || rows.isEmpty) {
        completeWalk = false;
        break;
      }
      final documents = rows
          .whereType<Map<String, dynamic>>()
          .map((row) => JsonUtils.getObject('doc', row))
          .whereType<Map<String, dynamic>>()
          .toList(growable: false);
      await cacheDocuments(documents);
      docIds.addAll(
        documents
            .map((doc) => JsonUtils.getString('_id', doc))
            .where((id) => id.isNotEmpty && !id.startsWith('_design/')),
      );
      skip += rows.length;
      onProgress?.call(
        SyncProgress(completed: skip.clamp(0, total), total: total),
      );
    }
    // Only after a complete walk, so a server that changed mid-pagination
    // cannot make the app delete posts it simply did not see.
    if (completeWalk) await _dao.deleteNotIn(docIds);
    return SyncComplete(docIds.length);
  }
}
