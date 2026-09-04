import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/background/background_scheduler.dart';
import 'package:myplanet/core/background/background_task_names.dart';
import 'package:myplanet/core/background/background_work_coordinator.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MockSecureStorage extends Mock implements FlutterSecureStorage {}

class _RecordingScheduler implements BackgroundScheduler {
  var initializeCalls = 0;
  final scheduled = <({String name, Duration frequency, bool network})>[];
  final cancelled = <String>[];

  @override
  Future<void> initialize() async => initializeCalls++;

  @override
  Future<void> cancel(String uniqueName) async => cancelled.add(uniqueName);

  @override
  Future<void> scheduleOneOff({
    required String uniqueName,
    required String taskName,
    required bool requiresNetwork,
  }) async {}

  @override
  Future<void> schedulePeriodic({
    required String uniqueName,
    required String taskName,
    required Duration frequency,
    required bool requiresNetwork,
  }) async {
    expect(taskName, uniqueName);
    scheduled.add((
      name: uniqueName,
      frequency: frequency,
      network: requiresNetwork,
    ));
  }
}

Future<PlanetPrefs> _prefs(Map<String, Object> values) async {
  SharedPreferences.setMockInitialValues(values);
  return PlanetPrefs(
    await SharedPreferences.getInstance(),
    secureStorage: _MockSecureStorage(),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('start registers network sync and battery-aware maintenance', () async {
    final scheduler = _RecordingScheduler();
    final coordinator = BackgroundWorkCoordinator(scheduler, await _prefs({}));

    await coordinator.start();

    expect(scheduler.initializeCalls, 1);
    expect(scheduler.cancelled, isEmpty);
    expect(scheduler.scheduled, [
      (
        name: BackgroundTaskNames.autoSync,
        frequency: const Duration(hours: 1),
        network: true,
      ),
      (
        name: BackgroundTaskNames.maintenance,
        frequency: const Duration(minutes: 15),
        network: true,
      ),
    ]);
  });

  test('disabled auto sync cancels persisted periodic work', () async {
    final scheduler = _RecordingScheduler();
    final coordinator = BackgroundWorkCoordinator(
      scheduler,
      await _prefs({'autoSync': false}),
    );

    await coordinator.start();

    expect(scheduler.cancelled, [BackgroundTaskNames.autoSync]);
    expect(scheduler.scheduled.map((work) => work.name), [
      BackgroundTaskNames.maintenance,
    ]);
  });

  test('intervals below WorkManager floor are clamped to 15 minutes', () async {
    final scheduler = _RecordingScheduler();
    final coordinator = BackgroundWorkCoordinator(
      scheduler,
      await _prefs({'autoSyncInterval': 60}),
    );

    await coordinator.applyAutoSyncSettings();

    expect(scheduler.scheduled.single.frequency, const Duration(minutes: 15));
  });
}
