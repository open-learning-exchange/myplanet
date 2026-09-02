import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../l10n/app_localizations.dart';
import '../../../providers/app_providers.dart';
import '../../../providers/session_provider.dart';
import '../../../providers/teams_provider.dart';
import '../../components/profile_avatar.dart';
import '../../../repository/progress_repository.dart'
    show CourseProgressSummary;
import 'team_leaderboard_calculator.dart';

/// Port of `ui/teams/leaderboard/TeamLeaderboardFragment.kt`. A ranked list
/// of team members by courses completed (then surveys completed), with an
/// all-time / this-month period toggle.
class TeamLeaderboardScreen extends ConsumerStatefulWidget {
  const TeamLeaderboardScreen({required this.teamId, super.key});

  final String teamId;

  @override
  ConsumerState<TeamLeaderboardScreen> createState() =>
      _TeamLeaderboardScreenState();
}

enum _Period { allTime, thisMonth }

class _TeamLeaderboardScreenState extends ConsumerState<TeamLeaderboardScreen> {
  _Period _period = _Period.allTime;
  List<TeamLeaderboardEntry>? _entries;
  bool _loading = false;
  bool _failed = false;

  /// Bumped on every [_load]. A load whose token is stale when it finishes
  /// must not write its result — the period toggle starts a second load
  /// without cancelling the first, and either can complete last.
  int _loadToken = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final token = ++_loadToken;
    setState(() {
      _loading = true;
      _failed = false;
    });
    try {
      // Captured at entry, not read after the awaits. Reading it late meant a
      // stale load recomputed with the *current* period and produced the same
      // answer, which made the `_loadToken` guard below unobservable — and
      // left a real hazard latent: any future work that depends on the period
      // before the last await would have silently used the wrong one.
      await _gather(token, _period);
    } catch (_) {
      // Without this the exception escaped, `_loading` stayed true, and the
      // screen sat on an indefinite spinner with no way out.
      if (mounted && token == _loadToken) {
        setState(() {
          _entries = null;
          _loading = false;
          _failed = true;
        });
      }
    }
  }

  Future<void> _gather(int token, _Period period) async {
    final db = ref.read(appDatabaseProvider);
    final teamsRepo = ref.read(teamsRepositoryProvider);
    final progressRepo = ref.read(progressRepositoryProvider);
    final surveysRepo = ref.read(surveysRepositoryProvider);
    // `sessionProvider` is read here but never watched, and `_load` runs from
    // `initState` — so `.valueOrNull` was null on every load and the current
    // user was never highlighted. Awaiting the future resolves it.
    final currentUser = await ref.read(sessionProvider.future);

    // Get team courses
    final courses = await ref.read(teamCoursesProvider(widget.teamId).future);
    final courseIds = courses.map((c) => c.id).toList();

    // Get team members
    final membersRows = await teamsRepo.watchMembers(widget.teamId).first;
    final memberUserIds = membersRows
        .where((r) => r.userId != null && r.userId!.isNotEmpty)
        .map((r) => r.userId!)
        .toSet();

    // Resolve member display names from the users table
    final members = <MemberWithProgress>[];
    for (final userId in memberUserIds) {
      final user = await db.userDao.getById(userId);
      if (user != null) {
        // `profile_avatar.dart`'s `displayName` is the port's single source
        // for a user's name: it includes the middle name and trims, which the
        // local copy this replaces did neither of.
        final memberName = displayName(user);
        final visits = await teamsRepo.teamVisitsForUsers(widget.teamId, [
          user.name ?? '',
        ]);
        members.add(
          MemberWithProgress(
            userId: userId,
            displayName: memberName,
            visitCount: visits.length,
            userImage: user.userImage,
          ),
        );
      }
    }

    // Pre-compute course progress for each member
    final progressByUser = <String, Map<String, CourseProgressSummary>>{};
    for (final member in members) {
      progressByUser[member.userId] = await progressRepo.courseProgressSummary(
        courseIds,
        member.userId,
      );
    }

    // Get team-owned surveys
    final teamSurveys = await surveysRepo.teamOwnedSurveys(widget.teamId);
    final surveysTotal = teamSurveys.length;

    // Get survey completion timestamps per user
    final submissionDao = db.submissionDao;
    final surveyTimestampsByUser = <String, List<int>>{};
    for (final userId in memberUserIds) {
      final submissions = await submissionDao.getSurveySubmissionsByUser(
        userId,
      );
      surveyTimestampsByUser[userId] = submissions
          .map((s) => s.lastUpdateTime)
          .where((t) => t > 0)
          .toList();
    }

    final periodStart = period == _Period.thisMonth
        ? TeamLeaderboardCalculator.startOfCurrentMonth(
            DateTime.now().millisecondsSinceEpoch,
          )
        : null;

    final entries = TeamLeaderboardCalculator.build(
      members: members,
      courseIds: courseIds,
      progressForUser: (userId) => progressByUser[userId] ?? const {},
      surveyTimestampsForUser: (userId) =>
          surveyTimestampsByUser[userId] ?? const [],
      surveysTotal: surveysTotal,
      currentUserId: currentUser?.id,
      periodStart: periodStart,
    );

    if (mounted && token == _loadToken) {
      setState(() {
        _entries = entries;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.leaderboard)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: SegmentedButton<_Period>(
              segments: [
                ButtonSegment(
                  value: _Period.allTime,
                  label: Text(l10n.allTime),
                ),
                ButtonSegment(
                  value: _Period.thisMonth,
                  label: Text(l10n.thisMonth),
                ),
              ],
              selected: {_period},
              onSelectionChanged: (selection) {
                setState(() => _period = selection.first);
                _load();
              },
            ),
          ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _failed
                ? Center(child: Text(l10n.leaderboardUnavailable))
                : _entries == null || _entries!.isEmpty
                ? Center(child: Text(l10n.noDataAvailable))
                : ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: _entries!.length,
                    itemBuilder: (context, index) => _LeaderboardCard(
                      entry: _entries![index],
                      rank: index + 1,
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}

class _LeaderboardCard extends StatelessWidget {
  const _LeaderboardCard({required this.entry, required this.rank});

  final TeamLeaderboardEntry entry;
  final int rank;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final medal = switch (rank) {
      1 => '🥇',
      2 => '🥈',
      3 => '🥉',
      _ => '',
    };

    return Card(
      color: entry.isCurrentUser ? theme.colorScheme.primaryContainer : null,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            if (medal.isNotEmpty)
              Text(medal, style: const TextStyle(fontSize: 24))
            else
              SizedBox(
                width: 28,
                child: Text(
                  '$rank',
                  style: theme.textTheme.titleMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    entry.displayName,
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: entry.isCurrentUser ? FontWeight.bold : null,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Wrap(
                    spacing: 16,
                    children: [
                      _Stat(
                        icon: Icons.school_outlined,
                        label: l10n.coursesCompleted(
                          entry.coursesCompleted,
                          entry.coursesTotal,
                        ),
                      ),
                      _Stat(
                        icon: Icons.assignment_outlined,
                        label: l10n.surveysCompleted(
                          entry.surveysCompleted,
                          entry.surveysTotal,
                        ),
                      ),
                      if (entry.visitCount > 0)
                        _Stat(
                          icon: Icons.visibility_outlined,
                          // `numberOfVisits` is a bare label — it is right in
                          // `member_detail_screen`, where a value sits beside
                          // it, but here it was the whole stat and the count
                          // never reached the screen.
                          label: l10n.visitsCount(entry.visitCount),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.icon, required this.label});
  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16, color: theme.colorScheme.onSurfaceVariant),
        const SizedBox(width: 4),
        Text(
          label,
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}
