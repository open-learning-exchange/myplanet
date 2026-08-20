import 'dart:convert';

/// Port of `utils/JsonUtils.kt`.
///
/// CouchDB documents arrive as loosely-typed maps, and the Kotlin readers all
/// coerce a missing or null field to a zero value rather than throwing. These
/// helpers keep that behaviour so a malformed document degrades one field
/// instead of failing a whole sync page.
class JsonUtils {
  const JsonUtils._();

  /// Port of `JsonUtils.getString` — missing/null becomes `''`.
  static String getString(String key, Map<String, dynamic>? json) {
    final value = json?[key];
    if (value == null) return '';
    return value is String ? value : value.toString();
  }

  static String? getStringOrNull(String key, Map<String, dynamic>? json) {
    final value = getString(key, json);
    return value.isEmpty ? null : value;
  }

  /// Port of `JsonUtils.getLong` — missing/unparseable becomes `0`.
  static int getLong(String key, Map<String, dynamic>? json) {
    final value = json?[key];
    if (value is int) return value;
    if (value is double) return value.toInt();
    if (value is String) return int.tryParse(value) ?? 0;
    return 0;
  }

  /// Port of `JsonUtils.getInt`.
  static int getInt(String key, Map<String, dynamic>? json) =>
      getLong(key, json);

  /// Port of `JsonUtils.getBoolean`.
  static bool getBool(String key, Map<String, dynamic>? json) {
    final value = json?[key];
    if (value is bool) return value;
    if (value is String) return value.toLowerCase() == 'true';
    return false;
  }

  /// Port of `JsonUtils.getJsonArray` — always a list, never null.
  static List<String> getStringList(String key, Map<String, dynamic>? json) {
    final value = json?[key];
    if (value is! List) return const [];
    return value
        .where((e) => e != null)
        .map((e) => e.toString())
        .toList(growable: false);
  }

  static Map<String, dynamic>? getObject(
    String key,
    Map<String, dynamic>? json,
  ) {
    final value = json?[key];
    return value is Map<String, dynamic> ? value : null;
  }

  /// Port of `JsonUtils.extractSharedTeamName` — reads the first entry's
  /// `name` from a news document's `viewIn` JSON array. Returns `''` when the
  /// array is missing, empty, or its first element has no `name`.
  static String extractSharedTeamName(String? viewInJson) {
    if (viewInJson == null || viewInJson.isEmpty) return '';
    List<dynamic>? parsed;
    try {
      parsed = jsonDecode(viewInJson) as List<dynamic>?;
    } catch (_) {
      return '';
    }
    if (parsed == null || parsed.length <= 1) return '';
    final first = parsed.first;
    if (first is! Map<String, dynamic>) return '';
    return getString('name', first);
  }
}
