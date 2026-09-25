import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';

import '../support/stream_provider_reads.dart';

/// The two team-tab feeds, driven through their **providers** rather than
/// their DAOs.
///
/// Phase 158 landed both halves of each in different places: Lane 1 ported the
/// DAO statements (`MyLibraryDao.getTeamPrivate`, `NewsDao.watchTopLevelByTeam`)
/// and left expiring tripwires saying the callers had not moved, because
/// `lib/providers/` was Lane 2's file; the integrator moved the callers and the
/// tripwires duly failed and were retired.
///
/// **This file exists because retiring them left the fixes pinned by nothing.**
/// Measured, not assumed: with both tripwire groups deleted, reverting
/// `teamResourcesProvider`'s private arm and putting `teamVoicesProvider` back
/// on `watchTopLevelMessages` each left the whole suite green. The DAO tests
/// prove the statements are right; only these prove anything *calls* them.
///
/// That is the Phase 154 shape arriving one layer up — a lane's work merging
/// green, tested and dead — and the reason an exemption must never be deleted
/// without checking what is left holding its subject.
void main() {
  late AppDatabase db;
  late ProviderContainer container;

  setUp(() {
    db = AppDatabase.memory();
    // `retry:` is inherited only from a *parent* container and every test
    // container is a root, so the opt-out has to be repeated here — see
    // `test/core/provider_retry_policy_test.dart`, which enforces it and
    // which caught this file omitting it.
    container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [appDatabaseProvider.overrideWithValue(db)],
    );
  });
  tearDown(() {
    container.dispose();
    db.close();
  });

  group('teamResourcesProvider — TeamsRepositoryImpl.getTeamResources:318-323', () {
    Future<void> link(String teamId, String resourceId) =>
        db.teamDao.upsertAll([
          TeamsCompanion.insert(
            id: 'link-$resourceId',
            teamId: Value(teamId),
            docType: const Value('resourceLink'),
            resourceId: Value(resourceId),
          ),
        ]);

    Future<void> resource(
      String id, {
      bool isPrivate = false,
      String? privateFor,
    }) => db.myLibraryDao.upsertAll([
      MyLibraryTableCompanion.insert(
        id: id,
        isPrivate: Value(isPrivate),
        privateFor: privateFor == null
            ? const Value.absent()
            : Value(privateFor),
      ),
    ]);

    test('the tab is the union of the linked and the private arms', () async {
      // `linked-only` is reachable through a resourceLink document and is not
      // private; `private-only` is the arm the port never had — `isPrivate` is
      // what `add_resource_screen` defaults to when opened from a team, and
      // nothing links it. Before the union, a resource in that state was in no
      // view of the app at all.
      await resource('linked-only');
      await resource('private-only', isPrivate: true, privateFor: 'T');
      await link('T', 'linked-only');

      final rows = await readStreamValue(container, teamResourcesProvider('T'));

      expect(
        rows.map((r) => r.id).toSet(),
        {'linked-only', 'private-only'},
        reason:
            'teamResourcesProvider must union myLibraryDao.getTeamPrivate '
            'with the link rows. Dropping either arm fails here.',
      );
    });

    test('a resource that is both linked and private appears once', () async {
      // The de-duplication is load-bearing rather than defensive: Kotlin ends
      // getTeamResources with `distinctBy { it.id }` precisely because a team
      // can hold a resource privately *and* carry a resourceLink to it, which
      // is the ordinary state after that resource has been uploaded.
      await resource('both', isPrivate: true, privateFor: 'T');
      await link('T', 'both');

      final rows = await readStreamValue(container, teamResourcesProvider('T'));

      expect(rows.map((r) => r.id).toList(), ['both']);
    });

    test("another team's private resource is not in this team's tab", () async {
      // The decoy that makes the previous tests mean something: without the
      // `privateFor` predicate the union would pull every private resource on
      // the device into every team.
      await resource('theirs', isPrivate: true, privateFor: 'OTHER');
      await resource('mine', isPrivate: true, privateFor: 'T');

      final rows = await readStreamValue(container, teamResourcesProvider('T'));

      expect(rows.map((r) => r.id).toList(), ['mine']);
    });
  });

  group('teamVoicesProvider — NewsDao.getTopLevelByTeamFlow:30-31', () {
    Future<void> post(
      String id, {
      required int time,
      String? viewIn,
      String? viewableBy,
      String? viewableId,
      String? replyTo,
      String docType = 'message',
    }) => db.newsDao.upsertAll([
      NewsEntriesCompanion.insert(
        id: id,
        docType: Value(docType),
        time: Value(time),
        replyTo: replyTo == null ? const Value.absent() : Value(replyTo),
        viewIn: viewIn == null ? const Value.absent() : Value(viewIn),
        viewableBy: viewableBy == null
            ? const Value.absent()
            : Value(viewableBy),
        viewableId: viewableId == null
            ? const Value.absent()
            : Value(viewableId),
      ),
    ]);

    String viewInFor(String teamId) =>
        '[{"_id":"$teamId","section":"teams","name":"T"}]';

    test('both of Kotlin\'s arms reach the feed', () async {
      // `by-viewin` is what the in-app composer writes; `by-viewable` is what a
      // server-authored team post carries. The hand-rolled Dart filter this
      // provider used to run read only the first, so the second never appeared
      // in the feed — while the dashboard badge, which is the same statement in
      // Kotlin, counted it. The feed and its own watermark could not agree.
      await post('by-viewin', time: 10, viewIn: viewInFor('team-1'));
      await post(
        'by-viewable',
        time: 20,
        viewableBy: 'teams',
        viewableId: 'team-1',
      );

      final rows = await readStreamValue(
        container,
        teamVoicesProvider('team-1'),
      );

      expect(rows.map((r) => r.id).toList(), ['by-viewable', 'by-viewin']);
    });

    test(
      'a reply is not in the feed, and another team is not either',
      () async {
        // Two decoys, one per remaining conjunct of the Kotlin predicate.
        await post('top', time: 30, viewIn: viewInFor('team-1'));
        await post(
          'reply',
          time: 40,
          viewIn: viewInFor('team-1'),
          replyTo: 'top',
        );
        await post('other-team', time: 50, viewIn: viewInFor('team-2'));

        final rows = await readStreamValue(
          container,
          teamVoicesProvider('team-1'),
        );

        expect(rows.map((r) => r.id).toList(), ['top']);
      },
    );

    test('docType is not a predicate here', () async {
      // The old provider went through `watchTopLevelMessages`, which adds
      // `docType = 'message'`. Kotlin's statement has no such conjunct, so a
      // team post stored under any other docType was silently absent.
      await post(
        'not-a-message',
        time: 10,
        docType: 'news',
        viewIn: viewInFor('team-1'),
      );

      final rows = await readStreamValue(
        container,
        teamVoicesProvider('team-1'),
      );

      expect(rows.map((r) => r.id).toList(), ['not-a-message']);
    });
  });
}
