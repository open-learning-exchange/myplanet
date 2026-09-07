import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/notifications/notification_tap.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/providers/notification_tap_provider.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/ui/notification_tap_scope.dart';
import 'package:myplanet/ui/router.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Mounts [NotificationTapScope] where the app mounts it — inside
/// `MaterialApp.router`'s `builder` — for the same reason
/// `deep_link_scope_test.dart` does: that context sits *above* the
/// `InheritedGoRouter`, so `GoRouter.of(context)` would throw there and
/// navigation has to go through `routerProvider`.
///
/// **This file exists because two mutations to the scope passed the whole
/// suite.** A background mutation-testing pass injected them — the cold-start
/// tap never asked for, and the resolved location computed and thrown away —
/// and all 2301 tests stayed green, because nothing mounted this widget. Every
/// other link in the tray chain had a guard; the two lines that connect them to
/// the app did not. That is the phase's own subject arriving one layer up: a
/// correct handler nothing can reach.
void main() {
  late PlanetPrefs prefs;
  late _FakeSource source;
  late _FakeHandler handler;

  GoRouter buildRouter() => GoRouter(
    initialLocation: '/home',
    routes: [
      GoRoute(path: '/home', builder: (_, _) => const Text('home')),
      GoRoute(
        path: '${Routes.teams}/:teamId/tasks',
        builder: (_, _) => const Text('tasks'),
      ),
    ],
  );

  Future<void> pumpScope(WidgetTester tester, {GoRouter? router}) async {
    final config = router ?? buildRouter();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          notificationTapSourceProvider.overrideWithValue(source),
          notificationTapHandlerProvider.overrideWithValue(handler),
          routerProvider.overrideWithValue(config),
        ],
        child: MaterialApp.router(
          routerConfig: config,
          builder: (context, child) =>
              NotificationTapScope(child: child ?? const SizedBox.shrink()),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  const tap = NotificationTap(
    payload: NotificationTapPayload(
      type: 'task',
      notificationId: 'task-42',
      relatedId: 'task-42',
    ),
  );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
    source = _FakeSource();
    handler = _FakeHandler('/life/teams/team-9/tasks');
  });
  tearDown(() => source.dispose());

  testWidgets('the tap that launched the app navigates', (tester) async {
    // `DashboardActivity.onCreate` → `handleNotificationIntent(intent)`, which
    // the plugin exposes as `getNotificationAppLaunchDetails` rather than
    // through the response callback. Kills the "never asked for" mutation.
    source.launch = tap;

    await pumpScope(tester);

    expect(handler.handled, [tap]);
    expect(find.text('tasks'), findsOneWidget);
  });

  testWidgets('a tap while the app is running navigates', (tester) async {
    // Kotlin's `onNewIntent` (`DashboardActivity.kt:1106`), which is the
    // response callback here. Nothing is subscribed until the post-frame
    // callback runs, so this also pins that the subscription happens at all.
    await pumpScope(tester);
    expect(find.text('home'), findsOneWidget);

    source.emit(tap);
    await tester.pumpAndSettle();

    expect(handler.handled, [tap]);
    expect(find.text('tasks'), findsOneWidget);
  });

  testWidgets('the subscription is taken before the launch tap is awaited', (
    tester,
  ) async {
    // The ordering `DeepLinkScope` documents and this copies: a tap arriving
    // between the two would otherwise be dropped, and a broadcast stream does
    // not replay it. Driven by holding the launch future open and emitting
    // while it is pending.
    source.holdLaunch = true;

    await pumpScope(tester);
    source.emit(tap);
    await tester.pumpAndSettle();

    expect(
      handler.handled,
      [tap],
      reason: 'a tap arriving while launchTap() was still pending was lost',
    );

    source.releaseLaunch(null);
    await tester.pumpAndSettle();
  });

  testWidgets('a handler that resolves nowhere leaves the app where it is', (
    tester,
  ) async {
    // *Mark as Read* is this case: it marks read and returns null, and the
    // Kotlin's `ACTION_MARK_AS_READ` arm has no navigation either.
    handler = _FakeHandler(null);
    source.launch = tap;

    await pumpScope(tester);

    expect(handler.handled, [tap]);
    expect(find.text('home'), findsOneWidget);
  });

  testWidgets('a source with no platform channel does not break startup', (
    tester,
  ) async {
    // Constructing the production source reaches for a method channel, so this
    // is the ordinary case on any platform without the plugin. The Kotlin has
    // no equivalent — an Android intent always arrives — so the guard is the
    // port's own, and it must not take the first frame down with it.
    source.throwOnLaunch = true;

    await pumpScope(tester);

    // Reported, not swallowed — `FlutterError.reportError`, which the test
    // binding records and `takeException` consumes. Both halves matter: a
    // silent failure here is a tray tap that stops working with no trace.
    expect(tester.takeException(), isA<StateError>());
    expect(find.text('home'), findsOneWidget);
    expect(handler.handled, isEmpty);
  });

  testWidgets('an error the source emits is not an unhandled async error', (
    tester,
  ) async {
    // `_handle` is async, so an exception inside it never reaches the
    // subscription's `onError` — which is why the handler guards both of its
    // own halves. This covers the other side.
    await pumpScope(tester);

    source.emitError(StateError('channel died'));
    await tester.pumpAndSettle();

    // Without the `onError` it would be an unhandled async error rather than a
    // reported one, and the subscription would be torn down with it — so no
    // later tap would arrive either.
    expect(tester.takeException(), isA<StateError>());
    expect(find.text('home'), findsOneWidget);

    // The subscription survived, which is the half the report alone does not
    // prove.
    source.emit(tap);
    await tester.pumpAndSettle();
    expect(handler.handled, [tap]);
    expect(find.text('tasks'), findsOneWidget);
  });
}

class _FakeSource implements NotificationTapSource {
  final _controller = StreamController<NotificationTap>.broadcast();
  NotificationTap? launch;
  bool throwOnLaunch = false;

  /// Holds [launchTap] pending so a stream tap can be delivered while it is
  /// still in flight.
  bool holdLaunch = false;
  final _held = Completer<NotificationTap?>();

  void emit(NotificationTap tap) => _controller.add(tap);
  void emitError(Object error) => _controller.addError(error);
  void releaseLaunch(NotificationTap? tap) {
    if (!_held.isCompleted) _held.complete(tap);
  }

  void dispose() => _controller.close();

  @override
  Future<NotificationTap?> launchTap() {
    if (throwOnLaunch) throw StateError('no platform channel');
    if (holdLaunch) return _held.future;
    return Future.value(launch);
  }

  @override
  Stream<NotificationTap> taps() => _controller.stream;
}

class _FakeHandler extends NotificationTapHandler {
  _FakeHandler(this.location) : super(_unusedRef);

  final String? location;
  final List<NotificationTap> handled = [];

  @override
  Future<String?> handle(NotificationTap tap) async {
    handled.add(tap);
    return location;
  }
}

/// The superclass takes a `Ref` it never uses once `handle` is overridden.
final Ref _unusedRef = _NoRef();

class _NoRef implements Ref {
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnsupportedError('the fake handler reads no providers');
}
