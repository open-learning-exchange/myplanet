import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'session_provider.dart';
import 'sync_state.dart';

/// One bar on the "My Activity" chart: the calendar month and how many logins
/// the user made in it. Port of `ActivitiesFragment`'s monthly grouping.
class MonthLoginCount {
  const MonthLoginCount({required this.month, required this.count});

  final DateTime month;
  final int count;
}

/// Buckets the current user's login rows by calendar month.
///
/// Mirrors `ActivitiesFragment.getOfflineLogins`: the start of the chart range
/// is twelve months before the latest login (the Kotlin walks
/// `Calendar.MONTH` offsets from the newest row), and every month in that
/// range appears in the list — empty months carry a `0` count — so the chart
/// draws a continuous axis rather than skipping quiet months.
List<MonthLoginCount> bucketLoginsByMonth(
  List<OfflineActivityRow> rows, {
  int monthCount = 12,
}) {
  if (rows.isEmpty) return const [];
  final times = rows
      .map((r) => r.loginTime ?? 0)
      .where((t) => t > 0)
      .toList();
  if (times.isEmpty) return const [];
  final latest = DateTime.fromMillisecondsSinceEpoch(times.reduce(max));
  final newestMonth = DateTime(latest.year, latest.month);

  final byMonth = <DateTime, int>{};
  for (final t in times) {
    final date = DateTime.fromMillisecondsSinceEpoch(t);
    final key = DateTime(date.year, date.month);
    byMonth[key] = (byMonth[key] ?? 0) + 1;
  }

  final result = <MonthLoginCount>[];
  for (var i = monthCount - 1; i >= 0; i--) {
    final month = DateTime(newestMonth.year, newestMonth.month - i);
    result.add(MonthLoginCount(month: month, count: byMonth[month] ?? 0));
  }
  return result;
}

/// The current user's login rows, live, for the chart.
final loginActivitiesProvider = StreamProvider<List<OfflineActivityRow>>((ref) {
  final repo = ref.watch(activitiesRepositoryProvider);
  final session = ref.watch(sessionProvider).valueOrNull;
  final userName = session?.name;
  if (userName == null || userName.isEmpty) {
    return Stream.value(const <OfflineActivityRow>[]);
  }
  return repo.loginActivities(userName);
});

/// The monthly login counts the bar chart renders, derived from
/// [loginActivitiesProvider].
final monthlyLoginCountsProvider = Provider<AsyncValue<List<MonthLoginCount>>>((
  ref,
) {
  final activities = ref.watch(loginActivitiesProvider);
  return activities.whenData(
    (rows) => bucketLoginsByMonth(rows),
  );
});

/// Drives the `login_activities` pull. Wired as a standalone sync notifier so
/// the chart's pull-to-refresh can refresh its own data without running the
/// whole dashboard sync.
class ActivitiesSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) => ref
      .read(activitiesRepositoryProvider)
      .sync(config: config, onProgress: onProgress);
}

final activitiesSyncProvider =
    NotifierProvider<ActivitiesSyncNotifier, SyncUiState>(
      ActivitiesSyncNotifier.new,
    );
