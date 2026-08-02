import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/resources_providers.dart';
import 'package:myplanet/ui/resources/resources_screen.dart';

MyLibraryRow buildRow({
  required String id,
  String? title,
  String? author,
  bool offline = false,
  List<String> subject = const [],
}) {
  return MyLibraryRow(
    id: id,
    userId: const [],
    title: title,
    titleNormal: title?.toLowerCase(),
    resourceOffline: offline,
    createdDate: 0,
    timesRated: 0,
    resourceFor: const [],
    subject: subject,
    level: const [],
    tag: const [],
    languages: const [],
    isPrivate: false,
    author: author,
  );
}

Widget wrap(Widget child, {List<Override> overrides = const []}) {
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: child,
    ),
  );
}

void main() {
  testWidgets('renders the resources returned by the stream', (tester) async {
    await tester.pumpWidget(
      wrap(
        const ResourcesScreen(),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value([
              buildRow(id: 'r1', title: 'Álgebra Básica', author: 'Ada'),
              buildRow(id: 'r2', title: 'Biology', offline: true),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Álgebra Básica'), findsOneWidget);
    expect(find.text('Biology'), findsOneWidget);
    // The offline resource is badged; the other is not.
    expect(find.byIcon(Icons.offline_pin_outlined), findsOneWidget);
  });

  testWidgets('shows the empty state when nothing is synced', (tester) async {
    await tester.pumpWidget(
      wrap(
        const ResourcesScreen(),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value(const <MyLibraryRow>[]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No data available'), findsOneWidget);
  });

  testWidgets('typing in the search box updates the query provider', (
    tester,
  ) async {
    late WidgetRef capturedRef;

    await tester.pumpWidget(
      wrap(
        Consumer(
          builder: (context, ref, _) {
            capturedRef = ref;
            return const ResourcesScreen();
          },
        ),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value(const <MyLibraryRow>[]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(SearchBar), 'algebra');
    await tester.pump();

    expect(capturedRef.read(resourceSearchQueryProvider), 'algebra');
  });

  testWidgets('renders Spanish strings under the es locale', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value(const <MyLibraryRow>[]),
          ),
        ],
        child: const MaterialApp(
          locale: Locale('es'),
          localizationsDelegates: [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
          home: ResourcesScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Recursos'), findsOneWidget);
    expect(find.text('No hay datos disponibles.'), findsOneWidget);
  });
}
