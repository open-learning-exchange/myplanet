import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/session_provider.dart';
import '../../providers/app_providers.dart';
import '../../providers/voices_provider.dart';
import '../components/profile_avatar.dart';

/// Inline comment thread for a team task or meetup. Port of the inline
/// comments from `15112-team-tasks-meetups-comment-threads` — comments are
/// `News` rows with `messageType = 'comment'` and `replyTo = parentId`.
///
/// Renders a collapsible section: a header row with the comment count, the
/// list of comments, and an input field to add a new one.
class InlineComments extends ConsumerStatefulWidget {
  const InlineComments({required this.parentId, this.teamId, super.key});

  final String parentId;
  final String? teamId;

  @override
  ConsumerState<InlineComments> createState() => _InlineCommentsState();
}

class _InlineCommentsState extends ConsumerState<InlineComments> {
  final _controller = TextEditingController();
  bool _expanded = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final comments = ref.watch(commentsForParentProvider(widget.parentId));

    final count = comments.valueOrNull?.length ?? 0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        InkWell(
          onTap: () => setState(() => _expanded = !_expanded),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(
              children: [
                Icon(
                  _expanded ? Icons.expand_less : Icons.expand_more,
                  size: 20,
                ),
                const SizedBox(width: 4),
                Text(
                  l10n.commentsCount(count),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ),
        if (_expanded) ...[
          if (comments.isLoading)
            const Padding(
              padding: EdgeInsets.all(8),
              child: SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            )
          else if (comments.hasError)
            Padding(
              padding: const EdgeInsets.only(left: 24, bottom: 4),
              child: Text(
                l10n.commentsUnavailable,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            )
          else if (comments.valueOrNull?.isEmpty == true)
            Padding(
              padding: const EdgeInsets.only(left: 24, bottom: 4),
              child: Text(
                l10n.noComments,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            )
          else
            for (final comment in comments.valueOrNull ?? const <NewsRow>[])
              _CommentTile(
                comment: comment,
                parentId: widget.parentId,
                teamId: widget.teamId,
              ),
          _CommentInput(
            controller: _controller,
            onSubmitted: _addComment,
            l10n: l10n,
          ),
        ],
      ],
    );
  }

  Future<void> _addComment() async {
    final text = _controller.text.trim();
    if (text.isEmpty) return;
    try {
      // This widget never *watches* `sessionProvider`, so
      // `ref.read(...).valueOrNull` is null until something else resolves it
      // and the comment was dropped with no error and no record. In the app
      // the router holds a `ref.listen`, which is what made it latent.
      // The await sits inside the `try` because a future can reject where
      // `valueOrNull` could not.
      final user = await ref.read(sessionProvider.future);
      if (user == null) return;
      await ref
          .read(voicesRepositoryProvider)
          .addComment(
            parentId: widget.parentId,
            teamId: widget.teamId,
            message: text,
            userId: user.id,
            userName: user.name,
            planetCode: user.planetCode,
            parentCode: user.parentCode,
          );
      // Cleared only once the write has landed, so a failure leaves the text
      // in the field to retry rather than discarding it.
      _controller.clear();
    } catch (_) {
      // A rejecting session or a failed write must not take the thread down.
    }
  }
}

class _CommentTile extends StatelessWidget {
  const _CommentTile({
    required this.comment,
    required this.parentId,
    this.teamId,
  });

  final NewsRow comment;
  final String parentId;
  final String? teamId;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final time = comment.time == 0
        ? ''
        : MaterialLocalizations.of(
            context,
          ).formatMediumDate(DateTime.fromMillisecondsSinceEpoch(comment.time));
    return Padding(
      padding: const EdgeInsets.only(left: 24, bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 12,
            child: Text(
              // `''.characters.first` throws `StateError`, and a synced `News`
              // row can carry an empty `userName` — `??` only guards null.
              initialFor(comment.userName),
              style: const TextStyle(fontSize: 12),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      comment.userName ?? '',
                      style: theme.textTheme.labelMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    if (time.isNotEmpty) ...[
                      const SizedBox(width: 8),
                      Text(time, style: theme.textTheme.bodySmall),
                    ],
                  ],
                ),
                const SizedBox(height: 2),
                Text(comment.message ?? '', style: theme.textTheme.bodyMedium),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CommentInput extends StatelessWidget {
  const _CommentInput({
    required this.controller,
    required this.onSubmitted,
    required this.l10n,
  });

  final TextEditingController controller;
  final VoidCallback onSubmitted;
  final AppLocalizations l10n;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 24, top: 4),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: controller,
              decoration: InputDecoration(
                hintText: l10n.addComment,
                isDense: true,
                border: const OutlineInputBorder(
                  borderRadius: BorderRadius.all(Radius.circular(8)),
                ),
              ),
              onSubmitted: (_) => onSubmitted(),
            ),
          ),
          const SizedBox(width: 4),
          IconButton(
            icon: const Icon(Icons.send_outlined, size: 20),
            onPressed: onSubmitted,
          ),
        ],
      ),
    );
  }
}
