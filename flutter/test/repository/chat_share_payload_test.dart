import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/chat_repository.dart';

/// Port of `app/src/test/.../model/ChatSharePayloadTest.kt`, plus the cases
/// that test pins by omission.
ChatRow _chat({
  String id = 'local-1',
  String? docId,
  String? rev,
  String? title,
  String? user,
  String? aiProvider,
  String? conversations,
}) => ChatRow(
  id: id,
  docId: docId,
  rev: rev,
  title: title,
  user: user,
  aiProvider: aiProvider,
  conversations: conversations,
  lastUsed: 0,
  isUploaded: false,
);

const _now = 1700000000000;

Map<String, dynamic> _news(Map<String, String> map) =>
    jsonDecode(map['news']!) as Map<String, dynamic>;

void main() {
  test('the outer key set is exactly the seven the Kotlin writes', () {
    final map = buildChatShareMap(
      chat: _chat(
        docId: 'chat_1',
        rev: '1-rev',
        title: 'Sample Chat',
        user: 'user_1',
        aiProvider: 'openai',
      ),
      note: 'Check this chat out',
      target: null,
      section: 'Teams',
      nowMillis: _now,
    );

    expect(map.keys.toSet(), {
      'message',
      'viewInId',
      'viewInSection',
      'messageType',
      'messagePlanetCode',
      'chat',
      'news',
    });
  });

  test('a null target leaves the three team-derived keys empty', () {
    final map = buildChatShareMap(
      chat: _chat(),
      note: 'Note text',
      target: null,
      section: 'Community',
      nowMillis: _now,
    );

    expect(map['viewInId'], '');
    expect(map['messageType'], '');
    expect(map['messagePlanetCode'], '');
    expect(map['viewInSection'], 'Community');
    expect(map['message'], 'Note text');
    expect(map['chat'], 'true');
  });

  test('a populated target fills viewInId, messageType and planet code', () {
    final map = buildChatShareMap(
      chat: _chat(),
      note: 'Team note',
      target: const ChatShareTarget(
        id: 'team_123',
        name: 'Test Team',
        teamType: 'team',
        teamPlanetCode: 'planet_xyz',
      ),
      section: ChatShareSection.teams,
      nowMillis: _now,
    );

    expect(map['viewInId'], 'team_123');
    expect(map['messageType'], 'team');
    expect(map['messagePlanetCode'], 'planet_xyz');
  });

  test('null id, rev, user and provider serialize as empty strings', () {
    final news = _news(
      buildChatShareMap(
        chat: _chat(),
        note: 'test',
        target: null,
        section: 'test',
        nowMillis: _now,
      ),
    );

    expect(news['_id'], '');
    expect(news['_rev'], '');
    expect(news['user'], '');
    expect(news['aiProvider'], '');
    // Stringified, not numeric: every value of the Kotlin map is a String.
    expect(news['createdDate'], '1700000000000');
    expect(news['updatedDate'], '1700000000000');
  });

  // The behaviour upstream `7167684` changed. The adapter used to write
  // `"${chatHistory.title}".trim()`, and a Kotlin string template of null
  // renders the four characters `null`, so an untitled chat was shared under
  // the title "null". `(chat.title ?: "").trim()` is what is ported.
  test('an untitled chat shares as an empty title, never the word null', () {
    final news = _news(
      buildChatShareMap(
        chat: _chat(title: null),
        note: '',
        target: null,
        section: ChatShareSection.community,
        nowMillis: _now,
      ),
    );

    expect(news['title'], '');
    expect(news['title'], isNot('null'));
  });

  test('a title is trimmed', () {
    final news = _news(
      buildChatShareMap(
        chat: _chat(title: '  Tech Discussion \n'),
        note: '',
        target: null,
        section: ChatShareSection.community,
        nowMillis: _now,
      ),
    );

    expect(news['title'], 'Tech Discussion');
  });

  test('conversations round-trip through the nested JSON string', () {
    final map = buildChatShareMap(
      chat: _chat(
        docId: 'chat_100',
        rev: '2-rev',
        title: 'Tech Discussion',
        conversations: jsonEncode([
          {'query': 'What is Kotlin?', 'response': 'A programming language.'},
          {'query': 'What is Android?', 'response': 'A mobile OS.'},
        ]),
      ),
      note: 'Round trip test',
      target: null,
      section: ChatShareSection.community,
      nowMillis: _now,
    );

    final conversations = _news(map)['conversations'];
    // A *string*, not an array — `News.createNews` re-parses it out of a
    // string primitive, and would find nothing if this were an array.
    expect(conversations, isA<String>());

    final decoded = jsonDecode(conversations as String) as List;
    expect(decoded, hasLength(2));
    expect(decoded[0]['query'], 'What is Kotlin?');
    expect(decoded[0]['response'], 'A programming language.');
    expect(decoded[1]['query'], 'What is Android?');
    expect(decoded[1]['response'], 'A mobile OS.');
  });

  test('a turn with no response serializes it as an empty string', () {
    final map = buildChatShareMap(
      chat: _chat(
        conversations: jsonEncode([
          {'query': 'Only asked'},
        ]),
      ),
      note: '',
      target: null,
      section: ChatShareSection.community,
      nowMillis: _now,
    );

    final decoded = jsonDecode(_news(map)['conversations'] as String) as List;
    expect(decoded.single, {'query': 'Only asked', 'response': ''});
  });

  test('a chat with no conversations yields an empty array, not "null"', () {
    final map = buildChatShareMap(
      chat: _chat(conversations: null),
      note: '',
      target: null,
      section: ChatShareSection.community,
      nowMillis: _now,
    );

    // Kotlin's `gson.toJson(null)` writes the four characters `null` here,
    // which `News.createNews` then hands to `fromJson(..., JsonArray)` and
    // dereferences — an uncaught NPE out of the share. `[]` is the same
    // *meaning* with no crash.
    expect(_news(map)['conversations'], '[]');
  });

  test('the section is carried verbatim', () {
    for (final section in [
      ChatShareSection.community,
      ChatShareSection.teams,
      ChatShareSection.enterprises,
    ]) {
      final map = buildChatShareMap(
        chat: _chat(),
        note: '',
        target: null,
        section: section,
        nowMillis: _now,
      );
      expect(map['viewInSection'], section);
    }
  });
}
