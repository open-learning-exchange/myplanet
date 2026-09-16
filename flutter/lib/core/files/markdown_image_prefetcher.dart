import '../../data/api/planet_api.dart';
import '../config/server_config.dart';
import '../network/network_result.dart';
import '../utils/markdown_links.dart';
import '../utils/url_utils.dart';
import 'markdown_image_files.dart';

/// Downloads the images a synced markdown description references, so they
/// render with no network.
///
/// Port of the download half of Kotlin's concatenated-links path: three
/// repositories call `DownloadUtils.extractLinks` on a description as they
/// ingest it (`CoursesRepositoryImpl:670`/`:684`, `VoicesRepositoryImpl:393`,
/// `TeamsRepositoryImpl:1187`) and the collected paths are handed to
/// `DownloadService`, which writes each under `<externalFilesDir>/ole/`. The
/// port had neither half: `CourseMarkdownBody` fetched a relative path as
/// authenticated bytes at render time, which works online and not offline —
/// in an offline-first app.
///
/// **Two deliberate deviations from the Kotlin**, both about *what is
/// remembered*:
///
/// * Kotlin flushes every link it has seen into a `SharedPreferences` JSON list
///   (`MyCourse.saveConcatenatedLinksToPrefs`,
///   `VoicesRepositoryImpl.saveConcatenatedLinksToPrefs`). The **pref** is
///   cleared at the start of each sync (`SyncManager.kt:93`); what grows is the
///   two collectors that repopulate it — `MyCourse.concatenatedLinks`
///   (`MyCourse.kt:77`) and `VoicesRepositoryImpl.concatenatedLinks` (`:33`),
///   neither ever cleared and both process-lifetime, so a sync re-queues links
///   from documents the server no longer has. The port keeps no list: the sync walk that
///   collects a link is a full `_all_docs` walk that re-reads every document
///   every time, so **the documents are the durable store** and a link whose
///   download failed is retried by the next sync at no storage cost.
/// * Kotlin hands the queue to a foreground service and returns; this awaits
///   its downloads inside the sync. Bounded by [prefetch] skipping anything
///   already on disk, so the steady-state cost is one `exists()` per link.
///
/// Failures are swallowed on purpose, and that is enforced rather than
/// implied: every link runs inside its own `try`, because this sits at the end
/// of `CoursesRepository.sync` and anything that escapes it fails a sync whose
/// documents are already written. In the background isolate such an escape is
/// worse than a lost image — `recordLastSync` is skipped, `run()` returns
/// false, and WorkManager re-runs the whole walk, so **one bad link would stop
/// auto-sync for as long as its document existed on the server**. The
/// filesystem, `path_provider`'s platform channel, a percent-escape that is
/// not valid UTF-8, and the transport can all throw here; none of them is a
/// sync failure. The renderer still falls back to the authenticated network
/// fetch when it is online.
///
/// Two budgets bound the work, because neither existed before this ran inside
/// a sync. [perLinkTimeout] covers `PlanetApi.getBytes` deliberately setting
/// `receiveTimeout: null` — right for a large user-initiated download, and an
/// unbounded stall inside a sync, where `SyncNotifier` refuses a second
/// attempt while one is running and the button never re-enables. [budget]
/// covers a first sync on a fresh install, which is not the steady state the
/// skip-if-present check makes cheap, and which WorkManager will kill at
/// around ten minutes. Kotlin needs neither: it hands the queue to a
/// foreground service precisely so it can outlive that budget.
class MarkdownImagePrefetcher {
  const MarkdownImagePrefetcher(
    this._api, {
    this.perLinkTimeout = const Duration(seconds: 30),
    this.budget = const Duration(minutes: 3),
  });

  final PlanetApi _api;

  /// How long one image may take before it is abandoned for this sync.
  final Duration perLinkTimeout;

  /// How long the whole prefetch may take before the remaining links are left
  /// to the next sync. Nothing is lost: the walk re-collects them.
  final Duration budget;

  /// Materialises each of [links] that is not already on disk.
  ///
  /// Returns the number newly written, which is what the tests assert on;
  /// callers ignore it.
  Future<int> prefetch(
    Iterable<String> links, {
    required ServerConfig config,
  }) async {
    var written = 0;
    final deadline = DateTime.now().add(budget);
    // Sequential rather than concurrent: these run behind a sync that has just
    // finished paging the same server, and the Kotlin's download service is
    // serial too.
    for (final link in links) {
      if (!DateTime.now().isBefore(deadline)) break;
      try {
        if (await _fetch(link, config)) written++;
      } catch (_) {
        // Deliberately unconditional. Anything at all that this link throws is
        // one missing image, never a failed sync — see the class doc.
      }
    }
    return written;
  }

  Future<bool> _fetch(String link, ServerConfig config) async {
    final file = await MarkdownImageFiles.fileFor(link);
    // Null means the link names nothing local — an absolute URL, or a path
    // that cannot be trusted on the filesystem. Not an error; the renderer
    // resolves those itself.
    if (file == null) return false;
    if (await MarkdownImageFiles.hasBytes(file)) return false;

    // The *download* URL keeps the `resources/` prefix that the on-disk path
    // drops — the same asymmetry the Kotlin has, where the queued URL is
    // `"$baseUrl/$link"` from the unstripped link (`CoursesRepositoryImpl:671`)
    // while the render base strips it. It does *not* keep a markdown title or
    // pointy brackets, which the regex collector captures and the renderer's
    // CommonMark parser does not — see [markdownImageDestination].
    final result = await _api
        .getBytes(
          '${UrlUtils.dbUrl(config)}/${markdownImageDestination(link)}',
          authHeader: UrlUtils.authHeader(config),
        )
        .timeout(perLinkTimeout);
    if (result is! NetworkSuccess<List<int>>) return false;
    // An empty body is a failure that looks like a success: the file would be
    // created, `existingFileFor` would still reject it as zero-length, and
    // every later sync would re-download it. Same guard as `ResourceDownloader`.
    if (result.data.isEmpty) return false;

    try {
      await file.parent.create(recursive: true);
      await file.writeAsBytes(result.data, flush: true);
    } catch (_) {
      // A full disk or an unwritable path is not a sync failure.
      return false;
    }
    return true;
  }
}
