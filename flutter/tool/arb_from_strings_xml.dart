// Derives `lib/l10n/app_<locale>.arb` from the Kotlin app's
// `res/values-<locale>/strings.xml`.
//
// The Kotlin app is the port's specification for translations too: all five of
// its locales are fully translated (1041 strings each), and those translations
// were paid for. This script carries across the ones that can be matched
// *safely* and leaves the rest absent so `gen-l10n` falls back to English.
//
// Nothing here machine-translates. A key is carried only when one of three
// rules holds. The first two are the rules `app_es.arb` was originally built
// with; the third was added once it became clear that some of the port's copy
// is several Kotlin strings joined together:
//
//   1. The Kotlin string's name normalises to the ARB key (snake_case →
//      camelCase) *and* its English text matches the template exactly.
//   2. No name match, but some Kotlin string's English text matches the
//      template exactly, and every candidate sharing that English has the same
//      translation in the target locale. (Unanimity matters: `values/strings.xml`
//      has several names for one English phrase, and they do not always agree in
//      translation.)
//
//   3. The template value is several Kotlin strings joined by newlines — the
//      onboarding paragraphs, where the Android layout has a `TextView` per
//      line. Every non-blank piece must match some Kotlin English exactly and
//      every piece's candidates must agree, so this is as strict as rule 2,
//      applied per line. See [compositeSegments].
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
// string as unreviewed machine output; 391–439 keys carry it in ar/es/fr and
// 23 in ne/so, where the external pass emitted a `[Nepali] `-style marker
// instead of a translation and Phase 118 deleted those. (Counted at the
// Phase 160 fold. The figure this line carried before was two rounds stale,
// which is the hazard of writing a count into prose at all —
// `--unreviewed` prints the live one.) The merge
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

/// Kotlin translations this script refuses to carry across, keyed by locale
/// and ARB key, with the exact `values-<locale>` string each refusal is about.
///
/// The standing rule is that an English fallback beats a confident wrong
/// translation: a wrong one can mislead a learner where English merely
/// inconveniences them. These two are wrong by the file's own usage, which is
/// checkable without reading Somali:
///
///   * `no_surveys` renders "survey" as *saamayn*, a word `values-so` uses for
///     this nowhere else — its other ten survey strings all say *sahan*
///     (`survey_load_failed`, `redo_survey`, `no_survey_submissions`, …).
///   * `update_health_record` renders "health record" as *dabeecada
///     caafimaadka*, where every other health-record string in the same file
///     says *diiwaan caafimaad* (`unable_to_add_health_record`,
///     `health_record_not_available`, `no_health_records_available`).
///
/// A hapax against ten consistent uses is evidence; both keys were **absent**
/// from `app_so.arb` before Phase 160, so carrying them would replace a clean
/// English fallback rather than a worse translation. That is the trade the rule
/// forbids, which is why this list exists at all rather than the values simply
/// being deleted — a deletion would be undone by the next merge run.
///
/// **This map is an exemption, so it expires in both directions.**
/// `test/l10n/format_derivation_test.dart` asserts each entry still quotes the
/// live `values-<locale>` string: if upstream retranslates one, the quoted text
/// stops matching and the test says to delete the entry. An entry that no
/// longer describes anything is a silent debt, not a guard.
const refusedTranslations = <String, Map<String, String>>{
  'so': {
    'noSurveys': 'saamayn ma jiraan',
    'updateHealth': 'Cusbooneysiinta Dabeecada Caafimaadka',
  },
};

/// Whether [locale] refuses to take [key] from the Kotlin XML.
bool isRefusedTranslation(String locale, String key) =>
    refusedTranslations[locale]?.containsKey(key) ?? false;

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
      final outcome = deriveMergeValue(
        key: key,
        templateValue: templateValue,
        locale: locale,
        english: english,
        translated: translated,
        byCamelCase: byCamelCase,
        byEnglishText: byEnglishText,
        byEnglishFormat: byEnglishFormat,
      );
      if (outcome == null) continue;
      derived[key] = outcome.value;
      if (outcome.byName) {
        byName++;
      } else {
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

/// The value the **merge path** derives for one key, or null when it derives
/// nothing, with `byName` saying which rule produced it (for the run summary).
///
/// Extracted from `main`'s loop so the rules can be driven by a test rather
/// than inferred from the `.arb` files the loop happens to have written. That
/// distinction is the whole reason this exists: the first guard over the
/// composite rule counted occurrences of `compositeSegments(` in this file,
/// which stays at three whether the branch runs or is stranded behind an early
/// `continue` — so it could not fail on the exact defect its own comment
/// named. A test that calls this and asserts a value comes back can.
({String value, bool byName})? deriveMergeValue({
  required String key,
  required String templateValue,
  required String locale,
  required Map<String, String> english,
  required Map<String, String> translated,
  required Map<String, List<String>> byCamelCase,
  required Map<String, List<String>> byEnglishText,
  required Map<String, List<String>> byEnglishFormat,
}) {
  // Kotlin's printf syntax in the *template*. Three keys —
  // `communityEarnings`, `perSurvey`, `yourEarnings` — declare ICU
  // placeholders but write their English with `%1$d`/`%1$s`, which ICU
  // never interpolates: the generated getter takes the argument and drops
  // it, in every language including English. Deriving a translation would
  // spread that defect into the locale files, where the placeholder guard
  // then fails on it. Leave them absent until `app_en.arb` is corrected to
  // `{amount}`/`{status}`.
  if (_printfSpecifier.hasMatch(templateValue)) return null;
  // A translation this locale refuses — see [refusedTranslations].
  if (isRefusedTranslation(locale, key)) return null;

  // A message with ICU placeholders is derived through the format layer at
  // the foot of this file, which lines the two notations up by argument
  // index. Everything else is plain text and matches on the text itself.
  if (templateValue.contains('{')) {
    final format = _parseIcuFormat(templateValue);
    if (format == null || format.holes.isEmpty || !format.hasWords) {
      return null;
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
      return (value: fromName, byName: true);
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
      return (value: proposals.single, byName: false);
    }
    return null;
  }
  final wanted = templateValue.trim();

  final named = byCamelCase[key] ?? const [];
  final exactNamed = named.firstWhere(
    (name) => english[name]?.trim() == wanted,
    orElse: () => '',
  );
  if (exactNamed.isNotEmpty) {
    final value = derivePlainTextValue(
      templateEnglish: templateValue,
      translation: translated[exactNamed],
    );
    if (value != null) return (value: value, byName: true);
  }

  final sameText = byEnglishText[wanted] ?? const [];
  // No `continue` on an empty `sameText`: the composite rule below is the
  // last thing tried, and an early exit here made it unreachable in this
  // path while `--adopt` reached it fine. Three onboarding paragraphs
  // derived on one route and not the other, which is the shape a plain
  // re-run would never have shown — `0 added` reads exactly like `nothing
  // to add`.
  final candidates = sameText
      .map(
        (name) => derivePlainTextValue(
          templateEnglish: templateValue,
          translation: translated[name],
        ),
      )
      .whereType<String>()
      .toSet();
  if (candidates.length == 1) {
    return (value: candidates.single, byName: false);
  }
  // Last resort: the value is several Kotlin strings joined by newlines.
  // Same function `--adopt` uses, so the two paths cannot disagree about
  // what a composite derives to — the divergence that cost the port a
  // character in ten places is documented at [derivePlainTextValue].
  final composite = compositeSegments(templateValue, byEnglishText);
  if (composite == null) return null;
  final joined = deriveCompositeValue(
    templateEnglish: templateValue,
    segments: composite,
    english: english,
    translated: translated,
  );
  if (joined != null) return (value: joined, byName: false);
  return null;
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
// a translation of different words. The English text is the evidence, in four
// adoptable tiers:
//
//   [MatchTier.exact]       identical after trimming. No transformation at all.
//   [MatchTier.punctuation] identical once a trailing `: . … ! *` run and its
//                           surrounding space are removed from both. Kotlin
//                           labels carry the colon the layout draws
//                           (`author` → "Author:") and the asterisk that
//                           marks a required field (`note` → "Note *",
//                           `levels` → "Levels*"); the ARB draws neither. The
//                           translation is stripped the same way, then given the
//                           template's own trailing punctuation back, so the
//                           port's English and its translations agree on it.
//   [MatchTier.casing]      identical once case is also ignored — title case
//                           against sentence case ("Total Visits : " vs "Total
//                           visits"). Only the first character is realigned, and
//                           only upwards: lowercasing a foreign string's first
//                           letter is not safe in general, and no rule here
//                           needs it.
//   [MatchTier.composite]   the value is several Kotlin strings joined by
//                           newlines, each piece matching exactly. Ranked below
//                           the whole-string tiers on purpose: a value that
//                           matches some Kotlin string outright *is* that
//                           string, whatever its newlines say. Alone among the
//                           adoptable tiers it does not go through `_proposal`.
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
  composite,
  nameOnly,
  containment;

  /// Whether `--adopt` may write this tier. The two text-differs tiers are
  /// evidence for a human to read, not a derivation.
  bool get isAdoptable =>
      this == exact ||
      this == punctuation ||
      this == casing ||
      this == composite;
}

/// A Kotlin string (or several sharing one English text) matched to an ARB key.
/// Public so [matchTemplateToKotlin] can be, which is what lets a test ask
/// which tier a key landed at instead of counting this file's own source.
class TemplateMatch {
  const TemplateMatch(this.tier, this.names, {this.segments});

  final MatchTier tier;
  final List<String> names;

  /// For [MatchTier.composite] only: the template value decomposed into the
  /// Kotlin strings it joins. Null for every other tier, where [names] are
  /// alternatives rather than parts — the two readings of one field is why
  /// this exists separately.
  final CompositeMatch? segments;
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
  final TemplateMatch match;
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
  final matches = matchTemplateToKotlin(template, english);

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
      final byKotlinName = recoverProposals(
        locale: locale,
        key: key,
        templateValue: templateValue,
        match: entry.value,
        english: english,
        translated: translated,
      );
      if (byKotlinName == null) continue;
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
        // to recover.
        //
        // "not a disagreement either" used to end that sentence, and the
        // composite tier made it false: a composite whose pieces *do* disagree
        // in this locale, or whose translation carries `{` or `%1$s`, produces
        // no proposal at all and lands here too. Worth knowing before reading
        // a `no-translation` tally as "this locale has nothing".
        verdict = 'no-translation';
      } else if (current != null && proposals.contains(current)) {
        // The value already *is* one of the Kotlin translations — which settles
        // it whether or not the candidates agree with each other. This used to
        // sit below the unanimity test, so a key whose two Kotlin names
        // disagreed was called `no-unanimous` even when the `.arb` carried one
        // of them verbatim, and `_adopt`'s flag reconciliation (which only ever
        // looks at `apply` and `already`) never reached it. `progressFilterCompleted`
        // is the live instance: Kotlin's `completed` and `status_completed` are
        // different Arabic strings, `app_ar.arb` holds `status_completed`'s
        // word for word, and it was still flagged unreviewed machine output.
        // Nothing a user sees changes here — only the marking.
        verdict = 'already';
      } else if (proposals.length != 1) {
        verdict = 'no-unanimous';
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

/// Every proposal the **recovery path** has for one key in one locale, keyed
/// by the Kotlin name that produced it, or null when the locale refuses the
/// key outright.
///
/// Extracted from `_recover` for the same reason [deriveMergeValue] was
/// extracted from `main`: the two refused Somali values are **not** derivable
/// by the merge path at all (their English differs from Kotlin's by case, a
/// tier only `--adopt` has), so a merge-path test of the refusal would pass
/// whether the refusal fired or not — a fixture that cannot distinguish,
/// which is the shape this round has now hit three times. This is where the
/// refusal actually bites, so this is where it has to be driven from.
Map<String, String>? recoverProposals({
  required String locale,
  required String key,
  required String templateValue,
  required TemplateMatch match,
  required Map<String, String> english,
  required Map<String, String> translated,
}) {
  if (isRefusedTranslation(locale, key)) return null;
  // Keyed by Kotlin name, because when two names disagree the name itself
  // is the tiebreak — see below.
  final byKotlinName = <String, String>{};
  final isFormat = templateValue.contains('{');
  final segments = match.segments;
  if (segments != null) {
    // A composite's names are parts, not alternatives, so the loop below
    // — which reads them as candidates that must agree with each other —
    // would be asking the wrong question of them. One proposal or none.
    final composite = deriveCompositeValue(
      templateEnglish: templateValue,
      segments: segments,
      english: english,
      translated: translated,
    );
    if (composite != null) {
      byKotlinName[match.names.join('+')] = composite;
    }
  }
  for (final name in segments != null ? const <String>[] : match.names) {
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
    final proposal = _proposal(match.tier, value, templateValue);
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
    if (isUntranslatedSource(
      localeValue: value,
      kotlinEnglish: english[name] ?? '',
      templateEnglish: templateValue,
    )) {
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
  byKotlinName.removeWhere(
    (_, proposal) => proposal.trim() == templateValue.trim(),
  );
  return byKotlinName;
}

/// Matches every template key to the Kotlin strings whose English it shares,
/// most confident tier first. A key stops at the first tier that hits.
///
/// Exported so a test can assert which tier a key lands at. Counting this
/// file's own text cannot: a tier branch stranded behind an earlier `continue`
/// still contributes its source to the count.
Map<String, TemplateMatch> matchTemplateToKotlin(
  Map<String, Object?> template,
  Map<String, String> english,
) {
  final byCamelCase = <String, List<String>>{};
  for (final name in english.keys) {
    byCamelCase.putIfAbsent(_camelCase(name), () => []).add(name);
  }

  final byEnglishText = <String, List<String>>{};
  for (final entry in english.entries) {
    byEnglishText.putIfAbsent(entry.value.trim(), () => []).add(entry.key);
  }

  final matches = <String, TemplateMatch>{};
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
        matches[key] = TemplateMatch(MatchTier.exact, sameLiteral);
      } else if (byCamelCase.containsKey(key)) {
        matches[key] = TemplateMatch(MatchTier.nameOnly, byCamelCase[key]!);
      }
      continue;
    }

    final exact = <String>[];
    final punctuation = <String>[];
    final casing = <String>[];
    final containment = <String>[];
    final wanted = value.trim();
    final wantedCore = stripLabelPunctuation(wanted);
    for (final entry in english.entries) {
      final other = entry.value.trim();
      if (other.isEmpty) continue;
      if (other == wanted) {
        exact.add(entry.key);
      } else if (stripLabelPunctuation(other) == wantedCore) {
        punctuation.add(entry.key);
      } else if (stripLabelPunctuation(other).toLowerCase() ==
          wantedCore.toLowerCase()) {
        casing.add(entry.key);
      } else if (_contains(other, wantedCore)) {
        containment.add(entry.key);
      }
    }
    final segments = compositeSegments(value, byEnglishText);
    if (exact.isNotEmpty) {
      matches[key] = TemplateMatch(MatchTier.exact, exact);
    } else if (punctuation.isNotEmpty) {
      matches[key] = TemplateMatch(MatchTier.punctuation, punctuation);
    } else if (casing.isNotEmpty) {
      matches[key] = TemplateMatch(MatchTier.casing, casing);
    } else if (segments != null) {
      // Below the whole-string tiers on purpose: a value that matches some
      // Kotlin string outright is that string, whatever its newlines say.
      matches[key] = TemplateMatch(
        MatchTier.composite,
        segments.names,
        segments: segments,
      );
    } else if (byCamelCase.containsKey(key)) {
      matches[key] = TemplateMatch(MatchTier.nameOnly, byCamelCase[key]!);
    } else if (containment.isNotEmpty) {
      matches[key] = TemplateMatch(MatchTier.containment, containment);
    }
  }
  return matches;
}

/// The value to write for [translation], given the tier it matched at.
String _proposal(MatchTier tier, String translation, String templateEnglish) {
  if (tier == MatchTier.exact) {
    return _mirrorTrailingSpace(translation.trim(), templateEnglish);
  }
  var value = stripLabelPunctuation(translation);
  // Give the template's own trailing punctuation back — but only onto a word.
  // Nepali ends a sentence with the danda `।`, which `stripLabelPunctuation` does not strip and
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

/// The value to write for a plain-text key, or null when there is nothing
/// usable to write.
///
/// This is the whole of what the two plain-text derivation rules do with a
/// candidate translation, and it exists as one exported function because both
/// of them used to do it *slightly* differently from the recovery path: they
/// wrote `translated[name]?.trim()`, and `--adopt`'s [_proposal] mirrors the
/// template's trailing space.
///
/// That divergence cost the port a character in ten places. `storage_running_low`
/// and `storage_available` are `"Storage running low: "` / `"Storage available: "`
/// in `values/strings.xml` and carry the same trailing space in all five
/// translated locales; the by-name rule trimmed it out of every one of them,
/// while `selected` and `select_resources` — repaired by hand through `--adopt`
/// — kept theirs. Same four labels, same deliberate space, two derivation paths
/// disagreeing about it. `test/l10n/locale_coverage_test.dart` now guards the
/// output rather than the path, so a third rule cannot reintroduce it.
///
/// A translation that is blank once trimmed is *no* translation and returns
/// null, which is what the `isNotEmpty` guards these two rules already carried
/// were for.
String? derivePlainTextValue({
  required String templateEnglish,
  required String? translation,
}) {
  final value = translation?.trim();
  if (value == null || value.isEmpty) return null;
  return _mirrorTrailingSpace(value, templateEnglish);
}

/// Keeps a trailing space the template carries deliberately.
///
/// `selected` is `"Selected: "` in both `app_en.arb` and the Kotlin XML, where
/// Android's quoting exists precisely to protect that space — it is a label
/// prefix, and the value is drawn straight after it. Trimming the translation
/// would close the gap in every language but English.
///
/// A *leading* space is not mirrored, and no template carries one; if one ever
/// does, this is the function to extend rather than a third one to add.
String _mirrorTrailingSpace(String value, String templateEnglish) =>
    templateEnglish.endsWith(' ') && !value.endsWith(' ') ? '$value ' : value;

/// A template value recognised as several Kotlin strings joined by newlines.
///
/// The port writes some multi-line copy as one ARB value where the Android
/// layout has a `TextView` per line: `onboardingOfflineDescription` is
/// `ob_desc2_1` and `ob_desc2_2` joined with a newline, and the same holds for
/// the other two onboarding paragraphs. Those are the port's longest
/// user-facing prose and the worst place to leave machine output, and no
/// whole-string rule can reach them because the whole string exists nowhere in
/// `values/strings.xml`.
class CompositeMatch {
  const CompositeMatch(this.pieces, this.candidates);

  /// The template value split on newlines, with **every** piece kept —
  /// including the blank ones. `onboardingPowerDescription` separates its
  /// paragraphs with `\n\n`, and a first cut that filtered blanks out before
  /// rejoining silently reflowed a five-line screen into one block.
  final List<String> pieces;

  /// Per piece, the Kotlin names whose English says exactly that. Empty for a
  /// blank piece, which is a separator reproduced verbatim rather than
  /// translated.
  final List<List<String>> candidates;

  /// Every contributing Kotlin name, in order, for reporting.
  List<String> get names => [for (final row in candidates) ...row];
}

/// [templateValue] decomposed, or null when it is not a composite.
///
/// This is a derivation, not a judgement: every non-blank piece must match some
/// Kotlin English **exactly**, there must be more than one of them, and the
/// caller then requires each piece's candidates to agree in the target locale.
/// A single-piece value is rejected because that is just the exact tier, which
/// has already had its turn.
CompositeMatch? compositeSegments(
  String templateValue,
  Map<String, List<String>> byEnglishText,
) {
  if (!templateValue.contains('\n')) return null;
  final pieces = templateValue.split('\n');
  final candidates = <List<String>>[];
  var matched = 0;
  for (final piece in pieces) {
    if (piece.trim().isEmpty) {
      candidates.add(const []);
      continue;
    }
    final names = byEnglishText[piece.trim()];
    if (names == null || names.isEmpty) return null;
    candidates.add(names);
    matched++;
  }
  if (matched < 2) return null;
  return CompositeMatch(pieces, candidates);
}

/// The value to write for a composite key, or null when any piece is missing,
/// blank, untranslated or disputed in this locale.
///
/// Partial recovery is deliberately not offered. Half a paragraph in Nepali and
/// half in English is worse than the English fallback, which is the same
/// reasoning that makes a translation dropping a placeholder unusable.
String? deriveCompositeValue({
  required String templateEnglish,
  required CompositeMatch segments,
  required Map<String, String> english,
  required Map<String, String> translated,
}) {
  final parts = <String>[];
  for (var i = 0; i < segments.candidates.length; i++) {
    final names = segments.candidates[i];
    if (names.isEmpty) {
      parts.add(segments.pieces[i]);
      continue;
    }
    final proposals = <String>{};
    for (final name in names) {
      final value = translated[name]?.trim();
      if (value == null || value.isEmpty) continue;
      // The same floor the plain-text path has: a locale file that still
      // holds the English is not a translation of it.
      //
      // Asked directly rather than through [isUntranslatedSource], whose
      // "only where the Kotlin English differs from the template's" escape —
      // there so an invariant value like "PDF" is not rejected — is vacuous
      // here: a piece matches its Kotlin string exactly, so the two Englishes
      // are always equal and the escape would always fire. A whole sentence
      // that equals its source is untranslated, not invariant.
      if (value == (english[name] ?? '').trim()) continue;
      proposals.add(value);
    }
    if (proposals.length != 1) return null;
    parts.add(proposals.single);
  }
  // Known limitation, latent today: a piece is trimmed, so a *deliberate*
  // trailing space inside a line would be lost — only the whole join is run
  // through `_mirrorTrailingSpace`. Everywhere else in this file treats such a
  // space as load-bearing (see [derivePlainTextValue], and
  // `locale_coverage_test`'s "a deliberate trailing space survives into every
  // locale"). No composite template has one, and none could without the piece
  // also matching a Kotlin string that carries it, so this is recorded rather
  // than fixed — but it is the one place the composite rule disagrees with the
  // file's most-documented convention.
  final joined = parts.join('\n');
  // A translation is a separate string and may carry syntax of its own; the
  // template here is placeholder-free by construction.
  if (joined.contains('{') ||
      joined.contains('}') ||
      _printfSpecifier.hasMatch(joined)) {
    return null;
  }
  if (joined.trim() == templateEnglish.trim()) return null;
  return _mirrorTrailingSpace(joined, templateEnglish);
}

/// Whether [localeValue] is really the Kotlin *source* string sitting
/// untranslated in a `values-<locale>` file, and must therefore not be adopted.
///
/// 47 of the Kotlin app's 1056 strings are byte-identical to their English in at
/// least four of the five locales — `my_survey` is the literal token
/// `mySurveys` in all five, `my_library` is `"mylibrary"` in all five. They are
/// not translations, and adopting one *replaces* a translation with English:
/// `app_ar.arb` holds `استطلاعاتي` for `mySurveys` and `app_fr.arb` holds
/// `Mes enquêtes`, both of which such an adoption would overwrite.
///
/// The caller already drops a proposal equal to the *template's* English, which
/// is the same idea — but only sound while every tier matches English that is
/// byte-identical to the template's. The three whole-string tiers do:
/// `_proposal`
/// normalises a punctuation- or casing-tier value back toward the template, so
/// an untranslated one collapses onto it and is caught. A tier matching on
/// anything looser would not, and the `my*` compound family is exactly what
/// such a tier reaches — the port re-spaced Kotlin's `myLibrary`/`mySurveys`
/// into "My Library"/"My surveys", so those keys sit one notch below the
/// ladder's floor with Kotlin translations that inherit the original defect.
///
/// The fourth adoptable tier, `composite`, does not go through `_proposal`
/// at all: [deriveCompositeValue] asks this question of each piece itself,
/// directly rather than through here, for the reason recorded there.
///
/// So the comparison that actually means "untranslated" is against the Kotlin
/// string's *own* English, and only where that differs from the template's —
/// otherwise this would reject the legitimately invariant values ("HTML",
/// "PDF", "N/A"), which are translations that happen to equal their source.
///
/// **Judge the raw `values-<locale>` string, never the proposal.** The first
/// cut of this took `_proposal`'s output and was inert: `_proposal` runs
/// `_alignInitialCase` for every non-`exact` tier, so `values-ar`'s `mySurveys`
/// arrives as `MySurveys` and no longer equals the `mySurveys` it is a copy of.
/// The guard returned false on both examples its own comment names, and the
/// unit test pinning it passed only because the fixture handed it a `proposal`
/// the pipeline cannot produce — a fabricated join, which is the shape this
/// project has been caught by before. Comparing the untransformed locale value
/// is what makes the question answerable at all: "did this translator leave the
/// source string in place" is a fact about the XML, not about our rendering
/// of it.
///
/// No tier reaches this today; it is the guard that keeps the floor safe by
/// construction rather than by luck. `test/l10n/format_derivation_test.dart`
/// pins it against the real `values-*/strings.xml`, and
/// `test/l10n/placeholder_integrity_test.dart` pins the outcome from the other
/// end, over the shipped `.arb` files.
bool isUntranslatedSource({
  required String localeValue,
  required String kotlinEnglish,
  required String templateEnglish,
}) {
  final source = kotlinEnglish.trim();
  if (source.isEmpty || source == templateEnglish.trim()) return false;
  return localeValue.trim() == source;
}

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
///
/// The `*` is in this class for the same reason the colon is: Android writes
/// the required-field marker into the string itself, because the layout has
/// nowhere else to put it — `note` is "Note *", `levels` is "Levels*",
/// `feedback_type` is "Feedback Type: *" — and every locale carries it
/// through.
///
/// The justification is the **template's own English**, not the port's
/// widgets. `app_en.arb` writes "Note", "Levels", "Feedback type" and "Your
/// feedback" with no marker, so a translation carrying one disagrees with the
/// string it is a translation of. An earlier revision of this comment claimed
/// instead that "the port marks a required field in its own widgets", and an
/// audit disproved it at all six call sites: the port draws a required marker
/// in exactly one place, `take_survey_screen.dart`'s question prompt, and none
/// of these is it. That Kotlin marks six required fields where the port marks
/// none is a real parity gap, and it is nothing to do with this rule.
///
/// A `*` is also never the last character of a sentence, which is why widening
/// the class here cannot eat meaning the way widening it to `?` would.
final _trailingLabelPunctuation = RegExp('[\\s ]*[:.…!*]+[\\s ]*\$');

/// The same run at the *start* of a string — the right-to-left mirror.
///
/// Arabic stores the marker at the logical start, so `values-ar`'s `task` is
/// `"* المهمة"` and its `sync_to_server` puts the colon there too. An
/// end-anchored strip leaves those untouched, and this lane shipped
/// `app_ar.arb`'s `taskNotification` as `"* المهمة"` — a required-field
/// asterisk on a screen that draws none, which is the exact outcome the
/// trailing rule exists to prevent, arriving from the other end.
///
/// Stripping here is safe by measurement rather than by argument: **no** string
/// in `values/strings.xml` begins with a run of this class, so English matching
/// cannot change, and exactly two translations in the whole corpus do — the two
/// above. `test/l10n/format_derivation_test.dart` pins both facts.
final _leadingLabelPunctuation = RegExp('^[\\s ]*[:.…!*]+[\\s ]*');

/// The same run, unanchored to whitespace, as the template writes it.
final _trailingPunctuationOnly = RegExp(r'[:.…!*]+$');

/// [text] with every trailing run of label punctuation removed.
///
/// Exported so `test/l10n/format_derivation_test.dart` can pin the rule rather
/// than only its output: the shipped `.arb` values stay correct after the
/// character class is narrowed again, so a test reading them cannot fail on
/// the change that matters.
String stripLabelPunctuation(String text) {
  var value = text.trim();
  while (true) {
    var next = value.replaceFirst(_trailingLabelPunctuation, '').trim();
    next = next.replaceFirst(_leadingLabelPunctuation, '').trim();
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
      // A composite's names are its parts in order, not alternatives, so they
      // are joined with `+` to read as the concatenation they are. Every other
      // tier's are candidates that had to agree, and read as a list.
      final separator = row.match.segments == null ? ', ' : ' + ';
      final names = row.match.names
          .map((n) => '$n="${_oneLine(english[n])}"')
          .join(separator);
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
  // The same mirror the plain-text rules get through [derivePlainTextValue].
  // `translation.trim()` above strips a deliberate trailing space exactly as
  // `translated[name]?.trim()` used to, and this is a *fourth* derivation path
  // — so "one function, no third rule can disagree" was true of the plain-text
  // rules and not of this one. Unreachable today (no `app_en.arb` value
  // containing a placeholder ends in a space) and closed anyway, because the
  // guard that would catch it — `locale_coverage_test`'s check over every
  // template key ending in a space — would then fail with no way for the tool
  // to produce a passing value, pointing at the test rather than at the gap.
  return _mirrorTrailingSpace(rendered, templateValue);
}
