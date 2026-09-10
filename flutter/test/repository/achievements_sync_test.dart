import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/achievement_files.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/achievements_repository.dart';

import '../support/mock_planet_api.dart';

/// The `achievements` walk — `TransactionSyncManager.syncDb("achievements")`
/// plus `downloadCvAttachmentsFromBatch`.
///
/// Phase 116's D19: `AchievementsRepository.syncAchievements` was written and
/// never called, and `AchievementFiles.hasResume` carried a doc comment naming
/// itself "the Kotlin's `!file.exists()` guard on the sync-in" for a sync-in
/// that did not exist. A second device showed a blank ledger, and saving there
/// POSTed a document with no `_rev`, which CouchDB answers 409 — a status
/// `OutboxDrainer` treats as permanent, so the row was abandoned with no
/// snackbar and no log. Both ends close together.
void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late AchievementsRepository repository;
  late Directory tempDir;
  late Future<Directory> Function() savedBaseDirectory;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = AchievementsRepository(api, db.achievementDao);
    tempDir = Directory.systemTemp.createTempSync('achievements_sync');
    savedBaseDirectory = AchievementFiles.baseDirectory;
    AchievementFiles.baseDirectory = () async => tempDir;
  });

  tearDown(() {
    AchievementFiles.baseDirectory = savedBaseDirectory;
    if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
    return db.close();
  });

  Map<String, dynamic> achievementDoc(
    String id, {
    String purpose = 'Run the community seed bank',
    String? resumeFileName,
    bool withAttachment = false,
  }) => {
    '_id': id,
    '_rev': '4-abc',
    'purpose': purpose,
    'goals': 'Train two more growers',
    'achievementsHeader': 'What I have done',
    'sendToNation': false,
    'dateSortOrder': 'none',
    'createdOn': 'ole',
    'username': 'ada',
    'parentCode': 'ole',
    'achievements': [
      {'title': 'Seed bank opened', 'date': '2024-03-01', 'resources': []},
    ],
    'references': [
      {'name': 'Bob', 'phone': '555-0100'},
    ],
    'links': <dynamic>[],
    'otherInfo': <dynamic>[],
    'resumeFileName': ?resumeFileName,
    '_attachments': withAttachment
        ? {
            'resume.pdf': {'content_type': 'application/pdf', 'length': 9},
          }
        : null,
  };

  void stubWalk(List<Map<String, dynamic>> docs) {
    when(
      () => api.getJsonObject(
        '$dbUrl/achievements/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': docs.length}),
    );
    when(
      () => api.getJsonObject(
        any(that: contains('achievements/_all_docs?include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          for (final doc in docs) {'id': doc['_id'], 'doc': doc},
        ],
      }),
    );
  }

  test('a ledger written on another device arrives here', () async {
    stubWalk([achievementDoc('org.couchdb.user:ada@gua')]);

    await repository.sync(config: config);

    final row = await db.achievementDao.getById('org.couchdb.user:ada@gua');
    expect(row, isNotNull);
    expect(row!.purpose, 'Run the community seed bank');
    expect(row.rev, '4-abc');
    expect(row.uploaded, isTrue);
    expect(
      AchievementsRepository.achievementsArray(row.achievementsJson).single,
      containsPair('title', 'Seed bank opened'),
    );
  });

  test('skips _design documents', () async {
    stubWalk([
      {'_id': '_design/achievements'},
      achievementDoc('org.couchdb.user:ada@gua'),
    ]);

    await repository.sync(config: config);

    expect(await db.achievementDao.getById('_design/achievements'), isNull);
  });

  test('downloads the CV attachment named by the document', () async {
    stubWalk([
      achievementDoc(
        'org.couchdb.user:ada@gua',
        resumeFileName: 'ada-cv.pdf',
        withAttachment: true,
      ),
    ]);
    // The URL uses the literal attachment key `resume.pdf`, not the
    // `resumeFileName` the local file is stored under
    // (`TransactionSyncManager.kt:451` vs `:376`).
    when(
      () => api.getBytes(
        '$dbUrl/achievements/org.couchdb.user:ada@gua/resume.pdf',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((_) async => NetworkSuccess<List<int>>(List.filled(9, 37)));

    await repository.sync(config: config);

    expect(await AchievementFiles.hasResume('ada-cv.pdf'), isTrue);
  });

  test('a document with no resume.pdf attachment fetches nothing', () async {
    // `hasAttachment` is the Kotlin's guard: a `resumeFileName` alone is not
    // enough, because the document may name a CV the server never stored.
    stubWalk([
      achievementDoc('org.couchdb.user:ada@gua', resumeFileName: 'ada-cv.pdf'),
    ]);

    await repository.sync(config: config);

    verifyNever(
      () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
    );
    expect(await AchievementFiles.hasResume('ada-cv.pdf'), isFalse);
  });

  test('a CV already on disk is not fetched again', () async {
    await AchievementFiles.write(
      resumeFileName: 'ada-cv.pdf',
      bytes: List.filled(9, 1),
    );
    stubWalk([
      achievementDoc(
        'org.couchdb.user:ada@gua',
        resumeFileName: 'ada-cv.pdf',
        withAttachment: true,
      ),
    ]);

    await repository.sync(config: config);

    verifyNever(
      () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
    );
  });

  test(
    'an edit not yet uploaded survives the pull, and gains its rev',
    () async {
      // The row is authored locally under `"<userId>@<planetCode>"` and carries
      // no `_rev` until a walk supplies one. Overwriting it — which the Kotlin
      // does, `isUpdated = false` and all — discards the edit *and* stops it
      // uploading; and the missing `_rev` is exactly what makes its POST 409.
      await repository.update(
        'org.couchdb.user:ada@gua',
        const AchievementInput(
          purpose: 'Run the community seed bank and the tool library',
          username: 'ada',
          parentCode: 'ole',
        ),
      );

      stubWalk([achievementDoc('org.couchdb.user:ada@gua')]);
      await repository.sync(config: config);

      final row = await db.achievementDao.getById('org.couchdb.user:ada@gua');
      expect(row!.purpose, 'Run the community seed bank and the tool library');
      expect(row.uploaded, isFalse);
      expect(row.rev, '4-abc');
      expect(row.couchId, 'org.couchdb.user:ada@gua');
      expect(await repository.pendingUploads(), hasLength(1));
    },
  );

  test(
    'opening the screen first does not make the walk skip the server ledger',
    () async {
      // `achievementEntryProvider` calls `getOrInitialize` on watch, so simply
      // opening the achievements screen inserts a blank row. `uploaded`
      // defaults to `false` — the inverse of Kotlin's `isUpdated = false` — so
      // that blank row read as "an edit the user has not uploaded", the walk
      // preserved it *instead of* the server's ledger, and nothing ever
      // cleared the flag, so the screen stayed blank forever. Worse, the walk
      // then handed the blank row a valid `_rev`, arming the next save to PUT
      // an empty ledger over the real one.
      await repository.getOrInitialize('org.couchdb.user:ada@gua');

      stubWalk([achievementDoc('org.couchdb.user:ada@gua')]);
      await repository.sync(config: config);

      final row = await db.achievementDao.getById('org.couchdb.user:ada@gua');
      expect(row!.purpose, 'Run the community seed bank');
      expect(row.uploaded, isTrue);
      expect(await repository.pendingUploads(), isEmpty);
    },
  );

  test('a placeholder row is not queued for upload', () async {
    // The same defect from the other side: a blank ledger nobody has edited
    // must not reach `pendingUploads`, or Save would push it to the server.
    await repository.getOrInitialize('org.couchdb.user:ada@gua');

    expect(await repository.pendingUploads(), isEmpty);
  });

  test('one non-string scalar does not take the whole page down', () async {
    // Kotlin's `JsonUtils.getString` coerces through `toString()`, and so does
    // the port's — but this walk was reading `doc['parentCode'] as String?`
    // directly, so a numeric `parentCode` threw out of the mapper, `insertDocs`
    // never ran, the good document on the page was lost with it, and the whole
    // achievements area reported failed.
    stubWalk([
      achievementDoc('org.couchdb.user:ada@gua'),
      {...achievementDoc('org.couchdb.user:bob@gua'), 'parentCode': 123},
    ]);

    final result = await repository.sync(config: config);

    expect(result, isA<SyncComplete>());
    expect(
      await db.achievementDao.getById('org.couchdb.user:ada@gua'),
      isNotNull,
    );
    expect(
      (await db.achievementDao.getById('org.couchdb.user:bob@gua'))!.parentCode,
      '123',
    );
  });

  test('never prunes: a ledger the walk did not list survives', () async {
    await repository.getOrInitialize('org.couchdb.user:bob@gua');

    stubWalk([achievementDoc('org.couchdb.user:ada@gua')]);
    await repository.sync(config: config);

    expect(
      await db.achievementDao.getById('org.couchdb.user:bob@gua'),
      isNotNull,
    );
  });
}
