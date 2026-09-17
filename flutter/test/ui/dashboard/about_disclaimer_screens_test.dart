import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/ui/dashboard/about_disclaimer_screens.dart';

import '../../support/widget_harness.dart';

void main() {
  testWidgets('About screen renders the heading and the injected version', (
    tester,
  ) async {
    const version = (version: '0.62.98', buildNumber: '6298');
    await tester.pumpWidget(
      wrapScreen(
        const AboutScreen(),
        overrides: [
          appVersionInfoProvider.overrideWith((ref) async => version),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('About'), findsOneWidget);
    // The about body's first heading.
    expect(find.text('MyPlanet'), findsWidgets);
    // AboutFragment injects the version as an <h4> after the heading. The
    // version now comes from package_info_plus rather than a hardcoded
    // constant, so the test injects one through the provider.
    expect(find.text('Version 0.62.98'), findsOneWidget);
  });

  testWidgets('Disclaimer screen renders the title and a body paragraph', (
    tester,
  ) async {
    await tester.pumpWidget(wrapScreen(const DisclaimerScreen()));

    // "Disclaimer" appears as the app-bar title and as body headings.
    expect(find.text('Disclaimer'), findsWidgets);
    // A paragraph from the markdown body, rendered as plain text.
    expect(find.text('Last updated: January 10, 2020'), findsOneWidget);
  });

  testWidgets('Disclaimer renders in Spanish when the locale is es', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(const DisclaimerScreen(), locale: const Locale('es')),
    );

    // The localized `disclaimer` key is the app-bar title and a body heading.
    expect(find.text('Descargo de responsabilidad'), findsWidgets);
    // The body's first heading is the Spanish "Renuncia de responsabilidad".
    expect(find.text('Renuncia de responsabilidad'), findsWidgets);
  });
}
