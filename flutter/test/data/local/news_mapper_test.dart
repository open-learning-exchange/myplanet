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

  test('reads reactions from the nested news object, where they are sent', () {
    // `serializeNews` writes reactions inside the `news` sub-object. Reading
    // them from the top level meant a reaction uploaded by one device was never
    // visible to another.
    final mapped = NewsMapper.fromDoc({
      '_id': 'server-1',
      'docType': 'message',
      'message': 'Hello',
      'news': {'_id': 'chat-1', 'reactions': '{"\u{1F44D}":["user-1"]}'},
    })!;

    expect(mapped.reactions.value, '{"\u{1F44D}":["user-1"]}');
  });

  test('a re-pull keeps the reaction the server sent', () async {
    // Before the fix this was the visible symptom: the mapper writes its
    // companion on every pull, so a document whose reactions it could not find
    // wrote null over the local column and the reaction disappeared on the
    // user's next sync — not merely failing to propagate, but erasing.
    for (final message in ['Hello', 'Hello edited']) {
      await database.newsDao.upsert(
        NewsMapper.fromDoc({
          '_id': 'server-1',
          'docType': 'message',
          'message': message,
          'news': {'_id': 'chat-1', 'reactions': '{"\u{1F44D}":["user-1"]}'},
        })!,
      );
    }

    final row = (await database.newsDao.getById('server-1'))!;
    expect(row.message, 'Hello edited');
    expect(row.reactions, '{"\u{1F44D}":["user-1"]}');
  });

  test('a pull with no reactions still clears the column', () async {
    // Pins the behaviour that was *not* changed, so the trade-off is visible
    // rather than discovered later. `serializeNews` omits `reactions` when the
    // column is empty, so an absent key cannot be told apart from "no
    // reactions". Preserving on absence (the `Value.absent()` trick used for
    // security data in Phase 56) would therefore stop a *removal* from ever
    // reaching another device. Clearing keeps removals working, at the cost of
    // a pull that lands between a local reaction and its upload discarding it.
    // Resolving it properly means always writing the key so absence is
    // unambiguous — a wire-format change, and reactions are not in the shipping
    // Kotlin app to agree with yet.
    await database.newsDao.upsert(
      NewsMapper.fromDoc({
        '_id': 'server-1',
        'docType': 'message',
        'message': 'Hello',
        'news': {'_id': 'chat-1', 'reactions': '{"\u{1F44D}":["user-1"]}'},
      })!,
    );

    await database.newsDao.upsert(
      NewsMapper.fromDoc({
        '_id': 'server-1',
        'docType': 'message',
        'message': 'Hello',
        'news': {'_id': 'chat-1'},
      })!,
    );

    expect((await database.newsDao.getById('server-1'))!.reactions, isNull);
  });
}
