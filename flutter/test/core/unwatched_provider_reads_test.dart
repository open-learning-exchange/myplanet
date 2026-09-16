import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// **The port's most-repeated defect, and until now nothing guarded it.**
///
/// A screen or an action provider calls `ref.read(someProvider).value` without
/// ever *watching* that provider. In Riverpod 3 `AsyncValue.value` is
/// "previous value, else null" (`async_value.dart:557`), so with nothing
/// listening the element has never been driven and the read is null. The
/// caller then takes a silent failure branch — and because it is the *same*
/// branch a genuinely signed-out user takes, nothing logs, nothing tells the
/// user, and the action simply does not happen.
///
/// Found by hand five separate times before this file existed:
///
/// * Phase 100 — `take_exam_screen._submitExam`: a graded exam attempt
///   discarded with no dialog, no snackbar and no row.
/// * Phase 102 — `add_resource_screen`.
/// * Phase 103 — `public_survey_screen._submit` and `_leave`.
/// * Phase 104/105 — `take_survey_screen`, found separately by two lanes in
///   one round.
///
/// It is latent in the shipping app only because the router holds a
/// `ref.listen(sessionProvider, …)`, which keeps that one provider resolved.
/// That is a property of one caller, not a guarantee the callee can rely on,
/// and `background_entrypoint.dart` does not provide it at all.
///
/// The fix is [resolveSession] in `lib/providers/session_provider.dart`:
/// `await ref.read(sessionProvider.future)`, with the read *and* the await
/// inside the enclosing `try`, because a future can reject where `.value`
/// could only be null.
///
/// ## What this file does, and the three things it gets right on purpose
///
/// **Comments are stripped and string bodies are blanked before scanning.**
/// 63 of the 224 textual `.value` occurrences the Riverpod 3 round counted
/// were prose in doc comments — including several that quote this exact shape
/// while explaining why it was removed, and including the paragraphs above.
/// A scanner that read them would fire on its own documentation. Blanking
/// string bodies (rather than dropping the literals) also makes Dart's
/// adjacent-literal concatenation a non-issue: `'${Routes.submissions}/'
/// '${Uri.encodeComponent(id)}'` is one string that `dart format` wrapped, and
/// there are 91 such pairs in `lib/`. Nothing here reads literal *content*, so
/// blanking is strictly safer than trying to re-join them, and offsets are
/// preserved so every reported line number stays true.
///
/// **Family providers count.** `ref.read(examProvider(widget.examId)).value`
/// is the same defect as `ref.read(sessionProvider).value`, and a regex over
/// a bare identifier misses it. A first cut did exactly that and silently
/// dropped three real sites — `take_exam_screen:539` and both of
/// `home_screen`'s dashboard cards — so the provider expression is taken with
/// a balanced-paren scan instead.
///
/// **The two-step shape counts too.** `final s = ref.read(p); … s.value` is
/// the same read spelled over two statements, and the direct rule cannot see
/// it. `lib/ui/router.dart` is the one place in `lib/` that uses it — and uses
/// it *correctly*, which is why it is exempted below rather than absent: a
/// rule with no instances is a rule nobody has tested.
///
/// ## Exemptions expire
///
/// Phase 157's lesson, and the best thing in that round: an allow-list entry
/// that only ever suppresses is a silent debt, while one that also fails when
/// its reason stops being true is a scheduled reminder. Every entry in
/// [_exempt] therefore carries a declared occurrence count, and the
/// reconciliation below fails in **both** directions — an unexempted offender
/// fails it, and so does an exemption whose site has been fixed, moved or
/// multiplied. The failure message names the retirement condition, so the
/// integrator's job is mechanical rather than archaeological.
void main() {
  group('unwatched provider reads', () {
    test('every ref.read(p).value in lib/ is resolved or exempted', () {
      final found = <String, List<int>>{};
      for (final entity in Directory('lib').listSync(recursive: true)) {
        if (entity is! File || !entity.path.endsWith('.dart')) continue;
        // Drift's output is generated from `tables.dart`; it carries no
        // provider reads and regenerating it must never turn this red.
        if (entity.path.endsWith('.g.dart')) continue;
        final path = entity.path.replaceAll(r'\', '/');
        for (final site in findUnwatchedReads(entity.readAsStringSync())) {
          (found['$path::${site.provider}'] ??= []).add(site.line);
        }
      }

      final problems = reconcile(found, _exempt);
      expect(
        problems,
        isEmpty,
        reason:
            'A provider read but never watched is null. Resolve it with '
            '`await resolveSession(ref)` (or `await ref.read(p.future)`), '
            'putting the read inside the enclosing `try` — a future can '
            'reject where `.value` could only be null. If the site is '
            'genuinely safe, add it to `_exempt` with a reason and a '
            'retirement condition.\n\n${problems.join('\n\n')}',
      );
    });

    test('the scanner classifies each shape correctly', () {
      // Phase 134's rule: a test that cannot fail reads as coverage, and
      // Phase 156's corollary: when it stays green, suspect the *fixture*
      // before the assertion. So each of these is a shape the scanner has to
      // separate from its neighbour, not a shape it obviously handles.
      String providers(String source) =>
          findUnwatchedReads(source).map((s) => s.provider).join(',');

      // The plain shape, and the one `dart format` produces when the line is
      // long enough to wrap — the same read, three tokens apart.
      expect(
        providers('final u = ref.read(sessionProvider).value;'),
        'session',
      );
      expect(
        providers('final u = ref\n    .read(sessionProvider)\n    .value;'),
        'session',
      );
      expect(
        providers('final u = _ref.read(sessionProvider).value;'),
        'session',
      );

      // A family. The decoy is the argument list: a scanner that stops at the
      // first `)` sees `.value` nowhere and reports nothing at all.
      expect(
        providers('final e = ref.read(examProvider(widget.examId)).value;'),
        'exam',
      );

      // Resolved reads are the whole point of the fix and must not fire.
      expect(
        providers('final u = await ref.read(sessionProvider.future);'),
        '',
      );
      expect(providers('final u = ref.watch(sessionProvider).value;'), '');
      // `.notifier` and a plain synchronous read are not AsyncValue reads.
      expect(providers('ref.read(fooProvider.notifier).state = 1;'), '');
      expect(providers('final c = ref.read(serverConfigProvider);'), '');

      // Prose. Every one of these appears verbatim in `lib/` today, which is
      // why stripping comments is load-bearing rather than tidy.
      expect(providers('// ref.read(sessionProvider).value is null until'), '');
      expect(
        providers('/// `ref.read(sessionProvider).value?.planetCode`'),
        '',
      );
      expect(
        providers('/* a block\n * ref.read(sessionProvider).value\n */'),
        '',
      );
      // A block comment must not swallow the code after it.
      expect(
        providers('/* note */ final u = ref.read(sessionProvider).value;'),
        'session',
      );

      // A string body. Blanking it means the adjacent-literal pairs `dart
      // format` leaves all over `lib/` cannot confuse anything here.
      expect(providers("log('ref.read(sessionProvider).value');"), '');
      expect(providers("const a = 'ref.read(pProvider)' '.value';"), '');
      // …but a string must not hide the statement that follows it.
      expect(
        providers("log('hi'); final u = ref.read(sessionProvider).value;"),
        'session',
      );
    });

    test('the two-step shape is caught, and only inside its own block', () {
      String providers(String source) =>
          findUnwatchedReads(source).map((s) => s.provider).join(',');

      // `lib/ui/router.dart`'s shape.
      expect(
        providers(
          'void f() {\n'
          '  final session = ref.read(sessionProvider);\n'
          '  final signedIn = session.value != null;\n'
          '}',
        ),
        'session',
      );
      // A nested block still counts — it is the same variable.
      expect(
        providers(
          'void f() {\n'
          '  final session = ref.read(sessionProvider);\n'
          '  if (x) { use(session.value); }\n'
          '}',
        ),
        'session',
      );

      // The decoy that makes this a test rather than a restatement: the same
      // two lines in *different* blocks, and a same-named local that is not
      // the one read. Both were false positives in the first cut, and a
      // whole-file search reports them as violations.
      expect(
        providers(
          'void f() {\n'
          '  final session = ref.read(sessionProvider);\n'
          '  use(session);\n'
          '}\n'
          'void g() {\n'
          '  final session = other();\n'
          '  use(session.value);\n'
          '}',
        ),
        '',
      );
      // A non-provider read — a drift row's `read`, which `app_database.dart`
      // uses — must not be mistaken for one.
      expect(
        providers(
          'void f() {\n'
          '  final courseId = row.read(courseSteps.courseId);\n'
          '  use(courseId.value);\n'
          '}',
        ),
        '',
      );
    });

    test('redaction preserves every line, so reported lines are true', () {
      // **The `at <path>:<line>` list is the whole value of this guard**, and
      // the second audit pass found it wrong by up to ~120 lines: the
      // line-comment branch wrote its newline *and* left `i` pointing at it,
      // so every `//` above a site shifted that site down by one.
      //
      // The shape-classification tests above could not see it — every fixture
      // there is a single line with nothing above it. So this pins the
      // invariant directly, on each construct that has its own branch.
      for (final source in [
        '// a comment\nfinal u = ref.read(sessionProvider).value;\n',
        '/// doc\n/// doc\nclass A {}\n',
        '/* block\nspanning\nlines */\nfinal x = 1;\n',
        "final s = 'a\\nb';\nfinal t = '''\nmulti\n''';\n",
        "final u = '\${d.month.toString().padLeft(2, '0')}';\n",
      ]) {
        expect(
          '\n'.allMatches(redactCommentsAndStrings(source)).length,
          '\n'.allMatches(source).length,
          reason: 'newline count changed for: $source',
        );
      }

      // And end to end, which is what actually gets reported.
      expect(
        findUnwatchedReads(
          '// one\n'
          '// two\n'
          'final u = ref.read(sessionProvider).value;\n',
        ).single.line,
        3,
      );
    });

    test('the exemption bookkeeping fails in both directions', () {
      // An exemption that only suppresses is a silent debt. This is the half
      // that makes it a reminder instead, and mutating the map both ways is
      // the only thing that proves the map is not simply switched off.
      const reason = Exemption(count: 1, why: 'because', retire: 'when fixed');

      // Direction 1: an offender nobody exempted.
      expect(
        reconcile({
          'a.dart::p': [7],
        }, const {}),
        [contains('a.dart:7')],
      );

      // Direction 2: an exemption whose site is gone. This is the one Phase
      // 157 shipped and the one that pays: the entry notices its own reason
      // has expired, and says what to delete.
      final stale = reconcile(const {}, const {'a.dart::p': reason});
      expect(stale, hasLength(1));
      expect(stale.single, contains('no longer occurs'));
      expect(stale.single, contains('when fixed'));

      // Direction 3: the count moved, either way. A second occurrence added
      // to an exempt file must not ride in free on the first one's reason.
      expect(
        reconcile(
          {
            'a.dart::p': [7, 9],
          },
          const {'a.dart::p': reason},
        ),
        [contains('2 occurrences')],
      );

      // And the only green case: exactly what was declared.
      expect(
        reconcile(
          {
            'a.dart::p': [7],
          },
          const {'a.dart::p': reason},
        ),
        isEmpty,
      );
    });

    test('every exemption states a reason and a retirement condition', () {
      // An entry whose `why` is empty is an allow-list entry wearing a
      // costume, and one with no `retire` can never expire.
      for (final entry in _exempt.entries) {
        expect(entry.value.why.trim(), isNotEmpty, reason: entry.key);
        expect(entry.value.retire.trim(), isNotEmpty, reason: entry.key);
        expect(entry.value.count, greaterThan(0), reason: entry.key);
      }
    });
  });
}

/// The sites still reading a provider they do not watch, each with why it is
/// tolerated and what has to change for the entry to go.
///
/// Keyed `<path>::<providerName>` — the provider's *name*, so a family's
/// argument expression can be renamed without churning this map, and counted,
/// so a second occurrence in an already-exempt file cannot ride in free.
///
/// **Not one of these is this lane's file.** Phase 158 Lane 2 owned
/// `lib/providers/**` (less `feedback_provider.dart`) and resolved all 29
/// sites there. Everything below is `lib/ui/` or another lane's file.
///
/// ## How wide the window actually is, because the first cut of this map
/// overstated it
///
/// `routerProvider`'s `_RouterRefresh` (`lib/ui/router.dart:780-785`) holds
/// `ref.listen(sessionProvider, …)` for the life of the process, and
/// `lib/background_entrypoint.dart` never touches `sessionProvider` at all —
/// it goes straight to `prefs.loggedInUserId` and `userDaoProvider`. So in
/// the shipping app there is no non-router entry point that reaches these
/// sites, and the window is **first resolution only**: app start until the
/// persisted session has been read back. Real — a deep link delivered at cold
/// start lands squarely in it — but not the "silently discarded" this map
/// first claimed of nine of its entries.
///
/// Each reason below therefore names the **gate**, in one of three classes:
///
/// * **unreachable** — the callback cannot run with a null value at all,
///   because a `if (x != null)` or a `onPressed: x == null ? null : …`
///   stands between it and the user.
/// * **watched** — some mounted widget in the same tree watches the provider,
///   so the element is driven and the only exposure is the frames before it
///   emits, during which the screen shows a spinner or a placeholder.
/// * **unwatched** — nothing drives it; this is the live class, and the
///   entry is a defect waiting for its owner.
///
/// The first cut of this map applied *watched ⇒ safe* to `take_exam_screen`
/// and its negation to six neighbours, having read each method without
/// checking what gates its caller. Phase 149's rule — trace the caller chain
/// to its end before judging a line — applies to writing an exemption too.
const _exempt = <String, Exemption>{
  // ---- unreachable: the callback cannot run with a null value ----
  'lib/ui/resources/resource_detail_screen.dart::session': Exemption(
    count: 1,
    why:
        '`_toggleLibraryMembership:446`. The button that calls it is rendered '
        'under `if (session != null) ...[` (`:351`), where `session` is '
        '`ref.watch(sessionProvider).value` at `:329` in the same '
        '`ConsumerState`. With a null session the button does not exist.',
    retire:
        'Delete if the `:351` gate goes, or if another caller of '
        '`_toggleLibraryMembership` appears outside it.',
  ),
  'lib/ui/feedback/feedback_create_screen.dart::session': Exemption(
    count: 1,
    why:
        'Lane 3\'s file. `_submit:202` — and **not** the live defect an '
        'earlier revision of this entry called it. `build:40` watches the '
        'session and `:170` is `onPressed: session == null ? null : _submit`, '
        'which is `_submit`\'s only trigger, so the button is disabled rather '
        'than the form discarded.',
    retire: 'Delete if the `:170` gate goes, or when the read is resolved.',
  ),
  'lib/ui/notifications/notifications_screen.dart::notifications': Exemption(
    count: 1,
    why:
        '`:278`, inside `_GroupHeader` (`:264`), which is constructed at '
        '`:247` from the grouped tree derived from `notificationsProvider` — '
        'watched at `build:48`. A header cannot exist before the provider has '
        'emitted, so the `?? const []` is unreachable.',
    retire: 'Delete if `_GroupHeader` gains a caller outside that tree.',
  ),

  // ---- watched: driven by a mounted widget; exposure is the pre-emit frames
  'lib/ui/router.dart::session': Exemption(
    count: 1,
    why:
        'The two-step shape (`:240`, `:254`) — and the one place in `lib/` '
        'that handles this class deliberately rather than accidentally. `:252` '
        'is `if (session.isLoading) return null;`, so the redirect holds '
        'position until the persisted session has been read back and `.value` '
        'is only reached once resolved. go_router\'s `redirect` is the '
        'synchronous consumer this pattern exists for; awaiting here would '
        'change routing, not fix anything.',
    retire:
        'Delete if the `session.isLoading` hold at `:252` is ever removed — '
        'without it this becomes the ordinary defect, on the one read the '
        'whole app\'s first paint depends on.',
  ),
  'lib/ui/exam/take_exam_screen.dart::exam': Exemption(
    count: 1,
    why:
        '`_submitExam:539`. `build:92` watches this provider and the same flow '
        'already awaited `examProvider(...).future` at `:453`, so it is '
        'resolved by the time `:539` reads it. Worth stating rather than '
        'assuming: Phase 100 fixed the *session* read in this very method.',
    retire:
        'Delete if `:92`\'s `ref.watch` or the `:453` await goes — either '
        'alone is what makes this read safe.',
  ),
  'lib/ui/courses/course_detail_screen.dart::session': Exemption(
    count: 1,
    why:
        '`_toggleMembership:79`, a method of `_CourseBody`, whose `build:92` '
        'watches the session; `CourseDetailScreen.build:31` watches it too. '
        'Pre-emit only, and even then the membership write has already '
        'happened and is flagged for upload — what is skipped is the '
        'immediate shelf push, which a later sync redoes.',
    retire: 'Delete when the read is resolved, or if both watches go.',
  ),
  'lib/ui/dashboard/home_screen.dart::session': Exemption(
    count: 1,
    why:
        '`_showDue:108`. `_HomeScreenState.initState:71` holds '
        '`ref.listenManual(sessionProvider, fireImmediately: true, …)` and '
        '`build:330` watches it, so the provider is driven from the first '
        'frame. Pre-emit the snoozed-survey reminder is simply not shown.',
    retire: 'Delete when the read is resolved, or if the `:71` listen goes.',
  ),
  'lib/ui/dashboard/home_screen.dart::myLibraryStream': Exemption(
    count: 1,
    why:
        '`_openLibraryCard:310`. The card whose `onTap` calls it has '
        '`_LibraryTiles(userId: session.id)` as its own child (`:500-501`), '
        'and `_LibraryTiles.build:1091` watches '
        '`myLibraryStreamProvider(userId)` on the **same family key** — so by '
        'the time the card is tappable the provider is driven. Pre-emit, '
        '`?? const []` reads as "empty shelf" and the card opens the catalog '
        'rather than My Library: wrong destination, nothing lost.',
    retire: 'Delete when the read is resolved, or if `_LibraryTiles` moves.',
  ),
  'lib/ui/dashboard/home_screen.dart::myCoursesStream': Exemption(
    count: 1,
    why:
        '`_openCoursesCard:320` — identical to the library card above, via '
        '`_CourseTiles` at `:520` and its watch at `:1138`.',
    retire: 'Delete when the read is resolved, or if `_CourseTiles` moves.',
  ),
  'lib/ui/teams/team_courses_screen.dart::teamCourses': Exemption(
    count: 1,
    why:
        '`_chooseCourse:83`. `build:17` watches the same family key, so this '
        'is pre-emit only; in that window "nothing linked yet" makes the '
        'picker offer courses the team already has.',
    retire: 'Delete when the read is resolved.',
  ),
  'lib/ui/teams/team_resources_screen.dart::teamResources': Exemption(
    count: 1,
    why: '`_chooseResource:69` — the resource analogue of `teamCourses` above.',
    retire: 'Delete when the read is resolved.',
  ),
  'lib/ui/achievements/edit_achievement_screen.dart::session': Exemption(
    count: 2,
    why:
        'Two sites, both watched, for different reasons. `_initialize:72` '
        'early-returns without setting `_initialized` (set at `:91`, past the '
        'guard), so the prefill simply retries on the next build rather than '
        'leaving the blank form Phase 155 found in `add_examination_screen`. '
        '`_save:150` is reached from under `build:250`\'s watch of '
        '`achievementEntryProvider`, which itself does '
        '`ref.watch(sessionProvider)` (`achievements_provider.dart:14`), so '
        'the session is listened transitively from the first frame.',
    retire:
        'Delete when both reads are resolved. Drop the count to 1 if only one '
        'is — do not delete the entry.',
  ),
  'lib/ui/achievements/edit_achievement_screen.dart::achievementEntry':
      Exemption(
        count: 1,
        why:
            '`_initialize:73`. `build:250` watches this provider and only '
            'calls `_initialize` when `entry.value != null` (`:251`), so the '
            'read is resolved. Listed rather than omitted because the guard '
            'cannot see that gating, and a future edit moving the call out of '
            '`build` would make it real.',
        retire:
            'Delete if `_initialize` stops being called from under the `:251` '
            'gate, or when it is rewritten to take the row as a parameter.',
      ),

  // ---- unwatched: nothing drives these; the live class ----
  'lib/ui/submissions/submissions_screen.dart::session': Exemption(
    count: 1,
    why:
        '`:173`, and the one unambiguous defect in this map. It is the only '
        'occurrence of `sessionProvider` in the file and nothing in its tree '
        'watches it, so inside the first-resolution window a draft the user '
        'has titled, answered and confirmed is dropped with no snackbar and '
        'no row — the Phase 100 shape.',
    retire: 'Delete with the fix: `final user = await resolveSession(ref);`.',
  ),
  'lib/ui/teams/team_courses_screen.dart::coursesStream': Exemption(
    count: 1,
    why:
        '`_chooseCourse:85`, and unlike its `teamCourses` neighbour two lines '
        'up, nothing watches this one. Pre-resolution it reads as "no courses '
        'exist" and the picker opens empty.',
    retire: 'Delete with the fix.',
  ),
  'lib/ui/teams/team_resources_screen.dart::resourcesStream': Exemption(
    count: 1,
    why: '`_chooseResource:71` — the resource analogue of `coursesStream`.',
    retire: 'Delete with the fix.',
  ),
  'lib/providers/feedback_provider.dart::session': Exemption(
    count: 2,
    why:
        'Lane 3\'s file, carved out of Lane 2\'s set. `:133` tags an outbox '
        'row (inert — the `outbox.userId` column has no reader in either app; '
        'see the lane report); `:224` is on `FeedbackNotifier`\'s submit path '
        'and nothing watches the session for it.',
    retire: 'Delete when Lane 3 lands the fix, which was in flight.',
  ),
};

/// One tolerated site: how many occurrences were seen, why, and what has to
/// change before the entry is deleted.
class Exemption {
  const Exemption({
    required this.count,
    required this.why,
    required this.retire,
  });

  final int count;
  final String why;
  final String retire;
}

/// One unwatched read: the provider's name and the line it sits on.
class UnwatchedRead {
  const UnwatchedRead(this.provider, this.line);

  final String provider;
  final int line;
}

/// Compares what the scan found against what is declared, **in both
/// directions**. Returns one message per problem; empty means the tree and the
/// map agree.
List<String> reconcile(
  Map<String, List<int>> found,
  Map<String, Exemption> exempt,
) {
  final problems = <String>[];
  for (final entry in found.entries) {
    final path = entry.key.split('::').first;
    final sites = entry.value.map((line) => '$path:$line').join(', ');
    final allowed = exempt[entry.key];
    if (allowed == null) {
      problems.add(
        'UNRESOLVED  ${entry.key}\n  at $sites\n'
        '  This provider is read but never watched, so the read is null until '
        'something else resolves it.',
      );
    } else if (allowed.count != entry.value.length) {
      problems.add(
        'COUNT MOVED  ${entry.key}\n  at $sites\n'
        '  The exemption declares ${allowed.count}; there are now '
        '${entry.value.length} occurrences. If one was fixed, lower the count. '
        'If one was added, resolve it — an existing entry\'s reason does not '
        'extend to a new site.\n  Retirement: ${allowed.retire}',
      );
    }
  }
  for (final entry in exempt.entries) {
    if (found.containsKey(entry.key)) continue;
    problems.add(
      'EXEMPTION EXPIRED  ${entry.key}\n'
      '  This read no longer occurs, so the exemption is now a silent debt '
      'rather than a record. Delete the entry.\n'
      '  Retirement condition it was written with: ${entry.value.retire}',
    );
  }
  return problems;
}

/// Every `read(p).value` in [source], direct or over two statements, with
/// comments stripped and string bodies blanked first.
List<UnwatchedRead> findUnwatchedReads(String source) {
  final code = redactCommentsAndStrings(source);
  final sites = <UnwatchedRead>[..._directReads(code), ..._twoStepReads(code)];
  sites.sort((a, b) => a.line.compareTo(b.line));
  return sites;
}

/// `<receiver>.read(<provider>).value`, with the provider expression taken by
/// balanced parens so a family's argument list cannot truncate it.
Iterable<UnwatchedRead> _directReads(String code) sync* {
  for (final match in RegExp(r'\.\s*read\s*\(').allMatches(code)) {
    final open = match.end - 1;
    final close = _close(code, open);
    if (close == null) continue;
    if (!RegExp(r'^\s*\)\s*\.\s*value\b').hasMatch(code.substring(close))) {
      continue;
    }
    final name = _providerName(code.substring(open + 1, close));
    if (name == null) continue;
    yield UnwatchedRead(name, _lineOf(code, match.start));
  }
}

/// `final x = <receiver>.read(<provider>); … x.value`, where the `x.value` is
/// inside the block the assignment sits in.
///
/// Scoped rather than searched file-wide: two same-named locals in different
/// functions are ordinary Dart, and a whole-file search reports them as a
/// violation. Measured — that shape occurs three times in `lib/` today
/// (`courses_providers.dart`, `take_exam_screen.dart`, `app_database.dart`)
/// and every one is a false positive.
Iterable<UnwatchedRead> _twoStepReads(String code) sync* {
  final assignment = RegExp(
    r'(?:final|var)\s+(\w+)\s*=\s*\w+\s*\.\s*read\s*\(',
  );
  for (final match in assignment.allMatches(code)) {
    final open = match.end - 1;
    final close = _close(code, open);
    if (close == null) continue;
    // A `.future`/`.notifier` tail, or a `.value` directly after, is somebody
    // else's rule — the first is the fix, the last is [_directReads].
    if (!RegExp(r'^\s*\)\s*;').hasMatch(code.substring(close))) continue;
    final name = _providerName(code.substring(open + 1, close));
    if (name == null) continue;
    final scope = _enclosingBlock(code, close);
    final local = RegExp(
      r'\b' + RegExp.escape(match.group(1)!) + r'\s*\.\s*value\b',
    );
    if (!local.hasMatch(code.substring(close, scope))) continue;
    yield UnwatchedRead(name, _lineOf(code, match.start));
  }
}

/// The provider's bare name — `sessionProvider` from `sessionProvider`, and
/// from `examProvider(widget.examId)` — with the `Provider` suffix dropped so
/// the exemption keys read as domain names.
///
/// Returns null for anything that is not a provider reference: a repository
/// handle (`ref.read(coursesRepositoryProvider)` is synchronous and has no
/// `AsyncValue`), a `.notifier`, or a drift row's own `read`.
String? _providerName(String expression) {
  final head = RegExp(r'^\s*([A-Za-z_]\w*)').firstMatch(expression);
  if (head == null) return null;
  final name = head.group(1)!;
  if (!name.endsWith('Provider')) return null;
  final rest = expression.substring(head.end).trimLeft();
  // `p.notifier`, `p.future`, `p.select(...)` — none of them yield an
  // `AsyncValue`, so none of them can carry this defect.
  if (rest.startsWith('.')) return null;
  return name.substring(0, name.length - 'Provider'.length);
}

/// The offset just past the block enclosing [from] — where a local declared at
/// [from] goes out of scope.
int _enclosingBlock(String code, int from) {
  var depth = 0;
  for (var i = from; i < code.length; i++) {
    if (code[i] == '{') depth++;
    if (code[i] == '}') {
      if (depth == 0) return i;
      depth--;
    }
  }
  return code.length;
}

int? _close(String code, int open) {
  var depth = 0;
  for (var i = open; i < code.length; i++) {
    if (code[i] == '(') depth++;
    if (code[i] == ')') {
      depth--;
      if (depth == 0) return i;
    }
  }
  return null;
}

int _lineOf(String code, int offset) =>
    '\n'.allMatches(code.substring(0, offset)).length + 1;

/// Comments removed and string bodies replaced with spaces, with every newline
/// kept so offsets — and therefore the reported line numbers — are unchanged.
///
/// Blanking rather than deleting is what makes Dart's adjacent-literal
/// concatenation harmless here: nothing downstream reads literal content, so
/// whether `'a' 'b'` is one string or two never comes up. There are 91 such
/// wrapped pairs in `lib/`.
///
/// **Interpolated expressions stay as code**, and that is not a refinement —
/// it is what stops the redactor corrupting the file. `'${date.month
/// .toString().padLeft(2, '0')}'` is one string containing a *quote*, and a
/// scanner that ends the string there reads the rest of the line as code and
/// the code after it as a string, blanking whatever follows. Ten files in
/// `lib/` contain that shape today.
String redactCommentsAndStrings(String source) {
  final out = StringBuffer();
  // A stack of frames: code, or a string that a `${…}` may re-enter code from.
  // `braces` is the brace depth within the innermost code frame, so the `}`
  // closing an interpolation can be told from one closing a map literal.
  final strings = <({String terminator, bool raw})?>[null];
  final braces = <int>[0];
  var i = 0;

  void blank(String text) {
    for (final unit in text.split('')) {
      out.write(unit == '\n' ? '\n' : ' ');
    }
  }

  while (i < source.length) {
    final open = strings.last;
    final char = source[i];

    if (open == null) {
      if (char == '/' && i + 1 < source.length && source[i + 1] == '/') {
        // Stop *on* the newline and let the main loop write it. Writing it
        // here and continuing emitted it twice — `i` had not advanced past it
        // — so every line comment inflated every line number below it by one.
        // Found by the second audit pass; `take_exam_screen.dart`'s read at
        // 539 was being reported at 662.
        while (i < source.length && source[i] != '\n') {
          i++;
        }
        continue;
      }
      if (char == '/' && i + 1 < source.length && source[i + 1] == '*') {
        final end = source.indexOf('*/', i + 2);
        final stop = end == -1 ? source.length : end + 2;
        blank(source.substring(i, stop));
        i = stop;
        continue;
      }
      if (char == "'" || char == '"') {
        final triple = source.startsWith(char * 3, i);
        final terminator = triple ? char * 3 : char;
        strings.add((
          terminator: terminator,
          raw: i > 0 && source[i - 1] == 'r',
        ));
        blank(terminator);
        i += terminator.length;
        continue;
      }
      if (char == '{') {
        braces[braces.length - 1]++;
        out.write(char);
        i++;
        continue;
      }
      if (char == '}') {
        if (braces.last == 0 && strings.length > 1) {
          // The end of a `${…}`; the string it interrupted resumes.
          braces.removeLast();
          strings.removeLast();
          out.write(' ');
          i++;
          continue;
        }
        if (braces.last > 0) braces[braces.length - 1]--;
        out.write(char);
        i++;
        continue;
      }
      out.write(char);
      i++;
      continue;
    }

    if (!open.raw && char == r'\' && i + 1 < source.length) {
      blank(source.substring(i, i + 2));
      i += 2;
      continue;
    }
    if (source.startsWith(open.terminator, i)) {
      blank(open.terminator);
      strings.removeLast();
      i += open.terminator.length;
      continue;
    }
    if (!open.raw && source.startsWith(r'${', i)) {
      // Back into code, for as long as the interpolation lasts. The string
      // frame stays on the stack underneath it.
      strings.add(null);
      braces.add(0);
      blank(r'${');
      i += 2;
      continue;
    }
    blank(char);
    i++;
  }
  return out.toString();
}
