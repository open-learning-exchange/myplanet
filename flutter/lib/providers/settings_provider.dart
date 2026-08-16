import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/background/background_scheduler.dart';
import '../core/background/background_work_coordinator.dart';
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

final backgroundSchedulerProvider = Provider<BackgroundScheduler>(
  (ref) => const WorkmanagerScheduler(),
);

class BackgroundSettings {
  const BackgroundSettings({required this.enabled, required this.interval});

  final bool enabled;
  final Duration interval;

  BackgroundSettings copyWith({bool? enabled, Duration? interval}) =>
      BackgroundSettings(
        enabled: enabled ?? this.enabled,
        interval: interval ?? this.interval,
      );
}

class BackgroundSettingsNotifier extends Notifier<BackgroundSettings> {
  @override
  BackgroundSettings build() {
    final prefs = ref.watch(planetPrefsProvider);
    return BackgroundSettings(
      enabled: prefs.autoSyncEnabled,
      interval: prefs.autoSyncInterval,
    );
  }

  Future<void> setEnabled(bool enabled) async {
    final prefs = ref.read(planetPrefsProvider);
    await prefs.setAutoSyncEnabled(enabled);
    state = state.copyWith(enabled: enabled);
    await _apply();
  }

  Future<void> setInterval(Duration interval) async {
    final prefs = ref.read(planetPrefsProvider);
    await prefs.setAutoSyncInterval(interval);
    state = state.copyWith(interval: interval);
    if (state.enabled) await _apply();
  }

  Future<void> _apply() => BackgroundWorkCoordinator(
    ref.read(backgroundSchedulerProvider),
    ref.read(planetPrefsProvider),
  ).applyAutoSyncSettings();
}

final backgroundSettingsProvider =
    NotifierProvider<BackgroundSettingsNotifier, BackgroundSettings>(
      BackgroundSettingsNotifier.new,
    );
