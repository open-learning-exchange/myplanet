import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/files/markdown_image_files.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:path/path.dart' as p;

void main() {
  late Directory tempDir;
  late Future<Directory> Function() savedBaseDirectory;

  setUp(() async {
    savedBaseDirectory = ResourceFiles.baseDirectory;
    tempDir = await Directory.systemTemp.createTemp('markdown_image_files');
    ResourceFiles.baseDirectory = () async => tempDir;
  });

  tearDown(() async {
    ResourceFiles.baseDirectory = savedBaseDirectory;
    if (tempDir.existsSync()) await tempDir.delete(recursive: true);
  });

  test(
    'resolves a link to the same <base>/ole/<id>/<file> a resource uses',
    () async {
      final markdown = await MarkdownImageFiles.fileFor(
        'resources/abc123/chart.png',
      );
      final resource = await ResourceFiles.fileFor(
        docId: 'abc123',
        filename: 'chart.png',
      );
      // The two must agree: a markdown image *is* a resource attachment reached
      // by path instead of by my_library row, and the whole point of routing
      // both through ResourceFiles.baseDirectory is that they cannot drift.
      expect(markdown, isNotNull);
      expect(markdown!.path, resource.path);
      expect(markdown.path, p.join(tempDir.path, 'ole', 'abc123', 'chart.png'));
    },
  );

  test('returns null for a link that names nothing local', () async {
    expect(
      await MarkdownImageFiles.fileFor('https://cdn.example/a.png'),
      isNull,
    );
    expect(
      await MarkdownImageFiles.fileFor('resources/../../escape.png'),
      isNull,
    );
  });

  test('keeps every resolved file inside the ole directory', () async {
    for (final link in <String>[
      'resources/abc/chart.png',
      'abc/def/ghi/chart.png',
    ]) {
      final file = await MarkdownImageFiles.fileFor(link);
      expect(file, isNotNull, reason: link);
      expect(
        p.isWithin(p.join(tempDir.path, 'ole'), file!.path),
        isTrue,
        reason: link,
      );
    }
  });

  group('existingFileFor', () {
    test('returns null when nothing has been downloaded', () async {
      expect(
        await MarkdownImageFiles.existingFileFor('resources/abc/chart.png'),
        isNull,
      );
    });

    test('returns the file once it holds bytes', () async {
      final file = (await MarkdownImageFiles.fileFor(
        'resources/abc/chart.png',
      ))!;
      await file.parent.create(recursive: true);
      await file.writeAsBytes(const [1, 2, 3]);
      final found = await MarkdownImageFiles.existingFileFor(
        'resources/abc/chart.png',
      );
      expect(found, isNotNull);
      expect(found!.path, file.path);
    });

    test(
      'rejects a zero-length file an interrupted download left behind',
      () async {
        // exists() alone would report this as present and the renderer would
        // prefer an empty local copy over the network fetch that still works.
        final file = (await MarkdownImageFiles.fileFor(
          'resources/abc/chart.png',
        ))!;
        await file.parent.create(recursive: true);
        await file.writeAsBytes(const []);
        expect(
          await MarkdownImageFiles.existingFileFor('resources/abc/chart.png'),
          isNull,
        );
      },
    );
  });
}
