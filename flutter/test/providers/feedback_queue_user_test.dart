import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/feedback_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/feedback_provider.dart';
import 'package:myplanet/providers/session_provider.dart';

/// `FeedbackQueue.queuePending` has **two** callers, and only one of them
/// resolves the session first.
///
/// `FeedbackCreateNotifier.submit` awaits `sessionProvider.future` two lines
/// before it calls this, so by then `.value` is populated and a test driven
/// through the form cannot tell an awaited read from a `.value` one. That is
/// the fixture-that-cannot-distinguish shape: the obvious assertion
/// (`outbox.userId == 'ada'` after filing a form) stays green with the fix
/// reverted, which was measured, not guessed.
///
/// The other caller is `FeedbackSyncNotifier.runSync`, which reaches
/// `queuePending()` on a container where nothing has touched the session —
/// a background sync, not a screen. That is what this drives, and it is the
/// only input in the suite that separates the two reads.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  test('a queue pass nothing has primed still knows who is signed in', () async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    await database.feedbackDao.upsert(
      FeedbackMapper.createFeedback(
        user: 'ada',
        priority: 'No',
        type: 'Bug',
        message: 'filed earlier',
      ),
    );

    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWith((ref) => database),
        sessionProvider.overrideWith(_ResolvedSession.new),
        serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
        deviceIdentitySourceProvider.overrideWithValue(
          const FixedDeviceIdentitySource(
            DeviceIdentity(
              androidId: 'android-1',
              deviceName: 'Pixel',
              customDeviceName: 'test-phone',
            ),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);

    // Deliberately no `container.read(sessionProvider.future)` here. Priming it
    // is what makes this test unable to fail: with the session already
    // resolved, `ref.read(sessionProvider).value` answers correctly too. Do not
    // "fix a flaky test" by adding that line.
    expect(await container.read(feedbackQueueProvider).queuePending(), 1);

    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(
      queued.single.userId,
      'user-1',
      reason:
          'queuePending read the session without awaiting it, so a background '
          'pass records the row as filed by nobody',
    );
  });
}

class _ResolvedSession extends SessionNotifier {
  @override
  Future<UserRow?> build() async => UserRow(
    id: 'user-1',
    name: 'ada',
    rolesList: const ['learner'],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);
  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}
