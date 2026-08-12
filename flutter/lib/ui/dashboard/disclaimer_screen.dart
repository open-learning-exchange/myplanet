import 'package:flutter/material.dart';
import 'package:flutter_widget_from_html/flutter_widget_from_html.dart';

import '../../l10n/app_localizations.dart';

/// Port of `ui/dashboard/DisclaimerFragment.kt`.
///
/// Renders the `disclaimer` HTML with clickable links — the Kotlin sets
/// `LinkMovementMethod` so the `<a>` tags work; `flutter_widget_from_html`
/// makes anchors tappable by default, so no extra wiring is needed.
class DisclaimerScreen extends StatelessWidget {
  const DisclaimerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.actionDisclaimer)),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: HtmlWidget(l10n.disclaimer),
      ),
    );
  }
}
