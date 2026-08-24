import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_providers.dart';
import 'session_provider.dart';

/// Queues any pending search-activity rows into the outbox. Mirrors the
/// `queuePending` call the activities provider makes after each write — so a
/// row written in `dispose()` reaches the outbox before the next sync rather
/// than sitting in `search_activity` until then.
Future<void> _queuePending(ProviderContainer container) async {
  final config = container.read(serverConfigProvider);
  if (config == null) return;
  final user = container.read(sessionProvider).valueOrNull;
  await container
      .read(searchActivityUploaderProvider)
      .queuePending(config: config, userId: user?.id);
}

/// Saves a courses search-activity row, mirroring
/// `CoursesRepositoryImpl.saveSearchActivity` called from
/// `CoursesFragment.onPause`. No-op when no filter is applied or the session
/// is missing — the Kotlin's `filterApplied` guard and `model?` null-checks.
///
/// Takes a [ProviderContainer] rather than a `WidgetRef` so it can be called
/// from `dispose()`, where the widget's `ref` is already torn down.
Future<void> saveCourseSearchActivity(
  ProviderContainer container, {
  required String searchText,
  List<String> tags = const [],
  String? grade,
  String? subject,
}) async {
  final user = container.read(sessionProvider).valueOrNull;
  if (user == null) return;
  final name = user.name;
  final planetCode = user.planetCode;
  final parentCode = user.parentCode;
  if (name == null || planetCode == null || parentCode == null) return;
  await container
      .read(searchActivityRepositoryProvider)
      .saveCourseSearch(
        searchText: searchText,
        userName: name,
        planetCode: planetCode,
        parentCode: parentCode,
        tags: tags,
        grade: grade,
        subject: subject,
      );
  await _queuePending(container);
}

/// Saves a resources search-activity row, mirroring
/// `ResourcesRepositoryImpl.saveSearchActivity` called from
/// `ResourcesFragment.onPause`. No-op when no filter is applied or the session
/// is missing.
///
/// Takes a [ProviderContainer] rather than a `WidgetRef` so it can be called
/// from `dispose()`, where the widget's `ref` is already torn down.
Future<void> saveResourceSearchActivity(
  ProviderContainer container, {
  required String searchText,
  List<String> tags = const [],
  Set<String> subjects = const {},
  Set<String> languages = const {},
  Set<String> levels = const {},
  Set<String> mediums = const {},
}) async {
  final user = container.read(sessionProvider).valueOrNull;
  if (user == null) return;
  final name = user.name;
  final planetCode = user.planetCode;
  final parentCode = user.parentCode;
  if (name == null || planetCode == null || parentCode == null) return;
  await container
      .read(searchActivityRepositoryProvider)
      .saveResourceSearch(
        userName: name,
        searchText: searchText,
        planetCode: planetCode,
        parentCode: parentCode,
        tags: tags,
        subjects: subjects,
        languages: languages,
        levels: levels,
        mediums: mediums,
      );
  await _queuePending(container);
}
