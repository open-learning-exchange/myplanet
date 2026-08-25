/// Port of `utils/TimeUtils.kt`.
///
/// Date conversion helpers used by the health profile editor and any screen
/// that round-trips dates through the ISO-8601 form the CouchDB server stores.
class TimeUtils {
  /// Formats an ISO-8601 date string (`yyyy-MM-dd` or
  /// `yyyy-MM-ddTHH:mm:ss.SSSZ`) as `dd-MM-yyyy` for display.
  ///
  /// Port of `TimeUtils.formatDateToDDMMYYYY`. Returns an empty string for
  /// null/blank input, and echoes the input back on any parse failure (the
  /// Kotlin returns the original string).
  static String formatDateToDDMMYYYY(String? dateString) {
    if (dateString == null || dateString.trim().isEmpty) return '';
    try {
      final cleaned = dateString.contains('T')
          ? dateString.replaceFirst('T', ' ').replaceFirst('.000Z', '')
          : dateString;
      final parts = cleaned.split(' ');
      final datePart = parts[0].split('-');
      if (datePart.length != 3) return dateString;
      final year = int.tryParse(datePart[0]);
      final month = int.tryParse(datePart[1]);
      final day = int.tryParse(datePart[2]);
      if (year == null || month == null || day == null) return dateString;
      return '${datePart[2]}-${datePart[1]}-${datePart[0]}';
    } catch (_) {
      return dateString;
    }
  }

  /// Converts a `dd-MM-yyyy` display string back to the ISO-8601 form the
  /// server stores (`yyyy-MM-ddTHH:mm:ss.SSSZ`).
  ///
  /// Port of `TimeUtils.convertDDMMYYYYToISO`. Returns an empty string for
  /// null/blank input, and echoes the input back on any parse failure.
  static String convertDDMMYYYYToISO(String? dateString) {
    if (dateString == null || dateString.trim().isEmpty) return '';
    try {
      final parts = dateString.split('-');
      if (parts.length != 3) return dateString;
      final day = int.tryParse(parts[0]);
      final month = int.tryParse(parts[1]);
      final year = int.tryParse(parts[2]);
      if (day == null || month == null || year == null) return dateString;
      final isoDate = '${parts[2]}-${parts[1]}-${parts[0]}';
      return convertToISO8601(isoDate);
    } catch (_) {
      return dateString;
    }
  }

  /// Wraps a `yyyy-MM-dd` date as a zero-time ISO-8601 timestamp.
  ///
  /// Port of `TimeUtils.convertToISO8601`.
  static String convertToISO8601(String date) {
    try {
      final parts = date.split('-');
      if (parts.length != 3) return date;
      final year = int.tryParse(parts[0]);
      final month = int.tryParse(parts[1]);
      final day = int.tryParse(parts[2]);
      if (year == null || month == null || day == null) return date;
      return '${parts[0]}-${parts[1]}-${parts[2]}T00:00:00.000Z';
    } catch (_) {
      return date;
    }
  }
}
