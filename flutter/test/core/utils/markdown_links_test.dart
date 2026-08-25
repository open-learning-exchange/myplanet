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
        '<img src=file:///ole/img/a.png width=150 height=100/>',
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
        '<img src=file:///ole/img/a.png width=600 height=350/>',
      );
    });

    test('strips a leading resources/ so the base does not double it', () {
      const markdown = '![](resources/c/cover.jpg)';
      expect(
        prependBaseUrlToImages(markdown, 'file:///ole/'),
        '<img src=file:///ole/c/cover.jpg width=150 height=100/>',
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
        '<img src=file:///ole/a.png width=150 height=100/>'
        '<img src=file:///ole/b.png width=150 height=100/>',
      );
    });
  });
}
