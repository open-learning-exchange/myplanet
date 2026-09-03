import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/repository/notifications_repository.dart';

void main() {
  group('extractTeamSubtype', () {
    test('returns join_request when linkParams.activeTab is applicantTab', () {
      expect(
        extractTeamSubtype('team', {
          'linkParams': {'activeTab': 'applicantTab'},
        }),
        'join_request',
      );
    });

    test('returns null when activeTab is a different tab', () {
      expect(
        extractTeamSubtype('team', {
          'linkParams': {'activeTab': 'messagesTab'},
        }),
        isNull,
      );
    });

    test('returns null when linkParams is absent', () {
      expect(extractTeamSubtype('team', {'message': 'hi'}), isNull);
    });

    test('returns null for non-team raw types', () {
      expect(
        extractTeamSubtype('storage', {
          'linkParams': {'activeTab': 'applicantTab'},
        }),
        isNull,
      );
    });

    test('handles null doc', () {
      expect(extractTeamSubtype('team', null), isNull);
    });
  });

  group('extractRelatedId', () {
    test('team uses the item field', () {
      expect(extractRelatedId('team', null, {'item': 'team-abc'}), 'team-abc');
    });

    test('replyMessage uses the replyTo field', () {
      expect(
        extractRelatedId('replyMessage', null, {'replyTo': 'voice-123'}),
        'voice-123',
      );
    });

    test('newTask parses the id from a view link', () {
      expect(
        extractRelatedId('newTask', '/teams/view/team-xyz', null),
        'team-xyz',
      );
    });

    test('newTask returns null when link has no view segment', () {
      expect(extractRelatedId('newTask', '/teams/team-xyz', null), isNull);
    });

    test('other types return null', () {
      expect(extractRelatedId('storage', null, null), isNull);
    });

    // Kotlin is `doc.get("item")?.asString` — no blank check at all
    // (`NotificationsRepositoryImpl.kt:393-397`). A whitespace-only value is
    // stored as-is; `NotificationDestinationResolver._nonBlank` is where it
    // stops being actionable, which is where Kotlin's `isNullOrEmpty` guards
    // sit too. A duplicate of this helper used to null it out here instead.
    test('a whitespace-only item is carried through, as Kotlin carries it', () {
      expect(extractRelatedId('team', null, {'item': '  '}), '  ');
    });
  });

  group('extractIdFromLink', () {
    test('parses the segment after view', () {
      expect(extractIdFromLink('/teams/view/team-abc'), 'team-abc');
    });

    test('returns null when view is the last segment', () {
      expect(extractIdFromLink('/teams/view'), isNull);
    });

    test('returns null for blank links', () {
      expect(extractIdFromLink(null), isNull);
      expect(extractIdFromLink('  '), isNull);
    });
  });

  group('resolveType', () {
    test('known types pass through lowercased', () {
      expect(resolveNotificationType('storage', ''), 'storage');
      expect(resolveNotificationType('RESOURCE', ''), 'resource');
      expect(resolveNotificationType('Task', ''), 'task');
      expect(resolveNotificationType('join_request', ''), 'join_request');
      expect(resolveNotificationType('team_join', ''), 'team_join');
      expect(resolveNotificationType('chat', ''), 'chat');
      expect(resolveNotificationType('voice_reply', ''), 'voice_reply');
    });

    test('team with join_request subType uses the subType', () {
      expect(
        resolveNotificationType(
          'team',
          'someone wants to join',
          subType: 'join_request',
        ),
        'join_request',
      );
    });

    test('team without subType sniffs join_request from message', () {
      expect(
        resolveNotificationType('team', 'User requested to join Team'),
        'join_request',
      );
      expect(
        resolveNotificationType('team', 'wants to join your team'),
        'join_request',
      );
    });

    test('team without subType sniffs chat from message', () {
      expect(resolveNotificationType('team', 'New voice in the team'), 'chat');
    });

    test(
      'team without subType and unrecognized message defaults to team_join',
      () {
        expect(
          resolveNotificationType('team', 'You have been added to Team'),
          'team_join',
        );
      },
    );

    test('newTask collapses to task', () {
      expect(resolveNotificationType('newTask', ''), 'task');
    });

    test('newResource collapses to resource', () {
      expect(resolveNotificationType('newResource', ''), 'resource');
    });

    // Kotlin's final `when` block (`NotificationsRepositoryImpl.kt:352-362`)
    // sniffs the message for any type outside KNOWN_TYPES and falls back to
    // "notification". The port used to `return type`, so a `replyMessage`
    // document stayed `replyMessage` and matched no reader.
    test('an unknown type is sniffed from its message', () {
      expect(
        resolveNotificationType('replyMessage', 'bob replied to your voice'),
        'voice_reply',
      );
      expect(
        resolveNotificationType('mystery', 'Fatima wants to join Team Blue'),
        'join_request',
      );
      expect(
        resolveNotificationType('mystery', "You've been added to Team Blue"),
        'team_join',
      );
      expect(
        resolveNotificationType('mystery', 'Water run is due: 2026-09-10'),
        'task',
      );
      expect(
        resolveNotificationType('mystery', 'Storage is running low'),
        'storage',
      );
      expect(resolveNotificationType('mystery', '3 new resources'), 'resource');
    });

    test('an unknown type with an unreadable message is "notification"', () {
      expect(resolveNotificationType('mystery', ''), 'notification');
    });

    // `lowerType` is what Kotlin tests against, so the raw server casing of
    // `newTask`/`newResource`/`team` must not decide the outcome.
    test('the raw type is matched case-insensitively', () {
      expect(resolveNotificationType('NEWTASK', ''), 'task');
      expect(resolveNotificationType('NewResource', ''), 'resource');
      expect(resolveNotificationType('TEAM', 'unremarkable'), 'team_join');
    });

    // Kotlin's `extractIdFromLink` is `link.trim('/')` — a *character* trim.
    // An earlier duplicate of this helper filtered every empty segment out and
    // trimmed whitespace instead, which disagrees here.
    test('an empty segment after `view` is kept, as Kotlin keeps it', () {
      expect(extractIdFromLink('/teams/view//abc'), '');
    });
  });
}
