import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/achievement_files.dart';
import 'package:myplanet/core/network/network_result.dart';
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
