import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/teams_provider.dart';
import '../../providers/voices_provider.dart';
import '../router.dart';
import '../voices/voice_composer.dart';
import '../voices/voices_screen.dart';

/// Port of `ui/teams/voices/TeamsVoicesFragment.kt`.
///
/// Shows team-specific discussion posts and allows composing new ones.
/// Filtered by team ID in the `viewIn` field; new posts are addressed
/// to the team.
class TeamVoicesScreen extends ConsumerWidget {
  const TeamVoicesScreen({required this.teamId, super.key});

  final String teamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final team = ref.watch(teamProvider(teamId)).valueOrNull;
    final voices = ref.watch(teamVoicesProvider(teamId));
    final memberships =
        ref.watch(teamMembershipsProvider).valueOrNull ?? const {};
    final membership = memberships[teamId];

    return Scaffold(
      appBar: AppBar(title: Text(l10n.teamDiscussions)),
      floatingActionButton: membership != null
          ? FloatingActionButton.extended(
              onPressed: () => _compose(context, ref, team),
              icon: const Icon(Icons.campaign_outlined),
              label: Text(l10n.shareYourVoice),
            )
          : null,
      body: voices.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.voicesUnavailable)),
        data: (rows) => rows.isEmpty
            ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(32),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.forum_outlined, size: 56),
                      const SizedBox(height: 12),
                      Text(l10n.noVoices, textAlign: TextAlign.center),
                    ],
                  ),
                ),
              )
            : RefreshIndicator(
                onRefresh: () async {
                  // Trigger a re-fetch by invalidating the provider.
                  ref.invalidate(teamVoicesProvider(teamId));
                },
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(12, 8, 12, 88),
                  itemCount: rows.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (context, index) =>
                      _TeamVoiceCard(row: rows[index], teamId: teamId),
                ),
              ),
      ),
    );
  }

  Future<void> _compose(
    BuildContext context,
    WidgetRef ref,
    dynamic team,
  ) async {
    final message = await showVoiceComposer(context);
    if (message == null || message.isEmpty) return;
    await ref
        .read(voicesActionsProvider)
        .createTeamPost(
          teamId: teamId,
          teamName: team?.name ?? '',
          message: message,
        );
  }
}

/// A voice card scoped to a team, navigates to the full thread.
class _TeamVoiceCard extends ConsumerWidget {
  const _TeamVoiceCard({required this.row, required this.teamId});

  final dynamic row;
  final String teamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Reuse the community VoiceCard for the post content.
    // It handles replies, editing, etc. via the voices providers.
    return _VoiceCardContent(row: row);
  }
}

/// Voice card content without the community compose affordance.
class _VoiceCardContent extends ConsumerWidget {
  const _VoiceCardContent({required this.row});

  final dynamic row;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final replies = ref.watch(voiceReplyCountProvider(row.id));

    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.push('${Routes.voices}/${row.id}'),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(child: Text(_initial(row.userName))),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          row.userName ?? '',
                          style: theme.textTheme.titleSmall,
                        ),
                        Text(
                          formatVoiceTime(row.time),
                          style: theme.textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  if (row.isEdited)
                    Chip(
                      label: Text(l10n.edited),
                      visualDensity: VisualDensity.compact,
                    ),
                  if (row.docId == null || row.docId!.isEmpty)
                    const Padding(
                      padding: EdgeInsets.only(left: 6),
                      child: Icon(Icons.cloud_upload_outlined, size: 18),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              Text(row.message ?? ''),
              if (row.labels.isNotEmpty) ...[
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  children: [
                    for (final label in row.labels)
                      Chip(
                        label: Text(label),
                        visualDensity: VisualDensity.compact,
                      ),
                  ],
                ),
              ],
              const SizedBox(height: 4),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: () => context.push('${Routes.voices}/${row.id}'),
                  icon: const Icon(Icons.mode_comment_outlined, size: 18),
                  label: Text(l10n.repliesCount(replies.valueOrNull ?? 0)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static String _initial(String? name) {
    final trimmed = (name ?? '').trim();
    return trimmed.isEmpty ? '?' : trimmed.characters.first.toUpperCase();
  }
}
