import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'sync_state.dart';

enum SurveySort { newest, oldest, titleAscending, titleDescending }

final surveySearchProvider = StateProvider<String>((ref) => '');
final surveySortProvider = StateProvider<SurveySort>(
  (ref) => SurveySort.newest,
);

final surveysProvider = StreamProvider<List<SurveyRow>>((ref) async* {
  final query = ref.watch(surveySearchProvider).trim().toLowerCase();
  final sort = ref.watch(surveySortProvider);
  await for (final cached in ref.watch(surveysRepositoryProvider).watchAll()) {
    final rows = cached
        .where(
          (row) =>
              !row.teamShareAllowed &&
              (row.teamId == null || row.teamId!.isEmpty) &&
              (query.isEmpty ||
                  (row.name ?? '').toLowerCase().contains(query) ||
                  (row.description ?? '').toLowerCase().contains(query)),
        )
        .toList();
    rows.sort(switch (sort) {
      SurveySort.newest => (a, b) => b.createdDate.compareTo(a.createdDate),
      SurveySort.oldest => (a, b) => a.createdDate.compareTo(b.createdDate),
      SurveySort.titleAscending => (a, b) => (a.name ?? '').compareTo(
        b.name ?? '',
      ),
      SurveySort.titleDescending => (a, b) => (b.name ?? '').compareTo(
        a.name ?? '',
      ),
    });
    yield rows;
  }
});

final surveyProvider = FutureProvider.family<SurveyRow?, String>(
  (ref, id) => ref.watch(surveysRepositoryProvider).getById(id),
);
final surveyQuestionsProvider =
    FutureProvider.family<List<SurveyQuestionRow>, String>(
      (ref, id) => ref.watch(surveysRepositoryProvider).questionsFor(id),
    );

class SurveysSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) => ref
      .read(surveysRepositoryProvider)
      .sync(config: config, onProgress: onProgress);
}

final surveysSyncProvider = NotifierProvider<SurveysSyncNotifier, SyncUiState>(
  SurveysSyncNotifier.new,
);
