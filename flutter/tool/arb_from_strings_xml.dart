// Derives `lib/l10n/app_<locale>.arb` from the Kotlin app's
// `res/values-<locale>/strings.xml`.
//
// The Kotlin app is the port's specification for translations too: all five of
// its locales are fully translated (1041 strings each), and those translations
// were paid for. This script carries across the ones that can be matched
// *safely* and leaves the rest absent so `gen-l10n` falls back to English.
//
// Nothing here machine-translates. A key is carried only when one of two rules
// holds, which are the rules `app_es.arb` was originally built with:
//
//   1. The Kotlin string's name normalises to the ARB key (snake_case →
//      camelCase) *and* its English text matches the template exactly.
//   2. No name match, but some Kotlin string's English text matches the
//      template exactly, and every candidate sharing that English has the same
//      translation in the target locale. (Unanimity matters: `values/strings.xml`
//      has several names for one English phrase, and they do not always agree in
//      translation.)
//
// Keys carrying ICU placeholders or plurals are skipped outright. Kotlin writes
// `%1$s`/`%1$d`, and where a namesake exists its wording is usually a different
// phrasing than the ARB template — deriving from it would attach a translation
// to text that says something else.
//
// Usage, from the `flutter/` directory:
//   dart tool/arb_from_strings_xml.dart            # writes ar, fr, ne, so
//   dart tool/arb_from_strings_xml.dart ar fr      # or named locales
//
// Re-running is safe and idempotent: it regenerates from the template and the
// XML, so a key hand-translated into an .arb afterwards would be lost. Add such
// strings to the Kotlin `strings.xml` instead, where both apps get them.

import 'dart:convert';
import 'dart:io';

import 'package:xml/xml.dart';

/// Locales with a `values-<code>` directory in the Kotlin app.
const defaultLocales = ['ar', 'fr', 'ne', 'so'];

void main(List<String> args) {
  final locales = args.isEmpty ? defaultLocales : args;
  final resDir = Directory('../app/src/main/res');
  if (!resDir.existsSync()) {
    stderr.writeln('Run this from the flutter/ directory.');
    exitCode = 1;
    return;
  }

  final template = _readArb('lib/l10n/app_en.arb');
  final english = _readStringsXml('${resDir.path}/values/strings.xml');

  // Every key in template order, so the generated files read alongside the
  // template rather than in hash order.
  final keys = template.keys.where((k) => !k.startsWith('@')).toList();

  final byCamelCase = <String, List<String>>{};
  for (final name in english.keys) {
    byCamelCase.putIfAbsent(_camelCase(name), () => []).add(name);
  }
  final byEnglishText = <String, List<String>>{};
  for (final entry in english.entries) {
    byEnglishText.putIfAbsent(entry.value.trim(), () => []).add(entry.key);
  }

  for (final locale in locales) {
    final path = '${resDir.path}/values-$locale/strings.xml';
    if (!File(path).existsSync()) {
      stderr.writeln('No strings.xml for "$locale" — skipped.');
      continue;
    }
    final translated = _readStringsXml(path);
    final out = <String, String>{'@@locale': locale};
    var byName = 0;
    var byText = 0;

    for (final key in keys) {
      final templateValue = template[key];
      if (templateValue is! String) continue;
      // ICU syntax of any kind is out of scope — see the header.
      if (templateValue.contains('{')) continue;
      final wanted = templateValue.trim();

      final named = byCamelCase[key] ?? const [];
      final exactNamed = named.firstWhere(
        (name) => english[name]?.trim() == wanted,
        orElse: () => '',
      );
      if (exactNamed.isNotEmpty) {
        final value = translated[exactNamed]?.trim();
        if (value != null && value.isNotEmpty) {
          out[key] = value;
          byName++;
          continue;
        }
      }

      final sameText = byEnglishText[wanted] ?? const [];
      if (sameText.isEmpty) continue;
      final candidates = sameText
          .map((name) => translated[name]?.trim())
          .where((value) => value != null && value.isNotEmpty)
          .toSet();
      if (candidates.length == 1) {
        out[key] = candidates.single!;
        byText++;
      }
    }

    final file = File('lib/l10n/app_$locale.arb');
    // `ensure_ascii`-style escaping and two-space indent, matching app_es.arb.
    file.writeAsStringSync('${_encodeArb(out)}\n');
    stdout.writeln(
      'app_$locale.arb: ${out.length - 1} strings '
      '($byName by name, $byText by shared English) of ${keys.length} keys',
    );
  }
}

Map<String, Object?> _readArb(String path) =>
    jsonDecode(File(path).readAsStringSync()) as Map<String, Object?>;

/// `name` → text, skipping `translatable="false"` entries. Inline markup
/// (`<b>`, `<xliff:g>`) is flattened to its text, which is what the ARB holds.
Map<String, String> _readStringsXml(String path) {
  final document = XmlDocument.parse(File(path).readAsStringSync());
  final result = <String, String>{};
  for (final element in document.findAllElements('string')) {
    final name = element.getAttribute('name');
    if (name == null) continue;
    if (element.getAttribute('translatable') == 'false') continue;
    result[name] = element.innerText;
  }
  return result;
}

String _camelCase(String snakeCase) {
  final parts = snakeCase.split('_');
  return parts.first +
      parts
          .skip(1)
          .map(
            (part) =>
                part.isEmpty ? '' : part[0].toUpperCase() + part.substring(1),
          )
          .join();
}

/// Two-space indented JSON with non-ASCII escaped, so the generated files match
/// `app_es.arb` byte-for-byte in style and diff cleanly.
String _encodeArb(Map<String, String> values) {
  final buffer = StringBuffer('{\n');
  var index = 0;
  for (final entry in values.entries) {
    buffer.write('  ${_escape(entry.key)}: ${_escape(entry.value)}');
    buffer.write(++index == values.length ? '\n' : ',\n');
  }
  buffer.write('}');
  return buffer.toString();
}

String _escape(String value) {
  final buffer = StringBuffer('"');
  for (final rune in value.runes) {
    switch (rune) {
      case 0x22:
        buffer.write(r'\"');
      case 0x5C:
        buffer.write(r'\\');
      case 0x0A:
        buffer.write(r'\n');
      case 0x0D:
        buffer.write(r'\r');
      case 0x09:
        buffer.write(r'\t');
      default:
        if (rune < 0x20 || rune > 0x7E) {
          if (rune > 0xFFFF) {
            // Surrogate pair, as JSON requires for astral characters.
            final value = rune - 0x10000;
            final high = 0xD800 + (value >> 10);
            final low = 0xDC00 + (value & 0x3FF);
            buffer.write('\\u${high.toRadixString(16).padLeft(4, '0')}');
            buffer.write('\\u${low.toRadixString(16).padLeft(4, '0')}');
          } else {
            buffer.write('\\u${rune.toRadixString(16).padLeft(4, '0')}');
          }
        } else {
          buffer.writeCharCode(rune);
        }
    }
  }
  buffer.write('"');
  return buffer.toString();
}
