import 'package:drift/drift.dart' show Value;
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_providers.dart';
import 'package:myplanet/providers/health_provider.dart';
import 'package:myplanet/providers/life_provider.dart';
import 'package:myplanet/providers/network_status_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/sync_state.dart';
import 'package:myplanet/ui/dashboard/home_screen.dart';
import 'package:myplanet/ui/notifications/notification_format.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/widget_harness.dart';

/// Reachability guard for the resource-update notification row (Phase 131).
///
/// `NotificationsRepository.updateResourceNotification` was ported with six
/// tests and no production caller — the "ported, tested, green and dead" class.
/// Every one of those tests calls the writer directly, so none of them could
/// see that nothing in the app ever does.
///
/// The three questions this file asks, per `CLAUDE.md`:
/// **who writes this table** (the dashboard, on load), **can the writer produce
/// values the reader's predicate matches** (`notification_format.dart`'s
/// `resource` arm needs a bare integer, which is what the writer stores), and
/// **does anything navigate here** (the bell, whose unread badge the row feeds).
class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

class _TestLastSyncNotifier extends LastSyncNotifier {
  @override
  int build() => 0;
}

class _TestNetworkStatusNotifier extends NetworkStatusNotifier {
  @override
  NetworkStatus build() => NetworkStatus.connected;
}

class _NoopKeyIvSync extends HealthKeyIvSyncNotifier {
  @override
  Future<void> sync(String? role) async {}
}

UserRow _user(String id) => UserRow(
  id: id,
  name: 'ada',
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  firstName: 'Ada',
  lastName: 'Lovelace',
  isArchived: false,
  isUpdated: false,
);

void main() {
  late AppDatabase database;

  setUp(() {
    SharedPreferences.setMockInitialValues(const {});
    database = AppDatabase.memory();
  });

  tearDown(() => database.close());

  /// A `my_library` row shaped the way a synced server document lands, not the
  /// way a fixture would find convenient: `userId` is the JSON list
  /// `MyLibraryDao` matches with `LIKE '%"<id>"%'`, and the download state is
  /// spelled with the same three columns the Kotlin predicate reads.
  MyLibraryTableCompanion resource(
    String id, {
    required List<String> userId,
    bool isPrivate = false,
    bool offline = false,
    String? localAddress,
    String? rev,
    String? downloadedRev,
  }) => MyLibraryTableCompanion.insert(
    id: id,
    userId: Value(userId),
    title: Value('Resource $id'),
    titleNormal: Value('resource $id'),
    isPrivate: Value(isPrivate),
    resourceOffline: Value(offline),
    resourceLocalAddress: Value(localAddress),
    rev: Value(rev),
    downloadedRev: Value(downloadedRev),
  );

  Future<List<Override>> overrides(UserRow user) async => [
    appDatabaseProvider.overrideWithValue(database),
    sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
    planetPrefsProvider.overrideWithValue(
      PlanetPrefs(await SharedPreferences.getInstance()),
    ),
    healthKeyIvSyncProvider.overrideWith(_NoopKeyIvSync.new),
    myCoursesStreamProvider.overrideWith(
      (ref, userId) => Stream.value(const []),
    ),
    myTeamsStreamProvider.overrideWith((ref, userId) => Stream.value(const [])),
    lifeItemsProvider.overrideWith((ref) => Stream.value(const [])),
    pendingSurveysProvider.overrideWith((ref, userId) async => const []),
    lastSyncProvider.overrideWith(_TestLastSyncNotifier.new),
    completedCoursesProvider.overrideWith((ref, userId) async => const []),
    teamNotificationsProvider.overrideWith((ref, userId) async => const {}),
    offlineLoginCountProvider.overrideWith((ref, userName) async => 0),
    networkStatusProvider.overrideWith(_TestNetworkStatusNotifier.new),
  ];

  /// Mounts the dashboard against the real database and then tears the tree
  /// down again *inside* the test body.
  ///
  /// The teardown is not tidiness. This file is the one place in the suite that
  /// runs `HomeScreen` with live drift query streams rather than overridden
  /// ones (`team_voices_screen_test.dart:40-43` explains why the others do
  /// not): cancelling a drift stream schedules a zero-duration timer through
  /// `StreamQueryStore.markAsClosed`, and if the `ProviderScope` is disposed by
  /// the harness backstop after the body returns, that timer is still pending
  /// and every test here fails with `'!timersPending'` — a failure that says
  /// nothing about the behaviour under test. Unmounting and settling first lets
  /// the cancel run while there is still a fake clock to run it on.
  ///
  /// The assertions come after, reading the database directly, which is what
  /// makes that ordering harmless: the row outlives the widget.
  Future<void> mountDashboard(WidgetTester tester, UserRow user) async {
    await tester.pumpWidget(
      wrapScreen(const HomeScreen(), overrides: await overrides(user)),
    );
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpAndSettle();
  }

  testWidgets('loading the dashboard writes the resource-update notification', (
    tester,
  ) async {
    const userId = 'org.couchdb.user:ada';
    await database.myLibraryDao.upsertAll([
      // On the shelf, public, never downloaded — counts.
      resource('a', userId: const [userId]),
      // On the shelf, public, downloaded but the server moved on — counts.
      resource(
        'b',
        userId: const [userId],
        offline: true,
        localAddress: '/tmp/b',
        rev: '2-def',
        downloadedRev: '1-abc',
      ),
      // On the shelf, downloaded and current — does not count.
      resource(
        'c',
        userId: const [userId],
        offline: true,
        localAddress: '/tmp/c',
        rev: '1-abc',
        downloadedRev: '1-abc',
      ),
      // Not on this user's shelf — does not count.
      resource('d', userId: const ['org.couchdb.user:bob']),
      // Private — does not count, whatever its download state.
      resource('e', userId: const [userId], isPrivate: true),
      // Flagged offline but with no local address: the second disjunct
      // requires `resource_local_address IS NOT NULL`, so this is excluded
      // however the revisions compare. Easy to miss — it is an `AND` inside
      // the `OR`, not a third top-level case.
      resource('f', userId: const [userId], offline: true, rev: '9-zzz'),
    ]);

    await mountDashboard(tester, _user(userId));

    final row = await database.notificationDao.getById(
      '$userId:resource:count',
    );
    expect(
      row,
      isNotNull,
      reason: 'the dashboard must author the resource-update notification',
    );
    expect(row!.message, '2');
    expect(row.type, 'resource');
    expect(row.isRead, isFalse);
  });

  testWidgets('the row the dashboard writes renders through the resource arm', (
    tester,
  ) async {
    const userId = 'org.couchdb.user:ada';
    await database.myLibraryDao.upsertAll([
      resource('a', userId: const [userId]),
      resource('b', userId: const [userId]),
      resource('c', userId: const [userId]),
    ]);

    await mountDashboard(tester, _user(userId));

    final row = await database.notificationDao.getById(
      '$userId:resource:count',
    );
    expect(row, isNotNull);

    // `notification_format.dart`'s `resource` arm reads `kotlinToIntOrNull`,
    // which only succeeds on a bare integer. This asserts the live writer
    // produces a value that predicate matches — the second reachability
    // question, and the one a fixture-built row cannot answer.
    final l10n = await AppLocalizations.delegate.load(const Locale('en'));
    expect(
      formatNotification(row!, l10n: l10n).text,
      'You have 3 resources not downloaded',
    );
  });

  testWidgets('an inactive user gets no resource notification', (tester) async {
    // `DashboardActivity.kt:165-182` calls `handleGuestAccess()` and
    // `return@launch`es **before** `initializeDashboard()`, so for a user with
    // no roles and no admin flag none of the three triggers ever runs — no data
    // observer, no notification check, no badge. The port's equivalent gate is
    // `HomeScreen.build`'s early return to `InactiveDashboardScreen`, and the
    // watch has to sit below it: placed above, the port would author a row the
    // Android app never writes, on the one screen that cannot show it.
    //
    // Despite its name `handleGuestAccess` gates on the *inactive* condition,
    // not on guest — a guest carries `roles: ["guest"]`, so `rolesList` is
    // non-empty and a guest falls through to the full dashboard and does get
    // the row. The test below pins that half too.
    const userId = 'org.couchdb.user:inert';
    await database.myLibraryDao.upsertAll([
      resource('a', userId: const [userId]),
      resource('b', userId: const [userId]),
    ]);

    final inactive = UserRow(
      id: userId,
      name: 'inert',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      isArchived: false,
      isUpdated: false,
    );

    await mountDashboard(tester, inactive);

    expect(
      await database.notificationDao.getById('$userId:resource:count'),
      isNull,
      reason: 'the inactive dashboard runs none of the Kotlin triggers',
    );
  });

  testWidgets('a guest does get one — `roles: ["guest"]` is not empty', (
    tester,
  ) async {
    const userId = 'guest_ada';
    await database.myLibraryDao.upsertAll([
      resource('a', userId: const [userId]),
    ]);

    final guest = UserRow(
      id: userId,
      name: 'guest_ada',
      rolesList: const ['guest'],
      userAdmin: false,
      joinDate: 0,
      isArchived: false,
      isUpdated: false,
    );

    await mountDashboard(tester, guest);

    final row = await database.notificationDao.getById(
      '$userId:resource:count',
    );
    expect(row, isNotNull, reason: 'Kotlin has no guest gate on this path');
    expect(row!.message, '1');
  });

  test('the writer does not outlive the dashboard', () async {
    // `setupDashboardDataObserver` (`DashboardActivity.kt:591-595`) collects
    // through `collectWhenStarted`, i.e. `repeatOnLifecycle(STARTED)`
    // (`FlowExtensions.kt:38-43`), bound to the activity. When the dashboard
    // finishes — logout, or any route that replaces it — the collection stops
    // and nothing rewrites the row until the user opens the dashboard again.
    //
    // A plain `StreamProvider.family` does not do that: its element lives for
    // the container's lifetime, so the stream stays subscribed after the last
    // listener goes and keeps writing. `SessionNotifier.signOut` invalidates
    // nothing, so the concrete failure is: Alice reads her notification and
    // logs out, Bob signs in and syncs, the sync writes `my_library`, and
    // *Alice's* row is rewritten unread with a `createdAt` from inside Bob's
    // session — which is the sort key for `watchForUser`
    // (`ORDER BY isRead ASC, createdAt DESC`) and the relative-time label.
    //
    // Hence `.autoDispose`. Note the neighbouring families in this file are
    // deliberately *not* auto-disposing — they are pure readers, where a stale
    // live stream costs nothing. This is the only one that writes.
    const userId = 'org.couchdb.user:alice';
    final container = ProviderContainer(
      overrides: [appDatabaseProvider.overrideWithValue(database)],
    );
    addTearDown(container.dispose);

    await database.myLibraryDao.upsertAll([
      resource('a', userId: const [userId]),
    ]);

    final subscription = container.listen(
      resourceUpdateNotificationProvider(userId),
      (_, _) {},
      fireImmediately: true,
    );
    await pumpEventQueue();
    expect(
      await database.notificationDao.getById('$userId:resource:count'),
      isNotNull,
      reason: 'the dashboard writes while it is listening',
    );

    // The dashboard goes away, and the row is cleared the way a user deleting
    // it from the bell would clear it.
    subscription.close();
    // Riverpod schedules auto-disposal rather than doing it inline, so the
    // cancellation has to be given a turn of the event loop before the row is
    // cleared — otherwise the stream is still live and simply rewrites it.
    await pumpEventQueue();
    await database.notificationDao.deleteById('$userId:resource:count');

    // Someone else's session syncs and touches the shelf.
    await database.myLibraryDao.upsertAll([
      resource('b', userId: const [userId]),
    ]);
    await pumpEventQueue();

    expect(
      await database.notificationDao.getById('$userId:resource:count'),
      isNull,
      reason: 'nothing should write this row once the dashboard is gone',
    );
  });
}
