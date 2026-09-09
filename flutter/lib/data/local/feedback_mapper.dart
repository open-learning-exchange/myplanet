import 'dart:convert';
import 'dart:math';

import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Reads a string-valued field the way the rest of the port reads one, and
/// survives every shape a raw `as String?` cast does not.
///
/// Every field below used to be read with `as String?`, which throws a
/// `TypeError` on anything that is neither a string nor null. **Kotlin cannot
/// throw here**: `JsonUtils.getString` returns `""` for a non-string
/// (`JsonUtils.kt:65-68`), so the port was stricter than the app it ports —
/// the same defect that made a Planet's 1,630 chat documents fail to sync as
/// one batch because 3 of them carried an object-valued `user`, fixed in
/// `chat_mapper.dart`.
///
/// Delegating to [JsonUtils.getString] keeps a JSON **number** readable
/// (`2019` reads as `'2019'` — the port's documented, deliberate divergence
/// from Kotlin's `""`), and the two guards around it mean a well-formed
/// document maps to exactly the bytes the cast produced.
///
/// **Both guards are load-bearing, and the empty one is not obvious.**
///
/// * A missing key and an explicit JSON `null` read as null, because that is
///   what this mapper's columns have always held for an absent field —
///   [JsonUtils.getString] would give `''`, and [JsonUtils.getStringOrNull]
///   would fold a *present* `""` to null, which is a different thing.
///   `json_utils.dart` documents at length why that fold costs defects on a
///   mapper path, and it would have cost one here: `owner` is matched with
///   `FeedbackDao.watchByOwner`'s `f.owner.equals(owner ?? '')`, so a thread
///   filed by a session with an empty name would map to null on the next pull
///   and **disappear from its own author's list**.
/// * A Map or a List reads as null, because [JsonUtils.getString]'s own doc
///   comment forbids passing it either: it would store Dart's
///   `Map.toString()`, which is not JSON and throws wherever it is read back.
///   Null is this mapper's spelling of Kotlin's `""` for a field it cannot
///   read.
String? _string(String key, Map<String, dynamic> json) {
  final value = json[key];
  if (value == null || value is Map || value is List) return null;
  return JsonUtils.getString(key, json);
}

/// [_string], except that an object reads its `name`.
///
/// Used only for the fields that hold a **person** — `owner`, `source`, and a
/// reply's `user`. Kotlin writes all three as plain strings
/// (`FeedbackRepositoryImpl.kt:63-66, 100-104`), but a CouchDB field that is a
/// user name on one document can be the whole user object on another: that is
/// **observed** on `chat_history` (3 of a Planet's 1,630, see
/// `chat_mapper.dart`) and **inferred** here, from feedback replies being
/// authored through the same web UI. No captured feedback document proves it;
/// what is certain is only what each app does when it arrives.
///
/// Taking it is a deliberate improvement, and **how much of one depends on
/// which field**, because Kotlin reads the two through different code:
///
/// * `owner` and `source` come from `JsonUtils.getString`
///   (`FeedbackRepositoryImpl.kt:142, 145`), so Kotlin stores `""` and
///   discards something unambiguous.
/// * A reply's `user` is read as `ob["user"].asString` with no `JsonUtils` and
///   no `try` (`Feedback.kt:61-69`), and `JsonObject` does not override
///   `getAsString` — so **Kotlin throws `UnsupportedOperationException` out of
///   `messageList`**, which `FeedbackDetailActivity.kt:61` calls from an
///   unguarded collector. The Android app loses the detail screen on the same
///   document. Neither app should, so this reads the name.
///
/// **It is a write as well as a read.** [FeedbackMapper.toDoc] rebuilds the
/// document from these columns and the uploader sends it under the carried
/// `_rev`, so replying to a thread whose `owner` was an object flattens it to
/// `"learning"` on the server. Kotlin would flatten the same field to `""`,
/// which is why this is still the better of the two — but it is a change to
/// the server's document, not only to what this device shows.
///
/// The shape was first observed against `https://planet.learning.ole.org`;
/// `chat_mapper.dart`'s `_stringOrNull` reads the object the same way, bar
/// `{"name": ""}`, which it keeps as `''` and this drops to null. It differs
/// on a **number**, which that helper drops and [JsonUtils.getString] keeps as
/// its digits — the port-wide convention, and what Gson's `asString` gives
/// Kotlin's own reply reader, where a numeric `time` renders as its literal
/// text rather than being dropped (`Feedback.kt:61-69`).
String? _userString(String key, Map<String, dynamic> json) {
  final value = json[key];
  if (value is Map) {
    final name = value['name'];
    return name is String && name.isNotEmpty ? name : null;
  }
  return _string(key, json);
}

/// Port of `model/Feedback.kt` and `model/FeedbackReply.kt`.
///
/// Maps CouchDB feedback documents to [FeedbackEntriesCompanion] for Drift persistence.
class FeedbackMapper {
  FeedbackMapper._();

  /// The document id, read the one way so the sync's batch read of existing
  /// rows and the mapper agree on it.
  ///
  /// `FeedbackRepositoryImpl.insertFromJson` derives the id a second time to
  /// look up the stored row whose pending reply [fromDoc] must preserve. When
  /// the two derivations disagree the lookup misses, [fromDoc] sees no
  /// existing row, and the server's copy of the thread overwrites an unsent
  /// reply — so they read through this.
  static String idOf(Map<String, dynamic> doc) =>
      _string('_id', doc) ?? _string('id', doc) ?? '';

  /// Converts a CouchDB feedback document JSON to a [FeedbackEntriesCompanion].
  ///
  /// Port of `FeedbackRepositoryImpl.mapToFeedback`. When the stored row has a
  /// reply the server has not confirmed (`isUploaded == false`), the row stays
  /// pending and its thread is merged rather than replaced — a sync used to
  /// overwrite it with the server's copy, silently destroying the reply the
  /// uploader was about to send. See [_mergePendingReplies] for why keeping
  /// the local array alone, which is what Kotlin does, loses the other half.
  static FeedbackEntriesCompanion fromDoc(
    Map<String, dynamic> doc, [
    FeedbackRow? existing,
  ]) {
    final id = idOf(doc);
    final hasPendingLocalReply = existing != null && !existing.isUploaded;
    final serverMessages = doc['messages'];
    final isUploaded =
        !hasPendingLocalReply && doc.containsKey('_rev') && doc['_rev'] != null;

    return FeedbackEntriesCompanion(
      id: Value(id),
      rev: Value(_string('_rev', doc)),
      title: Value(_string('title', doc)),
      source: Value(_userString('source', doc)),
      status: Value(_string('status', doc) ?? 'Open'),
      priority: Value(_string('priority', doc)),
      owner: Value(_userString('owner', doc)),
      openTime: Value(JsonUtils.getLong('openTime', doc)),
      type: Value(_string('type', doc)),
      url: Value(_string('url', doc)),
      parentCode: Value(_string('parentCode', doc)),
      isUploaded: Value(isUploaded),
      messages: Value(
        hasPendingLocalReply
            ? _mergePendingReplies(existing.messages, serverMessages)
            : (serverMessages != null ? jsonEncode(serverMessages) : null),
      ),
      item: Value(_string('item', doc)),
      state: Value(_string('state', doc)),
    );
  }

  /// Puts the server's copy of a thread back together with the replies this
  /// device has not sent yet.
  ///
  /// **A deliberate divergence, and the one place the port refuses to copy
  /// Kotlin.** On the pending branch Kotlin keeps the local array and throws
  /// the server's away (`FeedbackRepositoryImpl.kt:154`) while still adopting
  /// the server's `_rev` (`:152`); the upload then POSTs that local array back
  /// under the fresh revision, and CouchDB replaces the document
  /// (`UploadConfigs.kt:198-208`, no `dbIdExtractor`, so it is always a POST
  /// of the whole body). An admin who replies through the web UI while a
  /// handset has an unsent reply therefore loses their reply **on the server**,
  /// for every device and the web UI too. Both apps did this; the port stops.
  ///
  /// Both arrays are append-only in normal use, so they share a prefix and
  /// diverge into "the admin's replies" and "ours". Keeping the server's copy
  /// whole and re-appending only our tail after that prefix is what makes the
  /// merge idempotent: once our reply has landed and been pulled back, the
  /// local array is a **prefix** of the server's, the tail is empty, and the
  /// server's copy is adopted unchanged — so a reply cannot be duplicated by
  /// repeated syncs.
  ///
  /// Elements are compared on `message`/`user`/`time` through the same readers
  /// the rest of this file uses, not on their bytes: the server may echo a
  /// reply back with its keys in a different order, and comparing encoded
  /// bytes would read that as a divergence and append our copy a second time.
  static String? _mergePendingReplies(
    String? localJson,
    Object? serverMessages,
  ) {
    if (serverMessages is! List) return localJson;
    final local = _decodeMessages(localJson);

    var shared = 0;
    while (shared < local.length &&
        shared < serverMessages.length &&
        _sameMessage(local[shared], serverMessages[shared])) {
      shared++;
    }

    if (shared == local.length) return jsonEncode(serverMessages);
    return jsonEncode([...serverMessages, ...local.skip(shared)]);
  }

  /// Whether two message elements are the same reply.
  static bool _sameMessage(Object? a, Object? b) {
    if (a is! Map<String, dynamic> || b is! Map<String, dynamic>) return false;
    return _string('message', a) == _string('message', b) &&
        _userString('user', a) == _userString('user', b) &&
        _string('time', a) == _string('time', b);
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
  ///
  /// Port of `Feedback.messageList` / `Feedback.message` (`Feedback.kt:54-85`),
  /// which read each element with Gson's `asString` — so a **numeric** `time`
  /// reads as its digits there, not as nothing.
  ///
  /// The field reads used to be raw `as String?` casts inside a `try` that
  /// wrapped the whole loop, which made one odd value cost the entire thread:
  /// a reply whose `time` is a JSON number (what a Planet's web UI writes) or
  /// whose `user` is the full user document threw a `TypeError`, the blanket
  /// `catch` swallowed it, and this returned `[]`. The detail screen then drew
  /// no body and no replies — an admin's answer simply absent — and, worse,
  /// [addReply] used to rebuild the thread from this list, so the user's next
  /// reply replaced every earlier message and the uploader PUT that truncated
  /// array over the server's document.
  ///
  /// The `try` now covers only [jsonDecode], where a genuinely unparseable
  /// string is the one thing that cannot be salvaged; the element mapping is
  /// total, so a surprising field degrades to `''` instead of emptying the
  /// thread.
  static List<FeedbackMessage> parseMessages(String? messagesJson) {
    return _decodeMessages(messagesJson)
        .map(
          (e) => e is Map<String, dynamic>
              ? FeedbackMessage(
                  message: _string('message', e) ?? '',
                  user: _userString('user', e) ?? '',
                  date: _string('time', e) ?? '',
                )
              : const FeedbackMessage(),
        )
        .toList();
  }

  /// The messages array exactly as stored, elements untouched.
  ///
  /// A column that decodes to something other than a list is `[]` here, which
  /// [addReply] then appends to — so a reply on a thread whose stored
  /// `messages` is a JSON object or a JSON *string* still replaces it. Kotlin
  /// has the same hole from the other side (`getJsonArray` normalises any
  /// non-array to `"[]"`, `JsonUtils.kt:126-129`), so this is not a parity
  /// gap; it is the one shape the array-preserving fix does not reach.
  static List<dynamic> _decodeMessages(String? messagesJson) {
    if (messagesJson == null || messagesJson.isEmpty) return [];
    try {
      final decoded = jsonDecode(messagesJson);
      return decoded is List ? decoded : [];
    } catch (_) {
      return [];
    }
  }

  /// Gets the first message from the messages list.
  static String getFirstMessage(String? messagesJson) {
    final messages = parseMessages(messagesJson);
    if (messages.isEmpty) return '';
    return messages.first.message;
  }

  /// Adds a reply to the messages JSON.
  ///
  /// Appends to the decoded array **without re-encoding the elements already
  /// in it**, as Kotlin does (`FeedbackRepositoryImpl.addReply:100-110` adds to
  /// the `JsonArray` it parsed). Rebuilding the array from [parseMessages]
  /// rewrote every earlier message down to `message`/`user`/`time`: an admin
  /// reply that arrived from the web UI carrying an object-valued `user`, a
  /// numeric `time` or any other key lost it here, and because the uploader
  /// sends the whole array back with the document's `_rev`, that loss reached
  /// the server and every other device.
  static String addReply(
    String? existingMessagesJson,
    String message,
    String user,
  ) {
    final messages = _decodeMessages(existingMessagesJson);
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    messages.add({
      'message': message,
      'time': timestamp.toString(),
      'user': user,
    });
    return jsonEncode(messages);
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
