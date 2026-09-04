import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../repository/progress_repository.dart';
import '../ui/components/challenge_dialog.dart';
import 'app_providers.dart';

/// Port of `DashboardViewModel.evaluateChallengeDialog` +
/// `ChallengePrompter.showChallengeDialog`.
///
/// The December 2024 / January 2025 challenge campaign. A non-guest user on a
/// participating server, between Nov 30 2024 and Jan 16 2025, sees a dialog
/// tracking three tasks: complete the challenge course ("terminado"), post
/// five community voices, and sync. The dialog fires once on dashboard load;
/// the "sync" action is recorded separately when the user actually syncs.
class ChallengeEvaluator {
  ChallengeEvaluator(this._ref);

  final Ref _ref;

  /// The challenge course id, matching the Kotlin literal in
  /// `DashboardViewModel.evaluateChallengeDialog`.
  static const String challengeCourseId = '4e6b78800b6ad18b4e8b0e1e38a98cac';

  /// The campaign window. `startTime` / `endTime` are the same millis the
  /// Kotlin passes to `getCommunityVoiceDates`, so the voice counts match.
  static const int startTime = 1730419200000; // 2024-11-01 00:00:00 UTC
  static const int endTime = 1734307200000; // 2024-12-16 00:00:00 UTC

  /// The prompt window: the dialog only appears between these dates.
  static final DateTime promptStart = DateTime(2024, 11, 30);
  static final DateTime promptEnd = DateTime(2025, 1, 16);

  /// The servers that run the challenge. Mirrors
  /// `ServerConfigUtils.getChallengeServerUrls`, which reads BuildConfig
  /// fields populated from `gradle.properties`.
  static const List<String> challengeServerUrls = [
    'https://planet.gt',
    'http://10.82.1.31',
    'http://192.168.1.73',
    'http://192.168.48.253',
    'http://192.168.1.148',
    'https://planet.vi.ole.org',
  ];

  /// Evaluates the challenge conditions and returns the dialog data, or null
  /// when the campaign is outside its window, the user is a guest, or the
  /// server is not participating.
  Future<ChallengeDialogData?> evaluate({
    required String? userId,
    required bool isGuest,
    required String serverUrl,
    DateTime Function()? now,
  }) async {
    final today = (now ?? DateTime.now)();
    final shouldPrompt =
        today.isAfter(promptStart) &&
        today.isBefore(promptEnd) &&
        challengeServerUrls.contains(serverUrl);

    if (isGuest || !shouldPrompt) return null;

    final progressRepo = _ref.read(progressRepositoryProvider);
    final voicesRepo = _ref.read(voicesRepositoryProvider);
    final coursesRepo = _ref.read(coursesRepositoryProvider);
    final submissionsRepo = _ref.read(submissionsRepositoryProvider);
    final activitiesRepo = _ref.read(activitiesRepositoryProvider);

    final progress = await progressRepo.courseProgressForChallenge(
      userId,
      challengeCourseId,
    );
    final userDates = await voicesRepo.getCommunityVoiceDates(
      startTime,
      endTime,
      userId,
    );
    final allDates = await voicesRepo.getCommunityVoiceDates(
      startTime,
      endTime,
      null,
    );
    final courseName = await coursesRepo.getCourseTitleById(challengeCourseId);
    final hasUnfinishedSurvey = await submissionsRepo.hasPendingSurvey(
      challengeCourseId,
      userId,
    );

    final courseStatus = courseStatusString(progress, courseName);
    final voiceCount = userDates.length;
    final allVoiceCount = allDates.length;

    final prereqsMet =
        courseStatus.toLowerCase().contains('terminado') && voiceCount >= 5;
    var hasValidSync = false;
    if (prereqsMet) {
      hasValidSync = await activitiesRepo.hasUserCompletedSync(userId ?? '');
    }

    return ChallengeDialogData(
      voiceCount: voiceCount,
      allVoiceCount: allVoiceCount,
      hasUnfinishedSurvey: hasUnfinishedSurvey,
      hasValidSync: hasValidSync,
      courseStatus: courseStatus,
    );
  }

  /// Port of `DashboardViewModel.getCourseStatusString`. The Kotlin reads
  /// localized strings; the port's strings live in `app_en.arb` and the
  /// dialog itself localizes the labels, so this returns the key-bearing
  /// template the dialog interpolates.
  ///
  /// A completed course (`current == max`) returns the course name with a
  /// "terminado" marker — the dialog's completion check keys on that
  /// substring, matching the Kotlin's `courseStatus.contains("terminado")`.
  String courseStatusString(
    CourseProgressSummary? progress,
    String? courseName,
  ) {
    final name = courseName ?? '';
    if (progress == null) return name;
    final max = progress.max;
    final current = progress.current;
    if (current == max) {
      return '$name terminado';
    }
    return '$name $current/$max';
  }
}

/// The challenge evaluator, scoped to the app container.
final challengeEvaluatorProvider = Provider<ChallengeEvaluator>(
  (ref) => ChallengeEvaluator(ref),
);

/// Whether the challenge dialog has already shown its congratulations message.
/// Persisted in SharedPreferences, matching `SharedPrefManager.HAS_SHOWN_CONGRATS`.
final hasShownChallengeCongratsProvider =
    NotifierProvider<HasShownChallengeCongratsNotifier, bool>(
      HasShownChallengeCongratsNotifier.new,
    );

class HasShownChallengeCongratsNotifier extends Notifier<bool> {
  @override
  bool build() => ref.read(planetPrefsProvider).hasShownChallengeCongrats;

  Future<void> setShown() async {
    await ref.read(planetPrefsProvider).setHasShownChallengeCongrats(true);
    state = true;
  }
}
