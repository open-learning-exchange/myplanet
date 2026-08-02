import 'package:meta/meta.dart';

/// The persisted server identity, replacing the loose bag of `serverPin` /
/// `couchdbURL` / `alternativeUrl` / `isAlternativeUrl` keys that
/// `services/SharedPrefManager.kt` reads out of `SharedPreferences`.
///
/// Bundling them into one immutable value means [UrlUtils] can be a set of pure
/// functions instead of a singleton that has to be `init`-ed with a
/// `SharedPrefManager` before use (the Kotlin `UrlUtils.init` / `spm()` dance).
@immutable
class ServerConfig {
  const ServerConfig({
    required this.serverUrl,
    required this.pin,
    required this.couchDbUrl,
    this.alternativeUrl,
    this.isAlternativeUrl = false,
    this.code = '',
    this.id = '',
    this.parentCode = '',
  });

  /// The URL the user typed, e.g. `https://planet.example.org`. Used verbatim
  /// for the `/versions` probe.
  final String serverUrl;

  /// The `satellite` account PIN for this server.
  final String pin;

  /// `scheme://satellite:pin@host:port`, built by [buildCouchDbUrl].
  final String couchDbUrl;

  /// A mirror of this server discovered via [ServerUrlMapper], if any.
  final String? alternativeUrl;

  /// Whether the app should currently prefer [alternativeUrl] over [couchDbUrl].
  final bool isAlternativeUrl;

  /// `code` from the community's `configurations` document.
  final String code;

  /// `_id` of the community's `configurations` document.
  final String id;

  /// `parentCode` from the community's `configurations` document.
  final String parentCode;

  /// Port of `ConfigurationsRepositoryImpl.buildCouchdbUrl`.
  ///
  /// Note this deliberately keeps only scheme/host/port and drops any path, so
  /// `https://planet.example.org/ml` yields `https://satellite:pin@planet.example.org:443`.
  /// CouchDB is reached at the host root under `/db`.
  static String buildCouchDbUrl(String currentUrl, String pin) {
    if (currentUrl.contains('@')) return currentUrl;

    final uri = Uri.parse(currentUrl);
    final scheme = uri.scheme;
    final port = uri.hasPort ? uri.port : (scheme == 'http' ? 80 : 443);
    return '$scheme://satellite:$pin@${uri.host}:$port';
  }

  ServerConfig copyWith({
    String? serverUrl,
    String? pin,
    String? couchDbUrl,
    String? alternativeUrl,
    bool? isAlternativeUrl,
    String? code,
    String? id,
    String? parentCode,
  }) {
    return ServerConfig(
      serverUrl: serverUrl ?? this.serverUrl,
      pin: pin ?? this.pin,
      couchDbUrl: couchDbUrl ?? this.couchDbUrl,
      alternativeUrl: alternativeUrl ?? this.alternativeUrl,
      isAlternativeUrl: isAlternativeUrl ?? this.isAlternativeUrl,
      code: code ?? this.code,
      id: id ?? this.id,
      parentCode: parentCode ?? this.parentCode,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ServerConfig &&
          other.serverUrl == serverUrl &&
          other.pin == pin &&
          other.couchDbUrl == couchDbUrl &&
          other.alternativeUrl == alternativeUrl &&
          other.isAlternativeUrl == isAlternativeUrl &&
          other.code == code &&
          other.id == id &&
          other.parentCode == parentCode;

  @override
  int get hashCode => Object.hash(
    serverUrl,
    pin,
    couchDbUrl,
    alternativeUrl,
    isAlternativeUrl,
    code,
    id,
    parentCode,
  );
}
