import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/ui/voices/voices_screen.dart';

import '../support/widget_harness.dart';

void main() {
  NewsRow post({
    required String id,
    String? message,
    String? userName,
    String? docId,
    bool isEdited = false,
    List<String> labels = const [],
  }) => NewsRow(
    id: id,
    docId: docId,
    message: message,
    userName: userName,
    time: 1735689600000,
    updatedDate: 0,
    imageUrls: const [],
    labels: labels,
    newsCreatedDate: 0,
    newsUpdatedDate: 0,
    chat: false,
    isEdited: isEdited,
    editedTime: 0,
  );

  testWidgets('renders the community feed', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const VoicesScreen(),
        overrides: [
          communityFeedProvider.overrideWith(
            (ref) => Stream.value([
              post(
                id: 'n1',
                message: 'Water pump is fixed',
                userName: 'Ada',
                docId: 'server-1',
                labels: const ['announcement'],
              ),
            ]),
          ),
          voiceReplyCountProvider('n1').overrideWith((ref) async => 3),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Water pump is fixed'), findsOneWidget);
    expect(find.text('Ada'), findsOneWidget);
    expect(find.text('announcement'), findsOneWidget);
    expect(find.text('3 replies'), findsOneWidget);
  });

  testWidgets('marks a post that has not reached the server', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const VoicesScreen(),
        overrides: [
          communityFeedProvider.overrideWith(
            (ref) => Stream.value([
              post(id: 'n1', message: 'Queued offline', userName: 'Ada'),
            ]),
          ),
          voiceReplyCountProvider('n1').overrideWith((ref) async => 0),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The upload badge is the only signal that a post is still local.
    expect(find.byIcon(Icons.cloud_upload_outlined), findsOneWidget);
    expect(find.text('No replies'), findsOneWidget);
  });

  testWidgets('shows the empty state', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const VoicesScreen(),
        overrides: [
          communityFeedProvider.overrideWith((ref) => Stream.value(const [])),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No voices yet'), findsOneWidget);
  });
}
