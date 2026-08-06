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

  testWidgets('renders submission details from the offline cache', (
    tester,
  ) async {
    final row = SubmissionRow(
      id: 'submission-2',
      type: 'exam',
      user: 'Ada Learner',
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
}
