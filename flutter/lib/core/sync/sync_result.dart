import 'package:meta/meta.dart';

import '../network/network_result.dart';

/// Progress of a table pull, for the sync indicator.
@immutable
class SyncProgress {
  const SyncProgress({required this.completed, required this.total});

  final int completed;
  final int total;

  double get fraction => total == 0 ? 0 : completed / total;
}

/// Outcome of a table pull. Port of the terminal states of the `SyncStatus`
/// StateFlow in `services/sync/SyncManager.kt`.
@immutable
sealed class SyncResult {
  const SyncResult();
}

class SyncComplete extends SyncResult {
  const SyncComplete(this.savedCount);

  final int savedCount;
}

class SyncFailed extends SyncResult {
  const SyncFailed(this.message);

  final String message;
}

/// Renders a failed [NetworkResult] as a message for the sync snackbar.
String describeNetworkFailure(NetworkResult<Object?> result) {
  return switch (result) {
    NetworkError(:final code, :final message) =>
      'Server responded ${code ?? '?'}${message == null ? '' : ': $message'}',
    NetworkException(:final error) => error.toString(),
    NetworkSuccess() => '',
  };
}
