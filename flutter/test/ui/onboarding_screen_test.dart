import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/ui/onboarding/onboarding_screen.dart';

import '../support/widget_harness.dart';

class _TestOnboardingNotifier extends OnboardingNotifier {
  @override
  bool build() => false;

  @override
  Future<void> complete() async {
    state = true;
  }
}

void main() {
  List<Override> overrides() => [
    onboardingProvider.overrideWith(_TestOnboardingNotifier.new),
  ];

  testWidgets('walks through all four onboarding pages', (tester) async {
    await tester.pumpWidget(
      wrapScreen(const OnboardingScreen(), overrides: overrides()),
    );

    expect(find.text('Welcome to myPlanet'), findsOneWidget);
    expect(find.text('Skip'), findsOneWidget);

    for (var page = 0; page < 3; page++) {
      await tester.tap(find.widgetWithText(FilledButton, 'Next'));
      await tester.pumpAndSettle();
    }

    expect(find.text('Unleash Learning Power'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'GET STARTED'), findsOneWidget);
    expect(find.textContaining('Planet server address'), findsOneWidget);
  });

  // The notifier is stubbed here, so this covers the callback and in-memory
  // state only; PlanetPrefs persistence is covered in planet_prefs_test.dart.
  testWidgets('skip completes onboarding and invokes the callback', (
    tester,
  ) async {
    var finished = false;
    late WidgetRef ref;
    await tester.pumpWidget(
      wrapScreen(
        Consumer(
          builder: (context, widgetRef, _) {
            ref = widgetRef;
            return OnboardingScreen(onFinished: () => finished = true);
          },
        ),
        overrides: overrides(),
      ),
    );

    await tester.tap(find.text('Skip'));
    await tester.pumpAndSettle();

    expect(finished, isTrue);
    expect(ref.read(onboardingProvider), isTrue);
  });

  testWidgets('uses existing Spanish onboarding translations', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const OnboardingScreen(),
        overrides: overrides(),
        locale: const Locale('es'),
      ),
    );

    expect(find.text('Bienvenido a myPlanet'), findsOneWidget);
    expect(find.text('Omitir'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Siguiente'), findsOneWidget);
  });
}
