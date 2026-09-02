import '../../../repository/progress_repository.dart'
    show CourseProgressSummary;

/// Port of `model/TeamLeaderboardEntry.kt`. One row per team member in the
/// leaderboard: their display name, courses completed, surveys completed,
/// and whether they are the current user (for highlighting).
class TeamLeaderboardEntry {
  const TeamLeaderboardEntry({
    required this.userId,
    required this.displayName,
    required this.coursesCompleted,
    required this.coursesTotal,
    required this.surveysCompleted,
    required this.surveysTotal,
    required this.isCurrentUser,
    required this.visitCount,
    this.userImage,
  });

  final String userId;
  final String displayName;
  final int coursesCompleted;
  final int coursesTotal;
  final int surveysCompleted;
  final int surveysTotal;
  final bool isCurrentUser;
  final int visitCount;
  final String? userImage;
}

/// Port of `TeamLeaderboardCalculator`. Pure logic: given members, course
/// progress, and survey completion timestamps, build the ranked list sorted
/// by courses completed then surveys completed (both descending).
class TeamLeaderboardCalculator {
  const TeamLeaderboardCalculator._();

  static List<TeamLeaderboardEntry> build({
    required List<MemberWithProgress> members,
    required List<String> courseIds,
    required Map<String, CourseProgressSummary> Function(String userId)
    progressForUser,
    required List<int> Function(String userId) surveyTimestampsForUser,
    required int surveysTotal,
    required String? currentUserId,
    int? periodStart,
  }) {
    final entries = <TeamLeaderboardEntry>[];
    for (final member in members) {
      final progress = progressForUser(member.userId);
      var coursesCompleted = 0;
      for (final courseId in courseIds) {
        final cp = progress[courseId];
        if (cp == null) continue;
        final isCompleted = cp.max > 0 && cp.current >= cp.max;
        if (isCompleted) coursesCompleted++;
      }
      final timestamps = surveyTimestampsForUser(member.userId);
      final surveysCompleted = periodStart == null
          ? timestamps.length
          : timestamps.where((t) => t >= periodStart).length;
      entries.add(
        TeamLeaderboardEntry(
          userId: member.userId,
          displayName: member.displayName,
          coursesCompleted: coursesCompleted,
          coursesTotal: courseIds.length,
          surveysCompleted: surveysCompleted,
          surveysTotal: surveysTotal,
          isCurrentUser:
              currentUserId != null && member.userId == currentUserId,
          visitCount: member.visitCount,
          userImage: member.userImage,
        ),
      );
    }
    entries.sort((a, b) {
      final byCourses = b.coursesCompleted.compareTo(a.coursesCompleted);
      if (byCourses != 0) return byCourses;
      final bySurveys = b.surveysCompleted.compareTo(a.surveysCompleted);
      if (bySurveys != 0) return bySurveys;
      // Members arrive through a `Set` and `List.sort` is not stable, so
      // without a total order two members with identical scores could swap
      // rank between two loads of unchanged data. Name then id, so the
      // ranking is reproducible.
      final byName = a.displayName.toLowerCase().compareTo(
        b.displayName.toLowerCase(),
      );
      return byName != 0 ? byName : a.userId.compareTo(b.userId);
    });
    return entries;
  }

  /// Port of `startOfCurrentMonth` — the first millisecond of the current
  /// month in UTC, for the "this month" period filter.
  static int startOfCurrentMonth(int nowMillis) {
    final now = DateTime.fromMillisecondsSinceEpoch(nowMillis, isUtc: true);
    return DateTime.utc(now.year, now.month, 1).millisecondsSinceEpoch;
  }
}

/// A member with the fields the leaderboard needs, resolved from the users
/// table and team-log visit counts.
class MemberWithProgress {
  const MemberWithProgress({
    required this.userId,
    required this.displayName,
    required this.visitCount,
    this.userImage,
  });

  final String userId;
  final String displayName;
  final int visitCount;
  final String? userImage;
}
