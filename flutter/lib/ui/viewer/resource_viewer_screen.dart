import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:video_player/video_player.dart';
import 'package:pdfx/pdfx.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../core/files/resource_files.dart';
import '../../core/network/network_result.dart';
import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/activities_provider.dart';
import '../../providers/app_providers.dart';
import '../../repository/resource_downloader.dart';

/// Port of `ui/viewer/ResourceViewerActivity.kt` and `ui/viewer/ResourceViewerFragment.kt`.
///
/// Handles viewing different resource types: video, audio, PDF, image, text, markdown.
class ResourceViewerScreen extends ConsumerStatefulWidget {
  const ResourceViewerScreen({super.key, required this.resourceId});

  final String resourceId;

  @override
  ConsumerState<ResourceViewerScreen> createState() =>
      _ResourceViewerScreenState();
}

class _ResourceViewerScreenState extends ConsumerState<ResourceViewerScreen> {
  MyLibraryRow? _resource;
  bool _loading = true;
  String? _error;
  String? _localPath;
  bool _downloading = false;
  bool _downloadFailed = false;
  double? _progress;

  @override
  void initState() {
    super.initState();
    _loadResource();
  }

  Future<void> _loadResource() async {
    try {
      final resource = await ref
          .read(resourcesRepositoryProvider)
          .getById(widget.resourceId);
      if (!mounted) return;
      _resource = resource;
      // Resolved once here rather than per sub-viewer: each of them used to
      // hit the disk independently on every rebuild.
      final path = await _getLocalFilePath();
      if (!mounted) return;
      // A row flagged offline whose file has since been deleted would otherwise
      // keep claiming to be downloaded.
      if (path == null && resource != null && resource.resourceOffline) {
        await ref
            .read(myLibraryDaoProvider)
            .markNotDownloaded(widget.resourceId);
      }
      if (!mounted) return;
      setState(() {
        _localPath = path;
        _loading = false;
      });
      // Port of `ResourcesRepositoryImpl.trackResourceOpen`, which the Kotlin
      // calls when a resource is opened. Awaited after the frame state is set
      // so a slow write cannot delay the render.
      if (resource != null) {
        await ref.read(activityLogProvider).logResourceOpen(resource);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  ResourceType _getResourceType() {
    final mediaType = _resource?.mediaType?.toLowerCase() ?? '';
    final resourceType = _resource?.resourceType?.toLowerCase() ?? '';
    final filename = _resource?.filename?.toLowerCase() ?? '';

    if (mediaType == 'html' || resourceType == 'html') {
      return ResourceType.html;
    }
    if (mediaType == 'video' || resourceType == 'video') {
      return ResourceType.video;
    }
    if (mediaType == 'audio' || resourceType == 'audio') {
      return ResourceType.audio;
    }
    if (mediaType == 'pdf' ||
        resourceType == 'pdf' ||
        filename.endsWith('.pdf')) {
      return ResourceType.pdf;
    }
    if (mediaType == 'image' ||
        resourceType == 'image' ||
        filename.endsWith('.png') ||
        filename.endsWith('.jpg') ||
        filename.endsWith('.jpeg') ||
        filename.endsWith('.gif') ||
        filename.endsWith('.webp')) {
      return ResourceType.image;
    }
    if (filename.endsWith('.md') || filename.endsWith('.markdown')) {
      return ResourceType.markdown;
    }
    if (filename.endsWith('.csv')) {
      return ResourceType.csv;
    }
    if (mediaType == 'text' || resourceType == 'text' || filename.isNotEmpty) {
      return ResourceType.text;
    }
    return ResourceType.unknown;
  }

  /// Resolved through [ResourceFiles] so the viewer reads exactly where
  /// [ResourceDownloader] writes.
  ///
  /// For HTML resources the entry file may be nested in a subfolder
  /// (`openWhichFile = "sudoku/index.html"`), so [ResourceFiles.resolveHtmlEntryFile]
  /// resolves it against the resource's download directory rather than looking
  /// up a flat filename.
  Future<String?> _getLocalFilePath() async {
    final resource = _resource;
    if (resource == null) return null;
    if (_getResourceType() == ResourceType.html) {
      final dir = await ResourceFiles.directoryFor(
        docId: resource.couchId ?? resource.id,
      );
      final entry = ResourceFiles.resolveHtmlEntryFile(
        dir,
        resource.openWhichFile,
      );
      if (entry == null || !await entry.exists() || await entry.length() <= 0) {
        return null;
      }
      return entry.path;
    }
    final file = await ResourceFiles.existingFileFor(
      docId: resource.couchId ?? resource.id,
      filename: resource.filename ?? '',
    );
    return file?.path;
  }

  /// Reads a text/CSV/markdown attachment's contents through the
  /// [resourceContentReaderProvider] seam. Routing the read through a
  /// provider (rather than each renderer calling `File.readAsString` itself)
  /// is what makes the rendering pipeline testable: a widget test overrides
  /// the provider with a fixed string and pumps without real `dart:io`,
  /// which the test binding's fake clock cannot drive.
  Future<String?> _readTextContent() async {
    final resource = _resource;
    if (resource == null) return null;
    return ref.read(resourceContentReaderProvider)(
      docId: resource.couchId ?? resource.id,
      filename: resource.filename ?? '',
    );
  }

  Future<void> _download() async {
    final resource = _resource;
    final config = ref.read(serverConfigProvider);
    if (resource == null || config == null) return;

    setState(() {
      _downloading = true;
      _progress = null;
      _error = null;
    });

    final result = await ref
        .read(resourceDownloaderProvider)
        .download(
          resource,
          config: config,
          onProgress: (received, total) {
            if (!mounted || total <= 0) return;
            setState(() => _progress = received / total);
          },
        );

    if (!mounted) return;
    if (result case NetworkSuccess<String>(:final data)) {
      setState(() {
        _localPath = data;
        _downloading = false;
      });
      // `BaseContainerFragment` logs the download alongside starting it; the
      // port logs it on success, so a failed fetch is not reported to the
      // server as a download that happened.
      await ref.read(activityLogProvider).logResourceDownload(resource);
      return;
    }
    setState(() {
      _downloading = false;
      _downloadFailed = true;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    if (_loading) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.resources)),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    if (_error != null) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.resources)),
        body: Center(child: Text(l10n.syncFailed(_error!))),
      );
    }

    if (_resource == null) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.resources)),
        body: Center(child: Text(l10n.noDataAvailable)),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(_resource!.title ?? l10n.untitledResourceTitle),
      ),
      // Nothing can render until the attachment is on disk. Previously every
      // viewer was built regardless and each showed its own "not downloaded"
      // message, with no way to actually get the file.
      body: _localPath == null
          ? _buildDownloadPrompt(context)
          : _buildViewer(context, _getResourceType()),
    );
  }

  Widget _buildDownloadPrompt(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final config = ref.watch(serverConfigProvider);
    // No server, or a row that names no attachment, means there is nothing to
    // fetch — offering a button that cannot work would be worse than saying so.
    final canDownload =
        config != null && ResourceDownloader.urlFor(config, _resource!) != null;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              _downloadFailed
                  ? Icons.cloud_off_outlined
                  : Icons.cloud_download_outlined,
              size: 64,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(height: 16),
            Text(
              _downloadFailed
                  ? l10n.downloadFailed
                  : l10n.resourceNotDownloaded,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 24),
            if (_downloading) ...[
              LinearProgressIndicator(value: _progress),
              const SizedBox(height: 8),
              Text(
                _progress == null
                    ? l10n.downloading
                    : '${(_progress! * 100).round()}%',
              ),
            ] else if (canDownload)
              FilledButton.icon(
                onPressed: _download,
                icon: const Icon(Icons.download),
                label: Text(_downloadFailed ? l10n.retry : l10n.download),
              )
            else
              Text(
                l10n.noDataAvailable,
                style: Theme.of(context).textTheme.bodySmall,
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildViewer(BuildContext context, ResourceType type) {
    switch (type) {
      case ResourceType.video:
        return _VideoViewer(
          resource: _resource!,
          getLocalFilePath: _getLocalFilePath,
        );
      case ResourceType.audio:
        return _AudioViewer(
          resource: _resource!,
          getLocalFilePath: _getLocalFilePath,
        );
      case ResourceType.pdf:
        return _PdfViewer(
          resource: _resource!,
          getLocalFilePath: _getLocalFilePath,
        );
      case ResourceType.image:
        return _ImageViewer(
          resource: _resource!,
          getLocalFilePath: _getLocalFilePath,
        );
      case ResourceType.markdown:
        return _MarkdownViewer(
          resource: _resource!,
          getContent: _readTextContent,
        );
      case ResourceType.text:
      case ResourceType.csv:
        return _TextViewer(resource: _resource!, getContent: _readTextContent);
      case ResourceType.html:
        return _HtmlViewer(
          resource: _resource!,
          getLocalFilePath: _getLocalFilePath,
        );
      case ResourceType.unknown:
        return Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.help_outline, size: 64),
              const SizedBox(height: 16),
              Text(AppLocalizations.of(context).noDataAvailable),
            ],
          ),
        );
    }
  }
}

enum ResourceType {
  video,
  audio,
  pdf,
  image,
  text,
  markdown,
  csv,
  html,
  unknown,
}

class _VideoViewer extends StatefulWidget {
  const _VideoViewer({required this.resource, required this.getLocalFilePath});

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  State<_VideoViewer> createState() => _VideoViewerState();
}

class _VideoViewerState extends State<_VideoViewer> {
  VideoPlayerController? _controller;
  bool _loading = true;
  String? _error;
  bool _fileMissing = false;

  @override
  void initState() {
    super.initState();
    _initPlayer();
  }

  Future<void> _initPlayer() async {
    try {
      final path = await widget.getLocalFilePath();
      if (path == null) {
        if (mounted) {
          setState(() {
            _fileMissing = true;
            _loading = false;
          });
        }
        return;
      }

      final controller = VideoPlayerController.file(File(path));
      await controller.initialize();

      if (mounted) {
        setState(() {
          _controller = controller;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_fileMissing) {
      return Center(child: Text(l10n.videoFileNotFound));
    }

    if (_error != null) {
      return Center(child: Text(_error!));
    }

    if (_controller == null || !_controller!.value.isInitialized) {
      return Center(child: Text(l10n.unableToLoadVideo));
    }

    return AspectRatio(
      aspectRatio: _controller!.value.aspectRatio,
      child: Stack(
        alignment: Alignment.bottomCenter,
        children: [
          VideoPlayer(_controller!),
          VideoProgressIndicator(_controller!, allowScrubbing: true),
          PlayPauseOverlay(controller: _controller!),
        ],
      ),
    );
  }
}

class PlayPauseOverlay extends StatefulWidget {
  const PlayPauseOverlay({super.key, required this.controller});

  final VideoPlayerController controller;

  @override
  State<PlayPauseOverlay> createState() => _PlayPauseOverlayState();
}

class _PlayPauseOverlayState extends State<PlayPauseOverlay> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_updateState);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_updateState);
    super.dispose();
  }

  void _updateState() {
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        if (widget.controller.value.isPlaying) {
          widget.controller.pause();
        } else {
          widget.controller.play();
        }
      },
      child: Container(
        color: Colors.transparent,
        child: Center(
          child: Icon(
            widget.controller.value.isPlaying ? Icons.pause : Icons.play_arrow,
            color: Colors.white,
            size: 64,
          ),
        ),
      ),
    );
  }
}

class _AudioViewer extends StatefulWidget {
  const _AudioViewer({required this.resource, required this.getLocalFilePath});

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  State<_AudioViewer> createState() => _AudioViewerState();
}

class _AudioViewerState extends State<_AudioViewer> {
  VideoPlayerController? _controller;
  bool _loading = true;
  String? _error;
  bool _fileMissing = false;

  @override
  void initState() {
    super.initState();
    _initPlayer();
  }

  Future<void> _initPlayer() async {
    try {
      final path = await widget.getLocalFilePath();
      if (path == null) {
        if (mounted) {
          setState(() {
            _fileMissing = true;
            _loading = false;
          });
        }
        return;
      }

      // VideoPlayerController can also play audio
      final controller = VideoPlayerController.file(File(path));
      await controller.initialize();

      if (mounted) {
        setState(() {
          _controller = controller;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_fileMissing) {
      return Center(child: Text(l10n.audioFileNotFound));
    }

    if (_error != null) {
      return Center(child: Text(_error!));
    }

    if (_controller == null || !_controller!.value.isInitialized) {
      return Center(child: Text(l10n.unableToLoadAudio));
    }

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.audiotrack, size: 80),
          const SizedBox(height: 16),
          Text(
            widget.resource.title ?? l10n.untitledResourceTitle,
            style: Theme.of(context).textTheme.titleLarge,
          ),
          if (widget.resource.author != null) ...[
            const SizedBox(height: 8),
            Text(widget.resource.author!),
          ],
          const SizedBox(height: 32),
          // Audio visualization via video player controls (no video shown)
          Expanded(child: VideoPlayer(_controller!)),
          VideoProgressIndicator(_controller!, allowScrubbing: true),
          IconButton(
            iconSize: 64,
            icon: Icon(
              _controller!.value.isPlaying
                  ? Icons.pause_circle
                  : Icons.play_circle,
            ),
            onPressed: () {
              if (_controller!.value.isPlaying) {
                _controller!.pause();
              } else {
                _controller!.play();
              }
              setState(() {});
            },
          ),
        ],
      ),
    );
  }
}

class _PdfViewer extends StatefulWidget {
  const _PdfViewer({required this.resource, required this.getLocalFilePath});

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  State<_PdfViewer> createState() => _PdfViewerState();
}

class _PdfViewerState extends State<_PdfViewer> {
  PdfControllerPinch? _controller;
  bool _loading = true;
  String? _error;
  bool _fileMissing = false;

  @override
  void initState() {
    super.initState();
    _initPdf();
  }

  Future<void> _initPdf() async {
    try {
      final path = await widget.getLocalFilePath();
      if (path == null) {
        if (mounted) {
          setState(() {
            _fileMissing = true;
            _loading = false;
          });
        }
        return;
      }

      _controller = PdfControllerPinch(document: PdfDocument.openFile(path));

      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_fileMissing) {
      return Center(child: Text(l10n.pdfFileNotFound));
    }

    if (_error != null) {
      return Center(child: Text(_error!));
    }

    if (_controller == null) {
      return Center(child: Text(l10n.unableToLoadPdf));
    }

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8.0),
          child: Text(
            widget.resource.title ?? l10n.untitledResourceTitle,
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        Expanded(
          child: PdfViewPinch(
            controller: _controller!,
            builders: PdfViewPinchBuilders<DefaultBuilderOptions>(
              options: const DefaultBuilderOptions(),
              documentLoaderBuilder: (_) =>
                  const Center(child: CircularProgressIndicator()),
              pageLoaderBuilder: (_) =>
                  const Center(child: CircularProgressIndicator()),
              errorBuilder: (_, error) => Center(child: Text(error.toString())),
            ),
          ),
        ),
      ],
    );
  }
}

class _ImageViewer extends StatelessWidget {
  const _ImageViewer({required this.resource, required this.getLocalFilePath});

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return FutureBuilder<String?>(
      future: getLocalFilePath(),
      builder: (context, snapshot) {
        if (!snapshot.hasData || snapshot.data == null) {
          return Center(child: Text(l10n.imageFileNotFound));
        }

        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text(
                resource.title ?? l10n.untitledResourceTitle,
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ),
            Expanded(
              child: PhotoView(
                imageProvider: FileImage(File(snapshot.data!)),
                minScale: PhotoViewComputedScale.contained,
                maxScale: PhotoViewComputedScale.covered * 3,
              ),
            ),
          ],
        );
      },
    );
  }
}

class _TextViewer extends StatefulWidget {
  const _TextViewer({required this.resource, required this.getContent});

  final MyLibraryRow resource;
  final Future<String?> Function() getContent;

  @override
  State<_TextViewer> createState() => _TextViewerState();
}

class _TextViewerState extends State<_TextViewer> {
  String? _content;
  bool _loading = true;
  bool _fileMissing = false;

  @override
  void initState() {
    super.initState();
    _loadContent();
  }

  Future<void> _loadContent() async {
    final content = await widget.getContent();
    if (mounted) {
      setState(() {
        _content = content;
        _fileMissing = content == null;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_fileMissing) {
      return Center(
        child: Text(l10n.fileNotFound(widget.resource.filename ?? '')),
      );
    }

    final isCsv =
        widget.resource.filename?.toLowerCase().endsWith('.csv') ?? false;

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8.0),
          child: Text(
            widget.resource.title ?? l10n.untitledResourceTitle,
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        Expanded(
          child: _content == null
              ? Center(child: Text(l10n.noContent))
              : isCsv
              ? _CsvContent(content: _content!)
              : SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: SelectableText(_content!),
                ),
        ),
      ],
    );
  }
}

class _CsvContent extends StatelessWidget {
  const _CsvContent({required this.content});

  final String content;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final lines = content.split('\n');
    if (lines.isEmpty) return Center(child: Text(l10n.emptyFile));

    final headers = lines.first.split(',').map((h) => h.trim()).toList();

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: SingleChildScrollView(
        child: DataTable(
          columns: headers.map((h) => DataColumn(label: Text(h))).toList(),
          rows: lines.skip(1).where((line) => line.trim().isNotEmpty).map((
            line,
          ) {
            final values = line.split(',').map((v) => v.trim()).toList();
            return DataRow(
              cells: List.generate(
                headers.length,
                (i) => DataCell(Text(i < values.length ? values[i] : '')),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}

class _MarkdownViewer extends StatefulWidget {
  const _MarkdownViewer({required this.resource, required this.getContent});

  final MyLibraryRow resource;
  final Future<String?> Function() getContent;

  @override
  State<_MarkdownViewer> createState() => _MarkdownViewerState();
}

class _MarkdownViewerState extends State<_MarkdownViewer> {
  String? _content;
  bool _loading = true;
  bool _fileMissing = false;

  @override
  void initState() {
    super.initState();
    _loadContent();
  }

  Future<void> _loadContent() async {
    final content = await widget.getContent();
    if (mounted) {
      setState(() {
        _content = content;
        _fileMissing = content == null;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_fileMissing) {
      return Center(
        child: Text(l10n.fileNotFound(widget.resource.filename ?? '')),
      );
    }

    if (_content == null) {
      return Center(child: Text(l10n.noContent));
    }

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8.0),
          child: Text(
            widget.resource.title ?? l10n.untitledResourceTitle,
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        Expanded(child: Markdown(data: _content!, selectable: true)),
      ],
    );
  }
}

/// Port of `ui/viewer/WebViewActivity.kt`.
///
/// Loads the entry file of a downloaded HTML resource bundle in a platform
/// WebView. The entry file is resolved through [ResourceFiles.resolveHtmlEntryFile],
/// which honours `openWhichFile`'s subfolder nesting and refuses to serve a
/// path outside the resource directory.
class _HtmlViewer extends StatefulWidget {
  const _HtmlViewer({required this.resource, required this.getLocalFilePath});

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  State<_HtmlViewer> createState() => _HtmlViewerState();
}

class _HtmlViewerState extends State<_HtmlViewer> {
  late final WebViewController _controller;
  String? _filePath;
  String? _error;
  bool _fileMissing = false;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      // Port of `WebViewActivity`'s `javaScriptEnabled = isLocalResource`.
      // `webview_flutter` defaults to `JavaScriptMode.disabled`, so without this
      // an interactive HTML resource — a lesson with a quiz, anything scripted —
      // renders inert here while working in the Kotlin app.
      //
      // The permission is as narrow as the Kotlin's: this viewer only ever calls
      // `loadFile` on a path under the app's own resource directory, and never
      // `loadRequest`, so scripts run against downloaded Planet content and
      // never against a remote page.
      ..setJavaScriptMode(JavaScriptMode.unrestricted);
    _load();
  }

  Future<void> _load() async {
    final path = await widget.getLocalFilePath();
    if (path == null) {
      if (mounted) {
        setState(() => _fileMissing = true);
      }
      return;
    }
    _filePath = path;
    try {
      await _controller.loadFile(path);
    } on Exception catch (e) {
      if (mounted) {
        setState(() => _error = e.toString());
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (_fileMissing) {
      return Center(child: Text(l10n.htmlEntryNotFound));
    }
    if (_error != null) {
      return Center(child: Text(_error!));
    }
    if (_filePath == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return WebViewWidget(controller: _controller);
  }
}
