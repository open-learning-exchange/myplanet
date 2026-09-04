import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'team_tasks_repository.dart';

class TeamTasksUploader {
  TeamTasksUploader(this._api, this._tasks, this._outbox);
  static const type = 'teamTasks';
  final PlanetApi _api;
  final TeamTasksRepository _tasks;
  final OutboxRepository _outbox;

  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/tasks';

  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
    String? planetCode,
  }) async {
    final rows = await _tasks.pending();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: TeamTasksRepository.serialize(row, planetCode: planetCode),
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
      await _tasks.markUploaded(row.itemId, id, rev);
    }
    return result;
  };
}
