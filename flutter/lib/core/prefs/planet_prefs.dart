import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/server_config.dart';

/// Port of `services/SharedPrefManager.kt` plus the credential half of
/// `utils/SecurePrefs.kt`.
///
/// Non-secret server identity lives in [SharedPreferences]; the sync password
/// lives in [FlutterSecureStorage], which wraps the Keystore-backed
/// `EncryptedSharedPreferences` the Kotlin uses via Tink.
class PlanetPrefs {
  PlanetPrefs(this._prefs, {FlutterSecureStorage? secureStorage})
    : _secureStorage = secureStorage ?? const FlutterSecureStorage();

  static const String _keyServerUrl = 'serverURL';
  static const String _keyServerPin = 'serverPin';
  static const String _keyCouchDbUrl = 'couchdbURL';
  static const String _keyAlternativeUrl = 'alternativeUrl';
  static const String _keyIsAlternativeUrl = 'isAlternativeUrl';
  static const String _keyConfigurationId = 'configurationId';
  static const String _keyCommunityCode = 'communityCode';
  static const String _keyParentCode = 'parentCode';
  static const String _keyLoggedInUserId = 'loggedInUserId';
  static const String _secureKeyPassword = 'userPassword';

  final SharedPreferences _prefs;
  final FlutterSecureStorage _secureStorage;

  static Future<PlanetPrefs> load() async =>
      PlanetPrefs(await SharedPreferences.getInstance());

  /// `null` until the server-configuration handshake has succeeded once.
  ServerConfig? get serverConfig {
    final serverUrl = _prefs.getString(_keyServerUrl);
    final couchDbUrl = _prefs.getString(_keyCouchDbUrl);
    if (serverUrl == null || couchDbUrl == null) return null;

    return ServerConfig(
      serverUrl: serverUrl,
      pin: _prefs.getString(_keyServerPin) ?? '',
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
    await _prefs.setString(_keyServerPin, config.pin);
    await _prefs.setString(_keyCouchDbUrl, config.couchDbUrl);
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
  }

  String? get loggedInUserId => _prefs.getString(_keyLoggedInUserId);

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
