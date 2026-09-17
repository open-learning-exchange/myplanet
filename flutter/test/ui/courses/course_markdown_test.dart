import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/markdown_image_files.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/ui/courses/course_markdown.dart';

/// The renderer half of Phase 153.
///
/// Note the trap these overrides exist for: `markdownImageFileProvider` is
/// real `dart:io`, whose futures never complete in a widget test's fake-async
/// zone — pumping on it looks exactly like a hang. Every test here overrides
/// it, so nothing touches the filesystem.
class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);

  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}

void main() {
  group('markdownImageFileProvider re-reads the disk', () {
    late Directory tempDir;
    late Future<Directory> Function() savedBaseDirectory;
    const link = 'resources/abc123/chart.png';

    setUp(() async {
      tempDir = await Directory.systemTemp.createTemp('markdown_provider');
      savedBaseDirectory = ResourceFiles.baseDirectory;
      ResourceFiles.baseDirectory = () async => tempDir;
    });

    tearDown(() async {
      ResourceFiles.baseDirectory = savedBaseDirectory;
      if (tempDir.existsSync()) await tempDir.delete(recursive: true);
    });

    test('a miss is not cached for the life of the container', () async {
      // The scenario: the user opens a course while the sync is still walking
      // `courses`, so the first lookup misses; the sync writes the file a
      // moment later; the user goes offline. Without autoDispose the null is
      // cached for the whole process and the image never renders from disk
      // again — the feature not working, in the situation it exists for.
      final container = ProviderContainer(retry: noProviderRetry);
      addTearDown(container.dispose);

      expect(
        await container.read(markdownImageFileProvider(link).future),
        isNull,
      );

      final file = (await MarkdownImageFiles.fileFor(link))!;
      await file.parent.create(recursive: true);
      await file.writeAsBytes(const [1, 2, 3]);

      expect(
        await container.read(markdownImageFileProvider(link).future),
        file.path,
      );
    });
  });

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );
  const dbUrl = 'https://satellite:1234@planet.example:443/db';
  const link = 'resources/abc123/chart.png';
  const markdown = 'Before ![chart](resources/abc123/chart.png) after';

  Future<void> pumpBody(
    WidgetTester tester, {
    required String? localPath,
    Uint8List? bytes,
  }) async {
    await tester.pumpWidget(
      ProviderScope(
        retry: noProviderRetry,
        overrides: [
          serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
          markdownImageFileProvider.overrideWith((ref, _) async => localPath),
          markdownImageBytesProvider.overrideWith((ref, _) async => bytes),
        ],
        child: const MaterialApp(
          home: Scaffold(body: CourseMarkdownBody(data: markdown)),
        ),
      ),
    );
    await tester.pump();
  }

  Finder fileImageAt(String path) => find.byWidgetPredicate(
    (w) =>
        w is Image &&
        w.image is FileImage &&
        (w.image as FileImage).file.path == path,
  );

  final memoryImage = find.byWidgetPredicate(
    (w) => w is Image && w.image is MemoryImage,
  );

  testWidgets('renders the pre-downloaded copy when one is on disk', (
    tester,
  ) async {
    // The offline case, and the whole point of the prefetch: no network is
    // consulted at all.
    await pumpBody(tester, localPath: '/cache/ole/abc123/chart.png');

    expect(fileImageAt('/cache/ole/abc123/chart.png'), findsOneWidget);
    expect(memoryImage, findsNothing);
  });

  testWidgets('falls back to the authenticated fetch with no local copy', (
    tester,
  ) async {
    await pumpBody(
      tester,
      localPath: null,
      bytes: Uint8List.fromList(const [1, 2, 3]),
    );

    expect(
      find.byWidgetPredicate((w) => w is Image && w.image is FileImage),
      findsNothing,
    );
    expect(memoryImage, findsOneWidget);
  });

  testWidgets('falls back while the local lookup is still in flight', (
    tester,
  ) async {
    // Loading must not render nothing: an online device would flash an empty
    // gap on every rebuild.
    await tester.pumpWidget(
      ProviderScope(
        retry: noProviderRetry,
        overrides: [
          serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
          markdownImageFileProvider.overrideWith(
            (ref, _) => Completer<String?>().future,
          ),
          markdownImageBytesProvider.overrideWith(
            (ref, _) async => Uint8List.fromList(const [1, 2, 3]),
          ),
        ],
        child: const MaterialApp(
          home: Scaffold(body: CourseMarkdownBody(data: markdown)),
        ),
      ),
    );
    await tester.pump();

    expect(memoryImage, findsOneWidget);
  });

  testWidgets('resolves the fallback URL against the server /db root', (
    tester,
  ) async {
    final requested = <String>[];
    await tester.pumpWidget(
      ProviderScope(
        retry: noProviderRetry,
        overrides: [
          serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
          markdownImageFileProvider.overrideWith((ref, _) async => null),
          markdownImageBytesProvider.overrideWith((ref, url) async {
            requested.add(url);
            return Uint8List.fromList(const [1]);
          }),
        ],
        child: const MaterialApp(
          home: Scaffold(body: CourseMarkdownBody(data: markdown)),
        ),
      ),
    );
    await tester.pump();

    expect(requested, ['$dbUrl/$link']);
  });

  testWidgets('an absolute image URL never consults the local cache', (
    tester,
  ) async {
    // markdownImageCachePath declines these, so the lookup would return null
    // anyway; asserting the provider is not even read pins the branch order.
    var lookups = 0;
    await tester.pumpWidget(
      ProviderScope(
        retry: noProviderRetry,
        overrides: [
          serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
          markdownImageFileProvider.overrideWith((ref, _) async {
            lookups++;
            return null;
          }),
          markdownImageBytesProvider.overrideWith(
            (ref, _) async => Uint8List.fromList(const [1]),
          ),
        ],
        child: const MaterialApp(
          home: Scaffold(
            body: CourseMarkdownBody(
              data: '![x](https://planet.example/db/resources/a/b.png)',
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(lookups, 0);
  });
}
