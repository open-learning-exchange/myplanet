import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/submissions_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// A [TeamDao] whose one read fails, standing in for the throwing lookup
/// `getTeamByIdOrNull` exists to absorb.
class _FailingTeamDao extends Mock implements TeamDao {
  _FailingTeamDao(this.error);
  final Object error;

  @override
  Future<TeamRow?> getById(String id) => Future<TeamRow?>.error(error);
}

/// The `team` object an uploaded submission carries — `resolveTeamJson` and
/// the unconditional `teamId` write, ported from upstream `aca425a`
/// ("teams: smoother submissions repository stamping", fixes #16664).
///
/// The whole point of that change is what happens when the team document is
/// **not** on this handset, which is the normal state of a device that has not
/// finished syncing, so the cache-miss path is tested first-class rather than
/// as an afterthought.
void main() {
  late AppDatabase database;
  late SubmissionsRepository repository;

  SubmissionsRepository build({TeamDao? teamDao}) => SubmissionsRepository(
    MockPlanetApi(),
    database.submissionDao,
    database.submitPhotosDao,
    database.surveyDao,
    database.examDao,
    teamDao: teamDao ?? database.teamDao,
  );

  setUp(() {
    database = AppDatabase.memory();
    repository = build();
  });
  tearDown(() => database.close());

  Future<void> seedTeam({
    String id = 'team-1',
    String? name = 'Water quality group',
    String? type = 'enterprise',
  }) => database.teamDao.upsert(
    TeamsCompanion.insert(id: id, name: Value(name), type: Value(type)),
  );

  Future<String> adoptionFor(String? teamId, {String id = 'adoption-1'}) async {
    await repository.createSurveyAdoptionSubmission(
      id: id,
      surveyId: 'survey-1',
      userId: 'ada',
      parentJson: '{"_id":"survey-1","name":"Water quality"}',
      userJson: '{"_id":"ada"}',
      source: 'planet-1',
      parentCode: 'parent-1',
      teamId: teamId,
    );
    return id;
  }

  Future<Object?> teamOf(String id) async =>
      (await repository.serialize((await repository.getById(id))!))['team'];

  Future<ExamRow> seedExam() async {
    await database.examDao.upsertAll([
      ExamsCompanion.insert(
        id: 'exam-1',
        name: const Value('Week 1'),
        courseId: const Value('course-1'),
      ),
    ], const {});
    return (await database.examDao.getById('exam-1'))!;
  }

  group('the team object an upload carries', () {
    test('is the local name and type under the row\'s team id', () async {
      await seedTeam();
      expect(await teamOf(await adoptionFor('team-1')), {
        '_id': 'team-1',
        'name': 'Water quality group',
        'type': 'enterprise',
      });
    });

    /// The reason `aca425a` exists. `_id` alone still attributes the
    /// submission — Planet joins on it — where the pre-change Kotlin emitted
    /// no `team` key at all and the association was gone for good.
    test(
      'keeps the id when the team document is not on this handset',
      () async {
        expect(await teamOf(await adoptionFor('team-missing')), {
          '_id': 'team-missing',
        });
      },
    );

    /// `name`/`type` are **omitted**, never sent as null: Kotlin guards each
    /// with `?.let { addProperty(...) }` (`:806-808`). A null would overwrite
    /// a name Planet already holds.
    test(
      'omits a name and type it cannot resolve rather than nulling them',
      () async {
        final team = await teamOf(await adoptionFor('team-missing'));
        expect((team! as Map).containsKey('name'), isFalse);
        expect((team as Map).containsKey('type'), isFalse);
      },
    );

    test('omits a blank name and type the same way', () async {
      // `takeIf { it.isNotBlank() }` (`:805-808`) — blank, not merely empty.
      await seedTeam(name: '   ', type: '');
      expect(await teamOf(await adoptionFor('team-1')), {'_id': 'team-1'});
    });

    /// `type` is no longer defaulted to `"team"`. The pre-change Kotlin wrote
    /// `team.type ?: "team"`, which mislabelled an enterprise whose own
    /// document said otherwise; the type now travels only when the local
    /// document has one.
    test('does not invent a type for a team whose document omits it', () async {
      await seedTeam(type: null);
      expect(await teamOf(await adoptionFor('team-1')), {
        '_id': 'team-1',
        'name': 'Water quality group',
      });
    });

    test('is absent entirely for a submission with no team', () async {
      final payload = await repository.serialize(
        (await repository.getById(await adoptionFor(null)))!,
      );
      expect(payload.containsKey('team'), isFalse);
    });

    test(
      'is absent for a blank team id rather than sent as a blank id',
      () async {
        final payload = await repository.serialize(
          (await repository.getById(await adoptionFor('  ')))!,
        );
        expect(payload.containsKey('team'), isFalse);
      },
    );

    /// `membershipDoc` is the other half Planet and the port's own sync-in
    /// read the team back from, and it must survive alongside the new key —
    /// not be replaced by it.
    test('travels alongside the user object\'s membershipDoc', () async {
      await seedTeam();
      final payload = await repository.serialize(
        (await repository.getById(await adoptionFor('team-1')))!,
      );
      expect((payload['user'] as Map)['membershipDoc'], {'teamId': 'team-1'});
      expect((payload['team'] as Map)['_id'], 'team-1');
    });
  });

  group('the team lookup', () {
    /// `getTeamByIdOrNull` swallows `Exception` so a failing read cannot cost
    /// the upload its team id — the document still goes up attributable.
    test('failing does not fail the upload', () async {
      repository = build(
        teamDao: _FailingTeamDao(Exception('database is locked')),
      );
      expect(await teamOf(await adoptionFor('team-1')), {'_id': 'team-1'});
    });

    /// The port of Kotlin's `CancellationException` rethrow. Dart has no
    /// cancellation exception; the hazard `on Exception` guards against is an
    /// `Error`, which means a defect here rather than a missing row, and
    /// reporting it as a cache miss would hide it.
    test('rethrows an Error instead of reporting a cache miss', () async {
      repository = build(teamDao: _FailingTeamDao(StateError('bad state')));
      final id = await adoptionFor('team-1');
      final row = (await repository.getById(id))!;
      await expectLater(repository.serialize(row), throwsA(isA<StateError>()));
    });
  });

  group('an exam attempt started from a team', () {
    test('persists the team id', () async {
      await seedTeam();
      final id = await repository.startExamSession(
        exam: await seedExam(),
        questions: const [],
        userId: 'ada',
        teamId: 'team-1',
      );
      expect((await repository.getById(id))!.teamId, 'team-1');
      expect(await teamOf(id), {
        '_id': 'team-1',
        'name': 'Water quality group',
        'type': 'enterprise',
      });
    });

    /// The `aca425a` fix, at the writer. `Submission.teamObject` is `@Ignore`d,
    /// so the column is the only durable carrier: gating the write on a
    /// resolved team — as the Kotlin used to — lost the attribution
    /// permanently, and it lost it precisely on the handsets whose cache was
    /// incomplete.
    test('persists it even when the team document is missing', () async {
      final id = await repository.startExamSession(
        exam: await seedExam(),
        questions: const [],
        userId: 'ada',
        teamId: 'team-1',
      );
      expect((await repository.getById(id))!.teamId, 'team-1');
      expect(await teamOf(id), {'_id': 'team-1'});
    });

    test('stores no team for a blank id', () async {
      final id = await repository.startExamSession(
        exam: await seedExam(),
        questions: const [],
        userId: 'ada',
        teamId: '   ',
      );
      expect((await repository.getById(id))!.teamId, isNull);
    });

    test('stores no team when none is given', () async {
      final id = await repository.startExamSession(
        exam: await seedExam(),
        questions: const [],
        userId: 'ada',
      );
      expect((await repository.getById(id))!.teamId, isNull);
    });
  });
}
