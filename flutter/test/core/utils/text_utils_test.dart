import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/utils/text_utils.dart';

void main() {
  // Expected values computed against the Kotlin `String.format("%x",
  // BigInteger(1, value.toByteArray()))` — the encoding behind the
  // `userdb-<hex(planetCode)>-<hex(name)>` database names.
  group('toHexString', () {
    test('encodes ASCII as the big-endian hex of its UTF-8 bytes', () {
      expect(toHexString('planet'), '706c616e6574');
      expect(toHexString('alice'), '616c696365');
      expect(toHexString('earth'), '6561727468');
      expect(toHexString('x'), '78');
    });

    test('encodes the empty string as "0", as BigInteger zero formats', () {
      expect(toHexString(''), '0');
    });

    test('encodes multi-byte UTF-8 as one number, not per-byte pairs', () {
      // 'ñ' is C3 B1 in UTF-8; the Kotlin formats 0xC3B1, so "c3b1".
      expect(toHexString('ñ'), 'c3b1');
    });

    test('drops leading zero bytes, as the Kotlin BigInteger does', () {
      // '\x01a' is 0x0161 → "161"; per-byte hex would give "0161".
      expect(toHexString('\x01a'), '161');
    });
  });
}
