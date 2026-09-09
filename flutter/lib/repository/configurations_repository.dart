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

  /// The server answered, and refused the `satellite` PIN.
  ///
  /// **Kotlin does not distinguish this**, and that is the point of adding it.
  /// `checkConfigurationUrl` folds every failure into one "couldn't reach the
  /// server" string, so a wrong or empty PIN — the single most likely thing to
  /// go wrong on this screen — tells the user to check their internet
  /// connection. It cost this project a full diagnostic pass on a device that
  /// was online, against a server that was up, with a `minapk` that passed:
  /// `/versions` returned 200 and only the credentialed
  /// `configurations/_all_docs` came back 401. Phase 60 predicted exactly this
  /// class of confusion for the *version* check; this is its other half.
  pinRejected,
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
  const ConfigurationFailure(this.reason, this.url, {this.diagnostic});

  final ConfigurationFailureReason reason;
  final String url;

  /// Which step failed and what it reported, un-localised, for a debug build
  /// to show.
  ///
  /// Every failure in the handshake used to collapse into one sentence with
  /// the status code, the exception type and even *which of the two requests*
  /// discarded. Kotlin at least logs its cause to Logcat; the port had nowhere
  /// to put it, so a failure that could not be reproduced off-device could
  /// only be guessed at — and two of my guesses about one were wrong. This is
  /// the cheapest thing that turns the next report into an answer.
  final String? diagnostic;
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
  static const String defaultAppVersion = '0.70.11';

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

    // A refusal outranks a silence. With an alternative URL in play the two
    // candidates are raced, so one can 401 while the other never answers —
    // and "the PIN was rejected" is the one the user can act on, and the one
    // that proves a server was there at all.
    final rejected = results.whereType<_UrlCheckFailure>().any(
      (failure) => failure.pinRejected,
    );
    final diagnostics = results
        .whereType<_UrlCheckFailure>()
        .map((failure) => failure.diagnostic)
        .whereType<String>()
        .toList();
    return ConfigurationFailure(
      rejected
          ? ConfigurationFailureReason.pinRejected
          : _failureReasonFor(url),
      url,
      diagnostic: diagnostics.isEmpty ? null : diagnostics.join('\n'),
    );
  }

  /// Port of `checkConfigurationUrl`.
  Future<_UrlCheckResult> _checkConfigurationUrl(
    String currentUrl,
    String pin,
  ) async {
    final versionsResult = await _api.getConfiguration('$currentUrl/versions');
    if (versionsResult is! NetworkSuccess<Map<String, dynamic>>) {
      return _UrlCheckFailure(
        currentUrl,
        diagnostic: _describeFailure('$currentUrl/versions', versionsResult),
      );
    }

    // Port of `SharedPrefManager.setVersionDetail`: keep the raw `/versions`
    // body so the telemetry upload can echo `planetVersion` back. The Kotlin
    // round-trips it through Gson; the port stores the canonical JSON string.
    final versionDetail = jsonEncode(versionsResult.data);

    final minApkVersion = JsonUtils.getStringOrNull(
      'minapk',
      versionsResult.data,
    );
    final appVersion = await _resolveAppVersion();
    if (minApkVersion == null ||
        !VersionUtils.isVersionAllowed(appVersion, minApkVersion)) {
      return _UrlCheckFailure(
        currentUrl,
        diagnostic: minApkVersion == null
            ? '$currentUrl/versions: no minapk'
            : '$currentUrl: build $appVersion below minapk $minApkVersion',
      );
    }

    final couchDbUrl = ServerConfig.buildCouchDbUrl(currentUrl, pin);
    final fetch = await _fetchConfiguration(couchDbUrl);
    final configuration = fetch.configuration;
    if (configuration == null) {
      // `buildCouchDbUrl` short-circuits on a URL that already carries
      // `user:pass@`, so on that path the PIN field was never sent and a 401
      // is not a verdict on it. Blaming it would point the user at a field
      // that had no effect on the request.
      final pinWasUsed = !currentUrl.contains('@');
      return _UrlCheckFailure(
        currentUrl,
        pinRejected: fetch.pinRejected && pinWasUsed,
        diagnostic: fetch.diagnostic == null
            ? null
            : '$currentUrl/db/configurations: ${fetch.diagnostic}',
      );
    }

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
  ///
  /// Returns the configuration, or why it could not be read. The Kotlin
  /// returns a nullable pair and so cannot tell a refusal from a silence; this
  /// keeps the distinction so [ConfigurationFailureReason.pinRejected] can be
  /// reported. **This is the only request in the handshake that carries the
  /// PIN** — `/versions` is unauthenticated — so a 401 or 403 here is a
  /// verdict on the credentials and nothing else.
  Future<_ConfigurationFetch> _fetchConfiguration(String couchDbUrl) async {
    final url =
        '${UrlUtils.dbUrlOf(couchDbUrl)}/configurations/_all_docs?include_docs=true';
    final result = await _api.getConfiguration(url);
    if (result is! NetworkSuccess<Map<String, dynamic>>) {
      final rejected =
          result is NetworkError<Map<String, dynamic>> &&
          (result.code == 401 || result.code == 403);
      return _ConfigurationFetch.failed(
        pinRejected: rejected,
        diagnostic: _describeFailure('fetch', result),
      );
    }

    final rows = result.data['rows'];
    if (rows is! List || rows.isEmpty) {
      return _ConfigurationFetch.failed(diagnostic: 'no configuration rows');
    }

    final firstRow = rows.first;
    if (firstRow is! Map<String, dynamic>) return _ConfigurationFetch.failed();

    final doc = JsonUtils.getObject('doc', firstRow);
    if (doc == null) return _ConfigurationFetch.failed();

    return _ConfigurationFetch(
      _CommunityConfiguration(
        id: JsonUtils.getString('id', firstRow),
        code: JsonUtils.getString('code', doc),
        parentCode: JsonUtils.getString('parentCode', doc),
        preferredLanguage: languageCodeFromName(
          JsonUtils.getString('preferredLang', doc),
        ),
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

/// What [ConfigurationsRepository._fetchConfiguration] found: the community
/// configuration, or the reason it is absent. Exists so a refused PIN can be
/// told from a server that never answered.
@immutable
class _ConfigurationFetch {
  const _ConfigurationFetch(this.configuration)
    : pinRejected = false,
      diagnostic = null;

  const _ConfigurationFetch.failed({this.pinRejected = false, this.diagnostic})
    : configuration = null;

  final _CommunityConfiguration? configuration;

  /// The server answered and refused the credentials.
  final bool pinRejected;

  final String? diagnostic;
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
  const _UrlCheckFailure(this.url, {this.pinRejected = false, this.diagnostic});

  final String url;

  /// The server was reached and rejected the PIN, rather than not answering.
  final bool pinRejected;

  final String? diagnostic;
}

/// A short description of a failed request, for [ConfigurationFailure.diagnostic].
String _describeFailure(String step, NetworkResult<dynamic> result) {
  if (result is NetworkError) {
    final message = result.message;
    return '$step: HTTP ${result.code}${message == null ? '' : ' $message'}';
  }
  if (result is NetworkException) {
    return '$step: ${result.error.runtimeType}: ${result.error}';
  }
  return step;
}
