import 'dart:convert';

import 'package:drift/drift.dart';

import 'app_database.dart';

/// Port of `model/ChatHistory.kt`, `model/Conversation.kt`,
/// `model/ChatRequest.kt`, and the JSON-mapping portions of
/// `ChatRepositoryImpl`.
///
/// Maps CouchDB chat documents to [ChatEntriesCompanion] for Drift persistence.
class ChatMapper {
  ChatMapper._();

  /// Converts a CouchDB chat document JSON to a [ChatEntriesCompanion].
  ///
  /// Port of `ChatRepositoryImpl.insertChatsBatchInternal`.
  static ChatEntriesCompanion fromDoc(Map<String, dynamic> doc) {
    final id = doc['_id'] as String? ?? doc['id'] as String? ?? '';
    final rev = doc['_rev'] as String?;
    final createdDate = doc['createdDate'];
    final updatedDate = doc['updatedDate'];
    final conversations = doc['conversations'] as List<dynamic>?;

    return ChatEntriesCompanion(
      id: Value(id),
      docId: Value(id),
      rev: Value(rev),
      user: Value(doc['user'] as String?),
      aiProvider: Value(doc['aiProvider'] as String?),
      title: Value(doc['title'] as String?),
      createdDate: Value(createdDate?.toString()),
      updatedDate: Value(updatedDate?.toString()),
      conversations: Value(
        conversations != null ? jsonEncode(conversations) : null,
      ),
      lastUsed: Value(DateTime.now().millisecondsSinceEpoch),
      // This document came from the server, so it is by definition already
      // there. `is_uploaded` defaults to false, and nothing else sets it —
      // leaving it would make `getPending()` return every synced conversation
      // and post a duplicate of each on the next drain.
      isUploaded: const Value(true),
    );
  }

  /// Parses the embedded conversations JSON into a list of [ChatConversation].
  ///
  /// Port of `ChatRepositoryImpl.insertChatsBatchInternal` conversation parsing.
  static List<ChatConversation> parseConversations(String? conversationsJson) {
    if (conversationsJson == null || conversationsJson.isEmpty) {
      return [];
    }
    try {
      final decoded = jsonDecode(conversationsJson);
      if (decoded is List) {
        return decoded.map((e) {
          if (e is Map<String, dynamic>) {
            return ChatConversation(
              query: e['query'] as String?,
              response: e['response'] as String?,
            );
          }
          return const ChatConversation();
        }).toList();
      }
    } catch (_) {}
    return [];
  }

  /// Encodes a list of conversations to JSON for storage.
  static String encodeConversations(List<ChatConversation> conversations) {
    return jsonEncode(
      conversations
          .map(
            (c) => {
              if (c.query != null) 'query': c.query,
              if (c.response != null) 'response': c.response,
            },
          )
          .toList(),
    );
  }

  /// Builds the initial chat document for a new conversation.
  ///
  /// Port of `ChatRepositoryImpl.sendNewChatRequest` document building.
  static Map<String, dynamic> buildNewChatDoc({
    required String id,
    required String rev,
    required String user,
    required String query,
    required String response,
    required String aiProvider,
  }) {
    final now = DateTime.now().millisecondsSinceEpoch;
    return {
      '_id': id,
      if (rev.isNotEmpty) '_rev': rev,
      'user': user,
      'title': query,
      'aiProvider': aiProvider,
      'createdDate': now,
      'updatedDate': now,
      'conversations': [
        {'query': query, 'response': response},
      ],
    };
  }

  /// Builds the continuation chat document.
  ///
  /// Port of `ChatRepositoryImpl.sendContinueChatRequest`.
  static Map<String, dynamic> buildContinueChatDoc({
    required String id,
    required String rev,
    required String query,
    required String response,
    required List<ChatConversation> existingConversations,
  }) {
    final now = DateTime.now().millisecondsSinceEpoch;
    final newConversations = [
      ...existingConversations.map(
        (c) => {
          if (c.query != null) 'query': c.query,
          if (c.response != null) 'response': c.response,
        },
      ),
      {'query': query, 'response': response},
    ];
    return {
      '_id': id,
      '_rev': rev,
      'updatedDate': now,
      'conversations': newConversations,
    };
  }
}

/// Represents a single query-response pair in a chat conversation.
///
/// Port of `model/Conversation.kt`.
class ChatConversation {
  const ChatConversation({this.query, this.response});

  final String? query;
  final String? response;

  ChatConversation copyWith({String? query, String? response}) {
    return ChatConversation(
      query: query ?? this.query,
      response: response ?? this.response,
    );
  }
}

/// Represents an AI provider configuration.
///
/// Port of `model/ChatRequest.kt` AiProvider.
class AiProviderConfig {
  const AiProviderConfig({required this.name, required this.model});

  final String name;
  final String model;

  Map<String, dynamic> toJson() => {'name': name, 'model': model};

  static AiProviderConfig? fromJson(Map<String, dynamic>? json) {
    if (json == null) return null;
    final name = json['name'] as String?;
    final model = json['model'] as String?;
    if (name == null || model == null) return null;
    return AiProviderConfig(name: name, model: model);
  }
}
