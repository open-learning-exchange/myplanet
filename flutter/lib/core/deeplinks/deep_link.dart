/// Port of `OnboardingActivity.handleDeepLinkIntent` and
/// `OnboardingActivity.maybeLaunchPublicSurvey`.
///
/// Pure Dart on purpose: the Kotlin does this work against a raw `Intent`, and
/// the interesting part — which URI shapes mean what, and the quirks in how the
/// Kotlin decides — is worth testing without a platform channel or a plugin.
/// The plugin side lives in `providers/deep_link_provider.dart`.
library;

/// What an incoming link resolved to, or null when the URI means nothing to
/// this app.
sealed class DeepLink {
  const DeepLink();
}

/// A `publicAccess` survey, answerable with no session at all.
///
/// [origin] is the server the link came from — `"${uri.scheme}://${uri
/// .encodedAuthority}"` in the Kotlin. It is the reason a plugin is needed:
/// this value only exists on the *original* URI, and a go_router location is
/// relative whenever the platform did not hand over an absolute one.
class PublicSurveyDeepLink extends DeepLink {
  const PublicSurveyDeepLink({
    required this.origin,
    required this.teamId,
    required this.surveyId,
  });

  final String origin;
  final String teamId;
  final String surveyId;

  @override
  String toString() =>
      'PublicSurveyDeepLink($origin, team: $teamId, survey: $surveyId)';

  @override
  bool operator ==(Object other) =>
      other is PublicSurveyDeepLink &&
      other.origin == origin &&
      other.teamId == teamId &&
      other.surveyId == surveyId;

  @override
  int get hashCode => Object.hash(origin, teamId, surveyId);
}

/// A destination inside the app: `myplanet://courses/<id>` or
/// `https://host/app/courses/<id>`.
///
/// The Kotlin does not navigate on these directly. It writes
/// `pending_deep_link_section`/`pending_deep_link_id` to preferences and
/// `DashboardActivity` consumes them once the user is past login, which is what
/// makes a link survive the sign-in it triggers.
class SectionDeepLink extends DeepLink {
  const SectionDeepLink(this.section, [this.contentId]);

  final String section;
  final String? contentId;

  @override
  String toString() => 'SectionDeepLink($section, $contentId)';

  @override
  bool operator ==(Object other) =>
      other is SectionDeepLink &&
      other.section == section &&
      other.contentId == contentId;

  @override
  int get hashCode => Object.hash(section, contentId);
}

/// Resolves [uri] the way `handleDeepLinkIntent` resolves an `ACTION_VIEW`
/// intent.
///
/// [isSignedIn] is `prefData.isLoggedIn()`: a survey link opened by a member
/// deliberately does *not* go to the anonymous public screen. The Kotlin falls
/// through to its section branch instead, which maps `/survey/<team>/<survey>`
/// to the `surveys` section carrying the **survey** id — the third segment, not
/// the second. Reproduced.
DeepLink? parseDeepLink(Uri uri, {required bool isSignedIn}) {
  final scheme = uri.scheme.toLowerCase();
  final segments = uri.pathSegments.where((s) => s.isNotEmpty).toList();

  if (scheme == 'myplanet') {
    // `uri.host` is the section for the custom scheme: `myplanet://courses/id`
    // parses the section as the authority, not as a path segment.
    final section = uri.host;
    if (section.isEmpty) return null;
    return SectionDeepLink(section, segments.isEmpty ? null : segments.first);
  }

  if (scheme != 'http' && scheme != 'https') return null;

  final isSurveyPath =
      segments.isNotEmpty && segments.first.toLowerCase() == 'survey';

  if (isSurveyPath && !isSignedIn) {
    if (segments.length < 3) return null;
    return PublicSurveyDeepLink(
      // `encodedAuthority` in the Kotlin: host plus a non-default port, and
      // any userinfo the link carried.
      origin: '$scheme://${uri.authority}',
      teamId: segments[1],
      surveyId: segments[2],
    );
  }

  if (isSurveyPath) {
    return segments.length < 3
        ? const SectionDeepLink('surveys')
        : SectionDeepLink('surveys', segments[2]);
  }

  // `val appIndex = segments.indexOf("app")` — and when there is no `app`
  // segment that is -1, so `appIndex + 1` is 0 and the *first* segment becomes
  // the section. So `https://host/courses/abc` is read as the courses section
  // even though nothing in the path says `app`. Reproduced rather than
  // tightened: the section names are a closed set (see `deepLinkRoute`), so an
  // unrelated path resolves to a section nothing maps and is dropped one layer
  // up. Narrowing it here would instead change which links work.
  final appIndex = segments.indexOf('app');
  final sectionIndex = appIndex + 1;
  if (sectionIndex >= segments.length) return null;
  final section = segments[sectionIndex];
  final idIndex = sectionIndex + 1;
  return SectionDeepLink(
    section,
    idIndex < segments.length ? segments[idIndex] : null,
  );
}
