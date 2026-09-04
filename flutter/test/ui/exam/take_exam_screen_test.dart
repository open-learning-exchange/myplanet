import 'dart:io';
import 'dart:typed_data';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/submit_photos_files.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/core/system/photo_capture.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/converters.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/ui/exam/take_exam_screen.dart';

import '../../support/widget_harness.dart';

/// First coverage for `TakeExamScreen` — the port of `ExamTakingFragment` /
/// `BaseExamFragment`, and until now 460 lines with none.
///
/// These drive the real provider graph against an in-memory [AppDatabase]
/// rather than faking screen state, the way `my_health_screen_test.dart` does:
/// the exam and its questions are seeded as rows, so `examProvider` /
/// `examQuestionsProvider` resolve through the real DAO, and a submit runs the
/// real [SubmissionsRepository] all the way to the `submissions`,
/// `submission_answers` and `submit_photos` tables. That is deliberate — the
/// graded attempt *is* the deliverable, and a fake repository would assert on
/// nothing.
///
/// Two traps this file works around, both documented in `CLAUDE.md`:
///
///  * The verification-photo path writes through [SubmitPhotosFiles], which is
///    genuine `dart:io`, and a widget test's zone is fake-async — real file
///    futures never progress there. [settleExam] yields wall-clock time with
///    `tester.runAsync` and pumps afterwards; pumping *inside* `runAsync` does
///    not work.
///  * `pumpAndSettle` must never be used after tapping Submit. While
///    `_isSubmitting` is true the submit button holds a `CircularProgressIndicator`,
///    and that indefinite animation keeps `pumpAndSettle` spinning to its
///    ten-minute default — a failure that looks exactly like a hang.
class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}

/// A session whose future rejects, for the submit path's error branch.
class _FailingSessionNotifier extends SessionNotifier {
  @override
  Future<UserRow?> build() async => throw Exception('session unavailable');
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);

  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}

/// A [PhotoCapture] that records every call and hands back whatever the test
/// wants — bytes for the happy path, `null` for "no camera / permission denied
/// / user backed out", which the screen must swallow.
class _FakePhotoCapture implements PhotoCapture {
  _FakePhotoCapture({this.photo});

  final CapturedPhoto? photo;
  int calls = 0;

  @override
  Future<CapturedPhoto?> capture() async {
    calls++;
    return photo;
  }
}

/// Fails the first [startExamSession] and then delegates, so a retry can be
/// shown to recover rather than replaying the same error. A [Fake] rather than
/// a mock because `startExamSession` takes an `ExamRow`, and mocktail's `any()`
/// would need a registered fallback for it.
class _FlakySubmissionsRepository extends Fake
    implements SubmissionsRepository {
  _FlakySubmissionsRepository(this._inner);

  final SubmissionsRepository _inner;
  int calls = 0;

  @override
  Future<String> startExamSession({
    required ExamRow exam,
    required List<ExamQuestionRow> questions,
    required String userId,
    String? courseId,
    DateTime? now,
  }) async {
    calls++;
    if (calls == 1) throw Exception('transient');
    return _inner.startExamSession(
      exam: exam,
      questions: questions,
      userId: userId,
      courseId: courseId,
      now: now,
    );
  }

  @override
  Future<bool> saveExamAnswer({
    required String submissionId,
    required ExamQuestionRow question,
    required ExamDraftAnswer answer,
    required bool isFinal,
    required bool isExplicitSubmission,
    DateTime? now,
  }) => _inner.saveExamAnswer(
    submissionId: submissionId,
    question: question,
    answer: answer,
    isFinal: isFinal,
    isExplicitSubmission: isExplicitSubmission,
    now: now,
  );
}

class _ThrowingSubmissionsRepository extends Fake
    implements SubmissionsRepository {
  @override
  Future<String> startExamSession({
    required ExamRow exam,
    required List<ExamQuestionRow> questions,
    required String userId,
    String? courseId,
    DateTime? now,
  }) async => throw Exception('disk full');
}

void main() {
  const server = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: 'secret-pin',
    couchDbUrl: 'https://satellite:secret-pin@planet.example.org',
    id: 'config-1',
    code: 'community-a',
    parentCode: 'nation',
  );

  late AppDatabase db;
  late Directory tempDir;
  late Future<Directory> Function() savedBaseDirectory;
  late PhotoCapture savedPhotoCapture;

  final user = UserRow(
    id: 'user-1',
    couchId: 'org.couchdb.user:ada',
    name: 'ada',
    rolesList: const ['learner'],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  setUp(() async {
    db = AppDatabase.memory();
    tempDir = await Directory.systemTemp.createTemp('take_exam_test');
    savedBaseDirectory = SubmitPhotosFiles.baseDirectory;
    SubmitPhotosFiles.baseDirectory = () async => tempDir;
    savedPhotoCapture = PhotoCapture.instance;
  });

  tearDown(() async {
    SubmitPhotosFiles.baseDirectory = savedBaseDirectory;
    PhotoCapture.instance = savedPhotoCapture;
    await db.close();
    try {
      if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
    } catch (_) {}
  });

  Future<void> seedExam({
    String id = 'exam-1',
    String? name = 'Algebra final',
    String? courseId,
    String? passingPercentage,
  }) async {
    await db.examDao.upsertExam(
      ExamsCompanion.insert(
        id: id,
        name: Value(name),
        courseId: Value(courseId),
        passingPercentage: Value(passingPercentage),
      ),
    );
  }

  Future<void> seedQuestion({
    required String id,
    required int position,
    String examId = 'exam-1',
    String? header,
    String? body,
    String? type,
    List<ExamChoice> choices = const [],
    List<String> correctChoices = const [],
    int scaleMax = 9,
  }) async {
    await db.examDao.upsertQuestion(
      ExamQuestionsCompanion.insert(
        id: id,
        examId: examId,
        position: position,
        header: Value(header),
        body: Value(body),
        type: Value(type),
        choices: Value(choices),
        correctChoices: Value(correctChoices),
        scaleMax: Value(scaleMax),
      ),
    );
  }

  /// Marks [courseId] certified, which is the only gate on the verification
  /// photo — `ProgressRepository.isCourseCertified` counts `certification`
  /// rows whose `courseIds` blob mentions the course.
  Future<void> seedCertification(String courseId) async {
    await db.certificationDao.upsertAll([
      CertificationsCompanion.insert(
        id: 'cert-1',
        name: const Value('Certified course'),
        courseIds: Value('["$courseId"]'),
      ),
    ]);
  }

  /// Lets the screen's real disk and database work finish, then rebuilds.
  ///
  /// See the file header: `runAsync` yields wall-clock time so the `dart:io`
  /// futures behind [SubmitPhotosFiles] complete, and the `pump` afterwards
  /// rebuilds with the resolved state.
  Future<void> settleExam(WidgetTester tester, {int rounds = 8}) async {
    for (var round = 0; round < rounds; round++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 20)),
      );
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  /// Waits out a snackbar.
  ///
  /// The `incorrect_ans` / "please answer" snackbars sit over the bottom of
  /// the screen, which is where this screen's Next and Submit buttons live, so
  /// a tap that follows one lands on the snackbar instead of the button.
  ///
  /// Kotlin's overlap is narrower, not the same: `btnBack` and `btnNext` are
  /// `ImageButton`s in the **top** bar (`fragment_exam_taking.xml:30`, `:62`),
  /// so only its Submit is covered. The screen answers that by using Kotlin's
  /// own 2750 ms `LENGTH_LONG` and by dismissing the snackbar as soon as the
  /// answer changes — so in practice a learner who edits their answer before
  /// pressing again is never blocked. These tests press again *without*
  /// editing, which is the one path that still has to wait.
  Future<void> clearSnackBar(WidgetTester tester) async {
    await tester.pump(const Duration(seconds: 5));
    // And settle, not just pump: jumping time past the duration starts the
    // exit transition but does not finish it, and a half-faded snackbar still
    // absorbs a tap aimed at the button underneath it.
    await tester.pumpAndSettle();
  }

  Future<void> pumpExam(
    WidgetTester tester, {
    String examId = 'exam-1',
    String? courseId,
    UserRow? session,
    ServerConfig? config = server,
    List<Override> overrides = const [],
  }) async {
    await tester.pumpWidget(
      wrapScreen(
        Builder(
          builder: (context) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              final router = GoRouter.of(context);
              final location = router
                  .routerDelegate
                  .currentConfiguration
                  .last
                  .matchedLocation;
              if (location != '/exam') router.push('/exam');
            });
            return const Scaffold(body: Text('ROOT_PAGE'));
          },
        ),
        pushTargets: {
          '/exam': (_) => TakeExamScreen(examId: examId, courseId: courseId),
        },
        overrides: [
          appDatabaseProvider.overrideWith((ref) => db),
          sessionProvider.overrideWith(
            () => _TestSessionNotifier(session ?? user),
          ),
          serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
          // The real source reaches `planetPrefs`, which is `UnimplementedError`
          // in the harness; the uploaders read it at queue time.
          deviceIdentitySourceProvider.overrideWithValue(
            const FixedDeviceIdentitySource(
              DeviceIdentity(
                androidId: 'android-1',
                deviceName: 'Pixel',
                customDeviceName: 'ada-phone',
              ),
            ),
          ),
          ...overrides,
        ],
      ),
    );
    await settleExam(tester);
  }

  /// Seeds the two-question exam most of these tests use: one `select` whose
  /// correct choice is `c2`, one free-text `input` whose answer is `paris`.
  Future<void> seedTwoQuestionExam({String? courseId}) async {
    await seedExam(courseId: courseId);
    await seedQuestion(
      id: 'q1',
      position: 0,
      type: 'select',
      header: 'Capital city',
      body: 'Which one is the capital of France?',
      choices: const [
        ExamChoice(id: 'c1', text: 'Lyon'),
        ExamChoice(id: 'c2', text: 'Paris'),
      ],
      correctChoices: const ['c2'],
    );
    await seedQuestion(
      id: 'q2',
      position: 1,
      type: 'input',
      header: 'Spell it',
      correctChoices: const ['paris'],
    );
  }

  group('question rendering', () {
    testWidgets('an exam with no questions says so', (tester) async {
      await seedExam();
      await pumpExam(tester);

      expect(find.text('This exam has no questions'), findsOneWidget);
    });

    testWidgets('renders the exam name, the counter and the question', (
      tester,
    ) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      expect(find.text('Algebra final'), findsOneWidget);
      expect(find.text('Question 1 / 2'), findsOneWidget);
      expect(find.text('Capital city'), findsOneWidget);
      expect(find.text('Which one is the capital of France?'), findsOneWidget);
      expect(find.text('Lyon'), findsOneWidget);
      expect(find.text('Paris'), findsOneWidget);
    });

    testWidgets('falls back to the generic title when the exam has no name', (
      tester,
    ) async {
      await seedExam(name: null);
      await seedQuestion(id: 'q1', position: 0, header: 'Only question');
      await pumpExam(tester);

      expect(find.text('Take exam'), findsOneWidget);
    });

    testWidgets('a rating question renders one chip per point on its scale', (
      tester,
    ) async {
      await seedExam();
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'ratingScale',
        header: 'How was it?',
        scaleMax: 5,
      );
      await pumpExam(tester);

      expect(find.byType(ChoiceChip), findsNWidgets(5));
      expect(find.widgetWithText(ChoiceChip, '5'), findsOneWidget);
      expect(find.widgetWithText(ChoiceChip, '6'), findsNothing);
    });
  });

  group('navigation', () {
    testWidgets('Next and Previous move between questions', (tester) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      expect(find.text('Previous'), findsNothing, reason: 'first question');
      // Answering first, and answering correctly, is now the precondition for
      // moving on at all — see the retry gate group.
      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);

      expect(find.text('Question 2 / 2'), findsOneWidget);
      expect(find.text('Spell it'), findsOneWidget);
      // The last question swaps Next for Submit.
      expect(find.text('Next'), findsNothing);
      expect(find.text('Submit exam'), findsOneWidget);

      await tester.tap(find.text('Previous'));
      await settleExam(tester);
      expect(find.text('Question 1 / 2'), findsOneWidget);
    });

    /// Regression guard for the per-question answer map. The screen once held a
    /// single shared `_singleAnswer`/`_multipleAnswers` cleared on every
    /// navigation, which lost an answer the moment you moved off its question
    /// and pre-filled the next question's text field with the previous one's.
    testWidgets('answers survive a round trip between questions', (
      tester,
    ) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);

      // The second question's field is its own, not seeded from question one.
      expect(find.widgetWithText(TextField, 'Paris'), findsNothing);
      await tester.enterText(find.byType(TextField), 'Paris');
      await tester.pumpAndSettle();

      await tester.tap(find.text('Previous'));
      await settleExam(tester);
      final radio = tester.widget<RadioGroup<String>>(
        find.byType(RadioGroup<String>),
      );
      expect(radio.groupValue, 'c2', reason: 'the choice is still selected');

      await tester.tap(find.text('Next'));
      await settleExam(tester);
      expect(find.widgetWithText(TextField, 'Paris'), findsOneWidget);
    });
  });

  /// Kotlin's exam is a retry model: `updateAnsDb` returns the verdict and
  /// both `btnNext` and `btnSubmit` bail with the `incorrect_ans` snackbar
  /// when it is false, so the learner cannot leave a question until the answer
  /// is right. Every test in this group failed before the gate was ported —
  /// the screen advanced on any answer, graded once at the end, and never
  /// wrote `mistakes` at all.
  group('the retry gate', () {
    testWidgets('a wrong answer does not advance and says so', (tester) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.text('Lyon'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);

      expect(find.text('Incorrect answer, please try again'), findsOneWidget);
      expect(
        find.text('Question 1 / 2'),
        findsOneWidget,
        reason: 'a wrong answer keeps the learner on its question',
      );
    });

    testWidgets('a right answer advances', (tester) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);

      expect(find.text('Incorrect answer, please try again'), findsNothing);
      expect(find.text('Question 2 / 2'), findsOneWidget);
    });

    /// The accumulator. `mistakes` is `(existing?.mistakes ?: 0) + 1` per
    /// wrong attempt, which only works because the row is already on disk —
    /// the whole reason the attempt is persisted before the first question
    /// rather than graded in widget state at the end.
    testWidgets('each wrong attempt adds a mistake, and the right one does '
        'not clear them', (tester) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      // Two wrong tries.
      for (var attempt = 0; attempt < 2; attempt++) {
        await tester.tap(find.text('Lyon'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Next'));
        await settleExam(tester);
        await clearSnackBar(tester);
      }
      expect(find.text('Question 1 / 2'), findsOneWidget);

      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);

      expect(find.text('Question 2 / 2'), findsOneWidget);
      final answers = await db.select(db.submissionAnswers).get();
      final q1 = answers.firstWhere((answer) => answer.questionId == 'q1');
      expect(q1.mistakes, 2);
      expect(q1.isPassed, isTrue, reason: 'the answer that got through');
      expect(q1.value, 'Paris');
      // `grade = 1` for every exam answer, right or wrong — a "worth one
      // mark" marker, not a score.
      expect(q1.grade, 1);
    });

    /// `checkTextAnswer` is `correctChoices.any { ans.contains(it) }`, not
    /// equality — `ExamAnswerUtilsTest.testCheckCorrectAnswer_InputText`
    /// asserts "the expected word is here" passes a key of "expected word".
    /// The port had exact equality, which under the gate refuses a right
    /// answer that carries a stray word and leaves the learner stuck.
    testWidgets('a text answer passes by containment, as Kotlin marks it', (
      tester,
    ) async {
      await seedExam();
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'input',
        header: 'Spell it',
        correctChoices: const ['paris'],
      );
      await pumpExam(tester);

      await tester.enterText(find.byType(TextField), 'It is Paris, I think');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(find.text('Incorrect answer, please try again'), findsNothing);
      expect(find.text('Exam Complete'), findsOneWidget);
    });

    /// `isQuestionAnswered` gates the press before any answer is written:
    /// Kotlin toasts `please_select_write_your_answer_to_continue` and
    /// returns. Without this an empty press would score a mistake against a
    /// question the learner had not answered yet.
    testWidgets('pressing on with no answer asks for one instead of scoring '
        'a mistake', (tester) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.text('Next'));
      await settleExam(tester);

      expect(
        find.text('Please select / write your answer to continue'),
        findsOneWidget,
      );
      expect(find.text('Question 1 / 2'), findsOneWidget);
      expect(
        await db.select(db.submissionAnswers).get(),
        isEmpty,
        reason: 'nothing was answered, so nothing is a mistake',
      );
    });

    /// `btnBack` calls `updateAnsDb()` and then moves regardless of the
    /// verdict (`ExamTakingFragment.kt:187-194`) — going back is the one route
    /// out of a question left wrong, and it still records the mistake.
    testWidgets('going back records the answer as it stands', (tester) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);
      await tester.enterText(find.byType(TextField), 'Lyon');
      await tester.pumpAndSettle();

      await tester.tap(find.text('Previous'));
      await settleExam(tester);

      expect(find.text('Question 1 / 2'), findsOneWidget);
      final answers = await db.select(db.submissionAnswers).get();
      final q2 = answers.firstWhere((answer) => answer.questionId == 'q2');
      expect(q2.mistakes, 1);
      expect(q2.isPassed, isFalse);
    });

    /// `checkMultipleSelectAnswer` compares the whole sorted set, so a subset
    /// is wrong — and under the gate that means it does not advance.
    testWidgets('a partial multi-select answer does not get through', (
      tester,
    ) async {
      await seedExam();
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'selectMultiple',
        header: 'Pick the primary colours',
        choices: const [
          ExamChoice(id: 'c1', text: 'Red'),
          ExamChoice(id: 'c2', text: 'Blue'),
          ExamChoice(id: 'c3', text: 'Green'),
        ],
        correctChoices: const ['c1', 'c2'],
      );
      await pumpExam(tester);

      await tester.tap(find.text('Red'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(find.text('Incorrect answer, please try again'), findsOneWidget);
      expect(find.text('Exam Complete'), findsNothing);
      final answers = await db.select(db.submissionAnswers).get();
      // Recorded, with its mistake — the attempt is not thrown away, it just
      // does not finish the exam.
      expect(answers.single.valueChoices, ['{"id":"c1","text":"Red"}']);
      expect(answers.single.isPassed, isFalse);
      expect(answers.single.mistakes, 1);

      await clearSnackBar(tester);
      await tester.tap(find.text('Blue'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(find.text('Exam Complete'), findsOneWidget);
      final passed = await db.select(db.submissionAnswers).get();
      expect(passed.single.isPassed, isTrue);
      expect(passed.single.mistakes, 1, reason: 'the earlier try still counts');
    });

    /// A question whose document carries no answer key is the one place the
    /// port deliberately parts from Kotlin. `extractCorrectChoices` returns
    /// `emptyList()` when `correctChoice` is absent or blank, and every check
    /// helper is then vacuously false — so no answer can satisfy the question
    /// and there is no route past it but abandoning the exam. Every
    /// `ratingScale` question in an exam is in that position, as is any
    /// `input`/`textarea` whose author supplied no key. The gate presupposes a
    /// right answer exists; when the document names none, any answer will do.
    ///
    /// (An earlier draft of this comment said `insertCorrectChoice` is only
    /// called for `select*` types "so a Kotlin exam containing one free-text
    /// question cannot be finished at all". That is the reading Phase 110's
    /// audit overturned: `CoursesRepositoryImpl.extractCorrectChoices` — the
    /// parser a course exam actually goes through — runs for **every**
    /// question regardless of type, so a keyed free-text question is
    /// gradeable. The trap is the missing key, not the type.)
    ///
    /// The cost, stated because it is real: a *malformed* exam — an author who
    /// forgot the key on a genuine multiple-choice question — now accepts any
    /// answer and uploads `passed: true`, where Kotlin fails loudly by
    /// trapping the learner. Under `requires grading` a teacher marks it
    /// anyway, so what is lost is a meaningless `passed`, not a wrong grade.
    testWidgets('a question with no answer key accepts any answer', (
      tester,
    ) async {
      await seedExam();
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'textarea',
        header: 'Tell us what you thought',
      );
      await pumpExam(tester);

      await tester.enterText(find.byType(TextField), 'It was fine');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(find.text('Incorrect answer, please try again'), findsNothing);
      expect(find.text('Exam Complete'), findsOneWidget);
      final answers = await db.select(db.submissionAnswers).get();
      expect(answers.single.mistakes, 0);
      expect(answers.single.isPassed, isTrue);
    });
  });

  group('submitting', () {
    testWidgets('a finished exam is sent for grading rather than scored', (
      tester,
    ) async {
      await seedTwoQuestionExam(courseId: 'course-1');
      await pumpExam(tester, courseId: 'course-1');

      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);
      await tester.enterText(find.byType(TextField), 'Paris');
      await tester.pumpAndSettle();

      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(find.text('Exam Complete'), findsOneWidget);
      expect(
        find.text('Thank you for taking this exam! We wish you all the best.'),
        findsOneWidget,
      );
      expect(find.text('Saved offline — pending upload'), findsOneWidget);
      // `continueExam` shows no score, and under the gate a percentage could
      // only ever read 100%.
      expect(find.text('100%'), findsNothing);
      expect(find.textContaining('Correct:'), findsNothing);

      final saved = await db.select(db.submissions).get();
      expect(saved, hasLength(1));
      expect(saved.single.status, 'requires grading');
      // `createExamSubmission` sets no submission-level grade for an exam and
      // neither does `saveExamAnswer`: Planet marks it and the sync-in reads
      // the mark back into this column.
      expect(saved.single.grade, 0);
      expect(saved.single.userId, 'user-1');
      expect(saved.single.uploaded, isFalse);
      // `"$examId@$courseId"`, the shape `createExamSubmission` writes and
      // `ProgressRepository._examIdFromParent` splits to find the exam — the
      // port stored the bare course id, so the per-step mistake counts could
      // never match their exam.
      expect(saved.single.parentId, 'exam-1@course-1');
      expect(await db.submissionDao.pendingUploads(), hasLength(1));

      final answers = await db.select(db.submissionAnswers).get();
      expect(answers, hasLength(2));
      expect(answers.every((answer) => answer.isPassed), isTrue);
      expect(answers.every((answer) => answer.grade == 1), isTrue);
      expect(answers.every((answer) => answer.examId == 'exam-1'), isTrue);
    });

    /// The invariant Phase 106 could only state as an assertion, now a
    /// consequence: every answer in a finished attempt is `isPassed`, because
    /// no wrong one can be left behind — and `mistakes` is what records that
    /// it took three tries.
    testWidgets('a finished attempt passes every answer and keeps the '
        'mistake count', (tester) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.text('Lyon'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);
      await clearSnackBar(tester);
      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);

      await tester.enterText(find.byType(TextField), 'Lyon');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);
      expect(find.text('Exam Complete'), findsNothing);
      await clearSnackBar(tester);

      await tester.enterText(find.byType(TextField), 'Paris');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(find.text('Exam Complete'), findsOneWidget);
      final answers = await db.select(db.submissionAnswers).get();
      expect(answers.every((answer) => answer.isPassed), isTrue);
      expect(
        {for (final answer in answers) answer.questionId: answer.mistakes},
        {'q1': 1, 'q2': 1},
      );
    });

    testWidgets('Finish closes the result dialog and leaves the exam', (
      tester,
    ) async {
      await seedExam();
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'input',
        header: 'Anything',
      );
      await pumpExam(tester);

      await tester.enterText(find.byType(TextField), 'anything at all');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);
      expect(find.text('Exam Complete'), findsOneWidget);

      await tester.tap(find.text('Finish'));
      await settleExam(tester);

      expect(find.text('ROOT_PAGE'), findsOneWidget);
      expect(find.text('Exam Complete'), findsNothing);
    });

    /// The exam session awaits `sessionProvider.future` — which, unlike the
    /// `ref.read(...).valueOrNull` it replaced, can also *reject*. Left
    /// uncaught that reproduces the silence the await was introduced to
    /// remove: the attempt gone with nothing on screen.
    testWidgets('a rejecting session reports the failure instead of silence', (
      tester,
    ) async {
      await seedExam();
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'input',
        header: 'Anything',
      );
      await pumpExam(
        tester,
        overrides: [sessionProvider.overrideWith(_FailingSessionNotifier.new)],
      );

      await tester.enterText(find.byType(TextField), 'anything at all');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(
        find.text('Could not save your exam. Please try again.'),
        findsOneWidget,
      );
      expect(find.text('Exam Complete'), findsNothing);
    });

    /// A failed *save* must not be reported as a wrong answer: the learner
    /// would be told their answer was incorrect when the truth is that
    /// nothing was written.
    testWidgets('a failed save reports it and keeps the user on the exam', (
      tester,
    ) async {
      final repository = _ThrowingSubmissionsRepository();

      await seedExam();
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'input',
        header: 'Anything',
      );
      await pumpExam(
        tester,
        overrides: [
          submissionsRepositoryProvider.overrideWithValue(repository),
        ],
      );

      await tester.enterText(find.byType(TextField), 'anything at all');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(
        find.text('Could not save your exam. Please try again.'),
        findsOneWidget,
      );
      expect(find.text('Incorrect answer, please try again'), findsNothing);
      expect(find.text('Exam Complete'), findsNothing);
      // The submit button is live again rather than stuck on its spinner.
      expect(find.text('Question 1 / 1'), findsOneWidget);
      final button = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Submit exam'),
      );
      expect(button.onPressed, isNotNull);
    });
  });

  group('exiting', () {
    testWidgets('Cancel on the exit prompt keeps the exam open', (
      tester,
    ) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.byIcon(Icons.close));
      await tester.pumpAndSettle();
      expect(find.text('Exit exam?'), findsOneWidget);
      expect(find.text('Your progress will be lost.'), findsOneWidget);

      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();

      expect(find.text('Question 1 / 2'), findsOneWidget);
      expect(find.text('ROOT_PAGE'), findsNothing);
    });

    testWidgets('Exit leaves the exam', (tester) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.byIcon(Icons.close));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Exit'));
      await tester.pumpAndSettle();

      expect(find.text('ROOT_PAGE'), findsOneWidget);
    });
  });

  group('certified-course verification photo', () {
    /// Port note: Kotlin's `ExamTakingFragment.capturePhoto` fires only when
    /// `isCertified && !isMySurvey`. This screen carries no `isMySurvey`
    /// analogue, so certification alone is the gate — and an uncertified course
    /// must not open the camera.
    testWidgets('an uncertified course never opens the camera', (tester) async {
      final capture = _FakePhotoCapture(
        photo: CapturedPhoto(
          bytes: Uint8List.fromList(const [1, 2, 3]),
          filename: 'shot.jpg',
        ),
      );
      PhotoCapture.instance = capture;

      await seedExam(courseId: 'course-1');
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'input',
        header: 'Anything',
      );
      await pumpExam(tester, courseId: 'course-1');

      await tester.enterText(find.byType(TextField), 'anything at all');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(capture.calls, 0);
      expect(await db.select(db.submitPhotosTable).get(), isEmpty);
      expect(find.text('Exam Complete'), findsOneWidget);
    });

    /// The defect this test was written for: the bytes were saved under the
    /// **submission** id while [SubmitPhotosUploader] reads them back with
    /// `SubmitPhotosFiles.existingFileFor(photoId: <the photo row's id>)`. The
    /// two ids are different sha1s, so the lookup always missed, the attachment
    /// step returned early, and the verification photo — the entire point of a
    /// certified course's exam — never reached CouchDB. Nothing failed loudly:
    /// the document uploaded fine, just without its JPEG.
    ///
    /// This asserts the uploader's own read-back, so it fails on the pre-fix
    /// code ("Expected: not null / Actual: <null>") and passes after.
    testWidgets('a certified course captures a photo the uploader can find', (
      tester,
    ) async {
      final capture = _FakePhotoCapture(
        photo: CapturedPhoto(
          bytes: Uint8List.fromList(const [9, 8, 7, 6]),
          filename: 'verification.jpg',
        ),
      );
      PhotoCapture.instance = capture;

      await seedExam(courseId: 'course-1');
      await seedCertification('course-1');
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'input',
        header: 'Anything',
      );
      await pumpExam(tester, courseId: 'course-1');

      await tester.enterText(find.byType(TextField), 'anything at all');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      // The only test that waits on a real file write, so it needs the most
      // wall-clock time: eight rounds is enough for drift, not for
      // `Directory.create` + `writeAsBytes` + the row that follows them.
      await settleExam(tester, rounds: 40);

      expect(capture.calls, 1);

      final photos = await db.select(db.submitPhotosTable).get();
      expect(photos, hasLength(1));
      final row = photos.single;
      expect(row.courseId, 'course-1');
      expect(row.examId, 'exam-1');
      expect(row.memberId, 'user-1');
      expect(row.uploaded, isFalse);
      expect(row.photoLocation, isNotNull);

      // Exactly the lookup `SubmitPhotosUploader._uploadAttachment` performs.
      final name = SubmitPhotosFiles.attachmentNameFor(
        row.photoLocation,
        row.id,
      );
      final onDisk = await tester.runAsync(
        () =>
            SubmitPhotosFiles.existingFileFor(photoId: row.id, filename: name),
      );
      expect(
        onDisk,
        isNotNull,
        reason:
            'the uploader resolves the JPEG from the photo row id, so the '
            'capture has to be written under that id and not the submission id',
      );
      expect(await tester.runAsync(() => onDisk!.readAsBytes()), [9, 8, 7, 6]);
    });

    /// `capturePhoto` swallows a null capture in Kotlin too — no camera, denied
    /// permission, or the user backing out is not grounds to throw the attempt
    /// away.
    testWidgets('a cancelled capture still saves the exam', (tester) async {
      final capture = _FakePhotoCapture();
      PhotoCapture.instance = capture;

      await seedExam(courseId: 'course-1');
      await seedCertification('course-1');
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'input',
        header: 'Anything',
      );
      await pumpExam(tester, courseId: 'course-1');

      await tester.enterText(find.byType(TextField), 'anything at all');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(capture.calls, 1);
      expect(await db.select(db.submitPhotosTable).get(), isEmpty);
      expect(await db.select(db.submissions).get(), hasLength(1));
      expect(find.text('Exam Complete'), findsOneWidget);
    });
  });

  group('recovering from a failed save', () {
    /// The defect: `_ensureSession` memoised the session future with `??=`, so
    /// a **rejected** future stayed cached and no later press ever re-ran the
    /// body. One transient failure ended the exam for the rest of the mount —
    /// every press replayed the same error, with no submission row and no
    /// answers, and the only way out was to leave the screen.
    ///
    /// The pre-existing `a failed save reports it…` test could not catch this:
    /// it asserts the button is live again and stops, so it passed while the
    /// screen was dead. This one presses a second time.
    testWidgets('a second press after a transient failure gets through', (
      tester,
    ) async {
      final real = SubmissionsRepository(
        _UnusedApi(),
        db.submissionDao,
        db.submitPhotosDao,
        db.surveyDao,
        db.examDao,
      );
      final flaky = _FlakySubmissionsRepository(real);

      await seedTwoQuestionExam();
      await pumpExam(
        tester,
        overrides: [submissionsRepositoryProvider.overrideWithValue(flaky)],
      );

      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);

      expect(
        find.text('Could not save your exam. Please try again.'),
        findsOneWidget,
      );
      expect(find.text('Question 1 / 2'), findsOneWidget);
      await clearSnackBar(tester);

      // Same answer, pressed again. Nothing else changed.
      await tester.tap(find.text('Next'));
      await settleExam(tester);

      expect(
        flaky.calls,
        2,
        reason: 'the rejected session must not be memoised',
      );
      expect(find.text('Question 2 / 2'), findsOneWidget);
      final answers = await db.select(db.submissionAnswers).get();
      expect(answers.single.questionId, 'q1');
      expect(answers.single.isPassed, isTrue);
    });
  });

  group('question types the server did not lowercase', () {
    /// `ExamGrading.isCorrect` and `AnswerShape.forQuestion` both normalise the
    /// type (Kotlin compares with `ignoreCase = true`); the renderer matched
    /// exactly. So a question typed `"Select"` drew a **text field**, the typed
    /// answer went through `AnswerShape`'s select branch and was stored as the
    /// empty string, and the grader graded an empty selection against a
    /// non-empty key — the answer discarded and the gate unable to open.
    testWidgets('a capitalised select still renders its choices', (
      tester,
    ) async {
      await seedExam();
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'Select',
        header: 'Capital city',
        choices: const [
          ExamChoice(id: 'c1', text: 'Lyon'),
          ExamChoice(id: 'c2', text: 'Paris'),
        ],
        correctChoices: const ['c2'],
      );
      await pumpExam(tester);

      expect(find.byType(RadioGroup<String>), findsOneWidget);
      expect(find.byType(TextField), findsNothing);

      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(find.text('Exam Complete'), findsOneWidget);
      final answers = await db.select(db.submissionAnswers).get();
      expect(answers.single.valueChoices, ['{"id":"c2","text":"Paris"}']);
      expect(answers.single.isPassed, isTrue);
    });

    testWidgets('a capitalised selectMultiple still renders its checkboxes', (
      tester,
    ) async {
      await seedExam();
      await seedQuestion(
        id: 'q1',
        position: 0,
        type: 'SelectMultiple',
        header: 'Pick two',
        choices: const [
          ExamChoice(id: 'c1', text: 'Red'),
          ExamChoice(id: 'c2', text: 'Blue'),
        ],
        correctChoices: const ['c1', 'c2'],
      );
      await pumpExam(tester);

      expect(find.byType(CheckboxListTile), findsNWidgets(2));
      expect(find.byType(TextField), findsNothing);
    });
  });

  group('going back on an unanswered question', () {
    /// A deliberate divergence: Kotlin's `btnBack` saves unconditionally, so an
    /// untouched question is written with `mistakes + 1` and `isPassed = false`
    /// — and that row uploads, because `getPendingExamResults` has no status
    /// filter. Scoring a mistake against a question the learner never answered
    /// is a defect, not a behaviour.
    testWidgets('records nothing rather than a mistake', (tester) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await settleExam(tester);

      // Question two is untouched.
      await tester.tap(find.text('Previous'));
      await settleExam(tester);

      expect(find.text('Question 1 / 2'), findsOneWidget);
      final answers = await db.select(db.submissionAnswers).get();
      expect(answers, hasLength(1));
      expect(answers.single.questionId, 'q1');
    });
  });
}

/// Only ever handed to a [SubmissionsRepository] that the test drives through
/// its local DAOs; no method on it is called.
class _UnusedApi extends Fake implements PlanetApi {}
