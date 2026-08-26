import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import '../repository/surveys_repository.dart';
import 'app_providers.dart';
import 'sync_state.dart';

enum SurveySort { newest, oldest, titleAscending, titleDescending }

final surveySearchProvider = StateProvider<String>((ref) => '');
final surveySortProvider = StateProvider<SurveySort>(
  (ref) => SurveySort.newest,
);

final surveysProvider = StreamProvider<List<SurveyRow>>((ref) async* {
  final query = ref.watch(surveySearchProvider);
  final sort = ref.watch(surveySortProvider);
  await for (final cached in ref.watch(surveysRepositoryProvider).watchAll()) {
    final individualIds =
        (await ref.watch(surveysRepositoryProvider).individualSurveys())
            .map((row) => row.id)
            .toSet();
    // Port of `SurveysViewModel.filter`: ranked startsWith-before-contains
    // matching on `name` only (the Kotlin never searches description), with
    // accent folding via `normalizeText`. Plain `toLowerCase().contains`
    // could neither rank nor word-split nor fold accents.
    final ranked = searchSurveys(
      cached.where((row) => individualIds.contains(row.id)).toList(),
      query,
    );
    final rows = ranked
      ..sort(switch (sort) {
        // `getSortDate` prefers adoptionDate over createdDate for adopted
        // surveys (those with a sourceSurveyId).
        SurveySort.newest => (a, b) => surveySortDate(
          b,
        ).compareTo(surveySortDate(a)),
        SurveySort.oldest => (a, b) => surveySortDate(
          a,
        ).compareTo(surveySortDate(b)),
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
