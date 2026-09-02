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

/// Fails the persist step the way a full disk or a closed database would, so
/// the screen's failure branch can be exercised. A [Fake] rather than a mock
/// because `createExamDraft` takes an `ExamRow`, and mocktail's `any()` would
/// need a registered fallback for it.
class _ThrowingSubmissionsRepository extends Fake
    implements SubmissionsRepository {
  @override
  Future<String> createExamDraft({
    required ExamRow exam,
    required List<ExamQuestionRow> questions,
    required String userId,
    required Map<String, ExamDraftAnswer> answers,
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
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();

      expect(find.text('Question 2 / 2'), findsOneWidget);
      expect(find.text('Spell it'), findsOneWidget);
      // The last question swaps Next for Submit.
      expect(find.text('Next'), findsNothing);
      expect(find.text('Submit exam'), findsOneWidget);

      await tester.tap(find.text('Previous'));
      await tester.pumpAndSettle();
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
      await tester.pumpAndSettle();

      // The second question's field is its own, not seeded from question one.
      expect(find.widgetWithText(TextField, 'Paris'), findsNothing);
      await tester.enterText(find.byType(TextField), 'Paris');
      await tester.pumpAndSettle();

      await tester.tap(find.text('Previous'));
      await tester.pumpAndSettle();
      final radio = tester.widget<RadioGroup<String>>(
        find.byType(RadioGroup<String>),
      );
      expect(radio.groupValue, 'c2', reason: 'the choice is still selected');

      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      expect(find.widgetWithText(TextField, 'Paris'), findsOneWidget);
    });
  });

  group('submitting', () {
    testWidgets('a fully correct attempt is persisted and scored 100%', (
      tester,
    ) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.text('Paris'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Paris');
      await tester.pumpAndSettle();

      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(find.text('Exam Complete'), findsOneWidget);
      expect(find.text('100%'), findsOneWidget);
      expect(find.text('Correct: 2 / 2'), findsOneWidget);
      expect(find.text('Saved offline — pending upload'), findsOneWidget);

      // The attempt reached the database, which is the whole point: a graded
      // exam that lives only in a dialog is lost when the dialog closes.
      final saved = await db.select(db.submissions).get();
      expect(saved, hasLength(1));
      expect(saved.single.grade, 100);
      expect(saved.single.status, 'complete');
      expect(saved.single.userId, 'user-1');
      expect(saved.single.uploaded, isFalse);

      final answers = await db.select(db.submissionAnswers).get();
      expect(answers, hasLength(2));
      expect(answers.every((answer) => answer.isPassed), isTrue);
    });

    testWidgets('a wrong choice scores zero and is still recorded', (
      tester,
    ) async {
      await seedTwoQuestionExam();
      await pumpExam(tester);

      await tester.tap(find.text('Lyon'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Lyon');
      await tester.pumpAndSettle();

      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(find.text('0%'), findsOneWidget);
      expect(find.text('Correct: 0 / 2'), findsOneWidget);
      final saved = await db.select(db.submissions).get();
      expect(saved.single.grade, 0);
    });

    /// `selectMultiple` is all-or-nothing in `ExamGrading.isSelectionCorrect`,
    /// matching the Kotlin's marking: a subset of the answer key does not pass.
    testWidgets('a partial multi-select answer does not pass', (tester) async {
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

      expect(find.text('0%'), findsOneWidget);

      // Adding the second correct choice would have passed it.
      final answers = await db.select(db.submissionAnswers).get();
      expect(answers.single.valueChoices, ['c1']);
      expect(answers.single.isPassed, isFalse);
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

      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);
      expect(find.text('Exam Complete'), findsOneWidget);

      await tester.tap(find.text('Finish'));
      await settleExam(tester);

      expect(find.text('ROOT_PAGE'), findsOneWidget);
      expect(find.text('Exam Complete'), findsNothing);
    });

    /// The submit path awaits `sessionProvider.future` — which, unlike the
    /// `ref.read(...).valueOrNull` it replaced, can also *reject*. Left
    /// uncaught that reproduces the silence the await was introduced to
    /// remove: the graded attempt gone with nothing on screen.
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

      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(
        find.text('Could not save your exam. Please try again.'),
        findsOneWidget,
      );
      expect(find.text('Exam Complete'), findsNothing);
    });

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

      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(
        find.text('Could not save your exam. Please try again.'),
        findsOneWidget,
      );
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

      await tester.tap(find.text('Submit exam'));
      await settleExam(tester);

      expect(capture.calls, 1);
      expect(await db.select(db.submitPhotosTable).get(), isEmpty);
      expect(await db.select(db.submissions).get(), hasLength(1));
      expect(find.text('Exam Complete'), findsOneWidget);
    });
  });
}
