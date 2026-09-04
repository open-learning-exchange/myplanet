import '../../l10n/app_localizations.dart';

/// Localized "n minutes ago" for an elapsed duration in milliseconds.
///
/// Port of `utils/TimeUtils.getRelativeTime`, which delegates to Android's
/// `DateUtils.getRelativeTimeSpanString` and returns "Just now" for a future
/// timestamp. There is no framework equivalent in Flutter, so the buckets are
/// explicit: under a minute reads as just now, then minutes, then hours, then
/// days. A future timestamp lands in the first bucket, as the Kotlin's does.
///
/// Shared by the dashboard's last-sync strip and the profile's last-login row —
/// the two screens that show a relative time — so they cannot drift apart.
String relativeTimeLabel(AppLocalizations l10n, int elapsedMillis) {
  if (elapsedMillis <= 0 ||
      elapsedMillis < const Duration(minutes: 1).inMilliseconds) {
    return l10n.justNow;
  }
  final elapsed = Duration(milliseconds: elapsedMillis);
  if (elapsed.inHours < 1) return l10n.minutesAgo(elapsed.inMinutes);
  if (elapsed.inDays < 1) return l10n.hoursAgo(elapsed.inHours);
  return l10n.daysAgo(elapsed.inDays);
}
