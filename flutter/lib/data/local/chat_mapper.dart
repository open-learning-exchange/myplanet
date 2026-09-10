import 'dart:convert';

import 'package:drift/drift.dart';

import 'app_database.dart';

/// Port of `model/ChatHistory.kt`, `model/Conversation.kt`,
/// `model/ChatRequest.kt`, and the JSON-mapping portions of
/// `ChatRepositoryImpl`.
///
/// Maps CouchDB chat documents to [ChatEntriesCompanion] for Drift persistence.
/// A `String?` from a server value that is not guaranteed to be a string.
///
/// **Three of planet.learning's 1,630 `chat_history` documents carry `user`
/// as the whole CouchDB user document** — `{"_id":
/// "org.couchdb.user:learning", "name": "learning", "roles": [...], ...}` —
/// and a raw `as String?` on one of them threw
/// `type '_Map<String, dynamic>' is not a subtype of type 'String?'`,
/// failing the entire chat sync. Three documents took 1,630 down with them.
///
/// Kotlin does not crash: `JsonUtils.getString("user", json)`
/// (`ChatRepositoryImpl:260`) returns `""` for anything that is not a string
/// primitive. So the crash is the port being stricter than what it ports,
/// exactly like the `Content-Type` case in `PlanetApi`.
///
/// **Deliberate divergence, one step better than Kotlin:** where the value
/// is an object carrying a `name`, that name is used. These three documents
/// mean `learning`, the same value the other 972 store as a plain string, so
/// Kotlin's `""` throws away something unambiguous. Anything else becomes
/// null rather than `Map.toString()` — `json_utils.dart` documents why that
/// literal is worse than nothing.
String? _stringOrNull(Object? value) {
  if (value is String) return value;
  if (value is Map<String, dynamic>) {
    final name = value['name'];
    return name is String ? name : null;
  }
  return null;
}

class ChatMapper {
  ChatMapper._();

  /// Converts a CouchDB chat document JSON to a [ChatEntriesCompanion].
  ///
  /// Port of `ChatRepositoryImpl.insertChatsBatchInternal`.
  static ChatEntriesCompanion fromDoc(Map<String, dynamic> doc) {
    final id = _stringOrNull(doc['_id']) ?? _stringOrNull(doc['id']) ?? '';
    final rev = _stringOrNull(doc['_rev']);
    final createdDate = doc['createdDate'];
    final updatedDate = doc['updatedDate'];
    final conversations = doc['conversations'] as List<dynamic>?;

    return ChatEntriesCompanion(
      id: Value(id),
      docId: Value(id),
      rev: Value(rev),
      user: Value(_stringOrNull(doc['user'])),
      aiProvider: Value(_stringOrNull(doc['aiProvider'])),
      title: Value(_stringOrNull(doc['title'])),
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
              query: _stringOrNull(e['query']),
              response: _stringOrNull(e['response']),
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
    final name = _stringOrNull(json['name']);
    final model = _stringOrNull(json['model']);
    if (name == null || model == null) return null;
    return AiProviderConfig(name: name, model: model);
  }
}
