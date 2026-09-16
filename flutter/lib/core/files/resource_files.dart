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

  /// Moves a resource's downloaded bytes from one `ole/<docId>/` directory to
  /// another, and reports whether the bytes — if there were any — are now
  /// under [toDocId].
  ///
  /// **The one key, applied to a key that changes.** Every reader in the port
  /// resolves a resource's files under `couchId ?? id`
  /// (`resource_viewer_screen._getLocalFilePath`,
  /// `ResourceDownloader`), while [ResourcesRepository.saveLocalResource]
  /// writes them under the row's `id`, because a locally authored row has no
  /// `couchId` yet. Those agree right up to the moment the resource uploads
  /// and adopts one — and from then on the viewer looks in a directory that
  /// does not exist, reads the absent file as "not downloaded", and clears the
  /// flag on a file that is sitting on the device. `ResourcesUploader` calls
  /// this so the bytes arrive at the new key in the same step the row does.
  ///
  /// Returns false only when bytes exist and could not be moved, which the
  /// caller uses to keep reading them from the old directory for the
  /// attachment PUT. A missing source is **true**: there is nothing under
  /// either key, which is what the caller needs to know.
  /// **This never throws**, and that is load-bearing rather than defensive.
  /// Its caller runs inside an `outbox` handler *after* the document POST has
  /// been accepted, and a throw there is recorded as a failed send: the row
  /// goes back to `pending` and the next drain POSTs the same document again,
  /// filing a **second** one in the shared catalog with no way to tell them
  /// apart. A filesystem error must not be able to cost a duplicate document,
  /// so every failure — `FileSystemException` and the
  /// `MissingPluginException` [baseDirectory] raises on an engine with no
  /// platform channel alike — becomes a false answer.
  static Future<bool> moveResourceDirectory({
    required String fromDocId,
    required String toDocId,
  }) async {
    if (fromDocId == toDocId) return true;
    try {
      final source = await directoryFor(docId: fromDocId);
      final destination = await directoryFor(docId: toDocId);
      // [_segment] can collapse two different ids onto the same segment (both
      // reduce to `_`), and renaming a directory onto itself deletes it.
      if (source.path == destination.path) return true;
      if (!await source.exists()) return true;

      if (!await destination.exists()) {
        try {
          await destination.parent.create(recursive: true);
          await source.rename(destination.path);
          return true;
        } on FileSystemException catch (_) {
          // `rename` refuses across filesystems, and some platforms refuse it
          // for a non-empty directory. Fall through to the copy.
        }
      }

      // The destination may already hold files — a previous partial move, or
      // a download of the same document. Copying over it and then removing the
      // source is deliberate: the source is this device's own authored bytes,
      // which are the copy worth keeping when the two disagree.
      await _copyDirectory(source, destination);
      await source.delete(recursive: true);
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Recursive on purpose: an HTML resource is a whole bundle
  /// (`ole/<id>/sudoku/index.html` and its siblings), and a flat copy would
  /// move the entry file away from the assets it links to.
  static Future<void> _copyDirectory(Directory source, Directory target) async {
    await target.create(recursive: true);
    await for (final entity in source.list()) {
      final name = p.basename(entity.path);
      if (entity is Directory) {
        await _copyDirectory(entity, Directory(p.join(target.path, name)));
      } else if (entity is File) {
        await entity.copy(p.join(target.path, name));
      }
    }
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
