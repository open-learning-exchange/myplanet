import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'dart:io';

import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/health_repository.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';
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

/// Every send fails as a transport error, which the drainer treats as
/// retryable — so a row it touches stays queued rather than being abandoned or
/// deleted.
class _UnreachableApi extends Mock implements PlanetApi {
  @override
  Future<NetworkResult<Map<String, dynamic>>> sendJsonObject(
    String url, {
    required Map<String, dynamic> body,
    String method = 'POST',
    String? authHeader,
  }) async => const NetworkException(SocketException('offline'));
}

class _TestServerConfigNotifier extends ServerConfigNotifier {
  @override
  ServerConfig? build() => const ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );
}

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
    String? couchId,
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
        // A member registered on this device keeps its local id and gains a
        // `couchId` when the upload lands, so the two are not always equal.
        couchId: Value(couchId ?? id),
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
    List<Override> overrides = const [],
  }) async {
    final db = database ?? AppDatabase.memory();
    await tester.pumpWidget(
      wrapScreen(
        const MyHealthScreen(),
        overrides: [
          ...overrides,
          sessionProvider.overrideWith(() => _TestSessionNotifier(session)),
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          healthRepositoryProvider.overrideWith((ref) => repoFor(db)),
        ],
        pushTargets: {
          // The query is rendered so a test can assert which patient the
          // editors were handed.
          '/health/add': (context) => Scaffold(
            body: Text('add-health?${GoRouterState.of(context).uri.query}'),
          ),
          '/health/examination': (context) => Scaffold(
            body: Text(
              'add-examination?${GoRouterState.of(context).uri.query}',
            ),
          ),
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
    /// `showAlert`'s Edit is `putExtra("userId", mh._id)` — the profile row's
    /// own id, the one that row was created under. The *user* row's `id` is
    /// not interchangeable with it: for a member registered on this device the
    /// two differ, and handing over the user id makes the form miss the
    /// profile row, mint a second one under a new key, and drop the
    /// examination it is editing out of the patient's record.
    testWidgets('editing an examination carries the profile row id', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(
        db,
        id: '1750000000000',
        couchId: 'org.couchdb.user:jane',
      );
      await seedHealthRecord(
        db,
        repoFor(db),
        userId: 'org.couchdb.user:jane',
        examinations: [
          {'temperature': 38.2, 'pulse': 88},
        ],
      );
      await pumpScreen(tester, session: user, database: db);

      // A tall surface rather than `scrollTo`: dragging the body far enough
      // to reach the history strip pulls the `RefreshIndicator`, whose reload
      // swaps the whole body for a spinner mid-drag and leaves
      // `dragUntilVisible` with no scrollable to hold on to.
      tester.view.physicalSize = const Size(1200, 4000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpAndSettle();

      // The card labels its vitals (`Temp: 38.2°C`), so this is a substring.
      await tester.tap(find.textContaining('38.2°C'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, 'Edit'));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('userId=org.couchdb.user%3Ajane'),
        findsOneWidget,
      );
    });

    /// `updateHealth` is `putExtra("userId", userId)`, the selected patient —
    /// the port sent nobody and the editor fell back to the signed-in user.
    testWidgets('the profile editor carries the selected patient', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(
        db,
        id: '1750000000000',
        couchId: 'org.couchdb.user:jane',
      );
      await pumpScreen(tester, session: user, database: db);

      await tester.tap(find.byIcon(Icons.edit));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('userId=org.couchdb.user%3Ajane'),
        findsOneWidget,
      );
    });

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

  group('records the server refused', () {
    // The outbox classifies a 409 as permanent, so a health record whose `_id`
    // collides with another document is abandoned on its first attempt. Nothing
    // read that state before — the reading stayed on the handset while the
    // screen listed it as recorded.
    Future<void> abandon(AppDatabase db, String itemId) async {
      await db.outboxDao.upsert(
        OutboxEntriesCompanion.insert(
          id: 'op-$itemId',
          uploadType: 'health',
          itemId: itemId,
          payload: '{}',
          endpoint: 'https://planet.example/db/health',
          createdAt: 1000,
          status: const Value('abandoned'),
          httpCode: const Value(409),
        ),
      );
    }

    testWidgets('nothing is said when every record was delivered', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await pumpScreen(tester, session: user, database: db);

      expect(find.byIcon(Icons.cloud_off), findsNothing);
    });

    testWidgets('a stranded record is reported on the screen', (tester) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await abandon(db, 'health-1');
      await pumpScreen(tester, session: user, database: db);
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.cloud_off), findsOneWidget);
      expect(
        find.textContaining('1 health record could not be sent'),
        findsOneWidget,
      );
    });

    testWidgets('pull to refresh picks up a newly stranded record', (
      tester,
    ) async {
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await pumpScreen(tester, session: user, database: db);
      await tester.pumpAndSettle();
      expect(find.byIcon(Icons.cloud_off), findsNothing);

      // A drain finished behind the screen's back and gave up on a record.
      await abandon(db, 'health-1');
      await tester.fling(
        find.byType(RefreshIndicator),
        const Offset(0, 300),
        1000,
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.cloud_off), findsOneWidget);
    });

    testWidgets('the caution shows even when no patient resolves', (
      tester,
    ) async {
      // "No patient resolves" is exactly the state a clinician might be in
      // while wondering where a reading went, and the refusal is a
      // device-level fact rather than a property of the selected patient.
      final db = AppDatabase.memory();
      await abandon(db, 'health-1');
      await pumpScreen(tester, session: null, database: db);
      await tester.pumpAndSettle();

      expect(find.text('Health record not available'), findsOneWidget);
      expect(find.byIcon(Icons.cloud_off), findsOneWidget);
    });

    testWidgets('the caution offers a retry that re-queues the record', (
      tester,
    ) async {
      // Kotlin re-attempts every failed health upload on each sync; the port
      // re-queues only from the examination form's save, so without this the
      // banner names a problem the clinician has no way to act on.
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await db.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'health-1',
          userId: const Value('health-1'),
          pulse: const Value(72),
          isUpdated: const Value(true),
        ),
      );
      await abandon(db, 'health-1');
      await pumpScreen(
        tester,
        session: user,
        database: db,
        // `serverConfigProvider` reaches `planetPrefsProvider`, which the
        // widget-test harness leaves as `UnimplementedError` — the Phase 75
        // trap. The retry reads it to decide whether it can drain.
        overrides: [
          serverConfigProvider.overrideWith(_TestServerConfigNotifier.new),
          // `outboxDrainerProvider` builds every uploader, several of which
          // reach `planetPrefsProvider` too. A drainer over an unreachable
          // server exercises the same path: the send fails as a transport
          // error, which is retryable, so the fresh operation stays queued.
          outboxDrainerProvider.overrideWith(
            (ref) => OutboxDrainer(
              _UnreachableApi(),
              OutboxRepository(db.outboxDao),
            ),
          ),
        ],
      );
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(TextButton, 'Retry'));
      await tester.pumpAndSettle();

      // A fresh operation alongside the abandoned one, because `enqueue`
      // ignores an abandoned row rather than reusing it — which is what makes
      // the retry a real re-attempt.
      final open = await db.outboxDao.findOpen('health', 'health-1');
      expect(open, isNotNull);
      expect(open!.status, OutboxDao.statusPending);
    });

    testWidgets('the count is of records, not of attempts', (tester) async {
      // A doomed record earns a fresh abandoned row on every save, because
      // `enqueue` only looks for an *open* operation. Counting rows would show
      // a number that climbs while nothing new is wrong.
      final db = AppDatabase.memory();
      final user = await seedPatient(db);
      await abandon(db, 'health-1');
      await db.outboxDao.upsert(
        OutboxEntriesCompanion.insert(
          id: 'op-retry',
          uploadType: 'health',
          itemId: 'health-1',
          payload: '{}',
          endpoint: 'https://planet.example/db/health',
          createdAt: 2000,
          status: const Value('abandoned'),
          httpCode: const Value(409),
        ),
      );
      await pumpScreen(tester, session: user, database: db);
      await tester.pumpAndSettle();

      expect(
        find.textContaining('1 health record could not be sent'),
        findsOneWidget,
      );
    });
  });
}
