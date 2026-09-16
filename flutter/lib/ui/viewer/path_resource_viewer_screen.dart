import 'dart:io';

import 'package:flutter/material.dart';
import 'package:pdfx/pdfx.dart';
import 'package:video_player/video_player.dart';

import '../../l10n/app_localizations.dart';

/// Opens a local file by its raw filesystem path, routing on the extension
/// to the matching renderer. Ports `PersonalsAdapter.openResource` (and the
/// `TOUCHED_FILE` / `isFullPath=true` entry point into `ResourceViewerActivity`)
/// for personal-note attachments, which are not `MyLibrary` rows and so cannot
/// go through [ResourceViewerScreen].
///
/// The Kotlin switch only covers pdf, image (bmp/gif/jpg/png/webp), audio
/// (aac/mp3), and video (mp4) for personals; other extensions fall through to
/// a "cannot open" message, matching the Kotlin `when`'s implicit no-op.
class PathResourceViewerScreen extends StatefulWidget {
  const PathResourceViewerScreen({super.key, required this.path, this.title});

  final String path;
  final String? title;

  @override
  State<PathResourceViewerScreen> createState() =>
      _PathResourceViewerScreenState();
}

class _PathResourceViewerScreenState extends State<PathResourceViewerScreen> {
  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final type = pathResourceType(widget.path);
    return Scaffold(
      appBar: AppBar(title: Text(widget.title ?? l10n.attachment)),
      body: switch (type) {
        PathResourceType.pdf => _PathPdfView(path: widget.path),
        PathResourceType.image => _PathImageView(path: widget.path),
        PathResourceType.audio => _PathAudioView(path: widget.path),
        PathResourceType.video => _PathVideoView(path: widget.path),
        PathResourceType.unsupported => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(l10n.cannotOpenAttachment),
          ),
        ),
      },
    );
  }
}

/// Derives the viewer kind from a file path's extension. Pure and synchronous
/// so it can be unit-tested without a real file; mirrors the Kotlin
/// `PersonalsAdapter.openResource` switch.
PathResourceType pathResourceType(String path) {
  final dot = path.lastIndexOf('.');
  if (dot < 0 || dot == path.length - 1) return PathResourceType.unsupported;
  final ext = path.substring(dot + 1).toLowerCase();
  switch (ext) {
    case 'pdf':
      return PathResourceType.pdf;
    case 'bmp':
    case 'gif':
    case 'jpg':
    case 'jpeg':
    case 'png':
    case 'webp':
      return PathResourceType.image;
    case 'aac':
    case 'mp3':
      return PathResourceType.audio;
    case 'mp4':
      return PathResourceType.video;
    default:
      return PathResourceType.unsupported;
  }
}

enum PathResourceType { pdf, image, audio, video, unsupported }

class _PathPdfView extends StatefulWidget {
  const _PathPdfView({required this.path});
  final String path;

  @override
  State<_PathPdfView> createState() => _PathPdfViewState();
}

class _PathPdfViewState extends State<_PathPdfView> {
  PdfControllerPinch? _controller;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    try {
      _controller = PdfControllerPinch(
        document: PdfDocument.openFile(widget.path),
      );
    } catch (e) {
      _error = e.toString();
    }
    _loading = false;
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) return Center(child: Text(_error!));
    if (_controller == null) {
      return Center(child: Text(l10n.unableToLoadPdf));
    }
    return PdfViewPinch(
      controller: _controller!,
      builders: PdfViewPinchBuilders<DefaultBuilderOptions>(
        options: const DefaultBuilderOptions(),
        documentLoaderBuilder: (_) =>
            const Center(child: CircularProgressIndicator()),
        pageLoaderBuilder: (_) =>
            const Center(child: CircularProgressIndicator()),
        errorBuilder: (_, error) => Center(child: Text(error.toString())),
      ),
    );
  }
}

class _PathImageView extends StatelessWidget {
  const _PathImageView({required this.path});
  final String path;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final file = File(path);
    if (!file.existsSync()) {
      return Center(child: Text(l10n.imageFileNotFound));
    }
    return PhotoView(
      imageProvider: FileImage(file),
      minScale: PhotoViewComputedScale.contained,
      maxScale: PhotoViewComputedScale.covered * 3,
    );
  }
}

class _PathAudioView extends StatefulWidget {
  const _PathAudioView({required this.path});
  final String path;

  @override
  State<_PathAudioView> createState() => _PathAudioViewState();
}

class _PathAudioViewState extends State<_PathAudioView> {
  VideoPlayerController? _controller;
  bool _loading = true;
  String? _error;
  bool _fileMissing = false;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    try {
      final file = File(widget.path);
      if (!file.existsSync()) {
        if (mounted) {
          setState(() {
            _fileMissing = true;
            _loading = false;
          });
        }
        return;
      }
      final controller = VideoPlayerController.file(file);
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
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_fileMissing) return Center(child: Text(l10n.audioFileNotFound));
    if (_error != null) return Center(child: Text(_error!));
    if (_controller == null || !_controller!.value.isInitialized) {
      return Center(child: Text(l10n.unableToLoadAudio));
    }
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.audiotrack, size: 80),
          const SizedBox(height: 32),
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

class _PathVideoView extends StatefulWidget {
  const _PathVideoView({required this.path});
  final String path;

  @override
  State<_PathVideoView> createState() => _PathVideoViewState();
}

class _PathVideoViewState extends State<_PathVideoView> {
  VideoPlayerController? _controller;
  bool _loading = true;
  String? _error;
  bool _fileMissing = false;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    try {
      final file = File(widget.path);
      if (!file.existsSync()) {
        if (mounted) {
          setState(() {
            _fileMissing = true;
            _loading = false;
          });
        }
        return;
      }
      final controller = VideoPlayerController.file(file);
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
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_fileMissing) return Center(child: Text(l10n.videoFileNotFound));
    if (_error != null) return Center(child: Text(_error!));
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
        ],
      ),
    );
  }
}
