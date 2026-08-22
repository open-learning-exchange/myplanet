import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../repository/ratings_repository.dart';
import 'app_providers.dart';
import 'session_provider.dart';

/// Port of `ui/ratings/RatingsViewModel.kt`.
///
/// [RatingTarget] is the `(type, item)` pair the Kotlin passes around as two
/// loose strings; making it a record keeps the family key from being ordered
/// wrongly at a call site.
typedef RatingTarget = ({String type, String itemId});

final ratingSummaryProvider =
    StreamProvider.family<RatingSummary, RatingTarget>((ref, target) {
      final userId = ref.watch(sessionProvider).valueOrNull?.id;
      return ref
          .watch(ratingsRepositoryProvider)
          .watchSummary(target.type, target.itemId, userId);
    });

class RatingActions {
  const RatingActions(this.ref);
  final Ref ref;

  Future<void> submit({
    required RatingTarget target,
    required String title,
    required int rate,
    String? comment,
  }) async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    await ref
        .read(ratingsRepositoryProvider)
        .submit(
          type: target.type,
          itemId: target.itemId,
          title: title,
          userId: user.id,
          rate: rate,
          comment: comment,
          parentCode: user.parentCode,
          planetCode: user.planetCode,
        );
    await queuePending();
  }

  /// Hands every un-uploaded rating to the durable outbox.
  ///
  /// `RatingsRepository.pendingUploads()` and `RatingsUploader` both existed
  /// with no caller between them, so a rating was saved locally and stopped
  /// there. Queued from inside `submit` so no rating screen can forget.
  Future<int> queuePending() async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return 0;
    return ref.read(ratingsUploaderProvider).queuePending(config: config);
  }
}

final ratingActionsProvider = Provider(RatingActions.new);
