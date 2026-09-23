import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/feedback/feedback_create_screen.dart';

/// Urgency is a **choice**, not a default.
///
/// `fragment_feedback.xml:33-46` gives neither radio `android:checked`, the
/// enclosing `RadioGroup` (`:27-31`) carries no `android:checkedButton`, and
/// nothing in `FeedbackFragment.kt` calls `check()`. So
/// `rgUrgent.checkedRadioButtonId` is `View.NO_ID` until the person picks, and
/// `validateAndSaveData:96-99` refuses with `feedback_priority_is_required`:
///
/// ```kotlin
/// val rbUrgent = requireView().findViewById<RadioButton>(binding.rgUrgent.checkedRadioButtonId)
/// if (rbUrgent == null) {
///     binding.tlUrgent.error = getString(R.string.feedback_priority_is_required)
///     return
/// }
/// ```
///
/// The port defaulted `_priority = 'No'`, so `_priorityError` was a field that
/// only ever took `null` and the error `Text` reading it was unreachable code.
/// Every unattended form was filed as not-urgent on its author's behalf — a
/// server-visible value nobody chose, on the one channel a user who cannot
/// sign in has to an administrator.
///
/// **A divergence this does not close, recorded rather than fixed:** Kotlin
/// sends `rbUrgent.text.toString()`, the *localised* label, so a Spanish
/// device uploads `"priority": "Sí"` and an Arabic one `"priority": "نعم"`.
/// The port sends the stable `'Yes'`/`'No'` tokens asserted below. That is the
/// better behaviour and is deliberately kept.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  Future<void> fillAndSubmit(
    WidgetTester tester, {
    String? urgency,
    String type = 'Bug',
    String message = 'I cannot sign in',
  }) async {
    if (urgency != null) {
      await tester.tap(find.text(urgency));
      await tester.pump();
    }
    await tester.tap(find.text(type));
    await tester.pump();
    await tester.enterText(find.byType(TextField), message);
    await tester.tap(find.text('Submit'));
    await tester.pumpAndSettle();
  }

  testWidgets('neither urgency option starts selected', (tester) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    await tester.pumpWidget(_wrap(database, config));
    await tester.pumpAndSettle();

    for (final tile in tester.widgetList<RadioListTile<String>>(
      find.byType(RadioListTile<String>),
    )) {
      expect(
        tester
            .widget<RadioGroup<String>>(find.byType(RadioGroup<String>))
            .groupValue,
        isNull,
        reason: '${tile.value} was chosen for the author',
      );
    }
  });

  testWidgets('submitting with no urgency chosen is refused, and says why', (
    tester,
  ) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    await tester.pumpWidget(_wrap(database, config));
    await tester.pumpAndSettle();

    await fillAndSubmit(tester, urgency: null);

    // Both halves matter. The row assertion alone would pass for a screen that
    // refused in silence, which is the class this round is closing; the text
    // assertion alone would pass for one that complained and filed it anyway.
    expect(await database.feedbackDao.getPending(), isEmpty);
    expect(find.text('Feedback priority is required.'), findsOneWidget);
  });

  testWidgets('choosing an urgency clears the refusal and files the form', (
    tester,
  ) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    await tester.pumpWidget(_wrap(database, config));
    await tester.pumpAndSettle();

    await fillAndSubmit(tester, urgency: null);
    expect(find.text('Feedback priority is required.'), findsOneWidget);

    // `rgUrgent.setOnCheckedChangeListener { _, _ -> binding.tlUrgent.error =
    // null }` (`FeedbackFragment.kt:71-73`).
    await tester.tap(find.text('Yes'));
    await tester.pumpAndSettle();
    expect(find.text('Feedback priority is required.'), findsNothing);

    await tester.tap(find.text('Submit'));
    await tester.pumpAndSettle();

    final row = (await database.feedbackDao.getPending()).single;
    expect(row.priority, 'Yes');
    expect(row.type, 'Bug');
  });

  testWidgets('"No" is a choice the author can still make', (tester) async {
    // The previous default was not wrong as a *value* — it was wrong as an
    // assumption. This is the fixture that keeps the fix from being read as
    // "urgent feedback only".
    final database = AppDatabase.memory();
    addTearDown(database.close);

    await tester.pumpWidget(_wrap(database, config));
    await tester.pumpAndSettle();

    await fillAndSubmit(tester, urgency: 'No');

    expect((await database.feedbackDao.getPending()).single.priority, 'No');
  });
}

class _NoSession extends SessionNotifier {
  @override
  Future<UserRow?> build() async => null;
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);
  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}

Widget _wrap(AppDatabase database, ServerConfig? config) {
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
      sessionProvider.overrideWith(_NoSession.new),
      serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
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
