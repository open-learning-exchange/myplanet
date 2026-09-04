import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import '../support/mock_planet_api.dart';

void main() {
  late AppDatabase database;
  late RatingsRepository repository;
  var id = 0;

  setUp(() {
    database = AppDatabase.memory();
    id = 0;
    repository = RatingsRepository(
      MockPlanetApi(),
      database.ratingDao,
      database.userDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(1234),
      createId: () => 'rating-${id++}',
    );
  });
  tearDown(() => database.close());

  test('aggregates ratings and updates one rating per user and item', () async {
    await repository.submit(
      type: 'course',
      itemId: 'course-1',
      title: 'Course',
      userId: 'user-1',
      rate: 4,
      comment: ' Useful ',
    );
    await repository.submit(
      type: 'course',
      itemId: 'course-1',
      title: 'Course',
      userId: 'user-2',
      rate: 2,
    );

    var summary = await repository
        .watchSummary('course', 'course-1', 'user-1')
        .first;
    expect(summary.average, 3);
    expect(summary.total, 2);
    expect(summary.userRating, 4);
    expect(summary.userComment, 'Useful');

    await repository.submit(
      type: 'course',
      itemId: 'course-1',
      title: 'Course',
      userId: 'user-1',
      rate: 5,
      comment: '',
    );
    summary = await repository
        .watchSummary('course', 'course-1', 'user-1')
        .first;
    expect(summary.average, 3.5);
    expect(summary.total, 2, reason: 'an edit must not create another row');
    expect(summary.userComment, isNull);
  });

  test('clamps ratings and tracks non-guest pending uploads', () async {
    await repository.submit(
      type: 'resource',
      itemId: 'resource-1',
      title: 'Resource',
      userId: 'user-1',
      rate: 99,
    );
    await repository.submit(
      type: 'resource',
      itemId: 'resource-1',
      title: 'Resource',
      userId: 'guest-user',
      rate: 3,
    );

    final summary = await repository
        .watchSummary('resource', 'resource-1', 'user-1')
        .first;
    expect(summary.userRating, 5);
    final pending = await repository.pendingUploads();
    expect(pending.map((row) => row.userId), ['user-1']);
    expect(await repository.markUploaded(pending.single.id), 1);
    expect(await repository.pendingUploads(), isEmpty);
  });

  test(
    'summary() one-shot agrees with watchSummary and reports no user row',
    () async {
      // Two ratings, neither from user-1.
      await repository.submit(
        type: 'course',
        itemId: 'course-1',
        title: 'Course',
        userId: 'user-2',
        rate: 4,
      );
      await repository.submit(
        type: 'course',
        itemId: 'course-1',
        title: 'Course',
        userId: 'user-3',
        rate: 2,
      );

      final oneShot = await repository.summary('course', 'course-1', 'user-1');
      final streamed = await repository
          .watchSummary('course', 'course-1', 'user-1')
          .first;

      expect(oneShot.total, 2);
      expect(oneShot.average, 3);
      expect(oneShot.userRating, isNull, reason: 'user-1 has not rated');
      // The one-shot and stream must agree.
      expect(oneShot.total, streamed.total);
      expect(oneShot.average, streamed.average);
      expect(oneShot.userRating, streamed.userRating);

      // Once user-1 rates, the one-shot picks up their row.
      await repository.submit(
        type: 'course',
        itemId: 'course-1',
        title: 'Course',
        userId: 'user-1',
        rate: 5,
      );
      final after = await repository.summary('course', 'course-1', 'user-1');
      expect(after.userRating, 5);
      expect(after.total, 3);
    },
  );

  test('a submitted rating snapshots its rater and parent code', () async {
    // `RatingsRepositoryImpl.setRatingData` writes
    // `createdOn = resolvedUser.parentCode` and
    // `user = gson.toJson(resolvedUser.serialize())` at submit time, so the
    // document uploads the rater as they were when they rated.
    await database.userDao.upsert(
      UsersCompanion.insert(
        id: 'user-1',
        couchId: const Value('org.couchdb.user:ada'),
        name: const Value('ada'),
        planetCode: const Value('gua'),
        parentCode: const Value('ole'),
      ),
    );

    await repository.submit(
      type: 'course',
      itemId: 'course-1',
      title: 'Course',
      userId: 'user-1',
      rate: 4,
      parentCode: 'ole',
      planetCode: 'gua',
    );

    final row = (await database.ratingDao.findById('rating-0'))!;
    expect(row.createdOn, 'ole');
    final user = jsonDecode(row.user!) as Map<String, dynamic>;
    expect(user['_id'], 'org.couchdb.user:ada');
    expect(user['name'], 'ada');
  });
}
