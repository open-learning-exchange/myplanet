import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Pins Phase 112's unification: **one** guest predicate, in one place.
///
/// **Why this exists.** Kotlin spells "is this user a guest?" five ways —
/// `_id?.startsWith("guest_")` (`UserEntity.isGuest`, `migrateGuestUser`,
/// `cleanupDuplicateUsers`, `insertUsersFromSync`),
/// `SUBSTR(_id, 1, 6) = 'guest_'` (`UserDao.getGuestUserByName`),
/// `_id.orEmpty().startsWith("guest")` (`validateUsername`),
/// `id.startsWith("guest")` (every UI gate, `getSyncedUserByName`) and
/// `SUBSTR(id, 1, 5) != 'guest'` (`UserDao.getSyncedUsers`) — and they always
/// agree, because `createGuestUser` writes the same `guest_<username>` string
/// to both id columns (`buildGuestUserJson` -> `applyJsonToUser`).
///
/// The port inherited three of those spellings across unrelated files and has
/// **no path that creates a guest row**, so nothing enforced the equality that
/// makes them interchangeable. Phase 107 found the divergence; this test stops
/// a fourth spelling reappearing, which a code review of one file cannot.
///
/// If this fails: do not add another `startsWith('guest…')`. Call
/// `UserMapper.isGuest(row)` for a `UserRow`, or `UserMapper.isGuestId(id)`
/// for a bare id string.
void main() {
  test('the guest prefix is written down exactly once in lib/', () {
    // `UserMapper` is the one definition, so the literal lives there.
    const owner = 'lib/data/local/user_mapper.dart';
    final pattern = RegExp(r'''["']guest''');

    final offenders = <String>[];
    for (final entity in Directory('lib').listSync(recursive: true)) {
      if (entity is! File || !entity.path.endsWith('.dart')) continue;
      // Generated sources are gitignored and rebuilt; they carry no rules.
      if (entity.path.endsWith('.g.dart')) continue;
      if (entity.path == owner) continue;
      // Drift DAO query bodies are exempt, and deliberately so. Three Kotlin
      // DAOs spell the rule in SQL as `NOT LIKE 'guest%'`
      // (`AchievementDao.kt:19`, `CourseProgressDao.kt:28`,
      // `RatingDao.kt:35`) and the port's four `like('guest%')` predicates are
      // faithful transcriptions of that text. A SQL `WHERE` cannot call a Dart
      // predicate, so pinning those to the Kotlin query text is the closest
      // thing to one source of truth available there. They cannot disagree
      // with `guestIdPrefix` on any id either app writes — see
      // `UserMapper.isGuestId`.
      if (entity.path == 'lib/data/local/app_database.dart') continue;

      final lines = entity.readAsLinesSync();
      for (var i = 0; i < lines.length; i++) {
        final line = lines[i];
        // Comments and doc comments may quote the Kotlin freely.
        final code = line.trimLeft();
        if (code.startsWith('//') || code.startsWith('///')) continue;
        if (pattern.hasMatch(line)) {
          offenders.add('${entity.path}:${i + 1}: ${line.trim()}');
        }
      }
    }

    expect(
      offenders,
      isEmpty,
      reason:
          'A guest id prefix appears outside $owner. Use '
          'UserMapper.isGuest / UserMapper.isGuestId instead:\n'
          '${offenders.join('\n')}',
    );
  });
}
