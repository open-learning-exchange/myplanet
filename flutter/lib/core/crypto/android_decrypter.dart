import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';

import 'pbkdf2.dart';

/// Port of `utils/AndroidDecrypter.androidDecrypter`.
///
/// Verifies a plaintext password against the `derived_key` / `salt` pair stored
/// on a CouchDB `_users` document. CouchDB's own PBKDF2 scheme is
/// HMAC-SHA1-based, and the Kotlin implementation pins the parameters as:
///
/// ```kotlin
/// val p = PBKDF2Parameters("HmacSHA1", "utf-8", dbSalt?.toByteArray(), 10)
/// val dk = PBKDF2Engine(p).deriveKey(usrRawPwd, 20)
/// return MessageDigest.isEqual(dk, hexStringToByteArray(dbPwdKeyValue))
/// ```
///
/// Note the iteration count is **hard-coded to 10** and ignores the
/// `iterations` field on the user document; that is replicated verbatim here so
/// the Flutter client accepts exactly the credentials the Kotlin client does.
/// Changing it would silently lock every existing user out.
class AndroidDecrypter {
  const AndroidDecrypter._();

  /// The salt is hashed as its raw UTF-8 bytes, matching Kotlin's
  /// `dbSalt?.toByteArray()` (which defaults to UTF-8).
  static const int _iterations = 10;
  static const int _derivedKeyLength = 20;

  static bool androidDecrypter(
    String? userId,
    String? rawPassword,
    String? derivedKeyHex,
    String? salt,
  ) {
    if (derivedKeyHex == null) return false;

    final Uint8List expected;
    try {
      expected = hexToBytes(derivedKeyHex);
    } on FormatException {
      // Kotlin catches the malformed-hex case and returns false rather than
      // propagating; a corrupt stored key must not crash the login screen.
      return false;
    }

    final derived = pbkdf2(
      hash: sha1,
      password: utf8.encode(rawPassword ?? ''),
      salt: utf8.encode(salt ?? ''),
      iterations: _iterations,
      derivedKeyLength: _derivedKeyLength,
    );

    return constantTimeEquals(derived, expected);
  }

  /// Equivalent of `MessageDigest.isEqual` — compares without an early exit so
  /// the comparison does not leak how much of the key matched.
  static bool constantTimeEquals(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    var diff = 0;
    for (var i = 0; i < a.length; i++) {
      diff |= a[i] ^ b[i];
    }
    return diff == 0;
  }

  /// Port of `AndroidDecrypter.hexStringToByteArray`.
  static Uint8List hexToBytes(String hex) {
    if (hex.length.isOdd) {
      throw FormatException('Odd-length hex string', hex);
    }
    final out = Uint8List(hex.length ~/ 2);
    for (var i = 0; i < out.length; i++) {
      final byte = int.tryParse(hex.substring(i * 2, i * 2 + 2), radix: 16);
      if (byte == null) {
        throw FormatException('Not a hex string', hex, i * 2);
      }
      out[i] = byte;
    }
    return out;
  }

  /// Port of `AndroidDecrypter.bytesToHex`.
  static String bytesToHex(List<int> bytes) {
    final buffer = StringBuffer();
    for (final byte in bytes) {
      buffer.write(byte.toRadixString(16).padLeft(2, '0'));
    }
    return buffer.toString();
  }
}
