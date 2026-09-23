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
    // That is where the Kotlin data stops being trustworthy: 47 of its 1055
    // translatable strings are byte-identical to their English in at least four
    // of the five locales, and the `my*` family is the worst of them. A tier
    // reaching those keys would have written the token `mySurveys` over the real
    // `استطلاعاتي` and `Mes enquêtes`, and called it a recovered translation.
    //
    // These read the real `values-*/strings.xml` rather than hand-made inputs.
    // The first cut of this group did hand-make them, and that is exactly why
    // it certified a guard that never fired: it passed `proposal: 'mySurveys'`,
    // a string the pipeline cannot produce, because `_proposal` case-aligns
    // every non-`exact` tier and hands the guard `MySurveys`.

    test('an untranslated source string is not a candidate translation', () {
      // `my_survey` is `mySurveys` in `values/strings.xml` and left at
      // `mySurveys` in Arabic, French, Nepali and Somali.
      for (final code in ['ar', 'fr', 'ne', 'so']) {
        for (final name in ['my_survey', 'my_library']) {
          expect(
            isUntranslatedSource(
              localeValue: _kotlin(code, name),
              kotlinEnglish: _kotlin('en', name),
              templateEnglish: name == 'my_survey'
                  ? 'My surveys'
                  : 'My Library',
            ),
            isTrue,
            reason:
                '$code/$name is "${_kotlin(code, name)}" against the English '
                '"${_kotlin('en', name)}" and must be refused',
          );
        }
      }
    });

    test('the one locale that did translate them is still a candidate', () {
      // Spanish is why the headline figure is "four of five" and not "all
      // five": it renders both, so both are real candidates there.
      expect(_kotlin('es', 'my_survey'), 'misEncuestas');
      expect(_kotlin('es', 'my_library'), ' miBiblioteca');
      for (final name in ['my_survey', 'my_library']) {
        expect(
          isUntranslatedSource(
            localeValue: _kotlin('es', name),
            kotlinEnglish: _kotlin('en', name),
            templateEnglish: name == 'my_survey' ? 'My surveys' : 'My Library',
          ),
          isFalse,
          reason: 'es/$name is a real translation',
        );
      }
    });

    test('a legitimately invariant value is not mistaken for English', () {
      // The guard must compare against the Kotlin string's own English *and*
      // require it to differ from the template's, or it would reject the values
      // that are correctly identical to their source. `medium_html` is "HTML"
      // in `values/strings.xml` and "HTML" in `values-fr` — that is the French
      // translation, not a missing one, and `app_fr.arb` ships it.
      expect(_kotlin('fr', 'medium_html'), _kotlin('en', 'medium_html'));
      expect(
        isUntranslatedSource(
          localeValue: _kotlin('fr', 'medium_html'),
          kotlinEnglish: _kotlin('en', 'medium_html'),
          templateEnglish: 'HTML',
        ),
        isFalse,
      );
    });

    test('an absent source string proposes nothing either way', () {
      expect(
        isUntranslatedSource(
          localeValue: 'anything',
          kotlinEnglish: '',
          templateEnglish: 'Something',
        ),
        isFalse,
      );
    });
  });

  group('the required-field asterisk', () {
    // Phase 160. Android writes the required-field marker into the string
    // because the layout has nowhere else to put it, exactly as it writes the
    // colon a label draws: `note` is "Note *", `levels` is "Levels*",
    // `feedback_type` is "Feedback Type: *". Every locale carries the marker
    // through.
    //
    // The justification is the **template's own English**: `app_en.arb` writes
    // "Note", "Levels", "Feedback type" and "Your feedback" with no marker, so
    // a translation carrying one disagrees with the string it translates. A
    // first cut of this comment said instead that "the port marks a required
    // field in its own widgets", and an audit disproved it at all six call
    // sites — the port draws a required marker in exactly one place,
    // `take_survey_screen.dart`'s question prompt, and none of these is it.
    //
    // These pin the *rule*, not the shipped values. The five keys the change
    // recovered stay correct in the `.arb` after the class is narrowed again —
    // a test reading them could not fail on the change that matters, which is
    // this file's own standing lesson about a fixture that cannot distinguish.

    test('a trailing asterisk is label punctuation', () {
      expect(stripLabelPunctuation(_kotlin('en', 'note')), 'Note');
      expect(stripLabelPunctuation(_kotlin('en', 'levels')), 'Levels');
      expect(
        stripLabelPunctuation(_kotlin('en', 'your_feedback')),
        'Your Feedback',
      );
      expect(stripLabelPunctuation(_kotlin('en', 'task')), 'Task');
      // A run mixing the two, stripped whichever order they come in.
      expect(
        stripLabelPunctuation(_kotlin('en', 'feedback_type')),
        'Feedback Type',
      );
    });

    test('it is stripped out of the translation the same way', () {
      // The marker is in the locale files too, so leaving it there would put a
      // required-field asterisk on a screen that does not draw one.
      expect(_kotlin('ne', 'note'), 'नोट *');
      expect(stripLabelPunctuation(_kotlin('ne', 'note')), 'नोट');
      expect(_readArb('ne')['note'], 'नोट');
    });

    test('only a trailing run, and only punctuation', () {
      // An asterisk in the middle is content and is left alone.
      expect(stripLabelPunctuation('2 * 3 = 6'), '2 * 3 = 6');
      expect(stripLabelPunctuation('a *bold* word'), 'a *bold* word');
      // A *leading* run is stripped, because Arabic puts the marker there —
      // see the group below. That does mean a string opening with markdown
      // emphasis would lose its first `*`, which is safe here by measurement
      // rather than by construction: no string in `values/strings.xml` begins
      // with a run of this class, and `no English string has one, so matching
      // cannot change` is the test that keeps it that way. The two Kotlin
      // strings that *do* use `*` for markdown bold (`community_earnings`,
      // `your_earnings`) both carry `%1$d`, so `_printfSpecifier` rejects them
      // in both derivation paths long before any stripping.
      expect(stripLabelPunctuation('*emphasis* here'), 'emphasis* here');
      // A question mark is not in the class and must not join it: "Is your
      // feedback Urgent? *" keeps its question mark, which is why `isUrgent`
      // needed a template edit rather than riding this rule.
      expect(
        stripLabelPunctuation(_kotlin('en', 'is_urgent')),
        'Is your feedback Urgent?',
      );
      // And the widened class must not turn a near-miss into a match:
      // `subject` is "Subject(s)*:", whose core is "Subject(s)" and not the
      // template's "Subject".
      expect(stripLabelPunctuation(_kotlin('en', 'subject')), 'Subject(s)');
    });
  });

  group('the marker at the other end', () {
    // An audit found `app_ar.arb` shipping `taskNotification` as "* المهمة":
    // Arabic stores the required-field marker at the *logical start*, and the
    // strip was end-anchored. A required-field asterisk on a screen that draws
    // none — the exact outcome the trailing rule exists to prevent, arriving
    // from the other end, and past the test written to catch it, because that
    // test's fixture (`ne/note`) has the marker trailing and so could not tell
    // the two readings apart. The Phase 156 shape, inside the rule meant to
    // catch it.

    test('a leading run is stripped too', () {
      expect(stripLabelPunctuation(_kotlin('ar', 'task')), 'المهمة');
      expect(_readArb('ar')['taskNotification'], 'المهمة');
    });

    test('no English string has one, so matching cannot change', () {
      // This is why stripping from the front is safe by measurement rather
      // than by argument. If a Kotlin English string ever begins with a run of
      // this class, the leading strip starts affecting which keys match which
      // strings, and that has to be thought about rather than assumed.
      final leading = RegExp('^[\\s\u00a0]*[:.…!*]+');
      final offenders = <String>[];
      for (final entry in _allKotlin('en').entries) {
        if (entry.value.trim().isEmpty) continue;
        if (leading.hasMatch(entry.value)) offenders.add(entry.key);
      }
      expect(offenders, isEmpty);
    });

    test('exactly three translations in the corpus have one', () {
      // All three Arabic, all three the right-to-left mirror. If this count
      // moves, a new one arrived and wants reading before it is stripped.
      //
      // `deadline_required` is here because of *unquoting*, and it is why this
      // reads the corpus through `_allKotlin` rather than the raw XML. Arabic
      // writes it `"* الموعد النهائي   "` — Android-quoted to preserve the
      // trailing spaces — so a scan of the raw text sees a leading `"` and
      // misses it. A first cut of this test expected two for exactly that
      // reason and went red on the third. Nothing reads it today (no template
      // value's English is "Deadline"), so the leading strip protects it in
      // advance rather than repairing it.
      final leading = RegExp('^[\\s\u00a0]*[:.…!*]+');
      final found = <String>[];
      for (final code in ['ar', 'es', 'fr', 'ne', 'so']) {
        for (final entry in _allKotlin(code).entries) {
          if (entry.value.trim().isEmpty) continue;
          if (leading.hasMatch(entry.value)) found.add('$code/${entry.key}');
        }
      }
      expect(found..sort(), [
        'ar/deadline_required',
        'ar/sync_to_server',
        'ar/task',
      ]);
    });

    test('a template that keeps its colon keeps the translation\'s', () {
      // `ar/sync_to_server` is ":المزامنة إلى الخادم" and was reported as the
      // same defect. It is not: `app_en.arb`'s `syncToServer` **is**
      // "Sync to server:", so the template carries the colon and Arabic puts
      // it where Arabic puts it. The exact tier copies the translation
      // verbatim and must keep doing so.
      expect(_readArb('en')['syncToServer'], 'Sync to server:');
      expect(_readArb('ar')['syncToServer'], _kotlin('ar', 'sync_to_server'));
    });
  });

  group('refused translations', () {
    // An English fallback beats a confident wrong translation, so two Somali
    // values the tool *could* derive are refused. See [refusedTranslations] for
    // the evidence; this pins that the refusal is live and that it expires.

    test('a refused key falls back to English', () {
      for (final entry in refusedTranslations.entries) {
        final arb = _readArb(entry.key);
        for (final key in entry.value.keys) {
          expect(
            arb.containsKey(key),
            isFalse,
            reason: '${entry.key}/$key is refused and must stay absent',
          );
        }
      }
    });

    test('the recovery path honours the refusal', () {
      // Driven through `recoverProposals`, not the merge path, because the
      // merge path cannot distinguish: both refused keys differ from Kotlin's
      // English by case, a tier only `--adopt` has, so a merge-path assertion
      // would read `null` whether the refusal fired or not. Third fixture this
      // round that had to be chosen rather than assumed.
      final english = _allKotlin('en');
      final translated = _allKotlin('so');
      final matches = matchTemplateToKotlin(_readArb('en'), english);
      for (final key in refusedTranslations['so']!.keys) {
        final match = matches[key];
        // The control: absent the refusal this key *is* adoptable, so a null
        // below means the refusal and not a missing match.
        expect(match, isNotNull, reason: key);
        expect(match!.tier.isAdoptable, isTrue, reason: key);
        expect(
          translated[_snakeCase(key)],
          isNotNull,
          reason: 'values-so has a string to adopt for $key',
        );
        expect(
          recoverProposals(
            locale: 'so',
            key: key,
            templateValue: _readArb('en')[key]! as String,
            match: match,
            english: english,
            translated: translated,
          ),
          isNull,
          reason: 'so/$key is refused',
        );
        // …and another locale, not refused, does propose one.
        expect(
          recoverProposals(
            locale: 'ne',
            key: key,
            templateValue: _readArb('en')[key]! as String,
            match: match,
            english: english,
            translated: _allKotlin('ne'),
          ),
          isNotEmpty,
          reason: 'ne/$key is not refused and is otherwise recoverable',
        );
      }
    });

    test('every refusal still quotes the live Kotlin string', () {
      // The exemption expires in both directions. If upstream retranslates one
      // of these, the quoted text stops matching and this says to delete the
      // entry — an entry that no longer describes anything is a silent debt.
      for (final entry in refusedTranslations.entries) {
        final kotlin = _allKotlin(entry.key);
        for (final row in entry.value.entries) {
          final name = _snakeCase(row.key);
          expect(
            kotlin[name],
            row.value,
            reason:
                'values-${entry.key}/$name has changed. Re-read it: if it is a '
                'real translation now, delete this refusal.',
          );
        }
      }
    });
  });

  group('composites', () {
    // Phase 160. The port writes some multi-line copy as one ARB value where
    // the Android layout has a TextView per line, so the whole string exists
    // nowhere in `values/strings.xml` and no whole-string tier can reach it.
    // The three onboarding paragraphs are the port's longest user-facing prose
    // and were machine-translated in ar/fr and absent in ne/so.

    Map<String, List<String>> byEnglish() {
      final document = XmlDocument.parse(
        File('../app/src/main/res/values/strings.xml').readAsStringSync(),
      );
      final out = <String, List<String>>{};
      for (final element in document.findAllElements('string')) {
        final name = element.getAttribute('name');
        if (name == null) continue;
        final raw = element.innerText;
        final value = raw.length > 1 && raw.startsWith('"') && raw.endsWith('"')
            ? raw.substring(1, raw.length - 1)
            : raw;
        out.putIfAbsent(value.trim(), () => []).add(name);
      }
      return out;
    }

    test('a paragraph decomposes into the Kotlin strings it joins', () {
      final template =
          _readArb('en')['onboardingOfflineDescription']! as String;
      final match = compositeSegments(template, byEnglish());
      expect(match, isNotNull);
      expect(match!.names, ['ob_desc2_1', 'ob_desc2_2']);
    });

    test('blank lines are kept as separators, not dropped', () {
      // The defect this test was written against: a first cut filtered the
      // blank pieces out before rejoining, which silently reflowed a five-line
      // onboarding screen into one block. `onboardingPowerDescription` splits
      // into seven pieces, two of them blank.
      final template = _readArb('en')['onboardingPowerDescription']! as String;
      final match = compositeSegments(template, byEnglish())!;
      expect(match.pieces.length, 7);
      expect(match.candidates.where((row) => row.isEmpty).length, 2);

      final derived = deriveCompositeValue(
        templateEnglish: template,
        segments: match,
        english: _allKotlin('en'),
        translated: _allKotlin('ne'),
      );
      expect(derived, isNotNull);
      expect('\n'.allMatches(derived!).length, 6);
      expect(derived.contains('\n\n'), isTrue);
      expect(derived, _readArb('ne')['onboardingPowerDescription']);
    });

    test('a single-line value is not a composite', () {
      expect(compositeSegments('Download', byEnglish()), isNull);
      // Two pieces are needed, and a lone line with a trailing newline is one.
      expect(compositeSegments('Download\n', byEnglish()), isNull);
    });

    test('a piece with no Kotlin counterpart derives nothing', () {
      final template =
          _readArb('en')['onboardingOfflineDescription']! as String;
      expect(
        compositeSegments(
          '$template\nSomething the Kotlin app never said.',
          byEnglish(),
        ),
        isNull,
      );
    });

    test('a piece left in English derives nothing at all', () {
      // Partial recovery is refused: half a paragraph in Nepali and half in
      // English is worse than the English fallback.
      final template =
          _readArb('en')['onboardingOfflineDescription']! as String;
      final match = compositeSegments(template, byEnglish())!;
      final english = _allKotlin('en');
      final crippled = Map<String, String>.from(_allKotlin('ne'))
        ..['ob_desc2_2'] = english['ob_desc2_2']!;
      expect(
        deriveCompositeValue(
          templateEnglish: template,
          segments: match,
          english: english,
          translated: crippled,
        ),
        isNull,
      );
    });

    test('the merge path actually derives a composite', () {
      // This replaces a guard that counted occurrences of `compositeSegments(`
      // in the tool's source. That count is three whether the branch runs or
      // is stranded behind an early `continue` — so it could not fail on the
      // exact defect its own comment named, which a second audit pass proved
      // by reintroducing that `continue` and watching the suite stay green.
      //
      // This drives `deriveMergeValue` instead, with no `.arb` involved: a
      // dead branch returns null and the assertion goes red.
      final english = _allKotlin('en');
      final translated = _allKotlin('ne');
      final outcome = deriveMergeValue(
        key: 'onboardingOfflineDescription',
        templateValue:
            _readArb('en')['onboardingOfflineDescription']! as String,
        locale: 'ne',
        english: english,
        translated: translated,
        byCamelCase: _byCamelCase(english),
        byEnglishText: _byEnglishText(english),
        byEnglishFormat: const {},
      );
      expect(outcome, isNotNull);
      expect(
        outcome!.value,
        '${translated['ob_desc2_1']}\n${translated['ob_desc2_2']}',
      );
    });

    test('the recovery path assigns the composite tier', () {
      // The other half of the same reachability question, asked of
      // `--candidates`/`--adopt`: does the matcher hand the key a tier the
      // adopt step will act on?
      final matches = matchTemplateToKotlin(_readArb('en'), _allKotlin('en'));
      for (final key in [
        'onboardingOfflineDescription',
        'onboardingOpenDescription',
        'onboardingPowerDescription',
      ]) {
        expect(matches[key]?.tier, MatchTier.composite, reason: key);
        expect(matches[key]!.tier.isAdoptable, isTrue, reason: key);
      }
    });

    test('a piece its candidates disagree about derives nothing', () {
      // The unanimity rule, which nothing pinned: `values/strings.xml` gives
      // several names to one English phrase and they do not always agree in
      // translation. Two Kotlin names share the English "Join requests" and
      // render it differently in Nepali, so a composite built on it must
      // refuse rather than pick one.
      final english = _allKotlin('en');
      final translated = _allKotlin('ne');
      final shared = _byEnglishText(english)['Join Requests'];
      expect(shared, isNotNull);
      expect(shared!.length, greaterThan(1), reason: 'need a disputed piece');
      expect(
        shared.map((name) => translated[name]).toSet().length,
        greaterThan(1),
        reason: 'the candidates must actually disagree in Nepali',
      );

      const template = 'Join Requests\nCancel';
      final match = compositeSegments(template, _byEnglishText(english))!;
      expect(
        deriveCompositeValue(
          templateEnglish: template,
          segments: match,
          english: english,
          translated: translated,
        ),
        isNull,
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

/// The tool's own two indexes over the English corpus, rebuilt for a test.
Map<String, List<String>> _byEnglishText(Map<String, String> english) {
  final out = <String, List<String>>{};
  for (final entry in english.entries) {
    out.putIfAbsent(entry.value.trim(), () => []).add(entry.key);
  }
  return out;
}

Map<String, List<String>> _byCamelCase(Map<String, String> english) {
  final out = <String, List<String>>{};
  for (final name in english.keys) {
    final parts = name.split('_');
    final camel =
        parts.first +
        parts
            .skip(1)
            .map(
              (part) => part.isEmpty
                  ? part
                  : part[0].toUpperCase() + part.substring(1),
            )
            .join();
    out.putIfAbsent(camel, () => []).add(name);
  }
  return out;
}

/// The Kotlin `strings.xml` name an ARB key is refused against.
String _snakeCase(String key) => switch (key) {
  'noSurveys' => 'no_surveys',
  'updateHealth' => 'update_health_record',
  _ => throw ArgumentError('no mapping for $key'),
};

Map<String, Object?> _readArb(String locale) =>
    jsonDecode(File('lib/l10n/app_$locale.arb').readAsStringSync())
        as Map<String, Object?>;

/// Every string out of a `values*` directory, with Android's quoting undone.
Map<String, String> _allKotlin(String locale) {
  final dir = locale == 'en' ? 'values' : 'values-$locale';
  final document = XmlDocument.parse(
    File('../app/src/main/res/$dir/strings.xml').readAsStringSync(),
  );
  return {
    for (final element in document.findAllElements('string'))
      if (element.getAttribute('name') != null)
        element.getAttribute('name')!: _unquote(element.innerText),
  };
}

/// Android's quoting undone, the way `tool/arb_from_strings_xml.dart` does it.
///
/// The surrounding `"…"` **and** the backslash escapes. A first cut stripped
/// only the quotes, which the composite tests could not notice because
/// `ob_desc*` carries no escape in `en` or `ne` — but `values-fr`'s
/// `ob_desc2_1` is `l\'apprentissage`, so extending a test one locale would
/// have failed it for a reason with nothing to do with the rule under test.
String _unquote(String raw) {
  final value = raw.length > 1 && raw.startsWith('"') && raw.endsWith('"')
      ? raw.substring(1, raw.length - 1)
      : raw;
  return value.replaceAllMapped(RegExp(r"""\\(['"])"""), (match) => match[1]!);
}

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
