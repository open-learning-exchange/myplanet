import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/markdown_image_files.dart';
import 'package:myplanet/core/files/markdown_image_prefetcher.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/courses_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// The join, end to end: a **server-shaped course document** goes through the
/// real sync walk and the real prefetcher, and the file that comes out is
/// found by the *renderer's own lookup*.
///
/// Not a fixture that hand-places a file where the renderer computes it —
/// that is the fabricated join this project keeps catching (Phase 113's
/// unreachable exam screen, Phase 100's verification photo). If the collector,
/// the writer and the reader are only ever tested apart, a disagreement
/// between the key one writes and the key another reads passes every suite and
/// fails on every device.
void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late CoursesRepository repository;
  late Directory tempDir;
  late Future<Directory> Function() savedBaseDirectory;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() async {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = CoursesRepository(
      api,
      db.courseDao,
      db.removedLogDao,
      db.examDao,
      db.surveyDao,
      markdownImages: MarkdownImagePrefetcher(api),
    );
    tempDir = await Directory.systemTemp.createTemp('markdown_round_trip');
    savedBaseDirectory = ResourceFiles.baseDirectory;
    ResourceFiles.baseDirectory = () async => tempDir;
  });

  tearDown(() async {
    ResourceFiles.baseDirectory = savedBaseDirectory;
    await db.close();
    if (tempDir.existsSync()) await tempDir.delete(recursive: true);
  });

  void stubWalk(List<Map<String, dynamic>> docs) {
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': docs.length}),
    );
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?include_docs=true&limit=50&skip=0',
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

  /// The lookup `markdownImageFileProvider` performs, driven with the link the
  /// renderer would hand it — `Uri.parse(...).toString()` is what
  /// `flutter_markdown`'s `imageBuilder` produces from the same span.
  Future<File?> asTheRendererLooksItUp(String link) =>
      MarkdownImageFiles.existingFileFor(Uri.parse(link).toString());

  test(
    'a synced course description image is on disk where the renderer looks',
    () async {
      const link = 'resources/cover-doc/cover.png';
      stubWalk([
        {
          '_id': 'course-1',
          'courseTitle': 'Algebra',
          'description': 'Intro ![cover]($link)',
        },
      ]);
      when(
        () =>
            api.getBytes('$dbUrl/$link', authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => const NetworkSuccess<List<int>>([1, 2, 3, 4]));

      await repository.sync(config: config);

      final found = await asTheRendererLooksItUp(link);
      expect(
        found,
        isNotNull,
        reason: 'the renderer cannot find what sync wrote',
      );
      expect(await found!.readAsBytes(), const [1, 2, 3, 4]);
    },
  );

  test("a synced step description's image makes the same round trip", () async {
    const link = 'resources/step-doc/diagram.png';
    stubWalk([
      {
        '_id': 'course-1',
        'courseTitle': 'Algebra',
        'steps': [
          {'stepTitle': 'One', 'description': 'See ![d]($link)'},
        ],
      },
    ]);
    when(
      () => api.getBytes('$dbUrl/$link', authHeader: any(named: 'authHeader')),
    ).thenAnswer((_) async => const NetworkSuccess<List<int>>([9]));

    await repository.sync(config: config);

    expect(await asTheRendererLooksItUp(link), isNotNull);
  });

  test(
    'an encoded link and its literal-space twin resolve to one file',
    () async {
      // The Kotlin's download side decodes each segment and its render side does
      // not, so these two disagree there. One derivation serves both here.
      const encoded = 'resources/doc/my%20chart.png';
      stubWalk([
        {
          '_id': 'course-1',
          'courseTitle': 'Algebra',
          'description': '![c]($encoded)',
        },
      ]);
      when(
        () => api.getBytes(
          '$dbUrl/$encoded',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async => const NetworkSuccess<List<int>>([5]));

      await repository.sync(config: config);

      expect(await asTheRendererLooksItUp(encoded), isNotNull);
      expect(
        await MarkdownImageFiles.existingFileFor('resources/doc/my chart.png'),
        isNotNull,
      );
    },
  );

  test(
    'a failed image download leaves nothing for the renderer to prefer',
    () async {
      // The renderer must fall through to its authenticated fetch rather than
      // find a zero-length file and render an empty box.
      const link = 'resources/doc/missing.png';
      stubWalk([
        {
          '_id': 'course-1',
          'courseTitle': 'Algebra',
          'description': '![m]($link)',
        },
      ]);
      when(
        () =>
            api.getBytes('$dbUrl/$link', authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => const NetworkError<List<int>>(404, 'gone'));

      expect(await repository.sync(config: config), isA<SyncComplete>());
      expect(await asTheRendererLooksItUp(link), isNull);
    },
  );
}
