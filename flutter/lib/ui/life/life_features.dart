import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../router.dart';

/// The myLife feature registry, shared by [LifeScreen]'s list and the home
/// dashboard's myLife card. Port of `BaseDashboardFragment.handleClickMyLife`
/// and `DashboardPluginFragment.imageResourceMap`.

/// Features a guest cannot open — `handleClickMyLife` routes these through
/// `openIfLoggedIn`. References and Calendar stay open to guests.
const Set<String> guestGatedLifeFeatures = {
  'health',
  'achievements',
  'submissions',
  'surveys',
  'personals',
};

void openLifeFeature(BuildContext context, String feature) {
  switch (feature) {
    case 'calendar':
      context.go(Routes.calendar);
      return;
    case 'references':
      context.push(Routes.references);
      return;
    case 'personals':
      context.push(Routes.personals);
      return;
    case 'submissions':
      context.push(Routes.submissions);
      return;
    case 'surveys':
      context.push(Routes.surveys);
      return;
    case 'health':
      context.push(Routes.health);
      return;
    case 'achievements':
      context.push(Routes.achievements);
      return;
    default:
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context).featureComingSoon)),
      );
      return;
  }
}

IconData lifeFeatureIcon(String feature) => switch (feature) {
  'health' => Icons.favorite_outline,
  'achievements' => Icons.emoji_events_outlined,
  'submissions' => Icons.assignment_outlined,
  'surveys' => Icons.poll_outlined,
  'references' => Icons.library_books_outlined,
  'calendar' => Icons.calendar_month_outlined,
  'personals' => Icons.lock_person_outlined,
  _ => Icons.apps,
};

String lifeFeatureTitle(
  AppLocalizations l10n,
  String feature,
  String? fallback,
) => switch (feature) {
  'health' => l10n.myHealth,
  'achievements' => l10n.achievements,
  'submissions' => l10n.submissions,
  'surveys' => l10n.mySurveys,
  'references' => l10n.references,
  'calendar' => l10n.calendar,
  'personals' => l10n.myPersonals,
  _ => fallback ?? feature,
};
