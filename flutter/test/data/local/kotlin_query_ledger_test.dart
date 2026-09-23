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
    expect(uncovered, 316 - 204);
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
const _comparedCount = 204;

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

  // ---------------------------------------------------------------------
  // Phase 160 Lane 3, taking the running order this file states above.
  //
  // **`ApkLogDao`'s four statements are deliberately left uncompared.** They
  // are in the corpus and they are the obvious cheap win, and taking them
  // would have been dishonest: Lane 1 is porting that table this round, so
  // there is no port counterpart on this branch to read them against. An
  // entry recording a comparison against a method that does not exist yet is
  // the inflation this file's header refuses.
  // ---------------------------------------------------------------------

  // --- NewsDao, the last four ------------------------------------------
  // `getById` is the primary key, so the port's `getSingleOrNull` without a
  // `LIMIT 1` cannot return two rows where Kotlin returns one.
  'NewsDao.getById': '09bfeba3cec8',
  'NewsDao.getAll': 'f2d049eef89b',
  // **No port counterpart on the `id` column, and the reason is worth a line
  // because the shape reads like a gap.** Kotlin's one caller is
  // `VoicesRepositoryImpl.markNewsUploaded` (`:52`), a fetch-then-mutate over
  // the whole row set; the port's `voices_repository.markUploaded` (`:1044`)
  // writes each row by id instead, so it needs no bulk read. The port's
  // `NewsDao.getByDocIds` is the same statement on `docId`, not `id`, and is
  // a different question.
  'NewsDao.getByIds': 'd997732fcabd',
  'NewsDao.deleteByIds': '125a745485e4',

  // --- CourseDao / CourseStepDao ---------------------------------------
  // **The port answers three Kotlin columns with one, and it is the same rows
  // — but only because of what the mapper writes.** Kotlin's
  // `getByCourseId` is `courseId = :x OR id = :x LIMIT 1` and
  // `getByCourseIdsInternal` is `courseId IN (…) OR id IN (…) OR _id IN (…)`,
  // a three-way alternation over columns the port's `Courses` table also has
  // (`id`, `couchId`/`_id`, `courseId` — `tables.dart:5-8`). The port's
  // `CourseDao.getById`/`getByIds` test `id` alone. That is safe here and not
  // in general: `CourseMapper.fromDoc` (`course_mapper.dart:73-75`) writes the
  // document's `_id` into **all three** columns, and it is the only writer of
  // the table in `lib/`, so the three predicates select identically. A second
  // writer that set `courseId` to anything else would silently narrow every
  // one of these lookups, which is why the alternation is recorded here rather
  // than dismissed.
  'CourseDao.getByCourseId': '7b223bebeeea',
  'CourseDao.observeByCourseId': '7b223bebeeea',
  'CourseDao.getByCourseIdsInternal': '4ac5ca683dc9',
  // Same statement, and `observeAll` has **no caller in `app/src/main`** while
  // `getAll` has three (`CoursesRepositoryImpl.kt:107,275,324`). The port's
  // nearest is `watchCourses()` with every filter omitted, which adds
  // `ORDER BY courseTitleNormal`; Kotlin sorts afterwards in `mapCourses`'
  // consumers. Stronger, not different.
  'CourseDao.getAll': '321a08fd26e9',
  'CourseDao.observeAll': '321a08fd26e9',
  // The port folds `CourseStepDao` into its own `CourseDao`: `getSteps` and
  // `watchSteps` are `getByCourseId`, and both add
  // `ORDER BY stepIndex` where Kotlin has no `ORDER BY` and leans on insertion
  // order from the course document. `stepCountsByCourseIds` is the plural,
  // chunked and aggregated in SQL rather than returning rows.
  'CourseStepDao.getByCourseId': 'a8cb34df37c3',
  'CourseStepDao.getByCourseIds': '96498e6ba6f3',
  // **No port counterpart and none is owed.** Its one caller is
  // `CoursesRepositoryImpl.getCourseStepData(stepId, userId)` (`:549-551`),
  // which assembles a step's resources, exams and survey from a step id alone.
  // The port cannot need it: its step ids are positional
  // (`CourseMapper.stepIdFor` is `'$courseId:$index'`,
  // `course_mapper.dart:198`), so a step is only ever reached through the
  // course that owns it and the screen already holds `getSteps(courseId)`.
  'CourseStepDao.getById': 'dda73647ccb8',

  // --- NotificationDao, the eleven remaining ---------------------------
  // **The badge counts a population the list refuses to show, in both apps.**
  // `getNotifications` (compared above) carries
  // `message != 'INVALID' AND message != ''`; this does not. So a row whose
  // message failed to parse is counted by the bell and then absent from the
  // list the bell opens. The port reproduces it exactly — `watchUnreadCount`
  // is `_userMatch & isRead = false` with no message predicate, against
  // `watchForUser`'s two `.not()` clauses. Recorded as parity rather than
  // repaired: the divergence is Kotlin's own and closing it here would make
  // the two apps disagree about an unread count.
  'NotificationDao.getUnreadCount': '0a7c2a5c7e72',
  // The singular; the port's `markOneAsRead` is the same statement written
  // out in raw SQL, `CASE WHEN is_from_server` included.
  'NotificationDao.markAsRead': '63986920dd24',
  // **The plural, and the one entry here that needs its reasoning kept.**
  // Kotlin sets `isRead`, `createdAt` and the conditional `needsSync` in
  // **one** statement over `id IN (:ids)`. The port splits it into two writes
  // (`app_database.dart`: the companion update, then a `needsSync` update
  // filtered on `isFromServer`). That is the shape Phase 98 recorded as a
  // defect — a read-then-flag whose second statement can no longer identify
  // the rows the first changed — and here it is **safe, for the reason that
  // rule gives**: the ids are captured first (`values`), so the second write
  // re-selects by `id IN (values) AND is_from_server`, never by
  // `is_read = 1`. Written down so the next reader neither "fixes" it into
  // Phase 98's bug nor reports it as one.
  'NotificationDao.markAsRead#2': '18fc7688b0e1',
  // `WHERE userId = :userId AND isRead = 0` — **no `SYSTEM` arm**, where
  // `getUnreadCount` above has one for an admin. So "mark all read" leaves an
  // admin's SYSTEM notifications unread in both apps. The port's
  // `markAllAsRead` is the same single statement and the same population. The
  // *display* then diverges in the port's favour and not by design: Kotlin's
  // `NotificationsViewModel.markAllAsRead` (`:184`) sets `_unreadCount.value
  // = 0` optimistically, so the badge shows zero and jumps back on the next
  // recount, while the port's badge is a drift stream that re-emits the true
  // residual immediately.
  'NotificationDao.markAllUnreadAsRead': '1e06a5d7c508',
  // **No port counterpart, and the reactive stream is why.** Kotlin reads the
  // unread ids *before* the update purely so
  // `NotificationsViewModel.markAllAsRead` can patch its in-memory
  // `MutableStateFlow` list without re-querying (`:177-189`). The port's list
  // is `watchForUser`, a drift stream over the same table, so the write
  // re-emits it. Behaviour moved across the boundary rather than lost — and
  // note it carries the same missing `SYSTEM` arm as the update it precedes.
  'NotificationDao.getUnreadIds': '8c47f026e237',
  // An existence probe (`SELECT id … WHERE id IN (:ids)`) used twice, to
  // narrow a caller's id set to rows that exist before marking
  // (`NotificationsRepositoryImpl.kt:126`) or deleting (`:500`). The port asks
  // the same question through `NotificationDao.getByIds(...).keys`
  // (`notifications_repository.dart:75,131`) — a widened projection over
  // identical rows.
  'NotificationDao.getIdsByIds': '9ab385a3e868',
  // The port chunks this at the SQLite variable limit and Kotlin does not.
  // Not a port gap: `bulkInsertFromSync` (`:511`) hands it every id in a sync
  // page, so the **Kotlin** is the side that can exceed 999 bound variables.
  'NotificationDao.getByIds': '6d93610f88e8',
  'NotificationDao.getById': '5d990dda3c25',
  'NotificationDao.deleteById': '4214b52de826',
  'NotificationDao.deleteByIds': 'ac097307bbc7',
  // Kotlin splits the sync acknowledgement by whether the server returned a
  // revision: this one clears `needsSync` and leaves `rev` untouched, while
  // the non-null half goes through a hand-built `CASE id WHEN … END` raw
  // query. The port's `markSynced(id, rev)` is one method doing both, with
  // `Value.absent()` for a null `rev` — which is precisely "leave the column
  // alone", the same rule Phase 56 established for the security-data write.
  'NotificationDao.markSyncedNullRevs': '148e55d04d8d',

  // --- The six fully-uncompared small DAOs ------------------------------
  // Three different spellings of "this row has not been uploaded yet", and
  // each one is the class where a port silently disagrees, so each was
  // checked against its column's nullability rather than its wording:
  //
  //  * `course_activity`: `_rev IS NULL AND type != 'sync'`. `CourseActivity._rev`
  //    is `String?` and defaults null, so `IS NULL` is the right test, and the
  //    `type` clause keeps sync rows out of this pipeline. The port is the
  //    same two clauses (`rev.isNull() & type.equals(ActivityTypes.sync).not()`).
  //  * `search_activity`: `_rev = ''` — strict equality, which would exclude a
  //    NULL row. It cannot arise: `SearchActivity._rev` is a non-null
  //    `String = ""` (`SearchActivity.kt:26`), and the port's column is
  //    `text().named('_rev').withDefault(const Constant(''))`
  //    (`tables.dart`), non-nullable with the same default. Both sides are
  //    consistent with themselves; neither is one nullable column away from
  //    never uploading anything.
  //  * `news_log`: `_id IS NULL OR _id = ''`, and see below.
  'CourseActivityDao.getPendingUploads': '182727d23df2',
  'CourseActivityDao.markUploaded': '67971ee47ca8',
  'SearchActivityDao.getPendingUploads': '2061a81b76b0',
  'SearchActivityDao.markUploaded': '1a25fb2886db',
  // **No port counterpart, and none is owed, because the Kotlin table has no
  // writer.** `newsLogDao.insert` has **zero callers in `app/src/main`** and
  // nothing anywhere constructs a `NewsLog`; the only references are the read
  // sweep (`VoicesRepositoryImpl:509-514`) and a complete upload pipeline
  // wired around it (`UploadConfigs.kt:62-68`, serializer and all). So
  // `getPendingUploads` returns empty for ever and the pipeline uploads
  // nothing. Recorded rather than left blank because the shape is exactly
  // Phase 158's fifth question — *does Kotlin offer something the port never
  // built?* — and the honest answer here is no: a lane that saw the missing
  // table and ported it would be building a feature Kotlin does not ship.
  // Phase 149's rule, and the fourth instance of it in this file.
  'NewsLogDao.getPendingUploads': 'd055a59fea56',
  'NewsLogDao.markUploaded': 'a70474fc2653',
  // The port narrows `getByIds` by dropping empty ids before binding; an
  // empty id matches no primary key either way, so the rows are identical.
  'SubmitPhotosDao.getUnuploaded': '5d7cf3e142d9',
  'SubmitPhotosDao.getByIds': '75d570557986',
  'SubmitPhotosDao.markUploaded': '4d78fa70e1e8',
  // `updateCount` is an `UPDATE … WHERE parentId = … AND type = …` whose one
  // caller reads its row count and inserts when it is zero
  // (`NotificationsRepositoryImpl.kt:368`) — an upsert written as two steps.
  // The port's `upsert` is that upsert. Same end state; the port cannot
  // reach the window between the two statements, which is a narrowing.
  'TeamNotificationDao.updateCount': '65344064e897',
  'TeamNotificationDao.getByTypeAndParentIds': '08049ab5c51a',
  'UserChallengeActionsDao.countByUserAndType': '6ecaa1081fc0',

  // --- Eight DAOs finished off -----------------------------------------
  // **No caller in `app/src/main`.** The community walk replaces rows through
  // `upsertAll`, and nothing wipes the table.
  'CommunityDao.deleteAll': '2535384f99ea',
  // No by-question-id lookup in the port, and the behaviour it serves is
  // there. Kotlin's one caller is `ProgressRepositoryImpl.submissionMap`
  // (`:189`), which turns a submission's answers into the per-step mistake
  // counts the My Progress **list row** renders. The port computes the same
  // `stepMistakes` map in `courseProgressStreamProvider`
  // (`courses_providers.dart:714-727,745`), reaching questions through
  // `questionsForExams(examIds)` — the exam, not the question — and joining in
  // Dart. `progress_repository.dart:69-72` documents the split and was
  // checked rather than taken on trust.
  'QuestionDao.getByIds': '6765d757a427',
  'ResourceActivityDao.markUploaded': 'd72379b98ce9',
  'CourseProgressDao.getByIds': '24038d7bf283',
  'CourseProgressDao.markUploaded': '0306215a28a9',
  'MyLifeDao.getByIds': '85ca3b21bcc6',
  // **Three columns on one side, one on the other, and the port's table has no
  // second column to offer.** Kotlin is
  // `UPDATE my_life SET isVisible = :isVisible WHERE _id = :id OR imageId = :id
  // OR title = :id`; the port's `setVisibility` matches the primary key alone,
  // and `MyLifeEntries` has **no `imageId` column at all** (`tables.dart`), so
  // the other two arms are not narrowed here, they are unrepresentable. Same
  // rows for the caller either app actually uses: the port's chain is
  // `life_screen.dart:105` → `life_provider.dart:27`, which passes `row.id`,
  // and that column is `text().named('_id')` — Kotlin's primary key under its
  // own name. The alternation is recorded rather than shrugged off because a
  // future seeded-entry path that identified a row by title would silently
  // stop toggling.
  'MyLifeDao.updateVisibility': '2271b630aeaa',
  'AchievementDao.getById': '6e05be068c0a',
  // `_rev = COALESCE(:rev, _rev)` — a null revision leaves the column as it
  // was. The port's `markUploaded` writes `Value.absent()` for a null `rev`,
  // which is the drift spelling of exactly that, and the same rule Phase 56
  // established after a null-returning fetch wiped a stored credential.
  'AchievementDao.markUploaded': '44fb93b0a8cf',
  'TeamLogDao.getPendingUploads': '7dc31c204117',
  'TeamLogDao.markUploaded': 'cb1253e7f20d',
  // **The port has a counterpart for a Kotlin method Kotlin never calls**, and
  // that is the right way round. Phase 156 established `getByRemoteIds` has no
  // caller in `app/src/main` — which is why Kotlin double-counts a pulled team
  // visit against a locally authored one. The port's `getByCouchIds` is the
  // same statement and it *is* wired, into the sync-in merge that avoids the
  // double count. Recorded so a future round seeing this deleted upstream does
  // not follow.
  'TeamLogDao.getByRemoteIds': 'b9ff3bd839fa',
  'RatingDao.findById': '8fc2395f7379',
  'RatingDao.markUploaded': '9969623b0fc2',
  // **The one real gap this batch found, and it is a whole table.**
  // `rating_prompt_log` has no counterpart in the port — no table, no query,
  // nothing. In Kotlin it is what makes the resource rating prompt *once per
  // (user, resource)*: `ResourcesExitCoordinator:26-30` asks
  // `shouldShowResourceRatingDialog` when the viewer closes, which refuses if
  // the pair is already logged or already rated, and stamps the log when it
  // shows. The port's only rating affordances are explicit buttons
  // (`resource_detail_screen.dart:219`, `course_detail_screen.dart:169`,
  // `take_course_screen.dart:481`); `resource_viewer_screen.dart` mentions
  // rating nowhere. So the nudge that collects most resource ratings in the
  // Android app does not exist here. Reported, not fixed — every file it
  // touches belongs to another lane.
  'RatingDao.isRatingPrompted': '21695a43707a',

  // --- FeedbackDao and PersonalDao finished off -------------------------
  'FeedbackDao.getPending': '10f71ca95fc3',
  'FeedbackDao.findById': '8665edb47fca',
  'FeedbackDao.getByIds': '2e032cf2d3ac',
  'FeedbackDao.closeById': 'ca3a7e93cd26',
  // Kotlin sets `isUploaded = 1` alone; the port's `markUploaded(id, rev)`
  // writes the revision with it, which `ConflictRecovery` needs and Kotlin
  // takes from a separate path. A superset, and Phase 157's feedback fix is
  // built on it.
  'FeedbackDao.markUploaded': '6ef7bc2f6fa6',
  'PersonalDao.getPendingUploads': 'bfc74c05a209',
  'PersonalDao.findById': '4981b637eb70',
  // **No caller in `app/src/main`** — the by-`_id` twin of `findById`, laid in
  // and never wired. (The `findByDocId` hits a grep turns up are `ChatDao`'s.)
  'PersonalDao.findByDocId': '5db571045ddf',
  // `_id = :id OR id = :id`, against the port's primary-key `deleteById`. The
  // port's caller chain hands it `row.id` throughout, so the `_id` arm is
  // unreachable from it; recorded because the arm exists and a future caller
  // resolving a note from a server document would need it.
  'PersonalDao.deleteByIdOrDocId': '7978a7e331b5',
  'PersonalDao.updateUploadedStatus': 'f3d9ebf0ac14',
  // **This one corrected a wrong statement in the port**, which is the one
  // thing this lane is allowed to fix outside its file.
  // `personals_repository.dart`'s `update` carried a comment saying its
  // whole-row write matches "Room's `@Update` in `PersonalDao`". There is no
  // `@Update` in `PersonalDao` — one `@Insert` and nine `@Query`s
  // (`PersonalDao.kt:20`) — and the edit path is this method, a targeted
  // `SET title = COALESCE(:title, title), description = COALESCE(:description,
  // description) WHERE _id = :id OR id = :id`.
  //
  // Reading it properly also found a real divergence the wrong citation was
  // hiding: `updateFields` **never touches `isUploaded`**, so on Android
  // editing an already-uploaded note leaves it flagged uploaded and the edit
  // is never sent. The port writes `isUploaded: false`, so it is. Clearing a
  // description works in both, but not for the reason `COALESCE` suggests —
  // `PersonalsFragment.kt:120-122` passes the dialog's text rather than null,
  // and `COALESCE('', …)` writes the empty string. The comment now says all
  // of this.
  'PersonalDao.updateFields': '44c2c177b1bf',

  // --- UserDao finished off ---------------------------------------------
  'UserDao.getAll': '7b6ddc41e5c5',
  'UserDao.count': 'c8df01c9cbdf',
  'UserDao.deleteById': '3ad0aec54bc8',
  'UserDao.deleteByIds': '338a01649c2c',
  // **The plural the previous round flagged as the one to take next**, and it
  // is the method with the port counterpart. `SUBSTR(_id, 1, 6) = 'guest_'`
  // done in Dart at `app_database.dart:1654` precisely because
  // `LIKE 'guest_%'` would read the underscore as LIKE's single-character
  // wildcard. The `name IN (:names)` half is SQL on both sides.
  'UserDao.getGuestUsersByNames': 'b6108be85495',
  // **No port counterpart, and this one is a judgement rather than an
  // equivalence.** Kotlin sweeps rows sharing a name
  // (`IFNULL(name,'') IN (SELECT … GROUP BY … HAVING COUNT(*) > 1)`) and
  // deletes all but the `org.couchdb.user:` one
  // (`UserRepositoryImpl.kt:854-871`). The port prevents the duplicate instead
  // of repairing it: `_cacheUserDoc` (`user_repository.dart:172-175`) resolves
  // the row by `couchId` before writing, and `insertUsersFromSync`
  // (`:229-234,247-250`) adopts a guest row by name rather than adding a
  // second — both documented, both read. So the sweep has nothing to sweep.
  // What the port does **not** have is a repair path for a duplicate that
  // arrived some other way, and `user_repository.dart:160` is explicit that a
  // duplicate here costs a member their health records. Recorded as a
  // difference in strategy with a residual, not as a gap to fill: adding a
  // name-collision delete would be a destructive sweep over a preserved
  // table, which is a decision for a round that has evidence a duplicate can
  // occur.
  'UserDao.getDuplicateUsers': 'abd443b77f6e',
};
