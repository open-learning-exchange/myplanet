import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../providers/app_providers.dart';
import '../providers/session_provider.dart';
import 'achievements/achievements_screen.dart';
import 'achievements/edit_achievement_screen.dart';
import 'calendar/calendar_screen.dart';
import 'chat/chat_detail_screen.dart';
import 'chat/chat_history_screen.dart';
import 'community/community_screen.dart';
import 'exam/take_exam_screen.dart';
import 'exam/user_information_screen.dart';
import 'courses/course_detail_screen.dart';
import 'courses/courses_screen.dart';
import 'courses/courses_progress_screen.dart';
import 'courses/take_course_screen.dart';
import 'dashboard/about_disclaimer_screens.dart';
import 'dashboard/activities_screen.dart';
import 'dashboard/dashboard_shell.dart';
import 'dashboard/home_screen.dart';
import 'dictionary/dictionary_screen.dart';
import 'events/event_detail_screen.dart';
import 'events/events_screen.dart';
import 'feedback/feedback_create_screen.dart';
import 'feedback/feedback_detail_screen.dart';
import 'feedback/feedback_list_screen.dart';
import 'health/my_health_screen.dart';
import 'health/add_health_screen.dart';
import 'health/add_examination_screen.dart';
import 'life/life_screen.dart';
import 'maps/offline_maps_screen.dart';
import 'onboarding/onboarding_screen.dart';
import 'notifications/notifications_screen.dart';
import 'personals/personals_screen.dart';
import 'resources/resource_detail_screen.dart';
import 'resources/add_resource_screen.dart';
import 'resources/resources_screen.dart';
import 'references/references_screen.dart';
import 'settings/settings_screen.dart';
import 'settings/storage_breakdown_screen.dart';
import 'settings/storage_category_detail_screen.dart';
import 'sync/login_screen.dart';
import 'sync/server_config_screen.dart';
import 'sync/sync_center_screen.dart';
import 'user/profile_screen.dart';
import 'user/become_member_screen.dart';
import 'submissions/submissions_screen.dart';
import 'submissions/submission_detail_screen.dart';
import 'surveys/surveys_screen.dart';
import 'voices/voice_thread_screen.dart';
import 'voices/voices_screen.dart';
import 'surveys/public_survey_screen.dart';
import 'surveys/take_survey_screen.dart';
import 'teams/teams_screen.dart';
import 'teams/team_tasks_screen.dart';
import 'teams/team_members_screen.dart';
import 'teams/member_detail_screen.dart';
import 'teams/leaderboard/team_leaderboard_screen.dart';
import 'teams/team_resources_screen.dart';
import 'teams/team_courses_screen.dart';
import 'teams/team_reports_screen.dart';
import 'teams/team_surveys_screen.dart';
import 'teams/team_voices_screen.dart';
import 'teams/team_plan_screen.dart';
import 'teams/team_finances_screen.dart';
import 'teams/team_calendar_screen.dart';
import 'viewer/resource_viewer_screen.dart';
import 'viewer/web_view_screen.dart';

/// Replaces the Activity/Fragment navigation in `ui/components/FragmentNavigator`
/// and the manual `Intent` hops between `SyncActivity` -> `LoginActivity` ->
/// `DashboardActivity`.
///
/// The gating those activities do imperatively (each one checking prefs in
/// `onCreate` and finishing itself) becomes one declarative [GoRouter.redirect]:
/// no server configured -> `/server`, no session -> `/login`, otherwise the
/// dashboard shell.
class Routes {
  const Routes._();

  static const String home = '/home';
  static const String server = '/server';
  static const String onboarding = '/onboarding';
  static const String login = '/login';
  static const String becomeMember = '/become-member';
  static const String offlineMaps = '/life/references/maps';
  static const String resources = '/resources';
  static const String resourceDetail = '/resources/detail/:resourceId';
  static const String addResource = '/resources/add';
  static const String webView = '/web-view';
  static const String teamLeaderboard = '/life/teams/:teamId/leaderboard';
  static const String resourceViewer = '/resources/viewer/:resourceId';
  static const String courses = '/courses';
  static const String myProgress = '/courses/progress';
  static const String calendar = '/calendar';
  static const String profile = '/profile';
  static const String settings = '/profile/settings';
  static const String dictionary = '/profile/settings/dictionary';
  static const String storageManagement = '/profile/settings/storage';
  static const String storageCategory = '/profile/settings/storage/category';
  static const String notifications = '/profile/notifications';
  static const String life = '/life';
  static const String references = '/life/references';
  static const String personals = '/life/personals';
  static const String submissions = '/life/submissions';
  static const String health = '/life/health';
  static const String addHealth = '/life/health/add';
  static const String addExamination = '/life/health/examination';
  static const String events = '/calendar/events';
  static const String surveys = '/life/surveys';
  static const String voices = '/life/voices';
  static const String teams = '/life/teams';
  static const String chatHistory = '/life/chat';
  static const String chat = '/life/chat/:chatId';
  static const String feedback = '/life/feedback';
  static const String feedbackDetail = '/life/feedback/:feedbackId';
  static const String feedbackCreate = '/life/feedback/create';
  static const String achievements = '/life/achievements';
  static const String editAchievement = '/life/achievements/edit';
  static const String about = '/about';
  static const String disclaimer = '/disclaimer';
  static const String community = '/community';
  static const String exam = '/courses/exam/:examId';
  static const String userInfo = '/exam/user-info/:submissionId';
  static const String publicSurvey = '/survey/:teamId/:surveyId';
  static const String syncCenter = '/sync-center';

  /// `ActivitiesFragment`, opened from the dashboard's `fab_my_activity`.
  static const String activities = '/activities';
}

/// The server a public-survey deep link points at.
///
/// Kotlin reads this straight off the incoming intent
/// (`"${uri.scheme}://${uri.encodedAuthority}"` in
/// `OnboardingActivity.maybeLaunchPublicSurvey`), which it can do because the
/// activity receives the raw `Intent.getData()`. Here the link arrives as a
/// go_router location, and that is only an absolute URI when the platform hands
/// one over. A relative location — an in-app `context.go('/survey/...')`, or an
/// engine that forwards the path alone — has no origin at all, and `Uri.origin`
/// *throws* `StateError` rather than returning an empty string, so reading it
/// unguarded crashes the route it is meant to build.
///
/// Three sources, in order:
///
/// 1. The `origin` query parameter, which `DeepLinkHandler` puts there from the
///    complete URI `app_links` gives it. This is the reliable one, and the
///    reason the plugin exists: it is the only path that recovers the origin
///    when the platform hands over a path-only location.
/// 2. The location's own origin, for a platform that did pass an absolute URI.
/// 3. The configured server, which is right for an in-app navigation and is a
///    guess for anything else.
///
/// A respondent with none of the three lands on the screen's own failure path —
/// "survey could not be loaded" — which is the honest outcome, because there is
/// nowhere to fetch the survey from.
String publicSurveyBaseUrl(Uri uri, String? configuredServerUrl) {
  final origin = uri.queryParameters['origin'];
  if (origin != null && origin.isNotEmpty) {
    final parsed = Uri.tryParse(origin);
    if (parsed != null && parsed.hasScheme && parsed.host.isNotEmpty) {
      return '${parsed.scheme}://${parsed.authority}';
    }
  }
  if (uri.hasScheme && uri.host.isNotEmpty) {
    return '${uri.scheme}://${uri.authority}';
  }
  return configuredServerUrl ?? '';
}

final _rootNavigatorKey = GlobalKey<NavigatorState>();

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    // The Kotlin app lands on the bell dashboard after login.
    initialLocation: Routes.home,
    refreshListenable: _RouterRefresh(ref),
    redirect: (context, state) {
      // Public surveys are answerable without onboarding, server config or a
      // signed-in session. Match before any gating redirects.
      final pathSegments = state.uri.pathSegments;
      if (pathSegments.isNotEmpty && pathSegments.first == 'survey') {
        return null;
      }

      final hasServer = ref.read(serverConfigProvider) != null;
      final onboardingComplete = ref.read(onboardingProvider);
      final session = ref.read(sessionProvider);
      final location = state.matchedLocation;

      // Onboarding does not depend on the asynchronous session restoration.
      // Gate it first to avoid flashing the resources screen on a fresh install.
      if (!onboardingComplete) {
        return location == Routes.onboarding ? null : Routes.onboarding;
      }

      // Hold position until the persisted session has been read back.
      if (session.isLoading) return null;

      final isSignedIn = session.valueOrNull != null;
      if (location == Routes.onboarding) {
        return hasServer ? Routes.login : Routes.server;
      }

      if (!hasServer) {
        return location == Routes.server ? null : Routes.server;
      }
      if (!isSignedIn) {
        return location == Routes.login ? null : Routes.login;
      }
      if (location == Routes.server || location == Routes.login) {
        return Routes.home;
      }
      return null;
    },
    routes: [
      GoRoute(
        path: Routes.onboarding,
        builder: (context, state) => const OnboardingScreen(),
      ),
      GoRoute(
        path: Routes.server,
        builder: (context, state) => const ServerConfigScreen(),
      ),
      GoRoute(
        path: Routes.login,
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: Routes.becomeMember,
        builder: (context, state) => const BecomeMemberScreen(),
      ),
      GoRoute(
        path: Routes.publicSurvey,
        builder: (context, state) => PublicSurveyScreen(
          baseUrl: publicSurveyBaseUrl(
            state.uri,
            ref.read(serverConfigProvider)?.serverUrl,
          ),
          teamId: state.pathParameters['teamId']!,
          surveyId: state.pathParameters['surveyId']!,
        ),
      ),
      GoRoute(
        path: Routes.offlineMaps,
        builder: (context, state) => const OfflineMapsScreen(),
      ),
      GoRoute(
        path: Routes.resourceDetail,
        builder: (context, state) => ResourceDetailScreen(
          resourceId: state.pathParameters['resourceId']!,
        ),
      ),
      GoRoute(
        path: 'add',
        builder: (context, state) => AddResourceScreen(
          teamId: state.uri.queryParameters['teamId'],
          editResourceId: state.uri.queryParameters['edit'],
        ),
      ),
      GoRoute(
        path: Routes.webView.replaceFirst('/', ''),
        builder: (context, state) => WebViewScreen(
          url: state.uri.queryParameters['url']!,
          title: state.uri.queryParameters['title'],
        ),
      ),
      GoRoute(
        path: Routes.resourceViewer,
        builder: (context, state) => ResourceViewerScreen(
          resourceId: state.pathParameters['resourceId']!,
        ),
      ),
      GoRoute(
        path: Routes.community,
        builder: (context, state) => const CommunityScreen(fromLogin: false),
      ),
      GoRoute(
        path: Routes.syncCenter,
        builder: (context, state) => const SyncCenterScreen(),
      ),
      GoRoute(
        path: Routes.about,
        builder: (context, state) => const AboutScreen(),
      ),
      GoRoute(
        path: Routes.disclaimer,
        builder: (context, state) => const DisclaimerScreen(),
      ),
      GoRoute(
        path: Routes.activities,
        builder: (context, state) => const ActivitiesScreen(),
      ),
      GoRoute(
        path: Routes.exam,
        builder: (context, state) => TakeExamScreen(
          examId: state.pathParameters['examId']!,
          stepId: state.uri.queryParameters['stepId'],
          courseId: state.uri.queryParameters['courseId'],
        ),
      ),
      GoRoute(
        path: Routes.userInfo,
        builder: (context, state) => UserInformationScreen(
          submissionId: state.pathParameters['submissionId']!,
          teamId: state.uri.queryParameters['teamId'],
        ),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) =>
            DashboardShell(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: Routes.home,
                builder: (context, state) => const HomeScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: Routes.resources,
                builder: (context, state) => const ResourcesScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: Routes.courses,
                builder: (context, state) => const CoursesScreen(),
                routes: [
                  GoRoute(
                    path: 'progress',
                    builder: (context, state) => const CoursesProgressScreen(),
                  ),
                  GoRoute(
                    path: ':courseId',
                    builder: (context, state) => CourseDetailScreen(
                      courseId: state.pathParameters['courseId']!,
                    ),
                  ),
                  GoRoute(
                    path: ':courseId/take',
                    builder: (context, state) => TakeCourseScreen(
                      courseId: state.pathParameters['courseId']!,
                    ),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: Routes.calendar,
                builder: (context, state) => const CalendarScreen(),
                routes: [
                  GoRoute(
                    path: 'events',
                    builder: (context, state) => const EventsScreen(),
                    routes: [
                      GoRoute(
                        path: 'new',
                        builder: (context, state) => const NewMeetupScreen(),
                      ),
                      GoRoute(
                        path: ':meetupId',
                        builder: (context, state) => EventDetailScreen(
                          meetupId: state.pathParameters['meetupId']!,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: Routes.profile,
                builder: (context, state) => const ProfileScreen(),
                routes: [
                  GoRoute(
                    path: 'notifications',
                    builder: (context, state) => const NotificationsScreen(),
                  ),
                  GoRoute(
                    path: 'settings',
                    builder: (context, state) => const SettingsScreen(),
                    routes: [
                      GoRoute(
                        path: 'dictionary',
                        builder: (context, state) => const DictionaryScreen(),
                      ),
                      GoRoute(
                        path: 'storage',
                        builder: (context, state) =>
                            const StorageBreakdownScreen(),
                        routes: [
                          GoRoute(
                            path: 'category',
                            builder: (context, state) =>
                                StorageCategoryDetailScreen(
                                  extra: state.extra as StorageCategoryExtra,
                                ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: Routes.life,
                builder: (context, state) => const LifeScreen(),
                routes: [
                  GoRoute(
                    path: 'references',
                    builder: (context, state) => const ReferencesScreen(),
                  ),
                  GoRoute(
                    path: 'personals',
                    builder: (context, state) => const PersonalsScreen(),
                  ),
                  GoRoute(
                    path: 'submissions',
                    builder: (context, state) => const SubmissionsScreen(),
                    routes: [
                      GoRoute(
                        path: ':submissionId',
                        builder: (context, state) => SubmissionDetailScreen(
                          submissionId: state.pathParameters['submissionId']!,
                        ),
                      ),
                    ],
                  ),
                  GoRoute(
                    path: 'surveys',
                    builder: (context, state) => const SurveysScreen(),
                    routes: [
                      GoRoute(
                        path: ':surveyId',
                        builder: (context, state) => TakeSurveyScreen(
                          surveyId: state.pathParameters['surveyId']!,
                          submissionId: state.uri.queryParameters['submission'],
                        ),
                      ),
                    ],
                  ),
                  GoRoute(
                    path: 'voices',
                    builder: (context, state) => const VoicesScreen(),
                    routes: [
                      GoRoute(
                        path: ':newsId',
                        builder: (context, state) => VoiceThreadScreen(
                          newsId: state.pathParameters['newsId']!,
                        ),
                      ),
                    ],
                  ),
                  GoRoute(
                    path: 'teams',
                    builder: (context, state) => const TeamsScreen(),
                    routes: [
                      GoRoute(
                        path: ':teamId',
                        builder: (context, state) => TeamDetailScreen(
                          teamId: state.pathParameters['teamId']!,
                        ),
                        routes: [
                          GoRoute(
                            path: 'tasks',
                            builder: (context, state) => TeamTasksScreen(
                              teamId: state.pathParameters['teamId']!,
                            ),
                          ),
                          GoRoute(
                            path: 'members',
                            builder: (context, state) => TeamMembersScreen(
                              teamId: state.pathParameters['teamId']!,
                              openJoinRequests:
                                  state.uri.queryParameters['tab'] ==
                                  'requests',
                            ),
                            routes: [
                              GoRoute(
                                path: ':userId',
                                builder: (context, state) => MemberDetailScreen(
                                  teamId: state.pathParameters['teamId']!,
                                  userId: state.pathParameters['userId']!,
                                ),
                              ),
                            ],
                          ),
                          GoRoute(
                            path: 'leaderboard',
                            builder: (context, state) => TeamLeaderboardScreen(
                              teamId: state.pathParameters['teamId']!,
                            ),
                          ),
                          GoRoute(
                            path: 'resources',
                            builder: (context, state) => TeamResourcesScreen(
                              teamId: state.pathParameters['teamId']!,
                            ),
                          ),
                          GoRoute(
                            path: 'courses',
                            builder: (context, state) => TeamCoursesScreen(
                              teamId: state.pathParameters['teamId']!,
                            ),
                          ),
                          GoRoute(
                            path: 'reports',
                            builder: (context, state) => TeamReportsScreen(
                              teamId: state.pathParameters['teamId']!,
                            ),
                          ),
                          GoRoute(
                            path: 'surveys',
                            builder: (context, state) => TeamSurveysScreen(
                              teamId: state.pathParameters['teamId']!,
                            ),
                          ),
                          GoRoute(
                            path: 'voices',
                            builder: (context, state) => TeamVoicesScreen(
                              teamId: state.pathParameters['teamId']!,
                            ),
                          ),
                          GoRoute(
                            path: 'plan',
                            builder: (context, state) => TeamPlanScreen(
                              teamId: state.pathParameters['teamId']!,
                            ),
                          ),
                          GoRoute(
                            path: 'finances',
                            builder: (context, state) => TeamFinancesScreen(
                              teamId: state.pathParameters['teamId']!,
                            ),
                          ),
                          GoRoute(
                            path: 'calendar',
                            builder: (context, state) => TeamCalendarScreen(
                              teamId: state.pathParameters['teamId']!,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  GoRoute(
                    path: 'chat',
                    builder: (context, state) => const ChatHistoryScreen(),
                    routes: [
                      GoRoute(
                        path: ':chatId',
                        builder: (context, state) => ChatDetailScreen(
                          chatId: state.pathParameters['chatId'],
                        ),
                      ),
                    ],
                  ),
                  GoRoute(
                    path: 'feedback',
                    builder: (context, state) => const FeedbackListScreen(),
                    routes: [
                      GoRoute(
                        path: ':feedbackId',
                        builder: (context, state) => FeedbackDetailScreen(
                          feedbackId: state.pathParameters['feedbackId']!,
                        ),
                      ),
                      GoRoute(
                        path: 'create',
                        builder: (context, state) => FeedbackCreateScreen(
                          item: state.uri.queryParameters['item'],
                          state: state.uri.queryParameters['state'],
                        ),
                      ),
                    ],
                  ),
                  GoRoute(
                    path: 'achievements',
                    builder: (context, state) => const AchievementsScreen(),
                    routes: [
                      GoRoute(
                        path: 'edit',
                        builder: (context, state) =>
                            const EditAchievementScreen(),
                      ),
                    ],
                  ),
                  GoRoute(
                    path: 'health',
                    builder: (context, state) => const MyHealthScreen(),
                    routes: [
                      GoRoute(
                        path: 'add',
                        // `userId` is the patient the record belongs to,
                        // which the Kotlin passes as an intent extra.
                        builder: (context, state) => AddHealthScreen(
                          userId: state.uri.queryParameters['userId'],
                        ),
                      ),
                      GoRoute(
                        path: 'examination',
                        builder: (context, state) => AddExaminationScreen(
                          examinationId: state.uri.queryParameters['id'],
                          userId: state.uri.queryParameters['userId'],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    ],
  );
});

/// Bridges Riverpod state changes to GoRouter's [Listenable]-based refresh, so
/// saving a server config or signing in re-runs the redirect.
class _RouterRefresh extends ChangeNotifier {
  _RouterRefresh(Ref ref) {
    ref.listen(serverConfigProvider, (_, _) => notifyListeners());
    ref.listen(onboardingProvider, (_, _) => notifyListeners());
    ref.listen(sessionProvider, (_, _) => notifyListeners());
  }
}
