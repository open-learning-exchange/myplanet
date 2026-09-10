import 'dart:typed_data';

import 'package:image_picker/image_picker.dart';

/// Port of `utils/CameraUtils.kt`'s capture step.
///
/// Kotlin drives the camera2 API directly to capture a JPEG and write it to
/// `<ole>/userimages/<timestamp>.jpg`. Flutter's `image_picker` covers the
/// capture-via-camera (`ImageSource.camera`) and returns a cache path, which
/// the exam screen then persists to the durable `SubmitPhotosFiles` slot and
/// records on the `submit_photos` row.
///
/// Like `DiskStats` and `DeviceStats`, this is an interface so the platform
/// call (which compiles and runs only on an Android device) can be replaced by
/// a fake in widget tests. A null return is the contract for "no camera or
/// permission denied" — the Kotlin `capturePhoto` swallows the same condition
/// by checking `CAMERA` permission up front and returning silently.
abstract class PhotoCapture {
  /// Captures a single still image from the device camera.
  ///
  /// Returns the bytes and a filename for the capture, or `null` when the
  /// device has no camera, the user denied permission, or the capture was
  /// cancelled. The filename is what the attachment is filed under on CouchDB;
  /// a timestamp-based name keeps captures from overwriting one another.
  Future<CapturedPhoto?> capture();

  /// The implementation used in production. Tests inject a fake directly.
  static PhotoCapture instance = _ImagePickerPhotoCapture();
}

class CapturedPhoto {
  const CapturedPhoto({required this.bytes, required this.filename});

  final Uint8List bytes;
  final String filename;
}

class _ImagePickerPhotoCapture implements PhotoCapture {
  const _ImagePickerPhotoCapture();

  @override
  Future<CapturedPhoto?> capture() async {
    final picker = ImagePicker();
    final picked = await picker.pickImage(
      source: ImageSource.camera,
      maxWidth: 640,
      maxHeight: 480,
      imageQuality: 85,
    );
    if (picked == null) return null;
    final bytes = await picked.readAsBytes();
    final name = picked.name.isNotEmpty
        ? picked.name
        : '${DateTime.now().millisecondsSinceEpoch}.jpg';
    return CapturedPhoto(bytes: bytes, filename: name);
  }
}
