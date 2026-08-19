import 'dart:convert';

import 'package:meta/meta.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/server_url_mapper.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../core/utils/version_utils.dart';
import '../data/api/planet_api.dart';

/// Why a server could not be configured. The Kotlin returns an already-localised
/// `String` from `context.getString(...)`; keeping the reason symbolic here lets
/// the UI localise it and keeps the repository free of a `BuildContext`.
enum ConfigurationFailureReason {
  /// The device could not reach a local (http) community server.
  localServerUnreachable,

  /// The device could not reach a nation (https) server.
  nationServerUnreachable,
}

/// Port of `ConfigurationsRepository.ConfigurationResult`.
@immutable
sealed class ConfigurationResult {
  const ConfigurationResult();
}

class ConfigurationSuccess extends ConfigurationResult {
  const ConfigurationSuccess(this.config, {this.versionDetail});

  final ServerConfig config;

  /// The raw `/versions` JSON the server reported, port of
  /// `SharedPrefManager.setVersionDetail`. `null` when the handshake could not
  /// read it. Persisted by the caller so `MyPlanet.getNormalMyPlanetActivities`
  /// can echo `planetVersion` back on the next telemetry upload.
  final String? versionDetail;
}

class ConfigurationFailure extends ConfigurationResult {
  const ConfigurationFailure(this.reason, this.url);

  final ConfigurationFailureReason reason;
  final String url;
}

/// Port of the configuration half of `repository/ConfigurationsRepositoryImpl.kt`.
///
/// Drives the "which server am I talking to" handshake:
/// 1. `GET {url}/versions` and check this build satisfies the server's `minapk`.
/// 2. Derive the CouchDB URL as `scheme://satellite:pin@host:port`.
/// 3. `GET {couchdb}/db/configurations/_all_docs?include_docs=true` and read the
///    community's `code` / `parentCode` / `preferredLang` off the first row.
///
/// The primary URL and its mirror are probed concurrently, and the first success
/// wins — the same `async`/`awaitAll` race the Kotlin runs.
class ConfigurationsRepository {
  ConfigurationsRepository(
    this._api,
    this._urlMapper, {
    this.currentAppVersion = defaultAppVersion,
  });

  /// Matches `versionName` in `app/build.gradle` and `version:` in pubspec.yaml.
  static const String defaultAppVersion = '0.62.97';

  final PlanetApi _api;
  final ServerUrlMapper _urlMapper;
  final String currentAppVersion;

  /// Port of `ConfigurationsRepositoryImpl.getMinApk`.
  Future<ConfigurationResult> getMinApk(String url, String pin) async {
    final mapping = _urlMapper.processUrl(url);
    final urlsToTry = <String>[
      url,
      if (mapping.alternativeUrl != null) mapping.alternativeUrl!,
    ];

    final results = await Future.wait(
      urlsToTry.map((candidate) => _checkConfigurationUrl(candidate, pin)),
    );

    for (final result in results) {
      if (result is _UrlCheckSuccess) {
        return ConfigurationSuccess(
          ServerConfig(
            serverUrl: url,
            pin: pin,
            couchDbUrl: ServerConfig.buildCouchDbUrl(result.url, pin),
            alternativeUrl: mapping.alternativeUrl,
            isAlternativeUrl: result.url != url,
            id: result.id,
            code: result.code,
            parentCode: result.parentCode,
          ),
          versionDetail: result.versionDetail,
        );
      }
    }

    return ConfigurationFailure(_failureReasonFor(url), url);
  }

  /// Port of `checkConfigurationUrl`.
  Future<_UrlCheckResult> _checkConfigurationUrl(
    String currentUrl,
    String pin,
  ) async {
    final versionsResult = await _api.getConfiguration('$currentUrl/versions');
    if (versionsResult is! NetworkSuccess<Map<String, dynamic>>) {
      return _UrlCheckFailure(currentUrl);
    }

    // Port of `SharedPrefManager.setVersionDetail`: keep the raw `/versions`
    // body so the telemetry upload can echo `planetVersion` back. The Kotlin
    // round-trips it through Gson; the port stores the canonical JSON string.
    final versionDetail = jsonEncode(versionsResult.data);

    final minApkVersion = JsonUtils.getStringOrNull(
      'minapk',
      versionsResult.data,
    );
    if (minApkVersion == null ||
        !VersionUtils.isVersionAllowed(currentAppVersion, minApkVersion)) {
      return _UrlCheckFailure(currentUrl);
    }

    final couchDbUrl = ServerConfig.buildCouchDbUrl(currentUrl, pin);
    final configuration = await _fetchConfiguration(couchDbUrl);
    if (configuration == null) return _UrlCheckFailure(currentUrl);

    return _UrlCheckSuccess(
      id: configuration.id,
      code: configuration.code,
      parentCode: configuration.parentCode,
      preferredLanguage: configuration.preferredLanguage,
      url: currentUrl,
      versionDetail: versionDetail,
    );
  }

  /// Port of `fetchConfiguration` + `processConfigurationDoc`.
  Future<_CommunityConfiguration?> _fetchConfiguration(
    String couchDbUrl,
  ) async {
    final url =
        '${UrlUtils.dbUrlOf(couchDbUrl)}/configurations/_all_docs?include_docs=true';
    final result = await _api.getConfiguration(url);
    if (result is! NetworkSuccess<Map<String, dynamic>>) return null;

    final rows = result.data['rows'];
    if (rows is! List || rows.isEmpty) return null;

    final firstRow = rows.first;
    if (firstRow is! Map<String, dynamic>) return null;

    final doc = JsonUtils.getObject('doc', firstRow);
    if (doc == null) return null;

    return _CommunityConfiguration(
      id: JsonUtils.getString('id', firstRow),
      code: JsonUtils.getString('code', doc),
      parentCode: JsonUtils.getString('parentCode', doc),
      preferredLanguage: languageCodeFromName(
        JsonUtils.getString('preferredLang', doc),
      ),
    );
  }

  /// Port of `ConfigurationsRepositoryImpl.getLanguageCodeFromName`.
  static String? languageCodeFromName(String languageName) {
    switch (languageName.toLowerCase()) {
      case 'english':
        return 'en';
      case 'spanish':
      case 'español':
        return 'es';
      case 'somali':
        return 'so';
      case 'nepali':
        return 'ne';
      case 'arabic':
      case 'العربية':
        return 'ar';
      case 'french':
      case 'français':
        return 'fr';
      default:
        return null;
    }
  }

  static ConfigurationFailureReason _failureReasonFor(String url) {
    return url.startsWith('https')
        ? ConfigurationFailureReason.nationServerUnreachable
        : ConfigurationFailureReason.localServerUnreachable;
  }
}

@immutable
class _CommunityConfiguration {
  const _CommunityConfiguration({
    required this.id,
    required this.code,
    required this.parentCode,
    required this.preferredLanguage,
  });

  final String id;
  final String code;
  final String parentCode;
  final String? preferredLanguage;
}

sealed class _UrlCheckResult {
  const _UrlCheckResult();
}

class _UrlCheckSuccess extends _UrlCheckResult {
  const _UrlCheckSuccess({
    required this.id,
    required this.code,
    required this.parentCode,
    required this.preferredLanguage,
    required this.url,
    required this.versionDetail,
  });

  final String id;
  final String code;
  final String parentCode;
  final String? preferredLanguage;
  final String url;
  final String? versionDetail;
}

class _UrlCheckFailure extends _UrlCheckResult {
  const _UrlCheckFailure(this.url);

  final String url;
}
