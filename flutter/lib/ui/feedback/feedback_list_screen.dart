import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/feedback_provider.dart';
import '../../providers/sync_state.dart';
import '../router.dart';

/// Port of `ui/feedback/FeedbackListFragment.kt`.
///
/// Shows the list of feedback submissions with filtering.
class FeedbackListScreen extends ConsumerWidget {
  const FeedbackListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final feedbackList = ref.watch(filteredFeedbackProvider);
    final filter = ref.watch(feedbackFilterProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.feedback),
        actions: [
          IconButton(
            icon: const Icon(Icons.sync),
            tooltip: l10n.sync,
            onPressed: ref.watch(feedbackSyncProvider) is SyncRunning
                ? null
                : () => ref.read(feedbackSyncProvider.notifier).sync(),
          ),
          IconButton(
            icon: const Icon(Icons.filter_list),
            tooltip: l10n.filter,
            onPressed: () => _showFilterSheet(context, ref, filter),
          ),
        ],
      ),
      body: Column(
        children: [
          // Header row
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: Row(
              children: [
                Expanded(
                  flex: 2,
                  child: Text(
                    l10n.title,
                    style: Theme.of(context).textTheme.labelLarge,
                  ),
                ),
                Expanded(
                  child: Text(
                    l10n.type,
                    style: Theme.of(context).textTheme.labelLarge,
                  ),
                ),
                Expanded(
                  child: Text(
                    l10n.priority,
                    style: Theme.of(context).textTheme.labelLarge,
                  ),
                ),
                Expanded(
                  child: Text(
                    l10n.status,
                    style: Theme.of(context).textTheme.labelLarge,
                  ),
                ),
                Expanded(
                  child: Text(
                    l10n.openDate,
                    style: Theme.of(context).textTheme.labelLarge,
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: feedbackList.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(child: Text('${l10n.error}: $e')),
              data: (items) {
                if (items.isEmpty) {
                  return Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.feedback_outlined, size: 64),
                        const SizedBox(height: 16),
                        Text(l10n.noDataAvailable),
                      ],
                    ),
                  );
                }

                return ListView.builder(
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final feedback = items[index];
                    return _FeedbackListTile(
                      feedback: feedback,
                      onTap: () {
                        context.push('${Routes.feedback}/${feedback.id}');
                      },
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          context.push(Routes.feedbackCreate);
        },
        child: const Icon(Icons.add),
      ),
    );
  }

  void _showFilterSheet(
    BuildContext context,
    WidgetRef ref,
    FeedbackFilter currentFilter,
  ) {
    final l10n = AppLocalizations.of(context);

    showModalBottomSheet(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setState) {
            var filter = currentFilter;

            return Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l10n.filter,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 16),

                  // Status filter
                  Text(l10n.status),
                  Wrap(
                    spacing: 8,
                    children: ['all', 'open', 'closed'].map((s) {
                      return ChoiceChip(
                        label: Text(s == 'all' ? l10n.all : s),
                        selected: filter.status == s,
                        onSelected: (selected) {
                          setState(() {
                            filter = filter.copyWith(status: s);
                          });
                        },
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: 12),

                  // Priority filter
                  Text(l10n.priority),
                  Wrap(
                    spacing: 8,
                    children: ['all', 'yes', 'no'].map((s) {
                      return ChoiceChip(
                        label: Text(s == 'all' ? l10n.all : s),
                        selected: filter.priority == s,
                        onSelected: (selected) {
                          setState(() {
                            filter = filter.copyWith(priority: s);
                          });
                        },
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: 12),

                  // Type filter
                  Text(l10n.feedbackType),
                  Wrap(
                    spacing: 8,
                    children: ['all', 'question', 'bug', 'suggestion'].map((s) {
                      return ChoiceChip(
                        label: Text(
                          s == 'all'
                              ? l10n.all
                              : s == 'question'
                              ? l10n.question
                              : s == 'bug'
                              ? l10n.bug
                              : l10n.suggestion,
                        ),
                        selected: filter.type == s,
                        onSelected: (selected) {
                          setState(() {
                            filter = filter.copyWith(type: s);
                          });
                        },
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: 24),

                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      TextButton(
                        onPressed: () {
                          ref.read(feedbackFilterProvider.notifier).state =
                              const FeedbackFilter();
                          Navigator.pop(context);
                        },
                        child: Text(l10n.clearFilters),
                      ),
                      const SizedBox(width: 8),
                      FilledButton(
                        onPressed: () {
                          ref.read(feedbackFilterProvider.notifier).state =
                              filter;
                          Navigator.pop(context);
                        },
                        child: Text(l10n.apply),
                      ),
                    ],
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }
}

class _FeedbackListTile extends StatelessWidget {
  const _FeedbackListTile({required this.feedback, required this.onTap});

  final dynamic feedback;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final isUrgent = feedback.priority?.toLowerCase() == 'yes';
    final isOpen = feedback.status?.toLowerCase() == 'open';

    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(color: Theme.of(context).dividerColor),
          ),
        ),
        child: Row(
          children: [
            Expanded(
              flex: 2,
              child: Text(
                feedback.title ?? l10n.untitledFeedback,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            Expanded(
              child: _StatusChip(
                label: feedback.type ?? '',
                isHighlighted: false,
              ),
            ),
            Expanded(
              child: _StatusChip(
                label: feedback.priority ?? '',
                isHighlighted: isUrgent,
                highlightColor: isUrgent
                    ? Theme.of(context).colorScheme.primary
                    : Colors.amber,
              ),
            ),
            Expanded(
              child: _StatusChip(
                label: feedback.status ?? '',
                isHighlighted: isOpen,
                highlightColor: isOpen
                    ? Theme.of(context).colorScheme.primary
                    : Colors.amber,
              ),
            ),
            Expanded(
              child: Text(
                _formatDate(feedback.openTime),
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _formatDate(int timestamp) {
    if (timestamp == 0) return '';
    final date = DateTime.fromMillisecondsSinceEpoch(timestamp);
    return '${date.day}/${date.month}/${date.year}';
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({
    required this.label,
    required this.isHighlighted,
    this.highlightColor,
  });

  final String label;
  final bool isHighlighted;
  final Color? highlightColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: isHighlighted
            ? (highlightColor ?? Theme.of(context).colorScheme.primary)
            : Colors.grey,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(label, style: TextStyle(color: Colors.white, fontSize: 12)),
    );
  }
}
