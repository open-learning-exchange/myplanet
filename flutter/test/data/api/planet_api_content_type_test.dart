import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';

/// The first transport-level tests for `PlanetApi`, and they exist because the
/// absence of them let the port ship unable to configure against any real
/// Planet server.
///
/// Every other fixture in this suite stubs a `NetworkSuccess<Map>` straight
/// into a repository, so nothing ever exercised Dio's decode. Planet serves
/// `/versions` as `text/plain` with a JSON body; Dio's `ResponseType.json`
/// only decodes when the `Content-Type` announces JSON, so the body arrived as
/// a `String`, `getJsonObject` threw `Expected a JSON object`, and the screen
/// reported "device couldn't reach the server" — on the very first request the
/// app makes. Retrofit's `GsonConverterFactory` never consults `Content-Type`,
/// which is why the Kotlin app works.
class _FakeAdapter implements HttpClientAdapter {
  _FakeAdapter({
    required this.body,
    required this.contentType,
    this.status = 200,
  });

  final String body;
  final String contentType;
  final int status;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    return ResponseBody.fromString(
      body,
      status,
      headers: <String, List<String>>{
        Headers.contentTypeHeader: <String>[contentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

PlanetApi _apiReturning({
  required String body,
  required String contentType,
  int status = 200,
}) {
  final dio = Dio(BaseOptions(validateStatus: (_) => true))
    ..httpClientAdapter = _FakeAdapter(
      body: body,
      contentType: contentType,
      status: status,
    );
  return PlanetApi(dio);
}

/// The real body `https://planet.learning.ole.org/versions` returns.
const _versionsBody =
    '{"appname":"planet","planetVersion":"0.24.49",'
    '"latestapk":"v0.69.36","latestapkcode":6936,'
    '"minapk":"v0.65.55","minapkcode":6555}';

void main() {
  group('getConfiguration tolerates a mislabelled Content-Type', () {
    test(
      'reads a JSON object served as text/plain, as Planet serves it',
      () async {
        final api = _apiReturning(
          body: _versionsBody,
          contentType: 'text/plain',
        );

        final result = await api.getConfiguration(
          'https://planet.example.org/versions',
        );

        expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
        final data = (result as NetworkSuccess<Map<String, dynamic>>).data;
        expect(data['minapk'], 'v0.65.55');
        expect(data['planetVersion'], '0.24.49');
      },
    );

    test('still reads a correctly labelled application/json body', () async {
      final api = _apiReturning(
        body: _versionsBody,
        contentType: 'application/json',
      );

      final result = await api.getConfiguration(
        'https://planet.example.org/versions',
      );

      expect(
        (result as NetworkSuccess<Map<String, dynamic>>).data['minapk'],
        'v0.65.55',
      );
    });

    test('a CouchDB document served as text/plain reads too', () async {
      // The same fix covers every JSON call, not just /versions.
      final api = _apiReturning(
        body: '{"rows":[{"id":"cfg","doc":{"code":"gt"}}]}',
        contentType: 'text/plain; charset=utf-8',
      );

      final result = await api.getConfiguration(
        'https://planet.example.org/db/x',
      );

      expect(
        (result as NetworkSuccess<Map<String, dynamic>>).data['rows'],
        hasLength(1),
      );
    });

    test(
      'a captive portal HTML page is still a failure, not a false success',
      () async {
        // Tolerating a string body must not turn any non-JSON response into a
        // success — the other real cause of this symptom is a wifi portal.
        final api = _apiReturning(
          body: '<html><body>Sign in to continue</body></html>',
          contentType: 'text/html',
        );

        final result = await api.getConfiguration(
          'https://planet.example.org/versions',
        );

        expect(result, isA<NetworkException<Map<String, dynamic>>>());
        expect(
          (result as NetworkException<Map<String, dynamic>>).error,
          isA<FormatException>(),
        );
      },
    );

    test(
      'a JSON array where an object is required is still a failure',
      () async {
        final api = _apiReturning(body: '[1,2,3]', contentType: 'text/plain');

        expect(
          await api.getConfiguration('https://planet.example.org/versions'),
          isA<NetworkException<Map<String, dynamic>>>(),
        );
      },
    );

    test('an empty body is a failure rather than an empty map', () async {
      final api = _apiReturning(body: '', contentType: 'text/plain');

      expect(
        await api.getConfiguration('https://planet.example.org/versions'),
        isA<NetworkException<Map<String, dynamic>>>(),
      );
    });

    test('a non-2xx still reports its status, not a parse failure', () async {
      final api = _apiReturning(
        body: 'nope',
        contentType: 'text/plain',
        status: 401,
      );

      final result = await api.getConfiguration(
        'https://planet.example.org/db/x',
      );

      expect(result, isA<NetworkError<Map<String, dynamic>>>());
      expect((result as NetworkError<Map<String, dynamic>>).code, 401);
    });
  });
}
