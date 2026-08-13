import 'dart:math' as math;

/// Port of `utils/GridSpanCalculator.kt`.
///
/// Calculates how many columns a responsive grid should have based on the
/// available width. The Kotlin uses 165 dp per column, clamped between 2 and 6.
class GridSpanCalculator {
  static const int _minColumns = 2;
  static const int _maxColumns = 6;
  static const double _columnWidthDp = 165;

  static int columnCount(double availableWidthDp) {
    final columns = (availableWidthDp / _columnWidthDp).floor();
    return math.max(_minColumns, math.min(columns, _maxColumns));
  }
}
