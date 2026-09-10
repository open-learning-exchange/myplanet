import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/resources_providers.dart';
import 'package:myplanet/ui/resources/resources_filter_sheet.dart';

import '../support/widget_harness.dart';

/// Covers [mediaTypeDisplayName] and the wiring that uses it — the port of
/// `ResourcesFilterFragment.getMediumDisplayName` from upstream `64140ca`.
///
/// The label tests alone were not enough: with only those, deleting the
/// `labelFor:` argument, adding it to the three lists Kotlin leaves raw, or
/// storing the *label* instead of the raw medium all left the suite green. The
/// last of those is the writer/reader key disagreement this project keeps
/// finding, and it is exactly what this feature risks, so it is pinned here.
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

  MyLibraryRow row(String id, {String? mediaType, String? language}) =>
      buildLibraryRow(
        id: id,
        title: id,
        subject: const ['Science'],
      ).copyWith(mediaType: Value(mediaType), language: Value(language));

  /// Pumps the real sheet over a stream of [rows]. Without the
  /// `resourcesStreamProvider` override the sheet reads through
  /// `resourcesRepositoryProvider` and reaches `AppDatabase.open()`.
  Future<ProviderContainer> pumpSheet(
    WidgetTester tester,
    List<MyLibraryRow> rows,
  ) async {
    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        resourcesStreamProvider.overrideWith((ref) => Stream.value(rows)),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: ResourcesFilterSheet()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return container;
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

  testWidgets('the sheet renders media-type chips through the mapping', (
    tester,
  ) async {
    await pumpSheet(tester, [
      row('a', mediaType: 'pdf'),
      row('b', mediaType: 'text/html'),
    ]);

    // Reds if `labelFor:` is dropped from the media-type section.
    expect(find.widgetWithText(FilterChip, 'PDFs'), findsOneWidget);
    expect(find.widgetWithText(FilterChip, 'Text / HTML'), findsOneWidget);
    expect(find.widgetWithText(FilterChip, 'pdf'), findsNothing);
  });

  testWidgets('media-type chips are ordered by the label, not the raw value', (
    tester,
  ) async {
    // `Video` sorts before `pdf` on the raw value, but the user sees `PDFs`
    // and `Videos`. Sorting on a string nobody can see is worse than not
    // sorting, which is what the Kotlin does.
    await pumpSheet(tester, [
      row('a', mediaType: 'Video'),
      row('b', mediaType: 'pdf'),
    ]);

    final chips = tester
        .widgetList<FilterChip>(find.byType(FilterChip))
        .map((c) => (c.label as Text).data)
        .toList();
    expect(chips.indexOf('PDFs'), lessThan(chips.indexOf('Videos')));
  });

  testWidgets('selecting a chip stores the raw medium, not its label', (
    tester,
  ) async {
    final container = await pumpSheet(tester, [row('a', mediaType: 'pdf')]);

    await tester.tap(find.widgetWithText(FilterChip, 'PDFs'));
    await tester.pumpAndSettle();
    // The sheet holds the selection locally until Apply commits it.
    await tester.tap(find.widgetWithText(FilledButton, 'Apply'));
    await tester.pumpAndSettle();

    // The whole point of mapping only the label. `applyFilter` compares the
    // stored value against `MyLibraryRow.mediaType`, so storing 'PDFs' here
    // would make the filter match nothing at all — silently.
    final selected = container.read(resourceFilterProvider).mediaTypes;
    expect(selected, {'pdf'});
    expect(
      [
        row('a', mediaType: 'pdf'),
      ].applyFilter(const ResourceFilter(mediaTypes: {'pdf'})),
      hasLength(1),
    );
  });

  testWidgets('only the medium list is mapped; the others stay raw', (
    tester,
  ) async {
    // Kotlin's `setAdapter` takes `label: (String) -> String = { it }` and only
    // `listMedium` passes a mapper, so a language or subject literally named
    // "pdf" must still render as "pdf" in both apps.
    await pumpSheet(tester, [row('a', mediaType: 'video', language: 'pdf')]);

    expect(find.widgetWithText(FilterChip, 'Videos'), findsOneWidget);
    expect(find.widgetWithText(FilterChip, 'pdf'), findsOneWidget);
    expect(find.widgetWithText(FilterChip, 'PDFs'), findsNothing);
    expect(find.widgetWithText(FilterChip, 'Science'), findsOneWidget);
  });
}
