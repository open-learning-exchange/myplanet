import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// Where a picked resume/CV lives on disk.
///
/// Port of the path half of `EditAchievementFragment.attachFileDestination`:
/// Kotlin copies the picked file under `<ole>/cv/<resumeFileName>`. Port of
/// `utils/FileUtils.getOlePath`'s layout: when the sync-in arrives, it
/// downloads the attachment to the same slot named on the document, unless
/// the file was already there (Kotlin skips a file that survives).
class AchievementFiles {
  const AchievementFiles._();

  /// Overridable so tests do not need a platform channel.
  static Future<Directory> Function() baseDirectory =
      getApplicationDocumentsDirectory;

  /// `<base>/ole/cv/<resumeFileName>`.
  static Future<File> fileFor(String resumeFileName) async {
    final base = await baseDirectory();
    return File(p.join(base.path, 'ole', 'cv', _segment(resumeFileName)));
  }

  /// Writes [bytes] to the slot for [resumeFileName] and returns the file.
  ///
  /// Best-effort like `EditAchievementFragment.computeCvFilename`: the row
  /// carrying `resumeFileName` is already persisted, so a write failure leaves
  /// the document naming a file it has no bytes for — matching Kotlin, which
  /// catches the `IOException` and toasts.
  static Future<File?> write({
    required String resumeFileName,
    required List<int> bytes,
  }) async {
    if (resumeFileName.isEmpty) return null;
    final file = await fileFor(resumeFileName);
    await file.parent.create(recursive: true);
    await file.writeAsBytes(bytes, flush: true);
    return file;
  }

  /// The bytes only when they are actually usable, mirroring
  /// [ResourceFiles.existingFileFor]: a zero-length capture is what a failed
  /// picker read leaves behind, and `exists()` alone would report it present.
  static Future<List<int>?> readResumeBytes(String resumeFileName) async {
    if (resumeFileName.isEmpty) return null;
    final file = await fileFor(resumeFileName);
    if (!await file.exists()) return null;
    if (await file.length() <= 0) return null;
    return file.readAsBytes();
  }

  /// Whether the slot has usable bytes, the Kotlin's `!file.exists()` guard on
  /// the sync-in.
  static Future<bool> hasResume(String resumeFileName) async {
    if (resumeFileName.isEmpty) return false;
    final file = await fileFor(resumeFileName);
    if (!await file.exists()) return false;
    return await file.length() > 0;
  }

  /// Keeps a `..` or a path separator in a server-supplied filename from
  /// escaping the cv directory.
  static String _segment(String raw) {
    final name = p.basename(raw.replaceAll(r'\', '/'));
    return (name.isEmpty || name == '.' || name == '..') ? '_' : name;
  }
}
