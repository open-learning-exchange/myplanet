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
              context.push(Routes.chat);
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
