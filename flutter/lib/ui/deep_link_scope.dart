import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/deep_link_provider.dart';
import '../providers/session_provider.dart';
import 'router.dart';

/// Delivers incoming deep links to the router.
///
/// Replaces `OnboardingActivity`'s `onCreate`/`onNewIntent` pair: the launch
/// link and the links that arrive while the app is running are the same two
/// cases, and here they are a future and a stream from the same source. Wrapped
/// around the navigator, like `OutboxDrainScope`, so it follows the app rather
/// than any one screen.
///
/// The pending-section link is applied when the session appears rather than in
/// the dashboard's own `initState`, which is where `DashboardActivity` does it.
/// The behaviour is the same — the link waits for a session and is consumed once
/// — and doing it here keeps every deep-link path in one file.
///
/// Navigation goes through [routerProvider] rather than `GoRouter.of(context)`.
/// This widget is mounted from `MaterialApp.router`'s `builder`, whose context is
/// *above* the `InheritedGoRouter` the router delegate inserts, so the lookup
/// would throw "No GoRouter found in context" on the first link that arrived.
class DeepLinkScope extends ConsumerStatefulWidget {
  const DeepLinkScope({required this.child, super.key});

  final Widget child;

  @override
  ConsumerState<DeepLinkScope> createState() => _DeepLinkScopeState();
}

class _DeepLinkScopeState extends ConsumerState<DeepLinkScope> {
  StreamSubscription<Uri>? _subscription;

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
      // Inside the guard: constructing `AppLinks` reaches for a platform
      // channel, so on a platform without the plugin this throws before any
      // link is even asked for.
      final source = ref.read(deepLinkSourceProvider);
      // Listening before awaiting the initial link: a link arriving in that
      // window would otherwise be dropped, and the plugin does not replay it.
      _subscription = source.links().listen(_handle);
      final initial = await source.initialLink();
      if (initial != null) await _handle(initial);
    } catch (error, stack) {
      // A platform channel failure must not take startup down with it.
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stack,
          library: 'deep links',
        ),
      );
    }
  }

  Future<void> _handle(Uri uri) async {
    if (!mounted) return;
    final location = await ref.read(deepLinkHandlerProvider).handle(uri);
    if (!mounted || location == null) return;
    ref.read(routerProvider).go(location);
  }

  Future<void> _applyPending() async {
    if (!mounted) return;
    final location = await ref
        .read(deepLinkHandlerProvider)
        .takePendingLocation();
    if (!mounted || location == null) return;
    ref.read(routerProvider).go(location);
  }

  @override
  Widget build(BuildContext context) {
    // A link that arrived before sign-in was persisted; this is the moment the
    // Kotlin's `DashboardActivity` reads it back.
    ref.listen(sessionProvider, (previous, next) {
      final wasSignedIn = previous?.valueOrNull != null;
      if (!wasSignedIn && next.valueOrNull != null) {
        unawaited(_applyPending());
      }
    });
    return widget.child;
  }
}
