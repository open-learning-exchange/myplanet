import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:pointycastle/export.dart';

/// Port of the AES half of `utils/AndroidDecrypter.kt`.
///
/// Health examinations carry diagnoses, medications and allergies in their
/// `data` blob. Kotlin encrypts that blob before it touches Realm or the
/// server, so the port has to use the identical scheme or two things break at
/// once: the records sit in plaintext in SQLite and travel in plaintext to
/// CouchDB, and nothing either app writes can be read by the other.
///
/// The scheme, matching `AndroidDecrypter.encrypt`:
///   - AES-256 in CBC mode with PKCS#7 padding (Java calls it PKCS5Padding;
///     for a 16-byte block the two are the same padding).
///   - `key` and `iv` arrive as hex strings and are decoded into a 32-byte key
///     and a 16-byte IV. Short input is zero-padded, long input truncated,
///     which is what `System.arraycopy` into a fixed-size array does.
///   - The output is hex of `iv + ciphertext` — the IV is prepended, not
///     stored separately.
class HealthCipher {
  const HealthCipher._();

  static const int _keyBytes = 32;
  static const int _ivBytes = 16;

  /// Encrypts [plainText] to the hex `iv + ciphertext` form Kotlin writes.
  static String encrypt(String plainText, String? key, String? iv) {
    final ivValue = _fixedBytes(iv, _ivBytes);
    final cipher = _cipher(key, ivValue, encrypting: true);
    final input = Uint8List.fromList(utf8.encode(plainText));
    final output = cipher.process(input);
    return _toHex(Uint8List.fromList([...ivValue, ...output]));
  }

  /// Reverses [encrypt], returning null rather than throwing on anything it
  /// cannot read.
  ///
  /// Returning null matters: a health screen handed a blob from a different
  /// key — a re-installed device, a record authored by another user — has to
  /// render "no data" instead of taking down the screen.
  static String? decrypt(String? encrypted, String? key, String? iv) {
    if (encrypted == null || encrypted.isEmpty || key == null || iv == null) {
      return null;
    }
    try {
      final ivValue = _fixedBytes(iv, _ivBytes);
      final raw = _fromHex(encrypted);
      if (raw.isEmpty) return null;

      // Kotlin prepends the IV, but records written before it did so are just
      // the ciphertext. Detect which by looking for the IV at the front, the
      // same test `AndroidDecrypter.decrypt` makes.
      final body = raw.length >= _ivBytes && _startsWith(raw, ivValue)
          ? Uint8List.sublistView(raw, _ivBytes)
          : raw;
      if (body.isEmpty || body.length % _ivBytes != 0) return null;

      final cipher = _cipher(key, ivValue, encrypting: false);
      return utf8.decode(cipher.process(body));
    } catch (_) {
      return null;
    }
  }

  /// Port of `AndroidDecrypter.generateKey` — a 256-bit key as hex.
  static String generateKey() => _randomHex(_keyBytes);

  /// Port of `AndroidDecrypter.generateIv` — a 128-bit IV as hex.
  static String generateIv() => _randomHex(_ivBytes);

  static PaddedBlockCipher _cipher(
    String? key,
    Uint8List iv, {
    required bool encrypting,
  }) {
    final params = PaddedBlockCipherParameters<CipherParameters, Null>(
      ParametersWithIV(KeyParameter(_fixedBytes(key, _keyBytes)), iv),
      null,
    );
    return PaddedBlockCipherImpl(PKCS7Padding(), CBCBlockCipher(AESEngine()))
      ..init(encrypting, params);
  }

  /// Hex-decodes into a buffer of exactly [length] bytes, zero-filling a short
  /// value and ignoring the tail of a long one.
  static Uint8List _fixedBytes(String? hex, int length) {
    final decoded = _fromHex(hex ?? '');
    final out = Uint8List(length);
    out.setRange(0, min(length, decoded.length), decoded);
    return out;
  }

  static Uint8List _fromHex(String hex) {
    final clean = hex.length.isOdd ? hex.substring(0, hex.length - 1) : hex;
    final out = Uint8List(clean.length ~/ 2);
    for (var i = 0; i < out.length; i++) {
      final byte = int.tryParse(clean.substring(i * 2, i * 2 + 2), radix: 16);
      if (byte == null) return Uint8List(0);
      out[i] = byte;
    }
    return out;
  }

  static String _toHex(Uint8List bytes) {
    final buffer = StringBuffer();
    for (final byte in bytes) {
      buffer.write(byte.toRadixString(16).padLeft(2, '0'));
    }
    return buffer.toString();
  }

  static bool _startsWith(Uint8List haystack, Uint8List prefix) {
    for (var i = 0; i < prefix.length; i++) {
      if (haystack[i] != prefix[i]) return false;
    }
    return true;
  }

  static String _randomHex(int length) {
    // `Random.secure` is the platform CSPRNG, matching `SecureRandom` and
    // `KeyGenerator` on the Kotlin side. A predictable key here would make the
    // encryption decorative.
    final random = Random.secure();
    return _toHex(
      Uint8List.fromList(List.generate(length, (_) => random.nextInt(256))),
    );
  }
}
