import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';

/// Port of `ui/dashboard/AboutFragment.kt` -- renders the static `about`
/// body as markdown, with the app version injected after the `### MyPlanet`
/// heading, matching the Kotlin's `<h3>MyPlanet</h3>` ->
/// `<h3>MyPlanet</h3>\n<h4>...</h4>` replacement.
class AboutScreen extends ConsumerWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    // The Kotlin reads `BuildConfig.VERSION_NAME`; the port reads the same
    // value at runtime through `package_info_plus` so the line tracks pubspec
    // rather than a hardcoded constant.
    final versionInfo = ref.watch(appVersionInfoProvider).valueOrNull;
    final versionLine = l10n.appVersion(versionInfo?.version ?? '…');
    final body = '${l10n.aboutContent}\n\n#### $versionLine';
    return Scaffold(
      appBar: AppBar(title: Text(l10n.about)),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: MarkdownBody(
          data: body,
          styleSheet: MarkdownStyleSheet(
            h3: Theme.of(context).textTheme.headlineSmall,
            h4: Theme.of(context).textTheme.titleMedium,
            p: Theme.of(context).textTheme.bodyMedium,
          ),
        ),
      ),
    );
  }
}

/// Port of `ui/dashboard/DisclaimerFragment.kt` -- renders the static
/// `disclaimer` body as markdown. The Kotlin sets `LinkMovementMethod` so
/// the `<a href>` links are tappable; `onTapLink` opens them in the
/// platform browser, the markdown equivalent.
class DisclaimerScreen extends StatelessWidget {
  const DisclaimerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.disclaimer)),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: MarkdownBody(
          data: l10n.disclaimerContent,
          onTapLink: (text, href, title) {
            if (href != null) {
              launchUrl(Uri.parse(href));
            }
          },
          styleSheet: MarkdownStyleSheet(
            h1: Theme.of(context).textTheme.headlineSmall,
            h2: Theme.of(context).textTheme.titleLarge,
            p: Theme.of(context).textTheme.bodyMedium,
          ),
        ),
      ),
    );
  }
}
