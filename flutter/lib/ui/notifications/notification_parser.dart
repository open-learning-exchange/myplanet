/// Ports the server-notification parsing that `NotificationsRepositoryImpl`
/// and `NotificationsViewModel` do on the Kotlin side (upstream #15606/#15607).
///
/// A raw server `type` of `"team"` covers join requests, membership changes,
/// and chat posts alike, and the server renders `message` in the recipient's
/// locale, so it cannot be classified reliably by sniffing English or Spanish
/// phrases. The server sends a locale-independent `linkParams.activeTab ==
/// "applicantTab"` signal for join-request notifications specifically; that is
/// the [extractTeamSubtype] signal. When it is absent the fallback is the
/// message-sniffing in [resolveType], which is best-effort and degrades
/// gracefully to `"team_join"`.
///
/// These are pure functions so the parsing policy is independently testable
/// without a sync direction or database.
class NotificationParser {
  const NotificationParser._();

  static const Set<String> knownTypes = {
    'storage',
    'resource',
    'task',
    'join_request',
    'team_join',
    'chat',
    'voice_reply',
  };

  /// Extracts the join-request sub-type for a raw `"team"` notification.
  ///
  /// Returns `"join_request"` when `linkParams.activeTab == "applicantTab"`,
  /// otherwise `null` — the caller falls through to message-sniffing.
  static String? extractTeamSubtype(
    String? rawType,
    Map<String, dynamic>? doc,
  ) {
    if (rawType == null || rawType.toLowerCase() != 'team') return null;
    final linkParams = doc?['linkParams'];
    if (linkParams is! Map) return null;
    final activeTab = linkParams['activeTab'];
    return activeTab == 'applicantTab' ? 'join_request' : null;
  }

  /// Extracts the related id from a server notification document.
  ///
  /// * `"team"` — the `item` field (a team id).
  /// * `"replyMessage"` — the `replyTo` field (a voice/news id).
  /// * `"newTask"` — the id parsed from the `link` URL (segment after `view`).
  static String? extractRelatedId(
    String? rawType,
    String? link,
    Map<String, dynamic>? doc,
  ) {
    if (rawType == null) return null;
    switch (rawType) {
      case 'team':
        return _nonBlank(doc?['item']?.toString());
      case 'replyMessage':
        return _nonBlank(doc?['replyTo']?.toString());
      case 'newTask':
        return extractIdFromLink(link);
      default:
        return null;
    }
  }

  /// Parses the id from a `view` link, e.g. `/teams/view/team-abc` -> `team-abc`.
  static String? extractIdFromLink(String? link) {
    if (link == null || link.trim().isEmpty) return null;
    final segments = link
        .trim()
        .split('/')
        .where((s) => s.isNotEmpty)
        .toList(growable: false);
    final viewIndex = segments.indexOf('view');
    if (viewIndex >= 0 && viewIndex < segments.length - 1) {
      return segments[viewIndex + 1];
    }
    return null;
  }

  /// Normalizes a raw server type into the destination-routing type.
  ///
  /// Known types pass through. `"team"` is split via [subType] or message
  /// sniffing into `join_request`, `chat`, or `team_join`. `"newTask"` and
  /// `"newResource"` collapse to `task` and `resource`.
  static String resolveType(String type, String message, {String? subType}) {
    final lower = type.toLowerCase();
    if (knownTypes.contains(lower)) return lower;
    final msgLower = message.toLowerCase();
    if (type == 'team') {
      if (subType != null && subType.isNotEmpty) return subType;
      if (msgLower.contains('requested to join') ||
          msgLower.contains('wants to join') ||
          msgLower.contains('solicitado unirse')) {
        return 'join_request';
      }
      if (msgLower.contains('posted a message on') ||
          msgLower.contains('posted a new voice') ||
          msgLower.contains('new voice in') ||
          msgLower.contains('posted in')) {
        return 'chat';
      }
      return 'team_join';
    }
    if (type == 'newTask') return 'task';
    if (type == 'newResource') return 'resource';
    return type;
  }

  static String? _nonBlank(String? value) {
    if (value == null) return null;
    final trimmed = value.trim();
    return trimmed.isEmpty ? null : trimmed;
  }
}
