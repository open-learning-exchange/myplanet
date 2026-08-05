import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../../core/network/network_result.dart';

/// Port of the subset of `data/api/ApiInterface.kt` that the login /
/// configuration / resources slice needs.
///
/// Retrofit's interface-plus-codegen model has no direct Dart analogue, so the
/// declarative annotations become thin methods over [Dio]. Every call funnels
/// through [_request], which converts Dio's throw-on-error behaviour into the
/// [NetworkResult] the Kotlin `ApiClient.executeWithResult` produced — callers
/// switch on the result instead of wrapping each call in try/catch.
class PlanetApi {
  PlanetApi(this._dio);

  /// The `withTimeout(15_000)` wrapped around the configuration calls in
  /// `ConfigurationsRepositoryImpl`.
  static const Duration defaultTimeout = Duration(seconds: 15);

  factory PlanetApi.withDefaults({Duration timeout = defaultTimeout}) {
    return PlanetApi(
      Dio(
        BaseOptions(
          connectTimeout: timeout,
          receiveTimeout: timeout,
          sendTimeout: timeout,
          // CouchDB answers an unauthenticated request with 401, which the
          // availability probe treats as "reachable" — so non-2xx must be a
          // value, not a throw.
          validateStatus: (_) => true,
        ),
      ),
    );
  }

  final Dio _dio;

  /// Port of `ApiInterface.getJsonObject`.
  Future<NetworkResult<Map<String, dynamic>>> getJsonObject(
    String url, {
    String? authHeader,
  }) {
    return _request<Map<String, dynamic>>(
      url,
      authHeader: authHeader,
      responseType: ResponseType.json,
      convert: (data) => data is Map<String, dynamic>
          ? data
          : throw const FormatException('Expected a JSON object'),
    );
  }

  /// Port of `ApiInterface.getConfiguration` — same shape, no auth header.
  Future<NetworkResult<Map<String, dynamic>>> getConfiguration(String url) =>
      getJsonObject(url);

  /// JSON-array variant used by the legacy dictionary download.
  Future<NetworkResult<List<dynamic>>> getJsonList(String url) {
    return _request<List<dynamic>>(
      url,
      responseType: ResponseType.json,
      convert: (data) => data is List
          ? data
          : throw const FormatException('Expected a JSON array'),
    );
  }

  /// Port of `ApiInterface.putDoc` — creates or updates a CouchDB document.
  ///
  /// The body must carry `_rev` for an update, or CouchDB answers 409.
  Future<NetworkResult<Map<String, dynamic>>> putJsonObject(
    String url,
    Map<String, dynamic> body, {
    String? authHeader,
  }) {
    return _request<Map<String, dynamic>>(
      url,
      method: 'PUT',
      body: body,
      authHeader: authHeader,
      responseType: ResponseType.json,
      convert: (data) => data is Map<String, dynamic>
          ? data
          : throw const FormatException('Expected a JSON object'),
    );
  }

  /// Port of `ApiInterface.postDoc` — appends a new CouchDB document.
  ///
  /// CouchDB assigns the `_id`/`_rev` and returns them, so unlike
  /// [putJsonObject] the body carries neither.
  Future<NetworkResult<Map<String, dynamic>>> postJsonObject(
    String url,
    Map<String, dynamic> body, {
    String? authHeader,
  }) => sendJsonObject(url, body: body, authHeader: authHeader);

  /// [putJsonObject]/[postJsonObject] with the verb chosen at call time.
  ///
  /// The outbox stores the method alongside the payload and replays it, so it
  /// needs one entry point rather than a branch per verb.
  Future<NetworkResult<Map<String, dynamic>>> sendJsonObject(
    String url, {
    required Map<String, dynamic> body,
    String method = 'POST',
    String? authHeader,
  }) {
    return _request<Map<String, dynamic>>(
      url,
      method: method,
      body: body,
      authHeader: authHeader,
      responseType: ResponseType.json,
      convert: (data) => data is Map<String, dynamic>
          ? data
          : throw const FormatException('Expected a JSON object'),
    );
  }

  /// Same as [sendJsonObject], but returns the raw decoded response without
  /// forcing it to be a JSON object. Used by the public survey API, where the
  /// success body shape is not important — only the HTTP status matters.
  Future<NetworkResult<dynamic>> sendJsonDynamic(
    String url, {
    required Map<String, dynamic> body,
    String method = 'POST',
    String? authHeader,
  }) {
    return _request<dynamic>(
      url,
      method: method,
      body: body,
      authHeader: authHeader,
      responseType: ResponseType.json,
      convert: (data) => data,
    );
  }

  /// Port of `ApiInterface.getApkVersion` / `getChecksum` — raw text bodies.
  Future<NetworkResult<String>> getRaw(String url, {String? authHeader}) {
    return _request<String>(
      url,
      authHeader: authHeader,
      responseType: ResponseType.plain,
      convert: (data) => data?.toString() ?? '',
    );
  }

  /// Port of `ApiInterface.uploadResource` — PUTs a raw attachment body.
  ///
  /// CouchDB stores the bytes at the named slot for the given document and
  /// returns `{"ok": true, "id": "...", "rev": "..."}`.
  Future<NetworkResult<Map<String, dynamic>>> uploadAttachment(
    String url, {
    required List<int> bytes,
    String? authHeader,
    String? contentType,
    String? ifMatch,
  }) {
    return _request<Map<String, dynamic>>(
      url,
      method: 'PUT',
      body: Uint8List.fromList(bytes),
      authHeader: authHeader,
      responseType: ResponseType.json,
      extraHeaders: {
        if (contentType != null && contentType.isNotEmpty)
          'Content-Type': contentType,
        if (ifMatch != null && ifMatch.isNotEmpty) 'If-Match': ifMatch,
      },
      convert: (data) => data is Map<String, dynamic>
          ? data
          : throw const FormatException('Expected a JSON object'),
    );
  }

  /// Port of `ApiInterface.downloadFile` — an attachment body as raw bytes.
  ///
  /// Separate from [_request] because it reports progress and must not apply
  /// the shared receive timeout: a large resource on a slow link would abort
  /// mid-transfer, which is precisely the connection myPlanet is built for.
  Future<NetworkResult<List<int>>> getBytes(
    String url, {
    String? authHeader,
    void Function(int received, int total)? onProgress,
  }) async {
    try {
      final response = await _dio.get<List<int>>(
        url,
        onReceiveProgress: onProgress,
        options: Options(
          responseType: ResponseType.bytes,
          receiveTimeout: null,
          validateStatus: (_) => true,
          headers: {'Authorization': ?authHeader},
        ),
      );
      final status = response.statusCode ?? 0;
      if (status < 200 || status >= 300) {
        return NetworkError<List<int>>(status, response.statusMessage);
      }
      return NetworkSuccess<List<int>>(response.data ?? const []);
    } on DioException catch (e) {
      return NetworkException<List<int>>(e);
    }
  }

  Future<NetworkResult<T>> _request<T>(
    String url, {
    required ResponseType responseType,
    required T Function(dynamic data) convert,
    String method = 'GET',
    Object? body,
    String? authHeader,
    Map<String, dynamic>? extraHeaders,
  }) async {
    final headers = <String, dynamic>{
      if (authHeader != null && authHeader.isNotEmpty)
        'Authorization': authHeader,
      ...?extraHeaders,
    };
    // JSON bodies are the common case for the CouchDB document API; raw byte
    // bodies (resource attachments) carry their own Content-Type.
    if (!headers.containsKey('Content-Type') && body is Map<String, dynamic>) {
      headers['Content-Type'] = 'application/json';
    }
    try {
      final response = await _dio.request<dynamic>(
        url,
        data: body,
        options: Options(
          method: method,
          responseType: responseType,
          // Per request for the same reason as validateStatus below: an
          // injected Dio may carry no BaseOptions timeouts. connectTimeout is
          // connection-scoped and stays in BaseOptions.
          receiveTimeout: defaultTimeout,
          sendTimeout: defaultTimeout,
          // Set per request, not only in `withDefaults`: the public constructor
          // accepts any Dio, and with a default one a 401 would throw and be
          // reported as unreachable rather than as an authentication failure.
          validateStatus: (_) => true,
          headers: headers,
        ),
      );

      final status = response.statusCode ?? 0;
      if (status < 200 || status >= 300) {
        return NetworkError<T>(status, response.statusMessage);
      }
      return NetworkSuccess<T>(convert(response.data));
    } on DioException catch (e) {
      return NetworkException<T>(e);
    } on FormatException catch (e) {
      return NetworkException<T>(e);
    }
  }
}
