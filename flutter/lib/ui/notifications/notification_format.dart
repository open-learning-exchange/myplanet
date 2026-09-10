/// Port of `NotificationsViewModel.formatNotification`
/// (`NotificationsViewModel.kt:354-425`).
///
/// The stored `message` is **not** what the Kotlin app shows. Each row's text
/// is rewritten per *resolved* type, and the adapter renders the result through
/// `Html.fromHtml` into the row's single TextView
/// (`NotificationsAdapter.kt:116-127`, `row_notifications.xml` — no second line
/// and no per-row icon). The port had none of that and drew `message` verbatim,
/// so its own resource notification — whose stored message is the bare count,
/// written by [NotificationsRepository.updateResourceNotification] — rendered
/// as `7`.
///
/// ## The two halves
///
/// [notificationHtml] composes exactly the string Kotlin composes, markup and
/// spacing quirks included; [renderNotificationHtml] is the `Html.fromHtml`
/// stand-in that turns it into spans. Keeping them apart is what lets the
/// composition stay literally faithful — the double space in the storage arm,
/// the `<b>` in the join-request prefix — while the renderer accounts for
/// everything a user does not see.
///
/// The renderer is a small parser rather than a package, and it is **not**
/// optional: three of the four arms pass the server's message through
/// unchanged, and those messages carry `<b>` around the names they mention
/// (`NotificationsRepositoryImplTest.kt:168,192,214`). A span list built only
/// from the port's own emphasis would have drawn those tags literally.
library;

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../repository/notifications_repository.dart';
import 'notification_html.dart';

/// The text the notification list draws for [row], as spans.
FormattedNotification formatNotification(
  NotificationRow row, {
  required AppLocalizations l10n,
  NotificationFormatContext context = const NotificationFormatContext.empty(),
}) =>
    renderNotificationHtml(notificationHtml(row, l10n: l10n, context: context));

/// Kotlin's `formattedText` for [row] — the HTML string, before rendering.
///
/// [context] carries the team names and join-request details
/// `loadNotifications` gathers before formatting
/// (`NotificationsViewModel.kt:71-143`); an empty context is the state before
/// those lookups resolve and degrades exactly as an uncached row does in the
/// Kotlin — the sentence renders without its `<b>Team</b>:` prefix.
String notificationHtml(
  NotificationRow row, {
  required AppLocalizations l10n,
  NotificationFormatContext context = const NotificationFormatContext.empty(),
}) {
  final message = row.message;
  switch (resolvedNotificationType(row)) {
    case 'task':
      final parsed = parseTaskDate(message);
      if (parsed == null) return message;
      return _taskNotificationHtml(
        l10n,
        title: parsed.title,
        date: parsed.date,
        relatedId: row.relatedId,
        taskTeamNames: context.taskTeamNames,
      );
    case 'resource':
      final count = kotlinToIntOrNull(message);
      return count == null ? message : l10n.resourceNotificationMessage(count);
    case 'storage':
      return formatStorageNotification(
        message,
        runningLow: l10n.storageRunningLow,
        available: l10n.storageAvailable,
      );
    case 'join_request':
      // Only a row whose *raw* type is `join_request` gets the lookup. A server
      // notification resolved onto `join_request` from a raw `team` type
      // carries a message Planet already wrote as a sentence — with its own
      // `<b>` around the names — and Kotlin uses it verbatim (`:382-384`,
      // "Server notification with pre-formatted message"). In practice that is
      // the only path that reaches this arm: nothing in either app stores a row
      // whose raw type is `join_request`, so the lookup below is ported for
      // fidelity rather than because it fires. See `PHASE_124_NOTES.md`.
      if (row.type.toLowerCase() != 'join_request') return message;
      final relatedId = row.relatedId;
      // Kotlin keys the fallback lookup on `""` — the entry
      // `loadNotifications` stores for the rows that carry no `relatedId`.
      final key = (relatedId == null || relatedId.isEmpty) ? '' : relatedId;
      final details = context.joinRequestDetails[key];
      // Kotlin's `?: Pair("Unknown User", "Unknown Team")`, and the same two
      // literals inside `getJoinRequestDetailsBatch`, localised: the repository
      // leaves the fields null so the fallback comes from the ARB rather than
      // being hardcoded English (the correction Phase 95 made to
      // `MyHealthScreen`'s `'Unknown'`).
      return formatJoinRequestNotification(
        l10n.joinRequestPrefix,
        l10n.userRequestedToJoinTeam(
          details?.requester ?? l10n.unknownUser,
          details?.team ?? l10n.unknownTeam,
        ),
      );
    default:
      return message;
  }
}

/// Port of `formatTaskNotification` (`NotificationsViewModel.kt:414-425`).
///
/// The team name is looked up by the task's id first and its title second, and
/// only a row that carries a `relatedId` tries the id — Kotlin's
/// `if (!relatedId.isNullOrEmpty())` branch, not a collapsed `??` chain. The
/// colon sits *outside* the `<b>`, unlike the join-request prefix.
String _taskNotificationHtml(
  AppLocalizations l10n, {
  required String title,
  required String date,
  required String? relatedId,
  required Map<String, String> taskTeamNames,
}) {
  final sentence = l10n.taskNotificationMessage(title, date);
  final teamName = (relatedId != null && relatedId.isNotEmpty)
      ? (taskTeamNames[relatedId] ?? taskTeamNames[title])
      : taskTeamNames[title];
  return teamName == null ? sentence : '<b>$teamName</b>: $sentence';
}

/// Port of `formatJoinRequestNotification` (`:346-351`) —
/// `"<b>$prefix</b> $body"`.
String formatJoinRequestNotification(String prefix, String body) =>
    '<b>$prefix</b> $body';

/// Port of `formatStorageNotification` (`:335-344`).
///
/// Kotlin's `<= 10` and `<= 40` arms produce the same string; only `> 40`
/// differs, so the two are one condition here — `NotificationsViewModelTest`
/// pins all three (`:60-88`). A message that is not a percentage falls back to
/// itself **including its `%`**, not to the stripped form.
///
/// [runningLow] and [available] are the `storage_running_low` /
/// `storage_available` resources, which are Android-quoted and therefore carry
/// a trailing space; Kotlin's template adds a second one. The double space is
/// preserved here and collapsed by [renderNotificationHtml], which is where
/// Kotlin loses it too.
///
/// Only the `<= 10` arm is reachable in production: the local writer deletes
/// the row above 10% and never stores a higher number
/// (`NotificationsRepositoryImpl.kt:88,108-110`).
String formatStorageNotification(
  String message, {
  required String runningLow,
  required String available,
}) {
  final percent = kotlinToIntOrNull(message.replaceAll('%', ''));
  if (percent == null) return message;
  return percent <= 40 ? '$runningLow $percent%' : '$available $percent%';
}

/// Port of `NotificationsViewModel.parseTaskDate` (`:322-333`).
///
/// Splits `"Read chapter 3 Thu 12, August 2027"` into its title and the date
/// tail. The tail runs from the match start to the **end of the message**, not
/// to the end of the match, so anything trailing the date rides along with it —
/// Kotlin's `message.substring(matcher.start())`.
({String title, String date})? parseTaskDate(String message) {
  final match = _taskDatePattern.firstMatch(message);
  if (match == null) return null;
  return (
    title: message.substring(0, match.start).trim(),
    date: message.substring(match.start).trim(),
  );
}

/// `NotificationsViewModel.TASK_DATE_PATTERN` (`:322`).
///
/// `\s` is spelled out as Java's ASCII class rather than written `\s`: Dart's
/// `\s` also matches U+00A0 and the other Unicode spaces, so a message using a
/// non-breaking space would match here and not in the Kotlin. `\b`, `\w` and
/// `\d` agree between the two — and `\w` is genuinely `[A-Za-z0-9_]`, so the
/// month position matches any word, not a month name. The weekday tokens are
/// case-sensitive; there is no `CASE_INSENSITIVE` flag.
final RegExp _taskDatePattern = RegExp(
  r'\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)[ \t\n\x0B\f\r]\d{1,2},'
  r'[ \t\n\x0B\f\r]\w+[ \t\n\x0B\f\r]\d{4}\b',
);

/// Kotlin's `String.toIntOrNull()`, which `int.tryParse` is not.
///
/// Two differences decide whether a message is rewritten or shown raw:
/// `int.tryParse` accepts surrounding whitespace where Kotlin does not, and it
/// has no 32-bit ceiling — `"2147483648"` is null in Kotlin and a number in
/// Dart, so the port would have announced 2,147,483,648 undownloaded resources
/// where the Kotlin shows the raw string.
///
/// One divergence is left in place: Kotlin's parser accepts any Unicode decimal
/// digit (`"٧"` is 7), which is not reproduced. No writer in either app
/// produces one — the count is composed with `"$resourceCount"` — and this arm
/// only ever sees a message a writer authored.
int? kotlinToIntOrNull(String value) {
  if (!RegExp(r'^[+-]?[0-9]+$').hasMatch(value)) return null;
  final parsed = int.tryParse(value);
  if (parsed == null) return null;
  return (parsed < -2147483648 || parsed > 2147483647) ? null : parsed;
}

/// A notification's text, as the runs the row draws.
class FormattedNotification {
  const FormattedNotification(this.spans);

  /// A single unemphasised run.
  factory FormattedNotification.plain(String text) =>
      FormattedNotification([NotificationSpan(text)]);

  final List<NotificationSpan> spans;

  /// The text with the emphasis dropped, for tests, semantics and any caller
  /// that needs a plain string.
  String get text => spans.map((span) => span.text).join();

  @override
  bool operator ==(Object other) =>
      other is FormattedNotification &&
      other.spans.length == spans.length &&
      Iterable<int>.generate(
        spans.length,
      ).every((i) => other.spans[i] == spans[i]);

  @override
  int get hashCode => Object.hashAll(spans);

  @override
  String toString() => 'FormattedNotification($spans)';
}

/// One run of a formatted notification. [bold] is Kotlin's `<b>`.
class NotificationSpan {
  const NotificationSpan(this.text, {this.bold = false, this.italic = false});

  final String text;
  final bool bold;
  final bool italic;

  @override
  bool operator ==(Object other) =>
      other is NotificationSpan &&
      other.text == text &&
      other.bold == bold &&
      other.italic == italic;

  @override
  int get hashCode => Object.hash(text, bold, italic);

  @override
  String toString() {
    var out = text;
    if (italic) out = '<i>$out</i>';
    if (bold) out = '<b>$out</b>';
    return out;
  }
}

/// The lookups `NotificationsViewModel.loadNotifications` gathers before it
/// formats a page of notifications (`:71-143`), in one value.
class NotificationFormatContext {
  const NotificationFormatContext({
    required this.taskTeamNames,
    required this.joinRequestDetails,
  });

  const NotificationFormatContext.empty()
    : taskTeamNames = const {},
      joinRequestDetails = const {};

  /// Task id **and** task title → the owning team's name. Kotlin merges the
  /// two maps into one and looks the id up first, so they share a namespace.
  final Map<String, String> taskTeamNames;

  /// Join-request document id → (requester, team). The `''` key is the
  /// fallback entry for rows that carry no `relatedId`.
  final Map<String, JoinRequestDetail> joinRequestDetails;
}

/// Port of the lookup half of `loadNotifications` (`:71-140`).
///
/// **The partition is on the raw stored type, not the resolved one**, exactly
/// as the Kotlin's `notification.type.equals("task", ignoreCase = true)` is. So
/// a server `newTask` document — which resolves to `task`, groups under Tasks
/// and routes to the team's task list — never enters `taskNotifications`, never
/// gets a team-name lookup and renders without the `<b>Team</b>:` prefix.
/// Resolving before partitioning would be an improvement, not a port, so it is
/// left alone and recorded in `PHASE_124_NOTES.md`.
Future<NotificationFormatContext> buildNotificationFormatContext(
  List<NotificationRow> rows,
  NotificationsRepository repository,
) async {
  final taskRows = rows
      .where((row) => row.type.toLowerCase() == 'task')
      .toList(growable: false);
  final joinRequestRows = rows
      .where((row) => row.type.toLowerCase() == 'join_request')
      .toList(growable: false);
  if (taskRows.isEmpty && joinRequestRows.isEmpty) {
    return const NotificationFormatContext.empty();
  }

  // `mapNotNull { it.relatedId }` filters nulls only, so an empty-string
  // `relatedId` survives into the query list — and also lands in
  // `joinRequestsWithoutRelatedId`, which tests `isNullOrEmpty`.
  final taskIds = <String>{
    for (final row in taskRows)
      if (row.relatedId != null) row.relatedId!,
  }.toList(growable: false);
  final taskTitles = <String>{
    for (final row in taskRows)
      if (parseTaskDate(row.message) case final parsed?) parsed.title,
  }.toList(growable: false);
  final joinRequestIds = <String>{
    for (final row in joinRequestRows)
      if (row.relatedId != null) row.relatedId!,
  }.toList(growable: false);
  final hasJoinRequestWithoutRelatedId = joinRequestRows.any(
    (row) => row.relatedId == null || row.relatedId!.isEmpty,
  );

  // `async { … }` × 4 in the Kotlin, awaited together. The by-title map is
  // written first and the by-id map `putAll`-ed over it, so an id wins a
  // collision.
  final results = await Future.wait([
    repository.taskTeamNamesByTaskTitles(taskTitles),
    repository.taskTeamNamesByTaskIds(taskIds),
    repository.joinRequestDetailsBatch(joinRequestIds),
    if (hasJoinRequestWithoutRelatedId) repository.joinRequestDetails(null),
  ]);

  return NotificationFormatContext(
    taskTeamNames: {
      ...results[0] as Map<String, String>,
      ...results[1] as Map<String, String>,
    },
    joinRequestDetails: {
      ...results[2] as Map<String, JoinRequestDetail>,
      // Written after the batch, so it overwrites any `""` entry the batch
      // produced — as Kotlin's `details[""] = fallbackDetail` does.
      if (hasJoinRequestWithoutRelatedId) '': results[3] as JoinRequestDetail,
    },
  );
}
