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
      // The rows have to be in the database, not just in the overridden
      // stream: `updateTeamNotification` **derives** the watermark from
      // `NewsDao.countTopLevelByTeam` rather than taking the count the screen
      // passes. That is the port of Kotlin, whose
      // `updateTeamNotification(teamId, news)` receives the list
      // `getFilteredNews` just loaded with that same statement — the badge is
      // `watermark < count` and only means anything while both sides count one
      // population. This test used to pass a stream of two and assert two,
      // which could not tell a derived watermark from a pass-through.
      for (final row in [_post('post-1'), _post('post-2')]) {
        await database.newsDao.upsert(row.toCompanion(false));
      }

      await tester.pumpWidget(
        wrapScreen(
          const TeamVoicesScreen(teamId: 'team-1'),
          overrides: [
            // The real database, so the watermark write is observable — but the
            // screen's *streams* are overridden rather than run against it. A
            // live drift query stream leaves a pending timer when the provider
            // scope is torn down, which is the harness backstop firing.
            appDatabaseProvider.overrideWithValue(database),
            // **Load-bearing: this stream deliberately disagrees with the
            // database.** The screen calls
            // `updateTeamNotification(teamId, rows.length)` with *five*, while
            // the table holds two qualifying rows. A watermark taken from the
            // caller writes 5; the derivation writes 2. Make these agree and
            // the test cannot tell the two readings apart — which is what it
            // did before, with two and two.
            teamVoicesProvider.overrideWith(
              (ref, teamId) => Stream.value([
                for (var i = 1; i <= 5; i++) _post('streamed-\$i'),
              ]),
            ),
            teamProvider.overrideWith((ref, teamId) async => null),
            teamMembershipsProvider.overrideWith(
              (ref) => Stream.value(const {}),
            ),
            voiceReplyCountProvider.overrideWith((ref, newsId) async => 0),
          ],
          fallbackDatabase: false,
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
