import '../prefs/planet_prefs.dart';
import 'background_scheduler.dart';
import 'background_task_names.dart';

/// Durable hand-off to the Android one-shot worker used for resource files.
class BackgroundDownloadQueue {
  const BackgroundDownloadQueue(this._prefs, this._scheduler);

  final PlanetPrefs _prefs;
  final BackgroundScheduler _scheduler;

  Future<void> enqueue(String resourceId) async {
    await _prefs.addPendingResourceDownload(resourceId);
    await _scheduler.scheduleOneOff(
      uniqueName: BackgroundTaskNames.downloadWork,
      taskName: BackgroundTaskNames.download,
      requiresNetwork: true,
    );
  }

  Future<void> complete(String resourceId) =>
      _prefs.removePendingResourceDownload(resourceId);
}
