import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/chat_provider.dart';
import '../../providers/sync_state.dart';
import '../../repository/chat_repository.dart';
import '../router.dart';

/// Port of `ui/chat/ChatHistoryFragment.kt`.
///
/// Shows the list of chat conversations with search/filter capabilities.
/// The search bar matches the title by default; a "full conversation" toggle
/// reveals a question/response switch and searches every turn's query or
/// response text, ranked so a prefix hit in the first turn outranks a
/// substring hit in a later one.
class ChatHistoryScreen extends ConsumerWidget {
  const ChatHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final history = ref.watch(filteredChatHistoryProvider);
    final searchQuery = ref.watch(chatSearchQueryProvider);
    final fullSearch = ref.watch(chatFullSearchProvider);
    final searchMode = ref.watch(chatSearchModeProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.chatHistory),
        actions: [
          IconButton(
            icon: const Icon(Icons.sync),
            tooltip: l10n.sync,
            onPressed: ref.watch(chatSyncProvider) is SyncRunning
                ? null
                : () => ref.read(chatSyncProvider.notifier).sync(),
          ),
          IconButton(
            icon: const Icon(Icons.add),
            tooltip: l10n.newChat,
            onPressed: () {
              ref.read(chatConversationProvider.notifier).startNewChat();
              // `/life/chat/new`, not [Routes.chat]: that constant is the
              // template `/life/chat/:chatId`, and go_router matches a
              // placeholder against its own literal text, so pushing it opened
              // the detail screen with `chatId: ':chatId'`. `loadChat` then
              // searched for a conversation by that name, found none and
              // returned, which is the only reason the new chat still worked.
              context.push('${Routes.chatHistory}/new');
            },
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: SearchBar(
              hintText: l10n.searchChats,
              leading: const Icon(Icons.search),
              onChanged: (value) =>
                  ref.read(chatSearchQueryProvider.notifier).state = value,
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              children: [
                Expanded(
                  child: CheckboxListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    controlAffinity: ListTileControlAffinity.leading,
                    value: fullSearch,
                    onChanged: (value) =>
                        ref.read(chatFullSearchProvider.notifier).state =
                            value ?? false,
                    title: Text(
                      l10n.fullConversationResponse,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (fullSearch)
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
              child: Row(
                children: [
                  Expanded(
                    child: SegmentedButton<ChatSearchMode>(
                      segments: [
                        ButtonSegment(
                          value: ChatSearchMode.question,
                          label: Text(l10n.question),
                        ),
                        ButtonSegment(
                          value: ChatSearchMode.response,
                          label: Text(l10n.response),
                        ),
                      ],
                      selected: {searchMode},
                      onSelectionChanged: (selection) =>
                          ref.read(chatSearchModeProvider.notifier).state =
                              selection.first,
                    ),
                  ),
                ],
              ),
            ),
          Expanded(
            child: history.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (_, _) => Center(child: Text(l10n.chatsUnavailable)),
              data: (chats) {
                if (chats.isEmpty) {
                  return Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.chat_bubble_outline, size: 64),
                        const SizedBox(height: 16),
                        Text(
                          searchQuery.isEmpty
                              ? l10n.noChats
                              : l10n.noSearchResults,
                        ),
                      ],
                    ),
                  );
                }

                return ListView.separated(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  itemCount: chats.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    final chat = chats[index];
                    final conversations = chat.conversations;
                    final lastMessage = _extractLastMessage(conversations);
                    final timestamp = _formatTimestamp(chat.updatedDate, l10n);
                    final heading = _heading(chat);

                    return Card(
                      child: ListTile(
                        leading: CircleAvatar(
                          child: Text(
                            heading?.isNotEmpty == true
                                ? heading![0].toUpperCase()
                                : '?',
                          ),
                        ),
                        title: Text(
                          heading ?? l10n.untitledChat,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            if (lastMessage != null)
                              Text(
                                lastMessage,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                              ),
                            if (timestamp != null)
                              Text(
                                timestamp,
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                          ],
                        ),
                        // `RowChatHistoryBinding.shareChat` — the per-row
                        // share affordance. Without it every screen and
                        // repository below is unreachable, which is the whole
                        // reason the port had no chat share.
                        trailing: IconButton(
                          key: Key('share-chat-${chat.id}'),
                          icon: const Icon(Icons.share),
                          tooltip: l10n.shareChat,
                          onPressed: () => startChatShare(context, ref, chat),
                        ),
                        onTap: () {
                          ref
                              .read(chatConversationProvider.notifier)
                              .loadChat(chat.id);
                          // Built from the list's own route, not from
                          // [Routes.chat] — that constant is already the
                          // template `/life/chat/:chatId`, so appending an id
                          // produced a three-segment path the router has no
                          // route for and every tap landed on go_router's
                          // error page.
                          context.push('${Routes.chatHistory}/${chat.id}');
                        },
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  /// The name this conversation goes by in the list.
  ///
  /// `ChatHistoryAdapter.onBindViewHolder` uses `conversations[0].query` and
  /// falls back to the stored `title` only when there is no first query. That
  /// order matters: a synced document need not carry a `title` at all, and
  /// those rows were showing as "Untitled chat" behind a "?" avatar with the
  /// question sitting unread in the conversation.
  String? _heading(ChatRow chat) {
    final conversationsJson = chat.conversations;
    if (conversationsJson != null && conversationsJson.isNotEmpty) {
      try {
        final decoded = jsonDecode(conversationsJson);
        if (decoded is List && decoded.isNotEmpty) {
          final first = decoded.first;
          if (first is Map) {
            final query = first['query'];
            if (query is String) return query;
          }
        }
      } catch (_) {
        // Fall through to the stored title.
      }
    }
    return chat.title;
  }

  /// Last turn of the stored conversation, for the list subtitle.
  ///
  /// `ChatEntries.conversations` holds a JSON array of `{query, response}`
  /// objects. The response is preferred because the list reads as the
  /// assistant's side of the thread; a turn still awaiting one falls back to
  /// the query. Malformed JSON yields no subtitle rather than throwing —
  /// this is decoration, and one bad row should not blank the whole list.
  String? _extractLastMessage(String? conversationsJson) {
    if (conversationsJson == null || conversationsJson.isEmpty) return null;
    try {
      final decoded = jsonDecode(conversationsJson);
      if (decoded is! List) return null;
      for (final turn in decoded.reversed) {
        if (turn is! Map) continue;
        for (final key in const ['response', 'query']) {
          final value = turn[key];
          if (value is String && value.trim().isNotEmpty) return value.trim();
        }
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  String? _formatTimestamp(String? dateStr, AppLocalizations l10n) {
    if (dateStr == null || dateStr.isEmpty) return null;
    try {
      final timestamp = int.tryParse(dateStr);
      if (timestamp == null) return null;
      final date = DateTime.fromMillisecondsSinceEpoch(timestamp);
      final now = DateTime.now();
      final diff = now.difference(date);

      if (diff.inDays > 7) {
        return '${date.day}/${date.month}/${date.year}';
      } else if (diff.inDays > 0) {
        return '${diff.inDays}d ago';
      } else if (diff.inHours > 0) {
        return '${diff.inHours}h ago';
      } else if (diff.inMinutes > 0) {
        return '${diff.inMinutes}m ago';
      } else {
        return l10n.justNow;
      }
    } catch (_) {
      return null;
    }
  }
}

/// Which branch of the share dialog the user picked.
enum _ShareBranch { community, teams, enterprises }

/// Port of `ChatHistoryAdapter.bindShareChat` and the two dialogs it opens.
///
/// The Kotlin flow is: an expandable list with a *community* group and a
/// *team/enterprise* group; picking a group's child either opens the note
/// dialog straight away (community) or a second dialog listing the teams or
/// enterprises to pick from. A destination the chat has already been shared
/// to shows a marker and is not tappable, and an empty team or enterprise
/// list gets its own "please join one first" alert rather than an empty
/// dialog.
///
/// The two providers are awaited, not read: nothing on this screen watches
/// them, so `valueOrNull` would be null on the first tap and the dialog would
/// silently offer no destinations. Both awaits are inside the `try`, because a
/// future can reject where `valueOrNull` could not.
@visibleForTesting
Future<void> startChatShare(
  BuildContext context,
  WidgetRef ref,
  ChatRow chat,
) async {
  final l10n = AppLocalizations.of(context);
  final ChatShareTargets targets;
  final Map<String, Set<String>> destinations;
  try {
    targets = await ref.read(chatShareTargetsProvider.future);
    destinations = await ref.read(sharedChatDestinationsProvider.future);
  } catch (_) {
    if (context.mounted) _showChatShareMessage(context, l10n.chatsUnavailable);
    return;
  }
  if (!context.mounted) return;

  final sharedIds = destinations[chat.docId ?? ''] ?? const <String>{};
  final community = targets.community;
  final communityShared = community != null && sharedIds.contains(community.id);

  final branch = await showDialog<_ShareBranch>(
    context: context,
    builder: (context) => _ShareBranchDialog(
      hasCommunity: community != null,
      communityShared: communityShared,
    ),
  );
  if (branch == null || !context.mounted) return;

  final ChatShareTarget? target;
  final String section;
  switch (branch) {
    case _ShareBranch.community:
      target = community;
      section = ChatShareSection.community;
    case _ShareBranch.teams:
      section = ChatShareSection.teams;
      target = await _pickShareTarget(
        context,
        title: l10n.teams,
        emptyMessage: l10n.joinTeamFirst,
        targets: targets.teams,
        sharedIds: sharedIds,
      );
    case _ShareBranch.enterprises:
      section = ChatShareSection.enterprises;
      target = await _pickShareTarget(
        context,
        title: l10n.enterprises,
        emptyMessage: l10n.joinEnterpriseFirst,
        targets: targets.enterprises,
        sharedIds: sharedIds,
      );
  }
  if (target == null || !context.mounted) return;

  final note = await showDialog<String>(
    context: context,
    builder: (context) => const _ShareNoteDialog(),
  );
  // Cancel writes nothing, as `setNegativeButton` does; an empty note is a
  // deliberate share and still posts, because `add_note` is optional.
  if (note == null || !context.mounted) return;

  final outcome = await ref
      .read(chatShareActionsProvider)
      .share(chat: chat, target: target, section: section, note: note);
  if (!context.mounted) return;
  _showChatShareMessage(context, switch (outcome) {
    ChatShareOutcome.shared => l10n.chatShared,
    ChatShareOutcome.alreadyShared => l10n.chatAlreadyShared,
    ChatShareOutcome.unavailable => l10n.chatsUnavailable,
  });
}

void _showChatShareMessage(BuildContext context, String message) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
}

/// Second dialog: the teams or enterprises to share into.
///
/// Port of `showGrandChildRecyclerView`, including its empty branch — an
/// alert naming the section rather than a list with nothing in it.
Future<ChatShareTarget?> _pickShareTarget(
  BuildContext context, {
  required String title,
  required String emptyMessage,
  required List<ChatShareTarget> targets,
  required Set<String> sharedIds,
}) async {
  if (targets.isEmpty) {
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(emptyMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(MaterialLocalizations.of(context).okButtonLabel),
          ),
        ],
      ),
    );
    return null;
  }
  return showDialog<ChatShareTarget>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: SizedBox(
        width: double.maxFinite,
        child: ListView(
          shrinkWrap: true,
          children: [
            for (final target in targets)
              _ShareTargetTile(
                target: target,
                // `TeamsSelectionAdapter.bind`: an already-shared destination
                // shows the shared icon and has its click listener removed.
                alreadyShared: sharedIds.contains(target.id),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(AppLocalizations.of(context).close),
        ),
      ],
    ),
  );
}

class _ShareTargetTile extends StatelessWidget {
  const _ShareTargetTile({required this.target, required this.alreadyShared});

  final ChatShareTarget target;
  final bool alreadyShared;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      key: Key('share-target-${target.id}'),
      leading: const Icon(Icons.groups_outlined),
      title: Text(target.name),
      trailing: alreadyShared ? const Icon(Icons.check) : null,
      enabled: !alreadyShared,
      onTap: alreadyShared ? null : () => Navigator.of(context).pop(target),
    );
  }
}

/// First dialog: the two collapsible destination groups.
class _ShareBranchDialog extends StatefulWidget {
  const _ShareBranchDialog({
    required this.hasCommunity,
    required this.communityShared,
  });

  /// False when the planet has no community name or parent code configured,
  /// in which case `loadShareTargets` leaves `community` null and there is
  /// nothing for the group to open.
  final bool hasCommunity;
  final bool communityShared;

  @override
  State<_ShareBranchDialog> createState() => _ShareBranchDialogState();
}

class _ShareBranchDialogState extends State<_ShareBranchDialog> {
  bool _communityExpanded = false;
  bool _teamsExpanded = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      content: SizedBox(
        width: double.maxFinite,
        child: ListView(
          shrinkWrap: true,
          children: [
            ListTile(
              key: const Key('share-group-community'),
              title: Text(
                l10n.shareWithCommunity,
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              trailing: Icon(
                _communityExpanded ? Icons.expand_less : Icons.expand_more,
              ),
              onTap: () =>
                  setState(() => _communityExpanded = !_communityExpanded),
            ),
            if (_communityExpanded && widget.hasCommunity)
              ListTile(
                key: const Key('share-child-community'),
                title: Text(l10n.community),
                trailing: widget.communityShared
                    ? const Icon(Icons.check)
                    : null,
                enabled: !widget.communityShared,
                onTap: widget.communityShared
                    ? null
                    : () => Navigator.of(context).pop(_ShareBranch.community),
              ),
            ListTile(
              key: const Key('share-group-team'),
              title: Text(
                l10n.shareWithTeamEnterprise,
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              trailing: Icon(
                _teamsExpanded ? Icons.expand_less : Icons.expand_more,
              ),
              onTap: () => setState(() => _teamsExpanded = !_teamsExpanded),
            ),
            if (_teamsExpanded) ...[
              ListTile(
                key: const Key('share-child-teams'),
                title: Text(l10n.teams),
                onTap: () => Navigator.of(context).pop(_ShareBranch.teams),
              ),
              ListTile(
                key: const Key('share-child-enterprises'),
                title: Text(l10n.enterprises),
                onTap: () =>
                    Navigator.of(context).pop(_ShareBranch.enterprises),
              ),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.close),
        ),
      ],
    );
  }
}

/// Port of `showEditTextAndShareButton` — the optional note carried as the
/// post's `message`.
class _ShareNoteDialog extends StatefulWidget {
  const _ShareNoteDialog();

  @override
  State<_ShareNoteDialog> createState() => _ShareNoteDialogState();
}

class _ShareNoteDialogState extends State<_ShareNoteDialog> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      content: TextField(
        key: const Key('share-note-field'),
        controller: _controller,
        autofocus: true,
        maxLines: null,
        keyboardType: TextInputType.multiline,
        decoration: InputDecoration(hintText: l10n.addNoteOptional),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.cancel),
        ),
        TextButton(
          key: const Key('share-note-submit'),
          onPressed: () => Navigator.of(context).pop(_controller.text),
          child: Text(l10n.shareChat),
        ),
      ],
    );
  }
}
