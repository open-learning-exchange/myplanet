import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'teams_repository.dart';

/// Port of `UploadManager.uploadTeamActivities` — the `team_activities` half
/// of `TeamsSyncRepository.syncTeamActivities`.
///
/// One row per `teamVisit` the user makes to a team's detail screen, queued
/// at write time and drained to `team_activities` on the next sync/app
/// resume. Without this the port logged visits that never left the device.
class TeamLogUploader {
  TeamLogUploader(
    this._api,
    this._repo,
    this._dao,
    this._outbox,
    this._identity,
  );

  static const type = 'teamLog';

  final PlanetApi _api;
  final TeamsRepository _repo;
  final TeamLogDao _dao;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/team_activities';

  /// Serializes a row for upload. Port of
  /// `TeamsRepositoryImpl.serializeTeamActivities`, with the device identity
  /// layered on at queue time (matching how the other uploaders add it).
  static Map<String, dynamic> serialize(
    TeamLogRow row, {
    required DeviceIdentity identity,
  }) {
    return {
      if (row.couchId != null) '_id': row.couchId,
      if (row.rev != null) '_rev': row.rev,
      'user': row.user,
      'type': row.type,
      'createdOn': row.createdOn,
      'parentCode': row.parentCode,
      'teamType': row.teamType,
      'time': row.time,
      'teamId': row.teamId,
      ...identity.documentFields,
    };
  }

  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final rows = await _repo.pendingTeamLogUploads();
    final identity = await _identity.read();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: serialize(row, identity: identity),
        userId: userId,
      );
    }
    return rows.length;
  }

  OutboxHandler get handler => (row, payload, authHeader) async {
    final result = await _api.postJsonObject(
      row.endpoint,
      payload,
      authHeader: authHeader,
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final id = data['id'];
      final rev = data['rev'];
      if (id is! String || rev is! String) {
        return const NetworkError(null, 'Upload response carried no id/rev');
      }
      await _dao.markUploaded(row.itemId, id, rev);
    }
    return result;
  };
}
