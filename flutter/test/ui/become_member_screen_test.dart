import 'dart:async';

import 'package:drift/drift.dart' hide isNotNull, isNull;
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/repository/user_repository.dart';
import 'package:myplanet/ui/user/become_member_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/widget_harness.dart';

class _MockSecureStorage extends Mock implements FlutterSecureStorage {}

class _MockPlanetApi extends Mock implements PlanetApi {}

const _config = ServerConfig(
  serverUrl: 'https://planet.example.org',
  pin: '1234',
  couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  code: 'gt',
  parentCode: 'nation',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  registerFallbackValue(<String, dynamic>{});
  registerFallbackValue('');

  late AppDatabase db;
  late _MockPlanetApi api;
  late PlanetPrefs prefs;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    final secureStorage = _MockSecureStorage();
    when(
      () => secureStorage.write(
        key: any(named: 'key'),
        value: any(named: 'value'),
      ),
    ).thenAnswer((_) async => {});
    when(
      () => secureStorage.read(key: any(named: 'key')),
    ).thenAnswer((_) async => null);
    prefs = PlanetPrefs(
      await SharedPreferences.getInstance(),
      secureStorage: secureStorage,
    );
    db = AppDatabase.memory();
    api = _MockPlanetApi();
  });

  tearDown(() => db.close());

  Future<void> pumpScreen(
    WidgetTester tester, {
    ServerConfig? serverConfig,
  }) async {
    await tester.pumpWidget(
      wrapScreen(
        // The screen calls context.pop(), so it cannot be the root route.
        Builder(
          builder: (context) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              final router = GoRouter.of(context);
              final currentLocation = router
                  .routerDelegate
                  .currentConfiguration
                  .last
                  .matchedLocation;
              if (currentLocation != '/become-member') {
                router.push('/become-member');
              }
            });
            return const Scaffold(body: Text('ROOT_PAGE'));
          },
        ),
        pushTargets: {
          '/become-member': (context) => const BecomeMemberScreen(),
          '/login': (context) => const Scaffold(body: Text('LOGIN_PAGE')),
        },
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          userRepositoryProvider.overrideWith(
            (ref) => UserRepository(api, db.userDao),
          ),
          if (serverConfig != null)
            serverConfigProvider.overrideWith(
              () => _TestServerConfig(serverConfig),
            ),
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> tapSubmit(WidgetTester tester) async {
    await tester.ensureVisible(
      find.widgetWithText(FilledButton, 'Become a member'),
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Become a member'));
    await tester.pump();
  }

  Future<void> selectMale(WidgetTester tester) async {
    await tester.ensureVisible(find.text('Male'));
    await tester.tap(find.text('Male'));
    await tester.pump();
  }

  Future<void> fillRequired(
    WidgetTester tester, {
    String username = 'newuser',
    String password = 'secret',
  }) async {
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Enter username'),
      username,
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Enter password'),
      password,
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Retype password'),
      password,
    );
  }

  group('form validation', () {
    testWidgets('requires a username', (tester) async {
      await pumpScreen(tester);
      await tapSubmit(tester);
      expect(find.text('Enter username'), findsWidgets);
    });

    testWidgets('requires a password', (tester) async {
      await pumpScreen(tester);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Enter username'),
        'newuser',
      );
      await tapSubmit(tester);
      expect(find.text('Please enter a password'), findsOneWidget);
    });

    testWidgets('rejects mismatched passwords with inline error after submit', (
      tester,
    ) async {
      await pumpScreen(tester);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Enter username'),
        'newuser',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Enter password'),
        'secret',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Retype password'),
        'different',
      );
      await tapSubmit(tester);
      expect(find.text('Passwords do not match'), findsWidgets);
    });

    testWidgets('rejects an invalid email format', (tester) async {
      await pumpScreen(tester);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Email'),
        'not-an-email',
      );
      await tapSubmit(tester);
      expect(find.text('Enter a valid email address'), findsOneWidget);
    });

    testWidgets('accepts a blank email without error', (tester) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();
      expect(find.text('Enter a valid email address'), findsNothing);
    });
  });

  group('gender selection', () {
    testWidgets('requires a gender before creating a member', (tester) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();
      expect(find.text('Please select gender'), findsOneWidget);
    });
  });

  group('submission — username conflict', () {
    testWidgets('shows a snackbar when the username already exists', (
      tester,
    ) async {
      await db.userDao.upsert(
        UsersCompanion.insert(id: 'existing-1', name: const Value('taken')),
      );
      await pumpScreen(tester);
      await fillRequired(tester, username: 'taken');
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();
      expect(find.text('Username already exists'), findsOneWidget);
      expect(await db.userDao.count(), 1);
    });
  });

  group('submission — offline account', () {
    testWidgets('creates a local account when no server is configured', (
      tester,
    ) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'First name'),
        'Ada',
      );
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      expect(find.text('Member account created offline'), findsOneWidget);
      expect(find.text('LOGIN_PAGE'), findsOneWidget);

      final user = await db.userDao.getByName('newuser');
      expect(user, isNotNull);
      expect(user?.firstName, 'Ada');
      expect(user?.gender, 'male');
      expect(user?.password, 'secret');
      expect(user?.couchId, isNull);
    });
  });

  group('submission — online account', () {
    testWidgets('creates an account and navigates to login on success', (
      tester,
    ) async {
      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({
          'id': 'org.couchdb.user:newuser',
          'rev': '1-abc',
          'ok': true,
        }),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => NetworkSuccess({}));

      await pumpScreen(tester, serverConfig: _config);
      await fillRequired(tester);
      await tester.ensureVisible(find.text('Female'));
      await tester.tap(find.text('Female'));
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      expect(find.text('Member account created successfully'), findsOneWidget);
      expect(find.text('LOGIN_PAGE'), findsOneWidget);

      final user = await db.userDao.getByName('newuser');
      expect(user?.couchId, 'org.couchdb.user:newuser');
      expect(user?.rev, '1-abc');
      expect(user?.gender, 'female');
    });

    testWidgets('falls back to offline when the upload fails', (tester) async {
      when(
        () => api.putJsonObject(any(), any()),
      ).thenAnswer((_) async => NetworkError(500, 'server error'));

      await pumpScreen(tester, serverConfig: _config);
      await fillRequired(tester);
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      expect(find.text('Member account created offline'), findsOneWidget);
      final user = await db.userDao.getByName('newuser');
      expect(user?.couchId, isNull);
    });
  });

  group('username live validation', () {
    final usernameField = find.widgetWithText(TextFormField, 'Enter username');

    testWidgets('debounces the check 300 ms before showing an error', (
      tester,
    ) async {
      await pumpScreen(tester);
      // Typing a username with an illegal space — the check should only fire
      // after the 300 ms debounce window, not immediately on the keystroke.
      await tester.enterText(usernameField, 'bad name');
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.text('Invalid username'), findsNothing);
      await tester.pump(const Duration(milliseconds: 250));
      expect(find.text('Invalid username'), findsOneWidget);
    });

    testWidgets('cancels a pending check when the user keeps typing', (
      tester,
    ) async {
      await pumpScreen(tester);
      await tester.enterText(usernameField, 'bad name');
      await tester.pump(const Duration(milliseconds: 150));
      // Supersede the first input before its 300 ms timer fires.
      await tester.enterText(usernameField, 'goodname');
      await tester.pump(const Duration(milliseconds: 350));
      // The first input's error never lands; the second input is valid.
      expect(find.text('Invalid username'), findsNothing);
      expect(find.text('username taken'), findsNothing);
    });

    testWidgets('shows a taken error for an existing non-guest user', (
      tester,
    ) async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'org.couchdb.user:ada',
          couchId: const Value('org.couchdb.user:ada'),
          name: const Value('ada'),
        ),
      );
      await pumpScreen(tester);
      await tester.enterText(usernameField, 'ada');
      await tester.pump(const Duration(milliseconds: 350));
      expect(find.text('username taken'), findsOneWidget);
    });

    testWidgets('clears the error after the user fixes the input', (
      tester,
    ) async {
      await pumpScreen(tester);
      await tester.enterText(usernameField, 'bad name');
      await tester.pump(const Duration(milliseconds: 350));
      expect(find.text('Invalid username'), findsOneWidget);
      await tester.enterText(usernameField, 'goodname');
      await tester.pump(const Duration(milliseconds: 350));
      expect(find.text('Invalid username'), findsNothing);
    });

    testWidgets('blocks submit while a live error is showing', (tester) async {
      await pumpScreen(tester);
      await tester.enterText(usernameField, 'bad name');
      await tester.pump(const Duration(milliseconds: 350));
      // Fill the rest of the form so submit would otherwise proceed.
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Enter password'),
        'secret',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Retype password'),
        'secret',
      );
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();
      // No member created — the live error holds the form.
      expect(await db.userDao.count(), 0);
    });
  });

  group('username normalization', () {
    testWidgets('lowercases the username before checking for duplicates', (
      tester,
    ) async {
      await db.userDao.upsert(
        UsersCompanion.insert(id: 'existing-1', name: const Value('existing')),
      );
      await pumpScreen(tester);
      await fillRequired(tester, username: 'EXISTING');
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();
      expect(find.text('Username already exists'), findsOneWidget);
    });
  });

  group('cancel', () {
    testWidgets('pops back without creating a user', (tester) async {
      await pumpScreen(tester);
      await tester.ensureVisible(find.widgetWithText(TextButton, 'Cancel'));
      await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
      await tester.pumpAndSettle();
      expect(find.text('ROOT_PAGE'), findsOneWidget);
      expect(await db.userDao.count(), 0);
    });
  });

  group('optional fields', () {
    testWidgets('accepts a first name and stores it on the local user', (
      tester,
    ) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'First name'),
        'Ada',
      );
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      final user = await db.userDao.getByName('newuser');
      expect(user?.firstName, 'Ada');
    });

    testWidgets('accepts a last name and stores it on the local user', (
      tester,
    ) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Last name'),
        'Lovelace',
      );
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      final user = await db.userDao.getByName('newuser');
      expect(user?.lastName, 'Lovelace');
    });

    testWidgets('accepts a middle name and stores it on the local user', (
      tester,
    ) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Middle name'),
        'Augusta',
      );
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      final user = await db.userDao.getByName('newuser');
      expect(user?.middleName, 'Augusta');
    });

    testWidgets('accepts a phone number and stores it on the local user', (
      tester,
    ) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await tester.ensureVisible(find.widgetWithText(TextFormField, 'Phone'));
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Phone'),
        '+1 555 0100',
      );
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      final user = await db.userDao.getByName('newuser');
      expect(user?.phoneNumber, '+1 555 0100');
    });

    testWidgets('selects a language from the dropdown', (tester) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await tester.ensureVisible(find.text('English'));
      await tester.tap(find.text('English'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('नेपाली').last);
      await tester.pumpAndSettle();
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      final user = await db.userDao.getByName('newuser');
      expect(user?.language, 'नेपाली');
    });

    testWidgets('selects a level from the dropdown', (tester) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await tester.ensureVisible(find.text('Beginner'));
      await tester.tap(find.text('Beginner'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Advanced').last);
      await tester.pumpAndSettle();
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      final user = await db.userDao.getByName('newuser');
      expect(user?.level, 'Advanced');
    });

    testWidgets('selects female gender', (tester) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await tester.ensureVisible(find.text('Female'));
      await tester.tap(find.text('Female'));
      await tester.pump();
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      final user = await db.userDao.getByName('newuser');
      expect(user?.gender, 'female');
    });

    testWidgets('stores male gender explicitly', (tester) async {
      await pumpScreen(tester);
      await fillRequired(tester);
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      final user = await db.userDao.getByName('newuser');
      expect(user?.gender, 'male');
    });

    testWidgets(
      'switching gender from male to female updates the stored value',
      (tester) async {
        await pumpScreen(tester);
        await fillRequired(tester);
        await selectMale(tester);
        // Change to female
        await tester.ensureVisible(find.text('Female'));
        await tester.tap(find.text('Female'));
        await tester.pump();
        await tapSubmit(tester);
        await tester.pumpAndSettle();

        final user = await db.userDao.getByName('newuser');
        expect(user?.gender, 'female');
      },
    );
  });

  group('password mismatch', () {
    testWidgets('blocks submission when passwords do not match', (
      tester,
    ) async {
      await pumpScreen(tester);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Enter username'),
        'newuser',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Enter password'),
        'secret',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Retype password'),
        'different',
      );
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pump();

      expect(find.text('Passwords do not match'), findsOneWidget);
      expect(await db.userDao.count(), 0);
    });

    testWidgets('clears the mismatch error when passwords are corrected', (
      tester,
    ) async {
      await pumpScreen(tester);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Enter username'),
        'newuser',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Enter password'),
        'secret',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Retype password'),
        'different',
      );
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pump();
      expect(find.text('Passwords do not match'), findsOneWidget);

      // Fix the retyped password.
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Retype password'),
        'secret',
      );
      await tapSubmit(tester);
      await tester.pumpAndSettle();

      // The user was created — no mismatch error held it back.
      expect(find.text('Passwords do not match'), findsNothing);
      expect(await db.userDao.getByName('newuser'), isNotNull);
    });
  });

  group('loading state', () {
    testWidgets('shows a spinner while submitting', (tester) async {
      final completer = Completer<NetworkResult<Map<String, dynamic>>>();
      when(() => api.putJsonObject(any(), any())).thenAnswer(
        (_) async => NetworkSuccess({'id': 'x', 'rev': '1', 'ok': true}),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) => completer.future);

      await pumpScreen(tester, serverConfig: _config);
      await fillRequired(tester);
      await selectMale(tester);
      await tapSubmit(tester);
      await tester.pump(const Duration(milliseconds: 50));

      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(
        find.widgetWithText(TextFormField, 'Enter username'),
        findsNothing,
      );

      // Complete the future so the test can end without pending timers.
      completer.complete(NetworkSuccess({}));
      await tester.pumpAndSettle();
    });
  });
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);
  final ServerConfig config;
  @override
  ServerConfig? build() => config;
}
