import 'package:flutter_test/flutter_test.dart';

import 'package:myplanet/ui/components/grid_span_calculator.dart';
import 'package:myplanet/ui/components/list_view_mode.dart';

void main() {
  group('GridSpanCalculator', () {
    test('returns minimum 2 columns for narrow widths', () {
      expect(GridSpanCalculator.columnCount(0), 2);
      expect(GridSpanCalculator.columnCount(100), 2);
      expect(GridSpanCalculator.columnCount(164), 2);
    });

    test('returns 2 for exactly one column width', () {
      expect(GridSpanCalculator.columnCount(165), 2);
    });

    test('returns 3 for three column widths', () {
      expect(GridSpanCalculator.columnCount(495), 3);
    });

    test('returns 6 for large widths (clamped to max)', () {
      expect(GridSpanCalculator.columnCount(990), 6);
      expect(GridSpanCalculator.columnCount(2000), 6);
    });

    test('floors fractional widths', () {
      // 329.9 / 165 = 1.999... → floor → 1, clamped to 2
      expect(GridSpanCalculator.columnCount(329.9), 2);
      // 494.9 / 165 = 2.999... → floor → 2, clamped to min 2
      expect(GridSpanCalculator.columnCount(494.9), 2);
    });
  });

  group('ListViewMode', () {
    test('fromPref returns grid for null', () {
      expect(ListViewMode.fromPref(null), ListViewMode.grid);
    });

    test('fromPref returns grid for unrecognized values', () {
      expect(ListViewMode.fromPref(''), ListViewMode.grid);
      expect(ListViewMode.fromPref('unknown'), ListViewMode.grid);
      expect(ListViewMode.fromPref('GRID'), ListViewMode.grid);
    });

    test('fromPref returns list only for "list"', () {
      expect(ListViewMode.fromPref('list'), ListViewMode.list);
    });
  });
}
