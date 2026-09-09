// Derives the `--dart-define-from-file` JSON for the preconfigured Planet
// servers from the Kotlin app's own `gradle.properties`.
//
// Usage, from `flutter/`:
//
//   dart run tool/planet_server_defines.dart
//   flutter build apk --debug \
//     --dart-define-from-file=build/planet_server_defines.json
//
// Reading `gradle.properties` rather than keeping a second copy is the point:
// `app/build.gradle` reads the same eleven pairs into `BuildConfig`, so the two
// apps offer the same servers with the same PINs and cannot drift apart.
//
// The output carries real `satellite` PINs. It is written under `build/`, which
// is gitignored, and it must stay out of the tree — see
// `lib/core/config/planet_servers.dart` for why these are recoverable from the
// APK anyway and why that is not licence to add more.

import 'dart:convert';
import 'dart:io';

/// The property names `ServerConfigUtils.pinMap` and `getServerAddresses` read.
const List<String> _servers = <String>[
  'LEARNING',
  'GUATEMALA',
  'SANPABLO',
  'EARTH',
  'SOMALIA',
  'VI',
  'XELA',
  'URIUR',
  'RUIRU',
  'EMBAKASI',
  'CAMBRIDGE',
];

void main(List<String> args) {
  final propertiesPath = args.isNotEmpty
      ? args[0]
      : '${Directory.current.parent.path}/gradle.properties';
  final outputPath = args.length > 1
      ? args[1]
      : 'build/planet_server_defines.json';

  final properties = File(propertiesPath);
  if (!properties.existsSync()) {
    stderr.writeln('planet_server_defines: no $propertiesPath');
    stderr.writeln(
      'planet_server_defines: writing an empty set — the build will offer no '
      'server list and fall back to manual entry.',
    );
  }

  final parsed = properties.existsSync()
      ? _parseProperties(properties.readAsLinesSync())
      : <String, String>{};

  final defines = <String, String>{};
  var found = 0;
  for (final server in _servers) {
    for (final suffix in const <String>['URL', 'PIN']) {
      final key = 'PLANET_${server}_$suffix';
      final value = parsed[key];
      // Absent and empty are the same thing here, and both match
      // `findProperty(key) ?: ""` on the Kotlin side. Emitting the key with an
      // empty value keeps `String.fromEnvironment` on its documented default.
      defines[key] = value ?? '';
      if (suffix == 'URL' && (value ?? '').isNotEmpty) found++;
    }
  }

  // `PLANET_SERVER_MAPPINGS`, the local-to-clone fallback table.
  // `ServerUrlMapper` reads it from the environment and its keys are what
  // `extractBaseUrl` produces — `scheme://host`, no default port — so they are
  // built as `http://<primary>` exactly as
  // `services/sync/ServerUrlMapper.kt:20-24` does. Without this the table is
  // empty in every built APK, `alternativeUrl` is always null, and the clone
  // fallback the Kotlin has simply does not exist in the port.
  final mappings = <String>[];
  for (final server in const <String>['SANPABLO', 'URIUR', 'EMBAKASI']) {
    final primary = parsed['PLANET_${server}_URL'] ?? '';
    final clone = parsed['PLANET_${server}_CLONE_URL'] ?? '';
    if (primary.isEmpty || clone.isEmpty) continue;
    mappings.add('http://$primary=https://$clone');
  }
  defines['PLANET_SERVER_MAPPINGS'] = mappings.join(',');

  final output = File(outputPath);
  output.parent.createSync(recursive: true);
  output.writeAsStringSync(
    '${const JsonEncoder.withIndent('  ').convert(defines)}\n',
  );

  // Never print a PIN: this runs in CI, whose logs are public on a public repo.
  stdout.writeln(
    'planet_server_defines: wrote ${defines.length} defines '
    '($found of ${_servers.length} servers have a URL) to $outputPath',
  );
}

/// A `.properties` reader covering what this file needs: `key=value`, `#`/`!`
/// comments, blank lines, and surrounding whitespace. Deliberately does not
/// implement line continuations or `\u` escapes — no `PLANET_*` value uses
/// them, and a half-implemented unescaper would corrupt a PIN silently rather
/// than fail.
Map<String, String> _parseProperties(List<String> lines) {
  final result = <String, String>{};
  for (final line in lines) {
    final trimmed = line.trim();
    if (trimmed.isEmpty || trimmed.startsWith('#') || trimmed.startsWith('!')) {
      continue;
    }
    final separator = trimmed.indexOf('=');
    if (separator <= 0) continue;
    final key = trimmed.substring(0, separator).trim();
    final value = trimmed.substring(separator + 1).trim();
    result[key] = value;
  }
  return result;
}
