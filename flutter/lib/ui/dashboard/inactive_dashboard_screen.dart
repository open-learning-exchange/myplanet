import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../router.dart';

/// Port of `ui/dashboard/InactiveDashboardFragment.kt`.
///
/// Shown when a logged-in user has no roles and is not an admin — the account
/// has not been activated. The screen shows an explanatory message and a
/// "Submit Feedback" button, which opens the feedback create screen.
class InactiveDashboardScreen extends StatelessWidget {
  const InactiveDashboardScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.appTitle)),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                l10n.inactiveMessage,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ),
            const SizedBox(height: 16),
            FilledButton.tonal(
              onPressed: () => context.push(Routes.feedbackCreate),
              child: Text(l10n.submitFeedback),
            ),
          ],
        ),
      ),
    );
  }
}
