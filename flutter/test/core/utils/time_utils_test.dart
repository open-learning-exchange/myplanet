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
}
