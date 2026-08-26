import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// Where a downloaded resource lives on disk.
///
/// Port of `utils/FileUtils.getSDPathFromUrl` / `checkFileExist`. The download
/// and the viewer both resolve the path through here on purpose: when each
/// side builds its own path they drift, and the symptom is a file that
/// downloads successfully and then cannot be found.
class ResourceFiles {
  const ResourceFiles._();

  /// Overridable so tests do not need a platform channel.
  static Future<Directory> Function() baseDirectory =
      getApplicationDocumentsDirectory;

  /// `<base>/ole/<docId>/<filename>`.
  ///
  /// The `docId` segment is not decoration: resource attachments are routinely
  /// named `index.html`, `video.mp4` or `cover.jpg`, so a flat `ole/<filename>`
  /// makes two unrelated resources overwrite each other. `FileUtils` keys the
  /// directory on the id embedded in the attachment URL for the same reason.
  static Future<File> fileFor({
    required String docId,
    required String filename,
  }) async {
    final base = await baseDirectory();
    return File(p.join(base.path, 'ole', _segment(docId), _segment(filename)));
  }

  /// The file only when it is actually usable.
  ///
  /// A zero-length file is what a failed or interrupted download leaves
  /// behind; `exists()` alone would report it as present and the viewer would
  /// try to render nothing. `FileUtils.checkFileExist` makes the same
  /// `length() > 0` check.
  static Future<File?> existingFileFor({
    required String docId,
    required String filename,
  }) async {
    if (docId.isEmpty || filename.isEmpty) return null;
    final file = await fileFor(docId: docId, filename: filename);
    if (!await file.exists()) return null;
    if (await file.length() <= 0) return null;
    return file;
  }

  /// Reads a text/CSV/markdown attachment's contents, or `null` when it is
  /// absent or empty.
  ///
  /// The text renderers (`_TextViewer`, `_MarkdownViewer`) used to take a path
  /// from [existingFileFor] and call `File.readAsString` themselves, which
  /// hangs a widget test's fake-clock zone. Routing the read through here lets
  /// a provider seam hand the renderer its bytes instead of a path, so the
  /// rendering pipeline is testable without real `dart:io`.
  static Future<String?> readTextContent({
    required String docId,
    required String filename,
  }) async {
    final file = await existingFileFor(docId: docId, filename: filename);
    if (file == null) return null;
    try {
      return await file.readAsString();
    } catch (_) {
      // `readAsString` throws on undecodable bytes (a binary file behind a
      // .txt name, or a corrupted download) and on I/O failure. The renderers
      // treat null as "file not found", which is the honest state for content
      // that cannot be shown — before this catch the exception escaped their
      // `initState` unhandled and the screen sat on its spinner forever. The
      // renderers used to render `e.toString()`; a stack-trace-ish string is
      // not better UI than the not-found message.
      return null;
    }
  }

  /// The `<base>/ole/<docId>` directory a resource's files live under.
  ///
  /// Used by the HTML viewer to resolve the entry file via
  /// [resolveHtmlEntryFile], and by storage management to walk a resource's
  /// attachments.
  static Future<Directory> directoryFor({required String docId}) async {
    final base = await baseDirectory();
    return Directory(p.join(base.path, 'ole', _segment(docId)));
  }

  /// The `<base>/ole` directory every downloaded resource lives under.
  ///
  /// Storage management scans this tree; the downloader and viewer resolve
  /// individual files through [fileFor]. Routing all three through the same
  /// [baseDirectory] is what keeps "where the file is" and "where storage
  /// management looks" from drifting — the original port scanned
  /// `Directory.current` instead, which is the CWD, not the documents dir.
  static Future<Directory> oleDirectory() async {
    final base = await baseDirectory();
    return Directory(p.join(base.path, 'ole'));
  }

  /// Keeps a `..` or a path separator in a server-supplied id or filename from
  /// escaping the resource directory.
  static String _segment(String raw) {
    final name = p.basename(raw.replaceAll(r'\', '/'));
    return (name.isEmpty || name == '.' || name == '..') ? '_' : name;
  }

  /// Resolves an HTML resource's entry file against its download directory,
  /// defaulting to `index.html` when [relativePath] is unset.
  ///
  /// Port of `FileUtils.resolveHtmlEntryFile`. An HTML resource's
  /// `openWhichFile` may nest the entry point in a subfolder
  /// (`sudoku/index.html` rather than `index.html`); this resolves that path
  /// against [baseDirectory] and refuses to return anything outside it, so a
  /// malicious `openWhichFile` cannot read an arbitrary file off the device.
  static File? resolveHtmlEntryFile(
    Directory baseDirectory,
    String? relativePath,
  ) {
    final candidate = (relativePath == null || relativePath.trim().isEmpty)
        ? 'index.html'
        : relativePath.trim();
    if (candidate.startsWith('/') ||
        candidate.startsWith('\\') ||
        candidate.contains('..')) {
      return null;
    }
    final basePath = baseDirectory.absolute.path;
    final resolved = p.normalize(p.absolute(p.join(basePath, candidate)));
    if (resolved == basePath || p.isWithin(basePath, resolved)) {
      return File(resolved);
    }
    return null;
  }

  /// Extracts a resource's nested relative path from its attachment URL.
  ///
  /// Port of `FileUtils.getResourceRelativePathFromUrl`. A resource whose
  /// attachment lives at `/resources/<id>/sudoku/index.html` is stored under
  /// `ole/<id>/sudoku/index.html`, not flattened to `ole/<id>/index.html` —
  /// otherwise a multi-file HTML bundle loses its subfolder structure and the
  /// entry file's relative links break. Falls back to the plain filename when
  /// the URL does not carry the expected `/resources/<id>/…` shape.
  static String resourceRelativePathFromUrl(String? url) {
    if (url == null || url.isEmpty) return '';
    final parsed = Uri.tryParse(url);
    final segments = parsed?.pathSegments;
    if (segments == null || segments.isEmpty) return p.basename(url);
    final idx = segments.indexOf('resources');
    if (idx == -1 || idx + 2 >= segments.length) return p.basename(url);
    final nested = segments.sublist(idx + 2);
    return nested.map((s) => Uri.decodeComponent(s)).join('/');
  }
}
