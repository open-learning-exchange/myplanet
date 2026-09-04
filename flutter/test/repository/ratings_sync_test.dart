import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:drift/drift.dart' show Value;
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/ratings_repository.dart';

import '../support/mock_planet_api.dart';

/// The `ratings` walk — `TransactionSyncManager.syncDb("ratings")` plus
/// `RatingsRepositoryImpl.insertRatingsFromSync`.
///
/// Phase 116's D4: the only writer of the table was the local `submit()`, whose
/// one caller always passes the signed-in user. So the read predicate —
/// `type = ? AND item = ?`, deliberately unscoped by user because it computes a
/// community average — could only ever see this device's own rating, and every
/// "average" was a single number the user had typed themselves.
///
/// The documents carry the rater as an embedded `user` object, which is where
/// the Kotlin reads `userId` from and where a base64 profile photo would sit.
void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late RatingsRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = RatingsRepository(api, db.ratingDao, db.userDao);
  });

  tearDown(() => db.close());

  Map<String, dynamic> ratingDoc(
    String id, {
    required String userId,
    String type = 'resource',
    String item = 'resource-1',
    int rate = 4,
    String? comment,
  }) => {
    '_id': id,
    '_rev': '1-abc',
    'time': 1750000000000,
    'title': 'Water pump manual',
    'type': type,
    'item': item,
    'rate': rate,
    'comment': ?comment,
    'parentCode': 'ole',
    'planetCode': 'gua',
    'createdOn': 'ole',
    'user': {
      '_id': userId,
      'name': userId.split(':').last,
      'planetCode': 'gua',
      '_attachments': {
        'img': {'content_type': 'image/png', 'length': 4000, 'stub': true},
      },
    },
  };

  void stubWalk(List<Map<String, dynamic>> docs) {
    when(
      () => api.getJsonObject(
        '$dbUrl/ratings/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': docs.length}),
    );
    when(
      () => api.getJsonObject(
        any(that: contains('ratings/_all_docs?include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          for (final doc in docs) {'id': doc['_id'], 'doc': doc},
        ],
      }),
    );
  }

  test(
    'the average is other people\'s ratings, not only this device\'s',
    () async {
      stubWalk([
        ratingDoc('rating-1', userId: 'org.couchdb.user:ada', rate: 5),
        ratingDoc('rating-2', userId: 'org.couchdb.user:bob', rate: 3),
        ratingDoc('rating-3', userId: 'org.couchdb.user:cy', rate: 4),
      ]);

      await repository.sync(config: config);

      final summary = await repository.summary('resource', 'resource-1', null);
      expect(summary.total, 3);
      expect(summary.average, 4);
    },
  );

  test('reads userId out of the embedded user object', () async {
    stubWalk([ratingDoc('rating-1', userId: 'org.couchdb.user:ada', rate: 5)]);

    await repository.sync(config: config);

    final summary = await repository.summary(
      'resource',
      'resource-1',
      'org.couchdb.user:ada',
    );
    expect(summary.userRating, 5);
  });

  test('a synced rating is not queued for upload', () async {
    // `Ratings.isUpdated` defaults to **true**, so a companion that omitted it
    // would push the whole ratings database straight back to the server as new
    // documents. `insertRatingsFromSync:112` sets it false explicitly.
    stubWalk([ratingDoc('rating-1', userId: 'org.couchdb.user:ada')]);

    await repository.sync(config: config);

    expect(await repository.pendingUploads(), isEmpty);
  });

  test('skips _design documents', () async {
    stubWalk([
      {'_id': '_design/ratings'},
      ratingDoc('rating-1', userId: 'org.couchdb.user:ada'),
    ]);

    await repository.sync(config: config);

    expect(await db.ratingDao.findById('_design/ratings'), isNull);
  });

  test(
    'the rating the user just left is not counted twice when it comes back',
    () async {
      // A rating submitted here keeps its locally-minted id for life —
      // `markRatingUploaded` only clears the dirty flag, it never records the
      // server id. Keying the pulled document on `_id`, as `Rating().apply { id
      // = _id }` does, leaves two rows for one rating and `getAggregate` counts
      // both: a 5-star rating reads as "2 ratings, average 5".
      await repository.submit(
        type: 'resource',
        itemId: 'resource-1',
        title: 'Water pump manual',
        userId: 'org.couchdb.user:ada',
        rate: 5,
      );
      await repository.markUploaded(
        (await repository.pendingUploads()).single.id,
      );

      stubWalk([
        ratingDoc('rating-1', userId: 'org.couchdb.user:ada', rate: 5),
      ]);
      await repository.sync(config: config);

      final summary = await repository.summary(
        'resource',
        'resource-1',
        'org.couchdb.user:ada',
      );
      expect(summary.total, 1);
      expect(summary.userRating, 5);
    },
  );

  test('an unsent rating keeps its rate and stays queued', () async {
    // The user re-rated offline; the server still holds the old value. Taking
    // the document's rate and clearing `isUpdated` — which the Kotlin does —
    // discards the new rating and stops it ever uploading.
    await repository.submit(
      type: 'resource',
      itemId: 'resource-1',
      title: 'Water pump manual',
      userId: 'org.couchdb.user:ada',
      rate: 2,
      comment: 'The diagram on page 4 is wrong',
    );

    stubWalk([
      ratingDoc(
        'rating-1',
        userId: 'org.couchdb.user:ada',
        rate: 5,
        comment: 'Great',
      ),
    ]);
    await repository.sync(config: config);

    final pending = await repository.pendingUploads();
    expect(pending.length, 1);
    expect(pending.single.rate, 2);
    expect(pending.single.comment, 'The diagram on page 4 is wrong');
    // …and it now carries the rev, so the upload is a PUT rather than a second
    // POST of the same rating.
    expect(pending.single.couchId, 'rating-1');
    expect(pending.single.rev, '1-abc');
  });

  test('two server ratings for one item are both counted', () async {
    // Both documents match the same unsynced local row on
    // `(type, item, userId)`, so keying both companions on that row collapsed
    // them: the batch's last write won and one person's rating vanished from
    // the average. The match is consumed by the first document; the second
    // takes its CouchDB id as its key, which is what the Kotlin stores.
    await repository.submit(
      type: 'resource',
      itemId: 'resource-1',
      title: 'Water pump manual',
      userId: 'org.couchdb.user:ada',
      rate: 5,
    );
    await repository.markUploaded(
      (await repository.pendingUploads()).single.id,
    );

    stubWalk([
      ratingDoc('rating-1', userId: 'org.couchdb.user:ada', rate: 5),
      ratingDoc('rating-2', userId: 'org.couchdb.user:ada', rate: 1),
    ]);
    await repository.sync(config: config);

    final summary = await repository.summary('resource', 'resource-1', null);
    expect(summary.total, 2);
    expect(summary.average, 3);
  });

  test(
    'a member registered on this device sees their own rating as theirs',
    () async {
      // A rating document names its rater by CouchDB id; `submit` stores
      // `session.user.id`, and for a member registered offline those differ.
      // Storing the raw id hides the rating from `userRating` *and* defeats the
      // `(type, item, userId)` match, so the walk duplicates it.
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: '1756900000000',
          couchId: const Value('org.couchdb.user:ada'),
          name: const Value('ada'),
        ),
      );
      await repository.submit(
        type: 'resource',
        itemId: 'resource-1',
        title: 'Water pump manual',
        userId: '1756900000000',
        rate: 5,
      );
      await repository.markUploaded(
        (await repository.pendingUploads()).single.id,
      );

      stubWalk([
        ratingDoc('rating-1', userId: 'org.couchdb.user:ada', rate: 5),
      ]);
      await repository.sync(config: config);

      final summary = await repository.summary(
        'resource',
        'resource-1',
        '1756900000000',
      );
      expect(summary.total, 1);
      expect(summary.userRating, 5);
    },
  );

  test('never prunes: a rating the walk did not list survives', () async {
    await repository.submit(
      type: 'resource',
      itemId: 'resource-9',
      title: 'Seed catalogue',
      userId: 'org.couchdb.user:ada',
      rate: 3,
    );

    stubWalk([ratingDoc('rating-1', userId: 'org.couchdb.user:bob')]);
    await repository.sync(config: config);

    expect((await repository.summary('resource', 'resource-9', null)).total, 1);
  });

  group('user and createdOn (schema v46)', () {
    // `insertRatingsFromSync` writes both (`RatingsRepositoryImpl.kt:114-118`)
    // and `Rating.serializeRating` re-emits them. Before v46 the port had no
    // column for either, so the sync-in dropped them.

    test('stores the rater document and createdOn off the page', () async {
      stubWalk([ratingDoc('rating-1', userId: 'org.couchdb.user:ada')]);
      await repository.sync(config: config);

      final row = (await db.ratingDao.findById('rating-1'))!;
      expect(row.createdOn, 'ole');
      final user = jsonDecode(row.user!) as Map<String, dynamic>;
      expect(user['_id'], 'org.couchdb.user:ada');
      expect(user['name'], 'ada');
    });

    test('drops the embedded _attachments before storing', () async {
      // `RatingsRepositoryImpl.kt:98-102`: the rater's profile photo arrives
      // base64-encoded inside the rating document and is removed before the
      // blob is persisted. The Kotlin's stated reason is SQLite's ~2MB
      // CursorWindow, but the bytes are equally useless here and equally
      // costly to carry into an upload payload.
      stubWalk([ratingDoc('rating-1', userId: 'org.couchdb.user:ada')]);
      await repository.sync(config: config);

      final row = (await db.ratingDao.findById('rating-1'))!;
      expect(row.user, isNot(contains('_attachments')));
    });

    test('the rater credentials never reach the local database', () async {
      // `UserEntity.serialize()` writes `derived_key`, `salt` and
      // `password_scheme` into every document it builds (`UserEntity.kt:66-70`)
      // — and a plaintext `password` for a member registered offline
      // (`:61-65`) — so a `ratings` document carries the rater's password
      // verifier. Storing the object verbatim would keep a copy for every
      // rater on the planet in this device's SQLite file, one per rating row,
      // for a value nothing ever reads back.
      final doc = ratingDoc('rating-1', userId: 'org.couchdb.user:ada');
      doc['user'] = <String, dynamic>{
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
        'derived_key': 'deadbeef',
        'salt': 'a1b2',
        'password_scheme': 'pbkdf2',
        'password': 'hunter2',
      };
      stubWalk([doc]);
      await repository.sync(config: config);

      final stored = (await db.ratingDao.findById('rating-1'))!.user!;
      for (final secret in [
        'derived_key',
        'salt',
        'password_scheme',
        'password',
      ]) {
        expect(stored, isNot(contains(secret)), reason: secret);
      }
      // The fields that attribute the rating still survive.
      expect(jsonDecode(stored)['name'], 'ada');
    });

    test('a document with no user object stores an empty one', () async {
      // `JsonUtils.getJsonObject` returns an empty object for a missing key,
      // and `gson.toJson` of it is `"{}"`.
      final doc = ratingDoc('rating-1', userId: 'org.couchdb.user:ada')
        ..remove('user');
      stubWalk([doc]);
      await repository.sync(config: config);

      expect((await db.ratingDao.findById('rating-1'))!.user, '{}');
    });
  });
}
