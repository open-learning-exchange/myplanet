import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/feedback_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/feedback_repository.dart';
import 'package:myplanet/repository/feedback_uploader.dart';

class MockFeedbackRepository extends Mock implements FeedbackRepository {}

class MockFeedbackUploader extends Mock implements FeedbackUploader {}

/// The pull merges an admin's replies into a thread whose own reply has not
/// been sent (`FeedbackMapper._mergePendingReplies`). The outbox, though,
/// holds the payload **as it was when the reply was written** — and the two
/// feedback screens are the only other callers of `queuePending`, so without
/// this the merged thread would sit in the database while the stale snapshot
/// drained over the server's document and undid the merge there.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  late MockFeedbackRepository repository;
  late MockFeedbackUploader uploader;

  setUpAll(() {
    registerFallbackValue(
      const ServerConfig(serverUrl: '', couchDbUrl: '', pin: ''),
    );
  });

  setUp(() {
    repository = MockFeedbackRepository();
    uploader = MockFeedbackUploader();
    when(
      () => uploader.queuePending(
        config: any(named: 'config'),
        userId: any(named: 'userId'),
      ),
    ).thenAnswer((_) async => 1);
  });

  ProviderContainer containerFor() {
    final container = ProviderContainer(
      overrides: [
        feedbackRepositoryProvider.overrideWithValue(repository),
        feedbackUploaderProvider.overrideWithValue(uploader),
        serverConfigProvider.overrideWith(_TestConfig.new),
        sessionProvider.overrideWith(_NoSessionNotifier.new),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  test('a completed pull refreshes the queued payload', () async {
    when(
      () => repository.sync(
        config: any(named: 'config'),
        onProgress: any(named: 'onProgress'),
      ),
    ).thenAnswer((_) async => const SyncComplete(3));

    final container = containerFor();
    final result = await container
        .read(feedbackSyncProvider.notifier)
        .runSync(config, (_) {});

    expect(result, isA<SyncComplete>());
    verify(
      () => uploader.queuePending(
        config: config,
        userId: any(named: 'userId'),
      ),
    ).called(1);
  });

  test('a failed pull queues nothing', () async {
    // Half a page of documents is not a thread state worth pushing back.
    when(
      () => repository.sync(
        config: any(named: 'config'),
        onProgress: any(named: 'onProgress'),
      ),
    ).thenAnswer((_) async => const SyncFailed('unreachable'));

    final container = containerFor();
    final result = await container
        .read(feedbackSyncProvider.notifier)
        .runSync(config, (_) {});

    expect(result, isA<SyncFailed>());
    verifyNever(
      () => uploader.queuePending(
        config: any(named: 'config'),
        userId: any(named: 'userId'),
      ),
    );
  });
}

class _TestConfig extends ServerConfigNotifier {
  @override
  ServerConfig? build() => const ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );
}

class _NoSessionNotifier extends SessionNotifier {
  @override
  Future<UserRow?> build() async => null;
}
