import 'dart:typed_data';

import 'package:image_picker/image_picker.dart';

/// Picks the images a user attaches to a voice post.
///
/// Kotlin's affordance is `binding.addNewsImage`, which launches
/// `FileUtils.openOleFolder` — a document picker rooted at the app's `ole`
/// directory (`BaseVoicesFragment.kt:63-67`, `TeamsVoicesFragment.kt:63-67`) —
/// and resolves the returned URI to an absolute path
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
          filename: file.name.isNotEmpty
              ? file.name
              : '${DateTime.now().millisecondsSinceEpoch}.jpg',
        ),
      );
    }
    return images;
  }
}
