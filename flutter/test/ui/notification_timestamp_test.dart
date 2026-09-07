import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:intl/intl.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/l10n/app_localizations_ar.dart';
import 'package:myplanet/l10n/app_localizations_en.dart';
import 'package:myplanet/l10n/app_localizations_fr.dart';
import 'package:myplanet/l10n/app_localizations_ne.dart';
import 'package:myplanet/l10n/app_localizations_so.dart';
import 'package:myplanet/ui/components/relative_time.dart';

/// `NotificationsAdapter.ItemViewHolder.formatRelativeTime`
/// (`NotificationsAdapter.kt:152-163`), bucket by bucket and boundary by
/// boundary.
///
/// The Kotlin's own tests are at `NotificationsAdapterTest.kt:52-84`; the cases
/// there are mirrored here so a divergence in either app shows up as two suites
/// disagreeing.
void main() {
  // The absolute arm formats through `intl`, whose locale table is registered by
  // `flutter_localizations` in the running app and by nothing at all in a bare
  // unit test — where every locale would silently fall back to `en_US` and the
  // month-name assertions below would pass for the wrong reason. Loading the
  // same CLDR data here is what makes them mean something.
  setUpAll(initializeDateFormatting);

  final AppLocalizations l10n = AppLocalizationsEn();

  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;

  // An arbitrary fixed "now". Nothing here reads the wall clock: the formatter
  // takes both instants, which is the same shape `relativeTimeLabel`'s callers
  // use (they subtract at the call site).
  final now = DateTime(2026, 9, 7, 15, 30).millisecondsSinceEpoch;

  String at(int diff) =>
      notificationTimestampLabel(l10n, createdAtMillis: now - diff, now: now);

  group('the five relative buckets', () {
    test('under a minute is Just now, including the boundary below it', () {
      expect(at(0), 'Just now');
      expect(at(minute - 1), 'Just now');
    });

    test('a minute exactly is the first minutes-ago row', () {
      // `diff / 60_000L` is integer division, so this bucket renders 1..59 and
      // never 0 — the arm below it has already claimed everything under a
      // minute.
      expect(at(minute), '1 min ago');
      expect(at(5 * minute), '5 min ago');
      expect(at(hour - 1), '59 min ago');
    });

    test('an hour exactly is the first hours-ago row', () {
      expect(at(hour), '1 hr ago');
      expect(at(2 * hour), '2 hr ago');
      expect(at(day - 1), '23 hr ago');
    });

    test('a day exactly is Yesterday, for the whole of the second day', () {
      expect(at(day), 'Yesterday');
      expect(at(day + 1000), 'Yesterday');
      expect(at(2 * day - 1), 'Yesterday');
    });

    test('two days exactly is the first days-ago row', () {
      // The bucket spans 2..7 days, so `diff / 86_400_000L` yields 2..6 and
      // the ungrammatical "1 days ago" the Kotlin string would produce is
      // unreachable in both apps.
      expect(at(2 * day), '2 days ago');
      expect(at(3 * day + 1000), '3 days ago');
      expect(at(7 * day - 1), '6 days ago');
    });

    test('a future timestamp reads as Just now, as the Kotlin does', () {
      // `diff < 60_000L` is true for any negative diff, so a clock-skewed row
      // from the future lands in the first arm rather than falling through to
      // the absolute date.
      expect(at(-1), 'Just now');
      expect(at(-30 * day), 'Just now');
    });
  });

  group('the absolute arm beyond a week', () {
    test(
      'seven days exactly is an absolute date, and drops the time of day',
      () {
        // `MMM d, yyyy` — no time component, unlike the `yMMMd().add_jm()` the
        // port drew for every row before this.
        expect(at(7 * day), 'Aug 31, 2026');
        expect(at(30 * day), 'Aug 8, 2026');
      },
    );

    test('the absolute date is of the notification, not of now', () {
      final created = DateTime(2025, 1, 2, 3, 4).millisecondsSinceEpoch;
      expect(
        notificationTimestampLabel(l10n, createdAtMillis: created, now: now),
        'Jan 2, 2025',
      );
      expect(
        notificationTimestampLabel(l10n, createdAtMillis: created, now: now),
        DateFormat(
          'MMM d, yyyy',
        ).format(DateTime.fromMillisecondsSinceEpoch(created)),
      );
    });
  });

  group('the strings are the localized ones', () {
    // Kotlin reads `R.string.just_now`/`minutes_ago`/`hours_ago`/`yesterday`/
    // `days_ago`, all five of which ship a human translation in every locale.
    // The derivation tool carried them into the `.arb`, so an Arabic device
    // shows Arabic here in both apps.
    final AppLocalizations ar = AppLocalizationsAr();

    String arAt(int diff) =>
        notificationTimestampLabel(ar, createdAtMillis: now - diff, now: now);

    test('Arabic renders the Kotlin translations', () {
      // The *sentences* are at parity; the **digits are not**, and this is the
      // one place to say so rather than let the assertion read as evidence of
      // it. Kotlin reaches these strings through `Resources.getString(id, args)`
      // → `String.format(configLocale, …)`, which localises `%d`, so an Arabic
      // device shows `منذ ٥ دقيقة` in Arabic-Indic digits. `gen-l10n` emits a
      // bare `'$count'`, so the port shows ASCII. Reproducing it would mean a
      // `NumberFormat` per placeholder — and `intl`'s own `ar` symbols carry an
      // ASCII zero digit anyway, so it could not be reproduced faithfully even
      // then. A number is legible in either script; the sentence around it is
      // the part that has to be Arabic.
      expect(arAt(0), 'الآن');
      expect(arAt(5 * minute), 'منذ 5 دقيقة');
      expect(arAt(2 * hour), 'منذ 2 ساعة');
      expect(arAt(day), 'أمس');
      expect(arAt(3 * day), 'منذ 3 أيام');
    });

    test('the absolute arm uses the locale months, and survives Somali', () {
      // Kotlin builds its formatter with `Locale.getDefault()`, so the month
      // name follows the language picker. `intl` has no `so` at all and throws
      // `ArgumentError` for it — out of `build` — so that one locale falls back
      // to English month names rather than crashing the screen.
      String monthOf(AppLocalizations l) => notificationTimestampLabel(
        l,
        createdAtMillis: DateTime(2026, 8, 31).millisecondsSinceEpoch,
        now: DateTime(2026, 12, 1).millisecondsSinceEpoch,
      );

      expect(monthOf(l10n), 'Aug 31, 2026');
      expect(monthOf(ar), 'أغسطس 31, 2026');
      expect(monthOf(AppLocalizationsFr()), 'août 31, 2026');
      // Nepali month name, Nepali digits — see the group above on why the
      // digits diverge from Kotlin's ASCII here.
      expect(monthOf(AppLocalizationsNe()), 'अगस्ट ३१, २०२६');
      expect(monthOf(AppLocalizationsSo()), 'Aug 31, 2026');
    });
  });

  group('this is not relativeTimeLabel', () {
    // The two formatters port two different Kotlin functions and the Android
    // app renders two different sentences. `relativeTimeLabel` substitutes for
    // `DateUtils.getRelativeTimeSpanString` ("5 minutes ago"); the notification
    // row reads `strings.xml`'s `minutes_ago` ("5 min ago"). Collapsing them
    // would have changed one screen's text or the other's.
    test('the wordings differ, and both are deliberate', () {
      expect(relativeTimeLabel(l10n, 5 * minute), '5 minutes ago');
      expect(at(5 * minute), '5 min ago');
    });

    test('relativeTimeLabel has no Yesterday and no absolute arm', () {
      expect(relativeTimeLabel(l10n, day), '1 day ago');
      expect(relativeTimeLabel(l10n, 30 * day), '30 days ago');
    });
  });
}
