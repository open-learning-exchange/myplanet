import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';

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

/// Port of `LocaleUtils` + the `change_language` overflow action in
/// `DashboardActivity` / `SettingsActivity.SettingFragment.languageChanger`.
///
/// Exposes the user-selected [Locale], or `null` to follow the device locale
/// (the Kotlin's pre-selection default). `select` persists the code so a cold
/// start keeps the choice — the Kotlin recreates the Activity; Flutter
/// rebuilds the `MaterialApp` because [MyPlanetApp] watches this provider.
///
/// Only locales with a shipped `.arb` file are offered (en, es). The Kotlin
/// lists six, but the other four (so, ne, ar, fr) have no Flutter translations
/// yet — offering them would silently fall back to English, which is worse
/// than not offering them. See `docs/kotlin-to-flutter-migration.md`.
class LocaleNotifier extends Notifier<Locale?> {
  @override
  Locale? build() {
    final code = ref.watch(planetPrefsProvider).languageCode;
    return code.isEmpty ? null : Locale(code);
  }

  Future<void> select(String languageCode) async {
    await ref.read(planetPrefsProvider).setLanguageCode(languageCode);
    state = languageCode.isEmpty ? null : Locale(languageCode);
  }
}

final localeProvider = NotifierProvider<LocaleNotifier, Locale?>(
  LocaleNotifier.new,
);

/// The languages the language-changer offers. Each must have a shipped
/// `.arb` file (see `l10n.yaml`); a code with no `.arb` would fall back to
/// English silently. The display names match the Kotlin's `language` array so
/// a user switching back and forth sees consistent labels.
const Map<String, String> offeredLocales = {
  'en': 'English',
  'es': 'Español',
};

/// Port of `BuildConfig.VERSION_NAME` (exposed as `R.string.app_version` via
/// a `resValue` in `app/build.gradle`). The Kotlin's `AboutFragment` formats
/// it as `"Version %s"`; `PackageInfo.fromPlatform()` reads the same
/// `versionName` from the compiled manifest at runtime.
final appVersionProvider = FutureProvider<String>((ref) async {
  final info = await PackageInfo.fromPlatform();
  return info.version;
});
