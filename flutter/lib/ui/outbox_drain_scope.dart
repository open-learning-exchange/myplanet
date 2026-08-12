import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/app_providers.dart';
import '../repository/personals_uploader.dart';

/// Replaces the `WorkManager` registration `MainApplication.kt` performs for
/// `RetryQueueWorker`.
///
/// WorkManager can wake a process that is not running; Flutter has no
/// first-party equivalent, so the trigger moves into the app's own lifecycle.
/// Wrapping the router in this widget drains the outbox:
///
/// - once at startup, after clearing rows stranded `in_progress` by a kill
///   mid-drain (`recoverStuckOperations`), and
/// - on every resume, which is the closest honest analogue to the periodic
///   worker.
///
/// What this does **not** do is send while the app is closed. That is the real
/// residual gap, and it is survivable rather than silent: the queue is a SQLite
/// table, so a write made offline persists across process death and goes out on
/// the next launch. `docs/kotlin-to-flutter-migration.md` records it.
class OutboxDrainScope extends ConsumerStatefulWidget {
  const OutboxDrainScope({required this.child, super.key});

  final Widget child;

  @override
  ConsumerState<OutboxDrainScope> createState() => _OutboxDrainScopeState();
}

class _OutboxDrainScopeState extends ConsumerState<OutboxDrainScope>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => _startupDrain());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Nothing awaits this, so it carries the same guard as the startup path:
    // an escaping error here would be an unhandled async exception raised by
    // the act of bringing the app to the foreground.
    if (state == AppLifecycleState.resumed) unawaited(_reportingDrain());
  }

  Future<void> _startupDrain() async {
    if (!mounted) return;
    try {
      await ref.read(outboxDrainerProvider).recoverStuck();
    } catch (error, stack) {
      _report(error, stack);
      return;
    }
    await _reportingDrain();
  }

  /// A drain whose failure is reported rather than thrown.
  ///
  /// Both callers are fire-and-forget — a post-frame callback and a lifecycle
  /// notification — so a raised error would surface as an unhandled async
  /// exception instead of a failed upload. Per-operation outcomes are already
  /// recorded in the outbox table; this only catches a drain that fell over
  /// before it could record anything.
  Future<void> _reportingDrain() async {
    try {
      await _drain();
    } catch (error, stack) {
      _report(error, stack);
    }
  }

  void _report(Object error, StackTrace stack) {
    FlutterError.reportError(
      FlutterErrorDetails(exception: error, stack: stack, library: 'outbox'),
    );
  }

  Future<void> _drain() async {
    if (!mounted) return;
    // Nothing to send to before the server handshake; the operations keep.
    final config = ref.read(serverConfigProvider);
    if (config == null) return;
    // The credential must travel as a header: `endpointFor` deliberately
    // stores a credential-free URL, so without this every send is
    // unauthenticated and CouchDB's 401 is classified as permanent.
    await ref
        .read(outboxDrainerProvider)
        .drain(authHeader: PersonalsUploader.authHeaderFor(config));
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
