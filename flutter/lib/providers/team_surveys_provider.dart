import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import 'app_providers.dart';

final teamOwnedSurveysProvider = FutureProvider.family<List<SurveyRow>, String>(
  (ref, teamId) =>
      ref.watch(surveysRepositoryProvider).teamOwnedSurveys(teamId),
);

final teamAdoptableSurveysProvider =
    FutureProvider.family<List<SurveyRow>, String>(
      (ref, teamId) =>
          ref.watch(surveysRepositoryProvider).adoptableTeamSurveys(teamId),
    );
