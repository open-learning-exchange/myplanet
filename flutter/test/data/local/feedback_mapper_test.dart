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
}
