import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
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

  HealthData({
    this.user,
    this.examination,
    this.myHealth,
    this.examinations = const [],
  });
}

/// Provider for the current user's health data.
final healthDataProvider = FutureProvider<HealthData?>((ref) async {
  final session = await ref.watch(sessionProvider.future);
  if (session == null) return null;

  final repo = ref.watch(healthRepositoryProvider);
  final userId = session.id;

  final userDao = ref.watch(userDaoProvider);
  final user = await userDao.getById(userId);
  final examination = await repo.getByIdOrUserId(userId);
  final examinations = await repo.getForUser(userId);

  // `data` is AES ciphertext, exactly as `HealthExaminationActivity` writes
  // it. Parsing it as JSON — which is what this did — always threw, and the
  // catch turned every health record into a blank screen.
  final myHealth = _decodeHealth(
    await repo.decryptData(userId, examination?.data),
  );

  return HealthData(
    user: user,
    examination: examination,
    myHealth: myHealth,
    examinations: examinations,
  );
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

  ExaminationState copyWith({
    bool? isLoading,
    bool? isSaving,
    bool? saved,
    String? error,
    HealthExaminationRow? examination,
    Map<String, bool>? conditions,
    Examination? examData,
  }) {
    return ExaminationState(
      isLoading: isLoading ?? this.isLoading,
      isSaving: isSaving ?? this.isSaving,
      saved: saved ?? this.saved,
      error: error ?? this.error,
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
    return _ref
        .read(healthUploaderProvider)
        .queuePending(
          config: config,
          userId: _ref.read(sessionProvider).valueOrNull?.id,
        );
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

/// Notifier for managing examination form state.
class ExaminationNotifier extends StateNotifier<ExaminationState> {
  final HealthRepository _repo;
  final String? _userId;
  final String? _examinationId;
  final Future<void> Function()? _onSaved;

  ExaminationNotifier(
    this._repo,
    this._userId,
    this._examinationId, {
    Future<void> Function()? onSaved,
  }) : _onSaved = onSaved,
       super(ExaminationState()) {
    _loadData();
  }

  Future<void> _loadData() async {
    state = state.copyWith(isLoading: true);
    try {
      if (_examinationId != null) {
        final exam = await _repo.getById(_examinationId);
        final conditions = _repo.parseConditions(exam?.conditions);
        final examData = await _decryptExamination(exam);
        state = state.copyWith(
          isLoading: false,
          examination: exam,
          conditions: conditions,
          examData: examData,
        );
      } else if (_userId != null) {
        final exam = await _repo.getByIdOrUserId(_userId);
        final conditions = _repo.parseConditions(exam?.conditions);
        final examData = await _decryptExamination(exam);
        state = state.copyWith(
          isLoading: false,
          examination: exam,
          conditions: conditions,
          examData: examData,
        );
      } else {
        state = state.copyWith(isLoading: false);
      }
    } catch (e) {
      state = state.copyWith(isLoading: false, error: e.toString());
    }
  }

  Future<Examination?> _decryptExamination(HealthExaminationRow? exam) async {
    if (exam == null) return null;
    final owner = exam.userId ?? _userId;
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
    state = state.copyWith(isSaving: true);
    try {
      final conditionsJson = jsonEncode(conditions);
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
      );
      // Encrypted before it reaches the database, so the plaintext never
      // touches SQLite and the upload carries ciphertext to CouchDB — the
      // property Kotlin has and the port was about to lose.
      final owner = state.examination?.userId ?? _userId;
      final data = owner == null
          ? null
          : await _repo.encryptData(owner, jsonEncode(examData.toJson()));

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
        );
      } else if (_userId != null) {
        await _repo.createExamination(
          userId: _userId,
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
        );
      }

      // Queued from inside `save` rather than from each screen: an
      // examination that reaches the database and not the outbox is the
      // failure this whole path exists to prevent.
      await _onSaved?.call();

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
    StateNotifierProvider.autoDispose<PatientListNotifier, AsyncValue<List<UserRow>>>(
  PatientListNotifier.new,
);

class PatientListNotifier
    extends StateNotifier<AsyncValue<List<UserRow>>> {
  PatientListNotifier(this._ref) : super(const AsyncValue.loading()) {
    _load();
  }

  final Ref _ref;

  Future<void> _load() async {
    state = const AsyncValue.loading();
    try {
      final repo = _ref.read(healthRepositoryProvider);
      final patients = await repo.getPatientsSortedBy(
        'joinDate',
        descending: true,
      );
      state = AsyncValue.data(patients);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> sort(PatientSort sort) async {
    state = const AsyncValue.loading();
    try {
      final repo = _ref.read(healthRepositoryProvider);
      final patients = await repo.getPatientsSortedBy(
        sort.fieldName,
        descending: sort.descending,
      );
      state = AsyncValue.data(patients);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> search(String query, {PatientSort sort = PatientSort.joinDateDesc}) async {
    state = const AsyncValue.loading();
    try {
      final repo = _ref.read(healthRepositoryProvider);
      final patients = await repo.searchPatients(
        query,
        sortField: sort.fieldName,
        descending: sort.descending,
      );
      state = AsyncValue.data(patients);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> refresh() => _load();
}

/// The currently selected patient's full health record (profile +
/// examinations + creator user map). Null until a patient is chosen.
final patientDetailProvider =
    StateNotifierProvider.autoDispose<PatientDetailNotifier, PatientDetailState>(
  PatientDetailNotifier.new,
);

class PatientDetailNotifier extends StateNotifier<PatientDetailState> {
  PatientDetailNotifier(this._ref) : super(PatientDetailState.initial()) {
    _loadInitial();
  }

  final Ref _ref;

  Future<void> _loadInitial() async {
    final currentUser = await _ref.read(loggedInUserProvider.future);
    if (currentUser == null) return;
    final uid = (currentUser.couchId ?? '').isNotEmpty
        ? currentUser.couchId!
        : currentUser.id;
    final trimmed = uid.trim();
    if (trimmed.isNotEmpty) {
      await selectPatient(trimmed);
    }
  }

  Future<void> selectPatient(String userId) async {
    state = state.copyWith(isLoading: true);
    try {
      final repo = _ref.read(healthRepositoryProvider);
      final user = await repo.getPatientById(userId);
      if (user == null) {
        state = PatientDetailState.initial();
        return;
      }
      final record = await repo.getPatientHealthRecords(userId, user);
      state = PatientDetailState(user: user, record: record, isLoading: false);
    } catch (e) {
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

  factory PatientDetailState.initial() =>
      PatientDetailState(isLoading: false);

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
