import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// Where a team document's binary attachment (a transaction or finance-report
/// receipt image) lives on disk.
///
/// Port of `MyTeam.getAttachmentFile` / `attachTeamImage`. The Kotlin source
/// keeps only the attachment's *name* on the [TeamRow] (`imageName`) and
/// reconstructs the path from the team id, so two transactions naming their
/// receipts `receipt.jpg` do not overwrite each other — the directory is keyed
/// on the document id, the way [ResourceFiles.fileFor] keys on `docId`.
///
/// The download path, the upload read-back, and the in-app preview all resolve
/// through here for the same reason `ResourceFiles` does: when each side builds
/// its own path they drift, and the symptom is a file that saves successfully
/// and then cannot be found.
class TeamAttachments {
  const TeamAttachments._();

  /// Overridable so tests do not need a platform channel.
  static Future<Directory> Function() baseDirectory =
      getApplicationDocumentsDirectory;

  /// `<base>/team_attachments/<docId>/<filename>`.
  ///
  /// `docId` is the team document's own id (the transaction or report id), not
  /// the team it belongs to: a team accumulates many finance documents, so the
  /// team id alone would collide identically-named receipts. This mirrors the
  /// Kotlin layout, which nests under the document id via `getAttachmentFile`.
  static Future<File> fileFor({
    required String docId,
    required String filename,
  }) async {
    final base = await baseDirectory();
    return File(
      p.join(
        base.path,
        'team_attachments',
        _segment(docId),
        _segment(filename),
      ),
    );
  }

  /// Writes [bytes] to the slot for [docId]/[filename] and returns the file.
  ///
  /// Best-effort, like `attachTeamImage`: the caller has already persisted the
  /// row carrying `imageName`, so a write failure leaves the document without
  /// its attachment but does not roll the document back.
  static Future<File?> write({
    required String docId,
    required String filename,
    required List<int> bytes,
  }) async {
    if (docId.isEmpty || filename.isEmpty) return null;
    final file = await fileFor(docId: docId, filename: filename);
    await file.parent.create(recursive: true);
    await file.writeAsBytes(bytes, flush: true);
    return file;
  }

  /// The file only when it is actually usable.
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
  /// escaping the attachment directory.
  static String _segment(String raw) {
    final name = p.basename(raw.replaceAll(r'\', '/'));
    return (name.isEmpty || name == '.' || name == '..') ? '_' : name;
  }
}
