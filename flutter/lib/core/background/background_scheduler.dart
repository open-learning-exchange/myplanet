import 'package:workmanager/workmanager.dart';

import '../../background_entrypoint.dart';

/// Small seam around the plugin so scheduling policy has ordinary unit tests.
abstract interface class BackgroundScheduler {
  Future<void> initialize();

  Future<void> schedulePeriodic({
    required String uniqueName,
    required String taskName,
    required Duration frequency,
    required bool requiresNetwork,
  });

  Future<void> cancel(String uniqueName);
}

class WorkmanagerScheduler implements BackgroundScheduler {
  const WorkmanagerScheduler();

  @override
  Future<void> initialize() => Workmanager().initialize(callbackDispatcher);

  @override
  Future<void> schedulePeriodic({
    required String uniqueName,
    required String taskName,
    required Duration frequency,
    required bool requiresNetwork,
  }) => Workmanager().registerPeriodicTask(
    uniqueName,
    taskName,
    frequency: frequency,
    existingWorkPolicy: ExistingPeriodicWorkPolicy.update,
    constraints: Constraints(
      networkType: requiresNetwork
          ? NetworkType.connected
          : NetworkType.notRequired,
      requiresBatteryNotLow: true,
    ),
  );

  @override
  Future<void> cancel(String uniqueName) =>
      Workmanager().cancelByUniqueName(uniqueName);
}
