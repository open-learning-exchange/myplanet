import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/notifications/notification_tap.dart';
import '../providers/notification_tap_provider.dart';
import 'router.dart';

/// Delivers taps on system notifications to the router.
///
/// Replaces `DashboardActivity`'s `onCreate`/`onNewIntent` pair
/// (`:188` and `:1106`, both calling `handleNotificationIntent`): the tap that
/// launched the app and a tap that arrives while it is running are the same two
/// cases, and here they are a future and a stream from one source. Wrapped
/// around the navigator, like [DeepLinkScope] and `OutboxDrainScope`, so it
/// follows the app rather than any one screen — and unlike the Kotlin, which
/// hangs the whole slice off `initializeDashboard()` and therefore ignores a
/// notification tap entirely for an inactive user (`:301-306` returns before
/// `:188`).
///
/// Navigation goes through [routerProvider] rather than `GoRouter.of(context)`
/// for the reason [DeepLinkScope] documents: this widget is mounted from
/// `MaterialApp.router`'s `builder`, whose context sits *above* the
/// `InheritedGoRouter` the router delegate inserts.
///
/// There is no de-duplication between [NotificationTapSource.launchTap] and
/// the stream, and the reason is **not** the one `initialize`'s dartdoc
/// suggests. The Android side does not honour that split as a guarantee:
/// `onNewIntent` both invokes `didReceiveNotificationResponse` *and* calls
/// `mainActivity.setIntent(intent)`, and `getNotificationAppLaunchDetails`
/// re-reads that intent on every call — so a tap already delivered through the
/// callback keeps being reported as a launch tap for the rest of the process.
///
/// What makes this safe is narrower: [_start] runs **once**, from `initState`'s
/// post-frame callback, so `launchTap` is asked exactly once per mount. That is
/// a live trap rather than a theoretical one, because the sibling scope in this
/// directory does the thing that would break it — `OutboxDrainScope` re-runs on
/// `AppLifecycleState.resumed`. Adding a resume hook here (the obvious fix for
/// "the app was backgrounded when the tap arrived") would replay the last tap
/// on every foreground: yanking the user back to the tasks page and re-running
/// the read-mark, `createdAt` restamp included. **If a resume hook ever lands,
/// de-duplicate on the payload first.**
class NotificationTapScope extends ConsumerStatefulWidget {
  const NotificationTapScope({required this.child, super.key});

  final Widget child;

  @override
  ConsumerState<NotificationTapScope> createState() =>
      _NotificationTapScopeState();
}

class _NotificationTapScopeState extends ConsumerState<NotificationTapScope> {
  StreamSubscription<NotificationTap>? _subscription;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _start());
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  Future<void> _start() async {
    if (!mounted) return;
    try {
      // Inside the guard: the production source constructs the plugin, which
      // reaches for a platform channel.
      final source = ref.read(notificationTapSourceProvider);
      // Listening before awaiting the launch tap, as `DeepLinkScope` does: a
      // tap arriving in that window would otherwise be dropped, and a
      // broadcast stream does not replay it.
      _subscription = source.taps().listen(
        _handle,
        // `_handle` is async, so an exception inside it would become an
        // unhandled async error rather than reaching here — which is why
        // `NotificationTapHandler.handle` guards both of its halves itself.
        // This covers the other side: an error the *source* emits.
        onError: (Object error, StackTrace stack) => FlutterError.reportError(
          FlutterErrorDetails(
            exception: error,
            stack: stack,
            library: 'notification taps',
          ),
        ),
      );
      final launch = await source.launchTap();
      if (launch != null) await _handle(launch);
    } catch (error, stack) {
      // A platform channel failure must not take startup down with it.
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stack,
          library: 'notification taps',
        ),
      );
    }
  }

  Future<void> _handle(NotificationTap tap) async {
    if (!mounted) return;
    final location = await ref.read(notificationTapHandlerProvider).handle(tap);
    if (!mounted || location == null) return;
    ref.read(routerProvider).go(location);
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
