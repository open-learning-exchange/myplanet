import 'dart:convert';

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

/// The `viewIn` section a chat share is addressed to.
///
/// These are wire values, not labels. `ChatHistoryAdapter` passes
/// `context.getString(R.string.teams)` / `R.string.enterprises` /
/// `R.string.community` straight into [ChatSharePayload.buildShareMap] as the
/// `section`, so on an Android device running in French the document reaches
/// CouchDB with a French `viewIn[].section` — and
/// `VoicesRepository.isVisibleToUser` matches `section == "community"`, so a
/// community share made in a non-English locale is invisible in the community
/// feed of both apps. The port writes the stable literals instead, which is
/// what every *other* writer on both sides already does
/// (`VoicesFragment` writes `"community"`, `TeamsVoicesFragment` writes
/// `"teams"`, and the port's `createTeamPost` writes `"teams"`).
///
/// English is the only locale where this is observable as a difference at all,
/// and there only for enterprises: `R.string.enterprises` is `"Enterprises"`
/// where this writes `"enterprises"`. Nothing on either side reads that value —
/// the only `section` comparison anywhere is against `"community"`, and it is
/// case-insensitive.
abstract final class ChatShareSection {
  static const String community = 'community';
  static const String teams = 'teams';
  static const String enterprises = 'enterprises';
}

/// The share destination a chat is addressed to — a team, an enterprise, or
/// the planet's community pseudo-team.
///
/// Port of the three fields of `model/TeamSummary.kt` that the share path
/// reads: `_id` becomes `viewInId`, `teamType` becomes `messageType`, and
/// `teamPlanetCode` becomes `messagePlanetCode`. [name] is the dialog label;
/// note that it does *not* reach the payload — `News.getViewInJson` writes a
/// `name` key read from `map["name"]`, which `buildShareMap` never sets, so
/// the entry's name is null in the Kotlin too.
class ChatShareTarget {
  const ChatShareTarget({
    required this.id,
    required this.name,
    this.teamType,
    this.teamPlanetCode,
  });

  final String id;
  final String name;
  final String? teamType;

  /// Null for every target the port builds today: the `teams` Drift table has
  /// no `teamPlanetCode` column, so there is nothing to read it from. The
  /// Kotlin sends `""` for a team document that omits the field and for the
  /// synthesized community target, which is the same value this produces —
  /// see `PHASE_140_NOTES.md` § "Reported, not fixed".
  final String? teamPlanetCode;
}

/// Port of `model/ChatSharePayload.kt` — the `HashMap` a shared chat is
/// posted as.
///
/// Pure and top-level so a test can pin the wire shape without a repository,
/// and so the caller need not transitively watch `chatRepositoryProvider`
/// (and through it `planetPrefsProvider`, unimplemented in the widget-test
/// harness).
///
/// Two shapes are load-bearing and easy to lose:
///
/// - **Every value is a string**, the nested object included. `createdDate`
///   and `updatedDate` are the millis *stringified*, and `conversations` is a
///   JSON array encoded into a string and stored under a key of the outer
///   JSON — which is why `News.createNews` re-parses it out of a string
///   primitive rather than reading an array.
/// - **A null title shares as `""`, not `"null"`.** Upstream `7167684` moved
///   this out of the adapter, where it had been `"${chatHistory.title}".trim()`
///   — a Kotlin string template of null renders the four characters `null`, so
///   before that commit an untitled chat was shared under the title `null`.
///   The current behaviour is what is ported.
Map<String, String> buildChatShareMap({
  required ChatRow chat,
  required String note,
  required ChatShareTarget? target,
  required String section,
  required int nowMillis,
}) {
  final conversations = ChatMapper.parseConversations(chat.conversations)
      .map((c) => {'query': c.query ?? '', 'response': c.response ?? ''})
      .toList(growable: false);

  final news = <String, String>{
    '_id': chat.docId ?? '',
    '_rev': chat.rev ?? '',
    'title': (chat.title ?? '').trim(),
    'user': chat.user ?? '',
    'aiProvider': chat.aiProvider ?? '',
    'createdDate': '$nowMillis',
    'updatedDate': '$nowMillis',
    'conversations': jsonEncode(conversations),
  };

  return <String, String>{
    'message': note,
    'viewInId': target?.id ?? '',
    'viewInSection': section,
    'messageType': target?.teamType ?? '',
    'messagePlanetCode': target?.teamPlanetCode ?? '',
    'chat': 'true',
    'news': jsonEncode(news),
  };
}
