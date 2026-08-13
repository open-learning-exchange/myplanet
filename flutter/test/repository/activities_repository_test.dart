import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/activities_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late ActivitiesRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = ActivitiesRepository(api, db.offlineActivityDao);
  });

  tearDown(() => db.close());

  Map<String, dynamic> loginDoc(
    String id, {
    String user = 'ada',
    int loginTime = 1700000000000,
    String rev = '1-a',
  }) {
    return {
      '_id': id,
      '_rev': rev,
      'type': 'login',
      'user': user,
      'loginTime': loginTime,
    };
  }

  Map<String, dynamic> row(Map<String, dynamic> doc) => {
    'id': doc['_id'],
    'doc': doc,
  };

  void stubCount(int totalRows) {
    when(
      () => api.getJsonObject(
        '$dbUrl/login_activities/_all_docs?limit=0',
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
        '$dbUrl/login_activities/_all_docs?include_docs=true&limit=$limit&skip=$skip',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rows': rows}),
    );
  }

  group('sync', () {
    test('stores login documents and counts them as saved', () async {
      stubCount(1);
      stubPage(0, 200, [row(loginDoc('login-1'))]);

      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 1);
      final logins = await repository.loginActivities('ada').first;
      expect(logins, hasLength(1));
      expect(logins.first.couchId, 'login-1');
      expect(logins.first.loginTime, 1700000000000);
    });

    test(
      'returns SyncComplete with zero when the server has no rows',
      () async {
        stubCount(0);

        final result = await repository.sync(config: config);

        expect((result as SyncComplete).savedCount, 0);
      },
    );

    test('returns SyncFailed when the count request errors', () async {
      when(
        () => api.getJsonObject(
          '$dbUrl/login_activities/_all_docs?limit=0',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async =>
            const NetworkError<Map<String, dynamic>>(0, 'connection refused'),
      );

      final result = await repository.sync(config: config);

      expect(result, isA<SyncFailed>());
    });

    test('skips _design documents', () async {
      stubCount(2);
      stubPage(0, 200, [
        row({'_id': '_design/logins', 'type': 'login', 'user': 'ada'}),
        row(loginDoc('login-1')),
      ]);

      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 1);
      final logins = await repository.loginActivities('ada').first;
      expect(logins, hasLength(1));
    });

    test('re-sync merges by _id instead of duplicating', () async {
      stubCount(1);
      stubPage(0, 200, [row(loginDoc('login-1'))]);

      await repository.sync(config: config);
      await repository.sync(config: config);

      final logins = await repository.loginActivities('ada').first;
      expect(logins, hasLength(1));
    });

    test('merges an offline-authored row by loginTime+userName', () async {
      // An offline login row the app authored itself carries no `_id`; a later
      // sync pulls the server document for the same login (same loginTime +
      // userName). The merge must adopt the local row rather than create a twin.
      await db.offlineActivityDao.upsert(
        OfflineActivitiesCompanion.insert(
          id: 'local-1',
          userName: const Value('ada'),
          type: const Value('login'),
          loginTime: const Value(1700000000000),
        ),
      );

      stubCount(1);
      stubPage(0, 200, [row(loginDoc('login-1', loginTime: 1700000000000))]);

      await repository.sync(config: config);

      final logins = await repository.loginActivities('ada').first;
      expect(logins, hasLength(1), reason: 'merge must not duplicate');
      // The adopted row now carries the server id.
      expect(logins.first.couchId, 'login-1');
    });
  });

  group('loginCount', () {
    test('counts only the named user\'s login rows', () async {
      await db.offlineActivityDao.upsertAll([
        OfflineActivitiesCompanion.insert(
          id: 'a1',
          userName: const Value('ada'),
          type: const Value('login'),
          loginTime: const Value(1),
        ),
        OfflineActivitiesCompanion.insert(
          id: 'a2',
          userName: const Value('ada'),
          type: const Value('login'),
          loginTime: const Value(2),
        ),
        OfflineActivitiesCompanion.insert(
          id: 'b1',
          userName: const Value('bea'),
          type: const Value('login'),
          loginTime: const Value(3),
        ),
      ]);

      expect(await repository.loginCount('ada'), 2);
      expect(await repository.loginCount('bea'), 1);
    });

    test('excludes non-login activity types', () async {
      await db.offlineActivityDao.upsert(
        OfflineActivitiesCompanion.insert(
          id: 'a1',
          userName: const Value('ada'),
          type: const Value('resource_view'),
        ),
      );

      expect(await repository.loginCount('ada'), 0);
    });
  });
}
