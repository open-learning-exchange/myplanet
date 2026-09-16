import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;

import 'package:myplanet/core/files/resource_files.dart';

void main() {
  group('resolveHtmlEntryFile', () {
    late Directory base;

    setUp(() {
      // A temp directory stands in for the resource download root; the resolver
      // only string-compares, so the dir need not be on the real documents path.
      base = Directory(p.join(Directory.systemTemp.path, 'ole_html_test'));
      if (base.existsSync()) base.deleteSync(recursive: true);
      base.createSync(recursive: true);
    });

    tearDown(() {
      if (base.existsSync()) base.deleteSync(recursive: true);
    });

    test('defaults to index.html when relativePath is null', () {
      final resolved = ResourceFiles.resolveHtmlEntryFile(base, null);
      expect(resolved, isNotNull);
      expect(p.basename(resolved!.path), 'index.html');
    });

    test('defaults to index.html when relativePath is blank', () {
      final resolved = ResourceFiles.resolveHtmlEntryFile(base, '   ');
      expect(resolved, isNotNull);
      expect(p.basename(resolved!.path), 'index.html');
    });

    test('resolves a nested entry file within the base directory', () {
      final resolved = ResourceFiles.resolveHtmlEntryFile(
        base,
        'sudoku/index.html',
      );
      expect(resolved, isNotNull);
      expect(resolved!.path, endsWith('sudoku${p.separator}index.html'));
      expect(p.isWithin(base.absolute.path, resolved.path), isTrue);
    });

    test('rejects an absolute path', () {
      expect(ResourceFiles.resolveHtmlEntryFile(base, '/etc/passwd'), isNull);
    });

    test('rejects a backslash-prefixed path', () {
      expect(ResourceFiles.resolveHtmlEntryFile(base, '\\etc\\passwd'), isNull);
    });

    test('rejects a parent-directory traversal', () {
      expect(
        ResourceFiles.resolveHtmlEntryFile(base, '../../../etc/passwd'),
        isNull,
      );
    });

    test('rejects an encoded parent traversal', () {
      expect(
        ResourceFiles.resolveHtmlEntryFile(base, 'sub/../../etc/passwd'),
        isNull,
      );
    });
  });

  group('resourceRelativePathFromUrl', () {
    test('extracts the nested path after /resources/<id>/', () {
      const url =
          'https://planet.example.org/db/resources/doc-1/sudoku/index.html';
      expect(
        ResourceFiles.resourceRelativePathFromUrl(url),
        'sudoku/index.html',
      );
    });

    test('decodes percent-encoded segments', () {
      const url =
          'https://planet.example.org/db/resources/doc-1/my%20folder/page.html';
      expect(
        ResourceFiles.resourceRelativePathFromUrl(url),
        'my folder/page.html',
      );
    });

    test(
      'falls back to the filename when the URL lacks the resource shape',
      () {
        const url = 'https://planet.example.org/files/index.html';
        expect(ResourceFiles.resourceRelativePathFromUrl(url), 'index.html');
      },
    );

    test('returns empty for a null or empty URL', () {
      expect(ResourceFiles.resourceRelativePathFromUrl(null), '');
      expect(ResourceFiles.resourceRelativePathFromUrl(''), '');
    });

    test('falls back to filename when only /resources/<id> is present', () {
      const url = 'https://planet.example.org/db/resources/doc-1';
      expect(ResourceFiles.resourceRelativePathFromUrl(url), 'doc-1');
    });
  });

  group('directoryFor', () {
    test('resolves under <base>/ole/<docId>', () async {
      final original = ResourceFiles.baseDirectory;
      final tmp = await Directory.systemTemp.createTemp('rf_dir_');
      ResourceFiles.baseDirectory = () async => tmp;
      addTearDown(() {
        ResourceFiles.baseDirectory = original;
        tmp.deleteSync(recursive: true);
      });
      final dir = await ResourceFiles.directoryFor(docId: 'abc123');
      expect(dir.path, p.join(tmp.path, 'ole', 'abc123'));
    });

    test('sanitizes a docId that tries to escape', () async {
      final original = ResourceFiles.baseDirectory;
      final tmp = await Directory.systemTemp.createTemp('rf_dir_esc_');
      ResourceFiles.baseDirectory = () async => tmp;
      addTearDown(() {
        ResourceFiles.baseDirectory = original;
        tmp.deleteSync(recursive: true);
      });
      final dir = await ResourceFiles.directoryFor(docId: '../../etc');
      expect(dir.path, p.join(tmp.path, 'ole', 'etc'));
    });
  });

  group('readTextContent', () {
    test('returns null when the attachment is absent', () async {
      final original = ResourceFiles.baseDirectory;
      final tmp = await Directory.systemTemp.createTemp('rf_read_absent_');
      ResourceFiles.baseDirectory = () async => tmp;
      addTearDown(() {
        ResourceFiles.baseDirectory = original;
        tmp.deleteSync(recursive: true);
      });

      final content = await ResourceFiles.readTextContent(
        docId: 'doc1',
        filename: 'notes.txt',
      );
      expect(content, isNull);
    });

    test('reads an attachment that exists', () async {
      final original = ResourceFiles.baseDirectory;
      final tmp = await Directory.systemTemp.createTemp('rf_read_present_');
      ResourceFiles.baseDirectory = () async => tmp;
      final file = await ResourceFiles.fileFor(
        docId: 'doc1',
        filename: 'notes.txt',
      );
      await file.parent.create(recursive: true);
      await file.writeAsString('hello world');
      addTearDown(() {
        ResourceFiles.baseDirectory = original;
        tmp.deleteSync(recursive: true);
      });

      final content = await ResourceFiles.readTextContent(
        docId: 'doc1',
        filename: 'notes.txt',
      );
      expect(content, 'hello world');
    });

    test('returns null for a zero-length file', () async {
      // A failed download leaves an empty file behind; `readTextContent` must
      // not hand the renderer an empty string and call it content.
      final original = ResourceFiles.baseDirectory;
      final tmp = await Directory.systemTemp.createTemp('rf_read_empty_');
      ResourceFiles.baseDirectory = () async => tmp;
      final file = await ResourceFiles.fileFor(
        docId: 'doc1',
        filename: 'notes.txt',
      );
      await file.parent.create(recursive: true);
      await file.writeAsString('');
      addTearDown(() {
        ResourceFiles.baseDirectory = original;
        tmp.deleteSync(recursive: true);
      });

      final content = await ResourceFiles.readTextContent(
        docId: 'doc1',
        filename: 'notes.txt',
      );
      expect(content, isNull);
    });

    test('returns null for undecodable bytes instead of throwing', () async {
      // A binary file behind a .txt name (or a corrupted download) makes
      // `readAsString` throw a FormatException. The renderers call this from
      // `initState` and treat null as "file not found"; an escaping exception
      // there is unhandled and leaves the screen on its spinner forever.
      final original = ResourceFiles.baseDirectory;
      final tmp = await Directory.systemTemp.createTemp('rf_read_binary_');
      ResourceFiles.baseDirectory = () async => tmp;
      final file = await ResourceFiles.fileFor(
        docId: 'doc1',
        filename: 'notes.txt',
      );
      await file.parent.create(recursive: true);
      // 0xC3 opens a two-byte UTF-8 sequence; 0x28 cannot continue it.
      await file.writeAsBytes([0xC3, 0x28, 0xFF, 0xFE]);
      addTearDown(() {
        ResourceFiles.baseDirectory = original;
        tmp.deleteSync(recursive: true);
      });

      final content = await ResourceFiles.readTextContent(
        docId: 'doc1',
        filename: 'notes.txt',
      );
      expect(content, isNull);
    });
  });
}
