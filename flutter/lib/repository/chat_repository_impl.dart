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
  });

  final PlanetApi planetApi;
  final ChatDao chatDao;
  final String serverUrl;
  final Duration timeout;

  /// Chat documents are smaller than courses/resources, so a standard starting
  /// page size works well.
  static const int initialBatchSize = 100;

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
      final couchResponse = response['couchdb'] as Map<String, dynamic>?;
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
    final id =
        existingId ?? 'local-chat-\${DateTime.now().microsecondsSinceEpoch}';
    final existing = existingId == null
        ? null
        : await chatDao.findByDocId(existingId);
    final conversations = [
      ...ChatMapper.parseConversations(existing?.conversations),
      const ChatConversation().copyWith(query: query, response: ''),
    ];
    await chatDao.upsertAll([
      ChatEntriesCompanion(
        id: Value(id),
        docId: Value(existingId),
        rev: Value(existingRev),
        user: Value(user),
        aiProvider: Value(aiProvider.name),
        title: Value(existing?.title ?? query),
        conversations: Value(ChatMapper.encodeConversations(conversations)),
        lastUsed: Value(DateTime.now().millisecondsSinceEpoch),
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
      if (response == null) {
        return const ChatError('Request failed');
      }

      final status = response['status'] as String?;
      if (status != 'Success') {
        return ChatError(response['message'] as String? ?? 'Request failed');
      }

      final chatResponse = response['chat'] as String? ?? '';
      final newRev = response['couchdb']?['rev'] as String? ?? rev;

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
    return chatDao.getByUser(userName);
  }

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
      rev,
    );
  }

  Future<Map<String, dynamic>?> _postChat(Map<String, dynamic> request) async {
    final url = '$serverUrl/chat';
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
        return null;
    }
  }

  @override
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.basicAuthHeader('satellite', config.pin);

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
