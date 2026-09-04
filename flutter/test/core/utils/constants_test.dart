import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/utils/constants.dart';

void main() {
  group('memberLevels', () {
    test('contains the four proficiency tiers in order', () {
      expect(memberLevels, ['Beginner', 'Intermediate', 'Advanced', 'Expert']);
    });

    test('is not empty', () {
      expect(memberLevels, isNotEmpty);
    });

    test('has exactly four entries', () {
      expect(memberLevels.length, 4);
    });

    test('starts with Beginner', () {
      expect(memberLevels.first, 'Beginner');
    });

    test('ends with Expert', () {
      expect(memberLevels.last, 'Expert');
    });

    test('contains Intermediate', () {
      expect(memberLevels, contains('Intermediate'));
    });

    test('contains Advanced', () {
      expect(memberLevels, contains('Advanced'));
    });
  });

  group('memberLanguages', () {
    test('contains English', () {
      expect(memberLanguages, contains('English'));
    });

    test('contains Nepali in Devanagari', () {
      expect(memberLanguages, contains('नेपाली'));
    });

    test('contains French', () {
      expect(memberLanguages, contains('Français'));
    });

    test('contains Spanish', () {
      expect(memberLanguages, contains('Español'));
    });

    test('contains Arabic', () {
      expect(memberLanguages, contains('عربى'));
    });

    test('contains Somali', () {
      expect(memberLanguages, contains('Somali'));
    });

    test('has exactly six entries', () {
      expect(memberLanguages.length, 6);
    });

    test('starts with English', () {
      expect(memberLanguages.first, 'English');
    });

    test('is not empty', () {
      expect(memberLanguages, isNotEmpty);
    });

    test('has no duplicate entries', () {
      expect(memberLanguages.toSet().length, memberLanguages.length);
    });
  });

  group('memberLevels uniqueness', () {
    test('has no duplicate entries', () {
      expect(memberLevels.toSet().length, memberLevels.length);
    });
  });
}
