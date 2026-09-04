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
//   dart tool/arb_from_strings_xml.dart            # writes ar, es, fr, ne, so
//   dart tool/arb_from_strings_xml.dart ar fr      # or named locales
//
// The merge below can only *add* a key, never correct one — see "Recovering
// human translations the port left on the table" further down for `--candidates`
// and `--adopt`, which are the modes that may overwrite, and for the rule that
// says what they are allowed to overwrite.
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
// string as unreviewed machine output; 496–545 keys carry it in ar/es/fr and
// 26 in ne/so, where the external pass emitted a `[Nepali] `-style marker
// instead of a translation and Phase 118 deleted those. The merge
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

/// Every locale that ships an `.arb`, all five of which have a `values-<code>`
/// directory in the Kotlin app and are therefore derivable.
///
/// `es` used to be missing from this list, with a comment here claiming the
/// Kotlin app has no `values-es`. It does — 1050 strings, the same as every
/// other locale — so `app_es.arb` had never been through this script at all.
/// It carried the most machine-translated strings of any locale (619) for
/// exactly that reason.
const defaultLocales = ['ar', 'es', 'fr', 'ne', 'so'];

/// Retained under its old name for callers; identical to [defaultLocales] now
/// that `es` is derivable too.
const allLocales = defaultLocales;

/// The ARB attribute marking a string as unreviewed machine translation.
const machineTranslatedFlag = 'x-mt';

void main(List<String> args) {
  if (args.isNotEmpty && args.first == '--unreviewed') {
    _reportUnreviewed(args.skip(1).toList());
    return;
  }
  if (args.isNotEmpty &&
      (args.first == '--candidates' || args.first == '--adopt')) {
    _recover(args.skip(1).toList(), apply: args.first == '--adopt');
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
  // The same index for format strings, keyed on the literal with the holes
  // taken out: `Progress %1$s of %2$s` and `Progress {current} of {max}` are
  // the same message written twice, and only the literal says so.
  final byEnglishFormat = <String, List<String>>{};
  for (final entry in english.entries) {
    final format = _parseAndroidFormat(entry.value.trim());
    if (format == null || format.holes.isEmpty) continue;
    byEnglishFormat.putIfAbsent(format.literal.trim(), () => []).add(entry.key);
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
      // Kotlin's printf syntax in the *template*. Three keys —
      // `communityEarnings`, `perSurvey`, `yourEarnings` — declare ICU
      // placeholders but write their English with `%1$d`/`%1$s`, which ICU
      // never interpolates: the generated getter takes the argument and drops
      // it, in every language including English. Deriving a translation would
      // spread that defect into the locale files, where the placeholder guard
      // then fails on it. Leave them absent until `app_en.arb` is corrected to
      // `{amount}`/`{status}`.
      if (_printfSpecifier.hasMatch(templateValue)) continue;

      // A message with ICU placeholders is derived through the format layer at
      // the foot of this file, which lines the two notations up by argument
      // index. Everything else is plain text and matches on the text itself.
      if (templateValue.contains('{')) {
        final format = _parseIcuFormat(templateValue);
        if (format == null || format.holes.isEmpty || !format.hasWords) {
          continue;
        }
        final named = byCamelCase[key] ?? const [];
        String? fromName;
        for (final name in named) {
          final source = english[name];
          final value = translated[name];
          if (source == null || value == null) continue;
          fromName = convertAndroidFormat(
            templateValue: templateValue,
            kotlinEnglish: source,
            translation: value,
          );
          if (fromName != null) break;
        }
        if (fromName != null) {
          derived[key] = fromName;
          byName++;
          continue;
        }
        // No name match: fall back to every Kotlin string whose English says
        // the same thing, and require them to agree — the same unanimity rule
        // the plain-text path uses, for the same reason.
        final proposals = <String>{};
        for (final name in byEnglishFormat[format.literal.trim()] ?? const []) {
          final value = translated[name];
          if (value == null) continue;
          final converted = convertAndroidFormat(
            templateValue: templateValue,
            kotlinEnglish: english[name]!,
            translation: value,
          );
          if (converted != null) proposals.add(converted);
        }
        if (proposals.length == 1) {
          derived[key] = proposals.single;
          byText++;
        }
        continue;
      }
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
/// This is the whole point of the marking: several hundred keys per locale are
/// Google Translate output sitting indistinguishably beside translations
/// derived from the Kotlin app. A reviewer needs to see exactly which.
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

// ---------------------------------------------------------------------------
// Recovering human translations the port left on the table (Phase 114).
//
// The merge above can only *add* a key. It cannot correct one, so a key the
// external machine-translation pass filled keeps its machine string forever,
// even when the Kotlin app has been shipping a human translation of the very
// same English all along. `incorrectAnswer` was the instance that started this:
// port-minted English, machine-translated five ways, while Kotlin's
// `incorrect_ans` ("Incorrect answer, please try again") carries five real
// translations.
//
//   dart tool/arb_from_strings_xml.dart --candidates      # report, changes nothing
//   dart tool/arb_from_strings_xml.dart --adopt           # apply the confident ones
//   dart tool/arb_from_strings_xml.dart --candidates fr   # or named locales
//
// **How a match is made.** Never by key name alone — the ARB key and the Kotlin
// name agree on a concept, not on a string, and `achievements`/`myAchievements`
// or `teamLeader` ("You lead this team" vs "Team Leader") would silently swap in
// a translation of different words. The English text is the evidence, in three
// adoptable tiers:
//
//   [MatchTier.exact]       identical after trimming. No transformation at all.
//   [MatchTier.punctuation] identical once a trailing `: . … !` run and its
//                           surrounding space are removed from both. Kotlin
//                           labels carry the colon the layout draws
//                           (`author` → "Author:"); the ARB does not. The
//                           translation is stripped the same way, then given the
//                           template's own trailing punctuation back, so the
//                           port's English and its translations agree on it.
//   [MatchTier.casing]      identical once case is also ignored — title case
//                           against sentence case ("Total Visits : " vs "Total
//                           visits"). Only the first character is realigned, and
//                           only upwards: lowercasing a foreign string's first
//                           letter is not safe in general, and no rule here
//                           needs it.
//
// and two that are reported and never applied:
//
//   [MatchTier.nameOnly]    the name matches, the English does not.
//   [MatchTier.containment] one English is contained in the other — the
//                           `incorrectAnswer` shape, where the port paraphrased
//                           a string Kotlin already had. Fixing one means
//                           changing `app_en.arb`, which is a judgement about
//                           what the screen should say, not a derivation.
//
// **What may be overwritten.** Only a value that is demonstrably not a human
// translation: absent, flagged `x-mt`, still carrying an `[Nepali] `-style
// untranslated marker, or byte-identical to the English template. Anything else
// is treated as a human translation and left alone even when Kotlin disagrees
// with it — an unflagged value is somebody's work, and this script has no way to
// tell a better rendering from a worse one.
//
// A tier's candidates must also be unanimous: `values/strings.xml` gives several
// names to one English phrase and they do not always agree in translation.
//
// **The escape repair.** Separately from all of the above, `--adopt` undoes
// Android's `\'` and `\"` escapes wherever they survive in a locale file. The
// `_unquote` above learned to strip them, but values derived before it did kept
// them, and JSON has no reason to object: `app_fr.arb` shipped fourteen strings
// that render a literal backslash on a French screen (`Demandes d\'adhésion`).
enum MatchTier {
  exact,
  punctuation,
  casing,
  nameOnly,
  containment;

  /// Whether `--adopt` may write this tier. The two text-differs tiers are
  /// evidence for a human to read, not a derivation.
  bool get isAdoptable =>
      this == exact || this == punctuation || this == casing;
}

/// A Kotlin string (or several sharing one English text) matched to an ARB key.
class _Match {
  const _Match(this.tier, this.names);

  final MatchTier tier;
  final List<String> names;
}

/// One key/locale decision, ready to print or to write.
class _Candidate {
  const _Candidate({
    required this.key,
    required this.match,
    required this.current,
    required this.proposed,
    required this.verdict,
    this.nameResolved = false,
  });

  final String key;
  final _Match match;
  final String? current;
  final String? proposed;

  /// `apply`, `already`, `keep-human`, `report`, or `no-unanimous`.
  final String verdict;

  /// Whether the tier's candidates disagreed and the Kotlin name that
  /// camel-cases to [key] broke the tie.
  final bool nameResolved;
}

void _recover(List<String> args, {required bool apply}) {
  final resDir = Directory('../app/src/main/res');
  if (!resDir.existsSync()) {
    stderr.writeln('Run this from the flutter/ directory.');
    exitCode = 1;
    return;
  }
  final locales = args.isEmpty ? defaultLocales : args;
  final template = _readArb('lib/l10n/app_en.arb');
  final english = _readStringsXml('${resDir.path}/values/strings.xml');
  final matches = _matchTemplateToKotlin(template, english);

  final tally = <String, int>{};
  for (final locale in locales) {
    final xmlPath = '${resDir.path}/values-$locale/strings.xml';
    if (!File(xmlPath).existsSync()) {
      stderr.writeln('No strings.xml for "$locale" — skipped.');
      continue;
    }
    final translated = _readStringsXml(xmlPath);
    final file = File('lib/l10n/app_$locale.arb');
    final arb = _readArb(file.path);
    final machine = _machineTranslatedKeys(arb).toSet();

    final candidates = <_Candidate>[];
    for (final entry in matches.entries) {
      final key = entry.key;
      final templateValue = template[key];
      if (templateValue is! String) continue;
      final current = arb[key] is String ? arb[key] as String : null;
      // Keyed by Kotlin name, because when two names disagree the name itself
      // is the tiebreak — see below.
      final byKotlinName = <String, String>{};
      final isFormat = templateValue.contains('{');
      for (final name in entry.value.names) {
        final value = translated[name];
        if (value == null || value.trim().isEmpty) continue;
        if (isFormat) {
          // The format layer does its own guarding, and a null from it means
          // "not unambiguous" — the candidate simply drops out.
          final converted = convertAndroidFormat(
            templateValue: templateValue,
            kotlinEnglish: english[name] ?? '',
            translation: value,
          );
          if (converted != null) byKotlinName[name] = converted;
          continue;
        }
        final proposal = _proposal(entry.value.tier, value, templateValue);
        // The template is placeholder-free by the time it gets here, but a
        // translation is a separate string and could carry syntax of its own.
        // A stray `{` in a locale value is an ICU parse error at build time;
        // `%1$s` renders literally. Neither belongs in a value derived for a
        // key whose English has no placeholders.
        if (proposal.contains('{') ||
            proposal.contains('}') ||
            _printfSpecifier.hasMatch(proposal)) {
          continue;
        }
        byKotlinName[name] = proposal;
      }
      // A locale entry that is still the English is an untranslated string, not
      // a translation, and adopting one *replaces* a translation with English.
      // `values-so` renders `settings` as "Settings"; with the namesake rule
      // below that string would otherwise have won the tie against the real
      // Somali `Goobooyinka` the moment anybody flagged that key.
      byKotlinName.removeWhere(
        (_, proposal) => proposal.trim() == templateValue.trim(),
      );
      var proposals = byKotlinName.values.toSet();
      // Two Kotlin names sharing one English, translated differently. The
      // English alone cannot choose between them — but a name that *also*
      // camel-cases to this very ARB key is the same string identified twice,
      // and that is more evidence, not less. `addResource` has `add_resource`
      // and `add_res`; `joinRequests` and `notifGroupJoinRequests` share both
      // their candidates and each picks its own namesake. Phase 118 left 25
      // such keys unresolved for want of this rule; it settles 13 of them and
      // leaves the rest — `progressFilterCompleted` really is a choice between
      // `completed` and `status_completed`, with no name to break the tie.
      var nameResolved = false;
      if (proposals.length > 1) {
        final namesake = {
          for (final row in byKotlinName.entries)
            if (_camelCase(row.key) == key) row.value,
        };
        if (namesake.length == 1) {
          proposals = namesake;
          nameResolved = true;
        }
      }
      String verdict;
      if (!entry.value.tier.isAdoptable) {
        // A tier that only ever produces reading material. Say so before
        // judging its proposals: "the English differs" is the finding, and
        // whether two Kotlin names happen to agree is beside the point.
        verdict = 'report';
      } else if (proposals.isEmpty) {
        // The Kotlin name exists but this locale never translated it. Nothing
        // to recover; not a disagreement either.
        verdict = 'no-translation';
      } else if (proposals.length != 1) {
        verdict = 'no-unanimous';
      } else if (current == proposals.single) {
        verdict = 'already';
      } else if (current != null &&
          _sameButForSpacing(current, proposals.single)) {
        // Identical words, different space character. `app_fr.arb` writes
        // `Rapport créé le\u{a0}: {created}` where `values-fr` writes an
        // ordinary space — French typography puts a no-break space before a
        // colon and the Kotlin XML does not. There is no translation to gain
        // and a typographic nicety to lose, so this is not a candidate.
        verdict = 'already';
      } else if (current != null && current.trim() == proposals.single.trim()) {
        // Same translation, different surrounding whitespace. `selectResources`
        // is `"Select resources: "` — a label the value is drawn after — and
        // Arabic had lost the trailing space. Restoring it is not overwriting
        // anybody's words.
        verdict = 'apply';
      } else if (_replaceable(current, templateValue, machine.contains(key))) {
        verdict = 'apply';
      } else {
        verdict = 'keep-human';
      }
      candidates.add(
        _Candidate(
          key: key,
          match: entry.value,
          current: current,
          proposed: proposals.isEmpty ? null : proposals.join(' | '),
          verdict: verdict,
          nameResolved: nameResolved,
        ),
      );
    }

    for (final candidate in candidates) {
      tally['$locale/${candidate.verdict}'] =
          (tally['$locale/${candidate.verdict}'] ?? 0) + 1;
    }

    if (apply) {
      final adopted = _adopt(file, arb, template, candidates);
      stdout.writeln(
        'app_$locale.arb: ${adopted.values} value(s) taken from the Kotlin '
        'translations, ${adopted.escapes} escape artefact(s) repaired, '
        '${adopted.flags} x-mt flag(s) cleared',
      );
    } else {
      _printCandidates(locale, template, english, candidates, arb);
    }
  }

  if (!apply) {
    stdout.writeln('\n# summary');
    for (final entry
        in tally.entries.toList()..sort((a, b) => a.key.compareTo(b.key))) {
      stdout.writeln('${entry.key}\t${entry.value}');
    }
  }
}

/// Matches every template key to the Kotlin strings whose English it shares,
/// most confident tier first. A key stops at the first tier that hits.
Map<String, _Match> _matchTemplateToKotlin(
  Map<String, Object?> template,
  Map<String, String> english,
) {
  final byCamelCase = <String, List<String>>{};
  for (final name in english.keys) {
    byCamelCase.putIfAbsent(_camelCase(name), () => []).add(name);
  }

  final matches = <String, _Match>{};
  for (final key in template.keys) {
    final value = template[key];
    if (key.startsWith('@') || value is! String) continue;
    // Kotlin's printf syntax in the template is out of scope — see the header.
    if (_printfSpecifier.hasMatch(value)) continue;
    // A placeholder message matches on its literal, and only exactly. The
    // punctuation and casing tiers below reshape a value's ends, which is not
    // safe next to a hole; nothing in the corpus needs them here.
    if (value.contains('{')) {
      final format = _parseIcuFormat(value);
      if (format == null || format.holes.isEmpty || !format.hasWords) continue;
      final literal = format.literal.trim();
      final sameLiteral = <String>[];
      for (final entry in english.entries) {
        final other = _parseAndroidFormat(entry.value.trim());
        if (other == null || other.holes.length != format.holes.length) {
          continue;
        }
        if (other.literal.trim() == literal) sameLiteral.add(entry.key);
      }
      if (sameLiteral.isNotEmpty) {
        matches[key] = _Match(MatchTier.exact, sameLiteral);
      } else if (byCamelCase.containsKey(key)) {
        matches[key] = _Match(MatchTier.nameOnly, byCamelCase[key]!);
      }
      continue;
    }

    final exact = <String>[];
    final punctuation = <String>[];
    final casing = <String>[];
    final containment = <String>[];
    final wanted = value.trim();
    final wantedCore = _core(wanted);
    for (final entry in english.entries) {
      final other = entry.value.trim();
      if (other.isEmpty) continue;
      if (other == wanted) {
        exact.add(entry.key);
      } else if (_core(other) == wantedCore) {
        punctuation.add(entry.key);
      } else if (_core(other).toLowerCase() == wantedCore.toLowerCase()) {
        casing.add(entry.key);
      } else if (_contains(other, wantedCore)) {
        containment.add(entry.key);
      }
    }
    if (exact.isNotEmpty) {
      matches[key] = _Match(MatchTier.exact, exact);
    } else if (punctuation.isNotEmpty) {
      matches[key] = _Match(MatchTier.punctuation, punctuation);
    } else if (casing.isNotEmpty) {
      matches[key] = _Match(MatchTier.casing, casing);
    } else if (byCamelCase.containsKey(key)) {
      matches[key] = _Match(MatchTier.nameOnly, byCamelCase[key]!);
    } else if (containment.isNotEmpty) {
      matches[key] = _Match(MatchTier.containment, containment);
    }
  }
  return matches;
}

/// The value to write for [translation], given the tier it matched at.
String _proposal(MatchTier tier, String translation, String templateEnglish) {
  if (tier == MatchTier.exact) {
    return _mirrorTrailingSpace(translation.trim(), templateEnglish);
  }
  var value = _core(translation);
  // Give the template's own trailing punctuation back — but only onto a word.
  // Nepali ends a sentence with the danda `।`, which `_core` does not strip and
  // which must not be followed by a full stop: `CSV फाइल सुरक्षित गर्न असफल।.`
  if (_endsWithWordCharacter.hasMatch(value)) {
    value += _trailingPunctuation(templateEnglish);
  }
  return _mirrorTrailingSpace(
    _alignInitialCase(value, templateEnglish),
    templateEnglish,
  );
}

final _endsWithWordCharacter = RegExp(r'[\p{L}\p{N}]$', unicode: true);

/// Keeps a trailing space the template carries deliberately.
///
/// `selected` is `"Selected: "` in both `app_en.arb` and the Kotlin XML, where
/// Android's quoting exists precisely to protect that space — it is a label
/// prefix, and the value is drawn straight after it. Trimming the translation
/// would close the gap in every language but English.
String _mirrorTrailingSpace(String value, String templateEnglish) =>
    templateEnglish.endsWith(' ') && !value.endsWith(' ') ? '$value ' : value;

/// Whether [current] is something other than a human translation, and may
/// therefore be replaced. See the header — this is the guard that keeps the
/// script from undoing somebody's work.
bool _replaceable(String? current, String templateEnglish, bool isMachine) {
  if (current == null) return true;
  if (isMachine) return true;
  // `[Nepali] Incorrect answer` — the external pass's own "no translation here"
  // marker, left in the file as a value.
  if (_untranslatedMarker.hasMatch(current)) return true;
  // The English text itself, sitting in a locale file. Not a translation.
  if (current.trim() == templateEnglish.trim()) return true;
  return false;
}

final _untranslatedMarker = RegExp(r'^\[[A-Z][A-Za-z]+\]\s');

/// Whether two values are the same words differing only in which space
/// characters they use. See the caller for why that is not worth adopting.
bool _sameButForSpacing(String a, String b) =>
    _ordinarySpaces(a) == _ordinarySpaces(b);

String _ordinarySpaces(String value) =>
    value.trim().replaceAll(RegExp(r'[\s\u00a0\u202f\u2009]+'), ' ');

/// A trailing run of label punctuation, with the space Android puts around it.
final _trailingLabelPunctuation = RegExp('[\\s ]*[:.…!]+[\\s ]*\$');

/// The same run, unanchored to whitespace, as the template writes it.
final _trailingPunctuationOnly = RegExp(r'[:.…!]+$');

/// [text] with every trailing run of label punctuation removed.
String _core(String text) {
  var value = text.trim();
  while (true) {
    final next = value.replaceFirst(_trailingLabelPunctuation, '').trim();
    if (next == value || next.isEmpty) return value;
    value = next;
  }
}

String _trailingPunctuation(String english) =>
    _trailingPunctuationOnly.stringMatch(english.trimRight()) ?? '';

/// Gives [value] the first-letter case the template's English has.
///
/// Only upwards. Lowercasing a translation's first letter would be wrong
/// wherever the language capitalises for its own reasons, and nothing here
/// needs it: the template is sentence case throughout.
String _alignInitialCase(String value, String english) {
  if (value.isEmpty || english.isEmpty) return value;
  final first = english[0];
  if (first.toUpperCase() != first || first.toLowerCase() == first) {
    return value;
  }
  return value[0].toUpperCase() + value.substring(1);
}

/// Whether one English text contains the other as a whole phrase.
///
/// The `incorrectAnswer` shape: the port wrote "Incorrect answer" where Kotlin
/// says "Incorrect answer, please try again". Reported, never adopted — the
/// difference may be the whole point of the string.
bool _contains(String a, String b) {
  if (a.length < 8 || b.length < 8) return false; // too short to mean anything
  final left = a.toLowerCase();
  final right = b.toLowerCase();
  return left != right && (left.contains(right) || right.contains(left));
}

/// Android's `\'` and `\"`, carried into an `.arb` before [_unquote] undid them.
final _androidEscape = RegExp('\\\\([\'"])');

class _AdoptCounts {
  const _AdoptCounts(this.values, this.escapes, this.flags);
  final int values;
  final int escapes;
  final int flags;
}

/// Writes the adoptable candidates into [file], in place.
///
/// Existing keys keep their position, so the diff is the values that changed
/// rather than a reordered file; a key the locale did not have at all is
/// appended in template order.
_AdoptCounts _adopt(
  File file,
  Map<String, Object?> arb,
  Map<String, Object?> template,
  List<_Candidate> candidates,
) {
  final apply = {
    for (final candidate in candidates)
      if (candidate.verdict == 'apply') candidate.key: candidate.proposed!,
  };

  final merged = <String, Object?>{};
  var values = 0;
  var escapes = 0;
  for (final entry in arb.entries) {
    final value = entry.value;
    if (value is! String || entry.key.startsWith('@')) {
      merged[entry.key] = value;
      continue;
    }
    final adopted = apply.remove(entry.key);
    if (adopted != null) {
      merged[entry.key] = adopted;
      values++;
      continue;
    }
    final repaired = value.replaceAllMapped(
      _androidEscape,
      (match) => match[1]!,
    );
    if (repaired != value) escapes++;
    merged[entry.key] = repaired;
  }
  // Whatever is left was absent from this locale; append it in template order.
  for (final key in template.keys) {
    final adopted = apply[key];
    if (adopted == null) continue;
    merged[key] = adopted;
    values++;
  }

  final flags = _reconcileMachineTranslationFlags(merged, {
    for (final candidate in candidates)
      // `already` counts as well as `apply`. The flag means "unreviewed machine
      // output, a human still has to look at this", and a value byte-identical
      // to the translation shipping in the Android app has had one — whatever
      // pipeline produced this copy of it. 96 values across ar/es/fr were
      // flagged that way, and the rule dropping a stale flag on an adopted
      // value already said so; it simply never fired on the values that needed
      // no adopting. Nothing a user sees changes: this is marking only.
      if (candidate.verdict == 'apply' ||
          (candidate.verdict == 'already' && candidate.match.tier.isAdoptable))
        candidate.key,
  });
  file.writeAsStringSync('${_encodeArb(merged)}\n');
  return _AdoptCounts(values, escapes, flags);
}

String _oneLine(String? value) =>
    value == null ? 'null' : value.replaceAll('\n', r'\n');

void _printCandidates(
  String locale,
  Map<String, Object?> template,
  Map<String, String> english,
  List<_Candidate> candidates,
  Map<String, Object?> arb,
) {
  stdout.writeln('# $locale');
  for (final verdict in ['apply', 'keep-human', 'report', 'no-unanimous']) {
    final rows = candidates.where((c) => c.verdict == verdict).toList();
    if (rows.isEmpty) continue;
    stdout.writeln('## $verdict (${rows.length})');
    for (final row in rows) {
      final names = row.match.names
          .map((n) => '$n="${_oneLine(english[n])}"')
          .join(', ');
      // Every field on one line. A value with a newline in it would otherwise
      // split its own row in half, and this report is meant to be greppable.
      stdout.writeln(
        '${row.key}\t${row.match.tier.name}'
        '${row.nameResolved ? ' (name-resolved)' : ''}\n'
        '  en   ${_oneLine(template[row.key] as String?)}\n'
        '  xml  $names\n'
        '  was  ${_oneLine(row.current)}\n'
        '  now  ${_oneLine(row.proposed)}',
      );
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

// ---------------------------------------------------------------------------
// Placeholder keys (Phase 121).
//
// Everything above deliberately skipped a template value carrying an ICU
// placeholder. That skip was never about the placeholders being untranslatable
// — it was about not having a safe way to line two different notations up. It
// cost a whole class: **91 of the Kotlin app's 1052 strings carry a `%s`/`%d`
// format specifier**, and each one has a human translation shipping in all five
// locales that the port could not read.
//
// The two notations differ in a way a left-to-right substitution gets wrong.
// Android numbers its arguments (`%1$s`, `%2$s`) and a translator may reorder
// them: `values-ne` writes `download_progress` as
// `%2$d मध्ये %1$d फाइलहरू डाउनलोड भएका छन्` — argument 2 first. ICU names its
// arguments instead, so the conversion has to carry each *argument index* to
// the name that argument means, never to the name sitting in the same position.
// [_FormatString] is that: literal segments plus the ordered ids of the holes
// between them. Two strings describe the same message when their literals are
// identical; zipping their hole lists gives the index → name map, which is then
// applied to the translation wherever *its* holes fall.
//
// The guards, in the order they reject:
//
//   * **ICU plurals and selects are out of scope.** A Kotlin `%d` string is one
//     sentence; `{count, plural, =0{…} =1{…} other{…}}` is three. Filling the
//     `other` branch from the Kotlin and leaving `=0`/`=1` in English would put
//     two languages inside one rendered string, which is worse than the English
//     fallback. 23 of the template's 76 placeholder keys are plurals; they are
//     reported and never derived.
//   * **An unsupported conversion rejects the whole string.** `%.1f` formats a
//     number to one decimal place and `{name}` does not, so dropping the
//     precision would change what the number says.
//   * **A literal with no letter in it is not evidence.** `%1$s (%2$s)` is
//     punctuation; it is byte-identical in all five locales, so deriving from
//     it adds a value that says nothing. Worse, it matches *any* key of that
//     shape — `ratingCompact` ("{average} ({count})") matches Kotlin's
//     `user_name` ("name (logins)") on it, which is a translation of different
//     words. Requiring a letter throws the wrong match out with the useless
//     ones.
//   * **Every declared placeholder must survive.** A translation that drops one
//     renders a sentence with its data missing, which is what
//     `test/l10n/placeholder_integrity_test.dart` exists to stop.
//
// Matching is **exact literal only** — no punctuation or casing tier. Those
// tiers strip trailing punctuation and give the template's back, and a hole
// adjacent to the punctuation makes the result ambiguous for no gain: nothing
// in the corpus matches at those tiers anyway.

/// A format string reduced to its literal segments and the holes between them.
///
/// There is always one more segment than hole, so [parts] and [holes]
/// interleave: `parts[0] holes[0] parts[1] holes[1] … parts[n]`.
///
/// For a Kotlin string a hole id is the argument index as written (`%2$s` →
/// `'2'`, a bare `%s` → its ordinal). For an ARB message it is the placeholder
/// name.
class _FormatString {
  const _FormatString(this.parts, this.holes);

  final List<String> parts;
  final List<String> holes;

  /// The text with every hole removed, which is what makes the two notations
  /// comparable.
  String get literal => parts.join();

  /// Whether the literal carries a word at all — see the header.
  bool get hasWords => _anyLetter.hasMatch(literal);

  /// The string back again, with each hole id mapped through [names] and
  /// written in ICU form. Null when a hole has no mapping.
  String? renderIcu(Map<String, String> names) {
    final buffer = StringBuffer(parts.first);
    for (var i = 0; i < holes.length; i++) {
      final name = names[holes[i]];
      if (name == null) return null;
      buffer.write('{$name}');
      buffer.write(parts[i + 1]);
    }
    return buffer.toString();
  }
}

final _anyLetter = RegExp(r'\p{L}', unicode: true);

/// Every printf conversion Android permits, so an unsupported one is *seen*
/// rather than read as literal text.
final _anySpecifier = RegExp(
  r'%(?:(\d+)\$)?([-#+ 0,(]*\d*(?:\.\d+)?)([a-zA-Z])',
);

/// Parses an Android format string, or returns null when it is not convertible.
///
/// Null means "do not derive from this", never "this has no holes": a string
/// with no specifier parses to an empty [_FormatString.holes].
_FormatString? _parseAndroidFormat(String value) {
  // A brace in the literal would become ICU syntax the moment the value is
  // written into an `.arb`.
  if (value.contains('{') || value.contains('}')) return null;

  final parts = <String>[];
  final holes = <String>[];
  final segment = StringBuffer();
  var explicit = false;
  var implicit = 0;
  var index = 0;
  while (index < value.length) {
    final at = value.indexOf('%', index);
    if (at < 0) {
      segment.write(value.substring(index));
      break;
    }
    segment.write(value.substring(index, at));
    if (value.startsWith('%%', at)) {
      segment.write('%');
      index = at + 2;
      continue;
    }
    final match = _anySpecifier.matchAsPrefix(value, at);
    // A lone `%`, or a conversion carrying flags/width/precision this cannot
    // reproduce: `%.1f` says "one decimal place" and `{value}` does not.
    if (match == null ||
        match.group(2)!.isNotEmpty ||
        !const ['s', 'd'].contains(match.group(3))) {
      return null;
    }
    if (match.group(1) != null) {
      explicit = true;
      holes.add(match.group(1)!);
    } else {
      holes.add('${++implicit}');
    }
    // Android itself throws on a string that mixes the two forms.
    if (explicit && implicit > 0) return null;
    parts.add(segment.toString());
    segment.clear();
    index = match.end;
  }
  parts.add(segment.toString());
  return _FormatString(parts, holes);
}

/// `{name}` occurrences in an ARB message, or null for anything more elaborate.
///
/// A plural or select body is rejected here rather than downstream: its braces
/// are structure, not holes, and reducing it to a literal would compare a
/// three-sentence message against a one-sentence Kotlin string.
_FormatString? _parseIcuFormat(String value) {
  final parts = <String>[];
  final holes = <String>[];
  var index = 0;
  while (index < value.length) {
    final at = value.indexOf('{', index);
    if (at < 0) {
      parts.add(value.substring(index));
      index = value.length;
      break;
    }
    parts.add(value.substring(index, at));
    final close = value.indexOf('}', at);
    if (close < 0) return null;
    final name = value.substring(at + 1, close);
    // `{count, plural, …}`, `{choice, select, …}`, or a nested body.
    if (!_placeholderName.hasMatch(name)) return null;
    holes.add(name);
    index = close + 1;
  }
  if (index >= value.length && parts.length == holes.length) parts.add('');
  if (parts.any((part) => part.contains('}'))) return null;
  return _FormatString(parts, holes);
}

final _placeholderName = RegExp(r'^\w+$');

/// The ICU form of [translation], or null when the conversion is not
/// unambiguous.
///
/// [templateValue] is `app_en.arb`'s message and [kotlinEnglish] the
/// `values/strings.xml` string it was matched to; between them they fix which
/// argument index means which placeholder name. [translation] is that Kotlin
/// string in some locale, free to put those arguments anywhere.
String? convertAndroidFormat({
  required String templateValue,
  required String kotlinEnglish,
  required String translation,
}) {
  final template = _parseIcuFormat(templateValue);
  if (template == null || template.holes.isEmpty || !template.hasWords) {
    return null;
  }
  final english = _parseAndroidFormat(kotlinEnglish.trim());
  if (english == null) return null;
  if (english.literal.trim() != template.literal.trim()) return null;
  if (english.holes.length != template.holes.length) return null;

  // Argument index → placeholder name, read off the two English forms. A repeat
  // must agree with itself, and two indices may not claim one name: either
  // would mean the literals line up by accident rather than by meaning.
  final names = <String, String>{};
  for (var i = 0; i < english.holes.length; i++) {
    final existing = names[english.holes[i]];
    if (existing != null && existing != template.holes[i]) return null;
    if (existing == null && names.containsValue(template.holes[i])) return null;
    names[english.holes[i]] = template.holes[i];
  }

  final localised = _parseAndroidFormat(translation.trim());
  if (localised == null) return null;
  final rendered = localised.renderIcu(names);
  if (rendered == null) return null;

  // Nothing may be lost: the generated getter still takes every declared
  // argument, so a value that drops one discards it silently at render time.
  for (final name in template.holes.toSet()) {
    if (!rendered.contains('{$name}')) return null;
  }
  if (_printfSpecifier.hasMatch(rendered)) return null;
  return rendered;
}
