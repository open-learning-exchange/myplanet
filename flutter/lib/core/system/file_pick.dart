import 'dart:io';

import 'package:file_picker/file_picker.dart';

/// The document-picker step of `EditAchievementFragment.pickCvLauncher`.
///
/// Kotlin launches `ActivityResultContracts.GetContent()`, reads the display
/// name off the returned `Uri`, and only copies the bytes later, in
/// `computeCvFilename`. This keeps that split: [PickedFile] carries the name
/// and extension, and [PickedFile.readBytes] is what actually touches the
/// file — so a screen can reject a non-PDF without reading it.
///
/// Like [PhotoCapture], this is an interface so the platform call can be
/// replaced by a fake in widget tests; `file_picker` needs a platform channel
/// that `flutter test` has no way to serve, which is why the CV path had no
/// coverage at all. A null return is "the user backed out".
abstract class FilePick {
  /// Opens the platform picker for a single file.
  Future<PickedFile?> pickSingle();

  /// The implementation used in production. Tests inject a fake directly.
  static FilePick instance = const _PlatformFilePick();
}

/// One picked file, reduced to what a screen needs.
class PickedFile {
  const PickedFile({
    required this.name,
    required this.extension,
    required this.readBytes,
  });

  final String name;
  final String? extension;

  /// Reads the file's bytes. Called only once a screen has accepted the pick.
  final Future<List<int>> Function() readBytes;
}

class _PlatformFilePick implements FilePick {
  const _PlatformFilePick();

  @override
  Future<PickedFile?> pickSingle() async {
    final result = await FilePicker.pickFiles(
      type: FileType.any,
      allowMultiple: false,
    );
    if (result == null || result.files.isEmpty) return null;
    final file = result.files.single;
    final path = file.path;
    if (path == null) return null;
    return PickedFile(
      name: file.name,
      extension: file.extension,
      readBytes: () => File(path).readAsBytes(),
    );
  }
}
