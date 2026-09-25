import 'package:flutter/material.dart';
import 'package:pdfx/pdfx.dart';

import '../../core/files/achievement_files.dart';
import '../../l10n/app_localizations.dart';

/// The CV preview — Kotlin routes `btn_view_cv_edit` to `PdfViewerActivity`
/// with the stored file; here it is a full-screen dialog over the same
/// `<ole>/cv/<name>` slot the edit screen copies picks into.
Future<void> showCvViewerDialog(BuildContext context, String name) async {
  final l10n = AppLocalizations.of(context);
  await showDialog<void>(
    context: context,
    builder: (context) => Dialog.fullscreen(
      child: Scaffold(
        appBar: AppBar(title: Text(l10n.viewCv)),
        body: CvPdfViewer(resumeFileName: name),
      ),
    ),
  );
}

class CvPdfViewer extends StatefulWidget {
  const CvPdfViewer({super.key, required this.resumeFileName});

  final String resumeFileName;

  @override
  State<CvPdfViewer> createState() => _CvPdfViewerState();
}

class _CvPdfViewerState extends State<CvPdfViewer> {
  PdfControllerPinch? _controller;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _open();
  }

  Future<void> _open() async {
    try {
      final file = await AchievementFiles.fileFor(widget.resumeFileName);
      _controller = PdfControllerPinch(
        document: PdfDocument.openFile(file.path),
      );
    } catch (e) {
      _error = e.toString();
    }
    if (mounted) setState(() => _loading = false);
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
    final controller = _controller;
    if (_error != null || controller == null) {
      return Center(child: Text(l10n.unableToLoadPdf));
    }
    return PdfViewPinch(
      controller: controller,
      builders: PdfViewPinchBuilders<DefaultBuilderOptions>(
        options: const DefaultBuilderOptions(),
        documentLoaderBuilder: (_) =>
            const Center(child: CircularProgressIndicator()),
        pageLoaderBuilder: (_) =>
            const Center(child: CircularProgressIndicator()),
      ),
    );
  }
}
