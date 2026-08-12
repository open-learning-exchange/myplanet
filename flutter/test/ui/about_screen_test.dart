import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/providers/settings_provider.dart';
import 'package:myplanet/ui/dashboard/about_screen.dart';

import '../support/widget_harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('renders the about heading and the OLE logo', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          // package_info_plus returns an empty version under the test binding,
          // which is fine — the screen falls back to the bare about text.
          appVersionProvider.overrideWith((ref) async => '0.0.0'),
        ],
        child: wrapScreen(const AboutScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('About'), findsOneWidget);
    // The OLE logo image renders.
    expect(find.byType(Image), findsOneWidget);
    // The HTML body renders — flutter_widget_from_html emits RichText, so
    // verify the rendered tree carries the heading text rather than a Text.
    expect(
      find.byWidgetPredicate(
        (w) => w is RichText && w.text.toPlainText().contains('myPlanet'),
      ),
      findsWidgets,
    );
  });
}
