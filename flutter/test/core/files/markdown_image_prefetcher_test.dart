import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/markdown_image_files.dart';
import 'package:myplanet/core/files/markdown_image_prefetcher.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late MockPlanetApi api;
  late MarkdownImagePrefetcher prefetcher;
  late Directory tempDir;
  late Future<Directory> Function() savedBaseDirectory;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );
  const dbUrl = 'https://satellite:1234@planet.example:443/db';

  setUp(() async {
    api = MockPlanetApi();
    prefetcher = MarkdownImagePrefetcher(api);
    tempDir = await Directory.systemTemp.createTemp('markdown_prefetch');
    savedBaseDirectory = ResourceFiles.baseDirectory;
    ResourceFiles.baseDirectory = () async => tempDir;
  });

  tearDown(() async {
    ResourceFiles.baseDirectory = savedBaseDirectory;
    if (tempDir.existsSync()) await tempDir.delete(recursive: true);
  });

  void stubBytes(String url, NetworkResult<List<int>> result) {
    when(
      () => api.getBytes(url, authHeader: any(named: 'authHeader')),
    ).thenAnswer((_) async => result);
  }

  test('downloads a link and writes it where the renderer looks', () async {
    stubBytes(
      '$dbUrl/resources/abc123/chart.png',
      const NetworkSuccess<List<int>>([1, 2, 3]),
    );

    final written = await prefetcher.prefetch(const [
      'resources/abc123/chart.png',
    ], config: config);

    expect(written, 1);
    // The assertion that matters is not "a file exists" but "the file the
    // reader's own lookup finds" — the Phase 100 shape, where the writer and
    // reader derived different keys and the miss was silent.
    final found = await MarkdownImageFiles.existingFileFor(
      'resources/abc123/chart.png',
    );
    expect(found, isNotNull);
    expect(await found!.readAsBytes(), const [1, 2, 3]);
  });

  test('keeps the resources/ prefix on the download URL', () async {
    // The download URL and the on-disk path deliberately disagree: Kotlin
    // queues "$baseUrl/$link" from the unstripped link
    // (CoursesRepositoryImpl:671) while the render base strips the marker.
    stubBytes(
      '$dbUrl/resources/abc123/chart.png',
      const NetworkSuccess<List<int>>([9]),
    );
    await prefetcher.prefetch(const [
      'resources/abc123/chart.png',
    ], config: config);
    verify(
      () => api.getBytes(
        '$dbUrl/resources/abc123/chart.png',
        authHeader: any(named: 'authHeader'),
      ),
    ).called(1);
  });

  test('skips a link already on disk', () async {
    final file = (await MarkdownImageFiles.fileFor('resources/abc/c.png'))!;
    await file.parent.create(recursive: true);
    await file.writeAsBytes(const [7, 7]);

    final written = await prefetcher.prefetch(const [
      'resources/abc/c.png',
    ], config: config);

    expect(written, 0);
    verifyNever(
      () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
    );
    expect(await file.readAsBytes(), const [7, 7]);
  });

  test(
    're-downloads a zero-length leftover rather than treating it as done',
    () async {
      final file = (await MarkdownImageFiles.fileFor('resources/abc/c.png'))!;
      await file.parent.create(recursive: true);
      await file.writeAsBytes(const []);
      stubBytes(
        '$dbUrl/resources/abc/c.png',
        const NetworkSuccess<List<int>>([4]),
      );

      expect(
        await prefetcher.prefetch(const [
          'resources/abc/c.png',
        ], config: config),
        1,
      );
      expect(await file.readAsBytes(), const [4]);
    },
  );

  test('writes nothing for an empty response body', () async {
    // A success with no bytes would otherwise create a file that exists,
    // renders nothing, and is never retried.
    stubBytes(
      '$dbUrl/resources/abc/c.png',
      const NetworkSuccess<List<int>>([]),
    );

    expect(
      await prefetcher.prefetch(const ['resources/abc/c.png'], config: config),
      0,
    );
    expect(
      await MarkdownImageFiles.existingFileFor('resources/abc/c.png'),
      isNull,
    );
  });

  test('swallows a failed download and keeps going', () async {
    stubBytes(
      '$dbUrl/resources/a/one.png',
      const NetworkError<List<int>>(500, 'boom'),
    );
    stubBytes(
      '$dbUrl/resources/b/two.png',
      const NetworkSuccess<List<int>>([2]),
    );

    final written = await prefetcher.prefetch(const [
      'resources/a/one.png',
      'resources/b/two.png',
    ], config: config);

    // A missing image must not stop the rest, and must not fail the sync that
    // has already written the documents.
    expect(written, 1);
    expect(
      await MarkdownImageFiles.existingFileFor('resources/b/two.png'),
      isNotNull,
    );
  });

  test('stops requesting once the budget is spent', () async {
    // The prefetch runs inside a sync now, and a first sync on a fresh
    // install is not the steady state the skip-if-present check makes cheap.
    // WorkManager kills the background task at around ten minutes; Kotlin
    // needs no budget because it hands the queue to a foreground service.
    final bounded = MarkdownImagePrefetcher(api, budget: Duration.zero);

    expect(
      await bounded.prefetch(const ['resources/abc/c.png'], config: config),
      0,
    );
    verifyNever(
      () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
    );
  });

  test(
    'abandons a link whose download stalls past the per-link timeout',
    () async {
      // PlanetApi.getBytes sets receiveTimeout: null — right for a large
      // user-initiated download, an unbounded stall inside a sync, where
      // SyncNotifier refuses a second attempt while one is running so the
      // button never re-enables.
      final quick = MarkdownImagePrefetcher(
        api,
        perLinkTimeout: const Duration(milliseconds: 30),
      );
      when(
        () => api.getBytes(
          '$dbUrl/resources/abc/c.png',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) => Future<NetworkResult<List<int>>>.delayed(
          const Duration(seconds: 30),
          () => const NetworkSuccess<List<int>>([1]),
        ),
      );

      expect(
        await quick.prefetch(const ['resources/abc/c.png'], config: config),
        0,
      );
      expect(
        await MarkdownImageFiles.existingFileFor('resources/abc/c.png'),
        isNull,
      );
    },
  );

  test('never requests a link that names nothing local', () async {
    final written = await prefetcher.prefetch(const [
      'https://cdn.example/a.png',
      'resources/../../escape.png',
      'resources/%2e%2e/escape.png',
    ], config: config);

    expect(written, 0);
    verifyNever(
      () => api.getBytes(any(), authHeader: any(named: 'authHeader')),
    );
  });
}
