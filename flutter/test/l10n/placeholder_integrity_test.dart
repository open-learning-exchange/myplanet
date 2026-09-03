import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/providers/settings_provider.dart';

/// Guards the *values* in the locale files, which nothing checked before.
///
/// `test/l10n/locale_coverage_test.dart` guards the locale set and the key set:
/// that every language the picker offers has an `.arb`, that no locale invents
/// a key, that none is blank or declared twice. All of it is structural — it
/// looks at which keys exist, never at what is inside them.
///
/// That gap shipped a real regression. An external translation pass filled the
/// five locale files from ~250 keys to ~850, and it tokenised placeholders
/// before translating without restoring them afterwards: Arabic carried 38
/// strings with a literal `__0____` where a value belongs
/// (`ratingOutOfFive` → `'التقييم: __0____ من 5'`), and 20 more across all five
/// locales simply dropped a placeholder the template declares. 58 strings that
/// could not render their own data.
///
/// `flutter analyze` caught **one** of the 58, and only by accident: a dropped
/// placeholder is a lint solely when `gen-l10n` happens to emit an unused local
/// for it. The other 57 compiled, analyzed clean, and would have shipped. These
/// tests are what should have caught them.
void main() {
  final template = _readArb('en');

  /// Every locale that ships a translation file. `en` is the template, so it
  /// is the authority here rather than a subject.
  final locales = LocaleNotifier.supportedLanguageCodes
      .where((code) => code != 'en')
      .toList();

  test('no locale value carries a tokenisation artefact', () {
    // The failure this exists for. A translator that swaps `{count}` for a
    // sentinel and never swaps it back leaves text that renders literally —
    // the user sees `__0____`, not their rating.
    //
    // No value in any locale legitimately contains a doubled underscore or a
    // printf specifier today, so the nets are deliberately wider than the one
    // shape that got through: the next tokeniser will use a different sentinel.
    final defects = <String>[];
    for (final code in locales) {
      final arb = _readArb(code);
      for (final key in _messageKeys(arb)) {
        final value = arb[key] as String;
        for (final artefact in _artefacts) {
          if (RegExp(artefact.pattern).hasMatch(value)) {
            defects.add('$code:$key contains ${artefact.name} — "$value"');
          }
        }
      }
    }

    // Every defect at once, not just the first: the point of this guard is that
    // one run tells you the whole extent of the damage.
    expect(
      defects,
      isEmpty,
      reason:
          '${defects.length} value(s) carry a placeholder that was tokenised '
          'for translation and never restored:\n${defects.join("\n")}\n'
          'Put the ICU placeholder back, or drop the key so it falls back to '
          'English.',
    );
  });

  test('every locale value keeps the placeholders the template declares', () {
    // The other half of the same regression, and the half nothing could see:
    // a value that quietly omits `{count}` renders as a grammatical sentence
    // with the number missing.
    //
    // The authority is `app_en.arb`'s `@<key>.placeholders` metadata, **not** a
    // regex over `{...}` in the English text. An ICU plural body is full of
    // braces that are not placeholders — `{count, plural, =1{one file} other{}}`
    // yields spurious names like `1`, `No` and `Are`, and the first pass at
    // this analysis reported 54 "missing" placeholders where only 20 were real.
    // The metadata says exactly which names are values.
    final defects = <String>[];
    for (final code in locales) {
      final arb = _readArb(code);
      for (final key in _messageKeys(arb)) {
        final value = arb[key] as String;
        for (final name in _declaredPlaceholders(template, key)) {
          if (!_usesPlaceholder(value, name)) {
            defects.add('$code:$key drops "$name" — "$value"');
          }
        }
      }
    }

    expect(
      defects,
      isEmpty,
      reason:
          '${defects.length} value(s) drop a placeholder app_en.arb declares:'
          '\n${defects.join("\n")}\n'
          'The generated getter still takes the argument, so the value it was '
          'given is silently discarded at render time.',
    );
  });

  // ---------------------------------------------------------------------
  // Machine-translation marking (Phase 109).
  //
  // 496–545 keys in Arabic, Spanish and French are Google-Translate output
  // with no human review, sitting indistinguishably beside the strings derived
  // from the Kotlin `values-*/strings.xml` — translations already shipping in
  // the Android app. `"@<key>": {"x-mt": true}` in the locale file marks the
  // unreviewed ones, so a reviewer can list precisely what still needs a human.
  //
  // Nepali and Somali carry 26 each, and that is not because they were
  // reviewed: the external pass never translated them at all. It emitted
  // `[Nepali] `/`[Somali] ` plus the English for 899 keys, which Phase 118
  // deleted (see the marker test above), leaving 26 whose machine output
  // happened to equal the English.
  // ---------------------------------------------------------------------

  test('every locale marks its machine-translated strings', () {
    for (final code in locales) {
      expect(
        _machineTranslatedKeys(_readArb(code)),
        isNotEmpty,
        reason:
            'app_$code.arb carries no "x-mt" flags at all. Either every string '
            'has been human-reviewed — in which case say so in the phase notes '
            '— or a regeneration stripped the marking.',
      );
    }
  });

  test('no x-mt flag outlives the string it marks', () {
    // A flag is attached to its key rather than kept in a central list
    // precisely so the two cannot drift apart. This is the one way they still
    // can: delete the translation, leave the `@key` block behind, and the flag
    // then lands on whatever value is added for that key next — mislabelling a
    // human translation as unreviewed.
    for (final code in locales) {
      final arb = _readArb(code);
      final translated = _messageKeys(arb).toSet();
      for (final key in _machineTranslatedKeys(arb)) {
        expect(
          translated,
          contains(key),
          reason:
              'app_$code.arb flags "$key" as machine-translated but has no '
              '"$key" value — delete the orphaned "@$key" block',
        );
      }
    }
  });

  test('no locale value carries an Android escape', () {
    // Android escapes an apostrophe in `strings.xml` as `\'`, because the
    // platform's own string reader would otherwise treat it as quoting. The
    // backslash belongs to Android, not to the sentence — but XML parsing
    // leaves it alone (it is not XML syntax) and JSON has no objection to it
    // either, so `tool/arb_from_strings_xml.dart` carried it into the locale
    // files before it learned to strip it.
    //
    // Fourteen French strings and one Somali one shipped that way, rendering a
    // literal backslash on screen: `Demandes d\'adhésion`, `Su\'aal`. Nothing
    // could see it — the value is well-formed JSON, `gen-l10n` compiles it, and
    // no analyzer looks inside a string. Only a reader of French would notice.
    final defects = <String>[];
    for (final code in locales) {
      final arb = _readArb(code);
      for (final key in _messageKeys(arb)) {
        final value = arb[key] as String;
        if (RegExp("""\\\\['\\"]""").hasMatch(value)) {
          defects.add('$code:$key — "$value"');
        }
      }
    }

    expect(
      defects,
      isEmpty,
      reason:
          '${defects.length} value(s) carry an Android escape that the Kotlin '
          'XML needed and the ARB does not:\n${defects.join("\n")}\n'
          'Run `dart tool/arb_from_strings_xml.dart --adopt`, which repairs '
          'them.',
    );
  });

  test('the incorrect-answer retry hint is the Kotlin string, everywhere', () {
    // The instance this recovery pass started from, pinned so a regeneration
    // cannot quietly take it back.
    //
    // The port minted its own "Incorrect answer" and had it machine-translated
    // five ways, while Kotlin has shipped `incorrect_ans` — "Incorrect answer,
    // please try again" — with a real translation in every locale for years.
    // Under the exam retry gate this snackbar is the only thing telling a
    // learner to try again, so the hint is the string, not a flourish.
    //
    // The Kotlin XML is read rather than the six strings copied here: the point
    // is that the ARB tracks Kotlin, and a hardcoded expectation would pass
    // just as happily against a stale value.
    for (final code in [...locales, 'en']) {
      final kotlin = _kotlinString(code == 'en' ? 'values' : 'values-$code');
      expect(
        _readArb(code)['incorrectAnswer'],
        kotlin,
        reason:
            'app_$code.arb has drifted from `incorrect_ans` in '
            'app/src/main/res/values${code == 'en' ? '' : '-$code'}/strings.xml',
      );
    }
  });

  test('no locale value is a language marker wearing a translation\'s clothes', () {
    // The other half of the external pass's damage, and the larger half. Where
    // the tokenisation defect broke 58 strings that tried to render data, this
    // one filled 899 keys with the tool's own "nothing here" output: the value
    // was `[Nepali] ` or `[Somali] ` followed by the English template verbatim,
    // flagged `x-mt`, present in the file, and therefore *preferred over the
    // English fallback*. A Somali user read `[Somali] Join requests` — not a
    // translation, not even a clean untranslated string, but a bracketed tag
    // that reads as a bug.
    //
    // Nothing could see it. The value is well-formed JSON with the right
    // placeholders, `gen-l10n` compiles it, and every structural test passes:
    // the key exists, is non-empty, and is honestly flagged unreviewed. Only
    // reading the text finds it. Phase 114 named two instances; Phase 118
    // counted the rest and deleted them, so those keys fall back to English —
    // the same words, without the tag.
    //
    // The net is wider than the two markers that shipped, because the next tool
    // will pick a different one: no legitimate value in any locale, the
    // template included, opens with a bracketed word.
    final defects = <String>[];
    for (final code in locales) {
      final arb = _readArb(code);
      for (final key in _messageKeys(arb)) {
        final value = arb[key] as String;
        if (RegExp(r'^\s*\[[^\]]{1,20}\]').hasMatch(value)) {
          defects.add('$code:$key opens with a marker — "$value"');
        }
      }
    }

    expect(
      defects,
      isEmpty,
      reason:
          'A value that only labels the language it was not translated into is '
          'worse than no value at all: it displaces the English fallback with '
          'the same English plus a tag. Delete the key (and its "@key" block) '
          'so the fallback serves it, or translate it.\n${defects.join('\n')}',
    );
  });

  test('the template declares no review state of its own', () {
    // `app_en.arb` is the source text, not a translation of anything. An
    // `x-mt` flag there would mean the English itself is machine output.
    expect(_machineTranslatedKeys(template), isEmpty);
  });

  test('the human-reviewed string counts are what the notes claim', () {
    // Pins the marking against silent staleness. A future harvest that adds
    // translations without deciding their review state shows up here as a
    // count that moved, rather than as several hundred unreviewed strings
    // indistinguishable from the reviewed ones — which is exactly the state
    // this phase was opened to end.
    //
    // Adding a *reviewed* translation is meant to move these numbers: update
    // the map and say in the commit message who reviewed it. Adding an
    // unreviewed one means flagging it `x-mt`, which leaves them alone.
    //
    // Phase 121 moved all five, in two ways worth separating. 33 values across
    // the five locales are *new* human translations, derived from the Kotlin
    // XML for keys carrying an ICU placeholder — a class the derivation tool
    // used to skip outright. The other 259 (ar 62, es 122, fr 75) are values
    // that were already byte-identical to the translation shipping in the
    // Android app and were nonetheless flagged unreviewed machine output. The
    // flag means "a human still has to look at this", and one has; nothing a
    // user sees changed when they were cleared. If that reading is ever
    // rejected, `_reconcileMachineTranslationFlags`' `already` branch is the
    // one line to revert.
    const humanReviewed = {
      'ar': 395,
      'es': 448,
      'fr': 394,
      'ne': 396,
      'so': 396,
    };

    for (final code in locales) {
      final arb = _readArb(code);
      final unflagged =
          _messageKeys(arb).length - _machineTranslatedKeys(arb).length;
      expect(
        unflagged,
        humanReviewed[code],
        reason:
            'app_$code.arb has $unflagged strings with no "x-mt" flag, i.e. '
            '$unflagged claimed as human-reviewed, but the pinned count is '
            '${humanReviewed[code]}. If you reviewed strings or added a '
            'reviewed translation, update this map. If you added a translation '
            'nobody has reviewed, flag it "x-mt" instead.',
      );
    }
  });
}

/// Patterns that mean "a value was substituted out and never substituted back".
const _artefacts = [
  (
    name: 'a tokenisation sentinel (__0__)',
    // The exact shape that shipped: `__0____`, `__1__`.
    pattern: r'_{2,}\d+_{2,}',
  ),
  (
    name: 'a run of underscores',
    // Wider net for the same class of failure with a different sentinel. No
    // legitimate value in any locale contains one.
    pattern: r'_{2,}',
  ),
  (
    name: 'a printf format specifier',
    // Kotlin's `%1$s`/`%1$d`, which ICU does not interpolate. Three template
    // keys (`communityEarnings`, `perSurvey`, `yourEarnings`) still carry these
    // in their *English* text while declaring ICU placeholders, so they render
    // the literal specifier and drop the argument in every language. Deriving a
    // translation for one of them from the Kotlin XML would carry the defect
    // into the locale files; `tool/arb_from_strings_xml.dart` skips them for
    // that reason, and this fails if anything else reintroduces one.
    pattern: r'%\d*\$?[sd]',
  ),
];

Map<String, Object?> _readArb(String locale) =>
    jsonDecode(File('lib/l10n/app_$locale.arb').readAsStringSync())
        as Map<String, Object?>;

/// Translatable entries — everything that is not `@@locale` or an `@key` block.
Iterable<String> _messageKeys(Map<String, Object?> arb) =>
    arb.keys.where((key) => !key.startsWith('@') && arb[key] is String);

/// Placeholder names `app_en.arb` declares for [key], in declaration order.
Iterable<String> _declaredPlaceholders(Map<String, Object?> arb, String key) {
  final meta = arb['@$key'];
  if (meta is! Map<String, Object?>) return const [];
  final placeholders = meta['placeholders'];
  if (placeholders is! Map<String, Object?>) return const [];
  return placeholders.keys;
}

/// Keys the locale file flags as unreviewed machine output.
Iterable<String> _machineTranslatedKeys(Map<String, Object?> arb) => arb.keys
    .where((key) => key.startsWith('@') && key != '@@locale')
    .where((key) {
      final meta = arb[key];
      return meta is Map<String, Object?> && meta['x-mt'] == true;
    })
    .map((key) => key.substring(1));

/// Whether [value] interpolates [name].
///
/// `{name}` and the ICU forms that qualify it — `{count, plural, ...}`,
/// `{choice, select, ...}` — all count; whitespace inside the braces is legal
/// ICU and appears in the wild.
bool _usesPlaceholder(String value, String name) =>
    RegExp('\\{\\s*${RegExp.escape(name)}\\s*[,}]').hasMatch(value);

/// `incorrect_ans` as the Kotlin app ships it for one `values*` directory.
///
/// A regex rather than an XML parser: the test needs one string, `strings.xml`
/// writes it on one line, and pulling a parser into the test tree to read it
/// would be the larger dependency.
String _kotlinString(String valuesDir) {
  final xml = File(
    '../app/src/main/res/$valuesDir/strings.xml',
  ).readAsStringSync();
  final match = RegExp(
    r'<string name="incorrect_ans">(.*?)</string>',
  ).firstMatch(xml);
  return match!.group(1)!;
}
