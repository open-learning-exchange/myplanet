import 'dart:convert';
import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/achievement_files.dart';
import 'package:myplanet/core/system/file_pick.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/achievements_repository.dart';
import 'package:myplanet/repository/achievements_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/ui/achievements/edit_achievement_screen.dart';

import '../../support/widget_harness.dart';
import '../../support/mock_planet_api.dart';

/// The screen's own localisations, so a test never has to repeat a string the
/// `.arb` owns.
AppLocalizations l10nOf(WidgetTester tester) =>
    AppLocalizations.of(tester.element(find.byType(EditAchievementScreen)));

/// First coverage for `EditAchievementScreen` — the port of
/// `EditAchievementFragment`, and until now 634 lines with none.
///
/// The screen writes two rows (the achievements ledger and the user's profile
/// fields) plus a file on disk, so these drive the real provider graph against
/// an in-memory [AppDatabase] and a temp directory: what lands in the
/// `achievements` row, in `users`, and under `<base>/ole/cv/` *is* the
/// deliverable.
///
/// Two traps, both from `CLAUDE.md`:
///
///  * [AchievementFiles] is genuine `dart:io` and a widget test's zone is
///    fake-async, so those futures never progress there. [settle] yields
///    wall-clock time with `tester.runAsync` and pumps afterwards; pumping
///    *inside* `runAsync` does not work.
///  * The form runs well past the 600px test fold and its body is a
///    `ListView(children: [...])`, which only mounts what is in the viewport —
///    so a taller surface keeps the CV block and the buttons reachable.
class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);

  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}

/// A [FilePick] that hands back whatever the test wants — a PDF, a non-PDF, or
/// `null` for "the user backed out".
class _FakeFilePick implements FilePick {
  _FakeFilePick({this.name, this.extension, this.bytes = const [1, 2, 3]});

  final String? name;
  final String? extension;
  final List<int> bytes;
  int reads = 0;

  @override
  Future<PickedFile?> pickSingle() async {
    final pickedName = name;
    if (pickedName == null) return null;
    return PickedFile(
      name: pickedName,
      extension: extension,
      readBytes: () async {
        reads++;
        return bytes;
      },
    );
  }
}

class _FakeApi extends Fake implements PlanetApi {}

void main() {
  const server = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: 'secret-pin',
    couchDbUrl: 'https://satellite:secret-pin@planet.example.org',
    id: 'config-1',
    code: 'community-a',
    parentCode: 'nation',
  );

  const achievementId = 'user-1@earth';

  late AppDatabase db;
  late Directory tempDir;
  late Future<Directory> Function() savedBaseDirectory;
  late FilePick savedFilePick;

  UserRow userRow({String? dob, String? firstName = 'Ada'}) => UserRow(
    id: 'user-1',
    couchId: 'org.couchdb.user:ada',
    name: 'ada',
    firstName: firstName,
    middleName: 'B',
    lastName: 'Lovelace',
    birthPlace: 'London',
    planetCode: 'earth',
    parentCode: 'nation',
    dob: dob,
    rolesList: const ['learner'],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  setUp(() async {
    db = AppDatabase.memory();
    tempDir = await Directory.systemTemp.createTemp('edit_achievement_test');
    savedBaseDirectory = AchievementFiles.baseDirectory;
    AchievementFiles.baseDirectory = () async => tempDir;
    savedFilePick = FilePick.instance;
    await db.userDao.upsert(userRow().toCompanion(false));
  });

  tearDown(() async {
    AchievementFiles.baseDirectory = savedBaseDirectory;
    FilePick.instance = savedFilePick;
    await db.close();
    try {
      if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
    } catch (_) {}
  });

  AchievementsRepository repository() =>
      AchievementsRepository(MockPlanetApi(), db.achievementDao);

  Future<AchievementRow?> ledger() => db.achievementDao.getById(achievementId);

  Future<void> seedLedger(AchievementInput input) =>
      repository().update(achievementId, input);

  Future<void> seedLibrary({
    String id = 'lib-1',
    String title = 'Water cycle',
    String author = 'Ada',
  }) async {
    await db.myLibraryDao.upsertAll([
      MyLibraryTableCompanion.insert(
        id: id,
        couchId: Value('doc-$id'),
        rev: const Value('3-abc'),
        title: Value(title),
        author: Value(author),
      ),
    ]);
  }

  /// Lets the screen's real disk work finish, then rebuilds. See the header.
  Future<void> settle(WidgetTester tester, {int rounds = 8}) async {
    for (var round = 0; round < rounds; round++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 20)),
      );
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  Future<void> pumpScreen(
    WidgetTester tester, {
    UserRow? session,
    ServerConfig? config = server,
  }) async {
    tester.view.physicalSize = const Size(1000, 3000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
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
              if (location != '/edit') router.push('/edit');
            });
            return const Scaffold(body: Text('ROOT_PAGE'));
          },
        ),
        pushTargets: {'/edit': (_) => const EditAchievementScreen()},
        overrides: [
          appDatabaseProvider.overrideWith((ref) => db),
          sessionProvider.overrideWith(
            () => _TestSessionNotifier(session ?? userRow()),
          ),
          serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
        ],
      ),
    );
    await settle(tester, rounds: 4);
  }

  /// Never `pumpAndSettle` after Update: `_saving` swaps the body for a
  /// "Saving…" placeholder while real file and database work runs.
  Future<void> tapUpdate(WidgetTester tester) async {
    await tester.tap(find.widgetWithText(FilledButton, 'Update'));
    await settle(tester);
  }

  Future<void> addAchievementEntry(
    WidgetTester tester, {
    required String title,
    String? description,
  }) async {
    await tester.tap(find.widgetWithText(OutlinedButton, 'Add an Achievement'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, 'Title'), title);
    if (description != null) {
      await tester.enterText(
        find.widgetWithText(TextField, 'Description'),
        description,
      );
    }
    await tester.tap(find.widgetWithText(TextButton, 'Submit'));
    await tester.pumpAndSettle();
  }

  Future<void> addReferenceEntry(
    WidgetTester tester, {
    required String name,
  }) async {
    await tester.tap(find.widgetWithText(OutlinedButton, 'Add a Reference'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, 'Name'), name);
    await tester.tap(find.widgetWithText(TextButton, 'Submit'));
    await tester.pumpAndSettle();
  }

  group('the form', () {
    testWidgets('prefills the profile and the ledger', (tester) async {
      await seedLedger(
        const AchievementInput(
          achievementsHeader: 'a decade of teaching',
          purpose: 'to teach',
          goals: 'to learn',
          sendToNation: true,
          achievementsJson: '[{"title":"First summit"}]',
          referencesJson: '[{"name":"Mo"}]',
        ),
      );

      await pumpScreen(tester);

      expect(find.text('Ada'), findsOneWidget);
      expect(find.text('B'), findsOneWidget);
      expect(find.text('Lovelace'), findsOneWidget);
      expect(find.text('London'), findsOneWidget);
      expect(find.text('a decade of teaching'), findsOneWidget);
      expect(find.text('to teach'), findsOneWidget);
      expect(find.text('to learn'), findsOneWidget);
      expect(find.text('First summit'), findsOneWidget);
      expect(find.text('Mo'), findsOneWidget);
      expect(
        tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value,
        isTrue,
      );
    });

    testWidgets('a stored birth date renders formatted, not as a timestamp', (
      tester,
    ) async {
      // `populateAchievementData` runs the column through
      // `getFormattedDate(dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")`. The row used
      // to print the column verbatim.
      await seedLedger(const AchievementInput());
      await pumpScreen(
        tester,
        session: userRow(dob: '1990-05-02T00:00:00.000Z'),
      );

      expect(find.text('Wednesday, May 02, 1990'), findsOneWidget);
      expect(find.text('1990-05-02T00:00:00.000Z'), findsNothing);
    });

    testWidgets('the required fields are named when they are missing', (
      tester,
    ) async {
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(firstName: ''));

      await tapUpdate(tester);

      expect(
        find.text('Please fill required fields: First name, Birth date'),
        findsOneWidget,
      );
      expect((await ledger())?.username, isEmpty);
    });
  });

  group('saving', () {
    testWidgets('writes the ledger, the profile fields, and pops', (
      tester,
    ) async {
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await tester.enterText(
        find.widgetWithText(
          TextFormField,
          'My Goals - What are your goals for the next 10 years?',
        ),
        'run a marathon',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Birth place'),
        'Cambridge',
      );
      await tapUpdate(tester);

      final row = await ledger();
      expect(row?.goals, 'run a marathon');
      expect(row?.username, 'ada');
      expect(row?.createdOn, 'earth');
      expect(row?.parentCode, 'nation');
      expect(row?.uploaded, isFalse);
      final user = await db.userDao.getById('user-1');
      expect(user?.birthPlace, 'Cambridge');
      expect(user?.isUpdated, isTrue);
      expect(find.text('ROOT_PAGE'), findsOneWidget);
    });

    testWidgets('trims the header, goals and purpose like btnUpdate does', (
      tester,
    ) async {
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await tester.enterText(
        find.widgetWithText(
          TextFormField,
          'Summary of achievements - Briefly summarize your achievements',
        ),
        '  a decade of teaching  ',
      );
      await tester.enterText(
        find.widgetWithText(
          TextFormField,
          'My Goals - What are your goals for the next 10 years?',
        ),
        '  run a marathon  ',
      );
      await tester.enterText(
        find.widgetWithText(
          TextFormField,
          'My Purpose - What are your educational and professional ambitions?',
        ),
        '  to teach  ',
      );
      await tapUpdate(tester);

      final row = await ledger();
      expect(row?.achievementsHeader, 'a decade of teaching');
      expect(row?.goals, 'run a marathon');
      expect(row?.purpose, 'to teach');
    });

    testWidgets('a never-synced ledger still names a document to upload', (
      tester,
    ) async {
      // The whole point of the derived `_id`: this is the state a first-ever
      // edit leaves, and the outbox row it queues has to name a document.
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await tapUpdate(tester);

      final queued = await db.outboxDao.findOpen(
        AchievementsUploader.type,
        achievementId,
      );
      expect(queued, isNotNull);
      final payload = jsonDecode(queued!.payload) as Map<String, dynamic>;
      expect(payload['_id'], achievementId);
    });
  });

  group('achievement and reference entries', () {
    testWidgets('an added achievement can be removed again', (tester) async {
      // `EditAttachementBinding.ivDelete`: the port rendered an edit action
      // only, so an entry added by mistake was permanent.
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await addAchievementEntry(tester, title: 'First summit');
      expect(find.text('First summit'), findsOneWidget);

      await tester.tap(
        find.descendant(
          of: find.widgetWithText(ListTile, 'First summit'),
          matching: find.byIcon(Icons.delete_outline),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('First summit'), findsNothing);
      await tapUpdate(tester);
      expect((await ledger())?.achievementsJson, '[]');
    });

    testWidgets('an added reference can be removed again', (tester) async {
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await addReferenceEntry(tester, name: 'Mo');
      expect(find.text('Mo'), findsOneWidget);

      await tester.tap(
        find.descendant(
          of: find.widgetWithText(ListTile, 'Mo'),
          matching: find.byIcon(Icons.delete_outline),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Mo'), findsNothing);
      await tapUpdate(tester);
      expect((await ledger())?.referencesJson, '[]');
    });

    testWidgets('the entry dialogs trim what they store', (tester) async {
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await addAchievementEntry(
        tester,
        title: '  First summit  ',
        description: '  in winter  ',
      );
      await addReferenceEntry(tester, name: '  Mo  ');
      await tapUpdate(tester);

      final row = await ledger();
      final entry = AchievementsRepository.achievementsArray(
        row!.achievementsJson,
      ).single;
      expect(entry['title'], 'First summit');
      expect(entry['description'], 'in winter');
      expect(
        AchievementsRepository.referencesArray(
          row.referencesJson,
        ).single['name'],
        'Mo',
      );
    });

    testWidgets('an achievement date cannot be in the future', (tester) async {
      // `dpd.datePicker.maxDate = now.timeInMillis`.
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await tester.tap(
        find.widgetWithText(OutlinedButton, 'Add an Achievement'),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ListTile, 'Date'));
      await tester.pumpAndSettle();

      final picker = tester.widget<CalendarDatePicker>(
        find.byType(CalendarDatePicker),
      );
      final today = DateTime.now();
      expect(picker.lastDate.year, today.year);
      expect(picker.lastDate.month, today.month);
      expect(picker.lastDate.day, today.day);
    });

    testWidgets('the entry row names the resources it carries', (tester) async {
      // `showAchievementAndInfo` inflates a chip per attached resource into
      // the row's flexbox; the port's row showed the title only, so there was
      // no way to see what an entry carried without reopening the dialog.
      await seedLedger(
        const AchievementInput(
          achievementsJson:
              '[{"title":"First summit","resources":[{"title":"Water cycle"}]}]',
        ),
      );
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      expect(
        find.descendant(
          of: find.widgetWithText(ListTile, 'First summit'),
          matching: find.widgetWithText(Chip, 'Water cycle'),
        ),
        findsOneWidget,
      );
    });

    testWidgets('an attached resource keeps its whole document', (
      tester,
    ) async {
      // `showResourceListDialog` stores `list[ii].serializeResource()`; the
      // port stored `{'title': name}`, so the achievement reached the server
      // naming resources it could not identify.
      await seedLibrary();
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await tester.tap(
        find.widgetWithText(OutlinedButton, 'Add an Achievement'),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, 'Title'),
        'First summit',
      );
      await tester.tap(
        find.widgetWithText(OutlinedButton, l10nOf(tester).selectResources),
      );
      await settle(tester, rounds: 3);
      await tester.tap(find.widgetWithText(CheckboxListTile, 'Water cycle'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, 'Submit').last);
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, 'Submit'));
      await tester.pumpAndSettle();
      await tapUpdate(tester);

      final entry = AchievementsRepository.achievementsArray(
        (await ledger())!.achievementsJson,
      ).single;
      final resource = AchievementsRepository.resourcesOf(entry).single;
      expect(resource['title'], 'Water cycle');
      expect(resource['author'], 'Ada');
      expect(resource['_id'], 'doc-lib-1');
    });
  });

  group('the CV/resume attachment', () {
    testWidgets('the picked bytes land under the name the ledger carries', (
      tester,
    ) async {
      // The pair, together: the screen writes the file and the row, and the
      // uploader looks the bytes up by `payload['resumeFileName']`. Two
      // recent defects in this port were exactly a mismatch between those two
      // keys, and each half had a passing test of its own.
      FilePick.instance = _FakeFilePick(
        name: 'my resume.pdf',
        extension: 'pdf',
        bytes: const [8, 9, 10],
      );
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await tester.tap(find.widgetWithText(OutlinedButton, 'Choose File'));
      await settle(tester, rounds: 3);
      // `tvCvFilename` names the pending pick, and the picker callback hides
      // `llCurrentCv` — the View/Delete row — because a fresh pick has no
      // bytes on disk until `computeCvFilename` copies them at save time.
      expect(find.text('my resume.pdf'), findsOneWidget);
      expect(find.textContaining('Current CV/Resume:'), findsNothing);

      // The only test here that waits on a real file write, so it needs the
      // most wall-clock time: `Directory.create` + `writeAsBytes(flush: true)`
      // is several io round trips, and each one needs its own `runAsync`.
      await tester.tap(find.widgetWithText(FilledButton, 'Update'));
      await settle(tester, rounds: 40);

      final row = await ledger();
      expect(row?.resumeFileName, 'my resume.pdf');

      final uploader = AchievementsUploader(
        _FakeApi(),
        repository(),
        db.achievementDao,
        OutboxRepository(db.outboxDao),
      );
      final payload = AchievementsRepository.serialize(row!);
      final bytes = await tester.runAsync(
        () => uploader.readResumeBytes(payload['resumeFileName'] as String),
      );
      expect(bytes, const [8, 9, 10]);
    });

    testWidgets('a non-PDF pick is refused and reads no bytes', (tester) async {
      final picker = _FakeFilePick(name: 'notes.txt', extension: 'txt');
      FilePick.instance = picker;
      await seedLedger(const AchievementInput());
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await tester.tap(find.widgetWithText(OutlinedButton, 'Choose File'));
      await settle(tester, rounds: 3);

      expect(find.text('Please select a PDF file'), findsOneWidget);
      expect(picker.reads, 0);
      expect(find.text('No file chosen'), findsOneWidget);
    });

    testWidgets('a cancelled pick changes nothing', (tester) async {
      FilePick.instance = _FakeFilePick();
      await seedLedger(const AchievementInput(resumeFileName: 'old.pdf'));
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await tester.tap(find.widgetWithText(OutlinedButton, 'Choose File'));
      await settle(tester, rounds: 3);

      expect(find.text('Current CV/Resume: old.pdf'), findsOneWidget);
      await tapUpdate(tester);
      expect((await ledger())?.resumeFileName, 'old.pdf');
    });

    testWidgets('deleting the CV clears the name on the ledger', (
      tester,
    ) async {
      await seedLedger(const AchievementInput(resumeFileName: 'old.pdf'));
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await tester.tap(find.widgetWithText(TextButton, 'Delete CV'));
      await tester.pumpAndSettle();
      await tapUpdate(tester);

      expect((await ledger())?.resumeFileName, isEmpty);
    });

    testWidgets('viewing a CV whose bytes are gone says so', (tester) async {
      // `btnViewCvEdit`'s else branch toasts `file_not_found`; the port
      // returned silently, so the button looked broken.
      await seedLedger(const AchievementInput(resumeFileName: 'old.pdf'));
      await pumpScreen(tester, session: userRow(dob: '1990-05-02'));

      await tester.tap(find.widgetWithText(TextButton, 'View CV/Resume'));
      await settle(tester, rounds: 3);

      // `file_not_found` takes a `%s` and names the file.
      expect(find.text('File not found: old.pdf'), findsOneWidget);
    });
  });

  group('achievementBirthDate', () {
    test('formats a stored ISO-8601 timestamp the way the Kotlin does', () {
      expect(
        achievementBirthDate('1990-05-02T00:00:00.000Z'),
        'Wednesday, May 02, 1990',
      );
      expect(achievementBirthDate('1990-05-02'), 'Wednesday, May 02, 1990');
    });

    test('is null when nothing is stored, and N/A when it will not parse', () {
      expect(achievementBirthDate(null), isNull);
      expect(achievementBirthDate('   '), isNull);
      expect(achievementBirthDate('not a date'), 'N/A');
    });
  });
}
