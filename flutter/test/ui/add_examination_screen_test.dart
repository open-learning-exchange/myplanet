import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/health_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/health_repository.dart';
import 'package:myplanet/ui/health/add_examination_screen.dart';

import '../support/widget_harness.dart';

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

/// Pins the blood-pressure field to the Kotlin `validateFields` parity rules
/// (`HealthExaminationActivity`): the value must contain `/`, split into
/// exactly two numeric parts, and the systolic (60-300) and diastolic
/// (40-200) must each land in range. Empty is allowed (BP is optional).
///
/// The screen is pumped with no signed-in user, so `_saveExamination` returns
/// after `validate()` — the form still surfaces every validator's error text.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Finder bpField() => find.ancestor(
    of: find.text('Blood pressure (systolic/diastolic)'),
    matching: find.byType(TextFormField),
  );

  Future<void> pumpScreen(WidgetTester tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const AddExaminationScreen(),
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(null)),
          examinationNotifierProvider.overrideWith(
            (ref, params) =>
                ExaminationNotifier(_NeverHealthRepo(), null, null),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> enterBpAndSave(WidgetTester tester, String text) async {
    await tester.enterText(bpField(), text);
    final saveButton = find.widgetWithText(FilledButton, 'Save');
    await tester.dragUntilVisible(
      saveButton,
      find.byType(Scrollable).first,
      const Offset(0, -150),
    );
    await tester.tap(saveButton);
    await tester.pump();
    // Validation sets the field's errorText; scroll back to the BP field so
    // the lazy ListView builds its error helper line.
    await tester.dragUntilVisible(
      bpField(),
      find.byType(Scrollable).first,
      const Offset(0, 150),
    );
    await tester.pump();
  }

  testWidgets('valid BP passes validation', (tester) async {
    await pumpScreen(tester);
    await enterBpAndSave(tester, '120/80');
    expect(
      find.text('Blood pressure must be between 60/40 and 300/200'),
      findsNothing,
    );
    expect(
      find.text('Blood pressure should be systolic/diastolic'),
      findsNothing,
    );
  });

  testWidgets('BP without slash is rejected', (tester) async {
    await pumpScreen(tester);
    await enterBpAndSave(tester, '12080');
    expect(
      find.text('Blood pressure should be systolic/diastolic'),
      findsOneWidget,
    );
  });

  testWidgets('BP with too many parts is rejected', (tester) async {
    await pumpScreen(tester);
    await enterBpAndSave(tester, '120/80/40');
    expect(
      find.text('Blood pressure should be systolic/diastolic'),
      findsOneWidget,
    );
  });

  testWidgets('non-numeric BP is rejected', (tester) async {
    await pumpScreen(tester);
    await enterBpAndSave(tester, 'abc/def');
    expect(find.text('Systolic and diastolic must be numbers'), findsOneWidget);
  });

  testWidgets('out-of-range systolic is rejected', (tester) async {
    await pumpScreen(tester);
    await enterBpAndSave(tester, '400/80');
    expect(
      find.text('Blood pressure must be between 60/40 and 300/200'),
      findsOneWidget,
    );
  });

  testWidgets('out-of-range diastolic is rejected', (tester) async {
    await pumpScreen(tester);
    await enterBpAndSave(tester, '120/250');
    expect(
      find.text('Blood pressure must be between 60/40 and 300/200'),
      findsOneWidget,
    );
  });

  // ── Other Diagnosis chip cloud — port of Kotlin's ChipCloud ────────────
  //
  // Tapping Add with text in the Other diagnosis field creates a removable
  // chip; pressing Enter on the field does the same; the field clears after
  // adding; and the delete icon removes the chip. Empty input is a no-op.

  Finder addDiagButton() => find.widgetWithText(FilledButton, 'Add');

  Future<void> scrollToOtherDiagnosis(WidgetTester tester) async {
    await tester.dragUntilVisible(
      addDiagButton(),
      find.byType(Scrollable).first,
      const Offset(0, -150),
    );
  }

  testWidgets('typing a custom diagnosis and tapping Add creates a chip', (
    tester,
  ) async {
    await pumpScreen(tester);
    await scrollToOtherDiagnosis(tester);
    final field = find
        .descendant(
          of: find.ancestor(of: addDiagButton(), matching: find.byType(Row)),
          matching: find.byType(TextField),
        )
        .first;
    await tester.enterText(field, 'Test condition');
    // Dismiss the keyboard so the Add button is tappable.
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pump();
    await tester.tap(addDiagButton(), warnIfMissed: false);
    await tester.pumpAndSettle();
    expect(find.widgetWithText(Chip, 'Test condition'), findsOneWidget);
  });

  testWidgets('submitting the field with the keyboard also adds a chip', (
    tester,
  ) async {
    await pumpScreen(tester);
    await scrollToOtherDiagnosis(tester);
    final field = find
        .descendant(
          of: find.ancestor(of: addDiagButton(), matching: find.byType(Row)),
          matching: find.byType(TextField),
        )
        .first;
    await tester.enterText(field, 'Keyboard entry');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(find.widgetWithText(Chip, 'Keyboard entry'), findsOneWidget);
  });

  testWidgets('the chip delete icon removes the chip', (tester) async {
    await pumpScreen(tester);
    await scrollToOtherDiagnosis(tester);
    final field = find
        .descendant(
          of: find.ancestor(of: addDiagButton(), matching: find.byType(Row)),
          matching: find.byType(TextField),
        )
        .first;
    await tester.enterText(field, 'Removable');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pump();
    await tester.tap(addDiagButton(), warnIfMissed: false);
    await tester.pumpAndSettle();
    expect(find.widgetWithText(Chip, 'Removable'), findsOneWidget);
    // The delete icon is the only tappable icon in the chip.
    await tester.tap(find.byIcon(Icons.cancel).first);
    await tester.pumpAndSettle();
    expect(find.widgetWithText(Chip, 'Removable'), findsNothing);
  });

  testWidgets('empty other-diagnosis input is a no-op', (tester) async {
    await pumpScreen(tester);
    await scrollToOtherDiagnosis(tester);
    await tester.tap(addDiagButton(), warnIfMissed: false);
    await tester.pump();
    expect(find.byType(Chip), findsNothing);
  });

  // ── Exit confirmation — port of HealthExaminationActivity.finish() ─────
  //
  // The back gesture is intercepted (canPop: false) and a dialog asks the
  // user to confirm; Cancel dismisses the dialog and keeps the screen, while
  // "Yes, I want to exit" closes it.

  testWidgets('pressing back shows the exit-confirmation dialog', (
    tester,
  ) async {
    await pumpScreen(tester);
    final binding = tester.binding;
    binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Are you sure you want to cancel adding examination? The data will be lost.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('Cancel in the exit dialog keeps the screen open', (
    tester,
  ) async {
    await pumpScreen(tester);
    tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(find.byType(AddExaminationScreen), findsOneWidget);
    expect(
      find.text(
        'Are you sure you want to cancel adding examination? The data will be lost.',
      ),
      findsNothing,
    );
  });

  testWidgets('Yes exits the screen', (tester) async {
    await pumpScreen(tester);
    tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Yes, I want to exit.'));
    await tester.pumpAndSettle();
    expect(find.byType(AddExaminationScreen), findsNothing);
  });

  group('saving', () {
    /// The screen is pushed from a root page so `context.pop()` has somewhere
    /// to land — go_router asserts on a pop from the first route.
    /// Returns the notifiers the screen caused to be built — the provider is
    /// lazy, so nothing exists until the save path reads it.
    Future<List<_RecordingDetail>> pumpPushedScreen(
      WidgetTester tester, {
      required HealthRepository repository,
      required AppDatabase database,
      required UserRow session,
    }) async {
      final built = <_RecordingDetail>[];
      await tester.pumpWidget(
        wrapScreen(
          Builder(
            builder: (context) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                final router = GoRouter.of(context);
                final location = router
                    .routerDelegate
                    .currentConfiguration
                    .last
                    .matchedLocation;
                if (location != '/examination') router.push('/examination');
              });
              return const Scaffold(body: Text('ROOT_PAGE'));
            },
          ),
          overrides: [
            appDatabaseProvider.overrideWith((ref) {
              ref.onDispose(database.close);
              return database;
            }),
            healthRepositoryProvider.overrideWithValue(repository),
            // Left unset the real one reaches `PlanetPrefs`, which is
            // `UnimplementedError` in a widget test — the Phase 75 trap.
            serverConfigProvider.overrideWith(_NullConfig.new),
            sessionProvider.overrideWith(() => _TestSessionNotifier(session)),
            patientDetailProvider.overrideWith((ref) {
              final notifier = _RecordingDetail(ref);
              built.add(notifier);
              return notifier;
            }),
          ],
          pushTargets: {
            '/examination': (_) => const AddExaminationScreen(userId: 'pat-1'),
          },
        ),
      );
      await tester.pumpAndSettle();
      return built;
    }

    /// Never `pumpAndSettle` after Save: the button holds a
    /// `CircularProgressIndicator` while `_isSaving`.
    Future<void> tapSave(WidgetTester tester) async {
      final save = find.widgetWithText(FilledButton, 'Save');
      await tester.dragUntilVisible(
        save,
        find.byType(Scrollable).first,
        const Offset(0, -150),
      );
      await tester.tap(save);
      for (var i = 0; i < 16; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
    }

    testWidgets('a saved record reaches the patient and the screen pops', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      var counter = 0;
      final repo = HealthRepository(
        _NoopApi(),
        db.healthExaminationDao,
        db.userDao,
        createId: () => 'exam-${++counter}',
      );
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'pat-1',
          couchId: const Value('pat-1'),
          name: const Value('ada'),
        ),
      );
      final patient = (await db.userDao.getById('pat-1'))!;
      final built = await pumpPushedScreen(
        tester,
        repository: repo,
        database: db,
        session: patient,
      );

      await tapSave(tester);

      final record = await repo.getPatientHealthRecords(
        'pat-1',
        (await db.userDao.getById('pat-1'))!,
      );
      expect(record?.examinations, hasLength(1));
      // `MyHealthFragment.onResume` re-selects the patient when this screen
      // finishes; without it the record was saved and invisible.
      expect(built.single.refreshes, 1);
      expect(find.text('ROOT_PAGE'), findsOneWidget);
    });

    testWidgets('a failed save says so and keeps the form open', (
      tester,
    ) async {
      // `saveResult == false` toasts `unable_to_add_health_record` and does
      // *not* close the activity. `save` swallows its failures into
      // `state.error`, which nothing read, so every failure — a drift error, a
      // session that will not resolve — reported a saved record and popped.
      final db = AppDatabase.memory();
      await db.userDao.upsert(
        UsersCompanion.insert(id: 'pat-1', couchId: const Value('pat-1')),
      );
      final patient = (await db.userDao.getById('pat-1'))!;
      final built = await pumpPushedScreen(
        tester,
        repository: _ThrowingHealthRepo(),
        database: db,
        session: patient,
      );

      await tapSave(tester);

      expect(find.text('Unable to add health record.'), findsOneWidget);
      expect(find.text('ROOT_PAGE'), findsNothing);
      expect(find.byType(AddExaminationScreen), findsOneWidget);
      expect(built.fold<int>(0, (sum, d) => sum + d.refreshes), 0);
    });
  });
}

class _NoopApi extends Mock implements PlanetApi {}

class _NullConfig extends ServerConfigNotifier {
  @override
  ServerConfig? build() => null;
}

/// A patient detail notifier that records refreshes instead of running them.
class _RecordingDetail extends PatientDetailNotifier {
  _RecordingDetail(super.ref);

  int refreshes = 0;

  @override
  Future<void> refresh() async => refreshes++;
}

class _ThrowingHealthRepo implements HealthRepository {
  @override
  Future<UserRow?> getPatientById(String id) async =>
      throw StateError('database gone');

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

class _NeverHealthRepo implements HealthRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('repo not called for a new empty examination');
}
