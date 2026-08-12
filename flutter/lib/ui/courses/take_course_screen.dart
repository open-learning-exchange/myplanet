import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/courses_providers.dart';
import '../../providers/session_provider.dart';

/// Mints a local key for a new `course_progress` row. Mirrors
/// `RatingsRepository`'s `_defaultId` — the row's identity is reused on
/// re-open once it exists, so this only stamps the first visit.
String _localProgressId() =>
    DateTime.now().microsecondsSinceEpoch.toString();

/// Port of `ui/courses/TakeCourseFragment.kt`.
///
/// Step-by-step course navigation with previous/next buttons and progress tracking.
class TakeCourseScreen extends ConsumerStatefulWidget {
  const TakeCourseScreen({required this.courseId, super.key});
  final String courseId;

  @override
  ConsumerState<TakeCourseScreen> createState() => _TakeCourseScreenState();
}

class _TakeCourseScreenState extends ConsumerState<TakeCourseScreen> {
  int _currentStep = 0;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final course = ref.watch(courseProvider(widget.courseId));
    final steps = ref.watch(courseStepsProvider(widget.courseId));
    final userId = ref.watch(sessionProvider).valueOrNull?.id;

    return Scaffold(
      appBar: AppBar(
        title: Text(course.valueOrNull?.courseTitle ?? l10n.courses),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
      ),
      body: course.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(l10n.syncFailed('$e'))),
        data: (data) {
          if (data == null) {
            return Center(child: Text(l10n.courseNotFound));
          }
          return steps.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => Center(child: Text(l10n.syncFailed('$e'))),
            data: (stepList) {
              if (stepList.isEmpty) {
                return Center(child: Text(l10n.noDataAvailable));
              }
              // Clamp current step to valid range
              if (_currentStep >= stepList.length) {
                _currentStep = stepList.length - 1;
              }
              return _CourseContent(
                course: data,
                steps: stepList,
                currentStep: _currentStep,
                userId: userId,
                onStepChanged: (step) => setState(() => _currentStep = step),
                onCourseUpdated: () {
                  // Refresh course data
                  ref.invalidate(courseProvider(widget.courseId));
                },
              );
            },
          );
        },
      ),
    );
  }
}

class _CourseContent extends ConsumerWidget {
  const _CourseContent({
    required this.course,
    required this.steps,
    required this.currentStep,
    required this.userId,
    required this.onStepChanged,
    required this.onCourseUpdated,
  });

  final CourseRow course;
  final List<CourseStepRow> steps;
  final int currentStep;
  final String? userId;
  final ValueChanged<int> onStepChanged;
  final VoidCallback onCourseUpdated;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isMyCourse = userId != null && course.userId.contains(userId!);

    void goToStep(int index) {
      onStepChanged(index);
      _recordProgress(ref, index);
    }

    return Column(
      children: [
        // Progress indicator
        _ProgressSection(currentStep: currentStep, totalSteps: steps.length),
        // Step content
        Expanded(
          child: PageView.builder(
            itemCount: steps.length,
            controller: PageController(initialPage: currentStep),
            onPageChanged: goToStep,
            itemBuilder: (context, index) => _StepContent(
              step: steps[index],
              stepNumber: index + 1,
              totalSteps: steps.length,
            ),
          ),
        ),
        // Navigation buttons
        _NavigationBar(
          currentStep: currentStep,
          totalSteps: steps.length,
          isMyCourse: isMyCourse,
          onPrevious: currentStep > 0 ? () => goToStep(currentStep - 1) : null,
          onNext: currentStep < steps.length - 1
              ? () => goToStep(currentStep + 1)
              : null,
          onFinish: () => context.pop(),
          onToggleMembership: () => _toggleMembership(context, ref, isMyCourse),
        ),
      ],
    );
  }

  /// Port of `TakeCourseFragment.onPageSelected` — landing on a step records a
  /// `course_progress` row (passed=null: an exam grades it later) and queues
  /// it for upload. The row is keyed by `(courseId, userId, stepNum)`, so a
  /// re-visit upserts in place rather than creating duplicates.
  Future<void> _recordProgress(WidgetRef ref, int index) async {
    final userId = this.userId;
    if (userId == null) return;

    await ref.read(progressRepositoryProvider).saveCourseProgress(
          id: _localProgressId(),
          courseId: course.id,
          userId: userId,
          stepNum: index + 1,
        );
    final config = ref.read(serverConfigProvider);
    if (config != null) {
      await ref
          .read(courseProgressUploaderProvider)
          .queuePending(config: config);
    }
  }

  Future<void> _toggleMembership(
    BuildContext context,
    WidgetRef ref,
    bool isJoined,
  ) async {
    final userId = this.userId;
    if (userId == null) return;

    await ref
        .read(coursesRepositoryProvider)
        .setShelfMembership(course.id, userId, joined: !isJoined);

    onCourseUpdated();
    if (!context.mounted) return;

    final l10n = AppLocalizations.of(context);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(isJoined ? l10n.removedFromCourse : l10n.addedToCourse),
      ),
    );
  }
}

class _ProgressSection extends StatelessWidget {
  const _ProgressSection({required this.currentStep, required this.totalSteps});

  final int currentStep;
  final int totalSteps;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final progress = totalSteps > 0 ? (currentStep + 1) / totalSteps : 0.0;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                currentStep == 0
                    ? l10n.courseDetails
                    : l10n.stepNumber(currentStep + 1),
                style: Theme.of(context).textTheme.titleMedium,
              ),
              Text(
                '${currentStep + 1} / $totalSteps',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ],
          ),
          const SizedBox(height: 8),
          LinearProgressIndicator(
            value: progress,
            minHeight: 8,
            borderRadius: BorderRadius.circular(4),
          ),
        ],
      ),
    );
  }
}

class _StepContent extends StatelessWidget {
  const _StepContent({
    required this.step,
    required this.stepNumber,
    required this.totalSteps,
  });

  final CourseStepRow step;
  final int stepNumber;
  final int totalSteps;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Step header
          Row(
            children: [
              CircleAvatar(child: Text('$stepNumber')),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  step.stepTitle ?? l10n.stepNumber(stepNumber),
                  style: theme.textTheme.titleLarge,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          // Description
          if (step.description?.isNotEmpty == true) ...[
            Text(step.description!, style: theme.textTheme.bodyLarge),
            const SizedBox(height: 16),
          ],
          // Resources count
          if (step.noOfResources > 0) ...[
            Card(
              child: ListTile(
                leading: const Icon(Icons.folder_outlined),
                title: Text(l10n.resourcesInStep(step.noOfResources)),
                trailing: const Icon(Icons.chevron_right),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _NavigationBar extends StatelessWidget {
  const _NavigationBar({
    required this.currentStep,
    required this.totalSteps,
    required this.isMyCourse,
    required this.onPrevious,
    required this.onNext,
    required this.onFinish,
    required this.onToggleMembership,
  });

  final int currentStep;
  final int totalSteps;
  final bool isMyCourse;
  final VoidCallback? onPrevious;
  final VoidCallback? onNext;
  final VoidCallback onFinish;
  final VoidCallback onToggleMembership;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Container(
      padding: const EdgeInsets.all(16),
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
            // Leave/Join button
            OutlinedButton.icon(
              onPressed: onToggleMembership,
              icon: Icon(
                isMyCourse ? Icons.bookmark_remove : Icons.bookmark_add,
              ),
              label: Text(isMyCourse ? l10n.leaveCourse : l10n.joinCourse),
            ),
            const Spacer(),
            // Previous button
            if (onPrevious != null)
              FilledButton.tonalIcon(
                onPressed: onPrevious,
                icon: const Icon(Icons.arrow_back),
                label: Text(l10n.previous),
              ),
            const SizedBox(width: 8),
            // Next/Finish button
            if (currentStep < totalSteps - 1 && onNext != null)
              FilledButton.icon(
                onPressed: onNext,
                icon: const Icon(Icons.arrow_forward),
                label: Text(l10n.next),
              )
            else
              FilledButton.icon(
                onPressed: onFinish,
                icon: const Icon(Icons.check),
                label: Text(l10n.finish),
              ),
          ],
        ),
      ),
    );
  }
}
