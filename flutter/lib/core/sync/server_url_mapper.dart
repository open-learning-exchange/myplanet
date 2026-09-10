import 'package:meta/meta.dart';

/// Port of `services/sync/ServerUrlMapper.kt`.
///
/// Maps a known local (http) community server to its public (https) clone, so a
/// device that cannot reach the local box can fall back to the mirror.
///
/// **Deliberate change from the Kotlin.** The original reads its mapping table
/// out of `BuildConfig.PLANET_*_URL` constants, which come from the tracked
/// `gradle.properties` — the committed-secrets problem flagged in `CLAUDE.md`.
/// Here the table is supplied by the caller and defaults to the compile-time
/// `--dart-define=PLANET_SERVER_MAPPINGS=...` value, so nothing sensitive is
/// checked in. Format is a comma-separated list of `primary=alternative` pairs.
@immutable
class UrlMapping {
  const UrlMapping({
    required this.primaryUrl,
    this.alternativeUrl,
    this.extractedBaseUrl,
  });

  final String primaryUrl;
  final String? alternativeUrl;
  final String? extractedBaseUrl;
}

class ServerUrlMapper {
  ServerUrlMapper({Map<String, String>? mappings})
    : _mappings = mappings ?? parseMappings(_mappingsFromEnvironment);

  static const String _mappingsFromEnvironment = String.fromEnvironment(
    'PLANET_SERVER_MAPPINGS',
  );

  final Map<String, String> _mappings;

  /// Parses `http://a.example=https://a-clone.example,http://b.example=https://b-clone.example`.
  static Map<String, String> parseMappings(String raw) {
    final result = <String, String>{};
    for (final pair in raw.split(',')) {
      final trimmed = pair.trim();
      if (trimmed.isEmpty) continue;
      final separator = trimmed.indexOf('=');
      if (separator <= 0 || separator == trimmed.length - 1) continue;
      result[trimmed.substring(0, separator).trim()] = trimmed
          .substring(separator + 1)
          .trim();
    }
    return result;
  }

  /// Port of `ServerUrlMapper.extractBaseUrl`: `scheme://host[:port]`, omitting
  /// the port when it is the scheme default.
  static String? extractBaseUrl(String url) {
    try {
      final uri = Uri.parse(url);
      if (uri.scheme.isEmpty || uri.host.isEmpty) return null;
      final isDefaultPort =
          (uri.scheme == 'http' && uri.port == 80) ||
          (uri.scheme == 'https' && uri.port == 443);
      return uri.hasPort && !isDefaultPort
          ? '${uri.scheme}://${uri.host}:${uri.port}'
          : '${uri.scheme}://${uri.host}';
    } on FormatException {
      return null;
    }
  }

  /// Port of `ServerUrlMapper.processUrl`.
  UrlMapping processUrl(String url) {
    final extracted = extractBaseUrl(url);
    return UrlMapping(
      primaryUrl: url,
      alternativeUrl: extracted == null ? null : _mappings[extracted],
      extractedBaseUrl: extracted,
    );
  }
}
