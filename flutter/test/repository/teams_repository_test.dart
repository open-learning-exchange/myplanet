import 'dart:io';

import 'package:drift/drift.dart' show Value;
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
    repository = TeamsRepository(api, database.teamDao, database.teamLogDao);
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
    // `watchMemberCount` carries Kotlin's `EXISTS (SELECT 1 FROM users …)`
    // (`TeamDao.kt:29`): a membership naming a person this device has never
    // synced is not a member it can show. The row is seeded because the
    // `tablet_users` walk seeds it in the shipping app, not to satisfy the
    // assertion — drop it and the count is 0, which is what the guard is for.
    await database.userDao.upsert(UsersCompanion.insert(id: 'u'));
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
        database.teamLogDao,
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
      database.teamLogDao,
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
      database.teamLogDao,
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
      database.teamLogDao,
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
        database.teamLogDao,
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

  test('exportReportsAsCsv builds a summary with derived totals', () async {
    await repository.saveReport(
      teamId: 'team-1',
      description: 'Q1',
      startDate: 1700000000000,
      endDate: 1702500000000,
      beginningBalance: 1000,
      sales: 500,
      otherIncome: 200,
      wages: 300,
      otherExpenses: 100,
    );
    await repository.saveReport(
      teamId: 'team-1',
      description: 'Q2',
      startDate: 1702500000000,
      endDate: 1705000000000,
      beginningBalance: 1300,
      sales: 600,
      otherIncome: 0,
      wages: 200,
      otherExpenses: 50,
    );

    final reports = await database.teamDao.watchReports('team-1').first;
    final csv = repository.exportReportsAsCsv(reports, 'My Enterprise');

    expect(csv, startsWith('My Enterprise Financial Report Summary\n\n'));
    expect(
      csv,
      contains(
        'Start Date, End Date, Created Date, Updated Date, Beginning Balance,'
        ' Sales, Other Income, Wages, Other Expenses, Profit/Loss,'
        ' Ending Balance',
      ),
    );
    // Q1: totalIncome=700, totalExpenses=400, profitLoss=300, ending=1300
    expect(csv, contains(', 1000, 500, 200, 300, 100, 300, 1300'));
    // Q2: totalIncome=600, totalExpenses=250, profitLoss=350, ending=1650
    expect(csv, contains(', 1300, 600, 0, 200, 50, 350, 1650'));
    // Both report rows are present (data lines start with a weekday abbrev).
    final dataLines = csv
        .split('\n')
        .where((l) => l.contains(', 1') && l.contains('GMT'))
        .toList();
    expect(dataLines.length, 2);
  });

  test('formatDateForCsv renders a US-locale timezone-aware timestamp', () {
    // 2026-08-20 12:00:00 UTC → in UTC this is 12:00 with +0000 offset.
    final utc = DateTime.utc(2026, 8, 20, 12, 0, 0);
    final local = utc.toLocal();
    final formatted = formatDateForCsv(local.millisecondsSinceEpoch);
    // The weekday and month abbreviations are locale-independent English.
    expect(formatted, matches(RegExp(r'^\w{3} \w{3} \d{2} 2026 \d{2}:00:00')));
    expect(formatted, contains('GMT'));
  });

  // ── Team visit logging — port of TeamsRepositoryImpl.logTeamVisit ──────

  group('logTeamVisit', () {
    test('records a teamVisit row with the user and team fields', () async {
      final id = await repository.logTeamVisit(
        teamId: 'team-1',
        userName: 'ada',
        userPlanetCode: 'earth',
        userParentCode: 'sol',
        teamType: 'team',
      );

      expect(id, isNotNull);
      final rows = await repository.pendingTeamLogUploads();
      final row = rows.single;
      expect(row.id, id);
      expect(row.teamId, 'team-1');
      expect(row.user, 'ada');
      expect(row.type, 'teamVisit');
      expect(row.teamType, 'team');
      expect(row.createdOn, 'earth');
      expect(row.parentCode, 'sol');
      expect(row.time, isNotNull);
      expect(row.uploaded, isFalse);
    });

    test('returns null and writes nothing for a blank team id', () async {
      final id = await repository.logTeamVisit(teamId: '', userName: 'ada');

      expect(id, isNull);
      expect(await repository.pendingTeamLogUploads(), isEmpty);
    });

    test('returns null and writes nothing for a blank user name', () async {
      // The Kotlin's `userName.isNullOrBlank()` guard: a whitespace-only
      // name is as absent as a null one.
      final id = await repository.logTeamVisit(
        teamId: 'team-1',
        userName: '   ',
      );

      expect(id, isNull);
      expect(await repository.pendingTeamLogUploads(), isEmpty);
    });

    test('pendingTeamLogUploads excludes rows already uploaded', () async {
      final first = await repository.logTeamVisit(
        teamId: 'team-1',
        userName: 'ada',
      );
      final second = await repository.logTeamVisit(
        teamId: 'team-2',
        userName: 'ada',
      );
      await database.teamLogDao.markUploaded(first!, 'couch-1', '1-a');

      final pending = await repository.pendingTeamLogUploads();
      expect(pending.map((row) => row.id), [second]);
    });
  });

  // ------------------------------------------------------- planet codes (v50)

  group('planet codes', () {
    /// A repository with a deterministic id, so the row can be read back.
    TeamsRepository withId(String id) => TeamsRepository(
      api,
      database.teamDao,
      database.teamLogDao,
      createId: () => id,
    );

    test('the two resource-link producers stamp different fields', () async {
      // **The reason this is one test rather than two.** Kotlin has two
      // producers and the port had one method serving both callers, which is
      // precisely why the defect was invisible: whatever that method stamped
      // was "the" answer. Asserting them side by side is what makes a future
      // edit that merges them fail.
      //
      //   addResourceLinks     (:682-683) -> teamPlanetCode, userPlanetCode
      //   createLocalResourceLink (:716,718) -> sourcePlanet, teamPlanetCode
      final ui = await withId('link-ui').addResourceLink(
        teamId: 'team-1',
        resourceId: 'res-1',
        title: 'Atlas',
        planetCode: 'guatemala',
      );
      expect(ui!.teamPlanetCode, 'guatemala');
      expect(ui.userPlanetCode, 'guatemala');
      expect(
        ui.sourcePlanet,
        null,
        reason:
            'addResourceLinks never sets sourcePlanet (TeamsRepositoryImpl'
            ' has exactly one assignment of it, at :716)',
      );
      expect(
        ui.status,
        null,
        reason:
            'Kotlin writes user.parentCode here (:677) but its resourceLink '
            'serialize branch emits no status key, so the value never leaves '
            'the device — and serializeTeamDocument would send it',
      );

      final upload = await withId('link-upload').createLocalResourceLink(
        teamId: 'team-2',
        resourceId: 'res-2',
        title: 'Atlas',
        planetCode: 'guatemala',
      );
      expect(upload!.sourcePlanet, 'guatemala');
      expect(upload.teamPlanetCode, 'guatemala');
      expect(
        upload.userPlanetCode,
        null,
        reason: 'createLocalResourceLink never sets it (:702-723)',
      );
      expect(upload.parentCode, null, reason: 'neither producer sets it');
    });

    test('a blank planet code lands as absent, not empty', () async {
      // Kotlin's `takeIf { it.isNotBlank() }` before the `?:`. The fallback it
      // guards has no port counterpart (see createLocalResourceLink), but the
      // guard itself is what stops a whitespace-only value being uploaded as a
      // planet code — `serializeTeamDocument` omits null and would send `'  '`.
      final link = await withId('link-blank').createLocalResourceLink(
        teamId: 'team-1',
        resourceId: 'res-1',
        title: 'Atlas',
        planetCode: '   ',
      );

      expect(link!.sourcePlanet, null);
      expect(
        TeamsRepository.serializeTeamDocument(link).containsKey('sourcePlanet'),
        isFalse,
      );
    });

    test('a join request carries both planet codes onto the wire', () async {
      // `requestToJoin:623-624` writes the argument to both columns, and the
      // general serialize branch uploads both (`MyTeam.kt:204`, `:207`). A
      // request reaching Planet without them cannot be attributed to a planet.
      final request = await withId('req-1').createJoinRequest(
        teamId: 'team-1',
        userId: 'user-1',
        planetCode: 'guatemala',
      );

      expect(request!.teamPlanetCode, 'guatemala');
      expect(request.userPlanetCode, 'guatemala');
      expect(
        request.sourcePlanet,
        null,
        reason: 'Kotlin sets neither sourcePlanet nor parentCode here',
      );

      final doc = TeamsRepository.serializeTeamDocument(request);
      expect(doc['teamPlanetCode'], 'guatemala');
      expect(doc['userPlanetCode'], 'guatemala');
      expect(doc.containsKey('sourcePlanet'), isFalse);
    });

    test('a null planet code is not rescued, matching Kotlin', () async {
      // Both Kotlin callers pass `user?.planetCode` raw (`TeamFragment.kt:289`,
      // `TeamDetailFragment.kt:274-276`); only `createLocalResourceLink` has
      // the prefs fallback. Pinned so a future lane adding a fallback here
      // knows it is a divergence rather than a repair.
      final request = await withId(
        'req-2',
      ).createJoinRequest(teamId: 'team-1', userId: 'user-1');

      expect(request!.teamPlanetCode, null);
      expect(request.userPlanetCode, null);
    });

    test('the round trip preserves what was stamped', () async {
      // **The Phase 56 / 74 / 98 shape, tested as a pair rather than as two
      // halves.** A writer and a reader that each pass their own test and
      // disagree about a key is this project's most expensive recurring
      // defect. Here the document the port *uploads* is fed straight back
      // through the mapper that reads a *pull*, so a key name that drifts on
      // either side fails.
      final link = await withId('link-rt').createLocalResourceLink(
        teamId: 'team-1',
        resourceId: 'res-1',
        title: 'Atlas',
        planetCode: 'guatemala',
      );
      final uploaded = TeamsRepository.serializeTeamDocument(link!);

      // **The local row is deleted first, and mutation testing is why.** The
      // first cut upserted the pulled companion straight over the row
      // `createLocalResourceLink` had just written, and deleting the four
      // reads from `TeamMapper.fromDoc` left it **green**: the companion's
      // values then become `Value.absent()`, which drift excludes from the
      // `ON CONFLICT DO UPDATE SET` clause, so the row kept the values the
      // *writer* had put there and the mapper was never asked anything. The
      // fixture could not distinguish the two behaviours — the Phase 156
      // shape, found here a second time in one round. With no local row the
      // insert carries only what the mapper read.
      //
      // `isUpdated` is not passed as `existing` either: with a dirty row the
      // mapper takes its early-return branch and asserts nothing about the
      // reads at all.
      await database.teamDao.deleteById('link-rt');
      final pulled = TeamMapper.fromDoc({...uploaded, '_rev': '2-abc'})!;
      await database.teamDao.upsert(pulled);
      final row = await database.teamDao.getById('link-rt');

      expect(row!.sourcePlanet, 'guatemala');
      expect(row.teamPlanetCode, 'guatemala');
      expect(
        row.userPlanetCode,
        null,
        reason: 'absent from the document, and absent is what it stays',
      );
    });

    test('a pull no longer blanks a stamped column', () async {
      // The mutation that matters: delete the four reads from
      // `TeamMapper.fromDoc` and this is what breaks. The columns become
      // write-only, the first sync after the upload clears them, and the
      // server keeps a document whose planet attribution this device supplied
      // and then withdrew.
      await database.teamDao.upsert(
        TeamMapper.fromDoc({
          '_id': 'team-1',
          'name': 'District',
          'type': 'team',
          'sourcePlanet': 'guatemala',
          'teamPlanetCode': 'guatemala',
          'userPlanetCode': 'ada-planet',
          'parentCode': 'earth',
        })!,
      );

      final row = await database.teamDao.getById('team-1');
      expect(row!.sourcePlanet, 'guatemala');
      expect(row.teamPlanetCode, 'guatemala');
      expect(row.userPlanetCode, 'ada-planet');
      expect(row.parentCode, 'earth');

      // And back out again on a local edit — the direction that was lossy
      // before these columns existed, because the port could not emit what the
      // row could not hold.
      final doc = TeamsRepository.serializeTeamDocument(row);
      expect(doc['sourcePlanet'], 'guatemala');
      expect(doc['teamPlanetCode'], 'guatemala');
      expect(doc['userPlanetCode'], 'ada-planet');
      expect(doc['parentCode'], 'earth');
    });

    test('a dirty row still adopts the server planet codes', () async {
      // **The data loss the second audit pass found, and the one the v50
      // no-backfill decision was silently relying on not existing.**
      //
      // `MyTeam.populateTeamFields` assigns all four *above* its
      // `if (!hadLocalChanges)` guard (`MyTeam.kt:81`, `:86`, `:94`, `:96`;
      // the guard opens at `:100`), so a Kotlin row with an undelivered local
      // edit still takes the server's planet attribution. The port's mapper
      // returns early for `isUpdated` and carried only `_rev`, so the codes
      // stayed NULL — then `serializeTeamDocument` omits a null, and the
      // upload that clears `isUpdated` PUT a document **without** them. The
      // codes were gone from Planet and the next walk pulled the stripped
      // document back, so nothing could detect it afterwards.
      await database.teamDao.upsert(
        TeamsCompanion.insert(
          id: 'team-1',
          name: const Value('District (renamed offline)'),
          type: const Value('team'),
          isUpdated: const Value(true),
        ),
      );
      final existing = await database.teamDao.getById('team-1');

      final pulled = TeamMapper.fromDoc({
        '_id': 'team-1',
        '_rev': '7-abc',
        'name': 'District',
        'type': 'team',
        'sourcePlanet': 'guatemala',
        'teamPlanetCode': 'guatemala',
        'userPlanetCode': 'ada-planet',
        'parentCode': 'earth',
      }, existing: existing)!;
      await database.teamDao.upsert(pulled);

      final row = await database.teamDao.getById('team-1');
      expect(
        row!.name,
        'District (renamed offline)',
        reason: 'the local edit must still outrank the server copy',
      );
      expect(row.isUpdated, isTrue, reason: 'still owed to the server');
      expect(row.rev, '7-abc');
      // The planet codes are not the user's edit — no screen writes them — so
      // the "outranks the server copy" argument does not reach them.
      expect(row.sourcePlanet, 'guatemala');
      expect(row.teamPlanetCode, 'guatemala');
      expect(row.userPlanetCode, 'ada-planet');
      expect(row.parentCode, 'earth');

      // And the upload that clears `isUpdated` now carries them.
      final doc = TeamsRepository.serializeTeamDocument(row);
      expect(doc['sourcePlanet'], 'guatemala');
      expect(doc['parentCode'], 'earth');
    });

    test('a resource link uploads exactly the keys Kotlin sends', () async {
      // `MyTeam.serialize` returns early for this docType (`:167-179`) with
      // seven fields plus `_id`/`_rev`. The port had no such branch, so it
      // sent `createdDate`, `isLeader` and `public` — and, once
      // `addResourceLink` began stamping it, `userPlanetCode`, which Kotlin
      // sets on the row (`:683`) and deliberately keeps off the document. That
      // is the same shape as the `status` field this round declined to stamp
      // for exactly that reason, so the two had to be resolved the same way.
      final ui = await withId('link-keys').addResourceLink(
        teamId: 'team-1',
        resourceId: 'res-1',
        title: 'Atlas',
        planetCode: 'guatemala',
      );

      expect(
        ui!.userPlanetCode,
        'guatemala',
        reason: 'the row matches Kotlin, which does set it',
      );
      expect(
        TeamsRepository.serializeTeamDocument(ui).keys.toSet(),
        {
          '_id',
          'resourceId',
          'title',
          'teamId',
          'teamPlanetCode',
          'teamType',
          'docType',
        },
        reason:
            'and the document matches Kotlin, which does not send it — nor '
            'createdDate, isLeader or public',
      );
    });

    test('a general team document keeps every key it had', () async {
      // The branch above must not leak into any other docType: `MyTeam
      // .serialize`'s general block writes all four planet codes (`:204`,
      // `:207`, `:208`, `:211`) and the `createdDate`/`public`/`isLeader`
      // fields the resourceLink branch omits.
      await database.teamDao.upsert(
        TeamMapper.fromDoc({
          '_id': 'team-1',
          'name': 'District',
          'type': 'team',
          'createdDate': 42,
          'public': true,
          'userPlanetCode': 'ada-planet',
          'parentCode': 'earth',
        })!,
      );

      final doc = TeamsRepository.serializeTeamDocument(
        (await database.teamDao.getById('team-1'))!,
      );
      expect(doc['createdDate'], 42);
      expect(doc['public'], isTrue);
      expect(doc['userPlanetCode'], 'ada-planet');
      expect(doc['parentCode'], 'earth');
    });

    test('an absent key stays absent rather than becoming empty', () async {
      // The deliberate divergence. `JsonUtils.getString` defaults to `""`, so
      // Kotlin's row reads `""` and its serializer — which strips only
      // `JsonNull` — re-uploads `"sourcePlanet": ""`. The port maps absent to
      // null and omits it, restoring the document to the shape it arrived in.
      await database.teamDao.upsert(
        TeamMapper.fromDoc({'_id': 'team-2', 'name': 'District'})!,
      );

      final row = await database.teamDao.getById('team-2');
      expect(row!.sourcePlanet, null);
      expect(
        TeamsRepository.serializeTeamDocument(row).containsKey('sourcePlanet'),
        isFalse,
      );
    });
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
