import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/tags_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late TagsRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = TagsRepository(api, db.tagDao);
  });

  tearDown(() => db.close());

  Map<String, dynamic> definition(String id, String name, {String? db}) => {
    '_id': id,
    '_rev': '1-x',
    'name': name,
    'db': ?db,
  };

  Map<String, dynamic> child(String id, String name, String parentId) => {
    '_id': id,
    'name': name,
    'attachedTo': <dynamic>[parentId],
  };

  Map<String, dynamic> link(String db, String linkId, String tagId) => {
    '_id': 'link-$db-$linkId-$tagId',
    'db': db,
    'linkId': linkId,
    'tagId': tagId,
  };

  void stubCount(int totalRows) {
    when(
      () => api.getJsonObject(
        '$dbUrl/tags/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': totalRows}),
    );
  }

  void stubPage(int skip, int limit, List<Map<String, dynamic>> rows) {
    when(
      () => api.getJsonObject(
        '$dbUrl/tags/_all_docs?include_docs=true&limit=$limit&skip=$skip',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rows': rows}),
    );
  }

  group('insert', () {
    test(
      'skips design docs and maps definitions, children and links',
      () async {
        await repository.insertDocs([
          {'_id': '_design/foo', 'name': 'design'},
          definition('tag-math', 'Math'),
          child('tag-algebra', 'Algebra', 'tag-math'),
          link('resources', 'r1', 'tag-math'),
        ]);

        final rows = await db.tagDao.allTags();
        expect(rows, hasLength(3));

        final math = rows.singleWhere((r) => r.id == 'tag-math');
        expect(math.isAttached, isFalse);
        expect(math.attachedTo, isEmpty);

        final algebra = rows.singleWhere((r) => r.id == 'tag-algebra');
        expect(algebra.isAttached, isTrue);
        expect(algebra.attachedTo, ['tag-math']);

        final l = rows.singleWhere((r) => r.id == 'link-resources-r1-tag-math');
        expect(l.linkId, 'r1');
        expect(l.tagId, 'tag-math');
        expect(l.db, 'resources');
      },
    );

    test('accepts a bare string in attachedTo', () async {
      await repository.insertDocs([
        {'_id': 'tag-kid', 'name': 'Kid', 'attachedTo': 'tag-math'},
      ]);

      final row = (await db.tagDao.allTags()).single;
      expect(row.isAttached, isTrue);
      expect(row.attachedTo, ['tag-math']);
    });
  });

  group('queries', () {
    setUp(() async {
      await repository.insertDocs([
        definition('tag-math', 'Math', db: 'resources'),
        definition('tag-science', 'Science', db: 'resources'),
        child('tag-algebra', 'Algebra', 'tag-math'),
      ]);
    });

    test('getTagsWithChildren groups children under their parents', () async {
      final tree = await repository.getTagsWithChildren('resources');

      expect(tree.parents.map((p) => p.name), containsAll(['Math', 'Science']));
      expect(tree.children['tag-math']!.single.name, 'Algebra');
      expect(tree.children['tag-science'], isEmpty);

      // A null dbType is the Kotlin's "all definitions" case; a different
      // dbType excludes rows scoped to another one.
      expect(
        (await repository.getTagsWithChildren(null)).parents,
        hasLength(2),
      );
      expect(
        (await repository.getTagsWithChildren('courses')).parents,
        isEmpty,
      );
    });

    test('getTagsForResources joins link rows to their named tags', () async {
      await repository.insertDocs([
        link('resources', 'r1', 'tag-math'),
        link('resources', 'r1', 'tag-science'),
        link('resources', 'r2', 'tag-math'),
      ]);

      final tags = await repository.getTagsForResources(['r1', 'r2']);

      expect(tags['r1']!.map((t) => t.name), containsAll(['Math', 'Science']));
      expect(tags['r2']!.single.name, 'Math');
    });

    test(
      'getTagsForResources dedupes a tag carried twice by one resource',
      () async {
        await repository.insertDocs([
          link('resources', 'r1', 'tag-math'),
          link('resources', 'r1', 'tag-math'),
        ]);

        expect(
          (await repository.getTagsForResources(['r1']))['r1'],
          hasLength(1),
        );
      },
    );

    test(
      'getLinkIdsForTagNames resolves link ids behind the tag names',
      () async {
        await repository.insertDocs([
          link('courses', 'c1', 'tag-math'),
          link('courses', 'c2', 'tag-science'),
        ]);

        final ids = await repository.getLinkIdsForTagNames('courses', ['Math']);

        expect(ids, ['c1']);
        expect(
          await repository.getLinkIdsForTagNames('courses', [
            'Math',
            'Science',
          ]),
          containsAll(['c1', 'c2']),
        );
        // Kotlin filters courses with this set; an unknown tag yields nothing.
        expect(
          await repository.getLinkIdsForTagNames('courses', ['Unknown']),
          isEmpty,
        );
        expect(await repository.getLinkIdsForTagNames('courses', []), isEmpty);
      },
    );
  });

  group('sync', () {
    test('walks pages, upserts, and prunes rows the server dropped', () async {
      await repository.insertDocs([
        definition('tag-stale', 'Stale'),
        definition('tag-math', 'Math'),
      ]);

      stubCount(1);
      stubPage(0, 100, [
        {'doc': definition('tag-math', 'Math')},
      ]);

      final result = await repository.sync(config: config);

      expect(result, isA<SyncComplete>());
      final rows = await db.tagDao.allTags();
      expect(rows.map((r) => r.id), ['tag-math']);
    });

    test('on an empty server prunes the cached rows', () async {
      await repository.insertDocs([definition('tag-stale', 'Stale')]);
      stubCount(0);

      final result = await repository.sync(config: config);

      expect(result, isA<SyncComplete>());
      expect(await db.tagDao.allTags(), isEmpty);
    });

    test('failed count lookup returns SyncFailed', () async {
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkException<Map<String, dynamic>>(Exception('boom')),
      );

      final result = await repository.sync(config: config);

      expect(result, isA<SyncFailed>());
    });

    test('a failed batch skips the cleanup so valid rows survive', () async {
      await repository.insertDocs([definition('tag-stale', 'Stale')]);

      stubCount(105);
      stubPage(
        0,
        100,
        List.generate(100, (i) => {'doc': definition('tag-$i', 'T-$i')}),
      );
      when(
        () => api.getJsonObject(
          '$dbUrl/tags/_all_docs?include_docs=true&limit=100&skip=100',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkException<Map<String, dynamic>>(Exception('boom')),
      );

      final result = await repository.sync(config: config);

      expect(result, isA<SyncComplete>());
      // The incomplete walk must not delete what it could not see: the 100
      // walked rows and the stale row from before the sync all survive.
      final rows = await db.tagDao.allTags();
      expect(rows, hasLength(101));
      expect(rows.any((r) => r.id == 'tag-stale'), isTrue);
    });
  });
}
