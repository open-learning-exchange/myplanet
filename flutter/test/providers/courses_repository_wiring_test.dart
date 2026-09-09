import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/markdown_image_files.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// Does the **app, as wired**, prefetch a markdown image?
///
/// `CoursesRepository`'s `markdownImages` argument is optional with a null
/// default and the call site is `_markdownImages?.prefetch(...)`, so deleting
/// the argument from `coursesRepositoryProvider` is a silent no-op: analyze
/// stays clean, every prefetch test stays green — because each builds its own
/// repository — and the feature is simply gone from the shipping app. That is
/// the ported-tested-green-and-dead class Phases 113, 116 and 119 exist for,
/// and a dartdoc saying "the provider always supplies one" is a comment where
/// a test is needed. This is the test.
void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late Directory tempDir;
  late Future<Directory> Function() savedBaseDirectory;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';
  const link = 'resources/cover-doc/cover.png';

  setUp(() async {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    tempDir = await Directory.systemTemp.createTemp('courses_wiring');
    savedBaseDirectory = ResourceFiles.baseDirectory;
    ResourceFiles.baseDirectory = () async => tempDir;
  });

  tearDown(() async {
    ResourceFiles.baseDirectory = savedBaseDirectory;
    await db.close();
    if (tempDir.existsSync()) await tempDir.delete(recursive: true);
  });

  test(
    'the wired courses repository pre-downloads a description image',
    () async {
      when(
        () => api.getJsonObject(
          '$dbUrl/courses/_all_docs?limit=0',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({'total_rows': 1}),
      );
      when(
        () => api.getJsonObject(
          '$dbUrl/courses/_all_docs?include_docs=true&limit=50&skip=0',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          'rows': [
            {
              'id': 'course-1',
              'doc': {
                '_id': 'course-1',
                'courseTitle': 'Algebra',
                'description': 'Intro ![cover]($link)',
              },
            },
          ],
        }),
      );
      when(
        () =>
            api.getBytes('$dbUrl/$link', authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => const NetworkSuccess<List<int>>([1, 2, 3]));

      final container = ProviderContainer(
        overrides: [
          appDatabaseProvider.overrideWithValue(db),
          planetApiProvider.overrideWithValue(api),
        ],
      );
      addTearDown(container.dispose);

      await container.read(coursesRepositoryProvider).sync(config: config);

      // Not "a prefetcher was constructed" — the bytes are on disk, found by the
      // renderer's own lookup.
      expect(await MarkdownImageFiles.existingFileFor(link), isNotNull);
    },
  );
}
