/// The port's stand-in for `Html.fromHtml(text, FROM_HTML_MODE_LEGACY)`, which
/// `NotificationsAdapter.bind` (`NotificationsAdapter.kt:116-127`) runs over
/// every notification's text before it reaches the row's single TextView.
///
/// It is a deliberately small parser rather than a package, because the input
/// is a closed set: the two strings the port itself composes
/// (`formatJoinRequestNotification`'s `<b>` prefix and `formatTaskNotification`'s
/// `<b>` team name) and the messages Planet authors, which carry `<b>` around
/// the names they mention — `"<b>Jane</b> has requested to join <b>\"My
/// Team\"</b> team."`, `"You have been added to <b>\"My Team\"</b> team."`,
/// `"<b>Jane</b> replied to your message."`
/// (`NotificationsRepositoryImplTest.kt:168,192,214`). Those three shapes are
/// exactly the arms `formatNotification` passes through unchanged, so a
/// renderer that drew the message literally would show the tags.
///
/// What it reproduces, and why each piece is here:
///
/// * `<b>`/`<strong>` and `<i>`/`<em>` become emphasis flags — the markup that
///   actually occurs.
/// * An **unknown tag is dropped and its text kept**, as TagSoup does, so a
///   task titled `Read <chapter 3>` renders the same way in both apps.
/// * The HTML4 entities that can plausibly appear in a name or a team title are
///   decoded; an unrecognised entity is left alone, which is also what an
///   HTML parser does with `&foo;`.
/// * Runs of spaces and newlines collapse to one, and leading whitespace is
///   dropped. This is not decoration: `formatStorageNotification` composes
///   `"$prefix ${it}%"` from a string resource that already ends in a space
///   (`values/strings.xml:603`), so the Kotlin's own text carries a double
///   space that only this collapsing hides.
///
/// What it does not reproduce: block-level layout. `<p>`/`<div>` and friends
/// become a single newline where AOSP emits paragraph breaks, and lists lose
/// their bullets. No notification in the corpus contains one.
library;

import 'notification_format.dart';

/// Renders [html] into the spans the notification row draws.
FormattedNotification renderNotificationHtml(String html) {
  final spans = <NotificationSpan>[];
  final buffer = StringBuffer();
  var bold = 0;
  var italic = 0;
  // `pending` is the whitespace-collapsing state: a run of spaces or newlines
  // becomes one character, and one at the very start is dropped entirely.
  var pendingWhitespace = false;
  var wroteAnything = false;

  void flush() {
    // A space held over from before this tag belongs to the run that *ends*
    // here, not to the one that starts after it: `<b>very <i>late</i></b>` is
    // "very " in bold, then "late" in bold italic.
    if (pendingWhitespace && wroteAnything) {
      buffer.write(' ');
      pendingWhitespace = false;
    }
    if (buffer.isEmpty) return;
    spans.add(
      NotificationSpan(buffer.toString(), bold: bold > 0, italic: italic > 0),
    );
    buffer.clear();
  }

  void writeText(String text) {
    for (final rune in text.runes) {
      final char = String.fromCharCode(rune);
      // `\r` rides along with `\n`: an XML parser normalises CRLF to LF before
      // the converter sees the text, so a lone carriage return never reaches
      // AOSP's collapsing at all and matching it here is the closer reading.
      if (char == ' ' || char == '\n' || char == '\r') {
        pendingWhitespace = true;
        continue;
      }
      if (pendingWhitespace) {
        pendingWhitespace = false;
        if (wroteAnything) buffer.write(' ');
      }
      buffer.write(char);
      wroteAnything = true;
    }
  }

  void writeNewline() {
    if (!wroteAnything) return;
    pendingWhitespace = false;
    buffer.write('\n');
  }

  var index = 0;
  while (index < html.length) {
    final open = html.indexOf('<', index);
    if (open < 0) {
      writeText(_decodeEntities(html.substring(index)));
      break;
    }
    if (open > index) {
      writeText(_decodeEntities(html.substring(index, open)));
    }
    // A `<` only opens a tag when a tag name can follow it — a letter, or a
    // slash and then a letter. HTML tokenization emits every other `<` as
    // text, and so must this: `"Compare 3 < 5 > 1 today"` is a plain sentence
    // a server can send, and treating `< 5 >` as a tag deleted the ` 5 ` from
    // the middle of the row. Worse, a trimmed `< b >` read as `<b>` and
    // bolded the rest of the message.
    if (!_tagStart.hasMatch(
      html.substring(open, (open + 3).clamp(0, html.length)),
    )) {
      writeText(_decodeEntities('<'));
      index = open + 1;
      continue;
    }
    final close = html.indexOf('>', open);
    if (close < 0) {
      // An unterminated `<` is literal text, not a tag.
      writeText(_decodeEntities(html.substring(open)));
      break;
    }
    final raw = html.substring(open + 1, close);
    index = close + 1;
    if (raw.isEmpty) continue;

    final isClosing = raw.startsWith('/');
    final name = (isClosing ? raw.substring(1) : raw)
        .split(RegExp(r'[\s/]'))
        .first
        .toLowerCase();

    switch (name) {
      case 'b':
      case 'strong':
        flush();
        bold += isClosing ? -1 : 1;
        if (bold < 0) bold = 0;
      case 'i':
      case 'em':
        flush();
        italic += isClosing ? -1 : 1;
        if (italic < 0) italic = 0;
      case 'br':
      case 'p':
      case 'div':
      case 'li':
      case 'ul':
      case 'ol':
      case 'h1':
      case 'h2':
      case 'h3':
      case 'h4':
      case 'h5':
      case 'h6':
      case 'blockquote':
      case 'tr':
        writeNewline();
      default:
        // Every other tag is dropped, its text kept.
        break;
    }
  }
  flush();

  if (spans.isEmpty) return FormattedNotification.plain('');
  return FormattedNotification(_trimTrailingNewlines(spans));
}

List<NotificationSpan> _trimTrailingNewlines(List<NotificationSpan> spans) {
  final trimmed = [...spans];
  while (trimmed.isNotEmpty) {
    final last = trimmed.last;
    final text = last.text.replaceFirst(RegExp(r'\n+$'), '');
    if (text == last.text) break;
    trimmed.removeLast();
    if (text.isNotEmpty) {
      trimmed.add(NotificationSpan(text, bold: last.bold, italic: last.italic));
      break;
    }
  }
  return trimmed.isEmpty ? [const NotificationSpan('')] : trimmed;
}

/// `<` immediately followed by a tag name, or by `/` and a tag name. Anything
/// else — `< 5`, `<3`, a bare `<` — is text.
final RegExp _tagStart = RegExp(r'^<\/?[A-Za-z]');

/// The HTML4 entities worth decoding here, plus numeric references.
const Map<String, String> _entities = {
  'amp': '&',
  'lt': '<',
  'gt': '>',
  'quot': '"',
  'apos': "'",
  // U+00A0, not a plain space: an HTML parser decodes it that way and the
  // whitespace collapsing above leaves it alone, as AOSP's does.
  'nbsp': '\u00A0',
  'hellip': '…',
  'mdash': '—',
  'ndash': '–',
  'rsquo': '’',
  'lsquo': '‘',
  'ldquo': '“',
  'rdquo': '”',
};

String _decodeEntities(String text) {
  if (!text.contains('&')) return text;
  return text.replaceAllMapped(RegExp(r'&(#x?[0-9a-fA-F]+|\w+);'), (match) {
    final body = match.group(1)!;
    if (body.startsWith('#')) {
      final isHex = body.length > 1 && (body[1] == 'x' || body[1] == 'X');
      final digits = body.substring(isHex ? 2 : 1);
      final code = int.tryParse(digits, radix: isHex ? 16 : 10);
      if (code == null || code < 0 || code > 0x10FFFF) return match.group(0)!;
      return String.fromCharCode(code);
    }
    // An unrecognised entity stays as written, as a real parser leaves `&foo;`.
    return _entities[body.toLowerCase()] ?? match.group(0)!;
  });
}
