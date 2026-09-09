import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:meta/meta.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/url_utils.dart';
import '../data/local/app_database.dart';
import '../repository/progress_repository.dart';
import 'app_providers.dart';
import 'session_provider.dart';
import 'sync_state.dart';

/// The two filter spinners and the search box on the courses screen, ported
/// from the fields `CoursesFragment` keeps for them.
class CourseFilter {
  const CourseFilter({
    this.query = '',
    this.gradeLevel,
    this.subjectLevel,
    this.myCoursesOnly = false,
  });

  final String query;
  final String? gradeLevel;
  final String? subjectLevel;

  /// The "my courses" / "all courses" toggle.
  final bool myCoursesOnly;

  CourseFilter copyWith({
    String? query,
    String? gradeLevel,
    String? subjectLevel,
    bool? myCoursesOnly,
    bool clearGradeLevel = false,
    bool clearSubjectLevel = false,
  }) {
    return CourseFilter(
      query: query ?? this.query,
      gradeLevel: clearGradeLevel ? null : (gradeLevel ?? this.gradeLevel),
      subjectLevel: clearSubjectLevel
          ? null
          : (subjectLevel ?? this.subjectLevel),
      myCoursesOnly: myCoursesOnly ?? this.myCoursesOnly,
    );
  }
}

class CourseFilterNotifier extends Notifier<CourseFilter> {
  @override
  CourseFilter build() => const CourseFilter();

  void setQuery(String query) => state = state.copyWith(query: query);

  void setGradeLevel(String? gradeLevel) => state = state.copyWith(
    gradeLevel: gradeLevel,
    clearGradeLevel: gradeLevel == null,
  );

  void setSubjectLevel(String? subjectLevel) => state = state.copyWith(
    subjectLevel: subjectLevel,
    clearSubjectLevel: subjectLevel == null,
  );

  void setMyCoursesOnly(bool value) =>
      state = state.copyWith(myCoursesOnly: value);

  void clear() => state = const CourseFilter();
}

/// The progress filter spinner on the courses screen - `""` means "All".
///
/// Port of `CourseFilterController`'s `spnProgress` selection, whose values
/// are the `progress_filter` array: All / Not Started / In Progress / Completed.
enum CourseProgressFilter {
  all,
  notStarted,
  inProgress,
  completed;

  static const _labels = {
    CourseProgressFilter.all: 'All',
    CourseProgressFilter.notStarted: 'Not Started',
    CourseProgressFilter.inProgress: 'In Progress',
    CourseProgressFilter.completed: 'Completed',
  };

  /// The Kotlin compares the spinner's `selectedItem.toString()` against these
  /// exact strings; the port keeps the same spelling so a future sync of
  /// server-side filters matches.
  String get label => _labels[this]!;
}

class CourseProgressFilterNotifier extends Notifier<CourseProgressFilter> {
  @override
  CourseProgressFilter build() => CourseProgressFilter.all;

  void set(CourseProgressFilter value) => state = value;
  void clear() => state = CourseProgressFilter.all;
}

final courseProgressFilterProvider =
    NotifierProvider<CourseProgressFilterNotifier, CourseProgressFilter>(
      CourseProgressFilterNotifier.new,
    );

/// Which column the courses list sorts by, and in which direction. Port of
/// `CoursesViewModel`'s `activeSort` / `isTitleAscending` / `isDateAscending`.
/// `field == null` means "no sort applied" - the stream's natural order,
/// matching the Kotlin's fall-through branch.
enum CourseSortField { title, date }

class CourseSortState {
  const CourseSortState({
    this.field,
    this.titleAscending = false,
    this.dateAscending = true,
  });

  final CourseSortField? field;

  // Per-field direction, persisted in state so a provider rebuild (hot reload,
  // dispose+recreate) does not reset a direction the user already chose. The
  // Kotlin keeps these as independent ViewModel fields for the same reason.
  final bool titleAscending;
  final bool dateAscending;

  /// The direction of the active field - what `sortCourses` reads.
  bool get ascending =>
      field == CourseSortField.title ? titleAscending : dateAscending;
}

class CourseSortNotifier extends Notifier<CourseSortState> {
  @override
  CourseSortState build() => const CourseSortState();

  /// Toggling a sort sets it active and flips its direction, exactly as
  /// `CoursesViewModel.toggleTitleSort`/`toggleDateSort` do: the same field
  /// flips asc/desc; the other field becomes active carrying its own last
  /// direction.
  void toggleTitle() {
    final s = state;
    state = CourseSortState(
      field: CourseSortField.title,
      titleAscending: !s.titleAscending,
      dateAscending: s.dateAscending,
    );
  }

  void toggleDate() {
    final s = state;
    state = CourseSortState(
      field: CourseSortField.date,
      titleAscending: s.titleAscending,
      dateAscending: !s.dateAscending,
    );
  }

  void clear() => state = const CourseSortState();
}

final courseSortProvider =
    NotifierProvider<CourseSortNotifier, CourseSortState>(
      CourseSortNotifier.new,
    );

final courseFilterProvider =
    NotifierProvider<CourseFilterNotifier, CourseFilter>(
      CourseFilterNotifier.new,
    );

/// Offline-first course list, filtered by [courseFilterProvider].
///
/// The session is awaited, but **only** when the shelf filter is on. Read as
/// `.valueOrNull` it is null for the first pass, and `CourseDao.watchCourses`
/// drops the shelf predicate on a null `shelfUserId` — which is not "no
/// courses" but *every* course, so the first frame of the My Courses tab was
/// the whole catalogue: the same defect this phase fixed in
/// [courseProgressStreamProvider]. Awaiting unconditionally would instead
/// delay the catalogue view, which needs no user at all.
final coursesStreamProvider = StreamProvider<List<CourseRow>>((ref) async* {
  final filter = ref.watch(courseFilterProvider);
  final userId = filter.myCoursesOnly
      ? (await ref.watch(sessionProvider.future))?.id
      : null;

  yield* ref
      .watch(coursesRepositoryProvider)
      .watchCourses(
        query: filter.query,
        shelfUserId: userId,
        gradeLevel: filter.gradeLevel,
        subjectLevel: filter.subjectLevel,
      );
});

/// The courses list with the progress filter and sort applied. This is what
/// `CoursesScreen` renders.
///
/// Port of `CoursesViewModel`'s `filterCourses` + `sortCourses`, run together.
/// The sort state lives in [courseSortProvider] (surviving stream emissions,
/// not reset when the SQL-filtered list re-emits) and the progress filter in
/// [courseProgressFilterProvider]; both re-trigger this provider on change.
final filteredSortedCoursesProvider = StreamProvider<List<CourseRow>>((
  ref,
) async* {
  final courses = ref.watch(coursesStreamProvider);
  final sort = ref.watch(courseSortProvider);
  final progressFilter = ref.watch(courseProgressFilterProvider);

  final items = courses.valueOrNull;
  if (items == null || items.isEmpty) {
    yield items ?? const [];
    return;
  }

  // "All" is the common case - skip the progress query entirely, matching the
  // Kotlin's `if (progressFilter.isEmpty() || progressMap == null) baseCourses`.
  var filtered = items;
  if (progressFilter != CourseProgressFilter.all) {
    // Deliberately left as `.valueOrNull` — the one place in this file where
    // awaiting the session is the *worse* option. An unresolved session here
    // costs one frame in which every course reads "Not Started"; awaiting it
    // makes a session that rejects (or that no test harness resolves) fail
    // this provider and render an **empty** course list, where the Kotlin's
    // fallback for unavailable progress is `baseCourses` — show the list
    // unfiltered. A one-frame cosmetic flash is the cheaper failure.
    final userId = ref.watch(sessionProvider).valueOrNull?.id;
    final summary = await ref
        .watch(progressRepositoryProvider)
        .courseProgressSummary([for (final c in items) c.id], userId);
    filtered = items.where((course) {
      final s = summary[course.id];
      final current = s?.current ?? 0;
      final max = s?.effectiveMax ?? 0;
      switch (progressFilter) {
        case CourseProgressFilter.notStarted:
          return current == 0;
        case CourseProgressFilter.inProgress:
          return current > 0 && (max == 0 || current < max);
        case CourseProgressFilter.completed:
          return max > 0 && current >= max;
        case CourseProgressFilter.all:
          return true;
      }
    }).toList();
  }

  yield _sortCourses(filtered, sort);
});

List<CourseRow> _sortCourses(List<CourseRow> courses, CourseSortState sort) {
  final field = sort.field;
  if (field == null) return courses;
  final sorted = [...courses];
  switch (field) {
    case CourseSortField.title:
      sorted.sort(
        (a, b) => (a.courseTitle ?? '').toLowerCase().compareTo(
          (b.courseTitle ?? '').toLowerCase(),
        ),
      );
    case CourseSortField.date:
      sorted.sort((a, b) => a.createdDate.compareTo(b.createdDate));
  }
  return sort.ascending ? sorted : sorted.reversed.toList();
}

/// A single course, for the detail screen.
final courseProvider = StreamProvider.family<CourseRow?, String>((ref, id) {
  return ref.watch(coursesRepositoryProvider).watchCourse(id);
});

/// The steps of a single course, in order.
final courseStepsProvider = StreamProvider.family<List<CourseStepRow>, String>((
  ref,
  id,
) {
  return ref.watch(coursesRepositoryProvider).watchSteps(id);
});

/// The exam attached to a course step, or null if the step has none. Drives
/// the "Take test" button on the step content view and on the course detail
/// screen — both entries into `TakeExamScreen`.
///
/// **`autoDispose`, and that is load-bearing.** A plain `FutureProvider.family`
/// is cached for the container's lifetime and nothing invalidates these, so a
/// step opened before the sync wrote its exam answered `null` for the rest of
/// the process — the one gate on a feature that had just been made to work.
/// Kotlin re-reads in `CourseStepFragment.onViewCreated`, i.e. on every
/// fragment creation; disposing when the last listener goes is the closest
/// equivalent.
///
/// Port of `ExamDao.getFirstByStepId`, which is `… WHERE stepId = :stepId
/// LIMIT 1`. Deliberately **not** `ExamDao.getByStepId`, whose
/// `getSingleOrNull` throws when two rows share a step id: the `courses` walk
/// writes one exam per step, but a standalone `exams` document is free to carry
/// a `stepId` key naming the same step, and a screen that throws out of `build`
/// is worse than one that takes the first row, which is what the Kotlin does.
final stepExamProvider = FutureProvider.autoDispose.family<ExamRow?, String>((
  ref,
  stepId,
) async {
  final rows = await ref.watch(stepExamsProvider(stepId).future);
  return rows.isEmpty ? null : rows.first;
});

/// Every exam attached to a course step — the port of `getCourseStepData`'s
/// `stepExams`, which is `examDao.getByStepIdAndType(stepId, "courses")`
/// (`CoursesRepositoryImpl.kt:533`).
///
/// [stepExamProvider] is this list's head, and it is what most callers want;
/// the *list* exists because the step tile's label carries `exams.size`
/// (`CourseStepFragment.kt:244-250`, `getString(R.string.take_test,
/// exams.size)`).
///
/// **[Exams] is not the `type = "courses"` half — it is the
/// not-`"surveys"` half**, and the difference is a real one this doc used to
/// get wrong. `ExamMapper.fromDoc` routes on `type == 'surveys'` and files
/// *everything else* as an exam (`exam_mapper.dart`, "everything else is an
/// exam"), so `examDao` is a **superset** of Kotlin's `"courses"`: a
/// `steps[i].exam` carrying no `type` at all lands here and gets a button,
/// where Kotlin files it as `"exam"` — a value no Kotlin query selects — and
/// shows nothing. That is Phase 113's deliberate deviation, and since this
/// list's `length` is now rendered to the learner and its `first` decides the
/// wording, it is the assumption both rest on.
final stepExamsProvider = FutureProvider.autoDispose
    .family<List<ExamRow>, String>((ref, stepId) async {
      final db = ref.watch(appDatabaseProvider);
      return db.examDao.getByStepIds([stepId]);
    });

/// The surveys attached to a course step. Drives the "Take survey" button.
final stepSurveysProvider = FutureProvider.autoDispose
    .family<List<SurveyRow>, String>((ref, stepId) async {
      final db = ref.watch(appDatabaseProvider);
      return db.surveyDao.getByStepId(stepId);
    });

/// What [stepAssessmentProvider] is keyed on — `getCourseStepData`'s two
/// arguments plus the step's own course, which Kotlin reads off the step row it
/// has already loaded (`CoursesRepositoryImpl.kt:530`, `:538`).
typedef StepAssessmentKey = ({String stepId, String? courseId, String? userId});

/// The assessment half of Kotlin's `CourseStepData`: both lists, plus the two
/// booleans that decide whether the step tile says *take* or *retake*.
typedef StepAssessment = ({
  List<ExamRow> exams,
  List<SurveyRow> surveys,
  bool hasExam,
  bool hasSurvey,
});

/// Port of the assessment fields `CoursesRepositoryImpl.getCourseStepData`
/// computes (`:529-556`), feeding `CourseStepFragment.hideTestIfNoQuestion`
/// (`:241-260`).
///
/// The lists drive **visibility** and the count in the label; the booleans
/// drive the label's *wording*. Nothing here reads a question count, despite
/// `hideTestIfNoQuestion`'s name: that count lives inside
/// [SubmissionsRepository.hasSubmission] and decides the *wording*, never
/// whether a button appears. The name is a fossil.
///
/// Kotlin's two booleans are
/// `hasSubmission(stepExams[0].id, step.courseId, userId, "exam")` and the same
/// for `stepSurvey[0].id` with `"survey"` — so with two exams on one step the
/// label counts both and only the **first** one's submission decides whether it
/// reads "Retake". That is Kotlin's quirk, ported rather than corrected, and
/// the state it applies to is **port-only**: Kotlin cannot produce a step with
/// two exams (one `steps[i].exam` object per step, read with `getAsJsonObject`,
/// and the standalone `exams` walk passes `("", "", doc, "")` so it never sets
/// `stepId`), while `ExamMapper.fromDoc` does write a document's own `stepId`.
/// So this is fidelity to what the Kotlin *says*, not to a state it reaches.
///
/// One deliberate improvement on the Kotlin, preserved here rather than
/// introduced: the exam tile opens `exams.first.id`, the same row `hasExam`
/// interrogated. Kotlin's `btnTakeTest` routes through
/// `BaseExamFragment.initExam` → `ExamDao.getFirstByStepId`, which is
/// `WHERE stepId = ? LIMIT 1` with **no type filter**, so on a step carrying
/// both an exam and a survey it can open the survey row the label never asked
/// about. Label and destination cannot disagree in the port.
///
/// A step with no exams (or no surveys) short-circuits to `false` without a
/// query, as `getCourseStepData` does (`:537`, `:542`) — which matters because
/// `hasSubmission`'s guards would answer `false` anyway and this keeps the
/// reason legible.
final stepAssessmentProvider = FutureProvider.autoDispose
    .family<StepAssessment, StepAssessmentKey>((ref, key) async {
      final exams = await ref.watch(stepExamsProvider(key.stepId).future);
      final surveys = await ref.watch(stepSurveysProvider(key.stepId).future);
      final submissions = ref.watch(submissionsRepositoryProvider);
      final hasExam =
          exams.isNotEmpty &&
          await submissions.hasSubmission(
            stepExamId: exams.first.id,
            courseId: key.courseId,
            userId: key.userId,
            type: 'exam',
          );
      final hasSurvey =
          surveys.isNotEmpty &&
          await submissions.hasSubmission(
            stepExamId: surveys.first.id,
            courseId: key.courseId,
            userId: key.userId,
            type: 'survey',
          );
      return (
        exams: exams,
        surveys: surveys,
        hasExam: hasExam,
        hasSurvey: hasSurvey,
      );
    });

/// Kotlin's `isNextStepLocked` / `lockedStepMessage` pair for one step
/// ([locked] and, when locked, which of the two messages to show).
///
/// [blockedByTest] picks the message the way `changeNextButtonState:327-330`
/// does — `please_complete_test` when the step carries an exam, otherwise
/// `please_complete_survey` — so it is only meaningful while [locked].
typedef StepNextLock = ({bool locked, bool blockedByTest});

/// Port of `TakeCourseFragment.changeNextButtonState`
/// (`TakeCourseFragment.kt:315-338`) — the per-step refusal to advance that
/// the MyPlanet Onboarding course puts in front of an unanswered step
/// assessment. **This is not the finish-time gate**, which is
/// `onFinishStep`'s `hasUnfinishedSurveys` and lives in
/// `take_course_screen._onFinish`; the two are independent, and Kotlin's
/// per-step lock never applies to the last step because Next is replaced by
/// Finish there.
///
/// The course-id gate (`:316`) is deliberately **not** here: it reads the
/// screen's own course, so the caller decides whether to watch this at all,
/// exactly as `changeNextButtonState`'s `if` wraps its whole body and its
/// `else` is a bare `isNextStepLocked = false` (`:336`).
///
/// **Presence, not submission — and that distinction is the whole point.**
/// `changeNextButtonState` reads `stepData.stepExams.isNotEmpty()` /
/// `stepData.stepSurvey.isNotEmpty()` (`:320-321`), i.e. *does the step carry
/// an assessment at all*. It locally shadows the names `hasExam`/`hasSurvey`,
/// which on `CourseStepData` mean something else entirely —
/// `hasSubmission(...)`, *has the learner already answered*
/// (`CoursesRepositoryImpl.kt:534-542`). Building the lock on those would
/// invert it: only a learner who had already answered would be blocked. The
/// lists are read off [stepAssessmentProvider] rather than re-queried so the
/// lock and the step tile cannot disagree about what the step carries, and so
/// that the tile's post-return `invalidate` refreshes both — see below.
///
/// **The completion half is [SubmissionsRepository]-adjacent but is not
/// `hasSubmission`.** Kotlin's is `isStepCompleted`
/// (`SubmissionsRepositoryImpl.kt:359-365`), and it differs from
/// `hasSubmission` in four ways, of which two decide this feature:
///
///  * `status != 'pending'`. `hasSubmission` is status-blind, so a learner who
///    has merely *started* the exam satisfies it; this needs them to have
///    finished. `saveExamAnswer` writes `complete` for a survey's last answer
///    and `requires grading` for an exam's, both of which pass; a half-answered
///    attempt is `pending` and does not. In SQL `status != 'pending'` is NULL —
///    so false — for a NULL status; the team-adoption marker (`status: ''`)
///    does pass, as it does in Kotlin.
///  * `parentId LIKE '%' || :examId || '%'` (`SubmissionDao.kt:24`), against
///    `hasSubmission`'s exact `"$examId@$courseId"`. The `LIKE` is
///    deliberately loose: it matches the compound key every current writer
///    stores (Phase 125), a bare id an older build wrote, and — as Kotlin does
///    — any other `parentId` carrying the id as a substring.
///
/// Both are read off the Kotlin query, and since Phase 149 [_isStepCompleted]
/// **runs that query** rather than reproducing it in Dart; an earlier revision
/// of these two bullets said it kept the `contains` and the NULL test
/// explicitly, which the swap made false in the same file.
///
/// It also takes no `courseId` and no `type`, and answers **true** for a step
/// with no assessment row at all (`?: return true`) — which is why a step
/// carrying neither an exam nor a survey is unlocked.
///
/// **A rejected read is unlocked, and that is a decision rather than a
/// default.** `getCourseStepData` throws `IllegalStateException` on a missing
/// step row (`CoursesRepositoryImpl.kt:527-528`) and
/// `changeNextButtonState`'s `lifecycleScope.launch` (`:318`) has no `try`, so
/// Kotlin's `isNextStepLocked` keeps the `false` that `onPageSelected:306` had
/// just assigned. A caller reading `.valueOrNull` gets null here and treats it
/// as unlocked, which is Kotlin's outcome without Kotlin's crash. The same
/// mapping covers the still-loading case, which reproduces Kotlin's own race:
/// `:306` clears the flag synchronously and `:318` sets it from a coroutine,
/// so a tap in between is not blocked there either.
final stepNextLockProvider = FutureProvider.autoDispose
    .family<StepNextLock, StepAssessmentKey>((ref, key) async {
      // Watched before the await rather than after it: a `ref.watch` on the
      // far side of an `await` runs against an element that may already be
      // disposed. Harmless for this provider, which never changes, but the
      // shape is not worth keeping.
      final db = ref.watch(appDatabaseProvider);
      final assessment = await ref.watch(stepAssessmentProvider(key).future);
      // Kotlin's order (`:323-333`) is completion first, presence second. The
      // two agree either way — `isStepCompleted` answers true when the step
      // carries no assessment — and this keeps the cheap test first.
      final hasExam = assessment.exams.isNotEmpty;
      final hasSurvey = assessment.surveys.isNotEmpty;
      if (!hasExam && !hasSurvey) {
        return (locked: false, blockedByTest: false);
      }
      final completed = await _isStepCompleted(
        db: db,
        exams: assessment.exams,
        surveys: assessment.surveys,
        userId: key.userId,
      );
      return (locked: !completed, blockedByTest: hasExam);
    });

/// Port of `SubmissionsRepositoryImpl.isStepCompleted` (`:359-365`).
///
/// **Which row is interrogated is a judgement call the split tables force.**
/// Kotlin keeps every exam and survey in one `exams` table and picks the row
/// with `ExamDao.getFirstByStepId`, which is `WHERE stepId = ? LIMIT 1` with
/// **no type filter and no `ORDER BY`** (`ExamDao.kt:13`) — so on a step
/// carrying both, the row is whichever SQLite returns first. In practice that
/// is the exam: `buildCoursePayload` collects `steps[i].exam` before
/// `steps[i].survey` into one list (`CoursesRepositoryImpl.kt:698-699`) and
/// `@Upsert` inserts in list order, giving the exam the lower rowid. This
/// takes the step's first exam and falls back to its first survey, which
/// matches that and has a property worth keeping deliberately: the message
/// [stepNextLockProvider] chooses is the *test* one whenever an exam is
/// present (`changeNextButtonState:328`), so message and remedy name the same
/// assessment. An `OR` across both tables would let the two disagree.
///
/// Two consequences of the split, both in the port's favour and both
/// deviations rather than fidelity:
///
///  * **Kotlin can wedge a step permanently and the port cannot.** Its
///    `stepExams`/`stepSurvey` are filtered on `type` while
///    `getFirstByStepId` is not, so a step whose exam object carries no `type`
///    and whose survey object says `"type": "surveys"` locks with the *survey*
///    message while the unlock condition interrogates the *exam* row —
///    answering the survey never opens it. The type filter is not the only
///    route to that disagreement: `getFirstByStepId` has no `ORDER BY` either,
///    so a step that gains an exam *after* its first sync leaves the survey
///    holding the lower rowid and Kotlin picks the survey.
///  * **The lock fires on documents Kotlin's does not.** Kotlin's `type`
///    column holds the server document's own `type`, falling back to the
///    singular `"exam"`/`"survey"` that no Kotlin query selects
///    (`CoursesRepositoryImpl.kt:746`), so `getByStepIdAndType(…, "courses"
///    / "surveys")` misses a type-less step assessment — no button *and* no
///    lock. `ExamMapper` routes on `type == 'surveys'` and files everything
///    else as an exam, so the port's tables are a superset and a type-less
///    assessment both shows a tile and holds the step. That is Phase 113's
///    documented deviation; this is the reader that extends it from the
///    buttons to the lock.
///
/// **The submission read is now Kotlin's own query.** It used to pick a
/// submissions table by the assessment row's type and filter the rest in Dart,
/// which diverged from Kotlin on three axes at once; Phase 143 added
/// `SubmissionDao.countCompletedByUserAndExamId` (`SubmissionDao.kt:24`) and
/// this is the call site it was added for. Taking it closes all three, each in
/// the direction of the Kotlin:
///
///  * **No `type` predicate.** Kotlin's count has none, so a submission whose
///    `type` is null or unexpected, but whose `parentId` carries the
///    assessment id, releases Kotlin's lock. It did not release this one. That
///    is reachable rather than theoretical: `upsertDocuments` stores
///    `getStringOrNull('type', json)`, so a Planet document that omits `type`
///    lands here with a null one — pinned by *a synced submission with no type
///    releases the lock*.
///  * **`LIKE` is ASCII-case-insensitive** where Dart's `contains` is not.
///  * **`%` and `_` inside the exam id are wildcards**, because Kotlin
///    interpolates the id unescaped; escaping would make the port *stricter*
///    than the app it ports. Unreachable while `parentId` is minted from the
///    same `exam.id` through `examParentId`, and preserved deliberately.
///
/// The NULL-`status` case is unchanged, and the *predicate* was already right:
/// `NOT (status = 'pending')` is NULL for a NULL status and `WHERE NULL`
/// excludes the row, which is what the explicit `status != null` test did in
/// Dart — and which is Kotlin's behaviour too, so the predicate is right.
///
/// **The outcome used to diverge one layer down, and Phase 151 closed it.**
/// Kotlin's sync-in stores `JsonUtils.getString('status', …)`, which is `""`
/// for a missing key, and `'' != 'pending'` counts; the port's
/// `upsertDocuments` stored `getStringOrNull`, which is null for a missing key
/// *and* for `""`, so a Planet submission carrying no `status` unlocked the
/// step in Kotlin and locked it here. The writer now stores Kotlin's `''` and
/// this reader is unchanged, because only one side had drifted. Pinned by
/// `test/repository/submission_empty_vs_null_status_test.dart`.
///
/// Which *row* is interrogated stays this function's own judgement, above —
/// the query takes an id, not a step.
Future<bool> _isStepCompleted({
  required AppDatabase db,
  required List<ExamRow> exams,
  required List<SurveyRow> surveys,
  required String? userId,
}) async {
  final fromExam = exams.isNotEmpty;
  final assessmentId = fromExam
      ? exams.first.id
      : (surveys.isEmpty ? null : surveys.first.id);
  // `examDao.getFirstByStepId(stepId) ?: return true` — a step with no
  // assessment row is complete, which is what unlocks an ordinary step.
  //
  // Unreachable from the one caller, which short-circuits on both lists being
  // empty before it gets here. Kept because it is Kotlin's own `?:` and this
  // function reads as a port of `isStepCompleted` rather than as a helper for
  // one call site; its lack of coverage is deliberate, not a gap.
  if (assessmentId == null) return true;
  return await db.submissionDao.countCompletedByUserAndExamId(
        userId,
        assessmentId,
      ) >
      0;
}

/// Distinct grade levels present locally, for the filter spinner.
///
/// Deliberately *not* derived from [coursesStreamProvider]: that watches
/// [courseFilterProvider], so choosing a grade would invalidate the option list
/// it was chosen from. While it reloaded the dropdown would briefly see an empty
/// item list with a non-null selection, which trips a `DropdownButton` assert.
final gradeLevelsProvider = StreamProvider<List<String>>((ref) {
  return ref.watch(coursesRepositoryProvider).watchGradeLevels();
});

/// Distinct subject levels present locally, for the filter spinner.
final subjectLevelsProvider = StreamProvider<List<String>>((ref) {
  return ref.watch(coursesRepositoryProvider).watchSubjectLevels();
});

class CourseSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) async {
    final courses = ref.read(coursesRepositoryProvider);
    final progress = ref.read(progressRepositoryProvider);
    final tags = ref.read(tagsRepositoryProvider);

    // The four CouchDB caches are independent tables, so the pulls run
    // concurrently. A "sync courses" refreshes progress and certifications in
    // the same pass - the take-course view reads them together, and a stale
    // `certification` row is what gates the "certified" badge. Tags ride
    // along because the courses screen's collections filter reads them
    // (the Kotlin pulls `tags` in every full sync).
    final courseResult = courses.sync(config: config, onProgress: onProgress);
    final progressResult = progress.syncCourseProgress(
      config: config,
      onProgress: onProgress,
    );
    final certResult = progress.syncCertifications(
      config: config,
      onProgress: onProgress,
    );
    final tagsResult = tags.sync(config: config, onProgress: onProgress);

    final [a, b, c, d] = await Future.wait([
      courseResult,
      progressResult,
      certResult,
      tagsResult,
    ]);
    final totalSaved = [
      a,
      b,
      c,
      d,
    ].fold<int>(0, (sum, r) => sum + (r is SyncComplete ? r.savedCount : 0));
    final failed = [a, b, c, d].whereType<SyncFailed>().firstOrNull;
    return failed ?? SyncComplete(totalSaved);
  }
}

final courseSyncProvider = NotifierProvider<CourseSyncNotifier, SyncUiState>(
  CourseSyncNotifier.new,
);

/// Port of `model/CoursesProgressRow.kt`.
///
/// Named for the Kotlin class rather than shortened to `CourseProgressRow`,
/// which is the drift row class for the `courses_progress` table — the two
/// are different things and the shorter name shadowed the drift one inside
/// this file and clashed on import anywhere both were needed.
///
/// Represents a single row in the course progress list.
class CoursesProgressRow {
  const CoursesProgressRow({
    required this.courseId,
    required this.courseName,
    this.progressCurrent,
    this.progressMax,
    this.mistakes,
    this.stepMistakes,
  });

  final String courseId;
  final String courseName;
  final int? progressCurrent;
  final int? progressMax;
  final int? mistakes;

  /// Per-step mistake counts, keyed by the **0-based ordinal of the exam**
  /// within the course (the Kotlin `stepMistake` map's key), which the row
  /// renders as `key + 1`.
  final Map<int, int>? stepMistakes;
}

/// Course progress data for the current user — the My Progress list.
///
/// Combines course enrollment, step completion, and exam submissions
/// to compute progress statistics.
///
/// `autoDispose`, for the same reason [courseProgressGridProvider] is.
/// `CoursesProgressFragment.onViewCreated:31` calls `loadCourseData()`
/// unconditionally — it has none of `CourseProgressViewModel.loadProgress`'s
/// `if (value != null) return` guard — and the fragment is pushed with
/// `addToBackStack`, so leaving to take an exam destroys it and coming back
/// re-reads. This body `yield`s once and completes, and nothing in `lib/`
/// invalidates it, so without `autoDispose` the first read was frozen for the
/// process lifetime: two more mistakes, and the row kept the old count while
/// the grid it opens — which *is* autoDispose — showed the new one. Two halves
/// of one screen disagreeing.
final courseProgressStreamProvider = StreamProvider.autoDispose<List<CoursesProgressRow>>((
  ref,
) async* {
  // `ref.watch(sessionProvider).valueOrNull` — which this used to read — is
  // `null` for the whole first pass, and a null `shelfUserId` makes
  // `CourseDao.watchCourses` drop the shelf predicate altogether. So the first
  // thing "My Progress" emitted was **the entire course catalogue**, every
  // entry captioned with a progress bar, until the session resolved and the
  // provider rebuilt. Awaiting `.future` means the shelf filter is never
  // applied with an unknown user. Fifth instance of this shape in the port,
  // and the first found in a provider rather than a screen.
  final userId = (await ref.watch(sessionProvider.future))?.id;
  final coursesRepo = ref.watch(coursesRepositoryProvider);

  // Watch user's courses (shelf membership)
  final myCourses = await coursesRepo.watchCourses(shelfUserId: userId).first;
  if (myCourses.isEmpty) {
    yield [];
    return;
  }

  final courseIds = myCourses.map((c) => c.id).toList();

  // The contiguous-run progress (current) and step count (max) come from the
  // same repository method the courses list uses, so the "My Progress" grid and
  // the list's progress filter agree on what "current" means — a step the user
  // opened counts, regardless of `passed`, walked from step 1 until the first
  // gap. Previously this grid counted submissions, which over-reported
  // (re-taking an exam inflated "current") and bore no relation to the list.
  final progress = ref.watch(progressRepositoryProvider);
  final summary = await progress.courseProgressSummary(courseIds, userId);

  final db = ref.watch(appDatabaseProvider);

  // `fetchCourseData` groups the user's exam submissions by course with
  // `parentId.split("@").lastOrNull { courseIdsSet.contains(it) }`
  // (`ProgressRepositoryImpl.kt:69-78`) — an exact match on one `@`-delimited
  // segment. A `contains(courseId)` substring test, which this used to do,
  // attributes a submission to every course whose id is a substring of the
  // parent, and to a course whose id merely appears inside an exam id.
  final courseIdSet = courseIds.toSet();
  final submissions = await db.submissionDao.getExamSubmissionsByUser(userId);
  final submissionsByCourse = <String, List<SubmissionRow>>{};
  for (final sub in submissions) {
    final parentId = sub.parentId;
    if (parentId == null) continue;
    final owner = parentId
        .split('@')
        .lastWhere(courseIdSet.contains, orElse: () => '');
    if (owner.isEmpty) continue;
    submissionsByCourse.putIfAbsent(owner, () => []).add(sub);
  }

  // `submissionMap` keys `stepMistake` by the **index** of the exam within the
  // course's exam list (`examIds.forEachIndexed { index, id -> ... }`), and
  // `CoursesProgressAdapter.showStepMistakes` renders `stepKey.toInt() + 1`.
  // The key is therefore an exam ordinal that the column header calls "Step".
  // This used to be keyed by the raw exam id and the screen recovered a number
  // by running a `(\d+)` regex over it — which on a real CouchDB exam id
  // (a hex hash) yields whichever digit happens to appear first, and on an id
  // with no digit at all yields 0 for every row.
  final examsByCourse = <String, List<ExamRow>>{};
  for (final exam in await db.examDao.getByCourseIds(courseIds)) {
    final cid = exam.courseId;
    if (cid != null) examsByCourse.putIfAbsent(cid, () => []).add(exam);
  }

  final mistakesByCourse = <String, int>{};
  final stepMistakesByCourse = <String, Map<int, int>>{};
  for (final courseId in courseIds) {
    final examIndex = <String, int>{};
    final exams = examsByCourse[courseId] ?? const <ExamRow>[];
    for (var i = 0; i < exams.length; i++) {
      examIndex.putIfAbsent(exams[i].id, () => i);
    }

    var totalMistakes = 0;
    Map<int, int>? lastMap;
    for (final sub
        in submissionsByCourse[courseId] ?? const <SubmissionRow>[]) {
      // Kotlin rebuilds `mistakesMap` per submission and writes it to the
      // JsonObject inside the same loop, so the map that survives is the
      // **last** submission's while `mistakes` accumulates over all of them.
      // Faithful, and worth flagging as a Kotlin quirk rather than a port
      // simplification: two attempts at the same course show the per-step
      // breakdown of only one of them.
      final perExam = <int, int>{};
      for (final answer in await db.submissionDao.answersFor(sub.id)) {
        // Kotlin reaches the exam through the answer's *question* row
        // (`questionsMap[questionId]?.examId`) and skips an answer whose exam
        // is not one of this course's. `SubmissionAnswers.examId` carries the
        // same value on both the local write path and the sync-in, so the
        // join is the same one without the extra query — but the
        // course-membership filter is not optional, and dropping it let an
        // unrelated exam's mistakes into the total.
        //
        // One deliberate divergence: Kotlin drops an answer whose
        // `questionId` no longer resolves to an `exam_questions` row, because
        // that lookup is how it reaches the exam. Here the exam id is on the
        // answer, so a mistake still counts when its question row has been
        // pruned by a re-sync — which is the number the learner earned.
        final index = examIndex[answer.examId];
        if (index == null) continue;
        totalMistakes += answer.mistakes;
        perExam[index] = (perExam[index] ?? 0) + answer.mistakes;
      }
      lastMap = perExam;
    }
    // Kotlin calls `submissionMap` only for a course with submissions, so
    // `mistakes` is *absent* there and `CoursesProgressAdapter` falls back to
    // `message_placeholder("0")`. Storing 0 renders the same "0"; the model
    // differs from the Kotlin's null, the pixels do not.
    mistakesByCourse[courseId] = totalMistakes;
    if (lastMap != null && lastMap.isNotEmpty) {
      stepMistakesByCourse[courseId] = lastMap;
    }
  }

  // Build result rows
  final rows = <CoursesProgressRow>[];
  for (final course in myCourses) {
    final s = summary[course.id];
    rows.add(
      CoursesProgressRow(
        courseId: course.id,
        courseName: course.courseTitle ?? 'Untitled Course',
        progressCurrent: s?.current,
        progressMax: s?.max,
        mistakes: mistakesByCourse[course.id] ?? 0,
        stepMistakes: stepMistakesByCourse[course.id],
      ),
    );
  }

  yield rows;
});

/// One course's progress grid — port of `CourseProgressViewModel.loadProgress`
/// (`CourseProgressViewModel.kt:23-29`), which resolves the signed-in user and
/// then calls `coursesRepository.getCourseProgress(courseId, user?._id)`.
///
/// `autoDispose` on purpose. The Kotlin's `loadProgress` starts with
/// `if (_courseProgress.value != null) return`, so a given ViewModel loads
/// once — but the ViewModel dies with the Activity, and this screen is entered
/// afresh every time, so coming back after taking an exam re-reads. A cached
/// `FutureProvider.family` would instead pin the first read for the process
/// lifetime and show stale cells forever (Phase 113's defect D, on the exam
/// join).
///
/// The session is awaited via `.future`, not read as `.valueOrNull`. The
/// screen never watches `sessionProvider`, so an unwatched read yields `null`
/// until something else resolves it, and a grid built for "no user" is not
/// empty — it is a full grid of blank cells over a "Progress 0 of N" header,
/// indistinguishable from a learner who has done nothing. Written this way
/// from the start rather than fixed later; the same shape has now been found
/// five times in this port.
///
/// Kotlin's `CourseProgressViewModel` passes `user?._id` here while the list's
/// `ProgressViewModel` passes `user?.id`, and **every writer keys on `id`** —
/// `CourseStepFragment` for progress rows, `ExamTakingFragment` for
/// submissions. For a synced account the two are equal, but a member created
/// offline gets a generated `id` and an empty `_id`, so the Kotlin grid reads
/// nothing back and shows a blank grid under a list row that displayed real
/// numbers. The port passes `id` on both paths deliberately: reproducing that
/// would mean reproducing a lookup against a key nothing writes.
final courseProgressGridProvider = FutureProvider.autoDispose
    .family<CourseProgressData, String>((ref, courseId) async {
      final user = await ref.watch(sessionProvider.future);
      return ref
          .watch(progressRepositoryProvider)
          .courseProgress(courseId, user?.id);
    });

/// A course cover image fetched from the CouchDB `courses` attachment endpoint
/// behind Basic auth — the same pattern as [profileImageProvider]. A course's
/// `coverFileName` is persisted on the row (mapped from the CouchDB doc by
/// `CourseMapper`); the URL is rebuilt here against the current
/// [serverConfigProvider] and fetched as bytes through [PlanetApi.getBytes].
///
/// Port of `CoursesAdapter.bindCover` (818732139). Returns `null` when there
/// is no attachment, the config is absent, or the fetch fails — the grid tile
/// falls back to the subject-tinted icon.
final courseCoverImageProvider =
    FutureProvider.family<Uint8List?, CourseCoverImageRequest>((
      ref,
      key,
    ) async {
      if (key.courseId.isEmpty || key.coverFileName.isEmpty) return null;
      final config = ref.watch(serverConfigProvider);
      if (config == null) return null;
      final url = UrlUtils.courseImageUrl(
        config,
        key.courseId,
        key.coverFileName,
      );
      if (url == null) return null;
      final authHeader = UrlUtils.authHeader(config);
      final result = await ref
          .watch(planetApiProvider)
          .getBytes(url, authHeader: authHeader);
      return switch (result) {
        NetworkSuccess<List<int>>(:final data) => Uint8List.fromList(data),
        NetworkError<List<int>>() => null,
        NetworkException<List<int>>() => null,
      };
    });

/// The key for [courseCoverImageProvider] — the course's local id and the
/// CouchDB attachment name.
@immutable
class CourseCoverImageRequest {
  const CourseCoverImageRequest({
    required this.courseId,
    required this.coverFileName,
  });

  final String courseId;
  final String coverFileName;

  @override
  bool operator ==(Object other) =>
      other is CourseCoverImageRequest &&
      other.courseId == courseId &&
      other.coverFileName == coverFileName;

  @override
  int get hashCode => Object.hash(courseId, coverFileName);
}
