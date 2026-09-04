import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/ui/notifications/notification_format.dart';
import 'package:myplanet/ui/notifications/notification_html.dart';

/// Coverage for the `Html.fromHtml` stand-in the notification row renders
/// through. `NotificationsAdapterTest.kt:86-99` is the Kotlin counterpart: it
/// binds `"<b>Initial</b> text"` and asserts the TextView reads `Initial text`.
void main() {
  test('plain text is one unemphasised run', () {
    expect(
      renderNotificationHtml('Storage running low: 8%'),
      FormattedNotification.plain('Storage running low: 8%'),
    );
  });

  // The Kotlin adapter test, ported.
  test('a bold run is split out and the tags do not survive', () {
    final rendered = renderNotificationHtml('<b>Initial</b> text');
    expect(rendered.text, 'Initial text');
    expect(rendered.spans, const [
      NotificationSpan('Initial', bold: true),
      NotificationSpan(' text'),
    ]);
  });

  test('the three server messages Planet actually sends', () {
    // `NotificationsRepositoryImplTest.kt:168,192,214` — the fixtures for the
    // arms `formatNotification` passes through unchanged. Drawing these
    // literally would have put `<b>` on screen.
    expect(
      renderNotificationHtml(
        '<b>Jane</b> has requested to join <b>"My Team"</b> team.',
      ).text,
      'Jane has requested to join "My Team" team.',
    );
    expect(
      renderNotificationHtml(
        'You have been added to <b>"My Team"</b> team.',
      ).spans,
      const [
        NotificationSpan('You have been added to '),
        NotificationSpan('"My Team"', bold: true),
        NotificationSpan(' team.'),
      ],
    );
    expect(
      renderNotificationHtml(
        '<b>Jane</b> replied to your message.',
      ).spans.first,
      const NotificationSpan('Jane', bold: true),
    );
  });

  test('runs of spaces collapse, which is what hides the storage double '
      'space', () {
    // `formatStorageNotification` composes `"Storage running low:  8%"` from a
    // string resource that already ends in a space. Kotlin only ever shows one.
    expect(
      renderNotificationHtml('Storage running low:  8%').text,
      'Storage running low: 8%',
    );
    expect(renderNotificationHtml('a \n  b').text, 'a b');
  });

  test('leading whitespace is dropped', () {
    expect(renderNotificationHtml('   7').text, '7');
  });

  test('a tab is not collapsed', () {
    // AOSP's converter collapses ' ' and '\n' only.
    expect(renderNotificationHtml('a\t\tb').text, 'a\t\tb');
  });

  test('an unknown tag is dropped and its text kept', () {
    // A task titled `Read <chapter 3>` renders the same way in both apps: the
    // "tag" vanishes, the rest stays.
    expect(
      renderNotificationHtml(
        'Read <chapter 3> is due in Thu 12, Aug 2027',
      ).text,
      'Read is due in Thu 12, Aug 2027',
    );
  });

  test('italic markup is emphasis, not literal text', () {
    expect(renderNotificationHtml('<i>soon</i>').spans, const [
      NotificationSpan('soon', italic: true),
    ]);
    expect(renderNotificationHtml('<em>soon</em>').spans.first.italic, isTrue);
    expect(
      renderNotificationHtml('<strong>now</strong>').spans.first.bold,
      isTrue,
    );
  });

  test('nested emphasis keeps both flags', () {
    expect(renderNotificationHtml('<b>very <i>late</i></b>').spans, const [
      NotificationSpan('very ', bold: true),
      NotificationSpan('late', bold: true, italic: true),
    ]);
  });

  test('entities are decoded', () {
    expect(renderNotificationHtml('Tom &amp; Jerry').text, 'Tom & Jerry');
    expect(renderNotificationHtml('&quot;My Team&quot;').text, '"My Team"');
    expect(renderNotificationHtml('&#65;&#x42;').text, 'AB');
    // A non-breaking space decodes to U+00A0 and is not collapsed away.
    expect(renderNotificationHtml('a&nbsp;&nbsp;b').text, 'a  b');
  });

  test('an unrecognised entity is left as written', () {
    expect(renderNotificationHtml('a &foo; b').text, 'a &foo; b');
  });

  test('an unterminated angle bracket is text, not a tag', () {
    expect(renderNotificationHtml('2 < 3').text, '2 < 3');
  });

  test('a break becomes a newline and trailing ones are trimmed', () {
    expect(renderNotificationHtml('a<br>b').text, 'a\nb');
    expect(renderNotificationHtml('a<br>').text, 'a');
  });

  test('an empty string renders as empty rather than throwing', () {
    expect(renderNotificationHtml('').text, '');
    expect(renderNotificationHtml('<b></b>').text, '');
  });
}
