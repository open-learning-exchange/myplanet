import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/session_provider.dart';
import '../../providers/voices_provider.dart';
import 'voice_composer.dart';
import 'voices_screen.dart';

/// Port of `ui/voices/ReplyActivity.kt`: one post with its replies beneath it.
class VoiceThreadScreen extends ConsumerWidget {
  const VoiceThreadScreen({required this.newsId, super.key});

  final String newsId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final post = ref.watch(voiceProvider(newsId));
    final replies = ref.watch(voiceRepliesProvider(newsId));

    return Scaffold(
      appBar: AppBar(title: Text(l10n.thread)),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _reply(context, ref),
        icon: const Icon(Icons.reply),
        label: Text(l10n.replyToVoice),
      ),
      body: SafeArea(
        child: post.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, _) => Center(child: Text(l10n.voicesUnavailable)),
          data: (row) {
            if (row == null) return Center(child: Text(l10n.voicesUnavailable));
            return ListView(
              padding: const EdgeInsets.fromLTRB(12, 12, 12, 88),
              children: [
                VoiceCard(row: row),
                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: Text(
                    l10n.repliesCount(replies.valueOrNull?.length ?? 0),
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                ),
                const SizedBox(height: 8),
                ...?replies.valueOrNull?.map(
                  (reply) => Padding(
                    padding: const EdgeInsetsDirectional.only(
                      start: 16,
                      bottom: 8,
                    ),
                    child: VoiceCard(row: reply),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  /// The session is **awaited**, and the `await` sits inside the `try`.
  ///
  /// This screen never watches `sessionProvider`, so
  /// `ref.read(...).valueOrNull` was null until something else resolved it and
  /// the tap was dropped before the composer even opened — no dialog, no
  /// snackbar, no row. In the shipping app the router's `ref.listen` keeps it
  /// resolved, which is what made this latent rather than visible; it is real
  /// for any entry that does not, such as a deep link into a thread. The
  /// `await` is inside the `try` because a future can reject where
  /// `valueOrNull` could not.
  ///
  /// `VoicesActions.postReply` awaits the session for itself (Phase 144), so
  /// the guard here is only about not opening a composer whose reply would be
  /// discarded — but a guard that always fires discards the tap instead, which
  /// is what it was doing.
  Future<void> _reply(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final UserRow? user;
    try {
      user = await ref.read(sessionProvider.future);
    } catch (_) {
      return;
    }
    if (user == null || !context.mounted) return;
    final composed = await showVoiceComposer(
      context,
      title: l10n.replyToVoice,
      hintText: l10n.writeAReply,
      // `ReplyActivity` carries the same picker as the two compose screens
      // (`ReplyActivity.kt:226-233` builds the identical `imageUrls` entry).
      allowImages: true,
    );
    if (composed == null || composed.message.isEmpty) return;
    await ref
        .read(voicesActionsProvider)
        .postReply(
          parentId: newsId,
          message: composed.message,
          attachments: composed.images,
        );
  }
}
