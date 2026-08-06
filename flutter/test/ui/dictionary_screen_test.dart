import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/dictionary_provider.dart';
import 'package:myplanet/ui/dictionary/dictionary_screen.dart';

import '../support/widget_harness.dart';

void main() {
  testWidgets('searches cached entries and renders definition details', (
    tester,
  ) async {
    final notifier = _TestDictionaryNotifier(
      DictionaryRow(
        id: 'planet',
        code: '',
        language: 'en',
        advanceCode: '',
        word: 'Planet',
        wordNormalized: 'planet',
        meaning: 'A celestial body',
        definition: 'A world orbiting a star.',
        synonym: 'world',
        antonym: '',
      ),
    );
    await tester.pumpWidget(
      wrapScreen(
        const DictionaryScreen(),
        overrides: [dictionaryProvider.overrideWith(() => notifier)],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('1 downloaded word'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'pLaNeT');
    await tester.tap(find.widgetWithText(FilledButton, 'search'));
    await tester.pump();

    expect(notifier.lastQuery, 'pLaNeT');
    expect(find.text('Planet'), findsOneWidget);
    expect(find.text('A world orbiting a star.'), findsOneWidget);
    // The synonym renders as a `Text.rich` label/value pair, which `find.text`
    // only reaches with `findRichText`. Matching the whole line also keeps this
    // from colliding with the 'world' inside the definition above.
    expect(find.text('Synonyms: world', findRichText: true), findsOneWidget);
    expect(
      find.text('Meaning: A celestial body', findRichText: true),
      findsOneWidget,
    );
  });

  testWidgets('offers a download when the offline dictionary is empty', (
    tester,
  ) async {
    final notifier = _TestDictionaryNotifier(null, count: 0);
    await tester.pumpWidget(
      wrapScreen(
        const DictionaryScreen(),
        overrides: [dictionaryProvider.overrideWith(() => notifier)],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No downloaded words'), findsOneWidget);
    await tester.tap(find.text('Download dictionary'));
    await tester.pump();
    expect(notifier.downloadCalls, 1);
  });
}

class _TestDictionaryNotifier extends DictionaryNotifier {
  _TestDictionaryNotifier(this.entry, {this.count = 1});

  final DictionaryRow? entry;
  final int count;
  String? lastQuery;
  int downloadCalls = 0;

  @override
  Future<DictionaryState> build() async => DictionaryState(count: count);

  @override
  Future<bool> search(String query) async {
    lastQuery = query;
    state = AsyncData(state.requireValue.copyWith(result: entry));
    return entry != null;
  }

  @override
  Future<void> download() async {
    downloadCalls++;
  }
}
