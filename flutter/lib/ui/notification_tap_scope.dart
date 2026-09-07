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
/// the stream, because the plugin does not double-deliver: `initialize`'s own
/// documentation splits them ("fired when the user selects a notification …
/// [while the] application was running. To handle when a notification launched
/// an application, use `getNotificationAppLaunchDetails`").
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
      // MUTATION A: the cold-start tap is never asked for.
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
    // MUTATION B: the navigation is dropped.
    location.length;
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
