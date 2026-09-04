/// Port of the markdown-image half of `utils/MarkdownUtils.kt` and
/// `utils/DownloadUtils.extractLinks`.
///
/// Course and step descriptions are markdown, and their images are written as
/// `![alt](relativePath)`. The Kotlin rewrites those to `<img src=…>` so
/// Markwon can render them, and collects the paths so the sync flow can
/// pre-download the media for offline viewing. Both behaviours start from the
/// same regex, `!\[.*?]\((.*?)\)`, so they share it here.
const _imagePattern = r'!\[.*?]\((.*?)\)';

final RegExp _imageLinkRegex = RegExp(_imagePattern);

/// Port of `DownloadUtils.extractLinks` — every capture-group-1 from an
/// image markdown span, empty strings dropped.
///
/// Returns `[]` for `null` or blank input, matching the Kotlin's empty-list
/// return for a null matcher.
List<String> extractImageLinks(String? text) {
  if (text == null || text.isEmpty) return const [];
  return _imageLinkRegex
      .allMatches(text)
      .map((m) => m.group(1) ?? '')
      .where((link) => link.isNotEmpty)
      .toList(growable: false);
}

/// Port of `MarkdownUtils.prependBaseUrlToImages`.
///
/// Rewrites each `![alt](path)` to `<img src=$baseUrl$path width height/>`,
/// stripping a leading `resources/` from the path first — the server stores
/// resource attachments under `/<db>/resources/<id>/<file>`, but the markdown
/// author writes the path as `resources/…`, so the prefix would double up.
///
/// The Kotlin feeds a `file://<externalFilesDir>/ole/` [baseUrl] so the
/// rendered image reads a locally downloaded copy; the same rewrite works for
/// any base. Returns the empty string for `null` input, matching the Kotlin.
String prependBaseUrlToImages(
  String? markdownContent,
  String baseUrl, {
  int width = 150,
  int height = 100,
}) {
  if (markdownContent == null) return '';
  return markdownContent.replaceAllMapped(_imageLinkRegex, (match) {
    final relativePath = match.group(1) ?? '';
    final stripped = relativePath.startsWith('resources/')
        ? relativePath.substring('resources/'.length)
        : relativePath;
    return '<img src=$baseUrl$stripped width=$width height=$height/>';
  });
}
