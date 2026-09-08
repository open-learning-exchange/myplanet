import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../data/local/user_mapper.dart';
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

class _CourseContent extends ConsumerStatefulWidget {
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
  ConsumerState<_CourseContent> createState() => _CourseContentState();
}

class _CourseContentState extends ConsumerState<_CourseContent> {
  /// The step index whose `course_progress` row has already been recorded for
  /// this mount, so a rebuild is not a re-visit.
  int? _recordedStep;

  /// **Owned by the state, and that is a fix rather than a tidy-up.** The
  /// controller used to be built inline in `build` as
  /// `PageController(initialPage: currentStep)`, which meant Previous and Next
  /// moved the step *counter* and left the page where it was: `Scrollable`
  /// hands a replacement controller the existing `ScrollPosition`'s pixels
  /// (`createScrollPosition(..., oldPosition: _position)`), so `initialPage` is
  /// read once at first attach and ignored on every rebuild after it.
  ///
  /// Nothing caught it because nothing asserted on the page. The tests here
  /// checked the counter, which is driven by the same `currentStep` the
  /// controller was being handed — so the two agreed about the number while
  /// disagreeing about what was on screen. Found by a `stepNum` test that
  /// tapped the second step's assessment tile and could not find it.
  ///
  /// Kotlin drives the pager explicitly for the same reason: `onClick` does
  /// `binding.viewPager2.currentItem += 1` (`TakeCourseFragment.kt:372`) and
  /// `navigateToStep` calls `setCurrentItem(index + 1, true)` (`:293`).
  late final PageController _pageController = PageController(
    initialPage: widget.currentStep,
  );

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  /// **Stateful for one reason: the step the learner *lands on* has to be
  /// recorded, and a page-change callback never fires for it.**
  ///
  /// Kotlin records the row twice over, neither requiring a page change:
  /// `CourseStepFragment.onViewCreated` runs `launchSaveCourseProgress()`
  /// inside `withResumed` (`:168-172`), and `setMenuVisibility` runs it again
  /// when the pager brings a step into view (`:262-268`). Both are gated on
  /// `userHasCourse`, which is `isMyCourse(userId, step.courseId)`.
  ///
  /// The port drove `_recordProgress` from `goToStep` alone —
  /// `PageView.onPageChanged` plus the Previous/Next buttons — and
  /// `onPageChanged` does not fire for the initial page. Kotlin's pager has a
  /// course-cover page at position 0, so its first *step* is always arrived
  /// at; this `PageView` opens directly on step 1. Nothing recorded step 1
  /// until the learner navigated away and back, which made three readers wrong
  /// (`getCurrentProgress` and `courseProgressSummary` under-report by one
  /// step, `completedCourseIds` cannot see the step at all) and meant a
  /// **one-step course could never complete**, since the write that would pass
  /// its only step never ran. It also silently no-op'd the exam-finish write
  /// this phase added, whose `UPDATE` needs the row to exist.
  @override
  void initState() {
    super.initState();
    _recordCurrentStep();
  }

  /// Unconditional, not gated on `currentStep` having changed, and that is
  /// load-bearing. `userId` comes from the parent's
  /// `ref.watch(sessionProvider).valueOrNull?.id`, so it is **null on the
  /// first frame** — `_isMyCourse` is false, `initState`'s attempt bails, and
  /// the rebuild that carries the resolved session does not change
  /// `currentStep`. A `currentStep`-only gate therefore recorded nothing at
  /// all, which is how the first cut of this fix still failed its own test.
  /// [_recordCurrentStep] is idempotent instead: it sets `_recordedStep` only
  /// *after* the membership gate passes, so a bail leaves the attempt to be
  /// retried on the next rebuild.
  @override
  void didUpdateWidget(_CourseContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    _recordCurrentStep();
  }

  // The methods below this class's `build` were written against
  // `_CourseContent`'s own fields; these keep them reading the same way now
  // that the fields live on `widget`.
  CourseRow get course => widget.course;
  List<CourseStepRow> get steps => widget.steps;
  String? get userId => widget.userId;
  VoidCallback get onCourseUpdated => widget.onCourseUpdated;

  bool get _isMyCourse =>
      widget.userId != null && widget.course.userId.contains(widget.userId!);

  void _recordCurrentStep() {
    // `userHasCourse` gates both Kotlin triggers: browsing a course you have
    // not joined leaves no progress rows behind.
    if (!_isMyCourse) return;
    final index = widget.currentStep;
    if (index < 0 || index >= widget.steps.length) return;
    if (_recordedStep == index) return;
    _recordedStep = index;
    // After the frame, like `withResumed` — `initState` is too early to touch
    // providers, and the row is not needed to draw anything.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _recordProgress(ref, index);
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final currentStep = widget.currentStep;
    final onStepChanged = widget.onStepChanged;
    final isMyCourse = _isMyCourse;
    final lock = _nextStepLock(currentStep);

    // `setCourseData:216-232` shows `btnRemove` for `!isGuest && !containsUserId`
    // only, so Kotlin offers the membership button to a signed-in non-member
    // and to nobody else. Watched rather than derived from `widget.userId`,
    // because the predicate reads the row's roles and both id columns where
    // `isGuestId` reads one string; the parent already watches this provider,
    // so the value is resolved by the time this builds.
    //
    // `isGuestAccount`, not `isGuest`: the Kotlin here is `isGuest()`
    // (`TakeCourseFragment:213`), which is the id prefix **or** a `guest` role
    // with no `learner` role. Phase 145 landed this gate on the narrow rule,
    // so a role-only guest was offered a button Kotlin withholds.
    final user = ref.watch(sessionProvider).valueOrNull;
    final canChangeMembership =
        user != null && !UserMapper.isGuestAccount(user);

    // The recording moved to [_recordCurrentStep], which covers this and the
    // step the screen opens on alike.
    //
    // Moves the pager as well as the index, because with the controller held
    // in state nothing else does — see [_pageController]. `animateToPage`
    // rather than `jumpToPage` to match `viewPager2.currentItem += 1`, whose
    // ViewPager2 default is a smooth scroll.
    // `hasClients` cannot be false at either call site — both are button
    // `onPressed` callbacks, so the controller is attached — and if it ever
    // were, this would perform half the operation: the index would move and
    // the page would not, which is exactly the defect [_pageController]
    // documents. Guarded rather than left to throw because a throw out of a
    // tap handler is worse for the learner than a stuck page, and the state
    // that would cause it is unreachable.
    void goToStep(int index) {
      onStepChanged(index);
      if (_pageController.hasClients) {
        _pageController.animateToPage(
          index,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeInOut,
        );
      }
    }

    /// `onClick`'s `R.id.next_step` arm (`TakeCourseFragment.kt:366-376`): a
    /// locked step shows the message and returns **before** advancing. The
    /// button stays enabled — Kotlin never touches `isEnabled`, and a disabled
    /// button would say "you cannot go on" without saying why.
    void onNext() {
      if (lock?.locked ?? false) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              lock!.blockedByTest
                  ? l10n.pleaseCompleteTest
                  : l10n.pleaseCompleteSurvey,
            ),
          ),
        );
        return;
      }
      goToStep(currentStep + 1);
    }

    return Column(
      children: [
        // Progress indicator
        _ProgressSection(currentStep: currentStep, totalSteps: steps.length),
        // Step content
        Expanded(
          child: PageView.builder(
            itemCount: steps.length,
            controller: _pageController,
            // Reports the landing rather than re-driving the controller: a
            // programmatic `animateToPage` fires this too, and routing it back
            // through `goToStep` would call `animateToPage` from inside its own
            // completion.
            onPageChanged: onStepChanged,
            // `binding.viewPager2.isUserInputEnabled = false`
            // (`TakeCourseFragment.kt:137`). Kotlin advances the course by its
            // two buttons and nothing else — the SeekBar that looks like a
            // third way is inside `ll_progress`, which is `gone` in the layout
            // and never made visible by any code, so its `onProgressChanged`
            // jump is dead UI.
            //
            // Load-bearing for the lock above rather than cosmetic: a swipe
            // goes straight to `onPageChanged`, so while this `PageView`
            // scrolled, the refusal to advance an unanswered step could be
            // got past by dragging the page.
            physics: const NeverScrollableScrollPhysics(),
            itemBuilder: (context, index) => _StepContent(
              step: steps[index],
              stepNumber: index + 1,
              totalSteps: steps.length,
              isMyCourse: isMyCourse,
              userId: userId,
            ),
          ),
        ),
        // Navigation buttons
        _NavigationBar(
          currentStep: currentStep,
          totalSteps: steps.length,
          isMyCourse: isMyCourse,
          canChangeMembership: canChangeMembership,
          onPrevious: currentStep > 0 ? () => goToStep(currentStep - 1) : null,
          onNext: currentStep < steps.length - 1 ? onNext : null,
          onFinish: () => _onFinish(context, ref),
          onToggleMembership: () => _toggleMembership(context, ref, isMyCourse),
        ),
      ],
    );
  }

  /// The lock on advancing past [currentStep], or null when nothing can lock
  /// it. Port of the `if` that wraps `changeNextButtonState`'s whole body
  /// (`TakeCourseFragment.kt:315-338`); [stepNextLockProvider] carries the
  /// decision itself and documents it.
  ///
  /// Three reasons this returns null, in Kotlin's own terms:
  ///
  ///  * **Any other course.** `:316` compares against a hardcoded
  ///    `"4e6b78800b6ad18b4e8b0e1e38a98cac"` — the same value as the
  ///    `MANDATORY_SURVEY_COURSE_ID` companion constant, which only the finish
  ///    gate reads — and `:336` is an unconditional `isNextStepLocked = false`
  ///    for everything else. Kotlin compares the *navigation* argument
  ///    (`arguments.getString("id")`, `:61`) rather than the loaded row;
  ///    `course.id` is that same value, since the row is looked up by it.
  ///  * **A step index out of range**, which is `steps.getOrNull(position - 1)`
  ///    returning null and `isStepCompleted(null, …)` answering true (`:317`,
  ///    `SubmissionsRepositoryImpl.kt:360`).
  ///
  /// **There is deliberately no membership test here, and there used to be
  /// one.** `changeNextButtonState` has none either: Kotlin's membership test
  /// is on the button, `updateNavigationVisibility:256-270` hiding Next and
  /// Previous outright for a non-member. Phase 139 put a copy of that test on
  /// the lock because `_NavigationBar` had no gate at all, and recorded it as
  /// a deviation. Phase 145 moved the gate to `_NavigationBar`, where Kotlin
  /// keeps it, so the copy is gone and this method reads like its original.
  ///
  /// The consequence is worth stating rather than leaving to be rediscovered:
  /// a non-member has no Next to tap, so this value is computed and discarded
  /// for them. If `_NavigationBar`'s gate is ever relaxed, the lock becomes
  /// live for a learner whose assessment tile `_StepContent` is not showing —
  /// which is the state Phase 139's gate existed to avoid.
  StepNextLock? _nextStepLock(int currentStep) {
    if (course.id != _mandatorySurveyCourseId) return null;
    if (currentStep < 0 || currentStep >= steps.length) return null;
    final step = steps[currentStep];
    // The same key `_StepContent` builds, so the tile's post-return
    // `invalidate` of `stepAssessmentProvider` refreshes this too. That is not
    // an optimisation: Kotlin re-evaluates the lock only on a page-selection
    // event (`onPageSelected:309`, and `onResume` does *not* call it), and
    // gets away with it because `openCallFragment`'s `replace()` destroys this
    // screen's view so returning from the exam re-dispatches `onPageSelected`.
    // A `context.push` leaves the screen mounted, so without sharing the
    // refresh the lock would still be holding a step the learner had just
    // finished.
    return ref
        .watch(
          stepNextLockProvider((
            stepId: step.id,
            courseId: step.courseId,
            userId: userId,
          )),
        )
        .valueOrNull;
  }

  /// Port of `CourseStepFragment.launchSaveCourseProgress` — landing on a step
  /// records a `course_progress` row and queues it for upload. The row is keyed
  /// by `(courseId, userId, stepNum)`, so a re-visit upserts in place rather
  /// than creating duplicates.
  ///
  /// `passed` is `if (stepExams.isEmpty()) true else null`
  /// (`CourseStepFragment.kt:96`): a step with no test is passed by reaching
  /// it, and a step with one waits for the exam to grade it. The port passed
  /// `null` unconditionally, because until Phase 113 nothing could tell the two
  /// apart — `stepExams` is `getByStepIdAndType(stepId, "courses")`, the join
  /// that did not exist. A course with no tests could therefore never complete.
  /// Surveys are deliberately not consulted: Kotlin reads `stepExams` here, not
  /// `stepSurvey`.
  Future<void> _recordProgress(WidgetRef ref, int index) async {
    final userId = this.userId;
    if (userId == null) return;

    final exams = await ref.read(stepExamsProvider(steps[index].id).future);

    await ref
        .read(progressRepositoryProvider)
        .saveCourseProgress(
          id: _localProgressId(),
          courseId: course.id,
          userId: userId,
          stepNum: index + 1,
          passed: exams.isEmpty ? true : null,
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
              // `updateStepDisplay` (`TakeCourseFragment.kt:192-197`): the
              // heading is the bare literal `"Course Details"` at pager
              // position 0 and `setStepText(position, steps.size)` — `"step
              // p/N"` — at every later position.
              //
              // **Kotlin's pager has a cover page and this one does not**, and
              // that is the whole reason the `position == 0` branch does not
              // belong here. `CoursesPagerAdapter.getItemCount()` is
              // `steps.size + 1`; `createFragment(0)` builds a
              // `CourseDetailFragment` and `createFragment(p > 0)` builds the
              // step at `steps[p - 1]` with `stepNumber = p`
              // (`CoursesPagerAdapter.kt:40-60`). The port reaches the course
              // description through its own `CourseDetailScreen` route
              // instead, so `PageView.builder` has `itemCount: steps.length`
              // and **port index `i` is Kotlin position `i + 1`**. Special
              // casing index 0 therefore labelled a real step with the cover
              // page's heading, and on a one-step course it was the only
              // heading the course ever showed.
              //
              // `currentStep + 1` is right for every index under that mapping
              // — it is the number `_StepContent` draws in the step header,
              // the number in the `n / N` counter beside this, and the
              // `stepNum` the assessment tiles push at the exam screen.
              Text(
                l10n.stepNumber(currentStep + 1),
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
    required this.userId,
  });

  final CourseStepRow step;
  final int stepNumber;
  final int totalSteps;

  /// `CourseStepFragment.onViewCreated` hides **both** assessment buttons when
  /// the user has not joined the course, after `hideTestIfNoQuestion` has shown
  /// them. Without this the port offered a test on a course the learner is only
  /// browsing.
  final bool isMyCourse;

  /// The signed-in user, or null for a guest. `getCourseStepData` takes
  /// `user?.id` and hands it to `hasSubmission`, which answers `false` — i.e.
  /// *not yet taken* — for a blank one.
  final String? userId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    // One read for both buttons' visibility, counts and labels — the port of
    // `getCourseStepData` feeding `hideTestIfNoQuestion`.
    final assessmentKey = (
      stepId: step.id,
      courseId: step.courseId,
      userId: userId,
    );
    final assessment = isMyCourse
        ? ref.watch(stepAssessmentProvider(assessmentKey)).valueOrNull
        : null;

    /// Re-reads the step's assessment state, which each tile calls after the
    /// screen it pushed has popped — the only thing that can change either
    /// label is a submission made over there.
    ///
    /// **Kotlin gets this for free and the port does not.** `btnTakeTest`
    /// calls `openCallFragment` (`CourseStepFragment.kt:282`), which is
    /// `FragmentNavigator.replaceFragment(…, addToBackStack = true)` — a
    /// `replace()`, so popping back **recreates** `CourseStepFragment`,
    /// `onViewCreated` runs again and `getCourseStepData` re-queries. A
    /// `context.push` leaves this screen mounted: the widget keeps its
    /// listener, so the `autoDispose` family member is never disposed and its
    /// future never re-runs. Without this the label could not change until the
    /// learner left the course entirely and came back — a swap whose only
    /// trigger is a submission made on the screen it pushes.
    ///
    /// The `await context.push(…)` deliberately stays at each `onTap` with its
    /// route written out, rather than being folded in here behind a `location`
    /// parameter: `test/ui/route_reachability_test.dart` scans these call sites
    /// statically and a variable target is a navigation it cannot read. It
    /// caught exactly that when this was written the other way round.
    void refreshAssessment() {
      if (!context.mounted) return;
      ref.invalidate(stepAssessmentProvider(assessmentKey));
    }

    final exams = assessment?.exams ?? const <ExamRow>[];
    final surveys = assessment?.surveys ?? const <SurveyRow>[];

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
          // Resources count.
          //
          // **A count, and deliberately nothing more.** This tile used to
          // carry `trailing: Icon(Icons.chevron_right)` with no `onTap` — a
          // disclosure arrow for a screen nothing implements, reported by
          // Phase 128 and unchanged through Phase 139. The two tiles below
          // keep their chevrons because they navigate.
          //
          // Kotlin's per-step resources UI is not a button: `btnResources` is
          // dead twice over (no listener is ever attached, and `setListeners`
          // sets it `View.GONE`), and what a step actually shows is an inline
          // list — `CourseStepFragment.setupInlineResources()` binding
          // `rvInlineResources` to an `InlineResourceAdapter` whose rows call
          // `openResource(library)`, with `autoDownloadResources()` and
          // `prefetchNextStepResources()` alongside. The course-level button
          // Phase 128 cited (`BaseContainerFragment.setResourceButton`) is
          // live but belongs to `CourseDetailFragment` and lists the whole
          // course's resources, not a step's.
          //
          // **The port cannot render either of those yet, and the blocker is
          // the data, not the widget.** Kotlin knows a step's resources
          // because `queueCourseResources` writes each embedded resource
          // document into `my_library` stamped with its `courseId` and
          // `stepId` (`CoursesRepositoryImpl.kt:687`, `:794`) and
          // `getAllStepResources` reads them back with
          // `myLibraryDao.getByStepId`. `CourseMapper._parseSteps` keeps
          // `resources.length` and discards the documents; `my_library` has no
          // `stepId` column and `MyLibraryMapper` writes no `courseId`. So the
          // number here describes rows the port does not hold. Restoring a tap
          // target means porting that walk first — see `PHASE_145_NOTES.md`.
          if (step.noOfResources > 0) ...[
            Card(
              child: ListTile(
                leading: const Icon(Icons.folder_outlined),
                title: Text(l10n.resourcesInStep(step.noOfResources)),
              ),
            ),
            const SizedBox(height: 8),
          ],
          // Take Test button — port of CourseStepFragment's btnTakeTest
          if (assessment != null && exams.isNotEmpty) ...[
            Card(
              child: ListTile(
                leading: const Icon(Icons.quiz_outlined),
                // `hideTestIfNoQuestion` (`CourseStepFragment.kt:244-250`):
                // `retake_test` once the learner has a submission for
                // `stepExams[0]`, `take_test` otherwise — and both carry
                // `exams.size`, the whole list, not the one exam the button
                // opens. The port had hardcoded the count-less `take test`,
                // so a step already passed still invited a first attempt.
                title: Text(
                  assessment.hasExam
                      ? l10n.retakeTestCount(exams.length)
                      : l10n.takeTestCount(exams.length),
                ),
                trailing: const Icon(Icons.chevron_right),
                // `Routes.exam` is the *pattern* `/courses/exam/:examId`;
                // appending the id to it produced
                // `/courses/exam/:examId/<id>`, which matches no route and
                // dropped the learner on go_router's error page. The exam
                // screen wants the step and course as query parameters, as
                // `CourseStepFragment` passes `stepId`/`stepNum`.
                // `stepNum` is Kotlin's own argument, not a derivation:
                // `CoursesPagerAdapter.kt:49` puts the pager position on the
                // step fragment as `"stepNumber"` and
                // `CourseStepFragment.kt:280` forwards it to the exam as
                // `"stepNum"`, which `BaseExamFragment.kt:75` reads. Passing
                // it from here assumes nothing about ordering, where deriving
                // it from the step's position in a re-query rests on
                // `stepIndex`, whose column default of `0` makes ties legal.
                // This widget already holds the number it drew in the step
                // header, so the number written to `course_progress` is the
                // one the learner saw.
                onTap: () async {
                  await context.push(
                    '/courses/exam/${exams.first.id}'
                    '?stepId=${step.id}&courseId=${step.courseId ?? ''}'
                    '&stepNum=$stepNumber',
                  );
                  refreshAssessment();
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
          // Take Survey button — port of CourseStepFragment's btnTakeSurvey
          if (assessment != null && surveys.isNotEmpty) ...[
            Card(
              child: ListTile(
                leading: const Icon(Icons.assignment_outlined),
                // `redo_survey` once answered, `record_survey` otherwise
                // (`CourseStepFragment.kt:252-259`). Unlike the test label
                // this one carries no count, though Kotlin shows the button
                // off the same non-empty list.
                title: Text(
                  assessment.hasSurvey ? l10n.redoSurvey : l10n.recordSurvey,
                ),
                trailing: const Icon(Icons.chevron_right),
                // Kotlin's `btnTakeSurvey` calls
                // `SubmissionsAdapter.openSurvey(…, stepSurvey[0].id, …)`,
                // which opens `ExamTakingFragment` against the signed-in
                // user's own submission — not the anonymous public-survey
                // path. `Routes.publicSurvey` was both the wrong screen and,
                // being a pattern rather than a base, an unmatchable route: a
                // course-step survey has no `teamId`, so the interpolation
                // left an empty segment too.
                // **The refresh fires on a submit as well as a back-out since
                // Phase 139.** It used to fire only on a back-out: the survey
                // screen's submit ended with
                // `context.go('${Routes.submissions}/<id>')`, which unmounted
                // this screen, so the `await` never resolved into a live
                // element and `refreshAssessment` short-circuited on
                // `context.mounted` — the *redo survey* relabel was
                // unreachable on the one path that produces the submission it
                // reads. `TakeSurveyScreen` now ends where Kotlin does:
                // `openSurvey` → `BaseExamFragment.continueExam` shows the
                // thank-you dialog and its Finish calls
                // `FragmentNavigator.popBackStack`, landing back on the step.
                onTap: () async {
                  await context.push('${Routes.surveys}/${surveys.first.id}');
                  refreshAssessment();
                },
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
    required this.canChangeMembership,
    required this.onPrevious,
    required this.onNext,
    required this.onFinish,
    required this.onToggleMembership,
  });

  final int currentStep;
  final int totalSteps;

  /// Gates Next and Previous, which is where `updateNavigationVisibility`
  /// (`TakeCourseFragment.kt:256-270`) gates them:
  ///
  /// ```kotlin
  /// if (containsUserId) { … } else {
  ///     binding.nextStep.visibility = View.GONE
  ///     binding.previousStep.visibility = View.GONE
  /// }
  /// ```
  ///
  /// A learner who has not joined the course can read the step they opened on
  /// and nothing else; the join button beside these is how they get further,
  /// and Kotlin additionally puts a *do you want to join this course* dialog in
  /// front of them (`setCourseData`). Joining restores the buttons on both
  /// sides — Kotlin re-runs `setCourseData()` from `addRemoveCourse`'s success
  /// path, and here the course stream re-emits.
  ///
  /// **Kotlin's own last word on this is not the rule above, and the
  /// difference is deliberately not ported.** Three later writes put the
  /// buttons back without consulting membership: `onResume:158` sets
  /// `nextStep` VISIBLE, and `onClick`'s next arm sets `previousStep` VISIBLE
  /// at `:373` with `onClickNext():347` setting `nextStep` VISIBLE at `:375`.
  /// The first of those is not exam-specific — `onPause:164-169` stamps
  /// `lastPositionBeforeExam` on **every** pause, so backgrounding the app is
  /// enough — and it fires on every resume once a successful bind has
  /// assigned `steps`. So in the shipping Android app **the gate holds on a
  /// cold open and not afterwards.**
  ///
  /// How long it survives a Next tap after that turns on whether
  /// `viewPager2.currentItem += 1` dispatches `onPageSelected` (and with it
  /// `updateNavigationVisibility`) synchronously, before `:373`/`:375` run, or
  /// afterwards. ViewPager2's `setCurrentItemInternal` calls
  /// `ScrollEventAdapter.notifyProgrammaticScroll`, which dispatches the
  /// selection synchronously on a changed target — under that reading the
  /// buttons stay for the rest of the fragment's life; under the other they
  /// go again on the next page selection. **That is not in this repository to
  /// read and the decision here does not rest on it**, because either way the
  /// gate is gone after a resume.
  ///
  /// It is ported anyway, as the intent rather than the effect, and the port
  /// is therefore *stricter* than the shipping app in any session after the
  /// first resume. The reasons: `updateNavigationVisibility` is the only place
  /// Kotlin states a rule, while all three writes that undo it are doing
  /// something else — restoring after a lifecycle event, or swapping
  /// Next for Finish at the end of the list — and each is a partial copy of
  /// the *member* branch written without its `else`. And the same method that
  /// hides these puts a *do you want to join this course* dialog in front of a
  /// non-member (`setCourseData:219-229`), which is a design statement about
  /// whose screen this is. Reproducing the escape would also mean modelling
  /// Android's fragment lifecycle, so a Flutter learner's navigation would
  /// depend on whether they had been away.
  ///
  /// Phase 139 read the same lines from the other side, when the gate was on
  /// the lock, and reached the same conclusion.
  final bool isMyCourse;

  /// Whether the join/leave button is offered at all.
  ///
  /// `setCourseData:216-232` shows `btnRemove` only for `!isGuest &&
  /// !containsUserId`, so in Kotlin a guest gets no membership button — and
  /// `updateNavigationVisibility` gives them no navigation either, since it
  /// keys on `containsUserId` alone with no guest exception. The port offered
  /// the button unconditionally, which for a guest meant a control that
  /// **would have worked**: `_toggleMembership` bails only on a null
  /// `userId`, and the guest row has one, so tapping it wrote shelf
  /// membership Kotlin does not allow a guest to write.
  ///
  /// **The `!containsUserId` half is deliberately not ported.** Kotlin hides
  /// this button from members too, so `TakeCourseFragment` has no leave
  /// affordance at all; the port keeps one, as `CourseDetailScreen` does. That
  /// is a port addition rather than a defect, and taking it away is a
  /// capability decision rather than a parity fix — see `PHASE_145_NOTES.md`.
  final bool canChangeMembership;

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
            // Leave/Join button — see [canChangeMembership].
            if (canChangeMembership)
              OutlinedButton.icon(
                onPressed: onToggleMembership,
                icon: Icon(
                  isMyCourse ? Icons.bookmark_remove : Icons.bookmark_add,
                ),
                label: Text(isMyCourse ? l10n.leaveCourse : l10n.joinCourse),
              ),
            const Spacer(),
            // Previous button. Hidden for a non-member with Next, per
            // [isMyCourse] above; also hidden on the first step, which is
            // `previousStep.visibility = if (position == 0) GONE else VISIBLE`
            // one line up in the same Kotlin method — the port's index 0 is
            // Kotlin's position 1, but there is no cover page behind it to go
            // back to, so the first step is where Previous stops either way.
            if (isMyCourse && onPrevious != null)
              FilledButton.tonalIcon(
                onPressed: onPrevious,
                icon: const Icon(Icons.arrow_back),
                label: Text(l10n.previous),
              ),
            const SizedBox(width: 8),
            // Next / Finish.
            //
            // `updateNavigationVisibility` splits these by `position >=
            // steps.size` — Finish on the last step, Next before it — and the
            // port's `currentStep >= totalSteps - 1` is the same test under
            // the index mapping. `onNext` is null on exactly that step, so the
            // two conditions were redundant and only one is kept.
            //
            // **Finish is not gated on membership and Next is**, which looks
            // like an oversight and is Kotlin's own shape. The `else` branch
            // sets `nextStep` and `previousStep` GONE and never mentions
            // `finishStep`, so the button keeps whatever
            // `setNavigationButtons` (`:464-473`) left it — visible exactly
            // when `position >= steps.size`. Reachable here on a one-step
            // course, where the first step is also the last.
            if (currentStep >= totalSteps - 1)
              FilledButton.icon(
                onPressed: onFinish,
                icon: const Icon(Icons.check),
                label: Text(l10n.finish),
              )
            else if (isMyCourse)
              FilledButton.icon(
                onPressed: onNext,
                icon: const Icon(Icons.arrow_forward),
                label: Text(l10n.next),
              ),
          ],
        ),
      ),
    );
  }
}
