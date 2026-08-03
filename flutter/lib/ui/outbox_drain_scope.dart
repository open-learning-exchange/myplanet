import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/app_providers.dart';

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
    if (state == AppLifecycleState.resumed) _drain();
  }

  Future<void> _startupDrain() async {
    if (!mounted) return;
    await ref.read(outboxDrainerProvider).recoverStuck();
    await _drain();
  }

  Future<void> _drain() async {
    if (!mounted) return;
    // Nothing to send to before the server handshake; the operations keep.
    if (ref.read(serverConfigProvider) == null) return;
    await ref.read(outboxDrainerProvider).drain();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
