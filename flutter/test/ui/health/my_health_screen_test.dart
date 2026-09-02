import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/health_repository.dart';
import 'package:myplanet/ui/components/profile_avatar.dart';
import 'package:myplanet/ui/health/my_health_screen.dart';

import '../../support/widget_harness.dart';

/// Screen tests for `MyHealthScreen` — 955 lines that had none, the port's
/// largest untested hand-written surface after the resource viewer.
///
/// These drive the real provider graph (`patientDetailProvider` →
/// `loggedInUserProvider` → `sessionProvider` → the in-memory database) rather
/// than faking the state, so the encryption round trip the screen depends on is
/// exercised end to end: `ensureSecurityKeys` + `encryptData` build the same
/// blobs `HealthRepository.sync` would have written.
class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

class _NoopApi extends Mock implements PlanetApi {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  HealthRepository repoFor(AppDatabase db) {
    var counter = 0;
    return HealthRepository(
      _NoopApi(),
      db.healthExaminationDao,
      db.userDao,
      createId: () => 'health-local-${++counter}',
    );
  }

  /// Seeds [db] with a patient and returns the row the session will report.
  ///
  /// [name] is deliberately a free parameter: the display-name fallback reads
  /// it verbatim when every real name field is absent, which is where the
  /// whitespace regression below lives.
  Future<UserRow> seedPatient(
    AppDatabase db, {
    String id = 'user-a',
    String? name = 'alice',
    String? firstName = 'Alice',
    String? lastName = 'Smith',
    String? email = 'alice@example.com',
    String? userImage,
    List<String> roles = const [],
  }) async {
    await db.userDao.upsert(
      UsersCompanion.insert(
        id: id,
        couchId: Value(id),
        name: Value(name),
        firstName: Value(firstName),
        lastName: Value(lastName),
        email: Value(email),
        phoneNumber: const Value('+254700'),
        birthPlace: const Value('Nairobi'),
        language: const Value('English'),
        gender: const Value('female'),
        dob: const Value('1990-06-15'),
        userImage: Value(userImage),
        rolesList: Value(roles),
        joinDate: const Value(1000),
      ),
    );
    return (await db.userDao.getById(id))!;
  }

  /// Writes the encrypted health profile plus [examinations] for [userId],
  /// in the row shape a completed health sync leaves behind.
  ///
  /// That shape matters and is easy to get wrong: `_companionFromDoc` maps
  /// `userId` to the document's *own* `_id`, so only the profile document — the
  /// one whose `_id` is the patient's user key — has `userId == patient`. The
  /// examinations carry their own ids and point back through `profileId`. Give
  /// two rows the patient's `userId` and `getByIdOrUserId`'s `getSingleOrNull`
  /// throws, which is why this writes rows directly rather than looping
  /// `createExamination`.
  Future<void> seedHealthRecord(
    AppDatabase db,
    HealthRepository repo, {
    String userId = 'user-a',
    Map<String, dynamic> profile = const {
      'specialNeeds': 'Wheelchair access',
      'notes': 'Lactose intolerant',
      'emergencyContactName': 'Jane Doe',
      'emergencyContactType': 'Sister',
      'emergencyContact': '555-1234',
    },
    List<Map<String, dynamic>> examinations = const [],
  }) async {
    await db.userDao.ensureSecurityKeys(userId);
    final profileJson = jsonEncode({
      'profile': profile,
      'userKey': userId,
      'lastExamination': 0,
    });
    await db.healthExaminationDao.upsert(
      HealthExaminationsCompanion.insert(
        id: userId,
        userId: Value(userId),
        profileId: Value(userId),
        temperature: const Value(36.5),
        pulse: const Value(70),
        height: const Value(170),
        weight: const Value(65),
        bp: const Value('120/80'),
        vision: const Value('20/20'),
        hearing: const Value('Normal'),
        date: Value(DateTime(2026, 3, 14).millisecondsSinceEpoch),
        data: Value(await repo.encryptData(userId, profileJson)),
      ),
    );
    var index = 0;
    for (final exam in examinations) {
      final examId = 'exam-${++index}';
      await db.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: examId,
          userId: Value(examId),
          profileId: Value(userId),
          // `saveData` sets `creatorId` to the patient's `health.userKey`,
          // which `seedHealthRecord` keys as the patient id — the examiner
          // lives in the encrypted `data`, not here. A test can override it.
          creatorId: Value(
            exam['creatorId'] as String? ?? exam['createdBy'] as String?,
          ),
          temperature: Value((exam['temperature'] as num?)?.toDouble() ?? 37.0),
          pulse: Value(exam['pulse'] as int? ?? 72),
          height: const Value(170),
          weight: const Value(65),
          bp: Value(exam['bp'] as String?),
          date: Value(DateTime(2026, 4, index + 1).millisecondsSinceEpoch),
          data: Value(await repo.encryptData(userId, jsonEncode(exam))),
        ),
      );
    }
  }

  /// Scrolls the body until [finder] mounts.
  ///
  /// The screen's body is a `ListView(children: [...])`, which builds the child
  /// *widgets* eagerly but only mounts the ones inside the viewport — and
  /// `find.text` searches the element tree. With a full profile the vitals and
  /// history cards start below the 600px test viewport, so asserting on them
  /// without scrolling reports "Found 0 widgets" for content the screen renders
  /// perfectly well on a device.
  Future<void> scrollTo(WidgetTester tester, Finder finder) async {
    await tester.scrollUntilVisible(
      finder,
      120,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
  }

  Future<AppDatabase> pumpScreen(
    WidgetTester tester, {
    required UserRow? session,
    AppDatabase? database,
  }) async {
    final db = database ?? AppDatabase.memory();
    await tester.pumpWidget(
      wrapScreen(
        const MyHealthScreen(),
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(session)),
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          healthRepositoryProvider.overrideWith((ref) => repoFor(db)),
        ],
        pushTargets: {
          '/health/add': (_) => const Scaffold(body: Text('add-health')),
          '/health/examination': (_) =>
              const Scaffold(body: Text('add-examination')),
        },
      ),
    );
    await tester.pumpAndSettle();
    return db;
  }

  group('patient profile', () {
    testWidgets('renders the patient name, email and profile fields', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await pumpScreen(tester, session: user, database: db);

      expect(find.text('Alice Smith'), findsOneWidget);
      expect(find.text('alice@example.com'), findsOneWidget);
      expect(find.text('1990-06-15'), findsOneWidget);
      expect(find.text('Nairobi'), findsOneWidget);
      expect(find.text('+254700'), findsOneWidget);
    });

    testWidgets('reports the record as unavailable when no patient resolves', (
      tester,
    ) async {
      await pumpScreen(tester, session: null);
      expect(find.text('Health record not available'), findsOneWidget);
    });

    /// Regression: the screen used to build its own avatar with
    /// `NetworkImage(user.userImage!)`. `users.userImage` holds a CouchDB
    /// *attachment name*, not a URL — `UserMapper` says so where it writes the
    /// column — and the attachment sits behind Basic auth, which
    /// `Image.network` cannot send. The photo could therefore never load. The
    /// shared [ProfileAvatar] resolves the name through the authenticated bytes
    /// path and falls back to initials, which is what every other screen uses.
    testWidgets('renders the photo through the shared authenticated avatar', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db, userImage: 'photo.jpg');
      await pumpScreen(tester, session: user, database: db);

      expect(find.byType(ProfileAvatar), findsWidgets);
      final networkImages = find.byWidgetPredicate(
        (w) => w is CircleAvatar && w.backgroundImage is NetworkImage,
      );
      expect(
        networkImages,
        findsNothing,
        reason:
            'an attachment name is not a URL, and a CouchDB attachment needs '
            'the auth header NetworkImage cannot send',
      );
    });
  });

  group('display name fallbacks', () {
    /// Regression: `_getInitials` did `parts[0][0]` on `name.split(' ')`, and
    /// its `parts.isEmpty` guard was dead code — `''.split(' ')` is `['']`, so
    /// the list is never empty. A username with a leading or trailing space
    /// (the fallback path never trimmed it) produced an empty first or last
    /// part and threw `RangeError` out of `build`, taking the whole health
    /// screen down. The shared helpers filter blanks before indexing.
    testWidgets('a whitespace-padded username does not crash the screen', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(
        db,
        name: ' jane ',
        firstName: null,
        lastName: null,
      );
      await pumpScreen(tester, session: user, database: db);

      expect(tester.takeException(), isNull);
      expect(find.text('jane'), findsOneWidget);
    });

    /// Regression: the fallback was a hardcoded English `'Unknown'`, in a file
    /// that already reads `l10n.unknown` two screens down. Phases 57–58
    /// retired the port's hardcoded strings; this one survived because it sat
    /// in a helper rather than a widget.
    testWidgets('a user with no name at all falls back without hardcoding', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(
        db,
        name: null,
        firstName: null,
        lastName: null,
      );
      await pumpScreen(tester, session: user, database: db);

      expect(tester.takeException(), isNull);
      expect(find.text('Unknown'), findsNothing);
      expect(find.text(displayName(user)), findsOneWidget);
    });
  });

  group('health record', () {
    testWidgets('renders the health profile and vital signs', (tester) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await seedHealthRecord(db, repoFor(db));
      await pumpScreen(tester, session: user, database: db);

      expect(find.text('Wheelchair access'), findsOneWidget);
      expect(find.text('Lactose intolerant'), findsOneWidget);
      expect(find.text('Jane Doe (Sister)'), findsOneWidget);
      expect(find.text('555-1234'), findsOneWidget);

      await scrollTo(tester, find.text('36.5°C'));
      expect(find.text('36.5°C'), findsOneWidget);
      expect(find.text('70 bpm'), findsOneWidget);
      expect(find.text('170.0 cm'), findsOneWidget);
      expect(find.text('120/80'), findsOneWidget);
      expect(find.text('20/20'), findsOneWidget);
    });

    testWidgets('labels an examination the patient recorded themselves', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await seedHealthRecord(db, repoFor(db));
      await pumpScreen(tester, session: user, database: db);

      await scrollTo(tester, find.text('Self-examination'));
      expect(find.text('Self-examination'), findsWidgets);
    });

    /// A creator id that is not a known user falls back to the text after the
    /// colon, so `org.couchdb.user:provider-1` reads as `provider-1` rather
    /// than the raw document id.
    testWidgets('names an unknown examiner from the id after the colon', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      final repo = repoFor(db);
      await seedHealthRecord(
        db,
        repo,
        examinations: [
          {
            'temperature': 38.2,
            'pulse': 88,
            'notes': 'Fever',
            'createdBy': 'org.couchdb.user:provider-1',
          },
        ],
      );
      await pumpScreen(tester, session: user, database: db);

      await scrollTo(tester, find.text('provider-1'));
      expect(find.text('provider-1'), findsOneWidget);
    });

    /// The examiner is `getString("createdBy", encrypted)` in
    /// `submitExaminations` — inside the record's encrypted `data`. The
    /// `creatorId` column is not it: `saveData` sets it to the patient's
    /// `health.userKey`, so reading the examiner off the column named a cipher
    /// key when it differed from the patient id, and reported a provider's
    /// examination as a self-examination when it equalled it.
    testWidgets('reads the examiner from the decrypted record', (tester) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await seedHealthRecord(
        db,
        repoFor(db),
        examinations: [
          {
            'temperature': 38.2,
            'pulse': 88,
            'createdBy': 'org.couchdb.user:provider-1',
            // What the column actually carries: the patient's profile key.
            'creatorId': 'user-a',
          },
        ],
      );
      await pumpScreen(tester, session: user, database: db);

      // Reading the column instead would have made this card a
      // self-examination and rendered no name at all. (The strip also carries
      // a self-examination card for the profile row, which `seedHealthRecord`
      // gives a `profileId` — the app's own profile row has none.)
      await scrollTo(tester, find.text('provider-1'));
      expect(find.text('provider-1'), findsOneWidget);
    });

    /// Regression: the history strip was 140px tall, 8px short of a card
    /// carrying date, examiner, temperature, pulse, blood pressure and the
    /// has-info icon together — a `RenderFlex` overflow, which renders as the
    /// yellow-and-black stripe on a device and throws in a widget test. Only a
    /// fully-populated examination trips it, which is why nothing caught it.
    testWidgets('a fully-populated examination card does not overflow', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await seedHealthRecord(
        db,
        repoFor(db),
        examinations: [
          {
            'temperature': 38.2,
            'pulse': 88,
            'bp': '130/85',
            'notes': 'Fever',
            'createdBy': 'org.couchdb.user:provider-1',
          },
        ],
      );
      await pumpScreen(tester, session: user, database: db);

      await scrollTo(tester, find.text('provider-1'));
      expect(tester.takeException(), isNull);
    });

    testWidgets('opens the detail dialog with the decrypted fields', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      final repo = repoFor(db);
      await seedHealthRecord(
        db,
        repo,
        examinations: [
          {
            'temperature': 38.2,
            'pulse': 88,
            'notes': 'Patient reports chest pain',
            'diagnosis': 'Hypertension',
            'medications': 'Lisinopril 10mg',
            'createdBy': 'org.couchdb.user:provider-1',
          },
        ],
      );
      await pumpScreen(tester, session: user, database: db);

      await scrollTo(tester, find.text('provider-1'));
      await tester.tap(find.text('provider-1'));
      await tester.pumpAndSettle();

      expect(find.byType(AlertDialog), findsOneWidget);
      expect(find.text('Patient reports chest pain'), findsOneWidget);
      expect(find.text('Hypertension'), findsOneWidget);
      expect(find.text('Lisinopril 10mg'), findsOneWidget);
    });
  });

  group('actions', () {
    testWidgets('offers add-record to a user without the health role', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await pumpScreen(tester, session: user, database: db);

      expect(find.text('Add health record'), findsOneWidget);
      expect(find.text('New patient'), findsNothing);
    });

    testWidgets('offers a health provider both buttons', (tester) async {
      // The layout carries `btnnewPatient` *and* `addNewRecord`, and
      // `setupButtons` hides only the first from a non-provider. Offering one
      // or the other left the health role — whose whole purpose is recording
      // other people's examinations — with no way to record one.
      final db = AppDatabase.memory();
      final user = await seedPatient(db, roles: const ['health']);
      await pumpScreen(tester, session: user, database: db);

      expect(find.text('New patient'), findsOneWidget);
      expect(find.text('Add health record'), findsOneWidget);

      await tester.tap(find.text('New patient'));
      await tester.pumpAndSettle();
      expect(find.byType(AlertDialog), findsOneWidget);
    });

    /// Regression: `RefreshIndicator.onRefresh` was an empty async body with a
    /// `// Refresh health data` comment, so the gesture the screen advertises
    /// did nothing at all. Kotlin's `MyHealthFragment` has no
    /// `SwipeRefreshLayout`, so there is no upstream behaviour to copy — the
    /// affordance is the port's own, and it has to mean something.
    testWidgets('pull to refresh reloads the selected patient', (tester) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await pumpScreen(tester, session: user, database: db);

      expect(find.text('Alice Smith'), findsOneWidget);

      // Rename the patient behind the screen's back, then pull to refresh: a
      // working handler re-reads the row, a no-op one leaves the stale name.
      await seedPatient(db, firstName: 'Alicia');
      await tester.fling(
        find.byType(RefreshIndicator),
        const Offset(0, 300),
        1000,
      );
      await tester.pumpAndSettle();

      expect(find.text('Alicia Smith'), findsOneWidget);
    });
  });
}
