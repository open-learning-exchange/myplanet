import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/sync/sync_result.dart';
import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/submissions_provider.dart';
import '../../providers/session_provider.dart';
import '../../repository/personals_uploader.dart';
import '../../repository/submissions_repository.dart';
import '../router.dart';

/// Port of `ui/submissions/SubmissionsFragment.kt` and `SubmissionsAdapter.kt`.
class SubmissionsScreen extends ConsumerStatefulWidget {
  const SubmissionsScreen({super.key});

  @override
  ConsumerState<SubmissionsScreen> createState() => _SubmissionsScreenState();
}

class _SubmissionsScreenState extends ConsumerState<SubmissionsScreen> {
  String _filter = 'all';
  bool _syncing = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final submissions = ref.watch(submissionsProvider);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.submissions),
        actions: [
          IconButton(
            tooltip: l10n.refresh,
            onPressed: _syncing ? null : _sync,
            icon: _syncing
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.sync),
          ),
        ],
      ),
      body: submissions.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.submissionsUnavailable)),
        data: (rows) {
          // Kotlin filters first and collapses second
          // (`SubmissionViewModel.kt:55-73`), so a completed attempt does not
          // suppress the pending one on the Pending tab.
          final filtered = collapseSubmissionsByParent(switch (_filter) {
            'pending' => rows.where((row) => !_isComplete(row)).toList(),
            'complete' => rows.where(_isComplete).toList(),
            _ => rows,
          });
          return Column(
            children: [
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
                child: SegmentedButton<String>(
                  segments: [
                    ButtonSegment(value: 'all', label: Text(l10n.all)),
                    ButtonSegment(value: 'pending', label: Text(l10n.pending)),
                    ButtonSegment(
                      value: 'complete',
                      label: Text(l10n.complete),
                    ),
                  ],
                  selected: {_filter},
                  onSelectionChanged: (value) =>
                      setState(() => _filter = value.single),
                ),
              ),
              Expanded(
                child: filtered.isEmpty
                    ? Center(
                        child: Text(
                          rows.isEmpty
                              ? l10n.noSubmissions
                              : l10n.noMatchingSubmissions,
                        ),
                      )
                    : RefreshIndicator(
                        onRefresh: _sync,
                        child: ListView.separated(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          itemCount: filtered.length,
                          separatorBuilder: (_, _) => const Divider(height: 1),
                          itemBuilder: (context, index) => _SubmissionTile(
                            filtered[index],
                            onTap: () => context.push(
                              '${Routes.submissions}/'
                              '${Uri.encodeComponent(filtered[index].row.id)}',
                            ),
                          ),
                        ),
                      ),
              ),
            ],
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _createDraft,
        icon: const Icon(Icons.add),
        label: Text(l10n.newSubmission),
      ),
    );
  }

  Future<void> _sync() async {
    final config = ref.read(serverConfigProvider);
    if (config == null || _syncing) return;
    setState(() => _syncing = true);
    final result = await ref
        .read(submissionsRepositoryProvider)
        .sync(config: config);
    if (!mounted) return;
    setState(() => _syncing = false);
    final message = switch (result) {
      SyncComplete(:final savedCount) => AppLocalizations.of(
        context,
      ).submissionsSynced(savedCount),
      SyncFailed(:final message) => message,
    };
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _createDraft() async {
    final l10n = AppLocalizations.of(context);
    final title = TextEditingController();
    final answer = TextEditingController();
    final submitted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.newSubmission),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: title,
              autofocus: true,
              decoration: InputDecoration(labelText: l10n.title),
            ),
            TextField(
              controller: answer,
              decoration: InputDecoration(labelText: l10n.answer),
              minLines: 2,
              maxLines: 4,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.save),
          ),
        ],
      ),
    );
    if (submitted != true || title.text.trim().isEmpty || !mounted) return;
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    await ref
        .read(submissionsRepositoryProvider)
        .createDraft(
          userId: user.id,
          type: 'submission',
          title: title.text,
          answers: [
            if (answer.text.trim().isNotEmpty)
              SubmissionDraftAnswer(value: answer.text.trim()),
          ],
        );
    final config = ref.read(serverConfigProvider);
    if (config != null) {
      await ref
          .read(submissionsUploaderProvider)
          .queuePending(config: config, userId: user.id);
      await ref
          .read(outboxDrainerProvider)
          .drain(authHeader: PersonalsUploader.authHeaderFor(config));
    }
  }
}

/// Whether a submission is finished from the learner's side.
///
/// `requires grading` is on the list because that is the status
/// `saveExamAnswer` gives a **finished exam** — `complete` is the survey
/// value, and an exam is not complete until somebody marks it. Without it a
/// submitted exam would sit under Pending until its upload landed, which is
/// the opposite of what the learner just did.
/// `SurveysRepositoryImpl.kt:310` treats the two the same way
/// (`status == "complete" || status == "requires grading"`).
bool _isComplete(SubmissionRow row) =>
    row.uploaded ||
    const {
      'complete',
      'completed',
      'graded',
      'requires grading',
    }.contains(row.status?.trim().toLowerCase());

class _SubmissionTile extends StatelessWidget {
  const _SubmissionTile(this.entry, {required this.onTap});
  final SubmissionListEntry entry;
  final VoidCallback onTap;

  SubmissionRow get row => entry.row;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final parentTitle = submissionDisplayTitle(row);
    final base = parentTitle?.trim().isNotEmpty == true
        ? parentTitle!
        : row.type?.trim().isNotEmpty == true
        ? row.type!
        : l10n.submission;
    // `SubmissionsAdapter.updateSubmissionCount` shows `(N)` beside the title
    // when a parent has more than one attempt.
    final title = entry.count > 1 ? '\$base (\${entry.count})' : base;
    final updated = row.lastUpdateTime == 0
        ? null
        : DateFormat.yMMMd().add_jm().format(
            DateTime.fromMillisecondsSinceEpoch(row.lastUpdateTime),
          );
    return ListTile(
      onTap: onTap,
      leading: Icon(
        row.uploaded ? Icons.assignment_turned_in : Icons.assignment_outlined,
      ),
      title: Text(title),
      subtitle: Text(
        [
          row.status?.trim().isNotEmpty == true ? row.status! : l10n.pending,
          ?updated,
        ].join(' • '),
      ),
      trailing: row.grade > 0 ? Text(l10n.submissionGrade(row.grade)) : null,
    );
  }
}
