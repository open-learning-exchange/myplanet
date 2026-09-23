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
                            onTap: () {
                              _open(filtered[index]);
                            },
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

  /// Where a tapped row goes — the port of `SubmissionsAdapter`'s three-way
  /// branch (`:93-103`):
  ///
  /// ```kotlin
  /// if (count > 1) showAllSubmissions(submission)
  /// else if (type == "survey") openSurvey(listener, submission.id, true, false, "")
  /// else openSubmissionDetail(listener, submission.id)
  /// ```
  ///
  /// **The survey arm had no counterpart here, and it is the only way back
  /// into a half-finished survey from this screen.** Every tap went to the
  /// read-only detail, so a learner who left a survey part-answered and came
  /// to their submissions to finish it was shown what they had answered so
  /// far and given no way to add to it. `home_screen.dart:252` was the port's
  /// one pusher of the resume route, and it only offers the surveys the
  /// reminder dialog lists.
  ///
  /// **Why `status == 'pending'` when Kotlin's arm has no status test.**
  /// Kotlin branches on the *adapter's* type, and an earlier revision of this
  /// comment claimed the row's own type is the same test. It is not:
  /// `getSubmissionProjections` (`SubmissionsRepositoryImpl.kt:100-106`) has
  /// **three** arms, not two, and two of them are all-`type == "survey"` rows:
  ///
  /// ```kotlin
  /// "survey"            -> it.type == "survey"
  /// "survey_submission" -> it.type == "survey" && it.status != "pending"
  /// else                -> it.type != "survey"
  /// ```
  ///
  /// `"survey"` is *My surveys*, whose rows reopen the form. `survey_submission`
  /// is the Survey radio on *My submissions* (`SubmissionsFragment.kt:70-90`,
  /// reached from the no-argument `LifeAdapter.kt:180`), whose rows open the
  /// detail. This screen is the port's only submissions list and has to stand
  /// in for both, so it takes the behaviour they **agree** on: a *pending*
  /// survey sheet appears only in the first list, which resumes it; a finished
  /// one appears in both, and only the second is non-destructive, so it keeps
  /// the detail.
  ///
  /// That is not pedantry about a filter. Without the status test:
  ///
  /// * A completed sheet whose survey has since gone — a pruned adopted clone,
  ///   or one deleted upstream — would open `TakeSurveyScreen`, which reads the
  ///   **live** survey, find no questions and render *This survey has no
  ///   questions*. The learner could no longer see their own answers at all,
  ///   where the detail renders the submission's own stored questions.
  /// * The **team-adoption marker** would be reopened as if it were an answer
  ///   sheet. `createSurveyAdoptionSubmission` writes `type: 'survey'`,
  ///   `status: ''` and the **source** survey's bare id, and `watchForUser`
  ///   filters neither, so it is in this list looking like a half-finished
  ///   survey. Answering it would set `status: 'complete'`, which drops it out
  ///   of `findExistingAdoption`'s `status.isEmpty` test and into
  ///   `pendingUploads` — the adoption record destroyed and the answers
  ///   uploaded stapled to its blobs. Kotlin's *My surveys* arm has the same
  ///   hole, but the port's own `pendingSurveySubmissions` already decided the
  ///   other way ("Adoption records … describe the act of copying a survey,
  ///   not an answer sheet"), and two entry points disagreeing about one row
  ///   is how this project loses data.
  ///
  /// `status` is compared exactly rather than through [_isComplete], because
  /// what is wanted here is *this row is a sheet somebody is part-way through*
  /// — the marker's `''` and a graded exam's `'requires grading'` are both
  /// "not pending" and both belong on the detail.
  ///
  /// The survey id comes off `parentId`, which is `"<surveyId>@<courseId>"`
  /// for a course-attached survey and the bare id otherwise.
  /// [SubmissionsRepository.parentBaseId] is the named port of Kotlin's
  /// `sub?.parentId?.substringBefore("@")` (`BaseExamFragment.kt:90`) and
  /// exists so there is one derivation rather than a split re-implemented per
  /// reader — which is what the first cut of this method did.
  ///
  /// **`count > 1` is still a gap and deliberately left as one:** Kotlin opens
  /// `SubmissionListFragment` (every attempt for that parent, with a PDF
  /// export), which the port has no screen or route for, and a route is not in
  /// this lane's files. Until it exists a grouped row keeps going to the
  /// newest attempt's detail, which is what it did before.
  void _open(SubmissionListEntry entry) {
    final row = entry.row;
    if (entry.count == 1 &&
        (row.type ?? '') == 'survey' &&
        (row.status ?? '') == 'pending') {
      final surveyId = SubmissionsRepository.parentBaseId(row.parentId) ?? '';
      if (surveyId.isNotEmpty) {
        // `push`, never `go`: `_thankAndLeave` pops when it can, and `go`
        // would replace this list rather than stack on it, landing a learner
        // who just finished on the surveys catalog instead of back here. The
        // port already paid for that once — see the note at
        // `take_survey_screen.dart`'s course-step return path.
        context.push(
          '${Routes.surveys}/${Uri.encodeComponent(surveyId)}'
          '?submission=${Uri.encodeComponent(row.id)}',
        );
        return;
      }
    }
    context.push('${Routes.submissions}/${Uri.encodeComponent(row.id)}');
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
    // `ref.read(sessionProvider).value` here dropped the draft the user had
    // just confirmed — no row, no snackbar, nothing — for as long as the
    // session was still loading. Nothing in this screen's tree watches
    // `sessionProvider`, so the only thing resolving it is the router's
    // process-lifetime `ref.listen`, which leaves the window open from app
    // start until the persisted session has been read back. A deep link
    // delivered at cold start lands squarely in it.
    //
    // The `await` is inside the `try` deliberately: a future can reject where
    // `.value` could only be null.
    final UserRow? user;
    try {
      user = await ref.read(sessionProvider.future);
    } catch (_) {
      return;
    }
    if (user == null || !mounted) return;
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
    final title = entry.count > 1 ? '$base (${entry.count})' : base;
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
