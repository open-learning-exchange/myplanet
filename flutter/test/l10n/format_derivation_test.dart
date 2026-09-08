import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xml/xml.dart';

import '../../tool/arb_from_strings_xml.dart';

/// Guards the placeholder-key derivation (Phase 121).
///
/// `tool/arb_from_strings_xml.dart` used to skip every message carrying an ICU
/// placeholder, which cost the port a whole class of translations: 91 of the
/// Kotlin app's 1052 strings carry a `%s`/`%d` specifier, and every one of them
/// has a human translation shipping in all five locales.
///
/// The conversion those keys now go through is the part that has to be right
/// rather than merely plausible. Android numbers its arguments and a translator
/// may reorder them; ICU names them. A left-to-right substitution reads
/// correctly on every string where the order happens to match, and silently
/// prints the wrong value into a sentence on the ones where it does not — a
/// defect no other test in this tree could catch and no user could diagnose.
void main() {
  group('argument order', () {
    test('a reordered translation keeps each argument with its meaning', () {
      // `values-ne` writes `download_progress` with argument **2 first**, which
      // is the case that separates an index-aware conversion from a positional
      // one. A left-to-right substitution yields
      // `{completed} मध्ये {total} …` — the two numbers swapped, so a device
      // three files into eight reads "eight of three".
      expect(
        convertAndroidFormat(
          templateValue: '{completed} of {total} files downloaded',
          kotlinEnglish: r'%1$d of %2$d files downloaded',
          translation: _kotlin('ne', 'download_progress'),
        ),
        '{total} मध्ये {completed} फाइलहरू डाउनलोड भएका छन्',
      );
    });

    test('the same conversion is not merely a fixed swap', () {
      // The counterpart, so the test above cannot pass by reversing everything:
      // `values-es` keeps the English order and must come back unreordered.
      expect(
        convertAndroidFormat(
          templateValue: '{completed} of {total} files downloaded',
          kotlinEnglish: r'%1$d of %2$d files downloaded',
          translation: r'%1$d de %2$d archivos descargados',
        ),
        '{completed} de {total} archivos descargados',
      );
    });

    test('a repeated argument maps to one name', () {
      expect(
        convertAndroidFormat(
          templateValue: '{name} said hello to {name}',
          kotlinEnglish: r'%1$s said hello to %1$s',
          translation: r'%1$s a dit bonjour à %1$s',
        ),
        '{name} a dit bonjour à {name}',
      );
    });

    test('an argument the English never named is refused', () {
      // A locale string carrying more arguments than the English it translates
      // has no mapping for the extra one, and guessing would put a name on a
      // value nobody promised.
      expect(
        convertAndroidFormat(
          templateValue: 'Progress {current} of {max}',
          kotlinEnglish: r'Progress %1$s of %2$s',
          translation: r'Progreso %1$s de %2$s (%3$s)',
        ),
        isNull,
      );
    });
  });

  group('guards', () {
    test('an unsupported conversion refuses the whole string', () {
      // `%.1f` says "one decimal place" and `{value}` does not, so converting
      // it would quietly change what the number reads as.
      expect(
        convertAndroidFormat(
          templateValue: 'Rating {value}',
          kotlinEnglish: 'Rating %.1f',
          translation: 'Note %.1f',
        ),
        isNull,
      );
    });

    test('a literal with no word in it is not evidence of anything', () {
      // `%1$s (%2$s)` is punctuation. It is byte-identical in all five locales,
      // so there is nothing to derive — and it matches *any* key of that shape.
      // `ratingCompact` is "{average} ({count})" and Kotlin's `user_name` is
      // a name beside a login count: same punctuation, different words.
      expect(
        convertAndroidFormat(
          templateValue: '{average} ({count})',
          kotlinEnglish: r'%1$s (%2$s)',
          translation: _kotlin('ne', 'user_name'),
        ),
        isNull,
      );
    });

    test('a translation that drops a placeholder is refused', () {
      // The failure `placeholder_integrity_test.dart` exists for: the getter
      // still takes the argument, and renders a sentence with its data missing.
      expect(
        convertAndroidFormat(
          templateValue: 'Progress {current} of {max}',
          kotlinEnglish: r'Progress %1$s of %2$s',
          translation: r'Progreso %1$s',
        ),
        isNull,
      );
    });

    test('a plural template is out of scope', () {
      // A Kotlin `%d` string is one sentence; an ICU plural is three. Filling
      // the `other` branch and leaving `=0`/`=1` in English would put two
      // languages inside one rendered string.
      expect(
        convertAndroidFormat(
          templateValue: '{count, plural, =1{1 file} other{{count} files}}',
          kotlinEnglish: r'%1$d files',
          translation: r'%1$d fichiers',
        ),
        isNull,
      );
    });

    test('a brace in the translation is refused', () {
      // It would be ICU syntax the moment it is written into an `.arb`.
      expect(
        convertAndroidFormat(
          templateValue: 'Progress {current} of {max}',
          kotlinEnglish: r'Progress %1$s of %2$s',
          translation: r'Progreso %1$s de {%2$s}',
        ),
        isNull,
      );
    });

    test('a Kotlin string saying something else is refused', () {
      // The literal is the evidence, and it has to match. This is the guard
      // that stops a name collision attaching a translation of other words.
      expect(
        convertAndroidFormat(
          templateValue: 'Progress {current} of {max}',
          kotlinEnglish: r'Step %1$s of %2$s',
          translation: r'Paso %1$s de %2$s',
        ),
        isNull,
      );
    });

    test('a mix of numbered and bare specifiers is refused', () {
      // Android itself throws on this; there is no order to be faithful to.
      expect(
        convertAndroidFormat(
          templateValue: 'Progress {current} of {max}',
          kotlinEnglish: r'Progress %1$s of %s',
          translation: r'Progreso %1$s de %s',
        ),
        isNull,
      );
    });

    test('a literal percent survives the round trip', () {
      expect(
        convertAndroidFormat(
          templateValue: '{value}% done',
          kotlinEnglish: '%s%% done',
          translation: '%s%% terminé',
        ),
        '{value}% terminé',
      );
    });
  });

  group('the trust floor', () {
    // Phase 141. The tier ladder stops at `casing`, and the 11 template keys
    // that would match only one notch below it — `logOut`/`Logout`,
    // `myLibrary`/`myLibrary`, `profitLoss`/`Profit/Loss`, the rest of the
    // `my*` compound family — are not an oversight waiting to be swept up.
    // That is where the Kotlin data stops being trustworthy: 47 of its 1056
    // strings are byte-identical to their English in at least four of the five
    // locales, and the `my*` family is the worst of them. A tier reaching those
    // keys would have written the token `mySurveys` over the real `استطلاعاتي`
    // and `Mes enquêtes`, in all five locales, and called it a recovery.

    test('an untranslated source string is not a candidate translation', () {
      // `my_survey` is `mySurveys` in `values/strings.xml` and `mySurveys` in
      // every `values-<locale>` file. Whatever tier proposes it, it is English.
      expect(
        isUntranslatedSource(
          proposal: 'mySurveys',
          kotlinEnglish: 'mySurveys',
          templateEnglish: 'My surveys',
        ),
        isTrue,
      );
    });

    test('a real translation of the same key is still a candidate', () {
      expect(
        isUntranslatedSource(
          proposal: 'استطلاعاتي',
          kotlinEnglish: 'mySurveys',
          templateEnglish: 'My surveys',
        ),
        isFalse,
      );
    });

    test('a legitimately invariant value is not mistaken for English', () {
      // The guard must compare against the Kotlin string's own English *and*
      // require it to differ from the template's, or it would reject the values
      // that are correctly identical to their source. `medium_html` is "HTML"
      // in `values/strings.xml` and "HTML" in `values-fr` — that is the French
      // translation, not a missing one, and `app_fr.arb` ships it.
      expect(
        isUntranslatedSource(
          proposal: 'HTML',
          kotlinEnglish: 'HTML',
          templateEnglish: 'HTML',
        ),
        isFalse,
      );
      expect(
        isUntranslatedSource(
          proposal: 'PDF',
          kotlinEnglish: 'PDFs',
          templateEnglish: 'PDFs',
        ),
        isFalse,
      );
    });

    test('an absent source string proposes nothing either way', () {
      expect(
        isUntranslatedSource(
          proposal: 'anything',
          kotlinEnglish: '',
          templateEnglish: 'Something',
        ),
        isFalse,
      );
    });
  });

  group('plain text', () {
    // Not a placeholder concern, but the same file: this is where the tool's
    // derivation rules are tested, and the plain-text rules turned out to
    // disagree with the recovery path about one character.
    //
    // Android's quoting is how a trailing space survives in `strings.xml`, and
    // four template strings rely on it — every one a label a value is drawn
    // straight after. The two plain-text rules wrote `translated[name]?.trim()`
    // and stripped it; `--adopt`'s `_proposal` mirrors it. So `selected` and
    // `select_resources`, repaired by hand, kept their space while
    // `storage_running_low` and `storage_available`, derived by the tool, lost
    // it in all five locales.

    test('a trailing space the template carries is kept', () {
      // The real Arabic value, read from the XML rather than written here, so
      // this cannot pass against a stale copy. `"التخزين قليل: "` — Android's
      // quotes are the XML's, the space inside them is the string's.
      final arabic = _kotlin('ar', 'storage_running_low');
      expect(arabic, endsWith(' '), reason: 'the XML premise of this test');
      expect(
        derivePlainTextValue(
          templateEnglish: 'Storage running low: ',
          translation: arabic,
        ),
        endsWith(' '),
      );
    });

    test('a translation that never had the space is given it', () {
      // `values-ar/select_resources` is unquoted and so carries no trailing
      // space at all, where the other four locales do. The template's space is
      // the one that matters — it is the app's own layout decision — so the
      // mirror adds it rather than trusting each translator to have kept it.
      // This is why the fix is a mirror and not a "don't trim".
      expect(_kotlin('ar', 'select_resources'), isNot(endsWith(' ')));
      expect(
        derivePlainTextValue(
          templateEnglish: 'Select resources: ',
          translation: _kotlin('ar', 'select_resources'),
        ),
        'اختر الموارد: ',
      );
    });

    test('a template with no trailing space still gets a trimmed value', () {
      // The mirror is one-directional: it never *removes* whitespace the
      // template does not ask for, and the surrounding trim still runs. A
      // regression that mirrored in both directions would start writing
      // trailing spaces into 890 ordinary strings.
      expect(
        derivePlainTextValue(
          templateEnglish: 'Delete',
          translation: '  Supprimer  ',
        ),
        'Supprimer',
      );
    });

    test('the format path mirrors a trailing space too', () {
      // The fourth derivation path, and the one the phase's own fix did *not*
      // cover until the second audit pass pointed at it. No template value
      // carrying a placeholder ends in a space today, so this is a guard on a
      // shape rather than on live data — which is the point: the plain-text
      // fix's dartdoc claimed no further rule could disagree, and this one
      // could.
      expect(
        convertAndroidFormat(
          templateValue: 'Searching in {folder}: ',
          kotlinEnglish: r'Searching in %1$s: ',
          translation: r'Recherche dans %1$s :',
        ),
        'Recherche dans {folder} : ',
      );
      // And still one-directional: a template with no trailing space is
      // unaffected, which the group's other tests would catch but not state.
      expect(
        convertAndroidFormat(
          templateValue: 'Searching in {folder}',
          kotlinEnglish: r'Searching in %1$s',
          translation: r'Recherche dans %1$s ',
        ),
        'Recherche dans {folder}',
      );
    });

    test('a blank or absent translation derives nothing', () {
      // Both plain-text rules carried an `isNotEmpty` guard before this
      // function existed, and it is load-bearing: writing `""` into a locale
      // file displaces the English fallback with nothing at all, which
      // `locale_coverage_test`'s non-empty check would then fail on.
      expect(
        derivePlainTextValue(templateEnglish: 'Delete', translation: null),
        isNull,
      );
      expect(
        derivePlainTextValue(templateEnglish: 'Delete', translation: '   '),
        isNull,
      );
      // Including when the template ends in a space — the mirror must not
      // turn nothing into a lone space.
      expect(
        derivePlainTextValue(templateEnglish: 'Selected: ', translation: ''),
        isNull,
      );
    });
  });

  group('what shipped', () {
    // The derived values, pinned against the Kotlin XML rather than copied
    // here: the claim is that the `.arb` tracks the Android app's translations,
    // and a hardcoded expectation would pass just as happily against a stale
    // value. Every one of these was absent — the whole key, in every locale —
    // before this phase, so the alternative each replaces is English.
    const derived = {
      'courseProgressCount': ('course_progress', ['current', 'max']),
      'fileNotFound': ('file_not_found', ['fileName']),
      'appVersion': ('version', ['version']),
      'fileCountMany': ('file_count_many', ['count']),
      'reportDateDetails': ('report_date_details', ['created', 'updated']),
      'storageSelectedCount': ('storage_selected_count', ['count']),
      'currentCv': ('current_cv', ['name']),
    };

    final english = _readArb('en');

    for (final entry in derived.entries) {
      final (kotlinName, names) = entry.value;
      test('${entry.key} is the Kotlin ${entry.value.$1}, everywhere', () {
        for (final locale in const ['ar', 'es', 'fr', 'ne', 'so']) {
          final value = _readArb(locale)[entry.key];
          if (value == null) continue; // reported in the phase notes
          final expected = convertAndroidFormat(
            templateValue: english[entry.key]! as String,
            kotlinEnglish: _kotlin('en', kotlinName),
            translation: _kotlin(locale, kotlinName),
          );
          // Compared with the space characters normalised, because one
          // derived value deliberately differs from the XML by exactly that:
          // `app_fr.arb` writes a no-break space before the colon where
          // `values-fr` writes an ordinary one, and French typography wants
          // the former. The words are what this pins.
          expect(
            _spacing(value as String),
            _spacing(expected!),
            reason:
                'app_$locale.arb has drifted from `$kotlinName` in the Kotlin '
                'app, or the conversion changed',
          );
          for (final name in names) {
            expect(
              value,
              contains('{$name}'),
              reason: '$locale:${entry.key} lost the "$name" placeholder',
            );
          }
        }
      });
    }

    test('French keeps its no-break space before a colon', () {
      // The one place a derived value is deliberately not byte-identical to the
      // Kotlin XML. `--adopt` treats "same words, different space character" as
      // nothing to adopt: there is no translation to gain and a typographic
      // nicety to lose.
      expect(
        _readArb('fr')['reportDateDetails'],
        'Rapport créé le\u{a0}: {created} | Mis à jour le\u{a0}: {updated}',
      );
    });

    test('the Spanish course-progress header is Spanish', () {
      // The instance Phase 117 handed over, spelled out because it is the one
      // a reader can check without reading the tool: Kotlin has shipped
      // "Progreso %1$s de %2$s" all along, and the port rendered "Progress 3
      // of 8" to a Spanish learner because the derivation skipped the key.
      expect(
        _readArb('es')['courseProgressCount'],
        'Progreso {current} de {max}',
      );
      expect(
        _readArb('ne')['courseProgressCount'],
        '{current} को {max} प्रगति',
      );
    });
  });
}

/// Every run of whitespace as one ordinary space — see the caller.
String _spacing(String value) =>
    value.trim().replaceAll(RegExp(r'[\s\u00a0\u202f\u2009]+'), ' ');

Map<String, Object?> _readArb(String locale) =>
    jsonDecode(File('lib/l10n/app_$locale.arb').readAsStringSync())
        as Map<String, Object?>;

/// One string out of a `values*` directory, with Android's quoting undone —
/// the same reading `tool/arb_from_strings_xml.dart` does.
String _kotlin(String locale, String name) {
  final dir = locale == 'en' ? 'values' : 'values-$locale';
  final document = XmlDocument.parse(
    File('../app/src/main/res/$dir/strings.xml').readAsStringSync(),
  );
  for (final element in document.findAllElements('string')) {
    if (element.getAttribute('name') != name) continue;
    final raw = element.innerText;
    return raw.length > 1 && raw.startsWith('"') && raw.endsWith('"')
        ? raw.substring(1, raw.length - 1)
        : raw;
  }
  throw StateError('no <string name="$name"> in $dir');
}
