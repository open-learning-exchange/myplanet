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
      final userId = ref.watch(sessionProvider).value?.id;
      return ref
          .watch(ratingsRepositoryProvider)
          .watchSummary(target.type, target.itemId, userId);
    });

class RatingActions {
  const RatingActions(this.ref);
  final Ref ref;

  /// Returns whether the rating was recorded, and — where there is anything
  /// to hand it to — queued.
  ///
  /// Read that qualification literally, because the first draft of this line
  /// said "recorded **and** handed to the outbox" and was false on two live
  /// paths. [queuePending] answers 0 rather than throwing when no server is
  /// configured, and `RatingDao.pendingUploads` excludes a guest
  /// (`userId LIKE 'guest%'`, mirroring Kotlin's `RatingDao.kt:36`), so both
  /// reach `true` with nothing in the outbox. That is parity, not a hole:
  /// Kotlin's dialog toasts "Thank you, your rating is submitted." for the
  /// guest too, and the local row is the whole of what its `submitRating`
  /// does (`RatingsRepositoryImpl.kt:49-77` touches no network). What a
  /// person is being told is that their rating was *recorded*, which is true
  /// in both apps.
  ///
  /// It used to return `void`, and [RatingDialog] popped `true` regardless —
  /// so a rating that was never written looked, to the person who typed it,
  /// exactly like one that was. The port's most-repeated defect
  /// (Phase 100's `_submitExam`, and four more since): the failure is not the
  /// bug, the failure being *invisible* is.
  ///
  /// `RatingsViewModel.submitRating` (`RatingsViewModel.kt:81-108`) wraps the
  /// whole thing in `try`/`catch` and publishes `SubmitState.Error(e.message)`
  /// — including for the `null` user, which it reports as `"User not found"`
  /// rather than returning quietly (`:87-90`). `RatingsFragment:115-117`
  /// toasts it and, crucially, does **not** `dismiss()`: only
  /// `SubmitState.Success` reaches `dismiss()` (`:104-113`). Both arms are
  /// mirrored here as a `bool` the caller cannot ignore.
  ///
  /// A failure to **queue** counts as a failure too, though the local row is
  /// already written. Kotlin has no outbox, so this half has no counterpart;
  /// the rating would sit on the handset with nothing to carry it, which is
  /// the state the queue call was added to prevent. Retrying is safe —
  /// `RatingsRepository.submit` upserts on the existing row's id, and
  /// `queuePending` is bounded by one outbox row per `(uploadType, itemId)`.
  Future<bool> submit({
    required RatingTarget target,
    required String title,
    required int rate,
    String? comment,
  }) async {
    try {
      final user = await resolveSession(ref);
      if (user == null) return false;
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
      return true;
    } catch (_) {
      // The `await resolveSession` sits *inside* the `try` deliberately: a
      // future can reject where `.value` could only have been null, and
      // Phase 100's fix had to be amended on harvest for putting it outside.
      return false;
    }
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
