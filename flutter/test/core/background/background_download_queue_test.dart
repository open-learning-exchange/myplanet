import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/background/background_download_queue.dart';
import 'package:myplanet/core/background/background_scheduler.dart';
import 'package:myplanet/core/background/background_task_names.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MockSecureStorage extends Mock implements FlutterSecureStorage {}

class _Scheduler implements BackgroundScheduler {
  final oneOff = <String>[];

  @override
  Future<void> scheduleOneOff({
    required String uniqueName,
    required String taskName,
    required bool requiresNetwork,
  }) async {
    expect(uniqueName, BackgroundTaskNames.downloadWork);
    expect(taskName, BackgroundTaskNames.download);
    expect(requiresNetwork, isTrue);
    oneOff.add(uniqueName);
  }

  @override
  Future<void> cancel(String uniqueName) async {}

  @override
  Future<void> initialize() async {}

  @override
  Future<void> schedulePeriodic({
    required String uniqueName,
    required String taskName,
    required Duration frequency,
    required bool requiresNetwork,
  }) async {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('persists unique downloads and schedules one-shot work', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = PlanetPrefs(
      await SharedPreferences.getInstance(),
      secureStorage: _MockSecureStorage(),
    );
    final scheduler = _Scheduler();
    final queue = BackgroundDownloadQueue(prefs, scheduler);

    await queue.enqueue('resource-1');
    await queue.enqueue('resource-1');

    expect(prefs.pendingResourceDownloads, ['resource-1']);
    expect(scheduler.oneOff, hasLength(2));

    await queue.complete('resource-1');
    expect(prefs.pendingResourceDownloads, isEmpty);
  });
}
