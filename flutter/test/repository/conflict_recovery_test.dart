import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/repository/outbox_drainer.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// The 409 arm in isolation: `ConflictRecovery.send`.
///
/// The end-to-end consequences are pinned per uploader; these pin the rule
/// itself, including the two guards that keep it inside Phase 148's policy.
void main() {
  late MockPlanetApi api;

  const docUrl = 'https://planet.example.org/db/health/patient-1';
  const conflict = NetworkError<Map<String, dynamic>>(409, 'conflict');

  setUp(() => api = MockPlanetApi());

  void stubGet(NetworkResult<Map<String, dynamic>> result) {
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((_) async => result);
  }

  /// Records every body handed to the send, so a test can assert both *how
  /// many* requests were made and *what changed* between them.
  ({
    List<Map<String, dynamic>> sent,
    Future<NetworkResult<Map<String, dynamic>>> Function(
      Map<String, dynamic>,
    )
    attempt,
  })
  recorder(List<NetworkResult<Map<String, dynamic>>> answers) {
    final sent = <Map<String, dynamic>>[];
    var index = 0;
    return (
      sent: sent,
      attempt: (body) async {
        sent.add(Map<String, dynamic>.from(body));
        return answers[index++ < answers.length ? index - 1 : answers.length - 1];
      },
    );
  }

  Future<NetworkResult<Map<String, dynamic>>> run(
    ({
      List<Map<String, dynamic>> sent,
      Future<NetworkResult<Map<String, dynamic>>> Function(
        Map<String, dynamic>,
      )
      attempt,
    })
    r, {
    Map<String, dynamic> payload = const {'_id': 'patient-1', 'pulse': 70},
  }) => ConflictRecovery.send(
    api: api,
    documentUrl: docUrl,
    payload: payload,
    attempt: r.attempt,
    authHeader: 'Basic abc',
  );

  test('an accepted send never fetches anything', () async {
    final r = recorder([const NetworkSuccess<Map<String, dynamic>>({'rev': '1-a'})]);

    expect(await run(r), isA<NetworkSuccess<Map<String, dynamic>>>());
    expect(r.sent, hasLength(1));
    verifyNever(() => api.getJsonObject(any(), authHeader: any(named: 'authHeader')));
  });

  test('a refusal that is not a conflict is returned untouched', () async {
    final r = recorder([const NetworkError<Map<String, dynamic>>(400, 'bad')]);

    expect((await run(r) as NetworkError).code, 400);
    expect(r.sent, hasLength(1));
    verifyNever(() => api.getJsonObject(any(), authHeader: any(named: 'authHeader')));
  });

  test('a conflict re-sends the same content under the fetched revision', () async {
    final r = recorder([
      conflict,
      const NetworkSuccess<Map<String, dynamic>>({'id': 'patient-1', 'rev': '8-b'}),
    ]);
    stubGet(const NetworkSuccess<Map<String, dynamic>>({'_rev': '7-a'}));

    final result = await run(r);

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    expect((result as NetworkSuccess).data['rev'], '8-b');
    expect(r.sent, hasLength(2));
    // The whole point: the *content* survives. Kotlin's arm would have marked
    // this uploaded with the server's document still holding somebody else's
    // reading.
    expect(r.sent[1]['pulse'], 70);
    expect(r.sent[1]['_rev'], '7-a', reason: 'the request changed');
    expect(r.sent[0].containsKey('_rev'), isFalse);
  });

  test('the fetch carries the drain credential', () async {
    final r = recorder([conflict, const NetworkSuccess<Map<String, dynamic>>({})]);
    stubGet(const NetworkSuccess<Map<String, dynamic>>({'_rev': '7-a'}));

    await run(r);

    final captured = verify(
      () => api.getJsonObject(captureAny(), authHeader: captureAny(named: 'authHeader')),
    ).captured;
    expect(captured[0], docUrl);
    expect(captured[1], 'Basic abc');
  });

  test('a stale revision already in the payload is not re-sent', () async {
    // The guard that keeps this inside Phase 148's memo: the server reports
    // the revision the request already carried, so there is nothing new to
    // ask and the refusal stands.
    final r = recorder([conflict]);
    stubGet(const NetworkSuccess<Map<String, dynamic>>({'_rev': '7-a'}));

    final result = await run(r, payload: const {'_id': 'x', '_rev': '7-a'});

    expect((result as NetworkError).code, 409);
    expect(r.sent, hasLength(1), reason: 'no identical re-ask');
  });

  test('a failed fetch returns the original conflict, not a verdict of its own', () async {
    final r = recorder([conflict]);
    stubGet(const NetworkError<Map<String, dynamic>>(401, 'unauthorized'));

    expect((await run(r) as NetworkError).code, 409);
    expect(r.sent, hasLength(1));
  });

  test('a fetched document with no revision returns the original conflict', () async {
    final r = recorder([conflict]);
    stubGet(const NetworkSuccess<Map<String, dynamic>>({'_id': 'patient-1'}));

    expect((await run(r) as NetworkError).code, 409);
    expect(r.sent, hasLength(1));
  });

  test('a second conflict is terminal — the arm does not loop', () async {
    // Another writer raced in between. Two sends, no third, and the refusal
    // classifies exactly as it did before this arm existed.
    final r = recorder([conflict, conflict]);
    stubGet(const NetworkSuccess<Map<String, dynamic>>({'_rev': '7-a'}));

    final result = await run(r);

    expect((result as NetworkError).code, 409);
    expect(r.sent, hasLength(2));
  });
}
