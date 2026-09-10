import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/utils/text_utils.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/resources_repository.dart';

/// Builds a [MyLibraryRow] with [MyLibraryTable.titleNormal] populated the way
/// `MyLibraryMapper` does (diacritic-folded, lower-cased), so the search sees
/// the same column the production read path populates.
MyLibraryRow _row(String id, String title) => MyLibraryRow(
  id: id,
  userId: const [],
  title: title,
  titleNormal: normalizeText(title),
  resourceOffline: false,
  createdDate: 0,
  timesRated: 0,
  resourceFor: const [],
  subject: const [],
  level: const [],
  tag: const [],
  languages: const [],
  isPrivate: false,
);

void main() {
  group('searchResources', () {
    test('returns every item for a blank query', () {
      final items = [_row('1', 'Alpha'), _row('2', 'Beta')];
      expect(searchResources(items, '   '), items);
    });

    test('ranks a prefix match ahead of a substring match', () {
      // "Math Basics" starts with "math"; "Advanced Math" only contains it.
      final items = [_row('a', 'Advanced Math'), _row('b', 'Math Basics')];
      final result = searchResources(items, 'math');
      expect(result.map((r) => r.id), ['b', 'a']);
    });

    test('splits the query into words and matches each anywhere', () {
      // A flat `LIKE '%math basic%'` would miss "Basic Math"; the port must
      // find it because both words appear, in any order.
      final items = [_row('a', 'Basic Mathematics'), _row('b', 'Chemistry')];
      final result = searchResources(items, 'math basic');
      expect(result.map((r) => r.id), ['a']);
    });

    test('matches across diacritics', () {
      final items = [_row('a', 'Café Recipes')];
      expect(searchResources(items, 'cafe').map((r) => r.id), ['a']);
      expect(searchResources(items, 'café').map((r) => r.id), ['a']);
    });

    test('matches case-insensitively', () {
      final items = [_row('a', 'Algebra')];
      expect(searchResources(items, 'ALG').map((r) => r.id), ['a']);
    });

    test('excludes items that share no word with the query', () {
      final items = [_row('a', 'Algebra'), _row('b', 'Biology')];
      expect(searchResources(items, 'chemistry'), isEmpty);
    });

    test('a word that appears in none of the titles drops every item', () {
      // "all parts contained" is an AND: one absent word excludes the row.
      final items = [_row('a', 'Math Basics')];
      expect(searchResources(items, 'math chemistry'), isEmpty);
    });

    test('keeps the input order within each bucket', () {
      // Both start with "math"; their relative order is preserved.
      final items = [_row('a', 'Math Alpha'), _row('b', 'Math Beta')];
      final result = searchResources(items, 'math');
      expect(result.map((r) => r.id), ['a', 'b']);
    });

    test('falls back to normalizing title when titleNormal is null', () {
      final row = MyLibraryRow(
        id: 'a',
        userId: const [],
        title: 'Café',
        titleNormal: null,
        resourceOffline: false,
        createdDate: 0,
        timesRated: 0,
        resourceFor: const [],
        subject: const [],
        level: const [],
        tag: const [],
        languages: const [],
        isPrivate: false,
      );
      expect(searchResources([row], 'cafe').map((r) => r.id), ['a']);
    });
  });
}
