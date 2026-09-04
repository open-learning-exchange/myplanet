import 'dart:convert';

import 'package:drift/drift.dart' show Variable;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/news_mapper.dart';
import 'package:myplanet/repository/voices_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late VoicesRepository repository;
  var idCounter = 0;

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    idCounter = 0;
    repository = VoicesRepository(
      api,
      database.newsDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(5000),
      createId: () => 'local-${++idCounter}',
    );
  });
  tearDown(() => database.close());

  NewsRow rowWith({String? viewableBy, String? viewIn}) => NewsRow(
    id: 'n1',
    time: 0,
    updatedDate: 0,
    imageUrls: const [],
    labels: const [],
    newsCreatedDate: 0,
    newsUpdatedDate: 0,
    chat: false,
    isEdited: false,
    editedTime: 0,
    viewableBy: viewableBy,
    viewIn: viewIn,
  );

  group('visibility', () {
    test('a community post is visible to anyone', () {
      expect(
        VoicesRepository.isVisibleToUser(
          rowWith(viewableBy: 'Community'),
          'anyone',
        ),
        isTrue,
      );
    });

    test(
      'only a community viewIn entry makes the post visible, named or wildcard',
      () {
        // Upstream f4adebf rewrote this: a *teams* entry that names the viewer
        // no longer leaks the post into the community feed, and an empty or
        // "@" community id means "everyone".
        final named = rowWith(
          viewIn: jsonEncode([
            {'_id': 'USER-1', 'section': 'community'},
          ]),
        );
        expect(VoicesRepository.isVisibleToUser(named, 'user-1'), isTrue);
        expect(VoicesRepository.isVisibleToUser(named, 'user-2'), isFalse);

        final wildcard = rowWith(
          viewIn: jsonEncode([
            {'_id': '@', 'section': 'community'},
          ]),
        );
        expect(VoicesRepository.isVisibleToUser(wildcard, 'anyone'), isTrue);

        final noId = rowWith(
          viewIn: jsonEncode([
            {'section': 'community'},
          ]),
        );
        expect(VoicesRepository.isVisibleToUser(noId, 'anyone'), isTrue);
      },
    );

    test('a team section naming the viewer stays invisible', () {
      final row = rowWith(
        viewIn: jsonEncode([
          {'_id': 'USER-1', 'section': 'teams'},
        ]),
      );
      expect(VoicesRepository.isVisibleToUser(row, 'user-1'), isFalse);
    });

    test('a wildcard viewer sees every community entry', () {
      // A guest asking with "@" would otherwise hide nothing.
      final row = rowWith(
        viewIn: jsonEncode([
          {'_id': 'user-1', 'section': 'community'},
        ]),
      );
      expect(VoicesRepository.isVisibleToUser(row, '@'), isTrue);
      expect(VoicesRepository.isVisibleToUser(row, ''), isTrue);
    });

    test('a post addressed to nobody is visible to nobody', () {
      // The fall-through that keeps team-only posts out of the community feed.
      expect(
        VoicesRepository.isVisibleToUser(
          rowWith(viewableBy: 'teams'),
          'user-1',
        ),
        isFalse,
      );
    });

    test('malformed viewIn fails closed', () {
      // `isVisibleToUser` catches Throwable and returns false; a parse error
      // must not open a post up to everyone.
      expect(
        VoicesRepository.isVisibleToUser(
          rowWith(viewIn: 'not json at all'),
          'user-1',
        ),
        isFalse,
      );
      expect(
        VoicesRepository.isVisibleToUser(
          rowWith(viewIn: jsonEncode({'_id': 'user-1'})),
          'user-1',
        ),
        isFalse,
        reason: 'viewIn must be an array; an object is not a match list',
      );
    });
  });

  group('sort date', () {
    test('a shared post sorts by when it was shared', () {
      final row = rowWith(
        viewIn: jsonEncode([
          {'section': 'community', 'sharedDate': 900},
        ]),
      );
      expect(VoicesRepository.sortDateOf(row), 900);
    });

    test('falls back to the post time without a shared date', () {
      expect(VoicesRepository.sortDateOf(rowWith(viewIn: '[]')), 0);
      expect(VoicesRepository.sortDateOf(rowWith(viewIn: 'broken')), 0);
    });
  });

  test('a reply keys on the parent local id even with a server _id', () async {
    // Port of the Kotlin test added in 5f3198970: `postReply` switched from
    // `news._id ?: news.id` to `news.id`. The discriminating case is a post
    // authored offline and then uploaded — its row keeps the local id while
    // `docId` carries the server's.
    final rootId = await repository.createPost(
      message: 'Root',
      userId: 'user-1',
      userName: 'Ada',
    );
    await repository.markUploaded(rootId, 'server-id-1', '1-abc');

    final replyId = await repository.postReply(
      parentId: rootId,
      message: 'Child',
      userId: 'user-1',
      userName: 'Ada',
    );

    expect((await repository.getById(replyId!))?.replyTo, rootId);
  });

  test(
    'a reply inherits its parent audience and threads by server id',
    () async {
      await repository.cacheDocuments([
        {
          '_id': 'post-1',
          'docType': 'message',
          'message': 'Hello',
          'viewableBy': 'teams',
          'viewableId': 'team-1',
          'viewIn': [
            {'_id': 'team-1', 'section': 'teams'},
          ],
        },
      ]);

      final replyId = await repository.postReply(
        parentId: 'post-1',
        message: 'A reply',
        userId: 'user-1',
        userName: 'Ada',
      );

      final reply = await repository.getById(replyId!);
      expect(reply?.replyTo, 'post-1');
      expect(reply?.viewableBy, 'teams');
      expect(reply?.viewableId, 'team-1');
      // Inheriting viewIn is what stops a reply reaching an audience the post
      // it answers never had.
      expect(reply?.viewIn, contains('team-1'));
      expect(await repository.replyCount('post-1'), 1);
    },
  );

  test('a reply cycle terminates instead of overflowing the stack', () async {
    // `replyTo` is server data and nothing guarantees it is acyclic. Two rows
    // pointing at each other used to recurse forever, because the visited
    // guard was rebuilt in each frame.
    await repository.cacheDocuments([
      {'_id': 'a', 'docType': 'message', 'message': 'A', 'replyTo': 'b'},
      {'_id': 'b', 'docType': 'message', 'message': 'B', 'replyTo': 'a'},
    ]);

    final ids = await repository.collectThreadIds('a');

    expect(ids.toSet(), {'a', 'b'});
    expect(ids.length, 2, reason: 'each row is collected exactly once');
  });

  test('a post that replies to itself terminates', () async {
    await repository.cacheDocuments([
      {'_id': 'self', 'docType': 'message', 'message': 'S', 'replyTo': 'self'},
    ]);
    expect(await repository.collectThreadIds('self'), ['self']);
  });

  test('sync cleanup handles more ids than SQLite will bind', () async {
    // `deleteNotIn` used to bind one variable per synced id in a single
    // statement, which SQLite rejects past SQLITE_MAX_VARIABLE_NUMBER.
    final docs = [
      for (var i = 0; i < 1200; i++)
        {'_id': 'post-$i', 'docType': 'message', 'message': 'm$i'},
    ];
    await repository.cacheDocuments(docs);
    await repository.cacheDocuments([
      {'_id': 'stale', 'docType': 'message', 'message': 'gone'},
    ]);
    expect(await database.newsDao.count(), 1201);

    await database.newsDao.deleteNotIn(
      docs.map((doc) => doc['_id']!).toList(growable: false),
    );

    expect(await database.newsDao.count(), 1200);
    expect(await repository.getById('stale'), equals(null));
  });

  test('deleting a post takes its whole reply subtree', () async {
    final rootId = await repository.createPost(
      message: 'Root',
      userId: 'user-1',
      userName: 'Ada',
    );
    final replyId = await repository.postReply(
      parentId: rootId,
      message: 'Child',
      userId: 'user-1',
      userName: 'Ada',
    );
    await repository.postReply(
      parentId: replyId!,
      message: 'Grandchild',
      userId: 'user-1',
      userName: 'Ada',
    );

    expect(await database.newsDao.count(), 3);
    final removed = await repository.deletePost(rootId);

    // Leaving replies behind would orphan them: nothing lists a reply whose
    // parent is gone, so they would linger invisibly forever.
    expect(removed, 3);
    expect(await database.newsDao.count(), 0);
  });

  group('community sharing', () {
    test('isCommunityNews pins which posts offer the share action', () {
      expect(VoicesRepository.isCommunityNews(rowWith(viewIn: '[]')), isFalse);
      expect(
        VoicesRepository.isCommunityNews(
          rowWith(
            viewIn: jsonEncode([
              {'_id': 'team-1', 'section': 'teams'},
            ]),
          ),
        ),
        isFalse,
      );
      expect(
        VoicesRepository.isCommunityNews(
          rowWith(
            viewIn: jsonEncode([
              {'section': 'community', '_id': 'planet@parent'},
            ]),
          ),
        ),
        isTrue,
      );
    });

    test(
      'shareToCommunity appends the community entry and marks edited',
      () async {
        await repository.cacheDocuments([
          {
            '_id': 'post-1',
            'docType': 'message',
            'message': 'From the team',
            'viewIn': [
              {'_id': 'team-1', 'section': 'teams', 'name': 'Team A'},
            ],
          },
        ]);

        expect(
          await repository.shareToCommunity(
            newsId: 'post-1',
            userId: 'user-1',
            planetCode: 'planet',
            parentCode: 'parent',
          ),
          isTrue,
        );

        final row = await repository.getById('post-1');
        final entries = jsonDecode(row!.viewIn!) as List;
        expect(entries, hasLength(2));
        expect(entries.last, {
          'section': 'community',
          '_id': 'planet@parent',
          'sharedDate': 5000,
        });
        expect(row.sharedBy, 'user-1');
        // The only way the share qualifies for the deliberately-narrowed
        // `pendingUploads` list.
        expect(row.isEdited, isTrue);
      },
    );

    test(
      'shareToCommunity fills the first entry name only when it was missing',
      () async {
        await repository.cacheDocuments([
          {
            '_id': 'named',
            'docType': 'message',
            'message': 'm',
            'viewIn': [
              {'_id': 'team-1', 'section': 'teams'},
            ],
          },
          {'_id': 'plain', 'docType': 'message', 'message': 'm'},
        ]);

        await repository.shareToCommunity(
          newsId: 'named',
          userId: 'user-1',
          teamName: 'Team A',
        );
        await repository.shareToCommunity(newsId: 'plain', userId: 'user-1');

        final named =
            jsonDecode((await repository.getById('named'))!.viewIn!) as List;
        expect(named.first, {
          '_id': 'team-1',
          'section': 'teams',
          'name': 'Team A',
        });

        final plain =
            jsonDecode((await repository.getById('plain'))!.viewIn!) as List;
        expect(plain, [
          {'section': 'community', '_id': '', 'sharedDate': 5000},
        ]);
      },
    );

    test(
      'shareToCommunity survives a malformed viewIn instead of failing',
      () async {
        final id = await repository.createPost(
          message: 'm',
          userId: 'user-1',
          userName: 'Ada',
        );
        // createPost writes `[]`; corrupt it behind the repository's back.
        await database.customUpdate(
          'UPDATE news SET view_in = ? WHERE id = ?',
          variables: [Variable('broken'), Variable(id)],
        );

        expect(
          await repository.shareToCommunity(newsId: id, userId: 'user-1'),
          isTrue,
        );
        final entries =
            jsonDecode((await repository.getById(id))!.viewIn!) as List;
        expect(entries, hasLength(1));
        expect(entries.single['section'], 'community');
      },
    );

    group('deletePost', () {
      test(
        'from the community feed un-shares a shared team post, keeps the row',
        () async {
          // Pins upstream f4adebf's community-delete semantics.
          await repository.cacheDocuments([
            {
              '_id': 'shared',
              'docType': 'message',
              'message': 'm',
              'sharedBy': 'user-1',
              'viewIn': [
                {'_id': 'team-1', 'section': 'teams', 'name': 'Enterprise A'},
                {
                  'section': 'community',
                  '_id': 'planet@parent',
                  'sharedDate': 123456789,
                },
              ],
            },
          ]);

          final removed = await repository.deletePost('shared');

          expect(removed, 0);
          final row = await repository.getById('shared');
          expect(row, isNotNull);
          expect(row!.sharedBy, '');
          expect(row.isEdited, isTrue);
          expect(jsonDecode(row.viewIn!), [
            {'_id': 'team-1', 'section': 'teams', 'name': 'Enterprise A'},
          ]);
        },
      );

      test('from a team screen deletes the post and its replies', () async {
        await repository.cacheDocuments([
          {
            '_id': 'team-post',
            'docType': 'message',
            'message': 'm',
            'viewIn': [
              {'_id': 'team-1', 'section': 'teams', 'name': 'Enterprise A'},
              {
                'section': 'community',
                '_id': 'planet@parent',
                'sharedDate': 123456789,
              },
            ],
          },
        ]);

        final removed = await repository.deletePost(
          'team-post',
          teamName: 'Enterprise A',
        );

        expect(removed, 1);
        expect(await repository.getById('team-post'), isNull);
      });

      test(
        'from the community feed deletes a direct community post outright',
        () async {
          await repository.cacheDocuments([
            {
              '_id': 'direct',
              'docType': 'message',
              'message': 'm',
              'viewIn': [
                {'_id': 'planet@parent', 'section': 'community', 'name': ''},
              ],
            },
          ]);

          final removed = await repository.deletePost('direct');

          expect(removed, 1);
          expect(await repository.getById('direct'), isNull);
        },
      );

      test(
        'from the community feed deletes when nothing would be left',
        () async {
          // A post shared to two communities still dies when the un-share
          // filter empties its viewIn.
          await repository.cacheDocuments([
            {
              '_id': 'twice',
              'docType': 'message',
              'message': 'm',
              'viewIn': [
                {'section': 'community', '_id': 'a@b', 'sharedDate': 1},
                {'section': 'community', '_id': 'c@d', 'sharedDate': 2},
              ],
            },
          ]);

          expect(await repository.deletePost('twice'), 1);
          expect(await repository.getById('twice'), isNull);
        },
      );
    });

    test('the community feed orders by shared date, not write time', () async {
      // `getCommunityNews` sorts by `sortDate` descending; pinned by
      // f4adebf's `.sortedByDescending { it.sortDate }`.
      await repository.cacheDocuments([
        {
          '_id': 'old-written',
          'docType': 'message',
          'time': 100,
          'viewableBy': 'community',
          'message': 'written long ago',
        },
        {
          '_id': 'shared-late',
          'docType': 'message',
          'time': 50,
          'message': 'shared recently',
          'viewIn': [
            {'section': 'community', '_id': 'p@c', 'sharedDate': 900},
          ],
        },
      ]);

      final rows = await repository.watchCommunityFeed('p@c').first;

      expect(rows.map((row) => row.id), ['shared-late', 'old-written']);
    });
  });

  test(
    'editing marks the post and preserves unparseable attachments',
    () async {
      final id = await repository.createPost(
        message: 'Original',
        userId: 'user-1',
        userName: 'Ada',
        imageUrls: const ['{"imageUrl":"a.png"}', 'not-json'],
      );

      expect(
        await repository.editPost(
          newsId: id,
          message: 'Updated',
          imagesToRemove: {'a.png'},
          newImages: const ['{"imageUrl":"b.png"}'],
        ),
        isTrue,
      );

      final row = await repository.getById(id);
      expect(row?.message, 'Updated');
      expect(row?.isEdited, isTrue);
      expect(row?.editedTime, 5000);
      // `editPost`'s catch keeps an entry it cannot parse rather than dropping
      // an attachment it does not understand.
      expect(row?.imageUrls, ['not-json', '{"imageUrl":"b.png"}']);
    },
  );

  test('an empty edit is ignored', () async {
    final id = await repository.createPost(
      message: 'Original',
      userId: 'user-1',
      userName: 'Ada',
    );
    expect(await repository.editPost(newsId: id, message: ''), isFalse);
    expect((await repository.getById(id))?.message, 'Original');
  });

  group('labels', () {
    test('adding the same label twice does not duplicate it', () async {
      final id = await repository.createPost(
        message: 'Post',
        userId: 'user-1',
        userName: 'Ada',
      );
      await repository.addLabel(id, 'important');
      await repository.addLabel(id, 'important');
      expect((await repository.getById(id))?.labels, ['important']);

      await repository.removeLabel(id, 'important');
      expect((await repository.getById(id))?.labels, isEmpty);
    });
  });

  group('pending uploads', () {
    test('queues a local post, skips one already delivered', () async {
      await repository.createPost(
        message: 'Local',
        userId: 'user-1',
        userName: 'Ada',
      );
      await repository.cacheDocuments([
        {'_id': 'server-1', 'docType': 'message', 'message': 'Synced'},
      ]);

      final pending = await repository.pendingUploads();
      expect(pending.map((row) => row.message), ['Local']);
    });

    test('re-queues a delivered post once it is edited', () async {
      await repository.cacheDocuments([
        {'_id': 'server-1', 'docType': 'message', 'message': 'Synced'},
      ]);
      expect(await repository.pendingUploads(), isEmpty);

      await repository.editPost(newsId: 'server-1', message: 'Changed');
      expect(await repository.pendingUploads(), hasLength(1));
    });

    test('never queues a guest post', () async {
      // A guest has no CouchDB user document, so the server rejects it.
      await repository.createPost(
        message: 'Guest post',
        userId: 'guest_abc',
        userName: 'Guest',
      );
      expect(await repository.pendingUploads(), isEmpty);
    });

    test('markUploaded clears the pending attachments', () async {
      final id = await repository.createPost(
        message: 'Local',
        userId: 'user-1',
        userName: 'Ada',
        imageUrls: const ['{"imageUrl":"a.png"}'],
      );
      await repository.markUploaded(
        id,
        'server-9',
        '1-abc',
        images: const [
          {'_attachments': 'a.png'},
        ],
      );

      final row = await repository.getById(id);
      expect(row?.docId, 'server-9');
      expect(row?.imageUrls, isEmpty);
      expect(row?.images, contains('a.png'));
      expect(await repository.pendingUploads(), isEmpty);
    });
  });

  group('serialize', () {
    test('omits audience keys when the post is unaddressed', () async {
      final id = await repository.createPost(
        message: 'Post',
        userId: 'user-1',
        userName: 'Ada',
      );
      final payload = VoicesRepository.serialize(
        (await repository.getById(id))!,
      );

      // `addViewIn` writes neither key when there is nothing to write.
      expect(payload.containsKey('viewableId'), isFalse);
      expect(payload.containsKey('viewIn'), isFalse);
      expect(payload.containsKey('_id'), isFalse);
      expect(payload['docType'], 'message');
      expect((payload['news'] as Map)['_id'], isNull);
    });

    test('carries _id and _rev once the post exists on the server', () async {
      await repository.cacheDocuments([
        {
          '_id': 'server-1',
          '_rev': '1-abc',
          'docType': 'message',
          'message': 'Synced',
          'viewableBy': 'teams',
          'viewableId': 'team-1',
        },
      ]);
      final payload = VoicesRepository.serialize(
        (await repository.getById('server-1'))!,
      );

      // Without these CouchDB treats the PUT as a new document and forks it.
      expect(payload['_id'], 'server-1');
      expect(payload['_rev'], '1-abc');
      expect(payload['viewableId'], 'team-1');
      expect(payload['viewableBy'], 'teams');
    });
  });

  test('sync prunes stale server rows but spares undelivered posts', () async {
    await repository.cacheDocuments([
      {'_id': 'stale', 'docType': 'message', 'message': 'Old'},
    ]);
    final localId = await repository.createPost(
      message: 'Not yet sent',
      userId: 'user-1',
      userName: 'Ada',
    );

    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments.single as String;
      if (url.endsWith('limit=0')) {
        return NetworkSuccess<Map<String, dynamic>>({'total_rows': 1});
      }
      return NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'doc': {'_id': 'fresh', 'docType': 'message', 'message': 'Fresh'},
          },
        ],
      });
    });

    const config = ServerConfig(
      serverUrl: 'https://planet.example',
      couchDbUrl: 'https://satellite:1234@planet.example',
      pin: '1234',
    );
    final result = await repository.sync(config: config);

    expect(result, isA<SyncComplete>());
    expect(await repository.getById('stale'), equals(null));
    expect((await repository.getById('fresh'))?.message, 'Fresh');
    // The pruning walk must not take a post the outbox has not delivered yet.
    expect((await repository.getById(localId))?.message, 'Not yet sent');
  });

  group('reaction round trip', () {
    // The end-to-end shape that was broken: toggleReaction writes the column,
    // serialize puts it on the wire, and NewsMapper.fromDoc has to find it
    // again. Serializing and re-mapping in one test is what catches the two
    // ends disagreeing on where the field lives -- neither side is wrong on
    // its own, and each had its own passing test.
    test('survives serialize then map back', () async {
      await repository.cacheDocuments([
        {'_id': 'voice-1', 'docType': 'message', 'message': 'Hi'},
      ]);
      await repository.toggleReaction('voice-1', '\u{1F44D}', 'user-1');

      final row = (await repository.getById('voice-1'))!;
      expect(row.reactions, isNotNull);

      final wire = VoicesRepository.serialize(row);
      final mapped = NewsMapper.fromDoc(wire)!;

      expect(mapped.reactions.value, row.reactions);
    });

    test('a second toggle of the same emoji removes the user', () async {
      await repository.cacheDocuments([
        {'_id': 'voice-1', 'docType': 'message', 'message': 'Hi'},
      ]);
      await repository.toggleReaction('voice-1', '\u{1F44D}', 'user-1');
      await repository.toggleReaction('voice-1', '\u{1F44D}', 'user-1');

      // The emoji key goes with the last user, so the column returns to null
      // rather than holding an empty map.
      expect((await repository.getById('voice-1'))!.reactions, isNull);
    });

    test('switching emoji keeps only one reaction per user', () async {
      await repository.cacheDocuments([
        {'_id': 'voice-1', 'docType': 'message', 'message': 'Hi'},
      ]);
      await repository.toggleReaction('voice-1', '\u{1F44D}', 'user-1');
      await repository.toggleReaction('voice-1', '\u{2764}', 'user-1');

      final decoded =
          jsonDecode((await repository.getById('voice-1'))!.reactions!)
              as Map<String, dynamic>;
      expect(decoded.keys, ['\u{2764}']);
      expect(decoded['\u{2764}'], ['user-1']);
    });
  });
}
