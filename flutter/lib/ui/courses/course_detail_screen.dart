import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../data/local/user_mapper.dart';
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
    final userId = ref.watch(sessionProvider).value?.id;

    return Scaffold(
      appBar: AppBar(title: Text(course.value?.courseTitle ?? l10n.courses)),
      body: course.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(l10n.syncFailed('$error'))),
        data: (data) {
          if (data == null) {
            return Center(child: Text(l10n.courseNotFound));
          }
          return _CourseBody(
            course: data,
            steps: steps.value ?? const [],
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
    final session = ref.read(sessionProvider).value;
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
    final user = ref.watch(sessionProvider).value;
    // `UserMapper.isGuestAccount`, the port of `UserEntity.isGuest()` —
    // the id prefix **or** a `guest` role with no `learner` role. The narrow
    // `UserMapper.isGuest` was what this gate read until Phase 149, which
    // matched neither this site's Kotlin nor the other course screen's.
    final isGuest = user != null && UserMapper.isGuestAccount(user);
    final target = (type: 'course', itemId: course.id);
    final rating = ref.watch(ratingSummaryProvider(target)).value;

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
              // Gated on `!isGuest`, as `TakeCourseFragment.setCourseData`
              // (`:216-232`) gates `btnRemove` — Kotlin offers the membership
              // button to a signed-in non-guest and to nobody else.
              //
              // **This is the more consequential of the port's two copies**,
              // and Phase 145 gated the other one first. `take_course_screen`'s
              // `_toggleMembership` writes locally; this one writes *and*
              // pushes the shelf, so an ungated guest tap here reached the
              // server. The guard is `UserMapper.isGuestAccount`, the full
              // `UserEntity.isGuest()`: the id-prefix rule no port writer can
              // currently satisfy, **or** a `guest` role without a `learner`
              // role, which the sync-in writes straight from the account
              // document. Phase 145 landed this gate on the narrow rule and
              // Phase 149 widened it. See `PHASE_149_NOTES.md`.
              if (!isGuest)
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
        // **No subtitle, because Kotlin's row has no second line the learner
        // ever sees.** `CoursesStepsAdapter.bind` (`:51-55`) fills
        // `row_steps.xml`'s `tv_description` from `R.string.test_size` —
        // "This test has %d questions", with `step.questionCount` — and never
        // a resource count: `CourseStep.noOfResources` is written at
        // `CoursesRepositoryImpl.kt:695` and read nowhere in `app/src/main`.
        //
        // The test-size line is not ported either, because it is dead code.
        // `tv_description` is `visibility="gone"` in the layout and
        // `updateDescriptionVisibility` keys on `StepItem.isDescriptionVisible`
        // (default `false`), whose only writer is
        // `CourseDetailViewModel.toggleStepDescription` — reached only from
        // `CourseDetailFragment:140`'s `else`, which cannot run:
        // `CourseDetailFragment` exists only as page 0 of
        // `CoursesPagerAdapter(this@TakeCourseFragment, courseId)`
        // (`CoursesPagerAdapter.kt:44`, `TakeCourseFragment.kt:122`), so
        // `parentFragment as? TakeCourseFragment` is never null and a tap
        // always navigates. Porting a line no user sees would also mean
        // porting its mislabel: `questionCount` is
        // `examDao.getFirstByStepId(stepId)?.noOfQuestions`, a query with no
        // `type` filter, so on a survey-only step it reports the *survey's*
        // question count under "This test has …".
        //
        // Phase 139 item 5 read this slot as having "no analogue" and Phase
        // 145 as showing "the wrong datum". The analogue exists; it renders
        // nothing.
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        children: [
          if (step.description != null && step.description!.isNotEmpty)
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: CourseMarkdownBody(data: step.description!),
            ),
          if (exam.value != null)
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: Padding(
                padding: const EdgeInsets.only(top: 12),
                child: FilledButton.tonalIcon(
                  icon: const Icon(Icons.assignment_turned_in_outlined),
                  label: Text(l10n.takeExam),
                  // `stepNum` is the tile's own 1-based position, passed
                  // explicitly rather than derived from the step's place in a
                  // re-query. There is no Kotlin `stepNum` to copy on this
                  // path — the button itself is a port addition — but the
                  // number is the same one `take_course_screen` sends, and
                  // both come from the ordering `courseStepsProvider` renders,
                  // so the two entries into the same step exam cannot key
                  // `course_progress` differently.
                  onPressed: () => context.push(
                    '/courses/exam/${exam.value!.id}'
                    '?stepId=${step.id}&courseId=${step.courseId ?? ''}'
                    '&stepNum=$number',
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
