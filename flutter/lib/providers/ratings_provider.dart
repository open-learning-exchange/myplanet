import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../repository/ratings_repository.dart';
import 'app_providers.dart';
import 'session_provider.dart';

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
  }
}

final ratingActionsProvider = Provider(RatingActions.new);
