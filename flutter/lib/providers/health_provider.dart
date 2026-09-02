import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/utils/time_utils.dart';
import '../core/sync/sync_result.dart';
import 'sync_state.dart';
import '../data/local/app_database.dart';
import '../data/local/health_models.dart';
import '../repository/health_repository.dart';
import 'app_providers.dart';
import 'session_provider.dart';

/// Health data for the current user.
class HealthData {
  final UserRow? user;
  final HealthExaminationRow? examination;
  final MyHealth? myHealth;
  final List<HealthExaminationRow> examinations;
  final Map<String, UserRow> userMap;

  /// The examiner each examination names, by examination id — see
  /// [HealthRecord.createdByOf].
  final Map<String, String> createdByOf;

  HealthData({
    this.user,
    this.examination,
    this.myHealth,
    this.examinations = const [],
    this.userMap = const {},
    this.createdByOf = const {},
  });
}

/// One patient's health data, keyed by the id the health screens carry.
///
/// Port of `HealthViewModel.loadHealthData(userId)`, which takes the id
/// `MyHealthFragment` puts in the intent — the *selected* patient's, which for
/// a health provider is not their own. Keyed rather than session-scoped for
/// exactly that reason: as a session-scoped provider it could only ever
/// describe the signed-in user, so `AddHealthActivity`'s form had no way to
/// reach the patient whose profile it was opened on.
final healthDataProvider = FutureProvider.family<HealthData?, String>((
  ref,
  userId,
) async {
  if (userId.isEmpty) return null;

  final repo = ref.watch(healthRepositoryProvider);

  final userDao = ref.watch(userDaoProvider);
  final user = await userDao.getById(userId);
  final examination = await repo.getByIdOrUserId(userId);

  // `data` is AES ciphertext, exactly as `HealthExaminationActivity` writes
  // it. Parsing it as JSON — which is what this did — always threw, and the
  // catch turned every health record into a blank screen.
  final myHealth = _decodeHealth(
    await repo.decryptData(userId, examination?.data),
  );

  // No examination list: `loadHealthData` carries none, and `getForUser`
  // matches on `userId`, which an examination row does not key by — see
  // `createExamination`. The one consumer (`AddHealthActivity`'s form) reads
  // the user and the profile.
  return HealthData(user: user, examination: examination, myHealth: myHealth);
});

/// Decrypts a single examination's encrypted `data` blob for display in the
/// examination detail dialog. The patient's own key/iv decrypts their records.
/// Port of `HealthExamination.getEncryptedDataAsJson`.
final examinationDetailProvider = FutureProvider.autoDispose
    .family<Examination?, ({String userId, String examId})>((
      ref,
      params,
    ) async {
      final repo = ref.watch(healthRepositoryProvider);
      final exam = await repo.getExaminationById(params.examId);
      if (exam == null) return null;
      return repo.decryptExamination(params.userId, exam);
    });

MyHealth? _decodeHealth(String? plainJson) {
  if (plainJson == null || plainJson.isEmpty) return null;
  try {
    final decoded = jsonDecode(plainJson);
    return decoded is Map<String, dynamic> ? MyHealth.fromJson(decoded) : null;
  } catch (_) {
    return null;
  }
}

Examination? _decodeExamination(String? plainJson) {
  if (plainJson == null || plainJson.isEmpty) return null;
  try {
    final decoded = jsonDecode(plainJson);
    return decoded is Map<String, dynamic>
        ? Examination.fromJson(decoded)
        : null;
  } catch (_) {
    return null;
  }
}

/// State for health examination form.
class ExaminationState {
  final bool isLoading;
  final bool isSaving;
  final bool saved;
  final String? error;
  final HealthExaminationRow? examination;
  final Map<String, bool> conditions;
  final Examination? examData;

  ExaminationState({
    this.isLoading = false,
    this.isSaving = false,
    this.saved = false,
    this.error,
    this.examination,
    this.conditions = const {},
    this.examData,
  });

  /// [clearError] drops a stale message: `error ?? this.error` alone cannot,
  /// so a failed save's error outlived a successful retry and the screen went
  /// on reporting a failure that had already been fixed.
  ExaminationState copyWith({
    bool? isLoading,
    bool? isSaving,
    bool? saved,
    String? error,
    bool clearError = false,
    HealthExaminationRow? examination,
    Map<String, bool>? conditions,
    Examination? examData,
  }) {
    return ExaminationState(
      isLoading: isLoading ?? this.isLoading,
      isSaving: isSaving ?? this.isSaving,
      saved: saved ?? this.saved,
      error: clearError ? null : (error ?? this.error),
      examination: examination ?? this.examination,
      conditions: conditions ?? this.conditions,
      examData: examData ?? this.examData,
    );
  }
}

/// Hands every un-uploaded examination to the durable outbox.
///
/// The uploader queues by `isUpdated`, so this covers a new examination and an
/// edit to an old one alike. Nothing else in the app calls it — without this
/// the records stay on the handset that recorded them.
class HealthQueue {
  const HealthQueue(this._ref);

  final Ref _ref;

  Future<int> queuePending() async {
    final config = _ref.read(serverConfigProvider);
    if (config == null) return 0;
    // `await`ed rather than read with `valueOrNull`: nothing on the
    // examination form watches the session, so the synchronous read was still
    // `AsyncLoading` and every queued row carried a null user. The `await`
    // sits inside the caller's `try` for the reason Phase 100 records — a
    // future can reject where `valueOrNull` could not.
    final session = await _ref.read(sessionProvider.future);
    return _ref
        .read(healthUploaderProvider)
        .queuePending(config: config, userId: session?.id);
  }
}

final healthQueueProvider = Provider<HealthQueue>(HealthQueue.new);

/// Pull of the `health` database, giving `HealthRepository.sync` its first
/// caller — it was written and never invoked, so a device that had not
/// recorded its own examinations showed an empty screen forever.
class HealthSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) => ref.read(healthRepositoryProvider).sync(onProgress: onProgress);
}

final healthSyncProvider = NotifierProvider<HealthSyncNotifier, SyncUiState>(
  HealthSyncNotifier.new,
);

/// Single-flight driver for [HealthRepository.syncDashboardKeyIv], fired from
/// the home screen when a signed-in non-guest user has no local health key.
///
/// Port of `DashboardViewModel.syncKeyId`: the `syncJob?.isActive` re-entrancy
/// guard (9f3fac1d9) is the `SyncRunning` check below, and the `SyncUiState`
/// events mirror its `_syncKeyIdEvent` emissions. Unlike [SyncNotifier] this
/// does not stamp the last-sync preference — the Kotlin records that only for
/// full syncs, and a key fetch is not one. Master's `di?.show()`/`di?.dismiss()`
/// progress dialog is deliberately not ported: `di` is never instantiated on
/// master, so the calls are no-ops and nothing ever renders.
class HealthKeyIvSyncNotifier extends Notifier<SyncUiState> {
  @override
  SyncUiState build() => const SyncIdle();

  Future<void> sync(String? role) async {
    if (state is SyncRunning) return;

    final config = ref.read(serverConfigProvider);
    final session = ref.read(sessionProvider).valueOrNull;
    if (config == null || session == null) return;

    state = const SyncRunning(SyncProgress(completed: 0, total: 0));
    try {
      // A missing password becomes "", as `SecurePrefs.getPassword(...) ?: ""`
      // does; the per-user request then fails and is swallowed per account.
      final password = await ref.read(planetPrefsProvider).readPassword() ?? '';
      await ref
          .read(healthRepositoryProvider)
          .syncDashboardKeyIv(
            userName: session.name ?? '',
            password: password,
            currentUserId: session.id,
            role: role,
          );
      state = const SyncSucceeded(0);
    } catch (error) {
      state = SyncErrored('$error');
    }
  }
}

final healthKeyIvSyncProvider =
    NotifierProvider<HealthKeyIvSyncNotifier, SyncUiState>(
      HealthKeyIvSyncNotifier.new,
    );

/// Notifier for managing examination form state.
class ExaminationNotifier extends StateNotifier<ExaminationState> {
  final HealthRepository _repo;
  final String? _userId;
  final String? _examinationId;
  final Future<void> Function()? _onSaved;
  final Future<UserRow?> Function()? _currentUser;

  ExaminationNotifier(
    this._repo,
    this._userId,
    this._examinationId, {
    Future<void> Function()? onSaved,
    Future<UserRow?> Function()? currentUser,
  }) : _onSaved = onSaved,
       _currentUser = currentUser,
       super(ExaminationState()) {
    _loadData();
  }

  final Completer<void> _loaded = Completer<void>();

  /// Completes once the initial load has settled, whatever it found.
  ///
  /// This is `viewModel.state.first { !it.isLoading }`, which
  /// `HealthExaminationActivity` awaits before it prefills the form and
  /// before it enables Save.
  Future<void> get loaded => _loaded.future;

  Future<void> _loadData() async {
    state = state.copyWith(isLoading: true);
    try {
      // `loadData(userId, examinationId)` loads `examination` **only** when
      // an id was given; `getByIdOrUserId(userId)` is the patient's profile
      // row, which it keeps in a separate `pojo`. Loading that row into
      // `examination` turned every new record into an edit of it: `save` took
      // the update branch, so the second examination overwrote the first, and
      // once a profile row existed it overwrote the health profile itself —
      // emergency contact, special needs and the `userKey` every examination
      // is found by.
      final exam = _examinationId == null
          ? null
          : await _repo.getById(_examinationId);
      state = state.copyWith(
        isLoading: false,
        examination: exam,
        conditions: _repo.parseConditions(exam?.conditions),
        examData: await _decryptExamination(exam),
      );
    } catch (e) {
      state = state.copyWith(isLoading: false, error: e.toString());
    } finally {
      if (!_loaded.isCompleted) _loaded.complete();
    }
  }

  /// The blob is encrypted with the *patient's* key/iv, which is what
  /// `initExamination`'s `examination.getEncryptedDataAsJson(user)` passes —
  /// `user` there being `getHealthEntry(userId).first`, the patient.
  ///
  /// Reading the owner off the row instead is wrong for every synced
  /// examination: `_docToCompanion` maps `userId` to the document's own `_id`,
  /// so the lookup found no user, the decrypt returned null, and the form
  /// opened blank on a record that was perfectly readable.
  Future<Examination?> _decryptExamination(HealthExaminationRow? exam) async {
    if (exam == null) return null;
    final owner = _userId ?? exam.userId;
    if (owner == null) return null;
    return _decodeExamination(await _repo.decryptData(owner, exam.data));
  }

  Future<void> save({
    required double temperature,
    required int pulse,
    required String? bp,
    required double height,
    required double weight,
    required String? vision,
    required String? hearing,
    required String? allergies,
    required String? diagnosis,
    required String? medications,
    required String? immunizations,
    required String? treatments,
    required String? notes,
    required String? referrals,
    required String? tests,
    required String? xrays,
    required Map<String, bool> conditions,
  }) async {
    // `saveExamination` opens with `if (_isSaving.value) return`. Without the
    // guard a second Save while the first was in flight re-ran the create
    // branch — `state.examination` is still null then — and wrote the patient
    // a second examination for one form.
    if (state.isSaving) return;
    state = state.copyWith(isSaving: true, clearError: true);
    try {
      final conditionsJson = jsonEncode(conditions);
      final patientId = _userId;

      // `getHealthEntry(userId)` and `userSessionManager.getUserModel()`: the
      // patient carries the record's gender/age/planetCode, and the signed-in
      // user is the examiner `sign.createdBy` names.
      final patient = patientId == null
          ? null
          : await _repo.getPatientById(patientId);
      final signedIn = await _currentUser?.call();

      final examData = Examination(
        allergies: allergies,
        diagnosis: diagnosis,
        medications: medications,
        immunizations: immunizations,
        treatments: treatments,
        notes: notes,
        referrals: referrals,
        tests: tests,
        xrays: xrays,
        // `sign.createdBy = currentUser?._id`. Unset, every record read back
        // as self-recorded and `getPatientHealthRecords` had no creator to
        // resolve, so the card named no examiner.
        createdBy: signedIn?.couchId,
      );

      // `createPojo`: the patient's profile row holds the encrypted MyHealth
      // and mints the `userKey`. This is the link the whole screen runs on —
      // `getPatientHealthRecords` lists a patient's examinations with
      // `getByProfileId(userKey)`, so an examination saved without one was
      // invisible the moment it was written, and giving it the patient's
      // `userId` instead made `getByIdOrUserId`'s `getSingleOrNull` throw as
      // soon as a second record existed.
      var health = patientId == null
          ? null
          : await _repo.getHealthProfile(patientId);
      health = (health ?? HealthRepository.initHealth()).copyWith(
        lastExamination: DateTime.now().millisecondsSinceEpoch,
      );
      final profileRow = patientId == null
          ? null
          : await _repo.saveHealthProfileBlob(patientId, health);

      // `examination?.isSelfExamination = currentUser?._id == pojo?._id`,
      // null equality included.
      final isSelfExamination = signedIn?.couchId == profileRow?.id;

      // Encrypted before it reaches the database, so the plaintext never
      // touches SQLite and the upload carries ciphertext to CouchDB — the
      // property Kotlin has and the port was about to lose. The patient's
      // key/iv, as `encrypt(json, user.key, user.iv)` uses.
      final data = patientId == null
          ? null
          : await _repo.encryptData(patientId, jsonEncode(examData.toJson()));

      final hasInfo =
          allergies?.isNotEmpty == true ||
          diagnosis?.isNotEmpty == true ||
          medications?.isNotEmpty == true ||
          immunizations?.isNotEmpty == true ||
          treatments?.isNotEmpty == true ||
          notes?.isNotEmpty == true ||
          referrals?.isNotEmpty == true ||
          tests?.isNotEmpty == true ||
          xrays?.isNotEmpty == true;

      // `saveData` re-stamps all of these on every save, an edit included,
      // and moves the date to now.
      if (state.examination != null) {
        await _repo.updateExamination(
          state.examination!.id,
          temperature: temperature,
          pulse: pulse,
          bp: bp,
          height: height,
          weight: weight,
          vision: vision,
          hearing: hearing,
          conditions: conditionsJson,
          data: data,
          hasInfo: hasInfo,
          profileId: health.userKey,
          creatorId: health.userKey,
          gender: patient?.gender,
          age: TimeUtils.getAge(patient?.dob),
          planetCode: patient?.planetCode,
          selfExamination: isSelfExamination,
          date: DateTime.now().millisecondsSinceEpoch,
        );
      } else if (patientId != null) {
        // No `userId`: the row's own id fills it, which is what
        // `_id`/`userId` both being one `generateIv()` means in Kotlin.
        await _repo.createExamination(
          temperature: temperature,
          pulse: pulse,
          bp: bp,
          height: height,
          weight: weight,
          vision: vision,
          hearing: hearing,
          conditions: conditionsJson,
          data: data,
          hasInfo: hasInfo,
          profileId: health.userKey,
          creatorId: health.userKey,
          gender: patient?.gender,
          age: TimeUtils.getAge(patient?.dob),
          planetCode: patient?.planetCode,
          selfExamination: isSelfExamination,
        );
      }

      // Queued from inside `save` rather than from each screen: an
      // examination that reaches the database and not the outbox is the
      // failure this whole path exists to prevent.
      //
      // In its own `try` because the row is already durable and carries
      // `isUpdated`, so the next drain queues it anyway: a server config that
      // is not loaded yet, or a session that will not resolve, is not a
      // failure to save, and reporting it as one would tell the examiner their
      // record was lost. `saveExamination`'s own failure — the database write
      // — is what the Kotlin's false `saveResult` means. Phase 103's
      // user-information screen draws the same line.
      try {
        await _onSaved?.call();
      } catch (_) {}

      state = state.copyWith(isSaving: false, saved: true);
    } catch (e) {
      state = state.copyWith(isSaving: false, error: e.toString());
    }
  }
}

/// Provider for examination form state.
final examinationNotifierProvider = StateNotifierProvider.autoDispose
    .family<
      ExaminationNotifier,
      ExaminationState,
      ({String? userId, String? examId})
    >((ref, params) {
      final repo = ref.watch(healthRepositoryProvider);
      return ExaminationNotifier(
        repo,
        params.userId,
        params.examId,
        onSaved: ref.read(healthQueueProvider).queuePending,
        currentUser: () => ref.read(sessionProvider.future),
      );
    });

// ─────────────────────────────────────────────────────────────────────
// Patient management — port of Kotlin `HealthViewModel` (962e1e736).
// Riverpod replaces the ViewModel's StateFlow fields.
// ─────────────────────────────────────────────────────────────────────

/// Sort options for the patient list, matching Kotlin's spinner indices.
enum PatientSort { joinDateDesc, joinDateAsc, nameAsc, nameDesc }

extension PatientSortX on PatientSort {
  String get fieldName => switch (this) {
    PatientSort.joinDateDesc || PatientSort.joinDateAsc => 'joinDate',
    PatientSort.nameAsc || PatientSort.nameDesc => 'name',
  };

  bool get descending => switch (this) {
    PatientSort.joinDateDesc || PatientSort.nameDesc => true,
    PatientSort.joinDateAsc || PatientSort.nameAsc => false,
  };
}

/// The logged-in user — health providers check their `rolesList` for
/// `"health"` to decide whether the patient picker is offered.
final loggedInUserProvider = FutureProvider<UserRow?>((ref) async {
  final session = await ref.watch(sessionProvider.future);
  if (session == null) return null;
  return ref.watch(userDaoProvider).getById(session.id);
});

/// Whether the current user has the `"health"` role.
final isHealthProviderProvider = FutureProvider<bool>((ref) async {
  final user = await ref.watch(loggedInUserProvider.future);
  if (user == null) return false;
  return user.rolesList.contains('health');
});

/// The patient list with an optional search query and sort order.
final patientListProvider =
    StateNotifierProvider.autoDispose<
      PatientListNotifier,
      AsyncValue<List<UserRow>>
    >(PatientListNotifier.new);

class PatientListNotifier extends StateNotifier<AsyncValue<List<UserRow>>> {
  PatientListNotifier(this._ref) : super(const AsyncValue.loading()) {
    _load();
  }

  final Ref _ref;

  /// Stands in for `HealthViewModel`'s `searchJob?.cancel()`: a superseded
  /// request cannot publish its result. Riverpod has no job to cancel, and
  /// without the check a slow early query landed *after* a fast later one —
  /// so typing "ali" quickly left the list showing the matches for "a".
  int _request = 0;

  Future<void> _fetch(Future<List<UserRow>> Function() query) async {
    final request = ++_request;
    state = const AsyncValue.loading();
    try {
      final patients = await query();
      if (!mounted || request != _request) return;
      state = AsyncValue.data(patients);
    } catch (e, st) {
      if (!mounted || request != _request) return;
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> _load() => _fetch(
    () => _ref
        .read(healthRepositoryProvider)
        .getPatientsSortedBy('joinDate', descending: true),
  );

  Future<void> sort(PatientSort sort) => _fetch(
    () => _ref
        .read(healthRepositoryProvider)
        .getPatientsSortedBy(sort.fieldName, descending: sort.descending),
  );

  Future<void> search(
    String query, {
    PatientSort sort = PatientSort.joinDateDesc,
  }) => _fetch(
    () => _ref
        .read(healthRepositoryProvider)
        .searchPatients(
          query,
          sortField: sort.fieldName,
          descending: sort.descending,
        ),
  );

  Future<void> refresh() => _load();
}

/// The id a health record is keyed by: the CouchDB `_users` document id when
/// the row has one, else the local row id.
///
/// Port of `HealthViewModel`'s
/// `if (currentUser?._id.isNullOrEmpty()) currentUser?.id else currentUser?._id`
/// — `_id` wins when present, because the health documents are keyed by it.
String patientIdOf(UserRow user) {
  final couchId = user.couchId ?? '';
  return (couchId.isNotEmpty ? couchId : user.id).trim();
}

/// The currently selected patient's full health record (profile +
/// examinations + creator user map). Null until a patient is chosen.
final patientDetailProvider =
    StateNotifierProvider.autoDispose<
      PatientDetailNotifier,
      PatientDetailState
    >(PatientDetailNotifier.new);

class PatientDetailNotifier extends StateNotifier<PatientDetailState> {
  PatientDetailNotifier(this._ref) : super(PatientDetailState.initial()) {
    _loadInitial();
  }

  final Ref _ref;

  Future<void> _loadInitial() async {
    final currentUser = await _ref.read(loggedInUserProvider.future);
    if (currentUser == null) return;
    final uid = patientIdOf(currentUser);
    if (uid.isNotEmpty) {
      await selectPatient(uid);
    }
  }

  /// Re-reads the currently selected patient's record, keeping the selection.
  ///
  /// Deliberately not `ref.invalidate(patientDetailProvider)`: recreating the
  /// notifier reruns [_loadInitial], which resolves the *logged-in* user — so a
  /// health provider who had selected another patient would be silently bounced
  /// back to their own record.
  Future<void> refresh() async {
    final current = state.user;
    if (current == null) return;
    final uid = patientIdOf(current);
    if (uid.isNotEmpty) {
      await selectPatient(uid);
    }
  }

  /// Mirrors `selectPatientJob?.cancel()`: a selection that has been
  /// superseded cannot publish, so two quick taps in the picker cannot leave
  /// the screen on the first patient's record.
  int _request = 0;

  Future<void> selectPatient(String userId) async {
    final request = ++_request;
    state = state.copyWith(isLoading: true);
    try {
      final repo = _ref.read(healthRepositoryProvider);
      final user = await repo.getPatientById(userId);
      if (!mounted || request != _request) return;
      if (user == null) {
        state = PatientDetailState.initial();
        return;
      }
      final record = await repo.getPatientHealthRecords(userId, user);
      if (!mounted || request != _request) return;
      state = PatientDetailState(user: user, record: record, isLoading: false);
    } catch (e) {
      if (!mounted || request != _request) return;
      state = state.copyWith(isLoading: false);
    }
  }
}

/// Port of Kotlin `PatientDetailState`.
class PatientDetailState {
  final UserRow? user;
  final HealthRecord? record;
  final bool isLoading;

  PatientDetailState({this.user, this.record, this.isLoading = false});

  factory PatientDetailState.initial() => PatientDetailState(isLoading: false);

  PatientDetailState copyWith({
    UserRow? user,
    HealthRecord? record,
    bool? isLoading,
  }) {
    return PatientDetailState(
      user: user ?? this.user,
      record: record ?? this.record,
      isLoading: isLoading ?? this.isLoading,
    );
  }
}
