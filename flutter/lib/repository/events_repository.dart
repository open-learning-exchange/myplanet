import 'dart:convert';

import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/meetup_mapper.dart';

/// Port of `repository/EventsRepositoryImpl.kt`.
class EventsRepository {
  EventsRepository(
    this._api,
    this._dao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId = createId ?? _defaultId;

  final MeetupDao _dao;
  final PlanetApi _api;
  final DateTime Function() _now;
  final String Function() _createId;

  static String _defaultId() =>
      'meetup-${DateTime.now().microsecondsSinceEpoch}';

  Stream<List<MeetupRow>> watchAll() => _dao.watchAll();

  Stream<List<MeetupRow>> watchForTeam(String teamId) =>
      _dao.watchForTeam(teamId);

  Future<MeetupRow?> getById(String id) =>
      id.trim().isEmpty ? Future.value(null) : _dao.getById(id);

  Future<MeetupRow?> getByMeetupId(String id) =>
      id.trim().isEmpty ? Future.value(null) : _dao.getByMeetupId(id);

  /// Server refreshes never overwrite a locally edited meetup.
  Future<int> cacheDocuments(List<Map<String, dynamic>> documents) async {
    if (documents.isEmpty) return 0;
    final ids = documents
        .map((doc) => doc['_id']?.toString() ?? '')
        .where((id) => id.isNotEmpty)
        .toList(growable: false);
    final existing = {
      for (final row in await _dao.getByMeetupIds(ids)) row.meetupId: row,
    };
    final rows = <MeetupsCompanion>[];
    for (final doc in documents) {
      final current = existing[doc['_id']?.toString()];
      if (current?.updated == true) continue;
      final mapped = MeetupMapper.fromDoc(doc, existing: current);
      if (mapped != null) rows.add(mapped);
    }
    await _dao.upsertAll(rows);
    return documents.length;
  }

  Future<bool> update(
    String id, {
    required String title,
    required String description,
    required int startDate,
    required int endDate,
    required String startTime,
    required String endTime,
    required String location,
    required String link,
    required String recurring,
  }) async {
    final row = await _dao.getById(id);
    if (row == null || title.trim().isEmpty) return false;
    await _dao.upsert(
      row
          .toCompanion(false)
          .copyWith(
            title: Value(title.trim()),
            description: Value(description.trim()),
            startDate: Value(startDate),
            endDate: Value(endDate),
            startTime: Value(startTime),
            endTime: Value(endTime),
            meetupLocation: Value(location.trim()),
            meetupLink: Value(link.trim()),
            recurring: Value(recurring),
            updated: const Value(true),
          ),
    );
    return true;
  }

  Future<String?> create({
    required String title,
    required String description,
    required int startDate,
    required int endDate,
    required String startTime,
    required String endTime,
    required String location,
    required String link,
    required String recurring,
    required String creator,
    String? teamId,
    String? sourcePlanet,
  }) async {
    final cleanTitle = title.trim();
    if (cleanTitle.isEmpty) return null;
    final id = _createId();
    await _dao.upsert(
      MeetupsCompanion.insert(
        id: id,
        title: Value(cleanTitle),
        description: Value(description.trim()),
        startDate: Value(startDate),
        endDate: Value(endDate),
        startTime: Value(startTime),
        endTime: Value(endTime),
        meetupLocation: Value(location.trim()),
        meetupLink: Value(link.trim()),
        recurring: Value(recurring),
        creator: Value(creator),
        teamId: Value(teamId),
        link: Value(teamId == null ? null : jsonEncode({'teams': teamId})),
        createdDate: Value(_now().millisecondsSinceEpoch),
        sourcePlanet: Value(sourcePlanet),
        sync: Value(
          sourcePlanet == null
              ? null
              : jsonEncode({'type': 'local', 'planetCode': sourcePlanet}),
        ),
        updated: const Value(true),
      ),
    );
    return id;
  }

  Future<MeetupRow?> toggleAttendance(String meetupId, String? userId) async {
    final row = await _dao.getByMeetupId(meetupId);
    if (row == null) return null;
    // No signed-in user, no write in either direction — the Kotlin's old
    // guard allowed a joined meetup to be *left* with no current user
    // (writing userId = ''), which 2a49db978 closed. Bail out unchanged.
    if (userId == null || userId.isEmpty) return row;
    final joined = row.userId?.isNotEmpty == true;
    await _dao.upsert(
      row.toCompanion(false).copyWith(userId: Value(joined ? '' : userId)),
    );
    return _dao.getByMeetupId(meetupId);
  }

  Future<List<MeetupRow>> pendingUploads() => _dao.pendingUploads();

  Future<int> localCount() => _dao.count();

  /// Pulls the CouchDB meetups database page-by-page while retaining local
  /// edits. Cleanup only runs after a complete walk, matching the other sync
  /// repositories' protection against a server changing mid-page.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final auth = UrlUtils.authHeader(config);
    final countResult = await _api.getJsonObject(
      '$dbUrl/meetups/_all_docs?limit=0',
      authHeader: auth,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }
    final total = JsonUtils.getInt('total_rows', countResult.data);
    if (total == 0) {
      await _dao.deleteNotIn(const []);
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final batchSizer = AdaptiveBatchProcessor(initialSize: 100);
    final ids = <String>[];
    var skip = 0;
    var completeWalk = true;
    while (skip < total) {
      final size = batchSizer.currentSize;
      final timer = Stopwatch()..start();
      final result = await _api.getJsonObject(
        '$dbUrl/meetups/_all_docs?include_docs=true&limit=$size&skip=$skip',
        authHeader: auth,
      );
      timer.stop();
      if (result is! NetworkSuccess<Map<String, dynamic>>) {
        batchSizer.recordFailure();
        return SyncFailed(describeNetworkFailure(result));
      }
      batchSizer.recordSuccess(timer.elapsedMilliseconds);
      final rows = result.data['rows'];
      if (rows is! List || rows.isEmpty) {
        completeWalk = false;
        break;
      }
      final documents = rows
          .whereType<Map<String, dynamic>>()
          .map((row) => JsonUtils.getObject('doc', row))
          .whereType<Map<String, dynamic>>()
          .toList(growable: false);
      await cacheDocuments(documents);
      ids.addAll(
        documents
            .map((doc) => JsonUtils.getString('_id', doc))
            .where((id) => id.isNotEmpty && !id.startsWith('_design/')),
      );
      skip += rows.length;
      onProgress?.call(
        SyncProgress(completed: skip.clamp(0, total), total: total),
      );
    }
    if (completeWalk) await _dao.deleteNotIn(ids);
    return SyncComplete(ids.length);
  }

  Future<void> markUploaded(
    String id,
    String remoteId,
    String remoteRev,
  ) async {
    await _dao.markUploaded(id, remoteId, remoteRev);
  }

  /// Port of `Meetup.serialize`, field for field.
  ///
  /// Two shapes are copied from the Kotlin deliberately rather than
  /// "corrected", because both apps write the same CouchDB database and a
  /// document only one of them can read is worse than an odd one both can:
  ///
  /// - `sync` is sent as the raw JSON *string* the column holds, which is what
  ///   `addProperty("sync", meetup.sync)` produces — a double-encoded value.
  ///   Neither app reads it back; only the server sees it.
  /// - `link` is omitted entirely when empty rather than sent as null, matching
  ///   the `if (!meetup.link.isNullOrEmpty())` guard.
  static Map<String, dynamic> serialize(MeetupRow row) => {
    if (row.meetupId?.isNotEmpty == true) '_id': row.meetupId,
    if (row.meetupIdRev?.isNotEmpty == true) '_rev': row.meetupIdRev,
    'title': row.title,
    'description': row.description,
    'startDate': row.startDate,
    'endDate': row.endDate,
    'startTime': row.startTime,
    'endTime': row.endTime,
    'recurring': row.recurring,
    'meetupLocation': row.meetupLocation,
    'meetupLink': row.meetupLink,
    'createdBy': row.creator,
    'teamId': row.teamId,
    'category': row.category,
    'createdDate': row.createdDate,
    'recurringNumber': row.recurringNumber,
    'sourcePlanet': row.sourcePlanet,
    'sync': row.sync,
    if (row.link?.isNotEmpty == true) 'link': _jsonObject(row.link),
  };

  static Map<String, dynamic>? _jsonObject(String? value) {
    if (value == null || value.isEmpty) return null;
    final decoded = jsonDecode(value);
    return decoded is Map<String, dynamic> ? decoded : null;
  }
}
