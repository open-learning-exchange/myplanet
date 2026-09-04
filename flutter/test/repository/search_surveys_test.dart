import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/surveys_repository.dart';

/// A native survey (no `sourceSurveyId`), sorted by `createdDate`.
SurveyRow _native(String id, String? name, {int createdDate = 0}) => SurveyRow(
  id: id,
  name: name,
  createdDate: createdDate,
  updatedDate: 0,
  adoptionDate: 0,
  totalMarks: 0,
  isFromNation: false,
  teamShareAllowed: false,
);

/// An adopted survey — copied from a source, so it carries a `sourceSurveyId`
/// and an `adoptionDate`.
SurveyRow _adopted(
  String id,
  String? name, {
  required String sourceSurveyId,
  int adoptionDate = 0,
  int createdDate = 0,
}) => SurveyRow(
  id: id,
  name: name,
  sourceSurveyId: sourceSurveyId,
  adoptionDate: adoptionDate,
  createdDate: createdDate,
  updatedDate: 0,
  totalMarks: 0,
  isFromNation: false,
  teamShareAllowed: false,
);

void main() {
  group('searchSurveys', () {
    test('returns every item for a blank query', () {
      final items = [_native('1', 'Alpha'), _native('2', 'Beta')];
      expect(searchSurveys(items, '   '), items);
    });

    test('ranks a prefix match ahead of a substring match', () {
      final items = [
        _native('a', 'Advanced Math'),
        _native('b', 'Math Survey'),
      ];
      final result = searchSurveys(items, 'math');
      expect(result.map((r) => r.id), ['b', 'a']);
    });

    test('splits the query into words and matches each anywhere', () {
      final items = [
        _native('a', 'Basic Mathematics'),
        _native('b', 'Chemistry'),
      ];
      final result = searchSurveys(items, 'math basic');
      expect(result.map((r) => r.id), ['a']);
    });

    test('folds accents so "cafe" finds "Café"', () {
      final items = [_native('a', 'Café Survey')];
      expect(searchSurveys(items, 'cafe').map((r) => r.id), ['a']);
    });

    test('is case-insensitive', () {
      final items = [_native('a', 'Algebra')];
      expect(searchSurveys(items, 'ALG').map((r) => r.id), ['a']);
    });

    test('never searches the description', () {
      // The Kotlin `filter` reads only `name`; a query matching the description
      // alone must not surface the survey.
      final item = _native(
        'a',
        'Health',
      ).copyWith(description: const Value('Math talk'));
      expect(searchSurveys([item], 'math'), isEmpty);
    });

    test('skips surveys with no name', () {
      final items = [_native('a', null), _native('b', 'Math')];
      expect(searchSurveys(items, 'math').map((r) => r.id), ['b']);
    });

    test('preserves input order within each bucket', () {
      final items = [
        _native('a', 'Math One'),
        _native('b', 'Math Two'),
        _native('c', 'Not Math'),
      ];
      expect(searchSurveys(items, 'math').map((r) => r.id), ['a', 'b', 'c']);
    });
  });

  group('surveySortDate', () {
    test('uses createdDate for a native survey', () {
      expect(surveySortDate(_native('a', 'N', createdDate: 50)), 50);
    });

    test('uses adoptionDate for an adopted survey when set', () {
      expect(
        surveySortDate(
          _adopted(
            'a',
            'N',
            sourceSurveyId: 'src',
            adoptionDate: 80,
            createdDate: 50,
          ),
        ),
        80,
      );
    });

    test('falls back to createdDate when adoptionDate is 0', () {
      expect(
        surveySortDate(
          _adopted(
            'a',
            'N',
            sourceSurveyId: 'src',
            adoptionDate: 0,
            createdDate: 50,
          ),
        ),
        50,
      );
    });
  });
}
