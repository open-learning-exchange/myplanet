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
    Future<String> Function()? appVersionLookup,
  }) : _appVersionLookup = appVersionLookup;

  /// Fallback for [currentAppVersion] when no [appVersionLookup] is supplied
  /// (every unit test) or when the lookup yields nothing usable.
  ///
  /// Kept in step with `version:` in pubspec.yaml by hand, which is exactly why
  /// production reads the runtime value instead: see [_resolveAppVersion].
  static const String defaultAppVersion = '0.69.18';

  /// The placeholder `package_info_plus` reports when no manifest values are
  /// available — under `flutter test`, and on any platform that declines the
  /// query. It must never reach the `minapk` comparison.
  static const String _placeholderVersion = '0.0.0';

  final PlanetApi _api;
  final ServerUrlMapper _urlMapper;
  final String currentAppVersion;
  final Future<String> Function()? _appVersionLookup;

  /// The version to compare against the server's `minapk`.
  ///
  /// This is the one path where a stale version is not cosmetic: a failed
  /// [VersionUtils.isVersionAllowed] makes `_checkConfigurationUrl` return
  /// `_UrlCheckFailure`, so the whole server-configuration screen fails with no
  /// hint that the version was the reason. A hardcoded constant has to be
  /// remembered on every release bump, so production supplies the build's real
  /// version instead.
  ///
  /// Anything unusable — a throwing lookup, an empty string, or the `0.0.0`
  /// placeholder — falls back to [currentAppVersion]. Under-reporting the
  /// version here would block configuration outright, which is a far worse
  /// outcome than comparing a slightly stale constant.
  Future<String> _resolveAppVersion() async {
    final lookup = _appVersionLookup;
    if (lookup == null) return currentAppVersion;
    try {
      final resolved = await lookup();
      if (resolved.isEmpty || resolved == _placeholderVersion) {
        return currentAppVersion;
      }
      return resolved;
    } catch (_) {
      return currentAppVersion;
    }
  }

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
        !VersionUtils.isVersionAllowed(
          await _resolveAppVersion(),
          minApkVersion,
        )) {
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
