import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/courses_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/ratings_provider.dart';
import '../ratings/rating_dialog.dart';

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
          Text(course.description!, style: theme.textTheme.bodyMedium),
        ],
        const SizedBox(height: 16),
        if (userId != null)
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
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

class _StepTile extends StatelessWidget {
  const _StepTile({required this.step, required this.number});

  final CourseStepRow step;
  final int number;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

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
              child: Text(step.description!),
            ),
        ],
      ),
    );
  }
}
