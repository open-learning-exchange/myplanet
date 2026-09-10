import '../config/server_config.dart';
import '../network/network_result.dart';
import '../prefs/planet_prefs.dart';
import '../utils/url_utils.dart';
import '../../data/api/planet_api.dart';
import 'sync_result.dart';
import 'table_walk.dart';

/// Writes one page of documents for a heavy table.
///
/// The repositories own the merge; this walk owns only the pagination and the
/// checkpoint, exactly as `TransactionSyncManager.syncDb` dispatches each page
/// to a repository's `insert…FromSync` and keeps the loop to itself.
typedef HeavyTableWriter =
    Future<void> Function(List<Map<String, dynamic>> docs);

/// What one walk did, for logging and tests. **The retry verdict is not read
/// from this** — see [HeavyTableSync.run].
class HeavyTableWalkResult {
  const HeavyTableWalkResult({
    required this.savedCount,
    required this.completedFully,
    this.failure,
  });

  /// Documents handed to the writer, `_design` rows excluded.
  final int savedCount;

  /// True only on the two paths `syncDb` sets `syncCompletedFully` on: an
  /// empty page and a short page. Every other exit leaves the checkpoint
  /// standing.
  final bool completedFully;

  /// Why the walk stopped early, or null.
  final String? failure;
}

/// Port of `services/sync/HeavyTableSyncWorker.kt` together with the
/// checkpoint half of `TransactionSyncManager.syncDb`
/// (`TransactionSyncManager.kt:166-341`).
///
/// **Why this exists.** Five tables are too large to walk inside the
/// interactive sync, and two of them proved it on a real handset against
/// `planet.learning`: `courses_progress` is 114,219 documents (572 pages at
/// this table's size) and aborted at `skip=23600`; `login_activities` is
/// 19,324 and aborted at `skip=10400`. CouchDB answers `skip` in O(n), so each
/// page costs more than the last, and with no checkpoint every retry began
/// again at zero — neither walk could *ever* finish, and the courses area
/// failed with it. The checkpoint is the whole feature: an interrupted walk
/// resumes past the pages it already committed.
///
/// **The verdict is derived from the checkpoint, not from the walk.** Kotlin
/// discards `syncDb`'s return value and re-reads the preference
/// (`HeavyTableSyncWorker.kt:32-34`); this does the same, quirk included — see
/// [run].
class HeavyTableSync {
  HeavyTableSync({
    required PlanetApi api,
    required PlanetPrefs prefs,
    required Map<String, HeavyTableWriter> writers,
    DateTime Function()? now,
    Future<void> Function(Duration)? sleep,
  }) : _api = api,
       _prefs = prefs,
       _writers = writers,
       _now = now ?? DateTime.now,
       _sleep = sleep ?? Future<void>.delayed;

  final PlanetApi _api;
  final PlanetPrefs _prefs;
  final Map<String, HeavyTableWriter> _writers;

  /// The tables this instance can actually walk.
  ///
  /// Exposed so a test can ask the question that green tests structurally miss:
  /// does every table [tables] schedules have a writer wired to it? A
  /// scheduled table with no writer is a job that runs, finds nothing to do
  /// and reports success for ever — the reachability shape this project keeps
  /// finding.
  Iterable<String> get writableTables => _writers.keys;

  /// The writers themselves, so a test can drive the *real* ones against a
  /// real database. Knowing the keys is not enough: an audit replaced both
  /// bodies with no-ops and every test still passed.
  Map<String, HeavyTableWriter> get writers => Map.unmodifiable(_writers);
  final DateTime Function() _now;
  final Future<void> Function(Duration) _sleep;

  /// `HeavyTableSyncWorker.ALL_HEAVY_TABLES`, verbatim, as the reference for
  /// what the Kotlin keeps out of its interactive sync. It is **not** the set
  /// the port schedules — see [tables].
  static const List<String> kotlinHeavyTables = [
    'ratings',
    'courses_progress',
    'submissions',
    'login_activities',
    'team_activities',
  ];

  /// The tables the port walks in the background, and the three the Kotlin
  /// list has that this one does not:
  ///
  /// * **`ratings` stays an interactive sync area.** It is 116 documents on
  ///   planet.learning — 6 pages — so the checkpoint buys it nothing, while
  ///   the sync centre's row buys the user an error message when the walk
  ///   fails. Scheduling it here *as well* would walk it twice every sync,
  ///   and moving it here would mean deleting a `DashboardSyncArea`, which
  ///   reaches into the sync-centre UI and its labels. If a server ever
  ///   carries a large `ratings` table the fix is one line — add it below and
  ///   drop the area — but that is a change with a reason, not a guess.
  /// * **`team_activities` has no writer in the port at all.** Kotlin pulls it
  ///   (`teamsSyncRepository.bulkInsertTeamActivitiesFromSync`,
  ///   `TransactionSyncManager.kt:251-253`); the port only ever *uploads*
  ///   team-visit rows. Adding the table here without a writer would schedule
  ///   a job that does nothing, so the gap is reported rather than papered
  ///   over: see the phase notes. The consequence is the same shape as the
  ///   `ratings` gap this port already fixed — `TeamLogDao.teamVisitsForUsers`
  ///   and `lastTeamVisit` feed the member-detail screen's visit count and
  ///   last-visit row *and* the team leaderboard's ranking
  ///   (`team_leaderboard_screen.dart:101`), so all three show only what
  ///   *this* handset observed. A leaderboard is a comparison between members
  ///   by construction, which makes it the sharpest case.
  /// * **`submissions` keeps its inline pull**, and this one was moved here
  ///   and moved back. It is 1,975 documents — 20 pages — and it is not one
  ///   of the two tables that aborted at depth, so the checkpoint solves a
  ///   problem it does not have. What moving it *costs* is an ordering the
  ///   port depends on and Kotlin does not: `SubmissionsRepository
  ///   .upsertDocuments` writes `isUpdated: false` over every row it writes,
  ///   keyed on the server `_id`, so a sheet that arrived from the server and
  ///   was then edited locally (survey resume's `markComplete`) loses the
  ///   flag that makes it upload. The headless path closes that window by
  ///   running the submissions *sweep* before the submissions *pull* in the
  ///   same invocation — see `sweepPendingSubmissions` and
  ///   `test/providers/pending_submissions_sweep_test.dart`, which is the test
  ///   that caught this — and a background walk that can run at any time
  ///   cannot be ordered against it at all. Kotlin has the same exposure
  ///   (`upsertRoomSubmissionsFromSync` hard-writes `isUpdated = false` and
  ///   its pull is in the worker), so this is the port keeping a mitigation
  ///   Kotlin never had rather than inventing one. **Make the merge preserve a
  ///   pending local edit and this table can move here**; until then the
  ///   parity gain is not worth the loss.
  static const List<String> tables = ['courses_progress', 'login_activities'];

  /// How long a persisted interactive-sync flag is believed.
  ///
  /// Kotlin needs no such bound: `SyncManager.isSyncing` is an
  /// `AtomicBoolean` that dies with the process, so a killed app cannot leave
  /// it set. A preference can, and a permanently-set flag would make every
  /// heavy run return retry for ever — the same "never finishes" outcome this
  /// class exists to end. Bounded generously, because the cost of believing a
  /// stale flag is a deferred walk while the cost of ignoring a live one is
  /// two isolates walking the same table.
  static const Duration interactiveSyncTimeout = Duration(minutes: 30);

  /// Backoff between attempts at one page: three retries at 1s, 2s and 4s.
  ///
  /// **This is a port, not an invention, and it is not from `syncDb`.** The
  /// Kotlin walk has no retry of its own because it does not need one — its
  /// page request is a POST to `/_all_docs`, which `RetryInterceptor`
  /// whitelists for retry (`RetryInterceptor.kt:24,29`) and retries three
  /// times at 1s/2s/4s on any `IOException` (a socket timeout included) or any
  /// 5xx (`:46-52`), wired into every call by `NetworkModule.kt:86`. So
  /// `syncDb`'s `break` on a failed page is reached only after four attempts.
  /// The port's `PlanetApi` has no interceptor at all, so a bare `break` here
  /// would give one attempt where the Kotlin gives four — and with a
  /// checkpoint that is worse than it sounds: the walk would commit everything
  /// up to the first flaky page and then freeze there, every WorkManager retry
  /// re-attempting the same page once. That is the "could never finish"
  /// outcome this class exists to end, relocated from `skip=0` to `skip=N`.
  ///
  /// One divergence remains and is **not** fixable from here:
  /// `PlanetApi._request` pins `receiveTimeout` to 15 seconds per request
  /// where `NetworkModule` gives OkHttp 60 (`NetworkModule.kt:51`), and the
  /// value is hard-coded rather than a parameter. A page at depth that needs
  /// more than 15 seconds to start answering fails all four attempts.
  static const List<Duration> pageRetryDelays = [
    Duration(seconds: 1),
    Duration(seconds: 2),
    Duration(seconds: 4),
  ];

  /// Page size per table, from `TransactionSyncManager.kt:171-176`. Fixed, not
  /// adaptive: the Kotlin heavy walk has no `AdaptiveBatchProcessor` in it, and
  /// a size that grows on a fast page is the opposite of what a table that
  /// aborts at depth needs.
  static int pageSizeFor(String table) => switch (table) {
    'ratings' => 20,
    'submissions' => 100,
    'courses_progress' || 'login_activities' || 'team_activities' => 200,
    _ => 1000,
  };

  /// Stands in for `syncManager.isMainSyncActive()`
  /// (`HeavyTableSyncWorker.kt:31` → `SyncManager.kt:118`).
  ///
  /// **The port cannot have the Kotlin's version.** Android WorkManager runs a
  /// Kotlin worker in the app process, so the worker reads the very
  /// `AtomicBoolean` the UI sets. A `workmanager` task runs in a *separate
  /// Flutter isolate* with its own Riverpod graph, where `DashboardSyncState`
  /// is invisible — so the flag is persisted instead, written by
  /// `DashboardSyncNotifier.syncAll` and read here after a
  /// [PlanetPrefs.reload].
  ///
  /// Two properties of the persisted version differ, both documented rather
  /// than hidden. It is checked once at the start of a run, as the Kotlin's is
  /// (`doWork` tests it and never re-tests), so a sync the user starts *during*
  /// a walk still overlaps it — identical to the Kotlin. And it decays, for
  /// the reason [interactiveSyncTimeout] gives.
  bool get isInteractiveSyncActive {
    final startedAt = _prefs.interactiveSyncStartedAt;
    if (startedAt == null) return false;
    // A negative elapsed time is a clock corrected *backwards* under a running
    // sync, so it counts as active; only a flag older than the timeout is
    // disbelieved.
    return _now().difference(startedAt) < interactiveSyncTimeout;
  }

  /// One page, with the attempts `RetryInterceptor` would have given it.
  ///
  /// Retried on the two classes the Kotlin interceptor retries: a transport
  /// failure or timeout (its `IOException`, the port's [NetworkException]) and
  /// a 5xx. Anything else — 401, 403, 404, a malformed body — is returned
  /// immediately, because the interceptor does not retry those either and
  /// Retrofit hands `syncDb` a null body for them, which is its other `break`
  /// condition.
  Future<NetworkResult<Map<String, dynamic>>> _fetchPage(
    String url,
    String authHeader,
  ) async {
    var result = await _api.getJsonObject(url, authHeader: authHeader);
    for (final delay in pageRetryDelays) {
      if (!_worthRetrying(result)) return result;
      await _sleep(delay);
      result = await _api.getJsonObject(url, authHeader: authHeader);
    }
    return result;
  }

  static bool _worthRetrying(NetworkResult<Map<String, dynamic>> result) =>
      switch (result) {
        NetworkSuccess() => false,
        NetworkException() => true,
        // A null code is a response whose status Dio did not report; treated
        // as not-worth-retrying, matching the interceptor, which retries on a
        // status it can read being 5xx and otherwise passes the response on.
        NetworkError(:final code) => code != null && code >= 500,
      };

  /// Port of `HeavyTableSyncWorker.doWork`. Returns WorkManager's success
  /// flag: `false` reschedules per the registered backoff policy, which is
  /// what `Result.retry()` means.
  ///
  /// ```kotlin
  /// val table = inputData.getString(KEY_TABLE) ?: return Result.failure()
  /// if (syncManager.isMainSyncActive()) return Result.retry()
  /// transactionSyncManager.syncDb(table, useCheckpoint = true)
  /// val interrupted = sharedPrefManager.rawPreferences
  ///     .getInt("heavy_sync_skip_$table", 0) > 0
  /// return if (interrupted) Result.retry() else Result.success()
  /// ```
  ///
  /// Three things about that, each deliberate:
  ///
  /// * **The verdict ignores the walk's outcome entirely.** `syncDb` returns a
  ///   document count that `doWork` discards, and the retry decision is a
  ///   fresh read of the checkpoint. So a walk that failed at `skip=4000`
  ///   retries because the checkpoint says 4000, not because it reported a
  ///   failure.
  /// * **A walk that fails on its *first* page reports success**, because the
  ///   checkpoint written before that request was `0` and `getInt(key, 0) > 0`
  ///   is false. The table is then re-scheduled by the next full sync rather
  ///   than by this worker's backoff. The quirk is ported rather than
  ///   corrected: "retry" here would mean retrying a table whose server is
  ///   simply unreachable, on a linear 30-second backoff, having done nothing.
  /// * **A table this build cannot walk is `true`, not `false`.** Retrying
  ///   would loop for ever on a task name a later build persisted — the
  ///   reason `BackgroundTaskRunner.run` gives for the same choice. Kotlin
  ///   reaches this state differently and agrees on the verdict: a missing
  ///   `KEY_TABLE` is its `Result.failure()`, while a table name with no
  ///   handler takes `syncDb`'s `else -> Log.e("Unknown table")` arm
  ///   (`TransactionSyncManager.kt:279`), walks every page of it, clears the
  ///   checkpoint and returns success. Neither retries.
  Future<bool> run(String table, {required ServerConfig? config}) async {
    if (!_writers.containsKey(table)) return true;
    if (config == null) return true;
    if (isInteractiveSyncActive) return false;
    await walk(table: table, config: config);
    final interrupted = _prefs.heavyTableSkip(table) > 0;
    return !interrupted;
  }

  /// Port of `syncDb(table, useCheckpoint = true)`: pages `_all_docs` from the
  /// saved offset, writing the checkpoint **before** each request and **after**
  /// each committed page, and removing it only on a fully completed walk.
  ///
  /// The two writes are both in the Kotlin (`:193-195` and `:308-310`) and
  /// they mean different things. The one before the request records where a
  /// walk killed mid-page must resume — the page is not committed, so it must
  /// be re-fetched. The one after the insert records that the page *is*
  /// committed, so a resume skips past it rather than re-processing it.
  ///
  /// **Only the first is pinned by a test, and cannot be otherwise.** In one
  /// process the post-insert write is redundant: the next iteration's
  /// pre-request write stores the identical value, so deleting it changes no
  /// observable state — an audit deleted it and the whole suite stayed green.
  /// The two diverge only across a process killed between the page's database
  /// commit and the next request, which no unit test can reach. Said here
  /// rather than left implied, because a claim a test does not pin should not
  /// look like one it does.
  ///
  /// Unlike every other walk in this port there is **no `total_rows` count
  /// query and no `skip < total` bound**: the Kotlin heavy walk has neither,
  /// stopping on a short or empty page instead. Matching it matters here —
  /// a count taken at the start of a walk that resumes days later is a bound
  /// computed against a table that has since grown.
  ///
  /// No pruning, for any of these tables, and never add one: the Kotlin issues
  /// no `deleteNotIn` for them, and all three hold locally-authored rows that
  /// appear in no server keep set until they have been uploaded *and* pulled
  /// back.
  Future<HeavyTableWalkResult> walk({
    required String table,
    required ServerConfig config,
  }) async {
    final writer = _writers[table];
    if (writer == null) {
      return const HeavyTableWalkResult(
        savedCount: 0,
        completedFully: false,
        failure: 'no writer',
      );
    }

    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);
    final pageSize = pageSizeFor(table);

    var skip = _prefs.heavyTableSkip(table);
    var saved = 0;
    var completedFully = false;
    String? failure;

    try {
      while (true) {
        // Before the request, so a walk the OS kills mid-page resumes at this
        // page rather than past it. Awaited, which is what makes it durable
        // before the request that may not come back.
        await _prefs.setHeavyTableSkip(table, skip);

        final pageResult = await _fetchPage(
          '$dbUrl/$table/_all_docs?include_docs=true'
          '&limit=$pageSize&skip=$skip',
          authHeader,
        );
        if (pageResult is! NetworkSuccess<Map<String, dynamic>>) {
          // `break`, not `return`: the checkpoint stays where it is and the
          // caller's re-read turns it into the retry. Kotlin's `break` on
          // `!response.isSuccessful` (`:205-208`) leaves
          // `syncCompletedFully` false for exactly this reason — and, note,
          // is reached only after `RetryInterceptor` has spent its own three
          // attempts, which is why [pageRetryDelays] exists above.
          failure = describeNetworkFailure(pageResult);
          break;
        }

        final rows = pageResult.data['rows'];
        if (rows is! List || rows.isEmpty) {
          // Kotlin's `getJsonArray("rows", body)` yields an empty array for a
          // body without the key, and an empty array is its
          // `syncCompletedFully = true` exit (`:210-213`). A malformed 200 is
          // therefore treated as a finished walk in both apps; the cost is a
          // cleared checkpoint and a walk that starts over, not lost rows.
          completedFully = true;
          break;
        }

        final docs = extractDocs(rows);
        if (docs.isNotEmpty) await writer(docs);
        saved += docs.length;

        // `rows.length`, not `docs.length`: the offset counts what the server
        // returned, and a `_design` row the writer skipped still occupied a
        // slot. Advancing by the filtered count would re-fetch it for ever.
        skip += rows.length;
        await _prefs.setHeavyTableSkip(table, skip);

        if (rows.length < pageSize) {
          completedFully = true;
          break;
        }
      }
    } catch (error) {
      // `syncDb` catches `Exception`, logs, and returns 0 with the checkpoint
      // left standing (`:335-339`); a `CancellationException` is rethrown but
      // has the same effect on the preference. Either way the walk is
      // resumable, which is the only property that matters here.
      failure = 'heavy sync for $table stopped early';
    }

    if (completedFully) {
      // `remove`, not `setInt(0)`, matching `:323-325`. The two read alike
      // through `heavyTableSkip`, and keeping the Kotlin's shape means a
      // reader comparing the trees finds the same key absent.
      //
      // Guarded because this is the one statement that used to sit outside
      // the walk's own `try`: a throwing `remove` propagated to
      // `executeBackgroundTask`'s `catch (_) { return false; }`, and `false`
      // on a one-off with linear backoff and no attempt limit is an unbounded
      // retry of a walk that had just finished.
      try {
        await _prefs.clearHeavyTableSkip(table);
      } catch (_) {
        // The checkpoint stays at the last page instead, so the next run
        // re-walks the tail. `syncDb`'s `apply()` has the same shape: it can
        // lose the removal to a process kill and re-walk too.
      }
    }

    return HeavyTableWalkResult(
      savedCount: saved,
      completedFully: completedFully,
      failure: failure,
    );
  }
}
