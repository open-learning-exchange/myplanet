import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/notifications_repository.dart';
import 'package:myplanet/ui/notifications/notification_destination.dart';
import 'package:myplanet/ui/notifications/notification_grouping.dart';

/// Pull a server notification, then read it the way the bell list does.
///
/// The writer and the readers disagreed about what `notifications.type` holds.
/// The pull stores the server's **raw** type — `team`, `newTask`,
/// `replyMessage` — exactly as Kotlin's `parseNotification` does
/// (`NotificationsRepositoryImpl.kt:365-383`), and Kotlin resolves it to one of
/// the seven display types on the way out, in
/// `NotificationsViewModel.formatNotification` (`:359`), which is what
/// `handleNotificationClick` (`NotificationsFragment.kt:115-127`) and
/// `buildNotificationGroups` (`:265-267`) then switch on.
///
/// The port ported `resolveType` and never called it, so all three readers
/// switched on the raw value: a join request landed in "Other" under a generic
/// bell and did nothing when tapped. Each half had a passing test — the parser's
/// unit tests and the destination resolver's — and only the pair was wrong.
class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late NotificationsRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = NotificationsRepository(
      database.notificationDao,
      teamNotificationDao: database.teamNotificationDao,
      newsDao: database.newsDao,
      teamTaskDao: database.teamTaskDao,
      api: MockPlanetApi(),
    );
  });
  tearDown(() => database.close());

  Future<NotificationRow> pull(Map<String, dynamic> doc) async {
    await repository.bulkInsertFromSync([doc]);
    return (await database.notificationDao.getById(doc['_id'] as String))!;
  }

  test('a join request is routed to the team it names', () async {
    final row = await pull({
      '_id': 'n-join',
      'user': 'org.couchdb.user:ada',
      'type': 'team',
      'message': 'bob has requested to join Team Blue',
      'item': 'request-1',
      'linkParams': {'activeTab': 'applicantTab'},
      'status': 'unread',
    });

    await database.teamDao.upsertAll([
      TeamsCompanion.insert(id: 'request-1', teamId: const Value('team-blue')),
    ]);

    final destination = await NotificationDestinationResolver(
      taskDao: database.teamTaskDao,
      teamDao: database.teamDao,
    ).resolve(row);

    expect(
      destination,
      const NotificationDestination(
        NotificationDestinationKind.teamMembers,
        teamId: 'team-blue',
      ),
      reason:
          'the resolver switched on the raw server type `team`, which none of '
          'its arms match, so tapping a join request did nothing',
    );
  });

  test('a join request is grouped under Join Requests, not Other', () async {
    final row = await pull({
      '_id': 'n-join',
      'user': 'org.couchdb.user:ada',
      'type': 'team',
      'message': 'bob has requested to join Team Blue',
      'linkParams': {'activeTab': 'applicantTab'},
      'status': 'unread',
    });

    final items = buildGroupedList(
      [row],
      collapsedGroups: const {},
      expandedGroups: const {},
    );
    expect(
      (items.first as NotificationHeaderItem).type,
      'join_request',
      reason: 'every server notification fell into the Other group',
    );
  });

  test('a server task notification is routed to its team tasks', () async {
    await database.teamTaskDao.upsert(
      TeamTasksCompanion.insert(id: 'task-7', teamId: 'team-red'),
    );
    final row = await pull({
      '_id': 'n-task',
      'user': 'org.couchdb.user:ada',
      'type': 'newTask',
      'message': 'Fetch water is due: 2026-09-10',
      'link': '/teams/view/task-7',
      'status': 'unread',
    });

    final destination = await NotificationDestinationResolver(
      taskDao: database.teamTaskDao,
      teamDao: database.teamDao,
    ).resolve(row);

    expect(
      destination,
      const NotificationDestination(
        NotificationDestinationKind.teamTasks,
        teamId: 'team-red',
      ),
      reason: '`newTask` is the server type; the readers only know `task`',
    );
  });

  test('a voice reply is recognized from its message', () async {
    final row = await pull({
      '_id': 'n-reply',
      'user': 'org.couchdb.user:ada',
      'type': 'replyMessage',
      'message': 'bob replied to your voice',
      'replyTo': 'news-3',
      'status': 'unread',
    });

    final destination = await NotificationDestinationResolver(
      taskDao: database.teamTaskDao,
      teamDao: database.teamDao,
    ).resolve(row);

    expect(
      destination,
      const NotificationDestination(
        NotificationDestinationKind.voiceReply,
        voiceId: 'news-3',
      ),
      reason:
          'Kotlin resolveType falls through to message sniffing for any type '
          'outside KNOWN_TYPES; the port returned the raw type instead',
    );
  });

  test('the row keeps the raw server type, as Kotlin stores it', () async {
    final row = await pull({
      '_id': 'n-join',
      'user': 'org.couchdb.user:ada',
      'type': 'team',
      'message': 'bob has requested to join Team Blue',
      'linkParams': {'activeTab': 'applicantTab'},
      'status': 'unread',
    });
    expect(row.type, 'team');
    expect(row.subType, 'join_request');
  });

  test('a locally authored row is unaffected', () async {
    await repository.updateResourceNotification('org.couchdb.user:ada', 4);
    final row = await database.notificationDao.getById(
      'org.couchdb.user:ada:resource:count',
    );
    expect(resolvedNotificationType(row!), 'resource');
  });
}
