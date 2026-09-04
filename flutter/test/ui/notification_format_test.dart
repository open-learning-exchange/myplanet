import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/l10n/app_localizations_en.dart';
import 'package:myplanet/repository/notifications_repository.dart';
import 'package:myplanet/ui/notifications/notification_format.dart';
import 'package:myplanet/ui/notifications/notification_html.dart';

/// Port coverage for `NotificationsViewModel.formatNotification` and the four
/// helpers it delegates to. Where the Kotlin has a unit test
/// (`NotificationsViewModelTest.kt:45-107`) the case is mirrored exactly, so a
/// divergence in either app shows up as a disagreement between two suites
/// rather than as a rendering nobody looks at.
void main() {
  final AppLocalizations l10n = AppLocalizationsEn();

  NotificationRow row({
    required String message,
    required String type,
    String? subType,
    String? relatedId,
    bool isRead = false,
  }) => NotificationRow(
    id: 'n-1',
    userId: 'user-1',
    message: message,
    type: type,
    subType: subType,
    relatedId: relatedId,
    isRead: isRead,
    createdAt: 1000,
    priority: 0,
    isFromServer: false,
    needsSync: false,
  );

  group('parseTaskDate', () {
    // `NotificationsViewModelTest.kt:45-51`.
    test('splits the title from the date', () {
      final parsed = parseTaskDate('Complete math assignment Mon 12, Jan 2024');
      expect(parsed?.title, 'Complete math assignment');
      expect(parsed?.date, 'Mon 12, Jan 2024');
    });

    // `:53-58`.
    test('returns null when the message carries no date', () {
      expect(parseTaskDate('Some random message without date'), isNull);
    });

    test('the date runs to the end of the message, not to the end of the '
        'match', () {
      // Kotlin is `message.substring(matcher.start())`, so anything trailing
      // the date rides along with it.
      final parsed = parseTaskDate('Deadline Wed 03, September 2025 — hurry');
      expect(parsed?.title, 'Deadline');
      expect(parsed?.date, 'Wed 03, September 2025 — hurry');
    });

    test('the weekday is case-sensitive', () {
      // No CASE_INSENSITIVE flag on the Kotlin pattern.
      expect(parseTaskDate('Report sun 12, Jan 2024'), isNull);
    });

    test('the month position is any word, not a month name', () {
      // Java's `\w` is `[A-Za-z0-9_]` and that is all the pattern asks for.
      expect(parseTaskDate('Report Mon 12, _ 2024')?.date, 'Mon 12, _ 2024');
    });

    test('a non-breaking space does not match, as in Java', () {
      // Dart's `\s` matches U+00A0 and Java's does not; the pattern spells the
      // ASCII class out so the two agree.
      expect(parseTaskDate('Report Mon 12, January 2024'), isNull);
    });
  });

  group('formatStorageNotification', () {
    String format(String message) => formatStorageNotification(
      message,
      runningLow: l10n.storageRunningLow,
      available: l10n.storageAvailable,
    );

    // `NotificationsViewModelTest.kt:60-78` — both arms produce the same text.
    test('10% and 40% are both "running low"', () {
      expect(renderedText(format('10%')), 'Storage running low: 10%');
      expect(renderedText(format('40%')), 'Storage running low: 40%');
    });

    // `:80-88`.
    test('above 40% reads as available', () {
      expect(renderedText(format('50%')), 'Storage available: 50%');
    });

    // `:90-98`.
    test('a non-numeric message falls back to itself, % included', () {
      expect(format('not_an_int'), 'not_an_int');
      expect(format('lots of space left %'), 'lots of space left %');
    });

    test('the composed string carries the double space Kotlin composes', () {
      // The resource string ends in a space and the template adds another;
      // only the renderer hides it. Composing one space here would look right
      // and silently diverge from the Kotlin source.
      expect(format('8%'), 'Storage running low:  8%');
    });
  });

  // `NotificationsViewModelTest.kt:100-107`.
  test('formatJoinRequestNotification bolds the prefix only', () {
    expect(
      formatJoinRequestNotification(
        'Join Request',
        'John Doe requested to join Awesome Team',
      ),
      '<b>Join Request</b> John Doe requested to join Awesome Team',
    );
  });

  group('kotlinToIntOrNull', () {
    test('accepts a plain integer', () {
      expect(kotlinToIntOrNull('7'), 7);
      expect(kotlinToIntOrNull('-3'), -3);
      expect(kotlinToIntOrNull('+3'), 3);
    });

    test('rejects what Kotlin rejects and Dart would accept', () {
      // `int.tryParse` trims; `toIntOrNull` does not.
      expect(kotlinToIntOrNull(' 7'), isNull);
      expect(kotlinToIntOrNull('7 '), isNull);
      // 32-bit ceiling.
      expect(kotlinToIntOrNull('2147483648'), isNull);
      expect(kotlinToIntOrNull('2147483647'), 2147483647);
      expect(kotlinToIntOrNull(''), isNull);
      expect(kotlinToIntOrNull('7.0'), isNull);
    });
  });

  group('the resource arm', () {
    test('rewrites the bare count the port itself stores', () {
      // `updateResourceNotification` stores the count and nothing else, so this
      // row rendered as `7` before the format was ported.
      expect(
        formatNotification(
          row(message: '7', type: 'resource', relatedId: '7'),
          l10n: l10n,
        ).text,
        'You have 7 resources not downloaded',
      );
    });

    test('a server resource message is shown as written', () {
      expect(
        formatNotification(
          row(message: '4 new resources are available', type: 'resource'),
          l10n: l10n,
        ).text,
        '4 new resources are available',
      );
    });

    test('a whitespace-padded count is not a count', () {
      // `toIntOrNull` rejects the space, so the message is passed through
      // rather than rewritten — asserted on the composed string, because the
      // renderer then drops the leading space exactly as `Html.fromHtml` does,
      // leaving the same bare `7` on screen in both apps.
      expect(
        notificationHtml(
          row(message: ' 7', type: 'resource'),
          l10n: l10n,
        ),
        ' 7',
      );
    });
  });

  group('the task arm', () {
    test(
      'rewrites title and date, with no prefix when the team is unknown',
      () {
        expect(
          formatNotification(
            row(message: 'Read chapter 3 Thu 12, August 2027', type: 'task'),
            l10n: l10n,
          ).text,
          'Read chapter 3 is due in Thu 12, August 2027',
        );
      },
    );

    test('prefixes the team name in bold when the task id is known', () {
      final formatted = formatNotification(
        row(
          message: 'Read chapter 3 Thu 12, August 2027',
          type: 'task',
          relatedId: 'task-9',
        ),
        l10n: l10n,
        context: const NotificationFormatContext(
          taskTeamNames: {'task-9': 'Reading Club'},
          joinRequestDetails: {},
        ),
      );
      expect(
        formatted.text,
        'Reading Club: Read chapter 3 is due in Thu 12, August 2027',
      );
      // The colon is *outside* the bold, unlike the join-request prefix.
      expect(formatted.spans.first.bold, isTrue);
      expect(formatted.spans.first.text, 'Reading Club');
      expect(formatted.spans[1].bold, isFalse);
      expect(formatted.spans[1].text, startsWith(': '));
    });

    test('falls back to the title key when the id has no entry', () {
      expect(
        formatNotification(
          row(
            message: 'Read chapter 3 Thu 12, August 2027',
            type: 'task',
            relatedId: 'task-9',
          ),
          l10n: l10n,
          context: const NotificationFormatContext(
            taskTeamNames: {'Read chapter 3': 'Reading Club'},
            joinRequestDetails: {},
          ),
        ).text,
        'Reading Club: Read chapter 3 is due in Thu 12, August 2027',
      );
    });

    test('the id wins over the title', () {
      expect(
        formatNotification(
          row(
            message: 'Read chapter 3 Thu 12, August 2027',
            type: 'task',
            relatedId: 'task-9',
          ),
          l10n: l10n,
          context: const NotificationFormatContext(
            taskTeamNames: {'task-9': 'By id', 'Read chapter 3': 'By title'},
            joinRequestDetails: {},
          ),
        ).text,
        startsWith('By id: '),
      );
    });

    test('a message with no parseable date is left alone', () {
      expect(
        formatNotification(
          row(message: 'Something is due soon', type: 'task'),
          l10n: l10n,
        ).text,
        'Something is due soon',
      );
    });
  });

  group('the join-request arm', () {
    test('a raw join_request row is rewritten from the lookup', () {
      final formatted = formatNotification(
        row(message: 'ignored', type: 'join_request', relatedId: 'req-1'),
        l10n: l10n,
        context: const NotificationFormatContext(
          taskTeamNames: {},
          joinRequestDetails: {
            'req-1': JoinRequestDetail(requester: 'Jane', team: 'My Team'),
          },
        ),
      );
      expect(
        formatted.text,
        'Join Request: Jane has requested to join My Team',
      );
      // Here the colon *is* inside the bold — it belongs to the prefix string.
      expect(
        formatted.spans.first,
        const NotificationSpan('Join Request:', bold: true),
      );
    });

    test('the raw type test is case-insensitive', () {
      expect(
        formatNotification(
          row(message: 'ignored', type: 'JOIN_REQUEST', relatedId: 'req-1'),
          l10n: l10n,
          context: const NotificationFormatContext(
            taskTeamNames: {},
            joinRequestDetails: {
              'req-1': JoinRequestDetail(requester: 'Jane', team: 'My Team'),
            },
          ),
        ).text,
        'Join Request: Jane has requested to join My Team',
      );
    });

    test('an unknown requester and team fall back to the localised pair', () {
      expect(
        formatNotification(
          row(message: 'ignored', type: 'join_request', relatedId: 'req-1'),
          l10n: l10n,
        ).text,
        'Join Request: Unknown User has requested to join Unknown Team',
      );
    });

    test('a row with no relatedId reads the "" fallback entry', () {
      expect(
        formatNotification(
          row(message: 'ignored', type: 'join_request'),
          l10n: l10n,
          context: const NotificationFormatContext(
            taskTeamNames: {},
            joinRequestDetails: {
              '': JoinRequestDetail(requester: 'Jane', team: 'My Team'),
            },
          ),
        ).text,
        'Join Request: Jane has requested to join My Team',
      );
    });

    test('a server team notification keeps its own sentence', () {
      // The only path that reaches this arm in production: raw type `team`
      // with `activeTab: applicantTab`. Kotlin's `if` sends it to the verbatim
      // branch, and the message it carries is already a sentence — with its own
      // markup, which the renderer interprets rather than printing.
      final formatted = formatNotification(
        row(
          message: '<b>Jane</b> has requested to join <b>"My Team"</b> team.',
          type: 'team',
          subType: 'join_request',
          relatedId: 'team-1',
        ),
        l10n: l10n,
      );
      expect(formatted.text, 'Jane has requested to join "My Team" team.');
      expect(formatted.spans.first, const NotificationSpan('Jane', bold: true));
    });
  });

  group('the arms that rewrite nothing', () {
    test('a team_join message is passed through', () {
      expect(
        formatNotification(
          row(
            message: 'You have been added to <b>"My Team"</b> team.',
            type: 'team',
            relatedId: 'team-1',
          ),
          l10n: l10n,
        ).text,
        'You have been added to "My Team" team.',
      );
    });

    test('a voice reply is passed through', () {
      expect(
        formatNotification(
          row(
            message: '<b>Jane</b> replied to your message.',
            type: 'replyMessage',
            relatedId: 'news-1',
          ),
          l10n: l10n,
        ).text,
        'Jane replied to your message.',
      );
    });

    test('an unresolved notification is passed through', () {
      expect(
        formatNotification(
          row(message: 'Something happened', type: 'somethingElse'),
          l10n: l10n,
        ).text,
        'Something happened',
      );
    });
  });

  group('buildNotificationFormatContext', () {
    test('partitions on the raw type, so a newTask gets no team name', () async {
      final repository = _RecordingLookups();
      await buildNotificationFormatContext([
        row(
          message: 'Read chapter 3 Thu 12, August 2027',
          type: 'newTask',
          relatedId: 'task-9',
        ),
      ], repository);
      // Nothing was looked up at all: `newTask` is not `task`, so the row never
      // enters `taskNotifications`. Kotlin has the identical hole.
      expect(repository.taskIdLookups, isEmpty);
      expect(repository.taskTitleLookups, isEmpty);
    });

    test('collects ids and parsed titles from raw task rows', () async {
      final repository = _RecordingLookups();
      final context = await buildNotificationFormatContext([
        row(
          message: 'Read chapter 3 Thu 12, August 2027',
          type: 'TaSk',
          relatedId: 'task-9',
        ),
        row(message: 'No date here', type: 'task'),
      ], repository);
      expect(repository.taskIdLookups, [
        ['task-9'],
      ]);
      expect(repository.taskTitleLookups, [
        ['Read chapter 3'],
      ]);
      expect(context.taskTeamNames, {
        'task-9': 'By id',
        'Read chapter 3': 'By title',
      });
    });

    test('a join request without a relatedId adds the "" fallback', () async {
      final repository = _RecordingLookups();
      final context = await buildNotificationFormatContext([
        row(message: 'ignored', type: 'join_request'),
      ], repository);
      expect(repository.fallbackCalls, 1);
      expect(context.joinRequestDetails['']?.requester, 'Fallback');
    });

    test('no task or join-request row means no queries at all', () async {
      final repository = _RecordingLookups();
      final context = await buildNotificationFormatContext([
        row(message: '7', type: 'resource'),
      ], repository);
      expect(repository.taskIdLookups, isEmpty);
      expect(repository.joinRequestBatches, isEmpty);
      expect(context.taskTeamNames, isEmpty);
    });
  });
}

/// The rendered text of a composed HTML string — what the row actually shows,
/// as opposed to what `formatStorageNotification` composes.
String renderedText(String html) => renderNotificationHtml(html).text;

/// A stand-in for the repository that records which lookups the context builder
/// asked for — the half of `loadNotifications` that decides whether a row can
/// have a team prefix at all.
class _RecordingLookups implements NotificationsRepository {
  final List<List<String>> taskIdLookups = [];
  final List<List<String>> taskTitleLookups = [];
  final List<List<String>> joinRequestBatches = [];
  int fallbackCalls = 0;

  @override
  Future<Map<String, String>> taskTeamNamesByTaskIds(
    List<String> taskIds,
  ) async {
    if (taskIds.isEmpty) return const {};
    taskIdLookups.add(taskIds);
    return {for (final id in taskIds) id: 'By id'};
  }

  @override
  Future<Map<String, String>> taskTeamNamesByTaskTitles(
    List<String> taskTitles,
  ) async {
    if (taskTitles.isEmpty) return const {};
    taskTitleLookups.add(taskTitles);
    return {for (final title in taskTitles) title: 'By title'};
  }

  @override
  Future<Map<String, JoinRequestDetail>> joinRequestDetailsBatch(
    List<String> relatedIds,
  ) async {
    if (relatedIds.isEmpty) return const {};
    joinRequestBatches.add(relatedIds);
    return {
      for (final id in relatedIds)
        id: const JoinRequestDetail(requester: 'Batch', team: 'Batch team'),
    };
  }

  @override
  Future<JoinRequestDetail> joinRequestDetails(String? relatedId) async {
    fallbackCalls++;
    return const JoinRequestDetail(
      requester: 'Fallback',
      team: 'Fallback team',
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnsupportedError('${invocation.memberName} is not used here');
}
