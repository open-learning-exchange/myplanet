import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../l10n/app_localizations.dart';

/// Port of `ui/viewer/WebViewActivity.kt`'s external-URL half. The
/// local-resource HTML viewer is already ported by the resource viewer
/// (Phase 54, `ResourceViewerScreen`); this screen is the standalone browser
/// that `CommunityServicesFragment` and voices links open when a link starts
/// with `http(s)://`.
///
/// The Kotlin gates JavaScript behind `isLocalResource`; for an external URL
/// JavaScript is off by default (the Kotlin only enables it for local
/// resources), and the user is warned this is an untrusted page.
class WebViewScreen extends StatefulWidget {
  const WebViewScreen({required this.url, this.title, super.key});

  final String url;
  final String? title;

  @override
  State<WebViewScreen> createState() => _WebViewScreenState();
}

class _WebViewScreenState extends State<WebViewScreen> {
  late final WebViewController _controller;
  int _progress = 0;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.disabled)
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (progress) => setState(() {
            _progress = progress;
            _loading = progress < 100;
          }),
          onPageFinished: (_) => setState(() {
            _loading = false;
            _progress = 100;
          }),
          onWebResourceError: (error) => setState(() {
            _loading = false;
            _error = error.description;
          }),
        ),
      )
      ..loadRequest(Uri.parse(widget.url));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title ?? widget.url),
        actions: [
          IconButton(
            tooltip: l10n.refresh,
            icon: const Icon(Icons.refresh),
            onPressed: () => _controller.reload(),
          ),
        ],
      ),
      body: Stack(
        children: [
          WebViewWidget(controller: _controller),
          if (_loading && _error == null)
            LinearProgressIndicator(value: _progress / 100),
          if (_error != null)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.error_outline, size: 48),
                    const SizedBox(height: 16),
                    Text(_error!),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: () {
                        setState(() => _error = null);
                        _controller.reload();
                      },
                      child: Text(l10n.retry),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
