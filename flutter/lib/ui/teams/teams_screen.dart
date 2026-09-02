import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../data/local/app_database.dart';
import '../../providers/app_providers.dart';
import '../../data/local/user_mapper.dart';
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
                // `teamsProvider` watches the search text and the type
                // segment, so every keystroke and every segment tap rebuilds
                // it. Without this the list was replaced by a centered
                // spinner once per character — `skipLoadingOnReload`
                // defaults to false and "does not skip loading states if
                // triggered by `Ref.watch`". Kotlin re-filters an in-memory
                // list (`TeamViewModel.applyFilters`) and never shows one.
                skipLoadingOnReload: true,
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
      try {
        // The latch above is committed before this runs, so a null read here
        // dropped the visit for the whole mount with nothing to retry it.
        // `createTeamLog` (`TeamDetailFragment.kt:426-441`) awaits its own
        // `getUserModel()`; the await is inside the `try` because a future
        // can reject where `valueOrNull` could not.
        final user = await ref.read(sessionProvider.future);
        if (user == null || !mounted) return;
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

  /// `setupMyTeamButtons` puts every leave behind a `confirm_exit` Yes/No
  /// dialog (`TeamDetailFragment.kt:291-306`). The port's button called
  /// `leave` straight from `onPressed`, so one mis-tap dropped the
  /// membership and queued its tombstone with nothing to confirm.
  Future<void> _confirmLeave(String teamId) async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        content: Text(l10n.confirmLeaveTeam),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(l10n.no),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.yes),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await ref.read(teamMembershipActionsProvider).leave(teamId);
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
    final currentUser = ref.watch(sessionProvider).valueOrNull;
    final currentUserId = currentUser?.id;
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
              // Keyed on the team document's `_id` alone, as
              // `getTeamMemberStatuses` (`TeamsRepositoryImpl.kt:562-601`)
              // is. The `memberships[team.teamId]` fallback this replaces
              // could never resolve — `teamMembershipsProvider` excludes
              // empty ids and a root team's own `teamId` is null or `''` —
              // and if it ever had, it would have reported a *parent* team's
              // membership for a sub-document.
              final membership = memberships[team.id];
              final hasPendingRequest = requests.any(
                (row) => row.userId == currentUserId,
              );
              final isGuest =
                  currentUser != null && UserMapper.isGuest(currentUser);
              // `buildPages` (`TeamDetailFragment.kt:74-92`): a non-member of
              // a team that is not public gets exactly Plan/Mission and
              // Members — no tasks, calendar, surveys, courses, resources,
              // discussions, finances or reports. Without this the port
              // offered a private team's whole contents to any visitor.
              final canView = membership != null || team.isPublic;
              final isEnterprise = team.type == 'enterprise';
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
                  // `setupNonMyTeamButtons` (:249-283) hides the button
                  // outright for a `guest_` id before wiring join at all.
                  if (membership == null && isGuest)
                    const SizedBox.shrink()
                  else if (membership == null && hasPendingRequest)
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
                  // `TeamDetailFragment.kt:179-186` hides leave once
                  // `getJoinedMemberCount(teamId) <= 1 && isMyTeam`. A null
                  // count has not resolved yet, so the button shows — the
                  // Kotlin check is async too and only hides on arrival.
                  else if (memberCount == null || memberCount > 1)
                    OutlinedButton.icon(
                      // No leader gate: `setupMyTeamButtons` (:285-308)
                      // attaches the handler unconditionally and
                      // `markMembershipsForLeave` has no leader test either.
                      // Kotlin has `isTeamLeader` and pointedly does not use
                      // it here — the Phase 99 shape, again.
                      onPressed: () => _confirmLeave(team.id),
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
                  // Plan/Mission and Members are the two entries Kotlin
                  // shows on both sides of the gate.
                  ListTile(
                    leading: const Icon(Icons.assignment_outlined),
                    title: Text(isEnterprise ? l10n.mission : l10n.plan),
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
                  if (canView) ...[
                    ListTile(
                      leading: const Icon(Icons.folder_copy_outlined),
                      // The same screen under two labels, as
                      // `DocumentsPage`/`ResourcesPage` are
                      // (`TeamPageConfig.kt:63-69`).
                      title: Text(
                        isEnterprise ? l10n.teamDocuments : l10n.teamResources,
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () =>
                          context.push('${Routes.teams}/${team.id}/resources'),
                    ),
                    // `buildPages` swaps `CoursesPage` out for `FinancesPage`
                    // on an enterprise (:84) — an enterprise has no courses.
                    if (!isEnterprise)
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
                    if (isEnterprise)
                      ListTile(
                        leading: const Icon(Icons.assessment_outlined),
                        title: Text(l10n.financialReports),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () =>
                            context.push('${Routes.teams}/${team.id}/reports'),
                      ),
                    // Kotlin gates `FinancesPage` on `isEnterprise`; the
                    // port also offers it to a plain team's members, a
                    // surplus recorded in
                    // `docs/kotlin-to-flutter-migration.md`.
                    if (isEnterprise || membership != null)
                      ListTile(
                        leading: const Icon(
                          Icons.account_balance_wallet_outlined,
                        ),
                        title: Text(l10n.finances),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () =>
                            context.push('${Routes.teams}/${team.id}/finances'),
                      ),
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
