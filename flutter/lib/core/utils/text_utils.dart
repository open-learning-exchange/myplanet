import 'dart:convert';

import 'package:diacritic/diacritic.dart';

/// Port of `utils/Utilities.normalizeText`.
///
/// Lower-cases and strips combining marks so search is accent-insensitive —
/// "cafe" finds "Café". The Kotlin runs NFD normalisation then drops
/// `\p{InCombiningDiacriticalMarks}`; `removeDiacritics` is the equivalent for
/// the scripts myPlanet ships in, since `java.text.Normalizer` has no
/// `dart:core` counterpart.
///
/// The result is what lands in the `*TitleNormal` columns, which is what the
/// resource and course searches filter on.
String normalizeText(String value) => removeDiacritics(value).toLowerCase();

/// Port of `Utilities.toHex`: the UTF-8 bytes of [value] interpreted as one
/// big-endian unsigned integer, formatted lowercase —
/// `String.format("%x", BigInteger(1, value.toByteArray()))`.
///
/// This is not per-byte hex: leading zero bytes collapse into the number, and
/// the empty string is `"0"`. The per-user CouchDB database name
/// (`userdb-<hex(planetCode)>-<hex(name)>`) is built with it, so a divergent
/// encoding would read the wrong database — or none.
String toHexString(String value) {
  var result = BigInt.zero;
  for (final byte in utf8.encode(value)) {
    result = (result << 8) + BigInt.from(byte);
  }
  return result.toRadixString(16);
}
