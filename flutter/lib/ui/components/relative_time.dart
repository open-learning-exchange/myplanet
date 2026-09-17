import 'package:intl/intl.dart';

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
///
/// **Not** the notification row's formatter; see
/// [notificationTimestampLabel] below for why there are two.
String relativeTimeLabel(AppLocalizations l10n, int elapsedMillis) {
  if (elapsedMillis <= 0 ||
      elapsedMillis < const Duration(minutes: 1).inMilliseconds) {
    return l10n.justNow;
  }
  final elapsed = Duration(milliseconds: elapsedMillis);
  if (elapsed.inHours < 1) return l10n.relativeMinutesAgo(elapsed.inMinutes);
  if (elapsed.inDays < 1) return l10n.relativeHoursAgo(elapsed.inHours);
  return l10n.relativeDaysAgo(elapsed.inDays);
}

const _minuteMillis = 60 * 1000;
const _hourMillis = 60 * _minuteMillis;
const _dayMillis = 24 * _hourMillis;

/// `MMM d, yyyy` per locale, as `NotificationsAdapter.getDateFormatter()`
/// builds it (`NotificationsAdapter.kt:43-53`): `DateTimeFormatter.ofPattern`
/// with `Locale.getDefault()`, which `LocaleUtils` sets from the in-app
/// language picker, and cached until that locale changes. The cache here is the
/// same, keyed by locale rather than invalidated on change.
///
/// `intl` knows five of the port's six languages. `so` is in neither
/// `flutter_localizations`' generated date table nor `intl`'s own
/// `date_symbol_data_local`, and `DateFormat('MMM d, yyyy', 'so')` throws
/// `ArgumentError` — out of `build`, which would take the whole bell screen
/// down (the Phase 95 shape). Hence the fallback to the unlocalised formatter,
/// which is what the port drew for every locale before: a Somali reader sees
/// English month names, where the Kotlin gives them `Abr`/`Lul`. The catch is
/// deliberately not a `so` allowlist — it is `intl`'s locale table that decides,
/// not ours.
///
/// One divergence that survives: Kotlin renders ASCII digits here whatever the
/// locale, because `DateTimeFormatterBuilder.toFormatter(Locale)` uses
/// `DecimalStyle.STANDARD` rather than the locale's. `intl` uses the locale's,
/// so Nepali reads `अगस्ट ३१, २०२६` against Kotlin's `अगस्ट 31, 2026`. Nepali
/// digits in a Nepali sentence are not a defect, and chasing byte parity here
/// would mean reproducing a Java quirk that the Kotlin's own relative arms
/// already contradict (those go through `String.format`, which *does* localise
/// `%d`, so the Android app mixes numbering systems inside one list).
DateFormat _notificationDateFormat(String localeName) =>
    _dateFormatCache.putIfAbsent(localeName, () {
      try {
        return DateFormat('MMM d, yyyy', localeName);
      } catch (_) {
        return DateFormat('MMM d, yyyy');
      }
    });

final _dateFormatCache = <String, DateFormat>{};

/// Localized timestamp for one notification row.
///
/// Port of `NotificationsAdapter.ItemViewHolder.formatRelativeTime`
/// (`NotificationsAdapter.kt:152-163`), which is a *different* function from the
/// one [relativeTimeLabel] ports, in three ways that all matter:
///
///   * it has a **Yesterday** bucket, covering the whole second day, which
///     `DateUtils.getRelativeTimeSpanString` does not;
///   * beyond a week it stops being relative at all and shows an absolute date;
///   * and it reads `strings.xml` — `minutes_ago` is `%1$d min ago` — where
///     `getRelativeTimeSpanString` reads the Android framework's own strings,
///     which say "5 minutes ago". So the two render different sentences *in the
///     Kotlin app*, and folding this into [relativeTimeLabel] would have had to
///     change the wording on one screen or the other.
///
/// They live in the same file so that difference stays visible: two relative-
/// time formatters in two files is how the port ended up with two disagreeing
/// `normalizeText`s (Phase 78).
///
/// Both instants are parameters rather than read from the clock, matching how
/// [relativeTimeLabel]'s callers already subtract at the call site — and the
/// absolute arm needs `createdAtMillis` itself, not just the elapsed time.
///
/// A `createdAtMillis` in the future makes `diff` negative, which satisfies the
/// first arm and reads as "Just now", exactly as the Kotlin's `diff < 60_000L`
/// does. Every `~/` below therefore sees a non-negative dividend.
String notificationTimestampLabel(
  AppLocalizations l10n, {
  required int createdAtMillis,
  required int now,
}) {
  final diff = now - createdAtMillis;
  if (diff < _minuteMillis) return l10n.justNow;
  // Integer division, as Kotlin's `diff / 60_000L` is. Each bucket's own lower
  // bound keeps the quotient at 1 or more, so no arm can render a zero.
  if (diff < _hourMillis) return l10n.minutesAgo(diff ~/ _minuteMillis);
  if (diff < _dayMillis) return l10n.hoursAgo(diff ~/ _hourMillis);
  if (diff < 2 * _dayMillis) return l10n.yesterday;
  // 2..6, so the ungrammatical "1 days ago" that `days_ago` would produce is
  // unreachable — the Yesterday arm above owns the whole of day one.
  if (diff < 7 * _dayMillis) return l10n.daysAgo(diff ~/ _dayMillis);
  return _notificationDateFormat(
    l10n.localeName,
  ).format(DateTime.fromMillisecondsSinceEpoch(createdAtMillis));
}
