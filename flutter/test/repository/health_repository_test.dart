import 'dart:convert';

import 'package:drift/drift.dart' hide isNotNull, isNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/health_repository.dart';
import 'package:myplanet/repository/user_repository.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
  });

  tearDown(() => database.close());

  HealthRepository createRepository({ServerConfig? config, int counter = 0}) {
    return HealthRepository(
      api,
      database.healthExaminationDao,
      database.userDao,
      config: config,
      createId: () => 'health-local-${++counter}',
    );
  }

  test('creates and retrieves a health examination', () async {
    final repository = createRepository();
    final id = await repository.createExamination(
      userId: 'user-1',
      temperature: 36.5,
      pulse: 72,
      bp: '120/80',
      height: 175,
      weight: 70,
      vision: '20/20',
      hearing: 'Normal',
    );

    expect(id, 'health-local-1');

    final row = await repository.getById(id);
    expect(row, isNotNull);
    expect(row!.temperature, 36.5);
    expect(row.pulse, 72);
    expect(row.bp, '120/80');
    expect(row.height, 175);
    expect(row.weight, 70);
    expect(row.isUpdated, isTrue);
  });

  test('updates an existing examination', () async {
    final repository = createRepository();
    final id = await repository.createExamination(
      userId: 'user-1',
      temperature: 36.5,
      pulse: 72,
      height: 175,
      weight: 70,
    );

    await repository.updateExamination(id, temperature: 37.0, pulse: 80);

    final row = await repository.getById(id);
    expect(row!.temperature, 37.0);
    expect(row.pulse, 80);
    expect(row.isUpdated, isTrue);
  });

  test('parses conditions from JSON', () {
    final repository = createRepository();
    expect(repository.parseConditions('{"Fever": true, "Cough": false}'), {
      'Fever': true,
      'Cough': false,
    });

    expect(repository.parseConditions(null), isEmpty);
    expect(repository.parseConditions(''), isEmpty);
    expect(repository.parseConditions('invalid'), isEmpty);
  });

  test('converts row to model', () async {
    final companion = HealthExaminationsCompanion.insert(
      id: 'health-1',
      userId: const Value('user-1'),
      temperature: const Value(36.5),
      pulse: const Value(72),
    );

    // Insert into database and get back
    await database.healthExaminationDao.upsert(companion);
    final row = await database.healthExaminationDao.getById('health-1');

    expect(row, isNotNull);
    expect(row!.temperature, 36.5);

    // Test rowToModel conversion
    final model = HealthRepository.rowToModel(row);
    expect(model.temperature, 36.5);
    expect(model.pulse, 72);
  });

  test('maps and caches server health documents', () async {
    final repository = createRepository();
    expect(
      await repository.cacheDocuments([
        {
          '_id': 'health-1',
          '_rev': '1-a',
          'temperature': 36.6,
          'pulse': 75,
          'bp': '118/78',
          'height': 180.0,
          'weight': 80.0,
        },
      ]),
      1,
    );

    final row = await repository.getById('health-1');
    expect(row?.temperature, 36.6);
    expect(row?.pulse, 75);
    expect(row?.bp, '118/78');
    expect(row?.rev, '1-a');
    expect(row?.isUpdated, isFalse);
  });

  test('preserves local edits during cache', () async {
    final repository = createRepository();
    await repository.cacheDocuments([
      {'_id': 'health-1', 'temperature': 36.5, 'pulse': 70},
    ]);

    // Create a local edit
    await repository.updateExamination(
      'health-1',
      temperature: 37.0,
      pulse: 80,
    );

    // Server sends an update
    await repository.cacheDocuments([
      {'_id': 'health-1', 'temperature': 36.0, 'pulse': 60},
    ]);

    // Local edit should be preserved
    final row = await repository.getById('health-1');
    expect(row?.temperature, 37.0);
    expect(row?.pulse, 80);
    expect(row?.isUpdated, isTrue);
  });

  test('conditions cross the wire as a nested object', () async {
    // `serialize` is `addJson(object, "conditions", gson.fromJson(...))` — the
    // map as a nested JSON *object*, omitted when empty. Sending the stored
    // string put a primitive where `getJsonObject` expects an object, so a
    // Kotlin reader saw no conditions at all and its next save overwrote them.
    final repository = createRepository();
    final id = await repository.createExamination(
      temperature: 37,
      pulse: 72,
      height: 170,
      weight: 65,
      conditions: jsonEncode({'Malaria': true, 'Anaemia': false}),
    );

    final payload = HealthRepository.serialize((await repository.getById(id))!);
    expect(payload['conditions'], {'Malaria': true, 'Anaemia': false});

    // And an empty map is left out, as `addJson` leaves it out.
    final empty = await repository.createExamination(
      temperature: 37,
      pulse: 72,
      height: 170,
      weight: 65,
      conditions: jsonEncode(<String, bool>{}),
    );
    expect(
      HealthRepository.serialize((await repository.getById(empty))!),
      isNot(contains('conditions')),
    );
  });

  test('a synced document conditions object is stored as JSON', () async {
    // `conditions = gson.toJson(getJsonObject("conditions", act))`. Calling
    // `toString()` on the decoded map yields `{Malaria: true}`, which is not
    // JSON: `parseConditions` threw, the record read as having no conditions,
    // and saving that edit wrote the empty map back over them.
    final repository = createRepository();
    await repository.cacheDocuments([
      {
        '_id': 'health-1',
        'temperature': 37.0,
        'conditions': {'Malaria': true, 'Anaemia': false},
      },
    ]);

    final row = await repository.getById('health-1');
    expect(repository.parseConditions(row?.conditions), {
      'Malaria': true,
      'Anaemia': false,
    });
  });

  test('serializes examination for upload', () async {
    final repository = createRepository();
    await repository.cacheDocuments([
      {
        '_id': 'health-1',
        '_rev': '1-a',
        'temperature': 36.5,
        'pulse': 72,
        'bp': '120/80',
        'height': 175.0,
        'weight': 70.0,
      },
    ]);

    final row = await repository.getById('health-1');
    final payload = HealthRepository.serialize(row!);

    expect(payload['_id'], 'health-1');
    expect(payload['_rev'], '1-a');
    expect(payload['temperature'], 36.5);
    expect(payload['pulse'], 72);
    expect(payload['bp'], '120/80');
    expect(payload['height'], 175.0);
    expect(payload['weight'], 70.0);
  });

  test('gets examinations by user id', () async {
    final repository = createRepository();
    // Create examinations directly via repository
    await repository.createExamination(
      userId: 'user-1',
      temperature: 36.5,
      pulse: 72,
      height: 175,
      weight: 70,
    );
    await repository.createExamination(
      userId: 'user-1',
      temperature: 36.6,
      pulse: 72,
      height: 175,
      weight: 70,
    );
    await repository.createExamination(
      userId: 'user-2',
      temperature: 36.7,
      pulse: 75,
      height: 180,
      weight: 80,
    );

    final rows = await repository.getForUser('user-1');
    expect(rows.length, 2);
    expect(rows.every((r) => r.userId == 'user-1'), isTrue);
  });

  test('gets updated examinations for sync', () async {
    final repository = createRepository();
    await repository.cacheDocuments([
      {'_id': 'health-1', 'temperature': 36.5},
    ]);

    // Create a local edit
    await repository.updateExamination('health-1', temperature: 37.0);

    final updated = await repository.getUpdated();
    expect(updated.length, 1);
    expect(updated.first.temperature, 37.0);
  });

  test('marks examination as uploaded', () async {
    final repository = createRepository();
    await repository.cacheDocuments([
      {'_id': 'health-1', 'temperature': 36.5},
    ]);

    await repository.updateExamination('health-1', temperature: 37.0);

    await repository.markUploaded('health-1', '2-b');

    final row = await repository.getById('health-1');
    expect(row!.isUpdated, isFalse);
    expect(row.rev, '2-b');
  });

  test('sync walks CouchDB pages', () async {
    final repository = createRepository(
      config: const ServerConfig(
        serverUrl: 'https://planet.example',
        couchDbUrl: 'https://satellite:1234@planet.example:443',
        pin: '1234',
      ),
    );
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments.single as String;
      if (url.endsWith('limit=0')) {
        return NetworkSuccess<Map<String, dynamic>>({'total_rows': 2});
      }
      return NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'doc': {'_id': 'health-1', 'temperature': 36.5},
          },
          {
            'doc': {'_id': 'health-2', 'temperature': 36.6},
          },
        ],
      });
    });

    final result = await repository.sync();

    expect(result, isA<SyncComplete>());
    expect((result as SyncComplete).savedCount, 2);
  });

  // ── Patient management — port of Kotlin 962e1e736 ──────────────────────

  group('patient management', () {
    Future<void> seedUsers() async {
      await database.userDao.upsert(
        UsersCompanion.insert(
          id: 'user-a',
          couchId: const Value('org.couchdb.user:alice'),
          name: const Value('alice'),
          firstName: const Value('Alice'),
          joinDate: const Value(1000),
        ),
      );
      await database.userDao.upsert(
        UsersCompanion.insert(
          id: 'user-b',
          couchId: const Value('org.couchdb.user:bob'),
          name: const Value('bob'),
          firstName: const Value('Bob'),
          joinDate: const Value(2000),
        ),
      );
      await database.userDao.upsert(
        UsersCompanion.insert(
          id: 'user-c',
          couchId: const Value('org.couchdb.user:carol'),
          name: const Value('carol'),
          firstName: const Value('Carol'),
          joinDate: const Value(3000),
        ),
      );
    }

    test('getPatientById returns user by id', () async {
      await seedUsers();
      final repo = createRepository();
      final user = await repo.getPatientById('user-b');
      expect(user, isNotNull);
      expect(user!.name, 'bob');
    });

    test('getPatientsSortedBy sorts by joinDate descending', () async {
      await seedUsers();
      final repo = createRepository();
      final patients = await repo.getPatientsSortedBy(
        'joinDate',
        descending: true,
      );
      expect(patients.map((u) => u.name).toList(), ['carol', 'bob', 'alice']);
    });

    test('getPatientsSortedBy sorts by joinDate ascending', () async {
      await seedUsers();
      final repo = createRepository();
      final patients = await repo.getPatientsSortedBy(
        'joinDate',
        descending: false,
      );
      expect(patients.map((u) => u.name).toList(), ['alice', 'bob', 'carol']);
    });

    test('getPatientsSortedBy sorts by name', () async {
      await seedUsers();
      final repo = createRepository();
      final patients = await repo.getPatientsSortedBy(
        'name',
        descending: false,
      );
      expect(patients.map((u) => u.name).toList(), ['alice', 'bob', 'carol']);
    });

    test('searchPatients with blank query returns all', () async {
      await seedUsers();
      final repo = createRepository();
      final patients = await repo.searchPatients('');
      expect(patients.length, 3);
    });

    test('searchPatients filters by name', () async {
      await seedUsers();
      final repo = createRepository();
      final patients = await repo.searchPatients('bob');
      expect(patients.length, 1);
      expect(patients.first.name, 'bob');
    });

    test('searchPatients filters by firstName', () async {
      await seedUsers();
      final repo = createRepository();
      final patients = await repo.searchPatients('Car');
      expect(patients.length, 1);
      expect(patients.first.firstName, 'Carol');
    });

    test(
      'getPatientHealthRecords returns null when no examination exists',
      () async {
        await seedUsers();
        final repo = createRepository();
        final user = await repo.getPatientById('user-a');
        final record = await repo.getPatientHealthRecords('user-a', user!);
        expect(record, isNull);
      },
    );

    test(
      'getPatientHealthRecords decrypts and bundles the health record',
      () async {
        await seedUsers();
        final repo = createRepository();
        // Ensure the user has crypto keys.
        final user = await database.userDao.ensureSecurityKeys('user-a');
        // Encrypt a MyHealth JSON payload.
        final myHealthJson = jsonEncode({
          'profile': {
            'emergencyContactName': 'Jane',
            'emergencyContact': '555-1234',
          },
          'userKey': 'user-a',
          'lastExamination': 0,
        });
        final encrypted = await repo.encryptData('user-a', myHealthJson);
        // Create the primary health examination row carrying the encrypted
        // profile. Its profileId is set so getByProfileId includes it.
        await repo.createExamination(
          userId: 'user-a',
          profileId: 'user-a',
          temperature: 36.5,
          pulse: 70,
          height: 170,
          weight: 65,
          data: encrypted,
        );

        final record = await repo.getPatientHealthRecords('user-a', user!);
        expect(record, isNotNull);
        expect(record!.healthProfile.profile?.emergencyContactName, 'Jane');
        // The primary examination is included in the bundled history.
        expect(record.examinations, isNotEmpty);
        expect(record.healthPojo.temperature, 36.5);
      },
    );

    test(
      'decryptExamination decrypts the data blob and returns Examination',
      () async {
        await seedUsers();
        final repo = createRepository();
        await database.userDao.ensureSecurityKeys('user-a');
        final examJson = jsonEncode({
          'notes': 'Patient reports chest pain',
          'diagnosis': 'Hypertension',
          'treatments': 'ACE inhibitor',
          'medications': 'Lisinopril 10mg',
          'immunizations': 'Influenza vaccine',
          'allergies': 'Penicillin',
          'xrays': 'Chest X-ray clear',
          'tests': 'Lipid panel normal',
          'referrals': 'Cardiology',
          'createdBy': 'org.couchdb.user:provider-1',
        });
        final encrypted = await repo.encryptData('user-a', examJson);
        final id = await repo.createExamination(
          userId: 'user-a',
          temperature: 36.5,
          pulse: 72,
          height: 170,
          weight: 65,
          data: encrypted,
        );
        final row = await repo.getById(id);
        expect(row, isNotNull);
        final decrypted = await repo.decryptExamination('user-a', row!);
        expect(decrypted, isNotNull);
        expect(decrypted!.notes, 'Patient reports chest pain');
        expect(decrypted.diagnosis, 'Hypertension');
        expect(decrypted.treatments, 'ACE inhibitor');
        expect(decrypted.medications, 'Lisinopril 10mg');
        expect(decrypted.immunizations, 'Influenza vaccine');
        expect(decrypted.allergies, 'Penicillin');
        expect(decrypted.xrays, 'Chest X-ray clear');
        expect(decrypted.tests, 'Lipid panel normal');
        expect(decrypted.referrals, 'Cardiology');
        expect(decrypted.createdBy, 'org.couchdb.user:provider-1');
      },
    );

    test(
      'decryptExamination returns null when data is empty or missing',
      () async {
        await seedUsers();
        final repo = createRepository();
        final id = await repo.createExamination(
          userId: 'user-a',
          temperature: 36.5,
          pulse: 72,
          height: 170,
          weight: 65,
        );
        final row = await repo.getById(id);
        expect(row, isNotNull);
        expect(await repo.decryptExamination('user-a', row!), isNull);
      },
    );
  });
  // ── Dashboard key/IV sync — port of TransactionSyncManager ──────────────
  //
  // `syncDashboardKeyId` pulls the health AES key/IV a user published to
  // `userdb-<hex(planetCode)>-<hex(name)>`, so records written on another
  // device decrypt here. The expected table names below are pinned to the
  // Kotlin `Utilities.toHex` output (see text_utils_test).

  group('syncDashboardKeyIv', () {
    const config = ServerConfig(
      serverUrl: 'https://planet.example',
      couchDbUrl: 'https://satellite:1234@planet.example:443',
      pin: '1234',
    );

    Future<void> seedSyncedUser(String id, String name) =>
        database.userDao.upsert(
          UsersCompanion.insert(
            id: id,
            couchId: Value('org.couchdb.user:$name'),
            name: Value(name),
            planetCode: const Value('earth'),
          ),
        );

    void stubKeyIvDoc({
      required String table,
      Map<String, dynamic>? doc,
      bool allDocsFail = false,
      List<String?>? capturedHeaders,
    }) {
      void capture(Invocation invocation) {
        capturedHeaders?.add(invocation.namedArguments[#authHeader] as String?);
      }

      when(
        () => api.getJsonObject(
          'https://satellite:1234@planet.example:443/db/$table/_all_docs',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((invocation) async {
        capture(invocation);
        return allDocsFail
            ? const NetworkError<Map<String, dynamic>>(503, 'down')
            : NetworkSuccess<Map<String, dynamic>>({
                'rows': [
                  {'id': 'keydoc'},
                ],
              });
      });
      when(
        () => api.getJsonObject(
          'https://satellite:1234@planet.example:443/db/$table/keydoc',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((invocation) async {
        capture(invocation);
        return NetworkSuccess<Map<String, dynamic>>(doc ?? {});
      });
    }

    test(
      'a non-health role fetches only the signed-in user, with their own credentials',
      () async {
        await seedSyncedUser('user-1', 'alice');
        await seedSyncedUser('user-2', 'bob');
        final captured = <String?>[];
        stubKeyIvDoc(
          table: 'userdb-6561727468-616c696365',
          doc: {'key': 'KEY', 'iv': 'IV'},
          capturedHeaders: captured,
        );

        await createRepository(config: config).syncDashboardKeyIv(
          userName: 'alice',
          password: 'pw',
          currentUserId: 'user-1',
          role: 'learner',
        );

        final user = await database.userDao.getById('user-1');
        expect(user?.key, 'KEY');
        expect(user?.iv, 'IV');
        // The header is the user's own basic auth, not the satellite PIN.
        expect(captured, ['Basic YWxpY2U6cHc=', 'Basic YWxpY2U6cHc=']);
        // Bob's userdb is never touched without a health role.
        verifyNever(
          () => api.getJsonObject(
            any(that: contains('bob')),
            authHeader: any(named: 'authHeader'),
          ),
        );
      },
    );

    test('a health role syncs every synced account', () async {
      await seedSyncedUser('user-1', 'alice');
      await seedSyncedUser('user-2', 'bob');
      // Not synced (blank couchId): excluded from the batch.
      await database.userDao.upsert(
        UsersCompanion.insert(
          id: 'local-only',
          couchId: const Value(''),
          name: const Value('carol'),
        ),
      );
      stubKeyIvDoc(
        table: 'userdb-6561727468-616c696365',
        doc: {'key': 'AK', 'iv': 'AI'},
      );
      stubKeyIvDoc(
        table: 'userdb-6561727468-626f62',
        doc: {'key': 'BK', 'iv': 'BI'},
      );

      await createRepository(config: config).syncDashboardKeyIv(
        userName: 'alice',
        password: 'pw',
        currentUserId: 'user-1',
        role: 'learner,health',
      );

      expect((await database.userDao.getById('user-1'))?.key, 'AK');
      expect((await database.userDao.getById('user-2'))?.key, 'BK');
      expect((await database.userDao.getById('local-only'))?.key, isNull);
    });

    test('a document without key or iv leaves the row untouched', () async {
      await seedSyncedUser('user-1', 'alice');
      stubKeyIvDoc(table: 'userdb-6561727468-616c696365', doc: {});

      await createRepository(config: config).syncDashboardKeyIv(
        userName: 'alice',
        password: 'pw',
        currentUserId: 'user-1',
        role: 'learner',
      );

      final user = await database.userDao.getById('user-1');
      expect(user?.key, isNull);
      expect(user?.iv, isNull);
    });

    test('one failing account does not fail the rest of the batch', () async {
      await seedSyncedUser('user-1', 'alice');
      await seedSyncedUser('user-2', 'bob');
      stubKeyIvDoc(table: 'userdb-6561727468-616c696365', allDocsFail: true);
      stubKeyIvDoc(
        table: 'userdb-6561727468-626f62',
        doc: {'key': 'BK', 'iv': 'BI'},
      );

      await createRepository(config: config).syncDashboardKeyIv(
        userName: 'alice',
        password: 'pw',
        currentUserId: 'user-1',
        role: 'health',
      );

      expect((await database.userDao.getById('user-1'))?.key, isNull);
      expect((await database.userDao.getById('user-2'))?.key, 'BK');
    });

    test('no server config is a silent no-op', () async {
      await seedSyncedUser('user-1', 'alice');

      await createRepository().syncDashboardKeyIv(
        userName: 'alice',
        password: 'pw',
        currentUserId: 'user-1',
        role: 'learner',
      );

      verifyNever(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      );
    });
  });

  // ── Health profile (AddHealthActivity) ─────────────────────────────────

  group('health profile', () {
    Future<void> seedUser() async {
      await database.userDao.upsert(
        UsersCompanion.insert(
          id: 'user-1',
          couchId: const Value('org.couchdb.user:alice'),
          name: const Value('alice'),
          firstName: const Value('Alice'),
          joinDate: const Value(1000),
        ),
      );
    }

    test('getHealthProfile returns null when no examination exists', () async {
      await seedUser();
      final repo = createRepository();
      expect(await repo.getHealthProfile('user-1'), isNull);
    });

    test('saveHealthProfile creates examination row and user fields', () async {
      await seedUser();
      final repo = createRepository();

      await repo.saveHealthProfile('user-1', {
        'firstName': 'Alice',
        'middleName': 'Marie',
        'lastName': 'Smith',
        'email': 'alice@example.com',
        'dob': '15-06-1990',
        'birthPlace': 'Nairobi',
        'phoneNumber': '+254700',
        'emergencyContactName': 'Bob',
        'emergencyContact': '+254701',
        'emergencyContactType': 'Phone',
        'specialNeeds': 'Asthma',
        'notes': 'Carries inhaler',
      });

      // User fields updated.
      final user = await database.userDao.getById('user-1');
      expect(user!.firstName, 'Alice');
      expect(user.middleName, 'Marie');
      expect(user.lastName, 'Smith');
      expect(user.email, 'alice@example.com');
      expect(user.birthPlace, 'Nairobi');
      expect(user.phoneNumber, '+254700');
      expect(user.dob, '1990-06-15T00:00:00.000Z');
      expect(user.isUpdated, isTrue);
      // Security keys generated on first save.
      expect(user.key, isNotNull);
      expect(user.iv, isNotNull);

      // Examination row created with encrypted data.
      final exam = await database.healthExaminationDao.getByIdOrUserId(
        'user-1',
      );
      expect(exam, isNotNull);
      expect(exam!.data, isNotNull);
      expect(exam.data!.isNotEmpty, isTrue);
      expect(exam.isUpdated, isTrue);
    });

    test('getHealthProfile round-trips after saveHealthProfile', () async {
      await seedUser();
      final repo = createRepository();

      await repo.saveHealthProfile('user-1', {
        'firstName': 'Alice',
        'emergencyContactName': 'Bob',
        'emergencyContact': '+254701',
        'emergencyContactType': 'Phone',
        'specialNeeds': 'Asthma',
        'notes': 'Carries inhaler',
      });

      final health = await repo.getHealthProfile('user-1');
      expect(health, isNotNull);
      expect(health!.profile, isNotNull);
      expect(health.profile!.emergencyContactName, 'Bob');
      expect(health.profile!.emergencyContact, '+254701');
      expect(health.profile!.emergencyContactType, 'Phone');
      expect(health.profile!.specialNeeds, 'Asthma');
      expect(health.profile!.notes, 'Carries inhaler');
      expect(health.userKey, isNotNull);
    });

    test('saveHealthProfile preserves existing profile fields', () async {
      await seedUser();
      final repo = createRepository();

      await repo.saveHealthProfile('user-1', {
        'firstName': 'Alice',
        'emergencyContactName': 'Bob',
        'emergencyContact': '+254701',
        'emergencyContactType': 'Phone',
        'specialNeeds': 'Asthma',
        'notes': 'Carries inhaler',
      });

      // Second save with only some fields — the Kotlin keeps existing
      // emergencyContact/emergencyContactType when the new value is empty,
      // but overwrites name/specialNeeds/notes unconditionally.
      await repo.saveHealthProfile('user-1', {
        'firstName': 'Alice',
        'emergencyContactName': 'Robert',
        'emergencyContact': '',
        'emergencyContactType': '',
        'specialNeeds': 'Diabetes',
        'notes': '',
      });

      final health = await repo.getHealthProfile('user-1');
      expect(health!.profile!.emergencyContactName, 'Robert');
      expect(health.profile!.emergencyContact, '+254701');
      expect(health.profile!.emergencyContactType, 'Phone');
      expect(health.profile!.specialNeeds, 'Diabetes');
      expect(health.profile!.notes, '');
    });

    test('saveHealthProfile trims every profile field', () async {
      // The Kotlin reads each of these as `(userData[k] as? String)?.trim()`,
      // and the port trimmed emergencyContact/emergencyContactType but not
      // name/specialNeeds/notes — so the same typed input stored differently
      // in the two apps, and a whitespace-only entry survived as whitespace
      // where the Kotlin stores "".
      await seedUser();
      final repo = createRepository();

      await repo.saveHealthProfile('user-1', {
        'firstName': 'Alice',
        'emergencyContactName': '  Bob  ',
        'emergencyContact': '  +254701  ',
        'emergencyContactType': '  Phone  ',
        'specialNeeds': '  Asthma  ',
        'notes': '   ',
      });

      final health = await repo.getHealthProfile('user-1');
      expect(health!.profile!.emergencyContactName, 'Bob');
      expect(health.profile!.emergencyContact, '+254701');
      expect(health.profile!.emergencyContactType, 'Phone');
      expect(health.profile!.specialNeeds, 'Asthma');
      // Whitespace-only collapses to empty, so it does not read as a note.
      expect(health.profile!.notes, '');
    });

    test('saveHealthProfile is a no-op when user does not exist', () async {
      final repo = createRepository();
      await repo.saveHealthProfile('nonexistent', {'firstName': 'Ghost'});
      final exam = await database.healthExaminationDao.getByIdOrUserId(
        'nonexistent',
      );
      expect(exam, isNull);
    });
  });
  group('a member registered offline who later signs in online', () {
    // The transition Phase 107 is about, end to end: the account is created on
    // this device with a local id, health records are encrypted with the
    // `key`/`iv` minted on that row, the upload records the server-assigned
    // `couchId`, and only then does the member sign in online. From that point
    // the health screens address them by `patientIdOf` — the CouchDB id — so
    // everything below depends on that id resolving to the one row that holds
    // their key.
    const localId = '1700000000000';
    const couchId = 'org.couchdb.user:ada';

    // Generated independently with Python's `hashlib.pbkdf2_hmac` using the
    // parameters `AndroidDecrypter` pins (SHA1, 10 iterations, 20 bytes).
    const password = 'correct-horse';
    const salt = 'a3f1c9d2e5b74806a1c2d3e4f5061728';
    const derivedKey = 'ddb696f6f34c21547a45f8034c6e41daf63b3ce3';

    test('their examinations are still readable afterwards', () async {
      final health = createRepository();

      // 1. `become_member_screen`: a local id, no couchId.
      await database.userDao.upsert(
        const UsersCompanion(id: Value(localId), name: Value('ada')),
      );

      // 2. An examination recorded offline, encrypted with the key
      //    `ensureSecurityKeys` mints on that row.
      final blob = await health.encryptData(localId, '{"allergies":"peanuts"}');
      expect(blob, isNotNull);
      final examId = await health.createExamination(
        profileId: 'profile-key',
        temperature: 37,
        pulse: 72,
        height: 170,
        weight: 65,
        data: blob,
      );

      // 3. The upload lands and the row gains its server id.
      await database.userDao.updateUserSecurityData(
        localId: localId,
        couchId: couchId,
        rev: '1-abc',
        passwordScheme: 'pbkdf2',
        derivedKey: derivedKey,
        salt: salt,
        iterations: '10',
      );

      // 4. The member signs in online for the first time.
      final loginApi = MockPlanetApi();
      const config = ServerConfig(
        serverUrl: 'https://planet.example.org',
        pin: '1234',
        couchDbUrl: 'https://satellite:1234@planet.example.org:443',
      );
      when(
        () =>
            loginApi.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          '_id': couchId,
          '_rev': '1-abc',
          'name': 'ada',
          'derived_key': derivedKey,
          'salt': salt,
          'password_scheme': 'pbkdf2',
          'iterations': '10',
        }),
      );
      final login = await UserRepository(
        loginApi,
        database.userDao,
      ).loginOnline(config: config, username: 'ada', password: password);
      expect(login, isA<LoginSuccess>());

      // 5. One row, and the CouchDB id the health screens use resolves to it.
      expect(await database.userDao.getAllUsers(), hasLength(1));
      final patient = await database.userDao.getById(couchId);
      expect(patient?.id, localId);

      // 6. Which is what keeps the record readable.
      final row = await health.getById(examId);
      final plain = await health.decryptData(couchId, row!.data);
      expect(plain, '{"allergies":"peanuts"}');
    });
  });
  group('the upload keys documents the way Kotlin keys them', () {
    // `HealthExamination.serialize` is
    // `if (!health.userId.isNullOrEmpty()) object.addProperty("_id", health.userId)`
    // (`HealthExamination.kt:96`) — the document is keyed on `userId`, not on
    // the row's own id, and carries no `_id` at all while `userId` is empty.
    //
    // `HealthExaminationDao.getUpdated()` is
    // `WHERE isUpdated = 1 AND userId != ''`, and in SQL `userId != ''` is
    // false for NULL too, so Kotlin excludes both. The two halves are one
    // rule: the guard is what makes serialize's omit-`_id` branch unreachable
    // from the upload path. Porting either half alone is what creates the
    // hazard — a POST with no `_id` makes CouchDB mint a fresh document on
    // every drain, so one examination becomes a new record each sync.

    test('the document is keyed on userId, not on the row id', () async {
      final repository = createRepository();
      // `createPojo` writes the profile row with `_id` = the patient id the
      // screen was opened with and `userId` = the patient's CouchDB id, and
      // `app_providers`' `updateUserId` rewrites that `userId` once the
      // account uploads. So the two genuinely differ for a member registered
      // on this device, and Kotlin uploads under the CouchDB id.
      final id = await repository.createExamination(
        userId: 'org.couchdb.user:ada',
        temperature: 37,
        pulse: 72,
        height: 170,
        weight: 65,
      );

      final payload = HealthRepository.serialize(
        (await repository.getById(id))!,
      );
      expect(payload['_id'], 'org.couchdb.user:ada');
    });

    test('a row with no userId carries no _id', () async {
      final repository = createRepository();
      await repository.createExamination(
        temperature: 37,
        pulse: 72,
        height: 170,
        weight: 65,
      );
      // The row above gets `userId` = its own id; blank it the way an older
      // build's row would have been.
      await database.healthExaminationDao.updateUserId('health-local-1', '');

      final row = await repository.getById('health-local-1');
      expect(
        HealthRepository.serialize(row!),
        isNot(contains('_id')),
        reason: 'addProperty is guarded by !userId.isNullOrEmpty()',
      );
    });

    test('getUpdated skips a row whose userId is blank', () async {
      final repository = createRepository();
      await repository.createExamination(
        temperature: 37,
        pulse: 72,
        height: 170,
        weight: 65,
      );
      await repository.createExamination(
        temperature: 38,
        pulse: 80,
        height: 170,
        weight: 65,
      );
      await database.healthExaminationDao.updateUserId('health-local-1', '');

      // `userId != ''` excludes the blank row, which is exactly the row
      // serialize would have POSTed without an `_id`.
      final pending = await repository.getUpdated();
      expect(pending.map((r) => r.id), ['health-local-2']);
    });

    test('getUpdated skips a row whose userId is null', () async {
      final repository = createRepository();
      await database.healthExaminationDao.upsert(
        HealthExaminationsCompanion.insert(
          id: 'legacy-1',
          isUpdated: const Value(true),
        ),
      );
      expect(await repository.getUpdated(), isEmpty);
    });
  });
  group('the profile row waits for a server identity', () {
    // `updateUserHealthProfile` re-stamps `healthPojo.userId = userModel?._id`
    // on **every** profile save (`HealthRepositoryImpl.kt:231`), and
    // `createPojo` writes the same value when it creates the row
    // (`HealthExaminationActivity.kt:377`). For a member whose account has not
    // uploaded that is null, `getUpdated()`'s `userId != ''` withholds the
    // row, and Kotlin uploads nothing until the account has a CouchDB id —
    // at which point `updateUserId` fills it in (`app_providers.dart:399`).
    //
    // The port's `?? userId` fallback wrote the device-local `'<millis>'` id
    // instead, which is neither null nor empty, so the row was selected and
    // POSTed to `/health` under a millisecond timestamp no server and no
    // other device can resolve to a person.

    test(
      'a member with no couchId gets a null userId, so nothing uploads',
      () async {
        final repository = createRepository();
        await database.userDao.upsert(
          const UsersCompanion(id: Value('1700000000000'), name: Value('ada')),
        );

        await repository.saveHealthProfile('1700000000000', {
          'emergencyContact': '555-0100',
        });

        final row = await database.healthExaminationDao.getByIdOrUserId(
          '1700000000000',
        );
        expect(row?.userId, isNull);
        expect(
          await repository.getUpdated(),
          isEmpty,
          reason:
              'Kotlin withholds this row until the account exists on a '
              'server; the fallback made it upload under a local timestamp',
        );
      },
    );

    test('the couchId is stamped in once the account uploads', () async {
      final repository = createRepository();
      await database.userDao.upsert(
        const UsersCompanion(id: Value('1700000000000'), name: Value('ada')),
      );
      await repository.saveHealthProfile('1700000000000', {
        'emergencyContact': '555-0100',
      });

      // The upload lands, and `app_providers`' `updateUserId` rewrites the
      // row — the third part of the rule.
      await database.userDao.updateUserSecurityData(
        localId: '1700000000000',
        couchId: 'org.couchdb.user:ada',
        rev: '1-a',
        passwordScheme: null,
        derivedKey: null,
        salt: null,
        iterations: null,
      );
      await database.healthExaminationDao.updateUserId(
        '1700000000000',
        'org.couchdb.user:ada',
      );

      final pending = await repository.getUpdated();
      expect(pending, hasLength(1));
      expect(
        HealthRepository.serialize(pending.single)['_id'],
        'org.couchdb.user:ada',
      );
    });

    test(
      'a later profile save re-stamps the couchId rather than dropping it',
      () async {
        final repository = createRepository();
        await database.userDao.upsert(
          const UsersCompanion(
            id: Value('1700000000000'),
            couchId: Value('org.couchdb.user:ada'),
            name: Value('ada'),
          ),
        );

        // `patientIdOf` prefers the couch id, which is the id the health
        // screens address a patient by once the account exists.
        await repository.saveHealthProfile('org.couchdb.user:ada', {
          'notes': 'first',
        });
        await repository.saveHealthProfile('org.couchdb.user:ada', {
          'notes': 'second',
        });

        final row = await database.healthExaminationDao.getByIdOrUserId(
          'org.couchdb.user:ada',
        );
        expect(row?.userId, 'org.couchdb.user:ada');
      },
    );
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
