/// Stable names are part of the persisted Android WorkManager contract.
abstract final class BackgroundTaskNames {
  static const autoSync = 'myplanet.autoSync';
  static const maintenance = 'myplanet.maintenance';
  static const download = 'myplanet.download';
  static const downloadWork = 'myplanet.download.work';

  /// Prefix of the per-table heavy-sync task, one job per table.
  ///
  /// The table travels in the **task name** rather than in `inputData`, which
  /// is where `HeavyTableSyncWorker` puts it (`workDataOf(KEY_TABLE to table)`,
  /// read back as `inputData.getString(KEY_TABLE)`). The port dispatches on
  /// the task name alone — `callbackDispatcher` hands `executeBackgroundTask`
  /// nothing else — and a name per table is needed regardless, because
  /// scheduling is `enqueueUniqueWork("heavy_sync_$table", KEEP, …)`: the
  /// unique name is what makes a second enqueue for a table already walking a
  /// no-op instead of a second walk of the same 114k documents.
  ///
  /// Being part of the persisted contract, the scheme has to stay: a name a
  /// future build cannot parse resolves to a null table, and
  /// [heavyTableSyncTable] returning null is treated as "do not retry" rather
  /// than as an error, for the reason `BackgroundTaskRunner.run` gives about
  /// renamed tasks surviving an upgrade.
  static const heavyPrefix = 'myplanet.heavy.';

  /// The task (and unique work) name for one heavy table.
  static String heavyTableSyncTask(String table) => '$heavyPrefix$table';

  /// The table a [heavyTableSyncTask] name carries, or null when [taskName] is
  /// not one.
  static String? heavyTableSyncTable(String taskName) {
    if (!taskName.startsWith(heavyPrefix)) return null;
    final table = taskName.substring(heavyPrefix.length);
    return table.isEmpty ? null : table;
  }
}
