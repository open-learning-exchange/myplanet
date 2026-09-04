import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/sync/sync_result.dart';
import 'app_providers.dart';
import 'sync_state.dart';

/// The five sync-in walks Phase 116 found missing and Phase 119 added.
///
/// Each is an arm of `TransactionSyncManager.syncDb` or, for the shelf, phase 3
/// of `services/sync/SyncManager.kt`. They live together rather than in their
/// domain provider files because what they have in common — that the port ran
/// without them, so every screen reading their tables was dead — is more useful
/// to a reader than their individual domains.

/// `TransactionSyncManager.syncDb("tablet_users")` — the planet's accounts.
///
/// Feeds member detail, the team leaderboard and every place a team member's
/// name is rendered. Runs before [shelfSyncProvider] so a shelf keyed by a
/// CouchDB id can be resolved to the local row that carries it.
class TabletUsersSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(config, void Function(SyncProgress) onProgress) =>
      ref
          .read(userRepositoryProvider)
          .syncTabletUsers(config: config, onProgress: onProgress);
}

final tabletUsersSyncProvider =
    NotifierProvider<TabletUsersSyncNotifier, SyncUiState>(
      TabletUsersSyncNotifier.new,
    );

/// `TransactionSyncManager.syncDb("ratings")` — community ratings.
class RatingsSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(config, void Function(SyncProgress) onProgress) =>
      ref
          .read(ratingsRepositoryProvider)
          .sync(config: config, onProgress: onProgress);
}

final ratingsSyncProvider = NotifierProvider<RatingsSyncNotifier, SyncUiState>(
  RatingsSyncNotifier.new,
);

/// `TransactionSyncManager.syncDb("tasks")` — team tasks.
class TeamTasksSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(config, void Function(SyncProgress) onProgress) =>
      ref
          .read(teamTasksRepositoryProvider)
          .sync(config: config, onProgress: onProgress);
}

final teamTasksSyncProvider =
    NotifierProvider<TeamTasksSyncNotifier, SyncUiState>(
      TeamTasksSyncNotifier.new,
    );

/// `TransactionSyncManager.syncDb("achievements")`, plus the CV attachments the
/// same page downloads.
class AchievementsSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(config, void Function(SyncProgress) onProgress) =>
      ref
          .read(achievementsRepositoryProvider)
          .sync(config: config, onProgress: onProgress);
}

final achievementsSyncProvider =
    NotifierProvider<AchievementsSyncNotifier, SyncUiState>(
      AchievementsSyncNotifier.new,
    );

/// Phase 3 of `SyncManager.startFullSync` — the shelf pass.
///
/// Runs last, as it does in the Kotlin: it augments the `my_library` and
/// `courses` rows the resources and courses walks have already written, so
/// running it first would stamp membership onto rows that a later
/// `deleteNotIn` could still remove.
class ShelfSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(config, void Function(SyncProgress) onProgress) =>
      ref
          .read(shelfSyncRepositoryProvider)
          .sync(config: config, onProgress: onProgress);
}

final shelfSyncProvider = NotifierProvider<ShelfSyncNotifier, SyncUiState>(
  ShelfSyncNotifier.new,
);
