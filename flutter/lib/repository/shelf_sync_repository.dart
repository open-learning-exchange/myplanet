import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/sync_result.dart';
import '../core/sync/table_walk.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/course_mapper.dart';
import '../data/local/my_library_mapper.dart';

/// Port of phase 3 of `services/sync/SyncManager.kt` — `myLibraryTransactionSync`
/// → `getShelvesWithDataBatchOptimized` → `SyncRepositoryImpl.processShelfParallel`
/// → `processShelfDataOptimizedSync`.
///
/// A `shelf` document is one user's library: `resourceIds`, `courseIds`,
/// `meetupIds`, `myTeamIds`. The walk fetches every shelf, keeps the ones that
/// list anything, and stamps the shelf's owner onto each listed resource and
/// course — `my_library.userId` and `courses.userId`, which every "my" view in
/// the app reads with `LIKE '%"<uid>"%'`.
///
/// **This is the walk whose absence made those views dead.** Phase 116's D1/D2:
/// with no shelf pull, `my_library.userId` and `courses.userId` could only ever
/// hold what a tap on this device had put there, so a shelf built on Planet web
/// or on another handset never arrived, and `CoursesRepository.sync`'s
/// `shelfId` parameter — threaded end to end — had no production caller.
///
/// **Two deliberate narrowings from the Kotlin**, both recorded in
/// `PHASE_119_NOTES.md`:
///
/// * Only the `resources` and `courses` arms of `Constants.shelfDataList` are
///   walked. The `meetups` and `teams` arms call `batchInsertMeetups(docs)` and
///   `batchInsertMyTeams(docs)` — neither takes the shelf id, so neither
///   records membership; they re-insert documents that the port's own `meetups`
///   and `teams` walks have already pulled in full during the same pass, from
///   the same databases. There is nothing for them to add.
/// * The courses arm writes the course row and its steps but not the exams and
///   surveys embedded in the same document, where `upsertRoomCoursesFromSync`
///   writes all four. The `courses` walk runs earlier in the same pass over the
///   same documents and owns those two tables, including the step-join release
///   whose churn Phase 113 had to fix; a second writer of them adds nothing and
///   risks that.
///
/// **Never prunes.** Kotlin's shelf pass issues no delete, and it must not: the
/// stamp is a union (`MyLibrary.setUserId`, `MyCourse.setUserId`), so syncing
/// one user's shelf leaves another's membership on the same row intact.
class ShelfSyncRepository {
  ShelfSyncRepository(
    this._api,
    this._libraryDao,
    this._courseDao,
    this._userDao,
  );

  /// `getShelvesWithDataBatchOptimized` checks shelves 25 at a time.
  static const int shelfProbeBatchSize = 25;

  /// `processShelfDataOptimizedSync` seeds its `AdaptiveBatchProcessor` at 50.
  static const int itemBatchSize = 50;

  final PlanetApi _api;
  final MyLibraryDao _libraryDao;
  final CourseDao _courseDao;
  final UserDao _userDao;

  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);

    final listing = await _api.getJsonObject(
      '$dbUrl/shelf/_all_docs',
      authHeader: authHeader,
    );
    if (listing is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(listing));
    }

    final shelfIds = <String>[
      for (final row in (listing.data['rows'] as List? ?? const []))
        if (row is Map<String, dynamic>)
          if (JsonUtils.getString('id', row) case final id
              when id.isNotEmpty && !id.startsWith('_design'))
            id,
    ];
    if (shelfIds.isEmpty) {
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final shelvesWithData = <Map<String, dynamic>>[];
    for (final chunk in _chunks(shelfIds, shelfProbeBatchSize)) {
      final page = await _keysPage(
        url: '$dbUrl/shelf/_all_docs?include_docs=true',
        authHeader: authHeader,
        keys: chunk,
      );
      if (page == null) {
        return SyncFailed('Could not read the shelf listing from $dbUrl');
      }
      for (final doc in page) {
        if (hasShelfData(doc)) shelvesWithData.add(doc);
      }
    }

    if (shelvesWithData.isEmpty) {
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    // The Kotlin re-fetches each shelf document in `processShelfParallel`
    // because its "which shelves have data" answer is cached in preferences for
    // six hours and the ids in it may be stale by the time it is reused. This
    // walk has no such cache, so the documents just read are the ones used.
    var processed = 0;
    var completed = 0;
    for (final shelf in shelvesWithData) {
      final shelfId = await _localUserId(JsonUtils.getString('_id', shelf));
      processed += await _pullShelfResources(
        config: config,
        shelfId: shelfId,
        ids: JsonUtils.getStringList('resourceIds', shelf),
      );
      processed += await _pullShelfCourses(
        config: config,
        shelfId: shelfId,
        ids: JsonUtils.getStringList('courseIds', shelf),
      );
      completed++;
      onProgress?.call(
        SyncProgress(completed: completed, total: shelvesWithData.length),
      );
    }

    return SyncComplete(processed);
  }

  /// The id the port's shelf readers actually scope by.
  ///
  /// A shelf document is keyed by the owner's **CouchDB** id
  /// (`org.couchdb.user:<name>`) — that is what `shelf/_all_docs` returns — but
  /// every reader in this app passes `session.user.id`, the local row id:
  /// `MyLibraryDao.watchResources(shelfUserId:)`, `CourseDao.watchCourses`, the
  /// home library and course cards, the completed-course stars. For an account
  /// that first appeared server-side the two are the same string, because
  /// `UserMapper.fromDoc` keys the row on the document id. For a **member
  /// registered on this device** they are not: that row keeps its locally
  /// minted `'<millis>'` id and only gains a `couchId` when the upload lands
  /// (the identity rule at `user_mapper.dart:26-35`). Stamping the raw shelf id
  /// for such a member would write a `userId` no reader can match, and My
  /// Library would stay empty with the walk reporting success.
  ///
  /// [UserDao.getById] resolves either column, so the lookup covers both. An
  /// account this device has never seen falls back to the document id — which
  /// is what a later `tablet_users` pull will key its row on anyway. That is
  /// also why the sync centre runs `tablet_users` **before** `shelf`.
  Future<String> _localUserId(String shelfDocId) async {
    if (shelfDocId.isEmpty) return shelfDocId;
    final user = await _userDao.getById(shelfDocId);
    return user?.id ?? shelfDocId;
  }

  /// Port of `UserRepositoryImpl.hasShelfDataUltraFast`, key set included.
  ///
  /// It reads `teamIds`, while `Constants.shelfDataList` — the list the shelf
  /// is actually *processed* through — reads `myTeamIds`. The two disagree, and
  /// the consequence in the Kotlin is that a shelf listing only teams, under
  /// the key the processing loop uses, is judged to have no data and is skipped
  /// entirely. The key set is kept verbatim rather than "fixed": this walk does
  /// not process either team key (see the class comment), so the only effect of
  /// widening it here would be to spend two requests on a shelf with nothing
  /// for us in it.
  static bool hasShelfData(Map<String, dynamic> shelf) {
    const keys = ['resourceIds', 'courseIds', 'meetupIds', 'teamIds'];
    for (final key in keys) {
      final value = shelf[key];
      if (value is List && value.isNotEmpty) return true;
    }
    return false;
  }

  /// Port of the `"resources"` arm of `processShelfDataOptimizedSync` →
  /// `ResourcesRepositoryImpl.batchInsertMyLibrary`: the shelf's resource ids
  /// are fetched 50 at a time with a `keys` POST and written through the same
  /// mapper the `resources` walk uses, with the shelf's owner added to
  /// `userId`.
  ///
  /// Every list column is passed back as its `existing…` argument. That is not
  /// optional: [MyLibraryMapper.fromDoc] writes each of them unconditionally,
  /// so omitting one replaces the stored list with `const []` — the Phase 116
  /// D1 defect, which emptied My Library on every sync.
  Future<int> _pullShelfResources({
    required ServerConfig config,
    required String shelfId,
    required List<String> ids,
  }) async {
    if (ids.isEmpty) return 0;
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);
    var saved = 0;

    for (final chunk in _chunks(ids, itemBatchSize)) {
      final docs = await _keysPage(
        url: '$dbUrl/resources/_all_docs?include_docs=true',
        authHeader: authHeader,
        keys: chunk,
      );
      // A failed batch is skipped, as `processShelfDataOptimizedSync` does
      // (`if (response == null) { recordFailure(); continue }`). Nothing is
      // pruned here, so a gap costs a missing stamp until the next sync rather
      // than a deletion.
      if (docs == null || docs.isEmpty) continue;

      final existingById = {
        for (final row in await _libraryDao.getByIds([
          for (final doc in docs) JsonUtils.getString('_id', doc),
        ]))
          row.id: row,
      };

      final companions = <MyLibraryTableCompanion>[];
      for (final doc in docs) {
        final existing = existingById[JsonUtils.getString('_id', doc)];
        final companion = MyLibraryMapper.fromDoc(
          doc,
          couchDbUrl: config.couchDbUrl,
          existingUserIds: existing?.userId ?? const [],
          existingResourceFor: existing?.resourceFor ?? const [],
          existingSubject: existing?.subject ?? const [],
          existingLevel: existing?.level ?? const [],
          existingTag: existing?.tag ?? const [],
          existingLanguages: existing?.languages ?? const [],
          shelfId: shelfId,
        );
        if (companion == null) continue;
        companions.add(companion);
      }

      if (companions.isNotEmpty) {
        await _libraryDao.upsertAll(companions);
        saved += companions.length;
      }
    }
    return saved;
  }

  /// Port of the `"courses"` arm — `CoursesRepositoryImpl.batchInsertMyCourses`,
  /// which is `upsertRoomCoursesFromSync(documents, shelfId)`.
  ///
  /// The course row and its steps are written exactly as the `courses` walk
  /// writes them, with the shelf's owner added to `userId`. The embedded exams
  /// and surveys are **not**: the `courses` walk runs earlier in the same pass
  /// over the same documents and owns those tables, including the step-join
  /// release whose churn Phase 113 had to fix, and a second writer of them adds
  /// nothing.
  Future<int> _pullShelfCourses({
    required ServerConfig config,
    required String shelfId,
    required List<String> ids,
  }) async {
    if (ids.isEmpty) return 0;
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);
    var saved = 0;

    for (final chunk in _chunks(ids, itemBatchSize)) {
      final docs = await _keysPage(
        url: '$dbUrl/courses/_all_docs?include_docs=true',
        authHeader: authHeader,
        keys: chunk,
      );
      if (docs == null || docs.isEmpty) continue;

      final existingById = {
        for (final row in await _courseDao.getByIds([
          for (final doc in docs) JsonUtils.getString('_id', doc),
        ]))
          row.id: row,
      };

      final courseRows = <CoursesCompanion>[];
      final stepRows = <CourseStepsCompanion>[];
      for (final doc in docs) {
        final existing = existingById[JsonUtils.getString('_id', doc)];
        final parsed = CourseMapper.fromDoc(
          doc,
          existingUserIds: existing?.userId ?? const [],
          shelfId: shelfId,
        );
        if (parsed == null) continue;
        courseRows.add(parsed.course);
        stepRows.addAll(parsed.steps);
      }

      if (courseRows.isNotEmpty) {
        await _courseDao.upsertAll(courseRows, stepRows);
        saved += courseRows.length;
      }
    }
    return saved;
  }

  /// One `POST …/_all_docs?include_docs=true` with a `keys` body, returning the
  /// documents it carried, or `null` when the request failed.
  Future<List<Map<String, dynamic>>?> _keysPage({
    required String url,
    required String? authHeader,
    required List<String> keys,
  }) async {
    final result = await _api.postJsonObject(url, {
      'keys': keys,
    }, authHeader: authHeader);
    if (result is! NetworkSuccess<Map<String, dynamic>>) return null;
    final rows = result.data['rows'];
    if (rows is! List) return const [];
    return extractDocs(rows);
  }

  static Iterable<List<String>> _chunks(List<String> ids, int size) sync* {
    for (var i = 0; i < ids.length; i += size) {
      yield ids.sublist(i, i + size > ids.length ? ids.length : i + size);
    }
  }
}
