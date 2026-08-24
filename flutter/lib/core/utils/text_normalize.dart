/// Accent-insensitive text normalization for search.
///
/// Port of the Kotlin `Utilities.normalizeText`, which decomposes a string
/// to NFD, strips combining diacritical marks, and lowercases. Dart's core
/// library has no NFD normalizer and its `RegExp` does not accept the
/// `\p{InCombiningDiacriticalMarks}` block name, so this bridges both gaps by
/// hand: precomposed Latin letters are rewritten to base + combining mark,
/// then combining marks (U+0300–U+036F) are dropped in the same pass. That
/// covers the accented letters in the languages this app ships (French,
/// Spanish, plus general Latin diacritics). Combining marks arriving
/// already-decomposed (from a server document) are stripped too, so both
/// forms normalize the same way.
library;

/// Normalizes [text] for search: lowercased and stripped of diacritics.
///
/// "café" → "cafe", "NIÑO" → "nino". Identical to the Kotlin
/// `Utilities.normalizeText` output for the Latin block.
String normalizeText(String text) {
  if (identical(text, '')) return '';
  final out = <int>[];
  for (final code in text.toLowerCase().runes) {
    final decomp = _decomp[code];
    if (decomp != null) {
      for (final part in decomp) {
        if (part < 0x0300 || part > 0x036F) out.add(part);
      }
    } else if (code < 0x0300 || code > 0x036F) {
      out.add(code);
    }
  }
  return String.fromCharCodes(out);
}

const Map<int, List<int>> _decomp = {
  // Latin-1 Supplement (U+00C0–U+00FF) — covers French and Spanish accented
  // vowels, the cedilla, and ñ. Uppercase entries are included because a
  // precomposed char lowercases to its lowercase precomposed counterpart
  // (Æ→æ), not to a decomposed form, before this table runs.
  0x00C0: [0x0061, 0x0300], // À
  0x00C1: [0x0061, 0x0301], // Á
  0x00C2: [0x0061, 0x0302], // Â
  0x00C3: [0x0061, 0x0303], // Ã
  0x00C4: [0x0061, 0x0308], // Ä
  0x00C5: [0x0061, 0x030A], // Å
  0x00C7: [0x0063, 0x0327], // Ç
  0x00C8: [0x0065, 0x0300], // È
  0x00C9: [0x0065, 0x0301], // É
  0x00CA: [0x0065, 0x0302], // Ê
  0x00CB: [0x0065, 0x0308], // Ë
  0x00CC: [0x0069, 0x0300], // Ì
  0x00CD: [0x0069, 0x0301], // Í
  0x00CE: [0x0069, 0x0302], // Î
  0x00CF: [0x0069, 0x0308], // Ï
  0x00D1: [0x006E, 0x0303], // Ñ
  0x00D2: [0x006F, 0x0300], // Ò
  0x00D3: [0x006F, 0x0301], // Ó
  0x00D4: [0x006F, 0x0302], // Ô
  0x00D5: [0x006F, 0x0303], // Õ
  0x00D6: [0x006F, 0x0308], // Ö
  0x00D9: [0x0075, 0x0300], // Ù
  0x00DA: [0x0075, 0x0301], // Ú
  0x00DB: [0x0075, 0x0302], // Û
  0x00DC: [0x0075, 0x0308], // Ü
  0x00DD: [0x0079, 0x0301], // Ý
  0x00E0: [0x0061, 0x0300], // à
  0x00E1: [0x0061, 0x0301], // á
  0x00E2: [0x0061, 0x0302], // â
  0x00E3: [0x0061, 0x0303], // ã
  0x00E4: [0x0061, 0x0308], // ä
  0x00E5: [0x0061, 0x030A], // å
  0x00E7: [0x0063, 0x0327], // ç
  0x00E8: [0x0065, 0x0300], // è
  0x00E9: [0x0065, 0x0301], // é
  0x00EA: [0x0065, 0x0302], // ê
  0x00EB: [0x0065, 0x0308], // ë
  0x00EC: [0x0069, 0x0300], // ì
  0x00ED: [0x0069, 0x0301], // í
  0x00EE: [0x0069, 0x0302], // î
  0x00EF: [0x0069, 0x0308], // ï
  0x00F1: [0x006E, 0x0303], // ñ
  0x00F2: [0x006F, 0x0300], // ò
  0x00F3: [0x006F, 0x0301], // ó
  0x00F4: [0x006F, 0x0302], // ô
  0x00F5: [0x006F, 0x0303], // õ
  0x00F6: [0x006F, 0x0308], // ö
  0x00F9: [0x0075, 0x0300], // ù
  0x00FA: [0x0075, 0x0301], // ú
  0x00FB: [0x0075, 0x0302], // û
  0x00FC: [0x0075, 0x0308], // ü
  0x00FD: [0x0079, 0x0301], // ý
  0x00FF: [0x0079, 0x0308], // ÿ
};
