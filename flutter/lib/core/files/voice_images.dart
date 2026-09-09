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
  /// Best-effort, and the `catch` is deliberately unqualified. `markUploaded`
  /// calls this **after** the news document has been accepted, so anything
  /// escaping here would fail the outbox handler for a post that is already on
  /// the server — and the next drain would POST a second copy, which is
  /// precisely the duplicate the handler's id/rev guard exists to prevent.
  /// `on Exception` was not enough: `getApplicationDocumentsDirectory` on an
  /// engine with no `path_provider` channel throws a **`FlutterError`**, which
  /// is an `Error`, and headless WorkManager engines are exactly where this
  /// runs. The existing `markUploaded` test caught it.
  ///
  /// A slot left behind costs disk, not correctness: `imageUrls` is what the
  /// uploader reads and it has just been cleared.
  static Future<void> deleteFor(String newsId) async {
    if (newsId.isEmpty) return;
    try {
      final base = await baseDirectory();
      final dir = Directory(
        p.join(base.path, 'voice_images', _segment(newsId)),
      );
      if (await dir.exists()) await dir.delete(recursive: true);
    } catch (_) {
      // See above: never let cleanup fail a delivered upload.
    }
  }

  /// The name a picked file is actually stored under.
  ///
  /// Exposed because the caller has to de-duplicate on **this**, not on the
  /// raw name: `a/photo.jpg` and `b/photo.jpg` are different strings that
  /// [_segment] reduces to the same slot, so a de-duplication keyed on the raw
  /// name would let them collide and one image would be silently lost — the
  /// very failure the de-duplication exists to prevent.
  static String storedNameFor(String filename) => _segment(filename);

  /// Keeps a `..` or a path separator in a picked filename from escaping the
  /// attachment directory.
  static String _segment(String raw) {
    final name = p.basename(raw.replaceAll(r'\', '/'));
    return (name.isEmpty || name == '.' || name == '..') ? '_' : name;
  }
}
