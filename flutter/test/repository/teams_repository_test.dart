import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/team_attachments.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/team_mapper.dart';
import 'package:myplanet/repository/teams_repository.dart';
import 'package:path_provider/path_provider.dart';

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

  // Port of `MyTeamTest.testSerializeStripsNulls` (commit 756cf75ce). A team
  // document uploaded to CouchDB must never carry null-valued keys — the
  // Kotlin strips them in `MyTeam.serialize`, and `serializeTeamDocument`
  // does the same via its `if (row.X != null)` entries. Guards the four
  // nullable columns (`teamId`/`userId`/`docType`/`teamType`) that were once
  // added unconditionally and would have sent `"teamId": null` upstream.
  test('serializeTeamDocument strips null-valued keys', () async {
    await database.teamDao.upsert(
      TeamMapper.fromDoc({'_id': 'nulls', '_rev': 'r1', 'name': 'Named'})!,
    );
    final row = (await database.teamDao.getById('nulls'))!;
    final doc = TeamsRepository.serializeTeamDocument(row);
    expect(doc['_id'], 'nulls');
    expect(doc['_rev'], 'r1');
    expect(doc['name'], 'Named');
    expect(doc.containsKey('teamId'), isFalse);
    expect(doc.containsKey('userId'), isFalse);
    expect(doc.containsKey('docType'), isFalse);
    expect(doc.containsKey('teamType'), isFalse);
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

  /// The `teams` database is not a pure cache: the user authors documents into
  /// it offline, and those ids never appear in a server page.
  group('stale cleanup spares local work', () {
    Future<void> stubServerReturning(List<Map<String, dynamic>> docs) async {
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((invocation) async {
        final url = invocation.positionalArguments.single as String;
        if (url.endsWith('limit=0')) {
          return NetworkSuccess<Map<String, dynamic>>({
            'total_rows': docs.length,
          });
        }
        return NetworkSuccess<Map<String, dynamic>>({
          'rows': [
            for (final doc in docs) {'doc': doc},
          ],
        });
      });
    }

    test('a financial report written offline outlives a sync', () async {
      await database.teamDao.upsertAll([
        TeamMapper.fromDoc({'_id': 'team-1', 'type': 'team'})!,
      ]);
      final report = await repository.saveReport(
        teamId: 'team-1',
        description: 'Q1',
        startDate: 1,
        endDate: 2,
        beginningBalance: 100,
        sales: 50,
        otherIncome: 0,
        wages: 20,
        otherExpenses: 0,
      );
      expect(report, isNotNull);

      await stubServerReturning([
        {'_id': 'team-1', 'type': 'team'},
      ]);
      expect(await repository.sync(config: config), isA<SyncComplete>());

      // The report has a device-generated id, so it is "not in" every synced
      // page by construction — and nothing uploads it, so a delete is final.
      expect(
        (await repository.watchReports('team-1').first).single.id,
        report!.id,
      );
    });

    test('an offline join request and its membership outlive a sync', () async {
      await repository.createJoinRequest(teamId: 'team-1', userId: 'user-1');
      final accepted = await repository.respondToRequest(
        (await repository.request('team-1', 'user-1'))!.id,
        accept: true,
      );
      expect(accepted?.docType, 'membership');

      await stubServerReturning([
        {'_id': 'team-1', 'type': 'team'},
      ]);
      await repository.sync(config: config);

      expect(await repository.membership('team-1', 'user-1'), isNotNull);
    });

    test('a resource link added offline outlives a sync', () async {
      await repository.addResourceLink(
        teamId: 'team-1',
        resourceId: 'res-1',
        title: 'Handbook',
      );

      await stubServerReturning([
        {'_id': 'team-1', 'type': 'team'},
      ]);
      await repository.sync(config: config);

      expect(
        (await repository.watchResourceLinks('team-1').first).single.resourceId,
        'res-1',
      );
    });

    test('a server row the sync no longer returns is still evicted', () async {
      await database.teamDao.upsertAll([
        TeamMapper.fromDoc({'_id': 'gone', 'type': 'team'})!,
        TeamMapper.fromDoc({'_id': 'kept', 'type': 'team'})!,
      ]);

      await stubServerReturning([
        {'_id': 'kept', 'type': 'team'},
      ]);
      await repository.sync(config: config);

      expect((await repository.watchCatalog().first).map((row) => row.id), [
        'kept',
      ]);
    });

    test('an empty server does not wipe local documents', () async {
      await database.teamDao.upsertAll([
        TeamMapper.fromDoc({'_id': 'cached', 'type': 'team'})!,
      ]);
      await repository.createJoinRequest(teamId: 'team-1', userId: 'user-1');
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({'total_rows': 0}),
      );

      expect(await repository.sync(config: config), isA<SyncComplete>());

      expect(await repository.getById('cached'), isNull);
      expect(await repository.request('team-1', 'user-1'), isNotNull);
    });
  });

  test('cleanup chunks past SQLite\'s variable limit', () async {
    await database.teamDao.upsertAll([
      for (var i = 0; i < 1200; i++)
        TeamMapper.fromDoc({'_id': 'team-$i', 'type': 'team'})!,
    ]);

    // A `NOT IN` over 1200 ids would exceed SQLITE_MAX_VARIABLE_NUMBER, and it
    // cannot simply be chunked: each chunk matches the rows the others keep.
    final deleted = await database.teamDao.deleteNotIn(['team-7']);

    expect(deleted, 1199);
    expect((await repository.watchCatalog().first).single.id, 'team-7');
  });

  test('a refresh does not discard an edit made offline', () async {
    await database.teamDao.upsertAll([
      TeamMapper.fromDoc({'_id': 'team-1', 'type': 'team', 'name': 'Old'})!,
    ]);
    await repository.addCourses('team-1', ['course-1']);

    final merged = TeamMapper.fromDoc({
      '_id': 'team-1',
      'type': 'team',
      'name': 'Server name',
      '_rev': '2-b',
    }, existing: await repository.getById('team-1'));
    await database.teamDao.upsertAll([merged!]);

    final row = await repository.getById('team-1');
    expect(row?.courses, [
      'course-1',
    ], reason: 'the local edit is authoritative');
    // The revision still advances, so the eventual upload is not stale.
    expect(row?.rev, '2-b');
  });

  test('getById does not resolve a team id to one of its documents', () async {
    await database.teamDao.upsertAll([
      TeamMapper.fromDoc({
        '_id': 'membership-1',
        'teamId': 'team-1',
        'userId': 'u',
        'docType': 'membership',
      })!,
      TeamMapper.fromDoc({'_id': 'team-1', 'type': 'team'})!,
    ]);

    // Matching `teamId` too made this ambiguous, and `addCourses` bails on any
    // row with a `docType` — so adding a course intermittently did nothing.
    expect((await repository.getById('team-1'))?.docType, isNull);
    expect((await repository.addCourses('team-1', ['course-1']))?.courses, [
      'course-1',
    ]);
  });

  test('a transaction with a receipt stores the name and the bytes', () async {
    // Port of `TeamsRepositoryImpl.createTransaction` + `attachTeamImage`: the
    // receipt's name lands on the row and the bytes land at the attachment
    // slot the uploader and preview share. Routing the file store at a temp
    // dir keeps this off the platform channel.
    final tmp = await Directory.systemTemp.createTemp('team_tx_test');
    TeamAttachments.baseDirectory = () async => tmp;
    try {
      final local = TeamsRepository(
        api,
        database.teamDao,
        createId: () => 'tx-1',
      );
      final row = await local.createTransaction(
        teamId: 'team-1',
        type: 'credit',
        note: 'sale',
        amount: 100,
        date: 5,
        imageName: 'receipt.png',
        imageBytes: [10, 20, 30],
      );

      expect(row?.id, 'tx-1');
      expect(row?.imageName, 'receipt.png');
      expect(row?.isUpdated, isTrue);
      expect(
        TeamsRepository.serializeTeamDocument(row!)['imageName'],
        'receipt.png',
      );
      final file = await TeamAttachments.existingFileFor(
        docId: 'tx-1',
        filename: 'receipt.png',
      );
      expect(file, isNotNull);
      expect(await file!.readAsBytes(), [10, 20, 30]);
    } finally {
      TeamAttachments.baseDirectory = getApplicationDocumentsDirectory;
      if (await tmp.exists()) await tmp.delete(recursive: true);
    }
  });

  test('a transaction without a receipt stores no attachment name', () async {
    final row = await repository.createTransaction(
      teamId: 'team-1',
      type: 'debit',
      note: 'cash',
      amount: 50,
      date: 5,
    );

    expect(row?.imageName, isNull);
    expect(row?.isUpdated, isTrue);
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
