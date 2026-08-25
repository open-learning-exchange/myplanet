import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/config/server_config.dart';
import '../../core/network/network_result.dart';
import '../../core/utils/url_utils.dart';
import '../../providers/app_providers.dart';

/// Renders a course or step description as markdown — the Flutter counterpart
/// of `MarkdownUtils.setMarkdownText` after `prependBaseUrlToImages`.
///
/// The Kotlin rewrites each `![alt](path)` to `<img src=file://…/ole/path>`
/// and relies on the sync's concatenated-links download to materialise those
/// files locally; Markwon then reads them off disk. The Flutter port has no
/// such pre-download path yet, so images are resolved against the server and
/// fetched as bytes through the authenticated [PlanetApi.getBytes] path — the
/// same pattern [profileImageProvider] and [courseCoverImageProvider] use —
/// because a CouchDB attachment is behind Basic auth and `Image.network`
/// cannot send the header.
///
/// Text formatting (headers, lists, bold, code) renders regardless of network
/// state, which is the immediate improvement over the plain `Text` the screens
/// had before. Links open in the platform browser, matching the Kotlin's
/// `LinkMovementMethod`.
class CourseMarkdownBody extends ConsumerWidget {
  const CourseMarkdownBody({required this.data, this.styleSheet, super.key});

  final String data;
  final MarkdownStyleSheet? styleSheet;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    return MarkdownBody(
      data: data,
      styleSheet:
          styleSheet ??
          MarkdownStyleSheet(
            h1: theme.textTheme.headlineSmall,
            h2: theme.textTheme.titleLarge,
            p: theme.textTheme.bodyMedium,
          ),
      onTapLink: (_, href, _) {
        if (href != null) {
          launchUrl(Uri.parse(href));
        }
      },
      imageBuilder: (uri, _, _) => _MarkdownImage(uri: uri),
    );
  }
}

/// Fetches an inline markdown image through the authenticated bytes path.
///
/// Relative paths (`resources/<id>/<file>` or bare `<id>/<file>`) are resolved
/// against the active server's `/db` root; absolute http/https URIs on the
/// server host are fetched with the `satellite` auth header; absolute URIs on
/// a different host are loaded with plain `Image.network` (no auth); `file://`
/// URIs are read off disk. A fetch miss or failure renders nothing rather than
/// a broken-image icon, matching the Kotlin's silent `<img>` fallback.
class _MarkdownImage extends ConsumerWidget {
  const _MarkdownImage({required this.uri});

  final Uri uri;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (uri.scheme == 'file') {
      return Image.file(
        File(uri.toFilePath()),
        errorBuilder: (_, _, _) => const SizedBox.shrink(),
      );
    }
    final config = ref.watch(serverConfigProvider);
    if (uri.scheme == 'http' || uri.scheme == 'https') {
      // An absolute URL on a host other than the server is external — do not
      // attach the satellite credentials to a third-party request.
      if (config == null || uri.host != _serverHost(config)) {
        return Image.network(
          uri.toString(),
          errorBuilder: (_, _, _) => const SizedBox.shrink(),
        );
      }
      return _AuthedBytesImage(url: uri.toString(), config: config);
    }
    // Relative path — resolve against the server's /db root.
    if (config == null) return const SizedBox.shrink();
    final resolved = '${UrlUtils.dbUrl(config)}/${uri.toString()}';
    return _AuthedBytesImage(url: resolved, config: config);
  }
}

/// The host of the active server, for deciding whether an absolute image URL
/// is one of ours (authed) or external (plain network).
String? _serverHost(ServerConfig config) {
  final raw = config.isAlternativeUrl && config.alternativeUrl != null
      ? config.alternativeUrl!
      : config.couchDbUrl;
  return Uri.tryParse(raw)?.host;
}

/// Fetches [url] as bytes with [config]'s auth header and renders
/// `Image.memory`, shrinking on any miss.
class _AuthedBytesImage extends ConsumerWidget {
  const _AuthedBytesImage({required this.url, required this.config});

  final String url;
  final ServerConfig config;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bytes = ref.watch(markdownImageBytesProvider(url));
    return bytes.when(
      data: (data) => data == null || data.isEmpty
          ? const SizedBox.shrink()
          : Image.memory(
              data,
              errorBuilder: (_, _, _) => const SizedBox.shrink(),
            ),
      loading: () => const SizedBox.shrink(),
      error: (_, _) => const SizedBox.shrink(),
    );
  }
}

/// Authenticated byte fetch for an inline markdown image URL.
///
/// Family-keyed by the resolved URL so two images with the same src share one
/// fetch. Returns `null` on any failure — the widget shrinks rather than
/// throwing, so one bad image cannot break the whole description.
final markdownImageBytesProvider = FutureProvider.family<Uint8List?, String>((
  ref,
  url,
) async {
  final config = ref.watch(serverConfigProvider);
  if (config == null) return null;
  final result = await ref
      .watch(planetApiProvider)
      .getBytes(url, authHeader: UrlUtils.authHeader(config));
  return switch (result) {
    NetworkSuccess<List<int>>(:final data) => Uint8List.fromList(data),
    NetworkError<List<int>>() => null,
    NetworkException<List<int>>() => null,
  };
});
