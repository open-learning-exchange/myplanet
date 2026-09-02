import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/resources/add_resource_screen.dart';

import '../support/widget_harness.dart';

/// First tests for `AddResourceScreen` (Phase 102).
///
/// The Kotlin specification is `ui/resources/AddResourceActivity.kt`: the
/// metadata form, its four-step validation ladder (title -> description ->
/// levels -> subjects), the private-team checkbox that is only visible with a
/// `teamId`, and the edit-mode prefill.
///
/// The screen is reached by a `context.push`, so every test mounts it behind a
/// root page and pushes to it — `context.pop()` is a go_router extension and
/// needs something to pop back to.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AppDatabase db;

  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  /// The metadata fields in declaration order. The file-picker card holds no
  /// `TextField`, so these indices are the same in create and edit mode.
  const titleField = 0;
  const authorField = 1;
  const yearField = 2;
  const descriptionField = 3;

  Finder field(int index) => find.byType(TextField).at(index);

  Future<void> pumpScreen(
    WidgetTester tester, {
    String? teamId,
    String? editResourceId,
    UserRow? user,
  }) async {
    // The form is one long `SingleChildScrollView`. It builds every child
    // eagerly, so `find.text` locates widgets below the fold but `tap` misses
    // them, and `dragUntilVisible` never scrolls because its finder already
    // evaluates non-empty. A tall surface sidesteps all of it.
    tester.view.physicalSize = const Size(1000, 3000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      wrapScreen(
        Builder(
          builder: (context) {
            final router = GoRouter.of(context);
            WidgetsBinding.instance.addPostFrameCallback((_) {
              final at = router
                  .routerDelegate
                  .currentConfiguration
                  .last
                  .matchedLocation;
              if (at != '/resources/add') router.push('/resources/add');
            });
            return const Scaffold(body: Text('ROOT_PAGE'));
          },
        ),
        pushTargets: {
          '/resources/add': (_) =>
              AddResourceScreen(teamId: teamId, editResourceId: editResourceId),
        },
        overrides: [
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          sessionProvider.overrideWith(() => _StubSession(user)),
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> tapChip(WidgetTester tester, String label) async {
    await tester.tap(find.widgetWithText(FilterChip, label));
    await tester.pumpAndSettle();
  }

  /// Fills every field the validation ladder requires, so a test that is not
  /// about validation reaches the save.
  Future<void> fillValidForm(
    WidgetTester tester, {
    String title = 'Kihu',
  }) async {
    await tester.enterText(field(titleField), title);
    await tester.enterText(field(descriptionField), 'A description');
    await tapChip(tester, 'Lower Primary');
    await tapChip(tester, 'Agriculture');
  }

  Future<void> tapSubmit(WidgetTester tester, String label) async {
    await tester.tap(find.widgetWithText(FilledButton, label));
    // Never pumpAndSettle here: while `_saving` is true the button holds an
    // indefinite CircularProgressIndicator, which spins pumpAndSettle to its
    // ten-minute default.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
  }

  Future<MyLibraryRow?> onlyRow() async {
    final rows = await db.select(db.myLibraryTable).get();
    return rows.isEmpty ? null : rows.single;
  }

  testWidgets('create mode shows the file picker and the add-resource title', (
    tester,
  ) async {
    await pumpScreen(tester);

    expect(find.text('Add resource'), findsOneWidget);
    expect(find.text('Select a file'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Submit'), findsOneWidget);
  });

  testWidgets('the year field prefills with the current year', (tester) async {
    await pumpScreen(tester);

    final year = tester.widget<TextField>(field(yearField));
    expect(year.controller!.text, DateTime.now().year.toString());
  });

  testWidgets('the private-resource switch appears only with a team', (
    tester,
  ) async {
    await pumpScreen(tester);
    expect(find.byType(SwitchListTile), findsNothing);

    await pumpScreen(tester, teamId: 'team-1');
    final toggle = find.byType(SwitchListTile);
    expect(tester.widget<SwitchListTile>(toggle).value, isTrue);
  });

  group('validation ladder (AddResourceActivity.validate)', () {
    testWidgets('an empty title is rejected', (tester) async {
      await pumpScreen(tester);
      await tapSubmit(tester, 'Submit');

      expect(find.text('Title is required'), findsOneWidget);
      expect(await onlyRow(), isNull);
    });

    testWidgets('an empty description is rejected after the title', (
      tester,
    ) async {
      await pumpScreen(tester);
      await tester.enterText(field(titleField), 'Kihu');
      await tapSubmit(tester, 'Submit');

      expect(find.text('Description is required'), findsOneWidget);
      expect(await onlyRow(), isNull);
    });

    testWidgets('no level is rejected after the description', (tester) async {
      await pumpScreen(tester);
      await tester.enterText(field(titleField), 'Kihu');
      await tester.enterText(field(descriptionField), 'A description');
      await tapSubmit(tester, 'Submit');

      expect(find.text('Level is required'), findsOneWidget);
      expect(await onlyRow(), isNull);
    });

    testWidgets('no subject is rejected after the level', (tester) async {
      await pumpScreen(tester);
      await tester.enterText(field(titleField), 'Kihu');
      await tester.enterText(field(descriptionField), 'A description');
      await tapChip(tester, 'Lower Primary');
      await tapSubmit(tester, 'Submit');

      expect(find.text('Subject is required'), findsOneWidget);
      expect(await onlyRow(), isNull);
    });
  });

  testWidgets('a valid form writes the row and confirms', (tester) async {
    await pumpScreen(
      tester,
      user: buildUserRow(id: 'user-a', name: 'jane'),
    );
    await fillValidForm(tester);
    await tester.enterText(field(authorField), '  Ada  ');
    await tapSubmit(tester, 'Submit');

    expect(find.text('Added to My Library'), findsOneWidget);

    final row = await onlyRow();
    expect(row, isNotNull);
    expect(row!.title, 'Kihu');
    expect(row.author, 'Ada', reason: 'the Kotlin trims every text field');
    expect(row.level, ['Lower Primary']);
    expect(row.subject, ['Agriculture']);
    expect(row.resourceOffline, isTrue);
  });

  testWidgets('the signed-in user is recorded on the new resource', (
    tester,
  ) async {
    await pumpScreen(
      tester,
      user: buildUserRow(id: 'user-a', name: 'jane'),
    );
    await fillValidForm(tester);
    await tapSubmit(tester, 'Submit');

    final row = await onlyRow();
    // `AddResourceActivity` resolves `userSessionManager.getUserModel()` in
    // onCreate and paints it into `tv_added_by`, then reads both back when it
    // builds the request (AddResourceActivity.kt:74-77, :175, :189).
    expect(row!.addedBy, 'jane');
    expect(row.userId, ['user-a'], reason: 'the resource lands on the shelf');
  });

  testWidgets('with no signed-in user the shelf list stays empty', (
    tester,
  ) async {
    await pumpScreen(tester);
    await fillValidForm(tester);
    await tapSubmit(tester, 'Submit');

    final row = await onlyRow();
    // `MyLibrary.setUserId` returns early on a null or blank id, leaving
    // `userId` an empty list. A `[""]` entry is worse than nothing: it fails
    // the My Library predicate (`userId LIKE '%"<uid>"%'`) and passes the
    // catalog one (`userId NOT LIKE ...`), so the row the user just created
    // shows up everywhere except their own library.
    expect(row!.userId, isEmpty);
  });

  testWidgets('a duplicate title is refused', (tester) async {
    await db
        .into(db.myLibraryTable)
        .insert(
          MyLibraryTableCompanion.insert(
            id: 'existing',
            title: const Value('Kihu'),
            titleNormal: const Value('kihu'),
          ),
        );

    await pumpScreen(
      tester,
      user: buildUserRow(id: 'user-a', name: 'jane'),
    );
    await fillValidForm(tester);
    await tapSubmit(tester, 'Submit');

    expect(find.text('Added to My Library'), findsNothing);
    expect((await db.select(db.myLibraryTable).get()).length, 1);
  });

  testWidgets('a team resource is private to that team', (tester) async {
    await pumpScreen(
      tester,
      teamId: 'team-1',
      user: buildUserRow(id: 'user-a', name: 'jane'),
    );
    await fillValidForm(tester);
    await tapSubmit(tester, 'Submit');

    expect(find.text('Resource added to team'), findsOneWidget);
    final row = await onlyRow();
    expect(row!.isPrivate, isTrue);
    expect(row.privateFor, 'team-1');
  });

  group('edit mode (AddResourceActivity.prefillFields)', () {
    Future<void> seed({String? mediaType, String? language}) => db
        .into(db.myLibraryTable)
        .insert(
          MyLibraryTableCompanion.insert(
            id: 'r1',
            title: const Value('Old title'),
            titleNormal: const Value('old title'),
            author: const Value('Ada'),
            description: const Value('Old description'),
            subject: const Value(['Agriculture']),
            level: const Value(['Lower Primary']),
            mediaType: Value(mediaType),
            language: Value(language),
          ),
        );

    testWidgets('prefills the fields and hides the file picker', (
      tester,
    ) async {
      await seed();
      await pumpScreen(tester, editResourceId: 'r1');

      expect(find.text('Edit resource'), findsOneWidget);
      expect(find.text('Select a file'), findsNothing);
      expect(
        tester.widget<TextField>(field(titleField)).controller!.text,
        'Old title',
      );
      expect(
        tester.widget<TextField>(field(descriptionField)).controller!.text,
        'Old description',
      );
    });

    testWidgets('saving updates the row and confirms', (tester) async {
      await seed();
      await pumpScreen(tester, editResourceId: 'r1');

      await tester.enterText(field(titleField), 'New title');
      await tapSubmit(tester, 'Save changes');

      expect(find.text('Resource updated'), findsOneWidget);
      final row = await onlyRow();
      expect(row!.title, 'New title');
      expect(row.titleNormal, 'new title');
    });

    testWidgets('a stored year of null leaves the field empty', (tester) async {
      // `prefillFields` does `binding.etYear.setText(resource.year)`
      // (AddResourceActivity.kt:126) — a null year clears the field rather
      // than substituting this year, which would silently restamp the row.
      await seed();
      await pumpScreen(tester, editResourceId: 'r1');

      expect(tester.widget<TextField>(field(yearField)).controller!.text, '');
    });

    testWidgets(
      'a stored value outside the option list does not break the form '
      '(AddResourceActivity leaves the spinner on its hint)',
      (tester) async {
        // A resource synced from CouchDB carries whatever the server wrote:
        // `mediaType` is commonly lowercase ("video"), and `language` an ISO
        // code ("en"). Neither is in the port's option lists.
        await seed(mediaType: 'video', language: 'en');
        await pumpScreen(tester, editResourceId: 'r1');

        expect(find.text('Edit resource'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  });
}

class _StubSession extends SessionNotifier {
  _StubSession(this.user);
  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}
