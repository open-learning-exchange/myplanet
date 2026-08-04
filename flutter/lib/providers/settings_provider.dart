import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_providers.dart';

/// Port of `services/ThemeManager.kt` and the `dark_mode` preference in
/// `ui/settings/SettingsActivity.kt`.
class ThemeModeNotifier extends Notifier<ThemeMode> {
  @override
  ThemeMode build() {
    final stored = ref.watch(planetPrefsProvider).themeModeName;
    return _fromName(stored);
  }

  Future<void> select(ThemeMode mode) async {
    await ref.read(planetPrefsProvider).setThemeModeName(mode.name);
    state = mode;
  }
}

ThemeMode _fromName(String name) => switch (name) {
      'light' => ThemeMode.light,
      'dark' => ThemeMode.dark,
      _ => ThemeMode.system,
    };

final themeModeProvider = NotifierProvider<ThemeModeNotifier, ThemeMode>(
  ThemeModeNotifier.new,
);
