import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/ui/outbox_drain_scope.dart';

/// `OutboxDrainScope` is what replaced `RetryQueueWorker`'s WorkManager
/// registration: it is the only thing that sends a write made offline. It had
/// no test at all — it never appeared in the coverage report, because nothing
/// ever loaded the file.
void main() {
  late _RecordingDrainer drainer;

  const config = ServerConfig(
    serverUrl: 'http://planet.example',
    pin: '1234',
    couchDbUrl: 'http://planet.example:5984',
  );

  Future<void> pump(
    WidgetTester tester, {
    ServerConfig? server = config,
  }) async {
    drainer = _RecordingDrainer();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          outboxDrainerProvider.overrideWithValue(drainer),
          serverConfigProvider.overrideWith(() => _StubConfig(server)),
        ],
        child: const MaterialApp(
          home: OutboxDrainScope(child: SizedBox.shrink()),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('drains once at startup, after clearing stranded rows', (
    tester,
  ) async {
    await pump(tester);

    expect(drainer.recovered, 1);
    expect(drainer.calls, 1);
    // Recovery must precede the drain: a row left `in_progress` by a kill
    // mid-drain is not due until it is returned to `pending`, so draining first
    // would skip exactly the operations that were interrupted.
    expect(drainer.order, ['recoverStuck', 'drain']);
  });

  testWidgets('sends the credential as a header', (tester) async {
    await pump(tester);

    // `endpointFor` deliberately stores a credential-free URL, so without this
    // every send is anonymous and CouchDB's 401 is classified permanent.
    expect(drainer.authHeaders.single, startsWith('Basic '));
  });

  testWidgets('drains again on resume', (tester) async {
    await pump(tester);
    expect(drainer.calls, 1);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();

    expect(
      drainer.calls,
      2,
      reason: 'resume is the closest analogue to the periodic worker',
    );
    expect(drainer.recovered, 1, reason: 'recovery is a startup-only concern');
  });

  testWidgets('keeps the queue when no server is configured', (tester) async {
    await pump(tester, server: null);

    // Nothing to send to before the handshake. The operations keep — they are
    // rows in SQLite, not in-memory state.
    expect(drainer.calls, 0);
  });

  testWidgets('a throwing startup drain is reported, not raised', (
    tester,
  ) async {
    drainer = _RecordingDrainer()..throwOnDrain = true;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          outboxDrainerProvider.overrideWithValue(drainer),
          serverConfigProvider.overrideWith(() => _StubConfig(config)),
        ],
        child: const MaterialApp(home: OutboxDrainScope(child: Text('shell'))),
      ),
    );
    await tester.pumpAndSettle();

    // The drain runs from a post-frame callback, so the failure is routed to
    // FlutterError — visible in logs and crash reporting — instead of becoming
    // an unhandled async exception. The shell still builds either way.
    expect(tester.takeException(), isA<StateError>());
    expect(find.text('shell'), findsOneWidget);
    expect(drainer.calls, 1);
  });

  testWidgets('a throwing resume drain is reported too', (tester) async {
    await pump(tester);
    drainer.throwOnDrain = true;

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();

    // The resume path is fire-and-forget like the startup one, so it needs the
    // same guard; without it, foregrounding the app raises an unhandled error.
    expect(tester.takeException(), isA<StateError>());
    expect(drainer.calls, 2);
  });
}

class _MockPlanetApi extends Mock implements PlanetApi {}

class _MockOutboxRepository extends Mock implements OutboxRepository {}

/// Both inherited members are overridden, so the api and repository handed to
/// `super` are never touched.
class _RecordingDrainer extends OutboxDrainer {
  _RecordingDrainer() : super(_MockPlanetApi(), _MockOutboxRepository());

  int calls = 0;
  int recovered = 0;
  final List<String> order = [];
  final List<String?> authHeaders = [];
  bool throwOnDrain = false;

  @override
  Future<void> recoverStuck() async {
    recovered++;
    order.add('recoverStuck');
  }

  @override
  Future<List<OutboxOutcome>> drain({String? authHeader}) async {
    calls++;
    order.add('drain');
    authHeaders.add(authHeader);
    if (throwOnDrain) throw StateError('network gone');
    return const [];
  }
}

class _StubConfig extends ServerConfigNotifier {
  _StubConfig(this.config);

  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}
