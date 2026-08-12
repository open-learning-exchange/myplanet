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
import 'package:myplanet/repository/progress_repository.dart';
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
    List<CourseCompletion> completedCourses = const [],
    bool Function(String courseId)? isCertified,
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
    completedCoursesProvider.overrideWith(
      (ref, userId) async => completedCourses,
    ),
    if (isCertified != null)
      isCourseCertifiedProvider.overrideWith(
        (ref, courseId) async => isCertified(courseId),
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

  testWidgets('renders a star per completed course, tinted by certification', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(
          user: _user('user-1'),
          completedCourses: const [
            CourseCompletion(courseId: 'cert-course', courseTitle: 'Math 101'),
            CourseCompletion(
              courseId: 'plain-course',
              courseTitle: 'Reading 101',
            ),
          ],
          // Only `cert-course` is certified; the Kotlin's `setColor` tints it
          // primary and the other `md_blue_grey_300`.
          isCertified: (courseId) => courseId == 'cert-course',
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Two stars, each carrying a content-description prefix the Kotlin uses.
    expect(find.byTooltip('completed course Math 101'), findsOneWidget);
    expect(find.byTooltip('completed course Reading 101'), findsOneWidget);

    // The certified star is primary blue; the uncertified one is blue-grey.
    final certifiedIcon = tester.widget<Icon>(
      find.descendant(
        of: find.byTooltip('completed course Math 101'),
        matching: find.byType(Icon),
      ),
    );
    expect(certifiedIcon.color, const Color(0xFF1976D2));

    final plainIcon = tester.widget<Icon>(
      find.descendant(
        of: find.byTooltip('completed course Reading 101'),
        matching: find.byType(Icon),
      ),
    );
    expect(plainIcon.color, const Color(0xFF90A4AE));
  });

  testWidgets('hides the star row when no courses are completed', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('user-1')),
      ),
    );
    await tester.pumpAndSettle();

    // No star icons render: `find.byIcon(Icons.star)` is the badge's signature.
    expect(find.byIcon(Icons.star), findsNothing);
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

  testWidgets('language overflow opens a single-choice dialog', (tester) async {
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
    await tester.tap(find.text('Language'));
    await tester.pumpAndSettle();

    // All six languages the Kotlin's `SettingsActivity.languageChanger` offers,
    // in its order, labelled with the endonyms from `values/strings.xml` — now
    // that ar/fr/ne/so have shipped .arb files, none of them falls back wholly
    // to English any more.
    expect(find.text('Select Language'), findsOneWidget);
    for (final label in [
      'english',
      'español',
      'somali',
      'नेपाली',
      'عربى',
      'français',
    ]) {
      expect(find.text(label), findsOneWidget, reason: label);
    }

    await tester.tap(find.text('नेपाली'));
    await tester.pumpAndSettle();

    expect(prefs.languageCode, 'ne');
  });

  testWidgets('about overflow navigates to the about screen', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('user-1')),
        pushTargets: {'/profile/about': (_) => const Placeholder()},
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('More options'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('About'));
    await tester.pumpAndSettle();

    expect(find.byType(Placeholder), findsOneWidget);
  });

  testWidgets('disclaimer overflow navigates to the disclaimer screen', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('user-1')),
        pushTargets: {'/profile/disclaimer': (_) => const Placeholder()},
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('More options'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Disclaimer'));
    await tester.pumpAndSettle();

    expect(find.byType(Placeholder), findsOneWidget);
  });
}
