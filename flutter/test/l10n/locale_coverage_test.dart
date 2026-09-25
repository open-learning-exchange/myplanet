import 'dart:convert';
import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/l10n/framework_fallback_delegates.dart';
import 'package:myplanet/providers/settings_provider.dart';

/// Guards the locale set itself, which nothing checked before.
///
/// The language picker has offered six codes since the language action landed,
/// but only `en` and `es` had an `.arb` — so `AppLocalizations.supportedLocales`
/// held two entries and choosing Arabic, French, Nepali or Somali resolved
/// straight back to English. Four menu items that did nothing, and no test could
/// have noticed: every string still rendered, just in the wrong language.
void main() {
  /// The exact delegate list `app.dart` installs, so these tests exercise what
  /// ships rather than a convenient subset.
  const appDelegates = <LocalizationsDelegate<dynamic>>[
    AppLocalizations.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    ...frameworkFallbackDelegates,
  ];

  Map<String, Object?> readArb(String locale) =>
      jsonDecode(File('lib/l10n/app_$locale.arb').readAsStringSync())
          as Map<String, Object?>;

  List<String> keysOf(Map<String, Object?> arb) =>
      arb.keys.where((key) => !key.startsWith('@')).toList();

  test('every language the picker offers is a supported locale', () {
    final supported = AppLocalizations.supportedLocales
        .map((locale) => locale.languageCode)
        .toSet();

    for (final code in LocaleNotifier.supportedLanguageCodes) {
      expect(
        supported,
        contains(code),
        reason:
            'the picker offers "$code", so lib/l10n/app_$code.arb must exist — '
            'otherwise the entry silently falls back to English',
      );
    }
  });

  test('each locale file declares its own @@locale', () {
    for (final code in LocaleNotifier.supportedLanguageCodes) {
      if (code == 'en') continue; // the template carries no @@locale
      expect(readArb(code)['@@locale'], code);
    }
  });

  test('no locale invents a key the template does not have', () {
    // A stray key is dead weight `gen-l10n` ignores, and usually means a hand
    // edit drifted from the template.
    final template = keysOf(readArb('en')).toSet();
    for (final code in LocaleNotifier.supportedLanguageCodes) {
      if (code == 'en') continue;
      expect(
        keysOf(readArb(code)).where((key) => !template.contains(key)),
        isEmpty,
        reason: '$code has keys absent from app_en.arb',
      );
    }
  });

  test('every translated value is non-empty', () {
    // An empty string renders as nothing at all, which is worse than falling
    // back to English.
    for (final code in LocaleNotifier.supportedLanguageCodes) {
      if (code == 'en') continue;
      final arb = readArb(code);
      for (final key in keysOf(arb)) {
        expect(
          (arb[key] as String).trim(),
          isNotEmpty,
          reason: '$code:$key is blank',
        );
      }
    }
  });

  test('the derived locales carry a real translation, not the English', () {
    // Cheap smoke test that the generator matched rather than copied: `login`
    // exists in all five Kotlin locales and differs from English in each.
    final english = readArb('en')['login'] as String;
    for (final code in ['ar', 'es', 'fr', 'ne', 'so']) {
      expect(readArb(code)['login'], isNot(english), reason: code);
    }
  });

  testWidgets('Arabic resolves and lays out right-to-left', (tester) async {
    late TextDirection direction;
    late AppLocalizations l10n;

    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('ar'),
        localizationsDelegates: appDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) {
            direction = Directionality.of(context);
            l10n = AppLocalizations.of(context);
            return const SizedBox.shrink();
          },
        ),
      ),
    );

    // Directionality comes from the resolved locale, so this failing means
    // Arabic did not resolve at all.
    expect(direction, TextDirection.rtl);
    expect(l10n.localeName, 'ar');
    // And the delegate really is serving the Arabic table, not the fallback.
    expect(l10n.login, readArb('ar')['login']);
  });

  testWidgets('an untranslated key falls back to English, not to blank', (
    tester,
  ) async {
    late AppLocalizations l10n;
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('so'),
        localizationsDelegates: appDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) {
            l10n = AppLocalizations.of(context);
            return const SizedBox.shrink();
          },
        ),
      ),
    );

    // `login` is derived; `appTitle` may not be. Either way both must render
    // something — 455 of the 870 template keys have no Somali value at all
    // today, and that has to read as English rather than as an empty label.
    // Phase 118 raised that number on purpose: 446 of those keys used to hold
    // `[Somali] ` plus the English, which displaced this fallback with the same
    // words under a tag.
    expect(l10n.login, isNotEmpty);
    expect(l10n.appTitle, isNotEmpty);
  });

  testWidgets('Somali gets English framework labels instead of crashing', (
    tester,
  ) async {
    // `flutter_localizations` has no Somali: all three global delegates decline
    // it. Without the fallbacks the locale still resolves, no delegate provides
    // `MaterialLocalizations`, and the first widget to ask for one throws — so
    // shipping app_so.arb would have turned a quietly-English menu entry into a
    // crashing one.
    late MaterialLocalizations material;
    late CupertinoLocalizations cupertino;

    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('so'),
        localizationsDelegates: appDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) {
            material = MaterialLocalizations.of(context);
            cupertino = CupertinoLocalizations.of(context);
            return const SizedBox.shrink();
          },
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(material.okButtonLabel, 'OK');
    expect(cupertino.copyButtonLabel, 'Copy');
    // The app's own strings are still Somali — only the framework's fall back.
    final l10n = AppLocalizations.of(tester.element(find.byType(SizedBox)));
    expect(l10n.login, readArb('so')['login']);
  });

  testWidgets('the fallback does not displace a real framework translation', (
    tester,
  ) async {
    // The ordering constraint, pinned: the fallbacks sit last, so Spanish must
    // still get Spanish Material labels. If someone moves them earlier, every
    // locale silently reverts to English framework strings — a regression no
    // other test would catch.
    late MaterialLocalizations material;

    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('es'),
        localizationsDelegates: appDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) {
            material = MaterialLocalizations.of(context);
            return const SizedBox.shrink();
          },
        ),
      ),
    );

    expect(material.cancelButtonLabel, isNot('Cancel'));
  });

  test('hand-authored translations survive in every derived locale', () {
    // `tool/arb_from_strings_xml.dart` derives translations from the Kotlin
    // `strings.xml`, and these 16 keys are exactly the ones it *cannot* derive
    // for any locale — mostly the resource viewer's per-media-type error
    // states, which have no Kotlin counterpart at all. They were translated by
    // hand, and the tool used to regenerate each file from scratch, deleting
    // every one of them on every run with no error and nothing a reader would
    // recognise as data loss.
    //
    // It merges now, but it remains the documented way to refresh these files,
    // so the artifact is what gets pinned: whatever future change makes
    // regeneration destructive again, this fails instead of four locales
    // quietly reverting to English.
    const handAuthored = [
      'failedToSaveCsvFile',
      'exportCancelled',
      'videoFileNotFound',
      'unableToLoadVideo',
      'audioFileNotFound',
      'unableToLoadAudio',
      'pdfFileNotFound',
      'unableToLoadPdf',
      'imageFileNotFound',
      'noContent',
      'emptyFile',
      'htmlEntryNotFound',
      'errorOccurred',
      'openingResource',
      'selected',
      'currentCv',
    ];

    for (final code in ['ar', 'fr', 'ne', 'so']) {
      final arb = readArb(code);
      for (final key in handAuthored) {
        expect(
          arb[key],
          isA<String>().having((value) => value.trim(), 'value', isNotEmpty),
          reason:
              '$code lost the hand-authored "$key" — most likely a '
              'regeneration that overwrote instead of merging',
        );
      }
      // `currentCv` takes a placeholder, so its `@currentCv` declaration has to
      // survive too. An earlier cut of the merge fix dropped it by filtering
      // non-String values, which un-declares the placeholder without touching
      // the translation — the kind of loss the check above cannot see.
      expect(
        arb['@currentCv'],
        isA<Map<String, Object?>>(),
        reason: '$code lost the @currentCv placeholder declaration',
      );
    }
  });

  test('a deliberate trailing space survives into every locale', () {
    // Four template strings end in a space, and every one of them is a label
    // that a value is drawn straight after: `Storage running low: `,
    // `Storage available: `, `Selected: `, `Select resources: `. Android's
    // quoting exists precisely so a trailing space survives in `strings.xml`
    // (`<string name="storage_running_low">"Storage running low: "</string>`),
    // and all five Kotlin locales carry it.
    //
    // `tool/arb_from_strings_xml.dart` derived them through
    // `translated[name]?.trim()`, so it stripped exactly the character the
    // quoting was there to protect. `selected` and `selectResources` were
    // repaired by hand through `--adopt`, whose `_proposal` does mirror the
    // space; the two storage keys came through the plain-text path and lost it
    // in all five locales.
    //
    // A missing space is not always visible — `formatStorageNotification`
    // interpolates `'$runningLow $percent%'`, so its own space masks the loss,
    // and `renderNotificationHtml` collapses the double space the Kotlin ends
    // up with. The next consumer that concatenates without a separator gets
    // `Almacenamiento bajo:10%`, in a string a translator cannot see is wrong.
    // So the guard is on the derivation's output, not on one caller.
    final template = readArb('en');
    final labelKeys = keysOf(template).where((key) {
      final value = template[key];
      return value is String && value.endsWith(' ');
    }).toList();
    expect(
      labelKeys,
      isNotEmpty,
      reason:
          'the template has stopped carrying trailing-space labels, so '
          'this guard now proves nothing — delete it or repoint it',
    );

    final stripped = <String>[];
    for (final code in {...LocaleNotifier.supportedLanguageCodes, 'es'}) {
      if (code == 'en') continue;
      final arb = readArb(code);
      for (final key in labelKeys) {
        final value = arb[key];
        // Absent is fine: the key falls back to the English, space and all.
        if (value is! String) continue;
        if (!value.endsWith(' ')) stripped.add('$code/$key = "$value"');
      }
    }

    expect(
      stripped,
      isEmpty,
      reason:
          'these locale values lost the template\'s trailing space, so the '
          'label runs into the value it prefixes:\n'
          '${stripped.map((entry) => '  $entry').join('\n')}',
    );
  });

  test('no locale file declares the same key twice', () {
    // `gen-l10n` parses ARB as JSON, and a duplicate key is not an error there:
    // the last value silently wins and one getter is emitted. That is how
    // `untitledResource` came to render "Untitled Resource" everywhere after a
    // second, title-case entry was appended 400 lines below the sentence-case
    // original — every list screen that wanted the first value got the second.
    // No existing test could see it, because they all read through `readArb`
    // and `jsonDecode` collapses the pair before they look. So this one counts
    // the keys in the source text instead.
    for (final code in [...LocaleNotifier.supportedLanguageCodes, 'es']) {
      final duplicates = _duplicateTopLevelKeys(
        File('lib/l10n/app_$code.arb').readAsStringSync(),
      );

      expect(
        duplicates,
        isEmpty,
        reason:
            'app_$code.arb declares ${duplicates.join(", ")} more than once; '
            'gen-l10n keeps only the last value of each',
      );
    }
  });
}

/// Top-level ARB keys that appear more than once in [source].
///
/// Only depth-1 keys count: an `@key` metadata block nests its own
/// `description`/`placeholders`/`type`, and placeholder names like `count`
/// legitimately repeat across entries, so a naive scan reports all of those as
/// duplicates. Tracking brace depth outside string literals is enough to tell
/// the two apart.
Set<String> _duplicateTopLevelKeys(String source) {
  final seen = <String>{};
  final duplicates = <String>{};

  var depth = 0;
  var inString = false;
  var escaped = false;
  final current = StringBuffer();
  String? lastString;

  for (final char in source.split('')) {
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (char == r'\') {
        escaped = true;
      } else if (char == '"') {
        inString = false;
        lastString = current.toString();
      } else {
        current.write(char);
      }
      continue;
    }

    switch (char) {
      case '"':
        inString = true;
        current.clear();
      case '{':
      case '[':
        depth++;
      case '}':
      case ']':
        depth--;
      case ':':
        // A `key:` at depth 1 is an entry of the outermost object.
        if (depth == 1 && lastString != null) {
          if (!seen.add(lastString)) duplicates.add(lastString);
          lastString = null;
        }
    }
  }

  return duplicates;
}
