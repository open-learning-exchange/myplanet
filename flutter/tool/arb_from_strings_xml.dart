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
//
// Machine-translation flags (Phase 109) survive a re-run too, and are kept
// *honest* across one. `"@<key>": {"x-mt": true}` in a locale file marks a
// string as unreviewed machine output; ~560 keys per locale carry it. The merge
// carries those blocks over verbatim, and then reconciles them, because merely
// preserving them is not enough:
//
//   * a key this script derives from the Kotlin XML is a human translation
//     already shipping in the Android app, so if a stale `x-mt` block is
//     sitting there — the workflow above, delete the key and re-run to pick up
//     a better translation, leaves exactly that — the flag is dropped;
//   * a flag whose key has no value at all is dropped, so it cannot land on
//     whatever value is written for that key next and mislabel it.
//
// `test/l10n/placeholder_integrity_test.dart` pins both ends of this.
//
// Usage, again from `flutter/`:
//   dart tool/arb_from_strings_xml.dart --unreviewed        # list what needs a
//   dart tool/arb_from_strings_xml.dart --unreviewed fr     # human, per locale

import 'dart:convert';
import 'dart:io';

import 'package:xml/xml.dart';

/// Locales with a `values-<code>` directory in the Kotlin app.
const defaultLocales = ['ar', 'fr', 'ne', 'so'];

/// Every locale that ships an `.arb`. `es` has no Kotlin `values-es`, so it is
/// not derivable — but it does carry machine-translated strings to review.
const allLocales = ['ar', 'es', 'fr', 'ne', 'so'];

/// The ARB attribute marking a string as unreviewed machine translation.
const machineTranslatedFlag = 'x-mt';

void main(List<String> args) {
  if (args.isNotEmpty && args.first == '--unreviewed') {
    _reportUnreviewed(args.skip(1).toList());
    return;
  }
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
      // So is Kotlin's printf syntax. Three template keys — `communityEarnings`,
      // `perSurvey`, `yourEarnings` — declare ICU placeholders but write their
      // English with `%1$d`/`%1$s`, which ICU never interpolates: the generated
      // getter takes the argument and drops it, in every language including
      // English. Deriving a translation would spread that defect into the
      // locale files, where the placeholder guard then fails on it. Leave them
      // absent until `app_en.arb` is corrected to `{amount}`/`{status}`.
      if (_printfSpecifier.hasMatch(templateValue)) continue;
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
    final humanDerived = <String>{};
    for (final key in keys) {
      final value = derived[key];
      if (value == null || merged.containsKey(key)) continue;
      merged[key] = value;
      humanDerived.add(key);
      added++;
    }

    final cleared = _reconcileMachineTranslationFlags(merged, humanDerived);

    file.writeAsStringSync('${_encodeArb(merged)}\n');
    stdout.writeln(
      'app_$locale.arb: ${_messageKeys(merged).length} strings '
      '($byName by name, $byText by shared English) of ${keys.length} keys'
      ' — $added added, $preserved kept that the XML does not derive,'
      ' ${_machineTranslatedKeys(merged).length} still unreviewed'
      '${cleared == 0 ? '' : ' ($cleared flag(s) cleared)'}',
    );
  }
}

/// Kotlin's `%s`/`%d`/`%1$s` format specifiers, which ICU does not interpolate.
final _printfSpecifier = RegExp(r'%\d*\$?[sd]');

/// Translatable entries — not `@@locale`, not an `@key` metadata block.
Iterable<String> _messageKeys(Map<String, Object?> arb) =>
    arb.keys.where((key) => !key.startsWith('@') && arb[key] is String);

/// Keys flagged as unreviewed machine translation.
Iterable<String> _machineTranslatedKeys(Map<String, Object?> arb) => arb.keys
    .where((key) => key.startsWith('@') && key != '@@locale')
    .where((key) {
      final meta = arb[key];
      return meta is Map && meta[machineTranslatedFlag] == true;
    })
    .map((key) => key.substring(1));

/// Drops `x-mt` flags that have stopped being true, and returns how many.
///
/// Two cases, both of which this script itself creates:
///
///   * [humanDerived] is what this run carried across from the Kotlin
///     `strings.xml` — translations already shipping in the Android app. A flag
///     on one of those is stale by definition.
///   * a flag whose key carries no value marks nothing. Left in place it would
///     attach itself to the next value written for that key.
///
/// A metadata block that also declares placeholders keeps them: only the flag
/// is removed, and only an emptied block is deleted outright.
int _reconcileMachineTranslationFlags(
  Map<String, Object?> merged,
  Set<String> humanDerived,
) {
  final translated = _messageKeys(merged).toSet();
  var cleared = 0;

  for (final key in _machineTranslatedKeys(merged).toList()) {
    if (!humanDerived.contains(key) && translated.contains(key)) continue;
    final meta = Map<String, Object?>.from(merged['@$key']! as Map);
    meta.remove(machineTranslatedFlag);
    if (meta.isEmpty) {
      merged.remove('@$key');
    } else {
      merged['@$key'] = meta;
    }
    cleared++;
  }

  return cleared;
}

/// Prints the strings still awaiting a human, per locale.
///
/// This is the whole point of the marking: ~560 keys per locale are Google
/// Translate output sitting indistinguishably beside translations derived from
/// the Kotlin app. A reviewer needs to see exactly which.
void _reportUnreviewed(List<String> args) {
  for (final locale in args.isEmpty ? allLocales : args) {
    final file = File('lib/l10n/app_$locale.arb');
    if (!file.existsSync()) {
      stderr.writeln('No app_$locale.arb — skipped.');
      continue;
    }
    final arb = _readArb(file.path);
    final unreviewed = _machineTranslatedKeys(arb).toList();
    stdout.writeln(
      '# $locale — ${unreviewed.length} unreviewed of '
      '${_messageKeys(arb).length} translated',
    );
    for (final key in unreviewed) {
      stdout.writeln('$key\t${arb[key]}');
    }
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

/// Strips Android's whitespace-preserving quoting and backslash escapes.
///
/// `<string name="x">"Select resources: "</string>` is the XML way to keep a
/// trailing space; the quotes are not part of the value. Reading `innerText`
/// verbatim carried them into the `.arb`, and the app then displayed them.
///
/// Android also escapes apostrophes and quotes with a backslash, which the XML
/// parser leaves alone because they are not XML syntax: `values-fr` writes
/// `Impossible d\'ajouter un dossier de santé.` The backslash is Android's, not
/// the string's, and carrying it across would put a literal `d\'ajouter` on a
/// French screen. Every escape Android documents is undone here; an unknown one
/// keeps its backslash rather than being silently eaten.
String _unquote(String raw) {
  final quoted = raw.length > 1 && raw.startsWith('"') && raw.endsWith('"')
      ? raw.substring(1, raw.length - 1)
      : raw;

  final buffer = StringBuffer();
  for (var i = 0; i < quoted.length; i++) {
    if (quoted[i] != r'\' || i + 1 == quoted.length) {
      buffer.write(quoted[i]);
      continue;
    }
    final escaped = quoted[i + 1];
    switch (escaped) {
      case "'":
      case '"':
      case '@':
      case '?':
      case r'\':
        buffer.write(escaped);
      case 'n':
        buffer.write('\n');
      case 't':
        buffer.write('\t');
      default:
        buffer.write(quoted[i]);
        continue; // not an escape — keep the backslash and re-read the next char
    }
    i++;
  }
  return buffer.toString();
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
