import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
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
  Future<ChatResult> sendContinueChatRequest({
    required String query,
    required String user,
    required AiProviderConfig aiProvider,
    required String id,
    required String rev,
  });

  /// Gets chat history for a user from the local database.
  Future<List<ChatRow>> getChatHistoryForUser(String? userName);

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
