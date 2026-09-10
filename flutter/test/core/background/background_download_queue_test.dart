import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/background/background_download_queue.dart';
import 'package:myplanet/core/background/background_scheduler.dart';
import 'package:myplanet/core/background/background_task_names.dart';
import 'package:myplanet/data/local/app_database.dart';

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
  late AppDatabase database;

  setUp(() => database = AppDatabase.memory());
  tearDown(() => database.close());

  test('persists unique downloads and schedules one-shot work', () async {
    final scheduler = _Scheduler();
    final queue = BackgroundDownloadQueue(database.downloadQueueDao, scheduler);

    await queue.enqueue('resource-1');
    await queue.enqueue('resource-1');

    expect(await queue.pending(), ['resource-1']);
    expect(scheduler.oneOff, hasLength(2));

    await queue.complete('resource-1');
    expect(await queue.pending(), isEmpty);
  });
}
