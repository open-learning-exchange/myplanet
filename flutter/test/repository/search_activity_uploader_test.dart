import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/search_activity_repository.dart';
import 'package:myplanet/repository/search_activity_uploader.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

const config = ServerConfig(
  serverUrl: 'https://planet.example',
  couchDbUrl: 'https://satellite:1234@planet.example:443',
  pin: '1234',
);

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late SearchActivityRepository repository;
  late OutboxRepository outbox;
  late SearchActivityUploader uploader;

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    repository = SearchActivityRepository(database.searchActivityDao);
    outbox = OutboxRepository(database.outboxDao);
    uploader = SearchActivityUploader(
      api,
      repository,
      database.searchActivityDao,
      outbox,
      testDeviceIdentity,
    );
    registerFallbackValue(<String, dynamic>{});
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: SearchActivityUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: 'https://planet.example/db/search_activities',
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  test('the endpoint is credential-free and points at search_activities', () {
    expect(
      SearchActivityUploader.endpointFor(config),
      'https://planet.example/db/search_activities',
    );
  });

  test(
    'queuePending enqueues every un-uploaded search with device identity',
    () async {
      await repository.saveCourseSearch(
        searchText: 'math',
        userName: 'ada',
        planetCode: 'earth',
        parentCode: 'sol',
        grade: 'Beginner',
        subject: 'Math',
      );
      // An already-uploaded row is not re-queued.
      await repository.saveResourceSearch(
        userName: 'ada',
        searchText: 'water',
        planetCode: 'earth',
        parentCode: 'sol',
      );
      final pending = await repository.pendingUploads();
      await database.searchActivityDao.markUploaded(
        pending.last.id,
        'couch-1',
        '1-a',
      );

      final queued = await uploader.queuePending(config: config, userId: 'u-1');

      expect(queued, 1);
      final due = await database.outboxDao.due(
        DateTime.now().millisecondsSinceEpoch + 1000,
      );
      final entry = due.single;
      expect(entry.uploadType, SearchActivityUploader.type);
      expect(entry.endpoint, 'https://planet.example/db/search_activities');
      expect(entry.userId, 'u-1');
      final payload = jsonDecode(entry.payload) as Map<String, dynamic>;
      expect(payload['text'], 'math');
      expect(payload['type'], 'courses');
      expect(payload['user'], 'ada');
      expect(payload['createdOn'], 'earth');
      expect(payload['parentCode'], 'sol');
      expect(payload['androidId'], 'android-id_build-id');
      expect(payload['deviceName'], 'TEST DEVICE');
      expect(payload['customDeviceName'], 'classroom tablet');
      final filter = payload['filter'] as Map<String, dynamic>;
      expect(filter['doc.gradeLevel'], 'Beginner');
      expect(filter['doc.subjectLevel'], 'Math');
    },
  );

  test(
    'handler POSTs and marks the row uploaded with the returned id/rev',
    () async {
      await repository.saveCourseSearch(
        searchText: 'math',
        userName: 'ada',
        planetCode: 'earth',
        parentCode: 'sol',
      );
      final id = (await repository.pendingUploads()).single.id;
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          'id': 'couch-1',
          'rev': '1-a',
          'ok': true,
        }),
      );

      final result = await uploader.handler(rowFor(id), {
        'text': 'math',
      }, 'auth');

      expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
      final rows = await repository.pendingUploads();
      expect(rows, isEmpty);
    },
  );

  test(
    'handler does not mark uploaded when the response lacks id/rev',
    () async {
      await repository.saveCourseSearch(
        searchText: 'math',
        userName: 'ada',
        planetCode: 'earth',
        parentCode: 'sol',
      );
      final id = (await repository.pendingUploads()).single.id;
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
      );

      final result = await uploader.handler(rowFor(id), {
        'text': 'math',
      }, 'auth');

      expect(result, isA<NetworkError<Map<String, dynamic>>>());
      final rows = await repository.pendingUploads();
      expect(rows.length, 1);
    },
  );

  test('serialize reproduces SearchActivity.serialize', () {
    final row = SearchActivityRow(
      id: 'search-1',
      couchId: '',
      rev: '',
      searchText: 'math',
      type: 'resources',
      time: 1000,
      user: 'ada',
      filterJson: '{"subjects":["a"]}',
      createdOn: 'earth',
      parentCode: 'sol',
    );
    final serialized = SearchActivityRepository.serialize(row);
    expect(serialized['text'], 'math');
    expect(serialized['type'], 'resources');
    expect(serialized['time'], 1000);
    expect(serialized['user'], 'ada');
    expect(serialized['createdOn'], 'earth');
    expect(serialized['parentCode'], 'sol');
    final filter = serialized['filter'] as Map<String, dynamic>;
    expect(filter['subjects'], ['a']);
  });

  test('serialize handles an empty filter string', () {
    final row = SearchActivityRow(
      id: 'search-1',
      couchId: '',
      rev: '',
      searchText: '',
      type: 'courses',
      time: 0,
      user: '',
      filterJson: '',
      createdOn: '',
      parentCode: '',
    );
    final serialized = SearchActivityRepository.serialize(row);
    expect(serialized['filter'], isEmpty);
  });

  test('saveResourceSearch serializes the resource filter shape', () async {
    await repository.saveResourceSearch(
      userName: 'ada',
      searchText: 'water',
      planetCode: 'earth',
      parentCode: 'sol',
      subjects: const {'a', 'b'},
      languages: const {'en'},
      levels: const {'1'},
      mediums: const {'video'},
    );
    final rows = await repository.pendingUploads();
    final row = rows.single;
    expect(row.type, 'resources');
    expect(row.searchText, 'water');
    final filter = jsonDecode(row.filterJson) as Map<String, dynamic>;
    expect(filter['subjects'], ['a', 'b']);
    expect(filter['language'], ['en']);
    expect(filter['level'], ['1']);
    expect(filter['mediaType'], ['video']);
    expect(filter['tags'], isEmpty);
  });
}
