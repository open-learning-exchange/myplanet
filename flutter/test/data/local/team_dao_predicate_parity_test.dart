import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// Two `TeamDao` predicates against the Kotlin they port, each with the raw
/// statement run on the same rows as an independent oracle.
void main() {
  late AppDatabase db;
  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  Future<void> doc(
    String id, {
    String? teamId,
    String? docType,
    String? resourceId,
    String? type,
    String? status,
    String? userId,
  }) => db.teamDao.upsertAll([
    TeamsCompanion.insert(
      id: id,
      teamId: teamId == null ? const Value.absent() : Value(teamId),
      docType: docType == null ? const Value.absent() : Value(docType),
      resourceId: resourceId == null ? const Value.absent() : Value(resourceId),
      type: type == null ? const Value.absent() : Value(type),
      status: status == null ? const Value.absent() : Value(status),
      userId: userId == null ? const Value.absent() : Value(userId),
    ),
  ]);

  group('watchResourceLinks — TeamDao.kt:13', () {
    Future<List<String>> kotlinResourceIds(String teamId) async {
      final rows = await db
          .customSelect(
            'SELECT resource_id FROM teams WHERE team_id = ?1 '
            "AND resource_id IS NOT NULL AND TRIM(resource_id) != '' "
            "AND (doc_type IS NULL OR TRIM(doc_type) = '' "
            "OR doc_type = 'resourceLink' OR doc_type = 'link')",
            variables: [Variable<String>(teamId)],
          )
          .get();
      return rows.map((r) => r.read<String>('resource_id')).toList()..sort();
    }

    test('all four docType shapes are links', () async {
      await doc('l1', teamId: 'T', docType: 'resourceLink', resourceId: 'r1');
      // The three the port could not see. `TeamMapper.fromDoc` copies docType
      // off the server document, so a link Planet stored any of these ways
      // reached the table and no reader matched it.
      await doc('l2', teamId: 'T', docType: 'link', resourceId: 'r2');
      await doc('l3', teamId: 'T', resourceId: 'r3'); // docType absent
      await doc('l4', teamId: 'T', docType: '  ', resourceId: 'r4');

      final ids =
          (await db.teamDao.watchResourceLinks('T').first)
              .map((r) => r.resourceId!)
              .toList()
            ..sort();
      expect(ids, ['r1', 'r2', 'r3', 'r4']);
      expect(await kotlinResourceIds('T'), ids);
    });

    test(
      'a team document is not a link, which is what the resourceId guard is for',
      () async {
        // **Load-bearing decoy.** A team's own document has `docType` NULL, so
        // the widened predicate matches it on that clause alone; only the
        // non-blank `resourceId` test keeps it out. Drop that guard and this is
        // the single case in the file that notices.
        await doc('team-doc', teamId: 'T', type: 'team');
        await doc('blank', teamId: 'T', resourceId: '   ');
        await doc('real', teamId: 'T', docType: 'link', resourceId: 'r1');

        expect(
          (await db.teamDao.watchResourceLinks('T').first).map((r) => r.id),
          ['real'],
        );
        expect(await kotlinResourceIds('T'), ['r1']);
      },
    );

    test(
      'a membership is not a link and another team is out of scope',
      () async {
        await doc('m', teamId: 'T', docType: 'membership', resourceId: 'r9');
        await doc('other', teamId: 'U', docType: 'link', resourceId: 'r8');
        expect(await db.teamDao.watchResourceLinks('T').first, isEmpty);
        expect(await kotlinResourceIds('T'), isEmpty);
      },
    );
  });

  group('teamsByIds — TeamsRepositoryImpl.getMyTeamsFlow:206', () {
    test('an enterprise is not one of my teams', () async {
      await doc('t', type: 'team');
      // `type.isNullOrBlank()` keeps these two, which is why the predicate is
      // a three-way and not `type = 'team'`.
      await doc('untyped');
      await doc('blank-typed', type: '');
      // The decoy: an enterprise is a team *type*, so it differs from a team
      // in this column and nowhere else. Without it every fixture here is
      // kept and the clause is pinned by nothing.
      await doc('e', type: 'enterprise');

      final ids = (await db.teamDao.teamsByIds([
        't',
        'untyped',
        'blank-typed',
        'e',
      ])).map((r) => r.id).toList()..sort();
      expect(ids, ['blank-typed', 't', 'untyped']);
    });

    test('archived and sub-documents stay out, as before', () async {
      await doc('t', type: 'team');
      await doc('old', type: 'team', status: 'archived');
      await doc('sub', type: 'team', docType: 'membership');
      expect(
        (await db.teamDao.teamsByIds(['t', 'old', 'sub'])).map((r) => r.id),
        ['t'],
      );
    });
  });

  group('getFirstByStepId — ExamDao.kt:13', () {
    test('two exams on one step take the first rather than throwing', () async {
      // `getSingleOrNull` without `LIMIT 1` raises `Bad state: Too many
      // elements` here. The courses walk writes one exam per step, but a
      // standalone `exams` document may carry a `stepId` naming the same one.
      await db.examDao.upsertAll([
        ExamsCompanion.insert(id: 'e1', stepId: const Value('s1')),
        ExamsCompanion.insert(id: 'e2', stepId: const Value('s1')),
      ], const {});
      expect((await db.examDao.getFirstByStepId('s1'))?.id, isNotNull);
      expect(await db.examDao.getFirstByStepId('absent'), isNull);
    });
  });
}
