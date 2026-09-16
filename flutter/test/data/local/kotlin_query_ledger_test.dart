import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';

/// The **coverage ledger** for the Kotlin→Dart query differential.
///
/// Phase 158 retired harvesting-by-commit-walk. Master moves 110–129 commits a
/// week, so a walk can never catch up, and the Follows it did find were all
/// findable by comparing the two implementations' *current state* — Phase 156's
/// `LIKE` hole and missing `ORDER BY`, Phase 96's ranked search. What a
/// state-to-state comparison needs and a commit walk supplied for free is an
/// answer to **"what changed since anyone last looked?"** This file is that
/// answer, and it costs no history.
///
/// Kotlin keeps its SQL literally, in `@Query` annotations. [_kotlinQueries]
/// extracts all of them and digests each statement; [_compared] records the
/// digest each query had **when a lane read it against the port and reached a
/// verdict**. So:
///
/// * a compared statement that changes upstream → its digest moves → **red**,
///   naming the query to re-compare. That is the harvest signal, delivered by
///   the Flutter gate rather than by reading 136 commits.
/// * a compared query that is renamed or deleted → **red**.
/// * a query added or removed anywhere in the corpus → [_corpusSize] is wrong
///   → **red**, so new SQL cannot arrive unnoticed.
///
/// **This is not an assertion that the compared queries are at parity.** It
/// records that they were *looked at*, and when. A verdict of "at parity",
/// "diverges, fixed here" and "diverges, reported" are all equally in scope;
/// what the ledger buys is that none of them silently goes stale.
///
/// **To extend it:** compare a query against its port counterpart, then add
/// `'<Dao>.<method>': '<digest>'` — the failure message prints the digest — and
/// bump [_comparedCount]. Do not add an entry for a query you have not read.
/// An inflated ledger is worse than a short one: it is the *"a test that
/// cannot fail reads as coverage"* shape, one level up.
void main() {
  late Map<String, String> corpus;

  setUpAll(() => corpus = _kotlinQueries());

  test('the corpus is the size the ledger was built against', () {
    expect(
      corpus.length,
      _corpusSize,
      reason:
          'Kotlin has ${corpus.length} @Query annotations, not $_corpusSize. '
          'Queries were added or removed upstream. Triage the difference '
          'against the port, then update _corpusSize — and add the new ones '
          'to _compared if you compared them.',
    );
  });

  test('every ledger entry still names a query that exists', () {
    final gone = _compared.keys.where((k) => !corpus.containsKey(k)).toList()
      ..sort();
    expect(
      gone,
      isEmpty,
      reason:
          'These were compared against the port and their Kotlin methods have '
          'since been renamed or deleted. Find where each went (or confirm it '
          'is gone), re-compare, and update the ledger: $gone',
    );
  });

  test('no compared statement has changed since it was compared', () {
    final drifted = <String>[];
    for (final entry in _compared.entries) {
      final now = corpus[entry.key];
      if (now == null) continue; // reported by the test above
      if (now != entry.value) {
        drifted.add('${entry.key}: ${entry.value} -> $now');
      }
    }
    drifted.sort();
    expect(
      drifted,
      isEmpty,
      reason:
          'The Kotlin SQL changed under a comparison someone already made. '
          'Re-read each against its port counterpart, act on any divergence, '
          'then paste the new digest into _compared:\n  '
          '${drifted.join('\n  ')}',
    );
  });

  test('the ledger reports its own coverage honestly', () {
    // Fails in both directions. Coverage rising is the good direction and it
    // still has to be recorded — an exemption that only suppresses is a silent
    // debt (Phase 157).
    expect(
      _compared.length,
      _comparedCount,
      reason:
          '_compared holds ${_compared.length} entries against a declared '
          '$_comparedCount. Update _comparedCount.',
    );
    expect(
      _compared.length,
      lessThanOrEqualTo(corpus.length),
      reason: 'the ledger cannot cover more queries than exist',
    );
  });

  test('coverage is stated where the next round will read it', () {
    // Phase 158 Lane 1 reached 77 of 312. The remainder is the next round's
    // work, and an honest partial number is worth more than an implied
    // complete one — so this is asserted rather than left in a PR body where
    // it would rot.
    final uncovered = corpus.length - _compared.length;
    expect(uncovered, 312 - 77);
  });
}

/// Every `@Query` in the Kotlin DAOs, keyed `<DaoFile>.<method>` and valued
/// with a digest of the statement.
///
/// The parser mirrors how these files are actually written: a `@Query(...)`
/// whose argument may be a raw `"""…"""` string, a single `"…"`, or several
/// concatenated with `+`; and a `fun` declaration that may sit on the same
/// line as the annotation (`TeamDao`, `UserDao` are written that way) or on a
/// following line, possibly after further annotations. Whitespace inside the
/// statement is collapsed, so reformatting a long query across lines is not a
/// change and does not cost a false failure.
Map<String, String> _kotlinQueries() {
  final dir = Directory(
    '../app/src/main/java/org/ole/planet/myplanet/data/room/dao',
  );
  expect(
    dir.existsSync(),
    isTrue,
    reason:
        'the Kotlin DAO directory is missing at ${dir.path} — this test reads '
        'it relative to `flutter/`, which is where the gate runs',
  );

  final queries = <String, String>{};
  final funName = RegExp(r'fun (\w+)');
  final tripleQuoted = RegExp('"""(.*?)"""', dotAll: true);
  final quoted = RegExp(r'"((?:[^"\\]|\\.)*)"', dotAll: true);
  final whitespace = RegExp(r'\s+');

  final files =
      dir
          .listSync()
          .whereType<File>()
          .where((f) => f.path.endsWith('.kt'))
          .toList()
        ..sort((a, b) => a.path.compareTo(b.path));

  for (final file in files) {
    final dao = file.uri.pathSegments.last.replaceAll('.kt', '');
    final lines = file.readAsStringSync().split('\n');
    var i = 0;
    while (i < lines.length) {
      if (!lines[i].trimLeft().startsWith('@Query(')) {
        i++;
        continue;
      }
      final start = i;
      final buffer = StringBuffer();
      var depth = 0;
      var opened = false;
      while (i < lines.length) {
        buffer.writeln(lines[i]);
        for (final ch in lines[i].split('')) {
          if (ch == '(') {
            depth++;
            opened = true;
          } else if (ch == ')') {
            depth--;
          }
        }
        if (opened && depth == 0) break;
        i++;
      }
      final block = buffer.toString();

      var signature = '';
      for (var j = i + 1; j < lines.length && j < i + 8; j++) {
        final line = lines[j].trim();
        if (line.startsWith('@') || line.isEmpty) continue;
        signature = line;
        break;
      }
      final match = funName.firstMatch(block) ?? funName.firstMatch(signature);
      final name = match?.group(1) ?? 'line${start + 1}';

      final triples = tripleQuoted
          .allMatches(block)
          .map((m) => m.group(1)!)
          .toList();
      final parts = triples.isNotEmpty
          ? triples
          : quoted.allMatches(block).map((m) => m.group(1)!).toList();
      final sql = parts.join(' ').replaceAll(whitespace, ' ').trim();

      var key = '$dao.$name';
      var n = 2;
      while (queries.containsKey(key)) {
        key = '$dao.$name#${n++}';
      }
      queries[key] = sha1.convert(utf8.encode(sql)).toString().substring(0, 12);
      i++;
    }
  }
  return queries;
}

/// `@Query` annotations in `app/src/main/.../data/room/dao/`, as of Phase 158.
const _corpusSize = 312;

/// Entries in [_compared], stated separately so the map and the claim about it
/// cannot drift apart.
const _comparedCount = 77;

/// Queries a lane has read against the port's Drift builder and reached a
/// verdict on, with the digest the statement had at that moment.
///
/// Phase 158 Lane 1 seeded all 77, working outward from the statements
/// carrying `LIKE`, `ORDER BY`, `IS :param` and `LIMIT` — the classes where a
/// divergence is silent. Two produced fixes (`NewsDao.countTopLevelByTeam`'s
/// family and `TeamDao.countByTeamIdAndDocType`); the rest were confirmed at
/// parity or reported.
const _compared = <String, String>{
  'TeamDao.getResourceIdsByTeamId': '2e5632059e3b',
  'ExamDao.getFirstByStepId': 'e4c80c44fd26',
  'ExamDao.getByStepId': '2095e7c10090',
  'ExamDao.getByStepIds': '6ffcac1c67b0',
  'MyLibraryDao.getTeamPrivate': 'b71238974e8d',
  'TeamDao.getEligibleNextLeaderCandidates': '8c8ad4450999',
  'TeamDao.teamNameExists': '16cc27e97d3e',
  'MeetupDao.getPendingUploads': '3ef3f76a9cac',
  'TeamTaskDao.getPendingUploads': '36f7b43063be',
  'TeamLogDao.getRecentTeamVisits': 'c56d3d9701ce',
  'TeamLogDao.getLastVisit': '4ad257e581b7',
  'NewsDao.getNewsAndRepliesIds': '4162382f2192',
  'NewsDao.getPlanetMessages': '083f202da4ee',
  'SubmissionDao.getExamSubmissionsByUser': '029abbf7665e',
  'AchievementDao.getPendingUploads': '5d25f4a7f470',
  'CertificationDao.countByCourseId': '1c1ca01da1d3',
  'CommunityDao.getAllSorted': 'fe4003bc2615',
  'CourseDao.getForUserPattern': '0d6a10018627',
  'CourseDao.observeForUserPattern': '0d6a10018627',
  'CourseProgressDao.findByCourseUserAndStep': '43ae768e4eb4',
  'CourseProgressDao.getByUser': '5021f4597653',
  'CourseProgressDao.getByUserAndCourse': '36f1a76f7dd7',
  'CourseProgressDao.getByUserAndCourseIds': '369e35bc1905',
  'CourseProgressDao.getPendingUploads': '752fa36be31d',
  'FeedbackDao.getAllSortedFlow': 'e726818fe0c9',
  'FeedbackDao.getByOwnerFlow': '8a6b54055ed4',
  'MyLibraryDao.countPublicNeedingUpdateForUserPattern': '443e139c7acd',
  'MyLibraryDao.deleteAllStalePublic': 'ba1f591ddd74',
  'MyLibraryDao.deleteStalePublicNotIn': '092f4c48c1dd',
  'MyLibraryDao.getByResourceIdsNotUserPattern': 'aa2d3f2abd30',
  'MyLibraryDao.getForUserPattern': '733779683499',
  'MyLibraryDao.getForUserPatternFlow': '733779683499',
  'MyLibraryDao.getIdsForUserPattern': '34ece08a734f',
  'MyLibraryDao.getPendingDownloadsForUserPatternFlow': '7e79ebca5699',
  'MyLibraryDao.getPublicForUserPattern': '2f3f685e2304',
  'MyLibraryDao.getPublicNeedingUpdate': '1b16c6bfac70',
  'MyLibraryDao.getPublicNeedingUpdateForUserPattern': '5021f431bba1',
  'MyLibraryDao.getPublicNotUserPattern': '339d656f2afd',
  'MyLibraryDao.getRecentForUserPatternFlow': '80fd2df900aa',
  'MyLibraryDao.getSyncable': '2e24afc08b14',
  'MyLifeDao.countByUserId': '135ab762355d',
  'MyLifeDao.getByUserId': 'ddf6bd918ac4',
  'MyLifeDao.getVisibleByUserId': '4e71e9bb7a0e',
  'NewsDao.countDistinctCommunityVoiceDates': 'e03a9ad20338',
  'NewsDao.countDistinctCommunityVoiceDatesForUser': '5bfceedebe52',
  'NewsDao.countTeamChats': '95202314b030',
  'NewsDao.countTopLevelByTeam': 'aa6c81fd7cd6',
  'NewsDao.getByNewsId': '5fc9981fbfb7',
  'NewsDao.getReplies': '9fccefc24993',
  'NewsDao.getReplyCount': 'dc0dad1ce2aa',
  'NewsDao.getTopLevelByTeam': '2b3afdf288a2',
  'NewsDao.getTopLevelByTeamFlow': '2b3afdf288a2',
  'NewsDao.getTopLevelMessages': '8f3abe170999',
  'NewsDao.getTopLevelMessagesFlow': '8f3abe170999',
  'NotificationDao.getNotifications': '44ead9c797b4',
  'NotificationDao.getPendingSyncNotifications': '1440627fce08',
  'NotificationDao.markSummaryAsRead': '0d7a161b8a31',
  'OfflineActivityDao.getLatestByType': '4668e1ff4629',
  'PersonalDao.getByUserIdFlow': '937cb6fe5767',
  'RatingDao.findByTypeUserItem': 'bce509132996',
  'RatingDao.getAggregate': '9f8e2706f9a0',
  'RatingDao.getByType': '464a8f98df80',
  'RatingDao.getPendingUploads': '0a6522598b54',
  'ResourceActivityDao.countByUserAndType': 'e6437c6b6bf7',
  'ResourceActivityDao.getMostOpenedResource': '5cc9aeaa99f7',
  'ResourceActivityDao.getPendingSyncUploads': '2ac75165d83d',
  'ResourceActivityDao.getPendingUploads': '7c9573fd449c',
  'ResourceActivityDao.observeByUserAndType': '822b20fd1b91',
  'TagDao.getParentTags': '8f07f240526f',
  'TeamDao.countByTeamIdAndDocType': '9b263b0a376e',
  'UserDao.getById': 'fe9f574c3621',
  'UserDao.getByName': '6bdb69d68461',
  'UserDao.getByNameIgnoreCase': 'c1245a3cb25d',
  'UserDao.getPendingSyncUsers': '0951b32b4787',
  'UserDao.getSyncedUsers': 'e16d1cba09cd',
  'UserDao.getUsersForHealthSync': '691c508bc3ae',
  'UserDao.search': '56f1c4215fdc',
};
