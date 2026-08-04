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

  /// Keeps a `..` or a path separator in a server-supplied id or filename from
  /// escaping the resource directory.
  static String _segment(String raw) {
    final name = p.basename(raw.replaceAll(r'\', '/'));
    return (name.isEmpty || name == '.' || name == '..') ? '_' : name;
  }
}
