import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/ui/feedback/feedback_create_screen.dart';
import 'package:myplanet/ui/router.dart';
import 'package:myplanet/ui/sync/login_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/widget_harness.dart';

/// Reachability, asked of the **redirect** rather than of the route table.
///
/// `route_reachability_test.dart` compares the table with the navigations, and
/// every question it asks goes through `findMatch`, which is pure matching.
/// It never runs [GoRouter]'s `redirect`. So a route can have a correct
/// pattern, an unshadowed declaration, a real `context.push`, a working screen
/// and green screen tests — and still be unreachable, because the redirect
/// sends the user somewhere else.
///
/// That is not hypothetical twice over. `/server` spent three phases with
/// three tested, green code paths dead behind it, reachable only by clearing
/// the persisted configuration. And `/become-member` was dead when this file
/// was written: `login_screen.dart` has the button Kotlin has
/// (`LoginActivity.kt:678`), but the signed-out branch of the redirect sent
/// every location that was not `/login` back to `/login`, so the tap flashed
/// and stayed put. Nothing could see it — `guest_dialog.dart` also pushes
/// `/become-member`, with a session, so the route *is* reached and the
/// reachability guard was satisfied.
///
/// The question here is the strong one, and it is asked of the real router:
/// **from this gate state, does this location end up at the route it names?**
/// `RouteConfiguration.redirect` answers it without building a single screen,
/// which matters because these locations' screens want a populated database.
/// Cross-checked against a fully pumped `MaterialApp.router` on the cases
/// where both can be driven: the two agree, including on the `?change=1`
/// marker that `/server` depends on.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('signed out, with a server configured', () {
    // The login screen's own state. Everything a user can do before signing in
    // has to survive this gate.

    testWidgets('the locations a signed-out user must reach are not gated', (
      tester,
    ) async {
      final harness = await _RedirectHarness.create(tester, signedIn: false);

      for (final entry in const {
        // Kotlin: `activity_login.xml:274`/`LoginActivity.kt:200-207`.
        Routes.login: '/login',
        // Kotlin: `activity_login.xml:257`/`LoginActivity.kt:678`.
        Routes.becomeMember: '/become-member',
        // Kotlin: `activity_login.xml:274`/`LoginActivity.kt:200-202`, the
        // front door's route to an administrator for a user who cannot get
        // through it. `/life/feedback/create`, the other way in, is inside
        // the dashboard shell and gated behind exactly this branch.
        Routes.loginFeedback: '/feedback',
        // The server switch, whose marker is the narrowest exemption in the
        // redirect and the one most likely to be broken by tidying it.
        Routes.changeServer: '/server',
        // A public survey is answerable with no session at all.
        '/survey/team-1/survey-1': '/survey/:teamId/:surveyId',
      }.entries) {
        expect(
          await harness.lands(entry.key),
          entry.value,
          reason:
              '${entry.key} is gated away from ${entry.value} for a signed-out '
              'user, so whatever offers it does nothing when tapped',
        );
      }
    });

    testWidgets('the gate still closes on everything else', (tester) async {
      // Without this the test above passes by making the gate useless, which
      // is the failure mode the round's own notes call "a fixture that cannot
      // distinguish". These two assertions have to disagree.
      final harness = await _RedirectHarness.create(tester, signedIn: false);

      for (final location in const [
        Routes.home,
        Routes.community,
        '/life/teams/team-1/tasks',
      ]) {
        expect(
          await harness.lands(location),
          '/login',
          reason: '$location is reachable without signing in',
        );
      }
    });
  });

  group('the login screen, driven for real', () {
    // The redirect assertions above cannot see a button that does nothing, and
    // that is half of what "unreachable" has meant in this port. `/become-member`
    // had a live button and a dead gate; a no-op button with a live gate looks
    // identical to a user. These two tap the real widget inside the real router
    // and read the location it arrives at, so either failure shows up here —
    // and they say which, because the location distinguishes them: a dead gate
    // lands back on `/login`, a dead button never leaves it in the first place
    // and the login form is still on screen.

    testWidgets('the feedback button arrives at the feedback form', (
      tester,
    ) async {
      final harness = await _RouterHarness.create(tester);
      expect(harness.location, '/login');
      expect(find.byType(LoginScreen), findsOneWidget);

      await tester.tap(find.widgetWithText(TextButton, 'Feedback'));
      await tester.pumpAndSettle();

      expect(
        harness.location,
        Routes.loginFeedback,
        reason:
            'The login screen offers feedback and the tap does not arrive. If '
            'the login form is still on screen the button is a no-op; if this '
            'reads /login the redirect ate it, so add Routes.loginFeedback to '
            'signedOutLocations.',
      );
      expect(find.byType(FeedbackCreateScreen), findsOneWidget);
    });

    testWidgets('the become-member button still arrives', (tester) async {
      // Phase 157's fix, re-asked through the widget rather than through
      // `redirect` alone — the same button, the same gate, and the pair of
      // them is what the port actually ships.
      final harness = await _RouterHarness.create(tester);

      await tester.tap(find.widgetWithText(TextButton, 'Become a member'));
      await tester.pumpAndSettle();

      expect(harness.location, Routes.becomeMember);
    });
  });

  group('signed in', () {
    testWidgets('every registered route survives the redirect', (tester) async {
      // The general sweep, and the one that will catch the *next* instance:
      // every pattern in the table, driven through the real redirect, has to
      // arrive at itself.
      //
      // The three exceptions are the gates themselves, and they are asserted
      // rather than skipped — a redirect that stopped sending a signed-in user
      // away from `/login` would be a real change and should fail here.
      const gates = <String, String>{
        '/login': '/home',
        '/server': '/home',
        '/onboarding': '/home',
      };

      final harness = await _RedirectHarness.create(tester, signedIn: true);
      final wrong = <String>[];
      for (final path in harness.registeredPaths()) {
        final expected = gates[path] ?? path;
        final landed = await harness.lands(
          path.replaceAll(RegExp(r':[A-Za-z_]\w*'), 'x'),
        );
        if (landed != expected) wrong.add('$path -> ${landed ?? 'no route'}');
      }

      expect(
        wrong,
        isEmpty,
        reason:
            'These routes do not survive the redirect for a signed-in user, so '
            'the screens behind them cannot be opened however correct the '
            'route and the screen are:\n${wrong.map((e) => '  $e').join('\n')}',
      );
    });
  });
}

class _Server extends ServerConfigNotifier {
  @override
  ServerConfig? build() => const ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: 'pin',
    couchDbUrl: 'https://satellite:pin@planet.example.org',
  );
}

class _Onboarded extends OnboardingNotifier {
  @override
  bool build() => true;
}

class _Session extends SessionNotifier {
  _Session(this.user);
  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}

/// Drives the real `routerProvider` from a chosen gate state.
class _RedirectHarness {
  _RedirectHarness(this._router, this._context);

  final GoRouter _router;
  final BuildContext _context;

  static Future<_RedirectHarness> create(
    WidgetTester tester, {
    required bool signedIn,
  }) async {
    SharedPreferences.setMockInitialValues({'onboardingComplete': true});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    final database = AppDatabase.memory();
    addTearDown(database.close);

    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        planetPrefsProvider.overrideWithValue(prefs),
        appDatabaseProvider.overrideWithValue(database),
        // The gates are overridden rather than driven through prefs because
        // `serverConfig` reads two values out of secure storage, which a test
        // cannot set without a platform channel.
        serverConfigProvider.overrideWith(_Server.new),
        onboardingProvider.overrideWith(_Onboarded.new),
        sessionProvider.overrideWith(
          () => _Session(signedIn ? buildUserRow(id: 'user-1') : null),
        ),
      ],
    );
    addTearDown(container.dispose);

    // The redirect holds position while the session is loading, so resolving it
    // first is what makes this a test of the signed-in/out branches rather than
    // of the loading one.
    await container.read(sessionProvider.future);

    late BuildContext context;
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          home: Builder(
            builder: (innerContext) {
              context = innerContext;
              return const SizedBox();
            },
          ),
        ),
      ),
    );

    return _RedirectHarness(container.read(routerProvider), context);
  }

  /// The pattern of the route [location] actually arrives at, redirects
  /// applied, or null when none serves it.
  Future<String?> lands(String location) async {
    final result = await _router.configuration.redirect(
      _context,
      _router.configuration.findMatch(Uri.parse(location)),
      redirectHistory: <RouteMatchList>[],
    );
    return result.isError ? null : result.fullPath;
  }

  List<String> registeredPaths() {
    final paths = <String>[];
    void walk(List<RouteBase> routes, String parent) {
      for (final route in routes) {
        var full = parent;
        if (route is GoRoute) {
          full = route.path.startsWith('/')
              ? route.path
              : '$parent/${route.path}'.replaceAll('//', '/');
          paths.add(full);
        }
        walk(route.routes, full);
      }
    }

    walk(_router.configuration.routes, '');
    return paths;
  }
}

/// Pumps the real router, signed out, and reports where it is.
///
/// [_RedirectHarness] asks `redirect` a question directly; this one asks the
/// application. It is the more expensive of the two and only earns that on the
/// login screen, whose widgets build without a populated database — the reason
/// the other harness exists at all.
class _RouterHarness {
  _RouterHarness(this._router);

  final GoRouter _router;

  static Future<_RouterHarness> create(WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({'onboardingComplete': true});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    final database = AppDatabase.memory();
    addTearDown(database.close);

    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        planetPrefsProvider.overrideWithValue(prefs),
        appDatabaseProvider.overrideWithValue(database),
        serverConfigProvider.overrideWith(_Server.new),
        onboardingProvider.overrideWith(_Onboarded.new),
        sessionProvider.overrideWith(() => _Session(null)),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);

    final router = container.read(routerProvider);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(
          localizationsDelegates: const <LocalizationsDelegate<Object?>>[
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
          routerConfig: router,
        ),
      ),
    );
    await tester.pumpAndSettle();

    return _RouterHarness(router);
  }

  /// Where the router has actually put the user.
  ///
  /// **Not `currentConfiguration.uri`, and the difference is the whole test.**
  /// `context.push` does not replace the match list: it appends an
  /// [ImperativeRouteMatch] wrapping the pushed one
  /// (`go_router-18.0.1/lib/src/match.dart:431-451`), so the list's own `uri`
  /// stays the *base* location — `/login`, for every push made from the login
  /// screen. Read that way, a button that works and a button that does nothing
  /// report the identical string, and both of these tests passed the assertion
  /// they were written to make while proving nothing. That is the fixture that
  /// cannot distinguish, found by mutation: no-oping the button changed no
  /// output.
  ///
  /// `last.matchedLocation` is the deepest match, which for a push is the
  /// pushed route and otherwise is the location itself. A dead *gate* also
  /// reads `/login` here — the redirect resolves the push to it — so the two
  /// failures are told apart by whether the login form is still on screen,
  /// which is why these tests assert the widget as well as the string.
  String get location =>
      _router.routerDelegate.currentConfiguration.last.matchedLocation;
}
