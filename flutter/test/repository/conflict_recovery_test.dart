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
    Future<NetworkResult<Map<String, dynamic>>> Function(Map<String, dynamic>)
    attempt,
  })
  recorder(List<NetworkResult<Map<String, dynamic>>> answers) {
    final sent = <Map<String, dynamic>>[];
    var index = 0;
    return (
      sent: sent,
      attempt: (body) async {
        sent.add(Map<String, dynamic>.from(body));
        return answers[index++ < answers.length
            ? index - 1
            : answers.length - 1];
      },
    );
  }

  Future<NetworkResult<Map<String, dynamic>>> run(
    ({
      List<Map<String, dynamic>> sent,
      Future<NetworkResult<Map<String, dynamic>>> Function(Map<String, dynamic>)
      attempt,
    })
    r, {
    Map<String, dynamic> payload = const {
      '_id': 'patient-1',
      '_rev': '6-stale',
      'pulse': 70,
    },
    bool adoptExisting = false,
  }) => ConflictRecovery.send(
    api: api,
    documentUrl: docUrl,
    payload: payload,
    attempt: r.attempt,
    authHeader: 'Basic abc',
    adoptExisting: adoptExisting,
  );

  test('an accepted send never fetches anything', () async {
    final r = recorder([
      const NetworkSuccess<Map<String, dynamic>>({'rev': '1-a'}),
    ]);

    expect(await run(r), isA<NetworkSuccess<Map<String, dynamic>>>());
    expect(r.sent, hasLength(1));
    verifyNever(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    );
  });

  test('a refusal that is not a conflict is returned untouched', () async {
    final r = recorder([const NetworkError<Map<String, dynamic>>(400, 'bad')]);

    expect((await run(r) as NetworkError).code, 400);
    expect(r.sent, hasLength(1));
    verifyNever(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    );
  });

  test(
    'a conflict re-sends the same content under the fetched revision',
    () async {
      final r = recorder([
        conflict,
        const NetworkSuccess<Map<String, dynamic>>({
          'id': 'patient-1',
          'rev': '8-b',
        }),
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
      expect(r.sent[0]['_rev'], '6-stale', reason: 'the stale one');
    },
  );

  test('the fetch carries the drain credential', () async {
    final r = recorder([
      conflict,
      const NetworkSuccess<Map<String, dynamic>>({}),
    ]);
    stubGet(const NetworkSuccess<Map<String, dynamic>>({'_rev': '7-a'}));

    await run(r);

    final captured = verify(
      () => api.getJsonObject(
        captureAny(),
        authHeader: captureAny(named: 'authHeader'),
      ),
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

  test(
    'a failed fetch returns the original conflict, not a verdict of its own',
    () async {
      final r = recorder([conflict]);
      stubGet(const NetworkError<Map<String, dynamic>>(401, 'unauthorized'));

      expect((await run(r) as NetworkError).code, 409);
      expect(r.sent, hasLength(1));
    },
  );

  test(
    'a fetched document with no revision returns the original conflict',
    () async {
      final r = recorder([conflict]);
      stubGet(const NetworkSuccess<Map<String, dynamic>>({'_id': 'patient-1'}));

      expect((await run(r) as NetworkError).code, 409);
      expect(r.sent, hasLength(1));
    },
  );

  test('a second conflict is terminal — the arm does not loop', () async {
    // Another writer raced in between. Two sends, no third, and the refusal
    // classifies exactly as it did before this arm existed.
    final r = recorder([conflict, conflict]);
    stubGet(const NetworkSuccess<Map<String, dynamic>>({'_rev': '7-a'}));

    final result = await run(r);

    expect((result as NetworkError).code, 409);
    expect(r.sent, hasLength(2));
  });

  group('a create conflict', () {
    const create = {'_id': 'patient-1', 'pulse': 70};

    test('stands as a refusal by default', () async {
      // No `_rev`, so as far as this device knows the document has never been
      // published and the one on the server is content it has never seen.
      // Re-sending would overwrite that; adopting would report a delivery that
      // did not happen. The refusal stands and the row stays inspectable.
      final r = recorder([conflict]);
      stubGet(const NetworkSuccess<Map<String, dynamic>>({'_rev': '7-a'}));

      final result = await run(r, payload: create);

      expect((result as NetworkError).code, 409);
      expect(r.sent, hasLength(1), reason: 'nothing is overwritten');
    });

    test(
      'is adopted when the uploader can prove the content identical',
      () async {
        // Kotlin's original arm, kept for the one uploader whose document is a
        // pure function of shared inputs. Shaped like the create response the
        // handler expects: a write answers `id`/`rev`, a document read `_id`
        // and `_rev`.
        final r = recorder([conflict]);
        stubGet(
          const NetworkSuccess<Map<String, dynamic>>({
            '_id': 'patient-1',
            '_rev': '7-a',
          }),
        );

        final result = await run(r, payload: create, adoptExisting: true);

        expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
        expect((result as NetworkSuccess).data, {
          'id': 'patient-1',
          'rev': '7-a',
        });
        expect(r.sent, hasLength(1), reason: 'adopted, never re-sent');
      },
    );

    test('adopting falls back to the id the payload named', () async {
      final r = recorder([conflict]);
      stubGet(const NetworkSuccess<Map<String, dynamic>>({'_rev': '7-a'}));

      final result = await run(r, payload: create, adoptExisting: true);

      expect((result as NetworkSuccess).data['id'], 'patient-1');
    });
  });

  test('adoptExisting does not change what an update does', () async {
    // The opt-in is about creates only. An update still re-sends, because
    // adopting would clear the dirty flag with the edit still on the handset.
    final r = recorder([
      conflict,
      const NetworkSuccess<Map<String, dynamic>>({'id': 'x', 'rev': '8-b'}),
    ]);
    stubGet(const NetworkSuccess<Map<String, dynamic>>({'_rev': '7-a'}));

    await run(r, adoptExisting: true);

    expect(r.sent, hasLength(2));
    expect(r.sent[1]['pulse'], 70);
  });

  group('documentUrlUnder', () {
    test('appends the payload id, encoded', () {
      expect(
        ConflictRecovery.documentUrlUnder('https://x/db/health', const {
          '_id': 'user/one two',
        }),
        'https://x/db/health/user%2Fone%20two',
      );
    });

    test('is null when the payload names no document', () {
      expect(
        ConflictRecovery.documentUrlUnder('https://x/db/health', const {}),
        isNull,
      );
      expect(
        ConflictRecovery.documentUrlUnder('https://x/db/health', const {
          '_id': '',
        }),
        isNull,
      );
    });

    test('an append is never recovered', () async {
      // The structural guard against a duplicate: no `_id`, no recovery, and
      // therefore no second send that CouchDB would file under a fresh id.
      final r = recorder([conflict]);
      stubGet(const NetworkSuccess<Map<String, dynamic>>({'_rev': '7-a'}));

      final result = await ConflictRecovery.send(
        api: api,
        documentUrl: ConflictRecovery.documentUrlUnder(
          'https://x/db/personals',
          const {'title': 'A note'},
        ),
        payload: const {'title': 'A note'},
        attempt: r.attempt,
      );

      expect((result as NetworkError).code, 409);
      expect(r.sent, hasLength(1));
      verifyNever(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      );
    });
  });
}
