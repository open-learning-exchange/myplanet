import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/team_mapper.dart';
import 'package:myplanet/repository/teams_repository.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late TeamsRepository repository;
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    repository = TeamsRepository(api, database.teamDao);
  });
  tearDown(() => database.close());

  test('catalog separates types and derives membership data', () async {
    await database.teamDao.upsertAll([
      TeamMapper.fromDoc({'_id': 'team', 'name': 'Active', 'type': 'team'})!,
      TeamMapper.fromDoc({'_id': 'old', 'type': 'team', 'status': 'archived'})!,
      TeamMapper.fromDoc({'_id': 'enterprise', 'type': 'enterprise'})!,
      TeamMapper.fromDoc({
        '_id': 'membership',
        'teamId': 'team',
        'userId': 'u',
        'docType': 'membership',
      })!,
    ]);
    expect((await repository.watchCatalog().first).map((row) => row.id), [
      'team',
    ]);
    expect(
      (await repository.watchCatalog(type: 'enterprise').first).single.id,
      'enterprise',
    );
    expect(
      (await repository.watchMemberships('u').first).single.teamId,
      'team',
    );
    expect(await repository.watchMemberCount('team').first, 1);
  });

  test(
    'creates, totals, edits, serializes, and archives financial reports',
    () async {
      final local = TeamsRepository(
        api,
        database.teamDao,
        createId: () => 'report-1',
      );
      final report = await local.saveReport(
        teamId: 'enterprise',
        description: 'Month',
        startDate: 1,
        endDate: 2,
        beginningBalance: 100,
        sales: 50,
        otherIncome: 10,
        wages: 20,
        otherExpenses: 5,
      );
      expect(report?.profitLoss, 35);
      expect(report?.endingBalance, 135);
      expect(TeamsRepository.serializeTeamDocument(report!)['sales'], 50);
      expect(
        (await local.watchReports('enterprise').first).single.id,
        'report-1',
      );
      final edited = await local.saveReport(
        id: 'report-1',
        teamId: 'enterprise',
        description: 'Edited',
        startDate: 1,
        endDate: 3,
        beginningBalance: 100,
        sales: 75,
        otherIncome: 10,
        wages: 20,
        otherExpenses: 5,
      );
      expect(edited?.description, 'Edited');
      await local.archiveReport('report-1');
      expect(await local.watchReports('enterprise').first, isEmpty);
      expect(
        await local.saveReport(
          teamId: 'enterprise',
          description: '',
          startDate: 5,
          endDate: 2,
          beginningBalance: 0,
          sales: 0,
          otherIncome: 0,
          wages: 0,
          otherExpenses: 0,
        ),
        isNull,
      );
    },
  );

  test('adds, deduplicates, serializes, and removes team courses', () async {
    await database.teamDao.upsert(
      TeamMapper.fromDoc({
        '_id': 'team',
        'type': 'team',
        'name': 'A',
        'courses': ['one'],
      })!,
    );
    final updated = await repository.addCourses('team', ['one', 'two', '']);
    expect(updated?.courses, ['one', 'two']);
    expect(TeamsRepository.serializeTeamDocument(updated!)['courses'], [
      'one',
      'two',
    ]);
    expect((await repository.removeCourse('team', 'one'))?.courses, ['two']);
    expect(await repository.addCourses('missing', ['x']), isNull);
  });

  test('adds, deduplicates, serializes, and removes resource links', () async {
    final local = TeamsRepository(
      api,
      database.teamDao,
      createId: () => 'link-1',
    );
    final link = await local.addResourceLink(
      teamId: 'team',
      resourceId: 'resource',
      title: 'Book',
    );
    expect(link?.docType, 'resourceLink');
    expect(
      TeamsRepository.serializeTeamDocument(link!)['resourceId'],
      'resource',
    );
    expect((await local.watchResourceLinks('team').first), hasLength(1));
    expect(
      (await local.addResourceLink(
        teamId: 'team',
        resourceId: 'resource',
        title: 'Duplicate',
      ))?.id,
      'link-1',
    );
    expect((await local.watchResourceLinks('team').first), hasLength(1));
    expect((await local.removeResourceLink('team', 'resource'))?.id, 'link-1');
    expect(await local.watchResourceLinks('team').first, isEmpty);
  });

  test('join, accept, and leave update membership documents offline', () async {
    final local = TeamsRepository(
      api,
      database.teamDao,
      createId: () => 'request-1',
    );
    final request = await local.createJoinRequest(
      teamId: 'team',
      userId: 'user',
      teamType: 'local',
    );
    expect(request?.docType, 'request');
    expect(TeamsRepository.serializeTeamDocument(request!)['teamId'], 'team');

    final membership = await local.respondToRequest('request-1', accept: true);
    expect(membership?.docType, 'membership');
    expect((await local.watchMembers('team').first).single.userId, 'user');

    final removed = await local.leave('team', 'user');
    expect(removed?.id, 'request-1');
    expect(await local.watchMemberCount('team').first, 0);
  });

  test('declining a request removes it from the pending queue', () async {
    final local = TeamsRepository(
      api,
      database.teamDao,
      createId: () => 'request-1',
    );
    await local.createJoinRequest(teamId: 'team', userId: 'user');
    expect(await local.respondToRequest('request-1', accept: false), isNotNull);
    expect(await local.watchRequests('team').first, isEmpty);
  });

  test(
    'sync authenticates, reports progress, and removes stale rows',
    () async {
      await database.teamDao.upsertAll([
        TeamMapper.fromDoc({'_id': 'stale', 'type': 'team'})!,
      ]);
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((invocation) async {
        final url = invocation.positionalArguments.single as String;
        if (url.endsWith('limit=0')) return NetworkSuccess({'total_rows': 2});
        return NetworkSuccess({
          'rows': [
            {
              'doc': {'_id': 'one', 'name': 'One', 'type': 'team'},
            },
            {
              'doc': {'_id': 'two', 'name': 'Two', 'type': 'team'},
            },
          ],
        });
      });
      final progress = <SyncProgress>[];
      final result = await repository.sync(
        config: config,
        onProgress: progress.add,
      );
      expect(result, isA<SyncComplete>());
      expect((result as SyncComplete).savedCount, 2);
      expect((await repository.watchCatalog().first).map((row) => row.id), [
        'one',
        'two',
      ]);
      expect(progress.single.completed, 2);
      expect(progress.single.total, 2);
      verify(
        () =>
            api.getJsonObject(any(), authHeader: 'Basic c2F0ZWxsaXRlOjEyMzQ='),
      ).called(2);
    },
  );

  test(
    'a failed later page retains old rows and skips stale cleanup',
    () async {
      await database.teamDao.upsertAll([
        TeamMapper.fromDoc({'_id': 'stale', 'type': 'team'})!,
      ]);
      var page = 0;
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((invocation) async {
        final url = invocation.positionalArguments.single as String;
        if (url.endsWith('limit=0')) return NetworkSuccess({'total_rows': 101});
        if (++page == 2) {
          return const NetworkException<Map<String, dynamic>>('offline');
        }
        return NetworkSuccess({
          'rows': List.generate(
            100,
            (i) => {
              'doc': {'_id': 'team-$i', 'type': 'team'},
            },
          ),
        });
      });
      expect(await repository.sync(config: config), isA<SyncFailed>());
      expect(await repository.getById('stale'), isNotNull);
      expect(await repository.watchCatalog().first, hasLength(101));
    },
  );
}

class MockPlanetApi extends Mock implements PlanetApi {}
