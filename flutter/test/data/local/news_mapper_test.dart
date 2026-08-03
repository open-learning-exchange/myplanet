import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/news_mapper.dart';

void main() {
  late AppDatabase database;

  setUp(() => database = AppDatabase.memory());
  tearDown(() => database.close());

  test('maps a server document', () async {
    final mapped = NewsMapper.fromDoc({
      '_id': 'server-1',
      '_rev': '1-abc',
      'docType': 'message',
      'message': 'Hello',
      'time': 1200,
      'viewableBy': 'community',
      'user': {'_id': 'user-1', 'name': 'Ada'},
      'labels': ['important'],
      'images': [
        {'markdown': '![](a.png)'},
      ],
      'viewIn': [
        {'_id': 'team-1', 'section': 'teams'},
      ],
      'news': {'_id': 'chat-1', 'title': 'Chat', 'sharedBy': 'Ada'},
    })!;
    await database.newsDao.upsert(mapped);

    final row = (await database.newsDao.getById('server-1'))!;
    expect(row.docId, 'server-1');
    expect(row.rev, '1-abc');
    expect(row.userId, 'user-1');
    expect(row.userName, 'Ada');
    expect(row.labels, ['important']);
    expect(row.viewIn, contains('team-1'));
    expect(row.newsId, 'chat-1');
    expect(row.newsTitle, 'Chat');
    // `buildNewsFromJson` reads `sharedBy` from the *nested* news object even
    // though `createNews` writes it at the top level. Asymmetry preserved.
    expect(row.sharedBy, 'Ada');
  });

  test('a re-sync keeps the local row id and local-only fields', () async {
    // A post composed offline keeps its generated id after upload, so the
    // server copy must update that row rather than forking a second one.
    final existing = NewsRow(
      id: 'local-1',
      docId: 'server-1',
      time: 0,
      updatedDate: 0,
      imageUrls: const ['{"imageUrl":"pending.png"}'],
      labels: const [],
      newsCreatedDate: 0,
      newsUpdatedDate: 0,
      chat: false,
      isEdited: true,
      editedTime: 77,
    );

    final mapped = NewsMapper.fromDoc({
      '_id': 'server-1',
      'docType': 'message',
      'message': 'From the server',
    }, existing: existing)!;
    await database.newsDao.upsert(mapped);

    final row = (await database.newsDao.getById('local-1'))!;
    expect(row.message, 'From the server');
    // These belong to this device: an attachment still awaiting upload and the
    // edit marker that keeps the post queued.
    expect(row.imageUrls, ['{"imageUrl":"pending.png"}']);
    expect(row.isEdited, isTrue);
    expect(row.editedTime, 77);
    expect(await database.newsDao.count(), 1);
  });

  test('rejects design documents and id-less rows', () {
    expect(NewsMapper.fromDoc({'_id': '_design/news'}), isNull);
    expect(NewsMapper.fromDoc(const {}), isNull);
  });
}
