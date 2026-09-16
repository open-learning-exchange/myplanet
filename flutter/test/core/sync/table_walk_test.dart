import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/core/sync/table_walk.dart';

import '../../support/mock_planet_api.dart';

/// The pagination shared by the five Phase 119 walks.
///
/// The per-walk tests all stub one page that answers every `skip`, which is
/// enough to exercise a writer and no part at all of the loop that feeds it —
/// the second audit's largest coverage finding. These drive the loop itself:
/// change `skip += rows.length` to `skip += 1` and the first test hangs on
/// duplicate ids rather than passing.
void main() {
  late MockPlanetApi api;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() => api = MockPlanetApi());

  void stubCount(Map<String, dynamic> body) {
    when(
      () => api.getJsonObject(
        '$dbUrl/widgets/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((_) async => NetworkSuccess<Map<String, dynamic>>(body));
  }

  /// Serves `total` documents out of a real corpus, honouring `limit`/`skip`.
  void stubCorpus(int total, {Set<int> failAtSkip = const {}}) {
    stubCount({'total_rows': total});
    when(
      () => api.getJsonObject(
        any(that: contains('widgets/_all_docs?include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments[0] as String;
      final query = Uri.parse(url).queryParameters;
      final limit = int.parse(query['limit']!);
      final skip = int.parse(query['skip']!);
      if (failAtSkip.contains(skip)) {
        return const NetworkError<Map<String, dynamic>>(503, 'upstream down');
      }
      final end = (skip + limit) > total ? total : skip + limit;
      return NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          for (var i = skip; i < end; i++)
            {
              'id': 'doc-$i',
              'doc': {'_id': 'doc-$i', '_rev': '1-x'},
            },
        ],
      });
    });
  }

  test('walks every page exactly once', () async {
    stubCorpus(250);
    final seen = <String>[];

    final result = await walkAllDocs(
      api: api,
      config: config,
      table: 'widgets',
      initialBatchSize: 100,
      insert: (docs) async {
        seen.addAll(docs.map((d) => d['_id'] as String));
        return docs.length;
      },
    );

    expect(result, isA<SyncComplete>());
    expect((result as SyncComplete).savedCount, 250);
    expect(seen.length, 250);
    expect(seen.toSet().length, 250);
  });

  test('reports progress that ends at the total', () async {
    stubCorpus(250);
    final progress = <int>[];

    await walkAllDocs(
      api: api,
      config: config,
      table: 'widgets',
      initialBatchSize: 100,
      insert: (docs) async => docs.length,
      onProgress: (p) => progress.add(p.completed),
    );

    expect(progress.last, 250);
    expect(progress.every((c) => c <= 250), isTrue);
  });

  test(
    'a mid-walk failure fails the area rather than half-reporting',
    () async {
      stubCorpus(250, failAtSkip: {100});
      var inserted = 0;

      final result = await walkAllDocs(
        api: api,
        config: config,
        table: 'widgets',
        initialBatchSize: 100,
        insert: (docs) async {
          inserted += docs.length;
          return docs.length;
        },
      );

      // The first page is kept — nothing prunes, so a partial pull is a partial
      // cache, not a loss — but the area reports failed so the user can retry.
      expect(result, isA<SyncFailed>());
      expect(inserted, 100);
    },
  );

  test('_design documents are dropped without stalling the loop', () async {
    // `total_rows` counts design documents, so a page that is entirely design
    // docs still has to advance `skip` by the raw row count.
    stubCount({'total_rows': 3});
    when(
      () => api.getJsonObject(
        any(that: contains('widgets/_all_docs?include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((invocation) async {
      final skip = int.parse(
        Uri.parse(
          invocation.positionalArguments[0] as String,
        ).queryParameters['skip']!,
      );
      if (skip > 0) {
        return const NetworkSuccess<Map<String, dynamic>>({'rows': []});
      }
      return const NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'id': '_design/widgets',
            'doc': {'_id': '_design/widgets'},
          },
          {
            'id': 'doc-0',
            'doc': {'_id': 'doc-0'},
          },
          {'key': 'missing', 'error': 'not_found'},
        ],
      });
    });

    var seen = <String>[];
    final result = await walkAllDocs(
      api: api,
      config: config,
      table: 'widgets',
      initialBatchSize: 3,
      insert: (docs) async {
        seen = docs.map((d) => d['_id'] as String).toList();
        return docs.length;
      },
    );

    expect(seen, ['doc-0']);
    expect((result as SyncComplete).savedCount, 1);
  });

  test('an empty database is a success with no page request', () async {
    stubCount({'total_rows': 0});

    final result = await walkAllDocs(
      api: api,
      config: config,
      table: 'widgets',
      initialBatchSize: 100,
      insert: (docs) async => docs.length,
    );

    expect((result as SyncComplete).savedCount, 0);
    verifyNever(
      () => api.getJsonObject(
        any(that: contains('include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    );
  });

  test(
    'a count response with no total_rows fails rather than ticking',
    () async {
      // `JsonUtils.getInt` reads a missing key and a real zero alike, so without
      // the containsKey guard a malformed count response put a green tick on a
      // walk that never issued a page request.
      stubCount({'offset': 0});

      final result = await walkAllDocs(
        api: api,
        config: config,
        table: 'widgets',
        initialBatchSize: 100,
        insert: (docs) async => docs.length,
      );

      expect(result, isA<SyncFailed>());
    },
  );
}
