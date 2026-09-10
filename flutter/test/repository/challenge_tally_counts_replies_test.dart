import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/voices_repository.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Phase 150, Job 2 (`PHASE_147_NOTES.md` item 1).
///
/// The challenge dialog's "post five community voices" task counts the
/// distinct days the user posted to the community. Kotlin counts them with
/// `countDistinctCommunityVoiceDates` / `…ForUser` (`NewsDao.kt:60-73`), whose
/// only predicates are the time window, the optional `userId` and
/// `viewIn LIKE '%"section":"community"%'`. There is **no** `replyTo`
/// predicate, and `postReply` copies the parent's `viewIn` verbatim
/// (`VoicesRepositoryImpl.kt:319`) — so in Kotlin, answering a community voice
/// makes that day count.
///
/// The port's two DAO queries carried an extra `_isTopLevel(r)` conjunct, so a
/// day on which the user only replied was dropped. The direction matters: the
/// port **under**-counted, telling a learner they had not done something they
/// had.
///
/// Both halves are driven through the production writers — `createPost` and
/// `postReply` — rather than hand-built rows, because the whole defect lives
/// in whether a *reply's* stored shape satisfies the tally, and a fixture that
/// set `viewIn` itself would be asserting about a document shape instead.
void main() {
  late AppDatabase db;
  late VoicesRepository repository;
  late DateTime clock;
  var idCounter = 0;

  setUp(() {
    db = AppDatabase.memory();
    idCounter = 0;
    clock = DateTime.utc(2026);
    repository = VoicesRepository(
      _MockPlanetApi(),
      db.newsDao,
      now: () => clock,
      createId: () => 'local-${++idCounter}',
    );
  });
  tearDown(() => db.close());

  const ada = 'org.couchdb.user:ada';
  const grace = 'org.couchdb.user:grace';

  /// 2026-09-01T00:00:00Z and the two days after it. The window below spans
  /// all three, and the device offset is left at the machine's so this file
  /// says nothing about day bucketing — `challenge_voice_day_test.dart` owns
  /// that, and these instants sit at midday UTC to stay on one day in every
  /// zone the app ships a locale for.
  const day1 = 1788264000000;
  const day2 = day1 + 86400000;

  Future<String> postAt(int millis, {String userId = ada}) async {
    clock = DateTime.fromMillisecondsSinceEpoch(millis, isUtc: true);
    return repository.createPost(
      message: 'The well is dry',
      userId: userId,
      userName: 'ada',
      messageType: 'sync',
      viewInId: 'learning@earth',
      viewInSection: 'community',
    );
  }

  Future<void> replyAt(
    int millis,
    String parentId, {
    String userId = ada,
  }) async {
    clock = DateTime.fromMillisecondsSinceEpoch(millis, isUtc: true);
    final id = await repository.postReply(
      parentId: parentId,
      message: 'Ours too',
      userId: userId,
      userName: 'ada',
    );
    expect(id, isNotNull, reason: 'the reply must actually be written');
  }

  Future<List<String>> dates({String? userId}) async {
    final found = await repository.getCommunityVoiceDates(
      day1 - 86400000,
      day2 + 86400000,
      userId,
    );
    return found..sort();
  }

  test('a day the user only replied on still counts', () async {
    final parent = await postAt(day1);
    await replyAt(day2, parent);

    expect(
      await dates(userId: ada),
      ['2026-09-01', '2026-09-02'],
      reason:
          'Kotlin has no replyTo predicate and postReply copies the parent viewIn, '
          'so the reply day counts; the port dropped it',
    );
  });

  test(
    'a reply to a community voice counts in the whole-community tally',
    () async {
      // The `userId`-less arm is a different SQL statement in both apps, so it
      // needs its own case: the reply is written by a *different* user, which is
      // the only thing distinguishing this from the test above.
      final parent = await postAt(day1, userId: ada);
      await replyAt(day2, parent, userId: grace);

      expect(
        await dates(),
        ['2026-09-01', '2026-09-02'],
        reason:
            'countDistinctCommunityVoiceDates has no replyTo predicate either',
      );
    },
  );

  test('a reply to a team post is still not a community voice', () async {
    // The conjunct that must survive. `isCommunityNews` is what excludes this,
    // not `_isTopLevel` — so removing the top-level filter may not widen the
    // tally to non-community posts.
    clock = DateTime.fromMillisecondsSinceEpoch(day1, isUtc: true);
    final parent = await repository.createPost(
      message: 'Standup notes',
      userId: ada,
      userName: 'ada',
      messageType: 'sync',
      viewInId: 'team-1',
      viewInSection: 'teams',
    );
    await replyAt(day2, parent);

    expect(
      await dates(userId: ada),
      isEmpty,
      reason: 'a team thread never satisfies the viewIn community filter',
    );
  });
}
