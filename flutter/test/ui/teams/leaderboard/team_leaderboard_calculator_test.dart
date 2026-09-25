import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/ui/teams/leaderboard/team_leaderboard_calculator.dart';

import 'package:myplanet/repository/progress_repository.dart';

void main() {
  CourseProgressSummary summary(int current, int max) =>
      CourseProgressSummary(current: current, max: max);

  group('TeamLeaderboardCalculator', () {
    test('ranks by courses completed then surveys completed', () {
      final members = [
        const MemberWithProgress(
          userId: 'a',
          displayName: 'Alice',
          visitCount: 5,
        ),
        const MemberWithProgress(
          userId: 'b',
          displayName: 'Bob',
          visitCount: 3,
        ),
        const MemberWithProgress(
          userId: 'c',
          displayName: 'Carol',
          visitCount: 1,
        ),
      ];
      final courseIds = ['course-1', 'course-2'];

      final entries = TeamLeaderboardCalculator.build(
        members: members,
        courseIds: courseIds,
        progressForUser: (userId) => switch (userId) {
          'a' => {'course-1': summary(5, 5), 'course-2': summary(3, 5)},
          'b' => {'course-1': summary(5, 5), 'course-2': summary(5, 5)},
          _ => {},
        },
        surveyTimestampsForUser: (userId) => switch (userId) {
          'a' => [1000, 2000],
          'b' => [1000],
          _ => [],
        },
        surveysTotal: 3,
        currentUserId: 'a',
      );

      // Bob completes 2 courses → rank 1, Alice 1 → rank 2, Carol 0 → rank 3.
      expect(entries.map((e) => e.userId).toList(), ['b', 'a', 'c']);
      expect(entries[0].coursesCompleted, 2);
      expect(entries[1].coursesCompleted, 1);
      expect(entries[1].surveysCompleted, 2);
      expect(entries[1].isCurrentUser, isTrue);
    });

    test('thisMonth period filters survey timestamps', () {
      final members = [
        const MemberWithProgress(
          userId: 'a',
          displayName: 'Alice',
          visitCount: 0,
        ),
      ];
      final now = DateTime.utc(2026, 8, 15).millisecondsSinceEpoch;
      final periodStart = TeamLeaderboardCalculator.startOfCurrentMonth(now);

      final entries = TeamLeaderboardCalculator.build(
        members: members,
        courseIds: const [],
        progressForUser: (_) => {},
        surveyTimestampsForUser: (_) => [
          DateTime.utc(2026, 7, 1).millisecondsSinceEpoch,
          periodStart + 1,
        ],
        surveysTotal: 5,
        currentUserId: null,
        periodStart: periodStart,
      );

      // Only the August timestamp counts; the July one is filtered.
      expect(entries.first.surveysCompleted, 1);
    });

    test('startOfCurrentMonth is the first millisecond of the month', () {
      final now = DateTime.utc(2026, 8, 24, 14, 30).millisecondsSinceEpoch;
      final start = TeamLeaderboardCalculator.startOfCurrentMonth(now);
      final startDt = DateTime.fromMillisecondsSinceEpoch(start, isUtc: true);
      expect(startDt.year, 2026);
      expect(startDt.month, 8);
      expect(startDt.day, 1);
      expect(startDt.hour, 0);
    });

    test('empty members list returns empty', () {
      final entries = TeamLeaderboardCalculator.build(
        members: const [],
        courseIds: const [],
        progressForUser: (_) => {},
        surveyTimestampsForUser: (_) => const [],
        surveysTotal: 0,
        currentUserId: null,
      );
      expect(entries, isEmpty);
    });
  });
}
