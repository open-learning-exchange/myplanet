import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/view_mode_providers.dart';
import 'package:myplanet/ui/components/list_view_mode.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<({ProviderContainer container, PlanetPrefs prefs})> harness({
    Map<String, Object> initial = const {},
  }) async {
    SharedPreferences.setMockInitialValues(initial);
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    final container = ProviderContainer(
      overrides: [planetPrefsProvider.overrideWithValue(prefs)],
    );
    addTearDown(container.dispose);
    return (container: container, prefs: prefs);
  }

  group('CourseViewModeNotifier', () {
    test('defaults to grid when no pref is stored', () async {
      final h = await harness();
      expect(h.container.read(courseViewModeProvider), ListViewMode.grid);
    });

    test('reads persisted list mode from prefs', () async {
      final h = await harness(initial: {'courseViewMode': 'list'});
      expect(h.container.read(courseViewModeProvider), ListViewMode.list);
    });

    test('set writes back to prefs and updates state', () async {
      final h = await harness();
      await h.container
          .read(courseViewModeProvider.notifier)
          .set(ListViewMode.list);
      expect(h.container.read(courseViewModeProvider), ListViewMode.list);
      expect(h.prefs.courseViewMode, 'list');
    });
  });

  group('LibraryViewModeNotifier', () {
    test('defaults to grid when no pref is stored', () async {
      final h = await harness();
      expect(h.container.read(libraryViewModeProvider), ListViewMode.grid);
    });

    test('reads persisted list mode from prefs', () async {
      final h = await harness(initial: {'libraryViewMode': 'list'});
      expect(h.container.read(libraryViewModeProvider), ListViewMode.list);
    });

    test('set writes back to prefs and updates state', () async {
      final h = await harness();
      await h.container
          .read(libraryViewModeProvider.notifier)
          .set(ListViewMode.list);
      expect(h.container.read(libraryViewModeProvider), ListViewMode.list);
      expect(h.prefs.libraryViewMode, 'list');
    });
  });
}
