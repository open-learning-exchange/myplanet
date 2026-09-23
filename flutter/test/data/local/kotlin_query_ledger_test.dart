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
    // below. **133 of 316 after Phase 159 Lane 3**, whose own second audit
    // pass found four of its coverage claims wrong in this file's worst
    // direction — overstating what had been looked at. Those corrections are
    // kept at their entries rather than deleted, because the correction is the
    // useful record.
    //
    // **243 of 317 after Phase 160 Lane 3**, taking the order the previous
    // round set down. **29 of the 37 DAOs are now complete, 23 of them
    // finished off this round**; the eight with anything left are listed
    // below. Those two figures were counted against the tree rather than
    // remembered, and the first draft of this sentence said "fourteen" and
    // then enumerated fifteen — which is the same failure as the four
    // corrections above, caught here only because the count was re-run. If
    // you edit this paragraph, re-measure it; do not adjust it.
    //
    // **Phase 160's own second pass corrected this file again, in the same
    // direction, and that is now three rounds running.** Two of the Phase 159
    // justifications at `SubmissionDao.getLatestPendingByUserAndParent` were
    // wrong: one vacuous, one describing a mapper mechanism that does not
    // exist. The *verdict* those arguments supported is right and stands.
    // Read that entry before trusting any reasoning here that you have not
    // opened the citation for — **the failure mode of this file is a
    // confident sentence, not a missing one.**
    //
    // **74 left, and the running order for Phase 161**, by remaining count:
    //
    //  * `MyLibraryDao` — 19 of 36 left, and the biggest. It is also where
    //    the `LIKE`-shaped shelf predicates live, so take it first.
    //  * `ExamDao` — 16 of 22 left. Kotlin keeps surveys and tests in one
    //    table and filters on `type`, which the port answers by splitting into
    //    [Exams] and [Surveys]; every entry here has to say which table
    //    answers it, and the six already compared show the shape.
    //  * `OfflineActivityDao` — 10 of 11 left.
    //  * `TeamTaskDao` — 9 of 11 left.
    //  * `TagDao` — 7 of 8 left.
    //  * `MeetupDao` — 6 of 8 left.
    //  * `RetryDao` — 5 of 10 left. The port's `OutboxDao` diverges here
    //    *deliberately* under Phase 148's policy, so read the five entries
    //    already recorded before judging the rest. Two things established this
    //    round but not yet recorded, since the lane ran out of round: Kotlin's
    //    `markCompleted` keeps a `'completed'` row where the port's
    //    `markCompleted` is `deleteIfInProgress` and deletes it; and
    //    `recoverStuck` resets every `in_progress` row and rewrites
    //    `nextRetryTime`, where the port's resets only rows older than a
    //    staleness window and leaves `nextAttemptAt` alone. The backoff
    //    itself matches: base 30 s, cap 30 min, doubling, on both sides.
    //  * `ApkLogDao` — 2, deliberately skipped this round because Lane 1 was
    //    porting that table and there was nothing on this branch to read its
    //    queries against. It is the cheapest entry in the next round.
    final uncovered = corpus.length - _compared.length;
    expect(uncovered, 317 - 243);
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

/// `@Query` annotations in `app/src/main/.../data/room/dao/`, as of Phase 160.
const _corpusSize = 317;

/// Entries in [_compared], stated separately so the map and the claim about it
/// cannot drift apart.
const _comparedCount = 243;

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
  // **The open list's "the port permits duplicate team names" is refuted, and
  // the first half of it is true.** (Phase 160.) There is indeed no port
  // counterpart — `nameExists` appears nowhere in `lib/`. It is not owed
  // today, because neither Kotlin caller has a port equivalent: both
  // `isTeamNameExists` call sites are `TeamViewModel.kt:164` (create) and
  // `:195` (`updateExistingTeam`, reached from `TeamFragment.kt:139`), i.e.
  // the teams-list create/edit dialog, and **the port creates no teams at
  // all** — its four `TeamsCompanion.insert` sites write transaction, report,
  // resourceLink and request (`teams_repository.dart:358,409,666,801`). The
  // port's one rename path, `team_plan_screen` → `updateTeam`, mirrors
  // `PlanFragment.kt:169` → `updateTeamDetails` (`TeamsRepositoryImpl:861`),
  // which carries **no name check in Kotlin either** — the two chains are
  // disjoint. It becomes owed the moment a create-team or list-edit
  // affordance is added. (And per `getUpdatedTeams` below, that rename does
  // not reach the server anyway.)
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
  // the conjunct.**
  //
  // **Phase 160 correction: the verdict stands and both justifications given
  // for it were wrong.** Found by this round's ground-truth pass and
  // re-verified by hand before being written here, because propagating it
  // once was enough.
  //
  //  * *"Its one Kotlin caller (`getOrCreateSubmission`) is reached only from
  //    survey flows, so the conjunct is redundant rather than restrictive."*
  //    Vacuous: `getOrCreateSubmission` appears exactly twice in `app/src` —
  //    its interface (`SubmissionsRepository.kt:65`) and its impl
  //    (`SubmissionsRepositoryImpl.kt:666`) — and is **reached from nothing**,
  //    which the very next lines of this entry already said. And the rows that
  //    method *writes* being `type = "survey"` says nothing about what the
  //    query *finds*.
  //  * *"the port's exam and survey id spaces are not disjoint, because
  //    `ExamMapper.mapStepExams` and `SurveyMapper.fromCourseDoc` synthesize
  //    the same `'$courseId-$stepId-$examKey'`."* **The mechanism does not
  //    exist.** The two arms are partitioned: `ExamMapper.fromCourseDoc`
  //    passes `examKey: 'exam'` with `accept: (j) => !isSurveyType(j)`
  //    (`exam_mapper.dart:234,243`) and `SurveyMapper`'s first arm passes the
  //    same key with `accept: ExamMapper.isSurveyType`
  //    (`survey_mapper.dart:125-126`) — mutual complements over one object, so
  //    it lands in exactly one table — while its second arm passes
  //    `examKey: 'survey'` (`:132`), which mints a **different** suffix.
  //    Synthesised ids are disjoint across the two tables.
  //
  // The two id spaces *do* overlap, by a route neither comment named: both
  // tables are filled from the same `exams` database, and a standalone
  // document and its copy embedded under `steps[i].exam` are routed
  // independently — an embedded object omitting `type` (the common shape,
  // `exam_mapper.dart:24-27`) goes to [Exams] under its server `_id` while the
  // standalone document carrying `type: 'surveys'` goes to [Surveys]. One
  // `_id`, two tables. The stronger reason is the mixed fleet:
  // `ExamDao.getFirstByStepId` is `WHERE stepId = :stepId LIMIT 1` with no
  // type filter and no `ORDER BY`, and `createExamSubmission` builds
  // `parentId` from whatever it returns, so an Android learner on a step
  // carrying both can author a `type='exam'` sheet under the survey's
  // `parentId`.
  //
  // What the same reading *did* find is that the port never called this
  // lookup from the path that needs it. Kotlin resumes a pending sheet in
  // `startExamSession`'s first statement under `recreate = isTeam`
  // (`ExamTakingFragment.kt:154`) — though **not through this statement or
  // its live sibling**: Phase 160 traced it and the resume that actually
  // fires is `getByParentUserAndStatus` (`:25`) at
  // `ExamTakingFragment.kt:106-109`. `getPendingByUserAndParent`'s two
  // callers are `startExamSession:458`, where it is a guaranteed-null
  // re-query (identical predicate and sort to the lookup that just returned
  // nothing), and `saveExamAnswer:543`. `SubmissionListViewModel` reaches
  // neither — it calls `getSubmissionItems` → `getByParentUserAndStatus`.
  // The port's surveys list pushed
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
  // **The ledger's third live catch, and it landed mid-round on the DAO this
  // lane had just finished.** Master `2adbccd` ("smoother personals
  // repository dao upload retrying", fixes #17465) added one statement, the
  // corpus went 316 → 317, and the gate went red on a PR whose own tree still
  // held 316 — the merge is what CI tests. It is
  // `updateUploadedStatus` **minus `isUploaded = 1`**, and the point of it is
  // the ordering around it:
  //
  // `uploadPersonalDocument` now records the id and rev through *this*
  // statement rather than through `updatePersonalAfterSync`, so a document
  // that POSTed successfully carries its server identity without yet being
  // flagged uploaded; `uploadPersonal` resumes from a stored `_id`/`_rev`
  // instead of re-POSTing; and `updatePersonalAfterSync` — the one that sets
  // `isUploaded = 1` — moved to **after** the attachment PUT succeeds, with
  // the attachment's own returned `rev` carried into it.
  //
  // **A Follow, and the port has the bug this fixes.**
  // `personals_uploader.dart:117-118` calls `markUploaded(...)` — which writes
  // `isUploaded: true` — and *then* `_uploadAttachment`, and the comment at
  // `:27-29` states the intent outright: "an attachment failure does not roll
  // the [document] back". So a personal note whose attachment upload fails is
  // flagged uploaded, the file never reaches CouchDB, and nothing retries: the
  // Phase 156 lost-attachment shape, for personals. Reported rather than
  // fixed — `personals_uploader.dart` is a behaviour port, not a statement
  // this lane may make true.
  'PersonalDao.updateRemoteDocRef': '74b11cdce08e',
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

  // --- TeamDao, the twenty-one the previous round named first -----------
  //
  // The ledger's stated order put `TeamDao` first because of its
  // `IFNULL(status, '') != 'archived'` family, on the reasoning that an
  // `IFNULL` around a nullable column is where a Drift port silently
  // disagrees. **It does not disagree, anywhere**, and the inventory is worth
  // recording so the suspicion is retired rather than re-raised:
  // `IFNULL(s,'') != 'archived'` ≡ `s IS NULL OR s != 'archived'` ≡ Kotlin's
  // in-memory `it.status != "archived"` on a nullable String, and there is
  // **no bare `status != 'archived'` on either side**. Kotlin uses the
  // `IFNULL` form six times (`TeamDao.kt:24,25,26,42,45,51`), the spelled-out
  // form once (`:22`), and an in-memory test four times; the port writes
  // `status.isNull() | status.equals('archived').not()` at every one.
  //
  // What the pass found instead is that the divergences are in **other**
  // conjuncts, and three of them are live. The verdicts below come from a
  // `parity-auditor` pass at `effort: max` over every caller chain; the three
  // marked **verified here** were re-opened and re-read by this lane before
  // being written down, because they are the severe ones.
  //
  // **A team-document edit never leaves the device, and the row then freezes.
  // (verified here)** `getUpdatedTeams` (`WHERE isUpdated = 1`) is the only
  // route a plain team edit takes off an Android handset:
  // `TeamsRepositoryImpl.getTeamsForUpload:88` → `TeamsUploader.kt:37`. The
  // port has no query selecting `isUpdated` on `teams` at all — the only
  // `teams`+`isUpdated` read in `lib/` is `deleteNotIn`'s
  // `isUpdated.equals(false)` — and `TeamsUploader`'s five types
  // (`teams_uploader.dart:37-41`) are membership/resource/courses/reports/
  // finances, none of them the document itself. `team_plan_screen.dart:183`
  // awaits `updateTeam` and pops, enqueueing nothing, while
  // `TeamsRepository.updateTeam` writes `isUpdated: true`. The second half is
  // worse than the first: `TeamMapper.fromDoc:16` short-circuits on
  // `existing.isUpdated` and its own comment says the row outranks the server
  // *"until something uploads it"* — so with nothing to clear the flag, that
  // team row never takes another server-side change for the life of the
  // install, and `deleteNotIn` skips it for ever.
  'TeamDao.getUpdatedTeams': '14028cfd129c',
  // **A whole Community tab is permanently empty. (verified here)** Kotlin
  // reads `getByDocType("link")` (`TeamsRepositoryImpl:355`) for the services
  // list; the port's `watchTeamLinks` (`teams_repository.dart:766`) reads
  // `watchTeamDocumentsByType('service')`, and `"service"` appears **nowhere**
  // in `app/src/main` — no writer on either side ever produces it. The screen
  // is a live tab of `community_screen.dart`, and all five of its tests
  // override the provider, so the statement is never exercised. A one-word
  // change and a DAO-level test.
  'TeamDao.getByDocType': 'fc0b7632061c',
  // **The port moved this sort into SQL, and the default Finances view now
  // shows both the wrong order and backwards running balances. (verified
  // here)** Kotlin has no `ORDER BY` and sorts afterwards, but the ordering
  // is load-bearing: `getTeamTransactionsWithBalance:432` passes
  // `sortAscending = true` **hard-coded**, accumulates the balance
  // oldest→newest, and reverses for display only at the end. The port's
  // `watchTransactions` sorts in SQL by the caller's direction, and
  // `teams_provider.dart:170` then reads
  // `for (final row in params.ascending ? rows : rows)` — a ternary whose two
  // branches are the same expression. So under the screen's default
  // (`team_finances_screen.dart:39`, `_ascending = false`) the rows arrive
  // date DESC, the balance accumulates newest→oldest, and the result is
  // reversed back to ascending. The no-op ternary is the tell that the
  // intent was Kotlin's shape. All four tests of that screen override the
  // provider.
  'TeamDao.observeByTeamIdAndDocType': '144d788d1f94',
  // **The sync has no request/membership dedup.** Kotlin runs both of these
  // from one place (`insertMyTeam:1220-1225`): an arriving `membership`
  // deletes the local `request` row for that (team, user), and an arriving
  // `request` is dropped when a membership already exists. The port's
  // `TeamsRepository.sync` maps and upserts with neither. Where Planet writes
  // a separate membership document, the accepted requester stays under **Join
  // requests** for every leader indefinitely, with Accept/Decline live, and
  // declining enqueues a `_deleted` tombstone for a document the server still
  // holds.
  'TeamDao.countByTeamIdUserIdAndDocType': 'c0589cb0c584',
  'TeamDao.deleteByTeamIdUserIdAndDocType': '5b9d45b3eea8',
  // **`status = 'active'` — strict equality, the one predicate in this DAO
  // that is not the archived test, and deliberately narrower**: a team whose
  // document omits `status` stores `''` in Kotlin (`JsonUtils.getString`
  // defaults to `""`) and is therefore excluded. No port counterpart, and
  // neither has the affordance: its chain is `getAllActiveTeams:188` →
  // `LoginViewModel.kt:43` → `LoginActivity.setupTeamDropdown:445-491`, the
  // login screen's team spinner, which seeds the saved-user list so a team's
  // members can sign in by tapping their name. `login_screen.dart` mentions
  // teams nowhere. Phase 158's fifth question again — nothing in `lib/` is
  // wrong, so no scan over the port finds it.
  'TeamDao.getActiveRootTeams': 'a07ef769c35c',
  // `observeAll` feeds two Kotlin readers and the port matches neither
  // exactly. `getMyTeamsFlow:192` backs the **calendar**, whose meetup markers
  // and day-click agenda (`CalendarFragment.kt:53,57`) the port's bare
  // `CalendarDatePicker` does not have — and whose own doc comment says
  // Android "leaves selection entirely local to the widget", which is a
  // misreading of those two lines. `getMyTeamDetailsFlow:279` backs the
  // dashboard's **My teams** card, reached only under `fromDashboard`
  // (`TeamViewModel.kt:73`); the port's `home_screen.dart:533` pushes the
  // catalog route unconditionally, so tapping "My teams" opens every team on
  // the planet. `getAll` is the same statement and has **no caller in
  // `app/src/main`** — `TeamsRepositoryImplTest` asserts `exactly = 0` on it,
  // so it is a guard rather than an oversight. Nothing owed for that one.
  'TeamDao.observeAll': 'b0f044f81c1f',
  'TeamDao.getAll': 'b0f044f81c1f',
  // Community leaders never appear in a team's member list. Kotlin reads every
  // docType for the team (`getJoinedMembersWithVisitInfo:969`) so it can append
  // a cached community admin whose `org.couchdb.user:<name>` is in that set,
  // and sorts leaders first (`:992-1002`). The port's member list is
  // `watchTeamDocuments(teamId,'membership')` ordered by `user_id`, with no
  // admin arm and no leaders-first rule.
  'TeamDao.getAllByTeamId': '971ace5365e3',
  // Narrower lookup on Kotlin's side, more destructive removal on the port's.
  // Kotlin's strict `docType = 'resourceLink'` misses the three other link
  // shapes its own `getResourceIdsByTeamId` admits, where the port unlinks
  // through `watchResourceLinks` and finds them. But Kotlin's
  // `removeResourceLink:692` blanks `resourceId` and keeps the document, while
  // the port hard-deletes and enqueues `{_deleted: true}` — same result in
  // both apps' Resources tab, different state for Planet's web UI.
  'TeamDao.getResourceLink': '5583a6fc3ac0',
  // **The root-team test, settled rather than asserted.** Kotlin's is
  // `teamId IS NULL OR TRIM(teamId) = ''` (and `MyTeam.isRootTeam()` is
  // `teamId.isNullOrBlank()`); the port's `watchCatalog`/`teamsByIds` test
  // `docType IS NULL`. Coextensive over anything either app writes: a team
  // document reaches CouchDB with **neither** key — `createTeamAndAddMember`
  // sets `teamId = ""` and no `docType`, and `MyTeam.serialize` strips empty
  // and null keys — while every sub-document carries both. Two classes where
  // they would part: a link document with no `docType` (which Kotlin's own
  // four-way alternation says exists) has a real `teamId`, so Kotlin excludes
  // it and `docType IS NULL` admits it — blocked independently, by
  // `watchCatalog`'s `type = 'team'` conjunct, since a link row's `type` is
  // null in both apps; and a **child team** document, which Kotlin's test
  // exists to exclude and the port's would show in the catalog. Nothing in
  // `app/src/main` creates one. Recorded as the shape to watch, not a live
  // defect.
  'TeamDao.getRootTeamsByType': '502c4c4c222d',
  'TeamDao.getRootTeamsByTypeAndIds': '4ef3f5e1a399',
  // **A missing team looks different in the two apps, and the port's is
  // better.** Kotlin's `_id = :teamId OR teamId = :teamId LIMIT 1` is the
  // fallback arm of `getTeamEntityByAnyId:1275`, and every caller passes what
  // is meant to be a team's `_id` — so the arm only fires when the team
  // document is *not cached*, at which point it returns an arbitrary
  // sub-document of that team picked by scan order, with a null `name`.
  // Android draws a detail screen with a blank title; the port's `getById`
  // matches `_id` alone (its comment at `app_database.dart:1112` records why)
  // and shows "Team not found".
  'TeamDao.getByTeamId': '4b06559b03c1',
  'TeamDao.getById': '1665ff896123',
  // Kotlin chunks 500 at all five call sites and de-duplicates with
  // `distinctBy`; the port chunks at the same width and returns a `Map` keyed
  // by id, so the dedup is structural.
  'TeamDao.getByIds': '8813a6d74109',
  // The membership half is pushed into SQL by the port
  // (`watchMemberships`/`membershipsForUser`) where Kotlin reads every docType
  // and filters in memory — same rows. The **request** half has no
  // counterpart: `getTeamMemberStatuses:574` also builds a pending-request
  // set, and the port's `TeamMemberStatus` carries no `hasPendingRequest`. Not
  // lost — `team_detail_screen` recovers it from `teamRequestsProvider` — and
  // moot in the catalog, because the port's catalog row has no join/leave
  // affordance where `TeamsAdapter.showActionButton:90-146` gives Kotlin's
  // four states. That is a breadth gap, not a query divergence.
  'TeamDao.getByUserId': '8672d14e22f8',
  // **No caller in `app/src/main`** — only a DAO test. The live report reader
  // is the `Flow` sibling below. Nothing owed.
  'TeamDao.getNonArchivedReportsByTeamId': 'dce31e3d595b',
  // Identical predicate and identical `ORDER BY createdDate DESC` in
  // `watchReports`.
  'TeamDao.observeNonArchivedReportsByTeamId': 'dce31e3d595b',
  // Kotlin re-queries a projection for the CSV export; the port passes the
  // list `watchReports` has already delivered (`team_reports_screen.dart:124`).
  // Same rows, same order, same column order.
  'TeamDao.getNonArchivedReportCsvProjectionsByTeamId': 'ef96613c2df2',
  'TeamDao.deleteById': '30bb666a416f',
  // No counterpart and none owed: Kotlin's only caller cleans up after a
  // `_deleted` bulk upload (`deleteLocalTeamRecords:115` ← `TeamsUploader:69`),
  // and the port deletes the row *before* enqueueing the tombstone on all four
  // such paths. Same end state, the other order.
  'TeamDao.deleteByIds': '8e40c51a936b',

  // --- SubmissionDao, the seventeen the previous round enumerated -------
  //
  // `SubmissionDao` is now complete: 33 of 33. Four of the seventeen are dead
  // upstream, five are at parity by another spelling, and eight diverge —
  // none of them losing data today.
  //
  // **Dead upstream, nothing owed.** `getPendingSurveys`
  // (`SubmissionsRepositoryImpl:137` → `SubmissionsRepository.kt:29`, no
  // caller in `app/src`) and `countPendingSurveys`
  // (`SurveysRepositoryImpl:366`, likewise).
  'SubmissionDao.getPendingSurveys': '5ddf8ef4b72c',
  'SubmissionDao.countPendingSurveys': 'a8f88525d633',
  // **Live chain, empty result set, and establishing that took the work.**
  // `CoursesFragment.kt:265,273` always calls `deleteSelected(true)`, so
  // leaving *or* archiving a course reaches `deleteCoursesProgress:612-631`,
  // which feeds `getByCourseIds`' exam ids into
  // `parentId IN (:parentIds) AND type != 'survey' AND uploaded = 0` and then
  // deletes them. But every exam `getByCourseIds` returns has a non-empty
  // `courseId`, and `createExamSubmission:489-496` writes such a submission's
  // `parentId` as `"$examId@$courseId"` — never the bare id. **The `IN`
  // matches nothing.** So the port having no counterpart costs nothing today;
  // it is a coincidence rather than a design, and it is a latent divergence
  // with a trigger, because the port's `pendingUploads()` is unscoped and
  // status-blind, so a `requires grading` attempt for a course the learner
  // deliberately left would still reach Planet the moment any writer produces
  // a bare-id `parentId`.
  'SubmissionDao.getUnuploadedNonSurveyByParentIds': 'ff6b6638ca67',
  'SubmissionDao.deleteByIds': '2b998521b193',

  // **At parity by another spelling.** `getByIds` feeds the due-reminder
  // lookup (`BellDashboardViewModel:80`) and the multi-submission PDF; the
  // port re-reads `pendingSurveysProvider` and filters by id, which is the
  // same population because the reminder ids came from that set to begin
  // with. The multi-submission PDF has no counterpart — a feature gap, not a
  // query gap.
  'SubmissionDao.getByIds': 'e6f971d73cbb',
  // `userId = ? AND teamId = ?` for `findExistingAdoption`; the port runs
  // `byTeam(teamId)` and filters `row.userId` in Dart. The filter moved out of
  // SQL, the rows did not change.
  'SubmissionDao.getByUserIdAndTeamId': '90a8230d3aad',
  // **Both are gates, not badges** — the brief's premise that a `countPending*`
  // divergence would be visible does not hold here. `ServerReachabilityWorker:203,222`
  // uses them as cheap "should I bother?" tests in front of upload configs
  // that carry their own predicates, and the port has no gate at all
  // (`dashboard_sync_provider.queuePendingSubmissions` calls `pendingUploads()`
  // directly). Worth one line because **Kotlin's second gate does not describe
  // its own payload**: `countPendingExamResults` counts
  // `LOWER(status) = 'pending' AND id IN (SELECT submissionId FROM answers …)`
  // while the payload query `getPendingExamResults` has **no status test** at
  // all, so a finished `requires grading` attempt is uploadable and uncounted
  // and the worker skips it. The port cannot reproduce the miss.
  'SubmissionDao.countPendingOfflineSubmissions': 'c0316ed67598',
  'SubmissionDao.countPendingExamResults': 'fa83fb1c24e8',
  // `SET status = ?, isUpdated = 1 WHERE id = ?`, from the arm of
  // `showUserInfoDialog` taken when `isMySurvey || exam?.isFromNation`.
  // `take_survey_screen.dart:236-251` reproduces that gate literally and both
  // of its routes write `status: 'complete', isUpdated: true`.
  'SubmissionDao.updateStatus': 'fe59d8e87e12',

  // **The eight that diverge.**
  //
  // **The port's delete is narrower by a `type = 'exam'` conjunct.** Kotlin is
  // `DELETE FROM submissions WHERE parentId = :parentId AND userId IS :userId`
  // with no type filter, from `deleteExamSubmissions:396` ←
  // `startExamSession:469` under `recreate = true`. The port does not delete by
  // predicate at all: it reads `getExamSubmissionsByUser(userId)`
  // (`userId = ? AND type = 'exam'`) and filters `parentId` in Dart. A
  // non-exam-typed sheet at the same `(parentId, userId)` survives here and
  // not there — reachable exactly where the `_id` overlap above makes one
  // `parentId` name both. Conservative direction: a stale row, not lost
  // answers.
  'SubmissionDao.deleteByParentAndUser': 'e8768f17a3ea',
  // **The zero-question survey the port offers and Android hides.** This
  // drives `SurveysAdapter.kt:79-103`, whose `shouldAdopt` the port covers by
  // sectioning the list instead (`team_surveys_screen.dart:23-24`) — same
  // information, another route. What has no counterpart is the sibling rule in
  // the same block: `if (questionCount == 0) { sendSurvey.visibility = GONE;
  // startSurvey.visibility = GONE }`. A `type: 'surveys'` document with an
  // empty `questions` array — how a survey looks between being created on
  // Planet and its author adding questions — is hidden on Android and offered
  // here, opening a form with nothing to answer.
  'SubmissionDao.getByParentIdsAndTeamId': '4ceb7a7bdfdc',
  // **An unescaped `LIKE` with a `LIMIT 1` and no `ORDER BY`, and no exposure
  // on either side.** `parentId LIKE '%' || :parentIdFragment || '%'`, no
  // `ESCAPE`, from the `?:` fallback of `getSubmissionByRemoteIdOrParentId:285`.
  // Its two push sites (`SubmissionsAdapter:100`, `SubmissionsListAdapter:52`)
  // both pass a **submission row id** — a UUID or CouchDB hex — against a
  // column holding exam/survey ids, so the fallback is unreachable and the
  // parameter name records an intent the callers do not honour. Neither shape
  // contains a `%` or `_`, so nothing leaks. The port has no counterpart
  // statement: its detail screen goes straight to `watchById`
  // (`id = ? OR _id = ? LIMIT 1`), the port of `getByIdOrRemoteId` alone. The
  // port's one unescaped `parentId` `LIKE` is `countCompletedByUserAndExamId`,
  // already compared above and deliberately unescaped because escaping would
  // make it *stricter* than Kotlin.
  'SubmissionDao.getFirstByParentIdContaining': 'b0d909c90a9f',
  // **Three Kotlin uses, one ported, and this is the statement that performs
  // Kotlin's survey resume** — see the correction at
  // `getLatestPendingByUserAndParent` above. `parentId IS ? AND userId IS ?
  // AND (:status IS NULL OR status = ?) ORDER BY startTime DESC`, both ids
  // null-safe. The port's `_pendingSurveySheet` matches it with
  // `equalsNullable` on both, plus the `type = 'survey'` conjunct, which makes
  // the port *safer*: Kotlin's resume would adopt a pending `type='exam'`
  // sheet at the same key and overwrite its answers with survey answers.
  // The second use has no counterpart: `getSubmissionItems` backs
  // `SubmissionsAdapter.showAllSubmissions`' "(N)" badge and its per-parent
  // drill-down, where `submissions_screen.dart` is a flat list of every
  // submission. Five attempts at one exam are one row on Android and five
  // here. Presentation, not data.
  'SubmissionDao.getByParentUserAndStatus': 'db86115e98d0',
  // **No `userId` predicate at all**, and none should be written here.
  // `parentId = ? AND status = ? ORDER BY lastUpdateTime DESC LIMIT 1`, from
  // `PublicSurveyActivity:121` with `(surveyId, "complete")`, guarded only by
  // `lastUpdateTime < launchTime`. On a shared handset it can return *another
  // respondent's* completed sheet for the same public survey, with that
  // staleness filter the only thing between it and an upload of the wrong
  // person's answers. The port holds the id `createSurveyDraft` returned and
  // never re-looks-up, so it cannot express this.
  'SubmissionDao.getLatestByParentIdAndStatus': '9ac796b8b039',
  // **No `parentId` either**, and reproducing it would mis-file an answer.
  // `userId IS ? AND status = 'pending' ORDER BY startTime DESC LIMIT 1`, the
  // last `?:` in `saveExamAnswer:535-546`, reached when the exam session never
  // opened: it attaches the answer to *any* pending sheet the learner holds,
  // plausibly a different exam entirely, and nothing logs it. The port's
  // `saveExamAnswer` takes `submissionId` as a required parameter and has no
  // fallback chain.
  'SubmissionDao.getLatestPendingByUser': '2b08a320f8f2',
  // **A no-effect trigger, traced to its end.** `LOWER(status) = 'pending'` —
  // inconsistent with the three sibling statements' plain `=`, since SQLite's
  // `=` on TEXT is BINARY-collated. It merges into
  // `DashboardViewModel.dashboardDataFlow:246` → `DashboardActivity:594` →
  // `checkAndCreateNewNotifications:389-397`, whose entire body is
  // `updateResourceNotification(userId)` plus an unread count — **neither
  // reads pending surveys**. So the flow's only effect is to re-run the
  // *resource* notification update when a survey row changes, which that
  // method short-circuits when the message is unchanged. Nothing owed, and
  // the case difference is moot because the statement that drives the
  // dashboard prompt is `getUniquePendingSurveyCandidates` (compared above,
  // case-sensitive), which the port matches.
  'SubmissionDao.observePendingSurveys': 'a8a4d6fa6e2e',
  // **`UPDATE … WHERE id = ?` against an upsert, and that is a real
  // difference in the statement.** Kotlin's affects zero rows when the id is
  // absent; the port writes the same three columns through
  // `_dao.upsertAll([SubmissionsCompanion(id, status, lastUpdateTime,
  // isUpdated)])`, i.e. `insertAllOnConflictUpdate`, and every column but `id`
  // on [Submissions] is nullable or defaulted — so an absent id **inserts** a
  // ghost sheet with no `userId`, no `parentId`, no `type` and
  // `startTime = 0`. Latent, because both callers hold an id from a row they
  // just wrote; it matters because such a row is invisible to every
  // type-scoped reader and, at `requires grading`, enters `pendingUploads()`.
  // The `isUpdated: Value(status == 'requires grading')` divergence from
  // Kotlin's unconditional `1` is separate and already documented at the code.
  'SubmissionDao.updateStatusAndLastUpdate': 'a991096f608a',
};
