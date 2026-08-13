import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/activities_provider.dart';

/// Pure-function tests for [bucketLoginsByMonth], the monthly grouping the
/// "My Activity" bar chart renders. The grouping is intentionally
/// month-bucketed and gap-filling (empty months show as 0), mirroring
/// `ActivitiesFragment`'s `Calendar.MONTH` walk from the newest login.
void main() {
  group('bucketLoginsByMonth', () {
    test('returns an empty list when there are no login rows', () {
      expect(bucketLoginsByMonth(const []), isEmpty);
    });

    test('returns an empty list when every row lacks a loginTime', () {
      expect(
        bucketLoginsByMonth([
          _row(loginTime: null),
          _row(loginTime: null),
        ]),
        isEmpty,
      );
    });

    test('produces a bar for every month in the trailing window', () {
      // Two logins in the same calendar month; the chart still draws the full
      // trailing window so the axis is continuous.
      final now = DateTime.now();
      final sameMonth = DateTime(now.year, now.month, 10);
      final rows = [
        _row(loginTime: sameMonth.millisecondsSinceEpoch),
        _row(loginTime: sameMonth.millisecondsSinceEpoch),
      ];

      final counts = bucketLoginsByMonth(rows, monthCount: 12);

      expect(counts, hasLength(12));
      // The newest month is last, and it carries both logins.
      expect(counts.last.count, 2);
      // Every month in the window is present, even empty ones.
      for (final entry in counts) {
        expect(entry.count, greaterThanOrEqualTo(0));
      }
    });

    test('counts logins in separate months separately', () {
      final newest = DateTime(2024, 6, 15);
      final older = DateTime(2024, 5, 1);

      final counts = bucketLoginsByMonth(
        [
          _row(loginTime: newest.millisecondsSinceEpoch),
          _row(loginTime: older.millisecondsSinceEpoch),
        ],
        monthCount: 3,
      );

      expect(counts, hasLength(3));
      expect(counts.last.month, DateTime(2024, 6));
      expect(counts.last.count, 1);
      expect(counts[counts.length - 2].month, DateTime(2024, 5));
      expect(counts[counts.length - 2].count, 1);
      // The month before the oldest login is present but empty.
      expect(counts.first.count, 0);
    });

    test('uses the newest login to anchor the chart window', () {
      // Even if rows arrive out of order, the window ends at the newest login.
      final oldest = DateTime(2024, 1, 1);
      final newest = DateTime(2024, 6, 1);

      final counts = bucketLoginsByMonth(
        [
          _row(loginTime: newest.millisecondsSinceEpoch),
          _row(loginTime: oldest.millisecondsSinceEpoch),
        ],
        monthCount: 6,
      );

      expect(counts.last.month, DateTime(2024, 6));
      expect(counts.first.month, DateTime(2024, 1));
    });
  });
}

/// Builds a minimal [OfflineActivityRow] for the grouping tests — only
/// [OfflineActivityRow.loginTime] is read by `bucketLoginsByMonth`.
OfflineActivityRow _row({int? loginTime}) => OfflineActivityRow(
  id: '',
  type: 'login',
  userName: 'ada',
  loginTime: loginTime,
);
