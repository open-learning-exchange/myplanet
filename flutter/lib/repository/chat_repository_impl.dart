import 'dart:convert';

import '../core/network/network_result.dart';
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
    final companions = docs.map(ChatMapper.fromDoc).toList();
    await chatDao.upsertAll(companions);
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
}
