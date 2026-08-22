import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/feedback_mapper.dart';
import '../../data/local/user_mapper.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/feedback_provider.dart';
import '../../providers/session_provider.dart';

/// Port of `ui/feedback/FeedbackDetailActivity.kt`.
///
/// Shows feedback details with replies and allows adding new replies.
class FeedbackDetailScreen extends ConsumerStatefulWidget {
  const FeedbackDetailScreen({super.key, required this.feedbackId});

  final String feedbackId;

  @override
  ConsumerState<FeedbackDetailScreen> createState() =>
      _FeedbackDetailScreenState();
}

class _FeedbackDetailScreenState extends ConsumerState<FeedbackDetailScreen> {
  final _replyController = TextEditingController();
  bool _isSubmitting = false;

  @override
  void dispose() {
    _replyController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final feedbackAsync = ref.watch(feedbackByIdProvider(widget.feedbackId));
    final session = ref.watch(sessionProvider).valueOrNull;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.feedback)),
      body: feedbackAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('${l10n.error}: $e')),
        data: (feedback) {
          if (feedback == null) {
            return Center(child: Text(l10n.feedbackNotFound));
          }

          final messages = FeedbackMapper.parseMessages(feedback.messages);
          final isClosed = feedback.status?.toLowerCase() == 'closed';
          final isManager =
              session != null && UserMapper.isManager(session) == true;

          return Column(
            children: [
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    // First message (the original feedback)
                    if (messages.isNotEmpty) ...[
                      _MessageCard(
                        message: messages.first.message,
                        user: messages.first.user,
                        date: messages.first.date,
                        isFirst: true,
                      ),
                      const SizedBox(height: 16),
                    ],

                    // Replies
                    if (messages.length > 1) ...[
                      Text(
                        l10n.replies,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 8),
                      ...messages
                          .skip(1)
                          .map(
                            (m) => Padding(
                              padding: const EdgeInsets.only(bottom: 8),
                              child: _MessageCard(
                                message: m.message,
                                user: m.user,
                                date: m.date,
                                isFirst: false,
                              ),
                            ),
                          ),
                    ],
                  ],
                ),
              ),

              // Reply input
              if (!isClosed)
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.surface,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.1),
                        blurRadius: 4,
                        offset: const Offset(0, -2),
                      ),
                    ],
                  ),
                  child: SafeArea(
                    child: Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _replyController,
                            decoration: InputDecoration(
                              hintText: l10n.pleaseEnterReply,
                              border: const OutlineInputBorder(),
                            ),
                            maxLines: 3,
                            minLines: 1,
                          ),
                        ),
                        const SizedBox(width: 8),
                        if (isManager)
                          IconButton.filled(
                            onPressed: _isSubmitting
                                ? null
                                : () => _closeFeedback(feedback.id),
                            icon: const Icon(Icons.check),
                            tooltip: l10n.close,
                          ),
                        const SizedBox(width: 4),
                        IconButton.filled(
                          onPressed: _isSubmitting || session == null
                              ? null
                              : () => _submitReply(
                                  feedback.id,
                                  session.name ?? '',
                                ),
                          icon: _isSubmitting
                              ? const SizedBox.square(
                                  dimension: 20,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Icon(Icons.send),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _submitReply(String feedbackId, String userName) async {
    final message = _replyController.text.trim();
    if (message.isEmpty) return;

    setState(() => _isSubmitting = true);

    try {
      await ref
          .read(feedbackRepositoryProvider)
          .addReply(feedbackId, message, userName);
      // `addReply` sets `isUploaded = false`; without this the row sits pending
      // and the reply never reaches the thread on the server.
      await ref.read(feedbackQueueProvider).queuePending();
      ref.invalidate(feedbackByIdProvider(feedbackId));
      _replyController.clear();
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  Future<void> _closeFeedback(String feedbackId) async {
    await ref.read(feedbackRepositoryProvider).closeFeedback(feedbackId);
    // Same as a reply: closing re-marks the row un-uploaded, so the status
    // change has to be queued or the thread stays open for everyone else.
    await ref.read(feedbackQueueProvider).queuePending();
    ref.invalidate(feedbackByIdProvider(feedbackId));
  }
}

class _MessageCard extends StatelessWidget {
  const _MessageCard({
    required this.message,
    required this.user,
    required this.date,
    required this.isFirst,
  });

  final String message;
  final String user;
  final String date;
  final bool isFirst;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 16,
                  child: Text(user.isNotEmpty ? user[0].toUpperCase() : '?'),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(user, style: Theme.of(context).textTheme.titleSmall),
                      Text(
                        _formatDate(date),
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(message),
            if (isFirst)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Chip(
                  label: Text(l10n.feedback),
                  backgroundColor: Theme.of(
                    context,
                  ).colorScheme.primaryContainer,
                ),
              ),
          ],
        ),
      ),
    );
  }

  String _formatDate(String dateStr) {
    if (dateStr.isEmpty) return '';
    try {
      final timestamp = int.tryParse(dateStr);
      if (timestamp == null) return dateStr;
      final date = DateTime.fromMillisecondsSinceEpoch(timestamp);
      return '${date.day}/${date.month}/${date.year} ${date.hour}:${date.minute.toString().padLeft(2, '0')}';
    } catch (_) {
      return dateStr;
    }
  }
}
