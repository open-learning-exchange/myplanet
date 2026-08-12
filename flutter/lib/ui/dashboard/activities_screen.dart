import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/dashboard_providers.dart';
import '../../providers/session_provider.dart';

/// Port of `ui/dashboard/ActivitiesFragment.kt` — the user's offline logins for
/// the past year, bucketed by calendar month.
///
/// The Kotlin renders this with MPAndroidChart (`BarChart`, one `BarEntry` per
/// month). The port draws the bars itself rather than taking a charting
/// dependency for one screen: the data is at most twelve integers, and the
/// Kotlin's chart is a plain bar chart with month labels and no interaction
/// beyond MPAndroidChart's built-in gestures.
///
/// The month bucketing reproduces `computeMonthlyCounts` exactly, including its
/// quirk: logins are grouped by `Calendar.MONTH` **alone**, so a login from
/// eleven months ago and one from this month in a different year land in the
/// same bar. The window is the last 365 days, so at most one year is in scope
/// and collisions need a login on the same month either side of the boundary.
class ActivitiesScreen extends ConsumerWidget {
  const ActivitiesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final session = ref.watch(sessionProvider).valueOrNull;
    // `userSessionManager.getUserModel()?.name ?: return@launch` — no name, no
    // chart.
    final userName = session?.name ?? '';
    final logins = ref.watch(offlineLoginsProvider(userName));

    return Scaffold(
      appBar: AppBar(title: Text(l10n.myActivities)),
      body: logins.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(l10n.operationFailed)),
        data: (rows) {
          final counts = monthlyLoginCounts(rows, now: DateTime.now());
          if (counts.isEmpty) {
            // `binding.emptyState.visibility = VISIBLE`.
            return Center(child: Text(l10n.noDataAvailable));
          }
          return Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  l10n.chartLabel,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 16),
                Expanded(child: _LoginBarChart(counts: counts)),
              ],
            ),
          );
        },
      ),
    );
  }
}

/// Logins per calendar month over the year ending at [now], keyed by
/// `DateTime.month` (1–12) and ordered by key, as the Kotlin's `toSortedMap()`
/// leaves it.
///
/// Port of `ActivitiesFragment.computeMonthlyCounts`: rows without a
/// `loginTime` are dropped (`mapNotNull`), and the window is inclusive at both
/// ends (`it in startMillis..endMillis`).
@visibleForTesting
Map<int, int> monthlyLoginCounts(
  List<OfflineActivityRow> logins, {
  required DateTime now,
}) {
  final endMillis = now.millisecondsSinceEpoch;
  // `Calendar.getInstance().apply { add(Calendar.YEAR, -1) }`.
  final startMillis = DateTime(
    now.year - 1,
    now.month,
    now.day,
    now.hour,
    now.minute,
    now.second,
    now.millisecond,
  ).millisecondsSinceEpoch;

  final counts = <int, int>{};
  for (final row in logins) {
    final loginTime = row.loginTime;
    if (loginTime == null) continue;
    if (loginTime < startMillis || loginTime > endMillis) continue;
    final month = DateTime.fromMillisecondsSinceEpoch(loginTime).month;
    counts[month] = (counts[month] ?? 0) + 1;
  }
  return Map.fromEntries(
    counts.entries.toList()..sort((a, b) => a.key.compareTo(b.key)),
  );
}

/// The bars: one per month present in [counts], height proportional to the
/// busiest month, with the count above and the localised month name below.
class _LoginBarChart extends StatelessWidget {
  const _LoginBarChart({required this.counts});

  final Map<int, int> counts;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final max = counts.values.reduce((a, b) => a > b ? a : b);
    // `DateFormatSymbols().months[month]` — the full month name in the current
    // locale. `DateFormat.MMM` keeps it short enough for a dozen labels.
    final monthName = DateFormat.MMM(
      Localizations.localeOf(context).toLanguageTag(),
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        // Room for the value label and the month label, so the bar itself never
        // overflows the column.
        final chartHeight = (constraints.maxHeight - 44).clamp(24.0, 400.0);
        return SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              for (final entry in counts.entries)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 6),
                  child: Semantics(
                    label:
                        '${monthName.format(DateTime(2000, entry.key))}: ${entry.value}',
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        Text(
                          '${entry.value}',
                          style: Theme.of(context).textTheme.labelSmall,
                        ),
                        const SizedBox(height: 4),
                        Container(
                          width: 28,
                          height: chartHeight * (entry.value / max),
                          decoration: BoxDecoration(
                            color: scheme.primary,
                            borderRadius: const BorderRadius.vertical(
                              top: Radius.circular(4),
                            ),
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          monthName.format(DateTime(2000, entry.key)),
                          style: Theme.of(context).textTheme.labelSmall,
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
}
