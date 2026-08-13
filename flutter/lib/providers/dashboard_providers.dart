import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:meta/meta.dart';

import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/local/app_database.dart';
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

/// One star on the home dashboard's completed-course row. Port of
/// `BellDashboardFragment.showBadges` + `setColor`: a course is complete when
/// every step is passed, and the star is tinted with the primary color when
/// the course is certified, otherwise a muted blue-grey.
class CompletedCourseBadge {
  const CompletedCourseBadge({
    required this.courseId,
    this.courseTitle,
    required this.certified,
  });

  final String courseId;
  final String? courseTitle;
  final bool certified;
}

/// Port of `BellDashboardViewModel.loadCompletedCourses` — the completed-course
/// list for the home dashboard's star row, each resolved to whether it is
/// certified. The Kotlin calls `progressRepository.getCompletedCourses` once and
/// `coursesRepository.isCourseCertified` per star (in `setColor`); the count is
/// small, so the certifications are resolved in the provider and the widget
/// stays synchronous.
final completedCoursesProvider =
    FutureProvider.family<List<CompletedCourseBadge>, String>((
      ref,
      userId,
    ) async {
      final progress = ref.watch(progressRepositoryProvider);
      final completed = await progress.completedCourses(userId);
      if (completed.isEmpty) return const <CompletedCourseBadge>[];
      return [
        for (final course in completed)
          CompletedCourseBadge(
            courseId: course.courseId,
            courseTitle: course.courseTitle,
            certified: await progress.isCourseCertified(course.courseId),
          ),
      ];
    });

/// Identifies a CouchDB attachment to fetch as a profile photo. The
/// [imageName] is the `_attachments` key stored in `users.userImage` by
/// [UserMapper]; the [userId] is the document id.
@immutable
class ProfileImageRequest {
  const ProfileImageRequest({required this.userId, required this.imageName});

  final String userId;
  final String imageName;

  @override
  bool operator ==(Object other) =>
      other is ProfileImageRequest &&
      other.userId == userId &&
      other.imageName == imageName;

  @override
  int get hashCode => Object.hash(userId, imageName);
}

/// Port of the Kotlin profile-photo fetch: a `_users` document's attachment
/// is a CouchDB blob behind Basic auth with the `satellite` account, so it
/// cannot be loaded with a plain `Image.network`. The attachment *name* is
/// persisted (no credentials), and the full URL is rebuilt here against the
/// current [serverConfigProvider], fetched as bytes through [PlanetApi.getBytes]
/// (the same path resource downloads use), and handed to `Image.memory`.
///
/// Returns `null` when there is no attachment, the config is absent, or the
/// fetch fails - the widget falls back to the user's initials, matching
/// Kotlin's `R.drawable.profile` placeholder path.
final profileImageProvider =
    FutureProvider.family<Uint8List?, ProfileImageRequest>((ref, key) async {
      if (key.userId.isEmpty || key.imageName.isEmpty) return null;
      final config = ref.watch(serverConfigProvider);
      if (config == null) return null;
      final url = UrlUtils.userImageUrl(config, key.userId, key.imageName);
      if (url == null) return null;
      final authHeader = UrlUtils.basicAuthHeader('satellite', config.pin);
      final result = await ref
          .watch(planetApiProvider)
          .getBytes(url, authHeader: authHeader);
      return switch (result) {
        NetworkSuccess<List<int>>(:final data) => Uint8List.fromList(data),
        NetworkError<List<int>>() => null,
        NetworkException<List<int>>() => null,
      };
    });
