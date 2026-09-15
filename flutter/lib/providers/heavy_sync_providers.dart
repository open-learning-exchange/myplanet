import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/background/heavy_table_scheduler.dart';
import '../core/sync/heavy_table_sync.dart';
import 'app_providers.dart';

/// The `workmanager` side of heavy-table scheduling. Overridden in tests,
/// which must not reach a plugin channel.
final heavyTableWorkSchedulerProvider = Provider<HeavyTableWorkScheduler>(
  (ref) => const WorkmanagerHeavyTableScheduler(),
);

final heavyTableSyncSchedulerProvider = Provider<HeavyTableSyncScheduler>(
  (ref) => HeavyTableSyncScheduler(
    ref.watch(heavyTableWorkSchedulerProvider),
    ref.watch(planetPrefsProvider),
  ),
);

/// The walk itself, with one writer per table.
///
/// The writers are the repositories' existing `insert…FromSync` merges —
/// exactly the dispatch `TransactionSyncManager.syncDb`'s `when (table)` does,
/// with the pagination and the checkpoint kept out of the repositories. Both
/// merges were already here and reachable from nothing that runs: the walks
/// that called them were removed as a stopgap and their dartdocs named this
/// worker as what they were waiting for. They are wired *here* rather than
/// given a caller back inside the interactive sync, which is what could not
/// finish.
///
/// `submissions` is deliberately absent even though Kotlin walks it here —
/// see [HeavyTableSync.tables] for why its inline pull is worth more than the
/// parity.
///
/// **Every key here must also be in [HeavyTableSync.tables].** A table
/// scheduled with no writer is a job that runs, walks nothing and reports
/// success for ever — `team_activities` was kept out of that list for several
/// phases for exactly this reason, until the writer above existed.
/// `heavy_table_sync_scheduling_test.dart` pins both halves: that every
/// scheduled table appears in `writableTables`, and — because an audit once
/// replaced every writer body with a no-op and the suite stayed green — that
/// driving each real writer over a stub page actually lands a row.
final heavyTableSyncProvider = Provider<HeavyTableSync>(
  (ref) => HeavyTableSync(
    api: ref.watch(planetApiProvider),
    prefs: ref.watch(planetPrefsProvider),
    writers: {
      'courses_progress': (docs) async {
        await ref
            .read(progressRepositoryProvider)
            .insertCourseProgressFromSync(docs);
      },
      'login_activities': (docs) async {
        await ref
            .read(activitiesRepositoryProvider)
            .insertLoginActivitiesFromSync(docs);
      },
      // The `team_activities` pull the port never had. Without this entry the
      // walk is unreachable and the leaderboard keeps ranking members by what
      // one handset observed — see
      // `TeamsRepository.insertTeamActivitiesFromSync`.
      'team_activities': (docs) async {
        await ref
            .read(teamsRepositoryProvider)
            .insertTeamActivitiesFromSync(docs);
      },
    },
  ),
);
