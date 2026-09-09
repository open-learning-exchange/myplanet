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
/// with the pagination and the checkpoint kept out of the repositories. Two of
/// the three have sat here uncalled since the inline pulls were removed
/// (`ProgressRepository.syncCourseProgress`'s dartdoc and
/// `ActivitiesRepository.sync`'s both name this worker as what they were
/// waiting for), which is why they are wired *here* rather than given a caller
/// back inside the interactive sync.
///
/// `submissions` is deliberately absent even though Kotlin walks it here —
/// see [HeavyTableSync.tables] for why its inline pull is worth more than the
/// parity.
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
    },
  ),
);
