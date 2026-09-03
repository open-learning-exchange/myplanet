import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/courses_providers.dart';
import '../router.dart';

/// Port of `ui/courses/CoursesProgressFragment.kt`.
///
/// Displays a list of the user's enrolled courses with their progress.
class CoursesProgressScreen extends ConsumerWidget {
  const CoursesProgressScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final progress = ref.watch(courseProgressStreamProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.myProgress)),
      body: progress.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(l10n.unavailable)),
        data: (rows) {
          if (rows.isEmpty) {
            return Center(child: Text(l10n.noEnrolledCourses));
          }
          return ListView.separated(
            padding: const EdgeInsets.all(12),
            itemCount: rows.length,
            separatorBuilder: (context, index) => const SizedBox(height: 8),
            itemBuilder: (context, index) =>
                _CourseProgressCard(row: rows[index]),
          );
        },
      ),
    );
  }
}

class _CourseProgressCard extends StatelessWidget {
  const _CourseProgressCard({required this.row});
  final CoursesProgressRow row;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final current = row.progressCurrent ?? 0;
    final max = row.progressMax ?? 0;
    final progress = max > 0 ? current / max : 0.0;

    // `CoursesProgressAdapter.onBindViewHolder` installs the click listener
    // **inside** `if (item.progressCurrent != null && item.progressMax != null)`
    // — a course whose progress could not be computed is inert — and it opens
    // `CourseProgressActivity`, the per-step grid. This used to push the course
    // *detail* screen, which shows none of that.
    final canOpenGrid = row.progressCurrent != null && row.progressMax != null;

    return Card(
      child: InkWell(
        onTap: canOpenGrid
            ? () => context.push(
                '${Routes.myProgress}/${Uri.encodeComponent(row.courseId)}',
              )
            : null,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  // Mistakes badge
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primary,
                      shape: BoxShape.circle,
                    ),
                    child: Center(
                      child: Text(
                        '${row.mistakes ?? 0}',
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  // Course name and progress
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          row.courseName,
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        if (max > 0) ...[
                          const SizedBox(height: 4),
                          Text(
                            l10n.stepProgress(current, max),
                            style: theme.textTheme.bodyMedium,
                          ),
                        ],
                      ],
                    ),
                  ),
                  if (canOpenGrid) const Icon(Icons.chevron_right),
                ],
              ),
              if (max > 0) ...[
                const SizedBox(height: 12),
                LinearProgressIndicator(
                  value: progress,
                  borderRadius: BorderRadius.circular(4),
                ),
              ],
              // Step mistakes section
              if (row.stepMistakes != null && row.stepMistakes!.isNotEmpty) ...[
                const SizedBox(height: 12),
                _StepMistakesTable(stepMistakes: row.stepMistakes!, l10n: l10n),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _StepMistakesTable extends StatelessWidget {
  const _StepMistakesTable({required this.stepMistakes, required this.l10n});

  /// Keyed by the exam's 0-based ordinal within the course, as
  /// `submissionMap` builds it; the row label is `key + 1`.
  final Map<int, int> stepMistakes;
  final AppLocalizations l10n;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerHighest,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  l10n.step,
                  style: theme.textTheme.labelMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                  textAlign: TextAlign.center,
                ),
              ),
              Expanded(
                child: Text(
                  l10n.mistakes,
                  style: theme.textTheme.labelMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                  textAlign: TextAlign.center,
                ),
              ),
            ],
          ),
        ),
        ...stepMistakes.entries.map((entry) {
          // `stepView.text = "${stepKey.toInt().plus(1)}"`.
          final stepNum = entry.key + 1;
          return Container(
            padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 8),
            decoration: BoxDecoration(
              border: Border(
                bottom: BorderSide(color: theme.dividerColor, width: 1),
              ),
            ),
            child: Row(
              children: [
                Expanded(child: Text('$stepNum', textAlign: TextAlign.center)),
                Expanded(
                  child: Text(
                    '${entry.value}',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: entry.value > 0
                          ? theme.colorScheme.error
                          : theme.colorScheme.primary,
                    ),
                  ),
                ),
              ],
            ),
          );
        }),
      ],
    );
  }
}
