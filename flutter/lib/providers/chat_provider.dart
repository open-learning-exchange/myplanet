import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import '../data/local/chat_mapper.dart';
import '../repository/chat_repository.dart';
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
  final session = ref.watch(sessionProvider).valueOrNull;
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
    final session = ref.read(sessionProvider).valueOrNull;
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
    final user = _ref.read(sessionProvider).valueOrNull;
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
