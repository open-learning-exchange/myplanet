import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';

import '../../providers/courses_providers.dart';
import '../../providers/activities_provider.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../ratings/rating_dialog.dart';
import '../router.dart';
import 'course_markdown.dart';

/// Mints a local key for a new `course_progress` row. Mirrors
/// `RatingsRepository`'s `_defaultId` — the row's identity is reused on
/// re-open once it exists, so this only stamps the first visit.
String _localProgressId() => DateTime.now().microsecondsSinceEpoch.toString();

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

  /// One `course_activity` row per open. `TakeCourseFragment` logs the visit
  /// from `setData`, which can run again on a rebuild; this fires once per
  /// mount, which is what the Kotlin means by a visit.
  bool _visitLogged = false;

  void _logVisitOnce(CourseRow course) {
    if (_visitLogged) return;
    _visitLogged = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      // The Kotlin passes `currentCourse.courseId` — the server's id for the
      // course, which is what the server's `course_activities` documents key
      // on — falling back to the local row key when a course has none.
      ref
          .read(activityLogProvider)
          .logCourseVisit(
            courseId: course.courseId ?? course.id,
            title: course.courseTitle,
          );
    });
  }

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
          _logVisitOnce(data);
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
              isMyCourse: isMyCourse,
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
          onFinish: () => _onFinish(context, ref),
          onToggleMembership: () => _toggleMembership(context, ref, isMyCourse),
        ),
      ],
    );
  }

  /// Port of `CourseStepFragment.saveCourseProgress` — landing on a step
  /// records a `course_progress` row and queues it for upload. The row is keyed
  /// by `(courseId, userId, stepNum)`, so a re-visit upserts in place rather
  /// than creating duplicates.
  ///
  /// `passed` is `if (stepExams.isEmpty()) true else null`
  /// (`CourseStepFragment.kt:97`): a step with no test is passed by reaching
  /// it, and a step with one waits for the exam to grade it. The port passed
  /// `null` unconditionally, because until Phase 113 nothing could tell the two
  /// apart — `stepExams` is `getByStepIdAndType(stepId, "courses")`, the join
  /// that did not exist. A course with no tests could therefore never complete.
  /// Surveys are deliberately not consulted: Kotlin reads `stepExams` here, not
  /// `stepSurvey`.
  Future<void> _recordProgress(WidgetRef ref, int index) async {
    final userId = this.userId;
    if (userId == null) return;

    final exam = await ref.read(stepExamProvider(steps[index].id).future);

    await ref
        .read(progressRepositoryProvider)
        .saveCourseProgress(
          id: _localProgressId(),
          courseId: course.id,
          userId: userId,
          stepNum: index + 1,
          passed: exam == null ? true : null,
        );
    final config = ref.read(serverConfigProvider);
    if (config != null) {
      await ref
          .read(courseProgressUploaderProvider)
          .queuePending(config: config);
    }
  }

  /// Port of `TakeCourseFragment.onFinishStep` /
  /// `showCourseRatingDialogAndFinish`. Finishing a course pops the screen,
  /// but first — if the user has not rated this course yet — offers the
  /// rating dialog, exactly as the Kotlin does on its finish step. The dialog
  /// dismiss (submit or cancel) pops the course, matching the Kotlin's
  /// `setOnDismissListener`.
  ///
  /// One specific course (the MyPlanet Onboarding course) is gated behind an
  /// unfinished-survey toast before reaching the rating dialog — matching the
  /// Kotlin's `MANDATORY_SURVEY_COURSE_ID` check in `onFinishStep`.
  static const String _mandatorySurveyCourseId =
      '4e6b78800b6ad18b4e8b0e1e38a98cac';

  Future<void> _onFinish(BuildContext context, WidgetRef ref) async {
    final userId = this.userId;

    if (course.id == _mandatorySurveyCourseId && userId != null) {
      final hasUnfinished = await ref
          .read(submissionsRepositoryProvider)
          .hasUnfinishedSurveys(course.id, userId);
      if (!context.mounted) return;
      if (hasUnfinished) {
        final l10n = AppLocalizations.of(context);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(l10n.pleaseCompleteSurvey)));
        return;
      }
    }

    final summary = await ref
        .read(ratingsRepositoryProvider)
        .summary('course', course.id, userId);
    if (!context.mounted) return;

    final hasRated = summary.userRating != null;
    if (!hasRated) {
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => RatingDialog(
          target: (type: 'course', itemId: course.id),
          title: course.courseTitle ?? '',
        ),
      );
      if (!context.mounted) return;
    }
    context.pop();
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

class _StepContent extends ConsumerWidget {
  const _StepContent({
    required this.step,
    required this.stepNumber,
    required this.totalSteps,
    required this.isMyCourse,
  });

  final CourseStepRow step;
  final int stepNumber;
  final int totalSteps;

  /// `CourseStepFragment.onViewCreated` hides **both** assessment buttons when
  /// the user has not joined the course, after `hideTestIfNoQuestion` has shown
  /// them. Without this the port offered a test on a course the learner is only
  /// browsing.
  final bool isMyCourse;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final exam = isMyCourse
        ? ref.watch(stepExamProvider(step.id)).valueOrNull
        : null;
    final surveys = isMyCourse
        ? ref.watch(stepSurveysProvider(step.id)).valueOrNull
        : null;

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
          // Description — rendered as markdown so images and formatting
          // authored in the step description appear, matching
          // `CourseStepFragment`'s `prependBaseUrlToImages` + `setMarkdownText`.
          if (step.description?.isNotEmpty == true) ...[
            CourseMarkdownBody(data: step.description!),
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
            const SizedBox(height: 8),
          ],
          // Take Test button — port of CourseStepFragment's btnTakeTest
          if (exam != null) ...[
            Card(
              child: ListTile(
                leading: const Icon(Icons.quiz_outlined),
                title: Text(l10n.takeTest),
                trailing: const Icon(Icons.chevron_right),
                // `Routes.exam` is the *pattern* `/courses/exam/:examId`;
                // appending the id to it produced
                // `/courses/exam/:examId/<id>`, which matches no route and
                // dropped the learner on go_router's error page. The exam
                // screen wants the step and course as query parameters, as
                // `CourseStepFragment` passes `stepId`/`stepNum`.
                onTap: () => context.push(
                  '/courses/exam/${exam.id}'
                  '?stepId=${step.id}&courseId=${step.courseId ?? ''}',
                ),
              ),
            ),
            const SizedBox(height: 8),
          ],
          // Take Survey button — port of CourseStepFragment's btnTakeSurvey
          if (surveys != null && surveys.isNotEmpty) ...[
            Card(
              child: ListTile(
                leading: const Icon(Icons.assignment_outlined),
                title: Text(l10n.recordSurvey),
                trailing: const Icon(Icons.chevron_right),
                // Kotlin's `btnTakeSurvey` calls
                // `SubmissionsAdapter.openSurvey(…, stepSurvey[0].id, …)`,
                // which opens `ExamTakingFragment` against the signed-in
                // user's own submission — not the anonymous public-survey
                // path. `Routes.publicSurvey` was both the wrong screen and,
                // being a pattern rather than a base, an unmatchable route: a
                // course-step survey has no `teamId`, so the interpolation
                // left an empty segment too.
                onTap: () =>
                    context.push('${Routes.surveys}/${surveys.first.id}'),
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
