import '../prefs/planet_prefs.dart';
import 'background_scheduler.dart';
import 'background_task_names.dart';

/// Applies persisted user policy to OS-scheduled jobs.
class BackgroundWorkCoordinator {
  BackgroundWorkCoordinator(this._scheduler, this._prefs);

  static const minimumPeriodicInterval = Duration(minutes: 15);
  static const maintenanceInterval = Duration(minutes: 15);

  final BackgroundScheduler _scheduler;
  final PlanetPrefs _prefs;

  Future<void> start() async {
    await _scheduler.initialize();
    await applyAutoSyncSettings();
    await _scheduler.schedulePeriodic(
      uniqueName: BackgroundTaskNames.maintenance,
      taskName: BackgroundTaskNames.maintenance,
      frequency: maintenanceInterval,
      requiresNetwork: true,
    );
  }

  Future<void> applyAutoSyncSettings() async {
    if (!_prefs.autoSyncEnabled) {
      await _scheduler.cancel(BackgroundTaskNames.autoSync);
      return;
    }

    final requested = _prefs.autoSyncInterval;
    await _scheduler.schedulePeriodic(
      uniqueName: BackgroundTaskNames.autoSync,
      taskName: BackgroundTaskNames.autoSync,
      frequency: requested < minimumPeriodicInterval
          ? minimumPeriodicInterval
          : requested,
      requiresNetwork: true,
    );
  }
}
