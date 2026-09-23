import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/providers/ratings_provider.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/ui/ratings/rating_dialog.dart';

import '../support/widget_harness.dart';

/// Stands in for the real [RatingActions] so the dialog can be driven without
/// a session, a repository or an outbox. [verdict] is the whole point: the
/// defect being closed here is that the dialog could not tell a rating that
/// was recorded from one that was not.
class _FakeRatingActions implements RatingActions {
  _FakeRatingActions(this.verdict);

  final bool verdict;
  int calls = 0;

  @override
  Ref get ref => throw UnimplementedError();

  @override
  Future<bool> submit({
    required RatingTarget target,
    required String title,
    required int rate,
    String? comment,
  }) async {
    calls++;
    return verdict;
  }

  @override
  Future<int> queuePending() async => 0;
}

void main() {
  const target = (type: 'course', itemId: 'course-1');

  testWidgets('loads existing rating and aggregate summary', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const RatingDialog(target: target, title: 'Open learning'),
        overrides: [
          ratingSummaryProvider(target).overrideWith(
            (ref) => Stream.value(
              const RatingSummary(
                average: 4.5,
                total: 2,
                userRating: 4,
                userComment: 'Very useful',
              ),
            ),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Rate Open learning'), findsOneWidget);
    expect(find.textContaining('2 ratings'), findsOneWidget);
    expect(find.text('Very useful'), findsOneWidget);
    expect(find.byIcon(Icons.star), findsNWidgets(4));
    expect(find.byIcon(Icons.star_border), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Submit rating'), findsOneWidget);
  });

  testWidgets('requires a star selection', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const RatingDialog(target: target, title: 'Course'),
        overrides: [
          ratingSummaryProvider(target).overrideWith(
            (ref) => Stream.value(const RatingSummary(average: 0, total: 0)),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Choose a rating'), findsOneWidget);
    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Submit rating'),
    );
    expect(button.onPressed, isNull);
  });

  /// Opens the dialog through `showDialog`, as all three of its call sites do
  /// (`take_course_screen`, `course_detail_screen`, `resource_detail_screen`),
  /// and records what it popped with. Rendering it as the home widget instead
  /// would make `Navigator.pop` pop the whole app, which is exactly the
  /// difference this file has to be able to see.
  Future<({_FakeRatingActions actions, List<Object?> popped})> openDialog(
    WidgetTester tester, {
    required bool verdict,
  }) async {
    final actions = _FakeRatingActions(verdict);
    final popped = <Object?>[];
    await tester.pumpWidget(
      wrapScreen(
        Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: ElevatedButton(
                onPressed: () async => popped.add(
                  await showDialog<bool>(
                    context: context,
                    builder: (_) =>
                        const RatingDialog(target: target, title: 'Course'),
                  ),
                ),
                child: const Text('open'),
              ),
            ),
          ),
        ),
        overrides: [
          ratingSummaryProvider(target).overrideWith(
            (ref) => Stream.value(const RatingSummary(average: 0, total: 0)),
          ),
          ratingActionsProvider.overrideWithValue(actions),
        ],
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Set rating to 4'));
    await tester.pump();
    await tester.enterText(find.byType(TextField), 'Very useful');
    await tester.pump();
    // Never `pumpAndSettle` past this point: while `_submitting` is true the
    // button holds a `CircularProgressIndicator`, whose indefinite animation
    // spins `pumpAndSettle` to its ten-minute default and reads exactly like a
    // hang.
    await tester.tap(find.widgetWithText(FilledButton, 'Submit rating'));
    await tester.pump();
    await tester.pump();
    return (actions: actions, popped: popped);
  }

  testWidgets('a rating that was not recorded keeps the dialog open and says '
      'so', (tester) async {
    // The defect in one test. `_submit` used to throw the result away and call
    // `Navigator.pop(context, true)` unconditionally, so a rating that reached
    // nothing — an unresolved session, a failing write, an outbox that could
    // not take it — closed the dialog with the same gesture as one that had.
    //
    // `RatingsFragment.kt:115-117` toasts `SubmitState.Error` and does **not**
    // dismiss; only `SubmitState.Success` reaches `dismiss()` (`:104-113`).
    final opened = await openDialog(tester, verdict: false);

    expect(opened.actions.calls, 1);
    expect(opened.popped, isEmpty, reason: 'the dialog popped on a failure');
    expect(
      find.byType(RatingDialog),
      findsOneWidget,
      reason: 'the dialog closed on a rating that was never recorded',
    );
    // The message is the assertion that matters: a test that only counted the
    // call could not tell a reported failure from a silent one, which is the
    // entire class being closed.
    expect(
      find.text('Could not submit your rating. Please try again.'),
      findsOneWidget,
    );
  });

  testWidgets('a failed rating leaves Submit live and the input intact', (
    tester,
  ) async {
    // `updateSubmitButtonState` (`RatingsFragment.kt:147-151`) re-enables on
    // anything that is not `Submitting`, so `Error` hands the button back and
    // the rating bar and comment keep what the person entered — the retry
    // costs them nothing.
    await openDialog(tester, verdict: false);

    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Submit rating'),
    );
    expect(button.onPressed, isNotNull);
    expect(find.byIcon(Icons.star), findsNWidgets(4));
    // The comment half of "input intact", which the test name claimed and
    // nothing held: clearing `_comment` in the failure arm left every
    // assertion here green while costing the person everything they had
    // typed. `_ratingState` is reassigned on Kotlin's success path only
    // (`RatingsViewModel.kt:103`), so the collector that writes `etComment`
    // never fires on a failure and the field keeps its text.
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller!.text,
      'Very useful',
    );
  });

  testWidgets('changing the rating retires the refusal', (tester) async {
    // A message that outlives the input it describes is a message about
    // nothing. `RatingsFragment.kt:59-64` hides the dialog's other error on
    // any `fromUser` change, and Kotlin's toast expires by itself in ~2s
    // where this text would sit there indefinitely.
    await openDialog(tester, verdict: false);
    expect(
      find.text('Could not submit your rating. Please try again.'),
      findsOneWidget,
    );

    await tester.tap(find.byTooltip('Set rating to 5'));
    await tester.pump();

    expect(
      find.text('Could not submit your rating. Please try again.'),
      findsNothing,
    );
  });

  testWidgets('a refusal still shows when the summary stream errors', (
    tester,
  ) async {
    // The message used to live inside `summary.when(data:)`. A stream error
    // arriving after the person had chosen a rating swapped the content for
    // `ratingsUnavailable` while Submit stayed enabled — so a failed submit
    // set the flag with nowhere to draw it and the dialog refused to close
    // saying nothing at all.
    //
    // Driven by erroring the stream *after* the rating is set, because that
    // ordering is the only one that reaches the state.
    //
    // Mutating this one took two goes, and the first is worth recording.
    // `if (_failed && summary.hasValue)` left the whole suite green — Riverpod
    // 3's `AsyncError` keeps the previous value, so `hasValue` is still true
    // here and the guard changed nothing. What the defect actually was is that
    // `when` does not call its `data:` builder at all on an error, so the
    // equivalent mutation is `if (_failed && !summary.hasError)`, and that
    // does go red. A mutation that stays green means "nothing was mutated" as
    // readily as "nothing is pinned".
    final actions = _FakeRatingActions(false);
    final controller = StreamController<RatingSummary>();
    addTearDown(controller.close);

    await tester.pumpWidget(
      wrapScreen(
        const RatingDialog(target: target, title: 'Course'),
        overrides: [
          ratingSummaryProvider(
            target,
          ).overrideWith((ref) => controller.stream),
          ratingActionsProvider.overrideWithValue(actions),
        ],
      ),
    );
    controller.add(const RatingSummary(average: 0, total: 0));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Set rating to 4'));
    await tester.pump();
    controller.addError(StateError('the ratings stream died'));
    await tester.pump();

    expect(find.text('Ratings are unavailable'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Submit rating'));
    await tester.pump();
    await tester.pump();

    expect(actions.calls, 1);
    expect(
      find.text('Could not submit your rating. Please try again.'),
      findsOneWidget,
      reason: 'the dialog refused to close and would not say why',
    );
  });

  testWidgets('a recorded rating closes the dialog', (tester) async {
    final opened = await openDialog(tester, verdict: true);

    expect(opened.actions.calls, 1);
    await tester.pumpAndSettle();
    expect(find.byType(RatingDialog), findsNothing);
    expect(opened.popped, [true]);
    expect(
      find.text('Could not submit your rating. Please try again.'),
      findsNothing,
    );
  });
}
