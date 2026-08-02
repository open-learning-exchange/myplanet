import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/resources_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late ResourcesRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = ResourcesRepository(api, db.myLibraryDao);
  });

  tearDown(() => db.close());

  Map<String, dynamic> row(String id, String title) => {
    'id': id,
    'doc': {'_id': id, 'title': title},
  };

  void stubCount(int totalRows) {
    when(
      () => api.getJsonObject(
        '$dbUrl/resources/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': totalRows}),
    );
  }

  void stubPage(int skip, int limit, List<Map<String, dynamic>> rows) {
    when(
      () => api.getJsonObject(
        '$dbUrl/resources/_all_docs?include_docs=true&limit=$limit&skip=$skip',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rows': rows}),
    );
  }

  group('sync', () {
    test('pulls a single page and stores the resources', () async {
      stubCount(2);
      stubPage(0, 100, [row('res-1', 'First'), row('res-2', 'Second')]);

      final result = await repository.sync(config: config);

      expect(result, isA<SyncComplete>());
      expect((result as SyncComplete).savedCount, 2);
      expect(await repository.localCount(), 2);
    });

    test('walks multiple pages until the total is reached', () async {
      stubCount(150);
      stubPage(0, 100, List.generate(100, (i) => row('res-$i', 'Title $i')));
      stubPage(
        100,
        100,
        List.generate(50, (i) => row('res-${100 + i}', 'Title')),
      );

      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 150);
      expect(await repository.localCount(), 150);
    });

    test('reports progress as pages land', () async {
      stubCount(150);
      stubPage(0, 100, List.generate(100, (i) => row('res-$i', 'Title')));
      stubPage(
        100,
        100,
        List.generate(50, (i) => row('res-${100 + i}', 'Title')),
      );

      final progress = <int>[];
      await repository.sync(
        config: config,
        onProgress: (p) => progress.add(p.completed),
      );

      expect(progress, [100, 150]);
    });

    test('drops local rows the server no longer lists', () async {
      stubCount(2);
      stubPage(0, 100, [row('res-1', 'First'), row('res-2', 'Second')]);
      await repository.sync(config: config);
      expect(await repository.localCount(), 2);

      // Second sync: the server now only knows about res-1.
      stubCount(1);
      stubPage(0, 100, [row('res-1', 'First')]);
      await repository.sync(config: config);

      expect(await repository.localCount(), 1);
      final remaining = await db.myLibraryDao.getByIds(['res-1', 'res-2']);
      expect(remaining.map((r) => r.id), ['res-1']);
    });

    test('clears the table when the server reports no resources', () async {
      stubCount(1);
      stubPage(0, 100, [row('res-1', 'First')]);
      await repository.sync(config: config);

      stubCount(0);
      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 0);
      expect(await repository.localCount(), 0);
    });

    test('skips design documents and malformed rows', () async {
      stubCount(3);
      stubPage(0, 100, [
        row('res-1', 'Real'),
        {
          'id': '_design/resources',
          'doc': {'_id': '_design/resources'},
        },
        {'id': 'no-doc'},
      ]);

      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 1);
      expect(await repository.localCount(), 1);
    });

    test(
      'fails without touching the database when the count call fails',
      () async {
        when(
          () => api.getJsonObject(
            '$dbUrl/resources/_all_docs?limit=0',
            authHeader: any(named: 'authHeader'),
          ),
        ).thenAnswer(
          (_) async =>
              const NetworkError<Map<String, dynamic>>(401, 'Unauthorized'),
        );

        final result = await repository.sync(config: config);

        expect(result, isA<SyncFailed>());
        expect((result as SyncFailed).message, contains('401'));
        expect(await repository.localCount(), 0);
      },
    );

    test('fails when a page request fails mid-walk', () async {
      stubCount(150);
      stubPage(0, 100, List.generate(100, (i) => row('res-$i', 'Title')));
      when(
        () => api.getJsonObject(
          '$dbUrl/resources/_all_docs?include_docs=true&limit=100&skip=100',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async =>
            NetworkException<Map<String, dynamic>>(Exception('dropped')),
      );

      final result = await repository.sync(config: config);

      expect(result, isA<SyncFailed>());
      // The first page is still persisted — a partial sync is not rolled back.
      expect(await repository.localCount(), 100);
    });

    test('authenticates as the satellite account', () async {
      stubCount(0);
      await repository.sync(config: config);

      final header =
          verify(
                () => api.getJsonObject(
                  any(),
                  authHeader: captureAny(named: 'authHeader'),
                ),
              ).captured.first
              as String;
      expect(header, startsWith('Basic '));
    });
  });

  group('watchResources', () {
    test('emits an updated list when a sync writes new rows', () async {
      stubCount(1);
      stubPage(0, 100, [row('res-1', 'Álgebra')]);

      final emissions = <int>[];
      final subscription = repository.watchResources().listen(
        (rows) => emissions.add(rows.length),
      );

      await pumpEventQueue();
      await repository.sync(config: config);
      await pumpEventQueue();

      expect(emissions.first, 0);
      expect(emissions.last, 1);
      await subscription.cancel();
    });

    test('filters on the diacritic-folded title', () async {
      stubCount(2);
      stubPage(0, 100, [row('res-1', 'Álgebra'), row('res-2', 'Biology')]);
      await repository.sync(config: config);

      // Search without the accent still matches.
      final matches = await repository.watchResources(query: 'algebra').first;
      expect(matches.map((r) => r.title), ['Álgebra']);

      final none = await repository.watchResources(query: 'chemistry').first;
      expect(none, isEmpty);
    });

    test('returns everything for a blank query', () async {
      stubCount(2);
      stubPage(0, 100, [row('res-1', 'A'), row('res-2', 'B')]);
      await repository.sync(config: config);

      expect((await repository.watchResources(query: '  ').first).length, 2);
    });
  });
}
