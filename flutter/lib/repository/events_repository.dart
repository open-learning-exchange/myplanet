import 'dart:convert';

import 'package:drift/drift.dart';

import '../data/local/app_database.dart';
import '../data/local/meetup_mapper.dart';

/// Port of `repository/EventsRepositoryImpl.kt`.
class EventsRepository {
  EventsRepository(
    this._dao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId = createId ?? _defaultId;

  final MeetupDao _dao;
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
    final joined = row.userId?.isNotEmpty == true;
    if (!joined && (userId == null || userId.isEmpty)) return row;
    await _dao.upsert(
      row.toCompanion(false).copyWith(userId: Value(joined ? '' : userId)),
    );
    return _dao.getByMeetupId(meetupId);
  }

  Future<List<MeetupRow>> pendingUploads() => _dao.pendingUploads();

  Future<void> markUploaded(
    String id,
    String remoteId,
    String remoteRev,
  ) async {
    await _dao.markUploaded(id, remoteId, remoteRev);
  }

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
    'sync': _jsonObject(row.sync),
    'link': _jsonObject(row.link),
  };

  static Map<String, dynamic>? _jsonObject(String? value) {
    if (value == null || value.isEmpty) return null;
    final decoded = jsonDecode(value);
    return decoded is Map<String, dynamic> ? decoded : null;
  }
}
