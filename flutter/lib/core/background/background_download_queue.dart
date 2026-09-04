import '../../data/local/app_database.dart';
import 'background_scheduler.dart';
import 'background_task_names.dart';

/// Durable hand-off to the Android one-shot worker used for resource files.
class BackgroundDownloadQueue {
  const BackgroundDownloadQueue(this._dao, this._scheduler);

  final DownloadQueueDao _dao;
  final BackgroundScheduler _scheduler;

  Future<void> enqueue(String resourceId) async {
    await _dao.enqueue(resourceId);
    await _scheduler.scheduleOneOff(
      uniqueName: BackgroundTaskNames.downloadWork,
      taskName: BackgroundTaskNames.download,
      requiresNetwork: true,
    );
  }

  Future<List<String>> pending() async =>
      (await _dao.pending()).map((row) => row.resourceId).toList();

  Future<void> complete(String resourceId) async {
    await _dao.complete(resourceId);
  }
}
