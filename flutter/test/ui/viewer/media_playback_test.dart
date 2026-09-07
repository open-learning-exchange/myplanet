import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/ui/viewer/media_playback.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Port of `ResourceViewerViewModel`'s playback-progress companion and the
/// `media_*` accessors in `SharedPrefManager` (upstream `ae20602`).
///
/// The players themselves want a platform view no widget test can serve, so
/// the arithmetic and the persistence are covered here; the toolbar action is
/// covered in `resource_viewer_screen_test.dart`.
void main() {
  group('effectivePlaybackPosition', () {
    test('a position before the start is the start', () {
      expect(effectivePlaybackPosition(0, 60000), 0);
      expect(effectivePlaybackPosition(-1, 60000), 0);
    });

    test('a position mid-resource is kept', () {
      expect(effectivePlaybackPosition(12345, 60000), 12345);
    });

    test('a position within two seconds of the end resets to the start', () {
      // Kotlin: `duration - currentPos < NEAR_END_THRESHOLD_MS`. Reopening a
      // finished resource at its final frame looks like a player that refuses
      // to play, which is what the threshold exists to prevent.
      expect(effectivePlaybackPosition(59001, 60000), 0);
      expect(effectivePlaybackPosition(60000, 60000), 0);
    });

    test('exactly two seconds from the end is still kept', () {
      // The comparison is `<`, not `<=`, so the boundary itself survives.
      expect(effectivePlaybackPosition(58000, 60000), 58000);
    });

    test('an unresolved duration leaves the position alone', () {
      // `duration > 0L` guards the near-end test, so a player that has not
      // reported a length yet keeps the real position rather than losing it.
      expect(effectivePlaybackPosition(12345, 0), 12345);
      expect(effectivePlaybackPosition(12345, -1), 12345);
    });
  });

  group('shouldPersistPlaybackPosition', () {
    test('the first write always lands', () {
      // Kotlin's `lastSavedPositionMs == -1L` sentinel.
      expect(
        shouldPersistPlaybackPosition(lastSavedMs: null, effectiveMs: 10),
        isTrue,
      );
    });

    test('a move under two seconds is throttled away', () {
      expect(
        shouldPersistPlaybackPosition(lastSavedMs: 10000, effectiveMs: 11000),
        isFalse,
      );
      expect(
        shouldPersistPlaybackPosition(lastSavedMs: 10000, effectiveMs: 9000),
        isFalse,
      );
    });

    test('a move of two seconds or more is written', () {
      expect(
        shouldPersistPlaybackPosition(lastSavedMs: 10000, effectiveMs: 12000),
        isTrue,
      );
      expect(
        shouldPersistPlaybackPosition(lastSavedMs: 10000, effectiveMs: 8000),
        isTrue,
      );
    });

    test('a reset to the start is never throttled', () {
      // The `effectivePosition == 0L` clause. Without it a resource finished
      // moments after the last write would keep its stale position, and the
      // next open would resume at the end.
      expect(
        shouldPersistPlaybackPosition(lastSavedMs: 500, effectiveMs: 0),
        isTrue,
      );
    });
  });

  group('playbackSpeedIndex', () {
    test('the offered speeds are the Kotlin ones, in order', () {
      expect(playbackSpeeds, [0.75, 1.0, 1.25, 1.5, 2.0]);
    });

    test('each offered speed selects itself', () {
      for (var index = 0; index < playbackSpeeds.length; index++) {
        expect(playbackSpeedIndex(playbackSpeeds[index]), index);
      }
    });

    test('a near match inside the tolerance selects that speed', () {
      // `abs(it - currentSpeed) < 0.05f` — a stored float that does not round
      // trip exactly still selects its option.
      expect(playbackSpeedIndex(1.24), 2);
    });

    test('a speed matching nothing falls back to 1.0x', () {
      // Kotlin: `if (selectedIndex == -1) selectedIndex = 1`.
      expect(playbackSpeedIndex(3.0), 1);
      expect(playbackSpeeds[playbackSpeedIndex(3.0)], 1.0);
    });
  });

  group('MediaPlaybackStore', () {
    late MediaPlaybackStore store;

    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      store = MediaPlaybackStore(
        PlanetPrefs(await SharedPreferences.getInstance()),
      );
    });

    test('an unseen resource starts at the beginning', () {
      expect(store.positionFor('res-1'), 0);
    });

    test('a saved position is read back', () async {
      await store.savePosition('res-1', 42000);
      expect(store.positionFor('res-1'), 42000);
    });

    test('positions are per resource', () async {
      await store.savePosition('res-1', 42000);
      await store.savePosition('res-2', 7000);
      expect(store.positionFor('res-1'), 42000);
      expect(store.positionFor('res-2'), 7000);
    });

    test('a non-positive position removes the entry', () async {
      await store.savePosition('res-1', 42000);
      await store.savePosition('res-1', 0);
      expect(store.positionFor('res-1'), 0);
      // Kotlin deletes rather than storing a zero, so a device that has
      // finished many resources does not accumulate one dead key per resource.
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.containsKey('media_progress_res-1'), isFalse);
    });

    test('the speed defaults to 1.0 and persists once set', () async {
      expect(store.speed, 1.0);
      await store.saveSpeed(1.5);
      expect(store.speed, 1.5);
    });

    test('the speed is one global entry, not per resource', () async {
      await store.saveSpeed(2.0);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getDouble('media_playback_speed'), 2.0);
    });
  });
}
