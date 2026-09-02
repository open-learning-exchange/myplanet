import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/core/utils/constants.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/submissions_uploader.dart';
import 'package:myplanet/ui/exam/user_information_screen.dart';

import '../../support/widget_harness.dart';

/// First coverage for `UserInformationScreen` — the port of
/// `UserInformationFragment`, and until now 415 lines with none.
///
/// The Kotlin is the specification, and it is worth restating because the two
/// visibility modes are easy to invert:
///
///  * `shouldHideElements == true` (`BaseExamFragment` passes
///    `exam?.isFromNation != true`, so every team/public survey) opens on the
///    **year-of-birth** field with the four extra blocks hidden, and shows the
///    `btnAdditionalFields` toggle so the respondent can open them. The layout
///    ships that button reading "Show additional fields".
///  * `shouldHideElements == false` opens on the full profile form with the
///    toggle **gone** — the layout's default visibilities.
///
/// The port's parameter is the negation (`showAdditionalFields`), so
/// `showAdditionalFields: false` is Kotlin's `shouldHideElements = true`.
///
/// These drive the real [SubmissionsRepository] against an in-memory
/// [AppDatabase] rather than faking it: the profile document written into the
/// `submissions.user` column *is* the deliverable — `serialize` hands it
/// straight to CouchDB — so a fake repository would assert on nothing.
class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}

/// A session whose future rejects — the shape Phase 100 found on the exam
/// screen, where `valueOrNull` could only ever be null but the future can
/// throw.
class _FailingSessionNotifier extends SessionNotifier {
  @override
  Future<UserRow?> build() async => throw Exception('session unavailable');
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);

  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}

/// Pushes the screen onto a root page, the way `public_survey_screen` does.
/// A pushed route is also what lets the screen's `Navigator.pop()` land
/// somewhere instead of emptying the tree.
class _PushOnce extends StatefulWidget {
  const _PushOnce({required this.child, required this.onPopped});

  final Widget child;

  /// Records what the pushed screen popped with.
  final void Function(bool?) onPopped;

  @override
  State<_PushOnce> createState() => _PushOnceState();
}

class _PushOnceState extends State<_PushOnce> {
  bool _pushed = false;

  @override
  Widget build(BuildContext context) {
    if (!_pushed) {
      _pushed = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        Navigator.of(context)
            .push<bool>(MaterialPageRoute<bool>(builder: (_) => widget.child))
            .then(widget.onPopped);
      });
    }
    return const Scaffold(body: Text('ROOT_PAGE'));
  }
}

void main() {
  const server = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: 'secret-pin',
    couchDbUrl: 'https://satellite:secret-pin@planet.example.org',
    id: 'config-1',
    code: 'community-a',
    parentCode: 'nation',
  );

  const submissionId = 'submission-1';

  late AppDatabase db;
  final popResults = <bool?>[];

  final user = UserRow(
    id: 'user-1',
    couchId: 'org.couchdb.user:ada',
    name: 'ada',
    firstName: 'Ada',
    middleName: 'B',
    lastName: 'Lovelace',
    email: 'ada@example.org',
    phoneNumber: '555-0100',
    language: 'नेपाली',
    level: 'Expert',
    rolesList: const ['learner'],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  setUp(() async {
    popResults.clear();
    db = AppDatabase.memory();
    await db.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: submissionId,
        parentId: const Value('survey-1'),
        parent: Value(jsonEncode({'_id': 'survey-1', 'name': 'Water survey'})),
        userId: const Value('user-1'),
        type: const Value('survey'),
        status: const Value('pending'),
        uploaded: const Value(false),
      ),
    ]);
  });

  tearDown(() async {
    await db.close();
  });

  /// The form runs past the 600px test fold in full-fields mode, and a
  /// `ListView(children: [...])` only mounts what is in the viewport — so
  /// `find.text` reports "Found 0 widgets" for a Save button that renders fine
  /// on a device. A taller surface keeps every field reachable without a
  /// scroll between each assertion.
  void useTallSurface(WidgetTester tester) {
    tester.view.physicalSize = const Size(1000, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
  }

  Future<void> pumpScreen(
    WidgetTester tester, {
    bool showAdditionalFields = false,
    UserRow? session,
    bool anonymous = false,
    bool failingSession = false,
    ServerConfig? config = server,
    String? teamId = 'team-1',
  }) async {
    useTallSurface(tester);
    await tester.pumpWidget(
      wrapScreen(
        _PushOnce(
          onPopped: popResults.add,
          child: UserInformationScreen(
            submissionId: submissionId,
            teamId: teamId,
            showAdditionalFields: showAdditionalFields,
          ),
        ),
        overrides: [
          appDatabaseProvider.overrideWith((ref) => db),
          sessionProvider.overrideWith(
            failingSession
                ? _FailingSessionNotifier.new
                : () => _TestSessionNotifier(
                    anonymous ? null : (session ?? user),
                  ),
          ),
          serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
          // The real source reads `planetPrefs`, which is `UnimplementedError`
          // in the harness; the uploader reads it at queue time.
          deviceIdentitySourceProvider.overrideWithValue(
            const FixedDeviceIdentitySource(
              DeviceIdentity(
                androidId: 'android-1',
                deviceName: 'Pixel',
                customDeviceName: 'ada-phone',
              ),
            ),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  /// The submission's profile document, decoded.
  Future<Map<String, dynamic>?> savedProfile() async {
    final row = await db.submissionDao.getById(submissionId);
    final raw = row?.user;
    if (raw == null || raw.isEmpty) return null;
    return jsonDecode(raw) as Map<String, dynamic>;
  }

  /// Never `pumpAndSettle` here: while `_isSubmitting` is true the Save button
  /// holds a `CircularProgressIndicator`, whose indefinite animation spins
  /// `pumpAndSettle` to its ten-minute default — a failure that looks exactly
  /// like a hang.
  Future<void> tapSave(WidgetTester tester) async {
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    for (var i = 0; i < 8; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
  }

  group('visibility modes', () {
    testWidgets('a team survey opens on the year of birth with the toggle', (
      tester,
    ) async {
      await pumpScreen(tester);

      expect(find.text('Year of birth'), findsOneWidget);
      expect(find.text('First name'), findsNothing);
      expect(find.text('Level'), findsNothing);
      // `btnAdditionalFields` is VISIBLE exactly when the form opens
      // collapsed, and the layout ships it reading "Show additional fields".
      expect(find.text('Show additional fields'), findsOneWidget);
    });

    testWidgets('the toggle opens the extra blocks and hides the year', (
      tester,
    ) async {
      await pumpScreen(tester);

      await tester.tap(find.text('Show additional fields'));
      await tester.pumpAndSettle();

      expect(find.text('First name'), findsOneWidget);
      expect(find.text('Email'), findsOneWidget);
      expect(find.text('Phone number'), findsOneWidget);
      expect(find.text('Level'), findsOneWidget);
      expect(find.text('Year of birth'), findsNothing);
      expect(find.text('Hide additional fields'), findsOneWidget);
    });

    testWidgets('a from-nation survey opens on the full form with no toggle', (
      tester,
    ) async {
      await pumpScreen(tester, showAdditionalFields: true);

      expect(find.text('First name'), findsOneWidget);
      expect(find.text('Level'), findsOneWidget);
      expect(find.text('Year of birth'), findsNothing);
      // `initViews`'s else branch hides the button outright: this mode has no
      // second state to toggle into.
      expect(find.text('Show additional fields'), findsNothing);
      expect(find.text('Hide additional fields'), findsNothing);
    });

    testWidgets('the collapsed mode is the default', (tester) async {
      // The only Kotlin caller that opens the full form
      // (`BaseDashboardFragment:89`) passes an *empty* `sub_id`, so no caller
      // carrying a submission id uses that mode.
      expect(
        const UserInformationScreen(submissionId: 'x').showAdditionalFields,
        isFalse,
      );
    });

    testWidgets('gender is offered in both modes', (tester) async {
      await pumpScreen(tester);
      expect(find.text('Male'), findsOneWidget);
      expect(find.text('Female'), findsOneWidget);

      await pumpScreen(tester, showAdditionalFields: true);
      expect(find.text('Male'), findsOneWidget);
      expect(find.text('Female'), findsOneWidget);
    });
  });

  group('year of birth validation', () {
    testWidgets('an empty year blocks the save', (tester) async {
      await pumpScreen(tester);

      await tapSave(tester);

      expect(find.text('Year of birth is required'), findsOneWidget);
      expect(await savedProfile(), isNull);
    });

    testWidgets('a year before 1900 blocks the save', (tester) async {
      await pumpScreen(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Year of birth'),
        '1899',
      );
      await tapSave(tester);

      final currentYear = DateTime.now().year;
      expect(
        find.text('Year must be between 1900 and $currentYear'),
        findsOneWidget,
      );
      expect(await savedProfile(), isNull);
    });
  });

  group('the profile document', () {
    testWidgets('the collapsed form stores age, gender and betaEnabled', (
      tester,
    ) async {
      await pumpScreen(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Year of birth'),
        '2000',
      );
      await tester.tap(find.text('Female'));
      await tester.pumpAndSettle();
      await tapSave(tester);

      // `UserSurveyProfile.toJson`: the year of birth becomes an `age` string,
      // every empty field is omitted, and `betaEnabled` is always present.
      final expectedAge = '${DateTime.now().year - 2000}';
      expect(await savedProfile(), <String, dynamic>{
        'age': expectedAge,
        'gender': 'female',
        'betaEnabled': false,
      });
    });

    testWidgets('the full form omits what the respondent left blank', (
      tester,
    ) async {
      await pumpScreen(tester, showAdditionalFields: true, anonymous: true);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'First name'),
        '  Grace  ',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Email'),
        'grace@example.org',
      );
      await tapSave(tester);

      // No `middleName`, `lastName`, `phoneNumber` or `gender`, and no `age`
      // — the year-of-birth block is not on screen. The two spinners always
      // carry a value, so `language` and `level` are their first entries.
      expect(await savedProfile(), <String, dynamic>{
        'firstName': 'Grace',
        'email': 'grace@example.org',
        'language': 'English',
        'level': 'Beginner',
        'betaEnabled': false,
      });
    });

    testWidgets('a picked birth date is stored as an ISO-8601 timestamp', (
      tester,
    ) async {
      await pumpScreen(tester, showAdditionalFields: true, anonymous: true);

      await tester.tap(find.text('Select date'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();
      await tapSave(tester);

      final today = DateTime.now();
      final expected =
          '${today.year.toString().padLeft(4, '0')}-'
          '${today.month.toString().padLeft(2, '0')}-'
          '${today.day.toString().padLeft(2, '0')}'
          'T00:00:00.000Z';
      final profile = await savedProfile();
      expect(profile?['birthDate'], expected);
      // Kotlin's key is `birthDate`; nothing writes a bare `dob`.
      expect(profile?.containsKey('dob'), isFalse);
    });

    testWidgets('a blank first name does not block the save', (tester) async {
      // `createUserProfile` requires nothing but the year of birth, and only
      // when that block is on screen: a respondent who gives no name still
      // completes the survey.
      await pumpScreen(tester, showAdditionalFields: true, anonymous: true);

      await tapSave(tester);

      expect(await savedProfile(), <String, dynamic>{
        'language': 'English',
        'level': 'Beginner',
        'betaEnabled': false,
      });
      final row = await db.submissionDao.getById(submissionId);
      expect(row?.status, 'complete');
    });
  });

  group('save and cancel', () {
    testWidgets('a saved profile marks the submission complete and thanks', (
      tester,
    ) async {
      await pumpScreen(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Year of birth'),
        '1990',
      );
      await tapSave(tester);

      final row = await db.submissionDao.getById(submissionId);
      expect(row?.status, 'complete');
      expect(row?.uploaded, isFalse);
      expect(find.text('Thank you for taking this survey'), findsOneWidget);
    });

    testWidgets('the completed submission is queued for upload', (
      tester,
    ) async {
      await pumpScreen(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Year of birth'),
        '1990',
      );
      await tapSave(tester);
      await tester.pump(const Duration(milliseconds: 200));

      final queued = await db.outboxDao.findOpen(
        SubmissionsUploader.type,
        submissionId,
      );
      expect(queued, isNotNull);
    });

    testWidgets('cancel leaves the submission alone', (tester) async {
      await pumpScreen(tester);

      await tester.tap(find.widgetWithText(OutlinedButton, 'Cancel'));
      await tester.pumpAndSettle();

      expect(await savedProfile(), isNull);
      final row = await db.submissionDao.getById(submissionId);
      expect(row?.status, 'pending');
      expect(find.text('ROOT_PAGE'), findsOneWidget);
    });

    testWidgets('cancel pops false', (tester) async {
      // `public_survey_screen` reads this to decide whether to POST the answer
      // sheet: `PublicSurveyActivity.uploadCompletedSubmission` finds no
      // `complete` submission when the dialog is cancelled, so a cancel has to
      // send nothing.
      await pumpScreen(tester);

      await tester.tap(find.widgetWithText(OutlinedButton, 'Cancel'));
      await tester.pumpAndSettle();

      expect(popResults, [false]);
    });

    testWidgets('a saved profile pops true', (tester) async {
      await pumpScreen(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Year of birth'),
        '1990',
      );
      await tapSave(tester);

      expect(popResults, [true]);
    });
  });

  group('the signed-in profile is not a respondent profile', () {
    testWidgets('a session prefills nothing', (tester) async {
      // `initViews` sets no text on any field and the gender radios ship
      // unchecked, so `createUserProfile` reports only what was typed. The
      // port's prefill was dead code — `valueOrNull` from `initState` — and
      // waking it up would have filed the device owner's demographics as the
      // respondent's: `sessionProvider` restores the last account that logged
      // in on this device, which for a public-survey link is not the person
      // answering.
      await pumpScreen(tester, showAdditionalFields: true);

      expect(find.text('Ada'), findsNothing);
      expect(find.text('Lovelace'), findsNothing);
      expect(find.text('ada@example.org'), findsNothing);
      expect(find.text('555-0100'), findsNothing);
      // The two spinners still open on item 0, not on the profile's values.
      expect(find.text('English'), findsOneWidget);
      expect(find.text('Beginner'), findsOneWidget);
      expect(find.text('नेपाली'), findsNothing);
      expect(find.text('Expert'), findsNothing);
    });

    testWidgets('the stored document carries only what was typed', (
      tester,
    ) async {
      await pumpScreen(tester, showAdditionalFields: true);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'First name'),
        'Grace',
      );
      await tapSave(tester);

      // No `middleName`/`lastName`/`email`/`phoneNumber` from the session, and
      // no `gender` — the respondent tapped neither radio.
      expect(await savedProfile(), <String, dynamic>{
        'firstName': 'Grace',
        'language': 'English',
        'level': 'Beginner',
        'betaEnabled': false,
      });
    });

    testWidgets('an off-vocabulary level cannot take the screen down', (
      tester,
    ) async {
      // A regression guard rather than a live bug now: a
      // `DropdownButtonFormField` whose value is not among its items asserts
      // out of `build`, which is what a profile carrying `College` (this
      // screen's own earlier writes) would have done to any prefill.
      await pumpScreen(
        tester,
        showAdditionalFields: true,
        session: buildUserRow(
          id: 'user-2',
          name: 'mo',
          firstName: 'Mo',
          language: 'Klingon',
          level: 'College',
        ),
      );

      expect(tester.takeException(), isNull);
      expect(find.text('English'), findsOneWidget);
      expect(find.text('Beginner'), findsOneWidget);
    });

    testWidgets('a rejecting session still saves the profile', (tester) async {
      // The submission must be marked complete whatever the session does; the
      // queue step is the only part that needs a user, and its failure is not
      // a failure to save.
      await pumpScreen(tester, failingSession: true);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Year of birth'),
        '1990',
      );
      await tapSave(tester);

      final row = await db.submissionDao.getById(submissionId);
      expect(row?.status, 'complete');
      expect(find.text('Error saving profile'), findsNothing);
    });
  });

  group('the option lists', () {
    testWidgets('the dropdowns offer the ported member vocabularies', (
      tester,
    ) async {
      // `R.array.level` is Beginner/Intermediate/Advanced/Expert and
      // `R.array.language` carries the native names — both already ported as
      // `memberLevels`/`memberLanguages` for `become_member_screen`. This
      // screen used to hand-roll its own, so it wrote a `level` from a
      // vocabulary nothing else in either app uses.
      await pumpScreen(tester, showAdditionalFields: true, anonymous: true);

      await tester.tap(find.byType(DropdownButtonFormField<String>).first);
      await tester.pumpAndSettle();
      for (final language in memberLanguages) {
        expect(find.text(language), findsWidgets, reason: language);
      }
      expect(find.text('Spanish'), findsNothing);
      await tester.tap(find.text(memberLanguages.first).last);
      await tester.pumpAndSettle();

      await tester.tap(find.byType(DropdownButtonFormField<String>).last);
      await tester.pumpAndSettle();
      for (final level in memberLevels) {
        expect(find.text(level), findsWidgets, reason: level);
      }
      expect(find.text('High School'), findsNothing);
    });
  });

  group('userSurveyProfileJson', () {
    test('omits every empty field but keeps betaEnabled', () {
      expect(userSurveyProfileJson(), {'betaEnabled': false});
    });

    test('carries the Kotlin keys in the Kotlin order', () {
      final profile = userSurveyProfileJson(
        fname: 'Ada',
        mName: 'B',
        lname: 'Lovelace',
        email: 'ada@example.org',
        language: 'English',
        phone: '555-0100',
        dob: '1990-05-02',
        level: 'Expert',
        gender: 'female',
        now: DateTime(2026),
      );
      expect(profile.keys.toList(), [
        'firstName',
        'middleName',
        'lastName',
        'email',
        'language',
        'phoneNumber',
        'birthDate',
        'level',
        'gender',
        'betaEnabled',
      ]);
      expect(profile['birthDate'], '1990-05-02T00:00:00.000Z');
    });

    test('turns a year of birth into an age string', () {
      final profile = userSurveyProfileJson(yob: '2000', now: DateTime(2026));
      expect(profile, {'age': '26', 'betaEnabled': false});
    });

    test('a year of birth that will not parse is dropped, not sent raw', () {
      expect(userSurveyProfileJson(yob: 'nineteen'), {'betaEnabled': false});
    });
  });
}
