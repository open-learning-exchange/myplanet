import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/sync/heavy_table_sync.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/mock_planet_api.dart';

/// The resumable checkpoint, which is the whole of this feature.
///
/// Every assertion here was mutation-tested: the production behaviour it
/// claims to pin was flipped and the test confirmed red. Where a mutation is
/// not obvious the test says which one it survives, because a test that cannot
/// fail reads as coverage.
/// Stands in for the 1s/2s/4s page backoff so the retry tests cost no
/// wall-clock time; the delays themselves are pinned as a constant.
Future<void> _noSleep(Duration _) async {}

void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  late MockPlanetApi api;
  late SharedPreferences store;
  late PlanetPrefs prefs;
  late List<String> requested;
  late List<List<Map<String, dynamic>>> written;

  setUp(() async {
    SharedPreferences.setMockInitialValues(const {});
    store = await SharedPreferences.getInstance();
    prefs = PlanetPrefs(store);
    api = MockPlanetApi();
    requested = [];
    written = [];
  });

  HeavyTableSync build({
    DateTime Function()? now,
    Set<String>? tables,
    Future<void> Function(List<Map<String, dynamic>> docs)? writer,
    Future<void> Function(Duration)? sleep,
  }) => HeavyTableSync(
    api: api,
    prefs: prefs,
    now: now,
    // No test wants the real 1s/2s/4s backoff: it would add ~28 seconds of
    // wall clock across the failure cases, and the delays are pinned as a
    // constant instead.
    sleep: sleep ?? _noSleep,
    writers: {
      for (final table in tables ?? const {'courses_progress', 'submissions'})
        table:
            writer ??
            (docs) async {
              written.add(docs);
            },
    },
  );

  /// Serves a corpus of [total] documents, honouring `limit` and `skip`, and
  /// records every URL asked for. `failAtSkip` makes that one page a 503;
  /// `designAtSkip` puts a `_design` row at the head of that page.
  void stubCorpus(
    String table,
    int total, {
    Set<int> failAtSkip = const {},
    Set<int> designAtSkip = const {},
    Map<int, int> failTimes = const {},
    Map<int, int> throwTimes = const {},
    int failStatus = 503,
  }) {
    final remainingFailures = {...failTimes};
    final remainingThrows = {...throwTimes};
    when(
      () => api.getJsonObject(
        any(that: contains('/$table/_all_docs')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments[0] as String;
      requested.add(url);
      final query = Uri.parse(url).queryParameters;
      final limit = int.parse(query['limit']!);
      final skip = int.parse(query['skip']!);
      if (failAtSkip.contains(skip)) {
        return NetworkError<Map<String, dynamic>>(failStatus, 'refused');
      }
      final failuresLeft = remainingFailures[skip] ?? 0;
      if (failuresLeft > 0) {
        remainingFailures[skip] = failuresLeft - 1;
        return const NetworkError<Map<String, dynamic>>(503, 'upstream down');
      }
      final throwsLeft = remainingThrows[skip] ?? 0;
      if (throwsLeft > 0) {
        remainingThrows[skip] = throwsLeft - 1;
        return NetworkException<Map<String, dynamic>>(
          Exception('connection closed'),
        );
      }
      final end = (skip + limit) > total ? total : skip + limit;
      return NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          if (designAtSkip.contains(skip))
            {
              'id': '_design/x',
              'doc': {'_id': '_design/x'},
            },
          for (var i = skip; i < end; i++)
            if (!designAtSkip.contains(skip) || i > skip)
              {
                'id': 'doc-$i',
                'doc': {'_id': 'doc-$i', '_rev': '1-x'},
              },
        ],
      });
    });
  }

  List<int> skipsRequested() => [
    for (final url in requested)
      int.parse(Uri.parse(url).queryParameters['skip']!),
  ];

  group('page sizes', () {
    test('match the Kotlin table, else arm included', () {
      // `TransactionSyncManager.kt:171-176`.
      expect(HeavyTableSync.pageSizeFor('ratings'), 20);
      expect(HeavyTableSync.pageSizeFor('submissions'), 100);
      expect(HeavyTableSync.pageSizeFor('courses_progress'), 200);
      expect(HeavyTableSync.pageSizeFor('login_activities'), 200);
      expect(HeavyTableSync.pageSizeFor('team_activities'), 200);
      expect(HeavyTableSync.pageSizeFor('news'), 1000);
    });

    test('reach the request, per table', () async {
      stubCorpus('courses_progress', 10);
      await build().walk(table: 'courses_progress', config: config);
      expect(requested.single, contains('limit=200'));

      requested.clear();
      stubCorpus('submissions', 10);
      await build().walk(table: 'submissions', config: config);
      expect(requested.single, contains('limit=100'));
    });
  });

  group('the checkpoint', () {
    test('a resumed walk starts from the saved skip', () async {
      await prefs.setHeavyTableSkip('courses_progress', 600);
      stubCorpus('courses_progress', 700);

      await build().walk(table: 'courses_progress', config: config);

      // Not 0: the walk resumes rather than restarting. Seed the checkpoint
      // reader with a constant 0 and the first skip is 0 and this fails.
      expect(skipsRequested().first, 600);
      expect(written.single, hasLength(100));
    });

    test(
      'an interrupted walk leaves the checkpoint at the committed page',
      () async {
        // 500 documents, page 3 (skip=400) refused: pages 1 and 2 commit.
        stubCorpus('courses_progress', 500, failAtSkip: {400});

        final result = await build().walk(
          table: 'courses_progress',
          config: config,
        );

        expect(result.completedFully, isFalse);
        expect(result.failure, isNotNull);
        expect(prefs.heavyTableSkip('courses_progress'), 400);
        // The pages that did land stayed landed — `syncDb` never rolls back.
        expect(written.map((page) => page.length), [200, 200]);
      },
    );

    test('a completed walk removes the key entirely', () async {
      stubCorpus('courses_progress', 250);

      final result = await build().walk(
        table: 'courses_progress',
        config: config,
      );

      expect(result.completedFully, isTrue);
      expect(prefs.heavyTableSkip('courses_progress'), 0);
      // `remove`, not `setInt(0)`, matching `TransactionSyncManager.kt:324`.
      // Swap the production `clearHeavyTableSkip` for a `setHeavyTableSkip(0)`
      // and only this line fails.
      expect(store.containsKey('heavy_sync_skip_courses_progress'), isFalse);
    });

    test(
      'is written before the first request, so the key exists at 0',
      () async {
        // The Kotlin's pre-request write (`:193-195`) is otherwise redundant —
        // the previous iteration's post-insert write already stored the same
        // value — and its one real effect is materialising the key on the first
        // iteration. Drop that write from the walk and this fails while every
        // other test here still passes.
        stubCorpus('courses_progress', 500, failAtSkip: {0});

        await build().walk(table: 'courses_progress', config: config);

        expect(store.containsKey('heavy_sync_skip_courses_progress'), isTrue);
        expect(prefs.heavyTableSkip('courses_progress'), 0);
      },
    );

    test('advances by the row count, not the document count', () async {
      // The first page carries a `_design` row the writer drops, so 200 rows
      // yield 199 documents. `skip += docs.length` would ask for 199 next and
      // re-fetch a document it already committed; a page that was *entirely*
      // `_design` would never advance at all. `TransactionSyncManager.kt:305`
      // is `skip += arr.size()`.
      stubCorpus('courses_progress', 400, designAtSkip: {0});

      await build().walk(table: 'courses_progress', config: config);

      expect(skipsRequested(), [0, 200, 400]);
      expect(written.first, hasLength(199));
    });

    test('does not page against a stale total, having asked for none', () async {
      // Kotlin issues no `_all_docs?limit=0` count query and bounds nothing by
      // `total_rows`; it pages until the server sends a short page. A count
      // taken at the start of a walk that resumes days later is a bound
      // computed against a table that has since grown.
      stubCorpus('courses_progress', 250);

      await build().walk(table: 'courses_progress', config: config);

      expect(requested.where((url) => url.contains('limit=0')), isEmpty);
      expect(requested, hasLength(2));
      expect(requested.first, startsWith('$dbUrl/courses_progress/_all_docs'));
      // `include_docs=true` was pinned by nothing: the fixture matches on the
      // path alone. Dropping it makes every page yield zero documents while
      // `skip` still advances by `rows.length`, so the walk completes, clears
      // the checkpoint, and has written nothing — silent, and indistinguishable
      // from an empty table.
      expect(requested.first, contains('include_docs=true'));
    });

    test(
      'a writer that throws keeps the checkpoint and stops the walk',
      () async {
        stubCorpus('courses_progress', 500);
        var pages = 0;

        final result = await build(
          writer: (docs) async {
            pages++;
            if (pages == 2) throw StateError('drift is closed');
          },
        ).walk(table: 'courses_progress', config: config);

        expect(result.completedFully, isFalse);
        expect(prefs.heavyTableSkip('courses_progress'), 200);
        expect(store.containsKey('heavy_sync_skip_courses_progress'), isTrue);
      },
    );

    test('an empty page completes the walk', () async {
      // A table whose size is an exact multiple of the page size costs one
      // extra request, which comes back with no rows. Both that and a short
      // page are `syncCompletedFully` in the Kotlin (`:211` and `:319`).
      stubCorpus('courses_progress', 200);

      final result = await build().walk(
        table: 'courses_progress',
        config: config,
      );

      expect(skipsRequested(), [0, 200]);
      expect(result.completedFully, isTrue);
      expect(store.containsKey('heavy_sync_skip_courses_progress'), isFalse);
    });
  });

  group('page retries', () {
    test('a 5xx page is retried, and a later attempt lands', () async {
      // `RetryInterceptor` whitelists `_all_docs` for POST retry and retries
      // three times at 1s/2s/4s on a 5xx or an `IOException`, so Kotlin's
      // `break` is reached only after four attempts. The port's `PlanetApi`
      // has no interceptor, so the walk does it — and with a checkpoint the
      // difference is not cosmetic: one attempt per page would freeze the walk
      // at the first flaky page for ever, every WorkManager retry re-trying
      // the same page once.
      stubCorpus('courses_progress', 300, failTimes: {0: 2});

      final result = await build().walk(
        table: 'courses_progress',
        config: config,
      );

      expect(result.completedFully, isTrue);
      // Three requests at skip=0 (two refused, one served) then the rest.
      expect(skipsRequested(), [0, 0, 0, 200]);
    });

    test('a transport failure is retried', () async {
      stubCorpus('courses_progress', 100, throwTimes: {0: 1});

      final result = await build().walk(
        table: 'courses_progress',
        config: config,
      );

      expect(result.completedFully, isTrue);
      expect(skipsRequested(), [0, 0]);
    });

    test('four failed attempts leave the checkpoint and stop', () async {
      stubCorpus('courses_progress', 500, failAtSkip: {200});

      final result = await build().walk(
        table: 'courses_progress',
        config: config,
      );

      expect(result.completedFully, isFalse);
      expect(prefs.heavyTableSkip('courses_progress'), 200);
      // One attempt plus the three retries, and no more: an unbounded retry
      // here would hold the worker's execution window open against a server
      // that is simply down.
      expect(skipsRequested().where((skip) => skip == 200), hasLength(4));
      expect(HeavyTableSync.pageRetryDelays, hasLength(3));
    });

    test('a 404 is not retried', () async {
      // The interceptor retries an `IOException` or a 5xx and nothing else;
      // Retrofit hands `syncDb` a null body for a 401/404, which is its other
      // `break`. Retrying a missing database would spend the whole window on
      // a request that cannot start succeeding.
      stubCorpus('courses_progress', 500, failAtSkip: {0}, failStatus: 404);

      await build().walk(table: 'courses_progress', config: config);

      expect(skipsRequested(), [0]);
    });
  });

  group('the retry verdict', () {
    test('is derived from the checkpoint, not from the walk result', () async {
      // The walk fails outright — every page refused — but the checkpoint it
      // resumed from is non-zero, so the verdict is retry. Nothing reads the
      // returned `HeavyTableWalkResult`: derive the verdict from
      // `result.completedFully` or `result.failure` instead and this still
      // passes, which is why the converse case below exists.
      await prefs.setHeavyTableSkip('courses_progress', 400);
      stubCorpus('courses_progress', 500, failAtSkip: {400});

      final retryNotRequested = await build().run(
        'courses_progress',
        config: config,
      );

      expect(retryNotRequested, isFalse);
      expect(prefs.heavyTableSkip('courses_progress'), 400);
    });

    test('a walk that fails on its first page reports success', () async {
      // The Kotlin quirk, ported deliberately. `getInt(key, 0) > 0` cannot
      // tell "absent" from "present and 0", and the pre-request write stored
      // 0 — so a table that synced nothing reports success and is not
      // retried. Recovery is the next completed sync's unconditional
      // `scheduleAll`, never `scheduleIfPending`, which shares the blind spot.
      //
      // Derive the verdict from the walk result instead and this test goes
      // red: `completedFully` is false and `failure` is set.
      stubCorpus('courses_progress', 500, failAtSkip: {0});

      final retryNotRequested = await build().run(
        'courses_progress',
        config: config,
      );

      expect(retryNotRequested, isTrue);
      expect(written, isEmpty);
    });

    test('a completed walk reports success', () async {
      stubCorpus('courses_progress', 150);

      expect(await build().run('courses_progress', config: config), isTrue);
      expect(prefs.heavyTableSkip('courses_progress'), 0);
    });

    test('a table with no writer is success, not retry', () async {
      // Kotlin answers `Result.failure()` for a missing `KEY_TABLE`; neither
      // retries, and retrying would loop for ever on a task name persisted by
      // a later build.
      expect(await build().run('team_activities', config: config), isTrue);
      verifyNever(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      );
    });

    test('an unconfigured server is success, not retry', () async {
      expect(await build().run('courses_progress', config: null), isTrue);
      verifyNever(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      );
    });
  });

  group('the interactive-sync guard', () {
    final now = DateTime(2026, 9, 9, 12);

    test('declines to run beside a live interactive sync', () async {
      stubCorpus('courses_progress', 500);
      await prefs.markInteractiveSyncStarted(
        now.subtract(const Duration(minutes: 2)),
      );

      final sync = build(now: () => now);

      expect(sync.isInteractiveSyncActive, isTrue);
      // `Result.retry()`, and nothing walked: the guard is ahead of the walk.
      expect(await sync.run('courses_progress', config: config), isFalse);
      expect(requested, isEmpty);
      expect(store.containsKey('heavy_sync_skip_courses_progress'), isFalse);
    });

    test('disbelieves a flag older than the timeout', () async {
      stubCorpus('courses_progress', 150);
      await prefs.markInteractiveSyncStarted(
        now
            .subtract(HeavyTableSync.interactiveSyncTimeout)
            .subtract(const Duration(minutes: 1)),
      );

      final sync = build(now: () => now);

      // A process killed mid-sync leaves the flag set for ever, and a flag
      // believed for ever means every heavy run returns retry and no table
      // ever syncs — the exact outcome this class exists to end. Remove the
      // decay and this fails.
      expect(sync.isInteractiveSyncActive, isFalse);
      expect(await sync.run('courses_progress', config: config), isTrue);
      expect(requested, isNotEmpty);
    });

    test('a clock corrected backwards still counts as active', () async {
      await prefs.markInteractiveSyncStarted(
        now.add(const Duration(minutes: 5)),
      );

      expect(build(now: () => now).isInteractiveSyncActive, isTrue);
    });

    test('a cleared flag is not active', () async {
      await prefs.markInteractiveSyncStarted(now);
      await prefs.clearInteractiveSyncStarted();

      expect(build(now: () => now).isInteractiveSyncActive, isFalse);
    });
  });

  group('the table set', () {
    test('names the Kotlin five for reference', () {
      expect(HeavyTableSync.kotlinHeavyTables, [
        'ratings',
        'courses_progress',
        'submissions',
        'login_activities',
        'team_activities',
      ]);
    });

    test('walks the two the port schedules, not the Kotlin five', () {
      // Three of the Kotlin five are deliberately absent, each argued at
      // [HeavyTableSync.tables]: `ratings` stays an interactive sync area,
      // `team_activities` has no writer in this port at all, and
      // `submissions` keeps an inline pull whose ordering against the upload
      // sweep a background walk cannot preserve. Pinned so that adding one
      // back is a decision somebody has to defend rather than a one-line
      // drift.
      expect(HeavyTableSync.tables, ['courses_progress', 'login_activities']);
      expect(HeavyTableSync.tables, isNot(contains('ratings')));
      expect(HeavyTableSync.tables, isNot(contains('team_activities')));
      expect(HeavyTableSync.tables, isNot(contains('submissions')));
    });
  });
}
