import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/providers/voices_provider.dart';

/// The community feed matches a post's `viewIn[]._id` against the viewer, and
/// the two sides have to name the viewer the same way.
///
/// Kotlin's `VoicesFragment.getUserIdentifier()` (`:157-165`) is
/// `"${planetCode}@${parentCode}"`, which is exactly what
/// `shareNewsToCommunity` writes into the entry
/// (`VoicesRepositoryImpl.kt:212-220`). The port's own
/// `VoicesRepository.shareToCommunity` writes `'$planetCode@$parentCode'` too
/// (`voices_repository.dart:354-360`) — and `communityFeedProvider` was
/// filtering on `user.couchId ?? user.id`, the CouchDB user id
/// (`org.couchdb.user:<name>`), so the two halves of the port disagreed about
/// the same key and the feed could never show a shared post.
///
/// The comment that justified it — "`viewIn` entries carry server ids" — was
/// true and beside the point: they carry a *planet* id, not a *user* id.
void main() {
  test('the viewer is named by planet, not by user', () {
    expect(
      communityViewerIdentifier(planetCode: 'lea', parentCode: 'ole'),
      'lea@ole',
    );
  });

  test('a blank half is kept, because "@" is the planet-wide wildcard', () {
    // `isVisibleToUser` treats an empty or `"@"` id as "everyone" on both
    // sides, which is the Planet convention for a planet-wide share. Kotlin
    // builds the same string from possibly-blank halves before falling back.
    expect(
      communityViewerIdentifier(planetCode: '', parentCode: 'ole'),
      '@ole',
    );
    expect(
      communityViewerIdentifier(planetCode: 'lea', parentCode: ''),
      'lea@',
    );
  });

  test(
    'both halves blank yields the wildcard rather than a bare separator',
    () {
      expect(communityViewerIdentifier(planetCode: '', parentCode: ''), '@');
    },
  );

  test('a null code reads as blank', () {
    expect(communityViewerIdentifier(planetCode: null, parentCode: null), '@');
  });
}
