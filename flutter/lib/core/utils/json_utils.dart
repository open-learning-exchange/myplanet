import 'dart:convert';

/// Port of `utils/JsonUtils.kt`.
///
/// CouchDB documents arrive as loosely-typed maps, and the Kotlin readers all
/// coerce a missing or null field to a zero value rather than throwing. These
/// helpers keep that behaviour so a malformed document degrades one field
/// instead of failing a whole sync page.
class JsonUtils {
  const JsonUtils._();

  /// Port of `JsonUtils.getString`. A missing key and an explicit JSON `null`
  /// become `''`, as Kotlin's do (`JsonUtils.kt:65-68`).
  ///
  /// **Everything else diverges, in two ways, and only one of them is
  /// benign.** Kotlin's map overload is
  /// `if (el.isJsonPrimitive && el.asJsonPrimitive.isString) el.asString else ""`,
  /// so it returns `''` for *anything* that is not a string primitive. This
  /// returns `value.toString()`:
  ///
  /// * **A non-string primitive** — `{"year": 2019}` is `''` in Kotlin and
  ///   `'2019'` here. The port's value is the useful one; the Kotlin app
  ///   really does drop the year on a resource whose `year` is a JSON number
  ///   (`MyLibrary.kt:274`). Do not "correct" this toward Kotlin.
  /// * **A Map or List** — `{"parent": {...}}` is `''` in Kotlin and
  ///   `'{_id: exam-1, name: Week 1 quiz}'` here, which is Dart's
  ///   `Map.toString()` and **is not JSON**, so `jsonDecode` throws on it
  ///   wherever it is read back. That has already cost this project a defect,
  ///   recorded at [SubmissionsRepository.upsertDocuments]: the sync-in stored
  ///   that literal for `user`/`parent` until it was changed to `jsonEncode`.
  ///   **Never pass this an object or array key** — use [getObject] and encode
  ///   it, or `jsonEncode` the value yourself.
  ///
  /// An earlier revision of this comment claimed a non-primitive becomes `''`
  /// "as Kotlin's does" and named the primitive case as the only divergence.
  /// Both halves were wrong, in the doc comment added to stop the next of 198
  /// callers being misled.
  static String getString(String key, Map<String, dynamic>? json) {
    final value = json?[key];
    if (value == null) return '';
    return value is String ? value : value.toString();
  }

  /// [getString]'s result, with the empty string folded to null.
  ///
  /// **This helper has no Kotlin counterpart, and folding `''` to null is not
  /// a free convenience — it changes what SQL does with the column.** Kotlin's
  /// sync-in stores `getString`, so a key the server omits is `''` there and
  /// null here, and the two are only interchangeable under a *positive*
  /// predicate: `'' = 'x'` and `NULL = 'x'` both fail to select. Under a
  /// negated one they diverge, because SQL is three-valued —
  /// `NOT (NULL = 'x')` is NULL, and `WHERE NULL` drops the row. `LIKE`, `IS
  /// NOT` (`NULL IS NOT ''` is **true**) and a Dart `?? fallback` all
  /// distinguish them too.
  ///
  /// That is not hypothetical. Phase 151 traced one instance end to end: the
  /// submissions sync-in stored a missing `status` as null, and
  /// `SubmissionDao.countCompletedByUserAndExamId`'s `status != 'pending'`
  /// therefore counted 0 where Kotlin counts 1 — **a course step stayed locked
  /// for a learner Kotlin lets through.** Three earlier phases had already
  /// paid for the same fold one reader at a time
  /// (`SubmissionDao.pendingUploads`' coalesced operands,
  /// `SubmissionsRepository._repairSurveyParentId`, `SurveysRepository`'s
  /// adoption guard), each found as a separate defect.
  ///
  /// So: on a **sync-in or mapper** path, prefer [getString] and store what
  /// Kotlin stores, unless the column's readers are all positive equalities
  /// *and* nothing reads it with a `??` fallback. Reach for this helper where
  /// null genuinely means absent to the code that reads it back.
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
