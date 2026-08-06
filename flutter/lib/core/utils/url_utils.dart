import 'dart:convert';

import '../config/server_config.dart';

/// Port of `utils/UrlUtils.kt`.
///
/// The Kotlin original is an `object` holding a `@Volatile` `SharedPrefManager`
/// that must be `init`-ed before any getter works, and it throws if it wasn't.
/// Here every function takes the [ServerConfig] explicitly, which removes the
/// initialisation order hazard and makes the whole file trivially testable.
class UrlUtils {
  const UrlUtils._();

  /// Port of `UrlUtils.basicAuthHeader`.
  static String basicAuthHeader(String username, String password) =>
      'Basic ${base64.encode(utf8.encode('$username:$password'))}';

  /// Port of `UrlUtils.baseUrl(spm)`: the active server root, without `/db`.
  static String baseUrl(ServerConfig config) {
    final url = config.isAlternativeUrl && config.alternativeUrl != null
        ? config.alternativeUrl!
        : config.couchDbUrl;
    return url.endsWith('/db') ? url.substring(0, url.length - 3) : url;
  }

  /// Port of `UrlUtils.dbUrl(spm)`: the CouchDB root for the active server.
  static String dbUrl(ServerConfig config) => dbUrlOf(baseUrl(config));

  /// [dbUrl] with the `satellite:PIN@` userinfo removed.
  ///
  /// [dbUrl] embeds the credentials because most callers hand the string
  /// straight to Dio and never store it. Anything that *persists* a URL must
  /// use this instead: the outbox keeps `endpoint` in SQLite until the
  /// operation succeeds, and that table deliberately survives schema upgrades,
  /// so a credential-bearing endpoint would leave the server PIN sitting in
  /// plaintext on disk indefinitely. Authentication travels as the
  /// `Authorization` header from [basicAuthHeader] instead.
  ///
  /// Returns [dbUrl] unchanged if the URL cannot be parsed — callers still need
  /// a usable endpoint, and an unparseable one has no userinfo to leak.
  static String credentialFreeDbUrl(ServerConfig config) {
    final url = dbUrl(config);
    final parsed = Uri.tryParse(url);
    if (parsed == null || parsed.host.isEmpty) return url;
    return parsed.replace(userInfo: '').toString();
  }

  /// Port of `UrlUtils.dbUrl(url: String)`.
  static String dbUrlOf(String url) {
    var base = url;
    if (base.endsWith('/')) {
      base = base.substring(0, base.length - 1);
    }
    return base.endsWith('/db') ? base : '$base/db';
  }

  /// Port of `UrlUtils.getUpdateUrl`.
  static String updateUrl(ServerConfig config) => '${baseUrl(config)}/versions';

  /// Port of `UrlUtils.getApkVersionUrl`.
  static String apkVersionUrl(ServerConfig config) =>
      '${baseUrl(config)}/apkversion';

  /// Port of `UrlUtils.getChecksumUrl`.
  static String checksumUrl(ServerConfig config) =>
      '${baseUrl(config)}/fs/myPlanet.apk.sha256';

  /// Port of `UrlUtils.getHealthAccessUrl`.
  static String healthAccessUrl(ServerConfig config) {
    final pin = config.pin.isEmpty ? '0000' : config.pin;
    return '${baseUrl(config)}/healthaccess?p=$pin';
  }

  /// Port of `UrlUtils.getUrl(id, file)` — the download URL for a resource
  /// attachment.
  ///
  /// Returns `null` for a missing id or filename. The Kotlin interpolates the
  /// nulls and produces a URL containing the literal text `null`, which can only
  /// 404; refusing to build it lets the caller handle the gap instead.
  static String? resourceUrl(ServerConfig config, String? id, String? file) {
    if (id == null || id.isEmpty || file == null || file.isEmpty) return null;
    return '${dbUrl(config)}/resources'
        '/${Uri.encodeComponent(id)}/${Uri.encodeComponent(file)}';
  }

  /// Port of `UrlUtils.getUserImageUrl`.
  ///
  /// The Kotlin form-encodes both segments and then rewrites `+` back to `%20`
  /// in the image name only; that asymmetry is preserved.
  static String? userImageUrl(
    ServerConfig config,
    String? userId,
    String imageName,
  ) {
    if (userId == null || userId.trim().isEmpty || imageName.trim().isEmpty) {
      return null;
    }
    final encodedUserId = Uri.encodeQueryComponent(userId);
    final encodedImageName = Uri.encodeQueryComponent(
      imageName,
    ).replaceAll('+', '%20');
    return '${dbUrl(config)}/_users/$encodedUserId/$encodedImageName';
  }

  /// Port of `UrlUtils.getCourseImageUrl`.
  static String? courseImageUrl(
    ServerConfig config,
    String? courseId,
    String? imageName,
  ) {
    if (courseId == null ||
        courseId.trim().isEmpty ||
        imageName == null ||
        imageName.trim().isEmpty) {
      return null;
    }
    final encodedCourseId = Uri.encodeQueryComponent(courseId);
    final encodedImageName = Uri.encodeQueryComponent(
      imageName,
    ).replaceAll('+', '%20');
    return '${dbUrl(config)}/courses/$encodedCourseId/$encodedImageName';
  }

  /// The CouchDB `_users` document URL for a login name, as built by
  /// `LoginSyncManager.login`.
  ///
  /// The `org.couchdb.user:` prefix stays literal — CouchDB requires the
  /// unencoded colon in the document id — but the name itself is encoded, so a
  /// login containing a space or `/` addresses the right document.
  static String userDocUrl(ServerConfig config, String userName) =>
      '${dbUrl(config)}/_users/org.couchdb.user:'
      '${Uri.encodeComponent(userName)}';
}
