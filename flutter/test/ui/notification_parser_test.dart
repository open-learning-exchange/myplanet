import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/ui/notifications/notification_parser.dart';

void main() {
  group('extractTeamSubtype', () {
    test('returns join_request when linkParams.activeTab is applicantTab', () {
      expect(
        NotificationParser.extractTeamSubtype('team', {
          'linkParams': {'activeTab': 'applicantTab'},
        }),
        'join_request',
      );
    });

    test('returns null when activeTab is a different tab', () {
      expect(
        NotificationParser.extractTeamSubtype('team', {
          'linkParams': {'activeTab': 'messagesTab'},
        }),
        isNull,
      );
    });

    test('returns null when linkParams is absent', () {
      expect(
        NotificationParser.extractTeamSubtype('team', {'message': 'hi'}),
        isNull,
      );
    });

    test('returns null for non-team raw types', () {
      expect(
        NotificationParser.extractTeamSubtype('storage', {
          'linkParams': {'activeTab': 'applicantTab'},
        }),
        isNull,
      );
    });

    test('handles null doc', () {
      expect(NotificationParser.extractTeamSubtype('team', null), isNull);
    });
  });

  group('extractRelatedId', () {
    test('team uses the item field', () {
      expect(
        NotificationParser.extractRelatedId('team', null, {'item': 'team-abc'}),
        'team-abc',
      );
    });

    test('replyMessage uses the replyTo field', () {
      expect(
        NotificationParser.extractRelatedId('replyMessage', null, {
          'replyTo': 'voice-123',
        }),
        'voice-123',
      );
    });

    test('newTask parses the id from a view link', () {
      expect(
        NotificationParser.extractRelatedId(
          'newTask',
          '/teams/view/team-xyz',
          null,
        ),
        'team-xyz',
      );
    });

    test('newTask returns null when link has no view segment', () {
      expect(
        NotificationParser.extractRelatedId('newTask', '/teams/team-xyz', null),
        isNull,
      );
    });

    test('other types return null', () {
      expect(
        NotificationParser.extractRelatedId('storage', null, null),
        isNull,
      );
    });

    test('blank item/replyTo values are treated as absent', () {
      expect(
        NotificationParser.extractRelatedId('team', null, {'item': '  '}),
        isNull,
      );
    });
  });

  group('extractIdFromLink', () {
    test('parses the segment after view', () {
      expect(
        NotificationParser.extractIdFromLink('/teams/view/team-abc'),
        'team-abc',
      );
    });

    test('returns null when view is the last segment', () {
      expect(NotificationParser.extractIdFromLink('/teams/view'), isNull);
    });

    test('returns null for blank links', () {
      expect(NotificationParser.extractIdFromLink(null), isNull);
      expect(NotificationParser.extractIdFromLink('  '), isNull);
    });
  });

  group('resolveType', () {
    test('known types pass through lowercased', () {
      expect(NotificationParser.resolveType('storage', ''), 'storage');
      expect(NotificationParser.resolveType('RESOURCE', ''), 'resource');
      expect(NotificationParser.resolveType('Task', ''), 'task');
      expect(
        NotificationParser.resolveType('join_request', ''),
        'join_request',
      );
      expect(NotificationParser.resolveType('team_join', ''), 'team_join');
      expect(NotificationParser.resolveType('chat', ''), 'chat');
      expect(NotificationParser.resolveType('voice_reply', ''), 'voice_reply');
    });

    test('team with join_request subType uses the subType', () {
      expect(
        NotificationParser.resolveType(
          'team',
          'someone wants to join',
          subType: 'join_request',
        ),
        'join_request',
      );
    });

    test('team without subType sniffs join_request from message', () {
      expect(
        NotificationParser.resolveType('team', 'User requested to join Team'),
        'join_request',
      );
      expect(
        NotificationParser.resolveType('team', 'wants to join your team'),
        'join_request',
      );
    });

    test('team without subType sniffs chat from message', () {
      expect(
        NotificationParser.resolveType('team', 'New voice in the team'),
        'chat',
      );
    });

    test(
      'team without subType and unrecognized message defaults to team_join',
      () {
        expect(
          NotificationParser.resolveType('team', 'You have been added to Team'),
          'team_join',
        );
      },
    );

    test('newTask collapses to task', () {
      expect(NotificationParser.resolveType('newTask', ''), 'task');
    });

    test('newResource collapses to resource', () {
      expect(NotificationParser.resolveType('newResource', ''), 'resource');
    });

    test('unknown type is returned as-is', () {
      expect(NotificationParser.resolveType('mystery', ''), 'mystery');
    });
  });
}
