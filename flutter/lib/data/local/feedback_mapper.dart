import 'dart:convert';
import 'dart:math';

import 'package:drift/drift.dart';

import 'app_database.dart';

/// Port of `model/Feedback.kt` and `model/FeedbackReply.kt`.
///
/// Maps CouchDB feedback documents to [FeedbackEntriesCompanion] for Drift persistence.
class FeedbackMapper {
  FeedbackMapper._();

  /// Converts a CouchDB feedback document JSON to a [FeedbackEntriesCompanion].
  ///
  /// Port of `FeedbackRepositoryImpl.mapToFeedback`. When the stored row has a
  /// reply the server has not confirmed (`isUploaded == false`), the local
  /// `messages` are kept and the row stays pending — a sync used to overwrite
  /// the thread with the server's copy, silently destroying the reply the
  /// uploader was about to send.
  static FeedbackEntriesCompanion fromDoc(
    Map<String, dynamic> doc, [
    FeedbackRow? existing,
  ]) {
    final id = doc['_id'] as String? ?? doc['id'] as String? ?? '';
    final rev = doc['_rev'] as String?;
    final hasPendingLocalReply = existing != null && !existing.isUploaded;
    final messages = hasPendingLocalReply ? null : doc['messages'];
    final openTime = doc['openTime'];
    final isUploaded =
        !hasPendingLocalReply && doc.containsKey('_rev') && doc['_rev'] != null;

    return FeedbackEntriesCompanion(
      id: Value(id),
      rev: Value(rev),
      title: Value(doc['title'] as String?),
      source: Value(doc['source'] as String?),
      status: Value(doc['status'] as String? ?? 'Open'),
      priority: Value(doc['priority'] as String?),
      owner: Value(doc['owner'] as String?),
      openTime: Value(
        openTime is int
            ? openTime
            : int.tryParse(openTime?.toString() ?? '') ?? 0,
      ),
      type: Value(doc['type'] as String?),
      url: Value(doc['url'] as String?),
      parentCode: Value(doc['parentCode'] as String?),
      isUploaded: Value(isUploaded),
      messages: Value(
        hasPendingLocalReply
            ? existing.messages
            : (messages != null ? jsonEncode(messages) : null),
      ),
      item: Value(doc['item'] as String?),
      state: Value(doc['state'] as String?),
    );
  }

  /// Creates a new feedback entry.
  ///
  /// Port of `FeedbackRepositoryImpl.createFeedback`.
  static FeedbackEntriesCompanion createFeedback({
    required String user,
    required String priority,
    required String type,
    required String message,
    String? item,
    String? state,
  }) {
    final id = _generateId();
    final timestamp = DateTime.now().millisecondsSinceEpoch;

    String title;
    String url;
    if (state != null) {
      title = 'Question regarding /$state';
      url = '/$state';
    } else {
      title = 'Question regarding /';
      url = '/';
    }

    final firstMessage = {
      'message': message,
      'time': timestamp.toString(),
      'user': user,
    };
    final messagesJson = jsonEncode([firstMessage]);

    return FeedbackEntriesCompanion(
      id: Value(id),
      title: Value(title),
      source: Value(user),
      status: const Value('Open'),
      priority: Value(priority),
      owner: Value(user),
      openTime: Value(timestamp),
      type: Value(type),
      url: Value(url),
      parentCode: const Value('dev'),
      isUploaded: const Value(false),
      messages: Value(messagesJson),
      item: Value(item),
      state: Value(state),
    );
  }

  /// Parses the embedded messages JSON into a list of [FeedbackMessage].
  static List<FeedbackMessage> parseMessages(String? messagesJson) {
    if (messagesJson == null || messagesJson.isEmpty) {
      return [];
    }
    try {
      final decoded = jsonDecode(messagesJson);
      if (decoded is List) {
        return decoded.map((e) {
          if (e is Map<String, dynamic>) {
            return FeedbackMessage(
              message: e['message'] as String? ?? '',
              user: e['user'] as String? ?? '',
              date: e['time'] as String? ?? '',
            );
          }
          return const FeedbackMessage();
        }).toList();
      }
    } catch (_) {}
    return [];
  }

  /// Gets the first message from the messages list.
  static String getFirstMessage(String? messagesJson) {
    final messages = parseMessages(messagesJson);
    if (messages.isEmpty) return '';
    return messages.first.message;
  }

  /// Adds a reply to the messages JSON.
  static String addReply(
    String? existingMessagesJson,
    String message,
    String user,
  ) {
    final messages = parseMessages(existingMessagesJson);
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    messages.add(
      FeedbackMessage(message: message, user: user, date: timestamp.toString()),
    );
    return jsonEncode(
      messages
          .map((m) => {'message': m.message, 'user': m.user, 'time': m.date})
          .toList(),
    );
  }

  /// Serializes a feedback row to a CouchDB-compatible JSON map.
  static Map<String, dynamic> toDoc(FeedbackRow row) {
    final doc = <String, dynamic>{
      // The id is device-generated, so CouchDB stores the document under it.
      // Omitting it made every upload create a *new* document, which turns a
      // reply on an already-uploaded feedback into a duplicate thread rather
      // than an update.
      '_id': row.id,
      'title': row.title,
      'source': row.source,
      'status': row.status,
      'priority': row.priority,
      'owner': row.owner,
      'openTime': row.openTime,
      'type': row.type,
      'url': row.url,
      'parentCode': row.parentCode,
      'item': row.item,
      'state': row.state,
    };

    if (row.rev != null) doc['_rev'] = row.rev;
    if (row.messages != null) {
      try {
        doc['messages'] = jsonDecode(row.messages!);
      } catch (_) {
        doc['messages'] = [];
      }
    }

    return doc;
  }

  static String _generateId() {
    // The suffix must not be derived from the timestamp it is appended to —
    // that makes it a second copy of the same value, so two feedbacks filed in
    // the same millisecond collide on the primary key and one overwrites the
    // other.
    final now = DateTime.now().millisecondsSinceEpoch;
    final random = Random.secure().nextInt(1 << 32).toRadixString(16);
    return 'feedback_$now-$random';
  }
}

/// Represents a single message/reply in a feedback thread.
///
/// Port of `model/FeedbackReply.kt`.
class FeedbackMessage {
  const FeedbackMessage({this.message = '', this.user = '', this.date = ''});

  final String message;
  final String user;
  final String date;
}
