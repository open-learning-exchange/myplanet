import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/courses_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/ratings_provider.dart';
import '../ratings/rating_dialog.dart';
import '../router.dart';
import 'course_markdown.dart';

/// Port of `ui/courses/CourseDetailFragment.kt`.
///
/// Shows the course header and its ordered steps. Taking a course
/// (`TakeCourseFragment`) and step progress arrive with the progress and exam
/// packages — see `docs/kotlin-to-flutter-migration.md`.
class CourseDetailScreen extends ConsumerWidget {
  const CourseDetailScreen({required this.courseId, super.key});

  final String courseId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final course = ref.watch(courseProvider(courseId));
    final steps = ref.watch(courseStepsProvider(courseId));
    final userId = ref.watch(sessionProvider).valueOrNull?.id;

    return Scaffold(
      appBar: AppBar(
        title: Text(course.valueOrNull?.courseTitle ?? l10n.courses),
      ),
      body: course.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(l10n.syncFailed('$error'))),
        data: (data) {
          if (data == null) {
            return Center(child: Text(l10n.courseNotFound));
          }
          return _CourseBody(
            course: data,
            steps: steps.valueOrNull ?? const [],
            userId: userId,
          );
        },
      ),
    );
  }
}

class _CourseBody extends ConsumerWidget {
  const _CourseBody({
    required this.course,
    required this.steps,
    required this.userId,
  });

  final CourseRow course;
  final List<CourseStepRow> steps;
  final String? userId;

  /// Writes membership locally, then pushes the shelf.
  ///
  /// The local write is what the UI reflects, so this works offline. The push
  /// is best-effort: if it fails the local state stands and the next successful
  /// shelf upload carries it, because the payload is recomputed from the
  /// database rather than queued.
  Future<void> _toggleMembership(WidgetRef ref, {required bool joined}) async {
    final id = userId;
    if (id == null) return;

    await ref
        .read(coursesRepositoryProvider)
        .setShelfMembership(course.id, id, joined: joined);

    final config = ref.read(serverConfigProvider);
    final session = ref.read(sessionProvider).valueOrNull;
    if (config == null || session?.couchId == null) return;

    await ref
        .read(shelfRepositoryProvider)
        .upload(config: config, userId: id, shelfDocId: session!.couchId!);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final isMyCourse = userId != null && course.userId.contains(userId);
    final target = (type: 'course', itemId: course.id);
    final rating = ref.watch(ratingSummaryProvider(target)).valueOrNull;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (course.coverFileName != null &&
            course.coverFileName!.isNotEmpty) ...[
          CourseDetailCoverImage(
            courseId: course.id,
            coverFileName: course.coverFileName!,
          ),
          const SizedBox(height: 16),
        ],
        Text(course.courseTitle ?? '', style: theme.textTheme.headlineSmall),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            if (course.gradeLevel != null && course.gradeLevel!.isNotEmpty)
              Chip(label: Text(course.gradeLevel!)),
            if (course.subjectLevel != null && course.subjectLevel!.isNotEmpty)
              Chip(label: Text(course.subjectLevel!)),
            if (course.languageOfInstruction != null &&
                course.languageOfInstruction!.isNotEmpty)
              Chip(label: Text(course.languageOfInstruction!)),
          ],
        ),
        if (course.description != null && course.description!.isNotEmpty) ...[
          const SizedBox(height: 16),
          CourseMarkdownBody(data: course.description!),
        ],
        const SizedBox(height: 16),
        if (userId != null)
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              FilledButton.icon(
                onPressed: steps.isNotEmpty
                    ? () => context.push('${Routes.courses}/${course.id}/take')
                    : null,
                icon: const Icon(Icons.play_arrow),
                label: Text(l10n.takeCourse),
              ),
              FilledButton.tonalIcon(
                onPressed: () => _toggleMembership(ref, joined: !isMyCourse),
                icon: Icon(
                  isMyCourse ? Icons.bookmark_remove : Icons.bookmark_add,
                ),
                label: Text(isMyCourse ? l10n.leaveCourse : l10n.joinCourse),
              ),
              OutlinedButton.icon(
                onPressed: () => showDialog<void>(
                  context: context,
                  builder: (context) => RatingDialog(
                    target: target,
                    title: course.courseTitle ?? l10n.courses,
                  ),
                ),
                icon: Icon(
                  rating?.userRating == null ? Icons.star_border : Icons.star,
                ),
                label: Text(
                  rating == null || rating.total == 0
                      ? l10n.rateCourse
                      : l10n.ratingCompact(rating.average, rating.total),
                ),
              ),
            ],
          ),
        const SizedBox(height: 24),
        Text(
          l10n.courseSteps(steps.length),
          style: theme.textTheme.titleMedium,
        ),
        const SizedBox(height: 8),
        for (var i = 0; i < steps.length; i++)
          _StepTile(step: steps[i], number: i + 1),
      ],
    );
  }
}

class _StepTile extends ConsumerWidget {
  const _StepTile({required this.step, required this.number});

  final CourseStepRow step;
  final int number;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    // **A port addition, not a port of anything.** Kotlin's
    // `CourseDetailFragment` has no per-step exam button — page 0 of its pager
    // shows an exam *count* (`CourseDetailFragment.kt:86-88`,
    // `countByCourseIdAndType(courseId, "courses")`) and the button itself
    // lives on `CourseStepFragment`, which the port reaches through
    // `take_course_screen`. An earlier comment here claimed this was "exactly
    // as `TakeCourseFragment` is in the Kotlin"; it is a second, more direct
    // entry the port offers. Note it therefore carries none of
    // `CourseStepFragment`'s gating — no `userHasCourse`, no already-submitted
    // relabelling.
    final exam = ref.watch(stepExamProvider(step.id));

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ExpansionTile(
        leading: CircleAvatar(child: Text('$number')),
        title: Text(step.stepTitle ?? l10n.stepNumber(number)),
        subtitle: Text(l10n.resourcesInStep(step.noOfResources)),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        children: [
          if (step.description != null && step.description!.isNotEmpty)
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: CourseMarkdownBody(data: step.description!),
            ),
          if (exam.valueOrNull != null)
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: Padding(
                padding: const EdgeInsets.only(top: 12),
                child: FilledButton.tonalIcon(
                  icon: const Icon(Icons.assignment_turned_in_outlined),
                  label: Text(l10n.takeExam),
                  onPressed: () => context.push(
                    '/courses/exam/${exam.value!.id}'
                    '?stepId=${step.id}&courseId=${step.courseId ?? ''}',
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// The cover banner on the course detail screen — port of
/// `CourseDetailFragment.setCourseCover`.
///
/// The Kotlin loads a local file (`MyCourse.getCoverImageFile`) when the sync
/// has downloaded one, otherwise the CouchDB `courses/<id>/<cover>` attachment
/// via Glide with the `satellite` auth header. The Flutter port reuses the
/// authenticated [courseCoverImageProvider] (the same bytes path the grid tile
/// uses), since a CouchDB attachment cannot be loaded with `Image.network`.
/// The local-file branch is deferred until the cover download lands in the
/// sync; the bytes fetch works online and shrinks on failure, matching the
/// Kotlin's `courseCover.visibility = GONE`.
class CourseDetailCoverImage extends ConsumerWidget {
  const CourseDetailCoverImage({
    required this.courseId,
    required this.coverFileName,
    super.key,
  });

  final String courseId;
  final String coverFileName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final image = ref.watch(
      courseCoverImageProvider(
        CourseCoverImageRequest(
          courseId: courseId,
          coverFileName: coverFileName,
        ),
      ),
    );
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: image.when(
          data: (bytes) => bytes == null || bytes.isEmpty
              ? const SizedBox.shrink()
              : Image.memory(bytes, fit: BoxFit.cover),
          loading: () => ColoredBox(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
          ),
          error: (_, _) => const SizedBox.shrink(),
        ),
      ),
    );
  }
}
