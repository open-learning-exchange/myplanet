import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Chat-specific upload using the outbox for retry support.
///
/// This is the push direction for chat_history: a message typed offline is
/// queued and sent when connectivity returns, rather than failing outright
/// as the direct API call did before this uploader existed.
class ChatUploader {
  ChatUploader(this._api, this._chatDao, this._outbox);

  /// The `uploadType` these operations carry in the outbox.
  static const String type = 'chat';

  final PlanetApi _api;
  final ChatDao _chatDao;
  final OutboxRepository _outbox;

  /// Credential-free: this string is persisted in `outbox.endpoint`.
  /// The PIN travels as the `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/chat';

  /// Queues every not-yet-uploaded chat for retry.
  ///
  /// Safe to call repeatedly: [OutboxRepository.enqueue] keys on
  /// `(uploadType, itemId)`, so a chat already queued has its payload
  /// refreshed rather than being posted twice.
  Future<int> queuePending({
    required ServerConfig config,
    required String userId,
  }) async {
    final endpoint = endpointFor(config);
    final pending = await _chatDao.getPending();
    // Filter to chats belonging to this user
    final userChats = pending.where((row) => row.user == userId);
    for (final row in userChats) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpoint,
        payload: _serializeChat(row),
        userId: userId,
      );
    }
    return userChats.length;
  }

  /// The [OutboxHandler] for [type].
  ///
  /// Sends a new chat to the server. The chat document structure differs from
  /// a continuation: new chats have no `_id`/`_rev`, while continuations do.
  /// This handler always sends as a new chat; continuations use the same
  /// endpoint but with different payload structure handled by the server.
  OutboxHandler get handler => (row, payload, authHeader) async {
    // Send the chat request
    final result = await _api.sendJsonObject(
      row.endpoint,
      method: 'POST',
      body: payload,
      authHeader: authHeader,
    );

    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final status = data['status'] as String?;
      if (status != 'Success') {
        return NetworkError<Map<String, dynamic>>(
          null,
          data['message'] as String? ?? 'Chat upload failed',
        );
      }

      final couchResponse = data['couchdb'] as Map<String, dynamic>?;
      final couchId = couchResponse?['id'] as String?;
      final rev = couchResponse?['rev'] as String?;

      if (couchId == null || couchId.isEmpty) {
        return NetworkError<Map<String, dynamic>>(
          null,
          'Chat was saved without a document id',
        );
      }

      // Update the local row with server-assigned id and rev
      await _chatDao.markUploaded(row.itemId, couchId, rev ?? '');
    }
    return result;
  };

  /// Serializes a chat row for upload.
  Map<String, dynamic> _serializeChat(ChatRow row) {
    return {
      'data': {
        'user': row.user,
        'content': row.conversations,
        'aiProvider': row.aiProvider != null
            ? {'name': row.aiProvider, 'model': ''}
            : null,
        // Include docId/rev if this is a continuation
        if (row.docId != null) '_id': row.docId,
        if (row.rev != null) '_rev': row.rev,
      },
      'save': true,
    };
  }

  static String authHeaderFor(ServerConfig config) =>
      UrlUtils.authHeader(config);
}
