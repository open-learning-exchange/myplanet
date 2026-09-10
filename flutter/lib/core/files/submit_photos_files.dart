import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// Where a captured exam-verification photo's bytes live on disk.
///
/// Port of the path half of `CameraUtils.savePicture` / `MyLibrary`'s
/// attachment layout. Kotlin stores captured JPEGs under
/// `<ole>/userimages/<timestamp>.jpg` and keeps that absolute path on the
/// `SubmitPhotos.photoLocation` column. The bytes are then PUT to
/// `submissions/<docId>/<filename>` after the document POST lands, the same
/// two-step shape as [TeamAttachments].
///
/// This port keys the directory on the photo row's own id rather than a bare
/// timestamp, for the same reason [TeamAttachments] and [ResourceFiles] do:
/// when the write-back and the upload read-back build their paths from the
/// same source they cannot drift, and a collision between two captures would
/// overwrite one's bytes before the uploader reads them.
class SubmitPhotosFiles {
  const SubmitPhotosFiles._();

  /// Overridable so tests do not need a platform channel.
  static Future<Directory> Function() baseDirectory =
      getApplicationDocumentsDirectory;

  /// `<base>/submit_photos/<photoId>/<filename>`.
  static Future<File> fileFor({
    required String photoId,
    required String filename,
  }) async {
    final base = await baseDirectory();
    return File(
      p.join(base.path, 'submit_photos', _segment(photoId), _segment(filename)),
    );
  }

  /// Writes [bytes] to the slot for [photoId]/[filename] and returns the file.
  ///
  /// Best-effort, like `CameraUtils.savePicture`: the caller has already
  /// persisted the row carrying `photoLocation`, so a write failure leaves the
  /// document without its attachment but does not roll the row back.
  static Future<File?> write({
    required String photoId,
    required String filename,
    required List<int> bytes,
  }) async {
    if (photoId.isEmpty || filename.isEmpty) return null;
    final file = await fileFor(photoId: photoId, filename: filename);
    await file.parent.create(recursive: true);
    await file.writeAsBytes(bytes, flush: true);
    return file;
  }

  /// The file only when it is actually usable, mirroring
  /// [ResourceFiles.existingFileFor]: a zero-length capture is what a failed
  /// camera read leaves behind, and `exists()` alone would report it present.
  static Future<File?> existingFileFor({
    required String photoId,
    required String filename,
  }) async {
    if (photoId.isEmpty || filename.isEmpty) return null;
    final file = await fileFor(photoId: photoId, filename: filename);
    if (!await file.exists()) return null;
    if (await file.length() <= 0) return null;
    return file;
  }

  /// Derives the attachment name from a local path, the way
  /// `FileUtils.getFileNameFromUrl` does. Falls back to the photo row id when
  /// the path carries no basename, so an attachment slot is always addressable.
  static String attachmentNameFor(String? photoLocation, String photoId) {
    if (photoLocation == null || photoLocation.isEmpty) return '$photoId.jpg';
    final name = p.basename(photoLocation.replaceAll(r'\', '/'));
    return name.isEmpty ? '$photoId.jpg' : name;
  }

  /// Keeps a `..` or a path separator in a server-supplied id or filename from
  /// escaping the photo directory.
  static String _segment(String raw) {
    final name = p.basename(raw.replaceAll(r'\', '/'));
    return (name.isEmpty || name == '.' || name == '..') ? '_' : name;
  }
}
