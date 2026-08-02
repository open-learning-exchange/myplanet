import 'dart:typed_data';

import 'package:crypto/crypto.dart';

/// PBKDF2 (RFC 2898 / RFC 8018) over an arbitrary HMAC.
///
/// The Kotlin app pulls this in as `de.rtner.security.auth.spi.PBKDF2Engine`
/// (the `jpbkdf2` library). There is no equivalent Dart package in wide use, so
/// the derivation is implemented here directly on top of `package:crypto`'s
/// [Hmac]. It is verified against the RFC 6070 test vectors in
/// `test/core/crypto/pbkdf2_test.dart`.
Uint8List pbkdf2({
  required Hash hash,
  required List<int> password,
  required List<int> salt,
  required int iterations,
  required int derivedKeyLength,
}) {
  if (iterations < 1) {
    throw ArgumentError.value(iterations, 'iterations', 'must be >= 1');
  }
  if (derivedKeyLength < 1) {
    throw ArgumentError.value(
      derivedKeyLength,
      'derivedKeyLength',
      'must be >= 1',
    );
  }

  final hmac = Hmac(hash, password);
  final hLen = hmac.convert(const <int>[]).bytes.length;
  final blockCount = (derivedKeyLength + hLen - 1) ~/ hLen;

  final output = Uint8List(blockCount * hLen);
  for (var block = 1; block <= blockCount; block++) {
    final t = _deriveBlock(hmac, salt, iterations, block);
    output.setRange((block - 1) * hLen, block * hLen, t);
  }

  return Uint8List.sublistView(output, 0, derivedKeyLength);
}

/// F(P, S, c, i) = U_1 xor U_2 xor ... xor U_c
Uint8List _deriveBlock(Hmac hmac, List<int> salt, int iterations, int block) {
  final seed = Uint8List(salt.length + 4)
    ..setRange(0, salt.length, salt)
    // INT(i), a four-octet big-endian encoding of the block index.
    ..[salt.length] = (block >> 24) & 0xff
    ..[salt.length + 1] = (block >> 16) & 0xff
    ..[salt.length + 2] = (block >> 8) & 0xff
    ..[salt.length + 3] = block & 0xff;

  var u = Uint8List.fromList(hmac.convert(seed).bytes);
  final result = Uint8List.fromList(u);

  for (var i = 1; i < iterations; i++) {
    u = Uint8List.fromList(hmac.convert(u).bytes);
    for (var j = 0; j < result.length; j++) {
      result[j] ^= u[j];
    }
  }

  return result;
}
