import 'dart:convert';

import 'package:drift/drift.dart' show Value;

import '../data/local/app_database.dart';

/// Port of `repository/CoursesRepositoryImpl.saveSearchActivity` and
/// `repository/ResourcesRepositoryImpl.saveSearchActivity`, plus
/// `model/SearchActivity.serialize`.
///
/// One row per filtered search the user runs on the courses or resources
/// list. The Kotlin writes the row from `CoursesFragment.onPause` /
/// `ResourcesFragment.onPause` (only when a filter is applied) and
/// `UploadManager.uploadSearchActivity` carries it to the `search_activities`
/// CouchDB database on the next sync. Until this existed the port logged no
/// search analytics at all.
///
/// The filter payload is serialized to a JSON string at write time — the same
/// shape the Kotlin persists via Gson — and parsed back to a JSON object at
/// upload time by [serialize], matching `SearchActivity.serialize`'s
/// `JsonUtils.gson.fromJson(filter, JsonObject::class.java)`.
class SearchActivityRepository {
  SearchActivityRepository(this._dao);

  final SearchActivityDao _dao;

  /// Port of `CoursesRepositoryImpl.saveSearchActivity`.
  ///
  /// [tags] is the list of selected tag ids — `TagEntity.getTagsArray`
  /// serializes the tag's `_id` — the collections dialog's selection; an
  /// empty list is the shape the Kotlin produces when no tags are selected.
  Future<void> saveCourseSearch({
    required String searchText,
    required String userName,
    required String planetCode,
    required String parentCode,
    List<String> tags = const [],
    String? grade,
    String? subject,
  }) async {
    final filter = {
      'tags': tags,
      'doc.gradeLevel': grade ?? '',
      'doc.subjectLevel': subject ?? '',
    };
    await _dao.insert(
      SearchActivitiesCompanion.insert(
        id: _localId(),
        user: Value(userName),
        time: Value(DateTime.now().millisecondsSinceEpoch),
        createdOn: Value(planetCode),
        parentCode: Value(parentCode),
        searchText: Value(searchText),
        type: const Value('courses'),
        filterJson: Value(jsonEncode(filter)),
      ),
    );
  }

  /// Port of `ResourcesRepositoryImpl.saveSearchActivity`.
  Future<void> saveResourceSearch({
    required String userName,
    required String searchText,
    required String planetCode,
    required String parentCode,
    List<String> tags = const [],
    Set<String> subjects = const {},
    Set<String> languages = const {},
    Set<String> levels = const {},
    Set<String> mediums = const {},
  }) async {
    final filter = {
      'tags': tags,
      'subjects': subjects.toList(),
      'language': languages.toList(),
      'level': levels.toList(),
      'mediaType': mediums.toList(),
    };
    await _dao.insert(
      SearchActivitiesCompanion.insert(
        id: _localId(),
        user: Value(userName),
        time: Value(DateTime.now().millisecondsSinceEpoch),
        createdOn: Value(planetCode),
        parentCode: Value(parentCode),
        searchText: Value(searchText),
        type: const Value('resources'),
        filterJson: Value(jsonEncode(filter)),
      ),
    );
  }

  /// Rows whose filtered search has not yet reached `search_activities`.
  Future<List<SearchActivityRow>> pendingUploads() => _dao.pendingUploads();

  /// Records that the document POST landed.
  Future<int> markUploaded(String id, String couchId, String rev) =>
      _dao.markUploaded(id, couchId, rev);

  /// Port of `SearchActivity.serialize`. The device identity fields are
  /// layered on at queue time by [SearchActivityUploader], matching how the
  /// other uploaders add them.
  static Map<String, dynamic> serialize(SearchActivityRow row) {
    final filter = row.filterJson.isEmpty
        ? <String, dynamic>{}
        : jsonDecode(row.filterJson) as Map<String, dynamic>;
    return {
      'text': row.searchText,
      'type': row.type,
      'time': row.time,
      'user': row.user,
      'createdOn': row.createdOn,
      'parentCode': row.parentCode,
      'filter': filter,
    };
  }
}

/// Mints a local key for a new row. The Kotlin uses `UUID.randomUUID()`; the
/// port follows the `microsecondsSinceEpoch` convention the other
/// locally-authored repositories use (`ratings_repository._defaultId`,
/// `activities_provider._generateId`), which is unique on one device without
/// pulling in a uuid dependency.
String _localId() => DateTime.now().microsecondsSinceEpoch.toString();
