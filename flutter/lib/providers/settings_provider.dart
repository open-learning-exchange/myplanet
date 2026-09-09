import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/background/background_work_coordinator.dart';
import 'app_providers.dart';
import 'session_provider.dart';

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

/// Port of `LocaleUtils.getTextScale` / `setTextScale` and the
/// `textSizeChanger` dialog in `SettingsActivity.SettingFragment`. The three
/// scales match the Kotlin's `floatArrayOf(0.85f, 1.0f, 1.15f)`.
class TextScaleNotifier extends Notifier<double> {
  static const List<double> supportedScales = [0.85, 1.0, 1.15];

  @override
  double build() => ref.watch(planetPrefsProvider).textScale;

  Future<void> select(double scale) async {
    await ref.read(planetPrefsProvider).setTextScale(scale);
    state = scale;
  }
}

final textScaleProvider = NotifierProvider<TextScaleNotifier, double>(
  TextScaleNotifier.new,
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

/// Port of `SettingsViewModel.clearAllData`, wired to the settings screen's
/// "Reset app" preference (`R.string.reset_app`). The Kotlin calls
/// `appDatabase.clearAllTables()` + `sharedPrefManager.clearPreferences()`
/// then restarts the app; here the DB wipe, prefs wipe, and provider-state
/// reset happen together, and the router's `redirect` sends the user to the
/// server-config screen — the cleared prefs leave no server or session.
class ClearDataNotifier extends AsyncNotifier<void> {
  @override
  Future<void> build() async {}

  Future<void> clearAllData() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(_wipe);
  }

  /// The same wipe, for the server-switch path, which needs the failure rather
  /// than a swallowed [AsyncError].
  ///
  /// Port of the positive-button body of `SyncActivity.clearDataDialog`
  /// (`SyncActivity.kt:270-300`). Kotlin runs, in this order,
  /// `configurationsRepository.clearAllData()` (which is
  /// `appDatabase.clearAllTables()`), then `prefData.setManualConfig(config)`,
  /// then `prefData.clearPreferences()`, and on an exception it dismisses its
  /// progress dialog, releases the back-press guard and **re-enables both
  /// dialog buttons** so the user can retry. Reproducing that last part is
  /// why this rethrows where [clearAllData] guards: the settings screen reads
  /// the failure off [clearDataProvider], the dialog has to act on it.
  ///
  /// Two pieces of the Kotlin sequence are deliberately absent.
  ///
  /// `setManualConfig(config)` writes `MANUAL_CONFIG`, one of the two keys
  /// `SharedPrefManager.clearPreferences` deliberately keeps — which is why
  /// Kotlin sets it *before* clearing rather than after. Its only readers are
  /// the manual-configuration checkbox and `setupManualUi`, and the port has
  /// neither: its URL and PIN fields are always editable, so there is no mode
  /// to remember. Following it to its end (the Phase 149 rule) makes the
  /// honest port of that line *nothing*, not `false` — see the PR's
  /// "Reported, not fixed" for the two other call sites that pass `true`.
  ///
  /// `delay(500.milliseconds)` before `restartApp()` is a flush window rather
  /// than a courtesy: `SharedPreferences.edit {}` defaults to `apply()`, and
  /// `Runtime.getRuntime().exit(0)` skips the `QueuedWork` flush a normal
  /// pause would perform, so the delay is Kotlin's (unreliable) hope that
  /// steps 5 and 6 reached disk. Every write here is awaited, so there is
  /// nothing to wait out — copying the sleep would reproduce the symptom of a
  /// problem the port does not have.
  Future<void> clearForServerSwitch() async {
    state = const AsyncLoading();
    try {
      await _wipe();
      state = const AsyncData(null);
    } catch (error, stack) {
      state = AsyncError<void>(error, stack);
      rethrow;
    }
  }

  Future<void> _wipe() async {
    final db = ref.read(appDatabaseProvider);
    final prefs = ref.read(planetPrefsProvider);
    await db.clearAllData();
    // `PlanetPrefs.clearAllData` keeps `onboardingComplete` and deletes secure
    // storage, exactly as the reset-app path wants — and exactly as this path
    // wants too, for a different reason. The stored password and PIN belong to
    // the server being left behind, and the derived key and salt that back
    // offline PBKDF2 verification were issued by it, so carrying them to a new
    // Planet would leave credentials that can never authenticate. Kotlin's
    // `clearPreferences` does not touch `SecurePrefs`, which is a gap on both
    // of its call paths rather than a decision to copy.
    await prefs.clearAllData();
    // Reset the provider states that the router's `redirect` reads, so the
    // navigation lands without waiting for the next read of cleared prefs.
    ref.read(serverConfigProvider.notifier).clear();
    await ref.read(sessionProvider.notifier).signOut();
  }
}

/// Whether this device still holds data that belongs to a server other than
/// the one about to be configured — the port's answer to
/// `ServerAddressAdapter`'s `isServerAlreadyConfigured`.
///
/// Kotlin can use the configured URL itself (`!urlWithoutProtocol.isNullOrEmpty()`,
/// `ServerDialogExtensions.kt:192`) because its server dialog opens *over* a
/// configured device. The port cannot: the only way to reach
/// `ServerConfigScreen` on a configured device is the login screen's "change
/// server" action, and that clears the persisted config to make the router's
/// redirect fire — so by the time the screen builds, the signal Kotlin reads
/// has already been destroyed while the database is still full of the old
/// server's documents. Gating on the config alone would have produced a
/// warning that can never appear, guarding the one path that can actually mix
/// two Planets.
///
/// `lastSync` survives `clearServerConfig()` and is 0 on a fresh install and
/// after a reset, so "this device has synced with some server" is both the
/// question that matters and one the port can still answer. A configured
/// server counts too, for whenever the screen becomes reachable with one.
///
/// Reading this touches [planetPrefsProvider], which throws unless overridden:
/// a widget test of the server-config screen must override this provider.
final deviceHoldsServerDataProvider = Provider<bool>((ref) {
  if (ref.watch(serverConfigProvider) != null) return true;
  return ref.watch(planetPrefsProvider).lastSync != 0;
});

final clearDataProvider = AsyncNotifierProvider<ClearDataNotifier, void>(
  ClearDataNotifier.new,
);
