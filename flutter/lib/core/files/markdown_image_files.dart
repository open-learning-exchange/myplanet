import 'dart:io';

import 'package:path/path.dart' as p;

import '../utils/markdown_links.dart';
import 'resource_files.dart';

/// Where a markdown description's inline image lives on disk.
///
/// The Kotlin renders these from `file://<externalFilesDir>/ole/` with a
/// leading `resources/` stripped (`CourseStepFragment:125`,
/// `CourseDetailViewModel:65`), which is the same `<base>/ole/<docId>/<file>`
/// layout `ResourceFiles` already owns for a downloaded resource attachment —
/// markdown images *are* resource attachments, reached by path instead of by
/// `my_library` row. So this delegates its root to [ResourceFiles.baseDirectory]
/// rather than resolving its own: two roots is how a file that downloads
/// successfully becomes a file the viewer cannot find, and it is also what
/// makes both sides overridable from one place in a test.
///
/// The path inside that root comes from [markdownImageCachePath], which is
/// the only derivation either side uses.
class MarkdownImageFiles {
  const MarkdownImageFiles._();

  /// `<base>/ole/<markdownImageCachePath(link)>`, or `null` when the link
  /// cannot name a local file.
  static Future<File?> fileFor(String link) async {
    final relative = markdownImageCachePath(link);
    if (relative == null) return null;
    final ole = await ResourceFiles.oleDirectory();
    final basePath = ole.absolute.path;
    final resolved = p.normalize(p.absolute(p.join(basePath, relative)));
    // `markdownImageCachePath` already refuses `..` and an absolute link, so
    // this cannot currently fail. It is kept because the containment check is
    // the property that actually matters, and a later loosening of the
    // derivation should fail here rather than write outside `ole/`.
    if (!p.isWithin(basePath, resolved)) return null;
    return File(resolved);
  }

  /// The file only when it is actually usable.
  ///
  /// A zero-length file is what an interrupted download leaves behind, and
  /// `exists()` alone would report it as present — the renderer would then
  /// prefer an empty local copy over the network fetch that still works. Same
  /// `length() > 0` test as [ResourceFiles.existingFileFor] and Kotlin's
  /// `FileUtils.checkFileExist`.
  static Future<File?> existingFileFor(String link) async {
    final file = await fileFor(link);
    if (file == null) return null;
    return await hasBytes(file) ? file : null;
  }

  /// Whether [file] is a usable download rather than an interrupted one.
  ///
  /// Split out so a caller that already holds the `File` does not derive it a
  /// second time: [fileFor] awaits `ResourceFiles.oleDirectory()`, which goes
  /// through `path_provider`'s platform channel and is not cached, so the
  /// prefetcher's `fileFor`-then-`existingFileFor` pair was two channel hops
  /// per link — in the headless isolate as well.
  static Future<bool> hasBytes(File file) async {
    if (!await file.exists()) return false;
    return await file.length() > 0;
  }
}
