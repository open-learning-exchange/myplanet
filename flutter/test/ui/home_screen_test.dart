import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_providers.dart';
import 'package:myplanet/providers/life_provider.dart';
import 'package:myplanet/providers/notifications_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/sync_state.dart';
import 'package:myplanet/ui/dashboard/home_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/widget_harness.dart';

UserRow _user(String id, {String? planetCode}) => UserRow(
  id: id,
  name: 'ada',
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  firstName: 'Ada',
  lastName: 'Lovelace',
  planetCode: planetCode,
  isArchived: false,
);

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

void main() {
  Future<List<Override>> homeOverrides({
    UserRow? user,
    PlanetPrefs? prefs,
    List<MyLibraryRow> library = const [],
    List<PendingSurvey> pendingSurveys = const [],
  }) async => [
    sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
    planetPrefsProvider.overrideWithValue(prefs ?? await _prefs()),
    unreadNotificationCountProvider.overrideWith((ref) => Stream.value(0)),
    myLibraryStreamProvider.overrideWith(
      (ref, userId) => Stream.value(library),
    ),
    myCoursesStreamProvider.overrideWith(
      (ref, userId) => Stream.value(const []),
    ),
    myTeamsStreamProvider.overrideWith((ref, userId) => Stream.value(const [])),
    lifeItemsProvider.overrideWith((ref) => Stream.value(const [])),
    pendingSurveysProvider.overrideWith((ref, userId) async => pendingSurveys),
    lastSyncProvider.overrideWith(
      () => _TestLastSyncNotifier(prefs?.lastSync ?? 0),
    ),
  ];

  testWidgets('renders the four cards with the planet title', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(
          user: _user('user-1', planetCode: 'guatemala'),
          library: [buildLibraryRow(id: 'res-1', title: 'Algebra basics')],
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Planet guatemala'), findsOneWidget);
    expect(find.text('myLibrary'), findsOneWidget);
    expect(find.text('myCourses'), findsOneWidget);
    expect(find.text('myTeams'), findsOneWidget);
    expect(find.text('myLife'), findsOneWidget);
    expect(find.text('Ada Lovelace'), findsOneWidget);
    // The library tile and its count badge.
    expect(find.text('Algebra basics'), findsOneWidget);
    expect(find.text('1'), findsOneWidget);
  });

  testWidgets('shows empty placeholders and hides zero counts', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('user-1')),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('You can add resources'), findsOneWidget);
    expect(find.text('You can join courses'), findsOneWidget);
    expect(find.text('You can join a team'), findsOneWidget);
    expect(find.text('No data available'), findsOneWidget);
    expect(find.text('0'), findsNothing);
    expect(find.text('Last synced: Never synced'), findsOneWidget);
  });

  testWidgets('shows a relative last-sync value', (tester) async {
    final prefs = await _prefs();
    await prefs.setLastSync(
      DateTime.now().subtract(const Duration(hours: 2)).millisecondsSinceEpoch,
    );
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('user-1'), prefs: prefs),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Last synced: 2 hours ago'), findsOneWidget);
  });

  testWidgets('shows the pending-survey dialog once and stamps the throttle', (
    tester,
  ) async {
    final prefs = await _prefs();
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(
          user: _user('user-1'),
          prefs: prefs,
          pendingSurveys: const [
            PendingSurvey(submissionId: 'sub-1', name: 'Health check'),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('You have 1 survey to complete'), findsOneWidget);
    expect(find.text('Health check'), findsOneWidget);
    expect(prefs.lastSurveyDialogShown, greaterThan(0));

    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(find.text('You have 1 survey to complete'), findsNothing);
  });

  testWidgets('throttles the survey dialog inside the hour', (tester) async {
    final prefs = await _prefs();
    await prefs.setLastSurveyDialogShown(DateTime.now().millisecondsSinceEpoch);

    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(
          user: _user('user-1'),
          prefs: prefs,
          pendingSurveys: const [
            PendingSurvey(submissionId: 'sub-1', name: 'Health check'),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('You have 1 survey to complete'), findsNothing);
  });

  testWidgets('never shows the survey dialog to a guest', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(
          user: _user('guest_ada'),
          pendingSurveys: const [
            PendingSurvey(submissionId: 'sub-1', name: 'Health check'),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('You have 1 survey to complete'), findsNothing);
  });

  testWidgets('guest tapping the library header is offered membership', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('guest_ada')),
        pushTargets: {'/become-member': (_) => const Placeholder()},
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('myLibrary'));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'You are currently a guest user. To access this feature, become a member.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('drawer exposes the wider dashboard navigation', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('user-1')),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Open navigation menu'));
    await tester.pumpAndSettle();

    await tester.drag(find.byType(NavigationDrawer), const Offset(0, -400));
    await tester.pumpAndSettle();
    expect(find.text('AI chat'), findsOneWidget);
    expect(find.text('Feedback'), findsOneWidget);
    expect(find.text('References'), findsOneWidget);
    expect(find.text('Community'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
  });

  testWidgets('overflow menu changes and persists the theme', (tester) async {
    final prefs = await _prefs();
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('user-1'), prefs: prefs),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('More options'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('App theme'));
    await tester.pumpAndSettle();

    expect(prefs.themeModeName, 'light');
  });

  testWidgets('shows a star for each completed course', (tester) async {
    final database = AppDatabase.memory();
    await _seedCompletedCourse(database, 'course-1', title: 'Algebra');
    await _seedCompletedCourse(database, 'course-2', title: 'Geometry');

    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: [
          ...await homeOverrides(user: _user('user-1')),
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(database.close);
            return database;
          }),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.star), findsNWidgets(2));
    expect(find.byTooltip('completed course Algebra'), findsOneWidget);
    expect(find.byTooltip('completed course Geometry'), findsOneWidget);
  });

  testWidgets('a certified course star uses the primary color', (tester) async {
    final database = AppDatabase.memory();
    await _seedCompletedCourse(database, 'course-1', title: 'Algebra');
    await database.certificationDao.upsertAll([
      CertificationsCompanion.insert(
        id: 'cert-1',
        courseIds: const Value('["course-1"]'),
      ),
    ]);

    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: [
          ...await homeOverrides(user: _user('user-1')),
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(database.close);
            return database;
          }),
        ],
      ),
    );
    await tester.pumpAndSettle();

    final icon = tester.widget<Icon>(find.byIcon(Icons.star));
    expect(
      icon.color,
      Theme.of(tester.element(find.byIcon(Icons.star))).colorScheme.primary,
    );
  });

  testWidgets('tapping a completed-course star opens the take-course route', (
    tester,
  ) async {
    final database = AppDatabase.memory();
    await _seedCompletedCourse(database, 'course-1', title: 'Algebra');

    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: [
          ...await homeOverrides(user: _user('user-1')),
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(database.close);
            return database;
          }),
        ],
        pushTargets: {
          '/courses/course-1/take': (_) =>
              const Scaffold(body: Center(child: Text('Take course Algebra'))),
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('completed course Algebra'));
    await tester.pumpAndSettle();

    expect(find.text('Take course Algebra'), findsOneWidget);
  });

  testWidgets('hides the star row when no course is complete', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('user-1')),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.star), findsNothing);
  });
}

/// Seeds a completed course: on the user's shelf, with one passed step, so the
/// star row surfaces it.
Future<void> _seedCompletedCourse(
  AppDatabase database,
  String courseId, {
  required String title,
}) async {
  await database.courseDao.upsertAll(
    [
      CoursesCompanion.insert(
        id: courseId,
        courseId: Value(courseId),
        courseTitle: Value(title),
        userId: const Value(['user-1']),
      ),
    ],
    [
      CourseStepsCompanion.insert(
        id: '$courseId:0',
        courseId: Value(courseId),
        stepIndex: const Value(0),
      ),
    ],
  );
  await database.courseProgressDao.upsert(
    CourseProgressCompanion.insert(
      id: 'p-$courseId',
      courseId: Value(courseId),
      userId: const Value('user-1'),
      stepNum: const Value(1),
      passed: const Value(true),
    ),
  );
}
