import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import '../data/local/chat_mapper.dart';
import '../repository/chat_repository.dart';
import 'app_providers.dart';
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
  final history = ref.watch(chatHistoryProvider);

  return history.whenData((chats) {
    if (search.isEmpty) return chats;

    final normalizedSearch = _normalizeText(search);
    return chats.where((chat) {
      final title = _normalizeText(chat.title ?? '');
      return title.contains(normalizedSearch) ||
          title.startsWith(normalizedSearch);
    }).toList();
  });
});

/// Normalizes text for search by removing diacritics and lowercasing.
String _normalizeText(String text) {
  return text
      .toLowerCase()
      .replaceAll(RegExp(r'\p{InCombiningDiacriticalMarks}+'), '');
}

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

    final chatRow = rows.where((r) => r.id == chatId || r.docId == chatId).firstOrNull;
    if (chatRow == null) return;

    final conversations = ChatMapper.parseConversations(chatRow.conversations);
    final messages = conversations.map((c) {
      final msgs = <ChatMessage>[];
      if (c.query != null) {
        msgs.add(ChatMessage(content: c.query!, isUser: true));
      }
      if (c.response != null) {
        msgs.add(ChatMessage(content: c.response!, isUser: false));
      }
      return msgs;
    }).expand((e) => e).toList();

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
      messages: [...state.messages, ChatMessage(content: message, isUser: true)],
      isLoading: true,
      error: null,
    );

    ChatResult result;
    if (currentState.id.isEmpty) {
      // New chat
      result = await repo.sendNewChatRequest(
        query: message,
        user: session.name ?? '',
        aiProvider: currentState.aiProvider ??
            const AiProviderConfig(name: 'default', model: ''),
      );
    } else {
      // Continue existing chat
      result = await repo.sendNewChatRequest(
        query: message,
        user: session.name ?? '',
        aiProvider: currentState.aiProvider ??
            const AiProviderConfig(name: 'default', model: ''),
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
        state = state.copyWith(
          isLoading: false,
          error: message,
        );
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
