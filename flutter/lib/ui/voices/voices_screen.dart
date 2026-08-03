import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/sync_state.dart';
import '../../providers/voices_provider.dart';
import '../router.dart';
import 'voice_composer.dart';

/// Port of `ui/voices/VoicesFragment.kt` and `VoicesAdapter`.
class VoicesScreen extends ConsumerWidget {
  const VoicesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final feed = ref.watch(communityFeedProvider);
    final sync = ref.watch(voicesSyncProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.voices),
        actions: [
          IconButton(
            tooltip: l10n.syncVoices,
            onPressed: sync is SyncRunning
                ? null
                : () => ref.read(voicesSyncProvider.notifier).sync(),
            icon: const Icon(Icons.sync),
          ),
        ],
        bottom: PreferredSize(
          preferredSize: Size.fromHeight(sync is SyncRunning ? 68 : 64),
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                child: SearchBar(
                  hintText: l10n.searchVoices,
                  leading: const Icon(Icons.search),
                  onChanged: (value) =>
                      ref.read(voiceSearchProvider.notifier).state = value,
                ),
              ),
              if (sync is SyncRunning) const LinearProgressIndicator(),
            ],
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _compose(context, ref),
        icon: const Icon(Icons.campaign_outlined),
        label: Text(l10n.shareYourVoice),
      ),
      body: SafeArea(
        child: feed.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, _) => Center(child: Text(l10n.voicesUnavailable)),
          data: (rows) => rows.isEmpty
              ? Center(child: Text(l10n.noVoices))
              : RefreshIndicator(
                  onRefresh: () => ref.read(voicesSyncProvider.notifier).sync(),
                  child: ListView.separated(
                    padding: const EdgeInsets.fromLTRB(12, 8, 12, 88),
                    itemCount: rows.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 8),
                    itemBuilder: (context, index) =>
                        VoiceCard(row: rows[index]),
                  ),
                ),
        ),
      ),
    );
  }

  Future<void> _compose(BuildContext context, WidgetRef ref) async {
    final message = await showVoiceComposer(context);
    if (message == null || message.isEmpty) return;
    await ref.read(voicesActionsProvider).createPost(message);
  }
}

/// One post in the feed. Mirrors `row_news.xml`: author, relative time, the
/// message, an edited marker and the reply affordance.
class VoiceCard extends ConsumerWidget {
  const VoiceCard({required this.row, super.key});

  final NewsRow row;

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
                  // A post still carrying no CouchDB id has not reached the
                  // server; the outbox will deliver it on the next drain.
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

/// Absolute date-time, matching `VoicesAdapterHelper`'s formatting rather than
/// inventing a relative "2 hours ago" the Kotlin never shows.
String formatVoiceTime(int millis) {
  if (millis <= 0) return '';
  final date = DateTime.fromMillisecondsSinceEpoch(millis);
  String two(int value) => value.toString().padLeft(2, '0');
  return '${date.year}-${two(date.month)}-${two(date.day)} '
      '${two(date.hour)}:${two(date.minute)}';
}
