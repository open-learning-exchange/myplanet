import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/server_config.dart';

/// Port of `services/SharedPrefManager.kt` plus the credential half of
/// `utils/SecurePrefs.kt`.
///
/// Non-secret server identity lives in [SharedPreferences]. Everything that
/// grants CouchDB access — the `satellite` PIN, the credentialed CouchDB URL
/// derived from it, and the user's password — lives in [FlutterSecureStorage],
/// which wraps the Keystore-backed `EncryptedSharedPreferences` the Kotlin uses
/// via Tink.
///
/// **Deviation from the Kotlin**, which keeps `serverPin` and `couchdbURL` in
/// plain `SharedPreferences`: that file is world-readable by root and is
/// included in Android auto-backup by default, so the PIN leaves the device.
///
/// The secrets are read once by [load] and cached in memory, which keeps
/// [serverConfig] synchronous — the router and `ServerConfigNotifier` read it
/// during a synchronous `build()`.
class PlanetPrefs {
  PlanetPrefs(this._prefs, {FlutterSecureStorage? secureStorage})
    : _secureStorage = secureStorage ?? _defaultSecureStorage;

  /// `encryptedSharedPreferences` is opt-in; without it the plugin uses its own
  /// Keystore-backed AES store rather than the `EncryptedSharedPreferences` the
  /// Kotlin app uses via Tink. Requested explicitly so the two match.
  static const FlutterSecureStorage _defaultSecureStorage =
      FlutterSecureStorage(
        aOptions: AndroidOptions(encryptedSharedPreferences: true),
      );

  static const String _keyServerUrl = 'serverURL';
  static const String _keyAlternativeUrl = 'alternativeUrl';
  static const String _keyIsAlternativeUrl = 'isAlternativeUrl';
  static const String _keyConfigurationId = 'configurationId';
  static const String _keyCommunityCode = 'communityCode';
  static const String _keyParentCode = 'parentCode';
  static const String _keyLoggedInUserId = 'loggedInUserId';
  static const String _keyOnboardingComplete = 'onboardingComplete';
  static const String _keyThemeMode = 'themeMode';

  /// Matches the Kotlin's `language` preference key, read by `LocaleUtils`.
  static const String _keyLanguage = 'language';
  static const String _keyCommunityLeaders = 'communityLeaders';
  static const String _keyCommunityName = 'communityName';
  static const String _keyPlanetType = 'planetType';
  static const String _keyLastSurveyDialog = 'lastSurveyDialogShown';
  static const String _keyAutoSync = 'autoSync';
  static const String _keyAutoSyncInterval = 'autoSyncInterval';
  static const String _keyBackgroundRun = 'backgroundRun';

  /// Prefix of the per-table heavy-sync checkpoint, matching the Kotlin's
  /// `checkpointKey = "heavy_sync_skip_$table"`
  /// (`TransactionSyncManager.kt:168`). The stored value is a **document
  /// offset** — the `skip` the next `_all_docs` page should ask for — not a
  /// page number.
  static const String _keyHeavySyncSkipPrefix = 'heavy_sync_skip_';

  /// Epoch millis at which the interactive sync began; `0` when none is
  /// running. Stands in for `SyncManager.isSyncing`
  /// (`SyncManager.kt:118`) — see `HeavyTableSync.isInteractiveSyncActive`
  /// for why the port needs a persisted flag where the Kotlin has an
  /// `AtomicBoolean`, and why the value is a timestamp rather than a bool.
  static const String _keyInteractiveSyncStartedAt = 'interactiveSyncStartedAt';

  /// Prefix of the per-reminder keys, matching the Kotlin's
  /// `reminder_time_<surveyIds>` in its `survey_reminders` preferences file.
  /// The Kotlin scans `reminderPrefs.all` for this prefix; the port scans
  /// `getKeys()` on the one shared preferences store for the same thing.
  static const String _keyReminderTimePrefix = 'reminder_time_';
  static const String _keyLastSync = 'LastSync';

  /// `SharedPrefManager.CUSTOM_DEVICE_NAME` — a user-editable label for this
  /// device, written onto every activity telemetry document. Defaults to the
  /// empty string the way the Kotlin's getter does.
  static const String _keyCustomDeviceName = 'customDeviceName';

  /// `SharedPrefManager.LAST_USAGE_UPLOADED` — the cutoff (epoch millis) for
  /// the `UsageStatsManager` query `MyPlanet.getTabletUsages` runs. Advanced to
  /// "now" after a successful `myplanet_activities` upload so the next upload
  /// does not re-report the same usage interval.
  static const String _keyLastUsageUploaded = 'lastUsageUploaded';

  /// `SharedPrefManager.VERSION_DETAIL` — the server's planet-version JSON
  /// blob, cached so `MyPlanet.getNormalMyPlanetActivities` can echo
  /// `planetVersion` back. `null` until the first server handshake reports it.
  static const String _keyVersionDetail = 'versionDetail';
  static const String _keyDeviceUniqueIdentifier = 'deviceUniqueIdentifier';
  static const String _keyDeviceModelName = 'deviceModelName';
  static const String _keyStorageAvailablePercent = 'storageAvailablePercent';

  /// `libraryViewMode` / `courseViewMode` — port of
  /// `SharedPrefManager.getLibraryViewMode` / `getCourseViewMode`.
  static const String _keyLibraryViewMode = 'libraryViewMode';
  static const String _keyCourseViewMode = 'courseViewMode';

  /// `SharedPrefManager.HAS_SHOWN_CONGRATS`. Once-only flag so the challenge
  /// dialog's congratulations message does not reappear after the user has
  /// seen it once.
  static const String _keyHasShownChallengeCongrats =
      'has_shown_challenge_congrats';

  /// Port of `LocaleUtils.TEXT_SCALE` — the user's font-scale preference.
  /// The Kotlin stores 0.85 / 1.0 / 1.15 and applies it via a
  /// `Configuration.fontScale` override; here it is read by
  /// `textScaleProvider` and applied through a `MediaQuery` override on the
  /// `MaterialApp.router` builder.
  static const String _keyTextScale = 'textScale';

  /// `SharedPrefManager.getMediaPlaybackPosition` keys its per-resource entry
  /// `media_progress_<resourceKey>`; the speed is one global entry. Both names
  /// are the Kotlin ones, for the same reason `_keyLanguage` and `_keyLastSync`
  /// are — one name for one concept across the two trees, so a reader
  /// comparing them does not have to translate.
  ///
  /// It does **not** mean the two apps share the values, and they cannot:
  /// `shared_preferences` prefixes every key it writes with `flutter.` and
  /// keeps them in its own `FlutterSharedPreferences` store rather than the
  /// Kotlin app's `Constants.PREFS_NAME` file, and even in one file Kotlin
  /// writes the speed with `putFloat` where this writes a double.
  static const String _keyMediaProgressPrefix = 'media_progress_';
  static const String _keyMediaPlaybackSpeed = 'media_playback_speed';

  /// `OnboardingActivity.DEEP_LINK_SECTION_KEY` / `DEEP_LINK_ID_KEY`. A section
  /// link that arrives before sign-in is stored under these and applied by the
  /// dashboard afterwards, so the link survives the login it triggered.
  static const String _keyDeepLinkSection = 'pending_deep_link_section';
  static const String _keyDeepLinkId = 'pending_deep_link_id';

  static const String _secureKeyServerPin = 'serverPin';
  static const String _secureKeyCouchDbUrl = 'couchdbURL';
  static const String _secureKeyPassword = 'userPassword';

  final SharedPreferences _prefs;
  final FlutterSecureStorage _secureStorage;

  String? _cachedPin;
  String? _cachedCouchDbUrl;

  static Future<PlanetPrefs> load({FlutterSecureStorage? secureStorage}) async {
    final prefs = PlanetPrefs(
      await SharedPreferences.getInstance(),
      secureStorage: secureStorage,
    );
    await prefs.hydrateSecrets();
    return prefs;
  }

  /// Reads the secure-storage values into memory so [serverConfig] can stay
  /// synchronous. Called by [load]; call it directly when constructing manually.
  Future<void> hydrateSecrets() async {
    _cachedPin = await _secureStorage.read(key: _secureKeyServerPin);
    _cachedCouchDbUrl = await _secureStorage.read(key: _secureKeyCouchDbUrl);
  }

  /// `null` until the server-configuration handshake has succeeded once.
  ServerConfig? get serverConfig {
    final serverUrl = _prefs.getString(_keyServerUrl);
    final couchDbUrl = _cachedCouchDbUrl;
    if (serverUrl == null || couchDbUrl == null) return null;

    return ServerConfig(
      serverUrl: serverUrl,
      pin: _cachedPin ?? '',
      couchDbUrl: couchDbUrl,
      alternativeUrl: _prefs.getString(_keyAlternativeUrl),
      isAlternativeUrl: _prefs.getBool(_keyIsAlternativeUrl) ?? false,
      id: _prefs.getString(_keyConfigurationId) ?? '',
      code: _prefs.getString(_keyCommunityCode) ?? '',
      parentCode: _prefs.getString(_keyParentCode) ?? '',
    );
  }

  Future<void> saveServerConfig(ServerConfig config) async {
    await _prefs.setString(_keyServerUrl, config.serverUrl);
    await _prefs.setBool(_keyIsAlternativeUrl, config.isAlternativeUrl);
    await _prefs.setString(_keyConfigurationId, config.id);
    await _prefs.setString(_keyCommunityCode, config.code);
    await _prefs.setString(_keyParentCode, config.parentCode);

    final alternativeUrl = config.alternativeUrl;
    if (alternativeUrl == null) {
      await _prefs.remove(_keyAlternativeUrl);
    } else {
      await _prefs.setString(_keyAlternativeUrl, alternativeUrl);
    }

    await _secureStorage.write(key: _secureKeyServerPin, value: config.pin);
    await _secureStorage.write(
      key: _secureKeyCouchDbUrl,
      value: config.couchDbUrl,
    );
    _cachedPin = config.pin;
    _cachedCouchDbUrl = config.couchDbUrl;
  }

  /// Forgets the configured server entirely.
  ///
  /// Without this the "change server" action only clears in-memory state, and
  /// the next cold start reads the old server straight back out of storage.
  Future<void> clearServerConfig() async {
    await _prefs.remove(_keyServerUrl);
    await _prefs.remove(_keyAlternativeUrl);
    await _prefs.remove(_keyIsAlternativeUrl);
    await _prefs.remove(_keyConfigurationId);
    await _prefs.remove(_keyCommunityCode);
    await _prefs.remove(_keyParentCode);

    await _secureStorage.delete(key: _secureKeyServerPin);
    await _secureStorage.delete(key: _secureKeyCouchDbUrl);
    _cachedPin = null;
    _cachedCouchDbUrl = null;
  }

  String? get loggedInUserId => _prefs.getString(_keyLoggedInUserId);

  /// Whether the first-launch introduction has been completed or skipped.
  /// Port of `SharedPrefManager.getFirstLaunch` / `setFirstLaunch`.
  bool get onboardingComplete =>
      _prefs.getBool(_keyOnboardingComplete) ?? false;

  Future<void> setOnboardingComplete() =>
      _prefs.setBool(_keyOnboardingComplete, true);

  /// Persisted theme name. Kept as a string so `lib/core/` remains pure Dart
  /// and does not import Flutter's `ThemeMode`.
  String get themeModeName => _prefs.getString(_keyThemeMode) ?? 'system';

  Future<void> setThemeModeName(String value) =>
      _prefs.setString(_keyThemeMode, value);

  /// The chosen language override, or null to follow the device.
  ///
  /// Port of the `language` preference `LocaleUtils` reads. Stored as a bare
  /// code so `lib/core/` keeps its no-Flutter rule and never sees a `Locale`.
  String? get languageCode => _prefs.getString(_keyLanguage);

  Future<void> setLanguageCode(String? code) async {
    if (code == null) {
      await _prefs.remove(_keyLanguage);
      return;
    }
    await _prefs.setString(_keyLanguage, code);
  }

  Future<void> setLoggedInUserId(String? userId) async {
    if (userId == null) {
      await _prefs.remove(_keyLoggedInUserId);
    } else {
      await _prefs.setString(_keyLoggedInUserId, userId);
    }
  }

  /// The password is needed again to Basic-auth subsequent syncs, which is why
  /// the Kotlin keeps it too — but only ever in encrypted storage.
  Future<void> savePassword(String password) =>
      _secureStorage.write(key: _secureKeyPassword, value: password);

  Future<String?> readPassword() =>
      _secureStorage.read(key: _secureKeyPassword);

  Future<void> clearSession() async {
    await _prefs.remove(_keyLoggedInUserId);
    await _secureStorage.delete(key: _secureKeyPassword);
  }

  /// Port of `SharedPrefManager.clearPreferences`, called by the settings
  /// screen's "Reset app" action. Clears every preference and secret, keeping
  /// only [onboardingComplete] so the user does not see the first-launch
  /// slideshow again — the Kotlin keeps `FIRST_LAUNCH` and `MANUAL_CONFIG`
  /// for the same reason.
  Future<void> clearAllData() async {
    final keepOnboarding = _prefs.getBool(_keyOnboardingComplete) ?? false;
    await _prefs.clear();
    await _secureStorage.deleteAll();
    if (keepOnboarding) {
      await _prefs.setBool(_keyOnboardingComplete, true);
    }
  }

  /// JSON string of community leaders fetched from the server.
  /// Port of `SharedPrefManager.getCommunityLeaders` / `setCommunityLeaders`.
  String get communityLeaders => _prefs.getString(_keyCommunityLeaders) ?? '';

  Future<void> setCommunityLeaders(String json) =>
      _prefs.setString(_keyCommunityLeaders, json);

  /// Name of the community/nation.
  String get communityName => _prefs.getString(_keyCommunityName) ?? '';

  Future<void> setCommunityName(String name) =>
      _prefs.setString(_keyCommunityName, name);

  /// Type of planet (community, nation, center).
  String get planetType => _prefs.getString(_keyPlanetType) ?? '';

  Future<void> setPlanetType(String type) =>
      _prefs.setString(_keyPlanetType, type);

  /// When the pending-survey dialog was last shown, epoch millis.
  ///
  /// Port of `SurveysRepositoryImpl.getLastSurveyDialogShown` — the home
  /// dashboard throttles the dialog to once an hour.
  int get lastSurveyDialogShown => _prefs.getInt(_keyLastSurveyDialog) ?? 0;

  Future<void> setLastSurveyDialogShown(int epochMillis) =>
      _prefs.setInt(_keyLastSurveyDialog, epochMillis);

  /// Most recent successful server sync, in epoch milliseconds.
  ///
  /// The key deliberately matches `SharedPrefManager.LAST_SYNC`, making the
  /// meaning and persisted representation identical to the Kotlin app.
  int get lastSync => _prefs.getInt(_keyLastSync) ?? 0;

  Future<void> setLastSync(int epochMillis) =>
      _prefs.setInt(_keyLastSync, epochMillis);

  /// Port of `SharedPrefManager.getCustomDeviceName` /
  /// `setCustomDeviceName`. Empty by default.
  String get customDeviceName => _prefs.getString(_keyCustomDeviceName) ?? '';

  /// Cached for headless WorkManager engines, where MainActivity's custom
  /// method-channel handler is not necessarily registered.
  String? get deviceUniqueIdentifier =>
      _prefs.getString(_keyDeviceUniqueIdentifier);
  String? get deviceModelName => _prefs.getString(_keyDeviceModelName);

  Future<void> cacheDeviceIdentity({
    required String uniqueIdentifier,
    required String deviceName,
  }) async {
    await _prefs.setString(_keyDeviceUniqueIdentifier, uniqueIdentifier);
    await _prefs.setString(_keyDeviceModelName, deviceName);
  }

  Future<void> setCustomDeviceName(String name) =>
      _prefs.setString(_keyCustomDeviceName, name);

  /// Available-storage percentage, cached for the same reason the device
  /// identity above is: the `disk_stats` channel is registered by
  /// `MainActivity`, so a headless WorkManager engine cannot read it live.
  ///
  /// `null` means "never measured" — before the first UI launch there is no
  /// figure, and writing a storage warning from a guessed number would be worse
  /// than not writing one.
  int? get storageAvailablePercent =>
      _prefs.getInt(_keyStorageAvailablePercent);

  Future<void> cacheStorageAvailablePercent(int percent) =>
      _prefs.setInt(_keyStorageAvailablePercent, percent);

  /// Port of `SharedPrefManager.getLastUsageUploaded` /
  /// `setLastUsageUploaded`. Epoch millis; `0` means "from the epoch", which
  /// reports the full available usage history on the first upload.
  int get lastUsageUploaded => _prefs.getInt(_keyLastUsageUploaded) ?? 0;

  Future<void> setLastUsageUploaded(int epochMillis) =>
      _prefs.setInt(_keyLastUsageUploaded, epochMillis);

  /// Port of `SharedPrefManager.getVersionDetail` / `setVersionDetail`. The
  /// raw JSON string the server reported, or `null` before the first
  /// handshake that carries it.
  String? get versionDetail => _prefs.getString(_keyVersionDetail);

  Future<void> setVersionDetail(String json) =>
      _prefs.setString(_keyVersionDetail, json);

  /// Persisted library (resources) view mode. Port of
  /// `SharedPrefManager.getLibraryViewMode` / `setLibraryViewMode`.
  String get libraryViewMode => _prefs.getString(_keyLibraryViewMode) ?? 'grid';

  Future<void> setLibraryViewMode(String mode) =>
      _prefs.setString(_keyLibraryViewMode, mode);

  /// Persisted course view mode. Port of
  /// `SharedPrefManager.getCourseViewMode` / `setCourseViewMode`.
  String get courseViewMode => _prefs.getString(_keyCourseViewMode) ?? 'grid';

  Future<void> setCourseViewMode(String mode) =>
      _prefs.setString(_keyCourseViewMode, mode);

  /// Section of a deep link waiting for a session, or `''` when there is none.
  String get pendingDeepLinkSection =>
      _prefs.getString(_keyDeepLinkSection) ?? '';

  /// Content id that came with [pendingDeepLinkSection], if any.
  String? get pendingDeepLinkId {
    final id = _prefs.getString(_keyDeepLinkId);
    // The Kotlin reads this through `.ifEmpty { null }`, so a stored empty
    // string and an absent key mean the same thing.
    return id == null || id.isEmpty ? null : id;
  }

  Future<void> setPendingDeepLink(String section, String? contentId) async {
    await _prefs.setString(_keyDeepLinkSection, section);
    // `else prefData.removeKey(DEEP_LINK_ID_KEY)` — a link without an id must
    // clear the id left by an earlier one rather than inherit it.
    if (contentId != null && contentId.isNotEmpty) {
      await _prefs.setString(_keyDeepLinkId, contentId);
    } else {
      await _prefs.remove(_keyDeepLinkId);
    }
  }

  Future<void> clearPendingDeepLink() async {
    await _prefs.remove(_keyDeepLinkSection);
    await _prefs.remove(_keyDeepLinkId);
  }

  /// Port of `SurveysRepositoryImpl.scheduleSurveyReminder`.
  ///
  /// [surveyIds] is the comma-joined submission id list the dialog was showing,
  /// used verbatim as the key suffix so the reminder re-opens the same set. The
  /// Kotlin also writes a `reminder_surveys_<ids>` string holding the same ids
  /// it already encoded in the key; that second key is never read, so it is not
  /// ported.
  Future<void> scheduleSurveyReminder(String surveyIds, Duration delay) =>
      _prefs.setInt(
        '$_keyReminderTimePrefix$surveyIds',
        DateTime.now().add(delay).millisecondsSinceEpoch,
      );

  /// Port of `SurveysRepositoryImpl.isReminderScheduled` — the guard that stops
  /// the hourly dialog from reappearing for a set the user has snoozed.
  bool isReminderScheduled(String surveyIds) =>
      _prefs.containsKey('$_keyReminderTimePrefix$surveyIds');

  /// Reminder id-sets whose time has arrived, clearing them as it reports them.
  ///
  /// Port of the body of `SurveysRepositoryImpl.dueRemindersFlow`, minus its
  /// `while (true) { … delay(60_000) }` loop — the polling belongs to the
  /// caller (`dueSurveyRemindersProvider`) so this stays synchronous and
  /// testable. Reading and removing together matches the Kotlin, which removes
  /// each key in the same pass that emits it, so a reminder fires once.
  Future<List<String>> takeDueSurveyReminders(int nowMillis) async {
    final due = <String>[];
    for (final key in _prefs.getKeys()) {
      if (!key.startsWith(_keyReminderTimePrefix)) continue;
      final at = _prefs.getInt(key) ?? 0;
      if (at <= nowMillis) {
        due.add(key.substring(_keyReminderTimePrefix.length));
      }
    }
    for (final surveyIds in due) {
      await _prefs.remove('$_keyReminderTimePrefix$surveyIds');
    }
    return due;
  }

  /// Whether Android should wake the app to synchronize while it is closed.
  bool get autoSyncEnabled => _prefs.getBool(_keyAutoSync) ?? true;

  Future<void> setAutoSyncEnabled(bool value) =>
      _prefs.setBool(_keyAutoSync, value);

  /// `SharedPrefManager.getHasShownCongrats` / `setHasShownCongrats`.
  bool get hasShownChallengeCongrats =>
      _prefs.getBool(_keyHasShownChallengeCongrats) ?? false;

  Future<void> setHasShownChallengeCongrats(bool value) =>
      _prefs.setBool(_keyHasShownChallengeCongrats, value);

  /// `LocaleUtils.getTextScale` / `setTextScale`. The three scales the Kotlin
  /// dialog offers; a missing preference defaults to 1.0 (medium).
  double get textScale => _prefs.getDouble(_keyTextScale) ?? 1.0;

  Future<void> setTextScale(double value) =>
      _prefs.setDouble(_keyTextScale, value);

  /// `SharedPrefManager.getMediaPlaybackPosition` / `setMediaPlaybackPosition`.
  ///
  /// A non-positive position *removes* the key rather than storing a zero, as
  /// the Kotlin does: an absent entry and a stored 0 both mean "start from the
  /// beginning", and not accumulating one dead key per resource ever watched to
  /// the end is the reason it deletes.
  int mediaPlaybackPosition(String resourceKey) =>
      _prefs.getInt('$_keyMediaProgressPrefix$resourceKey') ?? 0;

  Future<void> setMediaPlaybackPosition(
    String resourceKey,
    int positionMs,
  ) async {
    final key = '$_keyMediaProgressPrefix$resourceKey';
    if (positionMs <= 0) {
      await _prefs.remove(key);
    } else {
      await _prefs.setInt(key, positionMs);
    }
  }

  /// `SharedPrefManager.getMediaPlaybackSpeed` / `setMediaPlaybackSpeed`. One
  /// speed for all media, defaulting to 1.0 — Kotlin stores a `Float`.
  double get mediaPlaybackSpeed =>
      _prefs.getDouble(_keyMediaPlaybackSpeed) ?? 1.0;

  Future<void> setMediaPlaybackSpeed(double speed) =>
      _prefs.setDouble(_keyMediaPlaybackSpeed, speed);

  /// Requested automatic-sync cadence. Android WorkManager enforces a
  /// 15-minute floor; older Kotlin preferences below that are clamped by the
  /// scheduler rather than silently discarded.
  Duration get autoSyncInterval =>
      Duration(seconds: _prefs.getInt(_keyAutoSyncInterval) ?? 60 * 60);

  Future<void> setAutoSyncInterval(Duration value) =>
      _prefs.setInt(_keyAutoSyncInterval, value.inSeconds);

  /// Last headless-run diagnostic. It intentionally contains no URLs,
  /// credentials, payloads, or exception text; only stable step names are
  /// persisted so support can distinguish scheduling from domain failures.
  Map<String, dynamic>? get lastBackgroundRun {
    final encoded = _prefs.getString(_keyBackgroundRun);
    if (encoded == null) return null;
    try {
      final decoded = jsonDecode(encoded);
      return decoded is Map<String, dynamic> ? decoded : null;
    } on FormatException {
      return null;
    }
  }

  Future<void> recordBackgroundRun({
    required String taskName,
    required DateTime attemptedAt,
    required String status,
    required List<String> failedSteps,
    String? skipReason,
  }) => _prefs.setString(
    _keyBackgroundRun,
    jsonEncode({
      'taskName': taskName,
      'attemptedAt': attemptedAt.toUtc().toIso8601String(),
      'status': status,
      'failedSteps': failedSteps,
      'skipReason': ?skipReason,
    }),
  );

  /// Document offset a heavy table's interrupted walk should resume from, `0`
  /// when there is nothing to resume. Port of
  /// `sharedPrefManager.rawPreferences.getInt(checkpointKey, 0)`
  /// (`TransactionSyncManager.kt:178`, `HeavyTableSyncWorker.kt:33`).
  ///
  /// A key present with the value `0` reads the same as an absent one, exactly
  /// as the Kotlin's `getInt(key, 0)` does — which matters because the walk
  /// writes `0` before its first page request and only *removes* the key on a
  /// completed walk.
  int heavyTableSkip(String table) =>
      _prefs.getInt('$_keyHeavySyncSkipPrefix$table') ?? 0;

  Future<void> setHeavyTableSkip(String table, int skip) =>
      _prefs.setInt('$_keyHeavySyncSkipPrefix$table', skip);

  Future<void> clearHeavyTableSkip(String table) =>
      _prefs.remove('$_keyHeavySyncSkipPrefix$table');

  /// When the interactive sync began, or `null` when none is running.
  DateTime? get interactiveSyncStartedAt {
    final millis = _prefs.getInt(_keyInteractiveSyncStartedAt) ?? 0;
    return millis == 0 ? null : DateTime.fromMillisecondsSinceEpoch(millis);
  }

  Future<void> markInteractiveSyncStarted(DateTime now) =>
      _prefs.setInt(_keyInteractiveSyncStartedAt, now.millisecondsSinceEpoch);

  Future<void> clearInteractiveSyncStarted() =>
      _prefs.remove(_keyInteractiveSyncStartedAt);

  /// Re-reads the backing store, discarding this instance's in-memory cache.
  ///
  /// `SharedPreferences` caches every value per isolate at
  /// `getInstance()`, so a value another isolate has written since is
  /// invisible to this one. The Kotlin has no analogue to need because its
  /// workers run in the app process and read the same `SharedPreferences`
  /// object the UI writes — a `HeavyTableSyncWorker` reading
  /// `heavy_sync_skip_*` and `isSyncing` sees the UI's writes for free.
  ///
  /// Called by the heavy-table entry point, which is the one path that reads
  /// state the *other* isolate authors. Everything else reads what it wrote.
  Future<void> reload() => _prefs.reload();
}
