import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Guards the ARB files against the two failures `gen-l10n` does not report.
///
/// A duplicate key is legal JSON — the last one silently wins — so a new entry
/// can shadow an existing one of the same name and nothing complains at build
/// time. That is exactly what happened when the About page landed: `about` had
/// been the menu label, the page body was appended under the same name, and
/// `flutter analyze` stayed clean while the settings screen was set to render a
/// two-thousand-character HTML document as a section heading.
///
/// The second failure is an orphan: a key in a translated locale that no longer
/// exists in English is dead weight Crowdin will never surface.
void main() {
  final l10nDir = Directory('lib/l10n');

  /// Reads an ARB and returns its top-level keys in file order, so duplicates
  /// survive to be counted — `json.decode` collapses them to the last one.
  ///
  /// The keys come from the raw text rather than the parsed map because a
  /// `json.decode` reviver fires innermost-first with no depth information. A
  /// top-level entry is the only one indented by exactly two spaces, which both
  /// `dart format` and the ARB tooling maintain.
  List<String> topLevelKeys(String source) {
    final pattern = RegExp(r'^  "([^"]+)"\s*:');
    return [
      for (final line in const LineSplitter().convert(source))
        if (pattern.firstMatch(line) case final match?) match.group(1)!,
    ];
  }

  final arbFiles =
      l10nDir
          .listSync()
          .whereType<File>()
          .where((f) => f.path.endsWith('.arb'))
          .toList()
        ..sort((a, b) => a.path.compareTo(b.path));

  test('every ARB file parses and declares its locale', () {
    expect(arbFiles, isNotEmpty, reason: 'no .arb files found in lib/l10n');
    for (final file in arbFiles) {
      final decoded = json.decode(file.readAsStringSync());
      expect(decoded, isA<Map<String, dynamic>>(), reason: file.path);
      expect(
        (decoded as Map<String, dynamic>)['@@locale'],
        isNotNull,
        reason: '${file.path} is missing @@locale',
      );
    }
  });

  test('no ARB file defines the same key twice', () {
    for (final file in arbFiles) {
      final keys = topLevelKeys(file.readAsStringSync());
      final seen = <String>{};
      final duplicates = <String>{};
      for (final key in keys) {
        if (!seen.add(key)) duplicates.add(key);
      }
      expect(
        duplicates,
        isEmpty,
        reason:
            '${file.path} defines these keys more than once: '
            '${duplicates.join(', ')}. The last definition wins silently — '
            'rename the new key instead of shadowing the old one.',
      );
    }
  });

  test('translated locales carry no key English does not have', () {
    final english =
        json.decode(File('lib/l10n/app_en.arb').readAsStringSync())
            as Map<String, dynamic>;
    final englishKeys = english.keys.where((k) => !k.startsWith('@')).toSet();

    for (final file in arbFiles) {
      if (file.path.endsWith('app_en.arb')) continue;
      final doc = json.decode(file.readAsStringSync()) as Map<String, dynamic>;
      final orphans = doc.keys
          .where((k) => !k.startsWith('@'))
          .where((k) => !englishKeys.contains(k))
          .toSet();
      expect(
        orphans,
        isEmpty,
        reason:
            '${file.path} has keys absent from English: '
            '${orphans.join(', ')}',
      );
    }
  });

  test('page bodies and menu labels stay separate keys', () {
    // Kotlin keeps `about`/`disclaimer` (the page bodies, HTML) apart from
    // `action_about`/`action_disclaimer` (the overflow-menu labels). Collapsing
    // either pair puts a whole document where a title belongs.
    for (final file in arbFiles) {
      final doc = json.decode(file.readAsStringSync()) as Map<String, dynamic>;
      for (final body in ['about', 'disclaimer']) {
        final value = doc[body];
        if (value == null) continue;
        expect(
          value,
          contains('<'),
          reason: '${file.path}: "$body" should hold the page body markup',
        );
      }
      for (final label in ['actionAbout', 'actionDisclaimer']) {
        final value = doc[label] as String?;
        if (value == null) continue;
        expect(
          value.contains('<'),
          isFalse,
          reason: '${file.path}: "$label" is a menu label, not markup',
        );
        expect(
          value.length,
          lessThan(60),
          reason: '${file.path}: "$label" is too long to be a menu label',
        );
      }
    }
  });
}
