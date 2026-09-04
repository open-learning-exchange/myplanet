import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/dashboard_providers.dart';
import 'package:myplanet/providers/health_provider.dart';
import 'package:myplanet/providers/life_provider.dart';
import 'package:myplanet/providers/network_status_provider.dart';
import 'package:myplanet/providers/notifications_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/sync_state.dart';
import 'package:myplanet/repository/notifications_repository.dart';
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
  isUpdated: false,
);

TeamRow _team(String id, String name) => TeamRow(
  id: id,
  name: name,
  courses: const [],
  createdDate: 0,
  limit: 0,
  isPublic: false,
  isLeader: false,
  beginningBalance: 0,
  sales: 0,
  otherIncome: 0,
  wages: 0,
  otherExpenses: 0,
  startDate: 0,
  endDate: 0,
  updatedDate: 0,
  date: 0,
  amount: 0,
  isUpdated: false,
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

class _TestNetworkStatusNotifier extends NetworkStatusNotifier {
  _TestNetworkStatusNotifier(this.status);

  final NetworkStatus status;

  /// Overriding `build` keeps the real notifier's probe from firing at a real
  /// server when a test mounts the dashboard.
  @override
  NetworkStatus build() => status;
}

/// Records `sync` calls instead of running the key/IV fetch. Overriding the
/// provider in every harness below also keeps the real notifier from reading
/// the (un-overridden) server config.
class RecordingKeyIvSync extends HealthKeyIvSyncNotifier {
  final calls = <String?>[];

  @override
  Future<void> sync(String? role) async => calls.add(role);
}

void main() {
  Future<List<Override>> homeOverrides({
    UserRow? user,
    PlanetPrefs? prefs,
    List<MyLibraryRow> library = const [],
    List<CourseRow> courses = const [],
    List<PendingSurvey> pendingSurveys = const [],
    List<TeamRow> teams = const [],
    List<CompletedCourse> completedCourses = const [],
    Map<String, TeamNotificationInfo> teamNotifications = const {},
    int offlineLogins = 0,
    NetworkStatus networkStatus = NetworkStatus.connected,
    RecordingKeyIvSync? keyIvSync,
  }) async => [
    sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
    planetPrefsProvider.overrideWithValue(prefs ?? await _prefs()),
    healthKeyIvSyncProvider.overrideWith(
      () => keyIvSync ?? RecordingKeyIvSync(),
    ),
    unreadNotificationCountProvider.overrideWith((ref) => Stream.value(0)),
    myLibraryStreamProvider.overrideWith(
      (ref, userId) => Stream.value(library),
    ),
    myCoursesStreamProvider.overrideWith(
      (ref, userId) => Stream.value(courses),
    ),
    myTeamsStreamProvider.overrideWith((ref, userId) => Stream.value(teams)),
    lifeItemsProvider.overrideWith((ref) => Stream.value(const [])),
    pendingSurveysProvider.overrideWith((ref, userId) async => pendingSurveys),
    lastSyncProvider.overrideWith(
      () => _TestLastSyncNotifier(prefs?.lastSync ?? 0),
    ),
    completedCoursesProvider.overrideWith(
      (ref, userId) async => completedCourses,
    ),
    teamNotificationsProvider.overrideWith(
      (ref, userId) async => teamNotifications,
    ),
    offlineLoginCountProvider.overrideWith(
      (ref, userName) async => offlineLogins,
    ),
    networkStatusProvider.overrideWith(
      () => _TestNetworkStatusNotifier(networkStatus),
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
    // `R.string.user_name` is "%1$s (%2$s)" — the name carries its
    // offline-login count.
    expect(find.text('Ada Lovelace (0)'), findsOneWidget);
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
            PendingSurvey(
              submissionId: 'sub-1',
              surveyId: 'survey-1',
              name: 'Health check',
            ),
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
            PendingSurvey(
              submissionId: 'sub-1',
              surveyId: 'survey-1',
              name: 'Health check',
            ),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('You have 1 survey to complete'), findsNothing);
  });

  testWidgets('tapping a pending survey resumes that submission', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(
          user: _user('user-1'),
          pendingSurveys: const [
            PendingSurvey(
              submissionId: 'sub-1',
              surveyId: 'survey-1',
              name: 'Health check',
            ),
          ],
        ),
        pushTargets: {
          '/life/surveys/survey-1': (_) =>
              const Placeholder(key: Key('resume-survey')),
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Health check'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('resume-survey')), findsOneWidget);
  });

  testWidgets('never shows the survey dialog to a guest', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(
          user: _user('guest_ada'),
          pendingSurveys: const [
            PendingSurvey(
              submissionId: 'sub-1',
              surveyId: 'survey-1',
              name: 'Health check',
            ),
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

  testWidgets(
    'tapping the courses header opens the shelf when the user has courses',
    (tester) async {
      await tester.pumpWidget(
        wrapScreen(
          const HomeScreen(),
          overrides: await homeOverrides(
            user: _user('user-1'),
            courses: [buildCourseRow(id: 'course-1', courseTitle: 'Algebra')],
          ),
          pushTargets: {'/courses': (_) => const Placeholder()},
        ),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(HomeScreen)),
      );

      await tester.tap(find.text('myCourses'));
      await tester.pumpAndSettle();

      expect(container.read(courseFilterProvider).myCoursesOnly, isTrue);
    },
  );

  testWidgets(
    'tapping the courses header opens the catalog when the user has no courses',
    (tester) async {
      await tester.pumpWidget(
        wrapScreen(
          const HomeScreen(),
          overrides: await homeOverrides(user: _user('user-1')),
          pushTargets: {'/courses': (_) => const Placeholder()},
        ),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(HomeScreen)),
      );

      await tester.tap(find.text('myCourses'));
      await tester.pumpAndSettle();

      expect(container.read(courseFilterProvider).myCoursesOnly, isFalse);
    },
  );

  testWidgets('a guest tapping the courses header is offered membership', (
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

    await tester.tap(find.text('myCourses'));
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

  testWidgets('the name line carries the offline-login count', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('user-1'), offlineLogins: 7),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Ada Lovelace (7)'), findsOneWidget);
  });

  testWidgets('a star per completed course, tinted when certified', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(
          user: _user('user-1'),
          completedCourses: const [
            CompletedCourse(
              courseId: 'course-1',
              courseTitle: 'Algebra',
              certified: true,
            ),
            CompletedCourse(
              courseId: 'course-2',
              courseTitle: 'Geometry',
              certified: false,
            ),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.star), findsNWidgets(2));
    // The content description the Kotlin sets on each star.
    expect(find.byTooltip('completed course Algebra'), findsOneWidget);
    expect(find.byTooltip('completed course Geometry'), findsOneWidget);

    final certified = tester.widget<Icon>(
      find.descendant(
        of: find.byTooltip('completed course Algebra'),
        matching: find.byIcon(Icons.star),
      ),
    );
    final uncertified = tester.widget<Icon>(
      find.descendant(
        of: find.byTooltip('completed course Geometry'),
        matching: find.byIcon(Icons.star),
      ),
    );
    // `colorPrimary` versus `md_blue_grey_300` — the star says "done", the
    // colour says "certified".
    expect(certified.color, isNot(uncertified.color));
    expect(uncertified.color, const Color(0xFF90A4AE));
  });

  testWidgets('no stars when nothing is completed', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(user: _user('user-1')),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.star), findsNothing);
  });

  testWidgets('team tiles carry chat and task alert badges', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(
          user: _user('user-1'),
          teams: [
            _team('team-1', 'Bees'),
            _team('team-2', 'Ants'),
            _team('team-3', 'Moths'),
          ],
          teamNotifications: const {
            'team-1': TeamNotificationInfo(hasTask: false, hasChat: true),
            'team-2': TeamNotificationInfo(hasTask: true, hasChat: false),
            'team-3': TeamNotificationInfo(hasTask: false, hasChat: false),
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.chat_bubble), findsOneWidget);
    expect(find.byIcon(Icons.assignment_late), findsOneWidget);
  });

  testWidgets(
    'snoozing a survey schedules a reminder and suppresses the dialog',
    (tester) async {
      final prefs = await _prefs();
      final overrides = await homeOverrides(
        user: _user('user-1'),
        prefs: prefs,
        pendingSurveys: const [
          PendingSurvey(
            submissionId: 'sub-1',
            surveyId: 'survey-1',
            name: 'Health check',
          ),
          PendingSurvey(
            submissionId: 'sub-2',
            surveyId: 'survey-2',
            name: 'Water survey',
          ),
        ],
      );
      Future<void> mountHome() => tester.pumpWidget(
        wrapScreen(const HomeScreen(), overrides: overrides),
      );

      await mountHome();
      await tester.pumpAndSettle();
      expect(find.text('You have 2 surveys to complete'), findsOneWidget);

      await tester.tap(find.text('Remind Later'));
      await tester.pumpAndSettle();

      expect(find.text('Remind Me Later'), findsOneWidget);
      await tester.tap(find.text('Set Reminder'));
      await tester.pumpAndSettle();

      // Keyed by the comma-joined submission ids, as the Kotlin keys it.
      expect(prefs.isReminderScheduled('sub-1,sub-2'), isTrue);

      // The hourly dialog must not undo the snooze. Clearing the throttle proves
      // the reminder guard is what suppresses it, not the hour.
      await prefs.setLastSurveyDialogShown(0);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
      await mountHome();
      await tester.pumpAndSettle();

      expect(find.text('You have 2 surveys to complete'), findsNothing);
    },
  );

  testWidgets('the remind-later amount is capped per unit', (tester) async {
    final prefs = await _prefs();
    await tester.pumpWidget(
      wrapScreen(
        const HomeScreen(),
        overrides: await homeOverrides(
          user: _user('user-1'),
          prefs: prefs,
          pendingSurveys: const [
            PendingSurvey(
              submissionId: 'sub-1',
              surveyId: 'survey-1',
              name: 'Health check',
            ),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Remind Later'));
    await tester.pumpAndSettle();

    // Minutes cap at 60, so dragging the slider to its end gives 60 minutes.
    await tester.drag(find.byType(Slider), const Offset(500, 0));
    await tester.pumpAndSettle();
    expect(find.text('60 Minutes'), findsOneWidget);

    // Switching to days lowers the cap to 30, which has to clamp the value.
    await tester.tap(find.text('Days'));
    await tester.pumpAndSettle();
    expect(find.text('30 Days'), findsOneWidget);
  });

  // Port of `BellDashboardFragment.onViewCreated`'s `syncKeyId()` gate:
  // `!guest && TextUtils.isEmpty(user.key)`.
  group('key/iv sync trigger', () {
    testWidgets(
      'a non-guest user without a health key triggers the key/iv sync',
      (tester) async {
        final keyIvSync = RecordingKeyIvSync();
        await tester.pumpWidget(
          wrapScreen(
            const HomeScreen(),
            overrides: await homeOverrides(
              user: _user('user-1'),
              keyIvSync: keyIvSync,
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(keyIvSync.calls, ['learner']);
      },
    );

    testWidgets('a user with a stored key does not trigger it', (tester) async {
      final keyIvSync = RecordingKeyIvSync();
      await tester.pumpWidget(
        wrapScreen(
          const HomeScreen(),
          overrides: await homeOverrides(
            user: UserRow(
              id: 'user-1',
              name: 'ada',
              rolesList: const ['learner'],
              userAdmin: false,
              joinDate: 0,
              key: 'stored-key',
              iv: 'stored-iv',
              isArchived: false,
              isUpdated: false,
            ),
            keyIvSync: keyIvSync,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(keyIvSync.calls, isEmpty);
    });

    testWidgets('a guest never triggers it', (tester) async {
      final keyIvSync = RecordingKeyIvSync();
      await tester.pumpWidget(
        wrapScreen(
          const HomeScreen(),
          overrides: await homeOverrides(
            // `guest_ada`, not `guest-ada`. This fixture used to carry a
            // hyphen while the three other guest fixtures in this file used
            // the underscore, and the pre-Phase-112 five-character
            // `startsWith('guest')` could not tell them apart — so a row
            // `createGuestUser` can never write was standing in for a guest.
            // `UserMapper.guestIdPrefix` is `guest_`.
            user: _user('guest_ada'),
            keyIvSync: keyIvSync,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(keyIvSync.calls, isEmpty);
    });
  });
}
