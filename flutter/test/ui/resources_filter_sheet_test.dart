import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/ui/resources/resources_filter_sheet.dart';

/// Covers [mediaTypeDisplayName], the port of
/// `ResourcesFilterFragment.getMediumDisplayName` from upstream `64140ca`.
///
/// The filter lists whatever `mediaType` strings the synced rows carry, so the
/// values under test are raw server values, not a curated enum.
void main() {
  /// Resolves [mediaTypeDisplayName] against a real [AppLocalizations] for
  /// [locale], which is the only way to read it — it takes a `BuildContext`.
  Future<String> label(
    WidgetTester tester,
    String medium, {
    Locale locale = const Locale('en'),
  }) async {
    late String resolved;
    await tester.pumpWidget(
      MaterialApp(
        locale: locale,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) {
            resolved = mediaTypeDisplayName(context, medium);
            return const SizedBox.shrink();
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    return resolved;
  }

  testWidgets('every medium the Kotlin recognises gets its label', (
    tester,
  ) async {
    expect(await label(tester, 'pdf'), 'PDFs');
    expect(await label(tester, 'video'), 'Videos');
    expect(await label(tester, 'audio'), 'Audio');
    expect(await label(tester, 'image'), 'Images');
    expect(await label(tester, 'text/html'), 'Text / HTML');
    expect(await label(tester, 'html'), 'HTML');
    // From `filterOther`, not `storageOther` — see the doc comment at
    // `mediaTypeDisplayName`. If this ever reds because `storageOther` was
    // repaired to "Other Files", the chip is wired to the wrong key again.
    expect(await label(tester, 'other'), 'Other');
  });

  testWidgets('an unrecognised medium is shown verbatim', (tester) async {
    // `else -> medium`: the filter must keep listing a value it has no name
    // for, because the list is built from whatever the server sent.
    expect(await label(tester, 'application/epub+zip'), 'application/epub+zip');
    expect(await label(tester, ''), '');
  });

  testWidgets('the match is case-insensitive, as the Kotlin `when` is', (
    tester,
  ) async {
    expect(await label(tester, 'PDF'), 'PDFs');
    expect(await label(tester, 'Text/HTML'), 'Text / HTML');
  });

  testWidgets('the label is localised, not hardcoded English', (tester) async {
    // Recovered from the Kotlin `values-fr/strings.xml`, which has shipped
    // these in the Android app for years.
    expect(await label(tester, 'video', locale: const Locale('fr')), 'Vidéos');
    expect(
      await label(tester, 'text/html', locale: const Locale('fr')),
      'Texte / HTML',
    );
  });
}
