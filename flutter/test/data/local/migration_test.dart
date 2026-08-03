import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// The upgrade path drops and recreates the CouchDB caches so the next sync
/// refills them. These tests pin the exception to that rule: a table holding
/// work the user did offline must survive, because no sync can give it back.
void main() {
  late NativeDatabase executor;
  late AppDatabase database;

  setUp(() {
    // One executor shared across both "runs" so the file-level state persists,
    // the way an on-device upgrade would see it.
    executor = NativeDatabase.memory();
    database = AppDatabase(executor);
  });
  tearDown(() => database.close());

  Future<void> runUpgrade() =>
      database.customStatement('SELECT 1').then((_) async {
        final migrator = database.createMigrator();
        await database.migration.onUpgrade(
          migrator,
          database.schemaVersion - 1,
          database.schemaVersion,
        );
      });

  test('an un-pushed outbox operation survives a schema upgrade', () async {
    await database.outboxDao.upsert(
      OutboxEntriesCompanion.insert(
        id: 'op-1',
        uploadType: 'personals',
        itemId: 'note-1',
        payload: '{"title":"offline note"}',
        endpoint: 'https://planet.example.org/db/resources',
        createdAt: 1000,
      ),
    );

    await runUpgrade();

    final survivors = await database.outboxDao.due(9999);
    expect(
      survivors.map((row) => row.id),
      ['op-1'],
      reason: 'dropping the queue would discard a write the user already made',
    );
  });

  test('an un-uploaded personal note survives a schema upgrade', () async {
    await database.personalDao.upsert(
      PersonalEntriesCompanion.insert(
        id: 'note-1',
        title: 'Private',
        titleNormalized: 'private',
        date: 1000,
        userId: 'user-1',
      ),
    );

    await runUpgrade();

    expect((await database.personalDao.getById('note-1'))?.title, 'Private');
  });

  test('a removed-log entry survives, so a leave is not re-added', () async {
    await database.removedLogDao.record(
      type: 'courses',
      docId: 'course-1',
      userId: 'user-1',
    );

    await runUpgrade();

    expect(
      await database.removedLogDao.removedDocIds('courses', 'user-1'),
      contains('course-1'),
    );
  });

  test('a meetup that has not reached the server survives', () async {
    // `EventsRepository.create` writes these before any upload, so a drop
    // would discard a meetup that exists nowhere else.
    await database.meetupDao.upsert(
      MeetupsCompanion.insert(
        id: 'local-1',
        title: const Value('Offline meetup'),
        updated: const Value(true),
      ),
    );

    await runUpgrade();

    final survivors = await database.meetupDao.pendingUploads();
    expect(survivors.map((row) => row.id), contains('local-1'));
  });

  test('a voices post that has not reached the server survives', () async {
    await database.newsDao.upsert(
      NewsEntriesCompanion.insert(
        id: 'local-1',
        message: const Value('Written offline'),
        userId: const Value('user-1'),
      ),
    );

    await runUpgrade();

    final survivor = await database.newsDao.getById('local-1');
    expect(survivor?.message, 'Written offline');
  });

  test('a survey cache is dropped, carrying no local writes', () async {
    await database.surveyDao.upsertAll([
      SurveysCompanion.insert(id: 'survey-1', name: const Value('Survey')),
    ], const {});
    // `isNull`/`isNotNull` are ambiguous here — drift exports its own.
    expect(await database.surveyDao.getById('survey-1'), isA<SurveyRow>());

    await runUpgrade();

    expect(await database.surveyDao.getById('survey-1'), equals(null));
  });

  test(
    'a CouchDB cache is still dropped and refilled by the next sync',
    () async {
      await database.userDao.upsert(
        UsersCompanion.insert(id: 'user-1', name: const Value('ada')),
      );
      expect(await database.userDao.count(), 1);

      await runUpgrade();

      expect(
        await database.userDao.count(),
        0,
        reason: 'users are a cache; the drop-and-resync policy still applies',
      );
    },
  );
}
