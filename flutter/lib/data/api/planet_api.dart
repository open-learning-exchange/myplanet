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

  /// Port of `ApiInterface.getApkVersion` / `getChecksum` — raw text bodies.
  Future<NetworkResult<String>> getRaw(String url, {String? authHeader}) {
    return _request<String>(
      url,
      authHeader: authHeader,
      responseType: ResponseType.plain,
      convert: (data) => data?.toString() ?? '',
    );
  }

  Future<NetworkResult<T>> _request<T>(
    String url, {
    required ResponseType responseType,
    required T Function(dynamic data) convert,
    String method = 'GET',
    Object? body,
    String? authHeader,
  }) async {
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
          headers: {
            'Authorization': ?authHeader,
            if (body != null) 'Content-Type': 'application/json',
          },
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
