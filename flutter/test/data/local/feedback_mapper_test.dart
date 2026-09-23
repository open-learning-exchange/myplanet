import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/feedback_mapper.dart';
import 'package:myplanet/repository/feedback_repository_impl.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// The shape that took a chat sync down: a Planet's web UI writes the whole
/// CouchDB user document where the handset writes a plain user name.
const _userDocument = {
  '_id': 'org.couchdb.user:learning',
  'name': 'learning',
  'roles': ['learner'],
  'type': 'user',
};

void main() {
  late AppDatabase database;
  late FeedbackRepositoryImpl repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = FeedbackRepositoryImpl(
      feedbackDao: database.feedbackDao,
      planetApi: MockPlanetApi(),
    );
  });

  tearDown(() async {
    await database.close();
  });

  group('a document the server actually sends', () {
    test('one object-valued field does not fail the batch around it', () async {
      // The chat failure mode, pinned for feedback: 3 of 1,630 documents threw
      // on a raw `as String?` and the exception propagated out of the batch,
      // so all 1,630 failed to sync. Every document here must land.
      await repository.insertFromJson([
        {
          '_id': 'fb-before',
          '_rev': '1-a',
          'title': 'filed from the handset',
          'owner': 'learning',
        },
        {
          '_id': 'fb-odd',
          '_rev': '1-b',
          'title': 'filed through the web UI',
          'owner': _userDocument,
          'source': _userDocument,
          'messages': [
            {'message': 'the question', 'time': 1757000000000, 'user': 'ada'},
            {
              'message': 'the admin answer',
              'time': 1757000900000,
              'user': _userDocument,
            },
          ],
        },
        {
          // `_id` itself, which the batch read of existing rows derives a
          // second time. A raw cast threw here too, and out of the *outer*
          // loop rather than the mapper.
          '_id': _userDocument,
          '_rev': '1-d',
          'title': 'a document with no usable id',
        },
        {'_id': 'fb-after', '_rev': '1-c', 'title': 'filed later'},
      ]);

      final stored = await database.feedbackDao.watchAllSorted().first;
      expect(
        stored.map((row) => row.id),
        containsAll(<String>['fb-before', 'fb-odd', 'fb-after']),
        reason: 'a single odd field must cost one field, never the batch',
      );
      expect(
        stored,
        hasLength(4),
        reason: 'the fourth lands under an empty id',
      );

      // Landing is not enough: the odd document must also have carried its
      // content across, or the batch survived by storing nothing.
      final odd = stored.firstWhere((row) => row.id == 'fb-odd');
      expect(odd.title, 'filed through the web UI');
      expect(odd.owner, 'learning');
      expect(FeedbackMapper.parseMessages(odd.messages).map((m) => m.message), [
        'the question',
        'the admin answer',
      ]);
    });

    test('an object-valued person reads its name, as chat does', () async {
      final mapped = FeedbackMapper.fromDoc({
        '_id': 'fb1',
        'owner': _userDocument,
        'source': _userDocument,
      });

      expect(mapped.owner.value, 'learning');
      expect(mapped.source.value, 'learning');
    });

    test(
      'an object with no usable name reads as null, never as its literal',
      () {
        // `Map.toString()` is not JSON, so storing it poisons every later read.
        final mapped = FeedbackMapper.fromDoc({
          '_id': 'fb1',
          'owner': const {'roles': <String>[]},
          'title': const {'en': 'localised title'},
          'state': const ['a', 'b'],
        });

        expect(mapped.owner.value, isNull);
        expect(mapped.title.value, isNull);
        expect(mapped.state.value, isNull);
      },
    );

    test('an explicit empty string is stored as one, not folded to null', () {
      // `JsonUtils.getStringOrNull` would fold these to null, and one of them
      // hides a row: `FeedbackDao.watchByOwner` matches `owner.equals(owner ??
      // '')`, so a thread filed by a session with an empty name would vanish
      // from its own author's list on the next pull. `json_utils.dart`
      // documents the fold's cost; this is the mapper refusing to pay it.
      final mapped = FeedbackMapper.fromDoc({
        '_id': 'fb1',
        'title': '',
        'owner': '',
        'status': '',
      });

      expect(mapped.title.value, '');
      expect(mapped.owner.value, '');
      expect(
        mapped.status.value,
        '',
        reason: "an empty status is the server's, not a missing one",
      );
    });

    test('a row whose owner is empty stays in its author\'s list', () async {
      // The same fold, driven through the query that would have hidden it.
      await repository.insertFromJson([
        {'_id': 'fb1', '_rev': '1-a', 'owner': '', 'title': 'filed by nobody'},
      ]);

      final visible = await repository.getFeedback(userName: '').first;
      expect(visible.map((row) => row.id), ['fb1']);
    });

    test('a boolean reads as its literal, as any other non-string does', () {
      expect(
        FeedbackMapper.fromDoc({'_id': 'fb1', 'state': true}).state.value,
        'true',
      );
    });

    test('an object whose name is empty reads as null', () {
      // Divergence from `chat_mapper.dart`'s `_stringOrNull`, which keeps the
      // empty name. Nothing downstream can use it, and null is what this
      // mapper stores for a field it cannot read.
      expect(
        FeedbackMapper.fromDoc({
          '_id': 'fb1',
          'owner': const {'name': ''},
        }).owner.value,
        isNull,
      );
    });

    test('a numeric field reads as its digits, the port-wide convention', () {
      final mapped = FeedbackMapper.fromDoc({'_id': 'fb1', 'priority': 2});

      expect(mapped.priority.value, '2');
    });

    test('an object-valued _id is not stored as a row key', () {
      // `JsonUtils.getString` would hand back `Map.toString()` here, which is
      // worse than nothing for a primary key.
      expect(FeedbackMapper.idOf(const {'_id': _userDocument}), '');
      expect(FeedbackMapper.idOf(const {'id': 'fallback-id'}), 'fallback-id');
      expect(
        FeedbackMapper.idOf(const {'_id': '', 'id': 'fallback-id'}),
        '',
        reason:
            'an empty `_id` is a present one; only an absent key falls back',
      );
    });

    test('a well-formed document maps exactly as it did before', () {
      final mapped = FeedbackMapper.fromDoc({
        '_id': 'fb1',
        '_rev': '2-b',
        'title': 'Question regarding /',
        'source': 'ada',
        'status': 'Open',
        'priority': 'Yes',
        'owner': 'ada',
        'openTime': 1757000000000,
        'type': 'Bug',
        'url': '/',
        'parentCode': 'dev',
        'item': 'item1',
        'state': 'state1',
      });

      expect(mapped.id.value, 'fb1');
      expect(mapped.rev.value, '2-b');
      expect(mapped.title.value, 'Question regarding /');
      expect(mapped.source.value, 'ada');
      expect(mapped.status.value, 'Open');
      expect(mapped.priority.value, 'Yes');
      expect(mapped.owner.value, 'ada');
      expect(mapped.openTime.value, 1757000000000);
      expect(mapped.type.value, 'Bug');
      expect(mapped.url.value, '/');
      expect(mapped.parentCode.value, 'dev');
      expect(mapped.item.value, 'item1');
      expect(mapped.state.value, 'state1');
      expect(mapped.isUploaded.value, isTrue);
    });
  });

  group('parseMessages', () {
    test('a numeric time reads as its digits, as Gson gives Kotlin', () {
      // `Feedback.kt:63-65` reads `ob["time"].asString`, which renders a JSON
      // number. `as String?` threw on it instead.
      final parsed = FeedbackMapper.parseMessages(
        jsonEncode([
          {'message': 'the question', 'time': 1757000000000, 'user': 'ada'},
        ]),
      );

      expect(parsed, hasLength(1));
      expect(parsed.single.date, '1757000000000');
    });

    test('one odd reply costs that field, not the whole thread', () {
      final parsed = FeedbackMapper.parseMessages(
        jsonEncode([
          {'message': 'the question', 'time': '1757000000000', 'user': 'ada'},
          {
            'message': 'the admin answer',
            'time': 1757000900000,
            'user': _userDocument,
          },
        ]),
      );

      expect(
        parsed.map((m) => m.message),
        ['the question', 'the admin answer'],
        reason: 'the whole thread used to come back empty',
      );
      expect(parsed.last.user, 'learning');
      expect(parsed.last.date, '1757000900000');
    });

    test('the first message survives an odd reply after it', () {
      // What the detail screen draws as the feedback body, and what the list
      // screen searches: both were blank whenever any later reply was odd.
      expect(
        FeedbackMapper.getFirstMessage(
          jsonEncode([
            {'message': 'the question', 'time': '1', 'user': 'ada'},
            {'message': 'the admin answer', 'time': 2, 'user': _userDocument},
          ]),
        ),
        'the question',
      );
    });

    test('unparseable JSON is still an empty thread, not a throw', () {
      expect(FeedbackMapper.parseMessages('{not json'), isEmpty);
      expect(FeedbackMapper.parseMessages('{"messages": []}'), isEmpty);
      expect(FeedbackMapper.parseMessages(null), isEmpty);
      expect(FeedbackMapper.parseMessages(''), isEmpty);
    });

    test('a non-object element degrades to an empty message', () {
      final parsed = FeedbackMapper.parseMessages(
        jsonEncode(['just a string']),
      );

      expect(parsed, hasLength(1));
      expect(parsed.single.message, '');
    });
  });

  group('addReply', () {
    test('leaves every earlier message byte-identical', () async {
      // Kotlin appends to the `JsonArray` it parsed
      // (`FeedbackRepositoryImpl.addReply:100-110`). Rebuilding the array from
      // `parseMessages` rewrote the admin's reply down to three string fields,
      // and the uploader sends the whole array back under the document's
      // `_rev` — so the rewrite reached the server and every other device.
      final serverThread = [
        {'message': 'the question', 'time': '1757000000000', 'user': 'ada'},
        {
          'message': 'the admin answer',
          'time': 1757000900000,
          'user': _userDocument,
          'attachments': ['screenshot.png'],
        },
      ];

      final updated = FeedbackMapper.addReply(
        jsonEncode(serverThread),
        'thank you',
        'ada',
      );

      final decoded = jsonDecode(updated) as List<dynamic>;
      expect(decoded, hasLength(3));
      expect(
        decoded.take(2),
        serverThread,
        reason: 'an appended reply must not rewrite what it appends to',
      );
      expect(decoded.last, {
        'message': 'thank you',
        'time': isA<String>(),
        'user': 'ada',
      });
    });

    test('an unreadable thread is not silently replaced by the reply', () {
      // The old `parseMessages` round trip turned an empty parse into a
      // one-element array, which the uploader then PUT over the server's copy.
      // Nothing here can produce that from a thread that decodes.
      final updated = FeedbackMapper.addReply(
        jsonEncode([
          {'message': 'the admin answer', 'time': 2, 'user': _userDocument},
        ]),
        'thank you',
        'ada',
      );

      expect(jsonDecode(updated), hasLength(2));
    });

    test('a `messages` column that is not an array survives the reply', () {
      // `fromDoc` stores what the server sent, so a `messages` that is an
      // object or a string lands in the column verbatim. Appending used to
      // read it as `[]`, so the array became the reply alone — and `toDoc`
      // sends that back under the document's `_rev`, taking the value off the
      // server too. Neither app can read such a column; the reply is the half
      // that cannot be recovered from anywhere, so both are kept.
      final fromObject = jsonDecode(
        FeedbackMapper.addReply('{"0":{"message":"hi"}}', 'thank you', 'ada'),
      );
      expect(fromObject, hasLength(2));
      expect(fromObject.first, {
        '0': {'message': 'hi'},
      });
      expect((fromObject.last as Map)['message'], 'thank you');

      final fromString = jsonDecode(
        FeedbackMapper.addReply('"just a string"', 'thank you', 'ada'),
      );
      expect(fromString, ['just a string', isA<Map<String, dynamic>>()]);
    });

    test('an empty or unparseable column still yields just the reply', () {
      // Nothing to preserve: `toDoc` cannot decode bytes that are not JSON
      // either, so they never reach the server under any behaviour.
      expect(jsonDecode(FeedbackMapper.addReply(null, 'hi', 'ada')), [
        isA<Map<String, dynamic>>(),
      ]);
      expect(jsonDecode(FeedbackMapper.addReply('', 'hi', 'ada')), [
        isA<Map<String, dynamic>>(),
      ]);
      expect(jsonDecode(FeedbackMapper.addReply('{not json', 'hi', 'ada')), [
        isA<Map<String, dynamic>>(),
      ]);
      expect(jsonDecode(FeedbackMapper.addReply('null', 'hi', 'ada')), [
        isA<Map<String, dynamic>>(),
      ]);
    });
  });

  group('the `_rev` the row is stored with', () {
    test('an absent `_rev` leaves the stored revision alone', () {
      // `Value(null)` would write over it, and `FeedbackDao.upsertAll` is an
      // insert-or-replace — the Phase 56 shape, where a fetch that omits a
      // field wipes the stored one.
      final mapped = FeedbackMapper.fromDoc({'_id': 'fb1', 'title': 'no rev'});

      expect(mapped.rev.present, isFalse);
      expect(mapped.isUploaded.value, isFalse);
    });

    test('an object `_rev` leaves the row coherent', () {
      // The column and the flag are one read now. As two expressions they
      // disagreed here: the row stored as `isUploaded = true, rev = null` —
      // on the server, with no revision to update it under.
      final mapped = FeedbackMapper.fromDoc({
        '_id': 'fb1',
        '_rev': {'unexpected': 'shape'},
      });

      expect(mapped.rev.present, isFalse);
      expect(mapped.isUploaded.value, isFalse);
    });

    test('an ordinary `_rev` is stored and marks the row uploaded', () {
      final mapped = FeedbackMapper.fromDoc({'_id': 'fb1', '_rev': '2-b'});

      expect(mapped.rev.value, '2-b');
      expect(mapped.isUploaded.value, isTrue);
    });
  });

  group('the id both sides of the sync derive', () {
    test(
      'the `id` fallback still finds the row whose reply is pending',
      () async {
        // `insertFromJson` reads the id once to batch-load the stored rows and
        // the mapper reads it again to look one up. When the two disagree the
        // lookup misses, the mapper sees no existing row, and the server's copy
        // of the thread overwrites a reply the outbox has not sent yet — so both
        // go through `FeedbackMapper.idOf`, `id` fallback included.
        await repository.insertFromJson([
          {
            'id': 'fb-legacy',
            '_rev': '1-a',
            'messages': [
              {'message': 'the question', 'time': '1', 'user': 'ada'},
            ],
          },
        ]);
        await repository.addReply('fb-legacy', 'my pending reply', 'ada');

        await repository.insertFromJson([
          {
            'id': 'fb-legacy',
            '_rev': '2-b',
            'messages': [
              {'message': 'the question', 'time': '1', 'user': 'ada'},
            ],
          },
        ]);

        final stored = await repository.getFeedbackById('fb-legacy');
        expect(
          FeedbackMapper.parseMessages(stored!.messages).map((m) => m.message),
          contains('my pending reply'),
        );
        expect(stored.isUploaded, isFalse);
      },
    );
  });

  group('columns whose default is the port\'s own choice', () {
    test('a document with no status reads as Open, not as Kotlin\'s empty', () {
      // A deliberate, pre-existing divergence: Kotlin stores `""`
      // (`FeedbackRepositoryImpl.kt:143` through `JsonUtils.getString`), the
      // port stores `Open` so the status chip and the closed check have
      // something to read. Pinned so it is not lost by accident.
      expect(FeedbackMapper.fromDoc({'_id': 'fb1'}).status.value, 'Open');
      expect(
        FeedbackMapper.fromDoc({'_id': 'fb1', 'status': 'Closed'}).status.value,
        'Closed',
      );
    });

    test('openTime accepts the number and the numeric string alike', () {
      // `JsonUtils.getLong` is what Kotlin uses here (`:146`), and it takes
      // either. A shape that is neither is `0`, never a throw.
      expect(
        FeedbackMapper.fromDoc({
          '_id': 'f',
          'openTime': 1757000000000,
        }).openTime.value,
        1757000000000,
      );
      expect(
        FeedbackMapper.fromDoc({
          '_id': 'f',
          'openTime': '1757000000000',
        }).openTime.value,
        1757000000000,
      );
      expect(
        FeedbackMapper.fromDoc({
          '_id': 'f',
          'openTime': _userDocument,
        }).openTime.value,
        0,
      );
    });
  });

  group('a preserved non-array value and the merge, driven together', () {
    test('a pull cannot duplicate the thread it was wrapped into', () async {
      // `_messagesForAppend` keeps an unreadable `messages` value as the
      // array's first element. The merge identifies replies by
      // `message`/`user`/`time`, which a wrapped string does not have — so an
      // element-identity that reads it as "different" stops the shared prefix
      // at zero and appends the whole local array to the server's copy again,
      // on every pull while the row is still pending. The sync re-queues after
      // a completed pull, so that doubled array is what uploads.
      await repository.insertFromJson([
        {'_id': 'fb1', '_rev': '1-a', 'messages': 'just a string'},
      ]);
      await repository.addReply('fb1', 'my reply', 'ada');

      final afterReply = await repository.getFeedbackById('fb1');
      final uploaded = FeedbackMapper.toDoc(afterReply!)['messages'];
      expect(uploaded, [
        'just a string',
        isA<Map<String, dynamic>>(),
      ], reason: 'the value the server sent must survive the reply');

      // The server echoes back exactly what the upload sent, twice over.
      for (var pull = 0; pull < 2; pull++) {
        await repository.insertFromJson([
          {'_id': 'fb1', '_rev': '1-a', 'messages': uploaded},
        ]);
        final merged = await repository.getFeedbackById('fb1');
        expect(
          jsonDecode(merged!.messages!),
          hasLength(2),
          reason: 'pull ${pull + 1} grew a thread that had not changed',
        );
      }
    });
  });

  group('the admin reply, writer and reader driven together', () {
    test(
      'survives a pull, the detail screen read, a local reply and the upload',
      () async {
        // The round trip the previous defects each passed one half of: the
        // sync writes the row, the detail screen reads the thread, the user
        // replies, and the uploader serializes what goes back to CouchDB.
        await repository.insertFromJson([
          {
            '_id': 'fb1',
            '_rev': '2-admin',
            'title': 'Sync fails offline',
            'owner': _userDocument,
            'messages': [
              {'message': 'the question', 'time': 1757000000000, 'user': 'ada'},
              {
                'message': 'the admin answer',
                'time': 1757000900000,
                'user': _userDocument,
              },
            ],
          },
        ]);

        final pulled = await repository.getFeedbackById('fb1');
        expect(
          FeedbackMapper.parseMessages(pulled!.messages).map((m) => m.message),
          ['the question', 'the admin answer'],
          reason: 'the detail screen drew an empty thread here',
        );

        await repository.addReply('fb1', 'thank you', 'ada');

        final replied = await repository.getFeedbackById('fb1');
        final payload = FeedbackMapper.toDoc(replied!);
        final messages = payload['messages'] as List<dynamic>;

        expect(messages, hasLength(3));
        expect(messages[1], {
          'message': 'the admin answer',
          'time': 1757000900000,
          'user': _userDocument,
        }, reason: "the upload must not rewrite the admin's own reply");
        expect((messages[2] as Map)['message'], 'thank you');
        expect(payload['_rev'], '2-admin');
        expect(
          payload['owner'],
          'learning',
          reason:
              'reading the name is also a write: the upload rebuilds the '
              'document from the columns, so the object is flattened on the '
              'server. Kotlin would flatten it to an empty string instead.',
        );
        expect(
          replied.isUploaded,
          isFalse,
          reason: 'so the outbox collects it',
        );
      },
    );
  });

  group('createFeedback scopes item to state', () {
    // `FeedbackRepositoryImpl.kt:45-53` assigns `state` and `item` **inside**
    // `if (state != null)` and neither in the else arm:
    //
    // ```kotlin
    // if (state != null) {
    //     feedback.title = "Question regarding /$state"
    //     feedback.url = "/$state"
    //     feedback.state = state
    //     feedback.item = item
    // } else {
    //     feedback.title = "Question regarding /"
    //     feedback.url = "/"
    // }
    // ```
    //
    // The port wrote both outside the branch, so it could emit a document the
    // Kotlin app is structurally incapable of producing: `item` naming a row
    // whose collection is unstated, while `title` and `url` still say `/`.
    // `serializeFeedback` sends both keys explicitly (null rather than
    // omitted, matching Kotlin's `buildJsonObject.put(String, String?)`), so
    // the difference reaches the server.

    test('an item with no state is dropped, as Kotlin drops it', () {
      final row = FeedbackMapper.createFeedback(
        user: 'ada',
        priority: 'No',
        type: 'Bug',
        message: 'from a screen that knows the row but not the collection',
        item: 'team-1',
      );

      expect(row.item.value, isNull);
      expect(row.state.value, isNull);
      expect(row.title.value, 'Question regarding /');
      expect(row.url.value, '/');
    });

    test('an item riding with its state is kept', () {
      // The fixture that makes the assertion above mean something: without
      // this pair, dropping `item` unconditionally would also be green.
      final row = FeedbackMapper.createFeedback(
        user: 'ada',
        priority: 'No',
        type: 'Bug',
        message: 'from the teams list',
        item: 'team-1',
        state: 'teams',
      );

      expect(row.item.value, 'team-1');
      expect(row.state.value, 'teams');
      expect(row.title.value, 'Question regarding /teams');
      expect(row.url.value, '/teams');
    });

    test('a state with no item keeps the state', () {
      // The one asymmetry Kotlin *can* produce, because `TeamFragment
      // .getBundle:299-305` puts `team._id`, which is nullable, under a
      // `state` that never is.
      final row = FeedbackMapper.createFeedback(
        user: 'ada',
        priority: 'No',
        type: 'Bug',
        message: 'a team with no id',
        state: 'teams',
      );

      expect(row.item.value, isNull);
      expect(row.state.value, 'teams');
      expect(row.url.value, '/teams');
    });
  });
}
