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
/// Rewrites each `![alt](path)` to `<img src="$baseUrl$path" width height/>`,
/// stripping a leading `resources/` from the path first.
///
/// The strip is about the **local** directory layout, not about a doubled
/// prefix: all four Kotlin callers pass a [baseUrl] of
/// `file://<externalFilesDir>/ole/`, which has no `resources` segment to
/// double. A markdown author writes `resources/<id>/<file>`, and
/// `FileUtils.getIdFromSegments` treats `resources` as a *marker* — the
/// download lands at `<externalFilesDir>/ole/<id>/<file>`. Stripping the
/// marker is what makes the rendered `src` point at where the file actually
/// is. Note the corollary: the download URL keeps `resources/`
/// (`CoursesRepositoryImpl:671` builds `"$baseUrl/$link"` from the unstripped
/// link) while the render base drops it, and the port's own relative
/// resolution in `course_markdown.dart` keeps it — correctly, because that
/// resolves against the server, not the local cache.
///
/// The Kotlin feeds a `file://<externalFilesDir>/ole/` [baseUrl] so the
/// rendered image reads a locally downloaded copy; the same rewrite works for
/// any base. Returns the empty string for `null` input, matching the Kotlin.
///
/// The `src` value is **quoted**, following upstream `9e402fb`: unquoted, a
/// path containing a space runs into the following `width=` attribute and the
/// tag is mis-parsed.
///
/// Nothing in `lib/` calls this yet — see `PHASE_142_NOTES.md`. The port's
/// `CourseMarkdownBody` renders `![alt](path)` spans directly and resolves
/// relative paths as authenticated bytes, so it never needs the rewrite; the
/// Kotlin uses it in four places.
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
    return '<img src="$baseUrl$stripped" width=$width height=$height/>';
  });
}
