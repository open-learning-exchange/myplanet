import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:video_player/video_player.dart';
import 'package:pdfx/pdfx.dart';
import 'package:photo_view/photo_view.dart';
import 'package:path_provider/path_provider.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/resources_providers.dart';

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
      if (mounted) {
        setState(() {
          _resource = resource;
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

  ResourceType _getResourceType() {
    final mediaType = _resource?.mediaType?.toLowerCase() ?? '';
    final resourceType = _resource?.resourceType?.toLowerCase() ?? '';
    final filename = _resource?.filename?.toLowerCase() ?? '';

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

  Future<String?> _getLocalFilePath() async {
    if (_resource == null) return null;
    final filename = _resource!.filename;
    if (filename == null || filename.isEmpty) return null;

    final dir = await getApplicationDocumentsDirectory();
    final file = File('${dir.path}/ole/$filename');
    if (await file.exists()) {
      return file.path;
    }
    return null;
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
        body: Center(child: Text('${l10n.syncFailed(_error!)}')),
      );
    }

    if (_resource == null) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.resources)),
        body: Center(child: Text(l10n.noDataAvailable)),
      );
    }

    final type = _getResourceType();

    return Scaffold(
      appBar: AppBar(
        title: Text(_resource!.title ?? l10n.untitledResource),
      ),
      body: _buildViewer(context, type),
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
          getLocalFilePath: _getLocalFilePath,
        );
      case ResourceType.text:
      case ResourceType.csv:
        return _TextViewer(
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
  unknown,
}

class _VideoViewer extends StatefulWidget {
  const _VideoViewer({
    required this.resource,
    required this.getLocalFilePath,
  });

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  State<_VideoViewer> createState() => _VideoViewerState();
}

class _VideoViewerState extends State<_VideoViewer> {
  VideoPlayerController? _controller;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initPlayer();
  }

  Future<void> _initPlayer() async {
    try {
      final path = await widget.getLocalFilePath();
      if (path == null) {
        setState(() {
          _error = 'Video file not found locally';
          _loading = false;
        });
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
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(child: Text(_error!));
    }

    if (_controller == null || !_controller!.value.isInitialized) {
      return const Center(child: Text('Unable to load video'));
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
  const _AudioViewer({
    required this.resource,
    required this.getLocalFilePath,
  });

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  State<_AudioViewer> createState() => _AudioViewerState();
}

class _AudioViewerState extends State<_AudioViewer> {
  VideoPlayerController? _controller;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initPlayer();
  }

  Future<void> _initPlayer() async {
    try {
      final path = await widget.getLocalFilePath();
      if (path == null) {
        setState(() {
          _error = 'Audio file not found locally';
          _loading = false;
        });
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
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(child: Text(_error!));
    }

    if (_controller == null || !_controller!.value.isInitialized) {
      return const Center(child: Text('Unable to load audio'));
    }

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.audiotrack, size: 80),
          const SizedBox(height: 16),
          Text(
            widget.resource.title ?? 'Untitled',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          if (widget.resource.author != null) ...[
            const SizedBox(height: 8),
            Text(widget.resource.author!),
          ],
          const SizedBox(height: 32),
          // Audio visualization via video player controls (no video shown)
          Expanded(
            child: VideoPlayer(_controller!),
          ),
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
  const _PdfViewer({
    required this.resource,
    required this.getLocalFilePath,
  });

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  State<_PdfViewer> createState() => _PdfViewerState();
}

class _PdfViewerState extends State<_PdfViewer> {
  PdfControllerPinch? _controller;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initPdf();
  }

  Future<void> _initPdf() async {
    try {
      final path = await widget.getLocalFilePath();
      if (path == null) {
        setState(() {
          _error = 'PDF file not found locally';
          _loading = false;
        });
        return;
      }

      _controller = PdfControllerPinch(
        document: PdfDocument.openFile(path),
      );

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
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(child: Text(_error!));
    }

    if (_controller == null) {
      return const Center(child: Text('Unable to load PDF'));
    }

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8.0),
          child: Text(
            widget.resource.title ?? 'Untitled',
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
  const _ImageViewer({
    required this.resource,
    required this.getLocalFilePath,
  });

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<String?>(
      future: getLocalFilePath(),
      builder: (context, snapshot) {
        if (!snapshot.hasData || snapshot.data == null) {
          return const Center(child: Text('Image file not found'));
        }

        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text(
                resource.title ?? 'Untitled',
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
  const _TextViewer({
    required this.resource,
    required this.getLocalFilePath,
  });

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  State<_TextViewer> createState() => _TextViewerState();
}

class _TextViewerState extends State<_TextViewer> {
  String? _content;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadContent();
  }

  Future<void> _loadContent() async {
    try {
      final path = await widget.getLocalFilePath();
      if (path == null) {
        setState(() {
          _error = 'File not found locally';
          _loading = false;
        });
        return;
      }

      final file = File(path);
      final content = await file.readAsString();

      if (mounted) {
        setState(() {
          _content = content;
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
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(child: Text(_error!));
    }

    final isCsv =
        widget.resource.filename?.toLowerCase().endsWith('.csv') ?? false;

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8.0),
          child: Text(
            widget.resource.title ?? 'Untitled',
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        Expanded(
          child: _content == null
              ? const Center(child: Text('No content'))
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
    final lines = content.split('\n');
    if (lines.isEmpty) return const Center(child: Text('Empty file'));

    final headers = lines.first.split(',').map((h) => h.trim()).toList();

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: SingleChildScrollView(
        child: DataTable(
          columns: headers.map((h) => DataColumn(label: Text(h))).toList(),
          rows:
              lines.skip(1).where((line) => line.trim().isNotEmpty).map((line) {
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
  const _MarkdownViewer({
    required this.resource,
    required this.getLocalFilePath,
  });

  final MyLibraryRow resource;
  final Future<String?> Function() getLocalFilePath;

  @override
  State<_MarkdownViewer> createState() => _MarkdownViewerState();
}

class _MarkdownViewerState extends State<_MarkdownViewer> {
  String? _content;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadContent();
  }

  Future<void> _loadContent() async {
    try {
      final path = await widget.getLocalFilePath();
      if (path == null) {
        setState(() {
          _error = 'File not found locally';
          _loading = false;
        });
        return;
      }

      final file = File(path);
      final content = await file.readAsString();

      if (mounted) {
        setState(() {
          _content = content;
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
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(child: Text(_error!));
    }

    if (_content == null) {
      return const Center(child: Text('No content'));
    }

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8.0),
          child: Text(
            widget.resource.title ?? 'Untitled',
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        Expanded(
          child: Markdown(
            data: _content!,
            selectable: true,
          ),
        ),
      ],
    );
  }
}
