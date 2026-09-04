import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/utils/version_utils.dart';

void main() {
  group('compareVersions', () {
    test('orders by each numeric segment', () {
      expect(
        VersionUtils.compareVersions('0.62.97', '0.62.96'),
        greaterThan(0),
      );
      expect(VersionUtils.compareVersions('0.62.96', '0.62.97'), lessThan(0));
      expect(VersionUtils.compareVersions('0.62.97', '0.62.97'), 0);
      expect(VersionUtils.compareVersions('1.0.0', '0.99.99'), greaterThan(0));
    });

    test('treats a longer version as greater when the prefix matches', () {
      expect(
        VersionUtils.compareVersions('0.62.97.1', '0.62.97'),
        greaterThan(0),
      );
    });

    test('tolerates a leading v and the -lite flavour suffix', () {
      expect(VersionUtils.compareVersions('v0.62.97', '0.62.97'), 0);
      expect(VersionUtils.compareVersions('0.62.97-lite', '0.62.97'), 0);
    });

    /// The Kotlin throws on a malformed segment; the port sorts it as 0 so the
    /// configuration check degrades instead of crashing.
    test('treats an unparseable segment as zero', () {
      expect(VersionUtils.compareVersions('0.62.x', '0.62.0'), 0);
    });
  });

  group('isVersionAllowed', () {
    test('allows an equal or newer build', () {
      expect(VersionUtils.isVersionAllowed('0.62.97', '0.62.97'), isTrue);
      expect(VersionUtils.isVersionAllowed('0.62.98', '0.62.97'), isTrue);
    });

    test('blocks a build older than the server minimum', () {
      expect(VersionUtils.isVersionAllowed('0.62.96', '0.62.97'), isFalse);
    });
  });

  group('parseApkVersionString', () {
    test('strips v and dots and drops one leading zero', () {
      expect(VersionUtils.parseApkVersionString('v0.62.97'), 6297);
      expect(VersionUtils.parseApkVersionString('0.62.97'), 6297);
    });

    test('returns null for empty or unparseable input', () {
      expect(VersionUtils.parseApkVersionString(null), isNull);
      expect(VersionUtils.parseApkVersionString(''), isNull);
      expect(VersionUtils.parseApkVersionString('not-a-version'), isNull);
    });
  });
}
