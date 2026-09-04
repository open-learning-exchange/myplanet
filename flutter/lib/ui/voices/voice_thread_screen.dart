import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

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

  Future<void> _reply(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    final message = await showVoiceComposer(
      context,
      title: l10n.replyToVoice,
      hintText: l10n.writeAReply,
    );
    if (message == null || message.isEmpty) return;
    await ref
        .read(voicesActionsProvider)
        .postReply(parentId: newsId, message: message);
  }
}
