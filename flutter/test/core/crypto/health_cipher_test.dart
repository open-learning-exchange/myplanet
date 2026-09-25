import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/crypto/health_cipher.dart';

void main() {
  // A fixed key/IV pair so the assertions below are about the format, not
  // about whatever the generator happened to produce.
  const key =
      '000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f';
  const iv = '0f0e0d0c0b0a09080706050403020100';

  test('round-trips a health payload', () {
    const plain = '{"diagnosis":"asthma","medications":"salbutamol"}';
    final encrypted = HealthCipher.encrypt(plain, key, iv);

    expect(encrypted, isNot(contains('asthma')));
    expect(HealthCipher.decrypt(encrypted, key, iv), plain);
  });

  test('prepends the IV to the ciphertext, hex encoded', () {
    // This is the wire format `AndroidDecrypter.encrypt` produces. Getting it
    // wrong means the Kotlin app and the Planet server cannot read anything
    // this app writes, which no round-trip test would notice.
    final encrypted = HealthCipher.encrypt('hello', key, iv);

    expect(encrypted, startsWith(iv));
    expect(RegExp(r'^[0-9a-f]+$').hasMatch(encrypted), isTrue);
    // 16-byte IV + one padded block, as hex.
    expect(encrypted.length, (16 + 16) * 2);
  });

  test('reads legacy ciphertext that carries no leading IV', () {
    // Records written before the IV was prepended are bare ciphertext.
    // `AndroidDecrypter.decrypt` still reads those, so this must too.
    final withIv = HealthCipher.encrypt('older record', key, iv);
    final legacy = withIv.substring(iv.length);

    expect(HealthCipher.decrypt(legacy, key, iv), 'older record');
  });

  test('a payload from another key decrypts to null, not an exception', () {
    // A re-installed device, or another user's record: the health screen has
    // to render "no data" rather than crash on it.
    final encrypted = HealthCipher.encrypt('secret', key, iv);
    const otherKey =
        'ffeeddccbbaa99887766554433221100ffeeddccbbaa998877665544332211ff';

    expect(HealthCipher.decrypt(encrypted, otherKey, iv), isNull);
  });

  test('malformed input decrypts to null', () {
    expect(HealthCipher.decrypt('not hex at all', key, iv), isNull);
    expect(HealthCipher.decrypt('', key, iv), isNull);
    expect(HealthCipher.decrypt(null, key, iv), isNull);
    expect(HealthCipher.decrypt('abcd', key, iv), isNull);
  });

  test('a missing key does not silently pass the plaintext through', () {
    // The dangerous failure is the one that looks like it worked. Encrypting
    // under a null key still has to produce ciphertext, not the input.
    final encrypted = HealthCipher.encrypt('{"notes":"private"}', null, null);
    expect(encrypted, isNot(contains('private')));
    // A null key decrypts to null by design (so does `AndroidDecrypter`);
    // empty hex is the same all-zero key material `encrypt` fell back to.
    expect(HealthCipher.decrypt(encrypted, '', ''), '{"notes":"private"}');
  });

  test('generated keys and IVs are the sizes AES needs', () {
    final generatedKey = HealthCipher.generateKey();
    final generatedIv = HealthCipher.generateIv();

    expect(generatedKey.length, 64); // 32 bytes of hex
    expect(generatedIv.length, 32); // 16 bytes of hex
    expect(generatedKey, isNot(HealthCipher.generateKey()));

    final encrypted = HealthCipher.encrypt('x', generatedKey, generatedIv);
    expect(HealthCipher.decrypt(encrypted, generatedKey, generatedIv), 'x');
  });

  test('short key material is padded rather than rejected', () {
    // `System.arraycopy` into a fixed 32-byte buffer is what Kotlin does, so a
    // short key is zero-extended on both sides and still round-trips.
    final encrypted = HealthCipher.encrypt('x', 'abcd', 'ef');
    expect(HealthCipher.decrypt(encrypted, 'abcd', 'ef'), 'x');
  });
}
