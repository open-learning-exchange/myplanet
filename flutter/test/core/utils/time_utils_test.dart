import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/utils/time_utils.dart';

void main() {
  group('TimeUtils.formatDateToDDMMYYYY', () {
    test('converts ISO date string to dd-MM-yyyy', () {
      expect(TimeUtils.formatDateToDDMMYYYY('1990-06-15'), '15-06-1990');
    });

    test('converts ISO-8601 timestamp to dd-MM-yyyy', () {
      expect(
        TimeUtils.formatDateToDDMMYYYY('1990-06-15T00:00:00.000Z'),
        '15-06-1990',
      );
    });

    test('returns empty string for null input', () {
      expect(TimeUtils.formatDateToDDMMYYYY(null), '');
    });

    test('returns empty string for blank input', () {
      expect(TimeUtils.formatDateToDDMMYYYY('  '), '');
    });

    test('echoes back unparseable input', () {
      expect(TimeUtils.formatDateToDDMMYYYY('not-a-date'), 'not-a-date');
    });
  });

  group('TimeUtils.convertDDMMYYYYToISO', () {
    test('converts dd-MM-yyyy to ISO-8601', () {
      expect(
        TimeUtils.convertDDMMYYYYToISO('15-06-1990'),
        '1990-06-15T00:00:00.000Z',
      );
    });

    test('returns empty string for null input', () {
      expect(TimeUtils.convertDDMMYYYYToISO(null), '');
    });

    test('returns empty string for blank input', () {
      expect(TimeUtils.convertDDMMYYYYToISO(''), '');
    });

    test('echoes back unparseable input', () {
      expect(TimeUtils.convertDDMMYYYYToISO('not-a-date'), 'not-a-date');
    });
  });

  group('TimeUtils.convertToISO8601', () {
    test('wraps yyyy-MM-dd as zero-time timestamp', () {
      expect(
        TimeUtils.convertToISO8601('1990-06-15'),
        '1990-06-15T00:00:00.000Z',
      );
    });

    test('echoes back input without three dash-parts', () {
      expect(TimeUtils.convertToISO8601('1990-06'), '1990-06');
    });
  });

  group('TimeUtils.getAge', () {
    test('counts whole years and rounds down before the birthday', () {
      final today = DateTime.now();
      final tenYesterday = DateTime(
        today.year - 10,
        today.month,
        today.day,
      ).subtract(const Duration(days: 1));
      expect(TimeUtils.getAge(_iso(tenYesterday)), 10);

      final tenTomorrow = DateTime(
        today.year - 10,
        today.month,
        today.day,
      ).add(const Duration(days: 1));
      expect(TimeUtils.getAge(_iso(tenTomorrow)), 9);
    });

    test('reads the CouchDB timestamp shape and refuses the rest', () {
      final today = DateTime.now();
      expect(
        TimeUtils.getAge('${today.year - 30}-01-01T00:00:00.000Z'),
        TimeUtils.getAge('${today.year - 30}-01-01'),
      );
      expect(TimeUtils.getAge(null), 0);
      expect(TimeUtils.getAge(''), 0);
      expect(TimeUtils.getAge('  '), 0);
      expect(TimeUtils.getAge('not a date'), 0);
    });

    test('a future date of birth truncates toward zero', () {
      // `Period.between(dob, today).years` truncates toward zero, so a date
      // three years and nine months away is -3, not -4. Only reachable for a
      // synced profile — `AddHealthActivity` caps its picker at today — but it
      // is the `age` an examination uploads.
      final today = DateTime.now();
      final future = DateTime(
        today.year + 4,
        today.month,
        today.day,
      ).subtract(const Duration(days: 1));
      expect(TimeUtils.getAge(_iso(future)), -3);
    });

    test('only the two shapes the Kotlin formatters parse are read', () {
      // `DateTime.tryParse` takes both of these; `ofPattern("yyyy-MM-dd")`
      // and `"yyyy-MM-dd HH:mm:ss"` take neither, so the Kotlin returns 0.
      expect(TimeUtils.getAge('19900615'), 0);
      expect(TimeUtils.getAge('1990-06-15T10:00:00.123Z'), 0);
      // The shape the app does write, with and without the time part.
      expect(TimeUtils.getAge('1990-06-15 00:00:00'), greaterThan(0));
    });
  });
}

String _iso(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-'
    '${date.month.toString().padLeft(2, '0')}-'
    '${date.day.toString().padLeft(2, '0')}';
