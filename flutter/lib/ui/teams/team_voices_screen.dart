import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/teams_provider.dart';
import '../../providers/voices_provider.dart';
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
    final team = ref.watch(teamProvider(teamId)).value;
    final voices = ref.watch(teamVoicesProvider(teamId));

    // Opening this screen is what clears the dashboard's chat badge for the
    // team: `TeamsVoicesViewModel.getFilteredNews` moves the watermark to the
    // post count it just read, and the badge is `lastCount < currentCount`.
    // Without this the badge could never clear — and, since `hasChat` requires
    // a watermark row to exist at all, could never appear either.
    // The `watch` above starts the subscription, so the first emission arrives
    // after this build and the listener catches it; a later post re-emits and
    // moves the watermark again.
    ref.listen(teamVoicesProvider(teamId), (previous, next) {
      final rows = next.value;
      if (rows == null) return;
      ref
          .read(notificationsRepositoryProvider)
          .updateTeamNotification(teamId, rows.length);
    });
    final memberships = ref.watch(teamMembershipsProvider).value ?? const {};
    final membership = memberships[teamId];

    return Scaffold(
      appBar: AppBar(title: Text(l10n.teamDiscussions)),
      // Gated on the **team** as well as the membership. The two resolve
      // independently — `teamMembershipsProvider` is a stream,
      // `teamProvider` a one-shot future — so without this there is a window
      // where the FAB renders with `team == null` and a tap composes an
      // enterprise's post as `messageType: ''` with an empty `viewIn` name,
      // which is exactly the mislabelling this screen was fixed to stop.
      // Kotlin has no such window: `TeamsVoicesFragment` only enables
      // `btnSubmit` from `updateCanPostMessage`, reached once
      // `viewModel.teamPolicy` has emitted the resolved team.
      floatingActionButton: membership != null && team != null
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
                      VoiceCard(row: rows[index], teamName: team?.name ?? ''),
                ),
              ),
      ),
    );
  }

  /// The team's own `type` is what labels the post, not the literal `'team'`:
  /// `TeamsVoicesFragment.kt:80` sends `getEffectiveTeamType()`, which is the
  /// nav argument or `team?.type`, falling back to `""`. This screen resolves
  /// the team directly rather than through Kotlin's tab pager, so there is no
  /// nav argument to prefer — `team?.type` is the whole chain. The `''`
  /// fallback covers a team document that omits `type`, which is the only case
  /// Kotlin sends `""` for; an *unresolved* team cannot reach here, because
  /// the FAB waits for it. An earlier version of this comment said Kotlin
  /// sends `""` for an unloaded team as well, and it does not.
  Future<void> _compose(
    BuildContext context,
    WidgetRef ref,
    TeamRow? team,
  ) async {
    final composed = await showVoiceComposer(context, allowImages: true);
    if (composed == null || composed.message.isEmpty) return;
    await ref
        .read(voicesActionsProvider)
        .createTeamPost(
          teamId: teamId,
          teamName: team?.name ?? '',
          teamType: team?.type ?? '',
          message: composed.message,
          attachments: composed.images,
        );
  }
}
