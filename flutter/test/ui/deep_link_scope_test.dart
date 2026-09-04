import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/deep_link_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/deep_link_scope.dart';
import 'package:myplanet/ui/router.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Mounts [DeepLinkScope] where the app mounts it — inside
/// `MaterialApp.router`'s `builder` — because that position is the whole point
/// of the test: the builder's context sits *above* the `InheritedGoRouter`, so
/// `GoRouter.of(context)` throws there and navigation has to go through
/// `routerProvider`.
void main() {
  late PlanetPrefs prefs;
  late _FakeSource source;

  UserRow user() => UserRow(
    id: 'user-1',
    name: 'ada',
    rolesList: const [],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  GoRouter buildRouter() => GoRouter(
    initialLocation: '/home',
    routes: [
      GoRoute(path: '/home', builder: (_, _) => const Text('home')),
      GoRoute(path: Routes.resources, builder: (_, _) => const Text('library')),
      GoRoute(path: Routes.teams, builder: (_, _) => const Text('teams')),
      GoRoute(
        path: Routes.publicSurvey,
        builder: (context, state) =>
            Text('survey ${publicSurveyBaseUrl(state.uri, null)}'),
      ),
    ],
  );

  late _TestSessionNotifier session;

  Future<void> pumpScope(
    WidgetTester tester, {
    UserRow? current,
    GoRouter? router,
  }) async {
    session = _TestSessionNotifier(current);
    final config = router ?? buildRouter();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          deepLinkSourceProvider.overrideWithValue(source),
          routerProvider.overrideWithValue(config),
          sessionProvider.overrideWith(() => session),
        ],
        child: MaterialApp.router(
          routerConfig: config,
          builder: (context, child) =>
              DeepLinkScope(child: child ?? const SizedBox.shrink()),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
    source = _FakeSource();
  });

  tearDown(() => source.dispose());

  testWidgets('the launch link navigates, carrying its origin', (tester) async {
    source.initial = Uri.parse('https://planet.gt/survey/team-1/survey-9');

    await pumpScope(tester);

    // Reaching the screen at all is the fix: the origin is what it fetches from,
    // and a respondent with no configured server had nothing before.
    expect(find.text('survey https://planet.gt'), findsOneWidget);
  });

  testWidgets('a link delivered while running navigates too', (tester) async {
    await pumpScope(tester, current: user());
    expect(find.text('home'), findsOneWidget);

    source.emit(Uri.parse('myplanet://resources'));
    await tester.pumpAndSettle();

    expect(find.text('library'), findsOneWidget);
  });

  testWidgets('a section link before sign-in waits for the session', (
    tester,
  ) async {
    source.initial = Uri.parse('myplanet://teams/team-2');

    await pumpScope(tester);

    // Stored, not navigated: there is no session yet.
    expect(find.text('home'), findsOneWidget);
    expect(prefs.pendingDeepLinkSection, 'teams');

    // Signing in is what applies it — `DashboardActivity` reads the stored
    // section once it has a user. This is the real transition the scope listens
    // for, not a remount.
    session.becomeSignedIn(user());
    await tester.pumpAndSettle();

    expect(find.text('teams'), findsOneWidget);
    expect(prefs.pendingDeepLinkSection, '');
  });

  testWidgets('a failing link source does not take startup down', (
    tester,
  ) async {
    source.throwOnInitial = true;

    await pumpScope(tester);

    // The app is still up on its initial route...
    expect(find.text('home'), findsOneWidget);
    // ...and the failure was reported rather than swallowed, so a broken
    // platform channel is visible instead of silently disabling deep links.
    expect(tester.takeException(), isA<StateError>());
  });
}

class _FakeSource implements DeepLinkSource {
  final _controller = StreamController<Uri>.broadcast();
  Uri? initial;
  bool throwOnInitial = false;

  void emit(Uri uri) => _controller.add(uri);
  void dispose() => _controller.close();

  @override
  Future<Uri?> initialLink() async {
    if (throwOnInitial) throw StateError('no platform channel');
    return initial;
  }

  @override
  Stream<Uri> links() => _controller.stream;
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;

  /// Named to avoid colliding with the real `signIn`, which writes prefs and a
  /// login activity row; the transition is all this test needs.
  void becomeSignedIn(UserRow row) => state = AsyncData(row);

  @override
  Future<UserRow?> build() async => user;
}
