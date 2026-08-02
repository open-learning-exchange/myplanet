import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../providers/app_providers.dart';
import '../providers/session_provider.dart';
import 'calendar/calendar_screen.dart';
import 'courses/course_detail_screen.dart';
import 'courses/courses_screen.dart';
import 'dashboard/dashboard_shell.dart';
import 'resources/resources_screen.dart';
import 'sync/login_screen.dart';
import 'sync/server_config_screen.dart';

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

  static const String server = '/server';
  static const String login = '/login';
  static const String resources = '/resources';
  static const String courses = '/courses';
  static const String calendar = '/calendar';
}

final _rootNavigatorKey = GlobalKey<NavigatorState>();

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: Routes.resources,
    refreshListenable: _RouterRefresh(ref),
    redirect: (context, state) {
      final hasServer = ref.read(serverConfigProvider) != null;
      final session = ref.read(sessionProvider);

      // Hold position until the persisted session has been read back.
      if (session.isLoading) return null;

      final isSignedIn = session.valueOrNull != null;
      final location = state.matchedLocation;

      if (!hasServer) {
        return location == Routes.server ? null : Routes.server;
      }
      if (!isSignedIn) {
        return location == Routes.login ? null : Routes.login;
      }
      if (location == Routes.server || location == Routes.login) {
        return Routes.resources;
      }
      return null;
    },
    routes: [
      GoRoute(
        path: Routes.server,
        builder: (context, state) => const ServerConfigScreen(),
      ),
      GoRoute(
        path: Routes.login,
        builder: (context, state) => const LoginScreen(),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) =>
            DashboardShell(navigationShell: navigationShell),
        branches: [
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
                    path: ':courseId',
                    builder: (context, state) => CourseDetailScreen(
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
    ref.listen(sessionProvider, (_, _) => notifyListeners());
  }
}
