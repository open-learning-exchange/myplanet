import 'dart:convert';

import 'package:dio/dio.dart';

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
      final id = _generateId();
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

      // Save to local database
      final doc = ChatMapper.buildNewChatDoc(
        id: id,
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
    return rows
        .map((r) => r.rev)
        .whereType<String>()
        .maxBy((rev) => int.tryParse(rev.split('-').firstOrNull ?? '0') ?? 0);
  }

  @override
  Future<void> insertChatHistoryList(List<Map<String, dynamic>> docs) async {
    await _insertChatsInternal(docs);
  }

  @override
  Future<void> insertChatHistoryFromSync(List<Map<String, dynamic>> docs) async {
    final unwrapped = docs.where((doc) {
      final id = doc['_id'] as String?;
      return id != null && !id.startsWith('_design');
    }).map((doc) => doc['doc'] as Map<String, dynamic>? ?? doc).toList();
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

  String _generateId() {
    // Generate a unique id similar to UUID but CouchDB-compatible
    final now = DateTime.now().millisecondsSinceEpoch;
    final random = (now % 0xFFFFFFFF).toRadixString(16).padLeft(8, '0');
    return 'chat_$now$random';
  }
}

extension<T> on List<T> {
  T? maxBy(int Function(T) selector) {
    if (isEmpty) return null;
    return fold(null as T?, (best, current) {
      if (best == null) return current;
      return selector(current) > selector(best) ? current : best;
    });
  }
}
