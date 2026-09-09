import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/utils/markdown_links.dart';

void main() {
  group('extractImageLinks', () {
    test('returns an empty list for null or blank input', () {
      expect(extractImageLinks(null), isEmpty);
      expect(extractImageLinks(''), isEmpty);
    });

    test('collects the path from each ![alt](path) span', () {
      const markdown = 'See ![one](img/a.png) and ![two](img/b.jpg).';
      expect(extractImageLinks(markdown), ['img/a.png', 'img/b.jpg']);
    });

    test('preserves a leading resources/ prefix', () {
      // extractLinks is the raw collector; only prependBaseUrlToImages
      // strips the prefix. The sync download uses these paths verbatim.
      expect(extractImageLinks('![] (resources/c/cover.jpg)'), isEmpty);
      expect(extractImageLinks('![](resources/c/cover.jpg)'), [
        'resources/c/cover.jpg',
      ]);
    });

    test('drops empty captures', () {
      expect(extractImageLinks('![]()'), isEmpty);
      expect(extractImageLinks('![]( )'), [' ']);
    });

    test('leaves non-image markdown alone', () {
      const markdown = '# Title\n[link](http://x) and **bold**';
      expect(extractImageLinks(markdown), isEmpty);
    });
  });

  group('prependBaseUrlToImages', () {
    test('returns the empty string for null', () {
      expect(prependBaseUrlToImages(null, 'file:///ole/'), '');
    });

    test('rewrites each image to an <img> with the base prepended', () {
      const markdown = '![](img/a.png)';
      expect(
        prependBaseUrlToImages(markdown, 'file:///ole/'),
        '<img src="file:///ole/img/a.png" width=150 height=100/>',
      );
    });

    test('honours the width/height arguments', () {
      const markdown = '![](img/a.png)';
      expect(
        prependBaseUrlToImages(
          markdown,
          'file:///ole/',
          width: 600,
          height: 350,
        ),
        '<img src="file:///ole/img/a.png" width=600 height=350/>',
      );
    });

    test('strips a leading resources/ so the base does not double it', () {
      const markdown = '![](resources/c/cover.jpg)';
      expect(
        prependBaseUrlToImages(markdown, 'file:///ole/'),
        '<img src="file:///ole/c/cover.jpg" width=150 height=100/>',
      );
    });

    // Upstream `9e402fb` added the quotes precisely so a path containing a
    // space cannot run into the following `width=` attribute.
    test('quotes the src so a path with spaces stays one attribute', () {
      const markdown = '![alt text](my image filename.png)';
      expect(
        prependBaseUrlToImages(markdown, 'file:///ole/'),
        '<img src="file:///ole/my image filename.png" width=150 height=100/>',
      );
    });

    test('leaves non-image text unchanged', () {
      const markdown = '# Title\nplain text and [a](b)';
      expect(prependBaseUrlToImages(markdown, 'file:///ole/'), markdown);
    });

    test('rewrites multiple images in one block', () {
      const markdown = '![](a.png)![](b.png)';
      expect(
        prependBaseUrlToImages(markdown, 'file:///ole/'),
        '<img src="file:///ole/a.png" width=150 height=100/>'
        '<img src="file:///ole/b.png" width=150 height=100/>',
      );
    });
  });

  group('markdownImageCachePath', () {
    test('strips a leading resources/ and keeps the id and file segments', () {
      expect(
        markdownImageCachePath('resources/abc123/chart.png'),
        'abc123/chart.png',
      );
    });

    test('accepts a link that already omits the resources/ marker', () {
      expect(markdownImageCachePath('abc123/chart.png'), 'abc123/chart.png');
    });

    test('keeps a nested path under the id directory', () {
      // A multi-file attachment bundle keeps its subfolder, the same reason
      // ResourceFiles.resourceRelativePathFromUrl does not flatten one.
      expect(
        markdownImageCachePath('resources/abc123/figures/chart.png'),
        'abc123/figures/chart.png',
      );
    });

    test('declines an absolute URL so the network path keeps handling it', () {
      // The Kotlin has no such guard: it queues "<serverUrl>/http://..." for
      // download and renders "file://.../ole/http://...", neither of which
      // resolves. _MarkdownImage already fetches these correctly.
      expect(markdownImageCachePath('https://cdn.example/a.png'), isNull);
      expect(markdownImageCachePath('http://cdn.example/a.png'), isNull);
      expect(markdownImageCachePath('file:///tmp/a.png'), isNull);
    });

    test('declines a link that could escape the cache directory', () {
      expect(markdownImageCachePath('resources/../../etc/passwd'), isNull);
      expect(markdownImageCachePath('../secrets/key.png'), isNull);
      expect(markdownImageCachePath('/etc/passwd'), isNull);
      expect(markdownImageCachePath(r'abc\..\x.png'), isNull);
    });

    test('declines a link with no id segment', () {
      // <base>/ole/cover.jpg with no id directory is how two unrelated
      // cover.jpg attachments overwrite each other.
      expect(markdownImageCachePath('cover.jpg'), isNull);
      expect(markdownImageCachePath('resources/cover.jpg'), isNull);
    });

    test('declines an empty or blank link', () {
      expect(markdownImageCachePath(''), isNull);
      expect(markdownImageCachePath('   '), isNull);
    });

    test('declines a link with an empty interior segment', () {
      expect(markdownImageCachePath('abc//chart.png'), isNull);
    });
  });
}
