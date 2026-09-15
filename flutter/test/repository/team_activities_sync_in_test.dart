import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/team_mapper.dart';
import 'package:myplanet/repository/teams_repository.dart';

/// The `team_activities` pull, and the two questions a green screen test
/// structurally cannot ask: can the writer produce rows the readers' predicates
/// match, and does the round trip preserve the one column this table holds
/// local authority over?
///
/// `TeamLogDao.teamVisitsForUsers` and `lastTeamVisit` feed the member-detail
/// screen *and* `team_leaderboard_screen`'s ranking, so every assertion here
/// goes through those readers rather than through the raw table where it can.
void main() {
  late AppDatabase database;
  late TeamsRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = TeamsRepository(
      _MockPlanetApi(),
      database.teamDao,
      database.teamLogDao,
    );
  });
  tearDown(() => database.close());

  Map<String, dynamic> doc({
    required String id,
    String rev = '1-a',
    String user = 'ada',
    String teamId = 'team-1',
    int time = 1000,
    String type = 'teamVisit',
  }) => {
    '_id': id,
    '_rev': rev,
    'user': user,
    'type': type,
    'teamId': teamId,
    'teamType': 'sync',
    'parentCode': 'nation',
    'createdOn': 'planet-a',
    'time': time,
  };

  group('a document with no local counterpart', () {
    test('is inserted, and the leaderboard readers see it', () async {
      expect(
        await repository.insertTeamActivitiesFromSync([doc(id: 'srv-1')]),
        1,
      );

      final rows = await repository.teamVisitsForUsers('team-1', ['ada']);
      expect(rows, hasLength(1));
      expect(rows.single.id, 'srv-1');
      expect(rows.single.couchId, 'srv-1');
      expect(rows.single.rev, '1-a');
      expect(rows.single.time, 1000);
      // The other reader, which the member-detail screen's last-visit row uses.
      expect(await repository.lastTeamVisit('ada', 'team-1'), 1000);
    });

    test('is never offered back to the uploader', () async {
      await repository.insertTeamActivitiesFromSync([doc(id: 'srv-1')]);

      // **The single most important assertion in this file.** The port's
      // pending predicate is `uploaded = false` where Kotlin's is
      // `_rev IS NULL`, so a literal port of `teamLogFromJson` — which never
      // sets `uploaded` — would leave every pulled document flagged for
      // upload. planet.learning holds 13,659 of them; the next sync would
      // POST all 13,659 straight back and duplicate the database, every time.
      expect(await repository.pendingTeamLogUploads(), isEmpty);
    });

    test('design documents are skipped', () async {
      expect(
        await repository.insertTeamActivitiesFromSync([
          doc(id: '_design/team_activities'),
        ]),
        0,
      );
      expect(await repository.teamVisitsForUsers('team-1', ['ada']), isEmpty);
    });

    test('a document with no usable id is skipped, not keyed by ""', () async {
      expect(await repository.insertTeamActivitiesFromSync([doc(id: '')]), 0);
      expect(await repository.teamVisitsForUsers('team-1', ['ada']), isEmpty);
    });
  });

  group('this device seeing its own visit come back', () {
    test('adopts the uploaded row instead of counting it twice', () async {
      final localId = await repository.logTeamVisit(
        teamId: 'team-1',
        userName: 'ada',
        teamType: 'sync',
      );
      // What `TeamLogUploader.handler` does once the POST is answered.
      await database.teamLogDao.markUploaded(localId!, 'srv-1', '1-a');
      final localTime = (await repository.teamVisitsForUsers('team-1', [
        'ada',
      ])).single.time!;

      await repository.insertTeamActivitiesFromSync([
        doc(id: 'srv-1', time: localTime),
      ]);

      // One visit happened, so the leaderboard must count one. Kotlin counts
      // two here: `teamLogFromJson` keys the pulled row by the server `_id`
      // while `markUploaded` leaves the local row on its generated id, so
      // `upsertAll` inserts a second row for the same visit. The dedup the
      // Kotlin wants is sitting unused in its own DAO
      // (`TeamLogDao.getByRemoteIds`, no caller in `app/src/main`).
      final rows = await repository.teamVisitsForUsers('team-1', ['ada']);
      expect(rows, hasLength(1));
      expect(rows.single.id, localId, reason: 'the local primary key is kept');
      expect(rows.single.rev, '1-a', reason: 'the server revision is adopted');
    });

    test(
      'adopts a row whose POST landed but whose markUploaded never ran',
      () async {
        // The process died between the POST and `markUploaded`. The row holds
        // no `_id`, so it cannot be matched by one — this is what the
        // `(time, user, teamId)` fallback is for, and without it the visit is
        // counted twice *and* re-uploaded, making a second server document.
        final localId = await repository.logTeamVisit(
          teamId: 'team-1',
          userName: 'ada',
          teamType: 'sync',
        );
        final localTime = (await repository.teamVisitsForUsers('team-1', [
          'ada',
        ])).single.time!;

        await repository.insertTeamActivitiesFromSync([
          doc(id: 'srv-1', time: localTime),
        ]);

        final rows = await repository.teamVisitsForUsers('team-1', ['ada']);
        expect(rows, hasLength(1));
        expect(rows.single.id, localId);
        expect(rows.single.couchId, 'srv-1');
        expect(await repository.pendingTeamLogUploads(), isEmpty);
      },
    );

    test(
      'does not adopt a visit to a different team at the same instant',
      () async {
        // The natural key carries `teamId` because one user legitimately visits
        // several teams; Kotlin's analogue on the sibling table is
        // `(loginTime, userName)` only, which has no second team to confuse.
        final localId = await repository.logTeamVisit(
          teamId: 'team-1',
          userName: 'ada',
          teamType: 'sync',
        );
        final localTime = (await repository.teamVisitsForUsers('team-1', [
          'ada',
        ])).single.time!;

        await repository.insertTeamActivitiesFromSync([
          doc(id: 'srv-2', teamId: 'team-2', time: localTime),
        ]);

        expect(
          (await repository.teamVisitsForUsers('team-1', ['ada'])).single.id,
          localId,
        );
        expect(
          (await repository.teamVisitsForUsers('team-2', ['ada'])).single.id,
          'srv-2',
        );
        // The round-trip assertion belongs here rather than on the test whose
        // input `getByTimesAndUsers` filters out in SQL: this document reaches
        // the Dart narrowing and is rejected by it, so this is what pins that
        // a near-miss leaves the local row's `uploaded` flag alone.
        expect(await repository.pendingTeamLogUploads(), hasLength(1));
      },
    );

    test('an unsynced visit of its own is left pending', () async {
      // The round-trip question this table is preserved for: the sync-in must
      // not touch the `uploaded` flag of a row it did not match, or the visit
      // silently never leaves the device.
      final pendingId = await repository.logTeamVisit(
        teamId: 'team-1',
        userName: 'ada',
        teamType: 'sync',
      );

      await repository.insertTeamActivitiesFromSync([
        doc(id: 'srv-1', user: 'grace', time: 5000),
      ]);

      final pending = await repository.pendingTeamLogUploads();
      expect(pending.map((row) => row.id), [pendingId]);
    });
  });

  group('logTeamVisit', () {
    test('refuses a whitespace-only team id, as isBlank does', () async {
      // Kotlin guards with `teamId.isBlank()`; the port tested
      // `teamId.isEmpty`, so a whitespace-only id filed a visit against a team
      // no screen can reach — and the sync-in now gives those rows a second
      // way to accumulate.
      expect(
        await repository.logTeamVisit(teamId: '   ', userName: 'ada'),
        isNull,
      );
      expect(
        await repository.logTeamVisit(teamId: 'team-1', userName: '  '),
        isNull,
      );
      expect(
        await repository.logTeamVisit(teamId: 'team-1', userName: 'ada'),
        isNotNull,
      );
    });
  });

  group('lastTeamVisit', () {
    test('a null user name asks for nameless rows, not for everyone', () async {
      // Kotlin's query is `user IS :userName`, which matches NULL against
      // NULL. Dropping the predicate instead was harmless while this table
      // held only this handset's rows; with the pull it answers with the most
      // recent visit by anybody, drawn as that member's last visit.
      await repository.insertTeamActivitiesFromSync([
        doc(id: 'srv-1', user: 'grace', time: 9000),
      ]);

      expect(await repository.lastTeamVisit(null, 'team-1'), isNull);
      expect(await repository.lastTeamVisit('grace', 'team-1'), 9000);
    });
  });

  group('the merge is idempotent and page-safe', () {
    test('re-pulling the same page changes nothing', () async {
      await repository.insertTeamActivitiesFromSync([doc(id: 'srv-1')]);
      await repository.insertTeamActivitiesFromSync([
        doc(id: 'srv-1', rev: '2-b'),
      ]);

      final rows = await repository.teamVisitsForUsers('team-1', ['ada']);
      expect(rows, hasLength(1));
      expect(rows.single.rev, '2-b');
    });

    test(
      'two documents matching one local row do not collapse into one',
      () async {
        // Two server documents sharing a `(time, user, teamId)` key: the first
        // adopts the local row, the second must be keyed by its own `_id`
        // rather than overwriting the first and losing a visit.
        await repository.logTeamVisit(
          teamId: 'team-1',
          userName: 'ada',
          teamType: 'sync',
        );
        final localTime = (await repository.teamVisitsForUsers('team-1', [
          'ada',
        ])).single.time!;

        await repository.insertTeamActivitiesFromSync([
          doc(id: 'srv-1', time: localTime),
          doc(id: 'srv-2', time: localTime),
        ]);

        expect(
          await repository.teamVisitsForUsers('team-1', ['ada']),
          hasLength(2),
        );
      },
    );

    test('a stamped local row does not shadow the unstamped one', () async {
      // A row that already carries an `_id` is reachable through the id map,
      // so it must not also occupy a natural-key slot: if it did, a *second*
      // document sharing that key would adopt its primary key and overwrite
      // it, losing the first visit entirely. Drop the `couchId` skip when
      // building the fallback map and this is the test that fails.
      await database.teamLogDao.insert(
        TeamLogTableCompanion.insert(
          id: 'local-1',
          couchId: const Value('srv-1'),
          rev: const Value('1-a'),
          teamId: const Value('team-1'),
          user: const Value('ada'),
          type: const Value('teamVisit'),
          time: const Value(1000),
          uploaded: const Value(true),
        ),
      );

      await repository.insertTeamActivitiesFromSync([doc(id: 'srv-2')]);

      final rows = await repository.teamVisitsForUsers('team-1', ['ada']);
      expect(rows, hasLength(2));
      expect(rows.map((row) => row.couchId).toSet(), {'srv-1', 'srv-2'});
    });

    test('an id-less document cannot starve a real one of its row', () async {
      // The defect the second audit found. The claim on a local row was taken
      // before the document was known to yield a row at all, and an id-less
      // document survived the repository filter (`''` does not start with
      // `_design`). So doc 1 consumed the claim and produced nothing, doc 2
      // was denied the local row and was keyed by its own `_id`: **two rows
      // for one visit, and the starved local row left pending so it uploads a
      // second server document** — both failures this merge exists to
      // prevent, from one malformed row.
      await repository.logTeamVisit(
        teamId: 'team-1',
        userName: 'ada',
        teamType: 'sync',
      );
      final localTime = (await repository.teamVisitsForUsers('team-1', [
        'ada',
      ])).single.time!;

      await repository.insertTeamActivitiesFromSync([
        doc(id: '', time: localTime),
        doc(id: 'srv-1', time: localTime),
      ]);

      expect(
        await repository.teamVisitsForUsers('team-1', ['ada']),
        hasLength(1),
      );
      expect(await repository.pendingTeamLogUploads(), isEmpty);
    });

    test('an empty page writes nothing and reports nothing', () async {
      expect(await repository.insertTeamActivitiesFromSync([]), 0);
    });

    test('other users\' visits arrive — the whole point of the pull', () async {
      // Before this phase the leaderboard ranked members by what *this*
      // handset observed, so a member working entirely on another device
      // ranked last however much they did.
      await repository.insertTeamActivitiesFromSync([
        doc(id: 'srv-1', user: 'grace', time: 1000),
        doc(id: 'srv-2', user: 'grace', time: 2000),
        doc(id: 'srv-3', user: 'ada', time: 3000),
      ]);

      expect(
        await repository.teamVisitsForUsers('team-1', ['grace']),
        hasLength(2),
      );
      expect(
        await repository.teamVisitsForUsers('team-1', ['ada']),
        hasLength(1),
      );
      expect(await repository.lastTeamVisit('grace', 'team-1'), 2000);
    });
  });

  group('TeamLogMapper', () {
    test(
      'a pulled row is marked uploaded, whatever the local row said',
      () async {
        await database.teamLogDao.insert(
          TeamLogTableCompanion.insert(
            id: 'local-1',
            teamId: const Value('team-1'),
            user: const Value('ada'),
            type: const Value('teamVisit'),
            time: const Value(1000),
          ),
        );

        final companion = TeamLogMapper.fromDoc(
          doc(id: 'srv-1'),
          fallback: (await database.teamLogDao.getByTimesAndUsers(
            [1000],
            ['ada'],
          )).single,
        )!;

        expect(companion.id.value, 'local-1');
        expect(companion.uploaded.value, isTrue);
      },
    );

    test('refuses a design document and an id-less one', () {
      // The load-bearing half of the `_design` guard. The repository filters
      // them too, mirroring `insertLoginActivitiesFromSync`, but that filter
      // is redundant: mutation-testing showed removing it changes nothing
      // because every document still passes through here. This is the layer
      // that has to hold, so this is the layer it is pinned at.
      expect(TeamLogMapper.fromDoc(doc(id: '_design/team_activities')), isNull);
      expect(TeamLogMapper.fromDoc(doc(id: '')), isNull);
      expect(TeamLogMapper.fromDoc(doc(id: 'srv-1')), isNotNull);
    });

    test('the natural key separates team, user and time', () {
      expect(
        TeamLogMapper.naturalKey(time: 1, user: 'a', teamId: 't'),
        TeamLogMapper.naturalKeyForDoc(
          doc(id: 'x', user: 'a', teamId: 't', time: 1),
        ),
      );
      expect(
        TeamLogMapper.naturalKey(time: 1, user: 'a', teamId: 't'),
        isNot(TeamLogMapper.naturalKey(time: 1, user: 'a', teamId: 'u')),
      );
      expect(
        TeamLogMapper.naturalKey(time: 1, user: 'a', teamId: 't'),
        isNot(TeamLogMapper.naturalKey(time: 2, user: 'a', teamId: 't')),
      );
      expect(
        TeamLogMapper.naturalKey(time: 1, user: 'a', teamId: 't'),
        isNot(TeamLogMapper.naturalKey(time: 1, user: 'b', teamId: 't')),
      );
      // Nulls collapse to a stable key rather than throwing.
      expect(TeamLogMapper.naturalKey(), '0__');
    });
  });
}

/// The merge never reaches the network — the pagination lives in
/// `HeavyTableSync`, which hands pages in.
class _MockPlanetApi extends Mock implements PlanetApi {}
