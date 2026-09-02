import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_providers.dart';
import 'package:myplanet/providers/health_provider.dart';
import 'package:myplanet/providers/life_provider.dart';
import 'package:myplanet/providers/network_status_provider.dart';
import 'package:myplanet/providers/notifications_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/sync_state.dart';
import 'package:myplanet/ui/dashboard/home_screen.dart';
import 'package:myplanet/ui/dashboard/inactive_dashboard_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/widget_harness.dart';

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

Future<PlanetPrefs> _prefs() async {
  SharedPreferences.setMockInitialValues(const {});
  return PlanetPrefs(await SharedPreferences.getInstance());
}

class _TestLastSyncNotifier extends LastSyncNotifier {
  _TestLastSyncNotifier(this.value);
  final int value;
  @override
  int build() => value;
}

class _TestNetworkStatusNotifier extends NetworkStatusNotifier {
  _TestNetworkStatusNotifier(this.status);
  final NetworkStatus status;
  @override
  NetworkStatus build() => status;
}

class RecordingKeyIvSync extends HealthKeyIvSyncNotifier {
  final calls = <String?>[];
  @override
  Future<void> sync(String? role) async => calls.add(role);
}

/// `DashboardActivity.handleGuestAccess` — a logged-in, non-guest user whose
/// `rolesList` is empty and who is not an admin sees the inactive dashboard
/// instead of the bell dashboard's four-card layout.
void main() {
  Future<List<Override>> homeOverrides({UserRow? user}) async => [
    sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
    planetPrefsProvider.overrideWithValue(await _prefs()),
    healthKeyIvSyncProvider.overrideWith(() => RecordingKeyIvSync()),
    unreadNotificationCountProvider.overrideWith((ref) => Stream.value(0)),
    myLibraryStreamProvider.overrideWith(
      (ref, userId) => Stream.value(const []),
    ),
    myCoursesStreamProvider.overrideWith(
      (ref, userId) => Stream.value(const []),
    ),
    myTeamsStreamProvider.overrideWith((ref, userId) => Stream.value(const [])),
    lifeItemsProvider.overrideWith((ref) => Stream.value(const [])),
    pendingSurveysProvider.overrideWith((ref, userId) async => const []),
    lastSyncProvider.overrideWith(() => _TestLastSyncNotifier(0)),
    completedCoursesProvider.overrideWith((ref, userId) async => const []),
    teamNotificationsProvider.overrideWith((ref, userId) async => const {}),
    offlineLoginCountProvider.overrideWith((ref, userName) async => 0),
    networkStatusProvider.overrideWith(
      () => _TestNetworkStatusNotifier(NetworkStatus.connected),
    ),
  ];

  testWidgets('inactive user (no roles, not admin) sees the inactive dashboard', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-inactive',
      name: 'bob',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      isArchived: false,
      isUpdated: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: user),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(InactiveDashboardScreen), findsOneWidget);
    expect(find.text('myLibrary'), findsNothing);
    expect(
      find.text(
        'User not activated, please contact administrator or manager to activate your account.',
      ),
      findsOneWidget,
    );
    expect(find.text('Submit Feedback'), findsOneWidget);
  });

  testWidgets('user with roles sees the full dashboard, not inactive', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-active',
      name: 'ada',
      rolesList: const ['learner'],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      lastName: 'Lovelace',
      isArchived: false,
      isUpdated: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: user),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(InactiveDashboardScreen), findsNothing);
    expect(find.text('myLibrary'), findsOneWidget);
  });

  testWidgets('admin with no roles sees the full dashboard, not inactive', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-admin',
      name: 'admin',
      rolesList: const [],
      userAdmin: true,
      joinDate: 0,
      firstName: 'Admin',
      lastName: 'User',
      isArchived: false,
      isUpdated: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: user),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(InactiveDashboardScreen), findsNothing);
    expect(find.text('myLibrary'), findsOneWidget);
  });

  testWidgets('guest user sees the full dashboard, not inactive', (
    tester,
  ) async {
    // `guest_123`, not `guest-123`. The hyphen spelled the prefix a fourth
    // way and the pre-Phase-112 five-character `startsWith('guest')` could
    // not tell it from `UserMapper.guestIdPrefix`, so this fixture was a row
    // `createGuestUser` can never write.
    //
    // `rolesList` stays empty on purpose, and that too is not a real guest
    // row: `buildGuestUserJson` sends `roles: ["guest"]`, which would keep
    // `rolesList.isEmpty` false and the inactive branch shut on its own. The
    // empty list is what makes this test about the `!isGuest` clause rather
    // than about the roles.
    final user = UserRow(
      id: 'guest_123',
      couchId: 'guest_123',
      name: 'guest',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      isArchived: false,
      isUpdated: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: user),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(InactiveDashboardScreen), findsNothing);
    expect(find.text('myLibrary'), findsOneWidget);
  });

  testWidgets('Submit Feedback button navigates to feedback create', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-inactive',
      name: 'bob',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      isArchived: false,
      isUpdated: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: user),
        pushTargets: {
          '/life/feedback/create': (context) =>
              const Scaffold(body: Center(child: Text('Feedback Create Page'))),
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Submit Feedback'));
    await tester.pumpAndSettle();

    expect(find.text('Feedback Create Page'), findsOneWidget);
  });
}
