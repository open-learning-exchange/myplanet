import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';

/// Completes the write-back for documents in the `teams` database.
///
/// The enqueue side lives in `providers/teams_provider.dart`, and without a
/// handler the drainer's generic fallback would still POST the payload — but
/// nothing would record the outcome. That matters because a locally-written
/// team document is marked [Teams.isUpdated], which makes it authoritative
/// over the server copy and exempt from stale-row cleanup. Left set forever,
/// the row would freeze at its local version and never be evicted once the
/// document is gone server-side.
///
/// Unlike meetups and voices, the id is not reassigned on upload:
/// `serializeTeamDocument` sends the device-generated `_id`, so CouchDB stores
/// the document under it and the next sync recognises the row as its own.
class TeamsUploader {
  TeamsUploader(this._api, this._dao);

  /// The upload types enqueued by the team providers, all handled the same way.
  static const membershipType = 'teamMembership';
  static const resourceType = 'teamResource';
  static const coursesType = 'teamCourses';
  static const reportsType = 'teamReports';
  static const types = {membershipType, resourceType, coursesType, reportsType};

  final PlanetApi _api;
  final TeamDao _dao;

  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/teams';

  OutboxHandler get handler => (row, payload, authHeader) async {
    final result = await _api.postJsonObject(
      row.endpoint,
      payload,
      authHeader: authHeader,
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      // A tombstone's subject was already deleted locally; there is no row
      // left to stamp, and treating the missing revision as a failure would
      // retry a delete that already succeeded.
      if (payload['_deleted'] == true) return result;
      final rev = data['rev'];
      if (rev is! String) {
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no rev',
        );
      }
      await _dao.markUploaded(row.itemId, rev);
    }
    return result;
  };
}
