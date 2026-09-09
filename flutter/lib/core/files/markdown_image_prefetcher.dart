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
/// ingest it (`CoursesRepositoryImpl:670`/`:684`, `VoicesRepositoryImpl:394`,
/// `TeamsRepositoryImpl:1183`) and the collected paths are handed to
/// `DownloadService`, which writes each under `<externalFilesDir>/ole/`. The
/// port had neither half: `CourseMarkdownBody` fetched a relative path as
/// authenticated bytes at render time, which works online and not offline —
/// in an offline-first app.
///
/// **Two deliberate deviations from the Kotlin**, both about *what is
/// remembered*:
///
/// * Kotlin accumulates every link it has ever seen into a `SharedPreferences`
///   JSON list (`MyCourse.saveConcatenatedLinksToPrefs`,
///   `VoicesRepositoryImpl.saveConcatenatedLinksToPrefs`) and never removes an
///   entry, so the list grows for the life of the install and every sync
///   re-queues the whole history. The port keeps no list: the sync walk that
///   collects a link is a full `_all_docs` walk that re-reads every document
///   every time, so **the documents are the durable store** and a link whose
///   download failed is retried by the next sync at no storage cost.
/// * Kotlin hands the queue to a foreground service and returns; this awaits
///   its downloads inside the sync. Bounded by [prefetch] skipping anything
///   already on disk, so the steady-state cost is one `exists()` per link.
///
/// Failures are swallowed on purpose. A missing image must not fail a sync
/// that has already written the documents, and the renderer still falls back
/// to the authenticated network fetch when it is online.
class MarkdownImagePrefetcher {
  const MarkdownImagePrefetcher(this._api);

  final PlanetApi _api;

  /// Materialises each of [links] that is not already on disk.
  ///
  /// Returns the number newly written, which is what the tests assert on;
  /// callers ignore it.
  Future<int> prefetch(
    Iterable<String> links, {
    required ServerConfig config,
  }) async {
    var written = 0;
    // Sequential rather than concurrent: these run behind a sync that has just
    // finished paging the same server, and the Kotlin's download service is
    // serial too.
    for (final link in links) {
      if (await _fetch(link, config)) written++;
    }
    return written;
  }

  Future<bool> _fetch(String link, ServerConfig config) async {
    final file = await MarkdownImageFiles.fileFor(link);
    // Null means the link names nothing local — an absolute URL, or a path
    // that cannot be trusted on the filesystem. Not an error; the renderer
    // resolves those itself.
    if (file == null) return false;
    if (await MarkdownImageFiles.existingFileFor(link) != null) return false;

    // The *download* URL keeps the `resources/` prefix that the on-disk path
    // drops — the same asymmetry the Kotlin has, where the queued URL is
    // `"$baseUrl/$link"` from the unstripped link (`CoursesRepositoryImpl:671`)
    // while the render base strips it. It does *not* keep a markdown title or
    // pointy brackets, which the regex collector captures and the renderer's
    // CommonMark parser does not — see [markdownImageDestination].
    final result = await _api.getBytes(
      '${UrlUtils.dbUrl(config)}/${markdownImageDestination(link)}',
      authHeader: UrlUtils.authHeader(config),
    );
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
