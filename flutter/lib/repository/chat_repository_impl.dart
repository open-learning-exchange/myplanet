import 'dart:convert';

import 'package:drift/drift.dart' show Value;

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/chat_mapper.dart';
import 'chat_repository.dart';

/// Port of `repository/ChatRepositoryImpl.kt`.
class ChatRepositoryImpl implements ChatRepository {
  ChatRepositoryImpl({
    required this.planetApi,
    required this.chatDao,
    required this.serverUrl,
    this.timeout = const Duration(seconds: 60),
    this.maxAttempts = 3,
    this.retryDelay = const Duration(seconds: 2),
  });

  final PlanetApi planetApi;
  final ChatDao chatDao;
  final String serverUrl;
  final Duration timeout;

  /// Number of attempts for one chat POST before falling back to the outbox.
  ///
  /// Port of `RetryUtils.retry(maxAttempts = 3, delayMs = 2000L)` used around
  /// the Kotlin chat calls. A transient failure while the user is still
  /// composing should retry immediately instead of waiting for the next outbox
  /// drain.
  final int maxAttempts;

  /// Delay between retry attempts.
  final Duration retryDelay;

  /// Chat documents are smaller than courses/resources, so a standard starting
  /// page size works well.
  static const int initialBatchSize = 100;

  /// The CouchDB write receipt inside a chat response.
  ///
  /// The key is `couchDBResponse`. That is not a guess: `model/ChatResponse.kt`
  /// declares the field as `@SerializedName("couchDBResponse")`, and the Gson
  /// mapping is the only authority on what the endpoint puts on the wire.
  /// Reading it as `couchdb` found nothing on every single response — so a new
  /// chat always reported "saved without a document id" and stored no row, and
  /// a continuation always kept its old `_rev` and hit a conflict on the turn
  /// after. Both halves had tests; both were written against this same wrong
  /// key, so both passed.
  static Map<String, dynamic>? _couchResponse(Map<String, dynamic> response) =>
      response['couchDBResponse'] as Map<String, dynamic>?;

  @override
  Future<Map<String, bool>?> fetchAiProviders() async {
    final url = '$serverUrl/checkProviders/';
    final result = await planetApi.getRaw(url);
    switch (result) {
      case NetworkSuccess(data: final raw):
        try {
          return Map<String, bool>.from(jsonDecode(raw) as Map);
        } catch (_) {
          return null;
        }
      case NetworkError():
      case NetworkException():
        return null;
    }
  }

  @override
  Future<ChatResult> sendNewChatRequest({
    required String query,
    required String user,
    required AiProviderConfig aiProvider,
  }) async {
    try {
      final request = {
        'data': {
          'user': user,
          'content': query,
          'aiProvider': aiProvider.toJson(),
        },
        'save': true,
      };

      final response = await _postChat(request);
      if (response == null) {
        return const ChatError('Request failed');
      }

      final status = response['status'] as String?;
      if (status != 'Success') {
        return ChatError(response['message'] as String? ?? 'Request failed');
      }

      final chatResponse = response['chat'] as String? ?? '';
      final couchResponse = _couchResponse(response);
      final rev = couchResponse?['rev'] as String? ?? '';
      // The server creates the CouchDB document and assigns its `_id`; the id
      // must come back from that response, as `couchDBResponse.id` does in the
      // Kotlin. Minting one locally instead left the row addressed by an id no
      // document has, so every follow-up message — which sends this id as
      // `_id` — targeted nothing, and a sync could not match the row either.
      final id = couchResponse?['id'] as String?;
      if (id == null || id.isEmpty) {
        // Kotlin falls back to `""`, which stores an unaddressable row that
        // also collides with any other on the primary key. Failing is closer
        // to the truth: without an id there is no conversation to continue.
        return const ChatError('Chat was saved without a document id');
      }

      final doc = ChatMapper.buildNewChatDoc(
        id: id,
        rev: rev,
        user: user,
        query: query,
        response: chatResponse,
        aiProvider: aiProvider.name,
      );
      await _saveChat(ChatMapper.fromDoc(doc));

      return ChatSuccess(response: chatResponse, id: id, rev: rev);
    } catch (e) {
      return ChatError(e.toString());
    }
  }

  @override
  Future<String> savePendingChat({
    required String user,
    required String query,
    required AiProviderConfig aiProvider,
    String? existingId,
    String? existingRev,
  }) async {
    // A local id is safe here precisely because the row is pending: when the
    // outbox drains, `ChatDao.markUploaded` replaces `docId`/`rev` with the
    // server's. That is not true of the success path, which must use the id
    // the server assigned.
    //
    // The timestamp has to actually interpolate. Written as '...\$…' inside
    // single quotes it was an escaped dollar, so every queued chat shared one
    // literal primary key and `upsertAll` replaced its predecessor: queue two
    // messages offline and only the second still existed to drain.
    final now = DateTime.now();
    final id = existingId ?? 'local-chat-${now.microsecondsSinceEpoch}';
    final existing = existingId == null
        ? null
        : await chatDao.findByDocId(existingId);
    final conversations = [
      ...ChatMapper.parseConversations(existing?.conversations),
      const ChatConversation().copyWith(query: query, response: ''),
    ];
    final timestamp = now.millisecondsSinceEpoch;
    await chatDao.upsertAll([
      ChatEntriesCompanion(
        id: Value(id),
        docId: Value(existingId),
        rev: Value(existingRev),
        user: Value(user),
        aiProvider: Value(aiProvider.name),
        title: Value(existing?.title ?? query),
        conversations: Value(ChatMapper.encodeConversations(conversations)),
        // `sortChatsByRecency` scores a row by the newest of these two, so a
        // row without them lands at 0 — the message the user just typed sorted
        // below every conversation they had ever had. A continuation keeps the
        // created date it already has and only moves its updated date, which
        // is what `addConversation` does in the Kotlin.
        createdDate: Value(existing?.createdDate ?? '$timestamp'),
        updatedDate: Value('$timestamp'),
        lastUsed: Value(timestamp),
        isUploaded: const Value(false),
      ),
    ]);
    return id;
  }

  @override
  Future<ChatResult> sendContinueChatRequest({
    required String query,
    required String user,
    required AiProviderConfig aiProvider,
    required String id,
    required String rev,
  }) async {
    try {
      final request = {
        'data': {
          'user': user,
          'content': query,
          'aiProvider': aiProvider.toJson(),
          '_id': id,
          '_rev': rev,
        },
        'save': true,
      };

      final response = await _postChat(request);
      // Kotlin's `sendContinueChatRequest` calls
      // `continueConversation(id, message, "", rev)` on its non-success branch
      // as well as its `catch`. Only the `catch` had been ported, and
      // `_postChat` turns a network failure into `null` rather than throwing —
      // so the offline send, the one case this behaviour exists for, took the
      // unported path and discarded the user's question outright. The row
      // written here is also what `_insertChatsInternal` protects from being
      // overwritten by a later sync, so without it that guard had nothing to
      // guard.
      if (response == null) {
        await _continueConversation(id, query, '', rev);
        return const ChatError('Request failed');
      }

      final status = response['status'] as String?;
      if (status != 'Success') {
        await _continueConversation(id, query, '', rev);
        return ChatError(response['message'] as String? ?? 'Request failed');
      }

      final chatResponse = response['chat'] as String? ?? '';
      final newRev = _couchResponse(response)?['rev'] as String? ?? rev;

      // Update local database
      await _continueConversation(id, query, chatResponse, newRev);

      return ChatSuccess(response: chatResponse, id: id, rev: newRev);
    } catch (e) {
      // Even on error, update local with empty response
      await _continueConversation(id, query, '', rev);
      return ChatError(e.toString());
    }
  }

  @override
  Future<List<ChatRow>> getChatHistoryForUser(String? userName) async {
    if (userName == null || userName.isEmpty) {
      return [];
    }
    final chats = await chatDao.getByUser(userName);
    return sortChatsByRecency(chats);
  }

  @override
  List<ChatRow> searchChats(
    String query,
    ChatSearchMode mode,
    List<ChatRow> chats,
  ) => searchChatsForMode(query, mode, chats);

  @override
  Future<String?> getLatestRev(String id) async {
    final rows = await chatDao.getByDocId(id);
    // Highest CouchDB generation wins. `_rev` is `<generation>-<hash>`, and the
    // hash is not ordered, so comparing the strings would pick the wrong
    // revision as soon as the generation reached two digits.
    String? latest;
    var highest = -1;
    for (final rev in rows.map((row) => row.rev).whereType<String>()) {
      final generation = int.tryParse(rev.split('-').first) ?? 0;
      if (generation > highest) {
        highest = generation;
        latest = rev;
      }
    }
    return latest;
  }

  @override
  Future<void> insertChatHistoryList(List<Map<String, dynamic>> docs) async {
    await _insertChatsInternal(docs);
  }

  @override
  Future<void> insertChatHistoryFromSync(
    List<Map<String, dynamic>> docs,
  ) async {
    final unwrapped = docs
        .where((doc) {
          final id = doc['_id'] as String?;
          return id != null && !id.startsWith('_design');
        })
        .map((doc) => doc['doc'] as Map<String, dynamic>? ?? doc)
        .toList();
    await _insertChatsInternal(unwrapped);
  }

  Future<void> _insertChatsInternal(List<Map<String, dynamic>> docs) async {
    if (docs.isEmpty) return;
    final companions = <ChatEntriesCompanion>[];
    for (final doc in docs) {
      final companion = ChatMapper.fromDoc(doc);
      // A continuation whose answer never arrived is stored locally as a
      // trailing query with an empty response, and it exists nowhere else —
      // `_continueConversation(id, query, '', rev)` on the error path. Letting
      // the sync overwrite `conversations` wholesale would replace it with the
      // server's older copy and drop the question the user asked.
      if (await _hasUnansweredQuery(companion.id.value)) continue;
      companions.add(companion);
    }
    if (companions.isEmpty) return;
    await chatDao.upsertAll(companions);
  }

  Future<bool> _hasUnansweredQuery(String id) async {
    final local = await chatDao.findByDocId(id);
    if (local == null) return false;
    final conversations = ChatMapper.parseConversations(local.conversations);
    if (conversations.isEmpty) return false;
    final last = conversations.last;
    return (last.query?.isNotEmpty ?? false) &&
        (last.response == null || last.response!.isEmpty);
  }

  Future<void> _saveChat(ChatEntriesCompanion chat) async {
    await chatDao.upsertAll([chat]);
  }

  Future<void> _continueConversation(
    String id,
    String query,
    String response,
    String rev,
  ) async {
    final existing = await chatDao.findByDocId(id);
    if (existing == null) return;

    final conversations = ChatMapper.parseConversations(existing.conversations);
    final newConversations = [
      ...conversations,
      ChatConversation(query: query, response: response),
    ];
    final updatedDate = DateTime.now().millisecondsSinceEpoch.toString();

    await chatDao.updateConversation(
      id,
      ChatMapper.encodeConversations(newConversations),
      updatedDate,
      // Kotlin's `addConversation` assigns `_rev` only
      // `if (!newRev.isNullOrEmpty())`. The DAO writes the column
      // unconditionally, so an empty revision from the server would erase the
      // handle the next upload needs — and `ChatDao.deleteNotIn` reads a
      // revision-less row as one the server never confirmed.
      rev.isEmpty ? (existing.rev ?? '') : rev,
    );
  }

  Future<Map<String, dynamic>?> _postChat(Map<String, dynamic> request) async {
    final url = '$serverUrl/chat';
    for (var attempt = 1; attempt <= maxAttempts; attempt++) {
      final result = await planetApi.sendJsonObject(
        url,
        method: 'POST',
        body: request,
      );

      switch (result) {
        case NetworkSuccess(data: final data):
          return data;
        case NetworkError():
        case NetworkException():
          if (attempt == maxAttempts) return null;
          await Future.delayed(retryDelay);
      }
    }
    return null;
  }

  @override
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);

    final countResult = await planetApi.getJsonObject(
      '$dbUrl/chat_history/_all_docs?limit=0',
      authHeader: authHeader,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }

    final totalRows = JsonUtils.getInt('total_rows', countResult.data);
    if (totalRows == 0) {
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final batchSizer = AdaptiveBatchProcessor(initialSize: initialBatchSize);
    final savedIds = <String>[];
    var skip = 0;
    var walkedEveryPage = true;

    while (skip < totalRows) {
      final batchSize = batchSizer.currentSize;
      final stopwatch = Stopwatch()..start();

      final pageResult = await planetApi.getJsonObject(
        '$dbUrl/chat_history/_all_docs?include_docs=true&limit=$batchSize&skip=$skip',
        authHeader: authHeader,
      );
      stopwatch.stop();

      if (pageResult is! NetworkSuccess<Map<String, dynamic>>) {
        batchSizer.recordFailure();
        return SyncFailed(describeNetworkFailure(pageResult));
      }
      batchSizer.recordSuccess(stopwatch.elapsedMilliseconds);

      final rows = pageResult.data['rows'];
      if (rows is! List || rows.isEmpty) {
        walkedEveryPage = false;
        break;
      }

      final docs = <Map<String, dynamic>>[];
      for (final row in rows) {
        if (row is! Map<String, dynamic>) continue;
        final doc = JsonUtils.getObject('doc', row);
        if (doc != null) {
          docs.add(doc);
          final id = JsonUtils.getString('_id', doc);
          if (id.isNotEmpty) savedIds.add(id);
        }
      }

      if (docs.isNotEmpty) {
        await insertChatHistoryFromSync(docs);
      }

      skip += rows.length;
      onProgress?.call(
        SyncProgress(
          completed: skip > totalRows ? totalRows : skip,
          total: totalRows,
        ),
      );
    }

    // Clean up local rows not present on server if we walked all pages.
    if (walkedEveryPage && savedIds.isNotEmpty) {
      await chatDao.deleteNotIn(savedIds);
    }

    return SyncComplete(savedIds.length);
  }
}
