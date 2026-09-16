import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../ui/components/list_view_mode.dart';
import 'app_providers.dart';

/// Port of the view-mode toggle `SharedPrefManager` stores under
/// `courseViewMode` / `libraryViewMode` and `CoursesFragment` /
/// `ResourcesFragment` read through `setupViewModeToggle`.
///
/// Each notifier reads its initial value from prefs and writes back on every
/// toggle, so the choice survives across sessions — matching the Kotlin, which
/// persists the mode and re-applies it in `onViewCreated`.
class CourseViewModeNotifier extends Notifier<ListViewMode> {
  @override
  ListViewMode build() =>
      ListViewMode.fromPref(ref.watch(planetPrefsProvider).courseViewMode);

  Future<void> set(ListViewMode mode) async {
    await ref.read(planetPrefsProvider).setCourseViewMode(mode.name);
    state = mode;
  }
}

final courseViewModeProvider =
    NotifierProvider<CourseViewModeNotifier, ListViewMode>(
      CourseViewModeNotifier.new,
    );

class LibraryViewModeNotifier extends Notifier<ListViewMode> {
  @override
  ListViewMode build() =>
      ListViewMode.fromPref(ref.watch(planetPrefsProvider).libraryViewMode);

  Future<void> set(ListViewMode mode) async {
    await ref.read(planetPrefsProvider).setLibraryViewMode(mode.name);
    state = mode;
  }
}

final libraryViewModeProvider =
    NotifierProvider<LibraryViewModeNotifier, ListViewMode>(
      LibraryViewModeNotifier.new,
    );
