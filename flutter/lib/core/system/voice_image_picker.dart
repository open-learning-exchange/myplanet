import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';

/// Picks the images a user attaches to a voice post.
///
/// Kotlin's affordance launches `FileUtils.openOleFolder` — a document picker
/// rooted at the app's `ole` directory — from `BaseVoicesFragment.addImage`
/// (`:131-134`) and from `TeamsVoicesFragment`'s own `binding.addNewsImage`
/// (`:63-67`); `BaseVoicesFragment.kt:63-67` is the *result handler*, not the
/// affordance. Either way it resolves the returned URI to an absolute path
/// (`BaseVoicesFragment.processImageUri`, `:141-169`). That folder exists
/// because the Kotlin app downloads resources into it; the port has no
/// equivalent user-visible directory, and the thing a user actually wants to
/// attach is a photo, so this is the platform image picker instead.
///
/// Like [PhotoCapture], [DiskStats] and [DeviceStats] this is an interface so
/// the platform call — which compiles and runs only on a device — can be
/// replaced by a fake in widget tests. An empty return is the contract for
/// "cancelled, or permission denied", which is what Kotlin's
/// `openFolderLauncher` callback does with a null result.
abstract class VoiceImagePicker {
  /// Picks zero or more images. Returns their bytes and filenames.
  Future<List<PickedVoiceImage>> pick();

  /// The implementation used in production. Tests inject a fake directly.
  static VoiceImagePicker instance = _ImagePickerVoiceImagePicker();

  /// The name the user's file actually has, undoing `image_picker`'s own
  /// rename.
  ///
  /// Android's `ImageResizer.resizeImageIfNeeded` returns the original path
  /// only when `maxWidth == null && maxHeight == null && imageQuality == 100`;
  /// otherwise it re-encodes and writes `"/scaled_" + outputImageName`. This
  /// picker asks for 1280×1280 at quality 85 — deliberately, because these
  /// become CouchDB attachments on the connection myPlanet exists to work
  /// around — so **every** pick comes back as `scaled_<original>`.
  ///
  /// That name is not cosmetic: it is the single key for the `resources`
  /// document's `title` and `filename`, the attachment name in the PUT URL,
  /// and the `![](…)` path in the post's message. Kotlin sends
  /// `FileUtils.getFileNameFromUrl(path)` — the bare basename of what the user
  /// picked — so leaving the prefix on would put `scaled_IMG_0001.jpg` on
  /// Planet where the Android app puts `IMG_0001.jpg`.
  ///
  /// A file the user really did name `scaled_x.jpg` is uploaded as `x.jpg`.
  /// That is an attachment name, not content, and the alternative is every
  /// image carrying an implementation detail of the picker.
  @visibleForTesting
  static String originalName(String pickedName) {
    const prefix = 'scaled_';
    final name = pickedName.startsWith(prefix)
        ? pickedName.substring(prefix.length)
        : pickedName;
    return name.isNotEmpty
        ? name
        : '${DateTime.now().millisecondsSinceEpoch}.jpg';
  }
}

class PickedVoiceImage {
  const PickedVoiceImage({required this.bytes, required this.filename});

  final Uint8List bytes;
  final String filename;
}

class _ImagePickerVoiceImagePicker implements VoiceImagePicker {
  @override
  Future<List<PickedVoiceImage>> pick() async {
    final picker = ImagePicker();
    // Resized on the device, matching what `PhotoCapture` does for the exam
    // verification photo: these become CouchDB attachments on a connection
    // myPlanet exists to work around, and a full-resolution phone camera JPEG
    // is several megabytes.
    final picked = await picker.pickMultiImage(
      maxWidth: 1280,
      maxHeight: 1280,
      imageQuality: 85,
    );
    final images = <PickedVoiceImage>[];
    for (final file in picked) {
      images.add(
        PickedVoiceImage(
          bytes: await file.readAsBytes(),
          filename: VoiceImagePicker.originalName(file.name),
        ),
      );
    }
    return images;
  }
}
