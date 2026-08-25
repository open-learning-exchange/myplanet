import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/text_utils.dart';
import '../data/local/app_database.dart';
import '../data/local/chat_mapper.dart';

/// Port of `repository/ChatRepository.kt` interface.
abstract class ChatRepository {
  /// Fetches available AI providers from the server.
  Future<Map<String, bool>?> fetchAiProviders();

  /// Sends a new chat request to the AI.
  Future<ChatResult> sendNewChatRequest({
    required String query,
    required String user,
    required AiProviderConfig aiProvider,
  });

  /// Sends a continuation message to an existing chat.
  /// Stores a message the server did not accept, so the outbox can retry it.
  ///
  /// Returns the local row id. Until this existed a failed send dropped the
  /// message on the floor and `ChatDao.getPending()` was always empty, which
  /// left `ChatUploader` with nothing to do — the offline case it was written
  /// for could not arise.
  Future<String> savePendingChat({
    required String user,
    required String query,
    required AiProviderConfig aiProvider,
    String? existingId,
    String? existingRev,
  });

  Future<ChatResult> sendContinueChatRequest({
    required String query,
    required String user,
    required AiProviderConfig aiProvider,
    required String id,
    required String rev,
  });

  /// Gets chat history for a user from the local database.
  Future<List<ChatRow>> getChatHistoryForUser(String? userName);

  /// Searches [chats] for [query] under the given [mode].
  ///
  /// Port of `ChatRepositoryImpl.searchChats`. Title search matches the
  /// conversation's first query (or its stored title when it has none);
  /// question/response search walks every turn. Matches are ranked so a
  /// prefix hit outranks a substring hit, and a hit in the first turn
  /// (which is the title) outranks one in a later turn. Both the query and
  /// the chat text are normalized — lowercased and stripped of diacritics —
  /// so an accented search finds its unaccented match.
  ///
  /// The pure implementation lives in the top-level [searchChatsForMode];
  /// providers call that directly to avoid transitively watching the repo
  /// (and `planetPrefsProvider`, which is unimplemented in tests).
  List<ChatRow> searchChats(
    String query,
    ChatSearchMode mode,
    List<ChatRow> chats,
  );

  /// Gets the latest revision for a chat document.
  Future<String?> getLatestRev(String id);

  /// Inserts chat history from CouchDB sync.
  Future<void> insertChatHistoryList(List<Map<String, dynamic>> docs);

  /// Inserts chat history from sync documents (wrapped in 'doc').
  Future<void> insertChatHistoryFromSync(List<Map<String, dynamic>> docs);

  /// Syncs chat history from CouchDB.
  ///
  /// Counts with `?limit=0`, then walks pages, upserting each chat document.
  /// Port of the `chat_history` table pull in `TransactionSyncManager.kt`.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  });
}

/// Result of a chat API request.
sealed class ChatResult {
  const ChatResult();
}

final class ChatSuccess extends ChatResult {
  const ChatSuccess({
    required this.response,
    required this.id,
    required this.rev,
  });

  final String response;
  final String id;
  final String rev;
}

final class ChatError extends ChatResult {
  const ChatError(this.message);
  final String message;
}

/// Which field a chat search inspects.
///
/// Port of the Kotlin `ChatSearchMode`. `title` matches the conversation's
/// first query (the title shown in the list); `question` and `response` walk
/// every turn's query or response respectively.
enum ChatSearchMode { title, question, response }

/// Pure, dependency-free chat search. The provider calls this directly so a
/// filter rebuild does not transitively watch `chatRepositoryProvider` (and
/// through it `planetPrefsProvider`, which is `UnimplementedError` in tests).
///
/// Port of `ChatRepositoryImpl.searchChats`/`sortChats`. Title search matches
/// the conversation's first query (or its stored title when it has none);
/// question/response search walks every turn. Matches are ranked so a prefix
/// hit outranks a substring hit, and a hit in the first turn (the title)
/// outranks one in a later turn. Both the query and the chat text are
/// normalized — lowercased and stripped of diacritics — so an accented search
/// finds its unaccented match.
List<ChatRow> searchChatsForMode(
  String query,
  ChatSearchMode mode,
  List<ChatRow> chats,
) {
  final precomputed = _buildPrecomputedChats(chats);
  return mode == ChatSearchMode.title
      ? _searchByTitle(query, precomputed)
      : _fullConvoSearch(
          s: query,
          isQuestion: mode == ChatSearchMode.question,
          precomputed: precomputed,
        );
}

/// Sorts chats by the newest of their created/updated dates, descending.
///
/// Port of `ChatRepositoryImpl.sortChats`. The DAO returns rows ordered by id,
/// which is a CouchDB uuid rather than a timestamp, so a freshly updated
/// older conversation would otherwise sit below a stale newer one.
List<ChatRow> sortChatsByRecency(List<ChatRow> chats) {
  final copy = [...chats];
  copy.sort((a, b) {
    final aTime = _maxDate(a.createdDate, a.updatedDate);
    final bTime = _maxDate(b.createdDate, b.updatedDate);
    return bTime.compareTo(aTime);
  });
  return copy;
}

int _maxDate(String? created, String? updated) {
  final c = int.tryParse(created ?? '') ?? 0;
  final u = int.tryParse(updated ?? '') ?? 0;
  return c > u ? c : u;
}

/// Per-chat normalized fields, precomputed once so the search loops do not
/// re-normalize the same strings for every query term. Port of the Kotlin
/// `PrecomputedChat`.
class _PrecomputedChat {
  _PrecomputedChat({
    required this.chat,
    required this.normalizedTitle,
    required this.normalizedQueries,
    required this.normalizedResponses,
  });

  final ChatRow chat;
  final String normalizedTitle;
  final List<String> normalizedQueries;
  final List<String> normalizedResponses;
}

List<_PrecomputedChat> _buildPrecomputedChats(List<ChatRow> chats) {
  return chats.map((chat) {
    final conversations = ChatMapper.parseConversations(chat.conversations);
    final title = conversations.isNotEmpty
        ? normalizeText(conversations.first.query ?? '')
        : normalizeText(chat.title ?? '');
    final queries = conversations.map((c) => normalizeText(c.query ?? ''));
    final responses = conversations.map((c) => normalizeText(c.response ?? ''));
    return _PrecomputedChat(
      chat: chat,
      normalizedTitle: title,
      normalizedQueries: queries.toList(),
      normalizedResponses: responses.toList(),
    );
  }).toList();
}

List<ChatRow> _searchByTitle(String s, List<_PrecomputedChat> precomputed) {
  final normalizedQuery = normalizeText(s);
  final queryParts = s.split(' ').where((p) => p.isNotEmpty).toList();
  final startsWith = <ChatRow>[];
  final contains = <ChatRow>[];
  for (final pChat in precomputed) {
    final title = pChat.normalizedTitle;
    if (title.isEmpty) continue;
    if (title.startsWith(normalizedQuery)) {
      startsWith.add(pChat.chat);
    } else if (queryParts.every(
      (part) => title.contains(normalizeText(part)),
    )) {
      contains.add(pChat.chat);
    }
  }
  return [...startsWith, ...contains];
}

List<ChatRow> _fullConvoSearch({
  required String s,
  required bool isQuestion,
  required List<_PrecomputedChat> precomputed,
}) {
  final normalizedQuery = normalizeText(s);
  final queryParts = s.split(' ').where((p) => p.isNotEmpty).toList();
  final inTitleStartQuery = <ChatRow>[];
  final inTitleContainsQuery = <ChatRow>[];
  final startsWith = <ChatRow>[];
  final contains = <ChatRow>[];
  for (final pChat in precomputed) {
    final conversations = isQuestion
        ? pChat.normalizedQueries
        : pChat.normalizedResponses;
    for (var i = 0; i < conversations.length; i++) {
      final conversation = conversations[i];
      if (conversation.isEmpty) continue;
      if (conversation.startsWith(normalizedQuery)) {
        if (i == 0) {
          inTitleStartQuery.add(pChat.chat);
        } else {
          startsWith.add(pChat.chat);
        }
        break;
      } else if (queryParts.every(
        (part) => conversation.contains(normalizeText(part)),
      )) {
        if (i == 0) {
          inTitleContainsQuery.add(pChat.chat);
        } else {
          contains.add(pChat.chat);
        }
        break;
      }
    }
  }
  return [
    ...inTitleStartQuery,
    ...inTitleContainsQuery,
    ...startsWith,
    ...contains,
  ];
}
