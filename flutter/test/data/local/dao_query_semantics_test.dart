import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// The three DAO queries Phase 143 added have no callers yet — their call sites
/// are other lanes' files — so these pin the SQL semantics their doc-comments
/// claim, and pin two of them against the **raw Kotlin statement** run on the
/// same rows rather than against a reading of it.
///
/// The three-valued cases are the point. A Dart `status != 'pending'` filter
/// counts a NULL status and SQL does not; a `userId ?? ''` fallback matches
/// nothing where `userId IS NULL` matches the anonymous rows.
void main() {
  late AppDatabase db;
  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  test('a NULL status does not count, as SQL != gives', () async {
    await db.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 's-null',
        userId: const Value('ada'),
        parentId: const Value('exam-1@ada'),
      ), // status absent -> NULL
      SubmissionsCompanion.insert(
        id: 's-pending',
        userId: const Value('ada'),
        parentId: const Value('exam-1@ada'),
        status: const Value('pending'),
      ),
      SubmissionsCompanion.insert(
        id: 's-complete',
        userId: const Value('ada'),
        parentId: const Value('exam-1@ada'),
        status: const Value('complete'),
      ),
    ]);
    expect(
      await db.submissionDao.countCompletedByUserAndExamId('ada', 'exam-1'),
      1,
    );
    // The raw Kotlin statement, for comparison.
    final raw = await db
        .customSelect(
          "SELECT COUNT(*) AS c FROM submissions WHERE user_id IS 'ada' "
          "AND parent_id LIKE '%' || 'exam-1' || '%' AND status != 'pending'",
        )
        .getSingle();
    expect(raw.read<int>('c'), 1);
  });

  test('a null userId matches the rows with no user, as IS does', () async {
    await db.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 's-anon',
        parentId: const Value('exam-1'),
        status: const Value('complete'),
      ),
      SubmissionsCompanion.insert(
        id: 's-ada',
        userId: const Value('ada'),
        parentId: const Value('exam-1'),
        status: const Value('complete'),
      ),
    ]);
    expect(
      await db.submissionDao.countCompletedByUserAndExamId(null, 'exam-1'),
      1,
    );
    expect(
      await db.submissionDao.countCompletedByUserAndExamId('ada', 'exam-1'),
      1,
    );
  });

  test("an exam id with a quote does not inject", () async {
    await db.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 's-1',
        userId: const Value('ada'),
        parentId: const Value("ex'am"),
        status: const Value('complete'),
      ),
    ]);
    expect(
      await db.submissionDao.countCompletedByUserAndExamId('ada', "ex'am"),
      1,
    );
    expect(
      await db.submissionDao.countCompletedByUserAndExamId(
        'ada',
        "' OR 1=1 --",
      ),
      0,
    );
  });

  test(
    'getPlanetMessages folds case on both columns; NULL matches neither',
    () async {
      await db.newsDao.upsert(
        NewsEntriesCompanion.insert(
          id: 'n-1',
          docType: const Value('MESSAGE'),
          createdOn: const Value('EARTH'),
        ),
      );
      await db.newsDao.upsert(
        NewsEntriesCompanion.insert(id: 'n-2'),
      ); // both NULL
      await db.newsDao.upsert(
        NewsEntriesCompanion.insert(
          id: 'n-3',
          docType: const Value('message'),
          createdOn: const Value('mars'),
        ),
      );
      expect((await db.newsDao.getPlanetMessages('earth')).map((r) => r.id), [
        'n-1',
      ]);
      final raw = await db
          .customSelect(
            "SELECT id FROM news WHERE doc_type = 'message' COLLATE NOCASE "
            "AND created_on = 'earth' COLLATE NOCASE",
          )
          .get();
      expect(raw.map((r) => r.read<String>('id')), ['n-1']);
    },
  );

  test('getByNewsId is case-sensitive, as the Kotlin query is', () async {
    await db.newsDao.upsert(
      NewsEntriesCompanion.insert(id: 'n-1', newsId: const Value('Chat-1')),
    );
    expect((await db.newsDao.getByNewsId('Chat-1')).map((r) => r.id), ['n-1']);
    expect(await db.newsDao.getByNewsId('chat-1'), isEmpty);
  });
}
