import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

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

  MyHealth? myHealth;
  if (examination?.data != null && examination!.data!.isNotEmpty) {
    try {
      // The examination data contains encrypted JSON with the MyHealth object
      // For now, we'll try to parse it directly
      final decoded = jsonDecode(examination.data!);
      if (decoded is Map<String, dynamic>) {
        myHealth = MyHealth.fromJson(decoded);
      }
    } catch (e) {
      // Data might be encrypted or in a different format
      myHealth = null;
    }
  }

  return HealthData(
    user: user,
    examination: examination,
    myHealth: myHealth,
    examinations: examinations,
  );
});

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

/// Notifier for managing examination form state.
class ExaminationNotifier extends StateNotifier<ExaminationState> {
  final HealthRepository _repo;
  final String? _userId;
  final String? _examinationId;

  ExaminationNotifier(this._repo, this._userId, this._examinationId)
    : super(ExaminationState()) {
    _loadData();
  }

  Future<void> _loadData() async {
    state = state.copyWith(isLoading: true);
    try {
      if (_examinationId != null) {
        final exam = await _repo.getById(_examinationId);
        final conditions = _repo.parseConditions(exam?.conditions);
        Examination? examData;
        if (exam?.data != null) {
          try {
            final decoded = jsonDecode(exam!.data!);
            if (decoded is Map<String, dynamic>) {
              examData = Examination.fromJson(decoded);
            }
          } catch (e) {
            // Encrypted or invalid data
          }
        }
        state = state.copyWith(
          isLoading: false,
          examination: exam,
          conditions: conditions,
          examData: examData,
        );
      } else if (_userId != null) {
        final exam = await _repo.getByIdOrUserId(_userId);
        final conditions = _repo.parseConditions(exam?.conditions);
        Examination? examData;
        if (exam?.data != null) {
          try {
            final decoded = jsonDecode(exam!.data!);
            if (decoded is Map<String, dynamic>) {
              examData = Examination.fromJson(decoded);
            }
          } catch (e) {
            // Encrypted or invalid data
          }
        }
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
      final data = jsonEncode(examData.toJson());

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
      return ExaminationNotifier(repo, params.userId, params.examId);
    });
