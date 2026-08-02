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

    final result = await runSync(
      config,
      (progress) => state = SyncRunning(progress),
    );

    state = switch (result) {
      SyncComplete(:final savedCount) => SyncSucceeded(savedCount),
      SyncFailed(:final message) => SyncErrored(message),
    };
  }
}
