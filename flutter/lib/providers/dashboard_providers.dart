import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:meta/meta.dart';

import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/local/app_database.dart';
import '../repository/notifications_repository.dart';
import '../repository/submissions_repository.dart';
import 'app_providers.dart';

/// The home dashboard's card data, replacing `DashboardViewModel.loadUserContent`
/// and `BaseDashboardFragment`'s flow collections.

/// The user's shelf resources — `resourcesRepository.getMyLibrary(userId)`.
/// The Kotlin reads this once per screen load; a stream costs nothing extra
/// here and keeps the card current after a shelf edit.
final myLibraryStreamProvider =
    StreamProvider.family<List<MyLibraryRow>, String>(
      (ref, userId) => ref
          .watch(myLibraryDaoProvider)
          .watchResources(shelfUserId: userId, myLibrary: true),
    );

/// Port of `DashboardViewModel.updateResourceNotification` (`:134-137`) and the
/// `DashboardActivity` trigger points that call it, via
/// `checkAndCreateNewNotifications` (`:354-362`).
///
/// The Android bell carries a *"You have N resources not downloaded"* row; the
/// port's never did, because `NotificationsRepository.updateResourceNotification`
/// was ported with six tests and **no caller** — the "ported, tested, green and
/// dead" class. This provider is that caller.
///
/// ## Why a stream, when the Kotlin calls a suspend function
///
/// The Kotlin fires the same one-line body from three places
/// (`DashboardActivity.kt`):
///
/// 1. dashboard load — `initializeDashboard` → `checkIfShouldShowNotifications`
///    (`:193`, `:690-698`), once per Activity unless launched from login,
///    after a `delay(1000)`;
/// 2. **data change** — `setupDashboardDataObserver` (`:591-595`) collects
///    `dashboardDataFlow`, two of whose four merged flows are `my_library`
///    queries, and `onRealmDataChange` (`:622-630`) re-runs the check, throttled
///    to once per 5s;
/// 3. notification permission granted (`:1116-1121`), unthrottled.
///
/// Watching the count collapses (1) and (2) into one subscription: the first
/// emission is the load, and drift re-runs the query on any `my_library` write,
/// which is what `dashboardDataFlow` was merging those two flows to detect.
/// That is the substitution the port already makes for
/// `RealtimeSyncManager.dataUpdateFlow` throughout.
///
/// Three deliberate omissions, none of them an improvement invented here:
///
/// * **No 1000ms delay.** It exists to let the Kotlin dashboard draw before a
///   DB round trip on the main-thread-adjacent path; a drift stream's first
///   emission is already off the first frame.
/// * **No 5s throttle.** The Kotlin's guards a pair of Realm change callbacks
///   that storm; drift coalesces its own updates, and the write is a no-op when
///   the count is unchanged (`updateResourceNotification` returns early on an
///   equal message), so a burst of shelf writes costs at most one row update.
/// * **No permission trigger.** It exists so the Kotlin can *raise an OS
///   notification* it suppressed while permission was denied. This row is a
///   bell row only — `DashboardUiState.newNotifications` has no writer, so
///   `createResourceNotification` never fires in the Android app either
///   (`PHASE_130_NOTES.md`). Nothing to re-raise.
///
/// The unread badge needs no equivalent of `checkAndCreateNewNotifications`'
/// second half: `unreadNotificationCountProvider` is itself a drift stream, so
/// writing the row updates the badge without a push into UI state.
///
/// The count is a value, not a void, so a test can assert what was written and
/// a caller can watch it; the write is the point.
final resourceUpdateNotificationProvider = StreamProvider.family<int, String>((
  ref,
  userId,
) {
  if (userId.isEmpty) return Stream.value(0);
  final notifications = ref.watch(notificationsRepositoryProvider);
  return ref
      .watch(resourcesRepositoryProvider)
      .watchResourcesNeedingUpdateCount(userId)
      .asyncMap((count) async {
        await notifications.updateResourceNotification(userId, count);
        return count;
      });
});

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

/// One completed course, for the profile card's star row: the course to open
/// and whether a certification covers it (which tints the star).
///
/// Port of `model/CourseCompletion.kt` plus the `setColor` lookup
/// `BellDashboardFragment` runs per star.
class CompletedCourse {
  const CompletedCourse({
    required this.courseId,
    required this.courseTitle,
    required this.certified,
  });

  final String courseId;
  final String courseTitle;
  final bool certified;
}

/// Port of `BellDashboardViewModel.loadCompletedCourses` — the stars on the
/// profile card, one per course whose every step the user has passed.
///
/// The Kotlin drops completions with a blank id or title
/// (`hasValidId && hasValidTitle`) because the star's only affordances are
/// opening the course and reading its name in the content description; this
/// keeps that filter.
final completedCoursesProvider =
    FutureProvider.family<List<CompletedCourse>, String>((ref, userId) async {
      final progress = ref.watch(progressRepositoryProvider);
      final completedIds = await progress.completedCourseIds(userId);
      if (completedIds.isEmpty) return const [];

      final courses = await ref
          .watch(courseDaoProvider)
          .getByIds(completedIds.toList(growable: false));

      final result = <CompletedCourse>[];
      for (final course in courses) {
        final title = course.courseTitle ?? '';
        if (course.id.isEmpty || title.trim().isEmpty) continue;
        result.add(
          CompletedCourse(
            courseId: course.id,
            courseTitle: title,
            certified: await progress.isCourseCertified(course.id),
          ),
        );
      }
      return result;
    });

/// Port of `BaseDashboardFragment.renderMyTeams`'s notification pass — the chat
/// and task dots per team tile.
///
/// Keyed by user id and recomputed whenever the team list changes, which is
/// when the Kotlin recomputes it too (`renderMyTeams` runs the query after
/// laying the tiles out).
final teamNotificationsProvider =
    FutureProvider.family<Map<String, TeamNotificationInfo>, String>((
      ref,
      userId,
    ) async {
      final teams = await ref.watch(myTeamsStreamProvider(userId).future);
      final teamIds = [
        for (final team in teams)
          if (team.id.isNotEmpty) team.id,
      ];
      if (teamIds.isEmpty) return const {};
      return ref
          .watch(notificationsRepositoryProvider)
          .getTeamNotifications(teamIds, userId);
    });

/// Port of the `offlineLogins` half of `UserRepositoryImpl.getDashboardProfile`
/// — the "(n)" the Kotlin renders beside the user's name via
/// `R.string.user_name`.
///
/// Counted by user *name*, as the Kotlin counts it. A user with no name yields
/// zero rather than counting every row.
final offlineLoginCountProvider = FutureProvider.family<int, String>((
  ref,
  userName,
) async {
  if (userName.isEmpty) return 0;
  return ref.watch(activitiesRepositoryProvider).offlineLoginCount(userName);
});

/// The user's offline `login` rows, for the activity chart.
final offlineLoginsProvider =
    StreamProvider.family<List<OfflineActivityRow>, String>(
      (ref, userName) => userName.isEmpty
          ? Stream.value(const <OfflineActivityRow>[])
          : ref
                .watch(activitiesRepositoryProvider)
                .watchOfflineLogins(userName),
    );

/// One pending individual survey, deduped: the submission to open and the
/// survey name the dialog lists.
class PendingSurvey {
  const PendingSurvey({
    required this.submissionId,
    required this.surveyId,
    required this.name,
  });

  final String submissionId;
  final String surveyId;
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
      // as the Kotlin's dedupe does. The key is the **stripped** parent id:
      // `getUniquePendingSurveys` dedupes and resolves by
      // `examIdFromParentId()` (`SubmissionsRepositoryImpl.kt:108-121`), never
      // by the whole column, because a course-attached survey's `parentId` is
      // `"$surveyId@$courseId"` (Phase 125). Reading it raw sent the composite
      // to `getByIds`, which matched nothing, so the prompt silently dropped
      // every pending survey belonging to a course.
      final bySurveyId = <String, SubmissionRow>{};
      for (final submission in submissions) {
        final surveyId =
            SubmissionsRepository.parentBaseId(submission.parentId) ?? '';
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
              surveyId: entry.key,
              name:
                  surveysById[entry.key]!.name ??
                  _nameFromParentJson(entry.value.parent),
            ),
      ];
    });

/// How often [dueSurveyRemindersProvider] re-reads the reminder store.
///
/// The Kotlin's `delay(60_000)`. Overridable so a test can drive the poll
/// without waiting a minute of wall clock.
final surveyReminderPollIntervalProvider = Provider<Duration>(
  (ref) => const Duration(minutes: 1),
);

/// Reminder id-sets that have come due, polled on
/// [surveyReminderPollIntervalProvider].
///
/// Port of `SurveysRepositoryImpl.dueRemindersFlow`, whose body is a
/// `while (true) { … delay(60_000) }` loop over the reminder preferences. Like
/// the Kotlin it checks once on subscribe and emits only when it found
/// something, so a quiet minute produces nothing rather than an empty list the
/// UI would have to ignore.
///
/// A `Timer.periodic` cancelled in `onDispose`, rather than an `async*` loop
/// with a `Future.delayed`: an un-cancellable delay outlives the provider, which
/// keeps the poll running after the dashboard is gone and leaves a pending timer
/// that fails every widget test that mounted the screen.
final dueSurveyRemindersProvider = StreamProvider<List<String>>((ref) {
  final prefs = ref.watch(planetPrefsProvider);
  final controller = StreamController<List<String>>();

  Future<void> check() async {
    final due = await prefs.takeDueSurveyReminders(
      DateTime.now().millisecondsSinceEpoch,
    );
    if (due.isNotEmpty && !controller.isClosed) controller.add(due);
  }

  final timer = Timer.periodic(
    ref.watch(surveyReminderPollIntervalProvider),
    (_) => check(),
  );
  check();

  ref.onDispose(() {
    timer.cancel();
    controller.close();
  });
  return controller.stream;
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
      final authHeader = UrlUtils.authHeader(config);
      final result = await ref
          .watch(planetApiProvider)
          .getBytes(url, authHeader: authHeader);
      return switch (result) {
        NetworkSuccess<List<int>>(:final data) => Uint8List.fromList(data),
        NetworkError<List<int>>() => null,
        NetworkException<List<int>>() => null,
      };
    });
