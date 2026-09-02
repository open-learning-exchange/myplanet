import 'dart:convert';

import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/news_mapper.dart';
import '../data/local/user_mapper.dart';

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
  /// The Kotlin filters `isCommunitySection` in memory after the DAO query;
  /// [isCommunityNews] is the port of that filter.
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

  static String _formatDate(int millis) {
    final dt = DateTime.fromMillisecondsSinceEpoch(millis, isUtc: true);
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
    bool chat = false,
  }) async {
    final id = _createId();
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
        imageUrls: Value(imageUrls),
      ),
    );
    return id;
  }

  /// Port of `getViewInJson`: an empty array unless a target was named.
  static String _viewInJson({String? id, String? section, String? name}) {
    if (id == null || id.isEmpty) return jsonEncode(const []);
    return jsonEncode([
      {'_id': id, 'section': section, 'name': name},
    ]);
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
  }) async {
    final parent = await _dao.getById(parentId);
    if (parent == null) return null;
    final id = _createId();
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
        imageUrls: Value(imageUrls),
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
    await _dao.upsert(
      row.toCompanion(false).copyWith(labels: Value([...row.labels, label])),
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
  Future<void> markUploaded(
    String id,
    String docId,
    String rev, {
    List<dynamic> images = const [],
  }) async {
    final row = await _dao.getById(id);
    if (row == null) return;
    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            docId: Value(docId),
            rev: Value(rev),
            images: Value(jsonEncode(images)),
            imageUrls: const Value([]),
            isEdited: const Value(false),
          ),
    );
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

  /// Port of `TeamsRepositoryImpl.addComment` — creates a `News` row as an
  /// inline comment on a team task or meetup. `messageType = 'comment'`
  /// and `replyTo = parentId` distinguish it from a voice post.
  Future<NewsRow> addComment({
    required String parentId,
    String? teamId,
    required String message,
    required String userId,
    String? userName,
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
