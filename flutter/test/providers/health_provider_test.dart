import 'dart:async';
import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/utils/time_utils.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/health_models.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/health_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/health_repository.dart';
import 'package:myplanet/repository/health_uploader.dart';

/// First tests for `providers/health_provider.dart` — 572 lines that had none.
///
/// These drive the real repository against an in-memory database rather than a
/// mock, because the defects here are all in the *shape* of what gets written:
/// which row an examination points at, which key encrypts it, and which user
/// it names. A mocked repository would have recorded the same wrong calls
/// happily. It is the same reason Phase 103 rewrote the achievements uploader's
/// setup — a test that hand-patches the row it wants has stopped checking
/// whether the app can produce that row, and `my_health_screen_test`'s
/// `seedHealthRecord` says so in a comment: it writes examinations directly
/// "rather than looping `createExamination`", because looping it threw.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  HealthRepository repoFor(AppDatabase db) {
    var counter = 0;
    return HealthRepository(
      _NoopApi(),
      db.healthExaminationDao,
      db.userDao,
      createId: () => 'exam-local-${++counter}',
    );
  }

  Future<UserRow> seedUser(
    AppDatabase db, {
    required String id,
    String? couchId,
    String? name,
    String? gender = 'female',
    String? dob = '1990-06-15',
    String? planetCode = 'guatemala',
    List<String> roles = const [],
    int joinDate = 1000,
  }) async {
    await db.userDao.upsert(
      UsersCompanion.insert(
        id: id,
        couchId: Value(couchId ?? id),
        name: Value(name ?? id),
        firstName: Value(name ?? id),
        gender: Value(gender),
        dob: Value(dob),
        planetCode: Value(planetCode),
        rolesList: Value(roles),
        joinDate: Value(joinDate),
      ),
    );
    return (await db.userDao.getById(id))!;
  }

  ProviderContainer containerFor(
    AppDatabase db, {
    UserRow? session,
    HealthRepository? repository,
  }) {
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWith((ref) {
          ref.onDispose(db.close);
          return db;
        }),
        healthRepositoryProvider.overrideWithValue(repository ?? repoFor(db)),
        serverConfigProvider.overrideWith(_TestConfig.new),
        sessionProvider.overrideWith(() => _TestSessionNotifier(session)),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  /// Spins the event loop until [done], for the paths whose work is kicked off
  /// by a constructor and cannot be awaited from outside it.
  Future<void> waitUntil(bool Function() done, {String? reason}) async {
    for (var i = 0; i < 500; i++) {
      if (done()) return;
      await Future<void>.delayed(Duration.zero);
    }
    fail(reason ?? 'the condition never became true');
  }

  /// The record as the screen reads it: `selectPatient` re-reads the patient
  /// before decrypting, and the key/iv only exist once a write has minted
  /// them, so a row captured before the save cannot read its own blob.
  Future<HealthRecord?> recordFor(
    AppDatabase db,
    HealthRepository repo,
    String id,
  ) async {
    final user = await db.userDao.getById(id);
    return repo.getPatientHealthRecords(id, user!);
  }

  /// The arguments `HealthExaminationActivity` collects, with only the ones a
  /// test cares about spelled out at the call site.
  Future<void> saveExamination(
    ExaminationNotifier notifier, {
    double temperature = 36.5,
    int pulse = 70,
    String? bp = '120/80',
    double height = 170,
    double weight = 65,
    String? allergies,
    String? diagnosis,
    Map<String, bool> conditions = const {},
  }) => notifier.save(
    temperature: temperature,
    pulse: pulse,
    bp: bp,
    height: height,
    weight: weight,
    vision: null,
    hearing: null,
    allergies: allergies,
    diagnosis: diagnosis,
    medications: null,
    immunizations: null,
    treatments: null,
    notes: null,
    referrals: null,
    tests: null,
    xrays: null,
    conditions: conditions,
  );

  ExaminationNotifier notifierFor(
    HealthRepository repo, {
    String? patientId,
    String? examId,
    UserRow? signedIn,
    Future<void> Function()? onSaved,
  }) {
    final notifier = ExaminationNotifier(
      repo,
      patientId,
      examId,
      onSaved: onSaved,
      currentUser: signedIn == null ? null : () async => signedIn,
    );
    addTearDown(notifier.dispose);
    return notifier;
  }

  group('patientIdOf', () {
    UserRow user({String id = 'local-1', String? couchId}) => UserRow(
      id: id,
      couchId: couchId,
      rolesList: const [],
      userAdmin: false,
      joinDate: 0,
      isArchived: false,
      isUpdated: false,
    );

    test('prefers the CouchDB id, as `_id ?: id` does', () {
      expect(
        patientIdOf(user(couchId: 'org.couchdb.user:ada')),
        'org.couchdb.user:ada',
      );
      expect(patientIdOf(user()), 'local-1');
      expect(patientIdOf(user(couchId: '')), 'local-1');
    });

    test('trims, so a whitespace-only id reads as absent', () {
      // `normalizedId = uid?.trim()`, then `if (!normalizedId.isNullOrEmpty())`
      // — a `_id` of " " selects nobody rather than selecting a blank patient.
      expect(patientIdOf(user(couchId: '  ')), isEmpty);
      expect(patientIdOf(user(couchId: ' ada ')), 'ada');
    });
  });

  group('PatientSort', () {
    test('maps the spinner indices `sortList` maps', () {
      // 0 -> joinDate desc, 1 -> joinDate asc, 2 -> name asc, 3 -> name desc.
      expect(PatientSort.values, hasLength(4));
      expect(PatientSort.joinDateDesc.fieldName, 'joinDate');
      expect(PatientSort.joinDateDesc.descending, isTrue);
      expect(PatientSort.joinDateAsc.fieldName, 'joinDate');
      expect(PatientSort.joinDateAsc.descending, isFalse);
      expect(PatientSort.nameAsc.fieldName, 'name');
      expect(PatientSort.nameAsc.descending, isFalse);
      expect(PatientSort.nameDesc.fieldName, 'name');
      expect(PatientSort.nameDesc.descending, isTrue);
    });
  });

  group('loggedInUserProvider / isHealthProviderProvider', () {
    test('no session resolves to no user and no health role', () async {
      final container = containerFor(AppDatabase.memory());
      expect(await container.read(loggedInUserProvider.future), isNull);
      expect(await container.read(isHealthProviderProvider.future), isFalse);
    });

    test('the health role is read off `rolesList`', () async {
      final db = AppDatabase.memory();
      final provider = await seedUser(db, id: 'p-1', roles: const ['health']);
      final container = containerFor(db, session: provider);
      expect(await container.read(isHealthProviderProvider.future), isTrue);
    });

    test('a learner is not a health provider', () async {
      final db = AppDatabase.memory();
      final learner = await seedUser(db, id: 'l-1', roles: const ['learner']);
      final container = containerFor(db, session: learner);
      expect(await container.read(isHealthProviderProvider.future), isFalse);
    });

    test('a member registered on this device is still found', () async {
      // `UserDao.getById` is `WHERE id = :id OR _id = :id`: a member created
      // by `become_member_screen` keeps its local `'<millis>'` id and gains a
      // `couchId` when the upload lands, while `patientIdOf` hands over the
      // `couchId`. Matching `id` alone lost every such account — the user
      // could not be resolved at all, so their health screen reported "health
      // record not available" for their own record.
      final db = AppDatabase.memory();
      final member = await seedUser(
        db,
        id: '1750000000000',
        couchId: 'org.couchdb.user:ada',
        roles: const ['health'],
      );
      final container = containerFor(db, session: member);
      expect(patientIdOf(member), 'org.couchdb.user:ada');
      expect(
        await container
            .read(healthRepositoryProvider)
            .getPatientById(patientIdOf(member)),
        isNotNull,
      );
      expect(await container.read(isHealthProviderProvider.future), isTrue);
    });
  });

  group('HealthRepository crypto for a locally-registered member', () {
    test('the key/iv are minted for a member addressed by their _id', () async {
      // `ensureUserSecurityKeys` upserts the entity it resolved;
      // `ensureSecurityKeys` wrote back with `WHERE id = <the argument>`, so
      // for a member whose local id and `_id` differ nothing was written, the
      // re-read still had no key, and every examination recorded for them
      // stored a null blob — the notes, diagnosis and medications silently
      // never reached the row.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      await seedUser(db, id: '1750000000000', couchId: 'org.couchdb.user:ada');

      final blob = await repo.encryptData(
        'org.couchdb.user:ada',
        jsonEncode({'notes': 'Lactose intolerant'}),
      );
      expect(blob, isNotNull);
      expect(
        await repo.decryptData('org.couchdb.user:ada', blob),
        contains('Lactose intolerant'),
      );
      final stored = await db.userDao.getById('1750000000000');
      expect(stored?.key, isNotNull);
      expect(stored?.iv, isNotNull);
    });
  });

  group('healthDataProvider', () {
    test('reads the patient it is keyed by, not the signed-in user', () async {
      // `loadHealthData(userId)` takes the id `MyHealthFragment` passes, which
      // for a health provider is the *selected* patient. As a session-scoped
      // provider this could only ever describe the signed-in user, so
      // `AddHealthActivity`'s form had no way to reach the patient it was
      // opened on — it loaded, and saved over, the provider's own profile.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final provider = await seedUser(
        db,
        id: 'prov-1',
        name: 'Provider',
        roles: const ['health'],
      );
      await seedUser(db, id: 'pat-1', name: 'Patient');
      await repo.saveHealthProfileBlob(
        'pat-1',
        MyHealth(
          userKey: 'key-pat-1',
          profile: MyHealthProfile(specialNeeds: 'Wheelchair access'),
        ),
      );

      final container = containerFor(db, session: provider, repository: repo);
      final data = await container.read(healthDataProvider('pat-1').future);

      expect(data?.user?.name, 'Patient');
      expect(data?.myHealth?.profile?.specialNeeds, 'Wheelchair access');
    });

    test('an empty id resolves to null rather than querying', () async {
      final db = AppDatabase.memory();
      final container = containerFor(db);
      expect(await container.read(healthDataProvider('').future), isNull);
    });

    test('the profile blob is decrypted, not parsed as JSON', () async {
      // `data` is AES ciphertext exactly as `HealthExaminationActivity` writes
      // it; parsing it as JSON always threw and the catch turned every health
      // record into a blank screen.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-2');
      await repo.saveHealthProfileBlob(
        'pat-2',
        MyHealth(
          userKey: 'k',
          profile: MyHealthProfile(notes: 'Lactose'),
        ),
      );
      final stored = await repo.getByIdOrUserId('pat-2');
      expect(stored?.data, isNot(contains('Lactose')));

      final container = containerFor(db, session: patient, repository: repo);
      final data = await container.read(healthDataProvider('pat-2').future);
      expect(data?.myHealth?.profile?.notes, 'Lactose');
    });
  });

  group('ExaminationNotifier.save', () {
    test('a recorded examination is one the screen can find', () async {
      // The headline defect. `getPatientHealthRecords` lists a patient's
      // examinations with `getByProfileId(health.userKey)` — the link
      // `saveData` builds through `examination.profileId = health?.userKey`.
      // Saving without one wrote a row nothing could reach: the record
      // vanished the moment it was taken, with no error anywhere.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1');
      final notifier = notifierFor(repo, patientId: 'pat-1', signedIn: patient);
      await notifier.loaded;

      await saveExamination(notifier, allergies: 'peanuts');

      final record = await recordFor(db, repo, 'pat-1');
      expect(record?.examinations, hasLength(1));
      expect(record!.examinations.single.temperature, 36.5);
    });

    test('two examinations do not break the patient lookup', () async {
      // Giving each examination the patient's `userId` made
      // `getByIdOrUserId`'s `getSingleOrNull` throw as soon as a second one
      // existed — and `selectPatient` swallows that, so the screen simply
      // stopped updating. Kotlin's `_id`/`userId` are one `generateIv()` and
      // the patient link is `profileId`, never `userId`.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1');

      for (var i = 0; i < 2; i++) {
        final notifier = notifierFor(
          repo,
          patientId: 'pat-1',
          signedIn: patient,
        );
        await notifier.loaded;
        await saveExamination(notifier, pulse: 70 + i);
      }

      final record = await recordFor(db, repo, 'pat-1');
      expect(record?.examinations, hasLength(2));
      final rows = await db.healthExaminationDao.getUpdated();
      // Three rows: the profile row plus the two examinations, and only the
      // profile row is addressed by the patient's id.
      expect(rows, hasLength(3));
      expect(rows.where((r) => r.userId == 'pat-1'), hasLength(1));
    });

    test('the record names its examiner, its patient and their code', () async {
      // `saveData` stamps profileId, creatorId, gender, age, planetCode and
      // `isSelfExamination`, and `sign.createdBy` names the examiner. None of
      // them were written, so an uploaded examination described nobody: the
      // report had no age or gender to group by and the card could not say who
      // took it.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final provider = await seedUser(
        db,
        id: 'prov-1',
        couchId: 'org.couchdb.user:provider-1',
        roles: const ['health'],
      );
      await seedUser(
        db,
        id: 'pat-1',
        gender: 'male',
        dob: '1990-06-15',
        planetCode: 'guatemala',
      );
      final notifier = notifierFor(
        repo,
        patientId: 'pat-1',
        signedIn: provider,
      );
      await notifier.loaded;

      await saveExamination(notifier, diagnosis: 'Malaria');

      final record = await recordFor(db, repo, 'pat-1');
      final exam = record!.examinations.single;
      expect(exam.profileId, record.healthProfile.userKey);
      expect(exam.creatorId, record.healthProfile.userKey);
      expect(exam.gender, 'male');
      expect(exam.age, TimeUtils.getAge('1990-06-15'));
      expect(exam.planetCode, 'guatemala');
      // The provider is not the patient, so this is not a self-examination.
      expect(exam.selfExamination, isFalse);
      // The examiner lives in the encrypted blob, which is what
      // `submitExaminations` reads and what the card must show.
      expect(record.createdByOf[exam.id], 'org.couchdb.user:provider-1');
      final decrypted = await repo.decryptExamination('pat-1', exam);
      expect(decrypted?.createdBy, 'org.couchdb.user:provider-1');
      expect(decrypted?.diagnosis, 'Malaria');
    });

    test(
      'a patient recording their own vitals is a self-examination',
      () async {
        // `currentUser?._id == pojo?._id`.
        final db = AppDatabase.memory();
        final repo = repoFor(db);
        final patient = await seedUser(db, id: 'pat-1', couchId: 'pat-1');
        final notifier = notifierFor(
          repo,
          patientId: 'pat-1',
          signedIn: patient,
        );
        await notifier.loaded;

        await saveExamination(notifier);

        final record = await recordFor(db, repo, 'pat-1');
        expect(record!.examinations.single.selfExamination, isTrue);
      },
    );

    test('the second Save of one form is dropped', () async {
      // `saveExamination` opens with `if (_isSaving.value) return`. Without it
      // the create branch ran twice — `state.examination` is still null the
      // second time — and one form wrote the patient two examinations.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1');
      final notifier = notifierFor(repo, patientId: 'pat-1', signedIn: patient);
      await notifier.loaded;

      final first = saveExamination(notifier);
      final second = saveExamination(notifier);
      await Future.wait([first, second]);

      // Counted off the rows, not off the record: two unguarded saves each
      // mint their own `initHealth` key, so the profile ends up naming one of
      // them and `getByProfileId` reports a single examination while the
      // database holds two.
      final rows = await db.healthExaminationDao.getUpdated();
      expect(rows.where((r) => r.id != 'pat-1'), hasLength(1));
      final record = await recordFor(db, repo, 'pat-1');
      expect(record?.examinations, hasLength(1));
    });

    test('editing an examination updates it instead of adding one', () async {
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1');
      final first = notifierFor(repo, patientId: 'pat-1', signedIn: patient);
      await first.loaded;
      await saveExamination(first, pulse: 70, allergies: 'peanuts');
      final created = (await recordFor(db, repo, 'pat-1'))!.examinations.single;

      final editor = notifierFor(
        repo,
        patientId: 'pat-1',
        examId: created.id,
        signedIn: patient,
      );
      await editor.loaded;
      // The prefill the form reads: `initExamination` needs the decrypted
      // blob, which is keyed by the *patient*, not by the row's `userId`.
      expect(editor.state.examination?.id, created.id);
      expect(editor.state.examData?.allergies, 'peanuts');

      await saveExamination(editor, pulse: 88, allergies: 'shellfish');

      final record = await recordFor(db, repo, 'pat-1');
      expect(record?.examinations, hasLength(1));
      expect(record!.examinations.single.pulse, 88);
      expect(
        (await repo.decryptExamination(
          'pat-1',
          record.examinations.single,
        ))?.allergies,
        'shellfish',
      );
    });

    test('an edit re-encrypts against the patient, not the row id', () async {
      // A synced examination's `userId` is the document's own `_id`
      // (`_docToCompanion`), so encrypting with it found no user, produced a
      // null blob, and `updateExamination`'s `data ?? existing.data` kept the
      // old ciphertext — the edited notes were dropped in silence.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1');
      await db.userDao.ensureSecurityKeys('pat-1');
      final synced = await repo.getHealthProfile('pat-1');
      expect(synced, isNull);
      await repo.saveHealthProfileBlob('pat-1', MyHealth(userKey: 'key-1'));
      await db.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'server-exam-1',
          // The shape a completed sync leaves: `userId` is the doc's own id.
          userId: const Value('server-exam-1'),
          profileId: const Value('key-1'),
          temperature: const Value(37),
          pulse: const Value(72),
          data: Value(
            await repo.encryptData('pat-1', jsonEncode({'notes': 'old'})),
          ),
        ),
      );

      final editor = notifierFor(
        repo,
        patientId: 'pat-1',
        examId: 'server-exam-1',
        signedIn: patient,
      );
      await editor.loaded;
      expect(editor.state.examData?.notes, 'old');

      await editor.save(
        temperature: 37,
        pulse: 72,
        bp: null,
        height: 170,
        weight: 65,
        vision: null,
        hearing: null,
        allergies: null,
        diagnosis: null,
        medications: null,
        immunizations: null,
        treatments: null,
        notes: 'new observation',
        referrals: null,
        tests: null,
        xrays: null,
        conditions: const {},
      );

      final row = await repo.getById('server-exam-1');
      expect(
        (await repo.decryptExamination('pat-1', row!))?.notes,
        'new observation',
      );
    });

    test('a failed save does not outlive its retry', () async {
      // `error ?? this.error` could never clear, so the screen went on
      // reporting a failure the retry had already fixed.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1');
      // The failure has to be a *save* failure: a queue failure is deliberately
      // not one (the row is durable and the next drain takes it), so
      // `_onSaved` throwing no longer sets an error.
      final flaky = _FlakyHealthRepository(repo);
      final notifier = notifierFor(
        flaky,
        patientId: 'pat-1',
        signedIn: patient,
      );
      await notifier.loaded;
      flaky.failNextWrite = true;

      await saveExamination(notifier);
      expect(notifier.state.error, isNotNull);
      expect(notifier.state.saved, isFalse);

      await saveExamination(notifier);
      expect(notifier.state.error, isNull);
      expect(notifier.state.saved, isTrue);
    });

    test('the record reaches the outbox before the queue is drained', () async {
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1');
      var queued = 0;
      final notifier = notifierFor(
        repo,
        patientId: 'pat-1',
        signedIn: patient,
        onSaved: () async => queued++,
      );
      await notifier.loaded;

      await saveExamination(notifier);

      expect(queued, 1);
      expect(notifier.state.saved, isTrue);
      expect(notifier.state.isSaving, isFalse);
    });
  });

  group('HealthQueue', () {
    test('every queued row carries the signed-in user', () async {
      // `_ref.read(sessionProvider).valueOrNull` on a path where nothing
      // watches the session: it was still `AsyncLoading`, so the id was null
      // on every row the examination form queued.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1');
      await repo.createExamination(
        temperature: 36.5,
        pulse: 70,
        height: 170,
        weight: 65,
        profileId: 'key-1',
      );

      final container = containerFor(db, session: patient, repository: repo);
      expect(await container.read(healthQueueProvider).queuePending(), 1);

      final rows = await db.outboxDao.due(
        DateTime.now().millisecondsSinceEpoch + 1000,
      );
      expect(rows.map((r) => r.uploadType), [HealthUploader.type]);
      expect(rows.single.userId, 'pat-1');
    });

    test('no server config queues nothing', () async {
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1');
      await repo.createExamination(
        temperature: 36.5,
        pulse: 70,
        height: 170,
        weight: 65,
      );
      final container = ProviderContainer(
        overrides: [
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          healthRepositoryProvider.overrideWithValue(repo),
          serverConfigProvider.overrideWith(_NullConfig.new),
          sessionProvider.overrideWith(() => _TestSessionNotifier(patient)),
        ],
      );
      addTearDown(container.dispose);

      expect(await container.read(healthQueueProvider).queuePending(), 0);
      expect(
        await db.outboxDao.due(DateTime.now().millisecondsSinceEpoch + 1000),
        isEmpty,
      );
    });
  });

  group('examinationDetailProvider', () {
    test('decrypts one examination with the patient key', () async {
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1');
      final notifier = notifierFor(repo, patientId: 'pat-1', signedIn: patient);
      await notifier.loaded;
      await saveExamination(notifier, diagnosis: 'Anaemia');
      final exam = (await recordFor(db, repo, 'pat-1'))!.examinations.single;

      final container = containerFor(db, session: patient, repository: repo);
      final detail = await container.read(
        examinationDetailProvider((userId: 'pat-1', examId: exam.id)).future,
      );
      expect(detail?.diagnosis, 'Anaemia');
    });

    test('an unknown examination id resolves to null', () async {
      final db = AppDatabase.memory();
      final patient = await seedUser(db, id: 'pat-1');
      final container = containerFor(db, session: patient);
      expect(
        await container.read(
          examinationDetailProvider((userId: 'pat-1', examId: 'nope')).future,
        ),
        isNull,
      );
    });
  });

  group('PatientListNotifier', () {
    test('opens on join date, newest first', () async {
      final db = AppDatabase.memory();
      await seedUser(db, id: 'old', name: 'Older', joinDate: 100);
      await seedUser(db, id: 'new', name: 'Newer', joinDate: 900);
      final container = containerFor(db);

      final sub = container.listen(patientListProvider, (_, _) {});
      addTearDown(sub.close);
      await container.read(patientListProvider.notifier).refresh();

      expect(
        container.read(patientListProvider).requireValue.map((u) => u.id),
        ['new', 'old'],
      );
    });

    test('sorting by name reads the spinner mapping', () async {
      final db = AppDatabase.memory();
      await seedUser(db, id: 'b', name: 'Bea');
      await seedUser(db, id: 'a', name: 'Ada');
      final container = containerFor(db);
      final sub = container.listen(patientListProvider, (_, _) {});
      addTearDown(sub.close);

      await container
          .read(patientListProvider.notifier)
          .sort(PatientSort.nameAsc);
      expect(
        container.read(patientListProvider).requireValue.map((u) => u.name),
        ['Ada', 'Bea'],
      );

      await container
          .read(patientListProvider.notifier)
          .sort(PatientSort.nameDesc);
      expect(
        container.read(patientListProvider).requireValue.map((u) => u.name),
        ['Bea', 'Ada'],
      );
    });

    test('search filters to the matching members', () async {
      final db = AppDatabase.memory();
      await seedUser(db, id: 'a', name: 'Ada');
      await seedUser(db, id: 'b', name: 'Bea');
      final container = containerFor(db);
      final sub = container.listen(patientListProvider, (_, _) {});
      addTearDown(sub.close);

      await container.read(patientListProvider.notifier).search('Ad');
      expect(
        container.read(patientListProvider).requireValue.map((u) => u.name),
        ['Ada'],
      );
    });

    test('a superseded search cannot overwrite a later one', () async {
      // `searchJob?.cancel()`. Riverpod has no job to cancel, so without a
      // generation check a slow early query landed after a fast later one:
      // typing "ali" quickly left the list showing the matches for "a".
      final db = AppDatabase.memory();
      final slow = _GatedHealthRepository(repoFor(db));
      await seedUser(db, id: 'a', name: 'Ada');
      await seedUser(db, id: 'b', name: 'Bea');
      final container = containerFor(db, repository: slow);
      final sub = container.listen(patientListProvider, (_, _) {});
      addTearDown(sub.close);

      final notifier = container.read(patientListProvider.notifier);
      final first = notifier.search('A');
      final second = notifier.search('Bea');
      // The later query answers first, then the earlier one.
      await slow.release('Bea');
      await second;
      await slow.release('A');
      await first;

      expect(
        container.read(patientListProvider).requireValue.map((u) => u.name),
        ['Bea'],
      );
    });
  });

  group('PatientDetailNotifier', () {
    test('a device carrying an older build rows still resolves', () async {
      // `getByIdOrUserId` ends in `LIMIT 1` in the Kotlin. Rows written by a
      // build that gave every examination the patient's `userId` are still on
      // devices, and without the limit `getSingleOrNull` threw out of
      // `getPatientHealthRecords` — which `selectPatient` swallows, leaving a
      // screen that never updates again.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1', name: 'Ada');
      await repo.saveHealthProfileBlob('pat-1', MyHealth(userKey: 'k1'));
      for (final id in ['legacy-1', 'legacy-2']) {
        await db.healthExaminationDao.upsert(
          HealthExaminationsCompanion.insert(
            id: id,
            userId: const Value('pat-1'),
            profileId: const Value('k1'),
            temperature: const Value(37),
          ),
        );
      }

      final container = containerFor(db, session: patient, repository: repo);
      final sub = container.listen(patientDetailProvider, (_, _) {});
      addTearDown(sub.close);
      await waitUntil(
        () => container.read(patientDetailProvider).user != null,
        reason: 'the patient was never resolved',
      );
      expect(container.read(patientDetailProvider).record, isNotNull);
    });

    test('opens on the signed-in user own record', () async {
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final patient = await seedUser(db, id: 'pat-1', name: 'Ada');
      await repo.saveHealthProfileBlob(
        'pat-1',
        MyHealth(
          userKey: 'k1',
          profile: MyHealthProfile(notes: 'Lactose'),
        ),
      );
      final container = containerFor(db, session: patient, repository: repo);

      // `loadInitialPatient` runs from the constructor and selects
      // `_id ?: id`, so nothing is passed in here.
      final sub = container.listen(patientDetailProvider, (_, _) {});
      addTearDown(sub.close);
      await waitUntil(
        () => container.read(patientDetailProvider).user != null,
        reason: 'the initial patient was never selected',
      );

      final state = container.read(patientDetailProvider);
      expect(state.user?.name, 'Ada');
      expect(state.record?.healthProfile.profile?.notes, 'Lactose');
      expect(state.isLoading, isFalse);
    });

    test('refresh keeps the selected patient', () async {
      // Regression guard for the Phase 95 fix: `ref.invalidate` rebuilt the
      // notifier, which reran `_loadInitial` and resolved the *logged-in*
      // user, so a provider lost their patient on every sync.
      final db = AppDatabase.memory();
      final repo = repoFor(db);
      final provider = await seedUser(
        db,
        id: 'prov-1',
        name: 'Provider',
        roles: const ['health'],
      );
      await seedUser(db, id: 'pat-1', name: 'Patient');
      await repo.saveHealthProfileBlob('pat-1', MyHealth(userKey: 'k1'));
      final container = containerFor(db, session: provider, repository: repo);
      final sub = container.listen(patientDetailProvider, (_, _) {});
      addTearDown(sub.close);

      // The provider's own record loads first, as it does on the real screen.
      final notifier = container.read(patientDetailProvider.notifier);
      await waitUntil(() => container.read(patientDetailProvider).user != null);
      await notifier.selectPatient('pat-1');
      await waitUntil(
        () => container.read(patientDetailProvider).user?.name == 'Patient',
      );

      await notifier.refresh();
      expect(container.read(patientDetailProvider).user?.name, 'Patient');
      expect(container.read(patientDetailProvider).record, isNotNull);
    });

    test('an unknown patient empties the state', () async {
      final db = AppDatabase.memory();
      await seedUser(db, id: 'pat-1');
      final container = containerFor(db);
      final sub = container.listen(patientDetailProvider, (_, _) {});
      addTearDown(sub.close);

      final notifier = container.read(patientDetailProvider.notifier);
      await notifier.selectPatient('nobody');
      final state = container.read(patientDetailProvider);
      expect(state.user, isNull);
      expect(state.record, isNull);
      expect(state.isLoading, isFalse);
    });

    test('a superseded selection cannot overwrite a later one', () async {
      // `selectPatientJob?.cancel()`: two quick taps in the picker must not
      // leave the screen on the first patient's record.
      final db = AppDatabase.memory();
      final gated = _GatedHealthRepository(repoFor(db));
      await seedUser(db, id: 'pat-1', name: 'First');
      await seedUser(db, id: 'pat-2', name: 'Second');
      // No session, so `loadInitialPatient` no-ops and the two taps below are
      // the only selections in flight.
      final container = containerFor(db, repository: gated);
      final sub = container.listen(patientDetailProvider, (_, _) {});
      addTearDown(sub.close);

      final notifier = container.read(patientDetailProvider.notifier);
      final a = notifier.selectPatient('pat-1');
      final b = notifier.selectPatient('pat-2');
      // Two reads each (`getPatientById` then `getPatientHealthRecords`), and
      // the later selection answers first.
      await gated.release('pat-2', 2);
      await b;
      await gated.release('pat-1', 2);
      await a;

      expect(container.read(patientDetailProvider).user?.name, 'Second');
    });
  });
}

class _NoopApi extends Mock implements PlanetApi {}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

class _TestConfig extends ServerConfigNotifier {
  @override
  ServerConfig? build() => const ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );
}

class _NullConfig extends ServerConfigNotifier {
  @override
  ServerConfig? build() => null;
}

/// Fails the next profile-row write, then behaves normally — a database error
/// on the save path, which is what the Kotlin's false `saveResult` reports.
class _FlakyHealthRepository implements HealthRepository {
  _FlakyHealthRepository(this._inner);

  final HealthRepository _inner;
  bool failNextWrite = false;

  @override
  Future<HealthExaminationRow?> saveHealthProfileBlob(
    String userId,
    MyHealth health,
  ) async {
    if (failNextWrite) {
      failNextWrite = false;
      throw StateError('database gone');
    }
    return _inner.saveHealthProfileBlob(userId, health);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => Function.apply(
    _forward(invocation.memberName),
    [...invocation.positionalArguments],
    invocation.namedArguments,
  );

  Function _forward(Symbol member) => switch (member) {
    #getPatientById => _inner.getPatientById,
    #getHealthProfile => _inner.getHealthProfile,
    #encryptData => _inner.encryptData,
    #createExamination => _inner.createExamination,
    #updateExamination => _inner.updateExamination,
    #getById => _inner.getById,
    #getByIdOrUserId => _inner.getByIdOrUserId,
    #parseConditions => _inner.parseConditions,
    #decryptData => _inner.decryptData,
    _ => throw UnsupportedError('$member is not forwarded'),
  };
}

/// Holds each patient query until the test lets it through, so a test can make
/// an earlier request answer *after* a later one — the ordering Kotlin's
/// `searchJob?.cancel()` / `selectPatientJob?.cancel()` rules out.
class _GatedHealthRepository implements HealthRepository {
  _GatedHealthRepository(this._inner);

  final HealthRepository _inner;
  final List<(String, Completer<void>)> _gates = [];

  Future<void> _gate(String label) {
    final completer = Completer<void>();
    _gates.add((label, completer));
    return completer.future;
  }

  /// Lets up to [count] queries made for [label] proceed, oldest first.
  ///
  /// A query that never arrives is not an error: a superseded request returns
  /// before making its second call, which is the behaviour under test — so
  /// releasing more than were made must be a no-op rather than a hang.
  Future<void> release(String label, [int count = 1]) async {
    for (var i = 0; i < count; i++) {
      var index = -1;
      for (var spin = 0; spin < 100 && index < 0; spin++) {
        index = _gates.indexWhere((gate) => gate.$1 == label);
        if (index < 0) await Future<void>.delayed(Duration.zero);
      }
      if (index < 0) return;
      _gates.removeAt(index).$2.complete();
      await Future<void>.delayed(Duration.zero);
    }
  }

  @override
  Future<List<UserRow>> searchPatients(
    String query, {
    String sortField = 'joinDate',
    bool descending = false,
  }) async {
    await _gate(query);
    return _inner.searchPatients(
      query,
      sortField: sortField,
      descending: descending,
    );
  }

  @override
  Future<List<UserRow>> getPatientsSortedBy(
    String fieldName, {
    bool descending = false,
  }) async {
    await _gate(fieldName);
    return _inner.getPatientsSortedBy(fieldName, descending: descending);
  }

  @override
  Future<UserRow?> getPatientById(String id) async {
    await _gate(id);
    return _inner.getPatientById(id);
  }

  @override
  Future<HealthRecord?> getPatientHealthRecords(
    String userId,
    UserRow currentUser,
  ) async {
    await _gate(userId);
    return _inner.getPatientHealthRecords(userId, currentUser);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnsupportedError('${invocation.memberName} is not gated');
}
