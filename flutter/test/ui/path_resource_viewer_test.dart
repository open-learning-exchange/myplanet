import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/ui/viewer/path_resource_viewer_screen.dart';

/// Pins `pathResourceType` to the Kotlin `PersonalsAdapter.openResource`
/// switch (#16070): pdf → pdf viewer; bmp/gif/jpg/png/webp → image;
/// aac/mp3 → audio; mp4 → video; everything else → unsupported (no-op).
void main() {
  group('pathResourceType', () {
    test('pdf extension', () {
      expect(pathResourceType('/a/b/note.pdf'), PathResourceType.pdf);
    });

    test('image extensions route to image', () {
      for (final ext in ['bmp', 'gif', 'jpg', 'jpeg', 'png', 'webp']) {
        expect(
          pathResourceType('/a/b/photo.$ext'),
          PathResourceType.image,
          reason: ext,
        );
      }
    });

    test('audio extensions route to audio', () {
      expect(pathResourceType('/a/b/clip.aac'), PathResourceType.audio);
      expect(pathResourceType('/a/b/clip.mp3'), PathResourceType.audio);
    });

    test('mp4 routes to video', () {
      expect(pathResourceType('/a/b/vid.mp4'), PathResourceType.video);
    });

    test('unknown extension is unsupported', () {
      expect(pathResourceType('/a/b/doc.docx'), PathResourceType.unsupported);
    });

    test('no extension is unsupported', () {
      expect(pathResourceType('/a/b/noext'), PathResourceType.unsupported);
    });

    test('trailing dot is unsupported', () {
      expect(pathResourceType('/a/b/edge.'), PathResourceType.unsupported);
    });

    test('extension is case-insensitive', () {
      expect(pathResourceType('/a/b/file.PDF'), PathResourceType.pdf);
      expect(pathResourceType('/a/b/photo.PNG'), PathResourceType.image);
      expect(pathResourceType('/a/b/clip.MP3'), PathResourceType.audio);
      expect(pathResourceType('/a/b/vid.MP4'), PathResourceType.video);
    });
  });
}
