import '../../../repository/progress_repository.dart'
    show CourseProgressSummary;

/// One row per team member in the leaderboard: their display name, courses
/// completed, surveys completed, and whether they are the current user (for
/// highlighting).
///
/// **There is no Kotlin counterpart.** `app/` has no `ui/teams/leaderboard/`
/// and no `TeamLeaderboardEntry.kt` — `grep -rn "eaderboard" app/src/main
/// --include=*.kt` finds nothing. Phase 73 ported this from the unmerged
/// upstream `14880` branch, so anything here is unverifiable against the
/// shipping app and must not be described as parity.
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

/// Pure logic: given members, course progress, and survey completion
/// timestamps, build the ranked list sorted by courses completed then surveys
/// completed (both descending). No Kotlin counterpart — see
/// [TeamLeaderboardEntry].
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
      // A total order, so a displayed *rank* is reproducible. The stronger
      // claim this comment first made — that ties could shuffle between two
      // loads of unchanged data — was overstated: Dart's `List.sort` uses a
      // stable insertion sort below 32 elements, and `watchMembers` orders by
      // `userId ASC`, so a team under 32 members was already deterministic
      // (ranked by user id). This makes it deterministic for any size, and
      // ranks ties by name rather than by an opaque id — a deliberate choice,
      // not a port: there is no Kotlin leaderboard to match (see the class
      // doc), so nothing specifies the tie order.
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
