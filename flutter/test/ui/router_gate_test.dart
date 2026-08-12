import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/ui/router.dart';

/// The gate that decides whether a navigation is allowed to land.
///
/// It replaces the per-activity `SharedPreferences` checks the Kotlin spreads
/// across `SyncActivity`, `LoginActivity` and `DashboardActivity`, so it is the
/// only thing standing between a signed-out launch and the dashboard — and it
/// had 3 of 208 lines covered before these tests.
void main() {
  String? gate({
    String location = '/home',
    List<String>? pathSegments,
    bool hasServer = true,
    bool onboardingComplete = true,
    bool sessionRestoring = false,
    bool isSignedIn = true,
  }) => gateRedirect(
    location: location,
    pathSegments:
        pathSegments ??
        location.split('/').where((segment) => segment.isNotEmpty).toList(),
    hasServer: hasServer,
    onboardingComplete: onboardingComplete,
    sessionRestoring: sessionRestoring,
    isSignedIn: isSignedIn,
  );

  group('first launch', () {
    test('anything before onboarding is sent to onboarding', () {
      for (final location in ['/home', '/resources', '/login', '/server']) {
        expect(
          gate(location: location, onboardingComplete: false),
          '/onboarding',
          reason: location,
        );
      }
    });

    test('onboarding itself is left alone', () {
      expect(gate(location: '/onboarding', onboardingComplete: false), isNull);
    });

    test('onboarding is gated before the session is known', () {
      // The onboarding gate deliberately runs ahead of session restoration so a
      // fresh install does not flash a signed-in screen.
      expect(
        gate(
          location: '/home',
          onboardingComplete: false,
          sessionRestoring: true,
          isSignedIn: false,
        ),
        '/onboarding',
      );
    });

    test(
      'finishing onboarding lands on server config, or login if configured',
      () {
        expect(gate(location: '/onboarding', hasServer: false), '/server');
        expect(gate(location: '/onboarding'), '/login');
      },
    );
  });

  group('session restoration', () {
    test('holds position rather than bouncing to login', () {
      // Redirecting here would flash the login screen on every cold start.
      expect(
        gate(location: '/home', sessionRestoring: true, isSignedIn: false),
        isNull,
      );
    });
  });

  group('gating', () {
    test('no server configured sends everything to server config', () {
      for (final location in ['/home', '/resources', '/login']) {
        expect(
          gate(location: location, hasServer: false),
          '/server',
          reason: location,
        );
      }
      expect(gate(location: '/server', hasServer: false), isNull);
    });

    test('server but no session sends everything to login', () {
      for (final location in ['/home', '/resources', '/life/teams']) {
        expect(
          gate(location: location, isSignedIn: false),
          '/login',
          reason: location,
        );
      }
      expect(gate(location: '/login', isSignedIn: false), isNull);
    });

    test('a signed-in user cannot sit on login or server config', () {
      expect(gate(location: '/login'), '/home');
      expect(gate(location: '/server'), '/home');
    });

    test('a signed-in user reaches an ordinary destination', () {
      for (final location in [
        '/home',
        '/resources',
        '/courses',
        '/life/teams',
      ]) {
        expect(gate(location: location), isNull, reason: location);
      }
    });
  });

  group('public survey deep link', () {
    test('bypasses every gate, including no session and no server', () {
      expect(
        gate(
          location: '/survey/team-1/survey-1',
          hasServer: false,
          onboardingComplete: false,
          isSignedIn: false,
        ),
        isNull,
      );
    });

    test('only the first segment counts, so /life/surveys stays gated', () {
      // A respondent answering a shared survey is anonymous by design; the
      // in-app surveys list is not, and must not inherit the exemption.
      expect(gate(location: '/life/surveys', isSignedIn: false), '/login');
    });
  });
}
