import 'package:drift/drift.dart' hide isNull;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_providers.dart';

void main() {
  late AppDatabase db;
  late ProviderContainer container;

  setUp(() {
    db = AppDatabase.memory();
    container = ProviderContainer(
      overrides: [appDatabaseProvider.overrideWithValue(db)],
    );
  });

  tearDown(() async {
    container.dispose();
    await db.close();
  });

  group('pendingSurveysProvider', () {
    SubmissionsCompanion submission(
      String id,
      String surveyId, {
      String status = 'pending',
      String? teamId,
      String userId = 'user-1',
    }) => SubmissionsCompanion.insert(
      id: id,
      parentId: Value(surveyId),
      type: const Value('survey'),
      status: Value(status),
      userId: Value(userId),
      teamId: Value(teamId),
    );

    SurveysCompanion survey(String id, String name) =>
        SurveysCompanion.insert(id: id, name: Value(name));

    test('counts pending answer sheets and dedupes per survey', () async {
      await db.surveyDao.upsertAll([
        survey('survey-1', 'Health check'),
        survey('survey-2', 'Feedback'),
      ], const {});
      await db.submissionDao.upsertAll([
        submission('sub-1', 'survey-1', status: 'pending'),
        submission('sub-2', 'survey-1', status: 'pending'),
        submission('sub-3', 'survey-2'),
        submission('sub-4', 'survey-2', status: 'complete'),
      ]);

      final pending = await container.read(
        pendingSurveysProvider('user-1').future,
      );

      expect(pending.map((p) => p.name), ['Health check', 'Feedback']);
    });

    test(
      'does not present blank-status adoption records as assignments',
      () async {
        await db.surveyDao.upsertAll([
          survey('survey-1', 'Adopted survey'),
        ], const {});
        await db.submissionDao.upsertAll([
          submission('adoption-1', 'survey-1', status: ''),
        ]);

        final pending = await container.read(
          pendingSurveysProvider('user-1').future,
        );

        expect(pending, isEmpty);
      },
    );

    test('drops team submissions and surveys that no longer exist', () async {
      await db.surveyDao.upsertAll([
        survey('survey-1', 'Individual'),
      ], const {});
      await db.submissionDao.upsertAll([
        submission('sub-1', 'survey-1'),
        submission('sub-2', 'survey-1', teamId: 'team-9'),
        submission('sub-3', 'survey-gone'),
        submission('sub-4', 'survey-1', userId: 'someone-else'),
      ]);

      final pending = await container.read(
        pendingSurveysProvider('user-1').future,
      );

      expect(pending, hasLength(1));
      expect(pending.single.name, 'Individual');
      expect(pending.single.submissionId, 'sub-1');
    });
  });

  group('myTeamsStreamProvider', () {
    TeamsCompanion teamDoc(String id, String name, {String? status}) =>
        TeamsCompanion.insert(
          id: id,
          name: Value(name),
          type: const Value('team'),
          status: Value(status),
        );

    TeamsCompanion membership(String id, String teamId, String userId) =>
        TeamsCompanion.insert(
          id: id,
          teamId: Value(teamId),
          userId: Value(userId),
          docType: const Value('membership'),
        );

    test('resolves memberships to team documents, skipping archived', () async {
      await db.teamDao.upsertAll([
        teamDoc('team-1', 'Gardeners'),
        teamDoc('team-2', 'Archived crew', status: 'archived'),
        teamDoc('team-3', 'Not mine'),
        membership('m-1', 'team-1', 'user-1'),
        membership('m-2', 'team-2', 'user-1'),
        membership('m-3', 'team-3', 'someone-else'),
      ]);

      final teams = await container.read(
        myTeamsStreamProvider('user-1').future,
      );

      expect(teams.map((t) => t.name), ['Gardeners']);
    });

    test('emits empty for a user with no memberships', () async {
      await db.teamDao.upsertAll([teamDoc('team-1', 'Gardeners')]);

      final teams = await container.read(
        myTeamsStreamProvider('user-1').future,
      );

      expect(teams, isEmpty);
    });
  });
}
