import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/repository/configurations_repository.dart';

/// Pins the three places the app's version lives to the Kotlin app's
/// `versionName`/`versionCode`, which is the release train both apps ride.
///
/// This is the drift this guard exists for: Kotlin moved 0.62.98 → 0.67.14
/// across five releases while pubspec sat still, and nothing noticed. The
/// pubspec version is what `package_info_plus` reports at runtime, which is
/// what the server's `minapk` gate compares (Phase 60) — so a server whose
/// `minapk` moved past the stale value refused the Flutter app configuration
/// and presented it as an unreachable server. A failing test at the moment
/// `app/build.gradle` is bumped is cheap; a support report about a server
/// that "cannot be reached" is not.
void main() {
  // The Kotlin gradle file is the source of truth — release.yml publishes
  // whatever version it names.
  final gradle = File('../app/build.gradle').readAsStringSync();
  final versionName = RegExp(
    r'versionName\s*=\s*"([0-9.]+)"',
  ).firstMatch(gradle)!.group(1)!;
  final versionCode = RegExp(
    r'versionCode\s*=\s*(\d+)',
  ).firstMatch(gradle)!.group(1)!;

  test('pubspec version matches the Kotlin versionName and versionCode', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    final match = RegExp(
      r'^version:\s*([0-9.]+)\+(\d+)\s*$',
      multiLine: true,
    ).firstMatch(pubspec);

    expect(match, isNotNull, reason: 'pubspec.yaml has no version: line');
    expect(
      match!.group(1),
      versionName,
      reason:
          'pubspec version drifted from app/build.gradle versionName — '
          'the runtime version feeds the server minapk gate, so a stale '
          'value locks the app out of configuration once servers move on',
    );
    expect(
      match.group(2),
      versionCode,
      reason: 'pubspec build number drifted from app/build.gradle versionCode',
    );
  });

  test('the minapk fallback constant matches the Kotlin versionName', () {
    // The constant only matters when the runtime lookup fails, but when it
    // does, an under-reported version fails configuration outright.
    expect(
      ConfigurationsRepository.defaultAppVersion,
      versionName,
      reason:
          'ConfigurationsRepository.defaultAppVersion drifted from '
          'app/build.gradle versionName',
    );
  });
}
