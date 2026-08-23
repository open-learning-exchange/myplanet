import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../data/local/app_database.dart';
import '../../providers/app_providers.dart';
import '../../providers/teams_provider.dart';
import '../../providers/session_provider.dart';
import '../../providers/sync_state.dart';
import '../router.dart';

/// Port of the catalog portion of `ui/teams/TeamFragment.kt`.
class TeamsScreen extends ConsumerWidget {
  const TeamsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final rows = ref.watch(teamsProvider);
    final sync = ref.watch(teamsSyncProvider);
    final type = ref.watch(teamsTypeProvider);
    final memberships =
        ref.watch(teamMembershipsProvider).valueOrNull ?? const {};
    return Scaffold(
      appBar: AppBar(
        title: Text(type == 'enterprise' ? l10n.enterprises : l10n.teams),
        actions: [
          IconButton(
            tooltip: l10n.syncTeams,
            onPressed: sync is SyncRunning
                ? null
                : () => ref.read(teamsSyncProvider.notifier).sync(),
            icon: sync is SyncRunning
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.sync),
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
            child: SegmentedButton<String>(
              segments: [
                ButtonSegment(
                  value: 'team',
                  label: Text(l10n.teams),
                  icon: const Icon(Icons.groups),
                ),
                ButtonSegment(
                  value: 'enterprise',
                  label: Text(l10n.enterprises),
                  icon: const Icon(Icons.business),
                ),
              ],
              selected: {type},
              onSelectionChanged: (value) =>
                  ref.read(teamsTypeProvider.notifier).state = value.single,
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
            child: SearchBar(
              hintText: type == 'enterprise'
                  ? l10n.searchEnterprises
                  : l10n.searchTeams,
              leading: const Icon(Icons.search),
              onChanged: (value) =>
                  ref.read(teamsSearchProvider.notifier).state = value,
            ),
          ),
          if (sync is SyncRunning)
            LinearProgressIndicator(
              value: sync.progress.total == 0 ? null : sync.progress.fraction,
            ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () => ref.read(teamsSyncProvider.notifier).sync(),
              child: rows.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (_, _) => Center(child: Text(l10n.teamsUnavailable)),
                data: (teams) => teams.isEmpty
                    ? ListView(
                        physics: const AlwaysScrollableScrollPhysics(),
                        children: [
                          SizedBox(
                            height: 300,
                            child: Center(child: Text(l10n.noTeams)),
                          ),
                        ],
                      )
                    : ListView.separated(
                        physics: const AlwaysScrollableScrollPhysics(),
                        padding: const EdgeInsets.all(12),
                        itemCount: teams.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 6),
                        itemBuilder: (context, index) {
                          final team = teams[index];
                          final membership =
                              memberships[team.id] ??
                              (team.teamId == null
                                  ? null
                                  : memberships[team.teamId]);
                          return Card(
                            child: ListTile(
                              leading: Icon(
                                team.isPublic ? Icons.public : Icons.groups,
                              ),
                              title: Text(team.name ?? l10n.untitledTeam),
                              subtitle: Text(team.description ?? ''),
                              trailing: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  if (membership != null)
                                    Chip(
                                      avatar: Icon(
                                        membership.isLeader
                                            ? Icons.star
                                            : Icons.check,
                                        size: 16,
                                      ),
                                      label: Text(
                                        membership.isLeader
                                            ? l10n.leader
                                            : l10n.member,
                                      ),
                                    ),
                                  const Icon(Icons.chevron_right),
                                ],
                              ),
                              onTap: () =>
                                  context.push('${Routes.teams}/${team.id}'),
                            ),
                          );
                        },
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class TeamDetailScreen extends ConsumerStatefulWidget {
  const TeamDetailScreen({required this.teamId, super.key});
  final String teamId;

  @override
  ConsumerState<TeamDetailScreen> createState() => _TeamDetailScreenState();
}

class _TeamDetailScreenState extends ConsumerState<TeamDetailScreen> {
  /// One `team_log` row per mount. `TeamDetailFragment.onViewCreated` fires
  /// `createTeamLog` once per screen creation; a rebuild is not a revisit.
  bool _visitLogged = false;

  void _logVisitOnce(TeamRow team) {
    if (_visitLogged) return;
    _visitLogged = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final user = ref.read(sessionProvider).valueOrNull;
      if (user == null) return;
      try {
        final config = ref.read(serverConfigProvider);
        await ref
            .read(teamsRepositoryProvider)
            .logTeamVisit(
              teamId: widget.teamId,
              userName: user.name,
              userPlanetCode: user.planetCode,
              userParentCode: user.parentCode,
              // `getEffectiveTeamType()` resolves from route args, falling
              // back to the team document; the port has the document, which
              // is the same value once it has loaded.
              teamType: team.type,
            );
        if (config != null) {
          await ref
              .read(teamLogUploaderProvider)
              .queuePending(config: config, userId: user.id);
        }
      } catch (_) {
        // A visit log is telemetry; a failure must not surface on the screen.
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final memberCount = ref
        .watch(teamMemberCountProvider(widget.teamId))
        .valueOrNull;
    final memberships =
        ref.watch(teamMembershipsProvider).valueOrNull ?? const {};
    final requests =
        ref.watch(teamRequestsProvider(widget.teamId)).valueOrNull ?? const [];
    final currentUserId = ref.watch(sessionProvider).valueOrNull?.id;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.teamDetails)),
      body: ref
          .watch(teamProvider(widget.teamId))
          .when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, _) => Center(child: Text(l10n.teamsUnavailable)),
            data: (team) {
              if (team == null) return Center(child: Text(l10n.teamNotFound));
              _logVisitOnce(team);
              final membership =
                  memberships[team.id] ??
                  (team.teamId == null ? null : memberships[team.teamId]);
              final hasPendingRequest = requests.any(
                (row) => row.userId == currentUserId,
              );
              return ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (membership != null)
                    Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: Chip(
                        label: Text(
                          membership.isLeader
                              ? l10n.teamLeader
                              : l10n.teamMember,
                        ),
                      ),
                    ),
                  if (membership == null && hasPendingRequest)
                    Chip(
                      avatar: const Icon(Icons.hourglass_top),
                      label: Text(l10n.requestPending),
                    )
                  else if (membership == null)
                    FilledButton.icon(
                      onPressed: () => ref
                          .read(teamMembershipActionsProvider)
                          .requestToJoin(team),
                      icon: const Icon(Icons.person_add),
                      label: Text(l10n.joinTeam),
                    )
                  else
                    OutlinedButton.icon(
                      onPressed: membership.isLeader
                          ? null
                          : () => ref
                                .read(teamMembershipActionsProvider)
                                .leave(team.id),
                      icon: const Icon(Icons.exit_to_app),
                      label: Text(l10n.leaveTeam),
                    ),
                  Text(
                    team.name ?? l10n.untitledTeam,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 12),
                  Text(team.description ?? ''),
                  if (team.createdBy?.isNotEmpty == true)
                    ListTile(
                      leading: const Icon(Icons.person_outline),
                      title: Text(l10n.createdBy),
                      subtitle: Text(team.createdBy!),
                    ),
                  ListTile(
                    leading: const Icon(Icons.visibility_outlined),
                    title: Text(
                      team.isPublic ? l10n.publicTeam : l10n.privateTeam,
                    ),
                  ),
                  if (memberCount != null)
                    ListTile(
                      leading: const Icon(Icons.people_outline),
                      title: Text(l10n.membersCount(memberCount)),
                    ),
                  if (team.services?.isNotEmpty == true)
                    _DetailSection(title: l10n.services, body: team.services!),
                  if (team.rules?.isNotEmpty == true)
                    _DetailSection(title: l10n.rules, body: team.rules!),
                  ListTile(
                    leading: const Icon(Icons.assignment_outlined),
                    title: Text(
                      team.type == 'enterprise' ? l10n.mission : l10n.plan,
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () =>
                        context.push('${Routes.teams}/${team.id}/plan'),
                  ),
                  if (team.courses.isNotEmpty) ...[
                    const Divider(),
                    Text(
                      l10n.teamCourses,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    ...team.courses.map(
                      (id) => ListTile(
                        leading: const Icon(Icons.school_outlined),
                        title: Text(id),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => context.push('${Routes.courses}/$id'),
                      ),
                    ),
                  ],
                  const Divider(),
                  ListTile(
                    leading: const Icon(Icons.people),
                    title: Text(l10n.teamMembers),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () =>
                        context.push('${Routes.teams}/${team.id}/members'),
                  ),
                  ListTile(
                    leading: const Icon(Icons.folder_copy_outlined),
                    title: Text(l10n.teamResources),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () =>
                        context.push('${Routes.teams}/${team.id}/resources'),
                  ),
                  ListTile(
                    leading: const Icon(Icons.school),
                    title: Text(l10n.teamCourses),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () =>
                        context.push('${Routes.teams}/${team.id}/courses'),
                  ),
                  ListTile(
                    leading: const Icon(Icons.poll_outlined),
                    title: Text(l10n.teamSurveys),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () =>
                        context.push('${Routes.teams}/${team.id}/surveys'),
                  ),
                  if (team.type == 'enterprise')
                    ListTile(
                      leading: const Icon(Icons.assessment_outlined),
                      title: Text(l10n.financialReports),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () =>
                          context.push('${Routes.teams}/${team.id}/reports'),
                    ),
                  if (membership != null)
                    ListTile(
                      leading: const Icon(
                        Icons.account_balance_wallet_outlined,
                      ),
                      title: Text(l10n.finances),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () =>
                          context.push('${Routes.teams}/${team.id}/finances'),
                    ),
                  if (membership != null)
                    ListTile(
                      leading: const Icon(Icons.calendar_month_outlined),
                      title: Text(l10n.teamCalendar),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () =>
                          context.push('${Routes.teams}/${team.id}/calendar'),
                    ),
                  ListTile(
                    leading: const Icon(Icons.task_alt),
                    title: Text(l10n.teamTasks),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () =>
                        context.push('${Routes.teams}/${team.id}/tasks'),
                  ),
                  ListTile(
                    leading: const Icon(Icons.forum_outlined),
                    title: Text(l10n.teamDiscussions),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () =>
                        context.push('${Routes.teams}/${team.id}/voices'),
                  ),
                ],
              );
            },
          ),
    );
  }
}

class _DetailSection extends StatelessWidget {
  const _DetailSection({required this.title, required this.body});
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 4),
        Text(body),
      ],
    ),
  );
}
