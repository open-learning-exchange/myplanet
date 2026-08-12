import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/ui/teams/team_voices_screen.dart';

import '../support/widget_harness.dart';

NewsRow _post(String id) => NewsRow(
  id: id,
  message: 'post $id',
  docType: 'message',
  viewableBy: 'teams',
  viewableId: 'team-1',
  updatedDate: 0,
  time: 0,
  imageUrls: const [],
  labels: const [],
  newsCreatedDate: 0,
  newsUpdatedDate: 0,
  chat: false,
  isEdited: false,
  editedTime: 0,
);

void main() {
  late AppDatabase database;

  setUp(() => database = AppDatabase.memory());
  tearDown(() => database.close());

  testWidgets(
    'opening team voices moves the chat watermark to the post count',
    (tester) async {
      await tester.pumpWidget(
        wrapScreen(
          const TeamVoicesScreen(teamId: 'team-1'),
          overrides: [
            // The real database, so the watermark write is observable — but the
            // screen's *streams* are overridden rather than run against it. A
            // live drift query stream leaves a pending timer when the provider
            // scope is torn down, which is the harness backstop firing.
            appDatabaseProvider.overrideWithValue(database),
            teamVoicesProvider.overrideWith(
              (ref, teamId) => Stream.value([_post('post-1'), _post('post-2')]),
            ),
            teamProvider.overrideWith((ref, teamId) async => null),
            teamMembershipsProvider.overrideWith(
              (ref) => Stream.value(const {}),
            ),
            voiceReplyCountProvider.overrideWith((ref, newsId) async => 0),
          ],
        ),
      );
      await tester.pumpAndSettle();

      // Without this wiring the dashboard's chat badge could never appear (the
      // `hasChat` check requires a watermark row to exist) nor clear.
      final watermark = await database.teamNotificationDao.findByParentAndType(
        'team-1',
        'chat',
      );
      expect(watermark?.lastCount, 2);
    },
  );
}
