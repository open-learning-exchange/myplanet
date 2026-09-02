import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/achievements_repository.dart';

void main() {
  late AppDatabase database;
  late AchievementsRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = AchievementsRepository(database.achievementDao);
  });
  tearDown(() => database.close());

  test(
    'getOrInitialize creates an empty row keyed by the derived id',
    () async {
      final row = await repository.getOrInitialize('ada@earth');
      expect(row?.id, 'ada@earth');
      expect(row?.achievementsJson, '[]');
      expect(row?.uploaded, isFalse);
    },
  );

  test('getOrInitialize does not overwrite an existing row', () async {
    await repository.update(
      'ada@earth',
      const AchievementInput(
        goals: 'learn',
        achievementsJson: '[{"title":"First"}]',
      ),
    );
    final row = await repository.getOrInitialize('ada@earth');
    expect(row?.goals, 'learn');
    expect(row?.achievementsJson, contains('First'));
  });

  test('update round-trips the fields without touching the couch id', () async {
    await repository.update(
      'ada@earth',
      const AchievementInput(
        purpose: 'p',
        goals: 'g',
        achievementsHeader: 'h',
        sendToNation: true,
        achievementsJson: '[{"title":"Been"}]',
        referencesJson: '[{"name":"Mo"}]',
        createdOn: 'earth',
        username: 'ada',
        parentCode: 'earth',
        resumeFileName: 'cv.pdf',
      ),
    );
    final row = await repository.getOrInitialize('ada@earth');
    expect(row?.purpose, 'p');
    expect(row?.sendToNation, isTrue);
    expect(row?.achievementsJson, contains('Been'));
    expect(row?.referencesJson, contains('Mo'));
    expect(row?.resumeFileName, 'cv.pdf');

    // The Kotlin `updateAchievement` sets `isUpdated = true`: an edit on an
    // uploaded ledger puts it back on the upload backlog.
    await database.achievementDao.markUploaded('ada@earth', 'couch-1', '1-r');
    await repository.update(
      'ada@earth',
      const AchievementInput(goals: 'next', achievementsJson: '[]'),
    );
    final kept = await repository.getOrInitialize('ada@earth');
    expect(kept?.goals, 'next');
    expect(kept?.couchId, 'couch-1');
    expect(kept?.rev, '1-r');
    expect(kept?.uploaded, isFalse);
  });

  test('serialize rebuilds the document the Kotlin emits', () {
    final row = AchievementRow(
      id: 'ada@earth',
      purpose: 'p',
      goals: 'g',
      achievementsHeader: 'h',
      sendToNation: true,
      achievementsJson: '[{"title":"Been"}]',
      referencesJson: '[{"name":"Mo"}]',
      linksJson: '[]',
      otherInfoJson: '[]',
      dateSortOrder: '',
      createdOn: 'earth',
      username: 'ada',
      parentCode: 'earth',
      couchId: 'couch-1',
      rev: '2-r',
      uploaded: false,
      resumeFileName: 'cv.pdf',
    );
    final doc = AchievementsRepository.serialize(row);
    expect(doc['_id'], 'couch-1');
    expect(doc['_rev'], '2-r');
    expect(doc['sendToNation'], isTrue);
    expect(doc['dateSortOrder'], 'none');
    expect(doc['createdOn'], 'earth');
    expect(doc['username'], 'ada');
    expect(doc['parentCode'], 'earth');
    expect((doc['achievements'] as List).single['title'], 'Been');
    expect((doc['references'] as List).single['name'], 'Mo');
    expect(doc['resumeFileName'], 'cv.pdf');
  });

  test('serialize falls back to the derived id before the first sync', () {
    // Kotlin's `Achievement._id` *is* the primary key — the derived
    // `"$userId@$planetCode"` — so `serialize` always names a document and
    // the first PUT creates `achievements/<that id>`. A ledger the user has
    // just edited here has no couch id yet, and reading `couchId` alone
    // emitted `'_id': ''`, which the outbox handler rejects.
    final row = AchievementRow(
      id: 'ada@earth',
      purpose: '',
      goals: '',
      achievementsHeader: '',
      sendToNation: false,
      achievementsJson: '[]',
      referencesJson: '[]',
      linksJson: '[]',
      otherInfoJson: '[]',
      dateSortOrder: '',
      createdOn: 'earth',
      username: 'ada',
      parentCode: 'earth',
      couchId: '',
      rev: '',
      uploaded: false,
      resumeFileName: '',
    );
    final doc = AchievementsRepository.serialize(row);
    expect(doc['_id'], 'ada@earth');
    expect(doc.containsKey('_rev'), isFalse);
  });

  test('pendingUploads is the non-guest upload backlog', () async {
    await repository.update('a@earth', const AchievementInput());
    await repository.update('b@earth', const AchievementInput());
    await repository.update('guest@earth', const AchievementInput());
    await database.achievementDao.markUploaded('b@earth', 'couch-b', '1-r');
    final pending = await repository.pendingUploads();
    expect(pending.map((r) => r.id), ['a@earth']);
  });

  test('achievementsArray/referencesArray tolerate empty or broken JSON', () {
    expect(AchievementsRepository.achievementsArray('[]'), isEmpty);
    expect(AchievementsRepository.achievementsArray('not json'), isEmpty);
    expect(AchievementsRepository.referencesArray(''), isEmpty);
    final list = AchievementsRepository.achievementsArray(
      '[{"title":"T","resources":[{"title":"pdf"}]}]',
    );
    expect(list.single['title'], 'T');
    expect(
      AchievementsRepository.resourcesOf(list.single).single['title'],
      'pdf',
    );
  });

  test('syncAchievements skips design docs and marks rows synced', () async {
    final count = await repository.syncAchievements([
      {'_id': '_design/idx', 'goals': 'ignored'},
      {
        '_id': 'ada@earth',
        '_rev': '1-a',
        'goals': 'grow',
        'sendToNation': true,
        'achievements': [
          {'title': 'First'},
        ],
        'references': [],
      },
    ]);
    expect(count, 1);
    final row = await repository.getOrInitialize('ada@earth');
    expect(row?.goals, 'grow');
    expect(row?.rev, '1-a');
    expect(row?.sendToNation, isTrue);
    expect(row?.uploaded, isTrue);
    expect(
      AchievementsRepository.achievementsArray(
        row!.achievementsJson,
      ).single['title'],
      'First',
    );
    // And the synced row stays off the backlog.
    expect(await repository.pendingUploads(), isEmpty);
  });

  test(
    'syncAchievements tolerates string sendToNation from old docs',
    () async {
      await repository.syncAchievements([
        {'_id': 'ada@earth', 'sendToNation': 'true'},
      ]);
      expect(
        (await repository.getOrInitialize('ada@earth'))?.sendToNation,
        isTrue,
      );
    },
  );

  test('a synced row serializes back to a matching document', () async {
    final doc = <String, dynamic>{
      '_id': 'ada@earth',
      '_rev': '3-z',
      'goals': 'grow',
      'purpose': 'learn',
      'achievementsHeader': 'hdr',
      'sendToNation': true,
      'dateSortOrder': 'desc',
      'createdOn': 'earth',
      'username': 'ada',
      'parentCode': 'earth',
      'achievements': [
        {'title': 'First'},
      ],
      'references': [
        {'name': 'Mo'},
      ],
      'links': [],
      'otherInfo': [],
      'resumeFileName': 'cv.pdf',
    };
    await repository.syncAchievements([doc]);
    final row = await repository.getOrInitialize('ada@earth');
    expect(jsonDecode(jsonEncode(AchievementsRepository.serialize(row!))), doc);
  });
}
