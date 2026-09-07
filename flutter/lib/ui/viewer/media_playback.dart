/// Port of the playback-progress and playback-speed half of
/// `ui/viewer/ResourceViewerViewModel.kt` and the `media_*` accessors
/// `services/SharedPrefManager.kt` gained alongside it (upstream `ae20602`).
///
/// The policy is separated from the players because the players cannot be
/// driven in a widget test — `video_player` needs a platform view — while the
/// arithmetic is where the behaviour actually lives.

library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/prefs/planet_prefs.dart';
import '../../providers/app_providers.dart';

/// `ResourceViewerViewModel.NEAR_END_THRESHOLD_MS`.
const int nearEndThresholdMs = 2000;

/// The speeds `showPlaybackSpeedDialog` offers, in its order.
const List<double> playbackSpeeds = <double>[0.75, 1.0, 1.25, 1.5, 2.0];

/// Port of `ResourceViewerViewModel.calculateEffectivePlaybackPosition`.
///
/// Resuming is suppressed for a resource watched to (or nearly to) the end:
/// storing that position would reopen the media at its final frame and look
/// like a player that refuses to play. A non-positive position and an unknown
/// duration are both "start from the beginning" — note the near-end test is
/// skipped entirely when `durationMs <= 0`, so a stream whose length the player
/// has not resolved yet keeps its real position rather than being zeroed.
int effectivePlaybackPosition(int currentPositionMs, int durationMs) {
  if (currentPositionMs <= 0) return 0;
  if (durationMs > 0 && durationMs - currentPositionMs < nearEndThresholdMs) {
    return 0;
  }
  return currentPositionMs;
}

/// Port of the write throttle in `ResourceViewerFragment.saveCurrentPlaybackProgress`.
///
/// [lastSavedMs] is null before the first write (Kotlin's `-1L` sentinel).
/// Kotlin writes when nothing has been written yet, when the position has moved
/// at least [nearEndThresholdMs], or whenever the effective position is 0 —
/// that last clause is what lets a finished resource clear its entry even
/// though the previous write was moments earlier.
bool shouldPersistPlaybackPosition({
  required int? lastSavedMs,
  required int effectiveMs,
}) {
  if (lastSavedMs == null) return true;
  if (effectiveMs == 0) return true;
  return (effectiveMs - lastSavedMs).abs() >= nearEndThresholdMs;
}

/// Port of `showPlaybackSpeedDialog`'s
/// `indexOfFirst { abs(it - currentSpeed) < 0.05f }`, falling back to index 1
/// (1.0x) when the stored speed matches no offered option.
int playbackSpeedIndex(double currentSpeed) {
  final index = playbackSpeeds.indexWhere(
    (speed) => (speed - currentSpeed).abs() < 0.05,
  );
  return index == -1 ? 1 : index;
}

/// The `SharedPrefManager` half of the same feature, behind a seam so the
/// players take a store rather than reaching for preferences themselves.
class MediaPlaybackStore {
  const MediaPlaybackStore(this._prefs);

  final PlanetPrefs _prefs;

  /// `getMediaKey()` is `resourceId ?: filePath` in Kotlin. The port always has
  /// a resource row, so its id is the key.
  int positionFor(String resourceKey) =>
      _prefs.mediaPlaybackPosition(resourceKey);

  Future<void> savePosition(String resourceKey, int positionMs) =>
      _prefs.setMediaPlaybackPosition(resourceKey, positionMs);

  double get speed => _prefs.mediaPlaybackSpeed;

  Future<void> saveSpeed(double speed) => _prefs.setMediaPlaybackSpeed(speed);
}

final mediaPlaybackStoreProvider = Provider<MediaPlaybackStore>(
  (ref) => MediaPlaybackStore(ref.watch(planetPrefsProvider)),
);
