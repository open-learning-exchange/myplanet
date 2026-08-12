import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_widget_from_html/flutter_widget_from_html.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/settings_provider.dart';

/// Port of `ui/dashboard/AboutFragment.kt`.
///
/// Renders the `about` HTML string with the app version spliced in after the
/// `<h3>MyPlanet</h3>` heading — the same string replacement the Kotlin does
/// in `onCreateView`. The HTML is rendered by `flutter_widget_from_html`,
/// which stands in for `HtmlCompat.fromHtml`.
class AboutScreen extends ConsumerWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final versionAsync = ref.watch(appVersionProvider);

    final body = versionAsync.when(
      data: (version) => l10n.about.replaceFirst(
        '<h3>MyPlanet</h3>',
        '<h3>MyPlanet</h3><h4>${l10n.version(version)}</h4>',
      ),
      loading: () => l10n.about,
      error: (_, _) => l10n.about,
    );

    return Scaffold(
      appBar: AppBar(title: Text(l10n.actionAbout)),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Image.asset('assets/images/ole_logo.png', width: 200, height: 200),
            const SizedBox(height: 8),
            HtmlWidget(body),
          ],
        ),
      ),
    );
  }
}
