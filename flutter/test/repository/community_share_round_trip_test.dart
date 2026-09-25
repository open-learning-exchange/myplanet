import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/repository/voices_repository.dart';

/// Share a voice with the community, then read the community feed back.
///
/// The two halves are in different files — `VoicesRepository.shareToCommunity`
/// writes the `viewIn` entry, `communityFeedProvider` supplies the identifier
/// the feed filters on — and each half had a passing test. Nothing ran them
/// together, and they disagreed: the writer used
/// `'<planetCode>@<parentCode>'` (as Kotlin's `shareNewsToCommunity` does), the
/// reader used the CouchDB user id. So no shared post could ever appear, and
/// both tests stayed green.
///
/// Same shape as Phase 74's reactions round trip, where `serializeNews` wrote
/// into a nested object and `NewsMapper.fromDoc` read the top level. **When two
/// files have to agree on a key, the test has to span both.**
class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late VoicesRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = VoicesRepository(
      MockPlanetApi(),
      database.newsDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(5000),
      createId: () => 'local-1',
    );
  });
  tearDown(() => database.close());

  test('a shared voice reaches the sharer own community feed', () async {
    final postedId = await repository.createPost(
      message: 'Rains are late this year',
      userId: 'org.couchdb.user:ada',
      userName: 'ada',
    );

    final shared = await repository.shareToCommunity(
      newsId: postedId,
      userId: 'org.couchdb.user:ada',
      planetCode: 'lea',
      parentCode: 'ole',
    );
    expect(shared, isTrue);

    // Exactly the identifier `communityFeedProvider` hands the repository.
    final viewer = communityViewerIdentifier(
      planetCode: 'lea',
      parentCode: 'ole',
    );
    final feed = await repository.watchCommunityFeed(viewer).first;

    expect(
      feed.map((row) => row.message),
      ['Rains are late this year'],
      reason:
          'the feed filtered on a different identifier than shareToCommunity '
          'writes, so a shared post was invisible to the person who shared it',
    );
  });

  test('a voice shared on another planet stays out of this feed', () async {
    final postedId = await repository.createPost(
      message: 'Elsewhere',
      userId: 'org.couchdb.user:bob',
      userName: 'bob',
    );
    await repository.shareToCommunity(
      newsId: postedId,
      userId: 'org.couchdb.user:bob',
      planetCode: 'other',
      parentCode: 'ole',
    );

    final feed = await repository
        .watchCommunityFeed(
          communityViewerIdentifier(planetCode: 'lea', parentCode: 'ole'),
        )
        .first;

    expect(
      feed,
      isEmpty,
      reason:
          'the identifier has to discriminate, or matching it proves nothing',
    );
  });
}
