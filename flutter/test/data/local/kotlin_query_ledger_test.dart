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
    //
    // 78 of 313 after the integrator's master merge, and that +1 is the
    // ledger's first live catch — see `NewsDao.getTopLevelTeamMembership`
    // below.
    //
    // **133 of 316 after Phase 159 Lane 3**, which took the ledger's own
    // running order: `SubmissionDao`, then `QuestionDao`/`AnswerDao`,
    // `HealthExaminationDao` and `ChatDao`, then outward through the
    // statements carrying `COLLATE NOCASE`, `IS :param`, `SUBSTR` and `LIKE`.
    //
    // **183 left**, and the four sentences this paragraph used to contain
    // were each wrong in the ledger's own worst direction — overstating what
    // had been looked at. They are corrected rather than deleted, because the
    // correction is the useful record:
    //
    //  * "`SubmissionDao` first (all 32 read)" — the file has **33** `@Query`
    //    statements and **16** are recorded here. The Kotlin side was read end
    //    to end; what is recorded is the subset this lane also traced to a
    //    port counterpart itself. Seventeen remain, and they are the next
    //    round's first target because the reading is half done:
    //    `getByIds`, `getByUserIdAndTeamId`, `getPendingSurveys`,
    //    `countPendingSurveys`, `observePendingSurveys`,
    //    `countPendingOfflineSubmissions`, `countPendingExamResults`,
    //    `getByParentUserAndStatus`, `getByParentIdsAndTeamId`,
    //    `getLatestByParentIdAndStatus`, `getLatestPendingByUser`,
    //    `getFirstByParentIdContaining`, `getUnuploadedNonSurveyByParentIds`,
    //    `updateStatus`, `updateStatusAndLastUpdate`, `deleteByParentAndUser`,
    //    `deleteByIds`.
    //  * "The 191 left" — 316 − 133 is **183**.
    //  * "`COLLATE NOCASE`, all three instances of it in the corpus" — there
    //    are **15**. Coverage of that class does happen to be complete, the
    //    nine `NewsDao` ones having arrived with Phase 158, but the claim as
    //    written was false and `DictionaryDao.count` sits under that heading
    //    carrying no `COLLATE NOCASE` at all.
    //  * "the remaining … `SUBSTR` statements" — `UserDao.getGuestUsersByNames`
    //    is still uncompared, and it is the one with a port counterpart.
    //
    // Found by this lane's second `parity-auditor` pass, aimed at its own
    // finished, green work. Next round's order: `TeamDao` (~22 uncompared,
    // and its `IFNULL(status, '') != 'archived' ORDER BY createdDate DESC`
    // family is the risky part), the rest of `SubmissionDao` above, `NewsDao`,
    // `CourseDao`/`CourseStepDao`, then `NotificationDao`.
    final uncovered = corpus.length - _compared.length;
    expect(uncovered, 316 - 137);
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
const _corpusSize = 316;

/// Entries in [_compared], stated separately so the map and the claim about it
/// cannot drift apart.
const _comparedCount = 137;

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
  // **Four queries Lane 1 read against the port and could not record**, this
  // file being Lane 3's that round. Each is reasoned about by name in the
  // succession work's doc comments, so the reading happened; recording it here
  // is what turns the gate red if any of them changes upstream. Landed by the
  // integrator, per *anything a lane reports because of a file boundary is the
  // integrator's to land in the same round*.
  'TeamLogDao.getTeamVisitsForUsers': '84569532cf8a',
  'UserDao.getUsersByAnyIds': '85062f40b97d',
  'TeamDao.getByTeamIdUserIdAndDocType': 'd0cbe64d50e2',
  'TeamDao.getByTeamIdAndDocType': '144d788d1f94',
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
  // **The ledger's first catch, and it arrived the day it was written.**
  // Merging master brought 26 commits; the corpus went 312 → 313 and this is
  // the one that moved. Triaged: `NotificationsRepositoryImpl:392` now calls
  // `countTopLevelByTeams`, which fetches the membership columns for every
  // watermarked team in **one** query and tallies per team in Kotlin, where it
  // used to loop `countTopLevelByTeam` — which as a result now has **no caller
  // in `app/src/main` at all**.
  //
  // **Not a Follow.** The batched path is the same population: its SQL
  // `viewIn LIKE '%"_id":"%'` is a deliberate superset narrowed in memory by
  // `contains("\"_id\":\"\$teamId\"")`, and the viewable arm is the same
  // comparison with `IN` in place of `=`. The port's per-team loop yields the
  // same counts, so this is batching, not behaviour.
  //
  // Recorded rather than ported for two reasons. A future round seeing
  // `countTopLevelByTeam` deleted upstream must not follow: the port still
  // uses it and it is still correct. And the in-memory `contains` is
  // `ignoreCase = true` — Unicode-aware where SQLite's `LIKE` is ASCII-only,
  // the exact divergence Lane 1 fixed one method away. Unreachable for a
  // CouchDB `_id`, which is lower-case hex, but it is Kotlin that carries it
  // now, not the port.
  'NewsDao.getTopLevelTeamMembership': 'd81260f5b43e',
  // Not a rename, although the ledger first read it as one. `getByNewsId` was
  // `SELECT * FROM news WHERE newsId = :chatId` returning a **list**, and it is
  // gone — its one caller is now `isSharedWith` (above). `getByUnderscoreId` is
  // a different statement on a different column (`_id`, `LIMIT 1`), compared
  // here fresh. The port's counterpart is `NewsDao.getById`; nothing in `lib/`
  // queried the `newsId` column the deleted method used.
  'NewsDao.getByUnderscoreId': 'a57b090ac069',
  // **The ledger's second catch: 92 master commits, five queries to read.**
  // Corpus 313 → 316, one rename, and *no compared statement's SQL changed* —
  // that assertion passed, which is the whole claim the ledger exists to make.
  //
  // `isSharedWith` replaced `getByNewsId`, which this ledger had compared and
  // which is now gone. Kotlin moved `VoicesRepositoryImpl.isAlreadyShared` from
  // `SELECT *` plus an in-memory `contains(…, ignoreCase = true)` to an
  // `EXISTS` with `viewIn LIKE :pattern ESCAPE '\'`, escaping `\`, `%` and `_`
  // in the id first. **Not a Follow**: same population for a CouchDB `_id`
  // (lower-case hex, so the case-folding difference between Kotlin's
  // Unicode-aware `contains` and SQLite's ASCII `LIKE` is unreachable), and the
  // port answers the question a third way — `chat_history_screen.dart:434`
  // tests `sharedIds.contains(target.id)` against a set it already holds.
  //
  // The other three additions are batching and projection with no port
  // counterpart needed: `MeetupDao.getByTeamIdsInternal` (an `IN` over team
  // ids), `MyLibraryDao.getLibraryTitles` (`SELECT id, title`, a projection),
  // `NewsDao.getByUnderscoreIds` (the plural of an existing lookup).
  //
  // `SubmissionDao.getPendingByUserAndParent` is worth one line beyond "no
  // Follow": it is `status = 'pending'` with **no `type` filter**, which
  // corroborates the open report that the port's `getLatestPendingByUserAndParent`
  // adds a `type = 'survey'` conjunct Kotlin does not have.
  'NewsDao.isSharedWith': 'c7c2ce462c5c',
  'NewsDao.getByUnderscoreIds': 'e3d8b8b73970',
  'MeetupDao.getByTeamIdsInternal': 'f112d1ae9997',
  'MyLibraryDao.getLibraryTitles': 'e931d4909697',
  'SubmissionDao.getPendingByUserAndParent': 'ae2bb3697f06',
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

  // ---------------------------------------------------------------------
  // Phase 159 Lane 3. `SubmissionDao` read end to end (all 32 statements),
  // then `QuestionDao`/`AnswerDao`, `HealthExaminationDao` and `ChatDao`, then
  // the remaining `COLLATE NOCASE`, `IS :param` and `SUBSTR` statements
  // wherever they live. Verdicts are in the lane's report; the ones worth a
  // line here are marked at their entry.
  // ---------------------------------------------------------------------

  // **The round's fix.** `getLatestPendingByUserAndParent` has no `type`
  // filter and the port's `latestPendingByUserAndParent` adds
  // `type = 'survey'` — the open report Phase 158 recorded. Settled: **keep
  // the conjunct.** Its one Kotlin caller (`getOrCreateSubmission`) is reached
  // only from survey flows, so the conjunct is redundant rather than
  // restrictive; and `_liveParentDocument` documents that the port's exam and
  // survey id spaces are *not* disjoint (`ExamMapper.mapStepExams` and
  // `SurveyMapper.fromCourseDoc` synthesize the same
  // `'$courseId-$stepId-$examKey'` for a step document with no `_id`), so
  // removing it would let an exam row be handed to a survey caller. Deleting
  // it on sight would have been the wrong move.
  //
  // What the same reading *did* find is that the port never called this
  // lookup from the path that needs it. Kotlin resumes a pending sheet in
  // `startExamSession`'s first statement under `recreate = isTeam`
  // (`ExamTakingFragment.kt:154`); the port's surveys list pushed
  // `/surveys/<id>` with no `?submission=` and inserted a second row, leaving
  // the leader's `pending` sheet — and the dashboard prompt that reads it —
  // for ever. See `test/repository/pending_survey_sheet_resume_test.dart`.
  'SubmissionDao.getLatestPendingByUserAndParent': 'c1e117c053ac',
  // Ported this round alongside it: Kotlin runs it from the single place a
  // survey sheet becomes `complete`, the port from the two paths its split
  // creates.
  'SubmissionDao.deletePendingSurveyOrphans': '59024aa403e1',
  'SubmissionDao.getByIdOrRemoteId': '42d5719de5b7',
  'SubmissionDao.getByUserIdWithoutTeam': 'fb6858798471',
  // The port's `watchForUser` adds `ORDER BY lastUpdateTime DESC` where this
  // has no `ORDER BY` at all. A superset of Kotlin's guarantees, and the
  // submissions screen sorts on that column anyway.
  'SubmissionDao.observeByUserId': 'd1791b52a151',
  // The port's `pendingSurveySubmissions` widens `teamId IS NULL` to
  // `IS NULL OR = ''` and adds `ORDER BY startTime`. The empty string is
  // unreachable — every writer nulls a blank `teamId` and the sync-in reads
  // it with `getStringOrNull` — so the widening selects the same rows.
  'SubmissionDao.getUniquePendingSurveyCandidates': '01af19676abf',
  'SubmissionDao.countByUserParentAndType': '0b5ce3936e12',
  // The three claims in the port's own comment on this one were re-read
  // against the Kotlin rather than trusted, and all three hold: no `type`
  // predicate, an unescaped `LIKE` pattern, and a NULL `status` excluded by
  // SQL's three-valued `!=`.
  'SubmissionDao.countCompletedByUserAndExamId': '7e2ec8323366',
  'SubmissionDao.getByTeamId': '97a67ac57ebb',
  // No port counterpart and none needed: `createBulkSurveySubmissions` uses
  // this to skip members who already hold a sheet, and the port's loop over
  // `getOrCreateSurveySubmission` is find-or-create per member. The behaviour
  // moved across the SQL/Dart boundary rather than being lost at it.
  'SubmissionDao.getPendingByUsersAndParent': 'd89ab798aa1f',
  'SubmissionDao.markComplete': '3bdd9b57ed69',
  'SubmissionDao.getPendingExamResults': 'e61e5bb33f42',
  'SubmissionDao.getPendingSubmissions': '56f1bd0cf04c',
  'SubmissionDao.markUploaded': '36a146efe07a',

  // The port orders `exam_questions` by its own `position` column where these
  // have no `ORDER BY`; Kotlin leans on insertion order from the exam
  // document. Stronger, not different.
  'QuestionDao.getByExamId': '6f1dd22d3049',
  'QuestionDao.getByExamIds': '2f8b9ab282a6',
  // `hasSubmission`'s gate. The port branches on the submission `type` across
  // two tables because it splits Kotlin's single `exam_questions` into
  // `exam_questions` + `survey_questions`; one Kotlin count is two here.
  'QuestionDao.countByExamId': 'd6d76c91a20f',
  'AnswerDao.getBySubmissionId': 'dabf59be27d1',
  'AnswerDao.getBySubmissionIdsInternal': '7e32ad862fc3',
  // Kotlin looks an answer up by `(submissionId, questionId)` and mints a
  // random UUID when there is none; the port derives the row id as
  // `'$submissionId:$questionId'` and filters the submission's own answer set
  // in Dart. Same one-row-per-question identity, reached by a deterministic
  // key instead of a lookup.
  'AnswerDao.getBySubmissionAndQuestion': '44e06507bc0d',
  'AnswerDao.deleteBySubmissionIdsInternal': '67501479715a',

  // All eight read against `HealthExaminationDao` in `app_database.dart` and
  // `health_repository.dart`. The port's `id` column carries the CouchDB
  // `_id` for a synced row (`_docToCompanion` writes both), so `_id = :id`
  // and `id.equals(id)` select the same rows, and `id` is the primary key, so
  // the missing `LIMIT 1` on `getById` cannot matter.
  'HealthExaminationDao.getByIdOrUserId': 'b70ae2ad0950',
  'HealthExaminationDao.getById': 'e16d767cabd5',
  'HealthExaminationDao.getUpdated': '333cb69e5fb1',
  'HealthExaminationDao.getUpdatedForUser': '165dfff996d0',
  // The port collapses Kotlin's two overloads into one nullable-`rev` method;
  // the comment at it explains why that is parity rather than an improvement.
  'HealthExaminationDao.markUploaded': '30745a1231f8',
  'HealthExaminationDao.markUploaded#2': 'd693d3aa9945',
  'HealthExaminationDao.updateUserId': '23f1c650d3d1',
  'HealthExaminationDao.getByProfileId': '0db4c46e69fb',

  // `getByUser` has no `ORDER BY` and `ChatRepositoryImpl.sortChats` sorts by
  // `max(createdDate, updatedDate)` descending afterwards. The port's DAO adds
  // `ORDER BY id DESC` — a CouchDB uuid, so not a recency order — and
  // `chat_repository_impl.dart:246` then applies the same `sortChatsByRecency`.
  // Behaviour moved, not lost.
  'ChatDao.getByUser': 'db2bb05c99ee',
  'ChatDao.getByDocId': 'af0fad4b07fb',
  'ChatDao.findByDocId': 'b683ae64ac8f',

  // **The three `COLLATE NOCASE` statements outside `NewsDao`** — the corpus
  // holds 15, the other twelve being `NewsDao`'s, already compared. (The
  // `DictionaryDao.count` entry below carries no `COLLATE NOCASE`; it is here
  // because the DAO was read whole.) The port
  // answers each with a stored normalized column plus a Dart-side
  // `toLowerCase()`, which is *stronger* than `NOCASE` — that collation folds
  // ASCII only, so Kotlin treats `ÉCOLE` and `école` as different words and
  // the port does not. Recorded rather than "fixed" toward Kotlin: the port's
  // reading is the useful one and matching the ASCII limitation would be a
  // regression.
  //
  // `findByWord` additionally trims, where Kotlin does not, so a query with a
  // stray space finds its entry here and returns nothing on Android.
  'DictionaryDao.findByWord': '14713292992b',
  'DictionaryDao.count': '2242af130070',
  'MyLibraryDao.countByTitle': '323254318596',
  // The one of the three with a real divergence, and it is narrow. Kotlin's
  // `(:userId IS NULL OR :userId = '' OR userId = :userId)` makes the guard
  // **global** for a null or blank user — any note with that title blocks —
  // while the port's `titleExists` always scopes to the user. Reached from
  // `AddResourceViewModel.checkTitleExists(title, userId)`, whose `userId` is
  // `String?`, so a session that has not resolved gives Kotlin a cross-user
  // check and the port a per-user one. The port also adds an `excludingId`
  // Kotlin has no counterpart for, so renaming a note to its own title is
  // refused on Android and allowed here. Both are in the lane's report; the
  // fix is in `app_database.dart` and `personals_repository.dart`, neither of
  // which this lane owns.
  'PersonalDao.countByTitle': 'ebecc1111d01',

  // `SUBSTR(_id, 1, 6) = 'guest_'` done right: the port tests the prefix in
  // Dart precisely because `LIKE 'guest_%'` would read the underscore as
  // LIKE's single-character wildcard. The code that does so
  // (`app_database.dart:1603`) belongs to the **plural**
  // `getGuestUsersByNames`, which is the method with a port counterpart and is
  // itself **not yet compared** — the singular one is recorded here because
  // its statement was read, and the next round should take the plural.
  'UserDao.getGuestUserByName': 'd413ad67e2d1',

  // The port keys `removed_log` on a percent-encoded `type:user:doc` composite
  // and deletes by that key, so Kotlin's three `IS :param` predicates collapse
  // into one primary-key match, and the port's `record`/`clear`/`removedDocIds`
  // all take non-nullable strings. **The reason first given for that being
  // safe — "every Kotlin caller passes a resolved `userId`" — is wrong**:
  // `ActivitiesRepository.markResourceAdded(userId: String?)` is nullable and
  // so is `LocalResourceRequest.userId` (`ResourcesRepository.kt:34`), so
  // `deleteByTypeUserAndDoc("resources", null, id)` is reachable from
  // `AddResourceActivity`. The verdict stands on the port side, where no
  // caller can supply a null; what does not stand is the claim about Kotlin.
  // The bulk `deleteByTypeUserAndDocs`
  // has no single counterpart; the port's batch paths loop `clear`.
  'RemovedLogDao.deleteByTypeUserAndDoc': '5d6e6e8b7d9e',
  'RemovedLogDao.deleteByTypeUserAndDocs': 'd61e8ed27f41',
  'RemovedLogDao.getRemovedDocIds': '910de1e79b57',

  // No port counterpart, and none is owed: `TeamsRepositoryImpl.getTasksFlow`
  // is consumed by `DashboardViewModel.dashboardDataFlow` as
  // `teamsRepository.getTasksFlow(userId).map {}` — the **rows are discarded**
  // and the flow is a change signal merged into a refresh. Any port stream
  // over `team_tasks` serves the same purpose. Traced to the end because a
  // query with no reader is exactly where a missing screen would hide.
  'TeamTaskDao.getOpenTasksForUser': '5cf38a9d940a',

  // **`RetryDao`, read whole against the port's `OutboxDao` and
  // `outbox_repository.dart`.** The port replaced `RetryQueue`'s worker with a
  // drain on app resume, and Phase 148 then gave the table a policy — *an
  // `outbox` item owns exactly one row, for ever; a terminal row is a memo* —
  // so several of these diverge **deliberately** and are documented at the
  // code rather than being drift.
  //
  // `findExisting`'s `status != 'completed' AND status != 'abandoned'` and the
  // port's `findOpen`'s `status IN ('pending','in_progress')` select the same
  // rows: the four statuses are exhaustive and SQL excludes a NULL status
  // under both spellings.
  'RetryDao.findExisting': 'e4f07ac70358',
  'RetryDao.findById': '9cbc5a8eaaff',
  // `attemptCount < maxAttempts` is **not** in the port's `due(now)`. It moved
  // to the transition: the drainer writes `abandoned` when the attempts run
  // out, so an exhausted row is already outside `status = 'pending'`. Moved
  // across the boundary, not lost — and the memo the abandoned row leaves is
  // the point of Phase 148's policy.
  'RetryDao.getPending': '1a76b4339062',
  // Kotlin counts `pending OR in_progress`; the port's `watchPendingCount` is
  // `pending` only, so a claimed-but-unfinished item is not in the badge.
  // Cosmetic, and recorded rather than closed.
  'RetryDao.getActiveCount': '5061973bf199',
  // No age cutoff in the port, and `cleanup()` has no caller at all — which is
  // the Phase 148 policy working rather than a gap: the terminal row is how a
  // permanent refusal is read back, and `clearAbandonedFor` removes it at the
  // one event that makes it untrue.
  'RetryDao.deleteOldCompleted': '5998b03e5a7f',

  // Kotlin keeps surveys and tests in one `exams` table and filters on `type`;
  // the port splits them into [Exams] and [Surveys] at mapper time, so the
  // `type` predicate *becomes the table choice*. One Kotlin lookup is two port
  // lookups, and `_liveParentDocument` documents the one place that costs
  // something.
  'ExamDao.getById': 'b37f66ab510e',
  'ExamDao.getByType': 'e7773f2bdab4',
  // **No port counterpart, and none is owed — the Kotlin path is dead.** This
  // is `SurveysRepositoryImpl.getSurvey`'s fallback, `examDao.getById(id) ?:
  // getByTypeAndName("surveys", id)`, i.e. look the survey up by *name* when
  // the id misses. Its one caller chain is
  // `DashboardActivity:543` -> `DashboardViewModel.handleSurveyNavigation`,
  // reached only from an `auto_navigate` intent carrying
  // `NotificationUtils.TYPE_SURVEY` — and **`createSurveyNotification`
  // (`NotificationUtils.kt:109`) has no caller in `app/src/main`**, so no such
  // notification is ever posted. Recorded because the shape without this trace
  // reads like a missing entry point (Phase 158's fifth question), and a lane
  // could spend a round building one Kotlin does not ship. Phase 149's rule:
  // trace the caller chain to its end before porting a line.
  'ExamDao.getByTypeAndName': '5d56c90172f9',

  // `userId IS :userId` ported as drift's `equalsNullable`, which is the
  // spelling of `IS`. Re-read rather than inherited from the comment at it,
  // because this is the write in the `IS`-vs-`=` class with the largest blast
  // radius: without the scope it clears a peer's server-granted pass.
  'CourseProgressDao.updatePassedByCourseAndStep': '21bb072171b2',
};
