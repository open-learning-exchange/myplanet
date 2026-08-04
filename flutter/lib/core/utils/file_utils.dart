import 'package:intl/intl.dart';

/// Formats a file size in bytes to a human-readable string.
///
/// Port of `utils/FileUtils.formatSize` using Android's Formatter.formatFileSize.
String formatFileSize(int bytes) {
  return NumberFormat.compact().format(bytes);
}

/// Formats a file size in bytes to a human-readable string with full precision.
///
/// Similar to Android's Formatter.formatFileSize.
String formatFileSizeFull(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  if (bytes < 1024 * 1024 * 1024) {
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
  return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
}
