import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'session_provider.dart';

/// State for the port of `ui/submissions/SubmissionsFragment.kt`.
final submissionsProvider = StreamProvider<List<SubmissionRow>>((ref) {
  final user = ref.watch(sessionProvider).valueOrNull;
  if (user == null) return Stream.value(const []);
  return ref.watch(submissionsRepositoryProvider).watchForUser(user.id);
});

final submissionProvider = StreamProvider.family<SubmissionRow?, String>(
  (ref, id) => ref.watch(submissionsRepositoryProvider).watchById(id),
);

final submissionAnswersProvider =
    StreamProvider.family<List<SubmissionAnswerRow>, String>(
      (ref, id) => ref.watch(submissionsRepositoryProvider).watchAnswers(id),
    );

final submissionQuestionsProvider =
    StreamProvider.family<List<SubmissionQuestionRow>, String>(
      (ref, id) => ref.watch(submissionsRepositoryProvider).watchQuestions(id),
    );
