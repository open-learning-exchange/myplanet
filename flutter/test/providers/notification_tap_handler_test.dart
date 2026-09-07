import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/notifications/notification_config.dart';
import 'package:myplanet/core/notifications/notification_tap.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/notification_tap_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/notifications_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// `NotificationTapHandler` — the port of `NotificationActionReceiver` plus
/// `DashboardActivity.handleNotificationIntent`.
///
/// Before Phase 130 the port raised system notifications and tapping one did
/// nothing at all: no payload on the notification, no response callback, no
/// handler. `NotificationsRepository.markNotificationAsRead` — the correct port
/// of Kotlin's body-tap handler — had no caller anywhere in `lib/`.
///
/// The three gestures are three different behaviours in the Kotlin and the
/// tests are grouped that way. The distinction that matters most is which of
/// the two read-marking statements runs: the bulk one stamps `createdAt`, which
/// is the notification list's sort key *and* is uploaded back into the server
/// document's `time`, and the single one does not.
void main() {
  late AppDatabase database;
  late PlanetPrefs prefs;

  /// A fixed clock, so a stamped `createdAt` is distinguishable from the
  /// row's own age by value rather than by "roughly now".
  const stampedAt = 1_700_000_000_000;
  const rowCreatedAt = 500;

  UserRow user() => UserRow(
    id: 'user-1',
    name: 'ada',
    rolesList: const [],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  NotificationsRepository repositoryFor() => NotificationsRepository(
    database.notificationDao,
    teamNotificationDao: database.teamNotificationDao,
    newsDao: database.newsDao,
    teamTaskDao: database.teamTaskDao,
    teamDao: database.teamDao,
    userDao: database.userDao,
    now: () => DateTime.fromMillisecondsSinceEpoch(stampedAt),
  );

  ProviderContainer containerFor({
    UserRow? current,
    bool sessionFails = false,
  }) {
    final container = ProviderContainer(
      overrides: [
        planetPrefsProvider.overrideWithValue(prefs),
        appDatabaseProvider.overrideWithValue(database),
        notificationsRepositoryProvider.overrideWithValue(repositoryFor()),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(current, fails: sessionFails),
        ),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  /// A synced `newTask` notification row. `parseNotification` stores the
  /// server's raw type verbatim, so the stored type is `newTask` and
  /// `resolveNotificationType` maps it to `task` at read time.
  Future<void> insertNotification({
    required String id,
    String type = 'newTask',
    String? relatedId = 'task-42',
    bool isRead = false,
  }) => database.notificationDao.upsert(
    NotificationsCompanion.insert(
      id: id,
      userId: 'user-1',
      type: Value(type),
      relatedId: Value(relatedId),
      message: const Value('Read chapter 3 Wed 19, August 2026'),
      isRead: Value(isRead),
      isFromServer: const Value(true),
      // A synced row carries the document's `rev`, and
      // `getPendingSyncNotifications` filters on it: a row with no rev was
      // authored locally and cannot be PUT back.
      rev: const Value('1-abc'),
      createdAt: rowCreatedAt,
    ),
  );

  /// The task the deadline reminder was raised for, with its team id where the
  /// mapper puts it — out of `link.teams`.
  Future<void> insertTask() => database.teamTaskDao.upsertAll([
    TeamTasksCompanion.insert(
      id: 'task-42',
      title: const Value('Read chapter 3'),
      teamId: 'team-9',
      assignee: const Value('user-1'),
      deadline: const Value(0),
    ),
  ]);

  /// The tap the port's own producer delivers. `TaskDeadlineNotifier` builds
  /// this exact config, so the payload is not invented here.
  NotificationTap tapFor({String? actionId, String taskId = 'task-42'}) =>
      NotificationTap(
        actionId: actionId,
        payload: NotificationTapPayload.forConfig(
          NotificationConfig.task(
            taskId: taskId,
            taskTitle: 'Read chapter 3',
            deadlineLabel: 'Wed 19, August 2026',
            urgent: true,
          ),
        ),
      );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
    database = AppDatabase.memory();
  });
  tearDown(() async {
    // One test closes it mid-run to drive the resolver's failure path.
    try {
      await database.close();
    } catch (_) {}
  });

  group('the notification body', () {
    test('reaches the tasks page of the task’s team', () async {
      await insertTask();
      final container = containerFor(current: user());

      final location = await container
          .read(notificationTapHandlerProvider)
          .handle(tapFor());

      // `handleTaskNavigation` → `TeamDetailFragment(navigateToPage = TasksPage)`,
      // which is where the in-app row tap lands too.
      expect(location, '/life/teams/team-9/tasks');
    });

    test('marks read without stamping createdAt', () async {
      // The distinction Phase 127 separated and left with no caller. This is
      // the caller: `markDatabaseNotificationAsRead` goes through
      // `NotificationDao.markOneAsRead`, whose SQL has no `createdAt` column
      // in it at all.
      await insertTask();
      await insertNotification(id: 'task-42');
      final container = containerFor(current: user());

      await container.read(notificationTapHandlerProvider).handle(tapFor());

      final row = await database.notificationDao.getById('task-42');
      expect(row!.isRead, isTrue);
      expect(
        row.createdAt,
        rowCreatedAt,
        reason:
            'the body tap must not restamp — the list sorts on this and '
            'syncNotificationReads uploads it as the document’s `time`',
      );
    });

    test('a summary id marks the whole type read', () async {
      // The `summary_` branch is unique to this path — the bulk statement the
      // action buttons use has no such prefix handling. Nothing in either app
      // mints a `summary_` id today (`createSummaryNotification` has no live
      // caller), so this pins the branch rather than a live behaviour.
      await insertNotification(id: 'n-1', type: 'storage');
      await insertNotification(id: 'n-2', type: 'storage');
      await insertNotification(id: 'n-3', type: 'resource');
      final container = containerFor(current: user());

      await container
          .read(notificationTapHandlerProvider)
          .handle(
            const NotificationTap(
              payload: NotificationTapPayload(
                type: 'storage',
                notificationId: 'summary_storage',
              ),
            ),
          );

      expect((await database.notificationDao.getById('n-1'))!.isRead, isTrue);
      expect((await database.notificationDao.getById('n-2'))!.isRead, isTrue);
      expect(
        (await database.notificationDao.getById('n-3'))!.isRead,
        isFalse,
        reason: 'markSummaryAsRead is scoped to one type',
      );
    });

    test('resolves the session rather than reading it unwatched', () async {
      // The rule this project turned into a rule after four independent
      // instances: nothing here watches `sessionProvider`, so
      // `ref.read(sessionProvider).valueOrNull` is null while it loads. A null
      // user routes a `summary_` id to `markSummaryAsRead(null, type)`, which
      // matches no row — so the read-marking silently does nothing.
      await insertNotification(id: 'n-1', type: 'storage');
      final container = containerFor(current: user());
      // Deliberately *not* resolving the session first.

      await container
          .read(notificationTapHandlerProvider)
          .handle(
            const NotificationTap(
              payload: NotificationTapPayload(
                type: 'storage',
                notificationId: 'summary_storage',
              ),
            ),
          );

      expect((await database.notificationDao.getById('n-1'))!.isRead, isTrue);
    });

    test('a rejecting session still navigates', () async {
      // The correction Phase 100's fix needed on harvest: the `await` belongs
      // *inside* the `try`, because a future can reject where `valueOrNull`
      // could not. Kotlin's read-mark is wrapped in its own try/catch and the
      // navigation follows regardless.
      await insertTask();
      final container = containerFor(sessionFails: true);

      expect(
        await container.read(notificationTapHandlerProvider).handle(tapFor()),
        '/life/teams/team-9/tasks',
      );
    });
  });

  group('Mark as Read', () {
    test('marks read, stamps, and navigates nowhere', () async {
      await insertTask();
      await insertNotification(id: 'task-42');
      final container = containerFor(current: user());

      final location = await container
          .read(notificationTapHandlerProvider)
          .handle(tapFor(actionId: NotificationTapActions.markAsRead));

      expect(
        location,
        isNull,
        reason:
            'ACTION_MARK_AS_READ has no navigation arm; it only brings the '
            'app to the foreground, which showsUserInterface already does',
      );
      final row = await database.notificationDao.getById('task-42');
      expect(row!.isRead, isTrue);
      expect(
        row.createdAt,
        stampedAt,
        reason:
            'NotificationActionReceiver:81 calls the bulk '
            'markNotificationsAsRead, which passes its own Date()',
      );
    });

    test('flags a server row for read-state upload', () async {
      // The other half of what the stamping statement does, and the reason the
      // stamp reaches another device at all.
      await insertNotification(id: 'task-42');
      final container = containerFor(current: user());

      await container
          .read(notificationTapHandlerProvider)
          .handle(tapFor(actionId: NotificationTapActions.markAsRead));

      final pending = await database.notificationDao
          .getPendingSyncNotifications();
      expect(pending.map((row) => row.id), ['task-42']);
    });
  });

  group('View Task', () {
    test('marks read, stamps, and navigates', () async {
      await insertTask();
      await insertNotification(id: 'task-42');
      final container = containerFor(current: user());

      final location = await container
          .read(notificationTapHandlerProvider)
          .handle(tapFor(actionId: NotificationTapActions.open));

      expect(location, '/life/teams/team-9/tasks');
      final row = await database.notificationDao.getById('task-42');
      expect(row!.createdAt, stampedAt);
    });

    test('an uncached task still opens the team the id names', () async {
      // `resolveAndOpenTeam`'s `resolve(relatedId) ?: relatedId`. Kotlin's
      // *button* path is stricter than this — `getTaskTeamInfo` returns null
      // when the team row is absent and navigates nowhere — and the port
      // deliberately shares the row tap's forgiving resolver so a tray tap and
      // a row tap land in the same place. Recorded in PHASE_130_NOTES.md.
      final container = containerFor(current: user());

      expect(
        await container
            .read(notificationTapHandlerProvider)
            .handle(tapFor(actionId: NotificationTapActions.open)),
        '/life/teams/task-42/tasks',
      );
    });
  });

  group('at parity: the tray marks nothing read on real data', () {
    test('because the tray id and the row id are different documents', () async {
      // The finding, reproduced rather than fixed. `TaskDeadlineNotifier`
      // mints the notification with `id = task.id`, as
      // `TaskNotificationWorker.kt:48-53` does. A `task` notification *row* is
      // a synced CouchDB notification document: its id is its own `_id`, and
      // the task id lands in `relatedId`. So every tray gesture looks up an id
      // that is not in the table.
      //
      // Fixing it would mean resolving `relatedId` back to the row — an
      // improvement the Android app does not have, and therefore a divergence
      // rather than a repair. The navigation half works precisely because it
      // reads `relatedId`, which is why the tap is still worth wiring.
      await insertTask();
      await insertNotification(id: 'n-77', relatedId: 'task-42');
      final container = containerFor(current: user());

      for (final actionId in const [
        null,
        NotificationTapActions.markAsRead,
        NotificationTapActions.open,
      ]) {
        await container
            .read(notificationTapHandlerProvider)
            .handle(tapFor(actionId: actionId));
      }

      final row = await database.notificationDao.getById('n-77');
      expect(
        row!.isRead,
        isFalse,
        reason: 'no tray gesture reaches the row, in either app',
      );
      expect(row.createdAt, rowCreatedAt);
    });
  });

  group('a failure in one half does not cost the other', () {
    test(
      'a database the resolver cannot read navigates nowhere, quietly',
      () async {
        // Kotlin's lookups sit inside `viewModelScope.launch` blocks whose
        // failure leaves the fragment alone. Here the listener that delivered the
        // tap is a stream subscription, and an exception out of `handle` would be
        // an unhandled async error — so the resolve has its own guard.
        await insertNotification(id: 'task-42');
        final container = containerFor(current: user());

        // Captured rather than left to print: swallowing the failure silently
        // would be the wrong outcome too, so this asserts it was reported.
        final reported = <Object>[];
        final previous = FlutterError.onError;
        FlutterError.onError = (details) => reported.add(details.exception);
        addTearDown(() => FlutterError.onError = previous);

        // Closed under the handler's feet, which is what a disposed graph or a
        // corrupt database file looks like from here.
        await database.close();

        expect(
          await container.read(notificationTapHandlerProvider).handle(tapFor()),
          isNull,
        );
        expect(reported, isNotEmpty, reason: 'the failure went unreported');
      },
    );
  });

  group('which field the destination is resolved from', () {
    test('relatedId, not the notification id', () async {
      // `handleNotificationIntent`'s `auto_navigate` branch reads
      // `related_id` (`DashboardActivity.kt:536`), and the two are *not*
      // interchangeable even though the task factory happens to set them to
      // the same string. `createStorageWarningNotification(percent, customId)`
      // sets `id = customId` and `relatedId = "storage"`; a `voice_reply`
      // notification's `relatedId` is the news id while its own id is the
      // notification document's. Feeding the resolver the notification id
      // instead is invisible on the one producer the port has, which is
      // exactly why it needs a test that separates them.
      final container = containerFor(current: user());

      expect(
        await container
            .read(notificationTapHandlerProvider)
            .handle(
              const NotificationTap(
                payload: NotificationTapPayload(
                  type: 'voice_reply',
                  notificationId: 'n-1',
                  relatedId: 'voice-7',
                ),
              ),
            ),
        '/life/voices/voice-7',
      );
    });
  });

  group('types with no destination', () {
    test('an unknown type marks read and navigates nowhere', () async {
      // `handleNotificationIntent`'s `else ->` opens the notifications list;
      // the port stays where it is instead, for the reason `deepLinkRoute`
      // gives for the same fall-through — yanking the user off the screen they
      // are on is worse than doing nothing. The resolver returning null is the
      // single place that decision lives.
      await insertNotification(id: 'n-9', type: 'newsAdded');
      final container = containerFor(current: user());

      expect(
        await container
            .read(notificationTapHandlerProvider)
            .handle(
              const NotificationTap(
                payload: NotificationTapPayload(
                  type: 'course',
                  notificationId: 'n-9',
                ),
              ),
            ),
        isNull,
      );
      expect((await database.notificationDao.getById('n-9'))!.isRead, isTrue);
    });
  });
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user, {this.fails = false});

  final UserRow? user;
  final bool fails;

  @override
  Future<UserRow?> build() async {
    if (fails) throw StateError('no session');
    return user;
  }
}
