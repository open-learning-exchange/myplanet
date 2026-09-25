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

  group('normalizeText', () {
    test('lowercases plain text', () {
      expect(normalizeText('HELLO'), 'hello');
      expect(normalizeText('HeLLo World'), 'hello world');
    });

    test('strips Latin-1 accented vowels', () {
      expect(normalizeText('café'), 'cafe');
      expect(normalizeText('Élan'), 'elan');
      expect(normalizeText('ÑOÑO'), 'nono');
      expect(normalizeText('über'), 'uber');
      expect(normalizeText('façade'), 'facade');
    });

    test('a search term and its accented source normalize the same way', () {
      // The contract that makes accent-insensitive search work: the search
      // term and the stored text collapse to the same normalized form.
      expect(normalizeText('cafe'), normalizeText('café'));
      expect(normalizeText('resume'), normalizeText('résumé'));
    });

    test('folds beyond the common French and Spanish accents', () {
      // These are the cases a hand-written decomposition table gets wrong.
      // A second `normalizeText` once lived in `text_normalize.dart` with its
      // own table covering only precomposed Latin vowels; chat search used it
      // while resource and course search used this one, so "skoda" found the
      // resource "Škoda" but not a chat about it. Pinning the wider folding
      // keeps a narrower reimplementation from coming back unnoticed.
      expect(normalizeText('Māori'), 'maori');
      expect(normalizeText('Łódź'), 'lodz');
      expect(normalizeText('Škoda'), 'skoda');
      expect(normalizeText('Çağrı'), 'cagri');
      expect(normalizeText('Ærø'), 'aero');
    });

    test('leaves unaccented text untouched', () {
      expect(normalizeText('plain text 123'), 'plain text 123');
    });

    test('handles an empty string', () {
      expect(normalizeText(''), '');
    });
  });
}
