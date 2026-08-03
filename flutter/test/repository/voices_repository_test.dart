import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
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

    test('a post is visible to a user named in viewIn', () {
      final row = rowWith(
        viewIn: jsonEncode([
          {'_id': 'USER-1', 'section': 'teams'},
        ]),
      );
      expect(VoicesRepository.isVisibleToUser(row, 'user-1'), isTrue);
      expect(VoicesRepository.isVisibleToUser(row, 'user-2'), isFalse);
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
}
