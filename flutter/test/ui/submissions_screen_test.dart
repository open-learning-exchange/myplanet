import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/submissions_provider.dart';
import 'package:myplanet/ui/submissions/submissions_screen.dart';
import 'package:myplanet/ui/submissions/submission_detail_screen.dart';

import '../support/widget_harness.dart';

void main() {
  testWidgets('renders cached submission status and grade', (tester) async {
    final row = SubmissionRow(
      id: 'submission-1',
      startTime: 0,
      parent: 'Safety exam',
      userId: 'user-1',
      lastUpdateTime: DateTime(2026, 8, 3).millisecondsSinceEpoch,
      grade: 85,
      status: 'complete',
      uploaded: true,
      isUpdated: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const SubmissionsScreen(),
        overrides: [
          submissionsProvider.overrideWith((ref) => Stream.value([row])),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Submissions'), findsOneWidget);
    expect(find.text('Safety exam'), findsOneWidget);
    expect(find.textContaining('complete'), findsOneWidget);
    expect(find.text('Grade: 85'), findsOneWidget);
    expect(find.byIcon(Icons.assignment_turned_in), findsOneWidget);
  });

  // The duplicate removing the prune exposes: a locally authored row keeps its
  // sha1 key after upload, so the walk re-inserts the same document under its
  // CouchDB `_id`. Kotlin collapses the pair and badges it `(N)`
  // (`SubmissionsAdapter.updateSubmissionCount`) rather than deleting either.
  testWidgets('two rows for one attempt render once, badged with the count', (
    tester,
  ) async {
    SubmissionRow attempt(String id, int updated) => SubmissionRow(
      id: id,
      parentId: 'exam-1@course-1',
      parent: '{"_id":"exam-1","name":"Week 1 quiz"}',
      startTime: 0,
      lastUpdateTime: updated,
      grade: 0,
      status: 'complete',
      uploaded: true,
      isUpdated: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const SubmissionsScreen(),
        overrides: [
          submissionsProvider.overrideWith(
            (ref) =>
                Stream.value([attempt('couch-1', 20), attempt('sha1', 10)]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Week 1 quiz (2)'), findsOneWidget);
    expect(find.byType(ListTile), findsOneWidget);
  });

  testWidgets('filters pending and complete submissions', (tester) async {
    final pending = SubmissionRow(
      id: 'pending',
      type: 'survey',
      startTime: 0,
      lastUpdateTime: 1,
      grade: 0,
      uploaded: false,
      isUpdated: true,
    );
    final complete = SubmissionRow(
      id: 'complete',
      type: 'exam',
      startTime: 0,
      lastUpdateTime: 2,
      grade: 70,
      status: 'complete',
      uploaded: true,
      isUpdated: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const SubmissionsScreen(),
        overrides: [
          submissionsProvider.overrideWith(
            (ref) => Stream.value([pending, complete]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('survey'), findsOneWidget);
    expect(find.text('exam'), findsOneWidget);
    await tester.tap(find.text('Pending'));
    await tester.pumpAndSettle();
    expect(find.text('survey'), findsOneWidget);
    expect(find.text('exam'), findsNothing);
  });

  /// `saveExamAnswer` gives a **finished exam** the status `requires grading`
  /// — `complete` is the survey value, and an exam is not complete until
  /// somebody marks it. Without it on `_isComplete`'s list a just-submitted
  /// exam would file under Pending until its upload landed, which is the
  /// opposite of what the learner just did.
  testWidgets('a submitted exam awaiting grading counts as complete', (
    tester,
  ) async {
    const submitted = SubmissionRow(
      id: 'awaiting',
      type: 'exam',
      startTime: 0,
      lastUpdateTime: 2,
      grade: 0,
      status: 'requires grading',
      // Not yet on the wire, so `uploaded` cannot be what carries it.
      uploaded: false,
      isUpdated: true,
    );
    const pending = SubmissionRow(
      id: 'in-progress',
      type: 'exam',
      startTime: 0,
      lastUpdateTime: 1,
      grade: 0,
      status: 'pending',
      uploaded: false,
      isUpdated: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const SubmissionsScreen(),
        overrides: [
          submissionsProvider.overrideWith(
            (ref) => Stream.value([submitted, pending]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Complete'));
    await tester.pumpAndSettle();
    expect(find.textContaining('requires grading'), findsOneWidget);
    expect(find.textContaining('pending'), findsNothing);

    await tester.tap(find.text('Pending'));
    await tester.pumpAndSettle();
    expect(find.textContaining('pending'), findsOneWidget);
    expect(find.textContaining('requires grading'), findsNothing);
  });

  /// The per-answer `grade` is `1` for **every** exam answer, right or wrong —
  /// `saveExamAnswer`'s "worth one mark" marker, not a score. A badge reading
  /// it would say "1" on every row and mean nothing, and
  /// `QuestionAnswerAdapter.bind` renders it nowhere. This pins the absence,
  /// because the temptation to restore the mark later is real and a comment
  /// alone will not stop it.
  testWidgets('an answer shows no mark of its own', (tester) async {
    const row = SubmissionRow(
      id: 'submission-5',
      userId: 'user-1',
      type: 'exam',
      startTime: 0,
      lastUpdateTime: 0,
      grade: 0,
      status: 'requires grading',
      uploaded: false,
      isUpdated: true,
    );
    await tester.pumpWidget(
      wrapScreen(
        const SubmissionDetailScreen(submissionId: 'submission-5'),
        overrides: [
          submissionProvider(
            'submission-5',
          ).overrideWith((ref) => Stream.value(row)),
          submissionAnswersProvider('submission-5').overrideWith(
            (ref) => Stream.value([
              const SubmissionAnswerRow(
                id: 'submission-5:q-1',
                submissionId: 'submission-5',
                questionId: 'q-1',
                value: 'Paris',
                valueChoices: [],
                mistakes: 3,
                isPassed: true,
                grade: 1,
              ),
            ]),
          ),
          submissionQuestionsProvider('submission-5').overrideWith(
            (ref) => Stream.value([
              const SubmissionQuestionRow(
                id: 'submission-5:q-1',
                submissionId: 'submission-5',
                header: 'Capital city',
                correctChoices: ['paris'],
                choices: [],
                position: 0,
              ),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The body is a `ListView(children: [...])`: it builds its children
    // eagerly but only *mounts* those in the viewport, and `find.text` walks
    // the element tree — so the answer card sits below the 600px test fold
    // until it is scrolled to. Asserting the badge's absence without scrolling
    // would pass because nothing is mounted, not because nothing is drawn.
    await tester.scrollUntilVisible(find.text('Capital city'), 200);
    await tester.pumpAndSettle();

    expect(find.text('Paris'), findsOneWidget);
    expect(
      find.text('1'),
      findsNothing,
      reason: 'the marker is not a score and is not rendered',
    );
  });

  testWidgets('renders submission details from the offline cache', (
    tester,
  ) async {
    // `user` is JSON text on every write path — `gson.toJson(userJson)` on the
    // pull, `jsonEncode` on each local write — and the screen reads the `name`
    // out of it (`getNormalizedSubmitterName`). A bare `'Ada Learner'` here was
    // a shape no writer produces, which is how the screen printing the whole
    // column went unnoticed.
    final row = SubmissionRow(
      id: 'submission-2',
      type: 'exam',
      user: '{"_id":"org.couchdb.user:ada","name":"Ada Learner"}',
      startTime: 0,
      lastUpdateTime: 0,
      grade: 92,
      status: 'graded',
      uploaded: true,
      isUpdated: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const SubmissionDetailScreen(submissionId: 'submission-2'),
        overrides: [
          submissionProvider(
            'submission-2',
          ).overrideWith((ref) => Stream.value(row)),
          submissionAnswersProvider('submission-2').overrideWith(
            (ref) => Stream.value([
              const SubmissionAnswerRow(
                id: 'submission-2:q-1',
                submissionId: 'submission-2',
                questionId: 'q-1',
                value: 'Paris',
                valueChoices: [],
                mistakes: 0,
                isPassed: true,
                grade: 10,
              ),
            ]),
          ),
          submissionQuestionsProvider('submission-2').overrideWith(
            (ref) => Stream.value([
              const SubmissionQuestionRow(
                id: 'submission-2:q-1',
                submissionId: 'submission-2',
                header: 'Capital city',
                body: 'What is the capital?',
                type: 'selectOne',
                correctChoices: ['paris'],
                choices: ['Paris', 'Rome'],
                marks: '10',
                position: 0,
              ),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Submission details'), findsOneWidget);
    expect(find.text('Ada Learner'), findsOneWidget);
    expect(find.text('92'), findsOneWidget);
    expect(find.text('Uploaded'), findsOneWidget);
    await tester.drag(find.byType(ListView), const Offset(0, -500));
    await tester.pumpAndSettle();
    expect(find.text('Answers'), findsOneWidget);
    expect(find.text('Capital city'), findsOneWidget);
    expect(find.textContaining('Choices: Paris, Rome'), findsOneWidget);
    expect(find.byIcon(Icons.check_circle), findsOneWidget);
  });

  testWidgets('a choice answer shows the choice label, not its JSON', (
    tester,
  ) async {
    // An answer records the whole `{id, text}` object as a JSON string, the
    // way `Answer.valueChoicesArray` reads it straight back — so both Kotlin
    // display paths decode the label out of it
    // (`SubmissionsRepositoryExporter.formatAnswer` does
    // `JSONObject(choice).optString("text", choice)`). Joining the entries
    // verbatim put raw JSON in front of the user.
    const row = SubmissionRow(
      id: 'submission-3',
      userId: 'user-1',
      type: 'survey',
      startTime: 0,
      lastUpdateTime: 0,
      grade: 0,
      status: 'complete',
      uploaded: false,
      isUpdated: true,
    );
    await tester.pumpWidget(
      wrapScreen(
        const SubmissionDetailScreen(submissionId: 'submission-3'),
        overrides: [
          submissionProvider(
            'submission-3',
          ).overrideWith((ref) => Stream.value(row)),
          submissionAnswersProvider('submission-3').overrideWith(
            (ref) => Stream.value([
              const SubmissionAnswerRow(
                id: 'submission-3:q-1',
                submissionId: 'submission-3',
                questionId: 'q-1',
                valueChoices: [
                  '{"id":"water","text":"Water"}',
                  '{"id":"power","text":"Power"}',
                ],
                mistakes: 0,
                isPassed: false,
                grade: 0,
              ),
            ]),
          ),
          submissionQuestionsProvider(
            'submission-3',
          ).overrideWith((ref) => Stream.value(const [])),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.drag(find.byType(ListView), const Offset(0, -500));
    await tester.pumpAndSettle();
    expect(find.text('Water, Power'), findsOneWidget);
  });

  testWidgets('a choice answer is marked correct by its choice id', (
    tester,
  ) async {
    // The tile's own correctness check compares the stored answer against the
    // question's `correctChoices`, which are choice **ids**
    // (`ExamMapper._parseCorrectChoices`, and the `correctChoice` array a
    // synced question carries). A stored answer entry is the whole
    // `{id, text}` object, so comparing the entries verbatim could only ever
    // match the exam path's bare ids — and once both paths store objects, as
    // Kotlin's `saveExamAnswer` does, it matched nothing at all.
    const row = SubmissionRow(
      id: 'submission-4',
      userId: 'user-1',
      type: 'exam',
      startTime: 0,
      lastUpdateTime: 0,
      grade: 0,
      status: 'complete',
      uploaded: false,
      isUpdated: true,
    );
    await tester.pumpWidget(
      wrapScreen(
        const SubmissionDetailScreen(submissionId: 'submission-4'),
        overrides: [
          submissionProvider(
            'submission-4',
          ).overrideWith((ref) => Stream.value(row)),
          submissionAnswersProvider('submission-4').overrideWith(
            (ref) => Stream.value([
              const SubmissionAnswerRow(
                id: 'submission-4:q-1',
                submissionId: 'submission-4',
                questionId: 'q-1',
                valueChoices: ['{"id":"paris","text":"Paris"}'],
                mistakes: 0,
                // Not pre-graded — the tile's own check is what is under test.
                isPassed: false,
                grade: 0,
              ),
            ]),
          ),
          submissionQuestionsProvider('submission-4').overrideWith(
            (ref) => Stream.value([
              const SubmissionQuestionRow(
                id: 'submission-4:q-1',
                submissionId: 'submission-4',
                header: 'Capital city',
                type: 'select',
                correctChoices: ['paris'],
                choices: ['Paris', 'Rome'],
                position: 0,
              ),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.drag(find.byType(ListView), const Offset(0, -500));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.check_circle), findsOneWidget);
  });
}
