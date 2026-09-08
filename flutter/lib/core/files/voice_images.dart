import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// Where a voice post's pending image attachment lives on disk between being
/// picked and reaching CouchDB.
///
/// **This has no Kotlin counterpart, deliberately.** Kotlin stores the
/// picker's absolute device path in `News.imageUrls`
/// (`BaseVoicesFragment.kt:153-156`) and opens it again at upload time
/// (`UploadManager.kt:322`). That works there because the upload runs from a
/// worker minutes later on the same filesystem; it does not transfer to a port
/// whose write-back is a durable outbox that may drain after a process death,
/// an app update, or — on iOS — a change to the documents directory that
/// invalidates every absolute path ever recorded. So the bytes are copied into
/// a slot this class owns, the way [TeamAttachments] and [SubmitPhotosFiles]
/// already do for the two other attachment paths in the port.
///
/// **One key, derived by one side.** The slot is `<newsId>/<filename>` and the
/// *repository that mints the news id* writes the bytes, so the row and the
/// bytes cannot disagree about where they are. Phase 100's verification-photo
/// bug was exactly this pair disagreeing — bytes written under one id, read
/// back under another — and the symptom was silence, because the uploader's
/// "a missing file is a no-op" branch swallowed it and the document went up
/// without its attachment.
class VoiceImages {
  const VoiceImages._();

  /// Overridable so tests do not need a platform channel.
  static Future<Directory> Function() baseDirectory =
      getApplicationDocumentsDirectory;

  /// `<base>/voice_images/<newsId>/<filename>`.
  ///
  /// Keyed on the news row's **local** id rather than its CouchDB `_id`: the
  /// bytes are written before the post has ever been uploaded, so there is no
  /// `_id` yet, and `NewsMapper.fromDoc` keeps `existing?.id` on the way back
  /// in so the local id survives the round trip.
  static Future<File> fileFor({
    required String newsId,
    required String filename,
  }) async {
    final base = await baseDirectory();
    return File(
      p.join(base.path, 'voice_images', _segment(newsId), _segment(filename)),
    );
  }

  /// Writes [bytes] to the slot for [newsId]/[filename] and returns the file.
  static Future<File?> write({
    required String newsId,
    required String filename,
    required List<int> bytes,
  }) async {
    if (newsId.isEmpty || filename.isEmpty) return null;
    final file = await fileFor(newsId: newsId, filename: filename);
    await file.parent.create(recursive: true);
    await file.writeAsBytes(bytes, flush: true);
    return file;
  }

  /// The file only when it is actually usable.
  static Future<File?> existingFileFor({
    required String newsId,
    required String filename,
  }) async {
    if (newsId.isEmpty || filename.isEmpty) return null;
    final file = await fileFor(newsId: newsId, filename: filename);
    if (!await file.exists()) return null;
    if (await file.length() <= 0) return null;
    return file;
  }

  /// Removes every pending image for a post, once they have been delivered.
  ///
  /// Best-effort: the row's `imageUrls` is what the uploader reads, so a
  /// directory left behind costs disk, not correctness.
  static Future<void> deleteFor(String newsId) async {
    if (newsId.isEmpty) return;
    try {
      final base = await baseDirectory();
      final dir = Directory(
        p.join(base.path, 'voice_images', _segment(newsId)),
      );
      if (await dir.exists()) await dir.delete(recursive: true);
    } on Exception {
      // A slot that cannot be cleaned is not a failed upload.
    }
  }

  /// Keeps a `..` or a path separator in a picked filename from escaping the
  /// attachment directory.
  static String _segment(String raw) {
    final name = p.basename(raw.replaceAll(r'\', '/'));
    return (name.isEmpty || name == '.' || name == '..') ? '_' : name;
  }
}
