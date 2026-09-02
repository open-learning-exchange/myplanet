import 'dart:convert';

import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/text_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../core/crypto/health_cipher.dart';
import '../core/utils/time_utils.dart';
import '../data/local/health_models.dart';

/// Port of `repository/HealthRepositoryImpl.kt`.
///
/// HealthRepository manages health examination records, including local storage
/// in Drift and sync with the CouchDB health database.
class HealthRepository {
  HealthRepository(
    this._api,
    this._dao,
    this._userDao, {
    ServerConfig? config,
    String Function()? createId,
  }) : _config = config,
       _createId = createId ?? _defaultId;

  final PlanetApi _api;
  final HealthExaminationDao _dao;
  final UserDao _userDao;
  final ServerConfig? _config;
  final String Function() _createId;

  static String _defaultId() =>
      'health-${DateTime.now().microsecondsSinceEpoch}';

  /// Get health examination by user id or examination id.
  Future<HealthExaminationRow?> getByIdOrUserId(String id) =>
      _dao.getByIdOrUserId(id);

  /// Get health examination by id.
  Future<HealthExaminationRow?> getById(String id) => _dao.getById(id);

  /// Alias for [getById] — fetches a single examination row by its id.
  /// Used by the examination detail dialog provider.
  Future<HealthExaminationRow?> getExaminationById(String id) =>
      _dao.getById(id);

  /// Get all examinations for a user.
  Future<List<HealthExaminationRow>> getForUser(String userId) =>
      _dao.getForUser(userId);

  /// Get examinations by profileId.
  Future<List<HealthExaminationRow>> getByProfileId(String profileId) =>
      _dao.getByProfileId(profileId);

  /// Get updated examinations that need syncing.
  Future<List<HealthExaminationRow>> getUpdated() => _dao.getUpdated();

  /// Get updated examinations for a specific user.
  Future<List<HealthExaminationRow>> getUpdatedForUser(String userId) =>
      _dao.getUpdatedForUser(userId);

  /// Create a new health examination.
  ///
  /// [userId] defaults to the row's own generated id, which is the invariant
  /// `_docToCompanion` maintains for every synced row (`userId: doc['_id']`)
  /// and what `HealthExaminationActivity.saveData` writes for a new one
  /// (`_id` and `userId` are both the same `generateIv()`). A patient's
  /// examinations are found through [profileId], never through `userId`, so
  /// giving two of them the patient's id makes `getByIdOrUserId`'s
  /// `getSingleOrNull` throw.
  Future<String> createExamination({
    String? userId,
    String? profileId,
    String? creatorId,
    required double temperature,
    required int pulse,
    String? bp,
    required double height,
    required double weight,
    String? vision,
    String? hearing,
    String? conditions,
    String? gender,
    int? age,
    bool selfExamination = false,
    String? planetCode,
    String? data,
    bool hasInfo = false,
  }) async {
    final id = _createId();
    final now = DateTime.now().millisecondsSinceEpoch;
    await _dao.upsert(
      HealthExaminationsCompanion.insert(
        id: id,
        userId: Value(userId ?? id),
        temperature: Value(temperature),
        pulse: Value(pulse),
        bp: Value(bp),
        height: Value(height),
        weight: Value(weight),
        vision: Value(vision),
        hearing: Value(hearing),
        conditions: Value(conditions),
        selfExamination: Value(selfExamination),
        planetCode: Value(planetCode),
        hasInfo: Value(hasInfo),
        profileId: Value(profileId),
        creatorId: Value(creatorId),
        gender: Value(gender),
        age: Value(age ?? 0),
        date: Value(now),
        data: Value(data),
        isUpdated: const Value(true),
      ),
    );
    return id;
  }

  /// Update an existing examination.
  Future<void> updateExamination(
    String id, {
    double? temperature,
    int? pulse,
    String? bp,
    double? height,
    double? weight,
    String? vision,
    String? hearing,
    String? conditions,
    String? data,
    bool? hasInfo,
    String? profileId,
    String? creatorId,
    String? gender,
    int? age,
    String? planetCode,
    bool? selfExamination,
    int? date,
  }) async {
    final existing = await _dao.getById(id);
    if (existing == null) return;

    await _dao.upsert(
      existing
          .toCompanion(false)
          .copyWith(
            temperature: Value(temperature ?? existing.temperature),
            pulse: Value(pulse ?? existing.pulse),
            bp: Value(bp ?? existing.bp),
            height: Value(height ?? existing.height),
            weight: Value(weight ?? existing.weight),
            vision: Value(vision ?? existing.vision),
            hearing: Value(hearing ?? existing.hearing),
            conditions: Value(conditions ?? existing.conditions),
            data: Value(data ?? existing.data),
            hasInfo: Value(hasInfo ?? existing.hasInfo),
            profileId: Value(profileId ?? existing.profileId),
            creatorId: Value(creatorId ?? existing.creatorId),
            gender: Value(gender ?? existing.gender),
            age: Value(age ?? existing.age),
            planetCode: Value(planetCode ?? existing.planetCode),
            selfExamination: Value(selfExamination ?? existing.selfExamination),
            date: Value(date ?? existing.date),
            isUpdated: const Value(true),
          ),
    );
  }

  /// Insert or update a health examination row.
  Future<void> upsert(HealthExaminationsCompanion row) => _dao.upsert(row);

  /// Insert or update multiple health examinations.
  Future<void> upsertAll(List<HealthExaminationsCompanion> rows) =>
      _dao.upsertAll(rows);

  /// Mark examination as uploaded with the server revision.
  Future<void> markUploaded(String id, String? rev) =>
      _dao.markUploaded(id, rev);

  /// Mark multiple examinations as uploaded.
  Future<void> markUploadedBatch(Map<String, String?> idToRev) async {
    for (final entry in idToRev.entries) {
      await _dao.markUploaded(entry.key, entry.value);
    }
  }

  /// Update the userId for an examination.
  Future<void> updateUserId(String id, String userId) =>
      _dao.updateUserId(id, userId);

  /// Encrypts an examination payload with the user's key, generating one on
  /// first use.
  ///
  /// Kotlin does this in `HealthExaminationActivity` before the record is
  /// saved; doing it here instead keeps every write path — form, sync,
  /// upload — on the same side of the cipher.
  Future<String?> encryptData(String userId, String plainJson) async {
    final user = await _userDao.ensureSecurityKeys(userId);
    if (user == null) return null;
    return HealthCipher.encrypt(plainJson, user.key, user.iv);
  }

  /// Reverses [encryptData], returning null for a blob this user cannot read.
  Future<String?> decryptData(String userId, String? encrypted) async {
    if (encrypted == null || encrypted.isEmpty) return null;
    final user = await _userDao.getById(userId);
    if (user == null) return null;
    return HealthCipher.decrypt(encrypted, user.key, user.iv);
  }

  /// Decrypts a single examination's `data` blob and returns it as an
  /// [Examination], or null if the blob is empty or cannot be read.
  ///
  /// Port of `HealthExamination.getEncryptedDataAsJson` — the patient's own
  /// key/iv decrypts their records, not the current viewer's.
  Future<Examination?> decryptExamination(
    String userId,
    HealthExaminationRow exam,
  ) async {
    final plain = await decryptData(userId, exam.data);
    if (plain == null || plain.isEmpty) return null;
    try {
      final decoded = jsonDecode(plain);
      return decoded is Map<String, dynamic>
          ? Examination.fromJson(decoded)
          : null;
    } catch (_) {
      return null;
    }
  }

  /// Decrypts the patient's existing health profile (the encrypted `data` blob
  /// on their examination row) and returns it as a [MyHealth], or null when
  /// there is no row or the blob cannot be read.
  ///
  /// Port of `UserRepositoryImpl.getHealthProfile`.
  Future<MyHealth?> getHealthProfile(String userId) async {
    final exam = await _dao.getByIdOrUserId(userId);
    if (exam == null || exam.data == null || exam.data!.isEmpty) return null;
    final plain = await decryptData(userId, exam.data);
    if (plain == null || plain.isEmpty) return null;
    try {
      final decoded = jsonDecode(plain);
      return decoded is Map<String, dynamic>
          ? MyHealth.fromJson(decoded)
          : null;
    } catch (_) {
      return null;
    }
  }

  /// Port of `HealthRepositoryImpl.initHealth` — a fresh profile carrying the
  /// `userKey` every one of the patient's examinations points back to through
  /// `profileId`.
  static MyHealth initHealth() => MyHealth(
    profile: MyHealthProfile(),
    userKey: HealthCipher.generateKey(),
    lastExamination: DateTime.now().millisecondsSinceEpoch,
  );

  /// Writes [health] to the patient's profile row, creating the row when the
  /// patient has none.
  ///
  /// Port of `HealthExaminationActivity.createPojo` plus the `pojo` half of
  /// `HealthRepositoryImpl.saveExamination`: the row's id is the patient's own
  /// id and its `data` is the encrypted [MyHealth]. Returns the row as stored,
  /// whose id `isSelfExamination` compares the signed-in user against.
  Future<HealthExaminationRow?> saveHealthProfileBlob(
    String userId,
    MyHealth health,
  ) async {
    final encrypted = await encryptData(userId, jsonEncode(health.toJson()));
    final existing = await _dao.getByIdOrUserId(userId);
    if (existing == null) {
      final user = await _userDao.getById(userId);
      await _dao.upsert(
        HealthExaminationsCompanion(
          id: Value(userId),
          userId: Value(user?.couchId ?? userId),
          data: Value(encrypted),
          isUpdated: const Value(true),
          date: Value(DateTime.now().millisecondsSinceEpoch),
        ),
      );
    } else {
      await _dao.upsert(
        HealthExaminationsCompanion(
          id: Value(existing.id),
          userId: Value(existing.userId),
          data: Value(encrypted),
          isUpdated: const Value(true),
        ),
      );
    }
    return _dao.getByIdOrUserId(userId);
  }

  /// Updates the user's health profile: writes the user fields (firstName,
  /// email, dob, birthPlace, etc.) to the [Users] row with `isUpdated = true`,
  /// then decrypts the existing health profile, updates the emergency-contact
  /// and special-needs fields, re-encrypts it, and saves it to the
  /// examination row's `data` column.
  ///
  /// Port of `UserRepositoryImpl.updateUserHealthProfile`. The `data` map
  /// carries every field the Kotlin `AddHealthActivity.createMyHealth`
  /// gathers: firstName, middleName, lastName, email, dob (dd-MM-yyyy),
  /// birthPlace, phoneNumber, emergencyContactName, emergencyContact,
  /// emergencyContactType, specialNeeds, notes.
  Future<void> saveHealthProfile(
    String userId,
    Map<String, String> data,
  ) async {
    final user = await _userDao.ensureSecurityKeys(userId);
    if (user == null) return;

    // Update user fields and mark the row dirty for upload.
    await _userDao.upsert(
      UsersCompanion(
        id: Value(user.id),
        firstName: Value(data['firstName']?.trim()),
        middleName: Value(data['middleName']?.trim()),
        lastName: Value(data['lastName']?.trim()),
        email: Value(data['email']?.trim()),
        phoneNumber: Value(data['phoneNumber']?.trim()),
        birthPlace: Value(data['birthPlace']?.trim()),
        dob: data['dob'] != null && data['dob']!.trim().isNotEmpty
            ? Value(TimeUtils.convertDDMMYYYYToISO(data['dob']))
            : Value(user.dob),
        isUpdated: const Value(true),
      ),
    );

    // Load or create the health profile.
    var health = await getHealthProfile(userId) ?? MyHealth();
    if (health.userKey == null || health.userKey!.isEmpty) {
      health = health.copyWith(userKey: HealthCipher.generateKey());
    }
    // Port of `UserRepositoryImpl.updateUserHealthProfile` field rules:
    // emergencyContactName/specialNeeds/notes are overwritten unconditionally,
    // while emergencyContact/emergencyContactType keep their existing value
    // when the new one is blank (so a partial edit does not clear a number).
    final existing = health.profile ?? MyHealthProfile();
    final newContact = data['emergencyContact']?.trim() ?? '';
    final newType = data['emergencyContactType']?.trim() ?? '';
    final profile = existing.copyWith(
      // Trimmed like every other field here, and like the Kotlin, which reads
      // each of these as `(userData[k] as? String)?.trim() ?: ""`. Without the
      // trim a value typed with a trailing space stored differently in the two
      // apps, and a whitespace-only entry stored as whitespace instead of "".
      emergencyContactName: data['emergencyContactName']?.trim() ?? '',
      emergencyContact: newContact.isEmpty
          ? existing.emergencyContact
          : newContact,
      emergencyContactType: newType.isEmpty
          ? existing.emergencyContactType
          : newType,
      specialNeeds: data['specialNeeds']?.trim() ?? '',
      notes: data['notes']?.trim() ?? '',
    );
    health = health.copyWith(profile: profile);

    // Re-encrypt and save to the examination row.
    final encrypted = await encryptData(userId, jsonEncode(health.toJson()));
    final exam = await _dao.getByIdOrUserId(userId);
    if (exam == null) {
      await _dao.upsert(
        HealthExaminationsCompanion(
          id: Value(userId),
          userId: Value(user.couchId ?? user.id),
          data: Value(encrypted),
          isUpdated: const Value(true),
          date: Value(DateTime.now().millisecondsSinceEpoch),
        ),
      );
    } else {
      await _dao.upsert(
        HealthExaminationsCompanion(
          id: Value(exam.id),
          userId: Value(exam.userId),
          data: Value(encrypted),
          isUpdated: const Value(true),
        ),
      );
    }
  }

  /// Parse examination conditions from JSON string.
  Map<String, bool> parseConditions(String? conditionsJson) {
    if (conditionsJson == null || conditionsJson.isEmpty) return {};
    try {
      final decoded = jsonDecode(conditionsJson);
      if (decoded is! Map) return {};
      return decoded.map((k, v) => MapEntry(k.toString(), v == true));
    } catch (e) {
      return {};
    }
  }

  /// Convert database row to domain model.
  static HealthExamination rowToModel(HealthExaminationRow row) {
    return HealthExamination(
      id: row.id,
      couchId: row.couchId,
      rev: row.rev,
      userId: row.userId,
      temperature: row.temperature,
      pulse: row.pulse,
      bp: row.bp,
      height: row.height,
      weight: row.weight,
      vision: row.vision,
      hearing: row.hearing,
      conditions: row.conditions,
      selfExamination: row.selfExamination,
      planetCode: row.planetCode,
      hasInfo: row.hasInfo,
      profileId: row.profileId,
      creatorId: row.creatorId,
      gender: row.gender,
      age: row.age,
      date: row.date,
      data: row.data,
      isUpdated: row.isUpdated,
    );
  }

  /// Convert domain model to database companion for insertion.
  static HealthExaminationsCompanion modelToCompanion(
    HealthExamination model, {
    bool update = true,
  }) {
    return HealthExaminationsCompanion(
      id: Value(model.id),
      couchId: Value(model.couchId),
      rev: Value(model.rev),
      userId: Value(model.userId),
      temperature: Value(model.temperature),
      pulse: Value(model.pulse),
      bp: Value(model.bp),
      height: Value(model.height),
      weight: Value(model.weight),
      vision: Value(model.vision),
      hearing: Value(model.hearing),
      conditions: Value(model.conditions),
      selfExamination: Value(model.selfExamination),
      planetCode: Value(model.planetCode),
      hasInfo: Value(model.hasInfo),
      profileId: Value(model.profileId),
      creatorId: Value(model.creatorId),
      gender: Value(model.gender),
      age: Value(model.age),
      date: Value(model.date),
      data: Value(model.data),
      isUpdated: Value(model.isUpdated),
    );
  }

  // ─────────────────────────────────────────────────────────────────────
  // Patient management — port of `HealthRepositoryImpl` methods added in
  // Kotlin 962e1e736. Moves user/patient queries from `UserRepositoryImpl`
  // so the health screens own their domain.
  // ─────────────────────────────────────────────────────────────────────

  /// Returns a single patient by its local or CouchDB id.
  Future<UserRow?> getPatientById(String id) => _userDao.getById(id);

  /// All users sorted by [fieldName] — "joinDate" (int) or "name" (string).
  Future<List<UserRow>> getPatientsSortedBy(
    String fieldName, {
    bool descending = false,
  }) async {
    final users = await _userDao.getAllUsers();
    return _sortUsers(users, fieldName, descending);
  }

  /// Users whose name/firstName/lastName contains [query], sorted.
  /// A blank query returns all users (matching Kotlin's `searchUsers`).
  Future<List<UserRow>> searchPatients(
    String query, {
    String sortField = 'joinDate',
    bool descending = false,
  }) async {
    final users = query.isEmpty
        ? await _userDao.getAllUsers()
        : await _userDao.search(query);
    return _sortUsers(users, sortField, descending);
  }

  static List<UserRow> _sortUsers(
    List<UserRow> users,
    String fieldName,
    bool descending,
  ) {
    String lower(String? v) => (v ?? '').toLowerCase();
    int cmp(UserRow a, UserRow b) {
      switch (fieldName) {
        case 'joinDate':
          return a.joinDate.compareTo(b.joinDate);
        case 'name':
          return lower(a.name).compareTo(lower(b.name));
        case 'firstName':
          return lower(a.firstName).compareTo(lower(b.firstName));
        case 'lastName':
          return lower(a.lastName).compareTo(lower(b.lastName));
        default:
          return 0;
      }
    }

    final sorted = [...users]..sort(cmp);
    return descending ? sorted.reversed.toList() : sorted;
  }

  /// Decrypts a patient's health entry and bundles it with the examination
  /// history and the users who authored those examinations.
  ///
  /// Port of `HealthRepositoryImpl.getPatientHealthRecords` (Kotlin
  /// 962e1e736), moved here from `UserRepositoryImpl.getHealthRecordsAndAssociatedUsers`.
  Future<HealthRecord?> getPatientHealthRecords(
    String userId,
    UserRow currentUser,
  ) async {
    final mh = await _dao.getByIdOrUserId(userId);
    if (mh == null) return null;

    final json = await _decryptForUser(mh.data, currentUser);
    MyHealth? mm;
    if (json != null && json.isNotEmpty) {
      try {
        final decoded = jsonDecode(json);
        mm = decoded is Map<String, dynamic>
            ? MyHealth.fromJson(decoded)
            : null;
      } catch (_) {
        mm = null;
      }
    }
    if (mm == null) return null;

    final list = await _dao.getByProfileId(mm.userKey ?? '');
    if (list.isEmpty) {
      return HealthRecord(
        healthPojo: mh,
        healthProfile: mm,
        examinations: const [],
        userMap: const {},
      );
    }

    // Collect the distinct creator IDs from each examination's encrypted data,
    // and keep which record named which — `submitExaminations` resolves the
    // examiner from this same decrypted `createdBy`, never from a column.
    final userIds = <String>{};
    final createdByOf = <String, String>{};
    for (final exam in list) {
      final plain = await _decryptForUser(exam.data, currentUser);
      if (plain != null && plain.isNotEmpty) {
        try {
          final decoded = jsonDecode(plain);
          if (decoded is Map<String, dynamic>) {
            final createdBy = decoded['createdBy']?.toString();
            if (createdBy != null && createdBy.isNotEmpty) {
              userIds.add(createdBy);
              createdByOf[exam.id] = createdBy;
            }
          }
        } catch (_) {}
      }
    }

    final userMap = <String, UserRow>{};
    if (userIds.isNotEmpty) {
      final all = await _userDao.getAllUsers();
      for (final u in all) {
        if (userIds.contains(u.id)) {
          userMap[u.id] = u;
        }
      }
    }

    return HealthRecord(
      healthPojo: mh,
      healthProfile: mm,
      examinations: list,
      userMap: userMap,
      createdByOf: createdByOf,
    );
  }

  Future<String?> _decryptForUser(String? encrypted, UserRow user) async {
    if (encrypted == null || encrypted.isEmpty) return null;
    return HealthCipher.decrypt(encrypted, user.key, user.iv);
  }

  /// Port of `TransactionSyncManager.syncDashboardKeyId` — pull the health
  /// AES key/IV a user's account published to its per-user CouchDB database
  /// (`userdb-<hex(planetCode)>-<hex(name)>`), so records written on another
  /// device decrypt here.
  ///
  /// A role containing "health" syncs every locally-known synced account
  /// (`syncAllHealthData`); anything else syncs only the signed-in user
  /// (`syncKeyIv`). The user-credentialed header — not the satellite PIN — is
  /// what the per-user database accepts.
  Future<void> syncDashboardKeyIv({
    required String userName,
    required String password,
    required String? currentUserId,
    String? role,
  }) async {
    final config = _config;
    if (config == null) return;
    final authHeader = UrlUtils.basicAuthHeader(userName, password);

    final List<UserRow> users;
    if (role != null && role.contains('health')) {
      users = await _userDao.getUsersForHealthSync();
    } else {
      final current = currentUserId == null
          ? null
          : await _userDao.getById(currentUserId);
      users = [?current];
    }

    for (final user in users) {
      await _syncHealthKeyIvFor(config, user, authHeader);
    }
  }

  /// Port of `TransactionSyncManager.syncHealthData` for one user: read the
  /// first document of the user's `userdb-*` database and store its key/IV.
  ///
  /// Failures are swallowed exactly as the Kotlin's
  /// `catch (e: Exception) { e.printStackTrace() }` — one unreachable account
  /// must not fail the rest of the batch, and the dashboard never surfaces
  /// this sync; a failure just means the key stays local.
  Future<void> _syncHealthKeyIvFor(
    ServerConfig config,
    UserRow user,
    String authHeader,
  ) async {
    // Kotlin's string interpolation renders a null planetCode/name as the
    // literal "null", and `?.let` keeps `toHex` itself from ever seeing null.
    final table =
        'userdb-${user.planetCode == null ? 'null' : toHexString(user.planetCode!)}-'
        '${user.name == null ? 'null' : toHexString(user.name!)}';
    try {
      final dbBase = UrlUtils.dbUrl(config);
      final allDocs = await _api.getJsonObject(
        '$dbBase/$table/_all_docs',
        authHeader: authHeader,
      );
      if (allDocs is! NetworkSuccess<Map<String, dynamic>>) return;
      final rows = allDocs.data['rows'];
      if (rows is! List || rows.isEmpty) return;
      final first = rows.first;
      if (first is! Map<String, dynamic>) return;
      final docId = JsonUtils.getString('id', first);
      if (docId.isEmpty) return;

      final docResult = await _api.getJsonObject(
        '$dbBase/$table/$docId',
        authHeader: authHeader,
      );
      if (docResult is! NetworkSuccess<Map<String, dynamic>>) return;
      final key = JsonUtils.getString('key', docResult.data);
      final iv = JsonUtils.getString('iv', docResult.data);
      if (key.isEmpty && iv.isEmpty) return;
      await _userDao.markUserKeyIvSaved(user.id, key, iv);
    } catch (_) {
      // Swallowed — see the doc comment.
    }
  }

  /// Sync health examinations from CouchDB.
  Future<SyncResult> sync({void Function(SyncProgress)? onProgress}) async {
    if (_config == null) return const SyncFailed('No server config');

    final config = _config;
    final dbUrl = UrlUtils.dbUrl(config);
    final auth = UrlUtils.authHeader(config);

    final countResult = await _api.getJsonObject(
      '$dbUrl/health/_all_docs?limit=0',
      authHeader: auth,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }
    final total = JsonUtils.getInt('total_rows', countResult.data);
    if (total == 0) {
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    const batchSize = 100;
    var skip = 0;
    var synced = 0;
    while (skip < total) {
      final result = await _api.getJsonObject(
        '$dbUrl/health/_all_docs?include_docs=true&limit=$batchSize&skip=$skip',
        authHeader: auth,
      );
      if (result is! NetworkSuccess<Map<String, dynamic>>) {
        return SyncFailed(describeNetworkFailure(result));
      }
      final rows = result.data['rows'];
      if (rows is! List || rows.isEmpty) break;

      final documents = rows
          .whereType<Map<String, dynamic>>()
          .map((row) => JsonUtils.getObject('doc', row))
          .whereType<Map<String, dynamic>>()
          .where((doc) => !doc['_id'].toString().startsWith('_design/'))
          .toList(growable: false);

      await cacheDocuments(documents);
      synced += documents.length;
      skip += rows.length;
      onProgress?.call(
        SyncProgress(completed: skip.clamp(0, total), total: total),
      );
    }
    return SyncComplete(synced);
  }

  /// Cache documents from the server, preserving local edits.
  Future<int> cacheDocuments(List<Map<String, dynamic>> documents) async {
    if (documents.isEmpty) return 0;
    final ids = documents
        .map((doc) => doc['_id']?.toString() ?? '')
        .where((id) => id.isNotEmpty)
        .toList(growable: false);

    final existing = <String, HealthExaminationRow>{};
    for (final row in await _dao.getForUser(ids.first)) {
      existing[row.id] = row;
    }
    // Also fetch by id
    for (final id in ids) {
      final row = await _dao.getById(id);
      if (row != null) existing[id] = row;
    }

    final rows = <HealthExaminationsCompanion>[];
    for (final doc in documents) {
      final id = doc['_id']?.toString() ?? '';
      final current = existing[id];
      // Don't overwrite locally edited rows
      if (current?.isUpdated == true) continue;
      rows.add(_docToCompanion(doc, existing: current));
    }
    await _dao.upsertAll(rows);
    return documents.length;
  }

  static HealthExaminationsCompanion _docToCompanion(
    Map<String, dynamic> doc, {
    HealthExaminationRow? existing,
  }) {
    return HealthExaminationsCompanion(
      id: Value(doc['_id']?.toString() ?? ''),
      couchId: Value(doc['_id']?.toString()),
      rev: Value(doc['_rev']?.toString()),
      userId: Value(doc['_id']?.toString()),
      temperature: Value(_parseDouble(doc['temperature'])),
      pulse: Value(_parseInt(doc['pulse'])),
      bp: Value(doc['bp']?.toString()),
      height: Value(_parseDouble(doc['height'])),
      weight: Value(_parseDouble(doc['weight'])),
      vision: Value(doc['vision']?.toString()),
      hearing: Value(doc['hearing']?.toString()),
      conditions: Value(conditionsJsonFromDoc(doc['conditions'])),
      selfExamination: Value(doc['selfExamination'] == true),
      planetCode: Value(doc['planetCode']?.toString()),
      hasInfo: Value(doc['hasInfo'] == true),
      profileId: Value(doc['profileId']?.toString()),
      creatorId: Value(doc['creatorId']?.toString()),
      gender: Value(doc['gender']?.toString()),
      age: Value(_parseInt(doc['age'])),
      date: Value(_parseInt(doc['date'])),
      data: Value(doc['data']?.toString()),
      isUpdated: Value(existing?.isUpdated ?? false),
    );
  }

  static double _parseDouble(dynamic value) {
    if (value == null) return 0;
    if (value is double) return value;
    if (value is int) return value.toDouble();
    if (value is String && value.isNotEmpty) {
      return double.tryParse(value) ?? 0;
    }
    return 0;
  }

  static int _parseInt(dynamic value) {
    if (value == null) return 0;
    if (value is int) return value;
    if (value is double) return value.toInt();
    if (value is String && value.isNotEmpty) {
      return int.tryParse(value) ?? 0;
    }
    return 0;
  }

  /// Serialize a row to JSON for upload.
  ///
  /// The document is keyed on `userId`, not on the row's own id:
  /// `if (!health.userId.isNullOrEmpty()) object.addProperty("_id", health.userId)`
  /// (`HealthExamination.kt:96`). The two differ for the profile row of a
  /// member registered on this device — `createPojo` writes `_id` = the
  /// patient id the screen was opened with and `userId` = the patient's
  /// CouchDB id, which `app_providers`' `updateUserId` fills in once the
  /// account uploads — and `userId` is the id the sync-in direction would key
  /// the row on (`_docToCompanion`: `userId: doc['_id']`), so it is the
  /// document's identity in both directions.
  ///
  /// A blank `userId` omits `_id` entirely, which would make CouchDB mint a
  /// fresh document on every drain. That branch is unreachable from the
  /// upload path because [HealthExaminationDao.getUpdated] excludes those rows
  /// — `userId != ''` — and the two rules only work as a pair.
  static Map<String, dynamic> serialize(HealthExaminationRow row) {
    final docId = row.userId ?? '';
    return {
      if (docId.isNotEmpty) '_id': docId,
      if (row.rev != null && row.rev!.isNotEmpty) '_rev': row.rev,
      if (row.data != null) 'data': row.data,
      'temperature': row.temperature,
      'pulse': row.pulse,
      if (row.bp != null) 'bp': row.bp,
      'height': row.height,
      'weight': row.weight,
      if (row.vision != null) 'vision': row.vision,
      if (row.hearing != null) 'hearing': row.hearing,
      'date': row.date,
      'selfExamination': row.selfExamination,
      if (row.planetCode != null) 'planetCode': row.planetCode,
      'hasInfo': row.hasInfo,
      if (row.profileId != null) 'profileId': row.profileId,
      if (row.creatorId != null) 'creatorId': row.creatorId,
      if (row.gender != null) 'gender': row.gender,
      'age': row.age,
      // `addJson(object, "conditions", gson.fromJson(conditions, JsonObject))`
      // — a nested object, omitted when null or empty. Sending the stored JSON
      // *string* instead put a primitive where `getJsonObject` expects an
      // object, so Kotlin read no conditions at all and its next save of the
      // same record overwrote them with its own map.
      if (conditionsObject(row.conditions) != null)
        'conditions': conditionsObject(row.conditions),
    };
  }

  /// The `conditions` map as a JSON object, or null when there is nothing to
  /// send — `JsonUtils.addJson` skips a null or empty object.
  static Map<String, dynamic>? conditionsObject(String? conditionsJson) {
    if (conditionsJson == null || conditionsJson.isEmpty) return null;
    try {
      final decoded = jsonDecode(conditionsJson);
      if (decoded is! Map || decoded.isEmpty) return null;
      return decoded.map((key, value) => MapEntry(key.toString(), value));
    } catch (_) {
      return null;
    }
  }

  /// The stored form of a synced document's `conditions`.
  ///
  /// `conditions = gson.toJson(getJsonObject("conditions", act))` — the nested
  /// object, re-encoded as JSON. `toString()` on the decoded Map, which is
  /// what this did, yields `{Malaria: true}`: not JSON, so `parseConditions`
  /// threw and the record read as having no conditions, and saving that edit
  /// wrote the empty map back over them.
  static String? conditionsJsonFromDoc(dynamic value) {
    if (value is Map) return jsonEncode(value);
    if (value is String && value.isNotEmpty) return value;
    return null;
  }
}
