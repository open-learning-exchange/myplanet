import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/health_repository.dart';
import 'package:myplanet/ui/health/add_health_screen.dart';

import '../support/widget_harness.dart';

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

class _NoopApi extends Mock implements PlanetApi {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<void> seedUser(AppDatabase db) async {
    await db.userDao.upsert(
      UsersCompanion.insert(
        id: 'user-1',
        couchId: const Value('org.couchdb.user:alice'),
        name: const Value('alice'),
        firstName: const Value('Alice'),
        lastName: const Value('Smith'),
        email: const Value('alice@example.com'),
        phoneNumber: const Value('+254700'),
        birthPlace: const Value('Nairobi'),
        dob: const Value('1990-06-15T00:00:00.000Z'),
        joinDate: const Value(1000),
      ),
    );
  }

  /// Builds a [HealthRepository] bound to [db], bypassing the provider graph
  /// (which transitively reads `planetPrefsProvider`/`serverConfigProvider`
  /// and so cannot run under the widget-test harness).
  HealthRepository repoFor(AppDatabase db, {int counter = 0}) {
    return HealthRepository(
      _NoopApi(),
      db.healthExaminationDao,
      db.userDao,
      createId: () => 'health-local-${++counter}',
    );
  }

  Future<AppDatabase> pumpScreen(
    WidgetTester tester, {
    UserRow? user,
    bool seed = false,
  }) async {
    final db = AppDatabase.memory();
    if (seed) await seedUser(db);
    await tester.pumpWidget(
      wrapScreen(
        const AddHealthScreen(),
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          healthRepositoryProvider.overrideWith((ref) => repoFor(db)),
        ],
      ),
    );
    await tester.pumpAndSettle();
    return db;
  }

  testWidgets('loads existing user fields and birthplace into the form', (
    tester,
  ) async {
    await pumpScreen(
      tester,
      seed: true,
      user: buildUserRow(
        id: 'user-1',
        firstName: 'Alice',
        lastName: 'Smith',
        email: 'alice@example.com',
        phoneNumber: '+254700',
        birthPlace: 'Nairobi',
        dob: '1990-06-15T00:00:00.000Z',
      ),
    );

    expect(find.text('Alice'), findsOneWidget);
    expect(find.text('Smith'), findsOneWidget);
    expect(find.text('alice@example.com'), findsOneWidget);
    expect(find.text('+254700'), findsOneWidget);
    expect(find.text('Nairobi'), findsOneWidget);
    expect(find.text('15-06-1990'), findsOneWidget);
  });

  testWidgets('shows birth place field', (tester) async {
    await pumpScreen(tester, user: buildUserRow(id: 'user-1'));
    expect(find.text('Birth place'), findsOneWidget);
  });

  testWidgets('contact type dropdown has Phone and Email options', (
    tester,
  ) async {
    await pumpScreen(tester, user: buildUserRow(id: 'user-1'));

    final dropdown = find.byType(DropdownButtonFormField<int>);
    await tester.dragUntilVisible(
      dropdown,
      find.byType(Scrollable).first,
      const Offset(0, -200),
    );
    await tester.tap(dropdown);
    await tester.pumpAndSettle();

    expect(find.text('Phone'), findsWidgets);
    expect(find.text('Email'), findsWidgets);
  });

  testWidgets('requires first name', (tester) async {
    await pumpScreen(
      tester,
      user: buildUserRow(id: 'user-1', firstName: 'X'),
    );

    final fnameField = find.ancestor(
      of: find.text('First name'),
      matching: find.byType(TextFormField),
    );
    await tester.enterText(fnameField, '');

    final saveButton = find.widgetWithText(FilledButton, 'Save');
    await tester.dragUntilVisible(
      saveButton,
      find.byType(Scrollable).first,
      const Offset(0, -200),
    );
    // Drag a bit more so the button is centered and tap-able.
    await tester.drag(find.byType(Scrollable).first, const Offset(0, -100));
    await tester.pump();
    await tester.tap(saveButton, warnIfMissed: false);
    await tester.pumpAndSettle();

    // Scroll back to the first name field so its error text is rendered.
    await tester.dragUntilVisible(
      fnameField,
      find.byType(Scrollable).first,
      const Offset(0, 200),
    );
    await tester.pumpAndSettle();

    expect(find.text('This field is required'), findsOneWidget);
  });

  testWidgets('persists profile via saveHealthProfile on save', (tester) async {
    final db = await pumpScreen(
      tester,
      seed: true,
      user: buildUserRow(
        id: 'user-1',
        firstName: 'Alice',
        lastName: 'Smith',
        email: 'alice@example.com',
        phoneNumber: '+254700',
        birthPlace: 'Nairobi',
        dob: '1990-06-15T00:00:00.000Z',
      ),
    );

    final contactField = find.ancestor(
      of: find.text('Contact name'),
      matching: find.byType(TextFormField),
    );
    await tester.dragUntilVisible(
      contactField,
      find.byType(Scrollable).first,
      const Offset(0, -200),
    );
    await tester.enterText(contactField, 'Bob');

    final saveButton = find.widgetWithText(FilledButton, 'Save');
    await tester.dragUntilVisible(
      saveButton,
      find.byType(Scrollable).first,
      const Offset(0, -200),
    );
    // The button sits at the very bottom of the form and its center falls
    // outside the test viewport, so `tap()` never hits it. Invoke the
    // `onPressed` callback directly instead.
    tester.widget<FilledButton>(saveButton).onPressed!.call();
    await tester.pumpAndSettle();

    final health = await repoFor(db).getHealthProfile('user-1');
    expect(health, isNotNull);
    expect(health!.profile, isNotNull);
    expect(health.profile!.emergencyContactName, 'Bob');
  });
}
