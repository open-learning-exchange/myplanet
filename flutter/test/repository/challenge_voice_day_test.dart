import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/voices_repository.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Phase 147, Job 5 (`PHASE_142_NOTES.md` item 4).
///
/// The challenge dialog's "post five community voices" task counts the
/// *distinct days* a user posted. Kotlin counts them in SQL with
/// `strftime('%Y-%m-%d', time / 1000, 'unixepoch', 'localtime')`
/// (`NewsDao.kt:61,68`) — the **device's** day. The port's `_formatDate` built
/// the date from `DateTime.fromMillisecondsSinceEpoch(millis, isUtc: true)`,
/// so a post made near midnight was attributed to a different day by the two
/// apps, and two posts either side of local midnight collapsed into one bucket
/// where Kotlin counts two.
///
/// **Why the offset is injected rather than read from the machine.** CI runs
/// in UTC, where the local and UTC bucketings are identical and *no* test
/// could fail on the defect. `VoicesRepository.deviceUtcOffset` is the seam
/// that lets a test pin a non-UTC device; in production it is the real offset
/// at that instant, so it follows DST the way `'localtime'` does.
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

  /// Kathmandu. Chosen deliberately: a 45-minute offset catches an
  /// implementation that rounds to whole hours as well as one that ignores the
  /// offset entirely, and Nepali is one of the app's five shipped locales.
  const nepal = Duration(hours: 5, minutes: 45);

  void pinDevice(Duration offset) {
    VoicesRepository.deviceUtcOffset = (_) => offset;
    addTearDown(VoicesRepository.resetDeviceUtcOffset);
  }

  /// A community post at [millis], written the way the composer writes one so
  /// `isCommunityNews` matches it — a fixture that hand-set `viewIn` would be
  /// asserting about a document shape rather than about the writer.
  Future<void> postAt(int millis) async {
    clock = DateTime.fromMillisecondsSinceEpoch(millis, isUtc: true);
    await repository.createPost(
      message: 'The well is dry',
      userId: 'org.couchdb.user:ada',
      userName: 'ada',
      messageType: 'sync',
      viewInId: 'learning@earth',
      viewInSection: 'community',
    );
  }

  Future<List<String>> datesAround(int millis) async {
    final dates = await repository.getCommunityVoiceDates(
      millis - 86400000,
      millis + 86400000,
      'org.couchdb.user:ada',
    );
    return dates..sort();
  }

  /// 2026-09-08T19:30:00Z. In UTC that is the 8th; in Kathmandu it is
  /// 2026-09-09T01:15, the **9th** — the day Kotlin credits it to.
  const eveningUtc = 1788895800000;

  test('a post after local midnight is credited to the local day', () async {
    pinDevice(nepal);
    await postAt(eveningUtc);

    expect(
      await datesAround(eveningUtc),
      ['2026-09-09'],
      reason:
          "Kotlin's `strftime(..., 'localtime')` buckets this post on the 9th; "
          'formatting the UTC instant buckets it on the 8th',
    );
  });

  test('two posts either side of local midnight are two days', () async {
    pinDevice(nepal);
    // 18:00Z on the 8th is 23:45 local on the 8th; 19:30Z is 01:15 local on
    // the 9th. In UTC both fall on the 8th, so the tally reads one day where
    // Kotlin reads two — and it is the *count* the challenge threshold
    // compares against.
    await postAt(1788890400000);
    await postAt(eveningUtc);

    expect(await datesAround(eveningUtc), ['2026-09-08', '2026-09-09']);
  });

  test('a negative offset moves the day back, not forward', () async {
    // Lima, UTC-5: 2026-09-08T02:00Z is still the **7th** locally. The
    // mirror-image case, so a fix that applied the offset with the wrong sign
    // and happened to pass the Kathmandu cases fails here.
    pinDevice(const Duration(hours: -5));
    await postAt(1788832800000);

    expect(await datesAround(1788832800000), ['2026-09-07']);
  });
}
