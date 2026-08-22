import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import 'app_providers.dart';

/// UI-facing state of a table pull. Port of the `SyncStatus` StateFlow in
/// `services/sync/SyncManager.kt` (Idle / Syncing / Success / Error).
sealed class SyncUiState {
  const SyncUiState();
}

class SyncIdle extends SyncUiState {
  const SyncIdle();
}

class SyncRunning extends SyncUiState {
  const SyncRunning(this.progress);

  final SyncProgress progress;
}

class SyncSucceeded extends SyncUiState {
  const SyncSucceeded(this.savedCount);

  final int savedCount;
}

class SyncErrored extends SyncUiState {
  const SyncErrored(this.message);

  final String message;
}

/// Reactive view of `SharedPrefManager.getLastSync()`.
///
/// Keeping this as provider state (rather than reading SharedPreferences in a
/// widget) lets every successful foreground sync update the dashboard strip
/// immediately.
class LastSyncNotifier extends Notifier<int> {
  @override
  int build() => ref.watch(planetPrefsProvider).lastSync;

  Future<void> recordSuccess({DateTime? at}) async {
    final timestamp = (at ?? DateTime.now()).millisecondsSinceEpoch;
    await ref.read(planetPrefsProvider).setLastSync(timestamp);
    state = timestamp;
  }
}

final lastSyncProvider = NotifierProvider<LastSyncNotifier, int>(
  LastSyncNotifier.new,
);

/// Shared driver for a single table's pull.
///
/// Both the resources and courses screens run the same cycle — refuse to start
/// twice, require a configured server, stream progress, land on success or
/// error — so it lives here once. Subclasses only supply [runSync].
abstract class SyncNotifier extends Notifier<SyncUiState> {
  @override
  SyncUiState build() => const SyncIdle();

  /// Performs the actual pull. Implementations delegate to their repository.
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  );

  Future<void> sync() async {
    if (state is SyncRunning) return;

    final config = ref.read(serverConfigProvider);
    if (config == null) return;

    state = const SyncRunning(SyncProgress(completed: 0, total: 0));

    try {
      final result = await runSync(
        config,
        (progress) => state = SyncRunning(progress),
      );

      state = switch (result) {
        SyncComplete(:final savedCount) => SyncSucceeded(savedCount),
        SyncFailed(:final message) => SyncErrored(message),
      };
      if (result is SyncComplete) {
        await ref.read(lastSyncProvider.notifier).recordSuccess();
      }
    } catch (error) {
      // Repositories return SyncFailed for network problems, but a database or
      // parsing fault still throws. Without this the state would stay
      // SyncRunning and the sync button would never re-enable.
      state = SyncErrored('$error');
    }
  }
}
