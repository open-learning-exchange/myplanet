/// Port of the version-comparison half of `utils/VersionUtils.kt`.
///
/// Only the pieces the configuration flow needs are ported here; the
/// `getVersionCode` / `getAndroidId` helpers are platform lookups that belong in
/// a platform channel wrapper, not in this pure-Dart layer.
class VersionUtils {
  const VersionUtils._();

  static bool isVersionAllowed(String currentVersion, String minApkVersion) =>
      compareVersions(currentVersion, minApkVersion) >= 0;

  /// Mirrors `VersionUtils.compareVersions`: dotted numeric compare, tolerating a
  /// leading `v` on either side and the `-lite` flavor suffix on the left.
  static int compareVersions(String version1, String version2) {
    final parts1 = _split(_stripSuffix(version1, '-lite'));
    final parts2 = _split(version2);

    final shared = parts1.length < parts2.length
        ? parts1.length
        : parts2.length;
    for (var i = 0; i < shared; i++) {
      if (parts1[i] != parts2[i]) {
        return parts1[i].compareTo(parts2[i]);
      }
    }
    return parts1.length.compareTo(parts2.length);
  }

  /// Mirrors `VersionUtils.parseApkVersionString`: strip every `v` and `.`, then
  /// drop a single leading zero. `"v0.62.97"` -> `6297`.
  static int? parseApkVersionString(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    var vsn = raw.replaceAll('v', '').replaceAll('.', '');
    if (vsn.startsWith('0')) {
      vsn = vsn.replaceFirst('0', '');
    }
    return int.tryParse(vsn);
  }

  static String _stripSuffix(String value, String suffix) =>
      value.endsWith(suffix)
      ? value.substring(0, value.length - suffix.length)
      : value;

  /// The Kotlin uses `it.toInt()`, which throws on a malformed segment. Dart's
  /// `int.tryParse` lets a malformed segment sort as 0 rather than crashing the
  /// configuration check, which is the behaviour the caller actually wants.
  static List<int> _split(String version) {
    final stripped = version.startsWith('v') ? version.substring(1) : version;
    return stripped.split('.').map((p) => int.tryParse(p) ?? 0).toList();
  }
}
