import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/feedback_mapper.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/feedback_uploader.dart';
import 'package:myplanet/ui/feedback/feedback_create_screen.dart';

/// Feedback filed with **nobody signed in**, which is the whole point of the
/// login screen's button: the user who most needs to reach an administrator is
/// the one who cannot get past the front door.
///
/// The port used to refuse this three times over — a Submit button disabled on
/// `session == null`, a bare `return` in the screen, and a `'Not logged in'`
/// error in the notifier — and none of it came from the Kotlin, which has no
/// user test anywhere on the path. `FeedbackComposerViewModel.kt:38` is
/// `val user = userRepository.getUserModel()?.name ?: ""`: an elvis default.
/// `FeedbackRepositoryImpl.createFeedback:57-58,66` writes that string into
/// `owner`, `source` and `messages[0].user`, `saveFeedback:115-117` persists
/// unconditionally, and the upload authenticates with the server credential
/// (`UrlUtils.header`), not the user's.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  Future<List<OutboxRow>> queued(AppDatabase database) =>
      database.outboxDao.due(DateTime.now().millisecondsSinceEpoch + 1000);

  Future<void> fileFeedback(WidgetTester tester) async {
    await tester.tap(find.text('Bug'));
    await tester.pump();
    await tester.enterText(find.byType(TextField), 'I cannot sign in');
    await tester.tap(find.text('Submit'));
    await tester.pumpAndSettle();
  }

  testWidgets('a signed-out user can file feedback, and it reaches the outbox', (
    tester,
  ) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    await tester.pumpWidget(_wrap(database, _NoSession.new, config));
    await tester.pumpAndSettle();

    // Asserted separately from the tap because the refusal used to live in two
    // independent places: with only the notifier's guard restored the button
    // is live and the row never appears, and with only the button's guard
    // restored the tap does nothing. Either one alone fails the row assertion
    // below, so this line is what says *which*.
    expect(
      tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
      isNotNull,
      reason: 'Submit is disabled with no session, so the form is a dead end',
    );

    await fileFeedback(tester);

    final pending = await database.feedbackDao.getPending();
    expect(pending, hasLength(1), reason: 'the feedback was discarded');

    final row = pending.single;
    expect(row.owner, '');
    expect(row.source, '');
    expect(FeedbackMapper.parseMessages(row.messages).single.user, '');
    // No `item`/`state` from the login screen, so Kotlin's else branch.
    expect(row.title, 'Question regarding /');
    expect(row.url, '/');

    final entries = await queued(database);
    expect(entries.map((e) => e.uploadType), [FeedbackUploader.type]);
    expect(entries.single.itemId, row.id);
    // Nullable by design, and nothing in `OutboxDrainer` reads it — the row
    // still drains. Asserted so a later "tidy" that makes it required has to
    // notice this caller.
    expect(entries.single.userId, isNull);
  });

  testWidgets('a signed-in user still signs their own feedback', (
    tester,
  ) async {
    // The fixture that makes the test above mean something. `''` is only
    // evidence of the Kotlin default if a real session produces something
    // else; without this pair, writing `''` unconditionally would be green.
    final database = AppDatabase.memory();
    addTearDown(database.close);

    await tester.pumpWidget(
      _wrap(database, () => _ResolvedSession(_ada), config),
    );
    await tester.pumpAndSettle();
    await fileFeedback(tester);

    final row = (await database.feedbackDao.getPending()).single;
    expect(row.owner, 'ada');
    expect(row.source, 'ada');
    expect(FeedbackMapper.parseMessages(row.messages).single.user, 'ada');
    expect((await queued(database)).single.userId, 'user-1');
  });

  testWidgets('a session that has not finished loading does not lose the form', (
    tester,
  ) async {
    // The port's standing trap, and the reason this screen is not simply
    // "session-free": `ref.read(sessionProvider).value` is null while the
    // provider loads, and on a screen that never watches it the two nulls are
    // indistinguishable. The old code took a bare `return` on both — no
    // snackbar, no error, no row, exactly Phase 100's discarded exam attempt.
    //
    // The session here resolves to a real user *after* Submit is tapped, so a
    // `.value` read sees null and an awaited `.future` sees ada. The assertion
    // is `'ada'` rather than "a row exists", because a read that gave up and
    // wrote `''` would also leave a row.
    final database = AppDatabase.memory();
    addTearDown(database.close);
    final gate = Completer<UserRow?>();

    await tester.pumpWidget(
      _wrap(database, () => _PendingSession(gate.future), config),
    );
    await tester.pump();

    await tester.tap(find.text('Bug'));
    await tester.pump();
    await tester.enterText(find.byType(TextField), 'I cannot sign in');
    await tester.tap(find.text('Submit'));
    await tester.pump();

    expect(await database.feedbackDao.getPending(), isEmpty);

    gate.complete(_ada);
    await tester.pumpAndSettle();

    final pending = await database.feedbackDao.getPending();
    expect(pending, hasLength(1), reason: 'the submit was dropped mid-load');
    expect(pending.single.owner, 'ada');
  });
}

final _ada = UserRow(
  id: 'user-1',
  name: 'ada',
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  isArchived: false,
  isUpdated: false,
);

class _NoSession extends SessionNotifier {
  @override
  Future<UserRow?> build() async => null;
}

class _ResolvedSession extends SessionNotifier {
  _ResolvedSession(this.user);
  final UserRow user;

  @override
  Future<UserRow?> build() async => user;
}

class _PendingSession extends SessionNotifier {
  _PendingSession(this._gate);

  /// Not named `future`: `AsyncNotifier` already has one, and a field of that
  /// name shadows it rather than overriding it.
  final Future<UserRow?> _gate;

  @override
  Future<UserRow?> build() => _gate;
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);
  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}

Widget _wrap(
  AppDatabase database,
  SessionNotifier Function() session,
  ServerConfig? config,
) {
  final router = GoRouter(
    initialLocation: '/login/feedback',
    routes: [
      GoRoute(
        path: '/login',
        builder: (_, _) => const Scaffold(body: Text('login')),
        routes: [
          GoRoute(
            path: 'feedback',
            builder: (_, _) => const FeedbackCreateScreen(),
          ),
        ],
      ),
    ],
  );
  return ProviderScope(
    retry: noProviderRetry,
    overrides: [
      appDatabaseProvider.overrideWith((ref) => database),
      sessionProvider.overrideWith(session),
      serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
      // The uploader stamps the document's origin at queue time; the real
      // source needs a platform channel this harness does not serve, and a
      // throw there would leave the outbox empty — which is what these tests
      // assert against.
      deviceIdentitySourceProvider.overrideWithValue(
        const FixedDeviceIdentitySource(
          DeviceIdentity(
            androidId: 'android-1',
            deviceName: 'Pixel',
            customDeviceName: 'test-phone',
          ),
        ),
      ),
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
