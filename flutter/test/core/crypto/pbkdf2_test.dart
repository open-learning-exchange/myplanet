import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/crypto/android_decrypter.dart';
import 'package:myplanet/core/crypto/pbkdf2.dart';

/// The PBKDF2 implementation replaces the `jpbkdf2` library the Kotlin uses, so
/// it is checked against the published RFC 6070 vectors rather than against
/// itself. If these pass, the derivation is correct independently of anything
/// else in this repo.
void main() {
  String derive(String password, String salt, int iterations, int length) {
    return AndroidDecrypter.bytesToHex(
      pbkdf2(
        hash: sha1,
        password: utf8.encode(password),
        salt: utf8.encode(salt),
        iterations: iterations,
        derivedKeyLength: length,
      ),
    );
  }

  group('pbkdf2 — RFC 6070 test vectors', () {
    test('c=1, dkLen=20', () {
      expect(
        derive('password', 'salt', 1, 20),
        '0c60c80f961f0e71f3a9b524af6012062fe037a6',
      );
    });

    test('c=2, dkLen=20', () {
      expect(
        derive('password', 'salt', 2, 20),
        'ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957',
      );
    });

    test('c=4096, dkLen=20', () {
      expect(
        derive('password', 'salt', 4096, 20),
        '4b007901b765489abead49d926f721d065a429c1',
      );
    });

    /// dkLen 25 > hLen 20, so this exercises the multi-block path.
    test('c=4096, dkLen=25 spans two blocks', () {
      expect(
        derive(
          'passwordPASSWORDpassword',
          'saltSALTsaltSALTsaltSALTsaltSALTsalt',
          4096,
          25,
        ),
        '3d2eec4fe41c849b80c8d83662c0e44a8b291a964cf2f07038',
      );
    });
  });

  group('pbkdf2 — argument validation', () {
    test('rejects a zero iteration count', () {
      expect(
        () => pbkdf2(
          hash: sha1,
          password: utf8.encode('a'),
          salt: utf8.encode('b'),
          iterations: 0,
          derivedKeyLength: 20,
        ),
        throwsArgumentError,
      );
    });

    test('rejects a zero key length', () {
      expect(
        () => pbkdf2(
          hash: sha1,
          password: utf8.encode('a'),
          salt: utf8.encode('b'),
          iterations: 10,
          derivedKeyLength: 0,
        ),
        throwsArgumentError,
      );
    });
  });
}
