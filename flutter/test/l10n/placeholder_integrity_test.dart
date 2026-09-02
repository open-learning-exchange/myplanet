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
  // ~560 keys per locale are Google-Translate output with no human review,
  // sitting indistinguishably beside the ~250 Phase 47 strings derived from the
  // Kotlin `values-*/strings.xml` — translations already shipping in the
  // Android app. `"@<key>": {"x-mt": true}` in the locale file marks the
  // unreviewed ones, so a reviewer can list precisely what still needs a human.
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
    const humanReviewed = {
      'ar': 260,
      'es': 235,
      'fr': 261,
      'ne': 260,
      'so': 260,
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
