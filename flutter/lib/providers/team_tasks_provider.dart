import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'session_provider.dart';
import '../repository/team_tasks_uploader.dart';

final teamTasksProvider = StreamProvider.family<List<TeamTaskRow>, String>(
  (ref, teamId) => ref.watch(teamTasksRepositoryProvider).watchForTeam(teamId),
);

class TeamTaskActions {
  TeamTaskActions(this.ref);
  final Ref ref;

  Future<bool> save({
    String? id,
    required String teamId,
    required String title,
    required String description,
    required int deadline,
    String? assignee,
  }) async {
    final repository = ref.read(teamTasksRepositoryProvider);
    final ok = id == null
        ? await repository.create(
                teamId: teamId,
                title: title,
                description: description,
                deadline: deadline,
                assignee: assignee,
              ) !=
              null
        : await repository.update(
            id,
            title: title,
            description: description,
            deadline: deadline,
            assignee: assignee,
          );
    if (ok) await queuePending();
    return ok;
  }

  Future<void> complete(String id, bool value) async {
    if (await ref.read(teamTasksRepositoryProvider).setCompleted(id, value)) {
      await queuePending();
    }
  }

  Future<void> delete(String id) async {
    final repository = ref.read(teamTasksRepositoryProvider);
    final row = await repository.getById(id);
    if (row == null) return;
    await ref.read(outboxRepositoryProvider).cancel(TeamTasksUploader.type, id);
    final config = ref.read(serverConfigProvider);
    if (config != null && row.docId?.isNotEmpty == true) {
      await ref
          .read(outboxRepositoryProvider)
          .enqueue(
            uploadType: TeamTasksUploader.type,
            itemId: id,
            endpoint: TeamTasksUploader.endpointFor(config),
            payload: {'_id': row.docId, '_rev': row.rev, '_deleted': true},
            userId: ref.read(sessionProvider).valueOrNull?.id,
          );
    }
    await repository.delete(id);
  }

  Future<int> queuePending() async {
    final config = ref.read(serverConfigProvider);
    final user = ref.read(sessionProvider).valueOrNull;
    if (config == null) return 0;
    return ref
        .read(teamTasksUploaderProvider)
        .queuePending(
          config: config,
          userId: user?.id,
          planetCode: user?.planetCode,
        );
  }
}

final teamTaskActionsProvider = Provider<TeamTaskActions>(TeamTaskActions.new);
