import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/utils/text_normalize.dart';

void main() {
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

    test('leaves unaccented text untouched', () {
      expect(normalizeText('plain text 123'), 'plain text 123');
    });

    test('handles an empty string', () {
      expect(normalizeText(''), '');
    });
  });
}
