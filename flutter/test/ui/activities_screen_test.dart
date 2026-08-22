import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/ui/dashboard/activities_screen.dart';

OfflineActivityRow _login(int? loginTime) => OfflineActivityRow(
  id: 'login-$loginTime',
  userName: 'ada',
  type: 'login',
  loginTime: loginTime,
);

void main() {
  // Fixed so the year-long window is deterministic.
  final now = DateTime(2026, 8, 12, 12);

  test('buckets logins by calendar month, oldest month first', () {
    final counts = monthlyLoginCounts([
      _login(DateTime(2026, 3, 4).millisecondsSinceEpoch),
      _login(DateTime(2026, 3, 20).millisecondsSinceEpoch),
      _login(DateTime(2026, 7, 1).millisecondsSinceEpoch),
    ], now: now);

    expect(counts, {3: 2, 7: 1});
    expect(counts.keys.toList(), [3, 7]);
  });

  test('drops logins with no timestamp', () {
    final counts = monthlyLoginCounts([
      _login(null),
      _login(DateTime(2026, 7, 1).millisecondsSinceEpoch),
    ], now: now);

    expect(counts, {7: 1});
  });

  test('excludes logins older than the one-year window', () {
    final counts = monthlyLoginCounts([
      // Just inside a year.
      _login(DateTime(2025, 8, 13).millisecondsSinceEpoch),
      // Two years back — outside.
      _login(DateTime(2024, 9, 1).millisecondsSinceEpoch),
    ], now: now);

    expect(counts, {8: 1});
  });

  test('excludes a login stamped in the future', () {
    final counts = monthlyLoginCounts([
      _login(DateTime(2026, 12, 1).millisecondsSinceEpoch),
    ], now: now);

    expect(counts, isEmpty);
  });

  test('groups by month alone, as Calendar.MONTH does', () {
    // The Kotlin buckets on `Calendar.MONTH` with no year component, so two
    // logins in the same month either side of the window boundary collide.
    // Reproduced deliberately — documented on `monthlyLoginCounts`.
    final counts = monthlyLoginCounts([
      _login(DateTime(2025, 8, 13).millisecondsSinceEpoch),
      _login(DateTime(2026, 8, 1).millisecondsSinceEpoch),
    ], now: now);

    expect(counts, {8: 2});
  });

  test(
    'no logins at all yields an empty map, which renders the empty state',
    () {
      expect(monthlyLoginCounts(const [], now: now), isEmpty);
    },
  );
}
