import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';

import '../data/local/app_database.dart';
import '../data/local/chat_mapper.dart';
import '../repository/chat_repository.dart';
import '../repository/teams_repository.dart';
import '../repository/voices_repository.dart';
import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import 'app_providers.dart';
import 'sync_state.dart';
import 'session_provider.dart';

/// Provider for available AI providers.
final aiProvidersProvider = FutureProvider<Map<String, bool>?>((ref) async {
  final repo = ref.watch(chatRepositoryProvider);
  return repo.fetchAiProviders();
});

/// Provider for chat history for the current user.
final chatHistoryProvider = FutureProvider<List<ChatRow>>((ref) async {
  final session = ref.watch(sessionProvider).value;
  final repo = ref.watch(chatRepositoryProvider);
  return repo.getChatHistoryForUser(session?.name);
});

/// Provider for filtered chat history.
final chatSearchQueryProvider = StateProvider<String>((ref) => '');

/// Provider for filtered chat history based on search.
final filteredChatHistoryProvider = Provider<AsyncValue<List<ChatRow>>>((ref) {
  final search = ref.watch(chatSearchQueryProvider);
  final mode = ref.watch(chatSearchModeProvider);
  final fullSearch = ref.watch(chatFullSearchProvider);
  final history = ref.watch(chatHistoryProvider);

  return history.whenData((chats) {
    if (search.isEmpty) return chats;

    final effectiveMode = fullSearch ? mode : ChatSearchMode.title;
    return searchChatsForMode(search, effectiveMode, chats);
  });
});

/// Current chat search field (only meaningful when full search is on).
final chatSearchModeProvider = StateProvider<ChatSearchMode>(
  (ref) => ChatSearchMode.question,
);

/// Whether full conversation search (across every question/response) is on.
/// Off means title-only search, matching the Kotlin's `fullSearch` checkbox.
final chatFullSearchProvider = StateProvider<bool>((ref) => false);

/// State for a chat conversation.
class ChatConversationState {
  const ChatConversationState({
    this.messages = const [],
    this.id = '',
    this.rev = '',
    this.aiProvider,
    this.isLoading = false,
    this.error,
  });

  final List<ChatMessage> messages;
  final String id;
  final String rev;
  final AiProviderConfig? aiProvider;
  final bool isLoading;
  final String? error;

  ChatConversationState copyWith({
    List<ChatMessage>? messages,
    String? id,
    String? rev,
    AiProviderConfig? aiProvider,
    bool? isLoading,
    String? error,
  }) {
    return ChatConversationState(
      messages: messages ?? this.messages,
      id: id ?? this.id,
      rev: rev ?? this.rev,
      aiProvider: aiProvider ?? this.aiProvider,
      isLoading: isLoading ?? this.isLoading,
      error: error,
    );
  }
}

/// A single message in the chat conversation.
class ChatMessage {
  const ChatMessage({
    required this.content,
    required this.isUser,
    this.id = '',
  });

  final String content;
  final bool isUser;
  final String id;
}

/// Notifier for managing chat conversation state.
class ChatConversationNotifier extends Notifier<ChatConversationState> {
  @override
  ChatConversationState build() => const ChatConversationState();

  /// Loads a chat by its ID.
  Future<void> loadChat(String chatId) async {
    final repo = ref.read(chatRepositoryProvider);
    final rows = await repo.getChatHistoryForUser(null);

    final chatRow = rows
        .where((r) => r.id == chatId || r.docId == chatId)
        .firstOrNull;
    if (chatRow == null) return;

    final conversations = ChatMapper.parseConversations(chatRow.conversations);
    final messages = conversations
        .map((c) {
          final msgs = <ChatMessage>[];
          if (c.query != null) {
            msgs.add(ChatMessage(content: c.query!, isUser: true));
          }
          if (c.response != null) {
            msgs.add(ChatMessage(content: c.response!, isUser: false));
          }
          return msgs;
        })
        .expand((e) => e)
        .toList();

    final aiProvider = AiProviderConfig.fromJson(
      chatRow.aiProvider != null
          ? {'name': chatRow.aiProvider, 'model': ''}
          : null,
    );

    state = state.copyWith(
      messages: messages,
      id: chatRow.docId ?? chatRow.id,
      rev: chatRow.rev ?? '',
      aiProvider: aiProvider,
    );
  }

  /// Starts a new chat.
  void startNewChat() {
    state = const ChatConversationState();
  }

  /// Sends a message.
  Future<void> sendMessage(String message) async {
    final session = ref.read(sessionProvider).value;
    if (session == null || message.trim().isEmpty) return;

    final repo = ref.read(chatRepositoryProvider);
    final currentState = state;

    // Add user message immediately
    state = state.copyWith(
      messages: [
        ...state.messages,
        ChatMessage(content: message, isUser: true),
      ],
      isLoading: true,
      error: null,
    );

    ChatResult result;
    if (currentState.id.isEmpty) {
      // New chat
      result = await repo.sendNewChatRequest(
        query: message,
        user: session.name ?? '',
        aiProvider:
            currentState.aiProvider ??
            const AiProviderConfig(name: 'default', model: ''),
      );
    } else {
      // Continue existing chat. This branch called `sendNewChatRequest` too,
      // so every follow-up message opened a fresh conversation instead of
      // appending to the one on screen.
      result = await repo.sendContinueChatRequest(
        query: message,
        user: session.name ?? '',
        aiProvider:
            currentState.aiProvider ??
            const AiProviderConfig(name: 'default', model: ''),
        id: currentState.id,
        rev: currentState.rev,
      );
    }

    switch (result) {
      case ChatSuccess(response: final response, id: final id, rev: final rev):
        state = state.copyWith(
          messages: [
            ...state.messages,
            ChatMessage(content: response, isUser: false),
          ],
          id: id,
          rev: rev,
          isLoading: false,
        );
      case ChatError(message: final message):
        // Keep the message rather than dropping it, and hand it to the outbox.
        // `ChatUploader` was registered on the drain with nothing ever queuing
        // for it, because a failed send left no pending row to find.
        await repo.savePendingChat(
          user: session.name ?? '',
          query: message,
          aiProvider:
              currentState.aiProvider ??
              const AiProviderConfig(name: 'default', model: ''),
          existingId: currentState.id.isEmpty ? null : currentState.id,
          existingRev: currentState.rev.isEmpty ? null : currentState.rev,
        );
        await ref.read(chatQueueProvider).queuePending();
        state = state.copyWith(isLoading: false, error: message);
    }
  }

  /// Sets the AI provider for the conversation.
  void setAiProvider(AiProviderConfig provider) {
    state = state.copyWith(aiProvider: provider);
  }
}

final chatConversationProvider =
    NotifierProvider<ChatConversationNotifier, ChatConversationState>(
      ChatConversationNotifier.new,
    );

/// Pull of the `chat_history` database, giving `ChatRepository.sync` its first
/// caller. `insertChatHistoryFromSync` had been written and left unreachable
/// for long enough that the preserved-table list cited its absence as the
/// reason chat could not be dropped on a schema upgrade.
/// Hands every chat the server has not acknowledged to the durable outbox.
class ChatQueue {
  const ChatQueue(this._ref);

  final Ref _ref;

  Future<int> queuePending() async {
    final config = _ref.read(serverConfigProvider);
    final user = _ref.read(sessionProvider).value;
    if (config == null || user == null) return 0;
    return _ref
        .read(chatUploaderProvider)
        .queuePending(config: config, userId: user.name ?? '');
  }
}

final chatQueueProvider = Provider<ChatQueue>(ChatQueue.new);

class ChatSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) => ref
      .read(chatRepositoryProvider)
      .sync(config: config, onProgress: onProgress);
}

final chatSyncProvider = NotifierProvider<ChatSyncNotifier, SyncUiState>(
  ChatSyncNotifier.new,
);

/// Port of `model/ChatShareTargets.kt` — the three destination groups the
/// share dialog offers.
class ChatShareTargets {
  const ChatShareTargets({
    this.community,
    this.teams = const [],
    this.enterprises = const [],
  });

  final ChatShareTarget? community;
  final List<ChatShareTarget> teams;
  final List<ChatShareTarget> enterprises;
}

/// Port of `ChatViewModel.loadShareTargets`.
///
/// Teams and enterprises are the *root*, non-archived documents of each type
/// the signed-in user is a member of; a signed-out session sees the whole
/// catalog, which is the Kotlin's `userId.isNullOrBlank()` branch. The
/// community is a pseudo-team whose id is `"<communityName>@<parentCode>"`,
/// taken from the cached team document when the planet has one and
/// synthesized from the name when it does not — so the community entry is
/// offered even on a device that has never synced a community team.
///
/// The session is awaited rather than read: this provider never watches
/// `sessionProvider` for its value, and `value` is null until something
/// else resolves it.
final chatShareTargetsProvider = FutureProvider<ChatShareTargets>((ref) async {
  final session = await ref.watch(sessionProvider.future);
  final repo = ref.watch(teamsRepositoryProvider);
  final prefs = ref.watch(planetPrefsProvider);

  // `currentUser?._id` — the CouchDB id, not the local row id. The two
  // coincide for a synced account, but a member registered on this device
  // keeps a locally minted `'<millis>'` id while their membership documents
  // carry `org.couchdb.user:<name>`, and this is a *hard* filter: the row id
  // would offer that user no teams at all and show them "Please join a Team".
  // The write path below already uses `couchId ?? id`.
  final userId = session?.couchId ?? session?.id;
  final teams = await _shareableTargets(repo, 'team', userId);
  final enterprises = await _shareableTargets(repo, 'enterprise', userId);

  // `sharedPrefManager.getCommunityName()`, which is not a name: Kotlin writes
  // the *configuration document's `code`* into it, once, from
  // `SyncConfigurationCoordinator`. The port persists that same value as
  // `ServerConfig.code`, and **nothing in `lib/` ever calls
  // `PlanetPrefs.setCommunityName`** — so reading only the pref left
  // `communityName` permanently empty, `community` permanently null, and the
  // whole community branch of the share dialog unreachable in the running app,
  // green tests and all, because the pref's only writer was a test. The pref
  // still wins when something does set it, so the two agree if it is wired.
  final config = ref.watch(serverConfigProvider);
  final prefName = prefs.communityName;
  final communityName = prefName.isNotEmpty ? prefName : (config?.code ?? '');
  final parentCode = config?.parentCode ?? '';
  ChatShareTarget? community;
  if (communityName.trim().isNotEmpty && parentCode.trim().isNotEmpty) {
    final id = '$communityName@$parentCode';
    final row = await repo.getById(id);
    community = row == null
        ? ChatShareTarget(id: id, name: communityName)
        : _targetOf(row);
  }

  return ChatShareTargets(
    community: community,
    teams: teams,
    enterprises: enterprises,
  );
});

/// Port of `getShareableTeams` / `getShareableEnterpriseSummaries`, which
/// differ only in the `type` they query.
///
/// `watchCatalog` is `getRootTeamsByType`: real team documents of that type
/// that are not archived. A blank user id skips the membership filter, and a
/// user with no memberships gets an empty list rather than the catalog.
Future<List<ChatShareTarget>> _shareableTargets(
  TeamsRepository repo,
  String type,
  String? userId,
) async {
  final rows = await repo.watchCatalog(type: type).first;
  if (userId == null || userId.trim().isEmpty) {
    return rows.map(_targetOf).toList(growable: false);
  }
  final statuses = await repo.memberStatuses(userId, rows.map((r) => r.id));
  return rows
      .where((row) => statuses[row.id]?.isMember ?? false)
      .map(_targetOf)
      .toList(growable: false);
}

/// Port of `MyTeam.toSummary()`, narrowed to the fields the share reads.
ChatShareTarget _targetOf(TeamRow row) =>
    ChatShareTarget(id: row.id, name: row.name ?? '', teamType: row.teamType);

/// The destinations each already-shared chat has reached, keyed by the chat's
/// CouchDB `_id`.
///
/// Port of `ChatViewModel.loadChatHistoryScreenData`'s
/// `chatRepository.extractSharedViewInIds(newsMessages)` over
/// `voicesRepository.getPlanetNewsMessages(currentUser?.planetCode)`.
///
/// Read it through [ChatShareActions.destinations], which recomputes it:
/// Kotlin re-runs `getPlanetNewsMessages` + `extractSharedViewInIds` on every
/// `refreshChatSignal` and on screen re-creation, so a value cached for the
/// process lifetime would keep offering a destination that a sync from
/// another device had already filled. (`isAlreadyShared` still refuses the
/// duplicate at write time, but only after the user has walked the whole
/// dialog flow.)
final sharedChatDestinationsProvider = FutureProvider<Map<String, Set<String>>>(
  (ref) async {
    final session = await ref.watch(sessionProvider.future);
    final rows = await ref
        .watch(voicesRepositoryProvider)
        .planetNewsMessages(session?.planetCode);
    return VoicesRepository.extractSharedViewInIds(rows);
  },
);

/// What a share attempt did, so the screen can tell the user.
enum ChatShareOutcome {
  shared,

  /// `ShareChatResult.AlreadyShared` — the chat is already in this
  /// destination's feed.
  alreadyShared,

  /// The share was not attempted: no signed-in user, no destination, or a
  /// chat the server has never seen. `ChatHistoryFragment` gates on
  /// `chatId.isNotEmpty() && viewInId.isNotEmpty()` and drops the tap
  /// silently; the port says so instead.
  unavailable,
}

/// The write path for a chat share, keeping "build the payload", "write the
/// voices row" and "queue it for upload" together — the shape `VoicesActions`
/// uses.
///
/// **The delivery guarantee is not in this class**, and an earlier revision of
/// this comment said it was. [share] skips the enqueue entirely when
/// `serverConfigProvider` is null and still reports
/// [ChatShareOutcome.shared] — the claim was withdrawn in `PHASE_140_NOTES.md`
/// item 4, when nothing swept the row afterwards either.
///
/// What makes that report honest today is elsewhere: `createFromShareMap`
/// leaves the row with no `_id`, so `VoicesRepository.pendingUploads` finds it,
/// and Phase 144's voices sweep picks it up on the first pass that has a server
/// config — `DashboardSyncNotifier.queuePendingVoices` in the foreground,
/// `sweepPendingVoices` from `drainOutbox` when the app is not running. That
/// is the port of Kotlin's `uploadNews()`, which is likewise the only thing
/// that ever sends a composed voice. So a shared chat does not sit undelivered;
/// the reason is the sweep, not the write path here.
class ChatShareActions {
  ChatShareActions(this.ref);

  final Ref ref;

  /// The already-shared map, recomputed. See [sharedChatDestinationsProvider]
  /// for why the cached value is not good enough to open a dialog on.
  /// `refresh`, not `invalidate` then `read`: `invalidate` only schedules the
  /// rebuild, so a read in the same microtask still resolves the stale future.
  Future<Map<String, Set<String>>> destinations() =>
      ref.refresh(sharedChatDestinationsProvider.future);

  Future<ChatShareOutcome> share({
    required ChatRow chat,
    required ChatShareTarget? target,
    required String section,
    required String note,
  }) async {
    final UserRow? user;
    try {
      user = await ref.read(sessionProvider.future);
    } catch (_) {
      return ChatShareOutcome.unavailable;
    }
    // `chat._id`, not the local row id: the payload's `newsId` is what
    // `isAlreadyShared` and the shared-destination map are both keyed by, and
    // a chat the server has never seen has nothing to key on.
    final chatId = chat.docId ?? '';
    final viewInId = target?.id ?? '';
    if (user == null || chatId.isEmpty || viewInId.isEmpty) {
      return ChatShareOutcome.unavailable;
    }

    final voices = ref.read(voicesRepositoryProvider);
    if (await voices.isAlreadyShared(chatId, viewInId)) {
      return ChatShareOutcome.alreadyShared;
    }

    await voices.createFromShareMap(
      payload: buildChatShareMap(
        chat: chat,
        note: note,
        target: target,
        section: section,
        nowMillis: DateTime.now().millisecondsSinceEpoch,
      ),
      userId: user.couchId ?? user.id,
      userName: user.name ?? '',
      userJson: VoicesRepository.authorJson(user),
      planetCode: user.planetCode,
      parentCode: user.parentCode,
    );

    final config = ref.read(serverConfigProvider);
    if (config != null) {
      await ref
          .read(voicesUploaderProvider)
          .queuePending(config: config, userId: user.id);
    }
    ref.invalidate(sharedChatDestinationsProvider);
    return ChatShareOutcome.shared;
  }
}

final chatShareActionsProvider = Provider<ChatShareActions>(
  ChatShareActions.new,
);
