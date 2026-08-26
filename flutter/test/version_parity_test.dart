import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/repository/configurations_repository.dart';

/// Guards the app version against the drift that matters, in two parts.
///
/// **Why this exists.** The pubspec version is what `package_info_plus` reports
/// at runtime, and since Phase 60 that is what the server's `minapk` gate
/// compares. A stale value does not degrade gracefully: the check fails, the
/// screen reports an unreachable server, and nothing points at the version.
/// Phase 93 found pubspec five *minor* versions behind `app/build.gradle`
/// (0.62.98 against 0.67.14) after five releases, entirely by hand.
///
/// **Why it is not an equality check.** The first cut of this test asserted
/// pubspec == the Kotlin `versionName` exactly, and CI went red immediately —
/// not on this branch's own push, but on the pull-request run, which tests the
/// *merge* with master. `automerge.yml` bumps the version on every PR it
/// merges, so master moved 0.67.14 → 0.67.25 within the hour. Exact equality
/// against a target that moves several times an hour is a permanently-red
/// build, and a permanently-red build teaches everyone to ignore it.
///
/// So the external check tolerates patch lag and fails on a **minor** version
/// behind. That is the line where the risk becomes real: servers set `minapk`
/// to force upgrades of genuinely old clients, not to the newest patch — a
/// server demanding the current patch would lock out every user who had not
/// updated in the last hour. Patch lag is normal and harmless; a minor behind
/// is the shape of the drift that actually bit.
///
/// Please do not "tighten" this back to equality. It was, and it did not work.
void main() {
  /// `(major, minor)` of a `major.minor.patch` string. The patch is
  /// deliberately dropped — see the note above.
  (int, int) majorMinor(String version) {
    final parts = version.split('.');
    return (int.parse(parts[0]), int.parse(parts[1]));
  }

  // The Kotlin gradle file is the release train both apps ride: release.yml
  // publishes whatever version it names.
  final gradle = File('../app/build.gradle').readAsStringSync();
  final kotlinVersionName = RegExp(
    r'versionName\s*=\s*"([0-9.]+)"',
  ).firstMatch(gradle)!.group(1)!;

  final pubspec = File('pubspec.yaml').readAsStringSync();
  final pubspecMatch = RegExp(
    r'^version:\s*([0-9.]+)\+(\d+)\s*$',
    multiLine: true,
  ).firstMatch(pubspec);

  test('pubspec declares a version and build number', () {
    expect(
      pubspecMatch,
      isNotNull,
      reason: 'pubspec.yaml has no parseable "version: x.y.z+build" line',
    );
  });

  test('the minapk fallback constant matches the pubspec version', () {
    // The strict half, and the one fully within this repo's control: these two
    // must never disagree with each other. The constant is what the `minapk`
    // comparison falls back to when the runtime lookup fails, so a constant
    // that disagrees with the shipped version reports a version the app is
    // not.
    expect(
      ConfigurationsRepository.defaultAppVersion,
      pubspecMatch!.group(1),
      reason:
          'ConfigurationsRepository.defaultAppVersion and pubspec version '
          'disagree — bump both together',
    );
  });

  test('the port is not a minor version behind the Kotlin app', () {
    final port = majorMinor(pubspecMatch!.group(1)!);
    final kotlin = majorMinor(kotlinVersionName);

    // Compares (major, minor) as a pair: behind on major, or level on major
    // and behind on minor.
    final behind =
        port.$1 < kotlin.$1 || (port.$1 == kotlin.$1 && port.$2 < kotlin.$2);

    expect(
      behind,
      isFalse,
      reason:
          'the port reports ${pubspecMatch.group(1)} while the Kotlin app '
          'ships $kotlinVersionName — a full minor version behind is the '
          'drift that locks the app out of server configuration through the '
          'minapk gate. Bump pubspec version and '
          'ConfigurationsRepository.defaultAppVersion to '
          '$kotlinVersionName. (Patch lag is expected and does not fail: '
          'automerge bumps the Kotlin patch on every merge.)',
    );
  });
}
