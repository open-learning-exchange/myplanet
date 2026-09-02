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
// Re-running is safe: the script *merges* into the existing .arb rather than
// replacing it. A key already present keeps its value, and only keys the .arb
// does not have yet are added. That matters because the port has UI text with
// no Kotlin counterpart at all — `videoFileNotFound`, `exportCancelled` — which
// was translated by hand into these files; an earlier version of this script
// regenerated from scratch and would have deleted 17 such keys per locale. The
// advice that used to live here (put new strings in the Kotlin `strings.xml`
// instead) does not work for those: there is no Kotlin string to add them to.
//
// A consequence worth knowing: because existing values win, the script cannot
// *correct* a translation already in the .arb. Delete the key first, then
// re-run, if the XML has a better one.

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
    final derived = <String, String>{};
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
          derived[key] = value;
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
        derived[key] = candidates.single!;
        byText++;
      }
    }

    final file = File('lib/l10n/app_$locale.arb');

    // Merge into whatever is already there. Existing keys keep their existing
    // value and their existing position, so re-running produces no diff for
    // them; newly derived keys are appended in template order. Iterating the
    // existing file's order rather than the template's is deliberate — these
    // files are not in template order, and reordering them would bury the real
    // change under a whole-file diff.
    final existing = file.existsSync()
        ? _readArb(file.path)
        : const <String, Object?>{};
    final merged = <String, Object?>{'@@locale': locale};
    var preserved = 0;
    for (final entry in existing.entries) {
      if (entry.key == '@@locale') continue;
      final value = entry.value;
      // `@key` metadata blocks (placeholder declarations, which `gen-l10n`
      // needs) are objects, not strings. They are carried over verbatim —
      // dropping one silently un-declares a message's placeholders.
      if (value is! String) {
        merged[entry.key] = value;
        continue;
      }
      merged[entry.key] = value;
      if (!derived.containsKey(entry.key)) preserved++;
    }
    var added = 0;
    for (final key in keys) {
      final value = derived[key];
      if (value == null || merged.containsKey(key)) continue;
      merged[key] = value;
      added++;
    }

    file.writeAsStringSync('${_encodeArb(merged)}\n');
    stdout.writeln(
      'app_$locale.arb: ${merged.length - 1} strings '
      '($byName by name, $byText by shared English) of ${keys.length} keys'
      ' — $added added, $preserved kept that the XML does not derive',
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
    result[name] = _unquote(element.innerText);
  }
  return result;
}

/// Strips Android's whitespace-preserving quoting.
///
/// `<string name="x">"Select resources: "</string>` is the XML way to keep a
/// trailing space; the quotes are not part of the value. Reading `innerText`
/// verbatim carried them into the `.arb`, and the app then displayed them.
String _unquote(String raw) {
  if (raw.length > 1 && raw.startsWith('"') && raw.endsWith('"')) {
    return raw.substring(1, raw.length - 1);
  }
  return raw;
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

/// Two-space indented JSON, non-ASCII written literally.
///
/// This used to `\u`-escape every non-ASCII rune to match `app_es.arb`'s
/// original style. The locale files have since been rewritten as literal UTF-8,
/// which is far easier to review — a reviewer can actually read the Arabic —
/// so escaping here would rewrite all four files on every run.
String _encodeArb(Map<String, Object?> values) {
  final buffer = StringBuffer('{\n');
  var index = 0;
  for (final entry in values.entries) {
    final value = entry.value;
    // Strings go through [_escape]; a carried-over `@key` metadata object is
    // re-encoded as indented JSON so it stays readable.
    final encoded = value is String
        ? _escape(value)
        : const JsonEncoder.withIndent(
            '  ',
          ).convert(value).replaceAll('\n', '\n  ');
    buffer.write('  ${_escape(entry.key)}: $encoded');
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
        // Control characters still have to be escaped — JSON forbids them raw.
        // Everything else, including all non-ASCII, is written as-is.
        if (rune < 0x20) {
          buffer.write('\\u${rune.toRadixString(16).padLeft(4, '0')}');
        } else {
          buffer.writeCharCode(rune);
        }
    }
  }
  buffer.write('"');
  return buffer.toString();
}
