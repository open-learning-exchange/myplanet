import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/feedback_uploader.dart';
import 'package:myplanet/ui/feedback/feedback_create_screen.dart';
import 'package:myplanet/ui/feedback/feedback_detail_screen.dart';

/// These tests exist because the feedback screens each looked complete on
/// their own while none of them reached the uploader. The create screen called
/// the repository directly, so `queuePending()` — the only code that hands
/// feedback to the outbox — was never called from the running app; replies and
/// closures marked their row un-uploaded with nothing collecting it.
///
/// So the assertion here is deliberately not "the row was saved". It is "the
/// outbox has an entry", which is the part that was missing and the part
/// `flutter analyze` cannot notice.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  final user = UserRow(
    id: 'user-1',
    name: 'ada',
    rolesList: const ['manager'],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  Future<List<OutboxRow>> queued(AppDatabase database) =>
      database.outboxDao.due(DateTime.now().millisecondsSinceEpoch + 1000);

  testWidgets('filing feedback hands the row to the outbox', (tester) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    await tester.pumpWidget(
      _wrap(database, user, config, const FeedbackCreateScreen()),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Bug'));
    await tester.pump();
    await tester.enterText(find.byType(TextField), 'Sync fails offline');
    await tester.tap(find.text('Submit'));
    await tester.pumpAndSettle();

    final pending = await database.feedbackDao.getPending();
    expect(pending, hasLength(1));

    final entries = await queued(database);
    expect(entries.map((row) => row.uploadType), [FeedbackUploader.type]);
    expect(entries.single.itemId, pending.single.id);
    // The PIN travels as a header at send time; the persisted endpoint must not
    // carry it.
    expect(entries.single.endpoint, isNot(contains('1234')));
  });

  testWidgets('a reply on an uploaded thread re-enters the outbox', (
    tester,
  ) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);
    await _seedUploaded(database);

    await tester.pumpWidget(
      _wrap(
        database,
        user,
        config,
        const FeedbackDetailScreen(feedbackId: 'feedback-1'),
      ),
    );
    await tester.pumpAndSettle();

    // Nothing to send yet: the seeded row already reached the server.
    expect(await queued(database), isEmpty);

    await tester.enterText(find.byType(TextField), 'Still broken');
    await tester.tap(find.byIcon(Icons.send));
    await tester.pumpAndSettle();

    expect(
      await database.outboxDao.findOpen(FeedbackUploader.type, 'feedback-1'),
      isNotNull,
    );
  });

  testWidgets('closing a thread queues the status change', (tester) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);
    await _seedUploaded(database);

    await tester.pumpWidget(
      _wrap(
        database,
        user,
        config,
        const FeedbackDetailScreen(feedbackId: 'feedback-1'),
      ),
    );
    await tester.pumpAndSettle();

    // The close control is a manager-only affordance, hence the manager role
    // on the session above.
    await tester.tap(find.byIcon(Icons.check));
    await tester.pumpAndSettle();

    expect(
      (await database.feedbackDao.getById('feedback-1'))?.status,
      'Closed',
    );
    expect(
      await database.outboxDao.findOpen(FeedbackUploader.type, 'feedback-1'),
      isNotNull,
    );
  });

  testWidgets('feedback is still saved when no server is configured', (
    tester,
  ) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    // A user who has not finished server setup should not lose what they typed;
    // the row waits on disk for a later queue attempt.
    await tester.pumpWidget(
      _wrap(database, user, null, const FeedbackCreateScreen()),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Bug'));
    await tester.pump();
    await tester.enterText(find.byType(TextField), 'Sync fails offline');
    await tester.tap(find.text('Submit'));
    await tester.pumpAndSettle();

    expect(await database.feedbackDao.getPending(), hasLength(1));
    expect(await queued(database), isEmpty);
  });
}

Future<void> _seedUploaded(AppDatabase database) {
  return database.feedbackDao.upsert(
    FeedbackEntriesCompanion.insert(
      id: 'feedback-1',
      rev: const Value('1-a'),
      title: const Value('Sync fails offline'),
      owner: const Value('ada'),
      status: const Value('Open'),
      messages: Value(
        '[{"message":"Sync fails offline","time":"0","user":"ada"}]',
      ),
      isUploaded: const Value(true),
    ),
  );
}

/// The create screen pops itself on success, so these screens need a real
/// router rather than a bare `home:` — `context.pop()` looks up a `GoRouter`.
Widget _wrap(
  AppDatabase database,
  UserRow user,
  ServerConfig? config,
  Widget child,
) {
  final router = GoRouter(
    initialLocation: '/list/screen',
    routes: [
      GoRoute(
        path: '/list',
        builder: (_, _) => const Scaffold(body: Text('list')),
        routes: [GoRoute(path: 'screen', builder: (_, _) => child)],
      ),
    ],
  );
  return ProviderScope(
    overrides: [
      appDatabaseProvider.overrideWith((ref) => database),
      sessionProvider.overrideWith(() => _TestSession(user)),
      serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
    ],
    child: MaterialApp.router(
      routerConfig: router,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
    ),
  );
}

class _TestSession extends SessionNotifier {
  _TestSession(this.user);

  final UserRow user;

  @override
  Future<UserRow?> build() async => user;
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);

  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}
