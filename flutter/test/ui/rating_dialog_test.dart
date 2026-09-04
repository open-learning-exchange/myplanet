import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/providers/ratings_provider.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/ui/ratings/rating_dialog.dart';

import '../support/widget_harness.dart';

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
}
