import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import '../repository/progress_repository.dart';
import 'app_providers.dart';

/// The home dashboard's card data, replacing `DashboardViewModel.loadUserContent`
/// and `BaseDashboardFragment`'s flow collections.

/// The user's shelf resources — `resourcesRepository.getMyLibrary(userId)`.
/// The Kotlin reads this once per screen load; a stream costs nothing extra
/// here and keeps the card current after a shelf edit.
final myLibraryStreamProvider =
    StreamProvider.family<List<MyLibraryRow>, String>(
      (ref, userId) =>
          ref.watch(myLibraryDaoProvider).watchResources(shelfUserId: userId),
    );

/// The user's joined courses — `coursesRepository.getMyCoursesFlow(userId)`,
/// including its blank-title filter (`renderMyCourses` drops those before
/// counting).
final myCoursesStreamProvider = StreamProvider.family<List<CourseRow>, String>(
  (ref, userId) => ref
      .watch(courseDaoProvider)
      .watchCourses(shelfUserId: userId)
      .map(
        (rows) => rows
            .where((row) => (row.courseTitle ?? '').trim().isNotEmpty)
            .toList(growable: false),
      ),
);

/// The user's teams — `teamsRepository.getMyTeamsFlow(userId)`: membership
/// rows resolved to their team documents, archived teams dropped.
///
/// Drift re-emits the membership stream on any write to the `teams` table
/// (memberships and teams share it), so the second-step read stays current
/// without its own watcher.
final myTeamsStreamProvider = StreamProvider.family<List<TeamRow>, String>((
  ref,
  userId,
) {
  final dao = ref.watch(teamDaoProvider);
  return dao.watchMemberships(userId).asyncMap((memberships) {
    final teamIds = memberships
        .map((row) => row.teamId)
        .whereType<String>()
        .where((id) => id.isNotEmpty)
        .toSet()
        .toList(growable: false);
    if (teamIds.isEmpty) return Future.value(const <TeamRow>[]);
    return dao.teamsByIds(teamIds);
  });
});

/// One pending individual survey, deduped: the submission to open and the
/// survey name the dialog lists.
class PendingSurvey {
  const PendingSurvey({required this.submissionId, required this.name});

  final String submissionId;
  final String name;
}

/// Port of `BellDashboardFragment.checkPendingSurveys` — pending individual
/// survey submissions, deduped to one per survey, resolved to titles. Only
/// submissions whose survey still exists are counted, exactly as the Kotlin
/// drops candidates whose exam is gone.
final pendingSurveysProvider =
    FutureProvider.family<List<PendingSurvey>, String>((ref, userId) async {
      final submissions = await ref
          .watch(submissionDaoProvider)
          .pendingSurveySubmissions(userId);

      // One submission per survey, first (oldest) wins — LinkedHashMap order,
      // as the Kotlin's dedupe does.
      final bySurveyId = <String, SubmissionRow>{};
      for (final submission in submissions) {
        final surveyId = submission.parentId ?? '';
        if (surveyId.isEmpty) continue;
        bySurveyId.putIfAbsent(surveyId, () => submission);
      }
      if (bySurveyId.isEmpty) return const [];

      final surveys = await ref
          .watch(surveyDaoProvider)
          .getByIds(bySurveyId.keys.toList(growable: false));
      final surveysById = {for (final row in surveys) row.id: row};

      return [
        for (final entry in bySurveyId.entries)
          if (surveysById[entry.key] != null)
            PendingSurvey(
              submissionId: entry.value.id,
              name:
                  surveysById[entry.key]!.name ??
                  _nameFromParentJson(entry.value.parent),
            ),
      ];
    });

/// A submission's `parent` column holds the survey document it was created
/// from; the name there is the fallback when the survey row carries none.
String _nameFromParentJson(String? parentJson) {
  if (parentJson == null || parentJson.isEmpty) return '';
  try {
    final parsed = jsonDecode(parentJson);
    if (parsed is Map<String, dynamic>) {
      final name = parsed['name'];
      if (name is String) return name;
    }
  } catch (_) {}
  return '';
}

/// Port of `BellDashboardViewModel.loadCompletedCourses(userId)` — the
/// completed-course list the home profile card renders as a star badge row.
///
/// Keyed on `userId` (not the whole session) because the Kotlin loads it once
/// per user and re-loads on a user change; a `FutureProvider.family` matches
/// that lifetime without holding the value in a `Notifier`'s state. A
/// successful course-progress sync should refresh this — callers invalidate it
/// after `syncCourseProgress` returns, as the Kotlin's `collectLatestWhenStarted`
/// re-emits on the ViewModel's own `_completedCourses`.
final completedCoursesProvider =
    FutureProvider.family<List<CourseCompletion>, String>(
      (ref, userId) =>
          ref.watch(progressRepositoryProvider).completedCourses(userId),
    );

/// Port of `BellDashboardFragment.setColor` — whether a completed course is
/// certified, so its star renders in the primary colour rather than the
/// greyed-out "completed but not certified" tint.
///
/// Per-course, fetched lazily: the Kotlin launches a coroutine per star, and a
/// family provider mirrors that without pre-loading certification rows for
/// courses that never completed.
final isCourseCertifiedProvider = FutureProvider.family<bool, String>(
  (ref, courseId) =>
      ref.watch(progressRepositoryProvider).isCourseCertified(courseId),
);
