import 'package:drift/drift.dart' hide isNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/chat_repository_impl.dart';

void main() {
  late AppDatabase database;
  late ChatRepositoryImpl repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = ChatRepositoryImpl(
      planetApi: MockPlanetApi(),
      chatDao: database.chatDao,
      serverUrl: 'https://planet.example',
    );
  });
  tearDown(() => database.close());

  Future<void> seedRevisions(List<String> revs) async {
    await database.chatDao.upsertAll([
      for (var i = 0; i < revs.length; i++)
        ChatEntriesCompanion.insert(
          id: 'row-$i',
          docId: const Value('chat-1'),
          rev: Value(revs[i]),
        ),
    ]);
  }

  test('getLatestRev orders by generation, not lexicographically', () async {
    // `_rev` is `<generation>-<hash>`, and the hash carries no ordering. String
    // comparison puts '9-...' above '10-...', so the newest revision would be
    // skipped the moment a document reached ten generations — and an upload
    // sent with a stale `_rev` is rejected as a conflict.
    await seedRevisions(['9-aaa', '10-bbb', '2-zzz']);

    expect(await repository.getLatestRev('chat-1'), '10-bbb');
  });

  test('getLatestRev tolerates an unparseable revision', () async {
    await seedRevisions(['not-a-rev', '3-ccc']);

    expect(await repository.getLatestRev('chat-1'), '3-ccc');
  });

  test('getLatestRev returns null when the document is unknown', () async {
    expect(await repository.getLatestRev('missing'), isNull);
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
