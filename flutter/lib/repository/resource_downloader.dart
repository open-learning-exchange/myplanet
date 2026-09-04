import '../core/config/server_config.dart';
import '../core/background/background_download_queue.dart';
import '../core/files/resource_files.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';

/// Fetches a resource's attachment and puts it where the viewer looks.
///
/// Port of the download half of `services/DownloadService.kt` and
/// `ResourceDownloadCoordinator`. Without it the port synced resource
/// *metadata* only: the list rendered, the viewer opened, and every resource
/// reported itself as not downloaded because nothing had ever written a file.
///
/// The user-initiated request starts in the foreground, but is first persisted
/// through [BackgroundDownloadQueue]. If the process disappears or the first
/// attempt fails, WorkManager can finish the same request in a headless isolate.
class ResourceDownloader {
  ResourceDownloader(this._api, this._dao, {BackgroundDownloadQueue? queue})
    : _queue = queue;

  final PlanetApi _api;
  final MyLibraryDao _dao;
  final BackgroundDownloadQueue? _queue;

  /// The attachment URL, or null when the row cannot name a file.
  static String? urlFor(ServerConfig config, MyLibraryRow resource) =>
      UrlUtils.resourceUrl(config, resource.couchId, resource.filename);

  Future<NetworkResult<String>> download(
    MyLibraryRow resource, {
    required ServerConfig config,
    void Function(int received, int total)? onProgress,
    bool persistInBackground = true,
  }) async {
    if (persistInBackground) await _queue?.enqueue(resource.id);
    final url = urlFor(config, resource);
    if (url == null) {
      return const NetworkError<String>(null, 'Resource has no attachment');
    }

    final result = await _api.getBytes(
      url,
      authHeader: UrlUtils.authHeader(config),
      onProgress: onProgress,
    );
    if (result is! NetworkSuccess<List<int>>) {
      return switch (result) {
        NetworkError<List<int>>(:final code, :final message) =>
          NetworkError<String>(code, message),
        NetworkException<List<int>>(:final error) => NetworkException<String>(
          error,
        ),
        _ => const NetworkError<String>(null, 'Download failed'),
      };
    }

    // An empty body is a failure that looks like a success: the file would be
    // created, `exists()` would pass, and the viewer would render nothing.
    if (result.data.isEmpty) {
      return const NetworkError<String>(null, 'Resource body was empty');
    }

    final file = await ResourceFiles.fileFor(
      docId: resource.couchId ?? resource.id,
      filename: resource.filename!,
    );
    await file.parent.create(recursive: true);
    await file.writeAsBytes(result.data, flush: true);

    await _dao.markDownloaded(resource.id, file.path, resource.rev);
    await _queue?.complete(resource.id);
    return NetworkSuccess<String>(file.path);
  }
}
