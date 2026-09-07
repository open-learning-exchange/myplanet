import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/ui/notifications/notification_destination.dart';

NotificationRow _notification(String type, {String? relatedId}) =>
    NotificationRow(
      id: '$type-notification',
      userId: 'user-1',
      message: type,
      type: type,
      relatedId: relatedId,
      isRead: false,
      createdAt: 0,
      priority: 0,
      isFromServer: false,
      needsSync: false,
    );

void main() {
  late AppDatabase database;
  late NotificationDestinationResolver resolver;

  setUp(() {
    database = AppDatabase.memory();
    resolver = NotificationDestinationResolver(
      taskDao: database.teamTaskDao,
      teamDao: database.teamDao,
    );
  });
  tearDown(() => database.close());

  test('resource notifications open the resource catalog', () async {
    expect(
      await resolver.resolve(_notification('resource')),
      const NotificationDestination(NotificationDestinationKind.resources),
    );
  });

  test('storage notifications open storage management', () async {
    expect(
      await resolver.resolve(_notification('storage')),
      const NotificationDestination(NotificationDestinationKind.storage),
    );
  });

  test('task notifications resolve the cached task team', () async {
    await database.teamTaskDao.upsert(
      TeamTasksCompanion.insert(id: 'task-1', teamId: 'team-1'),
    );

    expect(
      await resolver.resolve(_notification('task', relatedId: 'task-1')),
      const NotificationDestination(
        NotificationDestinationKind.teamTasks,
        teamId: 'team-1',
      ),
    );
  });

  test('join requests resolve their team membership screen', () async {
    await database.teamDao.upsert(
      TeamsCompanion.insert(
        id: 'request-1',
        teamId: const Value('team-2'),
        type: const Value('request'),
      ),
    );

    expect(
      await resolver.resolve(
        _notification('join_request', relatedId: 'request-1'),
      ),
      const NotificationDestination(
        NotificationDestinationKind.teamMembers,
        teamId: 'team-2',
      ),
    );
  });

  // Kotlin's `resolveAndOpenTeam` is `resolve(relatedId) ?: relatedId`
  // (`NotificationsFragment.kt:142-148`) — an uncached task or join request
  // still opens the team, using the id the notification carried. The port used
  // to return null here, so the tap did nothing; and a server task's row is
  // never cached, because there is no `tasks` sync walk yet.
  test('an uncached related row falls back to the id it carried', () async {
    expect(
      await resolver.resolve(_notification('task', relatedId: 'missing')),
      const NotificationDestination(
        NotificationDestinationKind.teamTasks,
        teamId: 'missing',
      ),
    );
    expect(
      await resolver.resolve(
        _notification('join_request', relatedId: 'missing'),
      ),
      const NotificationDestination(
        NotificationDestinationKind.teamMembers,
        teamId: 'missing',
      ),
    );
  });

  // `getJoinRequestTeamId` strips the prefix the system-tray path adds
  // (`NotificationsRepositoryImpl.kt:169-177`) before the lookup, and the
  // stripped id is also what the fallback opens.
  test('a join_request_ prefixed id is stripped before the lookup', () async {
    await database.teamDao.upsertAll([
      TeamsCompanion.insert(id: 'req-1', teamId: const Value('team-9')),
    ]);
    expect(
      await resolver.resolve(
        _notification('join_request', relatedId: 'join_request_req-1'),
      ),
      const NotificationDestination(
        NotificationDestinationKind.teamMembers,
        teamId: 'team-9',
      ),
    );
    // The strip happens inside `getJoinRequestTeamId`, so
    // `resolveAndOpenTeam`'s `?: relatedId` falls back to the id the
    // notification carried — prefix and all, not the stripped form.
    expect(
      await resolver.resolve(
        _notification('join_request', relatedId: 'join_request_absent'),
      ),
      const NotificationDestination(
        NotificationDestinationKind.teamMembers,
        teamId: 'join_request_absent',
      ),
    );
  });

  test(
    'blank ids and unknown types stay on the notifications screen',
    () async {
      expect(
        await resolver.resolve(_notification('task', relatedId: '  ')),
        isNull,
      );
      expect(await resolver.resolve(_notification('other')), isNull);
    },
  );

  test('team_join carries the team id directly', () async {
    expect(
      await resolver.resolve(_notification('team_join', relatedId: 'team-3')),
      const NotificationDestination(
        NotificationDestinationKind.teamJoin,
        teamId: 'team-3',
      ),
    );
  });

  test('chat carries the team id directly', () async {
    expect(
      await resolver.resolve(_notification('chat', relatedId: 'team-4')),
      const NotificationDestination(
        NotificationDestinationKind.teamChat,
        teamId: 'team-4',
      ),
    );
  });

  test('voice_reply carries the news id directly', () async {
    expect(
      await resolver.resolve(_notification('voice_reply', relatedId: 'news-5')),
      const NotificationDestination(
        NotificationDestinationKind.voiceReply,
        voiceId: 'news-5',
      ),
    );
  });

  test('team_join and voice_reply with blank ids resolve to nothing', () async {
    expect(
      await resolver.resolve(_notification('team_join', relatedId: '  ')),
      isNull,
    );
    expect(await resolver.resolve(_notification('voice_reply')), isNull);
  });

  test('a bell row and a tray tap on the same notification agree', () async {
    // The claim Phase 130's whole design rests on: one mapping, two callers.
    // A tray tap has no row, so it goes through `resolveFor(type:, relatedId:)`
    // while the bell row goes through `resolve(row)` — and if those two ever
    // diverge, the same notification opens two different screens depending on
    // where the user tapped it. That is the shape `CLAUDE.md` records four
    // times over ("a writer and a reader disagreeing about a key, where each
    // half passes its own test and only the pair is wrong"), so the pair is
    // what this tests.
    //
    // It is also the guard against re-inlining: if someone puts a second
    // switch back into `notifications_screen.dart` and lets it drift, nothing
    // else here would notice.
    await database.teamTaskDao.upsertAll([
      TeamTasksCompanion.insert(
        id: 'task-42',
        teamId: 'team-9',
        title: const Value('Read chapter 3'),
      ),
    ]);
    await database.teamDao.upsertAll([
      TeamsCompanion.insert(id: 'request-7', teamId: const Value('team-3')),
    ]);

    // Every type the resolver answers for, with the `relatedId` shape each one
    // carries in production.
    const cases = {
      'resource': null,
      'storage': null,
      'task': 'task-42',
      'join_request': 'request-7',
      'team_join': 'team-9',
      'chat': 'team-9',
      'voice_reply': 'news-5',
    };

    for (final entry in cases.entries) {
      final fromRow = await resolver.resolve(
        _notification(entry.key, relatedId: entry.value),
      );
      final fromTray = await resolver.resolveFor(
        type: entry.key,
        relatedId: entry.value,
      );
      expect(
        fromTray,
        fromRow,
        reason: 'the two entry points disagree about "${entry.key}"',
      );
      expect(
        fromRow,
        isNotNull,
        reason: 'this case proves nothing if both resolve to null',
      );
      expect(
        notificationDestinationLocation(fromTray!),
        notificationDestinationLocation(fromRow!),
      );
    }
  });
}
