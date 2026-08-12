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

  /// Prefix of the per-reminder keys, matching the Kotlin's
  /// `reminder_time_<surveyIds>` in its `survey_reminders` preferences file.
  /// The Kotlin scans `reminderPrefs.all` for this prefix; the port scans
  /// `getKeys()` on the one shared preferences store for the same thing.
  static const String _keyReminderTimePrefix = 'reminder_time_';
  static const String _keyLastSync = 'LastSync';

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
}
