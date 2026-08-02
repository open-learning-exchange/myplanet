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
    : _secureStorage = secureStorage ?? const FlutterSecureStorage();

  static const String _keyServerUrl = 'serverURL';
  static const String _keyAlternativeUrl = 'alternativeUrl';
  static const String _keyIsAlternativeUrl = 'isAlternativeUrl';
  static const String _keyConfigurationId = 'configurationId';
  static const String _keyCommunityCode = 'communityCode';
  static const String _keyParentCode = 'parentCode';
  static const String _keyLoggedInUserId = 'loggedInUserId';
  static const String _keyOnboardingComplete = 'onboardingComplete';

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
}
