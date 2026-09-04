import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../data/local/chat_mapper.dart';
import '../../providers/chat_provider.dart';
import '../../providers/session_provider.dart';

/// Port of `ui/chat/ChatDetailFragment.kt`.
///
/// Shows a single chat conversation with message input.
class ChatDetailScreen extends ConsumerStatefulWidget {
  const ChatDetailScreen({super.key, this.chatId});

  final String? chatId;

  @override
  ConsumerState<ChatDetailScreen> createState() => _ChatDetailScreenState();
}

class _ChatDetailScreenState extends ConsumerState<ChatDetailScreen> {
  final _messageController = TextEditingController();
  final _scrollController = ScrollController();
  bool _isSending = false;

  @override
  void initState() {
    super.initState();
    if (widget.chatId != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        ref.read(chatConversationProvider.notifier).loadChat(widget.chatId!);
      });
    }
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = ref.watch(sessionProvider).valueOrNull;
    final chatState = ref.watch(chatConversationProvider);
    final aiProviders = ref.watch(aiProvidersProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(
          chatState.id.isEmpty ? l10n.newChat : l10n.chatConversation,
        ),
        actions: [
          if (aiProviders.valueOrNull?.isNotEmpty == true)
            PopupMenuButton<String>(
              icon: const Icon(Icons.psychology),
              tooltip: l10n.selectAiProvider,
              // `aiProvidersProvider` is a name -> available map; it carries no
              // model, so the menu selects a provider by name and leaves the
              // model empty, as the default config does. Encoding `name:model`
              // in the value packed the name in twice and set the model to it.
              onSelected: (provider) => ref
                  .read(chatConversationProvider.notifier)
                  .setAiProvider(AiProviderConfig(name: provider, model: '')),
              itemBuilder: (context) {
                final providers = aiProviders.valueOrNull ?? {};
                return [
                  for (final entry in providers.entries)
                    PopupMenuItem(
                      value: entry.key,
                      // An unavailable provider stays visible but unselectable,
                      // rather than sending a request that cannot be served.
                      enabled: entry.value,
                      child: Text(
                        entry.value
                            ? entry.key
                            : '${entry.key} (${l10n.unavailable})',
                      ),
                    ),
                ];
              },
            ),
        ],
      ),
      body: Column(
        children: [
          if (chatState.error != null)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(8),
              color: Theme.of(context).colorScheme.errorContainer,
              child: Text(
                chatState.error!,
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onErrorContainer,
                ),
              ),
            ),
          Expanded(
            child: chatState.messages.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.chat_bubble_outline, size: 64),
                        const SizedBox(height: 16),
                        Text(l10n.startConversation),
                      ],
                    ),
                  )
                : ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.all(16),
                    itemCount: chatState.messages.length,
                    itemBuilder: (context, index) {
                      final message = chatState.messages[index];
                      final isUser = message.isUser;

                      return Align(
                        alignment: isUser
                            ? AlignmentDirectional.centerEnd
                            : AlignmentDirectional.centerStart,
                        child: Container(
                          constraints: BoxConstraints(
                            maxWidth: MediaQuery.of(context).size.width * 0.75,
                          ),
                          margin: const EdgeInsets.only(bottom: 8),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 10,
                          ),
                          decoration: BoxDecoration(
                            color: isUser
                                ? Theme.of(context).colorScheme.primary
                                : Theme.of(
                                    context,
                                  ).colorScheme.surfaceContainerHighest,
                            borderRadius: BorderRadius.only(
                              topLeft: const Radius.circular(16),
                              topRight: const Radius.circular(16),
                              bottomLeft: isUser
                                  ? const Radius.circular(16)
                                  : Radius.zero,
                              bottomRight: isUser
                                  ? Radius.zero
                                  : const Radius.circular(16),
                            ),
                          ),
                          child: Text(
                            message.content,
                            style: TextStyle(
                              color: isUser
                                  ? Theme.of(context).colorScheme.onPrimary
                                  : Theme.of(context).colorScheme.onSurface,
                            ),
                          ),
                        ),
                      );
                    },
                  ),
          ),
          if (chatState.isLoading)
            const Padding(
              padding: EdgeInsets.all(8),
              child: LinearProgressIndicator(),
            ),
          Container(
            padding: const EdgeInsets.all(8),
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
                      controller: _messageController,
                      decoration: InputDecoration(
                        hintText: l10n.typeMessage,
                        border: const OutlineInputBorder(),
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                      ),
                      maxLines: 4,
                      minLines: 1,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _sendMessage(),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    onPressed: _isSending || session == null
                        ? null
                        : _sendMessage,
                    icon: _isSending
                        ? const SizedBox.square(
                            dimension: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.send),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _sendMessage() async {
    // `ChatDetailFragment.kt` flattens the field before sending:
    // `"${binding.editGchatMessage.text}".replace("\n", " ")`. The field here
    // is `maxLines: 4`, so a pasted or soft-keyboard newline survived into the
    // request body where the Kotlin would have sent a space.
    final message = _messageController.text.replaceAll('\n', ' ').trim();
    if (message.isEmpty) return;

    setState(() => _isSending = true);
    _messageController.clear();

    await ref.read(chatConversationProvider.notifier).sendMessage(message);

    if (!mounted) return;
    setState(() => _isSending = false);
    _scrollToBottom();
  }

  /// Scrolls the thread to the newest message once the frame that renders it
  /// has been laid out.
  ///
  /// [ScrollController.position] asserts when nothing is attached, and nothing
  /// is: the message list is replaced by an empty-state [Column] until there
  /// is at least one message, and a rebuild is a frame away rather than an
  /// `await` away. So a send that resolved without adding a bubble — the
  /// notifier declining it, which is exactly what happens when `onSubmitted`
  /// fires with no session and bypasses the disabled button — took the whole
  /// screen down on a keystroke.
  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scrollController.hasClients) return;
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
    });
  }
}
