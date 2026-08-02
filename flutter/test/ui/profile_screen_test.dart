import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/user/profile_screen.dart';

import '../support/widget_harness.dart';

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
    );

    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
        ],
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
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(null)),
        ],
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
      ),
    );
    await tester.pumpWidget(
      wrapScreen(
        const ProfileScreen(),
        overrides: [sessionProvider.overrideWith(() => notifier)],
      ),
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
}

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
import 'package:drift/drift.dart';
import 'package:flutter/material.dart';
