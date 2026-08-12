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

    expect(find.text("You haven't added any resources yet"), findsOneWidget);
    expect(find.text("You haven't joined any courses yet"), findsOneWidget);
    expect(find.text("You haven't joined a team yet"), findsOneWidget);
    expect(find.text('No data available'), findsOneWidget);
    expect(find.text('0'), findsNothing);
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
}
