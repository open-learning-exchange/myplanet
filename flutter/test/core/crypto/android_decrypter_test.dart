import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/crypto/android_decrypter.dart';

/// The expected digests below were produced independently with Python's
/// `hashlib.pbkdf2_hmac('sha1', password, salt, 10, 20)` — the same parameters
/// `AndroidDecrypter` pins — so a regression in the Dart port cannot hide behind
/// a matching bug in the test.
void main() {
  const password = 'correct-horse';
  const salt = 'a3f1c9d2e5b74806a1c2d3e4f5061728';
  const derivedKey = 'ddb696f6f34c21547a45f8034c6e41daf63b3ce3';

  group('androidDecrypter', () {
    test('accepts the correct password', () {
      expect(
        AndroidDecrypter.androidDecrypter('user', password, derivedKey, salt),
        isTrue,
      );
    });

    test('accepts a second independently generated credential', () {
      expect(
        AndroidDecrypter.androidDecrypter(
          'user',
          'P@ssw0rd!',
          '5b494be925819c8a4c12d622b3580f7210fcf4b3',
          'deadbeefcafebabe',
        ),
        isTrue,
      );
    });

    test('rejects a wrong password', () {
      expect(
        AndroidDecrypter.androidDecrypter('user', 'wrong', derivedKey, salt),
        isFalse,
      );
    });

    test('rejects the right password against a different salt', () {
      expect(
        AndroidDecrypter.androidDecrypter(
          'user',
          password,
          derivedKey,
          'a3f1c9d2e5b74806a1c2d3e4f5061729',
        ),
        isFalse,
      );
    });

    test('rejects a null derived key rather than throwing', () {
      expect(
        AndroidDecrypter.androidDecrypter('user', password, null, salt),
        isFalse,
      );
    });

    test('rejects a malformed derived key rather than throwing', () {
      expect(
        AndroidDecrypter.androidDecrypter('user', password, 'nothex!!', salt),
        isFalse,
      );
      expect(
        AndroidDecrypter.androidDecrypter('user', password, 'abc', salt),
        isFalse,
      );
    });

    test('rejects an empty password', () {
      expect(
        AndroidDecrypter.androidDecrypter('user', '', derivedKey, salt),
        isFalse,
      );
    });

    test('is unaffected by the user id, which the Kotlin also ignores', () {
      expect(
        AndroidDecrypter.androidDecrypter(
          'someone-else',
          password,
          derivedKey,
          salt,
        ),
        isTrue,
      );
    });
  });

  group('hex helpers', () {
    test('round-trips bytes', () {
      final bytes = AndroidDecrypter.hexToBytes('00ff1080');
      expect(bytes, [0, 255, 16, 128]);
      expect(AndroidDecrypter.bytesToHex(bytes), '00ff1080');
    });

    test('rejects odd-length input', () {
      expect(() => AndroidDecrypter.hexToBytes('abc'), throwsFormatException);
    });

    test('rejects non-hex characters', () {
      expect(() => AndroidDecrypter.hexToBytes('zz'), throwsFormatException);
    });
  });

  group('constantTimeEquals', () {
    test('is true only for identical byte lists', () {
      expect(AndroidDecrypter.constantTimeEquals([1, 2, 3], [1, 2, 3]), isTrue);
      expect(
        AndroidDecrypter.constantTimeEquals([1, 2, 3], [1, 2, 4]),
        isFalse,
      );
      expect(AndroidDecrypter.constantTimeEquals([1, 2], [1, 2, 3]), isFalse);
      expect(AndroidDecrypter.constantTimeEquals([], []), isTrue);
    });
  });
}
