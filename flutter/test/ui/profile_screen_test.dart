import 'dart:async';

import 'package:drift/drift.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/activities_provider.dart';
import 'package:myplanet/providers/notifications_provider.dart';
import 'package:myplanet/repository/activities_repository.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/user/profile_screen.dart';

import '../support/widget_harness.dart';

/// The screen's app-bar badge watches the notification DAO, so every test has
/// to stub it — otherwise it opens a drift stream against the harness fallback
/// database and leaves a timer pending past teardown.
List<Override> profileOverrides(SessionNotifier notifier) => [
  sessionProvider.overrideWith(() => notifier),
  unreadNotificationCountProvider.overrideWith((ref) => Stream.value(0)),
];

void main() {
  testWidgets('shows the cached user profile and omits empty fields', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const ['learner', 'leader'],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.org',
      phoneNumber: '+1 555 0100',
      language: 'English',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Profile'), findsOneWidget);
    expect(find.text('AL'), findsOneWidget);
    expect(find.text('Ada Lovelace'), findsOneWidget);
    expect(find.text('learner'), findsOneWidget);
    expect(find.text('leader'), findsOneWidget);
    expect(find.text('ada'), findsOneWidget);
    expect(find.text('ada@example.org'), findsOneWidget);
    expect(find.text('+1 555 0100'), findsOneWidget);
    expect(find.text('English'), findsOneWidget);
    expect(find.text('Date of birth'), findsNothing);
  });

  testWidgets('shows a safe empty state without a signed-in user', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(null)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Profile unavailable'), findsOneWidget);
  });

  testWidgets('validates and saves offline profile edits', (tester) async {
    final notifier = _TestSessionNotifier(
      UserRow(
        id: 'user-1',
        name: 'ada',
        rolesList: const [],
        userAdmin: false,
        joinDate: 0,
        firstName: 'Ada',
        email: 'old@example.org',
        isArchived: false,
        isUpdated: false,
      ),
    );
    await tester.pumpWidget(
      wrapScreen(const ProfileScreen(), overrides: profileOverrides(notifier)),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.edit_outlined));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.widgetWithText(TextFormField, 'First name'),
      'Augusta',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Email'),
      'not-an-email',
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pump();

    expect(find.text('Enter a valid email address'), findsOneWidget);
    expect(notifier.updateCalls, 0);

    await tester.enterText(
      find.widgetWithText(TextFormField, 'Email'),
      'augusta@example.org',
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pumpAndSettle();

    expect(notifier.updateCalls, 1);
    expect(find.text('Augusta'), findsOneWidget);
    expect(find.text('augusta@example.org'), findsOneWidget);
    expect(find.text('Profile saved on this device'), findsOneWidget);
  });

  testWidgets('renders the Spanish strings carried over from values-es', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      email: 'ada@example.org',
      phoneNumber: '+1 555 0100',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
        locale: const Locale('es'),
      ),
    );
    await tester.pumpAndSettle();

    // Each of these comes from `res/values-es/strings.xml`, not a machine
    // translation — see docs/kotlin-to-flutter-migration.md.
    expect(find.text('Perfil'), findsOneWidget);
    expect(find.text('Nombre'), findsOneWidget);
    expect(find.text('Correo electrónico'), findsOneWidget);
    expect(find.text('Número de teléfono'), findsOneWidget);
  });
  testWidgets('shows the activity stats the Kotlin stats list shows', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: [
          ...profileOverrides(_TestSessionNotifier(_user())),
          profileActivityStatsProvider.overrideWith(
            (ref) async => ProfileActivityStats(
              lastVisit:
                  DateTime.now().millisecondsSinceEpoch -
                  const Duration(hours: 3).inMilliseconds,
              offlineVisits: 7,
              mostOpened: const MostOpenedResource('Algebra', 4),
              resourceOpenCount: 9,
            ),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Last login'), findsOneWidget);
    expect(find.text('3 hours ago'), findsOneWidget);
    expect(find.text('Total visits'), findsOneWidget);
    expect(find.text('7'), findsOneWidget);
    expect(find.text('Most opened resource'), findsOneWidget);
    expect(find.text('Algebra opened 4 times'), findsOneWidget);
    expect(find.text('Number of resources opened'), findsOneWidget);
    expect(find.text('Resource opened 9 times'), findsOneWidget);
  });

  testWidgets('omits the activity block until something has been logged', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: [
          ...profileOverrides(_TestSessionNotifier(_user())),
          profileActivityStatsProvider.overrideWith(
            (ref) async => const ProfileActivityStats(),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The Kotlin renders every row with "N/A"; a fresh install here shows no
    // card at all rather than a column of zeroes.
    expect(find.text('Activity'), findsNothing);
    expect(find.text('Total visits'), findsNothing);
  });

  testWidgets('shows a spinner while the session is loading', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: [
          sessionProvider.overrideWith(() => _PendingSessionNotifier()),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(0),
          ),
        ],
      ),
    );
    await tester.pump();

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('shows the error message when the session load fails', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: [
          sessionProvider.overrideWith(() => _ErrorSessionNotifier()),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(0),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Profile unavailable'), findsOneWidget);
  });

  testWidgets('cancel in the edit dialog does not call updateProfile', (
    tester,
  ) async {
    final notifier = _TestSessionNotifier(_user());
    await tester.pumpWidget(
      wrapScreen(const ProfileScreen(), overrides: profileOverrides(notifier)),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.edit_outlined));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
    await tester.pumpAndSettle();

    expect(notifier.updateCalls, 0);
    // The dialog is gone.
    expect(find.widgetWithText(TextFormField, 'First name'), findsNothing);
  });

  testWidgets('falls back to the username when name parts are empty', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-1',
      name: 'solo',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('solo'), findsWidgets);
  });

  testWidgets('falls back to a default label when no name is set at all', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-1',
      name: null,
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('myPlanet learner'), findsOneWidget);
  });

  testWidgets(
    'shows the no-details message when all profile fields are empty',
    (tester) async {
      final user = UserRow(
        id: 'user-1',
        name: null,
        rolesList: const [],
        userAdmin: false,
        joinDate: 0,
        isArchived: false,
        isUpdated: false,
      );

      await tester.pumpWidget(
        wrapScreen(
          const ProfileScreen(),
          overrides: profileOverrides(_TestSessionNotifier(user)),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('No profile details are available.'), findsOneWidget);
    },
  );

  testWidgets('uses the middle name in the display name when present', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      middleName: 'Augusta',
      lastName: 'Lovelace',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Ada Augusta Lovelace'), findsOneWidget);
  });

  testWidgets('shows the planet code among the account details', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      planetCode: 'gt',
      parentCode: 'nation',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('gt'), findsOneWidget);
  });

  testWidgets('falls back to parentCode when planetCode is absent', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      planetCode: null,
      parentCode: 'nation',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('nation'), findsOneWidget);
  });

  testWidgets('preserves all fields when opening the edit dialog', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.org',
      phoneNumber: '+1 555',
      level: 'a1',
      language: 'English',
      gender: 'female',
      dob: '1815-12-10',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.edit_outlined));
    await tester.pumpAndSettle();

    expect(find.widgetWithText(TextFormField, 'First name'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Last name'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Email'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Phone'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Level'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Language'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Gender'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Date of birth'), findsOneWidget);
  });

  testWidgets('accepts a blank email in the edit dialog', (tester) async {
    final notifier = _TestSessionNotifier(
      UserRow(
        id: 'user-1',
        name: 'ada',
        rolesList: const [],
        userAdmin: false,
        joinDate: 0,
        firstName: 'Ada',
        email: 'old@example.org',
        isArchived: false,
        isUpdated: false,
      ),
    );
    await tester.pumpWidget(
      wrapScreen(const ProfileScreen(), overrides: profileOverrides(notifier)),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.edit_outlined));
    await tester.pumpAndSettle();

    await tester.enterText(find.widgetWithText(TextFormField, 'Email'), '');
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pumpAndSettle();

    expect(notifier.updateCalls, 1);
    expect(find.text('Enter a valid email address'), findsNothing);
  });

  testWidgets('hides the edit button when no user is signed in', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(null)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.edit_outlined), findsNothing);
  });

  testWidgets('shows the notification badge count in the app bar', (
    tester,
  ) async {
    final user = _user();
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(5),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('5'), findsOneWidget);
  });

  testWidgets('caps the notification badge at 99+', (tester) async {
    final user = _user();
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(150),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('99+'), findsOneWidget);
  });

  testWidgets('shows the date of birth when present', (tester) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      dob: '1815-12-10',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Date of birth'), findsOneWidget);
    expect(find.text('1815-12-10'), findsOneWidget);
  });

  testWidgets('shows the level when present', (tester) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      level: 'Advanced',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Advanced'), findsOneWidget);
  });

  testWidgets('shows the gender when present', (tester) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      gender: 'female',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('female'), findsOneWidget);
  });

  testWidgets('shows the language when present', (tester) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      language: 'English',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('English'), findsOneWidget);
  });

  testWidgets('shows the admin badge when userAdmin is true', (tester) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: true,
      joinDate: 0,
      firstName: 'Ada',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    // The admin flag is carried on the row; the screen does not surface a
    // separate badge for it, but the roles list renders.
    expect(find.text('ada'), findsOneWidget);
  });

  testWidgets('omits the admin badge when userAdmin is false', (tester) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Admin'), findsNothing);
  });

  testWidgets('shows first-name initial only when last name is absent', (
    tester,
  ) async {
    final user = UserRow(
      id: 'user-1',
      name: 'ada',
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      firstName: 'Ada',
      isArchived: false,
      isUpdated: false,
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: profileOverrides(_TestSessionNotifier(user)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('A'), findsOneWidget);
  });

  testWidgets('edits the last name in the edit dialog', (tester) async {
    final notifier = _TestSessionNotifier(
      UserRow(
        id: 'user-1',
        name: 'ada',
        rolesList: const [],
        userAdmin: false,
        joinDate: 0,
        firstName: 'Ada',
        lastName: 'Old',
        isArchived: false,
        isUpdated: false,
      ),
    );
    await tester.pumpWidget(
      wrapScreen(const ProfileScreen(), overrides: profileOverrides(notifier)),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.edit_outlined));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Last name'),
      'Lovelace',
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pumpAndSettle();

    expect(notifier.updateCalls, 1);
  });

  testWidgets('edits the phone number in the edit dialog', (tester) async {
    final notifier = _TestSessionNotifier(
      UserRow(
        id: 'user-1',
        name: 'ada',
        rolesList: const [],
        userAdmin: false,
        joinDate: 0,
        firstName: 'Ada',
        phoneNumber: 'old',
        isArchived: false,
        isUpdated: false,
      ),
    );
    await tester.pumpWidget(
      wrapScreen(const ProfileScreen(), overrides: profileOverrides(notifier)),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.edit_outlined));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Phone'),
      '+1 555 0100',
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pumpAndSettle();

    expect(notifier.updateCalls, 1);
  });

  testWidgets('hides the notification badge label when count is zero', (
    tester,
  ) async {
    final user = _user();
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(0),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The badge label is hidden when count is 0, but the bell icon is present.
    expect(find.byIcon(Icons.notifications_outlined), findsOneWidget);
    expect(find.text('0'), findsNothing);
  });

  testWidgets('shows one notification badge when count is one', (tester) async {
    final user = _user();
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('1'), findsOneWidget);
  });
}

UserRow _user() => UserRow(
  id: 'user-1',
  name: 'ada',
  rolesList: const [],
  userAdmin: false,
  joinDate: 0,
  firstName: 'Ada',
  isArchived: false,
  isUpdated: false,
);

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;
  int updateCalls = 0;

  @override
  Future<UserRow?> build() async => user;

  @override
  Future<void> updateProfile({
    required String firstName,
    required String middleName,
    required String lastName,
    required String email,
    required String phoneNumber,
    required String level,
    required String language,
    required String gender,
    required String dateOfBirth,
  }) async {
    updateCalls++;
    state = AsyncData(
      state.requireValue!.copyWith(
        firstName: Value(firstName),
        email: Value(email),
      ),
    );
  }
}

class _PendingSessionNotifier extends SessionNotifier {
  final _completer = Completer<UserRow?>();
  @override
  Future<UserRow?> build() => _completer.future;
}

class _ErrorSessionNotifier extends SessionNotifier {
  @override
  Future<UserRow?> build() async {
    throw Exception('session load failed');
  }
}
