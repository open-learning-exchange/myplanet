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

/// Port of `SettingsActivity.SettingFragment.languageChanger` and
/// `LocaleUtils` — the app's language override, reachable from the dashboard
/// overflow menu exactly as `R.id.change_language` is.
///
/// A null state means "follow the device", which is what the app did before
/// this existed and what `LocaleUtils.getLanguage` reports when the preference
/// is unset. The Kotlin recreates the activity to apply the change; here the
/// `MaterialApp`'s `locale` is rebuilt from this provider, so the change lands
/// on the next frame.
class LocaleNotifier extends Notifier<Locale?> {
  /// The six languages `languageChanger` offers, in its order.
  static const List<String> supportedLanguageCodes = [
    'en',
    'es',
    'so',
    'ne',
    'ar',
    'fr',
  ];

  @override
  Locale? build() {
    final stored = ref.watch(planetPrefsProvider).languageCode;
    if (stored == null || !supportedLanguageCodes.contains(stored)) return null;
    return Locale(stored);
  }

  Future<void> select(String? languageCode) async {
    await ref.read(planetPrefsProvider).setLanguageCode(languageCode);
    state = languageCode == null ? null : Locale(languageCode);
  }
}

final localeProvider = NotifierProvider<LocaleNotifier, Locale?>(
  LocaleNotifier.new,
);
