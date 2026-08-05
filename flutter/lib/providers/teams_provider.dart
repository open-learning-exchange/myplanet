import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'sync_state.dart';
import 'session_provider.dart';
import '../core/utils/url_utils.dart';
import '../repository/teams_repository.dart';

final teamsSearchProvider = StateProvider<String>((ref) => '');
final teamsTypeProvider = StateProvider<String>((ref) => 'team');

final teamMembershipsProvider = StreamProvider<Map<String, TeamRow>>((ref) {
  final userId = ref.watch(sessionProvider).valueOrNull?.id;
  if (userId == null || userId.isEmpty) return Stream.value(const {});
  return ref
      .watch(teamsRepositoryProvider)
      .watchMemberships(userId)
      .map(
        (rows) => {
          for (final row in rows)
            if (row.teamId?.isNotEmpty == true) row.teamId!: row,
        },
      );
});

final teamMemberCountProvider = StreamProvider.family<int, String>(
  (ref, teamId) => ref.watch(teamsRepositoryProvider).watchMemberCount(teamId),
);

final teamMembersProvider = StreamProvider.family<List<TeamRow>, String>(
  (ref, teamId) => ref.watch(teamsRepositoryProvider).watchMembers(teamId),
);
final teamRequestsProvider = StreamProvider.family<List<TeamRow>, String>(
  (ref, teamId) => ref.watch(teamsRepositoryProvider).watchRequests(teamId),
);
final teamResourceLinksProvider = StreamProvider.family<List<TeamRow>, String>(
  (ref, teamId) =>
      ref.watch(teamsRepositoryProvider).watchResourceLinks(teamId),
);
final teamResourcesProvider = StreamProvider.family<List<MyLibraryRow>, String>(
  (ref, teamId) async* {
    await for (final links
        in ref.watch(teamsRepositoryProvider).watchResourceLinks(teamId)) {
      final ids = links
          .map((row) => row.resourceId)
          .whereType<String>()
          .toList();
      yield ids.isEmpty
          ? const []
          : await ref.watch(myLibraryDaoProvider).getByIds(ids);
    }
  },
);
final teamCoursesProvider = FutureProvider.family<List<CourseRow>, String>((
  ref,
  teamId,
) async {
  final team = await ref.watch(teamsRepositoryProvider).getById(teamId);
  return team == null || team.courses.isEmpty
      ? const []
      : ref.watch(courseDaoProvider).getByIds(team.courses);
});
final teamReportsProvider = StreamProvider.family<List<TeamRow>, String>(
  (ref, teamId) => ref.watch(teamsRepositoryProvider).watchReports(teamId),
);

/// A financial transaction with computed balance.
class TransactionRow {
  final TeamRow row;
  final int balance;
  TransactionRow({required this.row, required this.balance});
}

final teamTransactionsProvider =
    StreamProvider.family<
      List<TransactionRow>,
      ({String teamId, int? startDate, int? endDate, bool ascending})
    >((ref, params) {
      final repo = ref.watch(teamsRepositoryProvider);
      return repo
          .watchTransactions(
            params.teamId,
            startDate: params.startDate,
            endDate: params.endDate,
            ascending: params.ascending,
          )
          .map((rows) {
            var balance = 0;
            final result = <TransactionRow>[];
            for (final row in params.ascending ? rows : rows) {
              if (row.type?.toLowerCase() == 'debit') {
                balance -= row.amount;
              } else {
                balance += row.amount;
              }
              result.add(TransactionRow(row: row, balance: balance));
            }
            return params.ascending ? result : result.reversed.toList();
          });
    });

class TeamFinancesActions {
  TeamFinancesActions(this.ref);
  final Ref ref;

  Future<bool> createTransaction({
    required String teamId,
    required String type,
    required String note,
    required int amount,
    required int date,
  }) async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return false;
    final ok = await ref
        .read(teamsRepositoryProvider)
        .createTransaction(
          teamId: teamId,
          type: type,
          note: note,
          amount: amount,
          date: date,
        );
    if (!ok) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamFinances',
          itemId: teamId,
          endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
          payload: {
            'teamId': teamId,
            'type': type,
            'note': note,
            'amount': amount,
            'date': date,
          },
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
    return true;
  }
}

final teamFinancesActionsProvider = Provider<TeamFinancesActions>(
  TeamFinancesActions.new,
);

class TeamMembershipActions {
  TeamMembershipActions(this.ref);
  final Ref ref;

  String? get _endpoint {
    final config = ref.read(serverConfigProvider);
    return config == null
        ? null
        : '${UrlUtils.credentialFreeDbUrl(config)}/teams';
  }

  Future<bool> requestToJoin(TeamRow team) async {
    final user = ref.read(sessionProvider).valueOrNull;
    final endpoint = _endpoint;
    if (user == null || endpoint == null) return false;
    final row = await ref
        .read(teamsRepositoryProvider)
        .createJoinRequest(
          teamId: team.id,
          userId: user.id,
          teamType: team.teamType,
          planetCode: user.planetCode,
        );
    if (row == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamMembership',
          itemId: row.id,
          endpoint: endpoint,
          payload: TeamsRepository.serializeTeamDocument(row),
          userId: user.id,
        );
    return true;
  }

  Future<bool> leave(String teamId) async {
    final user = ref.read(sessionProvider).valueOrNull;
    final endpoint = _endpoint;
    if (user == null || endpoint == null) return false;
    final row = await ref.read(teamsRepositoryProvider).leave(teamId, user.id);
    if (row == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamMembership',
          itemId: row.id,
          endpoint: endpoint,
          payload: {'_id': row.id, '_rev': row.rev, '_deleted': true},
          userId: user.id,
        );
    return true;
  }

  Future<bool> respond(String requestId, {required bool accept}) async {
    final endpoint = _endpoint;
    if (endpoint == null) return false;
    final original = await ref.read(teamsRepositoryProvider).getById(requestId);
    if (original == null) return false;
    final updated = await ref
        .read(teamsRepositoryProvider)
        .respondToRequest(requestId, accept: accept);
    if (updated == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamMembership',
          itemId: original.id,
          endpoint: endpoint,
          payload: accept
              ? TeamsRepository.serializeTeamDocument(updated)
              : {'_id': original.id, '_rev': original.rev, '_deleted': true},
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
    return true;
  }
}

class TeamResourceActions {
  TeamResourceActions(this.ref);
  final Ref ref;

  Future<bool> add(String teamId, MyLibraryRow resource) async {
    final config = ref.read(serverConfigProvider);
    if (config == null || resource.resourceId?.isNotEmpty != true) return false;
    final row = await ref
        .read(teamsRepositoryProvider)
        .addResourceLink(
          teamId: teamId,
          resourceId: resource.resourceId!,
          title: resource.title ?? '',
          planetCode: ref.read(sessionProvider).valueOrNull?.planetCode,
        );
    if (row == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamResource',
          itemId: row.id,
          endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
          payload: TeamsRepository.serializeTeamDocument(row),
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
    return true;
  }

  Future<bool> remove(String teamId, MyLibraryRow resource) async {
    final config = ref.read(serverConfigProvider);
    if (config == null || resource.resourceId == null) return false;
    final row = await ref
        .read(teamsRepositoryProvider)
        .removeResourceLink(teamId, resource.resourceId!);
    if (row == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamResource',
          itemId: row.id,
          endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
          payload: {'_id': row.id, '_rev': row.rev, '_deleted': true},
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
    return true;
  }
}

final teamResourceActionsProvider = Provider<TeamResourceActions>(
  TeamResourceActions.new,
);

class TeamCourseActions {
  TeamCourseActions(this.ref);
  final Ref ref;

  Future<bool> _queue(TeamRow? team) async {
    final config = ref.read(serverConfigProvider);
    if (team == null || config == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamCourses',
          itemId: team.id,
          endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
          payload: TeamsRepository.serializeTeamDocument(team),
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
    ref.invalidate(teamProvider(team.id));
    ref.invalidate(teamCoursesProvider(team.id));
    return true;
  }

  Future<bool> add(String teamId, Iterable<String> courseIds) async => _queue(
    await ref.read(teamsRepositoryProvider).addCourses(teamId, courseIds),
  );

  Future<bool> remove(String teamId, String courseId) async => _queue(
    await ref.read(teamsRepositoryProvider).removeCourse(teamId, courseId),
  );
}

final teamCourseActionsProvider = Provider<TeamCourseActions>(
  TeamCourseActions.new,
);

class TeamReportActions {
  TeamReportActions(this.ref);
  final Ref ref;

  Future<bool> _queue(TeamRow? report) async {
    final config = ref.read(serverConfigProvider);
    if (report == null || config == null) return false;
    await ref
        .read(outboxRepositoryProvider)
        .enqueue(
          uploadType: 'teamReports',
          itemId: report.id,
          endpoint: '${UrlUtils.credentialFreeDbUrl(config)}/teams',
          payload: TeamsRepository.serializeTeamDocument(report),
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
    return true;
  }

  Future<bool> save({
    String? id,
    required String teamId,
    required String description,
    required int startDate,
    required int endDate,
    required int beginningBalance,
    required int sales,
    required int otherIncome,
    required int wages,
    required int otherExpenses,
  }) async => _queue(
    await ref
        .read(teamsRepositoryProvider)
        .saveReport(
          id: id,
          teamId: teamId,
          description: description,
          startDate: startDate,
          endDate: endDate,
          beginningBalance: beginningBalance,
          sales: sales,
          otherIncome: otherIncome,
          wages: wages,
          otherExpenses: otherExpenses,
        ),
  );

  Future<bool> archive(String id) async =>
      _queue(await ref.read(teamsRepositoryProvider).archiveReport(id));
}

final teamReportActionsProvider = Provider<TeamReportActions>(
  TeamReportActions.new,
);

final teamMembershipActionsProvider = Provider<TeamMembershipActions>(
  TeamMembershipActions.new,
);

final teamsProvider = StreamProvider<List<TeamRow>>((ref) async* {
  final search = ref.watch(teamsSearchProvider).trim().toLowerCase();
  final type = ref.watch(teamsTypeProvider);
  await for (final rows
      in ref.watch(teamsRepositoryProvider).watchCatalog(type: type)) {
    yield rows
        .where(
          (row) =>
              search.isEmpty ||
              (row.name ?? '').toLowerCase().contains(search) ||
              (row.description ?? '').toLowerCase().contains(search),
        )
        .toList();
  }
});

final teamProvider = FutureProvider.family<TeamRow?, String>(
  (ref, id) => ref.watch(teamsRepositoryProvider).getById(id),
);

class TeamsSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(config, void Function(SyncProgress) onProgress) =>
      ref
          .read(teamsRepositoryProvider)
          .sync(config: config, onProgress: onProgress);
}

final teamsSyncProvider = NotifierProvider<TeamsSyncNotifier, SyncUiState>(
  TeamsSyncNotifier.new,
);
