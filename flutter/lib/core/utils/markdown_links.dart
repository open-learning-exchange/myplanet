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
/// Nothing in `lib/` calls this, and Phase 153 established that nothing should.
/// The port's `CourseMarkdownBody` renders `![alt](path)` spans through
/// `flutter_markdown_plus`, preferring the pre-downloaded local copy
/// ([markdownImageCachePath]) and falling back to an authenticated fetch, so it
/// never needs the rewrite. The Kotlin uses it in four places, all live.
///
/// One property worth knowing before anyone wires this up: it **discards the
/// markdown alt text**. Markwon draws an `<img>` whose file is missing as
/// `HtmlEmptyTagReplacement`'s `IMG_REPLACEMENT`, the alt attribute if there is
/// one and `U+FFFC OBJECT REPLACEMENT CHARACTER` if there is not — so the
/// Kotlin's missing-image state is one unlabelled box glyph rather than the
/// image's description. Kept here as the line-for-line port of a live Kotlin
/// function, quirk included.
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

/// The image destination a markdown link actually points at, with a CommonMark
/// title removed and a pointy-bracket destination unwrapped.
///
/// **This is where two parsers are reconciled, and it is load-bearing.** The
/// collector is [extractImageLinks], a line-for-line port of Kotlin's
/// `DownloadUtils.extractLinks` regex, whose lazy `(.*?)` captures everything
/// up to the first `)` — so `![a](p "Title")` yields `p "Title"` and
/// `![a](<p>)` yields `<p>`. The renderer's link comes from
/// `flutter_markdown_plus`'s CommonMark parser, which yields `p` for both.
/// Verified against the real parser rather than assumed.
///
/// Kotlin has the same regex quirk and is *self-consistent* with it, because
/// `prependBaseUrlToImages` matches on the identical pattern: it downloads and
/// renders the same wrong path, so a titled image simply never appears. The
/// port cannot be self-consistent that way — its renderer is a real markdown
/// parser — so the collector is normalised to what the renderer will ask for.
/// Without this the prefetcher 404s on `…/c.png "Title"` while the renderer
/// looks up `abc/c.png`, which nothing wrote: the exact writer/reader key
/// disagreement of Phase 100, and just as silent.
///
/// Applied to a link the renderer supplies this is a no-op — a parsed
/// destination carries neither a title nor brackets.
String markdownImageDestination(String link) {
  var value = link.trim();
  // CommonMark: `<destination> "title"`, so the title comes off first. The
  // title may be double-quoted, single-quoted or parenthesised, and must be
  // separated from the destination by whitespace.
  final title = _titleSuffix.firstMatch(value);
  if (title != null) value = value.substring(0, title.start).trimRight();
  if (value.length >= 2 && value.startsWith('<') && value.endsWith('>')) {
    value = value.substring(1, value.length - 1).trim();
  }
  return value;
}

/// A trailing CommonMark title: quoted, and preceded by whitespace.
///
/// CommonMark also allows a **parenthesised** title (`p (T)`), deliberately
/// absent here because neither producer can emit one. [extractImageLinks]'s
/// lazy `(.*?)` stops at the first `)`, so `![a](p (T))` yields `p (T` — never
/// a string ending in `)` — and the renderer's destinations are
/// percent-encoded with no whitespace, so `\s+` cannot match there either. A
/// branch no producer can reach reads as a guard while pinning nothing, which
/// is a shape this file has already removed two of.
final RegExp _titleSuffix = RegExp(r"""\s+("[^"]*"|'[^']*')$""");

/// The cache-relative path a markdown image [link] names, or `null` when the
/// link cannot name a locally cached file.
///
/// This is the **single key derivation** shared by the prefetcher that writes
/// the bytes and the renderer that reads them back. Phase 100 lost a certified
/// exam's verification photo because the writer and the reader each derived
/// their own key and the lookup missed on every capture, silently; the same
/// trap is why this is one function rather than two similar ones.
///
/// The shape it resolves is the one [prependBaseUrlToImages] documents: a
/// Planet markdown author writes `resources/<attachmentId>/<file>`, the
/// download lands at `<base>/ole/<attachmentId>/<file>`, and the Kotlin's
/// render base (`file://<externalFilesDir>/ole/`) plus the `resources/`-
/// stripped path point at it. So the returned path is the link with a leading
/// `resources/` removed — the same strip, kept in the same file as the
/// rewrite so the two cannot drift apart.
///
/// Returns `null` — meaning "there is no local copy of this, resolve it some
/// other way" — for:
///
/// * an **absolute** URL (`http:`, `https:`, `file:`, or any other scheme).
///   The Kotlin has no such guard and mishandles these at both ends: it builds
///   a download URL of `<serverUrl>/http://…` and renders
///   `file://…/ole/http://…`, neither of which resolves. The port's
///   `_MarkdownImage` already handles an absolute URL correctly, so declining
///   here hands it back to the path that works.
/// * a link containing a `..` segment, a leading `/`, or a backslash. A
///   server-supplied path reaching the filesystem is how a directory is
///   escaped (`AchievementFiles._segment` and
///   `ResourceFiles.resolveHtmlEntryFile` are the precedents), and a legitimate
///   attachment path never contains one.
/// * a link that is left with no segments at all after the strip.
///
/// A **single**-segment link (a bare `cover.jpg`) is accepted, resolving to
/// `<base>/ole/cover.jpg`. That is what the Kotlin does — the ground-truth
/// audit for this phase confirmed a bare filename is one of the only two
/// shapes whose download path and render path agree there — so two courses
/// that both write `![](cover.jpg)` collide in this port exactly as they do in
/// the Kotlin. Declining the shape instead would have been a regression
/// against the app being ported, not a hardening of it.
///
/// Each segment is percent-decoded, so `a%20b.png` and a literal `a b.png`
/// name the same file. That matches the Kotlin's *download* side
/// (`FileUtils.getResourceRelativePathFromSegments` runs `URLDecoder.decode`
/// per segment). The Kotlin's *render* side does not
/// decode, so those two disagree there for an encoded path; here one
/// derivation serves both, so they cannot.
///
/// It does **not** follow that a markdown link always lands on the same file
/// `ResourceDownloader` would write — that holds for a flat attachment and not
/// for a nested one, because `ResourceFiles.fileFor` runs `p.basename` over
/// its `filename` and so flattens `sub/chart.png`. Only the **root** is
/// shared.
String? markdownImageCachePath(String link) {
  final trimmed = markdownImageDestination(link);
  if (trimmed.isEmpty) return null;
  // A scheme means an absolute URL. `http://…` and `https://…` would fail the
  // per-segment checks anyway, on the empty segment their `//` leaves; what
  // this actually catches are the authority-less schemes — `data:`, `mailto:`,
  // `tel:`, a bare `file:a.png` — which split into ordinary-looking segments
  // and would otherwise be requested from the server.
  if (Uri.tryParse(trimmed)?.hasScheme ?? false) return null;

  final stripped = trimmed.startsWith('resources/')
      ? trimmed.substring('resources/'.length)
      : trimmed;
  final segments = stripped.split('/');
  if (segments.isEmpty) return null;
  final decoded = <String>[];
  for (final segment in segments) {
    final String plain;
    try {
      plain = Uri.decodeComponent(segment);
    } on ArgumentError {
      // Bad hex (`%zz`, a truncated `%2`). Nothing can name a file from this.
      return null;
    } on FormatException {
      // **Valid hex that is not valid UTF-8**, which is a different exception
      // and the one that matters: `caf%E9.png` is Latin-1 `café.png`, the
      // shape any pre-UTF-8 attachment name on an older Planet install has.
      // Catching only `ArgumentError` let this escape the prefetch and the
      // whole `CoursesRepository.sync` — and in the background isolate that
      // left `recordLastSync` unwritten and WorkManager retrying the entire
      // walk for as long as that one document existed on the server.
      return null;
    }
    // Checked *after* decoding, and only after: `%2e%2e` decodes to `..` and
    // `%2f` to a separator, so a check on the raw segment alone is one
    // encoding away from useless — and a check on both is the raw one dead,
    // which reads as a guard while pinning nothing. A leading `/` and a
    // backslash-separated path fail here too, on their empty first segment
    // and on the separator respectively.
    if (plain.isEmpty ||
        plain == '.' ||
        plain == '..' ||
        plain.contains('/') ||
        plain.contains(r'\')) {
      return null;
    }
    decoded.add(plain);
  }
  return decoded.join('/');
}
